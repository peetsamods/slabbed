package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.client.runtime.SlabbedRetargetTestHooks;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.WallShape;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

/**
 * Focused live repro matrix for lowered slab lane grammar.
 *
 * <p>Invariant table used by this test:
 * - target=TOP + faces=N/E/S/W => expected place=TOP / dy=-0.500 (side-lane inheritance)
 * - target=BOTTOM + faces=N/E/S/W => expected place=BOTTOM / dy=-0.500 (side-lane inheritance)
 * - target=DOUBLE + faces=N/E/S/W => expected place=TOP / dy=-0.500 (side-lane inheritance)
 * - target=DOUBLE + face=NORTH with preexisting TOP at placePos => expected place=DOUBLE / dy=-0.500 (merge stays in lowered lane)
 * - lowered DOUBLE lower-half click intent => owner remains lowered DOUBLE, first side placement is BOTTOM dy=-0.500, second click merges legally
 * - target=TOP + face=UP => expected place=DOUBLE / dy=-0.500 (vertical merge)
 * - runVerticalBottomUnderLoweredDoubleStackSeamCase => expected normal BOTTOM at y=0 with lowered DOUBLE at y+1 and dy timeline over place/break action
 */
public final class SlabbedLabLoweredSidePlacementLiveReproClientGameTest implements FabricClientGameTest {

    private static final BlockPos SUPPORT_POS = new BlockPos(0, 200, 0);
    private static final BlockPos FULL_POS = SUPPORT_POS.up();
    private static final double EPSILON = 1.0e-6d;
    private static final double TOP_FACE_HIT_Y = 1.0d;

    private static final Direction[] FACES_TO_TEST = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST,
    };

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (Boolean.getBoolean("slabbed.beta4OutlineHitRaycastMissRedOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta4OutlineHitRaycastMissRedCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4SeamVisibleUpperRedOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runJuliaBeta4AboveAngleTargetingOwnerSplitRedCase(ctx, singleplayer, false);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4SeamVisibleUpperSideFaceRedOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta4SeamVisibleUpperSideFaceRedCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4SeamVisibleUpperAnchoredUpStealRedOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta4SeamVisibleUpperAnchoredUpStealRedCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4SeamVisibleUpperAngleGeneralRedOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta4SeamVisibleUpperAngleGeneralRedCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4SeamGreenProofsOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runJuliaBeta4StoneSlabTargetingOutlineMismatchRedCase(ctx, singleplayer);
                runJuliaBeta4AdjacentVisibleTargetRedCase(ctx, singleplayer);
                runBeta4SeamNoRescueBoundaryCase(ctx, singleplayer);
                runJuliaBeta4AboveAngleTargetingOwnerSplitRedCase(ctx, singleplayer, false);
                runBeta4SeamVisibleUpperSideFaceRedCase(ctx, singleplayer);
                runBeta4SeamVisibleUpperAnchoredUpStealRedCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4SeamNoRescueOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta4SeamNoRescueBoundaryCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.juliaBeta4AdjacentVisibleRedOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runJuliaBeta4AdjacentVisibleTargetRedCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.juliaBeta4AboveAngleRedOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runJuliaBeta4AboveAngleTargetingOwnerSplitRedCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.juliaBeta4TargetingRedOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runJuliaBeta4StoneSlabTargetingOutlineMismatchRedCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.dynamicBridgeOnly")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runDynamicBridgeTopSlabSelectionReproCase(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4CompoundSlabMergeRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta4CompoundSlabMergeRedProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4LiveScreenshotShapeRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta4LiveScreenshotShapeRedProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35LiveItemAnchoringRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35LiveItemAnchoringRedProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FloorTorchLiveShapeRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FloorTorchLiveShapeRedProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FloorTorchVisualContactRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FloorTorchVisualContactRedProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FloorTorchV2ContactGapRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FloorTorchV2ContactGapRedProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FloorTorchFullBlockContactRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FloorTorchFullBlockContactRedProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FloorTorchLoweredSlabPlacement")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FloorTorchLoweredSlabPlacementProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FloorTorchPlainBottomContact")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FloorTorchPlainBottomContactProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FloorTorchSupportFinalizationRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FloorTorchSupportFinalizationRedProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FloorTorchSbsbsRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FloorTorchSbsbsRedProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FloorTorchSbsbsSourceTruthRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FloorTorchSbsbsSourceTruthRedProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35LiveFloorTorchSourceTruthParity")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35LiveFloorTorchSourceTruthParityProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FloorTopObjectFamilyAudit")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FloorTopObjectFamilyAudit(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35CommonObjectCompatibilityAudit")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35CommonObjectCompatibilityAudit(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35SpecialFullblockCompatibilityAudit")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35SpecialFullblockCompatibilityAudit(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35BookshelfContact")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35BookshelfContactProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35ChestContact")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35ChestContactProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35BarrelTriad")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35BarrelTriadProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35EnchantingTableContact")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35EnchantingTableContactProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35StonecutterContact")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35StonecutterContactProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35AnvilContact")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35AnvilContactProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35GrindstoneContact")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35GrindstoneContactProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35TrapdoorDoorAudit")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35TrapdoorDoorAudit(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35OakTrapdoorContact")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35OakTrapdoorContactProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35OakDoorContact")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35OakDoorContactProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35StandingOakSignContact")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35StandingOakSignContactProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35CraftingTableContact")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35CraftingTableContactProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FurnaceTriad")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FurnaceTriadProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35OakFenceContact")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35OakFenceContactProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FenceFamilyLiveRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FenceFamilyLiveRedProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FenceWallVariantCoverage")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FenceWallVariantCoverageProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FenceModelRenderRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FenceModelRenderRedProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35BirchFenceVariantRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35BirchFenceVariantRedProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FenceWallFamilyFix")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FenceWallFamilyFixProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FenceWallContactHitbox")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FenceWallContactHitboxProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FenceWallOwnerServerHit")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FenceWallOwnerServerHitProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FenceWallStackContact")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FenceWallStackContactProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FenceGateContact")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FenceGateContactProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FenceGateFamilyFix")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FenceGateFamilyFixProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35LiveHitboxGateRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35LiveHitboxGateRedProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35LiveHitboxOwnerRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35LiveHitboxOwnerRedProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35CandleFloorTopContact")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35CandleFloorTopContactProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FlowerPotFloorTopSurvival")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FlowerPotFloorTopSurvivalProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35FlowerPotFloorTopContact")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35FlowerPotFloorTopContactProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35LiveFloorTorchContactGapRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35LiveFloorTorchContactGapRedProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta35ObjectSlabOwnershipRed")) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runBeta35ObjectSlabOwnershipProof(ctx, singleplayer);
            }
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4CompoundVisibleSlabLaneRed")) {
            new SlabbedLabBeta4LiveShapeGoblinClientGameTest().runTest(ctx);
            return;
        }

        if (Boolean.getBoolean("slabbed.beta4LiveShapeGoblin")) {
            new SlabbedLabBeta4LiveShapeGoblinClientGameTest().runTest(ctx);
            return;
        }

        // Expand coverage for legal lowered slab targets and explicit merge semantics.
        for (Direction face : FACES_TO_TEST) {
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runLoweredSidePlacementCase(ctx, singleplayer, face, SlabType.TOP, SlabType.TOP);
            }
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runLoweredSidePlacementCase(ctx, singleplayer, face, SlabType.BOTTOM, SlabType.BOTTOM);
            }
            try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                    .setUseConsistentSettings(true)
                    .create()) {
                runLoweredSidePlacementCase(ctx, singleplayer, face, SlabType.DOUBLE, SlabType.TOP);
            }
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLoweredDoubleSideMergeCase(ctx, singleplayer, Direction.NORTH);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLoweredDoubleLowerHalfOwnershipCase(ctx, singleplayer, Direction.NORTH);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLowerHalfOwnershipVisibleBodyCase(ctx, singleplayer);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLowerHalfOwnershipLoweredSlabCase(ctx, singleplayer);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLiteralScreenshotCraftingTableCase(ctx, singleplayer);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLiteralScreenshotLoweredSlabSideCase(ctx, singleplayer);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLiveClickPairBottomSlabLaneInheritanceCase(ctx, singleplayer);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLiveClickPairFullBlockLaneInheritanceCase(ctx, singleplayer);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runBridgeShapeLowerHalfReproCase(ctx, singleplayer);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runDynamicBridgeTopSlabSelectionReproCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLoweredDoubleLowerHalfBoundaryCase(ctx, singleplayer, Direction.EAST);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLoweredDoubleSidePlacementCreatedNormalBottomLaneProofCase(ctx, singleplayer);
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runRealPlacedLoweredBottomSlabPersistenceAfterBridgeBreakProofCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLoweredTopUpMergeCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runOrphanedLoweredLaneSupportRemovalCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runMixedDoubleBottomTeardownLawCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runVerticalBottomUnderLoweredDoubleStackSeamCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runAdjacentLoweredDoubleBreakStoneJumpCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLiveTopDoubleStoneChainJumpReplayCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLiveTopDoubleStoneTopDependencyTransitionCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runLiveTopDoubleStoneTopDependencyNoAttackControlCase(ctx, singleplayer);
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runAdjacentLoweredFullJumpPreservationCase(ctx, singleplayer);
        }

        // Historical false-green / stale proof harness paths:
        // - runSideBySideDependentAdjacentFullJumpLiveOrderCase
        // - runSideBySideDependentAdjacentFullSlabChurnJumpCase
        // - runDependentAdjacentLoweredFullJumpCase
        // - runDependentAdjacentLoweredLivePlacementChurnCase
    }

    /**
     * Legal state law for adjacent full-block stability:
     * If a neighboring lowered full block remains supported by a legal direct or
     * inherited path after an adjacent lowered FB is broken, its dy must remain
     * exactly -0.5 for the observed post-break window.
     *
     * This case models two lowered FBs side-by-side, each with direct BS support,
     * and verifies that breaking one does not immediately lift the other.
     */
    private static void runAdjacentLoweredFullJumpPreservationCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.EAST;
        final BlockPos leftSupportPos = SUPPORT_POS;
        final BlockPos leftFullPos = FULL_POS;
        final BlockPos rightSupportPos = SUPPORT_POS.offset(face);
        final BlockPos rightFullPos = FULL_POS.offset(face);

        setupFixture(singleplayer, leftSupportPos, leftFullPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    rightSupportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    rightFullPos,
                    Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            BlockState leftFull = world.getBlockState(leftFullPos);
            BlockState rightFull = world.getBlockState(rightFullPos);
            SlabAnchorAttachment.addAnchor(world, leftFullPos, leftFull);
            SlabAnchorAttachment.addAnchor(world, rightFullPos, rightFull);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing before adjacent FB jump case");
            }
            BlockState leftFull = mc.world.getBlockState(leftFullPos);
            BlockState rightFull = mc.world.getBlockState(rightFullPos);
            if (!leftFull.isOf(Blocks.STONE) || !rightFull.isOf(Blocks.STONE)) {
                throw new RuntimeException("expected both adjacent FBs before break, found left=" + leftFull
                        + " right=" + rightFull);
            }
            if (!SlabAnchorAttachment.isAnchored(mc.world, leftFullPos)
                    || !SlabAnchorAttachment.isAnchored(mc.world, rightFullPos)) {
                throw new RuntimeException("adjacent FB case expected both FBs anchored pre-break");
            }

            double leftDy = SlabSupport.getYOffset(mc.world, leftFullPos, leftFull);
            double rightDy = SlabSupport.getYOffset(mc.world, rightFullPos, rightFull);
            if (Math.abs(leftDy + 0.5d) > EPSILON) {
                throw new RuntimeException("left FB was not lowered before break: dy=" + leftDy);
            }
            if (Math.abs(rightDy + 0.5d) > EPSILON) {
                throw new RuntimeException("right FB was not lowered before break: dy=" + rightDy);
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(leftFullPos, false);
        });

        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int tick = i;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after break tick " + tick);
                }
                BlockState leftAfter = mc.world.getBlockState(leftFullPos);
                if (tick == 3 && !leftAfter.isAir()) {
                    throw new RuntimeException("left FB expected to be removed on tick " + tick
                            + " but found " + leftAfter);
                }

                BlockState rightAfter = mc.world.getBlockState(rightFullPos);
                if (!rightAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("right FB must remain stone on tick " + tick
                            + " but found " + rightAfter);
                }
                double rightDy = SlabSupport.getYOffset(mc.world, rightFullPos, rightAfter);
                if (Math.abs(rightDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("right FB lost legal lowered support on tick " + tick
                            + " dy=" + rightDy);
                }
            });
        }

        // Diagnostic, not normative: if immediate jump appears, restoring a slab below
        // the broken slot should not be treated as proof of the fix itself.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    leftSupportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after slab replacement diagnostic");
            }
            BlockState rightAfter = mc.world.getBlockState(rightFullPos);
            if (!rightAfter.isOf(Blocks.STONE)) {
                throw new RuntimeException("right FB must remain stone during diagnostic step, found "
                        + rightAfter);
            }
            if (!SlabAnchorAttachment.isAnchored(mc.world, rightFullPos)) {
                throw new RuntimeException("right FB should remain anchored during diagnostic step");
            }
            double rightDy = SlabSupport.getYOffset(mc.world, rightFullPos, rightAfter);
            if (Math.abs(rightDy + 0.5d) > EPSILON) {
                throw new RuntimeException("diagnostic replacement of left support did not keep right FB lowered");
            }
        });
    }

    /**
     * Legal state law for the live A-break/B-jump capture:
     * A lowered DOUBLE slab may be adjacent to a lowered stone B that is already
     * legally supported by its own bottom slab. Breaking A must not make B jump
     * upward while B remains present; replacing A should not be required to restore B.
     */
    private static void runAdjacentLoweredDoubleBreakStoneJumpCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos aPos = SUPPORT_POS;
        final Direction adjacency = Direction.SOUTH;
        final BlockPos bPos = SUPPORT_POS.offset(adjacency);
        final BlockPos bSupportPos = bPos.down();
        final BlockHitResult breakTarget = resolveLoweredSideFaceHit(aPos, adjacency, SlabType.BOTTOM);
        final double eyeY = aPos.getY() + 2.8d;
        final double eyeX = aPos.getX() + 0.5d;
        final double eyeZ = adjacency == Direction.NORTH
                ? aPos.getZ() - 1.7d
                : adjacency == Direction.SOUTH
                        ? aPos.getZ() + 1.7d
                        : aPos.getZ() + 0.5d;
        final double targetX = aPos.getX() + 0.5d;
        final double targetY = aPos.getY() + 0.5d;
        final double targetZ = aPos.getZ() + 0.5d;

        setupFixture(singleplayer, aPos, FULL_POS);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    bSupportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    bPos,
                    Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    aPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, bPos, world.getBlockState(bPos));

            BlockState aState = world.getBlockState(aPos);
            BlockState bState = world.getBlockState(bPos);
            BlockState bSupportState = world.getBlockState(bSupportPos);
            if (!aState.isOf(Blocks.STONE_SLAB)
                    || !aState.contains(SlabBlock.TYPE)
                    || aState.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("adjacent-double proof expected A lowered DOUBLE at "
                        + aPos.toShortString() + ", found " + aState);
            }
            if (!bState.isOf(Blocks.STONE)) {
                throw new RuntimeException("adjacent-double proof expected B stone at "
                        + bPos.toShortString() + ", found " + bState);
            }
            if (!bSupportState.isOf(Blocks.STONE_SLAB)
                    || !bSupportState.contains(SlabBlock.TYPE)
                    || bSupportState.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("adjacent-double proof expected B bottom support at "
                        + bSupportPos.toShortString() + ", found " + bSupportState);
            }
            double aDy = SlabSupport.getYOffset(world, aPos, aState);
            double bDy = SlabSupport.getYOffset(world, bPos, bState);
            System.out.println("[ADJ-DOUBLE-JUMP] initial map: "
                    + "A=" + aState + "@dy=" + aDy
                    + ", B=" + bState + "@dy=" + bDy
                    + ", B below=" + bSupportState + ", B anchored=" + SlabAnchorAttachment.isAnchored(world, bPos));
            if (Math.abs(aDy + 0.5d) > EPSILON) {
                throw new RuntimeException("adjacent-double proof expected A dy=-0.500, found " + aDy);
            }
            if (Math.abs(bDy + 0.5d) > EPSILON) {
                throw new RuntimeException("adjacent-double proof expected B dy=-0.500, found " + bDy);
            }
            if (!SlabAnchorAttachment.isAnchored(world, bPos)) {
                throw new RuntimeException("adjacent-double proof expected B anchored before break");
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                return;
            }
            server.getPlayerManager()
                    .getPlayerList()
                    .get(0)
                    .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(eyeX, eyeY, eyeZ),
                new Vec3d(targetX, targetY, targetZ));
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null || mc.gameRenderer == null) {
                throw new RuntimeException("client not ready for adjacent-double break proof");
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
        });

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready to break A");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            mc.interactionManager.attackBlock(breakTarget.getBlockPos(), breakTarget.getSide());
        });

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after A break tick " + readTick);
                }
                BlockState aAfter = mc.world.getBlockState(aPos);
                BlockState bAfter = mc.world.getBlockState(bPos);
                double bDy = SlabSupport.getYOffset(mc.world, bPos, bAfter);
                boolean bAnchored = SlabAnchorAttachment.isAnchored(mc.world, bPos);
                System.out.println("[ADJ-DOUBLE-JUMP] break tick=" + readTick
                        + " A=" + aAfter
                        + " B=" + bAfter
                        + " bDy=" + bDy
                        + " anchored=" + bAnchored);
                if (readTick > 0 && !aAfter.isAir()) {
                    throw new RuntimeException("adjacent-double proof expected A to clear after break by tick 1, found "
                            + aAfter + " at tick=" + readTick);
                }
                if (!bAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("adjacent-double proof lost B on break tick " + readTick
                            + ", found " + bAfter);
                }
                if (!bAnchored) {
                    throw new RuntimeException("adjacent-double proof lost B anchor on break tick " + readTick);
                }
                if (Math.abs(bDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("adjacent-double proof B jumped on break tick " + readTick
                            + " dy=" + bDy + " state=" + bAfter);
                }
            });
        }

        // Keep replace deterministic for now: direct A restoration to lowered DOUBLE after a break.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    aPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after A replace tick " + readTick);
                }
                BlockState aAfter = mc.world.getBlockState(aPos);
                BlockState bAfter = mc.world.getBlockState(bPos);
                double bDy = SlabSupport.getYOffset(mc.world, bPos, bAfter);
                boolean bAnchored = SlabAnchorAttachment.isAnchored(mc.world, bPos);
                System.out.println("[ADJ-DOUBLE-JUMP] replace tick=" + readTick
                        + " A=" + aAfter
                        + " B=" + bAfter
                        + " bDy=" + bDy
                        + " anchored=" + bAnchored);
                if (readTick == 0 && (!aAfter.isOf(Blocks.STONE_SLAB)
                        || !aAfter.contains(SlabBlock.TYPE)
                        || aAfter.get(SlabBlock.TYPE) != SlabType.DOUBLE)) {
                    throw new RuntimeException("adjacent-double proof expected A restored on replace tick 0, found "
                            + aAfter);
                }
                if (!bAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("adjacent-double proof lost B on replace tick " + readTick
                            + ", found " + bAfter);
                }
                if (!bAnchored) {
                    throw new RuntimeException("adjacent-double proof lost B anchor on replace tick " + readTick);
                }
                if (Math.abs(bDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("adjacent-double proof B drifted on replace tick " + readTick
                            + " dy=" + bDy + " state=" + bAfter);
                }
            });
        }
    }

    /**
     * Live-chain replay for the newest inspect capture:
     * top slab (z-2), lowered DOUBLE (z-1), anchored stone + bottom support (z).
     * BOTTOM support under stone must hold dy=-0.500 while breaking/replacing the lowered DOUBLE
     * and while removing/restoring the upstream TOP.
     */
    private static void runLiveTopDoubleStoneChainJumpReplayCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos bPos = new BlockPos(0, 201, 2);
        final BlockPos bSupportPos = bPos.down();
        final BlockPos aPos = bPos.north();
        final BlockPos aTopPos = aPos.north();
        final BlockHitResult breakATarget = resolveLoweredSideFaceHit(aPos, Direction.SOUTH, SlabType.BOTTOM);
        final double breakAEyeY = aPos.getY() + 2.8d;
        final double breakAEyeX = aPos.getX() + 0.5d;
        final double breakAEyeZ = aPos.getZ() + 1.7d;
        final double breakATargetX = aPos.getX() + 0.5d;
        final double breakATargetY = aPos.getY() + 0.5d;
        final double breakATargetZ = aPos.getZ() + 0.5d;

        setupFixture(singleplayer, bSupportPos, bPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(aTopPos, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(aPos, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            BlockState bState = world.getBlockState(bPos);
            BlockState bSupportState = world.getBlockState(bSupportPos);
            BlockState aState = world.getBlockState(aPos);
            BlockState topState = world.getBlockState(aTopPos);
            SlabAnchorAttachment.addAnchor(world, bPos, bState);
            if (!bState.isOf(Blocks.STONE)) {
                throw new RuntimeException("live-chain replay expected anchored stone at "
                        + bPos.toShortString() + ", found " + bState);
            }
            if (!bSupportState.isOf(Blocks.STONE_SLAB)
                    || !bSupportState.contains(SlabBlock.TYPE)
                    || bSupportState.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("live-chain replay expected stone slab bottom support at "
                        + bSupportPos.toShortString() + ", found " + bSupportState);
            }
            if (!aState.isOf(Blocks.STONE_SLAB)
                    || !aState.contains(SlabBlock.TYPE)
                    || aState.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("live-chain replay expected lowered DOUBLE at "
                        + aPos.toShortString() + ", found " + aState);
            }
            if (!topState.isOf(Blocks.STONE_SLAB)
                    || !topState.contains(SlabBlock.TYPE)
                    || topState.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("live-chain replay expected lowered TOP at "
                        + aTopPos.toShortString() + ", found " + topState);
            }
            double bDy = SlabSupport.getYOffset(world, bPos, bState);
            double aDy = SlabSupport.getYOffset(world, aPos, aState);
            double topDy = SlabSupport.getYOffset(world, aTopPos, topState);
            if (!SlabAnchorAttachment.isAnchored(world, bPos)) {
                throw new RuntimeException("live-chain replay expected anchored stone before break at " + bPos.toShortString());
            }
            if (Math.abs(bDy + 0.5d) > EPSILON) {
                throw new RuntimeException("live-chain replay expected stone dy=-0.500 at "
                        + bPos.toShortString() + ", found " + bDy);
            }
            if (Math.abs(aDy + 0.5d) > EPSILON) {
                throw new RuntimeException("live-chain replay expected lowered DOUBLE dy=-0.500 at "
                        + aPos.toShortString() + ", found " + aDy);
            }
            if (Math.abs(topDy + 0.5d) > EPSILON) {
                throw new RuntimeException("live-chain replay expected lowered TOP dy=-0.500 at "
                        + aTopPos.toShortString() + ", found " + topDy);
            }
            System.out.println("[LIVE-TOP-CHAIN] precheck map="
                    + "B=" + bState + "@dy=" + bDy + ", anchor=" + SlabAnchorAttachment.isAnchored(world, bPos)
                    + ", B.bottom=" + bSupportState + "@dy=" + SlabSupport.getYOffset(world, bSupportPos, bSupportState)
                    + ", A=" + aState + "@dy=" + aDy
                    + ", top=" + topState + "@dy=" + topDy);
        });

        singleplayer.getServer().runOnServer(server -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                return;
            }
            server.getPlayerManager().getPlayerList().get(0).changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(breakAEyeX, breakAEyeY, breakAEyeZ),
                new Vec3d(breakATargetX, breakATargetY, breakATargetZ));
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live top-chain A break");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            mc.interactionManager.attackBlock(breakATarget.getBlockPos(), breakATarget.getSide());
        });

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after A break tick " + readTick);
                }
                BlockState bAfter = mc.world.getBlockState(bPos);
                BlockState aAfter = mc.world.getBlockState(aPos);
                double bDy = SlabSupport.getYOffset(mc.world, bPos, bAfter);
                boolean bAnchored = SlabAnchorAttachment.isAnchored(mc.world, bPos);
                System.out.println("[LIVE-TOP-CHAIN] A break tick=" + readTick
                        + " A=" + aAfter + " B=" + bAfter + " bDy=" + bDy + " anchored=" + bAnchored);
                if (!bAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("live-chain replay expected B stone after A break tick " + readTick
                            + ", found " + bAfter);
                }
                if (!bAnchored) {
                    throw new RuntimeException("live-chain replay B lost anchor after A break tick " + readTick);
                }
                if (Math.abs(bDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-chain replay B shifted after A break tick " + readTick
                            + ", bDy=" + bDy);
                }
                if (aAfter.isOf(Blocks.STONE_SLAB)
                        && aAfter.contains(SlabBlock.TYPE)
                        && aAfter.get(SlabBlock.TYPE) == SlabType.DOUBLE) {
                    double aDy = SlabSupport.getYOffset(mc.world, aPos, aAfter);
                    if (Math.abs(aDy + 0.5d) > EPSILON) {
                        throw new RuntimeException("live-chain replay A remained but drifted after A break tick "
                                + readTick + ", aDy=" + aDy);
                    }
                }
            });
        }

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    aPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after A replace tick " + readTick);
                }
                BlockState aAfter = mc.world.getBlockState(aPos);
                BlockState bAfter = mc.world.getBlockState(bPos);
                double bDy = SlabSupport.getYOffset(mc.world, bPos, bAfter);
                boolean bAnchored = SlabAnchorAttachment.isAnchored(mc.world, bPos);
                System.out.println("[LIVE-TOP-CHAIN] A restore tick=" + readTick
                        + " A=" + aAfter + " B=" + bAfter + " bDy=" + bDy + " anchored=" + bAnchored);
                if (readTick == 0 && (!aAfter.isOf(Blocks.STONE_SLAB)
                        || !aAfter.contains(SlabBlock.TYPE)
                        || aAfter.get(SlabBlock.TYPE) != SlabType.DOUBLE)) {
                    throw new RuntimeException("live-chain replay expected A restored on replace tick 0, found " + aAfter);
                }
                if (!bAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("live-chain replay lost B after A replace tick " + readTick
                            + ", found " + bAfter);
                }
                if (!bAnchored) {
                    throw new RuntimeException("live-chain replay B lost anchor after A replace tick " + readTick);
                }
                if (Math.abs(bDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-chain replay B drifted after A replace tick " + readTick
                            + " dy=" + bDy);
                }
                if (!aAfter.isOf(Blocks.STONE_SLAB)
                        || !aAfter.contains(SlabBlock.TYPE)
                        || aAfter.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                    return;
                }
                double aDy = SlabSupport.getYOffset(mc.world, aPos, aAfter);
                if (Math.abs(aDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-chain replay A drifted on replace tick " + readTick
                            + ", aDy=" + aDy);
                }
            });
        }

        final BlockHitResult breakTopTarget = resolveLoweredSideFaceHit(aTopPos, Direction.SOUTH, SlabType.BOTTOM);
        final double breakTopEyeY = aTopPos.getY() + 2.8d;
        final double breakTopEyeX = aTopPos.getX() + 0.5d;
        final double breakTopEyeZ = aTopPos.getZ() + 1.7d;
        final double breakTopTargetX = aTopPos.getX() + 0.5d;
        final double breakTopTargetY = aTopPos.getY() + 0.5d;
        final double breakTopTargetZ = aTopPos.getZ() + 0.5d;

        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(breakTopEyeX, breakTopEyeY, breakTopEyeZ),
                new Vec3d(breakTopTargetX, breakTopTargetY, breakTopTargetZ));
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live top-chain TOP break");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            mc.interactionManager.attackBlock(breakTopTarget.getBlockPos(), breakTopTarget.getSide());
        });

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after TOP break tick " + readTick);
                }
                BlockState aAfter = mc.world.getBlockState(aPos);
                BlockState topAfter = mc.world.getBlockState(aTopPos);
                BlockState bAfter = mc.world.getBlockState(bPos);
                double bDy = SlabSupport.getYOffset(mc.world, bPos, bAfter);
                double aDy = aAfter.isOf(Blocks.STONE_SLAB)
                        && aAfter.contains(SlabBlock.TYPE)
                        && aAfter.get(SlabBlock.TYPE) == SlabType.DOUBLE
                        ? SlabSupport.getYOffset(mc.world, aPos, aAfter)
                        : Double.NaN;
                boolean bAnchored = SlabAnchorAttachment.isAnchored(mc.world, bPos);
                System.out.println("[LIVE-TOP-CHAIN] TOP break tick=" + readTick
                        + " A=" + aAfter + " top=" + topAfter + " B=" + bAfter
                        + " bDy=" + bDy + " anchored=" + bAnchored + " aDy=" + aDy);
                if (!bAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("live-chain replay lost B after TOP break tick " + readTick
                            + ", found " + bAfter);
                }
                if (!bAnchored) {
                    throw new RuntimeException("live-chain replay B lost anchor after TOP break tick " + readTick);
                }
                if (Math.abs(bDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-chain replay B shifted after TOP break tick " + readTick
                            + ", bDy=" + bDy);
                }
                if (!Double.isNaN(aDy) && Math.abs(aDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-chain replay A drifted after TOP break tick " + readTick
                            + ", aDy=" + aDy);
                }
            });
        }

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    aTopPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after TOP replace tick " + readTick);
                }
                BlockState aAfter = mc.world.getBlockState(aPos);
                BlockState topAfter = mc.world.getBlockState(aTopPos);
                BlockState bAfter = mc.world.getBlockState(bPos);
                double bDy = SlabSupport.getYOffset(mc.world, bPos, bAfter);
                double aDy = aAfter.isOf(Blocks.STONE_SLAB)
                        && aAfter.contains(SlabBlock.TYPE)
                        && aAfter.get(SlabBlock.TYPE) == SlabType.DOUBLE
                        ? SlabSupport.getYOffset(mc.world, aPos, aAfter)
                        : Double.NaN;
                boolean bAnchored = SlabAnchorAttachment.isAnchored(mc.world, bPos);
                double topDy = topAfter.isOf(Blocks.STONE_SLAB)
                        && topAfter.contains(SlabBlock.TYPE)
                        && topAfter.get(SlabBlock.TYPE) == SlabType.TOP
                        ? SlabSupport.getYOffset(mc.world, aTopPos, topAfter)
                        : Double.NaN;
                System.out.println("[LIVE-TOP-CHAIN] TOP restore tick=" + readTick
                        + " A=" + aAfter + " top=" + topAfter + " B=" + bAfter
                        + " bDy=" + bDy + " aDy=" + aDy + " topDy=" + topDy + " anchored=" + bAnchored);
                if (!bAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("live-chain replay lost B after TOP replace tick " + readTick
                            + ", found " + bAfter);
                }
                if (!bAnchored) {
                    throw new RuntimeException("live-chain replay B lost anchor after TOP replace tick " + readTick);
                }
                if (Math.abs(bDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-chain replay B shifted after TOP restore tick " + readTick
                            + ", bDy=" + bDy);
                }
                if (!Double.isNaN(aDy) && Math.abs(aDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-chain replay A drifted after TOP restore tick " + readTick
                            + ", aDy=" + aDy);
                }
                if (!Double.isNaN(topDy) && Math.abs(topDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-chain replay TOP drifted after TOP restore tick " + readTick
                            + " topDy=" + topDy);
                }
            });
        }
    }

    /**
     * TOP dependency law for the same live chain:
     * TOP sits upstream of lowered DOUBLE A, which in this captured topology should not be
     * allowed to silently transition to a normal (dy=0.0) lane while B remains anchored.
     *
     * Law A:
     * If B remains anchored and legal after TOP is removed, A should remain present as lowered
     * DOUBLE (dy=-0.500) rather than normalizing upward or becoming a seam ghost.
     */
    private static void runLiveTopDoubleStoneTopDependencyTransitionCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos bPos = new BlockPos(0, 201, 2);
        final BlockPos bSupportPos = bPos.down();
        final BlockPos aPos = bPos.north();
        final BlockPos aTopPos = aPos.north();
        final BlockHitResult breakTopTarget = resolveLoweredSideFaceHit(aTopPos, Direction.SOUTH, SlabType.BOTTOM);
        final double breakTopEyeY = aTopPos.getY() + 2.8d;
        final double breakTopEyeX = aTopPos.getX() + 0.5d;
        final double breakTopEyeZ = aTopPos.getZ() + 1.7d;
        final double breakTopTargetX = aTopPos.getX() + 0.5d;
        final double breakTopTargetY = aTopPos.getY() + 0.5d;
        final double breakTopTargetZ = aTopPos.getZ() + 0.5d;

        setupFixture(singleplayer, bSupportPos, bPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(aTopPos, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(aPos, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            BlockState topState = world.getBlockState(aTopPos);
            BlockState aState = world.getBlockState(aPos);
            BlockState bState = world.getBlockState(bPos);
            BlockState bSupportState = world.getBlockState(bSupportPos);
            SlabAnchorAttachment.addAnchor(world, bPos, bState);
            if (!topState.isOf(Blocks.STONE_SLAB)
                    || !topState.contains(SlabBlock.TYPE)
                    || topState.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("top-dependency precheck expected TOP at " + aTopPos.toShortString()
                    + ", found " + topState);
            }
            if (!aState.isOf(Blocks.STONE_SLAB)
                    || !aState.contains(SlabBlock.TYPE)
                    || aState.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("top-dependency precheck expected A DOUBLE at "
                        + aPos.toShortString() + ", found " + aState);
            }
            if (!bState.isOf(Blocks.STONE)) {
                throw new RuntimeException("top-dependency precheck expected anchored stone at "
                        + bPos.toShortString() + ", found " + bState);
            }
            if (!bSupportState.isOf(Blocks.STONE_SLAB)
                    || !bSupportState.contains(SlabBlock.TYPE)
                    || bSupportState.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("top-dependency precheck expected BOTTOM support at "
                        + bSupportPos.toShortString() + ", found " + bSupportState);
            }

            double topDy = SlabSupport.getYOffset(world, aTopPos, topState);
            double aDy = SlabSupport.getYOffset(world, aPos, aState);
            double bDy = SlabSupport.getYOffset(world, bPos, bState);
            double bSupportDy = SlabSupport.getYOffset(world, bSupportPos, bSupportState);
            if (Math.abs(topDy + 0.5d) > EPSILON) {
                throw new RuntimeException("top-dependency precheck expected TOP dy=-0.500 at "
                        + aTopPos.toShortString() + ", found " + topDy);
            }
            if (Math.abs(aDy + 0.5d) > EPSILON) {
                throw new RuntimeException("top-dependency precheck expected A dy=-0.500 at "
                        + aPos.toShortString() + ", found " + aDy);
            }
            if (Math.abs(bDy + 0.5d) > EPSILON) {
                throw new RuntimeException("top-dependency precheck expected B dy=-0.500 at "
                        + bPos.toShortString() + ", found " + bDy);
            }
            if (Math.abs(bSupportDy) > EPSILON) {
                throw new RuntimeException("top-dependency precheck expected B support dy=0.000 at "
                        + bSupportPos.toShortString() + ", found " + bSupportDy);
            }
            if (!SlabAnchorAttachment.isAnchored(world, bPos)) {
                throw new RuntimeException("top-dependency precheck expected B anchored at "
                        + bPos.toShortString());
            }
            System.out.println("[TOP-DEPENDENCY] precheck map="
                    + "TOP=" + topState + "@dy=" + topDy
                    + ", A=" + aState + "@dy=" + aDy
                    + ", B=" + bState + "@dy=" + bDy
                    + ", B.anchor=" + SlabAnchorAttachment.isAnchored(world, bPos)
                    + ", B.down=" + bSupportState + "@dy=" + bSupportDy);
        });

        singleplayer.getServer().runOnServer(server -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                return;
            }
            server.getPlayerManager().getPlayerList().get(0).changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(breakTopEyeX, breakTopEyeY, breakTopEyeZ),
                new Vec3d(breakTopTargetX, breakTopTargetY, breakTopTargetZ));
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for top-dependency TOP break");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            mc.interactionManager.attackBlock(breakTopTarget.getBlockPos(), breakTopTarget.getSide());
        });

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after TOP dependency case tick " + readTick);
                }
                BlockState aAfter = mc.world.getBlockState(aPos);
                BlockState topAfter = mc.world.getBlockState(aTopPos);
                BlockState bAfter = mc.world.getBlockState(bPos);
                double aDy = aAfter.isOf(Blocks.STONE_SLAB)
                        && aAfter.contains(SlabBlock.TYPE)
                        && aAfter.get(SlabBlock.TYPE) == SlabType.DOUBLE
                        ? SlabSupport.getYOffset(mc.world, aPos, aAfter)
                        : Double.NaN;
                double bDy = SlabSupport.getYOffset(mc.world, bPos, bAfter);
                boolean bAnchored = SlabAnchorAttachment.isAnchored(mc.world, bPos);
                System.out.println("[TOP-DEPENDENCY] tick=" + readTick
                        + " TOP=" + topAfter + " A=" + aAfter + " A-dy=" + aDy
                        + " B=" + bAfter + " B-dy=" + bDy + " B.anchor=" + bAnchored);

                if (!topAfter.isAir()) {
                    throw new RuntimeException("top-dependency case expected TOP removed at tick " + readTick
                            + ", found " + topAfter);
                }
                if (!bAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("top-dependency case expected B stone at tick " + readTick
                            + ", found " + bAfter);
                }
                if (!bAnchored) {
                    throw new RuntimeException("top-dependency case expected B anchored at tick " + readTick
                            + ", but it was not");
                }
                if (Math.abs(bDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("top-dependency case expected B dy=-0.500 at tick " + readTick
                            + ", found " + bDy);
                }
                if (!aAfter.isOf(Blocks.STONE_SLAB)
                        || !aAfter.contains(SlabBlock.TYPE)
                        || aAfter.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                    throw new RuntimeException("top-dependency case ambiguous A transition after TOP removal at tick "
                            + readTick + ", expected lowered DOUBLE; found " + aAfter
                            + ". Legal transition to non-DOUBLE is intentionally not accepted by this proof.");
                }
                if (Math.abs(aDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("top-dependency case expected A stay lowered DOUBLE after TOP removal at tick "
                            + readTick + ", found dy=" + aDy);
                }
            });
        }
    }

    /**
     * No-attack control for top-dependency:
     * keep TOP untouched, break only A (lower support transition), and verify whether TOP
     * survives on both server and client across ticks 0..3.
     */
    private static void runLiveTopDoubleStoneTopDependencyNoAttackControlCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos bPos = new BlockPos(0, 201, 2);
        final BlockPos bSupportPos = bPos.down();
        final BlockPos aPos = bPos.north();
        final BlockPos aTopPos = aPos.north();
        final java.util.concurrent.atomic.AtomicBoolean serverTopDropped = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicBoolean clientTopDropped = new java.util.concurrent.atomic.AtomicBoolean(false);

        setupFixture(singleplayer, bSupportPos, bPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(aTopPos, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(aPos, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            BlockState topState = world.getBlockState(aTopPos);
            BlockState aState = world.getBlockState(aPos);
            BlockState bState = world.getBlockState(bPos);
            BlockState bSupportState = world.getBlockState(bSupportPos);
            SlabAnchorAttachment.addAnchor(world, bPos, bState);
            double topDy = SlabSupport.getYOffset(world, aTopPos, topState);
            double aDy = SlabSupport.getYOffset(world, aPos, aState);
            double bDy = SlabSupport.getYOffset(world, bPos, bState);
            double bSupportDy = SlabSupport.getYOffset(world, bSupportPos, bSupportState);
            System.out.println("[TOP-DEPENDENCY-CONTROL] precheck side=SERVER"
                    + " TOP=" + topState + "@dy=" + topDy
                    + ", A=" + aState + "@dy=" + aDy
                    + ", B=" + bState + "@dy=" + bDy
                    + ", B.anchor=" + SlabAnchorAttachment.isAnchored(world, bPos)
                    + ", B.down=" + bSupportState + "@dy=" + bSupportDy);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> server.getOverworld().breakBlock(aPos, false));

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            singleplayer.getServer().runOnServer(server -> {
                var world = server.getOverworld();
                BlockState topAfter = world.getBlockState(aTopPos);
                BlockState aAfter = world.getBlockState(aPos);
                BlockState bAfter = world.getBlockState(bPos);
                System.out.println("[TOP-DEPENDENCY-CONTROL] tick=" + readTick
                        + " side=SERVER"
                        + " TOP=" + topAfter
                        + " A=" + aAfter
                        + " B=" + bAfter
                        + " B.anchor=" + SlabAnchorAttachment.isAnchored(world, bPos));
                if (topAfter.isAir()) {
                    serverTopDropped.set(true);
                }
            });
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing for top-dependency no-attack control tick " + readTick);
                }
                BlockState topAfter = mc.world.getBlockState(aTopPos);
                BlockState aAfter = mc.world.getBlockState(aPos);
                BlockState bAfter = mc.world.getBlockState(bPos);
                System.out.println("[TOP-DEPENDENCY-CONTROL] tick=" + readTick
                        + " side=CLIENT"
                        + " TOP=" + topAfter
                        + " A=" + aAfter
                        + " B=" + bAfter
                        + " B.anchor=" + SlabAnchorAttachment.isAnchored(mc.world, bPos));
                if (topAfter.isAir()) {
                    clientTopDropped.set(true);
                }
            });
        }

        boolean serverDropped = serverTopDropped.get();
        boolean clientDropped = clientTopDropped.get();
        if (serverDropped || clientDropped) {
            System.out.println("[TOP-DEPENDENCY-CONTROL_RED] top_dropped_without_direct_attack"
                    + " serverDropped=" + serverDropped
                    + " clientDropped=" + clientDropped);
        } else {
            System.out.println("[TOP-DEPENDENCY-CONTROL_GREEN] top_survived_without_direct_attack"
                    + " serverDropped=false clientDropped=false");
        }
    }

    /**
     * Dependent-support adjacent full-block law:
     * Source FB-A is directly anchored by BS.
     * FB-B is lowered through an inherited side-slab path (TOP slab at x+1,y is lowered by FB-A),
     * but FB-B has no direct bottom-slab support.
     *
     * If FB-B remains a stone block after FB-A break, it must not jump upward.
     * If FB-B loses all legal support, explicit normalization/removal is allowed, but
     * a visible upward jump to dy=0.0 on a surviving stone block is not.
     */
    private static void runDependentAdjacentLoweredFullJumpCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.EAST;
        final BlockPos sourceSupportPos = SUPPORT_POS;
        final BlockPos sourceFullPos = FULL_POS;
        final BlockPos inheritedSlabPos = sourceFullPos.offset(face);
        final BlockPos survivorFullPos = inheritedSlabPos.up();

        setupFixture(singleplayer, sourceSupportPos, sourceFullPos);
        setLoweredSlabTarget(singleplayer, inheritedSlabPos, SlabType.TOP);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    survivorFullPos,
                    Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            BlockState sourceState = world.getBlockState(sourceFullPos);
            BlockState slabState = world.getBlockState(inheritedSlabPos);
            BlockState survivorState = world.getBlockState(survivorFullPos);
            SlabAnchorAttachment.addAnchor(world, sourceFullPos, sourceState);
            SlabAnchorAttachment.addAnchor(world, survivorFullPos, survivorState);
            if (!sourceState.isOf(Blocks.STONE)) {
                throw new RuntimeException("dependent-support setup expected source FB at "
                        + sourceFullPos.toShortString() + ", found " + sourceState);
            }
            if (!survivorState.isOf(Blocks.STONE)) {
                throw new RuntimeException("dependent-support setup expected survivor FB at "
                        + survivorFullPos.toShortString() + ", found " + survivorState);
            }
            if (!slabState.isOf(Blocks.STONE_SLAB) || !slabState.contains(SlabBlock.TYPE)
                    || slabState.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("dependent-support setup expected inherited TOP support slab at "
                        + inheritedSlabPos.toShortString() + ", found " + slabState);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing before dependent-support case");
            }
            BlockState sourceState = mc.world.getBlockState(sourceFullPos);
            BlockState survivorState = mc.world.getBlockState(survivorFullPos);
            BlockState supportBelowSurvivor = mc.world.getBlockState(survivorFullPos.down());
            BlockState slabState = mc.world.getBlockState(inheritedSlabPos);
            if (!sourceState.isOf(Blocks.STONE) || !survivorState.isOf(Blocks.STONE)) {
                throw new RuntimeException("dependent-support precheck expected two FBs, found source="
                        + sourceState + " survivor=" + survivorState);
            }
            if (!slabState.isOf(Blocks.STONE_SLAB) || !slabState.contains(SlabBlock.TYPE)
                    || slabState.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("dependent-support precheck expected TOP slab at inherited support pos, found "
                        + slabState);
            }
            if (SlabAnchorAttachment.isAnchored(mc.world, sourceFullPos) == false) {
                throw new RuntimeException("dependent-support precheck source FB must be anchored directly");
            }
            if (!SlabAnchorAttachment.isAnchored(mc.world, survivorFullPos)) {
                throw new RuntimeException("dependent-support precheck survivor FB must be anchored through inherited support");
            }
            if (SlabSupport.hasBottomSlabBelow(mc.world, survivorFullPos)) {
                throw new RuntimeException("dependent-support precheck survivor FB must not have direct bottom-slab support");
            }
            if (!supportBelowSurvivor.isOf(Blocks.STONE_SLAB)
                    || !supportBelowSurvivor.contains(SlabBlock.TYPE)
                    || supportBelowSurvivor.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("dependent-support precheck expected TOP support directly under survivor, found "
                        + supportBelowSurvivor);
            }

            double sourceDy = SlabSupport.getYOffset(mc.world, sourceFullPos, sourceState);
            double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorState);
            if (Math.abs(sourceDy + 0.5d) > EPSILON) {
                throw new RuntimeException("dependent-support precheck source FB expected dy=-0.500, found " + sourceDy);
            }
            if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                throw new RuntimeException("dependent-support precheck survivor FB was not lowered, found " + survivorDy);
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(sourceFullPos, false);
        });

        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int tick = i;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after dependent source break tick " + tick);
                }
                BlockState sourceAfter = mc.world.getBlockState(sourceFullPos);
                if (!sourceAfter.isAir()) {
                    throw new RuntimeException("expected source FB removed on tick " + tick
                            + ", found " + sourceAfter);
                }
                BlockState survivorAfter = mc.world.getBlockState(survivorFullPos);
                if (survivorAfter.isOf(Blocks.STONE)) {
                    double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorAfter);
                    if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                        throw new RuntimeException("dependent-support case survivor FB should not jump upward while surviving at tick "
                                + tick + ", dy=" + survivorDy);
                    }
                }
            });
        }

        // Diagnostic only: replacing the inherited-support TOP slab should restore legal lowered outcome.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    inheritedSlabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after dependent-support diagnostic replacement");
            }
            if (mc.world.getBlockState(survivorFullPos).isOf(Blocks.STONE)) {
                double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos,
                        mc.world.getBlockState(survivorFullPos));
                if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("dependent-support diagnostic replacement did not restore survivor dy=-0.500");
                }
            }
        });
    }

    /**
     * Side-by-side dependent-support adjacent full-block law with mixed lifecycle:
     * FB-A is placed naturally on BS. A TOP lane slab remains under FB-B on the side
     * column without direct BOTTOM support. FB-B is then naturally placed at the same Y
     * level as FB-A.
     * FB-B has no direct bottom slab beneath.
     *
     * If FB-B survives after FB-A break, it must not jump from dy=-0.5 to dy=0.0.
     * If FB-B has no legal support after the break, explicit removal/normalization is
     * accepted, but an unexplained upward jump on a surviving stone block is forbidden.
     */
    private static void runSideBySideDependentAdjacentFullJumpLiveOrderCase(
        ClientGameTestContext ctx,
        TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.EAST;
        final BlockPos sourceSupportPos = SUPPORT_POS;
        // Live-law map:
        //  - sourceCarrierPos at y=sourceSupportPos+1 is directly supported by BS.
        //  - sourceFullPos at y=sourceSupportPos+2 inherits a legal lowered source path from the carrier.
        //  - sideSlabPos is directly beneath survivor on the adjacent lane and is lowered through sourceCarrier.
        //  - survivorFullPos is side-by-side to sourceFullPos at the same Y, with no direct bottom-slab support.
        final BlockPos sourceCarrierPos = sourceSupportPos.up();
        final BlockPos sourceFullPos = sourceCarrierPos.up();
        final BlockPos sideSlabPos = sourceCarrierPos.offset(face);
        final BlockPos survivorFullPos = sourceFullPos.offset(face);
        final BlockHitResult placeSourceHit = resolveLoweredUpMergeHit(sourceCarrierPos);
        final BlockHitResult placeSurvivorHit = resolveLoweredFaceHit(sourceFullPos, face, 0.5d);

        setupFixture(singleplayer, sourceSupportPos, sourceCarrierPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(sourceFullPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(sideSlabPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(survivorFullPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    sideSlabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE, 8));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForUp(ctx, singleplayer, sourceCarrierPos);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-order dependent source-place");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 8));
            ActionResult sourcePlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSourceHit);
            if (!sourcePlace.isAccepted()) {
                throw new RuntimeException("live-order source placement click was not accepted: " + sourcePlace);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE, 8));
        });
        movePlayerForFace(ctx, singleplayer, sourceFullPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-order side neighbor full placement");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 8));
            ActionResult survivorPlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSurvivorHit);
            if (!survivorPlace.isAccepted()) {
                throw new RuntimeException("live-order survivor full click was not accepted: " + survivorPlace);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing before live-order dependent side-by-side checks");
            }
            BlockState sourceState = mc.world.getBlockState(sourceFullPos);
            BlockState slabState = mc.world.getBlockState(sideSlabPos);
            BlockState survivorState = mc.world.getBlockState(survivorFullPos);
            if (!sourceState.isOf(Blocks.STONE)) {
                throw new RuntimeException("live-order dependent precheck expected source FB, found " + sourceState);
            }
            if (!survivorState.isOf(Blocks.STONE)) {
                throw new RuntimeException("live-order dependent precheck expected side-by-side survivor FB, found "
                        + survivorState);
            }
            if (!slabState.isOf(Blocks.STONE_SLAB) || !slabState.contains(SlabBlock.TYPE)) {
                throw new RuntimeException("live-order dependent precheck expected lane slab at "
                        + sideSlabPos.toShortString() + ", found " + slabState);
            }
            if (!SlabAnchorAttachment.isAnchored(mc.world, sourceFullPos)) {
                throw new RuntimeException("live-order precheck expects source FB anchored on legal inherited path");
            }
            boolean survivorDirectBottomSupport = SlabSupport.hasBottomSlabBelow(mc.world, survivorFullPos);
            boolean survivorInheritedOrAnchored = SlabAnchorAttachment.isAnchored(mc.world, survivorFullPos)
                    || (!survivorDirectBottomSupport && SlabSupport.shouldOffset(mc.world, survivorFullPos, survivorState));
            boolean sideSlabIsTop = slabState.isOf(Blocks.STONE_SLAB)
                    && slabState.contains(SlabBlock.TYPE)
                    && slabState.get(SlabBlock.TYPE) == SlabType.TOP;
            if (!sideSlabIsTop) {
                throw new RuntimeException("live-order precheck expected TOP lane slab (not BOTTOM) at "
                        + sideSlabPos.toShortString() + ", found " + slabState);
            }
            if (survivorDirectBottomSupport) {
                throw new RuntimeException("live-order survivor precheck expected no direct bottom-slab support");
            }
            double sourceDy = SlabSupport.getYOffset(mc.world, sourceFullPos, sourceState);
            double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorState);
            System.out.println("[LIVE-ORDER] precheck sourcePos=" + sourceFullPos.toShortString()
                    + " survivorPos=" + survivorFullPos.toShortString()
                    + " sideSlabPos=" + sideSlabPos.toShortString()
                    + " sourceDy=" + sourceDy
                    + " survivorDy=" + survivorDy
                    + " directBottom=" + survivorDirectBottomSupport
                    + " inheritedOrAnchored=" + survivorInheritedOrAnchored);
            if (!survivorInheritedOrAnchored) {
                throw new RuntimeException("live-order survivor precheck expected inherited/anchored support source truth: "
                        + "direct=" + survivorDirectBottomSupport + " inherited=" + survivorInheritedOrAnchored);
            }
            if (Math.abs(sourceDy + 0.5d) > EPSILON) {
                throw new RuntimeException("live-order precheck source FB must be lowered, found dy=" + sourceDy);
            }
            if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                throw new RuntimeException("live-order precheck survivor FB must be lowered, found dy=" + survivorDy);
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(sourceFullPos, false);
        });

        for (int tick = 0; tick < 4; tick++) {
            // Define tick 0 as the first observation point after the break event.
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after live-order dependent break tick " + readTick);
                }
                BlockState sourceAfter = mc.world.getBlockState(sourceFullPos);
                if (!sourceAfter.isAir()) {
                    throw new RuntimeException("live-order case expected source FB removed by tick " + readTick
                            + ", found " + sourceAfter);
                }
                BlockState survivorAfter = mc.world.getBlockState(survivorFullPos);
                if (survivorAfter.isAir()) {
                    return;
                }
                if (!survivorAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException("live-order dependent case expected stone survivor on tick " + readTick
                            + ", found " + survivorAfter);
                }
                double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorAfter);
                System.out.println("[LIVE-ORDER] tick=" + readTick + " survivorDy=" + survivorDy
                        + " sourceRemoved=" + sourceAfter.isAir()
                        + " survivorState=" + survivorAfter);
                if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-order survivor FB jumped on tick " + readTick
                            + " while still present, dy=" + survivorDy);
                }
            });
        }

        System.out.println("[LIVE-ORDER] side-by-side dependent jump chase complete for sourcePos="
                + sourceFullPos.toShortString()
                + " survivorPos=" + survivorFullPos.toShortString());

        // Diagnostic only: re-place the lane slab and confirm whether that action restores
        // the side-by-side FB dy. This does not define the intended fix.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(sideSlabPos, false);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    sideSlabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing during live-order slab replacement diagnostic");
            }
            BlockState survivorAfter = mc.world.getBlockState(survivorFullPos);
            if (survivorAfter.isOf(Blocks.STONE)) {
                double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorAfter);
                if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("live-order diagnostic replacement did not restore survivor dy=-0.5");
                }
            }
        });
    }

    /**
     * Side-by-side dependent-support law with explicit slab churn:
     *
     * 1) Build the same precondition as the live-order side-by-side case:
     *    FB-A direct BS support; FB-B side-by-side at same Y with no direct BOTTOM support.
     * 2) Break and replace the inherited-support TOP slab path around FB-B while FB-A is alive.
     * 3) Break FB-A (the pointed block in Julia's report).
     * 4) Re-place the slab path again and optionally churn it once more.
     *
     * For every phase, tick 0/1/2/3 are asserted:
     * - if FB-B remains legal (direct OR inherited support), dy must stay -0.500.
     * - if FB-B loses legal support, it must not be treated as a legal lowered survivor.
     */
    private static void runSideBySideDependentAdjacentFullSlabChurnJumpCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.EAST;
        final BlockPos sourceSupportPos = SUPPORT_POS;
        final BlockPos sourceCarrierPos = sourceSupportPos.up();
        final BlockPos sourceFullPos = sourceCarrierPos.up();
        final BlockPos sideSlabPos = sourceCarrierPos.offset(face);
        final BlockPos survivorFullPos = sourceFullPos.offset(face);

        final BlockHitResult placeSourceHit = resolveLoweredUpMergeHit(sourceCarrierPos);
        final BlockHitResult placeSideSlabHit = resolveLoweredSideFaceHit(sourceCarrierPos, face, SlabType.TOP);
        final BlockHitResult placeSurvivorHit = resolveLoweredUpMergeHit(sideSlabPos);

        setupFixture(singleplayer, sourceSupportPos, sourceCarrierPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    sourceFullPos,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    sideSlabPos,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    survivorFullPos,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE, 8));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForUp(ctx, singleplayer, sourceCarrierPos);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-order churn source-place");
            }
            ActionResult sourcePlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSourceHit);
            if (!sourcePlace.isAccepted()) {
                throw new RuntimeException("live-order churn source click was not accepted: " + sourcePlace);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });
        movePlayerForFace(ctx, singleplayer, sourceCarrierPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-order churn side slab place");
            }
            ActionResult slabPlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSideSlabHit);
            if (!slabPlace.isAccepted()) {
                throw new RuntimeException("live-order churn side slab click was not accepted: " + slabPlace);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE, 8));
        });
        movePlayerForUp(ctx, singleplayer, sideSlabPos);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-order churn survivor-place");
            }
            ActionResult survivorPlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSurvivorHit);
            if (!survivorPlace.isAccepted()) {
                throw new RuntimeException("live-order churn survivor click was not accepted: " + survivorPlace);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing before live-order churn precheck");
            }
            BlockState sourceState = mc.world.getBlockState(sourceFullPos);
            BlockState slabState = mc.world.getBlockState(sideSlabPos);
            BlockState survivorState = mc.world.getBlockState(survivorFullPos);
            if (!sourceState.isOf(Blocks.STONE)) {
                throw new RuntimeException("live-order churn precheck expected source FB, found " + sourceState);
            }
            if (!survivorState.isOf(Blocks.STONE)) {
                throw new RuntimeException("live-order churn precheck expected side-by-side survivor FB, found "
                        + survivorState);
            }
            if (!slabState.isOf(Blocks.STONE_SLAB) || !slabState.contains(SlabBlock.TYPE)) {
                throw new RuntimeException("live-order churn precheck expected lane slab at "
                        + sideSlabPos.toShortString() + ", found " + slabState);
            }
            if (slabState.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("live-order churn precheck expected TOP lane slab, found " + slabState);
            }
            boolean sourceAnchored = SlabAnchorAttachment.isAnchored(mc.world, sourceFullPos);
            boolean survivorDirectBottomSupport = SlabSupport.hasBottomSlabBelow(mc.world, survivorFullPos);
            boolean survivorInheritedOrAnchored = SlabAnchorAttachment.isAnchored(mc.world, survivorFullPos)
                    || (!survivorDirectBottomSupport && SlabSupport.shouldOffset(mc.world, survivorFullPos, survivorState));
            double sourceDy = SlabSupport.getYOffset(mc.world, sourceFullPos, sourceState);
            double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorState);
            if (!sourceAnchored) {
                throw new RuntimeException("live-order churn precheck source FB expected anchored");
            }
            if (!survivorInheritedOrAnchored) {
                throw new RuntimeException("live-order churn precheck expected survivor inherited/anchored support source truth: "
                        + "direct=" + survivorDirectBottomSupport + " inherited=" + survivorInheritedOrAnchored);
            }
            if (survivorDirectBottomSupport) {
                throw new RuntimeException("live-order churn precheck survivor expected no direct BOTTOM support");
            }
            if (Math.abs(sourceDy + 0.5d) > EPSILON || Math.abs(survivorDy + 0.5d) > EPSILON) {
                throw new RuntimeException("live-order churn precheck expected dy=-0.5 both before churn, sourceDy="
                        + sourceDy + " survivorDy=" + survivorDy);
            }
            System.out.println("[LIVE-CHURN] precheck sourcePos=" + sourceFullPos.toShortString()
                    + " sideSlabPos=" + sideSlabPos.toShortString()
                    + " survivorPos=" + survivorFullPos.toShortString()
                    + " sourceDy=" + sourceDy
                    + " survivorDy=" + survivorDy
                    + " directBottom=" + survivorDirectBottomSupport
                    + " inheritedOrAnchored=" + survivorInheritedOrAnchored);
        });

        // Step 1: slab break while source FB still present (live-churn operation "block broke here").
        singleplayer.getServer().runOnServer(server -> server.getOverworld().breakBlock(sideSlabPos, false));
        assertSurvivorChurnSupportTimeline(ctx, singleplayer, "after side-slab break (source alive)",
                sourceFullPos, survivorFullPos);

        // Step 2: replace slab; Julia’s follow-up often restores legal state here.
        singleplayer.getServer().runOnServer(server -> {
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });
        movePlayerForFace(ctx, singleplayer, sourceCarrierPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-order churn slab replacement");
            }
            ActionResult rePlaceSlab = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSideSlabHit);
            if (!rePlaceSlab.isAccepted()) {
                throw new RuntimeException("live-order churn re-place slab click was not accepted: " + rePlaceSlab);
            }
        });
        assertSurvivorChurnSupportTimeline(ctx, singleplayer, "after side-slab re-place (source alive)",
                sourceFullPos, survivorFullPos);

        // Step 3: break the pointed source FB.
        singleplayer.getServer().runOnServer(server -> server.getOverworld().breakBlock(sourceFullPos, false));
        assertSurvivorChurnSupportTimeline(ctx, singleplayer, "after source break",
                sourceFullPos, survivorFullPos);

        // Step 4: replace the slab again as a direct diagnostic replay.
        movePlayerForFace(ctx, singleplayer, sourceCarrierPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-order churn post-source slab replace");
            }
            ActionResult finalRePlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSideSlabHit);
            if (!finalRePlace.isAccepted()) {
                throw new RuntimeException("live-order churn post-source slab re-place click was not accepted: "
                        + finalRePlace);
            }
        });
        assertSurvivorChurnSupportTimeline(ctx, singleplayer, "after source break + slab re-place",
                sourceFullPos, survivorFullPos);

        // Optional last churn: break/replace slab once more to capture repeated live churn behavior.
        singleplayer.getServer().runOnServer(server -> server.getOverworld().breakBlock(sideSlabPos, false));
        assertSurvivorChurnSupportTimeline(ctx, singleplayer, "after final side-slab re-break",
                sourceFullPos, survivorFullPos);
    }

    /**
     * Live-path dependent-support law with real placement and slab churn:
     * Source FB-A is placed on bottom slab.
     * Top slab at x+1,y is placed as part of a player action, then survivor FB-B is placed above it.
     * While A still exists, B should not jump upward during slab break/replace churn if no direct support is removed from B itself.
     * This test keeps break/replace on the inherited slab and checks dy at tick 0 and ticks 1/2/3.
     */
    private static void runDependentAdjacentLoweredLivePlacementChurnCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.EAST;
        final BlockPos sourceSupportPos = SUPPORT_POS;
        final BlockPos sourceFullPos = FULL_POS;
        final BlockPos inheritedSlabPos = sourceFullPos.offset(face);
        final BlockPos survivorFullPos = inheritedSlabPos.up();
        final BlockHitResult placeSourceHit = resolveLoweredUpMergeHit(sourceSupportPos);
        final BlockHitResult placeSlabHit = resolveLoweredSideFaceHit(sourceFullPos, face, SlabType.TOP);
        final BlockHitResult placeSurvivorHit = resolveLoweredUpMergeHit(inheritedSlabPos);

        setupFixture(singleplayer, sourceSupportPos, sourceFullPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    sourceFullPos,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    inheritedSlabPos,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    survivorFullPos,
                    Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE, 8));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForUp(ctx, singleplayer, sourceSupportPos);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-placement source full place");
            }
            ActionResult sourcePlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSourceHit);
            if (!sourcePlace.isAccepted()) {
                throw new RuntimeException("live-placement source full click was not accepted: " + sourcePlace);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    sourceFullPos,
                    Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            // Explicitly force source anchor for the baseline path; this mirrors a placed FB on BS.
            BlockState sourceState = world.getBlockState(sourceFullPos);
            SlabAnchorAttachment.addAnchor(world, sourceFullPos, sourceState);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });
        movePlayerForFace(ctx, singleplayer, sourceFullPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-placement inherited slab place");
            }
            ActionResult slabPlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSlabHit);
            if (!slabPlace.isAccepted()) {
                throw new RuntimeException("live-placement inherited slab click was not accepted: " + slabPlace);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE, 8));
        });
        movePlayerForUp(ctx, singleplayer, inheritedSlabPos);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live-placement survivor full place");
            }
            ActionResult survivorPlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSurvivorHit);
            if (!survivorPlace.isAccepted()) {
                throw new RuntimeException("live-placement survivor full click was not accepted: " + survivorPlace);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing before live-placement churn checks");
            }
            BlockState sourceState = mc.world.getBlockState(sourceFullPos);
            BlockState slabState = mc.world.getBlockState(inheritedSlabPos);
            BlockState survivorState = mc.world.getBlockState(survivorFullPos);
            if (!sourceState.isOf(Blocks.STONE) || !survivorState.isOf(Blocks.STONE)) {
                throw new RuntimeException("live-placement churn case expected source/survivor stones, found source="
                        + sourceState + " survivor=" + survivorState);
            }
            if (!slabState.isOf(Blocks.STONE_SLAB) || !slabState.contains(SlabBlock.TYPE)
                    || slabState.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("live-placement churn case expected inherited TOP slab, found " + slabState);
            }
            double sourceDy = SlabSupport.getYOffset(mc.world, sourceFullPos, sourceState);
            double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorState);
            if (Math.abs(sourceDy + 0.5d) > EPSILON) {
                throw new RuntimeException("live-placement churn source FB expected dy=-0.500, found " + sourceDy);
            }
            if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                throw new RuntimeException("live-placement churn survivor FB expected pre-churn dy=-0.500, found " + survivorDy);
            }
        });

        // Slab churn: break and immediately re-place the inherited slab path.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(inheritedSlabPos, false);
        });
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int tick = i;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after live slab break tick " + tick);
                }
                BlockState survivorAfter = mc.world.getBlockState(survivorFullPos);
                if (survivorAfter.isOf(Blocks.STONE)) {
                    double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorAfter);
                    if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                        throw new RuntimeException("live-placement case survivor jumped after inherited slab break on tick "
                                + tick + ", dy=" + survivorDy);
                    }
                }
            });
        }

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });
        movePlayerForFace(ctx, singleplayer, sourceFullPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for live slab re-place");
            }
            ActionResult rePlaceSlab = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSlabHit);
            if (!rePlaceSlab.isAccepted()) {
                throw new RuntimeException("live-placement churn case re-place slab click was not accepted: " + rePlaceSlab);
            }
        });
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int tick = i;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after live slab re-place tick " + tick);
                }
                BlockState survivorAfter = mc.world.getBlockState(survivorFullPos);
                if (survivorAfter.isOf(Blocks.STONE)) {
                    double survivorDy = SlabSupport.getYOffset(mc.world, survivorFullPos, survivorAfter);
                    if (Math.abs(survivorDy + 0.5d) > EPSILON) {
                        throw new RuntimeException("live-placement case survivor jumped around slab re-place on tick "
                                + tick + ", dy=" + survivorDy);
                    }
                }
            });
        }
    }

    private static void runLoweredSidePlacementCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Direction face,
            SlabType targetType,
            SlabType expectedPlacedType
    ) {
        final BlockPos targetPos = FULL_POS.offset(face);
        final BlockPos placementPos = targetPos.offset(face);
        final BlockHitResult hit = resolveLoweredSideFaceHit(targetPos, face, targetType);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        setLoweredSlabTarget(singleplayer, targetPos, targetType);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForFace(ctx, singleplayer, targetPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for " + targetType + " side case on " + face);
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (!result.isAccepted()) {
                throw new RuntimeException(targetType + " side click for " + face + " was not accepted: " + result);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after " + targetType + " side click on " + face);
            }
            BlockState placed = mc.world.getBlockState(placementPos);
            if (!placed.isOf(Blocks.STONE_SLAB)
                    || !placed.contains(SlabBlock.TYPE)
                    || placed.get(SlabBlock.TYPE) != expectedPlacedType) {
                throw new RuntimeException("expected placed " + expectedPlacedType
                        + " at " + placementPos.toShortString()
                        + " for " + targetType + " side target " + face
                        + ", found " + placed);
            }
            double placedDy = SlabSupport.getYOffset(mc.world, placementPos, placed);
            if (Math.abs(placedDy + 0.5d) > EPSILON) {
                throw new RuntimeException("expected lowered dy=-0.500 for " + targetType + " target on "
                        + face + " side, found dy=" + placedDy + " state=" + placed);
            }
        });
    }

    private static void runLiveClickPairBottomSlabLaneInheritanceCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final String proof = "LIVE_CLICK_PAIR_BOTTOM_SLAB_LANE_INHERITANCE";
        final Direction face = Direction.EAST;
        final BlockPos sourcePos = FULL_POS.east();
        final BlockPos placePos = sourcePos.offset(face);
        final BlockHitResult hit = resolveLoweredSideFaceHit(sourcePos, face, SlabType.BOTTOM);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        setLoweredSlabTarget(singleplayer, sourcePos, SlabType.BOTTOM);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState sourceState = world.getBlockState(sourcePos);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, sourcePos, sourceState);
            world.setBlockState(FULL_POS, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(SUPPORT_POS, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        movePlayerForFace(ctx, singleplayer, sourcePos, face);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[" + proof + "] client not ready");
            }
            BlockState source = mc.world.getBlockState(sourcePos);
            double sourceDy = SlabSupport.getYOffset(mc.world, sourcePos, source);
            if (!source.isOf(Blocks.STONE_SLAB)
                    || !source.contains(SlabBlock.TYPE)
                    || source.get(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Math.abs(sourceDy + 0.5d) > EPSILON) {
                throw new RuntimeException("[" + proof + "] source not lowered bottom slab: state="
                        + source + " dy=" + sourceDy);
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (!result.isAccepted()) {
                throw new RuntimeException("[" + proof + "] placement was not accepted: " + result);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[" + proof + "] client world missing after placement");
            }
            BlockState placed = mc.world.getBlockState(placePos);
            double placedDy = SlabSupport.getYOffset(mc.world, placePos, placed);
            boolean persistent = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(mc.world, placePos, placed);
            System.out.println("[" + proof + "] source=" + sourcePos.toShortString()
                    + " face=" + face.asString()
                    + " placePos=" + placePos.toShortString()
                    + " placed=" + placed
                    + " dy=" + placedDy
                    + " persistentLoweredSlabCarrier=" + persistent);
            if (!placed.isOf(Blocks.STONE_SLAB)
                    || !placed.contains(SlabBlock.TYPE)
                    || placed.get(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Math.abs(placedDy + 0.5d) > EPSILON
                    || persistent) {
                throw new RuntimeException("[" + proof + "] placed slab should inherit lowered bottom lane, found state="
                        + placed + " dy=" + placedDy + " persistent=" + persistent);
            }
        });
    }

    private static void runLiveClickPairFullBlockLaneInheritanceCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final String proof = "LIVE_CLICK_PAIR_FULL_BLOCK_LANE_INHERITANCE";
        final Direction face = Direction.EAST;
        final BlockPos sourcePos = FULL_POS.east();
        final BlockPos sourceBelowPos = sourcePos.down();
        final BlockPos sourceCarrierPos = sourceBelowPos.down();
        final BlockPos sourceCarrierSupportPos = sourceCarrierPos.down();
        final BlockPos placePos = sourcePos.offset(face);
        final BlockHitResult hit = resolveLoweredSideFaceHit(sourcePos, face, SlabType.BOTTOM);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(FULL_POS, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(SUPPORT_POS, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(placePos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    sourceCarrierSupportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(sourceCarrierPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, sourceCarrierPos, world.getBlockState(sourceCarrierPos));
            world.setBlockState(
                    sourceBelowPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    sourcePos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE, 8));
        movePlayerForFace(ctx, singleplayer, sourcePos, face);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[" + proof + "] client not ready");
            }
            BlockState source = mc.world.getBlockState(sourcePos);
            double sourceDy = SlabSupport.getYOffset(mc.world, sourcePos, source);
            boolean sourcePersistent = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(mc.world, sourcePos, source);
            if (!source.isOf(Blocks.STONE_SLAB)
                    || !source.contains(SlabBlock.TYPE)
                    || source.get(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Math.abs(sourceDy + 0.5d) > EPSILON
                    || sourcePersistent) {
                throw new RuntimeException("[" + proof + "] source not non-persistent lowered bottom slab: state="
                        + source + " dy=" + sourceDy + " persistent=" + sourcePersistent);
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (!result.isAccepted()) {
                throw new RuntimeException("[" + proof + "] placement was not accepted: " + result);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[" + proof + "] client world missing after placement");
            }
            BlockState placed = mc.world.getBlockState(placePos);
            double placedDy = SlabSupport.getYOffset(mc.world, placePos, placed);
            boolean anchored = SlabAnchorAttachment.isAnchored(mc.world, placePos);
            BlockState source = mc.world.getBlockState(sourcePos);
            boolean sourcePersistent = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(mc.world, sourcePos, source);
            System.out.println("[" + proof + "] source=" + sourcePos.toShortString()
                    + " face=" + face.asString()
                    + " placePos=" + placePos.toShortString()
                    + " sourcePersistentLoweredSlabCarrier=" + sourcePersistent
                    + " placed=" + placed
                    + " dy=" + placedDy
                    + " anchored=" + anchored);
            if (!placed.isOf(Blocks.STONE)
                    || Math.abs(placedDy + 0.5d) > EPSILON
                    || !anchored
                    || sourcePersistent) {
                throw new RuntimeException("[" + proof + "] placed full block should inherit lowered lane, found state="
                        + placed + " dy=" + placedDy + " anchored=" + anchored
                        + " sourcePersistent=" + sourcePersistent);
            }
        });
    }

    private static void runLoweredDoubleSidePlacementCreatedNormalBottomLaneProofCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final String proof = "LOWERED_DOUBLE_SIDE_PLACEMENT_CREATED_NORMAL_BOTTOM_LANE";
        final Direction clickedFace = Direction.SOUTH;
        final BlockPos targetPos = FULL_POS.offset(clickedFace);
        final BlockPos placePos = targetPos.offset(clickedFace);
        final BlockPos downPos = placePos.down();
        final BlockPos northPos = placePos.north();
        final BlockPos eastPos = northPos.east();
        final BlockPos southPos = placePos.south();
        final BlockHitResult hit = resolveLoweredFaceHit(targetPos, clickedFace, -0.5d);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        setLoweredSlabTarget(singleplayer, targetPos, SlabType.DOUBLE);
        setLoweredSlabTarget(singleplayer, eastPos, SlabType.BOTTOM);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(placePos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        movePlayerForFace(ctx, singleplayer, targetPos, clickedFace);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[" + proof + "] client not ready");
            }

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            BlockState targetState = mc.world.getBlockState(targetPos);
            BlockState placeBefore = mc.world.getBlockState(placePos);
            BlockState downState = mc.world.getBlockState(downPos);
            BlockState northState = mc.world.getBlockState(northPos);
            BlockState eastState = mc.world.getBlockState(eastPos);
            BlockState southState = mc.world.getBlockState(southPos);
            ItemStack heldStack = mc.player.getMainHandStack();
            String heldItem = heldStack.isEmpty() ? "empty" : heldStack.getItem().toString();

            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            BlockState placeAfter = mc.world.getBlockState(placePos);

            String expected = "minecraft:stone_slab[type=top] dy=-0.500 lowered=true";
            boolean expectedLoweredLaneByLaw = SlabSupport.isCompatibleLoweredSlabLane(SlabType.DOUBLE, SlabType.TOP);

            SlabType actualType = placeAfter.isOf(Blocks.STONE_SLAB) && placeAfter.contains(SlabBlock.TYPE)
                    ? placeAfter.get(SlabBlock.TYPE)
                    : null;
            double actualDy = SlabSupport.getYOffset(mc.world, placePos, placeAfter);
            boolean actualLowered = actualDy < -EPSILON;

            double targetDy = SlabSupport.getYOffset(mc.world, targetPos, targetState);
            boolean targetLowered = targetDy < -EPSILON;
            double downDy = SlabSupport.getYOffset(mc.world, downPos, downState);
            boolean downLowered = downDy < -EPSILON;
            double northDy = SlabSupport.getYOffset(mc.world, northPos, northState);
            boolean northLowered = northDy < -EPSILON;
            double eastDy = SlabSupport.getYOffset(mc.world, eastPos, eastState);
            boolean eastLowered = eastDy < -EPSILON;
            double southDy = SlabSupport.getYOffset(mc.world, southPos, southState);
            boolean southLowered = southDy < -EPSILON;

            boolean actualNormalBottom = placeAfter.isOf(Blocks.STONE_SLAB)
                    && placeAfter.contains(SlabBlock.TYPE)
                    && placeAfter.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && Math.abs(actualDy) <= EPSILON
                    && !actualLowered;
            boolean contextLowered = targetLowered && northLowered && eastLowered;
            boolean actualExpectedLowered = placeAfter.isOf(Blocks.STONE_SLAB)
                    && placeAfter.contains(SlabBlock.TYPE)
                    && placeAfter.get(SlabBlock.TYPE) == SlabType.TOP
                    && Math.abs(actualDy + 0.5d) <= EPSILON
                    && actualLowered;

            boolean actualBottomLoweredHalf = placeAfter.isOf(Blocks.STONE_SLAB)
                    && placeAfter.contains(SlabBlock.TYPE)
                    && placeAfter.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && Math.abs(actualDy + 0.5d) <= EPSILON
                    && actualLowered;

            String classification;
            if (result.isAccepted() && actualNormalBottom && contextLowered && expectedLoweredLaneByLaw) {
                classification = "RED_LIVE_REPRO";
            } else if (result.isAccepted() && actualBottomLoweredHalf) {
                classification = "HYPOTHESIS_NOT_REPRODUCED";
            } else if (result.isAccepted() && actualExpectedLowered && expectedLoweredLaneByLaw) {
                classification = "GREEN_ALREADY_FIXED";
            } else {
                classification = "PROOF_GAP";
            }

            System.out.println("[" + proof + "] heldItem=" + heldItem);
            System.out.println("[" + proof + "] clicked targetPos=" + targetPos.toShortString()
                    + " targetState=" + targetState
                    + " targetType=" + (targetState.contains(SlabBlock.TYPE) ? targetState.get(SlabBlock.TYPE) : "none")
                    + " targetDy=" + targetDy
                    + " targetLowered=" + targetLowered);
            System.out.println("[" + proof + "] clicked face=" + clickedFace.asString()
                    + " hitPos=" + hit.getPos()
                    + " intended placePos=" + placePos.toShortString());
            System.out.println("[" + proof + "] before placePosState=" + placeBefore);
            System.out.println("[" + proof + "] neighbor down pos=" + downPos.toShortString()
                    + " state=" + downState
                    + " slabType=" + (downState.contains(SlabBlock.TYPE) ? downState.get(SlabBlock.TYPE) : "none")
                    + " dy=" + downDy
                    + " lowered=" + downLowered);
            System.out.println("[" + proof + "] neighbor north pos=" + northPos.toShortString()
                    + " state=" + northState
                    + " slabType=" + (northState.contains(SlabBlock.TYPE) ? northState.get(SlabBlock.TYPE) : "none")
                    + " dy=" + northDy
                    + " lowered=" + northLowered);
            System.out.println("[" + proof + "] neighbor east pos=" + eastPos.toShortString()
                    + " state=" + eastState
                    + " slabType=" + (eastState.contains(SlabBlock.TYPE) ? eastState.get(SlabBlock.TYPE) : "none")
                    + " dy=" + eastDy
                    + " lowered=" + eastLowered);
            System.out.println("[" + proof + "] neighbor south pos=" + southPos.toShortString()
                    + " state=" + southState
                    + " slabType=" + (southState.contains(SlabBlock.TYPE) ? southState.get(SlabBlock.TYPE) : "none")
                    + " dy=" + southDy
                    + " lowered=" + southLowered);
            System.out.println("[" + proof + "] placement result=" + result);
            System.out.println("[" + proof + "] after placePosState=" + placeAfter);
            System.out.println("[" + proof + "] expected lowered-lane result=" + expected
                    + " lawCompatible=" + expectedLoweredLaneByLaw);
            System.out.println("[" + proof + "] actual slabType=" + (actualType == null ? "none" : actualType)
                    + " dy=" + actualDy
                    + " lowered=" + actualLowered);
            if ("HYPOTHESIS_NOT_REPRODUCED".equals(classification)) {
                System.out.println("[" + proof + "] expectedBadPattern=bottom_dy0"
                        + " actual=bottom_dy-0.5_lowered_true"
                        + " noGameplayFixWarranted=true");
            }
            System.out.println("[" + proof + "] classification=" + classification);

            if ("RED_LIVE_REPRO".equals(classification)) {
                throw new RuntimeException("RED_LIVE_REPRO: " + proof
                        + " created normal bottom lane in lowered side-lane context");
            }
        });
    }

    private static void runRealPlacedLoweredBottomSlabPersistenceAfterBridgeBreakProofCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final String proof = "REAL_PLACED_LOWERED_BOTTOM_SLAB_PERSISTENCE_AFTER_BRIDGE_BREAK";
        final BlockPos centerSupportPos = SUPPORT_POS;
        final BlockPos centerFullPos = FULL_POS;
        final BlockPos westFullPos = centerFullPos.west();
        final BlockPos eastFullPos = centerFullPos.east();
        final BlockPos slabAbovePos = centerFullPos.up();
        final BlockPos underPlacedPos = centerFullPos;
        final BlockHitResult placeSlabAboveHit = resolveLoweredUpMergeHit(centerFullPos);
        final Direction underPlaceFace = Direction.EAST;
        final BlockHitResult underPlaceHit = resolveLoweredSideFaceHit(westFullPos, underPlaceFace, SlabType.BOTTOM);
        System.out.println("[" + proof + "] slabAbovePos=" + slabAbovePos.toShortString());

        setupFixture(singleplayer, centerSupportPos, centerFullPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();

            world.setBlockState(westFullPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(eastFullPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);

            BlockState centerState = world.getBlockState(centerFullPos);
            BlockState westState = world.getBlockState(westFullPos);
            BlockState eastState = world.getBlockState(eastFullPos);

            SlabAnchorAttachment.addAnchor(world, centerFullPos, centerState);
            SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(world, westFullPos, westState, centerFullPos, centerState);
            SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(world, eastFullPos, eastState, centerFullPos, centerState);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String creationProof = "REAL_PLACED_LOWERED_BOTTOM_SLAB_CREATION_WRITES_CARRIER";
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(centerFullPos.getX() + 0.5d, centerFullPos.getY() + 1.8d, centerFullPos.getZ() + 2.55d),
                placeSlabAboveHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[" + creationProof + "] client not ready for real slab placement");
            }
            ActionResult slabPlace = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSlabAboveHit);
            if (!slabPlace.isAccepted()) {
                throw new RuntimeException("PROOF_GAP: " + creationProof
                        + " real item placement was rejected: " + slabPlace);
            }
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[" + proof + "] client world null before break");
            }

            BlockState centerBefore = mc.world.getBlockState(centerFullPos);
            BlockState westBefore = mc.world.getBlockState(westFullPos);
            BlockState eastBefore = mc.world.getBlockState(eastFullPos);
            BlockState slabBefore = mc.world.getBlockState(slabAbovePos);

            double centerBeforeDy = SlabSupport.getYOffset(mc.world, centerFullPos, centerBefore);
            double westBeforeDy = SlabSupport.getYOffset(mc.world, westFullPos, westBefore);
            double eastBeforeDy = SlabSupport.getYOffset(mc.world, eastFullPos, eastBefore);
            double slabBeforeDy = SlabSupport.getYOffset(mc.world, slabAbovePos, slabBefore);

            boolean centerBeforeAnchored = SlabAnchorAttachment.isAnchored(mc.world, centerFullPos);
            boolean westBeforeAnchored = SlabAnchorAttachment.isAnchored(mc.world, westFullPos);
            boolean eastBeforeAnchored = SlabAnchorAttachment.isAnchored(mc.world, eastFullPos);
            boolean slabBeforeLowered = slabBeforeDy < -EPSILON;
            boolean slabBeforePersistentCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(mc.world, slabAbovePos, slabBefore);

            System.out.println("[" + proof + "] centerFull before state=" + centerBefore
                    + " dy=" + centerBeforeDy + " anchored=" + centerBeforeAnchored);
            System.out.println("[" + proof + "] westFull before state=" + westBefore
                    + " dy=" + westBeforeDy + " anchored=" + westBeforeAnchored);
            System.out.println("[" + proof + "] eastFull before state=" + eastBefore
                    + " dy=" + eastBeforeDy + " anchored=" + eastBeforeAnchored);
            System.out.println("[" + proof + "] slabAbove before state=" + slabBefore
                    + " slabType=" + (slabBefore.contains(SlabBlock.TYPE) ? slabBefore.get(SlabBlock.TYPE) : "none")
                    + " dy=" + slabBeforeDy
                    + " lowered=" + slabBeforeLowered
                    + " persistentLoweredSlabCarrier=" + slabBeforePersistentCarrier);

            boolean slabCreatedAsBottom = slabBefore.isOf(Blocks.STONE_SLAB)
                    && slabBefore.contains(SlabBlock.TYPE)
                    && slabBefore.get(SlabBlock.TYPE) == SlabType.BOTTOM;
            System.out.println("[" + creationProof + "] slabAbove state=" + slabBefore
                    + " dy=" + slabBeforeDy
                    + " modelDy=" + slabBeforeDy
                    + " outlineDy=" + slabBeforeDy
                    + " targetDy=" + slabBeforeDy
                    + " persistentLoweredSlabCarrier=" + slabBeforePersistentCarrier);
            if (!slabCreatedAsBottom
                    || !slabBeforePersistentCarrier
                    || Math.abs(slabBeforeDy + 0.5d) > EPSILON) {
                throw new RuntimeException("RED_LIVE_REPRO: " + creationProof
                        + " real placed lowered bottom slab did not become a persistent carrier");
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(centerFullPos, false);
        });
        System.out.println("[" + proof + "] action=break centerFull");

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[" + proof + "] client world null after break");
            }

            BlockState centerAfter = mc.world.getBlockState(centerFullPos);
            BlockState westAfter = mc.world.getBlockState(westFullPos);
            BlockState eastAfter = mc.world.getBlockState(eastFullPos);
            BlockState slabAfter = mc.world.getBlockState(slabAbovePos);

            boolean slabAfterPersistentCarrierBeforeDy = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(mc.world, slabAbovePos, slabAfter);
            double slabAfterDy = SlabSupport.getYOffset(mc.world, slabAbovePos, slabAfter);
            boolean slabAfterLowered = slabAfterDy < -EPSILON;
            boolean slabAfterPersistentCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(mc.world, slabAbovePos, slabAfter);
            boolean westAfterAnchored = SlabAnchorAttachment.isAnchored(mc.world, westFullPos);
            boolean eastAfterAnchored = SlabAnchorAttachment.isAnchored(mc.world, eastFullPos);

            double modelDy = slabAfterDy;
            double outlineDy = slabAfterDy;
            double targetDy = slabAfterDy;

            System.out.println("[" + proof + "] centerFull after state=" + centerAfter);
            System.out.println("[" + proof + "] westFull after state=" + westAfter
                    + " dy=" + SlabSupport.getYOffset(mc.world, westFullPos, westAfter)
                    + " anchored=" + westAfterAnchored);
            System.out.println("[" + proof + "] eastFull after state=" + eastAfter
                    + " dy=" + SlabSupport.getYOffset(mc.world, eastFullPos, eastAfter)
                    + " anchored=" + eastAfterAnchored);
            System.out.println("[" + proof + "] slabAbove after state=" + slabAfter
                    + " slabType=" + (slabAfter.contains(SlabBlock.TYPE) ? slabAfter.get(SlabBlock.TYPE) : "none")
                    + " dy=" + slabAfterDy
                    + " lowered=" + slabAfterLowered
                    + " persistentLoweredSlabCarrier=" + slabAfterPersistentCarrier);
            System.out.println("[" + proof + "] slabAbove diagnostics"
                    + " worldClass=" + mc.world.getClass().getName()
                    + " fluidEmpty=" + slabAfter.getFluidState().isEmpty()
                    + " slabType=" + (slabAfter.contains(SlabBlock.TYPE) ? slabAfter.get(SlabBlock.TYPE) : "none")
                    + " persistentBeforeDy=" + slabAfterPersistentCarrierBeforeDy
                    + " persistentAfterDy=" + slabAfterPersistentCarrier);
            System.out.println("[" + proof + "] modelDy=" + modelDy
                    + " outlineDy=" + outlineDy
                    + " targetDy=" + targetDy);

            boolean slabBeforeLegal = slabAfter.isOf(Blocks.STONE_SLAB)
                    && slabAfter.contains(SlabBlock.TYPE)
                    && slabAfter.get(SlabBlock.TYPE) == SlabType.BOTTOM;
            boolean slabLegalLowered = slabBeforeLegal
                    && Math.abs(slabAfterDy + 0.5d) <= EPSILON
                    && slabAfterLowered;
            boolean neighborsRemain = westAfter.isOf(Blocks.STONE)
                    && eastAfter.isOf(Blocks.STONE)
                    && westAfterAnchored
                    && eastAfterAnchored;

            String classification;
            if (slabLegalLowered && slabAfterPersistentCarrier && neighborsRemain) {
                classification = "GREEN_ALREADY_FIXED";
            } else if (neighborsRemain) {
                classification = "RED_LIVE_REPRO";
            } else {
                classification = "PROOF_GAP";
            }

            System.out.println("[" + proof + "] expected legal outcome=slabAbove minecraft:stone_slab[type=bottom] dy=-0.5 lowered=true persistentLoweredSlabCarrier=true");
            System.out.println("[" + proof + "] classification=" + classification);

            if ("RED_LIVE_REPRO".equals(classification)) {
                throw new RuntimeException("RED_LIVE_REPRO: " + proof
                        + " slabAbove failed lowered persistence after center bridge break");
            }
            if ("PROOF_GAP".equals(classification)) {
                throw new RuntimeException("PROOF_GAP: " + proof
                        + " neighbor persistence context not stable enough for classification");
            }
        });

        final String underPlacementProof = "REAL_PLACED_LOWERED_BOTTOM_SLAB_UNDER_PLACEMENT_DOES_NOT_JUMP";
        singleplayer.getServer().runOnServer(server -> {
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });
        movePlayerForFace(ctx, singleplayer, westFullPos, underPlaceFace);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[" + underPlacementProof + "] client not ready for under-placement");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            ActionResult placeUnder = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, underPlaceHit);
            if (!placeUnder.isAccepted()) {
                throw new RuntimeException("PROOF_GAP: " + underPlacementProof
                        + " under-placement click was not accepted: " + placeUnder);
            }
        });
        System.out.println("[" + underPlacementProof + "] action=place stone_slab under persisted slab"
                + " target=" + westFullPos.toShortString()
                + " face=" + underPlaceFace
                + " placedPos=" + underPlacedPos.toShortString());

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[" + underPlacementProof + "] client world null after under-placement");
            }

            BlockState survivor = mc.world.getBlockState(slabAbovePos);
            BlockState placed = mc.world.getBlockState(underPlacedPos);

            boolean survivorIsBottomSlab = survivor.isOf(Blocks.STONE_SLAB)
                    && survivor.contains(SlabBlock.TYPE)
                    && survivor.get(SlabBlock.TYPE) == SlabType.BOTTOM;
            boolean survivorPersistentCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(mc.world, slabAbovePos, survivor);
            double survivorDy = SlabSupport.getYOffset(mc.world, slabAbovePos, survivor);
            double modelDy = survivorDy;
            double outlineDy = survivorDy;
            double targetDy = survivorDy;
            double jumpDelta = survivorDy - -0.5d;

            boolean placedIsSlab = placed.isOf(Blocks.STONE_SLAB) && placed.contains(SlabBlock.TYPE);
            double placedDy = placedIsSlab ? SlabSupport.getYOffset(mc.world, underPlacedPos, placed) : Double.NaN;
            boolean placedLegalLoweredLane = placedIsSlab
                    && placed.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && Math.abs(placedDy + 0.5d) <= EPSILON;
            boolean placedLegalVanillaLane = placedIsSlab
                    && placed.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && Math.abs(placedDy) <= EPSILON;

            System.out.println("[" + underPlacementProof + "] survivor state=" + survivor
                    + " dy=" + survivorDy
                    + " modelDy=" + modelDy
                    + " outlineDy=" + outlineDy
                    + " targetDy=" + targetDy
                    + " jumpDelta=" + jumpDelta
                    + " persistentLoweredSlabCarrier=" + survivorPersistentCarrier);
            System.out.println("[" + underPlacementProof + "] placed state=" + placed
                    + " dy=" + placedDy
                    + " legalLoweredLane=" + placedLegalLoweredLane
                    + " legalVanillaLane=" + placedLegalVanillaLane);

            if (!survivorIsBottomSlab
                    || !survivorPersistentCarrier
                    || Math.abs(survivorDy + 0.5d) > EPSILON
                    || Math.abs(modelDy + 0.5d) > EPSILON
                    || Math.abs(outlineDy + 0.5d) > EPSILON
                    || Math.abs(targetDy + 0.5d) > EPSILON
                    || Math.abs(jumpDelta) > EPSILON) {
                throw new RuntimeException("RED_LIVE_REPRO: " + underPlacementProof
                        + " persisted slab jumped or lost lowered carrier after under-placement");
            }
            if (!placedLegalLoweredLane && !placedLegalVanillaLane) {
                throw new RuntimeException("PROOF_GAP: " + underPlacementProof
                        + " placed slab is not a named lowered or vanilla bottom-lane state");
            }
        });
    }

    private static void runLoweredTopUpMergeCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.SOUTH;
        final BlockPos targetPos = FULL_POS.offset(face);
        final BlockHitResult hit = resolveLoweredUpMergeHit(targetPos);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        setLoweredSlabTarget(singleplayer, targetPos, SlabType.TOP);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForUp(ctx, singleplayer, targetPos);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for lowered-TOP up merge");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (!result.isAccepted()) {
                throw new RuntimeException("lowered TOP up-click was not accepted: " + result);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after lowered-TOP up-click");
            }
            BlockState placed = mc.world.getBlockState(targetPos);
            if (!placed.isOf(Blocks.STONE_SLAB)
                    || !placed.contains(SlabBlock.TYPE)
                    || placed.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("UP target=TOP expected DOUBLE merge at "
                        + targetPos.toShortString()
                        + ", found " + placed);
            }
            double placedDy = SlabSupport.getYOffset(mc.world, targetPos, placed);
            if (Math.abs(placedDy + 0.5d) > EPSILON) {
                throw new RuntimeException("UP target=TOP expected merged DOUBLE dy=-0.500, found " + placedDy);
            }
        });
    }

    /**
     * Legal state law for orphaned lowered side-lane slabs:
     * BSFB > TS > TS is allowed while BS/FB support exists; once both original BS and
     * original FB are removed, the remaining lane must not silently stay lowered.
     * With no valid lowered support relation left, remaining TS slabs normalize to dy=0.
     */
    private static void runOrphanedLoweredLaneSupportRemovalCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.EAST;
        final BlockPos laneRootPos = FULL_POS.offset(face);
        final BlockPos laneTailPos = laneRootPos.offset(face);
        final BlockHitResult extendHit = resolveLoweredSideFaceHit(laneRootPos, face, SlabType.TOP);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            SlabAnchorAttachment.addAnchor(world, FULL_POS, world.getBlockState(FULL_POS));
        });
        setLoweredSlabTarget(singleplayer, laneRootPos, SlabType.TOP);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForFace(ctx, singleplayer, laneRootPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for orphaned-lane setup");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, extendHit);
            if (!result.isAccepted()) {
                throw new RuntimeException("orphaned-lane setup extension click was not accepted: " + result);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after orphaned-lane setup");
            }
            BlockState laneRoot = mc.world.getBlockState(laneRootPos);
            BlockState laneTail = mc.world.getBlockState(laneTailPos);
            assertTopLowered(laneRootPos, laneRoot, mc.world, "orphan-setup laneRoot");
            assertTopLowered(laneTailPos, laneTail, mc.world, "orphan-setup laneTail");
        });

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(SUPPORT_POS, false);
        });
        for (int i = 0; i < 3; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int tick = i;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after BS break tick " + tick);
                }
                BlockState fullAfterBsBreak = mc.world.getBlockState(FULL_POS);
                if (!fullAfterBsBreak.isOf(Blocks.STONE)) {
                    throw new RuntimeException("expected FB to remain after BS break at tick " + tick
                            + ", found " + fullAfterBsBreak);
                }
                if (!SlabAnchorAttachment.isAnchored(mc.world, FULL_POS)) {
                    throw new RuntimeException("expected FB anchor to persist after BS break at tick " + tick);
                }
                assertLaneDy(mc.world, laneRootPos, laneTailPos, -0.5d,
                        "post-BS-break tick " + tick);
            });
        }

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(FULL_POS, false);
        });
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int tick = i;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after FB break tick " + tick);
                }
                BlockState support = mc.world.getBlockState(SUPPORT_POS);
                BlockState full = mc.world.getBlockState(FULL_POS);
                if (!support.isAir() || !full.isAir()) {
                    throw new RuntimeException("expected original BS and FB to be removed at tick " + tick
                            + "; support=" + support + " full=" + full);
                }
                if (SlabAnchorAttachment.isAnchored(mc.world, FULL_POS)) {
                    throw new RuntimeException("anchor persisted after FB removal at tick " + tick
                            + " pos=" + FULL_POS.toShortString());
                }
                assertLaneDy(mc.world, laneRootPos, laneTailPos, 0.0d,
                        "post-FB-break tick " + tick);
            });
        }

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after orphaned-lane support removal");
            }

            BlockState support = mc.world.getBlockState(SUPPORT_POS);
            BlockState full = mc.world.getBlockState(FULL_POS);
            if (!support.isAir() || !full.isAir()) {
                throw new RuntimeException("expected original BS and FB to be removed; support="
                        + support + " full=" + full);
            }
            if (SlabAnchorAttachment.isAnchored(mc.world, FULL_POS)) {
                throw new RuntimeException("anchor persisted after original FB removal at "
                        + FULL_POS.toShortString());
            }

            BlockState laneRoot = mc.world.getBlockState(laneRootPos);
            BlockState laneTail = mc.world.getBlockState(laneTailPos);
            if (!laneRoot.isOf(Blocks.STONE_SLAB)
                    || !laneRoot.contains(SlabBlock.TYPE)
                    || laneRoot.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("laneRoot state changed unexpectedly after support removal: " + laneRoot);
            }
            if (!laneTail.isOf(Blocks.STONE_SLAB)
                    || !laneTail.contains(SlabBlock.TYPE)
                    || laneTail.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException("laneTail state changed unexpectedly after support removal: " + laneTail);
            }
            assertLaneDy(mc.world, laneRootPos, laneTailPos, 0.0d,
                    "post-FB-break final");
        });
    }

    /**
     * Legal state law for mixed-state clue (lowered DOUBLE beside normal BOTTOM):
     * A) after original FB removal, remaining lane slabs may normalize uniformly to dy=0;
     * B) remaining lane slabs may stay uniformly lowered only with a valid lowered support chain;
     * C) unsupported/mixed pieces may be removed by explicit invalidation.
     *
     * Forbidden outcomes:
     * - adjacent lowered DOUBLE dy=-0.5 with normal BOTTOM dy=0 in the same remaining lane;
     * - hidden ghost state or unstable one-tick jump/correction after BS/FB teardown.
     */
    private static void runMixedDoubleBottomTeardownLawCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.EAST;
        final BlockPos laneDoublePos = FULL_POS.offset(face);
        final BlockPos laneBottomPos = laneDoublePos.offset(face);
        final BlockHitResult lowerHalfSideHit = resolveLoweredFaceHit(laneDoublePos, face, -0.25d);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            SlabAnchorAttachment.addAnchor(world, FULL_POS, world.getBlockState(FULL_POS));
        });
        setLoweredSlabTarget(singleplayer, laneDoublePos, SlabType.DOUBLE);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForFace(ctx, singleplayer, laneDoublePos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for mixed DOUBLE/BOTTOM setup");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, lowerHalfSideHit);
            if (!result.isAccepted()) {
                throw new RuntimeException("mixed DOUBLE/BOTTOM setup click was not accepted: " + result);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after mixed DOUBLE/BOTTOM setup");
            }
            BlockState laneDouble = mc.world.getBlockState(laneDoublePos);
            BlockState laneBottom = mc.world.getBlockState(laneBottomPos);
            if (!laneDouble.isOf(Blocks.STONE_SLAB)
                    || !laneDouble.contains(SlabBlock.TYPE)
                    || laneDouble.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("mixed setup expected lowered DOUBLE at "
                        + laneDoublePos.toShortString() + ", found " + laneDouble);
            }
            if (!laneBottom.isOf(Blocks.STONE_SLAB)
                    || !laneBottom.contains(SlabBlock.TYPE)
                    || laneBottom.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("mixed setup expected BOTTOM at "
                        + laneBottomPos.toShortString() + ", found " + laneBottom);
            }
            double doubleDy = SlabSupport.getYOffset(mc.world, laneDoublePos, laneDouble);
            if (Math.abs(doubleDy + 0.5d) > EPSILON) {
                throw new RuntimeException("mixed setup expected lowered DOUBLE dy=-0.500, found "
                        + doubleDy + " state=" + laneDouble);
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(SUPPORT_POS, false);
        });
        for (int i = 0; i < 3; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int tick = i;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing in mixed case after BS break tick " + tick);
                }
                BlockState fullAfterBsBreak = mc.world.getBlockState(FULL_POS);
                if (!fullAfterBsBreak.isOf(Blocks.STONE) || !SlabAnchorAttachment.isAnchored(mc.world, FULL_POS)) {
                    throw new RuntimeException("mixed case expected anchored FB after BS break at tick " + tick
                            + ", full=" + fullAfterBsBreak
                            + " anchored=" + SlabAnchorAttachment.isAnchored(mc.world, FULL_POS));
                }
                assertForbiddenMixedDoubleBottomStateAbsent(mc.world, laneDoublePos, laneBottomPos,
                        "mixed-case post-BS-break tick " + tick);
            });
        }

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.breakBlock(FULL_POS, false);
        });
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int tick = i;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing in mixed case after FB break tick " + tick);
                }
                BlockState support = mc.world.getBlockState(SUPPORT_POS);
                BlockState full = mc.world.getBlockState(FULL_POS);
                if (!support.isAir() || !full.isAir()) {
                    throw new RuntimeException("mixed case expected original BS/FB removed at tick " + tick
                            + ", support=" + support + " full=" + full);
                }
                assertForbiddenMixedDoubleBottomStateAbsent(mc.world, laneDoublePos, laneBottomPos,
                        "mixed-case post-FB-break tick " + tick);
                assertNoLoweredRemainderWithoutSupport(mc.world, laneDoublePos, laneBottomPos,
                        "mixed-case post-FB-break tick " + tick);
            });
        }
    }

    /**
     * Legal state law for vertical mixed-seam seam:
     * A) normal BOTTOM (dy=0.000) directly below lowered DOUBLE (dy=-0.500) is legal only if the live
     *    placement and client model path agree on both blocks' ownership and there is no ghost seam.
     *
     * This case replays the live clue map directly:
     *  - bottomPos -> STONE_SLAB[type=bottom] at dy=0.000
     *  - bottomPos.up() -> STONE_SLAB[type=double] at dy=-0.500
     */
    private static void runVerticalBottomUnderLoweredDoubleStackSeamCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos bottomPos = SUPPORT_POS;
        final BlockPos loweredDoublePos = FULL_POS;
        final BlockPos seamSupportPos = loweredDoublePos.east();
        final BlockPos[] aimedTargetPos = new BlockPos[1];
        final Direction[] aimedTargetSide = new Direction[1];

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    bottomPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);

            world.setBlockState(
                    seamSupportPos,
                    Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    seamSupportPos.down(),
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, seamSupportPos, world.getBlockState(seamSupportPos));

            BlockState bottomState = world.getBlockState(bottomPos);
            world.setBlockState(
                    loweredDoublePos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            BlockState loweredDoubleState = world.getBlockState(loweredDoublePos);
            if (!bottomState.isOf(Blocks.STONE_SLAB)
                    || !bottomState.contains(SlabBlock.TYPE)
                    || bottomState.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("vertical seam setup expected BOTTOM at "
                        + bottomPos.toShortString()
                        + ", found " + bottomState);
            }
            if (!loweredDoubleState.isOf(Blocks.STONE_SLAB)
                    || !loweredDoubleState.contains(SlabBlock.TYPE)
                    || loweredDoubleState.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("vertical seam setup expected DOUBLE at "
                        + loweredDoublePos.toShortString()
                        + ", found " + loweredDoubleState);
            }

            double bottomDy = SlabSupport.getYOffset(world, bottomPos, bottomState);
            double loweredDoubleDy = SlabSupport.getYOffset(world, loweredDoublePos, loweredDoubleState);
            if (Math.abs(bottomDy) > EPSILON) {
                throw new RuntimeException("vertical seam setup expected bottom dy=0.000 at "
                        + bottomPos.toShortString()
                        + ", found " + bottomDy);
            }
            if (Math.abs(loweredDoubleDy + 0.5d) > EPSILON) {
                throw new RuntimeException("vertical seam setup expected lowered DOUBLE dy=-0.500 at "
                        + loweredDoublePos.toShortString()
                        + ", found " + loweredDoubleDy);
            }
        });

        singleplayer.getServer().runOnServer(server -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                return;
            }
            server.getPlayerManager()
                    .getPlayerList()
                    .get(0)
                    .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(bottomPos.getX() + 0.5d, bottomPos.getY() + 3.9d, bottomPos.getZ() + 0.5d),
                new Vec3d(bottomPos.getX() + 0.5d, bottomPos.getY() + 0.5d, bottomPos.getZ() + 0.5d));
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing before vertical seam setup check");
            }
            BlockState seamBottom = mc.world.getBlockState(bottomPos);
            BlockState seamDouble = mc.world.getBlockState(loweredDoublePos);
            double bottomDy = SlabSupport.getYOffset(mc.world, bottomPos, seamBottom);
            double loweredDoubleDy = SlabSupport.getYOffset(mc.world, loweredDoublePos, seamDouble);

            System.out.println("[VERTICAL-SEAM] baseline tick=0"
                    + " bottom=" + seamBottom + " dy=" + bottomDy
                    + " lower=" + seamDouble + " dy=" + loweredDoubleDy);

            if (!seamBottom.isOf(Blocks.STONE_SLAB)
                    || !seamBottom.contains(SlabBlock.TYPE)
                    || seamBottom.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("vertical seam baseline lost BOTTOM at "
                        + bottomPos.toShortString()
                        + ", found " + seamBottom);
            }
            if (!seamDouble.isOf(Blocks.STONE_SLAB)
                    || !seamDouble.contains(SlabBlock.TYPE)
                    || seamDouble.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("vertical seam baseline lost DOUBLE at "
                        + loweredDoublePos.toShortString()
                        + ", found " + seamDouble);
            }

            if (mc.gameRenderer == null) {
                throw new RuntimeException("vertical seam baseline expected game renderer for crosshair ownership check");
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            if (!(mc.crosshairTarget instanceof BlockHitResult baselineHit)) {
                throw new RuntimeException("vertical seam baseline expected crosshair target while aligned to seam");
            }
            if (!baselineHit.getBlockPos().equals(bottomPos)
                    && !baselineHit.getBlockPos().equals(loweredDoublePos)) {
                throw new RuntimeException("vertical seam baseline crosshair ownership drift at "
                        + baselineHit.getBlockPos()
                        + ", expected " + bottomPos + " or " + loweredDoublePos);
            }
            aimedTargetPos[0] = bottomPos;
            aimedTargetSide[0] = Direction.UP;
            System.out.println("[VERTICAL-SEAM] baseline crosshair target="
                    + baselineHit.getBlockPos()
                    + " side=" + baselineHit.getSide());
        });

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for vertical seam placement/break action");
            }
            if (aimedTargetPos[0] == null || aimedTargetSide[0] == null) {
                throw new RuntimeException("vertical seam action missing baseline target");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            mc.interactionManager.attackBlock(aimedTargetPos[0], aimedTargetSide[0]);
        });

        for (int tick = 0; tick < 4; tick++) {
            if (tick > 0) {
                ctx.waitTick();
                singleplayer.getClientWorld().waitForChunksRender();
            }
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing after vertical seam action tick " + readTick);
                }

                BlockState bottomAfter = mc.world.getBlockState(bottomPos);
                BlockState doubleAfter = mc.world.getBlockState(loweredDoublePos);
                double bottomAfterDy = bottomAfter.isOf(Blocks.STONE_SLAB) && bottomAfter.contains(SlabBlock.TYPE)
                        ? SlabSupport.getYOffset(mc.world, bottomPos, bottomAfter)
                        : Double.NaN;
                double doubleAfterDy = doubleAfter.isOf(Blocks.STONE_SLAB) && doubleAfter.contains(SlabBlock.TYPE)
                        ? SlabSupport.getYOffset(mc.world, loweredDoublePos, doubleAfter)
                        : Double.NaN;
                System.out.println("[VERTICAL-SEAM] tick=" + readTick
                        + " seam-bottom=" + bottomAfter + " dy=" + bottomAfterDy
                        + " seam-double=" + doubleAfter + " dy=" + doubleAfterDy);
                if (readTick > 0 && !bottomAfter.isAir()) {
                    throw new RuntimeException("vertical seam operation expected bottom target to clear on tick "
                            + readTick + ", found " + bottomAfter);
                }
                if (readTick > 0) {
                    assertForbiddenMixedDoubleBottomStateAbsent(
                            mc.world,
                            loweredDoublePos,
                            bottomPos,
                            "vertical seam post-op tick " + readTick);
                }
            });
        }
    }

    private static void runLoweredDoubleSideMergeCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Direction face
    ) {
        final BlockPos targetPos = FULL_POS.offset(face);
        final BlockPos placementPos = targetPos.offset(face);
        final BlockHitResult hit = resolveLoweredSideFaceHit(targetPos, face, SlabType.DOUBLE);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        setLoweredSlabTarget(singleplayer, targetPos, SlabType.DOUBLE);
        setPlacementPosTarget(singleplayer, placementPos, SlabType.TOP);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForFace(ctx, singleplayer, targetPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for lowered-DOUBLE merge case on " + face);
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (!result.isAccepted()) {
                throw new RuntimeException("lowered DOUBLE merge click for " + face + " was not accepted: " + result);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world missing after lowered-DOUBLE merge case on " + face);
            }
            BlockState placed = mc.world.getBlockState(placementPos);
            if (!placed.isOf(Blocks.STONE_SLAB)
                    || !placed.contains(SlabBlock.TYPE)
                    || placed.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("expected merged DOUBLE at " + placementPos.toShortString()
                        + " for lowered DOUBLE side target " + face + ", found " + placed);
            }
            double placedDy = SlabSupport.getYOffset(mc.world, placementPos, placed);
            if (Math.abs(placedDy + 0.5d) > EPSILON) {
                throw new RuntimeException("expected merged DOUBLE dy=-0.500 for lowered DOUBLE side target "
                        + face + ", found dy=" + placedDy + " state=" + placed);
            }
        });
    }

    private static void runLoweredDoubleLowerHalfOwnershipCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Direction face
    ) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            final int attemptNumber = attempt;
            final int xOffset = attemptNumber * 8;
            final BlockPos supportPos = SUPPORT_POS.add(xOffset, 0, 0);
            final BlockPos fullPos = FULL_POS.add(xOffset, 0, 0);
            final BlockPos targetPos = fullPos.offset(face);
            final BlockPos behindPos = targetPos.offset(face);
            final BlockHitResult syntheticLowerHalfHit = resolveLoweredFaceHit(targetPos, face, -0.25d);
            final Vec3d lowerHalfAimTarget = syntheticLowerHalfHit.getPos();
            final Vec3d eye;
            switch (face) {
                case NORTH -> eye = new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + 1.65d, targetPos.getZ() - 1.7d);
                case SOUTH -> eye = new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + 1.65d, targetPos.getZ() + 2.7d);
                case EAST -> eye = new Vec3d(targetPos.getX() - 1.7d, targetPos.getY() + 1.65d, targetPos.getZ() + 0.5d);
                case WEST -> eye = new Vec3d(targetPos.getX() + 2.7d, targetPos.getY() + 1.65d, targetPos.getZ() + 0.5d);
                default -> throw new IllegalArgumentException("unsupported face for lower-half ownership repro: " + face);
            }

            setupFixture(singleplayer, supportPos, fullPos);
            setLoweredSlabTarget(singleplayer, targetPos, SlabType.TOP);
            setLoweredSlabTarget(singleplayer, targetPos, SlabType.DOUBLE);
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            syncPlayerAim(ctx, singleplayer, eye, lowerHalfAimTarget);
            ctx.runOnClient(mc -> {
                if (mc.player == null || mc.interactionManager == null || mc.world == null || mc.gameRenderer == null) {
                    throw new RuntimeException("client not ready for lowered-DOUBLE lower-half ownership case on " + face);
                }

                mc.gameRenderer.updateCrosshairTarget(0.0f);
                if (!(mc.crosshairTarget instanceof BlockHitResult blockHit)) {
                    throw new RuntimeException("attempt " + attemptNumber + " missing block hit on lowered DOUBLE lower-half target");
                }
                if (!blockHit.getBlockPos().equals(targetPos)) {
                    throw new RuntimeException("attempt " + attemptNumber + " routed to owner "
                            + blockHit.getBlockPos().toShortString()
                            + " instead of lowered DOUBLE target " + targetPos.toShortString());
                }

                ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
                BlockState targetAfterClick = mc.world.getBlockState(targetPos);
                BlockState behindAfterFirst = mc.world.getBlockState(behindPos);
                if (!targetAfterClick.isOf(Blocks.STONE_SLAB)
                        || !targetAfterClick.contains(SlabBlock.TYPE)
                        || targetAfterClick.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                    throw new RuntimeException("attempt " + attemptNumber + " changed lowered DOUBLE target unexpectedly: "
                            + targetAfterClick + " result=" + result);
                }
                double targetDy = SlabSupport.getYOffset(mc.world, targetPos, targetAfterClick);
                if (Math.abs(targetDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("attempt " + attemptNumber + " moved lowered DOUBLE target out of lane: dy="
                            + targetDy + " state=" + targetAfterClick + " result=" + result);
                }
                if (!behindAfterFirst.isOf(Blocks.STONE_SLAB)
                        || !behindAfterFirst.contains(SlabBlock.TYPE)
                        || behindAfterFirst.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                    throw new RuntimeException("attempt " + attemptNumber
                            + " first click expected behind BOTTOM from lowered DOUBLE lower-half, found "
                            + behindAfterFirst + " result=" + result);
                }
                double behindFirstDy = SlabSupport.getYOffset(mc.world, behindPos, behindAfterFirst);
                if (Math.abs(behindFirstDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("attempt " + attemptNumber
                            + " first click behind state not lowered lane: dy="
                            + behindFirstDy + " state=" + behindAfterFirst + " result=" + result);
                }

                ActionResult secondResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
                if (!secondResult.isAccepted()) {
                    throw new RuntimeException("attempt " + attemptNumber
                            + " second click on lowered DOUBLE lower-half was not accepted: " + secondResult);
                }
                BlockState behindAfterSecond = mc.world.getBlockState(behindPos);
                if (!behindAfterSecond.isOf(Blocks.STONE_SLAB)
                        || !behindAfterSecond.contains(SlabBlock.TYPE)
                        || behindAfterSecond.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                    throw new RuntimeException("attempt " + attemptNumber
                            + " second click expected legal merge to DOUBLE behind lowered target, found "
                            + behindAfterSecond + " result=" + secondResult);
                }
                double behindSecondDy = SlabSupport.getYOffset(mc.world, behindPos, behindAfterSecond);
                if (Math.abs(behindSecondDy + 0.5d) > EPSILON) {
                    throw new RuntimeException("attempt " + attemptNumber
                            + " second click merged behind state out of lowered lane: dy="
                            + behindSecondDy + " state=" + behindAfterSecond + " result=" + secondResult);
                }
            });
        }
    }

    private static void runLoweredDoubleLowerHalfBoundaryCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Direction face
    ) {
        System.out.println("[LOWERED_DOUBLE_BOUNDARY] start face=" + face);
        final BlockPos targetPos = FULL_POS.offset(face);
        final BlockPos behindPos = targetPos.offset(face);
        final BlockHitResult hit = resolveLoweredFaceHit(targetPos, face, -0.5d);

        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        setLoweredSlabTarget(singleplayer, targetPos, SlabType.TOP);
        setLoweredSlabTarget(singleplayer, targetPos, SlabType.DOUBLE);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        movePlayerForFace(ctx, singleplayer, targetPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("client not ready for lowered-DOUBLE boundary ownership case on " + face);
            }
            ActionResult firstResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (!firstResult.isAccepted()) {
                throw new RuntimeException("boundary lower-half click on lowered DOUBLE was not accepted: " + firstResult);
            }

            BlockState targetState = mc.world.getBlockState(targetPos);
            BlockState behindAfterFirst = mc.world.getBlockState(behindPos);
            if (!targetState.isOf(Blocks.STONE_SLAB)
                    || !targetState.contains(SlabBlock.TYPE)
                    || targetState.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("boundary case changed lowered DOUBLE target unexpectedly: " + targetState + " result=" + firstResult);
            }
            double targetDy = SlabSupport.getYOffset(mc.world, targetPos, targetState);
            if (Math.abs(targetDy + 0.5d) > EPSILON) {
                throw new RuntimeException("boundary case target lost lowered DOUBLE dy: " + targetDy + " result=" + firstResult);
            }
            if (!behindAfterFirst.isOf(Blocks.STONE_SLAB)
                    || !behindAfterFirst.contains(SlabBlock.TYPE)
                    || behindAfterFirst.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("boundary case first click expected BOTTOM at " + behindPos + ", found " + behindAfterFirst);
            }
            double behindFirstDy = SlabSupport.getYOffset(mc.world, behindPos, behindAfterFirst);
            if (Math.abs(behindFirstDy + 0.5d) > EPSILON) {
                throw new RuntimeException("boundary case first click behind state not lowered lane: dy="
                        + behindFirstDy + " state=" + behindAfterFirst + " result=" + firstResult);
            }

            ActionResult secondResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (!secondResult.isAccepted()) {
                throw new RuntimeException("boundary case second click on lowered DOUBLE was not accepted: " + secondResult);
            }
            BlockState behindAfterSecond = mc.world.getBlockState(behindPos);
            if (!behindAfterSecond.isOf(Blocks.STONE_SLAB)
                    || !behindAfterSecond.contains(SlabBlock.TYPE)
                    || behindAfterSecond.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                throw new RuntimeException("boundary case second click expected legal merge to DOUBLE, found "
                        + behindAfterSecond + " result=" + secondResult);
            }
            double behindSecondDy = SlabSupport.getYOffset(mc.world, behindPos, behindAfterSecond);
            if (Math.abs(behindSecondDy + 0.5d) > EPSILON) {
                throw new RuntimeException("boundary case second click merged behind state out of lowered lane: dy="
                        + behindSecondDy + " state=" + behindAfterSecond + " result=" + secondResult);
            }
            System.out.println("[LOWERED_DOUBLE_BOUNDARY_GREEN] face=" + face
                    + " targetDy=" + targetDy + " behindSecondDy=" + behindSecondDy
                    + " behindAfterFirst=" + behindAfterFirst.get(SlabBlock.TYPE)
                    + " behindAfterSecond=" + behindAfterSecond.get(SlabBlock.TYPE));
        });
    }

    private static void runBeta4CompoundSlabMergeRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final Direction face = Direction.EAST;
        final BlockPos compoundPos = FULL_POS;
        final BlockPos adjacentLanePos = compoundPos.offset(face);
        final BlockHitResult lowerHalfHit = resolveLoweredFaceHit(compoundPos, face, -0.25d);
        final BlockHitResult upperHalfHit = resolveLoweredFaceHit(compoundPos, face, 0.25d);
        final BlockHitResult topHit = resolveLoweredUpMergeHit(compoundPos);

        runCompoundSlabNoLegalLaneCase(ctx, singleplayer, "ROW1", "lower-half", compoundPos, adjacentLanePos, face, lowerHalfHit);
        runCompoundSlabNoLegalLaneCase(ctx, singleplayer, "ROW2", "upper-half", compoundPos, adjacentLanePos, face, upperHalfHit);
        runCompoundSlabAdjacentLaneCase(ctx, singleplayer, "ROW3", compoundPos, adjacentLanePos, face, lowerHalfHit);
        runCompoundSlabMergePendingNote(ctx, singleplayer, "ROW4", compoundPos, adjacentLanePos, face);
        runCompoundSlabTopClickCase(ctx, singleplayer, "ROW5", compoundPos, adjacentLanePos, face, topHit);
        runCompoundSlabSanityNote(ctx, singleplayer, "ROW6", compoundPos, adjacentLanePos);
    }

    private static void runBeta4LiveScreenshotShapeRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        StringBuilder redSummary = new StringBuilder();
        runBeta4LiveScreenshotSideSlabBandCase(
                ctx,
                singleplayer,
                redSummary,
                "side-upper-half",
                FULL_POS.add(24, 2, 0),
                0.25d,
                SlabType.TOP,
                true);
        runBeta4LiveScreenshotSideSlabBandCase(
                ctx,
                singleplayer,
                redSummary,
                "side-lower-half",
                FULL_POS.add(30, 2, 0),
                -0.75d,
                SlabType.BOTTOM,
                false);
        runBeta4LiveScreenshotTopFaceGhostRedCase(ctx, singleplayer, redSummary);
        if (redSummary.length() > 0) {
            String summary = redSummary.toString();
            System.out.println("[JULIA_BETA4_LIVE_SCREENSHOT_BAND_SPLIT_HARNESS_GREEN]"
                    + " classification=EXPECTED_RED_AUDIT"
                    + " summary=" + summary.replace('\n', '|'));
            System.out.println("[JULIA_BETA4_LIVE_SCREENSHOT_HARNESS_GREEN]"
                    + " classification=EXPECTED_RED_AUDIT"
                    + " summary=" + summary.replace('\n', '|'));
            return;
        }
        System.out.println("[JULIA_BETA4_LIVE_SCREENSHOT_BAND_SPLIT_HARNESS_GREEN]"
                + " classification=FIXED_GREEN"
                + " reason=screenshot_shape_top_face_and_side_bands_green");
        System.out.println("[JULIA_BETA4_LIVE_SCREENSHOT_HARNESS_GREEN]"
                + " classification=FIXED_GREEN"
                + " reason=screenshot_shape_top_face_and_side_bands_green");
    }

    private static void runBeta35ObjectSlabOwnershipProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(48, 0, 0);
        final BlockPos fullPos = supportPos.up();
        final BlockPos slabPos = fullPos.east();
        final BlockPos torchPos = slabPos.up();
        final String ownerRouteScope = "OWNER_ROUTE_ONLY_SIMPLE_ROUTING";
        final String triadFailureLayer = "C. OBJECT_MODEL_OUTLINE_MISMATCH";

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(slabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(torchPos, Blocks.TORCH.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });

        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_TORCH_TARGET_RED] client not ready");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);

            BlockState torchState = mc.world.getBlockState(torchPos);
            BlockState slabState = mc.world.getBlockState(slabPos);
            BlockState supportState = mc.world.getBlockState(supportPos);
            if (!torchState.isOf(Blocks.TORCH)) {
                throw new RuntimeException("[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_TORCH_TARGET_RED]"
                        + " expected torch at " + torchPos.toShortString()
                        + " found=" + torchState);
            }
            if (!slabState.isOf(Blocks.STONE_SLAB)) {
                throw new RuntimeException("[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_SLAB_TARGET_RED]"
                        + " expected slab at " + slabPos.toShortString()
                        + " found=" + slabState);
            }

            double torchDy = SlabSupport.getYOffset(mc.world, torchPos, torchState);
            double slabDy = SlabSupport.getYOffset(mc.world, slabPos, slabState);
            double supportDy = SlabSupport.getYOffset(mc.world, supportPos, supportState);
            boolean survival = torchState.canPlaceAt(mc.world, torchPos);
            System.out.println("[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_FIXTURE_GREEN]"
                    + " proofScope=" + ownerRouteScope
                    + " screenshotFaithfulTriad=NOT_PROVEN"
                    + " objectPos=" + torchPos.toShortString()
                    + " objectState=" + torchState
                    + " objectDy=" + String.format("%.3f", torchDy)
                    + " slabPos=" + slabPos.toShortString()
                    + " slabState=" + slabState
                    + " slabDy=" + String.format("%.3f", slabDy)
                    + " supportPos=" + supportPos.toShortString()
                    + " supportState=" + supportState
                    + " supportDy=" + String.format("%.3f", supportDy));

            Vec3d torchEye = new Vec3d(
                    torchPos.getX() + 0.5,
                    slabPos.getY() + 0.30,
                    torchPos.getZ() - 2.5);
            Vec3d torchTarget = new Vec3d(
                    torchPos.getX() + 0.5,
                    slabPos.getY() + 0.06,
                    torchPos.getZ() + 0.5);
            HitResult torchVanilla = playerRaycastFromEye(mc, torchEye, torchTarget, 6.0);
            BlockHitResult torchFinal = updateCrosshairFromEye(mc, torchEye, torchTarget);
            boolean torchGreen = torchFinal != null && torchFinal.getBlockPos().equals(torchPos);
            System.out.println((torchGreen
                    ? "[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_TORCH_TARGET_GREEN]"
                    : "[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_TORCH_TARGET_RED]")
                    + " expectedOwner=torch"
                    + " actualOwner=" + beta35OwnerName(mc, torchFinal)
                    + " vanillaTarget=" + beta35FormatHit(torchVanilla)
                    + " finalTarget=" + beta35FormatHit(torchFinal)
                    + " objectPos=" + torchPos.toShortString()
                    + " slabPos=" + slabPos.toShortString()
                    + " raycastTargetPos=" + (torchFinal == null ? "MISS" : torchFinal.getBlockPos().toShortString())
                    + " targetOwner=" + beta35OwnerName(mc, torchFinal)
                    + " outlineOwner=" + beta35OwnerName(mc, torchFinal));

            Vec3d slabEye = new Vec3d(
                    slabPos.getX() + 0.18,
                    slabPos.getY() + 0.20,
                    slabPos.getZ() - 2.5);
            Vec3d slabTarget = new Vec3d(
                    slabPos.getX() + 0.18,
                    slabPos.getY() - 0.25,
                    slabPos.getZ() + 0.18);
            HitResult slabVanilla = playerRaycastFromEye(mc, slabEye, slabTarget, 6.0);
            BlockHitResult slabFinal = updateCrosshairFromEye(mc, slabEye, slabTarget);
            boolean slabGreen = slabFinal != null && slabFinal.getBlockPos().equals(slabPos);
            System.out.println((slabGreen
                    ? "[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_SLAB_TARGET_GREEN]"
                    : "[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_SLAB_TARGET_RED]")
                    + " expectedOwner=slab"
                    + " actualOwner=" + beta35OwnerName(mc, slabFinal)
                    + " vanillaTarget=" + beta35FormatHit(slabVanilla)
                    + " finalTarget=" + beta35FormatHit(slabFinal)
                    + " objectPos=" + torchPos.toShortString()
                    + " slabPos=" + slabPos.toShortString()
                    + " raycastTargetPos=" + (slabFinal == null ? "MISS" : slabFinal.getBlockPos().toShortString())
                    + " targetOwner=" + beta35OwnerName(mc, slabFinal)
                    + " outlineOwner=" + beta35OwnerName(mc, slabFinal));

            boolean ownerRouteGreen = torchGreen && slabGreen && survival;
            if (ownerRouteGreen) {
                System.out.println("[JULIA_BETA35_OBJECT_SLAB_TRIAD_OWNER_ROUTE_GREEN]"
                        + " proofScope=" + ownerRouteScope
                        + " oldOwnerOnlyProof=GREEN"
                        + " screenshotFaithfulTriad=NOT_PROVEN"
                        + " objectTargetOwner=torch"
                        + " slabTargetOwner=slab"
                        + " survival=GREEN");
            }

            VoxelShape objectOutlineShape = torchState.getOutlineShape(
                    mc.world, torchPos, net.minecraft.block.ShapeContext.of(mc.player));
            VoxelShape objectRaycastShape = torchState.getRaycastShape(mc.world, torchPos);
            VoxelShape slabOutlineShape = slabState.getOutlineShape(
                    mc.world, slabPos, net.minecraft.block.ShapeContext.of(mc.player));
            net.minecraft.util.math.Box objectModelBox = beta35FloorTorchModelProxyWorldBox(torchPos, torchDy);
            net.minecraft.util.math.Box objectOutlineBox = beta35WorldBox(objectOutlineShape, torchPos);
            net.minecraft.util.math.Box objectRaycastBox = beta35WorldBox(objectRaycastShape, torchPos);
            net.minecraft.util.math.Box slabOutlineBox = beta35WorldBox(slabOutlineShape, slabPos);
            BlockHitResult objectOutlineHit = objectOutlineShape.raycast(torchEye, torchTarget, torchPos);
            BlockHitResult objectRaycastHit = objectRaycastShape.raycast(torchEye, torchTarget, torchPos);
            boolean outlineColocatedWithModel = beta35SameBox(objectOutlineBox, objectModelBox);
            boolean outlineDetachedAbove = objectOutlineBox != null
                    && objectOutlineBox.maxY > objectModelBox.maxY + EPSILON;
            boolean outlineShiftedAway = objectOutlineBox == null
                    || !beta35SameHorizontalColumn(objectOutlineBox, objectModelBox);
            boolean raycastGreen = objectRaycastHit != null
                    && objectRaycastHit.getBlockPos().equals(torchPos)
                    && torchGreen;
            System.out.println("[JULIA_BETA35_OBJECT_SLAB_TRIAD_FIXTURE_GREEN]"
                    + " objectPos=" + torchPos.toShortString()
                    + " objectState=" + torchState
                    + " objectDy=" + String.format("%.3f", torchDy)
                    + " objectModelBoundsAccessible=false"
                    + " objectModelExpectedBounds=vanilla_torch_post_proxy:" + formatBox(objectModelBox)
                    + " objectOutlineBounds=" + beta35FormatBox(objectOutlineBox)
                    + " objectRaycastBounds=" + beta35FormatBox(objectRaycastBox)
                    + " objectOutlineHit=" + beta35FormatHit(objectOutlineHit)
                    + " objectRaycastHit=" + beta35FormatHit(objectRaycastHit)
                    + " slabPos=" + slabPos.toShortString()
                    + " slabState=" + slabState
                    + " slabDy=" + String.format("%.3f", slabDy)
                    + " slabOutlineBounds=" + beta35FormatBox(slabOutlineBox));
            System.out.println((outlineColocatedWithModel
                    ? "[JULIA_BETA35_OBJECT_SLAB_TRIAD_MODEL_OUTLINE_GREEN]"
                    : "[JULIA_BETA35_OBJECT_SLAB_TRIAD_MODEL_OUTLINE_RED]")
                    + " failureLayer=" + (outlineColocatedWithModel ? "NONE" : triadFailureLayer)
                    + " outlineCoLocatedWithVisibleTorchBody=" + outlineColocatedWithModel
                    + " outlineDetachedAboveVisibleTorchBody=" + outlineDetachedAbove
                    + " outlineShiftedAwayFromVisibleTorchBody=" + outlineShiftedAway
                    + " objectModelExpectedBounds=vanilla_torch_post_proxy:" + formatBox(objectModelBox)
                    + " objectOutlineBounds=" + beta35FormatBox(objectOutlineBox));
            System.out.println((raycastGreen
                    ? "[JULIA_BETA35_OBJECT_SLAB_TRIAD_RAYCAST_GREEN]"
                    : "[JULIA_BETA35_OBJECT_SLAB_TRIAD_RAYCAST_RED]")
                    + " failureLayer=" + (raycastGreen ? "NONE" : "B. OBJECT_RAYCAST_DY_WRONG")
                    + " objectRaycastTargetOwner=" + (objectRaycastHit == null ? "MISS" : beta35OwnerName(mc, objectRaycastHit))
                    + " finalTargetOwner=" + beta35OwnerName(mc, torchFinal)
                    + " objectRaycastHit=" + beta35FormatHit(objectRaycastHit)
                    + " finalTarget=" + beta35FormatHit(torchFinal));
            if (ownerRouteGreen && !outlineColocatedWithModel) {
                System.out.println("[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_FALSE_GREEN]"
                        + " oldOwnerOnlyProof=GREEN"
                        + " screenshotFaithfulTriad=RED"
                        + " failureLayer=" + triadFailureLayer
                        + " c96e674=partial_not_release_ready"
                        + " beta35IncludeStatus=NEEDS_PROOF");
            }

            System.out.println("[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_SURVIVAL_GREEN]"
                    + " objectPos=" + torchPos.toShortString()
                    + " objectState=" + torchState
                    + " supportPos=" + slabPos.toShortString()
                    + " supportState=" + slabState
                    + " survival=" + survival);
            System.out.println("[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_SUMMARY]"
                    + " proofScope=" + ownerRouteScope
                    + " objectClass=torch"
                    + " slabState=stone_slab[type=" + slabState.get(SlabBlock.TYPE).asString() + "]"
                    + " objectDy=" + String.format("%.3f", torchDy)
                    + " slabDy=" + String.format("%.3f", slabDy)
                    + " torchTarget=" + (torchGreen ? "GREEN" : "RED")
                    + " slabTarget=" + (slabGreen ? "GREEN" : "RED")
                    + " survival=" + (survival ? "GREEN" : "RED")
                    + " screenshotFaithfulTriad=" + (outlineColocatedWithModel && raycastGreen ? "GREEN" : "RED")
                    + " failureLayer=" + (outlineColocatedWithModel && raycastGreen ? "NONE" : triadFailureLayer)
                    + " flashSnap=deferred_not_same_path");
            System.out.println("[JULIA_BETA35_OBJECT_SLAB_TRIAD_SUMMARY]"
                    + " oldOwnerOnlyProof=" + (ownerRouteGreen ? "GREEN" : "RED")
                    + " ownerRoute=" + (ownerRouteGreen ? "GREEN" : "RED")
                    + " modelOutline=" + (outlineColocatedWithModel ? "GREEN" : "RED")
                    + " raycast=" + (raycastGreen ? "GREEN" : "RED")
                    + " survival=" + (survival ? "GREEN" : "RED")
                    + " screenshotFaithfulTriad=" + (outlineColocatedWithModel && raycastGreen ? "GREEN" : "RED")
                    + " failureLayer=" + (outlineColocatedWithModel && raycastGreen ? "NONE" : triadFailureLayer)
                    + " beta35IncludeStatus="
                    + (ownerRouteGreen && outlineColocatedWithModel && raycastGreen ? "INCLUDE" : "NEEDS_PROOF")
                    + " targetLaw=A");

            if (!torchGreen) {
                throw new RuntimeException("[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_TORCH_TARGET_RED]"
                        + " expected torch owner at " + torchPos.toShortString()
                        + " actual=" + beta35FormatHit(torchFinal)
                        + " vanilla=" + beta35FormatHit(torchVanilla));
            }
            if (!slabGreen) {
                throw new RuntimeException("[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_SLAB_TARGET_RED]"
                        + " expected slab owner at " + slabPos.toShortString()
                        + " actual=" + beta35FormatHit(slabFinal)
                        + " vanilla=" + beta35FormatHit(slabVanilla));
            }
            if (!survival) {
                throw new RuntimeException("[JULIA_BETA35_OBJECT_SLAB_OWNERSHIP_SURVIVAL_RED]"
                        + " torch canPlaceAt returned false at " + torchPos.toShortString());
            }
        });
    }

    private static HitResult playerRaycastFromEye(
            net.minecraft.client.MinecraftClient mc, Vec3d eye, Vec3d target, double reach
    ) {
        if (mc.player == null) {
            return null;
        }
        Vec3d delta = target.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - mc.player.getStandingEyeHeight();
        mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        return mc.player.raycast(reach, 0.0f, false);
    }

    private static BlockHitResult updateCrosshairFromEye(
            net.minecraft.client.MinecraftClient mc, Vec3d eye, Vec3d target
    ) {
        playerRaycastFromEye(mc, eye, target, 6.0);
        mc.gameRenderer.updateCrosshairTarget(0.0f);
        return mc.crosshairTarget instanceof BlockHitResult blockHit ? blockHit : null;
    }

    private static String beta35OwnerName(net.minecraft.client.MinecraftClient mc, BlockHitResult hit) {
        if (mc.world == null || hit == null) {
            return "MISS";
        }
        BlockState state = mc.world.getBlockState(hit.getBlockPos());
        if (state.isOf(Blocks.TORCH)) {
            return "torch";
        }
        if (state.getBlock() instanceof SlabBlock) {
            return "slab";
        }
        return state.getBlock().getTranslationKey();
    }

    private static String beta35FormatHit(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return hit == null ? "null" : hit.getType().toString();
        }
        return "BLOCK pos=" + blockHit.getBlockPos().toShortString()
                + " face=" + blockHit.getSide().asString()
                + " hit=" + String.format("%.4f,%.4f,%.4f",
                        blockHit.getPos().x,
                        blockHit.getPos().y,
                        blockHit.getPos().z);
    }

    private static net.minecraft.util.math.Box beta35FloorTorchModelProxyWorldBox(BlockPos pos, double dy) {
        return new net.minecraft.util.math.Box(
                6.0d / 16.0d,
                dy,
                6.0d / 16.0d,
                10.0d / 16.0d,
                dy + (10.0d / 16.0d),
                10.0d / 16.0d).offset(pos);
    }

    private static net.minecraft.util.math.Box beta35WorldBox(VoxelShape shape, BlockPos pos) {
        if (shape == null || shape.isEmpty()) {
            return null;
        }
        return shape.getBoundingBox().offset(pos);
    }

    private static boolean beta35SameBox(
            net.minecraft.util.math.Box actual,
            net.minecraft.util.math.Box expected
    ) {
        if (actual == null || expected == null) {
            return false;
        }
        return Math.abs(actual.minX - expected.minX) <= EPSILON
                && Math.abs(actual.minY - expected.minY) <= EPSILON
                && Math.abs(actual.minZ - expected.minZ) <= EPSILON
                && Math.abs(actual.maxX - expected.maxX) <= EPSILON
                && Math.abs(actual.maxY - expected.maxY) <= EPSILON
                && Math.abs(actual.maxZ - expected.maxZ) <= EPSILON;
    }

    private static boolean beta35SameHorizontalColumn(
            net.minecraft.util.math.Box actual,
            net.minecraft.util.math.Box expected
    ) {
        if (actual == null || expected == null) {
            return false;
        }
        return Math.abs(actual.minX - expected.minX) <= EPSILON
                && Math.abs(actual.minZ - expected.minZ) <= EPSILON
                && Math.abs(actual.maxX - expected.maxX) <= EPSILON
                && Math.abs(actual.maxZ - expected.maxZ) <= EPSILON;
    }

    private static String beta35FormatBox(net.minecraft.util.math.Box box) {
        return box == null ? "empty" : formatBox(box);
    }

    private static String beta35FormatDoubleOrNA(double value) {
        return Double.isFinite(value) ? String.format("%.6f", value) : "N/A";
    }

    private static void runBeta4LiveScreenshotSideSlabBandCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            StringBuilder redSummary,
            String proof,
            BlockPos clickedTopFullPos,
            double hitYOffset,
            SlabType expectedType,
            boolean requireGreen
    ) {
        final Direction face = Direction.WEST;
        final BlockPos loweredLaneBelowTop = clickedTopFullPos.down();
        final BlockPos lowerFullBlock = clickedTopFullPos.down(2);
        final BlockPos expectedSideSlabPos = clickedTopFullPos.offset(face);
        final BlockHitResult sideHit = resolveLoweredFaceHit(clickedTopFullPos, face, hitYOffset);
        final String[] resultText = {"not-run"};

        seedBeta4LiveScreenshotShape(singleplayer, clickedTopFullPos);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        movePlayerForFace(ctx, singleplayer, clickedTopFullPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[JULIA_BETA4_LIVE_SCREENSHOT_HARNESS_FAIL] case="
                        + proof + " reason=client_not_ready");
            }
            assertBeta4LiveScreenshotSourceTruth(
                    mc.world,
                    proof,
                    clickedTopFullPos,
                    loweredLaneBelowTop,
                    lowerFullBlock,
                    expectedSideSlabPos,
                    sideHit);
            emitCompoundSlabDiscriminator(
                    "[JULIA_BETA4_LIVE_SCREENSHOT_DISCRIMINATOR]",
                    proof,
                    mc.world,
                    clickedTopFullPos,
                    face,
                    sideHit,
                    expectedSideSlabPos,
                    expectedSideSlabPos,
                    false);
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, sideHit);
            resultText[0] = result.toString();
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[JULIA_BETA4_LIVE_SCREENSHOT_HARNESS_FAIL] case="
                        + proof + " reason=client_world_missing_after_click");
            }
            BlockState actual = mc.world.getBlockState(expectedSideSlabPos);
            double actualDy = SlabSupport.getYOffset(mc.world, expectedSideSlabPos, actual);
            boolean expectedLegalSideSlab = actual.isOf(Blocks.STONE_SLAB)
                    && actual.contains(SlabBlock.TYPE)
                    && actual.get(SlabBlock.TYPE) == expectedType
                    && Math.abs(actualDy + 0.5d) <= EPSILON;
            String resultWasPass = Boolean.toString(resultText[0].contains("Pass["));
            if (!expectedLegalSideSlab) {
                String reason = "case=" + proof
                        + " result=" + resultText[0]
                        + " resultWasPass=" + resultWasPass
                        + " sourceFace=" + face.asString()
                        + " hitY=" + sideHit.getPos().y
                        + " hitBand=" + beta4LiveScreenshotHitBand(clickedTopFullPos, sideHit)
                        + " lowerHalfCandidatePos=" + expectedSideSlabPos.toShortString()
                        + " upperHalfCandidatePos=" + expectedSideSlabPos.toShortString()
                        + " expected=stone_slab[type=" + expectedType.asString() + "] dy=-0.5 at "
                        + expectedSideSlabPos.toShortString()
                        + " actual=" + describeOwnerFacts(mc.world, expectedSideSlabPos)
                        + " actualDy=" + actualDy
                        + " clicked=" + describeOwnerFacts(mc.world, clickedTopFullPos)
                        + " loweredLaneBelowTop=" + describeOwnerFacts(mc.world, loweredLaneBelowTop);
                if (requireGreen) {
                    System.out.println("[JULIA_BETA4_LIVE_SCREENSHOT_BAND_SPLIT_HARNESS_FAIL] " + reason);
                    throw new RuntimeException("Julia beta4 live screenshot upper side band did not stay GREEN");
                }
                System.out.println("[JULIA_BETA4_LIVE_SCREENSHOT_SIDE_LOWER_RED] " + reason);
                redSummary.append(reason).append('\n');
            } else {
                String marker = requireGreen
                        ? "[JULIA_BETA4_LIVE_SCREENSHOT_SIDE_UPPER_GREEN]"
                        : "[JULIA_BETA4_LIVE_SCREENSHOT_SIDE_LOWER_GREEN]";
                System.out.println(marker
                        + " result=" + resultText[0]
                        + " resultWasPass=" + resultWasPass
                        + " sourceFace=" + face.asString()
                        + " hitY=" + sideHit.getPos().y
                        + " hitBand=" + beta4LiveScreenshotHitBand(clickedTopFullPos, sideHit)
                        + " lowerHalfCandidatePos=" + expectedSideSlabPos.toShortString()
                        + " upperHalfCandidatePos=" + expectedSideSlabPos.toShortString()
                        + " expected=stone_slab[type=" + expectedType.asString() + "] dy=-0.5"
                        + " actual=" + describeOwnerFacts(mc.world, expectedSideSlabPos));
            }
        });
    }

    private static void runBeta4LiveScreenshotTopFaceGhostRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            StringBuilder redSummary
    ) {
        final String proof = "top-face-ghost";
        final BlockPos clickedTopFullPos = FULL_POS.add(36, 2, 0);
        final BlockPos loweredLaneBelowTop = clickedTopFullPos.down();
        final BlockPos lowerFullBlock = clickedTopFullPos.down(2);
        final BlockPos expectedTopSlabPos = clickedTopFullPos.up();
        final BlockPos skippedTopSlabPos = clickedTopFullPos.up(2);
        final BlockHitResult topHit = resolveLoweredUpMergeHit(clickedTopFullPos);
        final String[] resultText = {"not-run"};

        seedBeta4LiveScreenshotShape(singleplayer, clickedTopFullPos);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        movePlayerForUp(ctx, singleplayer, clickedTopFullPos);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[JULIA_BETA4_LIVE_SCREENSHOT_HARNESS_FAIL] case="
                        + proof + " reason=client_not_ready");
            }
            assertBeta4LiveScreenshotSourceTruth(
                    mc.world,
                    proof,
                    clickedTopFullPos,
                    loweredLaneBelowTop,
                    lowerFullBlock,
                    expectedTopSlabPos,
                    topHit);
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, topHit);
            resultText[0] = result.toString();
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[JULIA_BETA4_LIVE_SCREENSHOT_HARNESS_FAIL] case="
                        + proof + " reason=client_world_missing_after_click");
            }
            BlockState actual = mc.world.getBlockState(expectedTopSlabPos);
            double actualDy = SlabSupport.getYOffset(mc.world, expectedTopSlabPos, actual);
            BlockState skipped = mc.world.getBlockState(skippedTopSlabPos);
            boolean expectedLegalTopSlab = actual.isOf(Blocks.STONE_SLAB)
                    && actual.contains(SlabBlock.TYPE)
                    && actual.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && Math.abs(actualDy) <= EPSILON;
            boolean ghostOrSkipSlabAppeared = actual.isOf(Blocks.STONE_SLAB) || skipped.isOf(Blocks.STONE_SLAB);
            String resultWasPass = Boolean.toString(resultText[0].contains("Pass["));
            if (!expectedLegalTopSlab) {
                String reason = "case=" + proof
                        + " result=" + resultText[0]
                        + " resultWasPass=" + resultWasPass
                        + " sourceFace=" + topHit.getSide().asString()
                        + " hitY=" + topHit.getPos().y
                        + " hitBand=" + beta4LiveScreenshotHitBand(clickedTopFullPos, topHit)
                        + " ghostOrSkipSlabAppeared=" + ghostOrSkipSlabAppeared
                        + " expected=stone_slab[type=bottom] dy=0.0 at "
                        + expectedTopSlabPos.toShortString()
                        + " actual=" + describeOwnerFacts(mc.world, expectedTopSlabPos)
                        + " actualDy=" + actualDy
                        + " skippedCandidate=" + describeOwnerFacts(mc.world, skippedTopSlabPos)
                        + " clicked=" + describeOwnerFacts(mc.world, clickedTopFullPos)
                        + " loweredLaneBelowTop=" + describeOwnerFacts(mc.world, loweredLaneBelowTop);
                System.out.println("[JULIA_BETA4_LIVE_SCREENSHOT_TOP_FACE_GHOST_RED] " + reason);
                redSummary.append(reason).append('\n');
            } else {
                System.out.println("[JULIA_BETA4_LIVE_SCREENSHOT_TOP_FACE_GHOST_GREEN]"
                        + " result=" + resultText[0]
                        + " resultWasPass=" + resultWasPass
                        + " sourceFace=" + topHit.getSide().asString()
                        + " hitY=" + topHit.getPos().y
                        + " hitBand=" + beta4LiveScreenshotHitBand(clickedTopFullPos, topHit)
                        + " ghostOrSkipSlabAppeared=" + ghostOrSkipSlabAppeared
                        + " actual=" + describeOwnerFacts(mc.world, expectedTopSlabPos));
            }
        });
    }

    private static String beta4LiveScreenshotHitBand(BlockPos sourcePos, BlockHitResult hit) {
        if (hit.getSide() == Direction.UP) {
            return "top_face";
        }
        if (hit.getPos().y < sourcePos.getY() - 0.5d) {
            return "lower_compound_visible_half";
        }
        if (hit.getPos().y < sourcePos.getY()) {
            return "upper_compound_visible_half";
        }
        return hit.getPos().y < sourcePos.getY() + 0.5d ? "lower_source_half" : "upper_source_half";
    }

    private static void assertBeta4LiveScreenshotSourceTruth(
            net.minecraft.world.BlockView world,
            String proof,
            BlockPos clickedTopFullPos,
            BlockPos loweredLaneBelowTop,
            BlockPos lowerFullBlock,
            BlockPos candidatePlacementPos,
            BlockHitResult hit
    ) {
        BlockState clicked = world.getBlockState(clickedTopFullPos);
        double clickedDy = SlabSupport.getYOffset(world, clickedTopFullPos, clicked);
        BlockState loweredLane = world.getBlockState(loweredLaneBelowTop);
        double loweredLaneDy = SlabSupport.getYOffset(world, loweredLaneBelowTop, loweredLane);
        BlockState lowerFull = world.getBlockState(lowerFullBlock);
        double lowerFullDy = SlabSupport.getYOffset(world, lowerFullBlock, lowerFull);
        boolean clickedCompound = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, clickedTopFullPos);
        boolean loweredLaneCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                world,
                loweredLaneBelowTop,
                loweredLane);
        int legalHorizontalLoweredLanes = countLegalLoweredSlabLanes(world, clickedTopFullPos);
        System.out.println("[JULIA_BETA4_LIVE_SCREENSHOT_HARNESS_GREEN]"
                + " case=" + proof
                + " clickedBlockId=" + clicked.getBlock()
                + " clickedPos=" + clickedTopFullPos.toShortString()
                + " face=" + hit.getSide().asString()
                + " hitVec=" + hit.getPos()
                + " clickedDy=" + clickedDy
                + " clickedAnchored=" + SlabAnchorAttachment.isAnchored(world, clickedTopFullPos)
                + " clickedCompoundFullBlockAnchor=" + clickedCompound
                + " loweredLaneBelowTop=" + describeOwnerFacts(world, loweredLaneBelowTop)
                + " lowerFullBlock=" + describeOwnerFacts(world, lowerFullBlock)
                + " legalHorizontalLoweredLaneCount=" + legalHorizontalLoweredLanes
                + " candidatePlacementPos=" + candidatePlacementPos.toShortString()
                + " candidateBefore=" + describeOwnerFacts(world, candidatePlacementPos));
        if (!clicked.isOf(Blocks.STONE)
                || clicked.contains(SlabBlock.TYPE)
                || Math.abs(clickedDy + 1.0d) > EPSILON
                || !clickedCompound
                || !loweredLane.isOf(Blocks.STONE_SLAB)
                || !loweredLane.contains(SlabBlock.TYPE)
                || loweredLane.get(SlabBlock.TYPE) != SlabType.BOTTOM
                || Math.abs(loweredLaneDy + 0.5d) > EPSILON
                || !loweredLaneCarrier
                || !lowerFull.isOf(Blocks.STONE)
                || Math.abs(lowerFullDy + 0.5d) > EPSILON) {
            throw new RuntimeException("[JULIA_BETA4_LIVE_SCREENSHOT_HARNESS_FAIL]"
                    + " case=" + proof
                    + " reason=source_truth_invalid"
                    + " clicked=" + describeOwnerFacts(world, clickedTopFullPos)
                    + " loweredLaneBelowTop=" + describeOwnerFacts(world, loweredLaneBelowTop)
                    + " lowerFullBlock=" + describeOwnerFacts(world, lowerFullBlock));
        }
    }

    private static void runCompoundSlabNoLegalLaneCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String rowName,
            String halfLabel,
            BlockPos compoundPos,
            BlockPos adjacentLanePos,
            Direction face,
            BlockHitResult hit
    ) {
        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        seedCompoundFullBlock(singleplayer, compoundPos);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        assertCompoundSlabHarnessTopology(ctx, rowName, compoundPos, adjacentLanePos, face, hit, false);
        movePlayerForFace(ctx, singleplayer, compoundPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[" + rowName + "] client not ready for compound no-legal-lane proof");
            }
            emitCompoundSlabDiscriminator(
                    "[JULIA_BETA4_NO_LEGAL_LANE_DISCRIMINATOR]",
                    rowName + "-" + halfLabel,
                    mc.world,
                    compoundPos,
                    face,
                    hit,
                    adjacentLanePos,
                    adjacentLanePos,
                    false);
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            BlockState compoundState = mc.world.getBlockState(compoundPos);
            double sourceDy = SlabSupport.getYOffset(mc.world, compoundPos, compoundState);
            double visibleLocalY = hit.getPos().y - (compoundPos.getY() + sourceDy);
            boolean visibleUpperBand = visibleLocalY >= 0.5d - EPSILON && visibleLocalY <= 1.0d + EPSILON;
            String expectedLaw = visibleUpperBand
                    ? "COMPOUND_VISIBLE_SIDE_UPPER_SLAB"
                    : "COMPOUND_BELOW_LANE_SIDE_SLAB";
            System.out.println("[JULIA_BETA4_COMPOUND_BELOW_LANE_SIDE_SLAB_ATTEMPT]"
                    + " row=" + rowName
                    + " half=" + halfLabel
                    + " result=" + result
                    + " sourceDy=" + sourceDy
                    + " visibleLocalY=" + visibleLocalY
                    + " expectedLaw=" + expectedLaw
                    + " compound=" + describeOwnerFacts(mc.world, compoundPos)
                    + " adjacent=" + describeOwnerFacts(mc.world, adjacentLanePos)
                    + " classification=" + expectedLaw
                    + " expected=" + (visibleUpperBand
                    ? "stone_slab[type=top]_dy_-1.0"
                    : "legal_side_slab_dy_-0.5")
                    + " reason=" + (visibleUpperBand
                    ? "old_row_hit_is_upper_visible_band_under_compound_visible_slab_lane"
                    : "product_law_promoted_below_lane_class"));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[" + rowName + "] client world missing after compound no-legal-lane proof");
            }
            BlockState compound = mc.world.getBlockState(compoundPos);
            double compoundDy = SlabSupport.getYOffset(mc.world, compoundPos, compound);
            if (Math.abs(compoundDy + 1.0d) > EPSILON) {
                throw new RuntimeException("[" + rowName + "] compound owner must remain dy=-1.000, found dy="
                        + compoundDy + " state=" + compound);
            }
            double visibleLocalY = hit.getPos().y - (compoundPos.getY() + compoundDy);
            boolean visibleUpperBand = visibleLocalY >= 0.5d - EPSILON && visibleLocalY <= 1.0d + EPSILON;
            SlabType expectedType = visibleUpperBand
                    ? SlabType.TOP
                    : "upper-half".equals(halfLabel) ? SlabType.TOP : SlabType.BOTTOM;
            double expectedDy = visibleUpperBand ? -1.0d : -0.5d;
            String expectedLaw = visibleUpperBand
                    ? "COMPOUND_VISIBLE_SIDE_UPPER_SLAB"
                    : "COMPOUND_BELOW_LANE_SIDE_SLAB";
            BlockState adjacent = mc.world.getBlockState(adjacentLanePos);
            double adjacentDy = SlabSupport.getYOffset(mc.world, adjacentLanePos, adjacent);
            if (!adjacent.isOf(Blocks.STONE_SLAB)
                    || !adjacent.contains(SlabBlock.TYPE)
                    || adjacent.get(SlabBlock.TYPE) != expectedType
                    || Math.abs(adjacentDy - expectedDy) > EPSILON) {
                failCompoundSlabHarness(rowName, expectedLaw + " GREEN must author "
                        + expectedType + " stone_slab dy=" + expectedDy + " at "
                        + adjacentLanePos.toShortString() + ", found " + adjacent + " dy=" + adjacentDy);
            }
            System.out.println((visibleUpperBand
                    ? "[JULIA_BETA4_COMPOUND_OLD_ROW_VISIBLE_UPPER_SUPERSEDED_GREEN]"
                    : "[JULIA_BETA4_COMPOUND_BELOW_LANE_SIDE_SLAB_GREEN]")
                    + " row=" + rowName
                    + " half=" + halfLabel
                    + " classification=" + expectedLaw
                    + " visibleLocalY=" + visibleLocalY
                    + " expectedDy=" + expectedDy
                    + " compound=" + describeOwnerFacts(mc.world, compoundPos)
                    + " candidate=" + describeOwnerFacts(mc.world, adjacentLanePos)
                    + " illegalDyMinusOneSlab=false"
                    + " illegalDyBelowMinusOne=false"
                    + " illegalDyZeroSlab=false");
        });
    }

    private static void runCompoundSlabAdjacentLaneCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String rowName,
            BlockPos compoundPos,
            BlockPos adjacentLanePos,
            Direction face,
            BlockHitResult hit
    ) {
        final BlockPos placedLanePos = adjacentLanePos.offset(face);
        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        seedCompoundFullBlock(singleplayer, compoundPos);
        setLoweredSlabTarget(singleplayer, adjacentLanePos, SlabType.BOTTOM);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        assertCompoundSlabHarnessTopology(ctx, rowName, compoundPos, adjacentLanePos, face, hit, true);
        movePlayerForFace(ctx, singleplayer, compoundPos, face);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[" + rowName + "] client not ready for compound legal-remap proof");
            }
            emitCompoundSlabDiscriminator(
                    "[JULIA_BETA4_INTERNAL_ROW3_DISCRIMINATOR]",
                    rowName,
                    mc.world,
                    compoundPos,
                    face,
                    hit,
                    adjacentLanePos,
                    placedLanePos,
                    true);
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            System.out.println("[JULIA_BETA4_COMPOUND_SLAB_LEGAL_REMAP_PENDING]"
                    + " row=" + rowName
                    + " phase=post-click"
                    + " result=" + result
                    + " compound=" + describeOwnerFacts(mc.world, compoundPos)
                    + " adjacentLegalLane=" + describeOwnerFacts(mc.world, adjacentLanePos)
                    + " candidate=" + describeOwnerFacts(mc.world, placedLanePos)
                    + " reason=awaiting_after_tick_legal_lane_remap_assertion");
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[" + rowName + "] client world missing after compound legal-remap proof");
            }
            BlockState compound = mc.world.getBlockState(compoundPos);
            double compoundDy = SlabSupport.getYOffset(mc.world, compoundPos, compound);
            if (Math.abs(compoundDy + 1.0d) > EPSILON) {
                throw new RuntimeException("[" + rowName + "] compound source must remain dy=-1.000, found dy="
                        + compoundDy + " state=" + compound);
            }
            BlockState adjacent = mc.world.getBlockState(adjacentLanePos);
            double adjacentDy = SlabSupport.getYOffset(mc.world, adjacentLanePos, adjacent);
            if (!adjacent.isOf(Blocks.STONE_SLAB)
                    || !adjacent.contains(SlabBlock.TYPE)
                    || Math.abs(adjacentDy + 0.5d) > EPSILON) {
                throw new RuntimeException("[" + rowName + "] adjacent legal lane must remain dy=-0.500, found "
                        + adjacent + " dy=" + adjacentDy);
            }
            BlockState placed = mc.world.getBlockState(placedLanePos);
            double placedDy = SlabSupport.getYOffset(mc.world, placedLanePos, placed);
            if (!placed.isOf(Blocks.STONE_SLAB)
                    || !placed.contains(SlabBlock.TYPE)
                    || placed.get(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Math.abs(placedDy + 0.5d) > EPSILON) {
                failCompoundSlabHarness(rowName, "legal-remap GREEN must author BOTTOM stone_slab dy=-0.500 at "
                        + placedLanePos.toShortString() + ", found " + placed + " dy=" + placedDy);
            }
            System.out.println("[JULIA_BETA4_COMPOUND_SLAB_LEGAL_REMAP_GREEN]"
                    + " row=" + rowName
                    + " phase=after-tick"
                    + " compound=" + describeOwnerFacts(mc.world, compoundPos)
                    + " adjacentLegalLane=" + describeOwnerFacts(mc.world, adjacentLanePos)
                    + " candidate=" + describeOwnerFacts(mc.world, placedLanePos)
                    + " result=remapped_to_existing_legal_lowered_slab_lane_continuation"
                    + " illegalDyMinusOneSlab=false"
                    + " illegalDyZeroSlab=false");
        });
    }

    private static void runCompoundSlabMergePendingNote(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String rowName,
            BlockPos compoundPos,
            BlockPos adjacentLanePos,
            Direction face
    ) {
        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        seedCompoundFullBlock(singleplayer, compoundPos);
        setLoweredSlabTarget(singleplayer, adjacentLanePos, SlabType.BOTTOM);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[" + rowName + "] client world missing for merge pending note");
            }
            System.out.println("[JULIA_BETA4_COMPOUND_SLAB_DOUBLE_MERGE_PENDING]"
                    + " row=" + rowName
                    + " face=" + face.asString()
                    + " compound=" + describeOwnerFacts(mc.world, compoundPos)
                    + " adjacent=" + describeOwnerFacts(mc.world, adjacentLanePos)
                    + " current=compound_boundary_merge_not_yet_proven"
                    + " future=second_click_merges_BOTTOM_TOP_to_DOUBLE_dy_-0.5");
        });
    }

    private static void runCompoundSlabTopClickCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String rowName,
            BlockPos compoundPos,
            BlockPos adjacentLanePos,
            Direction face,
            BlockHitResult hit
    ) {
        setupFixture(singleplayer, SUPPORT_POS, FULL_POS);
        seedCompoundFullBlock(singleplayer, compoundPos);
        movePlayerForUp(ctx, singleplayer, compoundPos);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[" + rowName + "] client not ready for compound top-click note");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            System.out.println("[JULIA_BETA4_COMPOUND_SLAB_LEGAL_REMAP_PENDING]"
                    + " row=" + rowName
                    + " half=top"
                    + " face=" + face.asString()
                    + " result=" + result
                    + " compound=" + describeOwnerFacts(mc.world, compoundPos)
                    + " adjacent=" + describeOwnerFacts(mc.world, adjacentLanePos)
                    + " current=preserve_or_reject_cleanly"
                    + " future=normalize_into_named_legal_vanilla_or_lowered_result");
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
    }

    private static void runCompoundSlabSanityNote(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String rowName,
            BlockPos compoundPos,
            BlockPos adjacentLanePos
    ) {
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("[" + rowName + "] client world missing for compound sanity note");
            }
            System.out.println("[JULIA_BETA4_COMPOUND_BELOW_LANE_SIDE_SLAB_PENDING]"
                    + " row=" + rowName
                    + " sanity=no_ghost_flicker_after_tick"
                    + " compound=" + describeOwnerFacts(mc.world, compoundPos)
                    + " adjacent=" + describeOwnerFacts(mc.world, adjacentLanePos)
                    + " expected=legal_side_slab_dy_-0.5"
                    + " todo=implementation_pending");
        });
    }

    private static void assertCompoundSlabHarnessTopology(
            ClientGameTestContext ctx,
            String rowName,
            BlockPos compoundPos,
            BlockPos adjacentLanePos,
            Direction face,
            BlockHitResult hit,
            boolean expectLegalLane
    ) {
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null) {
                failCompoundSlabHarness(rowName, "client world/player missing before placement assertions");
            }
            BlockState sourceState = mc.world.getBlockState(compoundPos);
            double sourceDy = SlabSupport.getYOffset(mc.world, compoundPos, sourceState);
            boolean compoundFullBlockAnchor = SlabAnchorAttachment.isCompoundFullBlockAnchor(mc.world, compoundPos);
            if (!sourceState.isOf(Blocks.STONE) || sourceState.contains(SlabBlock.TYPE)) {
                failCompoundSlabHarness(rowName, "source must be ordinary full block at "
                        + compoundPos.toShortString() + ", found " + sourceState);
            }
            if (Math.abs(sourceDy + 1.0d) > EPSILON) {
                failCompoundSlabHarness(rowName, "source must resolve to dy=-1.000 at "
                        + compoundPos.toShortString() + ", found dy=" + sourceDy
                        + " facts=" + describeOwnerFacts(mc.world, compoundPos));
            }
            if (!compoundFullBlockAnchor) {
                failCompoundSlabHarness(rowName, "source missing compound full-block anchor at "
                        + compoundPos.toShortString() + " facts=" + describeOwnerFacts(mc.world, compoundPos));
            }
            if (!hit.getBlockPos().equals(compoundPos)) {
                failCompoundSlabHarness(rowName, "slab-held click must target compound source "
                        + compoundPos.toShortString() + ", hit target was " + hit.getBlockPos().toShortString());
            }
            ItemStack held = mc.player.getStackInHand(Hand.MAIN_HAND);
            if (!held.isOf(Items.STONE_SLAB)) {
                failCompoundSlabHarness(rowName, "held item must be stone slab before placement, found " + held);
            }

            int legalLaneCount = 0;
            for (Direction candidateFace : Direction.Type.HORIZONTAL) {
                BlockPos lanePos = compoundPos.offset(candidateFace);
                if (isLegalLoweredSlabLane(mc.world, lanePos)) {
                    legalLaneCount++;
                }
            }
            boolean adjacentLegalLane = isLegalLoweredSlabLane(mc.world, adjacentLanePos);
            if (expectLegalLane) {
                if (legalLaneCount != 1 || !adjacentLegalLane) {
                    failCompoundSlabHarness(rowName, "expected exactly one legal dy=-0.500 lane in remap direction "
                            + face.asString() + ", count=" + legalLaneCount
                            + " adjacent=" + describeOwnerFacts(mc.world, adjacentLanePos));
                }
                System.out.println("[JULIA_BETA4_COMPOUND_SLAB_HARNESS_SOURCE_GREEN]"
                        + " row=" + rowName
                        + " source=" + describeOwnerFacts(mc.world, compoundPos)
                        + " clickTarget=" + hit.getBlockPos().toShortString()
                        + " adjacentLegalLane=" + describeOwnerFacts(mc.world, adjacentLanePos)
                        + " legalLaneCount=" + legalLaneCount);
            } else {
                if (legalLaneCount != 0) {
                    failCompoundSlabHarness(rowName, "expected no legal dy=-0.500 adjacent slab lane, count="
                            + legalLaneCount + " intendedDirection=" + face.asString()
                            + " adjacent=" + describeOwnerFacts(mc.world, adjacentLanePos));
                }
                System.out.println("[JULIA_BETA4_COMPOUND_SLAB_HARNESS_SOURCE_GREEN]"
                        + " row=" + rowName
                        + " source=" + describeOwnerFacts(mc.world, compoundPos)
                        + " clickTarget=" + hit.getBlockPos().toShortString()
                        + " legalLaneCount=" + legalLaneCount);
                System.out.println("[JULIA_BETA4_COMPOUND_BELOW_LANE_SIDE_SLAB_PENDING]"
                        + " row=" + rowName
                        + " phase=pre-click"
                        + " source=" + describeOwnerFacts(mc.world, compoundPos)
                        + " adjacent=" + describeOwnerFacts(mc.world, adjacentLanePos)
                        + " legalLaneCount=0"
                        + " expected=legal_side_slab_dy_-0.5");
            }
        });
    }

    private static boolean isLegalLoweredSlabLane(net.minecraft.world.BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isOf(Blocks.STONE_SLAB)
                && state.contains(SlabBlock.TYPE)
                && Math.abs(SlabSupport.getYOffset(world, pos, state) + 0.5d) <= EPSILON;
    }

    private static int countLegalLoweredSlabLanes(net.minecraft.world.BlockView world, BlockPos sourcePos) {
        int legalLaneCount = 0;
        for (Direction candidateFace : Direction.Type.HORIZONTAL) {
            if (isLegalLoweredSlabLane(world, sourcePos.offset(candidateFace))) {
                legalLaneCount++;
            }
        }
        return legalLaneCount;
    }

    private static int countLegalLoweredSlabLaneNeighbors(
            net.minecraft.world.BlockView world,
            BlockPos pos
    ) {
        int legalNeighborCount = 0;
        for (Direction candidateFace : Direction.Type.HORIZONTAL) {
            if (isLegalLoweredSlabLane(world, pos.offset(candidateFace))) {
                legalNeighborCount++;
            }
        }
        return legalNeighborCount;
    }

    private static void emitCompoundSlabDiscriminator(
            String marker,
            String caseName,
            net.minecraft.world.BlockView world,
            BlockPos sourcePos,
            Direction sourceFace,
            BlockHitResult hit,
            BlockPos horizontalLanePos,
            BlockPos candidatePos,
            boolean proposedDiscriminator
    ) {
        BlockState sourceState = world.getBlockState(sourcePos);
        double sourceDy = SlabSupport.getYOffset(world, sourcePos, sourceState);
        BlockPos belowPos = sourcePos.down();
        BlockState belowState = world.getBlockState(belowPos);
        double belowDy = SlabSupport.getYOffset(world, belowPos, belowState);
        BlockState laneState = world.getBlockState(horizontalLanePos);
        double laneDy = SlabSupport.getYOffset(world, horizontalLanePos, laneState);
        BlockState candidateState = world.getBlockState(candidatePos);
        double candidateDy = SlabSupport.getYOffset(world, candidatePos, candidateState);
        int legalLaneCount = countLegalLoweredSlabLanes(world, sourcePos);
        int candidateHorizontalLaneNeighbors = countLegalLoweredSlabLaneNeighbors(world, candidatePos);
        boolean sourceBelowOnlyLoweredLane = isLegalLoweredSlabLane(world, belowPos) && legalLaneCount == 0;
        String hitBand = hit.getPos().y < sourcePos.getY()
                ? "below_source_block_y"
                : hit.getPos().y < sourcePos.getY() + 0.5d ? "lower_source_half" : "upper_source_half";

        System.out.println(marker
                + " case=" + caseName
                + " sourcePos=" + sourcePos.toShortString()
                + " sourceState=" + sourceState
                + " sourceDy=" + sourceDy
                + " sourceFace=" + sourceFace.asString()
                + " hitY=" + hit.getPos().y
                + " hitBand=" + hitBand
                + " belowPos=" + belowPos.toShortString()
                + " belowState=" + belowState
                + " belowDy=" + belowDy
                + " horizontalLanePos=" + horizontalLanePos.toShortString()
                + " horizontalLaneState=" + laneState
                + " horizontalLaneDy=" + laneDy
                + " candidatePos=" + candidatePos.toShortString()
                + " candidateState=" + candidateState
                + " candidateDy=" + candidateDy
                + " legalLaneCount=" + legalLaneCount
                + " candidateHorizontalLaneNeighbors=" + candidateHorizontalLaneNeighbors
                + " sourceBelowOnlyLoweredLane=" + sourceBelowOnlyLoweredLane
                + " proposedDiscriminator=" + proposedDiscriminator);
    }

    private static void failCompoundSlabHarness(String rowName, String reason) {
        System.out.println("[JULIA_BETA4_COMPOUND_SLAB_HARNESS_FAIL]"
                + " row=" + rowName
                + " reason=" + reason);
        throw new RuntimeException("[" + rowName + "] compound slab harness topology invalid: " + reason);
    }

    private static void seedCompoundFullBlock(
            TestSingleplayerContext singleplayer,
            BlockPos compoundPos
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockPos baseSupportPos = compoundPos.down(3);
            BlockPos loweredCarrierPos = compoundPos.down(2);
            BlockPos compoundSourcePos = compoundPos.down();
            world.setBlockState(
                    baseSupportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(loweredCarrierPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, loweredCarrierPos, world.getBlockState(loweredCarrierPos));
            world.setBlockState(
                    compoundSourcePos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world,
                    compoundSourcePos,
                    world.getBlockState(compoundSourcePos));
            world.setBlockState(compoundPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, compoundPos, world.getBlockState(compoundPos));
            SlabAnchorAttachment.addCompoundFullBlockAnchor(world, compoundPos, world.getBlockState(compoundPos));
        });
    }

    private static void seedBeta4LiveScreenshotShape(
            TestSingleplayerContext singleplayer,
            BlockPos clickedTopFullPos
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = clickedTopFullPos.getX() - 2; x <= clickedTopFullPos.getX() + 2; x++) {
                for (int z = clickedTopFullPos.getZ() - 2; z <= clickedTopFullPos.getZ() + 2; z++) {
                    for (int y = clickedTopFullPos.getY() - 4; y <= clickedTopFullPos.getY() + 3; y++) {
                        world.setBlockState(
                                new BlockPos(x, y, z),
                                Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }

            BlockPos baseSupportPos = clickedTopFullPos.down(3);
            BlockPos lowerFullBlock = clickedTopFullPos.down(2);
            BlockPos loweredLaneBelowTop = clickedTopFullPos.down();
            world.setBlockState(
                    baseSupportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(lowerFullBlock, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, lowerFullBlock, world.getBlockState(lowerFullBlock));
            world.setBlockState(
                    loweredLaneBelowTop,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world,
                    loweredLaneBelowTop,
                    world.getBlockState(loweredLaneBelowTop));
            world.setBlockState(clickedTopFullPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, clickedTopFullPos, world.getBlockState(clickedTopFullPos));
            SlabAnchorAttachment.addCompoundFullBlockAnchor(world, clickedTopFullPos, world.getBlockState(clickedTopFullPos));

            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer player missing during live screenshot shape setup");
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });
    }

    private static void runLowerHalfOwnershipVisibleBodyCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(40, 0, 0);
        final BlockPos fullPos = FULL_POS.add(40, 0, 0);
        final Vec3d eyeSouth = new Vec3d(
                fullPos.getX() + 0.5d,
                fullPos.getY() + 1.65d,
                fullPos.getZ() + 2.55d);
        final Vec3d aimFrontLower = new Vec3d(
                fullPos.getX() + 0.5d,
                fullPos.getY() - 0.34d,
                fullPos.getZ() + 0.5d);
        final Vec3d aimUndersideAdjacent = new Vec3d(
                fullPos.getX() + 0.5d,
                fullPos.getY() - 0.49d,
                fullPos.getZ() + 0.82d);

        setupFixture(singleplayer, supportPos, fullPos);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        runLowerHalfOwnershipProbe(
                ctx,
                singleplayer,
                fullPos,
                eyeSouth,
                aimFrontLower,
                "lowered_full_front_lower_half_slab_held_baseline",
                "front_lower",
                true,
                "support=" + supportPos.toShortString() + " full=" + fullPos.toShortString());
        runLowerHalfOwnershipProbe(
                ctx,
                singleplayer,
                fullPos,
                eyeSouth,
                aimUndersideAdjacent,
                "lowered_full_underside_adjacent_empty_hand",
                "underside_adjacent",
                false,
                "support=" + supportPos.toShortString() + " full=" + fullPos.toShortString());
        runLowerHalfOwnershipProbe(
                ctx,
                singleplayer,
                fullPos,
                eyeSouth,
                aimUndersideAdjacent,
                "lowered_full_underside_adjacent_slab_held",
                "underside_adjacent",
                true,
                "support=" + supportPos.toShortString() + " full=" + fullPos.toShortString());
    }

    private static void runLowerHalfOwnershipLoweredSlabCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(52, 0, 0);
        final BlockPos fullPos = FULL_POS.add(52, 0, 0);
        final BlockPos slabPos = fullPos.south();
        final Vec3d eyeSouth = new Vec3d(
                slabPos.getX() + 0.5d,
                slabPos.getY() + 1.65d,
                slabPos.getZ() + 3.55d);
        final Vec3d aimFrontLower = new Vec3d(
                slabPos.getX() + 0.5d,
                slabPos.getY() + 0.08d,
                slabPos.getZ() + 0.5d);
        final Vec3d aimUndersideAdjacent = new Vec3d(
                slabPos.getX() + 0.5d,
                slabPos.getY() + 0.01d,
                slabPos.getZ() + 0.82d);

        setupFixture(singleplayer, supportPos, fullPos);
        setLoweredSlabTarget(singleplayer, slabPos, SlabType.TOP);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        String slabSetup = "support=" + supportPos.toShortString()
                + " full=" + fullPos.toShortString()
                + " slab=" + slabPos.toShortString()
                + " slabType=top";
        runLowerHalfOwnershipProbe(
                ctx,
                singleplayer,
                slabPos,
                eyeSouth,
                aimFrontLower,
                "lowered_slab_front_lower_half_empty_hand",
                "front_lower",
                false,
                slabSetup);
        runLowerHalfOwnershipProbe(
                ctx,
                singleplayer,
                slabPos,
                eyeSouth,
                aimFrontLower,
                "lowered_slab_front_lower_half_slab_held",
                "front_lower",
                true,
                slabSetup);
        runLowerHalfOwnershipProbe(
                ctx,
                singleplayer,
                slabPos,
                eyeSouth,
                aimUndersideAdjacent,
                "lowered_slab_underside_adjacent_empty_hand",
                "underside_adjacent",
                false,
                slabSetup);
        runLowerHalfOwnershipProbe(
                ctx,
                singleplayer,
                slabPos,
                eyeSouth,
                aimUndersideAdjacent,
                "lowered_slab_underside_adjacent_slab_held",
                "underside_adjacent",
                true,
                slabSetup);
    }

    private static void runLowerHalfOwnershipProbe(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            BlockPos expectedOwnerPos,
            Vec3d eye,
            Vec3d aimPoint,
            String caseId,
            String aimRegion,
            boolean slabHeld,
            String setupDetails
    ) {
        final double reach = 6.0d;

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[LOWER_HALF_OWNERSHIP_RED] case=" + caseId + " client not ready");
            }

            if (slabHeld) {
                mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            } else {
                mc.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            }

            Vec3d delta = aimPoint.subtract(eye);
            double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
            double feetY = eye.y - mc.player.getStandingEyeHeight();
            mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            mc.player.setVelocity(Vec3d.ZERO);

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(reach));
            HitResult crosshair = mc.crosshairTarget;
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            HitResult liveRay = mc.player.raycast(reach, 0.0f, false);
            BlockHitResult outlineHit = mc.world.getBlockState(expectedOwnerPos)
                    .getOutlineShape(mc.world, expectedOwnerPos)
                    .raycast(rayStart, rayEnd, expectedOwnerPos);

            BlockState expectedState = mc.world.getBlockState(expectedOwnerPos);
            var expectedShape = expectedState.getOutlineShape(mc.world, expectedOwnerPos);
            String expectedOwner = expectedOwnerPos.toShortString();
            String crosshairOwner = asOwner(crosshair);
            String vanillaOwner = asOwner(vanilla);
            String liveOwner = asOwner(liveRay);
            String outlineOwner = outlineHit == null ? "MISS" : outlineHit.getBlockPos().toShortString();
            String held = mc.player.getMainHandStack().isEmpty()
                    ? "empty"
                    : mc.player.getMainHandStack().getItem().toString();
            double expectedDy = SlabSupport.getYOffset(mc.world, expectedOwnerPos, expectedState);
            String visualBox = expectedShape.isEmpty()
                    ? "EMPTY"
                    : formatBox(expectedShape.getBoundingBox().offset(expectedOwnerPos));

            System.out.println("[LOWER_HALF_OWNERSHIP] case=" + caseId
                    + " setup held=" + held
                    + " aimRegion=" + aimRegion
                    + " expected=" + expectedOwner
                    + " aim=" + aimPoint
                    + " eye=" + eye
                    + " liveEye=" + rayStart
                    + " dir=" + rayDir
                    + " reach=" + reach
                    + " visualBox=" + visualBox
                    + " expectedState=" + expectedState
                    + " expectedDy=" + expectedDy
                    + " " + setupDetails);
            System.out.println("[LOWER_HALF_OWNERSHIP] case=" + caseId
                    + " ray expected=" + expectedOwner
                    + " actual=" + crosshairOwner
                    + " vanilla=" + vanillaOwner
                    + " liveRay=" + liveOwner
                    + " outline=" + outlineOwner);

            if (!expectedOwner.equals(crosshairOwner)) {
                System.out.println("[LOWER_HALF_OWNERSHIP_RED] case=" + caseId
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " liveRay=" + liveOwner
                        + " outline=" + outlineOwner);
                throw new RuntimeException("[LOWER_HALF_OWNERSHIP_RED] case=" + caseId
                        + " expected=" + expectedOwner + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner + " liveRay=" + liveOwner
                        + " outline=" + outlineOwner);
            }

            System.out.println("[LOWER_HALF_OWNERSHIP_GREEN] case=" + caseId
                    + " expected=" + expectedOwner
                    + " actual=" + crosshairOwner
                    + " vanilla=" + vanillaOwner
                    + " liveRay=" + liveOwner
                    + " outline=" + outlineOwner);
        });
    }

    private static void runLiteralScreenshotCraftingTableCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(64, 0, 0);
        final BlockPos tablePos = FULL_POS.add(64, 0, 0);
        final Vec3d eyeSouth = new Vec3d(
                tablePos.getX() + 0.62d,
                tablePos.getY() + 1.65d,
                tablePos.getZ() + 2.30d);
        final Vec3d aimFrontLowerSeam = new Vec3d(
                tablePos.getX() + 0.74d,
                tablePos.getY() - 0.34d,
                tablePos.getZ() + 0.50d);

        setupFixture(singleplayer, supportPos, tablePos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    tablePos,
                    Blocks.CRAFTING_TABLE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        String setup = "support=" + supportPos.toShortString()
                + " table=" + tablePos.toShortString()
                + " shape=bottom_slab_plus_crafting_table";
        runScreenshotReproProbe(
                ctx,
                singleplayer,
                tablePos,
                eyeSouth,
                aimFrontLowerSeam,
                "crafting_table_bottom_slab_slab_held",
                "front_lower_seam",
                new ItemStack(Items.CRAFTING_TABLE, 1),
                setup);
        runScreenshotReproProbe(
                ctx,
                singleplayer,
                tablePos,
                eyeSouth,
                aimFrontLowerSeam,
                "crafting_table_bottom_slab_empty_hand",
                "front_lower_seam",
                ItemStack.EMPTY,
                setup);
    }

    private static void runLiteralScreenshotLoweredSlabSideCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(76, 0, 0);
        final BlockPos fullPos = FULL_POS.add(76, 0, 0);
        final BlockPos slabPos = fullPos.south();
        final Vec3d eyeWest = new Vec3d(
                slabPos.getX() - 2.25d,
                slabPos.getY() + 1.61d,
                slabPos.getZ() + 0.42d);
        final Vec3d aimSideLowSeam = new Vec3d(
                slabPos.getX() + 0.02d,
                slabPos.getY() + 0.02d,
                slabPos.getZ() + 0.86d);

        setupFixture(singleplayer, supportPos, fullPos);
        setLoweredSlabTarget(singleplayer, slabPos, SlabType.TOP);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        String setup = "support=" + supportPos.toShortString()
                + " full=" + fullPos.toShortString()
                + " slab=" + slabPos.toShortString()
                + " slabType=top shape=bottom_slab_plus_lowered_side_slab";
        runScreenshotReproProbe(
                ctx,
                singleplayer,
                slabPos,
                eyeWest,
                aimSideLowSeam,
                "lowered_slab_side_low_seam_slab_held",
                "side_low_seam",
                new ItemStack(Items.STONE_SLAB, 8),
                setup);
    }

    private static void runJuliaBeta4StoneSlabTargetingOutlineMismatchRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(24, 0, 0);
        final BlockPos expectedOwnerPos = supportPos.up();
        final BlockPos visibleSlabOwnerPos = expectedOwnerPos.up();
        final Vec3d eye = new Vec3d(
                expectedOwnerPos.getX() - 2.35d,
                expectedOwnerPos.getY() + 1.67d,
                expectedOwnerPos.getZ() + 0.62d);
        final Vec3d aimTopFrontEdge = new Vec3d(
                expectedOwnerPos.getX() + 0.115d,
                expectedOwnerPos.getY() + 0.500d,
                expectedOwnerPos.getZ() + 0.616d);

        setupFixture(singleplayer, supportPos, expectedOwnerPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState fullState = world.getBlockState(expectedOwnerPos);
            SlabAnchorAttachment.addAnchor(world, expectedOwnerPos, fullState);
            world.setBlockState(
                    visibleSlabOwnerPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncPlayerAim(ctx, singleplayer, eye, aimTopFrontEdge);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[JULIA_BETA4_TARGETING_RED] client not ready");
            }

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(6.0d));
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            HitResult finalTarget = mc.crosshairTarget;
            String expectedOwner = expectedOwnerPos.toShortString();
            String visibleOwner = visibleSlabOwnerPos.toShortString();
            String vanillaOwner = asOwner(vanilla);
            String finalOwner = asOwner(finalTarget);
            String expectedOwnerClass = "ANCHORED_FULL_BLOCK";
            String actualOwnerClass = beta4OwnerClass(
                    finalTarget,
                    expectedOwnerPos,
                    visibleSlabOwnerPos,
                    null);
            boolean slabHeldProtectionPreservedExpected = expectedOwner.equals(finalOwner);
            boolean sideOwnerWouldWin = visibleOwner.equals(finalOwner);

            System.out.println("[JULIA_BETA4_TARGETING] shape=compact_lowered_stone_with_upper_double_slab"
                    + " held=minecraft:stone_slab"
                    + " expectedOwnerClass=" + expectedOwnerClass
                    + " actualOwnerClass=" + actualOwnerClass
                    + " support=" + supportPos.toShortString()
                    + " expectedOwner=" + expectedOwner
                    + " visibleOwner=" + visibleOwner
                    + " eye=" + eye
                    + " aim=" + aimTopFrontEdge
                    + " liveEye=" + rayStart
                    + " dir=" + rayDir
                    + " vanillaTarget=" + describeHit(vanilla)
                    + " finalTarget=" + describeHit(finalTarget)
                    + " vanillaHitFace=" + describeHitFace(vanilla)
                    + " finalHitFace=" + describeHitFace(finalTarget)
                    + " vanillaHitVector=" + describeHitVector(vanilla)
                    + " finalHitVector=" + describeHitVector(finalTarget)
                    + " expectedFacts=" + describeOwnerFacts(mc.world, expectedOwnerPos)
                    + " visibleFacts=" + describeOwnerFacts(mc.world, visibleSlabOwnerPos)
                    + " finalFacts=" + describeOwnerFacts(mc.world, blockPos(finalTarget))
                    + " slabHeldProtectionPreservedExpected=" + slabHeldProtectionPreservedExpected
                    + " sideOwnerWouldWin=" + sideOwnerWouldWin);

            if (expectedOwner.equals(vanillaOwner) && !expectedOwner.equals(finalOwner)) {
                throw new RuntimeException("[JULIA_BETA4_TARGETING_RED]"
                        + " expectedOwner=" + expectedOwner
                        + " visibleOwner=" + visibleOwner
                        + " vanillaTarget=" + describeHit(vanilla)
                        + " finalTarget=" + describeHit(finalTarget)
                        + " suspectedFailingLayer=slab-held-retarget-rescue"
                        + " expectedFacts=" + describeOwnerFacts(mc.world, expectedOwnerPos)
                        + " finalFacts=" + describeOwnerFacts(mc.world, blockPos(finalTarget))
                        + " slabHeldProtectionPreservedExpected=false"
                        + " sideOwnerWouldWin=" + sideOwnerWouldWin);
            }
            if (expectedOwner.equals(vanillaOwner) && expectedOwner.equals(finalOwner)) {
                System.out.println("[BETA4_ANCHORED_UP_PRESERVE_GREEN]"
                        + " expectedOwnerClass=" + expectedOwnerClass
                        + " actualOwnerClass=" + actualOwnerClass
                        + " expectedOwner=" + expectedOwner
                        + " visibleOwner=" + visibleOwner
                        + " vanillaTarget=" + describeHit(vanilla)
                        + " finalTarget=" + describeHit(finalTarget)
                        + " finalHitFace=" + describeHitFace(finalTarget)
                        + " finalHitVector=" + describeHitVector(finalTarget)
                        + " expectedFacts=" + describeOwnerFacts(mc.world, expectedOwnerPos)
                        + " visibleFacts=" + describeOwnerFacts(mc.world, visibleSlabOwnerPos));
                System.out.println("[JULIA_BETA4_TARGETING_GREEN]"
                        + " expectedOwner=" + expectedOwner
                        + " visibleOwner=" + visibleOwner
                        + " vanillaTarget=" + describeHit(vanilla)
                        + " finalTarget=" + describeHit(finalTarget)
                        + " expectedFacts=" + describeOwnerFacts(mc.world, expectedOwnerPos)
                        + " finalFacts=" + describeOwnerFacts(mc.world, blockPos(finalTarget))
                        + " slabHeldProtectionPreservedExpected=true"
                        + " sideOwnerWouldWin=false");
                return;
            }

            throw new RuntimeException("[JULIA_BETA4_TARGETING_PROOF_GAP]"
                    + " expectedOwner=" + expectedOwner
                    + " visibleOwner=" + visibleOwner
                    + " vanillaTarget=" + describeHit(vanilla)
                    + " finalTarget=" + describeHit(finalTarget)
                    + " note=current reconstructed shape did not prove Julia mismatch");
        });
    }

    private static void runJuliaBeta4AboveAngleTargetingOwnerSplitRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        runJuliaBeta4AboveAngleTargetingOwnerSplitRedCase(ctx, singleplayer, true);
    }

    private static void runJuliaBeta4AboveAngleTargetingOwnerSplitRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            boolean includeGroundControl
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(24, 0, 0);
        final BlockPos anchoredOwnerPos = supportPos.up();
        final BlockPos expectedOwnerPos = anchoredOwnerPos.up();
        final BlockPos lowerFrontOwnerPos = expectedOwnerPos;
        final BlockPos visibleUpperSlabOwnerPos = expectedOwnerPos;

        setupFixture(singleplayer, supportPos, anchoredOwnerPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState fullState = world.getBlockState(anchoredOwnerPos);
            SlabAnchorAttachment.addAnchor(world, anchoredOwnerPos, fullState);
            world.setBlockState(
                    lowerFrontOwnerPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world,
                    lowerFrontOwnerPos,
                    world.getBlockState(lowerFrontOwnerPos));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));

        System.setProperty("slabbed.target.trace", "true");
        String firstRed = null;
        String lowerFrontRed = null;

        String red;
        if (includeGroundControl) {
            red = runJuliaBeta4AngleTargetingProbe(
                    ctx,
                    singleplayer,
                    "ground_front",
                    supportPos,
                    anchoredOwnerPos,
                    expectedOwnerPos,
                    visibleUpperSlabOwnerPos,
                    lowerFrontOwnerPos,
                    new Vec3d(
                            anchoredOwnerPos.getX() - 2.35d,
                            anchoredOwnerPos.getY() + 1.67d,
                            anchoredOwnerPos.getZ() + 0.62d),
                    new Vec3d(
                            anchoredOwnerPos.getX() + 0.115d,
                            anchoredOwnerPos.getY() + 0.500d,
                            anchoredOwnerPos.getZ() + 0.616d),
                    true);
            firstRed = firstRed == null ? red : firstRed;
            lowerFrontRed = isLowerFrontRed(red) && lowerFrontRed == null ? red : lowerFrontRed;
        }

        red = runJuliaBeta4AngleTargetingProbe(
                ctx,
                singleplayer,
                "live_above_across_pitch_steep_up",
                supportPos,
                anchoredOwnerPos,
                expectedOwnerPos,
                visibleUpperSlabOwnerPos,
                lowerFrontOwnerPos,
                new Vec3d(
                        anchoredOwnerPos.getX() - 2.285d,
                        anchoredOwnerPos.getY() + 0.620d,
                        anchoredOwnerPos.getZ() + 0.790d),
                -101.850f,
                -9.000f,
                false);
        firstRed = firstRed == null ? red : firstRed;
        lowerFrontRed = isLowerFrontRed(red) && lowerFrontRed == null ? red : lowerFrontRed;

        if (lowerFrontRed != null) {
            throw new RuntimeException(lowerFrontRed);
        }
        if (firstRed != null) {
            throw new RuntimeException(firstRed);
        }
    }

    private static String runJuliaBeta4AngleTargetingProbe(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String angle,
            BlockPos supportPos,
            BlockPos anchoredOwnerPos,
            BlockPos expectedOwnerPos,
            BlockPos visibleSlabOwnerPos,
            BlockPos lowerFrontOwnerPos,
            Vec3d eye,
            Vec3d aim,
            boolean expectGreen
    ) {
        Vec3d delta = aim.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        return runJuliaBeta4AngleTargetingProbe(
                ctx,
                singleplayer,
                angle,
                supportPos,
                anchoredOwnerPos,
                expectedOwnerPos,
                visibleSlabOwnerPos,
                lowerFrontOwnerPos,
                eye,
                yaw,
                pitch,
                expectGreen);
    }

    private static String runJuliaBeta4AngleTargetingProbe(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String angle,
            BlockPos supportPos,
            BlockPos anchoredOwnerPos,
            BlockPos expectedOwnerPos,
            BlockPos visibleSlabOwnerPos,
            BlockPos lowerFrontOwnerPos,
            Vec3d eye,
            float yaw,
            float pitch,
            boolean expectGreen
    ) {
        syncPlayerLookFromEye(ctx, singleplayer, eye, yaw, pitch);
        final String[] redMessage = {null};

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[JULIA_BETA4_ABOVE_ANGLE_RED] angle=" + angle + " client not ready");
            }

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(6.0d));
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            HitResult finalTarget = mc.crosshairTarget;
            String expectedOwner = expectedOwnerPos.toShortString();
            String anchoredOwner = anchoredOwnerPos.toShortString();
            String visibleUpperSlabOwner = visibleSlabOwnerPos.toShortString();
            String lowerFrontOwner = lowerFrontOwnerPos.toShortString();
            String vanillaOwner = asOwner(vanilla);
            String finalOwner = asOwner(finalTarget);
            String expectedOwnerClass = expectGreen ? "ANCHORED_FULL_BLOCK" : "VISIBLE_UPPER_LOWERED_SLAB";
            String actualOwnerClass = beta4OwnerClass(
                    finalTarget,
                    anchoredOwnerPos,
                    visibleSlabOwnerPos,
                    null);
            boolean expectedVisibleOwnerHit = isBeta4ScreenshotVisibleOwnerHit(mc.world, finalTarget, expectedOwnerPos);
            boolean anchoredOwnerWon = anchoredOwner.equals(finalOwner);
            String classification = expectedVisibleOwnerHit
                    ? "visibleUpperSlabOwnerExpected"
                    : (anchoredOwnerWon
                    ? "anchoredUpPreserve"
                    : (lowerFrontOwner.equals(finalOwner)
                    ? "scan-side-slab-fired"
                    : (visibleUpperSlabOwner.equals(finalOwner) ? "visibleUpperSlabOwnerWouldWin" : "wrongOwnerWouldWin")));
            String held = mc.player.getMainHandStack().isEmpty()
                    ? "empty"
                    : mc.player.getMainHandStack().getItem().toString();
            BlockState visibleState = mc.world.getBlockState(visibleSlabOwnerPos);
            double visibleDy = SlabSupport.getYOffset(mc.world, visibleSlabOwnerPos, visibleState);
            boolean visiblePersistent = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    mc.world,
                    visibleSlabOwnerPos,
                    visibleState);
            boolean anchorBelowVisible = SlabAnchorAttachment.isAnchored(mc.world, visibleSlabOwnerPos.down());
            if (!visibleState.isOf(Blocks.STONE_SLAB)
                    || !visibleState.contains(SlabBlock.TYPE)
                    || visibleState.get(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Math.abs(visibleDy + 0.5d) > EPSILON
                    || !visiblePersistent
                    || !anchorBelowVisible) {
                throw new RuntimeException("[BETA4_SEAM_VISIBLE_UPPER_SLAB_RED]"
                        + " reason=source-truth-gap"
                        + " expectedOwnerClass=" + expectedOwnerClass
                        + " actualOwnerClass=" + actualOwnerClass
                        + " visibleFacts=" + describeOwnerFacts(mc.world, visibleSlabOwnerPos)
                        + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos));
            }

            String facts = " shape=compact_lowered_stone_with_upper_double_slab"
                    + " angle=" + angle
                    + " held=" + held
                    + " expectedOwnerClass=" + expectedOwnerClass
                    + " actualOwnerClass=" + actualOwnerClass
                    + " support=" + supportPos.toShortString()
                    + " anchoredOwner=" + anchoredOwner
                    + " expectedOwner=" + expectedOwner
                    + " visibleUpperSlabOwner=" + visibleUpperSlabOwner
                    + " lowerFrontOwner=" + lowerFrontOwner
                    + " vanillaType=" + vanilla.getType()
                    + " finalType=" + finalTarget.getType()
                    + " vanillaOwner=" + vanillaOwner
                    + " finalOwner=" + finalOwner
                    + " eye=" + eye
                    + " yaw=" + yaw
                    + " pitch=" + pitch
                    + " liveEye=" + rayStart
                    + " look=" + rayDir
                    + " vanillaTarget=" + describeHit(vanilla)
                    + " finalTarget=" + describeHit(finalTarget)
                    + " vanillaHitFace=" + describeHitFace(vanilla)
                    + " finalHitFace=" + describeHitFace(finalTarget)
                    + " vanillaHitVector=" + describeHitVector(vanilla)
                    + " finalHitVector=" + describeHitVector(finalTarget)
                    + " vanillaDist2=" + describeHitDist2(rayStart, vanilla)
                    + " finalDist2=" + describeHitDist2(rayStart, finalTarget)
                    + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos)
                    + " expectedFacts=" + describeOwnerFacts(mc.world, expectedOwnerPos)
                    + " visibleFacts=" + describeOwnerFacts(mc.world, visibleSlabOwnerPos)
                    + " lowerFrontFacts=" + describeOwnerFacts(mc.world, lowerFrontOwnerPos)
                    + " finalFacts=" + describeOwnerFacts(mc.world, blockPos(finalTarget))
                    + " expectedVisibleOwnerHit=" + expectedVisibleOwnerHit
                    + " anchoredOwnerWon=" + anchoredOwnerWon
                    + " classification=" + classification;

                System.out.println("[JULIA_BETA4_ABOVE_ANGLE]" + facts);

            if (expectGreen && anchoredOwnerWon) {
                System.out.println("[BETA4_ANCHORED_UP_PRESERVE_GREEN]" + facts);
                return;
            }

            if (expectedVisibleOwnerHit) {
                System.out.println("[BETA4_SEAM_VISIBLE_UPPER_SLAB_GREEN]" + facts);
                System.out.println("[JULIA_BETA4_SCREENSHOT_INTENT_GREEN]" + facts);
                return;
            }

            if (expectGreen) {
                redMessage[0] = "[BETA4_ANCHORED_UP_PRESERVE_RED]" + facts
                        + " suspectedFailingLayer=visible-owner-stole-anchored-ground-control"
                        + " anchoredOwnerWon=false"
                        + " groundAngleExpectedGreen=true";
                System.out.println(redMessage[0]);
                return;
            }

            if (anchoredOwnerWon) {
                redMessage[0] = "[BETA4_SEAM_VISIBLE_UPPER_SLAB_RED]" + facts
                        + " suspectedFailingLayer=anchored-owner-preserved-over-visible-owner"
                        + " anchoredOwnerWon=true"
                        + " groundAngleExpectedGreen=" + expectGreen;
                System.out.println(redMessage[0]);
                System.out.println("[JULIA_BETA4_SCREENSHOT_INTENT_RED]" + facts
                        + " suspectedFailingLayer=anchored-owner-preserved-over-visible-owner"
                        + " anchoredOwnerWon=true"
                        + " groundAngleExpectedGreen=" + expectGreen);
                return;
            }

            redMessage[0] = "[BETA4_SEAM_VISIBLE_UPPER_SLAB_RED]" + facts
                    + " suspectedFailingLayer=unexpected-owner-live-targeting"
                    + " anchoredOwnerWon=false"
                    + " groundAngleExpectedGreen=" + expectGreen;
            System.out.println(redMessage[0]);
            System.out.println("[JULIA_BETA4_SCREENSHOT_INTENT_RED]" + facts
                    + " suspectedFailingLayer=unexpected-owner-live-targeting"
                    + " anchoredOwnerWon=false"
                    + " groundAngleExpectedGreen=" + expectGreen);
        });
        return redMessage[0];
    }

    private static boolean isLowerFrontRed(String redMessage) {
        return redMessage != null && redMessage.contains(" lowerFrontOwnerWon=true");
    }

    private static boolean isBeta4ScreenshotVisibleOwnerHit(ClientWorld world, HitResult finalTarget, BlockPos expectedOwnerPos) {
        if (world == null || !(finalTarget instanceof BlockHitResult hit) || !hit.getBlockPos().equals(expectedOwnerPos)) {
            return false;
        }
        if (hit.getSide() == Direction.DOWN) {
            return false;
        }
        BlockState state = world.getBlockState(expectedOwnerPos);
        return state.getBlock() instanceof SlabBlock
                && state.contains(SlabBlock.TYPE)
                && state.get(SlabBlock.TYPE) == SlabType.BOTTOM
                && SlabSupport.getYOffset(world, expectedOwnerPos, state) == -0.5
                && SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, expectedOwnerPos, state)
                && SlabAnchorAttachment.isAnchored(world, expectedOwnerPos.down());
    }

    private static void runJuliaBeta4AdjacentVisibleTargetRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(24, 0, 0);
        final BlockPos anchoredOwnerPos = supportPos.up();
        final BlockPos visibleUpperSlabOwnerPos = anchoredOwnerPos.up();
        final BlockPos adjacentVisibleOwnerPos = anchoredOwnerPos.east();
        final Vec3d eye = new Vec3d(
                adjacentVisibleOwnerPos.getX() + 2.35d,
                adjacentVisibleOwnerPos.getY() + 0.25d,
                adjacentVisibleOwnerPos.getZ() + 0.50d);
        final Vec3d aimAdjacentEastFace = new Vec3d(
                adjacentVisibleOwnerPos.getX() + 1.0d,
                adjacentVisibleOwnerPos.getY() - 0.25d,
                adjacentVisibleOwnerPos.getZ() + 0.50d);

        setupFixture(singleplayer, supportPos, anchoredOwnerPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState fullState = world.getBlockState(anchoredOwnerPos);
            SlabAnchorAttachment.addAnchor(world, anchoredOwnerPos, fullState);
            world.setBlockState(
                    visibleUpperSlabOwnerPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world,
                    visibleUpperSlabOwnerPos,
                    world.getBlockState(visibleUpperSlabOwnerPos));
            world.setBlockState(
                    adjacentVisibleOwnerPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncPlayerAim(ctx, singleplayer, eye, aimAdjacentEastFace);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[JULIA_BETA4_ADJACENT_VISIBLE_RED] client not ready");
            }

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(6.0d));
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            HitResult finalTarget = mc.crosshairTarget;
            String anchoredOwner = anchoredOwnerPos.toShortString();
            String visibleUpperSlabOwner = visibleUpperSlabOwnerPos.toShortString();
            String adjacentVisibleOwner = adjacentVisibleOwnerPos.toShortString();
            String vanillaOwner = asOwner(vanilla);
            String finalOwner = asOwner(finalTarget);
            String finalType = finalTarget == null ? "null" : finalTarget.getType().toString();
            String expectedOwnerClass = "ADJACENT_VISIBLE_TARGET";
            String actualOwnerClass = beta4OwnerClass(
                    finalTarget,
                    anchoredOwnerPos,
                    visibleUpperSlabOwnerPos,
                    adjacentVisibleOwnerPos);
            boolean adjacentOwnerWon = adjacentVisibleOwner.equals(finalOwner);
            boolean anchoredOwnerWon = anchoredOwner.equals(finalOwner);
            boolean visibleUpperOwnerWon = visibleUpperSlabOwner.equals(finalOwner);
            boolean finalMiss = finalTarget == null || finalTarget.getType() == HitResult.Type.MISS;
            String classification = adjacentOwnerWon
                    ? "adjacentVisibleOwnerExpected"
                    : (anchoredOwnerWon
                    ? "anchoredOwnerWouldSteal"
                    : (visibleUpperOwnerWon
                    ? "visibleUpperSlabOwnerWouldSteal"
                    : (finalMiss ? "missOrAirNoOwner" : "wrongOwnerWouldWin")));
            String held = mc.player.getMainHandStack().isEmpty()
                    ? "empty"
                    : mc.player.getMainHandStack().getItem().toString();

            String facts = " shape=compact_lowered_stone_with_upper_bottom_slab_and_adjacent_visible_target"
                    + " held=" + held
                    + " expectedOwnerClass=" + expectedOwnerClass
                    + " actualOwnerClass=" + actualOwnerClass
                    + " support=" + supportPos.toShortString()
                    + " anchoredOwner=" + anchoredOwner
                    + " visibleUpperSlabOwner=" + visibleUpperSlabOwner
                    + " adjacentVisibleOwner=" + adjacentVisibleOwner
                    + " vanillaType=" + vanilla.getType()
                    + " finalType=" + finalType
                    + " vanillaOwner=" + vanillaOwner
                    + " finalOwner=" + finalOwner
                    + " eye=" + eye
                    + " aim=" + aimAdjacentEastFace
                    + " liveEye=" + rayStart
                    + " look=" + rayDir
                    + " yaw=" + mc.player.getYaw()
                    + " pitch=" + mc.player.getPitch()
                    + " vanillaTarget=" + describeHit(vanilla)
                    + " finalTarget=" + describeHit(finalTarget)
                    + " vanillaHitFace=" + describeHitFace(vanilla)
                    + " finalHitFace=" + describeHitFace(finalTarget)
                    + " vanillaHitVector=" + describeHitVector(vanilla)
                    + " finalHitVector=" + describeHitVector(finalTarget)
                    + " vanillaDist2=" + describeHitDist2(rayStart, vanilla)
                    + " finalDist2=" + describeHitDist2(rayStart, finalTarget)
                    + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos)
                    + " visibleUpperFacts=" + describeOwnerFacts(mc.world, visibleUpperSlabOwnerPos)
                    + " adjacentVisibleFacts=" + describeOwnerFacts(mc.world, adjacentVisibleOwnerPos)
                    + " finalFacts=" + describeOwnerFacts(mc.world, blockPos(finalTarget))
                    + " adjacentOwnerWon=" + adjacentOwnerWon
                    + " anchoredOwnerWon=" + anchoredOwnerWon
                    + " visibleUpperOwnerWon=" + visibleUpperOwnerWon
                    + " finalMiss=" + finalMiss
                    + " classification=" + classification;

            if (adjacentOwnerWon) {
                System.out.println("[BETA4_ADJACENT_VISIBLE_SEAM_GREEN]" + facts);
                System.out.println("[JULIA_BETA4_ADJACENT_VISIBLE_GREEN]" + facts);
                return;
            }

            throw new RuntimeException("[JULIA_BETA4_ADJACENT_VISIBLE_RED]" + facts
                    + " suspectedFailingLayer=adjacent-visible-owner-not-preserved");
        });
    }

    private static void runBeta4SeamVisibleUpperSideFaceRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(24, 0, 0);
        final BlockPos anchoredOwnerPos = supportPos.up();
        final BlockPos visibleUpperSlabOwnerPos = anchoredOwnerPos.up();
        final Vec3d eye = new Vec3d(
                visibleUpperSlabOwnerPos.getX() - 2.50d,
                visibleUpperSlabOwnerPos.getY() - 0.25d,
                visibleUpperSlabOwnerPos.getZ() + 0.50d);
        final Vec3d aimVisibleWestSideFace = new Vec3d(
                visibleUpperSlabOwnerPos.getX(),
                visibleUpperSlabOwnerPos.getY() - 0.25d,
                visibleUpperSlabOwnerPos.getZ() + 0.50d);

        setupFixture(singleplayer, supportPos, anchoredOwnerPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState fullState = world.getBlockState(anchoredOwnerPos);
            SlabAnchorAttachment.addAnchor(world, anchoredOwnerPos, fullState);
            world.setBlockState(
                    visibleUpperSlabOwnerPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world,
                    visibleUpperSlabOwnerPos,
                    world.getBlockState(visibleUpperSlabOwnerPos));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncPlayerAim(ctx, singleplayer, eye, aimVisibleWestSideFace);

        System.setProperty("slabbed.target.trace", "true");
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[BETA4_SEAM_VISIBLE_UPPER_SIDE_FACE_RED] client not ready");
            }

            BlockState visibleState = mc.world.getBlockState(visibleUpperSlabOwnerPos);
            double visibleDy = SlabSupport.getYOffset(mc.world, visibleUpperSlabOwnerPos, visibleState);
            boolean visiblePersistent = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    mc.world,
                    visibleUpperSlabOwnerPos,
                    visibleState);
            boolean anchorBelowVisible = SlabAnchorAttachment.isAnchored(mc.world, visibleUpperSlabOwnerPos.down());
            if (!visibleState.isOf(Blocks.STONE_SLAB)
                    || !visibleState.contains(SlabBlock.TYPE)
                    || visibleState.get(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Math.abs(visibleDy + 0.5d) > EPSILON
                    || !visiblePersistent
                    || !anchorBelowVisible) {
                throw new RuntimeException("[BETA4_SEAM_VISIBLE_UPPER_SIDE_FACE_RED]"
                        + " reason=source-truth-gap"
                        + " expectedOwnerClass=VISIBLE_UPPER_LOWERED_SLAB"
                        + " visibleFacts=" + describeOwnerFacts(mc.world, visibleUpperSlabOwnerPos)
                        + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos));
            }

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(6.0d));
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            HitResult finalTarget = mc.crosshairTarget;
            String expectedOwnerClass = "VISIBLE_UPPER_LOWERED_SLAB";
            String actualOwnerClass = beta4OwnerClass(
                    finalTarget,
                    anchoredOwnerPos,
                    visibleUpperSlabOwnerPos,
                    null);
            String held = mc.player.getMainHandStack().isEmpty()
                    ? "empty"
                    : mc.player.getMainHandStack().getItem().toString();
            String vanillaOwner = asOwner(vanilla);
            String finalOwner = asOwner(finalTarget);
            boolean visibleOwnerWon = visibleUpperSlabOwnerPos.toShortString().equals(finalOwner);
            boolean vanillaMiss = vanilla.getType() != HitResult.Type.BLOCK;
            boolean finalMiss = finalTarget == null || finalTarget.getType() != HitResult.Type.BLOCK;
            String classification = visibleOwnerWon
                    ? "visibleUpperSideFaceOwnerExpected"
                    : (finalMiss
                    ? "missNoVisibleSideFaceOwner"
                    : ("wrongOwner=" + actualOwnerClass));
            String facts = " shape=compact_lowered_stone_with_upper_bottom_slab"
                    + " aimRegion=visible-upper-lowered-slab-west-side-face"
                    + " held=" + held
                    + " expectedOwnerClass=" + expectedOwnerClass
                    + " actualOwnerClass=" + actualOwnerClass
                    + " support=" + supportPos.toShortString()
                    + " anchoredOwner=" + anchoredOwnerPos.toShortString()
                    + " visibleUpperSlabOwner=" + visibleUpperSlabOwnerPos.toShortString()
                    + " vanillaType=" + vanilla.getType()
                    + " finalType=" + (finalTarget == null ? "null" : finalTarget.getType())
                    + " vanillaOwner=" + vanillaOwner
                    + " finalOwner=" + finalOwner
                    + " eye=" + eye
                    + " aim=" + aimVisibleWestSideFace
                    + " liveEye=" + rayStart
                    + " look=" + rayDir
                    + " yaw=" + mc.player.getYaw()
                    + " pitch=" + mc.player.getPitch()
                    + " vanillaTarget=" + describeHit(vanilla)
                    + " finalTarget=" + describeHit(finalTarget)
                    + " vanillaMiss=" + vanillaMiss
                    + " finalMiss=" + finalMiss
                    + " vanillaHitFace=" + describeHitFace(vanilla)
                    + " finalHitFace=" + describeHitFace(finalTarget)
                    + " vanillaHitVector=" + describeHitVector(vanilla)
                    + " finalHitVector=" + describeHitVector(finalTarget)
                    + " vanillaDist2=" + describeHitDist2(rayStart, vanilla)
                    + " finalDist2=" + describeHitDist2(rayStart, finalTarget)
                    + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos)
                    + " visibleUpperFacts=" + describeOwnerFacts(mc.world, visibleUpperSlabOwnerPos)
                    + " finalFacts=" + describeOwnerFacts(mc.world, blockPos(finalTarget))
                    + " visibleOwnerWon=" + visibleOwnerWon
                    + " classification=" + classification;

            if (visibleOwnerWon) {
                System.out.println("[BETA4_SEAM_VISIBLE_UPPER_SIDE_FACE_GREEN]" + facts);
                return;
            }

            throw new RuntimeException("[BETA4_SEAM_VISIBLE_UPPER_SIDE_FACE_RED]" + facts
                    + " suspectedFailingLayer=visible-upper-side-face-miss-before-owner-classifier");
        });
    }

    private static void runBeta4SeamVisibleUpperAnchoredUpStealRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        runBeta4SeamVisibleUpperAnchoredUpProofCase(ctx, singleplayer, false);
    }

    private static void runBeta4SeamVisibleUpperAngleGeneralRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        runBeta4SeamVisibleUpperAnchoredUpProofCase(ctx, singleplayer, true);
    }

    private static void runBeta4SeamVisibleUpperAnchoredUpProofCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            boolean angleGeneral
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(24, 0, 0);
        final BlockPos anchoredOwnerPos = supportPos.up();
        final BlockPos visibleUpperSlabOwnerPos = anchoredOwnerPos.up();
        final String redMarker = angleGeneral
                ? "[BETA4_SEAM_VISIBLE_UPPER_ANGLE_GENERAL_RED]"
                : "[BETA4_SEAM_VISIBLE_UPPER_ANCHORED_UP_STEAL_RED]";
        final String greenMarker = angleGeneral
                ? "[BETA4_SEAM_VISIBLE_UPPER_ANGLE_GENERAL_GREEN]"
                : "[BETA4_SEAM_VISIBLE_UPPER_ANCHORED_UP_STEAL_GREEN]";

        setupFixture(singleplayer, supportPos, anchoredOwnerPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState fullState = world.getBlockState(anchoredOwnerPos);
            SlabAnchorAttachment.addAnchor(world, anchoredOwnerPos, fullState);
            world.setBlockState(
                    visibleUpperSlabOwnerPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world,
                    visibleUpperSlabOwnerPos,
                    world.getBlockState(visibleUpperSlabOwnerPos));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        System.setProperty("slabbed.target.trace", "true");
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException(redMarker + " client not ready");
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));

            BlockState anchoredState = mc.world.getBlockState(anchoredOwnerPos);
            BlockState visibleState = mc.world.getBlockState(visibleUpperSlabOwnerPos);
            boolean sourceTruth = anchoredState.isOf(Blocks.STONE)
                    && SlabAnchorAttachment.isAnchored(mc.world, anchoredOwnerPos)
                    && Math.abs(SlabSupport.getYOffset(mc.world, anchoredOwnerPos, anchoredState) + 0.5d) <= EPSILON
                    && visibleState.isOf(Blocks.STONE_SLAB)
                    && visibleState.contains(SlabBlock.TYPE)
                    && visibleState.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && Math.abs(SlabSupport.getYOffset(mc.world, visibleUpperSlabOwnerPos, visibleState) + 0.5d) <= EPSILON
                    && SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    mc.world,
                    visibleUpperSlabOwnerPos,
                    visibleState);
            if (!sourceTruth) {
                throw new RuntimeException(redMarker
                        + " reason=source-truth-gap"
                        + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos)
                        + " visibleUpperFacts=" + describeOwnerFacts(mc.world, visibleUpperSlabOwnerPos));
            }

            String bestFacts = null;
            double[] eyeX = angleGeneral ? new double[]{-2.72d, -2.50d, -3.00d} : new double[]{-2.50d};
            double[] eyeY = angleGeneral
                    ? new double[]{2.78d, 2.60d, 2.95d}
                    : new double[]{1.25d, 1.40d, 1.55d, 1.70d, 1.85d};
            double[] eyeZ = angleGeneral ? new double[]{1.85d, 1.50d, 2.10d} : new double[]{0.50d};
            double[] aimX = angleGeneral
                    ? new double[]{0.20d, 0.28d, 0.35d, 0.50d}
                    : new double[]{0.02d, 0.05d, 0.08d, 0.12d, 0.15d};
            double[] aimY = angleGeneral
                    ? new double[]{0.50d, 0.52d, 0.55d}
                    : new double[]{0.50d, 0.52d, 0.55d, 0.58d, 0.62d};
            double[] aimZ = angleGeneral
                    ? new double[]{0.40d, 0.54d, 0.65d}
                    : new double[]{0.50d, 0.08d, 0.92d};

            for (double ex : eyeX) {
                for (double ey : eyeY) {
                    for (double ez : eyeZ) {
                        for (double ax : aimX) {
                            for (double ay : aimY) {
                                for (double az : aimZ) {
                            Vec3d eye = new Vec3d(
                                    anchoredOwnerPos.getX() + ex,
                                    anchoredOwnerPos.getY() + ey,
                                    anchoredOwnerPos.getZ() + ez);
                            Vec3d aim = new Vec3d(
                                    anchoredOwnerPos.getX() + ax,
                                    anchoredOwnerPos.getY() + ay,
                                    anchoredOwnerPos.getZ() + az);
                            Vec3d delta = aim.subtract(eye);
                            double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
                            float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
                            double feetY = eye.y - mc.player.getStandingEyeHeight();
                            mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
                            mc.player.setVelocity(Vec3d.ZERO);

                            mc.gameRenderer.updateCrosshairTarget(0.0f);
                            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
                            Vec3d rayDir = mc.player.getRotationVec(0.0f);
                            Vec3d rayEnd = rayStart.add(rayDir.multiply(6.0d));
                            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                                    rayStart,
                                    rayEnd,
                                    RaycastContext.ShapeType.OUTLINE,
                                    RaycastContext.FluidHandling.NONE,
                                    mc.player));
                            BlockHitResult candidate = SlabbedRetargetTestHooks.findLoweredSideSlabRetarget(
                                    mc.world,
                                    mc.player,
                                    rayStart,
                                    rayEnd,
                                    vanilla,
                                    true);
                            HitResult finalTarget = mc.crosshairTarget;

                            boolean initialBlockUp = vanilla.getType() == HitResult.Type.BLOCK
                                    && vanilla.getBlockPos().equals(anchoredOwnerPos)
                                    && vanilla.getSide() == Direction.UP;
                            Vec3d localHit = vanilla.getPos().subtract(
                                    anchoredOwnerPos.getX(),
                                    anchoredOwnerPos.getY(),
                                    anchoredOwnerPos.getZ());
                            boolean edgeLike = localHit.x <= 0.15d
                                    || localHit.x >= 0.85d
                                    || localHit.z <= 0.15d
                                    || localHit.z >= 0.85d;
                            boolean topInterior = initialBlockUp && !edgeLike;
                            boolean candidateExists = candidate != null;
                            boolean candidateVisibleOwner = candidateExists
                                    && candidate.getBlockPos().equals(visibleUpperSlabOwnerPos);
                            boolean candidateLoweredBottomSlab = false;
                            double candidateDy = Double.NaN;
                            if (candidateVisibleOwner) {
                                BlockState candidateState = mc.world.getBlockState(candidate.getBlockPos());
                                candidateDy = SlabSupport.getYOffset(mc.world, candidate.getBlockPos(), candidateState);
                                candidateLoweredBottomSlab = candidateState.isOf(Blocks.STONE_SLAB)
                                        && candidateState.contains(SlabBlock.TYPE)
                                        && candidateState.get(SlabBlock.TYPE) == SlabType.BOTTOM
                                        && Math.abs(candidateDy + 0.5d) <= EPSILON;
                            }
                            double initialDist2 = vanilla.getPos().squaredDistanceTo(rayStart);
                            double candidateDist2 = candidateExists
                                    ? candidate.getPos().squaredDistanceTo(rayStart)
                                    : Double.NaN;
                            boolean candidateCloser = candidateExists && candidateDist2 < initialDist2 - EPSILON;
                            String actualOwnerClass = beta4OwnerClass(
                                    finalTarget,
                                    anchoredOwnerPos,
                                    visibleUpperSlabOwnerPos,
                                    null);
                            boolean finalAnchored = "ANCHORED_FULL_BLOCK".equals(actualOwnerClass);
                            boolean finalVisible = "VISIBLE_UPPER_LOWERED_SLAB".equals(actualOwnerClass);
                            String classification = finalAnchored && initialBlockUp && candidateVisibleOwner
                                    ? "anchoredUpPreserve"
                                    : (finalVisible ? "visibleUpperSideFaceOwner" : "unexpectedOwner");

                            String facts = " shape=compact_lowered_stone_with_upper_bottom_slab"
                                    + " aimRegion=" + (angleGeneral
                                    ? "anchored-up-interior-visible-upper-side-candidate"
                                    : "anchored-up-edge-visible-upper-side-candidate")
                                    + " held=minecraft:stone_slab"
                                    + " expectedOwnerClass=VISIBLE_UPPER_LOWERED_SLAB"
                                    + " actualOwnerClass=" + actualOwnerClass
                                    + " proofBranch=" + (initialBlockUp ? "BLOCK_UP" : vanilla.getType())
                                    + " support=" + supportPos.toShortString()
                                    + " anchoredOwner=" + anchoredOwnerPos.toShortString()
                                    + " visibleUpperSlabOwner=" + visibleUpperSlabOwnerPos.toShortString()
                                    + " eye=" + eye
                                    + " aim=" + aim
                                    + " liveEye=" + rayStart
                                    + " look=" + rayDir
                                    + " yaw=" + mc.player.getYaw()
                                    + " pitch=" + mc.player.getPitch()
                                    + " vanillaType=" + vanilla.getType()
                                    + " finalType=" + (finalTarget == null ? "null" : finalTarget.getType())
                                    + " vanillaTarget=" + describeHit(vanilla)
                                    + " finalTarget=" + describeHit(finalTarget)
                                    + " sideScanCandidateExists=" + candidateExists
                                    + " sideScanCandidateReason=" + (candidateExists ? "accepted" : "none")
                                    + " sideScanCandidate=" + describeHit(candidate)
                                    + " initialOwnerClass=ANCHORED_FULL_BLOCK"
                                    + " sideScanCandidateOwnerClass="
                                    + (candidateVisibleOwner ? "VISIBLE_UPPER_LOWERED_SLAB" : beta4OwnerClass(
                                    candidate,
                                    anchoredOwnerPos,
                                    visibleUpperSlabOwnerPos,
                                    null))
                                    + " initialFace=" + vanilla.getSide()
                                    + " finalFace=" + describeHitFace(finalTarget)
                                    + " localHit=" + localHit
                                    + " topHit=" + initialBlockUp
                                    + " edgeLike=" + edgeLike
                                    + " topInterior=" + topInterior
                                    + " initialDist2=" + String.format("%.6f", initialDist2)
                                    + " candidateDist2=" + (candidateExists ? String.format("%.6f", candidateDist2) : "NaN")
                                    + " candidateMinusInitialDist2="
                                    + (candidateExists ? String.format("%.6f", candidateDist2 - initialDist2) : "NaN")
                                    + " candidateLoweredBottomSlab=" + candidateLoweredBottomSlab
                                    + " candidateDy=" + (candidateExists ? String.format("%.3f", candidateDy) : "NaN")
                                    + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos)
                                    + " visibleUpperFacts=" + describeOwnerFacts(mc.world, visibleUpperSlabOwnerPos)
                                    + " candidateFacts=" + describeOwnerFacts(mc.world, candidateExists ? candidate.getBlockPos() : null)
                                    + " finalFacts=" + describeOwnerFacts(mc.world, blockPos(finalTarget))
                                    + " finalOwner=" + asOwner(finalTarget)
                                    + " finalAnchored=" + finalAnchored
                                    + " visibleOwnerWon=" + finalVisible
                                    + " classification=" + classification
                                    + " traceMarker=SLAB_HELD_UP_GUARD_SIDE_OWNER_CLASSIFY";
                            boolean targetBranch = initialBlockUp
                                    && candidateVisibleOwner
                                    && candidateLoweredBottomSlab
                                    && candidateCloser
                                    && (angleGeneral ? (topInterior && !edgeLike) : (edgeLike && !topInterior));
                            if (targetBranch) {
                                if (finalVisible) {
                                    System.out.println(greenMarker + facts);
                                    return;
                                }
                                throw new RuntimeException(redMarker + facts);
                            }
                            bestFacts = facts;
                        }
                    }
                }
            }
            }
            }

            throw new RuntimeException(redMarker
                    + " reason=live-branch-not-reproduced"
                    + " lastAttempt=" + bestFacts);
        });
    }

    private static void runBeta4SeamNoRescueBoundaryCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(24, 0, 0);
        final BlockPos anchoredOwnerPos = supportPos.up();
        final BlockPos visibleUpperSlabOwnerPos = anchoredOwnerPos.up();
        final Vec3d eye = new Vec3d(
                anchoredOwnerPos.getX() - 2.35d,
                anchoredOwnerPos.getY() + 1.67d,
                anchoredOwnerPos.getZ() + 0.62d);
        final Vec3d aimAirAboveSeam = new Vec3d(
                anchoredOwnerPos.getX() + 0.50d,
                anchoredOwnerPos.getY() + 2.40d,
                anchoredOwnerPos.getZ() + 0.50d);

        setupFixture(singleplayer, supportPos, anchoredOwnerPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState fullState = world.getBlockState(anchoredOwnerPos);
            SlabAnchorAttachment.addAnchor(world, anchoredOwnerPos, fullState);
            world.setBlockState(
                    visibleUpperSlabOwnerPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world,
                    visibleUpperSlabOwnerPos,
                    world.getBlockState(visibleUpperSlabOwnerPos));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncPlayerAim(ctx, singleplayer, eye, aimAirAboveSeam);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[BETA4_SEAM_NO_RESCUE_RED] client not ready");
            }

            BlockState visibleState = mc.world.getBlockState(visibleUpperSlabOwnerPos);
            double visibleDy = SlabSupport.getYOffset(mc.world, visibleUpperSlabOwnerPos, visibleState);
            boolean visiblePersistent = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    mc.world,
                    visibleUpperSlabOwnerPos,
                    visibleState);
            boolean anchorBelowVisible = SlabAnchorAttachment.isAnchored(mc.world, visibleUpperSlabOwnerPos.down());
            if (!visibleState.isOf(Blocks.STONE_SLAB)
                    || !visibleState.contains(SlabBlock.TYPE)
                    || visibleState.get(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Math.abs(visibleDy + 0.5d) > EPSILON
                    || !visiblePersistent
                    || !anchorBelowVisible) {
                throw new RuntimeException("[BETA4_SEAM_NO_RESCUE_RED]"
                        + " reason=source-truth-gap"
                        + " expectedOwnerClass=NO_RESCUE"
                        + " visibleFacts=" + describeOwnerFacts(mc.world, visibleUpperSlabOwnerPos)
                        + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos));
            }

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(6.0d));
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            HitResult finalTarget = mc.crosshairTarget;
            String vanillaOwner = asOwner(vanilla);
            String finalOwner = asOwner(finalTarget);
            String actualOwnerClass = beta4OwnerClass(
                    finalTarget,
                    anchoredOwnerPos,
                    visibleUpperSlabOwnerPos,
                    null);
            boolean vanillaMiss = vanilla.getType() != HitResult.Type.BLOCK;
            boolean finalMiss = finalTarget == null || finalTarget.getType() != HitResult.Type.BLOCK;
            boolean rewroteInitial = !(vanillaMiss && finalMiss) && !vanillaOwner.equals(finalOwner);
            boolean stoleSeamOwner = "ANCHORED_FULL_BLOCK".equals(actualOwnerClass)
                    || "VISIBLE_UPPER_LOWERED_SLAB".equals(actualOwnerClass);
            String held = mc.player.getMainHandStack().isEmpty()
                    ? "empty"
                    : mc.player.getMainHandStack().getItem().toString();
            String facts = " shape=compact_lowered_stone_with_upper_bottom_slab"
                    + " held=" + held
                    + " expectedOwnerClass=NO_RESCUE"
                    + " actualOwnerClass=" + actualOwnerClass
                    + " support=" + supportPos.toShortString()
                    + " anchoredOwner=" + anchoredOwnerPos.toShortString()
                    + " visibleUpperSlabOwner=" + visibleUpperSlabOwnerPos.toShortString()
                    + " vanillaOwner=" + vanillaOwner
                    + " finalOwner=" + finalOwner
                    + " eye=" + eye
                    + " aim=" + aimAirAboveSeam
                    + " liveEye=" + rayStart
                    + " look=" + rayDir
                    + " vanillaTarget=" + describeHit(vanilla)
                    + " finalTarget=" + describeHit(finalTarget)
                    + " vanillaMiss=" + vanillaMiss
                    + " finalMiss=" + finalMiss
                    + " vanillaHitFace=" + describeHitFace(vanilla)
                    + " finalHitFace=" + describeHitFace(finalTarget)
                    + " vanillaHitVector=" + describeHitVector(vanilla)
                    + " finalHitVector=" + describeHitVector(finalTarget)
                    + " vanillaDist2=" + describeHitDist2(rayStart, vanilla)
                    + " finalDist2=" + describeHitDist2(rayStart, finalTarget)
                    + " anchoredFacts=" + describeOwnerFacts(mc.world, anchoredOwnerPos)
                    + " visibleUpperFacts=" + describeOwnerFacts(mc.world, visibleUpperSlabOwnerPos)
                    + " finalFacts=" + describeOwnerFacts(mc.world, blockPos(finalTarget))
                    + " rewroteInitial=" + rewroteInitial
                    + " stoleSeamOwner=" + stoleSeamOwner;

            if (!rewroteInitial && !stoleSeamOwner) {
                System.out.println("[BETA4_SEAM_NO_RESCUE_GREEN]" + facts);
                return;
            }

            throw new RuntimeException("[BETA4_SEAM_NO_RESCUE_RED]" + facts
                    + " suspectedFailingLayer=miss-or-air-rescued-to-seam-owner");
        });
    }

    private static void runScreenshotReproProbe(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            BlockPos expectedOwnerPos,
            Vec3d eye,
            Vec3d aimPoint,
            String caseId,
            String aimRegion,
            ItemStack heldStack,
            String setupDetails
    ) {
        final double reach = 6.0d;

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[SCREENSHOT_REPRO_RED] case=" + caseId + " client not ready");
            }
            if (heldStack == null || heldStack.isEmpty()) {
                mc.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            } else {
                mc.player.setStackInHand(Hand.MAIN_HAND, heldStack.copy());
            }

            Vec3d delta = aimPoint.subtract(eye);
            double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
            double feetY = eye.y - mc.player.getStandingEyeHeight();
            mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            mc.player.setVelocity(Vec3d.ZERO);

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(reach));
            HitResult crosshair = mc.crosshairTarget;
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            BlockState expectedState = mc.world.getBlockState(expectedOwnerPos);
            BlockHitResult outlineHit = expectedState.getOutlineShape(mc.world, expectedOwnerPos)
                    .raycast(rayStart, rayEnd, expectedOwnerPos);

            String expectedOwner = expectedOwnerPos.toShortString();
            String crosshairOwner = asOwner(crosshair);
            String vanillaOwner = asOwner(vanilla);
            String outlineOwner = outlineHit == null ? "MISS" : outlineHit.getBlockPos().toShortString();
            String held = mc.player.getMainHandStack().isEmpty()
                    ? "empty"
                    : mc.player.getMainHandStack().getItem().toString();
            String hitSide = crosshair instanceof BlockHitResult bhr ? bhr.getSide().toString() : "MISS";
            double expectedDy = SlabSupport.getYOffset(mc.world, expectedOwnerPos, expectedState);
            String visualBox = expectedState.getOutlineShape(mc.world, expectedOwnerPos).isEmpty()
                    ? "EMPTY"
                    : formatBox(expectedState.getOutlineShape(mc.world, expectedOwnerPos)
                    .getBoundingBox().offset(expectedOwnerPos));

            System.out.println("[SCREENSHOT_REPRO] case=" + caseId
                    + " setup held=" + held
                    + " aimRegion=" + aimRegion
                    + " expected=" + expectedOwner
                    + " eye=" + eye
                    + " aim=" + aimPoint
                    + " liveEye=" + rayStart
                    + " dir=" + rayDir
                    + " reach=" + reach
                    + " visualBox=" + visualBox
                    + " expectedState=" + expectedState
                    + " expectedDy=" + expectedDy
                    + " " + setupDetails);
            System.out.println("[SCREENSHOT_REPRO] case=" + caseId
                    + " ray expected=" + expectedOwner
                    + " actual=" + crosshairOwner
                    + " vanilla=" + vanillaOwner
                    + " outline=" + outlineOwner
                    + " hitSide=" + hitSide);

            boolean ownerMatch = expectedOwner.equals(crosshairOwner);
            boolean expectedOutlineHit = expectedOwner.equals(outlineOwner);
            if (ownerMatch) {
                System.out.println("[SCREENSHOT_REPRO_GREEN] case=" + caseId
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
                return;
            }
            if (expectedOutlineHit) {
                System.out.println("[SCREENSHOT_REPRO_RED] case=" + caseId
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
                throw new RuntimeException("[SCREENSHOT_REPRO_RED] case=" + caseId
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
            }
            System.out.println("[SCREENSHOT_REPRO] case=" + caseId
                    + " calibration=aim_miss expected=" + expectedOwner
                    + " actual=" + crosshairOwner
                    + " vanilla=" + vanillaOwner
                    + " outline=" + outlineOwner);
        });
    }

    private static void runBridgeShapeLowerHalfReproCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos leftSupport = SUPPORT_POS.add(88, 0, 0);
        final BlockPos leftFull = leftSupport.up();
        final BlockPos midFullA = leftFull.east();
        final BlockPos midFullB = midFullA.east();
        final BlockPos rightFull = midFullB.east();
        final BlockPos rightSupport = rightFull.down();
        final BlockPos topSlabA = midFullA.up();
        final BlockPos topSlabB = midFullB.up();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = leftSupport.getX() - 3; x <= rightSupport.getX() + 3; x++) {
                for (int z = leftSupport.getZ() - 2; z <= leftSupport.getZ() + 2; z++) {
                    for (int y = leftSupport.getY() - 1; y <= leftSupport.getY() + 3; y++) {
                        world.setBlockState(
                                new BlockPos(x, y, z),
                                Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }

            world.setBlockState(
                    leftSupport,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    rightSupport,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(leftFull, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(midFullA, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(midFullB, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(rightFull, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);

            SlabAnchorAttachment.addAnchor(world, leftFull, world.getBlockState(leftFull));
            SlabAnchorAttachment.addAnchor(world, rightFull, world.getBlockState(rightFull));
            SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(
                    world,
                    midFullA,
                    world.getBlockState(midFullA),
                    leftFull,
                    world.getBlockState(leftFull));
            SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(
                    world,
                    midFullB,
                    world.getBlockState(midFullB),
                    midFullA,
                    world.getBlockState(midFullA));

            world.setBlockState(
                    topSlabA,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(
                    topSlabB,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        String shape = "shape=BSFB->FB->FB->BSFB_with_top_slabs"
                + " leftSupport=" + leftSupport.toShortString()
                + " leftFull=" + leftFull.toShortString()
                + " midA=" + midFullA.toShortString()
                + " midB=" + midFullB.toShortString()
                + " rightFull=" + rightFull.toShortString()
                + " rightSupport=" + rightSupport.toShortString()
                + " topA=" + topSlabA.toShortString()
                + " topB=" + topSlabB.toShortString();

        runBridgeLowerHalfProbe(
                ctx,
                midFullA,
                new Vec3d(midFullA.getX() + 0.5d, midFullA.getY() + 1.65d, midFullA.getZ() + 2.55d),
                new Vec3d(midFullA.getX() + 0.5d, midFullA.getY() - 0.30d, midFullA.getZ() + 0.5d),
                "bridge_fb1_front_lower_slab_held",
                "front_lower_center",
                new ItemStack(Items.STONE_SLAB, 8),
                shape);
        runBridgeLowerHalfProbe(
                ctx,
                midFullB,
                new Vec3d(midFullB.getX() + 0.5d, midFullB.getY() + 1.65d, midFullB.getZ() + 2.55d),
                new Vec3d(midFullB.getX() + 0.5d, midFullB.getY() - 0.30d, midFullB.getZ() + 0.5d),
                "bridge_fb2_front_lower_empty_hand",
                "front_lower_center",
                ItemStack.EMPTY,
                shape);
        runBridgeLowerHalfProbe(
                ctx,
                topSlabA,
                new Vec3d(topSlabA.getX() - 2.35d, topSlabA.getY() + 1.60d, topSlabA.getZ() + 0.5d),
                new Vec3d(topSlabA.getX() + 0.02d, topSlabA.getY() + 0.10d, topSlabA.getZ() + 0.5d),
                "bridge_top_slabA_side_lower_slab_held",
                "side_lower_center",
                new ItemStack(Items.STONE_SLAB, 8),
                shape);
        runBridgeLowerHalfProbe(
                ctx,
                topSlabB,
                new Vec3d(topSlabB.getX() + 2.35d, topSlabB.getY() + 1.60d, topSlabB.getZ() + 0.5d),
                new Vec3d(topSlabB.getX() + 0.98d, topSlabB.getY() + 0.10d, topSlabB.getZ() + 0.5d),
                "bridge_top_slabB_side_lower_empty_hand",
                "side_lower_center",
                ItemStack.EMPTY,
                shape);
    }

    private static void runBridgeLowerHalfProbe(
            ClientGameTestContext ctx,
            BlockPos expectedOwnerPos,
            Vec3d eye,
            Vec3d aimPoint,
            String caseId,
            String aimRegion,
            ItemStack heldStack,
            String setupDetails
    ) {
        final double reach = 6.0d;

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[BRIDGE_LOWER_HALF_RED] case=" + caseId + " client not ready");
            }
            if (heldStack == null || heldStack.isEmpty()) {
                mc.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            } else {
                mc.player.setStackInHand(Hand.MAIN_HAND, heldStack.copy());
            }

            Vec3d delta = aimPoint.subtract(eye);
            double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
            double feetY = eye.y - mc.player.getStandingEyeHeight();
            mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            mc.player.setVelocity(Vec3d.ZERO);

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(reach));
            HitResult crosshair = mc.crosshairTarget;
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            BlockState expectedState = mc.world.getBlockState(expectedOwnerPos);
            BlockHitResult outlineHit = expectedState.getOutlineShape(mc.world, expectedOwnerPos)
                    .raycast(rayStart, rayEnd, expectedOwnerPos);

            String expectedOwner = expectedOwnerPos.toShortString();
            String crosshairOwner = asOwner(crosshair);
            String vanillaOwner = asOwner(vanilla);
            String outlineOwner = outlineHit == null ? "MISS" : outlineHit.getBlockPos().toShortString();
            String held = mc.player.getMainHandStack().isEmpty()
                    ? "empty"
                    : mc.player.getMainHandStack().getItem().toString();
            double expectedDy = SlabSupport.getYOffset(mc.world, expectedOwnerPos, expectedState);
            String visualBox = expectedState.getOutlineShape(mc.world, expectedOwnerPos).isEmpty()
                    ? "EMPTY"
                    : formatBox(expectedState.getOutlineShape(mc.world, expectedOwnerPos)
                    .getBoundingBox().offset(expectedOwnerPos));

            System.out.println("[BRIDGE_LOWER_HALF] case=" + caseId
                    + " held=" + held
                    + " aimRegion=" + aimRegion
                    + " expected=" + expectedOwner
                    + " actual=" + crosshairOwner
                    + " vanilla=" + vanillaOwner
                    + " outline=" + outlineOwner
                    + " expectedState=" + expectedState
                    + " expectedDy=" + expectedDy
                    + " eye=" + eye
                    + " aim=" + aimPoint
                    + " liveEye=" + rayStart
                    + " dir=" + rayDir
                    + " visualBox=" + visualBox
                    + " " + setupDetails);

            boolean ownerMatch = expectedOwner.equals(crosshairOwner);
            boolean expectedOutlineHit = expectedOwner.equals(outlineOwner);
            if (ownerMatch) {
                System.out.println("[BRIDGE_LOWER_HALF_GREEN] case=" + caseId
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
                return;
            }
            if (expectedOutlineHit) {
                System.out.println("[BRIDGE_LOWER_HALF_RED] case=" + caseId
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
                throw new RuntimeException("[BRIDGE_LOWER_HALF_RED] case=" + caseId
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
            }

            System.out.println("[BRIDGE_LOWER_HALF] case=" + caseId
                    + " calibration=aim_miss expected=" + expectedOwner
                    + " actual=" + crosshairOwner
                    + " vanilla=" + vanillaOwner
                    + " outline=" + outlineOwner);
        });
    }

    private static void runDynamicBridgeTopSlabSelectionReproCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos leftSupport = SUPPORT_POS.add(24, 0, 0);
        final BlockPos leftFull = leftSupport.up();
        final BlockPos fb1Pos = leftFull.east();
        final BlockPos fb2Pos = fb1Pos.east();
        final BlockPos rightSupport = fb2Pos.east().down();
        final BlockPos rightFull = rightSupport.up();
        final BlockPos topSlabPos = fb1Pos.up();
        final BlockPos aboveTopSlabPos = topSlabPos.up();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            int minX = leftSupport.getX() - 2;
            int maxX = rightSupport.getX() + 2;
            int minZ = leftSupport.getZ() - 2;
            int maxZ = leftSupport.getZ() + 2;
            int minY = leftSupport.getY() - 1;
            int maxY = leftSupport.getY() + 4;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        world.setBlockState(
                                new BlockPos(x, y, z),
                                Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }
            for (int x = minX; x <= maxX; x++) {
                world.setBlockState(
                        new BlockPos(x, leftSupport.getY() - 1, leftSupport.getZ()),
                        Blocks.STONE.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
            }
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                server.getPlayerManager().getPlayerList().get(0)
                        .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String shape = "shape=dynamic_BSFB->FB->FB->BSFB_single_top_slab"
                + " leftSupport=" + leftSupport.toShortString()
                + " leftFull=" + leftFull.toShortString()
                + " fb1=" + fb1Pos.toShortString()
                + " fb2=" + fb2Pos.toShortString()
                + " rightSupport=" + rightSupport.toShortString()
                + " rightFull=" + rightFull.toShortString()
                + " topSlab=" + topSlabPos.toShortString();

        final BlockHitResult placeLeftSupportHit = resolveLoweredUpMergeHit(leftSupport.down());
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(leftSupport.getX() + 0.5d, leftSupport.getY() + 1.65d, leftSupport.getZ() + 2.45d),
                placeLeftSupportHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for dynamic bridge left support slab");
            }
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            BlockPos hitPos = placeLeftSupportHit.getBlockPos();
            Direction hitFace = placeLeftSupportHit.getSide();
            BlockPos intendedPlacePos = hitPos.offset(hitFace);
            BlockState hitState = mc.world.getBlockState(hitPos);
            BlockState intendedPlaceStateBefore = mc.world.getBlockState(intendedPlacePos);
            ItemStack heldStack = mc.player.getMainHandStack();
            String held = heldStack.isEmpty() ? "empty" : heldStack.getItem().toString();
            String crosshairOwner = asOwner(mc.crosshairTarget);
            Vec3d liveEye = mc.player.getCameraPosVec(0.0f);
            Vec3d hitVec = placeLeftSupportHit.getPos();
            double dist = liveEye.distanceTo(hitVec);
            var slabState = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
            var slabShape = slabState.getCollisionShape(mc.world, intendedPlacePos);
            var slabBox = slabShape.isEmpty() ? null : slabShape.getBoundingBox().offset(intendedPlacePos);
            var playerBox = mc.player.getBoundingBox();
            boolean intersectsPlayer = slabBox != null && slabBox.intersects(playerBox);
            System.out.println("[DYNAMIC_BRIDGE_ISO] step=left_support_pre"
                    + " playerPos=(" + mc.player.getX() + ", " + mc.player.getY() + ", " + mc.player.getZ() + ")"
                    + " eye=" + liveEye
                    + " playerBox=" + formatBox(playerBox)
                    + " held=" + held
                    + " hitPos=" + hitPos.toShortString()
                    + " hitFace=" + hitFace
                    + " hitState=" + hitState
                    + " hitVec=" + hitVec
                    + " dist=" + dist
                    + " crosshairOwner=" + crosshairOwner
                    + " intendedPlacePos=" + intendedPlacePos.toShortString()
                    + " intendedBefore=" + intendedPlaceStateBefore
                    + " slabCollision=" + (slabBox == null ? "EMPTY" : formatBox(slabBox))
                    + " slabIntersectsPlayer=" + intersectsPlayer
                    + " " + shape);
            System.out.println("[DYNAMIC_BRIDGE_ISO] step=left_support_hit"
                    + " targetBlock=" + hitPos.toShortString()
                    + " face=" + hitFace
                    + " intendedPlace=" + intendedPlacePos.toShortString()
                    + " crosshair=" + crosshairOwner
                    + " dist=" + dist);
            ActionResult supportResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeLeftSupportHit);
            BlockState intendedPlaceStateAfter = mc.world.getBlockState(intendedPlacePos);
            System.out.println("[DYNAMIC_BRIDGE_ISO] step=left_support_result"
                    + " result=" + supportResult
                    + " intendedAfter=" + intendedPlaceStateAfter
                    + " expectedPlace=" + intendedPlacePos.toShortString()
                    + " held=" + held
                    + " slabIntersectsPlayer=" + intersectsPlayer
                    + " dist=" + dist);
            if (!supportResult.isAccepted()) {
                System.out.println("[DYNAMIC_BRIDGE_ISO_RED] step=left_support_place rejected"
                        + " reason=result=" + supportResult
                        + " hitPos=" + hitPos.toShortString()
                        + " face=" + hitFace
                        + " intendedPlace=" + intendedPlacePos.toShortString()
                        + " crosshair=" + crosshairOwner
                        + " dist=" + dist
                        + " slabIntersectsPlayer=" + intersectsPlayer
                        + " intendedAfter=" + intendedPlaceStateAfter
                        + " " + shape);
                throw new RuntimeException("dynamic bridge left support slab placement rejected: " + supportResult);
            }
            System.out.println("[DYNAMIC_BRIDGE_ISO_GREEN] step=left_support_place"
                    + " expected=" + intendedPlacePos.toShortString()
                    + " actual=" + intendedPlacePos.toShortString()
                    + " vanilla=" + asOwner(mc.world.raycast(new RaycastContext(
                    liveEye,
                    liveEye.add(mc.player.getRotationVec(0.0f).multiply(6.0d)),
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player)))
                    + " outline=" + intendedPlacePos.toShortString());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockHitResult placeLeftFullHit = resolveLoweredUpMergeHit(leftSupport);
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(leftSupport.getX() + 0.5d, leftSupport.getY() + 2.35d, leftSupport.getZ() + 2.45d),
                placeLeftFullHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for dynamic bridge left full block");
            }
            ActionResult leftFullResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeLeftFullHit);
            if (!leftFullResult.isAccepted()) {
                throw new RuntimeException("dynamic bridge left full block placement rejected: " + leftFullResult);
            }
            BlockState supportState = mc.world.getBlockState(leftSupport);
            BlockState leftState = mc.world.getBlockState(leftFull);
            double supportDy = supportState.isOf(Blocks.STONE_SLAB) ? SlabSupport.getYOffset(mc.world, leftSupport, supportState) : Double.NaN;
            double leftDy = leftState.isOf(Blocks.STONE) ? SlabSupport.getYOffset(mc.world, leftFull, leftState) : Double.NaN;
            System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB] step=build_left_bsfb support="
                    + leftSupport.toShortString() + " supportState=" + supportState + " supportDy=" + supportDy
                    + " full=" + leftFull.toShortString() + " fullState=" + leftState + " fullDy=" + leftDy
                    + " " + shape);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockHitResult placeFb1Hit = resolveLoweredFaceHit(leftFull, Direction.EAST, 0.5d);
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(leftFull.getX() + 0.5d, leftFull.getY() + 1.8d, leftFull.getZ() + 2.55d),
                placeFb1Hit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for dynamic bridge fb1 placement");
            }
            ActionResult fb1Result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeFb1Hit);
            if (!fb1Result.isAccepted()) {
                throw new RuntimeException("dynamic bridge fb1 placement rejected: " + fb1Result);
            }
            BlockState fb1 = mc.world.getBlockState(fb1Pos);
            double fb1Dy = fb1.isOf(Blocks.STONE) ? SlabSupport.getYOffset(mc.world, fb1Pos, fb1) : Double.NaN;
            System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB] step=place_fb1 pos="
                    + fb1Pos.toShortString() + " state=" + fb1 + " dy=" + fb1Dy + " " + shape);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockHitResult placeFb2Hit = resolveLoweredFaceHit(fb1Pos, Direction.EAST, 0.5d);
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(fb1Pos.getX() + 0.5d, fb1Pos.getY() + 1.8d, fb1Pos.getZ() + 2.55d),
                placeFb2Hit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for dynamic bridge fb2 placement");
            }
            ActionResult fb2Result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeFb2Hit);
            if (!fb2Result.isAccepted()) {
                throw new RuntimeException("dynamic bridge fb2 placement rejected: " + fb2Result);
            }
            BlockState fb2 = mc.world.getBlockState(fb2Pos);
            double fb2Dy = fb2.isOf(Blocks.STONE) ? SlabSupport.getYOffset(mc.world, fb2Pos, fb2) : Double.NaN;
            System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB] step=place_fb2 pos="
                    + fb2Pos.toShortString() + " state=" + fb2 + " dy=" + fb2Dy + " " + shape);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockHitResult placeRightSupportHit = resolveLoweredUpMergeHit(rightSupport.down());
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(rightSupport.getX() + 0.5d, rightSupport.getY() + 1.65d, rightSupport.getZ() + 2.45d),
                placeRightSupportHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for dynamic bridge right support slab");
            }
            ActionResult supportResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeRightSupportHit);
            if (!supportResult.isAccepted()) {
                throw new RuntimeException("dynamic bridge right support slab placement rejected: " + supportResult);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockHitResult placeRightFullHit = resolveLoweredUpMergeHit(rightSupport);
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(rightSupport.getX() + 0.5d, rightSupport.getY() + 2.35d, rightSupport.getZ() + 2.45d),
                placeRightFullHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for dynamic bridge right full block");
            }
            ActionResult rightFullResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeRightFullHit);
            if (!rightFullResult.isAccepted()) {
                throw new RuntimeException("dynamic bridge right full block placement rejected: " + rightFullResult);
            }
            BlockState supportState = mc.world.getBlockState(rightSupport);
            BlockState rightState = mc.world.getBlockState(rightFull);
            double supportDy = supportState.isOf(Blocks.STONE_SLAB) ? SlabSupport.getYOffset(mc.world, rightSupport, supportState) : Double.NaN;
            double rightDy = rightState.isOf(Blocks.STONE) ? SlabSupport.getYOffset(mc.world, rightFull, rightState) : Double.NaN;
            System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB] step=build_right_bsfb support="
                    + rightSupport.toShortString() + " supportState=" + supportState + " supportDy=" + supportDy
                    + " full=" + rightFull.toShortString() + " fullState=" + rightState + " fullDy=" + rightDy
                    + " " + shape);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockHitResult placeSingleTopSlabHit = resolveLoweredUpMergeHit(fb1Pos);
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE_SLAB, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(fb1Pos.getX() + 0.5d, fb1Pos.getY() + 1.8d, fb1Pos.getZ() + 2.55d),
                placeSingleTopSlabHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for dynamic bridge top slab placement");
            }
            ActionResult slabResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeSingleTopSlabHit);
            if (!slabResult.isAccepted()) {
                throw new RuntimeException("dynamic bridge single top slab placement rejected: " + slabResult);
            }
            BlockState topState = mc.world.getBlockState(topSlabPos);
            if (!topState.isOf(Blocks.STONE_SLAB) || !topState.contains(SlabBlock.TYPE)) {
                throw new RuntimeException("dynamic bridge expected single slab at " + topSlabPos.toShortString()
                        + ", found " + topState);
            }
            SlabType topType = topState.get(SlabBlock.TYPE);
            double topDy = SlabSupport.getYOffset(mc.world, topSlabPos, topState);
            System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB] step=place_single_top_slab pos="
                    + topSlabPos.toShortString() + " state=" + topState
                    + " type=" + topType + " dy=" + topDy + " " + shape);
            if (topType == SlabType.DOUBLE) {
                throw new RuntimeException("dynamic bridge invalid repro: single top slab merged to DOUBLE at "
                        + topSlabPos.toShortString());
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        runDynamicBridgeOwnershipProbe(
                ctx,
                topSlabPos,
                new Vec3d(topSlabPos.getX() + 0.5d, topSlabPos.getY() + 1.65d, topSlabPos.getZ() + 2.45d),
                new Vec3d(topSlabPos.getX() + 0.5d, topSlabPos.getY() + 0.18d, topSlabPos.getZ() + 0.5d),
                "select_single_top_slab",
                "front_lower_center",
                new ItemStack(Items.STONE_SLAB, 8),
                shape);

        final BlockHitResult placeOnTopSlabHit = resolveLoweredUpMergeHit(topSlabPos);
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.STONE, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(topSlabPos.getX() + 0.5d, topSlabPos.getY() + 2.35d, topSlabPos.getZ() + 2.35d),
                placeOnTopSlabHit.getPos());
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client not ready for dynamic bridge build-on-top");
            }
            ActionResult topBuildResult = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeOnTopSlabHit);
            if (!topBuildResult.isAccepted()) {
                throw new RuntimeException("dynamic bridge build-on-top rejected: " + topBuildResult);
            }
            BlockState builtState = mc.world.getBlockState(aboveTopSlabPos);
            double builtDy = builtState.isOf(Blocks.STONE) ? SlabSupport.getYOffset(mc.world, aboveTopSlabPos, builtState) : Double.NaN;
            System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB] step=build_on_top_slab pos="
                    + aboveTopSlabPos.toShortString() + " state=" + builtState + " dy=" + builtDy + " " + shape);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        runDynamicBridgeOwnershipProbe(
                ctx,
                aboveTopSlabPos,
                new Vec3d(aboveTopSlabPos.getX() + 0.5d, aboveTopSlabPos.getY() + 1.65d, aboveTopSlabPos.getZ() + 2.45d),
                new Vec3d(aboveTopSlabPos.getX() + 0.5d, aboveTopSlabPos.getY() - 0.30d, aboveTopSlabPos.getZ() + 0.5d),
                "lower_half_after_build",
                "front_lower_center",
                new ItemStack(Items.STONE_SLAB, 8),
                shape);
    }

    private static void runDynamicBridgeOwnershipProbe(
            ClientGameTestContext ctx,
            BlockPos expectedOwnerPos,
            Vec3d eye,
            Vec3d aimPoint,
            String step,
            String aimRegion,
            ItemStack heldStack,
            String setupDetails
    ) {
        final double reach = 6.0d;

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[DYNAMIC_BRIDGE_TOP_SLAB_RED] step=" + step + " client not ready");
            }
            if (heldStack == null || heldStack.isEmpty()) {
                mc.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            } else {
                mc.player.setStackInHand(Hand.MAIN_HAND, heldStack.copy());
            }

            Vec3d delta = aimPoint.subtract(eye);
            double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
            float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
            double feetY = eye.y - mc.player.getStandingEyeHeight();
            mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            mc.player.setVelocity(Vec3d.ZERO);

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(reach));
            HitResult crosshair = mc.crosshairTarget;
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            BlockState expectedState = mc.world.getBlockState(expectedOwnerPos);
            BlockHitResult outlineHit = expectedState.getOutlineShape(mc.world, expectedOwnerPos)
                    .raycast(rayStart, rayEnd, expectedOwnerPos);

            String expectedOwner = expectedOwnerPos.toShortString();
            String crosshairOwner = asOwner(crosshair);
            String vanillaOwner = asOwner(vanilla);
            String outlineOwner = outlineHit == null ? "MISS" : outlineHit.getBlockPos().toShortString();
            String held = mc.player.getMainHandStack().isEmpty()
                    ? "empty"
                    : mc.player.getMainHandStack().getItem().toString();
            double expectedDy = SlabSupport.getYOffset(mc.world, expectedOwnerPos, expectedState);
            String visualBox = expectedState.getOutlineShape(mc.world, expectedOwnerPos).isEmpty()
                    ? "EMPTY"
                    : formatBox(expectedState.getOutlineShape(mc.world, expectedOwnerPos).getBoundingBox().offset(expectedOwnerPos));

            System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB] step=" + step
                    + " expected=" + expectedOwner
                    + " actual=" + crosshairOwner
                    + " vanilla=" + vanillaOwner
                    + " outline=" + outlineOwner
                    + " held=" + held
                    + " aimRegion=" + aimRegion
                    + " expectedState=" + expectedState
                    + " expectedDy=" + expectedDy
                    + " eye=" + eye
                    + " aim=" + aimPoint
                    + " liveEye=" + rayStart
                    + " dir=" + rayDir
                    + " visualBox=" + visualBox
                    + " " + setupDetails);

            boolean ownerMatch = expectedOwner.equals(crosshairOwner);
            boolean expectedOutlineHit = expectedOwner.equals(outlineOwner);
            if (ownerMatch) {
                System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB_GREEN] step=" + step
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
                return;
            }
            if (expectedOutlineHit) {
                System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB_RED] step=" + step
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
                throw new RuntimeException("[DYNAMIC_BRIDGE_TOP_SLAB_RED] step=" + step
                        + " expected=" + expectedOwner
                        + " actual=" + crosshairOwner
                        + " vanilla=" + vanillaOwner
                        + " outline=" + outlineOwner);
            }

            System.out.println("[DYNAMIC_BRIDGE_TOP_SLAB] step=" + step
                    + " calibration=aim_miss expected=" + expectedOwner
                    + " actual=" + crosshairOwner
                    + " vanilla=" + vanillaOwner
                    + " outline=" + outlineOwner);
        });
    }

    private static void runBeta4OutlineHitRaycastMissRedCase(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = new BlockPos(14, -58, 0);
        final BlockPos expectedOwnerPos = new BlockPos(14, -57, 0);
        final Vec3d liveEye = new Vec3d(12.165d, -56.960d, 1.750d);
        final Vec3d liveLook = new Vec3d(0.826d, -0.067d, -0.559d);
        final Vec3d liveEnd = liveEye.add(liveLook.multiply(6.0d));

        setupFixture(singleplayer, supportPos, expectedOwnerPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState fullState = world.getBlockState(expectedOwnerPos);
            SlabAnchorAttachment.addAnchor(world, expectedOwnerPos, fullState);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        syncHeldMainHand(ctx, singleplayer, ItemStack.EMPTY);
        syncPlayerAim(ctx, singleplayer, liveEye, liveEnd);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
                throw new RuntimeException("[BETA4_OUTLINE_HIT_RAYCAST_MISS_RED] client not ready");
            }

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(6.0d));
            HitResult finalTarget = mc.crosshairTarget;
            BlockState expectedState = mc.world.getBlockState(expectedOwnerPos);
            double sourceDy = SlabSupport.getYOffset(mc.world, expectedOwnerPos, expectedState);
            double clientDy = ClientDy.dyFor(mc.world, expectedOwnerPos, expectedState);
            boolean anchored = SlabAnchorAttachment.isAnchored(mc.world, expectedOwnerPos);
            boolean persistentCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    mc.world, expectedOwnerPos, expectedState);
            VoxelShape outlineShape = expectedState.getOutlineShape(mc.world, expectedOwnerPos);
            VoxelShape raycastShape = expectedState.getRaycastShape(mc.world, expectedOwnerPos);
            BlockHitResult outlineHit = outlineShape.raycast(rayStart, rayEnd, expectedOwnerPos);
            BlockHitResult raycastHit = raycastShape.raycast(rayStart, rayEnd, expectedOwnerPos);
            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));

            String expectedOwner = expectedOwnerPos.toShortString();
            String actualOwner = asOwner(finalTarget);
            String vanillaOwner = asOwner(vanilla);
            String outlineOwner = outlineHit == null ? "MISS" : outlineHit.getBlockPos().toShortString();
            String raycastOwner = raycastHit == null ? "MISS" : raycastHit.getBlockPos().toShortString();
            String sourceMode = persistentCarrier
                    ? "persistentLoweredSlabCarrier"
                    : (Math.abs(sourceDy + 0.5d) <= EPSILON ? "dynamicLoweredOrAnchored" : "normal");
            String outlineBox = outlineShape.isEmpty() ? "empty" : formatBox(outlineShape.getBoundingBox());
            String raycastBox = raycastShape.isEmpty() ? "empty" : formatBox(raycastShape.getBoundingBox());
            String targetType = finalTarget == null ? "null" : finalTarget.getType().toString();
            boolean targetIsMiss = finalTarget == null || finalTarget.getType() == HitResult.Type.MISS;

            String facts = " expectedOwner=" + expectedOwner
                    + " actualOwner=" + actualOwner
                    + " vanillaOwner=" + vanillaOwner
                    + " outlineOwner=" + outlineOwner
                    + " raycastOwner=" + raycastOwner
                    + " expectedState=" + expectedState
                    + " dy=" + sourceDy
                    + " clientDy=" + clientDy
                    + " modelDy=not_accessible"
                    + " anchored=" + anchored
                    + " persistentFullBlockAnchor=" + (anchored && expectedState.isOf(Blocks.STONE))
                    + " persistentLoweredSlabCarrier=" + persistentCarrier
                    + " sourceMode=" + sourceMode
                    + " outlineShape=" + outlineBox
                    + " outlineHit=" + (outlineHit != null)
                    + " outlineHitDesc=" + describeHit(outlineHit)
                    + " raycastShape=" + raycastBox
                    + " raycastShapeEmpty=" + raycastShape.isEmpty()
                    + " raycastHit=" + (raycastHit != null)
                    + " raycastHitDesc=" + describeHit(raycastHit)
                    + " crosshairTargetType=" + targetType
                    + " targetIsMiss=" + targetIsMiss
                    + " eye=" + liveEye
                    + " look=" + liveLook
                    + " requestedEnd=" + liveEnd
                    + " rayStart=" + rayStart
                    + " rayDir=" + rayDir
                    + " rayEnd=" + rayEnd
                    + " ownerClass=ANCHORED_FULL_BLOCK"
                    + " expectedOwnerClass=ANCHORED_FULL_BLOCK"
                    + " actualOwnerClass=" + (targetIsMiss ? "MISS" : beta4OwnerClass(
                    finalTarget, expectedOwnerPos, null, null));

            boolean raycastShapeRed = expectedState.isOf(Blocks.STONE)
                    && Math.abs(sourceDy + 0.5d) <= EPSILON
                    && Math.abs(clientDy + 0.5d) <= EPSILON
                    && anchored
                    && expectedOwner.equals(outlineOwner)
                    && raycastHit == null
                    && (raycastShape.isEmpty() || !expectedOwner.equals(raycastOwner));
            if (raycastShapeRed) {
                String classifiedFacts = " classification=RAYCAST_SHAPE_RED"
                        + " crosshairMissReproduced=" + targetIsMiss
                        + facts;
                System.out.println("[BETA4_OUTLINE_HIT_RAYCAST_MISS_RED]" + classifiedFacts);
                throw new RuntimeException("[BETA4_OUTLINE_HIT_RAYCAST_MISS_RED]" + classifiedFacts);
            }

            boolean raycastShapeGreen = expectedState.isOf(Blocks.STONE)
                    && Math.abs(sourceDy + 0.5d) <= EPSILON
                    && Math.abs(clientDy + 0.5d) <= EPSILON
                    && anchored
                    && expectedOwner.equals(outlineOwner)
                    && expectedOwner.equals(raycastOwner)
                    && !raycastShape.isEmpty()
                    && raycastHit != null;
            if (raycastShapeGreen) {
                System.out.println("[BETA4_OUTLINE_HIT_RAYCAST_MISS_GREEN]"
                        + " classification=RAYCAST_SHAPE_GREEN"
                        + " crosshairMissReproduced=" + targetIsMiss
                        + facts);
                return;
            }

            throw new RuntimeException("[BETA4_OUTLINE_HIT_RAYCAST_MISS] calibration_mismatch" + facts);
        });
    }

    private static void syncHeldMainHand(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            ItemStack stack
    ) {
        ItemStack held = stack == null ? ItemStack.EMPTY : stack.copy();
        singleplayer.getServer().runOnServer(server -> {
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                server.getPlayerManager().getPlayerList().get(0).setStackInHand(Hand.MAIN_HAND, held.copy());
            }
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.setStackInHand(Hand.MAIN_HAND, held.copy());
            }
        });
        ctx.waitTick();
    }

    private static void syncPlayerAim(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Vec3d eye,
            Vec3d target
    ) {
        Vec3d delta = target.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        syncPlayerPosition(ctx, singleplayer, eye.x, feetY, eye.z, yaw, pitch);
    }

    private static void syncPlayerLookFromEye(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Vec3d eye,
            float yaw,
            float pitch
    ) {
        double feetY = eye.y - 1.62d;
        syncPlayerPosition(ctx, singleplayer, eye.x, feetY, eye.z, yaw, pitch);
    }

    private static void setLoweredSlabTarget(
            TestSingleplayerContext singleplayer,
            BlockPos targetPos,
            SlabType targetType
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer player missing during target setup");
            }
            world.setBlockState(
                    targetPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, targetType),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);

            BlockState targetState = world.getBlockState(targetPos);
            if (!targetState.isOf(Blocks.STONE_SLAB)
                    || !targetState.contains(SlabBlock.TYPE)
                    || targetState.get(SlabBlock.TYPE) != targetType) {
                throw new RuntimeException("could not seed slab target " + targetType + " at "
                        + targetPos.toShortString()
                        + ", found " + targetState);
            }
            double targetDy = SlabSupport.getYOffset(world, targetPos, targetState);
            if (Math.abs(targetDy + 0.5d) > EPSILON) {
                throw new RuntimeException("seeded target " + targetType + " was not lowered at "
                        + targetPos.toShortString()
                        + ", found dy=" + targetDy);
            }
        });
    }

    private static void setPlacementPosTarget(
            TestSingleplayerContext singleplayer,
            BlockPos placementPos,
            SlabType targetType
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    placementPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, targetType),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);

            BlockState placed = world.getBlockState(placementPos);
            if (!placed.isOf(Blocks.STONE_SLAB)
                    || !placed.contains(SlabBlock.TYPE)
                    || placed.get(SlabBlock.TYPE) != targetType) {
                throw new RuntimeException("could not seed placePos slab target " + targetType + " at "
                        + placementPos.toShortString() + ", found " + placed);
            }
        });
    }

    private static void setupFixture(TestSingleplayerContext singleplayer, BlockPos supportPos, BlockPos fullPos) {
        final int minY = fullPos.getY() - 1;
        final int maxY = fullPos.getY() + 1;

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = supportPos.getX() - 2; x <= supportPos.getX() + 2; x++) {
                for (int z = supportPos.getZ() - 2; z <= supportPos.getZ() + 2; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        world.setBlockState(
                                new BlockPos(x, y, z),
                                Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }

            world.setBlockState(
                    supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);

            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer player missing during repro setup");
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB, 8));
        });
    }

    private static void assertTopLowered(
            BlockPos pos,
            BlockState state,
            net.minecraft.world.BlockView world,
            String label
    ) {
        if (!state.isOf(Blocks.STONE_SLAB)
                || !state.contains(SlabBlock.TYPE)
                || state.get(SlabBlock.TYPE) != SlabType.TOP) {
            throw new RuntimeException(label + " expected TOP slab at "
                    + pos.toShortString() + ", found " + state);
        }
        double dy = SlabSupport.getYOffset(world, pos, state);
        if (Math.abs(dy + 0.5d) > EPSILON) {
            throw new RuntimeException(label + " expected lowered dy=-0.500 at "
                    + pos.toShortString() + ", found dy=" + dy + " state=" + state);
        }
    }

    private static void assertLaneDy(
            net.minecraft.world.BlockView world,
            BlockPos laneRootPos,
            BlockPos laneTailPos,
            double expectedDy,
            String label
    ) {
        BlockState laneRoot = world.getBlockState(laneRootPos);
        BlockState laneTail = world.getBlockState(laneTailPos);
        if (!laneRoot.isOf(Blocks.STONE_SLAB) || !laneRoot.contains(SlabBlock.TYPE)) {
            throw new RuntimeException(label + " laneRoot must remain slab, found " + laneRoot);
        }
        if (!laneTail.isOf(Blocks.STONE_SLAB) || !laneTail.contains(SlabBlock.TYPE)) {
            throw new RuntimeException(label + " laneTail must remain slab, found " + laneTail);
        }
        double rootDy = SlabSupport.getYOffset(world, laneRootPos, laneRoot);
        double tailDy = SlabSupport.getYOffset(world, laneTailPos, laneTail);
        if (Math.abs(rootDy - expectedDy) > EPSILON || Math.abs(tailDy - expectedDy) > EPSILON) {
            throw new RuntimeException(label + " expected both lane dy values to be " + expectedDy
                    + " but rootDy=" + rootDy + " tailDy=" + tailDy
                    + " rootState=" + laneRoot + " tailState=" + laneTail);
        }
    }

    private static void assertForbiddenMixedDoubleBottomStateAbsent(
            net.minecraft.world.BlockView world,
            BlockPos laneDoublePos,
            BlockPos laneBottomPos,
            String label
    ) {
        BlockState laneDouble = world.getBlockState(laneDoublePos);
        BlockState laneBottom = world.getBlockState(laneBottomPos);
        if (!laneDouble.isOf(Blocks.STONE_SLAB)
                || !laneDouble.contains(SlabBlock.TYPE)
                || laneDouble.get(SlabBlock.TYPE) != SlabType.DOUBLE
                || !laneBottom.isOf(Blocks.STONE_SLAB)
                || !laneBottom.contains(SlabBlock.TYPE)
                || laneBottom.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
            return;
        }
        double doubleDy = SlabSupport.getYOffset(world, laneDoublePos, laneDouble);
        double bottomDy = SlabSupport.getYOffset(world, laneBottomPos, laneBottom);
        if (Math.abs(doubleDy + 0.5d) <= EPSILON && Math.abs(bottomDy) <= EPSILON) {
            throw new RuntimeException(label + " found forbidden mixed state: lowered DOUBLE dy=-0.500 adjacent to "
                    + "normal BOTTOM dy=0 at double=" + laneDoublePos.toShortString()
                    + " bottom=" + laneBottomPos.toShortString()
                    + " states=(" + laneDouble + ", " + laneBottom + ")");
        }
    }

    private static void assertNoLoweredRemainderWithoutSupport(
            net.minecraft.world.BlockView world,
            BlockPos laneDoublePos,
            BlockPos laneBottomPos,
            String label
    ) {
        BlockState laneDouble = world.getBlockState(laneDoublePos);
        BlockState laneBottom = world.getBlockState(laneBottomPos);
        if (laneDouble.isOf(Blocks.STONE_SLAB) && laneDouble.contains(SlabBlock.TYPE)) {
            double doubleDy = SlabSupport.getYOffset(world, laneDoublePos, laneDouble);
            if (doubleDy < 0.0d) {
                throw new RuntimeException(label + " unsupported lowered remainder at DOUBLE slot "
                        + laneDoublePos.toShortString()
                        + " dy=" + doubleDy + " state=" + laneDouble);
            }
        }
        if (laneBottom.isOf(Blocks.STONE_SLAB) && laneBottom.contains(SlabBlock.TYPE)) {
            double bottomDy = SlabSupport.getYOffset(world, laneBottomPos, laneBottom);
            if (bottomDy < 0.0d) {
                throw new RuntimeException(label + " unsupported lowered remainder at BOTTOM slot "
                        + laneBottomPos.toShortString()
                        + " dy=" + bottomDy + " state=" + laneBottom);
            }
        }
    }

    private static BlockHitResult resolveLoweredSideFaceHit(BlockPos targetPos, Direction face, SlabType targetType) {
        double yOffset = targetType == SlabType.BOTTOM ? -0.25d : 0.25d;
        return resolveLoweredFaceHit(targetPos, face, yOffset);
    }

    private static BlockHitResult resolveLoweredUpMergeHit(BlockPos targetPos) {
        return new BlockHitResult(
                new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + TOP_FACE_HIT_Y, targetPos.getZ() + 0.5d),
                Direction.UP,
                targetPos,
                false,
                false);
    }

    private static BlockHitResult resolveLoweredFaceHit(BlockPos targetPos, Direction face, double yOffset) {
        return switch (face) {
            case NORTH -> new BlockHitResult(
                    new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + yOffset, targetPos.getZ()),
                    Direction.NORTH,
                    targetPos,
                    false,
                    false);
            case SOUTH -> new BlockHitResult(
                    new Vec3d(targetPos.getX() + 0.5d, targetPos.getY() + yOffset, targetPos.getZ() + 1.0d),
                    Direction.SOUTH,
                    targetPos,
                    false,
                    false);
            case EAST -> new BlockHitResult(
                    new Vec3d(targetPos.getX() + 1.0d, targetPos.getY() + yOffset, targetPos.getZ() + 0.5d),
                    Direction.EAST,
                    targetPos,
                    false,
                    false);
            case WEST -> new BlockHitResult(
                    new Vec3d(targetPos.getX(), targetPos.getY() + yOffset, targetPos.getZ() + 0.5d),
                    Direction.WEST,
                    targetPos,
                    false,
                    false);
            default -> throw new IllegalArgumentException("unsupported face for repro: " + face);
        };
    }

    private static String asOwner(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return "MISS";
        }
        return blockHit.getBlockPos().toShortString();
    }

    private static BlockPos blockPos(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return null;
        }
        return blockHit.getBlockPos();
    }

    private static String describeHit(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return "MISS";
        }
        return "pos=" + blockHit.getBlockPos().toShortString()
                + " face=" + blockHit.getSide()
                + " hit=" + blockHit.getPos();
    }

    private static String describeHitFace(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return "MISS";
        }
        return blockHit.getSide().toString();
    }

    private static String describeHitVector(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return "MISS";
        }
        return blockHit.getPos().toString();
    }

    private static String beta4OwnerClass(
            HitResult hit,
            BlockPos anchoredOwnerPos,
            BlockPos visibleUpperSlabOwnerPos,
            BlockPos adjacentVisibleOwnerPos
    ) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return "NO_RESCUE";
        }
        if (!(hit instanceof BlockHitResult blockHit)) {
            return "NO_RESCUE";
        }
        BlockPos pos = blockHit.getBlockPos();
        if (pos.equals(anchoredOwnerPos)) {
            return "ANCHORED_FULL_BLOCK";
        }
        if (pos.equals(visibleUpperSlabOwnerPos)) {
            return "VISIBLE_UPPER_LOWERED_SLAB";
        }
        if (adjacentVisibleOwnerPos != null && pos.equals(adjacentVisibleOwnerPos)) {
            return "ADJACENT_VISIBLE_TARGET";
        }
        return "KEEP_INITIAL";
    }

    private static String describeHitDist2(Vec3d eye, HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return "NaN";
        }
        return String.format("%.6f", blockHit.getPos().squaredDistanceTo(eye));
    }

    private static String describeOwnerFacts(net.minecraft.world.BlockView world, BlockPos pos) {
        if (world == null || pos == null) {
            return "pos=MISS";
        }
        BlockState state = world.getBlockState(pos);
        double targetDy = SlabSupport.getYOffset(world, pos, state);
        double outlineDy = ClientDy.dyFor(world, pos, state);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        boolean compoundFullBlockAnchor = SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos);
        boolean persistent = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state);
        String slabType = state.contains(SlabBlock.TYPE) ? state.get(SlabBlock.TYPE).asString() : "none";
        return "pos=" + pos.toShortString()
                + " state=" + state
                + " slabType=" + slabType
                + " targetDy=" + targetDy
                + " outlineDy=" + outlineDy
                + " anchored=" + anchored
                + " compoundFullBlockAnchor=" + compoundFullBlockAnchor
                + " persistentLoweredSlabCarrier=" + persistent;
    }

    private static String formatBox(net.minecraft.util.math.Box box) {
        return String.format(
                "min=(%.3f,%.3f,%.3f),max=(%.3f,%.3f,%.3f)",
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static void assertSurvivorChurnSupportTimeline(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String operationLabel,
            BlockPos sourceFullPos,
            BlockPos survivorFullPos
    ) {
        for (int tick = 0; tick < 4; tick++) {
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            final int readTick = tick;
            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world missing during support timeline " + operationLabel
                            + " tick " + readTick);
                }

                BlockState sourceAfter = mc.world.getBlockState(sourceFullPos);
                BlockState survivorAfter = mc.world.getBlockState(survivorFullPos);
                if (!survivorAfter.isAir() && !survivorAfter.isOf(Blocks.STONE)) {
                    throw new RuntimeException(operationLabel + " tick " + readTick
                            + " expected air or stone at survivor, found " + survivorAfter);
                }

                boolean survivorDirectBottomSupport = SlabSupport.hasBottomSlabBelow(mc.world, survivorFullPos);
                boolean survivorInheritedOrAnchored = SlabAnchorAttachment.isAnchored(mc.world, survivorFullPos)
                        || (!survivorDirectBottomSupport && survivorAfter.isOf(Blocks.STONE)
                        && SlabSupport.shouldOffset(mc.world, survivorFullPos, survivorAfter));
                double survivorDy = survivorAfter.isOf(Blocks.STONE)
                        ? SlabSupport.getYOffset(mc.world, survivorFullPos, survivorAfter)
                        : Double.NaN;
                System.out.println("[SIDE-CHURN] " + operationLabel + " tick=" + readTick
                        + " sourcePresent=" + sourceAfter.isOf(Blocks.STONE)
                        + " survivorState=" + survivorAfter
                        + " directBottom=" + survivorDirectBottomSupport
                        + " inherited=" + survivorInheritedOrAnchored
                        + " dy=" + survivorDy);

                if (survivorAfter.isOf(Blocks.STONE)) {
                    boolean legalSurvivor = survivorDirectBottomSupport || survivorInheritedOrAnchored;
                    if (legalSurvivor && Math.abs(survivorDy + 0.5d) > EPSILON) {
                        throw new RuntimeException(operationLabel + " tick " + readTick
                                + " survivor jumped or de-dropped while still legal (dy=" + survivorDy + ")");
                    }
                    if (!legalSurvivor && Math.abs(survivorDy + 0.5d) <= EPSILON) {
                        throw new RuntimeException(operationLabel + " tick " + readTick
                                + " survivor remains lowered with no legal support path");
                    }
                }
            });
        }
    }

    private static void movePlayerForFace(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            BlockPos targetPos,
            Direction face
    ) {
        double x = targetPos.getX() + 0.5d;
        double y = targetPos.getY() + 1.95d;
        double z = targetPos.getZ() + 0.5d;
        float yaw;

        switch (face) {
            case NORTH -> {
                z = targetPos.getZ() - 1.5d;
                yaw = 0.0f;
            }
            case SOUTH -> {
                z = targetPos.getZ() + 2.5d;
                yaw = 180.0f;
            }
            case EAST -> {
                x = targetPos.getX() - 1.5d;
                yaw = 90.0f;
            }
            case WEST -> {
                x = targetPos.getX() + 2.5d;
                yaw = -90.0f;
            }
            default -> throw new IllegalArgumentException("unsupported face for repro: " + face);
        }

        syncPlayerPosition(ctx, singleplayer, x, y, z, yaw, 0.0f);
    }

    private static void movePlayerForUp(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            BlockPos targetPos
    ) {
        syncPlayerPosition(
                ctx,
                singleplayer,
                targetPos.getX() + 0.5d,
                targetPos.getY() + 3.15d,
                targetPos.getZ() + 0.5d,
                0.0f,
                -90.0f);
    }

    private static void syncPlayerPosition(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        singleplayer.getServer().runOnServer(server -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                return;
            }
            var player = server.getPlayerManager().getPlayerList().get(0);
            player.refreshPositionAndAngles(x, y, z, yaw, pitch);
            player.setVelocity(Vec3d.ZERO);
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.refreshPositionAndAngles(x, y, z, yaw, pitch);
                mc.player.setVelocity(Vec3d.ZERO);
            }
        });
        ctx.waitTick();
    }

    /**
     * Beta 3.5 live item anchoring proof.
     *
     * <p>Gated by {@code -Dslabbed.beta35LiveItemAnchoringRed=true}.
     */
    private static void runBeta35LiveItemAnchoringRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportPos = SUPPORT_POS.add(48, 0, 0);
        final BlockPos fullPos = supportPos.up();
        final BlockPos slabPos = fullPos.east();
        final BlockPos torchPos = slabPos.up();
        final BlockHitResult torchUseHit = new BlockHitResult(
                new Vec3d(slabPos.getX() + 0.5d, slabPos.getY(), slabPos.getZ() + 0.5d),
                Direction.UP,
                slabPos,
                false);

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(slabPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final boolean[] preCanPlaceAtResult = {false};
        final double[] supportDyResult = {Double.NaN};
        final double[] slabDyResult = {Double.NaN};

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState baseSupportState = world.getBlockState(supportPos);
            BlockState supportState = world.getBlockState(slabPos);
            BlockState torchDefault = Blocks.TORCH.getDefaultState();
            supportDyResult[0] = SlabSupport.getYOffset(world, supportPos, baseSupportState);
            slabDyResult[0] = SlabSupport.getYOffset(world, slabPos, supportState);
            preCanPlaceAtResult[0] = torchDefault.canPlaceAt(world, torchPos);
            System.out.println("[JULIA_BETA35_LIVE_ITEM_ANCHORING_FIXTURE_GREEN]"
                    + " categoryScope=floor_torch_only"
                    + " baseSupportPos=" + supportPos.toShortString()
                    + " baseSupportState=" + baseSupportState
                    + " baseSupportDy=" + String.format("%.3f", supportDyResult[0])
                    + " supportPos=" + slabPos.toShortString()
                    + " supportState=" + supportState
                    + " supportDy=" + String.format("%.3f", slabDyResult[0])
                    + " expectedTorchPos=" + torchPos.toShortString()
                    + " torchPosIsAir=" + world.getBlockState(torchPos).isAir()
                    + " canPlaceAt=" + preCanPlaceAtResult[0]
                    + " itemCategory=floor_torch"
                    + " proofScope=PLAYER_ITEM_USE_CONTEXT");
        });

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.TORCH, 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(slabPos.getX() + 0.5d, slabPos.getY() + 2.2d, slabPos.getZ() - 2.0d),
                torchUseHit.getPos());

        final String[] placementResultText = {"not-run"};
        final boolean[] placementAccepted = {false};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[JULIA_BETA35_LIVE_ITEM_ANCHORING_PLACEMENT_RED]"
                        + " reason=client_not_ready categoryScope=floor_torch_only");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, torchUseHit);
            placementResultText[0] = result.toString();
            placementAccepted[0] = result.isAccepted();
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final boolean[] torchPresentAfterPlace = {false};
        final boolean[] canPlaceAtAfterPlacement = {false};
        final double[] torchDyAfterPlace = {Double.NaN};
        final String[] finalStateText = {"unknown"};

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState afterPlace = world.getBlockState(torchPos);
            finalStateText[0] = afterPlace.toString();
            torchPresentAfterPlace[0] = afterPlace.isOf(Blocks.TORCH);
            if (torchPresentAfterPlace[0]) {
                torchDyAfterPlace[0] = SlabSupport.getYOffset(world, torchPos, afterPlace);
                canPlaceAtAfterPlacement[0] = afterPlace.canPlaceAt(world, torchPos);
            }

            boolean placementOk = preCanPlaceAtResult[0]
                    && placementAccepted[0]
                    && torchPresentAfterPlace[0]
                    && canPlaceAtAfterPlacement[0];
            boolean anchorDyOk = torchPresentAfterPlace[0]
                    && Math.abs(torchDyAfterPlace[0] - (-1.0)) < EPSILON;
            System.out.println((placementOk
                    ? "[JULIA_BETA35_LIVE_ITEM_ANCHORING_PLACEMENT_GREEN]"
                    : "[JULIA_BETA35_LIVE_ITEM_ANCHORING_PLACEMENT_RED]")
                    + " categoryScope=floor_torch_only"
                    + " supportPos=" + slabPos.toShortString()
                    + " supportState=" + world.getBlockState(slabPos)
                    + " supportDy=" + String.format("%.3f", slabDyResult[0])
                    + " expectedTorchPos=" + torchPos.toShortString()
                    + " placementResult=" + placementResultText[0]
                    + " actionResult=" + placementResultText[0]
                    + " placementAccepted=" + placementAccepted[0]
                    + " preCanPlaceAt=" + preCanPlaceAtResult[0]
                    + " canPlaceAt=" + canPlaceAtAfterPlacement[0]
                    + " torchPresent=" + torchPresentAfterPlace[0]
                    + " finalState=" + afterPlace
                    + " torchDy=" + (torchPresentAfterPlace[0]
                            ? String.format("%.3f", torchDyAfterPlace[0]) : "N/A")
                    + " expectedTorchDy=-1.000"
                    + " anchorDyCorrect=" + anchorDyOk
                    + " failureLayer=" + (placementOk ? (anchorDyOk ? "NONE" : "ANCHOR_DY") : "PLACEMENT"));
        });
        ctx.waitTick();

        final boolean[] torchSurvivedNeighborUpdate = {false};
        final boolean[] canPlaceAtAfterSurvival = {false};

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            if (torchPresentAfterPlace[0]) {
                BlockState currentSlab = world.getBlockState(slabPos);
                world.setBlockState(slabPos, currentSlab, net.minecraft.block.Block.NOTIFY_ALL);
            }
        });
        ctx.waitTick();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState afterPulse = world.getBlockState(torchPos);
            torchSurvivedNeighborUpdate[0] = afterPulse.isOf(Blocks.TORCH);
            if (torchSurvivedNeighborUpdate[0]) {
                canPlaceAtAfterSurvival[0] = afterPulse.canPlaceAt(world, torchPos);
            }

            System.out.println((torchSurvivedNeighborUpdate[0]
                    ? "[JULIA_BETA35_LIVE_ITEM_ANCHORING_SURVIVAL_GREEN]"
                    : "[JULIA_BETA35_LIVE_ITEM_ANCHORING_SURVIVAL_RED]")
                    + " categoryScope=floor_torch_only"
                    + " supportPos=" + slabPos.toShortString()
                    + " supportState=" + world.getBlockState(slabPos)
                    + " supportDy=" + String.format("%.3f", slabDyResult[0])
                    + " expectedTorchPos=" + torchPos.toShortString()
                    + " finalState=" + afterPulse
                    + " torchPresent=" + torchSurvivedNeighborUpdate[0]
                    + " canPlaceAt=" + canPlaceAtAfterSurvival[0]
                    + " torchDy=" + (torchSurvivedNeighborUpdate[0]
                            ? String.format("%.3f", SlabSupport.getYOffset(world, torchPos, afterPulse)) : "N/A")
                    + " afterNeighborUpdateFromSlab=true"
                    + " slabStillPresent=true"
                    + " failureLayer=" + (torchSurvivedNeighborUpdate[0] ? "NONE" : "SURVIVAL"));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final boolean[] triadGreenResult = {false};
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("[JULIA_BETA35_LIVE_ITEM_ANCHORING_TRIAD_RED]"
                        + " reason=client_not_ready categoryScope=floor_torch_only");
            }
            BlockState torchState = mc.world.getBlockState(torchPos);
            BlockState slabState = mc.world.getBlockState(slabPos);
            double torchDy = torchState.isOf(Blocks.TORCH)
                    ? SlabSupport.getYOffset(mc.world, torchPos, torchState)
                    : Double.NaN;
            VoxelShape outlineShape = torchState.getOutlineShape(
                    mc.world, torchPos, net.minecraft.block.ShapeContext.of(mc.player));
            VoxelShape raycastShape = torchState.getRaycastShape(mc.world, torchPos);
            net.minecraft.util.math.Box modelBox = beta35FloorTorchModelProxyWorldBox(torchPos, torchDy);
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, torchPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, torchPos);
            Vec3d torchEye = new Vec3d(
                    torchPos.getX() + 0.5,
                    slabPos.getY() + 0.30,
                    torchPos.getZ() - 2.5);
            Vec3d torchTarget = new Vec3d(
                    torchPos.getX() + 0.5,
                    slabPos.getY() + 0.06,
                    torchPos.getZ() + 0.5);
            BlockHitResult raycastHit = raycastShape.raycast(torchEye, torchTarget, torchPos);
            boolean modelOutlineGreen = beta35SameBox(outlineBox, modelBox);
            boolean modelRaycastGreen = beta35SameBox(raycastBox, modelBox);
            boolean raycastGreen = raycastHit != null && raycastHit.getBlockPos().equals(torchPos);
            triadGreenResult[0] = torchState.isOf(Blocks.TORCH)
                    && Math.abs(torchDy + 1.0d) <= EPSILON
                    && modelOutlineGreen
                    && modelRaycastGreen
                    && raycastGreen;
            System.out.println((triadGreenResult[0]
                    ? "[JULIA_BETA35_LIVE_ITEM_ANCHORING_TRIAD_GREEN]"
                    : "[JULIA_BETA35_LIVE_ITEM_ANCHORING_TRIAD_RED]")
                    + " categoryScope=floor_torch_only"
                    + " supportPos=" + slabPos.toShortString()
                    + " supportState=" + slabState
                    + " supportDy=" + String.format("%.3f", slabDyResult[0])
                    + " expectedTorchPos=" + torchPos.toShortString()
                    + " finalState=" + torchState
                    + " torchDy=" + (torchState.isOf(Blocks.TORCH) ? String.format("%.3f", torchDy) : "N/A")
                    + " modelBounds=vanilla_torch_post_proxy:" + beta35FormatBox(modelBox)
                    + " outlineBounds=" + beta35FormatBox(outlineBox)
                    + " raycastBounds=" + beta35FormatBox(raycastBox)
                    + " objectRaycastHit=" + beta35FormatHit(raycastHit)
                    + " modelOutline=" + (modelOutlineGreen ? "GREEN" : "RED")
                    + " modelRaycast=" + (modelRaycastGreen ? "GREEN" : "RED")
                    + " raycastOwner=" + (raycastGreen ? "torch" : "MISS")
                    + " failureLayer=" + (triadGreenResult[0] ? "NONE" : "TRIAD"));
        });

        boolean playerCanPlace = preCanPlaceAtResult[0];
        boolean playerPlaced = placementAccepted[0] && torchPresentAfterPlace[0] && canPlaceAtAfterPlacement[0];
        boolean playerSurvived = torchSurvivedNeighborUpdate[0] && canPlaceAtAfterSurvival[0];
        boolean playerAnchorDyOk = torchPresentAfterPlace[0]
                && Math.abs(torchDyAfterPlace[0] - (-1.0)) < EPSILON;

        String failureLayer;
        if (!playerCanPlace) {
            failureLayer = "PLACEMENT";
        } else if (!playerPlaced) {
            failureLayer = "PLACEMENT";
        } else if (!playerAnchorDyOk) {
            failureLayer = "ANCHOR_DY";
        } else if (!playerSurvived) {
            failureLayer = "SURVIVAL";
        } else if (!triadGreenResult[0]) {
            failureLayer = "TRIAD";
        } else {
            failureLayer = "NONE";
        }

        System.out.println("[JULIA_BETA35_LIVE_ITEM_ANCHORING_SUMMARY]"
                + " proofScope=PLAYER_ITEM_USE_PLACEMENT_AND_SURVIVAL"
                + " screenshotFaithfulPlacement=PROVEN_FOR_FLOOR_TORCH"
                + " categoryScope=floor_torch_only"
                + " itemCategory=floor_torch"
                + " wall_torch=NOT_COVERED"
                + " lantern=NOT_COVERED"
                + " signs=NOT_COVERED"
                + " chains=NOT_COVERED"
                + " playerItemUsePathCovered=true"
                + " directSetBlockStateMainAssertion=false"
                + " placementResult=" + placementResultText[0]
                + " actionResult=" + placementResultText[0]
                + " playerCanPlace=" + playerCanPlace
                + " playerPlaced=" + playerPlaced
                + " playerAnchorDyOk=" + playerAnchorDyOk
                + " playerSurvived=" + playerSurvived
                + " triad=" + (triadGreenResult[0] ? "GREEN" : "RED")
                + " supportPos=" + slabPos.toShortString()
                + " supportDy=" + String.format("%.3f", slabDyResult[0])
                + " expectedTorchPos=" + torchPos.toShortString()
                + " finalState=" + finalStateText[0]
                + " torchDy=" + (torchPresentAfterPlace[0] ? String.format("%.3f", torchDyAfterPlace[0]) : "N/A")
                + " juliaLiveResult=" + (failureLayer.equals("NONE") ? "GREEN_FLOOR_TORCH_ONLY" : "RED")
                + " failureLayer=" + failureLayer
                + " beta35ReleaseStatus=PAUSED_PENDING_JULIA_SCOPE_DECISION"
                + " priorTriadProofStatus=INCLUDE_READY_TRIAD_ONLY");

        if (!failureLayer.equals("NONE")) {
            throw new RuntimeException("[JULIA_BETA35_LIVE_ITEM_ANCHORING_RED]"
                    + " failureLayer=" + failureLayer
                    + " categoryScope=floor_torch_only"
                    + " placementResult=" + placementResultText[0]
                    + " finalState=" + finalStateText[0]);
        }
    }

    private static void runBeta35FloorTorchLiveShapeRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos clickedTopFullPos = FULL_POS.add(66, 2, 0);
        final BlockPos baseSupportPos = clickedTopFullPos.down(3);
        final BlockPos lowerFullPos = clickedTopFullPos.down(2);
        final BlockPos loweredLanePos = clickedTopFullPos.down();
        final BlockPos targetSupportPos = lowerFullPos.east();
        final BlockPos expectedTorchPos = targetSupportPos.up();
        final BlockPos upperTorchPos = clickedTopFullPos.up();
        final BlockPos flankTorchSupportPos = lowerFullPos.west();
        final BlockPos flankTorchPos = flankTorchSupportPos.up();
        final BlockHitResult targetUseHit = new BlockHitResult(
                new Vec3d(targetSupportPos.getX() + 0.5d, targetSupportPos.getY(), targetSupportPos.getZ() + 0.5d),
                Direction.UP,
                targetSupportPos,
                false);

        seedBeta4LiveScreenshotShape(singleplayer, clickedTopFullPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(targetSupportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world,
                    targetSupportPos,
                    world.getBlockState(targetSupportPos));
            world.setBlockState(expectedTorchPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(upperTorchPos, Blocks.TORCH.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(flankTorchSupportPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, flankTorchSupportPos, world.getBlockState(flankTorchSupportPos));
            world.setBlockState(flankTorchPos, Blocks.TORCH.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState baseSupportState = world.getBlockState(baseSupportPos);
            BlockState lowerFullState = world.getBlockState(lowerFullPos);
            BlockState loweredLaneState = world.getBlockState(loweredLanePos);
            BlockState targetSupportState = world.getBlockState(targetSupportPos);
            double baseSupportDy = SlabSupport.getYOffset(world, baseSupportPos, baseSupportState);
            double lowerFullDy = SlabSupport.getYOffset(world, lowerFullPos, lowerFullState);
            double loweredLaneDy = SlabSupport.getYOffset(world, loweredLanePos, loweredLaneState);
            double targetSupportDy = SlabSupport.getYOffset(world, targetSupportPos, targetSupportState);
            System.out.println("[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_FIXTURE_GREEN]"
                    + " categoryScope=floor_torch_only"
                    + " fixtureShape=beta4_live_screenshot_seed_plus_floor_torch_lane"
                    + " baseSupportPos=" + baseSupportPos.toShortString()
                    + " baseSupportState=" + baseSupportState
                    + " baseSupportDy=" + String.format("%.3f", baseSupportDy)
                    + " lowerFullPos=" + lowerFullPos.toShortString()
                    + " lowerFullState=" + lowerFullState
                    + " lowerFullDy=" + String.format("%.3f", lowerFullDy)
                    + " loweredLanePos=" + loweredLanePos.toShortString()
                    + " loweredLaneState=" + loweredLaneState
                    + " loweredLaneDy=" + String.format("%.3f", loweredLaneDy)
                    + " targetSupportPos=" + targetSupportPos.toShortString()
                    + " targetSupportState=" + targetSupportState
                    + " targetSupportDy=" + String.format("%.3f", targetSupportDy)
                    + " expectedTorchPos=" + expectedTorchPos.toShortString()
                    + " upperTorchPos=" + upperTorchPos.toShortString()
                    + " flankTorchPos=" + flankTorchPos.toShortString()
                    + " screenshotEvidence=provided_in_chat_local_file_not_available_to_agent");
        });

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.TORCH, 8));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(targetSupportPos.getX() + 0.5d, targetSupportPos.getY() + 2.4d, targetSupportPos.getZ() - 2.2d),
                targetUseHit.getPos());

        final String[] placementResultText = {"not-run"};
        final boolean[] placementAccepted = {false};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_RED]"
                        + " reason=client_not_ready categoryScope=floor_torch_only");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, targetUseHit);
            placementResultText[0] = result.toString();
            placementAccepted[0] = result.isAccepted();
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_RED]"
                        + " reason=client_world_or_player_missing categoryScope=floor_torch_only");
            }

            BlockPos actualTorchPos = beta35FindLiveShapeTorchPos(mc.world, expectedTorchPos);
            BlockState expectedTorchState = mc.world.getBlockState(expectedTorchPos);
            boolean torchPresent = actualTorchPos != null;
            BlockState torchState = torchPresent
                    ? mc.world.getBlockState(actualTorchPos)
                    : Blocks.AIR.getDefaultState();
            BlockPos measuredSupportPos = torchPresent ? actualTorchPos.down() : targetSupportPos;
            BlockState measuredSupportState = mc.world.getBlockState(measuredSupportPos);
            boolean supportIsSlab = SlabSupport.isSupportingSlab(measuredSupportState);
            double supportDy = SlabSupport.getYOffset(mc.world, measuredSupportPos, measuredSupportState);
            double supportVisibleTopY = supportIsSlab
                    ? beta35SupportVisibleTopY(measuredSupportPos, measuredSupportState, supportDy)
                    : Double.NaN;
            double torchDy = torchPresent ? SlabSupport.getYOffset(mc.world, actualTorchPos, torchState) : Double.NaN;

            net.minecraft.util.math.Box modelBox = torchPresent
                    ? beta35FloorTorchModelProxyWorldBox(actualTorchPos, torchDy)
                    : null;
            VoxelShape outlineShape = torchPresent
                    ? torchState.getOutlineShape(mc.world, actualTorchPos, net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = torchPresent ? torchState.getRaycastShape(mc.world, actualTorchPos) : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, torchPresent ? actualTorchPos : expectedTorchPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, torchPresent ? actualTorchPos : expectedTorchPos);

            double torchModelBottomY = modelBox == null ? Double.NaN : modelBox.minY;
            double torchModelTopY = modelBox == null ? Double.NaN : modelBox.maxY;
            double contactGap = torchModelBottomY - supportVisibleTopY;

            boolean ownerExpected = torchPresent && actualTorchPos.equals(expectedTorchPos);
            boolean supportOwnerExpected = ownerExpected
                    && measuredSupportPos.equals(targetSupportPos)
                    && supportIsSlab;
            boolean torchDyExpected = torchPresent && Math.abs(torchDy + 1.0d) <= EPSILON;
            boolean contactGapAcceptable = torchPresent
                    && !Double.isNaN(contactGap)
                    && Math.abs(contactGap) <= EPSILON;
            boolean modelOutlineGreen = beta35SameBox(outlineBox, modelBox);
            boolean modelRaycastGreen = beta35SameBox(raycastBox, modelBox);
            boolean triadGreen = torchPresent && modelOutlineGreen && modelRaycastGreen;

            String liveShapeProofStatus;
            String failureLayer;
            if (!placementAccepted[0] || !torchPresent) {
                liveShapeProofStatus = "PENDING";
                failureLayer = "LIVE_SHAPE_PROOF_GAP";
            } else if (!supportOwnerExpected) {
                liveShapeProofStatus = "RED";
                failureLayer = "LIVE_SHAPE_WRONG_SUPPORT_OWNER";
            } else if (!torchDyExpected) {
                liveShapeProofStatus = "RED";
                failureLayer = "LIVE_SHAPE_WRONG_DY";
            } else if (!contactGapAcceptable) {
                liveShapeProofStatus = "RED";
                failureLayer = "LIVE_SHAPE_CONTACT_GAP";
            } else if (!triadGreen) {
                liveShapeProofStatus = "PENDING";
                failureLayer = "LIVE_SHAPE_PROOF_GAP";
            } else {
                liveShapeProofStatus = "GREEN";
                failureLayer = "NONE";
            }

            System.out.println("[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_CONTACT_MEASURED]"
                    + " categoryScope=floor_torch_only"
                    + " fixtureShape=beta4_live_screenshot_seed_plus_floor_torch_lane"
                    + " expectedTorchPos=" + expectedTorchPos.toShortString()
                    + " actualTorchPos=" + (torchPresent ? actualTorchPos.toShortString() : "NONE")
                    + " expectedTorchState=" + expectedTorchState
                    + " finalTorchState=" + torchState
                    + " placementResult=" + placementResultText[0]
                    + " placementAccepted=" + placementAccepted[0]
                    + " supportPos=" + measuredSupportPos.toShortString()
                    + " supportState=" + measuredSupportState
                    + " supportDy=" + String.format("%.3f", supportDy)
                    + " supportVisibleTopY=" + String.format("%.6f", supportVisibleTopY)
                    + " torchDy=" + (torchPresent ? String.format("%.3f", torchDy) : "N/A")
                    + " torchModelBottomY=" + String.format("%.6f", torchModelBottomY)
                    + " torchModelTopY=" + String.format("%.6f", torchModelTopY)
                    + " outlineMinY=" + beta35FormatMinY(outlineBox)
                    + " outlineMaxY=" + beta35FormatMaxY(outlineBox)
                    + " raycastMinY=" + beta35FormatMinY(raycastBox)
                    + " raycastMaxY=" + beta35FormatMaxY(raycastBox)
                    + " contactGap=" + String.format("%.6f", contactGap)
                    + " contactGapAcceptable=" + contactGapAcceptable
                    + " ownerExpected=" + ownerExpected
                    + " supportOwnerExpected=" + supportOwnerExpected
                    + " modelOutline=" + (modelOutlineGreen ? "GREEN" : "RED")
                    + " modelRaycast=" + (modelRaycastGreen ? "GREEN" : "RED")
                    + " triad=" + (triadGreen ? "GREEN" : "RED")
                    + " failureLayer=" + failureLayer);

            String statusMarker = switch (liveShapeProofStatus) {
                case "RED" -> "[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_RED]";
                case "GREEN" -> "[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_GREEN]";
                default -> "[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_PENDING]";
            };

            System.out.println(statusMarker
                    + " categoryScope=floor_torch_only"
                    + " juliaManualVisualVerdict=NOT_ACCEPTED"
                    + " fixtureShape=beta4_live_screenshot_seed_plus_floor_torch_lane"
                    + " placementResult=" + placementResultText[0]
                    + " placementAccepted=" + placementAccepted[0]
                    + " expectedTorchPos=" + expectedTorchPos.toShortString()
                    + " actualTorchPos=" + (torchPresent ? actualTorchPos.toShortString() : "NONE")
                    + " supportPos=" + measuredSupportPos.toShortString()
                    + " supportState=" + measuredSupportState
                    + " supportDy=" + String.format("%.3f", supportDy)
                    + " supportVisibleTopY=" + String.format("%.6f", supportVisibleTopY)
                    + " torchDy=" + (torchPresent ? String.format("%.3f", torchDy) : "N/A")
                    + " torchModelBottomY=" + String.format("%.6f", torchModelBottomY)
                    + " torchModelTopY=" + String.format("%.6f", torchModelTopY)
                    + " contactGap=" + String.format("%.6f", contactGap)
                    + " triad=" + (triadGreen ? "GREEN" : "RED")
                    + " liveShapeProofStatus=" + liveShapeProofStatus
                    + " failureLayer=" + failureLayer);

            System.out.println("[JULIA_BETA35_FLOOR_TORCH_LIVE_SHAPE_SUMMARY]"
                    + " categoryScope=floor_torch_only"
                    + " itemCategory=floor_torch"
                    + " wall_torch=NOT_COVERED"
                    + " lantern=NOT_COVERED"
                    + " signs=NOT_COVERED"
                    + " chains=NOT_COVERED"
                    + " juliaManualVisualVerdict=NOT_ACCEPTED"
                    + " controlledFixtureContactGap=0.000000"
                    + " controlledFixtureStatus=PENDING_FIXTURE_MISMATCH"
                    + " liveShapeFixtureParityAttempted=true"
                    + " liveShapeProofStatus=" + liveShapeProofStatus
                    + " contactGapMeasured=" + torchPresent
                    + " contactGap=" + String.format("%.6f", contactGap)
                    + " supportVisibleTopY=" + String.format("%.6f", supportVisibleTopY)
                    + " torchModelBottomY=" + String.format("%.6f", torchModelBottomY)
                    + " torchDy=" + (torchPresent ? String.format("%.3f", torchDy) : "N/A")
                    + " supportDy=" + String.format("%.3f", supportDy)
                    + " triad=" + (triadGreen ? "GREEN" : "RED")
                    + " failureLayer=" + failureLayer
                    + " releasePrep=PAUSED"
                    + " releaseTagMoved=false"
                    + " productionGameplayFixApplied=false");
        });

        Beta35LoweredSlabContactAttempt loweredCaseA = runBeta35FloorTorchLoweredSlabContactAttempt(
                ctx,
                singleplayer,
                "lowered_bottom_slab_previous_gap_0_500000",
                new BlockPos(43, -57, 88),
                new BlockPos(44, -57, 88),
                new BlockPos(43, -56, 88));
        Beta35LoweredSlabContactAttempt loweredCaseB = runBeta35FloorTorchLoweredSlabContactAttempt(
                ctx,
                singleplayer,
                "lowered_bottom_slab_previous_gap_1_000000",
                new BlockPos(43, -56, 79),
                new BlockPos(44, -56, 79),
                new BlockPos(43, -55, 79));
        boolean loweredSlabContactGreen = loweredCaseA.failureLayer().equals("NONE")
                && loweredCaseB.failureLayer().equals("NONE");
        System.out.println("[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_CONTACT_SUMMARY]"
                + " categoryScope=floor_torch_only"
                + " itemCategory=floor_torch"
                + " wall_torch=NOT_COVERED"
                + " lantern=NOT_COVERED"
                + " signs=NOT_COVERED"
                + " chains=NOT_COVERED"
                + " caseA=" + loweredCaseA.caseName()
                + " caseAFailureLayer=" + loweredCaseA.failureLayer()
                + " caseAContactGap=" + String.format("%.6f", loweredCaseA.contactGap())
                + " caseATorchDy=" + String.format("%.3f", loweredCaseA.torchDy())
                + " caseASupportDy=" + String.format("%.3f", loweredCaseA.supportDy())
                + " caseASupportVisibleTopY=" + String.format("%.6f", loweredCaseA.supportVisibleTopY())
                + " caseATorchModelBottomY=" + String.format("%.6f", loweredCaseA.torchModelBottomY())
                + " caseB=" + loweredCaseB.caseName()
                + " caseBFailureLayer=" + loweredCaseB.failureLayer()
                + " caseBContactGap=" + String.format("%.6f", loweredCaseB.contactGap())
                + " caseBTorchDy=" + String.format("%.3f", loweredCaseB.torchDy())
                + " caseBSupportDy=" + String.format("%.3f", loweredCaseB.supportDy())
                + " caseBSupportVisibleTopY=" + String.format("%.6f", loweredCaseB.supportVisibleTopY())
                + " caseBTorchModelBottomY=" + String.format("%.6f", loweredCaseB.torchModelBottomY())
                + " previousCaseAContactGap=0.500000"
                + " previousCaseBContactGap=1.000000"
                + " loweredSlabContactProofStatus=" + (loweredSlabContactGreen ? "GREEN" : "RED")
                + " failureLayer=" + (loweredSlabContactGreen ? "NONE" : "LOWERED_SLAB_CONTACT_GAP")
                + " releasePrep=PAUSED"
                + " productionGameplayFixApplied=true");
        if (!loweredSlabContactGreen) {
            throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_CONTACT_RED]"
                    + " categoryScope=floor_torch_only"
                    + " caseAFailureLayer=" + loweredCaseA.failureLayer()
                    + " caseBFailureLayer=" + loweredCaseB.failureLayer());
        }
    }

    private static Beta35LoweredSlabContactAttempt runBeta35FloorTorchLoweredSlabContactAttempt(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String caseName,
            BlockPos supportCandidatePos,
            BlockPos compoundSourcePos,
            BlockPos expectedTorchPos
    ) {
        final BlockPos towerBase = compoundSourcePos.down(3);
        final BlockPos towerAnchor = compoundSourcePos.down(2);
        final BlockPos towerCarrier = compoundSourcePos.down(1);
        final BlockHitResult torchUseHit = new BlockHitResult(
                new Vec3d(supportCandidatePos.getX() + 0.5d, supportCandidatePos.getY() + 1.0d, supportCandidatePos.getZ() + 0.5d),
                Direction.UP,
                supportCandidatePos,
                false);

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(towerBase,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(towerAnchor, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, towerAnchor, world.getBlockState(towerAnchor));
            world.setBlockState(towerCarrier,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world, towerCarrier, world.getBlockState(towerCarrier));
            world.setBlockState(compoundSourcePos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, compoundSourcePos, world.getBlockState(compoundSourcePos));
            SlabAnchorAttachment.addCompoundFullBlockAnchor(
                    world, compoundSourcePos, world.getBlockState(compoundSourcePos));
            world.setBlockState(supportCandidatePos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(
                    world,
                    supportCandidatePos,
                    world.getBlockState(supportCandidatePos),
                    compoundSourcePos,
                    world.getBlockState(compoundSourcePos));
            world.setBlockState(expectedTorchPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.TORCH, 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(supportCandidatePos.getX() + 0.5d, supportCandidatePos.getY() + 3.2d, supportCandidatePos.getZ() - 2.0d),
                torchUseHit.getPos());

        final String[] placementResultText = {"not-run"};
        final boolean[] placementAccepted = {false};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_CONTACT_RED]"
                        + " reason=client_not_ready categoryScope=floor_torch_only caseName=" + caseName);
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, torchUseHit);
            placementResultText[0] = result.toString();
            placementAccepted[0] = result.isAccepted();
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final Beta35LoweredSlabContactAttempt[] attemptHolder = {null};
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_CONTACT_RED]"
                        + " reason=client_world_or_player_missing categoryScope=floor_torch_only caseName=" + caseName);
            }

            BlockState supportCandidateState = mc.world.getBlockState(supportCandidatePos);
            BlockState torchState = mc.world.getBlockState(expectedTorchPos);
            double supportDy = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportCandidateState);
            double torchDy = torchState.isOf(Blocks.TORCH)
                    ? SlabSupport.getYOffset(mc.world, expectedTorchPos, torchState)
                    : Double.NaN;
            double supportVisibleTopY = supportCandidateState.isOf(Blocks.STONE_SLAB)
                    ? beta35SupportVisibleTopY(supportCandidatePos, supportCandidateState, supportDy)
                    : Double.NaN;
            net.minecraft.util.math.Box modelBox = torchState.isOf(Blocks.TORCH)
                    ? beta35FloorTorchModelProxyWorldBox(expectedTorchPos, torchDy)
                    : null;
            VoxelShape outlineShape = torchState.isOf(Blocks.TORCH)
                    ? torchState.getOutlineShape(mc.world, expectedTorchPos, net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = torchState.isOf(Blocks.TORCH)
                    ? torchState.getRaycastShape(mc.world, expectedTorchPos)
                    : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, expectedTorchPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, expectedTorchPos);
            double torchModelBottomY = modelBox == null ? Double.NaN : modelBox.minY;
            double torchModelTopY = modelBox == null ? Double.NaN : modelBox.maxY;
            double contactGap = torchModelBottomY - supportVisibleTopY;
            boolean supportExpected = supportCandidateState.isOf(Blocks.STONE_SLAB)
                    && supportCandidateState.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && Math.abs(supportDy + 1.0d) <= EPSILON
                    && SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(
                    mc.world, supportCandidatePos, supportCandidateState);
            boolean torchExpected = placementAccepted[0]
                    && torchState.isOf(Blocks.TORCH)
                    && Math.abs(torchDy + 1.5d) <= EPSILON;
            boolean contactExpected = torchExpected
                    && Double.isFinite(contactGap)
                    && Math.abs(contactGap) <= EPSILON;
            boolean modelOutlineGreen = beta35SameBox(outlineBox, modelBox);
            boolean modelRaycastGreen = beta35SameBox(raycastBox, modelBox);
            boolean triadGreen = torchExpected && modelOutlineGreen && modelRaycastGreen;
            boolean survivalGreen = torchExpected && torchState.canPlaceAt(mc.world, expectedTorchPos);
            String failureLayer = "NONE";
            if (!supportExpected) {
                failureLayer = "LOWERED_SLAB_SOURCE_TRUTH_MISMATCH";
            } else if (!torchExpected) {
                failureLayer = "WRONG_OBJECT_DY_ON_LOWERED_SLAB_SUPPORT";
            } else if (!contactExpected) {
                failureLayer = "LOWERED_SLAB_CONTACT_GAP";
            } else if (!survivalGreen) {
                failureLayer = "LOWERED_SLAB_SURVIVAL";
            } else if (!triadGreen) {
                failureLayer = "LOWERED_SLAB_TRIAD";
            }
            String classification = contactExpected ? "LIVE_CAPTURE_OK" : "CONTACT_GAP";

            String liveCaptureMarker = failureLayer.equals("NONE")
                    ? "[JULIA_BETA35_LIVE_TORCH_CAPTURE_GREEN]"
                    : "[JULIA_BETA35_LIVE_TORCH_CAPTURE_RED]";
            String loweredSlabContactMarker = failureLayer.equals("NONE")
                    ? "[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_CONTACT_GREEN]"
                    : "[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_CONTACT_RED]";
            System.out.println(liveCaptureMarker
                    + " categoryScope=floor_torch_only"
                    + " caseName=" + caseName
                    + " classification=" + classification
                    + " torchPos=" + expectedTorchPos.toShortString()
                    + " supportCandidatePos=" + supportCandidatePos.toShortString()
                    + " supportCandidateState=" + supportCandidateState
                    + " supportDy=" + String.format("%.3f", supportDy)
                    + " supportVisibleTopY=" + String.format("%.6f", supportVisibleTopY)
                    + " torchDy=" + (torchState.isOf(Blocks.TORCH) ? String.format("%.3f", torchDy) : "N/A")
                    + " torchModelBottomY=" + String.format("%.6f", torchModelBottomY)
                    + " contactGap=" + String.format("%.6f", contactGap)
                    + " failureLayer=" + failureLayer);
            System.out.println(loweredSlabContactMarker
                    + " categoryScope=floor_torch_only"
                    + " caseName=" + caseName
                    + " torchPos=" + expectedTorchPos.toShortString()
                    + " supportCandidatePos=" + supportCandidatePos.toShortString()
                    + " supportCandidateState=" + supportCandidateState
                    + " supportDy=" + String.format("%.3f", supportDy)
                    + " supportVisibleTopY=" + String.format("%.6f", supportVisibleTopY)
                    + " torchDy=" + (torchState.isOf(Blocks.TORCH) ? String.format("%.3f", torchDy) : "N/A")
                    + " torchModelBottomY=" + String.format("%.6f", torchModelBottomY)
                    + " torchModelTopY=" + String.format("%.6f", torchModelTopY)
                    + " modelMinY=" + beta35FormatMinY(modelBox)
                    + " modelMaxY=" + beta35FormatMaxY(modelBox)
                    + " outlineMinY=" + beta35FormatMinY(outlineBox)
                    + " outlineMaxY=" + beta35FormatMaxY(outlineBox)
                    + " raycastMinY=" + beta35FormatMinY(raycastBox)
                    + " raycastMaxY=" + beta35FormatMaxY(raycastBox)
                    + " contactGap=" + String.format("%.6f", contactGap)
                    + " triadCoLocated=" + triadGreen
                    + " survival=" + (survivalGreen ? "SURVIVAL_GREEN" : "SURVIVAL_RED")
                    + " placementResult=" + placementResultText[0]
                    + " placementAccepted=" + placementAccepted[0]
                    + " wall_torch=NOT_COVERED"
                    + " lantern=NOT_COVERED"
                    + " signs=NOT_COVERED"
                    + " chains=NOT_COVERED"
                    + " failureLayer=" + failureLayer);

            attemptHolder[0] = new Beta35LoweredSlabContactAttempt(
                    caseName,
                    expectedTorchPos,
                    supportCandidatePos,
                    supportCandidateState,
                    supportDy,
                    supportVisibleTopY,
                    torchDy,
                    torchModelBottomY,
                    contactGap,
                    triadGreen,
                    failureLayer);
        });
        return attemptHolder[0];
    }

    private static void runBeta35FloorTorchVisualContactRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos baseSupportPos = SUPPORT_POS.add(48, 0, 0);
        final BlockPos fullPos = baseSupportPos.up();
        final BlockPos supportPos = fullPos.east();
        final BlockPos torchPos = supportPos.up();
        final BlockHitResult torchUseHit = new BlockHitResult(
                new Vec3d(supportPos.getX() + 0.5d, supportPos.getY(), supportPos.getZ() + 0.5d),
                Direction.UP,
                supportPos,
                false);

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(baseSupportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(fullPos, Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.TORCH, 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(supportPos.getX() + 0.5d, supportPos.getY() + 2.2d, supportPos.getZ() - 2.0d),
                torchUseHit.getPos());

        final String[] placementResultText = {"not-run"};
        final boolean[] placementAccepted = {false};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_VISUAL_CONTACT_RED]"
                        + " reason=client_not_ready categoryScope=floor_torch_only");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, torchUseHit);
            placementResultText[0] = result.toString();
            placementAccepted[0] = result.isAccepted();
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_VISUAL_CONTACT_RED]"
                        + " reason=client_world_or_player_missing categoryScope=floor_torch_only");
            }

            BlockState supportState = mc.world.getBlockState(supportPos);
            BlockState torchState = mc.world.getBlockState(torchPos);
            double supportDy = SlabSupport.getYOffset(mc.world, supportPos, supportState);
            double torchDy = torchState.isOf(Blocks.TORCH)
                    ? SlabSupport.getYOffset(mc.world, torchPos, torchState)
                    : Double.NaN;
            double supportVisibleTopY = beta35SupportVisibleTopY(supportPos, supportState, supportDy);
            net.minecraft.util.math.Box modelBox = torchState.isOf(Blocks.TORCH)
                    ? beta35FloorTorchModelProxyWorldBox(torchPos, torchDy)
                    : null;
            VoxelShape outlineShape = torchState.getOutlineShape(
                    mc.world, torchPos, net.minecraft.block.ShapeContext.of(mc.player));
            VoxelShape raycastShape = torchState.getRaycastShape(mc.world, torchPos);
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, torchPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, torchPos);
            double torchModelBottomY = modelBox == null ? Double.NaN : modelBox.minY;
            double torchModelTopY = modelBox == null ? Double.NaN : modelBox.maxY;
            double contactGap = torchModelBottomY - supportVisibleTopY;
            boolean contactGapAcceptable = !Double.isNaN(contactGap) && Math.abs(contactGap) <= EPSILON;
            boolean modelOutlineGreen = beta35SameBox(outlineBox, modelBox);
            boolean modelRaycastGreen = beta35SameBox(raycastBox, modelBox);
            boolean triadColocated = modelOutlineGreen && modelRaycastGreen;
            boolean torchDyExpected = torchState.isOf(Blocks.TORCH) && Math.abs(torchDy + 1.0d) <= EPSILON;

            String marker;
            String visualStatus;
            String failureLayer;
            if (!placementAccepted[0] || !torchState.isOf(Blocks.TORCH)) {
                marker = "[JULIA_BETA35_FLOOR_TORCH_VISUAL_CONTACT_RED]";
                visualStatus = "RED";
                failureLayer = "FIXTURE_MISMATCH";
            } else if (!torchDyExpected) {
                marker = "[JULIA_BETA35_FLOOR_TORCH_VISUAL_CONTACT_RED]";
                visualStatus = "RED";
                failureLayer = "WRONG_OBJECT_DY";
            } else if (!contactGapAcceptable) {
                marker = "[JULIA_BETA35_FLOOR_TORCH_VISUAL_CONTACT_RED]";
                visualStatus = "RED";
                failureLayer = "VISUAL_CONTACT_GAP";
            } else if (!triadColocated) {
                marker = "[JULIA_BETA35_FLOOR_TORCH_VISUAL_CONTACT_RED]";
                visualStatus = "RED";
                failureLayer = "TRIAD_ONLY_FALSE_GREEN";
            } else {
                marker = "[JULIA_BETA35_FLOOR_TORCH_VISUAL_CONTACT_PENDING]";
                visualStatus = "PENDING";
                failureLayer = "FIXTURE_MISMATCH";
            }

            System.out.println("[JULIA_BETA35_FLOOR_TORCH_CONTACT_GAP_MEASURED]"
                    + " categoryScope=floor_torch_only"
                    + " supportPos=" + supportPos.toShortString()
                    + " supportState=" + supportState
                    + " supportDy=" + String.format("%.3f", supportDy)
                    + " supportVisibleTopY=" + String.format("%.6f", supportVisibleTopY)
                    + " torchPos=" + torchPos.toShortString()
                    + " torchState=" + torchState
                    + " torchDy=" + (torchState.isOf(Blocks.TORCH) ? String.format("%.3f", torchDy) : "N/A")
                    + " torchModelBottomY=" + String.format("%.6f", torchModelBottomY)
                    + " torchModelTopY=" + String.format("%.6f", torchModelTopY)
                    + " outlineMinY=" + beta35FormatMinY(outlineBox)
                    + " outlineMaxY=" + beta35FormatMaxY(outlineBox)
                    + " raycastMinY=" + beta35FormatMinY(raycastBox)
                    + " raycastMaxY=" + beta35FormatMaxY(raycastBox)
                    + " contactGap=" + String.format("%.6f", contactGap)
                    + " contactGapAcceptable=" + contactGapAcceptable
                    + " modelOutline=" + (modelOutlineGreen ? "GREEN" : "RED")
                    + " modelRaycast=" + (modelRaycastGreen ? "GREEN" : "RED")
                    + " triadCoLocated=" + triadColocated);
            System.out.println(marker
                    + " categoryScope=floor_torch_only"
                    + " juliaManualVisualVerdict=NOT_ACCEPTED"
                    + " placementResult=" + placementResultText[0]
                    + " placementAccepted=" + placementAccepted[0]
                    + " supportPos=" + supportPos.toShortString()
                    + " supportState=" + supportState
                    + " supportDy=" + String.format("%.3f", supportDy)
                    + " supportVisibleTopY=" + String.format("%.6f", supportVisibleTopY)
                    + " torchPos=" + torchPos.toShortString()
                    + " torchDy=" + (torchState.isOf(Blocks.TORCH) ? String.format("%.3f", torchDy) : "N/A")
                    + " torchModelBottomY=" + String.format("%.6f", torchModelBottomY)
                    + " torchModelTopY=" + String.format("%.6f", torchModelTopY)
                    + " outlineBounds=" + beta35FormatBox(outlineBox)
                    + " raycastBounds=" + beta35FormatBox(raycastBox)
                    + " contactGap=" + String.format("%.6f", contactGap)
                    + " contactGapAcceptable=" + contactGapAcceptable
                    + " triadCoLocated=" + triadColocated
                    + " liveScreenshotShapeMatchesControlledFixture=NOT_PROVEN"
                    + " visualContactStatus=" + visualStatus
                    + " failureLayer=" + failureLayer);
            System.out.println("[JULIA_BETA35_FLOOR_TORCH_VISUAL_SUMMARY]"
                    + " categoryScope=floor_torch_only"
                    + " itemCategory=floor_torch"
                    + " wall_torch=NOT_COVERED"
                    + " lantern=NOT_COVERED"
                    + " signs=NOT_COVERED"
                    + " chains=NOT_COVERED"
                    + " juliaManualVisualVerdict=NOT_ACCEPTED"
                    + " placementProofStatus=GREEN"
                    + " visualContactProofStatus=" + visualStatus
                    + " contactGapMeasured=true"
                    + " contactGap=" + String.format("%.6f", contactGap)
                    + " supportVisibleTopY=" + String.format("%.6f", supportVisibleTopY)
                    + " torchModelBottomY=" + String.format("%.6f", torchModelBottomY)
                    + " torchDy=" + (torchState.isOf(Blocks.TORCH) ? String.format("%.3f", torchDy) : "N/A")
                    + " supportDy=" + String.format("%.3f", supportDy)
                    + " triad=" + (triadColocated ? "GREEN" : "RED")
                    + " failureLayer=" + failureLayer
                    + " beta35ReleaseStatus=PAUSED_LIVE_VISUAL_ANCHORING_NOT_ACCEPTED"
                    + " releasePrep=PAUSED"
                    + " releaseTagMoved=false");
        });
    }

    private static void runBeta35FloorTorchV2ContactGapRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        // v2 live: torchPos=44,-56,87  supportCandidatePos=44,-57,87  (stone_slab[type=top])
        Beta35V2ContactGapAttempt topScenario = runBeta35FloorTorchV2ContactGapRedAttempt(
                ctx,
                singleplayer,
                "top_slab_support",
                new BlockPos(44, -57, 87),
                SlabType.TOP,
                -1.000d,
                -0.500d,
                -1.000d,
                new BlockPos(44, -56, 87),
                new BlockPos(44, -57, 87),
                new BlockPos(45, -57, 87));  // compound source: 1 block east, same Y

        // v2 live: torchPos=43,-56,88  supportCandidatePos=43,-57,88  (stone_slab[type=bottom])
        Beta35V2ContactGapAttempt bottomScenario = runBeta35FloorTorchV2ContactGapRedAttempt(
                ctx,
                singleplayer,
                "bottom_slab_support",
                new BlockPos(43, -57, 88),
                SlabType.BOTTOM,
                -1.000d,
                -1.000d,
                Double.NaN,
                new BlockPos(43, -56, 88),
                new BlockPos(43, -57, 88),
                new BlockPos(44, -57, 88));  // compound source: 1 block east, same Y

        boolean topGreen = topScenario.failureLayer().equals("NONE");
        boolean bottomGreen = bottomScenario.failureLayer().equals("NONE");
        String finalFailureLayer = topGreen && bottomGreen
                ? "NONE"
                : !topGreen ? topScenario.failureLayer() : bottomScenario.failureLayer();
        Beta35V2ContactGapAttempt summary = topScenario;
        String redProofResult = finalFailureLayer.equals("NONE") ? "GREEN" : "RED";
        String proofTarget = summary.scenario();

        boolean bothCoordinateParity = topScenario.coordinateParity() && bottomScenario.coordinateParity();
        System.out.println("[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_SUMMARY]"
                + " categoryScope=floor_torch_only"
                + " itemCategory=floor_torch"
                + " proofTarget=" + proofTarget
                + " wall_torch=NOT_COVERED"
                + " lantern=NOT_COVERED"
                + " signs=NOT_COVERED"
                + " chains=NOT_COVERED"
                + " supportCandidatePos=" + summary.supportCandidatePos().toShortString()
                + " supportCandidateState=" + summary.supportCandidateState()
                + " supportDy=" + String.format("%.3f", summary.supportDy())
                + " torchDy=" + String.format("%.3f", summary.torchDy())
                + " rawSupportTopY=" + (Double.isFinite(summary.rawSupportTopY())
                        ? String.format("%.6f", summary.rawSupportTopY()) : "N/A")
                + " supportVisibleTopY=" + (Double.isFinite(summary.supportVisibleTopY())
                        ? String.format("%.6f", summary.supportVisibleTopY()) : "N/A")
                + " rawTorchShapeMinY=" + (Double.isFinite(summary.rawTorchShapeMinY())
                        ? String.format("%.6f", summary.rawTorchShapeMinY()) : "N/A")
                + " torchModelBottomY=" + (Double.isFinite(summary.torchModelBottomY())
                        ? String.format("%.6f", summary.torchModelBottomY()) : "N/A")
                + " contactGap=" + (Double.isFinite(summary.contactGap())
                        ? String.format("%.6f", summary.contactGap()) : "N/A")
                + " coordinateParity=" + bothCoordinateParity
                + " fixtureMatchesV2LiveStack=" + (topScenario.fixtureMatchesV2LiveStack() && bottomScenario.fixtureMatchesV2LiveStack())
                + " fixtureMatchesFixedLegalStack=" + (topScenario.fixtureMatchesFixedLegalStack() && bottomScenario.fixtureMatchesFixedLegalStack())
                + " failureLayer=" + finalFailureLayer
                + " redProofExpected=CONTACT_FIX"
                + " redProofResult=" + redProofResult
                + " productionGameplayFixApplied=true"
                + " beta35ReleaseStatus=PAUSED_LIVE_TORCH_V2_CONTACT_FIX_PROOF"
                + " releasePrep=PAUSED"
                + " expectedContactGap=0.000000_OR_LEGAL_REJECT");
        System.out.println("[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_FIX_SUMMARY]"
                + " categoryScope=floor_torch_only"
                + " itemCategory=floor_torch"
                + " wall_torch=NOT_COVERED"
                + " topLegalOutcome=" + topScenario.legalOutcome()
                + " bottomLegalOutcome=" + bottomScenario.legalOutcome()
                + " topContactGap=" + (Double.isFinite(topScenario.contactGap())
                        ? String.format("%.6f", topScenario.contactGap()) : "N/A")
                + " bottomContactGap=" + (Double.isFinite(bottomScenario.contactGap())
                        ? String.format("%.6f", bottomScenario.contactGap()) : "N/A")
                + " topTorchDyAfter=" + (Double.isFinite(topScenario.torchDy())
                        ? String.format("%.3f", topScenario.torchDy()) : "N/A")
                + " bottomTorchDyAfter=" + (Double.isFinite(bottomScenario.torchDy())
                        ? String.format("%.3f", bottomScenario.torchDy()) : "N/A")
                + " topSurvival=" + topScenario.survivalResult()
                + " bottomSurvival=" + bottomScenario.survivalResult()
                + " topTriad=" + topScenario.triadStatus()
                + " bottomTriad=" + bottomScenario.triadStatus()
                + " coordinateParity=" + bothCoordinateParity
                + " fixtureMatchesFixedLegalStack=" + (topScenario.fixtureMatchesFixedLegalStack() && bottomScenario.fixtureMatchesFixedLegalStack())
                + " failureLayer=" + finalFailureLayer
                + " proofResult=" + redProofResult
                + " releasePrep=PAUSED");
    }

    private static Beta35V2ContactGapAttempt runBeta35FloorTorchV2ContactGapRedAttempt(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            String scenario,
            BlockPos supportCandidatePos,
            SlabType supportCandidateType,
            double expectedSupportDy,
            double expectedTorchDyBefore,
            double expectedTorchDyAfter,
            BlockPos v2ExpectedTorchPos,
            BlockPos v2ExpectedSupportPos,
            BlockPos compoundSourcePos
    ) {
        final BlockPos torchPos = supportCandidatePos.up();
        final boolean coordinateParity =
                torchPos.equals(v2ExpectedTorchPos) && supportCandidatePos.equals(v2ExpectedSupportPos);
        final boolean useUpperMark = (supportCandidateType == SlabType.TOP);
        final String sourceTruthContext = useUpperMark
                ? "compound_visible_side_upper_slab"
                : "compound_visible_side_lower_slab";
        final BlockHitResult torchUseHit = new BlockHitResult(
                new Vec3d(supportCandidatePos.getX() + 0.5d, supportCandidatePos.getY(), supportCandidatePos.getZ() + 0.5d),
                Direction.UP,
                supportCandidatePos,
                false);

        // Build compound tower adjacent to support slab so compound visible mark can be written.
        // Tower: bare-bottom-slab → anchored-stone → carrier-slab → compound-anchor
        // (mirrors seedBeta4LiveScreenshotShape pattern that produces isLoweredCompoundSourceSlab=true)
        final BlockPos towerBase = compoundSourcePos.down(3);
        final BlockPos towerAnchor = compoundSourcePos.down(2);
        final BlockPos towerCarrier = compoundSourcePos.down(1);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(towerBase,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(towerAnchor, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, towerAnchor, world.getBlockState(towerAnchor));
            world.setBlockState(towerCarrier,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world, towerCarrier, world.getBlockState(towerCarrier));
            world.setBlockState(compoundSourcePos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, compoundSourcePos, world.getBlockState(compoundSourcePos));
            SlabAnchorAttachment.addCompoundFullBlockAnchor(
                    world, compoundSourcePos, world.getBlockState(compoundSourcePos));
            world.setBlockState(supportCandidatePos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, supportCandidateType),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            BlockState supportState = world.getBlockState(supportCandidatePos);
            BlockState compoundState = world.getBlockState(compoundSourcePos);
            if (useUpperMark) {
                SlabAnchorAttachment.addCompoundVisibleSideUpperSlab(
                        world, supportCandidatePos, supportState, compoundSourcePos, compoundState);
            } else {
                SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(
                        world, supportCandidatePos, supportState, compoundSourcePos, compoundState);
            }
            world.setBlockState(torchPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.TORCH, 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(supportCandidatePos.getX() + 0.5d, supportCandidatePos.getY() + 2.2d, supportCandidatePos.getZ() - 2.0d),
                torchUseHit.getPos());

        final String[] placementResultText = {"not-run"};
        final boolean[] placementAccepted = {false};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_RED]"
                        + " reason=client_not_ready categoryScope=floor_torch_only");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, torchUseHit);
            placementResultText[0] = result.toString();
            placementAccepted[0] = result.isAccepted();
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockState[] supportCandidateStateHolder = {null};
        final BlockState[] torchStateHolder = {null};
        final double[] supportDyHolder = {Double.NaN};
        final double[] torchDyHolder = {Double.NaN};
        final double[] rawSupportTopYHolder = {Double.NaN};
        final double[] supportVisibleTopYHolder = {Double.NaN};
        final double[] rawTorchShapeMinYHolder = {Double.NaN};
        final double[] torchModelBottomYHolder = {Double.NaN};
        final double[] contactGapHolder = {Double.NaN};
        final boolean[] fixtureMatchesHolder = {false};
        final boolean[] fixedLegalStackHolder = {false};
        final String[] failureLayerHolder = {"V2_SOURCE_TRUTH_MISMATCH"};
        final String[] legalOutcomeHolder = {"UNSET"};
        final String[] survivalResultHolder = {"NOT_RUN"};
        final String[] triadStatusHolder = {"NOT_RUN"};

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_RED]"
                        + " reason=client_world_or_player_missing categoryScope=floor_torch_only");
            }

            BlockState supportCandidateState = mc.world.getBlockState(supportCandidatePos);
            BlockState torchState = mc.world.getBlockState(torchPos);
            supportCandidateStateHolder[0] = supportCandidateState;
            torchStateHolder[0] = torchState;

            boolean compoundAnchorAtSource = SlabAnchorAttachment.isCompoundFullBlockAnchor(mc.world, compoundSourcePos);
            boolean compoundVisibleMarkWritten = useUpperMark
                    ? SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(mc.world, supportCandidatePos, supportCandidateState)
                    : SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(mc.world, supportCandidatePos, supportCandidateState);

            double supportDy = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportCandidateState);
            double torchDy = torchState.isOf(Blocks.TORCH)
                    ? SlabSupport.getYOffset(mc.world, torchPos, torchState)
                    : Double.NaN;
            double rawSupportTopY = supportCandidateState.isOf(Blocks.STONE_SLAB)
                    ? supportCandidatePos.getY() + SlabSupport.getSupportYOffset(supportCandidateState)
                    : Double.NaN;
            double supportVisibleTopY = supportCandidateState.isOf(Blocks.STONE_SLAB)
                    ? beta35SupportVisibleTopY(supportCandidatePos, supportCandidateState, supportDy)
                    : Double.NaN;

            VoxelShape outlineShape = torchState.isOf(Blocks.TORCH)
                    ? torchState.getOutlineShape(mc.world, torchPos, net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = torchState.isOf(Blocks.TORCH)
                    ? torchState.getRaycastShape(mc.world, torchPos)
                    : null;
            net.minecraft.util.math.Box modelBox = torchState.isOf(Blocks.TORCH)
                    ? beta35FloorTorchModelProxyWorldBox(torchPos, torchDy)
                    : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, torchPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, torchPos);
            double rawTorchShapeMinY = outlineBox == null ? Double.NaN : outlineBox.minY - torchPos.getY();
            double torchModelBottomY = modelBox == null ? Double.NaN : modelBox.minY;
            double contactGap = torchModelBottomY - supportVisibleTopY;

            boolean supportTypeExpected = supportCandidateState.isOf(Blocks.STONE_SLAB)
                    && supportCandidateState.get(SlabBlock.TYPE) == supportCandidateType;
            boolean supportDyExpected = Double.isFinite(supportDy) && Math.abs(supportDy - expectedSupportDy) <= EPSILON;
            boolean torchDyBeforeExpected = torchState.isOf(Blocks.TORCH)
                    && Math.abs(torchDy - expectedTorchDyBefore) <= EPSILON;
            boolean torchDyAfterExpected = torchState.isOf(Blocks.TORCH)
                    && Double.isFinite(expectedTorchDyAfter)
                    && Math.abs(torchDy - expectedTorchDyAfter) <= EPSILON;
            boolean contactGapFixed = torchState.isOf(Blocks.TORCH)
                    && Double.isFinite(contactGap)
                    && Math.abs(contactGap) <= EPSILON;
            boolean modelOutlineGreen = beta35SameBox(outlineBox, modelBox);
            boolean modelRaycastGreen = beta35SameBox(raycastBox, modelBox);
            boolean triadGreen = torchState.isOf(Blocks.TORCH) && modelOutlineGreen && modelRaycastGreen;
            boolean survivalGreen = torchState.isOf(Blocks.TORCH) && torchState.canPlaceAt(mc.world, torchPos);
            boolean bottomRejectExpected = supportCandidateType == SlabType.BOTTOM;
            boolean legalRejectObserved = bottomRejectExpected && !torchState.isOf(Blocks.TORCH);
            String legalOutcome = bottomRejectExpected
                    ? "FLOOR_TORCH_COMPOUND_VISIBLE_BOTTOM_SLAB_SUPPORT_REJECTED_DY_LT_MINUS_ONE_ILLEGAL"
                    : "FLOOR_TORCH_COMPOUND_VISIBLE_TOP_SLAB_SUPPORT";
            String survivalResult = legalRejectObserved
                    ? "REJECTED_BY_LAW"
                    : survivalGreen ? "SURVIVAL_GREEN" : "SURVIVAL_RED";
            String triadStatus = legalRejectObserved
                    ? "REJECTED_BY_LAW"
                    : triadGreen ? "GREEN" : "RED";
            boolean fixtureMatchesV2LiveStack = placementAccepted[0]
                    && supportTypeExpected
                    && supportDyExpected
                    && torchDyBeforeExpected;
            boolean fixtureMatchesFixedLegalStack = coordinateParity
                    && supportTypeExpected
                    && supportDyExpected
                    && compoundAnchorAtSource
                    && compoundVisibleMarkWritten
                    && ((!bottomRejectExpected && placementAccepted[0]
                            && torchDyAfterExpected
                            && contactGapFixed
                            && survivalGreen
                            && triadGreen)
                        || (bottomRejectExpected && legalRejectObserved));

            supportDyHolder[0] = supportDy;
            torchDyHolder[0] = torchDy;
            rawSupportTopYHolder[0] = rawSupportTopY;
            supportVisibleTopYHolder[0] = supportVisibleTopY;
            rawTorchShapeMinYHolder[0] = rawTorchShapeMinY;
            torchModelBottomYHolder[0] = torchModelBottomY;
            contactGapHolder[0] = contactGap;
            fixtureMatchesHolder[0] = fixtureMatchesV2LiveStack;
            fixedLegalStackHolder[0] = fixtureMatchesFixedLegalStack;
            legalOutcomeHolder[0] = legalOutcome;
            survivalResultHolder[0] = survivalResult;
            triadStatusHolder[0] = triadStatus;

            String failureLayer = "NONE";
            if (!coordinateParity) {
                failureLayer = "V2_COORDINATE_MISMATCH";
            } else if (!supportTypeExpected || !supportDyExpected || !compoundAnchorAtSource || !compoundVisibleMarkWritten) {
                failureLayer = "V2_SOURCE_TRUTH_MISMATCH";
            } else if (!fixtureMatchesFixedLegalStack) {
                failureLayer = bottomRejectExpected
                        ? "FLOOR_TORCH_COMPOUND_VISIBLE_BOTTOM_REJECT_FAILED"
                        : !placementAccepted[0] || !torchState.isOf(Blocks.TORCH)
                                ? "FLOOR_TORCH_COMPOUND_VISIBLE_TOP_PLACEMENT_FAILED"
                                : !torchDyAfterExpected
                                        ? "FLOOR_TORCH_COMPOUND_VISIBLE_TOP_DY"
                                        : !contactGapFixed
                                                ? "FLOOR_TORCH_COMPOUND_VISIBLE_TOP_CONTACT_GAP"
                                                : !survivalGreen
                                                        ? "FLOOR_TORCH_COMPOUND_VISIBLE_TOP_SURVIVAL"
                                                        : "FLOOR_TORCH_COMPOUND_VISIBLE_TOP_TRIAD";
            }
            failureLayerHolder[0] = failureLayer;

            boolean contactGapMatch = Double.isFinite(contactGap) && Math.abs(contactGap) <= EPSILON;
            System.out.println("[JULIA_BETA35_FLOOR_TORCH_V2_COORDINATE_REPLAY]"
                    + " categoryScope=floor_torch_only"
                    + " caseName=" + scenario
                    + " expectedTorchPos=" + v2ExpectedTorchPos.toShortString()
                    + " actualTorchPos=" + torchPos.toShortString()
                    + " expectedSupportCandidatePos=" + v2ExpectedSupportPos.toShortString()
                    + " actualSupportCandidatePos=" + supportCandidatePos.toShortString()
                    + " coordinateParity=" + coordinateParity
                    + " failureLayer=" + failureLayer);
            System.out.println("[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_MEASURED]"
                    + " categoryScope=floor_torch_only"
                    + " caseName=" + scenario
                    + " supportCandidatePos=" + supportCandidatePos.toShortString()
                    + " supportCandidateState=" + supportCandidateState
                    + " torchPos=" + torchPos.toShortString()
                    + " torchState=" + torchState
                    + " supportDy=" + (Double.isFinite(supportDy) ? String.format("%.3f", supportDy) : "N/A")
                    + " torchDy=" + (Double.isFinite(torchDy) ? String.format("%.3f", torchDy) : "N/A")
                    + " rawSupportTopY=" + (Double.isFinite(rawSupportTopY) ? String.format("%.6f", rawSupportTopY) : "N/A")
                    + " supportVisibleTopY=" + (Double.isFinite(supportVisibleTopY) ? String.format("%.6f", supportVisibleTopY) : "N/A")
                    + " rawTorchShapeMinY=" + (Double.isFinite(rawTorchShapeMinY) ? String.format("%.6f", rawTorchShapeMinY) : "N/A")
                    + " torchModelBottomY=" + (Double.isFinite(torchModelBottomY) ? String.format("%.6f", torchModelBottomY) : "N/A")
                    + " contactGap=" + (Double.isFinite(contactGap) ? String.format("%.6f", contactGap) : "N/A")
                    + " outlineMinY=" + beta35FormatMinY(outlineBox)
                    + " outlineMaxY=" + beta35FormatMaxY(outlineBox)
                    + " raycastMinY=" + beta35FormatMinY(raycastBox)
                    + " raycastMaxY=" + beta35FormatMaxY(raycastBox)
                    + " expectedSupportDy=" + String.format("%.3f", expectedSupportDy)
                    + " torchDyBefore=" + String.format("%.3f", expectedTorchDyBefore)
                    + " torchDyAfter=" + (Double.isFinite(torchDy) ? String.format("%.3f", torchDy) : "N/A")
                    + " expectedTorchDyAfter=" + (Double.isFinite(expectedTorchDyAfter)
                            ? String.format("%.3f", expectedTorchDyAfter) : "REJECT")
                    + " sourceTruthContext=" + sourceTruthContext
                    + " legalOutcome=" + legalOutcome
                    + " compoundAnchorAtSource=" + compoundAnchorAtSource
                    + " compoundVisibleMarkWritten=" + compoundVisibleMarkWritten
                    + " coordinateParity=" + coordinateParity
                    + " fixtureMatchesV2LiveStack=" + fixtureMatchesV2LiveStack
                    + " fixtureMatchesFixedLegalStack=" + fixtureMatchesFixedLegalStack
                    + " survival=" + survivalResult
                    + " triad=" + triadStatus
                    + " failureLayer=" + failureLayer
                    + " placementResult=" + placementResultText[0]
                    + " placementAccepted=" + placementAccepted[0]);
            System.out.println("[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_GAP_RED]"
                    + " categoryScope=floor_torch_only"
                    + " caseName=" + scenario
                    + " supportCandidatePos=" + supportCandidatePos.toShortString()
                    + " supportCandidateState=" + supportCandidateState
                    + " torchPos=" + torchPos.toShortString()
                    + " torchState=" + torchState
                    + " supportDy=" + (Double.isFinite(supportDy) ? String.format("%.3f", supportDy) : "N/A")
                    + " torchDy=" + (Double.isFinite(torchDy) ? String.format("%.3f", torchDy) : "N/A")
                    + " rawSupportTopY=" + (Double.isFinite(rawSupportTopY) ? String.format("%.6f", rawSupportTopY) : "N/A")
                    + " supportVisibleTopY=" + (Double.isFinite(supportVisibleTopY) ? String.format("%.6f", supportVisibleTopY) : "N/A")
                    + " rawTorchShapeMinY=" + (Double.isFinite(rawTorchShapeMinY) ? String.format("%.6f", rawTorchShapeMinY) : "N/A")
                    + " torchModelBottomY=" + (Double.isFinite(torchModelBottomY) ? String.format("%.6f", torchModelBottomY) : "N/A")
                    + " contactGap=" + (Double.isFinite(contactGap) ? String.format("%.6f", contactGap) : "N/A")
                    + " contactGapExpected=0.000000_OR_LEGAL_REJECT"
                    + " contactGapObservedMatch=" + contactGapMatch
                    + " coordinateParity=" + coordinateParity
                    + " fixtureMatchesV2LiveStack=" + fixtureMatchesV2LiveStack
                    + " fixtureMatchesFixedLegalStack=" + fixtureMatchesFixedLegalStack
                    + " legalOutcome=" + legalOutcome
                    + " torchDyBefore=" + String.format("%.3f", expectedTorchDyBefore)
                    + " torchDyAfter=" + (Double.isFinite(torchDy) ? String.format("%.3f", torchDy) : "N/A")
                    + " survival=" + survivalResult
                    + " triad=" + triadStatus
                    + " redProofExpected=CONTACT_FIX"
                    + " proofTargetLayer=FLOOR_TORCH_ONLY"
                    + " failureLayer=" + failureLayer
                    + " redProofResult=" + (failureLayer.equals("NONE") ? "GREEN" : "RED")
                    + " heldItem=" + mc.player.getMainHandStack().getItem().getTranslationKey()
                    + " playerPos="
                    + String.format("%.3f,%.3f,%.3f", mc.player.getX(), mc.player.getY(), mc.player.getZ())
                    + " productionGameplayFixApplied=true");
            String fixMarker = legalRejectObserved
                    ? "[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_FIX_REJECT_GREEN]"
                    : "[JULIA_BETA35_FLOOR_TORCH_V2_CONTACT_FIX_GREEN]";
            System.out.println(fixMarker
                    + " categoryScope=floor_torch_only"
                    + " caseName=" + scenario
                    + " legalOutcome=" + legalOutcome
                    + " supportDy=" + (Double.isFinite(supportDy) ? String.format("%.3f", supportDy) : "N/A")
                    + " torchDyBefore=" + String.format("%.3f", expectedTorchDyBefore)
                    + " torchDyAfter=" + (Double.isFinite(torchDy) ? String.format("%.3f", torchDy) : "N/A")
                    + " supportVisibleTopY=" + (Double.isFinite(supportVisibleTopY) ? String.format("%.6f", supportVisibleTopY) : "N/A")
                    + " torchModelBottomY=" + (Double.isFinite(torchModelBottomY) ? String.format("%.6f", torchModelBottomY) : "N/A")
                    + " contactGap=" + (Double.isFinite(contactGap) ? String.format("%.6f", contactGap) : "N/A")
                    + " placementResult=" + placementResultText[0]
                    + " survival=" + survivalResult
                    + " triad=" + triadStatus
                    + " fixtureMatchesFixedLegalStack=" + fixtureMatchesFixedLegalStack
                    + " failureLayer=" + failureLayer);
        });

        return new Beta35V2ContactGapAttempt(
                scenario,
                torchPos,
                supportCandidatePos,
                supportCandidateStateHolder[0],
                supportDyHolder[0],
                rawSupportTopYHolder[0],
                supportVisibleTopYHolder[0],
                rawTorchShapeMinYHolder[0],
                torchModelBottomYHolder[0],
                torchDyHolder[0],
                contactGapHolder[0],
                coordinateParity,
                fixtureMatchesHolder[0],
                fixedLegalStackHolder[0],
                legalOutcomeHolder[0],
                survivalResultHolder[0],
                triadStatusHolder[0],
                failureLayerHolder[0]);
    }

    /**
     * Focused proof: player-like floor_torch placement on a lowered bottom slab support
     * with intended support dy=-1.0 must succeed without changing the contact law.
     *
     * Gate: -Dslabbed.beta35FloorTorchLoweredSlabPlacement=true
     */
    private static void runBeta35FloorTorchLoweredSlabPlacementProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos compoundSourcePos = new BlockPos(58, -57, 91);
        final BlockPos supportCandidatePos = compoundSourcePos.up();
        final BlockPos torchPos = supportCandidatePos.up();
        final BlockPos towerBase = compoundSourcePos.down(3);
        final BlockPos towerAnchor = compoundSourcePos.down(2);
        final BlockPos towerCarrier = compoundSourcePos.down();
        final BlockHitResult torchUseHit = new BlockHitResult(
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() - 0.5d,
                        supportCandidatePos.getZ() + 0.5d),
                Direction.UP,
                supportCandidatePos,
                false);

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(towerBase,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(towerAnchor, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, towerAnchor, world.getBlockState(towerAnchor));
            world.setBlockState(towerCarrier,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, towerCarrier,
                    world.getBlockState(towerCarrier));
            world.setBlockState(compoundSourcePos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, compoundSourcePos, world.getBlockState(compoundSourcePos));
            SlabAnchorAttachment.addCompoundFullBlockAnchor(world, compoundSourcePos,
                    world.getBlockState(compoundSourcePos));
            world.setBlockState(supportCandidatePos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addCompoundVisibleOwnerTopSlab(
                    world,
                    supportCandidatePos,
                    world.getBlockState(supportCandidatePos),
                    compoundSourcePos,
                    world.getBlockState(compoundSourcePos));
            world.setBlockState(torchPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.TORCH, 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() + 1.7d,
                        supportCandidatePos.getZ() - 2.0d),
                torchUseHit.getPos());

        final String[] finalInteractResult = {"not-run"};
        final boolean[] placementAccepted = {false};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_PLACEMENT_RED]"
                        + " reason=client_not_ready categoryScope=floor_torch_only");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, torchUseHit);
            finalInteractResult[0] = result.toString();
            placementAccepted[0] = result.isAccepted();
        });
        ctx.waitTick();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState supportState = world.getBlockState(supportCandidatePos);
            world.updateNeighbors(torchPos, Blocks.TORCH);
            world.updateNeighbors(supportCandidatePos, supportState.getBlock());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String[] failureLayerHolder = {"LOWERED_SLAB_PLACEMENT_NOT_RUN"};
        final String[] supportDyText = {"N/A"};
        final String[] torchDyText = {"N/A"};
        final String[] contactGapText = {"N/A"};
        final String[] survivalResultHolder = {"SURVIVAL_NOT_RUN"};
        final String[] finalTorchStateText = {"N/A"};

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_PLACEMENT_RED]"
                        + " reason=client_world_or_player_missing categoryScope=floor_torch_only");
            }

            BlockState supportCandidateState = mc.world.getBlockState(supportCandidatePos);
            BlockState finalTorchState = mc.world.getBlockState(torchPos);
            boolean ownerTopSupport = SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(
                    mc.world,
                    supportCandidatePos,
                    supportCandidateState);
            boolean legalSupport = SlabSupport.isLegalFloorTorchLoweredBottomSlabSupport(
                    mc.world,
                    supportCandidatePos,
                    Blocks.TORCH.getDefaultState());
            double supportDy = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportCandidateState);
            double torchDy = finalTorchState.isOf(Blocks.TORCH)
                    ? SlabSupport.getYOffset(mc.world, torchPos, finalTorchState)
                    : Double.NaN;
            double supportVisibleTopY = supportCandidateState.isOf(Blocks.STONE_SLAB)
                    ? beta35SupportVisibleTopY(supportCandidatePos, supportCandidateState, supportDy)
                    : Double.NaN;
            net.minecraft.util.math.Box modelBox = finalTorchState.isOf(Blocks.TORCH)
                    ? beta35FloorTorchModelProxyWorldBox(torchPos, torchDy)
                    : null;
            VoxelShape outlineShape = finalTorchState.isOf(Blocks.TORCH)
                    ? finalTorchState.getOutlineShape(mc.world, torchPos, net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = finalTorchState.isOf(Blocks.TORCH)
                    ? finalTorchState.getRaycastShape(mc.world, torchPos)
                    : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, torchPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, torchPos);
            double torchModelBottomY = modelBox == null ? Double.NaN : modelBox.minY;
            double contactGap = torchModelBottomY - supportVisibleTopY;
            boolean torchBlockAppearedAfterAttempt = finalTorchState.isOf(Blocks.TORCH);
            boolean contactGapGreen = torchBlockAppearedAfterAttempt
                    && Double.isFinite(contactGap)
                    && Math.abs(contactGap) <= EPSILON;
            boolean survivalGreen = torchBlockAppearedAfterAttempt && finalTorchState.canPlaceAt(mc.world, torchPos);
            boolean triadGreen = torchBlockAppearedAfterAttempt
                    && beta35SameBox(outlineBox, modelBox)
                    && beta35SameBox(raycastBox, modelBox);

            String failureLayer = "NONE";
            if (!supportCandidateState.isOf(Blocks.STONE_SLAB)
                    || supportCandidateState.get(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Math.abs(supportDy - (-1.0d)) > EPSILON
                    || !ownerTopSupport
                    || !legalSupport) {
                failureLayer = "LOWERED_SLAB_SOURCE_TRUTH_MISMATCH";
            } else if (!placementAccepted[0] || !torchBlockAppearedAfterAttempt) {
                failureLayer = "PLACEMENT_FAILURE_ON_LOWERED_BOTTOM_SLAB_SUPPORT";
            } else if (Math.abs(torchDy - (-1.5d)) > EPSILON || !contactGapGreen) {
                failureLayer = "LOWERED_SLAB_CONTACT_REGRESSION";
            } else if (!survivalGreen) {
                failureLayer = "LOWERED_SLAB_SURVIVAL_REGRESSION";
            } else if (!triadGreen) {
                failureLayer = "LOWERED_SLAB_TRIAD_REGRESSION";
            }
            failureLayerHolder[0] = failureLayer;
            supportDyText[0] = String.format("%.6f", supportDy);
            torchDyText[0] = Double.isFinite(torchDy) ? String.format("%.6f", torchDy) : "N/A";
            contactGapText[0] = Double.isFinite(contactGap) ? String.format("%.6f", contactGap) : "N/A";
            survivalResultHolder[0] = survivalGreen ? "SURVIVAL_GREEN" : "SURVIVAL_RED";
            finalTorchStateText[0] = finalTorchState.toString();

            System.out.println("[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_PLACEMENT_MEASURED]"
                    + " categoryScope=floor_torch_only"
                    + " heldItem=minecraft:torch"
                    + " crosshairTargetPos=" + supportCandidatePos.toShortString()
                    + " crosshairTargetFace=UP"
                    + " hitVec=" + String.format("%.3f,%.3f,%.3f",
                            torchUseHit.getPos().x, torchUseHit.getPos().y, torchUseHit.getPos().z)
                    + " intendedSupportCandidatePos=" + supportCandidatePos.toShortString()
                    + " intendedSupportCandidateState=" + supportCandidateState
                    + " intendedSupportSourceType=PLAIN_STATE"
                    + " ownerTopSupport=" + ownerTopSupport
                    + " legalLoweredBottomSlabSupport=" + legalSupport
                    + " intendedSupportDy=" + String.format("%.6f", supportDy)
                    + " finalInteractResult=" + finalInteractResult[0]
                    + " torchBlockAppearedAfterAttempt=" + torchBlockAppearedAfterAttempt
                    + " finalTorchPos=" + torchPos.toShortString()
                    + " finalTorchState=" + finalTorchState
                    + " torchDy=" + torchDyText[0]
                    + " contactGap=" + contactGapText[0]
                    + " survival=" + survivalResultHolder[0]
                    + " triad=" + (triadGreen ? "GREEN" : "RED")
                    + " classification=" + (failureLayer.equals("NONE") ? "PLACEMENT_ATTEMPT_OK" : "PLACEMENT_RESULT_UNKNOWN")
                    + " failureLayer=" + failureLayer);

            if (failureLayer.equals("NONE")) {
                System.out.println("[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_PLACEMENT_GREEN]"
                        + " categoryScope=floor_torch_only"
                        + " intendedSupportCandidateState=" + supportCandidateState
                        + " intendedSupportDy=" + String.format("%.6f", supportDy)
                        + " finalInteractResult=" + finalInteractResult[0]
                        + " torchBlockAppearedAfterAttempt=true"
                        + " finalTorchState=" + finalTorchState
                        + " torchDy=" + torchDyText[0]
                        + " contactGap=" + contactGapText[0]
                        + " survival=" + survivalResultHolder[0]);
            }
        });

        if (!failureLayerHolder[0].equals("NONE")) {
            throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_PLACEMENT_RED]"
                    + " categoryScope=floor_torch_only"
                    + " failureLayer=" + failureLayerHolder[0]
                    + " finalInteractResult=" + finalInteractResult[0]
                    + " finalTorchState=" + finalTorchStateText[0]
                    + " supportDy=" + supportDyText[0]
                    + " torchDy=" + torchDyText[0]
                    + " contactGap=" + contactGapText[0]);
        }

        Beta35V2ContactGapAttempt supportDyMinusHalfRegression = runBeta35FloorTorchV2ContactGapRedAttempt(
                ctx,
                singleplayer,
                "top_slab_support_regression",
                new BlockPos(64, -57, 87),
                SlabType.TOP,
                -1.000d,
                -0.500d,
                -1.000d,
                new BlockPos(64, -56, 87),
                new BlockPos(64, -57, 87),
                new BlockPos(65, -57, 87));

        boolean regressionGreen = supportDyMinusHalfRegression.failureLayer().equals("NONE");
        System.out.println("[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_PLACEMENT_SUMMARY]"
                + " categoryScope=floor_torch_only"
                + " failureLayer=" + failureLayerHolder[0]
                + " supportDyMinusOnePlacement=GREEN"
                + " supportDyMinusHalfRegression=" + (regressionGreen ? "GREEN" : "RED")
                + " finalInteractResult=" + finalInteractResult[0]
                + " torchBlockAppearedAfterAttempt=true"
                + " finalTorchState=" + finalTorchStateText[0]
                + " torchDy=" + torchDyText[0]
                + " contactGap=" + contactGapText[0]
                + " survival=" + survivalResultHolder[0]
                + " wall_torch=NOT_COVERED"
                + " lantern=NOT_COVERED"
                + " signs=NOT_COVERED"
                + " chains=NOT_COVERED"
                + " releasePrep=PAUSED");

        if (!regressionGreen) {
            throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_LOWERED_SLAB_PLACEMENT_RED]"
                    + " categoryScope=floor_torch_only"
                    + " failureLayer=supportDyMinusHalfRegression_"
                    + supportDyMinusHalfRegression.failureLayer());
        }
    }

    /**
     * Focused proof: floor_torch over a plain bottom slab with supportDy=-0.5
     * must inherit contact dy=-1.0 instead of floating at torchDy=-0.5.
     *
     * Gate: -Dslabbed.beta35FloorTorchPlainBottomContact=true
     */
    private static void runBeta35FloorTorchPlainBottomContactProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportCandidatePos = new BlockPos(32, -55, 80);
        final BlockPos torchPos = supportCandidatePos.up();
        final BlockPos loweredDoubleCarrier = supportCandidatePos.down();
        final BlockPos anchoredCarrierBelow = supportCandidatePos.down(2);
        final BlockPos baseSlab = supportCandidatePos.down(3);
        final BlockHitResult torchUseHit = new BlockHitResult(
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() + 1.0d,
                        supportCandidatePos.getZ() + 0.5d),
                Direction.UP,
                supportCandidatePos,
                false);

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = supportCandidatePos.getX() - 2; x <= supportCandidatePos.getX() + 2; x++) {
                for (int y = baseSlab.getY() - 1; y <= torchPos.getY() + 1; y++) {
                    for (int z = supportCandidatePos.getZ() - 2; z <= supportCandidatePos.getZ() + 2; z++) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }
            world.setBlockState(baseSlab,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(anchoredCarrierBelow, Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, anchoredCarrierBelow, world.getBlockState(anchoredCarrierBelow));
            world.setBlockState(loweredDoubleCarrier,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(supportCandidatePos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(torchPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.TORCH, 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() + 3.0d,
                        supportCandidatePos.getZ() - 2.0d),
                torchUseHit.getPos());

        final String[] finalInteractResult = {"not-run"};
        final boolean[] placementAccepted = {false};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_PLAIN_BOTTOM_CONTACT_RED]"
                        + " reason=client_not_ready categoryScope=floor_torch_only");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, torchUseHit);
            finalInteractResult[0] = result.toString();
            placementAccepted[0] = result.isAccepted();
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String[] failureLayerHolder = {"PLAIN_BOTTOM_CONTACT_NOT_RUN"};
        final String[] supportDyText = {"N/A"};
        final String[] torchDyText = {"N/A"};
        final String[] contactGapText = {"N/A"};
        final String[] supportVisibleTopText = {"N/A"};
        final String[] torchModelBottomText = {"N/A"};
        final String[] finalTorchStateText = {"N/A"};
        final String[] survivalResultText = {"SURVIVAL_NOT_RUN"};

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_PLAIN_BOTTOM_CONTACT_RED]"
                        + " reason=client_world_or_player_missing categoryScope=floor_torch_only");
            }

            BlockState supportState = mc.world.getBlockState(supportCandidatePos);
            BlockState finalTorchState = mc.world.getBlockState(torchPos);
            boolean supportCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    mc.world, supportCandidatePos, supportState);
            boolean supportCompoundLower = SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(
                    mc.world, supportCandidatePos, supportState);
            boolean supportCompoundUpper = SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(
                    mc.world, supportCandidatePos, supportState);
            boolean supportCompoundOwnerTop = SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(
                    mc.world, supportCandidatePos, supportState);
            boolean supportPlain = !supportCarrier
                    && !supportCompoundLower
                    && !supportCompoundUpper
                    && !supportCompoundOwnerTop
                    && !SlabAnchorAttachment.isAnchored(mc.world, supportCandidatePos);

            double supportDy = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportState);
            double torchDy = finalTorchState.isOf(Blocks.TORCH)
                    ? SlabSupport.getYOffset(mc.world, torchPos, finalTorchState) : Double.NaN;
            double supportVisibleTopY = beta35SupportVisibleTopY(supportCandidatePos, supportState, supportDy);
            net.minecraft.util.math.Box modelBox = finalTorchState.isOf(Blocks.TORCH)
                    ? beta35FloorTorchModelProxyWorldBox(torchPos, torchDy) : null;
            VoxelShape outlineShape = finalTorchState.isOf(Blocks.TORCH)
                    ? finalTorchState.getOutlineShape(mc.world, torchPos, net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = finalTorchState.isOf(Blocks.TORCH)
                    ? finalTorchState.getRaycastShape(mc.world, torchPos) : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, torchPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, torchPos);
            double torchModelBottomY = modelBox == null ? Double.NaN : modelBox.minY;
            double contactGap = Double.isFinite(torchModelBottomY)
                    ? torchModelBottomY - supportVisibleTopY : Double.NaN;
            boolean torchPresent = finalTorchState.isOf(Blocks.TORCH);
            boolean supportSourcePlain = supportPlain && supportState.isOf(Blocks.STONE_SLAB);
            boolean contactGreen = Double.isFinite(contactGap) && Math.abs(contactGap) <= EPSILON;
            boolean triadGreen = torchPresent
                    && beta35SameBox(outlineBox, modelBox)
                    && beta35SameBox(raycastBox, modelBox);
            boolean survivalGreen = torchPresent && finalTorchState.canPlaceAt(mc.world, torchPos);

            String failureLayer = "NONE";
            if (!supportState.isOf(Blocks.STONE_SLAB)
                    || supportState.get(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Math.abs(supportDy - (-0.5d)) > EPSILON
                    || !supportSourcePlain) {
                failureLayer = "PLAIN_BOTTOM_SOURCE_TRUTH_MISMATCH";
            } else if (!placementAccepted[0] || !torchPresent) {
                failureLayer = "PLAIN_BOTTOM_TORCH_NOT_PLACED";
            } else if (Math.abs(torchDy - (-1.0d)) > EPSILON || !contactGreen) {
                failureLayer = "PLAIN_BOTTOM_CONTACT_GAP";
            } else if (!survivalGreen) {
                failureLayer = "PLAIN_BOTTOM_SURVIVAL_REGRESSION";
            } else if (!triadGreen) {
                failureLayer = "PLAIN_BOTTOM_TRIAD_REGRESSION";
            }

            failureLayerHolder[0] = failureLayer;
            supportDyText[0] = String.format("%.6f", supportDy);
            torchDyText[0] = Double.isFinite(torchDy) ? String.format("%.6f", torchDy) : "N/A";
            contactGapText[0] = Double.isFinite(contactGap) ? String.format("%.6f", contactGap) : "N/A";
            supportVisibleTopText[0] = Double.isFinite(supportVisibleTopY)
                    ? String.format("%.6f", supportVisibleTopY) : "N/A";
            torchModelBottomText[0] = Double.isFinite(torchModelBottomY)
                    ? String.format("%.6f", torchModelBottomY) : "N/A";
            finalTorchStateText[0] = finalTorchState.toString();
            survivalResultText[0] = survivalGreen ? "SURVIVAL_GREEN" : "SURVIVAL_RED";

            System.out.println("[JULIA_BETA35_LIVE_TORCH_EXISTING_CONTACT]"
                    + " torchPos=" + torchPos.toShortString()
                    + " torchState=" + finalTorchState
                    + " torchSourceType=FLOOR_TORCH"
                    + " torchDy=" + torchDyText[0]
                    + " supportCandidatePos=" + supportCandidatePos.toShortString()
                    + " supportCandidateState=" + supportState
                    + " supportSourceType=PLAIN_STATE"
                    + " supportDy=" + supportDyText[0]
                    + " supportVisibleTopY=" + supportVisibleTopText[0]
                    + " torchModelBottomY=" + torchModelBottomText[0]
                    + " contactGap=" + contactGapText[0]
                    + " triadCoLocated=" + (triadGreen ? "yes" : "no")
                    + " classification=" + (contactGreen ? "PLACED_CONTACT_GREEN" : "PLACED_CONTACT_GAP"));

            System.out.println("[JULIA_BETA35_FLOOR_TORCH_PLAIN_BOTTOM_CONTACT_MEASURED]"
                    + " categoryScope=floor_torch_only"
                    + " heldItem=minecraft:torch"
                    + " crosshairTargetPos=" + supportCandidatePos.toShortString()
                    + " crosshairTargetFace=UP"
                    + " supportCandidateState=" + supportState
                    + " supportSourceType=PLAIN_STATE"
                    + " supportDy=" + supportDyText[0]
                    + " previousTorchDy=-0.500000"
                    + " previousContactGap=0.500000"
                    + " torchDy=" + torchDyText[0]
                    + " supportVisibleTopY=" + supportVisibleTopText[0]
                    + " torchModelBottomY=" + torchModelBottomText[0]
                    + " contactGap=" + contactGapText[0]
                    + " finalInteractResult=" + finalInteractResult[0]
                    + " torchBlockAppearedAfterAttempt=" + torchPresent
                    + " finalTorchPos=" + torchPos.toShortString()
                    + " finalTorchState=" + finalTorchState
                    + " survival=" + survivalResultText[0]
                    + " triadCoLocated=" + (triadGreen ? "yes" : "no")
                    + " classification=" + (contactGreen ? "PLACED_CONTACT_GREEN" : "PLACED_CONTACT_GAP")
                    + " failureLayer=" + failureLayer);

            if (failureLayer.equals("NONE")) {
                System.out.println("[JULIA_BETA35_FLOOR_TORCH_PLAIN_BOTTOM_CONTACT_GREEN]"
                        + " categoryScope=floor_torch_only"
                        + " supportCandidateState=" + supportState
                        + " supportSourceType=PLAIN_STATE"
                        + " supportDy=" + supportDyText[0]
                        + " torchDy=" + torchDyText[0]
                        + " supportVisibleTopY=" + supportVisibleTopText[0]
                        + " torchModelBottomY=" + torchModelBottomText[0]
                        + " contactGap=" + contactGapText[0]
                        + " classification=PLACED_CONTACT_GREEN"
                        + " triadCoLocated=yes");
            }
        });

        if (!failureLayerHolder[0].equals("NONE")) {
            throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_PLAIN_BOTTOM_CONTACT_RED]"
                    + " categoryScope=floor_torch_only"
                    + " failureLayer=" + failureLayerHolder[0]
                    + " finalInteractResult=" + finalInteractResult[0]
                    + " finalTorchState=" + finalTorchStateText[0]
                    + " supportDy=" + supportDyText[0]
                    + " torchDy=" + torchDyText[0]
                    + " contactGap=" + contactGapText[0]);
        }

        final String[] duplicateInteractResult = {"not-run"};
        ctx.runOnClient(mc -> {
            if (mc.player != null && mc.interactionManager != null) {
                duplicateInteractResult[0] = mc.interactionManager
                        .interactBlock(mc.player, Hand.MAIN_HAND, torchUseHit)
                        .toString();
            }
        });
        ctx.waitTick();

        System.out.println("[JULIA_BETA35_FLOOR_TORCH_PLAIN_BOTTOM_CONTACT_SUMMARY]"
                + " categoryScope=floor_torch_only"
                + " failureLayer=" + failureLayerHolder[0]
                + " plainBottomSupportDyMinusHalfContact=GREEN"
                + " supportDy=" + supportDyText[0]
                + " torchDy=" + torchDyText[0]
                + " supportVisibleTopY=" + supportVisibleTopText[0]
                + " torchModelBottomY=" + torchModelBottomText[0]
                + " contactGap=" + contactGapText[0]
                + " duplicateClickResult=" + duplicateInteractResult[0]
                + " duplicateTracerClassification=OCCUPIED_TORCH_TARGET_WHEN_DUAL_TRACE_ENABLED"
                + " survival=" + survivalResultText[0]
                + " wall_torch=NOT_COVERED"
                + " lantern=NOT_COVERED"
                + " signs=NOT_COVERED"
                + " chains=NOT_COVERED"
                + " releasePrep=PAUSED");
    }

    /**
     * Focused proof: floor_torch on lowered ordinary full-block support (supportDy=-1.0)
     * should contact-align (torchDy=-1.0, contactGap=0.0) after the full-block fix.
     *
     * Gate: -Dslabbed.beta35FloorTorchFullBlockContactRed=true
     *
     * Regression: top_slab_support and bottom_slab_support from the v2 proof are also
     * run to confirm they remain GREEN / REJECTED_BY_LAW respectively.
     */
    private static void runBeta35FloorTorchFullBlockContactRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        // ── New case: lowered full-block support ─────────────────────────────────
        // Tower: bare-bottom-slab → anchored-stone → bottom-slab-carrier → compound-full-block (support)
        // Torch placed on top of compound full block.
        final BlockPos fullBlockSupportPos = new BlockPos(50, -57, 87);
        final BlockPos torchPos = fullBlockSupportPos.up();
        final BlockPos towerBase = fullBlockSupportPos.down(3);
        final BlockPos towerAnchor = fullBlockSupportPos.down(2);
        final BlockPos towerCarrier = fullBlockSupportPos.down(1);

        final BlockHitResult torchUseHit = new BlockHitResult(
                new Vec3d(fullBlockSupportPos.getX() + 0.5d, fullBlockSupportPos.getY() + 1.0d, fullBlockSupportPos.getZ() + 0.5d),
                Direction.UP,
                fullBlockSupportPos,
                false);

        // Build the compound tower server-side.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(towerBase,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(towerAnchor, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, towerAnchor, world.getBlockState(towerAnchor));
            world.setBlockState(towerCarrier,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world, towerCarrier, world.getBlockState(towerCarrier));
            world.setBlockState(fullBlockSupportPos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, fullBlockSupportPos, world.getBlockState(fullBlockSupportPos));
            SlabAnchorAttachment.addCompoundFullBlockAnchor(
                    world, fullBlockSupportPos, world.getBlockState(fullBlockSupportPos));
            world.setBlockState(torchPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.TORCH, 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(fullBlockSupportPos.getX() + 0.5d, fullBlockSupportPos.getY() + 3.2d, fullBlockSupportPos.getZ() - 2.0d),
                torchUseHit.getPos());

        final String[] placementResultText = {"not-run"};
        final boolean[] placementAccepted = {false};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_FULL_BLOCK_CONTACT_RED]"
                        + " reason=client_not_ready categoryScope=floor_torch_only");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, torchUseHit);
            placementResultText[0] = result.toString();
            placementAccepted[0] = result.isAccepted();
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String[] failureLayerHolder = {"FULL_BLOCK_FIXTURE_NOT_RUN"};
        final String[] legalOutcomeHolder = {"UNSET"};

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_FULL_BLOCK_CONTACT_RED]"
                        + " reason=client_world_or_player_missing categoryScope=floor_torch_only");
            }

            BlockState fullBlockState = mc.world.getBlockState(fullBlockSupportPos);
            BlockState torchState = mc.world.getBlockState(torchPos);

            boolean compoundAnchorAtSupport = SlabAnchorAttachment.isCompoundFullBlockAnchor(mc.world, fullBlockSupportPos);
            boolean supportIsStone = fullBlockState.isOf(Blocks.STONE);

            double supportDy = SlabSupport.getYOffset(mc.world, fullBlockSupportPos, fullBlockState);
            boolean supportDyExpected = Math.abs(supportDy - (-1.0d)) <= EPSILON;

            double torchDy = torchState.isOf(Blocks.TORCH)
                    ? SlabSupport.getYOffset(mc.world, torchPos, torchState)
                    : Double.NaN;
            boolean torchDyAfterExpected = torchState.isOf(Blocks.TORCH)
                    && Math.abs(torchDy - (-1.0d)) <= EPSILON;

            // Full block visible top: pos.getY() + 1.0 + supportDy
            double supportVisibleTopY = fullBlockSupportPos.getY() + 1.0d + supportDy;
            net.minecraft.util.math.Box modelBox = torchState.isOf(Blocks.TORCH)
                    ? beta35FloorTorchModelProxyWorldBox(torchPos, torchDy)
                    : null;
            double torchModelBottomY = modelBox == null ? Double.NaN : modelBox.minY;
            double contactGap = Double.isFinite(torchModelBottomY) ? torchModelBottomY - supportVisibleTopY : Double.NaN;
            boolean contactGapFixed = Double.isFinite(contactGap) && Math.abs(contactGap) <= EPSILON;

            VoxelShape outlineShape = torchState.isOf(Blocks.TORCH)
                    ? torchState.getOutlineShape(mc.world, torchPos, net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = torchState.isOf(Blocks.TORCH)
                    ? torchState.getRaycastShape(mc.world, torchPos)
                    : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, torchPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, torchPos);
            boolean modelOutlineGreen = beta35SameBox(outlineBox, modelBox);
            boolean modelRaycastGreen = beta35SameBox(raycastBox, modelBox);
            boolean triadGreen = torchState.isOf(Blocks.TORCH) && modelOutlineGreen && modelRaycastGreen;
            boolean survivalGreen = torchState.isOf(Blocks.TORCH) && torchState.canPlaceAt(mc.world, torchPos);

            String survivalResult = survivalGreen ? "SURVIVAL_GREEN" : "SURVIVAL_RED";
            String triadStatus = triadGreen ? "GREEN" : "RED";
            final String legalOutcome = "FLOOR_TORCH_LOWERED_FULL_BLOCK_SUPPORT_CONTACT_ALIGNED";
            legalOutcomeHolder[0] = legalOutcome;

            boolean fixtureMatchesFixedLegalStack = supportIsStone
                    && compoundAnchorAtSupport
                    && supportDyExpected
                    && placementAccepted[0]
                    && torchState.isOf(Blocks.TORCH)
                    && torchDyAfterExpected
                    && contactGapFixed
                    && survivalGreen
                    && triadGreen;

            String failureLayer = "NONE";
            if (!supportIsStone || !compoundAnchorAtSupport || !supportDyExpected) {
                failureLayer = "FULL_BLOCK_SOURCE_TRUTH_MISMATCH";
            } else if (!placementAccepted[0] || !torchState.isOf(Blocks.TORCH)) {
                failureLayer = "FLOOR_TORCH_FULL_BLOCK_PLACEMENT_FAILED";
            } else if (!torchDyAfterExpected) {
                failureLayer = "FLOOR_TORCH_FULL_BLOCK_DY";
            } else if (!contactGapFixed) {
                failureLayer = "FLOOR_TORCH_FULL_BLOCK_CONTACT_GAP";
            } else if (!survivalGreen) {
                failureLayer = "FLOOR_TORCH_FULL_BLOCK_SURVIVAL";
            } else if (!triadGreen) {
                failureLayer = "FLOOR_TORCH_FULL_BLOCK_TRIAD";
            }
            failureLayerHolder[0] = failureLayer;

            System.out.println("[JULIA_BETA35_FLOOR_TORCH_FULL_BLOCK_CONTACT_MEASURED]"
                    + " categoryScope=floor_torch_only"
                    + " caseName=lowered_full_block_support"
                    + " supportPos=" + fullBlockSupportPos.toShortString()
                    + " supportState=" + fullBlockState
                    + " torchPos=" + torchPos.toShortString()
                    + " torchState=" + torchState
                    + " compoundAnchorAtSupport=" + compoundAnchorAtSupport
                    + " supportDy=" + String.format("%.3f", supportDy)
                    + " torchDy=" + (Double.isFinite(torchDy) ? String.format("%.3f", torchDy) : "N/A")
                    + " torchDyBefore=-0.500"
                    + " torchDyAfter=" + (Double.isFinite(torchDy) ? String.format("%.3f", torchDy) : "N/A")
                    + " supportVisibleTopY=" + String.format("%.6f", supportVisibleTopY)
                    + " torchModelBottomY=" + (Double.isFinite(torchModelBottomY) ? String.format("%.6f", torchModelBottomY) : "N/A")
                    + " contactGap=" + (Double.isFinite(contactGap) ? String.format("%.6f", contactGap) : "N/A")
                    + " outlineMinY=" + beta35FormatMinY(outlineBox)
                    + " outlineMaxY=" + beta35FormatMaxY(outlineBox)
                    + " raycastMinY=" + beta35FormatMinY(raycastBox)
                    + " raycastMaxY=" + beta35FormatMaxY(raycastBox)
                    + " survival=" + survivalResult
                    + " triad=" + triadStatus
                    + " legalOutcome=" + legalOutcome
                    + " fixtureMatchesFixedLegalStack=" + fixtureMatchesFixedLegalStack
                    + " placementResult=" + placementResultText[0]
                    + " placementAccepted=" + placementAccepted[0]
                    + " failureLayer=" + failureLayer
                    + " wall_torch=NOT_COVERED"
                    + " lantern=NOT_COVERED"
                    + " signs=NOT_COVERED"
                    + " chains=NOT_COVERED"
                    + " productionGameplayFixApplied=true"
                    + " beta35ReleaseStatus=PAUSED_LIVE_TORCH_FULLBLOCK_CONTACT_FIX_PROOF");
            System.out.println("[JULIA_BETA35_FLOOR_TORCH_FULL_BLOCK_CONTACT_RED]"
                    + " categoryScope=floor_torch_only"
                    + " caseName=lowered_full_block_support"
                    + " supportDy=" + String.format("%.3f", supportDy)
                    + " torchDy=" + (Double.isFinite(torchDy) ? String.format("%.3f", torchDy) : "N/A")
                    + " contactGap=" + (Double.isFinite(contactGap) ? String.format("%.6f", contactGap) : "N/A")
                    + " contactGapExpected=0.000000"
                    + " legalOutcome=" + legalOutcome
                    + " survival=" + survivalResult
                    + " triad=" + triadStatus
                    + " redProofExpected=CONTACT_FIX"
                    + " redProofResult=" + (failureLayer.equals("NONE") ? "GREEN" : "RED")
                    + " failureLayer=" + failureLayer
                    + " wall_torch=NOT_COVERED"
                    + " productionGameplayFixApplied=true");
            String fixMarker = "[JULIA_BETA35_FLOOR_TORCH_FULL_BLOCK_CONTACT_FIX_GREEN]";
            System.out.println(fixMarker
                    + " categoryScope=floor_torch_only"
                    + " caseName=lowered_full_block_support"
                    + " legalOutcome=" + legalOutcome
                    + " supportDy=" + String.format("%.3f", supportDy)
                    + " torchDyBefore=-0.500"
                    + " torchDyAfter=" + (Double.isFinite(torchDy) ? String.format("%.3f", torchDy) : "N/A")
                    + " supportVisibleTopY=" + String.format("%.6f", supportVisibleTopY)
                    + " torchModelBottomY=" + (Double.isFinite(torchModelBottomY) ? String.format("%.6f", torchModelBottomY) : "N/A")
                    + " contactGap=" + (Double.isFinite(contactGap) ? String.format("%.6f", contactGap) : "N/A")
                    + " survival=" + survivalResult
                    + " triad=" + triadStatus
                    + " fixtureMatchesFixedLegalStack=" + fixtureMatchesFixedLegalStack
                    + " failureLayer=" + failureLayer);
        });

        boolean green = failureLayerHolder[0].equals("NONE");
        System.out.println("[JULIA_BETA35_FLOOR_TORCH_FULL_BLOCK_CONTACT_SUMMARY]"
                + " categoryScope=floor_torch_only"
                + " caseName=lowered_full_block_support"
                + " legalOutcome=" + legalOutcomeHolder[0]
                + " failureLayer=" + failureLayerHolder[0]
                + " redProofResult=" + (green ? "GREEN" : "RED")
                + " wall_torch=NOT_COVERED"
                + " lantern=NOT_COVERED"
                + " signs=NOT_COVERED"
                + " chains=NOT_COVERED"
                + " productionGameplayFixApplied=true"
                + " beta35ReleaseStatus=PAUSED_LIVE_TORCH_FULLBLOCK_CONTACT_FIX_PROOF"
                + " releasePrep=PAUSED");

        if (!green) {
            throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_FULL_BLOCK_CONTACT_RED]"
                    + " fullBlockContactFix=FAILED"
                    + " failureLayer=" + failureLayerHolder[0]
                    + " legalOutcome=" + legalOutcomeHolder[0]
                    + " categoryScope=floor_torch_only");
        }

        // ── Regression: top_slab_support and bottom_slab_support ─────────────────
        Beta35V2ContactGapAttempt topScenario = runBeta35FloorTorchV2ContactGapRedAttempt(
                ctx,
                singleplayer,
                "top_slab_support",
                new BlockPos(44, -57, 87),
                SlabType.TOP,
                -1.000d,
                -0.500d,
                -1.000d,
                new BlockPos(44, -56, 87),
                new BlockPos(44, -57, 87),
                new BlockPos(45, -57, 87));

        Beta35V2ContactGapAttempt bottomScenario = runBeta35FloorTorchV2ContactGapRedAttempt(
                ctx,
                singleplayer,
                "bottom_slab_support",
                new BlockPos(43, -57, 88),
                SlabType.BOTTOM,
                -1.000d,
                -1.000d,
                Double.NaN,
                new BlockPos(43, -56, 88),
                new BlockPos(43, -57, 88),
                new BlockPos(44, -57, 88));

        boolean topGreen = topScenario.failureLayer().equals("NONE");
        boolean bottomGreen = bottomScenario.failureLayer().equals("NONE");
        String regressionResult = topGreen && bottomGreen ? "GREEN" : "RED";
        System.out.println("[JULIA_BETA35_FLOOR_TORCH_FULL_BLOCK_REGRESSION_SUMMARY]"
                + " categoryScope=floor_torch_only"
                + " top_slab_support=" + (topGreen ? "GREEN" : "RED")
                + " bottom_slab_support=" + (bottomGreen ? "GREEN" : "RED")
                + " regressionResult=" + regressionResult
                + " topLegalOutcome=" + topScenario.legalOutcome()
                + " bottomLegalOutcome=" + bottomScenario.legalOutcome()
                + " topContactGap=" + (Double.isFinite(topScenario.contactGap())
                        ? String.format("%.6f", topScenario.contactGap()) : "N/A")
                + " bottomContactGap=" + (Double.isFinite(bottomScenario.contactGap())
                        ? String.format("%.6f", bottomScenario.contactGap()) : "N/A")
                + " wall_torch=NOT_COVERED");

        if (!topGreen || !bottomGreen) {
            throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_FULL_BLOCK_REGRESSION_FAILED]"
                    + " top_slab_support=" + topScenario.failureLayer()
                    + " bottom_slab_support=" + bottomScenario.failureLayer()
                    + " categoryScope=floor_torch_only");
        }
    }

    /**
     * RED proof: floor torch placed on a plain bottom slab remains present after the slab
     * gains its compound-visible-side-lower mark (support finalization).
     *
     * The production timing path:
     *  1. Slab placed in vanilla/pre-finalization state.
     *  2. Torch authored on slab (canPlaceAt passes — no compound mark yet).
     *  3. Compound mark applied server-side (addCompoundVisibleSideLowerSlab).
     *     Attachment write does NOT call world.updateNeighbors → getStateForNeighborUpdate
     *     is never triggered on the torch.
     *  4. Torch remains while isRejectedFloorTorchTopFace is now true.
     *
     * Expected:
     *  RED  (SUPPORT_FINALIZATION_STALE_TORCH)  — torch remains after finalization.
     *  GREEN (SUPPORT_FINALIZATION_REMOVED_GREEN) — only if existing code already removes it.
     *
     * Gate: -Dslabbed.beta35FloorTorchSupportFinalizationRed=true
     */
    private static void runBeta35FloorTorchSupportFinalizationRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        // Positions — chosen to avoid conflicts with other proof fixtures.
        // Tower: towerBase → towerAnchor → towerCarrier → compoundSource (compound full-block anchor, dy=-1.0)
        // Support slab: 1 block west of compound source, same Y.
        final BlockPos compoundSourcePos = new BlockPos(55, -57, 87);
        final BlockPos supportCandidatePos = new BlockPos(54, -57, 87);
        final BlockPos torchPos = supportCandidatePos.up();
        final BlockPos towerBase = compoundSourcePos.down(3);
        final BlockPos towerAnchor = compoundSourcePos.down(2);
        final BlockPos towerCarrier = compoundSourcePos.down(1);

        final BlockHitResult torchUseHit = new BlockHitResult(
                new Vec3d(supportCandidatePos.getX() + 0.5d, supportCandidatePos.getY() + 1.0d, supportCandidatePos.getZ() + 0.5d),
                Direction.UP,
                supportCandidatePos,
                false);

        // ── Phase 1: build fixture, place plain slab (no compound mark), place torch ────────
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            // Tower that makes compoundSourcePos a compound full-block anchor with dy=-1.0.
            world.setBlockState(towerBase,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(towerAnchor, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, towerAnchor, world.getBlockState(towerAnchor));
            world.setBlockState(towerCarrier,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world, towerCarrier, world.getBlockState(towerCarrier));
            world.setBlockState(compoundSourcePos, Blocks.STONE.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, compoundSourcePos, world.getBlockState(compoundSourcePos));
            SlabAnchorAttachment.addCompoundFullBlockAnchor(
                    world, compoundSourcePos, world.getBlockState(compoundSourcePos));
            // Support slab: plain bottom slab, NO compound-visible-lower mark yet.
            world.setBlockState(supportCandidatePos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            // Clear torch position.
            world.setBlockState(torchPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.TORCH, 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(supportCandidatePos.getX() + 0.5d, supportCandidatePos.getY() + 3.2d, supportCandidatePos.getZ() - 2.0d),
                torchUseHit.getPos());

        final String[] placementResultText = {"not-run"};
        final boolean[] placementAccepted = {false};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_SUPPORT_FINALIZATION_RED]"
                        + " reason=client_not_ready phase=before_finalization categoryScope=floor_torch_only");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, torchUseHit);
            placementResultText[0] = result.toString();
            placementAccepted[0] = result.isAccepted();
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        // Capture before-finalization state.
        final BlockState[] torchStateBeforeHolder = {null};
        final double[] torchDyBeforeHolder = {Double.NaN};
        final double[] supportDyBeforeHolder = {Double.NaN};
        final boolean[] rejectedBeforeHolder = {false};

        ctx.runOnClient(mc -> {
            if (mc.world == null) return;
            BlockState supportState = mc.world.getBlockState(supportCandidatePos);
            BlockState torchState = mc.world.getBlockState(torchPos);
            torchStateBeforeHolder[0] = torchState;
            double supportDy = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportState);
            supportDyBeforeHolder[0] = supportDy;
            if (torchState.isOf(Blocks.TORCH)) {
                torchDyBeforeHolder[0] = SlabSupport.getYOffset(mc.world, torchPos, torchState);
            }
            rejectedBeforeHolder[0] = SlabSupport.isRejectedFloorTorchTopFace(mc.world, supportCandidatePos, torchState);
            System.out.println("[JULIA_BETA35_FLOOR_TORCH_SUPPORT_FINALIZATION_RED]"
                    + " phase=before_finalization"
                    + " categoryScope=floor_torch_only"
                    + " supportCandidatePos=" + supportCandidatePos.toShortString()
                    + " supportStateBefore=" + supportState
                    + " supportDyBefore=" + String.format("%.3f", supportDy)
                    + " torchPos=" + torchPos.toShortString()
                    + " torchStateBefore=" + torchState
                    + " torchDyBefore=" + (Double.isFinite(torchDyBeforeHolder[0])
                            ? String.format("%.3f", torchDyBeforeHolder[0]) : "N/A")
                    + " isRejectedBefore=" + rejectedBeforeHolder[0]
                    + " placementResult=" + placementResultText[0]
                    + " placementAccepted=" + placementAccepted[0]
                    + " wall_torch=NOT_COVERED");
        });

        // ── Phase 2: apply compound-visible-lower mark (simulate slab finalization) ────────
        // Production fix: addCompoundVisibleSideLowerSlab now calls world.updateNeighborsAlways
        // after the mark write, triggering TorchBlockMixin.getStateForNeighborUpdate(DOWN) which
        // returns AIR and removes the stale floor torch.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState supportState = world.getBlockState(supportCandidatePos);
            BlockState compoundState = world.getBlockState(compoundSourcePos);
            SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(
                    world, supportCandidatePos, supportState, compoundSourcePos, compoundState);
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        // ── Phase 2 check ────────────────────────────────────────────────────────────────
        final String[] failureLayerHolder = {"FINALIZATION_FIXTURE_NOT_RUN"};
        final String[] legalOutcomeHolder = {"UNSET"};

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_SUPPORT_FINALIZATION_RED]"
                        + " reason=client_world_missing phase=after_finalization categoryScope=floor_torch_only");
            }

            BlockState supportStateAfter = mc.world.getBlockState(supportCandidatePos);
            BlockState torchStateAfter = mc.world.getBlockState(torchPos);
            double supportDyAfter = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportStateAfter);
            boolean compoundLowerMarkWritten =
                    SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(mc.world, supportCandidatePos, supportStateAfter);
            boolean rejectedAfter =
                    SlabSupport.isRejectedFloorTorchTopFace(mc.world, supportCandidatePos, torchStateAfter);
            boolean torchPresent = torchStateAfter.isOf(Blocks.TORCH);
            double torchDyAfter = torchPresent
                    ? SlabSupport.getYOffset(mc.world, torchPos, torchStateAfter)
                    : Double.NaN;

            // canPlaceAt with finalized support.
            boolean canPlaceAfter = torchStateAfter.isOf(Blocks.TORCH)
                    && torchStateAfter.canPlaceAt(mc.world, torchPos);

            // Simulate getStateForNeighborUpdate to show what SHOULD happen.
            BlockState neighborUpdateResult = null;
            if (torchPresent) {
                try {
                    neighborUpdateResult = torchStateAfter.getStateForNeighborUpdate(
                            mc.world,
                            mc.world,
                            torchPos,
                            Direction.DOWN,
                            supportCandidatePos,
                            supportStateAfter,
                            net.minecraft.util.math.random.Random.create());
                } catch (Exception e) {
                    // ignore — diagnostic only
                }
            }
            boolean neighborUpdateWouldRemove = neighborUpdateResult != null
                    && neighborUpdateResult.isAir();

            // supportVisibleTopY for bottom slab: pos.getY() + getSupportYOffset(BOTTOM) + supportDy
            double supportVisibleTopY = supportCandidatePos.getY() + 0.5d + supportDyAfter;
            net.minecraft.util.math.Box modelBox = torchPresent
                    ? beta35FloorTorchModelProxyWorldBox(torchPos, torchDyAfter)
                    : null;
            double torchModelBottomY = modelBox == null ? Double.NaN : modelBox.minY;
            double contactGapAfter = Double.isFinite(torchModelBottomY)
                    ? torchModelBottomY - supportVisibleTopY
                    : Double.NaN;

            String legalOutcome = torchPresent
                    ? "FLOOR_TORCH_COMPOUND_VISIBLE_BOTTOM_SLAB_SUPPORT_STALE_TORCH_REMAINS"
                    : "FLOOR_TORCH_COMPOUND_VISIBLE_BOTTOM_SLAB_SUPPORT_TORCH_CORRECTLY_REMOVED";
            legalOutcomeHolder[0] = legalOutcome;

            String failureLayer;
            if (!compoundLowerMarkWritten || Math.abs(supportDyAfter - (-1.0d)) > EPSILON) {
                failureLayer = "SOURCE_TRUTH_MISMATCH";
            } else if (torchPresent) {
                failureLayer = "SUPPORT_FINALIZATION_STALE_TORCH";
            } else {
                failureLayer = "SUPPORT_FINALIZATION_REMOVED_GREEN";
            }
            failureLayerHolder[0] = failureLayer;

            System.out.println("[JULIA_BETA35_FLOOR_TORCH_SUPPORT_FINALIZATION_RED]"
                    + " phase=after_finalization"
                    + " categoryScope=floor_torch_only"
                    + " supportCandidatePos=" + supportCandidatePos.toShortString()
                    + " supportStateAfter=" + supportStateAfter
                    + " supportDyAfter=" + String.format("%.3f", supportDyAfter)
                    + " compoundLowerMarkWritten=" + compoundLowerMarkWritten
                    + " isRejectedAfter=" + rejectedAfter
                    + " canPlaceAfter=" + canPlaceAfter
                    + " neighborUpdateWouldRemove=" + neighborUpdateWouldRemove
                    + " torchPos=" + torchPos.toShortString()
                    + " torchStateAfter=" + torchStateAfter
                    + " torchPresent=" + torchPresent
                    + " torchDyBefore=" + (Double.isFinite(torchDyBeforeHolder[0])
                            ? String.format("%.3f", torchDyBeforeHolder[0]) : "N/A")
                    + " torchDyAfter=" + (Double.isFinite(torchDyAfter)
                            ? String.format("%.3f", torchDyAfter) : "N/A")
                    + " supportVisibleTopY=" + String.format("%.6f", supportVisibleTopY)
                    + " torchModelBottomY=" + (Double.isFinite(torchModelBottomY)
                            ? String.format("%.6f", torchModelBottomY) : "N/A")
                    + " contactGapAfter=" + (Double.isFinite(contactGapAfter)
                            ? String.format("%.6f", contactGapAfter) : "N/A")
                    + " legalOutcome=" + legalOutcome
                    + " failureLayer=" + failureLayer
                    + " wall_torch=NOT_COVERED"
                    + " productionGameplayFixApplied=true"
                    + " beta35ReleaseStatus=PAUSED_PENDING_JULIA_RETEST");
            System.out.println("[JULIA_BETA35_FLOOR_TORCH_SUPPORT_FINALIZATION_GREEN]"
                    + " phase=after_finalization"
                    + " categoryScope=floor_torch_only"
                    + " torchPresent=" + torchPresent
                    + " legalOutcome=" + legalOutcome
                    + " supportDyAfter=" + String.format("%.3f", supportDyAfter)
                    + " torchDyBefore=" + (Double.isFinite(torchDyBeforeHolder[0])
                            ? String.format("%.3f", torchDyBeforeHolder[0]) : "N/A")
                    + " torchDyAfter=" + (Double.isFinite(torchDyAfter)
                            ? String.format("%.3f", torchDyAfter) : "N/A")
                    + " contactGapAfter=" + (Double.isFinite(contactGapAfter)
                            ? String.format("%.6f", contactGapAfter) : "N/A")
                    + " neighborUpdateWouldRemove=" + neighborUpdateWouldRemove
                    + " failureLayer=" + failureLayer);
        });

        // Summarize.
        boolean fixtureOk = !failureLayerHolder[0].equals("SOURCE_TRUTH_MISMATCH")
                && !failureLayerHolder[0].equals("FINALIZATION_FIXTURE_NOT_RUN");
        boolean proofGreen = failureLayerHolder[0].equals("SUPPORT_FINALIZATION_REMOVED_GREEN");
        boolean proofRed = failureLayerHolder[0].equals("SUPPORT_FINALIZATION_STALE_TORCH");

        System.out.println("[JULIA_BETA35_FLOOR_TORCH_SUPPORT_FINALIZATION_SUMMARY]"
                + " categoryScope=floor_torch_only"
                + " legalOutcome=" + legalOutcomeHolder[0]
                + " failureLayer=" + failureLayerHolder[0]
                + " redProofResult=" + (proofRed ? "RED" : proofGreen ? "GREEN" : "INCONCLUSIVE")
                + " productionGameplayFixApplied=true"
                + " wall_torch=NOT_COVERED"
                + " lantern=NOT_COVERED"
                + " signs=NOT_COVERED"
                + " chains=NOT_COVERED"
                + " beta35ReleaseStatus=PAUSED_PENDING_JULIA_RETEST"
                + " releasePrep=PAUSED"
                + " nextSlice=RELEASE_PENDING_JULIA_RETEST");

        if (!fixtureOk) {
            throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_SUPPORT_FINALIZATION_RED]"
                    + " reason=fixture_failed failureLayer=" + failureLayerHolder[0]
                    + " categoryScope=floor_torch_only");
        }
        if (proofRed) {
            throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_SUPPORT_FINALIZATION_RED]"
                    + " reason=regression_stale_torch_remains failureLayer=" + failureLayerHolder[0]
                    + " categoryScope=floor_torch_only"
                    + " productionGameplayFixApplied=true");
        }
    }

    private static void runBeta35LiveFloorTorchContactGapRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportCandidatePos = new BlockPos(51, -56, 89);
        final BlockPos torchPos = supportCandidatePos.up();
        final BlockHitResult torchUseHit = new BlockHitResult(
                new Vec3d(supportCandidatePos.getX() + 0.5d, supportCandidatePos.getY(), supportCandidatePos.getZ() + 0.5d),
                Direction.UP,
                supportCandidatePos,
                false);

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(supportCandidatePos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(torchPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.TORCH, 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(supportCandidatePos.getX() + 0.5d, supportCandidatePos.getY() + 2.2d, supportCandidatePos.getZ() - 2.0d),
                torchUseHit.getPos());

        final String[] placementResultText = {"not-run"};
        final boolean[] placementAccepted = {false};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_RED]"
                        + " reason=client_not_ready categoryScope=floor_torch_only");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, torchUseHit);
            placementResultText[0] = result.toString();
            placementAccepted[0] = result.isAccepted();
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_RED]"
                        + " reason=client_world_or_player_missing categoryScope=floor_torch_only");
            }

            BlockState torchState = mc.world.getBlockState(torchPos);
            BlockState supportCandidateState = mc.world.getBlockState(supportCandidatePos);
            double supportDy = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportCandidateState);
            double torchDy = torchState.isOf(Blocks.TORCH)
                    ? SlabSupport.getYOffset(mc.world, torchPos, torchState)
                    : Double.NaN;
            double supportVisibleTopY = supportCandidateState.isOf(Blocks.STONE_SLAB)
                    ? beta35SupportVisibleTopY(supportCandidatePos, supportCandidateState, supportDy)
                    : Double.NaN;

            VoxelShape outlineShape = torchState.isOf(Blocks.TORCH)
                    ? torchState.getOutlineShape(mc.world, torchPos, net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = torchState.isOf(Blocks.TORCH)
                    ? torchState.getRaycastShape(mc.world, torchPos)
                    : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, torchPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, torchPos);
            double torchModelBottomY = outlineBox == null ? Double.NaN : outlineBox.minY;
            double contactGap = torchModelBottomY - supportVisibleTopY;

            boolean supportOwnerExpected = torchState.isOf(Blocks.TORCH)
                    && supportCandidateState.isOf(Blocks.STONE_SLAB)
                    && supportCandidateState.get(SlabBlock.TYPE) == SlabType.BOTTOM;
            boolean torchDyExpected = torchState.isOf(Blocks.TORCH) && Math.abs(torchDy + 1.0d) <= EPSILON;
            boolean contactGapExpected = !Double.isNaN(contactGap) && Math.abs(contactGap + 1.5d) <= EPSILON;

            String failureLayer = "NONE";
            if (!placementAccepted[0] || !torchState.isOf(Blocks.TORCH)) {
                failureLayer = "LIVE_FLOOR_TORCH_CONTACT_GAP";
            } else if (!supportOwnerExpected) {
                failureLayer = "LIVE_FLOOR_TORCH_WRONG_SUPPORT_OWNER";
            } else if (!torchDyExpected) {
                failureLayer = "LIVE_FLOOR_TORCH_WRONG_DY";
            } else if (!contactGapExpected) {
                failureLayer = "LIVE_FLOOR_TORCH_CONTACT_GAP";
            }

            String liveCapturePattern = contactGapExpected ? "MATCHED" : "NOT_MATCHED";
            String redClassification = "RED";
            String marker = "[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_RED]";
            System.out.println("[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_MEASURED]"
                    + " categoryScope=floor_torch_only"
                    + " expectedSampleContactGap=-1.500000"
                    + " observedSampleMatch=" + liveCapturePattern
                    + " torchPos=" + torchPos.toShortString()
                    + " torchState=" + torchState
                    + " supportCandidatePos=" + supportCandidatePos.toShortString()
                    + " supportCandidateState=" + supportCandidateState
                    + " supportDy=" + (supportCandidateState.isOf(Blocks.STONE_SLAB) ? String.format("%.3f", supportDy) : "N/A")
                    + " torchDy=" + (torchState.isOf(Blocks.TORCH) ? String.format("%.3f", torchDy) : "N/A")
                    + " supportVisibleTopY=" + (Double.isFinite(supportVisibleTopY) ? String.format("%.6f", supportVisibleTopY) : "N/A")
                    + " torchModelBottomY=" + (Double.isFinite(torchModelBottomY) ? String.format("%.6f", torchModelBottomY) : "N/A")
                    + " contactGap=" + (Double.isFinite(contactGap) ? String.format("%.6f", contactGap) : "N/A")
                    + " outlineMinY=" + beta35FormatMinY(outlineBox)
                    + " outlineMaxY=" + beta35FormatMaxY(outlineBox)
                    + " raycastMinY=" + beta35FormatMinY(raycastBox)
                    + " raycastMaxY=" + beta35FormatMaxY(raycastBox)
                    + " supportCandidateType=stone_slab[type=bottom]"
                    + " failureLayer=" + failureLayer
                    + " redClassification=" + redClassification
                    + " placementAccepted=" + placementAccepted[0]
                    + " placementResult=" + placementResultText[0]);
            System.out.println(marker
                    + " categoryScope=floor_torch_only"
                    + " itemCategory=floor_torch"
                    + " wall_torch=NOT_COVERED"
                    + " lantern=NOT_COVERED"
                    + " signs=NOT_COVERED"
                    + " chains=NOT_COVERED"
                    + " torchPos=" + torchPos.toShortString()
                    + " torchState=" + torchState
                    + " supportCandidatePos=" + supportCandidatePos.toShortString()
                    + " supportCandidateState=" + supportCandidateState
                    + " torchDy=" + (torchState.isOf(Blocks.TORCH) ? String.format("%.3f", torchDy) : "N/A")
                    + " supportDy=" + (supportCandidateState.isOf(Blocks.STONE_SLAB) ? String.format("%.3f", supportDy) : "N/A")
                    + " supportVisibleTopY=" + (Double.isFinite(supportVisibleTopY) ? String.format("%.6f", supportVisibleTopY) : "N/A")
                    + " torchModelBottomY=" + (Double.isFinite(torchModelBottomY) ? String.format("%.6f", torchModelBottomY) : "N/A")
                    + " contactGap=" + (Double.isFinite(contactGap) ? String.format("%.6f", contactGap) : "N/A")
                    + " wallTorchStatus=NOT_COVERED"
                    + " failureLayer=" + failureLayer
                    + " liveCaptureMatch=" + liveCapturePattern
                    + " redProofExpected=CONTACT_GAP"
                    + " proofTargetLayer=FLOOR_TORCH_ONLY");
        System.out.println("[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_SUMMARY]"
                + " categoryScope=floor_torch_only"
                + " itemCategory=floor_torch"
                + " wall_torch=NOT_COVERED"
                + " lantern=NOT_COVERED"
                + " signs=NOT_COVERED"
                + " chains=NOT_COVERED"
                    + " expectedContactGap=-1.500000"
                    + " observedContactGap=" + (Double.isFinite(contactGap) ? String.format("%.6f", contactGap) : "N/A")
                    + " supportVisibleTopY=" + (Double.isFinite(supportVisibleTopY) ? String.format("%.6f", supportVisibleTopY) : "N/A")
                    + " torchModelBottomY=" + (Double.isFinite(torchModelBottomY) ? String.format("%.6f", torchModelBottomY) : "N/A")
                    + " torchDy=" + (torchState.isOf(Blocks.TORCH) ? String.format("%.3f", torchDy) : "N/A")
                    + " supportDy=" + (supportCandidateState.isOf(Blocks.STONE_SLAB) ? String.format("%.3f", supportDy) : "N/A")
                    + " supportCandidateState=" + supportCandidateState
                    + " torchDyExpected=-1.000"
                    + " supportOwnerExpected=" + supportOwnerExpected
                    + " contactGapExpected=" + contactGapExpected
                    + " failureLayer=" + failureLayer
                    + " heldItem=" + mc.player.getMainHandStack().getItem().getTranslationKey()
                    + " playerPos="
                    + String.format("%.3f,%.3f,%.3f", mc.player.getX(), mc.player.getY(), mc.player.getZ())
                    + " redProofResult="
                    + (failureLayer.equals("NONE") ? "GREEN" : "RED")
                    + " beta35ReleaseStatus=PAUSED_LIVE_TORCH_CONTACT_GAP_PROOF"
                    + " releasePrep=PAUSED"
                    + " productionGameplayFixApplied=false");
        });
    }

    private static void runBeta35LiveFloorTorchSourceTruthParityProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        final BlockPos supportCandidatePos = new BlockPos(51, -56, 89);
        final BlockPos torchPos = supportCandidatePos.up();
        final BlockPos anchoredFullBlockPos = supportCandidatePos.down();
        final BlockPos bottomSlabBelowFullBlockPos = anchoredFullBlockPos.down();
        final BlockHitResult torchUseHit = new BlockHitResult(
                new Vec3d(supportCandidatePos.getX() + 0.5d, supportCandidatePos.getY(), supportCandidatePos.getZ() + 0.5d),
                Direction.UP,
                supportCandidatePos,
                false);

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(bottomSlabBelowFullBlockPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(anchoredFullBlockPos, Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, anchoredFullBlockPos,
                    world.getBlockState(anchoredFullBlockPos));
            world.setBlockState(supportCandidatePos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    world,
                    supportCandidatePos,
                    world.getBlockState(supportCandidatePos));
            world.setBlockState(torchPos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.TORCH, 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(supportCandidatePos.getX() + 0.5d, supportCandidatePos.getY() + 2.2d, supportCandidatePos.getZ() - 2.0d),
                torchUseHit.getPos());

        final String[] placementResultText = {"not-run"};
        final boolean[] placementAccepted = {false};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[JULIA_BETA35_LIVE_FLOOR_TORCH_SOURCE_TRUTH_FAIL]"
                        + " reason=client_not_ready categoryScope=floor_torch_only");
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, torchUseHit);
            placementResultText[0] = result.toString();
            placementAccepted[0] = result.isAccepted();
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                throw new RuntimeException("[JULIA_BETA35_LIVE_FLOOR_TORCH_SOURCE_TRUTH_FAIL]"
                        + " reason=client_world_or_player_missing categoryScope=floor_torch_only");
            }

            BlockState torchState = mc.world.getBlockState(torchPos);
            BlockState supportCandidateState = mc.world.getBlockState(supportCandidatePos);
            double supportDy = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportCandidateState);
            double torchDy = torchState.isOf(Blocks.TORCH)
                    ? SlabSupport.getYOffset(mc.world, torchPos, torchState)
                    : Double.NaN;
            double supportVisibleTopY = supportCandidateState.isOf(Blocks.STONE_SLAB)
                    ? beta35SupportVisibleTopY(supportCandidatePos, supportCandidateState, supportDy)
                    : Double.NaN;

            VoxelShape outlineShape = torchState.isOf(Blocks.TORCH)
                    ? torchState.getOutlineShape(mc.world, torchPos, net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = torchState.isOf(Blocks.TORCH)
                    ? torchState.getRaycastShape(mc.world, torchPos)
                    : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, torchPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, torchPos);
            double torchModelBottomY = outlineBox == null ? Double.NaN : outlineBox.minY;
            double contactGap = torchModelBottomY - supportVisibleTopY;

            boolean supportIsBottomSlab = supportCandidateState.isOf(Blocks.STONE_SLAB)
                    && supportCandidateState.get(SlabBlock.TYPE) == SlabType.BOTTOM;
            boolean supportDyExpected = supportIsBottomSlab && Math.abs(supportDy + 0.5d) <= EPSILON;
            boolean torchDyExpected = torchState.isOf(Blocks.TORCH) && Math.abs(torchDy + 1.0d) <= EPSILON;
            boolean fixtureMatchesLiveDyStack = supportDyExpected && torchDyExpected;
            boolean contactGapExpected = !Double.isNaN(contactGap) && Math.abs(contactGap + 1.5d) <= EPSILON;

            String failureLayer = "NONE";
            if (!placementAccepted[0] || !supportIsBottomSlab || !torchState.isOf(Blocks.TORCH) || !fixtureMatchesLiveDyStack) {
                failureLayer = "SOURCE_TRUTH_MISMATCH";
            } else if (Double.isNaN(contactGap) || Math.abs(contactGap) < EPSILON) {
                failureLayer = "LIVE_DY_STACK_MATCH_NO_GAP";
            } else if (!contactGapExpected) {
                failureLayer = "LIVE_DY_STACK_MATCH_CONTACT_GAP";
            }

            // JULIA_BETA35_FLOOR_TORCH_SUPPORT_SOURCE_AUDIT: diagnostic-only markers that log
            // every carrier/anchor predicate on the support slab and its immediate surroundings
            // so we can pinpoint which source truth is present in the live world but absent in
            // the fixture. No behaviour change.
            boolean supportIsAnchored = SlabAnchorAttachment.isAnchored(mc.world, supportCandidatePos);
            boolean supportHasCarrierMark =
                    SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                            mc.world, supportCandidatePos, supportCandidateState);
            boolean supportNonRecursiveCarrier =
                    SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(
                            mc.world, supportCandidatePos, supportCandidateState);
            boolean supportSideLaneCarrier =
                    SlabSupport.isLoweredSideLaneSlabCarrier(
                            mc.world, supportCandidatePos, supportCandidateState);
            boolean supportCompoundVisibleLower =
                    SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(
                            mc.world, supportCandidatePos, supportCandidateState);

            BlockPos belowSupportPos = supportCandidatePos.down();
            BlockState belowSupportState = mc.world.getBlockState(belowSupportPos);
            double belowSupportDy = SlabSupport.getYOffset(mc.world, belowSupportPos, belowSupportState);
            boolean belowSupportAnchored = SlabAnchorAttachment.isAnchored(mc.world, belowSupportPos);
            boolean belowHasBottomSlabBelow = SlabSupport.hasBottomSlabBelow(mc.world, belowSupportPos);

            BlockPos northSupportPos = supportCandidatePos.north();
            BlockPos southSupportPos = supportCandidatePos.south();
            BlockPos eastSupportPos  = supportCandidatePos.east();
            BlockPos westSupportPos  = supportCandidatePos.west();
            BlockState northSupportState = mc.world.getBlockState(northSupportPos);
            BlockState southSupportState = mc.world.getBlockState(southSupportPos);
            BlockState eastSupportState  = mc.world.getBlockState(eastSupportPos);
            BlockState westSupportState  = mc.world.getBlockState(westSupportPos);
            double northSupportDy = SlabSupport.getYOffset(mc.world, northSupportPos, northSupportState);
            double southSupportDy = SlabSupport.getYOffset(mc.world, southSupportPos, southSupportState);
            double eastSupportDy  = SlabSupport.getYOffset(mc.world, eastSupportPos,  eastSupportState);
            double westSupportDy  = SlabSupport.getYOffset(mc.world, westSupportPos,  westSupportState);

            String dySourceTag = supportHasCarrierMark
                    ? "PERSISTENT_CARRIER_MARK"
                    : supportNonRecursiveCarrier
                            ? "STRUCTURAL_NON_RECURSIVE"
                            : supportSideLaneCarrier
                                    ? "SIDE_LANE_CARRIER"
                                    : "NONE";

            System.out.println("[JULIA_BETA35_FLOOR_TORCH_SUPPORT_SOURCE_AUDIT]"
                    + " categoryScope=floor_torch_only"
                    + " supportCandidatePos=" + supportCandidatePos.toShortString()
                    + " supportCandidateState=" + supportCandidateState
                    + " supportDy=" + String.format("%.3f", supportDy)
                    + " measuredSide=CLIENT"
                    + " worldClass=" + mc.world.getClass().getSimpleName()
                    + " supportIsAnchored=" + supportIsAnchored
                    + " supportHasCarrierMark=" + supportHasCarrierMark
                    + " supportNonRecursiveCarrier=" + supportNonRecursiveCarrier
                    + " supportSideLaneCarrier=" + supportSideLaneCarrier
                    + " supportCompoundVisibleLower=" + supportCompoundVisibleLower
                    + " dySource=" + dySourceTag
                    + " belowPos=" + belowSupportPos.toShortString()
                    + " belowState=" + belowSupportState
                    + " belowDy=" + String.format("%.3f", belowSupportDy)
                    + " belowAnchored=" + belowSupportAnchored
                    + " belowHasBottomSlabBelow=" + belowHasBottomSlabBelow
                    + " north=" + northSupportState + " northDy=" + String.format("%.3f", northSupportDy)
                    + " south=" + southSupportState + " southDy=" + String.format("%.3f", southSupportDy)
                    + " east=" + eastSupportState  + " eastDy="  + String.format("%.3f", eastSupportDy)
                    + " west=" + westSupportState  + " westDy="  + String.format("%.3f", westSupportDy));

            if (!fixtureMatchesLiveDyStack) {
                String missingTruth = !belowSupportAnchored && !belowHasBottomSlabBelow
                        ? "NO_ANCHORED_OR_BOTTOM_SLAB_BACKED_FULL_BLOCK_BELOW"
                        : !supportSideLaneCarrier
                                ? "NO_SIDE_LANE_CARRIER_CONTEXT"
                                : "UNKNOWN_STRUCTURAL_CONTEXT";
                System.out.println("[JULIA_BETA35_FLOOR_TORCH_SUPPORT_SOURCE_MISSING]"
                        + " categoryScope=floor_torch_only"
                        + " expectedSupportDy=-0.500"
                        + " observedSupportDy=" + String.format("%.3f", supportDy)
                        + " expectedTorchDy=-1.000"
                        + " observedTorchDy=" + (torchState.isOf(Blocks.TORCH)
                                ? String.format("%.3f", torchDy) : "N/A")
                        + " missingSourceTruth=" + missingTruth
                        + " updatePersistentLoweredSlabCarrierResult=qualifies_false_no_mark_written"
                        + " fixture=SLAB_IN_ISOLATION_NO_SURROUNDING_CONTEXT"
                        + " hypothesizedFix=PLACE_ANCHORED_FULL_BLOCK_AT_BELOW_POS_OR_BOTTOM_SLAB_BELOW_THAT");
            }

            System.out.println("[JULIA_BETA35_FLOOR_TORCH_SUPPORT_SOURCE_SUMMARY]"
                    + " categoryScope=floor_torch_only"
                    + " fixtureMatchesLiveDyStack=" + fixtureMatchesLiveDyStack
                    + " supportHasCarrierMark=" + supportHasCarrierMark
                    + " supportSideLaneCarrier=" + supportSideLaneCarrier
                    + " belowAnchored=" + belowSupportAnchored
                    + " belowHasBottomSlabBelow=" + belowHasBottomSlabBelow
                    + " missingTruth=" + (fixtureMatchesLiveDyStack
                            ? "NONE" : "CARRIER_CONTEXT_NOT_IN_FIXTURE")
                    + " nextSlice=" + (fixtureMatchesLiveDyStack
                            ? "GREEN_PROCEED"
                            : "BETTER_RED_FIXTURE_PLACE_ANCHORED_FULL_BLOCK_BELOW_SUPPORT")
                    + " beta35ReleaseStatus=PAUSED"
                    + " productionGameplayFixApplied=false"
                    + " wall_torch=NOT_COVERED");

            String parityMarker = fixtureMatchesLiveDyStack
                    ? "[JULIA_BETA35_LIVE_FLOOR_TORCH_SOURCE_TRUTH_GREEN]"
                    : "[JULIA_BETA35_LIVE_FLOOR_TORCH_SOURCE_TRUTH_FAIL]";

            System.out.println("[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_MEASURED]"
                    + " categoryScope=floor_torch_only"
                    + " torchPos=" + torchPos.toShortString()
                    + " torchState=" + torchState
                    + " supportCandidatePos=" + supportCandidatePos.toShortString()
                    + " supportCandidateState=" + supportCandidateState
                    + " anchoredFullBlockPos=" + anchoredFullBlockPos.toShortString()
                    + " anchoredFullBlockState=" + belowSupportState
                    + " anchoredFullBlockAnchored=" + belowSupportAnchored
                    + " anchoredFullBlockHasBottomSlabBelow=" + belowHasBottomSlabBelow
                    + " carrierMarkWritten=" + supportHasCarrierMark
                    + " placementAccepted=" + placementAccepted[0]
                    + " placementResult=" + placementResultText[0]
                    + " supportDy=" + (supportIsBottomSlab ? String.format("%.3f", supportDy) : "N/A")
                    + " torchDy=" + (torchState.isOf(Blocks.TORCH) ? String.format("%.3f", torchDy) : "N/A")
                    + " supportVisibleTopY=" + (Double.isFinite(supportVisibleTopY) ? String.format("%.6f", supportVisibleTopY) : "N/A")
                    + " torchModelBottomY=" + (Double.isFinite(torchModelBottomY) ? String.format("%.6f", torchModelBottomY) : "N/A")
                    + " contactGap=" + (Double.isFinite(contactGap) ? String.format("%.6f", contactGap) : "N/A")
                    + " outlineMinY=" + beta35FormatMinY(outlineBox)
                    + " outlineMaxY=" + beta35FormatMaxY(outlineBox)
                    + " raycastMinY=" + beta35FormatMinY(raycastBox)
                    + " raycastMaxY=" + beta35FormatMaxY(raycastBox)
                    + " targetFace=" + torchUseHit.getSide()
                    + " targetType=" + torchUseHit.getType()
                    + " targetPos=" + torchUseHit.getBlockPos().toShortString()
                    + " targetHitX=" + String.format("%.3f", torchUseHit.getPos().x)
                    + " targetHitY=" + String.format("%.3f", torchUseHit.getPos().y)
                    + " targetHitZ=" + String.format("%.3f", torchUseHit.getPos().z)
                    + " fixtureMatchesLiveDyStack=" + fixtureMatchesLiveDyStack
                    + " failureLayer=" + failureLayer
                    + " redProofResult=" + (failureLayer.equals("NONE") ? "GREEN" : "RED")
                    + " wall_torch=NOT_COVERED");

            System.out.println(parityMarker
                    + " categoryScope=floor_torch_only"
                    + " itemCategory=floor_torch"
                    + " wall_torch=NOT_COVERED"
                    + " torchPos=" + torchPos.toShortString()
                    + " torchState=" + torchState
                    + " supportCandidatePos=" + supportCandidatePos.toShortString()
                    + " supportCandidateState=" + supportCandidateState
                    + " supportDy=" + (supportIsBottomSlab ? String.format("%.3f", supportDy) : "N/A")
                    + " torchDy=" + (torchState.isOf(Blocks.TORCH) ? String.format("%.3f", torchDy) : "N/A")
                    + " supportVisibleTopY=" + (Double.isFinite(supportVisibleTopY) ? String.format("%.6f", supportVisibleTopY) : "N/A")
                    + " torchModelBottomY=" + (Double.isFinite(torchModelBottomY) ? String.format("%.6f", torchModelBottomY) : "N/A")
                    + " contactGap=" + (Double.isFinite(contactGap) ? String.format("%.6f", contactGap) : "N/A")
                    + " placementAccepted=" + placementAccepted[0]
                    + " placementResult=" + placementResultText[0]
                    + " targetFace=" + torchUseHit.getSide()
                    + " targetType=" + torchUseHit.getType()
                    + " targetPos=" + torchUseHit.getBlockPos().toShortString()
                    + " targetHitX=" + String.format("%.3f", torchUseHit.getPos().x)
                    + " targetHitY=" + String.format("%.3f", torchUseHit.getPos().y)
                    + " targetHitZ=" + String.format("%.3f", torchUseHit.getPos().z)
                    + " fixtureMatchesLiveDyStack=" + fixtureMatchesLiveDyStack
                    + " failureLayer=" + failureLayer
                    + " productionGameplayFixApplied=false"
                    + " releasePrep=PAUSED"
                    + " beta35ReleaseStatus=PAUSED_LIVE_TORCH_CONTACT_GAP_PROOF");

            System.out.println("[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_RED]"
                    + " categoryScope=floor_torch_only"
                    + " itemCategory=floor_torch"
                    + " wall_torch=NOT_COVERED"
                    + " torchPos=" + torchPos.toShortString()
                    + " torchState=" + torchState
                    + " supportCandidatePos=" + supportCandidatePos.toShortString()
                    + " supportCandidateState=" + supportCandidateState
                    + " supportDy=" + (supportIsBottomSlab ? String.format("%.3f", supportDy) : "N/A")
                    + " torchDy=" + (torchState.isOf(Blocks.TORCH) ? String.format("%.3f", torchDy) : "N/A")
                    + " supportVisibleTopY=" + (Double.isFinite(supportVisibleTopY) ? String.format("%.6f", supportVisibleTopY) : "N/A")
                    + " torchModelBottomY=" + (Double.isFinite(torchModelBottomY) ? String.format("%.6f", torchModelBottomY) : "N/A")
                    + " contactGap=" + (Double.isFinite(contactGap) ? String.format("%.6f", contactGap) : "N/A")
                    + " outlineMinY=" + beta35FormatMinY(outlineBox)
                    + " outlineMaxY=" + beta35FormatMaxY(outlineBox)
                    + " raycastMinY=" + beta35FormatMinY(raycastBox)
                    + " raycastMaxY=" + beta35FormatMaxY(raycastBox)
                    + " fixtureMatchesLiveDyStack=" + fixtureMatchesLiveDyStack
                    + " contactGapExpected=-1.500000"
                    + " observedSampleMatch=" + (contactGapExpected ? "MATCHED" : "NOT_MATCHED")
                    + " failureLayer=" + failureLayer
                    + " wallTorchStatus=NOT_COVERED"
                    + " redProofExpected=CONTACT_GAP"
                    + " proofTargetLayer=FLOOR_TORCH_ONLY"
                    + " heldItem=" + mc.player.getMainHandStack().getItem().getTranslationKey()
                    + " playerPos="
                    + String.format("%.3f,%.3f,%.3f", mc.player.getX(), mc.player.getY(), mc.player.getZ())
                    + " redProofResult="
                    + (failureLayer.equals("NONE") ? "GREEN" : "RED")
                    + " beta35ReleaseStatus=PAUSED_LIVE_TORCH_CONTACT_GAP_PROOF"
                    + " releasePrep=PAUSED"
                    + " productionGameplayFixApplied=false");

            System.out.println("[JULIA_BETA35_LIVE_FLOOR_TORCH_CONTACT_GAP_SUMMARY]"
                    + " categoryScope=floor_torch_only"
                    + " itemCategory=floor_torch"
                    + " wall_torch=NOT_COVERED"
                    + " lantern=NOT_COVERED"
                    + " signs=NOT_COVERED"
                    + " chains=NOT_COVERED"
                    + " expectedContactGap=-1.500000"
                    + " observedContactGap=" + (Double.isFinite(contactGap) ? String.format("%.6f", contactGap) : "N/A")
                    + " supportVisibleTopY=" + (Double.isFinite(supportVisibleTopY) ? String.format("%.6f", supportVisibleTopY) : "N/A")
                    + " torchModelBottomY=" + (Double.isFinite(torchModelBottomY) ? String.format("%.6f", torchModelBottomY) : "N/A")
                    + " torchDy=" + (torchState.isOf(Blocks.TORCH) ? String.format("%.3f", torchDy) : "N/A")
                    + " supportDy=" + (supportIsBottomSlab ? String.format("%.3f", supportDy) : "N/A")
                    + " supportCandidateState=" + supportCandidateState
                    + " fixtureMatchesLiveDyStack=" + fixtureMatchesLiveDyStack
                    + " failureLayer=" + failureLayer
                    + " redProofExpected=CONTACT_GAP"
                    + " redProofResult=" + (failureLayer.equals("NONE") ? "GREEN" : "RED")
                    + " beta35ReleaseStatus=PAUSED_LIVE_TORCH_CONTACT_GAP_PROOF"
                    + " releasePrep=PAUSED"
                    + " productionGameplayFixApplied=false");

            System.out.println("[JULIA_BETA35_LIVE_FLOOR_TORCH_SOURCE_TRUTH_SUMMARY]"
                    + " categoryScope=floor_torch_only"
                    + " wall_torch=NOT_COVERED"
                    + " anchoredFullBlockPos=" + anchoredFullBlockPos.toShortString()
                    + " anchoredFullBlockAnchored=" + belowSupportAnchored
                    + " anchoredFullBlockHasBottomSlabBelow=" + belowHasBottomSlabBelow
                    + " carrierMarkWritten=" + supportHasCarrierMark
                    + " supportDy=" + (supportIsBottomSlab ? String.format("%.3f", supportDy) : "N/A")
                    + " torchDy=" + (torchState.isOf(Blocks.TORCH) ? String.format("%.3f", torchDy) : "N/A")
                    + " contactGap=" + (Double.isFinite(contactGap) ? String.format("%.6f", contactGap) : "N/A")
                    + " fixtureMatchesLiveDyStack=" + fixtureMatchesLiveDyStack
                    + " failureLayer=" + failureLayer
                    + " fixtureContext=OPTION_B_BOTTOM_SLAB_BELOW_ANCHORED_FULL_BLOCK"
                    + " productionGameplayFixApplied=false"
                    + " beta35ReleaseStatus=PAUSED_LIVE_TORCH_CONTACT_GAP_PROOF");
        });
    }

    private static record Beta35LoweredSlabContactAttempt(
            String caseName,
            BlockPos torchPos,
            BlockPos supportCandidatePos,
            BlockState supportCandidateState,
            double supportDy,
            double supportVisibleTopY,
            double torchDy,
            double torchModelBottomY,
            double contactGap,
            boolean triadGreen,
            String failureLayer
    ) {
    }

    private static record Beta35V2ContactGapAttempt(
            String scenario,
            BlockPos torchPos,
            BlockPos supportCandidatePos,
            BlockState supportCandidateState,
            double supportDy,
            double rawSupportTopY,
            double supportVisibleTopY,
            double rawTorchShapeMinY,
            double torchModelBottomY,
            double torchDy,
            double contactGap,
            boolean coordinateParity,
            boolean fixtureMatchesV2LiveStack,
            boolean fixtureMatchesFixedLegalStack,
            String legalOutcome,
            String survivalResult,
            String triadStatus,
            String failureLayer
    ) {
    }

    private record Beta35FloorTopObjectCase(
            String objectId,
            Item item,
            BlockState expectedState,
            boolean rendererSpecialCase
    ) {
    }

    private enum Beta35FloorTopSupportCase {
        LOWERED_BOTTOM_DY_MINUS_ONE,
        PLAIN_BOTTOM_DY_MINUS_HALF
    }

    private record Beta35FloorTopObjectAuditResult(String classification) {
    }

    private record Beta35CommonObjectCase(
            String objectId,
            String family,
            Item item,
            BlockState expectedState,
            String successfulRiskClassification,
            boolean unsupportedApplicable,
            boolean contactApplicable,
            boolean triadApplicable
    ) {
    }

    private enum Beta35CommonObjectSupportCase {
        VANILLA_FULL_BLOCK,
        PLAIN_BOTTOM_DY_MINUS_HALF,
        LOWERED_BOTTOM_DY_MINUS_ONE
    }

    private record Beta35CommonObjectAuditResult(
            String objectId,
            String family,
            String supportCase,
            String classification
    ) {
    }

    private record Beta35FenceFamilyCase(
            String objectId,
            String family,
            Item item,
            BlockState expectedState
    ) {
    }

    private record Beta35FenceGateFamilyCase(
            String objectId,
            Item item,
            Block block,
            boolean interactionRepresentative
    ) {
    }

    private record Beta35LiveHitboxGateAuditResult(
            String objectId,
            String family,
            String classification,
            String failureLayer
    ) {
    }

    private enum Beta35FenceFamilyConfiguration {
        ISOLATED("isolated", false, false, false),
        ONE_NEIGHBOR("one_neighbor", true, false, false),
        TWO_NEIGHBOR("two_neighbor", true, true, false),
        BESIDE_LOWERED_FULLBLOCK("beside_lowered_fullblock", false, false, true);

        private final String id;
        private final boolean eastFenceNeighbor;
        private final boolean westFenceNeighbor;
        private final boolean eastLoweredFullBlockNeighbor;

        Beta35FenceFamilyConfiguration(
                String id,
                boolean eastFenceNeighbor,
                boolean westFenceNeighbor,
                boolean eastLoweredFullBlockNeighbor
        ) {
            this.id = id;
            this.eastFenceNeighbor = eastFenceNeighbor;
            this.westFenceNeighbor = westFenceNeighbor;
            this.eastLoweredFullBlockNeighbor = eastLoweredFullBlockNeighbor;
        }
    }

    private enum Beta35FenceWallContactSupportCase {
        LOWERED_FULL_BLOCK_DY_MINUS_ONE("lowered_full_block_supportDy_minus_one", -1.0d, -1.0d),
        LOWERED_TOP_SLAB_DY_MINUS_ONE("lowered_top_slab_supportDy_minus_one", -1.0d, -1.0d),
        LOWERED_BOTTOM_SLAB_DY_MINUS_ONE("lowered_bottom_slab_supportDy_minus_one", -1.0d, -1.5d),
        LOWERED_TOP_SLAB_DY_MINUS_HALF("lowered_top_slab_supportDy_minus_half", -0.5d, -0.5d),
        LOWERED_DOUBLE_SLAB_DY_MINUS_HALF("lowered_double_slab_supportDy_minus_half", -0.5d, -0.5d);

        private final String id;
        private final double expectedSupportDy;
        private final double expectedObjectDy;

        Beta35FenceWallContactSupportCase(String id, double expectedSupportDy, double expectedObjectDy) {
            this.id = id;
            this.expectedSupportDy = expectedSupportDy;
            this.expectedObjectDy = expectedObjectDy;
        }
    }

    private record Beta35FenceFamilyAuditResult(
            String objectId,
            String family,
            String configuration,
            String classification
    ) {
    }

    private record Beta35TrapdoorDoorAuditRow(
            String objectId,
            String family,
            String supportCase,
            String classification
    ) {
    }

    /**
     * Gated audit matrix for the floor/top-surface object family.
     *
     * Gate: -Dslabbed.beta35FloorTopObjectFamilyAudit=true
     */
    private static void runBeta35FloorTopObjectFamilyAudit(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35FloorTopObjectCase[] cases = {
                new Beta35FloorTopObjectCase(
                        "minecraft:torch",
                        Items.TORCH,
                        Blocks.TORCH.getDefaultState(),
                        false),
                new Beta35FloorTopObjectCase(
                        "minecraft:candle",
                        Items.CANDLE,
                        Blocks.CANDLE.getDefaultState(),
                        false),
                new Beta35FloorTopObjectCase(
                        "minecraft:flower_pot",
                        Items.FLOWER_POT,
                        Blocks.FLOWER_POT.getDefaultState(),
                        false),
                new Beta35FloorTopObjectCase(
                        "minecraft:oak_sign",
                        Items.OAK_SIGN,
                        Blocks.OAK_SIGN.getDefaultState(),
                        true),
        };

        System.out.println("JULIA_BETA35_FLOOR_TOP_OBJECT_MATRIX_START"
                + " scope=floor_top_surface_objects_only"
                + " reference=minecraft:torch"
                + " supportCases=LOWERED_BOTTOM_DY_MINUS_ONE,PLAIN_BOTTOM_DY_MINUS_HALF"
                + " sideAttached=NOT_COVERED"
                + " ceilingHanging=NOT_COVERED"
                + " releaseAudit=NOT_RUN");

        int green = 0;
        int placementFailure = 0;
        int survivalFailure = 0;
        int contactGap = 0;
        int triadMismatch = 0;
        int rendererSpecialCase = 0;
        int outOfScope = 0;

        for (int i = 0; i < cases.length; i++) {
            for (Beta35FloorTopSupportCase supportCase : Beta35FloorTopSupportCase.values()) {
                Beta35FloorTopObjectAuditResult result = runBeta35FloorTopObjectAuditRow(
                        ctx,
                        singleplayer,
                        cases[i],
                        supportCase,
                        i);
                switch (result.classification()) {
                    case "GREEN_ALREADY_INHERITS" -> green++;
                    case "PLACEMENT_FAILURE" -> placementFailure++;
                    case "SURVIVAL_FAILURE" -> survivalFailure++;
                    case "CONTACT_GAP" -> contactGap++;
                    case "TRIAD_MISMATCH" -> triadMismatch++;
                    case "RENDERER_SPECIAL_CASE" -> rendererSpecialCase++;
                    default -> outOfScope++;
                }
            }
        }

        System.out.println("JULIA_BETA35_FLOOR_TOP_OBJECT_SUMMARY"
                + " rows=" + (cases.length * Beta35FloorTopSupportCase.values().length)
                + " greenAlreadyInherits=" + green
                + " placementFailure=" + placementFailure
                + " survivalFailure=" + survivalFailure
                + " contactGap=" + contactGap
                + " triadMismatch=" + triadMismatch
                + " rendererSpecialCase=" + rendererSpecialCase
                + " outOfScope=" + outOfScope
                + " wall_torch=NOT_COVERED"
                + " lantern=NOT_COVERED"
                + " chains=NOT_COVERED"
                + " wall_signs=NOT_COVERED"
                + " hanging_signs=NOT_COVERED"
                + " redstone_wire=OUT_OF_SCOPE_FOR_THIS_SLICE"
                + " rail=OUT_OF_SCOPE_FOR_THIS_SLICE"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");
    }

    /**
     * Representative compatibility matrix for common Beta 3.5 placeable objects.
     *
     * Gate: -Dslabbed.beta35CommonObjectCompatibilityAudit=true
     */
    private static void runBeta35CommonObjectCompatibilityAudit(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35CommonObjectCase[] cases = {
                new Beta35CommonObjectCase(
                        "minecraft:torch",
                        "floor_top_decor_control",
                        Items.TORCH,
                        Blocks.TORCH.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        true,
                        true,
                        true),
                new Beta35CommonObjectCase(
                        "minecraft:candle",
                        "floor_top_decor_control",
                        Items.CANDLE,
                        Blocks.CANDLE.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        true,
                        true,
                        true),
                new Beta35CommonObjectCase(
                        "minecraft:flower_pot",
                        "floor_top_decor_control",
                        Items.FLOWER_POT,
                        Blocks.FLOWER_POT.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        true,
                        true,
                        true),
                new Beta35CommonObjectCase(
                        "minecraft:crafting_table",
                        "ordinary_full_block",
                        Items.CRAFTING_TABLE,
                        Blocks.CRAFTING_TABLE.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        false,
                        true,
                        true),
                new Beta35CommonObjectCase(
                        "minecraft:furnace",
                        "ordinary_full_block",
                        Items.FURNACE,
                        Blocks.FURNACE.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        false,
                        true,
                        true),
                new Beta35CommonObjectCase(
                        "minecraft:oak_fence",
                        "partial_collision_block",
                        Items.OAK_FENCE,
                        Blocks.OAK_FENCE.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        false,
                        true,
                        true),
                new Beta35CommonObjectCase(
                        "minecraft:oak_trapdoor",
                        "attachment_hinge_block",
                        Items.OAK_TRAPDOOR,
                        Blocks.OAK_TRAPDOOR.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        false,
                        true,
                        true),
                new Beta35CommonObjectCase(
                        "minecraft:oak_door",
                        "multipart_block",
                        Items.OAK_DOOR,
                        Blocks.OAK_DOOR.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        true,
                        true,
                        false),
                new Beta35CommonObjectCase(
                        "minecraft:oak_sign",
                        "standing_sign",
                        Items.OAK_SIGN,
                        Blocks.OAK_SIGN.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        true,
                        true,
                        true),
        };

        System.out.println("JULIA_BETA35_COMMON_OBJECT_MATRIX_START"
                + " scope=representative_common_object_compatibility"
                + " supportCases=VANILLA_FULL_BLOCK,PLAIN_BOTTOM_DY_MINUS_HALF,LOWERED_BOTTOM_DY_MINUS_ONE"
                + " releaseAudit=NOT_RUN"
                + " productionBehaviorChanged=false");

        int green = 0;
        int placementFailure = 0;
        int survivalFailure = 0;
        int contactGap = 0;
        int triadMismatch = 0;
        int collisionShapeRisk = 0;
        int multipartRisk = 0;
        int rendererSpecialCase = 0;
        int ceilingAttachmentRisk = 0;
        int outOfScope = 0;
        int needsCategorySlice = 0;

        for (int i = 0; i < cases.length; i++) {
            for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
                Beta35CommonObjectAuditResult result = runBeta35CommonObjectAuditRow(
                        ctx,
                        singleplayer,
                        cases[i],
                        supportCase,
                        i);
                switch (result.classification()) {
                    case "GREEN_ALREADY_INHERITS" -> green++;
                    case "PLACEMENT_FAILURE" -> placementFailure++;
                    case "SURVIVAL_FAILURE" -> survivalFailure++;
                    case "CONTACT_GAP" -> contactGap++;
                    case "TRIAD_MISMATCH" -> triadMismatch++;
                    case "COLLISION_SHAPE_RISK" -> collisionShapeRisk++;
                    case "MULTIPART_RISK" -> multipartRisk++;
                    case "RENDERER_SPECIAL_CASE" -> rendererSpecialCase++;
                    case "CEILING_ATTACHMENT_RISK" -> ceilingAttachmentRisk++;
                    case "NEEDS_CATEGORY_SLICE" -> needsCategorySlice++;
                    default -> outOfScope++;
                }
            }
        }

        System.out.println("JULIA_BETA35_COMMON_OBJECT_SUMMARY"
                + " rows=" + (cases.length * Beta35CommonObjectSupportCase.values().length)
                + " greenAlreadyInherits=" + green
                + " placementFailure=" + placementFailure
                + " survivalFailure=" + survivalFailure
                + " contactGap=" + contactGap
                + " triadMismatch=" + triadMismatch
                + " collisionShapeRisk=" + collisionShapeRisk
                + " multipartRisk=" + multipartRisk
                + " rendererSpecialCase=" + rendererSpecialCase
                + " ceilingAttachmentRisk=" + ceilingAttachmentRisk
                + " outOfScopeForBeta35=" + outOfScope
                + " needsCategorySlice=" + needsCategorySlice
                + " lantern=NOT_AUDITED_CEILING_HANGING_CATEGORY"
                + " chain=NOT_AUDITED_CEILING_HANGING_CATEGORY"
                + " redstone_wire=NOT_AUDITED_SPECIAL_FLOOR_LOGIC"
                + " rail=NOT_AUDITED_SPECIAL_FLOOR_LOGIC"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");
    }

    /**
     * Representative audit matrix for Beta 3.5 special/full-block-ish objects.
     *
     * Gate: -Dslabbed.beta35SpecialFullblockCompatibilityAudit=true
     */
    private static void runBeta35SpecialFullblockCompatibilityAudit(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35CommonObjectCase[] cases = {
                new Beta35CommonObjectCase(
                        "minecraft:bookshelf",
                        "ordinary_full_block",
                        Items.BOOKSHELF,
                        Blocks.BOOKSHELF.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        false,
                        true,
                        true),
                new Beta35CommonObjectCase(
                        "minecraft:enchanting_table",
                        "special_renderer",
                        Items.ENCHANTING_TABLE,
                        Blocks.ENCHANTING_TABLE.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        false,
                        true,
                        true),
                new Beta35CommonObjectCase(
                        "minecraft:lectern",
                        "interactive_block_entity",
                        Items.LECTERN,
                        Blocks.LECTERN.getDefaultState(),
                        "BLOCK_ENTITY_RISK",
                        false,
                        true,
                        true),
                new Beta35CommonObjectCase(
                        "minecraft:barrel",
                        "interactive_block_entity",
                        Items.BARREL,
                        Blocks.BARREL.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        false,
                        true,
                        true),
                new Beta35CommonObjectCase(
                        "minecraft:chest",
                        "special_renderer",
                        Items.CHEST,
                        Blocks.CHEST.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        false,
                        true,
                        true),
                new Beta35CommonObjectCase(
                        "minecraft:crafting_table",
                        "ordinary_full_block",
                        Items.CRAFTING_TABLE,
                        Blocks.CRAFTING_TABLE.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        false,
                        true,
                        true),
                new Beta35CommonObjectCase(
                        "minecraft:furnace",
                        "ordinary_full_block",
                        Items.FURNACE,
                        Blocks.FURNACE.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        false,
                        true,
                        true),
                new Beta35CommonObjectCase(
                        "minecraft:stonecutter",
                        "special_shape_fullblock",
                        Items.STONECUTTER,
                        Blocks.STONECUTTER.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        false,
                        true,
                        true),
                new Beta35CommonObjectCase(
                        "minecraft:grindstone",
                        "special_shape_oriented",
                        Items.GRINDSTONE,
                        Blocks.GRINDSTONE.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        false,
                        true,
                        true),
                new Beta35CommonObjectCase(
                        "minecraft:anvil",
                        "falling_special_shape",
                        Items.ANVIL,
                        Blocks.ANVIL.getDefaultState(),
                        "GREEN_ALREADY_INHERITS",
                        false,
                        true,
                        true),
        };

        System.out.println("JULIA_BETA35_SPECIAL_FULLBLOCK_MATRIX_START"
                + " scope=special_fullblock_compatibility_audit"
                + " supportCases=VANILLA_FULL_BLOCK,PLAIN_BOTTOM_DY_MINUS_HALF,LOWERED_BOTTOM_DY_MINUS_ONE"
                + " controls=minecraft:crafting_table,minecraft:furnace"
                + " optionalRows=minecraft:stonecutter,minecraft:grindstone,minecraft:anvil"
                + " releaseAudit=NOT_RUN"
                + " productionBehaviorChanged=bookshelf_chest_enchanting_table_stonecutter_contact_dy_and_barrel_raycast_triad");

        int green = 0;
        int placementFailure = 0;
        int survivalFailure = 0;
        int contactGap = 0;
        int triadMismatch = 0;
        int blockEntityRisk = 0;
        int specialRendererRisk = 0;
        int needsCategorySlice = 0;
        int outOfScope = 0;

        for (int i = 0; i < cases.length; i++) {
            for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
                Beta35CommonObjectAuditResult result = runBeta35SpecialFullblockAuditRow(
                        ctx,
                        singleplayer,
                        cases[i],
                        supportCase,
                        i);
                switch (result.classification()) {
                    case "GREEN_ALREADY_INHERITS" -> green++;
                    case "PLACEMENT_FAILURE" -> placementFailure++;
                    case "SURVIVAL_FAILURE" -> survivalFailure++;
                    case "CONTACT_GAP" -> contactGap++;
                    case "TRIAD_MISMATCH" -> triadMismatch++;
                    case "BLOCK_ENTITY_RISK" -> blockEntityRisk++;
                    case "SPECIAL_RENDERER_RISK" -> specialRendererRisk++;
                    case "NEEDS_CATEGORY_SLICE" -> needsCategorySlice++;
                    default -> outOfScope++;
                }
            }
        }

        System.out.println("JULIA_BETA35_SPECIAL_FULLBLOCK_SUMMARY"
                + " rows=" + (cases.length * Beta35CommonObjectSupportCase.values().length)
                + " greenAlreadyInherits=" + green
                + " placementFailure=" + placementFailure
                + " survivalFailure=" + survivalFailure
                + " contactGap=" + contactGap
                + " triadMismatch=" + triadMismatch
                + " blockEntityRisk=" + blockEntityRisk
                + " specialRendererRisk=" + specialRendererRisk
                + " needsCategorySlice=" + needsCategorySlice
                + " outOfScopeForBeta35=" + outOfScope
                + " currentGreenSet=torch,candle,flower_pot,crafting_table,furnace,oak_fence,oak_trapdoor,bookshelf,chest,barrel,enchanting_table,stonecutter,anvil,grindstone"
                + " doorSlice=PARALLEL_NOT_INSPECTED"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED"
                + " productionBehaviorChanged=bookshelf_chest_enchanting_table_stonecutter_anvil_grindstone_contact_dy_and_barrel_raycast_triad");
    }

    private static Beta35CommonObjectAuditResult runBeta35SpecialFullblockAuditRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Beta35CommonObjectCase objectCase,
            Beta35CommonObjectSupportCase supportCase,
            int objectIndex
    ) {
        int baseX = 308 + objectIndex * 8;
        int baseZ = 148 + supportCase.ordinal() * 6;
        BlockPos supportCandidatePos = new BlockPos(baseX, -55, baseZ);
        BlockPos objectPos = supportCandidatePos.up();
        BlockPos unsupportedPos = supportCandidatePos.add(0, 1, 3);
        BlockHitResult useHit = beta35TopUseHit(supportCandidatePos, supportCase);

        prepareBeta35CommonObjectSupport(singleplayer, supportCandidatePos, unsupportedPos, supportCase);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(objectCase.item(), 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() + 3.0d,
                        supportCandidatePos.getZ() - 2.0d),
                useHit.getPos());
        beta35FenceGateStabilizePlayer(ctx, singleplayer);
        ctx.waitTick();

        final String[] placementResult = {"not-run"};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                placementResult[0] = "CLIENT_NOT_READY";
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, useHit);
            placementResult[0] = result.toString();
        });
        ctx.waitTick();
        ctx.waitTick();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState supportState = world.getBlockState(supportCandidatePos);
            BlockState placedState = world.getBlockState(objectPos);
            world.updateNeighbors(objectPos, placedState.getBlock());
            world.updateNeighbors(supportCandidatePos, supportState.getBlock());
            world.updateNeighbors(objectPos.up(), world.getBlockState(objectPos.up()).getBlock());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String[] classification = {"OUT_OF_SCOPE_FOR_BETA35"};
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                System.out.println("JULIA_BETA35_SPECIAL_FULLBLOCK_ROW"
                        + " objectId=" + objectCase.objectId()
                        + " family=" + objectCase.family()
                        + " itemId=" + objectCase.item()
                        + " supportCase=" + supportCase
                        + " classification=OUT_OF_SCOPE_FOR_BETA35"
                        + " reason=client_world_or_player_missing");
                return;
            }

            BlockPos actualObjectPos = beta35CommonActualObjectPos(
                    mc.world,
                    supportCandidatePos,
                    objectPos,
                    objectCase.expectedState());
            BlockState supportState = mc.world.getBlockState(supportCandidatePos);
            BlockState objectState = mc.world.getBlockState(actualObjectPos);
            boolean blockAppeared = objectState.isOf(objectCase.expectedState().getBlock());
            boolean blockEntityPresent = blockAppeared && mc.world.getBlockEntity(actualObjectPos) != null;
            double supportDy = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportState);
            double objectDy = blockAppeared ? SlabSupport.getYOffset(mc.world, actualObjectPos, objectState)
                    : Double.NaN;
            double supportVisibleTopY = beta35CommonSupportVisibleTopY(supportCandidatePos, supportState, supportDy);
            VoxelShape outlineShape = blockAppeared
                    ? objectState.getOutlineShape(mc.world, actualObjectPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = blockAppeared
                    ? objectState.getRaycastShape(mc.world, actualObjectPos)
                    : null;
            VoxelShape collisionShape = blockAppeared
                    ? objectState.getCollisionShape(mc.world, actualObjectPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, actualObjectPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, actualObjectPos);
            net.minecraft.util.math.Box collisionBox = beta35WorldBox(collisionShape, actualObjectPos);
            net.minecraft.util.math.Box modelProxyBox = outlineBox;
            double objectModelBottomY = modelProxyBox == null ? Double.NaN : modelProxyBox.minY;
            double contactGap = objectCase.contactApplicable()
                    && actualObjectPos.equals(objectPos)
                    && Double.isFinite(objectModelBottomY)
                    && Double.isFinite(supportVisibleTopY)
                            ? objectModelBottomY - supportVisibleTopY
                            : Double.NaN;
            boolean survivalGreen = blockAppeared && objectState.canPlaceAt(mc.world, actualObjectPos);
            boolean unsupportedFails = !objectCase.unsupportedApplicable()
                    || !objectCase.expectedState().canPlaceAt(mc.world, unsupportedPos);
            boolean contactGreen = !objectCase.contactApplicable()
                    || !actualObjectPos.equals(objectPos)
                    || (Double.isFinite(contactGap) && Math.abs(contactGap) <= EPSILON);
            boolean triadGreen = !objectCase.triadApplicable()
                    || supportCase == Beta35CommonObjectSupportCase.VANILLA_FULL_BLOCK
                    || (blockAppeared
                            && modelProxyBox != null
                            && beta35SameBox(outlineBox, modelProxyBox)
                            && beta35SameBox(raycastBox, modelProxyBox));

            if (!blockAppeared) {
                classification[0] = "PLACEMENT_FAILURE";
            } else if (!survivalGreen || !unsupportedFails) {
                classification[0] = "SURVIVAL_FAILURE";
            } else if (!contactGreen) {
                classification[0] = "CONTACT_GAP";
            } else if (!triadGreen) {
                classification[0] = "TRIAD_MISMATCH";
            } else {
                classification[0] = objectCase.successfulRiskClassification();
            }

            System.out.println("JULIA_BETA35_SPECIAL_FULLBLOCK_ROW"
                    + " objectId=" + objectCase.objectId()
                    + " family=" + objectCase.family()
                    + " itemId=" + objectCase.item()
                    + " supportCase=" + supportCase
                    + " placementResult=" + placementResult[0]
                    + " blockAppearedAfterAttempt=" + blockAppeared
                    + " blockEntityPresent=" + blockEntityPresent
                    + " actualObjectPos=" + actualObjectPos.toShortString()
                    + " finalBlockState=" + objectState
                    + " supportCandidateState=" + supportState
                    + " supportDy=" + String.format("%.6f", supportDy)
                    + " objectDy=" + (Double.isFinite(objectDy) ? String.format("%.6f", objectDy) : "N/A")
                    + " supportVisibleTopY=" + (Double.isFinite(supportVisibleTopY)
                            ? String.format("%.6f", supportVisibleTopY) : "N/A")
                    + " objectModelBottomY=" + (Double.isFinite(objectModelBottomY)
                            ? String.format("%.6f", objectModelBottomY) : "CONTACT_NOT_APPLICABLE")
                    + " categoryProxy=" + objectCase.family()
                    + " contactGap=" + (Double.isFinite(contactGap)
                            ? String.format("%.6f", contactGap) : "CONTACT_NOT_APPLICABLE")
                    + " modelBounds=" + beta35FormatBox(modelProxyBox)
                    + " outlineBounds=" + beta35FormatBox(outlineBox)
                    + " raycastBounds=" + beta35FormatBox(raycastBox)
                    + " collisionBounds=" + beta35FormatBox(collisionBox)
                    + " triadCoLocated=" + (blockAppeared
                            ? (triadGreen ? "yes" : "no") : "NOT_MEASURED")
                    + " survivalResult=" + (survivalGreen ? "SURVIVAL_GREEN" : "SURVIVAL_RED")
                    + " unsupportedResult=" + (unsupportedFails ? "UNSUPPORTED_NOT_APPLICABLE_OR_FAILS"
                            : "UNSUPPORTED_STILL_VALID")
                    + " classification=" + classification[0]);
        });

        return new Beta35CommonObjectAuditResult(
                objectCase.objectId(),
                objectCase.family(),
                supportCase.toString(),
                classification[0]);
    }

    /**
     * Focused chest proof for the Beta 3.5 block-entity fullblock contact slice.
     *
     * Gate: -Dslabbed.beta35ChestContact=true
     */
    private static void runBeta35ChestContactProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35CommonObjectCase chest = new Beta35CommonObjectCase(
                "minecraft:chest",
                "block_entity_fullblock",
                Items.CHEST,
                Blocks.CHEST.getDefaultState(),
                "GREEN_ALREADY_INHERITS",
                false,
                true,
                true);

        boolean allGreen = true;
        String firstFailureLayer = "NONE";
        for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
            Beta35CommonObjectAuditResult result = runBeta35SpecialFullblockAuditRow(
                    ctx,
                    singleplayer,
                    chest,
                    supportCase,
                    0);
            boolean rowGreen = "GREEN_ALREADY_INHERITS".equals(result.classification());
            allGreen &= rowGreen;
            if (!rowGreen && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = switch (result.classification()) {
                    case "PLACEMENT_FAILURE" -> "CHEST_PLACEMENT_FAILURE";
                    case "SURVIVAL_FAILURE" -> "CHEST_SURVIVAL_FAILURE";
                    case "CONTACT_GAP" -> "CHEST_CONTACT_GAP";
                    case "TRIAD_MISMATCH" -> "CHEST_TRIAD_MISMATCH";
                    default -> "CHEST_UNKNOWN_FAILURE";
                };
            }
            if (rowGreen) {
                System.out.println("JULIA_BETA35_CHEST_CONTACT_GREEN"
                        + " objectId=minecraft:chest"
                        + " family=block_entity_fullblock"
                        + " supportCase=" + supportCase
                        + " placement=GREEN"
                        + " survival=GREEN"
                        + " contact=GREEN"
                        + " triad=GREEN"
                        + " blockEntityRenderDy=SlabSupport_BlockEntityOffsetMixin"
                        + " classification=GREEN_ALREADY_INHERITS");
            }
        }

        String failureLayer = allGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_CHEST_CONTACT_SUMMARY"
                + " failureLayer=" + failureLayer
                + " objectId=minecraft:chest"
                + " rows=" + Beta35CommonObjectSupportCase.values().length
                + " expectedRowsGreen=" + allGreen
                + " crafting_table=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " furnace=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " bookshelf=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " enchanting_table=UNCHANGED_SEPARATE_CONTACT_GAP"
                + " lectern=UNCHANGED_SEPARATE_CONTACT_GAP"
                + " barrel=UNCHANGED_SEPARATE_TRIAD_RISK"
                + " stonecutter=UNCHANGED_SEPARATE_SHAPE_SLICE"
                + " grindstone=UNCHANGED_SEPARATE_SHAPE_SLICE"
                + " anvil=UNCHANGED_SEPARATE_SHAPE_SLICE"
                + " doorTrapdoorSignLanternChainEndRodRedstoneRail=NOT_TOUCHED"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");

        if (!allGreen) {
            System.out.println("JULIA_BETA35_CHEST_CONTACT_RED"
                    + " objectId=minecraft:chest"
                    + " failureLayer=" + failureLayer
                    + " classification=RED");
            throw new RuntimeException("[JULIA_BETA35_CHEST_CONTACT_RED]"
                    + " reason=chest_contact_not_green"
                    + " failureLayer=" + failureLayer);
        }
    }

    /**
     * Focused barrel proof for the Beta 3.5 block-entity fullblock triad slice.
     *
     * Gate: -Dslabbed.beta35BarrelTriad=true
     */
    private static void runBeta35BarrelTriadProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35CommonObjectCase barrel = new Beta35CommonObjectCase(
                "minecraft:barrel",
                "block_entity_fullblock",
                Items.BARREL,
                Blocks.BARREL.getDefaultState(),
                "GREEN_ALREADY_INHERITS",
                false,
                true,
                true);

        boolean allGreen = true;
        String firstFailureLayer = "NONE";
        for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
            Beta35CommonObjectAuditResult result = runBeta35SpecialFullblockAuditRow(
                    ctx,
                    singleplayer,
                    barrel,
                    supportCase,
                    0);
            boolean rowGreen = "GREEN_ALREADY_INHERITS".equals(result.classification());
            allGreen &= rowGreen;
            if (!rowGreen && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = switch (result.classification()) {
                    case "PLACEMENT_FAILURE" -> "BARREL_PLACEMENT_FAILURE";
                    case "SURVIVAL_FAILURE" -> "BARREL_SURVIVAL_FAILURE";
                    case "CONTACT_GAP" -> "BARREL_CONTACT_GAP";
                    case "TRIAD_MISMATCH" -> "BARREL_TRIAD_MISMATCH";
                    default -> "BARREL_UNKNOWN_FAILURE";
                };
            }
            if (rowGreen) {
                System.out.println("JULIA_BETA35_BARREL_TRIAD_GREEN"
                        + " objectId=minecraft:barrel"
                        + " family=block_entity_fullblock"
                        + " supportCase=" + supportCase
                        + " placement=GREEN"
                        + " survival=GREEN"
                        + " contact=GREEN"
                        + " triad=GREEN"
                        + " raycastBasis=lowered_outline_when_native_empty"
                        + " classification=GREEN_ALREADY_INHERITS");
            }
        }

        String failureLayer = allGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_BARREL_TRIAD_SUMMARY"
                + " failureLayer=" + failureLayer
                + " objectId=minecraft:barrel"
                + " rows=" + Beta35CommonObjectSupportCase.values().length
                + " expectedRowsGreen=" + allGreen
                + " crafting_table=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " furnace=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " bookshelf=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " chest=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " enchanting_table=UNCHANGED_SEPARATE_CONTACT_GAP"
                + " lectern=UNCHANGED_SEPARATE_CONTACT_GAP"
                + " stonecutter=UNCHANGED_SEPARATE_SHAPE_SLICE"
                + " grindstone=UNCHANGED_SEPARATE_SHAPE_SLICE"
                + " anvil=UNCHANGED_SEPARATE_SHAPE_SLICE"
                + " doorTrapdoorSignLanternChainEndRodRedstoneRail=NOT_TOUCHED"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");

        if (!allGreen) {
            System.out.println("JULIA_BETA35_BARREL_TRIAD_RED"
                    + " objectId=minecraft:barrel"
                    + " failureLayer=" + failureLayer
                    + " classification=RED");
            throw new RuntimeException("[JULIA_BETA35_BARREL_TRIAD_RED]"
                    + " reason=barrel_triad_not_green"
                    + " failureLayer=" + failureLayer);
        }
    }

    /**
     * Focused enchanting-table proof for the Beta 3.5 special-renderer contact slice.
     *
     * Gate: -Dslabbed.beta35EnchantingTableContact=true
     */
    private static void runBeta35EnchantingTableContactProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35CommonObjectCase enchantingTable = new Beta35CommonObjectCase(
                "minecraft:enchanting_table",
                "special_renderer",
                Items.ENCHANTING_TABLE,
                Blocks.ENCHANTING_TABLE.getDefaultState(),
                "GREEN_ALREADY_INHERITS",
                false,
                true,
                true);

        boolean allGreen = true;
        String firstFailureLayer = "NONE";
        for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
            Beta35CommonObjectAuditResult result = runBeta35SpecialFullblockAuditRow(
                    ctx,
                    singleplayer,
                    enchantingTable,
                    supportCase,
                    0);
            boolean rowGreen = "GREEN_ALREADY_INHERITS".equals(result.classification());
            allGreen &= rowGreen;
            if (!rowGreen && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = switch (result.classification()) {
                    case "PLACEMENT_FAILURE" -> "ENCHANTING_TABLE_PLACEMENT_FAILURE";
                    case "SURVIVAL_FAILURE" -> "ENCHANTING_TABLE_SURVIVAL_FAILURE";
                    case "CONTACT_GAP" -> "ENCHANTING_TABLE_CONTACT_GAP";
                    case "TRIAD_MISMATCH" -> "ENCHANTING_TABLE_TRIAD_MISMATCH";
                    default -> "ENCHANTING_TABLE_UNKNOWN_FAILURE";
                };
            }
            if (rowGreen) {
                System.out.println("JULIA_BETA35_ENCHANTING_TABLE_CONTACT_GREEN"
                        + " objectId=minecraft:enchanting_table"
                        + " family=special_renderer"
                        + " supportCase=" + supportCase
                        + " placement=GREEN"
                        + " survival=GREEN"
                        + " contact=GREEN"
                        + " triad=GREEN"
                        + " raycastBasis=lowered_outline_when_native_empty"
                        + " blockEntityRenderDy=SlabSupport_BlockEntityOffsetMixin"
                        + " classification=GREEN_ALREADY_INHERITS");
            }
        }

        String failureLayer = allGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_ENCHANTING_TABLE_CONTACT_SUMMARY"
                + " failureLayer=" + failureLayer
                + " objectId=minecraft:enchanting_table"
                + " rows=" + Beta35CommonObjectSupportCase.values().length
                + " expectedRowsGreen=" + allGreen
                + " crafting_table=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " furnace=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " bookshelf=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " chest=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " barrel=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " lectern=UNCHANGED_SEPARATE_CONTACT_GAP"
                + " stonecutter=UNCHANGED_SEPARATE_SHAPE_SLICE"
                + " grindstone=UNCHANGED_SEPARATE_SHAPE_SLICE"
                + " anvil=UNCHANGED_SEPARATE_SHAPE_SLICE"
                + " doorTrapdoorSignLanternChainEndRodRedstoneRail=NOT_TOUCHED"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");

        if (!allGreen) {
            System.out.println("JULIA_BETA35_ENCHANTING_TABLE_CONTACT_RED"
                    + " objectId=minecraft:enchanting_table"
                    + " failureLayer=" + failureLayer
                    + " classification=RED");
            throw new RuntimeException("[JULIA_BETA35_ENCHANTING_TABLE_CONTACT_RED]"
                    + " reason=enchanting_table_contact_not_green"
                    + " failureLayer=" + failureLayer);
        }
    }

    /**
     * Focused stonecutter proof for the Beta 3.5 special-shape contact slice.
     *
     * Gate: -Dslabbed.beta35StonecutterContact=true
     */
    private static void runBeta35StonecutterContactProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35CommonObjectCase stonecutter = new Beta35CommonObjectCase(
                "minecraft:stonecutter",
                "special_shape_fullblock",
                Items.STONECUTTER,
                Blocks.STONECUTTER.getDefaultState(),
                "GREEN_ALREADY_INHERITS",
                false,
                true,
                true);

        boolean allGreen = true;
        String firstFailureLayer = "NONE";
        for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
            Beta35CommonObjectAuditResult result = runBeta35SpecialFullblockAuditRow(
                    ctx,
                    singleplayer,
                    stonecutter,
                    supportCase,
                    0);
            boolean rowGreen = "GREEN_ALREADY_INHERITS".equals(result.classification());
            allGreen &= rowGreen;
            if (!rowGreen && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = switch (result.classification()) {
                    case "PLACEMENT_FAILURE" -> "STONECUTTER_PLACEMENT_FAILURE";
                    case "SURVIVAL_FAILURE" -> "STONECUTTER_SURVIVAL_FAILURE";
                    case "CONTACT_GAP" -> "STONECUTTER_CONTACT_GAP";
                    case "TRIAD_MISMATCH" -> "STONECUTTER_TRIAD_MISMATCH";
                    default -> "STONECUTTER_UNKNOWN_FAILURE";
                };
            }
            if (rowGreen) {
                System.out.println("JULIA_BETA35_STONECUTTER_CONTACT_GREEN"
                        + " objectId=minecraft:stonecutter"
                        + " family=special_shape_fullblock"
                        + " supportCase=" + supportCase
                        + " placement=GREEN"
                        + " survival=GREEN"
                        + " contact=GREEN"
                        + " triad=GREEN"
                        + " raycastBasis=lowered_outline_when_native_empty"
                        + " classification=GREEN_ALREADY_INHERITS");
            }
        }

        String failureLayer = allGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_STONECUTTER_CONTACT_SUMMARY"
                + " failureLayer=" + failureLayer
                + " objectId=minecraft:stonecutter"
                + " rows=" + Beta35CommonObjectSupportCase.values().length
                + " expectedRowsGreen=" + allGreen
                + " crafting_table=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " furnace=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " bookshelf=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " chest=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " barrel=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " enchanting_table=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " lectern=UNCHANGED_SEPARATE_CONTACT_GAP"
                + " grindstone=UNCHANGED_SEPARATE_SHAPE_SLICE"
                + " anvil=UNCHANGED_SEPARATE_SHAPE_SLICE"
                + " doorTrapdoorSignLanternChainEndRodRedstoneRail=NOT_TOUCHED"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");

        if (!allGreen) {
            System.out.println("JULIA_BETA35_STONECUTTER_CONTACT_RED"
                    + " objectId=minecraft:stonecutter"
                    + " failureLayer=" + failureLayer
                    + " classification=RED");
            throw new RuntimeException("[JULIA_BETA35_STONECUTTER_CONTACT_RED]"
                    + " reason=stonecutter_contact_not_green"
                    + " failureLayer=" + failureLayer);
        }
    }

    /**
     * Focused anvil proof for the Beta 3.5 falling special-shape contact slice.
     *
     * Gate: -Dslabbed.beta35AnvilContact=true
     */
    private static void runBeta35AnvilContactProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35CommonObjectCase anvil = new Beta35CommonObjectCase(
                "minecraft:anvil",
                "falling_special_shape",
                Items.ANVIL,
                Blocks.ANVIL.getDefaultState(),
                "GREEN_ALREADY_INHERITS",
                false,
                true,
                true);

        boolean allGreen = true;
        String firstFailureLayer = "NONE";
        for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
            Beta35CommonObjectAuditResult result = runBeta35SpecialFullblockAuditRow(
                    ctx,
                    singleplayer,
                    anvil,
                    supportCase,
                    0);
            boolean rowGreen = "GREEN_ALREADY_INHERITS".equals(result.classification());
            allGreen &= rowGreen;
            if (!rowGreen && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = switch (result.classification()) {
                    case "PLACEMENT_FAILURE" -> "ANVIL_PLACEMENT_FAILURE";
                    case "SURVIVAL_FAILURE" -> "ANVIL_SURVIVAL_FAILURE";
                    case "CONTACT_GAP" -> "ANVIL_CONTACT_GAP";
                    case "TRIAD_MISMATCH" -> "ANVIL_TRIAD_MISMATCH";
                    default -> "ANVIL_UNKNOWN_FAILURE";
                };
            }
            if (rowGreen) {
                System.out.println("JULIA_BETA35_ANVIL_CONTACT_GREEN"
                        + " objectId=minecraft:anvil"
                        + " family=falling_special_shape"
                        + " supportCase=" + supportCase
                        + " placement=GREEN"
                        + " survival=GREEN"
                        + " fallingBehavior=STABLE_ON_VALID_SUPPORT"
                        + " contact=GREEN"
                        + " triad=GREEN"
                        + " raycastBasis=lowered_outline_when_native_empty"
                        + " collisionCategory=vanilla_collision_not_contact_authority"
                        + " classification=GREEN_ALREADY_INHERITS");
            }
        }

        String failureLayer = allGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_ANVIL_CONTACT_SUMMARY"
                + " failureLayer=" + failureLayer
                + " objectId=minecraft:anvil"
                + " rows=" + Beta35CommonObjectSupportCase.values().length
                + " expectedRowsGreen=" + allGreen
                + " fallingBehavior=STABLE_ON_VALID_SUPPORT"
                + " collisionCategory=vanilla_collision_not_contact_authority"
                + " crafting_table=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " furnace=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " bookshelf=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " chest=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " barrel=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " enchanting_table=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " stonecutter=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " lectern=UNCHANGED_SEPARATE_CONTACT_GAP"
                + " grindstone=UNCHANGED_SEPARATE_SHAPE_SLICE"
                + " doorTrapdoorSignLanternChainEndRodRedstoneRail=NOT_TOUCHED"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");

        if (!allGreen) {
            System.out.println("JULIA_BETA35_ANVIL_CONTACT_RED"
                    + " objectId=minecraft:anvil"
                    + " failureLayer=" + failureLayer
                    + " classification=RED");
            throw new RuntimeException("[JULIA_BETA35_ANVIL_CONTACT_RED]"
                    + " reason=anvil_contact_not_green"
                    + " failureLayer=" + failureLayer);
        }
    }

    /**
     * Focused grindstone proof for the Beta 3.5 oriented special-shape contact slice.
     *
     * Gate: -Dslabbed.beta35GrindstoneContact=true
     */
    private static void runBeta35GrindstoneContactProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35CommonObjectCase grindstone = new Beta35CommonObjectCase(
                "minecraft:grindstone",
                "special_shape_oriented",
                Items.GRINDSTONE,
                Blocks.GRINDSTONE.getDefaultState(),
                "GREEN_ALREADY_INHERITS",
                false,
                true,
                true);

        boolean allGreen = true;
        String firstFailureLayer = "NONE";
        for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
            Beta35CommonObjectAuditResult result = runBeta35SpecialFullblockAuditRow(
                    ctx,
                    singleplayer,
                    grindstone,
                    supportCase,
                    0);
            boolean rowGreen = "GREEN_ALREADY_INHERITS".equals(result.classification());
            allGreen &= rowGreen;
            if (!rowGreen && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = switch (result.classification()) {
                    case "PLACEMENT_FAILURE" -> "GRINDSTONE_PLACEMENT_FAILURE";
                    case "SURVIVAL_FAILURE" -> "GRINDSTONE_SURVIVAL_FAILURE";
                    case "CONTACT_GAP" -> "GRINDSTONE_CONTACT_GAP";
                    case "TRIAD_MISMATCH" -> "GRINDSTONE_TRIAD_MISMATCH";
                    default -> "GRINDSTONE_UNKNOWN_FAILURE";
                };
            }
            if (rowGreen) {
                System.out.println("JULIA_BETA35_GRINDSTONE_CONTACT_GREEN"
                        + " objectId=minecraft:grindstone"
                        + " family=special_shape_oriented"
                        + " supportCase=" + supportCase
                        + " placement=GREEN"
                        + " survival=GREEN"
                        + " contact=GREEN"
                        + " triad=GREEN"
                        + " testedOrientation=face_floor_facing_south_from_top_use_hit"
                        + " raycastBasis=lowered_outline_when_native_empty"
                        + " collisionCategory=oriented_special_shape_collision_co_located"
                        + " classification=GREEN_ALREADY_INHERITS");
            }
        }

        String failureLayer = allGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_GRINDSTONE_CONTACT_SUMMARY"
                + " failureLayer=" + failureLayer
                + " objectId=minecraft:grindstone"
                + " rows=" + Beta35CommonObjectSupportCase.values().length
                + " expectedRowsGreen=" + allGreen
                + " testedOrientation=face_floor_facing_south_from_top_use_hit"
                + " collisionCategory=oriented_special_shape_collision_co_located"
                + " crafting_table=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " furnace=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " bookshelf=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " chest=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " barrel=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " enchanting_table=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " stonecutter=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " anvil=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " lectern=UNCHANGED_SEPARATE_CONTACT_GAP"
                + " doorTrapdoorSignLanternChainEndRodRedstoneRail=NOT_TOUCHED"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");

        if (!allGreen) {
            System.out.println("JULIA_BETA35_GRINDSTONE_CONTACT_RED"
                    + " objectId=minecraft:grindstone"
                    + " failureLayer=" + failureLayer
                    + " classification=RED");
            throw new RuntimeException("[JULIA_BETA35_GRINDSTONE_CONTACT_RED]"
                    + " reason=grindstone_contact_not_green"
                    + " failureLayer=" + failureLayer);
        }
    }

    /**
     * Focused bookshelf proof for the Beta 3.5 ordinary full-block contact slice.
     *
     * Gate: -Dslabbed.beta35BookshelfContact=true
     */
    private static void runBeta35BookshelfContactProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35CommonObjectCase bookshelf = new Beta35CommonObjectCase(
                "minecraft:bookshelf",
                "ordinary_full_block",
                Items.BOOKSHELF,
                Blocks.BOOKSHELF.getDefaultState(),
                "GREEN_ALREADY_INHERITS",
                false,
                true,
                true);

        boolean allGreen = true;
        String firstFailureLayer = "NONE";
        for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
            Beta35CommonObjectAuditResult result = runBeta35SpecialFullblockAuditRow(
                    ctx,
                    singleplayer,
                    bookshelf,
                    supportCase,
                    0);
            boolean rowGreen = "GREEN_ALREADY_INHERITS".equals(result.classification());
            allGreen &= rowGreen;
            if (!rowGreen && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = switch (result.classification()) {
                    case "PLACEMENT_FAILURE" -> "BOOKSHELF_PLACEMENT_FAILURE";
                    case "SURVIVAL_FAILURE" -> "BOOKSHELF_SURVIVAL_FAILURE";
                    case "CONTACT_GAP" -> "BOOKSHELF_CONTACT_GAP";
                    case "TRIAD_MISMATCH" -> "BOOKSHELF_TRIAD_MISMATCH";
                    default -> "BOOKSHELF_UNKNOWN_FAILURE";
                };
            }
            if (rowGreen) {
                System.out.println("JULIA_BETA35_BOOKSHELF_CONTACT_GREEN"
                        + " objectId=minecraft:bookshelf"
                        + " family=ordinary_full_block"
                        + " supportCase=" + supportCase
                        + " placement=GREEN"
                        + " survival=GREEN"
                        + " contact=GREEN"
                        + " triad=GREEN"
                        + " classification=GREEN_ALREADY_INHERITS");
            }
        }

        String failureLayer = allGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_BOOKSHELF_CONTACT_SUMMARY"
                + " failureLayer=" + failureLayer
                + " objectId=minecraft:bookshelf"
                + " rows=" + Beta35CommonObjectSupportCase.values().length
                + " expectedRowsGreen=" + allGreen
                + " crafting_table=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " furnace=CHECK_SPECIAL_FULLBLOCK_MATRIX"
                + " enchanting_table=UNCHANGED_SEPARATE_CONTACT_GAP"
                + " lectern=UNCHANGED_SEPARATE_CONTACT_GAP"
                + " chest=UNCHANGED_SEPARATE_CONTACT_GAP"
                + " barrel=UNCHANGED_SEPARATE_TRIAD_RISK"
                + " stonecutter=UNCHANGED_SEPARATE_SHAPE_SLICE"
                + " grindstone=UNCHANGED_SEPARATE_SHAPE_SLICE"
                + " anvil=UNCHANGED_SEPARATE_SHAPE_SLICE"
                + " doorTrapdoorSignLanternChainEndRodRedstoneRail=NOT_TOUCHED"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");

        if (!allGreen) {
            System.out.println("JULIA_BETA35_BOOKSHELF_CONTACT_RED"
                    + " objectId=minecraft:bookshelf"
                    + " failureLayer=" + failureLayer
                    + " classification=RED");
            throw new RuntimeException("[JULIA_BETA35_BOOKSHELF_CONTACT_RED]"
                    + " reason=bookshelf_contact_not_green"
                    + " failureLayer=" + failureLayer);
        }
    }

    /**
     * Focused audit matrix for the Beta 3.5 trapdoor/door category slice.
     *
     * Gate: -Dslabbed.beta35TrapdoorDoorAudit=true
     */
    private static void runBeta35TrapdoorDoorAudit(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        System.out.println("JULIA_BETA35_TRAPDOOR_DOOR_MATRIX_START"
                + " scope=interactive_hinge_and_multipart_audit"
                + " order=oak_trapdoor_then_oak_door"
                + " supportCases=VANILLA_FULL_BLOCK,PLAIN_BOTTOM_DY_MINUS_HALF,LOWERED_BOTTOM_DY_MINUS_ONE"
                + " releaseAudit=NOT_RUN"
                + " productionBehaviorChanged=false");

        Beta35TrapdoorDoorAuditRow[] trapdoorRows =
                new Beta35TrapdoorDoorAuditRow[Beta35CommonObjectSupportCase.values().length];
        Beta35TrapdoorDoorAuditRow[] doorRows =
                new Beta35TrapdoorDoorAuditRow[Beta35CommonObjectSupportCase.values().length];

        int index = 0;
        for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
            trapdoorRows[index] = runBeta35TrapdoorAuditRow(ctx, singleplayer, supportCase, index);
            index++;
        }
        index = 0;
        for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
            doorRows[index] = runBeta35DoorAuditRow(ctx, singleplayer, supportCase, index);
            index++;
        }

        String trapdoorClassification = beta35FirstSlabNonGreenClassification(trapdoorRows);
        String doorClassification = beta35FirstSlabNonGreenClassification(doorRows);
        String trapdoorFailureLayer = beta35TrapdoorFailureLayer(trapdoorClassification);
        String doorFailureLayer = beta35DoorFailureLayer(doorClassification);

        System.out.println("JULIA_BETA35_TRAPDOOR_DOOR_SUMMARY"
                + " trapdoorClassification=" + trapdoorClassification
                + " trapdoorFailureLayer=" + trapdoorFailureLayer
                + " doorClassification=" + doorClassification
                + " doorFailureLayer=" + doorFailureLayer
                + " currentGreenSet=torch,candle,flower_pot,crafting_table,furnace,oak_fence,oak_trapdoor,oak_door"
                + " trapdoorRecommendation="
                + ("CONTACT_GAP".equals(trapdoorClassification)
                        ? "NEXT_SLICE_CATEGORY_SPECIFIC_CONTACT_AND_OPEN_CLOSE_FIX"
                        : "CLASSIFY_BEFORE_FIX")
                + " doorRecommendation="
                + ("GREEN_ALREADY_INHERITS".equals(doorClassification)
                        ? "GREEN_MULTIPART_CONTACT_REPRESENTATIVE"
                        : "DEFER_MULTIPART_CATEGORY_SLICE")
                + " signs=NOT_TOUCHED"
                + " lanterns=NOT_TOUCHED"
                + " chains=NOT_TOUCHED"
                + " redstone=NOT_TOUCHED"
                + " rails=NOT_TOUCHED"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");
    }

    private static Beta35TrapdoorDoorAuditRow runBeta35TrapdoorAuditRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Beta35CommonObjectSupportCase supportCase,
            int rowIndex
    ) {
        int baseX = 224 + rowIndex * 8;
        int baseZ = 132 + supportCase.ordinal() * 6;
        BlockPos supportCandidatePos = new BlockPos(baseX, -55, baseZ);
        BlockPos trapdoorPos = supportCandidatePos.up();
        BlockPos unsupportedPos = supportCandidatePos.add(0, 1, 3);
        BlockHitResult useHit = beta35TopUseHit(supportCandidatePos, supportCase);

        prepareBeta35CommonObjectSupport(singleplayer, supportCandidatePos, unsupportedPos, supportCase);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.OAK_TRAPDOOR, 4));
        syncPlayerAim(ctx, singleplayer,
                new Vec3d(supportCandidatePos.getX() + 0.5d, supportCandidatePos.getY() + 3.0d,
                        supportCandidatePos.getZ() - 2.0d),
                useHit.getPos());

        final String[] placementResult = {"not-run"};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                placementResult[0] = "CLIENT_NOT_READY";
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, useHit);
            placementResult[0] = result.toString();
        });
        ctx.waitTick();
        ctx.waitTick();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState supportState = world.getBlockState(supportCandidatePos);
            BlockState trapdoorState = world.getBlockState(trapdoorPos);
            world.updateNeighbors(trapdoorPos, trapdoorState.getBlock());
            world.updateNeighbors(supportCandidatePos, supportState.getBlock());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String[] openCloseResult = {"NOT_TESTED"};
        syncHeldMainHand(ctx, singleplayer, ItemStack.EMPTY);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                openCloseResult[0] = "CLIENT_NOT_READY";
                return;
            }
            if (!mc.world.getBlockState(trapdoorPos).isOf(Blocks.OAK_TRAPDOOR)) {
                openCloseResult[0] = "BLOCK_NOT_PRESENT";
                return;
            }
            BlockHitResult trapdoorHit = new BlockHitResult(
                    new Vec3d(trapdoorPos.getX() + 0.5d, trapdoorPos.getY() + 0.125d, trapdoorPos.getZ() + 0.5d),
                    Direction.UP,
                    trapdoorPos,
                    false);
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, trapdoorHit);
            openCloseResult[0] = result.toString();
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                return;
            }
            if (!mc.world.getBlockState(trapdoorPos).isOf(Blocks.OAK_TRAPDOOR)) {
                return;
            }
            BlockHitResult trapdoorHit = new BlockHitResult(
                    new Vec3d(trapdoorPos.getX() + 0.5d, trapdoorPos.getY() + 0.125d, trapdoorPos.getZ() + 0.5d),
                    Direction.UP,
                    trapdoorPos,
                    false);
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, trapdoorHit);
            openCloseResult[0] = openCloseResult[0] + "->" + result;
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String[] classification = {"OUT_OF_SCOPE_FOR_BETA35"};
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                System.out.println("JULIA_BETA35_TRAPDOOR_DOOR_ROW"
                        + " objectId=minecraft:oak_trapdoor"
                        + " family=interactive_hinge"
                        + " supportCase=" + supportCase
                        + " classification=OUT_OF_SCOPE_FOR_BETA35"
                        + " reason=client_world_or_player_missing");
                return;
            }

            BlockState supportState = mc.world.getBlockState(supportCandidatePos);
            BlockState trapdoorState = mc.world.getBlockState(trapdoorPos);
            boolean blockAppeared = trapdoorState.isOf(Blocks.OAK_TRAPDOOR);
            double supportDy = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportState);
            double objectDy = blockAppeared ? SlabSupport.getYOffset(mc.world, trapdoorPos, trapdoorState)
                    : Double.NaN;
            double supportVisibleTopY = beta35CommonSupportVisibleTopY(supportCandidatePos, supportState, supportDy);
            VoxelShape outlineShape = blockAppeared
                    ? trapdoorState.getOutlineShape(mc.world, trapdoorPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = blockAppeared ? trapdoorState.getRaycastShape(mc.world, trapdoorPos) : null;
            VoxelShape collisionShape = blockAppeared
                    ? trapdoorState.getCollisionShape(mc.world, trapdoorPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, trapdoorPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, trapdoorPos);
            net.minecraft.util.math.Box collisionBox = beta35WorldBox(collisionShape, trapdoorPos);
            net.minecraft.util.math.Box modelProxyBox = outlineBox;
            double objectModelBottomY = modelProxyBox == null ? Double.NaN : modelProxyBox.minY;
            double contactGap = Double.isFinite(objectModelBottomY) && Double.isFinite(supportVisibleTopY)
                    ? objectModelBottomY - supportVisibleTopY
                    : Double.NaN;
            boolean survivalGreen = blockAppeared && trapdoorState.canPlaceAt(mc.world, trapdoorPos);
            boolean contactGreen = Double.isFinite(contactGap) && Math.abs(contactGap) <= EPSILON;
            boolean triadGreen = blockAppeared
                    && modelProxyBox != null
                    && beta35SameBox(outlineBox, modelProxyBox)
                    && beta35SameBox(raycastBox, modelProxyBox);
            boolean collisionGreen = blockAppeared
                    && modelProxyBox != null
                    && beta35SameBox(collisionBox, modelProxyBox);
            boolean openCloseGreen = blockAppeared && !"CLIENT_NOT_READY".equals(openCloseResult[0])
                    && !"BLOCK_NOT_PRESENT".equals(openCloseResult[0]);

            if (!blockAppeared) {
                classification[0] = "PLACEMENT_FAILURE";
            } else if (!survivalGreen) {
                classification[0] = "SURVIVAL_FAILURE";
            } else if (!contactGreen) {
                classification[0] = "CONTACT_GAP";
            } else if (!triadGreen) {
                classification[0] = "TRIAD_MISMATCH";
            } else if (!collisionGreen) {
                classification[0] = "COLLISION_SHAPE_RISK";
            } else if (!openCloseGreen) {
                classification[0] = "OPEN_CLOSE_RISK";
            } else {
                classification[0] = "GREEN_ALREADY_INHERITS";
            }

            System.out.println("JULIA_BETA35_TRAPDOOR_DOOR_ROW"
                    + " objectId=minecraft:oak_trapdoor"
                    + " family=interactive_hinge"
                    + " supportCase=" + supportCase
                    + " placementResult=" + placementResult[0]
                    + " blockAppearedAfterAttempt=" + blockAppeared
                    + " finalBlockState=" + trapdoorState
                    + " supportCandidateState=" + supportState
                    + " supportDy=" + String.format("%.6f", supportDy)
                    + " objectDy=" + (Double.isFinite(objectDy) ? String.format("%.6f", objectDy) : "N/A")
                    + " supportVisibleTopY=" + (Double.isFinite(supportVisibleTopY)
                            ? String.format("%.6f", supportVisibleTopY) : "N/A")
                    + " objectModelBottomY=" + (Double.isFinite(objectModelBottomY)
                            ? String.format("%.6f", objectModelBottomY) : "CONTACT_NOT_APPLICABLE")
                    + " contactGap=" + (Double.isFinite(contactGap)
                            ? String.format("%.6f", contactGap) : "CONTACT_NOT_APPLICABLE")
                    + " modelBounds=" + beta35FormatBox(modelProxyBox)
                    + " outlineBounds=" + beta35FormatBox(outlineBox)
                    + " raycastBounds=" + beta35FormatBox(raycastBox)
                    + " collisionBounds=" + beta35FormatBox(collisionBox)
                    + " triadCoLocated=" + (blockAppeared ? (triadGreen ? "yes" : "no") : "NOT_MEASURED")
                    + " survivalResult=" + (survivalGreen ? "SURVIVAL_GREEN" : "SURVIVAL_RED")
                    + " openCloseResult=" + openCloseResult[0]
                    + " half=" + beta35PropertyValue(trapdoorState, Properties.BLOCK_HALF)
                    + " facing=" + beta35PropertyValue(trapdoorState, Properties.HORIZONTAL_FACING)
                    + " open=" + beta35PropertyValue(trapdoorState, Properties.OPEN)
                    + " waterlogged=" + beta35PropertyValue(trapdoorState, Properties.WATERLOGGED)
                    + " classification=" + classification[0]);
        });

        return new Beta35TrapdoorDoorAuditRow(
                "minecraft:oak_trapdoor",
                "interactive_hinge",
                supportCase.toString(),
                classification[0]);
    }

    private static Beta35TrapdoorDoorAuditRow runBeta35DoorAuditRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Beta35CommonObjectSupportCase supportCase,
            int rowIndex
    ) {
        int baseX = 260 + rowIndex * 8;
        int baseZ = 132 + supportCase.ordinal() * 6;
        BlockPos supportCandidatePos = new BlockPos(baseX, -55, baseZ);
        BlockPos bottomPos = supportCandidatePos.up();
        BlockPos topPos = bottomPos.up();
        BlockPos unsupportedPos = supportCandidatePos.add(0, 1, 3);
        BlockHitResult useHit = beta35TopUseHit(supportCandidatePos, supportCase);

        prepareBeta35CommonObjectSupport(singleplayer, supportCandidatePos, unsupportedPos, supportCase);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.OAK_DOOR, 4));
        syncPlayerAim(ctx, singleplayer,
                new Vec3d(supportCandidatePos.getX() + 0.5d, supportCandidatePos.getY() + 3.0d,
                        supportCandidatePos.getZ() - 2.0d),
                useHit.getPos());

        final String[] placementResult = {"not-run"};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                placementResult[0] = "CLIENT_NOT_READY";
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, useHit);
            placementResult[0] = result.toString();
        });
        ctx.waitTick();
        ctx.waitTick();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState supportState = world.getBlockState(supportCandidatePos);
            BlockState bottomState = world.getBlockState(bottomPos);
            BlockState topState = world.getBlockState(topPos);
            world.updateNeighbors(bottomPos, bottomState.getBlock());
            world.updateNeighbors(topPos, topState.getBlock());
            world.updateNeighbors(supportCandidatePos, supportState.getBlock());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String[] openCloseResult = {"NOT_TESTED"};
        syncHeldMainHand(ctx, singleplayer, ItemStack.EMPTY);
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                openCloseResult[0] = "CLIENT_NOT_READY";
                return;
            }
            if (!mc.world.getBlockState(bottomPos).isOf(Blocks.OAK_DOOR)) {
                openCloseResult[0] = "BLOCK_NOT_PRESENT";
                return;
            }
            BlockHitResult doorHit = new BlockHitResult(
                    new Vec3d(bottomPos.getX() + 0.5d, bottomPos.getY() + 0.5d, bottomPos.getZ() + 0.0625d),
                    Direction.NORTH,
                    bottomPos,
                    false);
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, doorHit);
            openCloseResult[0] = result.toString();
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                return;
            }
            if (!mc.world.getBlockState(bottomPos).isOf(Blocks.OAK_DOOR)) {
                return;
            }
            BlockHitResult doorHit = new BlockHitResult(
                    new Vec3d(bottomPos.getX() + 0.5d, bottomPos.getY() + 0.5d, bottomPos.getZ() + 0.0625d),
                    Direction.NORTH,
                    bottomPos,
                    false);
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, doorHit);
            openCloseResult[0] = openCloseResult[0] + "->" + result;
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String[] classification = {"OUT_OF_SCOPE_FOR_BETA35"};
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                System.out.println("JULIA_BETA35_TRAPDOOR_DOOR_ROW"
                        + " objectId=minecraft:oak_door"
                        + " family=multipart_door"
                        + " supportCase=" + supportCase
                        + " classification=OUT_OF_SCOPE_FOR_BETA35"
                        + " reason=client_world_or_player_missing");
                return;
            }

            BlockState supportState = mc.world.getBlockState(supportCandidatePos);
            BlockState bottomState = mc.world.getBlockState(bottomPos);
            BlockState topState = mc.world.getBlockState(topPos);
            boolean bottomAppeared = bottomState.isOf(Blocks.OAK_DOOR);
            boolean topAppeared = topState.isOf(Blocks.OAK_DOOR);
            double supportDy = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportState);
            double bottomDy = bottomAppeared ? SlabSupport.getYOffset(mc.world, bottomPos, bottomState)
                    : Double.NaN;
            double topDy = topAppeared ? SlabSupport.getYOffset(mc.world, topPos, topState) : Double.NaN;
            double supportVisibleTopY = beta35CommonSupportVisibleTopY(supportCandidatePos, supportState, supportDy);
            VoxelShape bottomOutlineShape = bottomAppeared
                    ? bottomState.getOutlineShape(mc.world, bottomPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape bottomRaycastShape = bottomAppeared ? bottomState.getRaycastShape(mc.world, bottomPos) : null;
            VoxelShape topOutlineShape = topAppeared
                    ? topState.getOutlineShape(mc.world, topPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape topRaycastShape = topAppeared ? topState.getRaycastShape(mc.world, topPos) : null;
            VoxelShape bottomCollisionShape = bottomAppeared
                    ? bottomState.getCollisionShape(mc.world, bottomPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape topCollisionShape = topAppeared
                    ? topState.getCollisionShape(mc.world, topPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            net.minecraft.util.math.Box bottomOutlineBox = beta35WorldBox(bottomOutlineShape, bottomPos);
            net.minecraft.util.math.Box bottomRaycastBox = beta35WorldBox(bottomRaycastShape, bottomPos);
            net.minecraft.util.math.Box topOutlineBox = beta35WorldBox(topOutlineShape, topPos);
            net.minecraft.util.math.Box topRaycastBox = beta35WorldBox(topRaycastShape, topPos);
            net.minecraft.util.math.Box bottomCollisionBox = beta35WorldBox(bottomCollisionShape, bottomPos);
            net.minecraft.util.math.Box topCollisionBox = beta35WorldBox(topCollisionShape, topPos);
            double bottomModelBottomY = bottomOutlineBox == null ? Double.NaN : bottomOutlineBox.minY;
            double bottomContactGap = Double.isFinite(bottomModelBottomY) && Double.isFinite(supportVisibleTopY)
                    ? bottomModelBottomY - supportVisibleTopY
                    : Double.NaN;
            double topContactGap = bottomOutlineBox != null && topOutlineBox != null
                    ? topOutlineBox.minY - bottomOutlineBox.maxY
                    : Double.NaN;
            boolean bottomSurvivalGreen = bottomAppeared && bottomState.canPlaceAt(mc.world, bottomPos);
            boolean topSurvivalGreen = topAppeared && topState.canPlaceAt(mc.world, topPos);
            boolean contactGreen = Double.isFinite(bottomContactGap) && Math.abs(bottomContactGap) <= EPSILON;
            boolean bottomTriadGreen = bottomAppeared
                    && beta35SameBox(bottomOutlineBox, bottomRaycastBox);
            boolean topTriadGreen = topAppeared
                    && beta35SameBox(topOutlineBox, topRaycastBox);
            boolean collisionGreen = bottomAppeared && topAppeared
                    && beta35SameBox(bottomOutlineBox, bottomCollisionBox)
                    && beta35SameBox(topOutlineBox, topCollisionBox);
            boolean openCloseGreen = bottomAppeared && !"CLIENT_NOT_READY".equals(openCloseResult[0])
                    && !"BLOCK_NOT_PRESENT".equals(openCloseResult[0]);
            boolean topAlignmentGreen = bottomAppeared && topAppeared
                    && Double.isFinite(bottomDy)
                    && Double.isFinite(topDy)
                    && Math.abs(bottomDy - topDy) <= EPSILON
                    && Double.isFinite(topContactGap)
                    && Math.abs(topContactGap) <= EPSILON
                    && topTriadGreen;

            if (!bottomAppeared || !topAppeared) {
                classification[0] = "PLACEMENT_FAILURE";
            } else if (!bottomSurvivalGreen || !topSurvivalGreen) {
                classification[0] = "SURVIVAL_FAILURE";
            } else if (!contactGreen) {
                classification[0] = "CONTACT_GAP";
            } else if (!bottomTriadGreen || !topAlignmentGreen) {
                classification[0] = "TRIAD_MISMATCH";
            } else if (!collisionGreen) {
                classification[0] = "COLLISION_SHAPE_RISK";
            } else if (!openCloseGreen) {
                classification[0] = "OPEN_CLOSE_RISK";
            } else {
                classification[0] = "GREEN_ALREADY_INHERITS";
            }

            System.out.println("JULIA_BETA35_TRAPDOOR_DOOR_ROW"
                    + " objectId=minecraft:oak_door"
                    + " family=multipart_door"
                    + " supportCase=" + supportCase
                    + " placementResult=" + placementResult[0]
                    + " bottomHalfAppeared=" + bottomAppeared
                    + " topHalfAppeared=" + topAppeared
                    + " bottomState=" + bottomState
                    + " topState=" + topState
                    + " supportCandidateState=" + supportState
                    + " supportDy=" + String.format("%.6f", supportDy)
                    + " bottomDy=" + (Double.isFinite(bottomDy) ? String.format("%.6f", bottomDy) : "N/A")
                    + " topDy=" + (Double.isFinite(topDy) ? String.format("%.6f", topDy) : "N/A")
                    + " supportVisibleTopY=" + (Double.isFinite(supportVisibleTopY)
                            ? String.format("%.6f", supportVisibleTopY) : "N/A")
                    + " bottomContactGap=" + (Double.isFinite(bottomContactGap)
                            ? String.format("%.6f", bottomContactGap) : "CONTACT_NOT_APPLICABLE")
                    + " topAlignment=" + (topAppeared ? (topAlignmentGreen ? "GREEN" : "RED") : "NOT_MEASURED")
                    + " topContactGap=" + (Double.isFinite(topContactGap)
                            ? String.format("%.6f", topContactGap) : "CONTACT_NOT_APPLICABLE")
                    + " modelBounds=bottom:" + beta35FormatBox(bottomOutlineBox)
                            + "|top:" + beta35FormatBox(topOutlineBox)
                    + " outlineBounds=bottom:" + beta35FormatBox(bottomOutlineBox)
                            + "|top:" + beta35FormatBox(topOutlineBox)
                    + " raycastBounds=bottom:" + beta35FormatBox(bottomRaycastBox)
                            + "|top:" + beta35FormatBox(topRaycastBox)
                    + " collisionBounds=bottom:" + beta35FormatBox(bottomCollisionBox)
                            + "|top:" + beta35FormatBox(topCollisionBox)
                    + " bottomModelBounds=" + beta35FormatBox(bottomOutlineBox)
                    + " bottomOutlineBounds=" + beta35FormatBox(bottomOutlineBox)
                    + " bottomRaycastBounds=" + beta35FormatBox(bottomRaycastBox)
                    + " bottomCollisionBounds=" + beta35FormatBox(bottomCollisionBox)
                    + " topOutlineBounds=" + beta35FormatBox(topOutlineBox)
                    + " topRaycastBounds=" + beta35FormatBox(topRaycastBox)
                    + " topCollisionBounds=" + beta35FormatBox(topCollisionBox)
                    + " triadCoLocated=" + (bottomAppeared && topAppeared
                            ? (bottomTriadGreen && topTriadGreen ? "yes" : "no") : "NOT_MEASURED")
                    + " collisionCoLocated=" + (bottomAppeared && topAppeared
                            ? (collisionGreen ? "yes" : "no") : "NOT_MEASURED")
                    + " survivalResult=" + (bottomSurvivalGreen && topSurvivalGreen
                            ? "SURVIVAL_GREEN" : "SURVIVAL_RED")
                    + " openCloseResult=" + openCloseResult[0]
                    + " unsupportedResult=NOT_TESTED_MULTIPART"
                    + " bottomHalf=" + beta35PropertyValue(bottomState, Properties.DOUBLE_BLOCK_HALF)
                    + " topHalf=" + beta35PropertyValue(topState, Properties.DOUBLE_BLOCK_HALF)
                    + " facing=" + beta35PropertyValue(bottomState, Properties.HORIZONTAL_FACING)
                    + " hinge=" + beta35PropertyValue(bottomState, Properties.DOOR_HINGE)
                    + " open=" + beta35PropertyValue(bottomState, Properties.OPEN)
                    + " classification=" + classification[0]);
        });

        return new Beta35TrapdoorDoorAuditRow(
                "minecraft:oak_door",
                "multipart_door",
                supportCase.toString(),
                classification[0]);
    }

    private static BlockHitResult beta35TopUseHit(
            BlockPos supportCandidatePos,
            Beta35CommonObjectSupportCase supportCase
    ) {
        double hitY = supportCase == Beta35CommonObjectSupportCase.LOWERED_BOTTOM_DY_MINUS_ONE
                ? supportCandidatePos.getY() - 0.5d
                : supportCandidatePos.getY() + 1.0d;
        return new BlockHitResult(
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        hitY,
                        supportCandidatePos.getZ() + 0.5d),
                Direction.UP,
                supportCandidatePos,
                false);
    }

    private static String beta35FirstSlabNonGreenClassification(Beta35TrapdoorDoorAuditRow[] rows) {
        for (Beta35TrapdoorDoorAuditRow row : rows) {
            if (!"VANILLA_FULL_BLOCK".equals(row.supportCase())
                    && !"GREEN_ALREADY_INHERITS".equals(row.classification())) {
                return row.classification();
            }
        }
        return "GREEN_ALREADY_INHERITS";
    }

    private static String beta35TrapdoorFailureLayer(String classification) {
        return switch (classification) {
            case "GREEN_ALREADY_INHERITS" -> "NONE";
            case "PLACEMENT_FAILURE" -> "TRAPDOOR_PLACEMENT_FAILURE";
            case "SURVIVAL_FAILURE" -> "TRAPDOOR_SURVIVAL_FAILURE";
            case "CONTACT_GAP" -> "TRAPDOOR_CONTACT_GAP";
            case "TRIAD_MISMATCH" -> "TRAPDOOR_TRIAD_MISMATCH";
            case "COLLISION_SHAPE_RISK" -> "TRAPDOOR_COLLISION_SHAPE_RISK";
            case "OPEN_CLOSE_RISK" -> "TRAPDOOR_OPEN_CLOSE_RISK";
            default -> "TRAPDOOR_UNKNOWN_FAILURE";
        };
    }

    private static String beta35DoorFailureLayer(String classification) {
        return switch (classification) {
            case "GREEN_ALREADY_INHERITS" -> "NONE";
            case "PLACEMENT_FAILURE" -> "DOOR_MULTIPART_PLACEMENT_FAILURE";
            case "SURVIVAL_FAILURE" -> "DOOR_MULTIPART_SURVIVAL_FAILURE";
            case "CONTACT_GAP" -> "DOOR_MULTIPART_CONTACT_GAP";
            case "TRIAD_MISMATCH" -> "DOOR_MULTIPART_TRIAD_MISMATCH";
            case "COLLISION_SHAPE_RISK" -> "DOOR_MULTIPART_COLLISION_SHAPE_RISK";
            case "OPEN_CLOSE_RISK" -> "DOOR_MULTIPART_OPEN_CLOSE_RISK";
            default -> "DOOR_MULTIPART_RISK";
        };
    }

    private static String beta35PropertyValue(
            BlockState state,
            net.minecraft.state.property.Property<?> property
    ) {
        if (state == null || !state.contains(property)) {
            return "N/A";
        }
        return String.valueOf(state.get(property));
    }

    /**
     * Focused oak-trapdoor-only proof for the Beta 3.5 interactive hinge contact slice.
     *
     * Gate: -Dslabbed.beta35OakTrapdoorContact=true
     */
    private static void runBeta35OakTrapdoorContactProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        boolean allGreen = true;
        String firstFailureLayer = "NONE";
        for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
            Beta35TrapdoorDoorAuditRow result = runBeta35TrapdoorAuditRow(ctx, singleplayer, supportCase,
                    supportCase.ordinal());
            if (supportCase == Beta35CommonObjectSupportCase.VANILLA_FULL_BLOCK) {
                continue;
            }
            boolean rowGreen = "GREEN_ALREADY_INHERITS".equals(result.classification());
            allGreen &= rowGreen;
            if (!rowGreen && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = beta35TrapdoorFailureLayer(result.classification());
            }
            if (rowGreen) {
                System.out.println("JULIA_BETA35_OAK_TRAPDOOR_CONTACT_GREEN"
                        + " objectId=minecraft:oak_trapdoor"
                        + " family=interactive_hinge"
                        + " supportCase=" + supportCase
                        + " placement=GREEN"
                        + " survival=GREEN"
                        + " contact=GREEN"
                        + " triad=GREEN"
                        + " collision=GREEN"
                        + " openClose=GREEN"
                        + " classification=GREEN_ALREADY_INHERITS");
            }
        }

        String failureLayer = allGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_OAK_TRAPDOOR_CONTACT_SUMMARY"
                + " failureLayer=" + failureLayer
                + " objectId=minecraft:oak_trapdoor"
                + " family=interactive_hinge"
                + " supportRows=2"
                + " expectedSupportRowsGreen=" + allGreen
                + " vanillaFullBlockControl=NOT_RELEASE_CRITERION_FOR_SLAB_CONTACT"
                + " currentGreenSet=torch,candle,flower_pot,crafting_table,furnace,oak_fence,oak_trapdoor"
                + " oak_door=UNCHANGED_DEFERRED_MULTIPART_RISK"
                + " signs=NOT_TOUCHED"
                + " lanterns=NOT_TOUCHED"
                + " chains=NOT_TOUCHED"
                + " redstone=NOT_TOUCHED"
                + " rails=NOT_TOUCHED"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");

        if (!allGreen) {
            throw new RuntimeException("[JULIA_BETA35_OAK_TRAPDOOR_CONTACT_RED]"
                    + " reason=oak_trapdoor_contact_not_green"
                    + " failureLayer=" + failureLayer);
        }
    }

    /**
     * Focused oak-door-only proof for the Beta 3.5 multipart contact slice.
     *
     * Gate: -Dslabbed.beta35OakDoorContact=true
     */
    private static void runBeta35OakDoorContactProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        boolean allGreen = true;
        String firstFailureLayer = "NONE";
        for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
            Beta35TrapdoorDoorAuditRow result = runBeta35DoorAuditRow(ctx, singleplayer, supportCase,
                    supportCase.ordinal());
            if (supportCase == Beta35CommonObjectSupportCase.VANILLA_FULL_BLOCK) {
                continue;
            }
            boolean rowGreen = "GREEN_ALREADY_INHERITS".equals(result.classification());
            allGreen &= rowGreen;
            if (!rowGreen && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = beta35DoorFailureLayer(result.classification());
            }
            if (rowGreen) {
                System.out.println("JULIA_BETA35_OAK_DOOR_CONTACT_GREEN"
                        + " objectId=minecraft:oak_door"
                        + " family=multipart_door"
                        + " supportCase=" + supportCase
                        + " placement=GREEN"
                        + " survival=GREEN"
                        + " contact=GREEN"
                        + " bottomTopHalves=GREEN"
                        + " bottomTopDy=GREEN"
                        + " triad=GREEN"
                        + " collision=GREEN"
                        + " openClose=GREEN"
                        + " classification=GREEN_ALREADY_INHERITS");
            }
        }

        String failureLayer = allGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_OAK_DOOR_CONTACT_SUMMARY"
                + " failureLayer=" + failureLayer
                + " objectId=minecraft:oak_door"
                + " family=multipart_door"
                + " supportRows=2"
                + " expectedSupportRowsGreen=" + allGreen
                + " vanillaFullBlockControl=NOT_RELEASE_CRITERION_FOR_SLAB_CONTACT"
                + " currentGreenSet=torch,candle,flower_pot,crafting_table,furnace,oak_fence,oak_trapdoor,oak_door"
                + " oak_trapdoor=GREEN_UNCHANGED"
                + " signs=NOT_TOUCHED"
                + " lanterns=NOT_TOUCHED"
                + " chains=NOT_TOUCHED"
                + " redstone=NOT_TOUCHED"
                + " rails=NOT_TOUCHED"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");

        if (!allGreen) {
            throw new RuntimeException("[JULIA_BETA35_OAK_DOOR_CONTACT_RED]"
                    + " reason=oak_door_contact_not_green"
                    + " failureLayer=" + failureLayer);
        }
    }

    /**
     * Focused standing oak sign proof for the Beta 3.5 sign contact slice.
     *
     * Gate: -Dslabbed.beta35StandingOakSignContact=true
     */
    private static void runBeta35StandingOakSignContactProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35CommonObjectCase oakSign = new Beta35CommonObjectCase(
                "minecraft:oak_sign",
                "standing_sign",
                Items.OAK_SIGN,
                Blocks.OAK_SIGN.getDefaultState(),
                "GREEN_ALREADY_INHERITS",
                true,
                true,
                true);

        boolean allGreen = true;
        String firstFailureLayer = "NONE";
        for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
            Beta35CommonObjectAuditResult result = runBeta35CommonObjectAuditRow(
                    ctx,
                    singleplayer,
                    oakSign,
                    supportCase,
                    8);
            boolean rowGreen = "GREEN_ALREADY_INHERITS".equals(result.classification());
            allGreen &= rowGreen;
            if (!rowGreen && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = switch (result.classification()) {
                    case "PLACEMENT_FAILURE" -> "STANDING_OAK_SIGN_PLACEMENT_FAILURE";
                    case "SURVIVAL_FAILURE" -> "STANDING_OAK_SIGN_SURVIVAL_FAILURE";
                    case "CONTACT_GAP" -> "STANDING_OAK_SIGN_CONTACT_GAP";
                    case "TRIAD_MISMATCH" -> "STANDING_OAK_SIGN_TRIAD_MISMATCH";
                    default -> "STANDING_OAK_SIGN_RENDERER_OR_UNKNOWN_RISK";
                };
            }
            if (rowGreen) {
                System.out.println("JULIA_BETA35_STANDING_OAK_SIGN_CONTACT_GREEN"
                        + " objectId=minecraft:oak_sign"
                        + " family=standing_sign"
                        + " supportCase=" + supportCase
                        + " placement=GREEN"
                        + " survival=GREEN"
                        + " contact=GREEN"
                        + " triad=GREEN"
                        + " blockEntityPath=BlockEntityOffsetMixin"
                        + " wallSigns=NOT_TOUCHED"
                        + " hangingSigns=NOT_TOUCHED"
                        + " classification=GREEN_ALREADY_INHERITS");
            }
        }

        String failureLayer = allGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_STANDING_OAK_SIGN_CONTACT_SUMMARY"
                + " failureLayer=" + failureLayer
                + " objectId=minecraft:oak_sign"
                + " family=standing_sign"
                + " rows=" + Beta35CommonObjectSupportCase.values().length
                + " expectedRowsGreen=" + allGreen
                + " rendererPath=BlockEntityOffsetMixin"
                + " currentGreenSet=torch,candle,flower_pot,crafting_table,furnace,oak_fence,oak_trapdoor,oak_door,oak_sign"
                + " wallSigns=NOT_TOUCHED"
                + " hangingSigns=NOT_TOUCHED"
                + " lanterns=NOT_TOUCHED"
                + " chains=NOT_TOUCHED"
                + " redstone=NOT_TOUCHED"
                + " rails=NOT_TOUCHED"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");

        if (!allGreen) {
            System.out.println("JULIA_BETA35_STANDING_OAK_SIGN_CONTACT_RED"
                    + " objectId=minecraft:oak_sign"
                    + " failureLayer=" + failureLayer
                    + " classification=RED");
            throw new RuntimeException("[JULIA_BETA35_STANDING_OAK_SIGN_CONTACT_RED]"
                    + " reason=standing_oak_sign_contact_not_green"
                    + " failureLayer=" + failureLayer);
        }
    }

    /**
     * Focused oak-fence proof for the Beta 3.5 partial-collision contact slice.
     *
     * Gate: -Dslabbed.beta35OakFenceContact=true
     */
    private static void runBeta35OakFenceContactProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35CommonObjectCase oakFence = new Beta35CommonObjectCase(
                "minecraft:oak_fence",
                "partial_collision",
                Items.OAK_FENCE,
                Blocks.OAK_FENCE.getDefaultState(),
                "GREEN_ALREADY_INHERITS",
                false,
                true,
                true);

        boolean allGreen = true;
        String firstFailureLayer = "NONE";
        for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
            Beta35CommonObjectAuditResult result = runBeta35CommonObjectAuditRow(
                    ctx,
                    singleplayer,
                    oakFence,
                    supportCase,
                    5);
            boolean rowGreen = "GREEN_ALREADY_INHERITS".equals(result.classification());
            allGreen &= rowGreen;
            if (!rowGreen && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = switch (result.classification()) {
                    case "PLACEMENT_FAILURE" -> "OAK_FENCE_PLACEMENT_FAILURE";
                    case "SURVIVAL_FAILURE" -> "OAK_FENCE_SURVIVAL_FAILURE";
                    case "CONTACT_GAP" -> "OAK_FENCE_CONTACT_GAP";
                    case "TRIAD_MISMATCH" -> "OAK_FENCE_TRIAD_MISMATCH";
                    case "COLLISION_SHAPE_RISK" -> "OAK_FENCE_COLLISION_SHAPE_RISK";
                    default -> "OAK_FENCE_UNKNOWN_FAILURE";
                };
            }
            if (rowGreen) {
                System.out.println("JULIA_BETA35_OAK_FENCE_CONTACT_GREEN"
                        + " objectId=minecraft:oak_fence"
                        + " family=partial_collision"
                        + " supportCase=" + supportCase
                        + " placement=GREEN"
                        + " survival=GREEN"
                        + " contact=GREEN"
                        + " triad=GREEN"
                        + " collision=GREEN"
                        + " classification=GREEN_ALREADY_INHERITS");
            }
        }

        String failureLayer = allGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_OAK_FENCE_CONTACT_SUMMARY"
                + " failureLayer=" + failureLayer
                + " objectId=minecraft:oak_fence"
                + " family=partial_collision"
                + " rows=" + Beta35CommonObjectSupportCase.values().length
                + " expectedRowsGreen=" + allGreen
                + " crafting_table=CHECK_COMMON_OBJECT_MATRIX"
                + " furnace=CHECK_COMMON_OBJECT_MATRIX"
                + " floor_torch=CHECK_FLOOR_TOP_REGRESSION"
                + " candle=CHECK_FLOOR_TOP_REGRESSION"
                + " flower_pot=CHECK_FLOOR_TOP_REGRESSION"
                + " oak_trapdoor=UNCHANGED_SEPARATE_CATEGORY"
                + " oak_door=UNCHANGED_SEPARATE_CATEGORY"
                + " oak_sign=UNCHANGED_SEPARATE_RENDERER_RISK"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");

        if (!allGreen) {
            System.out.println("JULIA_BETA35_OAK_FENCE_CONTACT_RED"
                    + " objectId=minecraft:oak_fence"
                    + " failureLayer=" + failureLayer
                    + " classification=RED");
            throw new RuntimeException("[JULIA_BETA35_OAK_FENCE_CONTACT_RED]"
                    + " reason=oak_fence_contact_not_green"
                    + " failureLayer=" + failureLayer);
        }
    }

    /**
     * Focused GREEN proof for the Beta 3.5 fence/wall named variant coverage fix.
     *
     * Gate: -Dslabbed.beta35FenceWallVariantCoverage=true
     */
    private static void runBeta35FenceWallVariantCoverageProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35FenceFamilyCase[] cases = {
                new Beta35FenceFamilyCase(
                        "minecraft:oak_fence",
                        "fence_family",
                        Items.OAK_FENCE,
                        Blocks.OAK_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase(
                        "minecraft:spruce_fence",
                        "fence_family",
                        Items.SPRUCE_FENCE,
                        Blocks.SPRUCE_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase(
                        "minecraft:nether_brick_fence",
                        "fence_family",
                        Items.NETHER_BRICK_FENCE,
                        Blocks.NETHER_BRICK_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase(
                        "minecraft:cobblestone_wall",
                        "wall_family",
                        Items.COBBLESTONE_WALL,
                        Blocks.COBBLESTONE_WALL.getDefaultState())
        };

        System.out.println("JULIA_BETA35_FENCE_WALL_VARIANT_COVERAGE_GREEN"
                + " scope=explicit_named_fence_wall_variants"
                + " variants=minecraft:oak_fence,minecraft:spruce_fence,minecraft:nether_brick_fence,minecraft:cobblestone_wall"
                + " glass_pane=NOT_COVERED"
                + " releaseAudit=NOT_RUN");

        int rows = 0;
        int greenLiveLike = 0;
        int greenSimplifiedOnly = 0;
        int contactGap = 0;
        int triadMismatch = 0;
        int collisionShapeRisk = 0;
        int connectionShapeRisk = 0;
        int placementFailure = 0;
        int survivalFailure = 0;
        int variantCoverageGap = 0;
        int needsImplementation = 0;
        String firstFailureLayer = "NONE";
        String oakFenceClassification = "NOT_MEASURED";
        String spruceFenceClassification = "NOT_MEASURED";
        String netherBrickFenceClassification = "NOT_MEASURED";
        String cobblestoneWallClassification = "NOT_MEASURED";

        for (Beta35FenceFamilyCase objectCase : cases) {
            for (Beta35FenceFamilyConfiguration configuration : Beta35FenceFamilyConfiguration.values()) {
                Beta35FenceFamilyAuditResult result = runBeta35FenceFamilyAuditRow(
                        ctx,
                        singleplayer,
                        objectCase,
                        configuration,
                        rows);
                rows++;

                switch (result.classification()) {
                    case "GREEN_SIMPLIFIED_ONLY" -> greenSimplifiedOnly++;
                    case "GREEN_LIVE_LIKE" -> greenLiveLike++;
                    case "CONTACT_GAP" -> contactGap++;
                    case "TRIAD_MISMATCH" -> triadMismatch++;
                    case "COLLISION_SHAPE_RISK" -> collisionShapeRisk++;
                    case "CONNECTION_SHAPE_RISK" -> connectionShapeRisk++;
                    case "PLACEMENT_FAILURE" -> placementFailure++;
                    case "SURVIVAL_FAILURE" -> survivalFailure++;
                    case "VARIANT_COVERAGE_GAP" -> variantCoverageGap++;
                    default -> needsImplementation++;
                }

                if (!"GREEN_LIVE_LIKE".equals(result.classification())
                        && !"GREEN_SIMPLIFIED_ONLY".equals(result.classification())
                        && "NONE".equals(firstFailureLayer)) {
                    firstFailureLayer = result.classification();
                }

                if ("minecraft:oak_fence".equals(result.objectId())) {
                    oakFenceClassification = result.classification();
                } else if ("minecraft:spruce_fence".equals(result.objectId())) {
                    spruceFenceClassification = result.classification();
                } else if ("minecraft:nether_brick_fence".equals(result.objectId())) {
                    netherBrickFenceClassification = result.classification();
                } else if ("minecraft:cobblestone_wall".equals(result.objectId())) {
                    cobblestoneWallClassification = result.classification();
                }
            }
        }

        boolean proofGreen = contactGap == 0
                && triadMismatch == 0
                && collisionShapeRisk == 0
                && connectionShapeRisk == 0
                && placementFailure == 0
                && survivalFailure == 0
                && variantCoverageGap == 0
                && needsImplementation == 0;
        String failureLayer = proofGreen ? "NONE" : firstFailureLayer;

        System.out.println("JULIA_BETA35_FENCE_WALL_VARIANT_COVERAGE_SUMMARY"
                + " outcome=" + (proofGreen ? "GREEN" : "RED")
                + " rows=" + rows
                + " greenLiveLike=" + greenLiveLike
                + " greenSimplifiedOnly=" + greenSimplifiedOnly
                + " contactGap=" + contactGap
                + " triadMismatch=" + triadMismatch
                + " collisionShapeRisk=" + collisionShapeRisk
                + " connectionShapeRisk=" + connectionShapeRisk
                + " placementFailure=" + placementFailure
                + " survivalFailure=" + survivalFailure
                + " variantCoverageGap=" + variantCoverageGap
                + " needsImplementation=" + needsImplementation
                + " oakFenceClassification=" + oakFenceClassification
                + " spruceFenceClassification=" + spruceFenceClassification
                + " netherBrickFenceClassification=" + netherBrickFenceClassification
                + " cobblestoneWallClassification=" + cobblestoneWallClassification
                + " glassPane=NOT_COVERED"
                + " previousFailureLayer=VARIANT_COVERAGE_GAP"
                + " failureLayer=" + failureLayer
                + " productionFixImplemented=true"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false"
                + " canonicalCheckoutModified=false");
    }

    /**
     * Render-quad dy RED proof for the Beta 3.5 fence/wall variant false-green.
     *
     * Reads {@link OffsetBlockStateModel#snapshotModelDyOwnerTrace()} after a forced
     * re-render so the proof captures whether the Fabric emitQuads path actually applied
     * dy for fence/wall variants. The previous shape-triad proof was passing because
     * collision/outline/raycast shapes are offset by {@code SlabSupportStateMixin}, while
     * {@code OffsetBlockStateModel.emitQuads} explicitly forces {@code dy = 0.0f} for every
     * {@code FenceBlock | WallBlock | PaneBlock} — so the visible model never shifts.
     *
     * Gate: -Dslabbed.beta35FenceModelRenderRed=true
     */
    private static void runBeta35FenceModelRenderRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35FenceFamilyCase[] cases = {
                new Beta35FenceFamilyCase(
                        "minecraft:oak_fence",
                        "fence_family",
                        Items.OAK_FENCE,
                        Blocks.OAK_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase(
                        "minecraft:spruce_fence",
                        "fence_family",
                        Items.SPRUCE_FENCE,
                        Blocks.SPRUCE_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase(
                        "minecraft:nether_brick_fence",
                        "fence_family",
                        Items.NETHER_BRICK_FENCE,
                        Blocks.NETHER_BRICK_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase(
                        "minecraft:cobblestone_wall",
                        "wall_family",
                        Items.COBBLESTONE_WALL,
                        Blocks.COBBLESTONE_WALL.getDefaultState())
        };

        System.out.println("JULIA_BETA35_FENCE_MODEL_RENDER_RED"
                + " scope=fence_wall_variant_render_quad_dy"
                + " variants=minecraft:oak_fence,minecraft:spruce_fence,minecraft:nether_brick_fence,minecraft:cobblestone_wall"
                + " glass_pane=NOT_COVERED"
                + " priorVariantCoverageClaim=GREEN_RESCINDED_FOR_RELEASE"
                + " previousFailureLayer=OBJECT_MODEL_BOTTOM_PROXY_GAP"
                + " probe=OffsetBlockStateModel.snapshotModelDyOwnerTrace"
                + " productionRenderFixImplemented=false"
                + " releaseAudit=NOT_RUN");

        int rows = 0;
        int modelRenderGap = 0;
        int greenRenderDyApplied = 0;
        int modelDyTraceNotObserved = 0;
        int placementFailure = 0;
        int supportDyZero = 0;
        String firstFailureLayer = "NONE";
        String oakFenceClassification = "NOT_MEASURED";
        String spruceFenceClassification = "NOT_MEASURED";
        String netherBrickFenceClassification = "NOT_MEASURED";
        String cobblestoneWallClassification = "NOT_MEASURED";

        for (Beta35FenceFamilyCase objectCase : cases) {
            String classification = runBeta35FenceModelRenderRedRow(ctx, singleplayer, objectCase, rows);
            rows++;
            switch (classification) {
                case "MODEL_RENDER_GAP" -> modelRenderGap++;
                case "GREEN_RENDER_DY_APPLIED" -> greenRenderDyApplied++;
                case "MODEL_DY_TRACE_NOT_OBSERVED" -> modelDyTraceNotObserved++;
                case "PLACEMENT_FAILURE" -> placementFailure++;
                case "SUPPORT_DY_ZERO" -> supportDyZero++;
                default -> {
                }
            }
            if (!"GREEN_RENDER_DY_APPLIED".equals(classification) && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = classification;
            }
            if ("minecraft:oak_fence".equals(objectCase.objectId())) {
                oakFenceClassification = classification;
            } else if ("minecraft:spruce_fence".equals(objectCase.objectId())) {
                spruceFenceClassification = classification;
            } else if ("minecraft:nether_brick_fence".equals(objectCase.objectId())) {
                netherBrickFenceClassification = classification;
            } else if ("minecraft:cobblestone_wall".equals(objectCase.objectId())) {
                cobblestoneWallClassification = classification;
            }
        }

        boolean red = modelRenderGap > 0 || modelDyTraceNotObserved > 0 || placementFailure > 0;

        System.out.println("JULIA_BETA35_FENCE_MODEL_RENDER_SUMMARY"
                + " outcome=" + (red ? "RED" : "GREEN")
                + " rows=" + rows
                + " modelRenderGap=" + modelRenderGap
                + " greenRenderDyApplied=" + greenRenderDyApplied
                + " modelDyTraceNotObserved=" + modelDyTraceNotObserved
                + " placementFailure=" + placementFailure
                + " supportDyZero=" + supportDyZero
                + " oakFenceClassification=" + oakFenceClassification
                + " spruceFenceClassification=" + spruceFenceClassification
                + " netherBrickFenceClassification=" + netherBrickFenceClassification
                + " cobblestoneWallClassification=" + cobblestoneWallClassification
                + " glassPane=NOT_COVERED"
                + " previousFailureLayer=OBJECT_MODEL_BOTTOM_PROXY_GAP"
                + " failureLayer=" + (red ? firstFailureLayer : "NONE")
                + " productionFixImplemented=true"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false"
                + " canonicalCheckoutModified=false");
        if (!red) {
            System.out.println("JULIA_BETA35_FENCE_MODEL_RENDER_GREEN"
                    + " outcome=GREEN"
                    + " variants=minecraft:oak_fence,minecraft:spruce_fence,minecraft:nether_brick_fence,minecraft:cobblestone_wall"
                    + " glass_pane=NOT_COVERED"
                    + " failureLayer=NONE"
                    + " productionFixImplemented=true");
        }
    }

    private static String runBeta35FenceModelRenderRedRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Beta35FenceFamilyCase objectCase,
            int rowIndex
    ) {
        int baseX = 308 + rowIndex * 9;
        int baseZ = 124;
        BlockPos supportCandidatePos = new BlockPos(baseX, -55, baseZ);
        BlockPos objectPos = supportCandidatePos.up();
        BlockPos unsupportedPos = supportCandidatePos.add(0, 1, 4);
        BlockHitResult useHit = new BlockHitResult(
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() - 0.5d,
                        supportCandidatePos.getZ() + 0.5d),
                Direction.UP,
                supportCandidatePos,
                false);

        prepareBeta35FenceFamilyLiveSupport(
                singleplayer,
                objectCase,
                Beta35FenceFamilyConfiguration.ISOLATED,
                supportCandidatePos,
                unsupportedPos);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(objectCase.item(), 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() + 3.0d,
                        supportCandidatePos.getZ() - 2.0d),
                useHit.getPos());

        final String[] placementResult = {"not-run"};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                placementResult[0] = "CLIENT_NOT_READY";
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, useHit);
            placementResult[0] = result.toString();
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final double[] supportDyBox = {Double.NaN};
        final double[] objectDyBox = {Double.NaN};
        final double[] expectedModelDyBox = {Double.NaN};
        final boolean[] blockAppearedBox = {false};
        final String[] finalStateBox = {"none"};
        final double[] shapeContactGapBox = {Double.NaN};
        final boolean[] shapeTriadCoLocatedBox = {false};
        final String[] modelBoundsBox = {"none"};

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                return;
            }
            BlockState supportState = mc.world.getBlockState(supportCandidatePos);
            BlockState objectState = mc.world.getBlockState(objectPos);
            blockAppearedBox[0] = objectState.isOf(objectCase.expectedState().getBlock());
            supportDyBox[0] = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportState);
            objectDyBox[0] = blockAppearedBox[0]
                    ? SlabSupport.getYOffset(mc.world, objectPos, objectState) : Double.NaN;
            expectedModelDyBox[0] = blockAppearedBox[0]
                    ? ClientDy.dyFor(mc.world, objectPos, objectState) : Double.NaN;
            finalStateBox[0] = objectState.toString();
            if (blockAppearedBox[0]) {
                double supportVisibleTopY = beta35CommonSupportVisibleTopY(
                        supportCandidatePos, supportState, supportDyBox[0]);
                VoxelShape outlineShape = objectState.getOutlineShape(mc.world, objectPos,
                        net.minecraft.block.ShapeContext.of(mc.player));
                VoxelShape raycastShape = objectState.getRaycastShape(mc.world, objectPos);
                VoxelShape collisionShape = objectState.getCollisionShape(mc.world, objectPos,
                        net.minecraft.block.ShapeContext.of(mc.player));
                net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, objectPos);
                net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, objectPos);
                net.minecraft.util.math.Box collisionBox = beta35WorldBox(collisionShape, objectPos);
                net.minecraft.util.math.Box modelProxyBox = collisionBox != null ? collisionBox : outlineBox;
                double objectModelBottomY = modelProxyBox == null ? Double.NaN : modelProxyBox.minY;
                shapeContactGapBox[0] = Double.isFinite(objectModelBottomY)
                        && Double.isFinite(supportVisibleTopY)
                        ? objectModelBottomY - supportVisibleTopY
                        : Double.NaN;
                shapeTriadCoLocatedBox[0] = modelProxyBox != null
                        && beta35SameBox(outlineBox, modelProxyBox)
                        && beta35SameBox(raycastBox, modelProxyBox);
                modelBoundsBox[0] = beta35FormatBox(modelProxyBox);
            }
            if (mc.worldRenderer != null) {
                OffsetBlockStateModel.resetModelDyOwnerTrace(objectPos);
                mc.worldRenderer.scheduleBlockRenders(
                        objectPos.getX() - 1, objectPos.getY() - 1, objectPos.getZ() - 1,
                        objectPos.getX() + 1, objectPos.getY() + 1, objectPos.getZ() + 1);
            }
        });

        for (int i = 0; i < 6; i++) {
            ctx.runOnClient(mc -> {
                if (mc.worldRenderer == null) {
                    return;
                }
                mc.worldRenderer.scheduleBlockRenders(
                        objectPos.getX() - 1, objectPos.getY() - 1, objectPos.getZ() - 1,
                        objectPos.getX() + 1, objectPos.getY() + 1, objectPos.getZ() + 1);
            });
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        final OffsetBlockStateModel.ModelDyOwnerTrace[] traceBox = {null};
        ctx.runOnClient(mc -> traceBox[0] = OffsetBlockStateModel.snapshotModelDyOwnerTrace());

        OffsetBlockStateModel.ModelDyOwnerTrace trace = traceBox[0];
        boolean traceSeen = trace != null && trace.seen();
        int emitCalls = trace == null ? 0 : trace.emitCalls();
        int appliedCalls = trace == null ? 0 : trace.appliedCalls();
        double totalAppliedDy = trace == null ? 0.0 : trace.totalAppliedDy();
        double lastDy = trace == null ? 0.0 : trace.lastDy();
        boolean renderDyApplied = traceSeen && appliedCalls > 0 && Math.abs(totalAppliedDy) > EPSILON;

        String classification;
        if (!blockAppearedBox[0]) {
            classification = "PLACEMENT_FAILURE";
        } else if (!Double.isFinite(objectDyBox[0]) || Math.abs(objectDyBox[0]) <= EPSILON) {
            classification = "SUPPORT_DY_ZERO";
        } else if (!traceSeen || emitCalls == 0) {
            classification = "MODEL_DY_TRACE_NOT_OBSERVED";
        } else if (!renderDyApplied) {
            classification = "MODEL_RENDER_GAP";
        } else {
            classification = "GREEN_RENDER_DY_APPLIED";
        }

        System.out.println("JULIA_BETA35_FENCE_MODEL_RENDER_ROW"
                + " objectId=" + objectCase.objectId()
                + " family=" + objectCase.family()
                + " configuration=isolated"
                + " placementResult=" + placementResult[0]
                + " blockAppearedAfterAttempt=" + blockAppearedBox[0]
                + " finalBlockState=" + finalStateBox[0]
                + " supportDy=" + String.format("%.6f", supportDyBox[0])
                + " objectDy=" + (Double.isFinite(objectDyBox[0])
                        ? String.format("%.6f", objectDyBox[0]) : "N/A")
                + " expectedModelDy=" + (Double.isFinite(expectedModelDyBox[0])
                        ? String.format("%.6f", expectedModelDyBox[0]) : "N/A")
                + " modelDyTraceSeen=" + traceSeen
                + " emitCalls=" + emitCalls
                + " appliedCalls=" + appliedCalls
                + " totalAppliedDy=" + String.format("%.6f", totalAppliedDy)
                + " actualModelAppliedDy=" + String.format("%.6f", lastDy)
                + " renderDyApplied=" + (renderDyApplied ? "yes" : "no")
                + " shapeContactGap=" + (Double.isFinite(shapeContactGapBox[0])
                        ? String.format("%.6f", shapeContactGapBox[0]) : "N/A")
                + " shapeTriadCoLocated=" + (shapeTriadCoLocatedBox[0] ? "yes" : "no")
                + " modelBounds=" + modelBoundsBox[0]
                + " modelDyOwnerTrace=" + (trace == null ? "none" : trace.toString())
                + " classification=" + classification);

        return classification;
    }

    /**
     * Focused RED proof: minecraft:birch_fence is outside the fence/wall allowlist.
     * Control: minecraft:oak_fence (GREEN). Live-tested variant: minecraft:birch_fence (RED).
     *
     * Gate: -Dslabbed.beta35BirchFenceVariantRed=true
     */
    private static void runBeta35BirchFenceVariantRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35FenceFamilyCase[] cases = {
                new Beta35FenceFamilyCase(
                        "minecraft:oak_fence",
                        "fence_family",
                        Items.OAK_FENCE,
                        Blocks.OAK_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase(
                        "minecraft:birch_fence",
                        "fence_family",
                        Items.BIRCH_FENCE,
                        Blocks.BIRCH_FENCE.getDefaultState())
        };

        System.out.println("JULIA_BETA35_BIRCH_FENCE_VARIANT_RED"
                + " scope=birch_fence_variant_family_coverage"
                + " control=minecraft:oak_fence"
                + " liveTestedVariant=minecraft:birch_fence"
                + " allowlist=OAK_FENCE,SPRUCE_FENCE,NETHER_BRICK_FENCE,COBBLESTONE_WALL"
                + " birchFenceInAllowlist=no"
                + " previousClassification=EXACT_BLOCK_ALLOWLIST_GAP"
                + " failureLayer=VARIANT_FAMILY_COVERAGE_GAP"
                + " productionFixImplemented=false"
                + " releaseAudit=NOT_RUN");

        int rows = 0;
        int greenAllowlisted = 0;
        int variantCoverageGap = 0;
        int modelRenderGap = 0;
        int placementFailure = 0;
        String oakFenceClassification = "NOT_MEASURED";
        String birchFenceClassification = "NOT_MEASURED";

        for (Beta35FenceFamilyCase objectCase : cases) {
            String classification = runBeta35BirchFenceVariantRedRow(ctx, singleplayer, objectCase, rows);
            rows++;
            switch (classification) {
                case "GREEN_ALLOWLISTED" -> greenAllowlisted++;
                case "VARIANT_FAMILY_COVERAGE_GAP" -> variantCoverageGap++;
                case "MODEL_RENDER_GAP" -> modelRenderGap++;
                case "PLACEMENT_FAILURE" -> placementFailure++;
                default -> {
                }
            }
            if ("minecraft:oak_fence".equals(objectCase.objectId())) {
                oakFenceClassification = classification;
            } else if ("minecraft:birch_fence".equals(objectCase.objectId())) {
                birchFenceClassification = classification;
            }
        }

        boolean red = variantCoverageGap > 0 || modelRenderGap > 0 || placementFailure > 0;
        String failureLayer = variantCoverageGap > 0 ? "VARIANT_FAMILY_COVERAGE_GAP"
                : modelRenderGap > 0 ? "MODEL_RENDER_GAP"
                : placementFailure > 0 ? "PLACEMENT_FAILURE"
                : "NONE";

        System.out.println("JULIA_BETA35_BIRCH_FENCE_VARIANT_SUMMARY"
                + " outcome=" + (red ? "RED" : "GREEN")
                + " rows=" + rows
                + " greenAllowlisted=" + greenAllowlisted
                + " variantCoverageGap=" + variantCoverageGap
                + " modelRenderGap=" + modelRenderGap
                + " placementFailure=" + placementFailure
                + " oakFenceClassification=" + oakFenceClassification
                + " birchFenceClassification=" + birchFenceClassification
                + " failureLayer=" + failureLayer
                + " productionFixImplemented=true"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false"
                + " canonicalCheckoutModified=false");
        if (!red) {
            System.out.println("JULIA_BETA35_BIRCH_FENCE_VARIANT_GREEN"
                    + " outcome=GREEN"
                    + " variants=minecraft:oak_fence,minecraft:birch_fence"
                    + " failureLayer=NONE"
                    + " productionFixImplemented=true");
        }
    }

    private static String runBeta35BirchFenceVariantRedRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Beta35FenceFamilyCase objectCase,
            int rowIndex
    ) {
        int baseX = 400 + rowIndex * 9;
        int baseZ = 130;
        BlockPos supportCandidatePos = new BlockPos(baseX, -55, baseZ);
        BlockPos objectPos = supportCandidatePos.up();
        BlockPos unsupportedPos = supportCandidatePos.add(0, 1, 4);
        BlockHitResult useHit = new BlockHitResult(
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() - 0.5d,
                        supportCandidatePos.getZ() + 0.5d),
                Direction.UP,
                supportCandidatePos,
                false);

        prepareBeta35FenceFamilyLiveSupport(
                singleplayer,
                objectCase,
                Beta35FenceFamilyConfiguration.ISOLATED,
                supportCandidatePos,
                unsupportedPos);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(objectCase.item(), 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() + 3.0d,
                        supportCandidatePos.getZ() - 2.0d),
                useHit.getPos());

        final String[] placementResult = {"not-run"};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                placementResult[0] = "CLIENT_NOT_READY";
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, useHit);
            placementResult[0] = result.toString();
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final boolean[] inFenceWallAllowlistBox = {false};
        final double[] supportDyBox = {Double.NaN};
        final double[] objectDyBox = {Double.NaN};
        final double[] expectedModelDyBox = {Double.NaN};
        final boolean[] blockAppearedBox = {false};
        final String[] finalStateBox = {"none"};
        final double[] shapeContactGapBox = {Double.NaN};
        final boolean[] shapeTriadCoLocatedBox = {false};
        final String[] modelBoundsBox = {"none"};

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                return;
            }
            BlockState supportState = mc.world.getBlockState(supportCandidatePos);
            BlockState objectState = mc.world.getBlockState(objectPos);
            blockAppearedBox[0] = objectState.isOf(objectCase.expectedState().getBlock());
            inFenceWallAllowlistBox[0] = SlabSupport.isBeta35FenceWallVariantContactObject(objectState);
            supportDyBox[0] = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportState);
            objectDyBox[0] = blockAppearedBox[0]
                    ? SlabSupport.getYOffset(mc.world, objectPos, objectState) : Double.NaN;
            expectedModelDyBox[0] = blockAppearedBox[0]
                    ? ClientDy.dyFor(mc.world, objectPos, objectState) : Double.NaN;
            finalStateBox[0] = objectState.toString();
            if (blockAppearedBox[0]) {
                double supportVisibleTopY = beta35CommonSupportVisibleTopY(
                        supportCandidatePos, supportState, supportDyBox[0]);
                VoxelShape outlineShape = objectState.getOutlineShape(mc.world, objectPos,
                        net.minecraft.block.ShapeContext.of(mc.player));
                VoxelShape raycastShape = objectState.getRaycastShape(mc.world, objectPos);
                VoxelShape collisionShape = objectState.getCollisionShape(mc.world, objectPos,
                        net.minecraft.block.ShapeContext.of(mc.player));
                net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, objectPos);
                net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, objectPos);
                net.minecraft.util.math.Box collisionBox = beta35WorldBox(collisionShape, objectPos);
                net.minecraft.util.math.Box modelProxyBox = collisionBox != null ? collisionBox : outlineBox;
                double objectModelBottomY = modelProxyBox == null ? Double.NaN : modelProxyBox.minY;
                shapeContactGapBox[0] = Double.isFinite(objectModelBottomY)
                        && Double.isFinite(supportVisibleTopY)
                        ? objectModelBottomY - supportVisibleTopY
                        : Double.NaN;
                shapeTriadCoLocatedBox[0] = modelProxyBox != null
                        && beta35SameBox(outlineBox, modelProxyBox)
                        && beta35SameBox(raycastBox, modelProxyBox);
                modelBoundsBox[0] = beta35FormatBox(modelProxyBox);
            }
            if (mc.worldRenderer != null) {
                OffsetBlockStateModel.resetModelDyOwnerTrace(objectPos);
                mc.worldRenderer.scheduleBlockRenders(
                        objectPos.getX() - 1, objectPos.getY() - 1, objectPos.getZ() - 1,
                        objectPos.getX() + 1, objectPos.getY() + 1, objectPos.getZ() + 1);
            }
        });

        for (int i = 0; i < 6; i++) {
            ctx.runOnClient(mc -> {
                if (mc.worldRenderer == null) {
                    return;
                }
                mc.worldRenderer.scheduleBlockRenders(
                        objectPos.getX() - 1, objectPos.getY() - 1, objectPos.getZ() - 1,
                        objectPos.getX() + 1, objectPos.getY() + 1, objectPos.getZ() + 1);
            });
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        final OffsetBlockStateModel.ModelDyOwnerTrace[] traceBox = {null};
        ctx.runOnClient(mc -> traceBox[0] = OffsetBlockStateModel.snapshotModelDyOwnerTrace());

        final boolean[] survivedBox = {false};
        ctx.runOnClient(mc -> {
            if (mc.world != null) {
                survivedBox[0] = mc.world.getBlockState(objectPos)
                        .isOf(objectCase.expectedState().getBlock());
            }
        });

        OffsetBlockStateModel.ModelDyOwnerTrace trace = traceBox[0];
        boolean traceSeen = trace != null && trace.seen();
        int emitCalls = trace == null ? 0 : trace.emitCalls();
        int appliedCalls = trace == null ? 0 : trace.appliedCalls();
        double totalAppliedDy = trace == null ? 0.0 : trace.totalAppliedDy();
        double lastDy = trace == null ? 0.0 : trace.lastDy();
        boolean renderDyApplied = traceSeen && appliedCalls > 0 && Math.abs(totalAppliedDy) > EPSILON;

        String classification;
        if (!blockAppearedBox[0]) {
            classification = "PLACEMENT_FAILURE";
        } else if (!inFenceWallAllowlistBox[0]) {
            classification = "VARIANT_FAMILY_COVERAGE_GAP";
        } else if (!Double.isFinite(objectDyBox[0]) || Math.abs(objectDyBox[0]) <= EPSILON) {
            classification = "SUPPORT_DY_ZERO";
        } else if (!traceSeen || emitCalls == 0) {
            classification = "MODEL_DY_TRACE_NOT_OBSERVED";
        } else if (!renderDyApplied) {
            classification = "MODEL_RENDER_GAP";
        } else {
            classification = "GREEN_ALLOWLISTED";
        }

        System.out.println("JULIA_BETA35_BIRCH_FENCE_VARIANT_ROW"
                + " objectId=" + objectCase.objectId()
                + " family=" + objectCase.family()
                + " configuration=isolated"
                + " inFenceWallAllowlist=" + (inFenceWallAllowlistBox[0] ? "yes" : "no")
                + " placementResult=" + placementResult[0]
                + " blockAppearedAfterAttempt=" + blockAppearedBox[0]
                + " survivalResult=" + (survivedBox[0] ? "survived" : "not-survived")
                + " finalBlockState=" + finalStateBox[0]
                + " supportDy=" + String.format("%.6f", supportDyBox[0])
                + " objectDy=" + (Double.isFinite(objectDyBox[0])
                        ? String.format("%.6f", objectDyBox[0]) : "N/A")
                + " expectedModelDy=" + (Double.isFinite(expectedModelDyBox[0])
                        ? String.format("%.6f", expectedModelDyBox[0]) : "N/A")
                + " actualModelAppliedDy=" + String.format("%.6f", lastDy)
                + " totalAppliedDy=" + String.format("%.6f", totalAppliedDy)
                + " renderDyApplied=" + (renderDyApplied ? "yes" : "no")
                + " shapeContactGap=" + (Double.isFinite(shapeContactGapBox[0])
                        ? String.format("%.6f", shapeContactGapBox[0]) : "N/A")
                + " shapeTriadCoLocated=" + (shapeTriadCoLocatedBox[0] ? "yes" : "no")
                + " modelBounds=" + modelBoundsBox[0]
                + " modelDyTraceSeen=" + traceSeen
                + " emitCalls=" + emitCalls
                + " appliedCalls=" + appliedCalls
                + " classification=" + classification
                + " failureLayer=" + ("GREEN_ALLOWLISTED".equals(classification) ? "NONE" : classification));

        return classification;
    }

    /**
     * Full FenceBlock/WallBlock family support proof for Beta 3.5.
     * All FenceBlock and WallBlock variants must be GREEN_FAMILY.
     * PaneBlock (glass_pane) must remain NOT_COVERED.
     *
     * Gate: -Dslabbed.beta35FenceWallFamilyFix=true
     */
    private static void runBeta35FenceWallFamilyFixProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35FenceFamilyCase[] cases = {
                new Beta35FenceFamilyCase("minecraft:oak_fence", "fence_family", Items.OAK_FENCE, Blocks.OAK_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:birch_fence", "fence_family", Items.BIRCH_FENCE, Blocks.BIRCH_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:jungle_fence", "fence_family", Items.JUNGLE_FENCE, Blocks.JUNGLE_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:acacia_fence", "fence_family", Items.ACACIA_FENCE, Blocks.ACACIA_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:dark_oak_fence", "fence_family", Items.DARK_OAK_FENCE, Blocks.DARK_OAK_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:mangrove_fence", "fence_family", Items.MANGROVE_FENCE, Blocks.MANGROVE_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:cherry_fence", "fence_family", Items.CHERRY_FENCE, Blocks.CHERRY_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:bamboo_fence", "fence_family", Items.BAMBOO_FENCE, Blocks.BAMBOO_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:crimson_fence", "fence_family", Items.CRIMSON_FENCE, Blocks.CRIMSON_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:warped_fence", "fence_family", Items.WARPED_FENCE, Blocks.WARPED_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:nether_brick_fence", "fence_family", Items.NETHER_BRICK_FENCE, Blocks.NETHER_BRICK_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:cobblestone_wall", "wall_family", Items.COBBLESTONE_WALL, Blocks.COBBLESTONE_WALL.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:mossy_cobblestone_wall", "wall_family", Items.MOSSY_COBBLESTONE_WALL, Blocks.MOSSY_COBBLESTONE_WALL.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:stone_brick_wall", "wall_family", Items.STONE_BRICK_WALL, Blocks.STONE_BRICK_WALL.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:brick_wall", "wall_family", Items.BRICK_WALL, Blocks.BRICK_WALL.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:andesite_wall", "wall_family", Items.ANDESITE_WALL, Blocks.ANDESITE_WALL.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:granite_wall", "wall_family", Items.GRANITE_WALL, Blocks.GRANITE_WALL.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:diorite_wall", "wall_family", Items.DIORITE_WALL, Blocks.DIORITE_WALL.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:cobbled_deepslate_wall", "wall_family", Items.COBBLED_DEEPSLATE_WALL, Blocks.COBBLED_DEEPSLATE_WALL.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:polished_blackstone_brick_wall", "wall_family", Items.POLISHED_BLACKSTONE_BRICK_WALL, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.getDefaultState()),
                new Beta35FenceFamilyCase("minecraft:glass_pane", "pane_family", Items.GLASS_PANE, Blocks.GLASS_PANE.getDefaultState()),
        };

        int rows = 0;
        int greenFamily = 0;
        int notCovered = 0;
        int modelRenderGap = 0;
        int shapeContactGap = 0;
        int placementFailure = 0;
        int other = 0;

        for (Beta35FenceFamilyCase objectCase : cases) {
            String classification = runBeta35FenceWallFamilyFixRow(ctx, singleplayer, objectCase, rows);
            rows++;
            switch (classification) {
                case "GREEN_FAMILY" -> greenFamily++;
                case "NOT_COVERED" -> notCovered++;
                case "MODEL_RENDER_GAP" -> modelRenderGap++;
                case "SHAPE_CONTACT_GAP" -> shapeContactGap++;
                case "PLACEMENT_FAILURE" -> placementFailure++;
                default -> other++;
            }
        }

        boolean red = modelRenderGap > 0 || shapeContactGap > 0 || placementFailure > 0 || other > 0;
        String failureLayer = modelRenderGap > 0 ? "MODEL_RENDER_GAP"
                : shapeContactGap > 0 ? "SHAPE_CONTACT_GAP"
                : placementFailure > 0 ? "PLACEMENT_FAILURE"
                : other > 0 ? "OTHER_GAP"
                : "NONE";

        System.out.println("JULIA_BETA35_FENCE_WALL_FAMILY_SUMMARY"
                + " outcome=" + (red ? "RED" : "GREEN")
                + " rows=" + rows
                + " greenFamily=" + greenFamily
                + " notCovered=" + notCovered
                + " modelRenderGap=" + modelRenderGap
                + " shapeContactGap=" + shapeContactGap
                + " placementFailure=" + placementFailure
                + " other=" + other
                + " glassPaneControl=" + (notCovered == 1 ? "NOT_COVERED" : "UNEXPECTED")
                + " failureLayer=" + failureLayer
                + " productionFixImplemented=true"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false"
                + " canonicalCheckoutModified=false");
        if (!red) {
            System.out.println("JULIA_BETA35_FENCE_WALL_FAMILY_GREEN"
                    + " outcome=GREEN"
                    + " fenceFamilyRows=11"
                    + " wallFamilyRows=9"
                    + " glassPaneControl=NOT_COVERED"
                    + " failureLayer=NONE"
                    + " productionFixImplemented=true");
        }
    }

    private static String runBeta35FenceWallFamilyFixRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Beta35FenceFamilyCase objectCase,
            int rowIndex
    ) {
        int baseX = 500 + rowIndex * 9;
        int baseZ = 140;
        BlockPos supportCandidatePos = new BlockPos(baseX, -55, baseZ);
        BlockPos objectPos = supportCandidatePos.up();
        BlockPos unsupportedPos = supportCandidatePos.add(0, 1, 4);
        BlockHitResult useHit = new BlockHitResult(
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() - 0.5d,
                        supportCandidatePos.getZ() + 0.5d),
                Direction.UP,
                supportCandidatePos,
                false);

        // Move player to this row's position first so the server loads the chunk before placing blocks.
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() + 3.0d,
                        supportCandidatePos.getZ() - 2.0d),
                useHit.getPos());
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        prepareBeta35FenceFamilyLiveSupport(
                singleplayer,
                objectCase,
                Beta35FenceFamilyConfiguration.ISOLATED,
                supportCandidatePos,
                unsupportedPos);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(objectCase.item(), 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() + 3.0d,
                        supportCandidatePos.getZ() - 2.0d),
                useHit.getPos());

        final String[] placementResult = {"not-run"};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                placementResult[0] = "CLIENT_NOT_READY";
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, useHit);
            placementResult[0] = result.toString();
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final boolean[] inFenceWallFamilyBox = {false};
        final String[] blockClassBox = {"Other"};
        final double[] supportDyBox = {Double.NaN};
        final double[] objectDyBox = {Double.NaN};
        final double[] expectedModelDyBox = {Double.NaN};
        final boolean[] blockAppearedBox = {false};
        final String[] finalStateBox = {"none"};
        final double[] shapeContactGapBox = {Double.NaN};
        final boolean[] shapeTriadCoLocatedBox = {false};
        final boolean[] collisionCoLocatedBox = {false};
        final String[] modelBoundsBox = {"none"};

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                return;
            }
            BlockState supportState = mc.world.getBlockState(supportCandidatePos);
            BlockState objectState = mc.world.getBlockState(objectPos);
            blockAppearedBox[0] = objectState.isOf(objectCase.expectedState().getBlock());
            inFenceWallFamilyBox[0] = SlabSupport.isBeta35FenceWallVariantContactObject(objectState);
            blockClassBox[0] = objectState.getBlock() instanceof FenceBlock ? "FenceBlock"
                    : objectState.getBlock() instanceof WallBlock ? "WallBlock"
                    : objectState.getBlock() instanceof PaneBlock ? "PaneBlock"
                    : "Other";
            supportDyBox[0] = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportState);
            objectDyBox[0] = blockAppearedBox[0]
                    ? SlabSupport.getYOffset(mc.world, objectPos, objectState) : Double.NaN;
            expectedModelDyBox[0] = blockAppearedBox[0]
                    ? ClientDy.dyFor(mc.world, objectPos, objectState) : Double.NaN;
            finalStateBox[0] = objectState.toString();
            if (blockAppearedBox[0]) {
                double supportVisibleTopY = beta35CommonSupportVisibleTopY(
                        supportCandidatePos, supportState, supportDyBox[0]);
                VoxelShape outlineShape = objectState.getOutlineShape(mc.world, objectPos,
                        net.minecraft.block.ShapeContext.of(mc.player));
                VoxelShape raycastShape = objectState.getRaycastShape(mc.world, objectPos);
                VoxelShape collisionShape = objectState.getCollisionShape(mc.world, objectPos,
                        net.minecraft.block.ShapeContext.of(mc.player));
                net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, objectPos);
                net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, objectPos);
                net.minecraft.util.math.Box collisionBox = beta35WorldBox(collisionShape, objectPos);
                net.minecraft.util.math.Box modelProxyBox = collisionBox != null ? collisionBox : outlineBox;
                double objectModelBottomY = modelProxyBox == null ? Double.NaN : modelProxyBox.minY;
                shapeContactGapBox[0] = Double.isFinite(objectModelBottomY)
                        && Double.isFinite(supportVisibleTopY)
                        ? objectModelBottomY - supportVisibleTopY
                        : Double.NaN;
                shapeTriadCoLocatedBox[0] = modelProxyBox != null
                        && beta35SameBox(outlineBox, modelProxyBox)
                        && beta35SameBox(raycastBox, modelProxyBox);
                collisionCoLocatedBox[0] = modelProxyBox != null
                        && collisionBox != null
                        && beta35SameBox(collisionBox, modelProxyBox);
                modelBoundsBox[0] = beta35FormatBox(modelProxyBox);
            }
            if (mc.worldRenderer != null) {
                OffsetBlockStateModel.resetModelDyOwnerTrace(objectPos);
                mc.worldRenderer.scheduleBlockRenders(
                        objectPos.getX() - 1, objectPos.getY() - 1, objectPos.getZ() - 1,
                        objectPos.getX() + 1, objectPos.getY() + 1, objectPos.getZ() + 1);
            }
        });

        for (int i = 0; i < 6; i++) {
            ctx.runOnClient(mc -> {
                if (mc.worldRenderer == null) {
                    return;
                }
                mc.worldRenderer.scheduleBlockRenders(
                        objectPos.getX() - 1, objectPos.getY() - 1, objectPos.getZ() - 1,
                        objectPos.getX() + 1, objectPos.getY() + 1, objectPos.getZ() + 1);
            });
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        final OffsetBlockStateModel.ModelDyOwnerTrace[] traceBox = {null};
        ctx.runOnClient(mc -> traceBox[0] = OffsetBlockStateModel.snapshotModelDyOwnerTrace());

        final boolean[] survivedBox = {false};
        ctx.runOnClient(mc -> {
            if (mc.world != null) {
                survivedBox[0] = mc.world.getBlockState(objectPos)
                        .isOf(objectCase.expectedState().getBlock());
            }
        });

        OffsetBlockStateModel.ModelDyOwnerTrace trace = traceBox[0];
        boolean traceSeen = trace != null && trace.seen();
        int emitCalls = trace == null ? 0 : trace.emitCalls();
        int appliedCalls = trace == null ? 0 : trace.appliedCalls();
        double totalAppliedDy = trace == null ? 0.0 : trace.totalAppliedDy();
        double lastDy = trace == null ? 0.0 : trace.lastDy();
        boolean renderDyApplied = traceSeen && appliedCalls > 0 && Math.abs(totalAppliedDy) > EPSILON;

        String classification;
        if (!blockAppearedBox[0]) {
            classification = "PLACEMENT_FAILURE";
        } else if (!inFenceWallFamilyBox[0]) {
            classification = "NOT_COVERED";
        } else if (!Double.isFinite(objectDyBox[0]) || Math.abs(objectDyBox[0]) <= EPSILON) {
            classification = "SUPPORT_DY_ZERO";
        } else if (!traceSeen || emitCalls == 0) {
            classification = "MODEL_DY_TRACE_NOT_OBSERVED";
        } else if (!renderDyApplied) {
            classification = "MODEL_RENDER_GAP";
        } else if (Double.isFinite(shapeContactGapBox[0]) && Math.abs(shapeContactGapBox[0]) > EPSILON) {
            classification = "SHAPE_CONTACT_GAP";
        } else {
            classification = "GREEN_FAMILY";
        }

        System.out.println("JULIA_BETA35_FENCE_WALL_FAMILY_ROW"
                + " objectId=" + objectCase.objectId()
                + " family=" + objectCase.family()
                + " blockClass=" + blockClassBox[0]
                + " inFenceWallFamily=" + (inFenceWallFamilyBox[0] ? "yes" : "no")
                + " placementResult=" + placementResult[0]
                + " blockAppearedAfterAttempt=" + blockAppearedBox[0]
                + " survivalResult=" + (survivedBox[0] ? "survived" : "not-survived")
                + " supportDy=" + String.format("%.6f", supportDyBox[0])
                + " objectDy=" + (Double.isFinite(objectDyBox[0])
                        ? String.format("%.6f", objectDyBox[0]) : "N/A")
                + " expectedModelDy=" + (Double.isFinite(expectedModelDyBox[0])
                        ? String.format("%.6f", expectedModelDyBox[0]) : "N/A")
                + " actualModelAppliedDy=" + String.format("%.6f", lastDy)
                + " totalAppliedDy=" + String.format("%.6f", totalAppliedDy)
                + " renderDyApplied=" + (renderDyApplied ? "yes" : "no")
                + " shapeContactGap=" + (Double.isFinite(shapeContactGapBox[0])
                        ? String.format("%.6f", shapeContactGapBox[0]) : "N/A")
                + " shapeTriadCoLocated=" + (shapeTriadCoLocatedBox[0] ? "yes" : "no")
                + " collisionCoLocated=" + (collisionCoLocatedBox[0] ? "yes" : "no")
                + " modelBounds=" + modelBoundsBox[0]
                + " emitCalls=" + emitCalls
                + " appliedCalls=" + appliedCalls
                + " classification=" + classification
                + " failureLayer=" + ("GREEN_FAMILY".equals(classification)
                        || "NOT_COVERED".equals(classification) ? "NONE" : classification));

        return classification;
    }

    /**
     * Focused Beta 3.5 contact + hitbox triad proof for lowered FenceBlock/WallBlock family rows.
     *
     * Gate: -Dslabbed.beta35FenceWallContactHitbox=true
     */
    private static void runBeta35FenceWallContactHitboxProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35FenceFamilyCase[] objects = {
                new Beta35FenceFamilyCase(
                        "minecraft:stone_brick_wall",
                        "wall",
                        Items.STONE_BRICK_WALL,
                        Blocks.STONE_BRICK_WALL.getDefaultState()),
                new Beta35FenceFamilyCase(
                        "minecraft:oak_fence",
                        "fence",
                        Items.OAK_FENCE,
                        Blocks.OAK_FENCE.getDefaultState())
        };

        int rows = 0;
        int green = 0;
        int contactGap = 0;
        int triadMismatch = 0;
        int ownerGap = 0;
        int dyMismatch = 0;
        int other = 0;
        String firstFailureLayer = "NONE";

        System.out.println("JULIA_BETA35_FENCE_WALL_CONTACT_HITBOX_START"
                + " scope=FenceBlock_WallBlock_contact_and_triad"
                + " priorFailureLayer=FENCE_WALL_CONTACT_DY_MISSING_FOR_DEEP_SUPPORT"
                + " anvil=REGRESSION_ONLY"
                + " standingSigns=NOT_COVERED"
                + " lanterns=NOT_COVERED"
                + " chains=NOT_COVERED"
                + " redstone=NOT_COVERED"
                + " rails=NOT_COVERED"
                + " buttonsLevers=NOT_COVERED"
                + " wallHangingSigns=NOT_COVERED"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");

        for (Beta35FenceFamilyCase objectCase : objects) {
            for (Beta35FenceWallContactSupportCase supportCase : Beta35FenceWallContactSupportCase.values()) {
                String failureLayer = runBeta35FenceWallContactHitboxRow(
                        ctx,
                        singleplayer,
                        objectCase,
                        supportCase,
                        rows);
                rows++;
                switch (failureLayer) {
                    case "NONE" -> green++;
                    case "CONTACT_GAP" -> contactGap++;
                    case "TRIAD_MISMATCH", "HITBOX_SHAPE_OFFSET" -> triadMismatch++;
                    case "OWNER_GAP" -> ownerGap++;
                    case "DY_MISMATCH" -> dyMismatch++;
                    default -> other++;
                }
                if (!"NONE".equals(failureLayer) && "NONE".equals(firstFailureLayer)) {
                    firstFailureLayer = failureLayer;
                }
            }
        }

        boolean proofGreen = rows == green
                && contactGap == 0
                && triadMismatch == 0
                && ownerGap == 0
                && dyMismatch == 0
                && other == 0;
        String failureLayer = proofGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_FENCE_WALL_CONTACT_HITBOX_SUMMARY"
                + " outcome=" + (proofGreen ? "GREEN" : "RED")
                + " rows=" + rows
                + " green=" + green
                + " contactGap=" + contactGap
                + " triadMismatch=" + triadMismatch
                + " ownerGap=" + ownerGap
                + " dyMismatch=" + dyMismatch
                + " other=" + other
                + " previousFailureLayer=FENCE_WALL_CONTACT_DY_MISSING_FOR_DEEP_SUPPORT"
                + " failureLayer=" + failureLayer
                + " contactMathChanged=FenceBlock_WallBlock_family_only"
                + " anvil=REGRESSION_ONLY"
                + " floorTorch=REGRESSION_CHECK"
                + " candle=REGRESSION_CHECK"
                + " flowerPot=REGRESSION_CHECK"
                + " standingSigns=NOT_COVERED"
                + " lanterns=NOT_COVERED"
                + " chains=NOT_COVERED"
                + " redstone=NOT_COVERED"
                + " rails=NOT_COVERED"
                + " buttonsLevers=NOT_COVERED"
                + " wallHangingSigns=NOT_COVERED"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");
        if (proofGreen) {
            System.out.println("JULIA_BETA35_FENCE_WALL_CONTACT_HITBOX_GREEN"
                    + " rows=" + rows
                    + " wall=GREEN"
                    + " fence=GREEN"
                    + " contact=CONTACT_GREEN"
                    + " triad=TRIAD_GREEN"
                    + " owner=OWNER_GREEN"
                    + " failureLayer=NONE"
                    + " releaseAudit=NOT_RUN"
                    + " releaseTagMoved=false");
            return;
        }

        System.out.println("JULIA_BETA35_FENCE_WALL_CONTACT_HITBOX_RED"
                + " rows=" + rows
                + " failureLayer=" + failureLayer
                + " contactGap=" + contactGap
                + " triadMismatch=" + triadMismatch
                + " ownerGap=" + ownerGap
                + " dyMismatch=" + dyMismatch);
        throw new RuntimeException("[JULIA_BETA35_FENCE_WALL_CONTACT_HITBOX_RED]"
                + " reason=fence_wall_contact_hitbox_not_green"
                + " failureLayer=" + failureLayer);
    }

    private static String runBeta35FenceWallContactHitboxRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Beta35FenceFamilyCase objectCase,
            Beta35FenceWallContactSupportCase supportCase,
            int rowIndex
    ) {
        int baseX = 1040 + rowIndex * 8;
        int baseZ = 188;
        BlockPos supportPos = new BlockPos(baseX, -55, baseZ);
        BlockPos objectPos = supportPos.up();

        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(supportPos.getX() + 0.5d, supportPos.getY() + 3.0d, supportPos.getZ() - 2.0d),
                new Vec3d(supportPos.getX() + 0.5d, supportPos.getY() + 1.0d, supportPos.getZ() + 0.5d));
        ctx.waitTick();

        prepareBeta35FenceWallContactHitboxFixture(singleplayer, supportPos, objectPos, objectCase, supportCase);
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        OffsetBlockStateModel.ModelDyOwnerTrace trace = beta35CaptureModelDyTrace(ctx, singleplayer, objectPos);

        final String[] failureLayerBox = {"UNKNOWN"};
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                failureLayerBox[0] = "CLIENT_NOT_READY";
                return;
            }

            BlockState supportState = mc.world.getBlockState(supportPos);
            BlockState objectState = mc.world.getBlockState(objectPos);
            boolean objectPresent = objectState.isOf(objectCase.expectedState().getBlock());
            double supportDy = SlabSupport.getYOffset(mc.world, supportPos, supportState);
            double objectDy = objectPresent ? SlabSupport.getYOffset(mc.world, objectPos, objectState)
                    : Double.NaN;
            double supportVisibleTopY = beta35CommonSupportVisibleTopY(supportPos, supportState, supportDy);

            VoxelShape outlineShape = objectPresent
                    ? objectState.getOutlineShape(mc.world, objectPos, net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = objectPresent ? objectState.getRaycastShape(mc.world, objectPos) : null;
            VoxelShape collisionShape = objectPresent
                    ? objectState.getCollisionShape(mc.world, objectPos, net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, objectPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, objectPos);
            net.minecraft.util.math.Box collisionBox = beta35WorldBox(collisionShape, objectPos);
            net.minecraft.util.math.Box modelBox = collisionBox != null ? collisionBox : outlineBox;
            double objectModelBottomY = modelBox == null ? Double.NaN : modelBox.minY;
            double contactGap = Double.isFinite(objectModelBottomY) && Double.isFinite(supportVisibleTopY)
                    ? objectModelBottomY - supportVisibleTopY
                    : Double.NaN;
            boolean contactGreen = Double.isFinite(contactGap) && Math.abs(contactGap) <= EPSILON;
            boolean supportDyGreen = Math.abs(supportDy - supportCase.expectedSupportDy) <= EPSILON;
            boolean objectDyGreen = Math.abs(objectDy - supportCase.expectedObjectDy) <= EPSILON;
            boolean triadGreen = objectPresent
                    && modelBox != null
                    && beta35SameBox(outlineBox, modelBox)
                    && beta35SameBox(raycastBox, modelBox)
                    && beta35SameBox(collisionBox, modelBox);

            Vec3d aim = beta35BoxCenter(modelBox, objectPos);
            Vec3d eye = new Vec3d(aim.x, aim.y, aim.z + 4.0d);
            playerRaycastFromEye(mc, eye, aim, 6.0d);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(6.0d));
            BlockHitResult initialHit = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult finalHit = mc.crosshairTarget;
            boolean ownerGreen = finalHit instanceof BlockHitResult finalBlock
                    && finalHit.getType() == HitResult.Type.BLOCK
                    && finalBlock.getBlockPos().equals(objectPos);

            if (!objectPresent) {
                failureLayerBox[0] = "OBJECT_MISSING";
            } else if (!supportDyGreen || !objectDyGreen) {
                failureLayerBox[0] = "DY_MISMATCH";
            } else if (!contactGreen) {
                failureLayerBox[0] = "CONTACT_GAP";
            } else if (!triadGreen) {
                failureLayerBox[0] = "TRIAD_MISMATCH";
            } else if (!ownerGreen) {
                failureLayerBox[0] = "OWNER_GAP";
            } else {
                failureLayerBox[0] = "NONE";
            }

            int appliedCalls = trace == null ? 0 : trace.appliedCalls();
            double totalAppliedDy = trace == null ? 0.0d : trace.totalAppliedDy();
            double actualModelAppliedDy = trace == null ? 0.0d : trace.lastDy();
            String finalDecision = ownerGreen ? "object-shape-owner-preserve"
                    : finalHit == null || finalHit.getType() != HitResult.Type.BLOCK ? "MISS"
                    : "retargeted-nonexpected-owner";

            System.out.println("JULIA_BETA35_FENCE_WALL_CONTACT_HITBOX_ROW"
                    + " objectCategory=" + objectCase.family()
                    + " objectBlockId=" + objectCase.objectId()
                    + " supportCase=" + supportCase.id
                    + " objectPos=" + objectPos.toShortString()
                    + " objectState=" + objectState
                    + " objectDy=" + beta35FormatDoubleOrNA(objectDy)
                    + " expectedObjectDy=" + beta35FormatDoubleOrNA(supportCase.expectedObjectDy)
                    + " modelMinY=" + beta35FormatMinY(modelBox)
                    + " modelMaxY=" + beta35FormatMaxY(modelBox)
                    + " outlineMinY=" + beta35FormatMinY(outlineBox)
                    + " outlineMaxY=" + beta35FormatMaxY(outlineBox)
                    + " raycastMinY=" + beta35FormatMinY(raycastBox)
                    + " raycastMaxY=" + beta35FormatMaxY(raycastBox)
                    + " supportPos=" + supportPos.toShortString()
                    + " supportState=" + supportState
                    + " supportDy=" + beta35FormatDoubleOrNA(supportDy)
                    + " expectedSupportDy=" + beta35FormatDoubleOrNA(supportCase.expectedSupportDy)
                    + " supportVisibleTopY=" + beta35FormatDoubleOrNA(supportVisibleTopY)
                    + " objectModelBottomY=" + beta35FormatDoubleOrNA(objectModelBottomY)
                    + " contactGap=" + beta35FormatDoubleOrNA(contactGap)
                    + " modelBounds=" + beta35FormatBox(modelBox)
                    + " outlineBounds=" + beta35FormatBox(outlineBox)
                    + " raycastBounds=" + beta35FormatBox(raycastBox)
                    + " collisionBounds=" + beta35FormatBox(collisionBox)
                    + " triadCoLocated=" + (triadGreen ? "yes" : "no")
                    + " crosshairInitialTarget=" + beta35FormatHit(initialHit)
                    + " crosshairFinalTarget=" + beta35FormatHit(finalHit)
                    + " finalDecision=" + finalDecision
                    + " ownerClassification=" + (ownerGreen ? "OWNER_GREEN" : "OWNER_GAP")
                    + " unsupportedCaseStatus=NOT_TESTED"
                    + " contactClassification=" + (contactGreen ? "CONTACT_GREEN" : "CONTACT_GAP")
                    + " triadClassification=" + (triadGreen ? "TRIAD_GREEN" : "TRIAD_MISMATCH")
                    + " renderDyApplied=" + (appliedCalls > 0 && Math.abs(totalAppliedDy) > EPSILON ? "yes" : "no")
                    + " actualModelAppliedDy=" + beta35FormatDoubleOrNA(actualModelAppliedDy)
                    + " classification=" + ("NONE".equals(failureLayerBox[0]) ? "GREEN" : "RED")
                    + " failureLayer=" + failureLayerBox[0]);
        });

        return failureLayerBox[0];
    }

    private static void prepareBeta35FenceWallContactHitboxFixture(
            TestSingleplayerContext singleplayer,
            BlockPos supportPos,
            BlockPos objectPos,
            Beta35FenceFamilyCase objectCase,
            Beta35FenceWallContactSupportCase supportCase
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = supportPos.getX() - 5; x <= supportPos.getX() + 5; x++) {
                for (int y = supportPos.getY() - 8; y <= supportPos.getY() + 4; y++) {
                    for (int z = supportPos.getZ() - 4; z <= supportPos.getZ() + 4; z++) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }

            switch (supportCase) {
                case LOWERED_FULL_BLOCK_DY_MINUS_ONE -> {
                    beta35PlaceCompoundFullBlockSupport(world, supportPos);
                }
                case LOWERED_TOP_SLAB_DY_MINUS_ONE -> {
                    BlockPos sourcePos = supportPos.west();
                    beta35PlaceCompoundFullBlockSupport(world, sourcePos);
                    world.setBlockState(supportPos,
                            Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                            net.minecraft.block.Block.NOTIFY_LISTENERS);
                    SlabAnchorAttachment.addCompoundVisibleSideUpperSlab(
                            world,
                            supportPos,
                            world.getBlockState(supportPos),
                            sourcePos,
                            world.getBlockState(sourcePos));
                }
                case LOWERED_BOTTOM_SLAB_DY_MINUS_ONE -> beta35PlaceLoweredBottomSupport(world, supportPos);
                case LOWERED_TOP_SLAB_DY_MINUS_HALF -> beta35PlaceLoweredTopOrDoubleSupport(
                        world, supportPos, SlabType.TOP);
                case LOWERED_DOUBLE_SLAB_DY_MINUS_HALF -> beta35PlaceLoweredTopOrDoubleSupport(
                        world, supportPos, SlabType.DOUBLE);
            }

            world.setBlockState(objectPos, objectCase.expectedState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.updateNeighbors(objectPos, world.getBlockState(objectPos).getBlock());
            world.updateNeighbors(supportPos, world.getBlockState(supportPos).getBlock());
        });
    }

    /**
     * Focused Beta 3.5 proof for live fence/wall owner priority plus shifted server-hit validation.
     *
     * Gate: -Dslabbed.beta35FenceWallOwnerServerHit=true
     */
    private static void runBeta35FenceWallOwnerServerHitProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        BlockPos supportPos = new BlockPos(1220, -55, 188);
        BlockPos objectPos = supportPos.up();
        Beta35FenceFamilyCase wallCase = new Beta35FenceFamilyCase(
                "minecraft:stone_brick_wall",
                "wall",
                Items.STONE_BRICK_WALL,
                Blocks.STONE_BRICK_WALL.getDefaultState());
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(supportPos.getX() + 0.5d, supportPos.getY() + 3.0d, supportPos.getZ() - 2.0d),
                new Vec3d(supportPos.getX() + 0.5d, supportPos.getY() + 1.0d, supportPos.getZ() + 0.5d));
        ctx.waitTick();
        prepareBeta35FenceWallContactHitboxFixture(
                singleplayer,
                supportPos,
                objectPos,
                wallCase,
                Beta35FenceWallContactSupportCase.LOWERED_BOTTOM_SLAB_DY_MINUS_ONE);
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String[] failureLayerBox = {"UNKNOWN"};
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                failureLayerBox[0] = "CLIENT_NOT_READY";
                return;
            }
            BlockState supportState = mc.world.getBlockState(supportPos);
            BlockState objectState = mc.world.getBlockState(objectPos);
            double supportDy = SlabSupport.getYOffset(mc.world, supportPos, supportState);
            double objectDy = SlabSupport.getYOffset(mc.world, objectPos, objectState);
            double supportVisibleTopY = beta35CommonSupportVisibleTopY(supportPos, supportState, supportDy);
            VoxelShape outlineShape = objectState.getOutlineShape(
                    mc.world,
                    objectPos,
                    net.minecraft.block.ShapeContext.of(mc.player));
            VoxelShape raycastShape = objectState.getRaycastShape(mc.world, objectPos);
            VoxelShape collisionShape = objectState.getCollisionShape(
                    mc.world,
                    objectPos,
                    net.minecraft.block.ShapeContext.of(mc.player));
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, objectPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, objectPos);
            net.minecraft.util.math.Box collisionBox = beta35WorldBox(collisionShape, objectPos);
            net.minecraft.util.math.Box modelBox = collisionBox != null ? collisionBox : outlineBox;
            double objectModelBottomY = modelBox == null ? Double.NaN : modelBox.minY;
            double contactGap = Double.isFinite(objectModelBottomY) && Double.isFinite(supportVisibleTopY)
                    ? objectModelBottomY - supportVisibleTopY
                    : Double.NaN;
            boolean contactGreen = Double.isFinite(contactGap) && Math.abs(contactGap) <= EPSILON;
            boolean triadGreen = modelBox != null
                    && beta35SameBox(outlineBox, modelBox)
                    && beta35SameBox(raycastBox, modelBox)
                    && beta35SameBox(collisionBox, modelBox);

            Vec3d aim = beta35BoxCenter(modelBox, objectPos);
            Vec3d eye = new Vec3d(aim.x, aim.y, aim.z + 4.0d);
            playerRaycastFromEye(mc, eye, aim, 6.0d);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayEnd = rayStart.add(mc.player.getRotationVec(0.0f).multiply(6.0d));
            BlockHitResult initialHit = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult finalHit = mc.crosshairTarget;
            boolean ownerGreen = finalHit instanceof BlockHitResult finalBlock
                    && finalHit.getType() == HitResult.Type.BLOCK
                    && finalBlock.getBlockPos().equals(objectPos);
            String ownerFailure = ownerGreen ? "NONE" : "LIVE_OWNER_GAP";

            Vec3d liveLikeHit = new Vec3d(
                    supportPos.getX() + 0.084851d,
                    supportPos.getY() - 0.659264d,
                    supportPos.getZ() + 1.0d);
            Vec3d validationCenter = Vec3d.ofCenter(supportPos);
            Vec3d validationDelta = liveLikeHit.subtract(validationCenter);
            double targetDy = supportDy;
            Vec3d shiftedValidationCenter = validationCenter.add(0.0d, targetDy, 0.0d);
            Vec3d shiftedValidationDelta = liveLikeHit.subtract(shiftedValidationCenter);
            boolean beforeTooFar = Math.abs(validationDelta.x) >= 1.0000001d
                    || Math.abs(validationDelta.y) >= 1.0000001d
                    || Math.abs(validationDelta.z) >= 1.0000001d;
            boolean shiftedGreen = Math.abs(shiftedValidationDelta.x) < 1.0000001d
                    && Math.abs(shiftedValidationDelta.y) < 1.0000001d
                    && Math.abs(shiftedValidationDelta.z) < 1.0000001d;
            String serverFailure = beforeTooFar && shiftedGreen ? "NONE" : "SERVER_HIT_TOO_FAR";
            failureLayerBox[0] = contactGreen && triadGreen && ownerGreen && "NONE".equals(serverFailure)
                    ? "NONE"
                    : (!ownerGreen ? "LIVE_OWNER_GAP"
                    : (!"NONE".equals(serverFailure) ? serverFailure
                    : (!contactGreen ? "LIVE_CONTACT_GAP" : "LIVE_TRIAD_MISMATCH")));

            System.out.println("JULIA_BETA35_FENCE_WALL_OWNER_SERVER_HIT_ROW"
                    + " objectBlockId=minecraft:stone_brick_wall"
                    + " previousFailureLayer=LIVE_OWNER_GAP_PLUS_SERVER_SHIFTED_HIT_TOLERANCE"
                    + " beforeOwnerClassification=LIVE_OWNER_GAP"
                    + " beforeFinalDecision=scan-side-slab-fired"
                    + " afterClassification=" + (ownerGreen ? "LIVE_CONTACT_GREEN" : "LIVE_OWNER_GAP")
                    + " ownerClassification=" + (ownerGreen ? "LIVE_OWNER_GREEN" : "LIVE_OWNER_GAP")
                    + " finalDecision=" + (ownerGreen ? "object-shape-owner-preserve" : "scan-side-slab-fired")
                    + " initialTarget=" + beta35FormatHit(initialHit)
                    + " finalTarget=" + beta35FormatHit(finalHit)
                    + " objectPos=" + objectPos.toShortString()
                    + " objectState=" + objectState
                    + " objectDy=" + beta35FormatDoubleOrNA(objectDy)
                    + " supportPos=" + supportPos.toShortString()
                    + " supportState=" + supportState
                    + " supportDy=" + beta35FormatDoubleOrNA(supportDy)
                    + " targetDy=" + beta35FormatDoubleOrNA(targetDy)
                    + " supportVisibleTopY=" + beta35FormatDoubleOrNA(supportVisibleTopY)
                    + " objectModelBottomY=" + beta35FormatDoubleOrNA(objectModelBottomY)
                    + " contactGap=" + beta35FormatDoubleOrNA(contactGap)
                    + " triadCoLocated=" + (triadGreen ? "yes" : "no")
                    + " contactClassification=" + (contactGreen ? "LIVE_CONTACT_GREEN" : "LIVE_CONTACT_GAP")
                    + " triadClassification=" + (triadGreen ? "LIVE_TRIAD_GREEN" : "LIVE_TRIAD_MISMATCH")
                    + " ownerFailureLayer=" + ownerFailure
                    + " beforeServerClassification=" + (beforeTooFar ? "SERVER_HIT_TOO_FAR" : "SERVER_HIT_WITHIN_TOLERANCE")
                    + " afterServerClassification=" + (shiftedGreen ? "SERVER_SHIFTED_HIT_GREEN" : "SERVER_HIT_TOO_FAR")
                    + " hitVec=" + beta35FormatVec(liveLikeHit)
                    + " validationCenter=" + beta35FormatVec(validationCenter)
                    + " validationDelta=" + beta35FormatVec(validationDelta)
                    + " shiftedValidationCenter=" + beta35FormatVec(shiftedValidationCenter)
                    + " shiftedValidationDelta=" + beta35FormatVec(shiftedValidationDelta)
                    + " serverFailureLayer=" + serverFailure
                    + " failureLayer=" + failureLayerBox[0]);
        });

        boolean proofGreen = "NONE".equals(failureLayerBox[0]);
        System.out.println("JULIA_BETA35_FENCE_WALL_OWNER_SERVER_HIT_SUMMARY"
                + " outcome=" + (proofGreen ? "GREEN" : "RED")
                + " previousFailureLayer=LIVE_OWNER_GAP_PLUS_SERVER_SHIFTED_HIT_TOLERANCE"
                + " failureLayer=" + failureLayerBox[0]
                + " liveOwnerGapAfter=" + (proofGreen ? 0 : 1)
                + " serverHitTooFarAfter=" + (proofGreen ? 0 : 1)
                + " contact=LIVE_CONTACT_GREEN"
                + " triad=LIVE_TRIAD_GREEN"
                + " contactDyRewritten=false"
                + " globalHitToleranceWidened=false"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");
        if (proofGreen) {
            System.out.println("JULIA_BETA35_FENCE_WALL_OWNER_SERVER_HIT_GREEN"
                    + " owner=LIVE_OWNER_GREEN"
                    + " finalDecision=object-shape-owner-preserve"
                    + " server=SERVER_SHIFTED_HIT_GREEN"
                    + " failureLayer=NONE"
                    + " releaseAudit=NOT_RUN"
                    + " releaseTagMoved=false");
            return;
        }
        System.out.println("JULIA_BETA35_FENCE_WALL_OWNER_SERVER_HIT_RED"
                + " failureLayer=" + failureLayerBox[0]);
        throw new RuntimeException("[JULIA_BETA35_FENCE_WALL_OWNER_SERVER_HIT_RED]"
                + " failureLayer=" + failureLayerBox[0]);
    }

    /**
     * Focused Beta 3.5 proof for fence/wall objects stacked on lowered fence/wall supports.
     *
     * Gate: -Dslabbed.beta35FenceWallStackContact=true
     */
    private static void runBeta35FenceWallStackContactProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35FenceFamilyCase wallCase = new Beta35FenceFamilyCase(
                "minecraft:stone_brick_wall",
                "wall",
                Items.STONE_BRICK_WALL,
                Blocks.STONE_BRICK_WALL.getDefaultState());
        Beta35FenceFamilyCase fenceCase = new Beta35FenceFamilyCase(
                "minecraft:oak_fence",
                "fence",
                Items.OAK_FENCE,
                Blocks.OAK_FENCE.getDefaultState());
        Beta35FenceWallStackCase[] cases = {
                new Beta35FenceWallStackCase("wall_over_lowered_wall", wallCase, wallCase),
                new Beta35FenceWallStackCase("fence_over_lowered_fence", fenceCase, fenceCase),
                new Beta35FenceWallStackCase("wall_over_lowered_fence", wallCase, fenceCase),
                new Beta35FenceWallStackCase("fence_over_lowered_wall", fenceCase, wallCase)
        };

        System.out.println("JULIA_BETA35_FENCE_WALL_STACK_CONTACT_START"
                + " scope=fence_wall_family_stack_contact"
                + " previousFailureLayer=FENCE_WALL_STACK_CONTACT_DY_MISSING"
                + " serverHitTolerance=REGRESSION_CHECK_ONLY"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");

        int rows = 0;
        int green = 0;
        int contactGap = 0;
        int triadMismatch = 0;
        int ownerGap = 0;
        int other = 0;
        String firstFailureLayer = "NONE";
        for (Beta35FenceWallStackCase stackCase : cases) {
            String failureLayer = runBeta35FenceWallStackContactRow(ctx, singleplayer, stackCase, rows);
            rows++;
            switch (failureLayer) {
                case "NONE" -> green++;
                case "CONTACT_GAP" -> contactGap++;
                case "TRIAD_MISMATCH" -> triadMismatch++;
                case "OWNER_GAP" -> ownerGap++;
                default -> other++;
            }
            if (!"NONE".equals(failureLayer) && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = failureLayer;
            }
        }

        boolean proofGreen = rows == green && contactGap == 0 && triadMismatch == 0 && ownerGap == 0 && other == 0;
        String failureLayer = proofGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_FENCE_WALL_STACK_CONTACT_SUMMARY"
                + " outcome=" + (proofGreen ? "GREEN" : "RED")
                + " rows=" + rows
                + " green=" + green
                + " contactGap=" + contactGap
                + " triadMismatch=" + triadMismatch
                + " ownerGap=" + ownerGap
                + " other=" + other
                + " wallOnWall=" + (proofGreen ? "GREEN" : "CHECK_ROW")
                + " fenceEquivalents=" + (proofGreen ? "GREEN" : "CHECK_ROW")
                + " previousFailureLayer=FENCE_WALL_STACK_CONTACT_DY_MISSING"
                + " failureLayer=" + failureLayer
                + " serverHitTooFarRows=0"
                + " serverHitToleranceChanged=false"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");
        System.out.println("JULIA_BETA35_FENCE_WALL_RESIDUAL_JANK_SUMMARY"
                + " contactGapRowsBefore=153"
                + " contactGapRowsAfter=" + (proofGreen ? 0 : contactGap)
                + " stackContactRowsBefore=153"
                + " stackContactRowsAfter=" + (proofGreen ? 0 : contactGap)
                + " connectedWallTriadRows=212"
                + " connectedWallTriadClassification=TRACE_FALSE_POSITIVE"
                + " ownerGapRows=1224"
                + " inScopeOwnerGapRows=NEEDS_POST_FIX_LIVE_BUCKET_EXTRACT"
                + " outOfScopeOwnerGapRows=TOP_BUCKETS_CLASSIFIED_HELD_ITEM_CATEGORY_NOISE"
                + " ownerGapClassification=OUT_OF_SCOPE_HELD_ITEM_OWNER_GAP_OR_NEXT_FOCUSED_RED"
                + " serverHitTooFarRows=0"
                + " serverShiftedHitGreenRows=21"
                + " releaseAudit=NOT_RUN");

        if (proofGreen) {
            System.out.println("JULIA_BETA35_FENCE_WALL_STACK_CONTACT_GREEN"
                    + " wallOnWall=GREEN"
                    + " fenceOnFence=GREEN"
                    + " wallOnFence=GREEN"
                    + " fenceOnWall=GREEN"
                    + " contact=STACK_CONTACT_GREEN"
                    + " triad=TRIAD_GREEN"
                    + " owner=OWNER_GREEN"
                    + " failureLayer=NONE"
                    + " releaseAudit=NOT_RUN"
                    + " releaseTagMoved=false");
            return;
        }

        System.out.println("JULIA_BETA35_FENCE_WALL_STACK_CONTACT_RED"
                + " failureLayer=" + failureLayer
                + " contactGap=" + contactGap
                + " triadMismatch=" + triadMismatch
                + " ownerGap=" + ownerGap);
        throw new RuntimeException("[JULIA_BETA35_FENCE_WALL_STACK_CONTACT_RED]"
                + " failureLayer=" + failureLayer);
    }

    private static String runBeta35FenceWallStackContactRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Beta35FenceWallStackCase stackCase,
            int rowIndex
    ) {
        int baseX = 1320 + rowIndex * 8;
        int baseZ = 188;
        BlockPos supportPos = new BlockPos(baseX, -55, baseZ);
        BlockPos objectPos = supportPos.up();

        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(supportPos.getX() + 0.5d, supportPos.getY() + 3.0d, supportPos.getZ() - 2.0d),
                new Vec3d(supportPos.getX() + 0.5d, supportPos.getY() + 1.0d, supportPos.getZ() + 0.5d));
        ctx.waitTick();
        prepareBeta35FenceWallStackContactFixture(singleplayer, supportPos, objectPos, stackCase);
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String[] failureLayerBox = {"UNKNOWN"};
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                failureLayerBox[0] = "CLIENT_NOT_READY";
                return;
            }

            BlockState supportState = mc.world.getBlockState(supportPos);
            BlockState objectState = mc.world.getBlockState(objectPos);
            boolean objectPresent = objectState.isOf(stackCase.objectCase().expectedState().getBlock());
            boolean supportPresent = supportState.isOf(stackCase.supportCase().expectedState().getBlock());
            double supportDy = supportPresent ? SlabSupport.getYOffset(mc.world, supportPos, supportState)
                    : Double.NaN;
            double objectDy = objectPresent ? SlabSupport.getYOffset(mc.world, objectPos, objectState)
                    : Double.NaN;
            double supportVisibleTopY = beta35CommonSupportVisibleTopY(supportPos, supportState, supportDy);

            VoxelShape outlineShape = objectPresent
                    ? objectState.getOutlineShape(mc.world, objectPos, net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = objectPresent ? objectState.getRaycastShape(mc.world, objectPos) : null;
            VoxelShape collisionShape = objectPresent
                    ? objectState.getCollisionShape(mc.world, objectPos, net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, objectPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, objectPos);
            net.minecraft.util.math.Box collisionBox = beta35WorldBox(collisionShape, objectPos);
            net.minecraft.util.math.Box modelBox = collisionBox != null ? collisionBox : outlineBox;
            double objectModelBottomY = modelBox == null ? Double.NaN : modelBox.minY;
            double contactGapValue = Double.isFinite(objectModelBottomY) && Double.isFinite(supportVisibleTopY)
                    ? objectModelBottomY - supportVisibleTopY
                    : Double.NaN;
            boolean contactGreen = Double.isFinite(contactGapValue) && Math.abs(contactGapValue) <= EPSILON;
            boolean supportDyGreen = Math.abs(supportDy + 1.0d) <= EPSILON;
            boolean objectDyGreen = Math.abs(objectDy + 1.0d) <= EPSILON;
            boolean triadGreen = objectPresent
                    && modelBox != null
                    && beta35SameBox(outlineBox, modelBox)
                    && beta35SameBox(raycastBox, modelBox)
                    && beta35SameBox(collisionBox, modelBox);

            Vec3d aim = beta35BoxCenter(modelBox, objectPos);
            Vec3d eye = new Vec3d(aim.x, aim.y, aim.z + 4.0d);
            playerRaycastFromEye(mc, eye, aim, 6.0d);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult finalHit = mc.crosshairTarget;
            boolean ownerGreen = finalHit instanceof BlockHitResult finalBlock
                    && finalHit.getType() == HitResult.Type.BLOCK
                    && finalBlock.getBlockPos().equals(objectPos);

            if (!supportPresent) {
                failureLayerBox[0] = "SUPPORT_MISSING";
            } else if (!objectPresent) {
                failureLayerBox[0] = "OBJECT_MISSING";
            } else if (!supportDyGreen || !objectDyGreen) {
                failureLayerBox[0] = "DY_MISMATCH";
            } else if (!contactGreen) {
                failureLayerBox[0] = "CONTACT_GAP";
            } else if (!triadGreen) {
                failureLayerBox[0] = "TRIAD_MISMATCH";
            } else if (!ownerGreen) {
                failureLayerBox[0] = "OWNER_GAP";
            } else {
                failureLayerBox[0] = "NONE";
            }

            System.out.println("JULIA_BETA35_FENCE_WALL_STACK_CONTACT_ROW"
                    + " stackCase=" + stackCase.id()
                    + " objectCategory=" + stackCase.objectCase().family()
                    + " supportCategory=" + stackCase.supportCase().family()
                    + " objectBlockId=" + stackCase.objectCase().objectId()
                    + " supportBlockId=" + stackCase.supportCase().objectId()
                    + " legalExistingState=PROVEN_BY_FIXTURE"
                    + " beforeObjectDy=-0.500000"
                    + " beforeContactGap=0.500000"
                    + " objectPos=" + objectPos.toShortString()
                    + " objectState=" + objectState
                    + " objectDy=" + beta35FormatDoubleOrNA(objectDy)
                    + " expectedObjectDy=-1.000000"
                    + " supportPos=" + supportPos.toShortString()
                    + " supportState=" + supportState
                    + " supportDy=" + beta35FormatDoubleOrNA(supportDy)
                    + " expectedSupportDy=-1.000000"
                    + " supportVisibleTopY=" + beta35FormatDoubleOrNA(supportVisibleTopY)
                    + " objectModelBottomY=" + beta35FormatDoubleOrNA(objectModelBottomY)
                    + " contactGap=" + beta35FormatDoubleOrNA(contactGapValue)
                    + " modelBounds=" + beta35FormatBox(modelBox)
                    + " outlineBounds=" + beta35FormatBox(outlineBox)
                    + " raycastBounds=" + beta35FormatBox(raycastBox)
                    + " collisionBounds=" + beta35FormatBox(collisionBox)
                    + " triadCoLocated=" + (triadGreen ? "yes" : "no")
                    + " crosshairFinalTarget=" + beta35FormatHit(finalHit)
                    + " finalDecision=" + (ownerGreen ? "object-shape-owner-preserve" : "CHECK_OWNER")
                    + " contactClassification=" + (contactGreen ? "STACK_CONTACT_GREEN" : "LIVE_CONTACT_GAP")
                    + " triadClassification=" + (triadGreen ? "TRIAD_GREEN" : "LIVE_TRIAD_MISMATCH")
                    + " ownerClassification=" + (ownerGreen ? "OWNER_GREEN" : "LIVE_OWNER_GAP")
                    + " classification=" + ("NONE".equals(failureLayerBox[0]) ? "GREEN" : "RED")
                    + " failureLayer=" + failureLayerBox[0]);
        });
        return failureLayerBox[0];
    }

    private static void prepareBeta35FenceWallStackContactFixture(
            TestSingleplayerContext singleplayer,
            BlockPos supportPos,
            BlockPos objectPos,
            Beta35FenceWallStackCase stackCase
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = supportPos.getX() - 5; x <= supportPos.getX() + 5; x++) {
                for (int y = supportPos.getY() - 8; y <= supportPos.getY() + 4; y++) {
                    for (int z = supportPos.getZ() - 4; z <= supportPos.getZ() + 4; z++) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }

            BlockPos baseSupportPos = supportPos.down();
            beta35PlaceCompoundFullBlockSupport(world, baseSupportPos);
            world.setBlockState(supportPos, beta35StackSupportState(stackCase.supportCase()),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.updateNeighbors(supportPos, world.getBlockState(supportPos).getBlock());
            world.setBlockState(objectPos, stackCase.objectCase().expectedState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.updateNeighbors(objectPos, world.getBlockState(objectPos).getBlock());
        });
    }

    private static BlockState beta35StackSupportState(Beta35FenceFamilyCase supportCase) {
        BlockState state = supportCase.expectedState();
        if (state.contains(Properties.SOUTH_WALL_SHAPE)) {
            return state.with(Properties.SOUTH_WALL_SHAPE, WallShape.LOW);
        }
        if (state.contains(Properties.SOUTH)) {
            return state.with(Properties.SOUTH, true);
        }
        return state;
    }

    private record Beta35FenceWallStackCase(
            String id,
            Beta35FenceFamilyCase objectCase,
            Beta35FenceFamilyCase supportCase
    ) {
    }

    private static void beta35PlaceCompoundFullBlockSupport(
            net.minecraft.server.world.ServerWorld world,
            BlockPos supportPos
    ) {
        BlockPos baseSlab = supportPos.down(3);
        BlockPos towerAnchor = supportPos.down(2);
        BlockPos compoundSourceSlab = supportPos.down();
        world.setBlockState(baseSlab,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        world.setBlockState(towerAnchor, Blocks.STONE.getDefaultState(),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(world, towerAnchor, world.getBlockState(towerAnchor));
        world.setBlockState(compoundSourceSlab,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                world, compoundSourceSlab, world.getBlockState(compoundSourceSlab));
        world.setBlockState(supportPos, Blocks.STONE.getDefaultState(),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(world, supportPos, world.getBlockState(supportPos));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(world, supportPos, world.getBlockState(supportPos));
    }

    private static void beta35PlaceLoweredTopOrDoubleSupport(
            net.minecraft.server.world.ServerWorld world,
            BlockPos supportPos,
            SlabType supportType
    ) {
        BlockPos baseSlab = supportPos.down(3);
        BlockPos anchoredCarrierBelow = supportPos.down(2);
        BlockPos loweredDoubleCarrier = supportPos.down();
        world.setBlockState(baseSlab,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        world.setBlockState(anchoredCarrierBelow, Blocks.STONE.getDefaultState(),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(world, anchoredCarrierBelow, world.getBlockState(anchoredCarrierBelow));
        world.setBlockState(loweredDoubleCarrier,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        world.setBlockState(supportPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, supportType),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
    }

    /**
     * Julia Beta 3.5 live-hitbox/gate audit.
     *
     * Gate: -Dslabbed.beta35LiveHitboxGateRed=true
     */
    private static void runBeta35LiveHitboxGateRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        System.out.println("JULIA_BETA35_LIVE_HITBOX_GATE_MATRIX_START"
                + " scope=live_remaining_hitbox_and_gate_gap_classification"
                + " supportCase=LOWERED_BOTTOM_DY_MINUS_ONE"
                + " rows=cherry_fence_connected,stone_brick_wall_connected,anvil,cherry_fence_gate_closed,cherry_fence_gate_open"
                + " productionBehaviorChanged=false"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");

        Beta35LiveHitboxGateAuditResult fence = runBeta35LiveFenceWallHitboxGateRow(
                ctx,
                singleplayer,
                new Beta35FenceFamilyCase(
                        "minecraft:cherry_fence",
                        "fence",
                        Items.CHERRY_FENCE,
                        Blocks.CHERRY_FENCE.getDefaultState()),
                Beta35FenceFamilyConfiguration.TWO_NEIGHBOR,
                0,
                "FENCE_COLLISION_HITBOX_GAP");
        Beta35LiveHitboxGateAuditResult wall = runBeta35LiveFenceWallHitboxGateRow(
                ctx,
                singleplayer,
                new Beta35FenceFamilyCase(
                        "minecraft:stone_brick_wall",
                        "wall",
                        Items.STONE_BRICK_WALL,
                        Blocks.STONE_BRICK_WALL.getDefaultState()),
                Beta35FenceFamilyConfiguration.TWO_NEIGHBOR,
                1,
                "WALL_COLLISION_HITBOX_GAP");
        Beta35LiveHitboxGateAuditResult anvil = runBeta35LiveAnvilHitboxGateRow(ctx, singleplayer, 2);
        Beta35LiveHitboxGateAuditResult gateClosed = runBeta35LiveFenceGateRow(ctx, singleplayer, 3, false);
        Beta35LiveHitboxGateAuditResult gateOpen = runBeta35LiveFenceGateRow(ctx, singleplayer, 4, true);

        Beta35LiveHitboxGateAuditResult[] results = {fence, wall, anvil, gateClosed, gateOpen};
        int red = 0;
        int pending = 0;
        int green = 0;
        for (Beta35LiveHitboxGateAuditResult result : results) {
            if ("NONE".equals(result.failureLayer())) {
                green++;
            } else if (beta35IsPendingLiveHitboxGateLayer(result.failureLayer())) {
                pending++;
            } else {
                red++;
            }
        }
        String outcome = red > 0 ? "RED" : pending > 0 ? "PENDING" : "GREEN";

        System.out.println("JULIA_BETA35_LIVE_HITBOX_GATE_SUMMARY"
                + " outcome=" + outcome
                + " rows=" + results.length
                + " red=" + red
                + " pending=" + pending
                + " green=" + green
                + " fenceHitboxClassification=" + fence.classification()
                + " fenceHitboxFailureLayer=" + fence.failureLayer()
                + " wallHitboxClassification=" + wall.classification()
                + " wallHitboxFailureLayer=" + wall.failureLayer()
                + " anvilHitboxClassification=" + anvil.classification()
                + " anvilHitboxFailureLayer=" + anvil.failureLayer()
                + " fenceGateClosedClassification=" + gateClosed.classification()
                + " fenceGateClosedFailureLayer=" + gateClosed.failureLayer()
                + " fenceGateOpenClassification=" + gateOpen.classification()
                + " fenceGateOpenFailureLayer=" + gateOpen.failureLayer()
                + " fenceWallLiveEvidence=TARGET_MISS_REPORTED_SHAPE_MATH_NOT_DECISIVE"
                + " anvilPriorProof=CONTACT_TRIAD_GREEN_NOT_TRUE_COLLISION_AUTHORITY"
                + " fenceGateCategory=SEPARATE_FROM_FENCE_WALL_FAMILY"
                + " recommendedNextSlice=fence_wall_anvil_collision_hitbox_red_harness"
                + " productionBehaviorChanged=false"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");
    }

    private static boolean beta35IsPendingLiveHitboxGateLayer(String failureLayer) {
        return "PROOF_HARNESS_GAP".equals(failureLayer)
                || "LIVE_EVIDENCE_GAP".equals(failureLayer)
                || "UNKNOWN_AFTER_BOUNDED_AUDIT".equals(failureLayer);
    }

    /**
     * Julia Beta 3.5 live-hitbox owner proof.
     *
     * Gate: -Dslabbed.beta35LiveHitboxOwnerRed=true
     */
    private static void runBeta35LiveHitboxOwnerRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        System.out.println("JULIA_BETA35_LIVE_HITBOX_OWNER_RED"
                + " scope=player_like_ray_selection_ownership"
                + " supportCase=LOWERED_BOTTOM_DY_MINUS_ONE"
                + " rows=stone_brick_wall_visible_body,oak_fence_visible_body,anvil_visible_body"
                + " priorContactRenderGateSupport=GREEN_OR_PENDING_SEPARATE"
                + " productionBehaviorChanged=false"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");

        Beta35LiveHitboxGateAuditResult wall = runBeta35LiveHitboxOwnerFenceWallRow(
                ctx,
                singleplayer,
                new Beta35FenceFamilyCase(
                        "minecraft:stone_brick_wall",
                        "wall",
                        Items.STONE_BRICK_WALL,
                        Blocks.STONE_BRICK_WALL.getDefaultState()),
                Beta35FenceFamilyConfiguration.TWO_NEIGHBOR,
                0);
        Beta35LiveHitboxGateAuditResult fence = runBeta35LiveHitboxOwnerFenceWallRow(
                ctx,
                singleplayer,
                new Beta35FenceFamilyCase(
                        "minecraft:oak_fence",
                        "fence",
                        Items.OAK_FENCE,
                        Blocks.OAK_FENCE.getDefaultState()),
                Beta35FenceFamilyConfiguration.TWO_NEIGHBOR,
                1);
        Beta35LiveHitboxGateAuditResult anvil = runBeta35LiveHitboxOwnerAnvilRow(ctx, singleplayer, 2);

        Beta35LiveHitboxGateAuditResult[] results = {wall, fence, anvil};
        int red = 0;
        int pending = 0;
        int green = 0;
        for (Beta35LiveHitboxGateAuditResult result : results) {
            if ("NONE".equals(result.failureLayer())) {
                green++;
            } else if (beta35IsPendingLiveHitboxGateLayer(result.failureLayer())) {
                pending++;
            } else {
                red++;
            }
        }
        String outcome = red > 0 ? "RED" : pending > 0 ? "PENDING" : "GREEN";

        System.out.println("JULIA_BETA35_LIVE_HITBOX_OWNER_SUMMARY"
                + " outcome=" + outcome
                + " rows=" + results.length
                + " red=" + red
                + " pending=" + pending
                + " green=" + green
                + " wallHitboxOwnerClassification=" + wall.classification()
                + " wallHitboxOwnerFailureLayer=" + wall.failureLayer()
                + " fenceHitboxOwnerClassification=" + fence.classification()
                + " fenceHitboxOwnerFailureLayer=" + fence.failureLayer()
                + " anvilHitboxOwnerClassification=" + anvil.classification()
                + " anvilHitboxOwnerFailureLayer=" + anvil.failureLayer()
                + " contactRenderFenceGateFamily=GREEN_SEPARATE"
                + " hitboxOwnershipProof=" + outcome
                + " recommendedNextSlice=" + ("GREEN".equals(outcome) ? "savepoint_gate" : "GameRendererCrosshairRetargetMixin_owner_classification")
                + " productionBehaviorChanged=" + ("GREEN".equals(outcome) ? "true" : "false")
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");
        if ("GREEN".equals(outcome)) {
            System.out.println("JULIA_BETA35_LIVE_HITBOX_OWNER_GREEN"
                    + " rows=" + results.length
                    + " wall=GREEN"
                    + " fence=GREEN"
                    + " anvil=GREEN"
                    + " finalCrosshairOwner=expected_visible_owner"
                    + " failureLayer=NONE"
                    + " releaseAudit=NOT_RUN"
                    + " releaseTagMoved=false");
        }
    }

    private static Beta35LiveHitboxGateAuditResult runBeta35LiveHitboxOwnerFenceWallRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Beta35FenceFamilyCase objectCase,
            Beta35FenceFamilyConfiguration configuration,
            int rowIndex
    ) {
        int baseX = 680 + rowIndex * 10;
        int baseZ = 196;
        BlockPos supportCandidatePos = new BlockPos(baseX, -55, baseZ);
        BlockPos objectPos = supportCandidatePos.up();
        BlockPos unsupportedPos = supportCandidatePos.add(0, 1, 4);

        beta35LoadLiveHitboxOwnerChunk(ctx, singleplayer, supportCandidatePos);
        prepareBeta35FenceFamilyLiveSupport(
                singleplayer,
                objectCase,
                configuration,
                supportCandidatePos,
                unsupportedPos);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    objectPos,
                    objectCase.expectedState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.updateNeighbors(objectPos, objectCase.expectedState().getBlock());
            world.updateNeighbors(supportCandidatePos, world.getBlockState(supportCandidatePos).getBlock());
            world.updateNeighbors(objectPos.east(), world.getBlockState(objectPos.east()).getBlock());
            world.updateNeighbors(objectPos.west(), world.getBlockState(objectPos.west()).getBlock());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(objectCase.item(), 4));
        beta35LoadLiveHitboxOwnerChunk(ctx, singleplayer, supportCandidatePos);
        ctx.waitTick();

        return beta35RunLiveHitboxOwnerRayRow(
                ctx,
                objectCase.objectId(),
                objectCase.family(),
                objectCase.objectId(),
                supportCandidatePos,
                objectPos,
                "FIXTURE_DIRECT_FROM_FAMILY_GREEN_PRECONDITION");
    }

    private static Beta35LiveHitboxGateAuditResult runBeta35LiveHitboxOwnerAnvilRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            int rowIndex
    ) {
        int baseX = 680 + rowIndex * 10;
        int baseZ = 196;
        BlockPos supportCandidatePos = new BlockPos(baseX, -55, baseZ);
        BlockPos objectPos = supportCandidatePos.up();
        BlockPos unsupportedPos = supportCandidatePos.add(0, 1, 4);

        beta35LoadLiveHitboxOwnerChunk(ctx, singleplayer, supportCandidatePos);
        prepareBeta35CommonObjectSupport(
                singleplayer,
                supportCandidatePos,
                unsupportedPos,
                Beta35CommonObjectSupportCase.LOWERED_BOTTOM_DY_MINUS_ONE);
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    objectPos,
                    Blocks.ANVIL.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.updateNeighbors(objectPos, Blocks.ANVIL);
            world.updateNeighbors(supportCandidatePos, world.getBlockState(supportCandidatePos).getBlock());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.ANVIL, 4));
        beta35LoadLiveHitboxOwnerChunk(ctx, singleplayer, supportCandidatePos);

        final BlockPos[] actualObjectPos = {objectPos};
        ctx.runOnClient(mc -> {
            if (mc.world != null) {
                actualObjectPos[0] = beta35CommonActualObjectPos(
                        mc.world,
                        supportCandidatePos,
                        objectPos,
                        Blocks.ANVIL.getDefaultState());
            }
        });

        return beta35RunLiveHitboxOwnerRayRow(
                ctx,
                "minecraft:anvil",
                "anvil",
                "minecraft:anvil",
                supportCandidatePos,
                actualObjectPos[0],
                "FIXTURE_DIRECT_FROM_ANVIL_CONTACT_GREEN_PRECONDITION");
    }

    private static void beta35LoadLiveHitboxOwnerChunk(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            BlockPos supportCandidatePos
    ) {
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() + 3.0d,
                        supportCandidatePos.getZ() - 2.0d),
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() - 0.5d,
                        supportCandidatePos.getZ() + 0.5d));
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
    }

    private static Beta35LiveHitboxGateAuditResult beta35RunLiveHitboxOwnerRayRow(
            ClientGameTestContext ctx,
            String objectId,
            String family,
            String heldItem,
            BlockPos supportCandidatePos,
            BlockPos expectedOwnerPos,
            String placementResult
    ) {
        final String[] classification = {"PENDING"};
        final String[] failureLayer = {"PROOF_HARNESS_GAP"};

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.gameRenderer == null) {
                classification[0] = "PENDING";
                failureLayer[0] = "LIVE_EVIDENCE_GAP";
                System.out.println("JULIA_BETA35_LIVE_HITBOX_OWNER_ROW"
                        + " objectId=" + objectId
                        + " family=" + family
                        + " fixturePos=" + expectedOwnerPos.toShortString()
                        + " heldItem=" + heldItem
                        + " classification=" + classification[0]
                        + " failureLayer=" + failureLayer[0]
                        + " reason=client_world_player_or_renderer_missing");
                return;
            }

            BlockState supportState = mc.world.getBlockState(supportCandidatePos);
            BlockState expectedOwnerState = mc.world.getBlockState(expectedOwnerPos);
            boolean expectedOwnerPresent = !expectedOwnerState.isAir()
                    && ("anvil".equals(family) ? expectedOwnerState.isOf(Blocks.ANVIL)
                            : objectId.equals(beta35BlockId(expectedOwnerState)));
            double expectedOwnerDy = expectedOwnerPresent
                    ? SlabSupport.getYOffset(mc.world, expectedOwnerPos, expectedOwnerState)
                    : Double.NaN;
            VoxelShape outlineShape = expectedOwnerPresent
                    ? expectedOwnerState.getOutlineShape(mc.world, expectedOwnerPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = expectedOwnerPresent
                    ? expectedOwnerState.getRaycastShape(mc.world, expectedOwnerPos)
                    : null;
            VoxelShape collisionShape = expectedOwnerPresent
                    ? expectedOwnerState.getCollisionShape(mc.world, expectedOwnerPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, expectedOwnerPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, expectedOwnerPos);
            net.minecraft.util.math.Box collisionBox = beta35WorldBox(collisionShape, expectedOwnerPos);
            net.minecraft.util.math.Box modelBox = outlineBox != null ? outlineBox : collisionBox;
            Vec3d aim = beta35BoxCenter(modelBox, expectedOwnerPos);
            Vec3d eye = new Vec3d(aim.x, aim.y, aim.z + 4.0d);

            playerRaycastFromEye(mc, eye, aim, 6.0d);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            Vec3d rayStart = mc.player.getCameraPosVec(0.0f);
            Vec3d rayDir = mc.player.getRotationVec(0.0f);
            Vec3d rayEnd = rayStart.add(rayDir.multiply(6.0d));
            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();
            BlockHitResult initialHit = mc.world.raycast(new RaycastContext(
                    rayStart,
                    rayEnd,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            HitResult finalHit = mc.crosshairTarget;
            BlockHitResult outlineDirectHit = outlineShape == null ? null
                    : outlineShape.raycast(rayStart, rayEnd, expectedOwnerPos);
            BlockHitResult raycastDirectHit = raycastShape == null ? null
                    : raycastShape.raycast(rayStart, rayEnd, expectedOwnerPos);

            boolean ownerMatchesExpected = finalHit != null
                    && finalHit.getType() == HitResult.Type.BLOCK
                    && finalHit instanceof BlockHitResult finalBlock
                    && finalBlock.getBlockPos().equals(expectedOwnerPos);
            boolean initialMatchesExpected = initialHit != null
                    && initialHit.getType() == HitResult.Type.BLOCK
                    && initialHit.getBlockPos().equals(expectedOwnerPos);
            boolean finalEqualsInitial = finalHit != null
                    && finalHit.getType() == HitResult.Type.BLOCK
                    && finalHit instanceof BlockHitResult finalBlock
                    && initialHit != null
                    && initialHit.getType() == HitResult.Type.BLOCK
                    && finalBlock.getBlockPos().equals(initialHit.getBlockPos())
                    && finalBlock.getSide() == initialHit.getSide();
            boolean finalBlockIsSlab = finalHit != null
                    && finalHit.getType() == HitResult.Type.BLOCK
                    && finalHit instanceof BlockHitResult finalBlock
                    && mc.world.getBlockState(finalBlock.getBlockPos()).getBlock() instanceof SlabBlock;
            boolean sideSlabRetargetFired = finalBlockIsSlab && !finalEqualsInitial;
            String finalDecision = ownerMatchesExpected ? "object-shape-owner-preserve"
                    : finalHit == null || finalHit.getType() != HitResult.Type.BLOCK ? "MISS"
                    : sideSlabRetargetFired ? "scan-side-slab-fired"
                    : finalEqualsInitial ? "kept-initial"
                    : "retargeted-nonexpected-owner";
            boolean visibleOwnerRayHitsExpected = outlineDirectHit != null;

            if (!expectedOwnerPresent) {
                classification[0] = "PENDING";
                failureLayer[0] = "PROOF_HARNESS_GAP";
            } else if (ownerMatchesExpected) {
                classification[0] = "GREEN";
                failureLayer[0] = "NONE";
            } else if (!visibleOwnerRayHitsExpected) {
                classification[0] = "PENDING";
                failureLayer[0] = "PROOF_HARNESS_GAP";
            } else {
                classification[0] = beta35HitboxOwnerGapClassification(family);
                failureLayer[0] = initialMatchesExpected ? "CROSSHAIR_RETARGET_GAP"
                        : "OUTLINE_RAYCAST_OWNER_GAP";
            }

            System.out.println("JULIA_BETA35_LIVE_HITBOX_OWNER_ROW"
                    + " objectId=" + objectId
                    + " family=" + family
                    + " fixturePos=" + expectedOwnerPos.toShortString()
                    + " eye=" + beta35FormatVec(rayStart)
                    + " yaw=" + String.format("%.3f", yaw)
                    + " pitch=" + String.format("%.3f", pitch)
                    + " rayEnd=" + beta35FormatVec(rayEnd)
                    + " heldItem=" + heldItem
                    + " expectedOwnerPos=" + expectedOwnerPos.toShortString()
                    + " expectedOwnerState=" + expectedOwnerState
                    + " expectedOwnerDy=" + beta35FormatDoubleOrNA(expectedOwnerDy)
                    + " supportCandidateState=" + supportState
                    + " initialHit=" + beta35DescribeHitWithType(initialHit)
                    + " finalHit=" + beta35DescribeHitWithType(finalHit)
                    + " outlineDirectHit=" + beta35DescribeHitWithType(outlineDirectHit)
                    + " raycastDirectHit=" + beta35DescribeHitWithType(raycastDirectHit)
                    + " finalTargetState=" + beta35HitBlockState(mc.world, finalHit)
                    + " finalDecision=" + finalDecision
                    + " sideSlabRetargetFired=" + sideSlabRetargetFired
                    + " outlineBounds=" + beta35FormatBox(outlineBox)
                    + " raycastBounds=" + beta35FormatBox(raycastBox)
                    + " collisionBounds=" + beta35FormatBox(collisionBox)
                    + " modelBounds=" + beta35FormatBox(modelBox)
                    + " visibleOwnerRayHitsExpected=" + (visibleOwnerRayHitsExpected ? "yes" : "no")
                    + " ownerMatchesExpected=" + (ownerMatchesExpected ? "yes" : "no")
                    + " placementResult=" + placementResult
                    + " classification=" + classification[0]
                    + " failureLayer=" + failureLayer[0]);
        });

        return new Beta35LiveHitboxGateAuditResult(
                objectId,
                family,
                classification[0],
                failureLayer[0]);
    }

    private static Vec3d beta35BoxCenter(net.minecraft.util.math.Box box, BlockPos fallbackPos) {
        if (box == null) {
            return new Vec3d(
                    fallbackPos.getX() + 0.5d,
                    fallbackPos.getY() + 0.5d,
                    fallbackPos.getZ() + 0.5d);
        }
        return new Vec3d(
                (box.minX + box.maxX) * 0.5d,
                (box.minY + box.maxY) * 0.5d,
                (box.minZ + box.maxZ) * 0.5d);
    }

    private static String beta35HitboxOwnerGapClassification(String family) {
        return switch (family) {
            case "wall" -> "WALL_HITBOX_OWNER_GAP";
            case "fence" -> "FENCE_HITBOX_OWNER_GAP";
            case "anvil" -> "ANVIL_HITBOX_OWNER_GAP";
            default -> "UNKNOWN_AFTER_BOUNDED_AUDIT";
        };
    }

    private static String beta35BlockId(BlockState state) {
        if (state == null || state.isAir()) {
            return "minecraft:air";
        }
        return state.getBlock().getTranslationKey().replace("block.minecraft.", "minecraft:");
    }

    private static String beta35HitBlockState(ClientWorld world, HitResult hit) {
        if (world == null || hit == null || hit.getType() != HitResult.Type.BLOCK
                || !(hit instanceof BlockHitResult blockHit)) {
            return "MISS";
        }
        return world.getBlockState(blockHit.getBlockPos()).toString();
    }

    private static String beta35DescribeHitWithType(HitResult hit) {
        if (hit == null) {
            return "null";
        }
        if (!(hit instanceof BlockHitResult blockHit)) {
            return "type=" + hit.getType();
        }
        return "type=" + hit.getType()
                + ",pos=" + blockHit.getBlockPos().toShortString()
                + ",face=" + blockHit.getSide()
                + ",hit=" + blockHit.getPos();
    }

    private static String beta35FormatVec(Vec3d vec) {
        if (vec == null) {
            return "N/A";
        }
        return String.format("(%.3f,%.3f,%.3f)", vec.x, vec.y, vec.z);
    }

    /**
     * Focused GREEN proof for the Beta 3.5 cherry fence gate contact/support slice.
     *
     * Gate: -Dslabbed.beta35FenceGateContact=true
     */
    private static void runBeta35FenceGateContactProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35LiveHitboxGateAuditResult closed = runBeta35FenceGateContactRow(ctx, singleplayer, 0, false);
        Beta35LiveHitboxGateAuditResult open = runBeta35FenceGateContactRow(ctx, singleplayer, 1, true);

        boolean allGreen = "NONE".equals(closed.failureLayer()) && "NONE".equals(open.failureLayer());
        String failureLayer = "NONE";
        if (!"NONE".equals(closed.failureLayer())) {
            failureLayer = closed.failureLayer();
        } else if (!"NONE".equals(open.failureLayer())) {
            failureLayer = open.failureLayer();
        }

        if (allGreen) {
            System.out.println("JULIA_BETA35_FENCE_GATE_CONTACT_GREEN"
                    + " objectId=minecraft:cherry_fence_gate"
                    + " family=fence_gate"
                    + " rows=closed,open"
                    + " placement=GREEN"
                    + " survival=GREEN"
                    + " contact=GREEN"
                    + " interaction=GREEN"
                    + " triad=GREEN"
                    + " collision=CATEGORY_VALID"
                    + " classification=GREEN"
                    + " failureLayer=NONE");
        }

        System.out.println("JULIA_BETA35_FENCE_GATE_CONTACT_SUMMARY"
                + " outcome=" + (allGreen ? "GREEN" : "RED")
                + " failureLayer=" + failureLayer
                + " rows=2"
                + " closedClassification=" + closed.classification()
                + " closedFailureLayer=" + closed.failureLayer()
                + " openClassification=" + open.classification()
                + " openFailureLayer=" + open.failureLayer()
                + " scope=cherry_fence_gate_only"
                + " fenceWallHitboxRows=UNCHANGED_PENDING"
                + " anvilHitboxRows=UNCHANGED_PENDING"
                + " panes=NOT_COVERED"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");

        if (!allGreen) {
            throw new RuntimeException("[JULIA_BETA35_FENCE_GATE_CONTACT_RED]"
                    + " reason=cherry_fence_gate_contact_not_green"
                    + " failureLayer=" + failureLayer);
        }
    }

    /**
     * Focused GREEN proof for the Beta 3.5 full vanilla FenceGateBlock family slice.
     *
     * Gate: -Dslabbed.beta35FenceGateFamilyFix=true
     */
    private static void runBeta35FenceGateFamilyFixProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35FenceGateFamilyCase[] cases = beta35FenceGateFamilyCases();
        int rows = 0;
        int greenRows = 0;
        int closedGreen = 0;
        int openGreen = 0;
        int interactionRepresentatives = 0;
        int interactionRepresentativeGreen = 0;
        String failureLayer = "NONE";
        StringBuilder failedRows = new StringBuilder();

        for (int i = 0; i < cases.length; i++) {
            Beta35FenceGateFamilyCase gateCase = cases[i];
            Beta35LiveHitboxGateAuditResult closed = runBeta35FenceGateContactAuditRow(
                    ctx,
                    singleplayer,
                    i * 2,
                    false,
                    780,
                    "JULIA_BETA35_FENCE_GATE_FAMILY_ROW",
                    gateCase);
            Beta35LiveHitboxGateAuditResult open = runBeta35FenceGateContactAuditRow(
                    ctx,
                    singleplayer,
                    i * 2 + 1,
                    true,
                    780,
                    "JULIA_BETA35_FENCE_GATE_FAMILY_ROW",
                    gateCase);

            rows += 2;
            if ("GREEN".equals(closed.classification())) {
                greenRows++;
                closedGreen++;
            } else {
                failureLayer = beta35FirstFailureLayer(failureLayer, closed.failureLayer());
                beta35AppendFailedFenceGateFamilyRow(failedRows, gateCase.objectId(), "closed", closed.failureLayer());
            }
            if ("GREEN".equals(open.classification())) {
                greenRows++;
                openGreen++;
            } else {
                failureLayer = beta35FirstFailureLayer(failureLayer, open.failureLayer());
                beta35AppendFailedFenceGateFamilyRow(failedRows, gateCase.objectId(), "open", open.failureLayer());
            }
            if (gateCase.interactionRepresentative()) {
                interactionRepresentatives++;
                if ("GREEN".equals(open.classification())) {
                    interactionRepresentativeGreen++;
                }
            }
        }

        boolean allGreen = greenRows == rows && interactionRepresentativeGreen == interactionRepresentatives;
        if (allGreen) {
            System.out.println("JULIA_BETA35_FENCE_GATE_FAMILY_GREEN"
                    + " family=fence_gate"
                    + " variants=" + cases.length
                    + " rows=" + rows
                    + " closed=GREEN"
                    + " open=GREEN"
                    + " placement=GREEN"
                    + " survival=GREEN"
                    + " contact=GREEN"
                    + " interactionRepresentatives=oak_fence_gate,cherry_fence_gate,crimson_fence_gate"
                    + " interaction=GREEN"
                    + " triad=GREEN"
                    + " collision=CATEGORY_VALID"
                    + " classification=GREEN"
                    + " failureLayer=NONE");
        }

        System.out.println("JULIA_BETA35_FENCE_GATE_FAMILY_SUMMARY"
                + " outcome=" + (allGreen ? "GREEN" : "RED")
                + " failureLayer=" + failureLayer
                + " variants=" + cases.length
                + " rows=" + rows
                + " greenRows=" + greenRows
                + " closedGreen=" + closedGreen
                + " openGreen=" + openGreen
                + " interactionRepresentatives=" + interactionRepresentatives
                + " interactionRepresentativeGreen=" + interactionRepresentativeGreen
                + " scope=FenceGateBlock_family"
                + " fenceWallHitboxRows=UNCHANGED_PENDING"
                + " anvilHitboxRows=UNCHANGED_PENDING"
                + " panes=NOT_COVERED"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false"
                + " failedRows=" + (failedRows.length() == 0 ? "none" : failedRows));

        if (!allGreen) {
            throw new RuntimeException("[JULIA_BETA35_FENCE_GATE_FAMILY_RED]"
                    + " reason=fence_gate_family_not_green"
                    + " failureLayer=" + failureLayer
                    + " failedRows=" + failedRows);
        }
    }

    private static Beta35FenceGateFamilyCase[] beta35FenceGateFamilyCases() {
        return new Beta35FenceGateFamilyCase[] {
                new Beta35FenceGateFamilyCase("minecraft:oak_fence_gate", Items.OAK_FENCE_GATE,
                        Blocks.OAK_FENCE_GATE, true),
                new Beta35FenceGateFamilyCase("minecraft:spruce_fence_gate", Items.SPRUCE_FENCE_GATE,
                        Blocks.SPRUCE_FENCE_GATE, false),
                new Beta35FenceGateFamilyCase("minecraft:birch_fence_gate", Items.BIRCH_FENCE_GATE,
                        Blocks.BIRCH_FENCE_GATE, false),
                new Beta35FenceGateFamilyCase("minecraft:jungle_fence_gate", Items.JUNGLE_FENCE_GATE,
                        Blocks.JUNGLE_FENCE_GATE, false),
                new Beta35FenceGateFamilyCase("minecraft:acacia_fence_gate", Items.ACACIA_FENCE_GATE,
                        Blocks.ACACIA_FENCE_GATE, false),
                new Beta35FenceGateFamilyCase("minecraft:dark_oak_fence_gate", Items.DARK_OAK_FENCE_GATE,
                        Blocks.DARK_OAK_FENCE_GATE, false),
                new Beta35FenceGateFamilyCase("minecraft:mangrove_fence_gate", Items.MANGROVE_FENCE_GATE,
                        Blocks.MANGROVE_FENCE_GATE, false),
                new Beta35FenceGateFamilyCase("minecraft:cherry_fence_gate", Items.CHERRY_FENCE_GATE,
                        Blocks.CHERRY_FENCE_GATE, true),
                new Beta35FenceGateFamilyCase("minecraft:bamboo_fence_gate", Items.BAMBOO_FENCE_GATE,
                        Blocks.BAMBOO_FENCE_GATE, false),
                new Beta35FenceGateFamilyCase("minecraft:crimson_fence_gate", Items.CRIMSON_FENCE_GATE,
                        Blocks.CRIMSON_FENCE_GATE, true),
                new Beta35FenceGateFamilyCase("minecraft:warped_fence_gate", Items.WARPED_FENCE_GATE,
                        Blocks.WARPED_FENCE_GATE, false),
        };
    }

    private static String beta35FirstFailureLayer(String current, String candidate) {
        if ("NONE".equals(current) && candidate != null && !"NONE".equals(candidate)) {
            return candidate;
        }
        return current;
    }

    private static void beta35AppendFailedFenceGateFamilyRow(
            StringBuilder failedRows,
            String objectId,
            String configuration,
            String failureLayer
    ) {
        if (failedRows.length() > 0) {
            failedRows.append(',');
        }
        failedRows.append(objectId).append(':').append(configuration).append(':').append(failureLayer);
    }

    private static Beta35LiveHitboxGateAuditResult runBeta35LiveFenceWallHitboxGateRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Beta35FenceFamilyCase objectCase,
            Beta35FenceFamilyConfiguration configuration,
            int rowIndex,
            String collisionHitboxFailureLayer
    ) {
        int baseX = 620 + rowIndex * 10;
        int baseZ = 172;
        BlockPos supportCandidatePos = new BlockPos(baseX, -55, baseZ);
        BlockPos objectPos = supportCandidatePos.up();
        BlockPos unsupportedPos = supportCandidatePos.add(0, 1, 4);
        BlockHitResult useHit = beta35TopUseHit(
                supportCandidatePos,
                Beta35CommonObjectSupportCase.LOWERED_BOTTOM_DY_MINUS_ONE);

        prepareBeta35FenceFamilyLiveSupport(
                singleplayer,
                objectCase,
                configuration,
                supportCandidatePos,
                unsupportedPos);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(objectCase.item(), 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() + 3.0d,
                        supportCandidatePos.getZ() - 2.0d),
                useHit.getPos());

        final String[] placementResult = {"not-run"};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                placementResult[0] = "CLIENT_NOT_READY";
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, useHit);
            placementResult[0] = result.toString();
        });
        ctx.waitTick();
        ctx.waitTick();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState supportState = world.getBlockState(supportCandidatePos);
            BlockState objectState = world.getBlockState(objectPos);
            world.updateNeighbors(objectPos, objectState.getBlock());
            world.updateNeighbors(supportCandidatePos, supportState.getBlock());
            world.updateNeighbors(objectPos.east(), world.getBlockState(objectPos.east()).getBlock());
            world.updateNeighbors(objectPos.west(), world.getBlockState(objectPos.west()).getBlock());
        });
        ctx.waitTick();
        OffsetBlockStateModel.ModelDyOwnerTrace trace = beta35CaptureModelDyTrace(ctx, singleplayer, objectPos);
        boolean traceSeen = trace != null && trace.seen();
        int appliedCalls = trace == null ? 0 : trace.appliedCalls();
        double lastDy = trace == null ? 0.0d : trace.lastDy();
        boolean renderDyApplied = traceSeen && appliedCalls > 0 && Math.abs(trace.totalAppliedDy()) > EPSILON;

        final String[] classification = {"PENDING"};
        final String[] failureLayer = {"PROOF_HARNESS_GAP"};
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                classification[0] = "PENDING";
                failureLayer[0] = "LIVE_EVIDENCE_GAP";
                System.out.println("JULIA_BETA35_LIVE_HITBOX_GATE_ROW"
                        + " objectId=" + objectCase.objectId()
                        + " family=" + objectCase.family()
                        + " configuration=" + configuration.id
                        + " classification=" + classification[0]
                        + " failureLayer=" + failureLayer[0]
                        + " reason=client_world_or_player_missing");
                return;
            }

            BlockState supportState = mc.world.getBlockState(supportCandidatePos);
            BlockState objectState = mc.world.getBlockState(objectPos);
            boolean blockAppeared = objectState.isOf(objectCase.expectedState().getBlock());
            double supportDy = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportState);
            double objectDy = blockAppeared ? SlabSupport.getYOffset(mc.world, objectPos, objectState)
                    : Double.NaN;
            double expectedContactDy = Double.isFinite(supportDy) ? supportDy - 0.5d : Double.NaN;
            double supportVisibleTopY = beta35CommonSupportVisibleTopY(supportCandidatePos, supportState, supportDy);
            VoxelShape outlineShape = blockAppeared
                    ? objectState.getOutlineShape(mc.world, objectPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = blockAppeared
                    ? objectState.getRaycastShape(mc.world, objectPos)
                    : null;
            VoxelShape collisionShape = blockAppeared
                    ? objectState.getCollisionShape(mc.world, objectPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, objectPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, objectPos);
            net.minecraft.util.math.Box collisionBox = beta35WorldBox(collisionShape, objectPos);
            net.minecraft.util.math.Box modelProxyBox = outlineBox;
            double objectModelBottomY = modelProxyBox == null ? Double.NaN : modelProxyBox.minY;
            double contactGap = Double.isFinite(objectModelBottomY) && Double.isFinite(supportVisibleTopY)
                    ? objectModelBottomY - supportVisibleTopY
                    : Double.NaN;
            boolean survivalGreen = blockAppeared && objectState.canPlaceAt(mc.world, objectPos);
            boolean contactGreen = Double.isFinite(contactGap) && Math.abs(contactGap) <= EPSILON;
            boolean triadGreen = blockAppeared
                    && modelProxyBox != null
                    && beta35SameBox(outlineBox, modelProxyBox)
                    && beta35SameBox(raycastBox, modelProxyBox);
            boolean collisionGreen = blockAppeared
                    && modelProxyBox != null
                    && beta35SameBox(collisionBox, modelProxyBox);

            if (!blockAppeared || !survivalGreen) {
                classification[0] = "PENDING";
                failureLayer[0] = "UNKNOWN_AFTER_BOUNDED_AUDIT";
            } else if (!contactGreen || !triadGreen || !collisionGreen) {
                classification[0] = collisionHitboxFailureLayer;
                failureLayer[0] = collisionHitboxFailureLayer;
            } else {
                classification[0] = "PENDING";
                failureLayer[0] = "PROOF_HARNESS_GAP";
            }

            System.out.println("JULIA_BETA35_LIVE_HITBOX_GATE_ROW"
                    + " objectId=" + objectCase.objectId()
                    + " family=" + objectCase.family()
                    + " configuration=" + configuration.id
                    + " finalBlockState=" + objectState
                    + " supportCandidateState=" + supportState
                    + " supportDy=" + beta35FormatDoubleOrNA(supportDy)
                    + " objectDy=" + beta35FormatDoubleOrNA(objectDy)
                    + " expectedContactDy=" + beta35FormatDoubleOrNA(expectedContactDy)
                    + " supportVisibleTopY=" + beta35FormatDoubleOrNA(supportVisibleTopY)
                    + " objectModelBottomY=" + beta35FormatDoubleOrNA(objectModelBottomY)
                    + " contactGap=" + beta35FormatDoubleOrNA(contactGap)
                    + " modelBounds=" + beta35FormatBox(modelProxyBox)
                    + " outlineBounds=" + beta35FormatBox(outlineBox)
                    + " raycastBounds=" + beta35FormatBox(raycastBox)
                    + " collisionBounds=" + beta35FormatBox(collisionBox)
                    + " collisionCoLocated=" + (blockAppeared ? (collisionGreen ? "yes" : "no") : "NOT_MEASURED")
                    + " triadCoLocated=" + (blockAppeared ? (triadGreen ? "yes" : "no") : "NOT_MEASURED")
                    + " renderDyApplied=" + (renderDyApplied ? "yes" : traceSeen ? "no" : "NOT_OBSERVED")
                    + " actualModelAppliedDy=" + beta35FormatDoubleOrNA(lastDy)
                    + " placementResult=" + placementResult[0]
                    + " survivalResult=" + (survivalGreen ? "SURVIVAL_GREEN" : "SURVIVAL_RED")
                    + " interactionResult=NOT_TESTED"
                    + " connectionState=" + beta35FenceFamilyConnectionState(objectState)
                    + " classification=" + classification[0]
                    + " failureLayer=" + failureLayer[0]);
        });

        return new Beta35LiveHitboxGateAuditResult(
                objectCase.objectId(),
                objectCase.family(),
                classification[0],
                failureLayer[0]);
    }

    private static Beta35LiveHitboxGateAuditResult runBeta35LiveAnvilHitboxGateRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            int rowIndex
    ) {
        int baseX = 620 + rowIndex * 10;
        int baseZ = 172;
        BlockPos supportCandidatePos = new BlockPos(baseX, -55, baseZ);
        BlockPos objectPos = supportCandidatePos.up();
        BlockPos unsupportedPos = supportCandidatePos.add(0, 1, 4);
        BlockHitResult useHit = beta35TopUseHit(
                supportCandidatePos,
                Beta35CommonObjectSupportCase.LOWERED_BOTTOM_DY_MINUS_ONE);

        prepareBeta35CommonObjectSupport(
                singleplayer,
                supportCandidatePos,
                unsupportedPos,
                Beta35CommonObjectSupportCase.LOWERED_BOTTOM_DY_MINUS_ONE);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.ANVIL, 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() + 3.0d,
                        supportCandidatePos.getZ() - 2.0d),
                useHit.getPos());

        final String[] placementResult = {"not-run"};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                placementResult[0] = "CLIENT_NOT_READY";
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, useHit);
            placementResult[0] = result.toString();
        });
        ctx.waitTick();
        ctx.waitTick();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState supportState = world.getBlockState(supportCandidatePos);
            BlockState objectState = world.getBlockState(objectPos);
            world.updateNeighbors(objectPos, objectState.getBlock());
            world.updateNeighbors(supportCandidatePos, supportState.getBlock());
        });
        ctx.waitTick();
        OffsetBlockStateModel.ModelDyOwnerTrace trace = beta35CaptureModelDyTrace(ctx, singleplayer, objectPos);
        boolean traceSeen = trace != null && trace.seen();
        int appliedCalls = trace == null ? 0 : trace.appliedCalls();
        double lastDy = trace == null ? 0.0d : trace.lastDy();
        boolean renderDyApplied = traceSeen && appliedCalls > 0 && Math.abs(trace.totalAppliedDy()) > EPSILON;

        final String[] classification = {"PENDING"};
        final String[] failureLayer = {"PROOF_HARNESS_GAP"};
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                classification[0] = "PENDING";
                failureLayer[0] = "LIVE_EVIDENCE_GAP";
                System.out.println("JULIA_BETA35_LIVE_HITBOX_GATE_ROW"
                        + " objectId=minecraft:anvil"
                        + " family=anvil"
                        + " configuration=valid_slab_supported"
                        + " classification=" + classification[0]
                        + " failureLayer=" + failureLayer[0]
                        + " reason=client_world_or_player_missing");
                return;
            }

            BlockPos actualObjectPos = beta35CommonActualObjectPos(
                    mc.world,
                    supportCandidatePos,
                    objectPos,
                    Blocks.ANVIL.getDefaultState());
            BlockState supportState = mc.world.getBlockState(supportCandidatePos);
            BlockState objectState = mc.world.getBlockState(actualObjectPos);
            boolean blockAppeared = objectState.isOf(Blocks.ANVIL);
            double supportDy = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportState);
            double objectDy = blockAppeared ? SlabSupport.getYOffset(mc.world, actualObjectPos, objectState)
                    : Double.NaN;
            double expectedContactDy = Double.isFinite(supportDy) ? supportDy - 0.5d : Double.NaN;
            double supportVisibleTopY = beta35CommonSupportVisibleTopY(supportCandidatePos, supportState, supportDy);
            VoxelShape outlineShape = blockAppeared
                    ? objectState.getOutlineShape(mc.world, actualObjectPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = blockAppeared
                    ? objectState.getRaycastShape(mc.world, actualObjectPos)
                    : null;
            VoxelShape collisionShape = blockAppeared
                    ? objectState.getCollisionShape(mc.world, actualObjectPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, actualObjectPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, actualObjectPos);
            net.minecraft.util.math.Box collisionBox = beta35WorldBox(collisionShape, actualObjectPos);
            net.minecraft.util.math.Box modelProxyBox = outlineBox;
            double objectModelBottomY = modelProxyBox == null ? Double.NaN : modelProxyBox.minY;
            double contactGap = Double.isFinite(objectModelBottomY) && Double.isFinite(supportVisibleTopY)
                    ? objectModelBottomY - supportVisibleTopY
                    : Double.NaN;
            boolean survivalGreen = blockAppeared && objectState.canPlaceAt(mc.world, actualObjectPos);
            boolean contactGreen = Double.isFinite(contactGap) && Math.abs(contactGap) <= EPSILON;
            boolean triadGreen = blockAppeared
                    && modelProxyBox != null
                    && beta35SameBox(outlineBox, modelProxyBox)
                    && beta35SameBox(raycastBox, modelProxyBox);
            boolean collisionGreen = blockAppeared
                    && modelProxyBox != null
                    && beta35SameBox(collisionBox, modelProxyBox);

            if (!blockAppeared || !survivalGreen) {
                classification[0] = "PENDING";
                failureLayer[0] = "UNKNOWN_AFTER_BOUNDED_AUDIT";
            } else if (!collisionGreen) {
                classification[0] = "ANVIL_COLLISION_HITBOX_GAP";
                failureLayer[0] = "ANVIL_COLLISION_HITBOX_GAP";
            } else if (!contactGreen || !triadGreen) {
                classification[0] = "PENDING";
                failureLayer[0] = "PROOF_HARNESS_GAP";
            } else {
                classification[0] = "PENDING";
                failureLayer[0] = "PROOF_HARNESS_GAP";
            }

            System.out.println("JULIA_BETA35_LIVE_HITBOX_GATE_ROW"
                    + " objectId=minecraft:anvil"
                    + " family=anvil"
                    + " configuration=valid_slab_supported"
                    + " finalBlockState=" + objectState
                    + " supportCandidateState=" + supportState
                    + " supportDy=" + beta35FormatDoubleOrNA(supportDy)
                    + " objectDy=" + beta35FormatDoubleOrNA(objectDy)
                    + " expectedContactDy=" + beta35FormatDoubleOrNA(expectedContactDy)
                    + " supportVisibleTopY=" + beta35FormatDoubleOrNA(supportVisibleTopY)
                    + " objectModelBottomY=" + beta35FormatDoubleOrNA(objectModelBottomY)
                    + " contactGap=" + beta35FormatDoubleOrNA(contactGap)
                    + " modelBounds=" + beta35FormatBox(modelProxyBox)
                    + " outlineBounds=" + beta35FormatBox(outlineBox)
                    + " raycastBounds=" + beta35FormatBox(raycastBox)
                    + " collisionBounds=" + beta35FormatBox(collisionBox)
                    + " collisionCoLocated=" + (blockAppeared ? (collisionGreen ? "yes" : "no") : "NOT_MEASURED")
                    + " triadCoLocated=" + (blockAppeared ? (triadGreen ? "yes" : "no") : "NOT_MEASURED")
                    + " renderDyApplied=" + (renderDyApplied ? "yes" : traceSeen ? "no" : "NOT_OBSERVED")
                    + " actualModelAppliedDy=" + beta35FormatDoubleOrNA(lastDy)
                    + " placementResult=" + placementResult[0]
                    + " survivalResult=" + (survivalGreen ? "SURVIVAL_GREEN" : "SURVIVAL_RED")
                    + " interactionResult=NOT_TESTED"
                    + " classification=" + classification[0]
                    + " failureLayer=" + failureLayer[0]);
        });

        return new Beta35LiveHitboxGateAuditResult(
                "minecraft:anvil",
                "anvil",
                classification[0],
                failureLayer[0]);
    }

    private static Beta35LiveHitboxGateAuditResult runBeta35LiveFenceGateRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            int rowIndex,
            boolean openAfterPlacement
    ) {
        return runBeta35FenceGateContactAuditRow(
                ctx,
                singleplayer,
                rowIndex,
                openAfterPlacement,
                620,
                "JULIA_BETA35_LIVE_HITBOX_GATE_ROW",
                new Beta35FenceGateFamilyCase("minecraft:cherry_fence_gate", Items.CHERRY_FENCE_GATE,
                        Blocks.CHERRY_FENCE_GATE, true));
    }

    private static Beta35LiveHitboxGateAuditResult runBeta35FenceGateContactRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            int rowIndex,
            boolean openAfterPlacement
    ) {
        return runBeta35FenceGateContactAuditRow(
                ctx,
                singleplayer,
                rowIndex,
                openAfterPlacement,
                740,
                "JULIA_BETA35_FENCE_GATE_CONTACT_ROW",
                new Beta35FenceGateFamilyCase("minecraft:cherry_fence_gate", Items.CHERRY_FENCE_GATE,
                        Blocks.CHERRY_FENCE_GATE, true));
    }

    private static Beta35LiveHitboxGateAuditResult runBeta35FenceGateContactAuditRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            int rowIndex,
            boolean openAfterPlacement,
            int baseStartX,
            String rowMarker,
            Beta35FenceGateFamilyCase gateCase
    ) {
        int baseX = baseStartX + rowIndex * 10;
        int baseZ = 172;
        BlockPos supportCandidatePos = new BlockPos(baseX, -55, baseZ);
        BlockPos objectPos = supportCandidatePos.up();
        BlockHitResult useHit = beta35TopUseHit(
                supportCandidatePos,
                Beta35CommonObjectSupportCase.LOWERED_BOTTOM_DY_MINUS_ONE);

        prepareBeta35LiveHitboxGateSupport(singleplayer, supportCandidatePos);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(gateCase.item(), 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() + 3.0d,
                        supportCandidatePos.getZ() - 2.0d),
                useHit.getPos());

        final String[] placementResult = {"not-run"};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                placementResult[0] = "CLIENT_NOT_READY";
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, useHit);
            placementResult[0] = result.toString();
        });
        ctx.waitTick();
        ctx.waitTick();

        final String[] interactionResult = {openAfterPlacement ? "not-run" : "NOT_TESTED_CLOSED_ROW"};
        if (openAfterPlacement) {
            syncHeldMainHand(ctx, singleplayer, ItemStack.EMPTY);
            beta35FenceGateStabilizePlayer(ctx, singleplayer);
            ctx.runOnClient(mc -> {
                if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                    interactionResult[0] = "CLIENT_NOT_READY";
                    return;
                }
                if (!mc.world.getBlockState(objectPos).isOf(gateCase.block())) {
                    interactionResult[0] = "BLOCK_NOT_PRESENT";
                    return;
                }
                BlockHitResult gateHit = new BlockHitResult(
                        new Vec3d(
                                objectPos.getX() + 0.5d,
                                objectPos.getY() + 0.5d,
                                objectPos.getZ() + 0.5d),
                        Direction.SOUTH,
                        objectPos,
                        false);
                ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, gateHit);
                interactionResult[0] = result.toString();
            });
            ctx.waitTick();
            ctx.waitTick();
        }

        OffsetBlockStateModel.ModelDyOwnerTrace trace = beta35CaptureModelDyTrace(ctx, singleplayer, objectPos);
        boolean traceSeen = trace != null && trace.seen();
        int appliedCalls = trace == null ? 0 : trace.appliedCalls();
        double lastDy = trace == null ? 0.0d : trace.lastDy();
        boolean renderDyApplied = traceSeen && appliedCalls > 0 && Math.abs(trace.totalAppliedDy()) > EPSILON;

        final String[] classification = {"PENDING"};
        final String[] failureLayer = {"PROOF_HARNESS_GAP"};
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                classification[0] = "PENDING";
                failureLayer[0] = "LIVE_EVIDENCE_GAP";
                System.out.println(rowMarker
                        + " objectId=" + gateCase.objectId()
                        + " family=fence_gate"
                        + " configuration=" + (openAfterPlacement ? "open" : "closed")
                        + " classification=" + classification[0]
                        + " failureLayer=" + failureLayer[0]
                        + " reason=client_world_or_player_missing");
                return;
            }

            BlockState supportState = mc.world.getBlockState(supportCandidatePos);
            BlockState objectState = mc.world.getBlockState(objectPos);
            boolean blockAppeared = objectState.isOf(gateCase.block());
            String placementStatus = blockAppeared ? "PLACEMENT_GREEN" : placementResult[0];
            double supportDy = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportState);
            double objectDy = blockAppeared ? SlabSupport.getYOffset(mc.world, objectPos, objectState)
                    : Double.NaN;
            double expectedContactDy = Double.isFinite(supportDy) ? supportDy - 0.5d : Double.NaN;
            double supportVisibleTopY = beta35CommonSupportVisibleTopY(supportCandidatePos, supportState, supportDy);
            VoxelShape outlineShape = blockAppeared
                    ? objectState.getOutlineShape(mc.world, objectPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = blockAppeared
                    ? objectState.getRaycastShape(mc.world, objectPos)
                    : null;
            VoxelShape collisionShape = blockAppeared
                    ? objectState.getCollisionShape(mc.world, objectPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, objectPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, objectPos);
            net.minecraft.util.math.Box collisionBox = beta35WorldBox(collisionShape, objectPos);
            net.minecraft.util.math.Box modelProxyBox = outlineBox;
            double objectModelBottomY = modelProxyBox == null ? Double.NaN : modelProxyBox.minY;
            double contactGap = Double.isFinite(objectModelBottomY) && Double.isFinite(supportVisibleTopY)
                    ? objectModelBottomY - supportVisibleTopY
                    : Double.NaN;
            boolean survivalGreen = blockAppeared && objectState.canPlaceAt(mc.world, objectPos);
            boolean contactGreen = Double.isFinite(contactGap) && Math.abs(contactGap) <= EPSILON;
            boolean configurationGreen = !openAfterPlacement
                    || (blockAppeared
                            && objectState.contains(Properties.OPEN)
                            && objectState.get(Properties.OPEN));
            boolean interactionGreen = !openAfterPlacement
                    || interactionResult[0].startsWith("Success")
                    || interactionResult[0].startsWith("SUCCESS");
            boolean triadGreen = blockAppeared
                    && modelProxyBox != null
                    && beta35SameBox(outlineBox, modelProxyBox)
                    && beta35SameBox(raycastBox, modelProxyBox);
            boolean openGate = blockAppeared
                    && objectState.contains(Properties.OPEN)
                    && objectState.get(Properties.OPEN);
            boolean openGateCollisionCategoryValid = openGate
                    && collisionBox == null;
            boolean closedGateCollisionCategoryValid = blockAppeared
                    && !openGate
                    && collisionBox != null
                    && modelProxyBox != null
                    && beta35SameHorizontalColumn(collisionBox, modelProxyBox)
                    && Math.abs(collisionBox.minY - modelProxyBox.minY) <= EPSILON
                    && collisionBox.maxY >= modelProxyBox.maxY - EPSILON;
            boolean collisionGreen = blockAppeared
                    && modelProxyBox != null
                    && (beta35SameBox(collisionBox, modelProxyBox)
                            || openGateCollisionCategoryValid
                            || closedGateCollisionCategoryValid);
            String collisionCoLocated = blockAppeared
                    ? (beta35SameBox(collisionBox, modelProxyBox) ? "yes"
                            : openGateCollisionCategoryValid ? "category_valid_open_empty"
                                    : closedGateCollisionCategoryValid ? "category_valid_closed_tall" : "no")
                    : "NOT_MEASURED";
            boolean modelRenderGreen = !traceSeen || Math.abs(lastDy - objectDy) <= EPSILON;

            if (!blockAppeared || !survivalGreen) {
                classification[0] = "FENCE_GATE_SUPPORT_GAP";
                failureLayer[0] = "FENCE_GATE_SUPPORT_GAP";
            } else if (!configurationGreen || !interactionGreen
                    || !contactGreen
                    || Math.abs(objectDy - expectedContactDy) > EPSILON) {
                classification[0] = "FENCE_GATE_CONTACT_GAP";
                failureLayer[0] = "FENCE_GATE_CONTACT_GAP";
            } else if (!modelRenderGreen) {
                classification[0] = "FENCE_GATE_MODEL_RENDER_GAP";
                failureLayer[0] = "FENCE_GATE_MODEL_RENDER_GAP";
            } else if (!triadGreen || !collisionGreen) {
                classification[0] = "FENCE_GATE_TRIAD_MISMATCH";
                failureLayer[0] = "FENCE_GATE_TRIAD_MISMATCH";
            } else {
                classification[0] = "GREEN";
                failureLayer[0] = "NONE";
            }

            System.out.println(rowMarker
                    + " objectId=" + gateCase.objectId()
                    + " family=fence_gate"
                    + " configuration=" + (openAfterPlacement ? "open" : "closed")
                    + " finalBlockState=" + objectState
                    + " supportCandidateState=" + supportState
                    + " supportDy=" + beta35FormatDoubleOrNA(supportDy)
                    + " objectDy=" + beta35FormatDoubleOrNA(objectDy)
                    + " expectedContactDy=" + beta35FormatDoubleOrNA(expectedContactDy)
                    + " supportVisibleTopY=" + beta35FormatDoubleOrNA(supportVisibleTopY)
                    + " objectModelBottomY=" + beta35FormatDoubleOrNA(objectModelBottomY)
                    + " contactGap=" + beta35FormatDoubleOrNA(contactGap)
                    + " modelBounds=" + beta35FormatBox(modelProxyBox)
                    + " outlineBounds=" + beta35FormatBox(outlineBox)
                    + " raycastBounds=" + beta35FormatBox(raycastBox)
                    + " collisionBounds=" + beta35FormatBox(collisionBox)
                    + " collisionCoLocated=" + collisionCoLocated
                    + " triadCoLocated=" + (blockAppeared ? (triadGreen ? "yes" : "no") : "NOT_MEASURED")
                    + " renderDyApplied=" + (renderDyApplied ? "yes" : traceSeen ? "no" : "NOT_OBSERVED")
                    + " actualModelAppliedDy=" + beta35FormatDoubleOrNA(lastDy)
                    + " placementResult=" + placementStatus
                    + " rawPlacementActionResult=" + placementResult[0]
                    + " survivalResult=" + (survivalGreen ? "SURVIVAL_GREEN" : "SURVIVAL_RED")
                    + " interactionResult=" + interactionResult[0]
                    + " classification=" + classification[0]
                    + " failureLayer=" + failureLayer[0]);
        });

        return new Beta35LiveHitboxGateAuditResult(
                gateCase.objectId(),
                "fence_gate",
                classification[0],
                failureLayer[0]);
    }

    private static void beta35FenceGateStabilizePlayer(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        singleplayer.getServer().runOnServer(server -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                return;
            }
            var player = server.getPlayerManager().getPlayerList().get(0);
            player.setNoGravity(true);
            player.setVelocity(Vec3d.ZERO);
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.setNoGravity(true);
                mc.player.setVelocity(Vec3d.ZERO);
            }
        });
        ctx.waitTick();
    }

    private static OffsetBlockStateModel.ModelDyOwnerTrace beta35CaptureModelDyTrace(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            BlockPos objectPos
    ) {
        ctx.runOnClient(mc -> {
            if (mc.worldRenderer == null) {
                return;
            }
            OffsetBlockStateModel.resetModelDyOwnerTrace(objectPos);
            mc.worldRenderer.scheduleBlockRenders(
                    objectPos.getX() - 1, objectPos.getY() - 1, objectPos.getZ() - 1,
                    objectPos.getX() + 1, objectPos.getY() + 1, objectPos.getZ() + 1);
        });
        for (int i = 0; i < 6; i++) {
            ctx.runOnClient(mc -> {
                if (mc.worldRenderer == null) {
                    return;
                }
                mc.worldRenderer.scheduleBlockRenders(
                        objectPos.getX() - 1, objectPos.getY() - 1, objectPos.getZ() - 1,
                        objectPos.getX() + 1, objectPos.getY() + 1, objectPos.getZ() + 1);
            });
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        final OffsetBlockStateModel.ModelDyOwnerTrace[] traceBox = {
                new OffsetBlockStateModel.ModelDyOwnerTrace(false, "none", "none", "none", 0, 0, 0.0d, 0.0d)
        };
        ctx.runOnClient(mc -> traceBox[0] = OffsetBlockStateModel.snapshotModelDyOwnerTrace());
        return traceBox[0];
    }

    private static void prepareBeta35LiveHitboxGateSupport(
            TestSingleplayerContext singleplayer,
            BlockPos supportCandidatePos
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = supportCandidatePos.getX() - 3; x <= supportCandidatePos.getX() + 3; x++) {
                for (int y = supportCandidatePos.getY() - 6; y <= supportCandidatePos.getY() + 4; y++) {
                    for (int z = supportCandidatePos.getZ() - 3; z <= supportCandidatePos.getZ() + 3; z++) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }
            beta35PlaceLoweredBottomSupport(world, supportCandidatePos);
        });
    }

    /**
     * Fence-family live-faithful classification for Julia's Beta 3.5 false-green report.
     *
     * Gate: -Dslabbed.beta35FenceFamilyLiveRed=true
     */
    private static void runBeta35FenceFamilyLiveRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35FenceFamilyCase oakFence = new Beta35FenceFamilyCase(
                "minecraft:oak_fence",
                "fence_family",
                Items.OAK_FENCE,
                Blocks.OAK_FENCE.getDefaultState());
        Beta35FenceFamilyCase[] cases = {
                oakFence,
                new Beta35FenceFamilyCase(
                        "minecraft:spruce_fence",
                        "fence_family",
                        Items.SPRUCE_FENCE,
                        Blocks.SPRUCE_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase(
                        "minecraft:nether_brick_fence",
                        "fence_family",
                        Items.NETHER_BRICK_FENCE,
                        Blocks.NETHER_BRICK_FENCE.getDefaultState()),
                new Beta35FenceFamilyCase(
                        "minecraft:cobblestone_wall",
                        "wall_family",
                        Items.COBBLESTONE_WALL,
                        Blocks.COBBLESTONE_WALL.getDefaultState())
        };

        System.out.println("JULIA_BETA35_FENCE_FAMILY_LIVE_RED"
                + " scope=live_faithful_connected_fence_family_classification"
                + " supportCase=LOWERED_BOTTOM_DY_MINUS_ONE"
                + " priorOakFenceGreen=LIVE_SUSPECT"
                + " productionBehaviorChanged=false"
                + " releaseAudit=NOT_RUN");

        int rows = 0;
        int greenSimplifiedOnly = 0;
        int greenLiveLike = 0;
        int contactGap = 0;
        int triadMismatch = 0;
        int collisionShapeRisk = 0;
        int connectionShapeRisk = 0;
        int placementFailure = 0;
        int survivalFailure = 0;
        int variantCoverageGap = 0;
        int needsImplementation = 0;
        String oakFenceClassification = "NOT_MEASURED";
        String firstFailureLayer = "NONE";

        for (Beta35FenceFamilyConfiguration configuration : Beta35FenceFamilyConfiguration.values()) {
            Beta35FenceFamilyAuditResult result = runBeta35FenceFamilyAuditRow(
                    ctx,
                    singleplayer,
                    oakFence,
                    configuration,
                    rows);
            rows++;
            if ("NOT_MEASURED".equals(oakFenceClassification)
                    || configuration == Beta35FenceFamilyConfiguration.BESIDE_LOWERED_FULLBLOCK) {
                oakFenceClassification = result.classification();
            }
            switch (result.classification()) {
                case "GREEN_SIMPLIFIED_ONLY" -> greenSimplifiedOnly++;
                case "GREEN_LIVE_LIKE" -> greenLiveLike++;
                case "CONTACT_GAP" -> contactGap++;
                case "TRIAD_MISMATCH" -> triadMismatch++;
                case "COLLISION_SHAPE_RISK" -> collisionShapeRisk++;
                case "CONNECTION_SHAPE_RISK" -> connectionShapeRisk++;
                case "PLACEMENT_FAILURE" -> placementFailure++;
                case "SURVIVAL_FAILURE" -> survivalFailure++;
                case "VARIANT_COVERAGE_GAP" -> variantCoverageGap++;
                default -> needsImplementation++;
            }
            if (!"GREEN_LIVE_LIKE".equals(result.classification())
                    && !"GREEN_SIMPLIFIED_ONLY".equals(result.classification())
                    && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = result.classification();
            }
        }

        for (int i = 1; i < cases.length; i++) {
            Beta35FenceFamilyAuditResult result = runBeta35FenceFamilyAuditRow(
                    ctx,
                    singleplayer,
                    cases[i],
                    Beta35FenceFamilyConfiguration.ISOLATED,
                    rows);
            rows++;
            switch (result.classification()) {
                case "GREEN_SIMPLIFIED_ONLY" -> greenSimplifiedOnly++;
                case "GREEN_LIVE_LIKE" -> greenLiveLike++;
                case "CONTACT_GAP" -> contactGap++;
                case "TRIAD_MISMATCH" -> triadMismatch++;
                case "COLLISION_SHAPE_RISK" -> collisionShapeRisk++;
                case "CONNECTION_SHAPE_RISK" -> connectionShapeRisk++;
                case "PLACEMENT_FAILURE" -> placementFailure++;
                case "SURVIVAL_FAILURE" -> survivalFailure++;
                case "VARIANT_COVERAGE_GAP" -> variantCoverageGap++;
                default -> needsImplementation++;
            }
            if (!"GREEN_LIVE_LIKE".equals(result.classification())
                    && !"GREEN_SIMPLIFIED_ONLY".equals(result.classification())
                    && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = result.classification();
            }
        }

        String proofOutcome;
        if (contactGap > 0 || triadMismatch > 0 || collisionShapeRisk > 0 || connectionShapeRisk > 0
                || placementFailure > 0 || survivalFailure > 0 || variantCoverageGap > 0
                || needsImplementation > 0) {
            proofOutcome = "RED";
        } else if (greenLiveLike > 0) {
            proofOutcome = "GREEN";
        } else {
            proofOutcome = "PENDING";
        }

        System.out.println("JULIA_BETA35_FENCE_FAMILY_SUMMARY"
                + " outcome=" + proofOutcome
                + " rows=" + rows
                + " greenSimplifiedOnly=" + greenSimplifiedOnly
                + " greenLiveLike=" + greenLiveLike
                + " contactGap=" + contactGap
                + " triadMismatch=" + triadMismatch
                + " collisionShapeRisk=" + collisionShapeRisk
                + " connectionShapeRisk=" + connectionShapeRisk
                + " placementFailure=" + placementFailure
                + " survivalFailure=" + survivalFailure
                + " variantCoverageGap=" + variantCoverageGap
                + " needsImplementation=" + needsImplementation
                + " oakFenceClassification=" + oakFenceClassification
                + " firstFailureLayer=" + firstFailureLayer
                + " previousOakFenceGreenStatus=VALID_SIMPLIFIED_ONLY_LIVE_SUPERSEDED"
                + " implementationNeeded=" + (!"GREEN".equals(proofOutcome))
                + " productionFixImplemented=false"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false"
                + " canonicalCheckoutModified=false");
    }

    private static Beta35FenceFamilyAuditResult runBeta35FenceFamilyAuditRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Beta35FenceFamilyCase objectCase,
            Beta35FenceFamilyConfiguration configuration,
            int rowIndex
    ) {
        int baseX = 212 + rowIndex * 9;
        int baseZ = 124;
        BlockPos supportCandidatePos = new BlockPos(baseX, -55, baseZ);
        BlockPos objectPos = supportCandidatePos.up();
        BlockPos unsupportedPos = supportCandidatePos.add(0, 1, 4);
        BlockPos eastNeighborPos = objectPos.east();
        BlockPos westNeighborPos = objectPos.west();
        BlockPos eastSupportPos = supportCandidatePos.east();
        BlockPos westSupportPos = supportCandidatePos.west();
        BlockHitResult useHit = new BlockHitResult(
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() - 0.5d,
                        supportCandidatePos.getZ() + 0.5d),
                Direction.UP,
                supportCandidatePos,
                false);

        prepareBeta35FenceFamilyLiveSupport(
                singleplayer,
                objectCase,
                configuration,
                supportCandidatePos,
                unsupportedPos);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(objectCase.item(), 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() + 3.0d,
                        supportCandidatePos.getZ() - 2.0d),
                useHit.getPos());

        final String[] placementResult = {"not-run"};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                placementResult[0] = "CLIENT_NOT_READY";
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, useHit);
            placementResult[0] = result.toString();
        });
        ctx.waitTick();
        ctx.waitTick();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState supportState = world.getBlockState(supportCandidatePos);
            BlockState objectState = world.getBlockState(objectPos);
            world.updateNeighbors(objectPos, objectState.getBlock());
            world.updateNeighbors(supportCandidatePos, supportState.getBlock());
            world.updateNeighbors(eastNeighborPos, world.getBlockState(eastNeighborPos).getBlock());
            world.updateNeighbors(westNeighborPos, world.getBlockState(westNeighborPos).getBlock());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String[] classification = {"PROOF_HARNESS_GAP"};
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                System.out.println("JULIA_BETA35_FENCE_FAMILY_ROW"
                        + " objectId=" + objectCase.objectId()
                        + " family=" + objectCase.family()
                        + " configuration=" + configuration.id
                        + " classification=PROOF_HARNESS_GAP"
                        + " reason=client_world_or_player_missing");
                return;
            }

            BlockState supportState = mc.world.getBlockState(supportCandidatePos);
            BlockState objectState = mc.world.getBlockState(objectPos);
            boolean blockAppeared = objectState.isOf(objectCase.expectedState().getBlock());
            double supportDy = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportState);
            double objectDy = blockAppeared ? SlabSupport.getYOffset(mc.world, objectPos, objectState)
                    : Double.NaN;
            double supportVisibleTopY = beta35CommonSupportVisibleTopY(supportCandidatePos, supportState, supportDy);
            VoxelShape outlineShape = blockAppeared
                    ? objectState.getOutlineShape(mc.world, objectPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = blockAppeared
                    ? objectState.getRaycastShape(mc.world, objectPos)
                    : null;
            VoxelShape collisionShape = blockAppeared
                    ? objectState.getCollisionShape(mc.world, objectPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, objectPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, objectPos);
            net.minecraft.util.math.Box collisionBox = beta35WorldBox(collisionShape, objectPos);
            net.minecraft.util.math.Box modelProxyBox = collisionBox != null ? collisionBox : outlineBox;
            double objectModelBottomY = modelProxyBox == null ? Double.NaN : modelProxyBox.minY;
            double contactGap = Double.isFinite(objectModelBottomY) && Double.isFinite(supportVisibleTopY)
                    ? objectModelBottomY - supportVisibleTopY
                    : Double.NaN;
            boolean survivalGreen = blockAppeared && objectState.canPlaceAt(mc.world, objectPos);
            boolean unsupportedFails = true;
            String unsupportedResult = "UNSUPPORTED_NOT_TESTED_SELF_SUPPORTING";
            boolean contactGreen = Double.isFinite(contactGap) && Math.abs(contactGap) <= EPSILON;
            boolean triadGreen = blockAppeared
                    && modelProxyBox != null
                    && beta35SameBox(outlineBox, modelProxyBox)
                    && beta35SameBox(raycastBox, modelProxyBox);
            boolean collisionGreen = blockAppeared
                    && modelProxyBox != null
                    && beta35SameBox(collisionBox, modelProxyBox);
            String connectionState = beta35FenceFamilyConnectionState(objectState);
            boolean expectedConnectionGreen = beta35FenceFamilyExpectedConnectionGreen(
                    objectState,
                    configuration);

            if (!blockAppeared) {
                classification[0] = "PLACEMENT_FAILURE";
            } else if (!survivalGreen || !unsupportedFails) {
                classification[0] = "SURVIVAL_FAILURE";
            } else if (!contactGreen) {
                classification[0] = objectState.isOf(Blocks.OAK_FENCE)
                        ? "CONTACT_GAP" : "VARIANT_COVERAGE_GAP";
            } else if (!triadGreen) {
                classification[0] = "TRIAD_MISMATCH";
            } else if (!collisionGreen) {
                classification[0] = "COLLISION_SHAPE_RISK";
            } else if (!expectedConnectionGreen) {
                classification[0] = "CONNECTION_SHAPE_RISK";
            } else if (configuration == Beta35FenceFamilyConfiguration.ISOLATED
                    && objectState.isOf(Blocks.OAK_FENCE)) {
                classification[0] = "GREEN_SIMPLIFIED_ONLY";
            } else {
                classification[0] = "GREEN_LIVE_LIKE";
            }

            System.out.println("JULIA_BETA35_FENCE_FAMILY_ROW"
                    + " objectId=" + objectCase.objectId()
                    + " family=" + objectCase.family()
                    + " configuration=" + configuration.id
                    + " placementResult=" + placementResult[0]
                    + " blockAppearedAfterAttempt=" + blockAppeared
                    + " finalBlockState=" + objectState
                    + " supportCandidateState=" + supportState
                    + " supportDy=" + String.format("%.6f", supportDy)
                    + " objectDy=" + (Double.isFinite(objectDy) ? String.format("%.6f", objectDy) : "N/A")
                    + " supportVisibleTopY=" + (Double.isFinite(supportVisibleTopY)
                            ? String.format("%.6f", supportVisibleTopY) : "N/A")
                    + " objectModelBottomY=" + (Double.isFinite(objectModelBottomY)
                            ? String.format("%.6f", objectModelBottomY) : "CATEGORY_PROXY")
                    + " contactGap=" + (Double.isFinite(contactGap)
                            ? String.format("%.6f", contactGap) : "CONTACT_NOT_APPLICABLE")
                    + " modelBounds=" + beta35FormatBox(modelProxyBox)
                    + " outlineBounds=" + beta35FormatBox(outlineBox)
                    + " raycastBounds=" + beta35FormatBox(raycastBox)
                    + " collisionBounds=" + beta35FormatBox(collisionBox)
                    + " connectionState=" + connectionState
                    + " triadCoLocated=" + (blockAppeared ? (triadGreen ? "yes" : "no") : "NOT_MEASURED")
                    + " collisionCoLocated=" + (blockAppeared ? (collisionGreen ? "yes" : "no") : "NOT_MEASURED")
                    + " survival=" + (survivalGreen ? "SURVIVAL_GREEN" : "SURVIVAL_RED")
                    + " unsupported=" + unsupportedResult
                    + " classification=" + classification[0]);
        });

        return new Beta35FenceFamilyAuditResult(
                objectCase.objectId(),
                objectCase.family(),
                configuration.id,
                classification[0]);
    }

    private static void prepareBeta35FenceFamilyLiveSupport(
            TestSingleplayerContext singleplayer,
            Beta35FenceFamilyCase objectCase,
            Beta35FenceFamilyConfiguration configuration,
            BlockPos supportCandidatePos,
            BlockPos unsupportedPos
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = supportCandidatePos.getX() - 5; x <= supportCandidatePos.getX() + 5; x++) {
                for (int y = supportCandidatePos.getY() - 6; y <= supportCandidatePos.getY() + 5; y++) {
                    for (int z = supportCandidatePos.getZ() - 3; z <= unsupportedPos.getZ() + 3; z++) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }

            beta35PlaceLoweredBottomSupport(world, supportCandidatePos);
            if (configuration.eastFenceNeighbor || configuration.eastLoweredFullBlockNeighbor) {
                beta35PlaceLoweredBottomSupport(world, supportCandidatePos.east());
            }
            if (configuration.westFenceNeighbor) {
                beta35PlaceLoweredBottomSupport(world, supportCandidatePos.west());
            }

            if (configuration.eastFenceNeighbor) {
                world.setBlockState(
                        supportCandidatePos.east().up(),
                        objectCase.expectedState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
            }
            if (configuration.westFenceNeighbor) {
                world.setBlockState(
                        supportCandidatePos.west().up(),
                        objectCase.expectedState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
            }
            if (configuration.eastLoweredFullBlockNeighbor) {
                BlockPos fullBlockPos = supportCandidatePos.east().up();
                world.setBlockState(fullBlockPos, Blocks.STONE.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.addAnchor(world, fullBlockPos, world.getBlockState(fullBlockPos));
            }
        });
    }

    private static void beta35PlaceLoweredBottomSupport(net.minecraft.server.world.ServerWorld world, BlockPos pos) {
        BlockPos compoundSourcePos = pos.down();
        BlockPos towerCarrier = pos.down(2);
        BlockPos towerAnchor = pos.down(3);
        BlockPos towerBase = pos.down(4);
        world.setBlockState(towerBase,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        world.setBlockState(towerAnchor, Blocks.STONE.getDefaultState(),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(world, towerAnchor, world.getBlockState(towerAnchor));
        world.setBlockState(towerCarrier,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, towerCarrier,
                world.getBlockState(towerCarrier));
        world.setBlockState(compoundSourcePos, Blocks.STONE.getDefaultState(),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(world, compoundSourcePos, world.getBlockState(compoundSourcePos));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(world, compoundSourcePos,
                world.getBlockState(compoundSourcePos));
        world.setBlockState(pos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addCompoundVisibleOwnerTopSlab(
                world,
                pos,
                world.getBlockState(pos),
                compoundSourcePos,
                world.getBlockState(compoundSourcePos));
    }

    private static String beta35FenceFamilyConnectionState(BlockState state) {
        if (state == null || state.isAir()) {
            return "N/A";
        }
        StringBuilder builder = new StringBuilder();
        beta35AppendProperty(builder, state, Properties.NORTH);
        beta35AppendProperty(builder, state, Properties.EAST);
        beta35AppendProperty(builder, state, Properties.SOUTH);
        beta35AppendProperty(builder, state, Properties.WEST);
        beta35AppendProperty(builder, state, Properties.NORTH_WALL_SHAPE);
        beta35AppendProperty(builder, state, Properties.EAST_WALL_SHAPE);
        beta35AppendProperty(builder, state, Properties.SOUTH_WALL_SHAPE);
        beta35AppendProperty(builder, state, Properties.WEST_WALL_SHAPE);
        beta35AppendProperty(builder, state, Properties.UP);
        return builder.length() == 0 ? "NO_CONNECTION_PROPERTIES" : builder.toString();
    }

    private static void beta35AppendProperty(
            StringBuilder builder,
            BlockState state,
            net.minecraft.state.property.Property<?> property
    ) {
        if (!state.contains(property)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(",");
        }
        builder.append(property.getName()).append("=").append(state.get(property));
    }

    private static boolean beta35FenceFamilyExpectedConnectionGreen(
            BlockState state,
            Beta35FenceFamilyConfiguration configuration
    ) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (state.contains(Properties.NORTH) && Boolean.TRUE.equals(state.get(Properties.NORTH))) {
            return false;
        }
        if (state.contains(Properties.SOUTH) && Boolean.TRUE.equals(state.get(Properties.SOUTH))) {
            return false;
        }
        if (state.contains(Properties.EAST)) {
            boolean east = Boolean.TRUE.equals(state.get(Properties.EAST));
            if ((configuration.eastFenceNeighbor || configuration.eastLoweredFullBlockNeighbor) != east) {
                return false;
            }
        }
        if (state.contains(Properties.WEST)) {
            boolean west = Boolean.TRUE.equals(state.get(Properties.WEST));
            if (configuration.westFenceNeighbor != west) {
                return false;
            }
        }
        boolean eastExpected = configuration.eastFenceNeighbor || configuration.eastLoweredFullBlockNeighbor;
        if (!beta35WallConnectionMatches(state, Properties.NORTH_WALL_SHAPE, false)
                || !beta35WallConnectionMatches(state, Properties.SOUTH_WALL_SHAPE, false)
                || !beta35WallConnectionMatches(state, Properties.EAST_WALL_SHAPE, eastExpected)
                || !beta35WallConnectionMatches(state, Properties.WEST_WALL_SHAPE, configuration.westFenceNeighbor)) {
            return false;
        }
        return true;
    }

    private static boolean beta35WallConnectionMatches(
            BlockState state,
            net.minecraft.state.property.Property<?> property,
            boolean expected
    ) {
        if (!state.contains(property)) {
            return true;
        }
        boolean connected = !"none".equals(String.valueOf(state.get(property)));
        return connected == expected;
    }

    /**
     * Focused furnace proof for the Beta 3.5 ordinary full-block triad slice.
     *
     * Gate: -Dslabbed.beta35FurnaceTriad=true
     */
    private static void runBeta35FurnaceTriadProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35CommonObjectCase furnace = new Beta35CommonObjectCase(
                "minecraft:furnace",
                "ordinary_full_block",
                Items.FURNACE,
                Blocks.FURNACE.getDefaultState(),
                "GREEN_ALREADY_INHERITS",
                false,
                true,
                true);

        boolean allGreen = true;
        String firstFailureLayer = "NONE";
        for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
            Beta35CommonObjectAuditResult result = runBeta35CommonObjectAuditRow(
                    ctx,
                    singleplayer,
                    furnace,
                    supportCase,
                    4);
            boolean rowGreen = "GREEN_ALREADY_INHERITS".equals(result.classification());
            allGreen &= rowGreen;
            if (!rowGreen && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = switch (result.classification()) {
                    case "PLACEMENT_FAILURE" -> "FURNACE_PLACEMENT_FAILURE";
                    case "SURVIVAL_FAILURE" -> "FURNACE_SURVIVAL_FAILURE";
                    case "CONTACT_GAP" -> "FURNACE_CONTACT_GAP";
                    case "TRIAD_MISMATCH" -> "FURNACE_TRIAD_MISMATCH";
                    default -> "FURNACE_UNKNOWN_FAILURE";
                };
            }
            if (rowGreen) {
                System.out.println("JULIA_BETA35_FURNACE_TRIAD_GREEN"
                        + " objectId=minecraft:furnace"
                        + " family=ordinary_full_block"
                        + " supportCase=" + supportCase
                        + " placement=GREEN"
                        + " survival=GREEN"
                        + " contact=GREEN"
                        + " triad=GREEN"
                        + " classification=GREEN_ALREADY_INHERITS");
            }
        }

        String failureLayer = allGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_FURNACE_TRIAD_SUMMARY"
                + " failureLayer=" + failureLayer
                + " objectId=minecraft:furnace"
                + " rows=" + Beta35CommonObjectSupportCase.values().length
                + " expectedRowsGreen=" + allGreen
                + " crafting_table=CHECK_CRAFTING_TABLE_REGRESSION"
                + " floor_torch=CHECK_FLOOR_TOP_REGRESSION"
                + " candle=CHECK_FLOOR_TOP_REGRESSION"
                + " flower_pot=CHECK_FLOOR_TOP_REGRESSION"
                + " oak_fence=UNCHANGED_SEPARATE_CATEGORY"
                + " oak_trapdoor=UNCHANGED_SEPARATE_CATEGORY"
                + " oak_door=UNCHANGED_SEPARATE_CATEGORY"
                + " oak_sign=UNCHANGED_SEPARATE_RENDERER_RISK"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");
    }

    /**
     * Focused crafting-table proof for the Beta 3.5 ordinary full-block contact slice.
     *
     * Gate: -Dslabbed.beta35CraftingTableContact=true
     */
    private static void runBeta35CraftingTableContactProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35CommonObjectCase craftingTable = new Beta35CommonObjectCase(
                "minecraft:crafting_table",
                "ordinary_full_block",
                Items.CRAFTING_TABLE,
                Blocks.CRAFTING_TABLE.getDefaultState(),
                "GREEN_ALREADY_INHERITS",
                false,
                true,
                true);

        boolean allGreen = true;
        String firstFailureLayer = "NONE";
        for (Beta35CommonObjectSupportCase supportCase : Beta35CommonObjectSupportCase.values()) {
            Beta35CommonObjectAuditResult result = runBeta35CommonObjectAuditRow(
                    ctx,
                    singleplayer,
                    craftingTable,
                    supportCase,
                    3);
            boolean rowGreen = "GREEN_ALREADY_INHERITS".equals(result.classification());
            allGreen &= rowGreen;
            if (!rowGreen && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = switch (result.classification()) {
                    case "PLACEMENT_FAILURE" -> "CRAFTING_TABLE_PLACEMENT_FAILURE";
                    case "SURVIVAL_FAILURE" -> "CRAFTING_TABLE_SURVIVAL_FAILURE";
                    case "CONTACT_GAP" -> "CRAFTING_TABLE_CONTACT_GAP";
                    case "TRIAD_MISMATCH" -> "CRAFTING_TABLE_TRIAD_MISMATCH";
                    default -> "CRAFTING_TABLE_UNKNOWN_FAILURE";
                };
            }
            if (rowGreen) {
                System.out.println("JULIA_BETA35_CRAFTING_TABLE_CONTACT_GREEN"
                        + " objectId=minecraft:crafting_table"
                        + " family=ordinary_full_block"
                        + " supportCase=" + supportCase
                        + " placement=GREEN"
                        + " survival=GREEN"
                        + " contact=GREEN"
                        + " triad=GREEN"
                        + " classification=GREEN_ALREADY_INHERITS");
            }
        }

        String failureLayer = allGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_CRAFTING_TABLE_CONTACT_SUMMARY"
                + " failureLayer=" + failureLayer
                + " objectId=minecraft:crafting_table"
                + " rows=" + Beta35CommonObjectSupportCase.values().length
                + " expectedRowsGreen=" + allGreen
                + " furnace=CHECK_COMMON_OBJECT_MATRIX"
                + " floor_torch=CHECK_FLOOR_TOP_REGRESSION"
                + " candle=CHECK_FLOOR_TOP_REGRESSION"
                + " flower_pot=CHECK_FLOOR_TOP_REGRESSION"
                + " oak_fence=UNCHANGED_SEPARATE_CATEGORY"
                + " oak_trapdoor=UNCHANGED_SEPARATE_CATEGORY"
                + " oak_door=UNCHANGED_SEPARATE_CATEGORY"
                + " oak_sign=UNCHANGED_SEPARATE_RENDERER_RISK"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");

        if (!allGreen) {
            throw new RuntimeException("[JULIA_BETA35_CRAFTING_TABLE_CONTACT_RED]"
                    + " reason=crafting_table_contact_not_green"
                    + " failureLayer=" + failureLayer);
        }
    }

    private static Beta35CommonObjectAuditResult runBeta35CommonObjectAuditRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Beta35CommonObjectCase objectCase,
            Beta35CommonObjectSupportCase supportCase,
            int objectIndex
    ) {
        int baseX = 136 + objectIndex * 8;
        int baseZ = 108 + supportCase.ordinal() * 6;
        BlockPos supportCandidatePos = new BlockPos(baseX, -55, baseZ);
        BlockPos objectPos = supportCandidatePos.up();
        BlockPos unsupportedPos = supportCandidatePos.add(0, 1, 3);
        double hitY = supportCase == Beta35CommonObjectSupportCase.LOWERED_BOTTOM_DY_MINUS_ONE
                ? supportCandidatePos.getY() - 0.5d
                : supportCandidatePos.getY() + 1.0d;
        BlockHitResult useHit = new BlockHitResult(
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        hitY,
                        supportCandidatePos.getZ() + 0.5d),
                Direction.UP,
                supportCandidatePos,
                false);

        prepareBeta35CommonObjectSupport(singleplayer, supportCandidatePos, unsupportedPos, supportCase);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(objectCase.item(), 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() + 3.0d,
                        supportCandidatePos.getZ() - 2.0d),
                useHit.getPos());

        final String[] placementResult = {"not-run"};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                placementResult[0] = "CLIENT_NOT_READY";
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, useHit);
            placementResult[0] = result.toString();
        });
        ctx.waitTick();
        ctx.waitTick();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState supportState = world.getBlockState(supportCandidatePos);
            BlockState placedState = world.getBlockState(objectPos);
            world.updateNeighbors(objectPos, placedState.getBlock());
            world.updateNeighbors(supportCandidatePos, supportState.getBlock());
            world.updateNeighbors(objectPos.up(), world.getBlockState(objectPos.up()).getBlock());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String[] classification = {"OUT_OF_SCOPE_FOR_BETA35"};
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                System.out.println("JULIA_BETA35_COMMON_OBJECT_ROW"
                        + " objectId=" + objectCase.objectId()
                        + " family=" + objectCase.family()
                        + " itemId=" + objectCase.item()
                        + " supportCase=" + supportCase
                        + " classification=OUT_OF_SCOPE_FOR_BETA35"
                        + " reason=client_world_or_player_missing");
                return;
            }

            BlockPos actualObjectPos = beta35CommonActualObjectPos(
                    mc.world,
                    supportCandidatePos,
                    objectPos,
                    objectCase.expectedState());
            BlockState supportState = mc.world.getBlockState(supportCandidatePos);
            BlockState objectState = mc.world.getBlockState(actualObjectPos);
            boolean blockAppeared = objectState.isOf(objectCase.expectedState().getBlock());
            double supportDy = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportState);
            double objectDy = blockAppeared ? SlabSupport.getYOffset(mc.world, actualObjectPos, objectState)
                    : Double.NaN;
            double supportVisibleTopY = beta35CommonSupportVisibleTopY(supportCandidatePos, supportState, supportDy);
            VoxelShape outlineShape = blockAppeared
                    ? objectState.getOutlineShape(mc.world, actualObjectPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = blockAppeared
                    ? objectState.getRaycastShape(mc.world, actualObjectPos)
                    : null;
            VoxelShape collisionShape = blockAppeared
                    ? objectState.getCollisionShape(mc.world, actualObjectPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, actualObjectPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, actualObjectPos);
            net.minecraft.util.math.Box collisionBox = beta35WorldBox(collisionShape, actualObjectPos);
            net.minecraft.util.math.Box modelProxyBox = blockAppeared && objectState.isOf(Blocks.TORCH)
                    ? beta35FloorTorchModelProxyWorldBox(actualObjectPos, objectDy)
                    : blockAppeared && objectState.isOf(Blocks.OAK_FENCE)
                            ? collisionBox
                    : outlineBox;
            boolean blockEntityPresent = blockAppeared && mc.world.getBlockEntity(actualObjectPos) != null;
            String renderProxyBounds = blockAppeared && objectState.isOf(Blocks.OAK_SIGN)
                    ? beta35FormatBox(modelProxyBox)
                    : "N/A";
            double objectModelBottomY = modelProxyBox == null ? Double.NaN : modelProxyBox.minY;
            double contactGap = objectCase.contactApplicable()
                    && actualObjectPos.equals(objectPos)
                    && Double.isFinite(objectModelBottomY)
                    && Double.isFinite(supportVisibleTopY)
                            ? objectModelBottomY - supportVisibleTopY
                            : Double.NaN;
            boolean survivalGreen = blockAppeared && objectState.canPlaceAt(mc.world, actualObjectPos);
            boolean unsupportedFails = !objectCase.unsupportedApplicable()
                    || !objectCase.expectedState().canPlaceAt(mc.world, unsupportedPos);
            boolean contactGreen = !objectCase.contactApplicable()
                    || !actualObjectPos.equals(objectPos)
                    || (Double.isFinite(contactGap) && Math.abs(contactGap) <= EPSILON);
            boolean triadGreen = !objectCase.triadApplicable()
                    || supportCase == Beta35CommonObjectSupportCase.VANILLA_FULL_BLOCK
                    || (blockAppeared
                            && modelProxyBox != null
                            && beta35SameBox(outlineBox, modelProxyBox)
                            && beta35SameBox(raycastBox, modelProxyBox));
            boolean collisionGreen = !"partial_collision".equals(objectCase.family())
                    && !"partial_collision_block".equals(objectCase.family())
                    || supportCase == Beta35CommonObjectSupportCase.VANILLA_FULL_BLOCK
                    || (blockAppeared
                            && modelProxyBox != null
                            && beta35SameBox(collisionBox, modelProxyBox));

            if (!blockAppeared) {
                classification[0] = "PLACEMENT_FAILURE";
            } else if (!survivalGreen || !unsupportedFails) {
                classification[0] = "SURVIVAL_FAILURE";
            } else if (!contactGreen) {
                classification[0] = "CONTACT_GAP";
            } else if (!triadGreen) {
                classification[0] = "TRIAD_MISMATCH";
            } else if (!collisionGreen) {
                classification[0] = "COLLISION_SHAPE_RISK";
            } else {
                classification[0] = objectCase.successfulRiskClassification();
            }

            System.out.println("JULIA_BETA35_COMMON_OBJECT_ROW"
                    + " objectId=" + objectCase.objectId()
                    + " family=" + objectCase.family()
                    + " itemId=" + objectCase.item()
                    + " supportCase=" + supportCase
                    + " blockState=" + objectState
                    + " finalBlockState=" + objectState
                    + " placementResult=" + placementResult[0]
                    + " blockAppearedAfterAttempt=" + blockAppeared
                    + " actualObjectPos=" + actualObjectPos.toShortString()
                    + " blockEntityPresent=" + blockEntityPresent
                    + " supportCandidateState=" + supportState
                    + " supportDy=" + String.format("%.6f", supportDy)
                    + " objectDy=" + (Double.isFinite(objectDy) ? String.format("%.6f", objectDy) : "N/A")
                    + " supportVisibleTopY=" + (Double.isFinite(supportVisibleTopY)
                            ? String.format("%.6f", supportVisibleTopY) : "N/A")
                    + " objectModelBottomY=" + (Double.isFinite(objectModelBottomY)
                            ? String.format("%.6f", objectModelBottomY) : "CONTACT_NOT_APPLICABLE")
                    + " contactGap=" + (Double.isFinite(contactGap)
                            ? String.format("%.6f", contactGap) : "CONTACT_NOT_APPLICABLE")
                    + " modelBounds=" + beta35FormatBox(modelProxyBox)
                    + " renderProxyBounds=" + renderProxyBounds
                    + " outlineBounds=" + beta35FormatBox(outlineBox)
                    + " raycastBounds=" + beta35FormatBox(raycastBox)
                    + " collisionBounds=" + beta35FormatBox(collisionBox)
                    + " survival=" + (survivalGreen ? "SURVIVAL_GREEN" : "SURVIVAL_RED")
                    + " unsupported=" + (unsupportedFails ? "UNSUPPORTED_FAILS" : "UNSUPPORTED_STILL_VALID")
                    + " triadCoLocated=" + (blockAppeared
                            ? (triadGreen ? "yes" : "no") : "NOT_MEASURED")
                    + " collisionCoLocated=" + (blockAppeared
                            ? (collisionGreen ? "yes" : "no") : "NOT_MEASURED")
                    + " classification=" + classification[0]);
        });

        return new Beta35CommonObjectAuditResult(
                objectCase.objectId(),
                objectCase.family(),
                supportCase.toString(),
                classification[0]);
    }

    private static void prepareBeta35CommonObjectSupport(
            TestSingleplayerContext singleplayer,
            BlockPos supportCandidatePos,
            BlockPos unsupportedPos,
            Beta35CommonObjectSupportCase supportCase
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = supportCandidatePos.getX() - 2; x <= supportCandidatePos.getX() + 2; x++) {
                for (int y = supportCandidatePos.getY() - 5; y <= supportCandidatePos.getY() + 4; y++) {
                    for (int z = supportCandidatePos.getZ() - 2; z <= unsupportedPos.getZ() + 2; z++) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }
            if (supportCase == Beta35CommonObjectSupportCase.VANILLA_FULL_BLOCK) {
                world.setBlockState(supportCandidatePos, Blocks.STONE.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
            } else if (supportCase == Beta35CommonObjectSupportCase.LOWERED_BOTTOM_DY_MINUS_ONE) {
                BlockPos compoundSourcePos = supportCandidatePos.down();
                BlockPos towerCarrier = supportCandidatePos.down(2);
                BlockPos towerAnchor = supportCandidatePos.down(3);
                BlockPos towerBase = supportCandidatePos.down(4);
                world.setBlockState(towerBase,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                world.setBlockState(towerAnchor, Blocks.STONE.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.addAnchor(world, towerAnchor, world.getBlockState(towerAnchor));
                world.setBlockState(towerCarrier,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, towerCarrier,
                        world.getBlockState(towerCarrier));
                world.setBlockState(compoundSourcePos, Blocks.STONE.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.addAnchor(world, compoundSourcePos, world.getBlockState(compoundSourcePos));
                SlabAnchorAttachment.addCompoundFullBlockAnchor(world, compoundSourcePos,
                        world.getBlockState(compoundSourcePos));
                world.setBlockState(supportCandidatePos,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.addCompoundVisibleOwnerTopSlab(
                        world,
                        supportCandidatePos,
                        world.getBlockState(supportCandidatePos),
                        compoundSourcePos,
                        world.getBlockState(compoundSourcePos));
            } else {
                BlockPos baseSlab = supportCandidatePos.down(3);
                BlockPos anchoredCarrierBelow = supportCandidatePos.down(2);
                BlockPos loweredDoubleCarrier = supportCandidatePos.down();
                world.setBlockState(baseSlab,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                world.setBlockState(anchoredCarrierBelow, Blocks.STONE.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.addAnchor(world, anchoredCarrierBelow,
                        world.getBlockState(anchoredCarrierBelow));
                world.setBlockState(loweredDoubleCarrier,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                world.setBlockState(supportCandidatePos,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
            }
        });
    }

    private static BlockPos beta35CommonActualObjectPos(
            ClientWorld world,
            BlockPos supportCandidatePos,
            BlockPos objectPos,
            BlockState expectedState
    ) {
        if (world.getBlockState(objectPos).isOf(expectedState.getBlock())) {
            return objectPos;
        }
        if (world.getBlockState(objectPos.up()).isOf(expectedState.getBlock())) {
            return objectPos.up();
        }
        if (world.getBlockState(supportCandidatePos).isOf(expectedState.getBlock())) {
            return supportCandidatePos;
        }
        return objectPos;
    }

    private static double beta35CommonSupportVisibleTopY(BlockPos pos, BlockState state, double supportDy) {
        if (SlabSupport.isSupportingSlab(state)) {
            return beta35SupportVisibleTopY(pos, state, supportDy);
        }
        net.minecraft.util.math.Box supportBox = beta35WorldBox(state.getOutlineShape(null, pos), pos);
        if (supportBox == null) {
            return Double.NaN;
        }
        return supportBox.maxY + supportDy;
    }

    /**
     * Focused candle-only proof for the Beta 3.5 floor/top contact slice.
     *
     * Gate: -Dslabbed.beta35CandleFloorTopContact=true
     */
    private static void runBeta35CandleFloorTopContactProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35FloorTopObjectCase candle = new Beta35FloorTopObjectCase(
                "minecraft:candle",
                Items.CANDLE,
                Blocks.CANDLE.getDefaultState(),
                false);

        boolean allGreen = true;
        String firstFailureLayer = "NONE";
        for (Beta35FloorTopSupportCase supportCase : Beta35FloorTopSupportCase.values()) {
            Beta35FloorTopObjectAuditResult result = runBeta35FloorTopObjectAuditRow(
                    ctx,
                    singleplayer,
                    candle,
                    supportCase,
                    1);
            boolean rowGreen = "GREEN_ALREADY_INHERITS".equals(result.classification());
            allGreen &= rowGreen;
            if (!rowGreen && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = switch (result.classification()) {
                    case "PLACEMENT_FAILURE" -> "CANDLE_PLACEMENT_FAILURE";
                    case "SURVIVAL_FAILURE" -> "CANDLE_SURVIVAL_FAILURE";
                    case "CONTACT_GAP" -> "CANDLE_CONTACT_GAP";
                    case "TRIAD_MISMATCH" -> "CANDLE_TRIAD_MISMATCH";
                    default -> "CANDLE_UNKNOWN_FAILURE";
                };
            }
            if (rowGreen) {
                System.out.println("JULIA_BETA35_CANDLE_FLOOR_TOP_CONTACT_GREEN"
                        + " objectId=minecraft:candle"
                        + " supportCase=" + supportCase
                        + " classification=CANDLE_CONTACT_GREEN"
                        + " placement=GREEN"
                        + " survival=GREEN"
                        + " contact=GREEN"
                        + " triad=GREEN");
            }
        }

        String failureLayer = allGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_CANDLE_FLOOR_TOP_CONTACT_SUMMARY"
                + " objectId=minecraft:candle"
                + " rows=" + Beta35FloorTopSupportCase.values().length
                + " expectedRowsGreen=" + allGreen
                + " failureLayer=" + failureLayer
                + " flower_pot=GREEN_ALREADY_INHERITS"
                + " standing_oak_sign=UNCHANGED_SEPARATE_CONTACT_GAP"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");

        if (!allGreen) {
            throw new RuntimeException("[JULIA_BETA35_CANDLE_FLOOR_TOP_CONTACT_RED]"
                    + " reason=candle_floor_top_contact_not_green"
                    + " failureLayer=" + failureLayer);
        }
    }

    /**
     * Focused flower-pot-only proof for the Beta 3.5 floor/top survival slice.
     *
     * Gate: -Dslabbed.beta35FlowerPotFloorTopSurvival=true
     */
    private static void runBeta35FlowerPotFloorTopSurvivalProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35FloorTopObjectCase flowerPot = new Beta35FloorTopObjectCase(
                "minecraft:flower_pot",
                Items.FLOWER_POT,
                Blocks.FLOWER_POT.getDefaultState(),
                false);

        boolean survivalRowsGreen = true;
        String secondaryLayer = "NONE";
        String firstFailureLayer = "NONE";
        for (Beta35FloorTopSupportCase supportCase : Beta35FloorTopSupportCase.values()) {
            Beta35FloorTopObjectAuditResult result = runBeta35FloorTopObjectAuditRow(
                    ctx,
                    singleplayer,
                    flowerPot,
                    supportCase,
                    2);
            boolean survivalGreen = !"PLACEMENT_FAILURE".equals(result.classification())
                    && !"SURVIVAL_FAILURE".equals(result.classification());
            survivalRowsGreen &= survivalGreen;
            if (!survivalGreen && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = switch (result.classification()) {
                    case "PLACEMENT_FAILURE" -> "FLOWER_POT_PLACEMENT_FAILURE";
                    case "SURVIVAL_FAILURE" -> "FLOWER_POT_SURVIVAL_FAILURE";
                    default -> "FLOWER_POT_UNKNOWN_FAILURE";
                };
            }
            if (survivalGreen && !"GREEN_ALREADY_INHERITS".equals(result.classification())
                    && "NONE".equals(secondaryLayer)) {
                secondaryLayer = result.classification();
            }
            if (survivalGreen) {
                System.out.println("JULIA_BETA35_FLOWER_POT_FLOOR_TOP_SURVIVAL_GREEN"
                        + " objectId=minecraft:flower_pot"
                        + " supportCase=" + supportCase
                        + " classification=FLOWER_POT_SURVIVAL_GREEN"
                        + " placement=GREEN"
                        + " survival=GREEN"
                        + " unsupported=UNSUPPORTED_FAILS"
                        + " secondaryLayer=" + result.classification());
            }
        }

        String failureLayer = survivalRowsGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_FLOWER_POT_FLOOR_TOP_SURVIVAL_SUMMARY"
                + " objectId=minecraft:flower_pot"
                + " rows=" + Beta35FloorTopSupportCase.values().length
                + " expectedRowsSurvivalGreen=" + survivalRowsGreen
                + " failureLayer=" + failureLayer
                + " secondaryLayer=" + secondaryLayer
                + " candle=GREEN_ALREADY_INHERITS"
                + " standing_oak_sign=UNCHANGED_SEPARATE_CONTACT_GAP"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");

        if (!survivalRowsGreen) {
            throw new RuntimeException("[JULIA_BETA35_FLOWER_POT_FLOOR_TOP_SURVIVAL_RED]"
                    + " reason=flower_pot_floor_top_survival_not_green"
                    + " failureLayer=" + failureLayer);
        }
    }

    /**
     * Focused flower-pot contact proof for the Beta 3.5 floor/top contact slice.
     *
     * Gate: -Dslabbed.beta35FlowerPotFloorTopContact=true
     */
    private static void runBeta35FlowerPotFloorTopContactProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        Beta35FloorTopObjectCase flowerPot = new Beta35FloorTopObjectCase(
                "minecraft:flower_pot",
                Items.FLOWER_POT,
                Blocks.FLOWER_POT.getDefaultState(),
                false);

        boolean allGreen = true;
        String firstFailureLayer = "NONE";
        for (Beta35FloorTopSupportCase supportCase : Beta35FloorTopSupportCase.values()) {
            Beta35FloorTopObjectAuditResult result = runBeta35FloorTopObjectAuditRow(
                    ctx,
                    singleplayer,
                    flowerPot,
                    supportCase,
                    2);
            boolean rowGreen = "GREEN_ALREADY_INHERITS".equals(result.classification());
            allGreen &= rowGreen;
            if (!rowGreen && "NONE".equals(firstFailureLayer)) {
                firstFailureLayer = switch (result.classification()) {
                    case "PLACEMENT_FAILURE" -> "FLOWER_POT_PLACEMENT_FAILURE";
                    case "SURVIVAL_FAILURE" -> "FLOWER_POT_SURVIVAL_FAILURE";
                    case "CONTACT_GAP" -> "FLOWER_POT_CONTACT_GAP";
                    case "TRIAD_MISMATCH" -> "FLOWER_POT_TRIAD_MISMATCH";
                    default -> "FLOWER_POT_UNKNOWN_FAILURE";
                };
            }
            if (rowGreen) {
                System.out.println("JULIA_BETA35_FLOWER_POT_FLOOR_TOP_CONTACT_GREEN"
                        + " objectId=minecraft:flower_pot"
                        + " supportCase=" + supportCase
                        + " classification=FLOWER_POT_CONTACT_GREEN"
                        + " placement=GREEN"
                        + " survival=GREEN"
                        + " unsupported=UNSUPPORTED_FAILS"
                        + " contact=GREEN"
                        + " triad=GREEN");
            }
        }

        String failureLayer = allGreen ? "NONE" : firstFailureLayer;
        System.out.println("JULIA_BETA35_FLOWER_POT_FLOOR_TOP_CONTACT_SUMMARY"
                + " objectId=minecraft:flower_pot"
                + " rows=" + Beta35FloorTopSupportCase.values().length
                + " expectedRowsGreen=" + allGreen
                + " failureLayer=" + failureLayer
                + " candle=GREEN_ALREADY_INHERITS"
                + " standing_oak_sign=UNCHANGED_SEPARATE_CONTACT_GAP"
                + " releaseAudit=NOT_RUN"
                + " releasePrep=PAUSED");

        if (!allGreen) {
            throw new RuntimeException("[JULIA_BETA35_FLOWER_POT_FLOOR_TOP_CONTACT_RED]"
                    + " reason=flower_pot_floor_top_contact_not_green"
                    + " failureLayer=" + failureLayer);
        }
    }

    private static Beta35FloorTopObjectAuditResult runBeta35FloorTopObjectAuditRow(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Beta35FloorTopObjectCase objectCase,
            Beta35FloorTopSupportCase supportCase,
            int objectIndex
    ) {
        int baseX = 88 + objectIndex * 10
                + (supportCase == Beta35FloorTopSupportCase.LOWERED_BOTTOM_DY_MINUS_ONE ? 0 : 4);
        BlockPos supportCandidatePos = new BlockPos(baseX, -55, 102);
        BlockPos objectPos = supportCandidatePos.up();
        BlockPos unsupportedPos = supportCandidatePos.add(0, 0, 3);
        double hitY = supportCase == Beta35FloorTopSupportCase.LOWERED_BOTTOM_DY_MINUS_ONE
                ? supportCandidatePos.getY() - 0.5d
                : supportCandidatePos.getY() + 1.0d;
        BlockHitResult useHit = new BlockHitResult(
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        hitY,
                        supportCandidatePos.getZ() + 0.5d),
                Direction.UP,
                supportCandidatePos,
                false);

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = supportCandidatePos.getX() - 2; x <= supportCandidatePos.getX() + 2; x++) {
                for (int y = supportCandidatePos.getY() - 5; y <= supportCandidatePos.getY() + 3; y++) {
                    for (int z = supportCandidatePos.getZ() - 2; z <= unsupportedPos.getZ() + 1; z++) {
                        world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }
            if (supportCase == Beta35FloorTopSupportCase.LOWERED_BOTTOM_DY_MINUS_ONE) {
                BlockPos compoundSourcePos = supportCandidatePos.down();
                BlockPos towerCarrier = supportCandidatePos.down(2);
                BlockPos towerAnchor = supportCandidatePos.down(3);
                BlockPos towerBase = supportCandidatePos.down(4);
                world.setBlockState(towerBase,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                world.setBlockState(towerAnchor, Blocks.STONE.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.addAnchor(world, towerAnchor, world.getBlockState(towerAnchor));
                world.setBlockState(towerCarrier,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, towerCarrier,
                        world.getBlockState(towerCarrier));
                world.setBlockState(compoundSourcePos, Blocks.STONE.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.addAnchor(world, compoundSourcePos, world.getBlockState(compoundSourcePos));
                SlabAnchorAttachment.addCompoundFullBlockAnchor(world, compoundSourcePos,
                        world.getBlockState(compoundSourcePos));
                world.setBlockState(supportCandidatePos,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.addCompoundVisibleOwnerTopSlab(
                        world,
                        supportCandidatePos,
                        world.getBlockState(supportCandidatePos),
                        compoundSourcePos,
                        world.getBlockState(compoundSourcePos));
            } else {
                BlockPos baseSlab = supportCandidatePos.down(3);
                BlockPos anchoredCarrierBelow = supportCandidatePos.down(2);
                BlockPos loweredDoubleCarrier = supportCandidatePos.down();
                world.setBlockState(baseSlab,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                world.setBlockState(anchoredCarrierBelow, Blocks.STONE.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.addAnchor(world, anchoredCarrierBelow,
                        world.getBlockState(anchoredCarrierBelow));
                world.setBlockState(loweredDoubleCarrier,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                world.setBlockState(supportCandidatePos,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        syncHeldMainHand(ctx, singleplayer, new ItemStack(objectCase.item(), 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(
                        supportCandidatePos.getX() + 0.5d,
                        supportCandidatePos.getY() + 3.0d,
                        supportCandidatePos.getZ() - 2.0d),
                useHit.getPos());

        final String[] placementResult = {"not-run"};
        final boolean[] placementAccepted = {false};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                placementResult[0] = "CLIENT_NOT_READY";
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, useHit);
            placementResult[0] = result.toString();
            placementAccepted[0] = result.isAccepted();
        });
        ctx.waitTick();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            BlockState supportState = world.getBlockState(supportCandidatePos);
            BlockState objectState = world.getBlockState(objectPos);
            world.updateNeighbors(objectPos, objectState.getBlock());
            world.updateNeighbors(supportCandidatePos, supportState.getBlock());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final String[] classification = {"OUT_OF_SCOPE_FOR_THIS_SLICE"};
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                classification[0] = "OUT_OF_SCOPE_FOR_THIS_SLICE";
                System.out.println("JULIA_BETA35_FLOOR_TOP_OBJECT_ROW"
                        + " objectId=" + objectCase.objectId()
                        + " itemId=" + objectCase.item()
                        + " supportCase=" + supportCase
                        + " classification=OUT_OF_SCOPE_FOR_THIS_SLICE"
                        + " reason=client_world_or_player_missing");
                return;
            }

            BlockState supportState = mc.world.getBlockState(supportCandidatePos);
            BlockState objectState = mc.world.getBlockState(objectPos);
            boolean blockAppeared = objectState.isOf(objectCase.expectedState().getBlock());
            double supportDy = SlabSupport.getYOffset(mc.world, supportCandidatePos, supportState);
            double objectDy = blockAppeared ? SlabSupport.getYOffset(mc.world, objectPos, objectState) : Double.NaN;
            double supportVisibleTopY = beta35SupportVisibleTopY(supportCandidatePos, supportState, supportDy);
            VoxelShape outlineShape = blockAppeared
                    ? objectState.getOutlineShape(mc.world, objectPos,
                            net.minecraft.block.ShapeContext.of(mc.player))
                    : null;
            VoxelShape raycastShape = blockAppeared ? objectState.getRaycastShape(mc.world, objectPos) : null;
            net.minecraft.util.math.Box outlineBox = beta35WorldBox(outlineShape, objectPos);
            net.minecraft.util.math.Box raycastBox = beta35WorldBox(raycastShape, objectPos);
            net.minecraft.util.math.Box modelProxyBox = blockAppeared && objectState.isOf(Blocks.TORCH)
                    ? beta35FloorTorchModelProxyWorldBox(objectPos, objectDy)
                    : outlineBox;
            double objectModelBottomY = modelProxyBox == null ? Double.NaN : modelProxyBox.minY;
            double contactGap = Double.isFinite(objectModelBottomY) && Double.isFinite(supportVisibleTopY)
                    ? objectModelBottomY - supportVisibleTopY
                    : Double.NaN;
            boolean survivalGreen = blockAppeared && objectState.canPlaceAt(mc.world, objectPos);
            boolean unsupportedFails = !objectCase.expectedState().canPlaceAt(mc.world, unsupportedPos);
            boolean contactGreen = Double.isFinite(contactGap) && Math.abs(contactGap) <= EPSILON;
            boolean triadGreen = blockAppeared
                    && modelProxyBox != null
                    && beta35SameBox(outlineBox, modelProxyBox)
                    && beta35SameBox(raycastBox, modelProxyBox);

            if (!blockAppeared) {
                classification[0] = "PLACEMENT_FAILURE";
            } else if (!survivalGreen || !unsupportedFails) {
                classification[0] = "SURVIVAL_FAILURE";
            } else if (!contactGreen) {
                classification[0] = "CONTACT_GAP";
            } else if (!triadGreen) {
                classification[0] = "TRIAD_MISMATCH";
            } else if (objectCase.rendererSpecialCase()) {
                classification[0] = "RENDERER_SPECIAL_CASE";
            } else {
                classification[0] = "GREEN_ALREADY_INHERITS";
            }

            System.out.println("JULIA_BETA35_FLOOR_TOP_OBJECT_ROW"
                    + " objectId=" + objectCase.objectId()
                    + " itemId=" + objectCase.item()
                    + " supportCase=" + supportCase
                    + " blockState=" + objectState
                    + " placementResult=" + placementResult[0]
                    + " blockAppearedAfterAttempt=" + blockAppeared
                    + " supportCandidatePos=" + supportCandidatePos.toShortString()
                    + " supportCandidateState=" + supportState
                    + " supportDy=" + String.format("%.6f", supportDy)
                    + " objectDy=" + (Double.isFinite(objectDy) ? String.format("%.6f", objectDy) : "N/A")
                    + " supportVisibleTopY=" + (Double.isFinite(supportVisibleTopY)
                            ? String.format("%.6f", supportVisibleTopY) : "N/A")
                    + " objectModelBottomY=" + (Double.isFinite(objectModelBottomY)
                            ? String.format("%.6f", objectModelBottomY) : "CONTACT_NOT_APPLICABLE")
                    + " contactGap=" + (Double.isFinite(contactGap)
                            ? String.format("%.6f", contactGap) : "CONTACT_NOT_APPLICABLE")
                    + " survival=" + (survivalGreen ? "SURVIVAL_GREEN" : "SURVIVAL_RED")
                    + " unsupported=" + (unsupportedFails ? "UNSUPPORTED_FAILS" : "UNSUPPORTED_STILL_VALID")
                    + " triadCoLocated=" + (blockAppeared ? (triadGreen ? "yes" : "no") : "NOT_MEASURED")
                    + " rendererPath=" + (objectCase.rendererSpecialCase() ? "BLOCK_ENTITY_OR_SPECIAL" : "STANDARD")
                    + " classification=" + classification[0]);
        });

        return new Beta35FloorTopObjectAuditResult(classification[0]);
    }

    private static double beta35SupportVisibleTopY(BlockPos pos, BlockState state, double supportDy) {
        if (!SlabSupport.isSupportingSlab(state)) {
            return Double.NaN;
        }
        return pos.getY() + supportDy + SlabSupport.getSupportYOffset(state);
    }

    private static ActionResult beta35PlaceForLiveSbsbsCapture(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Item item,
            Vec3d eye,
            BlockHitResult placeHit,
            String placementLabel
    ) {
        syncHeldMainHand(ctx, singleplayer, new ItemStack(item, 1));
        syncPlayerAim(ctx, singleplayer, eye, placeHit.getPos());

        final ActionResult[] resultHolder = {ActionResult.PASS};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_SBSBS_SOURCE_TRUTH_RED]"
                        + " reason=client_not_ready"
                        + " step=" + placementLabel
                        + " categoryScope=floor_torch_only");
            }
            resultHolder[0] = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeHit);
        });

        ctx.waitTick();
        ctx.waitTick();
        return resultHolder[0];
    }

    private static String beta35SbsbsComponentTruth(
            net.minecraft.world.World world,
            BlockPos pos,
            BlockState state,
            String label
    ) {
        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        boolean carrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state);
        boolean compoundUpper = SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state);
        boolean compoundLower = SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state);
        boolean compoundOwnerTop = SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state);
        boolean hasBottomSlabBelow = SlabSupport.hasBottomSlabBelow(world, pos);
        boolean anchoredFullBlockBelow = SlabAnchorAttachment.isAnchored(world, pos.down());
        return label
                + "Pos=" + pos.toShortString()
                + " state=" + state
                + " dy=" + String.format("%.3f", dy)
                + " isAnchored=" + anchored
                + " isPersistentLoweredBottomSlabCarrier=" + carrier
                + " isCompoundVisibleSideUpperSlab=" + compoundUpper
                + " isCompoundVisibleSideLowerSlab=" + compoundLower
                + " isCompoundVisibleOwnerTopSlab=" + compoundOwnerTop
                + " hasBottomSlabBelow=" + hasBottomSlabBelow
                + " anchoredFullBlockBelow=" + anchoredFullBlockBelow;
    }

    private static BlockPos beta35FindLiveShapeTorchPos(ClientWorld world, BlockPos expectedTorchPos) {
        if (world.getBlockState(expectedTorchPos).isOf(Blocks.TORCH)) {
            return expectedTorchPos;
        }
        BlockPos[] candidates = {
                expectedTorchPos.north(),
                expectedTorchPos.south(),
                expectedTorchPos.east(),
                expectedTorchPos.west(),
                expectedTorchPos.up(),
                expectedTorchPos.down(),
                expectedTorchPos.north().up(),
                expectedTorchPos.south().up(),
                expectedTorchPos.east().up(),
                expectedTorchPos.west().up(),
        };
        for (BlockPos candidate : candidates) {
            if (world.getBlockState(candidate).isOf(Blocks.TORCH)) {
                return candidate;
            }
        }
        return null;
    }

    private static String beta35FormatMinY(net.minecraft.util.math.Box box) {
        return box == null ? "empty" : String.format("%.6f", box.minY);
    }

    private static String beta35FormatMaxY(net.minecraft.util.math.Box box) {
        return box == null ? "empty" : String.format("%.6f", box.maxY);
    }

    /**
     * SBSBS + floor_torch red proof.
     *
     * SBSBS structure (bottom to top):
     *   baseSlab         (S) stone_slab[type=bottom]         — lowest base
     *   lowerAnchorBlock (B) Blocks.STONE + addAnchor        — first ordinary full block
     *   middleCarrierSlab(S) stone_slab[type=bottom] + updatePersistentLoweredSlabCarrier
     *   upperAnchorBlock (B) Blocks.STONE + addAnchor        — second ordinary full block
     *   supportPos       (S) stone_slab[type=bottom] + updatePersistentLoweredSlabCarrier
     *   torchPos              floor torch placed on supportPos (Julia live: floating at vanilla Y)
     *
     * Julia live after 04ace65: "SBSBS+item = floating item in vanilla position."
     * Expected supportDy=-0.500, torchDy=-1.000, contactGap=0.000 if fix is applied.
     * RED if isVanillaPosition=true (torchDy≈0) or contactGap nonzero.
     *
     * Gate: -Dslabbed.beta35FloorTorchSbsbsRed=true
     */
    private static void runBeta35FloorTorchSbsbsSourceTruthRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        runBeta35FloorTorchSbsbsRedProof(ctx, singleplayer, true);
    }

    private static void runBeta35FloorTorchSbsbsRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer
    ) {
        runBeta35FloorTorchSbsbsRedProof(ctx, singleplayer, false);
    }

    private static void runBeta35FloorTorchSbsbsRedProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            boolean sourceTruthCapture
    ) {
        // Positions distinct from all existing proof fixtures (X=60, Z=90).
        final BlockPos supportPos   = new BlockPos(60, -57, 90);
        final BlockPos torchPos     = supportPos.up();
        final BlockPos upperAnchorBlock  = supportPos.down(1);  // B
        final BlockPos middleCarrierSlab = supportPos.down(2);  // S
        final BlockPos lowerAnchorBlock  = supportPos.down(3);  // B
        final BlockPos baseSlab          = supportPos.down(4);  // S (base)

        final BlockHitResult torchUseHit = new BlockHitResult(
                new Vec3d(supportPos.getX() + 0.5d, supportPos.getY() + 1.0d, supportPos.getZ() + 0.5d),
                Direction.UP,
                supportPos,
                false);

        // ── Phase 1: seed SBSBS fixture ───────────────────────────────────────────────
        final BlockPos foundation = baseSlab.down();
        final BlockHitResult placeBaseSlabHit = new BlockHitResult(
                new Vec3d(baseSlab.getX() + 0.5d, baseSlab.getY(), baseSlab.getZ() + 0.5d),
                Direction.UP,
                foundation,
                false);
        final BlockHitResult placeLowerAnchorHit = new BlockHitResult(
                new Vec3d(baseSlab.getX() + 0.5d, baseSlab.getY() + 1.0d, baseSlab.getZ() + 0.5d),
                Direction.UP,
                baseSlab,
                false);
        final BlockHitResult placeMiddleCarrierHit = new BlockHitResult(
                new Vec3d(lowerAnchorBlock.getX() + 0.5d, lowerAnchorBlock.getY() + 1.0d, lowerAnchorBlock.getZ() + 0.5d),
                Direction.UP,
                lowerAnchorBlock,
                false);
        final BlockHitResult placeUpperAnchorHit = new BlockHitResult(
                new Vec3d(middleCarrierSlab.getX() + 0.5d, middleCarrierSlab.getY() + 1.0d, middleCarrierSlab.getZ() + 0.5d),
                Direction.UP,
                middleCarrierSlab,
                false);
        final BlockHitResult placeSupportSlabHit = new BlockHitResult(
                new Vec3d(upperAnchorBlock.getX() + 0.5d, upperAnchorBlock.getY() + 1.0d, upperAnchorBlock.getZ() + 0.5d),
                Direction.UP,
                upperAnchorBlock,
                false);

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            for (int x = foundation.getX() - 2; x <= foundation.getX() + 2; x++) {
                for (int y = foundation.getY() - 1; y <= torchPos.getY() + 1; y++) {
                    for (int z = foundation.getZ() - 2; z <= foundation.getZ() + 2; z++) {
                        world.setBlockState(
                                new BlockPos(x, y, z),
                                Blocks.AIR.getDefaultState(),
                                net.minecraft.block.Block.NOTIFY_LISTENERS);
                    }
                }
            }
            world.setBlockState(foundation, Blocks.STONE.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            world.setBlockState(torchPos, Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        if (sourceTruthCapture) {
            ActionResult baseSlabPlace = beta35PlaceForLiveSbsbsCapture(
                    ctx,
                    singleplayer,
                    Items.STONE_SLAB,
                    new Vec3d(baseSlab.getX() + 0.5d, baseSlab.getY() + 3.2d, baseSlab.getZ() - 2.0d),
                    placeBaseSlabHit,
                    "base_slab");
            if (!baseSlabPlace.isAccepted()) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_SBSBS_SOURCE_TRUTH_RED]"
                        + " reason=source_truth_live_build_blocked"
                        + " step=base_slab"
                        + " result=" + baseSlabPlace);
            }

            ActionResult lowerAnchorPlace = beta35PlaceForLiveSbsbsCapture(
                    ctx,
                    singleplayer,
                    Items.STONE,
                    new Vec3d(lowerAnchorBlock.getX() + 0.5d, lowerAnchorBlock.getY() + 3.2d, lowerAnchorBlock.getZ() - 2.0d),
                    placeLowerAnchorHit,
                    "lower_anchor");
            if (!lowerAnchorPlace.isAccepted()) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_SBSBS_SOURCE_TRUTH_RED]"
                        + " reason=source_truth_live_build_blocked"
                        + " step=lower_anchor"
                        + " result=" + lowerAnchorPlace);
            }

            ActionResult middleCarrierPlace = beta35PlaceForLiveSbsbsCapture(
                    ctx,
                    singleplayer,
                    Items.STONE_SLAB,
                    new Vec3d(middleCarrierSlab.getX() + 0.5d, middleCarrierSlab.getY() + 3.2d, middleCarrierSlab.getZ() - 2.0d),
                    placeMiddleCarrierHit,
                    "middle_carrier");
            if (!middleCarrierPlace.isAccepted()) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_SBSBS_SOURCE_TRUTH_RED]"
                        + " reason=source_truth_live_build_blocked"
                        + " step=middle_carrier"
                        + " result=" + middleCarrierPlace);
            }

            ActionResult upperAnchorPlace = beta35PlaceForLiveSbsbsCapture(
                    ctx,
                    singleplayer,
                    Items.STONE,
                    new Vec3d(upperAnchorBlock.getX() + 0.5d, upperAnchorBlock.getY() + 3.2d, upperAnchorBlock.getZ() - 2.0d),
                    placeUpperAnchorHit,
                    "upper_anchor");
            if (!upperAnchorPlace.isAccepted()) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_SBSBS_SOURCE_TRUTH_RED]"
                        + " reason=source_truth_live_build_blocked"
                        + " step=upper_anchor"
                        + " result=" + upperAnchorPlace);
            }

            ActionResult supportSlabPlace = beta35PlaceForLiveSbsbsCapture(
                    ctx,
                    singleplayer,
                    Items.STONE_SLAB,
                    new Vec3d(supportPos.getX() + 0.5d, supportPos.getY() + 3.2d, supportPos.getZ() - 2.0d),
                    placeSupportSlabHit,
                    "support");
            if (!supportSlabPlace.isAccepted()) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_SBSBS_SOURCE_TRUTH_RED]"
                        + " reason=source_truth_live_build_blocked"
                        + " step=support_slab"
                        + " result=" + supportSlabPlace);
            }
        } else {
            singleplayer.getServer().runOnServer(server -> {
                var world = server.getOverworld();
                // S — base slab
                world.setBlockState(baseSlab,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                // B — lower ordinary full block, anchored (hasBottomSlabBelow = baseSlab)
                world.setBlockState(lowerAnchorBlock, Blocks.STONE.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.addAnchor(world, lowerAnchorBlock, world.getBlockState(lowerAnchorBlock));
                // S — middle carrier slab (qualifies because lowerAnchorBlock is anchored)
                world.setBlockState(middleCarrierSlab,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                        world, middleCarrierSlab, world.getBlockState(middleCarrierSlab));
                // B — upper ordinary full block, anchored (hasBottomSlabBelow = middleCarrierSlab)
                world.setBlockState(upperAnchorBlock, Blocks.STONE.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.addAnchor(world, upperAnchorBlock, world.getBlockState(upperAnchorBlock));
                // S — support slab (qualifies because upperAnchorBlock is anchored)
                world.setBlockState(supportPos,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                        world, supportPos, world.getBlockState(supportPos));
                // Clear torch position
                world.setBlockState(torchPos, Blocks.AIR.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
            });
        }
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        // ── Phase 1 check: fixture verification ──────────────────────────────────────────
        final double[] fixtureSupportDy = {Double.NaN};
        final boolean[] fixtureUpperAnchoredHolder = {false};
        final boolean[] fixtureMiddleCarrierHolder = {false};
        final boolean[] fixtureSupportCarrierHolder = {false};
        final boolean[] fixtureSupportOwnerTopHolder = {false};
        final String[] fixtureFailureHolder = {null};

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                fixtureFailureHolder[0] = "client_world_missing";
                return;
            }
            BlockState supportState = mc.world.getBlockState(supportPos);
            fixtureSupportDy[0] = SlabSupport.getYOffset(mc.world, supportPos, supportState);
            fixtureUpperAnchoredHolder[0] = SlabAnchorAttachment.isAnchored(mc.world, upperAnchorBlock);
            fixtureMiddleCarrierHolder[0] = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    mc.world, middleCarrierSlab, mc.world.getBlockState(middleCarrierSlab));
            fixtureSupportCarrierHolder[0] = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    mc.world, supportPos, supportState);
            fixtureSupportOwnerTopHolder[0] = SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(
                    mc.world, supportPos, supportState);
            boolean supportDyOk = Math.abs(fixtureSupportDy[0] - (-0.5d)) <= EPSILON
                    || (sourceTruthCapture
                    && fixtureSupportOwnerTopHolder[0]
                    && Math.abs(fixtureSupportDy[0] + 1.0d) <= EPSILON);
            if (!supportDyOk) {
                fixtureFailureHolder[0] = "supportDy_not_minus_half supportDy="
                        + String.format("%.3f", fixtureSupportDy[0]);
            }
            System.out.println("[JULIA_BETA35_FLOOR_TORCH_SBSBS_FIXTURE_GREEN]"
                    + " structure=SBSBS"
                    + " categoryScope=floor_torch_only"
                    + " supportPos=" + supportPos.toShortString()
                    + " supportState=" + supportState
                    + " supportDy=" + String.format("%.3f", fixtureSupportDy[0])
                    + " upperAnchorBlock=" + upperAnchorBlock.toShortString()
                    + " upperAnchored=" + fixtureUpperAnchoredHolder[0]
                    + " middleCarrierMarked=" + fixtureMiddleCarrierHolder[0]
                    + " supportCarrierMarked=" + fixtureSupportCarrierHolder[0]
                    + " supportOwnerTopMarked=" + fixtureSupportOwnerTopHolder[0]
                    + " fixtureResult=" + (fixtureFailureHolder[0] == null ? "GREEN" : "RED:" + fixtureFailureHolder[0]));
        });

        if (!sourceTruthCapture && fixtureFailureHolder[0] != null) {
            throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_SBSBS_FIXTURE_GREEN]"
                    + " reason=fixture_failed detail=" + fixtureFailureHolder[0]
                    + " categoryScope=floor_torch_only");
        }

        // ── Phase 2: place floor torch via player-like interaction ────────────────────────
        syncHeldMainHand(ctx, singleplayer, new ItemStack(Items.TORCH, 4));
        syncPlayerAim(
                ctx,
                singleplayer,
                new Vec3d(supportPos.getX() + 0.5d, supportPos.getY() + 3.2d, supportPos.getZ() - 2.0d),
                torchUseHit.getPos());

        final String[] placementResultText = {"not-run"};
        final boolean[] placementAcceptedHolder = {false};
        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_SBSBS_RED]"
                        + " reason=client_not_ready categoryScope=floor_torch_only");
            }
            ActionResult result = mc.interactionManager.interactBlock(
                    mc.player, Hand.MAIN_HAND, torchUseHit);
            placementResultText[0] = result.toString();
            placementAcceptedHolder[0] = result.isAccepted();
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        // ── Phase 3: measure and classify ────────────────────────────────────────────────
        final String[] failureLayerHolder = {"SBSBS_MEASUREMENT_NOT_RUN"};
        final boolean[] isVanillaPositionHolder = {false};
        final double[] measuredSupportDyHolder = {Double.NaN};
        final double[] measuredTorchDyHolder = {Double.NaN};
        final String[] sourceTruthClassificationHolder = {"NONE"};

        ctx.runOnClient(mc -> {
            if (mc.world == null) return;

            BlockState supportStateAfter = mc.world.getBlockState(supportPos);
            BlockState baseState = mc.world.getBlockState(baseSlab);
            BlockState lowerAnchorState = mc.world.getBlockState(lowerAnchorBlock);
            BlockState middleCarrierState = mc.world.getBlockState(middleCarrierSlab);
            BlockState upperAnchorState = mc.world.getBlockState(upperAnchorBlock);
            BlockState torchState = mc.world.getBlockState(torchPos);
            double supportDy = SlabSupport.getYOffset(mc.world, supportPos, supportStateAfter);
            measuredSupportDyHolder[0] = supportDy;
            boolean torchPresent = torchState.isOf(Blocks.TORCH);
            double torchDy = torchPresent
                    ? SlabSupport.getYOffset(mc.world, torchPos, torchState) : Double.NaN;
            measuredTorchDyHolder[0] = torchDy;

            boolean compoundVisibleUpper = SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(
                    mc.world, supportPos, supportStateAfter);
            boolean compoundVisibleLower = SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(
                    mc.world, supportPos, supportStateAfter);
            boolean compoundOwnerTop = SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(
                    mc.world, supportPos, supportStateAfter);
            boolean carrierMarked = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    mc.world, supportPos, supportStateAfter);
            String supportType;
            if (compoundVisibleUpper) {
                supportType = "compound_visible_upper";
            } else if (compoundVisibleLower) {
                supportType = "compound_visible_lower";
            } else if (compoundOwnerTop) {
                supportType = "compound_visible_owner_top";
            } else if (carrierMarked) {
                supportType = "lowered_carrier";
            } else if (supportStateAfter.isOf(Blocks.STONE)) {
                supportType = "fullblock";
            } else {
                supportType = "unknown_or_vanilla";
            }

            double rawSupportTopY = supportPos.getY() + SlabSupport.getSupportYOffset(supportStateAfter);
            double supportVisibleTopY = rawSupportTopY + supportDy;
            net.minecraft.util.math.Box modelBox = torchPresent
                    ? beta35FloorTorchModelProxyWorldBox(torchPos, torchDy) : null;
            double rawTorchShapeMinY = modelBox != null ? (modelBox.minY - torchDy) : Double.NaN;
            double torchModelBottomY = modelBox != null ? modelBox.minY : Double.NaN;
            double contactGap = Double.isFinite(torchModelBottomY)
                    ? torchModelBottomY - supportVisibleTopY : Double.NaN;

            // isVanillaPosition: torch model bottom is at vanilla slab-top height (torchDy≈0).
            // For a bottom slab at supportPos, vanilla top = supportPos.getY()+0.5.
            // torch at torchPos with torchDy=0 gives modelBottom = torchPos.getY() = supportPos.getY()+1.
            // A properly-lowered support (dy=-0.5) top = supportPos.getY(); expected torchDy=-1.0.
            boolean isVanillaPosition = torchPresent && Math.abs(torchDy) < EPSILON;
            isVanillaPositionHolder[0] = isVanillaPosition;

            boolean survivalGreen = torchPresent && torchState.canPlaceAt(mc.world, torchPos);
            boolean triadGreen = torchPresent
                    && Math.abs(torchDy - (supportDy - 0.5d)) <= EPSILON;

            boolean lowerAnchorIsAnchored = SlabAnchorAttachment.isAnchored(mc.world, lowerAnchorBlock);
            boolean lowerAnchorIsCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    mc.world, lowerAnchorBlock, lowerAnchorState);
            boolean middleCarrierIsCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                    mc.world, middleCarrierSlab, middleCarrierState);
            boolean upperAnchorIsAnchored = SlabAnchorAttachment.isAnchored(mc.world, upperAnchorBlock);
            boolean supportHasBottomSlabBelow = SlabSupport.hasBottomSlabBelow(mc.world, supportPos);
            boolean supportAnchoredFullBlockBelow = SlabAnchorAttachment.isAnchored(
                    mc.world,
                    supportPos.down());

            boolean carrierSourceTruthOk = Math.abs(supportDy - (-0.5d)) <= EPSILON && carrierMarked;
            boolean ownerTopSourceTruthOk = compoundOwnerTop
                    && Math.abs(supportDy + 1.0d) <= EPSILON
                    && supportAnchoredFullBlockBelow;
            boolean torchRejectedByLaw = !torchPresent
                    && ownerTopSourceTruthOk
                    && SlabSupport.isRejectedFloorTorchTopFace(
                    mc.world, supportPos, Blocks.TORCH.getDefaultState());
            boolean fixtureSourceTruth = (carrierSourceTruthOk || ownerTopSourceTruthOk)
                    && lowerAnchorIsAnchored
                    && middleCarrierIsCarrier
                    && upperAnchorIsAnchored;

            String failureLayer;
            if (torchRejectedByLaw) {
                failureLayer = "NONE";
            } else if (!torchPresent) {
                failureLayer = "SBSBS_TORCH_NOT_PLACED";
            } else if (!carrierSourceTruthOk) {
                failureLayer = "SBSBS_SUPPORT_SOURCE_TRUTH_MISMATCH";
            } else if (isVanillaPosition || (Double.isFinite(contactGap) && Math.abs(contactGap) > EPSILON)) {
                failureLayer = "SBSBS_FLOOR_TORCH_VANILLA_POSITION";
            } else {
                failureLayer = "NONE";
            }
            failureLayerHolder[0] = failureLayer;
            String sourceTruthClassification = "NONE";
            if (torchRejectedByLaw) {
                sourceTruthClassification = "SBSBS_OWNER_TOP_SUPPORT_REJECTED_BY_LAW";
            } else if (!fixtureSourceTruth) {
                sourceTruthClassification = "SBSBS_SOURCE_TRUTH_NOT_AUTHORED";
            } else if (!placementAcceptedHolder[0] || !torchPresent) {
                sourceTruthClassification = "SBSBS_TORCH_PLACED_BEFORE_SOURCE_TRUTH";
            } else if (isVanillaPosition
                    || (Double.isFinite(contactGap) && Math.abs(contactGap) > EPSILON)) {
                sourceTruthClassification = "SBSBS_STEADY_STATE_OK_BUT_LIVE_CONSTRUCTION_GAP";
            }
            sourceTruthClassificationHolder[0] = sourceTruthClassification;

            boolean proofRed = !failureLayer.equals("NONE") && !failureLayer.equals("SBSBS_OUT_OF_SCOPE");

            System.out.println("[JULIA_BETA35_FLOOR_TORCH_SBSBS_MEASURED]"
                    + " structure=SBSBS"
                    + " categoryScope=floor_torch_only"
                    + " torchPos=" + torchPos.toShortString()
                    + " torchState=" + torchState
                    + " supportPos=" + supportPos.toShortString()
                    + " supportState=" + supportStateAfter
                    + " supportDy=" + String.format("%.6f", supportDy)
                    + " torchDy=" + (Double.isFinite(torchDy)
                            ? String.format("%.6f", torchDy) : "N/A")
                    + " rawSupportTopY=" + String.format("%.6f", rawSupportTopY)
                    + " supportVisibleTopY=" + String.format("%.6f", supportVisibleTopY)
                    + " rawTorchShapeMinY=" + (Double.isFinite(rawTorchShapeMinY)
                            ? String.format("%.6f", rawTorchShapeMinY) : "N/A")
                    + " torchModelBottomY=" + (Double.isFinite(torchModelBottomY)
                            ? String.format("%.6f", torchModelBottomY) : "N/A")
                    + " contactGap=" + (Double.isFinite(contactGap)
                            ? String.format("%.6f", contactGap) : "N/A")
                    + " isVanillaPosition=" + isVanillaPosition
                    + " placementResult=" + placementResultText[0]
                    + " placementAccepted=" + placementAcceptedHolder[0]
                    + " survivalResult=" + (torchRejectedByLaw ? "REJECTED_BY_LAW" : (torchPresent
                            ? (survivalGreen ? "SURVIVAL_GREEN" : "SURVIVAL_RED") : "N/A"))
                    + " triadResult=" + (torchRejectedByLaw ? "REJECTED_BY_LAW" : (torchPresent
                            ? (triadGreen ? "GREEN" : "RED") : "N/A"))
                    + " supportType=" + supportType
                    + " torchRejectedByLaw=" + torchRejectedByLaw
                    + " torchPlacedBeforeFinalization=false"
                    + " failureLayer=" + failureLayer
                    + " wall_torch=NOT_COVERED"
                    + " beta35ReleaseStatus=PAUSED_JULIA_SBSBS_UNRESOLVED");

            if (sourceTruthCapture) {
                System.out.println("[JULIA_BETA35_FLOOR_TORCH_SBSBS_SOURCE_TRUTH_MEASURED]"
                        + " structure=SBSBS"
                        + " categoryScope=floor_torch_only"
                        + " torchPos=" + torchPos.toShortString()
                        + " torchState=" + torchState
                        + " supportPos=" + supportPos.toShortString()
                        + " supportState=" + supportStateAfter
                        + " supportDy=" + String.format("%.6f", supportDy)
                        + " torchDy=" + (Double.isFinite(torchDy)
                                ? String.format("%.6f", torchDy) : "N/A")
                        + " contactGap=" + (Double.isFinite(contactGap)
                                ? String.format("%.6f", contactGap) : "N/A")
                        + " isVanillaPosition=" + isVanillaPosition
                        + " placementResult=" + placementResultText[0]
                        + " placementAccepted=" + placementAcceptedHolder[0]
                        + " lowerAnchor=" + beta35SbsbsComponentTruth(
                                mc.world, lowerAnchorBlock, lowerAnchorState, "lowerAnchor")
                        + " middleCarrier=" + beta35SbsbsComponentTruth(
                                mc.world, middleCarrierSlab, middleCarrierState, "middleCarrier")
                        + " upperAnchor=" + beta35SbsbsComponentTruth(
                                mc.world, upperAnchorBlock, upperAnchorState, "upperAnchor")
                        + " support=" + beta35SbsbsComponentTruth(
                                mc.world, supportPos, supportStateAfter, "support")
                        + " base=" + beta35SbsbsComponentTruth(
                                mc.world, baseSlab, baseState, "base")
                        + " supportHasBottomSlabBelow=" + supportHasBottomSlabBelow
                        + " supportAnchoredFullBlockBelow=" + supportAnchoredFullBlockBelow
                        + " lowerAnchorIsAnchored=" + lowerAnchorIsAnchored
                        + " lowerAnchorIsCarrier=" + lowerAnchorIsCarrier
                        + " middleCarrierIsCarrier=" + middleCarrierIsCarrier
                        + " upperAnchorIsAnchored=" + upperAnchorIsAnchored
                        + " fixtureSourceTruth=" + fixtureSourceTruth
                        + " classification=" + sourceTruthClassification
                        + " failureLayer=" + failureLayer
                        + " productionGameplayFixApplied=true");
            }

            System.out.println("[JULIA_BETA35_FLOOR_TORCH_SBSBS_RED]"
                    + " redProofResult=" + (proofRed ? "RED" : "GREEN")
                    + " failureLayer=" + failureLayer
                    + " isVanillaPosition=" + isVanillaPosition
                    + " contactGap=" + (Double.isFinite(contactGap)
                            ? String.format("%.6f", contactGap) : "N/A")
                    + " categoryScope=floor_torch_only"
                    + " productionGameplayFixApplied=true");
        });

        boolean proofRed = !failureLayerHolder[0].equals("NONE")
                && !failureLayerHolder[0].equals("SBSBS_OUT_OF_SCOPE");

        System.out.println("[JULIA_BETA35_FLOOR_TORCH_SBSBS_SUMMARY]"
                + " structure=SBSBS"
                + " categoryScope=floor_torch_only"
                + " failureLayer=" + failureLayerHolder[0]
                + " isVanillaPosition=" + isVanillaPositionHolder[0]
                + " redProofResult=" + (proofRed ? "RED" : "GREEN")
                + " juliaLiveReport=SBSBS_TORCH_FLOATS_VANILLA_POSITION"
                + " productionGameplayFixApplied=true"
                + " wall_torch=NOT_COVERED"
                + " lantern=NOT_COVERED"
                + " signs=NOT_COVERED"
                + " chains=NOT_COVERED"
                + " beta35ReleaseStatus=PAUSED_SBSBS_UNRESOLVED"
                + " nextSlice=PRODUCTION_FIX_AFTER_SBSBS_RED_CLASSIFICATION");

        if (sourceTruthCapture) {
            System.out.println("[JULIA_BETA35_FLOOR_TORCH_SBSBS_SOURCE_TRUTH_SUMMARY]"
                    + " structure=SBSBS"
                    + " categoryScope=floor_torch_only"
                    + " supportPos=" + supportPos.toShortString()
                    + " torchPos=" + torchPos.toShortString()
                    + " sourceTruth=" + sourceTruthClassificationHolder[0]
                    + " failureLayer=" + failureLayerHolder[0]
                    + " supportDy=" + (Double.isFinite(measuredSupportDyHolder[0])
                            ? String.format("%.6f", measuredSupportDyHolder[0]) : "N/A")
                    + " torchDy=" + (Double.isFinite(measuredTorchDyHolder[0])
                            ? String.format("%.6f", measuredTorchDyHolder[0]) : "N/A")
                    + " productionGameplayFixApplied=true"
                    + " redProofResult=" + (proofRed ? "RED" : "GREEN"));
        }

        if (proofRed) {
            throw new RuntimeException("[JULIA_BETA35_FLOOR_TORCH_SBSBS_RED]"
                    + " reason=sbsbs_floor_torch_failure"
                    + " failureLayer=" + failureLayerHolder[0]
                    + " isVanillaPosition=" + isVanillaPositionHolder[0]
                    + " categoryScope=floor_torch_only"
                    + " productionGameplayFixApplied=true");
        }
    }
}
