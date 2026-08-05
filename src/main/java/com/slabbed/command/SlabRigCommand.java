package com.slabbed.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabTestKit;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *   /slabrig mega [n] [force]  — THE everything-rig: n kit columns x 4 support variants
 *   /slabrig rows [n] [force]  — n bare -0.5 seats + n bare -1.0 seats, for hand-placing
 *   /slabrig clear          — remove what THIS rig built (never player builds)
 * </pre>
 *
 * <p><b>Stage 1 + Stage 2 port of the 26.2 donor's {@code SlabRigCommand}.</b> Deliberately omitted
 * (deferred, not lost): the hanging-direct executor, the status/verified/unresolved/incomplete
 * tracking, the paging / selector_page / route_index / resume machinery, the {@code catalog}
 * manifest writer, {@code stacks}, {@code cases}, {@code platform}, and the console/server-topology
 * reporting. Stage 2 adds {@code mega} and {@code rows} — the two donor rigs that actually CONSUME
 * {@link SlabTestKit}'s object palette, which is why Stage 1's board looked "meager".
 *
 * <p><b>Stage 2 deviations from the donor</b> (this line has no equivalent, see HANDOFF.md):
 * <ul>
 *   <li>The donor auto-places kit items through a proxy player's real {@code useOn}. This line has
 *       no proxy-player / diagnostics-bridge machinery, so subjects are AUTHORED as block states
 *       and then run through {@link SlabAnchorAttachment#addAnchor} — the same call a real click
 *       makes in {@code onPlaced}, which self-gates on its qualifier lanes. Items the world refuses
 *       at the seat ({@link BlockState#canPlaceAt}) are dropped and named in chat.</li>
 *   <li>The donor's {@code MEGA_ROW_DY} is the dy of the SEAT cell, because its compound recipe uses
 *       {@code addCompoundVisibleSideLowerSlab} (absent here). This line's seat vocabulary is
 *       SUBJECT-dy based ({@link CaseBuilder#seatFlush} / {@link CaseBuilder#seatHalf} /
 *       {@link CaseBuilder#seatMinusOne}), and it lands on the donor's array VALUES exactly:
 *       {@code {0.0, -0.5, -1.0, -0.5}}. So here the array is the expected dy of each row's
 *       SUBJECT.</li>
 * </ul>
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

    /** The mega rig's support variants, one row each (see {@link #planSeat}). */
    private static final int MEGA_ROW_COUNT = 4;

    /** Row 3 — the donor's {@code overhang_and_ceiling}: its subject cell is capped by a ceiling. */
    private static final int MEGA_ROW_HANGING = 3;

    /**
     * Expected dy of each mega row's SUBJECT cell, in row order — the donor's values verbatim, read
     * with this line's subject-dy seat vocabulary (see the class javadoc's deviation note). This is
     * what the rig self-verifies against after building: a rig that does not measure what its own
     * signs claim warns in red instead of reporting success.
     */
    private static final double[] MEGA_ROW_DY = {0.0, -0.5, -1.0, -0.5};

    /** Display names for the four mega rows, in row order. */
    private static final String[] MEGA_ROW_NAME = {
            "flush", "lowered slab", "compound column", "overhang_and_ceiling"};

    /** Horizontal pitch between rig columns — one free cell between columns. */
    private static final int COLUMN_SPACING = 2;

    /** Depth pitch between mega variant rows; row 2's compound recipe needs two cells of depth. */
    private static final int ROW_SPACING = 2;

    /** Column 0 is the labelled sign pedestal; column 1 is the self-verified reference column. */
    private static final int FIRST_KIT_COLUMN = 2;

    /** Default kit-column count for {@code /slabrig mega} (capped by the placeable-kit size). */
    private static final int DEFAULT_MEGA_COLUMNS = 40;

    /** Default column count for {@code /slabrig rows}. */
    private static final int DEFAULT_ROWS = 16;

    /** Grammar bound for the {@code [n]} argument of {@code mega} / {@code rows}. */
    private static final int MAX_RIG_COLUMNS = 64;

    /** Cap on the refused-id list echoed to chat (donor {@code MAX_REFUSED_LISTED}). */
    private static final int MAX_REFUSED_LISTED = 15;

    /** Tolerance for the self-verification dy comparison. */
    private static final double EPS = 1.0e-6;

    /**
     * Sign rotation 8 = front face pointing north (-Z). The rig grows +Z away from the operator
     * ({@link #rigOrigin}), so 8 is what a sign placed by a player standing at the origin would get.
     */
    private static final int SIGN_FACES_OPERATOR = 8;

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
        /** Sign pedestals: three lines of text applied to the block entity after the cell is written. */
        private final LinkedHashMap<BlockPos, String[]> signs = new LinkedHashMap<>();
        /** Post-commit dy expectations — the self-verification the donor's {@code warn(...)} reports. */
        private final List<DyCheck> checks = new ArrayList<>();
        /** Cells that are dropped at commit when the world refuses the state there. */
        private final Set<BlockPos> conditional = new LinkedHashSet<>();
        /** The reportable subset of {@link #conditional}: kit objects, by id and row. */
        private final LinkedHashMap<BlockPos, Subject> subjects = new LinkedHashMap<>();
        /** Refusals already known at plan time (an item that is not authorable at all). */
        private final List<Subject> planTimeRefusals = new ArrayList<>();
        /** Row labels in display order, so an empty row still shows up in the tally. */
        private final List<String> rowOrder = new ArrayList<>();

        void put(BlockPos pos, BlockState state) {
            cells.put(pos.toImmutable(), state);
        }

        /** Plans a labelled standing sign at {@code pos} (the pedestal below it is the caller's job). */
        void sign(BlockPos pos, String l0, String l1, String l2) {
            put(pos, Blocks.OAK_SIGN.getDefaultState()
                    .with(Properties.ROTATION, SIGN_FACES_OPERATOR));
            signs.put(pos.toImmutable(), new String[] {l0, l1, l2});
        }

        /**
         * Plans a kit object on a seat. The cell is CONDITIONAL — if the world refuses the state
         * there ({@link BlockState#canPlaceAt}) it is dropped at commit and reported by id, so the
         * rig never leaves a half-placed subject or silently swallows a category.
         */
        void putSubject(BlockPos pos, BlockState state, String id, String row) {
            putAnchored(pos, state);
            conditional.add(pos.toImmutable());
            subjects.put(pos.toImmutable(), new Subject(id, row));
        }

        /** A companion cell of a subject (a door's upper half): dropped with it, never counted. */
        void putCompanion(BlockPos pos, BlockState state) {
            put(pos, state);
            conditional.add(pos.toImmutable());
        }

        /** Records an item that cannot be authored at all — reported, never silently skipped. */
        void refuseUpfront(String id, String row) {
            planTimeRefusals.add(new Subject(id, row));
        }

        void check(BlockPos pos, double expected, String label) {
            checks.add(new DyCheck(pos.toImmutable(), expected, label));
        }

        /** The post-commit dy expectations, in row order. Read by the smoke gametest. */
        public List<DyCheck> checks() {
            return List.copyOf(checks);
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

        /**
         * Writes the plan bottom-up, dropping any conditional cell the world refuses at its
         * position, then anchors (the same {@link SlabAnchorAttachment#addAnchor} a real click
         * makes — it self-gates on its qualifier lanes) and finally the sign text.
         *
         * <p>Dropped cells are removed from the plan, so {@code clear} and the gametest's
         * "every planned cell landed" check both stay exact.
         */
        CommitReport commit(ServerWorld world) {
            LinkedHashMap<String, int[]> tally = new LinkedHashMap<>();
            for (String row : rowOrder) {
                tally.put(row, new int[2]);
            }
            LinkedHashSet<String> refusedIds = new LinkedHashSet<>();
            for (Subject refused : planTimeRefusals) {
                tally.computeIfAbsent(refused.row(), k -> new int[2])[1]++;
                refusedIds.add(refused.id());
            }

            List<BlockPos> dropped = new ArrayList<>();
            for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
                BlockPos pos = e.getKey();
                BlockState state = e.getValue();
                Subject subject = subjects.get(pos);
                if (conditional.contains(pos) && !state.canPlaceAt(world, pos)) {
                    dropped.add(pos);
                    if (subject != null) {
                        tally.computeIfAbsent(subject.row(), k -> new int[2])[1]++;
                        refusedIds.add(subject.id());
                    }
                    continue;
                }
                world.setBlockState(pos, state, BUILD_FLAG);
                if (subject != null) {
                    tally.computeIfAbsent(subject.row(), k -> new int[2])[0]++;
                }
            }
            for (BlockPos pos : dropped) {
                cells.remove(pos);
                conditional.remove(pos);
                subjects.remove(pos);
            }
            anchors.removeIf(pos -> !cells.containsKey(pos));
            checks.removeIf(check -> !cells.containsKey(check.pos()));

            for (BlockPos pos : anchors) {
                SlabAnchorAttachment.addAnchor(world, pos, world.getBlockState(pos));
            }
            // NEVER-POP AUTHORING (LIVE_LEDGER 2026-08-05 second pass, "popping in the back
            // row"): a real click runs BlockOnPlacedAnchorMixin.onPlaced = addAnchor THEN
            // freezeLoweredOnPlace. Rig subjects got only the addAnchor half (the loop above),
            // so a FLAT-placed subject recorded neither an anchor (the qualifier lanes only
            // accept lowered placements) nor the FROZEN_FLAT marker — it kept re-deriving live
            // and popped on neighbour change. Complete the pair through the SAME entry point a
            // click uses (no new lane): freezeLoweredOnPlace self-gates on structural/decorative
            // semantics and no-ops for already-anchored or lowered cells.
            for (BlockPos pos : subjects.keySet()) {
                SlabAnchorAttachment.freezeLoweredOnPlace(world, pos, world.getBlockState(pos));
            }
            for (Map.Entry<BlockPos, String[]> e : signs.entrySet()) {
                writeSign(world, e.getKey(), e.getValue());
            }

            List<RowTally> rows = new ArrayList<>();
            for (Map.Entry<String, int[]> e : tally.entrySet()) {
                rows.add(new RowTally(e.getKey(), e.getValue()[0], e.getValue()[1]));
            }
            return new CommitReport(cells.size(), rows, List.copyOf(refusedIds));
        }

        void absorb(RigPlan other) {
            cells.putAll(other.cells);
            anchors.addAll(other.anchors);
            caseNames.addAll(other.caseNames);
            signs.putAll(other.signs);
            checks.addAll(other.checks);
            conditional.addAll(other.conditional);
            subjects.putAll(other.subjects);
            planTimeRefusals.addAll(other.planTimeRefusals);
            rowOrder.addAll(other.rowOrder);
        }
    }

    /** A kit object authored on a seat, identified for the refusal report. */
    private record Subject(String id, String row) {
    }

    /** A post-commit dy expectation: what {@code pos} must measure, and what to call it in chat. */
    public record DyCheck(BlockPos pos, double expected, String label) {
    }

    /** Per-row placed/refused counts for one commit. */
    public record RowTally(String row, int placed, int refused) {
    }

    /** What a commit actually wrote: surviving cell count, per-row tallies, refused kit ids. */
    public record CommitReport(int cells, List<RowTally> rows, List<String> refusedIds) {
    }

    /** Applies three lines of sign text to the block entity a sign cell just created. */
    private static void writeSign(ServerWorld world, BlockPos pos, String[] lines) {
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SignBlockEntity sign)) {
            return;
        }
        sign.changeText(text -> text
                .withMessage(0, Text.literal(lines[0]))
                .withMessage(1, Text.literal(lines[1]))
                .withMessage(2, Text.literal(lines[2])), true);
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
            // y1 stays AIR under the seat slab (the donor's geometry): the seat is a legitimate
            // cantilever whose -0.5 destination volume is free. The recipe used to invent a flush
            // stone at y1 here — the seat slab then sank half a block INSIDE it, which is the mega
            // board's z=14 interpenetration row (LIVE_LEDGER 2026-08-05 second pass), and the
            // flush-seat guard now rightly refuses that shape entirely.
            set(x, 0, z, stone());
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
    // Stage 2 — the everything-rig (donor `mega`) and the bare seat board (donor `rows`)
    // -------------------------------------------------------------------------

    /** A committed Stage 2 rig: the plan (refusals already pruned) and what the commit wrote. */
    public record BuiltRig(RigPlan plan, CommitReport report) {
    }

    /**
     * Plans then commits {@code mega} at {@code origin}. Returns {@code null} if the footprint is
     * obstructed (nothing is written). Visible for the smoke gametest.
     */
    public static BuiltRig buildMega(ServerWorld world, BlockPos origin, int columns) {
        return build(world, planMega(origin, columns));
    }

    /**
     * Plans then commits {@code rows} at {@code origin}. Returns {@code null} if the footprint is
     * obstructed (nothing is written). Visible for the smoke gametest.
     */
    public static BuiltRig buildRows(ServerWorld world, BlockPos origin, int count) {
        return build(world, planRows(origin, count));
    }

    private static BuiltRig build(ServerWorld world, RigPlan plan) {
        if (plan.firstObstruction(world) != null) {
            return null;
        }
        return new BuiltRig(plan, plan.commit(world));
    }

    /**
     * Builds support variant {@code v}'s scenery at column {@code x} / row depth {@code z} and
     * returns the SUBJECT cell — the position a kit object (or the reference marker) is authored at.
     *
     * <p>Variant geometry, all vanilla-only (Terrain Slabs crashes bootstrap on this line, so the
     * rig must never depend on it):
     * <ol start="0">
     *   <li><b>flush</b> — ground stone + stone. Subject reads {@code 0.0}: the control lane.</li>
     *   <li><b>lowered slab</b> — ground stone + a plain bottom slab. Subject reads {@code -0.5}.</li>
     *   <li><b>compound column</b> — the donor's compound recipe: a SOURCE column at {@code z + 1}
     *       (stone / bottom slab / stone) whose lowered full block drags the SEAT slab beside it to
     *       {@code -0.5}, so what stands on the seat reads {@code -1.0}. Do NOT try to build this by
     *       anchoring a slab onto a slab — {@code addAnchor} rejects slabs on the direct/column
     *       lanes, so that column silently reads {@code -0.5} and the whole row becomes the wrong
     *       scene. Occupies {@code z} and {@code z + 1}.</li>
     *   <li><b>overhang_and_ceiling</b> — a lowered {@code -0.5} seat with a CEILING one cell above
     *       the subject, so ceiling-attached objects (hanging signs, hung lanterns) actually pass
     *       {@code canPlaceAt} here and nowhere else; plus a ceiling arm and ground at {@code x + 1}
     *       leaving an open column there for manual overhang clicks.</li>
     * </ol>
     */
    private static BlockPos planSeat(RigPlan plan, BlockPos origin, int x, int z, int v) {
        switch (v) {
            case 0 -> {
                plan.put(origin.add(x, 0, z), stone());
                plan.put(origin.add(x, 1, z), stone());
                return origin.add(x, 2, z);
            }
            case 1 -> {
                plan.put(origin.add(x, 0, z), stone());
                plan.put(origin.add(x, 1, z), bottomSlab(Blocks.STONE_SLAB));
                return origin.add(x, 2, z);
            }
            case 2 -> {
                plan.put(origin.add(x, 0, z + 1), stone());
                plan.put(origin.add(x, 1, z + 1), bottomSlab(Blocks.STONE_SLAB));
                plan.put(origin.add(x, 2, z + 1), stone());
                // y1 stays AIR under the seat slab (the donor's geometry — see seatMinusOne):
                // the invented flush stone here was the mega board's z=14 interpenetration row.
                plan.put(origin.add(x, 0, z), stone());
                plan.putAnchored(origin.add(x, 2, z), bottomSlab(Blocks.STONE_SLAB));
                return origin.add(x, 3, z);
            }
            case MEGA_ROW_HANGING -> {
                plan.put(origin.add(x, 0, z), stone());
                plan.put(origin.add(x, 1, z), bottomSlab(Blocks.STONE_SLAB));
                plan.put(origin.add(x, 2, z), stone());
                // Ceiling BEFORE the subject: canPlaceAt for a ceiling-hung object is evaluated in
                // plan order at commit, so the ceiling has to already be there.
                plan.put(origin.add(x, 4, z), stone());
                plan.put(origin.add(x + 1, 0, z), stone());
                plan.put(origin.add(x + 1, 4, z), stone());
                return origin.add(x, 3, z);
            }
            default -> throw new IllegalArgumentException("no such rig support variant: " + v);
        }
    }

    /**
     * The state a kit {@link Item} is authored as, or {@code null} when the entry cannot be authored
     * as single-column scenery at all (the refusal is reported by id, never silently skipped).
     *
     * <p>Public because {@code SlabRigCatalogSmokeTest} censuses the kit against it. DEV-ONLY, like
     * the rest of this class.
     */
    public static BlockState kitSubjectState(Item item) {
        Block block = Block.getBlockFromItem(item);
        if (block == Blocks.AIR) {
            return null;   // not a block item at all (e.g. powder_snow_bucket)
        }
        if (block instanceof BedBlock) {
            return null;   // two-cell HORIZONTAL subject: its head would land in the next column
        }
        BlockState state = block.getDefaultState();
        if (state.contains(Properties.WATERLOGGED)) {
            // KNOWN_INCOMPLETE 1i: conduit's DEFAULT state is waterlogged=true, so the rig was
            // authoring a waterlogged block into a DRY cell with no fluid cascade (BUILD_FLAG is
            // NOTIFY_LISTENERS) — Maintainer's "water won't envelop conduits" — and its non-empty
            // FluidState makes every anchor qualifier reject it. The dry board authors DRY
            // states; 1i's product-side question (anchoring waterlogged blocks) stays open.
            state = state.with(Properties.WATERLOGGED, false);
        }
        if (state.contains(Properties.BLOCK_FACE)) {
            // Buttons / levers default to a WALL mount; there is no wall on a seat, so stand them up.
            state = state.with(Properties.BLOCK_FACE, BlockFace.FLOOR);
        }
        if (state.contains(Properties.HORIZONTAL_FACING)) {
            state = state.with(Properties.HORIZONTAL_FACING, Direction.NORTH);
        }
        if (state.contains(Properties.ROTATION)) {
            state = state.with(Properties.ROTATION, SIGN_FACES_OPERATOR);
        }
        return state;
    }

    /** Authors {@code state} (plus a door's upper half) as the subject on {@code subject}. */
    private static void planSubject(RigPlan plan, BlockPos subject, BlockState state, String id, String row) {
        if (state.getBlock() instanceof DoorBlock) {
            BlockPos upper = subject.up();
            if (plan.cells.containsKey(upper)) {
                // Row 3's ceiling already owns that cell — a genuine per-variant refusal.
                plan.refuseUpfront(id, row);
                return;
            }
            plan.putSubject(subject, state.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
                    id, row);
            plan.putCompanion(upper, state.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
            return;
        }
        plan.putSubject(subject, state, id, row);
    }

    /**
     * THE mega rig: {@code columns} kit columns crossed with the four support variants, one row each.
     * Each row is laid out along {@code +X}: column 0 is a labelled sign pedestal, column 1 is the
     * REFERENCE column (a {@code stripped_jungle_log} marker whose dy the rig self-verifies against
     * {@link #MEGA_ROW_DY}), and every column after that carries the next entry of
     * {@link SlabTestKit#placeableItems()}. Rows march along {@code +Z}.
     *
     * <p>World-free, like every other planner here: the plan is authored first and committed only
     * once its footprint is known clear, so the rig can never overwrite a player build.
     */
    public static RigPlan planMega(BlockPos origin, int columns) {
        RigPlan plan = new RigPlan();
        plan.caseNames.add("mega");
        List<Item> kit = SlabTestKit.placeableItems();
        int n = Math.max(0, Math.min(columns, kit.size()));

        for (int v = 0; v < MEGA_ROW_COUNT; v++) {
            int z = v * ROW_SPACING;
            String row = "row " + v + " " + MEGA_ROW_NAME[v];
            plan.rowOrder.add(row);

            plan.put(origin.add(0, 0, z), stone());
            plan.sign(origin.add(0, 1, z), "Row " + v, MEGA_ROW_NAME[v], "dy " + MEGA_ROW_DY[v]);

            BlockPos reference = planSeat(plan, origin, COLUMN_SPACING, z, v);
            plan.putAnchored(reference, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
            plan.check(reference, MEGA_ROW_DY[v], row);

            for (int i = 0; i < n; i++) {
                Item item = kit.get(i);
                String id = Registries.ITEM.getId(item).getPath();
                BlockPos subject = planSeat(plan, origin, (i + FIRST_KIT_COLUMN) * COLUMN_SPACING, z, v);
                BlockState state = kitSubjectState(item);
                if (state == null) {
                    plan.refuseUpfront(id, row);
                    continue;
                }
                planSubject(plan, subject, state, id, row);
            }
        }
        return plan;
    }

    /**
     * The donor's simpler per-row rig: {@code count} BARE {@code -0.5} seats (row A) and
     * {@code count} BARE {@code -1.0} seats (row B), each row labelled by a sign and fronted by one
     * self-verified reference marker. Nothing is placed on the seats — this is the board Maintainer
     * hand-places onto, which is exactly what {@code mega} is not.
     */
    public static RigPlan planRows(BlockPos origin, int count) {
        RigPlan plan = new RigPlan();
        plan.caseNames.add("rows");
        int n = Math.max(1, Math.min(count, MAX_RIG_COLUMNS));

        int[] variants = {1, 2};
        int z = 0;
        for (int r = 0; r < variants.length; r++) {
            int v = variants[r];
            String label = "row " + (char) ('A' + r);
            String row = label + " " + MEGA_ROW_NAME[v];
            plan.rowOrder.add(row);

            plan.put(origin.add(0, 0, z), stone());
            plan.sign(origin.add(0, 1, z), "Row " + (char) ('A' + r),
                    MEGA_ROW_NAME[v], "dy " + MEGA_ROW_DY[v]);

            BlockPos reference = planSeat(plan, origin, COLUMN_SPACING, z, v);
            plan.putAnchored(reference, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
            plan.check(reference, MEGA_ROW_DY[v], row);

            for (int i = 0; i < n; i++) {
                planSeat(plan, origin, (i + FIRST_KIT_COLUMN) * COLUMN_SPACING, z, v);
            }
            // Variant 2 owns two cells of depth; everything else owns one.
            z += v == 2 ? ROW_SPACING + 1 : ROW_SPACING;
        }
        return plan;
    }

    /**
     * Post-commit self-verification (donor {@code addIfMismatch} + {@code warn}): what the rig
     * actually MEASURES at each reference marker vs what the row's sign claims. This is what turns a
     * built scene into evidence instead of scenery — a mismatch is a real regression, never a reason
     * to relax the expectation.
     */
    public static List<String> verify(ServerWorld world, RigPlan plan) {
        List<String> mismatches = new ArrayList<>();
        for (DyCheck check : plan.checks) {
            BlockState state = world.getBlockState(check.pos());
            double got = SlabSupport.getYOffset(world, check.pos(), state);
            if (Math.abs(got - check.expected()) > EPS) {
                mismatches.add(check.label() + " @" + check.pos().toShortString()
                        + ": sign says dy " + check.expected() + " but it measures " + got);
            }
        }
        // SEAT-SANITY (LIVE_LEDGER 2026-08-05 second pass, "interpenetration row"): NO rig-
        // authored cell may resolve a dy that puts its occupied volume inside its direct
        // support's occupied volume. This is the rule that would have caught the recipe's
        // invented flush stone under the z=14 seat slab before it ever reached a live board.
        for (BlockPos pos : plan.cells.keySet()) {
            String violation = seatSanityViolation(world, pos);
            if (violation != null) {
                mismatches.add(violation);
            }
        }
        return mismatches;
    }

    /**
     * Seat-sanity for one rig cell: {@code null} when clean, else a human-readable violation. A
     * cell's occupied bottom (its own Y plus resolved dy; a TOP slab's occupied volume starts
     * half a block up) may never sit below its direct support's occupied top (the support's Y
     * plus its resolved dy plus its occupied height — 1.0 for solid full blocks and TOP/DOUBLE
     * slabs, 0.5 for a bottom slab). Supports with no meaningful standing volume (air, signs,
     * other decor) impose no constraint.
     */
    private static String seatSanityViolation(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return null;
        }
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy >= -EPS) {
            return null;   // a flush or raised cell cannot sink into the cell below
        }
        BlockPos supportPos = pos.down();
        BlockState support = world.getBlockState(supportPos);
        Double supportHeight = occupiedHeight(world, supportPos, support);
        if (supportHeight == null) {
            return null;
        }
        double supportTop = supportPos.getY() + supportHeight
                + SlabSupport.getYOffset(world, supportPos, support);
        double cellBottom = pos.getY() + dy + (SlabSupport.isTopSlab(state) ? 0.5 : 0.0);
        if (cellBottom < supportTop - EPS) {
            return "seat-sanity @" + pos.toShortString() + ": " + state.getBlock().getName().getString()
                    + " resolves dy " + dy + " with occupied bottom " + cellBottom
                    + " INSIDE its direct support (occupied top " + supportTop
                    + ") — interpenetration";
        }
        return null;
    }

    /** Occupied height of a support above its own cell floor, or {@code null} for no standing volume. */
    private static Double occupiedHeight(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return null;
        }
        if (state.getBlock() instanceof SlabBlock && state.contains(SlabBlock.TYPE)) {
            return state.get(SlabBlock.TYPE) == SlabType.BOTTOM ? 0.5 : 1.0;
        }
        return state.isSolidBlock(world, pos) ? 1.0 : null;
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
                        .then(countedRig("mega", DEFAULT_MEGA_COLUMNS,
                                (ctx, n, force) -> commit(ctx, planMega(rigOrigin(ctx.getSource()), n),
                                        "mega", force)))
                        .then(countedRig("rows", DEFAULT_ROWS,
                                (ctx, n, force) -> commit(ctx, planRows(rigOrigin(ctx.getSource()), n),
                                        "rows", force)))
        );
    }

    /** {@code (ctx, n, force)} — the shape both Stage 2 rigs are driven by. */
    private interface CountedRig {
        int run(CommandContext<ServerCommandSource> ctx, int count, boolean force);
    }

    /** Builds the donor's {@code <name> [n] [force]} sub-tree once, for both {@code mega} and {@code rows}. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> countedRig(
            String name, int defaultCount, CountedRig rig) {
        return literal(name)
                .executes(ctx -> rig.run(ctx, defaultCount, false))
                .then(literal("force").executes(ctx -> rig.run(ctx, defaultCount, true)))
                .then(argument("n", IntegerArgumentType.integer(1, MAX_RIG_COLUMNS))
                        .executes(ctx -> rig.run(ctx, IntegerArgumentType.getInteger(ctx, "n"), false))
                        .then(literal("force").executes(ctx ->
                                rig.run(ctx, IntegerArgumentType.getInteger(ctx, "n"), true))));
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
        msg.append("  /slabrig mega [n] [force] — the everything-rig: n kit columns x ")
                .append(MEGA_ROW_COUNT).append(" support variants (")
                .append(String.join(" / ", MEGA_ROW_NAME)).append("), default n=")
                .append(Math.min(DEFAULT_MEGA_COLUMNS, SlabTestKit.placeableItems().size()))
                .append("; self-verifies each row against dy ")
                .append(java.util.Arrays.toString(MEGA_ROW_DY)).append('\n');
        msg.append("  /slabrig rows [n] [force] — n BARE -0.5 seats + n BARE -1.0 seats to hand-place "
                + "onto, default n=").append(DEFAULT_ROWS).append('\n');
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
        return commit(ctx, plan, label, false);
    }

    /**
     * Shared commit path. Refuses before any world mutation if the footprint is not clear, unless
     * {@code force} — the donor's escape hatch, which overwrites whatever is there. After the write
     * it reports the per-row placed/refused tally and the refused kit ids, then SELF-VERIFIES every
     * reference marker against its row's sign and warns in red on any mismatch.
     */
    private static int commit(CommandContext<ServerCommandSource> ctx, RigPlan plan, String label,
                              boolean force) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();

        BlockPos obstruction = plan.firstObstruction(world);
        if (obstruction != null && !force) {
            source.sendError(Text.literal("[slabrig] " + label + " refused before any world change: "
                    + "footprint occupied at " + obstruction.toShortString()
                    + ". Move, /slabrig clear first, or /slabrig " + label + " force."));
            return 0;
        }
        final BlockPos forced = obstruction;

        CommitReport report = plan.commit(world);
        LAST_RIG.put(rigKey(source), plan);

        List<String> names = plan.caseNames();
        StringBuilder msg = new StringBuilder("[slabrig] ").append(label)
                .append(" built (dev-only): ").append(report.cells()).append(" cells, ")
                .append(names.size()).append(" case(s) ").append(names)
                .append(". /slabrig clear removes exactly these.");
        if (forced != null) {
            msg.append("\n  FORCED over an occupied footprint (first at ").append(forced.toShortString())
                    .append(") — anything it overwrote is now rig-owned and /slabrig clear will take it.");
        }
        for (RowTally row : report.rows()) {
            if (row.placed() == 0 && row.refused() == 0) {
                continue;
            }
            msg.append("\n  ").append(row.row()).append(": placed ").append(row.placed())
                    .append(", refused ").append(row.refused());
        }
        if (!report.refusedIds().isEmpty()) {
            msg.append("\n  refused ids: ").append(summariseIds(report.refusedIds(), MAX_REFUSED_LISTED));
        }
        String summary = msg.toString();
        source.sendFeedback(() -> Text.literal(summary), false);

        List<String> mismatches = verify(world, plan);
        if (!mismatches.isEmpty()) {
            source.sendError(Text.literal("[slabrig] " + label
                    + " is built but does NOT measure what its signs say: "
                    + String.join("; ", mismatches)));
            return 0;
        }
        return 1;
    }

    /** A comma list of up to {@code cap} ids, with a {@code +K more} suffix beyond that. */
    private static String summariseIds(List<String> ids, int cap) {
        if (ids.size() <= cap) {
            return String.join(", ", ids);
        }
        return String.join(", ", ids.subList(0, cap)) + ", +" + (ids.size() - cap) + " more";
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
