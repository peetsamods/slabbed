package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

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

    /**
     * THE PLAYER'S GESTURE: the climb you FACE onto a lowered block equals the climb you SEE.
     *
     * <p>This is the reported symptom in its own terms (maintainer, live, 2026-08-28): "build a
     * lowered block, then a slab next to it so it looks like you should just be able to jump from the
     * slab onto the lowered block — you can't. It treats that lowered block like it's too high."
     *
     * <p>Stated as an equality rather than against a jump-height constant, deliberately. The invariant
     * is WYSIWYG applied to movement — the physical step must equal the visual step — and that survives
     * any change to vanilla's step/jump numbers, which a hardcoded threshold would not. For the record
     * of what those numbers were when this was written: vanilla step height is 0.6 and jump height is
     * about 1.25, the visual climb here is 1.0 (jumpable, not steppable), and the physical climb before
     * the fix was 1.5 — past BOTH, so the block was not reachable at all.
     *
     * <p>MUTATION that must redden this row: restore the {@code instanceof StairBlock} restriction on
     * {@code SlabSupportStateMixin#slabbed$collisionQueryExit}.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void theClimbOntoALoweredBlockMatchesTheClimbYouCanSee(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Slab the player stands on, and beside it a bottom slab carrying the lowered block.
        BlockPos standRel = new BlockPos(2, 1, 2);
        BlockPos carrierRel = new BlockPos(3, 1, 2);
        BlockPos loweredRel = carrierRel.above();
        helper.setBlock(standRel, bottomSlabState());
        helper.setBlock(carrierRel, bottomSlabState());
        helper.setBlock(loweredRel, Blocks.STONE.defaultBlockState());

        BlockPos standAbs = helper.absolutePos(standRel);
        BlockPos loweredAbs = helper.absolutePos(loweredRel);
        BlockState standState = level.getBlockState(standAbs);
        BlockState loweredState = level.getBlockState(loweredAbs);

        double dy = SlabSupport.getYOffset(level, loweredAbs, loweredState);
        if (Math.abs(dy + 0.5d) > EPS) {
            throw helper.assertionException(loweredRel,
                    "premise drift: the block must be LOWERED for this row to mean anything, dy=" + dy);
        }

        double standDrawnTop = standAbs.getY()
                + standState.getShape(level, standAbs, CollisionContext.empty()).bounds().maxY;
        double loweredDrawnTop = loweredAbs.getY()
                + loweredState.getShape(level, loweredAbs, CollisionContext.empty()).bounds().maxY;
        double visualClimb = loweredDrawnTop - standDrawnTop;

        // The physical top is whatever the entity world actually reports, so this measures the union
        // of the block's own cell AND the hanging contribution — not one contributor's opinion.
        double physicalTop = Double.NaN;
        for (int i = -8; i <= 40; i++) {
            double y = loweredAbs.getY() + i * 0.0625d;
            AABB probe = new AABB(loweredAbs.getX() + 0.3d, y + 0.001d, loweredAbs.getZ() + 0.3d,
                    loweredAbs.getX() + 0.7d, y + 0.0615d, loweredAbs.getZ() + 0.7d);
            if (!level.noCollision(probe)) {
                physicalTop = y + 0.0625d;
            }
        }
        double physicalClimb = physicalTop - standDrawnTop;

        Slabbed.LOGGER.info("CLIMB | standTop={} loweredDrawnTop={} loweredSolidTop={} visual={} physical={}",
                standDrawnTop, loweredDrawnTop, physicalTop, visualClimb, physicalClimb);

        if (Math.abs(physicalClimb - visualClimb) > 0.0625d) {
            throw helper.assertionException(loweredRel,
                    "UNREACHABLE-LOOKING BLOCK: from the neighbouring slab you SEE a climb of "
                    + visualClimb + " but you must actually climb " + physicalClimb + ". The lowered "
                    + "block is physically taller than it looks, so a jump that plainly should land "
                    + "on it does not. Reported live: 'it treats that lowered block like it's too "
                    + "high.' GH #31.");
        }
        helper.succeed();
    }

    /**
     * A lowered block is solid EXACTLY where it is drawn — no phantom above its visible top.
     *
     * <p>The class's other rows check PRESENCE (is there collision where it's drawn). Presence alone
     * cannot see an EXTRA solid volume, which is how GH #31 survived them: the compensation is
     * additive, so the block was solid where drawn AND for half a block above it. Measured before the
     * fix, stone on a bottom slab: {@code drawnTop=-54.5 solidTop=-54.0 delta=0.5}. The player stood
     * half a block above the surface and could not climb up from an adjacent slab (1.5 > jump height).
     *
     * <p>Asserted through {@code level.noCollision} rather than off the shape, so it measures what the
     * entity world actually reports — the union of the block's own cell and the hanging contribution
     * from {@code BlockCollisionsLoweredAboveMixin} — not just one contributor's opinion.
     *
     * <p>MUTATION that must redden this row alone: restore the {@code instanceof StairBlock} restriction
     * on {@code SlabSupportStateMixin#slabbed$collisionQueryExit}.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aLoweredFullBlockIsSolidExactlyWhereDrawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slabRel = new BlockPos(2, 1, 2);
        BlockPos blockRel = slabRel.above();
        helper.setBlock(slabRel, bottomSlabState());
        helper.setBlock(blockRel, Blocks.STONE.defaultBlockState());

        BlockPos abs = helper.absolutePos(blockRel);
        BlockState st = level.getBlockState(abs);
        double dy = SlabSupport.getYOffset(level, abs, st);
        if (Math.abs(dy + 0.5d) > EPS) {
            throw helper.assertionException(blockRel,
                    "premise drift: stone on a bottom slab must lower -0.5, got " + dy
                    + " — this row is no longer measuring a lowered block");
        }

        double drawnTop = abs.getY() + st.getShape(level, abs, CollisionContext.empty()).bounds().maxY;

        // The band immediately ABOVE the drawn top must be free. Probe just inside it so a shape that
        // ends exactly at drawnTop does not register.
        AABB abovePhantom = new AABB(
                abs.getX() + 0.3d, drawnTop + 0.01d, abs.getZ() + 0.3d,
                abs.getX() + 0.7d, drawnTop + 0.45d, abs.getZ() + 0.7d);
        if (!level.noCollision(abovePhantom)) {
            throw helper.assertionException(blockRel,
                    "PHANTOM COLLISION: the band just above a lowered block's DRAWN top ("
                    + drawnTop + ") is solid. The player stands there instead of on the visible "
                    + "surface, and cannot climb up from an adjacent slab. GH #31.");
        }

        // And the block must still be solid where it IS drawn — the no-ghost direction, restated here
        // so a fix that merely deletes collision cannot pass this row.
        AABB inVisual = new AABB(
                abs.getX() + 0.3d, drawnTop - 0.45d, abs.getZ() + 0.3d,
                abs.getX() + 0.7d, drawnTop - 0.05d, abs.getZ() + 0.7d);
        if (level.noCollision(inVisual)) {
            throw helper.assertionException(blockRel,
                    "CLIP-THROUGH: a lowered block must stay solid where it is drawn — removing the "
                    + "phantom must not remove the block's real body");
        }
        helper.succeed();
    }

    private static net.minecraft.world.level.block.state.BlockState bottomSlabState() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState stableScaffoldingState() {
        return Blocks.SCAFFOLDING.defaultBlockState()
                .setValue(BlockStateProperties.STABILITY_DISTANCE, 0)
                .setValue(BlockStateProperties.BOTTOM, false);
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

    /**
     * C5 carpet collision consumes the same stored -1.0 authority as its model and outline. The
     * hanging helper must expose the carpet's vanilla-thin collision in the cell where it is drawn.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aimedCarpetMinusOneCollisionFollowsStoredSeat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos belowRel = new BlockPos(2, 2, 2);
        BlockPos carpetRel = belowRel.above();
        helper.setBlock(carpetRel, Blocks.MOSS_CARPET.defaultBlockState());
        BlockPos carpetAbs = helper.absolutePos(carpetRel);
        SlabAnchorAttachment.writePlacementDy(level, carpetAbs, -1.0d);

        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            double dy = SlabSupport.getYOffset(level, carpetAbs, level.getBlockState(carpetAbs));
            VoxelShape hanging = SlabSupport.withHangingLoweredCollisionFromAbove(
                    Shapes.empty(), level, helper.absolutePos(belowRel));
            if (Math.abs(dy + 1.0d) > EPS
                    || hanging.isEmpty()
                    || Math.abs(hanging.bounds().minY) > EPS
                    || Math.abs(hanging.bounds().maxY - 0.0625d) > EPS) {
                throw helper.assertionException(carpetRel, "C5 carpet collision authority mismatch: dy="
                        + dy + " hanging=" + hanging);
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
        helper.succeed();
    }

    /**
     * Powder snow keeps vanilla's contextual collision policy. A stored -1.0 seat moves its logical
     * body/outline, but Slabbed must not invent an unconditional solid hanging shape.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aimedPowderSnowMinusOneDoesNotInventSolidCollision(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos belowRel = new BlockPos(2, 2, 2);
        BlockPos powderRel = belowRel.above();
        helper.setBlock(powderRel, Blocks.POWDER_SNOW.defaultBlockState());
        BlockPos powderAbs = helper.absolutePos(powderRel);
        SlabAnchorAttachment.writePlacementDy(level, powderAbs, -1.0d);

        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            double dy = SlabSupport.getYOffset(level, powderAbs, level.getBlockState(powderAbs));
            VoxelShape hanging = SlabSupport.withHangingLoweredCollisionFromAbove(
                    Shapes.empty(), level, helper.absolutePos(belowRel));
            if (Math.abs(dy + 1.0d) > EPS || !hanging.isEmpty()) {
                throw helper.assertionException(powderRel, "C5 powder snow must retain contextual collision: dy="
                        + dy + " unconditionalHanging=" + hanging);
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
        helper.succeed();
    }

    /**
     * P26-1 guard: lowered scaffolding must stay pass-through in its side/interior movement region.
     * This does not prove climb feel or rendered VIS, but it catches the server collision failure that
     * would make scaffolding behave like a solid wall after its visual body lowers onto a slab.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredScaffoldingSideInteriorStaysPassThrough(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slabRel = new BlockPos(2, 1, 2);
        BlockPos scaffoldingRel = slabRel.above();
        helper.setBlock(slabRel, bottomSlabState());
        helper.setBlock(scaffoldingRel, stableScaffoldingState());

        BlockPos scaffoldingAbs = helper.absolutePos(scaffoldingRel);
        BlockState scaffolding = level.getBlockState(scaffoldingAbs);
        double dy = SlabSupport.getYOffset(level, scaffoldingAbs, scaffolding);
        if (Math.abs(dy + 0.5d) > EPS) {
            throw helper.assertionException(scaffoldingRel,
                    "SETUP: scaffolding on a bottom slab should be visually lowered -0.5, got dy=" + dy);
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(scaffoldingAbs.getX() + 0.5d, scaffoldingAbs.getY() - 0.25d, scaffoldingAbs.getZ() + 0.5d);
        int n = scaffoldingAbs.getY();
        AABB sideInterior = new AABB(
                scaffoldingAbs.getX() + 0.25d, n - 0.45d, scaffoldingAbs.getZ() + 0.25d,
                scaffoldingAbs.getX() + 0.75d, n - 0.05d, scaffoldingAbs.getZ() + 0.75d);
        boolean passThrough = level.noCollision(player, sideInterior);
        Slabbed.LOGGER.info("COLLISION-SCAFFOLDING | lowered side/interior passThrough={}", passThrough);
        if (!passThrough) {
            throw helper.assertionException(scaffoldingRel,
                    "P26-1: lowered scaffolding side/interior collision is solid; it should stay pass-through, not act like a wall");
        }

        helper.succeed();
    }

    /**
     * P26-1 RED guard: scaffolding is not a generic solid-where-drawn object. Vanilla uses
     * player-aware scaffolding collision and climb state; Slabbed's lowered-above broadphase helper
     * must not inject an unconditional solid scaffolding shape into the cell below.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredScaffoldingDoesNotInjectSolidHangingCollision(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slabRel = new BlockPos(2, 1, 2);
        BlockPos scaffoldingRel = slabRel.above();
        helper.setBlock(slabRel, bottomSlabState());
        helper.setBlock(scaffoldingRel, stableScaffoldingState());

        BlockPos belowScaffoldingAbs = helper.absolutePos(slabRel);
        VoxelShape merged = SlabSupport.withHangingLoweredCollisionFromAbove(
                Shapes.empty(), level, belowScaffoldingAbs);
        boolean passThrough = merged.isEmpty();
        Slabbed.LOGGER.info("COLLISION-SCAFFOLDING | lowered hanging helper empty={}", passThrough);
        if (!passThrough) {
            throw helper.assertionException(scaffoldingRel,
                    "P26-1: lowered scaffolding must not use generic solid hanging collision; vanilla scaffolding stays player-contextual");
        }

        helper.succeed();
    }

    /**
     * P26-1 FEEL guard: when scaffolding is visually lowered into the cell below, a player occupying
     * that lowered visual volume must still count as being inside climbable scaffolding.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredScaffoldingVisualVolumeIsClimbable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slabRel = new BlockPos(2, 1, 2);
        BlockPos scaffoldingRel = slabRel.above();
        helper.setBlock(slabRel, bottomSlabState());
        helper.setBlock(scaffoldingRel, stableScaffoldingState());

        BlockPos scaffoldingAbs = helper.absolutePos(scaffoldingRel);
        BlockState scaffolding = level.getBlockState(scaffoldingAbs);
        double dy = SlabSupport.getYOffset(level, scaffoldingAbs, scaffolding);
        if (Math.abs(dy + 0.5d) > EPS) {
            throw helper.assertionException(scaffoldingRel,
                    "SETUP: scaffolding on a bottom slab should be visually lowered -0.5, got dy=" + dy);
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(scaffoldingAbs.getX() + 0.5d, scaffoldingAbs.getY() - 0.25d, scaffoldingAbs.getZ() + 0.5d);
        AABB visualScaffoldingVolume = new AABB(scaffoldingAbs).move(0.0d, dy, 0.0d);
        if (!player.getBoundingBox().intersects(visualScaffoldingVolume)) {
            throw helper.assertionException(scaffoldingRel,
                    "SETUP: mock player should intersect the lowered visual scaffolding volume");
        }
        boolean climbable = player.onClimbable();
        Slabbed.LOGGER.info("COLLISION-SCAFFOLDING | lowered visual volume climbable={}", climbable);
        if (!climbable) {
            throw helper.assertionException(scaffoldingRel,
                    "P26-1: player inside lowered visual scaffolding must be climbable for vanilla space/shift traversal");
        }

        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredStairCollisionFollowsVisualStepableHeight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slabRel = new BlockPos(2, 1, 2);
        BlockPos stairRel = slabRel.above();
        helper.setBlock(slabRel, bottomSlabState());
        helper.setBlock(stairRel, Blocks.OAK_STAIRS.defaultBlockState());

        assertLoweredStairCollisionFollowsVisual(helper, level, stairRel, "direct stair on bottom slab");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainedLoweredStairCollisionFollowsVisualStepableHeight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slabRel = new BlockPos(2, 1, 2);
        BlockPos supportRel = slabRel.above();
        BlockPos stairRel = supportRel.above();
        helper.setBlock(slabRel, bottomSlabState());
        helper.setBlock(supportRel, Blocks.OAK_PLANKS.defaultBlockState());
        helper.setBlock(stairRel, Blocks.OAK_STAIRS.defaultBlockState());

        assertDy(helper, level, supportRel, -0.5d, "setup lowered chained support");
        assertLoweredStairCollisionFollowsVisual(helper, level, stairRel, "chained stair on lowered support");
        helper.succeed();
    }

    private static void assertDy(GameTestHelper helper, ServerLevel level, BlockPos rel, double expected, String label) {
        BlockPos abs = helper.absolutePos(rel);
        double got = SlabSupport.getYOffset(level, abs, level.getBlockState(abs));
        if (Math.abs(got - expected) > EPS) {
            throw helper.assertionException(rel, "SETUP: " + label + " expected dy=" + expected + ", got " + got);
        }
    }

    private static void assertLoweredStairCollisionFollowsVisual(
            GameTestHelper helper, ServerLevel level, BlockPos rel, String label) {
        BlockPos abs = helper.absolutePos(rel);
        BlockState state = level.getBlockState(abs);
        double dy = SlabSupport.getYOffset(level, abs, state);
        if (Math.abs(dy + 0.5d) > EPS) {
            throw helper.assertionException(rel, "SETUP: " + label + " expected dy=-0.5, got " + dy);
        }
        VoxelShape collision = state.getCollisionShape(level, abs, CollisionContext.empty());
        if (collision.isEmpty()) {
            throw helper.assertionException(rel, "SETUP: " + label + " collision shape is empty");
        }
        AABB bounds = collision.bounds();
        Slabbed.LOGGER.info("COLLISION-STAIR | {} | dy={} minY={} maxY={}",
                label, dy, bounds.minY, bounds.maxY);
        if (Math.abs(bounds.minY - dy) > EPS || Math.abs(bounds.maxY - 0.5d) > EPS) {
            throw helper.assertionException(rel,
                    "STAIR-STEP: " + label + " collision still has an unlowered ghost stair "
                    + "boundsY=[" + bounds.minY + ", " + bounds.maxY + "]; expected [-0.5, 0.5] "
                    + "so slab-to-stair ascent stays within vanilla step height");
        }
    }
}
