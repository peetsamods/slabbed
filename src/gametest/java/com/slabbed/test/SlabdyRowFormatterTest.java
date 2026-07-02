package com.slabbed.test;

import com.slabbed.dev.SlabdyRowFormatter;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Headless proof for {@code /slabdy row}'s field computation ({@link SlabdyRowFormatter}).
 * The live wiring (reading the real crosshair target, printing to chat) is client-only and
 * therefore live-only — this only proves the formatter produces the right numbers for a
 * given world/pos/state, which is exactly what a tester would be reading off the dump.
 */
public final class SlabdyRowFormatterTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rowReportsFlushBlockAsZeroDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        String row = SlabdyRowFormatter.formatRow(
                w, pos, w.getBlockState(pos), Direction.UP, new Vec3d(0.5, 1.0, 0.5), ItemStack.EMPTY);

        ctx.assertTrue(row.contains("worldDy=0.000"), "flat stone on the ground must report worldDy=0.000: " + row);
        ctx.assertTrue(row.contains("support=none"), "a non-lowered block must report support=none: " + row);
        ctx.assertTrue(row.contains("held=empty"), "an empty held item must report held=empty: " + row);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rowReportsFenceLoweredOnVanillaSlab(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(slabPos, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos fencePos = slabPos.up();
        w.setBlockState(fencePos, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

        String row = SlabdyRowFormatter.formatRow(
                w, fencePos, w.getBlockState(fencePos), Direction.NORTH, new Vec3d(3.5, 2.6, 3.0),
                new ItemStack(Blocks.OAK_FENCE.asItem()));

        ctx.assertTrue(row.contains("worldDy=-0.500"),
                "a fence lowered onto a vanilla bottom slab must report worldDy=-0.500: " + row);
        ctx.assertTrue(row.contains("face=north"), "the reported face must match what was passed in: " + row);
        ctx.assertTrue(row.contains("held=minecraft:oak_fence"), "the held item must be reported by id: " + row);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void rowHandlesMissingHitVec(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        String row = SlabdyRowFormatter.formatRow(w, pos, w.getBlockState(pos), Direction.UP, null, null);

        ctx.assertTrue(row.contains("hit=none"), "a null hit vector must degrade to hit=none, not crash: " + row);
        ctx.assertTrue(row.contains("held=empty"), "a null held item must degrade to held=empty, not crash: " + row);
        ctx.complete();
    }
}
