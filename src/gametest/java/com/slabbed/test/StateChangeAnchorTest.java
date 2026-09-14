package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

/**
 * A block that changes KIND in place but keeps its SHAPE keeps its height (LAW.md corollary,
 * maintainer ruling 2026-09-13). Live on this line with Terrain Slabs: a grass slab on a lowered
 * stack converted to a dirt slab when covered and popped up to grid height, sinking into the block
 * above. Vanilla stands in here: a lowered stone slab becomes an oak slab of the same half.
 *
 * <p>MUTATION that must redden this row alone: drop the same-shape early return from
 * {@code BlockOnStateReplacedAnchorMixin}.
 */
public final class StateChangeAnchorTest {

    private static final double EPS = 1.0e-6d;

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void inPlaceSlabKindChangeWithTheSameShapeKeepsItsSeat(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos support = ctx.getAbsolutePos(new BlockPos(3, 16, 3));
        BlockPos slab = support.up();
        world.setBlockState(support, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_ALL);
        world.setBlockState(slab, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_ALL);
        // The slab's recorded seat and modern provenance, the two facts a real placement leaves.
        int writes = SlabAnchorAttachment.writePlacementDyBatch(world, Map.of(slab.toImmutable(), Double.doubleToRawLongBits(-0.5d)));
        int marks = SlabAnchorAttachment.markPostPolicyPlacements(world, List.of(slab.toImmutable()));
        double before = SlabSupport.getYOffset(world, slab, world.getBlockState(slab));
        if (writes != 1 || marks != 1 || Math.abs(before + 0.5d) > EPS) {
            ctx.throwGameTestException("precondition: the lowered slab must carry its seat; writes=" + writes + " marks=" + marks + " dy=" + before);
        }
        // Stone slab -> oak slab: a block-KIND change at the SAME position with the SAME shape.
        world.setBlockState(slab, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_ALL);
        double stored = SlabAnchorAttachment.storedPlacementDy(world, slab);
        double after = SlabSupport.getYOffset(world, slab, world.getBlockState(slab));
        if (!Double.isFinite(stored) || Math.abs(after - before) > EPS) {
            ctx.throwGameTestException("a slab that changes kind in place with the same shape must keep its height: before="
                    + before + " after=" + after + " stored=" + stored + " — the converted slab pops up (the Terrain Slabs grass->dirt slab report)");
        }
        ctx.complete();
    }
}
