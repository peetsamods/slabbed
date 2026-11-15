package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.HangingSeatDyHolder;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * A hung decoration REMEMBERS the face it was hung on (LAW 1 corollary, maintainer ruling
 * 2026-09-13): the seat is minted once, when it is hung, and never re-derived from the wall.
 *
 * <p>Rows: a frame hung on a lowered wall keeps its seat after the wall behind it is rebuilt flush
 * and the frame is saved and reloaded (the wall changed; the frame did not); a painting hangs on a
 * lowered wall's drawn face and a flush control keeps vanilla's box; a painting whose wall is flush
 * ignores a slab under its cell (the GH #48 scene, for entities); a frame saved before the seat
 * existed mints from its wall once on load.
 *
 * <p>MUTATION that must redden the reload row alone (measured 2026-09-13): in
 * {@code HangingEntityRememberedSeatMixin.slabbed$hangBoxOnRememberedSeat}, replace the remembered
 * number with a fresh read of the support's height ("follow the support"). Dropping the has-seat
 * guard in {@code slabbed$tryMintHangSeat} now also reddens that row, because the post-load
 * migration would re-read the rebuilt wall.
 */
public final class HangingSeatRememberedTest {

    /** The knot and the actual rope endpoint share the fence's remembered seat. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void leashKnotAndRopeEndpointSitOnLoweredFence(GameTestHelper helper) {
        boolean frozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            ServerLevel level = helper.getLevel();
            BlockPos rel = new BlockPos(2, 3, 2);
            helper.setBlock(rel.below(), bottomSlab());
            helper.setBlock(rel, Blocks.OAK_FENCE.defaultBlockState());
            BlockPos fence = helper.absolutePos(rel);
            SlabAnchorAttachment.writePlacementDy(level, fence, -0.5d);
            helper.setBlock(rel.below(), Blocks.AIR.defaultBlockState());
            double dy = com.slabbed.util.SlabSupport.getYOffset(level, fence, level.getBlockState(fence));
            if (Math.abs(dy + 0.5d) > EPS) {
                throw helper.assertionException(rel, "premise: fence must be lowered by half a block");
            }
            // Entity construction visits origin before receiving its actual attachment cell.
            level.getChunk(0, 0);
            var knot = net.minecraft.world.entity.decoration.LeashFenceKnotEntity.getOrCreateKnot(level, fence);
            double expected = fence.getY() + 0.375d + dy;
            if (Math.abs(knot.getBoundingBox().minY - expected) > EPS) {
                throw helper.assertionException(rel, "knot box must use lowered fence seat; expected " + expected
                        + " got " + knot.getBoundingBox().minY);
            }
            if (Math.abs(knot.getRopeHoldPosition(1.0f).y - expected - 0.2d) > EPS) {
                throw helper.assertionException(rel, "rope endpoint must meet lowered knot");
            }
            if (!knot.survives() || !knot.getPos().equals(fence)) {
                throw helper.assertionException(rel, "lowering must preserve fence attachment and survival");
            }
            // A support replacement would move a recomputed seat; the knot must keep its own fact.
            SlabAnchorAttachment.writePlacementDy(level, fence, 0.0d);
            knot.tick();
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            knot.saveWithoutId(output);
            var reloaded = EntityTypes.LEASH_KNOT.create(level, EntitySpawnReason.LOAD);
            if (reloaded == null) {
                throw helper.assertionException("could not create reloaded leash knot");
            }
            reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
            if (Math.abs(knot.getBoundingBox().minY - expected) > EPS
                    || Math.abs(reloaded.getBoundingBox().minY - expected) > EPS
                    || Math.abs(reloaded.getRopeHoldPosition(1.0f).y - expected - 0.2d) > EPS
                    || !reloaded.getPos().equals(fence) || !reloaded.survives()) {
                throw helper.assertionException(rel, "knot seat must persist after support height changes and reload");
            }
            helper.succeed();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = frozen;
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void leashKnotOnFlushFenceKeepsVanillaAttachment(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 3, 2);
        helper.setBlock(rel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(rel, Blocks.OAK_FENCE.defaultBlockState());
        BlockPos fence = helper.absolutePos(rel);
        var knot = net.minecraft.world.entity.decoration.LeashFenceKnotEntity.getOrCreateKnot(helper.getLevel(), fence);
        double expected = fence.getY() + 0.375d;
        if (Math.abs(knot.getBoundingBox().minY - expected) > EPS
                || Math.abs(knot.getRopeHoldPosition(1.0f).y - expected - 0.2d) > EPS
                || !knot.survives() || !knot.getPos().equals(fence)) {
            throw helper.assertionException(rel, "flush fence must retain vanilla knot geometry and attachment");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deeplyLoweredFenceReusesItsExistingKnot(GameTestHelper helper) {
        boolean frozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            BlockPos rel = new BlockPos(2, 3, 2);
            helper.setBlock(rel, Blocks.OAK_FENCE.defaultBlockState());
            BlockPos fence = helper.absolutePos(rel);
            SlabAnchorAttachment.writePlacementDy(helper.getLevel(), fence, -2.5d);
            var first = net.minecraft.world.entity.decoration.LeashFenceKnotEntity.getOrCreateKnot(helper.getLevel(), fence);
            var second = net.minecraft.world.entity.decoration.LeashFenceKnotEntity.getOrCreateKnot(helper.getLevel(), fence);
            if (first != second || Math.abs(first.getBoundingBox().minY - fence.getY() - 0.375d + 2.5d) > EPS
                    || net.minecraft.world.entity.decoration.LeashFenceKnotEntity.getKnot(helper.getLevel(), fence.above()).isPresent()) {
                throw helper.assertionException(rel, "a deep knot must be lowered and reused, never duplicated");
            }
            helper.succeed();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = frozen;
        }
    }

    private static final double EPS = 1.0e-6d;

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /** A wall block that reads −0.5: the established fixture (seat on a slab, anchor, clear the slab). */
    private static BlockPos loweredWall(GameTestHelper helper, BlockPos rel) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(rel.below(), bottomSlab());
        helper.setBlock(rel, Blocks.OAK_PLANKS.defaultBlockState());
        BlockPos abs = helper.absolutePos(rel);
        SlabAnchorAttachment.addAnchor(level, abs, level.getBlockState(abs));
        helper.setBlock(rel.below(), Blocks.AIR.defaultBlockState());
        return abs;
    }

    private static double seatOf(Object entity) {
        return ((HangingSeatDyHolder) entity).slabbed$hangSeatDy();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aFrameKeepsItsSeatWhenTheWallBehindItIsRebuiltFlushAndItIsReloaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos wallRel = new BlockPos(2, 3, 2);
        BlockPos wall = loweredWall(helper, wallRel);
        ItemFrame frame = new ItemFrame(level, wall.relative(Direction.EAST), Direction.EAST);
        level.addFreshEntity(frame);
        AABB hungBox = frame.getBoundingBox();
        if (Math.abs(seatOf(frame) + 0.5d) > EPS) {
            throw helper.assertionException(wallRel, "premise: a frame hung on a -0.5 wall must mint seat -0.5, got " + seatOf(frame));
        }
        // The wall behind it is rebuilt FLUSH: a new block, no stored height.
        helper.setBlock(wallRel, Blocks.AIR.defaultBlockState());
        helper.setBlock(wallRel, Blocks.OAK_PLANKS.defaultBlockState());
        double wallNow = com.slabbed.util.SlabSupport.getYOffset(level, wall, level.getBlockState(wall));
        if (Math.abs(wallNow) > EPS) {
            throw helper.assertionException(wallRel, "premise: the rebuilt wall must read flush, got " + wallNow);
        }
        // Save and reload the frame: the seat is the frame's own fact and comes back verbatim.
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        frame.saveWithoutId(output);
        CompoundTag saved = output.buildResult();
        ItemFrame reloaded = EntityTypes.ITEM_FRAME.create(level, EntitySpawnReason.LOAD);
        if (reloaded == null) {
            throw helper.assertionException("premise: could not create the reloaded frame");
        }
        reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        level.addFreshEntity(reloaded);
        AABB reloadedBox = reloaded.getBoundingBox();
        if (Math.abs(seatOf(reloaded) + 0.5d) > EPS || Math.abs(reloadedBox.minY - hungBox.minY) > EPS) {
            throw helper.assertionException(wallRel, "a frame must keep the seat it was hung at (-0.5) after its wall is rebuilt flush and it is reloaded; "
                    + "seat " + seatOf(reloaded) + " box minY " + reloadedBox.minY + " vs hung " + hungBox.minY
                    + " — the frame followed the wall instead of remembering (LAW 1 corollary)");
        }
        if (Math.abs(reloaded.getY() - frame.getY()) > EPS) {
            throw helper.assertionException(wallRel, "the entity position must stay at grid height across the reload; " + reloaded.getY() + " vs " + frame.getY());
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aPaintingHangsOnItsWallsDrawnFaceAndIgnoresTheSlabUnderItsCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Lowered wall.
        BlockPos loweredRel = new BlockPos(2, 3, 2);
        BlockPos lowered = loweredWall(helper, loweredRel);
        // Flush control wall.
        BlockPos controlRel = new BlockPos(2, 3, 5);
        helper.setBlock(controlRel, Blocks.OAK_PLANKS.defaultBlockState());
        BlockPos control = helper.absolutePos(controlRel);
        // The GH #48 scene for entities: flush wall, a bottom slab under the painting's cell.
        BlockPos slabSceneRel = new BlockPos(5, 3, 5);
        helper.setBlock(slabSceneRel, Blocks.OAK_PLANKS.defaultBlockState());
        helper.setBlock(slabSceneRel.east().below(), bottomSlab());
        BlockPos slabScene = helper.absolutePos(slabSceneRel);

        Painting onLowered = Painting.create(level, lowered.relative(Direction.EAST), Direction.EAST).orElse(null);
        Painting onControl = Painting.create(level, control.relative(Direction.EAST), Direction.EAST).orElse(null);
        Painting overSlab = Painting.create(level, slabScene.relative(Direction.EAST), Direction.EAST).orElse(null);
        if (onLowered == null || onControl == null || overSlab == null) {
            throw helper.assertionException("premise: every painting must find a fitting variant");
        }
        level.addFreshEntity(onLowered);
        level.addFreshEntity(onControl);
        level.addFreshEntity(overSlab);
        AABB lb = onLowered.getBoundingBox();
        AABB cb = onControl.getBoundingBox();
        AABB sb = overSlab.getBoundingBox();
        String report = "seats lowered=" + seatOf(onLowered) + " control=" + seatOf(onControl) + " overSlab=" + seatOf(overSlab)
                + " | box minY lowered=" + lb.minY + " control=" + cb.minY + " overSlab=" + sb.minY;
        System.out.println("[PAINTING_SEAT] " + report);
        if (Math.abs(seatOf(onControl)) > EPS || Math.abs(((cb.minY + cb.maxY) / 2.0d) - onControl.getY()) > EPS) {
            throw helper.assertionException(controlRel, "control: a painting on a flush wall keeps vanilla's centred box: " + report);
        }
        if (Math.abs(seatOf(onLowered) + 0.5d) > EPS || Math.abs((lb.minY - cb.minY) + 0.5d) > EPS) {
            throw helper.assertionException(loweredRel, "a painting on a -0.5 wall must hang on the drawn face (box 0.5 below the control's): " + report);
        }
        if (Math.abs(onLowered.getY() - onControl.getY()) > EPS) {
            throw helper.assertionException(loweredRel, "the painting's entity position must stay at grid height: " + report);
        }
        if (Math.abs(seatOf(overSlab)) > EPS || Math.abs(sb.minY - cb.minY) > EPS) {
            throw helper.assertionException(slabSceneRel, "GH #48 for entities: a painting on a FLUSH wall must ignore the slab under its cell: " + report);
        }
        if (!onLowered.survives() || !onControl.survives() || !overSlab.survives()) {
            throw helper.assertionException("every painting must still survive on its real wall: " + report);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aFrameSavedBeforeTheSeatExistedMintsFromItsWallOnceOnLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos wallRel = new BlockPos(2, 3, 2);
        BlockPos wall = loweredWall(helper, wallRel);
        ItemFrame frame = new ItemFrame(level, wall.relative(Direction.EAST), Direction.EAST);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        frame.saveWithoutId(output);
        CompoundTag saved = output.buildResult();
        saved.remove("slabbed:hang_dy"); // an old save: no remembered seat
        ItemFrame old = EntityTypes.ITEM_FRAME.create(level, EntitySpawnReason.LOAD);
        if (old == null) {
            throw helper.assertionException("premise: could not create the frame");
        }
        old.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        level.addFreshEntity(old);
        if (Math.abs(seatOf(old) + 0.5d) > EPS) {
            throw helper.assertionException(wallRel, "a frame with no saved seat must mint one from its wall on load (-0.5), got " + seatOf(old));
        }
        helper.succeed();
    }
}
