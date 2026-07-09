package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * THE LAW GATE (post-mortem safeguard S-2). The absolute Slabbed law:
 * <b>where a block is placed is where it goes and STAYS — a neighbor update must never change its
 * height (dy). No exceptions except a genuine vanilla mechanic (the block itself being removed).</b>
 *
 * <p>This is the ONE test family that asserts the law directly, instead of asserting that the height
 * lanes agree with each other (which is all the pre-existing suite ever did — see the law-violation
 * post-mortem, finding F-1). For each SUBJECT (placed via the REAL {@code useOn} path — never
 * {@code setBlock} + a hand-rolled onPlaced, the exact false-green F-1 names) it records the
 * placement height as an exact value, then applies each NEIGHBOR MUTATION <em>without touching the
 * subject's cell</em> and asserts the height is byte-identical afterward.
 *
 * <p><b>EXPECTED STATE: RED on the current build.</b> The engine recomputes height from neighbors on
 * every read (post-mortem F-2), so many cells legitimately fail today. That red is the instrument
 * working — each failure message enumerates exactly which (subject, mutation) cells move a placed
 * block, i.e. the punch-list for the frozen-dy restoration. The family goes fully green only once the
 * height is stored at placement and returned verbatim.
 *
 * <p>Extending: add a SUBJECT builder or a MUTATION and every existing pairing exercises it — you
 * cannot add a height behavior without this matrix testing whether it obeys the law.
 */
public final class NeighborUpdateInvarianceTest {

    // ── real-useOn placement ────────────────────────────────────────────────
    private static void place(GameTestHelper h, Item item, BlockPos clicked, Direction face, double yNudge) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked)
                .add(face.getStepX() * 0.5, face.getStepY() * 0.5 + yNudge, face.getStepZ() * 0.5);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    private static double dy(ServerLevel w, BlockPos p) {
        return SlabSupport.getYOffset(w, p, w.getBlockState(p));
    }

    /** Exact height identity (byte-identical intent), with -0.0 normalized to 0.0. */
    private static boolean sameHeight(double a, double b) {
        return Double.doubleToRawLongBits(a == 0.0 ? 0.0 : a)
                == Double.doubleToRawLongBits(b == 0.0 ? 0.0 : b);
    }

    private static void bslab(ServerLevel w, BlockPos p) {
        w.setBlock(p, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
    }

    private static void clearArena(GameTestHelper h, ServerLevel w) {
        for (int x = 0; x <= 6; x++)
            for (int y = 0; y <= 6; y++)
                for (int z = 0; z <= 6; z++)
                    w.setBlock(h.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 2);
    }

    // ── SUBJECTS: each builds a fresh rig and returns the placed subject's cell ──────────────
    @FunctionalInterface
    private interface Subject {
        BlockPos build(GameTestHelper h, ServerLevel w);
    }

    /** Anchored full block on a compound-visible marked bottom slab (source FB to the WEST). */
    private static BlockPos markedSlabRig(GameTestHelper h, ServerLevel w) {
        BlockPos base = h.absolutePos(new BlockPos(3, 1, 3));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bslab(w, base.above(1));
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        bslab(w, base.above(3));
        BlockPos fb = base.above(4);
        w.setBlock(fb, Blocks.STONE.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, fb, w.getBlockState(fb));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(w, fb, w.getBlockState(fb));
        BlockPos support = fb.west();
        bslab(w, support);
        SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(w, support, w.getBlockState(support),
                fb, w.getBlockState(fb));
        return support; // the marked slab; subjects place ON it
    }

    private static BlockPos loweredStackRig(GameTestHelper h, ServerLevel w) {
        BlockPos base = h.absolutePos(new BlockPos(3, 1, 3));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bslab(w, base.above(1));
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        bslab(w, base.above(3)); // reads -0.5 via carrier-below
        return base.above(3);
    }

    /** A full block that renders lowered (-0.5) because it sits on a bottom slab, with air to its WEST. */
    private static BlockPos loweredFullBlockWithAirWest(GameTestHelper h, ServerLevel w) {
        BlockPos base = h.absolutePos(new BlockPos(4, 1, 3));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bslab(w, base.above(1));
        BlockPos fb = base.above(2);
        w.setBlock(fb, Blocks.STONE.defaultBlockState(), 2); // lowered full block (-0.5), west cell is air
        return fb;
    }

    private static final List<NamedSubject> SUBJECTS = List.of(
            new NamedSubject("torch_on_marked_slab", (h, w) -> {
                BlockPos s = markedSlabRig(h, w);
                place(h, Items.TORCH, s, Direction.UP, 0.0);
                return s.above();
            }),
            new NamedSubject("fence_gate_on_marked_slab", (h, w) -> {
                BlockPos s = markedSlabRig(h, w);
                place(h, Items.OAK_FENCE_GATE, s, Direction.UP, 0.0);
                return s.above();
            }),
            new NamedSubject("full_block_on_lowered_stack", (h, w) -> {
                BlockPos s = loweredStackRig(h, w);
                place(h, Items.STONE, s, Direction.UP, 0.0);
                return s.above();
            }),
            new NamedSubject("flat_full_block_control", (h, w) -> {
                BlockPos ground = h.absolutePos(new BlockPos(3, 1, 3));
                w.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
                place(h, Items.STONE, ground, Direction.UP, 0.0);
                return ground.above();
            }),
            new NamedSubject("flat_slab_control", (h, w) -> {
                BlockPos ground = h.absolutePos(new BlockPos(3, 1, 3));
                w.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
                place(h, Items.STONE_SLAB, ground, Direction.UP, 0.0);
                return ground.above();
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
                return fb.above();
            }),
            new NamedSubject("candle_placed_flat_then_neighbored", (h, w) -> {
                BlockPos ground = h.absolutePos(new BlockPos(3, 1, 3));
                w.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
                place(h, Items.CANDLE, ground, Direction.UP, 0.0); // decoration, NOT freeze-flat protected
                return ground.above();
            })
    );

    private record NamedSubject(String name, Subject builder) {
    }

    // ── MUTATIONS: applied to the subject's neighbourhood, never its own cell ────────────────
    @FunctionalInterface
    private interface Mutation {
        void apply(ServerLevel w, BlockPos subject);
    }

    private static final List<NamedMutation> MUTATIONS = List.of(
            new NamedMutation("add_slab_north", (w, s) -> bslab(w, s.north())),
            new NamedMutation("add_slab_east", (w, s) -> bslab(w, s.east())),
            new NamedMutation("add_full_block_north", (w, s) -> w.setBlock(s.north(), Blocks.STONE.defaultBlockState(), 2)),
            new NamedMutation("add_full_block_above", (w, s) -> w.setBlock(s.above(), Blocks.STONE.defaultBlockState(), 2)),
            new NamedMutation("add_lowered_stack_east", (w, s) -> {
                // build a genuine lowered bottom-slab beside the subject (a common "keep building" edit)
                w.setBlock(s.east().below(), Blocks.STONE.defaultBlockState(), 2);
                bslab(w, s.east());
            }),
            new NamedMutation("break_north_neighbor", (w, s) -> w.destroyBlock(s.north(), false)),
            new NamedMutation("break_east_neighbor", (w, s) -> w.destroyBlock(s.east(), false)),
            new NamedMutation("break_west_neighbor", (w, s) -> w.destroyBlock(s.west(), false)),
            new NamedMutation("break_south_neighbor", (w, s) -> w.destroyBlock(s.south(), false)),
            new NamedMutation("break_directly_below", (w, s) -> w.destroyBlock(s.below(), false))
    );

    private record NamedMutation(String name, Mutation mutation) {
    }

    // ── the parametric law assertion: one @GameTest per subject, loops all mutations ─────────
    private void runSubject(GameTestHelper h, NamedSubject subject) {
        ServerLevel w = h.getLevel();
        List<String> violations = new ArrayList<>();
        for (NamedMutation m : MUTATIONS) {
            clearArena(h, w);
            BlockPos subj = subject.builder().build(h, w);
            if (w.getBlockState(subj).isAir()) {
                throw h.assertionException("premise: subject '" + subject.name() + "' failed to place");
            }
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
        if (!violations.isEmpty()) {
            throw h.assertionException("LAW VIOLATION — subject '" + subject.name()
                    + "' moved on neighbor edits (placed height must survive byte-identical):\n  "
                    + String.join("\n  ", violations));
        }
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void torchOnMarkedSlabSurvivesNeighborEdits(GameTestHelper h) {
        runSubject(h, SUBJECTS.get(0));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceGateOnMarkedSlabSurvivesNeighborEdits(GameTestHelper h) {
        runSubject(h, SUBJECTS.get(1));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockOnLoweredStackSurvivesNeighborEdits(GameTestHelper h) {
        runSubject(h, SUBJECTS.get(2));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatFullBlockControlSurvivesNeighborEdits(GameTestHelper h) {
        runSubject(h, SUBJECTS.get(3));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatSlabControlSurvivesNeighborEdits(GameTestHelper h) {
        runSubject(h, SUBJECTS.get(4));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cantileverSlabSurvivesNeighborEdits(GameTestHelper h) {
        runSubject(h, SUBJECTS.get(5));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnLoweredFullBlockSurvivesNeighborEdits(GameTestHelper h) {
        runSubject(h, SUBJECTS.get(6));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void candlePlacedFlatSurvivesNeighborEdits(GameTestHelper h) {
        runSubject(h, SUBJECTS.get(7));
    }
}
