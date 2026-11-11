package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Entity.class)
public abstract class EntityLoweredFootstepMixin {
    @ModifyArg(method = "vibrationAndSoundEffectsFromBlock", index = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gameevent/GameEvent$Context;of(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/gameevent/GameEvent$Context;"))
    private BlockState slabbed$useContactingDampeningSurface(Entity source, BlockState original) {
        // Vanilla identifies a step by its grid cell. A lowered carpet belongs to the cell above
        // the feet, so preserve its vibration semantics only when its actual collision top is touched.
        // LAW.md: this reads the placed height and never changes it or derives another seat.
        if (original.is(BlockTags.DAMPENS_VIBRATIONS)) return original;
        double feetY = source.getBoundingBox().minY;
        BlockPos candidate = BlockPos.containing(source.getX(), feetY - 1.0e-6d, source.getZ()).above();
        BlockState state = source.level().getBlockState(candidate);
        if (!state.is(BlockTags.DAMPENS_VIBRATIONS)
                || SlabSupport.getYOffset(source.level(), candidate, state) >= -1.0e-6d) {
            return original;
        }
        VoxelShape shape = state.getCollisionShape(source.level(), candidate, CollisionContext.of(source));
        double localX = source.getX() - candidate.getX();
        double localZ = source.getZ() - candidate.getZ();
        double localFeetY = feetY - candidate.getY();
        for (AABB box : shape.toAabbs()) {
            if (Math.abs(box.maxY - localFeetY) <= 1.0e-5d
                    && localX >= box.minX && localX <= box.maxX
                    && localZ >= box.minZ && localZ <= box.maxZ) {
                return state;
            }
        }
        return original;
    }
}
