package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.HangingSeatDyHolder;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.Map;

/**
 * A hung decoration REMEMBERS the face it was hung on (LAW.md corollary, maintainer ruling
 * 2026-09-13): the seat is minted once, when it is hung, and never re-derived from the wall. On this
 * line the seat is physical (box and position move together), so the invariant pinned is that the
 * hung box and position come back exactly after the wall behind is rebuilt and the entity reloads.
 *
 * <p>MUTATION that must redden the reload row alone: in {@code ItemFramePhysicalOffsetMixin}'s box
 * hook, re-derive the seat from the backing cell on every layout instead of keeping the tracked
 * number once it is finite. Dropping the NBT restore does not bite the same way; do not name it.
 */
public final class HangingSeatRememberedTest {

    private static final double EPS = 1.0e-6d;

    /** A wall block that reads −0.5 the way this line records it: a stored placement fact on the cell. */
    private static BlockPos loweredWall(TestContext ctx, BlockPos rel) {
        ServerWorld world = ctx.getWorld();
        BlockPos abs = ctx.getAbsolutePos(rel);
        world.setBlockState(abs, Blocks.OAK_PLANKS.getDefaultState(), Block.NOTIFY_ALL);
        int writes = SlabAnchorAttachment.writePlacementDyBatch(world, Map.of(abs.toImmutable(), Double.doubleToRawLongBits(-0.5d)));
        // On this line a stored height and modern PROVENANCE are two facts; a hung seat is minted only
        // from a cell that carries both, the way a real placement leaves it.
        int marks = SlabAnchorAttachment.markPostPolicyPlacements(world, java.util.List.of(abs.toImmutable()));
        if (writes != 1 || marks != 1 || !SlabAnchorAttachment.usesFrozenPlacementHeight(world, abs)) {
            ctx.throwGameTestException("premise: the wall fixture must carry a stored height AND provenance; writes=" + writes + " marks=" + marks);
        }
        return abs;
    }

    private static double seatOf(Object entity) {
        return ((HangingSeatDyHolder) entity).slabbed$hangSeatDy();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aFrameKeepsItsSeatWhenTheWallBehindItIsRebuiltFlushAndItIsReloaded(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wall = loweredWall(ctx, new BlockPos(2, 16, 2));
        ItemFrameEntity frame = new ItemFrameEntity(world, wall.offset(Direction.EAST), Direction.EAST);
        world.spawnEntity(frame);
        Box hungBox = frame.getBoundingBox();
        double hungY = frame.getY();
        if (Math.abs(seatOf(frame) + 0.5d) > EPS) {
            ctx.throwGameTestException("premise: a frame hung on a -0.5 wall must mint seat -0.5, got " + seatOf(frame));
        }
        // The wall behind it is rebuilt FLUSH: a new block with no stored height.
        world.setBlockState(wall, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(wall, Blocks.OAK_PLANKS.getDefaultState(), Block.NOTIFY_ALL);
        double wallNow = SlabSupport.getYOffset(world, wall, world.getBlockState(wall));
        if (Math.abs(wallNow) > EPS) {
            ctx.throwGameTestException("premise: the rebuilt wall must read flush, got " + wallNow);
        }
        NbtCompound saved = new NbtCompound();
        frame.writeNbt(saved);
        ItemFrameEntity reloaded = new ItemFrameEntity(EntityType.ITEM_FRAME, world);
        reloaded.readNbt(saved);
        world.spawnEntity(reloaded);
        Box box = reloaded.getBoundingBox();
        if (Math.abs(seatOf(reloaded) + 0.5d) > EPS || Math.abs(box.minY - hungBox.minY) > EPS || Math.abs(reloaded.getY() - hungY) > EPS) {
            ctx.throwGameTestException("a frame must keep the seat it was hung at (-0.5) after its wall is rebuilt flush and it is reloaded; seat "
                    + seatOf(reloaded) + " box minY " + box.minY + " vs hung " + hungBox.minY + " y " + reloaded.getY() + " vs " + hungY
                    + " — the frame followed the wall instead of remembering (LAW.md corollary)");
        }
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aPaintingHangsOnItsWallsDrawnFaceAndIgnoresTheSlabUnderItsCell(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos lowered = loweredWall(ctx, new BlockPos(2, 16, 2));
        BlockPos control = ctx.getAbsolutePos(new BlockPos(2, 16, 5));
        world.setBlockState(control, Blocks.OAK_PLANKS.getDefaultState(), Block.NOTIFY_ALL);
        // The GH #48 scene for entities: flush wall, a bottom slab under the painting's cell.
        BlockPos slabScene = ctx.getAbsolutePos(new BlockPos(5, 16, 5));
        world.setBlockState(slabScene, Blocks.OAK_PLANKS.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(slabScene.east().down(), Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_ALL);

        PaintingEntity onLowered = PaintingEntity.placePainting(world, lowered.offset(Direction.EAST), Direction.EAST).orElse(null);
        PaintingEntity onControl = PaintingEntity.placePainting(world, control.offset(Direction.EAST), Direction.EAST).orElse(null);
        PaintingEntity overSlab = PaintingEntity.placePainting(world, slabScene.offset(Direction.EAST), Direction.EAST).orElse(null);
        if (onLowered == null || onControl == null || overSlab == null) {
            ctx.throwGameTestException("premise: every painting must find a fitting variant");
        }
        world.spawnEntity(onLowered);
        world.spawnEntity(onControl);
        world.spawnEntity(overSlab);
        String report = "seats lowered=" + seatOf(onLowered) + " control=" + seatOf(onControl) + " overSlab=" + seatOf(overSlab)
                + " | y lowered=" + onLowered.getY() + " control=" + onControl.getY() + " overSlab=" + overSlab.getY();
        System.out.println("[PAINTING_SEAT] " + report);
        if (Math.abs(seatOf(onControl)) > EPS || Math.abs(seatOf(overSlab)) > EPS || Math.abs(overSlab.getY() - onControl.getY()) > EPS) {
            ctx.throwGameTestException("a painting on a FLUSH wall keeps vanilla's height and ignores the slab under its cell (GH #48 for entities): " + report);
        }
        if (Math.abs(seatOf(onLowered) + 0.5d) > EPS || Math.abs((onLowered.getY() - onControl.getY()) + 0.5d) > EPS) {
            ctx.throwGameTestException("a painting on a -0.5 wall must hang on the drawn face, half a block below the control: " + report);
        }
        if (!onLowered.canStayAttached() || !onControl.canStayAttached() || !overSlab.canStayAttached()) {
            ctx.throwGameTestException("every painting must still judge attachment on its real wall: " + report);
        }
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aPaintingSavedBeforeTheSeatExistedMintsFromItsWallOnceOnLoad(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wall = loweredWall(ctx, new BlockPos(2, 16, 2));
        PaintingEntity painting = PaintingEntity.placePainting(world, wall.offset(Direction.EAST), Direction.EAST).orElse(null);
        if (painting == null) {
            ctx.throwGameTestException("premise: no fitting painting variant");
        }
        NbtCompound saved = new NbtCompound();
        painting.writeNbt(saved);
        saved.remove("slabbed:hang_dy"); // an old save: no remembered seat
        PaintingEntity old = new PaintingEntity(EntityType.PAINTING, world);
        old.readNbt(saved);
        world.spawnEntity(old);
        if (Math.abs(seatOf(old) + 0.5d) > EPS) {
            ctx.throwGameTestException("a painting with no saved seat must mint one from its wall on load (-0.5), got " + seatOf(old));
        }
        ctx.complete();
    }
}
