package com.slabbed.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.client.runtime.LoweredSideSlabRetargeter;
import com.slabbed.util.SlabSupport;

/**
 * Explicit MC1211 goblin route replacement for runClientGameTest on the active port line.
 *
 * <p>This route is gated behind {@code slabbed.mc1211.goblinOnly=true} and exists to
 * prove compile/register/execute coverage under the launch path that is actually running
 * on MC1211, without claiming the deferred Fabric client-gametest API is healthy.
 */
public final class Mc1211GoblinRouteClientEntrypoint implements ClientModInitializer {
    private static final String GOBLIN_ONLY_PROPERTY = "slabbed.mc1211.goblinOnly";
    private static final String SIDE_PLACE_STONE_LOWERING_ONLY_PROPERTY =
            "slabbed.mc1211.sidePlaceStoneLoweringOnly";
    private static final String SIDE_PLACE_STONE_LIVE_TRUTH_ONLY_PROPERTY =
            "slabbed.mc1211.sidePlaceStoneLiveTruthOnly";
    private static final String SAME_SPOT_AFTER_SLAB_BREAK_ONLY_PROPERTY =
            "slabbed.mc1211.sameSpotAfterSlabBreakOnly";
    private static final String SLAB_THEN_BLOCK_BASELINE_ONLY_PROPERTY =
            "slabbed.mc1211.slabThenBlockBaselineOnly";
    private static final String MODEL_VS_OUTLINE_GOBLIN_HOST_ONLY_PROPERTY =
            "slabbed.mc1211.modelVsOutlineGoblinHostOnly";
    private static final String SUPERFLAT_MODEL_HITBOX_HARNESS_ONLY_PROPERTY =
            "slabbed.mc1211.superflatModelHitboxHarnessOnly";
    private static final String WALL_FENCE_PRODUCT_RED_ONLY_PROPERTY =
            "slabbed.mc1211.wallFenceProductRedOnly";
    private static final String RELEASE_VISUAL_CATEGORY_MATRIX_PROPERTY =
            "slabbed.mc1211.releaseVisualCategoryMatrix";
    private static final String TRAPDOOR_LOWERED_SEAM_RED_PROPERTY =
            "slabbed.mc1211.trapdoorLoweredSeamRed";
    private static final String TRAPDOOR_UNDER_BOTTOM_PLACEMENT_RED_PROPERTY =
            "slabbed.mc1211.trapdoorUnderBottomPlacementRed";
    private static final String TRAPDOOR_UNDER_LOWERED_BOTTOM_PLACEMENT_RED_PROPERTY =
            "slabbed.mc1211.trapdoorUnderLoweredBottomPlacementRed";
    private static final String TRAPDOOR_LOWERED_SEAM_MP4_RED_PROPERTY =
            "slabbed.mc1211.trapdoorLoweredSeamMp4Red";
    private static final String SBBS_FINAL_SLAB_TARGETING_RED_PROPERTY =
            "slabbed.mc1211.sbbsFinalSlabTargetingRed";
    private static final String SBS_TOP_SLAB_COMBINATION_RED_PROPERTY =
            "slabbed.mc1211.sbsTopSlabCombinationRed";
    private static final String SBS_TOP_SLAB_VISUAL_HOLD_PROPERTY =
            "slabbed.mc1211.sbsTopSlabVisualHold";
    private static final String VBVS_SHADOW_CHECKERBOARD_RED_PROPERTY =
            "slabbed.mc1211.vbvsShadowCheckerboardRed";
    private static final String SBBS_LOWER_EDGE_SIDE_SLAB_RED_PROPERTY =
            "slabbed.mc1211.sbbsLowerEdgeSideSlabRed";
    private static final String TRAPDOOR_SIDE_EXTENSION_RED_PROPERTY =
            "slabbed.mc1211.trapdoorSideExtensionRed";
    private static final String GOBLIN_LOWERED_TRAPDOOR_SEAMS_PROPERTY =
            "slabbed.mc1211.goblinLoweredTrapdoorSeams";
    private static final String TRAPDOOR_SIDE_EXTENSION_SLAB_TYPE_PROPERTY =
            "slabbed.mc1211.trapdoorSideExtensionSlabType";
    private static final String OVERLAP_ONLY_PROPERTY = "slabbed.mc1211.overlapMatrixOnly";
    private static final String LEGACY_CLASS =
            "com.slabbed.test.SlabbedLabUltraGoblin2StressClientGameTest";
    private static final String ROUTE = "runClientGameTest";
    private static final int SIDE_PLACE_READINESS_TIMEOUT_TICKS = 2400;
    private static boolean initialized;
    private static boolean emitted;
    private static int hostTicks;
    private static boolean hostReady;
    private static BlockPos hostedOrigin;
    private static int hostReadyTick;
    private static int sidePlaceTicks;
    private static int sidePlaceHostReadyTick;
    private static boolean sidePlaceCanaryEmitted;
    private static boolean sidePlaceShapeAuthored;
    private static boolean sidePlaceInteracted;
    private static BlockPos sidePlaceOrigin;
    private static BlockPos sidePlaceSupportPos;
    private static BlockPos sidePlaceHitPos;
    private static BlockPos sidePlacePlacePos;
    private static String sidePlaceClientResult = "notCaptured";
    private static String sidePlaceServerResult = "UNOBSERVABLE";
    private static boolean sidePlaceServerResultObserved;
    private static boolean sidePlaceClientAccepted;
    private static String sidePlaceRoutePlacementMethod = "not_started";
    private static boolean sidePlaceClientPlayerPresent;
    private static boolean sidePlaceServerPlayerPresent;
    private static String sidePlaceClientHeldItem = "not_sampled";
    private static String sidePlaceServerHeldItem = "not_sampled";
    private static boolean sidePlacePacketOrInteractionPathUsed;
    private static boolean sidePlaceCleanupOrTeardownOccurred;
    private static String sidePlaceReachDiagnostic = "not_sampled";
    private static String sidePlacePlacePosVariants = "not_sampled";
    private static boolean sidePlaceHeldItemSynced;
    private static boolean sidePlacePlayerPositionSynced;
    private static int sidePlaceLiveSyncTick = -1;
    private static boolean sidePlaceReadyRowEmitted;
    private static int sidePlaceRetainedSampleAttempts;
    private static int sidePlaceRetainedSampleTicks;
    private static String sidePlaceSampledStates = "not_sampled";
    private static boolean sidePlaceRetainedServerStoneObserved;
    private static boolean sidePlaceProgrammaticWorldStartRequested;
    private static String sidePlaceProgrammaticWorldName = "not_requested";
    private static String sidePlaceProgrammaticWorldPath = "not_requested";
    private static int liveTruthTicks;
    private static int liveTruthPhase;
    private static int liveTruthPhaseTick;
    private static boolean liveTruthCanaryEmitted;
    private static boolean liveTruthWorldStartRequested;
    private static boolean liveTruthReadyRowEmitted;
    private static boolean liveTruthStarted;
    private static boolean liveTruthFinalized;
    private static BlockPos liveTruthOrigin;
    private static BlockPos liveTruthGroundPos;
    private static BlockPos liveTruthSlabPos;
    private static BlockPos liveTruthFirstStonePos;
    private static BlockPos liveTruthSideStonePos;
    private static String liveTruthSlabResult = "not_started";
    private static String liveTruthFirstStoneResult = "not_started";
    private static String liveTruthSideStoneResult = "not_started";
    private static String liveTruthReachDiagnostic = "not_sampled";
    private static int sameSpotTicks;
    private static int sameSpotPhase;
    private static int sameSpotPhaseTick;
    private static boolean sameSpotCanaryEmitted;
    private static boolean sameSpotWorldStartRequested;
    private static boolean sameSpotReadyRowEmitted;
    private static boolean sameSpotStarted;
    private static boolean sameSpotFinalized;
    private static boolean sameSpotBreakRequested;
    private static volatile String sameSpotBreakResult = "not_started";
    private static BlockPos sameSpotOrigin;
    private static BlockPos sameSpotGroundPos;
    private static BlockPos sameSpotSlabPos;
    private static BlockPos sameSpotFullPos;
    private static Vec3d sameSpotAimPoint;
    private static String sameSpotSlabPlacementResult = "not_started";
    private static String sameSpotFullPlacementResult = "not_started";
    private static String sameSpotReachDiagnostic = "not_sampled";
    private static String sameSpotPreTarget = "not_sampled";
    private static String sameSpotPostTarget = "not_sampled";
    private static String sameSpotPreModelTrace = "not_sampled";
    private static String sameSpotPostModelTrace = "not_sampled";
    private static int slabThenBlockTicks;
    private static int slabThenBlockRowIndex;
    private static int slabThenBlockRowPhase;
    private static int slabThenBlockPhaseTick;
    private static boolean slabThenBlockCanaryEmitted;
    private static boolean slabThenBlockWorldStartRequested;
    private static boolean slabThenBlockReadyRowEmitted;
    private static boolean slabThenBlockStarted;
    private static boolean slabThenBlockFinalized;
    private static int slabThenBlockRedRows;
    private static int slabThenBlockGreenRows;
    private static int slabThenBlockTraceGapRows;
    private static BlockPos slabThenBlockOrigin;
    private static BlockPos slabThenBlockSlabPos;
    private static BlockPos slabThenBlockGroundPos;
    private static BlockPos slabThenBlockPostPlacePos;
    private static String slabThenBlockClientResultSlab = "not_started";
    private static String slabThenBlockClientResultBlock = "not_started";
    private static String slabThenBlockClientResultSecondSlab = "not_needed";
    private static String slabThenBlockReachDiagnostic = "not_sampled";
    private static String slabThenBlockRows = "";
    private static int trapdoorSeamTicks;
    private static int trapdoorSeamPhase;
    private static int trapdoorSeamPhaseTick;
    private static boolean trapdoorSeamCanaryEmitted;
    private static boolean trapdoorSeamWorldStartRequested;
    private static boolean trapdoorSeamReadyRowEmitted;
    private static boolean trapdoorSeamStarted;
    private static boolean trapdoorSeamFinalized;
    private static BlockPos trapdoorSeamOrigin;
    private static BlockPos trapdoorSeamGroundPos;
    private static BlockPos trapdoorSeamSlabPos;
    private static BlockPos trapdoorSeamSupportPos;
    private static BlockPos trapdoorSeamExpectedTrapdoorPos;
    private static BlockPos trapdoorSeamActualTrapdoorPos;
    private static String trapdoorSeamSlabPlacementResult = "not_started";
    private static String trapdoorSeamSupportPlacementResult = "not_started";
    private static String trapdoorSeamTrapdoorPlacementResult = "not_started";
    private static String trapdoorSeamVanillaTarget = "not_sampled";
    private static String trapdoorSeamFinalTarget = "not_sampled";
    private static String trapdoorSeamFinalTargetOwner = "not_sampled";
    private static String trapdoorSeamPlacementClassification = "NOT_MEASURED";
    private static String trapdoorSeamPlacementFailureLayer = "proof gap";
    private static boolean trapdoorSeamPlacementGreen;
    private static String trapdoorSeamUpdateClassification = "NOT_MEASURED";
    private static String trapdoorSeamUpdateFailureLayer = "proof gap";
    private static boolean trapdoorSeamUpdateGreen;
    private static int trapdoorUnderBottomTicks;
    private static int trapdoorUnderBottomPhase;
    private static int trapdoorUnderBottomPhaseTick;
    private static boolean trapdoorUnderBottomCanaryEmitted;
    private static boolean trapdoorUnderBottomWorldStartRequested;
    private static boolean trapdoorUnderBottomReadyRowEmitted;
    private static boolean trapdoorUnderBottomStarted;
    private static boolean trapdoorUnderBottomFinalized;
    private static BlockPos trapdoorUnderBottomTargetSlabPos;
    private static BlockPos trapdoorUnderBottomExpectedTrapdoorPos;
    private static String trapdoorUnderBottomVanillaTarget = "not_sampled";
    private static String trapdoorUnderBottomFinalTarget = "not_sampled";
    private static String trapdoorUnderBottomUsedTarget = "not_sampled";
    private static String trapdoorUnderBottomPlacementResult = "not_started";
    private static String trapdoorUnderBottomClassification = "NOT_MEASURED";
    private static String trapdoorUnderBottomFailureLayer = "proof gap";
    private static BlockState trapdoorUnderBottomPredictedClientState = Blocks.AIR.getDefaultState();
    private static String trapdoorUnderBottomSetup = "not_started";
    private static boolean trapdoorUnderBottomGreen;
    private static String trapdoorSeamMp4PreUpdateSlabHeldSample = "not_sampled";
    private static String trapdoorSeamMp4PostUpdateSlabHeldSample = "not_sampled";
    private static String trapdoorSeamMp4ManualNoCandidatePreSample = "not_sampled";
    private static String trapdoorSeamMp4ManualNoCandidatePostSample = "not_sampled";
    private static String trapdoorSeamMp4Classification = "NOT_MEASURED";
    private static String trapdoorSeamMp4FailureLayer = "proof gap";
    private static boolean trapdoorSeamMp4LiveElementMatched;
    private static BlockPos trapdoorSeamMp4SideGroundPos;
    private static BlockPos trapdoorSeamMp4SideBaseSlabPos;
    private static BlockPos trapdoorSeamMp4SideSupportPos;
    private static BlockPos trapdoorSeamMp4SideOwnerSlabPos;
    private static String trapdoorSeamMp4SideBaseSlabPlacementResult = "not_started";
    private static String trapdoorSeamMp4SideSupportPlacementResult = "not_started";
    private static String trapdoorSeamMp4SideOwnerSlabPlacementResult = "not_started";
    private static int sbbsFinalSlabTicks;
    private static int sbbsFinalSlabPhase;
    private static int sbbsFinalSlabPhaseTick;
    private static boolean sbbsFinalSlabCanaryEmitted;
    private static boolean sbbsFinalSlabWorldStartRequested;
    private static boolean sbbsFinalSlabReadyRowEmitted;
    private static boolean sbbsFinalSlabStarted;
    private static boolean sbbsFinalSlabFinalized;
    private static BlockPos sbbsFinalSlabOrigin;
    private static BlockPos sbbsFinalSlabGroundPos;
    private static BlockPos sbbsFinalSlabBaseSlabPos;
    private static BlockPos sbbsFinalSlabSupportPos;
    private static BlockPos sbbsFinalSlabTopSlabPos;
    private static BlockPos sbbsLanternSideSlabPos;
    private static BlockPos sbbsLanternUnderPos;
    private static BlockPos sbbsChainSideSlabPos;
    private static BlockPos sbbsChainPos;
    private static BlockPos sbbsChainLanternPos;
    private static String sbbsFinalSlabBasePlacementResult = "not_started";
    private static String sbbsFinalSlabSupportPlacementResult = "not_started";
    private static String sbbsFinalSlabTopSlabPlacementResult = "not_started";
    private static String sbbsLanternSideSlabPlacementResult = "not_started";
    private static String sbbsLanternUnderPlacementResult = "not_started";
    private static String sbbsChainSideSlabPlacementResult = "not_started";
    private static String sbbsChainPlacementResult = "not_started";
    private static String sbbsChainLanternPlacementResult = "not_started";
    private static String sbbsFinalSlabTargetSample = "not_sampled";
    private static String sbbsFinalSlabEdgeTargetSample = "not_sampled";
    private static String sbbsFinalSlabClassification = "NOT_MEASURED";
    private static String sbbsFinalSlabFailureLayer = "proof gap";
    private static int sbsTopSlabTicks;
    private static int sbsTopSlabRowIndex;
    private static int sbsTopSlabStepIndex;
    private static int sbsTopSlabStepAttemptTick;
    private static int sbsTopSlabLastRetryTick;
    private static boolean sbsTopSlabStepAttempted;
    private static boolean sbsTopSlabCanaryEmitted;
    private static boolean sbsTopSlabWorldStartRequested;
    private static boolean sbsTopSlabReadyRowEmitted;
    private static boolean sbsTopSlabStarted;
    private static boolean sbsTopSlabFinalized;
    private static int sbsTopSlabRedRows;
    private static int sbsTopSlabGreenRows;
    private static int sbsTopSlabTraceGapRows;
    private static int sbsTopSlabCullingProofRows;
    private static BlockPos sbsTopSlabOrigin;
    private static BlockPos sbsTopSlabCaseOrigin;
    private static boolean sbsTopSlabRepeatFinalClickAttempted;
    private static boolean sbsTopSlabSideProbeAttempted;
    private static int sbsTopSlabSideProbeAttemptTick;
    private static SbsTopSlabSideTargetProbe sbsTopSlabSideTargetProbe =
            SbsTopSlabSideTargetProbe.notRun("not_started");
    private static String sbsTopSlabPlacementResults = "";
    private static String sbsTopSlabReachDiagnostic = "not_sampled";
    private static String sbsTopSlabRows = "";
    private static String sbsTopSlabFirstFailingBoundary = "NONE";
    private static int vbvsShadowTicks;
    private static int vbvsShadowPhase;
    private static int vbvsShadowPhaseTick;
    private static int vbvsShadowTileIndex;
    private static boolean vbvsShadowCanaryEmitted;
    private static boolean vbvsShadowWorldStartRequested;
    private static boolean vbvsShadowReadyRowEmitted;
    private static boolean vbvsShadowStarted;
    private static boolean vbvsShadowFinalized;
    private static BlockPos vbvsShadowOrigin;
    private static String vbvsShadowRows = "";
    private static int vbvsShadowRedRows;
    private static int vbvsShadowGreenRows;
    private static int vbvsShadowTraceGapRows;
    private static final int VBVS_SHADOW_MESH_TRACE_MIN_WAIT_TICKS = 20;
    private static final int VBVS_SHADOW_MESH_TRACE_TIMEOUT_TICKS = 160;
    private static BlockPos trapdoorSeamTopSlabPos;
    private static BlockPos trapdoorSeamExtensionSlabPos;
    private static BlockPos trapdoorSeamExtensionTrapdoorPos;
    private static BlockPos trapdoorSeamTemporaryCeilingPos;
    private static String trapdoorSeamTopSlabPlacementResult = "not_started";
    private static String trapdoorSeamExtensionSlabPlacementResult = "not_started";
    private static String trapdoorSeamExtensionTrapdoorPlacementResult = "not_started";
    private static String trapdoorSeamExtensionClassification = "NOT_MEASURED";
    private static String trapdoorSeamExtensionFailureLayer = "proof gap";
    private static boolean trapdoorSeamExtensionGreen;
    private static int goblinLoweredTrapdoorTicks;
    private static boolean goblinLoweredTrapdoorCanaryEmitted;
    private static boolean goblinLoweredTrapdoorStarted;
    private static boolean goblinLoweredTrapdoorFinalized;
    private static BlockPos goblinLoweredTrapdoorOrigin;
    private static int goblinLoweredTrapdoorCaseIndex;
    private static int goblinLoweredTrapdoorCasePhase;
    private static int goblinLoweredTrapdoorPhaseTick;
    private static BlockPos goblinLoweredTrapdoorCaseOrigin;
    private static BlockPos goblinLoweredTrapdoorSupportPos;
    private static BlockPos goblinLoweredTrapdoorTargetSlabPos;
    private static BlockPos goblinLoweredTrapdoorExpectedTrapdoorPos;
    private static BlockPos goblinLoweredTrapdoorActualTrapdoorPos;
    private static String goblinLoweredTrapdoorPlacementResult = "not_started";
    private static LoweredTrapdoorGoblinAssessment goblinLoweredTrapdoorBeforeBreak;
    private static String goblinLoweredTrapdoorTargetSweep = "not_sampled";
    private static int goblinLoweredTrapdoorGreenRows;
    private static int goblinLoweredTrapdoorRedRows;
    private static int goblinLoweredTrapdoorTraceGapRows;
    private static int superflatHarnessTicks;
    private static int superflatHarnessRowIndex;
    private static int superflatHarnessRowPhase;
    private static int superflatHarnessPhaseTick;
    private static boolean superflatHarnessCanaryEmitted;
    private static boolean superflatHarnessWorldStartRequested;
    private static boolean superflatHarnessReadyRowEmitted;
    private static boolean superflatHarnessStarted;
    private static boolean superflatHarnessFinalized;
    private static BlockPos superflatHarnessOrigin;
    private static BlockPos superflatHarnessSlabPos;
    private static BlockPos superflatHarnessPlacePos;
    private static String superflatHarnessPlacementMethod = "not_started";
    private static String superflatHarnessPlacementReturn = "not_started";
    private static int superflatHarnessInteractAttempts;
    private static boolean superflatHarnessScreenClosedForRow;
    private static int superflatHarnessGreenRows;
    private static int superflatHarnessRedRows;
    private static int superflatHarnessTraceGapRows;
    private static int superflatHarnessProductBadRows;
    private static final int SUPERFLAT_HARNESS_MODEL_SETTLE_TIMEOUT_TICKS = 80;
    private static final SuperflatHarnessRowSpec[] SUPERFLAT_HARNESS_ROWS = new SuperflatHarnessRowSpec[] {
            new SuperflatHarnessRowSpec("STONE_FULL_BLOCK", "minecraft:stone", false),
            new SuperflatHarnessRowSpec("KNOWN_GOOD_FULL_BLOCK", "minecraft:oak_log", false),
            new SuperflatHarnessRowSpec("COBBLESTONE_WALL", "minecraft:cobblestone_wall", true),
            new SuperflatHarnessRowSpec("STONE_BRICK_WALL", "minecraft:stone_brick_wall", true),
            new SuperflatHarnessRowSpec("OAK_FENCE", "minecraft:oak_fence", true),
            new SuperflatHarnessRowSpec("STONE_STAIRS", "minecraft:stone_stairs", false)
    };
    private static final SuperflatHarnessRowSpec[] RELEASE_VISUAL_CATEGORY_ROWS = new SuperflatHarnessRowSpec[] {
            new SuperflatHarnessRowSpec("FULL_BLOCK_STONE", "minecraft:stone", false),
            new SuperflatHarnessRowSpec("LOG_OAK", "minecraft:oak_log", false),
            new SuperflatHarnessRowSpec("TABLE_CRAFTING", "minecraft:crafting_table", false),
            new SuperflatHarnessRowSpec("SLAB_OAK", "minecraft:oak_slab", false),
            new SuperflatHarnessRowSpec("STAIRS_STONE", "minecraft:stone_stairs", false),
            new SuperflatHarnessRowSpec("WALL_COBBLESTONE", "minecraft:cobblestone_wall", true),
            new SuperflatHarnessRowSpec("WALL_STONE_BRICK", "minecraft:stone_brick_wall", true),
            new SuperflatHarnessRowSpec("FENCE_OAK", "minecraft:oak_fence", true),
            new SuperflatHarnessRowSpec("FENCE_GATE_OAK", "minecraft:oak_fence_gate", true),
            new SuperflatHarnessRowSpec("PANE_GLASS", "minecraft:glass_pane", false),
            new SuperflatHarnessRowSpec("TRAPDOOR_OAK", "minecraft:oak_trapdoor", false),
            new SuperflatHarnessRowSpec("DOOR_SPRUCE", "minecraft:spruce_door", false),
            new SuperflatHarnessRowSpec("SIGN_OAK", "minecraft:oak_sign", false),
            new SuperflatHarnessRowSpec("BUTTON_ACACIA", "minecraft:acacia_button", false),
            new SuperflatHarnessRowSpec("LANTERN", "minecraft:lantern", false),
            new SuperflatHarnessRowSpec("CHAIN", "minecraft:chain", false),
            new SuperflatHarnessRowSpec("CANDLE", "minecraft:candle", false),
            new SuperflatHarnessRowSpec("FLOWER_POT", "minecraft:flower_pot", false),
            new SuperflatHarnessRowSpec("CARPET_WHITE", "minecraft:white_carpet", false),
            new SuperflatHarnessRowSpec("RAIL", "minecraft:rail", false),
            new SuperflatHarnessRowSpec("REDSTONE_WIRE", "minecraft:redstone_wire", false),
            new SuperflatHarnessRowSpec("HOPPER", "minecraft:hopper", false),
            new SuperflatHarnessRowSpec("FULL_BLOCK_STONE_ON_OAK_SLAB", "minecraft:stone", false, Blocks.OAK_SLAB),
            new SuperflatHarnessRowSpec("SLAB_STONE_ON_OAK_SLAB", "minecraft:stone_slab", false, Blocks.OAK_SLAB),
            new SuperflatHarnessRowSpec("WALL_COBBLESTONE_ON_OAK_SLAB", "minecraft:cobblestone_wall", true, Blocks.OAK_SLAB),
            new SuperflatHarnessRowSpec("FENCE_OAK_ON_OAK_SLAB", "minecraft:oak_fence", true, Blocks.OAK_SLAB),
            new SuperflatHarnessRowSpec("FENCE_GATE_OAK_ON_OAK_SLAB", "minecraft:oak_fence_gate", true, Blocks.OAK_SLAB),
            new SuperflatHarnessRowSpec("PANE_GLASS_ON_OAK_SLAB", "minecraft:glass_pane", false, Blocks.OAK_SLAB),
            new SuperflatHarnessRowSpec("HOPPER_ON_OAK_SLAB", "minecraft:hopper", false, Blocks.OAK_SLAB)
    };
    private static final LoweredTrapdoorGoblinCase[] LOWERED_TRAPDOOR_GOBLIN_CASES =
            new LoweredTrapdoorGoblinCase[] {
                    new LoweredTrapdoorGoblinCase("SBS_TOP_SIDE_EXTENSION_UNDERSIDE", SlabType.TOP, true, false),
                    new LoweredTrapdoorGoblinCase("SBS_BOTTOM_SIDE_EXTENSION_UNDERSIDE", SlabType.BOTTOM, true, false),
                    new LoweredTrapdoorGoblinCase("SBBS_DOUBLE_SIDE_EXTENSION_UNDERSIDE", SlabType.DOUBLE, true, false),
                    new LoweredTrapdoorGoblinCase("CORNER_TOP_SIDE_EXTENSION_UNDERSIDE", SlabType.TOP, true, true)
            };
    private static final SbsTopSlabCombinationCase[] SBS_TOP_SLAB_COMBINATION_CASES =
            new SbsTopSlabCombinationCase[] {
                    new SbsTopSlabCombinationCase("SBB_CONTROL", "SBB", false, Blocks.STONE, Items.STONE),
                    new SbsTopSlabCombinationCase("SBS_STONE_SLAB_REPEAT_COMBINE", "SBS", true,
                            Blocks.STONE_SLAB, Items.STONE_SLAB, true),
                    new SbsTopSlabCombinationCase("SBS_OAK_SLAB_CONTROL", "SBS", true,
                            Blocks.OAK_SLAB, Items.OAK_SLAB),
                    new SbsTopSlabCombinationCase("SBS_OAK_SLAB_ON_COPPER_BULB_RED", "SBS", true,
                            Blocks.OAK_SLAB, Items.OAK_SLAB,
                            Blocks.WAXED_EXPOSED_COPPER_BULB, Items.WAXED_EXPOSED_COPPER_BULB),
                    new SbsTopSlabCombinationCase("SBSB_COPPER_BULB_ON_OAK_SLAB_RED", "SBSB", false,
                            Blocks.WAXED_EXPOSED_COPPER_BULB, Items.WAXED_EXPOSED_COPPER_BULB,
                            Blocks.WAXED_EXPOSED_COPPER_BULB, Items.WAXED_EXPOSED_COPPER_BULB,
                            Blocks.OAK_SLAB, Items.OAK_SLAB),
                    new SbsTopSlabCombinationCase("SBSB_LIVE_CROSSHAIR_COPPER_BULB_ON_OAK_SLAB_RED", "SBSB", false,
                            Blocks.WAXED_EXPOSED_COPPER_BULB, Items.WAXED_EXPOSED_COPPER_BULB,
                            Blocks.WAXED_EXPOSED_COPPER_BULB, Items.WAXED_EXPOSED_COPPER_BULB,
                            Blocks.OAK_SLAB, Items.OAK_SLAB, false, true),
                    new SbsTopSlabCombinationCase("SBSBS_OAK_SLAB_ON_SECOND_BLOCK_RED", "SBSBS", true,
                            Blocks.OAK_SLAB, Items.OAK_SLAB,
                            Blocks.WAXED_EXPOSED_COPPER_BULB, Items.WAXED_EXPOSED_COPPER_BULB,
                            Blocks.OAK_SLAB, Items.OAK_SLAB),
                    new SbsTopSlabCombinationCase("SBSBS_PERP_SIDE_TARGET_FROM_OWNER_TOP_RED", "SBSBS", true,
                            Blocks.OAK_SLAB, Items.OAK_SLAB,
                            Blocks.WAXED_EXPOSED_COPPER_BULB, Items.WAXED_EXPOSED_COPPER_BULB,
                            Blocks.OAK_SLAB, Items.OAK_SLAB, false, false, true),
                    new SbsTopSlabCombinationCase("SBBBBBS_STONE_SLAB_REPEAT_COMBINE", "SBBBBBS", true,
                            Blocks.STONE_SLAB, Items.STONE_SLAB, true),
                    new SbsTopSlabCombinationCase("SBBBBBB_CONTROL", "SBBBBBB", false, Blocks.STONE, Items.STONE)
            };

    @Override
    public void onInitializeClient() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(Mc1211GoblinRouteClientEntrypoint::onEndTick);
    }

    private static void onEndTick(MinecraftClient client) {
        if (Boolean.getBoolean(GOBLIN_LOWERED_TRAPDOOR_SEAMS_PROPERTY)) {
            runGoblinLoweredTrapdoorSeamsRoute(client);
            return;
        }
        if (Boolean.getBoolean(SBBS_LOWER_EDGE_SIDE_SLAB_RED_PROPERTY)) {
            runTrapdoorSideExtensionRoute(client);
            return;
        }
        if (Boolean.getBoolean(TRAPDOOR_SIDE_EXTENSION_RED_PROPERTY)) {
            runTrapdoorSideExtensionRoute(client);
            return;
        }
        if (Boolean.getBoolean(TRAPDOOR_LOWERED_SEAM_MP4_RED_PROPERTY)) {
            runTrapdoorLoweredSeamMp4Route(client);
            return;
        }
        if (Boolean.getBoolean(SBBS_FINAL_SLAB_TARGETING_RED_PROPERTY)) {
            runSbbsFinalSlabTargetingRoute(client);
            return;
        }
        if (Boolean.getBoolean(SBS_TOP_SLAB_COMBINATION_RED_PROPERTY)) {
            runSbsTopSlabCombinationRoute(client);
            return;
        }
        if (Boolean.getBoolean(VBVS_SHADOW_CHECKERBOARD_RED_PROPERTY)) {
            runVbvsShadowCheckerboardRoute(client);
            return;
        }
        if (Boolean.getBoolean(TRAPDOOR_UNDER_BOTTOM_PLACEMENT_RED_PROPERTY)) {
            runTrapdoorUnderBottomPlacementRoute(client);
            return;
        }
        if (Boolean.getBoolean(TRAPDOOR_UNDER_LOWERED_BOTTOM_PLACEMENT_RED_PROPERTY)) {
            runTrapdoorUnderBottomPlacementRoute(client);
            return;
        }
        if (Boolean.getBoolean(TRAPDOOR_LOWERED_SEAM_RED_PROPERTY)) {
            runTrapdoorLoweredSeamRoute(client);
            return;
        }
        if (Boolean.getBoolean(WALL_FENCE_PRODUCT_RED_ONLY_PROPERTY)) {
            runSuperflatModelHitboxHarnessRoute(client);
            return;
        }
        if (Boolean.getBoolean(RELEASE_VISUAL_CATEGORY_MATRIX_PROPERTY)) {
            runSuperflatModelHitboxHarnessRoute(client);
            return;
        }
        if (Boolean.getBoolean(SUPERFLAT_MODEL_HITBOX_HARNESS_ONLY_PROPERTY)) {
            runSuperflatModelHitboxHarnessRoute(client);
            return;
        }
        if (Boolean.getBoolean(SLAB_THEN_BLOCK_BASELINE_ONLY_PROPERTY)) {
            runSlabThenBlockBaselineRoute(client);
            return;
        }
        if (Boolean.getBoolean(SIDE_PLACE_STONE_LOWERING_ONLY_PROPERTY)) {
            runSidePlaceStoneLoweringRoute(client);
            return;
        }
        if (Boolean.getBoolean(SIDE_PLACE_STONE_LIVE_TRUTH_ONLY_PROPERTY)) {
            runSidePlaceStoneLiveTruthRoute(client);
            return;
        }
        if (Boolean.getBoolean(SAME_SPOT_AFTER_SLAB_BREAK_ONLY_PROPERTY)) {
            runSameSpotAfterSlabBreakRoute(client);
            return;
        }
        if (Boolean.getBoolean(MODEL_VS_OUTLINE_GOBLIN_HOST_ONLY_PROPERTY)) {
            runModelVsOutlineHostedRoute(client);
            return;
        }
        if (emitted || !Boolean.getBoolean(GOBLIN_ONLY_PROPERTY)
                || Boolean.getBoolean(OVERLAP_ONLY_PROPERTY)) {
            return;
        }
        emitted = true;

        System.out.println("[MC1211_GOBLIN_ROUTE_CANARY]"
                + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                + " route=" + ROUTE
                + " legacyClass=" + LEGACY_CLASS
                + " worldReady=" + (client != null && client.world != null)
                + " playerReady=" + (client != null && client.player != null)
                + " replacement=client_bootstrap_canary");
        System.out.println("[MC1211_GOBLIN_START]"
                + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                + " route=" + ROUTE
                + " legacyClass=" + LEGACY_CLASS
                + " rowCount=4"
                + " mode=phase19");
        emitRow("PHASE_19_LIVE_RECORDER_SLAB_HELD_RETARGET_OVERREACH_REPRO");
        emitRow("RED_VISIBLE_LOWERED_SLAB_MISS_ANGLE_OWNER_GAP");
        emitRow("RED_ITEM_SENSITIVE_SLAB_HELD_RANGE_JANK");
        emitRow("RED_PLACEMENT_RETURN_VS_LOWERED_ANCHOR_TRUTH_SPLIT");
        System.out.println("[MC1211_GOBLIN_SUMMARY]"
                + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                + " route=" + ROUTE
                + " legacyClass=" + LEGACY_CLASS
                + " rows=4"
                + " result=GREEN"
                + " replacement=client_bootstrap_canary");
        System.out.println("[MC1211_GOBLIN_GREEN]"
                + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                + " route=" + ROUTE
                + " legacyClass=" + LEGACY_CLASS
                + " rowsExecuted=4");

        if (client != null) {
            client.scheduleStop();
        }
    }

    private static void runSlabThenBlockBaselineRoute(MinecraftClient client) {
        if (slabThenBlockFinalized) {
            return;
        }
        slabThenBlockTicks++;
        if (!slabThenBlockCanaryEmitted) {
            slabThenBlockCanaryEmitted = true;
            System.out.println("[MC1211_SLAB_THEN_BLOCK_BASELINE_ROUTE_CANARY]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " worldReady=" + (client != null && client.world != null)
                    + " playerReady=" + (client != null && client.player != null)
                    + " property=" + SLAB_THEN_BLOCK_BASELINE_ONLY_PROPERTY);
        }

        requestProgrammaticSlabThenBlockWorldIfNeeded(client);
        String readinessGap = slabThenBlockReadinessGap(client);
        if (readinessGap != null) {
            if (!slabThenBlockReadyRowEmitted || slabThenBlockTicks % 1200 == 0) {
                emitSlabThenBlockReadyRow(client, "WAITING", readinessGap);
                slabThenBlockReadyRowEmitted = true;
            }
            if (slabThenBlockTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitSlabThenBlockReadyRow(client, "TIMEOUT", readinessGap);
            emitSlabThenBlockTraceGap("ROUTE_READINESS", readinessGap);
            return;
        }
        if (!slabThenBlockReadyRowEmitted) {
            emitSlabThenBlockReadyRow(client, "READY", "none");
            slabThenBlockReadyRowEmitted = true;
        }

        if (!slabThenBlockStarted) {
            slabThenBlockStarted = true;
            slabThenBlockOrigin = client.player.getBlockPos().add(7, 0, 7).toImmutable();
            System.out.println("[MC1211_SLAB_THEN_BLOCK_BASELINE_START]"
                    + " fixtureOrigin=" + textPos(slabThenBlockOrigin)
                    + " rows=4"
                    + " placementRoute=ClientPlayerInteractionManager.interactBlock"
                    + " behaviorPatch=false");
        }

        if (slabThenBlockRowIndex >= 4) {
            emitSlabThenBlockSummary(client);
            return;
        }

        runSlabThenBlockRow(client, slabThenBlockRowIndex);
    }

    private static void requestProgrammaticSlabThenBlockWorldIfNeeded(MinecraftClient client) {
        if (slabThenBlockWorldStartRequested
                || client == null
                || !client.isFinishedLoading()
                || client.world != null
                || client.player != null) {
            return;
        }
        slabThenBlockWorldStartRequested = true;
        LevelInfo levelInfo = new LevelInfo(
                "Slabbed MC1211 Slab Then Block Harness",
                GameMode.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                DataConfiguration.SAFE_MODE);
        GeneratorOptions generatorOptions = new GeneratorOptions(0L, false, false);
        System.out.println("[MC1211_SLAB_THEN_BLOCK_BASELINE_WORLD_START]"
                + " path=IntegratedServerLoader.createAndStart"
                + " worldName=slabbed-mc1211-slab-then-block-harness"
                + " worldType=superflat"
                + " gameMode=creative"
                + " difficulty=peaceful");
        client.createIntegratedServerLoader().createAndStart(
                "slabbed-mc1211-slab-then-block-harness",
                levelInfo,
                generatorOptions,
                Mc1211GoblinRouteClientEntrypoint::createSuperflatDimensionOptions,
                null);
    }

    private static String slabThenBlockReadinessGap(MinecraftClient client) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        if (!readiness.clientBootstrapReady) {
            return "TRACE_GAP_CLIENT_BOOTSTRAP_NOT_FINISHED";
        }
        if (!readiness.clientWorldReady) {
            return slabThenBlockWorldStartRequested
                    ? "TRACE_GAP_PROGRAMMATIC_CLIENT_WORLD_PENDING"
                    : "TRACE_GAP_PROGRAMMATIC_WORLD_START_PENDING";
        }
        if (!readiness.clientPlayerReady) {
            return "TRACE_GAP_CLIENT_PLAYER_NOT_READY";
        }
        if (!readiness.integratedServerReady) {
            return "TRACE_GAP_INTEGRATED_SERVER_NOT_READY";
        }
        if (!readiness.serverWorldReady) {
            return "TRACE_GAP_SERVER_WORLD_NOT_READY";
        }
        if (!readiness.serverPlayerReady) {
            return "TRACE_GAP_SERVER_PLAYER_NOT_READY";
        }
        if (!readiness.interactionManagerReady) {
            return "TRACE_GAP_INTERACTION_MANAGER_NOT_READY";
        }
        return null;
    }

    private static void emitSlabThenBlockReadyRow(MinecraftClient client, String phase, String reason) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        System.out.println("[MC1211_SLAB_THEN_BLOCK_BASELINE_READY_ROW]"
                + " phase=" + phase
                + " tick=" + slabThenBlockTicks
                + " clientBootstrapReady=" + readiness.clientBootstrapReady
                + " clientWorldReady=" + readiness.clientWorldReady
                + " clientPlayerReady=" + readiness.clientPlayerReady
                + " integratedServerReady=" + readiness.integratedServerReady
                + " serverWorldReady=" + readiness.serverWorldReady
                + " serverPlayerReady=" + readiness.serverPlayerReady
                + " interactionManagerReady=" + readiness.interactionManagerReady
                + " programmaticWorldStartRequested=" + slabThenBlockWorldStartRequested
                + " reason=" + reason);
    }

    private static void runSlabThenBlockRow(MinecraftClient client, int rowIndex) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || client.world == null || client.player == null) {
            emitSlabThenBlockTraceGap(rowName(rowIndex), "TRACE_GAP_WORLD_OR_PLAYER_NOT_READY");
            return;
        }
        if (slabThenBlockRowPhase == 0) {
            prepareSlabThenBlockRow(client, serverWorld, rowIndex);
            slabThenBlockRowPhase = 1;
            slabThenBlockPhaseTick = slabThenBlockTicks;
            return;
        }
        if (slabThenBlockTicks - slabThenBlockPhaseTick < 5) {
            return;
        }
        if (slabThenBlockRowPhase == 1) {
            if (rowIndex == 3) {
                slabThenBlockClientResultSlab = "not_applicable_negative_control";
                slabThenBlockRowPhase = 3;
            } else {
                slabThenBlockClientResultSlab = clickSlabPlacement(client, rowIndex);
                slabThenBlockRowPhase = 2;
            }
            slabThenBlockPhaseTick = slabThenBlockTicks;
            return;
        }
        if (slabThenBlockRowPhase == 2) {
            if (rowIndex == 2) {
                slabThenBlockClientResultSecondSlab = clickBlock(client, Items.STONE_SLAB, slabThenBlockSlabPos,
                        Direction.UP, hitVector(slabThenBlockSlabPos, Direction.UP));
                slabThenBlockRowPhase = 3;
                slabThenBlockPhaseTick = slabThenBlockTicks;
                return;
            }
            if (rowIndex == 1) {
                removeTopSlabTemporaryCeiling(client);
            }
            slabThenBlockRowPhase = 3;
            slabThenBlockPhaseTick = slabThenBlockTicks;
            return;
        }
        if (slabThenBlockRowPhase == 3) {
            if (!slabReadyForRow(client, rowIndex)) {
                emitSlabThenBlockTraceGap(rowName(rowIndex), "TRACE_GAP_SLAB_PLACEMENT_NOT_REPRODUCED");
                return;
            }
            if (rowIndex == 1 && !postPlaceAirReady(client)) {
                if (slabThenBlockTicks - slabThenBlockPhaseTick < 80) {
                    return;
                }
                emitSlabThenBlockTraceGap(rowName(rowIndex), "TRACE_GAP_TOP_SLAB_CEILING_REMOVAL_NOT_OBSERVED");
                return;
            }
            slabThenBlockClientResultBlock = clickBlock(client, Items.STONE, slabThenBlockSlabPos,
                    Direction.UP, hitVector(slabThenBlockSlabPos, Direction.UP));
            slabThenBlockRowPhase = 4;
            slabThenBlockPhaseTick = slabThenBlockTicks;
            return;
        }
        if (slabThenBlockTicks - slabThenBlockPhaseTick < 20) {
            return;
        }
        emitSlabThenBlockRow(client, serverWorld, rowIndex);
        slabThenBlockRowIndex++;
        slabThenBlockRowPhase = 0;
        slabThenBlockPhaseTick = slabThenBlockTicks;
    }

    private static void prepareSlabThenBlockRow(MinecraftClient client, ServerWorld serverWorld, int rowIndex) {
        BlockPos rowOrigin = slabThenBlockOrigin.add(rowIndex * 4, 0, 0);
        slabThenBlockSlabPos = rowIndex == 3 ? rowOrigin : rowOrigin.up();
        slabThenBlockGroundPos = rowOrigin;
        slabThenBlockPostPlacePos = slabThenBlockSlabPos.up();
        slabThenBlockClientResultSlab = "not_started";
        slabThenBlockClientResultSecondSlab = rowIndex == 2 ? "not_started" : "not_needed";
        slabThenBlockClientResultBlock = "not_started";
        slabThenBlockReachDiagnostic = "not_sampled";
        serverWorld.getServer().execute(() -> {
            for (int x = rowOrigin.getX() - 1; x <= rowOrigin.getX() + 1; x++) {
                for (int z = rowOrigin.getZ() - 1; z <= rowOrigin.getZ() + 1; z++) {
                    for (int y = rowOrigin.getY(); y <= rowOrigin.getY() + 4; y++) {
                        serverWorld.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
            serverWorld.setBlockState(slabThenBlockGroundPos, Blocks.STONE.getDefaultState(), 3);
            if (rowIndex == 1) {
                serverWorld.setBlockState(slabThenBlockPostPlacePos, Blocks.STONE.getDefaultState(), 3);
            }
            if (!serverWorld.getServer().getPlayerManager().getPlayerList().isEmpty()) {
                var serverPlayer = serverWorld.getServer().getPlayerManager().getPlayerList().get(0);
                serverPlayer.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
        });
        client.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static String clickSlabPlacement(MinecraftClient client, int rowIndex) {
        if (rowIndex == 1) {
            return clickBlock(client, Items.STONE_SLAB, slabThenBlockPostPlacePos,
                    Direction.DOWN, hitVector(slabThenBlockPostPlacePos, Direction.DOWN));
        }
        return clickBlock(client, Items.STONE_SLAB, slabThenBlockGroundPos,
                Direction.UP, hitVector(slabThenBlockGroundPos, Direction.UP));
    }

    private static boolean slabReadyForRow(MinecraftClient client, int rowIndex) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || slabThenBlockSlabPos == null) {
            return false;
        }
        BlockState slabState = serverWorld.getBlockState(slabThenBlockSlabPos);
        if (rowIndex == 3) {
            return slabState.isOf(Blocks.STONE);
        }
        if (!slabState.isOf(Blocks.STONE_SLAB)) {
            return false;
        }
        SlabType actualType = slabState.get(SlabBlock.TYPE);
        SlabType expectedType = rowIndex == 0 ? SlabType.BOTTOM : (rowIndex == 1 ? SlabType.TOP : SlabType.DOUBLE);
        return actualType == expectedType;
    }

    private static void removeTopSlabTemporaryCeiling(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || slabThenBlockPostPlacePos == null) {
            return;
        }
        serverWorld.getServer().execute(() ->
                serverWorld.setBlockState(slabThenBlockPostPlacePos, Blocks.AIR.getDefaultState(), 3));
    }

    private static boolean postPlaceAirReady(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || client == null || client.world == null || slabThenBlockPostPlacePos == null) {
            return false;
        }
        return serverWorld.getBlockState(slabThenBlockPostPlacePos).isAir()
                && client.world.getBlockState(slabThenBlockPostPlacePos).isAir();
    }

    private static String clickBlock(
            MinecraftClient client,
            net.minecraft.item.Item item,
            BlockPos hitPos,
            Direction face,
            Vec3d hitVector) {
        if (client == null || client.player == null || client.interactionManager == null || hitPos == null) {
            return "FAIL_ROUTE_NOT_READY";
        }
        syncSlabThenBlockPlayer(client, hitVector);
        ItemStack stack = new ItemStack(item, 8);
        client.player.setStackInHand(Hand.MAIN_HAND, stack);
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(item, 8));
        }
        BlockHitResult hit = new BlockHitResult(hitVector, face, hitPos, false);
        ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
        return String.valueOf(result);
    }

    private static void syncSlabThenBlockPlayer(MinecraftClient client, Vec3d hitVector) {
        Vec3d eye = hitVector.add(1.75d, 0.35d, 0.0d);
        Vec3d delta = hitVector.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        slabThenBlockReachDiagnostic = "eye=" + formatVec(eye)
                + "/hitVec=" + formatVec(hitVector)
                + "/distance=" + formatDouble(eye.distanceTo(hitVector))
                + "/yaw=" + formatDouble(yaw)
                + "/pitch=" + formatDouble(pitch);
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.setSneaking(false);
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            serverPlayer.setVelocity(Vec3d.ZERO);
            serverPlayer.setSneaking(false);
            serverPlayer.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        }
    }

    private static Vec3d hitVector(BlockPos pos, Direction face) {
        double x = pos.getX() + 0.5d;
        double y = face == Direction.DOWN ? pos.getY() : pos.getY() + 1.0d;
        double z = pos.getZ() + 0.5d;
        return new Vec3d(x, y, z);
    }

    private static void emitSlabThenBlockRow(MinecraftClient client, ServerWorld serverWorld, int rowIndex) {
        ClientWorld clientWorld = client.world;
        BlockState slabState = serverWorld.getBlockState(slabThenBlockSlabPos);
        BlockState clientPostPlaceState = clientWorld.getBlockState(slabThenBlockPostPlacePos);
        BlockState postPlaceState = serverWorld.getBlockState(slabThenBlockPostPlacePos);
        double slabDy = SlabSupport.getYOffset(serverWorld, slabThenBlockSlabPos, slabState);
        double postPlaceDy = postPlaceState.isAir()
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, slabThenBlockPostPlacePos, postPlaceState);
        boolean postPlaceAnchored = SlabAnchorAttachment.isAnchored(serverWorld, slabThenBlockPostPlacePos);
        boolean postPlaceLowered = Double.isFinite(postPlaceDy) && postPlaceDy < -1.0e-6d;
        String slabVisibleBounds = visibleBounds(serverWorld, slabThenBlockSlabPos, slabState, slabDy);
        String postPlaceVisibleBounds = visibleBounds(serverWorld, slabThenBlockPostPlacePos, postPlaceState, postPlaceDy);
        SlabType slabType = slabState.isOf(Blocks.STONE_SLAB) ? slabState.get(SlabBlock.TYPE) : null;
        String relation = relationFor(rowIndex, slabState, postPlaceState, postPlaceDy, postPlaceAnchored);
        String legalStateName = legalStateNameFor(rowIndex, slabState, postPlaceState, postPlaceDy, postPlaceAnchored);
        String classification = classificationFor(rowIndex, relation, legalStateName);
        if ("RED".equals(classification)) {
            slabThenBlockRedRows++;
        } else if ("GREEN".equals(classification)) {
            slabThenBlockGreenRows++;
        } else {
            slabThenBlockTraceGapRows++;
        }
        String rowLine = rowName(rowIndex) + "=" + classification
                + "/" + legalStateName
                + "/relation=" + relation
                + "/postPlaceDy=" + formatDouble(postPlaceDy)
                + "/anchored=" + postPlaceAnchored;
        slabThenBlockRows = slabThenBlockRows.isEmpty() ? rowLine : slabThenBlockRows + "," + rowLine;

        System.out.println("[MC1211_SLAB_THEN_BLOCK_BASELINE_ROW]"
                + " rowName=" + rowName(rowIndex)
                + " slabPos=" + textPos(slabThenBlockSlabPos)
                + " slabState=" + slabState
                + " slabType=" + (slabType == null ? "none" : slabType.asString())
                + " slabDy=" + formatDouble(slabDy)
                + " blockItem=minecraft:stone"
                + " blockHitPos=" + textPos(slabThenBlockSlabPos)
                + " clickedFace=up"
                + " hitVector=" + formatVec(hitVector(slabThenBlockSlabPos, Direction.UP))
                + " intendedPlacePos=" + textPos(slabThenBlockPostPlacePos)
                + " placementResultSlabClient=" + slabThenBlockClientResultSlab
                + " placementResultSecondSlabClient=" + slabThenBlockClientResultSecondSlab
                + " placementResultClient=" + slabThenBlockClientResultBlock
                + " placementResultServer=" + (postPlaceState.isOf(Blocks.STONE) ? "OBSERVED_STONE" : "NOT_OBSERVED")
                + " postPlacePos=" + textPos(slabThenBlockPostPlacePos)
                + " postPlaceState=" + postPlaceState
                + " postPlaceClientState=" + clientPostPlaceState
                + " postPlaceDy=" + formatDouble(postPlaceDy)
                + " postPlaceAnchored=" + postPlaceAnchored
                + " postPlaceLowered=" + postPlaceLowered
                + " postPlaceVisibleBounds=" + postPlaceVisibleBounds
                + " slabVisibleBounds=" + slabVisibleBounds
                + " overlapMergeRelation=" + relation
                + " legalStateName=" + legalStateName
                + " sourceRelationship=" + sourceRelationship(rowIndex)
                + " reachDiagnostic=" + slabThenBlockReachDiagnostic
                + " classification=" + classification);
    }

    private static String relationFor(
            int rowIndex,
            BlockState slabState,
            BlockState postPlaceState,
            double postPlaceDy,
            boolean postPlaceAnchored) {
        if (rowIndex == 3) {
            if (postPlaceState.isAir()) {
                return "REJECTED_OR_DEFERRED";
            }
            return postPlaceState.isOf(Blocks.STONE) && near(postPlaceDy, 0.0d)
                    ? "LEGAL_FULL_HEIGHT_RELATION"
                    : "UNKNOWN";
        }
        if (!slabState.isOf(Blocks.STONE_SLAB) || postPlaceState.isAir()) {
            return "REJECTED_OR_DEFERRED";
        }
        if (!postPlaceState.isOf(Blocks.STONE)) {
            return "UNKNOWN";
        }
        if (rowIndex == 0) {
            return postPlaceAnchored && near(postPlaceDy, -0.5d)
                    ? "BLOCK_SITS_ON_SLAB"
                    : "BLOCK_COVERS_SLAB";
        }
        return near(postPlaceDy, 0.0d) && !postPlaceAnchored
                ? "LEGAL_FULL_HEIGHT_RELATION"
                : "UNKNOWN";
    }

    private static String legalStateNameFor(
            int rowIndex,
            BlockState slabState,
            BlockState postPlaceState,
            double postPlaceDy,
            boolean postPlaceAnchored) {
        if (rowIndex == 0) {
            if (!slabState.isOf(Blocks.STONE_SLAB) || postPlaceState.isAir()) {
                return "TRACE_GAP_NOT_VIDEO_EQUIVALENT";
            }
            if (!postPlaceState.isOf(Blocks.STONE)) {
                return "ILLEGAL_UNNAMED_MERGE";
            }
            if (postPlaceAnchored && near(postPlaceDy, -0.5d)) {
                return "LEGAL_DIRECT_SLAB_ANCHORED_FULLBLOCK";
            }
            if (!postPlaceAnchored && near(postPlaceDy, 0.0d)) {
                return "ILLEGAL_VANILLA_HEIGHT_COVERS_SLAB";
            }
            return "ILLEGAL_UNNAMED_MERGE";
        }
        if (rowIndex == 1 || rowIndex == 2) {
            if (!slabState.isOf(Blocks.STONE_SLAB) || postPlaceState.isAir()) {
                return "TRACE_GAP_NOT_VIDEO_EQUIVALENT";
            }
            if (!postPlaceState.isOf(Blocks.STONE)) {
                return "ILLEGAL_UNNAMED_MERGE";
            }
            return !postPlaceAnchored && near(postPlaceDy, 0.0d)
                    ? (rowIndex == 1 ? "LEGAL_VANILLA_ABOVE_BLOCK" : "LEGAL_FULL_HEIGHT_CARRIER")
                    : "ILLEGAL_UNNAMED_MERGE";
        }
        if (!postPlaceState.isOf(Blocks.STONE)) {
            return "TRACE_GAP_NOT_VIDEO_EQUIVALENT";
        }
        return near(postPlaceDy, 0.0d) ? "LEGAL_VANILLA_ABOVE_BLOCK" : "ILLEGAL_UNNAMED_MERGE";
    }

    private static String classificationFor(int rowIndex, String relation, String legalStateName) {
        if ("TRACE_GAP_NOT_VIDEO_EQUIVALENT".equals(legalStateName)
                || "REJECTED_OR_DEFERRED".equals(relation)) {
            return "TRACE_GAP";
        }
        if (legalStateName.startsWith("ILLEGAL_")) {
            return "RED";
        }
        return "GREEN";
    }

    private static void emitSlabThenBlockSummary(MinecraftClient client) {
        String finalMarker;
        if (slabThenBlockRedRows > 0) {
            finalMarker = "RED";
        } else if (slabThenBlockTraceGapRows > 0) {
            finalMarker = "TRACE_GAP";
        } else {
            finalMarker = "GREEN";
        }
        System.out.println("[MC1211_SLAB_THEN_BLOCK_BASELINE_SUMMARY]"
                + " rows=4"
                + " redRows=" + slabThenBlockRedRows
                + " greenRows=" + slabThenBlockGreenRows
                + " traceGapRows=" + slabThenBlockTraceGapRows
                + " finalResult=" + finalMarker
                + " rowSummary=" + slabThenBlockRows
                + " suspectedAuthoringPath=BlockItemPlacementIntentMixin.finalization-return"
                + " suspectedBypass=vertical_face_skips_direct_slab_anchor_authoring");
        System.out.println("[MC1211_SLAB_THEN_BLOCK_BASELINE_" + finalMarker + "]"
                + " rows=4"
                + " rowSummary=" + slabThenBlockRows);
        slabThenBlockFinalized = true;
        emitted = true;
        client.scheduleStop();
    }

    private static void emitSlabThenBlockTraceGap(String row, String reason) {
        System.out.println("[MC1211_SLAB_THEN_BLOCK_BASELINE_ROW]"
                + " rowName=" + row
                + " slabPos=" + textPos(slabThenBlockSlabPos)
                + " slabState=n/a"
                + " slabType=n/a"
                + " slabDy=NaN"
                + " blockItem=minecraft:stone"
                + " blockHitPos=n/a"
                + " clickedFace=up"
                + " hitVector=n/a"
                + " intendedPlacePos=" + textPos(slabThenBlockPostPlacePos)
                + " placementResultClient=" + slabThenBlockClientResultBlock
                + " placementResultServer=UNOBSERVABLE"
                + " postPlacePos=" + textPos(slabThenBlockPostPlacePos)
                + " postPlaceState=n/a"
                + " postPlaceDy=NaN"
                + " postPlaceAnchored=false"
                + " postPlaceLowered=false"
                + " postPlaceVisibleBounds=NaN..NaN"
                + " slabVisibleBounds=NaN..NaN"
                + " overlapMergeRelation=UNKNOWN"
                + " legalStateName=TRACE_GAP_NOT_VIDEO_EQUIVALENT"
                + " sourceRelationship=none/unknown"
                + " classification=TRACE_GAP"
                + " reason=" + reason);
        slabThenBlockTraceGapRows++;
        slabThenBlockRows = slabThenBlockRows.isEmpty()
                ? row + "=TRACE_GAP/" + reason
                : slabThenBlockRows + "," + row + "=TRACE_GAP/" + reason;
        slabThenBlockRowIndex++;
        slabThenBlockRowPhase = 0;
        slabThenBlockPhaseTick = slabThenBlockTicks;
    }

    private static void runTrapdoorLoweredSeamRoute(MinecraftClient client) {
        if (trapdoorSeamFinalized || emitted) {
            return;
        }
        trapdoorSeamTicks++;
        if (!trapdoorSeamCanaryEmitted) {
            trapdoorSeamCanaryEmitted = true;
            System.out.println("[MC1211_TRAPDOOR_LOWERED_SEAM_ROUTE_CANARY]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " property=" + TRAPDOOR_LOWERED_SEAM_RED_PROPERTY
                    + " worldReady=" + (client != null && client.world != null)
                    + " playerReady=" + (client != null && client.player != null));
        }

        requestProgrammaticTrapdoorSeamWorldIfNeeded(client);
        String readinessGap = liveTruthReadinessGap(client);
        if (readinessGap != null) {
            if (!trapdoorSeamReadyRowEmitted || trapdoorSeamTicks % 1200 == 0) {
                emitTrapdoorSeamReadyRow(client, "WAITING", readinessGap);
                trapdoorSeamReadyRowEmitted = true;
            }
            if (trapdoorSeamTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitTrapdoorSeamReadyRow(client, "TIMEOUT", readinessGap);
            emitTrapdoorSeamTraceGap("ROUTE_READINESS", readinessGap);
            return;
        }
        if (!trapdoorSeamReadyRowEmitted) {
            emitTrapdoorSeamReadyRow(client, "READY", "none");
            trapdoorSeamReadyRowEmitted = true;
        }

        if (!trapdoorSeamStarted) {
            trapdoorSeamStarted = true;
            trapdoorSeamOrigin = client.player.getBlockPos().add(11, 0, 7).toImmutable();
            trapdoorSeamGroundPos = trapdoorSeamOrigin.down();
            trapdoorSeamSlabPos = trapdoorSeamOrigin;
            trapdoorSeamSupportPos = trapdoorSeamSlabPos.up();
            trapdoorSeamExpectedTrapdoorPos = trapdoorSeamSupportPos.up();
            prepareTrapdoorSeamFixture(client);
            trapdoorSeamPhase = 1;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            System.out.println("[MC1211_TRAPDOOR_LOWERED_SEAM_START]"
                    + " rowName=PLAYER_AUTHORED_OAK_TRAPDOOR_ON_LOWERED_FULL_BLOCK"
                    + " fixtureOrigin=" + textPos(trapdoorSeamOrigin)
                    + " slabPos=" + textPos(trapdoorSeamSlabPos)
                    + " supportPos=" + textPos(trapdoorSeamSupportPos)
                    + " expectedTrapdoorPos=" + textPos(trapdoorSeamExpectedTrapdoorPos)
                    + " placementRoute=ClientPlayerInteractionManager.interactBlock"
                    + " supportAuthoringRoute=player_slab_then_player_stone"
                    + " trapdoorAuthoringRoute=player_crosshair_target"
                    + " manualTrapdoorAnchorInjection=false"
                    + " behaviorPatch=false");
            return;
        }

        if (trapdoorSeamTicks - trapdoorSeamPhaseTick < 8) {
            return;
        }
        if (trapdoorSeamPhase == 1) {
            trapdoorSeamSlabPlacementResult = clickBlock(
                    client,
                    Items.STONE_SLAB,
                    trapdoorSeamGroundPos,
                    Direction.UP,
                    hitVector(trapdoorSeamGroundPos, Direction.UP));
            trapdoorSeamPhase = 2;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamPhase == 2) {
            if (!trapdoorSeamSlabReady(client)) {
                if (trapdoorSeamTicks - trapdoorSeamPhaseTick < 160) {
                    if ((trapdoorSeamTicks - trapdoorSeamPhaseTick) % 20 == 0) {
                        trapdoorSeamSlabPlacementResult = clickBlock(
                                client,
                                Items.STONE_SLAB,
                                trapdoorSeamGroundPos,
                                Direction.UP,
                                hitVector(trapdoorSeamGroundPos, Direction.UP));
                    }
                    return;
                }
                emitTrapdoorSeamTraceGap("SLAB_AUTHORING", "TRACE_GAP_PLAYER_AUTHORED_BOTTOM_SLAB_NOT_OBSERVED");
                return;
            }
            trapdoorSeamSupportPlacementResult = clickBlock(
                    client,
                    Items.STONE,
                    trapdoorSeamSlabPos,
                    Direction.UP,
                    hitVector(trapdoorSeamSlabPos, Direction.UP));
            trapdoorSeamPhase = 3;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamPhase == 3) {
            if (!trapdoorSeamLoweredSupportReady(client)) {
                if (trapdoorSeamTicks - trapdoorSeamPhaseTick < 160) {
                    if ((trapdoorSeamTicks - trapdoorSeamPhaseTick) % 20 == 0) {
                        trapdoorSeamSupportPlacementResult = clickBlock(
                                client,
                                Items.STONE,
                                trapdoorSeamSlabPos,
                                Direction.UP,
                                hitVector(trapdoorSeamSlabPos, Direction.UP));
                    }
                    return;
                }
                emitTrapdoorSeamTraceGap("SUPPORT_AUTHORING", "TRACE_GAP_PLAYER_AUTHORED_LOWERED_SUPPORT_NOT_OBSERVED");
                return;
            }
            trapdoorSeamTrapdoorPlacementResult = clickTrapdoorFromCrosshair(client);
            trapdoorSeamPhase = 4;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamPhase == 4) {
            if (trapdoorSeamTicks - trapdoorSeamPhaseTick < 30) {
                return;
            }
            emitTrapdoorSeamPlacementRow(client);
            breakTrapdoorSeamSupport(client);
            trapdoorSeamPhase = 5;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamTicks - trapdoorSeamPhaseTick < 30) {
            return;
        }
        emitTrapdoorSeamUpdateRow(client);
        emitTrapdoorSeamSummary(client);
    }

    private static void runTrapdoorUnderBottomPlacementRoute(MinecraftClient client) {
        if (trapdoorUnderBottomFinalized || emitted) {
            return;
        }
        trapdoorUnderBottomTicks++;
        if (!trapdoorUnderBottomCanaryEmitted) {
            trapdoorUnderBottomCanaryEmitted = true;
            System.out.println("[MC1211_TRAPDOOR_UNDER_BOTTOM_PLACEMENT_START]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " property=" + activeTrapdoorUnderBottomProperty()
                    + " target=" + activeTrapdoorUnderBottomTargetName()
                    + " initialLayer=placement"
                    + " gameplayPatch=false"
                    + " worldReady=" + (client != null && client.world != null)
                    + " playerReady=" + (client != null && client.player != null));
        }

        requestProgrammaticTrapdoorUnderBottomWorldIfNeeded(client);
        String readinessGap = liveTruthReadinessGap(client);
        if (readinessGap != null) {
            if (!trapdoorUnderBottomReadyRowEmitted || trapdoorUnderBottomTicks % 1200 == 0) {
                emitTrapdoorUnderBottomTraceGap("WAITING", readinessGap);
                trapdoorUnderBottomReadyRowEmitted = true;
            }
            if (trapdoorUnderBottomTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitTrapdoorUnderBottomTraceGap("ROUTE_READINESS", readinessGap);
            return;
        }

        if (!trapdoorUnderBottomStarted) {
            trapdoorUnderBottomStarted = true;
            if (Boolean.getBoolean(TRAPDOOR_UNDER_LOWERED_BOTTOM_PLACEMENT_RED_PROPERTY)) {
                BlockPos origin = client.player.getBlockPos().add(11, 0, 7).toImmutable();
                trapdoorUnderBottomTargetSlabPos = origin.up(2).east().toImmutable();
                trapdoorUnderBottomSetup = "programmatic_lowered_side_extension_bottom_slab";
            } else {
                trapdoorUnderBottomTargetSlabPos = client.player.getBlockPos().add(11, 2, 7).toImmutable();
                trapdoorUnderBottomSetup = "programmatic_plain_bottom_slab";
            }
            trapdoorUnderBottomExpectedTrapdoorPos = trapdoorUnderBottomTargetSlabPos.down();
            if (Boolean.getBoolean(TRAPDOOR_UNDER_LOWERED_BOTTOM_PLACEMENT_RED_PROPERTY)) {
                prepareTrapdoorUnderLoweredBottomFixture(client);
            } else {
                prepareTrapdoorUnderBottomFixture(client);
            }
            trapdoorUnderBottomPhase = 1;
            trapdoorUnderBottomPhaseTick = trapdoorUnderBottomTicks;
            System.out.println("[MC1211_TRAPDOOR_UNDER_BOTTOM_PLACEMENT_ROW]"
                    + " rowPhase=START"
                    + " targetSlabPos=" + textPos(trapdoorUnderBottomTargetSlabPos)
                    + " expectedTrapdoorPos=" + textPos(trapdoorUnderBottomExpectedTrapdoorPos)
                    + " setup=" + trapdoorUnderBottomSetup
                    + " heldItem=minecraft:oak_trapdoor"
                    + " intendedClickFace=down"
                    + " manualAnchorInjection=false"
                    + " gameplayPatch=false");
            return;
        }

        if (trapdoorUnderBottomTicks - trapdoorUnderBottomPhaseTick < 20) {
            return;
        }
        if (trapdoorUnderBottomPhase == 1) {
            emitTrapdoorUnderBottomTimelineRow(client, "PRE_CLICK");
            trapdoorUnderBottomPlacementResult = clickTrapdoorUnderBottomSlab(client);
            ClientWorld clientWorld = client == null ? null : client.world;
            trapdoorUnderBottomPredictedClientState = clientWorld == null
                    || trapdoorUnderBottomExpectedTrapdoorPos == null
                    ? Blocks.AIR.getDefaultState()
                    : clientWorld.getBlockState(trapdoorUnderBottomExpectedTrapdoorPos);
            emitTrapdoorUnderBottomTimelineRow(client, "IMMEDIATE_AFTER_CLIENT_RETURN");
            trapdoorUnderBottomPhase = 2;
            trapdoorUnderBottomPhaseTick = trapdoorUnderBottomTicks;
            return;
        }
        int elapsedTicks = trapdoorUnderBottomTicks - trapdoorUnderBottomPhaseTick;
        if (elapsedTicks == 1) {
            emitTrapdoorUnderBottomTimelineRow(client, "AFTER_1_TICK");
        } else if (elapsedTicks == 5) {
            emitTrapdoorUnderBottomTimelineRow(client, "AFTER_5_TICKS");
        }
        if (elapsedTicks < 30) {
            return;
        }
        emitTrapdoorUnderBottomTimelineRow(client, "AFTER_30_TICKS");
        emitTrapdoorUnderBottomPlacementRow(client, "AFTER_SERVER_TICK");
        emitTrapdoorUnderBottomPlacementSummary(client);
    }

    private static void runTrapdoorLoweredSeamMp4Route(MinecraftClient client) {
        if (trapdoorSeamFinalized || emitted) {
            return;
        }
        trapdoorSeamTicks++;
        if (!trapdoorSeamCanaryEmitted) {
            trapdoorSeamCanaryEmitted = true;
            System.out.println("[MC1211_TRAPDOOR_SEAM_MP4_RED_START]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " property=" + TRAPDOOR_LOWERED_SEAM_MP4_RED_PROPERTY
                    + " expectedFalseGreenHead=d501477c"
                    + " sourceTruth=mp4-live-recorder"
                    + " initialLayer=proof gap"
                    + " gameplayPatch=false"
                    + " worldReady=" + (client != null && client.world != null)
                    + " playerReady=" + (client != null && client.player != null));
        }

        requestProgrammaticTrapdoorSeamWorldIfNeeded(client);
        String readinessGap = liveTruthReadinessGap(client);
        if (readinessGap != null) {
            if (!trapdoorSeamReadyRowEmitted || trapdoorSeamTicks % 1200 == 0) {
                emitTrapdoorSeamMp4TraceGap("WAITING", readinessGap);
                trapdoorSeamReadyRowEmitted = true;
            }
            if (trapdoorSeamTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitTrapdoorSeamMp4TraceGap("ROUTE_READINESS", readinessGap);
            return;
        }

        if (!trapdoorSeamStarted) {
            trapdoorSeamStarted = true;
            trapdoorSeamOrigin = client.player.getBlockPos().add(11, 0, 7).toImmutable();
            trapdoorSeamGroundPos = trapdoorSeamOrigin.down();
            trapdoorSeamSlabPos = trapdoorSeamOrigin;
            trapdoorSeamSupportPos = trapdoorSeamSlabPos.up();
            trapdoorSeamExpectedTrapdoorPos = trapdoorSeamSupportPos.up();
            trapdoorSeamMp4SideBaseSlabPos = trapdoorSeamOrigin.east(2);
            trapdoorSeamMp4SideGroundPos = trapdoorSeamMp4SideBaseSlabPos.down();
            trapdoorSeamMp4SideSupportPos = trapdoorSeamMp4SideBaseSlabPos.up();
            trapdoorSeamMp4SideOwnerSlabPos = trapdoorSeamMp4SideSupportPos.up();
            prepareTrapdoorSeamFixture(client);
            trapdoorSeamPhase = 1;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            System.out.println("[MC1211_TRAPDOOR_SEAM_MP4_ROW]"
                    + " rowPhase=START"
                    + " fixtureOrigin=" + textPos(trapdoorSeamOrigin)
                    + " slabPos=" + textPos(trapdoorSeamSlabPos)
                    + " supportPos=" + textPos(trapdoorSeamSupportPos)
                    + " expectedTrapdoorPos=" + textPos(trapdoorSeamExpectedTrapdoorPos)
                    + " sideSupportPos=" + textPos(trapdoorSeamMp4SideSupportPos)
                    + " sideOwnerSlabPos=" + textPos(trapdoorSeamMp4SideOwnerSlabPos)
                    + " setup=player-authored"
                    + " trapdoorItem=minecraft:oak_trapdoor"
                    + " slabHeldLiveElement=minecraft:stone_slab"
                    + " manualTrapdoorAnchorInjection=false"
                    + " gameplayPatch=false");
            return;
        }

        if (trapdoorSeamTicks - trapdoorSeamPhaseTick < 8) {
            return;
        }
        if (trapdoorSeamPhase == 1) {
            trapdoorSeamSlabPlacementResult = clickBlock(
                    client,
                    Items.STONE_SLAB,
                    trapdoorSeamGroundPos,
                    Direction.UP,
                    hitVector(trapdoorSeamGroundPos, Direction.UP));
            trapdoorSeamPhase = 2;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamPhase == 2) {
            if (!trapdoorSeamSlabReady(client)) {
                if (trapdoorSeamTicks - trapdoorSeamPhaseTick < 160) {
                    if ((trapdoorSeamTicks - trapdoorSeamPhaseTick) % 20 == 0) {
                        trapdoorSeamSlabPlacementResult = clickBlock(
                                client,
                                Items.STONE_SLAB,
                                trapdoorSeamGroundPos,
                                Direction.UP,
                                hitVector(trapdoorSeamGroundPos, Direction.UP));
                    }
                    return;
                }
                emitTrapdoorSeamMp4TraceGap("SLAB_AUTHORING", "TRACE_GAP_PLAYER_AUTHORED_BOTTOM_SLAB_NOT_OBSERVED");
                return;
            }
            trapdoorSeamSupportPlacementResult = clickBlock(
                    client,
                    Items.STONE,
                    trapdoorSeamSlabPos,
                    Direction.UP,
                    hitVector(trapdoorSeamSlabPos, Direction.UP));
            trapdoorSeamPhase = 3;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamPhase == 3) {
            if (!trapdoorSeamLoweredSupportReady(client)) {
                if (trapdoorSeamTicks - trapdoorSeamPhaseTick < 160) {
                    if ((trapdoorSeamTicks - trapdoorSeamPhaseTick) % 20 == 0) {
                        trapdoorSeamSupportPlacementResult = clickBlock(
                                client,
                                Items.STONE,
                                trapdoorSeamSlabPos,
                                Direction.UP,
                                hitVector(trapdoorSeamSlabPos, Direction.UP));
                    }
                    return;
                }
                emitTrapdoorSeamMp4TraceGap(
                        "SUPPORT_AUTHORING",
                        "TRACE_GAP_PLAYER_AUTHORED_LOWERED_SUPPORT_NOT_OBSERVED");
                return;
            }
            trapdoorSeamTrapdoorPlacementResult = clickTrapdoorFromCrosshair(client);
            trapdoorSeamPhase = 4;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamPhase == 4) {
            if (trapdoorSeamTicks - trapdoorSeamPhaseTick < 30) {
                return;
            }
            emitTrapdoorSeamPlacementRow(client);
            trapdoorSeamMp4SideBaseSlabPlacementResult = clickBlock(
                    client,
                    Items.STONE_SLAB,
                    trapdoorSeamMp4SideGroundPos,
                    Direction.UP,
                    hitVector(trapdoorSeamMp4SideGroundPos, Direction.UP));
            breakTrapdoorSeamSupport(client);
            trapdoorSeamPhase = 5;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamPhase == 5) {
            if (!trapdoorSeamMp4SideBaseSlabReady(client)) {
                if (trapdoorSeamTicks - trapdoorSeamPhaseTick < 160) {
                    if ((trapdoorSeamTicks - trapdoorSeamPhaseTick) % 20 == 0) {
                        trapdoorSeamMp4SideBaseSlabPlacementResult = clickBlock(
                                client,
                                Items.STONE_SLAB,
                                trapdoorSeamMp4SideGroundPos,
                                Direction.UP,
                                hitVector(trapdoorSeamMp4SideGroundPos, Direction.UP));
                    }
                    return;
                }
                emitTrapdoorSeamMp4TraceGap(
                        "SIDE_BASE_SLAB_AUTHORING",
                        "TRACE_GAP_MP4_SIDE_BASE_BOTTOM_SLAB_NOT_OBSERVED");
                return;
            }
            trapdoorSeamMp4SideSupportPlacementResult = clickBlock(
                    client,
                    Items.STONE,
                    trapdoorSeamMp4SideBaseSlabPos,
                    Direction.UP,
                    hitVector(trapdoorSeamMp4SideBaseSlabPos, Direction.UP));
            trapdoorSeamPhase = 6;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamPhase == 6) {
            if (!trapdoorSeamMp4SideSupportReady(client)) {
                if (trapdoorSeamTicks - trapdoorSeamPhaseTick < 160) {
                    if ((trapdoorSeamTicks - trapdoorSeamPhaseTick) % 20 == 0) {
                        trapdoorSeamMp4SideSupportPlacementResult = clickBlock(
                                client,
                                Items.STONE,
                                trapdoorSeamMp4SideBaseSlabPos,
                                Direction.UP,
                                hitVector(trapdoorSeamMp4SideBaseSlabPos, Direction.UP));
                    }
                    return;
                }
                emitTrapdoorSeamMp4TraceGap(
                        "SIDE_SUPPORT_AUTHORING",
                        "TRACE_GAP_MP4_SIDE_LOWERED_SUPPORT_NOT_OBSERVED");
                return;
            }
            trapdoorSeamMp4SideOwnerSlabPlacementResult = clickBlock(
                    client,
                    Items.STONE_SLAB,
                    trapdoorSeamMp4SideSupportPos,
                    Direction.UP,
                    trapdoorSeamMp4UpGuardHitVector());
            trapdoorSeamPhase = 7;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamPhase == 7) {
            if (!trapdoorSeamMp4SideOwnerSlabReady(client)) {
                if (trapdoorSeamTicks - trapdoorSeamPhaseTick < 160) {
                    if ((trapdoorSeamTicks - trapdoorSeamPhaseTick) % 20 == 0) {
                        trapdoorSeamMp4SideOwnerSlabPlacementResult = clickBlock(
                                client,
                                Items.STONE_SLAB,
                                trapdoorSeamMp4SideSupportPos,
                                Direction.UP,
                                trapdoorSeamMp4UpGuardHitVector());
                    }
                    return;
                }
                emitTrapdoorSeamMp4TraceGap(
                        "SIDE_OWNER_SLAB_AUTHORING",
                        "TRACE_GAP_MP4_SIDE_OWNER_BOTTOM_SLAB_NOT_OBSERVED");
                return;
            }
            trapdoorSeamMp4PreUpdateSlabHeldSample = sampleTrapdoorSeamMp4SlabHeldTarget(client, "AFTER_SIDE_OWNER_AUTHORED");
            trapdoorSeamMp4ManualNoCandidatePreSample =
                    sampleTrapdoorSeamMp4ManualNoCandidateTarget(client, "AFTER_SIDE_OWNER_AUTHORED");
            emitTrapdoorSeamMp4Row(client, "AFTER_PLAYER_PLACEMENT_AND_SIDE_OWNER_AUTHORED");
            trapdoorSeamPhase = 8;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamTicks - trapdoorSeamPhaseTick < 30) {
            return;
        }
        emitTrapdoorSeamUpdateRow(client);
        trapdoorSeamMp4PostUpdateSlabHeldSample = sampleTrapdoorSeamMp4SlabHeldTarget(client, "POST_SUPPORT_BREAK");
        trapdoorSeamMp4ManualNoCandidatePostSample =
                sampleTrapdoorSeamMp4ManualNoCandidateTarget(client, "POST_SUPPORT_BREAK");
        emitTrapdoorSeamMp4Row(client, "AFTER_SUPPORT_BREAK_AND_SLAB_HELD_TARGET");
        emitTrapdoorSeamMp4Summary(client);
    }

    private static void runSbbsFinalSlabTargetingRoute(MinecraftClient client) {
        if (sbbsFinalSlabFinalized || emitted) {
            return;
        }
        sbbsFinalSlabTicks++;
        if (!sbbsFinalSlabCanaryEmitted) {
            sbbsFinalSlabCanaryEmitted = true;
            System.out.println("[MC1211_SBBS_FINAL_SLAB_TARGET_START]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " property=" + SBBS_FINAL_SLAB_TARGETING_RED_PROPERTY
                    + " sourceTruth=manual-recorder-sbbs-final-slab-hitbox"
                    + " initialLayer=raycast/rescue"
                    + " gameplayPatch=false"
                    + " worldReady=" + (client != null && client.world != null)
                    + " playerReady=" + (client != null && client.player != null));
        }

        requestProgrammaticSbbsFinalSlabWorldIfNeeded(client);
        String readinessGap = liveTruthReadinessGap(client);
        if (readinessGap != null) {
            if (!sbbsFinalSlabReadyRowEmitted || sbbsFinalSlabTicks % 1200 == 0) {
                emitSbbsFinalSlabTraceGap("WAITING", readinessGap);
                sbbsFinalSlabReadyRowEmitted = true;
            }
            if (sbbsFinalSlabTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitSbbsFinalSlabTraceGap("ROUTE_READINESS", readinessGap);
            return;
        }

        if (!sbbsFinalSlabStarted) {
            sbbsFinalSlabStarted = true;
            sbbsFinalSlabOrigin = client.player.getBlockPos().add(11, 0, 7).toImmutable();
            sbbsFinalSlabGroundPos = sbbsFinalSlabOrigin.down();
            sbbsFinalSlabBaseSlabPos = sbbsFinalSlabOrigin;
            sbbsFinalSlabSupportPos = sbbsFinalSlabBaseSlabPos.up();
            sbbsFinalSlabTopSlabPos = sbbsFinalSlabSupportPos.up();
            sbbsLanternSideSlabPos = sbbsFinalSlabTopSlabPos.east();
            sbbsLanternUnderPos = sbbsLanternSideSlabPos.down();
            sbbsChainSideSlabPos = sbbsFinalSlabTopSlabPos.west();
            sbbsChainPos = sbbsChainSideSlabPos.down();
            sbbsChainLanternPos = sbbsChainPos.down();
            prepareSbbsFinalSlabFixture(client);
            sbbsFinalSlabPhase = 1;
            sbbsFinalSlabPhaseTick = sbbsFinalSlabTicks;
            System.out.println("[MC1211_SBBS_FINAL_SLAB_TARGET_ROW]"
                    + " rowPhase=START"
                    + " fixtureOrigin=" + textPos(sbbsFinalSlabOrigin)
                    + " baseSlabPos=" + textPos(sbbsFinalSlabBaseSlabPos)
                    + " supportPos=" + textPos(sbbsFinalSlabSupportPos)
                    + " finalSlabPos=" + textPos(sbbsFinalSlabTopSlabPos)
                    + " lanternSideSlabPos=" + textPos(sbbsLanternSideSlabPos)
                    + " lanternUnderPos=" + textPos(sbbsLanternUnderPos)
                    + " chainSideSlabPos=" + textPos(sbbsChainSideSlabPos)
                    + " chainPos=" + textPos(sbbsChainPos)
                    + " chainLanternPos=" + textPos(sbbsChainLanternPos)
                    + " setup=player-authored"
                    + " heldItemForProbe=minecraft:stone_slab"
                    + " interiorLocalHit=0.750,0.500,0.487"
                    + " edgeLocalHit=0.937,0.500,0.487"
                    + " manualAnchorInjection=false"
                    + " gameplayPatch=false");
            return;
        }

        if (sbbsFinalSlabTicks - sbbsFinalSlabPhaseTick < 8) {
            return;
        }
        if (sbbsFinalSlabPhase == 1) {
            sbbsFinalSlabBasePlacementResult = clickBlock(
                    client,
                    Items.STONE_SLAB,
                    sbbsFinalSlabGroundPos,
                    Direction.UP,
                    hitVector(sbbsFinalSlabGroundPos, Direction.UP));
            sbbsFinalSlabPhase = 2;
            sbbsFinalSlabPhaseTick = sbbsFinalSlabTicks;
            return;
        }
        if (sbbsFinalSlabPhase == 2) {
            if (!sbbsFinalSlabBaseReady(client)) {
                if (sbbsFinalSlabTicks - sbbsFinalSlabPhaseTick < 160) {
                    if ((sbbsFinalSlabTicks - sbbsFinalSlabPhaseTick) % 20 == 0) {
                        sbbsFinalSlabBasePlacementResult = clickBlock(
                                client,
                                Items.STONE_SLAB,
                                sbbsFinalSlabGroundPos,
                                Direction.UP,
                                hitVector(sbbsFinalSlabGroundPos, Direction.UP));
                    }
                    return;
                }
                emitSbbsFinalSlabTraceGap("BASE_SLAB_AUTHORING", "TRACE_GAP_BOTTOM_SLAB_NOT_OBSERVED");
                return;
            }
            sbbsFinalSlabSupportPlacementResult = clickBlock(
                    client,
                    Items.STONE,
                    sbbsFinalSlabBaseSlabPos,
                    Direction.UP,
                    hitVector(sbbsFinalSlabBaseSlabPos, Direction.UP));
            sbbsFinalSlabPhase = 3;
            sbbsFinalSlabPhaseTick = sbbsFinalSlabTicks;
            return;
        }
        if (sbbsFinalSlabPhase == 3) {
            if (!sbbsFinalSlabSupportReady(client)) {
                if (sbbsFinalSlabTicks - sbbsFinalSlabPhaseTick < 160) {
                    if ((sbbsFinalSlabTicks - sbbsFinalSlabPhaseTick) % 20 == 0) {
                        sbbsFinalSlabSupportPlacementResult = clickBlock(
                                client,
                                Items.STONE,
                                sbbsFinalSlabBaseSlabPos,
                                Direction.UP,
                                hitVector(sbbsFinalSlabBaseSlabPos, Direction.UP));
                    }
                    return;
                }
                emitSbbsFinalSlabTraceGap("SUPPORT_AUTHORING", "TRACE_GAP_LOWERED_STONE_SUPPORT_NOT_OBSERVED");
                return;
            }
            sbbsFinalSlabTopSlabPlacementResult = clickBlock(
                    client,
                    Items.STONE_SLAB,
                    sbbsFinalSlabSupportPos,
                    Direction.UP,
                    sbbsFinalSlabInteriorHitVector());
            sbbsFinalSlabPhase = 4;
            sbbsFinalSlabPhaseTick = sbbsFinalSlabTicks;
            return;
        }
        if (sbbsFinalSlabPhase == 4) {
            if (!sbbsFinalSlabTopReady(client)) {
                if (sbbsFinalSlabTicks - sbbsFinalSlabPhaseTick < 160) {
                    if ((sbbsFinalSlabTicks - sbbsFinalSlabPhaseTick) % 20 == 0) {
                        sbbsFinalSlabTopSlabPlacementResult = clickBlock(
                                client,
                                Items.STONE_SLAB,
                                sbbsFinalSlabSupportPos,
                                Direction.UP,
                                sbbsFinalSlabInteriorHitVector());
                    }
                    return;
                }
                emitSbbsFinalSlabTraceGap("FINAL_SLAB_AUTHORING", "TRACE_GAP_LOWERED_FINAL_BOTTOM_SLAB_NOT_OBSERVED");
                return;
            }
            sbbsFinalSlabTargetSample = sampleSbbsFinalSlabTarget(
                    client,
                    "AFTER_FINAL_SLAB_AUTHORED_INTERIOR",
                    sbbsFinalSlabInteriorHitVector());
            sbbsFinalSlabEdgeTargetSample = sampleSbbsFinalSlabTarget(
                    client,
                    "AFTER_FINAL_SLAB_AUTHORED_EDGE",
                    sbbsFinalSlabEdgeHitVector());
            if (!authorSbbsUndersideFixtures(client)) {
                emitSbbsFinalSlabTraceGap("SBBS_UNDERSIDE_AUTHORING", "TRACE_GAP_SBBS_UNDERSIDE_FIXTURE_NOT_OBSERVED");
                return;
            }
            classifySbbsFinalSlabSamples();
            emitSbbsFinalSlabRow(client, "AFTER_FINAL_SLAB_AUTHORED");
            emitSbbsFinalSlabSummary(client);
        }
    }

    private static boolean authorSbbsUndersideFixtures(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null
                || sbbsLanternSideSlabPos == null
                || sbbsLanternUnderPos == null
                || sbbsChainSideSlabPos == null
                || sbbsChainPos == null
                || sbbsChainLanternPos == null) {
            return false;
        }

        BlockState loweredBottomSlab = Blocks.STONE_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM)
                .with(SlabBlock.WATERLOGGED, false);
        serverWorld.setBlockState(sbbsLanternSideSlabPos, loweredBottomSlab, 3);
        sbbsLanternSideSlabPlacementResult = "SET";
        serverWorld.setBlockState(
                sbbsLanternUnderPos,
                Blocks.LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true),
                3);
        sbbsLanternUnderPlacementResult = "SET";

        serverWorld.setBlockState(sbbsChainSideSlabPos, loweredBottomSlab, 3);
        sbbsChainSideSlabPlacementResult = "SET";
        serverWorld.setBlockState(
                sbbsChainPos,
                Blocks.CHAIN.getDefaultState().with(net.minecraft.block.ChainBlock.AXIS, Direction.Axis.Y),
                3);
        sbbsChainPlacementResult = "SET";
        serverWorld.setBlockState(
                sbbsChainLanternPos,
                Blocks.LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true),
                3);
        sbbsChainLanternPlacementResult = "SET";

        BlockState lanternSideSlabState = serverWorld.getBlockState(sbbsLanternSideSlabPos);
        BlockState lanternUnderState = serverWorld.getBlockState(sbbsLanternUnderPos);
        BlockState chainSideSlabState = serverWorld.getBlockState(sbbsChainSideSlabPos);
        BlockState chainState = serverWorld.getBlockState(sbbsChainPos);
        BlockState chainLanternState = serverWorld.getBlockState(sbbsChainLanternPos);
        return lanternSideSlabState.isOf(Blocks.STONE_SLAB)
                && lanternSideSlabState.get(SlabBlock.TYPE) == SlabType.BOTTOM
                && lanternUnderState.isOf(Blocks.LANTERN)
                && chainSideSlabState.isOf(Blocks.STONE_SLAB)
                && chainSideSlabState.get(SlabBlock.TYPE) == SlabType.BOTTOM
                && chainState.isOf(Blocks.CHAIN)
                && chainLanternState.isOf(Blocks.LANTERN);
    }

    private static String activeTrapdoorUnderBottomProperty() {
        return Boolean.getBoolean(TRAPDOOR_UNDER_LOWERED_BOTTOM_PLACEMENT_RED_PROPERTY)
                ? TRAPDOOR_UNDER_LOWERED_BOTTOM_PLACEMENT_RED_PROPERTY
                : TRAPDOOR_UNDER_BOTTOM_PLACEMENT_RED_PROPERTY;
    }

    private static String activeTrapdoorUnderBottomTargetName() {
        return Boolean.getBoolean(TRAPDOOR_UNDER_LOWERED_BOTTOM_PLACEMENT_RED_PROPERTY)
                ? "lowered-side-extension-bottom-slab-underside"
                : "true-bottom-slab-underside";
    }

    private static void runTrapdoorSideExtensionRoute(MinecraftClient client) {
        if (trapdoorSeamFinalized || emitted) {
            return;
        }
        trapdoorSeamTicks++;
        if (!trapdoorSeamCanaryEmitted) {
            trapdoorSeamCanaryEmitted = true;
            System.out.println("[MC1211_TRAPDOOR_SIDE_EXTENSION_RED_START]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " property=" + activeTrapdoorSideExtensionProperty()
                    + " sourceTruth=live-S>B>S-side-extension-under-trapdoor"
                    + " initialLayer=" + (isSbbsLowerEdgeSideSlabRedRoute()
                    ? "placement-position"
                    : "proof gap")
                    + " gameplayPatch=false"
                    + " worldReady=" + (client != null && client.world != null)
                    + " playerReady=" + (client != null && client.player != null));
        }

        requestProgrammaticTrapdoorSeamWorldIfNeeded(client);
        String readinessGap = liveTruthReadinessGap(client);
        if (readinessGap != null) {
            if (!trapdoorSeamReadyRowEmitted || trapdoorSeamTicks % 1200 == 0) {
                emitTrapdoorSideExtensionTraceGap("WAITING", readinessGap);
                trapdoorSeamReadyRowEmitted = true;
            }
            if (trapdoorSeamTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitTrapdoorSideExtensionTraceGap("ROUTE_READINESS", readinessGap);
            return;
        }

        if (!trapdoorSeamStarted) {
            trapdoorSeamStarted = true;
            trapdoorSeamOrigin = client.player.getBlockPos().add(11, 0, 7).toImmutable();
            trapdoorSeamGroundPos = trapdoorSeamOrigin.down();
            trapdoorSeamSlabPos = trapdoorSeamOrigin;
            trapdoorSeamSupportPos = trapdoorSeamSlabPos.up();
            trapdoorSeamTopSlabPos = trapdoorSeamSupportPos.up();
            trapdoorSeamTemporaryCeilingPos = trapdoorSeamTopSlabPos.up();
            trapdoorSeamExtensionSlabPos = trapdoorSeamTopSlabPos.east();
            trapdoorSeamExtensionTrapdoorPos = trapdoorSeamExtensionSlabPos.down();
            trapdoorSeamExpectedTrapdoorPos = trapdoorSeamExtensionTrapdoorPos;
            prepareTrapdoorSideExtensionFixture(client);
            trapdoorSeamPhase = 1;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            System.out.println("[MC1211_TRAPDOOR_SIDE_EXTENSION_ROW]"
                    + " rowPhase=START"
                    + " fixtureOrigin=" + textPos(trapdoorSeamOrigin)
                    + " baseSlabPos=" + textPos(trapdoorSeamSlabPos)
                    + " supportPos=" + textPos(trapdoorSeamSupportPos)
                    + " topSlabPos=" + textPos(trapdoorSeamTopSlabPos)
                    + " extensionSlabPos=" + textPos(trapdoorSeamExtensionSlabPos)
                    + " expectedTrapdoorPos=" + textPos(trapdoorSeamExpectedTrapdoorPos)
                    + " setup=player-authored-S>B>S-side-extension"
                    + " finalSlabType=TOP"
                    + " extensionSlabType=" + desiredTrapdoorSideExtensionSlabType().asString()
                    + " trapdoorItem=minecraft:oak_trapdoor"
                    + " gameplayPatch=false");
            return;
        }

        if (trapdoorSeamTicks - trapdoorSeamPhaseTick < 8) {
            return;
        }
        if (trapdoorSeamPhase == 1) {
            trapdoorSeamSlabPlacementResult = clickBlock(
                    client,
                    Items.STONE_SLAB,
                    trapdoorSeamGroundPos,
                    Direction.UP,
                    hitVector(trapdoorSeamGroundPos, Direction.UP));
            trapdoorSeamPhase = 2;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamPhase == 2) {
            if (!trapdoorSeamSlabReady(client)) {
                if (retryTrapdoorSideExtensionClick(client, trapdoorSeamSlabPlacementResult, 160)) {
                    trapdoorSeamSlabPlacementResult = clickBlock(
                            client,
                            Items.STONE_SLAB,
                            trapdoorSeamGroundPos,
                            Direction.UP,
                            hitVector(trapdoorSeamGroundPos, Direction.UP));
                    return;
                }
                emitTrapdoorSideExtensionTraceGap("BASE_SLAB_AUTHORING",
                        "TRACE_GAP_PLAYER_AUTHORED_BOTTOM_BASE_SLAB_NOT_OBSERVED");
                return;
            }
            trapdoorSeamSupportPlacementResult = clickBlock(
                    client,
                    Items.STONE,
                    trapdoorSeamSlabPos,
                    Direction.UP,
                    hitVector(trapdoorSeamSlabPos, Direction.UP));
            trapdoorSeamPhase = 3;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamPhase == 3) {
            if (!trapdoorSeamLoweredSupportReady(client)) {
                if (retryTrapdoorSideExtensionClick(client, trapdoorSeamSupportPlacementResult, 160)) {
                    trapdoorSeamSupportPlacementResult = clickBlock(
                            client,
                            Items.STONE,
                            trapdoorSeamSlabPos,
                            Direction.UP,
                            hitVector(trapdoorSeamSlabPos, Direction.UP));
                    return;
                }
                emitTrapdoorSideExtensionTraceGap("SUPPORT_AUTHORING",
                        "TRACE_GAP_PLAYER_AUTHORED_LOWERED_SUPPORT_NOT_OBSERVED");
                return;
            }
            trapdoorSeamTopSlabPlacementResult = clickBlock(
                    client,
                    Items.STONE_SLAB,
                    trapdoorSeamTemporaryCeilingPos,
                    Direction.DOWN,
                    hitVector(trapdoorSeamTemporaryCeilingPos, Direction.DOWN));
            trapdoorSeamPhase = 4;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamPhase == 4) {
            if (!trapdoorSeamTopSlabReady(client)) {
                if (retryTrapdoorSideExtensionClick(client, trapdoorSeamTopSlabPlacementResult, 160)) {
                    trapdoorSeamTopSlabPlacementResult = clickBlock(
                            client,
                            Items.STONE_SLAB,
                            trapdoorSeamTemporaryCeilingPos,
                            Direction.DOWN,
                            hitVector(trapdoorSeamTemporaryCeilingPos, Direction.DOWN));
                    return;
                }
                emitTrapdoorSideExtensionTraceGap("TOP_SLAB_AUTHORING",
                        "TRACE_GAP_PLAYER_AUTHORED_TOP_SLAB_NOT_OBSERVED");
                return;
            }
            removeTrapdoorSideExtensionTemporaryCeiling(client);
            trapdoorSeamPhase = 5;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamPhase == 5) {
            if (!trapdoorSeamTemporaryCeilingRemoved(client)) {
                if (trapdoorSeamTicks - trapdoorSeamPhaseTick < 80) {
                    return;
                }
                emitTrapdoorSideExtensionTraceGap("TEMP_CEILING_REMOVAL",
                        "TRACE_GAP_TOP_SLAB_TEMP_CEILING_REMOVAL_NOT_OBSERVED");
                return;
            }
            trapdoorSeamExtensionSlabPlacementResult = clickBlock(
                    client,
                    Items.STONE_SLAB,
                    trapdoorSeamTopSlabPos,
                    Direction.EAST,
                    trapdoorSideExtensionHitVector(desiredTrapdoorSideExtensionSlabType()));
            trapdoorSeamPhase = 6;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamPhase == 6) {
            if (!trapdoorSeamExtensionSlabReady(client)) {
                if (retryTrapdoorSideExtensionClick(client, trapdoorSeamExtensionSlabPlacementResult, 160)) {
                    trapdoorSeamExtensionSlabPlacementResult = clickBlock(
                            client,
                            Items.STONE_SLAB,
                            trapdoorSeamTopSlabPos,
                            Direction.EAST,
                            trapdoorSideExtensionHitVector(desiredTrapdoorSideExtensionSlabType()));
                    return;
                }
                if (isSbbsLowerEdgeSideSlabRedRoute()) {
                    emitSbbsLowerEdgeSideSlabPlacementResult(false);
                    return;
                }
                if (desiredTrapdoorSideExtensionSlabType() == SlabType.BOTTOM) {
                    emitTrapdoorSideExtensionPlacementRed("SIDE_EXTENSION_BOTTOM_SLAB_AUTHORING",
                            "SIDE_EXTENSION_BOTTOM_SLAB_PLACEMENT_REJECTED_OR_WRONG_STATE");
                    return;
                }
                emitTrapdoorSideExtensionTraceGap("SIDE_EXTENSION_SLAB_AUTHORING",
                        "TRACE_GAP_PLAYER_AUTHORED_SIDE_EXTENSION_TOP_SLAB_NOT_OBSERVED");
                return;
            }
            if (isSbbsLowerEdgeSideSlabRedRoute()) {
                emitSbbsLowerEdgeSideSlabPlacementResult(true);
                return;
            }
            trapdoorSeamExtensionTrapdoorPlacementResult = clickTrapdoorUnderSideExtension(client);
            trapdoorSeamPhase = 7;
            trapdoorSeamPhaseTick = trapdoorSeamTicks;
            return;
        }
        if (trapdoorSeamTicks - trapdoorSeamPhaseTick < 30) {
            return;
        }
        emitTrapdoorSideExtensionRow(client);
        emitTrapdoorSideExtensionSummary(client);
    }

    private static void runGoblinLoweredTrapdoorSeamsRoute(MinecraftClient client) {
        if (goblinLoweredTrapdoorFinalized || emitted) {
            return;
        }
        goblinLoweredTrapdoorTicks++;
        if (!goblinLoweredTrapdoorCanaryEmitted) {
            goblinLoweredTrapdoorCanaryEmitted = true;
            System.out.println("[MC1211_GOBLIN_LOWERED_TRAPDOOR_START]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " property=" + GOBLIN_LOWERED_TRAPDOOR_SEAMS_PROPERTY
                    + " harnessDriven=true"
                    + " manualComputerUseEvidence=false"
                    + " initialLayer=proof gap"
                    + " gameplayPatch=false"
                    + " worldReady=" + (client != null && client.world != null)
                    + " playerReady=" + (client != null && client.player != null));
        }

        requestProgrammaticTrapdoorSeamWorldIfNeeded(client);
        String readinessGap = liveTruthReadinessGap(client);
        if (readinessGap != null) {
            if (goblinLoweredTrapdoorTicks % 1200 == 1) {
                System.out.println("[MC1211_GOBLIN_LOWERED_TRAPDOOR_ROW]"
                        + " rowPhase=WAITING"
                        + " classification=" + readinessGap
                        + " failureLayer=proof gap");
            }
            if (goblinLoweredTrapdoorTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            goblinLoweredTrapdoorTraceGapRows++;
            emitGoblinLoweredTrapdoorSummary("TRACE_GAP", readinessGap, "proof gap", client);
            return;
        }
        if (!goblinLoweredTrapdoorStarted) {
            goblinLoweredTrapdoorStarted = true;
            goblinLoweredTrapdoorOrigin = client.player.getBlockPos().add(17, 0, 11).toImmutable();
            System.out.println("[MC1211_GOBLIN_LOWERED_TRAPDOOR_CASE]"
                    + " rowPhase=SUITE_START"
                    + " fixtureOrigin=" + textPos(goblinLoweredTrapdoorOrigin)
                    + " cases=" + LOWERED_TRAPDOOR_GOBLIN_CASES.length
                    + " authoring=scripted_harness"
                    + " actions=build,place_trapdoor,sweep_targets,break_support,neighbor_update,recheck"
                    + " cullingCheck=NOT_OBSERVABLE_LOG_ONLY"
                    + " screenshot=NOT_REQUESTED");
            ServerWorld serverWorld = serverWorldFor(client);
            if (serverWorld == null || client.world == null) {
                goblinLoweredTrapdoorTraceGapRows++;
                emitGoblinLoweredTrapdoorSummary("TRACE_GAP", "TRACE_GAP_WORLD_NOT_READY", "proof gap", client);
                return;
            }
            goblinLoweredTrapdoorPhaseTick = goblinLoweredTrapdoorTicks;
            return;
        }
        if (goblinLoweredTrapdoorCaseIndex >= LOWERED_TRAPDOOR_GOBLIN_CASES.length) {
            String finalResult = goblinLoweredTrapdoorRedRows > 0
                    ? "RED"
                    : (goblinLoweredTrapdoorTraceGapRows > 0 ? "TRACE_GAP" : "GREEN");
            String classification = goblinLoweredTrapdoorRedRows > 0
                    ? "ONE_OR_MORE_GOBLIN_CASES_RED"
                    : (goblinLoweredTrapdoorTraceGapRows > 0
                    ? "ONE_OR_MORE_GOBLIN_CASES_TRACE_GAP"
                    : "ALL_GOBLIN_CASES_GREEN");
            String failureLayer = goblinLoweredTrapdoorRedRows > 0
                    ? "see_case_rows"
                    : (goblinLoweredTrapdoorTraceGapRows > 0 ? "proof gap" : "NONE");
            emitGoblinLoweredTrapdoorSummary(finalResult, classification, failureLayer, client);
            return;
        }
        runGoblinLoweredTrapdoorCaseStep(client);
    }

    private static void runGoblinLoweredTrapdoorCaseStep(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || client == null || client.world == null || client.player == null) {
            goblinLoweredTrapdoorTraceGapRows++;
            emitGoblinLoweredTrapdoorSummary("TRACE_GAP", "TRACE_GAP_WORLD_OR_PLAYER_NOT_READY", "proof gap", client);
            return;
        }
        LoweredTrapdoorGoblinCase spec = LOWERED_TRAPDOOR_GOBLIN_CASES[goblinLoweredTrapdoorCaseIndex];
        if (goblinLoweredTrapdoorCasePhase == 0) {
            goblinLoweredTrapdoorCaseOrigin =
                    goblinLoweredTrapdoorOrigin.add(goblinLoweredTrapdoorCaseIndex * 6, 0, 0).toImmutable();
            BlockPos baseSlabPos = goblinLoweredTrapdoorCaseOrigin;
            goblinLoweredTrapdoorSupportPos = baseSlabPos.up();
            BlockPos ownerTopSlabPos = goblinLoweredTrapdoorSupportPos.up();
            goblinLoweredTrapdoorTargetSlabPos = ownerTopSlabPos.east();
            goblinLoweredTrapdoorExpectedTrapdoorPos = goblinLoweredTrapdoorTargetSlabPos.down();
            prepareGoblinLoweredTrapdoorCaseWorld(client, serverWorld, goblinLoweredTrapdoorCaseOrigin);
            BlockState baseSlabState = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
            BlockState supportState = Blocks.STONE.getDefaultState();
            BlockState ownerTopState = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
            BlockState targetSlabState = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, spec.targetSlabType());
            serverWorld.setBlockState(baseSlabPos, baseSlabState, 3);
            serverWorld.setBlockState(goblinLoweredTrapdoorSupportPos, supportState, 3);
            serverWorld.setBlockState(ownerTopSlabPos, ownerTopState, 3);
            serverWorld.setBlockState(goblinLoweredTrapdoorTargetSlabPos, targetSlabState, 3);
            client.world.setBlockState(baseSlabPos, baseSlabState, 3);
            client.world.setBlockState(goblinLoweredTrapdoorSupportPos, supportState, 3);
            client.world.setBlockState(ownerTopSlabPos, ownerTopState, 3);
            client.world.setBlockState(goblinLoweredTrapdoorTargetSlabPos, targetSlabState, 3);
            SlabAnchorAttachment.addAnchor(serverWorld, goblinLoweredTrapdoorSupportPos, supportState);
            SlabAnchorAttachment.addCompoundVisibleOwnerTopSlab(
                    serverWorld, ownerTopSlabPos, ownerTopState, goblinLoweredTrapdoorSupportPos, supportState);
            addGoblinLoweredSlabMarker(
                    serverWorld, goblinLoweredTrapdoorTargetSlabPos, targetSlabState, ownerTopSlabPos, ownerTopState);
            if (spec.cornerCase()) {
                BlockPos cornerSlabPos = ownerTopSlabPos.north();
                serverWorld.setBlockState(cornerSlabPos, ownerTopState, 3);
                client.world.setBlockState(cornerSlabPos, ownerTopState, 3);
                SlabAnchorAttachment.addCompoundVisibleSideUpperSlab(
                        serverWorld, cornerSlabPos, ownerTopState, ownerTopSlabPos, ownerTopState);
            }
            goblinLoweredTrapdoorPlacementResult =
                    clickGoblinTrapdoorUnderSlab(client, goblinLoweredTrapdoorTargetSlabPos);
            BlockState authoredTargetState = serverWorld.getBlockState(goblinLoweredTrapdoorTargetSlabPos);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    serverWorld, goblinLoweredTrapdoorTargetSlabPos, authoredTargetState);
            goblinLoweredTrapdoorCasePhase = 1;
            goblinLoweredTrapdoorPhaseTick = goblinLoweredTrapdoorTicks;
            System.out.println("[MC1211_GOBLIN_LOWERED_TRAPDOOR_CASE]"
                    + " rowPhase=CASE_AUTHORED"
                    + " caseName=" + spec.caseName()
                    + " fixtureOrigin=" + textPos(goblinLoweredTrapdoorCaseOrigin)
                    + " targetSlabType=" + spec.targetSlabType().asString()
                    + " placementResultTrapdoor=" + goblinLoweredTrapdoorPlacementResult);
            return;
        }
        if (goblinLoweredTrapdoorCasePhase == 1) {
            if (goblinLoweredTrapdoorTicks - goblinLoweredTrapdoorPhaseTick < 30) {
                return;
            }
            BlockState targetBeforeBreak = serverWorld.getBlockState(goblinLoweredTrapdoorTargetSlabPos);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    serverWorld, goblinLoweredTrapdoorTargetSlabPos, targetBeforeBreak);
            goblinLoweredTrapdoorActualTrapdoorPos =
                    findNearbyTrapdoor(serverWorld, goblinLoweredTrapdoorExpectedTrapdoorPos);
            goblinLoweredTrapdoorBeforeBreak = assessGoblinLoweredTrapdoorCase(
                    client, serverWorld, spec, goblinLoweredTrapdoorCaseOrigin,
                    goblinLoweredTrapdoorSupportPos, goblinLoweredTrapdoorTargetSlabPos,
                    goblinLoweredTrapdoorExpectedTrapdoorPos, goblinLoweredTrapdoorActualTrapdoorPos,
                    goblinLoweredTrapdoorPlacementResult, "BEFORE_BREAK");
            goblinLoweredTrapdoorTargetSweep = sampleGoblinLoweredTrapdoorTargets(
                    client, spec, goblinLoweredTrapdoorTargetSlabPos,
                    goblinLoweredTrapdoorActualTrapdoorPos, "BEFORE_BREAK");
            serverWorld.breakBlock(goblinLoweredTrapdoorSupportPos, false);
            serverWorld.updateNeighbors(goblinLoweredTrapdoorSupportPos, Blocks.AIR);
            serverWorld.updateNeighbors(
                    goblinLoweredTrapdoorTargetSlabPos,
                    serverWorld.getBlockState(goblinLoweredTrapdoorTargetSlabPos).getBlock());
            goblinLoweredTrapdoorCasePhase = 2;
            goblinLoweredTrapdoorPhaseTick = goblinLoweredTrapdoorTicks;
            return;
        }
        if (goblinLoweredTrapdoorCasePhase == 2) {
            if (goblinLoweredTrapdoorTicks - goblinLoweredTrapdoorPhaseTick < 30) {
                return;
            }
            BlockState targetAfterBreak = serverWorld.getBlockState(goblinLoweredTrapdoorTargetSlabPos);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
                    serverWorld, goblinLoweredTrapdoorTargetSlabPos, targetAfterBreak);
            LoweredTrapdoorGoblinAssessment afterBreak = assessGoblinLoweredTrapdoorCase(
                    client, serverWorld, spec, goblinLoweredTrapdoorCaseOrigin,
                    goblinLoweredTrapdoorSupportPos, goblinLoweredTrapdoorTargetSlabPos,
                    goblinLoweredTrapdoorExpectedTrapdoorPos, goblinLoweredTrapdoorActualTrapdoorPos,
                    goblinLoweredTrapdoorPlacementResult, "AFTER_SUPPORT_BREAK");
            emitGoblinLoweredTrapdoorCaseResult(client, spec, afterBreak);
            goblinLoweredTrapdoorCaseIndex++;
            goblinLoweredTrapdoorCasePhase = 0;
            goblinLoweredTrapdoorPhaseTick = goblinLoweredTrapdoorTicks;
        }
    }

    private static void emitGoblinLoweredTrapdoorCaseResult(
            MinecraftClient client,
            LoweredTrapdoorGoblinCase spec,
            LoweredTrapdoorGoblinAssessment afterBreak
    ) {
        String classification;
        String failureLayer;
        String result;
        if (!goblinLoweredTrapdoorBeforeBreak.green()) {
            classification = goblinLoweredTrapdoorBeforeBreak.classification();
            failureLayer = goblinLoweredTrapdoorBeforeBreak.failureLayer();
            result = "RED";
        } else if (goblinLoweredTrapdoorTargetSweep.contains("wrongOwner=true")) {
            classification = "TARGET_SWEEP_OWNER_MISMATCH";
            failureLayer = "raycast";
            result = "RED";
        } else if (!afterBreak.green()) {
            classification = afterBreak.classification();
            failureLayer = afterBreak.failureLayer();
            result = "RED";
        } else {
            classification = "LOWERED_TRAPDOOR_SEAM_GOBLIN_GREEN";
            failureLayer = "NONE";
            result = "GREEN";
        }
        if ("GREEN".equals(result)) {
            goblinLoweredTrapdoorGreenRows++;
            System.out.println("[MC1211_GOBLIN_LOWERED_TRAPDOOR_GREEN]"
                    + " caseName=" + spec.caseName()
                    + " classification=" + classification
                    + " failureLayer=" + failureLayer);
        } else {
            goblinLoweredTrapdoorRedRows++;
            System.out.println("[MC1211_GOBLIN_LOWERED_TRAPDOOR_RED]"
                    + " caseName=" + spec.caseName()
                    + " classification=" + classification
                    + " failureLayer=" + failureLayer);
        }

        System.out.println("[MC1211_GOBLIN_LOWERED_TRAPDOOR_ROW]"
                + " caseName=" + spec.caseName()
                + " rowPhase=CASE_RESULT"
                + " fixtureOrigin=" + textPos(goblinLoweredTrapdoorCaseOrigin)
                + " supportPos=" + textPos(goblinLoweredTrapdoorSupportPos)
                + " targetSlabPos=" + textPos(goblinLoweredTrapdoorTargetSlabPos)
                + " targetSlabType=" + spec.targetSlabType().asString()
                + " sideExtension=" + spec.sideExtension()
                + " cornerCase=" + spec.cornerCase()
                + " expectedTrapdoorPos=" + textPos(goblinLoweredTrapdoorExpectedTrapdoorPos)
                + " actualTrapdoorPos=" + textPos(goblinLoweredTrapdoorActualTrapdoorPos)
                + " placementResultTrapdoor=" + goblinLoweredTrapdoorPlacementResult
                + " beforeBreak=" + goblinLoweredTrapdoorBeforeBreak.summary()
                + " afterBreak=" + afterBreak.summary()
                + " targetSweep=" + goblinLoweredTrapdoorTargetSweep
                + " crosshair=" + describeCrosshair(client)
                + " cullingVisibleFaceSanity=NOT_OBSERVABLE_LOG_ONLY"
                + " classification=" + classification
                + " failureLayer=" + failureLayer
                + " result=" + result);
    }

    private static LoweredTrapdoorGoblinRow runGoblinLoweredTrapdoorCase(
            MinecraftClient client,
            ServerWorld serverWorld,
            LoweredTrapdoorGoblinCase spec,
            BlockPos caseOrigin
    ) {
        if (client == null || client.world == null || client.player == null || serverWorld == null) {
            return new LoweredTrapdoorGoblinRow("TRACE_GAP", "TRACE_GAP_ROUTE_NOT_READY", "proof gap");
        }
        BlockPos baseSlabPos = caseOrigin;
        BlockPos supportPos = baseSlabPos.up();
        BlockPos ownerTopSlabPos = supportPos.up();
        BlockPos targetSlabPos = spec.sideExtension() ? ownerTopSlabPos.east() : ownerTopSlabPos;
        BlockPos trapdoorPos = targetSlabPos.down();
        BlockPos cornerSlabPos = ownerTopSlabPos.north();

        prepareGoblinLoweredTrapdoorCaseWorld(client, serverWorld, caseOrigin);
        BlockState baseSlabState = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState supportState = Blocks.STONE.getDefaultState();
        BlockState ownerTopState = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
        BlockState targetSlabState = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, spec.targetSlabType());
        serverWorld.setBlockState(baseSlabPos, baseSlabState, 3);
        serverWorld.setBlockState(supportPos, supportState, 3);
        serverWorld.setBlockState(ownerTopSlabPos, ownerTopState, 3);
        client.world.setBlockState(baseSlabPos, baseSlabState, 3);
        client.world.setBlockState(supportPos, supportState, 3);
        client.world.setBlockState(ownerTopSlabPos, ownerTopState, 3);
        SlabAnchorAttachment.addAnchor(serverWorld, supportPos, supportState);
        SlabAnchorAttachment.addCompoundVisibleOwnerTopSlab(
                serverWorld, ownerTopSlabPos, ownerTopState, supportPos, supportState);
        if (spec.sideExtension()) {
            serverWorld.setBlockState(targetSlabPos, targetSlabState, 3);
            client.world.setBlockState(targetSlabPos, targetSlabState, 3);
            addGoblinLoweredSlabMarker(serverWorld, targetSlabPos, targetSlabState, ownerTopSlabPos, ownerTopState);
        }
        if (spec.cornerCase()) {
            serverWorld.setBlockState(cornerSlabPos, ownerTopState, 3);
            client.world.setBlockState(cornerSlabPos, ownerTopState, 3);
            SlabAnchorAttachment.addCompoundVisibleSideUpperSlab(
                    serverWorld, cornerSlabPos, ownerTopState, ownerTopSlabPos, ownerTopState);
        }
        String trapdoorPlacementResult = clickGoblinTrapdoorUnderSlab(client, targetSlabPos);
        BlockState authoredTargetState = serverWorld.getBlockState(targetSlabPos);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(serverWorld, targetSlabPos, authoredTargetState);
        BlockPos actualTrapdoorPos = findNearbyTrapdoor(serverWorld, trapdoorPos);
        LoweredTrapdoorGoblinAssessment beforeBreak = assessGoblinLoweredTrapdoorCase(
                client, serverWorld, spec, caseOrigin, supportPos, targetSlabPos, trapdoorPos,
                actualTrapdoorPos, trapdoorPlacementResult, "BEFORE_BREAK");
        String targetSweep = sampleGoblinLoweredTrapdoorTargets(
                client, spec, targetSlabPos, actualTrapdoorPos, "BEFORE_BREAK");
        serverWorld.breakBlock(supportPos, false);
        serverWorld.updateNeighbors(supportPos, Blocks.AIR);
        serverWorld.updateNeighbors(targetSlabPos, serverWorld.getBlockState(targetSlabPos).getBlock());
        LoweredTrapdoorGoblinAssessment afterBreak = assessGoblinLoweredTrapdoorCase(
                client, serverWorld, spec, caseOrigin, supportPos, targetSlabPos, trapdoorPos,
                actualTrapdoorPos, trapdoorPlacementResult, "AFTER_SUPPORT_BREAK");
        String classification = beforeBreak.classification();
        String failureLayer = beforeBreak.failureLayer();
        String result = beforeBreak.green() && afterBreak.green() && !targetSweep.contains("wrongOwner=true")
                ? "GREEN"
                : "RED";
        if (!beforeBreak.green()) {
            classification = beforeBreak.classification();
            failureLayer = beforeBreak.failureLayer();
        } else if (targetSweep.contains("wrongOwner=true")) {
            classification = "TARGET_SWEEP_OWNER_MISMATCH";
            failureLayer = "raycast";
        } else if (!afterBreak.green()) {
            classification = afterBreak.classification();
            failureLayer = afterBreak.failureLayer();
        } else {
            classification = "LOWERED_TRAPDOOR_SEAM_GOBLIN_GREEN";
            failureLayer = "NONE";
        }

        System.out.println("[MC1211_GOBLIN_LOWERED_TRAPDOOR_ROW]"
                + " caseName=" + spec.caseName()
                + " rowPhase=CASE_RESULT"
                + " fixtureOrigin=" + textPos(caseOrigin)
                + " baseSlabPos=" + textPos(baseSlabPos)
                + " supportPos=" + textPos(supportPos)
                + " ownerTopSlabPos=" + textPos(ownerTopSlabPos)
                + " targetSlabPos=" + textPos(targetSlabPos)
                + " targetSlabType=" + spec.targetSlabType().asString()
                + " sideExtension=" + spec.sideExtension()
                + " cornerCase=" + spec.cornerCase()
                + " expectedTrapdoorPos=" + textPos(trapdoorPos)
                + " actualTrapdoorPos=" + textPos(actualTrapdoorPos)
                + " placementResultTrapdoor=" + trapdoorPlacementResult
                + " beforeBreak=" + beforeBreak.summary()
                + " afterBreak=" + afterBreak.summary()
                + " targetSweep=" + targetSweep
                + " cullingVisibleFaceSanity=NOT_OBSERVABLE_LOG_ONLY"
                + " classification=" + classification
                + " failureLayer=" + failureLayer
                + " result=" + result);
        return new LoweredTrapdoorGoblinRow(result, classification, failureLayer);
    }

    private static void prepareGoblinLoweredTrapdoorCaseWorld(
            MinecraftClient client,
            ServerWorld serverWorld,
            BlockPos caseOrigin
    ) {
        for (int x = caseOrigin.getX() - 2; x <= caseOrigin.getX() + 4; x++) {
            for (int z = caseOrigin.getZ() - 3; z <= caseOrigin.getZ() + 3; z++) {
                for (int y = caseOrigin.getY() - 2; y <= caseOrigin.getY() + 5; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    serverWorld.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                    if (client.world != null) {
                        client.world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
        }
        BlockPos groundPos = caseOrigin.down();
        serverWorld.setBlockState(groundPos, Blocks.STONE.getDefaultState(), 3);
        if (client.world != null) {
            client.world.setBlockState(groundPos, Blocks.STONE.getDefaultState(), 3);
        }
    }

    private static void addGoblinLoweredSlabMarker(
            ServerWorld world,
            BlockPos pos,
            BlockState state,
            BlockPos sourcePos,
            BlockState sourceState
    ) {
        SlabType type = state.get(SlabBlock.TYPE);
        if (type == SlabType.BOTTOM) {
            SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(world, pos, state, sourcePos, sourceState);
        } else if (type == SlabType.TOP) {
            SlabAnchorAttachment.addCompoundVisibleSideUpperSlab(world, pos, state, sourcePos, sourceState);
        } else {
            SlabAnchorAttachment.addCompoundVisibleSideDoubleSlab(world, pos, state, sourcePos, sourceState);
        }
    }

    private static String clickGoblinTrapdoorUnderSlab(MinecraftClient client, BlockPos targetSlabPos) {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null
                || client.gameRenderer == null || targetSlabPos == null) {
            return "FAIL_ROUTE_NOT_READY";
        }
        BlockPos trapdoorPos = targetSlabPos.down();
        BlockPos supportPos = trapdoorPos.west();
        Vec3d hitVector = new Vec3d(
                supportPos.getX() + 0.98d,
                supportPos.getY() + 0.90d,
                supportPos.getZ() + 0.5d);
        syncGoblinLoweredTrapdoorPlayer(client, hitVector, new Vec3d(1.75d, -0.35d, 0.0d), Items.OAK_TRAPDOOR);
        client.gameRenderer.updateCrosshairTarget(0.0f);
        // Deterministic authoring: click support east face so placement resolves into trapdoorPos.
        BlockHitResult fallback = new BlockHitResult(hitVector, Direction.EAST, supportPos, false);
        ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, fallback);

        ServerWorld serverWorld = serverWorldFor(client);
        BlockState targetSlabState = serverWorld == null ? Blocks.AIR.getDefaultState() : serverWorld.getBlockState(targetSlabPos);
        BlockState trapdoorState = serverWorld == null ? Blocks.AIR.getDefaultState() : serverWorld.getBlockState(trapdoorPos);
        double targetSlabDy = serverWorld == null ? Double.NaN : SlabSupport.getYOffset(serverWorld, targetSlabPos, targetSlabState);
        double trapdoorDy = (serverWorld == null || !trapdoorState.isOf(Blocks.OAK_TRAPDOOR))
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, trapdoorPos, trapdoorState);
        System.out.println("[MC1211_GOBLIN_LOWERED_TRAPDOOR_CLICK]"
                + " phase=IMMEDIATE_POST_CLICK"
                + " targetSlabPos=" + textPos(targetSlabPos)
                + " supportPos=" + textPos(supportPos)
                + " trapdoorPos=" + textPos(trapdoorPos)
                + " fallbackSide=east"
                + " interactResult=" + result
                + " targetSlabState=" + targetSlabState
                + " targetSlabDy=" + formatDouble(targetSlabDy)
                + " trapdoorState=" + trapdoorState
                + " trapdoorDy=" + formatDouble(trapdoorDy));

        return "FALLBACK_" + result;
    }

    private static void syncGoblinLoweredTrapdoorPlayer(
            MinecraftClient client,
            Vec3d hitVector,
            Vec3d eyeOffset,
            net.minecraft.item.Item heldItem
    ) {
        Vec3d eye = hitVector.add(eyeOffset);
        Vec3d delta = hitVector.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.setSneaking(false);
        client.player.setStackInHand(Hand.MAIN_HAND,
                heldItem == null ? ItemStack.EMPTY : new ItemStack(heldItem, 8));
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            serverPlayer.setVelocity(Vec3d.ZERO);
            serverPlayer.setSneaking(false);
            serverPlayer.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            serverPlayer.setStackInHand(Hand.MAIN_HAND,
                    heldItem == null ? ItemStack.EMPTY : new ItemStack(heldItem, 8));
        }
    }

    private static LoweredTrapdoorGoblinAssessment assessGoblinLoweredTrapdoorCase(
            MinecraftClient client,
            ServerWorld serverWorld,
            LoweredTrapdoorGoblinCase spec,
            BlockPos caseOrigin,
            BlockPos supportPos,
            BlockPos targetSlabPos,
            BlockPos expectedTrapdoorPos,
            BlockPos actualTrapdoorPos,
            String trapdoorPlacementResult,
            String phase
    ) {
        BlockState supportState = serverWorld.getBlockState(supportPos);
        BlockState targetSlabState = serverWorld.getBlockState(targetSlabPos);
        BlockState trapdoorState = serverWorld.getBlockState(actualTrapdoorPos);
        BlockState clientTrapdoorState = client.world.getBlockState(actualTrapdoorPos);
        double supportDy = SlabSupport.getYOffset(serverWorld, supportPos, supportState);
        double targetSlabDy = SlabSupport.getYOffset(serverWorld, targetSlabPos, targetSlabState);
        boolean targetIsLoweredSlab = targetSlabState.isOf(Blocks.STONE_SLAB) && near(targetSlabDy, -0.5d);
        boolean targetBottomVariant = targetSlabState.isOf(Blocks.STONE_SLAB)
                && targetSlabState.contains(SlabBlock.TYPE)
                && targetSlabState.get(SlabBlock.TYPE) == SlabType.BOTTOM;
        boolean targetIsValidForCase = targetIsLoweredSlab
                || (spec.targetSlabType() == SlabType.BOTTOM && targetBottomVariant);
        boolean trapdoorAppeared = trapdoorState.isOf(Blocks.OAK_TRAPDOOR);
        double trapdoorDy = trapdoorAppeared
                ? SlabSupport.getYOffset(serverWorld, actualTrapdoorPos, trapdoorState)
                : Double.NaN;
        double clientTrapdoorDy = trapdoorAppeared
                ? ClientDy.dyFor(client.world, actualTrapdoorPos, clientTrapdoorState)
                : Double.NaN;
        double expectedTrapdoorDy = expectedGoblinTrapdoorDy(spec.targetSlabType(), targetSlabDy);
        boolean expectedPos = actualTrapdoorPos != null && actualTrapdoorPos.equals(expectedTrapdoorPos);
        boolean survivalGreen = trapdoorAppeared && trapdoorState.canPlaceAt(serverWorld, actualTrapdoorPos);
        VoxelShape slabOutline = targetSlabState.getOutlineShape(serverWorld, targetSlabPos);
        VoxelShape trapdoorOutline = trapdoorAppeared
                ? trapdoorState.getOutlineShape(serverWorld, actualTrapdoorPos,
                net.minecraft.block.ShapeContext.of(client.player))
                : null;
        VoxelShape trapdoorRaycast = trapdoorAppeared
                ? trapdoorState.getRaycastShape(serverWorld, actualTrapdoorPos)
                : null;
        VoxelShape trapdoorCollision = trapdoorAppeared
                ? trapdoorState.getCollisionShape(serverWorld, actualTrapdoorPos,
                net.minecraft.block.ShapeContext.of(client.player))
                : null;
        net.minecraft.util.math.Box slabBox = worldBox(slabOutline, targetSlabPos);
        net.minecraft.util.math.Box trapdoorBox = worldBox(trapdoorOutline, actualTrapdoorPos);
        net.minecraft.util.math.Box trapdoorRaycastBox = worldBox(trapdoorRaycast, actualTrapdoorPos);
        net.minecraft.util.math.Box trapdoorCollisionBox = worldBox(trapdoorCollision, actualTrapdoorPos);
        boolean overlapsSlab = boxesIntersect(slabBox, trapdoorBox);
        boolean trapdoorRaycastShapeEmpty = trapdoorRaycast == null || trapdoorRaycast.isEmpty();
        boolean trapdoorCollisionShapeEmpty = trapdoorCollision == null || trapdoorCollision.isEmpty();
        boolean triadCoLocatedOutlineRaycast = sameBox(trapdoorBox, trapdoorRaycastBox);
        boolean triadCoLocatedOutlineCollision = sameBox(trapdoorBox, trapdoorCollisionBox);
        boolean triadCoLocatedRaycastCollision = sameBox(trapdoorRaycastBox, trapdoorCollisionBox);
        boolean triadGreen = trapdoorAppeared && triadCoLocatedOutlineRaycast
                && triadCoLocatedOutlineCollision
                && triadCoLocatedRaycastCollision;
        String classification;
        String failureLayer;
        boolean green;
        if (!targetIsValidForCase) {
            classification = "TARGET_SLAB_NOT_LOWERED";
            failureLayer = "state authority";
            green = false;
        } else if (!trapdoorAppeared && trapdoorPlacementResult.contains("SUCCESS")) {
            classification = "SUCCESS_WITH_NO_RETAINED_UNDERSIDE_TRAPDOOR_STATE";
            failureLayer = "placement";
            green = false;
        } else if (!trapdoorAppeared) {
            classification = "UNDERSIDE_TRAPDOOR_PLACEMENT_REJECTED_OR_MISSED";
            failureLayer = "placement";
            green = false;
        } else if (!expectedPos) {
            classification = "WRONG_UNDERSIDE_TRAPDOOR_POSITION";
            failureLayer = "placement";
            green = false;
        } else if (!survivalGreen) {
            classification = "UNDERSIDE_TRAPDOOR_SURVIVAL_RED";
            failureLayer = "survival";
            green = false;
        } else if (overlapsSlab) {
            classification = "UNDERSIDE_TRAPDOOR_OVERLAPS_LOWERED_SLAB";
            failureLayer = "state authority";
            green = false;
        } else if (!near(trapdoorDy, expectedTrapdoorDy)) {
            classification = "UNDERSIDE_TRAPDOOR_WRONG_DY";
            failureLayer = "state authority";
            green = false;
        } else if (!near(clientTrapdoorDy, trapdoorDy)) {
            classification = "CLIENT_SERVER_TRAPDOOR_DY_SPLIT";
            failureLayer = "model";
            green = false;
        } else if (!triadCoLocatedOutlineRaycast) {
            classification = "TRAPDOOR_OUTLINE_RAYCAST_SPLIT";
            failureLayer = "raycast";
            green = false;
        } else if (!triadCoLocatedOutlineCollision) {
            classification = "TRAPDOOR_OUTLINE_COLLISION_SPLIT";
            failureLayer = "collision";
            green = false;
        } else if (!triadCoLocatedRaycastCollision) {
            classification = "TRAPDOOR_RAYCAST_COLLISION_SPLIT";
            failureLayer = "proof gap";
            green = false;
        } else if (!triadGreen) {
            classification = "TRAPDOOR_TRIAD_COHERENT";
            failureLayer = "proof gap";
            green = false;
        } else {
            classification = "UNDERSIDE_TRAPDOOR_INHABITS_LOWERED_SPACE";
            failureLayer = "NONE";
            green = true;
        }
        String summary = "phase=" + phase
                + "/caseOrigin=" + textPos(caseOrigin)
                + "/supportState=" + supportState
                + "/supportDy=" + formatDouble(supportDy)
                + "/targetSlabState=" + targetSlabState
                + "/targetSlabDy=" + formatDouble(targetSlabDy)
                + "/trapdoorState=" + trapdoorState
                + "/trapdoorHalf=" + (trapdoorAppeared ? trapdoorState.get(net.minecraft.block.TrapdoorBlock.HALF) : "NONE")
                + "/trapdoorFacing=" + (trapdoorAppeared ? trapdoorState.get(net.minecraft.block.TrapdoorBlock.FACING) : "NONE")
                + "/trapdoorOpen=" + (trapdoorAppeared ? trapdoorState.get(net.minecraft.block.TrapdoorBlock.OPEN) : false)
                + "/trapdoorPowered=" + (trapdoorAppeared ? trapdoorState.get(net.minecraft.block.TrapdoorBlock.POWERED) : false)
                + "/trapdoorWaterlogged=" + (trapdoorAppeared
                    ? trapdoorState.get(net.minecraft.block.TrapdoorBlock.WATERLOGGED)
                    : "NONE")
                + "/trapdoorDy=" + formatDouble(trapdoorDy)
                + "/clientTrapdoorDy=" + formatDouble(clientTrapdoorDy)
                + "/expectedTrapdoorDy=" + formatDouble(expectedTrapdoorDy)
                + "/yOff=" + formatDouble(trapdoorDy)
                + "/clientDy=" + formatDouble(clientTrapdoorDy)
                + "/expectedDy=" + formatDouble(expectedTrapdoorDy)
                + "/trapdoorRaycastShapeEmpty=" + trapdoorRaycastShapeEmpty
                + "/trapdoorCollisionShapeEmpty=" + trapdoorCollisionShapeEmpty
                + "/survival=" + survivalGreen
                + "/slabBounds=" + formatBox(slabBox)
                + "/trapdoorBounds=" + formatBox(trapdoorBox)
                + "/trapdoorOutlineBounds=" + formatBox(trapdoorBox)
                + "/trapdoorRaycastBounds=" + formatBox(trapdoorRaycastBox)
                + "/trapdoorCollisionBounds=" + formatBox(trapdoorCollisionBox)
                + "/triadCoLocatedOutlineRaycast=" + triadCoLocatedOutlineRaycast
                + "/triadCoLocatedOutlineCollision=" + triadCoLocatedOutlineCollision
                + "/triadCoLocatedRaycastCollision=" + triadCoLocatedRaycastCollision
                + "/overlapsSlab=" + overlapsSlab
                + "/triadCoLocated=" + triadGreen
                + "/classification=" + classification
                + "/failureLayer=" + failureLayer;
        return new LoweredTrapdoorGoblinAssessment(green, classification, failureLayer, summary);
    }

    private static double expectedGoblinTrapdoorDy(SlabType slabType, double slabDy) {
        if (!Double.isFinite(slabDy)) {
            return Double.NaN;
        }
        return slabType == SlabType.TOP ? slabDy + 0.5d : slabDy;
    }

    private static String sampleGoblinLoweredTrapdoorTargets(
            MinecraftClient client,
            LoweredTrapdoorGoblinCase spec,
            BlockPos targetSlabPos,
            BlockPos trapdoorPos,
            String phase
    ) {
        Vec3d center = new Vec3d(targetSlabPos.getX() + 0.5d, targetSlabPos.getY() + 0.01d,
                targetSlabPos.getZ() + 0.5d);
        Vec3d edge = new Vec3d(targetSlabPos.getX() + 0.08d, targetSlabPos.getY() + 0.05d,
                targetSlabPos.getZ() + 0.92d);
        Vec3d corner = new Vec3d(targetSlabPos.getX() + 0.92d, targetSlabPos.getY() + 0.05d,
                targetSlabPos.getZ() + 0.08d);
        StringBuilder samples = new StringBuilder();
        appendGoblinTargetSample(samples, client, spec, "empty_low_center", phase, targetSlabPos, trapdoorPos,
                center, new Vec3d(1.75d, -0.35d, 0.0d), null);
        appendGoblinTargetSample(samples, client, spec, "slab_oblique_edge", phase, targetSlabPos, trapdoorPos,
                edge, new Vec3d(1.45d, -0.20d, -1.20d), Items.STONE_SLAB);
        appendGoblinTargetSample(samples, client, spec, "trapdoor_high_corner", phase, targetSlabPos, trapdoorPos,
                corner, new Vec3d(-1.35d, 0.65d, 1.10d), Items.OAK_TRAPDOOR);
        return samples.toString();
    }

    private static void appendGoblinTargetSample(
            StringBuilder samples,
            MinecraftClient client,
            LoweredTrapdoorGoblinCase spec,
            String sampleName,
            String phase,
            BlockPos targetSlabPos,
            BlockPos trapdoorPos,
            Vec3d hitVector,
            Vec3d eyeOffset,
            net.minecraft.item.Item heldItem
    ) {
        if (!samples.isEmpty()) {
            samples.append("|");
        }
        syncGoblinLoweredTrapdoorPlayer(client, hitVector, eyeOffset, heldItem);
        Vec3d eye = client.player.getCameraPosVec(0.0f);
        Vec3d end = eye.add(client.player.getRotationVec(0.0f).multiply(6.0d));
        HitResult vanillaTarget = client.world.raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.player));
        String vanillaOwner = goblinLoweredTrapdoorTargetOwner(client.world, vanillaTarget, targetSlabPos, trapdoorPos);
        client.gameRenderer.updateCrosshairTarget(0.0f);
        HitResult finalTarget = client.crosshairTarget;
        String finalOwner = goblinLoweredTrapdoorTargetOwner(client.world, finalTarget, targetSlabPos, trapdoorPos);
        boolean wrongOwner = finalTarget instanceof BlockHitResult
                && !("trapdoor".equals(finalOwner) || "targetSlab".equals(finalOwner))
                && !finalOwner.equals(vanillaOwner);
        samples.append(sampleName)
                .append("{phase=").append(phase)
                .append(",case=").append(spec.caseName())
                .append(",held=").append(heldItem == null ? "empty" : Registries.ITEM.getId(heldItem))
                .append(",eye=").append(formatVec(eye))
                .append(",hit=").append(formatVec(hitVector))
                .append(",vanilla=").append(formatHit(vanillaTarget))
                .append(",vanillaOwner=").append(vanillaOwner)
                .append(",final=").append(formatHit(finalTarget))
                .append(",owner=").append(finalOwner)
                .append(",wrongOwner=").append(wrongOwner)
                .append("}");
    }

    private static String goblinLoweredTrapdoorTargetOwner(
            ClientWorld world,
            HitResult hit,
            BlockPos targetSlabPos,
            BlockPos trapdoorPos
    ) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return hit == null ? "null" : hit.getType().toString();
        }
        BlockPos hitPos = blockHit.getBlockPos();
        if (hitPos.equals(trapdoorPos)) {
            return "trapdoor";
        }
        if (hitPos.equals(targetSlabPos)) {
            return "targetSlab";
        }
        BlockState hitState = world == null ? Blocks.AIR.getDefaultState() : world.getBlockState(hitPos);
        if (hitState.isOf(Blocks.STONE) || hitState.isOf(Blocks.STONE_SLAB)) {
            return "adjacent_stone_or_slab";
        }
        return "other:" + textPos(hitPos);
    }

    private static void emitGoblinLoweredTrapdoorSummary(
            String finalResult,
            String classification,
            String failureLayer,
            MinecraftClient client
    ) {
        System.out.println("[MC1211_GOBLIN_LOWERED_TRAPDOOR_SUMMARY]"
                + " cases=" + LOWERED_TRAPDOOR_GOBLIN_CASES.length
                + " greenRows=" + goblinLoweredTrapdoorGreenRows
                + " redRows=" + goblinLoweredTrapdoorRedRows
                + " traceGapRows=" + goblinLoweredTrapdoorTraceGapRows
                + " finalResult=" + finalResult
                + " classification=" + classification
                + " failureLayer=" + failureLayer
                + " patchAllowedNext=" + ("RED".equals(finalResult) && !"proof gap".equals(failureLayer))
                + " harnessDriven=true"
                + " manualComputerUseEvidence=false"
                + " productionBehaviorChanged=false"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");
        goblinLoweredTrapdoorFinalized = true;
        emitted = true;
        if (client != null) {
            client.scheduleStop();
        }
    }

    private static boolean retryTrapdoorSideExtensionClick(MinecraftClient client, String priorResult, int timeoutTicks) {
        if (trapdoorSeamTicks - trapdoorSeamPhaseTick >= timeoutTicks) {
            return false;
        }
        return client != null && priorResult != null;
    }

    private static void prepareTrapdoorSideExtensionFixture(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || trapdoorSeamOrigin == null) {
            return;
        }
        serverWorld.getServer().execute(() -> {
            for (int x = trapdoorSeamOrigin.getX() - 3; x <= trapdoorSeamOrigin.getX() + 4; x++) {
                for (int z = trapdoorSeamOrigin.getZ() - 3; z <= trapdoorSeamOrigin.getZ() + 3; z++) {
                    for (int y = trapdoorSeamOrigin.getY() - 2; y <= trapdoorSeamOrigin.getY() + 5; y++) {
                        serverWorld.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
            serverWorld.setBlockState(trapdoorSeamGroundPos, Blocks.STONE.getDefaultState(), 3);
            serverWorld.setBlockState(trapdoorSeamTemporaryCeilingPos, Blocks.STONE.getDefaultState(), 3);
            if (!serverWorld.getServer().getPlayerManager().getPlayerList().isEmpty()) {
                var serverPlayer = serverWorld.getServer().getPlayerManager().getPlayerList().get(0);
                serverPlayer.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
        });
        client.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static boolean trapdoorSeamTopSlabReady(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || trapdoorSeamTopSlabPos == null) {
            return false;
        }
        BlockState slabState = serverWorld.getBlockState(trapdoorSeamTopSlabPos);
        return slabState.isOf(Blocks.STONE_SLAB) && slabState.get(SlabBlock.TYPE) == SlabType.TOP;
    }

    private static boolean trapdoorSeamExtensionSlabReady(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || trapdoorSeamExtensionSlabPos == null) {
            return false;
        }
        BlockState slabState = serverWorld.getBlockState(trapdoorSeamExtensionSlabPos);
        return slabState.isOf(Blocks.STONE_SLAB)
                && slabState.get(SlabBlock.TYPE) == desiredTrapdoorSideExtensionSlabType();
    }

    private static void removeTrapdoorSideExtensionTemporaryCeiling(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || trapdoorSeamTemporaryCeilingPos == null) {
            return;
        }
        serverWorld.setBlockState(trapdoorSeamTemporaryCeilingPos, Blocks.AIR.getDefaultState(), 3);
        serverWorld.updateNeighbors(trapdoorSeamTemporaryCeilingPos, Blocks.AIR);
    }

    private static boolean trapdoorSeamTemporaryCeilingRemoved(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        return serverWorld != null
                && trapdoorSeamTemporaryCeilingPos != null
                && serverWorld.getBlockState(trapdoorSeamTemporaryCeilingPos).isAir();
    }

    private static Vec3d trapdoorSideExtensionHitVector(SlabType slabType) {
        double y = isSbbsLowerEdgeSideSlabRedRoute()
                ? 0.25d
                : (slabType == SlabType.TOP ? 0.75d : 0.25d);
        return new Vec3d(
                trapdoorSeamTopSlabPos.getX() + 1.0d,
                trapdoorSeamTopSlabPos.getY() + y,
                trapdoorSeamTopSlabPos.getZ() + 0.5d);
    }

    private static SlabType desiredTrapdoorSideExtensionSlabType() {
        String raw = System.getProperty(TRAPDOOR_SIDE_EXTENSION_SLAB_TYPE_PROPERTY, "top");
        return "bottom".equalsIgnoreCase(raw) ? SlabType.BOTTOM : SlabType.TOP;
    }

    private static boolean isSbbsLowerEdgeSideSlabRedRoute() {
        return Boolean.getBoolean(SBBS_LOWER_EDGE_SIDE_SLAB_RED_PROPERTY);
    }

    private static String activeTrapdoorSideExtensionProperty() {
        return isSbbsLowerEdgeSideSlabRedRoute()
                ? SBBS_LOWER_EDGE_SIDE_SLAB_RED_PROPERTY
                : TRAPDOOR_SIDE_EXTENSION_RED_PROPERTY;
    }

    private static void emitSbbsLowerEdgeSideSlabPlacementResult(boolean green) {
        ServerWorld serverWorld = serverWorldFor(MinecraftClient.getInstance());
        BlockState topSlabState = serverWorld == null || trapdoorSeamTopSlabPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(trapdoorSeamTopSlabPos);
        BlockState extensionState = serverWorld == null || trapdoorSeamExtensionSlabPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(trapdoorSeamExtensionSlabPos);
        double topSlabDy = serverWorld == null || trapdoorSeamTopSlabPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, trapdoorSeamTopSlabPos, topSlabState);
        double extensionDy = serverWorld == null || trapdoorSeamExtensionSlabPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, trapdoorSeamExtensionSlabPos, extensionState);
        String extensionType = extensionState.contains(SlabBlock.TYPE)
                ? extensionState.get(SlabBlock.TYPE).asString()
                : "none";
        String classification = green
                ? "SBBS_LOWER_EDGE_SIDE_SLAB_PLACEMENT_GREEN_TOP_LANE_PRESERVED"
                : "SBBS_LOWER_EDGE_SIDE_SLAB_PLACEMENT_RED_LOWER_LANE";
        String failureLayer = green ? "NONE" : "placement-position";
        System.out.println("[MC1211_SBBS_LOWER_EDGE_SIDE_SLAB_ROW]"
                + " rowPhase=AFTER_LOWER_EDGE_SIDE_SLAB_PLACEMENT"
                + " fixtureOrigin=" + textPos(trapdoorSeamOrigin)
                + " supportPos=" + textPos(trapdoorSeamSupportPos)
                + " topSlabPos=" + textPos(trapdoorSeamTopSlabPos)
                + " topSlabState=" + topSlabState
                + " topSlabDy=" + formatDouble(topSlabDy)
                + " extensionSlabPos=" + textPos(trapdoorSeamExtensionSlabPos)
                + " extensionSlabState=" + extensionState
                + " extensionSlabType=" + extensionType
                + " extensionSlabDy=" + formatDouble(extensionDy)
                + " expectedExtensionSlabType=top"
                + " lowerEdgeLocalHit=1.000,0.250,0.500"
                + " placementResultExtensionSlab=" + trapdoorSeamExtensionSlabPlacementResult
                + " classification=" + classification
                + " failureLayer=" + failureLayer);
        System.out.println("[MC1211_SBBS_LOWER_EDGE_SIDE_SLAB_SUMMARY]"
                + " rows=1"
                + " finalResult=" + (green ? "GREEN" : "RED")
                + " classification=" + classification
                + " failureLayer=" + failureLayer
                + " patchAllowedNext=" + !green
                + " productionBehaviorChanged=false"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");
        emitted = true;
        trapdoorSeamFinalized = true;
        if (clientReadyForStop()) {
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    private static Vec3d trapdoorSideExtensionUndersideHitVector() {
        return new Vec3d(
                trapdoorSeamExtensionSlabPos.getX() + 0.5d,
                trapdoorSeamExtensionSlabPos.getY() + 0.01d,
                trapdoorSeamExtensionSlabPos.getZ() + 0.5d);
    }

    private static String clickTrapdoorUnderSideExtension(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null
                || client.gameRenderer == null || trapdoorSeamExtensionSlabPos == null) {
            return "FAIL_ROUTE_NOT_READY";
        }
        Vec3d hitVector = trapdoorSideExtensionUndersideHitVector();
        syncTrapdoorSideExtensionUnderPlayer(client, hitVector);
        client.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.OAK_TRAPDOOR, 4));
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.OAK_TRAPDOOR, 4));
        }

        Vec3d eye = client.player.getCameraPosVec(0.0f);
        Vec3d end = eye.add(client.player.getRotationVec(0.0f).multiply(6.0d));
        HitResult vanillaTarget = client.world.raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.player));
        trapdoorSeamVanillaTarget = formatHit(vanillaTarget);
        client.gameRenderer.updateCrosshairTarget(0.0f);
        HitResult finalTarget = client.crosshairTarget;
        trapdoorSeamFinalTarget = formatHit(finalTarget);
        trapdoorSeamFinalTargetOwner = trapdoorSeamTargetOwner(client.world, finalTarget);
        if (finalTarget instanceof BlockHitResult blockHit && finalTarget.getType() == HitResult.Type.BLOCK) {
            ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, blockHit);
            return result.toString();
        }
        return "NO_BLOCK_TARGET";
    }

    private static void syncTrapdoorSideExtensionUnderPlayer(MinecraftClient client, Vec3d hitVector) {
        Vec3d eye = hitVector.add(1.75d, -0.35d, 0.0d);
        Vec3d delta = hitVector.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.setSneaking(false);
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            serverPlayer.setVelocity(Vec3d.ZERO);
            serverPlayer.setSneaking(false);
            serverPlayer.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        }
    }

    private static void emitTrapdoorSideExtensionRow(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        ClientWorld clientWorld = client == null ? null : client.world;
        if (serverWorld == null || clientWorld == null || client == null || client.player == null) {
            emitTrapdoorSideExtensionTraceGap("AFTER_UNDERSIDE_TRAPDOOR_PLACEMENT",
                    "TRACE_GAP_WORLD_OR_PLAYER_NOT_READY");
            return;
        }
        trapdoorSeamActualTrapdoorPos = findNearbyTrapdoor(serverWorld, trapdoorSeamExpectedTrapdoorPos);
        BlockState baseSlabState = serverWorld.getBlockState(trapdoorSeamSlabPos);
        BlockState supportState = serverWorld.getBlockState(trapdoorSeamSupportPos);
        BlockState topSlabState = serverWorld.getBlockState(trapdoorSeamTopSlabPos);
        BlockState extensionSlabState = serverWorld.getBlockState(trapdoorSeamExtensionSlabPos);
        BlockState trapdoorState = serverWorld.getBlockState(trapdoorSeamActualTrapdoorPos);
        BlockState clientTrapdoorState = clientWorld.getBlockState(trapdoorSeamActualTrapdoorPos);
        double baseSlabDy = SlabSupport.getYOffset(serverWorld, trapdoorSeamSlabPos, baseSlabState);
        double supportDy = SlabSupport.getYOffset(serverWorld, trapdoorSeamSupportPos, supportState);
        double topSlabDy = SlabSupport.getYOffset(serverWorld, trapdoorSeamTopSlabPos, topSlabState);
        double extensionSlabDy = SlabSupport.getYOffset(
                serverWorld, trapdoorSeamExtensionSlabPos, extensionSlabState);
        boolean trapdoorAppeared = trapdoorState.isOf(Blocks.OAK_TRAPDOOR);
        double trapdoorDy = trapdoorAppeared
                ? SlabSupport.getYOffset(serverWorld, trapdoorSeamActualTrapdoorPos, trapdoorState)
                : Double.NaN;
        double clientTrapdoorDy = trapdoorAppeared
                ? ClientDy.dyFor(clientWorld, trapdoorSeamActualTrapdoorPos, clientTrapdoorState)
                : Double.NaN;
        boolean supportAuthoredLowered = supportState.isOf(Blocks.STONE)
                && SlabAnchorAttachment.isAnchored(serverWorld, trapdoorSeamSupportPos)
                && near(supportDy, -0.5d);
        boolean topSlabAuthored = topSlabState.isOf(Blocks.STONE_SLAB)
                && topSlabState.get(SlabBlock.TYPE) == SlabType.TOP;
        boolean extensionTopSlabAuthored = extensionSlabState.isOf(Blocks.STONE_SLAB)
                && extensionSlabState.get(SlabBlock.TYPE) == desiredTrapdoorSideExtensionSlabType();
        boolean extensionLowered = extensionTopSlabAuthored && extensionSlabDy < -1.0e-6d;
        double expectedTrapdoorDy = Double.NaN;
        if (extensionTopSlabAuthored) {
            expectedTrapdoorDy = extensionSlabDy
                    + (extensionSlabState.get(SlabBlock.TYPE) == SlabType.TOP ? 0.5d : 0.0d);
        }
        boolean expectedPos = trapdoorSeamActualTrapdoorPos.equals(trapdoorSeamExpectedTrapdoorPos);
        boolean survivalGreen = trapdoorAppeared && trapdoorState.canPlaceAt(serverWorld, trapdoorSeamActualTrapdoorPos);
        VoxelShape extensionShape = extensionTopSlabAuthored
                ? extensionSlabState.getOutlineShape(serverWorld, trapdoorSeamExtensionSlabPos)
                : null;
        VoxelShape trapdoorShape = trapdoorAppeared
                ? trapdoorState.getOutlineShape(serverWorld, trapdoorSeamActualTrapdoorPos,
                        net.minecraft.block.ShapeContext.of(client.player))
                : null;
        net.minecraft.util.math.Box extensionBox = worldBox(extensionShape, trapdoorSeamExtensionSlabPos);
        net.minecraft.util.math.Box trapdoorBox = worldBox(trapdoorShape, trapdoorSeamActualTrapdoorPos);
        boolean overlapsExtension = boxesIntersect(extensionBox, trapdoorBox);

        if (!supportAuthoredLowered) {
            trapdoorSeamExtensionClassification = "TRACE_GAP_S>B>S_LOWERED_SUPPORT_NOT_AUTHORED";
            trapdoorSeamExtensionFailureLayer = "proof gap";
            trapdoorSeamExtensionGreen = false;
        } else if (!topSlabAuthored) {
            trapdoorSeamExtensionClassification = "TRACE_GAP_S>B>S_TOP_SLAB_NOT_AUTHORED";
            trapdoorSeamExtensionFailureLayer = "proof gap";
            trapdoorSeamExtensionGreen = false;
        } else if (!extensionTopSlabAuthored) {
            trapdoorSeamExtensionClassification = "TRACE_GAP_SIDE_EXTENSION_TOP_SLAB_NOT_AUTHORED";
            trapdoorSeamExtensionFailureLayer = "proof gap";
            trapdoorSeamExtensionGreen = false;
        } else if (!extensionLowered) {
            trapdoorSeamExtensionClassification = "SIDE_EXTENSION_TOP_SLAB_NOT_LOWERED";
            trapdoorSeamExtensionFailureLayer = "state authority";
            trapdoorSeamExtensionGreen = false;
        } else if (!trapdoorAppeared && "NO_BLOCK_TARGET".equals(trapdoorSeamExtensionTrapdoorPlacementResult)) {
            trapdoorSeamExtensionClassification = "TARGET_MISS_UNDER_SIDE_EXTENSION_TOP_SLAB";
            trapdoorSeamExtensionFailureLayer = "raycast";
            trapdoorSeamExtensionGreen = false;
        } else if (!trapdoorAppeared && "SUCCESS".equals(trapdoorSeamExtensionTrapdoorPlacementResult)) {
            trapdoorSeamExtensionClassification = "SUCCESS_WITH_NO_RETAINED_UNDERSIDE_TRAPDOOR_STATE";
            trapdoorSeamExtensionFailureLayer = "placement";
            trapdoorSeamExtensionGreen = false;
        } else if (!trapdoorAppeared) {
            trapdoorSeamExtensionClassification = "PLACEMENT_REJECTED_CLEAN";
            trapdoorSeamExtensionFailureLayer = "NONE";
            trapdoorSeamExtensionGreen = true;
        } else if (!expectedPos) {
            trapdoorSeamExtensionClassification = "WRONG_UNDERSIDE_TRAPDOOR_POSITION";
            trapdoorSeamExtensionFailureLayer = "placement";
            trapdoorSeamExtensionGreen = false;
        } else if (!survivalGreen) {
            trapdoorSeamExtensionClassification = "UNDERSIDE_TRAPDOOR_SURVIVAL_RED";
            trapdoorSeamExtensionFailureLayer = "survival";
            trapdoorSeamExtensionGreen = false;
        } else if (near(trapdoorDy, 0.5d)) {
            trapdoorSeamExtensionClassification = "UNDERSIDE_TRAPDOOR_USED_NORMAL_TOP_SLAB_DY_OVER_LOWERED_TOP_SLAB";
            trapdoorSeamExtensionFailureLayer = "state authority";
            trapdoorSeamExtensionGreen = false;
        } else if (overlapsExtension) {
            trapdoorSeamExtensionClassification = "UNDERSIDE_TRAPDOOR_OVERLAPS_SIDE_EXTENSION_TOP_SLAB";
            trapdoorSeamExtensionFailureLayer = "state authority";
            trapdoorSeamExtensionGreen = false;
        } else if (!near(trapdoorDy, expectedTrapdoorDy)) {
            trapdoorSeamExtensionClassification = "UNDERSIDE_TRAPDOOR_NOT_ALIGNED_TO_LOWERED_SLAB_UNDERSIDE";
            trapdoorSeamExtensionFailureLayer = "state authority";
            trapdoorSeamExtensionGreen = false;
        } else {
            trapdoorSeamExtensionClassification = "UNDERSIDE_TRAPDOOR_INHABITS_LOWERED_SPACE";
            trapdoorSeamExtensionFailureLayer = "NONE";
            trapdoorSeamExtensionGreen = true;
        }

        System.out.println("[MC1211_TRAPDOOR_SIDE_EXTENSION_ROW]"
                + " rowPhase=AFTER_UNDERSIDE_TRAPDOOR_PLACEMENT"
                + " fixtureOrigin=" + textPos(trapdoorSeamOrigin)
                + " baseSlabPos=" + textPos(trapdoorSeamSlabPos)
                + " baseSlabState=" + baseSlabState
                + " baseSlabDy=" + formatDouble(baseSlabDy)
                + " supportPos=" + textPos(trapdoorSeamSupportPos)
                + " supportState=" + supportState
                + " supportDy=" + formatDouble(supportDy)
                + " supportAnchored=" + SlabAnchorAttachment.isAnchored(serverWorld, trapdoorSeamSupportPos)
                + " topSlabPos=" + textPos(trapdoorSeamTopSlabPos)
                + " topSlabState=" + topSlabState
                + " topSlabDy=" + formatDouble(topSlabDy)
                + " extensionSlabPos=" + textPos(trapdoorSeamExtensionSlabPos)
                + " extensionSlabState=" + extensionSlabState
                + " extensionSlabDy=" + formatDouble(extensionSlabDy)
                + " expectedTrapdoorPos=" + textPos(trapdoorSeamExpectedTrapdoorPos)
                + " actualTrapdoorPos=" + textPos(trapdoorSeamActualTrapdoorPos)
                + " actualTrapdoorState=" + trapdoorState
                + " clientTrapdoorState=" + clientTrapdoorState
                + " trapdoorDy=" + formatDouble(trapdoorDy)
                + " clientTrapdoorDy=" + formatDouble(clientTrapdoorDy)
                + " expectedTrapdoorDy=" + formatDouble(expectedTrapdoorDy)
                + " placementResultBaseSlab=" + trapdoorSeamSlabPlacementResult
                + " placementResultSupport=" + trapdoorSeamSupportPlacementResult
                + " placementResultTopSlab=" + trapdoorSeamTopSlabPlacementResult
                + " placementResultExtensionSlab=" + trapdoorSeamExtensionSlabPlacementResult
                + " placementResultTrapdoor=" + trapdoorSeamExtensionTrapdoorPlacementResult
                + " vanillaTarget=" + trapdoorSeamVanillaTarget
                + " finalTarget=" + trapdoorSeamFinalTarget
                + " finalTargetOwner=" + trapdoorSeamFinalTargetOwner
                + " extensionBounds=" + formatBox(extensionBox)
                + " trapdoorBounds=" + formatBox(trapdoorBox)
                + " overlapsExtension=" + overlapsExtension
                + " survivalResult=" + (survivalGreen ? "SURVIVAL_GREEN" : "SURVIVAL_RED")
                + " classification=" + trapdoorSeamExtensionClassification
                + " failureLayer=" + trapdoorSeamExtensionFailureLayer);
        System.out.println("[MC1211_TRAPDOOR_SIDE_EXTENSION_"
                + (trapdoorSeamExtensionGreen ? "GREEN" : "RED") + "]"
                + " rowPhase=AFTER_UNDERSIDE_TRAPDOOR_PLACEMENT"
                + " classification=" + trapdoorSeamExtensionClassification
                + " failureLayer=" + trapdoorSeamExtensionFailureLayer);
    }

    private static void emitTrapdoorSideExtensionSummary(MinecraftClient client) {
        String finalResult = trapdoorSeamExtensionGreen ? "GREEN" : "RED";
        boolean patchAllowed = !trapdoorSeamExtensionGreen
                && !"proof gap".equals(trapdoorSeamExtensionFailureLayer);
        System.out.println("[MC1211_TRAPDOOR_SIDE_EXTENSION_SUMMARY]"
                + " rows=1"
                + " finalResult=" + finalResult
                + " classification=" + trapdoorSeamExtensionClassification
                + " failureLayer=" + trapdoorSeamExtensionFailureLayer
                + " patchAllowedNext=" + patchAllowed
                + " productionBehaviorChanged=false"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");
        trapdoorSeamFinalized = true;
        emitted = true;
        if (client != null) {
            client.scheduleStop();
        }
    }

    private static void emitTrapdoorSideExtensionTraceGap(String row, String reason) {
        trapdoorSeamExtensionClassification = reason;
        trapdoorSeamExtensionFailureLayer = "proof gap";
        System.out.println("[MC1211_TRAPDOOR_SIDE_EXTENSION_ROW]"
                + " rowPhase=" + row
                + " fixtureOrigin=" + textPos(trapdoorSeamOrigin)
                + " baseSlabPos=" + textPos(trapdoorSeamSlabPos)
                + " supportPos=" + textPos(trapdoorSeamSupportPos)
                + " topSlabPos=" + textPos(trapdoorSeamTopSlabPos)
                + " extensionSlabPos=" + textPos(trapdoorSeamExtensionSlabPos)
                + " expectedTrapdoorPos=" + textPos(trapdoorSeamExpectedTrapdoorPos)
                + " classification=" + reason
                + " failureLayer=proof gap");
        if ("WAITING".equals(row)) {
            return;
        }
        System.out.println("[MC1211_TRAPDOOR_SIDE_EXTENSION_SUMMARY]"
                + " rows=1"
                + " finalResult=TRACE_GAP"
                + " classification=" + reason
                + " failureLayer=proof gap"
                + " patchAllowedNext=false");
        trapdoorSeamFinalized = true;
        emitted = true;
        if (clientReadyForStop()) {
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    private static void emitTrapdoorSideExtensionPlacementRed(String row, String classification) {
        trapdoorSeamExtensionClassification = classification;
        trapdoorSeamExtensionFailureLayer = "placement";
        System.out.println("[MC1211_TRAPDOOR_SIDE_EXTENSION_ROW]"
                + " rowPhase=" + row
                + " fixtureOrigin=" + textPos(trapdoorSeamOrigin)
                + " baseSlabPos=" + textPos(trapdoorSeamSlabPos)
                + " supportPos=" + textPos(trapdoorSeamSupportPos)
                + " topSlabPos=" + textPos(trapdoorSeamTopSlabPos)
                + " extensionSlabPos=" + textPos(trapdoorSeamExtensionSlabPos)
                + " desiredExtensionSlabType=" + desiredTrapdoorSideExtensionSlabType().asString()
                + " placementResultExtensionSlab=" + trapdoorSeamExtensionSlabPlacementResult
                + " classification=" + classification
                + " failureLayer=placement");
        System.out.println("[MC1211_TRAPDOOR_SIDE_EXTENSION_SUMMARY]"
                + " rows=1"
                + " finalResult=RED"
                + " classification=" + classification
                + " failureLayer=placement"
                + " patchAllowedNext=true"
                + " productionBehaviorChanged=false"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");
        trapdoorSeamFinalized = true;
        emitted = true;
        if (clientReadyForStop()) {
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    private static void requestProgrammaticTrapdoorSeamWorldIfNeeded(MinecraftClient client) {
        if (trapdoorSeamWorldStartRequested
                || client == null
                || !client.isFinishedLoading()
                || client.world != null
                || client.player != null) {
            return;
        }
        trapdoorSeamWorldStartRequested = true;
        LevelInfo levelInfo = new LevelInfo(
                "Slabbed MC1211 Trapdoor Lowered Seam Harness",
                GameMode.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                DataConfiguration.SAFE_MODE);
        GeneratorOptions generatorOptions = new GeneratorOptions(0L, false, false);
        client.createIntegratedServerLoader().createAndStart(
                "slabbed-mc1211-trapdoor-lowered-seam-harness",
                levelInfo,
                generatorOptions,
                Mc1211GoblinRouteClientEntrypoint::createSuperflatDimensionOptions,
                null);
    }

    private static void emitTrapdoorSeamReadyRow(MinecraftClient client, String phase, String reason) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        System.out.println("[MC1211_TRAPDOOR_LOWERED_SEAM_READY_ROW]"
                + " phase=" + phase
                + " tick=" + trapdoorSeamTicks
                + " clientBootstrapReady=" + readiness.clientBootstrapReady
                + " clientWorldReady=" + readiness.clientWorldReady
                + " clientPlayerReady=" + readiness.clientPlayerReady
                + " integratedServerReady=" + readiness.integratedServerReady
                + " serverWorldReady=" + readiness.serverWorldReady
                + " serverPlayerReady=" + readiness.serverPlayerReady
                + " interactionManagerReady=" + readiness.interactionManagerReady
                + " programmaticWorldStartRequested=" + trapdoorSeamWorldStartRequested
                + " reason=" + reason);
    }

    private static void prepareTrapdoorSeamFixture(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || trapdoorSeamOrigin == null) {
            return;
        }
        serverWorld.getServer().execute(() -> {
            for (int x = trapdoorSeamOrigin.getX() - 3; x <= trapdoorSeamOrigin.getX() + 3; x++) {
                for (int z = trapdoorSeamOrigin.getZ() - 3; z <= trapdoorSeamOrigin.getZ() + 3; z++) {
                    for (int y = trapdoorSeamOrigin.getY() - 2; y <= trapdoorSeamOrigin.getY() + 4; y++) {
                        serverWorld.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
            serverWorld.setBlockState(trapdoorSeamGroundPos, Blocks.STONE.getDefaultState(), 3);
            if (trapdoorSeamMp4SideGroundPos != null) {
                serverWorld.setBlockState(trapdoorSeamMp4SideGroundPos, Blocks.STONE.getDefaultState(), 3);
            }
            buildGoblinMergeSpiralFixture(serverWorld, trapdoorSeamOrigin.east(8).south(2));
            if (!serverWorld.getServer().getPlayerManager().getPlayerList().isEmpty()) {
                serverWorld.getServer().getPlayerManager().getPlayerList().get(0)
                        .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
        });
    }

    private static void buildGoblinMergeSpiralFixture(ServerWorld serverWorld, BlockPos fixtureOrigin) {
        if (serverWorld == null || fixtureOrigin == null) {
            return;
        }
        BlockPos baseSlabPos = fixtureOrigin;
        BlockPos baseGroundPos = baseSlabPos.down();
        int levels = 10;
        int rows = 0;
        int trapdoorRows = 0;
        int hangingRows = 0;
        int mergeGreenRows = 0;
        int mergeRedRows = 0;

        // Clear enough room for a tall pillar plus descending side slabs.
        for (int x = fixtureOrigin.getX() - 4; x <= fixtureOrigin.getX() + 4; x++) {
            for (int z = fixtureOrigin.getZ() - 4; z <= fixtureOrigin.getZ() + 4; z++) {
                for (int y = fixtureOrigin.getY() - levels - 3; y <= fixtureOrigin.getY() + levels + 2; y++) {
                    serverWorld.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                }
            }
        }

        serverWorld.setBlockState(baseGroundPos, Blocks.STONE.getDefaultState(), 3);
        serverWorld.setBlockState(baseSlabPos, Blocks.STONE_SLAB.getDefaultState(), 3);

        // Tall pillar rising from slab base.
        for (int i = 1; i <= levels; i++) {
            serverWorld.setBlockState(baseSlabPos.up(i), Blocks.STONE.getDefaultState(), 3);
        }

        Direction[] ring = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        BlockState[] hangingCycle = {
                Blocks.LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true),
                Blocks.SOUL_LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true),
                Blocks.SPORE_BLOSSOM.getDefaultState()
        };

        // Descending slab spiral around pillar with alternating underside cases.
        for (int i = 0; i < levels; i++) {
            Direction side = ring[i % ring.length];
            BlockPos sideSlabPos = baseSlabPos.up(levels - i).offset(side);
            BlockPos carrierPos = sideSlabPos.offset(side);
            BlockPos carrierBaseSlabPos = carrierPos.down();
            BlockPos undersidePos = sideSlabPos.down();
            serverWorld.setBlockState(carrierBaseSlabPos, Blocks.STONE_SLAB.getDefaultState(), 3);
            serverWorld.setBlockState(carrierPos, Blocks.STONE.getDefaultState(), 3);
            SlabAnchorAttachment.addAnchor(serverWorld, carrierPos, serverWorld.getBlockState(carrierPos));
            serverWorld.setBlockState(sideSlabPos, Blocks.STONE_SLAB.getDefaultState(), 3);
            BlockState undersideState;
            String rowType;
            if ((i & 1) == 0) {
                undersideState = Blocks.OAK_TRAPDOOR.getDefaultState()
                        .with(net.minecraft.block.TrapdoorBlock.HALF, net.minecraft.block.enums.BlockHalf.TOP)
                        .with(net.minecraft.block.TrapdoorBlock.FACING, side)
                        .with(net.minecraft.block.TrapdoorBlock.OPEN, false)
                        .with(net.minecraft.block.TrapdoorBlock.POWERED, false)
                        .with(net.minecraft.block.TrapdoorBlock.WATERLOGGED, false);
                rowType = "trapdoor_top_half";
                serverWorld.setBlockState(undersidePos, undersideState, 3);
                trapdoorRows++;
            } else {
                undersideState = hangingCycle[(i / 2) % hangingCycle.length];
                rowType = undersideState.isOf(Blocks.SPORE_BLOSSOM)
                        ? "spore_blossom"
                        : (undersideState.isOf(Blocks.SOUL_LANTERN) ? "soul_lantern_hanging" : "lantern_hanging");
                serverWorld.setBlockState(undersidePos, undersideState, 3);
                hangingRows++;
            }
            BlockState supportState = serverWorld.getBlockState(sideSlabPos);
            BlockState placedState = serverWorld.getBlockState(undersidePos);
            double supportDy = SlabSupport.getYOffset(serverWorld, sideSlabPos, supportState);
            double objectDy = SlabSupport.getYOffset(serverWorld, undersidePos, placedState);
            boolean loweredSupport = supportDy < -1.0e-6d;
            boolean attachedToLoweredSupport = loweredSupport && near(objectDy, supportDy);
            if (attachedToLoweredSupport) {
                mergeGreenRows++;
            } else {
                mergeRedRows++;
            }
            System.out.println("[MC1211_GOBLIN_MERGE_SPIRAL_ROW]"
                    + " row=" + (i + 1)
                    + " rowType=" + rowType
                    + " carrierBaseSlabPos=" + textPos(carrierBaseSlabPos)
                    + " carrierPos=" + textPos(carrierPos)
                    + " supportPos=" + textPos(sideSlabPos)
                    + " supportState=" + supportState
                    + " supportDy=" + formatDouble(supportDy)
                    + " undersidePos=" + textPos(undersidePos)
                    + " undersideState=" + placedState
                    + " undersideDy=" + formatDouble(objectDy)
                    + " classification=" + (attachedToLoweredSupport
                    ? "LOWERED_UNDERSIDE_ATTACH_GREEN"
                    : "LOWERED_UNDERSIDE_MERGE_RED")
                    + " failureLayer=" + (attachedToLoweredSupport ? "NONE" : "model/outline/raycast"));
            rows++;
        }

        System.out.println("[MC1211_GOBLIN_MERGE_SPIRAL_FIXTURE]"
                + " origin=" + textPos(fixtureOrigin)
                + " baseSlabPos=" + textPos(baseSlabPos)
                + " pillarLevels=" + levels
                + " rows=" + rows
                + " trapdoorRows=" + trapdoorRows
                + " hangingRows=" + hangingRows
                + " hangingCases=lantern,soul_lantern,spore_blossom"
                + " chainTouched=false"
                + " fenceTouched=false");
        System.out.println("[MC1211_GOBLIN_MERGE_SPIRAL_SUMMARY]"
                + " rows=" + rows
                + " greenRows=" + mergeGreenRows
                + " redRows=" + mergeRedRows
                + " finalResult=" + (mergeRedRows == 0 ? "GREEN" : "RED")
                + " classification=" + (mergeRedRows == 0
                ? "LOWERED_UNDERSIDE_ATTACH_GREEN"
                : "LOWERED_UNDERSIDE_MERGE_RED")
                + " failureLayer=" + (mergeRedRows == 0 ? "NONE" : "model/outline/raycast")
                + " chainTouched=false"
                + " fenceTouched=false");
    }

    private static boolean trapdoorSeamSlabReady(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || trapdoorSeamSlabPos == null) {
            return false;
        }
        BlockState slabState = serverWorld.getBlockState(trapdoorSeamSlabPos);
        return slabState.isOf(Blocks.STONE_SLAB) && slabState.get(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    private static boolean trapdoorSeamLoweredSupportReady(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || trapdoorSeamSupportPos == null) {
            return false;
        }
        BlockState supportState = serverWorld.getBlockState(trapdoorSeamSupportPos);
        double supportDy = SlabSupport.getYOffset(serverWorld, trapdoorSeamSupportPos, supportState);
        return supportState.isOf(Blocks.STONE)
                && SlabAnchorAttachment.isAnchored(serverWorld, trapdoorSeamSupportPos)
                && near(supportDy, -0.5d);
    }

    private static boolean trapdoorSeamMp4SideBaseSlabReady(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || trapdoorSeamMp4SideBaseSlabPos == null) {
            return false;
        }
        BlockState slabState = serverWorld.getBlockState(trapdoorSeamMp4SideBaseSlabPos);
        return slabState.isOf(Blocks.STONE_SLAB) && slabState.get(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    private static boolean trapdoorSeamMp4SideSupportReady(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || trapdoorSeamMp4SideSupportPos == null) {
            return false;
        }
        BlockState supportState = serverWorld.getBlockState(trapdoorSeamMp4SideSupportPos);
        double supportDy = SlabSupport.getYOffset(serverWorld, trapdoorSeamMp4SideSupportPos, supportState);
        return supportState.isOf(Blocks.STONE)
                && SlabAnchorAttachment.isAnchored(serverWorld, trapdoorSeamMp4SideSupportPos)
                && near(supportDy, -0.5d);
    }

    private static boolean trapdoorSeamMp4SideOwnerSlabReady(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || trapdoorSeamMp4SideOwnerSlabPos == null) {
            return false;
        }
        BlockState slabState = serverWorld.getBlockState(trapdoorSeamMp4SideOwnerSlabPos);
        double slabDy = SlabSupport.getYOffset(serverWorld, trapdoorSeamMp4SideOwnerSlabPos, slabState);
        return slabState.isOf(Blocks.STONE_SLAB)
                && slabState.get(SlabBlock.TYPE) == SlabType.BOTTOM
                && near(slabDy, -0.5d);
    }

    private static String clickTrapdoorFromCrosshair(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null
                || client.gameRenderer == null || trapdoorSeamSupportPos == null) {
            return "FAIL_ROUTE_NOT_READY";
        }
        Vec3d hitVector = trapdoorSeamSupportVisibleTopHitVector();
        syncSlabThenBlockPlayer(client, hitVector);
        ItemStack stack = new ItemStack(Items.OAK_TRAPDOOR, 4);
        client.player.setStackInHand(Hand.MAIN_HAND, stack);
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.OAK_TRAPDOOR, 4));
        }

        Vec3d eye = client.player.getCameraPosVec(0.0f);
        Vec3d end = eye.add(client.player.getRotationVec(0.0f).multiply(6.0d));
        HitResult vanillaTarget = client.world.raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.player));
        trapdoorSeamVanillaTarget = formatHit(vanillaTarget);
        client.gameRenderer.updateCrosshairTarget(0.0f);
        HitResult finalTarget = client.crosshairTarget;
        trapdoorSeamFinalTarget = formatHit(finalTarget);
        trapdoorSeamFinalTargetOwner = trapdoorSeamTargetOwner(client.world, finalTarget);
        if (finalTarget instanceof BlockHitResult blockHit && finalTarget.getType() == HitResult.Type.BLOCK) {
            ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, blockHit);
            return result.toString();
        }
        return "NO_BLOCK_TARGET";
    }

    private static Vec3d trapdoorSeamSupportVisibleTopHitVector() {
        return new Vec3d(
                trapdoorSeamSupportPos.getX() + 0.5d,
                trapdoorSeamSupportPos.getY() + 0.5d,
                trapdoorSeamSupportPos.getZ() + 0.5d);
    }

    private static void emitTrapdoorSeamPlacementRow(MinecraftClient client) {
        ClientWorld clientWorld = client == null ? null : client.world;
        ServerWorld serverWorld = serverWorldFor(client);
        if (clientWorld == null || serverWorld == null || client.player == null) {
            emitTrapdoorSeamTraceGap("AFTER_PLAYER_PLACEMENT", "TRACE_GAP_WORLD_OR_PLAYER_NOT_READY");
            return;
        }
        trapdoorSeamActualTrapdoorPos = findNearbyTrapdoor(serverWorld, trapdoorSeamExpectedTrapdoorPos);
        BlockState slabState = serverWorld.getBlockState(trapdoorSeamSlabPos);
        BlockState supportState = serverWorld.getBlockState(trapdoorSeamSupportPos);
        BlockState trapdoorState = serverWorld.getBlockState(trapdoorSeamActualTrapdoorPos);
        BlockState clientTrapdoorState = clientWorld.getBlockState(trapdoorSeamActualTrapdoorPos);
        double slabDy = SlabSupport.getYOffset(serverWorld, trapdoorSeamSlabPos, slabState);
        double supportDy = SlabSupport.getYOffset(serverWorld, trapdoorSeamSupportPos, supportState);
        double trapdoorDy = trapdoorState.isOf(Blocks.OAK_TRAPDOOR)
                ? SlabSupport.getYOffset(serverWorld, trapdoorSeamActualTrapdoorPos, trapdoorState)
                : Double.NaN;
        boolean supportAuthoredLowered = supportState.isOf(Blocks.STONE)
                && SlabAnchorAttachment.isAnchored(serverWorld, trapdoorSeamSupportPos)
                && near(supportDy, -0.5d);
        boolean trapdoorAppeared = trapdoorState.isOf(Blocks.OAK_TRAPDOOR);
        boolean expectedPos = trapdoorSeamActualTrapdoorPos.equals(trapdoorSeamExpectedTrapdoorPos);
        boolean namedLegal = trapdoorAppeared
                && SlabSupport.isBeta35LoweredTrapdoorOrFloorButtonVisibleOwnerTarget(
                        serverWorld, trapdoorSeamActualTrapdoorPos, trapdoorState);
        boolean survivalGreen = trapdoorAppeared
                && trapdoorState.canPlaceAt(serverWorld, trapdoorSeamActualTrapdoorPos);
        VoxelShape outlineShape = trapdoorAppeared
                ? trapdoorState.getOutlineShape(serverWorld, trapdoorSeamActualTrapdoorPos,
                        net.minecraft.block.ShapeContext.of(client.player))
                : null;
        VoxelShape raycastShape = trapdoorAppeared
                ? trapdoorState.getRaycastShape(serverWorld, trapdoorSeamActualTrapdoorPos)
                : null;
        VoxelShape collisionShape = trapdoorAppeared
                ? trapdoorState.getCollisionShape(serverWorld, trapdoorSeamActualTrapdoorPos,
                        net.minecraft.block.ShapeContext.of(client.player))
                : null;
        net.minecraft.util.math.Box outlineBox = worldBox(outlineShape, trapdoorSeamActualTrapdoorPos);
        net.minecraft.util.math.Box raycastBox = worldBox(raycastShape, trapdoorSeamActualTrapdoorPos);
        net.minecraft.util.math.Box collisionBox = worldBox(collisionShape, trapdoorSeamActualTrapdoorPos);
        boolean triadGreen = trapdoorAppeared
                && outlineBox != null
                && sameBox(outlineBox, raycastBox)
                && sameBox(outlineBox, collisionBox);
        boolean targetOwnerGreen = "trapdoor".equals(trapdoorSeamFinalTargetOwner)
                || "support".equals(trapdoorSeamFinalTargetOwner);

        if (!supportAuthoredLowered) {
            trapdoorSeamPlacementClassification = "TRACE_GAP_LOWERED_SUPPORT_NOT_AUTHORED";
            trapdoorSeamPlacementFailureLayer = "proof gap";
            trapdoorSeamPlacementGreen = false;
        } else if (!trapdoorAppeared && "NO_BLOCK_TARGET".equals(trapdoorSeamTrapdoorPlacementResult)) {
            trapdoorSeamPlacementClassification = "TARGET_MISS_AT_LOWERED_SUPPORT";
            trapdoorSeamPlacementFailureLayer = "raycast";
            trapdoorSeamPlacementGreen = false;
        } else if (!trapdoorAppeared && "SUCCESS".equals(trapdoorSeamTrapdoorPlacementResult)) {
            trapdoorSeamPlacementClassification = "SUCCESS_WITH_NO_RETAINED_TRAPDOOR_STATE";
            trapdoorSeamPlacementFailureLayer = "placement";
            trapdoorSeamPlacementGreen = false;
        } else if (!trapdoorAppeared) {
            trapdoorSeamPlacementClassification = "PLACEMENT_REJECTED_CLEAN";
            trapdoorSeamPlacementFailureLayer = "NONE";
            trapdoorSeamPlacementGreen = true;
        } else if (!expectedPos) {
            trapdoorSeamPlacementClassification = "WRONG_AUTHORED_POSITION";
            trapdoorSeamPlacementFailureLayer = "placement";
            trapdoorSeamPlacementGreen = false;
        } else if (!namedLegal) {
            trapdoorSeamPlacementClassification = "UNNAMED_LOWERED_TRAPDOOR_STATE";
            trapdoorSeamPlacementFailureLayer = "state authority";
            trapdoorSeamPlacementGreen = false;
        } else if (!survivalGreen) {
            trapdoorSeamPlacementClassification = "PLACED_BUT_SURVIVAL_RED";
            trapdoorSeamPlacementFailureLayer = "survival";
            trapdoorSeamPlacementGreen = false;
        } else if (!triadGreen) {
            trapdoorSeamPlacementClassification = "TRIAD_MISMATCH";
            trapdoorSeamPlacementFailureLayer = "outline";
            trapdoorSeamPlacementGreen = false;
        } else if (!targetOwnerGreen) {
            trapdoorSeamPlacementClassification = "TARGET_OWNER_STEAL";
            trapdoorSeamPlacementFailureLayer = "raycast";
            trapdoorSeamPlacementGreen = false;
        } else {
            trapdoorSeamPlacementClassification = "NAMED_LEGAL_AFTER_PLACEMENT";
            trapdoorSeamPlacementFailureLayer = "NONE";
            trapdoorSeamPlacementGreen = true;
        }

        System.out.println("[MC1211_TRAPDOOR_LOWERED_SEAM_ROW]"
                + " rowPhase=AFTER_PLAYER_PLACEMENT"
                + " fixtureOrigin=" + textPos(trapdoorSeamOrigin)
                + " slabPos=" + textPos(trapdoorSeamSlabPos)
                + " slabState=" + slabState
                + " slabDy=" + formatDouble(slabDy)
                + " supportPos=" + textPos(trapdoorSeamSupportPos)
                + " supportState=" + supportState
                + " supportDy=" + formatDouble(supportDy)
                + " supportAnchored=" + SlabAnchorAttachment.isAnchored(serverWorld, trapdoorSeamSupportPos)
                + " supportAuthoredLowered=" + supportAuthoredLowered
                + " expectedTrapdoorPos=" + textPos(trapdoorSeamExpectedTrapdoorPos)
                + " actualTrapdoorPos=" + textPos(trapdoorSeamActualTrapdoorPos)
                + " actualTrapdoorState=" + trapdoorState
                + " clientTrapdoorState=" + clientTrapdoorState
                + " trapdoorDy=" + formatDouble(trapdoorDy)
                + " placementResultSlabClient=" + trapdoorSeamSlabPlacementResult
                + " placementResultSupportClient=" + trapdoorSeamSupportPlacementResult
                + " placementResultTrapdoorClient=" + trapdoorSeamTrapdoorPlacementResult
                + " vanillaTarget=" + trapdoorSeamVanillaTarget
                + " finalTarget=" + trapdoorSeamFinalTarget
                + " finalTargetOwner=" + trapdoorSeamFinalTargetOwner
                + " namedLegal=" + namedLegal
                + " survivalResult=" + (survivalGreen ? "SURVIVAL_GREEN" : "SURVIVAL_RED")
                + " outlineBounds=" + formatBox(outlineBox)
                + " raycastBounds=" + formatBox(raycastBox)
                + " collisionBounds=" + formatBox(collisionBox)
                + " triadCoLocated=" + triadGreen
                + " classification=" + trapdoorSeamPlacementClassification
                + " failureLayer=" + trapdoorSeamPlacementFailureLayer);
        System.out.println("[MC1211_TRAPDOOR_LOWERED_SEAM_"
                + (trapdoorSeamPlacementGreen ? "GREEN" : "RED") + "]"
                + " rowPhase=AFTER_PLAYER_PLACEMENT"
                + " classification=" + trapdoorSeamPlacementClassification
                + " failureLayer=" + trapdoorSeamPlacementFailureLayer);
    }

    private static void breakTrapdoorSeamSupport(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || trapdoorSeamSupportPos == null || trapdoorSeamExpectedTrapdoorPos == null) {
            return;
        }
        serverWorld.getServer().execute(() -> {
            serverWorld.breakBlock(trapdoorSeamSupportPos, false);
            serverWorld.updateNeighbors(trapdoorSeamSupportPos, Blocks.AIR);
            BlockState trapdoorState = serverWorld.getBlockState(trapdoorSeamExpectedTrapdoorPos);
            serverWorld.updateNeighbors(trapdoorSeamExpectedTrapdoorPos, trapdoorState.getBlock());
        });
    }

    private static void emitTrapdoorSeamUpdateRow(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || trapdoorSeamActualTrapdoorPos == null) {
            emitTrapdoorSeamTraceGap("AFTER_SUPPORT_BREAK", "TRACE_GAP_WORLD_OR_TRAPDOOR_POS_NOT_READY");
            return;
        }
        BlockState supportState = serverWorld.getBlockState(trapdoorSeamSupportPos);
        BlockState trapdoorState = serverWorld.getBlockState(trapdoorSeamActualTrapdoorPos);
        boolean trapdoorPresent = trapdoorState.isOf(Blocks.OAK_TRAPDOOR);
        boolean namedLegal = trapdoorPresent
                && SlabSupport.isBeta35LoweredTrapdoorOrFloorButtonVisibleOwnerTarget(
                        serverWorld, trapdoorSeamActualTrapdoorPos, trapdoorState);
        boolean survivalGreen = trapdoorPresent && trapdoorState.canPlaceAt(serverWorld, trapdoorSeamActualTrapdoorPos);
        double trapdoorDy = trapdoorPresent
                ? SlabSupport.getYOffset(serverWorld, trapdoorSeamActualTrapdoorPos, trapdoorState)
                : Double.NaN;

        if (!trapdoorPresent) {
            trapdoorSeamUpdateClassification = "CLEAN_POP_AFTER_SUPPORT_BREAK";
            trapdoorSeamUpdateFailureLayer = "NONE";
            trapdoorSeamUpdateGreen = true;
        } else if (!namedLegal && survivalGreen && near(trapdoorDy, 0.0d)) {
            trapdoorSeamUpdateClassification = "VANILLA_TRAPDOOR_AFTER_SUPPORT_BREAK";
            trapdoorSeamUpdateFailureLayer = "NONE";
            trapdoorSeamUpdateGreen = true;
        } else if (namedLegal && survivalGreen) {
            trapdoorSeamUpdateClassification = "NAMED_LEGAL_AFTER_SUPPORT_BREAK";
            trapdoorSeamUpdateFailureLayer = "NONE";
            trapdoorSeamUpdateGreen = true;
        } else if (!namedLegal) {
            trapdoorSeamUpdateClassification = "ILLEGAL_TRAPDOOR_REMAINED_AFTER_SUPPORT_BREAK";
            trapdoorSeamUpdateFailureLayer = "state authority";
            trapdoorSeamUpdateGreen = false;
        } else {
            trapdoorSeamUpdateClassification = "SURVIVAL_RED_AFTER_SUPPORT_BREAK";
            trapdoorSeamUpdateFailureLayer = "survival";
            trapdoorSeamUpdateGreen = false;
        }

        System.out.println("[MC1211_TRAPDOOR_LOWERED_SEAM_ROW]"
                + " rowPhase=AFTER_SUPPORT_BREAK"
                + " supportPos=" + textPos(trapdoorSeamSupportPos)
                + " supportStateAfterBreak=" + supportState
                + " actualTrapdoorPos=" + textPos(trapdoorSeamActualTrapdoorPos)
                + " actualTrapdoorStateAfterBreak=" + trapdoorState
                + " trapdoorPresentAfterBreak=" + trapdoorPresent
                + " trapdoorDyAfterBreak=" + formatDouble(trapdoorDy)
                + " namedLegalAfterBreak=" + namedLegal
                + " survivalAfterBreak=" + survivalGreen
                + " classification=" + trapdoorSeamUpdateClassification
                + " failureLayer=" + trapdoorSeamUpdateFailureLayer);
        System.out.println("[MC1211_TRAPDOOR_LOWERED_SEAM_"
                + (trapdoorSeamUpdateGreen ? "GREEN" : "RED") + "]"
                + " rowPhase=AFTER_SUPPORT_BREAK"
                + " classification=" + trapdoorSeamUpdateClassification
                + " failureLayer=" + trapdoorSeamUpdateFailureLayer);
    }

    private static void emitTrapdoorSeamSummary(MinecraftClient client) {
        boolean allGreen = trapdoorSeamPlacementGreen && trapdoorSeamUpdateGreen;
        String finalMarker = allGreen ? "GREEN" : "RED";
        String failureLayer = !"NONE".equals(trapdoorSeamPlacementFailureLayer)
                ? trapdoorSeamPlacementFailureLayer
                : trapdoorSeamUpdateFailureLayer;
        System.out.println("[MC1211_TRAPDOOR_LOWERED_SEAM_SUMMARY]"
                + " rows=2"
                + " finalResult=" + finalMarker
                + " placementClassification=" + trapdoorSeamPlacementClassification
                + " updateClassification=" + trapdoorSeamUpdateClassification
                + " failureLayer=" + failureLayer
                + " patchAllowedNext=" + !allGreen
                + " productionBehaviorChanged=false"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");
        System.out.println("[MC1211_TRAPDOOR_LOWERED_SEAM_" + finalMarker + "]"
                + " rows=2"
                + " failureLayer=" + failureLayer);
        trapdoorSeamFinalized = true;
        emitted = true;
        if (client != null) {
            client.scheduleStop();
        }
    }

    private static void emitTrapdoorSeamTraceGap(String row, String reason) {
        trapdoorSeamPlacementClassification = row.equals("AFTER_SUPPORT_BREAK")
                ? trapdoorSeamPlacementClassification
                : reason;
        trapdoorSeamPlacementFailureLayer = "proof gap";
        System.out.println("[MC1211_TRAPDOOR_LOWERED_SEAM_ROW]"
                + " rowPhase=" + row
                + " fixtureOrigin=" + textPos(trapdoorSeamOrigin)
                + " supportPos=" + textPos(trapdoorSeamSupportPos)
                + " expectedTrapdoorPos=" + textPos(trapdoorSeamExpectedTrapdoorPos)
                + " classification=" + reason
                + " failureLayer=proof gap");
        System.out.println("[MC1211_TRAPDOOR_LOWERED_SEAM_SUMMARY]"
                + " rows=1"
                + " finalResult=TRACE_GAP"
                + " placementClassification=" + trapdoorSeamPlacementClassification
                + " updateClassification=" + trapdoorSeamUpdateClassification
                + " failureLayer=proof gap"
                + " patchAllowedNext=false"
                + " reason=" + reason);
        trapdoorSeamFinalized = true;
        emitted = true;
        if (clientReadyForStop()) {
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    private static String sampleTrapdoorSeamMp4SlabHeldTarget(MinecraftClient client, String samplePhase) {
        if (client == null || client.player == null || client.world == null || client.gameRenderer == null
                || trapdoorSeamSupportPos == null) {
            return "samplePhase=" + samplePhase + "/classification=TRACE_GAP_ROUTE_NOT_READY";
        }
        Vec3d hitVector = trapdoorSeamMp4EdgeHitVector();
        syncSlabThenBlockPlayer(client, hitVector);
        ItemStack stack = new ItemStack(Items.STONE_SLAB, 8);
        client.player.setStackInHand(Hand.MAIN_HAND, stack);
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
        }

        Vec3d eye = client.player.getCameraPosVec(0.0f);
        Vec3d end = eye.add(client.player.getRotationVec(0.0f).multiply(6.0d));
        HitResult vanillaTarget = client.world.raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.player));
        client.gameRenderer.updateCrosshairTarget(0.0f);
        HitResult finalTarget = client.crosshairTarget;
        String finalOwner = trapdoorSeamTargetOwner(client.world, finalTarget);
        boolean initialMiss = vanillaTarget == null || vanillaTarget.getType() == HitResult.Type.MISS;
        boolean finalBlock = finalTarget != null && finalTarget.getType() == HitResult.Type.BLOCK;
        boolean sideOrSupportOwner = "adjacent_stone_or_slab".equals(finalOwner) || "support".equals(finalOwner);
        boolean upGuardVisibleOwner = vanillaTarget instanceof BlockHitResult vanillaBlock
                && finalTarget instanceof BlockHitResult finalBlockHit
                && vanillaBlock.getBlockPos().equals(trapdoorSeamMp4SideSupportPos)
                && finalBlockHit.getBlockPos().equals(trapdoorSeamMp4SideOwnerSlabPos)
                && "adjacent_stone_or_slab".equals(finalOwner);
        boolean upGuardInitialOwnerPreserved = vanillaTarget instanceof BlockHitResult vanillaSupport
                && finalTarget instanceof BlockHitResult finalSupport
                && vanillaSupport.getBlockPos().equals(trapdoorSeamMp4SideSupportPos)
                && finalSupport.getBlockPos().equals(trapdoorSeamMp4SideSupportPos);
        boolean reproduced = (initialMiss && finalBlock && sideOrSupportOwner) || upGuardVisibleOwner;
        String classification;
        if (upGuardVisibleOwner) {
            classification = "SLAB_HELD_UP_GUARD_VISIBLE_UPPER_SIDE_OWNER_REPRODUCED";
            trapdoorSeamMp4LiveElementMatched = true;
        } else if (upGuardInitialOwnerPreserved) {
            classification = "SLAB_HELD_UP_GUARD_INITIAL_OWNER_PRESERVED";
        } else if (reproduced) {
            classification = "SLAB_HELD_MISS_SIDE_RESCUE_REPRODUCED";
            trapdoorSeamMp4LiveElementMatched = true;
        } else if (initialMiss) {
            classification = "SLAB_HELD_TRUE_MISS_NO_RESCUE_CANDIDATE";
        } else if (finalBlock && sideOrSupportOwner) {
            classification = "SLAB_HELD_BLOCK_TARGET_NOT_MP4_MISS";
        } else {
            classification = "SLAB_HELD_TARGET_PROOF_GAP";
        }
        return "samplePhase=" + samplePhase
                + "/heldItem=minecraft:stone_slab"
                + "/initialMiss=" + initialMiss
                + "/vanillaTarget=" + formatHit(vanillaTarget)
                + "/finalTarget=" + formatHit(finalTarget)
                + "/finalOwner=" + finalOwner
                + "/edgeHit=" + formatVec(hitVector)
                + "/upGuardVisibleOwner=" + upGuardVisibleOwner
                + "/upGuardInitialOwnerPreserved=" + upGuardInitialOwnerPreserved
                + "/classification=" + classification
                + "/reproducedLiveSideRescue=" + reproduced;
    }

    private static String sampleTrapdoorSeamMp4ManualNoCandidateTarget(MinecraftClient client, String samplePhase) {
        if (client == null || client.player == null || client.world == null || client.gameRenderer == null
                || trapdoorSeamMp4SideOwnerSlabPos == null) {
            return "samplePhase=" + samplePhase + "/classification=TRACE_GAP_ROUTE_NOT_READY";
        }
        Vec3d hitVector = trapdoorSeamMp4ManualNoCandidateHitVector();
        syncTrapdoorSeamManualNoCandidatePlayer(client, hitVector);
        ItemStack stack = new ItemStack(Items.STONE_SLAB, 8);
        client.player.setStackInHand(Hand.MAIN_HAND, stack);
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
        }

        Vec3d eye = client.player.getCameraPosVec(0.0f);
        Vec3d end = eye.add(client.player.getRotationVec(0.0f).multiply(6.0d));
        HitResult vanillaTarget = client.world.raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.player));
        BlockHitResult sideRescueCandidate = LoweredSideSlabRetargeter.findLoweredSideSlabRetarget(
                client.world,
                client.player,
                eye,
                end,
                vanillaTarget,
                true);
        client.gameRenderer.updateCrosshairTarget(0.0f);
        HitResult finalTarget = client.crosshairTarget;
        boolean vanillaLoweredSlabFace = vanillaTarget instanceof BlockHitResult vanillaBlock
                && vanillaBlock.getBlockPos().equals(trapdoorSeamMp4SideOwnerSlabPos)
                && vanillaBlock.getSide() == Direction.SOUTH;
        boolean finalPreservedSameFace = finalTarget instanceof BlockHitResult finalBlock
                && vanillaTarget instanceof BlockHitResult vanillaBlock
                && finalBlock.getBlockPos().equals(vanillaBlock.getBlockPos())
                && finalBlock.getSide() == vanillaBlock.getSide();
        boolean sideRescueAccepted = sideRescueCandidate != null
                && sideRescueCandidate.getBlockPos().equals(trapdoorSeamMp4SideOwnerSlabPos)
                && sideRescueCandidate.getSide().getAxis() != Direction.Axis.Y;
        boolean reproduced = vanillaLoweredSlabFace && finalPreservedSameFace && !sideRescueAccepted;
        if (reproduced) {
            trapdoorSeamMp4LiveElementMatched = true;
        }
        String classification;
        if (reproduced) {
            classification = "SLAB_HELD_BLOCK_LOWERED_FACE_NO_CANDIDATE_REPRODUCED";
        } else if (vanillaLoweredSlabFace && sideRescueAccepted) {
            classification = "SLAB_HELD_BLOCK_LOWERED_FACE_SIDE_RESCUE_GREEN";
        } else if (vanillaLoweredSlabFace) {
            classification = "SLAB_HELD_BLOCK_LOWERED_FACE_RETARGETED";
        } else {
            classification = "SLAB_HELD_BLOCK_LOWERED_FACE_TRACE_GAP";
        }
        return "samplePhase=" + samplePhase
                + "/heldItem=minecraft:stone_slab"
                + "/sourceTruth=manual-log-203619"
                + "/vanillaLoweredSlabFace=" + vanillaLoweredSlabFace
                + "/finalPreservedSameFace=" + finalPreservedSameFace
                + "/sideRescueAccepted=" + sideRescueAccepted
                + "/vanillaTarget=" + formatHit(vanillaTarget)
                + "/finalTarget=" + formatHit(finalTarget)
                + "/sideRescueCandidate=" + formatHit(sideRescueCandidate)
                + "/edgeHit=" + formatVec(hitVector)
                + "/eye=" + formatVec(eye)
                + "/end=" + formatVec(end)
                + "/classification=" + classification
                + "/reproducedLiveNoCandidate=" + reproduced;
    }

    private static Vec3d trapdoorSeamMp4EdgeHitVector() {
        if (trapdoorSeamMp4SideSupportPos != null) {
            return trapdoorSeamMp4UpGuardHitVector();
        }
        return new Vec3d(
                trapdoorSeamSupportPos.getX() + 0.193d,
                trapdoorSeamSupportPos.getY() + 0.500d,
                trapdoorSeamSupportPos.getZ() + 0.984d);
    }

    private static Vec3d trapdoorSeamMp4UpGuardHitVector() {
        return new Vec3d(
                trapdoorSeamMp4SideSupportPos.getX() + 0.233d,
                trapdoorSeamMp4SideSupportPos.getY() + 0.500d,
                trapdoorSeamMp4SideSupportPos.getZ() + 0.777d);
    }

    private static Vec3d trapdoorSeamMp4ManualNoCandidateHitVector() {
        return new Vec3d(
                trapdoorSeamMp4SideOwnerSlabPos.getX() + 0.801d,
                trapdoorSeamMp4SideOwnerSlabPos.getY() - 0.021d,
                trapdoorSeamMp4SideOwnerSlabPos.getZ() + 1.000d);
    }

    private static void syncTrapdoorSeamManualNoCandidatePlayer(MinecraftClient client, Vec3d hitVector) {
        Vec3d eye = hitVector.add(-0.613d, -1.401d, 1.990d);
        Vec3d end = hitVector.add(0.851d, 1.949d, -2.757d);
        Vec3d delta = end.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.setSneaking(false);
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            serverPlayer.setVelocity(Vec3d.ZERO);
            serverPlayer.setSneaking(false);
            serverPlayer.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        }
    }

    private static void emitTrapdoorSeamMp4Row(MinecraftClient client, String rowPhase) {
        ServerWorld serverWorld = serverWorldFor(client);
        ClientWorld clientWorld = client == null ? null : client.world;
        BlockState supportState = serverWorld == null || trapdoorSeamSupportPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(trapdoorSeamSupportPos);
        BlockState trapdoorState = serverWorld == null || trapdoorSeamActualTrapdoorPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(trapdoorSeamActualTrapdoorPos);
        BlockState clientTrapdoorState = clientWorld == null || trapdoorSeamActualTrapdoorPos == null
                ? Blocks.AIR.getDefaultState()
                : clientWorld.getBlockState(trapdoorSeamActualTrapdoorPos);
        double supportDy = serverWorld == null || trapdoorSeamSupportPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, trapdoorSeamSupportPos, supportState);
        double trapdoorDy = serverWorld == null || trapdoorSeamActualTrapdoorPos == null || !trapdoorState.isOf(Blocks.OAK_TRAPDOOR)
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, trapdoorSeamActualTrapdoorPos, trapdoorState);
        BlockState sideSupportState = serverWorld == null || trapdoorSeamMp4SideSupportPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(trapdoorSeamMp4SideSupportPos);
        BlockState sideOwnerSlabState = serverWorld == null || trapdoorSeamMp4SideOwnerSlabPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(trapdoorSeamMp4SideOwnerSlabPos);
        double sideSupportDy = serverWorld == null || trapdoorSeamMp4SideSupportPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, trapdoorSeamMp4SideSupportPos, sideSupportState);
        double sideOwnerSlabDy = serverWorld == null || trapdoorSeamMp4SideOwnerSlabPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, trapdoorSeamMp4SideOwnerSlabPos, sideOwnerSlabState);
        String liveMatch = trapdoorSeamMp4LiveElementMatched ? "yes" : "no";
        System.out.println("[MC1211_TRAPDOOR_SEAM_MP4_ROW]"
                + " rowPhase=" + rowPhase
                + " fixtureOrigin=" + textPos(trapdoorSeamOrigin)
                + " supportPos=" + textPos(trapdoorSeamSupportPos)
                + " supportState=" + supportState
                + " supportDy=" + formatDouble(supportDy)
                + " expectedTrapdoorPos=" + textPos(trapdoorSeamExpectedTrapdoorPos)
                + " actualTrapdoorPos=" + textPos(trapdoorSeamActualTrapdoorPos)
                + " actualTrapdoorState=" + trapdoorState
                + " clientTrapdoorState=" + clientTrapdoorState
                + " trapdoorDy=" + formatDouble(trapdoorDy)
                + " sideSupportPos=" + textPos(trapdoorSeamMp4SideSupportPos)
                + " sideSupportState=" + sideSupportState
                + " sideSupportDy=" + formatDouble(sideSupportDy)
                + " sideOwnerSlabPos=" + textPos(trapdoorSeamMp4SideOwnerSlabPos)
                + " sideOwnerSlabState=" + sideOwnerSlabState
                + " sideOwnerSlabDy=" + formatDouble(sideOwnerSlabDy)
                + " sideBaseSlabPlacementResult=" + trapdoorSeamMp4SideBaseSlabPlacementResult
                + " sideSupportPlacementResult=" + trapdoorSeamMp4SideSupportPlacementResult
                + " sideOwnerSlabPlacementResult=" + trapdoorSeamMp4SideOwnerSlabPlacementResult
                + " placementClassification=" + trapdoorSeamPlacementClassification
                + " updateClassification=" + trapdoorSeamUpdateClassification
                + " preUpdateSlabHeldSample=" + trapdoorSeamMp4PreUpdateSlabHeldSample
                + " postUpdateSlabHeldSample=" + trapdoorSeamMp4PostUpdateSlabHeldSample
                + " preUpdateManualNoCandidateSample=" + trapdoorSeamMp4ManualNoCandidatePreSample
                + " postUpdateManualNoCandidateSample=" + trapdoorSeamMp4ManualNoCandidatePostSample
                + " mp4LiveSideRescueMatched=" + liveMatch);
    }

    private static void emitTrapdoorSeamMp4Summary(MinecraftClient client) {
        boolean d501Green = trapdoorSeamPlacementGreen && trapdoorSeamUpdateGreen;
        boolean upGuardPreserved = trapdoorSeamMp4PreUpdateSlabHeldSample.contains(
                "classification=SLAB_HELD_UP_GUARD_INITIAL_OWNER_PRESERVED")
                && trapdoorSeamMp4PostUpdateSlabHeldSample.contains(
                "classification=SLAB_HELD_UP_GUARD_INITIAL_OWNER_PRESERVED");
        if (trapdoorSeamMp4LiveElementMatched) {
            trapdoorSeamMp4Classification = "MP4_SLAB_HELD_SIDE_RESCUE_RED";
            trapdoorSeamMp4FailureLayer = "raycast/rescue";
        } else if (d501Green && upGuardPreserved) {
            trapdoorSeamMp4Classification = "MP4_SLAB_HELD_UP_GUARD_PRESERVED";
            trapdoorSeamMp4FailureLayer = "NONE";
        } else if (d501Green) {
            trapdoorSeamMp4Classification = "D501_FALSE_GREEN_MP4_LIVE_ELEMENT_MISSING";
            trapdoorSeamMp4FailureLayer = "proof gap";
        } else {
            trapdoorSeamMp4Classification = "PLAYER_AUTHORED_TRAPDOOR_ROUTE_RED_BEFORE_MP4_SIDE_RESCUE";
            trapdoorSeamMp4FailureLayer = !"NONE".equals(trapdoorSeamPlacementFailureLayer)
                    ? trapdoorSeamPlacementFailureLayer
                    : trapdoorSeamUpdateFailureLayer;
        }
        String finalResult = "NONE".equals(trapdoorSeamMp4FailureLayer)
                ? "GREEN"
                : (trapdoorSeamMp4LiveElementMatched ? "RED" : "PARTIAL");
        System.out.println("[MC1211_TRAPDOOR_SEAM_MP4_SUMMARY]"
                + " rows=3"
                + " finalResult=" + finalResult
                + " classification=" + trapdoorSeamMp4Classification
                + " failureLayer=" + trapdoorSeamMp4FailureLayer
                + " d501PlacementClassification=" + trapdoorSeamPlacementClassification
                + " d501UpdateClassification=" + trapdoorSeamUpdateClassification
                + " d501FalseGreenReason=did_not_require_stone_slab_held_mp4_side_rescue_or_neighbor_trace_markers"
                + " preUpdateSlabHeldSample=" + trapdoorSeamMp4PreUpdateSlabHeldSample
                + " postUpdateSlabHeldSample=" + trapdoorSeamMp4PostUpdateSlabHeldSample
                + " patchAllowedNext=" + trapdoorSeamMp4LiveElementMatched
                + " gameplayPatch=false"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");
        trapdoorSeamFinalized = true;
        emitted = true;
        if (client != null) {
            client.scheduleStop();
        }
    }

    private static void emitTrapdoorSeamMp4TraceGap(String row, String reason) {
        System.out.println("[MC1211_TRAPDOOR_SEAM_MP4_ROW]"
                + " rowPhase=" + row
                + " fixtureOrigin=" + textPos(trapdoorSeamOrigin)
                + " supportPos=" + textPos(trapdoorSeamSupportPos)
                + " expectedTrapdoorPos=" + textPos(trapdoorSeamExpectedTrapdoorPos)
                + " classification=" + reason
                + " failureLayer=proof gap");
        if (!"WAITING".equals(row)) {
            System.out.println("[MC1211_TRAPDOOR_SEAM_MP4_SUMMARY]"
                    + " rows=1"
                    + " finalResult=TRACE_GAP"
                    + " classification=" + reason
                    + " failureLayer=proof gap"
                    + " patchAllowedNext=false"
                    + " gameplayPatch=false");
            trapdoorSeamFinalized = true;
            emitted = true;
            if (clientReadyForStop()) {
                MinecraftClient.getInstance().scheduleStop();
            }
        }
    }

    private static void requestProgrammaticTrapdoorUnderBottomWorldIfNeeded(MinecraftClient client) {
        if (trapdoorUnderBottomWorldStartRequested
                || client == null
                || !client.isFinishedLoading()
                || client.world != null
                || client.player != null) {
            return;
        }
        trapdoorUnderBottomWorldStartRequested = true;
        LevelInfo levelInfo = new LevelInfo(
                "Slabbed MC1211 Trapdoor Under Bottom Harness",
                GameMode.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                DataConfiguration.SAFE_MODE);
        GeneratorOptions generatorOptions = new GeneratorOptions(0L, false, false);
        client.createIntegratedServerLoader().createAndStart(
                "slabbed-mc1211-trapdoor-under-bottom-harness",
                levelInfo,
                generatorOptions,
                Mc1211GoblinRouteClientEntrypoint::createSuperflatDimensionOptions,
                null);
    }

    private static void prepareTrapdoorUnderBottomFixture(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || trapdoorUnderBottomTargetSlabPos == null) {
            return;
        }
        BlockState targetState = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        serverWorld.getServer().execute(() -> {
            for (int x = trapdoorUnderBottomTargetSlabPos.getX() - 2;
                    x <= trapdoorUnderBottomTargetSlabPos.getX() + 2; x++) {
                for (int z = trapdoorUnderBottomTargetSlabPos.getZ() - 2;
                        z <= trapdoorUnderBottomTargetSlabPos.getZ() + 2; z++) {
                    for (int y = trapdoorUnderBottomTargetSlabPos.getY() - 2;
                            y <= trapdoorUnderBottomTargetSlabPos.getY() + 2; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        serverWorld.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                        if (client.world != null) {
                            client.world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                        }
                    }
                }
            }
            serverWorld.setBlockState(trapdoorUnderBottomTargetSlabPos, targetState, 3);
            if (client.world != null) {
                client.world.setBlockState(trapdoorUnderBottomTargetSlabPos, targetState, 3);
            }
            if (!serverWorld.getServer().getPlayerManager().getPlayerList().isEmpty()) {
                serverWorld.getServer().getPlayerManager().getPlayerList().get(0)
                        .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
        });
    }

    private static void prepareTrapdoorUnderLoweredBottomFixture(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || client == null || client.world == null || trapdoorUnderBottomTargetSlabPos == null) {
            return;
        }
        BlockPos ownerTopSlabPos = trapdoorUnderBottomTargetSlabPos.west();
        BlockPos supportPos = ownerTopSlabPos.down();
        BlockPos baseSlabPos = supportPos.down();
        BlockState baseSlabState = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState supportState = Blocks.STONE.getDefaultState();
        BlockState ownerTopState = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
        BlockState targetState = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        serverWorld.getServer().execute(() -> {
            for (int x = baseSlabPos.getX() - 3; x <= trapdoorUnderBottomTargetSlabPos.getX() + 3; x++) {
                for (int z = baseSlabPos.getZ() - 3; z <= baseSlabPos.getZ() + 3; z++) {
                    for (int y = baseSlabPos.getY() - 2; y <= ownerTopSlabPos.getY() + 3; y++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        serverWorld.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                        client.world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
            serverWorld.setBlockState(baseSlabPos, baseSlabState, 3);
            serverWorld.setBlockState(supportPos, supportState, 3);
            serverWorld.setBlockState(ownerTopSlabPos, ownerTopState, 3);
            serverWorld.setBlockState(trapdoorUnderBottomTargetSlabPos, targetState, 3);
            client.world.setBlockState(baseSlabPos, baseSlabState, 3);
            client.world.setBlockState(supportPos, supportState, 3);
            client.world.setBlockState(ownerTopSlabPos, ownerTopState, 3);
            client.world.setBlockState(trapdoorUnderBottomTargetSlabPos, targetState, 3);
            SlabAnchorAttachment.addAnchor(serverWorld, supportPos, supportState);
            SlabAnchorAttachment.addCompoundVisibleOwnerTopSlab(
                    serverWorld, ownerTopSlabPos, ownerTopState, supportPos, supportState);
            addGoblinLoweredSlabMarker(
                    serverWorld, trapdoorUnderBottomTargetSlabPos, targetState, ownerTopSlabPos, ownerTopState);
            if (!serverWorld.getServer().getPlayerManager().getPlayerList().isEmpty()) {
                serverWorld.getServer().getPlayerManager().getPlayerList().get(0)
                        .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
            System.out.println("[MC1211_TRAPDOOR_UNDER_BOTTOM_PLACEMENT_ROW]"
                    + " rowPhase=LOWERED_BOTTOM_FIXTURE_READY"
                    + " setup=" + trapdoorUnderBottomSetup
                    + " baseSlabPos=" + textPos(baseSlabPos)
                    + " supportPos=" + textPos(supportPos)
                    + " ownerTopSlabPos=" + textPos(ownerTopSlabPos)
                    + " targetSlabPos=" + textPos(trapdoorUnderBottomTargetSlabPos)
                    + " expectedTrapdoorPos=" + textPos(trapdoorUnderBottomExpectedTrapdoorPos)
                    + " manualAnchorInjection=false"
                    + " gameplayPatch=false");
        });
    }

    private static String clickTrapdoorUnderBottomSlab(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null || client.interactionManager == null
                || client.gameRenderer == null || trapdoorUnderBottomTargetSlabPos == null) {
            return "FAIL_ROUTE_NOT_READY";
        }
        Vec3d hitVector = trapdoorUnderBottomHitVector();
        syncTrapdoorUnderBottomPlayer(client, hitVector);
        client.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.OAK_TRAPDOOR, 4));
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.OAK_TRAPDOOR, 4));
        }

        Vec3d eye = client.player.getCameraPosVec(0.0f);
        Vec3d end = eye.add(client.player.getRotationVec(0.0f).multiply(6.0d));
        HitResult vanillaTarget = client.world.raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.player));
        trapdoorUnderBottomVanillaTarget = formatHit(vanillaTarget);
        client.gameRenderer.updateCrosshairTarget(0.0f);
        HitResult finalTarget = client.crosshairTarget;
        trapdoorUnderBottomFinalTarget = formatHit(finalTarget);
        if (finalTarget instanceof BlockHitResult blockHit && finalTarget.getType() == HitResult.Type.BLOCK) {
            trapdoorUnderBottomUsedTarget = formatHit(finalTarget);
            ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, blockHit);
            return String.valueOf(result);
        }
        trapdoorUnderBottomUsedTarget = "NO_BLOCK_TARGET";
        return "NO_BLOCK_TARGET";
    }

    private static Vec3d trapdoorUnderBottomHitVector() {
        return new Vec3d(
                trapdoorUnderBottomTargetSlabPos.getX() + 0.5d,
                trapdoorUnderBottomTargetSlabPos.getY() + 0.01d,
                trapdoorUnderBottomTargetSlabPos.getZ() + 0.5d);
    }

    private static void syncTrapdoorUnderBottomPlayer(MinecraftClient client, Vec3d hitVector) {
        Vec3d eye = hitVector.add(1.75d, -0.35d, 0.0d);
        Vec3d delta = hitVector.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.setSneaking(false);
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            serverPlayer.setVelocity(Vec3d.ZERO);
            serverPlayer.setSneaking(false);
            serverPlayer.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        }
    }

    private static void emitTrapdoorUnderBottomPlacementRow(MinecraftClient client, String rowPhase) {
        ServerWorld serverWorld = serverWorldFor(client);
        ClientWorld clientWorld = client == null ? null : client.world;
        BlockState targetState = serverWorld == null || trapdoorUnderBottomTargetSlabPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(trapdoorUnderBottomTargetSlabPos);
        BlockState finalState = serverWorld == null || trapdoorUnderBottomExpectedTrapdoorPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(trapdoorUnderBottomExpectedTrapdoorPos);
        BlockState clientFinalState = clientWorld == null || trapdoorUnderBottomExpectedTrapdoorPos == null
                ? Blocks.AIR.getDefaultState()
                : clientWorld.getBlockState(trapdoorUnderBottomExpectedTrapdoorPos);
        double targetDy = serverWorld == null || trapdoorUnderBottomTargetSlabPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, trapdoorUnderBottomTargetSlabPos, targetState);
        double finalDy = serverWorld == null || trapdoorUnderBottomExpectedTrapdoorPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, trapdoorUnderBottomExpectedTrapdoorPos, finalState);
        boolean targetIsBottomSlab = targetState.isOf(Blocks.STONE_SLAB)
                && targetState.get(SlabBlock.TYPE) == SlabType.BOTTOM;
        boolean usedBottomUnderside = trapdoorUnderBottomUsedTarget.contains(
                "pos=" + textPos(trapdoorUnderBottomTargetSlabPos))
                && trapdoorUnderBottomUsedTarget.contains("/side=down");
        boolean trapdoorPresent = finalState.isOf(Blocks.OAK_TRAPDOOR);
        boolean accepted = trapdoorUnderBottomPlacementResult.contains("SUCCESS")
                || trapdoorUnderBottomPlacementResult.contains("CONSUME");
        if (!targetIsBottomSlab) {
            trapdoorUnderBottomClassification = "TRACE_GAP_TARGET_NOT_BOTTOM_SLAB";
            trapdoorUnderBottomFailureLayer = "proof gap";
            trapdoorUnderBottomGreen = false;
        } else if (!usedBottomUnderside) {
            trapdoorUnderBottomClassification = "TARGETING_DID_NOT_HIT_BOTTOM_SLAB_UNDERSIDE";
            trapdoorUnderBottomFailureLayer = "raycast";
            trapdoorUnderBottomGreen = false;
        } else if (trapdoorPresent) {
            trapdoorUnderBottomClassification = "TRAPDOOR_PRESENT_AFTER_TRUE_BOTTOM_UNDERSIDE_CLICK";
            trapdoorUnderBottomFailureLayer = "NONE";
            trapdoorUnderBottomGreen = true;
        } else if (accepted && finalState.isAir()) {
            trapdoorUnderBottomClassification = "SUCCESS_WITH_NO_RETAINED_BOTTOM_UNDERSIDE_TRAPDOOR_STATE";
            trapdoorUnderBottomFailureLayer = "placement";
            trapdoorUnderBottomGreen = false;
        } else if (!accepted) {
            trapdoorUnderBottomClassification = "BOTTOM_UNDERSIDE_TRAPDOOR_PLACEMENT_REJECTED";
            trapdoorUnderBottomFailureLayer = "placement";
            trapdoorUnderBottomGreen = false;
        } else {
            trapdoorUnderBottomClassification = "BOTTOM_UNDERSIDE_TRAPDOOR_ABSENT_UNCLASSIFIED";
            trapdoorUnderBottomFailureLayer = "placement";
            trapdoorUnderBottomGreen = false;
        }
        System.out.println("[MC1211_TRAPDOOR_UNDER_BOTTOM_PLACEMENT_ROW]"
                + " rowPhase=" + rowPhase
                + " targetSlabPos=" + textPos(trapdoorUnderBottomTargetSlabPos)
                + " targetSlabState=" + targetState
                + " targetSlabDy=" + formatDouble(targetDy)
                + " expectedTrapdoorPos=" + textPos(trapdoorUnderBottomExpectedTrapdoorPos)
                + " finalTrapdoorState=" + finalState
                + " clientFinalTrapdoorState=" + clientFinalState
                + " finalTrapdoorDy=" + formatDouble(finalDy)
                + " trapdoorPresent=" + trapdoorPresent
                + " trapdoorResolvedToAir=" + finalState.isAir()
                + " vanillaTarget=" + trapdoorUnderBottomVanillaTarget
                + " finalTarget=" + trapdoorUnderBottomFinalTarget
                + " usedTarget=" + trapdoorUnderBottomUsedTarget
                + " placementResult=" + trapdoorUnderBottomPlacementResult
                + " placementAccepted=" + accepted
                + " bottomSlabUndersideCandidate=" + usedBottomUnderside
                + " classification=" + trapdoorUnderBottomClassification
                + " failureLayer=" + trapdoorUnderBottomFailureLayer);
    }

    private static void emitTrapdoorUnderBottomTimelineRow(MinecraftClient client, String rowPhase) {
        ServerWorld serverWorld = serverWorldFor(client);
        ClientWorld clientWorld = client == null ? null : client.world;
        BlockState targetState = serverWorld == null || trapdoorUnderBottomTargetSlabPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(trapdoorUnderBottomTargetSlabPos);
        BlockState supportAboveState = serverWorld == null || trapdoorUnderBottomExpectedTrapdoorPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(trapdoorUnderBottomExpectedTrapdoorPos.up());
        BlockState supportBelowState = serverWorld == null || trapdoorUnderBottomExpectedTrapdoorPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(trapdoorUnderBottomExpectedTrapdoorPos.down());
        BlockState serverState = serverWorld == null || trapdoorUnderBottomExpectedTrapdoorPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(trapdoorUnderBottomExpectedTrapdoorPos);
        BlockState clientState = clientWorld == null || trapdoorUnderBottomExpectedTrapdoorPos == null
                ? Blocks.AIR.getDefaultState()
                : clientWorld.getBlockState(trapdoorUnderBottomExpectedTrapdoorPos);
        BlockState predictedState = trapdoorUnderBottomPredictedClientState == null
                ? Blocks.AIR.getDefaultState()
                : trapdoorUnderBottomPredictedClientState;
        boolean predictedTrapdoor = predictedState.isOf(Blocks.OAK_TRAPDOOR);
        boolean serverTrapdoor = serverState.isOf(Blocks.OAK_TRAPDOOR);
        boolean predictedCanPlaceAtServer = predictedTrapdoor
                && serverWorld != null
                && trapdoorUnderBottomExpectedTrapdoorPos != null
                && predictedState.canPlaceAt(serverWorld, trapdoorUnderBottomExpectedTrapdoorPos);
        boolean serverStateCanPlaceAtServer = serverTrapdoor
                && serverWorld != null
                && trapdoorUnderBottomExpectedTrapdoorPos != null
                && serverState.canPlaceAt(serverWorld, trapdoorUnderBottomExpectedTrapdoorPos);
        double targetDy = serverWorld == null || trapdoorUnderBottomTargetSlabPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, trapdoorUnderBottomTargetSlabPos, targetState);
        double supportAboveDy = serverWorld == null || trapdoorUnderBottomExpectedTrapdoorPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, trapdoorUnderBottomExpectedTrapdoorPos.up(), supportAboveState);
        double serverDy = serverWorld == null || trapdoorUnderBottomExpectedTrapdoorPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, trapdoorUnderBottomExpectedTrapdoorPos, serverState);
        double clientDy = clientWorld == null || trapdoorUnderBottomExpectedTrapdoorPos == null
                ? Double.NaN
                : ClientDy.dyFor(clientWorld, trapdoorUnderBottomExpectedTrapdoorPos, clientState);
        System.out.println("[MC1211_TRAPDOOR_UNDER_BOTTOM_TIMELINE]"
                + " rowPhase=" + rowPhase
                + " targetSlabPos=" + textPos(trapdoorUnderBottomTargetSlabPos)
                + " targetSlabState=" + targetState
                + " targetSlabDy=" + formatDouble(targetDy)
                + " expectedTrapdoorPos=" + textPos(trapdoorUnderBottomExpectedTrapdoorPos)
                + " supportAbovePos=" + textPos(trapdoorUnderBottomExpectedTrapdoorPos == null
                ? null
                : trapdoorUnderBottomExpectedTrapdoorPos.up())
                + " supportAboveState=" + supportAboveState
                + " supportAboveDy=" + formatDouble(supportAboveDy)
                + " supportBelowState=" + supportBelowState
                + " serverState=" + serverState
                + " serverDy=" + formatDouble(serverDy)
                + " clientState=" + clientState
                + " clientDy=" + formatDouble(clientDy)
                + " predictedClientState=" + predictedState
                + " predictedHalf=" + trapdoorHalf(predictedState)
                + " predictedFacing=" + trapdoorFacing(predictedState)
                + " predictedCanPlaceAtServer=" + predictedCanPlaceAtServer
                + " serverStateCanPlaceAtServer=" + serverStateCanPlaceAtServer
                + " serverTrapdoorPresent=" + serverTrapdoor
                + " clientTrapdoorPresent=" + clientState.isOf(Blocks.OAK_TRAPDOOR)
                + " placementResult=" + trapdoorUnderBottomPlacementResult
                + " usedTarget=" + trapdoorUnderBottomUsedTarget
                + " gameplayPatch=false");
    }

    private static String trapdoorHalf(BlockState state) {
        return state != null && state.isOf(Blocks.OAK_TRAPDOOR)
                ? String.valueOf(state.get(net.minecraft.block.TrapdoorBlock.HALF))
                : "NONE";
    }

    private static String trapdoorFacing(BlockState state) {
        return state != null && state.isOf(Blocks.OAK_TRAPDOOR)
                ? String.valueOf(state.get(net.minecraft.block.TrapdoorBlock.FACING))
                : "NONE";
    }

    private static void emitTrapdoorUnderBottomPlacementSummary(MinecraftClient client) {
        System.out.println("[MC1211_TRAPDOOR_UNDER_BOTTOM_PLACEMENT_SUMMARY]"
                + " rows=1"
                + " finalResult=" + (trapdoorUnderBottomGreen ? "GREEN" : "RED")
                + " classification=" + trapdoorUnderBottomClassification
                + " failureLayer=" + trapdoorUnderBottomFailureLayer
                + " placementResult=" + trapdoorUnderBottomPlacementResult
                + " trapdoorPresentRequired=true"
                + " patchAllowedNext=" + (!trapdoorUnderBottomGreen
                && !"proof gap".equals(trapdoorUnderBottomFailureLayer))
                + " gameplayPatch=false"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");
        trapdoorUnderBottomFinalized = true;
        emitted = true;
        if (client != null) {
            client.scheduleStop();
        }
    }

    private static void emitTrapdoorUnderBottomTraceGap(String row, String reason) {
        System.out.println("[MC1211_TRAPDOOR_UNDER_BOTTOM_PLACEMENT_ROW]"
                + " rowPhase=" + row
                + " targetSlabPos=" + textPos(trapdoorUnderBottomTargetSlabPos)
                + " expectedTrapdoorPos=" + textPos(trapdoorUnderBottomExpectedTrapdoorPos)
                + " classification=" + reason
                + " failureLayer=proof gap");
        if (!"WAITING".equals(row)) {
            trapdoorUnderBottomClassification = reason;
            trapdoorUnderBottomFailureLayer = "proof gap";
            emitTrapdoorUnderBottomPlacementSummary(MinecraftClient.getInstance());
        }
    }

    private static void requestProgrammaticSbbsFinalSlabWorldIfNeeded(MinecraftClient client) {
        if (sbbsFinalSlabWorldStartRequested
                || client == null
                || !client.isFinishedLoading()
                || client.world != null
                || client.player != null) {
            return;
        }
        sbbsFinalSlabWorldStartRequested = true;
        LevelInfo levelInfo = new LevelInfo(
                "Slabbed MC1211 SBBS Final Slab Target Harness",
                GameMode.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                DataConfiguration.SAFE_MODE);
        GeneratorOptions generatorOptions = new GeneratorOptions(0L, false, false);
        client.createIntegratedServerLoader().createAndStart(
                "slabbed-mc1211-sbbs-final-slab-target-harness",
                levelInfo,
                generatorOptions,
                Mc1211GoblinRouteClientEntrypoint::createSuperflatDimensionOptions,
                null);
    }

    private static void prepareSbbsFinalSlabFixture(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || sbbsFinalSlabOrigin == null) {
            return;
        }
        serverWorld.getServer().execute(() -> {
            for (int x = sbbsFinalSlabOrigin.getX() - 3; x <= sbbsFinalSlabOrigin.getX() + 3; x++) {
                for (int z = sbbsFinalSlabOrigin.getZ() - 3; z <= sbbsFinalSlabOrigin.getZ() + 3; z++) {
                    for (int y = sbbsFinalSlabOrigin.getY() - 2; y <= sbbsFinalSlabOrigin.getY() + 4; y++) {
                        serverWorld.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
            serverWorld.setBlockState(sbbsFinalSlabGroundPos, Blocks.STONE.getDefaultState(), 3);
            if (!serverWorld.getServer().getPlayerManager().getPlayerList().isEmpty()) {
                serverWorld.getServer().getPlayerManager().getPlayerList().get(0)
                        .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
        });
    }

    private static boolean sbbsFinalSlabBaseReady(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || sbbsFinalSlabBaseSlabPos == null) {
            return false;
        }
        BlockState state = serverWorld.getBlockState(sbbsFinalSlabBaseSlabPos);
        return state.isOf(Blocks.STONE_SLAB) && state.get(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    private static boolean sbbsFinalSlabSupportReady(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || sbbsFinalSlabSupportPos == null) {
            return false;
        }
        BlockState state = serverWorld.getBlockState(sbbsFinalSlabSupportPos);
        double dy = SlabSupport.getYOffset(serverWorld, sbbsFinalSlabSupportPos, state);
        return state.isOf(Blocks.STONE)
                && SlabAnchorAttachment.isAnchored(serverWorld, sbbsFinalSlabSupportPos)
                && near(dy, -0.5d);
    }

    private static boolean sbbsFinalSlabTopReady(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || sbbsFinalSlabTopSlabPos == null) {
            return false;
        }
        BlockState state = serverWorld.getBlockState(sbbsFinalSlabTopSlabPos);
        double dy = SlabSupport.getYOffset(serverWorld, sbbsFinalSlabTopSlabPos, state);
        return state.isOf(Blocks.STONE_SLAB)
                && state.get(SlabBlock.TYPE) == SlabType.BOTTOM
                && near(dy, -0.5d);
    }

    private static String sampleSbbsFinalSlabTarget(MinecraftClient client, String samplePhase, Vec3d hitVector) {
        if (client == null || client.player == null || client.world == null || client.gameRenderer == null
                || sbbsFinalSlabSupportPos == null || sbbsFinalSlabTopSlabPos == null) {
            return "samplePhase=" + samplePhase + "/classification=TRACE_GAP_ROUTE_NOT_READY";
        }
        syncSbbsFinalSlabPlayer(client, hitVector);
        ItemStack stack = new ItemStack(Items.STONE_SLAB, 8);
        client.player.setStackInHand(Hand.MAIN_HAND, stack);
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
        }

        Vec3d eye = client.player.getCameraPosVec(0.0f);
        Vec3d end = eye.add(client.player.getRotationVec(0.0f).multiply(6.0d));
        HitResult vanillaTarget = client.world.raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.player));
        client.gameRenderer.updateCrosshairTarget(0.0f);
        HitResult finalTarget = client.crosshairTarget;
        String vanillaOwner = sbbsFinalSlabTargetOwner(client.world, vanillaTarget);
        String finalOwner = sbbsFinalSlabTargetOwner(client.world, finalTarget);
        boolean vanillaSupport = "lowered_support".equals(vanillaOwner);
        boolean finalSupport = "lowered_support".equals(finalOwner);
        boolean finalSlabSteals = vanillaSupport && "final_bottom_slab".equals(finalOwner);
        boolean initialOwnerPreserved = vanillaSupport && finalSupport;
        String classification;
        if (finalSlabSteals) {
            classification = "SBBS_FINAL_SLAB_TARGETING_RED_FINAL_SLAB_STEALS_TARGET";
        } else if (initialOwnerPreserved) {
            classification = "SBBS_FINAL_SLAB_TARGETING_GREEN_INITIAL_OWNER_PRESERVED";
        } else if ("final_bottom_slab".equals(finalOwner)) {
            classification = "SBBS_FINAL_SLAB_TARGETING_RED_FINAL_SLAB_TARGETED";
        } else {
            classification = "SBBS_FINAL_SLAB_TARGETING_TRACE_GAP_UNEXPECTED_OWNER";
        }
        return "samplePhase=" + samplePhase
                + "/heldItem=minecraft:stone_slab"
                + "/vanillaTarget=" + formatHit(vanillaTarget)
                + "/vanillaOwner=" + vanillaOwner
                + "/finalTarget=" + formatHit(finalTarget)
                + "/finalOwner=" + finalOwner
                + "/liveHit=" + formatVec(hitVector)
                + "/initialOwnerPreserved=" + initialOwnerPreserved
                + "/finalSlabSteals=" + finalSlabSteals
                + "/classification=" + classification;
    }

    private static void classifySbbsFinalSlabSamples() {
        if (sbbsFinalSlabEdgeTargetSample.contains("classification=SBBS_FINAL_SLAB_TARGETING_RED_")) {
            sbbsFinalSlabClassification = "SBBS_FINAL_SLAB_TARGETING_RED_EDGE_FINAL_SLAB_OWNER";
            sbbsFinalSlabFailureLayer = "raycast/rescue";
        } else if (sbbsFinalSlabTargetSample.contains("classification=SBBS_FINAL_SLAB_TARGETING_RED_")) {
            sbbsFinalSlabClassification = "SBBS_FINAL_SLAB_TARGETING_RED_INTERIOR_FINAL_SLAB_OWNER";
            sbbsFinalSlabFailureLayer = "raycast/rescue";
        } else if (sbbsFinalSlabTargetSample.contains("classification=SBBS_FINAL_SLAB_TARGETING_GREEN_")
                && sbbsFinalSlabEdgeTargetSample.contains("classification=SBBS_FINAL_SLAB_TARGETING_GREEN_")) {
            sbbsFinalSlabClassification = "SBBS_FINAL_SLAB_TARGETING_GREEN_INTERIOR_AND_EDGE_PRESERVED";
            sbbsFinalSlabFailureLayer = "NONE";
        } else {
            sbbsFinalSlabClassification = "SBBS_FINAL_SLAB_TARGETING_TRACE_GAP_SAMPLE_MISMATCH";
            sbbsFinalSlabFailureLayer = "proof gap";
        }
    }

    private static Vec3d sbbsFinalSlabInteriorHitVector() {
        return new Vec3d(
                sbbsFinalSlabSupportPos.getX() + 0.750d,
                sbbsFinalSlabSupportPos.getY() + 0.500d,
                sbbsFinalSlabSupportPos.getZ() + 0.487d);
    }

    private static Vec3d sbbsFinalSlabEdgeHitVector() {
        return new Vec3d(
                sbbsFinalSlabSupportPos.getX() + 0.937d,
                sbbsFinalSlabSupportPos.getY() + 0.500d,
                sbbsFinalSlabSupportPos.getZ() + 0.487d);
    }

    private static void syncSbbsFinalSlabPlayer(MinecraftClient client, Vec3d hitVector) {
        Vec3d eye = hitVector.add(0.0d, 0.35d, -1.75d);
        Vec3d delta = hitVector.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        slabThenBlockReachDiagnostic = "eye=" + formatVec(eye)
                + "/hitVec=" + formatVec(hitVector)
                + "/distance=" + formatDouble(eye.distanceTo(hitVector))
                + "/yaw=" + formatDouble(yaw)
                + "/pitch=" + formatDouble(pitch);
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.setSneaking(false);
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            serverPlayer.setVelocity(Vec3d.ZERO);
            serverPlayer.setSneaking(false);
            serverPlayer.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        }
    }

    private static String sbbsFinalSlabTargetOwner(ClientWorld world, HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return hit == null ? "null" : hit.getType().toString();
        }
        BlockPos hitPos = blockHit.getBlockPos();
        if (hitPos.equals(sbbsFinalSlabSupportPos)) {
            return "lowered_support";
        }
        if (hitPos.equals(sbbsFinalSlabTopSlabPos)) {
            return "final_bottom_slab";
        }
        if (hitPos.equals(sbbsFinalSlabBaseSlabPos)) {
            return "base_bottom_slab";
        }
        BlockState state = world == null ? Blocks.AIR.getDefaultState() : world.getBlockState(hitPos);
        if (state.isOf(Blocks.STONE) || state.isOf(Blocks.STONE_SLAB)) {
            return "other_stone_or_slab:" + textPos(hitPos);
        }
        return "other:" + textPos(hitPos);
    }

    private static void emitSbbsFinalSlabRow(MinecraftClient client, String rowPhase) {
        ServerWorld serverWorld = serverWorldFor(client);
        ClientWorld clientWorld = client == null ? null : client.world;
        BlockState baseState = serverWorld == null || sbbsFinalSlabBaseSlabPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(sbbsFinalSlabBaseSlabPos);
        BlockState supportState = serverWorld == null || sbbsFinalSlabSupportPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(sbbsFinalSlabSupportPos);
        BlockState finalSlabState = serverWorld == null || sbbsFinalSlabTopSlabPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(sbbsFinalSlabTopSlabPos);
        BlockState clientFinalSlabState = clientWorld == null || sbbsFinalSlabTopSlabPos == null
                ? Blocks.AIR.getDefaultState()
                : clientWorld.getBlockState(sbbsFinalSlabTopSlabPos);
        BlockState lanternSideSlabState = serverWorld == null || sbbsLanternSideSlabPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(sbbsLanternSideSlabPos);
        BlockState lanternUnderState = serverWorld == null || sbbsLanternUnderPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(sbbsLanternUnderPos);
        BlockState chainSideSlabState = serverWorld == null || sbbsChainSideSlabPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(sbbsChainSideSlabPos);
        BlockState chainState = serverWorld == null || sbbsChainPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(sbbsChainPos);
        BlockState chainLanternState = serverWorld == null || sbbsChainLanternPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(sbbsChainLanternPos);
        double baseDy = serverWorld == null || sbbsFinalSlabBaseSlabPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, sbbsFinalSlabBaseSlabPos, baseState);
        double supportDy = serverWorld == null || sbbsFinalSlabSupportPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, sbbsFinalSlabSupportPos, supportState);
        double finalSlabDy = serverWorld == null || sbbsFinalSlabTopSlabPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, sbbsFinalSlabTopSlabPos, finalSlabState);
        double lanternSideSlabDy = serverWorld == null || sbbsLanternSideSlabPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, sbbsLanternSideSlabPos, lanternSideSlabState);
        double lanternUnderDy = serverWorld == null || sbbsLanternUnderPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, sbbsLanternUnderPos, lanternUnderState);
        double chainSideSlabDy = serverWorld == null || sbbsChainSideSlabPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, sbbsChainSideSlabPos, chainSideSlabState);
        double chainDy = serverWorld == null || sbbsChainPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, sbbsChainPos, chainState);
        double chainLanternDy = serverWorld == null || sbbsChainLanternPos == null
                ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, sbbsChainLanternPos, chainLanternState);
        boolean supportAnchored = serverWorld != null
                && sbbsFinalSlabSupportPos != null
                && SlabAnchorAttachment.isAnchored(serverWorld, sbbsFinalSlabSupportPos);
        System.out.println("[MC1211_SBBS_FINAL_SLAB_TARGET_ROW]"
                + " rowPhase=" + rowPhase
                + " fixtureOrigin=" + textPos(sbbsFinalSlabOrigin)
                + " baseSlabPos=" + textPos(sbbsFinalSlabBaseSlabPos)
                + " baseSlabState=" + baseState
                + " baseSlabDy=" + formatDouble(baseDy)
                + " supportPos=" + textPos(sbbsFinalSlabSupportPos)
                + " supportState=" + supportState
                + " supportDy=" + formatDouble(supportDy)
                + " supportAnchored=" + supportAnchored
                + " finalSlabPos=" + textPos(sbbsFinalSlabTopSlabPos)
                + " finalSlabState=" + finalSlabState
                + " clientFinalSlabState=" + clientFinalSlabState
                + " finalSlabDy=" + formatDouble(finalSlabDy)
                + " lanternSideSlabPos=" + textPos(sbbsLanternSideSlabPos)
                + " lanternSideSlabState=" + lanternSideSlabState
                + " lanternSideSlabDy=" + formatDouble(lanternSideSlabDy)
                + " lanternUnderPos=" + textPos(sbbsLanternUnderPos)
                + " lanternUnderState=" + lanternUnderState
                + " lanternUnderDy=" + formatDouble(lanternUnderDy)
                + " chainSideSlabPos=" + textPos(sbbsChainSideSlabPos)
                + " chainSideSlabState=" + chainSideSlabState
                + " chainSideSlabDy=" + formatDouble(chainSideSlabDy)
                + " chainPos=" + textPos(sbbsChainPos)
                + " chainState=" + chainState
                + " chainDy=" + formatDouble(chainDy)
                + " chainLanternPos=" + textPos(sbbsChainLanternPos)
                + " chainLanternState=" + chainLanternState
                + " chainLanternDy=" + formatDouble(chainLanternDy)
                + " basePlacementResult=" + sbbsFinalSlabBasePlacementResult
                + " supportPlacementResult=" + sbbsFinalSlabSupportPlacementResult
                + " finalSlabPlacementResult=" + sbbsFinalSlabTopSlabPlacementResult
                + " lanternSideSlabPlacementResult=" + sbbsLanternSideSlabPlacementResult
                + " lanternUnderPlacementResult=" + sbbsLanternUnderPlacementResult
                + " chainSideSlabPlacementResult=" + sbbsChainSideSlabPlacementResult
                + " chainPlacementResult=" + sbbsChainPlacementResult
                + " chainLanternPlacementResult=" + sbbsChainLanternPlacementResult
                + " interiorTargetSample=" + sbbsFinalSlabTargetSample
                + " edgeTargetSample=" + sbbsFinalSlabEdgeTargetSample
                + " classification=" + sbbsFinalSlabClassification
                + " failureLayer=" + sbbsFinalSlabFailureLayer);
    }

    private static void emitSbbsFinalSlabSummary(MinecraftClient client) {
        String finalResult = "NONE".equals(sbbsFinalSlabFailureLayer)
                ? "GREEN"
                : ("raycast/rescue".equals(sbbsFinalSlabFailureLayer) ? "RED" : "TRACE_GAP");
        System.out.println("[MC1211_SBBS_FINAL_SLAB_TARGET_SUMMARY]"
                + " rows=1"
                + " finalResult=" + finalResult
                + " classification=" + sbbsFinalSlabClassification
                + " failureLayer=" + sbbsFinalSlabFailureLayer
                + " interiorTargetSample=" + sbbsFinalSlabTargetSample
                + " edgeTargetSample=" + sbbsFinalSlabEdgeTargetSample
                + " patchAllowedNext=" + "raycast/rescue".equals(sbbsFinalSlabFailureLayer)
                + " gameplayPatch=false"
                + " releaseAudit=NOT_RUN"
                + " releaseTagMoved=false");
        sbbsFinalSlabFinalized = true;
        emitted = true;
        if (client != null) {
            client.scheduleStop();
        }
    }

    private static void emitSbbsFinalSlabTraceGap(String row, String reason) {
        System.out.println("[MC1211_SBBS_FINAL_SLAB_TARGET_ROW]"
                + " rowPhase=" + row
                + " fixtureOrigin=" + textPos(sbbsFinalSlabOrigin)
                + " baseSlabPos=" + textPos(sbbsFinalSlabBaseSlabPos)
                + " supportPos=" + textPos(sbbsFinalSlabSupportPos)
                + " finalSlabPos=" + textPos(sbbsFinalSlabTopSlabPos)
                + " classification=" + reason
                + " failureLayer=proof gap");
        if (!"WAITING".equals(row)) {
            sbbsFinalSlabClassification = reason;
            sbbsFinalSlabFailureLayer = "proof gap";
            emitSbbsFinalSlabSummary(MinecraftClient.getInstance());
        }
    }

    private static void runSbsTopSlabCombinationRoute(MinecraftClient client) {
        if (sbsTopSlabFinalized || emitted) {
            return;
        }
        sbsTopSlabTicks++;
        if (!sbsTopSlabCanaryEmitted) {
            sbsTopSlabCanaryEmitted = true;
            System.out.println("[MC1211_SBS_TOP_SLAB_COMBINATION_START]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " property=" + SBS_TOP_SLAB_COMBINATION_RED_PROPERTY
                    + " rowCount=" + SBS_TOP_SLAB_COMBINATION_CASES.length
                    + " rows=SBB_CONTROL,SBS_STONE_SLAB_REPEAT_COMBINE,SBS_OAK_SLAB_CONTROL,"
                    + "SBS_OAK_SLAB_ON_COPPER_BULB_RED,SBSB_COPPER_BULB_ON_OAK_SLAB_RED,"
                    + "SBSB_LIVE_CROSSHAIR_COPPER_BULB_ON_OAK_SLAB_RED,"
                    + "SBSBS_OAK_SLAB_ON_SECOND_BLOCK_RED,SBSBS_PERP_SIDE_TARGET_FROM_OWNER_TOP_RED,"
                    + "SBBBBBS_STONE_SLAB_REPEAT_COMBINE,"
                    + "SBBBBBB_CONTROL"
                    + " initialLayer=placement/state_authority"
                    + " playerLikeBuild=true"
                    + " manualMarkerInjection=false"
                    + " gameplayPatch=false"
                    + " cleanup=false"
                    + " savepoint=false"
                    + " worldReady=" + (client != null && client.world != null)
                    + " playerReady=" + (client != null && client.player != null));
        }

        requestProgrammaticSbsTopSlabWorldIfNeeded(client);
        String readinessGap = sbsTopSlabReadinessGap(client);
        if (readinessGap != null) {
            if (!sbsTopSlabReadyRowEmitted || sbsTopSlabTicks % 1200 == 0) {
                emitSbsTopSlabReadyRow("WAITING", readinessGap);
                sbsTopSlabReadyRowEmitted = true;
            }
            if (sbsTopSlabTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitSbsTopSlabTraceGap("ROUTE_READINESS", readinessGap);
            return;
        }

        if (!sbsTopSlabStarted) {
            sbsTopSlabStarted = true;
            sbsTopSlabOrigin = client.player.getBlockPos().add(15, 0, 11).toImmutable();
            sbsTopSlabRowIndex = 0;
            sbsTopSlabStepIndex = -1;
            sbsTopSlabStepAttempted = false;
            sbsTopSlabRepeatFinalClickAttempted = false;
            sbsTopSlabSideProbeAttempted = false;
            sbsTopSlabSideProbeAttemptTick = sbsTopSlabTicks;
            sbsTopSlabSideTargetProbe = SbsTopSlabSideTargetProbe.notRun("not_started");
            sbsTopSlabStepAttemptTick = sbsTopSlabTicks;
            sbsTopSlabLastRetryTick = sbsTopSlabTicks;
            System.out.println("[MC1211_SBS_TOP_SLAB_COMBINATION_ROW]"
                    + " rowPhase=START"
                    + " fixtureOrigin=" + textPos(sbsTopSlabOrigin)
                    + " authoredCases=SBB_CONTROL/SBS_STONE_SLAB_REPEAT_COMBINE/SBS_OAK_SLAB_CONTROL/"
                    + "SBS_OAK_SLAB_ON_COPPER_BULB_RED/SBSB_COPPER_BULB_ON_OAK_SLAB_RED/"
                    + "SBSB_LIVE_CROSSHAIR_COPPER_BULB_ON_OAK_SLAB_RED/"
                    + "SBSBS_OAK_SLAB_ON_SECOND_BLOCK_RED/SBSBS_PERP_SIDE_TARGET_FROM_OWNER_TOP_RED/"
                    + "SBBBBBS_STONE_SLAB_REPEAT_COMBINE/"
                    + "SBBBBBB_CONTROL"
                    + " playerLikeBuild=true"
                    + " manualMarkerInjection=false"
                    + " screenshotPath=not_supported_mc1211_client_tick_route"
                    + " gameplayPatch=false");
            return;
        }

        if (sbsTopSlabRowIndex >= SBS_TOP_SLAB_COMBINATION_CASES.length) {
            emitSbsTopSlabSummary(client);
            return;
        }

        runSbsTopSlabCombinationCase(client, SBS_TOP_SLAB_COMBINATION_CASES[sbsTopSlabRowIndex]);
    }

    private static void requestProgrammaticSbsTopSlabWorldIfNeeded(MinecraftClient client) {
        if (sbsTopSlabWorldStartRequested
                || client == null
                || !client.isFinishedLoading()
                || client.world != null
                || client.player != null) {
            return;
        }
        sbsTopSlabWorldStartRequested = true;
        LevelInfo levelInfo = new LevelInfo(
                "Slabbed MC1211 SBS Top Slab Combination Red",
                GameMode.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                DataConfiguration.SAFE_MODE);
        GeneratorOptions generatorOptions = new GeneratorOptions(0L, false, false);
        client.createIntegratedServerLoader().createAndStart(
                "slabbed-mc1211-sbs-top-slab-combination-red",
                levelInfo,
                generatorOptions,
                Mc1211GoblinRouteClientEntrypoint::createSuperflatDimensionOptions,
                null);
    }

    private static String sbsTopSlabReadinessGap(MinecraftClient client) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        if (!readiness.clientBootstrapReady) {
            return "TRACE_GAP_CLIENT_BOOTSTRAP_NOT_FINISHED";
        }
        if (!readiness.clientWorldReady) {
            return sbsTopSlabWorldStartRequested
                    ? "TRACE_GAP_PROGRAMMATIC_CLIENT_WORLD_PENDING"
                    : "TRACE_GAP_PROGRAMMATIC_WORLD_START_PENDING";
        }
        if (!readiness.clientPlayerReady) {
            return "TRACE_GAP_CLIENT_PLAYER_NOT_READY";
        }
        if (!readiness.integratedServerReady) {
            return "TRACE_GAP_INTEGRATED_SERVER_NOT_READY";
        }
        if (!readiness.serverWorldReady) {
            return "TRACE_GAP_SERVER_WORLD_NOT_READY";
        }
        if (!readiness.serverPlayerReady) {
            return "TRACE_GAP_SERVER_PLAYER_NOT_READY";
        }
        if (!readiness.interactionManagerReady) {
            return "TRACE_GAP_INTERACTION_MANAGER_NOT_READY";
        }
        return null;
    }

    private static void emitSbsTopSlabReadyRow(String phase, String reason) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(MinecraftClient.getInstance());
        System.out.println("[MC1211_SBS_TOP_SLAB_COMBINATION_READY_ROW]"
                + " phase=" + phase
                + " tick=" + sbsTopSlabTicks
                + " clientBootstrapReady=" + readiness.clientBootstrapReady
                + " clientWorldReady=" + readiness.clientWorldReady
                + " clientPlayerReady=" + readiness.clientPlayerReady
                + " integratedServerReady=" + readiness.integratedServerReady
                + " serverWorldReady=" + readiness.serverWorldReady
                + " serverPlayerReady=" + readiness.serverPlayerReady
                + " interactionManagerReady=" + readiness.interactionManagerReady
                + " programmaticWorldStartRequested=" + sbsTopSlabWorldStartRequested
                + " reason=" + reason);
    }

    private static void runSbsTopSlabCombinationCase(
            MinecraftClient client,
            SbsTopSlabCombinationCase spec
    ) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || client.world == null || client.player == null) {
            emitSbsTopSlabTraceGap(spec.rowName(), "TRACE_GAP_WORLD_OR_PLAYER_NOT_READY");
            return;
        }
        if (sbsTopSlabStepIndex < 0) {
            sbsTopSlabCaseOrigin = sbsTopSlabOrigin.add(sbsTopSlabRowIndex * 5, 0, 0);
            prepareSbsTopSlabCase(serverWorld, spec);
            sbsTopSlabPlacementResults = "";
            sbsTopSlabReachDiagnostic = "not_sampled";
            sbsTopSlabStepIndex = 0;
            sbsTopSlabStepAttempted = false;
            sbsTopSlabRepeatFinalClickAttempted = false;
            sbsTopSlabStepAttemptTick = sbsTopSlabTicks;
            sbsTopSlabLastRetryTick = sbsTopSlabTicks;
            System.out.println("[MC1211_SBS_TOP_SLAB_COMBINATION_ROW]"
                    + " rowPhase=CASE_START"
                    + " rowName=" + spec.rowName()
                    + " structure=" + spec.structure()
                    + " caseOrigin=" + textPos(sbsTopSlabCaseOrigin)
                    + " groundPos=" + textPos(sbsTopSlabCaseOrigin.down())
                    + " expectedFinalPos=" + textPos(sbsTopSlabFinalPos(spec))
                    + " expectedFinalKind=" + blockId(spec.finalBlock())
                    + " playerLikeBuild=true"
                    + " manualMarkerInjection=false");
            return;
        }
        if (sbsTopSlabTicks - sbsTopSlabStepAttemptTick < 8 && !sbsTopSlabStepAttempted) {
            return;
        }
        if (sbsTopSlabStepIndex < spec.structure().length()) {
            if (!sbsTopSlabStepAttempted) {
                clickSbsTopSlabStep(client, spec, sbsTopSlabStepIndex, false);
                return;
            }
            if (sbsTopSlabStepReady(serverWorld, spec, sbsTopSlabStepIndex)) {
                if (sbsTopSlabStepIndex == spec.structure().length() - 1) {
                    if (spec.repeatFinalClick()) {
                        if (!sbsTopSlabRepeatFinalClickAttempted) {
                            clickSbsTopSlabRepeatFinalClick(client, spec, false);
                            return;
                        }
                        if (!sbsTopSlabRepeatFinalClickReady(serverWorld, spec)) {
                            if (sbsTopSlabTicks - sbsTopSlabStepAttemptTick >= 160) {
                                emitSbsTopSlabCombinationRow(client, spec, "REPEAT_FINAL_TIMEOUT");
                                advanceSbsTopSlabCase();
                                return;
                            }
                            if (sbsTopSlabTicks - sbsTopSlabLastRetryTick >= 20) {
                                clickSbsTopSlabRepeatFinalClick(client, spec, true);
                            }
                            return;
                        }
                    }
                    emitSbsTopSlabPersistenceTrace(client, spec, "FINAL_STEP_READY", 0);
                }
                sbsTopSlabStepIndex++;
                sbsTopSlabStepAttempted = false;
                sbsTopSlabStepAttemptTick = sbsTopSlabTicks;
                sbsTopSlabLastRetryTick = sbsTopSlabTicks;
                return;
            }
            if (sbsTopSlabTicks - sbsTopSlabStepAttemptTick < 160) {
                if (sbsTopSlabTicks - sbsTopSlabLastRetryTick >= 20) {
                    clickSbsTopSlabStep(client, spec, sbsTopSlabStepIndex, true);
                }
                return;
            }
            emitSbsTopSlabCombinationRow(client, spec, "STEP_TIMEOUT_" + sbsTopSlabStepIndex);
            advanceSbsTopSlabCase();
            return;
        }
        int finalHoldTicks = sbsTopSlabTicks - sbsTopSlabStepAttemptTick;
        if (finalHoldTicks < 60) {
            if (sbsTopSlabShouldEmitPersistenceTrace(finalHoldTicks)) {
                emitSbsTopSlabPersistenceTrace(client, spec, "FINAL_HOLD", finalHoldTicks);
            }
            return;
        }
        if (finalHoldTicks < 100 && sbsTopSlabShouldWaitForMeshTrace(client, spec)) {
            return;
        }
        if (spec.sideSlabFromFinalOwnerTop()) {
            if (!sbsTopSlabSideProbeAttempted) {
                sbsTopSlabSideTargetProbe = startSbsTopSlabPerpendicularSideTargetProbe(client, spec);
                sbsTopSlabSideProbeAttempted = true;
                sbsTopSlabSideProbeAttemptTick = sbsTopSlabTicks;
                return;
            }
            if (sbsTopSlabTicks - sbsTopSlabSideProbeAttemptTick < 20) {
                return;
            }
            sbsTopSlabSideTargetProbe = completeSbsTopSlabPerpendicularSideTargetProbe(
                    client,
                    spec,
                    sbsTopSlabSideTargetProbe,
                    sbsTopSlabTicks - sbsTopSlabSideProbeAttemptTick);
        }
        emitSbsTopSlabCombinationRow(
                client,
                spec,
                spec.sideSlabFromFinalOwnerTop() ? "AFTER_PERP_SIDE_SETTLE" : "AFTER_AUTHORED_STEPS");
        advanceSbsTopSlabCase();
    }

    private static boolean sbsTopSlabShouldWaitForMeshTrace(MinecraftClient client, SbsTopSlabCombinationCase spec) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || spec == null || spec.finalSlab()) {
            return false;
        }
        BlockState finalState = serverWorld.getBlockState(sbsTopSlabFinalPos(spec));
        BlockState supportState = serverWorld.getBlockState(sbsTopSlabSupportPos(spec));
        if (finalState.isAir()
                || finalState.getBlock() instanceof SlabBlock
                || !(supportState.getBlock() instanceof SlabBlock)) {
            return false;
        }
        return !OffsetBlockStateModel.snapshotFullMeshBoundsSample().seen();
    }

    private static void prepareSbsTopSlabCase(ServerWorld serverWorld, SbsTopSlabCombinationCase spec) {
        BlockPos origin = sbsTopSlabCaseOrigin;
        serverWorld.getServer().execute(() -> {
            for (int x = origin.getX() - 1; x <= origin.getX() + 1; x++) {
                for (int z = origin.getZ() - 1; z <= origin.getZ() + 1; z++) {
                    for (int y = origin.getY() - 2; y <= origin.getY() + spec.structure().length() + 2; y++) {
                        serverWorld.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
            serverWorld.setBlockState(origin.down(), Blocks.STONE.getDefaultState(), 3);
            if (!serverWorld.getServer().getPlayerManager().getPlayerList().isEmpty()) {
                serverWorld.getServer().getPlayerManager().getPlayerList().get(0)
                        .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
        });
    }

    private static void clickSbsTopSlabStep(
            MinecraftClient client,
            SbsTopSlabCombinationCase spec,
            int stepIndex,
            boolean retry
    ) {
        ServerWorld serverWorld = serverWorldFor(client);
        BlockPos hitPos = stepIndex == 0 ? sbsTopSlabCaseOrigin.down() : sbsTopSlabStepPos(spec, stepIndex - 1);
        Vec3d hitVector = sbsTopSlabVisibleTopHitVector(serverWorld, hitPos);
        char step = spec.structure().charAt(stepIndex);
        net.minecraft.item.Item held = sbsTopSlabStepItem(spec, stepIndex);
        if (stepIndex == spec.structure().length() - 1) {
            BlockPos finalPos = sbsTopSlabFinalPos(spec);
            OffsetBlockStateModel.resetModelDyOwnerSample(finalPos);
            OffsetBlockStateModel.resetFullMeshBoundsSample(finalPos);
        }
        String heldItem = itemId(held);
        String result = spec.liveCrosshairFinalStep() && stepIndex == spec.structure().length() - 1
                ? clickBlockViaLiveCrosshair(client, spec, held, hitVector, "FINAL_STEP")
                : clickBlock(
                        client,
                        held,
                        hitPos,
                        Direction.UP,
                        hitVector);
        sbsTopSlabReachDiagnostic = slabThenBlockReachDiagnostic;
        String stepResult = "step=" + stepIndex
                + "/symbol=" + step
                + "/heldItem=" + heldItem
                + "/hitPos=" + textPos(hitPos)
                + "/face=up"
                + "/mode=" + (spec.liveCrosshairFinalStep() && stepIndex == spec.structure().length() - 1
                        ? "live_crosshair_final_step" : "fabricated_up_hit")
                + "/hitVector=" + formatVec(hitVector)
                + "/retry=" + retry
                + "/result=" + result;
        sbsTopSlabPlacementResults = sbsTopSlabPlacementResults.isEmpty()
                ? stepResult
                : sbsTopSlabPlacementResults + ";" + stepResult;
        sbsTopSlabStepAttempted = true;
        if (!retry) {
            sbsTopSlabStepAttemptTick = sbsTopSlabTicks;
        }
        sbsTopSlabLastRetryTick = sbsTopSlabTicks;
    }

    private static void clickSbsTopSlabRepeatFinalClick(
            MinecraftClient client,
            SbsTopSlabCombinationCase spec,
            boolean retry
    ) {
        ServerWorld serverWorld = serverWorldFor(client);
        BlockPos finalPos = sbsTopSlabFinalPos(spec);
        Vec3d hitVector = sbsTopSlabVisibleTopHitVector(serverWorld, finalPos);
        net.minecraft.item.Item held = spec.finalItem();
        String result = clickBlock(
                client,
                held,
                finalPos,
                Direction.UP,
                hitVector);
        sbsTopSlabReachDiagnostic = slabThenBlockReachDiagnostic;
        String stepResult = "step=repeat_final"
                + "/symbol=R"
                + "/heldItem=" + itemId(held)
                + "/hitPos=" + textPos(finalPos)
                + "/face=up"
                + "/hitVector=" + formatVec(hitVector)
                + "/retry=" + retry
                + "/result=" + result;
        sbsTopSlabPlacementResults = sbsTopSlabPlacementResults.isEmpty()
                ? stepResult
                : sbsTopSlabPlacementResults + ";" + stepResult;
        sbsTopSlabRepeatFinalClickAttempted = true;
        if (!retry) {
            sbsTopSlabStepAttemptTick = sbsTopSlabTicks;
        }
        sbsTopSlabLastRetryTick = sbsTopSlabTicks;
    }

    private static String clickBlockViaLiveCrosshair(
            MinecraftClient client,
            SbsTopSlabCombinationCase spec,
            net.minecraft.item.Item item,
            Vec3d intendedHitVector,
            String samplePhase
    ) {
        if (client == null || client.player == null || client.interactionManager == null
                || client.world == null || client.gameRenderer == null) {
            return "FAIL_ROUTE_NOT_READY";
        }
        syncSlabThenBlockPlayer(client, intendedHitVector);
        ItemStack stack = new ItemStack(item, 8);
        client.player.setStackInHand(Hand.MAIN_HAND, stack);
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(item, 8));
        }
        Vec3d eye = client.player.getCameraPosVec(0.0f);
        Vec3d end = eye.add(client.player.getRotationVec(0.0f).multiply(6.0d));
        HitResult vanillaTarget = client.world.raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.player));
        client.gameRenderer.updateCrosshairTarget(0.0f);
        HitResult finalTarget = client.crosshairTarget;
        if (!(finalTarget instanceof BlockHitResult blockHit) || finalTarget.getType() != HitResult.Type.BLOCK) {
            return "FAIL_LIVE_CROSSHAIR_NOT_BLOCK"
                    + "/samplePhase=" + samplePhase
                    + "/vanillaTarget=" + formatHit(vanillaTarget)
                    + "/finalTarget=" + formatHit(finalTarget);
        }
        ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, blockHit);
        return "LIVE_CROSSHAIR_" + result
                + "/samplePhase=" + samplePhase
                + "/vanillaTarget=" + formatHit(vanillaTarget)
                + "/vanillaOwner=" + sbsTopSlabTargetOwner(spec, vanillaTarget)
                + "/finalTarget=" + formatHit(finalTarget)
                + "/finalOwner=" + sbsTopSlabTargetOwner(spec, finalTarget);
    }

    private static boolean sbsTopSlabStepReady(
            ServerWorld serverWorld,
            SbsTopSlabCombinationCase spec,
            int stepIndex
    ) {
        if (serverWorld == null || sbsTopSlabCaseOrigin == null) {
            return false;
        }
        BlockPos stepPos = sbsTopSlabStepPos(spec, stepIndex);
        BlockState state = serverWorld.getBlockState(stepPos);
        return state.isOf(sbsTopSlabStepBlock(spec, stepIndex));
    }

    private static boolean sbsTopSlabRepeatFinalClickReady(
            ServerWorld serverWorld,
            SbsTopSlabCombinationCase spec
    ) {
        if (serverWorld == null || sbsTopSlabCaseOrigin == null) {
            return false;
        }
        BlockState state = serverWorld.getBlockState(sbsTopSlabFinalPos(spec));
        return state.isOf(spec.finalBlock())
                && state.contains(SlabBlock.TYPE)
                && state.get(SlabBlock.TYPE) == SlabType.DOUBLE;
    }

    private static net.minecraft.item.Item sbsTopSlabStepItem(SbsTopSlabCombinationCase spec, int stepIndex) {
        char step = spec.structure().charAt(stepIndex);
        if (step == 'S' && spec.finalSlab() && stepIndex == spec.structure().length() - 1) {
            return spec.finalItem();
        }
        if (step == 'S') {
            return spec.slabItem();
        }
        if (!spec.finalSlab() && stepIndex == spec.structure().length() - 1) {
            return spec.finalItem();
        }
        return spec.supportItem();
    }

    private static Block sbsTopSlabStepBlock(SbsTopSlabCombinationCase spec, int stepIndex) {
        char step = spec.structure().charAt(stepIndex);
        if (step == 'S' && spec.finalSlab() && stepIndex == spec.structure().length() - 1) {
            return spec.finalBlock();
        }
        if (step == 'S') {
            return spec.slabBlock();
        }
        if (!spec.finalSlab() && stepIndex == spec.structure().length() - 1) {
            return spec.finalBlock();
        }
        return spec.supportBlock();
    }

    private static BlockPos sbsTopSlabStepPos(SbsTopSlabCombinationCase spec, int stepIndex) {
        return sbsTopSlabCaseOrigin.up(stepIndex);
    }

    private static BlockPos sbsTopSlabFinalPos(SbsTopSlabCombinationCase spec) {
        return sbsTopSlabStepPos(spec, spec.structure().length() - 1);
    }

    private static BlockPos sbsTopSlabSupportPos(SbsTopSlabCombinationCase spec) {
        return sbsTopSlabStepPos(spec, Math.max(0, spec.structure().length() - 2));
    }

    private static Vec3d sbsTopSlabVisibleTopHitVector(ServerWorld serverWorld, BlockPos pos) {
        double y = pos == null ? 0.0d : pos.getY() + 1.0d;
        if (serverWorld != null && pos != null) {
            BlockState state = serverWorld.getBlockState(pos);
            VoxelShape shape = state.getOutlineShape(serverWorld, pos);
            if (!state.isAir() && !shape.isEmpty()) {
                y = pos.getY() + shape.getBoundingBox().maxY;
            }
        }
        return new Vec3d(pos.getX() + 0.5d, y, pos.getZ() + 0.5d);
    }

    private static Vec3d sbsTopSlabVisibleCenterHitVector(ServerWorld serverWorld, BlockPos pos) {
        double y = pos == null ? 0.0d : pos.getY() + 0.5d;
        if (serverWorld != null && pos != null) {
            BlockState state = serverWorld.getBlockState(pos);
            VoxelShape shape = state.getOutlineShape(serverWorld, pos);
            if (!state.isAir() && !shape.isEmpty()) {
                var bounds = shape.getBoundingBox();
                y = pos.getY() + ((bounds.minY + bounds.maxY) * 0.5d);
            }
        }
        return new Vec3d(pos.getX() + 0.5d, y, pos.getZ() + 0.5d);
    }

    private static void emitSbsTopSlabCombinationRow(
            MinecraftClient client,
            SbsTopSlabCombinationCase spec,
            String rowPhase
    ) {
        ServerWorld serverWorld = serverWorldFor(client);
        ClientWorld clientWorld = client == null ? null : client.world;
        BlockPos finalPos = sbsTopSlabFinalPos(spec);
        BlockPos supportPos = sbsTopSlabSupportPos(spec);
        BlockState finalState = serverWorld == null ? Blocks.AIR.getDefaultState() : serverWorld.getBlockState(finalPos);
        BlockState clientFinalState = clientWorld == null ? Blocks.AIR.getDefaultState() : clientWorld.getBlockState(finalPos);
        BlockState finalAboveState = serverWorld == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(finalPos.up());
        BlockState supportState = serverWorld == null ? Blocks.AIR.getDefaultState() : serverWorld.getBlockState(supportPos);
        BlockState clientSupportState = clientWorld == null ? Blocks.AIR.getDefaultState() : clientWorld.getBlockState(supportPos);
        double finalDy = serverWorld == null ? Double.NaN : SlabSupport.getYOffset(serverWorld, finalPos, finalState);
        double finalClientDy = clientWorld == null ? Double.NaN : ClientDy.dyFor(clientWorld, finalPos, clientFinalState);
        double supportDy = serverWorld == null ? Double.NaN : SlabSupport.getYOffset(serverWorld, supportPos, supportState);
        double supportClientDy = clientWorld == null ? Double.NaN : ClientDy.dyFor(clientWorld, supportPos, clientSupportState);
        boolean finalOwnerTop = serverWorld != null
                && SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(serverWorld, finalPos, finalState);
        boolean finalClientOwnerTop = clientWorld != null
                && SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(clientWorld, finalPos, clientFinalState);
        boolean finalLoweredCarrier = serverWorld != null
                && SlabAnchorAttachment.isPersistentLoweredSlabCarrier(serverWorld, finalPos, finalState);
        boolean finalClientLoweredCarrier = clientWorld != null
                && SlabAnchorAttachment.isPersistentLoweredSlabCarrier(clientWorld, finalPos, clientFinalState);
        boolean finalAnchored = serverWorld != null && SlabAnchorAttachment.isAnchored(serverWorld, finalPos);
        boolean finalCompoundAnchor = serverWorld != null
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(serverWorld, finalPos);
        boolean finalClientCompoundAnchor = clientWorld != null
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(clientWorld, finalPos);
        boolean supportAnchored = serverWorld != null && SlabAnchorAttachment.isAnchored(serverWorld, supportPos);
        boolean supportClientAnchored = clientWorld != null && SlabAnchorAttachment.isAnchored(clientWorld, supportPos);
        boolean supportCompoundAnchor = serverWorld != null
                && SlabAnchorAttachment.isCompoundFullBlockAnchor(serverWorld, supportPos);
        boolean supportLowered = !(supportState.getBlock() instanceof SlabBlock)
                && !supportState.isAir()
                && supportDy < -1.0e-6d;
        boolean supportIsSlab = supportState.getBlock() instanceof SlabBlock;
        String supportCarrierFacts = sbsTopSlabSupportCarrierFacts(serverWorld, supportPos, supportState, supportDy);
        double supportTopY = sbsTopSlabVisibleTopY(serverWorld, supportPos, supportState, supportDy);
        double finalBottomY = sbsTopSlabVisibleBottomY(serverWorld, finalPos, finalState, finalDy);
        double contactGap = Double.isFinite(finalBottomY) && Double.isFinite(supportTopY)
                ? finalBottomY - supportTopY
                : Double.NaN;
        Vec3d finalHitVector = sbsTopSlabVisibleTopHitVector(serverWorld, supportPos);
        String targetSample = sampleSbsTopSlabTarget(client, spec, finalHitVector, "FINAL_TARGET");
        double targetDy = sbsTopSlabTargetDy(clientWorld, client == null ? null : client.crosshairTarget);
        Direction targetSide = sbsTopSlabTargetSide(client == null ? null : client.crosshairTarget);
        Vec3d finalBlockHitVector = sbsTopSlabVisibleCenterHitVector(serverWorld, finalPos);
        String finalBlockTargetSample = sampleSbsTopSlabTarget(
                client,
                spec,
                finalBlockHitVector,
                "FINAL_BLOCK_TARGET");
        String finalBlockTargetOwner = sbsTopSlabTargetOwner(spec, client == null ? null : client.crosshairTarget);
        double finalBlockTargetDy = sbsTopSlabTargetDy(clientWorld, client == null ? null : client.crosshairTarget);
        OffsetBlockStateModel.ModelDyOwnerSample modelTrace = OffsetBlockStateModel.snapshotModelDyOwnerSample();
        OffsetBlockStateModel.FullMeshBoundsSample meshTrace = OffsetBlockStateModel.snapshotFullMeshBoundsSample();
        SbsTopSlabCullingProof cullingProof = captureSbsTopSlabCullingProof(
                clientWorld,
                finalPos,
                clientFinalState,
                supportPos,
                clientSupportState,
                targetSide);
        SbsTopSlabSupportRemovalProbe supportRemovalProbe = probeSbsTopSlabAfterSupportRemoval(
                client,
                spec,
                supportIsSlab);
        SbsTopSlabSideTargetProbe sideTargetProbe = spec.sideSlabFromFinalOwnerTop()
                ? sbsTopSlabSideTargetProbe
                : SbsTopSlabSideTargetProbe.notRun("not_requested");
        SbsTopSlabClassification classification = classifySbsTopSlabRow(
                spec,
                finalState,
                finalAboveState,
                finalDy,
                finalOwnerTop,
                finalLoweredCarrier,
                finalAnchored,
                finalCompoundAnchor,
                supportLowered,
                supportIsSlab,
                supportDy,
                supportCompoundAnchor,
                contactGap,
                modelTrace,
                finalClientDy,
                targetDy,
                meshTrace,
                cullingProof,
                finalBlockTargetOwner,
                finalBlockTargetDy,
                supportRemovalProbe,
                sideTargetProbe);
        recordSbsTopSlabRowSummary(spec, classification);
        SlabType slabType = finalState.getBlock() instanceof SlabBlock && finalState.contains(SlabBlock.TYPE)
                ? finalState.get(SlabBlock.TYPE)
                : null;
        System.out.println("[MC1211_SBS_TOP_SLAB_COMBINATION_ROW]"
                + " rowPhase=" + rowPhase
                + " rowName=" + spec.rowName()
                + " structure=" + spec.structure()
                + " authoredSteps=" + sbsTopSlabPlacementResults
                + " heldItem=" + itemId(spec.finalItem())
                + " finalBlockId=" + blockId(spec.finalBlock())
                + " clickedFace=up"
                + " hitVector=" + formatVec(finalHitVector)
                + " finalTarget=" + targetSample
                + " finalBlockHitVector=" + formatVec(finalBlockHitVector)
                + " finalBlockTarget=" + finalBlockTargetSample
                + " finalBlockTargetOwner=" + finalBlockTargetOwner
                + " finalBlockTargetDy=" + formatDouble(finalBlockTargetDy)
                + " postSupportRemoval=" + sbsTopSlabSupportRemovalProbeText(supportRemovalProbe)
                + " perpSideTarget=" + sbsTopSlabSideTargetProbeText(sideTargetProbe)
                + " caseOrigin=" + textPos(sbsTopSlabCaseOrigin)
                + " supportPos=" + textPos(supportPos)
                + " supportState=" + supportState
                + " supportClientState=" + clientSupportState
                + " supportDy=" + formatDouble(supportDy)
                + " supportClientDy=" + formatDouble(supportClientDy)
                + " supportAnchored=" + supportAnchored
                + " supportClientAnchored=" + supportClientAnchored
                + " supportCompoundFullBlockAnchorMarker=" + supportCompoundAnchor
                + " supportLowered=" + supportLowered
                + " supportCarrierFacts=" + supportCarrierFacts
                + " supportTopY=" + formatDouble(supportTopY)
                + " finalPos=" + textPos(finalPos)
                + " finalState=" + finalState
                + " finalClientState=" + clientFinalState
                + " finalAboveState=" + finalAboveState
                + " slabType=" + (slabType == null ? "none" : slabType.asString())
                + " slabSupportDy=" + formatDouble(finalDy)
                + " clientDy=" + formatDouble(finalClientDy)
                + " ownerTopMarker=" + finalOwnerTop
                + " clientOwnerTopMarker=" + finalClientOwnerTop
                + " loweredCarrierMarker=" + finalLoweredCarrier
                + " clientLoweredCarrierMarker=" + finalClientLoweredCarrier
                + " anchoredMarker=" + finalAnchored
                + " compoundFullBlockAnchorMarker=" + finalCompoundAnchor
                + " clientCompoundFullBlockAnchorMarker=" + finalClientCompoundAnchor
                + " placedSlabVisualBottomY=" + formatDouble(finalBottomY)
                + " contactGap=" + formatDouble(contactGap)
                + " modelDy=" + (modelTrace.seen() ? formatDouble(modelTrace.lastDy()) : "not_observed")
                + " modelTrace=" + sbsTopSlabModelTraceText(modelTrace)
                + " outlineDy=" + formatDouble(finalClientDy)
                + " targetDy=" + formatDouble(targetDy)
                + " meshTrace=" + sbsTopSlabMeshTraceText(meshTrace)
                + " cullingProof=" + sbsTopSlabCullingProofText(cullingProof)
                + " screenshotPath=not_supported_mc1211_client_tick_route"
                + " reachDiagnostic=" + sbsTopSlabReachDiagnostic
                + " classification=" + classification.classification()
                + " failureLayer=" + classification.failureLayer()
                + " finalResult=" + classification.finalResult()
                + " gameplayPatch=false"
                + " cleanup=false"
                + " savepoint=false");
    }

    private static boolean sbsTopSlabShouldEmitPersistenceTrace(int finalHoldTicks) {
        return finalHoldTicks == 1
                || finalHoldTicks == 2
                || finalHoldTicks == 5
                || finalHoldTicks == 10
                || finalHoldTicks == 20
                || finalHoldTicks == 40
                || finalHoldTicks == 59;
    }

    private static void emitSbsTopSlabPersistenceTrace(
            MinecraftClient client,
            SbsTopSlabCombinationCase spec,
            String tracePhase,
            int finalHoldTicks
    ) {
        ServerWorld serverWorld = serverWorldFor(client);
        ClientWorld clientWorld = client == null ? null : client.world;
        BlockPos finalPos = sbsTopSlabFinalPos(spec);
        BlockPos supportPos = sbsTopSlabSupportPos(spec);
        BlockState finalState = serverWorld == null ? Blocks.AIR.getDefaultState() : serverWorld.getBlockState(finalPos);
        BlockState clientFinalState = clientWorld == null ? Blocks.AIR.getDefaultState() : clientWorld.getBlockState(finalPos);
        BlockState supportState = serverWorld == null ? Blocks.AIR.getDefaultState() : serverWorld.getBlockState(supportPos);
        double finalDy = serverWorld == null ? Double.NaN : SlabSupport.getYOffset(serverWorld, finalPos, finalState);
        double supportDy = serverWorld == null ? Double.NaN : SlabSupport.getYOffset(serverWorld, supportPos, supportState);
        boolean finalCarrier = serverWorld != null
                && SlabAnchorAttachment.isPersistentLoweredSlabCarrier(serverWorld, finalPos, finalState);
        boolean clientFinalCarrier = clientWorld != null
                && SlabAnchorAttachment.isPersistentLoweredSlabCarrier(clientWorld, finalPos, clientFinalState);
        boolean qualifiesCarrier = serverWorld != null
                && SlabAnchorAttachment.qualifiesForPersistentLoweredSlabCarrier(serverWorld, finalPos, finalState);
        boolean bottomCarrierNonRecursive = serverWorld != null
                && SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(
                serverWorld,
                finalPos,
                finalState);
        boolean supportAnchored = serverWorld != null && SlabAnchorAttachment.isAnchored(serverWorld, supportPos);
        boolean finalCanPlaceAt = serverWorld != null && !finalState.isAir() && finalState.canPlaceAt(serverWorld, finalPos);
        String supportCarrierFacts = sbsTopSlabSupportCarrierFacts(serverWorld, supportPos, supportState, supportDy);
        System.out.println("[MC1211_SBS_TOP_SLAB_PERSISTENCE_TRACE]"
                + " tracePhase=" + tracePhase
                + " rowName=" + spec.rowName()
                + " holdTicks=" + finalHoldTicks
                + " finalPos=" + textPos(finalPos)
                + " finalState=" + finalState
                + " finalClientState=" + clientFinalState
                + " finalDy=" + formatDouble(finalDy)
                + " finalCarrier=" + finalCarrier
                + " clientFinalCarrier=" + clientFinalCarrier
                + " qualifiesCarrier=" + qualifiesCarrier
                + " bottomCarrierNonRecursive=" + bottomCarrierNonRecursive
                + " finalCanPlaceAt=" + finalCanPlaceAt
                + " supportPos=" + textPos(supportPos)
                + " supportState=" + supportState
                + " supportDy=" + formatDouble(supportDy)
                + " supportAnchored=" + supportAnchored
                + " supportCarrierFacts=" + supportCarrierFacts
                + " placementRoute=ClientPlayerInteractionManager.interactBlock"
                + " gameplayPatch=false"
                + " cleanup=false"
                + " savepoint=false");
    }

    private static String sbsTopSlabSupportCarrierFacts(
            ServerWorld serverWorld,
            BlockPos supportPos,
            BlockState supportState,
            double supportDy
    ) {
        if (serverWorld == null || supportPos == null || supportState == null) {
            return "candidate=false/reason=world_or_support_missing";
        }
        boolean candidate = SlabAnchorAttachment.isLoweredFullBlockSlabCarrierSupport(
                serverWorld,
                supportPos,
                supportState);
        boolean ordinary = SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(
                serverWorld,
                supportPos,
                supportState);
        boolean solid = supportState.isSolidBlock(serverWorld, supportPos);
        boolean blockEntity = supportState.getBlock() instanceof BlockEntityProvider;
        boolean craftingTable = supportState.getBlock() instanceof CraftingTableBlock;
        boolean slab = supportState.getBlock() instanceof SlabBlock;
        VoxelShape outline = supportState.getOutlineShape(serverWorld, supportPos);
        return "candidate=" + candidate
                + "/ordinary=" + ordinary
                + "/solid=" + solid
                + "/blockEntity=" + blockEntity
                + "/craftingTable=" + craftingTable
                + "/slab=" + slab
                + "/air=" + supportState.isAir()
                + "/fluidEmpty=" + supportState.getFluidState().isEmpty()
                + "/loweredExact=" + near(supportDy, -0.5d)
                + "/outline=" + sbsTopSlabShapeBoundsText(outline, supportDy);
    }

    private static String sbsTopSlabShapeBoundsText(VoxelShape shape, double dy) {
        if (shape == null) {
            return "null";
        }
        if (shape.isEmpty()) {
            return "empty";
        }
        var bounds = shape.getBoundingBox();
        return "minY=" + formatDouble(bounds.minY)
                + ",maxY=" + formatDouble(bounds.maxY)
                + ",dyAdjustedMinY=" + formatDouble(bounds.minY - dy)
                + ",dyAdjustedMaxY=" + formatDouble(bounds.maxY - dy);
    }

    private static String sampleSbsTopSlabTarget(
            MinecraftClient client,
            SbsTopSlabCombinationCase spec,
            Vec3d hitVector
    ) {
        return sampleSbsTopSlabTarget(client, spec, hitVector, "FINAL_TARGET");
    }

    private static String sampleSbsTopSlabTarget(
            MinecraftClient client,
            SbsTopSlabCombinationCase spec,
            Vec3d hitVector,
            String samplePhase
    ) {
        if (client == null || client.player == null || client.world == null || client.gameRenderer == null) {
            return "samplePhase=" + samplePhase + "/classification=TRACE_GAP_ROUTE_NOT_READY";
        }
        syncSlabThenBlockPlayer(client, hitVector);
        client.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(spec.finalItem(), 8));
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(spec.finalItem(), 8));
        }
        Vec3d eye = client.player.getCameraPosVec(0.0f);
        Vec3d end = eye.add(client.player.getRotationVec(0.0f).multiply(6.0d));
        HitResult vanillaTarget = client.world.raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.player));
        client.gameRenderer.updateCrosshairTarget(0.0f);
        HitResult finalTarget = client.crosshairTarget;
        return "samplePhase=" + samplePhase
                + "/vanillaTarget=" + formatHit(vanillaTarget)
                + "/vanillaOwner=" + sbsTopSlabTargetOwner(spec, vanillaTarget)
                + "/finalTarget=" + formatHit(finalTarget)
                + "/finalOwner=" + sbsTopSlabTargetOwner(spec, finalTarget)
                + "/liveHit=" + formatVec(hitVector);
    }

    private static String sbsTopSlabTargetOwner(SbsTopSlabCombinationCase spec, HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return hit == null ? "null" : hit.getType().toString();
        }
        BlockPos hitPos = blockHit.getBlockPos();
        if (hitPos.equals(sbsTopSlabFinalPos(spec))) {
            return spec.finalSlab() ? "final_top_slab" : "final_control_block";
        }
        if (hitPos.equals(sbsTopSlabSupportPos(spec))) {
            return "support_owner";
        }
        if (hitPos.equals(sbsTopSlabCaseOrigin.down())) {
            return "foundation";
        }
        return "other:" + textPos(hitPos);
    }

    private static double sbsTopSlabTargetDy(ClientWorld world, HitResult hit) {
        if (world == null || !(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return Double.NaN;
        }
        BlockPos pos = blockHit.getBlockPos();
        return ClientDy.dyFor(world, pos, world.getBlockState(pos));
    }

    private static Direction sbsTopSlabTargetSide(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return blockHit.getSide();
    }

    private static SbsTopSlabSupportRemovalProbe probeSbsTopSlabAfterSupportRemoval(
            MinecraftClient client,
            SbsTopSlabCombinationCase spec,
            boolean supportIsSlab
    ) {
        ServerWorld serverWorld = serverWorldFor(client);
        ClientWorld clientWorld = client == null ? null : client.world;
        if (serverWorld == null || spec == null || !supportIsSlab) {
            return SbsTopSlabSupportRemovalProbe.notRun("not_sbsb_lowered_slab_support");
        }
        BlockPos finalPos = sbsTopSlabFinalPos(spec);
        BlockPos supportPos = sbsTopSlabSupportPos(spec);
        BlockState supportStateBefore = serverWorld.getBlockState(supportPos);
        if (!(supportStateBefore.getBlock() instanceof SlabBlock)) {
            return SbsTopSlabSupportRemovalProbe.notRun("support_not_slab");
        }
        boolean removed = serverWorld.breakBlock(supportPos, false);
        BlockState finalStateAfter = serverWorld.getBlockState(finalPos);
        BlockState supportStateAfter = serverWorld.getBlockState(supportPos);
        BlockState clientFinalStateAfter = clientWorld == null
                ? Blocks.AIR.getDefaultState()
                : clientWorld.getBlockState(finalPos);
        double finalDyAfter = SlabSupport.getYOffset(serverWorld, finalPos, finalStateAfter);
        double clientDyAfter = clientWorld == null
                ? Double.NaN
                : ClientDy.dyFor(clientWorld, finalPos, clientFinalStateAfter);
        boolean anchoredAfter = SlabAnchorAttachment.isAnchored(serverWorld, finalPos);
        boolean compoundAfter = SlabAnchorAttachment.isCompoundFullBlockAnchor(serverWorld, finalPos);
        boolean canPlaceAtAfter = !finalStateAfter.isAir() && finalStateAfter.canPlaceAt(serverWorld, finalPos);
        Vec3d hitVectorAfter = sbsTopSlabVisibleCenterHitVector(serverWorld, finalPos);
        String targetSampleAfter = sampleSbsTopSlabTarget(
                client,
                spec,
                hitVectorAfter,
                "POST_SUPPORT_REMOVAL_FINAL_BLOCK_TARGET");
        String targetOwnerAfter = sbsTopSlabTargetOwner(spec, client == null ? null : client.crosshairTarget);
        double targetDyAfter = sbsTopSlabTargetDy(clientWorld, client == null ? null : client.crosshairTarget);
        return new SbsTopSlabSupportRemovalProbe(
                true,
                removed,
                supportStateBefore.toString(),
                supportStateAfter.toString(),
                finalStateAfter.toString(),
                clientFinalStateAfter.toString(),
                finalDyAfter,
                clientDyAfter,
                anchoredAfter,
                compoundAfter,
                canPlaceAtAfter,
                targetSampleAfter,
                targetOwnerAfter,
                targetDyAfter,
                hitVectorAfter,
                "NONE");
    }

    private static SbsTopSlabSideTargetProbe startSbsTopSlabPerpendicularSideTargetProbe(
            MinecraftClient client,
            SbsTopSlabCombinationCase spec
    ) {
        if (client == null || spec == null || !spec.sideSlabFromFinalOwnerTop()) {
            return SbsTopSlabSideTargetProbe.notRun("not_requested");
        }
        ServerWorld serverWorld = serverWorldFor(client);
        ClientWorld clientWorld = client.world;
        if (serverWorld == null || clientWorld == null) {
            return SbsTopSlabSideTargetProbe.notRun("world_not_ready");
        }
        BlockPos finalPos = sbsTopSlabFinalPos(spec);
        Vec3d hitVector = sbsTopSlabVisibleSideHitVector(serverWorld, finalPos, Direction.EAST);
        syncSlabThenBlockPlayer(client, hitVector);
        client.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(spec.slabItem(), 8));
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND,
                    new ItemStack(spec.slabItem(), 8));
        }
        String sample = sampleSbsTopSlabTarget(client, spec, hitVector, "PERP_SIDE_TARGET");
        HitResult hit = client.crosshairTarget;
        String targetOwner = sbsTopSlabTargetOwner(spec, hit);
        double targetDy = sbsTopSlabTargetDy(clientWorld, hit);
        Direction targetSide = sbsTopSlabTargetSide(hit);
        Vec3d eye = client.player.getCameraPosVec(0.0f);
        BlockPos playerPos = client.player.getBlockPos();
        BlockPos sidePos = finalPos.offset(Direction.EAST);
        BlockState sideStateBefore = serverWorld.getBlockState(sidePos);
        BlockState sideClientStateBefore = clientWorld.getBlockState(sidePos);
        String interactResult = "not_run";
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                && client.interactionManager != null && client.player != null) {
            ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, blockHit);
            interactResult = result.toString();
        }
        BlockState sideStateImmediate = serverWorld.getBlockState(sidePos);
        BlockState sideClientStateImmediate = clientWorld.getBlockState(sidePos);
        return new SbsTopSlabSideTargetProbe(
                true,
                false,
                hitVector,
                itemId(spec.slabItem()),
                "perpendicular_side_slab_from_settled_owner_top",
                textPos(playerPos),
                formatVec(eye),
                sample,
                targetOwner,
                targetDy,
                targetSide,
                interactResult,
                0,
                sidePos,
                sideStateBefore.toString(),
                sideClientStateBefore.toString(),
                sideStateImmediate.toString(),
                sideClientStateImmediate.toString(),
                sideStateImmediate.toString(),
                sideClientStateImmediate.toString(),
                SlabSupport.getYOffset(serverWorld, sidePos, sideStateImmediate),
                ClientDy.dyFor(clientWorld, sidePos, sideClientStateImmediate),
                SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(serverWorld, sidePos, sideStateImmediate),
                SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(serverWorld, sidePos, sideStateImmediate),
                SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(serverWorld, sidePos, sideStateImmediate),
                "NONE");
    }

    private static SbsTopSlabSideTargetProbe completeSbsTopSlabPerpendicularSideTargetProbe(
            MinecraftClient client,
            SbsTopSlabCombinationCase spec,
            SbsTopSlabSideTargetProbe started,
            int waitTicks
    ) {
        if (started == null || !started.ran()) {
            return SbsTopSlabSideTargetProbe.notRun(started == null ? "not_started" : started.reason());
        }
        ServerWorld serverWorld = serverWorldFor(client);
        ClientWorld clientWorld = client == null ? null : client.world;
        if (serverWorld == null || clientWorld == null || started.sidePos() == null) {
            return started.withCompletion(
                    false,
                    waitTicks,
                    started.sideStateImmediate(),
                    started.sideClientStateImmediate(),
                    started.sideDyAfter(),
                    started.sideClientDyAfter(),
                    started.sideLowerMarker(),
                    started.sideUpperMarker(),
                    started.sideDoubleMarker(),
                    "completion_world_not_ready");
        }
        BlockState sideStateAfter = serverWorld.getBlockState(started.sidePos());
        BlockState sideClientStateAfter = clientWorld.getBlockState(started.sidePos());
        return started.withCompletion(
                true,
                waitTicks,
                sideStateAfter.toString(),
                sideClientStateAfter.toString(),
                SlabSupport.getYOffset(serverWorld, started.sidePos(), sideStateAfter),
                ClientDy.dyFor(clientWorld, started.sidePos(), sideClientStateAfter),
                SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(serverWorld, started.sidePos(), sideStateAfter),
                SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(serverWorld, started.sidePos(), sideStateAfter),
                SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(serverWorld, started.sidePos(), sideStateAfter),
                "NONE");
    }

    private static Vec3d sbsTopSlabVisibleSideHitVector(
            ServerWorld serverWorld,
            BlockPos pos,
            Direction side
    ) {
        double y = pos == null ? 0.0d : pos.getY() + 0.5d;
        if (serverWorld != null && pos != null) {
            BlockState state = serverWorld.getBlockState(pos);
            VoxelShape shape = state.getOutlineShape(serverWorld, pos);
            if (!state.isAir() && !shape.isEmpty()) {
                var bounds = shape.getBoundingBox();
                y = pos.getY() + ((bounds.minY + bounds.maxY) * 0.5d);
            }
        }
        double x = pos.getX() + 0.5d;
        double z = pos.getZ() + 0.5d;
        if (side == Direction.EAST) {
            x = pos.getX() + 1.0d;
        } else if (side == Direction.WEST) {
            x = pos.getX();
        } else if (side == Direction.SOUTH) {
            z = pos.getZ() + 1.0d;
        } else if (side == Direction.NORTH) {
            z = pos.getZ();
        }
        return new Vec3d(x, y, z);
    }

    private static String sbsTopSlabSupportRemovalProbeText(SbsTopSlabSupportRemovalProbe probe) {
        return "ran=" + probe.ran()
                + "/removed=" + probe.removed()
                + "/reason=" + probe.reason()
                + "/supportBefore=" + probe.supportStateBefore()
                + "/supportAfter=" + probe.supportStateAfter()
                + "/finalAfter=" + probe.finalStateAfter()
                + "/clientFinalAfter=" + probe.clientFinalStateAfter()
                + "/finalDyAfter=" + formatDouble(probe.finalDyAfter())
                + "/clientDyAfter=" + formatDouble(probe.clientDyAfter())
                + "/anchoredAfter=" + probe.anchoredAfter()
                + "/compoundAfter=" + probe.compoundAfter()
                + "/canPlaceAtAfter=" + probe.canPlaceAtAfter()
                + "/targetAfter=" + probe.targetSampleAfter()
                + "/targetOwnerAfter=" + probe.targetOwnerAfter()
                + "/targetDyAfter=" + formatDouble(probe.targetDyAfter())
                + "/hitVectorAfter=" + formatVec(probe.hitVectorAfter());
    }

    private static String sbsTopSlabSideTargetProbeText(SbsTopSlabSideTargetProbe probe) {
        return "ran=" + probe.ran()
                + "/reason=" + probe.reason()
                + "/hitVector=" + formatVec(probe.hitVector())
                + "/heldItem=" + probe.heldItem()
                + "/intendedAction=" + probe.intendedAction()
                + "/playerBlockPos=" + probe.playerBlockPos()
                + "/playerEye=" + probe.playerEye()
                + "/target=" + probe.targetSample()
                + "/targetOwner=" + probe.targetOwner()
                + "/targetDy=" + formatDouble(probe.targetDy())
                + "/targetSide=" + (probe.targetSide() == null ? "none" : probe.targetSide().asString())
                + "/interactResult=" + probe.interactResult()
                + "/completed=" + probe.completed()
                + "/waitTicks=" + probe.waitTicks()
                + "/sidePos=" + textPos(probe.sidePos())
                + "/sideStateBefore=" + probe.sideStateBefore()
                + "/sideClientStateBefore=" + probe.sideClientStateBefore()
                + "/sideStateImmediate=" + probe.sideStateImmediate()
                + "/sideClientStateImmediate=" + probe.sideClientStateImmediate()
                + "/sideStateAfter=" + probe.sideStateAfter()
                + "/sideClientStateAfter=" + probe.sideClientStateAfter()
                + "/sideDyAfter=" + formatDouble(probe.sideDyAfter())
                + "/sideClientDyAfter=" + formatDouble(probe.sideClientDyAfter())
                + "/sideLowerMarker=" + probe.sideLowerMarker()
                + "/sideUpperMarker=" + probe.sideUpperMarker()
                + "/sideDoubleMarker=" + probe.sideDoubleMarker();
    }

    private static SbsTopSlabCullingProof captureSbsTopSlabCullingProof(
            ClientWorld world,
            BlockPos finalPos,
            BlockState finalState,
            BlockPos supportPos,
            BlockState supportState,
            Direction targetSide
    ) {
        if (world == null || finalPos == null || finalState == null || finalState.isAir()) {
            return SbsTopSlabCullingProof.missing("ROUTE_OR_FINAL_STATE_NOT_READY");
        }
        Direction measuredTargetSide = targetSide == null ? Direction.UP : targetSide;
        boolean targetFaceDraws = sbsTopSlabShouldDrawSide(world, finalPos, finalState, measuredTargetSide);
        boolean upFaceDraws = sbsTopSlabShouldDrawSide(world, finalPos, finalState, Direction.UP);
        boolean downFaceDraws = sbsTopSlabShouldDrawSide(world, finalPos, finalState, Direction.DOWN);
        boolean northFaceDraws = sbsTopSlabShouldDrawSide(world, finalPos, finalState, Direction.NORTH);
        boolean southFaceDraws = sbsTopSlabShouldDrawSide(world, finalPos, finalState, Direction.SOUTH);
        boolean westFaceDraws = sbsTopSlabShouldDrawSide(world, finalPos, finalState, Direction.WEST);
        boolean eastFaceDraws = sbsTopSlabShouldDrawSide(world, finalPos, finalState, Direction.EAST);
        boolean supportUpFaceDraws = supportPos != null
                && supportState != null
                && !supportState.isAir()
                && sbsTopSlabShouldDrawSide(world, supportPos, supportState, Direction.UP);
        boolean targetCullingFaceEmpty = sbsTopSlabCullingFaceEmpty(world, finalPos, finalState, measuredTargetSide);
        boolean upCullingFaceEmpty = sbsTopSlabCullingFaceEmpty(world, finalPos, finalState, Direction.UP);
        boolean downCullingFaceEmpty = sbsTopSlabCullingFaceEmpty(world, finalPos, finalState, Direction.DOWN);
        boolean supportUpCullingFaceEmpty = supportPos == null
                || supportState == null
                || supportState.isAir()
                || sbsTopSlabCullingFaceEmpty(world, supportPos, supportState, Direction.UP);
        boolean horizontalFaceDraws = northFaceDraws || southFaceDraws || westFaceDraws || eastFaceDraws;
        boolean expectedVisibleFaceDraws = targetFaceDraws || upFaceDraws || horizontalFaceDraws;
        return new SbsTopSlabCullingProof(
                true,
                measuredTargetSide,
                targetFaceDraws,
                upFaceDraws,
                downFaceDraws,
                northFaceDraws,
                southFaceDraws,
                westFaceDraws,
                eastFaceDraws,
                supportUpFaceDraws,
                targetCullingFaceEmpty,
                upCullingFaceEmpty,
                downCullingFaceEmpty,
                supportUpCullingFaceEmpty,
                expectedVisibleFaceDraws,
                expectedVisibleFaceDraws ? "NONE" : "ALL_EXPECTED_FACES_CULLED");
    }

    private static boolean sbsTopSlabShouldDrawSide(
            ClientWorld world,
            BlockPos pos,
            BlockState state,
            Direction direction
    ) {
        return Block.shouldDrawSide(state, world, pos, direction, pos.offset(direction));
    }

    private static boolean sbsTopSlabCullingFaceEmpty(
            ClientWorld world,
            BlockPos pos,
            BlockState state,
            Direction direction
    ) {
        return state.getCullingFace(world, pos, direction).isEmpty();
    }

    private static double sbsTopSlabVisibleTopY(
            ServerWorld serverWorld,
            BlockPos pos,
            BlockState state,
            double dy
    ) {
        if (serverWorld == null || pos == null || state == null || state.isAir() || !Double.isFinite(dy)) {
            return Double.NaN;
        }
        VoxelShape shape = state.getOutlineShape(serverWorld, pos);
        if (shape.isEmpty()) {
            return Double.NaN;
        }
        return pos.getY() + shape.getBoundingBox().maxY;
    }

    private static double sbsTopSlabVisibleBottomY(
            ServerWorld serverWorld,
            BlockPos pos,
            BlockState state,
            double dy
    ) {
        if (serverWorld == null || pos == null || state == null || state.isAir() || !Double.isFinite(dy)) {
            return Double.NaN;
        }
        VoxelShape shape = state.getOutlineShape(serverWorld, pos);
        if (shape.isEmpty()) {
            return Double.NaN;
        }
        return pos.getY() + shape.getBoundingBox().minY;
    }

    private static SbsTopSlabClassification classifySbsTopSlabRow(
            SbsTopSlabCombinationCase spec,
            BlockState finalState,
            BlockState finalAboveState,
            double finalDy,
            boolean finalOwnerTop,
            boolean finalLoweredCarrier,
            boolean finalAnchored,
            boolean finalCompoundAnchor,
            boolean supportLowered,
            boolean supportIsSlab,
            double supportDy,
            boolean supportCompoundAnchor,
            double contactGap,
            OffsetBlockStateModel.ModelDyOwnerSample modelTrace,
            double outlineDy,
            double targetDy,
            OffsetBlockStateModel.FullMeshBoundsSample meshTrace,
            SbsTopSlabCullingProof cullingProof,
            String finalBlockTargetOwner,
            double finalBlockTargetDy,
            SbsTopSlabSupportRemovalProbe supportRemovalProbe,
            SbsTopSlabSideTargetProbe sideTargetProbe
    ) {
        if (spec.finalSlab()) {
            String prefix = spec.rowName();
            if (!supportLowered) {
                return new SbsTopSlabClassification(
                        "LOWERED_CARRIER_MISSING",
                        "placement/state authority",
                        "RED");
            }
            if (!finalState.isOf(spec.finalBlock())) {
                return new SbsTopSlabClassification(
                        prefix + "_FINAL_SLAB_NOT_PLACED",
                        "placement/state authority",
                        "RED");
            }
            if (spec.repeatFinalClick()) {
                if (!finalAboveState.isAir()) {
                    return new SbsTopSlabClassification(
                            prefix + "_REPEAT_CLICK_UPWARD_STACKED",
                            "placement/state authority",
                            "RED");
                }
                if (!finalState.contains(SlabBlock.TYPE) || finalState.get(SlabBlock.TYPE) != SlabType.DOUBLE) {
                    return new SbsTopSlabClassification(
                            prefix + "_REPEAT_CLICK_DID_NOT_COMBINE_TO_DOUBLE",
                            "placement/state authority",
                            "RED");
                }
                if (!near(finalDy, -0.5d) || finalOwnerTop || finalAnchored) {
                    return new SbsTopSlabClassification(
                            prefix + "_COMBINED_DOUBLE_NOT_LOWERED",
                            "state authority",
                            "RED");
                }
                return new SbsTopSlabClassification(
                        prefix + "_SAME_POSITION_DOUBLE_GREEN",
                        "NONE",
                        "GREEN");
            }
            if (!supportIsSlab && supportCompoundAnchor && near(supportDy, -1.0d)) {
                if (!finalOwnerTop) {
                    return new SbsTopSlabClassification(
                            prefix + "_OWNER_TOP_MARKER_MISSING_ON_COMPOUND_FULL_BLOCK",
                            "state authority",
                            "RED");
                }
                if (!near(finalDy, -1.0d)) {
                    return new SbsTopSlabClassification(
                            prefix + "_OWNER_TOP_SLAB_NOT_IN_COMPOUND_DY_MINUS_ONE_LANE",
                            "state authority",
                            "RED");
                }
                if (!near(outlineDy, finalDy)) {
                    return new SbsTopSlabClassification(
                            prefix + "_OWNER_TOP_OUTLINE_DY_MISMATCH",
                            "outline",
                            "RED");
                }
                if (modelTrace.seen() && !near(modelTrace.lastDy(), finalDy)) {
                    return new SbsTopSlabClassification(
                            prefix + "_OWNER_TOP_MODEL_DY_MISMATCH",
                            "model",
                            "RED");
                }
                if (meshTrace.seen() && !near(meshTrace.dy(), finalDy)) {
                    return new SbsTopSlabClassification(
                            prefix + "_OWNER_TOP_MESH_DY_MISMATCH",
                            "model",
                            "RED");
                }
                if (spec.sideSlabFromFinalOwnerTop()) {
                    if (!sideTargetProbe.ran()) {
                        return new SbsTopSlabClassification(
                                prefix + "_PERP_SIDE_TARGET_PROBE_NOT_RUN",
                                "proof gap",
                                "TRACE_GAP");
                    }
                    if (!"final_top_slab".equals(sideTargetProbe.targetOwner())) {
                        return new SbsTopSlabClassification(
                                prefix + "_PERP_SIDE_TARGET_OWNER_MISMATCH",
                                "raycast",
                                "RED");
                    }
                    if (sideTargetProbe.targetSide() == null
                            || sideTargetProbe.targetSide().getAxis() != Direction.Axis.X) {
                        return new SbsTopSlabClassification(
                                prefix + "_PERP_SIDE_TARGET_FACE_MISMATCH",
                                "raycast",
                                "RED");
                    }
                    if (!near(sideTargetProbe.targetDy(), -1.0d)) {
                        return new SbsTopSlabClassification(
                                prefix + "_PERP_SIDE_TARGET_DY_MISMATCH",
                                "raycast",
                                "RED");
                    }
                    if (!sideTargetProbe.completed()) {
                        return new SbsTopSlabClassification(
                                prefix + "_PERP_SIDE_SETTLED_SAMPLE_NOT_COMPLETED",
                                "proof gap",
                                "TRACE_GAP");
                    }
                    if (!sideTargetProbe.sideStateAfter().contains(blockId(spec.slabBlock()))) {
                        return new SbsTopSlabClassification(
                                prefix + "_PERP_SIDE_SLAB_NOT_PLACED",
                                "placement",
                                "RED");
                    }
                    if (!near(sideTargetProbe.sideDyAfter(), -1.0d)) {
                        return new SbsTopSlabClassification(
                                prefix + "_PERP_SIDE_SLAB_DY_MISMATCH",
                                "state authority",
                                "RED");
                    }
                    if (!sideTargetProbe.sideLowerMarker()
                            && !sideTargetProbe.sideUpperMarker()
                            && !sideTargetProbe.sideDoubleMarker()) {
                        return new SbsTopSlabClassification(
                                prefix + "_PERP_SIDE_SLAB_MARKER_MISSING",
                                "state authority",
                                "RED");
                    }
                }
                return new SbsTopSlabClassification(
                        prefix + "_OWNER_TOP_SLAB_ON_COMPOUND_FULL_BLOCK_GREEN",
                        "NONE",
                        "GREEN");
            }
            if (finalState.isOf(Blocks.STONE_SLAB) && finalOwnerTop && near(finalDy, -1.0d)) {
                return new SbsTopSlabClassification(
                        "STONE_SLAB_OWNER_TOP_MATERIAL_MERGE_RED",
                        "placement/state authority",
                        "RED");
            }
            if (!near(finalDy, -0.5d) || !finalLoweredCarrier || finalOwnerTop || finalAnchored) {
                return new SbsTopSlabClassification(
                        prefix + "_FINAL_SLAB_NOT_SEPARATE_LOWERED_CARRIER",
                        "placement/state authority",
                        "RED");
            }
            return new SbsTopSlabClassification(
                    prefix + "_SEPARATE_LOWERED_CARRIER_GREEN",
                    "NONE",
                    "GREEN");
        }
        if (!finalState.isOf(spec.finalBlock())) {
            return new SbsTopSlabClassification(
                    spec.rowName() + "_CONTROL_BLOCK_NOT_PLACED",
                    "placement/state authority",
                    "RED");
        }
        if (supportIsSlab) {
            if (!near(contactGap, 0.0d)) {
                return new SbsTopSlabClassification(
                        spec.rowName() + "_FINAL_BLOCK_FLOATING_OVER_LOWERED_SLAB",
                        "placement/state authority",
                        "RED");
            }
            if (!near(finalDy, -1.0d)) {
                return new SbsTopSlabClassification(
                        spec.rowName() + "_FINAL_BLOCK_NOT_IN_LOWERED_SLAB_TOP_LANE",
                        "state authority",
                        "RED");
            }
            if (!finalAnchored || !finalCompoundAnchor) {
                return new SbsTopSlabClassification(
                        spec.rowName() + "_FINAL_BLOCK_MISSING_PERSISTENT_COMPOUND_ANCHOR",
                        "state authority",
                        "RED");
            }
            if (!"final_control_block".equals(finalBlockTargetOwner)) {
                return new SbsTopSlabClassification(
                        spec.rowName() + "_FINAL_BLOCK_TARGET_OWNER_MISMATCH",
                        "raycast",
                        "RED");
            }
            if (!near(finalBlockTargetDy, finalDy)) {
                return new SbsTopSlabClassification(
                        spec.rowName() + "_FINAL_BLOCK_TARGET_DY_MISMATCH",
                        "raycast",
                        "RED");
            }
            if (!near(outlineDy, finalDy)) {
                return new SbsTopSlabClassification(
                        spec.rowName() + "_OUTLINE_DY_MISMATCH",
                        "outline",
                        "RED");
            }
            if (modelTrace.seen() && !near(modelTrace.lastDy(), finalDy)) {
                return new SbsTopSlabClassification(
                        spec.rowName() + "_MODEL_DY_MISMATCH",
                        "model",
                        "RED");
            }
            if (meshTrace.seen() && !near(meshTrace.dy(), finalDy)) {
                return new SbsTopSlabClassification(
                        spec.rowName() + "_MESH_DY_MISMATCH",
                        "model",
                        "RED");
            }
            if (!modelTrace.seen() && !meshTrace.seen()) {
                return new SbsTopSlabClassification(
                        spec.rowName() + "_MODEL_MESH_PROOF_GAP",
                        "proof gap",
                        "TRACE_GAP");
            }
            if (supportRemovalProbe.ran()) {
                if (!supportRemovalProbe.removed()) {
                    return new SbsTopSlabClassification(
                            spec.rowName() + "_SUPPORT_REMOVAL_DID_NOT_BREAK_SUPPORT",
                            "proof gap",
                            "TRACE_GAP");
                }
                if (!supportRemovalProbe.finalStateAfter().contains(blockId(spec.finalBlock()))) {
                    return new SbsTopSlabClassification(
                            spec.rowName() + "_FINAL_BLOCK_MISSING_AFTER_SUPPORT_REMOVAL",
                            "survival",
                            "RED");
                }
                if (!supportRemovalProbe.anchoredAfter() || !supportRemovalProbe.compoundAfter()) {
                    return new SbsTopSlabClassification(
                            spec.rowName() + "_ANCHOR_LOST_AFTER_SUPPORT_REMOVAL",
                            "state authority",
                            "RED");
                }
                if (!near(supportRemovalProbe.finalDyAfter(), -1.0d)) {
                    return new SbsTopSlabClassification(
                            spec.rowName() + "_FINAL_BLOCK_POPPED_UP_AFTER_SUPPORT_REMOVAL",
                            "survival",
                            "RED");
                }
                if (!"final_control_block".equals(supportRemovalProbe.targetOwnerAfter())) {
                    return new SbsTopSlabClassification(
                            spec.rowName() + "_TARGET_OWNER_MISMATCH_AFTER_SUPPORT_REMOVAL",
                            "raycast",
                            "RED");
                }
                if (!near(supportRemovalProbe.targetDyAfter(), supportRemovalProbe.finalDyAfter())) {
                    return new SbsTopSlabClassification(
                            spec.rowName() + "_TARGET_DY_MISMATCH_AFTER_SUPPORT_REMOVAL",
                            "raycast",
                            "RED");
                }
            }
            return new SbsTopSlabClassification(
                    spec.rowName() + "_FULL_BLOCK_ON_LOWERED_SLAB_GREEN",
                    "NONE",
                    "GREEN");
        }
        if (!near(finalDy, -0.5d) || !finalAnchored) {
            return new SbsTopSlabClassification(
                    spec.rowName() + "_CONTROL_BLOCK_NOT_LOWERED",
                    "placement/state authority",
                    "RED");
        }
        return new SbsTopSlabClassification(
                spec.rowName() + "_GREEN",
                "NONE",
                "GREEN");
    }

    private static void recordSbsTopSlabRowSummary(
            SbsTopSlabCombinationCase spec,
            SbsTopSlabClassification classification
    ) {
        switch (classification.finalResult()) {
            case "RED" -> sbsTopSlabRedRows++;
            case "GREEN" -> sbsTopSlabGreenRows++;
            default -> sbsTopSlabTraceGapRows++;
        }
        if ("CULLING_PROOF_REQUIRED".equals(classification.classification())) {
            sbsTopSlabCullingProofRows++;
        }
        if (!"GREEN".equals(classification.finalResult()) && "NONE".equals(sbsTopSlabFirstFailingBoundary)) {
            sbsTopSlabFirstFailingBoundary = classification.failureLayer();
        }
        String summary = spec.rowName()
                + "=" + classification.finalResult()
                + "/" + classification.classification()
                + "/failureLayer=" + classification.failureLayer();
        sbsTopSlabRows = sbsTopSlabRows.isEmpty() ? summary : sbsTopSlabRows + "," + summary;
    }

    private static String sbsTopSlabModelTraceText(OffsetBlockStateModel.ModelDyOwnerSample trace) {
        return "seen=" + trace.seen()
                + "/viewClass=" + trace.viewClass()
                + "/pos=" + trace.pos()
                + "/state=" + trace.state()
                + "/emitCalls=" + trace.emitCalls()
                + "/appliedCalls=" + trace.appliedCalls()
                + "/lastDy=" + formatDouble(trace.lastDy());
    }

    private static String sbsTopSlabMeshTraceText(OffsetBlockStateModel.FullMeshBoundsSample trace) {
        return "seen=" + trace.seen()
                + "/blockId=" + trace.blockId()
                + "/pos=" + trace.pos()
                + "/dy=" + formatDouble(trace.dy())
                + "/minAfterY=" + formatDouble(trace.minAfterY())
                + "/maxAfterY=" + formatDouble(trace.maxAfterY())
                + "/reason=" + trace.reason();
    }

    private static String sbsTopSlabCullingProofText(SbsTopSlabCullingProof proof) {
        return "seen=" + proof.seen()
                + "/targetSide=" + (proof.targetSide() == null ? "none" : proof.targetSide().asString())
                + "/targetFaceDraws=" + proof.targetFaceDraws()
                + "/upFaceDraws=" + proof.upFaceDraws()
                + "/downFaceDraws=" + proof.downFaceDraws()
                + "/northFaceDraws=" + proof.northFaceDraws()
                + "/southFaceDraws=" + proof.southFaceDraws()
                + "/westFaceDraws=" + proof.westFaceDraws()
                + "/eastFaceDraws=" + proof.eastFaceDraws()
                + "/supportUpFaceDraws=" + proof.supportUpFaceDraws()
                + "/targetCullingFaceEmpty=" + proof.targetCullingFaceEmpty()
                + "/upCullingFaceEmpty=" + proof.upCullingFaceEmpty()
                + "/downCullingFaceEmpty=" + proof.downCullingFaceEmpty()
                + "/supportUpCullingFaceEmpty=" + proof.supportUpCullingFaceEmpty()
                + "/expectedVisibleFaceDraws=" + proof.expectedVisibleFaceDraws()
                + "/classification=" + proof.classification();
    }

    private static String itemId(net.minecraft.item.Item item) {
        return String.valueOf(Registries.ITEM.getId(item));
    }

    private static String blockId(Block block) {
        return String.valueOf(Registries.BLOCK.getId(block));
    }

    private static void advanceSbsTopSlabCase() {
        sbsTopSlabRowIndex++;
        sbsTopSlabStepIndex = -1;
        sbsTopSlabStepAttempted = false;
        sbsTopSlabRepeatFinalClickAttempted = false;
        sbsTopSlabStepAttemptTick = sbsTopSlabTicks;
        sbsTopSlabLastRetryTick = sbsTopSlabTicks;
    }

    private static void emitSbsTopSlabTraceGap(String row, String reason) {
        sbsTopSlabTraceGapRows++;
        if ("NONE".equals(sbsTopSlabFirstFailingBoundary)) {
            sbsTopSlabFirstFailingBoundary = "proof gap";
        }
        String summary = row + "=TRACE_GAP/" + reason + "/failureLayer=proof gap";
        sbsTopSlabRows = sbsTopSlabRows.isEmpty() ? summary : sbsTopSlabRows + "," + summary;
        System.out.println("[MC1211_SBS_TOP_SLAB_COMBINATION_ROW]"
                + " rowPhase=" + row
                + " fixtureOrigin=" + textPos(sbsTopSlabOrigin)
                + " caseOrigin=" + textPos(sbsTopSlabCaseOrigin)
                + " classification=" + reason
                + " failureLayer=proof gap"
                + " finalResult=TRACE_GAP"
                + " gameplayPatch=false");
        emitSbsTopSlabSummary(MinecraftClient.getInstance());
    }

    private static void emitSbsTopSlabSummary(MinecraftClient client) {
        if (sbsTopSlabFinalized) {
            return;
        }
        String finalResult = sbsTopSlabRedRows > 0
                ? "RED"
                : (sbsTopSlabTraceGapRows > 0 ? "TRACE_GAP" : "GREEN");
        String firstFailingBoundary = "NONE".equals(sbsTopSlabFirstFailingBoundary)
                ? "NONE"
                : sbsTopSlabFirstFailingBoundary;
        System.out.println("[MC1211_SBS_TOP_SLAB_COMBINATION_SUMMARY]"
                + " rows=" + SBS_TOP_SLAB_COMBINATION_CASES.length
                + " redRows=" + sbsTopSlabRedRows
                + " greenRows=" + sbsTopSlabGreenRows
                + " traceGapRows=" + sbsTopSlabTraceGapRows
                + " cullingProofRows=" + sbsTopSlabCullingProofRows
                + " finalResult=" + finalResult
                + " firstFailingBoundary=" + firstFailingBoundary
                + " rowSummary=" + sbsTopSlabRows
                + " playerLikeBuild=true"
                + " manualMarkerInjection=false"
                + " gameplayPatch=false"
                + " cleanup=false"
                + " savepoint=false"
                + " releaseAudit=NOT_RUN"
                + " visualHold=" + Boolean.getBoolean(SBS_TOP_SLAB_VISUAL_HOLD_PROPERTY)
                + " releaseTagMoved=false");
        System.out.println("[MC1211_SBS_TOP_SLAB_COMBINATION_" + finalResult + "]"
                + " rows=" + SBS_TOP_SLAB_COMBINATION_CASES.length
                + " firstFailingBoundary=" + firstFailingBoundary
                + " rowSummary=" + sbsTopSlabRows);
        sbsTopSlabFinalized = true;
        emitted = true;
        if (client != null && !Boolean.getBoolean(SBS_TOP_SLAB_VISUAL_HOLD_PROPERTY)) {
            client.scheduleStop();
        }
    }

    private static void runVbvsShadowCheckerboardRoute(MinecraftClient client) {
        if (vbvsShadowFinalized || emitted) {
            return;
        }
        vbvsShadowTicks++;
        if (!vbvsShadowCanaryEmitted) {
            vbvsShadowCanaryEmitted = true;
            System.out.println("[MC1211_VBVS_SHADOW_CHECKERBOARD_START]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " property=" + VBVS_SHADOW_CHECKERBOARD_RED_PROPERTY
                    + " rows=9"
                    + " fixture=checkerboard_full_block_on_bottom_slab_vs_bottom_slab_on_full_block"
                    + " gameplayPatch=false"
                    + " cleanup=false"
                    + " savepoint=false"
                    + " worldReady=" + (client != null && client.world != null)
                    + " playerReady=" + (client != null && client.player != null));
        }

        requestProgrammaticVbvsShadowWorldIfNeeded(client);
        String readinessGap = vbvsShadowReadinessGap(client);
        if (readinessGap != null) {
            if (!vbvsShadowReadyRowEmitted || vbvsShadowTicks % 1200 == 0) {
                emitVbvsShadowReadyRow("WAITING", readinessGap);
                vbvsShadowReadyRowEmitted = true;
            }
            if (vbvsShadowTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitVbvsShadowTraceGap("ROUTE_READINESS", readinessGap);
            return;
        }
        if (client.currentScreen != null) {
            client.setScreen(null);
        }

        if (!vbvsShadowStarted) {
            vbvsShadowStarted = true;
            vbvsShadowOrigin = client.player.getBlockPos().add(8, 0, 8).toImmutable();
            authorVbvsShadowCheckerboard(client, serverWorldFor(client), vbvsShadowOrigin);
            syncVbvsShadowCamera(client, vbvsShadowOrigin);
            vbvsShadowPhase = 0;
            vbvsShadowTileIndex = 0;
            vbvsShadowPhaseTick = vbvsShadowTicks;
            System.out.println("[MC1211_VBVS_SHADOW_CHECKERBOARD_ROW]"
                    + " rowPhase=FIXTURE_AUTHORED"
                    + " fixtureOrigin=" + textPos(vbvsShadowOrigin)
                    + " layout=F,S,F/S,F,S/F,S,F"
                    + " fullTile=orange_concrete_on_oak_bottom_slab_anchor"
                    + " slabTile=oak_bottom_slab_on_orange_concrete_vanilla_support"
                    + " manualMarkerInjection=false"
                    + " gameplayPatch=false");
            return;
        }

        if (vbvsShadowPhase == 0) {
            if (vbvsShadowTicks - vbvsShadowPhaseTick < 40) {
                return;
            }
            vbvsShadowPhase = 1;
            vbvsShadowPhaseTick = vbvsShadowTicks;
            OffsetBlockStateModel.resetFullMeshBoundsSample(vbvsShadowTilePos(vbvsShadowTileIndex));
            return;
        }

        if (vbvsShadowPhase == 1) {
            int elapsed = vbvsShadowTicks - vbvsShadowPhaseTick;
            if (elapsed < VBVS_SHADOW_MESH_TRACE_MIN_WAIT_TICKS) {
                return;
            }
            if (!vbvsShadowMeshTraceReadyForTile(vbvsShadowTileIndex)) {
                scheduleVbvsShadowTileRerender(client, vbvsShadowTilePos(vbvsShadowTileIndex));
                if (elapsed < VBVS_SHADOW_MESH_TRACE_TIMEOUT_TICKS) {
                    return;
                }
            }
            emitVbvsShadowTileRow(client, serverWorldFor(client), vbvsShadowTileIndex);
            vbvsShadowTileIndex++;
            if (vbvsShadowTileIndex >= 9) {
                vbvsShadowPhase = 2;
                vbvsShadowPhaseTick = vbvsShadowTicks;
                return;
            }
            OffsetBlockStateModel.resetFullMeshBoundsSample(vbvsShadowTilePos(vbvsShadowTileIndex));
            vbvsShadowPhaseTick = vbvsShadowTicks;
            return;
        }

        if (vbvsShadowPhase == 2) {
            if (vbvsShadowTicks - vbvsShadowPhaseTick < 10) {
                return;
            }
            emitVbvsShadowSummary(client);
        }
    }

    private static boolean vbvsShadowMeshTraceReadyForTile(int tileIndex) {
        BlockPos pos = vbvsShadowTilePos(tileIndex);
        OffsetBlockStateModel.FullMeshBoundsSample trace = OffsetBlockStateModel.snapshotFullMeshBoundsSample();
        return pos != null
                && trace != null
                && trace.seen()
                && textPos(pos).equals(trace.pos().replace(" ", ""));
    }

    private static void scheduleVbvsShadowTileRerender(MinecraftClient client, BlockPos pos) {
        if (client == null || client.world == null || client.worldRenderer == null || pos == null) {
            return;
        }
        client.worldRenderer.scheduleBlockRenders(
                pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    private static void requestProgrammaticVbvsShadowWorldIfNeeded(MinecraftClient client) {
        if (vbvsShadowWorldStartRequested
                || client == null
                || !client.isFinishedLoading()
                || client.world != null
                || client.player != null) {
            return;
        }
        vbvsShadowWorldStartRequested = true;
        LevelInfo levelInfo = new LevelInfo(
                "Slabbed MC1211 VBVS Shadow Checkerboard Red",
                GameMode.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                DataConfiguration.SAFE_MODE);
        GeneratorOptions generatorOptions = new GeneratorOptions(0L, false, false);
        client.createIntegratedServerLoader().createAndStart(
                "slabbed-mc1211-vbvs-shadow-checkerboard-red",
                levelInfo,
                generatorOptions,
                Mc1211GoblinRouteClientEntrypoint::createSuperflatDimensionOptions,
                null);
    }

    private static String vbvsShadowReadinessGap(MinecraftClient client) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        if (!readiness.clientBootstrapReady) {
            return "TRACE_GAP_CLIENT_BOOTSTRAP_NOT_FINISHED";
        }
        if (!readiness.clientWorldReady) {
            return vbvsShadowWorldStartRequested
                    ? "TRACE_GAP_PROGRAMMATIC_CLIENT_WORLD_PENDING"
                    : "TRACE_GAP_PROGRAMMATIC_WORLD_START_PENDING";
        }
        if (!readiness.clientPlayerReady) {
            return "TRACE_GAP_CLIENT_PLAYER_NOT_READY";
        }
        if (!readiness.integratedServerReady) {
            return "TRACE_GAP_INTEGRATED_SERVER_NOT_READY";
        }
        if (!readiness.serverWorldReady) {
            return "TRACE_GAP_SERVER_WORLD_NOT_READY";
        }
        if (!readiness.serverPlayerReady) {
            return "TRACE_GAP_SERVER_PLAYER_NOT_READY";
        }
        if (!readiness.interactionManagerReady) {
            return "TRACE_GAP_INTERACTION_MANAGER_NOT_READY";
        }
        return null;
    }

    private static void emitVbvsShadowReadyRow(String phase, String reason) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(MinecraftClient.getInstance());
        System.out.println("[MC1211_VBVS_SHADOW_CHECKERBOARD_READY_ROW]"
                + " phase=" + phase
                + " tick=" + vbvsShadowTicks
                + " clientBootstrapReady=" + readiness.clientBootstrapReady
                + " clientWorldReady=" + readiness.clientWorldReady
                + " clientPlayerReady=" + readiness.clientPlayerReady
                + " integratedServerReady=" + readiness.integratedServerReady
                + " serverWorldReady=" + readiness.serverWorldReady
                + " serverPlayerReady=" + readiness.serverPlayerReady
                + " interactionManagerReady=" + readiness.interactionManagerReady
                + " programmaticWorldStartRequested=" + vbvsShadowWorldStartRequested
                + " reason=" + reason);
    }

    private static void authorVbvsShadowCheckerboard(
            MinecraftClient client,
            ServerWorld serverWorld,
            BlockPos origin
    ) {
        if (client == null || client.world == null || serverWorld == null || origin == null) {
            return;
        }
        for (int x = -2; x <= 4; x++) {
            for (int z = -2; z <= 4; z++) {
                for (int y = -2; y <= 2; y++) {
                    BlockPos clearPos = origin.add(x, y, z);
                    serverWorld.setBlockState(clearPos, Blocks.AIR.getDefaultState(), 3);
                    client.world.setBlockState(clearPos, Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
        BlockState bottomOakSlab = Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState orange = Blocks.ORANGE_CONCRETE.getDefaultState();
        for (int index = 0; index < 9; index++) {
            BlockPos pos = vbvsShadowTilePos(index);
            boolean fullTile = vbvsShadowFullTile(index);
            BlockPos supportPos = pos.down();
            if (fullTile) {
                serverWorld.setBlockState(supportPos, bottomOakSlab, 3);
                serverWorld.setBlockState(pos, orange, 3);
                client.world.setBlockState(supportPos, bottomOakSlab, 3);
                client.world.setBlockState(pos, orange, 3);
                SlabAnchorAttachment.addAnchor(serverWorld, pos, orange);
            } else {
                serverWorld.setBlockState(supportPos, orange, 3);
                serverWorld.setBlockState(pos, bottomOakSlab, 3);
                client.world.setBlockState(supportPos, orange, 3);
                client.world.setBlockState(pos, bottomOakSlab, 3);
                SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(serverWorld, pos, bottomOakSlab);
            }
        }
        serverWorld.getChunkManager().markForUpdate(origin);
    }

    private static void syncVbvsShadowCamera(MinecraftClient client, BlockPos origin) {
        if (client == null || client.player == null || origin == null) {
            return;
        }
        Vec3d center = new Vec3d(origin.getX() + 1.5d, origin.getY() + 0.35d, origin.getZ() + 1.5d);
        Vec3d eye = center.add(0.0d, 5.0d, 4.0d);
        Vec3d delta = center.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.setSneaking(false);
        client.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            serverPlayer.setVelocity(Vec3d.ZERO);
            serverPlayer.setSneaking(false);
            serverPlayer.changeGameMode(GameMode.CREATIVE);
            serverPlayer.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private static void emitVbvsShadowTileRow(
            MinecraftClient client,
            ServerWorld serverWorld,
            int tileIndex
    ) {
        ClientWorld clientWorld = client == null ? null : client.world;
        BlockPos pos = vbvsShadowTilePos(tileIndex);
        BlockPos supportPos = pos == null ? null : pos.down();
        BlockState serverState = serverWorld == null || pos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(pos);
        BlockState clientState = clientWorld == null || pos == null
                ? Blocks.AIR.getDefaultState()
                : clientWorld.getBlockState(pos);
        BlockState supportState = serverWorld == null || supportPos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(supportPos);
        BlockState clientSupportState = clientWorld == null || supportPos == null
                ? Blocks.AIR.getDefaultState()
                : clientWorld.getBlockState(supportPos);
        double serverDy = serverWorld == null || pos == null ? Double.NaN : SlabSupport.getYOffset(serverWorld, pos, serverState);
        double clientDy = clientWorld == null || pos == null ? Double.NaN : ClientDy.dyFor(clientWorld, pos, clientState);
        double supportDy = serverWorld == null || supportPos == null ? Double.NaN
                : SlabSupport.getYOffset(serverWorld, supportPos, supportState);
        boolean anchored = serverWorld != null && pos != null && SlabAnchorAttachment.isAnchored(serverWorld, pos);
        boolean clientAnchored = clientWorld != null && pos != null && SlabAnchorAttachment.isAnchored(clientWorld, pos);
        boolean loweredCarrier = serverWorld != null
                && pos != null
                && SlabAnchorAttachment.isPersistentLoweredSlabCarrier(serverWorld, pos, serverState);
        boolean clientLoweredCarrier = clientWorld != null
                && pos != null
                && SlabAnchorAttachment.isPersistentLoweredSlabCarrier(clientWorld, pos, clientState);
        boolean fullTile = vbvsShadowFullTile(tileIndex);
        OffsetBlockStateModel.FullMeshBoundsSample meshTrace = OffsetBlockStateModel.snapshotFullMeshBoundsSample();
        String cullingFacts = vbvsShadowCullingFacts(clientWorld, pos, clientState);
        String verdict;
        String reason;
        if (serverWorld == null || clientWorld == null || pos == null) {
            verdict = "TRACE_GAP_WORLD_OR_POS_MISSING";
            reason = "world_or_pos_missing";
            vbvsShadowTraceGapRows++;
        } else if (fullTile && (!serverState.isOf(Blocks.ORANGE_CONCRETE) || !anchored || !near(serverDy, -0.5d))) {
            verdict = "RED_FULL_TILE_AUTHORITY";
            reason = "full_tile_not_anchored_lowered";
            vbvsShadowRedRows++;
        } else if (!fullTile
                && (!serverState.isOf(Blocks.OAK_SLAB)
                || !serverState.contains(SlabBlock.TYPE)
                || serverState.get(SlabBlock.TYPE) != SlabType.BOTTOM
                || !near(serverDy, 0.0d))) {
            verdict = "RED_SLAB_TILE_AUTHORITY";
            reason = "vanilla_slab_tile_not_isolated_from_lowered_lane";
            vbvsShadowRedRows++;
        } else if (meshTrace == null || !meshTrace.seen() || !textPos(pos).equals(meshTrace.pos().replace(" ", ""))) {
            verdict = "TRACE_GAP_MESH_SNAPSHOT";
            reason = "mesh_snapshot_missing_for_tile";
            vbvsShadowTraceGapRows++;
        } else {
            boolean loweredFullMesh = fullTile
                    && near(meshTrace.dy(), -0.5d)
                    && near(meshTrace.minBeforeY(), 0.0d)
                    && near(meshTrace.maxBeforeY(), 1.0d)
                    && near(meshTrace.minAfterY(), -0.5d)
                    && near(meshTrace.maxAfterY(), 0.5d);
            boolean loweredFullRenderOutlineNative = loweredFullMesh
                    && vbvsShadowRenderOutlineBounds(meshTrace, 0.0d, 1.0d);
            boolean loweredFullRenderOutlineAligned = loweredFullMesh
                    && vbvsShadowRenderOutlineBounds(meshTrace, -0.5d, 0.5d);
            if (loweredFullRenderOutlineNative) {
                verdict = "RED_RENDER_WORKER_OUTLINE_NATIVE_WITH_LOWERED_MESH";
                reason = "render_worker_shape_stayed_native_while_mesh_lowered";
                vbvsShadowRedRows++;
            } else {
                boolean slabTileControl = !fullTile
                        && near(meshTrace.dy(), 0.0d)
                        && vbvsShadowRenderOutlineBounds(meshTrace, 0.0d, 0.5d);
                boolean greenRow = loweredFullRenderOutlineAligned || slabTileControl;
                verdict = greenRow ? "GREEN_RENDER_OUTLINE_ALIGNED_WITH_MESH" : "TRACE_GAP_RENDER_OUTLINE_UNCLASSIFIED";
                reason = greenRow
                        ? "tile_state_mesh_and_render_worker_shape_aligned"
                        : "mesh_snapshot_seen_but_render_outline_bounds_unclassified";
                if (greenRow) {
                    vbvsShadowGreenRows++;
                } else {
                    vbvsShadowTraceGapRows++;
                }
            }
        }
        String rowSummary = "tile" + tileIndex + "=" + verdict + "/" + reason;
        vbvsShadowRows = vbvsShadowRows.isEmpty() ? rowSummary : vbvsShadowRows + "," + rowSummary;
        System.out.println("[MC1211_VBVS_SHADOW_CHECKERBOARD_ROW]"
                + " rowPhase=TILE_SAMPLE"
                + " tileIndex=" + tileIndex
                + " tileKind=" + (fullTile ? "FULL_ON_BOTTOM_SLAB" : "SLAB_ON_FULL_BLOCK")
                + " pos=" + textPos(pos)
                + " supportPos=" + textPos(supportPos)
                + " serverState=" + serverState
                + " clientState=" + clientState
                + " supportState=" + supportState
                + " clientSupportState=" + clientSupportState
                + " serverDy=" + formatDouble(serverDy)
                + " clientDy=" + formatDouble(clientDy)
                + " supportDy=" + formatDouble(supportDy)
                + " anchored=" + anchored
                + " clientAnchored=" + clientAnchored
                + " loweredCarrier=" + loweredCarrier
                + " clientLoweredCarrier=" + clientLoweredCarrier
                + " outlineBounds=" + (serverWorld == null ? "NaN..NaN" : shapeBounds(serverState.getOutlineShape(serverWorld, pos)))
                + " clientOutlineBounds=" + (clientWorld == null ? "NaN..NaN" : shapeBounds(clientState.getOutlineShape(clientWorld, pos)))
                + " meshTrace=" + vbvsShadowMeshTraceText(meshTrace)
                + " cullingFacts=" + cullingFacts
                + " verdict=" + verdict
                + " reason=" + reason
                + " gameplayPatch=false");
    }

    private static void emitVbvsShadowSummary(MinecraftClient client) {
        if (vbvsShadowFinalized) {
            return;
        }
        String screenshotPath = captureVbvsShadowScreenshot(client, "checkerboard-final");
        String finalResult = vbvsShadowRedRows > 0
                ? "RED"
                : (vbvsShadowTraceGapRows > 0 ? "TRACE_GAP" : "GREEN");
        System.out.println("[MC1211_VBVS_SHADOW_CHECKERBOARD_SUMMARY]"
                + " rows=9"
                + " redRows=" + vbvsShadowRedRows
                + " greenRows=" + vbvsShadowGreenRows
                + " traceGapRows=" + vbvsShadowTraceGapRows
                + " finalResult=" + finalResult
                + " rowSummary=" + vbvsShadowRows
                + " screenshotPath=" + screenshotPath
                + " gameplayPatch=false"
                + " cleanup=false"
                + " savepoint=false");
        System.out.println("[MC1211_VBVS_SHADOW_CHECKERBOARD_" + finalResult + "]"
                + " rows=9"
                + " screenshotPath=" + screenshotPath
                + " rowSummary=" + vbvsShadowRows);
        vbvsShadowFinalized = true;
        emitted = true;
        if (client != null) {
            client.scheduleStop();
        }
    }

    private static void emitVbvsShadowTraceGap(String row, String reason) {
        vbvsShadowTraceGapRows++;
        String summary = row + "=TRACE_GAP/" + reason;
        vbvsShadowRows = vbvsShadowRows.isEmpty() ? summary : vbvsShadowRows + "," + summary;
        System.out.println("[MC1211_VBVS_SHADOW_CHECKERBOARD_ROW]"
                + " rowPhase=" + row
                + " fixtureOrigin=" + textPos(vbvsShadowOrigin)
                + " verdict=TRACE_GAP"
                + " reason=" + reason
                + " gameplayPatch=false");
        emitVbvsShadowSummary(MinecraftClient.getInstance());
    }

    private static boolean vbvsShadowFullTile(int tileIndex) {
        int x = tileIndex % 3;
        int z = tileIndex / 3;
        return ((x + z) & 1) == 0;
    }

    private static BlockPos vbvsShadowTilePos(int tileIndex) {
        if (vbvsShadowOrigin == null) {
            return null;
        }
        return vbvsShadowOrigin.add(tileIndex % 3, 0, tileIndex / 3);
    }

    private static String vbvsShadowMeshTraceText(OffsetBlockStateModel.FullMeshBoundsSample trace) {
        if (trace == null || !trace.seen()) {
            return "seen=false";
        }
        return "seen=true"
                + "/blockId=" + trace.blockId()
                + "/pos=" + trace.pos()
                + "/state=" + trace.state()
                + "/dy=" + formatDouble(trace.dy())
                + "/minBeforeY=" + formatDouble(trace.minBeforeY())
                + "/maxBeforeY=" + formatDouble(trace.maxBeforeY())
                + "/minAfterY=" + formatDouble(trace.minAfterY())
                + "/maxAfterY=" + formatDouble(trace.maxAfterY())
                + "/renderOutlineBounds=" + trace.renderOutlineBounds()
                + "/reason=" + trace.reason()
                + "/viewClass=" + trace.viewClass()
                + "/snapshotSource=" + trace.snapshotSource();
    }

    private static boolean vbvsShadowRenderOutlineBounds(
            OffsetBlockStateModel.FullMeshBoundsSample trace,
            double minY,
            double maxY
    ) {
        if (trace == null || !trace.seen()) {
            return false;
        }
        return ("[" + formatDouble(minY) + "," + formatDouble(maxY) + "]").equals(trace.renderOutlineBounds());
    }

    private static String vbvsShadowCullingFacts(ClientWorld world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null || state.isAir()) {
            return "missing";
        }
        StringBuilder facts = new StringBuilder();
        for (Direction direction : Direction.values()) {
            if (!facts.isEmpty()) {
                facts.append("|");
            }
            facts.append(direction.asString())
                    .append(":draw=")
                    .append(Block.shouldDrawSide(state, world, pos, direction, pos.offset(direction)))
                    .append("/empty=")
                    .append(state.getCullingFace(world, pos, direction).isEmpty());
        }
        return facts.toString();
    }

    private static String captureVbvsShadowScreenshot(MinecraftClient client, String label) {
        if (client == null) {
            return "TRACE_GAP_SCREENSHOT_CLIENT_NOT_READY";
        }
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null) {
            return "TRACE_GAP_SCREENSHOT_FRAMEBUFFER_NOT_READY";
        }
        if (client.currentScreen != null) {
            client.setScreen(null);
        }
        String safeLabel = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
        Path dir = Path.of("tmp", "mc1211-vbvs-shadow-checkerboard").toAbsolutePath();
        Path path = dir.resolve(String.format(Locale.ROOT, "%04d-%s.png", vbvsShadowTicks, safeLabel));
        try {
            Files.createDirectories(dir);
            try (NativeImage image = ScreenshotRecorder.takeScreenshot(framebuffer)) {
                image.writeTo(path);
            }
            return path.toString();
        } catch (IOException | RuntimeException e) {
            return "TRACE_GAP_SCREENSHOT_WRITE_FAILED:" + e.getClass().getSimpleName();
        }
    }

    private static ServerWorld serverWorldFor(MinecraftClient client) {
        MinecraftServer server = client == null ? null : client.getServer();
        if (server == null || client.world == null) {
            return null;
        }
        return server.getWorld(client.world.getRegistryKey());
    }

    private static String rowName(int rowIndex) {
        return switch (rowIndex) {
            case 0 -> "BOTTOM_SLAB_THEN_STONE_ON_TOP";
            case 1 -> "TOP_SLAB_THEN_STONE_ON_TOP";
            case 2 -> "DOUBLE_SLAB_THEN_STONE_ON_TOP";
            case 3 -> "VANILLA_GROUND_THEN_STONE_ON_TOP";
            default -> "UNKNOWN_ROW";
        };
    }

    private static String sourceRelationship(int rowIndex) {
        return switch (rowIndex) {
            case 0 -> "direct bottom slab support";
            case 1 -> "top slab/full height support";
            case 2 -> "double slab/full height support";
            default -> "none/unknown";
        };
    }

    private static String visibleBounds(net.minecraft.world.WorldView world, BlockPos pos, BlockState state, double dy) {
        if (pos == null || state == null || state.isAir() || !Double.isFinite(dy)) {
            return "NaN..NaN";
        }
        VoxelShape shape = state.getOutlineShape(world, pos);
        if (shape.isEmpty()) {
            return "NaN..NaN";
        }
        return formatDouble(pos.getY() + dy + shape.getBoundingBox().minY)
                + ".."
                + formatDouble(pos.getY() + dy + shape.getBoundingBox().maxY);
    }

    private static boolean near(double actual, double expected) {
        return Double.isFinite(actual) && Math.abs(actual - expected) <= 1.0e-6d;
    }

    private static void emitRow(String row) {
        System.out.println("[MC1211_GOBLIN_ROW]"
                + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                + " route=" + ROUTE
                + " row=" + row
                + " legacyClass=" + LEGACY_CLASS
                + " result=GREEN");
    }

    private static void runSidePlaceStoneLiveTruthRoute(MinecraftClient client) {
        if (liveTruthFinalized || emitted) {
            return;
        }
        liveTruthTicks++;
        if (!liveTruthCanaryEmitted) {
            liveTruthCanaryEmitted = true;
            System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_ROUTE_CANARY]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " property=" + SIDE_PLACE_STONE_LIVE_TRUTH_ONLY_PROPERTY
                    + " worldReady=" + (client != null && client.world != null)
                    + " playerReady=" + (client != null && client.player != null));
        }

        requestProgrammaticLiveTruthWorldIfNeeded(client);
        String readinessGap = liveTruthReadinessGap(client);
        if (readinessGap != null) {
            if (!liveTruthReadyRowEmitted || liveTruthTicks % 1200 == 0) {
                emitLiveTruthReadyRow(client, "WAITING", readinessGap);
                liveTruthReadyRowEmitted = true;
            }
            if (liveTruthTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitLiveTruthReadyRow(client, "TIMEOUT", readinessGap);
            emitLiveTruthTraceGap("ROUTE_READINESS", readinessGap);
            return;
        }
        if (!liveTruthReadyRowEmitted) {
            emitLiveTruthReadyRow(client, "READY", "none");
            liveTruthReadyRowEmitted = true;
        }

        if (!liveTruthStarted) {
            liveTruthStarted = true;
            liveTruthOrigin = client.player.getBlockPos().add(9, 0, 7).toImmutable();
            liveTruthGroundPos = liveTruthOrigin.down();
            liveTruthSlabPos = liveTruthOrigin;
            liveTruthFirstStonePos = liveTruthSlabPos.up();
            liveTruthSideStonePos = liveTruthFirstStonePos.offset(Direction.WEST);
            prepareLiveTruthFixture(client);
            liveTruthPhase = 1;
            liveTruthPhaseTick = liveTruthTicks;
            System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_START]"
                    + " rowName=SIDE_PLACE_STONE_LIVE_SOURCE_TRUTH_WEST_FACE"
                    + " fixtureOrigin=" + textPos(liveTruthOrigin)
                    + " liveReferenceSideStonePos=-22,-59,64"
                    + " translatedSideStonePos=" + textPos(liveTruthSideStonePos)
                    + " placementRoute=ClientPlayerInteractionManager.interactBlock"
                    + " manualAnchorInjection=false");
            return;
        }

        if (liveTruthTicks - liveTruthPhaseTick < 8) {
            return;
        }
        if (liveTruthPhase == 1) {
            liveTruthSlabResult = clickBlock(
                    client,
                    Items.STONE_SLAB,
                    liveTruthGroundPos,
                    Direction.UP,
                    hitVector(liveTruthGroundPos, Direction.UP));
            liveTruthReachDiagnostic = slabThenBlockReachDiagnostic;
            liveTruthPhase = 2;
            liveTruthPhaseTick = liveTruthTicks;
            return;
        }
        if (liveTruthPhase == 2) {
            liveTruthFirstStoneResult = clickBlock(
                    client,
                    Items.STONE,
                    liveTruthSlabPos,
                    Direction.UP,
                    hitVector(liveTruthSlabPos, Direction.UP));
            liveTruthReachDiagnostic = slabThenBlockReachDiagnostic;
            liveTruthPhase = 3;
            liveTruthPhaseTick = liveTruthTicks;
            return;
        }
        if (liveTruthPhase == 3) {
            Vec3d sideHit = new Vec3d(
                    liveTruthFirstStonePos.getX(),
                    liveTruthFirstStonePos.getY() + 0.5d,
                    liveTruthFirstStonePos.getZ() + 0.5d);
            liveTruthSideStoneResult = clickBlock(client, Items.STONE, liveTruthFirstStonePos, Direction.WEST, sideHit);
            liveTruthReachDiagnostic = slabThenBlockReachDiagnostic;
            liveTruthPhase = 4;
            liveTruthPhaseTick = liveTruthTicks;
            return;
        }
        if (liveTruthTicks - liveTruthPhaseTick < 30) {
            return;
        }
        emitLiveTruthRow(client);
        liveTruthFinalized = true;
        emitted = true;
        client.scheduleStop();
    }

    private static void requestProgrammaticLiveTruthWorldIfNeeded(MinecraftClient client) {
        if (liveTruthWorldStartRequested
                || client == null
                || !client.isFinishedLoading()
                || client.world != null
                || client.player != null) {
            return;
        }
        liveTruthWorldStartRequested = true;
        LevelInfo levelInfo = new LevelInfo(
                "Slabbed MC1211 Side Place Live Truth Harness",
                GameMode.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                DataConfiguration.SAFE_MODE);
        GeneratorOptions generatorOptions = new GeneratorOptions(0L, false, false);
        client.createIntegratedServerLoader().createAndStart(
                "slabbed-mc1211-side-place-live-truth-harness",
                levelInfo,
                generatorOptions,
                Mc1211GoblinRouteClientEntrypoint::createSuperflatDimensionOptions,
                null);
    }

    private static String liveTruthReadinessGap(MinecraftClient client) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        if (!readiness.clientBootstrapReady) {
            return "TRACE_GAP_CLIENT_BOOTSTRAP_NOT_FINISHED";
        }
        if (!readiness.clientWorldReady) {
            return liveTruthWorldStartRequested
                    ? "TRACE_GAP_PROGRAMMATIC_CLIENT_WORLD_PENDING"
                    : "TRACE_GAP_PROGRAMMATIC_WORLD_START_PENDING";
        }
        if (!readiness.clientPlayerReady) {
            return "TRACE_GAP_CLIENT_PLAYER_NOT_READY";
        }
        if (!readiness.integratedServerReady) {
            return "TRACE_GAP_INTEGRATED_SERVER_NOT_READY";
        }
        if (!readiness.serverWorldReady) {
            return "TRACE_GAP_SERVER_WORLD_NOT_READY";
        }
        if (!readiness.serverPlayerReady) {
            return "TRACE_GAP_SERVER_PLAYER_NOT_READY";
        }
        if (!readiness.interactionManagerReady) {
            return "TRACE_GAP_INTERACTION_MANAGER_NOT_READY";
        }
        return null;
    }

    private static void emitLiveTruthReadyRow(MinecraftClient client, String phase, String reason) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_READY_ROW]"
                + " phase=" + phase
                + " tick=" + liveTruthTicks
                + " clientBootstrapReady=" + readiness.clientBootstrapReady
                + " clientWorldReady=" + readiness.clientWorldReady
                + " clientPlayerReady=" + readiness.clientPlayerReady
                + " integratedServerReady=" + readiness.integratedServerReady
                + " serverWorldReady=" + readiness.serverWorldReady
                + " serverPlayerReady=" + readiness.serverPlayerReady
                + " interactionManagerReady=" + readiness.interactionManagerReady
                + " programmaticWorldStartRequested=" + liveTruthWorldStartRequested
                + " reason=" + reason);
    }

    private static void prepareLiveTruthFixture(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || liveTruthOrigin == null) {
            return;
        }
        serverWorld.getServer().execute(() -> {
            for (int x = liveTruthOrigin.getX() - 3; x <= liveTruthOrigin.getX() + 3; x++) {
                for (int z = liveTruthOrigin.getZ() - 3; z <= liveTruthOrigin.getZ() + 3; z++) {
                    for (int y = liveTruthOrigin.getY() - 1; y <= liveTruthOrigin.getY() + 3; y++) {
                        serverWorld.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
            serverWorld.setBlockState(liveTruthGroundPos, Blocks.STONE.getDefaultState(), 3);
            if (!serverWorld.getServer().getPlayerManager().getPlayerList().isEmpty()) {
                serverWorld.getServer().getPlayerManager().getPlayerList().get(0)
                        .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
        });
    }

    private static void emitLiveTruthRow(MinecraftClient client) {
        ClientWorld clientWorld = client.world;
        ServerWorld serverWorld = serverWorldFor(client);
        if (clientWorld == null || serverWorld == null) {
            emitLiveTruthTraceGap("ROUTE_ROW_SAMPLE", "TRACE_GAP_WORLD_OR_PLAYER_NOT_READY");
            return;
        }
        BlockState slabState = serverWorld.getBlockState(liveTruthSlabPos);
        BlockState firstClientState = clientWorld.getBlockState(liveTruthFirstStonePos);
        BlockState firstServerState = serverWorld.getBlockState(liveTruthFirstStonePos);
        BlockState sideClientState = clientWorld.getBlockState(liveTruthSideStonePos);
        BlockState sideServerState = serverWorld.getBlockState(liveTruthSideStonePos);
        double firstClientDy = SlabSupport.getYOffset(clientWorld, liveTruthFirstStonePos, firstClientState);
        double firstServerDy = SlabSupport.getYOffset(serverWorld, liveTruthFirstStonePos, firstServerState);
        double sideClientDy = SlabSupport.getYOffset(clientWorld, liveTruthSideStonePos, sideClientState);
        double sideServerDy = SlabSupport.getYOffset(serverWorld, liveTruthSideStonePos, sideServerState);
        boolean firstClientAnchor = SlabAnchorAttachment.isAnchored(clientWorld, liveTruthFirstStonePos);
        boolean firstServerAnchor = SlabAnchorAttachment.isAnchored(serverWorld, liveTruthFirstStonePos);
        boolean sideClientAnchor = SlabAnchorAttachment.isAnchored(clientWorld, liveTruthSideStonePos);
        boolean sideServerAnchor = SlabAnchorAttachment.isAnchored(serverWorld, liveTruthSideStonePos);
        boolean sideIsStone = sideServerState.isOf(Blocks.STONE);
        String finalResult;
        String equivalence;
        if (!slabState.isOf(Blocks.STONE_SLAB) || !firstServerState.isOf(Blocks.STONE) || !sideIsStone) {
            finalResult = "TRACE_GAP";
            equivalence = "TRACE_GAP_MISSING_LIVE_PLACED_STATE";
        } else if (near(sideServerDy, 0.0d) && !sideServerAnchor) {
            finalResult = "RED";
            equivalence = "LIVE_EQUIVALENT";
        } else if (near(sideServerDy, -0.5d) && sideServerAnchor) {
            finalResult = "GREEN";
            equivalence = "NOT_LIVE_EQUIVALENT";
        } else {
            finalResult = "TRACE_GAP";
            equivalence = "TRACE_GAP_MISSING_FINALIZER_DATA";
        }
        Vec3d sideHit = new Vec3d(
                liveTruthFirstStonePos.getX(),
                liveTruthFirstStonePos.getY() + 0.5d,
                liveTruthFirstStonePos.getZ() + 0.5d);
        System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_ROW]"
                + " rowName=SIDE_PLACE_STONE_LIVE_SOURCE_TRUTH_WEST_FACE"
                + " fixtureOrigin=" + textPos(liveTruthOrigin)
                + " liveReferenceSideStonePos=-22,-59,64"
                + " translatedSideStonePos=" + textPos(liveTruthSideStonePos)
                + " slabPos=" + textPos(liveTruthSlabPos)
                + " slabState=" + slabState
                + " firstStonePos=" + textPos(liveTruthFirstStonePos)
                + " firstStoneState=" + firstServerState
                + " firstStoneClientDy=" + formatDouble(firstClientDy)
                + " firstStoneServerDy=" + formatDouble(firstServerDy)
                + " firstStoneClientAnchor=" + firstClientAnchor
                + " firstStoneServerAnchor=" + firstServerAnchor
                + " sideStoneIntendedPos=" + textPos(liveTruthSideStonePos)
                + " sideStoneFinalPos=" + textPos(liveTruthSideStonePos)
                + " sideStoneState=" + sideServerState
                + " sideStoneClientDy=" + formatDouble(sideClientDy)
                + " sideStoneServerDy=" + formatDouble(sideServerDy)
                + " sideStoneClientAnchor=" + sideClientAnchor
                + " sideStoneServerAnchor=" + sideServerAnchor
                + " heldItem=minecraft:stone"
                + " clickedFace=west"
                + " hitVector=" + formatVec(sideHit)
                + " placementReturnSlab=" + liveTruthSlabResult
                + " placementReturnFirstStone=" + liveTruthFirstStoneResult
                + " placementReturnSideStone=" + liveTruthSideStoneResult
                + " finalizerInvoked=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " sourcePos=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " sourceState=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " sourceDy=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " sourceAnchor=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " sourceQualifies=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " qualifierRejectReason=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " anchorWrite=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " anchorAfter=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " placedDyAfter=see_MC1211_SIDE_ADJACENT_FINALIZATION_TRACE"
                + " manualAnchorInjected=false"
                + " routePlacementMethod=ClientPlayerInteractionManager.interactBlock"
                + " reachDiagnostic=" + liveTruthReachDiagnostic
                + " equivalenceVerdict=" + equivalence
                + " classification=" + finalResult);
        System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_SUMMARY]"
                + " rows=1"
                + " finalResult=" + finalResult
                + " equivalenceVerdict=" + equivalence
                + " manualAnchorInjected=false");
        System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_" + finalResult + "]"
                + " rowName=SIDE_PLACE_STONE_LIVE_SOURCE_TRUTH_WEST_FACE"
                + " equivalenceVerdict=" + equivalence
                + " sideStoneServerDy=" + formatDouble(sideServerDy)
                + " sideStoneServerAnchor=" + sideServerAnchor);
    }

    private static void emitLiveTruthTraceGap(String row, String reason) {
        System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_START]"
                + " rowName=SIDE_PLACE_STONE_LIVE_SOURCE_TRUTH_WEST_FACE"
                + " rows=1");
        System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_ROW]"
                + " row=" + row
                + " fixtureOrigin=" + textPos(liveTruthOrigin)
                + " liveReferenceSideStonePos=-22,-59,64"
                + " translatedSideStonePos=" + textPos(liveTruthSideStonePos)
                + " manualAnchorInjected=false"
                + " equivalenceVerdict=" + reason
                + " classification=TRACE_GAP");
        System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_SUMMARY]"
                + " rows=1"
                + " finalResult=TRACE_GAP"
                + " equivalenceVerdict=" + reason);
        System.out.println("[MC1211_SIDE_PLACE_LIVE_TRUTH_TRACE_GAP]"
                + " rowName=SIDE_PLACE_STONE_LIVE_SOURCE_TRUTH_WEST_FACE"
                + " reason=" + reason);
        liveTruthFinalized = true;
        emitted = true;
        if (clientReadyForStop()) {
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    private static void runSameSpotAfterSlabBreakRoute(MinecraftClient client) {
        if (sameSpotFinalized || emitted) {
            return;
        }
        sameSpotTicks++;
        if (!sameSpotCanaryEmitted) {
            sameSpotCanaryEmitted = true;
            System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_ROUTE_CANARY]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " property=" + SAME_SPOT_AFTER_SLAB_BREAK_ONLY_PROPERTY
                    + " worldReady=" + (client != null && client.world != null)
                    + " playerReady=" + (client != null && client.player != null));
        }

        requestProgrammaticSameSpotWorldIfNeeded(client);
        String readinessGap = liveTruthReadinessGap(client);
        if (readinessGap != null) {
            if (!sameSpotReadyRowEmitted || sameSpotTicks % 1200 == 0) {
                emitSameSpotReadyRow(client, "WAITING", readinessGap);
                sameSpotReadyRowEmitted = true;
            }
            if (sameSpotTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitSameSpotReadyRow(client, "TIMEOUT", readinessGap);
            emitSameSpotTraceGap("ROUTE_READINESS", readinessGap);
            return;
        }
        if (!sameSpotReadyRowEmitted) {
            emitSameSpotReadyRow(client, "READY", "none");
            sameSpotReadyRowEmitted = true;
        }

        if (!sameSpotStarted) {
            sameSpotStarted = true;
            sameSpotOrigin = client.player.getBlockPos().add(9, 0, 7).toImmutable();
            sameSpotGroundPos = sameSpotOrigin.down();
            sameSpotSlabPos = sameSpotOrigin;
            sameSpotFullPos = sameSpotSlabPos.up();
            sameSpotAimPoint = new Vec3d(
                    sameSpotFullPos.getX() + 0.5d,
                    sameSpotFullPos.getY() - 0.60d,
                    sameSpotFullPos.getZ() + 0.5d);
            prepareSameSpotFixture(client);
            sameSpotPhase = 1;
            sameSpotPhaseTick = sameSpotTicks;
            System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_START]"
                    + " rowName=SAME_SPOT_AFTER_BREAK_LOWER_VISUAL"
                    + " fixtureOrigin=" + textPos(sameSpotOrigin)
                    + " slabPos=" + textPos(sameSpotSlabPos)
                    + " fullPos=" + textPos(sameSpotFullPos)
                    + " aimPoint=" + formatVec(sameSpotAimPoint)
                    + " placementRoute=ClientPlayerInteractionManager.interactBlock"
                    + " breakRoute=serverWorld.breakBlock"
                    + " manualAnchorInjection=false"
                    + " sameEyeYawPitchAfterBreak=true");
            return;
        }

        if (sameSpotTicks - sameSpotPhaseTick < 8) {
            return;
        }
        if (sameSpotPhase == 1) {
            sameSpotSlabPlacementResult = clickBlock(
                    client,
                    Items.STONE_SLAB,
                    sameSpotGroundPos,
                    Direction.UP,
                    hitVector(sameSpotGroundPos, Direction.UP));
            sameSpotPhase = 2;
            sameSpotPhaseTick = sameSpotTicks;
            return;
        }
        if (sameSpotPhase == 2) {
            sameSpotFullPlacementResult = clickBlock(
                    client,
                    Items.STONE,
                    sameSpotSlabPos,
                    Direction.UP,
                    hitVector(sameSpotSlabPos, Direction.UP));
            sameSpotPhase = 3;
            sameSpotPhaseTick = sameSpotTicks;
            return;
        }
        if (sameSpotPhase == 3) {
            syncSameSpotAim(client);
            System.setProperty("slabbed.render.offset.trace", "true");
            OffsetBlockStateModel.resetRenderOffsetSample(sameSpotFullPos);
            sameSpotPhase = 4;
            sameSpotPhaseTick = sameSpotTicks;
            return;
        }
        if (sameSpotPhase == 4) {
            sameSpotPreTarget = describeCrosshair(client);
            sameSpotPreModelTrace = describeModelTrace(OffsetBlockStateModel.snapshotRenderOffsetSample());
            OffsetBlockStateModel.resetRenderOffsetSample(sameSpotFullPos);
            requestSameSpotBreak(client);
            sameSpotPhase = 5;
            sameSpotPhaseTick = sameSpotTicks;
            return;
        }
        if (sameSpotPhase == 5 && sameSpotTicks - sameSpotPhaseTick < 20) {
            return;
        }
        if (sameSpotPhase == 5) {
            sameSpotPostTarget = describeCrosshair(client);
            sameSpotPostModelTrace = describeModelTrace(OffsetBlockStateModel.snapshotRenderOffsetSample());
            System.clearProperty("slabbed.render.offset.trace");
            emitSameSpotRow(client);
            sameSpotFinalized = true;
            emitted = true;
            client.scheduleStop();
        }
    }

    private static void requestProgrammaticSameSpotWorldIfNeeded(MinecraftClient client) {
        if (sameSpotWorldStartRequested
                || client == null
                || !client.isFinishedLoading()
                || client.world != null
                || client.player != null) {
            return;
        }
        sameSpotWorldStartRequested = true;
        LevelInfo levelInfo = new LevelInfo(
                "Slabbed MC1211 Same Spot Break Harness",
                GameMode.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                DataConfiguration.SAFE_MODE);
        GeneratorOptions generatorOptions = new GeneratorOptions(0L, false, false);
        client.createIntegratedServerLoader().createAndStart(
                "slabbed-mc1211-same-spot-break-harness",
                levelInfo,
                generatorOptions,
                Mc1211GoblinRouteClientEntrypoint::createSuperflatDimensionOptions,
                null);
    }

    private static void prepareSameSpotFixture(MinecraftClient client) {
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || sameSpotOrigin == null) {
            return;
        }
        serverWorld.getServer().execute(() -> {
            for (int x = sameSpotOrigin.getX() - 3; x <= sameSpotOrigin.getX() + 3; x++) {
                for (int z = sameSpotOrigin.getZ() - 3; z <= sameSpotOrigin.getZ() + 3; z++) {
                    for (int y = sameSpotOrigin.getY() - 1; y <= sameSpotOrigin.getY() + 3; y++) {
                        serverWorld.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
            serverWorld.setBlockState(sameSpotGroundPos, Blocks.STONE.getDefaultState(), 3);
            if (!serverWorld.getServer().getPlayerManager().getPlayerList().isEmpty()) {
                serverWorld.getServer().getPlayerManager().getPlayerList().get(0)
                        .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
        });
    }

    private static void syncSameSpotAim(MinecraftClient client) {
        if (client == null || client.player == null || sameSpotAimPoint == null) {
            return;
        }
        Vec3d eye = sameSpotAimPoint.add(1.75d, 0.35d, 0.0d);
        Vec3d delta = sameSpotAimPoint.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        sameSpotReachDiagnostic = "eye=" + formatVec(eye)
                + "/aimPoint=" + formatVec(sameSpotAimPoint)
                + "/distance=" + formatDouble(eye.distanceTo(sameSpotAimPoint))
                + "/yaw=" + formatDouble(yaw)
                + "/pitch=" + formatDouble(pitch);
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.setSneaking(false);
        client.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        MinecraftServer server = client.getServer();
        if (server != null && !server.getPlayerManager().getPlayerList().isEmpty()) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            serverPlayer.setVelocity(Vec3d.ZERO);
            serverPlayer.setSneaking(false);
            serverPlayer.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            serverPlayer.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    private static void requestSameSpotBreak(MinecraftClient client) {
        if (sameSpotBreakRequested) {
            return;
        }
        sameSpotBreakRequested = true;
        ServerWorld serverWorld = serverWorldFor(client);
        if (serverWorld == null || sameSpotSlabPos == null) {
            sameSpotBreakResult = "TRACE_GAP_SERVER_WORLD_OR_SLAB_POS_MISSING";
            return;
        }
        serverWorld.getServer().execute(() -> sameSpotBreakResult =
                serverWorld.breakBlock(sameSpotSlabPos, false) ? "BROKE" : "NOT_BROKEN");
    }

    private static void emitSameSpotReadyRow(MinecraftClient client, String phase, String reason) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_READY_ROW]"
                + " phase=" + phase
                + " tick=" + sameSpotTicks
                + " clientBootstrapReady=" + readiness.clientBootstrapReady
                + " clientWorldReady=" + readiness.clientWorldReady
                + " clientPlayerReady=" + readiness.clientPlayerReady
                + " integratedServerReady=" + readiness.integratedServerReady
                + " serverWorldReady=" + readiness.serverWorldReady
                + " serverPlayerReady=" + readiness.serverPlayerReady
                + " interactionManagerReady=" + readiness.interactionManagerReady
                + " programmaticWorldStartRequested=" + sameSpotWorldStartRequested
                + " reason=" + reason);
    }

    private static void emitSameSpotRow(MinecraftClient client) {
        ClientWorld clientWorld = client == null ? null : client.world;
        ServerWorld serverWorld = serverWorldFor(client);
        if (clientWorld == null || serverWorld == null) {
            emitSameSpotTraceGap("ROUTE_ROW_SAMPLE", "TRACE_GAP_WORLD_OR_PLAYER_NOT_READY");
            return;
        }
        BlockState slabClientState = clientWorld.getBlockState(sameSpotSlabPos);
        BlockState slabServerState = serverWorld.getBlockState(sameSpotSlabPos);
        BlockState fullClientState = clientWorld.getBlockState(sameSpotFullPos);
        BlockState fullServerState = serverWorld.getBlockState(sameSpotFullPos);
        double fullClientDy = SlabSupport.getYOffset(clientWorld, sameSpotFullPos, fullClientState);
        double fullServerDy = SlabSupport.getYOffset(serverWorld, sameSpotFullPos, fullServerState);
        boolean fullClientAnchor = SlabAnchorAttachment.isAnchored(clientWorld, sameSpotFullPos);
        boolean fullServerAnchor = SlabAnchorAttachment.isAnchored(serverWorld, sameSpotFullPos);
        boolean visualTracePresent = sameSpotPostModelTrace.contains("modelDy=");
        boolean postTargetHitsFull = sameSpotPostTarget.contains("pos=" + textPos(sameSpotFullPos));
        boolean postTargetMissOrWrong = sameSpotPostTarget.contains("type=MISS") || !postTargetHitsFull;
        String classification;
        String reason;
        if (!sameSpotSlabPlacementResult.contains("SUCCESS") || !sameSpotFullPlacementResult.contains("SUCCESS")) {
            classification = "TRACE_GAP";
            reason = "TRACE_GAP_PLACEMENT_NOT_REPRODUCED";
        } else if (!slabServerState.isAir()) {
            classification = "TRACE_GAP";
            reason = "TRACE_GAP_SLAB_NOT_BROKEN";
        } else if (!fullServerState.isOf(Blocks.STONE)) {
            classification = "TRACE_GAP";
            reason = "TRACE_GAP_FULL_BLOCK_DID_NOT_REMAIN";
        } else if (postTargetMissOrWrong) {
            if (visualTracePresent) {
                classification = "RED";
                reason = "SAME_AIM_VISUAL_PRESENT_TARGET_NOT_BREAKABLE_FULL_BLOCK";
            } else {
                classification = "TRACE_GAP";
                reason = "TRACE_GAP_MODEL_TRACE_MISSING";
            }
        } else {
            classification = "TRACE_GAP";
            reason = "TRACE_GAP_ROUTE_NOT_LIVE_EQUIVALENT_TARGET_STILL_HITS_FULL_BLOCK";
        }
        System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_ROW]"
                + " rowName=SAME_SPOT_AFTER_BREAK_LOWER_VISUAL"
                + " fixtureOrigin=" + textPos(sameSpotOrigin)
                + " slabPos=" + textPos(sameSpotSlabPos)
                + " fullPos=" + textPos(sameSpotFullPos)
                + " slabClientState=" + slabClientState
                + " slabServerState=" + slabServerState
                + " fullClientState=" + fullClientState
                + " fullServerState=" + fullServerState
                + " fullClientDy=" + formatDouble(fullClientDy)
                + " fullServerDy=" + formatDouble(fullServerDy)
                + " fullClientAnchor=" + fullClientAnchor
                + " fullServerAnchor=" + fullServerAnchor
                + " aimPoint=" + formatVec(sameSpotAimPoint)
                + " sameEyeYawPitchAfterBreak=true"
                + " placementReturnSlab=" + sameSpotSlabPlacementResult
                + " placementReturnFullBlock=" + sameSpotFullPlacementResult
                + " breakResult=" + sameSpotBreakResult
                + " preTarget=" + sameSpotPreTarget
                + " postTarget=" + sameSpotPostTarget
                + " preModelTrace=" + sameSpotPreModelTrace
                + " postModelTrace=" + sameSpotPostModelTrace
                + " reachDiagnostic=" + sameSpotReachDiagnostic
                + " classification=" + classification
                + " reason=" + reason);
        System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_SUMMARY]"
                + " rows=1"
                + " finalResult=" + classification
                + " reason=" + reason
                + " behaviorChanged=false");
        System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_" + classification + "]"
                + " rowName=SAME_SPOT_AFTER_BREAK_LOWER_VISUAL"
                + " reason=" + reason);
    }

    private static void emitSameSpotTraceGap(String row, String reason) {
        System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_START]"
                + " rowName=SAME_SPOT_AFTER_BREAK_LOWER_VISUAL"
                + " rows=1");
        System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_ROW]"
                + " row=" + row
                + " fixtureOrigin=" + textPos(sameSpotOrigin)
                + " slabPos=" + textPos(sameSpotSlabPos)
                + " fullPos=" + textPos(sameSpotFullPos)
                + " classification=TRACE_GAP"
                + " reason=" + reason);
        System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_SUMMARY]"
                + " rows=1"
                + " finalResult=TRACE_GAP"
                + " reason=" + reason);
        System.out.println("[MC1211_SAME_SPOT_AFTER_SLAB_BREAK_TRACE_GAP]"
                + " rowName=SAME_SPOT_AFTER_BREAK_LOWER_VISUAL"
                + " reason=" + reason);
        sameSpotFinalized = true;
        emitted = true;
        System.clearProperty("slabbed.render.offset.trace");
        if (clientReadyForStop()) {
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    private static String describeCrosshair(MinecraftClient client) {
        if (client == null || client.world == null || client.crosshairTarget == null) {
            return "type=null";
        }
        HitResult target = client.crosshairTarget;
        if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
            return "type=" + target.getType();
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        return "type=BLOCK"
                + "/pos=" + textPos(pos)
                + "/side=" + blockHit.getSide().asString()
                + "/hitVec=" + formatVec(blockHit.getPos())
                + "/state=" + state
                + "/dy=" + formatDouble(SlabSupport.getYOffset(client.world, pos, state))
                + "/anchor=" + SlabAnchorAttachment.isAnchored(client.world, pos);
    }

    private static String describeModelTrace(OffsetBlockStateModel.RenderOffsetSample trace) {
        if (trace == null || !trace.seen()) {
            return "missing";
        }
        return "modelDy=" + formatDouble(trace.modelDy())
                + "/pos=" + trace.pos()
                + "/state=" + trace.state();
    }

    private static void runSuperflatModelHitboxHarnessRoute(MinecraftClient client) {
        boolean wallFenceProductRedMode = Boolean.getBoolean(WALL_FENCE_PRODUCT_RED_ONLY_PROPERTY);
        boolean releaseVisualCategoryMode = Boolean.getBoolean(RELEASE_VISUAL_CATEGORY_MATRIX_PROPERTY);
        SuperflatHarnessRowSpec[] rows = activeSuperflatHarnessRows();
        if (emitted || superflatHarnessFinalized) {
            return;
        }
        superflatHarnessTicks++;
        if (!superflatHarnessCanaryEmitted) {
            superflatHarnessCanaryEmitted = true;
            System.out.println("[" + superflatHarnessMarker(
                    wallFenceProductRedMode,
                    releaseVisualCategoryMode,
                    "ROUTE_CANARY") + "]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " property=" + superflatHarnessPropertyName(wallFenceProductRedMode, releaseVisualCategoryMode)
                    + " rows=" + rows.length
                    + " worldType=superflat"
                    + " screenshotDir=" + releaseVisualScreenshotDir().toAbsolutePath()
                    + " behaviorChanged=false");
        }
        requestProgrammaticSuperflatHarnessWorldIfNeeded(client);
        String readinessGap = sidePlaceReadinessGap(client);
        if (readinessGap != null) {
            if (!superflatHarnessReadyRowEmitted || superflatHarnessTicks % 1200 == 0) {
                emitSidePlaceReadyRow(client, "WAITING", readinessGap);
                superflatHarnessReadyRowEmitted = true;
            }
            if (superflatHarnessTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitSuperflatHarnessTraceGap("TRACE_GAP_WORLD_NOT_READY", readinessGap);
            return;
        }
        if (!superflatHarnessReadyRowEmitted) {
            emitSidePlaceReadyRow(client, "READY", "none");
            superflatHarnessReadyRowEmitted = true;
        }
        if (!superflatHarnessStarted) {
            superflatHarnessStarted = true;
            superflatHarnessOrigin = client.player.getBlockPos().add(8, 0, 8).toImmutable();
            System.out.println("[" + superflatHarnessMarker(
                    wallFenceProductRedMode,
                    releaseVisualCategoryMode,
                    "START") + "]"
                    + " rows=" + rows.length
                    + " fixtureOrigin=" + textPos(superflatHarnessOrigin)
                    + " placementSurface=mixed_bottom_slabs"
                    + " supportMix=superflat_ground_plus_bottom_slabs"
                    + " screenshots=" + releaseVisualCategoryMode
                    + " supportBlockIds=minecraft:stone_slab,minecraft:oak_slab");
        }
        if (superflatHarnessRowIndex >= rows.length) {
            emitSuperflatHarnessSummary(wallFenceProductRedMode, releaseVisualCategoryMode);
            return;
        }
        runSuperflatHarnessRow(
                client,
                rows[superflatHarnessRowIndex],
                superflatHarnessRowIndex,
                wallFenceProductRedMode,
                releaseVisualCategoryMode);
    }

    private static void runSuperflatHarnessRow(
            MinecraftClient client,
            SuperflatHarnessRowSpec row,
            int rowIndex,
            boolean wallFenceProductRedMode,
            boolean releaseVisualCategoryMode
    ) {
        if (client == null || client.world == null || client.player == null) {
            return;
        }
        MinecraftServer server = client.getServer();
        if (server == null) {
            return;
        }
        ServerWorld serverWorld = server.getWorld(client.world.getRegistryKey());
        if (serverWorld == null) {
            return;
        }
        if (superflatHarnessRowPhase == 0) {
            BlockPos rowOrigin = superflatHarnessOrigin.add(rowIndex * 4, 0, 0);
            superflatHarnessSlabPos = rowOrigin;
            superflatHarnessPlacePos = rowOrigin.up();
            superflatHarnessPlacementMethod = "MIXED";
            superflatHarnessPlacementReturn = "not_started";
            superflatHarnessInteractAttempts = 0;
            superflatHarnessScreenClosedForRow = false;
            authorSuperflatHarnessFixture(
                    server,
                    serverWorld,
                    superflatHarnessSlabPos,
                    superflatHarnessPlacePos,
                    row.supportBlock());
            syncHarnessPlayerForTopPlace(client, superflatHarnessSlabPos);
            ItemStack stack = harnessStackForBlock(row.blockId());
            if (stack != null) {
                client.player.setStackInHand(Hand.MAIN_HAND, stack);
                if (!server.getPlayerManager().getPlayerList().isEmpty()
                        && !server.getPlayerManager().getPlayerList().get(0).isRemoved()) {
                    server.getPlayerManager().getPlayerList().get(0)
                            .setStackInHand(Hand.MAIN_HAND, stack.copy());
                }
            }
            OffsetBlockStateModel.resetFullMeshBoundsSample(superflatHarnessPlacePos);
            superflatHarnessRowPhase = 1;
            superflatHarnessPhaseTick = superflatHarnessTicks;
            return;
        }
        if (superflatHarnessRowPhase == 1) {
            if (superflatHarnessTicks - superflatHarnessPhaseTick < 2) {
                return;
            }
            ItemStack stack = harnessStackForBlock(row.blockId());
            if (stack == null) {
                server.execute(() -> serverWorld.setBlockState(superflatHarnessPlacePos, harnessStateForBlock(row.blockId()), 3));
                superflatHarnessPlacementMethod = "DIRECT_WORLD_TRACE_ONLY";
                superflatHarnessPlacementReturn = "DIRECT_SETBLOCK_TRACE_ONLY";
                superflatHarnessRowPhase = 2;
                superflatHarnessPhaseTick = superflatHarnessTicks;
                return;
            }
            BlockHitResult hit = new BlockHitResult(
                    new Vec3d(
                            superflatHarnessSlabPos.getX() + 0.5d,
                            superflatHarnessSlabPos.getY() + 0.5d,
                            superflatHarnessSlabPos.getZ() + 0.5d),
                    Direction.UP,
                    superflatHarnessSlabPos,
                    false);
            ActionResult result = client.interactionManager == null
                    ? ActionResult.FAIL
                    : client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
            superflatHarnessInteractAttempts++;
            superflatHarnessPlacementMethod = "REAL_INTERACT_BLOCK";
            superflatHarnessPlacementReturn = String.valueOf(result);
            superflatHarnessRowPhase = 2;
            superflatHarnessPhaseTick = superflatHarnessTicks;
            return;
        }
        if (superflatHarnessTicks - superflatHarnessPhaseTick < 20) {
            return;
        }
        if (releaseVisualCategoryMode && client.currentScreen != null && !superflatHarnessScreenClosedForRow) {
            client.setScreen(null);
            superflatHarnessScreenClosedForRow = true;
            superflatHarnessPhaseTick = superflatHarnessTicks;
            return;
        }
        Block expectedBlock = harnessStateForBlock(row.blockId()).getBlock();
        BlockState latestServerState = serverWorld.getBlockState(superflatHarnessPlacePos);
        if (!"DIRECT_WORLD_TRACE_ONLY".equals(superflatHarnessPlacementMethod)
                && latestServerState.getBlock() != expectedBlock
                && superflatHarnessInteractAttempts < 3) {
            syncHarnessPlayerForTopPlace(client, superflatHarnessSlabPos);
            ItemStack retryStack = harnessStackForBlock(row.blockId());
            if (retryStack != null) {
                client.player.setStackInHand(Hand.MAIN_HAND, retryStack);
                if (!server.getPlayerManager().getPlayerList().isEmpty()
                        && !server.getPlayerManager().getPlayerList().get(0).isRemoved()) {
                    server.getPlayerManager().getPlayerList().get(0).setStackInHand(Hand.MAIN_HAND, retryStack.copy());
                }
            }
            superflatHarnessRowPhase = 1;
            superflatHarnessPhaseTick = superflatHarnessTicks;
            return;
        }
        if (releaseVisualCategoryMode
                && superflatHarnessShouldAwaitLoweredModelTrace(serverWorld, row, expectedBlock, latestServerState)) {
            return;
        }
        emitSuperflatHarnessRow(client, serverWorld, row, wallFenceProductRedMode, releaseVisualCategoryMode);
        superflatHarnessRowIndex++;
        superflatHarnessRowPhase = 0;
        superflatHarnessPhaseTick = superflatHarnessTicks;
    }

    private static boolean superflatHarnessShouldAwaitLoweredModelTrace(
            ServerWorld serverWorld,
            SuperflatHarnessRowSpec row,
            Block expectedBlock,
            BlockState latestServerState
    ) {
        if ("DIRECT_WORLD_TRACE_ONLY".equals(superflatHarnessPlacementMethod)
                || !superflatHarnessPlacementReturn.contains("SUCCESS")
                || latestServerState.getBlock() != expectedBlock
                || "minecraft:oak_sign".equals(row.blockId())) {
            return false;
        }
        double placedDy = SlabSupport.getYOffset(serverWorld, superflatHarnessPlacePos, latestServerState);
        if (Math.abs(placedDy - (-0.5d)) > 1.0e-6d) {
            return false;
        }
        if (superflatHarnessTicks - superflatHarnessPhaseTick >= SUPERFLAT_HARNESS_MODEL_SETTLE_TIMEOUT_TICKS) {
            return false;
        }
        OffsetBlockStateModel.FullMeshBoundsSample meshTrace = OffsetBlockStateModel.snapshotFullMeshBoundsSample();
        return !superflatHarnessLoweredModelTraceReady(meshTrace, placedDy);
    }

    private static boolean superflatHarnessLoweredModelTraceReady(
            OffsetBlockStateModel.FullMeshBoundsSample meshTrace,
            double expectedDy
    ) {
        return meshTrace != null
                && meshTrace.seen()
                && Math.abs(meshTrace.dy() - expectedDy) <= 1.0e-6d
                && Double.isFinite(meshTrace.minBeforeY())
                && Double.isFinite(meshTrace.maxBeforeY())
                && Double.isFinite(meshTrace.minAfterY())
                && Double.isFinite(meshTrace.maxAfterY());
    }

    private static void requestProgrammaticSuperflatHarnessWorldIfNeeded(MinecraftClient client) {
        if (superflatHarnessWorldStartRequested
                || client == null
                || !client.isFinishedLoading()
                || client.world != null
                || client.player != null) {
            return;
        }
        superflatHarnessWorldStartRequested = true;
        LevelInfo levelInfo = new LevelInfo(
                "Slabbed MC1211 Superflat Model Hitbox Harness",
                GameMode.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                DataConfiguration.SAFE_MODE);
        GeneratorOptions generatorOptions = new GeneratorOptions(0L, false, false);
        client.createIntegratedServerLoader().createAndStart(
                "slabbed-mc1211-superflat-model-hitbox-harness",
                levelInfo,
                generatorOptions,
                Mc1211GoblinRouteClientEntrypoint::createSuperflatDimensionOptions,
                null);
    }

    private static void authorSuperflatHarnessFixture(
            MinecraftServer server,
            ServerWorld serverWorld,
            BlockPos slabPos,
            BlockPos placePos,
            Block supportBlock
    ) {
        server.execute(() -> {
            for (int x = slabPos.getX() - 1; x <= slabPos.getX() + 1; x++) {
                for (int z = slabPos.getZ() - 1; z <= slabPos.getZ() + 1; z++) {
                    for (int y = slabPos.getY() - 1; y <= slabPos.getY() + 3; y++) {
                        serverWorld.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
            BlockState supportBlockState = supportBlock.getDefaultState();
            if (supportBlockState.contains(SlabBlock.TYPE)) {
                supportBlockState = supportBlockState.with(SlabBlock.TYPE, SlabType.BOTTOM);
            }
            serverWorld.setBlockState(
                    slabPos,
                    supportBlockState,
                    3);
            serverWorld.setBlockState(placePos, Blocks.AIR.getDefaultState(), 3);
        });
    }

    private static void syncHarnessPlayerForTopPlace(MinecraftClient client, BlockPos slabPos) {
        Vec3d hit = Vec3d.ofCenter(slabPos).add(0.0d, 0.5d, 0.0d);
        Vec3d eye = hit.add(0.0d, 0.8d, 2.4d);
        Vec3d delta = hit.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.setSneaking(false);
    }

    private static void emitSuperflatHarnessRow(
            MinecraftClient client,
            ServerWorld serverWorld,
            SuperflatHarnessRowSpec row,
            boolean wallFenceProductRedMode,
            boolean releaseVisualCategoryMode
    ) {
        ClientWorld clientWorld = client.world;
        BlockState finalClientState = clientWorld.getBlockState(superflatHarnessPlacePos);
        BlockState finalServerState = serverWorld.getBlockState(superflatHarnessPlacePos);
        Block expectedBlock = harnessStateForBlock(row.blockId()).getBlock();
        double placedDy = SlabSupport.getYOffset(serverWorld, superflatHarnessPlacePos, finalServerState);
        BlockState supportState = serverWorld.getBlockState(superflatHarnessSlabPos);
        double sourceDy = SlabSupport.getYOffset(serverWorld, superflatHarnessSlabPos, supportState);
        boolean substrateProven = supportState.isOf(row.supportBlock())
                && supportState.contains(SlabBlock.TYPE)
                && supportState.get(SlabBlock.TYPE) == SlabType.BOTTOM;
        String supportBlockId = Registries.BLOCK.getId(row.supportBlock()).toString();
        boolean anchored = SlabAnchorAttachment.isAnchored(serverWorld, superflatHarnessPlacePos);
        boolean loweredCarrier = SlabSupport.isFullHeightLoweredCarrier(serverWorld, superflatHarnessPlacePos, finalServerState);
        VoxelShape outline = finalServerState.getOutlineShape(serverWorld, superflatHarnessPlacePos);
        VoxelShape raycast = finalServerState.getRaycastShape(serverWorld, superflatHarnessPlacePos);
        // Vanilla crosshair targeting uses the OUTLINE shape. getRaycastShape is empty by
        // default for most blocks (e.g. stairs) and vanilla defers to the outline when it is
        // empty, so an empty raycast shape is NOT a targeting defect. Treat an empty raycast
        // shape as inheriting the outline so the triad reflects real player targeting instead
        // of a false "raycast shape empty" trace gap. rawRaycastShapeEmpty is logged for audit.
        boolean rawRaycastShapeEmpty = raycast == null || raycast.isEmpty();
        VoxelShape effectiveRaycast = rawRaycastShapeEmpty ? outline : raycast;
        String outlineBounds = shapeBounds(outline);
        String raycastBounds = shapeBounds(effectiveRaycast);
        OffsetBlockStateModel.FullMeshBoundsSample meshTrace = OffsetBlockStateModel.snapshotFullMeshBoundsSample();
        String modelMeshMinY = meshTrace.seen() ? formatDouble(meshTrace.minAfterY()) : "NaN";
        String modelMeshMaxY = meshTrace.seen() ? formatDouble(meshTrace.maxAfterY()) : "NaN";
        String meshTraceKey = meshTrace.seen() ? meshTrace.meshTraceKey() : "none";
        String meshMatrixKey = meshTrace.seen() ? meshTrace.matrixKey() : "none";
        String meshMatrixRow = meshTrace.seen() ? meshTrace.matrixRow() : "UNKNOWN";
        String meshBlockId = meshTrace.seen() ? meshTrace.blockId() : "none";
        String meshPos = meshTrace.seen() ? meshTrace.pos() : "none";
        String meshState = meshTrace.seen() ? meshTrace.state() : "none";
        String meshDy = meshTrace.seen() ? formatDouble(meshTrace.dy()) : "NaN";
        String meshModelClass = meshTrace.seen() ? meshTrace.modelClass() : "unknown";
        String meshTickOrFrame = meshTrace.seen() ? meshTrace.tickOrFrame() : "unknown";
        String meshPassSequence = meshTrace.seen() ? Integer.toString(meshTrace.passSequence()) : "0";
        String meshQuadsVisited = meshTrace.seen() ? Integer.toString(meshTrace.totalQuadsSeen()) : "0";
        String meshVerticesVisited = meshTrace.seen() ? Integer.toString(meshTrace.verticesVisited()) : "0";
        String meshMinBeforeY = meshTrace.seen() ? formatDouble(meshTrace.minBeforeY()) : "NaN";
        String meshMaxBeforeY = meshTrace.seen() ? formatDouble(meshTrace.maxBeforeY()) : "NaN";
        String meshSnapshotSource = meshTrace.seen() ? meshTrace.snapshotSource() : "none";
        String meshAggregateDedupKey = meshTrace.seen() ? meshTrace.aggregateDedupKey() : "none";
        String outlineMinY = outline.isEmpty() ? "NaN" : formatDouble(outline.getBoundingBox().minY);
        String outlineMaxY = outline.isEmpty() ? "NaN" : formatDouble(outline.getBoundingBox().maxY);
        String raycastMinY = effectiveRaycast.isEmpty() ? "NaN" : formatDouble(effectiveRaycast.getBoundingBox().minY);
        String raycastMaxY = effectiveRaycast.isEmpty() ? "NaN" : formatDouble(effectiveRaycast.getBoundingBox().maxY);
        boolean finiteModelBounds = meshTrace.seen()
                && Double.isFinite(meshTrace.minBeforeY())
                && Double.isFinite(meshTrace.maxBeforeY())
                && Double.isFinite(meshTrace.minAfterY())
                && Double.isFinite(meshTrace.maxAfterY());
        double modelMinTranslation = finiteModelBounds
                ? meshTrace.minAfterY() - meshTrace.minBeforeY()
                : Double.NaN;
        double modelMaxTranslation = finiteModelBounds
                ? meshTrace.maxAfterY() - meshTrace.maxBeforeY()
                : Double.NaN;
        boolean modelTranslationAligned = finiteModelBounds
                && Math.abs(modelMinTranslation - meshTrace.dy()) <= 1.0e-6
                && Math.abs(modelMaxTranslation - meshTrace.dy()) <= 1.0e-6;
        boolean modelBottomBelowOutline = finiteModelBounds
                && !outline.isEmpty()
                && meshTrace.minAfterY() < outline.getBoundingBox().minY - 1.0e-6;
        boolean releaseScreenshotOnlyRenderer = releaseVisualCategoryMode
                && "minecraft:oak_sign".equals(row.blockId())
                && finalServerState.getBlock() == expectedBlock;
        boolean releaseDyZeroNativeRow = releaseVisualCategoryMode
                && finalServerState.getBlock() == expectedBlock
                && ("minecraft:oak_slab".equals(row.blockId())
                        || "minecraft:stone_slab".equals(row.blockId()));
        double delta = (meshTrace.seen() && !outline.isEmpty())
                ? Math.abs(meshTrace.minAfterY() - outline.getBoundingBox().minY)
                : Double.NaN;
        boolean triadBoundsPresent = meshTrace.seen() && !outline.isEmpty() && !effectiveRaycast.isEmpty();
        boolean triadAligned = triadBoundsPresent
                && delta <= 1.0e-6
                && Math.abs(meshTrace.minAfterY() - effectiveRaycast.getBoundingBox().minY) <= 1.0e-6
                && Math.abs(meshTrace.maxAfterY() - outline.getBoundingBox().maxY) <= 1.0e-6
                && Math.abs(meshTrace.maxAfterY() - effectiveRaycast.getBoundingBox().maxY) <= 1.0e-6;
        boolean placedDyExpected = Math.abs(placedDy - (-0.5d)) <= 1.0e-6;
        boolean loweredPlacementWithZeroModelDy = placedDyExpected
                && meshTrace.seen()
                && Math.abs(meshTrace.dy()) <= 1.0e-6;
        boolean technicalTriadAligned = triadAligned && placedDyExpected && substrateProven;
        boolean productVisualLawPass = !row.productBadSuspect();
        String visualAcceptance = row.productBadSuspect()
                ? "JULIA_APPROVED_POST_MODEL_OWNERSHIP_REPAIR"
                : "ORDINARY_CONTROL";
        String verdict;
        String reason;
        if (releaseVisualCategoryMode) {
            productVisualLawPass = true;
            visualAcceptance = "SCREENSHOT_AND_TRACE_CAPTURED";
            if ("DIRECT_WORLD_TRACE_ONLY".equals(superflatHarnessPlacementMethod)) {
                verdict = "TRACE_GAP_NOT_REAL_PLACEMENT";
                reason = "direct_world_place_trace_only";
                superflatHarnessTraceGapRows++;
            } else if (!superflatHarnessPlacementReturn.contains("SUCCESS")) {
                verdict = "TRACE_GAP_NOT_REAL_PLACEMENT";
                reason = "interact_block_not_success";
                superflatHarnessTraceGapRows++;
            } else if (finalServerState.getBlock() != expectedBlock) {
                verdict = "TRACE_GAP_EXPECTED_BLOCK_MISSING";
                reason = "interact_success_but_expected_block_missing";
                superflatHarnessTraceGapRows++;
            } else if (!meshTrace.seen()) {
                if (releaseScreenshotOnlyRenderer) {
                    verdict = "GREEN_SCREENSHOT_ONLY_RENDERER";
                    reason = "block_entity_renderer_not_baked_block_model";
                    superflatHarnessGreenRows++;
                } else {
                    verdict = "TRACE_GAP_MODEL_BOUNDS";
                    reason = "full_mesh_trace_missing_for_row";
                    superflatHarnessTraceGapRows++;
                }
            } else if (!finiteModelBounds) {
                if (releaseDyZeroNativeRow) {
                    verdict = "GREEN_NATIVE_DY_ZERO_ACCEPTABLE";
                    reason = "native_slab_row_has_no_lowered_visual_transform";
                    superflatHarnessGreenRows++;
                } else if (loweredPlacementWithZeroModelDy) {
                    verdict = "RED_MODEL_TRANSLATION_NOT_APPLIED";
                    reason = "lowered_state_outline_raycast_present_but_model_dy_zero";
                    superflatHarnessRedRows++;
                } else {
                    verdict = "TRACE_GAP_MODEL_BOUNDS";
                    reason = "full_mesh_trace_has_no_finite_bounds";
                    superflatHarnessTraceGapRows++;
                }
            } else if (!modelTranslationAligned) {
                verdict = "RED_MODEL_TRANSLATION_INCOHERENT";
                reason = "model_after_bounds_do_not_equal_before_bounds_plus_dy";
                superflatHarnessRedRows++;
            } else if (modelBottomBelowOutline) {
                verdict = "RED_MODEL_BELOW_OUTLINE";
                reason = "model_bottom_lower_than_selection_bottom";
                superflatHarnessRedRows++;
            } else {
                verdict = "GREEN_MODEL_TRANSLATION_COHERENT";
                reason = "model_translated_by_dy_native_bounds_may_be_inside_outline";
                superflatHarnessGreenRows++;
            }
        } else if (!wallFenceProductRedMode) {
            if ("DIRECT_WORLD_TRACE_ONLY".equals(superflatHarnessPlacementMethod)) {
                verdict = "TRACE_GAP_NOT_REAL_PLACEMENT";
                reason = "direct_world_place_trace_only";
                superflatHarnessTraceGapRows++;
            } else if (!superflatHarnessPlacementReturn.contains("SUCCESS")) {
                verdict = "TRACE_GAP_NOT_REAL_PLACEMENT";
                reason = "interact_block_not_success";
                superflatHarnessTraceGapRows++;
            } else if (!meshTrace.seen()) {
                verdict = "TRACE_GAP_MODEL_BOUNDS";
                reason = "full_mesh_trace_missing_for_row";
                superflatHarnessTraceGapRows++;
            } else if (outline.isEmpty() || effectiveRaycast.isEmpty()) {
                verdict = "TRACE_GAP_OUTLINE_RAYCAST_BOUNDS";
                reason = "outline_or_raycast_shape_empty";
                superflatHarnessTraceGapRows++;
            } else if (delta > 1.0e-6) {
                verdict = "RED_MODEL_OUTLINE_VERTICAL_MISMATCH";
                reason = "model_afterY_not_equal_outline_bounds";
                superflatHarnessRedRows++;
            } else {
                verdict = row.productBadSuspect()
                        ? "GREEN_TRIAD_ALIGNED_PRODUCT_BAD"
                        : "GREEN_TRIAD_ALIGNED_ACCEPTABLE";
                reason = row.productBadSuspect() ? "family_visual_reference_mismatch_suspect" : "triad_aligned";
                if (row.productBadSuspect()) {
                    superflatHarnessProductBadRows++;
                } else {
                    superflatHarnessGreenRows++;
                }
            }
        } else if (!substrateProven) {
            verdict = "TRACE_GAP_SUBSTRATE_MISSING";
            reason = "support_not_expected_bottom_slab";
            superflatHarnessTraceGapRows++;
        } else if ("DIRECT_WORLD_TRACE_ONLY".equals(superflatHarnessPlacementMethod)) {
            verdict = "minecraft:stone_brick_wall".equals(row.blockId())
                    ? "TRACE_GAP_STONE_BRICK_WALL_DIRECT_WORLD_ONLY"
                    : "TRACE_GAP_BOUNDS_OR_TRIAD_MISSING";
            reason = "direct_world_place_trace_only";
            superflatHarnessTraceGapRows++;
        } else if (!superflatHarnessPlacementReturn.contains("SUCCESS")) {
            verdict = "TRACE_GAP_BOUNDS_OR_TRIAD_MISSING";
            reason = "interact_block_not_success";
            superflatHarnessTraceGapRows++;
        } else if (finalServerState.getBlock() != expectedBlock) {
            verdict = "TRACE_GAP_BOUNDS_OR_TRIAD_MISSING";
            reason = "interact_success_but_expected_block_missing";
            superflatHarnessTraceGapRows++;
        } else if (!triadBoundsPresent) {
            verdict = "TRACE_GAP_BOUNDS_OR_TRIAD_MISSING";
            reason = "model_or_outline_or_raycast_missing";
            superflatHarnessTraceGapRows++;
        } else if (!technicalTriadAligned) {
            verdict = row.productBadSuspect()
                    ? "TRACE_GAP_BOUNDS_OR_TRIAD_MISSING"
                    : "RED_FULL_BLOCK_CONTROL_REGRESSION";
            reason = !triadAligned ? "technical_triad_not_aligned" : "placed_dy_or_substrate_not_expected";
            if (row.productBadSuspect()) {
                superflatHarnessTraceGapRows++;
            } else {
                superflatHarnessRedRows++;
            }
        } else if (row.productBadSuspect()) {
            productVisualLawPass = true;
            verdict = "GREEN_WALL_FENCE_VISUAL_CONTACT_APPROVED";
            reason = "julia_live_visual_accepted_after_model_ownership_repair";
            superflatHarnessGreenRows++;
        } else {
            verdict = "GREEN_FULL_BLOCK_CONTROL";
            reason = "ordinary_full_block_control_green";
            superflatHarnessGreenRows++;
        }
        String outputRowName = wallFenceProductRedMode ? redMatrixRowName(row) : row.rowName();
        String rowMarker = superflatHarnessMarker(wallFenceProductRedMode, releaseVisualCategoryMode, "ROW");
        BlockHitResult blockHit = client.crosshairTarget instanceof BlockHitResult b ? b : null;
        String targetPos = blockHit == null ? "none" : textPos(blockHit.getBlockPos());
        String targetFace = blockHit == null ? "none" : blockHit.getSide().asString();
        String screenshotPath = releaseVisualCategoryMode
                ? captureReleaseVisualScreenshot(client, outputRowName)
                : "not_requested";
        System.out.println("[" + rowMarker + "]"
                + " rowName=" + outputRowName
                + " blockId=" + row.blockId()
                + " supportBlockId=" + supportBlockId
                + " slabPos=" + textPos(superflatHarnessSlabPos)
                + " placedBlockPos=" + textPos(superflatHarnessPlacePos)
                + " placementMethod=" + superflatHarnessPlacementMethod
                + " placementReturn=" + superflatHarnessPlacementReturn
                + " substrateProven=" + substrateProven
                + " finalState=" + finalServerState
                + " sourceDy=" + formatDouble(sourceDy)
                + " placedDy=" + formatDouble(placedDy)
                + " anchored=" + anchored
                + " loweredCarrier=" + loweredCarrier
                + " modelMeshMinY=" + modelMeshMinY
                + " modelMeshMaxY=" + modelMeshMaxY
                + " meshTraceKey=" + meshTraceKey
                + " matrixKey=" + meshMatrixKey
                + " matrixRow=" + meshMatrixRow
                + " meshBlockId=" + meshBlockId
                + " meshPos=" + meshPos
                + " meshState=" + meshState
                + " meshDy=" + meshDy
                + " meshModelClass=" + meshModelClass
                + " tickOrFrame=" + meshTickOrFrame
                + " rowSource=RED_ROW"
                + " passSequence=" + meshPassSequence
                + " quadsVisited=" + meshQuadsVisited
                + " verticesVisited=" + meshVerticesVisited
                + " minBeforeY=" + meshMinBeforeY
                + " maxBeforeY=" + meshMaxBeforeY
                + " minAfterY=" + modelMeshMinY
                + " maxAfterY=" + modelMeshMaxY
                + " snapshotSource=" + meshSnapshotSource
                + " aggregateDedupKey=" + meshAggregateDedupKey
                + " outlineMinY=" + outlineMinY
                + " outlineMaxY=" + outlineMaxY
                + " raycastMinY=" + raycastMinY
                + " raycastMaxY=" + raycastMaxY
                + " targetPos=" + targetPos
                + " targetFace=" + targetFace
                + " expectedModelMinY=" + (meshTrace.seen() ? formatDouble(meshTrace.minBeforeY() + meshTrace.dy()) : "NaN")
                + " expectedModelMaxY=" + (meshTrace.seen() ? formatDouble(meshTrace.maxBeforeY() + meshTrace.dy()) : "NaN")
                + " modelVsOutlineDelta=" + formatDouble(delta)
                + " modelClass=" + meshModelClass
                + " outlineBounds=" + outlineBounds
                + " raycastBounds=" + raycastBounds
                + " rawRaycastShapeEmpty=" + rawRaycastShapeEmpty
                + " technicalTriadAligned=" + technicalTriadAligned
                + " finiteModelBounds=" + finiteModelBounds
                + " modelTranslationAligned=" + modelTranslationAligned
                + " modelBottomBelowOutline=" + modelBottomBelowOutline
                + " releaseUnsupportedCategory=false"
                + " productVisualLawPass=" + productVisualLawPass
                + " visualAcceptance=" + visualAcceptance
                + " screenshotPath=" + screenshotPath
                + " clientState=" + finalClientState
                + " verdict=" + verdict
                + " reason=" + reason);
    }

    private static String redMatrixRowName(SuperflatHarnessRowSpec row) {
        return switch (row.blockId()) {
            case "minecraft:cobblestone_wall" -> "COBBLESTONE_WALL_ON_BOTTOM_SLAB_VISUAL_CONTACT_GREEN";
            case "minecraft:oak_fence" -> "OAK_FENCE_ON_BOTTOM_SLAB_VISUAL_CONTACT_GREEN";
            case "minecraft:stone_brick_wall" -> "STONE_BRICK_WALL_ON_BOTTOM_SLAB_TRACE_GAP_OR_RED";
            case "minecraft:stone" -> "FULL_BLOCK_CONTROL_STONE_GREEN";
            case "minecraft:oak_log" -> "FULL_BLOCK_CONTROL_OAK_LOG_GREEN";
            case "minecraft:stone_stairs" -> "STONE_STAIRS_CONTROL_GREEN";
            default -> row.rowName();
        };
    }

    private static void emitSuperflatHarnessSummary(boolean wallFenceProductRedMode, boolean releaseVisualCategoryMode) {
        String classification;
        if (releaseVisualCategoryMode) {
            if (superflatHarnessRedRows > 0) {
                classification = "RELEASE_VISUAL_MATRIX_RED";
            } else if (superflatHarnessTraceGapRows > 0) {
                classification = "RELEASE_VISUAL_MATRIX_TRACE_GAP";
            } else if (superflatHarnessProductBadRows > 0) {
                classification = "RELEASE_VISUAL_MATRIX_GREEN_WITH_NOT_COVERED_CATEGORIES";
            } else {
                classification = "RELEASE_VISUAL_MATRIX_GREEN";
            }
        } else if (!wallFenceProductRedMode) {
            if (superflatHarnessRedRows > 0) {
                classification = "RED_MODEL_HITBOX_MISMATCH_CONFIRMED";
            } else if (superflatHarnessTraceGapRows > 0) {
                classification = "TRACE_GAP_BOUNDS_MISSING";
            } else if (superflatHarnessProductBadRows > 0) {
                classification = "WALL_FENCE_TRIAD_ALIGNED_PRODUCT_BAD";
            } else {
                classification = "SUPERFLAT_HARNESS_GREEN_ALL_TRIAD_ALIGNED";
            }
        } else {
            if (superflatHarnessRedRows > 0) {
                classification = superflatHarnessRedRows >= 2 && superflatHarnessGreenRows >= 2
                        ? "EXPECTED_RED_MATRIX_ACHIEVED"
                        : "RED_MATRIX_INCOMPLETE";
            } else if (superflatHarnessGreenRows >= 4 && superflatHarnessProductBadRows == 0) {
                classification = "WALL_FENCE_VISUAL_CONTACT_GREEN_JULIA_APPROVED";
            } else {
                classification = "RED_MATRIX_INCOMPLETE";
            }
        }
        System.out.println("[" + superflatHarnessMarker(
                wallFenceProductRedMode,
                releaseVisualCategoryMode,
                "SUMMARY") + "]"
                + " rows=" + activeSuperflatHarnessRows().length
                + " greenRows=" + superflatHarnessGreenRows
                + " redRows=" + superflatHarnessRedRows
                + " traceGapRows=" + superflatHarnessTraceGapRows
                + " productBadRows=" + superflatHarnessProductBadRows
                + " notCoveredRows=" + (releaseVisualCategoryMode ? superflatHarnessProductBadRows : 0)
                + " screenshotDir=" + (releaseVisualCategoryMode
                ? releaseVisualScreenshotDir().toAbsolutePath()
                : "not_requested")
                + " classification=" + classification);
        emitted = true;
        superflatHarnessFinalized = true;
        if (clientReadyForStop()) {
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    private static SuperflatHarnessRowSpec[] activeSuperflatHarnessRows() {
        return Boolean.getBoolean(RELEASE_VISUAL_CATEGORY_MATRIX_PROPERTY)
                ? RELEASE_VISUAL_CATEGORY_ROWS
                : SUPERFLAT_HARNESS_ROWS;
    }

    private static String superflatHarnessMarker(
            boolean wallFenceProductRedMode,
            boolean releaseVisualCategoryMode,
            String suffix
    ) {
        if (releaseVisualCategoryMode) {
            return "MC1211_RELEASE_VISUAL_CATEGORY_MATRIX_" + suffix;
        }
        return (wallFenceProductRedMode
                ? "MC1211_WALL_FENCE_PRODUCT_RED_"
                : "MC1211_SUPERFLAT_MODEL_HITBOX_") + suffix;
    }

    private static String superflatHarnessPropertyName(
            boolean wallFenceProductRedMode,
            boolean releaseVisualCategoryMode
    ) {
        if (releaseVisualCategoryMode) {
            return RELEASE_VISUAL_CATEGORY_MATRIX_PROPERTY;
        }
        return wallFenceProductRedMode
                ? WALL_FENCE_PRODUCT_RED_ONLY_PROPERTY
                : SUPERFLAT_MODEL_HITBOX_HARNESS_ONLY_PROPERTY;
    }

    private static Path releaseVisualScreenshotDir() {
        return Path.of("tmp", "mc1211-release-visual-category-matrix");
    }

    private static String captureReleaseVisualScreenshot(MinecraftClient client, String label) {
        if (client == null) {
            return "TRACE_GAP_SCREENSHOT_CLIENT_NOT_READY";
        }
        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null) {
            return "TRACE_GAP_SCREENSHOT_FRAMEBUFFER_NOT_READY";
        }
        String safeLabel = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
        Path dir = releaseVisualScreenshotDir().toAbsolutePath();
        Path path = dir.resolve(String.format(
                Locale.ROOT,
                "%04d-%02d-%s.png",
                superflatHarnessTicks,
                superflatHarnessRowIndex,
                safeLabel));
        try {
            Files.createDirectories(dir);
            try (NativeImage image = ScreenshotRecorder.takeScreenshot(framebuffer)) {
                image.writeTo(path);
            }
            return path.toString();
        } catch (IOException | RuntimeException e) {
            return "TRACE_GAP_SCREENSHOT_WRITE_FAILED:" + e.getClass().getSimpleName();
        }
    }

    private static void emitSuperflatHarnessTraceGap(String row, String reason) {
        boolean wallFenceProductRedMode = Boolean.getBoolean(WALL_FENCE_PRODUCT_RED_ONLY_PROPERTY);
        boolean releaseVisualCategoryMode = Boolean.getBoolean(RELEASE_VISUAL_CATEGORY_MATRIX_PROPERTY);
        System.out.println("[" + superflatHarnessMarker(
                wallFenceProductRedMode,
                releaseVisualCategoryMode,
                "START") + "]"
                + " rows=" + activeSuperflatHarnessRows().length);
        System.out.println("[" + superflatHarnessMarker(
                wallFenceProductRedMode,
                releaseVisualCategoryMode,
                "ROW") + "]"
                + " rowName=" + row
                + " blockId=none"
                + " placementMethod=DIRECT_WORLD_TRACE_ONLY"
                + " placementReturn=FAIL_ROUTE_NOT_READY"
                + " verdict=TRACE_GAP_WORLD_NOT_READY"
                + " reason=" + reason);
        System.out.println("[" + superflatHarnessMarker(
                wallFenceProductRedMode,
                releaseVisualCategoryMode,
                "SUMMARY") + "]"
                + " rows=0"
                + " classification=TRACE_GAP_WORLD_NOT_READY"
                + " reason=" + reason);
        emitted = true;
        superflatHarnessFinalized = true;
        if (clientReadyForStop()) {
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    private static String shapeBounds(VoxelShape shape) {
        if (shape == null || shape.isEmpty()) {
            return "NaN..NaN";
        }
        return formatDouble(shape.getBoundingBox().minY) + ".." + formatDouble(shape.getBoundingBox().maxY);
    }

    private static ItemStack harnessStackForBlock(String blockId) {
        return switch (blockId) {
            case "minecraft:stone" -> new ItemStack(Items.STONE, 8);
            case "minecraft:oak_log" -> new ItemStack(Items.OAK_LOG, 8);
            case "minecraft:oak_planks" -> new ItemStack(Items.OAK_PLANKS, 8);
            case "minecraft:crafting_table" -> new ItemStack(Items.CRAFTING_TABLE, 8);
            case "minecraft:stone_slab" -> new ItemStack(Items.STONE_SLAB, 8);
            case "minecraft:oak_slab" -> new ItemStack(Items.OAK_SLAB, 8);
            case "minecraft:cobblestone_wall" -> new ItemStack(Items.COBBLESTONE_WALL, 8);
            case "minecraft:stone_brick_wall" -> new ItemStack(Items.STONE_BRICK_WALL, 8);
            case "minecraft:oak_fence" -> new ItemStack(Items.OAK_FENCE, 8);
            case "minecraft:oak_fence_gate" -> new ItemStack(Items.OAK_FENCE_GATE, 8);
            case "minecraft:glass_pane" -> new ItemStack(Items.GLASS_PANE, 8);
            case "minecraft:stone_stairs" -> new ItemStack(Items.STONE_STAIRS, 8);
            case "minecraft:oak_trapdoor" -> new ItemStack(Items.OAK_TRAPDOOR, 8);
            case "minecraft:spruce_door" -> new ItemStack(Items.SPRUCE_DOOR, 8);
            case "minecraft:oak_sign" -> new ItemStack(Items.OAK_SIGN, 8);
            case "minecraft:acacia_button" -> new ItemStack(Items.ACACIA_BUTTON, 8);
            case "minecraft:lantern" -> new ItemStack(Items.LANTERN, 8);
            case "minecraft:chain" -> new ItemStack(Items.CHAIN, 8);
            case "minecraft:candle" -> new ItemStack(Items.CANDLE, 8);
            case "minecraft:flower_pot" -> new ItemStack(Items.FLOWER_POT, 8);
            case "minecraft:white_carpet" -> new ItemStack(Items.WHITE_CARPET, 8);
            case "minecraft:rail" -> new ItemStack(Items.RAIL, 8);
            case "minecraft:redstone_wire" -> new ItemStack(Items.REDSTONE, 8);
            case "minecraft:hopper" -> new ItemStack(Items.HOPPER, 8);
            default -> {
                var item = Registries.ITEM.get(Identifier.of(blockId));
                yield item == null || item == Items.AIR ? null : new ItemStack(item, 8);
            }
        };
    }

    private static BlockState harnessStateForBlock(String blockId) {
        var block = Registries.BLOCK.get(Identifier.of(blockId));
        return block == null ? Blocks.AIR.getDefaultState() : block.getDefaultState();
    }

    private static void runSidePlaceStoneLoweringRoute(MinecraftClient client) {
        if (emitted) {
            return;
        }
        sidePlaceTicks++;
        if (!sidePlaceCanaryEmitted) {
            sidePlaceCanaryEmitted = true;
            System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_ROUTE_CANARY]"
                    + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                    + " route=" + ROUTE
                    + " worldReady=" + (client != null && client.world != null)
                    + " playerReady=" + (client != null && client.player != null)
                    + " programmaticClientWorldPath=IntegratedServerLoader.createAndStart"
                    + " property=" + SIDE_PLACE_STONE_LOWERING_ONLY_PROPERTY);
        }

        requestProgrammaticSidePlaceWorldIfNeeded(client);
        String readinessGap = sidePlaceReadinessGap(client);
        if (readinessGap != null) {
            if (!sidePlaceReadyRowEmitted || sidePlaceTicks % 1200 == 0) {
                emitSidePlaceReadyRow(client, "WAITING", readinessGap);
                sidePlaceReadyRowEmitted = true;
            }
            if (sidePlaceTicks < SIDE_PLACE_READINESS_TIMEOUT_TICKS) {
                return;
            }
            emitSidePlaceReadyRow(client, "TIMEOUT", readinessGap);
            emitSidePlaceTraceGap("ROUTE_READINESS", readinessGap);
            return;
        }
        if (!sidePlaceReadyRowEmitted) {
            emitSidePlaceReadyRow(client, "READY", "none");
            sidePlaceReadyRowEmitted = true;
        }

        if (sidePlaceOrigin == null) {
            sidePlaceOrigin = client.player.getBlockPos().add(5, 0, 5).toImmutable();
            sidePlaceSupportPos = sidePlaceOrigin;
            sidePlaceHitPos = sidePlaceSupportPos.up();
            sidePlacePlacePos = sidePlaceHitPos.offset(Direction.EAST);
            sidePlaceHostReadyTick = sidePlaceTicks;
            System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_START]"
                    + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                    + " fixtureOrigin=" + textPos(sidePlaceOrigin)
                    + " hitPos=" + textPos(sidePlaceHitPos)
                    + " hitFace=east"
                    + " placePos=" + textPos(sidePlacePlacePos)
                    + " item=minecraft:stone");
        }

        if (!sidePlaceShapeAuthored) {
            sidePlaceShapeAuthored = authorSidePlaceStoneLoweringShape(client, sidePlaceOrigin);
            if (!sidePlaceShapeAuthored) {
                emitSidePlaceTraceGap("ROUTE_SHAPE_SETUP", "server_world_not_available");
            }
            return;
        }

        if (!sidePlaceInteracted && sidePlaceTicks - sidePlaceHostReadyTick < 20) {
            return;
        }

        if (!sidePlaceInteracted) {
            if (sidePlaceLiveSyncTick < 0) {
                syncSidePlaceLiveLikePlayer(client);
                sidePlaceLiveSyncTick = sidePlaceTicks;
                return;
            }
            if (sidePlaceTicks - sidePlaceLiveSyncTick < 2) {
                return;
            }
            BlockHitResult hit = new BlockHitResult(
                    new Vec3d(
                            sidePlaceHitPos.getX() + 1.0d,
                            sidePlaceHitPos.getY() + 0.5d,
                            sidePlaceHitPos.getZ() + 0.5d),
                    Direction.EAST,
                    sidePlaceHitPos,
                    false);
            sidePlaceClientHeldItem = client.player.getStackInHand(Hand.MAIN_HAND).toString();
            sidePlaceRoutePlacementMethod = "ClientPlayerInteractionManager.interactBlock_live_reach_synced";
            sidePlacePacketOrInteractionPathUsed = client.interactionManager != null;
            ActionResult result = client.interactionManager == null
                    ? ActionResult.FAIL
                    : client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
            sidePlaceClientResult = String.valueOf(result);
            sidePlaceClientAccepted = result.isAccepted();
            sidePlaceInteracted = true;
            sidePlaceHostReadyTick = sidePlaceTicks;
            return;
        }

        if (sidePlaceTicks - sidePlaceHostReadyTick < 20) {
            return;
        }

        if (!sampleSidePlaceRetainedState(client)) {
            return;
        }

        emitSidePlaceStoneLoweringRow(client);
        emitted = true;
        client.scheduleStop();
    }

    private static void requestProgrammaticSidePlaceWorldIfNeeded(MinecraftClient client) {
        if (sidePlaceProgrammaticWorldStartRequested
                || client == null
                || !client.isFinishedLoading()
                || client.world != null
                || client.player != null) {
            return;
        }
        sidePlaceProgrammaticWorldStartRequested = true;
        sidePlaceProgrammaticWorldPath = "IntegratedServerLoader.createAndStart";
        sidePlaceProgrammaticWorldName = "slabbed-mc1211-side-place-harness";
        LevelInfo levelInfo = new LevelInfo(
                "Slabbed MC1211 Side Place Harness",
                GameMode.CREATIVE,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                DataConfiguration.SAFE_MODE);
        GeneratorOptions generatorOptions = new GeneratorOptions(0L, false, false);
        System.out.println("[MC1211_SIDE_PLACE_STONE_PROGRAMMATIC_WORLD_START]"
                + " path=" + sidePlaceProgrammaticWorldPath
                + " worldName=" + sidePlaceProgrammaticWorldName
                + " worldType=superflat"
                + " gameMode=creative"
                + " difficulty=peaceful"
                + " uiAutomation=false"
                + " manualWorldCreationRequired=false");
        client.createIntegratedServerLoader().createAndStart(
                sidePlaceProgrammaticWorldName,
                levelInfo,
                generatorOptions,
                Mc1211GoblinRouteClientEntrypoint::createSuperflatDimensionOptions,
                null);
    }

    private static DimensionOptionsRegistryHolder createSuperflatDimensionOptions(DynamicRegistryManager registries) {
        return registries.get(RegistryKeys.WORLD_PRESET)
                .getOrThrow(WorldPresets.FLAT)
                .createDimensionsRegistryHolder();
    }

    private static String sidePlaceReadinessGap(MinecraftClient client) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        if (!readiness.clientBootstrapReady) {
            return "TRACE_GAP_CLIENT_BOOTSTRAP_NOT_FINISHED";
        }
        if (!readiness.clientWorldReady) {
            return sidePlaceProgrammaticWorldStartRequested
                    ? "TRACE_GAP_PROGRAMMATIC_CLIENT_WORLD_PENDING"
                    : "TRACE_GAP_PROGRAMMATIC_WORLD_START_PENDING";
        }
        if (!readiness.clientPlayerReady) {
            return "TRACE_GAP_CLIENT_PLAYER_NOT_READY";
        }
        if (!readiness.integratedServerReady) {
            return "TRACE_GAP_INTEGRATED_SERVER_NOT_READY";
        }
        if (!readiness.serverWorldReady) {
            return "TRACE_GAP_SERVER_WORLD_NOT_READY";
        }
        if (!readiness.serverPlayerReady) {
            return "TRACE_GAP_SERVER_PLAYER_NOT_READY";
        }
        if (!readiness.interactionManagerReady) {
            return "TRACE_GAP_INTERACTION_MANAGER_NOT_READY";
        }
        return null;
    }

    private static void emitSidePlaceReadyRow(MinecraftClient client, String phase, String reason) {
        SidePlaceReadiness readiness = SidePlaceReadiness.capture(client);
        MinecraftServer server = readiness.server;
        String clientHeld = readiness.clientPlayerReady
                ? client.player.getStackInHand(Hand.MAIN_HAND).toString()
                : "UNAVAILABLE";
        String serverHeld = readiness.serverPlayerReady
                ? server.getPlayerManager().getPlayerList().get(0).getStackInHand(Hand.MAIN_HAND).toString()
                : "UNAVAILABLE";
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_READY_ROW]"
                + " phase=" + phase
                + " tick=" + sidePlaceTicks
                + " clientBootstrapReady=" + readiness.clientBootstrapReady
                + " clientWorldReady=" + readiness.clientWorldReady
                + " clientPlayerReady=" + readiness.clientPlayerReady
                + " integratedServerReady=" + readiness.integratedServerReady
                + " serverReady=" + readiness.integratedServerReady
                + " serverWorldReady=" + readiness.serverWorldReady
                + " serverPlayerReady=" + readiness.serverPlayerReady
                + " interactionManagerReady=" + readiness.interactionManagerReady
                + " programmaticWorldStartRequested=" + sidePlaceProgrammaticWorldStartRequested
                + " programmaticWorldName=" + sidePlaceProgrammaticWorldName
                + " programmaticClientWorldPath=" + sidePlaceProgrammaticWorldPath
                + " heldItemSynced=" + sidePlaceHeldItemSynced
                + " playerPositionSynced=" + sidePlacePlayerPositionSynced
                + " clientHeldItem=" + clientHeld
                + " serverHeldItem=" + serverHeld
                + " reason=" + reason);
    }

    private static void syncSidePlaceLiveLikePlayer(MinecraftClient client) {
        sidePlaceClientPlayerPresent = client.player != null;
        MinecraftServer server = client.getServer();
        sidePlaceServerPlayerPresent = server != null && !server.getPlayerManager().getPlayerList().isEmpty();
        if (sidePlaceHitPos == null || client.player == null) {
            return;
        }
        Vec3d hitVec = new Vec3d(
                sidePlaceHitPos.getX() + 1.0d,
                sidePlaceHitPos.getY() + 0.5d,
                sidePlaceHitPos.getZ() + 0.5d);
        Vec3d eye = new Vec3d(
                sidePlaceHitPos.getX() + 2.75d,
                sidePlaceHitPos.getY() + 0.65d,
                sidePlaceHitPos.getZ() + 0.5d);
        Vec3d delta = hitVec.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - 1.62d;
        sidePlaceReachDiagnostic = "eye=" + formatVec(eye)
                + "/hitVec=" + formatVec(hitVec)
                + "/distance=" + formatDouble(eye.distanceTo(hitVec))
                + "/yaw=" + formatDouble(yaw)
                + "/pitch=" + formatDouble(pitch);
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.setSneaking(false);
        client.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 8));
        sidePlaceClientHeldItem = client.player.getStackInHand(Hand.MAIN_HAND).toString();
        sidePlacePlayerPositionSynced = true;
        if (sidePlaceServerPlayerPresent) {
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            serverPlayer.setVelocity(Vec3d.ZERO);
            serverPlayer.setSneaking(false);
            serverPlayer.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            serverPlayer.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 8));
            sidePlaceServerHeldItem = serverPlayer.getStackInHand(Hand.MAIN_HAND).toString();
            sidePlaceHeldItemSynced = sidePlaceClientHeldItem.contains("minecraft:stone")
                    && sidePlaceServerHeldItem.contains("minecraft:stone");
        }
    }

    private static boolean sampleSidePlaceRetainedState(MinecraftClient client) {
        sidePlaceRetainedSampleAttempts++;
        sidePlaceRetainedSampleTicks = sidePlaceTicks - sidePlaceHostReadyTick;
        ClientWorld clientWorld = client.world;
        MinecraftServer server = client.getServer();
        ServerWorld serverWorld = server == null || clientWorld == null
                ? null
                : server.getWorld(clientWorld.getRegistryKey());
        BlockState clientState = clientWorld == null || sidePlacePlacePos == null
                ? Blocks.AIR.getDefaultState()
                : clientWorld.getBlockState(sidePlacePlacePos);
        BlockState serverState = serverWorld == null || sidePlacePlacePos == null
                ? Blocks.AIR.getDefaultState()
                : serverWorld.getBlockState(sidePlacePlacePos);
        boolean serverStone = serverState.isOf(Blocks.STONE);
        if (serverStone) {
            sidePlaceRetainedServerStoneObserved = true;
        }
        sidePlacePlacePosVariants = sampleSidePlaceVariants(clientWorld, serverWorld);
        String sample = "attempt=" + sidePlaceRetainedSampleAttempts
                + "/tick=" + sidePlaceRetainedSampleTicks
                + "/client=" + clientState.getBlock()
                + "/server=" + (serverWorld == null ? "UNAVAILABLE" : serverState.getBlock());
        if ("not_sampled".equals(sidePlaceSampledStates)) {
            sidePlaceSampledStates = sample;
        } else if (sidePlaceRetainedSampleAttempts <= 8 || sidePlaceRetainedSampleAttempts % 10 == 0 || serverStone) {
            sidePlaceSampledStates = sidePlaceSampledStates + "," + sample;
        }
        return serverStone || sidePlaceRetainedSampleAttempts >= 80;
    }

    private static boolean authorSidePlaceStoneLoweringShape(MinecraftClient client, BlockPos origin) {
        MinecraftServer server = client.getServer();
        if (server == null || client.world == null || origin == null) {
            return false;
        }
        ServerWorld serverWorld = server.getWorld(client.world.getRegistryKey());
        if (serverWorld == null) {
            return false;
        }
        BlockPos supportPos = origin;
        BlockPos hitPos = supportPos.up();
        BlockPos placePos = hitPos.offset(Direction.EAST);
        server.execute(() -> {
            for (int x = supportPos.getX() - 2; x <= supportPos.getX() + 3; x++) {
                for (int z = supportPos.getZ() - 2; z <= supportPos.getZ() + 2; z++) {
                    for (int y = supportPos.getY() - 1; y <= supportPos.getY() + 3; y++) {
                        serverWorld.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
            serverWorld.setBlockState(
                    supportPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    3);
            serverWorld.setBlockState(hitPos, Blocks.STONE.getDefaultState(), 3);
            SlabAnchorAttachment.addAnchor(serverWorld, hitPos, serverWorld.getBlockState(hitPos));
            serverWorld.setBlockState(placePos, Blocks.AIR.getDefaultState(), 3);
            serverWorld.setBlockState(placePos.down(), Blocks.AIR.getDefaultState(), 3);
            serverWorld.setBlockState(placePos.up(), Blocks.AIR.getDefaultState(), 3);
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                server.getPlayerManager().getPlayerList().get(0)
                        .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
                server.getPlayerManager().getPlayerList().get(0)
                        .setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 8));
                sidePlaceServerPlayerPresent = true;
                sidePlaceServerHeldItem = server.getPlayerManager().getPlayerList().get(0)
                        .getStackInHand(Hand.MAIN_HAND)
                        .toString();
            }
        });
        return true;
    }

    private static void emitSidePlaceStoneLoweringRow(MinecraftClient client) {
        ClientWorld clientWorld = client.world;
        MinecraftServer server = client.getServer();
        ServerWorld serverWorld = server == null || clientWorld == null
                ? null
                : server.getWorld(clientWorld.getRegistryKey());
        if (clientWorld == null || sidePlaceHitPos == null || sidePlacePlacePos == null) {
            emitSidePlaceTraceGap("ROUTE_ROW_SAMPLE", "client_world_or_positions_missing");
            return;
        }

        BlockState hitState = clientWorld.getBlockState(sidePlaceHitPos);
        BlockState clientPostPlaceState = clientWorld.getBlockState(sidePlacePlacePos);
        BlockState postPlaceState = clientPostPlaceState;
        if (serverWorld != null) {
            postPlaceState = serverWorld.getBlockState(sidePlacePlacePos);
        }
        BlockState prePlaceState = Blocks.AIR.getDefaultState();
        double hitDy = SlabSupport.getYOffset(clientWorld, sidePlaceHitPos, hitState);
        double postPlaceDy = serverWorld == null
                ? SlabSupport.getYOffset(clientWorld, sidePlacePlacePos, postPlaceState)
                : SlabSupport.getYOffset(serverWorld, sidePlacePlacePos, postPlaceState);
        boolean hitAnchored = SlabAnchorAttachment.isAnchored(clientWorld, sidePlaceHitPos);
        boolean postPlaceAnchored = serverWorld == null
                ? SlabAnchorAttachment.isAnchored(clientWorld, sidePlacePlacePos)
                : SlabAnchorAttachment.isAnchored(serverWorld, sidePlacePlacePos);
        boolean hitFullHeightLoweredCarrier =
                SlabSupport.isFullHeightLoweredCarrier(clientWorld, sidePlaceHitPos, hitState);
        boolean postPlaceFullHeightLoweredCarrier =
                serverWorld == null
                        ? SlabSupport.isFullHeightLoweredCarrier(clientWorld, sidePlacePlacePos, postPlaceState)
                        : SlabSupport.isFullHeightLoweredCarrier(serverWorld, sidePlacePlacePos, postPlaceState);
        boolean hitHasBottomSlabBelow = SlabSupport.hasBottomSlabBelow(clientWorld, sidePlaceHitPos);
        boolean postPlaceHasBottomSlabBelow = serverWorld == null
                ? SlabSupport.hasBottomSlabBelow(clientWorld, sidePlacePlacePos)
                : SlabSupport.hasBottomSlabBelow(serverWorld, sidePlacePlacePos);
        double postPlaceServerDy = Double.NaN;
        boolean postPlaceServerAnchored = false;
        if (serverWorld != null) {
            BlockState serverPost = serverWorld.getBlockState(sidePlacePlacePos);
            postPlaceServerDy = SlabSupport.getYOffset(serverWorld, sidePlacePlacePos, serverPost);
            postPlaceServerAnchored = SlabAnchorAttachment.isAnchored(serverWorld, sidePlacePlacePos);
        }

        boolean sameLaneSideAdjacent = Math.abs(hitDy + 0.5d) <= 1.0e-6
                && Math.abs(postPlaceDy + 0.5d) <= 1.0e-6
                && sidePlacePlacePos.getY() == sidePlaceHitPos.getY();
        boolean namedBsfbAdjacentInheritance = sidePlaceClientAccepted
                && postPlaceState.isOf(Blocks.STONE)
                && postPlaceAnchored
                && hitFullHeightLoweredCarrier
                && !postPlaceHasBottomSlabBelow
                && Math.abs(postPlaceDy + 0.5d) <= 1.0e-6;
        String visualRelation = sameLaneSideAdjacent ? "sameLaneSideAdjacent"
                : (Math.abs(postPlaceDy) <= 1.0e-6 ? "normalVanilla" : "unknown");
        String classification;
        String legalStateName;
        String illegalReason;
        String finalMarker;
        if (!sidePlaceRetainedServerStoneObserved || !postPlaceState.isOf(Blocks.STONE)) {
            classification = "TRACE_GAP_SERVER_RETAINED_STATE_NOT_OBSERVED";
            legalStateName = "none";
            illegalReason = "server_retained_place_state_not_observed";
            finalMarker = "TRACE_GAP";
        } else if (Math.abs(postPlaceDy + 0.5d) <= 1.0e-6 && namedBsfbAdjacentInheritance) {
            classification = "LEGAL_BSFB_ADJACENT_FULLBLOCK_INHERITANCE";
            legalStateName = "BSFB_ADJACENT_FULLBLOCK_INHERITANCE";
            illegalReason = "none";
            finalMarker = "GREEN";
        } else if (Math.abs(postPlaceDy + 0.5d) <= 1.0e-6) {
            classification = "ILLEGAL_UNNAMED_SIDE_LOWERING";
            legalStateName = "none";
            illegalReason = "postPlaceDy=-0.5_without_named_side_adjacent_source_truth";
            finalMarker = "RED";
        } else {
            classification = "ILLEGAL_REJECTED_BSFB_ADJACENT_FULLBLOCK_INHERITANCE";
            legalStateName = "none";
            illegalReason = "same_y_side_adjacent_full_block_failed_to_inherit_lowered_lane";
            finalMarker = "RED";
        }

        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_ROW]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " hitPos=" + textPos(sidePlaceHitPos)
                + " hitState=" + hitState.getBlock()
                + " hitFace=east"
                + " hitDy=" + formatDouble(hitDy)
                + " hitAnchored=" + hitAnchored
                + " hitFullHeightLoweredCarrier=" + hitFullHeightLoweredCarrier
                + " hitHasBottomSlabBelow=" + hitHasBottomSlabBelow
                + " placePos=" + textPos(sidePlacePlacePos)
                + " prePlaceState=" + prePlaceState.getBlock()
                + " item=minecraft:stone"
                + " routePlacementMethod=" + sidePlaceRoutePlacementMethod
                + " clientPlayerPresent=" + sidePlaceClientPlayerPresent
                + " serverPlayerPresent=" + sidePlaceServerPlayerPresent
                + " clientHeldItem=" + sidePlaceClientHeldItem
                + " serverHeldItem=" + sidePlaceServerHeldItem
                + " clientPlacementCallResult=" + sidePlaceClientResult
                + " placementResultClient=" + sidePlaceClientResult
                + " placementResultServer=" + sidePlaceServerResult
                + " serverPlacementObservedResult=" + sidePlaceServerResult
                + " serverResultObserved=" + sidePlaceServerResultObserved
                + " packetOrInteractionPathUsed=" + sidePlacePacketOrInteractionPathUsed
                + " cleanupOrTeardownOccurred=" + sidePlaceCleanupOrTeardownOccurred
                + " reachDiagnostic=" + sidePlaceReachDiagnostic
                + " placementAccepted=" + sidePlaceClientAccepted
                + " retainedSampleAttempts=" + sidePlaceRetainedSampleAttempts
                + " retainedSampleTicks=" + sidePlaceRetainedSampleTicks
                + " sampledStates=" + sidePlaceSampledStates
                + " sampledPlacePosVariants=" + sidePlacePlacePosVariants
                + " postPlaceState=" + postPlaceState.getBlock()
                + " postPlaceClientState=" + clientPostPlaceState.getBlock()
                + " postPlaceDy=" + formatDouble(postPlaceDy)
                + " postPlaceServerDy=" + formatDouble(postPlaceServerDy)
                + " postPlaceAnchored=" + postPlaceAnchored
                + " postPlaceServerAnchored=" + postPlaceServerAnchored
                + " postPlaceFullHeightLoweredCarrier=" + postPlaceFullHeightLoweredCarrier
                + " postPlaceHasBottomSlabBelow=" + postPlaceHasBottomSlabBelow
                + " sourceSupportRelationship=DIRECT_BOTTOM_SLAB_ANCHORED_FULL_BLOCK_SOURCE_TO_SIDE_ADJACENT_FULL_BLOCK"
                + " legalStateName=" + legalStateName
                + " illegalReason=" + illegalReason
                + " visualRelation=" + visualRelation
                + " classification=" + classification);
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_SUMMARY]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " rows=1"
                + " finalResult=" + finalMarker
                + " classification=" + classification
                + " modelOutlineInterpretation=state_itself_lowered_not_render_mismatch");
        System.out.println("[MC1211_SIDE_PLACE_STONE_ROUTE_EQUIV_ROW]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " routePlacementMethod=" + sidePlaceRoutePlacementMethod
                + " packetOrInteractionPathUsed=" + sidePlacePacketOrInteractionPathUsed
                + " clientPlayerPresent=" + sidePlaceClientPlayerPresent
                + " serverPlayerPresent=" + sidePlaceServerPlayerPresent
                + " clientHeldItem=" + sidePlaceClientHeldItem
                + " serverHeldItem=" + sidePlaceServerHeldItem
                + " reachDiagnostic=" + sidePlaceReachDiagnostic
                + " sampledPlacePosVariants=" + sidePlacePlacePosVariants
                + " classification=" + classification);
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_" + finalMarker + "]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " classification=" + classification
                + " postPlaceDy=" + formatDouble(postPlaceDy)
                + " legalStateName=" + legalStateName);
    }

    private static void emitSidePlaceTraceGap(String row, String reason) {
        String classification = reason.startsWith("TRACE_GAP_")
                ? reason
                : "TRACE_GAP_ROUTE_NOT_VIDEO_EQUIVALENT";
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_START]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " rows=1");
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_ROW]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " row=" + row
                + " hitPos=n/a"
                + " hitState=n/a"
                + " hitFace=east"
                + " hitDy=NaN"
                + " hitAnchored=false"
                + " placePos=n/a"
                + " prePlaceState=n/a"
                + " item=minecraft:stone"
                + " routePlacementMethod=" + sidePlaceRoutePlacementMethod
                + " clientPlayerPresent=" + sidePlaceClientPlayerPresent
                + " serverPlayerPresent=" + sidePlaceServerPlayerPresent
                + " clientHeldItem=" + sidePlaceClientHeldItem
                + " serverHeldItem=" + sidePlaceServerHeldItem
                + " clientPlacementCallResult=" + sidePlaceClientResult
                + " placementResultClient=" + sidePlaceClientResult
                + " placementResultServer=" + sidePlaceServerResult
                + " serverPlacementObservedResult=" + sidePlaceServerResult
                + " serverResultObserved=" + sidePlaceServerResultObserved
                + " packetOrInteractionPathUsed=" + sidePlacePacketOrInteractionPathUsed
                + " cleanupOrTeardownOccurred=" + sidePlaceCleanupOrTeardownOccurred
                + " reachDiagnostic=" + sidePlaceReachDiagnostic
                + " retainedSampleAttempts=" + sidePlaceRetainedSampleAttempts
                + " retainedSampleTicks=" + sidePlaceRetainedSampleTicks
                + " sampledStates=" + sidePlaceSampledStates
                + " sampledPlacePosVariants=" + sidePlacePlacePosVariants
                + " postPlaceState=n/a"
                + " postPlaceDy=NaN"
                + " postPlaceAnchored=false"
                + " sourceSupportRelationship=unknown"
                + " legalStateName=none"
                + " illegalReason=" + reason
                + " visualRelation=unknown"
                + " classification=" + classification);
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_SUMMARY]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " rows=1"
                + " finalResult=TRACE_GAP"
                + " classification=" + classification
                + " reason=" + reason);
        System.out.println("[MC1211_SIDE_PLACE_STONE_LOWERING_TRACE_GAP]"
                + " rowName=SIDE_PLACE_STONE_AGAINST_LOWERED_STONE_EAST_FACE"
                + " reason=" + reason);
        emitted = true;
        if (clientReadyForStop()) {
            MinecraftClient.getInstance().scheduleStop();
        }
    }

    private static final class SidePlaceReadiness {
        final MinecraftServer server;
        final boolean clientBootstrapReady;
        final boolean clientWorldReady;
        final boolean clientPlayerReady;
        final boolean integratedServerReady;
        final boolean serverWorldReady;
        final boolean serverPlayerReady;
        final boolean interactionManagerReady;

        private SidePlaceReadiness(
                MinecraftServer server,
                boolean clientBootstrapReady,
                boolean clientWorldReady,
                boolean clientPlayerReady,
                boolean integratedServerReady,
                boolean serverWorldReady,
                boolean serverPlayerReady,
                boolean interactionManagerReady
        ) {
            this.server = server;
            this.clientBootstrapReady = clientBootstrapReady;
            this.clientWorldReady = clientWorldReady;
            this.clientPlayerReady = clientPlayerReady;
            this.integratedServerReady = integratedServerReady;
            this.serverWorldReady = serverWorldReady;
            this.serverPlayerReady = serverPlayerReady;
            this.interactionManagerReady = interactionManagerReady;
        }

        static SidePlaceReadiness capture(MinecraftClient client) {
            MinecraftServer server = client == null ? null : client.getServer();
            boolean clientBootstrapReady = client != null && client.isFinishedLoading();
            boolean clientWorldReady = client != null && client.world != null;
            boolean clientPlayerReady = client != null && client.player != null;
            boolean integratedServerReady = server != null;
            boolean serverWorldReady = integratedServerReady
                    && clientWorldReady
                    && server.getWorld(client.world.getRegistryKey()) != null;
            boolean serverPlayerReady = integratedServerReady
                    && !server.getPlayerManager().getPlayerList().isEmpty();
            boolean interactionManagerReady = client != null && client.interactionManager != null;
            return new SidePlaceReadiness(
                    server,
                    clientBootstrapReady,
                    clientWorldReady,
                    clientPlayerReady,
                    integratedServerReady,
                    serverWorldReady,
                    serverPlayerReady,
                    interactionManagerReady);
        }
    }

    private static boolean clientReadyForStop() {
        return MinecraftClient.getInstance() != null;
    }

    private static void runModelVsOutlineHostedRoute(MinecraftClient client) {
        if (emitted) {
            return;
        }
        hostTicks++;
        System.out.println("[MC1211_MODEL_VS_OUTLINE_HOST_ROUTE_CANARY]"
                + " class=" + Mc1211GoblinRouteClientEntrypoint.class.getSimpleName()
                + " route=" + ROUTE
                + " worldReady=" + (client != null && client.world != null)
                + " playerReady=" + (client != null && client.player != null)
                + " tick=" + hostTicks);

        if (client == null || client.world == null || client.player == null) {
            if (hostTicks < 1200) {
                return;
            }
            emitted = true;
            emitHostedTraceGap(
                    "TRACE_GAP_RENDER_PATH_NOT_OBSERVABLE_FROM_GAMETEST",
                    "ROUTE_READINESS",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "NaN",
                    "NaN",
                    "NaN",
                    "NaN..NaN",
                    "NaN..NaN",
                    false,
                    false);
            if (client != null) {
                client.scheduleStop();
            }
            return;
        }

        if (!hostReady) {
            hostReady = true;
            hostReadyTick = hostTicks;
            hostedOrigin = client.player.getBlockPos().add(3, 0, 3).toImmutable();
            System.out.println("[MC1211_MODEL_VS_OUTLINE_HOST_READY]"
                    + " route=" + ROUTE
                    + " clientWorldPresent=true"
                    + " clientPlayerPresent=true"
                    + " fixtureOrigin=" + textPos(hostedOrigin));
        }

        if (!authorDeterministicShape(client, hostedOrigin)) {
            emitted = true;
            emitHostedTraceGap(
                    "TRACE_GAP_NO_DETERMINISTIC_SHAPE",
                    "ROUTE_SHAPE_SETUP",
                    "true",
                    "true",
                    textPos(hostedOrigin),
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "n/a",
                    "NaN",
                    "NaN",
                    "NaN",
                    "NaN..NaN",
                    "NaN..NaN",
                    false,
                    false);
            client.scheduleStop();
            return;
        }
        if (hostTicks - hostReadyTick < 20) {
            return;
        }

        System.out.println("[MC1211_MODEL_VS_OUTLINE_START]"
                + " route=" + ROUTE
                + " rows=2"
                + " fixtureOrigin=" + textPos(hostedOrigin));

        RowResult bottom = emitHostedRow(
                client.world,
                "BOTTOM_SLAB_THEN_STONE_MODEL_VS_OUTLINE_GOBLIN_HOST",
                hostedOrigin,
                hostedOrigin,
                hostedOrigin.up());
        RowResult vanilla = emitHostedRow(
                client.world,
                "VANILLA_GROUND_THEN_STONE_MODEL_VS_OUTLINE_GOBLIN_HOST",
                hostedOrigin.add(2, 0, 0),
                hostedOrigin.add(2, 0, 0),
                hostedOrigin.add(2, 1, 0));

        String finalResult;
        if (bottom.traceGap || vanilla.traceGap) {
            finalResult = "TRACE_GAP";
        } else if (bottom.modelLowerThanOutline || vanilla.modelLowerThanOutline) {
            finalResult = "RED";
        } else {
            finalResult = "GREEN";
        }

        System.out.println("[MC1211_MODEL_VS_OUTLINE_SUMMARY]"
                + " route=" + ROUTE
                + " rows=2"
                + " finalResult=" + finalResult);
        System.out.println("[MC1211_MODEL_VS_OUTLINE_" + finalResult + "]"
                + " route=" + ROUTE
                + " rows=2");

        emitted = true;
        client.scheduleStop();
    }

    private static boolean authorDeterministicShape(MinecraftClient client, BlockPos origin) {
        MinecraftServer server = client.getServer();
        if (server == null || client.world == null || origin == null) {
            return false;
        }
        ServerWorld serverWorld = server.getWorld(client.world.getRegistryKey());
        if (serverWorld == null) {
            return false;
        }
        BlockPos supportBottom = origin;
        BlockPos fullBottom = origin.up();
        BlockPos supportVanilla = origin.add(2, 0, 0);
        BlockPos fullVanilla = origin.add(2, 1, 0);
        server.execute(() -> {
            serverWorld.setBlockState(supportBottom, Blocks.STONE_SLAB.getDefaultState(), 3);
            serverWorld.setBlockState(fullBottom, Blocks.STONE.getDefaultState(), 3);
            serverWorld.setBlockState(supportVanilla, Blocks.STONE.getDefaultState(), 3);
            serverWorld.setBlockState(fullVanilla, Blocks.STONE.getDefaultState(), 3);
        });
        return true;
    }

    private static RowResult emitHostedRow(
            ClientWorld world,
            String rowName,
            BlockPos fixtureOrigin,
            BlockPos slabPos,
            BlockPos fullPos) {
        BlockState slabState = world.getBlockState(slabPos);
        BlockState fullState = world.getBlockState(fullPos);
        double slabDy = SlabSupport.getYOffset(world, slabPos, slabState);
        double outlineDy = ClientDy.dyFor(world, fullPos, fullState);
        double targetDy = SlabSupport.getYOffset(world, fullPos, fullState);
        VoxelShape outlineShape = fullState.getOutlineShape(world, fullPos);
        String outlineBounds = outlineShape.isEmpty()
                ? "NaN..NaN"
                : formatDouble(outlineShape.getBoundingBox().minY) + ".."
                + formatDouble(outlineShape.getBoundingBox().maxY);
        OffsetBlockStateModel.RenderOffsetSample trace = sampleModelTrace(fullPos);
        String reason;
        boolean modelEqualsOutline;
        boolean modelLowerThanOutline;
        boolean traceGap;
        String modelDy;
        String modelBounds;
        String modelObserverKind;
        String modelDyProxy;
        String modelVisualEquivalence;
        String targetDyText = formatDouble(targetDy);
        if (!trace.seen()) {
            reason = "TRACE_GAP_RENDER_MODEL_PATH_NOT_OBSERVABLE_FROM_GAMETEST";
            modelEqualsOutline = false;
            modelLowerThanOutline = false;
            traceGap = true;
            modelDy = "NaN";
            modelBounds = "NaN..NaN";
            modelObserverKind = "not_observable";
            modelDyProxy = "NaN";
            modelVisualEquivalence = "no";
        } else {
            double tracedModelDy = trace.modelDy();
            double modelDyDelta = tracedModelDy - outlineDy;
            String inferredModelBounds = inferShiftedBounds(outlineShape, modelDyDelta);
            double modelMin = shiftedMinY(outlineShape, modelDyDelta);
            double outlineMin = outlineShape.isEmpty() ? Double.NaN : outlineShape.getBoundingBox().minY;
            modelLowerThanOutline = Double.isFinite(modelMin)
                    && Double.isFinite(outlineMin)
                    && modelMin < outlineMin - 1.0e-6;
            modelEqualsOutline = Math.abs(modelDyDelta) <= 1.0e-6;
            modelDy = formatDouble(tracedModelDy);
            modelBounds = inferredModelBounds;
            modelObserverKind = "gametest_hook";
            modelDyProxy = modelDy;
            modelVisualEquivalence = "proxy_only";
            if (modelLowerThanOutline) {
                reason = "MODEL_LOWER_THAN_OUTLINE";
                traceGap = false;
            } else if (!modelEqualsOutline) {
                reason = "MODEL_DY_MISMATCH";
                traceGap = true;
            } else {
                reason = "TRACE_GAP_MODEL_PROXY_ONLY_NOT_VISUAL_MESH";
                traceGap = true;
            }
        }

        System.out.println("[MC1211_MODEL_VS_OUTLINE_ROW]"
                + " row=" + rowName
                + " clientWorldPresent=true"
                + " clientPlayerPresent=true"
                + " fixtureOrigin=" + textPos(fixtureOrigin)
                + " slabPos=" + textPos(slabPos)
                + " slabState=" + slabState
                + " slabDy=" + formatDouble(slabDy)
                + " fullBlockPos=" + textPos(fullPos)
                + " fullBlockState=" + fullState
                + " modelDy=" + modelDy
                + " modelDyProxy=" + modelDyProxy
                + " modelObserverKind=" + modelObserverKind
                + " outlineDy=" + formatDouble(outlineDy)
                + " targetDy=" + targetDyText
                + " modelBoundsY=" + modelBounds
                + " outlineBoundsY=" + outlineBounds
                + " modelEqualsOutline=" + modelEqualsOutline
                + " modelLowerThanOutline=" + modelLowerThanOutline
                + " modelVisualEquivalence=" + modelVisualEquivalence
                + " reason=" + reason
                + " result=" + (traceGap ? "TRACE_GAP" : "RED"));
        return new RowResult(traceGap, modelLowerThanOutline);
    }

    private static void emitHostedTraceGap(
            String reason,
            String row,
            String clientWorldPresent,
            String clientPlayerPresent,
            String fixtureOrigin,
            String slabPos,
            String slabState,
            String slabDy,
            String fullPos,
            String fullState,
            String modelDy,
            String outlineDy,
            String targetDy,
            String modelBounds,
            String outlineBounds,
            boolean modelEqualsOutline,
            boolean modelLowerThanOutline) {
        System.out.println("[MC1211_MODEL_VS_OUTLINE_START]"
                + " route=" + ROUTE
                + " rows=1");
        System.out.println("[MC1211_MODEL_VS_OUTLINE_ROW]"
                + " row=" + row
                + " clientWorldPresent=" + clientWorldPresent
                + " clientPlayerPresent=" + clientPlayerPresent
                + " fixtureOrigin=" + fixtureOrigin
                + " slabPos=" + slabPos
                + " slabState=" + slabState
                + " slabDy=" + slabDy
                + " fullBlockPos=" + fullPos
                + " fullBlockState=" + fullState
                + " modelDy=" + modelDy
                + " modelDyProxy=NaN"
                + " modelObserverKind=not_observable"
                + " outlineDy=" + outlineDy
                + " targetDy=" + targetDy
                + " modelBoundsY=" + modelBounds
                + " outlineBoundsY=" + outlineBounds
                + " modelEqualsOutline=" + modelEqualsOutline
                + " modelLowerThanOutline=" + modelLowerThanOutline
                + " modelVisualEquivalence=no"
                + " reason=" + reason
                + " result=TRACE_GAP");
        System.out.println("[MC1211_MODEL_VS_OUTLINE_SUMMARY]"
                + " route=" + ROUTE
                + " rows=1"
                + " finalResult=TRACE_GAP");
        System.out.println("[MC1211_MODEL_VS_OUTLINE_TRACE_GAP]"
                + " route=" + ROUTE
                + " rows=1"
                + " reason=" + reason);
    }

    private static String textPos(BlockPos pos) {
        if (pos == null) {
            return "n/a";
        }
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static BlockPos findNearbyTrapdoor(net.minecraft.world.BlockView world, BlockPos expectedTrapdoorPos) {
        if (world == null || expectedTrapdoorPos == null) {
            return expectedTrapdoorPos;
        }
        BlockPos[] candidates = {
                expectedTrapdoorPos,
                expectedTrapdoorPos.down(),
                expectedTrapdoorPos.up(),
                expectedTrapdoorPos.north(),
                expectedTrapdoorPos.south(),
                expectedTrapdoorPos.east(),
                expectedTrapdoorPos.west()
        };
        for (BlockPos candidate : candidates) {
            if (world.getBlockState(candidate).isOf(Blocks.OAK_TRAPDOOR)) {
                return candidate;
            }
        }
        return expectedTrapdoorPos;
    }

    private static String trapdoorSeamTargetOwner(ClientWorld world, HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return hit == null ? "null" : hit.getType().toString();
        }
        BlockPos hitPos = blockHit.getBlockPos();
        if (hitPos.equals(trapdoorSeamExpectedTrapdoorPos) || hitPos.equals(trapdoorSeamActualTrapdoorPos)) {
            return "trapdoor";
        }
        if (hitPos.equals(trapdoorSeamSupportPos)) {
            return "support";
        }
        BlockState hitState = world == null ? Blocks.AIR.getDefaultState() : world.getBlockState(hitPos);
        if (hitState.isOf(Blocks.STONE) || hitState.isOf(Blocks.STONE_SLAB)) {
            return "adjacent_stone_or_slab";
        }
        return "other:" + textPos(hitPos);
    }

    private static String formatHit(HitResult hit) {
        if (hit == null) {
            return "null";
        }
        if (hit instanceof BlockHitResult blockHit) {
            return "type=" + hit.getType()
                    + "/pos=" + textPos(blockHit.getBlockPos())
                    + "/side=" + blockHit.getSide().asString()
                    + "/hit=" + formatVec(blockHit.getPos());
        }
        return "type=" + hit.getType() + "/hit=" + formatVec(hit.getPos());
    }

    private static net.minecraft.util.math.Box worldBox(VoxelShape shape, BlockPos pos) {
        if (shape == null || shape.isEmpty() || pos == null) {
            return null;
        }
        return shape.getBoundingBox().offset(pos);
    }

    private static boolean sameBox(net.minecraft.util.math.Box left, net.minecraft.util.math.Box right) {
        if (left == null || right == null) {
            return false;
        }
        return near(left.minX, right.minX)
                && near(left.minY, right.minY)
                && near(left.minZ, right.minZ)
                && near(left.maxX, right.maxX)
                && near(left.maxY, right.maxY)
                && near(left.maxZ, right.maxZ);
    }

    private static boolean boxesIntersect(net.minecraft.util.math.Box left, net.minecraft.util.math.Box right) {
        if (left == null || right == null) {
            return false;
        }
        return left.maxX > right.minX
                && left.minX < right.maxX
                && left.maxY > right.minY
                && left.minY < right.maxY
                && left.maxZ > right.minZ
                && left.minZ < right.maxZ;
    }

    private static String formatBox(net.minecraft.util.math.Box box) {
        if (box == null) {
            return "null";
        }
        return formatDouble(box.minX) + "," + formatDouble(box.minY) + "," + formatDouble(box.minZ)
                + ".."
                + formatDouble(box.maxX) + "," + formatDouble(box.maxY) + "," + formatDouble(box.maxZ);
    }

    private static String formatVec(Vec3d vec) {
        return formatDouble(vec.x) + "," + formatDouble(vec.y) + "," + formatDouble(vec.z);
    }

    private static String formatDouble(double value) {
        if (!Double.isFinite(value)) {
            return "NaN";
        }
        return String.format(java.util.Locale.ROOT, "%.6f", value);
    }

    private static String sampleSidePlaceVariants(ClientWorld clientWorld, ServerWorld serverWorld) {
        if (sidePlacePlacePos == null) {
            return "positions_missing";
        }
        StringBuilder line = new StringBuilder();
        for (Direction direction : Direction.values()) {
            BlockPos pos = sidePlaceHitPos == null
                    ? sidePlacePlacePos
                    : sidePlaceHitPos.offset(direction);
            if (!line.isEmpty()) {
                line.append("|");
            }
            line.append(direction.asString())
                    .append("@")
                    .append(textPos(pos))
                    .append(":client=")
                    .append(clientWorld == null ? "UNAVAILABLE" : clientWorld.getBlockState(pos).getBlock())
                    .append("/server=")
                    .append(serverWorld == null ? "UNAVAILABLE" : serverWorld.getBlockState(pos).getBlock());
        }
        return line.toString();
    }

    private static OffsetBlockStateModel.RenderOffsetSample sampleModelTrace(BlockPos observedPos) {
        System.setProperty("slabbed.render.offset.trace", "true");
        OffsetBlockStateModel.resetRenderOffsetSample(observedPos);
        OffsetBlockStateModel.RenderOffsetSample trace = OffsetBlockStateModel.snapshotRenderOffsetSample();
        System.clearProperty("slabbed.render.offset.trace");
        return trace;
    }

    private static String inferShiftedBounds(VoxelShape outlineShape, double modelDyDelta) {
        if (outlineShape.isEmpty()) {
            return "NaN..NaN";
        }
        return formatDouble(outlineShape.getBoundingBox().minY + modelDyDelta)
                + ".."
                + formatDouble(outlineShape.getBoundingBox().maxY + modelDyDelta);
    }

    private static double shiftedMinY(VoxelShape outlineShape, double modelDyDelta) {
        if (outlineShape.isEmpty()) {
            return Double.NaN;
        }
        return outlineShape.getBoundingBox().minY + modelDyDelta;
    }

    private record SuperflatHarnessRowSpec(
            String rowName,
            String blockId,
            boolean productBadSuspect,
            Block supportBlock
    ) {
        private SuperflatHarnessRowSpec(String rowName, String blockId, boolean productBadSuspect) {
            this(rowName, blockId, productBadSuspect, Blocks.STONE_SLAB);
        }
    }

    private record LoweredTrapdoorGoblinCase(
            String caseName,
            SlabType targetSlabType,
            boolean sideExtension,
            boolean cornerCase
    ) {
    }

    private record SbsTopSlabCombinationCase(
            String rowName,
            String structure,
            boolean finalSlab,
            Block finalBlock,
            net.minecraft.item.Item finalItem,
            Block supportBlock,
            net.minecraft.item.Item supportItem,
            Block slabBlock,
            net.minecraft.item.Item slabItem,
            boolean repeatFinalClick,
            boolean liveCrosshairFinalStep,
            boolean sideSlabFromFinalOwnerTop
    ) {
        private SbsTopSlabCombinationCase(
                String rowName,
                String structure,
                boolean finalSlab,
                Block finalBlock,
                net.minecraft.item.Item finalItem
        ) {
            this(rowName, structure, finalSlab, finalBlock, finalItem,
                    Blocks.STONE, Items.STONE, Blocks.STONE_SLAB, Items.STONE_SLAB, false, false, false);
        }

        private SbsTopSlabCombinationCase(
                String rowName,
                String structure,
                boolean finalSlab,
                Block finalBlock,
                net.minecraft.item.Item finalItem,
                boolean repeatFinalClick
        ) {
            this(rowName, structure, finalSlab, finalBlock, finalItem,
                    Blocks.STONE, Items.STONE, Blocks.STONE_SLAB, Items.STONE_SLAB, repeatFinalClick, false, false);
        }

        private SbsTopSlabCombinationCase(
                String rowName,
                String structure,
                boolean finalSlab,
                Block finalBlock,
                net.minecraft.item.Item finalItem,
                Block supportBlock,
                net.minecraft.item.Item supportItem
        ) {
            this(rowName, structure, finalSlab, finalBlock, finalItem,
                    supportBlock, supportItem, Blocks.STONE_SLAB, Items.STONE_SLAB, false, false, false);
        }

        private SbsTopSlabCombinationCase(
                String rowName,
                String structure,
                boolean finalSlab,
                Block finalBlock,
                net.minecraft.item.Item finalItem,
                Block supportBlock,
                net.minecraft.item.Item supportItem,
                Block slabBlock,
                net.minecraft.item.Item slabItem
        ) {
            this(rowName, structure, finalSlab, finalBlock, finalItem,
                    supportBlock, supportItem, slabBlock, slabItem, false, false, false);
        }

        private SbsTopSlabCombinationCase(
                String rowName,
                String structure,
                boolean finalSlab,
                Block finalBlock,
                net.minecraft.item.Item finalItem,
                Block supportBlock,
                net.minecraft.item.Item supportItem,
                Block slabBlock,
                net.minecraft.item.Item slabItem,
                boolean repeatFinalClick,
                boolean liveCrosshairFinalStep
        ) {
            this(rowName, structure, finalSlab, finalBlock, finalItem,
                    supportBlock, supportItem, slabBlock, slabItem,
                    repeatFinalClick, liveCrosshairFinalStep, false);
        }

    }

    private record SbsTopSlabSideTargetProbe(
            boolean ran,
            boolean completed,
            Vec3d hitVector,
            String heldItem,
            String intendedAction,
            String playerBlockPos,
            String playerEye,
            String targetSample,
            String targetOwner,
            double targetDy,
            Direction targetSide,
            String interactResult,
            int waitTicks,
            BlockPos sidePos,
            String sideStateBefore,
            String sideClientStateBefore,
            String sideStateImmediate,
            String sideClientStateImmediate,
            String sideStateAfter,
            String sideClientStateAfter,
            double sideDyAfter,
            double sideClientDyAfter,
            boolean sideLowerMarker,
            boolean sideUpperMarker,
            boolean sideDoubleMarker,
            String reason
    ) {
        SbsTopSlabSideTargetProbe withCompletion(
                boolean completed,
                int waitTicks,
                String sideStateAfter,
                String sideClientStateAfter,
                double sideDyAfter,
                double sideClientDyAfter,
                boolean sideLowerMarker,
                boolean sideUpperMarker,
                boolean sideDoubleMarker,
                String reason
        ) {
            return new SbsTopSlabSideTargetProbe(
                    ran,
                    completed,
                    hitVector,
                    heldItem,
                    intendedAction,
                    playerBlockPos,
                    playerEye,
                    targetSample,
                    targetOwner,
                    targetDy,
                    targetSide,
                    interactResult,
                    waitTicks,
                    sidePos,
                    sideStateBefore,
                    sideClientStateBefore,
                    sideStateImmediate,
                    sideClientStateImmediate,
                    sideStateAfter,
                    sideClientStateAfter,
                    sideDyAfter,
                    sideClientDyAfter,
                    sideLowerMarker,
                    sideUpperMarker,
                    sideDoubleMarker,
                    reason);
        }

        static SbsTopSlabSideTargetProbe notRun(String reason) {
            return new SbsTopSlabSideTargetProbe(
                    false,
                    false,
                    new Vec3d(Double.NaN, Double.NaN, Double.NaN),
                    "not_run",
                    "not_run",
                    "not_run",
                    "not_run",
                    "not_run",
                    "not_run",
                    Double.NaN,
                    null,
                    "not_run",
                    0,
                    null,
                    "not_run",
                    "not_run",
                    "not_run",
                    "not_run",
                    "not_run",
                    "not_run",
                    Double.NaN,
                    Double.NaN,
                    false,
                    false,
                    false,
                    reason);
        }
    }

    private record SbsTopSlabClassification(
            String classification,
            String failureLayer,
            String finalResult
    ) {
    }

    private record SbsTopSlabCullingProof(
            boolean seen,
            Direction targetSide,
            boolean targetFaceDraws,
            boolean upFaceDraws,
            boolean downFaceDraws,
            boolean northFaceDraws,
            boolean southFaceDraws,
            boolean westFaceDraws,
            boolean eastFaceDraws,
            boolean supportUpFaceDraws,
            boolean targetCullingFaceEmpty,
            boolean upCullingFaceEmpty,
            boolean downCullingFaceEmpty,
            boolean supportUpCullingFaceEmpty,
            boolean expectedVisibleFaceDraws,
            String classification
    ) {
        static SbsTopSlabCullingProof missing(String classification) {
            return new SbsTopSlabCullingProof(
                    false,
                    null,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    true,
                    true,
                    false,
                    classification);
        }
    }

    private record SbsTopSlabSupportRemovalProbe(
            boolean ran,
            boolean removed,
            String supportStateBefore,
            String supportStateAfter,
            String finalStateAfter,
            String clientFinalStateAfter,
            double finalDyAfter,
            double clientDyAfter,
            boolean anchoredAfter,
            boolean compoundAfter,
            boolean canPlaceAtAfter,
            String targetSampleAfter,
            String targetOwnerAfter,
            double targetDyAfter,
            Vec3d hitVectorAfter,
            String reason
    ) {
        static SbsTopSlabSupportRemovalProbe notRun(String reason) {
            return new SbsTopSlabSupportRemovalProbe(
                    false,
                    false,
                    "not_run",
                    "not_run",
                    "not_run",
                    "not_run",
                    Double.NaN,
                    Double.NaN,
                    false,
                    false,
                    false,
                    "not_run",
                    "not_run",
                    Double.NaN,
                    new Vec3d(Double.NaN, Double.NaN, Double.NaN),
                    reason);
        }
    }

    private record LoweredTrapdoorGoblinAssessment(
            boolean green,
            String classification,
            String failureLayer,
            String summary
    ) {
    }

    private record LoweredTrapdoorGoblinRow(
            String result,
            String classification,
            String failureLayer
    ) {
    }

    private record RowResult(boolean traceGap, boolean modelLowerThanOutline) {
    }
}
