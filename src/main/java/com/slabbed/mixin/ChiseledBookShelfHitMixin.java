package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Bookshelf slots use local coordinates while the physical hit remains in the drawn world frame. */
@Mixin(ChiseledBookShelfBlock.class)
public abstract class ChiseledBookShelfHitMixin {
    @Redirect(method = "useItemOn", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/ChiseledBookShelfBlock;getHitSlot(Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/core/Direction;)Ljava/util/OptionalInt;"))
    private OptionalInt slabbed$insertIntoDrawnSlot(ChiseledBookShelfBlock shelf,
            BlockHitResult hit, Direction facing, ItemStack stack, BlockState state,
            Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult physicalHit) {
        return shelf.getHitSlot(slabbed$localHit(hit, level, pos, state), facing);
    }

    @Redirect(method = "useWithoutItem", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/block/ChiseledBookShelfBlock;getHitSlot(Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/core/Direction;)Ljava/util/OptionalInt;"))
    private OptionalInt slabbed$removeFromDrawnSlot(ChiseledBookShelfBlock shelf,
            BlockHitResult hit, Direction facing, BlockState state, Level level,
            BlockPos pos, Player player, BlockHitResult physicalHit) {
        return shelf.getHitSlot(slabbed$localHit(hit, level, pos, state), facing);
    }

    private static BlockHitResult slabbed$localHit(BlockHitResult hit, Level level,
            BlockPos pos, BlockState state) {
        double dy = SlabSupport.getYOffset(level, pos, state);
        if (dy == 0.0d) return hit;
        return new BlockHitResult(hit.getLocation().add(0.0d, -dy, 0.0d),
                hit.getDirection(), hit.getBlockPos(), hit.isInside(), hit.isWorldBorderHit());
    }
}
