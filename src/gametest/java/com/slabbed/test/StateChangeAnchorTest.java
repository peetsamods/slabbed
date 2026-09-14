package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.util.SlabSupport;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A block whose KIND changes in place, without its shape changing, is the block a player placed
 * still standing there, so it keeps the height it was placed at (LAW.md, Law 1; maintainer ruling,
 * 2026-09-13).
 *
 * <p>The reported scene: a compat grass slab on a lowered stack turns into its dirt slab when it is
 * covered, the way vanilla grass turns to dirt. The replacement seam read the change of block kind
 * as the slab leaving its cell, cleared the record, and the converted slab rose to grid height and
 * sank into the block above it. A vanilla stone slab becoming an oak slab of the same half stands
 * in here: the same seam, the same question, no compat mod required.
 *
 * <p>The scene is deliberately built on a full block, so the legacy lane answers 0 for this cell
 * and only the placement record can answer -0.5. A row built on a bottom slab would read -0.5 from
 * the legacy lane even after the record was cleared, and would pass without the fix.
 *
 * <p>The rule holds for every occupant, a full cube as much as a slab: shape, not block identity,
 * decides whether the placed thing is still standing there. {@code SlabPlacementHeightLifecycleTest}
 * carries the full-cube half of it, and the shape-change clears.
 *
 * <p>MUTATION that must redden this row alone: drop the {@code isSameShapeTransform} clause from
 * {@code SlabAnchorAttachment.replacementPreservesPlacementTruth}.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class StateChangeAnchorTest {
    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6d;

    @GameTest(template = TEMPLATE)
    public void inPlaceSlabKindChangeWithTheSameShapeKeepsItsSeat(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos ground = ctx.absolutePos(new BlockPos(3, 1, 3));
        BlockPos slab = ground.above();
        world.setBlock(ground, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(slab, bottomSlab(Blocks.STONE_SLAB), Block.UPDATE_ALL);

        // The seat a placement leaves behind: one recorded half step down.
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                        world.getChunk(slab.getX() >> 4, slab.getZ() >> 4), slab, -1),
                "fixture must record the slab's seat");
        double before = SlabSupport.getYOffset(world, slab, world.getBlockState(slab));
        ctx.assertTrue(Math.abs(before + 0.5d) <= EPS,
                "precondition: the recorded slab must read -0.5, read " + before);

        // Stone slab to oak slab: a change of block KIND at the same position, with the same shape.
        world.setBlock(slab, bottomSlab(Blocks.OAK_SLAB), Block.UPDATE_ALL);

        OptionalInt stored = SlabPlacementHeightAttachment.storedHalfSteps(
                world.getChunk(slab.getX() >> 4, slab.getZ() >> 4), slab);
        double after = SlabSupport.getYOffset(world, slab, world.getBlockState(slab));
        ctx.assertTrue(stored.isPresent() && Math.abs(after - before) <= EPS,
                "a slab that changes kind in place with the same shape must keep its height: before="
                        + before + " after=" + after
                        + " record=" + (stored.isPresent() ? String.valueOf(stored.getAsInt()) : "absent")
                        + " - the converted slab pops up to grid height (the covered grass-slab report)");
        ctx.succeed();
    }

    private static BlockState bottomSlab(Block block) {
        return block.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }
}
