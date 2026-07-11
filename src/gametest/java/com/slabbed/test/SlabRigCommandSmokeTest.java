package com.slabbed.test;

import com.mojang.brigadier.CommandDispatcher;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.command.SlabRigCommand;
import com.slabbed.command.SlabRigCaseArtifacts;
import com.slabbed.command.SlabRigCaseCatalog;
import com.slabbed.util.LiveCursorIntentRecorder;
import com.slabbed.util.BuildStamp;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Smoke tests for {@code /slabrig}: register the command into a fresh dispatcher and execute the
 * presets through it against a positioned command source, then assert the rigs are BUILT, MEASURE what
 * their signs say ({@link SlabSupport#getYOffset}), and — for {@code clear} — leave air with every
 * Slabbed attachment gone.
 *
 * <p>This exercises the real command path (parse + {@code requires} permission gate + executor),
 * not a private builder, so a broken registration or executor fails here.
 */
public final class SlabRigCommandSmokeTest {

    private static final double EPS = 1.0e-6;
    private static final int COLUMN_SPACING_FOR_TEST = 2;

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigIsRegisteredInProductionServerDispatcher(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        CommandSourceStack source = sourceAt(world, h.absolutePos(new BlockPos(1, 2, 1)));
        CommandDispatcher<CommandSourceStack> live = world.getServer().getCommands().getDispatcher();
        if (live.getRoot().getChild("slabrig") == null) {
            throw h.assertionException(
                    "production server dispatcher is missing /slabrig after Slabbed.onInitialize");
        }
        try {
            int result = live.execute("slabrig catalog", source);
            if (result != 1) {
                throw h.assertionException(
                        "production-dispatcher /slabrig catalog must execute successfully, got " + result);
            }
        } catch (Exception e) {
            throw h.assertionException("production-dispatcher /slabrig catalog threw: " + e);
        }
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigHangsCatalogIsRegisteredAndWorldFree(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos markerSupport = h.absolutePos(new BlockPos(6, 1, 6));
        BlockPos markerSubject = markerSupport.above();
        BlockPos blockEntitySentinel = h.absolutePos(new BlockPos(7, 1, 7));
        world.setBlock(markerSupport, Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), 3);
        world.setBlock(markerSubject, Blocks.STONE.defaultBlockState(), 3);
        SlabAnchorAttachment.addAnchor(world, markerSubject, world.getBlockState(markerSubject));
        world.setBlock(blockEntitySentinel, Blocks.CHEST.defaultBlockState(), 3);
        h.makeMockPlayer(GameType.SURVIVAL);
        if (!SlabAnchorAttachment.isAnchored(world, markerSubject)
                || world.getBlockEntity(blockEntitySentinel) == null) {
            throw h.assertionException("RIG-3B1 no-world proof sentinels failed to initialize");
        }
        List<String> messages = new ArrayList<>();
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                messages.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = sourceAt(world, h.absolutePos(new BlockPos(1, 2, 1)))
                .withSource(capture);
        String before = noWorldFingerprint(h, world, source);
        CommandDispatcher<CommandSourceStack> live = world.getServer().getCommands().getDispatcher();
        var slabrigNode = live.getRoot().getChild("slabrig");
        if (slabrigNode == null || slabrigNode.getChild("hangs") == null
                || slabrigNode.getChild("hangs").getChild("catalog") == null) {
            throw h.assertionException("production dispatcher lacks exact /slabrig hangs catalog nodes");
        }
        CommandDispatcher<CommandSourceStack> isolated = new CommandDispatcher<>();
        SlabRigCommand.register(isolated);
        if (tryExec(isolated, source, "slabrig hangs") != -1
                || tryExec(isolated, source, "slabrig hangs junk") != -1
                || tryExec(isolated, source, "slabrig hangs catalog force") != -1
                || tryExec(isolated, source.withPermission(PermissionSet.NO_PERMISSIONS),
                "slabrig hangs catalog") != -1) {
            throw h.assertionException("hangs catalog grammar/permission boundary accepted an invalid form");
        }
        int result = tryExec(live, source, "slabrig hangs catalog");
        if (result != 1) {
            throw h.assertionException("/slabrig hangs catalog must export successfully: " + messages);
        }
        String firstStatus = messages.getLast();
        int repeatResult = tryExec(live, source, "slabrig hangs catalog");
        if (repeatResult != 1 || !firstStatus.equals(messages.getLast())) {
            throw h.assertionException("repeated hangs catalog command changed identity/path/status: " + messages);
        }
        String after = noWorldFingerprint(h, world, source);
        if (!before.equals(after)) {
            throw h.assertionException("/slabrig hangs catalog mutated world/session state\nbefore="
                    + before + "\nafter=" + after);
        }
        String status = String.join("\n", messages);
        for (String required : new String[]{
                "[slabrig] hangs catalog", "runtimeItems=1537", "subjects=163", "routes=38740",
                "paintingVariants=51", "randomPlaceable=47", "catalogHash=",
                "minecraft=26.2", "runtimeContentSha256=", "paintingRegistry=minecraft:painting_variant",
                "placeableTag=minecraft:placeable", "paintingComponent=minecraft:painting/variant",
                "paintingHash=", "executionIdentity=", "artifactSha256=",
                "playerProof=ABSENT", "worldMutation=NONE", "artifact="}) {
            if (!status.contains(required)) {
                throw h.assertionException("hangs catalog truthful status omitted '" + required
                        + "': " + status);
            }
        }
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigHangsDirectRemainsUnavailableDuringRig3B2AKernelPass(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        CommandSourceStack source = sourceAt(world, h.absolutePos(new BlockPos(1, 2, 1)));
        String before = noWorldFingerprint(h, world, source);

        CommandDispatcher<CommandSourceStack> live = world.getServer().getCommands().getDispatcher();
        var slabrig = live.getRoot().getChild("slabrig");
        var hangs = slabrig == null ? null : slabrig.getChild("hangs");
        if (hangs == null || hangs.getChild("catalog") == null) {
            throw h.assertionException("RIG-3B2A boundary test lost the existing hangs catalog node");
        }
        if (hangs.getChild("direct") != null) {
            throw h.assertionException(
                    "RIG-3B2A must not expose a production world-writing hangs direct command");
        }

        CommandDispatcher<CommandSourceStack> isolated = new CommandDispatcher<>();
        SlabRigCommand.register(isolated);
        if (tryExec(isolated, source,
                "slabrig hangs direct 6143 topology 42 paintings 1 force") != -1) {
            throw h.assertionException("RIG-3B2A dispatcher accepted its B2B-reserved grammar");
        }
        String after = noWorldFingerprint(h, world, source);
        if (!before.equals(after)) {
            throw h.assertionException("rejected RIG-3B2B grammar changed world/session state\nbefore="
                    + before + "\nafter=" + after);
        }
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigStackCatalogAndPageContract(GameTestHelper h) {
        List<String> expected = new ArrayList<>();
        for (int length = 1; length <= 5; length++) {
            int count = 1 << length;
            for (int bits = 0; bits < count; bits++) {
                StringBuilder recipe = new StringBuilder(length);
                for (int shift = length - 1; shift >= 0; shift--) {
                    recipe.append((bits & (1 << shift)) == 0 ? 'S' : 'B');
                }
                expected.add(recipe.toString());
            }
            int prefix = (1 << (length + 1)) - 2;
            List<String> actualPrefix = SlabRigCommand.stackRecipes(length);
            if (actualPrefix.size() != prefix || !actualPrefix.equals(expected)) {
                throw h.assertionException("stack catalog max=" + length + " must be literal length-major "
                        + "S-before-B order; expected=" + expected + " got=" + actualPrefix);
            }
            if (!actualPrefix.equals(SlabRigCommand.stackRecipes(length))) {
                throw h.assertionException("stack catalog order must be stable across repeated calls");
            }
            if (new HashSet<>(actualPrefix).size() != actualPrefix.size()) {
                throw h.assertionException("stack catalog must contain no duplicates: " + actualPrefix);
            }
        }

        List<String> roundTrip = new ArrayList<>();
        int[] expectedPageSizes = {16, 16, 16, 14};
        for (int page = 1; page <= 4; page++) {
            SlabRigCommand.StackPage stackPage = SlabRigCommand.stackPage(5, page);
            if (stackPage.page() != page || stackPage.pageCount() != 4
                    || stackPage.recipes().size() != expectedPageSizes[page - 1]) {
                throw h.assertionException("stack page " + page + " must be 1-based with sizes 16/16/16/14: "
                        + stackPage);
            }
            roundTrip.addAll(stackPage.recipes());
        }
        if (!roundTrip.equals(expected)) {
            throw h.assertionException("concatenated pages must round-trip the exact 62-recipe catalog");
        }

        if (LiveCursorIntentRecorder.currentActionOrigin()
                != LiveCursorIntentRecorder.ActionOrigin.PLAYER_AUTHORED) {
            throw h.assertionException("recorder action origin must default to PLAYER_AUTHORED");
        }
        LiveCursorIntentRecorder.withActionOrigin(LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY, () -> {
            if (LiveCursorIntentRecorder.currentActionOrigin()
                    != LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY) {
                throw h.assertionException("outer origin scope must expose AUTO_USEON_PROXY");
            }
            LiveCursorIntentRecorder.withActionOrigin(LiveCursorIntentRecorder.ActionOrigin.PLAYER_AUTHORED, () -> {
                if (LiveCursorIntentRecorder.currentActionOrigin()
                        != LiveCursorIntentRecorder.ActionOrigin.PLAYER_AUTHORED) {
                    throw h.assertionException("nested origin scope must expose its own value");
                }
            });
            if (LiveCursorIntentRecorder.currentActionOrigin()
                    != LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY) {
                throw h.assertionException("nested origin scope must restore the outer value");
            }
        });
        if (LiveCursorIntentRecorder.currentActionOrigin()
                != LiveCursorIntentRecorder.ActionOrigin.PLAYER_AUTHORED) {
            throw h.assertionException("origin scope must restore PLAYER_AUTHORED after exit");
        }
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigStacksStatusAndExactClear(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        CommandSourceStack source = playerSourceAt(w, player, h.absolutePos(new BlockPos(7, 2, 0)));
        if (!"none".equals(SlabRigCommand.trackedManifestStatus(source))) {
            throw h.assertionException("fresh server session must not report a tracked rig");
        }

        int result = tryExec(source, "slabrig stacks 1 1 force");
        // Recipe S honestly exposes the currently sanctioned slab-on-lowered-seed gap; the rig remains
        // exact-clearable while reporting structural failure instead of a false green.
        if (result != 0) {
            throw h.assertionException("stacks 1 must report the known S-seed seam as structural failure");
        }
        Direction facing = SlabRigCommand.rigFacing(source);
        Direction right = facing.getClockWise();
        BlockPos base = SlabRigCommand.rigBase(source);
        BlockPos sCell = base.above(4);
        BlockPos bSeedBase = base.relative(right, COLUMN_SPACING_FOR_TEST);
        BlockPos bCell = bSeedBase.above(4);
        assertBottomSlab(h, w, sCell, "recipe S cell");
        assertBlock(h, w, bCell, Blocks.STONE.defaultBlockState(), "recipe B cell");

        String status = SlabRigCommand.trackedManifestStatus(source);
        for (String required : new String[]{
                "preset=stacks", "page=1/1 recipes=1-2/2", "structural=incomplete",
                "provenance=AUTO_USEON_PROXY", "reserved=", "authored=", "attachments=",
                "subjects=", "clearOwned=", "present=", "missing=", "bounds="}) {
            if (!status.contains(required)) {
                throw h.assertionException("status must expose exact manifest field '" + required
                        + "'; got: " + status);
            }
        }

        // Explicit incidental grid gap: inside displayed bounds and scan-reserved, never clear-owned.
        BlockPos unrelatedGap = base.relative(right, 1);
        w.setBlock(unrelatedGap, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        SlabAnchorAttachment.capturePlacementDy(w, unrelatedGap, w.getBlockState(unrelatedGap));
        double unrelatedStored = SlabAnchorAttachment.storedPlacementDy(w, unrelatedGap);
        if (Double.isNaN(unrelatedStored)) {
            throw h.assertionException("premise: unrelated gap must carry stored dy before clear");
        }

        BlockPos laterSubject = bCell.above();
        w.setBlock(laterSubject, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        BlockPos alreadyAirAttachment = bCell.above(2);
        w.setBlock(alreadyAirAttachment, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        SlabAnchorAttachment.capturePlacementDy(w, alreadyAirAttachment,
                w.getBlockState(alreadyAirAttachment));
        w.setBlock(alreadyAirAttachment, Blocks.AIR.defaultBlockState(), 2);
        if (Double.isNaN(SlabAnchorAttachment.storedPlacementDy(w, alreadyAirAttachment))) {
            throw h.assertionException("premise: flag-2 removal must leave a stored-dy touch on the air slot");
        }
        exec(h, source, "slabrig clear");
        assertAir(h, w, sCell, "recipe S after clear");
        assertAir(h, w, bCell, "recipe B after clear");
        assertAir(h, w, laterSubject, "later subject in declared slot after clear");
        if (!Double.isNaN(SlabAnchorAttachment.storedPlacementDy(w, alreadyAirAttachment))) {
            throw h.assertionException("exact clear must remove recorded attachment/store touches even for air");
        }
        assertBlock(h, w, unrelatedGap, Blocks.DIAMOND_BLOCK.defaultBlockState(),
                "unrelated in-bounds gap after exact clear");
        double storedAfter = SlabAnchorAttachment.storedPlacementDy(w, unrelatedGap);
        if (Double.doubleToLongBits(storedAfter) != Double.doubleToLongBits(unrelatedStored)) {
            throw h.assertionException("exact clear must preserve unrelated stored dy; before="
                    + unrelatedStored + " after=" + storedAfter);
        }
        if (!"none".equals(SlabRigCommand.trackedManifestStatus(source))) {
            throw h.assertionException("status must report none after exact clear");
        }
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigManifestRefusalForceAndSessionGuards(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        CommandSourceStack source = playerSourceAt(w, player, h.absolutePos(new BlockPos(7, 2, 0)));
        if (!"none".equals(SlabRigCommand.trackedManifestStatus(source))) {
            throw h.assertionException("a fresh player key must not inherit another player's manifest");
        }

        for (String legacy : new String[]{
                "slabrig tower", "slabrig tower force", "slabrig tower 1",
                "slabrig tower 1 2 force", "slabrig rows", "slabrig rows 1 force",
                "slabrig mega 1 force", "slabrig platform", "slabrig catalog",
                "slabrig cases", "slabrig cases 1 force", "slabrig cases resume force",
                "slabrig clear"}) {
            assertParses(h, source, legacy);
        }
        BlockPos base = SlabRigCommand.rigBase(source);
        for (String invalid : new String[]{
                "slabrig stacks 0", "slabrig stacks 6", "slabrig stacks 1 0",
                "slabrig stacks 1 1 junk"}) {
            if (tryExec(source, invalid) >= 0) {
                throw h.assertionException("invalid grammar must reject without executing: /" + invalid);
            }
            if (!w.getBlockState(base).isAir()
                    || !"none".equals(SlabRigCommand.trackedManifestStatus(source))) {
                throw h.assertionException("invalid grammar must perform zero world/manifest mutation: /" + invalid);
            }
        }

        tryExec(source, "slabrig stacks 1 1 force");
        String oldManifest = SlabRigCommand.trackedManifestStatus(source);
        BlockState oldBase = w.getBlockState(base);
        int impossiblePage = tryExec(source, "slabrig stacks 1 2");
        if (impossiblePage != 0 || !oldManifest.equals(SlabRigCommand.trackedManifestStatus(source))
                || !oldBase.equals(w.getBlockState(base))) {
            throw h.assertionException("dynamic invalid page must mutate nothing and preserve the old manifest");
        }

        int refused = tryExec(source, "slabrig rows 1");
        if (refused != 0 || !oldManifest.equals(SlabRigCommand.trackedManifestStatus(source))
                || !oldBase.equals(w.getBlockState(base))) {
            throw h.assertionException("new non-force build must preserve the tracked rig and world exactly");
        }

        exec(h, source, "slabrig rows 1 force");
        String replaced = SlabRigCommand.trackedManifestStatus(source);
        if (!replaced.contains("preset=rows")) {
            throw h.assertionException("force must exact-clear/replace without orphaning: " + replaced);
        }

        ServerLevel otherLevel = w.getServer().getLevel(Level.NETHER);
        if (otherLevel == null) {
            throw h.assertionException("premise: GameTest server must expose the nether level");
        }
        CommandSourceStack splitSource = playerSourceAt(otherLevel, player,
                new BlockPos(0, otherLevel.getMinY() + 10, 0));
        BlockPos splitBase = SlabRigCommand.rigBase(splitSource);
        BlockState splitBefore = otherLevel.getBlockState(splitBase);
        if (tryExec(splitSource, "slabrig mega 1 force") != 0
                || !splitBefore.equals(otherLevel.getBlockState(splitBase))
                || !"none".equals(SlabRigCommand.trackedManifestStatus(splitSource))
                || !replaced.equals(SlabRigCommand.trackedManifestStatus(source))) {
            throw h.assertionException("source/player level mismatch must refuse before any world or manifest mutation");
        }

        exec(h, source, "slabrig clear");
        CommandSourceStack anotherPlayer = playerSourceAt(w, h.makeMockPlayer(GameType.SURVIVAL),
                h.absolutePos(new BlockPos(7, 2, 0)));
        if (!"none".equals(SlabRigCommand.trackedManifestStatus(anotherPlayer))) {
            throw h.assertionException("manifest identity must remain player-bound within one server session");
        }
        h.succeed();
    }

    @GameTest(structure = "slabbed_gametest:rig2_board")
    public void slabrigCasesPageResumeStatusAndExactClear(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        CommandSourceStack source = playerSourceAt(w, player, h.absolutePos(new BlockPos(30, 2, 0)));
        SlabRigCaseCatalog.Snapshot snapshot = SlabRigCaseCatalog.snapshot();

        java.nio.file.Path progressPath = SlabRigCaseArtifacts.progressPath(
                SlabRigCaseArtifacts.defaultRoot(), player.getUUID().toString(),
                w.dimension().identifier().toString(), SlabRigCommand.caseWorldKey(source));
        try {
            SlabRigCaseArtifacts.writeProgress(progressPath, new SlabRigCaseArtifacts.Progress(
                    SlabRigCommand.caseWorldKey(source), BuildStamp.GIT_SHA,
                    BuildStamp.RUNTIME_CONTENT_SHA256, SlabAnchorAttachment.FROZEN_DY_ENABLED,
                    SlabRigCaseCatalog.EXECUTION_CONTRACT,
                    "0".repeat(64),
                    1, snapshot.pageCount(), 0, "none"));
        } catch (java.io.IOException e) {
            throw h.assertionException("could not seed stale progress premise: " + e.getMessage());
        }
        BlockPos untouchedBase = SlabRigCommand.rigBase(source);
        if (tryExec(source, "slabrig cases resume force") != 0
                || !w.getBlockState(untouchedBase).isAir()
                || !"none".equals(SlabRigCommand.trackedManifestStatus(source))) {
            throw h.assertionException("resume must reject a stale catalog hash with zero world/manifest mutation");
        }
        try {
            SlabRigCaseArtifacts.writeCatalog(SlabRigCaseArtifacts.defaultRoot(), snapshot);
            SlabRigCaseArtifacts.writeProgress(progressPath, new SlabRigCaseArtifacts.Progress(
                    SlabRigCommand.caseWorldKey(source), BuildStamp.GIT_SHA,
                    BuildStamp.RUNTIME_CONTENT_SHA256, SlabAnchorAttachment.FROZEN_DY_ENABLED,
                    SlabRigCaseCatalog.EXECUTION_CONTRACT,
                    snapshot.catalogHash(),
                    2, snapshot.pageCount(), 1, "1".repeat(64)));
        } catch (java.io.IOException e) {
            throw h.assertionException("could not seed forged same-catalog progress premise: " + e.getMessage());
        }
        if (tryExec(source, "slabrig cases resume force") != 0
                || !w.getBlockState(untouchedBase).isAir()
                || !"none".equals(SlabRigCommand.trackedManifestStatus(source))) {
            throw h.assertionException("resume must reject a missing prior page manifest with zero mutation");
        }

        BlockPos base = SlabRigCommand.rigBase(source);
        BlockPos reservedOnly = base.offset(2, 1, 2);
        w.setBlock(reservedOnly, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        SlabAnchorAttachment.capturePlacementDy(w, reservedOnly, w.getBlockState(reservedOnly));
        double stored = SlabAnchorAttachment.storedPlacementDy(w, reservedOnly);
        BlockPos hauntedAir = base.offset(-2, 1, -2);
        w.setBlock(hauntedAir, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        SlabAnchorAttachment.capturePlacementDy(w, hauntedAir, w.getBlockState(hauntedAir));
        w.setBlock(hauntedAir, Blocks.AIR.defaultBlockState(), 2);
        double hauntedStored = SlabAnchorAttachment.storedPlacementDy(w, hauntedAir);
        if (Double.isNaN(hauntedStored)) {
            throw h.assertionException("premise: flag-2 air must retain haunted stored dy");
        }

        int built = tryExec(source, "slabrig cases 1 force");
        if (built != 1) {
            throw h.assertionException("RIG-2 page 1 must finalize as an exact proxy diagnostic board");
        }
        String pageOne = SlabRigCommand.trackedManifestStatus(source);
        for (String required : new String[]{
                "preset=cases", "page=1/" + snapshot.pageCount(),
                "catalogHash=" + snapshot.catalogHash(), "caseBoard=4_items_x_4_topologies",
                "frozenDy=" + SlabAnchorAttachment.FROZEN_DY_ENABLED,
                "playerProof=ABSENT", "provenance=AUTO_USEON_PROXY", "structural=incomplete",
                "topologyLawReds="}) {
            if (!pageOne.contains(required)) {
                throw h.assertionException("cases status missing " + required + ": " + pageOne);
            }
        }
        String pageOneJson = SlabRigCommand.trackedCaseManifestJsonForTests(source);
        assertCaseManifestLimits(h, pageOneJson, 1, snapshot.pageCount());
        assertGuardContext(h, pageOneJson, reservedOnly, hauntedAir);
        assertTrackedRigInsideStructure(h, source);
        byte[] progressAfterPageOne = readRequiredBytes(h, progressPath,
                "page 1 durable cursor premise");
        exec(h, source, "slabrig clear");
        if (!w.getBlockState(base).isAir()
                || !"none".equals(SlabRigCommand.trackedManifestStatus(source))) {
            throw h.assertionException("cross-mode premise requires page 1 world ownership cleared");
        }
        boolean pageOneFrozenMode = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = !pageOneFrozenMode;
        try {
            int crossModeResume = tryExec(source, "slabrig cases resume force");
            if (crossModeResume != 0
                    || !w.getBlockState(base).isAir()
                    || !"none".equals(SlabRigCommand.trackedManifestStatus(source))
                    || !java.util.Arrays.equals(progressAfterPageOne,
                    readRequiredBytes(h, progressPath, "cursor after cross-mode resume refusal"))) {
                throw h.assertionException(
                        "resume must reject another frozen-dy mode with world/cursor byte-exactly preserved");
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = pageOneFrozenMode;
        }
        if (tryExec(source, "slabrig cases 1 force") != 1) {
            throw h.assertionException("matching-mode page 1 must rebuild after cross-mode refusal");
        }
        pageOne = SlabRigCommand.trackedManifestStatus(source);
        if (!java.util.Arrays.equals(progressAfterPageOne,
                readRequiredBytes(h, progressPath, "cursor after matching-mode page 1 rebuild"))) {
            throw h.assertionException("same-mode page 1 rebuild must restore identical cursor bytes");
        }

        int nonForce = tryExec(source, "slabrig cases 2");
        if (nonForce != 0 || !pageOne.equals(SlabRigCommand.trackedManifestStatus(source))
                || !java.util.Arrays.equals(progressAfterPageOne,
                readRequiredBytes(h, progressPath, "cursor after non-force refusal"))) {
            throw h.assertionException(
                    "non-force page replacement must preserve active board and cursor byte-exactly");
        }
        int invalid = tryExec(source, "slabrig cases " + (snapshot.pageCount() + 1));
        if (invalid != 0 || !pageOne.equals(SlabRigCommand.trackedManifestStatus(source))
                || !java.util.Arrays.equals(progressAfterPageOne,
                readRequiredBytes(h, progressPath, "cursor after invalid-page refusal"))) {
            throw h.assertionException(
                    "invalid case page must preserve world/manifest/progress byte-exactly");
        }

        int resumed = tryExec(source, "slabrig cases resume force");
        if (resumed != 1) {
            throw h.assertionException("durable resume must build exact next page for the matching catalog hash");
        }
        String pageTwo = SlabRigCommand.trackedManifestStatus(source);
        if (!pageTwo.contains("preset=cases") || !pageTwo.contains("page=2/" + snapshot.pageCount())) {
            throw h.assertionException("resume must advance to page 2, got: " + pageTwo);
        }
        assertBlock(h, w, reservedOnly, Blocks.DIAMOND_BLOCK.defaultBlockState(),
                "reserved-only guard cell after force replacement");
        double after = SlabAnchorAttachment.storedPlacementDy(w, reservedOnly);
        if (Double.doubleToLongBits(stored) != Double.doubleToLongBits(after)) {
            throw h.assertionException("force replacement must preserve unrelated reserved-only stored dy");
        }
        assertAir(h, w, hauntedAir, "haunted guard state after force replacement");
        double hauntedAfterForce = SlabAnchorAttachment.storedPlacementDy(w, hauntedAir);
        if (Double.doubleToLongBits(hauntedStored) != Double.doubleToLongBits(hauntedAfterForce)) {
            throw h.assertionException("force replacement must preserve haunted-air stored dy byte-exactly");
        }

        exec(h, source, "slabrig clear");
        assertBlock(h, w, reservedOnly, Blocks.DIAMOND_BLOCK.defaultBlockState(),
                "reserved-only guard cell after exact clear");
        if (!"none".equals(SlabRigCommand.trackedManifestStatus(source))) {
            throw h.assertionException("exact clear must remove the active cases manifest");
        }
        assertAir(h, w, hauntedAir, "haunted guard state after exact clear");
        double hauntedAfterClear = SlabAnchorAttachment.storedPlacementDy(w, hauntedAir);
        if (Double.doubleToLongBits(hauntedStored) != Double.doubleToLongBits(hauntedAfterClear)) {
            throw h.assertionException("exact clear must preserve haunted-air stored dy byte-exactly");
        }
        SlabAnchorAttachment.removeAnchor(w, reservedOnly);
        w.setBlock(reservedOnly, Blocks.AIR.defaultBlockState(), 3);
        SlabAnchorAttachment.removeAnchor(w, hauntedAir);
        h.succeed();
    }

    @GameTest(structure = "slabbed_gametest:rig2_board")
    public void slabrigCasesRecordDoorAndBedMultiCellEffects(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        CommandSourceStack source = playerSourceAt(w, player, h.absolutePos(new BlockPos(30, 2, 0)));
        SlabRigCaseCatalog.Snapshot snapshot = SlabRigCaseCatalog.snapshot();

        int doorPage = pageForItem(snapshot, "minecraft:oak_door", 0);
        if (tryExec(source, "slabrig cases " + doorPage + " force") != 1) {
            throw h.assertionException("door case page must finalize");
        }
        assertMultiCellCase(h, SlabRigCommand.trackedCaseManifestJsonForTests(source),
                "minecraft:oak_door");

        int bedPage = pageForItem(snapshot, "minecraft:white_bed", 0);
        if (tryExec(source, "slabrig cases " + bedPage + " force") != 1) {
            throw h.assertionException("bed case page must finalize");
        }
        assertMultiCellCase(h, SlabRigCommand.trackedCaseManifestJsonForTests(source),
                "minecraft:white_bed");
        assertTrackedRigInsideStructure(h, source);
        exec(h, source, "slabrig clear");
        h.succeed();
    }

    @GameTest(structure = "slabbed_gametest:rig2_board")
    public void slabrigCasesNonForcePreservesHauntedAirInsideEffectCell(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        CommandSourceStack source = playerSourceAt(world, player,
                h.absolutePos(new BlockPos(30, 2, 0)));
        BlockPos base = SlabRigCommand.rigBase(source);
        BlockPos hauntedEffect = base.above();
        world.setBlock(hauntedEffect, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
        SlabAnchorAttachment.capturePlacementDy(world, hauntedEffect,
                world.getBlockState(hauntedEffect));
        world.setBlock(hauntedEffect, Blocks.AIR.defaultBlockState(), 2);
        double before = SlabAnchorAttachment.storedPlacementDy(world, hauntedEffect);
        if (Double.isNaN(before)) {
            throw h.assertionException("premise: flag-2 air must retain stored dy inside effect cell");
        }

        int refused = tryExec(source, "slabrig cases 1");
        double after = SlabAnchorAttachment.storedPlacementDy(world, hauntedEffect);
        if (refused != 0 || !world.getBlockState(base).isAir()
                || !world.getBlockState(hauntedEffect).isAir()
                || Double.doubleToLongBits(before) != Double.doubleToLongBits(after)
                || !"none".equals(SlabRigCommand.trackedManifestStatus(source))) {
            throw h.assertionException(
                    "non-force case build must refuse before preclean and preserve haunted effect data");
        }
        SlabAnchorAttachment.removeAnchor(world, hauntedEffect);
        h.succeed();
    }

    @GameTest(structure = "slabbed_gametest:rig2_board")
    public void slabrigCasesPostActionTopologyResetIsExplicitLawRed(GameTestHelper h) {
        boolean savedFrozenMode = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        ServerLevel world = h.getLevel();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        CommandSourceStack source = playerSourceAt(world, player,
                h.absolutePos(new BlockPos(30, 2, 0)));
        SlabRigCaseCatalog.Snapshot snapshot = SlabRigCaseCatalog.snapshot();
        SlabRigCaseCatalog.Topology topology = snapshot.topologies().stream()
                .filter(candidate -> "SBSBS".equals(candidate.recipe()))
                .findFirst().orElseThrow();
        SlabRigCaseCatalog.CatalogItem item = snapshot.items().stream()
                .filter(candidate -> "minecraft:stone".equals(candidate.id()))
                .findFirst().orElseThrow();
        SlabRigCaseCatalog.CaseDefinition definition = SlabRigCaseCatalog.caseAt(snapshot,
                (long) item.index() * snapshot.topologies().size() + topology.index());
        int page = pageForItem(snapshot, item.id(), topology.index());
        Direction facing = Direction.SOUTH;
        BlockPos tile = SlabRigCommand.rigBase(source)
                .relative(facing.getClockWise(), (item.index() % 4) * 8)
                .relative(facing, (topology.index() % 4) * 8);
        BlockPos topSlab = tile.above(3 + topology.recipe().length());
        boolean[] injected = {false};
        try {
            SlabRigCommand.installCasePostUseHookForTests(source, definition.id(), () -> {
                BlockState state = world.getBlockState(topSlab);
                double stored = SlabAnchorAttachment.storedPlacementDy(world, topSlab);
                if (!(state.getBlock() instanceof SlabBlock) || Double.isNaN(stored)) {
                    throw h.assertionException(
                            "premise: SBSBS terminal S must be a stored slab before reset injection");
                }
                injected[0] = true;
                SlabAnchorAttachment.removeAnchor(world, topSlab);
            });
            try {
                if (tryExec(source, "slabrig cases " + page + " force") != 1 || !injected[0]) {
                    throw h.assertionException(
                            "target case page/hook must execute through the real command path");
                }
                JsonObject root = JsonParser.parseString(
                        SlabRigCommand.trackedCaseManifestJsonForTests(source)).getAsJsonObject();
                JsonObject targetRow = null;
                int postStable = 0;
                int postLawRed = 0;
                for (JsonElement element : root.getAsJsonArray("cases")) {
                    JsonObject row = element.getAsJsonObject();
                    if ("STABLE".equals(row.get("postActionStructureStatus").getAsString())) {
                        postStable++;
                    } else if ("LAW_RED".equals(
                            row.get("postActionStructureStatus").getAsString())) {
                        postLawRed++;
                    }
                    if (definition.id().equals(row.get("caseId").getAsString())) {
                        targetRow = row;
                    }
                }
                if (targetRow == null
                        || !root.get("frozenDyEnabled").getAsBoolean()
                        || root.getAsJsonObject("coverage").get("topologyLawReds").getAsInt() != 1
                        || postStable != 15 || postLawRed != 1
                        || !"FINALIZED_WITH_REDS".equals(root.get("status").getAsString())
                        || !"LAW_RED".equals(targetRow.get("structureStatus").getAsString())
                        || !"LAW_RED".equals(targetRow.get("postActionStructureStatus").getAsString())
                        || !targetRow.get("structureDetail").getAsString()
                        .contains("postActionTopologyChanged=")
                        || !targetRow.get("outcome").getAsString().startsWith("PLACED_")
                        || !targetRow.get("persistentSubjectPresent").getAsBoolean()
                        || targetRow.getAsJsonArray("actualStructureCells")
                        .equals(targetRow.getAsJsonArray("postActionStructureCells"))) {
                    throw h.assertionException(
                            "successful target placement must expose exactly the injected frozen-on reset: "
                                    + targetRow);
                }
                exec(h, source, "slabrig clear");
            } finally {
                SlabRigCommand.clearCasePostUseHookForTests(source);
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = savedFrozenMode;
        }
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigSameCountVanishIsNeverReportedAsRefusal(GameTestHelper h) {
        if (!"REFUSED_NO_CHANGE".equals(SlabRigCommand.classifyAbsentSubject(
                false, "minecraft:stone", 1, "minecraft:stone", 1, false))
                || !"PLACED_THEN_VANISHED".equals(SlabRigCommand.classifyAbsentSubject(
                true, "minecraft:stone", 1, "minecraft:stone", 1, false))
                || !"PLACED_THEN_VANISHED".equals(SlabRigCommand.classifyAbsentSubject(
                false, "minecraft:powder_snow_bucket", 1, "minecraft:bucket", 1, false))
                || !"PLACED_THEN_VANISHED".equals(SlabRigCommand.classifyAbsentSubject(
                false, "minecraft:stone", 1, "minecraft:stone", 1, true))) {
            throw h.assertionException(
                    "absent-subject truth table must independently cover action, transformation, and residuals");
        }
        ServerLevel world = h.getLevel();
        BlockPos survivalSupport = h.absolutePos(new BlockPos(1, 1, 1));
        BlockPos survivalTarget = survivalSupport.above();
        world.setBlock(survivalSupport, Blocks.STONE.defaultBlockState(), 3);
        Player survival = h.makeMockPlayer(GameType.SURVIVAL);
        boolean[] survivalPlaced = {false};
        SlabRigCommand.CasePlacementProbeForTests survivalProbe =
                SlabRigCommand.probeCasePlacementForTests(
                        world, survival, Items.POWDER_SNOW_BUCKET,
                        survivalSupport, survivalTarget, () -> {
                            if (!world.getBlockState(survivalTarget).is(Blocks.POWDER_SNOW)) {
                                throw h.assertionException(
                                        "premise: survival powder-snow bucket must place its subject before vanish");
                            }
                            survivalPlaced[0] = true;
                            world.setBlock(survivalTarget, Blocks.AIR.defaultBlockState(), 3);
                        });
        if (!survivalPlaced[0]
                || !"PLACED_THEN_VANISHED".equals(survivalProbe.outcome())
                || !survivalProbe.interactionConsumesAction()
                || survivalProbe.persistentSubjectPresent()
                || !"minecraft:powder_snow_bucket".equals(survivalProbe.stackItemBefore())
                || !"minecraft:bucket".equals(survivalProbe.stackItemAfter())
                || survivalProbe.stackBefore() != 1 || survivalProbe.stackAfter() != 1
                || survivalProbe.error() != null || survivalProbe.outsideEffect()) {
            throw h.assertionException(
                    "same-count survival item transformation must be an explicit vanished red: "
                            + survivalProbe);
        }

        BlockPos creativeSupport = h.absolutePos(new BlockPos(4, 1, 1));
        BlockPos creativeTarget = creativeSupport.above();
        world.setBlock(creativeSupport, Blocks.STONE.defaultBlockState(), 3);
        Player creative = h.makeMockPlayer(GameType.CREATIVE);
        // makeMockPlayer records the requested mode but direct ItemStack.useOn consults this flag.
        creative.getAbilities().instabuild = true;
        boolean[] creativePlaced = {false};
        SlabRigCommand.CasePlacementProbeForTests creativeProbe =
                SlabRigCommand.probeCasePlacementForTests(
                        world, creative, Items.STONE,
                        creativeSupport, creativeTarget, () -> {
                            BlockState placed = world.getBlockState(creativeTarget);
                            if (!placed.is(Blocks.STONE)) {
                                throw h.assertionException(
                                        "premise: creative block item must place its subject before vanish");
                            }
                            creativePlaced[0] = true;
                            SlabAnchorAttachment.capturePlacementDy(world, creativeTarget, placed);
                            world.setBlock(creativeTarget, Blocks.AIR.defaultBlockState(), 2);
                        });
        if (!creativePlaced[0]
                || !"PLACED_THEN_VANISHED".equals(creativeProbe.outcome())
                || !creativeProbe.interactionConsumesAction()
                || creativeProbe.persistentSubjectPresent()
                || !"minecraft:stone".equals(creativeProbe.stackItemBefore())
                || !"minecraft:stone".equals(creativeProbe.stackItemAfter())
                || creativeProbe.stackBefore() != 1 || creativeProbe.stackAfter() != 1
                || creativeProbe.observedChangeCount() < 1
                || creativeProbe.error() != null || creativeProbe.outsideEffect()) {
            throw h.assertionException(
                    "same-count creative consumed action with residual evidence must be a vanished red: "
                            + creativeProbe);
        }
        if (!world.getBlockState(survivalTarget).isAir()
                || !world.getBlockState(creativeTarget).isAir()
                || !Double.isNaN(SlabAnchorAttachment.storedPlacementDy(world, creativeTarget))) {
            throw h.assertionException("placement probe must exact-clean its subject/store evidence");
        }
        world.setBlock(survivalSupport, Blocks.AIR.defaultBlockState(), 3);
        world.setBlock(creativeSupport, Blocks.AIR.defaultBlockState(), 3);
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigTowerBuildsRigViaDispatcher(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        CommandSourceStack source = sourceFacingSouth(h, w);

        exec(h, source, "slabrig tower");

        Direction facing = SlabRigCommand.rigFacing(source);
        BlockPos base = SlabRigCommand.rigBase(source);
        if (facing != Direction.SOUTH) {
            throw h.assertionException("premise: expected SOUTH facing, got " + facing);
        }

        BlockPos slab1 = base.above(1);
        BlockPos slab3 = base.above(3);
        BlockPos fb = base.above(4);
        BlockPos support = fb.relative(facing.getCounterClockWise());

        // ── blocks exist ──
        assertBlock(h, w, base, Blocks.STONE.defaultBlockState(), "ground stone");
        assertBottomSlab(h, w, slab1, "carrier slab @+1");
        assertBlock(h, w, base.above(2), Blocks.STONE.defaultBlockState(), "carrier stone @+2");
        assertBottomSlab(h, w, slab3, "carrier slab @+3");
        assertBlock(h, w, fb, Blocks.STONE.defaultBlockState(), "compound full block");
        assertBottomSlab(h, w, support, "marked side slab");

        // ── attachments exist (the genuine Slabbed anchor authoring) ──
        if (!SlabAnchorAttachment.isAnchored(w, fb)) {
            throw h.assertionException("compound full block must be anchored");
        }
        if (!SlabAnchorAttachment.isCompoundFullBlockAnchor(w, fb)) {
            throw h.assertionException("compound full block must carry the compound-full-block anchor");
        }
        if (!SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(w, support, w.getBlockState(support))) {
            throw h.assertionException("marked side slab must carry the compound-visible-side-lower-slab marker");
        }

        // ── F8(a): the rig MEASURES -1.0 for both the FB and the marked slab (the sign's claim) ──
        assertOffset(h, w, fb, -1.0, "compound full block");
        assertOffset(h, w, support, -1.0, "marked side slab");

        h.succeed();
    }

    /**
     * F8(b): build {@code rows 2} via the dispatcher, assert the per-row sample supports MEASURE their
     * sign values (-0.5 / -1.0), then {@code clear} and assert the authored cells are air AND every
     * Slabbed attachment there died (isAnchored / isCompoundFullBlockAnchor /
     * isCompoundVisibleSideLowerSlab all false).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigRowsThenClear(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        // Feet near z=0 so the facing-SOUTH rig (rowB is base + facing*4) fits inside the 8x8x8 arena.
        CommandSourceStack source = rowsSourceFacingSouth(h, w);

        exec(h, source, "slabrig rows 1");

        Direction facing = SlabRigCommand.rigFacing(source);
        BlockPos base = SlabRigCommand.rigBase(source);

        // Row A sample: TOP slab of the deep lowered-slab stack (base.above(3)) reads -0.5.
        BlockPos rowASample = base.above(3);
        assertOffset(h, w, rowASample, -0.5, "Row A support");

        // Row B sample: first compound tower's FB (base + facing*4, +4) reads -1.0.
        BlockPos rowBStart = base.relative(facing, 4);
        BlockPos rowBFb = rowBStart.above(4);
        BlockPos rowBSupport = rowBFb.relative(facing.getCounterClockWise());
        assertOffset(h, w, rowBFb, -1.0, "Row B support");
        if (!SlabAnchorAttachment.isAnchored(w, rowBFb)) {
            throw h.assertionException("premise: Row B FB must be anchored after build");
        }

        exec(h, source, "slabrig clear");

        // Authored cells are air.
        assertAir(h, w, base, "Row A ground after clear");
        assertAir(h, w, rowASample, "Row A stone after clear");
        assertAir(h, w, rowBFb, "Row B FB after clear");
        assertAir(h, w, rowBSupport, "Row B marked slab after clear");

        // Attachments died at the previously-authored cells.
        if (SlabAnchorAttachment.isAnchored(w, rowBFb)) {
            throw h.assertionException("Row B FB must NOT be anchored after clear");
        }
        if (SlabAnchorAttachment.isCompoundFullBlockAnchor(w, rowBFb)) {
            throw h.assertionException("Row B FB must NOT carry the compound-full-block anchor after clear");
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(w, rowBSupport, w.getBlockState(rowBSupport))) {
            throw h.assertionException("Row B marked slab must NOT carry the compound-visible marker after clear");
        }

        h.succeed();
    }

    /**
     * F8(c): footprint refusal. Place one stone in the would-be footprint, run {@code rows 2} WITHOUT
     * force → refused (the stone is untouched, no rig authored); then {@code rows 2 force} → built.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigRowsRefusesOccupiedFootprintUnlessForced(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        CommandSourceStack source = rowsSourceFacingSouth(h, w);

        Direction facing = SlabRigCommand.rigFacing(source);
        BlockPos base = SlabRigCommand.rigBase(source);

        // An intruder full block inside the footprint (one cell above the Row A ground). Row A would
        // author a slab here; the scan must see it and refuse.
        BlockPos intruder = base.above(1);
        w.setBlock(intruder, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        SlabAnchorAttachment.capturePlacementDy(w, intruder, w.getBlockState(intruder));
        double intruderStoredBefore = SlabAnchorAttachment.storedPlacementDy(w, intruder);
        if (Double.isNaN(intruderStoredBefore)) {
            throw h.assertionException("premise: occupied intruder must carry stored dy before refusal");
        }

        // WITHOUT force → refusal: the command returns 0. The intruder must be UNTOUCHED and no rig
        // authored (the Row B FB cell stays air).
        BlockPos rowBFb = base.relative(facing, 4).above(4);
        int refusedResult = tryExec(source, "slabrig rows 1");
        if (!w.getBlockState(intruder).is(Blocks.DIAMOND_BLOCK)) {
            throw h.assertionException("refusal must NOT overwrite the intruder (found "
                    + w.getBlockState(intruder) + ")");
        }
        if (!w.getBlockState(rowBFb).isAir()) {
            throw h.assertionException("refusal must NOT author any rig (Row B FB should be air, found "
                    + w.getBlockState(rowBFb) + ")");
        }
        double intruderStoredAfter = SlabAnchorAttachment.storedPlacementDy(w, intruder);
        if (Double.doubleToLongBits(intruderStoredAfter) != Double.doubleToLongBits(intruderStoredBefore)) {
            throw h.assertionException("refusal must preserve the intruder's stored dy byte-identically; before="
                    + intruderStoredBefore + " after=" + intruderStoredAfter);
        }
        if (!"none".equals(SlabRigCommand.trackedManifestStatus(source))) {
            throw h.assertionException("occupied non-force refusal must not install a manifest");
        }
        if (refusedResult > 0) {
            throw h.assertionException("rows without force over an occupied footprint must not report success");
        }

        // WITH force → builds (overwrites the intruder). Row B FB now reads -1.0.
        exec(h, source, "slabrig rows 1 force");
        assertOffset(h, w, rowBFb, -1.0, "Row B support after force");

        h.succeed();
    }

    // ── harness ────────────────────────────────────────────────────────────────

    /** Synchronous state sentinel for the RIG-3B1 export-only command. */
    private static String noWorldFingerprint(GameTestHelper h, ServerLevel world,
                                             CommandSourceStack source) {
        StringBuilder out = new StringBuilder(32_768);
        out.append("manifest=").append(SlabRigCommand.trackedManifestStatus(source));
        out.append("|gameTime=").append(world.getGameTime());
        out.append("|scheduledTicks=").append(world.getBlockTicks().count()).append(',')
                .append(world.getFluidTicks().count());
        var weather = world.getWeatherData();
        out.append("|weather=").append(weather.getClearWeatherTime()).append(',')
                .append(weather.isRaining()).append(',').append(weather.getRainTime()).append(',')
                .append(weather.isThundering()).append(',').append(weather.getThunderTime());

        world.getGameRules().availableRules()
                .sorted(Comparator.comparing(rule -> rule.getIdentifierWithFallback().toString()))
                .forEach(rule -> out.append("|rule=").append(rule.getIdentifierWithFallback())
                        .append('=').append(world.getGameRules().getAsString(rule)));

        for (int y = 0; y < 8; y++) {
            for (int z = 0; z < 8; z++) {
                for (int x = 0; x < 8; x++) {
                    BlockPos pos = h.absolutePos(new BlockPos(x, y, z));
                    BlockState state = world.getBlockState(pos);
                    out.append("|block=").append(x).append(',').append(y).append(',').append(z)
                            .append(':').append(state)
                            .append(":dyBits=").append(Long.toHexString(Double.doubleToLongBits(
                                    SlabAnchorAttachment.storedPlacementDy(world, pos))))
                            .append(":markers=")
                            .append(SlabAnchorAttachment.isFrozenFlat(world, pos)).append(',')
                            .append(SlabAnchorAttachment.isAnchored(world, pos)).append(',')
                            .append(SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)).append(',')
                            .append(SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)).append(',')
                            .append(SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)).append(',')
                            .append(SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)).append(',')
                            .append(SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)).append(',')
                            .append(SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state));
                    var blockEntity = world.getBlockEntity(pos);
                    if (blockEntity != null) {
                        out.append(":be=").append(BuiltInRegistries.BLOCK_ENTITY_TYPE
                                .getKey(blockEntity.getType())).append(':')
                                .append(blockEntity.saveWithFullMetadata(world.registryAccess()));
                    }
                }
            }
        }

        List<String> entities = new ArrayList<>();
        for (Entity entity : world.getAllEntities()) {
            entities.add(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) + "@"
                    + entity.getUUID() + "@" + entity.position() + "@aabb=" + entity.getBoundingBox()
                    + "@rot=" + entity.getYRot() + ',' + entity.getXRot());
        }
        entities.sort(String::compareTo);
        for (String entity : entities) {
            out.append("|entity=").append(entity);
        }
        return out.toString();
    }

    private static CommandSourceStack sourceFacingSouth(GameTestHelper h, ServerLevel w) {
        // Source at a known cell facing SOUTH (yaw 0 -> Direction.fromYRot(0) == SOUTH), with full
        // permissions so the GAMEMASTERS gate passes. Feet at (1,2,1) relative -> rig base = feet.below()
        // three blocks south = (1,1,4), inside the empty structure's bounds.
        return playerSourceAt(w, h.makeMockPlayer(GameType.SURVIVAL),
                h.absolutePos(new BlockPos(1, 2, 1)));
    }

    private static CommandSourceStack rowsSourceFacingSouth(GameTestHelper h, ServerLevel w) {
        // Feet at (1,2,0) -> base = (1,1,3); rowB = base + facing*4 = (1,1,7); tower top +4 = y5, +2
        // headroom = y7. Whole facing-SOUTH rows-1 footprint stays inside the 8x8x8 empty arena.
        return playerSourceAt(w, h.makeMockPlayer(GameType.SURVIVAL),
                h.absolutePos(new BlockPos(1, 2, 0)));
    }

    private static CommandSourceStack sourceAt(ServerLevel w, BlockPos feet) {
        return w.getServer().createCommandSourceStack()
                .withLevel(w)
                .withPosition(Vec3.atBottomCenterOf(feet))
                .withRotation(new Vec2(0.0f, 0.0f))
                .withPermission(PermissionSet.ALL_PERMISSIONS);
    }

    private static CommandSourceStack playerSourceAt(ServerLevel w, Player player, BlockPos feet) {
        return sourceAt(w, feet).withEntity(player);
    }

    private static void exec(GameTestHelper h, CommandSourceStack source, String command) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        SlabRigCommand.register(dispatcher);
        try {
            dispatcher.execute(command, source);
        } catch (Exception e) {
            throw h.assertionException("/" + command + " threw: " + e);
        }
    }

    /** Executes a command expected to possibly refuse; returns -1 only for parse/dispatch exceptions. */
    private static int tryExec(CommandSourceStack source, String command) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        SlabRigCommand.register(dispatcher);
        return tryExec(dispatcher, source, command);
    }

    private static int tryExec(CommandDispatcher<CommandSourceStack> dispatcher,
                               CommandSourceStack source, String command) {
        try {
            return dispatcher.execute(command, source);
        } catch (Exception e) {
            return -1;
        }
    }

    private static int pageForItem(SlabRigCaseCatalog.Snapshot snapshot, String itemId,
                                   int topologyIndex) {
        int itemIndex = snapshot.items().stream()
                .filter(item -> item.id().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("runtime item missing: " + itemId))
                .index();
        int itemGroup = itemIndex / SlabRigCaseCatalog.PAGE_GRID_SIDE;
        int topologyGroup = topologyIndex / SlabRigCaseCatalog.PAGE_GRID_SIDE;
        int topologyGroups = snapshot.topologies().size() / SlabRigCaseCatalog.PAGE_GRID_SIDE;
        return itemGroup * topologyGroups + topologyGroup + 1;
    }

    private static void assertCaseManifestLimits(GameTestHelper h, String json, int page, int pageCount) {
        if (json == null) {
            throw h.assertionException("cases command must retain its final canonical page manifest");
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject coverage = root.getAsJsonObject("coverage");
            if (!root.get("status").getAsString().startsWith("FINALIZED")
                    || root.get("page").getAsInt() != page
                    || root.get("pageCount").getAsInt() != pageCount
                    || !"ABSENT_PROXY_DIAGNOSTIC_ONLY".equals(root.get("playerProof").getAsString())
                    || !"OUT_OF_SCOPE_RIG2_V1_SEPARATE_PASS".equals(root.get("hangingCoverage").getAsString())
                    || !root.get("runtimeContentSha256").getAsString().matches("[0-9a-f]{64}")
                    || coverage.get("topologyLawReds").getAsInt() < 1
                    || coverage.get("placedThenVanished").getAsInt() != 0
                    || coverage.get("planned").getAsInt() != 0
                    || coverage.get("errors").getAsInt() != 0
                    || coverage.get("interrupted").getAsInt() != 0
                    || coverage.get("proxyExecuted").getAsInt()
                    + coverage.get("deferred").getAsInt() != 16
                    || coverage.get("playerAuthoredPaired").getAsInt() != 0) {
                throw h.assertionException("case manifest blurred proxy/page/hanging proof limits: " + json);
            }
        } catch (RuntimeException e) {
            throw h.assertionException("cases command emitted invalid JSON: " + e.getMessage());
        }
    }

    private static void assertMultiCellCase(GameTestHelper h, String json, String itemId) {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw h.assertionException("invalid case manifest JSON: " + e.getMessage());
        }
        JsonArray cases = root.getAsJsonArray("cases");
        for (JsonElement element : cases) {
            JsonObject row = element.getAsJsonObject();
            if (itemId.equals(row.get("itemId").getAsString())) {
                if (!"PLACED_MULTI_CELL".equals(row.get("outcome").getAsString())
                        || row.getAsJsonArray("actualChanges").size() < 2) {
                    throw h.assertionException(itemId + " must record every multi-cell effect: " + row);
                }
                return;
            }
        }
        throw h.assertionException("page manifest missing expected case item " + itemId);
    }

    private static void assertTrackedRigInsideStructure(GameTestHelper h, CommandSourceStack source) {
        for (BlockPos pos : SlabRigCommand.trackedReservedCellsForTests(source)) {
            if (!h.getBounds().contains(Vec3.atCenterOf(pos))) {
                throw h.assertionException("RIG-2 reserved cell escaped its isolated 36x16x36 fixture: " + pos);
            }
        }
    }

    private static void assertGuardContext(GameTestHelper h, String json, BlockPos nonAir, BlockPos hauntedAir) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.getAsJsonObject("coverage").get("externalGuardCells").getAsInt() < 2) {
            throw h.assertionException("forced board must disclose non-air and haunted-air guard context");
        }
        String nonAirKey = nonAir.getX() + "," + nonAir.getY() + "," + nonAir.getZ();
        String hauntedKey = hauntedAir.getX() + "," + hauntedAir.getY() + "," + hauntedAir.getZ();
        boolean sawNonAir = false;
        boolean sawHaunted = false;
        for (JsonElement caseElement : root.getAsJsonArray("cases")) {
            for (JsonElement contextElement : caseElement.getAsJsonObject()
                    .getAsJsonArray("externalGuardContext")) {
                JsonObject context = contextElement.getAsJsonObject();
                if (nonAirKey.equals(context.get("pos").getAsString())
                        && context.get("state").getAsString().contains("diamond_block")) {
                    sawNonAir = true;
                }
                if (hauntedKey.equals(context.get("pos").getAsString())
                        && context.get("state").getAsString().contains("air")
                        && !"NaN".equals(context.get("storedDy").getAsString())) {
                    sawHaunted = true;
                }
            }
        }
        if (!sawNonAir || !sawHaunted) {
            throw h.assertionException("guard context manifest omitted exact non-air/haunted fixtures");
        }
    }

    private static byte[] readRequiredBytes(GameTestHelper h, java.nio.file.Path path, String label) {
        try {
            if (!java.nio.file.Files.isRegularFile(path)) {
                throw h.assertionException(label + " missing at " + path);
            }
            return java.nio.file.Files.readAllBytes(path);
        } catch (java.io.IOException e) {
            throw h.assertionException(label + " could not be read: " + e.getMessage());
        }
    }

    private static void assertParses(GameTestHelper h, CommandSourceStack source, String command) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        SlabRigCommand.register(dispatcher);
        var parsed = dispatcher.parse(command, source);
        if (parsed.getReader().canRead() || !parsed.getExceptions().isEmpty()) {
            throw h.assertionException("legacy command form must remain parseable: /" + command
                    + " remaining='" + parsed.getReader().getRemaining() + "'");
        }
    }

    private static void assertOffset(GameTestHelper h, ServerLevel w, BlockPos pos, double expected, String label) {
        double got = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        if (Math.abs(got - expected) > EPS) {
            throw h.assertionException(label + " @" + pos.toShortString() + " must read " + expected
                    + " but read " + got);
        }
    }

    private static void assertAir(GameTestHelper h, ServerLevel w, BlockPos pos, String label) {
        if (!w.getBlockState(pos).isAir()) {
            throw h.assertionException(label + " must be air at " + pos.toShortString() + " (found "
                    + w.getBlockState(pos) + ")");
        }
    }

    private static void assertBlock(GameTestHelper h, ServerLevel w, BlockPos pos, BlockState expected, String label) {
        BlockState actual = w.getBlockState(pos);
        if (!actual.is(expected.getBlock())) {
            throw h.assertionException(label + " missing at " + pos.toShortString() + " (found " + actual + ")");
        }
    }

    private static void assertBottomSlab(GameTestHelper h, ServerLevel w, BlockPos pos, String label) {
        BlockState actual = w.getBlockState(pos);
        if (!(actual.getBlock() instanceof SlabBlock) || actual.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
            throw h.assertionException(label + " must be a bottom slab at " + pos.toShortString() + " (found " + actual + ")");
        }
    }
}
