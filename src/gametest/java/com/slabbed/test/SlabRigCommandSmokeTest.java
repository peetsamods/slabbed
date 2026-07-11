package com.slabbed.test;

import com.mojang.brigadier.CommandDispatcher;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.command.SlabRigCommand;
import com.slabbed.util.LiveCursorIntentRecorder;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
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
                "slabrig mega 1 force", "slabrig platform", "slabrig clear"}) {
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
        try {
            return dispatcher.execute(command, source);
        } catch (Exception e) {
            return -1;
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
