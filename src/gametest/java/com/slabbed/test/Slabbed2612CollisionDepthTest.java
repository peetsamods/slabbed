package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

/**
 * Collision-PRESENCE for the deepest lowered cases — a lowered block must be SOLID where it is drawn,
 * so the player can't clip through it. Existing collision tests cover a -0.5 slab / full block; this
 * adds the -1.0 compound block, whose visual hangs a FULL block below its own cell (the deepest
 * hanging-collision path). Asserted via {@code level.noCollision(AABB)} — fully headless.
 *
 * <p>This checks PRESENCE only (is there collision where it's drawn). Movement FEEL (smooth stop, no
 * jitter) is a live check.
 */
public final class Slabbed2612CollisionDepthTest {

    private static final double EPS = 1.0e-6;

    private static net.minecraft.world.level.block.state.BlockState bottomSlabState() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /**
     * A compound -1.0 full block (vertical slab/stone/slab/stone) is solid in its visible region. Build
     * the stack, then clear the cell directly below the top stone so ONLY the block's own hanging
     * collision can fill that space, and assert a small box inside the visible lower portion collides.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void compoundMinusOneBlockIsSolidWhereDrawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(1), bottomSlabState());
        helper.setBlock(base.above(2), Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(3), bottomSlabState());
        BlockPos topRel = base.above(4);
        helper.setBlock(topRel, Blocks.STONE.defaultBlockState());
        BlockPos topAbs = helper.absolutePos(topRel);

        // Anchor the -1.0 so it holds when we clear below, then verify the dy.
        SlabAnchorAttachment.addAnchor(level, topAbs, level.getBlockState(topAbs));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(level, topAbs, level.getBlockState(topAbs));
        double dy = SlabSupport.getYOffset(level, topAbs, level.getBlockState(topAbs));
        if (Math.abs(dy + 1.0) > EPS) {
            throw helper.assertionException(topRel, "SETUP: compound block dy expected -1.0, got " + dy);
        }

        // Clear the cell directly below the top stone (was a bottom slab) so the only thing that can
        // block a box there is the top stone's hanging collision.
        helper.setBlock(base.above(3), Blocks.AIR.defaultBlockState());

        int n = topAbs.getY();
        // A box well inside the top block's visible lower region [n-1.0, n-0.5] (now air-celled below it).
        AABB inVisual = new AABB(topAbs.getX() + 0.3, n - 0.9, topAbs.getZ() + 0.3,
                topAbs.getX() + 0.7, n - 0.6, topAbs.getZ() + 0.7);
        boolean noColl = level.noCollision(inVisual);
        Slabbed.LOGGER.info("COLLISION-DEPTH | compound -1.0 block, box in visible lower region, noCollision={}", noColl);
        if (noColl) {
            throw helper.assertionException(topRel,
                    "CLIP-THROUGH: a box inside the compound -1.0 block's visible region has NO collision — "
                    + "the hanging collision must keep it solid where drawn");
        }
        helper.succeed();
    }
}
