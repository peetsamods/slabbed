package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.HangingSeatDyHolder;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.storage.WriteView;
import net.minecraft.test.TestContext;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

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
 * number with a fresh read of the support's height ("follow the support"). Dropping the mint guard
 * in {@code slabbed$mintSeatOnDirection} does NOT bite — the per-class read hook restores the saved
 * seat after any re-mint — so do not name that one.
 */
public final class HangingSeatRememberedTest {

    private static final double EPS = 1.0e-6d;

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /** A wall block that reads −0.5: the established fixture (seat on a slab, anchor, clear the slab). */
    private static BlockPos loweredWall(TestContext ctx, BlockPos rel) {
        ServerWorld world = ctx.getWorld();
        ctx.setBlockState(rel.down(), bottomSlab());
        ctx.setBlockState(rel, Blocks.OAK_PLANKS.getDefaultState());
        BlockPos abs = ctx.getAbsolutePos(rel);
        SlabAnchorAttachment.addAnchor(world, abs, world.getBlockState(abs));
        ctx.setBlockState(rel.down(), Blocks.AIR.getDefaultState());
        return abs;
    }

    private static double seatOf(Object entity) {
        return ((HangingSeatDyHolder) entity).slabbed$hangSeatDy();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aFrameKeepsItsSeatWhenTheWallBehindItIsRebuiltFlushAndItIsReloaded(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wallRel = new BlockPos(2, 3, 2);
        BlockPos wall = loweredWall(ctx, wallRel);
        ItemFrameEntity frame = new ItemFrameEntity(world, wall.offset(Direction.EAST), Direction.EAST);
        world.spawnEntity(frame);
        Box hungBox = frame.getBoundingBox();
        ctx.assertTrue(Math.abs(seatOf(frame) + 0.5d) <= EPS,
                "premise: a frame hung on a -0.5 wall must mint seat -0.5, got " + seatOf(frame));
        // The wall behind it is rebuilt FLUSH: a new block, no stored height.
        ctx.setBlockState(wallRel, Blocks.AIR.getDefaultState());
        ctx.setBlockState(wallRel, Blocks.OAK_PLANKS.getDefaultState());
        double wallNow = com.slabbed.util.SlabSupport.getYOffset(world, wall, world.getBlockState(wall));
        ctx.assertTrue(Math.abs(wallNow) <= EPS, "premise: the rebuilt wall must read flush, got " + wallNow);
        // Save and reload the frame: the seat is the frame's own fact and comes back verbatim.
        WriteView output = NbtWriteView.create(ErrorReporter.EMPTY, world.getRegistryManager());
        frame.saveData(output);
        NbtCompound saved = ((NbtWriteView) output).getNbt();
        ItemFrameEntity reloaded = EntityType.ITEM_FRAME.create(world, SpawnReason.LOAD);
        ctx.assertTrue(reloaded != null, "premise: could not create the reloaded frame");
        reloaded.readData(NbtReadView.create(ErrorReporter.EMPTY, world.getRegistryManager(), saved));
        world.spawnEntity(reloaded);
        Box reloadedBox = reloaded.getBoundingBox();
        ctx.assertTrue(Math.abs(seatOf(reloaded) + 0.5d) <= EPS && Math.abs(reloadedBox.minY - hungBox.minY) <= EPS,
                "a frame must keep the seat it was hung at (-0.5) after its wall is rebuilt flush and it is reloaded; "
                        + "seat " + seatOf(reloaded) + " box minY " + reloadedBox.minY + " vs hung " + hungBox.minY
                        + " — the frame followed the wall instead of remembering (LAW 1 corollary)");
        ctx.assertTrue(Math.abs(reloaded.getY() - frame.getY()) <= EPS,
                "the entity position must stay at grid height across the reload; " + reloaded.getY() + " vs " + frame.getY());
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aPaintingHangsOnItsWallsDrawnFaceAndIgnoresTheSlabUnderItsCell(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        // Lowered wall.
        BlockPos loweredRel = new BlockPos(2, 3, 2);
        BlockPos lowered = loweredWall(ctx, loweredRel);
        // Flush control wall.
        BlockPos controlRel = new BlockPos(2, 3, 5);
        ctx.setBlockState(controlRel, Blocks.OAK_PLANKS.getDefaultState());
        BlockPos control = ctx.getAbsolutePos(controlRel);
        // The GH #48 scene for entities: flush wall, a bottom slab under the painting's cell.
        BlockPos slabSceneRel = new BlockPos(5, 3, 5);
        ctx.setBlockState(slabSceneRel, Blocks.OAK_PLANKS.getDefaultState());
        ctx.setBlockState(slabSceneRel.offset(Direction.EAST).down(), bottomSlab());
        BlockPos slabScene = ctx.getAbsolutePos(slabSceneRel);

        PaintingEntity onLowered = PaintingEntity.placePainting(world, lowered.offset(Direction.EAST), Direction.EAST).orElse(null);
        PaintingEntity onControl = PaintingEntity.placePainting(world, control.offset(Direction.EAST), Direction.EAST).orElse(null);
        PaintingEntity overSlab = PaintingEntity.placePainting(world, slabScene.offset(Direction.EAST), Direction.EAST).orElse(null);
        ctx.assertTrue(onLowered != null && onControl != null && overSlab != null,
                "premise: every painting must find a fitting variant");
        world.spawnEntity(onLowered);
        world.spawnEntity(onControl);
        world.spawnEntity(overSlab);
        Box lb = onLowered.getBoundingBox();
        Box cb = onControl.getBoundingBox();
        Box sb = overSlab.getBoundingBox();
        String report = "seats lowered=" + seatOf(onLowered) + " control=" + seatOf(onControl) + " overSlab=" + seatOf(overSlab)
                + " | box minY lowered=" + lb.minY + " control=" + cb.minY + " overSlab=" + sb.minY;
        System.out.println("[PAINTING_SEAT] " + report);
        ctx.assertTrue(Math.abs(seatOf(onControl)) <= EPS && Math.abs(((cb.minY + cb.maxY) / 2.0d) - onControl.getY()) <= EPS,
                "control: a painting on a flush wall keeps vanilla's centred box: " + report);
        ctx.assertTrue(Math.abs(seatOf(onLowered) + 0.5d) <= EPS && Math.abs((lb.minY - cb.minY) + 0.5d) <= EPS,
                "a painting on a -0.5 wall must hang on the drawn face (box 0.5 below the control's): " + report);
        ctx.assertTrue(Math.abs(onLowered.getY() - onControl.getY()) <= EPS,
                "the painting's entity position must stay at grid height: " + report);
        ctx.assertTrue(Math.abs(seatOf(overSlab)) <= EPS && Math.abs(sb.minY - cb.minY) <= EPS,
                "GH #48 for entities: a painting on a FLUSH wall must ignore the slab under its cell: " + report);
        ctx.assertTrue(onLowered.canStayAttached() && onControl.canStayAttached() && overSlab.canStayAttached(),
                "every painting must still survive on its real wall: " + report);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aFrameSavedBeforeTheSeatExistedMintsFromItsWallOnceOnLoad(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wallRel = new BlockPos(2, 3, 2);
        BlockPos wall = loweredWall(ctx, wallRel);
        ItemFrameEntity frame = new ItemFrameEntity(world, wall.offset(Direction.EAST), Direction.EAST);
        WriteView output = NbtWriteView.create(ErrorReporter.EMPTY, world.getRegistryManager());
        frame.saveData(output);
        NbtCompound saved = ((NbtWriteView) output).getNbt();
        saved.remove("slabbed:hang_dy"); // an old save: no remembered seat
        ItemFrameEntity old = EntityType.ITEM_FRAME.create(world, SpawnReason.LOAD);
        ctx.assertTrue(old != null, "premise: could not create the frame");
        old.readData(NbtReadView.create(ErrorReporter.EMPTY, world.getRegistryManager(), saved));
        world.spawnEntity(old);
        ctx.assertTrue(Math.abs(seatOf(old) + 0.5d) <= EPS,
                "a frame with no saved seat must mint one from its wall on load (-0.5), got " + seatOf(old));
        ctx.complete();
    }
}
