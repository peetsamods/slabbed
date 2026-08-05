package com.slabbed.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabTestKit;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /slabrig} — the dev-only scene rig: builds the standard live-test cases on command so a
 * live pass stops depending on hand-built, unreproducible structures.
 *
 * <pre>
 *   /slabrig list           — list the case names
 *   /slabrig build &lt;case&gt;   — build one case in front of the player
 *   /slabrig all            — build the whole catalog in a spaced grid
 *   /slabrig clear          — remove what THIS rig built (never player builds)
 * </pre>
 *
 * <p><b>Stage 1 port of the 26.2 donor's {@code SlabRigCommand}.</b> Deliberately omitted (deferred,
 * not lost): the hanging-direct executor, the status/verified/unresolved/incomplete tracking, the
 * paging / selector_page / route_index / resume machinery, and the console/server-topology
 * reporting. The donor rigs that depend on that machinery ({@code rows}, {@code mega}, {@code
 * stacks}, {@code cases}, {@code catalog}, {@code hangs}, {@code platform}) are not ported here;
 * their geometry survives as named cases where it translated cleanly.
 *
 * <p><b>Case catalog.</b> Four cases reproduce the live-confirmed symptoms recorded in
 * {@code docs/process/LIVE_LEDGER.md} (2026-08-05 pass) and are the reason this rig exists:
 * {@code follower_on_minus_one}, {@code dodo_log_over_slab}, {@code hanging_smoosh},
 * {@code lantern_in_trapdoor}. Three more port donor geometry: {@code seat_ladder} (the mega rig's
 * support-variant rows), {@code overhang_and_ceiling} (mega row 3), {@code tower_alternating}
 * (the deep-stack tower, recipe SBSB).
 *
 * <p><b>Discipline.</b> Every case is PLANNED into a {@link RigPlan} before a single world write;
 * the plan is committed only if every planned cell is currently air, so the rig can never overwrite
 * a player build. {@code clear} removes only cells whose CURRENT state still exactly equals what the
 * rig wrote — anything the player changed is left alone.
 *
 * <p>DEV-ONLY: this whole package is excluded from the release jar (build.gradle's pre-release
 * hygiene gate). The 26.2 line shipped {@code /slabrig} in a release jar and that was logged as a
 * defect; do not reference this class from any shipping code path — {@code Slabbed#initDevFeatures}
 * reaches it reflectively and degrades to a warning when it is absent.
 */
public final class SlabRigCommand {

    private SlabRigCommand() {
    }

    /** Build flag: notify clients, no neighbour cascade while the scene is being authored. */
    private static final int BUILD_FLAG = Block.NOTIFY_LISTENERS;

    /**
     * Clear flag: NOTIFY_ALL (not NOTIFY_LISTENERS) so support-requiring blocks a tester left on
     * the rig get their vanilla survival recheck when the rig is torn down.
     */
    private static final int CLEAR_FLAG = Block.NOTIFY_ALL;

    /** Grid pitch for {@code /slabrig all}; wider than any case footprint so cases cannot interact. */
    private static final int TILE_SPACING = 8;

    /** Tiles per row in the {@code all} grid. */
    private static final int TILES_PER_ROW = 3;

    /** In-memory, server-session-bound record of the last rig each operator built. */
    private static final Map<RigKey, RigPlan> LAST_RIG = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Plan / commit / clear
    // -------------------------------------------------------------------------

    /**
     * An authored-but-not-yet-written scene: ordered cells plus the cells that must carry a Slabbed
     * placement anchor. Insertion order is bottom-up build order; clearing walks it in reverse.
     */
    public static final class RigPlan {
        private final LinkedHashMap<BlockPos, BlockState> cells = new LinkedHashMap<>();
        private final List<BlockPos> anchors = new ArrayList<>();
        private final List<String> caseNames = new ArrayList<>();

        void put(BlockPos pos, BlockState state) {
            cells.put(pos.toImmutable(), state);
        }

        /**
         * Plans {@code state} at {@code pos} and marks the cell as anchored — the placement fact a
         * real click on a lowered surface would have stored. This is what makes a slab-on-slab column
         * read the deeper offset instead of a flat -0.5.
         */
        void putAnchored(BlockPos pos, BlockState state) {
            put(pos, state);
            anchors.add(pos.toImmutable());
        }

        public int size() {
            return cells.size();
        }

        public List<String> caseNames() {
            return List.copyOf(caseNames);
        }

        /** The planned cells in build order — read-only; used by the smoke gametest for bounds checks. */
        public Map<BlockPos, BlockState> cells() {
            return java.util.Collections.unmodifiableMap(cells);
        }

        /** The first planned cell that is not currently air, or {@code null} if the plan is clear to commit. */
        public BlockPos firstObstruction(ServerWorld world) {
            for (BlockPos pos : cells.keySet()) {
                if (!world.getBlockState(pos).isAir()) {
                    return pos;
                }
            }
            return null;
        }

        void commit(ServerWorld world) {
            for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
                world.setBlockState(e.getKey(), e.getValue(), BUILD_FLAG);
            }
            for (BlockPos pos : anchors) {
                SlabAnchorAttachment.addAnchor(world, pos, world.getBlockState(pos));
            }
        }

        void absorb(RigPlan other) {
            cells.putAll(other.cells);
            anchors.addAll(other.anchors);
            caseNames.addAll(other.caseNames);
        }
    }

    /** Outcome of a clear: how many rig cells were removed, and how many were left alone. */
    public record ClearReport(int removed, int keptForeign) {
    }

    /**
     * Removes the rig described by {@code plan}, top-down. A cell is removed ONLY if its current
     * state still exactly equals what the rig wrote there; anything else is a player edit and is
     * counted in {@link ClearReport#keptForeign()} and left untouched.
     */
    public static ClearReport clear(ServerWorld world, RigPlan plan) {
        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(plan.cells.entrySet());
        int removed = 0;
        int kept = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            BlockPos pos = entries.get(i).getKey();
            if (!world.getBlockState(pos).equals(entries.get(i).getValue())) {
                kept++;
                continue;
            }
            SlabAnchorAttachment.removeAnchor(world, pos);
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), CLEAR_FLAG);
            removed++;
        }
        return new ClearReport(removed, kept);
    }

    // -------------------------------------------------------------------------
    // Case catalog
    // -------------------------------------------------------------------------

    /** One named scene. {@code build} authors into a plan; it must never touch the world. */
    public record RigCase(String name, String summary, Consumer<CaseBuilder> build) {
    }

    /** Plan-authoring cursor: everything is relative to the case's own tile origin. */
    public static final class CaseBuilder {
        private final RigPlan plan;
        private final BlockPos origin;

        private CaseBuilder(RigPlan plan, BlockPos origin) {
            this.plan = plan;
            this.origin = origin;
        }

        private BlockPos at(int x, int y, int z) {
            return origin.add(x, y, z);
        }

        void set(int x, int y, int z, BlockState state) {
            plan.put(at(x, y, z), state);
        }

        void setAnchored(int x, int y, int z, BlockState state) {
            plan.putAnchored(at(x, y, z), state);
        }

        /**
         * Ground stone + flush support: a subject at {@code y + 2} reads dy 0.0.
         * Returns the subject's Y.
         */
        int seatFlush(int x, int z) {
            set(x, 0, z, stone());
            set(x, 1, z, stone());
            return 2;
        }

        /**
         * Ground stone + a plain bottom slab: a subject at {@code y + 2} reads dy -0.5.
         * Returns the subject's Y.
         */
        int seatHalf(int x, int z) {
            set(x, 0, z, stone());
            set(x, 1, z, bottomSlab(Blocks.STONE_SLAB));
            return 2;
        }

        /**
         * The -1.0 seat — the LIVE_LEDGER boundary, and the whole reason this rig exists.
         *
         * <p>Vanilla-only geometry (Terrain Slabs crashes bootstrap on this line, so the rig must
         * not depend on it). It is the donor's compound-column shape: a SOURCE column at
         * {@code z + 1} (ground / bottom slab / full block — the full block reads as a lowered
         * side-slab source because it has a bottom slab beneath it) with the SEAT slab beside it at
         * {@code z}. The seat slab is adjacent-side lowered, so it renders -0.5, and a subject
         * standing on it reads -1.0 — exactly the ledger's "dy -1.0 over a -0.5 slab".
         *
         * <p>Do NOT try to build this by anchoring a slab onto a slab: {@code addAnchor} rejects
         * slabs on the direct/column lanes, so that column silently reads -0.5 and the case becomes
         * the wrong scene. {@code SlabRigCatalogSmokeTest#minusOneSeatReallyReadsMinusOne} pins it.
         *
         * <p>Occupies {@code z} and {@code z + 1}. Returns the subject's Y.
         */
        int seatMinusOne(int x, int z) {
            // Source column: its top full block is lowered by the bottom slab under it.
            set(x, 0, z + 1, stone());
            set(x, 1, z + 1, bottomSlab(Blocks.STONE_SLAB));
            set(x, 2, z + 1, stone());
            // Seat column: the slab beside the source renders -0.5; what stands on it reads -1.0.
            set(x, 0, z, stone());
            set(x, 1, z, stone());
            setAnchored(x, 2, z, bottomSlab(Blocks.STONE_SLAB));
            return 3;
        }
    }

    private static BlockState stone() {
        return Blocks.STONE.getDefaultState();
    }

    private static BlockState bottomSlab(Block slab) {
        return slab.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static final LinkedHashMap<String, RigCase> CATALOG = buildCatalog();

    private static LinkedHashMap<String, RigCase> buildCatalog() {
        LinkedHashMap<String, RigCase> cases = new LinkedHashMap<>();

        // LIVE_LEDGER symptom 1 — followers on a -1.0 support stop at -0.5 (the 0.5 gap).
        // Four followers, each on a stripped_jungle_log that itself sits on the -1.0 seat.
        add(cases, "follower_on_minus_one",
                "ledger #1: slab/fence/lantern/sign on a stripped_jungle_log lowered to -1.0 (0.5 gap)",
                b -> {
                    BlockState[] followers = {
                            bottomSlab(Blocks.BIRCH_SLAB),
                            Blocks.BIRCH_FENCE.getDefaultState(),
                            Blocks.LANTERN.getDefaultState(),
                            Blocks.OAK_SIGN.getDefaultState(),
                    };
                    for (int i = 0; i < followers.length; i++) {
                        int x = i * 2;
                        int subjectY = b.seatMinusOne(x, 0);
                        b.set(x, subjectY, 0, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
                        b.set(x, subjectY + 1, 0, followers[i]);
                    }
                });

        // LIVE_LEDGER symptom 2 — DODO (see-through hole) on stripped_jungle_log over birch_slab,
        // both away from and at the -1.0 boundary, plus a stacked-log variant.
        add(cases, "dodo_log_over_slab",
                "ledger #2: stripped_jungle_log over birch_slab, off-boundary / at -1.0 / stacked",
                b -> {
                    // off-boundary: log directly on a plain birch slab (-0.5).
                    b.set(0, 0, 0, stone());
                    b.set(0, 1, 0, bottomSlab(Blocks.BIRCH_SLAB));
                    b.set(0, 2, 0, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
                    // at the -1.0 boundary: log on the compound seat (2 of the 5 flagged live
                    // cells were at the boundary).
                    int boundaryY = b.seatMinusOne(3, 0);
                    b.set(3, boundaryY, 0, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
                    // stacked: two logs over the boundary — the hole reads through the whole stack.
                    int stackY = b.seatMinusOne(6, 0);
                    b.set(6, stackY, 0, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
                    b.set(6, stackY + 1, 0, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
                });

        // LIVE_LEDGER symptom 3 — SMOOSH: subject at dy -1.0 standing on a slab that renders -0.5,
        // so it sinks half a block INTO its own support. Columns 0/2 are the flagged pair
        // (lantern, oak_sign); columns 4/6 are the ceiling-hung comparison over a -0.5 floor.
        add(cases, "hanging_smoosh",
                "ledger #3: lantern + oak_sign at dy -1.0 over a -0.5 slab; hung pair for comparison",
                b -> {
                    int standY = b.seatMinusOne(0, 0);
                    b.set(0, standY, 0, Blocks.LANTERN.getDefaultState());
                    int signY = b.seatMinusOne(2, 0);
                    b.set(2, signY, 0, Blocks.OAK_SIGN.getDefaultState());

                    // Hung comparison: -0.5 floor, air gap, ceiling; lantern/sign hang from the ceiling.
                    for (int x : new int[] {4, 6}) {
                        b.set(x, 0, 0, stone());
                        b.set(x, 1, 0, bottomSlab(Blocks.STONE_SLAB));
                        b.set(x, 3, 0, stone());
                    }
                    b.set(4, 2, 0, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
                    b.set(6, 2, 0, Blocks.OAK_HANGING_SIGN.getDefaultState());
                });

        // LIVE_LEDGER symptom 4 — interpenetration: a lantern rendered inside an oak_trapdoor frame.
        // Two open trapdoors form the frame either side of the lantern cell; the lowered lane is the
        // repro, the flush lane is the control.
        add(cases, "lantern_in_trapdoor",
                "ledger #4: lantern caged by open oak_trapdoors — lowered repro lane + flush control",
                b -> {
                    int loweredY = b.seatMinusOne(1, 0);
                    b.seatMinusOne(0, 0);
                    b.seatMinusOne(2, 0);
                    b.set(1, loweredY, 0, Blocks.LANTERN.getDefaultState());
                    b.set(0, loweredY, 0, openTrapdoor(Direction.EAST));
                    b.set(2, loweredY, 0, openTrapdoor(Direction.WEST));

                    int flushY = b.seatFlush(5, 0);
                    b.seatFlush(4, 0);
                    b.seatFlush(6, 0);
                    b.set(5, flushY, 0, Blocks.LANTERN.getDefaultState());
                    b.set(4, flushY, 0, openTrapdoor(Direction.EAST));
                    b.set(6, flushY, 0, openTrapdoor(Direction.WEST));
                });

        // Donor mega rows 0-2: the three support variants side by side, each carrying the same
        // marker subject, so /slabdy reads a clean 0.0 / -0.5 / -1.0 ladder off one screenshot.
        add(cases, "seat_ladder",
                "donor mega rows 0-2: three seats carrying the same log — expect dy 0.0 / -0.5 / -1.0",
                b -> {
                    b.set(0, b.seatFlush(0, 0), 0, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
                    b.set(2, b.seatHalf(2, 0), 0, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
                    b.set(4, b.seatMinusOne(4, 0), 0, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
                });

        // Donor mega row 3 (overhang_and_ceiling): a lowered seat with a full block overhead and an
        // open air cell beside it, for manual overhang / hang-from-ceiling clicks.
        add(cases, "overhang_and_ceiling",
                "donor mega row 3: lowered seat + ceiling with a hang gap, open cell for overhang clicks",
                b -> {
                    b.set(0, 0, 0, stone());
                    b.set(0, 1, 0, bottomSlab(Blocks.STONE_SLAB));
                    b.set(0, 2, 0, stone());   // the lowered full block (seat reads -0.5)
                    // y=3 stays air: the hang gap.
                    b.set(0, 4, 0, stone());   // ceiling
                    b.set(1, 4, 0, stone());   // ceiling arm over the open cell
                    b.set(1, 0, 0, stone());   // ground under the open overhang cell
                    b.set(2, 0, 0, stone());
                });

        // Donor tower rig, recipe SBSB: a lowered base (seat at index 3) then an alternating
        // slab/block stack, each stacked slab anchored so the chain propagates the way a real
        // click-built tower does.
        add(cases, "tower_alternating",
                "donor tower rig: lowered base + alternating slab/block stack (recipe SBSB)",
                b -> {
                    b.set(0, 0, 0, stone());
                    b.set(0, 1, 0, bottomSlab(Blocks.STONE_SLAB));
                    b.set(0, 2, 0, stone());
                    b.set(0, 3, 0, bottomSlab(Blocks.STONE_SLAB));  // the seat (donor index 3)
                    b.set(0, 4, 0, stone());
                    b.set(0, 5, 0, bottomSlab(Blocks.STONE_SLAB));
                    b.set(0, 6, 0, stone());
                    b.set(0, 7, 0, bottomSlab(Blocks.STONE_SLAB));
                });

        return cases;
    }

    private static BlockState openTrapdoor(Direction leafFace) {
        return Blocks.OAK_TRAPDOOR.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, leafFace)
                .with(Properties.BLOCK_HALF, BlockHalf.BOTTOM)
                .with(Properties.OPEN, true);
    }

    private static void add(LinkedHashMap<String, RigCase> cases, String name, String summary,
                            Consumer<CaseBuilder> build) {
        cases.put(name, new RigCase(name, summary, build));
    }

    // -------------------------------------------------------------------------
    // Public API (also the surface the gametest drives)
    // -------------------------------------------------------------------------

    /** Case names in catalog order. */
    public static List<String> caseNames() {
        return List.copyOf(CATALOG.keySet());
    }

    /** The named case, or {@code null} if unknown. */
    public static RigCase caseByName(String name) {
        return CATALOG.get(name);
    }

    /** Authors {@code name} at {@code origin} into a plan. Returns {@code null} for an unknown case. */
    public static RigPlan planCase(String name, BlockPos origin) {
        RigCase rigCase = CATALOG.get(name);
        if (rigCase == null) {
            return null;
        }
        RigPlan plan = new RigPlan();
        plan.caseNames.add(name);
        rigCase.build().accept(new CaseBuilder(plan, origin));
        return plan;
    }

    /** Authors the whole catalog into one plan, laid out on a {@value #TILE_SPACING}-block grid. */
    public static RigPlan planAll(BlockPos origin) {
        RigPlan combined = new RigPlan();
        int index = 0;
        for (String name : CATALOG.keySet()) {
            BlockPos tile = origin.add((index % TILES_PER_ROW) * TILE_SPACING, 0,
                    (index / TILES_PER_ROW) * TILE_SPACING);
            combined.absorb(planCase(name, tile));
            index++;
        }
        return combined;
    }

    /**
     * Plans then commits {@code name} at {@code origin}. Returns the committed plan, or
     * {@code null} if the case is unknown or its footprint is obstructed (nothing is written).
     */
    public static RigPlan buildCase(ServerWorld world, BlockPos origin, String name) {
        RigPlan plan = planCase(name, origin);
        if (plan == null || plan.firstObstruction(world) != null) {
            return null;
        }
        plan.commit(world);
        return plan;
    }

    // -------------------------------------------------------------------------
    // Command registration — mirrors SlabbedDevCommands / SlabbedLab (op level GAMEMASTERS,
    // dev-environment-gated by Slabbed#initDevFeatures).
    // -------------------------------------------------------------------------

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerAll(dispatcher));
    }

    private static void registerAll(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                literal("slabrig")
                        .requires(src -> src.getPermissions().hasPermission(
                                new Permission.Level(PermissionLevel.GAMEMASTERS)))
                        .executes(SlabRigCommand::listCases)
                        .then(literal("list").executes(SlabRigCommand::listCases))
                        .then(literal("all").executes(SlabRigCommand::buildAll))
                        .then(literal("clear").executes(SlabRigCommand::clearRig))
                        .then(literal("build")
                                .then(argument("case", StringArgumentType.word())
                                        .suggests((ctx, builder) ->
                                                CommandSource.suggestMatching(CATALOG.keySet(), builder))
                                        .executes(SlabRigCommand::buildOne)))
        );
    }

    /** Rig origin: 1 below the operator's feet, 3 blocks out in +Z — the SlabbedLab idiom. */
    private static BlockPos rigOrigin(ServerCommandSource source) {
        return BlockPos.ofFloored(source.getPosition()).add(0, -1, 3);
    }

    private static int listCases(CommandContext<ServerCommandSource> ctx) {
        StringBuilder msg = new StringBuilder("[slabrig] ")
                .append(CATALOG.size()).append(" cases (dev-only; test kit: ")
                .append(SlabTestKit.placeableItems().size()).append(" placeable items)\n");
        for (RigCase rigCase : CATALOG.values()) {
            msg.append("  ").append(rigCase.name()).append(" — ").append(rigCase.summary()).append('\n');
        }
        String finalMsg = msg.toString().stripTrailing();
        ctx.getSource().sendFeedback(() -> Text.literal(finalMsg), false);
        return 1;
    }

    private static int buildOne(CommandContext<ServerCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "case");
        if (!CATALOG.containsKey(name)) {
            ctx.getSource().sendError(Text.literal("[slabrig] unknown case '" + name
                    + "'. Try /slabrig list."));
            return 0;
        }
        return commit(ctx, planCase(name, rigOrigin(ctx.getSource())), "build " + name);
    }

    private static int buildAll(CommandContext<ServerCommandSource> ctx) {
        return commit(ctx, planAll(rigOrigin(ctx.getSource())), "all");
    }

    /** Shared commit path: refuse before any world mutation if the footprint is not clear. */
    private static int commit(CommandContext<ServerCommandSource> ctx, RigPlan plan, String label) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();

        BlockPos obstruction = plan.firstObstruction(world);
        if (obstruction != null) {
            source.sendError(Text.literal("[slabrig] " + label + " refused before any world change: "
                    + "footprint occupied at " + obstruction.toShortString()
                    + ". Move, or /slabrig clear first."));
            return 0;
        }

        plan.commit(world);
        LAST_RIG.put(rigKey(source), plan);

        int cells = plan.size();
        List<String> names = plan.caseNames();
        source.sendFeedback(() -> Text.literal("[slabrig] " + label + " built (dev-only): "
                + cells + " cells, " + names.size() + " case(s) " + names
                + ". /slabrig clear removes exactly these."), false);
        return 1;
    }

    private static int clearRig(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        RigPlan plan = LAST_RIG.remove(rigKey(source));
        if (plan == null) {
            source.sendError(Text.literal("[slabrig] nothing to clear — no rig built in this "
                    + "server session/dimension. (The rig never removes blocks it did not place.)"));
            return 0;
        }

        ClearReport report = clear(source.getWorld(), plan);
        source.sendFeedback(() -> Text.literal("[slabrig] cleared (dev-only): "
                + report.removed() + " rig cells removed, " + report.keptForeign()
                + " left alone (changed since the rig was built — treated as player builds)."), false);
        return 1;
    }

    /**
     * Identity of a remembered rig. The actual server object is part of the key (by identity), so a
     * fresh integrated-server world can never inherit a previous world's manifest just because the
     * player UUID and dimension id match.
     */
    private record RigKey(MinecraftServer server, UUID player, RegistryKey<World> dimension) {
    }

    private static final UUID CONSOLE_KEY = new UUID(0L, 0L);

    private static RigKey rigKey(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        return new RigKey(source.getServer(),
                player == null ? CONSOLE_KEY : player.getUuid(),
                source.getWorld().getRegistryKey());
    }
}
