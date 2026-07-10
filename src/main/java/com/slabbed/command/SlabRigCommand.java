package com.slabbed.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabTestKit;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /slabrig <preset>} — builds Slabbed's standard live-test rigs in the world next to the
 * player, instantly and identically every time, so a human tester never rebuilds a test row by hand.
 *
 * <p>This is dev tooling that SHIPS in every jar (the debug-tooling convention: present, default
 * behaviour-neutral, player-invocable). It is a SERVER command (it authors real block state and the
 * genuine Slabbed anchor attachments), so it registers via {@link CommandRegistrationCallback} and
 * lives in a shipping package — NOT {@code com.slabbed.dev}, which the release jar strips.
 *
 * <p>It builds SCENERY only: {@code w.setBlock(..)} plus the public attachment-authoring API
 * ({@link SlabAnchorAttachment#addAnchor}, {@link SlabAnchorAttachment#addCompoundFullBlockAnchor},
 * {@link SlabAnchorAttachment#addCompoundVisibleSideLowerSlab}). It never touches height computation,
 * so it cannot violate the WYSIWYG law — the rigs are exactly what the gametest rig builders author
 * ({@code NeighborUpdateInvarianceTest.markedSlabRig} /
 * {@code AnchoredDepthReadbackTest.buildCompoundVisibleSupport}).
 *
 * <p>Presets:
 * <ul>
 *   <li>{@code /slabrig tower [force]} — the compound-visible tower: ground, bottom slab, stone,
 *       bottom slab, compound full block (anchored, reads -1.0) with a marked side slab beside it.
 *   <li>{@code /slabrig rows [n] [force]} — n columns (default 16, 2-cell spacing) of lowered -0.5
 *       supports (stone-on-bottom-slab-on-ground), plus a parallel row of genuine -1.0 compound
 *       supports, each row labelled with a sign stating the expected dy.
 *   <li>{@code /slabrig clear} — removes the last rig this player built in THIS dimension (bounds
 *       tracked per player per dimension, including 2 cells of headroom for hand-placed subjects).
 * </ul>
 *
 * <p>Safety: {@code tower}/{@code rows} refuse to overwrite a non-empty footprint (add the literal
 * {@code force} to overwrite), refuse to build below the world's minimum build height, pre-clean every
 * footprint cell's Slabbed attachments (flag-2 {@code setBlock} does NOT fire the removal hook, so a
 * rig authored over stale markers would be "haunted" — its sign would lie), and read back
 * {@link SlabSupport#getYOffset} after authoring so a rig that does not measure what its sign says
 * warns in red instead of reporting success.
 */
public final class SlabRigCommand {

    private SlabRigCommand() {
    }

    /** Build flag: notify clients, no neighbor updates (matches the gametest rig builders). */
    private static final int FLAG = Block.UPDATE_CLIENTS;

    /**
     * Clear flag: UPDATE_ALL (not UPDATE_CLIENTS) so support-requiring neighbours left behind by a
     * hand-placed subject get their vanilla survival recheck when the rig is torn down.
     */
    private static final int CLEAR_FLAG = Block.UPDATE_ALL;

    /** Cells of headroom tracked/scanned/cleared above the rig top, where the tester places subjects. */
    private static final int HEADROOM = 2;

    private static final int DEFAULT_ROWS = 16;
    private static final int COLUMN_SPACING = 2;
    private static final double EPS = 1.0e-6;

    /** The mega rig's four support variants, one per row (see {@link #buildMegaColumn}). */
    private static final int MEGA_ROW_COUNT = 4;
    /** The overhang_and_ceiling row: its auto-item is a hanging attempt (clicks the ceiling down-face). */
    private static final int MEGA_ROW_HANGING = 3;
    /** Cap the refused-id list echoed in the mega chat summary. */
    private static final int MAX_REFUSED_LISTED = 15;
    /** Default per-variant column count for {@code /slabrig mega} (capped by the placeable-kit size). */
    private static final int DEFAULT_MEGA_COLUMNS = 40;
    /** Expected support-surface dy per mega row, in row order (self-verify against the sign claims). */
    private static final double[] MEGA_ROW_DY = {0.0, -0.5, -1.0, -0.5};
    private static final String[] MEGA_ROW_NAME = {
            "bottom slab", "slab+block", "compound column", "overhang_and_ceiling"};

    /**
     * In-memory per-(player, dimension) record of the last rig volume, for {@code /slabrig clear}.
     * Keyed by {@link RigKey} so a rig built in the nether does not orphan an overworld rig record.
     * Concurrent because commands may run on the server thread while worlds unload elsewhere.
     */
    private static final Map<RigKey, Bounds> LAST_BOUNDS = new ConcurrentHashMap<>();
    private static final UUID CONSOLE_KEY = new UUID(0L, 0L);

    /** Identity of a remembered rig: which player, in which dimension. */
    private record RigKey(UUID player, ResourceKey<Level> dimension) {
    }

    // ── registration ─────────────────────────────────────────────────────────

    /** Wires the command into the server dispatcher for every world (integrated and dedicated). */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher));
    }

    /** Builds the {@code slabrig} node into {@code dispatcher}. Visible for the smoke test. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("slabrig")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(SlabRigCommand::usage)
                        .then(Commands.literal("tower")
                                .executes(ctx -> tower(ctx, false))
                                .then(Commands.literal("force")
                                        .executes(ctx -> tower(ctx, true))))
                        .then(Commands.literal("rows")
                                .executes(ctx -> rows(ctx, DEFAULT_ROWS, false))
                                .then(Commands.literal("force")
                                        .executes(ctx -> rows(ctx, DEFAULT_ROWS, true)))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> rows(ctx, IntegerArgumentType.getInteger(ctx, "count"), false))
                                        .then(Commands.literal("force")
                                                .executes(ctx -> rows(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "count"), true)))))
                        .then(Commands.literal("mega")
                                .executes(ctx -> mega(ctx, DEFAULT_MEGA_COLUMNS, false))
                                .then(Commands.literal("force")
                                        .executes(ctx -> mega(ctx, DEFAULT_MEGA_COLUMNS, true)))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> mega(ctx, IntegerArgumentType.getInteger(ctx, "count"), false))
                                        .then(Commands.literal("force")
                                                .executes(ctx -> mega(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "count"), true)))))
                        .then(Commands.literal("clear")
                                .executes(SlabRigCommand::clear))
        );
    }

    // ── geometry (shared with the smoke test) ─────────────────────────────────

    /** Horizontal direction the source faces (the rig builds this way). */
    public static Direction rigFacing(CommandSourceStack source) {
        return Direction.fromYRot(source.getRotation().y);
    }

    /**
     * Ground-level base cell of the rig: the player's feet {@code y - 1}, three blocks in front.
     * The bottom block of a rig sits here.
     */
    public static BlockPos rigBase(CommandSourceStack source) {
        BlockPos feet = BlockPos.containing(source.getPosition());
        return feet.below().relative(rigFacing(source), 3);
    }

    // ── /slabrig (usage) ──────────────────────────────────────────────────────

    private static int usage(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal(
                "[slabrig] usage: /slabrig <tower [force]|rows [n] [force]|mega [n] [force]|clear>\n"
                        + "  tower [force]    — compound-visible -1.0 marked tower\n"
                        + "  rows [n] [force] — n columns (default " + DEFAULT_ROWS + ") of -0.5 and -1.0 supports\n"
                        + "  mega [n] [force] — the everything-rig: n columns x 4 support variants with one\n"
                        + "                     kit item auto-placed on each (default " + DEFAULT_MEGA_COLUMNS + ", capped by kit size)\n"
                        + "  clear            — remove the last rig you built in this dimension\n"
                        + "  (force overwrites a non-empty footprint)"), false);
        return 1;
    }

    // ── /slabrig tower ────────────────────────────────────────────────────────

    private static int tower(CommandContext<CommandSourceStack> ctx, boolean force) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        Direction facing = rigFacing(source);
        BlockPos base = rigBase(source);

        if (belowWorldBottom(source, world, base)) {
            return 0;
        }

        // Footprint = the tower cells + HEADROOM above the rig top, as an inclusive box.
        Set<BlockPos> cells = new LinkedHashSet<>();
        collectTowerCells(base, facing, cells);
        Bounds bounds = footprintBounds(cells, world.dimension());

        if (refuseIfFootprintOccupied(source, world, bounds, force)) {
            return 0;
        }
        precleanFootprint(world, bounds);

        buildCompoundTower(world, base, facing, bounds);
        rememberBounds(source, bounds);

        // F4 self-verify: the compound full block and the marked side slab must both read -1.0.
        BlockPos fb = base.above(4);
        BlockPos support = fb.relative(facing.getCounterClockWise());
        List<String> mismatches = new ArrayList<>();
        addIfMismatch(mismatches, world, fb, -1.0, "compound full block");
        addIfMismatch(mismatches, world, support, -1.0, "marked side slab");
        if (!mismatches.isEmpty()) {
            source.sendFailure(warn("tower", base, facing, mismatches));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "[slabrig] tower built at " + base.toShortString() + " facing " + facing.getName()
                        + " — compound full block reads -1.0, marked side slab beside it."), false);
        return 1;
    }

    /**
     * The compound-visible tower — an exact mirror of
     * {@code AnchoredDepthReadbackTest.buildCompoundVisibleSupport}: ground stone, bottom slab, stone,
     * bottom slab, compound full block (anchored + compound-full-block anchor, reads -1.0), and a
     * marked side slab authored against it. Returns the compound full-block cell.
     */
    private static BlockPos buildCompoundTower(ServerLevel world, BlockPos base, Direction facing, Bounds bounds) {
        setStone(world, base, bounds);
        bottomSlab(world, base.above(1), bounds);
        setStone(world, base.above(2), bounds);
        bottomSlab(world, base.above(3), bounds);

        BlockPos fb = base.above(4);
        setStone(world, fb, bounds);
        SlabAnchorAttachment.addAnchor(world, fb, world.getBlockState(fb));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(world, fb, world.getBlockState(fb));

        // Marked side slab beside the compound full block (same Y, one horizontal step). The qualifier
        // is direction-agnostic, so we place it on the player's left for a clean read.
        BlockPos support = fb.relative(facing.getCounterClockWise());
        bottomSlab(world, support, bounds);
        SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(world, support, world.getBlockState(support),
                fb, world.getBlockState(fb));
        return fb;
    }

    /** The cells {@link #buildCompoundTower} touches, computed WITHOUT writing (footprint planning). */
    private static void collectTowerCells(BlockPos base, Direction facing, Set<BlockPos> cells) {
        cells.add(base);
        cells.add(base.above(1));
        cells.add(base.above(2));
        cells.add(base.above(3));
        BlockPos fb = base.above(4);
        cells.add(fb);
        cells.add(fb.relative(facing.getCounterClockWise()));
    }

    // ── /slabrig rows [n] ─────────────────────────────────────────────────────

    private static int rows(CommandContext<CommandSourceStack> ctx, int count, boolean force) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        Direction facing = rigFacing(source);
        Direction right = facing.getClockWise();
        BlockPos base = rigBase(source);

        if (belowWorldBottom(source, world, base)) {
            return 0;
        }

        Set<BlockPos> cells = new LinkedHashSet<>();
        collectRowsCells(base, facing, count, cells);
        Bounds bounds = footprintBounds(cells, world.dimension());

        if (refuseIfFootprintOccupied(source, world, bounds, force)) {
            return 0;
        }
        precleanFootprint(world, bounds);

        // Row A — lowered -0.5 supports: the deep lowered-slab stack (ground stone, slab, stone, slab),
        // an exact mirror of AnchoredDepthReadbackTest.buildLoweredSlabStack. The TOP slab reads -0.5 and
        // is where the tester places subjects. (A stone-on-slab is NOT lowered — an opaque full cube is
        // excluded as a subject by the world-hole guard, so it stays flush; the slab is the real seat.)
        BlockPos rowAStart = base;
        signAt(world, rowAStart.relative(right.getOpposite(), COLUMN_SPACING), "Row A", "dy -0.5", "place on top", bounds);
        for (int i = 0; i < count; i++) {
            BlockPos cell = rowAStart.relative(right, i * COLUMN_SPACING);
            setStone(world, cell, bounds);            // ground
            bottomSlab(world, cell.above(1), bounds); // slab (0)
            setStone(world, cell.above(2), bounds);   // stone (carrier)
            bottomSlab(world, cell.above(3), bounds); // TOP slab -> reads -0.5 (the seat)
        }

        // Row B — genuine -1.0 supports: a compound tower per column, marked side slab authored.
        // Offset 4 cells deeper so the two rows are clearly parallel and separated.
        BlockPos rowBStart = base.relative(facing, 4);
        signAt(world, rowBStart.relative(right.getOpposite(), COLUMN_SPACING), "Row B", "dy -1.0", "place on FB/slab", bounds);
        for (int i = 0; i < count; i++) {
            BlockPos cell = rowBStart.relative(right, i * COLUMN_SPACING);
            buildCompoundTower(world, cell, facing, bounds);
        }

        rememberBounds(source, bounds);

        // F4 self-verify: one sample support per row (Row A top slab -0.5, Row B compound FB -1.0).
        BlockPos rowASample = rowAStart.above(3);
        BlockPos rowBSample = rowBStart.above(4);
        List<String> mismatches = new ArrayList<>();
        addIfMismatch(mismatches, world, rowASample, -0.5, "Row A support");
        addIfMismatch(mismatches, world, rowBSample, -1.0, "Row B support");
        if (!mismatches.isEmpty()) {
            source.sendFailure(warn("rows", base, facing, mismatches));
            return 0;
        }

        final int placed = count;
        source.sendSuccess(() -> Component.literal(
                "[slabrig] rows built at " + base.toShortString() + " facing " + facing.getName()
                        + " — " + placed + " columns: Row A -0.5, Row B -1.0."), false);
        return 1;
    }

    /** The cells {@link #rows} touches, computed WITHOUT writing (footprint planning). */
    private static void collectRowsCells(BlockPos base, Direction facing, int count, Set<BlockPos> cells) {
        Direction right = facing.getClockWise();

        BlockPos rowAStart = base;
        collectSignCells(rowAStart.relative(right.getOpposite(), COLUMN_SPACING), cells);
        for (int i = 0; i < count; i++) {
            BlockPos cell = rowAStart.relative(right, i * COLUMN_SPACING);
            cells.add(cell);
            cells.add(cell.above(1));
            cells.add(cell.above(2));
            cells.add(cell.above(3));
        }

        BlockPos rowBStart = base.relative(facing, 4);
        collectSignCells(rowBStart.relative(right.getOpposite(), COLUMN_SPACING), cells);
        for (int i = 0; i < count; i++) {
            collectTowerCells(rowBStart.relative(right, i * COLUMN_SPACING), facing, cells);
        }
    }

    private static void collectSignCells(BlockPos ground, Set<BlockPos> cells) {
        cells.add(ground);
        cells.add(ground.above());
    }

    /** Places a labelled standing sign one block above {@code ground}, on a stone pedestal. */
    private static void signAt(ServerLevel world, BlockPos ground, String l0, String l1, String l2, Bounds bounds) {
        setStone(world, ground, bounds);
        BlockPos signPos = ground.above();
        BlockState sign = Blocks.OAK_SIGN.defaultBlockState();
        world.setBlock(signPos, sign, FLAG);
        bounds.include(signPos);
        BlockEntity be = world.getBlockEntity(signPos);
        if (be instanceof SignBlockEntity signEntity) {
            signEntity.updateText(text -> text
                    .setMessage(0, Component.literal(l0))
                    .setMessage(1, Component.literal(l1))
                    .setMessage(2, Component.literal(l2)), true);
        }
    }

    // ── /slabrig mega [n] ─────────────────────────────────────────────────────

    /**
     * THE mega rig: {@code n} columns wide, each column carrying the FOUR support variants (one per
     * row, along the facing axis): (0) a bare bottom slab, (1) a bottom slab + full block, (2) the
     * compound column (ground/slab/stone/slab/full block) with its marked side slab, and (3) the
     * overhang-and-ceiling variant (a lowered support with an air cell to the side and a full block
     * overhead for hanging tests). Every column {@code i} is seated with one kit item ({@code i}-th
     * entry of {@link SlabTestKit#placeableItems()}) auto-placed on each variant's surface via the
     * source player's REAL {@code useOn} — so a whole test board of category representatives on every
     * geometry appears in one command. Items that refuse to place are skipped and counted.
     *
     * <p>Scenery is authored with the same {@code setBlock} + anchor-authoring path as {@code tower} /
     * {@code rows} (it cannot touch height computation); only the SUBJECTS go through the real placement
     * path, exactly like the gametest rigs. The player's held item is saved and restored around the
     * auto-placement sweep.
     */
    private static int mega(CommandContext<CommandSourceStack> ctx, int columns, boolean force) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        Player player = source.getEntity() instanceof Player p ? p : null;
        if (player == null) {
            source.sendFailure(Component.literal(
                    "[slabrig] mega auto-places kit items via your hand — run it as a player, not the console."));
            return 0;
        }

        Direction facing = rigFacing(source);
        Direction right = facing.getClockWise();
        BlockPos base = rigBase(source);
        if (belowWorldBottom(source, world, base)) {
            return 0;
        }

        List<Item> kit = SlabTestKit.placeableItems();
        int n = Math.min(columns, kit.size());
        if (n <= 0) {
            source.sendFailure(Component.literal("[slabrig] mega has no kit items to place."));
            return 0;
        }

        Set<BlockPos> cells = new LinkedHashSet<>();
        collectMegaCells(base, facing, right, n, cells);
        Bounds bounds = footprintBounds(cells, world.dimension());

        if (refuseIfFootprintOccupied(source, world, bounds, force)) {
            return 0;
        }
        precleanFootprint(world, bounds);

        // Build every variant column's scenery, remembering each column's four seat surfaces.
        BlockPos[][] seats = new BlockPos[n][MEGA_ROW_COUNT];
        for (int i = 0; i < n; i++) {
            for (int v = 0; v < MEGA_ROW_COUNT; v++) {
                seats[i][v] = buildMegaColumn(world, base, facing, right, v, i, bounds);
            }
        }
        // One labelled sign per row variant (near end of the row, on the -right side).
        for (int v = 0; v < MEGA_ROW_COUNT; v++) {
            BlockPos signGround = base.relative(facing, v).relative(right.getOpposite(), COLUMN_SPACING);
            signAt(world, signGround, "Row " + v, MEGA_ROW_NAME[v], "dy " + MEGA_ROW_DY[v], bounds);
        }
        rememberBounds(source, bounds);

        // Auto-place one kit item on each seat via the REAL placement path. Save/restore the hand.
        ItemStack held = player.getMainHandItem().copy();
        int placed = 0;
        int refused = 0;
        Set<String> refusedIds = new LinkedHashSet<>();
        try {
            for (int i = 0; i < n; i++) {
                Item item = kit.get(i);
                for (int v = 0; v < MEGA_ROW_COUNT; v++) {
                    BlockPos seat = seats[i][v];
                    // Where the item lands and which face is clicked. Rows 0-2 seat on the support's
                    // up-face. Row 3 (overhang_and_ceiling) is a HANGING attempt: click the ceiling
                    // block's DOWN face so the item lands in the hang gap through the real hanging path.
                    // An item that refuses to hang leaves that gap empty and free for the maintainer to use.
                    BlockPos clicked;
                    Direction face;
                    BlockPos target;
                    if (v == MEGA_ROW_HANGING) {
                        clicked = seat.above(2); // the ceiling block (g.above(4))
                        face = Direction.DOWN;
                        target = seat.above(1);  // the hang gap (g.above(3))
                    } else {
                        clicked = seat;
                        face = Direction.UP;
                        target = seat.above();
                    }
                    BlockState clickedBefore = world.getBlockState(clicked);
                    boolean targetWasAir = world.getBlockState(target).isAir();
                    placeVia(world, player, item, clicked, face);
                    boolean targetFilled = targetWasAir && !world.getBlockState(target).isAir();
                    // An in-cell change (e.g. the clicked slab became a double slab) also counts as placed.
                    boolean clickedChanged = !world.getBlockState(clicked).equals(clickedBefore);
                    if (targetFilled || clickedChanged) {
                        placed++;
                    } else {
                        refused++;
                        refusedIds.add(BuiltInRegistries.ITEM.getKey(item).getPath());
                    }
                }
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }

        // Self-verify one sample seat per row against its sign's dy claim.
        List<String> mismatches = new ArrayList<>();
        for (int v = 0; v < MEGA_ROW_COUNT; v++) {
            addIfMismatch(mismatches, world, seats[0][v], MEGA_ROW_DY[v], "Row " + v + " (" + MEGA_ROW_NAME[v] + ")");
        }
        if (!mismatches.isEmpty()) {
            source.sendFailure(warn("mega", base, facing, mismatches));
            return 0;
        }

        final int builtColumns = n;
        final int placedFinal = placed;
        final int refusedFinal = refused;
        final String refusedList = summariseIds(refusedIds, MAX_REFUSED_LISTED);
        source.sendSuccess(() -> Component.literal(
                "[slabrig] mega built at " + base.toShortString() + " facing " + facing.getName()
                        + " — " + builtColumns + " columns x " + MEGA_ROW_COUNT + " variants; placed "
                        + placedFinal + ", refused " + refusedFinal + " (of "
                        + (builtColumns * MEGA_ROW_COUNT) + " attempts)"
                        + (refusedList.isEmpty() ? "." : " (refused: " + refusedList + ").")), false);
        return 1;
    }

    /**
     * Builds one variant column's scenery and returns the SEAT surface a subject is placed on. {@code v}
     * selects the variant, {@code i} the column index (spaced along {@code right}); the column's ground
     * cell sits at {@code base + facing*v + right*(i*spacing)}.
     */
    private static BlockPos buildMegaColumn(ServerLevel world, BlockPos base, Direction facing,
                                            Direction right, int v, int i, Bounds bounds) {
        BlockPos g = base.relative(facing, v).relative(right, i * COLUMN_SPACING);
        switch (v) {
            case 0 -> {
                // Bare bottom slab on the ground (a flush slab, seat reads 0.0).
                setStone(world, g, bounds);
                bottomSlab(world, g.above(1), bounds);
                return g.above(1);
            }
            case 1 -> {
                // Bottom slab carrying a full block (the lowered full block, seat reads -0.5).
                setStone(world, g, bounds);
                bottomSlab(world, g.above(1), bounds);
                setStone(world, g.above(2), bounds);
                return g.above(2);
            }
            case 2 -> {
                // The compound column + marked side slab (both read -1.0); the side slab is the seat.
                BlockPos fb = buildCompoundTower(world, g, facing, bounds);
                return fb.relative(facing.getCounterClockWise());
            }
            default -> {
                // Overhang-and-ceiling: a lowered support (seat -0.5) with a full block overhead (a
                // ceiling to hang from) and an open air cell to the side for manual overhang tests.
                setStone(world, g, bounds);
                bottomSlab(world, g.above(1), bounds);
                setStone(world, g.above(2), bounds);
                setStone(world, g.above(4), bounds); // ceiling block; g.above(3) stays air (the hang gap)
                return g.above(2);
            }
        }
    }

    /** The cells {@link #buildMegaColumn} + {@link #mega}'s signs touch, for footprint planning. */
    private static void collectMegaCells(BlockPos base, Direction facing, Direction right, int n,
                                         Set<BlockPos> cells) {
        for (int i = 0; i < n; i++) {
            for (int v = 0; v < MEGA_ROW_COUNT; v++) {
                BlockPos g = base.relative(facing, v).relative(right, i * COLUMN_SPACING);
                switch (v) {
                    case 0 -> {
                        cells.add(g);
                        cells.add(g.above(1));
                    }
                    case 1 -> {
                        cells.add(g);
                        cells.add(g.above(1));
                        cells.add(g.above(2));
                    }
                    case 2 -> collectTowerCells(g, facing, cells);
                    default -> {
                        cells.add(g);
                        cells.add(g.above(1));
                        cells.add(g.above(2));
                        cells.add(g.above(3)); // the hang gap (reserved so nothing intrudes)
                        cells.add(g.above(4)); // ceiling block
                        cells.add(g.above(2).relative(facing)); // the side overhang air cell (reserved)
                    }
                }
            }
        }
        for (int v = 0; v < MEGA_ROW_COUNT; v++) {
            collectSignCells(base.relative(facing, v).relative(right.getOpposite(), COLUMN_SPACING), cells);
        }
    }

    /**
     * Places {@code item} by clicking {@code clicked}'s {@code face} via the real player {@code useOn}
     * path (the same capture/freeze/marker machinery a hand placement runs). Sets the item in the
     * player's hand for the call; the caller restores the original hand afterward. Never throws — if the
     * item refuses, one bad item never aborts the mega build; the caller decides placed/refused by
     * inspecting the world.
     */
    private static void placeVia(ServerLevel world, Player player, Item item, BlockPos clicked, Direction face) {
        try {
            ItemStack stack = new ItemStack(item);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            Vec3 hit = Vec3.atCenterOf(clicked).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(hit, face, clicked, false)));
        } catch (RuntimeException e) {
            // swallow: a refusing item is detected by the caller as an unchanged world.
        }
    }

    /** A comma list of up to {@code cap} ids, with a "+K more" suffix when there are more. */
    private static String summariseIds(Set<String> ids, int cap) {
        if (ids.isEmpty()) {
            return "";
        }
        List<String> shown = new ArrayList<>();
        int extra = 0;
        for (String id : ids) {
            if (shown.size() < cap) {
                shown.add(id);
            } else {
                extra++;
            }
        }
        String joined = String.join(", ", shown);
        return extra > 0 ? joined + ", +" + extra + " more" : joined;
    }

    // ── /slabrig clear ────────────────────────────────────────────────────────

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        RigKey key = keyFor(source);
        Bounds bounds = LAST_BOUNDS.get(key);

        if (bounds == null || bounds.isEmpty()) {
            // Per-dimension records: a rig for this player may live in another dimension — name it
            // rather than silently reporting "nothing to clear".
            Bounds elsewhere = null;
            for (Map.Entry<RigKey, Bounds> e : LAST_BOUNDS.entrySet()) {
                if (e.getKey().player().equals(key.player())) {
                    elsewhere = e.getValue();
                    break;
                }
            }
            if (elsewhere != null && !elsewhere.isEmpty()) {
                final Bounds other = elsewhere;
                source.sendFailure(Component.literal(
                        "[slabrig] your last rig is in " + other.dimensionName()
                                + ", not this dimension — clear it there."));
                return 0;
            }
            source.sendFailure(Component.literal("[slabrig] nothing to clear — build a rig first."));
            return 0;
        }

        LAST_BOUNDS.remove(key);
        int cleared = 0;
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!world.getBlockState(pos).isAir()) {
                        // removeAnchor clears every Slabbed attachment type + the stored-dy entry;
                        // flag-2 setBlock would NOT fire the removal hook, so do it explicitly first.
                        SlabAnchorAttachment.removeAnchor(world, pos);
                        world.setBlock(pos, Blocks.AIR.defaultBlockState(), CLEAR_FLAG);
                        cleared++;
                    }
                }
            }
        }
        final int removed = cleared;
        source.sendSuccess(() -> Component.literal("[slabrig] cleared " + removed + " blocks."), false);
        return 1;
    }

    // ── footprint safety ──────────────────────────────────────────────────────

    /** F6: refuse (with a message) if the rig base would sit below the world's minimum build height. */
    private static boolean belowWorldBottom(CommandSourceStack source, ServerLevel world, BlockPos base) {
        int minY = world.getMinY();
        if (base.getY() < minY) {
            source.sendFailure(Component.literal(
                    "[slabrig] rig base Y=" + base.getY() + " is below the world minimum build height "
                            + minY + " — move up and retry."));
            return true;
        }
        return false;
    }

    /**
     * F2/F3: scan the full footprint box (planned cells + {@link #HEADROOM} above the top). If any cell
     * is non-air and {@code force} is not set, refuse and report the count + a sample position.
     */
    private static boolean refuseIfFootprintOccupied(CommandSourceStack source, ServerLevel world,
                                                     Bounds bounds, boolean force) {
        int occupied = 0;
        BlockPos sample = null;
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!world.getBlockState(pos).isAir()) {
                        occupied++;
                        if (sample == null) {
                            sample = pos;
                        }
                    }
                }
            }
        }
        if (occupied > 0 && !force) {
            final int count = occupied;
            final BlockPos at = sample;
            source.sendFailure(Component.literal(
                    "[slabrig] footprint not empty (" + count + " blocks at " + at.toShortString()
                            + ") — add 'force' to overwrite."));
            return true;
        }
        return false;
    }

    /**
     * Pre-clean EVERY footprint cell's Slabbed attachments before authoring. The RegistrySweep worker
     * proved empirically that flag-2 {@code setBlock} does NOT fire the attachment-removal hook, so a
     * rig authored over a previous rig's stale markers would be "haunted" — its sign would lie about the
     * dy. removeAnchor clears every marker type + the stored-dy entry.
     */
    private static void precleanFootprint(ServerLevel world, Bounds bounds) {
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    SlabAnchorAttachment.removeAnchor(world, new BlockPos(x, y, z));
                }
            }
        }
    }

    /** Inclusive footprint box of {@code cells}, expanded {@link #HEADROOM} cells upward. */
    private static Bounds footprintBounds(Set<BlockPos> cells, ResourceKey<Level> dimension) {
        Bounds bounds = new Bounds(dimension);
        for (BlockPos pos : cells) {
            bounds.include(pos);
        }
        bounds.includeHeadroom(HEADROOM);
        return bounds;
    }

    // ── self-verification (F4) ─────────────────────────────────────────────────

    private static void addIfMismatch(List<String> mismatches, ServerLevel world, BlockPos pos,
                                      double expected, String label) {
        double got = SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
        if (Math.abs(got - expected) > EPS) {
            mismatches.add(label + " @" + pos.toShortString() + ": expected " + expected + " but read " + got);
        }
    }

    private static Component warn(String preset, BlockPos base, Direction facing, List<String> mismatches) {
        return Component.literal(
                "[slabrig] " + preset + " built at " + base.toShortString() + " facing " + facing.getName()
                        + " but it does NOT read what its sign says: " + String.join("; ", mismatches))
                .withStyle(ChatFormatting.RED);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void setStone(ServerLevel world, BlockPos pos, Bounds bounds) {
        world.setBlock(pos, Blocks.STONE.defaultBlockState(), FLAG);
        bounds.include(pos);
    }

    private static void bottomSlab(ServerLevel world, BlockPos pos, Bounds bounds) {
        world.setBlock(pos, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), FLAG);
        bounds.include(pos);
    }

    /** F7: remembers {@code bounds}; if a record already existed for this (player, dimension), warns it is orphaned. */
    private static void rememberBounds(CommandSourceStack source, Bounds bounds) {
        if (bounds.isEmpty()) {
            return;
        }
        RigKey key = keyFor(source);
        Bounds previous = LAST_BOUNDS.get(key);
        if (previous != null && !previous.isEmpty()) {
            final Bounds orphan = previous;
            source.sendSuccess(() -> Component.literal(
                    "[slabrig] previous rig at " + orphan + " is now untracked (clear it manually if needed)."),
                    false);
        }
        LAST_BOUNDS.put(key, bounds);
    }

    private static RigKey keyFor(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        UUID id = player != null ? player.getUUID() : CONSOLE_KEY;
        return new RigKey(id, source.getLevel().dimension());
    }

    /** Growable inclusive AABB of the cells a build touched, tagged with its dimension. */
    private static final class Bounds {
        private final ResourceKey<Level> dimension;
        private boolean empty = true;
        private int minX, minY, minZ, maxX, maxY, maxZ;

        Bounds(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }

        void include(BlockPos pos) {
            if (empty) {
                minX = maxX = pos.getX();
                minY = maxY = pos.getY();
                minZ = maxZ = pos.getZ();
                empty = false;
                return;
            }
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        /** Extends the box {@code cells} layers upward so hand-placed subjects sit inside it. */
        void includeHeadroom(int cells) {
            if (!empty) {
                maxY += cells;
            }
        }

        boolean isEmpty() {
            return empty;
        }

        String dimensionName() {
            return dimension.identifier().toString();
        }

        @Override
        public String toString() {
            if (empty) {
                return "<empty>";
            }
            return "[" + minX + "," + minY + "," + minZ + "]..[" + maxX + "," + maxY + "," + maxZ + "] in "
                    + dimensionName();
        }
    }
}
