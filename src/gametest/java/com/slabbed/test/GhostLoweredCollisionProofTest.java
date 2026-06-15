package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Deterministic, Mojang-mapped proof for the "lowered slab/block pass-through
 * ghost" on the 26.1.2 port.
 *
 * <p><b>Mechanism under test.</b> MC's movement broadphase
 * ({@code BlockCollisions} / {@code CollisionGetter.getBlockCollisions}) is
 * cell-bounded: it assumes a block's collision shape lives inside its own unit
 * cube. For a block whose vanilla collision is the default
 * ({@code hasCollision ? state.getShape(world,pos) : empty}), the
 * collision shape is produced by {@code getShape} — the SAME method Slabbed's
 * {@code slabbed$offsetOutline} mixin lowers by {@code dy} for the visual
 * outline. So in 26.1.2 the outline offset BLEEDS into movement collision via
 * vanilla delegation: a lowered block's collision shape hangs to
 * {@code min y = dy} (e.g. -0.5), into the cell below, where the broadphase
 * never samples it → the player walks through (the ghost).
 *
 * <p>This is independent of the removed {@code getCollisionShape} injection
 * (that only ever touched fence/wall/grindstone). The fix is to keep movement
 * collision vanilla (within-cell) by suppressing the outline offset during a
 * collision query.
 *
 * <p><b>Assertion.</b> A full block sitting above a bottom slab (dy=-0.5) must
 * report a collision shape whose {@code min Y >= 0} — i.e. within its own cell.
 * RED (bleed present) reports {@code min Y = -0.5}; GREEN (vanilla collision)
 * reports {@code min Y = 0}. A second check confirms the broadphase actually
 * yields a collision for a player-sized box standing on the block's cell.
 *
 * <p>Does NOT mutate production logic; pure measurement/regression.
 */
public final class GhostLoweredCollisionProofTest {

    private static final double EPS = 1.0e-6;

    /**
     * Full block above a bottom slab: visual lowers to -0.5, but physical
     * movement collision must stay vanilla (within-cell) so the broadphase
     * samples it.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredFullBlockCollisionStaysWithinCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos slabRel = new BlockPos(2, 2, 2);
        BlockPos blockRel = slabRel.above();

        helper.setBlock(slabRel,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        helper.setBlock(blockRel, Blocks.STONE.defaultBlockState());

        BlockPos blockAbs = helper.absolutePos(blockRel);
        BlockState blockState = level.getBlockState(blockAbs);

        // Setup sanity: this configuration must actually lower the full block.
        double dy = SlabSupport.getYOffset(level, blockAbs, blockState);
        if (dy >= -EPS) {
            throw helper.assertionException(blockRel,
                    "SETUP: stone-above-bottom-slab did not lower (dy=" + dy
                    + "); cannot test the collision bleed");
        }

        // CORE: the collision shape the broadphase uses must be within the cell.
        VoxelShape collision = blockState.getCollisionShape(level, blockAbs, CollisionContext.empty());
        double minY = collision.isEmpty() ? 0.0 : collision.min(Direction.Axis.Y);
        if (minY < -EPS) {
            throw helper.assertionException(blockRel,
                    "GHOST: lowered full block (dy=" + dy + ") has SUB-CELL collision"
                    + " minY=" + minY + " — the outline offset bled into movement collision"
                    + " (vanilla getCollisionShape -> getShape). The cell-bounded broadphase"
                    + " drops a shape that hangs below its own cell, so the player passes through.");
        }

        // CORROBORATION: a player-sized box standing on the block's cell must collide.
        AABB playerBox = new AABB(
                blockAbs.getX() + 0.2, blockAbs.getY() + 0.01, blockAbs.getZ() + 0.2,
                blockAbs.getX() + 0.8, blockAbs.getY() + 1.81, blockAbs.getZ() + 0.8);
        boolean noCollision = level.noCollision(playerBox);
        if (noCollision) {
            throw helper.assertionException(blockRel,
                    "GHOST: broadphase yielded NO collision for a player box on the lowered"
                    + " block's cell (minY=" + minY + ", dy=" + dy + ") — pass-through.");
        }

        helper.succeed();
    }

    /**
     * Pure-vanilla control: a full block on solid ground (no slab) has dy=0 and
     * a within-cell collision shape. Guards against the fix accidentally
     * disturbing un-lowered blocks.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaFullBlockCollisionUnchanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        BlockPos groundRel = new BlockPos(2, 2, 2);
        BlockPos blockRel = groundRel.above();
        helper.setBlock(groundRel, Blocks.STONE.defaultBlockState());
        helper.setBlock(blockRel, Blocks.STONE.defaultBlockState());

        BlockPos blockAbs = helper.absolutePos(blockRel);
        BlockState blockState = level.getBlockState(blockAbs);

        double dy = SlabSupport.getYOffset(level, blockAbs, blockState);
        if (Math.abs(dy) > EPS) {
            throw helper.assertionException(blockRel,
                    "CONTROL: full block on solid ground should have dy=0, got " + dy);
        }
        VoxelShape collision = blockState.getCollisionShape(level, blockAbs, CollisionContext.empty());
        double minY = collision.isEmpty() ? 0.0 : collision.min(Direction.Axis.Y);
        if (Math.abs(minY) > EPS) {
            throw helper.assertionException(blockRel,
                    "CONTROL: vanilla full block collision minY should be 0, got " + minY);
        }
        helper.succeed();
    }
}
