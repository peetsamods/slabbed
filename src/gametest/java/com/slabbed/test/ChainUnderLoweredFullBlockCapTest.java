package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A vertical chain under an ORDINARY (non-slab) lowered cap follows that cap exactly (maintainer
 * ruling, 2026-09-01). The cap, the chain, and a lantern hanging from the chain must all agree on
 * the same dy — the lantern already followed via {@code ceilingHungDecorationDy}'s cap read, so a
 * chain left at grid height put the cap's lowered box visually inside the chain's top segment.
 *
 * <p>The negative control pins the fix as CONDITIONAL: under a genuinely flush, unanchored cap the
 * chain stays at grid height — this is a follow-the-lowered-cap rule, not a blanket
 * chain-always-moves change. The TOP/DOUBLE-slab ceiling-bridge column system (its own client
 * model, grid-height by design) is untouched: membership there returns before the cascading walk
 * this class exercises is ever reached.
 */
public final class ChainUnderLoweredFullBlockCapTest {

    private static final double EPS = 1.0e-6;

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState yChain() {
        return Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
    }

    private static BlockState hangingLantern() {
        return Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
    }

    private static double dy(ServerLevel level, GameTestHelper helper, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        return SlabSupport.getYOffset(level, abs, level.getBlockState(abs));
    }

    private static void expect(GameTestHelper helper, ServerLevel level, BlockPos rel, double want, String what) {
        double got = dy(level, helper, rel);
        if (Math.abs(got - want) > EPS) {
            throw helper.assertionException(rel, what + ": expected dy=" + want + " got " + got);
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainAndLanternFollowAnchoredLoweredFullBlockCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos temporarySlab = new BlockPos(2, 4, 2);
        BlockPos cap = new BlockPos(2, 5, 2);
        BlockPos chain = new BlockPos(2, 4, 2);
        BlockPos lantern = new BlockPos(2, 3, 2);

        helper.setBlock(temporarySlab, bottomSlab());
        helper.setBlock(cap, Blocks.OAK_PLANKS.defaultBlockState());
        BlockPos capAbs = helper.absolutePos(cap);
        SlabAnchorAttachment.addAnchor(level, capAbs, level.getBlockState(capAbs));
        expect(helper, level, cap, -0.5,
                "SETUP: ordinary full-block cap must be anchored lowered before hanging the chain");

        helper.setBlock(chain, yChain());
        helper.setBlock(lantern, hangingLantern());

        expect(helper, level, chain, -0.5,
                "maintainer ruling, 2026-09-01: a chain under an ordinary lowered cap follows it exactly");
        expect(helper, level, lantern, -0.5,
                "lantern hanging from the chain must agree with the cap and the chain, not sit 0.5 apart");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainUnderFlushUnanchoredCapStaysGridHeight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos cap = new BlockPos(2, 5, 2);
        BlockPos chain = new BlockPos(2, 4, 2);

        helper.setBlock(cap, Blocks.OAK_PLANKS.defaultBlockState());
        expect(helper, level, cap, 0.0,
                "SETUP: unanchored full-block cap must be flush");

        helper.setBlock(chain, yChain());

        expect(helper, level, chain, 0.0,
                "NEGATIVE CONTROL: a chain under a flush cap stays at grid height — the cap-follow is conditional on the cap actually being lowered");
        helper.succeed();
    }
}
