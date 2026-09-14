package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.C3TestPhaseTrace;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import com.slabbed.upgrade.WorldUpgradeDecision;
import com.slabbed.upgrade.WorldUpgradeRuntimePolicy;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.impl.attachment.AttachmentTargetImpl;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.GameMode;
import net.minecraft.world.ChunkSerializer;
import net.minecraft.world.BlockView;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * C3 route-completeness and sole-capture boundary proofs (Slice 2b), translated from the 26.2 donor.
 *
 * <p>Each row pins one boundary of the ONE placement-capture seam: the store must land on the cell
 * vanilla actually filled, must stay empty when nothing was placed, must overwrite a stale value, and
 * must never be written from anywhere but the single call site inside
 * {@code BlockItemPlacementIntentMixin}.
 *
 * <p>ADAPTED FOR THIS LINE: the donor's resolver-value rows ({@code replaceableCellUsesRootAim},
 * {@code noSuperUsesUnstoredFallback}) are dropped — the landing resolver they assert is Slice 2d and
 * does not exist here. The donor's {@code successfulWithoutSetPlacedByUsesTransformedTarget} row is
 * also dropped: it needs a gametest-side {@code BlockItem} mixin fixture (a whole extra mixin config
 * on this source set) purely to skip {@code onPlaced}, and the transformed-target property it proves
 * is already covered by {@link #transformedScaffoldingTarget} against real vanilla Scaffolding.
 * {@link #terrainSlabsFinalStateWritesNothing} likewise drops the donor's custom paired test block in
 * favour of the same compat-override seam applied to a vanilla block.
 */
public final class PlacementCaptureBoundaryGameTest {

    private static final String PREFIX = "slabbed_gametest:placement_capture_boundary_game_test_";

    /** The transformed target must be the sole cell that receives a fact — the clicked cell gets none. */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void transformedScaffoldingTarget(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos clicked = h.getAbsolutePos(new BlockPos(3, 3, 3));
        world.setBlockState(clicked.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(clicked, Blocks.SCAFFOLDING.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(clicked.south(), Blocks.SCAFFOLDING.getDefaultState(), Block.NOTIFY_ALL);
        BlockPos actual = clicked.south(2);
        PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
        player.setYaw(0.0f);
        ActionResult result = useOn(player, new ItemStack(Items.SCAFFOLDING), clicked, Direction.UP);
        h.assertTrue(result.isAccepted()
                        && world.getBlockState(actual).isOf(Blocks.SCAFFOLDING)
                        && Double.isFinite(stored(world, actual))
                        && Double.isNaN(stored(world, clicked)),
                "C3 transformed Scaffolding target was not the sole dy target: result=" + result
                        + " actual=" + world.getBlockState(actual)
                        + " actualDy=" + stored(world, actual)
                        + " staleDy=" + stored(world, clicked));
        // Column-premise guard (LandingResolver): the UP-face click transformed SIDEWAYS, so the
        // placed block rests on nothing of the owner's top plane. Applying the plane formula anyway
        // stores +1.0 — a full cube rendered a block above its own cell. The owner here is flush, so
        // the honest answer is 0.0. Value-pinned, not merely finite: the donor asserts only
        // finiteness, which is how that arithmetic went unexamined there.
        h.assertTrue(Double.doubleToRawLongBits(stored(world, actual))
                        == Double.doubleToRawLongBits(0.0d),
                "transformed Scaffolding must land in the owner's frame (0.0), not on a plane it "
                        + "never touched; got " + stored(world, actual));
        pass(h, "transformed_scaffolding_target");
    }

    /**
     * A direct {@code BlockItem.place} with no player never passes through {@code useOnBlock}, so the
     * frame has no aim at all. It must still capture a finite height and must not throw.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void directAimlessNoNpe(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos owner = flatOwner(h);
        BlockPos target = owner.up();
        ItemStack stack = new ItemStack(Items.STONE);
        ItemUsageContext usage =
                new ItemUsageContext(world, null, Hand.MAIN_HAND, stack, hit(owner, Direction.UP)) {
                };
        ActionResult result = ((BlockItem) Items.STONE).place(new ItemPlacementContext(usage));
        h.assertTrue(result != null
                        && result.isAccepted()
                        && Double.isFinite(stored(world, target)),
                "direct AIMLESS place must capture a finite height without throwing; result=" + result
                        + " dy=" + stored(world, target));
        pass(h, "direct_aimless_no_npe");
    }

    /** A refused placement writes nothing: no block changed, so no height was decided. */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void failedPlaceWritesNothing(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos owner = flatOwner(h);
        BlockPos blocked = owner.up();
        world.setBlockState(blocked, Blocks.OBSIDIAN.getDefaultState(), Block.NOTIFY_ALL);
        ActionResult result = useOn(h.createMockPlayer(GameMode.SURVIVAL),
                new ItemStack(Items.STONE), owner, Direction.UP);
        h.assertTrue(!result.isAccepted() && Double.isNaN(stored(world, blocked)),
                "failed placement authored a height; result=" + result + " dy=" + stored(world, blocked));
        pass(h, "failed_place_writes_nothing");
    }

    /** {@code setBlockState} is not a placement: worldgen, structures and commands author no fact. */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void nonItemSetBlockWritesNothing(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos pos = h.getAbsolutePos(new BlockPos(3, 3, 3));
        world.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        h.assertTrue(Double.isNaN(stored(world, pos)),
                "non-item setBlockState unexpectedly entered C3 capture; dy=" + stored(world, pos));
        pass(h, "non_item_set_block_writes_nothing");
    }

    /**
     * A stale ("haunted") value left in the store at a cell must be replaced bit-for-bit by the real
     * placement's own captured height — never averaged with it, never preserved. The support is a
     * bottom slab so the expected height is a genuine {@code -0.5}, and the expectation is taken from
     * the same public entry the capture used rather than hardcoded.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void hauntedStoreOverwritten(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos owner = h.getAbsolutePos(new BlockPos(3, 3, 3));
        world.setBlockState(owner.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(owner,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_ALL);
        BlockPos target = owner.up();
        forceStore(world, target, 0.25d);
        useOn(h.createMockPlayer(GameMode.SURVIVAL), new ItemStack(Items.STONE), owner, Direction.UP);
        h.assertTrue(world.getBlockState(target).isOf(Blocks.STONE),
                "premise: stone failed to place on the bottom slab at " + target);
        double expected = SlabSupport.getUnstoredYOffset(world, target, world.getBlockState(target));
        double actual = stored(world, target);
        h.assertTrue(Math.abs(expected + 0.5d) <= 1.0e-9d,
                "premise: placement on a bottom slab should read -0.5, got " + expected);
        h.assertTrue(Double.doubleToRawLongBits(actual) == Double.doubleToRawLongBits(expected),
                "real placement did not overwrite the haunted store bit-exactly; stored=" + actual
                        + " expected=" + expected);
        pass(h, "haunted_store_overwritten");
    }

    /**
     * A final state a compat mod owns never enters the store. Terrain Slabs is not on the dev
     * classpath, so the compat boundary is driven through its test-override seam.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void terrainSlabsFinalStateWritesNothing(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos owner = flatOwner(h);
        BlockPos target = owner.up();
        Predicate<BlockState> previous = CompatHooks.shouldSkipSlabSupportTestOverride;
        try {
            CompatHooks.shouldSkipSlabSupportTestOverride = state -> state.isOf(Blocks.GOLD_BLOCK);
            useOn(h.createMockPlayer(GameMode.SURVIVAL),
                    new ItemStack(Items.GOLD_BLOCK), owner, Direction.UP);
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = previous;
        }
        h.assertTrue(world.getBlockState(target).isOf(Blocks.GOLD_BLOCK),
                "premise: compat-owned block failed to place at " + target);
        h.assertTrue(Double.isNaN(stored(world, target)),
                "compat-owned final state entered the C3 store; dy=" + stored(world, target));

        // Control: the same placement without the override DOES author a fact, so the row above
        // proves the compat bail rather than a broken capture.
        BlockPos controlOwner = h.getAbsolutePos(new BlockPos(5, 3, 5));
        world.setBlockState(controlOwner, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        useOn(h.createMockPlayer(GameMode.SURVIVAL),
                new ItemStack(Items.GOLD_BLOCK), controlOwner, Direction.UP);
        h.assertTrue(Double.isFinite(stored(world, controlOwner.up())),
                "control: an un-owned placement must still author a fact");
        pass(h, "terrain_slabs_final_state_writes_nothing");
    }

    /**
     * ORDERING LAW: every legacy marker author must have finished before the placement fact is
     * published, or the store would freeze a height taken before the markers that decide it exist.
     *
     * <p>PORT NOTE: the donor's publish phase carries its {@code setPlacedBy} observation
     * ({@code "publish:setPlacedBy=true"}). This line does not wrap {@code Block.onPlaced} — the five
     * C3 seams here are place / getPlacementContext / getPlacementState / decrementUnlessCreative /
     * place-RETURN — so the publish phase is the constant {@code "publish"} and the asserted trace is
     * exactly {@code [markers_complete, publish]}.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void markerAuthorsCompleteBeforePublish(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos owner = flatOwner(h);
        C3TestPhaseTrace.beginTestPhaseTrace();
        List<String> trace;
        try {
            useOn(h.createMockPlayer(GameMode.SURVIVAL), new ItemStack(Items.STONE), owner, Direction.UP);
            trace = C3TestPhaseTrace.snapshotTestPhaseTrace();
        } finally {
            C3TestPhaseTrace.endTestPhaseTrace();
        }
        h.assertTrue(trace.equals(List.of("markers_complete", "publish"))
                        && Double.isFinite(stored(world, owner.up())),
                "legacy marker completion did not precede dy publication: trace=" + trace
                        + " dy=" + stored(world, owner.up()));
        pass(h, "marker_authors_complete_before_publish");
    }

    /**
     * SINGLE-WRITER structure proof (source text): the placement-time anchor mixin must not have
     * regained its own capture call, and the placement mixin must carry exactly ONE batch write.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void singleInitialWriter(TestContext h) {
        try {
            Path root = locateProjectRoot();
            String blockMixin = Files.readString(root.resolve(
                    "src/main/java/com/slabbed/mixin/BlockOnPlacedAnchorMixin.java"));
            String itemMixin = Files.readString(root.resolve(
                    "src/main/java/com/slabbed/mixin/BlockItemPlacementIntentMixin.java"));
            h.assertTrue(!blockMixin.contains("SlabAnchorAttachment.capturePlacementDy("),
                    "C3 single-writer structure regressed: BlockOnPlacedAnchorMixin regained a capture call");
            h.assertTrue(occurrences(itemMixin, "SlabAnchorAttachment.writePlacementDyBatch(") == 1,
                    "C3 single-writer structure regressed: BlockItemPlacementIntentMixin must contain "
                            + "exactly one writePlacementDyBatch call, found "
                            + occurrences(itemMixin, "SlabAnchorAttachment.writePlacementDyBatch("));
        } catch (IOException exception) {
            h.assertTrue(false, "unable to inspect C3 single-writer sources: " + exception);
        }
        pass(h, "single_initial_writer");
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = "slabbed_keep_async_guard_isolated")
    public void keepExistingCollisionGuardSkipsPolicyLookupsOnUnsafeWorkers(TestContext h) {
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        var previousModernLookup = SlabAnchorAttachment.clientModernPlacementLookup;
        Thread currentThread = Thread.currentThread();
        String previousThreadName = currentThread.getName();
        java.util.concurrent.atomic.AtomicInteger modernLookups =
                new java.util.concurrent.atomic.AtomicInteger();
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        SlabAnchorAttachment.clientModernPlacementLookup = pos -> {
            modernLookups.incrementAndGet();
            return false;
        };
        try {
            BlockView view = EmptyBlockView.INSTANCE;
            BlockPos pos = BlockPos.ORIGIN;

            currentThread.setName("Worker-Main-slabbed-keep-guard");
            Blocks.STONE.getDefaultState().getCollisionShape(view, pos, ShapeContext.absent());
            int unsafeIneligibleLookups = modernLookups.get();
            Blocks.GRINDSTONE.getDefaultState().getCollisionShape(view, pos, ShapeContext.absent());
            int unsafeEligibleLookups = modernLookups.get() - unsafeIneligibleLookups;

            currentThread.setName(previousThreadName);
            int normalBefore = modernLookups.get();
            Blocks.GRINDSTONE.getDefaultState().getCollisionShape(view, pos, ShapeContext.absent());
            int normalEligibleLookups = modernLookups.get() - normalBefore;

            h.assertTrue(unsafeIneligibleLookups == 0
                            && unsafeEligibleLookups == 0
                            && normalEligibleLookups > 0,
                    "collision guard routing regressed: unsafeIneligible=" + unsafeIneligibleLookups
                            + " unsafeEligible=" + unsafeEligibleLookups
                            + " normalEligible=" + normalEligibleLookups);
        } finally {
            currentThread.setName(previousThreadName);
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
            SlabAnchorAttachment.clientModernPlacementLookup = previousModernLookup;
        }
        pass(h, "keep_existing_collision_guard_skips_policy_lookups_on_unsafe_workers");
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = "slabbed_background_shape_query_guard")
    public void backgroundShapeQueryDoesNotWaitForServerThread(TestContext h) throws InterruptedException {
        ServerWorld world = h.getWorld();
        BlockPos unloadedPos = h.getAbsolutePos(new BlockPos(16_384, 64, 16_384));
        var result = new java.util.concurrent.atomic.AtomicReference<Double>();
        var error = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var done = new java.util.concurrent.CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                result.set(SlabSupport.getYOffset(world, unloadedPos, Blocks.STONE.getDefaultState()));
            } catch (Throwable failure) {
                error.set(failure);
            } finally {
                done.countDown();
            }
        }, "background-shape-query-test");
        worker.setDaemon(true);
        worker.start();

        // Holding the server thread makes any synchronous chunk request fail deterministically.
        if (!done.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
            h.throwGameTestException("BACKGROUND_SHAPE_QUERY_RED: offset lookup waited for the server thread");
        }
        if (error.get() != null) {
            h.throwGameTestException("background shape query failed: " + error.get());
        }
        h.assertTrue(result.get() != null
                        && Double.doubleToRawLongBits(result.get()) == Double.doubleToRawLongBits(0.0d),
                "background shape query must stay flat until the authoritative server thread resolves it");
        pass(h, "background_shape_query_does_not_wait_for_server_thread");
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void keepExistingRoutesOnlyPostDecisionPlacementsToFrozenHeight(TestContext h) {
        ServerWorld world = h.getWorld();
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        var previousModernLookup = SlabAnchorAttachment.clientModernPlacementLookup;
        var previousPredictionLookup = SlabAnchorAttachment.clientPostPolicyPredictionLookup;
        var previousEffectiveLookup = SlabAnchorAttachment.clientEffectivePlacementDyLookup;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        try {
            BlockPos legacy = h.getAbsolutePos(new BlockPos(2, 3, 2));
            BlockPos modernOwner = legacy;
            BlockPos modern = modernOwner.up();
            BlockPos missingFact = h.getAbsolutePos(new BlockPos(10, 3, 10));
            BlockPos predicted = h.getAbsolutePos(new BlockPos(12, 3, 12));

            world.setBlockState(legacy.down(),
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(legacy, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, legacy, world.getBlockState(legacy));
            SlabAnchorAttachment.writePlacementDyBatch(world,
                    java.util.Map.of(legacy, Double.doubleToRawLongBits(-1.5d)));

            WorldUpgradeRuntimePolicy.activate(world, WorldUpgradeDecision.Mode.KEEP_EXISTING);
            ActionResult placed = useOn(h.createMockPlayer(GameMode.SURVIVAL),
                    new ItemStack(Items.STONE), modernOwner, Direction.UP);

            world.setBlockState(missingFact, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.markPostPolicyPlacements(world, List.of(missingFact));
            double missingBefore = SlabSupport.getYOffset(world, missingFact, world.getBlockState(missingFact));
            world.setBlockState(missingFact.down(),
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            double missingAfter = SlabSupport.getYOffset(world, missingFact, world.getBlockState(missingFact));

            h.assertTrue(placed.isAccepted()
                            && !SlabAnchorAttachment.isModernPlacement(world, legacy)
                            && sameHeight(SlabAnchorAttachment.storedPlacementDy(world, legacy), -1.5d)
                            && sameHeight(SlabSupport.getYOffset(world, legacy, world.getBlockState(legacy)), -0.5d)
                            && SlabAnchorAttachment.isModernPlacement(world, modern)
                            && SlabAnchorAttachment.rawPlacementDyFact(world, modern).present()
                            && sameHeight(SlabSupport.getYOffset(world, modern, world.getBlockState(modern)), -0.5d)
                            && SlabAnchorAttachment.isModernPlacement(world, missingFact)
                            && !SlabAnchorAttachment.rawPlacementDyFact(world, missingFact).present()
                            && sameHeight(missingBefore, 0.0d) && sameHeight(missingAfter, 0.0d),
                    "KEEP routing failed: legacyVisible="
                            + SlabSupport.getYOffset(world, legacy, world.getBlockState(legacy))
                            + " modernVisible=" + SlabSupport.getYOffset(world, modern, world.getBlockState(modern))
                            + " missing=" + missingBefore + " -> " + missingAfter);

            WorldChunk chunk = world.getChunk(modern.getX() >> 4, modern.getZ() >> 4);
            NbtCompound attachments = ChunkSerializer.serialize(world, chunk)
                    .getCompound(AttachmentTarget.NBT_ATTACHMENT_KEY);
            h.assertTrue(attachments.contains("slabbed:modern_placements"),
                    "modern placement provenance was not persisted by the chunk serializer");

            SlabAnchorAttachment.clientModernPlacementLookup = pos -> pos.equals(modern);
            SlabAnchorAttachment.clientPostPolicyPredictionLookup = pos -> pos.equals(predicted);
            SlabAnchorAttachment.clientEffectivePlacementDyLookup = pos -> pos.equals(predicted)
                    ? new SlabAnchorAttachment.PlacementDyFact(true, Double.doubleToRawLongBits(-0.5d))
                    : null;
            h.assertTrue(SlabAnchorAttachment.usesFrozenPlacementHeight(null, modern)
                            && SlabAnchorAttachment.usesFrozenPlacementHeight(null, predicted)
                            && !SlabAnchorAttachment.usesFrozenPlacementHeight(null, legacy)
                            && !SlabAnchorAttachment.usesFrozenPlacementHeight(world, predicted),
                    "client bridge or server/client prediction separation selected the wrong route");
        } finally {
            WorldUpgradeRuntimePolicy.deactivate(world);
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
            SlabAnchorAttachment.clientModernPlacementLookup = previousModernLookup;
            SlabAnchorAttachment.clientPostPolicyPredictionLookup = previousPredictionLookup;
            SlabAnchorAttachment.clientEffectivePlacementDyLookup = previousEffectiveLookup;
        }
        pass(h, "keep_existing_routes_only_post_decision_placements");
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = "slabbed_keep_physical_isolated")
    public void keepExistingModernBlocksUseFrozenPhysicalShapes(TestContext h) {
        ServerWorld world = h.getWorld();
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        try {
            BlockPos legacyOwner = h.getAbsolutePos(new BlockPos(1, 1, 1));
            assertAir(world, List.of(legacyOwner, legacyOwner.up()), "legacy fixture");
            world.setBlockState(legacyOwner,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_ALL);
            PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
            player.setPosition(legacyOwner.getX() + 3.5d, legacyOwner.getY(), legacyOwner.getZ() + 0.5d);
            // A pre-policy block as a legacy world carries it: set directly and anchored, with no
            // provenance. An item placement would receive provenance — the policy is unconditional.
            BlockPos legacy = legacyOwner.up();
            world.setBlockState(legacy, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            SlabAnchorAttachment.addAnchor(world, legacy, world.getBlockState(legacy));
            VoxelShape legacyCollisionBefore = world.getBlockState(legacy)
                    .getCollisionShape(world, legacy, ShapeContext.absent());
            VoxelShape legacyOutlineBefore = world.getBlockState(legacy)
                    .getOutlineShape(world, legacy, ShapeContext.absent());

            WorldUpgradeRuntimePolicy.activate(world, WorldUpgradeDecision.Mode.KEEP_EXISTING);

            BlockPos deepOwner = h.getAbsolutePos(new BlockPos(3, 5, 3));
            assertAirBox(world, deepOwner.add(-1, -3, -1), deepOwner.add(1, 1, 1),
                    "deep placement and ray envelope");
            world.setBlockState(deepOwner, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            SlabAnchorAttachment.writePlacementDyBatch(world,
                    java.util.Map.of(deepOwner, Double.doubleToRawLongBits(-3.0d)));
            SlabAnchorAttachment.markPostPolicyPlacements(world, List.of(deepOwner));
            assertPhysicalShape(world, deepOwner, -3.0d, "modern deep owner");

            double ownerVisibleTop = deepOwner.getY() - 2.0d;
            Vec3d ownerRayStart = new Vec3d(
                    deepOwner.getX() + 0.5d, ownerVisibleTop + 1.0d, deepOwner.getZ() + 0.5d);
            Vec3d ownerRayEnd = new Vec3d(
                    deepOwner.getX() + 0.5d, ownerVisibleTop - 0.5d, deepOwner.getZ() + 0.5d);
            BlockHitResult ownerHit = SlabbedOffsetRaycast.raycast(
                    world, ownerRayStart, ownerRayEnd, ShapeContext.absent());
            BlockState hitState = world.getBlockState(ownerHit.getBlockPos());
            h.assertTrue(ownerHit.getType() == HitResult.Type.BLOCK
                            && ownerHit.getBlockPos().equals(deepOwner)
                            && ownerHit.getSide() == Direction.UP,
                    "deep owner visible-face ray failed: expectedOwner=" + deepOwner
                            + " hitOwner=" + ownerHit.getBlockPos() + " hitFace=" + ownerHit.getSide()
                            + " hitPos=" + ownerHit.getPos() + " hitState=" + hitState
                            + " hitStored=" + stored(world, ownerHit.getBlockPos())
                            + " hitModern=" + SlabAnchorAttachment.isModernPlacement(world, ownerHit.getBlockPos()));

            BlockPos deep = deepOwner.up();
            List<BlockPos> deepPlacementCells = placementObservationCells(deepOwner);
            Map<BlockPos, PlacementObservation> deepBefore = observe(world, deepPlacementCells);
            player.setPosition(deepOwner.getX() + 3.5d, deepOwner.getY() - 2.0d,
                    deepOwner.getZ() + 0.5d);
            ActionResult deepResult = useOn(player, new ItemStack(Items.STONE), ownerHit);
            Map<BlockPos, PlacementObservation> deepAfter = observe(world, deepPlacementCells);
            String deepDelta = describeChanges(deepBefore, deepAfter);
            h.assertTrue(deepResult.isAccepted()
                            && world.getBlockState(deep).isOf(Blocks.STONE)
                            && SlabAnchorAttachment.isModernPlacement(world, deep)
                            && SlabAnchorAttachment.rawPlacementDyFact(world, deep).present()
                            && sameHeight(stored(world, deep), -3.0d)
                            && onlyExpectedCellChanged(deepBefore, deepAfter, deep),
                    "real deep placement failed: result=" + deepResult + " ownerHit=" + ownerHit.getBlockPos()
                            + "/" + ownerHit.getSide() + " hitPos=" + ownerHit.getPos()
                            + " expectedState=" + world.getBlockState(deep)
                            + " expectedStored=" + stored(world, deep)
                            + " expectedModern=" + SlabAnchorAttachment.isModernPlacement(world, deep)
                            + " changes=" + deepDelta);

            BlockPos flatOwner = h.getAbsolutePos(new BlockPos(5, 1, 5));
            assertAir(world, List.of(flatOwner, flatOwner.up()), "flat control fixture");
            world.setBlockState(flatOwner, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            player.setPosition(flatOwner.getX() - 2.5d,
                    flatOwner.getY(),
                    flatOwner.getZ() + 0.5d);
            ActionResult flatResult = useOn(player, new ItemStack(Items.STONE), flatOwner, Direction.UP);
            BlockPos flat = flatOwner.up();

            h.assertTrue(flatResult.isAccepted()
                            && world.getBlockState(flat).isOf(Blocks.STONE)
                            && SlabAnchorAttachment.isModernPlacement(world, flat),
                    "real BlockItem control did not author the post-policy flat cell: flat="
                            + flatResult + " state=" + world.getBlockState(flat));

            assertPhysicalShape(world, deep, -3.0d, "modern deep placement");
            assertPhysicalShape(world, flat, 0.0d, "modern flat");
            Vec3d placedRayStart = new Vec3d(
                    deep.getX() - 1.0d, deep.getY() - 2.5d, deep.getZ() + 0.5d);
            Vec3d placedRayEnd = new Vec3d(
                    deep.getX() + 0.5d, deep.getY() - 2.5d, deep.getZ() + 0.5d);
            BlockHitResult placedHit = SlabbedOffsetRaycast.raycast(
                    world, placedRayStart, placedRayEnd, ShapeContext.absent());
            h.assertTrue(placedHit.getType() == HitResult.Type.BLOCK
                            && placedHit.getBlockPos().equals(deep)
                            && placedHit.getSide() == Direction.WEST,
                    "new deep placement visible-side ray failed: expectedOwner=" + deep
                            + " hitOwner=" + placedHit.getBlockPos() + " hitFace=" + placedHit.getSide()
                            + " hitPos=" + placedHit.getPos()
                            + " hitState=" + world.getBlockState(placedHit.getBlockPos())
                            + " hitStored=" + stored(world, placedHit.getBlockPos())
                            + " hitModern=" + SlabAnchorAttachment.isModernPlacement(
                                    world, placedHit.getBlockPos()));
            VoxelShape expectedDeepWorldCollision = world.getBlockState(deep)
                    .getCollisionShape(world, deep, ShapeContext.absent())
                    .offset(deep.getX(), deep.getY(), deep.getZ());
            Box deepQuery = new Box(deep.getX() + 0.1d, deep.getY() - 2.9d, deep.getZ() + 0.1d,
                    deep.getX() + 0.9d, deep.getY() - 2.1d, deep.getZ() + 0.9d);
            boolean foundDeepBody = false;
            for (VoxelShape collision : world.getBlockCollisions(null, deepQuery)) {
                if (!collision.isEmpty()
                        && sameShape(collision, expectedDeepWorldCollision)
                        && collision.getBoundingBox().intersects(deepQuery)) {
                    foundDeepBody = true;
                    break;
                }
            }
            h.assertTrue(foundDeepBody,
                    "modern deep placement's exact body was absent from the world collision query: expected="
                            + expectedDeepWorldCollision.getBoundingBox() + " query=" + deepQuery);

            VoxelShape legacyCollisionAfter = world.getBlockState(legacy)
                    .getCollisionShape(world, legacy, ShapeContext.absent());
            VoxelShape legacyOutlineAfter = world.getBlockState(legacy)
                    .getOutlineShape(world, legacy, ShapeContext.absent());
            h.assertTrue(flatResult.isAccepted()
                            && !SlabAnchorAttachment.isModernPlacement(world, legacy)
                            && SlabAnchorAttachment.isModernPlacement(world, deep)
                            && SlabAnchorAttachment.isModernPlacement(world, flat)
                            && sameShape(legacyCollisionBefore, legacyCollisionAfter)
                            && sameShape(legacyOutlineBefore, legacyOutlineAfter),
                    "KEEP changed a pre-policy block or failed to mark real post-policy placements");
            Slabbed.LOGGER.info(
                    "KEEP_PHYSICAL_COMPONENTS | PLACED=PASS ANCHOR=PASS MODEL=NOT_RUN COLLISION=PASS "
                            + "RAYCAST=PASS OUTLINE=PASS STABILITY=NOT_RUN LEGACY_PRESERVED=PASS "
                            + "| deepChanges={}",
                    deepDelta);
        } finally {
            WorldUpgradeRuntimePolicy.deactivate(world);
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
        pass(h, "keep_existing_modern_blocks_use_frozen_physical_shapes");
    }

    private static void assertPhysicalShape(ServerWorld world, BlockPos pos, double expectedDy, String label) {
        BlockState state = world.getBlockState(pos);
        VoxelShape collision = state.getCollisionShape(world, pos, ShapeContext.absent());
        VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.absent());
        if (collision.isEmpty() || outline.isEmpty()
                || !sameHeight(collision.getBoundingBox().minY, expectedDy)
                || !sameHeight(outline.getBoundingBox().minY, expectedDy)
                || !sameShape(collision, outline)) {
            throw new AssertionError(label + " shape mismatch: collision="
                    + (collision.isEmpty() ? "empty" : collision.getBoundingBox())
                    + " outline=" + (outline.isEmpty() ? "empty" : outline.getBoundingBox())
                    + " stored=" + SlabAnchorAttachment.storedPlacementDy(world, pos)
                    + " modern=" + SlabAnchorAttachment.isModernPlacement(world, pos));
        }
    }

    private static boolean sameShape(VoxelShape left, VoxelShape right) {
        return !VoxelShapes.matchesAnywhere(left, right, BooleanBiFunction.NOT_SAME);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────

    /**
     * Existing-world discriminator for the shipped mode. Runs the real chunk serializer and Fabric's
     * attachment loader through an actual compressed-NBT file with no whole-world override and no
     * explicit activation, and requires that legacy anchor-only placements keep their live heights,
     * that a latent height fact without provenance stays inert, and that a real item placement made
     * under the policy keeps its stored height — before the save and again after the reload.
     *
     * <p>This must not be weakened into a read-time live-geometry fallback for modern cells: a modern
     * cell with a missing fact stays stable-flat, as the KEEP routing row pins.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = "slabbed_keep_disk_isolated")
    public void shippedModeDiskRoundTripKeepsLegacyHeightsAndModernPlacements(TestContext h) {
        ServerWorld world = h.getWorld();
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        try {
            h.assertTrue(WorldUpgradeRuntimePolicy.authorsModernPlacements(world),
                    "the placement policy must be unconditional on this line");
            BlockPos seed = h.getAbsolutePos(new BlockPos(2, 24, 2));
            int chunkX = seed.getX() >> 4;
            int chunkZ = seed.getZ() >> 4;
            int baseX = chunkX << 4;
            int baseZ = chunkZ << 4;
            int y = seed.getY();
            BlockPos legacyHalf = new BlockPos(baseX + 2, y, baseZ + 2);
            BlockPos legacyCompound = new BlockPos(baseX + 5, y, baseZ + 5);
            BlockPos alphaOwner = new BlockPos(baseX + 8, y, baseZ + 8);
            BlockPos alphaPlaced = alphaOwner.up();
            BlockPos flatControl = new BlockPos(baseX + 11, y, baseZ + 11);
            BlockPos conflictingLegacy = new BlockPos(baseX + 13, y, baseZ + 13);
            WorldChunk chunk = world.getChunk(chunkX, chunkZ);

            assertAir(world, List.of(
                    legacyHalf, legacyCompound, alphaOwner.down(), alphaOwner, alphaPlaced,
                    flatControl.down(), flatControl, conflictingLegacy.down(), conflictingLegacy),
                    "isolated saved-chunk fixtures");

            world.setBlockState(legacyHalf, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(legacyCompound, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(alphaOwner.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(alphaOwner,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(flatControl.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(flatControl, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(conflictingLegacy.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(conflictingLegacy, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

            // Legacy anchors as a pre-policy world carries them: anchor-only, no height fact, no marker.
            LongOpenHashSet anchors = chunk.getAttached(SlabAnchorAttachment.ANCHOR_TYPE) == null
                    ? new LongOpenHashSet()
                    : new LongOpenHashSet(chunk.getAttached(SlabAnchorAttachment.ANCHOR_TYPE));
            anchors.add(legacyHalf.asLong());
            anchors.add(legacyCompound.asLong());
            chunk.setAttached(SlabAnchorAttachment.ANCHOR_TYPE, anchors);
            LongOpenHashSet compounds = chunk.getAttached(SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE) == null
                    ? new LongOpenHashSet()
                    : new LongOpenHashSet(chunk.getAttached(SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE));
            compounds.add(legacyCompound.asLong());
            chunk.setAttached(SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE, compounds);
            Long2ByteOpenHashMap placementDy = chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE) == null
                    ? new Long2ByteOpenHashMap()
                    : new Long2ByteOpenHashMap(chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE));
            placementDy.remove(legacyHalf.asLong());
            placementDy.remove(legacyCompound.asLong());
            placementDy.remove(flatControl.asLong());
            placementDy.put(conflictingLegacy.asLong(), (byte) SlabAnchorAttachment.quantiseDy(-2.0d));
            chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, placementDy);
            LongOpenHashSet modern = chunk.getAttached(SlabAnchorAttachment.MODERN_PLACEMENT_TYPE);
            h.assertTrue(modern == null || !modern.contains(conflictingLegacy.asLong()),
                    "fixture premise: the conflicting legacy cell must carry no provenance");

            PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
            player.setPosition(alphaOwner.getX() + 3.5d, alphaOwner.getY(), alphaOwner.getZ() + 0.5d);
            ActionResult placed = useOn(player, new ItemStack(Items.STONE), alphaOwner, Direction.UP);
            double legacyHalfBefore = SlabSupport.getYOffset(world, legacyHalf, world.getBlockState(legacyHalf));
            double legacyCompoundBefore = SlabSupport.getYOffset(world, legacyCompound,
                    world.getBlockState(legacyCompound));
            double alphaBefore = SlabSupport.getYOffset(world, alphaPlaced, world.getBlockState(alphaPlaced));
            double flatBefore = SlabSupport.getYOffset(world, flatControl, world.getBlockState(flatControl));
            double conflictingBefore = SlabSupport.getYOffset(world, conflictingLegacy,
                    world.getBlockState(conflictingLegacy));
            h.assertTrue(placed.isAccepted()
                            && sameHeight(legacyHalfBefore, -0.5d)
                            && sameHeight(legacyCompoundBefore, -1.0d)
                            && sameHeight(alphaBefore, -0.5d)
                            && sameHeight(flatBefore, 0.0d)
                            && sameHeight(conflictingBefore, 0.0d)
                            && !SlabAnchorAttachment.rawPlacementDyFact(world, legacyHalf).present()
                            && !SlabAnchorAttachment.rawPlacementDyFact(world, legacyCompound).present()
                            && SlabAnchorAttachment.rawPlacementDyFact(world, alphaPlaced).present()
                            && SlabAnchorAttachment.isModernPlacement(world, alphaPlaced)
                            && !SlabAnchorAttachment.isModernPlacement(world, conflictingLegacy)
                            && sameHeight(stored(world, conflictingLegacy), -2.0d)
                            && sameHeight(stored(world, alphaPlaced), -0.5d),
                    "shipped-mode fixture premise failed before save: result=" + placed
                            + " legacyHalf=" + legacyHalfBefore + " legacyCompound=" + legacyCompoundBefore
                            + " alpha=" + alphaBefore + " alphaStored=" + stored(world, alphaPlaced)
                            + " flat=" + flatBefore + " conflicting=" + conflictingBefore
                            + " conflictingStored=" + stored(world, conflictingLegacy));

            Path evidenceRoot = Path.of(System.getProperty(
                    "slabbed.upgradeEvidenceDir", System.getProperty("java.io.tmpdir")));
            Path evidenceDir = Files.createTempDirectory(evidenceRoot, "slabbed-keep-disk-");
            Path chunkFile = evidenceDir.resolve("chunk.nbt.gz");
            NbtCompound serialized = ChunkSerializer.serialize(world, chunk);
            NbtCompound serializedAttachments = serialized.getCompound(AttachmentTarget.NBT_ATTACHMENT_KEY);
            h.assertTrue(serializedAttachments.contains("slabbed:slab_anchors")
                            && serializedAttachments.contains("slabbed:compound_full_block_anchors")
                            && serializedAttachments.contains("slabbed:placement_dy")
                            && serializedAttachments.contains("slabbed:modern_placements"),
                    "production chunk serializer omitted required attachment payloads: keys="
                            + serializedAttachments.getKeys());
            NbtIo.writeCompressed(serialized, chunkFile);
            NbtCompound reloadedNbt = NbtIo.readCompressed(chunkFile, NbtSizeTracker.ofUnlimitedBytes());

            // Exact attachment reload seam used by Fabric's ChunkSerializer mixin. Reading an empty
            // payload first proves the observations below come from the bytes just read from disk.
            AttachmentTargetImpl target = (AttachmentTargetImpl) chunk;
            target.fabric_readAttachmentsFromNbt(new NbtCompound(), world.getRegistryManager());
            h.assertTrue(chunk.getAttached(SlabAnchorAttachment.ANCHOR_TYPE) == null
                            && chunk.getAttached(SlabAnchorAttachment.COMPOUND_FULL_BLOCK_ANCHOR_TYPE) == null
                            && chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE) == null
                            && chunk.getAttached(SlabAnchorAttachment.MODERN_PLACEMENT_TYPE) == null,
                    "fixture failed to clear in-memory attachments before disk reload");
            target.fabric_readAttachmentsFromNbt(reloadedNbt, world.getRegistryManager());

            double legacyHalfAfter = SlabSupport.getYOffset(world, legacyHalf, world.getBlockState(legacyHalf));
            double legacyCompoundAfter = SlabSupport.getYOffset(world, legacyCompound,
                    world.getBlockState(legacyCompound));
            double alphaAfter = SlabSupport.getYOffset(world, alphaPlaced, world.getBlockState(alphaPlaced));
            double flatAfter = SlabSupport.getYOffset(world, flatControl, world.getBlockState(flatControl));
            double conflictingAfter = SlabSupport.getYOffset(world, conflictingLegacy,
                    world.getBlockState(conflictingLegacy));
            h.assertTrue(sameHeight(legacyHalfAfter, -0.5d)
                            && sameHeight(legacyCompoundAfter, -1.0d)
                            && sameHeight(alphaAfter, -0.5d)
                            && sameHeight(flatAfter, 0.0d)
                            && sameHeight(conflictingAfter, 0.0d)
                            && SlabAnchorAttachment.isModernPlacement(world, alphaPlaced)
                            && !SlabAnchorAttachment.isModernPlacement(world, conflictingLegacy)
                            && !SlabAnchorAttachment.isModernPlacement(world, legacyHalf),
                    "shipped mode moved an existing block or lost a modern placement across disk reload:"
                            + " legacyHalf=" + legacyHalfAfter + " legacyCompound=" + legacyCompoundAfter
                            + " alpha=" + alphaAfter + " flat=" + flatAfter
                            + " conflicting=" + conflictingAfter);
        } catch (IOException exception) {
            throw new IllegalStateException("disk round-trip failed", exception);
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
        pass(h, "shipped_mode_disk_round_trip_keeps_legacy_heights_and_modern_placements");
    }

    static ActionResult useOn(PlayerEntity player, ItemStack stack, BlockPos clicked, Direction face) {
        player.setStackInHand(Hand.MAIN_HAND, stack);
        return stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit(clicked, face)));
    }

    static ActionResult useOn(PlayerEntity player, ItemStack stack, BlockHitResult hit) {
        player.setStackInHand(Hand.MAIN_HAND, stack);
        return stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
    }

    private record PlacementObservation(
            BlockState state,
            SlabAnchorAttachment.PlacementDyFact placementDy,
            boolean modern
    ) {
    }

    private static List<BlockPos> placementObservationCells(BlockPos owner) {
        LinkedHashMap<BlockPos, Boolean> cells = new LinkedHashMap<>();
        for (BlockPos center : List.of(owner, owner.up())) {
            cells.put(center.toImmutable(), true);
            for (Direction direction : Direction.values()) {
                cells.put(center.offset(direction).toImmutable(), true);
            }
        }
        return List.copyOf(cells.keySet());
    }

    private static Map<BlockPos, PlacementObservation> observe(ServerWorld world, List<BlockPos> cells) {
        LinkedHashMap<BlockPos, PlacementObservation> observations = new LinkedHashMap<>();
        for (BlockPos pos : cells) {
            observations.put(pos, new PlacementObservation(
                    world.getBlockState(pos), SlabAnchorAttachment.rawPlacementDyFact(world, pos),
                    SlabAnchorAttachment.isModernPlacement(world, pos)));
        }
        return Map.copyOf(observations);
    }

    private static boolean onlyExpectedCellChanged(
            Map<BlockPos, PlacementObservation> before,
            Map<BlockPos, PlacementObservation> after,
            BlockPos expected
    ) {
        for (BlockPos pos : before.keySet()) {
            if (pos.equals(expected) == before.get(pos).equals(after.get(pos))) {
                return false;
            }
        }
        return true;
    }

    private static String describeChanges(
            Map<BlockPos, PlacementObservation> before,
            Map<BlockPos, PlacementObservation> after
    ) {
        List<String> changes = new ArrayList<>();
        for (BlockPos pos : before.keySet()) {
            if (!before.get(pos).equals(after.get(pos))) {
                changes.add(pos.toShortString() + ":" + before.get(pos) + "->" + after.get(pos));
            }
        }
        return changes.toString();
    }

    private static void assertAir(ServerWorld world, List<BlockPos> cells, String label) {
        for (BlockPos pos : cells) {
            BlockState state = world.getBlockState(pos);
            if (!state.isAir()) {
                throw new AssertionError(label + " expected AIR at " + pos + " but found " + state);
            }
        }
    }

    private static void assertAirBox(ServerWorld world, BlockPos min, BlockPos max, String label) {
        for (BlockPos pos : BlockPos.iterate(min, max)) {
            BlockState state = world.getBlockState(pos);
            if (!state.isAir()) {
                throw new AssertionError(label + " expected AIR at " + pos + " but found " + state);
            }
        }
    }

    /** Test-only fixture: plant an exact raw value in the store without going through a placement. */
    static void forceStore(ServerWorld world, BlockPos pos, double dy) {
        WorldChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        Long2ByteOpenHashMap existing = chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
        Long2ByteOpenHashMap copy = existing == null
                ? new Long2ByteOpenHashMap()
                : new Long2ByteOpenHashMap(existing);
        int quantised = SlabAnchorAttachment.quantiseDy(dy);
        if (quantised == SlabAnchorAttachment.UNREPRESENTABLE) {
            throw new IllegalArgumentException(
                    "fixture: " + dy + " is not on the stored sixteenth grid");
        }
        copy.put(pos.asLong(), (byte) quantised);
        chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, copy);
    }

    static void withFrozen(Runnable body) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            body.run();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    private static BlockPos flatOwner(TestContext h) {
        BlockPos owner = h.getAbsolutePos(new BlockPos(3, 3, 3));
        h.getWorld().setBlockState(owner, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        return owner;
    }

    private static double stored(ServerWorld world, BlockPos pos) {
        return SlabAnchorAttachment.storedPlacementDy(world, pos);
    }

    private static BlockHitResult hit(BlockPos clicked, Direction face) {
        Vec3d location = Vec3d.ofCenter(clicked).add(
                face.getOffsetX() * 0.5d,
                face.getOffsetY() * 0.5d,
                face.getOffsetZ() * 0.5d);
        return new BlockHitResult(location, face, clicked, false);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int index = 0; (index = text.indexOf(needle, index)) >= 0; index += needle.length()) {
            count++;
        }
        return count;
    }

    private static boolean sameHeight(double actual, double expected) {
        return Double.doubleToRawLongBits(actual == 0.0d ? 0.0d : actual)
                == Double.doubleToRawLongBits(expected == 0.0d ? 0.0d : expected);
    }

    private static Path locateProjectRoot() throws IOException {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isRegularFile(cursor.resolve(
                    "src/main/java/com/slabbed/mixin/BlockOnPlacedAnchorMixin.java"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IOException("project root not found above " + Path.of("").toAbsolutePath());
    }

    private static void pass(TestContext h, String suffix) {
        Slabbed.LOGGER.info("C3_FOCUSED | {}{} | PASS", PREFIX, suffix);
        h.complete();
    }
}
