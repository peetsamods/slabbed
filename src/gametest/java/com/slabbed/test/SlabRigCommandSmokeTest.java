package com.slabbed.test;

import com.mojang.brigadier.CommandDispatcher;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.command.SlabRigCommand;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

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
        return sourceAt(w, h.absolutePos(new BlockPos(1, 2, 1)));
    }

    private static CommandSourceStack rowsSourceFacingSouth(GameTestHelper h, ServerLevel w) {
        // Feet at (1,2,0) -> base = (1,1,3); rowB = base + facing*4 = (1,1,7); tower top +4 = y5, +2
        // headroom = y7. Whole facing-SOUTH rows-1 footprint stays inside the 8x8x8 empty arena.
        return sourceAt(w, h.absolutePos(new BlockPos(1, 2, 0)));
    }

    private static CommandSourceStack sourceAt(ServerLevel w, BlockPos feet) {
        return w.getServer().createCommandSourceStack()
                .withLevel(w)
                .withPosition(Vec3.atBottomCenterOf(feet))
                .withRotation(new Vec2(0.0f, 0.0f))
                .withPermission(PermissionSet.ALL_PERMISSIONS);
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

    /** Executes a command expected to possibly refuse; returns its result (or 0 if it threw). */
    private static int tryExec(CommandSourceStack source, String command) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        SlabRigCommand.register(dispatcher);
        try {
            return dispatcher.execute(command, source);
        } catch (Exception e) {
            return 0;
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
