package com.slabbed.test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.command.SlabbedOperatorCommands;
import com.slabbed.command.SlabbedOperatorTools;
import com.slabbed.placement.LandingResolution;
import com.slabbed.rig.RigCase;
import com.slabbed.rig.RigManifest;
import com.slabbed.rig.SlabbedRigService;
import com.slabbed.util.SlabbedDiagnosticsBridge;
import com.slabbed.util.SlabSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Forge-native Phase 6D foundation contract for the generic owned-cell rig. */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ForgeRigOperatorGameTest {

    @GameTest(template = "empty", batch = "slabbed_mega_full")
    public void megaRigBuildsFourRowsThroughProxyAndClearsExactOwnership(
            GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(world);
        List<ItemStack> inventoryBefore = snapshotInventory(player);
        SlabbedDiagnosticsBridge.Provider previous = null;
        BlockPos sentinel = null;

        try {
            SlabbedRigService.MegaCoverage fullCoverage =
                    SlabbedRigService.megaCoverage(SlabbedRigService.DEFAULT_MEGA_COLUMNS);
            List<String> expectedItems = SlabbedOperatorTools.paletteItems().stream()
                    .map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
                    .toList();
            List<Long> expectedRowDy = List.of(
                    Double.doubleToRawLongBits(0.0d),
                    Double.doubleToRawLongBits(-0.5d),
                    Double.doubleToRawLongBits(-1.0d),
                    Double.doubleToRawLongBits(-0.5d));
            ctx.assertTrue(fullCoverage.columns() == expectedItems.size()
                            && fullCoverage.columns() == 40
                            && fullCoverage.supportVariants() == 4
                            && fullCoverage.attempts() == 160
                            && fullCoverage.itemIds().equals(expectedItems)
                            && fullCoverage.rowDyBits().equals(expectedRowDy)
                            && fullCoverage.caseIds().size() == 160,
                    "bare /slabrig mega must plan all 40 categories across four support geometries");

            // The full board is far wider than the tiny "empty" template. Keep its complete
            // footprint in a guaranteed air layer instead of colliding with the template shell.
            BlockPos feet = ctx.absolutePos(new BlockPos(4, 300, 12));
            player.moveTo(
                    feet.getX() + 0.5d,
                    feet.getY(),
                    feet.getZ() + 0.5d,
                    0.0f,
                    0.0f);
            Direction facing = player.getDirection();
            BlockPos anchor = SlabbedRigService.defaultMegaAnchor(player);
            ctx.assertTrue(anchor.equals(feet.relative(facing, 3))
                            && world.getBlockState(anchor).isAir(),
                    "the player's mega anchor must begin in a clean air layer; feet="
                            + feet.toShortString()
                            + " player=" + player.blockPosition().toShortString()
                            + " facing=" + facing
                            + " anchor=" + anchor.toShortString()
                            + " state=" + world.getBlockState(anchor));
            AtomicInteger opened = new AtomicInteger();
            AtomicInteger closed = new AtomicInteger();
            List<SlabbedDiagnosticsBridge.ActionOriginContext> origins = new ArrayList<>();
            previous = SlabbedDiagnosticsBridge.install(
                    new SlabbedDiagnosticsBridge.Provider() {
                        @Override
                        public SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(
                                String origin,
                                SlabbedDiagnosticsBridge.ActionOriginContext context) {
                            if (SlabbedDiagnosticsBridge.AUTO_USEON_PROXY.equals(origin)) {
                                origins.add(context);
                                opened.incrementAndGet();
                                return closed::incrementAndGet;
                            }
                            return () -> { };
                        }
                    });

            assertCommandTree(ctx, world, player);
            CommandSourceStack allowed = player.createCommandSourceStack()
                    .withLevel(world)
                    .withPermission(2);
            ctx.assertTrue(run(world, allowed, "slabrig mega") > 0,
                    "the bare /slabrig mega command must execute the complete default board");

            SlabbedRigService.RigStatus status = SlabbedRigService.status(world);
            ctx.assertTrue(status.active() && status.clearEligible(),
                    "a fresh mega board must be active, exact, and clear-eligible; active="
                            + status.active()
                            + " intact=" + status.intactCells() + "/" + status.ownedCells()
                            + " conflicts=" + status.conflicts().stream()
                                    .limit(12)
                                    .map(pos -> pos.toShortString()
                                            + "=" + world.getBlockState(pos))
                                    .toList());
            RigManifest manifest = status.manifest();
            ctx.assertTrue(manifest.mode().equals("mega")
                            && manifest.anchor().equals(anchor)
                            && manifest.caseIds().equals(fullCoverage.caseIds()),
                    "mega must bind its exact anchor and all 160 deterministic category/row cases");
            long ownedDoorCells = manifest.ownedCells().stream()
                    .filter(cell -> cell.role() == RigManifest.CellRole.SUBJECT)
                    .filter(cell -> cell.caseId().endsWith("minecraft.oak_door"))
                    .count();
            ctx.assertTrue(ownedDoorCells >= 2,
                    "mega must own every changed cell of a successful multi-cell door placement");
            sentinel = anchor.relative(facing.getOpposite(), 20)
                    .relative(facing.getClockWise(), 5)
                    .immutable();
            ctx.assertTrue(world.getBlockState(sentinel).isAir(),
                    "the unrelated clear sentinel must begin outside the Mega footprint");
            world.setBlock(sentinel, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            RigManifest.MegaReport report =
                    (RigManifest.MegaReport) manifest.structuralReport();
            ctx.assertTrue(report.columns() == 40
                            && report.attempts() == 160
                            && report.placed() + report.refused() == 160
                            && report.complete(),
                    "mega must account for all 160 proxy attempts and verify all four support seats");
            long[] expectedDy = {
                    Double.doubleToRawLongBits(0.0d),
                    Double.doubleToRawLongBits(-0.5d),
                    Double.doubleToRawLongBits(-1.0d),
                    Double.doubleToRawLongBits(-0.5d)
            };
            for (int row = 0; row < expectedDy.length; row++) {
                RigManifest.MegaSeatReadback seat = report.sampleSeats().get(row);
                ctx.assertTrue(seat.row() == row
                                && seat.expectedDyBits() == expectedDy[row]
                                && seat.liveDyBits() == expectedDy[row],
                        "mega row " + row + " must read back the advertised support dy exactly");
            }
            ctx.assertTrue(manifest.receipt().fixtureDirectWrites() > 15
                            && manifest.receipt().fixtureTruthWrites() == 80
                            && manifest.receipt().subjectUseOnCalls() == 160
                            && manifest.receipt().subjectDirectStateWrites() == 0
                            && manifest.receipt().resolutions().size() == 160,
                    "mega must separate declared scenery from all 160 real useOn subject attempts");
            ctx.assertTrue(opened.get() == 160 && closed.get() == 160 && origins.size() == 160,
                    "every mega subject must open and close one AUTO_USEON_PROXY scope");
            Set<BlockPos> expectedTargets = new LinkedHashSet<>();
            Direction right = facing.getClockWise();
            for (int column = 0; column < 40; column++) {
                for (int row = 0; row < 4; row++) {
                    BlockPos ground = anchor.relative(facing, row)
                            .relative(right, column * 2);
                    BlockPos seat = switch (row) {
                        case 0 -> ground.above();
                        case 1, 3 -> ground.above(2);
                        default -> ground.above(4).relative(facing.getCounterClockWise());
                    };
                    expectedTargets.add(seat.above());
                }
            }
            ctx.assertTrue(expectedTargets.size() == 160
                            && origins.stream().allMatch(origin ->
                            origin.playerUuid().equals(player.getUUID().toString())
                                    && origin.dimensionId().equals(
                                            world.dimension().location().toString())
                                    && expectedTargets.contains(origin.placementPos())),
                    "mega proxy scopes must bind the exact player, dimension, and intended cell");
            assertInventory(ctx, inventoryBefore, player,
                    "mega must restore every player inventory slot after its proxy sweep");

            List<RigManifest.OwnedCell> owned = manifest.ownedCells();
            ctx.assertTrue(run(world, allowed, "slabrig status") > 0,
                    "/slabrig status must report the active mega manifest");
            BlockPos compound = anchor.relative(facing, 2).above(4);
            SlabAnchorAttachment.removeCompoundFullBlockAnchor(world, compound);
            List<BlockSnapshot> beforeRefusedClear = snapshotBlocks(world, owned);
            SlabbedRigService.RigStatus changedTruth = SlabbedRigService.status(world);
            ctx.assertTrue(changedTruth.conflicts().contains(compound)
                            && !changedTruth.clearEligible(),
                    "mega status must detect changed compound marker truth, not only block state");
            ctx.assertTrue(run(world, allowed, "slabrig clear") == 0
                            && beforeRefusedClear.equals(snapshotBlocks(world, owned)),
                    "normal mega clear must refuse changed owned truth without removing cells");
            ctx.assertTrue(run(world, allowed, "slabrig clear force") > 0,
                    "explicit force clear must remove only the exact mega manifest after a conflict");
            assertAllAirAndUnstored(ctx, world, owned);
            ctx.assertTrue(world.getBlockState(sentinel).is(Blocks.GOLD_BLOCK)
                            && !SlabbedRigService.status(world).active(),
                    "mega clear must preserve unrelated blocks and release ownership");
        } finally {
            SlabbedRigService.clear(world, true);
            if (sentinel != null) {
                world.destroyBlock(sentinel, false);
            }
            restoreInventory(player, inventoryBefore);
            if (previous != null) {
                SlabbedDiagnosticsBridge.install(previous);
            }
        }
        ctx.succeed();
    }

    @GameTest(template = "empty")
    public void rowsRigUsesScopedRealUseOnAndClearsOnlyOwnedCells(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(world);
        List<ItemStack> inventoryBefore = snapshotInventory(player);
        SlabbedDiagnosticsBridge.Provider previous = null;
        BlockPos sentinel = null;
        BlockPos outsideSupport = null;
        BlockPos outsideSubject = null;
        BlockPos replacementBlocker = null;

        try {
            player.moveTo(
                    ctx.absolutePos(new BlockPos(4, 3, 2)).getX() + 0.5d,
                    ctx.absolutePos(new BlockPos(4, 3, 2)).getY(),
                    ctx.absolutePos(new BlockPos(4, 3, 2)).getZ() + 0.5d,
                    0.0f,
                    0.0f);
            BlockPos anchor = SlabbedRigService.defaultAnchor(player);
            sentinel = anchor.offset(1, 0, 1);
            outsideSupport = anchor.offset(0, 0, 3);
            outsideSubject = outsideSupport.above();
            world.setBlock(sentinel, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

            AtomicInteger opened = new AtomicInteger();
            AtomicInteger closed = new AtomicInteger();
            List<SlabbedDiagnosticsBridge.ActionOriginContext> origins = new ArrayList<>();
            SlabbedDiagnosticsBridge.Provider trackingProvider = new SlabbedDiagnosticsBridge.Provider() {
                @Override
                public SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(
                        String origin,
                        SlabbedDiagnosticsBridge.ActionOriginContext context) {
                    if (SlabbedDiagnosticsBridge.AUTO_USEON_PROXY.equals(origin)) {
                        origins.add(context);
                        opened.incrementAndGet();
                        return closed::incrementAndGet;
                    }
                    return () -> { };
                }
            };
            previous = SlabbedDiagnosticsBridge.install(trackingProvider);

            assertCommandTree(ctx, world, player);
            CommandSourceStack allowed = player.createCommandSourceStack()
                    .withLevel(world)
                    .withPermission(2);
            ctx.assertTrue(run(world, allowed, "slabrig catalog") > 0,
                    "/slabrig catalog must execute for an operator");
            ctx.assertTrue(run(world, allowed, "slabrig status") > 0,
                    "/slabrig status must honestly report no active rig before build");
            ctx.assertTrue(run(world, allowed, "slabrig rows") > 0,
                    "/slabrig rows must build the first deterministic rig");

            SlabbedRigService.RigStatus firstStatus = SlabbedRigService.status(world);
            ctx.assertTrue(firstStatus.active() && firstStatus.clearEligible(),
                    "a fresh rows rig must be active, exact, and clear-eligible");
            RigManifest first = firstStatus.manifest();
            assertRowsManifest(ctx, world, player, anchor, first, origins, opened, closed);
            assertInventory(ctx, inventoryBefore, player,
                    "the auto-placement proxy must restore every player inventory slot");

            // Named replacement-ordering RED: every destination refusal must happen before the
            // old owned rig is cleared. These checks directly exercise buildRowsAt(force=true),
            // not just command text or a helper that could accidentally run in a different order.
            List<BlockSnapshot> oldRigBeforeReplacement = snapshotBlocks(world, first.ownedCells());

            BlockPos unloadedAnchor = anchor.offset(4096, 0, 0);
            BlockPos unloadedDestination = SlabbedRigService.rowsPlan(
                            unloadedAnchor, Direction.SOUTH)
                    .get(0).fixtures().get(0).pos();
            ctx.assertTrue(!world.hasChunkAt(unloadedDestination),
                    "fixture precondition: guarded replacement destination must be unloaded");
            SlabbedRigService.BuildResult unloadedReplacement = SlabbedRigService.buildRowsAt(
                    world, player, unloadedAnchor, Direction.SOUTH, true);
            ctx.assertTrue(unloadedReplacement.outcome() == SlabbedRigService.BuildOutcome.UNLOADED,
                    "force replacement must refuse an unloaded destination");
            ctx.assertTrue(SlabbedRigService.status(world).manifest().equals(first)
                            && oldRigBeforeReplacement.equals(snapshotBlocks(world, first.ownedCells())),
                    "an unloaded replacement refusal must preserve the old rig exactly");

            BlockPos outOfBoundsAnchor = new BlockPos(
                    anchor.getX(), world.getMaxBuildHeight(), anchor.getZ());
            BlockPos outOfBoundsDestination = SlabbedRigService.rowsPlan(
                            outOfBoundsAnchor, Direction.SOUTH)
                    .get(0).fixtures().get(0).pos();
            ctx.assertTrue(!world.isInWorldBounds(outOfBoundsDestination),
                    "fixture precondition: guarded replacement destination must be out of bounds");
            SlabbedRigService.BuildResult outOfBoundsReplacement = SlabbedRigService.buildRowsAt(
                    world, player, outOfBoundsAnchor, Direction.SOUTH, true);
            ctx.assertTrue(
                    outOfBoundsReplacement.outcome() == SlabbedRigService.BuildOutcome.OUT_OF_BOUNDS,
                    "force replacement must refuse an out-of-bounds destination");
            ctx.assertTrue(SlabbedRigService.status(world).manifest().equals(first)
                            && oldRigBeforeReplacement.equals(snapshotBlocks(world, first.ownedCells())),
                    "an out-of-bounds replacement refusal must preserve the old rig exactly");

            BlockPos blockedAnchor = anchor.offset(0, 0, 8);
            replacementBlocker = SlabbedRigService.rowsPlan(blockedAnchor, Direction.SOUTH)
                    .get(0).fixtures().get(0).pos();
            ctx.assertTrue(world.hasChunkAt(replacementBlocker),
                    "fixture precondition: guarded replacement destination must be loaded");
            world.setBlock(replacementBlocker, Blocks.EMERALD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            SlabbedRigService.BuildResult refusedReplacement = SlabbedRigService.buildRowsAt(
                    world, player, blockedAnchor, Direction.SOUTH, true);
            ctx.assertTrue(refusedReplacement.outcome() == SlabbedRigService.BuildOutcome.OCCUPIED,
                    "force replacement must refuse a foreign destination");
            ctx.assertTrue(SlabbedRigService.status(world).manifest().equals(first)
                            && oldRigBeforeReplacement.equals(snapshotBlocks(world, first.ownedCells())),
                    "a refused replacement must preserve the old manifest and every old owned cell");
            ctx.assertTrue(world.getBlockState(replacementBlocker).is(Blocks.EMERALD_BLOCK),
                    "a refused replacement must preserve the foreign blocker");
            world.destroyBlock(replacementBlocker, false);
            replacementBlocker = null;

            int scopesAfterRig = opened.get();
            world.setBlock(outsideSupport, Blocks.OAK_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
            ItemStack originalHand = player.getMainHandItem().copy();
            try {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.STONE));
                BlockHitResult outsideHit = new BlockHitResult(
                        Vec3.atCenterOf(outsideSupport).add(0.0d, 0.5d, 0.0d),
                        Direction.UP,
                        outsideSupport,
                        false);
                InteractionResult outsideResult = ForgeHooks.onPlaceItemIntoWorld(
                        new UseOnContext(player, InteractionHand.MAIN_HAND, outsideHit));
                ctx.assertTrue(outsideResult.consumesAction()
                                && world.getBlockState(outsideSubject).is(Blocks.STONE),
                        "fixture: the ordinary out-of-scope fake-player action must place");
            } finally {
                player.setItemInHand(InteractionHand.MAIN_HAND, originalHand);
            }
            ctx.assertTrue(opened.get() == scopesAfterRig,
                    "an ordinary action outside the rig must not inherit a proxy scope");

            ctx.assertTrue(run(world, allowed, "slabrig clear") > 0,
                    "normal clear must remove an intact rig");
            assertAllAirAndUnstored(ctx, world, first.ownedCells());
            ctx.assertTrue(world.getBlockState(sentinel).is(Blocks.GOLD_BLOCK)
                            && world.getBlockState(outsideSupport).is(Blocks.OAK_SLAB)
                            && world.getBlockState(outsideSubject).is(Blocks.STONE),
                    "clear must preserve every unrelated cell, including nearby placed blocks");
            ctx.assertTrue(!SlabbedRigService.status(world).active(),
                    "successful clear must release the manifest");

            ctx.assertTrue(run(world, allowed, "slabrig rows") > 0,
                    "rows must rebuild deterministically after exact clear");
            RigManifest second = SlabbedRigService.status(world).manifest();
            assertDeterministic(ctx, first, second);

            RigManifest.OwnedCell changed = second.ownedCells().stream()
                    .filter(cell -> cell.role() == RigManifest.CellRole.SUBJECT)
                    .findFirst()
                    .orElseThrow();
            world.setBlock(changed.pos(), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            List<BlockSnapshot> beforeRefusedClear = snapshotBlocks(world, second.ownedCells());
            ctx.assertTrue(run(world, allowed, "slabrig clear") == 0,
                    "normal clear must refuse an owned cell changed by someone else");
            ctx.assertTrue(beforeRefusedClear.equals(snapshotBlocks(world, second.ownedCells())),
                    "a conflict must make normal clear all-or-nothing");
            ctx.assertTrue(world.getBlockState(sentinel).is(Blocks.GOLD_BLOCK),
                    "a refused clear must preserve the unrelated sentinel");
            SlabbedRigService.RigStatus conflicted = SlabbedRigService.status(world);
            ctx.assertTrue(conflicted.active() && !conflicted.clearEligible()
                            && conflicted.conflicts().equals(List.of(changed.pos())),
                    "status must expose the exact conflicting owned cell");

            ctx.assertTrue(run(world, allowed, "slabrig clear force") > 0,
                    "explicit force clear must remove only the manifest-owned cells");
            assertAllAirAndUnstored(ctx, world, second.ownedCells());
            ctx.assertTrue(world.getBlockState(sentinel).is(Blocks.GOLD_BLOCK)
                            && world.getBlockState(outsideSubject).is(Blocks.STONE),
                    "force clear must still preserve cells outside the exact manifest");
            ctx.assertTrue(!SlabbedRigService.status(world).active(),
                    "force clear must release a fully removed manifest");

            // A real useOn may mutate its target and only then fail a service postcondition.
            // Sabotage the first stored fact from the observer callback to prove the attempted
            // cell was ledgered before mutation and cannot become an unowned leftover.
            List<RigCase> failedPlan = SlabbedRigService.rowsPlan(anchor, player.getDirection());
            BlockPos sabotagedTarget = failedPlan.get(0).subjects().get(0).aim().vanillaTarget();
            AtomicBoolean sabotaged = new AtomicBoolean();
            SlabbedDiagnosticsBridge.Provider failureProvider = new SlabbedDiagnosticsBridge.Provider() {
                @Override
                public boolean recorderEnabled() {
                    return true;
                }

                @Override
                public void recordAction(LinkedHashMap<String, String> fields) {
                    if (sabotagedTarget.toShortString().equals(fields.get("placementPos"))
                            && sabotaged.compareAndSet(false, true)) {
                        SlabAnchorAttachment.clearPlacementTruth(world, sabotagedTarget);
                    }
                }
            };
            SlabbedDiagnosticsBridge.install(failureProvider);
            SlabbedRigService.BuildResult failedBuild;
            try {
                failedBuild = SlabbedRigService.buildRowsAt(
                        world, player, anchor, player.getDirection(), false);
            } finally {
                SlabbedDiagnosticsBridge.install(trackingProvider);
            }
            ctx.assertTrue(sabotaged.get(),
                    "fixture must force a post-mutation stored-dy validation failure");
            ctx.assertTrue(failedBuild.outcome() == SlabbedRigService.BuildOutcome.PLACEMENT_FAILED
                            && failedBuild.manifest() == null,
                    "a fully rolled-back post-mutation failure must report no owned residue");
            ctx.assertTrue(!SlabbedRigService.status(world).active(),
                    "a clean failed build must not publish an active manifest");
            assertPlanAirAndUnstored(ctx, world, failedPlan);
            assertInventory(ctx, inventoryBefore, player,
                    "a failed proxy placement must still restore every inventory slot");
        } finally {
            SlabbedRigService.clear(world, true);
            if (sentinel != null) {
                world.destroyBlock(sentinel, false);
            }
            if (outsideSubject != null) {
                world.destroyBlock(outsideSubject, false);
                SlabAnchorAttachment.clearPlacementTruth(world, outsideSubject);
            }
            if (outsideSupport != null) {
                world.destroyBlock(outsideSupport, false);
            }
            if (replacementBlocker != null) {
                world.destroyBlock(replacementBlocker, false);
            }
            restoreInventory(player, inventoryBefore);
            if (previous != null) {
                SlabbedDiagnosticsBridge.install(previous);
            }
        }
        ctx.succeed();
    }

    @GameTest(template = "empty")
    public void bareTowerMatchesCompoundFixtureAndFailedRowsReplacementRestores(
            GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(world);
        List<ItemStack> inventoryBefore = snapshotInventory(player);
        SlabbedDiagnosticsBridge.Provider previous = null;
        BlockPos sentinel = null;

        try {
            player.moveTo(
                    ctx.absolutePos(new BlockPos(4, 3, 2)).getX() + 0.5d,
                    ctx.absolutePos(new BlockPos(4, 3, 2)).getY(),
                    ctx.absolutePos(new BlockPos(4, 3, 2)).getZ() + 0.5d,
                    0.0f,
                    0.0f);
            BlockPos anchor = SlabbedRigService.defaultAnchor(player);
            sentinel = anchor.offset(1, 0, 1);
            world.setBlock(sentinel, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

            AtomicInteger successOpened = new AtomicInteger();
            AtomicInteger successClosed = new AtomicInteger();
            List<SlabbedDiagnosticsBridge.ActionOriginContext> successOrigins = new ArrayList<>();
            SlabbedDiagnosticsBridge.Provider trackingProvider = new SlabbedDiagnosticsBridge.Provider() {
                @Override
                public SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(
                        String origin,
                        SlabbedDiagnosticsBridge.ActionOriginContext context) {
                    if (SlabbedDiagnosticsBridge.AUTO_USEON_PROXY.equals(origin)) {
                        successOrigins.add(context);
                        successOpened.incrementAndGet();
                        return successClosed::incrementAndGet;
                    }
                    return () -> { };
                }
            };
            previous = SlabbedDiagnosticsBridge.install(trackingProvider);

            assertCommandTree(ctx, world, player);
            CommandSourceStack allowed = player.createCommandSourceStack()
                    .withLevel(world)
                    .withPermission(2);
            Direction facing = player.getDirection();
            List<RigCase> towerPlan = SlabbedRigService.towerPlan(anchor, facing);
            assertTowerPlan(ctx, anchor, facing, towerPlan);

            SlabbedRigService.BuildResult towerBuild =
                    SlabbedRigService.buildTower(world, player, false);
            ctx.assertTrue(towerBuild.success(),
                    "bare tower service refused: outcome=" + towerBuild.outcome()
                            + " detail=" + towerBuild.detail()
                            + " conflicts=" + towerBuild.conflicts());
            RigManifest tower = SlabbedRigService.status(world).manifest();
            assertTowerManifest(
                    ctx, world, player, anchor, facing, tower,
                    successOrigins, successOpened, successClosed);
            assertInventory(ctx, inventoryBefore, player,
                    "fixture-only tower must preserve every inventory slot");

            BlockPos compound = anchor.above(4);
            BlockPos side = compound.relative(facing.getCounterClockWise());
            SlabAnchorAttachment.removeCompoundFullBlockAnchor(world, compound);
            SlabbedRigService.RigStatus markerConflict = SlabbedRigService.status(world);
            ctx.assertTrue(!markerConflict.clearEligible()
                            && markerConflict.conflicts().contains(compound),
                    "tower status must treat missing declared compound truth as a conflict");
            ctx.assertTrue(run(world, allowed, "slabrig clear") == 0,
                    "normal clear must refuse a tower whose declared marker truth changed");
            SlabAnchorAttachment.addCompoundFullBlockAnchor(
                    world, compound, world.getBlockState(compound));
            ctx.assertTrue(SlabbedRigService.status(world).clearEligible(),
                    "restoring the declared compound marker must restore exact status");
            ctx.assertTrue(run(world, allowed, "slabrig clear") > 0,
                    "normal clear must remove an intact tower");
            assertAllAirAndUnstored(ctx, world, tower.ownedCells());
            ctx.assertTrue(!SlabAnchorAttachment.isAnchored(world, compound)
                            && !SlabAnchorAttachment.isCompoundFullBlockAnchor(world, compound)
                            && !SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(
                                    world, side, Blocks.STONE_SLAB.defaultBlockState()
                                            .setValue(SlabBlock.TYPE, SlabType.BOTTOM)),
                    "tower clear must remove the declared compound marker truth");
            ctx.assertTrue(world.getBlockState(sentinel).is(Blocks.GOLD_BLOCK),
                    "tower clear must preserve the unrelated sentinel");

            ctx.assertTrue(run(world, allowed, "slabrig tower") > 0,
                    "/slabrig tower must execute the same canonical compound fixture plan");
            RigManifest commandTower = SlabbedRigService.status(world).manifest();
            assertTowerManifest(
                    ctx, world, player, anchor, facing, commandTower,
                    successOrigins, successOpened, successClosed);
            assertDeterministic(ctx, tower, commandTower);
            ctx.assertTrue(run(world, allowed, "slabrig clear") > 0,
                    "the command-built canonical tower must remain exactly clearable");
            assertAllAirAndUnstored(ctx, world, commandTower.ownedCells());

            successOrigins.clear();
            successOpened.set(0);
            successClosed.set(0);
            ctx.assertTrue(run(world, allowed, "slabrig rows") > 0,
                    "fixture: rows must establish the previous rig for replacement rollback");
            RigManifest previousRows = SlabbedRigService.status(world).manifest();
            List<BlockSnapshot> previousRowsSnapshot =
                    snapshotBlocks(world, previousRows.ownedCells());

            BlockPos replacementAnchor = anchor.offset(0, 0, 8);
            List<RigCase> replacementRowsPlan =
                    SlabbedRigService.rowsPlan(replacementAnchor, facing);
            List<BlockPos> replacementTargets = replacementRowsPlan.stream()
                    .flatMap(rigCase -> rigCase.subjects().stream())
                    .map(subject -> subject.aim().vanillaTarget())
                    .toList();
            AtomicInteger failureOpened = new AtomicInteger();
            AtomicInteger failureClosed = new AtomicInteger();
            AtomicInteger replacementActionsBeforeFailure = new AtomicInteger();
            AtomicBoolean thirdReplacementSabotaged = new AtomicBoolean();
            List<SlabbedDiagnosticsBridge.ActionOriginContext> failureOrigins = new ArrayList<>();
            SlabbedDiagnosticsBridge.Provider failureProvider = new SlabbedDiagnosticsBridge.Provider() {
                @Override
                public boolean recorderEnabled() {
                    return true;
                }

                @Override
                public SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(
                        String origin,
                        SlabbedDiagnosticsBridge.ActionOriginContext context) {
                    if (SlabbedDiagnosticsBridge.AUTO_USEON_PROXY.equals(origin)) {
                        failureOrigins.add(context);
                        failureOpened.incrementAndGet();
                        return failureClosed::incrementAndGet;
                    }
                    return () -> { };
                }

                @Override
                public void recordAction(LinkedHashMap<String, String> fields) {
                    if (thirdReplacementSabotaged.get()) {
                        return;
                    }
                    String placement = fields.get("placementPos");
                    for (int index = 0; index < replacementTargets.size(); index++) {
                        if (replacementTargets.get(index).toShortString().equals(placement)) {
                            replacementActionsBeforeFailure.incrementAndGet();
                            if (index == 2
                                    && thirdReplacementSabotaged.compareAndSet(false, true)) {
                                SlabAnchorAttachment.clearPlacementTruth(
                                        world, replacementTargets.get(index));
                            }
                            return;
                        }
                    }
                }
            };

            SlabbedDiagnosticsBridge.install(failureProvider);
            SlabbedRigService.BuildResult failedReplacement;
            try {
                failedReplacement = SlabbedRigService.buildRowsAt(
                        world, player, replacementAnchor, facing, true);
            } finally {
                SlabbedDiagnosticsBridge.install(trackingProvider);
            }

            ctx.assertTrue(thirdReplacementSabotaged.get()
                            && replacementActionsBeforeFailure.get() == 3,
                    "fixture must fail only after all three replacement rows actions mutated");
            ctx.assertTrue(failedReplacement.outcome()
                            == SlabbedRigService.BuildOutcome.PLACEMENT_FAILED
                            && failedReplacement.manifest().equals(previousRows),
                    "failed rows replacement must report the exact restored previous manifest");
            ctx.assertTrue(SlabbedRigService.status(world).manifest().equals(previousRows)
                            && previousRowsSnapshot.equals(
                                    snapshotBlocks(world, previousRows.ownedCells())),
                    "failed rows replacement must restore every previous rows cell and fact");

            Set<BlockPos> previousPositions = previousRows.ownedCells().stream()
                    .map(RigManifest.OwnedCell::pos)
                    .collect(java.util.stream.Collectors.toSet());
            for (RigCase rigCase : replacementRowsPlan) {
                for (RigCase.FixtureCell fixture : rigCase.fixtures()) {
                    if (!previousPositions.contains(fixture.pos())) {
                        assertAirAndUnstored(ctx, world, fixture.pos());
                    }
                }
                for (RigCase.SubjectPlacement subject : rigCase.subjects()) {
                    BlockPos target = subject.aim().vanillaTarget();
                    if (!previousPositions.contains(target)) {
                        assertAirAndUnstored(ctx, world, target);
                    }
                }
            }

            List<BlockPos> previousTargets = previousRows.ownedCells().stream()
                    .filter(cell -> cell.role() == RigManifest.CellRole.SUBJECT)
                    .map(RigManifest.OwnedCell::pos)
                    .toList();
            List<BlockPos> scopedTargets = failureOrigins.stream()
                    .map(SlabbedDiagnosticsBridge.ActionOriginContext::placementPos)
                    .toList();
            ctx.assertTrue(failureOpened.get() == 6 && failureClosed.get() == 6
                            && scopedTargets.equals(List.of(
                                    replacementTargets.get(0), replacementTargets.get(1),
                                    replacementTargets.get(2),
                                    previousTargets.get(0), previousTargets.get(1), previousTargets.get(2))),
                    "failed replacement must close three new rows scopes and replay three old rows scopes"
                            + " in exact target order");
            ctx.assertTrue(world.getBlockState(sentinel).is(Blocks.GOLD_BLOCK),
                    "failed replacement and restore must preserve unrelated cells");
            assertInventory(ctx, inventoryBefore, player,
                    "failed replacement and restore must preserve every inventory slot");

            // If both the replacement and the exact prior-plan replay fail but each rollback is
            // clean, the service must say restore failed with no active ownership. Calling that
            // state rollback residue would be a false safety claim.
            AtomicBoolean cleanReplacementSabotaged = new AtomicBoolean();
            AtomicBoolean cleanRestoreSabotaged = new AtomicBoolean();
            AtomicInteger cleanFailureOpened = new AtomicInteger();
            AtomicInteger cleanFailureClosed = new AtomicInteger();
            SlabbedDiagnosticsBridge.Provider cleanRestoreFailureProvider =
                    new SlabbedDiagnosticsBridge.Provider() {
                        @Override
                        public boolean recorderEnabled() {
                            return true;
                        }

                        @Override
                        public SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(
                                String origin,
                                SlabbedDiagnosticsBridge.ActionOriginContext context) {
                            if (SlabbedDiagnosticsBridge.AUTO_USEON_PROXY.equals(origin)) {
                                cleanFailureOpened.incrementAndGet();
                                return cleanFailureClosed::incrementAndGet;
                            }
                            return () -> { };
                        }

                        @Override
                        public void recordAction(LinkedHashMap<String, String> fields) {
                            String placement = fields.get("placementPos");
                            if (!cleanReplacementSabotaged.get()
                                    && replacementTargets.get(2).toShortString().equals(placement)
                                    && cleanReplacementSabotaged.compareAndSet(false, true)) {
                                SlabAnchorAttachment.clearPlacementTruth(
                                        world, replacementTargets.get(2));
                                return;
                            }
                            if (cleanReplacementSabotaged.get()
                                    && previousTargets.get(0).toShortString().equals(placement)
                                    && cleanRestoreSabotaged.compareAndSet(false, true)) {
                                SlabAnchorAttachment.clearPlacementTruth(world, previousTargets.get(0));
                            }
                        }
                    };

            SlabbedDiagnosticsBridge.install(cleanRestoreFailureProvider);
            SlabbedRigService.BuildResult cleanRestoreFailure;
            try {
                cleanRestoreFailure = SlabbedRigService.buildRowsAt(
                        world, player, replacementAnchor, facing, true);
            } finally {
                SlabbedDiagnosticsBridge.install(trackingProvider);
            }
            ctx.assertTrue(cleanReplacementSabotaged.get() && cleanRestoreSabotaged.get(),
                    "fixture must independently fail the replacement and the prior-plan replay");
            ctx.assertTrue(cleanRestoreFailure.outcome()
                            == SlabbedRigService.BuildOutcome.RESTORE_FAILED
                            && cleanRestoreFailure.manifest() == null
                            && cleanRestoreFailure.conflicts().isEmpty(),
                    "a clean replay failure must report no rollback residue or active manifest");
            ctx.assertTrue(cleanFailureOpened.get() == 4 && cleanFailureClosed.get() == 4,
                    "clean restore failure must close three replacement scopes and one replay scope");
            ctx.assertTrue(!SlabbedRigService.status(world).active(),
                    "a clean replay failure must leave no active rig");
            assertPlanAirAndUnstored(ctx, world, replacementRowsPlan);
            assertPlanAirAndUnstored(
                    ctx, world, SlabbedRigService.rowsPlan(anchor, player.getDirection()));
            ctx.assertTrue(world.getBlockState(sentinel).is(Blocks.GOLD_BLOCK),
                    "clean replacement and replay failure must preserve unrelated cells");
            assertInventory(ctx, inventoryBefore, player,
                    "clean replacement and replay failure must preserve every inventory slot");
        } finally {
            SlabbedRigService.clear(world, true);
            if (sentinel != null) {
                world.destroyBlock(sentinel, false);
            }
            restoreInventory(player, inventoryBefore);
            if (previous != null) {
                SlabbedDiagnosticsBridge.install(previous);
            }
        }
        ctx.succeed();
    }

    @GameTest(template = "empty")
    public void stacksCatalogPagesOutcomesAndExactOwnership(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(world);
        List<ItemStack> inventoryBefore = snapshotInventory(player);
        SlabbedDiagnosticsBridge.Provider previous = null;
        BlockPos reservedSentinel = null;

        try {
            player.moveTo(
                    ctx.absolutePos(new BlockPos(7, 3, 0)).getX() + 0.5d,
                    ctx.absolutePos(new BlockPos(7, 3, 0)).getY(),
                    ctx.absolutePos(new BlockPos(7, 3, 0)).getZ() + 0.5d,
                    0.0f,
                    0.0f);
            Direction facing = player.getDirection();
            Direction right = facing.getClockWise();
            BlockPos anchor = SlabbedRigService.defaultStacksAnchor(player);
            SlabbedRigService.StackPlan plan =
                    SlabbedRigService.stacksPlan(anchor, facing, 3, 1);

            List<String> expectedRecipes = List.of(
                    "S", "B",
                    "SS", "SB", "BS", "BB",
                    "SSS", "SSB", "SBS", "SBB", "BSS", "BSB", "BBS", "BBB");
            ctx.assertTrue(plan.maxLength() == 3
                            && plan.page() == 1
                            && plan.totalPages() == 1
                            && plan.totalRecipes() == 14
                            && plan.stacks().stream()
                                    .map(SlabbedRigService.NumericTowerColumn::recipe)
                                    .toList().equals(expectedRecipes)
                            && plan.caseIds().stream().allMatch(id -> id.startsWith("stacks.")),
                    "stacks plan must enumerate every non-empty S/B word length-major, S first");
            for (int index = 0; index < plan.stacks().size(); index++) {
                SlabbedRigService.NumericTowerColumn stack = plan.stacks().get(index);
                BlockPos expectedBase = anchor
                        .relative(facing, (index / SlabbedRigService.STACK_GRID_SIZE) * 2)
                        .relative(right, (index % SlabbedRigService.STACK_GRID_SIZE) * 2);
                ctx.assertTrue(stack.base().equals(expectedBase)
                                && stack.seat().equals(expectedBase.above(3))
                                && stack.fixtures().size() == 4
                                && stack.fixtures().get(0).state().is(Blocks.STONE)
                                && stack.fixtures().get(1).state().is(Blocks.STONE_SLAB)
                                && stack.fixtures().get(1).state().getValue(SlabBlock.TYPE)
                                        == SlabType.BOTTOM
                                && stack.fixtures().get(2).state().is(Blocks.STONE)
                                && stack.fixtures().get(3).state().is(Blocks.STONE_SLAB)
                                && stack.fixtures().get(3).state().getValue(SlabBlock.TYPE)
                                        == SlabType.BOTTOM,
                        "stack " + index + " must use the exact row-major standard lowered seed");
            }
            ctx.assertTrue(new LinkedHashSet<>(plan.footprint())
                            .equals(expectedStackFootprint(plan)),
                    "stacks must reserve exactly every seed, useOn effect cell, and two-cell headroom");
            SlabbedRigService.StackPlan secondPage =
                    SlabbedRigService.stacksPlan(anchor, facing, 5, 2);
            SlabbedRigService.StackPlan thirdPage =
                    SlabbedRigService.stacksPlan(anchor, facing, 5, 3);
            SlabbedRigService.StackPlan lastPage =
                    SlabbedRigService.stacksPlan(anchor, facing, 5, 4);
            ctx.assertTrue(secondPage.stacks().size() == 16
                            && secondPage.stacks().get(0).recipe().equals("SSBS")
                            && secondPage.stacks().get(15).recipe().equals("SSSSB")
                            && thirdPage.stacks().size() == 16
                            && thirdPage.stacks().get(0).recipe().equals("SSSBS")
                            && thirdPage.stacks().get(15).recipe().equals("BSSSB")
                            && lastPage.totalRecipes() == 62
                            && lastPage.totalPages() == 4
                            && lastPage.stacks().size() == 14
                            && lastPage.stacks().get(0).recipe().equals("BSSBS")
                            && lastPage.stacks().get(13).recipe().equals("BBBBB"),
                    "length-five catalog must partition into stable 16/16/16/14 pages");

            assertCommandTree(ctx, world, player);
            AtomicInteger opened = new AtomicInteger();
            AtomicInteger closed = new AtomicInteger();
            List<SlabbedDiagnosticsBridge.ActionOriginContext> origins = new ArrayList<>();
            SlabbedDiagnosticsBridge.Provider trackingProvider =
                    new SlabbedDiagnosticsBridge.Provider() {
                        @Override
                        public SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(
                                String origin,
                                SlabbedDiagnosticsBridge.ActionOriginContext context) {
                            if (SlabbedDiagnosticsBridge.AUTO_USEON_PROXY.equals(origin)) {
                                origins.add(context);
                                opened.incrementAndGet();
                                return closed::incrementAndGet;
                            }
                            return () -> { };
                        }
                    };
            previous = SlabbedDiagnosticsBridge.install(trackingProvider);

            SlabbedRigService.BuildResult built = SlabbedRigService.buildStacksAt(
                    world, player, anchor, facing, 3, 1, false);
            ctx.assertTrue(built.success(),
                    "stacks service refused: " + built.outcome() + " " + built.detail());
            RigManifest first = built.manifest();
            RigManifest.StackPageReport firstReport =
                    (RigManifest.StackPageReport) first.structuralReport();
            int expectedAttempts = expectedRecipes.stream().mapToInt(String::length).sum();
            List<RigManifest.SeamFinding> independentlyReadSeams =
                    expectedStackSeams(firstReport);
            ctx.assertTrue(first.mode().equals("stacks")
                            && first.caseIds().equals(plan.caseIds())
                            && firstReport.maxLength() == 3
                            && firstReport.page() == 1
                            && firstReport.totalPages() == 1
                            && firstReport.totalRecipes() == 14
                            && firstReport.stacks().size() == 14
                            && firstReport.seams().equals(independentlyReadSeams)
                            && !firstReport.complete()
                            && !firstReport.seams().isEmpty()
                            && first.receipt().fixtureDirectWrites() == 56
                            && first.receipt().subjectUseOnCalls() == expectedAttempts
                            && first.receipt().subjectDirectStateWrites() == 0
                            && first.receipt().resolutions().stream()
                                    .allMatch(LandingResolution.Place.class::isInstance)
                            && opened.get() == expectedAttempts
                            && closed.get() == expectedAttempts
                            && origins.size() == expectedAttempts,
                    "stacks must own exact page truth and keep every actual seam RED visible");

            Map<BlockPos, RigManifest.OwnedCell> ownedByPos = new LinkedHashMap<>();
            for (RigManifest.OwnedCell cell : first.ownedCells()) {
                ctx.assertTrue(ownedByPos.put(cell.pos(), cell) == null,
                        "stack ownership must not contain duplicate positions");
            }
            Set<BlockPos> expectedOwned = expectedStackOwnedPositions(plan);
            ctx.assertTrue(ownedByPos.keySet().equals(expectedOwned),
                    "stack manifest must own exactly every seed and successfully placed recipe cell");
            Set<BlockPos> expectedSubjects = new LinkedHashSet<>();
            for (int stackIndex = 0; stackIndex < plan.stacks().size(); stackIndex++) {
                SlabbedRigService.NumericTowerColumn planned = plan.stacks().get(stackIndex);
                RigManifest.StackEntryReport reported = firstReport.stacks().get(stackIndex);
                ctx.assertTrue(reported.catalogIndex() == planned.index()
                                && reported.recipe().equals(planned.recipe())
                                && reported.column().seat().equals(planned.seat())
                                && reported.column().cells().get(0).liveDyBits()
                                        == Double.doubleToRawLongBits(-0.5d),
                        "every stack report must bind its catalog recipe to a genuinely lowered seed");
                for (RigCase.FixtureCell fixture : planned.fixtures()) {
                    RigManifest.OwnedCell ownedFixture = ownedByPos.get(fixture.pos());
                    ctx.assertTrue(ownedFixture != null
                                    && ownedFixture.role() == RigManifest.CellRole.FIXTURE
                                    && ownedFixture.expectedState().equals(fixture.state())
                                    && !ownedFixture.expectedStoredDy().present()
                                    && world.getBlockState(fixture.pos()).equals(fixture.state()),
                            "every declared stack seed cell must be exact, direct-written fixture truth");
                }
                int slabOrdinal = 0;
                for (int step = 0; step < planned.recipe().length(); step++) {
                    char token = planned.recipe().charAt(step);
                    Block expectedBlock = token == 'B'
                            ? Blocks.STONE
                            : ((slabOrdinal++ & 1) == 0
                                    ? Blocks.SMOOTH_STONE_SLAB
                                    : Blocks.STONE_SLAB);
                    BlockPos subjectPos = planned.seat().above(step + 1);
                    expectedSubjects.add(subjectPos);
                    RigManifest.OwnedCell ownedSubject = ownedByPos.get(subjectPos);
                    ctx.assertTrue(ownedSubject != null
                                    && ownedSubject.role() == RigManifest.CellRole.SUBJECT
                                    && ownedSubject.expectedState().is(expectedBlock)
                                    && ownedSubject.expectedStoredDy().present()
                                    && world.getBlockState(subjectPos)
                                            .equals(ownedSubject.expectedState())
                                    && SlabAnchorAttachment.storedPlacementDyFact(world, subjectPos)
                                            .equals(ownedSubject.expectedStoredDy()),
                            "every successful stack recipe cell must own exact live state and stored truth");
                }

                Set<BlockPos> stackCells = reported.column().cells().stream()
                        .map(RigManifest.TowerCellReadback::pos)
                        .collect(java.util.stream.Collectors.toSet());
                boolean hasSeam = independentlyReadSeams.stream().anyMatch(seam ->
                        stackCells.contains(seam.lowerPos())
                                && stackCells.contains(seam.upperPos()));
                boolean expectedComplete = reported.column().builtCells()
                                == reported.recipe().length()
                        && !reported.column().stalled()
                        && !hasSeam;
                String entrySummary =
                        SlabbedOperatorCommands.stackEntrySummary(firstReport, reported);
                ctx.assertTrue(entrySummary.contains(
                                        "structural="
                                                + (expectedComplete ? "complete" : "incomplete"))
                                && !entrySummary.contains("structural=built"),
                        "each emitted stack row must grade actual seams instead of calling them built");
            }
            for (SlabbedDiagnosticsBridge.ActionOriginContext origin : origins) {
                ctx.assertTrue(origin.playerUuid().equals(player.getUUID().toString())
                                && origin.dimensionId()
                                        .equals(world.dimension().location().toString())
                                && expectedSubjects.contains(origin.placementPos()),
                        "every stacks proxy scope must bind the exact player, dimension, and landing cell");
            }
            String pageSummary = SlabbedOperatorCommands.stackPageSummary(first, firstReport);
            ctx.assertTrue(pageSummary.startsWith("Slabbed stacks result:")
                            && pageSummary.contains("structural=incomplete")
                            && !pageSummary.contains("Slabbed stacks built"),
                    "an incomplete stack page must be reported as a result, never a successful build claim");

            RigManifest residualProjection = first.withResidualOwnedCells(
                    List.of(first.ownedCells().get(0)));
            ctx.assertTrue(residualProjection.ownedCells().size() == 1
                            && residualProjection.structuralReport()
                                    instanceof RigManifest.ResidueStructuralReport
                            && !residualProjection.structuralReport().complete(),
                    "a partial stack clear must retain an honest residue manifest instead of throwing");
            RigManifest dishonestResidue = new RigManifest(
                    residualProjection.runId(),
                    residualProjection.ownerUuid(),
                    residualProjection.dimensionId(),
                    residualProjection.anchor(),
                    residualProjection.mode(),
                    residualProjection.caseIds(),
                    residualProjection.ownedCells(),
                    residualProjection.receipt(),
                    RigManifest.StructuralReport.none());
            boolean rejectedDishonestResidue = false;
            try {
                new SlabbedRigService.BuildResult(
                        SlabbedRigService.BuildOutcome.ROLLBACK_RESIDUE,
                        dishonestResidue,
                        List.of(dishonestResidue.ownedCells().get(0).pos()),
                        "contract discriminator");
            } catch (IllegalArgumentException expected) {
                rejectedDishonestResidue = expected.getMessage().contains("honest residual");
            }
            SlabbedRigService.BuildResult honestResidue = new SlabbedRigService.BuildResult(
                    SlabbedRigService.BuildOutcome.ROLLBACK_RESIDUE,
                    residualProjection,
                    List.of(residualProjection.ownedCells().get(0).pos()),
                    "contract discriminator");
            ctx.assertTrue(rejectedDishonestResidue
                            && honestResidue.manifest().structuralReport()
                                    instanceof RigManifest.ResidueStructuralReport
                            && !honestResidue.manifest().structuralReport().complete(),
                    "rollback-residue results must reject complete-looking structural reports");
            assertInventory(ctx, inventoryBefore, player,
                    "stacks proxy actions must restore every inventory slot");

            List<BlockSnapshot> beforeInvalid = snapshotBlocks(world, first.ownedCells());
            int scopesBeforeInvalid = opened.get();
            SlabbedRigService.BuildResult invalidPage = SlabbedRigService.buildStacksAt(
                    world, player, anchor, facing, 1, 2, true);
            ctx.assertTrue(invalidPage.outcome()
                                    == SlabbedRigService.BuildOutcome.INVALID_REQUEST
                            && invalidPage.manifest().equals(first)
                            && SlabbedRigService.status(world).manifest().equals(first)
                            && beforeInvalid.equals(snapshotBlocks(world, first.ownedCells()))
                            && opened.get() == scopesBeforeInvalid
                            && closed.get() == scopesBeforeInvalid,
                    "a syntactically valid impossible page must refuse without mutation or scopes");

            Set<BlockPos> owned = first.ownedCells().stream()
                    .map(RigManifest.OwnedCell::pos)
                    .collect(java.util.stream.Collectors.toSet());
            reservedSentinel = plan.footprint().stream()
                    .filter(pos -> !owned.contains(pos))
                    .findFirst()
                    .orElseThrow();
            world.setBlock(
                    reservedSentinel, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            List<BlockSnapshot> beforeRefusal = snapshotBlocks(world, first.ownedCells());
            int scopesBeforeRefusal = opened.get();
            SlabbedRigService.BuildResult refused = SlabbedRigService.buildStacksAt(
                    world, player, anchor, facing, 3, 1, true);
            ctx.assertTrue(refused.outcome() == SlabbedRigService.BuildOutcome.OCCUPIED
                            && refused.manifest().equals(first)
                            && beforeRefusal.equals(snapshotBlocks(world, first.ownedCells()))
                            && world.getBlockState(reservedSentinel).is(Blocks.GOLD_BLOCK)
                            && opened.get() == scopesBeforeRefusal
                            && closed.get() == scopesBeforeRefusal,
                    "force stacks replacement must guard prior reserved-but-unowned cells");
            ctx.assertTrue(SlabbedRigService.clear(world, true).success()
                            && world.getBlockState(reservedSentinel).is(Blocks.GOLD_BLOCK),
                    "force clear must remove exact stack ownership and preserve foreign reserved cells");
            assertAllAirAndUnstored(ctx, world, first.ownedCells());
            world.destroyBlock(reservedSentinel, false);
            reservedSentinel = null;

            CommandSourceStack allowed = player.createCommandSourceStack()
                    .withLevel(world)
                    .withPermission(2);
            ctx.assertTrue(run(world, allowed, "slabrig stacks") > 0,
                    "the real default stacks command must execute max-length 5 page 1");
            RigManifest defaultCommandManifest = SlabbedRigService.status(world).manifest();
            RigManifest.StackPageReport defaultCommandReport =
                    (RigManifest.StackPageReport) defaultCommandManifest.structuralReport();
            ctx.assertTrue(defaultCommandManifest.mode().equals("stacks")
                            && defaultCommandReport.maxLength() == 5
                            && defaultCommandReport.page() == 1
                            && defaultCommandReport.totalPages() == 4
                            && defaultCommandReport.totalRecipes() == 62
                            && defaultCommandReport.stacks().size() == 16
                            && SlabbedOperatorCommands.stackPageSummary(
                                            defaultCommandManifest, defaultCommandReport)
                                    .contains("page=1/4"),
                    "default command must publish the exact first catalog page and honest output");
            List<RigManifest.OwnedCell> defaultOwned = defaultCommandManifest.ownedCells();
            ctx.assertTrue(SlabbedRigService.clear(world, true).success(),
                    "default command-built stacks must remain exactly clearable");
            assertAllAirAndUnstored(ctx, world, defaultOwned);

            ctx.assertTrue(run(world, allowed, "slabrig stacks 5 4") > 0,
                    "the real stacks command must execute the final partial catalog page");
            RigManifest finalCommandManifest = SlabbedRigService.status(world).manifest();
            RigManifest.StackPageReport finalCommandReport =
                    (RigManifest.StackPageReport) finalCommandManifest.structuralReport();
            ctx.assertTrue(finalCommandReport.maxLength() == 5
                            && finalCommandReport.page() == 4
                            && finalCommandReport.stacks().size() == 14
                            && finalCommandReport.stacks().get(0).recipe().equals("BSSBS")
                            && finalCommandReport.stacks().get(13).recipe().equals("BBBBB")
                            && SlabbedOperatorCommands.stackPageSummary(
                                            finalCommandManifest, finalCommandReport)
                                    .contains("recipes=14/62"),
                    "final command page must retain exact membership, size, and honest output");
            List<RigManifest.OwnedCell> finalOwned = finalCommandManifest.ownedCells();
            ctx.assertTrue(SlabbedRigService.clear(world, true).success(),
                    "final command-built stacks must remain exactly clearable");
            assertAllAirAndUnstored(ctx, world, finalOwned);

            // Prove both non-advanced actual-world outcomes are inherited honestly by stacks.
            SlabbedRigService.StackPlan outcomePlan =
                    SlabbedRigService.stacksPlan(anchor, facing, 1, 1);
            BlockPos preserveCursor = outcomePlan.stacks().get(0).seat();
            BlockPos preserveTarget = preserveCursor.above();
            BlockPos rejectTarget = outcomePlan.stacks().get(1).seat().above();
            AtomicBoolean preserved = new AtomicBoolean();
            AtomicBoolean rejected = new AtomicBoolean();
            SlabbedDiagnosticsBridge.Provider outcomeProvider =
                    new SlabbedDiagnosticsBridge.Provider() {
                        @Override
                        public boolean recorderEnabled() {
                            return true;
                        }

                        @Override
                        public void recordAction(LinkedHashMap<String, String> fields) {
                            if (preserveTarget.toShortString().equals(fields.get("placementPos"))
                                    && preserved.compareAndSet(false, true)) {
                                world.destroyBlock(preserveTarget, false);
                                SlabAnchorAttachment.clearPlacementTruth(world, preserveTarget);
                                world.setBlock(
                                        preserveCursor,
                                        Blocks.STONE_SLAB.defaultBlockState()
                                                .setValue(SlabBlock.TYPE, SlabType.DOUBLE),
                                        Block.UPDATE_ALL);
                                SlabAnchorAttachment.clearPlacementTruth(world, preserveCursor);
                            } else if (rejectTarget.toShortString().equals(fields.get("placementPos"))
                                    && rejected.compareAndSet(false, true)) {
                                world.destroyBlock(rejectTarget, false);
                                SlabAnchorAttachment.clearPlacementTruth(world, rejectTarget);
                            }
                        }
                    };
            SlabbedDiagnosticsBridge.install(outcomeProvider);
            SlabbedRigService.BuildResult outcomeBuild;
            try {
                outcomeBuild = SlabbedRigService.buildStacksAt(
                        world, player, anchor, facing, 1, 1, false);
            } finally {
                SlabbedDiagnosticsBridge.install(trackingProvider);
            }
            RigManifest.StackPageReport outcomeReport =
                    (RigManifest.StackPageReport) outcomeBuild.manifest().structuralReport();
            ctx.assertTrue(outcomeBuild.success()
                            && preserved.get()
                            && rejected.get()
                            && outcomeReport.stacks().get(0).column().attempts() == 1
                            && outcomeReport.stacks().get(0).column().builtCells() == 0
                            && !outcomeReport.stacks().get(0).column().stalled()
                            && outcomeReport.stacks().get(1).column().stalled()
                            && outcomeBuild.manifest().receipt().resolutions().get(0).equals(
                                    new LandingResolution.PreserveVanilla(
                                            "stacks_in_cell_change"))
                            && outcomeBuild.manifest().receipt().resolutions().get(1)
                                    instanceof LandingResolution.Reject
                            && outcomeBuild.manifest().ownedCells().stream()
                                    .noneMatch(cell -> cell.role()
                                            == RigManifest.CellRole.SUBJECT)
                            && world.getBlockState(preserveTarget).isAir()
                            && world.getBlockState(rejectTarget).isAir(),
                    "stacks must grade cursor-only preserve and no-mutation reject separately");
            assertInventory(ctx, inventoryBefore, player,
                    "non-advanced stack outcomes must restore every inventory slot");
            ctx.assertTrue(SlabbedRigService.clear(world, true).success(),
                    "partial stacks outcomes must remain exactly clearable");

            // A failed forced replacement must replay this new plan type through real useOn.
            SlabbedDiagnosticsBridge.install(trackingProvider);
            SlabbedRigService.BuildResult replayBase = SlabbedRigService.buildStacksAt(
                    world, player, anchor, facing, 1, 1, false);
            ctx.assertTrue(replayBase.success(), "stacks replay base must build");
            RigManifest previousStacks = replayBase.manifest();
            List<BlockSnapshot> previousSnapshot =
                    snapshotBlocks(world, previousStacks.ownedCells());
            List<RigCase> rowsPlan = SlabbedRigService.rowsPlan(anchor, facing);
            List<BlockPos> rowsTargets = rowsPlan.stream()
                    .flatMap(rigCase -> rigCase.subjects().stream())
                    .map(subject -> subject.aim().vanillaTarget())
                    .toList();
            AtomicBoolean replacementSabotaged = new AtomicBoolean();
            AtomicInteger replayOpened = new AtomicInteger();
            AtomicInteger replayClosed = new AtomicInteger();
            SlabbedDiagnosticsBridge.Provider replayProvider =
                    new SlabbedDiagnosticsBridge.Provider() {
                        @Override
                        public boolean recorderEnabled() {
                            return true;
                        }

                        @Override
                        public SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(
                                String origin,
                                SlabbedDiagnosticsBridge.ActionOriginContext context) {
                            if (SlabbedDiagnosticsBridge.AUTO_USEON_PROXY.equals(origin)) {
                                replayOpened.incrementAndGet();
                                return replayClosed::incrementAndGet;
                            }
                            return () -> { };
                        }

                        @Override
                        public void recordAction(LinkedHashMap<String, String> fields) {
                            if (rowsTargets.get(2).toShortString().equals(fields.get("placementPos"))
                                    && replacementSabotaged.compareAndSet(false, true)) {
                                SlabAnchorAttachment.clearPlacementTruth(world, rowsTargets.get(2));
                            }
                        }
                    };
            SlabbedDiagnosticsBridge.install(replayProvider);
            SlabbedRigService.BuildResult replayResult;
            try {
                replayResult = SlabbedRigService.buildRowsAt(
                        world, player, anchor, facing, true);
            } finally {
                SlabbedDiagnosticsBridge.install(trackingProvider);
            }
            ctx.assertTrue(replacementSabotaged.get()
                            && replayResult.outcome()
                                    == SlabbedRigService.BuildOutcome.PLACEMENT_FAILED
                            && replayResult.manifest().equals(previousStacks)
                            && SlabbedRigService.status(world).manifest().equals(previousStacks)
                            && previousSnapshot.equals(
                                    snapshotBlocks(world, previousStacks.ownedCells()))
                            && replayOpened.get()
                                    == 3 + previousStacks.receipt().subjectUseOnCalls()
                            && replayClosed.get() == replayOpened.get(),
                    "failed replacement must replay exact stacks cells, receipt, and structure");
        } finally {
            SlabbedRigService.clear(world, true);
            if (reservedSentinel != null) {
                world.destroyBlock(reservedSentinel, false);
            }
            restoreInventory(player, inventoryBefore);
            if (previous != null) {
                SlabbedDiagnosticsBridge.install(previous);
            }
        }
        ctx.succeed();
    }

    @GameTest(template = "empty")
    public void numericTowerReportsGapsStallsAndExactReplay(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(world);
        List<ItemStack> inventoryBefore = snapshotInventory(player);
        SlabbedDiagnosticsBridge.Provider previous = null;
        Set<BlockPos> cleanup = new LinkedHashSet<>();
        BlockPos reservedSentinel = null;

        try {
            player.moveTo(
                    ctx.absolutePos(new BlockPos(4, 3, 2)).getX() + 0.5d,
                    ctx.absolutePos(new BlockPos(4, 3, 2)).getY(),
                    ctx.absolutePos(new BlockPos(4, 3, 2)).getZ() + 0.5d,
                    0.0f,
                    0.0f);
            Direction facing = player.getDirection();
            BlockPos anchor = SlabbedRigService.defaultNumericTowerAnchor(player);
            SlabbedRigService.NumericTowerPlan plan =
                    SlabbedRigService.numericTowerPlan(anchor, facing, 4, 4);
            cleanup.addAll(plan.footprint());
            assertNumericTowerPlan(ctx, anchor, facing, plan);
            assertCommandTree(ctx, world, player);

            AtomicInteger opened = new AtomicInteger();
            AtomicInteger closed = new AtomicInteger();
            List<SlabbedDiagnosticsBridge.ActionOriginContext> origins = new ArrayList<>();
            SlabbedDiagnosticsBridge.Provider trackingProvider =
                    new SlabbedDiagnosticsBridge.Provider() {
                        @Override
                        public SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(
                                String origin,
                                SlabbedDiagnosticsBridge.ActionOriginContext context) {
                            if (SlabbedDiagnosticsBridge.AUTO_USEON_PROXY.equals(origin)) {
                                origins.add(context);
                                opened.incrementAndGet();
                                return closed::incrementAndGet;
                            }
                            return () -> { };
                        }
                    };
            previous = SlabbedDiagnosticsBridge.install(trackingProvider);

            SlabbedRigService.BuildResult built = SlabbedRigService.buildNumericTowerAt(
                    world, player, anchor, facing, 4, 4, false);
            ctx.assertTrue(built.success(),
                    "numeric tower service refused: " + built.outcome() + " " + built.detail());
            RigManifest first = built.manifest();
            assertNumericTowerManifest(
                    ctx, world, player, plan, first, origins, opened, closed);
            assertInventory(ctx, inventoryBefore, player,
                    "numeric tower must restore every inventory slot");

            Set<BlockPos> owned = first.ownedCells().stream()
                    .map(RigManifest.OwnedCell::pos)
                    .collect(java.util.stream.Collectors.toSet());
            List<RigCase> guardedRowsPlan = SlabbedRigService.rowsPlan(anchor, facing);
            Set<BlockPos> guardedRowsFootprint = guardedRowsPlan.stream()
                    .flatMap(rigCase -> java.util.stream.Stream.concat(
                            rigCase.fixtures().stream().map(RigCase.FixtureCell::pos),
                            rigCase.subjects().stream()
                                    .map(subject -> subject.aim().vanillaTarget())))
                    .collect(java.util.stream.Collectors.toSet());
            reservedSentinel = plan.footprint().stream()
                    .filter(pos -> !owned.contains(pos))
                    .filter(pos -> !guardedRowsFootprint.contains(pos))
                    .findFirst()
                    .orElseThrow();
            world.setBlock(
                    reservedSentinel, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            SlabbedRigService.RigStatus reservedConflict = SlabbedRigService.status(world);
            ctx.assertTrue(!reservedConflict.clearEligible()
                            && reservedConflict.conflicts().contains(reservedSentinel),
                    "status must expose a foreign cell inside the reserved diagnostic footprint");
            ctx.assertTrue(!SlabbedRigService.clear(world, false).success(),
                    "normal clear must refuse a changed reserved diagnostic cell");
            List<BlockSnapshot> beforeReservedReplacement =
                    snapshotBlocks(world, first.ownedCells());
            int scopesBeforeReservedReplacement = opened.get();
            SlabbedRigService.BuildResult reservedReplacement =
                    SlabbedRigService.buildRowsAt(world, player, anchor, facing, true);
            ctx.assertTrue(reservedReplacement.outcome()
                            == SlabbedRigService.BuildOutcome.OCCUPIED
                            && first.equals(reservedReplacement.manifest())
                            && reservedReplacement.conflicts().contains(reservedSentinel)
                            && first.equals(SlabbedRigService.status(world).manifest())
                            && beforeReservedReplacement.equals(
                                    snapshotBlocks(world, first.ownedCells()))
                            && world.getBlockState(reservedSentinel).is(Blocks.GOLD_BLOCK)
                            && opened.get() == scopesBeforeReservedReplacement
                            && closed.get() == scopesBeforeReservedReplacement,
                    "force replacement must refuse before clear when the previous replay footprint"
                            + " contains a foreign reserved cell");
            ctx.assertTrue(SlabbedRigService.clear(world, true).success()
                            && world.getBlockState(reservedSentinel).is(Blocks.GOLD_BLOCK),
                    "force clear must remove only owned cells and preserve a foreign reserved cell");
            world.destroyBlock(reservedSentinel, false);
            reservedSentinel = null;

            // The real command route keeps bare tower distinct and supplies height 8 only when
            // numeric <n> omits its height. A forced numeric replacement may choose a new height.
            origins.clear();
            opened.set(0);
            closed.set(0);
            CommandSourceStack allowed = player.createCommandSourceStack()
                    .withLevel(world)
                    .withPermission(2);
            ctx.assertTrue(run(world, allowed, "slabrig tower 1") > 0,
                    "numeric tower command must execute the required-n/default-height form");
            RigManifest.NumericTowerReport defaultReport =
                    (RigManifest.NumericTowerReport) SlabbedRigService.status(world)
                            .manifest().structuralReport();
            ctx.assertTrue(defaultReport.requestedHeight()
                            == SlabbedRigService.DEFAULT_NUMERIC_TOWER_HEIGHT
                            && defaultReport.towers().size() == 1,
                    "tower <n> must use the exact default height without changing bare tower");
            ctx.assertTrue(run(world, allowed, "slabrig tower 1 2 force") > 0,
                    "numeric tower command must execute explicit-height force replacement");
            RigManifest.NumericTowerReport explicitReport =
                    (RigManifest.NumericTowerReport) SlabbedRigService.status(world)
                            .manifest().structuralReport();
            ctx.assertTrue(explicitReport.requestedHeight() == 2
                            && explicitReport.towers().size() == 1,
                    "numeric force replacement must retain the explicit requested height");
            cleanup.addAll(SlabbedRigService.numericTowerPlan(
                    SlabbedRigService.defaultNumericTowerAnchor(player), facing, 1, 8).footprint());
            ctx.assertTrue(SlabbedRigService.clear(world, true).success(),
                    "command-built numeric tower must remain exactly clearable");

            // A provider removes one just-landed target before useOn returns. The observed world
            // is unchanged, so this is an honest stall: no phantom subject ownership, no rollback.
            BlockPos stallAnchor = anchor;
            SlabbedRigService.NumericTowerPlan stallPlan =
                    SlabbedRigService.numericTowerPlan(stallAnchor, facing, 3, 3);
            cleanup.addAll(stallPlan.footprint());
            BlockPos stalledTarget = stallPlan.towers().get(1).seat().above();
            AtomicBoolean stalledOnce = new AtomicBoolean();
            AtomicInteger stallOpened = new AtomicInteger();
            AtomicInteger stallClosed = new AtomicInteger();
            SlabbedDiagnosticsBridge.Provider stallProvider =
                    new SlabbedDiagnosticsBridge.Provider() {
                        @Override
                        public boolean recorderEnabled() {
                            return true;
                        }

                        @Override
                        public SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(
                                String origin,
                                SlabbedDiagnosticsBridge.ActionOriginContext context) {
                            if (SlabbedDiagnosticsBridge.AUTO_USEON_PROXY.equals(origin)) {
                                stallOpened.incrementAndGet();
                                return stallClosed::incrementAndGet;
                            }
                            return () -> { };
                        }

                        @Override
                        public void recordAction(LinkedHashMap<String, String> fields) {
                            if (stalledTarget.toShortString().equals(fields.get("placementPos"))
                                    && stalledOnce.compareAndSet(false, true)) {
                                world.destroyBlock(stalledTarget, false);
                                SlabAnchorAttachment.clearPlacementTruth(world, stalledTarget);
                            }
                        }
                    };
            SlabbedDiagnosticsBridge.install(stallProvider);
            SlabbedRigService.BuildResult stalledBuild;
            try {
                stalledBuild = SlabbedRigService.buildNumericTowerAt(
                        world, player, stallAnchor, facing, 3, 3, false);
            } finally {
                SlabbedDiagnosticsBridge.install(trackingProvider);
            }
            ctx.assertTrue(stalledBuild.success() && stalledOnce.get(),
                    "an observed no-cell action must remain a successful diagnostic stall");
            RigManifest stalledManifest = stalledBuild.manifest();
            RigManifest.NumericTowerReport stalledReport =
                    (RigManifest.NumericTowerReport) stalledManifest.structuralReport();
            RigManifest.TowerColumnReport stalledColumn = stalledReport.towers().get(1);
            ctx.assertTrue(stalledColumn.stalled()
                            && stalledColumn.attempts() == 1
                            && stalledColumn.builtCells() == 0
                            && !stalledReport.complete(),
                    "the sabotaged column must report one attempt, zero built cells, and a stall");
            ctx.assertTrue(stalledManifest.receipt().subjectUseOnCalls() == 7
                            && stalledManifest.receipt().resolutions().size() == 7
                            && stalledManifest.receipt().resolutions().stream()
                                    .filter(LandingResolution.Reject.class::isInstance)
                                    .count() == 1
                            && stallOpened.get() == 7
                            && stallClosed.get() == 7,
                    "stall evidence must count every real proxy action and exactly one rejection");
            ctx.assertTrue(stalledManifest.ownedCells().stream()
                            .noneMatch(cell -> cell.pos().equals(stalledTarget))
                            && world.getBlockState(stalledTarget).isAir()
                            && !SlabAnchorAttachment.storedPlacementDyFact(
                                    world, stalledTarget).present(),
                    "a stalled target must remain unowned, air, and without stored truth");
            assertInventory(ctx, inventoryBefore, player,
                    "stalled numeric tower must restore every inventory slot");
            ctx.assertTrue(SlabbedRigService.clear(world, true).success(),
                    "a partial diagnostic tower must remain exactly clearable");

            // Force a cursor-only mutation after real useOn. This is the third outcome grammar
            // arm: preserve the vanilla in-cell result, update only the already-owned cursor, and
            // never fabricate ownership or stored truth for the unchanged target.
            SlabbedRigService.NumericTowerPlan inCellPlan =
                    SlabbedRigService.numericTowerPlan(anchor, facing, 1, 1);
            cleanup.addAll(inCellPlan.footprint());
            BlockPos inCellCursor = inCellPlan.towers().get(0).seat();
            BlockPos inCellTarget = inCellCursor.above();
            AtomicBoolean inCellTriggered = new AtomicBoolean();
            AtomicBoolean inCellMutationApplied = new AtomicBoolean();
            AtomicInteger inCellOpened = new AtomicInteger();
            AtomicInteger inCellClosed = new AtomicInteger();
            SlabbedDiagnosticsBridge.Provider inCellProvider =
                    new SlabbedDiagnosticsBridge.Provider() {
                        @Override
                        public boolean recorderEnabled() {
                            return true;
                        }

                        @Override
                        public SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(
                                String origin,
                                SlabbedDiagnosticsBridge.ActionOriginContext context) {
                            if (SlabbedDiagnosticsBridge.AUTO_USEON_PROXY.equals(origin)) {
                                inCellOpened.incrementAndGet();
                                return inCellClosed::incrementAndGet;
                            }
                            return () -> { };
                        }

                        @Override
                        public void recordAction(LinkedHashMap<String, String> fields) {
                            if (inCellTarget.toShortString().equals(fields.get("placementPos"))
                                    && inCellTriggered.compareAndSet(false, true)) {
                                world.destroyBlock(inCellTarget, false);
                                SlabAnchorAttachment.clearPlacementTruth(world, inCellTarget);
                                inCellMutationApplied.set(world.setBlock(
                                        inCellCursor,
                                        Blocks.STONE_SLAB.defaultBlockState()
                                                .setValue(SlabBlock.TYPE, SlabType.DOUBLE),
                                        Block.UPDATE_ALL));
                                SlabAnchorAttachment.clearPlacementTruth(world, inCellCursor);
                            }
                        }
                    };
            SlabbedDiagnosticsBridge.install(inCellProvider);
            SlabbedRigService.BuildResult inCellBuild;
            try {
                inCellBuild = SlabbedRigService.buildNumericTowerAt(
                        world, player, anchor, facing, 1, 1, false);
            } finally {
                SlabbedDiagnosticsBridge.install(trackingProvider);
            }
            ctx.assertTrue(inCellBuild.success(),
                    "cursor-only numeric fixture refused: outcome=" + inCellBuild.outcome()
                            + " detail=" + inCellBuild.detail());
            RigManifest inCellManifest = inCellBuild.manifest();
            RigManifest.NumericTowerReport inCellReport =
                    (RigManifest.NumericTowerReport) inCellManifest.structuralReport();
            RigManifest.TowerColumnReport inCellColumn = inCellReport.towers().get(0);
            RigManifest.OwnedCell preservedCursor = inCellManifest.ownedCells().stream()
                    .filter(cell -> cell.pos().equals(inCellCursor))
                    .findFirst()
                    .orElseThrow();
            ctx.assertTrue(inCellBuild.success()
                            && inCellTriggered.get()
                            && inCellMutationApplied.get()
                            && inCellColumn.attempts() == 1
                            && inCellColumn.builtCells() == 0
                            && !inCellColumn.stalled()
                            && !inCellReport.complete()
                            && inCellReport.seams().isEmpty()
                            && inCellManifest.receipt().subjectUseOnCalls() == 1
                            && inCellManifest.receipt().resolutions().equals(List.of(
                                    new LandingResolution.PreserveVanilla(
                                            "numeric_tower_in_cell_change")))
                            && inCellManifest.ownedCells().stream()
                                    .noneMatch(cell -> cell.role()
                                            == RigManifest.CellRole.SUBJECT)
                            && preservedCursor.expectedState().is(Blocks.STONE_SLAB)
                            && preservedCursor.expectedState().getValue(SlabBlock.TYPE)
                                    == SlabType.DOUBLE
                            && !preservedCursor.expectedStoredDy().present()
                            && world.getBlockState(inCellTarget).isAir()
                            && !SlabAnchorAttachment.storedPlacementDyFact(
                                    world, inCellTarget).present()
                            && inCellOpened.get() == 1
                            && inCellClosed.get() == 1,
                    "cursor-only mutation must publish PreserveVanilla without phantom target"
                            + " ownership or truth");
            assertInventory(ctx, inventoryBefore, player,
                    "in-cell numeric outcome must restore every inventory slot");
            ctx.assertTrue(SlabbedRigService.clear(world, true).success(),
                    "an in-cell diagnostic result must remain exactly clearable");

            // Build one complete numeric diagnostic, fail a forced rows replacement after its
            // third real action, and prove replay reproduces cells, receipt, and structural RED.
            BlockPos replayAnchor = anchor;
            SlabbedRigService.NumericTowerPlan replayPlan =
                    SlabbedRigService.numericTowerPlan(replayAnchor, facing, 2, 3);
            cleanup.addAll(replayPlan.footprint());
            SlabbedRigService.BuildResult replayBase = SlabbedRigService.buildNumericTowerAt(
                    world, player, replayAnchor, facing, 2, 3, false);
            ctx.assertTrue(replayBase.success(), "numeric replay fixture must build");
            RigManifest previousNumeric = replayBase.manifest();
            List<BlockSnapshot> previousSnapshot =
                    snapshotBlocks(world, previousNumeric.ownedCells());

            BlockPos rowsAnchor = replayAnchor;
            List<RigCase> rowsPlan = SlabbedRigService.rowsPlan(rowsAnchor, facing);
            cleanup.addAll(rowsPlan.stream()
                    .flatMap(rigCase -> java.util.stream.Stream.concat(
                            rigCase.fixtures().stream().map(RigCase.FixtureCell::pos),
                            rigCase.subjects().stream()
                                    .map(subject -> subject.aim().vanillaTarget())))
                    .toList());
            List<BlockPos> rowsTargets = rowsPlan.stream()
                    .flatMap(rigCase -> rigCase.subjects().stream())
                    .map(subject -> subject.aim().vanillaTarget())
                    .toList();
            AtomicBoolean replacementSabotaged = new AtomicBoolean();
            AtomicInteger replayOpened = new AtomicInteger();
            AtomicInteger replayClosed = new AtomicInteger();
            SlabbedDiagnosticsBridge.Provider replayProvider =
                    new SlabbedDiagnosticsBridge.Provider() {
                        @Override
                        public boolean recorderEnabled() {
                            return true;
                        }

                        @Override
                        public SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(
                                String origin,
                                SlabbedDiagnosticsBridge.ActionOriginContext context) {
                            if (SlabbedDiagnosticsBridge.AUTO_USEON_PROXY.equals(origin)) {
                                replayOpened.incrementAndGet();
                                return replayClosed::incrementAndGet;
                            }
                            return () -> { };
                        }

                        @Override
                        public void recordAction(LinkedHashMap<String, String> fields) {
                            if (rowsTargets.get(2).toShortString().equals(fields.get("placementPos"))
                                    && replacementSabotaged.compareAndSet(false, true)) {
                                SlabAnchorAttachment.clearPlacementTruth(
                                        world, rowsTargets.get(2));
                            }
                        }
                    };
            SlabbedDiagnosticsBridge.install(replayProvider);
            SlabbedRigService.BuildResult replayResult;
            try {
                replayResult = SlabbedRigService.buildRowsAt(
                        world, player, rowsAnchor, facing, true);
            } finally {
                SlabbedDiagnosticsBridge.install(trackingProvider);
            }
            ctx.assertTrue(replacementSabotaged.get()
                            && replayResult.outcome()
                                    == SlabbedRigService.BuildOutcome.PLACEMENT_FAILED
                            && replayResult.manifest().equals(previousNumeric)
                            && SlabbedRigService.status(world).manifest().equals(previousNumeric)
                            && previousSnapshot.equals(
                                    snapshotBlocks(world, previousNumeric.ownedCells())),
                    "failed replacement must replay the exact previous numeric manifest: outcome="
                            + replayResult.outcome()
                            + " detail=" + replayResult.detail()
                            + " resultManifest="
                            + (replayResult.manifest() != null
                                    && replayResult.manifest().equals(previousNumeric))
                            + " activeManifest="
                            + (SlabbedRigService.status(world).manifest() != null
                                    && SlabbedRigService.status(world).manifest()
                                            .equals(previousNumeric))
                            + " blocks=" + previousSnapshot.equals(
                                    snapshotBlocks(world, previousNumeric.ownedCells())));
            int expectedReplayScopes = 3
                    + previousNumeric.receipt().subjectUseOnCalls();
            ctx.assertTrue(replayOpened.get() == expectedReplayScopes
                            && replayClosed.get() == expectedReplayScopes,
                    "failed rows replacement must close its scopes plus every numeric replay scope");
            assertInventory(ctx, inventoryBefore, player,
                    "numeric replacement replay must preserve every inventory slot");
        } finally {
            SlabbedRigService.clear(world, true);
            if (reservedSentinel != null) {
                world.destroyBlock(reservedSentinel, false);
            }
            for (BlockPos pos : cleanup) {
                if (world.hasChunkAt(pos)) {
                    world.destroyBlock(pos, false);
                    SlabAnchorAttachment.clearPlacementTruth(world, pos);
                }
            }
            restoreInventory(player, inventoryBefore);
            if (previous != null) {
                SlabbedDiagnosticsBridge.install(previous);
            }
        }
        ctx.succeed();
    }

    private static void assertNumericTowerPlan(
            GameTestHelper ctx,
            BlockPos anchor,
            Direction facing,
            SlabbedRigService.NumericTowerPlan plan) {
        ctx.assertTrue(plan.anchor().equals(anchor)
                        && plan.facing() == facing
                        && plan.count() == 4
                        && plan.height() == 4
                        && plan.towers().size() == 4
                        && plan.caseIds().equals(List.of(
                                "tower.numeric.1.sbsb",
                                "tower.numeric.2.ssbb",
                                "tower.numeric.3.bsbs",
                                "tower.numeric.4.ssss")),
                "numeric plan must bind exact anchor, facing, bounds, recipes, and case order");
        Direction right = facing.getClockWise();
        for (int index = 0; index < plan.towers().size(); index++) {
            SlabbedRigService.NumericTowerColumn tower = plan.towers().get(index);
            BlockPos expectedBase = anchor.relative(right, index * 2);
            ctx.assertTrue(tower.base().equals(expectedBase)
                            && tower.seat().equals(expectedBase.above(3))
                            && tower.fixtures().stream()
                                    .map(RigCase.FixtureCell::pos)
                                    .toList().equals(List.of(
                                            expectedBase,
                                            expectedBase.above(),
                                            expectedBase.above(2),
                                            expectedBase.above(3)))
                            && tower.fixtures().get(0).state().is(Blocks.STONE)
                            && tower.fixtures().get(1).state().is(Blocks.STONE_SLAB)
                            && tower.fixtures().get(2).state().is(Blocks.STONE)
                            && tower.fixtures().get(3).state().is(Blocks.STONE_SLAB)
                            && tower.fixtures().get(1).state().getValue(SlabBlock.TYPE)
                                    == SlabType.BOTTOM
                            && tower.fixtures().get(3).state().getValue(SlabBlock.TYPE)
                                    == SlabType.BOTTOM,
                    "numeric tower " + index + " must own the exact lowered four-cell base");
        }
        ctx.assertTrue(new LinkedHashSet<>(plan.footprint()).size() == plan.footprint().size()
                        && plan.footprint().contains(plan.towers().get(0).seat().above(6)),
                "numeric plan must reserve a unique structure/effect/headroom footprint");
    }

    private static void assertNumericTowerManifest(
            GameTestHelper ctx,
            ServerLevel world,
            ServerPlayer player,
            SlabbedRigService.NumericTowerPlan plan,
            RigManifest manifest,
            List<SlabbedDiagnosticsBridge.ActionOriginContext> origins,
            AtomicInteger opened,
            AtomicInteger closed) {
        ctx.assertTrue(manifest != null
                        && manifest.mode().equals("tower.numeric")
                        && manifest.anchor().equals(plan.anchor())
                        && manifest.ownerUuid().equals(player.getUUID().toString())
                        && manifest.dimensionId().equals(world.dimension().location().toString())
                        && manifest.caseIds().equals(plan.caseIds()),
                "numeric manifest must bind exact identity, mode, and column ordering");
        RigManifest.NumericTowerReport report =
                (RigManifest.NumericTowerReport) manifest.structuralReport();
        ctx.assertTrue(report.requestedHeight() == 4
                        && report.towers().size() == 4
                        && report.towers().stream().allMatch(tower ->
                                tower.attempts() == 4
                                        && tower.builtCells() == 4
                                        && !tower.stalled()
                                        && tower.cells().size() == 5)
                        && !report.complete()
                        && report.seams().equals(expectedNumericSeams(report))
                        && !report.seams().isEmpty(),
                "current deep law must remain an explicit structural GAP RED, not a false green");
        long fixtureCount = manifest.ownedCells().stream()
                .filter(cell -> cell.role() == RigManifest.CellRole.FIXTURE)
                .count();
        long subjectCount = manifest.ownedCells().stream()
                .filter(cell -> cell.role() == RigManifest.CellRole.SUBJECT)
                .count();
        ctx.assertTrue(fixtureCount == 16
                        && subjectCount == 16
                        && manifest.receipt().fixtureDirectWrites() == 16
                        && manifest.receipt().fixtureTruthWrites() == 0
                        && manifest.receipt().subjectUseOnCalls() == 16
                        && manifest.receipt().subjectDirectStateWrites() == 0
                        && manifest.receipt().resolutions().size() == 16
                        && manifest.receipt().resolutions().stream()
                                .allMatch(LandingResolution.Place.class::isInstance),
                "numeric execution must separate 16 fixture writes from 16 real useOn subjects");

        String[][] expected = {
                {"minecraft:stone_slab", "minecraft:smooth_stone_slab", "minecraft:stone",
                        "minecraft:stone_slab", "minecraft:stone"},
                {"minecraft:stone_slab", "minecraft:smooth_stone_slab", "minecraft:stone_slab",
                        "minecraft:stone", "minecraft:stone"},
                {"minecraft:stone_slab", "minecraft:stone", "minecraft:smooth_stone_slab",
                        "minecraft:stone", "minecraft:stone_slab"},
                {"minecraft:stone_slab", "minecraft:smooth_stone_slab", "minecraft:stone_slab",
                        "minecraft:smooth_stone_slab", "minecraft:stone_slab"}
        };
        for (int towerIndex = 0; towerIndex < report.towers().size(); towerIndex++) {
            List<RigManifest.TowerCellReadback> cells = report.towers().get(towerIndex).cells();
            for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
                RigManifest.TowerCellReadback cell = cells.get(cellIndex);
                String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(cell.state().getBlock()).toString();
                ctx.assertTrue(blockId.equals(expected[towerIndex][cellIndex])
                                && cell.liveDyBits() == Double.doubleToRawLongBits(
                                        SlabSupport.getYOffset(
                                                world, cell.pos(), world.getBlockState(cell.pos()))),
                        "numeric tower readback must preserve recipe and raw live-dy truth");
            }
        }
        ctx.assertTrue(opened.get() == 16 && closed.get() == 16 && origins.size() == 16,
                "every numeric subject must open and close one AUTO_USEON_PROXY scope");
        Set<BlockPos> placed = manifest.receipt().resolutions().stream()
                .map(LandingResolution.Place.class::cast)
                .map(LandingResolution.Place::targetPos)
                .collect(java.util.stream.Collectors.toSet());
        for (SlabbedDiagnosticsBridge.ActionOriginContext origin : origins) {
            ctx.assertTrue(origin.playerUuid().equals(player.getUUID().toString())
                            && origin.dimensionId().equals(world.dimension().location().toString())
                            && placed.contains(origin.placementPos()),
                    "numeric proxy scope must bind exact player, dimension, and resolved cell");
        }
    }

    private static void assertCommandTree(
            GameTestHelper ctx,
            ServerLevel world,
            ServerPlayer player) {
        CommandDispatcher<CommandSourceStack> dispatcher =
                world.getServer().getCommands().getDispatcher();
        CommandNode<CommandSourceStack> slabrig = dispatcher.getRoot().getChild("slabrig");
        ctx.assertTrue(slabrig != null, "/slabrig must be registered on the real server dispatcher");
        CommandSourceStack denied = player.createCommandSourceStack().withPermission(0);
        CommandSourceStack allowed = player.createCommandSourceStack().withPermission(2);
        ctx.assertTrue(!slabrig.canUse(denied) && slabrig.canUse(allowed),
                "/slabrig must reject permission level 0 and admit level 2");
        for (String child : List.of(
                "catalog", "status", "clear", "rows", "tower", "stacks", "mega")) {
            ctx.assertTrue(slabrig.getChild(child) != null,
                    "/slabrig must expose the implemented " + child + " arm");
        }
        ctx.assertTrue(slabrig.getChild("clear").getChild("force") != null
                        && slabrig.getChild("rows").getChild("force") != null
                        && slabrig.getChild("tower").getChild("force") != null,
                "clear and build must expose explicit guarded force arms");
        CommandNode<CommandSourceStack> tower = slabrig.getChild("tower");
        CommandNode<CommandSourceStack> n = tower.getChild("n");
        ctx.assertTrue(n instanceof ArgumentCommandNode<?, ?>
                        && ((ArgumentCommandNode<?, ?>) n).getType()
                                instanceof IntegerArgumentType
                        && ((IntegerArgumentType) ((ArgumentCommandNode<?, ?>) n).getType())
                                .getMinimum() == SlabbedRigService.MIN_NUMERIC_TOWER_COUNT
                        && ((IntegerArgumentType) ((ArgumentCommandNode<?, ?>) n).getType())
                                .getMaximum() == SlabbedRigService.MAX_NUMERIC_TOWER_COUNT
                        && n.getChild("force") != null
                        && n.getChild("height") instanceof ArgumentCommandNode<?, ?>
                        && n.getChild("height").getChild("force") != null,
                "numeric tower must expose bounded n, optional height, and both force positions");
        IntegerArgumentType heightType = (IntegerArgumentType)
                ((ArgumentCommandNode<?, ?>) n.getChild("height")).getType();
        ctx.assertTrue(heightType.getMinimum() == SlabbedRigService.MIN_NUMERIC_TOWER_HEIGHT
                        && heightType.getMaximum()
                                == SlabbedRigService.MAX_NUMERIC_TOWER_HEIGHT,
                "numeric tower height must remain bounded to the admitted diagnostic range");
        for (String command : List.of(
                "slabrig tower 1",
                "slabrig tower 1 force",
                "slabrig tower 1 2",
                "slabrig tower 1 2 force")) {
            ctx.assertTrue(dispatcher.parse(command, allowed).getExceptions().isEmpty(),
                    "dispatcher must parse numeric tower form: " + command);
        }
        CommandNode<CommandSourceStack> stacks = slabrig.getChild("stacks");
        CommandNode<CommandSourceStack> maxLength = stacks.getChild("max_length");
        ctx.assertTrue(stacks.getChild("force") != null
                        && maxLength instanceof ArgumentCommandNode<?, ?>
                        && ((ArgumentCommandNode<?, ?>) maxLength).getType()
                                instanceof IntegerArgumentType
                        && ((IntegerArgumentType)
                                ((ArgumentCommandNode<?, ?>) maxLength).getType())
                                .getMinimum() == SlabbedRigService.MIN_STACK_MAX_LENGTH
                        && ((IntegerArgumentType)
                                ((ArgumentCommandNode<?, ?>) maxLength).getType())
                                .getMaximum() == SlabbedRigService.MAX_STACK_MAX_LENGTH
                        && maxLength.getChild("force") != null
                        && maxLength.getChild("page") instanceof ArgumentCommandNode<?, ?>
                        && maxLength.getChild("page").getChild("force") != null,
                "stacks must expose bounded max_length, one-based page, and every force arm");
        IntegerArgumentType pageType = (IntegerArgumentType)
                ((ArgumentCommandNode<?, ?>) maxLength.getChild("page")).getType();
        ctx.assertTrue(pageType.getMinimum() == 1,
                "stack pages must be one-based before dynamic plan validation");
        for (String command : List.of(
                "slabrig stacks",
                "slabrig stacks force",
                "slabrig stacks 5",
                "slabrig stacks 5 force",
                "slabrig stacks 5 4",
                "slabrig stacks 5 4 force")) {
            ctx.assertTrue(dispatcher.parse(command, allowed).getExceptions().isEmpty(),
                    "dispatcher must parse stack form: " + command);
        }
        for (String command : List.of(
                "slabrig stacks 0",
                "slabrig stacks 6",
                "slabrig stacks 5 0",
                "slabrig stacks 5 1 junk")) {
            ctx.assertTrue(dispatcher.parse(command, allowed).getReader().canRead(),
                    "invalid stack form must not parse to an executable command: " + command);
        }
        CommandNode<CommandSourceStack> mega = slabrig.getChild("mega");
        CommandNode<CommandSourceStack> megaCount = mega.getChild("count");
        ctx.assertTrue(mega.getChild("force") != null
                        && megaCount instanceof ArgumentCommandNode<?, ?>
                        && ((ArgumentCommandNode<?, ?>) megaCount).getType()
                                instanceof IntegerArgumentType
                        && ((IntegerArgumentType)
                                ((ArgumentCommandNode<?, ?>) megaCount).getType())
                                .getMinimum() == SlabbedRigService.MIN_MEGA_COLUMNS
                        && ((IntegerArgumentType)
                                ((ArgumentCommandNode<?, ?>) megaCount).getType())
                                .getMaximum() == SlabbedRigService.MAX_MEGA_COLUMNS
                        && megaCount.getChild("force") != null,
                "mega must expose bounded count and both guarded force arms");
        for (String command : List.of(
                "slabrig mega",
                "slabrig mega force",
                "slabrig mega 1",
                "slabrig mega 1 force")) {
            ctx.assertTrue(dispatcher.parse(command, allowed).getExceptions().isEmpty(),
                    "dispatcher must parse mega form: " + command);
        }
        for (String command : List.of(
                "slabrig mega 0",
                "slabrig mega 41",
                "slabrig mega 1 junk")) {
            ctx.assertTrue(dispatcher.parse(command, allowed).getReader().canRead(),
                    "invalid mega form must not parse to an executable command: " + command);
        }
    }

    private static Set<BlockPos> expectedStackFootprint(
            SlabbedRigService.StackPlan plan) {
        Set<BlockPos> expected = new LinkedHashSet<>();
        for (SlabbedRigService.NumericTowerColumn stack : plan.stacks()) {
            stack.fixtures().forEach(fixture -> expected.add(fixture.pos()));
            for (int step = 0; step < stack.recipe().length(); step++) {
                BlockPos cursor = stack.seat().above(step);
                BlockPos target = cursor.above();
                expected.add(cursor);
                expected.add(target);
                expected.add(target.above());
                expected.add(target.below());
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    expected.add(target.relative(direction));
                }
            }
            BlockPos plannedTop = stack.seat().above(stack.recipe().length());
            expected.add(plannedTop.above());
            expected.add(plannedTop.above(2));
        }
        return expected;
    }

    private static Set<BlockPos> expectedStackOwnedPositions(
            SlabbedRigService.StackPlan plan) {
        Set<BlockPos> expected = new LinkedHashSet<>();
        for (SlabbedRigService.NumericTowerColumn stack : plan.stacks()) {
            stack.fixtures().forEach(fixture -> expected.add(fixture.pos()));
            for (int step = 1; step <= stack.recipe().length(); step++) {
                expected.add(stack.seat().above(step));
            }
        }
        return expected;
    }

    private static List<RigManifest.SeamFinding> expectedNumericSeams(
            RigManifest.NumericTowerReport report) {
        return expectedSeams(report.towers());
    }

    private static List<RigManifest.SeamFinding> expectedStackSeams(
            RigManifest.StackPageReport report) {
        return expectedSeams(report.stacks().stream()
                .map(RigManifest.StackEntryReport::column)
                .toList());
    }

    private static List<RigManifest.SeamFinding> expectedSeams(
            List<RigManifest.TowerColumnReport> towers) {
        List<RigManifest.SeamFinding> expected = new ArrayList<>();
        for (RigManifest.TowerColumnReport tower : towers) {
            List<RigManifest.TowerCellReadback> cells = tower.cells();
            for (int index = 1; index < cells.size(); index++) {
                RigManifest.TowerCellReadback lower = cells.get(index - 1);
                RigManifest.TowerCellReadback upper = cells.get(index);
                double visibleHeight = lower.state().getBlock() instanceof SlabBlock
                                && lower.state().getValue(SlabBlock.TYPE) != SlabType.DOUBLE
                        ? 0.5d : 1.0d;
                double lowerTop = lower.pos().getY() + lower.liveDy() + visibleHeight;
                double upperBottom = upper.pos().getY() + upper.liveDy();
                double seam = upperBottom - lowerTop;
                if (seam > 1.0e-6d) {
                    expected.add(new RigManifest.SeamFinding(
                            RigManifest.SeamKind.GAP,
                            lower.pos(),
                            upper.pos(),
                            Double.doubleToRawLongBits(lowerTop),
                            Double.doubleToRawLongBits(upperBottom),
                            Double.doubleToRawLongBits(seam)));
                } else if (seam < -1.0e-6d) {
                    expected.add(new RigManifest.SeamFinding(
                            RigManifest.SeamKind.OVERLAP,
                            lower.pos(),
                            upper.pos(),
                            Double.doubleToRawLongBits(lowerTop),
                            Double.doubleToRawLongBits(upperBottom),
                            Double.doubleToRawLongBits(seam)));
                }
            }
        }
        return List.copyOf(expected);
    }

    private static void assertTowerPlan(
            GameTestHelper ctx,
            BlockPos anchor,
            Direction facing,
            List<RigCase> plan) {
        ctx.assertTrue(plan.size() == 1
                        && plan.get(0).id().equals("tower.compound_visible"),
                "bare tower must be the canonical compound-visible fixture case");
        RigCase tower = plan.get(0);
        BlockPos compound = anchor.above(4);
        BlockPos side = compound.relative(facing.getCounterClockWise());
        ctx.assertTrue(tower.subjects().isEmpty() && tower.fixtures().size() == 6,
                "bare tower must contain six declared fixtures and no proxy subjects");
        List<BlockPos> expected = List.of(
                anchor, anchor.above(), anchor.above(2), anchor.above(3), compound, side);
        ctx.assertTrue(tower.fixtures().stream()
                        .map(RigCase.FixtureCell::pos)
                        .toList().equals(expected),
                "bare tower fixture order must match the compound-visible donor geometry");
        ctx.assertTrue(tower.fixtures().get(0).state().is(Blocks.STONE)
                        && tower.fixtures().get(1).state().is(Blocks.OAK_SLAB)
                        && tower.fixtures().get(2).state().is(Blocks.STONE)
                        && tower.fixtures().get(3).state().is(Blocks.OAK_SLAB)
                        && tower.fixtures().get(4).state().is(Blocks.STONE)
                        && tower.fixtures().get(5).state().is(Blocks.STONE_SLAB),
                "bare tower fixtures must be stone/oak/stone/oak/stone plus the legal stone side slab");
        ctx.assertTrue(tower.fixtures().get(4).authorship().kind()
                        == RigCase.FixtureAuthorship.Kind.COMPOUND_FULL_BLOCK
                        && tower.fixtures().get(5).authorship().kind()
                        == RigCase.FixtureAuthorship.Kind.COMPOUND_VISIBLE_SIDE_LOWER_SLAB
                        && tower.fixtures().get(5).authorship().sourcePos().equals(compound),
                "bare tower must declare both compound marker relationships explicitly");
    }

    private static void assertTowerManifest(
            GameTestHelper ctx,
            ServerLevel world,
            ServerPlayer player,
            BlockPos anchor,
            Direction facing,
            RigManifest manifest,
            List<SlabbedDiagnosticsBridge.ActionOriginContext> origins,
            AtomicInteger opened,
            AtomicInteger closed) {
        ctx.assertTrue(manifest != null
                        && manifest.anchor().equals(anchor)
                        && manifest.ownerUuid().equals(player.getUUID().toString())
                        && manifest.dimensionId().equals(world.dimension().location().toString())
                        && manifest.mode().equals("tower")
                        && manifest.caseIds().equals(List.of("tower.compound_visible")),
                "tower manifest must bind exact identity, mode, and case ordering");
        BlockPos compound = anchor.above(4);
        BlockPos side = compound.relative(facing.getCounterClockWise());
        List<BlockPos> expected = List.of(
                anchor, anchor.above(), anchor.above(2), anchor.above(3), compound, side);
        ctx.assertTrue(manifest.ownedCells().stream()
                        .map(RigManifest.OwnedCell::pos)
                        .toList().equals(expected),
                "tower manifest must own the exact six-cell compound fixture in execution order");
        ctx.assertTrue(manifest.ownedCells().stream().allMatch(cell ->
                        cell.role() == RigManifest.CellRole.FIXTURE
                                && !cell.expectedStoredDy().present()),
                "bare tower cells must remain declared scenery, never player-authored subjects");
        RigManifest.ExecutionReceipt receipt = manifest.receipt();
        ctx.assertTrue(receipt.fixtureDirectWrites() == 6
                        && receipt.fixtureTruthWrites() == 2
                        && receipt.subjectUseOnCalls() == 0
                        && receipt.subjectDirectStateWrites() == 0
                        && receipt.resolutions().isEmpty(),
                "tower receipt must record six fixture writes, two marker writes, and no useOn");
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, compound)
                        && SlabAnchorAttachment.isCompoundFullBlockAnchor(world, compound)
                        && SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(
                                world, side, world.getBlockState(side))
                        && Double.doubleToRawLongBits(SlabSupport.getYOffset(
                                world, compound, world.getBlockState(compound)))
                                == Double.doubleToRawLongBits(-1.0d)
                        && Double.doubleToRawLongBits(SlabSupport.getYOffset(
                                world, side, world.getBlockState(side)))
                                == Double.doubleToRawLongBits(-1.0d),
                "bare tower must self-prove exact -1.0 compound and side-marker truth");
        ctx.assertTrue(opened.get() == 0 && closed.get() == 0 && origins.isEmpty(),
                "fixture-only bare tower must not fabricate AUTO_USEON_PROXY evidence");
    }

    private static void assertRowsManifest(
            GameTestHelper ctx,
            ServerLevel world,
            ServerPlayer player,
            BlockPos anchor,
            RigManifest manifest,
            List<SlabbedDiagnosticsBridge.ActionOriginContext> origins,
            AtomicInteger opened,
            AtomicInteger closed) {
        ctx.assertTrue(manifest != null, "a successful rig build must publish a manifest");
        ctx.assertTrue(manifest.anchor().equals(anchor)
                        && manifest.ownerUuid().equals(player.getUUID().toString())
                        && manifest.dimensionId().equals(world.dimension().location().toString())
                        && manifest.mode().equals("rows"),
                "manifest identity must bind owner, dimension, anchor, and mode");
        ctx.assertTrue(manifest.caseIds().equals(List.of(
                        "rows.left", "rows.center", "rows.right")),
                "rows case IDs and ordering must be deterministic");

        Set<BlockPos> expected = Set.of(
                anchor.offset(2, 0, 0), anchor.offset(2, 1, 0),
                anchor, anchor.above(),
                anchor.offset(-2, 0, 0), anchor.offset(-2, 1, 0));
        Set<BlockPos> actual = new LinkedHashSet<>();
        long fixtureCount = 0;
        long subjectCount = 0;
        for (RigManifest.OwnedCell cell : manifest.ownedCells()) {
            actual.add(cell.pos());
            if (cell.role() == RigManifest.CellRole.FIXTURE) {
                fixtureCount++;
                ctx.assertTrue(cell.expectedState().is(Blocks.OAK_SLAB)
                                && !cell.expectedStoredDy().present(),
                        "direct-written fixtures must be declared slabs with no authored dy");
            } else {
                subjectCount++;
                ctx.assertTrue(cell.expectedState().is(Blocks.STONE)
                                && cell.expectedStoredDy().present()
                                && cell.expectedStoredDy().rawBits()
                                == Double.doubleToRawLongBits(-0.5d),
                        "real-use subjects must own their exact stone state and stored -0.5 bits");
            }
        }
        ctx.assertTrue(actual.equals(expected) && actual.size() == manifest.ownedCells().size(),
                "manifest must own exactly the six intended cells, with no duplicates or extras");
        ctx.assertTrue(fixtureCount == 3 && subjectCount == 3,
                "rows must declare three fixture cells and three subject cells");

        RigManifest.ExecutionReceipt receipt = manifest.receipt();
        ctx.assertTrue(receipt.fixtureDirectWrites() == 3
                        && receipt.fixtureTruthWrites() == 0
                        && receipt.subjectUseOnCalls() == 3
                        && receipt.subjectDirectStateWrites() == 0,
                "the execution receipt must prove direct writes are fixture-only");
        ctx.assertTrue(receipt.resolutions().size() == 3,
                "every subject useOn call must publish one landing resolution");
        for (LandingResolution resolution : receipt.resolutions()) {
            ctx.assertTrue(resolution instanceof LandingResolution.Place,
                    "every admitted rows subject must resolve as a real placement");
            LandingResolution.Place place = (LandingResolution.Place) resolution;
            ctx.assertTrue(place.lane() == LandingResolution.Lane.LOWERED
                            && place.rawDyBits() == Double.doubleToRawLongBits(-0.5d)
                            && expected.contains(place.targetPos()),
                    "landing resolution must carry exact target, lowered lane, and raw dy bits");
        }

        Set<BlockPos> subjects = manifest.ownedCells().stream()
                .filter(cell -> cell.role() == RigManifest.CellRole.SUBJECT)
                .map(RigManifest.OwnedCell::pos)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        ctx.assertTrue(opened.get() == 3 && closed.get() == 3 && origins.size() == 3,
                "each subject must open and close exactly one proxy-origin scope");
        for (SlabbedDiagnosticsBridge.ActionOriginContext origin : origins) {
            ctx.assertTrue(origin.playerUuid().equals(player.getUUID().toString())
                            && origin.dimensionId().equals(world.dimension().location().toString())
                            && subjects.contains(origin.placementPos()),
                    "each proxy scope must bind the exact player, dimension, and real landing cell");
        }
    }

    private static void assertDeterministic(
            GameTestHelper ctx,
            RigManifest first,
            RigManifest second) {
        ctx.assertTrue(first.caseIds().equals(second.caseIds()),
                "rebuild must preserve ordered case IDs");
        ctx.assertTrue(first.ownedCells().equals(second.ownedCells()),
                "rebuild must preserve exact owned cells, states, facts, and roles");
        ctx.assertTrue(first.receipt().equals(second.receipt()),
                "rebuild must preserve the fixture/useOn audit and landing resolutions");
    }

    private static int run(ServerLevel world, CommandSourceStack source, String command) {
        return world.getServer().getCommands().performPrefixedCommand(source, command);
    }

    private static void assertAllAirAndUnstored(
            GameTestHelper ctx,
            ServerLevel world,
            List<RigManifest.OwnedCell> cells) {
        for (RigManifest.OwnedCell cell : cells) {
            ctx.assertTrue(world.getBlockState(cell.pos()).isAir()
                            && !SlabAnchorAttachment.storedPlacementDyFact(world, cell.pos()).present(),
                    "clear must remove block and stored placement truth at "
                            + cell.pos().toShortString());
        }
    }

    private static void assertPlanAirAndUnstored(
            GameTestHelper ctx,
            ServerLevel world,
            List<RigCase> plan) {
        for (RigCase rigCase : plan) {
            for (RigCase.FixtureCell fixture : rigCase.fixtures()) {
                assertAirAndUnstored(ctx, world, fixture.pos());
            }
            for (RigCase.SubjectPlacement subject : rigCase.subjects()) {
                assertAirAndUnstored(ctx, world, subject.aim().vanillaTarget());
            }
        }
    }

    private static void assertAirAndUnstored(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos pos) {
        ctx.assertTrue(world.getBlockState(pos).isAir()
                        && !SlabAnchorAttachment.storedPlacementDyFact(world, pos).present(),
                "failed build rollback must leave no block or stored fact at "
                        + pos.toShortString());
    }

    private static List<ItemStack> snapshotInventory(ServerPlayer player) {
        List<ItemStack> snapshot = new ArrayList<>(player.getInventory().getContainerSize());
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            snapshot.add(player.getInventory().getItem(slot).copy());
        }
        return snapshot;
    }

    private static void restoreInventory(ServerPlayer player, List<ItemStack> snapshot) {
        for (int slot = 0; slot < snapshot.size(); slot++) {
            player.getInventory().setItem(slot, snapshot.get(slot).copy());
        }
    }

    private static void assertInventory(
            GameTestHelper ctx,
            List<ItemStack> expected,
            ServerPlayer player,
            String message) {
        for (int slot = 0; slot < expected.size(); slot++) {
            ctx.assertTrue(ItemStack.matches(expected.get(slot), player.getInventory().getItem(slot)),
                    message + " (slot " + slot + ")");
        }
    }

    private static List<BlockSnapshot> snapshotBlocks(
            ServerLevel world,
            List<RigManifest.OwnedCell> cells) {
        List<BlockSnapshot> snapshots = new ArrayList<>(cells.size());
        for (RigManifest.OwnedCell cell : cells) {
            snapshots.add(new BlockSnapshot(
                    cell.pos(),
                    world.getBlockState(cell.pos()),
                    SlabAnchorAttachment.storedPlacementDyFact(world, cell.pos())));
        }
        return snapshots;
    }

    private record BlockSnapshot(
            BlockPos pos,
            BlockState state,
            SlabAnchorAttachment.PlacementDyFact storedDy) {
    }
}
