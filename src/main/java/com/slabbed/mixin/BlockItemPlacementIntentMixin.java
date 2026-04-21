package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(BlockItem.class)
public abstract class BlockItemPlacementIntentMixin {

    @ModifyArg(
            method = "useOnBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemPlacementContext;<init>(Lnet/minecraft/item/ItemUsageContext;)V"
            )
    )
    private ItemUsageContext slabbed$remapLoweredFullBlockSideHit(ItemUsageContext context) {
        if (!(((BlockItem) (Object) this).getBlock() instanceof SlabBlock)) {
            return context;
        }

        Direction side = context.getSide();
        if (!side.getAxis().isHorizontal()) {
            return context;
        }

        BlockPos targetPos = context.getBlockPos();
        BlockState targetState = context.getWorld().getBlockState(targetPos);
        if (!targetState.isSolidBlock(context.getWorld(), targetPos)) {
            return context;
        }
        if (targetState.getBlock() instanceof BlockEntityProvider) {
            return context;
        }
        if (targetState.getBlock() instanceof CraftingTableBlock) {
            return context;
        }
        if (SlabSupport.getYOffset(context.getWorld(), targetPos, targetState) != -0.5d) {
            return context;
        }

        // Keep hit Y just below the slab half-split to avoid TOP reinterpretation.
        Vec3d hitPos = context.getHitPos();
        Vec3d remappedHitPos = new Vec3d(hitPos.x, targetPos.getY() + 0.499d, hitPos.z);
        BlockHitResult remappedHit = new BlockHitResult(
                remappedHitPos,
                side,
                targetPos,
                context.hitsInsideBlock(),
                false
        );

        return new ItemUsageContext(context.getWorld(), context.getPlayer(), context.getHand(), context.getStack(), remappedHit) {
        };
    }
}
