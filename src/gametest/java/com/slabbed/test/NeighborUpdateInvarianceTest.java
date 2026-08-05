package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.SlabType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * THE LAW GATE (post-mortem safeguard S-2), Fabric 1.21.1 translation. The absolute Slabbed law:
 * <b>where a block is placed is where it goes and STAYS — a neighbor update must never change its
 * height (dy). No exceptions except a genuine vanilla mechanic (the block itself being removed).</b>
 * See LAW.md — the test IS the law.
 *
 * <p>For each SUBJECT (placed via the REAL {@code useOnBlock} path — never {@code setBlockState}
 * + a hand-rolled onPlaced) it records the placement height as an exact value, then applies each
 * NEIGHBOR MUTATION <em>without touching the subject's cell</em> and asserts the height is
 * byte-identical afterward.
 *
 * <p><b>CENSUS MODE ({@code required = false} on every row):</b> this line does not yet carry the
 * frozen-dy placement store, so heights are recomputed from neighbors on every read and many rows
 * are EXPECTED to fail today. Each failure enumerates exactly which (subject, mutation) cells move
 * a placed block — that census is the deliverable driving Slice 2. Rows flip to {@code required}
 * (blocking) as Slice 2b–2e lands the frozen store on this line. Height authority here is
 * {@link SlabSupport#getYOffset}; the donor's raw placement-dy store reads are adapted to it
 * (see the port report), and the two store-corruption fixtures are omitted because the store they
 * corrupt does not exist on this line.
 */
public final class NeighborUpdateInvarianceTest {

    // ── real-useOnBlock placement ───────────────────────────────────────────
    private static void place(TestContext h, Item item, BlockPos clicked, Direction face, double yNudge) {
        placeStack(h, new ItemStack(item), clicked, face, yNudge);
    }

    private static void placeStack(
            TestContext h,
            ItemStack stack,
            BlockPos clicked,
            Direction face,
            double yNudge
    ) {
        PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        Vec3d hit = Vec3d.ofCenter(clicked)
                .add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5 + yNudge, face.getOffsetZ() * 0.5);
        stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    private static double dy(ServerWorld w, BlockPos p) {
        return SlabSupport.getYOffset(w, p, w.getBlockState(p));
    }

    /**
     * The height a real {@code useOnBlock} placement actually landed at, read from the placement fact
     * itself rather than from {@link #dy}.
     *
     * <p>Rig builders run inside {@link FrozenDySceneFixture#authored}, which forces the store OFF so
     * that {@code setBlockState} terrain and its marker authors behave; {@link #dy} therefore answers
     * from the live lanes in there, which is not what a store-backed row is about. A premise that wants
     * to name where the player's aim put a block must read the fact.
     */
    private static double landedDy(ServerWorld w, BlockPos p) {
        return SlabAnchorAttachment.storedPlacementDy(w, p);
    }

    /** Exact height identity (byte-identical intent), with -0.0 normalized to 0.0. */
    private static boolean sameHeight(double a, double b) {
        return Double.doubleToRawLongBits(a == 0.0 ? 0.0 : a)
                == Double.doubleToRawLongBits(b == 0.0 ? 0.0 : b);
    }

    /** Test-only fixture: drop exactly one placement-dy fact, leaving the block itself untouched. */
    private static void removePlacementDyOnly(TestContext h, ServerWorld world, BlockPos subject) {
        WorldChunk chunk = world.getChunk(subject.getX() >> 4, subject.getZ() >> 4);
        Long2DoubleOpenHashMap existing = chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
        h.assertTrue(existing != null && existing.containsKey(subject.asLong()),
                "premise: subject PLACEMENT_DY fact was already absent at " + subject);
        Long2DoubleOpenHashMap copy = new Long2DoubleOpenHashMap(existing);
        copy.defaultReturnValue(Double.NaN);
        copy.remove(subject.asLong());
        chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, copy);
    }

    /** Test-only corruption fixture: overwrite exactly one placement-dy fact with the supplied bits. */
    private static void overwritePlacementDyRaw(
            TestContext h, ServerWorld world, BlockPos subject, long rawBits
    ) {
        WorldChunk chunk = world.getChunk(subject.getX() >> 4, subject.getZ() >> 4);
        Long2DoubleOpenHashMap existing = chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
        h.assertTrue(existing != null && existing.containsKey(subject.asLong()),
                "premise: subject PLACEMENT_DY fact was absent before overwrite at " + subject);
        Long2DoubleOpenHashMap copy = new Long2DoubleOpenHashMap(existing);
        copy.defaultReturnValue(Double.NaN);
        copy.put(subject.asLong(), Double.longBitsToDouble(rawBits));
        chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, copy);
    }

    private static void bslab(ServerWorld w, BlockPos p) {
        w.setBlockState(p,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
    }

    /** Mutation-lane bottom slab: fired with full neighbor updates, like a real player edit. */
    private static void bslabNotifyAll(ServerWorld w, BlockPos p) {
        w.setBlockState(p,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_ALL);
    }

    private static void clearArena(TestContext h, ServerWorld w) {
        // y bound is 7 (the full fabric-gametest empty-structure interior, rows 0..7): the deep-rest
        // subject's tower tops out at y=7, and a stale subject cell surviving between mutations would
        // silently corrupt the before/after reads.
        for (int x = 0; x <= 6; x++)
            for (int y = 0; y <= 7; y++)
                for (int z = 0; z <= 6; z++)
                    w.setBlockState(h.getAbsolutePos(new BlockPos(x, y, z)),
                            Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    // ── SUBJECTS: each builds a fresh rig and returns the placed subject's cell ──────────────
    @FunctionalInterface
    private interface Subject {
        BlockPos build(TestContext h, ServerWorld w);
    }

    /** Anchored full block on a compound-visible marked bottom slab (source FB to the WEST). */
    private static BlockPos markedSlabRig(TestContext h, ServerWorld w) {
        BlockPos base = h.getAbsolutePos(new BlockPos(3, 1, 3));
        w.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        bslab(w, base.up(1));
        w.setBlockState(base.up(2), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        bslab(w, base.up(3));
        BlockPos fb = base.up(4);
        w.setBlockState(fb, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, fb, w.getBlockState(fb));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(w, fb, w.getBlockState(fb));
        BlockPos support = fb.west();
        bslab(w, support);
        SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(w, support, w.getBlockState(support),
                fb, w.getBlockState(fb));
        return support; // the marked slab; subjects place ON it
    }

    private static BlockPos loweredStackRig(TestContext h, ServerWorld w) {
        BlockPos base = h.getAbsolutePos(new BlockPos(3, 1, 3));
        w.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        bslab(w, base.up(1));
        w.setBlockState(base.up(2), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        bslab(w, base.up(3)); // reads -0.5 via carrier-below
        return base.up(3);
    }

    /** Real-useOnBlock SBSB tower whose top stone is the C5 -1.0 clicked owner. */
    private static BlockPos minusOneLoweredStoneRig(TestContext h, ServerWorld w) {
        BlockPos ground = h.getAbsolutePos(new BlockPos(3, 1, 3));
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        Item[] tower = {Items.STONE_SLAB, Items.STONE, Items.STONE_SLAB, Items.STONE};
        BlockPos cursor = ground;
        for (Item item : tower) {
            place(h, item, cursor, Direction.UP, 0.0d);
            cursor = cursor.up();
        }
        double topDy = landedDy(w, cursor);
        h.assertTrue(Math.abs(topDy + 1.0d) <= 1.0e-6d,
                "premise: aimed owner should have LANDED at -1.0, got " + topDy + " at " + cursor);
        return cursor;
    }

    /**
     * Deep-rest rig (depth-cap-removal, PKG-20260710): a real-useOnBlock SBSB tower (ground stone,
     * then slab/stone alternating, 6 placements) whose TOP stone reads the uncapped accumulated
     * -1.5. The subject slab placed on it exercises the anchored-slab deep-rest read (support
     * deeper than -1.0) — previously the matrix had NO subject resting on anything deeper than -1.0.
     */
    private static BlockPos deepLoweredStoneRig(TestContext h, ServerWorld w) {
        BlockPos ground = h.getAbsolutePos(new BlockPos(3, 0, 3));
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        Item[] tower = {Items.STONE_SLAB, Items.STONE, Items.STONE_SLAB, Items.STONE, Items.STONE_SLAB, Items.STONE};
        BlockPos cursor = ground;
        for (Item item : tower) {
            place(h, item, cursor, Direction.UP, 0.0);
            cursor = cursor.up();
        }
        double topDy = landedDy(w, cursor);
        h.assertTrue(Math.abs(topDy + 1.5) <= 1.0e-6,
                "premise: deep tower top stone should have LANDED at -1.5, got " + topDy + " at " + cursor);
        return cursor; // the -1.5 stone at y=6; the subject slab places ON it (lands at y=7)
    }

    /** A full block that renders lowered (-0.5) because it sits on a bottom slab, with air to its WEST. */
    private static BlockPos loweredFullBlockWithAirWest(TestContext h, ServerWorld w) {
        BlockPos base = h.getAbsolutePos(new BlockPos(4, 1, 3));
        w.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        bslab(w, base.up(1));
        BlockPos fb = base.up(2);
        w.setBlockState(fb, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS); // lowered FB (-0.5), west air
        return fb;
    }

    private static final List<NamedSubject> SUBJECTS = List.of(
            new NamedSubject("torch_on_marked_slab", (h, w) -> {
                BlockPos s = markedSlabRig(h, w);
                place(h, Items.TORCH, s, Direction.UP, 0.0);
                return s.up();
            }),
            new NamedSubject("fence_gate_on_marked_slab", (h, w) -> {
                BlockPos s = markedSlabRig(h, w);
                place(h, Items.OAK_FENCE_GATE, s, Direction.UP, 0.0);
                return s.up();
            }),
            new NamedSubject("full_block_on_lowered_stack", (h, w) -> {
                BlockPos s = loweredStackRig(h, w);
                place(h, Items.STONE, s, Direction.UP, 0.0);
                return s.up();
            }),
            new NamedSubject("flat_full_block_control", (h, w) -> {
                BlockPos ground = h.getAbsolutePos(new BlockPos(3, 1, 3));
                w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                place(h, Items.STONE, ground, Direction.UP, 0.0);
                return ground.up();
            }),
            new NamedSubject("flat_slab_control", (h, w) -> {
                BlockPos ground = h.getAbsolutePos(new BlockPos(3, 1, 3));
                w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                place(h, Items.STONE_SLAB, ground, Direction.UP, 0.0);
                return ground.up();
            }),
            // GEOMETRIC-LANE subjects — the cases the marker layer does not freeze (where the live
            // churn actually lives). 472c7b70's exact case + side inheritance + an unprotected decoration.
            new NamedSubject("cantilever_slab_beside_lowered_block", (h, w) -> {
                BlockPos fb = loweredFullBlockWithAirWest(h, w);
                place(h, Items.STONE_SLAB, fb, Direction.WEST, -0.25); // lower-half side click -> BOTTOM slab over air
                return fb.west();
            }),
            new NamedSubject("slab_on_lowered_full_block", (h, w) -> {
                BlockPos fb = loweredFullBlockWithAirWest(h, w); // a -0.5 full block
                place(h, Items.STONE_SLAB, fb, Direction.UP, 0.0); // slab lands ON it (inherits the stack)
                return fb.up();
            }),
            new NamedSubject("candle_placed_flat_then_neighbored", (h, w) -> {
                BlockPos ground = h.getAbsolutePos(new BlockPos(3, 1, 3));
                w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                place(h, Items.CANDLE, ground, Direction.UP, 0.0); // decoration, NOT freeze-flat protected
                return ground.up();
            }),
            // DEEP-REST subject (depth-cap-removal, PKG-20260710): a slab resting on a stone at -1.5.
            // On the donor this row runs under the frozen store; this line has none yet (census).
            new NamedSubject("slab_on_deep_lowered_full_block", (h, w) -> {
                BlockPos deepStone = deepLoweredStoneRig(h, w);
                place(h, Items.STONE_SLAB, deepStone, Direction.UP, 0.0);
                return deepStone.up();
            }),
            new NamedSubject("aimed_carpet_on_minus_one_owner", (h, w) -> {
                BlockPos owner = minusOneLoweredStoneRig(h, w);
                place(h, Items.MOSS_CARPET, owner, Direction.UP, 0.0d);
                BlockPos subject = owner.up();
                h.assertTrue(Math.abs(landedDy(w, subject) + 1.0d) <= 1.0e-6d,
                        "premise: aimed carpet should have LANDED at -1.0, got " + landedDy(w, subject));
                return subject;
            }),
            new NamedSubject("aimed_powder_snow_on_minus_one_owner", (h, w) -> {
                BlockPos owner = minusOneLoweredStoneRig(h, w);
                place(h, Items.POWDER_SNOW_BUCKET, owner, Direction.UP, 0.0d);
                BlockPos subject = owner.up();
                h.assertTrue(Math.abs(landedDy(w, subject) + 1.0d) <= 1.0e-6d,
                        "premise: aimed powder snow should have LANDED at -1.0, got "
                                + landedDy(w, subject));
                return subject;
            })
    );

    private record NamedSubject(String name, Subject builder) {
    }

    // ── MUTATIONS: applied to the subject's neighbourhood, never its own cell ────────────────
    @FunctionalInterface
    private interface Mutation {
        void apply(ServerWorld w, BlockPos subject);
    }

    private static final List<NamedMutation> MUTATIONS = List.of(
            new NamedMutation("add_slab_north", (w, s) -> bslabNotifyAll(w, s.north())),
            new NamedMutation("add_slab_east", (w, s) -> bslabNotifyAll(w, s.east())),
            new NamedMutation("add_full_block_north", (w, s) ->
                    w.setBlockState(s.north(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL)),
            new NamedMutation("add_full_block_above", (w, s) ->
                    w.setBlockState(s.up(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL)),
            new NamedMutation("add_lowered_stack_east", (w, s) -> {
                // build a genuine lowered bottom-slab beside the subject (a common "keep building" edit)
                w.setBlockState(s.east().down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                bslabNotifyAll(w, s.east());
            }),
            new NamedMutation("break_north_neighbor", (w, s) -> w.breakBlock(s.north(), false)),
            new NamedMutation("break_east_neighbor", (w, s) -> w.breakBlock(s.east(), false)),
            new NamedMutation("break_west_neighbor", (w, s) -> w.breakBlock(s.west(), false)),
            new NamedMutation("break_south_neighbor", (w, s) -> w.breakBlock(s.south(), false)),
            new NamedMutation("break_directly_below", (w, s) -> w.breakBlock(s.down(), false))
    );

    private record NamedMutation(String name, Mutation mutation) {
    }

    // ── the parametric law assertion: one @GameTest per subject, loops all mutations ─────────
    private void runSubject(TestContext h, NamedSubject subject) {
        runSubject(h, subject, false);
    }

    private void runSubject(TestContext h, NamedSubject subject, boolean authorSceneFacts) {
        ServerWorld w = h.getWorld();
        List<String> violations = new ArrayList<>();
        for (NamedMutation m : MUTATIONS) {
            clearArena(h, w);
            BlockPos subj = authorSceneFacts
                    ? FrozenDySceneFixture.authored(h, () -> subject.builder().build(h, w))
                    : subject.builder().build(h, w);
            h.assertTrue(!w.getBlockState(subj).isAir(),
                    "premise: subject '" + subject.name() + "' failed to place");
            double before = dy(w, subj);
            m.mutation().apply(w, subj);
            // Vanilla-mechanic carve-out: if the mutation caused vanilla to remove the subject itself
            // (e.g. its support went away and the block can't survive), that is a genuine vanilla
            // mechanic, not a Slabbed height violation — allowed.
            if (w.getBlockState(subj).isAir()) {
                continue;
            }
            double after = dy(w, subj);
            if (!sameHeight(before, after)) {
                violations.add(m.name() + ": dy " + before + " -> " + after);
            }
        }
        h.assertTrue(violations.isEmpty(), "LAW VIOLATION — subject '" + subject.name()
                + "' moved on neighbor edits (placed height must survive byte-identical):\n  "
                + String.join("\n  ", violations));
        h.complete();
    }

    /**
     * Runs a law row under the shipped frozen-store mode while the suite's compatibility floor stays
     * OFF. The rig is built inside a forced-OFF window and then has its facts authored
     * ({@link FrozenDySceneFixture#authored}), because the rig's own {@code setBlockState} terrain
     * carries no placement facts and would otherwise all read the stable-flat stand-in; the real
     * {@code useOnBlock} placement that creates the SUBJECT runs outside that window, under the store,
     * since it is the shipped path under test.
     */
    private void runSubjectWithFrozenStore(TestContext h, NamedSubject subject) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            runSubject(h, subject, true);
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", required = false)
    public void torchOnMarkedSlabSurvivesNeighborEdits(TestContext h) {
        runSubject(h, SUBJECTS.get(0));
    }

    /**
     * REQUIRED LAW GATE as of Slice 2b: with the frozen store on, the placed fence gate's height is
     * byte-identical across every neighbour mutation. This row was census-optional only because the
     * store did not exist on this line.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fenceGateOnMarkedSlabSurvivesNeighborEdits(TestContext h) {
        runSubjectWithFrozenStore(h, SUBJECTS.get(1));
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", required = false)
    public void fullBlockOnLoweredStackSurvivesNeighborEdits(TestContext h) {
        runSubject(h, SUBJECTS.get(2));
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", required = false)
    public void flatFullBlockControlSurvivesNeighborEdits(TestContext h) {
        runSubject(h, SUBJECTS.get(3));
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", required = false)
    public void flatSlabControlSurvivesNeighborEdits(TestContext h) {
        runSubject(h, SUBJECTS.get(4));
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", required = false)
    public void cantileverSlabSurvivesNeighborEdits(TestContext h) {
        runSubject(h, SUBJECTS.get(5));
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", required = false)
    public void slabOnLoweredFullBlockSurvivesNeighborEdits(TestContext h) {
        runSubject(h, SUBJECTS.get(6));
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", required = false)
    public void candlePlacedFlatSurvivesNeighborEdits(TestContext h) {
        runSubject(h, SUBJECTS.get(7));
    }

    /**
     * REQUIRED LAW GATE as of Slice 2d: a slab placed on the visible top of a stone resting at
     * {@code -1.5} keeps that landing byte-identical across every neighbour mutation. The row asserts
     * the donor's actual claim — the aimed landing is stored and survives — which needs the store on;
     * under the suite's frozen-OFF floor it was only ever exercising the live lanes.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void slabOnDeepLoweredFullBlockSurvivesNeighborEdits(TestContext h) {
        runSubjectWithFrozenStore(h, SUBJECTS.get(8));
    }

    /**
     * STABLE-FLAT law (store now exists on this line): a cell whose PLACEMENT_DY fact is missing
     * resolves to raw positive {@code 0.0} and STAYS there across a neighbour edit — it must never
     * fall back to the live lanes, because that fallback is exactly the height churn the store ends.
     *
     * <p>PORT NOTE: the donor drives this from its deep {@code -1.5} rest rig. That depth needs the
     * landing resolver (Slice 2d), so this line uses the minimal capability that produces a non-zero
     * stored value — a real-placed stone resting on a real-placed bottom slab, {@code -0.5}.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void missingPlacementDyResolvesStableFlatAcrossNeighborEdit(TestContext h) {
        ServerWorld world = h.getWorld();
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            clearArena(h, world);
            BlockPos subject = frozenLoweredSubject(h, world);

            SlabAnchorAttachment.PlacementDyFact authored =
                    SlabAnchorAttachment.rawPlacementDyFact(world, subject);
            h.assertTrue(authored.present()
                            && authored.rawBits() == Double.doubleToRawLongBits(-0.5d)
                            && sameHeight(dy(world, subject), -0.5d),
                    "premise: subject should carry raw-exact authored/stored/live dy -0.5, got "
                            + authored.valueOrNaN() + " / " + dy(world, subject));

            removePlacementDyOnly(h, world, subject);
            h.assertTrue(!SlabAnchorAttachment.rawPlacementDyFact(world, subject).present(),
                    "premise: subject PLACEMENT_DY fact remained after removal");
            h.assertTrue(!world.getBlockState(subject).isAir(),
                    "premise: removing PLACEMENT_DY removed the subject");

            double fallbackBefore = dy(world, subject);
            world.breakBlock(subject.down(), false);
            h.assertTrue(!world.getBlockState(subject).isAir(),
                    "premise: neighbor edit removed the subject");
            double fallbackAfter = dy(world, subject);
            long positiveZeroBits = Double.doubleToRawLongBits(0.0d);
            h.assertTrue(Double.doubleToRawLongBits(fallbackBefore) == positiveZeroBits
                            && Double.doubleToRawLongBits(fallbackAfter) == positiveZeroBits,
                    "missing PLACEMENT_DY must resolve raw positive 0.0 before/after neighbor edit; "
                            + "observed fallbackBefore=" + fallbackBefore
                            + ", fallbackAfter=" + fallbackAfter);
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
        h.complete();
    }

    /**
     * STABLE-FLAT law for a CORRUPT fact: NaN / ±Infinity in the store resolve to raw positive
     * {@code 0.0} before and after a neighbour edit, and the stored bits themselves are left exactly
     * as found (a read never repairs, rewrites, or re-derives the store).
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void corruptPlacementDyResolvesStablePositiveZeroAcrossNeighborEdit(TestContext h) {
        ServerWorld world = h.getWorld();
        long positiveZeroBits = Double.doubleToRawLongBits(0.0d);
        long[] corruptBits = {
                Double.doubleToRawLongBits(Double.NaN),
                Double.doubleToRawLongBits(Double.POSITIVE_INFINITY),
                Double.doubleToRawLongBits(Double.NEGATIVE_INFINITY)
        };
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            for (long corruptRawBits : corruptBits) {
                clearArena(h, world);
                BlockPos subject = frozenLoweredSubject(h, world);

                overwritePlacementDyRaw(h, world, subject, corruptRawBits);
                SlabAnchorAttachment.PlacementDyFact corruptBefore =
                        SlabAnchorAttachment.rawPlacementDyFact(world, subject);
                h.assertTrue(corruptBefore.present() && corruptBefore.rawBits() == corruptRawBits,
                        "premise: exact corrupt PLACEMENT_DY bits were not stored; wanted="
                                + Long.toHexString(corruptRawBits)
                                + " observed=" + Long.toHexString(corruptBefore.rawBits()));

                double fallbackBefore = dy(world, subject);
                world.breakBlock(subject.down(), false);
                h.assertTrue(!world.getBlockState(subject).isAir(),
                        "premise: corrupt-value neighbor edit removed the subject");
                SlabAnchorAttachment.PlacementDyFact corruptAfter =
                        SlabAnchorAttachment.rawPlacementDyFact(world, subject);
                double fallbackAfter = dy(world, subject);
                h.assertTrue(corruptAfter.present() && corruptAfter.rawBits() == corruptRawBits,
                        "premise: neighbor edit changed corrupt PLACEMENT_DY bits");
                h.assertTrue(Double.doubleToRawLongBits(fallbackBefore) == positiveZeroBits
                                && Double.doubleToRawLongBits(fallbackAfter) == positiveZeroBits,
                        "corrupt PLACEMENT_DY must resolve raw positive 0.0 before/after neighbor edit; "
                                + "corrupt=" + Long.toHexString(corruptRawBits)
                                + " fallbackBefore=" + fallbackBefore
                                + " fallbackAfter=" + fallbackAfter);
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
        h.complete();
    }

    /**
     * Real-useOnBlock rig producing a subject whose stored placement height is exactly {@code -0.5}:
     * ground stone, a slab placed on it, a stone placed on the slab. Every cell is authored through
     * the shipped placement path, so the whole rig carries genuine facts.
     */
    private static BlockPos frozenLoweredSubject(TestContext h, ServerWorld world) {
        BlockPos ground = h.getAbsolutePos(new BlockPos(3, 1, 3));
        FrozenDySceneFixture.authored(h, () ->
                world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS));
        place(h, Items.STONE_SLAB, ground, Direction.UP, 0.0d);
        place(h, Items.STONE, ground.up(), Direction.UP, 0.0d);
        BlockPos subject = ground.up(2);
        h.assertTrue(!world.getBlockState(subject).isAir(),
                "premise: frozen lowered subject failed to place at " + subject);
        return subject;
    }

    /**
     * REQUIRED LAW GATE as of Slice 2d: a carpet the player AIMED at the visible top of a -1.0 owner
     * is stored there and stays there. The natural/worldgen route is untouched — it never enters the
     * capture seam, so it carries no fact and keeps reading flush.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aimedCarpetOnMinusOneOwnerSurvivesNeighborEdits(TestContext h) {
        runSubjectWithFrozenStore(h, SUBJECTS.get(9));
    }

    /**
     * REQUIRED LAW GATE as of Slice 2d: the same claim for a bucket-placed powder snow. Its hard zero
     * in the live lanes now sits BELOW the store read, so an aimed placement is readable while natural
     * powder snow (no fact) still resolves flush and keeps the world-hole guard intact.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aimedPowderSnowOnMinusOneOwnerSurvivesNeighborEdits(TestContext h) {
        runSubjectWithFrozenStore(h, SUBJECTS.get(10));
    }

    /**
     * Donor c3 row force-stores -1.0 under the door via the frozen store and asserts the raw store
     * bits survive; no store exists on this line, so the adapted LAW statement is: a real-placed
     * door pair's {@link SlabSupport#getYOffset} height is byte-identical after an open/powered
     * toggle plus non-support neighbor edits.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty", required = false)
    public void c3_pair_door_toggle_and_neighbor_invariance(TestContext h) {
        ServerWorld world = h.getWorld();
        clearArena(h, world);
        BlockPos owner = h.getAbsolutePos(new BlockPos(3, 3, 3));
        world.setBlockState(owner, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        place(h, Items.OAK_DOOR, owner, Direction.UP, 0.0d);
        BlockPos lower = owner.up();
        BlockPos upper = lower.up();
        h.assertTrue(!world.getBlockState(lower).isAir() && !world.getBlockState(upper).isAir(),
                "premise: door pair failed to place at " + lower);
        double lowerBefore = dy(world, lower);
        double upperBefore = dy(world, upper);

        world.setBlockState(lower.east(), Blocks.GLASS.getDefaultState(), Block.NOTIFY_ALL);
        world.removeBlock(lower.east(), false);
        BlockState toggledLower = world.getBlockState(lower)
                .with(Properties.OPEN, true)
                .with(Properties.POWERED, true);
        BlockState toggledUpper = world.getBlockState(upper)
                .with(Properties.OPEN, true)
                .with(Properties.POWERED, true);
        world.setBlockState(lower, toggledLower, Block.NOTIFY_ALL);
        world.setBlockState(upper, toggledUpper, Block.NOTIFY_ALL);

        h.assertTrue(!world.getBlockState(lower).isAir()
                        && !world.getBlockState(upper).isAir()
                        && sameHeight(dy(world, lower), lowerBefore)
                        && sameHeight(dy(world, upper), upperBefore),
                "door pair moved/vanished after toggle and neighbor edits: lower "
                        + lowerBefore + " -> " + dy(world, lower)
                        + ", upper " + upperBefore + " -> " + dy(world, upper));
        c3Pass(h, "neighbor_update_invariance_test_c3_pair_door_toggle_and_neighbor_invariance");
    }

    /**
     * Donor c3 row force-stores -1.0 under both bed halves and asserts raw store bits; adapted the
     * same way as the door row: real-placed bed pair's getYOffset height must be byte-identical
     * after non-support neighbor edits, and the pair must stay a FOOT/HEAD pair.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty", required = false)
    public void c3_pair_bed_neighbor_invariance(TestContext h) {
        ServerWorld world = h.getWorld();
        clearArena(h, world);
        BlockPos support = h.getAbsolutePos(new BlockPos(3, 3, 3));
        world.setBlockState(support, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(support.east(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        ItemStack bed = new ItemStack(Items.RED_BED);
        bed.set(DataComponentTypes.BLOCK_STATE, BlockStateComponent.DEFAULT.with(
                Properties.HORIZONTAL_FACING, Direction.EAST));
        placeStack(h, bed, support, Direction.UP, 0.0d);
        BlockPos foot = support.up();
        h.assertTrue(!world.getBlockState(foot).isAir()
                        && world.getBlockState(foot).contains(Properties.BED_PART),
                "premise: bed foot failed to place at " + foot);
        // Derive the head from the placed facing (the component-driven EAST facing is the donor's
        // intent, but the mock player's own facing decides on some lines — the LAW statement is
        // facing-independent).
        Direction facing = world.getBlockState(foot).get(Properties.HORIZONTAL_FACING);
        BlockPos head = foot.offset(facing);
        h.assertTrue(world.getBlockState(head).contains(Properties.BED_PART),
                "premise: bed head missing at " + head);
        double footBefore = dy(world, foot);
        double headBefore = dy(world, head);
        world.setBlockState(foot.north(), Blocks.GLASS.getDefaultState(), Block.NOTIFY_ALL);
        world.removeBlock(foot.north(), false);
        world.setBlockState(head.south(), Blocks.GLASS.getDefaultState(), Block.NOTIFY_ALL);
        world.removeBlock(head.south(), false);
        h.assertTrue(world.getBlockState(foot).contains(Properties.BED_PART)
                        && world.getBlockState(foot).get(Properties.BED_PART) == BedPart.FOOT
                        && world.getBlockState(head).contains(Properties.BED_PART)
                        && world.getBlockState(head).get(Properties.BED_PART) == BedPart.HEAD
                        && sameHeight(dy(world, foot), footBefore)
                        && sameHeight(dy(world, head), headBefore),
                "bed pair moved/vanished after non-support neighbor edits: foot "
                        + footBefore + " -> " + dy(world, foot)
                        + ", head " + headBefore + " -> " + dy(world, head));
        c3Pass(h, "neighbor_update_invariance_test_c3_pair_bed_neighbor_invariance");
    }

    private static void c3Pass(TestContext h, String id) {
        Slabbed.LOGGER.info("C3_FOCUSED | slabbed_gametest:{} | PASS", id);
        h.complete();
    }
}
