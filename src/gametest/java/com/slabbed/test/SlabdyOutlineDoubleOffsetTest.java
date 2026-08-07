package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.dev.SlabdyRowFormatter;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Live-reported bug (2026-07-04): a hanging-lantern gap investigation kept finding an
 * "internally inconsistent" block — {@code /slabdy}'s on-screen overlay showed {@code dy=-0.500}
 * in text but {@code outline=[...,-1.000,... -> ...,0.000,...]} for the SAME block at the SAME
 * instant, a full -1.0 shift instead of -0.5.
 *
 * <p>Root cause: {@code SlabdyRowFormatter.outlineAt} applied {@code dy} to
 * {@code state.getOutlineShape(world, pos)} — but {@code SlabSupportStateMixin.slabbed$offsetOutline}
 * ALREADY shifts every {@code getOutlineShape} call by {@code getVisualYOffset} (that is the whole
 * point of the mixin: the outline/hitbox tracks the model). So the overlay was applying the SAME
 * dy a SECOND time on top of an already-shifted shape — a genuine double-offset bug in the
 * diagnostic tool itself, NOT in the actual game rendering (the real render path,
 * {@code OffsetBlockStateModel}, is unaffected and applies dy exactly once).
 *
 * <p>This means the "internally inconsistent anchor" findings from this session's lantern-gap
 * investigation were reading a broken diagnostic display, not a real dy inconsistency — corrected
 * in KNOWN_INCOMPLETE.md. The underlying visible gap the maintainer reported remains real and unexplained
 * by this fix; this fix only stops the debug tool from lying about it.
 */
public final class SlabdyOutlineDoubleOffsetTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void overlayOutlineMatchesSingleDyShiftNotDouble(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos dirtPos = slabPos.up();

        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtPos, w.getBlockState(dirtPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, dirtPos), "setup: dirt must anchor on the bottom slab");

        List<String> lines = SlabdyRowFormatter.formatRow(w, dirtPos, w.getBlockState(dirtPos),
                Direction.UP, new Vec3d(dirtPos.getX() + 0.5, dirtPos.getY() + 0.5, dirtPos.getZ() + 0.5),
                ItemStack.EMPTY, "missing", null);

        String outlineLine = lines.stream().filter(l -> l.contains("outlineMinY=")).findFirst().orElse("");
        ctx.assertTrue(outlineLine.contains("outlineMinY=-0.500"),
                "THE FIX: an anchored dirt at dy=-0.500 must show outlineMinY=-0.500 (a SINGLE "
                        + "offset, matching the native full-cube shape [0,0,0]->[1,1,1] shifted "
                        + "once) -- not -1.000 (a double-applied offset). Got: " + outlineLine);
        ctx.complete();
    }
}
