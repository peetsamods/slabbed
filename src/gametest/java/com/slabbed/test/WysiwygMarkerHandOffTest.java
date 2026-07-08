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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * F10 port (audit STATE_DEFENSE_DIVERGENCE_2026-07-07): the WYSIWYG follow marker must survive the
 * 2b upper-visible placement REMAP. The HEAD inject marks the cell predicted from the ORIGINAL click;
 * the upper-visible hop then retargets placement one cell UP (the click's hit-Y lies in the visible
 * band of the lowered full block ABOVE the clicked one) — so the slab landed one cell above the
 * marked cell, freezeLoweredOnPlace's exact-equals consume missed, and the slab FROZE FLAT at grid
 * height 0.5 above the aimed lowered surface (the audit's F10 route).
 */
public final class WysiwygMarkerHandOffTest {

    private static final double EPS = 1.0e-6;

    private static void useSlabOn(GameTestHelper helper, BlockPos clicked, Direction face, Vec3 hit) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(Items.STONE_SLAB);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    private static double dy(ServerLevel w, BlockPos pos) {
        return SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
    }

    private static void bottomSlab(ServerLevel w, BlockPos pos) {
        w.setBlock(pos, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
    }

    /**
     * The F10 rig: BOTH the clicked cell and the cell above it read -0.5 and the above is an ordinary
     * full block. Column x=3: ground(1), bslab(2), stoneA(3) -0.5 [the CLICKED block], stoneB(4) -0.5
     * via the geometric column walk [the hop's above-target]. The above-FB must NOT be a -1.0
     * compound: the RC3 compound-visible marker path OWNS -1.0 side placements and anchors the hopped
     * slab on its own (verified: with a compound fb this scene is green without any hand-off) — and an
     * anchored FB on a lowered slab is ALWAYS the -1.0 named compound state, so the -0.5 above-FB
     * must be plain geometric. Returns the clicked pos.
     */
    private static BlockPos buildHopRig(GameTestHelper helper, ServerLevel w) {
        BlockPos base = helper.absolutePos(new BlockPos(3, 1, 2));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(1));
        BlockPos clicked = base.above(2);
        w.setBlock(clicked, Blocks.STONE.defaultBlockState(), 2);
        BlockPos fb = base.above(3);
        w.setBlock(fb, Blocks.STONE.defaultBlockState(), 2);
        double clickedDy = dy(w, clicked);
        if (Math.abs(clickedDy + 0.5) > EPS) {
            throw helper.assertionException("scene premise: the clicked stone must read -0.5, got " + clickedDy);
        }
        double fbDy = dy(w, fb);
        if (Math.abs(fbDy + 0.5) > EPS) {
            throw helper.assertionException("scene premise: the above FB must read the geometric -0.5, got " + fbDy);
        }
        return clicked;
    }

    /**
     * F10 core RED: the hop retargets onto a cell with SOLID FLUSH GROUND below — the ONLY route where
     * the WYSIWYG marker is load-bearing. Over air, RC2-C anchors the cantilevered slab regardless of
     * the marker (pinned by the over-air scene below); on flush ground the slab reads dy 0 and the
     * freeze-flat rail fires UNLESS the consume-side marker overrides it. With the marker orphaned by
     * the hop, the slab froze FLAT at grid height — 0.5 above the aimed lowered side surface (the
     * audit's exact F10 symptom).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hoppedSideClickOntoFlushGroundNeedsTheMarker(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos clicked = buildHopRig(helper, w);
        BlockPos fb = clicked.above();
        BlockPos preHopCell = clicked.north();
        BlockPos postHopCell = fb.north();
        // Solid FLUSH ground filling the pre-hop cell so the hopped placement lands ON it.
        w.setBlock(preHopCell.below().below(), Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(preHopCell.below(), Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(preHopCell, Blocks.STONE.defaultBlockState(), 2);
        double groundDy = dy(w, preHopCell);
        if (Math.abs(groundDy) > EPS) {
            throw helper.assertionException("scene premise: the pre-hop cell stone must be FLUSH, got " + groundDy);
        }
        Vec3 hit = new Vec3(clicked.getX() + 0.5, fb.getY() + 0.2, clicked.getZ() - 0.5 + 1.0e-4);
        useSlabOn(helper, clicked, Direction.NORTH, hit);
        if (!(w.getBlockState(postHopCell).getBlock() instanceof SlabBlock)) {
            throw helper.assertionException(
                    "premise: the hopped placement must land at the post-hop cell, got "
                            + w.getBlockState(postHopCell));
        }
        if (SlabAnchorAttachment.isFrozenFlat(w, postHopCell)) {
            throw helper.assertionException(
                    "F10: the hopped slab froze FLAT 0.5 above the aimed lowered surface (the marker was "
                            + "orphaned by the hop) — the hand-off must carry it to the post-hop cell");
        }
        if (!SlabAnchorAttachment.isAnchored(w, postHopCell)) {
            throw helper.assertionException("F10: the hopped slab must be ANCHORED on the aimed surface");
        }
        double placedDy = dy(w, postHopCell);
        if (Math.abs(placedDy + 0.5) > EPS) {
            throw helper.assertionException("F10: the hopped slab must follow the aimed -0.5 surface, got " + placedDy);
        }
        helper.succeed();
    }

    /**
     * Over-AIR hop pin (green before AND after): RC2-C treats the cantilevered post-hop slab as
     * genuine geometry and anchors it with or without the marker — marker-independent by design.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hoppedSideClickLandsAnchoredNotFrozenFlat(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos clicked = buildHopRig(helper, w);
        BlockPos fb = clicked.above();
        // NORTH face = -Z: the predicted cell before the hop is clicked.north(); after the hop it is
        // fb.north(). Hit-Y inside the hop band [fb.y, fb.y + 0.5].
        Vec3 hit = new Vec3(clicked.getX() + 0.5, fb.getY() + 0.2, clicked.getZ() - 0.5 + 1.0e-4);
        useSlabOn(helper, clicked, Direction.NORTH, hit);
        BlockPos preHopCell = clicked.north();
        BlockPos postHopCell = fb.north();
        boolean placedPre = w.getBlockState(preHopCell).getBlock() instanceof SlabBlock;
        boolean placedPost = w.getBlockState(postHopCell).getBlock() instanceof SlabBlock;
        if (!placedPre && !placedPost) {
            throw helper.assertionException("premise: the slab placement must land at the pre- or post-hop cell");
        }
        BlockPos placed = placedPost ? postHopCell : preHopCell;
        if (!placedPost) {
            throw helper.assertionException(
                    "premise: the upper-visible hop must retarget the placement to the UPPER cell, landed at "
                            + placed.toShortString());
        }
        if (SlabAnchorAttachment.isFrozenFlat(w, placed)) {
            throw helper.assertionException(
                    "F10: the hopped slab froze FLAT (the WYSIWYG marker was orphaned by the hop) — it must "
                            + "anchor on the aimed lowered surface");
        }
        if (!SlabAnchorAttachment.isAnchored(w, placed)) {
            throw helper.assertionException("F10: the hopped slab must be ANCHORED (marker handed off and consumed)");
        }
        double placedDy = dy(w, placed);
        if (placedDy > -0.5 + EPS) {
            throw helper.assertionException(
                    "F10: the hopped slab must sit ON the aimed lowered surface (dy <= -0.5), got " + placedDy);
        }
        helper.succeed();
    }

    /** Control (green before AND after): the un-hopped side-click WYSIWYG route still anchors. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unhoppedSideClickStillAnchors(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(3, 1, 2));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(1));
        BlockPos clicked = base.above(2);
        w.setBlock(clicked, Blocks.STONE.defaultBlockState(), 2);
        double clickedDy = dy(w, clicked);
        if (Math.abs(clickedDy + 0.5) > EPS) {
            throw helper.assertionException("scene premise: the clicked stone must read -0.5, got " + clickedDy);
        }
        // Hit inside the clicked block's OWN visible band (below the above-cell hop band; above = air).
        Vec3 hit = new Vec3(clicked.getX() + 0.5, clicked.getY() + 0.2, clicked.getZ() - 0.5 + 1.0e-4);
        useSlabOn(helper, clicked, Direction.NORTH, hit);
        BlockPos placed = clicked.north();
        if (!(w.getBlockState(placed).getBlock() instanceof SlabBlock)) {
            throw helper.assertionException("premise: the side-click placement must land at clicked.north(), got "
                    + w.getBlockState(placed));
        }
        if (!SlabAnchorAttachment.isAnchored(w, placed)) {
            throw helper.assertionException(
                    "control: the un-hopped WYSIWYG side-click route must still anchor the placed slab");
        }
        double placedDy = dy(w, placed);
        if (Math.abs(placedDy + 0.5) > EPS) {
            throw helper.assertionException("control: the side-click slab must follow the -0.5 surface, got " + placedDy);
        }
        helper.succeed();
    }
}
