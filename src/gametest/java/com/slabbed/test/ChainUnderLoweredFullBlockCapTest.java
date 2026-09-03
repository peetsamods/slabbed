package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * A vertical chain under an ORDINARY (non-slab) lowered cap follows that cap exactly, and so does
 * everything hanging from the chain (maintainer ruling, 2026-09-01).
 *
 * <p>The lantern lane ({@code isLoweredUndersideHangerOwner}) always followed a lowered cap, but it
 * deliberately excluded chains, and the chain's only other lane — the cascading ceiling walk in
 * {@code getYOffsetInner} — had no lowered-cap tail. A chain therefore stayed at grid height while
 * the cap's lowered body descended into its top segment, and a lantern lower down the same chain
 * disagreed with the chain it hangs from.
 *
 * <p>Two boundaries are pinned as controls, because the rule is conditional in both directions:
 * <ul>
 *   <li>a chain under a genuinely flush cap stays at grid height — this is follow-the-lowered-cap,
 *       not chain-always-moves;</li>
 *   <li>a chain under a lowered TOP or DOUBLE slab cap is UNCHANGED at grid height. Slab caps keep
 *       their own flush treatment, and the cap read this class exercises answers {@code NaN} for
 *       every slab, so the tail can never claim one.</li>
 * </ul>
 */
public final class ChainUnderLoweredFullBlockCapTest {

    private static final double EPS = 1.0e-6;

    private static BlockState yChain() {
        return Blocks.IRON_CHAIN.getDefaultState().with(Properties.AXIS, Direction.Axis.Y);
    }

    private static BlockState hangingLantern() {
        return Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true);
    }

    private static void place(ServerWorld w, BlockPos pos, BlockState state) {
        w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }

    private static double dy(ServerWorld w, BlockPos pos) {
        return SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
    }

    private static void expect(TestContext ctx, ServerWorld w, BlockPos pos, double want, String what) {
        double got = dy(w, pos);
        ctx.assertTrue(Math.abs(got - want) <= EPS, what + ": expected dy=" + want + " got " + got);
    }

    /**
     * A cap lowered the way a real side-placed cantilevered beam is lowered on this line: its
     * placement height is a recorded fact (LAW 1). The fixture asserts the write landed, so a
     * refused fact cannot false-green the row.
     */
    private static void lowerCapByHalf(TestContext ctx, ServerWorld w, BlockPos cap) {
        place(w, cap, Blocks.OAK_PLANKS.getDefaultState());
        ctx.assertTrue(SlabPlacementDyAttachment.record(w, cap, -0.5),
                "fixture: the cap's placement height must actually be recorded");
        expect(ctx, w, cap, -0.5, "fixture: the ordinary cap must render lowered before anything hangs from it");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainColumnAndLanternFollowLoweredOrdinaryCap(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos cap = ctx.getAbsolutePos(BlockPos.ORIGIN).add(1, 5, 1);
        BlockPos upperChain = cap.down();
        BlockPos lowerChain = upperChain.down();
        BlockPos lantern = lowerChain.down();

        lowerCapByHalf(ctx, w, cap);
        place(w, upperChain, yChain());
        place(w, lowerChain, yChain());
        place(w, lantern, hangingLantern());

        expect(ctx, w, upperChain, -0.5,
                "maintainer ruling, 2026-09-01: a chain under an ordinary lowered cap follows it exactly");
        expect(ctx, w, lowerChain, -0.5,
                "column coherence: a deeper segment reaches the same cap through the cascading walk"
                        + " and must read the same dy, or the column splits across its own segments");
        expect(ctx, w, lantern, -0.5,
                "a lantern hanging from that chain must agree with the chain and the cap, not sit 0.5 apart");
        ctx.complete();
    }

    /**
     * The ruling's coherence clause reaches the ALWAYS-HUNG family too. A hanging sign attached
     * under a chain resolves through ceilingHungDecorationDy, a different walk from the one the
     * chain itself uses, so the two walks must terminate on the same cap read or the sign and the
     * chain it hangs from disagree — the very split this ruling exists to remove.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingSignBelowChainAgreesWithTheChainAndTheCap(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos cap = ctx.getAbsolutePos(BlockPos.ORIGIN).add(1, 5, 1);
        BlockPos chain = cap.down();
        BlockPos sign = chain.down();

        lowerCapByHalf(ctx, w, cap);
        place(w, chain, yChain());
        place(w, sign, Blocks.OAK_HANGING_SIGN.getDefaultState());

        expect(ctx, w, chain, -0.5, "fixture: the chain must already follow the cap");
        expect(ctx, w, sign, -0.5,
                "maintainer ruling, 2026-09-01: everything hanging from the chain agrees with it —"
                        + " the always-hung walk must terminate on the same cap read");
        ctx.complete();
    }

    /**
     * UNSHIFTED-BLOCK CONTROL: the same geometry with a cap that was never lowered. Without this
     * arm a blanket "chains always follow the cell above" change would also pass the row above.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainUnderFlushOrdinaryCapStaysGridHeight(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos cap = ctx.getAbsolutePos(BlockPos.ORIGIN).add(1, 4, 1);
        BlockPos chain = cap.down();
        BlockPos lantern = chain.down();

        place(w, cap, Blocks.OAK_PLANKS.getDefaultState());
        expect(ctx, w, cap, 0.0, "fixture: an unrecorded ordinary cap must be flush");

        place(w, chain, yChain());
        place(w, lantern, hangingLantern());

        expect(ctx, w, chain, 0.0,
                "control: a chain under a flush cap stays at grid height — the cap-follow is conditional");
        expect(ctx, w, lantern, 0.0,
                "control: the lantern under that flush chain stays at grid height too");
        ctx.complete();
    }

    /**
     * BOUNDARY CONTROL: a LOWERED slab cap must leave the chain exactly where it was. TOP and
     * DOUBLE slab ceilings keep their own flush treatment; widening the ordinary-cap tail to
     * answer slabs would move these two rows, which is a regression and not this ruling.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainUnderLoweredSlabCapIsUnchanged(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(1, 4, 1);

        assertLoweredSlabCapLeavesChainFlush(ctx, w, base, SlabType.TOP);
        assertLoweredSlabCapLeavesChainFlush(ctx, w, base.add(2, 0, 2), SlabType.DOUBLE);
        ctx.complete();
    }

    private static void assertLoweredSlabCapLeavesChainFlush(TestContext ctx, ServerWorld w,
                                                             BlockPos cap, SlabType type) {
        BlockPos chain = cap.down();
        place(w, cap, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, type));
        ctx.assertTrue(SlabPlacementDyAttachment.record(w, cap, -0.5),
                "fixture: the " + type + " slab cap's placement height must actually be recorded");
        expect(ctx, w, cap, -0.5, "fixture: the " + type + " slab cap must render lowered");

        place(w, chain, yChain());
        expect(ctx, w, chain, 0.0,
                "boundary: a chain under a lowered " + type + " slab cap must stay at grid height");
    }
}
