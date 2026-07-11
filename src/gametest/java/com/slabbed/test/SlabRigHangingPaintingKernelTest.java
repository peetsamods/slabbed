package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.command.SlabRigHangingArtifacts;
import com.slabbed.command.SlabRigHangingCatalog;
import com.slabbed.command.SlabRigHangingKernelArtifacts;
import com.slabbed.command.SlabRigHangingPaintingPlan;
import com.slabbed.util.BuildStamp;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TextComponentTagVisitor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Disposable-world RIG-3B2A kernel proof for real painting {@link ItemStack#useOn} execution.
 *
 * <p>This class deliberately owns no production command, durable artifact, delayed lifecycle, client,
 * or player-authored proof. It pins the mapped immediate server facts needed before a production page
 * executor may exist: full-world UUID deltas, exact entity inspection, registry-aware NBT, and exact
 * entity-first cleanup that never searches an AABB for things to delete.
 */
public final class SlabRigHangingPaintingKernelTest {

    private static final String ENTITY_STORE = "NOT_APPLICABLE_ENTITY";
    private static final String ENTITY_LIVE_DY = "NOT_APPLICABLE_ENTITY";
    private static final Direction FACE = Direction.WEST;
    private static final Stat<Item> PAINTING_USED = Stats.ITEM_USED.get(Items.PAINTING);

    @GameTest(structure = "slabbed_gametest:rig3b2a_board")
    public void paintingKernelPlannerPagePublishesPlannedImmediateAndCleared(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockServerPlayer(helper);
        SlabRigHangingCatalog.Snapshot catalog = SlabRigHangingCatalog.snapshot();
        SlabRigHangingArtifacts.RuntimeSnapshot runtime = SlabRigHangingArtifacts.snapshot(
                catalog, world.registryAccess());
        SlabRigHangingPaintingPlan.Universe universe = SlabRigHangingPaintingPlan.snapshot(catalog, runtime);
        SlabRigHangingPaintingPlan.PagePlan plannerPage = SlabRigHangingPaintingPlan.page(
                universe, 6143, 42, 1);
        AdaptedPage adapted = adaptPlannerPage(helper, plannerPage);
        SlabRigHangingKernelArtifacts.PagePlan artifactPage = artifactPage(adapted);
        SlabRigHangingKernelArtifacts.RunIdentity run = new SlabRigHangingKernelArtifacts.RunIdentity(
                plannerPage.planHash(), testBuildSha(), BuildStamp.RUNTIME_CONTENT_SHA256,
                runtime.minecraftVersion(), catalog.catalogHash(), runtime.executionIdentity(),
                runtime.paintingRegistryHash(),
                sha256("disposable-rig3b2a-world|" + world.dimension().identifier()),
                world.dimension().identifier().toString(), player.getUUID(),
                SlabAnchorAttachment.FROZEN_DY_ENABLED);

        Path artifactRoot = null;
        OwnedFixture owned = new OwnedFixture(world);
        boolean cleared = false;
        try {
            artifactRoot = Files.createTempDirectory("slabbed-rig3b2a-kernel-");
            SlabRigHangingKernelArtifacts.PhaseManifest planned =
                    SlabRigHangingKernelArtifacts.planned(run, artifactPage);
            SlabRigHangingKernelArtifacts.WrittenArtifact plannedFile =
                    publishPlannedBeforeBuild(helper, player, owned, adapted, artifactRoot, planned);
            if (!Files.isRegularFile(plannedFile.path())) {
                throw helper.assertionException("PLANNED artifact was not readback-visible before mutation");
            }
            Registry<PaintingVariant> registry = paintingRegistry(world);
            List<SlabRigHangingKernelArtifacts.CaseObservation> observations = new ArrayList<>();
            for (AdaptedCase plannedCase : adapted.cases()) {
                SlabRigHangingPaintingPlan.Selector selector = plannedCase.plan().selector();
                Holder.Reference<PaintingVariant> selected = selector.kind()
                        == SlabRigHangingPaintingPlan.SelectorKind.UNPINNED
                        ? null : holder(registry, selector.variantId());
                Attempt attempt = execute(helper, player, owned, plannedCase.attemptBacking(),
                        selected, null);
                if (attempt.paintings().size() != 1 || attempt.newPaintingUuids().size() != 1
                        || attempt.result() != InteractionResult.SUCCESS || attempt.boundedFailure()) {
                    throw helper.assertionException("planned 4x4 selector did not place exactly once: "
                            + selector + " attempt=" + attempt);
                }
                if (selected != null
                        && !selector.variantId().equals(attempt.paintings().getFirst().variantId())) {
                    throw helper.assertionException("planned typed selector/final holder mismatch: " + selector);
                }
                observations.add(caseObservation(plannedCase, attempt));
            }

            SlabRigHangingKernelArtifacts.PhaseManifest immediate =
                    SlabRigHangingKernelArtifacts.immediate(planned, observations);
            SlabRigHangingKernelArtifacts.WrittenArtifact immediateFile =
                    SlabRigHangingKernelArtifacts.write(artifactRoot, immediate);
            SlabRigHangingKernelArtifacts.validateTransition(planned, immediate);
            if (!Files.isRegularFile(immediateFile.path())) {
                throw helper.assertionException("IMMEDIATE artifact publication/readback failed");
            }

            SlabRigHangingKernelArtifacts.ClearResult clearResult =
                    owned.clearEntityFirstWithReceipt(immediate.ownership());
            cleared = true;
            SlabRigHangingKernelArtifacts.PhaseManifest clear =
                    SlabRigHangingKernelArtifacts.kernelCleared(immediate, clearResult);
            SlabRigHangingKernelArtifacts.WrittenArtifact clearFile =
                    SlabRigHangingKernelArtifacts.write(artifactRoot, clear);
            SlabRigHangingKernelArtifacts.validateTransition(immediate, clear);
            if (!Files.isRegularFile(clearFile.path()) || !owned.isEmpty()) {
                throw helper.assertionException("KERNEL_CLEARED artifact or exact teardown failed");
            }
            System.out.println("RIG3B2A-KERNEL | universe=" + universe.universeHash()
                    + " plan=" + plannerPage.planHash()
                    + " planned=" + planned.artifactId() + ':' + plannedFile.fileSha256()
                    + " immediate=" + immediate.artifactId() + ':' + immediateFile.fileSha256()
                    + " cleared=" + clear.artifactId() + ':' + clearFile.fileSha256()
                    + " cases=" + plannerPage.cases().size()
                    + " playerProof=ABSENT productionCommand=ABSENT progressEligible=false");
        } catch (IOException failure) {
            throw helper.assertionException("RIG-3B2A phase integration failed: " + failure);
        } finally {
            if (!cleared && !owned.isEmpty()) {
                owned.clearEntityFirst();
            }
            deleteTree(artifactRoot);
        }
        helper.succeed();
    }

    @GameTest(structure = "slabbed_gametest:rig3b2a_board")
    public void paintingKernelPlanPublicationFailureLeavesWorldUntouched(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockServerPlayer(helper);
        SlabRigHangingCatalog.Snapshot catalog = SlabRigHangingCatalog.snapshot();
        SlabRigHangingArtifacts.RuntimeSnapshot runtime = SlabRigHangingArtifacts.snapshot(
                catalog, world.registryAccess());
        SlabRigHangingPaintingPlan.Universe universe = SlabRigHangingPaintingPlan.snapshot(catalog, runtime);
        SlabRigHangingPaintingPlan.PagePlan plannerPage = SlabRigHangingPaintingPlan.page(
                universe, 6143, 42, 1);
        AdaptedPage adapted = adaptPlannerPage(helper, plannerPage);
        SlabRigHangingKernelArtifacts.RunIdentity run = new SlabRigHangingKernelArtifacts.RunIdentity(
                plannerPage.planHash(), testBuildSha(), BuildStamp.RUNTIME_CONTENT_SHA256,
                runtime.minecraftVersion(), catalog.catalogHash(), runtime.executionIdentity(),
                runtime.paintingRegistryHash(), sha256("disposable-plan-failure-world"),
                world.dimension().identifier().toString(), player.getUUID(),
                SlabAnchorAttachment.FROZEN_DY_ENABLED);
        SlabRigHangingKernelArtifacts.PhaseManifest planned =
                SlabRigHangingKernelArtifacts.planned(run, artifactPage(adapted));
        OwnedFixture owned = new OwnedFixture(world);
        Path root = null;
        try {
            root = Files.createTempDirectory("slabbed-rig3b2a-plan-failure-");
            Path artifactDirectory = root.resolve("hanging-page-artifacts");
            Files.createDirectories(artifactDirectory);
            Files.createSymbolicLink(artifactDirectory.resolve("planned"), root);
            try {
                publishPlannedBeforeBuild(helper, player, owned, adapted, root, planned);
                throw helper.assertionException("symlinked PLANNED publication unexpectedly succeeded");
            } catch (IOException expected) {
                // Required fail-before-world path.
            }
            if (!owned.isEmpty() || !world.getEntities(EntityTypes.PAINTING, painting -> true).isEmpty()) {
                throw helper.assertionException("failed PLANNED publication installed world/entity ownership");
            }
            for (BlockPos pos : adapted.reservedCells()) {
                if (!world.getBlockState(pos).isAir()) {
                    throw helper.assertionException(
                            "failed PLANNED publication changed reserved cell " + pos);
                }
            }
        } catch (IOException setupFailure) {
            throw helper.assertionException("plan-failure proof setup failed: " + setupFailure);
        } finally {
            deleteTree(root);
        }
        helper.succeed();
    }

    @GameTest(structure = "slabbed_gametest:rig3b2a_board")
    public void paintingKernelExecutesTaggedAndUntaggedPinnedVariants(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockServerPlayer(helper);
        Registry<PaintingVariant> registry = paintingRegistry(world);

        OwnedFixture owned = new OwnedFixture(world);
        Attempt kebab = executePinned(helper, player, owned,
                buildBacking(helper, owned, new BlockPos(4, 4, 4), FACE, 1, 1),
                holder(registry, "minecraft:kebab"), null);
        assertPlaced(helper, kebab, "minecraft:kebab", 1, 1);

        Attempt burningSkull = executePinned(helper, player, owned,
                buildBacking(helper, owned, new BlockPos(4, 10, 4), FACE, 4, 4),
                holder(registry, "minecraft:burning_skull"), null);
        assertPlaced(helper, burningSkull, "minecraft:burning_skull", 4, 4);

        Holder.Reference<PaintingVariant> earth = holder(registry, "minecraft:earth");
        HolderSet.Named<PaintingVariant> placeable = registry.get(PaintingVariantTags.PLACEABLE)
                .orElseThrow(() -> helper.assertionException("missing #minecraft:placeable"));
        if (placeable.contains(earth) || earth.value().width() != 2 || earth.value().height() != 2) {
            throw helper.assertionException("earth must remain the exact untagged 2x2 control");
        }
        Attempt untagged = executePinned(helper, player, owned,
                buildBacking(helper, owned, new BlockPos(4, 16, 4), FACE, 2, 2), earth, null);
        assertPlaced(helper, untagged, "minecraft:earth", 2, 2);

        owned.clearEntityFirst();
        if (!owned.isEmpty()) {
            throw helper.assertionException("successful variant matrix did not exact-clear its ownership");
        }
        helper.succeed();
    }

    @GameTest(structure = "slabbed_gametest:rig3b2a_board")
    public void paintingKernelDistinguishesOverrideAndVanillaRefusals(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockServerPlayer(helper);
        Registry<PaintingVariant> registry = paintingRegistry(world);
        OwnedFixture owned = new OwnedFixture(world);

        // A tagged 1x1 seed fits, then the explicit untagged 2x2 component fails final survival.
        Attempt tooLargeOverride = executePinned(helper, player, owned,
                buildBacking(helper, owned, new BlockPos(4, 4, 4), FACE, 1, 1),
                holder(registry, "minecraft:earth"), null);
        assertRefused(helper, tooLargeOverride, InteractionResult.CONSUME, 1);

        BlockPos verticalClicked = helper.absolutePos(new BlockPos(10, 4, 4));
        owned.setBlock(verticalClicked, Blocks.STONE.defaultBlockState());
        Backing vertical = new Backing(verticalClicked, Direction.UP, List.of(verticalClicked));
        Attempt verticalFailure = executePinned(helper, player, owned, vertical,
                holder(registry, "minecraft:kebab"), null);
        assertRefused(helper, verticalFailure, InteractionResult.FAIL, 0);

        BlockPos unsupported = helper.absolutePos(new BlockPos(16, 4, 4));
        Backing noSupport = new Backing(unsupported, FACE, List.of(unsupported));
        Attempt supportFailure = executePinned(helper, player, owned, noSupport,
                holder(registry, "minecraft:kebab"), null);
        assertRefused(helper, supportFailure, InteractionResult.CONSUME, 1);

        owned.clearEntityFirst();
        helper.succeed();
    }

    @GameTest(structure = "slabbed_gametest:rig3b2a_board")
    public void paintingKernelUnpinnedControlChoosesLargestTaggedSurvivor(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockServerPlayer(helper);
        Registry<PaintingVariant> registry = paintingRegistry(world);
        HolderSet.Named<PaintingVariant> placeable = registry.get(PaintingVariantTags.PLACEABLE)
                .orElseThrow(() -> helper.assertionException("missing #minecraft:placeable"));
        int largestTaggedArea = placeable.stream().mapToInt(entry -> entry.value().area()).max()
                .orElseThrow(() -> helper.assertionException("empty #minecraft:placeable"));

        OwnedFixture owned = new OwnedFixture(world);
        Backing backing = buildBacking(helper, owned, new BlockPos(6, 7, 6), FACE, 4, 4);
        Attempt attempt = execute(helper, player, owned, backing, null, null);
        if (attempt.paintings().size() != 1 || attempt.result() != InteractionResult.SUCCESS) {
            throw helper.assertionException("unpinned 4x4 control did not place exactly one painting: " + attempt);
        }
        PaintingEvidence evidence = attempt.paintings().getFirst();
        Holder.Reference<PaintingVariant> finalHolder = holder(registry, evidence.variantId());
        if (!placeable.contains(finalHolder) || evidence.width() * evidence.height() != largestTaggedArea) {
            throw helper.assertionException("unpinned control did not choose a largest tagged survivor: "
                    + evidence);
        }
        assertAttemptEnvelope(helper, attempt, 1);
        owned.clearEntityFirst();
        helper.succeed();
    }

    @GameTest(structure = "slabbed_gametest:rig3b2a_board")
    public void paintingKernelConflictAndMultiUuidSeamStayBounded(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockServerPlayer(helper);
        Registry<PaintingVariant> registry = paintingRegistry(world);
        Holder.Reference<PaintingVariant> kebab = holder(registry, "minecraft:kebab");
        OwnedFixture owned = new OwnedFixture(world);

        Backing conflictBacking = buildBacking(helper, owned, new BlockPos(5, 4, 5), FACE, 1, 1);
        Attempt first = executePinned(helper, player, owned, conflictBacking, kebab, null);
        assertPlaced(helper, first, "minecraft:kebab", 1, 1);
        UUID existing = first.paintings().getFirst().uuid();
        Attempt conflict = executePinned(helper, player, owned, conflictBacking, kebab, null);
        assertRefused(helper, conflict, InteractionResult.CONSUME, 1);
        if (world.getEntity(existing) == null) {
            throw helper.assertionException("conflicting attempt removed the pre-existing painting");
        }

        Backing primary = buildBacking(helper, owned, new BlockPos(12, 4, 5), FACE, 1, 1);
        Backing injected = buildBacking(helper, owned, new BlockPos(18, 4, 5), FACE, 1, 1);
        Consumer<AttemptInjection> extraPainting = injection -> {
            Painting extra = new Painting(world, injected.anchor(), injected.face(), kebab);
            if (!extra.survives() || !world.addFreshEntity(extra)) {
                throw helper.assertionException("multi-UUID seam could not add its bounded extra painting");
            }
        };
        Attempt multiple = executePinned(helper, player, owned, primary, kebab, extraPainting);
        if (multiple.newPaintingUuids().size() != 2 || multiple.paintings().size() != 2
                || !multiple.boundedFailure()) {
            throw helper.assertionException("kernel failed to capture every exact new UUID before flagging >1: "
                    + multiple);
        }
        if (!owned.entityUuids.containsAll(multiple.newPaintingUuids())) {
            throw helper.assertionException("multi-UUID failure left a created entity outside ownership");
        }

        owned.clearEntityFirst();
        for (UUID uuid : multiple.newPaintingUuids()) {
            if (world.getEntity(uuid) != null) {
                throw helper.assertionException("multi-UUID exact clear left owned entity " + uuid);
            }
        }
        helper.succeed();
    }

    @GameTest(structure = "slabbed_gametest:rig3b2a_board")
    public void paintingKernelUuidAndCellClearPreservesForeignSentinels(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockServerPlayer(helper);
        Registry<PaintingVariant> registry = paintingRegistry(world);
        Holder.Reference<PaintingVariant> kebab = holder(registry, "minecraft:kebab");

        Backing foreignBacking = buildForeignBacking(helper, new BlockPos(4, 4, 4), FACE, 1, 1);
        Painting foreignPainting = new Painting(world, foreignBacking.anchor(), FACE, kebab);
        if (!foreignPainting.survives() || !world.addFreshEntity(foreignPainting)) {
            throw helper.assertionException("foreign painting sentinel failed to initialize");
        }
        ItemEntity foreignItem = new ItemEntity(world,
                foreignBacking.anchor().getX() + 0.5,
                foreignBacking.anchor().getY() + 2.0,
                foreignBacking.anchor().getZ() + 0.5,
                new ItemStack(Items.DIAMOND));
        if (!world.addFreshEntity(foreignItem)) {
            throw helper.assertionException("foreign non-painting sentinel failed to initialize");
        }

        BlockPos markerSupport = helper.absolutePos(new BlockPos(10, 2, 10));
        BlockPos markerSubject = markerSupport.above();
        world.setBlock(markerSupport, Blocks.STONE_SLAB.defaultBlockState(), 3);
        world.setBlock(markerSubject, Blocks.STONE.defaultBlockState(), 3);
        SlabAnchorAttachment.addAnchor(world, markerSubject, world.getBlockState(markerSubject));
        CellFingerprint foreignCellBefore = cellFingerprint(world, markerSubject);
        String foreignPaintingBefore = entityFingerprint(world, foreignPainting);
        String foreignItemBefore = entityFingerprint(world, foreignItem);

        OwnedFixture owned = new OwnedFixture(world);
        Backing ownedBacking = buildBacking(helper, owned, new BlockPos(16, 4, 4), FACE, 1, 1);
        Attempt placed = executePinned(helper, player, owned, ownedBacking, kebab, null);
        assertPlaced(helper, placed, "minecraft:kebab", 1, 1);
        UUID ownedUuid = placed.paintings().getFirst().uuid();

        owned.clearEntityFirst();
        if (world.getEntity(ownedUuid) != null || !owned.isEmpty()) {
            throw helper.assertionException("exact owned painting/cells were not cleared");
        }
        if (!foreignPaintingBefore.equals(entityFingerprint(world, foreignPainting))
                || !foreignItemBefore.equals(entityFingerprint(world, foreignItem))
                || !foreignCellBefore.equals(cellFingerprint(world, markerSubject))) {
            throw helper.assertionException("UUID/cell clear changed a foreign entity/block/store/marker sentinel");
        }
        // The equality assertion above is the proof. This exact test-owned teardown prevents a sentinel
        // deliberately excluded from rig ownership from leaking into another concurrently scheduled test.
        foreignPainting.discard();
        foreignItem.discard();
        for (BlockPos pos : foreignBacking.cells()) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        SlabAnchorAttachment.removeAnchor(world, markerSubject);
        world.setBlock(markerSubject, Blocks.AIR.defaultBlockState(), 3);
        world.setBlock(markerSupport, Blocks.AIR.defaultBlockState(), 3);
        helper.succeed();
    }

    @GameTest(structure = "slabbed_gametest:rig3b2a_board")
    public void paintingKernelThrowableAfterUseQuarantinesCreatedUuid(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockServerPlayer(helper);
        Holder.Reference<PaintingVariant> kebab = holder(paintingRegistry(world), "minecraft:kebab");
        OwnedFixture owned = new OwnedFixture(world);
        Backing backing = buildBacking(helper, owned, new BlockPos(6, 5, 6), FACE, 1, 1);
        ItemStack handBefore = player.getItemInHand(InteractionHand.MAIN_HAND).copy();
        try {
            executePinned(helper, player, owned, backing, kebab,
                    ignored -> {
                        throw new InjectedAfterUseFailure();
                    });
            throw helper.assertionException("injected after-use failure did not propagate");
        } catch (InjectedAfterUseFailure expected) {
            // The finally path must already have quarantined the exact newly created UUID.
        }
        if (owned.entityUuids.size() != 1
                || world.getEntity(owned.entityUuids.getFirst()) == null
                || !ItemStack.matches(handBefore, player.getItemInHand(InteractionHand.MAIN_HAND))) {
            throw helper.assertionException(
                    "throwable post-use path escaped UUID quarantine or hand restoration");
        }
        UUID created = owned.entityUuids.getFirst();
        owned.clearEntityFirst();
        if (world.getEntity(created) != null || !owned.isEmpty()) {
            throw helper.assertionException("throwable post-use quarantine did not exact-clear");
        }
        helper.succeed();
    }

    private static Attempt executePinned(GameTestHelper helper, ServerPlayer player,
                                         OwnedFixture owned, Backing backing,
                                         Holder.Reference<PaintingVariant> variant,
                                         Consumer<AttemptInjection> afterUse) {
        return execute(helper, player, owned, backing, variant, afterUse);
    }

    /** Converts the pure planner's one relative page into one finite absolute disposable-world board. */
    private static AdaptedPage adaptPlannerPage(GameTestHelper helper,
                                                SlabRigHangingPaintingPlan.PagePlan page) {
        if (page.routeIndex() != 6143 || page.topologyIndex() != 42 || page.selectorPage() != 1
                || page.cases().size() != SlabRigHangingPaintingPlan.PAGE_SIZE) {
            throw helper.assertionException("kernel adapter accepts only route6143/topology42/page1");
        }
        BlockPos origin = helper.absolutePos(new BlockPos(8, 3, 8));
        List<AdaptedCase> cases = new ArrayList<>(page.cases().size());
        LinkedHashSet<BlockPos> reserved = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> clearOwned = new LinkedHashSet<>();
        for (SlabRigHangingPaintingPlan.CasePlan planned : page.cases()) {
            List<AdaptedCell> topology = planned.topologyCells().stream()
                    .map(cell -> adaptCell(origin, cell)).toList();
            List<AdaptedCell> backing = planned.backingCells().stream()
                    .map(cell -> adaptCell(origin, cell)).toList();
            List<BlockPos> support = planned.supportCells().stream()
                    .map(origin::offset).map(BlockPos::immutable).toList();
            for (BlockPos relative : planned.reservedCells()) {
                BlockPos absolute = origin.offset(relative).immutable();
                if (!reserved.add(absolute)) {
                    throw helper.assertionException("planner tiles overlap after absolute adaptation at " + absolute);
                }
            }
            for (SlabRigHangingPaintingPlan.CellPlan cell : planned.clearOwnedCells()) {
                BlockPos absolute = origin.offset(cell.relativePos()).immutable();
                if (!clearOwned.add(absolute)) {
                    throw helper.assertionException("planner clear ownership overlaps at " + absolute);
                }
            }
            Backing attemptBacking = new Backing(origin.offset(planned.clicked()).immutable(),
                    planned.clickedFace(), backing.stream().map(AdaptedCell::absolutePos).toList());
            if (!attemptBacking.anchor().equals(origin.offset(planned.anchor()))) {
                throw helper.assertionException("planner anchor changed during absolute adaptation");
            }
            cases.add(new AdaptedCase(planned, topology, backing, support, attemptBacking));
        }
        Bounds bounds = Bounds.of(reserved);
        if (bounds.xSize() > 40 || bounds.ySize() > 20 || bounds.zSize() > 40) {
            throw helper.assertionException("planner witness escaped finite 40x20x40 envelope: " + bounds);
        }
        return new AdaptedPage(page, origin, List.copyOf(cases), Set.copyOf(reserved),
                Set.copyOf(clearOwned), bounds);
    }

    private static AdaptedCell adaptCell(BlockPos origin,
                                         SlabRigHangingPaintingPlan.CellPlan cell) {
        return new AdaptedCell(cell, origin.offset(cell.relativePos()).immutable());
    }

    private static void buildAdaptedPage(GameTestHelper helper, ServerPlayer player,
                                         OwnedFixture owned, AdaptedPage page) {
        ServerLevel world = helper.getLevel();
        // Validate and install the complete exact cell envelope before the first world write.
        for (BlockPos pos : page.reservedCells()) {
            if (!world.getBlockState(pos).isAir()) {
                throw helper.assertionException("planned board reservation is not empty at " + pos);
            }
        }
        owned.reserveCells(page.clearOwnedCells());
        for (AdaptedCase planned : page.cases()) {
            planned.topologyCells().stream()
                    .sorted(Comparator.comparingInt(cell -> cell.absolutePos().getY()))
                    .forEach(cell -> authorPlannedCell(helper, player, cell));
            planned.backingCells().stream()
                    .sorted(Comparator.comparingInt(cell -> cell.absolutePos().getY()))
                    .forEach(cell -> authorPlannedCell(helper, player, cell));
            if (planned.plan().foundations().size() != 4 || planned.backingCells().size() != 16
                    || !SlabRigHangingPaintingPlan.FOOTPRINT_EXPANSION
                    .equals(planned.plan().footprintExpansion())) {
                throw helper.assertionException("adapted case lost repeated-SBSBS/4x4 fixture semantics");
            }
        }
    }

    private static SlabRigHangingKernelArtifacts.WrittenArtifact publishPlannedBeforeBuild(
            GameTestHelper helper, ServerPlayer player, OwnedFixture owned, AdaptedPage page,
            Path root, SlabRigHangingKernelArtifacts.PhaseManifest planned) throws IOException {
        SlabRigHangingKernelArtifacts.WrittenArtifact written =
                SlabRigHangingKernelArtifacts.write(root, planned);
        // This is the first disposable-world mutation, and can execute only after exact final readback.
        buildAdaptedPage(helper, player, owned, page);
        return written;
    }

    private static void authorPlannedCell(GameTestHelper helper, ServerPlayer player,
                                          AdaptedCell adapted) {
        ServerLevel world = helper.getLevel();
        SlabRigHangingPaintingPlan.CellPlan cell = adapted.plan();
        BlockPos target = adapted.absolutePos();
        if ("DIRECT_FIXTURE_SET".equals(cell.placementMethod())) {
            world.setBlock(target, expectedState(cell.stateRecipe()), 3);
        } else if ("PLAYER_ITEM_USEON".equals(cell.placementMethod())) {
            BlockPos clicked = target.below();
            if (world.getBlockState(clicked).isAir()) {
                throw helper.assertionException("planned useOn cell has no authored support below: " + target);
            }
            ItemStack previous = player.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack stack = new ItemStack(itemForRecipe(cell.stateRecipe()));
            try {
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                Vec3 hit = Vec3.atCenterOf(clicked).add(0.0, 0.5, 0.0);
                InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(hit, Direction.UP, clicked, false)));
                if (!result.consumesAction()) {
                    throw helper.assertionException("planned player useOn refused " + cell + " result=" + result);
                }
            } finally {
                player.setItemInHand(InteractionHand.MAIN_HAND, previous);
            }
        } else {
            throw helper.assertionException("unknown planner placement method " + cell.placementMethod());
        }
        BlockState expected = expectedState(cell.stateRecipe());
        BlockState actual = world.getBlockState(target);
        if (!actual.equals(expected)) {
            throw helper.assertionException("planned cell readback mismatch at " + target
                    + " expected=" + expected + " actual=" + actual + " plan=" + cell);
        }
    }

    private static Item itemForRecipe(String recipe) {
        return switch (recipe) {
            case "minecraft:stone" -> Items.STONE;
            case "minecraft:stone_slab[type=bottom]" -> Items.STONE_SLAB;
            case "minecraft:smooth_stone_slab[type=bottom]" -> Items.SMOOTH_STONE_SLAB;
            default -> throw new IllegalArgumentException("unsupported planner item recipe " + recipe);
        };
    }

    private static BlockState expectedState(String recipe) {
        return switch (recipe) {
            case "minecraft:stone" -> Blocks.STONE.defaultBlockState();
            case "minecraft:stone_slab[type=bottom]" -> Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            case "minecraft:smooth_stone_slab[type=bottom]" ->
                    Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
                            .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            default -> throw new IllegalArgumentException("unsupported planner state recipe " + recipe);
        };
    }

    private static SlabRigHangingKernelArtifacts.PagePlan artifactPage(AdaptedPage adapted) {
        List<SlabRigHangingKernelArtifacts.CasePlan> cases = new ArrayList<>();
        for (AdaptedCase entry : adapted.cases()) {
            SlabRigHangingPaintingPlan.CasePlan plan = entry.plan();
            Vec3 hit = Vec3.atCenterOf(entry.attemptBacking().clicked()).add(
                    plan.clickedFace().getStepX() * 0.5,
                    plan.clickedFace().getStepY() * 0.5,
                    plan.clickedFace().getStepZ() * 0.5);
            Bounds effect = Bounds.of(plan.reservedCells().stream()
                    .map(adapted.origin()::offset).map(BlockPos::immutable)
                    .collect(Collectors.toSet()));
            cases.add(new SlabRigHangingKernelArtifacts.CasePlan(
                    plan.executionIndex(), plan.attemptId(), plan.baseCaseId(), plan.route().id(),
                    plan.topology().id(), plan.selector().semanticId(),
                    position(adapted.origin().offset(plan.tileOrigin())),
                    position(entry.attemptBacking().clicked()), plan.clickedFace().getName(),
                    vec3Hex(hit), position(entry.attemptBacking().anchor()),
                    entry.topologyCells().stream().map(AdaptedCell::absolutePos)
                            .map(SlabRigHangingPaintingKernelTest::position).toList(),
                    entry.backingCells().stream().map(AdaptedCell::absolutePos)
                            .map(SlabRigHangingPaintingKernelTest::position).toList(),
                    plan.reservedCells().stream().map(adapted.origin()::offset)
                            .map(SlabRigHangingPaintingKernelTest::position).toList(),
                    SlabRigHangingKernelArtifacts.BoxBits.of(effect.minX(), effect.minY(), effect.minZ(),
                            effect.maxX() + 1.0, effect.maxY() + 1.0, effect.maxZ() + 1.0)));
        }
        SlabRigHangingPaintingPlan.CasePlan first = adapted.plan().cases().getFirst();
        return new SlabRigHangingKernelArtifacts.PagePlan(adapted.plan().planHash(),
                adapted.plan().addressablePage(), adapted.plan().pageCount(), first.route().id(),
                position(adapted.origin()), first.clickedFace().getName(), cases);
    }

    private static SlabRigHangingKernelArtifacts.CaseObservation caseObservation(
            AdaptedCase adapted, Attempt attempt) {
        List<SlabRigHangingKernelArtifacts.Position> authored = adapted.plan().clearOwnedCells().stream()
                .map(cell -> adapted.attemptBacking().clicked().subtract(adapted.plan().clicked())
                        .offset(cell.relativePos()))
                .map(SlabRigHangingPaintingKernelTest::position).toList();
        // The translation above is algebraically the page origin; assert it remains exact by comparing
        // with the already adapted topology/backing union rather than trusting coordinate coincidence.
        Set<SlabRigHangingKernelArtifacts.Position> adaptedAuthored = new LinkedHashSet<>();
        adapted.topologyCells().stream().map(AdaptedCell::absolutePos)
                .map(SlabRigHangingPaintingKernelTest::position).forEach(adaptedAuthored::add);
        adapted.backingCells().stream().map(AdaptedCell::absolutePos)
                .map(SlabRigHangingPaintingKernelTest::position).forEach(adaptedAuthored::add);
        if (!new LinkedHashSet<>(authored).equals(adaptedAuthored)) {
            throw new IllegalStateException("artifact case ownership translation drift");
        }
        SlabRigHangingKernelArtifacts.Ownership ownership =
                new SlabRigHangingKernelArtifacts.Ownership(authored, authored, authored,
                        List.copyOf(attempt.newPaintingUuids()));
        Set<UUID> removed = new LinkedHashSet<>(attempt.beforePaintingUuids());
        removed.removeAll(attempt.afterPaintingUuids());
        String outcome = attempt.newPaintingUuids().size() == 1 ? "PLACED_SURVIVES"
                : attempt.newPaintingUuids().isEmpty() ? "VANILLA_REFUSAL"
                : "BOUNDED_FAILURE_MULTIPLE_ENTITIES";
        return new SlabRigHangingKernelArtifacts.CaseObservation(adapted.plan().attemptId(),
                attempt.backingBefore().stream().map(SlabRigHangingPaintingKernelTest::cellObservation)
                        .toList(),
                attempt.beforePaintingEvidence().stream()
                        .map(SlabRigHangingPaintingKernelTest::entityObservation).toList(),
                interactionName(attempt.result()), attempt.result().consumesAction(),
                attempt.stackBeforeEvidence(), attempt.stackAfterEvidence(),
                List.copyOf(attempt.newPaintingUuids()), List.copyOf(removed),
                attempt.backingAfter().stream().map(SlabRigHangingPaintingKernelTest::cellObservation)
                        .toList(),
                attempt.afterPaintingEvidence().stream()
                        .map(SlabRigHangingPaintingKernelTest::entityObservation).toList(),
                ownership, outcome,
                "entityStore=" + attempt.entityStore() + ";entityLiveDy=" + attempt.entityLiveDy()
                        + ";handRestored=" + attempt.handRestored() + ";statDelta="
                        + (attempt.statAfter() - attempt.statBefore()));
    }

    private static SlabRigHangingKernelArtifacts.CellObservation cellObservation(
            BackingObservation observation) {
        return new SlabRigHangingKernelArtifacts.CellObservation(position(observation.pos()),
                observation.state(), "NONE", sha256("NONE"),
                Long.parseUnsignedLong(observation.liveDyBits(), 16),
                Long.parseUnsignedLong(observation.storedDyBits(), 16), observation.markers());
    }

    private static SlabRigHangingKernelArtifacts.EntityObservation entityObservation(
            PaintingEvidence evidence) {
        return new SlabRigHangingKernelArtifacts.EntityObservation(evidence.uuid(),
                "minecraft:painting", evidence.variantId(), evidence.componentVariantId(),
                position(evidence.attachment()), evidence.facing().getName(),
                SlabRigHangingKernelArtifacts.Vec3Bits.of(evidence.position()),
                SlabRigHangingKernelArtifacts.BoxBits.of(evidence.aabb()), evidence.survives(),
                evidence.alive(), evidence.removed(), sha256(evidence.sortedNbt()));
    }

    private static SlabRigHangingKernelArtifacts.Position position(BlockPos pos) {
        return SlabRigHangingKernelArtifacts.Position.of(pos);
    }

    private static String vec3Hex(Vec3 value) {
        return Double.toHexString(value.x) + ',' + Double.toHexString(value.y) + ','
                + Double.toHexString(value.z);
    }

    private static String interactionName(InteractionResult result) {
        if (result == InteractionResult.SUCCESS) {
            return "SUCCESS";
        }
        if (result == InteractionResult.CONSUME) {
            return "CONSUME";
        }
        if (result == InteractionResult.FAIL) {
            return "FAIL";
        }
        if (result == InteractionResult.PASS) {
            return "PASS";
        }
        return result.toString();
    }

    private static String testBuildSha() {
        return BuildStamp.GIT_SHA.matches("[0-9a-f]{7,64}")
                ? BuildStamp.GIT_SHA : sha256("RIG-3B2A-DISPOSABLE-TEST-BUILD").substring(0, 12);
    }

    /** Single seam to which the canonical planner CasePlan adapter is bound after its API is frozen. */
    private static Attempt execute(GameTestHelper helper, ServerPlayer player,
                                   OwnedFixture owned, Backing backing,
                                   Holder.Reference<PaintingVariant> variant,
                                   Consumer<AttemptInjection> afterUse) {
        if (owned == null || backing == null || player == null) {
            throw helper.assertionException("painting kernel requires ownership installed before useOn");
        }
        ServerLevel world = helper.getLevel();
        if (player.level() != world) {
            throw helper.assertionException("painting kernel player/world mismatch");
        }

        List<PaintingEvidence> beforePaintingEvidence = allPaintingEvidence(helper, world);
        Set<UUID> before = beforePaintingEvidence.stream().map(PaintingEvidence::uuid)
                .collect(Collectors.toUnmodifiableSet());
        List<BackingObservation> backingBefore = backing.cells().stream()
                .map(pos -> backingObservation(world, pos)).toList();
        ItemStack previousHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack previousSnapshot = previousHand.copy();
        ItemStack attemptStack = new ItemStack(Items.PAINTING);
        if (variant != null) {
            attemptStack.set(DataComponents.PAINTING_VARIANT, variant);
        }
        String stackBeforeEvidence = stackEvidence(attemptStack);
        int stackBefore = attemptStack.getCount();
        int statBefore = player.getStats().getValue(PAINTING_USED);
        InteractionResult result;
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, attemptStack);
            Vec3 hit = Vec3.atCenterOf(backing.clicked()).add(
                    backing.face().getStepX() * 0.5,
                    backing.face().getStepY() * 0.5,
                    backing.face().getStepZ() * 0.5);
            result = attemptStack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(hit, backing.face(), backing.clicked(), false)));
            if (afterUse != null) {
                afterUse.accept(new AttemptInjection(world, owned));
            }
        } finally {
            try {
                player.setItemInHand(InteractionHand.MAIN_HAND, previousHand);
            } finally {
                quarantineNewPaintingUuids(world, before, owned);
            }
        }

        int statAfter = player.getStats().getValue(PAINTING_USED);
        int stackAfter = attemptStack.getCount();
        String stackAfterEvidence = stackEvidence(attemptStack);
        boolean handRestored = ItemStack.matches(previousSnapshot,
                player.getItemInHand(InteractionHand.MAIN_HAND));
        List<PaintingEvidence> afterPaintingEvidence = allPaintingEvidence(helper, world);
        Set<UUID> after = afterPaintingEvidence.stream().map(PaintingEvidence::uuid)
                .collect(Collectors.toUnmodifiableSet());
        LinkedHashSet<UUID> created = after.stream().filter(uuid -> !before.contains(uuid))
                .sorted().collect(Collectors.toCollection(LinkedHashSet::new));
        if (!owned.entityUuids.containsAll(created)) {
            throw helper.assertionException("post-use evidence escaped the finally-path UUID quarantine");
        }

        List<PaintingEvidence> evidence = new ArrayList<>();
        for (UUID uuid : created) {
            Entity entity = world.getEntity(uuid);
            if (!(entity instanceof Painting painting)) {
                throw helper.assertionException("new painting UUID did not resolve to Painting: " + uuid);
            }
            PaintingEvidence observed = inspectPainting(helper, painting);
            if (created.size() == 1
                    && (!painting.getPos().equals(backing.anchor())
                    || painting.getDirection() != backing.face())) {
                throw helper.assertionException(
                        "single created painting attachment/facing differ from real useOn input");
            }
            evidence.add(observed);
        }
        evidence.sort(Comparator.comparing(entry -> entry.uuid().toString()));
        List<BackingObservation> backingAfter = backing.cells().stream()
                .map(pos -> backingObservation(world, pos)).toList();
        return new Attempt(result, stackBefore, stackAfter, statBefore, statAfter, handRestored,
                Set.copyOf(before), Set.copyOf(after), Set.copyOf(created), List.copyOf(evidence),
                beforePaintingEvidence, afterPaintingEvidence,
                stackBeforeEvidence, stackAfterEvidence,
                backingBefore, backingAfter, ENTITY_STORE, ENTITY_LIVE_DY, created.size() > 1);
    }

    private static PaintingEvidence inspectPainting(GameTestHelper helper, Backing backing,
                                                     Painting painting) {
        PaintingEvidence evidence = inspectPainting(helper, painting);
        if (!painting.getPos().equals(backing.anchor()) || painting.getDirection() != backing.face()) {
            throw helper.assertionException("painting attachment/facing differ from real useOn input");
        }
        return evidence;
    }

    private static PaintingEvidence inspectPainting(GameTestHelper helper, Painting painting) {
        Holder<PaintingVariant> variant = painting.getVariant();
        ResourceKey<PaintingVariant> key = variant.unwrapKey()
                .orElseThrow(() -> helper.assertionException("placed painting holder is not registry-backed"));
        PaintingVariant value = variant.value();
        String variantId = key.identifier().toString();
        Holder<PaintingVariant> component = painting.get(DataComponents.PAINTING_VARIANT);
        if (component == null || component.unwrapKey().isEmpty()
                || !component.unwrapKey().orElseThrow().equals(key)) {
            throw helper.assertionException("placed painting component/final holder disagree for " + variantId);
        }
        AABB expectedAabb = expectedAabb(painting.getPos(), painting.getDirection(),
                value.width(), value.height());
        if (!painting.getBoundingBox().equals(expectedAabb)
                || !painting.position().equals(expectedAabb.getCenter())
                || painting.getBoundingBox().hasNaN()) {
            throw helper.assertionException("placed painting position/AABB differs from mapped geometry: "
                    + painting.getBoundingBox() + " expected=" + expectedAabb);
        }

        CompoundTag nbt = saveNbt(helper, painting);
        Identifier nbtVariant = nbt.read("variant", Identifier.CODEC)
                .orElseThrow(() -> helper.assertionException("painting NBT lacks variant"));
        BlockPos nbtPos = nbt.read("block_pos", BlockPos.CODEC)
                .orElseThrow(() -> helper.assertionException("painting NBT lacks block_pos"));
        Direction nbtFacing = nbt.read("facing", Direction.LEGACY_ID_CODEC_2D)
                .orElseThrow(() -> helper.assertionException("painting NBT lacks facing"));
        UUID nbtUuid = nbt.read("UUID", UUIDUtil.CODEC)
                .orElseThrow(() -> helper.assertionException("painting NBT lacks UUID"));
        if (!"minecraft:painting".equals(nbt.getStringOr("id", ""))
                || !nbtVariant.toString().equals(variantId)
                || !nbtPos.equals(painting.getPos())
                || nbtFacing != painting.getDirection()
                || !nbtUuid.equals(painting.getUUID())) {
            throw helper.assertionException("painting direct fields/NBT disagree: " + nbt);
        }
        return new PaintingEvidence(painting.getUUID(), variantId,
                component.unwrapKey().orElseThrow().identifier().toString(), painting.getDirection(),
                painting.getPos(), painting.position(), painting.getBoundingBox(), aabbHex(painting.getBoundingBox()),
                value.width(), value.height(), painting.survives(), painting.isAlive(),
                painting.isRemoved(),
                String.valueOf(painting.getRemovalReason()), sortedSnbt(nbt),
                ENTITY_STORE, ENTITY_LIVE_DY);
    }

    private static List<PaintingEvidence> allPaintingEvidence(GameTestHelper helper,
                                                              ServerLevel world) {
        return world.getEntities(EntityTypes.PAINTING, painting -> true).stream()
                .map(painting -> inspectPainting(helper, painting))
                .sorted(Comparator.comparing(entry -> entry.uuid().toString())).toList();
    }

    /**
     * Raw UUID-only emergency quarantine. It runs in the useOn finally path before any mapped evidence
     * inspection can throw; deletion still occurs later only through exact {@link ServerLevel#getEntity}.
     */
    private static void quarantineNewPaintingUuids(ServerLevel world, Set<UUID> before,
                                                   OwnedFixture owned) {
        List<UUID> created = world.getEntities(EntityTypes.PAINTING, painting -> true).stream()
                .map(Entity::getUUID).filter(uuid -> !before.contains(uuid)).sorted().toList();
        owned.entityUuids.addAll(created);
    }

    private static String stackEvidence(ItemStack stack) {
        String item = stack.isEmpty() ? "minecraft:air"
                : String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        Holder<PaintingVariant> component = stack.get(DataComponents.PAINTING_VARIANT);
        String variant = component == null ? "ABSENT" : component.unwrapKey()
                .map(key -> key.identifier().toString()).orElse("UNKEYED");
        return "item=" + item + ";count=" + stack.getCount()
                + ";minecraft:painting/variant=" + variant;
    }

    private static void assertPlaced(GameTestHelper helper, Attempt attempt, String variant,
                                     int width, int height) {
        if (attempt.result() != InteractionResult.SUCCESS || attempt.paintings().size() != 1
                || attempt.newPaintingUuids().size() != 1 || attempt.boundedFailure()) {
            throw helper.assertionException("expected one successful painting, got " + attempt);
        }
        PaintingEvidence evidence = attempt.paintings().getFirst();
        if (!variant.equals(evidence.variantId()) || !variant.equals(evidence.componentVariantId())
                || evidence.width() != width
                || evidence.height() != height || !evidence.survives() || !evidence.alive()
                || evidence.removed() || !"null".equals(evidence.removalReason())
                || !ENTITY_STORE.equals(evidence.entityStore())
                || !ENTITY_LIVE_DY.equals(evidence.entityLiveDy())) {
            throw helper.assertionException("placed painting evidence mismatch: " + evidence);
        }
        assertAttemptEnvelope(helper, attempt, 1);
    }

    private static void assertRefused(GameTestHelper helper, Attempt attempt,
                                      InteractionResult expectedResult, int expectedStatDelta) {
        if (attempt.result() != expectedResult || !attempt.paintings().isEmpty()
                || !attempt.newPaintingUuids().isEmpty() || attempt.stackBefore() != attempt.stackAfter()
                || attempt.statAfter() - attempt.statBefore() != expectedStatDelta
                || !attempt.handRestored() || attempt.boundedFailure()) {
            throw helper.assertionException("painting refusal contract mismatch: " + attempt);
        }
    }

    private static void assertAttemptEnvelope(GameTestHelper helper, Attempt attempt,
                                              int expectedStatDelta) {
        if (attempt.stackBefore() != 1 || attempt.stackAfter() != 0
                || attempt.statAfter() - attempt.statBefore() != expectedStatDelta
                || !attempt.handRestored()
                || !ENTITY_STORE.equals(attempt.entityStore())
                || !ENTITY_LIVE_DY.equals(attempt.entityLiveDy())
                || !attempt.backingBefore().equals(attempt.backingAfter())) {
            throw helper.assertionException("painting useOn hand/stack/stat/backing envelope mismatch: " + attempt);
        }
        for (BackingObservation observation : attempt.backingAfter()) {
            if (!observation.state().contains("minecraft:stone")) {
                throw helper.assertionException("painting backing state changed or is not explicit: " + observation);
            }
        }
    }

    private static ServerPlayer mockServerPlayer(GameTestHelper helper) {
        if (!(helper.makeMockServerPlayer(GameType.SURVIVAL) instanceof ServerPlayer player)) {
            throw helper.assertionException("GameTest did not provide a server-backed mock player");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 3));
        return player;
    }

    private static Registry<PaintingVariant> paintingRegistry(ServerLevel world) {
        return world.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT);
    }

    private static Holder.Reference<PaintingVariant> holder(Registry<PaintingVariant> registry,
                                                            String id) {
        Identifier identifier = Identifier.parse(id);
        return registry.get(ResourceKey.create(Registries.PAINTING_VARIANT, identifier))
                .orElseThrow(() -> new IllegalArgumentException("missing painting variant " + id));
    }

    private static Backing buildBacking(GameTestHelper helper, OwnedFixture owned,
                                        BlockPos clickedRelative, Direction face,
                                        int width, int height) {
        BlockPos clicked = helper.absolutePos(clickedRelative);
        List<BlockPos> cells = backingCells(clicked, face, width, height);
        for (BlockPos cell : cells) {
            owned.setBlock(cell, Blocks.STONE.defaultBlockState());
        }
        return new Backing(clicked, face, cells);
    }

    private static Backing buildForeignBacking(GameTestHelper helper, BlockPos clickedRelative,
                                               Direction face, int width, int height) {
        BlockPos clicked = helper.absolutePos(clickedRelative);
        List<BlockPos> cells = backingCells(clicked, face, width, height);
        for (BlockPos cell : cells) {
            helper.getLevel().setBlock(cell, Blocks.STONE.defaultBlockState(), 3);
        }
        return new Backing(clicked, face, cells);
    }

    private static List<BlockPos> backingCells(BlockPos clicked, Direction face,
                                               int width, int height) {
        Direction lateral = face.getCounterClockWise();
        List<BlockPos> cells = new ArrayList<>();
        for (int y = -((height - 1) / 2); y <= height / 2; y++) {
            for (int x = -((width - 1) / 2); x <= width / 2; x++) {
                cells.add(clicked.relative(lateral, x).above(y).immutable());
            }
        }
        return List.copyOf(cells);
    }

    private static AABB expectedAabb(BlockPos anchor, Direction direction, int width, int height) {
        Vec3 center = Vec3.atCenterOf(anchor)
                .relative(direction, -0.46875)
                .relative(direction.getCounterClockWise(), width % 2 == 0 ? 0.5 : 0.0)
                .relative(Direction.UP, height % 2 == 0 ? 0.5 : 0.0);
        return AABB.ofSize(center,
                direction.getAxis() == Direction.Axis.X ? 0.0625 : width,
                height,
                direction.getAxis() == Direction.Axis.Z ? 0.0625 : width);
    }

    private static BackingObservation backingObservation(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return new BackingObservation(pos, state.toString(),
                rawBits(SlabAnchorAttachment.storedPlacementDy(world, pos)),
                rawBits(SlabSupport.getYOffset(world, pos, state)),
                markerFingerprint(world, pos, state));
    }

    private static CellFingerprint cellFingerprint(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return new CellFingerprint(pos, state.toString(),
                rawBits(SlabAnchorAttachment.storedPlacementDy(world, pos)),
                rawBits(SlabSupport.getYOffset(world, pos, state)),
                markerFingerprint(world, pos, state));
    }

    private static String markerFingerprint(ServerLevel world, BlockPos pos, BlockState state) {
        return "anchored=" + SlabAnchorAttachment.isAnchored(world, pos)
                + ",frozen=" + SlabAnchorAttachment.isFrozenFlat(world, pos)
                + ",compound=" + SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)
                + ",sideLower=" + SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                + ",sideUpper=" + SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)
                + ",sideDouble=" + SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)
                + ",ownerTop=" + SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state);
    }

    private static CompoundTag saveNbt(GameTestHelper helper, Entity entity) {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(problems,
                helper.getLevel().registryAccess());
        if (!entity.save(output) || !problems.isEmpty()) {
            throw helper.assertionException("entity NBT save failed: " + problems.getTreeReport());
        }
        return output.buildResult();
    }

    private static String sortedSnbt(CompoundTag tag) {
        return new TextComponentTagVisitor("", TextComponentTagVisitor.PlainStyling.INSTANCE, true)
                .visit(tag).getString();
    }

    private static String entityFingerprint(ServerLevel world, Entity entity) {
        Entity current = world.getEntity(entity.getUUID());
        if (current == null || current != entity) {
            return "ABSENT:" + entity.getUUID();
        }
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(problems, world.registryAccess());
        if (!entity.save(output) || !problems.isEmpty()) {
            return "SAVE_FAILURE:" + problems.getTreeReport();
        }
        return entity.getType() + "|" + entity.getUUID() + "|" + aabbHex(entity.getBoundingBox())
                + "|" + sortedSnbt(output.buildResult());
    }

    private static String aabbHex(AABB box) {
        return Double.toHexString(box.minX) + "," + Double.toHexString(box.minY) + ","
                + Double.toHexString(box.minZ) + ".." + Double.toHexString(box.maxX) + ","
                + Double.toHexString(box.maxY) + "," + Double.toHexString(box.maxZ);
    }

    private static String rawBits(double value) {
        return Long.toUnsignedString(Double.doubleToRawLongBits(value), 16);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort teardown of a unique disposable test root.
                }
            });
        } catch (IOException ignored) {
            // Best-effort teardown of a unique disposable test root.
        }
    }

    private record Backing(BlockPos clicked, Direction face, List<BlockPos> cells) {
        private Backing {
            cells = List.copyOf(cells);
        }

        BlockPos anchor() {
            return clicked.relative(face);
        }
    }

    private record BackingObservation(BlockPos pos, String state, String storedDyBits,
                                      String liveDyBits, String markers) {
    }

    private record CellFingerprint(BlockPos pos, String state, String storedDyBits,
                                   String liveDyBits, String markers) {
    }

    private record PaintingEvidence(UUID uuid, String variantId, String componentVariantId,
                                    Direction facing,
                                    BlockPos attachment, Vec3 position, AABB aabb, String aabbHex,
                                    int width, int height, boolean survives, boolean alive,
                                    boolean removed,
                                    String removalReason, String sortedNbt,
                                    String entityStore, String entityLiveDy) {
    }

    private record Attempt(InteractionResult result, int stackBefore, int stackAfter,
                           int statBefore, int statAfter, boolean handRestored,
                           Set<UUID> beforePaintingUuids, Set<UUID> afterPaintingUuids,
                           Set<UUID> newPaintingUuids, List<PaintingEvidence> paintings,
                           List<PaintingEvidence> beforePaintingEvidence,
                           List<PaintingEvidence> afterPaintingEvidence,
                           String stackBeforeEvidence, String stackAfterEvidence,
                           List<BackingObservation> backingBefore,
                           List<BackingObservation> backingAfter,
                           String entityStore, String entityLiveDy, boolean boundedFailure) {
    }

    private record AttemptInjection(ServerLevel world, OwnedFixture ownership) {
    }

    private record AdaptedCell(SlabRigHangingPaintingPlan.CellPlan plan, BlockPos absolutePos) {
    }

    private record AdaptedCase(SlabRigHangingPaintingPlan.CasePlan plan,
                               List<AdaptedCell> topologyCells,
                               List<AdaptedCell> backingCells,
                               List<BlockPos> supportCells,
                               Backing attemptBacking) {
        private AdaptedCase {
            topologyCells = List.copyOf(topologyCells);
            backingCells = List.copyOf(backingCells);
            supportCells = List.copyOf(supportCells);
        }
    }

    private record AdaptedPage(SlabRigHangingPaintingPlan.PagePlan plan, BlockPos origin,
                               List<AdaptedCase> cases, Set<BlockPos> reservedCells,
                               Set<BlockPos> clearOwnedCells, Bounds bounds) {
        private AdaptedPage {
            cases = List.copyOf(cases);
            reservedCells = Set.copyOf(reservedCells);
            clearOwnedCells = Set.copyOf(clearOwnedCells);
        }
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static Bounds of(Set<BlockPos> positions) {
            if (positions.isEmpty()) {
                throw new IllegalArgumentException("cannot bound an empty fixture");
            }
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : positions) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        private int xSize() {
            return maxX - minX + 1;
        }

        private int ySize() {
            return maxY - minY + 1;
        }

        private int zSize() {
            return maxZ - minZ + 1;
        }
    }

    private static final class InjectedAfterUseFailure extends RuntimeException {
    }

    private static final class OwnedFixture {
        private final ServerLevel world;
        private final LinkedHashSet<UUID> entityUuids = new LinkedHashSet<>();
        private final LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();

        private OwnedFixture(ServerLevel world) {
            this.world = world;
        }

        private void reserveCells(Set<BlockPos> planned) {
            for (BlockPos pos : planned.stream()
                    .sorted(Comparator.<BlockPos>comparingInt(pos -> pos.getX())
                            .thenComparingInt(pos -> pos.getY()).thenComparingInt(pos -> pos.getZ()))
                    .toList()) {
                if (!world.getBlockState(pos).isAir() || !cells.add(pos.immutable())) {
                    throw new IllegalStateException("cannot install exact cell ownership at " + pos);
                }
            }
        }

        private void setBlock(BlockPos pos, BlockState state) {
            if (!world.getBlockState(pos).isAir()) {
                throw new IllegalStateException("test fixture refuses to overwrite non-air cell " + pos);
            }
            world.setBlock(pos, state, 3);
            cells.add(pos.immutable());
        }

        /** Entity-first exact teardown; never discovers deletion targets by region, class, or proximity. */
        private void clearEntityFirst() {
            List<SlabRigHangingKernelArtifacts.Position> positions = cells.stream()
                    .map(SlabRigHangingPaintingKernelTest::position).toList();
            clearEntityFirstWithReceipt(new SlabRigHangingKernelArtifacts.Ownership(
                    positions, positions, positions, List.copyOf(entityUuids)));
        }

        private SlabRigHangingKernelArtifacts.ClearResult clearEntityFirstWithReceipt(
                SlabRigHangingKernelArtifacts.Ownership ownership) {
            Set<UUID> requestedEntitySet = Set.copyOf(ownership.ownedEntityUuids());
            Set<BlockPos> requestedCellSet = ownership.clearOwnedCells().stream()
                    .map(SlabRigHangingKernelArtifacts.Position::toBlockPos)
                    .collect(Collectors.toUnmodifiableSet());
            Set<BlockPos> requestedAttachmentSet = ownership.attachmentCells().stream()
                    .map(SlabRigHangingKernelArtifacts.Position::toBlockPos)
                    .collect(Collectors.toUnmodifiableSet());
            if (!requestedEntitySet.equals(Set.copyOf(entityUuids))
                    || !requestedCellSet.equals(Set.copyOf(cells))) {
                throw new IllegalStateException("clear receipt does not match installed exact ownership");
            }

            List<UUID> removedEntities = new ArrayList<>();
            List<UUID> alreadyAbsentEntities = new ArrayList<>();
            for (UUID uuid : requestedEntitySet.stream().sorted().toList()) {
                Entity entity = world.getEntity(uuid);
                if (entity == null) {
                    alreadyAbsentEntities.add(uuid);
                    continue;
                }
                if (entity.getType() != EntityTypes.PAINTING) {
                    throw new IllegalStateException("owned UUID changed type; refusing deletion: " + uuid);
                }
                entity.discard();
                if (world.getEntity(uuid) != null) {
                    throw new IllegalStateException("owned UUID remained after discard: " + uuid);
                }
                removedEntities.add(uuid);
            }

            List<BlockPos> clearedAttachments = new ArrayList<>();
            List<BlockPos> absentAttachments = new ArrayList<>();
            for (BlockPos pos : requestedAttachmentSet.stream()
                    .sorted(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed()
                            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ))
                    .toList()) {
                BlockState state = world.getBlockState(pos);
                boolean present = !Double.isNaN(SlabAnchorAttachment.storedPlacementDy(world, pos))
                        || markerFingerprint(world, pos, state).contains("=true");
                SlabAnchorAttachment.removeAnchor(world, pos);
                (present ? clearedAttachments : absentAttachments).add(pos);
            }

            List<BlockPos> clearedCells = new ArrayList<>();
            List<BlockPos> alreadyAirCells = new ArrayList<>();
            for (BlockPos pos : requestedCellSet.stream()
                    .sorted(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed()
                            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ))
                    .toList()) {
                if (world.getBlockState(pos).isAir()) {
                    alreadyAirCells.add(pos);
                } else {
                    world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    clearedCells.add(pos);
                }
            }
            entityUuids.clear();
            cells.clear();
            return new SlabRigHangingKernelArtifacts.ClearResult(
                    List.copyOf(requestedEntitySet), removedEntities, alreadyAbsentEntities,
                    requestedCellSet.stream().map(SlabRigHangingPaintingKernelTest::position).toList(),
                    clearedCells.stream().map(SlabRigHangingPaintingKernelTest::position).toList(),
                    alreadyAirCells.stream().map(SlabRigHangingPaintingKernelTest::position).toList(),
                    requestedAttachmentSet.stream().map(SlabRigHangingPaintingKernelTest::position).toList(),
                    clearedAttachments.stream().map(SlabRigHangingPaintingKernelTest::position).toList(),
                    absentAttachments.stream().map(SlabRigHangingPaintingKernelTest::position).toList());
        }

        private boolean isEmpty() {
            return entityUuids.isEmpty() && cells.isEmpty();
        }
    }
}
