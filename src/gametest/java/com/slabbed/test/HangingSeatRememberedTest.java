package com.slabbed.test;

import com.slabbed.util.HangingSeatDyHolder;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A hung decoration REMEMBERS the face it was hung on (LAW.md, Law 1 corollary; maintainer ruling
 * 2026-09-13): the seat is minted once, when it is hung, and never re-derived from the wall.
 *
 * <p>Rows: a frame hung on a lowered wall keeps its seat after the wall behind it is rebuilt flush
 * and the frame is saved and reloaded (the wall changed; the frame did not); a painting hangs on a
 * lowered wall's drawn face and a flush control keeps vanilla's box; a painting whose wall is flush
 * ignores a slab under its cell (the GH #48 scene, for entities); a frame saved before the seat
 * existed mints from its wall once on load.
 *
 * <p>MUTATION that must redden the reload row alone: in
 * {@code HangingEntityRememberedSeatMixin.slabbed$seatHangBox}, replace the remembered number
 * ({@code slabbed$hangSeatDy()}) with a fresh {@code SlabSupport.getYOffset} read of the support
 * cell ("follow the support") - the reloaded frame's box then tracks the rebuilt flush wall and
 * its minY no longer matches the box it was hung with. Dropping the mint guard in
 * {@code slabbed$mintHangSeatFor} does NOT bite - the per-class read hook restores the saved seat
 * after any re-mint - so do not name that one.
 *
 * <p>MUTATION for the painting rows: in the same method, drop the shift entirely (return without
 * moving the box). The lowered painting's box then equals the flush control's and the row reds.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class HangingSeatRememberedTest {

    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6d;
    private static final String HANG_DY_KEY = "slabbed:hang_dy";

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /** A wall block that reads -0.5: an ordinary block sitting on a bottom slab. */
    private static BlockPos loweredWall(GameTestHelper ctx, BlockPos rel) {
        ctx.setBlock(rel.below(), bottomSlab());
        ctx.setBlock(rel, Blocks.OAK_PLANKS.defaultBlockState());
        return ctx.absolutePos(rel);
    }

    private static double seatOf(Object entity) {
        return ((HangingSeatDyHolder) entity).slabbed$hangSeatDy();
    }

    private static double wallDy(ServerLevel world, BlockPos abs) {
        return SlabSupport.getYOffset(world, abs, world.getBlockState(abs));
    }

    /** A fixed 1x1 variant, so the lowered painting and the flush control have identical geometry. */
    private static Holder<PaintingVariant> kebab(ServerLevel world) {
        return world.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT)
                .getHolderOrThrow(PaintingVariants.KEBAB);
    }

    @GameTest(template = TEMPLATE)
    public void aFrameKeepsItsSeatWhenTheWallBehindItIsRebuiltFlushAndItIsReloaded(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos wallRel = new BlockPos(2, 3, 2);
        BlockPos wall = loweredWall(ctx, wallRel);
        ctx.assertTrue(Math.abs(wallDy(world, wall) + 0.5d) <= EPS,
                "premise: the wall must read -0.5 before the frame is hung, got " + wallDy(world, wall));

        ItemFrame frame = new ItemFrame(world, wall.relative(Direction.EAST), Direction.EAST);
        world.addFreshEntity(frame);
        AABB hungBox = frame.getBoundingBox();
        ctx.assertTrue(Math.abs(seatOf(frame) + 0.5d) <= EPS,
                "premise: a frame hung on a -0.5 wall must mint seat -0.5, got " + seatOf(frame));

        // The wall behind it is rebuilt FLUSH: its own support is taken away and a new block laid
        // in its place, so the wall itself now reads 0. The frame must not care.
        ctx.setBlock(wallRel.below(), Blocks.AIR.defaultBlockState());
        ctx.setBlock(wallRel, Blocks.AIR.defaultBlockState());
        ctx.setBlock(wallRel, Blocks.OAK_PLANKS.defaultBlockState());
        double wallNow = wallDy(world, wall);
        ctx.assertTrue(Math.abs(wallNow) <= EPS,
                "premise: the rebuilt wall must read flush, got " + wallNow);

        // Save and reload the frame: the seat is the frame's own fact and comes back verbatim.
        CompoundTag saved = frame.saveWithoutId(new CompoundTag());
        // Drop the identity so the reloaded copy can join the level beside the original; the seat,
        // not the entity's identity, is what this row measures.
        saved.remove("UUID");
        ItemFrame reloaded = EntityType.ITEM_FRAME.create(world);
        ctx.assertTrue(reloaded != null, "premise: could not create the reloaded frame");
        reloaded.load(saved);
        world.addFreshEntity(reloaded);

        AABB reloadedBox = reloaded.getBoundingBox();
        ctx.assertTrue(Math.abs(seatOf(reloaded) + 0.5d) <= EPS
                        && Math.abs(reloadedBox.minY - hungBox.minY) <= EPS,
                "a frame must keep the seat it was hung at (-0.5) after its wall is rebuilt flush and it is reloaded; "
                        + "seat " + seatOf(reloaded) + " box minY " + reloadedBox.minY + " vs hung " + hungBox.minY
                        + " - the frame followed the wall instead of remembering (LAW.md, Law 1 corollary)");
        ctx.assertTrue(Math.abs(reloaded.getY() - frame.getY()) <= EPS,
                "the entity position must stay at grid height across the reload; "
                        + reloaded.getY() + " vs " + frame.getY());
        ctx.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void aPaintingHangsOnItsWallsDrawnFaceAndIgnoresTheSlabUnderItsCell(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        // Lowered wall.
        BlockPos loweredRel = new BlockPos(2, 3, 2);
        BlockPos lowered = loweredWall(ctx, loweredRel);
        // Flush control wall.
        BlockPos controlRel = new BlockPos(2, 3, 5);
        ctx.setBlock(controlRel, Blocks.OAK_PLANKS.defaultBlockState());
        BlockPos control = ctx.absolutePos(controlRel);
        // The GH #48 scene for entities: flush wall, a bottom slab under the painting's cell.
        BlockPos slabSceneRel = new BlockPos(5, 3, 5);
        ctx.setBlock(slabSceneRel, Blocks.OAK_PLANKS.defaultBlockState());
        ctx.setBlock(slabSceneRel.east().below(), bottomSlab());
        BlockPos slabScene = ctx.absolutePos(slabSceneRel);

        Holder<PaintingVariant> variant = kebab(world);
        Painting onLowered = new Painting(world, lowered.relative(Direction.EAST), Direction.EAST, variant);
        Painting onControl = new Painting(world, control.relative(Direction.EAST), Direction.EAST, variant);
        Painting overSlab = new Painting(world, slabScene.relative(Direction.EAST), Direction.EAST, variant);
        world.addFreshEntity(onLowered);
        world.addFreshEntity(onControl);
        world.addFreshEntity(overSlab);

        AABB lb = onLowered.getBoundingBox();
        AABB cb = onControl.getBoundingBox();
        AABB sb = overSlab.getBoundingBox();
        String report = "seats lowered=" + seatOf(onLowered) + " control=" + seatOf(onControl)
                + " overSlab=" + seatOf(overSlab)
                + " | box minY lowered=" + lb.minY + " control=" + cb.minY + " overSlab=" + sb.minY;
        System.out.println("[PAINTING_SEAT] " + report);

        ctx.assertTrue(Math.abs(seatOf(onControl)) <= EPS
                        && Math.abs(((cb.minY + cb.maxY) / 2.0d) - onControl.getY()) <= EPS,
                "control: a painting on a flush wall keeps vanilla's centred box: " + report);
        ctx.assertTrue(Math.abs(seatOf(onLowered) + 0.5d) <= EPS
                        && Math.abs((lb.minY - cb.minY) + 0.5d) <= EPS,
                "a painting on a -0.5 wall must hang on the drawn face (box 0.5 below the control's): " + report);
        ctx.assertTrue(Math.abs(onLowered.getY() - onControl.getY()) <= EPS,
                "the painting's entity position must stay at grid height: " + report);
        ctx.assertTrue(Math.abs(seatOf(overSlab)) <= EPS && Math.abs(sb.minY - cb.minY) <= EPS,
                "GH #48 for entities: a painting on a FLUSH wall must ignore the slab under its cell: " + report);
        ctx.assertTrue(onLowered.survives() && onControl.survives() && overSlab.survives(),
                "every painting must still survive on its real wall: " + report);
        ctx.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void aFrameSavedBeforeTheSeatExistedMintsFromItsWallOnceOnLoad(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos wallRel = new BlockPos(2, 3, 2);
        BlockPos wall = loweredWall(ctx, wallRel);
        ItemFrame frame = new ItemFrame(world, wall.relative(Direction.EAST), Direction.EAST);
        CompoundTag saved = frame.saveWithoutId(new CompoundTag());
        saved.remove(HANG_DY_KEY); // an old save: no remembered seat
        saved.remove("UUID");
        ItemFrame old = EntityType.ITEM_FRAME.create(world);
        ctx.assertTrue(old != null, "premise: could not create the frame");
        old.load(saved);
        world.addFreshEntity(old);
        ctx.assertTrue(Math.abs(seatOf(old) + 0.5d) <= EPS,
                "a frame with no saved seat must mint one from its wall on load (-0.5), got " + seatOf(old));
        ctx.succeed();
    }
}
