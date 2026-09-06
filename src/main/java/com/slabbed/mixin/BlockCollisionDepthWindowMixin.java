package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.upgrade.WorldUpgradeRuntimePolicy;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockCollisionSpliterator;
import net.minecraft.world.CollisionView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.function.BiFunction;

/** Stored collision owners must be visited even when their bodies lie below their cells (LAW.md). */
@Mixin(BlockCollisionSpliterator.class)
public abstract class BlockCollisionDepthWindowMixin {
    @ModifyArgs(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/CuboidBlockIterator;<init>(IIIIII)V"))
    private void slabbed$includeDeepCollisionOwners(Args args, CollisionView collisionView,
                                                     Entity entity, Box box, boolean forEntity,
                                                     BiFunction<BlockPos.Mutable, VoxelShape, ?> resultFunction) {
        if (SlabAnchorAttachment.FROZEN_DY_ENABLED
                || collisionView instanceof World world
                && WorldUpgradeRuntimePolicy.authorsModernPlacements(world)) {
            int maxY = args.get(4);
            int radius = (int) Math.ceil(-SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY);
            args.set(4, maxY + radius);
        }
    }
}
