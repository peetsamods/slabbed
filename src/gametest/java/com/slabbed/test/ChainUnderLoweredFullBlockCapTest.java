package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.dev.SlabbedTestAccess;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A vertical chain hangs from ANY lowered cap, not only a TOP/DOUBLE slab (live, 2026-09-01:
 * an ordinary anchored full block — the shape a real cantilevered beam takes — left its chain
 * at grid height while a lantern one cell further down, hanging from the same chain, correctly
 * read the cap's drop through {@code ceilingHungDecorationDy}). The cap's lowered box then
 * visually descended into the chain's still-grid-height top segment.
 *
 * <p>The dedicated slab-bridge column system ({@code ceilingBridgedVerticalChainColumnMergeDy})
 * is deliberately untouched by this fix — it owns a separate visual model for the flush-slab
 * case. This row is the ordinary, non-slab cap the bridge system was never meant to cover.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ChainUnderLoweredFullBlockCapTest {
    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6;

    /** Cap, one chain segment, and a hung lantern must all read the same lowered dy. */
    @GameTest(template = TEMPLATE)
    public void chainAndLanternFollowAnchoredFullBlockCap(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos cap = ctx.absolutePos(new BlockPos(2, 4, 2));
        BlockPos chainPos = cap.below();
        BlockPos lanternPos = chainPos.below();

        world.setBlock(cap, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        anchor(world, cap);
        double capDy = SlabSupport.getYOffset(world, cap, world.getBlockState(cap));
        ctx.assertTrue(Math.abs(capDy - (-0.5d)) < EPS,
                "fixture cap must resolve at -0.5, got " + capDy);

        world.setBlock(chainPos, verticalChain(), Block.UPDATE_ALL);
        world.setBlock(lanternPos, Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), Block.UPDATE_ALL);

        double chainDy = SlabSupport.getYOffset(world, chainPos, world.getBlockState(chainPos));
        ctx.assertTrue(Math.abs(chainDy - capDy) < EPS,
                "chain must match its cap's dy exactly; cap=" + capDy + " chain=" + chainDy);

        double lanternDy = SlabSupport.getYOffset(world, lanternPos, world.getBlockState(lanternPos));
        ctx.assertTrue(Math.abs(lanternDy - capDy) < EPS,
                "lantern must match the cap's dy exactly; cap=" + capDy + " lantern=" + lanternDy);
        ctx.succeed();
    }

    /** A chain under a genuinely flush (unanchored) full block must stay at grid height. */
    @GameTest(template = TEMPLATE)
    public void chainUnderFlushFullBlockStaysAtGrid(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos cap = ctx.absolutePos(new BlockPos(2, 4, 2));
        BlockPos chainPos = cap.below();

        world.setBlock(cap, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(chainPos, verticalChain(), Block.UPDATE_ALL);

        double chainDy = SlabSupport.getYOffset(world, chainPos, world.getBlockState(chainPos));
        ctx.assertTrue(Math.abs(chainDy) < EPS,
                "chain under an unanchored (flush) cap must stay at grid height, got " + chainDy);
        ctx.succeed();
    }

    private static BlockState verticalChain() {
        return Blocks.CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
    }

    private static void anchor(ServerLevel world, BlockPos pos) {
        LevelChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        LongOpenHashSet existing = SlabbedTestAccess.marker(chunk, SlabAnchorAttachment.ANCHOR_TYPE);
        LongOpenHashSet replacement = existing == null
                ? new LongOpenHashSet()
                : new LongOpenHashSet(existing);
        replacement.add(pos.asLong());
        SlabbedTestAccess.putMarker(chunk, SlabAnchorAttachment.ANCHOR_TYPE, replacement);
    }
}
