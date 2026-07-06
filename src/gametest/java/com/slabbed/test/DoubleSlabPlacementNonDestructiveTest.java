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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * NEVER-POP / NEVER-DESTROY guard (2026-07-06): a slab placement interaction that targets a position
 * ALREADY fully occupied by a {@link SlabType#DOUBLE} slab (a full block made of two combined halves)
 * must NOT change that block's state — it must never silently downgrade the double to a single
 * {@code top}/{@code bottom} slab, drop its anchor, or snap it flush. This mirrors vanilla, where a
 * genuinely-double (full) slab cell is not replaceable, so a second slab cannot be placed into it.
 *
 * <p><b>Why this test exists (the live-recorder investigation).</b> The revived
 * {@code LiveCursorIntentRecorder}'s {@code session.jsonl} appeared to show an anchored DOUBLE slab
 * being repeatedly downgraded to {@code type=top} by subsequent "Success" placements at the same
 * spot. A full empirical reproduction via the REAL {@code useOn} placement path (throwaway probe,
 * deleted) proved that is NOT what happens: through the real server placement path a DOUBLE slab is
 * STABLE — repeated placements never downgrade it (vanilla {@code canBeReplaced} refuses, and
 * Slabbed's remap/{@code canPlace} overrides never override that for a double). The recorder's
 * apparent "double -> top" was an artifact: the recorder logs placements/uses but NOT block-breaks
 * (left-clicks), and the recorded transitions were separated by 2.7-3.4 s — the player broke the
 * slab between placements (unrecorded) and re-placed a fresh single slab. The probe reproduced the
 * recorder's exact {@code top -> double -> top -> top} sequence ONLY when a break was inserted between
 * placements; WITHOUT a break the cell reached {@code double} and stayed {@code double}. So there is
 * no destructive-placement bug — but this permanent guard locks in the invariant the investigation
 * proved, across the full-block configurations the campaign has built, so any future regression that
 * DID let a placement mutate an occupied double would fail here.
 *
 * <p>All scenes drive the REAL {@code BlockItem#useOn} path (mock player + hand-built
 * {@code UseOnContext}), never a {@code setBlock}/{@code canBeReplaced} shortcut, and each asserts the
 * pre-existing double is byte-identical after the attempted placement.
 */
public final class DoubleSlabPlacementNonDestructiveTest {

    private static final double EPS = 1.0e-6;

    private static ItemStack oakSlab() {
        return new ItemStack(Blocks.OAK_SLAB);
    }

    /** Fires the real useOn placement path with the given hit and held slab item. */
    private static void placeSlabVia(Player player, BlockPos clickAbs, Direction face, Vec3 hit) {
        ItemStack stack = oakSlab();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult bhr = new BlockHitResult(hit, face, clickAbs, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, bhr));
    }

    private static Player mockPlayerNear(GameTestHelper helper, BlockPos abs) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 0.5);
        return player;
    }

    private static void assertUnchangedDouble(
            GameTestHelper helper,
            ServerLevel level,
            BlockPos absDouble,
            BlockPos relForErr,
            BlockState expectedState,
            double expectedDy,
            boolean expectAnchored,
            String scene
    ) {
        BlockState actual = level.getBlockState(absDouble);
        if (!actual.equals(expectedState)) {
            throw helper.assertionException(relForErr,
                    scene + ": DESTRUCTIVE placement — the existing DOUBLE slab changed from "
                            + expectedState + " to " + actual + " (a placement must never downgrade a "
                            + "fully-occupied double slab)");
        }
        double dy = SlabSupport.getYOffset(level, absDouble, actual);
        if (Math.abs(dy - expectedDy) > EPS) {
            throw helper.assertionException(relForErr,
                    scene + ": the double's rendered dy changed from " + expectedDy + " to " + dy
                            + " (anchor/lowering must survive an attempted overlapping placement)");
        }
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(level, absDouble, actual) != expectAnchored
                && SlabAnchorAttachment.isAnchored(level, absDouble) != expectAnchored) {
            throw helper.assertionException(relForErr,
                    scene + ": the double lost its persisted lowering/anchor after the attempted placement");
        }
    }

    /**
     * PLAIN double, flush (dy 0). Click every face that would target INTO the double's own cell and
     * confirm it is never downgraded. (Placing INTO an occupied double must be a no-op / route
     * elsewhere; it must never replace the double with a single slab.)
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placementIntoPlainDoubleDoesNotDowngradeIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos doubleRel = new BlockPos(2, 3, 2);
        BlockState doubleState = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        BlockPos doubleAbs = helper.absolutePos(doubleRel);

        // From below (UP face) and from above (DOWN face): both target INTO doubleRel.
        Direction[] faces = {Direction.UP, Direction.DOWN};
        BlockPos[] clickRel = {doubleRel.below(), doubleRel.above()};
        for (int i = 0; i < faces.length; i++) {
            helper.setBlock(doubleRel, doubleState);
            helper.setBlock(clickRel[i], Blocks.STONE.defaultBlockState());
            BlockPos clickAbs = helper.absolutePos(clickRel[i]);
            Player player = mockPlayerNear(helper, clickAbs.above());
            Vec3 hit = new Vec3(clickAbs.getX() + 0.5,
                    clickAbs.getY() + (faces[i] == Direction.UP ? 1.0 : 0.0),
                    clickAbs.getZ() + 0.5);
            placeSlabVia(player, clickAbs, faces[i], hit);
            assertUnchangedDouble(helper, level, doubleAbs, doubleRel, doubleState, 0.0, false,
                    "plain double via " + faces[i] + " face");
            helper.setBlock(clickRel[i], Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    /**
     * ANCHORED, LOWERED double (dy -0.5) — the exact configuration the live recorder captured
     * (aid 51/52: a slab placed forming a DOUBLE that anchored lowered). Built the way real gameplay
     * does it (a DOUBLE beside a persisted lowered bottom slab, marked via the real placement
     * finalizer), then a subsequent DOWN-face placement into the cell below must leave the anchored
     * double intact — still {@code double}, still -0.5, still a persisted lowered carrier.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placementBelowAnchoredLoweredDoubleDoesNotDowngradeIt(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        // Genuine lowered chain (same idiom as SlabOnSlabVerticalAnchorTest):
        BlockPos groundSlab = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos doubleAbs = helper.absolutePos(new BlockPos(3, 3, 2));  // anchored lowered DOUBLE
        BlockPos belowDouble = helper.absolutePos(new BlockPos(3, 2, 2)); // cell below it (DOWN-face target)

        w.setBlock(groundSlab, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(loweredBottom, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));

        BlockState doubleState = Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        w.setBlock(doubleAbs, doubleState, 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, doubleAbs, w.getBlockState(doubleAbs));
        double dy = SlabSupport.getYOffset(w, doubleAbs, w.getBlockState(doubleAbs));
        if (Math.abs(dy + 0.5) > EPS) {
            throw helper.assertionException(new BlockPos(3, 3, 2),
                    "setup: the anchored double must render -0.5, got " + dy);
        }
        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, doubleAbs, w.getBlockState(doubleAbs))) {
            throw helper.assertionException(new BlockPos(3, 3, 2),
                    "setup: the double must be a persisted lowered carrier before the attempted placement");
        }

        // Attempt a DOWN-face placement clicking the anchored double, targeting the cell below it —
        // the recorder's exact interaction. The double itself must be untouched.
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 5, 2)));
        Vec3 downHit = new Vec3(doubleAbs.getX() + 0.5, doubleAbs.getY() - 0.5, doubleAbs.getZ() + 0.5);
        for (int click = 0; click < 3; click++) {
            placeSlabVia(player, doubleAbs, Direction.DOWN, downHit);
            assertUnchangedDouble(helper, w, doubleAbs, new BlockPos(3, 3, 2), doubleState, -0.5, true,
                    "anchored lowered double, DOWN-face placement below (click " + click + ")");
        }
        helper.succeed();
    }

    /**
     * FAILURE-MODE-4 sweep: an ANCHORED FULL BLOCK (not a slab). Placing a slab against/into it must
     * never downgrade or de-anchor the full block. A slab side-placement against a lowered full block
     * is a supported Slabbed feature (it cantilevers a slab beside it) — but the full block itself must
     * stay a full block, anchored, lowered.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placementBesideAnchoredFullBlockDoesNotDowngradeIt(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos fullAbs = helper.absolutePos(new BlockPos(2, 2, 2));  // anchored lowered full block
        w.setBlock(ground, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        BlockState fullState = Blocks.STONE.defaultBlockState();
        w.setBlock(fullAbs, fullState, 2);
        SlabAnchorAttachment.addAnchor(w, fullAbs, fullState);
        if (!SlabAnchorAttachment.isAnchored(w, fullAbs)) {
            throw helper.assertionException(new BlockPos(2, 2, 2), "setup: the full block must be anchored");
        }
        double dyBefore = SlabSupport.getYOffset(w, fullAbs, fullState);
        if (Math.abs(dyBefore + 0.5) > EPS) {
            throw helper.assertionException(new BlockPos(2, 2, 2),
                    "setup: the anchored full block must render -0.5, got " + dyBefore);
        }

        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 3, 2)));
        Vec3 eastHit = new Vec3(fullAbs.getX() + 1.0, fullAbs.getY() + 0.5, fullAbs.getZ() + 0.5);
        for (int click = 0; click < 3; click++) {
            placeSlabVia(player, fullAbs, Direction.EAST, eastHit);
            BlockState after = w.getBlockState(fullAbs);
            if (!after.equals(fullState)) {
                throw helper.assertionException(new BlockPos(2, 2, 2),
                        "DESTRUCTIVE: the anchored full block changed to " + after
                                + " after a slab placement against it (click " + click + ")");
            }
            if (!SlabAnchorAttachment.isAnchored(w, fullAbs)) {
                throw helper.assertionException(new BlockPos(2, 2, 2),
                        "the full block lost its anchor after a slab placement against it");
            }
            double dyAfter = SlabSupport.getYOffset(w, fullAbs, after);
            if (Math.abs(dyAfter + 0.5) > EPS) {
                throw helper.assertionException(new BlockPos(2, 2, 2),
                        "the full block's lowering changed to " + dyAfter + " after an adjacent slab placement");
            }
        }
        helper.succeed();
    }
}
