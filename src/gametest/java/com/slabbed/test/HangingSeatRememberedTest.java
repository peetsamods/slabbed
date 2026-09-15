package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.util.HangingSeatDyHolder;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A hung decoration REMEMBERS the face it was hung on (LAW.md Law 1 corollary, maintainer ruling
 * 2026-09-13): the seat is minted once, when it is hung, and never re-derived from the wall.
 *
 * <p>Rows: a frame hung on a lowered wall keeps its seat after the wall behind it is rebuilt flush
 * and the frame is saved and reloaded (the wall changed; the frame did not); a painting hangs on a
 * lowered wall's drawn face and a flush control keeps vanilla's box; a painting whose wall is flush
 * ignores a slab under its cell (the wall-attached scene, for entities); a frame saved before the
 * seat existed mints from its wall once on load.
 *
 * <p>MUTATIONS each row names, per LAW.md's reachability rule.
 * <ul>
 *   <li>Reload row: in {@code HangingEntityRememberedSeatMixin.slabbed$hangBoxOnRememberedSeat},
 *       replace the remembered number with a fresh read of the support's height ("follow the
 *       support"). Dropping the mint guard in {@code slabbed$mintHangSeatFromWall} does NOT bite —
 *       the per-class read hook restores the saved seat after any re-mint — so do not name that
 *       one.</li>
 *   <li>Painting row: return {@code 0.0} unconditionally from
 *       {@code HangingEntityRememberedSeatMixin.slabbed$hangSeatDy}; the lowered painting's box
 *       returns to grid height while the control stays put.</li>
 *   <li>Migration row: make {@code slabbed$mintHangSeatFromWall} return before it writes; a frame
 *       with no saved number then stays at 0.</li>
 * </ul>
 *
 * <p>The lowered wall is built by a REAL held-item placement onto a bottom slab's top face — the
 * same gesture {@code NeighborUpdateInvarianceTest} uses — so the wall's height is a genuine
 * placement fact and not a hand-authored one.
 */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class HangingSeatRememberedTest {

    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6d;

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /** Builds a wall block that reads −0.5 by really placing stone on a bottom slab's top face. */
    private static BlockPos loweredWall(GameTestHelper ctx, BlockPos slabRel) {
        ServerLevel level = ctx.getLevel();
        BlockPos slab = ctx.absolutePos(slabRel);
        BlockPos wall = slab.above();
        level.setBlock(slab, bottomSlab(), Block.UPDATE_ALL);
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(wall.getX() + 0.5d, wall.getY(), wall.getZ() + 0.5d);
        ItemStack stack = new ItemStack(Blocks.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(slab), Direction.UP, slab, false)));
        ctx.assertTrue(result.consumesAction(), "premise: the wall placement must be accepted");
        ctx.assertTrue(level.getBlockState(wall).is(Blocks.STONE), "premise: the wall placement must create the wall block");
        double dy = SlabSupport.getYOffset(level, wall, level.getBlockState(wall));
        ctx.assertTrue(Math.abs(dy + 0.5d) <= EPS, "premise: the placed wall must read -0.5, got " + dy);
        // Clear the slab the placement was seated on. The stored height must survive its support
        // being taken away (Law 1) - which is also what lets a later rebuild of this cell read
        // flush, so a row can tell "remembered" from "re-read the wall" at all.
        level.setBlock(slab, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        double afterClear = SlabSupport.getYOffset(level, wall, level.getBlockState(wall));
        ctx.assertTrue(Math.abs(afterClear + 0.5d) <= EPS,
                "premise: the placed wall must keep -0.5 after its slab is removed, got " + afterClear);
        return wall;
    }

    private static double seatOf(Object entity) {
        return ((HangingSeatDyHolder) entity).slabbed$hangSeatDy();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void aFrameKeepsItsSeatWhenTheWallBehindItIsRebuiltFlushAndItIsReloaded(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        BlockPos wall = loweredWall(ctx, new BlockPos(2, 2, 2));
        ItemFrame frame = new ItemFrame(level, wall.relative(Direction.EAST), Direction.EAST);
        level.addFreshEntity(frame);
        AABB hungBox = frame.getBoundingBox();
        ctx.assertTrue(Math.abs(seatOf(frame) + 0.5d) <= EPS,
                "premise: a frame hung on a -0.5 wall must mint seat -0.5, got " + seatOf(frame));

        // The wall behind it is rebuilt FLUSH: a new block, no stored height.
        level.setBlock(wall, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(wall, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        double wallNow = SlabSupport.getYOffset(level, wall, level.getBlockState(wall));
        ctx.assertTrue(Math.abs(wallNow) <= EPS, "premise: the rebuilt wall must read flush, got " + wallNow);

        // Save and reload the frame: the seat is the frame's own fact and comes back verbatim.
        CompoundTag saved = new CompoundTag();
        frame.saveWithoutId(saved);
        ItemFrame reloaded = EntityType.ITEM_FRAME.create(level);
        ctx.assertTrue(reloaded != null, "premise: could not create the reloaded frame");
        reloaded.load(saved);
        level.addFreshEntity(reloaded);
        AABB reloadedBox = reloaded.getBoundingBox();
        ctx.assertTrue(Math.abs(seatOf(reloaded) + 0.5d) <= EPS && Math.abs(reloadedBox.minY - hungBox.minY) <= EPS,
                "a frame must keep the seat it was hung at (-0.5) after its wall is rebuilt flush and it is reloaded; "
                        + "seat " + seatOf(reloaded) + " box minY " + reloadedBox.minY + " vs hung " + hungBox.minY
                        + " - the frame followed the wall instead of remembering (LAW.md Law 1 corollary)");
        ctx.assertTrue(Math.abs(reloaded.getY() - frame.getY()) <= EPS,
                "the entity position must stay at grid height across the reload; " + reloaded.getY() + " vs " + frame.getY());
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void aPaintingHangsOnItsWallsDrawnFaceAndIgnoresTheSlabUnderItsCell(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        BlockPos lowered = loweredWall(ctx, new BlockPos(1, 2, 1));
        // Flush control wall.
        BlockPos control = ctx.absolutePos(new BlockPos(1, 3, 4));
        level.setBlock(control, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        // The wall-attached scene for entities: flush wall, a bottom slab under the painting's cell.
        BlockPos slabScene = ctx.absolutePos(new BlockPos(1, 3, 6));
        level.setBlock(slabScene, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(slabScene.east().below(), bottomSlab(), Block.UPDATE_ALL);

        Painting onLowered = Painting.create(level, lowered.relative(Direction.EAST), Direction.EAST).orElse(null);
        Painting onControl = Painting.create(level, control.relative(Direction.EAST), Direction.EAST).orElse(null);
        Painting overSlab = Painting.create(level, slabScene.relative(Direction.EAST), Direction.EAST).orElse(null);
        ctx.assertTrue(onLowered != null && onControl != null && overSlab != null,
                "premise: every painting must find a fitting variant");
        level.addFreshEntity(onLowered);
        level.addFreshEntity(onControl);
        level.addFreshEntity(overSlab);
        AABB lb = onLowered.getBoundingBox();
        AABB cb = onControl.getBoundingBox();
        AABB sb = overSlab.getBoundingBox();
        String report = "seats lowered=" + seatOf(onLowered) + " control=" + seatOf(onControl) + " overSlab=" + seatOf(overSlab)
                + " | box minY lowered=" + lb.minY + " control=" + cb.minY + " overSlab=" + sb.minY;
        Slabbed.LOGGER.info("[PAINTING_SEAT] {}", report);
        ctx.assertTrue(Math.abs(seatOf(onControl)) <= EPS && Math.abs(((cb.minY + cb.maxY) / 2.0d) - onControl.getY()) <= EPS,
                "control: a painting on a flush wall keeps vanilla's centred box: " + report);
        ctx.assertTrue(Math.abs(seatOf(onLowered) + 0.5d) <= EPS && Math.abs((lb.minY - cb.minY) + 0.5d) <= EPS,
                "a painting on a -0.5 wall must hang on the drawn face (box 0.5 below the control's): " + report);
        ctx.assertTrue(Math.abs(onLowered.getY() - onControl.getY()) <= EPS,
                "the painting's entity position must stay at grid height: " + report);
        ctx.assertTrue(Math.abs(seatOf(overSlab)) <= EPS && Math.abs(sb.minY - cb.minY) <= EPS,
                "a painting on a FLUSH wall must ignore the slab under its cell: " + report);
        ctx.assertTrue(onLowered.survives() && onControl.survives() && overSlab.survives(),
                "every painting must still survive on its real wall: " + report);
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void aFrameSavedBeforeTheSeatExistedMintsFromItsWallOnceOnLoad(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        BlockPos wall = loweredWall(ctx, new BlockPos(2, 2, 2));
        ItemFrame frame = new ItemFrame(level, wall.relative(Direction.EAST), Direction.EAST);
        CompoundTag saved = new CompoundTag();
        frame.saveWithoutId(saved);
        saved.remove("slabbed:hang_dy"); // an old save: no remembered seat
        ItemFrame old = EntityType.ITEM_FRAME.create(level);
        ctx.assertTrue(old != null, "premise: could not create the frame");
        old.load(saved);
        level.addFreshEntity(old);
        ctx.assertTrue(Math.abs(seatOf(old) + 0.5d) <= EPS,
                "a frame with no saved seat must mint one from its wall on load (-0.5), got " + seatOf(old));
        ctx.succeed();
    }
}
