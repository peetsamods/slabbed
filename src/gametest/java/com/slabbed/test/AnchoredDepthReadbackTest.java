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
 * D3/F4 port (audit STATE_DEFENSE_DIVERGENCE_2026-07-07): ANCHORED DEPTH READ-BACK. The freeze-on-place
 * anchor records PRESENCE, not depth, and the in-anchor re-read whitelist had NO lanes for floor torches,
 * fence gates, or the generic floor-top-contact family — those categories fell to the generic -0.5
 * fallback (or were shadowed to -1.0 by the compound-dy entry when the live lane says -1.5). The client
 * predicts placement with NO anchor yet, so it renders the LIVE lane value; the server's anchored read
 * disagreed and the object visibly popped on sync (the RC1 lesson, the "+0.5 pop right after placing").
 *
 * <p>Contract per scene, separated into halves:
 * <ul>
 *   <li>WRITE half — after onPlaced the anchor must be present ({@code isAnchored}).</li>
 *   <li>READ half — the anchored read-back must equal the UNANCHORED live-lane dy (measured by
 *       stripping the attachments the real useOn placement wrote, reading, then re-anchoring). The
 *       unanchored read is exactly what the client predicts.</li>
 * </ul>
 * Real {@code useOn} placements only — no setBlock stand-in for the subject (the old false-green).
 * Scenes use the real compound marker APIs, which only qualify against a genuine compound stack.
 */
public final class AnchoredDepthReadbackTest {

    private static final double EPS = 1.0e-6;

    private static void place(GameTestHelper helper, ItemStack stack, BlockPos clicked, Direction face) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked).add(0, 0.5, 0), face, clicked, false)));
    }

    private static void onPlaced(ServerLevel w, BlockPos pos) {
        SlabAnchorAttachment.addAnchor(w, pos, w.getBlockState(pos));
        SlabAnchorAttachment.freezeLoweredOnPlace(w, pos, w.getBlockState(pos));
    }

    private static BlockPos stackBase(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(3, 1, 2));
    }

    /** The compound source full block sits atop the genuine compound stack. */
    private static BlockPos compoundSourcePos(GameTestHelper helper) {
        return stackBase(helper).above(4);
    }

    private static void bottomSlab(ServerLevel w, BlockPos pos) {
        w.setBlock(pos, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
    }

    /** Ground, slab(0), stone(-0.5), slab(-0.5): the deep lowered-slab support, NO authored markers. */
    private static BlockPos buildLoweredSlabStack(GameTestHelper helper, ServerLevel w) {
        BlockPos base = stackBase(helper);
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(1));
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(3));
        BlockPos support = base.above(3);
        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        if (Math.abs(supportDy + 0.5) > EPS) {
            throw helper.assertionException("scene premise: lowered slab-stack support must read -0.5, got " + supportDy);
        }
        return support;
    }

    /**
     * Compound-visible STONE bottom slab (dy -1.0) — authored-truth marker via the real API. The marker
     * only qualifies against a genuine compound full-block anchor reading exactly -1.0, so the source
     * column is the real compound stack: ground, slab(0), stone(-0.5), slab(-0.5), stone FB(-1.0).
     */
    private static BlockPos buildCompoundVisibleSupport(GameTestHelper helper, ServerLevel w) {
        BlockPos base = stackBase(helper);
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(1));
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(3));
        BlockPos fb = compoundSourcePos(helper);
        w.setBlock(fb, Blocks.STONE.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, fb, w.getBlockState(fb));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(w, fb, w.getBlockState(fb));
        double fbDy = SlabSupport.getYOffset(w, fb, w.getBlockState(fb));
        if (Math.abs(fbDy + 1.0) > EPS) {
            throw helper.assertionException("scene premise: compound source FB must read -1.0, got " + fbDy);
        }
        BlockPos support = fb.west();
        bottomSlab(w, support);
        SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(w, support, w.getBlockState(support),
                fb, w.getBlockState(fb));
        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        if (Math.abs(supportDy + 1.0) > EPS) {
            throw helper.assertionException("scene premise: compound-visible support must read -1.0, got " + supportDy);
        }
        return support;
    }

    /**
     * The shared WRITE-vs-READ contract. {@code expectedLiveDy} pins the live-lane (client-predicted)
     * value when it is established from the lane code (NaN skips the exact pin but the consistency
     * contract still holds).
     */
    private void depthSurvivesReadback(GameTestHelper helper, BlockPos support, ItemStack item,
                                       double expectedLiveDy, String label) {
        ServerLevel w = helper.getLevel();
        BlockPos placed = support.above();
        place(helper, item, support, Direction.UP);
        if (w.getBlockState(placed).isAir()) {
            throw helper.assertionException("premise: the " + label + " placement must succeed");
        }
        double placementDy = SlabSupport.getYOffset(w, placed, w.getBlockState(placed));
        // The client predicts placement with NO anchor yet: strip the attachments the real placement
        // wrote and read the live lane — this is the value the player SEES before the server syncs.
        SlabAnchorAttachment.removeAnchor(w, placed);
        double liveDy = SlabSupport.getYOffset(w, placed, w.getBlockState(placed));
        if (!Double.isNaN(expectedLiveDy) && Math.abs(liveDy - expectedLiveDy) > EPS) {
            throw helper.assertionException("premise: " + label + " live-lane (client-predicted) dy must be "
                    + expectedLiveDy + ", got " + liveDy);
        }
        onPlaced(w, placed);
        // WRITE half: the anchor landed.
        if (!SlabAnchorAttachment.isAnchored(w, placed)) {
            throw helper.assertionException("WRITE half broken: " + label + " must be anchored after onPlaced");
        }
        // READ half: the anchored read-back must equal what the client predicted, or it pops on sync.
        double readback = SlabSupport.getYOffset(w, placed, w.getBlockState(placed));
        if (Math.abs(readback - liveDy) > EPS) {
            throw helper.assertionException("D3/F4 READ half: anchored " + label
                    + " read-back must equal the client-predicted live dy " + liveDy + ", got " + readback
                    + " (the pop right after placing)");
        }
        // Placement half: the REAL placement path (which anchors synchronously) must agree too.
        if (Math.abs(placementDy - liveDy) > EPS) {
            throw helper.assertionException("D3/F4 placement half: the real placement path read " + label
                    + " at " + placementDy + " but the client predicts " + liveDy);
        }
        helper.succeed();
    }

    /** Marked-slab lane (beta35FloorTorchContactLaneDy): compound-visible side-lower support → -1.5. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floorTorchOnMarkedSlabSurvivesReadback(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        depthSurvivesReadback(helper, buildCompoundVisibleSupport(helper, w),
                new ItemStack(Items.TORCH), -1.5, "floor torch on marked slab");
    }

    /** Fence-gate lane (beta35FenceGateContactDy): marked support dy -1.0 → gate -1.5. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceGateOnMarkedSlabSurvivesReadback(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        depthSurvivesReadback(helper, buildCompoundVisibleSupport(helper, w),
                new ItemStack(Items.OAK_FENCE_GATE), -1.5, "fence gate on marked slab");
    }

    /** Deep floor-torch lane WITHOUT markers: lowered slab-stack support (-0.5) → torch -1.0. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floorTorchOnLoweredSlabStackSurvivesReadback(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        depthSurvivesReadback(helper, buildLoweredSlabStack(helper, w),
                new ItemStack(Items.TORCH), -1.0, "floor torch on lowered slab stack");
    }

    /** Deep fence-gate lane WITHOUT markers: lowered slab-stack support (-0.5) → gate -1.0. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceGateOnLoweredSlabStackSurvivesReadback(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        depthSurvivesReadback(helper, buildLoweredSlabStack(helper, w),
                new ItemStack(Items.OAK_FENCE_GATE), -1.0, "fence gate on lowered slab stack");
    }

    /** Floor-torch lane on TOP of the compound full block itself (support dy -1.0, not a slab) → -1.0. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floorTorchOnCompoundFullBlockSurvivesReadback(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        buildCompoundVisibleSupport(helper, w);
        depthSurvivesReadback(helper, compoundSourcePos(helper),
                new ItemStack(Items.TORCH), -1.0, "floor torch on compound FB");
    }

    /** Consistency-only (no live-lane pin): a fence gate on top of the compound full block. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceGateOnCompoundFullBlockSurvivesReadback(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        buildCompoundVisibleSupport(helper, w);
        depthSurvivesReadback(helper, compoundSourcePos(helper),
                new ItemStack(Items.OAK_FENCE_GATE), Double.NaN, "fence gate on compound FB");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void depthHoldsAfterNeighborCompoundEdit(GameTestHelper helper) {
        // The compound-edit half of the symptom: the marker is authored truth on the SUPPORT — breaking
        // the adjacent full block must not pop the anchored object (read-back unchanged, because the
        // marker and therefore the whole floor-torch lane survive the neighbor edit).
        ServerLevel w = helper.getLevel();
        BlockPos support = buildCompoundVisibleSupport(helper, w);
        BlockPos fb = compoundSourcePos(helper);
        BlockPos placed = support.above();
        place(helper, new ItemStack(Items.TORCH), support, Direction.UP);
        if (w.getBlockState(placed).isAir()) {
            throw helper.assertionException("premise: the torch placement must succeed");
        }
        double placementDy = SlabSupport.getYOffset(w, placed, w.getBlockState(placed));
        if (Math.abs(placementDy + 1.5) > EPS) {
            throw helper.assertionException("premise: torch on the marked slab must place at -1.5, got " + placementDy);
        }
        onPlaced(w, placed);
        w.destroyBlock(fb, false);
        double supportAfter = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        boolean markerAfter = SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(w, support, w.getBlockState(support));
        double readback = SlabSupport.getYOffset(w, placed, w.getBlockState(placed));
        if (Math.abs(readback - placementDy) > EPS) {
            throw helper.assertionException(
                    "D3/F4 compound-edit: breaking the adjacent full block must not move the anchored torch: placed at "
                            + placementDy + ", now " + readback
                            + " [support now " + supportAfter + ", marker survives=" + markerAfter + "]");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void plainSlabReadbackControlStaysMinusHalf(GameTestHelper helper) {
        // Control: no over-deepening — a torch on a PLAIN flush-context bottom slab still reads -0.5
        // after anchoring (the new whitelist lanes must be depth-conditional, not blanket).
        ServerLevel w = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos slab = ground.above();
        w.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(slab, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        BlockPos placed = slab.above();
        place(helper, new ItemStack(Items.TORCH), slab, Direction.UP);
        if (w.getBlockState(placed).isAir()) {
            throw helper.assertionException("premise: the torch placement must succeed");
        }
        onPlaced(w, placed);
        double readback = SlabSupport.getYOffset(w, placed, w.getBlockState(placed));
        if (Math.abs(readback + 0.5) > EPS) {
            throw helper.assertionException("control: torch on a plain bottom slab must read -0.5, got " + readback);
        }
        helper.succeed();
    }
}
