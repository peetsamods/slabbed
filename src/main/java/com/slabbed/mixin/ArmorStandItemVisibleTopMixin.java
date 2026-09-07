package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * An armor stand is checked for room where it will actually stand (maintainer ruling, 2026-09-06).
 *
 * <p>WHERE THE STAND LANDS IS ALREADY RIGHT and this class must not touch it. Vanilla creates the
 * stand with its own downward probe — it drops the stand onto the highest COLLISION surface within a
 * block of the spawn cell — and a lowered block's collision already follows its drawn body, so the
 * stand settles on the visible top by itself. Applying a second drop here would sink it half a block
 * into the ground; {@code ArmorStandVisibleTopPlacementTest} pins that it does not.
 *
 * <p>WHAT WAS WRONG is the free-space test that runs BEFORE that. It builds one box at the spawn
 * cell's GRID floor and asks both {@code noCollision} and {@code getEntities} about it, so over a
 * lowered block the game checked a band the stand will never occupy: it refused stands that plainly
 * fit, and accepted stands into space that was not free. One wrapped call moves that box to the band
 * the stand will really occupy, so the check and the placement agree.
 *
 * <p>INVARIANT: the drop is derived from the block UNDER THE SPAWN CELL — {@code new
 * BlockPlaceContext(ctx).getClickedPos().below()} — never from the clicked block. Those differ
 * whenever the clicked block is replaceable (a snow layer, tall grass, a fluid), where the spawn
 * cell IS the clicked cell.
 *
 * <p>INVARIANT: only a cell with a STORED lowered height moves the check, and the drop only ever
 * lowers. A block that merely looks short — an ordinary slab — keeps vanilla's answer exactly.
 */
@Mixin(ArmorStandItem.class)
public abstract class ArmorStandItemVisibleTopMixin {

    @Unique
    private static final double SLABBED$EPSILON = 1.0e-6d;

    @WrapOperation(
            method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)"
                    + "Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/EntityDimensions;"
                           + "makeBoundingBox(DDD)Lnet/minecraft/world/phys/AABB;"))
    private AABB slabbed$checkVisibleBand(
            EntityDimensions dimensions, double x, double y, double z,
            Operation<AABB> original,
            @Local(argsOnly = true) UseOnContext context) {
        return original.call(dimensions, x, y + slabbed$feetDrop(context), z);
    }

    /**
     * How far below its spawn cell's floor the stand's feet will be. Never positive: this lane only
     * ever follows a surface that is drawn below the grid, it never lifts a stand.
     *
     * <p>The DRAWN TOP must be read through the three-argument collision query. The two-argument
     * overload answers a state's CACHED shape and never reaches the lowering exit, so it would report
     * the un-lowered grid top for every cached full block.
     */
    @Unique
    private static double slabbed$feetDrop(UseOnContext context) {
        Level level = context.getLevel();
        if (level == null) {
            return 0.0d;
        }
        BlockPos spawnCell = new BlockPlaceContext(context).getClickedPos();
        BlockPos support = spawnCell.below();
        if (!level.hasChunkAt(support)) {
            return 0.0d;
        }
        BlockState state = level.getBlockState(support);
        double dy = SlabSupport.getYOffset(level, support, state);
        if (!Double.isFinite(dy) || Math.abs(dy) <= SLABBED$EPSILON) {
            return 0.0d;
        }
        VoxelShape collision = state.getCollisionShape(level, support, CollisionContext.empty());
        if (collision.isEmpty()) {
            return 0.0d;
        }
        double drawnTop = support.getY() + collision.max(Direction.Axis.Y);
        if (!Double.isFinite(drawnTop)) {
            return 0.0d;
        }
        double drop = drawnTop - spawnCell.getY();
        return drop < -SLABBED$EPSILON ? drop : 0.0d;
    }
}
