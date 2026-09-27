package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.decoration.painting.PaintingVariants;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.List;
import java.util.Map;

/**
 * Real entity save-data restoration must not synchronously request a pending chunk (LAW.md): a saved
 * seat is restored during the read, a missing seat is minted once from the decoration's own wall when
 * its chunks are ready, and the box carries the seat exactly once.
 */
public final class HangingLoadDeferralTest {
    private static final double EPS = 1.0e-6d;

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void savedFrameKeepsSeatWithoutLoadingChunks(TestContext ctx) { check(ctx, false, true); }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void legacyFrameDefersAndMintsOnce(TestContext ctx) { check(ctx, false, false); }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void savedWidePaintingKeepsSeatWithoutLoadingChunks(TestContext ctx) { check(ctx, true, true); }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void legacyWidePaintingDefersAndMintsOnce(TestContext ctx) { check(ctx, true, false); }

    /**
     * Pins the reading flag: the layouts that run inside the read still see the default facing, so a
     * mint there would read the cell on the opposite side of the attachment. That cell carries its own
     * stored height here; the seat must come from the frame's real wall.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void legacyFrameMintsFromItsOwnWallAfterTheRead(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wall = ctx.getAbsolutePos(new BlockPos(3, 4, 3)).toImmutable();
        storedWall(ctx, world, wall, -0.5d);
        BlockPos attachment = wall.offset(Direction.NORTH);
        storedWall(ctx, world, attachment.offset(Direction.NORTH), -0.25d);
        ItemFrameEntity original = new ItemFrameEntity(world, attachment, Direction.NORTH);
        ctx.assertTrue(Math.abs(((HangingSeatDyHolder) original).slabbed$hangSeatDy() + 0.5d) < EPS,
                "premise: original frame must be lowered from its real wall");
        Box originalBox = original.getBoundingBox();
        NbtCompound nbt = new NbtCompound();
        original.writeNbt(nbt);
        ctx.assertTrue(nbt.contains("slabbed:frame_dy", 99), "premise: the original must save its seat");
        nbt.remove("slabbed:frame_dy");
        ItemFrameEntity restored = new ItemFrameEntity(EntityType.ITEM_FRAME, world);
        restored.readNbt(nbt);
        double seat = ((HangingSeatDyHolder) restored).slabbed$hangSeatDy();
        ctx.assertTrue(Math.abs(seat + 0.5d) < EPS,
                "a seat-less frame must mint from its own wall (-0.5), not the cell opposite it; got " + seat);
        ctx.assertTrue(Math.abs(restored.getBoundingBox().minY - originalBox.minY) < EPS,
                "the minted layout must match the original lowered box");
        ctx.complete();
    }

    /** On this line a hung seat is minted only from a cell with a stored height AND modern provenance. */
    private static void storedWall(TestContext ctx, ServerWorld world, BlockPos pos, double dy) {
        world.setBlockState(pos, Blocks.OAK_PLANKS.getDefaultState(), Block.NOTIFY_ALL);
        int writes = SlabAnchorAttachment.writePlacementDyBatch(world, Map.of(pos, Double.doubleToRawLongBits(dy)));
        int marks = SlabAnchorAttachment.markPostPolicyPlacements(world, List.of(pos));
        ctx.assertTrue(writes == 1 && marks == 1 && SlabAnchorAttachment.usesFrozenPlacementHeight(world, pos),
                "premise: the wall must carry a stored height and provenance; writes=" + writes + " marks=" + marks);
    }

    private static void check(TestContext ctx, boolean painting, boolean savedSeat) {
        ServerWorld world = ctx.getWorld();
        BlockPos wall = ctx.getAbsolutePos(new BlockPos(3, 4, 3)).toImmutable();
        storedWall(ctx, world, wall, -0.5d);
        BlockPos attachment = wall.offset(Direction.NORTH);
        AbstractDecorationEntity original = painting
                ? new PaintingEntity(world, attachment, Direction.NORTH,
                    world.getRegistryManager().get(RegistryKeys.PAINTING_VARIANT).entryOf(PaintingVariants.POINTER))
                : new ItemFrameEntity(world, attachment, Direction.NORTH);
        ctx.assertTrue(Math.abs(((HangingSeatDyHolder) original).slabbed$hangSeatDy() + 0.5d) < EPS,
                "premise: original decoration must be lowered");
        Box originalBox = original.getBoundingBox();
        String key = painting ? "slabbed:hang_dy" : "slabbed:frame_dy";
        NbtCompound nbt = new NbtCompound();
        original.writeNbt(nbt);
        ctx.assertTrue(nbt.contains(key, 99), "premise: the original must save its seat under " + key);
        if (!savedSeat) {
            nbt.remove(key);
        }
        AbstractDecorationEntity restored = painting
                ? new PaintingEntity(EntityType.PAINTING, world)
                : new ItemFrameEntity(EntityType.ITEM_FRAME, world);
        PendingHangChunkProbe.begin(world.getChunkManager());
        try {
            restored.readNbt(nbt);
            ctx.assertTrue(PendingHangChunkProbe.blockingReads == 0,
                    "save-data restoration entered the blocking chunk API " + PendingHangChunkProbe.blockingReads + " times");
            ctx.assertTrue(PendingHangChunkProbe.nonblockingReads > 0, "premise: pending-chunk guard was exercised");
            ctx.assertTrue(((HangingSeatDyHolder) restored).slabbed$hasHangSeat() == savedSeat,
                    "pending load must preserve a saved seat and defer an absent seat");
        } finally {
            PendingHangChunkProbe.end();
        }
        restored.tick();
        ctx.assertTrue(Math.abs(((HangingSeatDyHolder) restored).slabbed$hangSeatDy() + 0.5d) < EPS,
                "ready tick must restore or mint the lowered seat");
        ctx.assertTrue(Math.abs(restored.getBoundingBox().minY - originalBox.minY) < EPS,
                "deferred layout must match the original lowered box");
        world.setBlockState(wall, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(wall, Blocks.OAK_PLANKS.getDefaultState(), Block.NOTIFY_ALL);
        restored.tick();
        ctx.assertTrue(Math.abs(((HangingSeatDyHolder) restored).slabbed$hangSeatDy() + 0.5d) < EPS,
                "later support changes must not remint the decoration seat");
        ctx.assertTrue(Math.abs(restored.getBoundingBox().minY - originalBox.minY) < EPS,
                "later ticks must not apply the height twice");
        System.out.println("[HANG_LOAD_PROOF] saved=" + savedSeat + " painting=" + painting + " blocking=0 seat=-0.5 PASS");
        ctx.complete();
    }
}
