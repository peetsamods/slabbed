package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.HangingSeatDyHolder;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.decoration.painting.PaintingVariants;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.test.TestContext;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

/** Real entity NBT restoration must not synchronously request a pending chunk (LAW 1). */
public final class HangingLoadDeferralTest {
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void savedFrameKeepsSeatWithoutLoadingChunks(TestContext ctx) { check(ctx, false, true); }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void legacyFrameDefersAndMintsOnce(TestContext ctx) { check(ctx, false, false); }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void savedWidePaintingKeepsSeatWithoutLoadingChunks(TestContext ctx) { check(ctx, true, true); }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void legacyWidePaintingDefersAndMintsOnce(TestContext ctx) { check(ctx, true, false); }

    private static void check(TestContext ctx, boolean painting, boolean savedSeat) {
        ServerWorld world = ctx.getWorld();
        BlockPos wall = ctx.getAbsolutePos(new BlockPos(3, 4, 3));
        world.setBlockState(wall.down(), Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        world.setBlockState(wall, Blocks.OAK_PLANKS.getDefaultState());
        SlabAnchorAttachment.addAnchor(world, wall, world.getBlockState(wall));
        world.setBlockState(wall.down(), Blocks.AIR.getDefaultState());
        BlockPos attachment = wall.offset(Direction.NORTH);
        AbstractDecorationEntity original = painting
                ? new PaintingEntity(world, attachment, Direction.NORTH,
                    world.getRegistryManager().getOrThrow(RegistryKeys.PAINTING_VARIANT).getOrThrow(PaintingVariants.POINTER))
                : new ItemFrameEntity(world, attachment, Direction.NORTH);
        ctx.assertTrue(Math.abs(((HangingSeatDyHolder) original).slabbed$hangSeatDy() + 0.5) < 1.0e-6,
                "premise: original decoration must be lowered");
        Box originalBox = original.getBoundingBox();
        NbtWriteView output = NbtWriteView.create(ErrorReporter.EMPTY, world.getRegistryManager());
        original.saveData(output);
        NbtCompound nbt = output.getNbt();
        if (!savedSeat) nbt.remove("slabbed:hang_dy");
        AbstractDecorationEntity restored = painting
                ? EntityType.PAINTING.create(world, SpawnReason.LOAD)
                : EntityType.ITEM_FRAME.create(world, SpawnReason.LOAD);
        ctx.assertTrue(restored != null, "premise: restored entity exists");
        PendingHangChunkProbe.begin(world.getChunkManager());
        try {
            restored.readData(NbtReadView.create(ErrorReporter.EMPTY, world.getRegistryManager(), nbt));
            ctx.assertTrue(PendingHangChunkProbe.blockingReads == 0,
                    "NBT restoration entered the blocking chunk API " + PendingHangChunkProbe.blockingReads + " times");
            ctx.assertTrue(PendingHangChunkProbe.nonblockingReads > 0,
                    "premise: pending-chunk guard was exercised");
            ctx.assertTrue(((HangingSeatDyHolder) restored).slabbed$hasHangSeat() == savedSeat,
                    "pending load must preserve a saved seat and defer an absent seat");
        } finally {
            PendingHangChunkProbe.end();
        }
        restored.tick();
        ctx.assertTrue(Math.abs(((HangingSeatDyHolder) restored).slabbed$hangSeatDy() + 0.5) < 1.0e-6,
                "ready tick must restore or mint the lowered seat");
        ctx.assertTrue(Math.abs(restored.getBoundingBox().minY - originalBox.minY) < 1.0e-6,
                "deferred layout must match the original lowered box");
        world.setBlockState(wall, Blocks.AIR.getDefaultState());
        world.setBlockState(wall, Blocks.OAK_PLANKS.getDefaultState());
        restored.tick();
        ctx.assertTrue(Math.abs(((HangingSeatDyHolder) restored).slabbed$hangSeatDy() + 0.5) < 1.0e-6,
                "later support changes must not remint the decoration seat");
        ctx.assertTrue(Math.abs(restored.getBoundingBox().minY - originalBox.minY) < 1.0e-6,
                "later ticks must not apply the height twice");
        System.out.println("[HANG_LOAD_PROOF] saved=" + savedSeat + " painting=" + painting + " blocking=0 seat=-0.5 PASS");
        ctx.complete();
    }
}
