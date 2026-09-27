package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.HangingSeatDyHolder;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariants;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Restoring a hung decoration's data must never synchronously request a chunk that may still be
 * loading.
 *
 * <p>Rows: a frame and a wide painting, each with a saved seat and without one (an old save). While
 * {@link PendingHangChunkProbe} makes every chunk look unfinished, restoration must make no blocking
 * chunk request, must keep a saved seat and must defer a missing one. The first ready tick then
 * restores or mints the lowered seat with the original box, and a later rebuild of the wall behind
 * it neither re-mints the seat nor shifts the box a second time.
 */
public final class HangingLoadDeferralTest {
    private static final double EPS = 1.0e-6d;

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void savedFrameKeepsSeatWithoutLoadingChunks(GameTestHelper helper) { check(helper, false, true); }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void legacyFrameDefersAndMintsOnce(GameTestHelper helper) { check(helper, false, false); }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void savedWidePaintingKeepsSeatWithoutLoadingChunks(GameTestHelper helper) { check(helper, true, true); }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void legacyWidePaintingDefersAndMintsOnce(GameTestHelper helper) { check(helper, true, false); }

    private static double seatOf(Object entity) {
        return ((HangingSeatDyHolder) entity).slabbed$hangSeatDy();
    }

    private static void check(GameTestHelper helper, boolean painting, boolean savedSeat) {
        ServerLevel level = helper.getLevel();
        BlockPos wallRel = new BlockPos(3, 4, 3);
        helper.setBlock(wallRel.below(), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        helper.setBlock(wallRel, Blocks.OAK_PLANKS.defaultBlockState());
        BlockPos wall = helper.absolutePos(wallRel);
        SlabAnchorAttachment.addAnchor(level, wall, level.getBlockState(wall));
        helper.setBlock(wallRel.below(), Blocks.AIR.defaultBlockState());
        BlockPos attachment = wall.relative(Direction.NORTH);
        HangingEntity original = painting
                ? new Painting(level, attachment, Direction.NORTH,
                    level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).getOrThrow(PaintingVariants.POINTER))
                : new ItemFrame(level, attachment, Direction.NORTH);
        if (Math.abs(seatOf(original) + 0.5d) > EPS) {
            throw helper.assertionException(wallRel, "premise: original decoration must be lowered, got " + seatOf(original));
        }
        AABB originalBox = original.getBoundingBox();
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        original.saveWithoutId(output);
        CompoundTag saved = output.buildResult();
        if (!savedSeat) {
            saved.remove("slabbed:hang_dy");
        }
        HangingEntity restored = painting
                ? EntityTypes.PAINTING.create(level, EntitySpawnReason.LOAD)
                : EntityTypes.ITEM_FRAME.create(level, EntitySpawnReason.LOAD);
        if (restored == null) {
            throw helper.assertionException("premise: restored entity exists");
        }
        PendingHangChunkProbe.begin(level.getChunkSource());
        try {
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
            if (PendingHangChunkProbe.blockingReads != 0) {
                throw helper.assertionException(wallRel, "NBT restoration entered the blocking chunk API "
                        + PendingHangChunkProbe.blockingReads + " times");
            }
            if (PendingHangChunkProbe.nonblockingReads <= 0) {
                throw helper.assertionException(wallRel, "premise: pending-chunk guard was exercised");
            }
            if (((HangingSeatDyHolder) restored).slabbed$hasHangSeat() != savedSeat) {
                throw helper.assertionException(wallRel, "pending load must preserve a saved seat and defer an absent seat");
            }
        } finally {
            PendingHangChunkProbe.end();
        }
        restored.tick();
        if (Math.abs(seatOf(restored) + 0.5d) > EPS) {
            throw helper.assertionException(wallRel, "ready tick must restore or mint the lowered seat, got " + seatOf(restored));
        }
        if (Math.abs(restored.getBoundingBox().minY - originalBox.minY) > EPS) {
            throw helper.assertionException(wallRel, "deferred layout must match the original lowered box: "
                    + restored.getBoundingBox().minY + " vs " + originalBox.minY);
        }
        helper.setBlock(wallRel, Blocks.AIR.defaultBlockState());
        helper.setBlock(wallRel, Blocks.OAK_PLANKS.defaultBlockState());
        double wallNow = SlabSupport.getYOffset(level, wall, level.getBlockState(wall));
        if (Math.abs(wallNow) > EPS) {
            throw helper.assertionException(wallRel, "premise: the rebuilt wall must read flush, got " + wallNow);
        }
        restored.tick();
        if (Math.abs(seatOf(restored) + 0.5d) > EPS) {
            throw helper.assertionException(wallRel, "later support changes must not remint the decoration seat");
        }
        if (Math.abs(restored.getBoundingBox().minY - originalBox.minY) > EPS) {
            throw helper.assertionException(wallRel, "later ticks must not apply the height twice");
        }
        System.out.println("[HANG_LOAD_PROOF] saved=" + savedSeat + " painting=" + painting + " blocking=0 seat=-0.5 PASS");
        helper.succeed();
    }
}
