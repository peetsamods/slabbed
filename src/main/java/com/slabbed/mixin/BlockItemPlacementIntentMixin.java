package com.slabbed.mixin;

import com.slabbed.dev.audit.LoweredSideLiveHitRemapRuntimeAudit;
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
        boolean itemIsSlab = ((BlockItem) (Object) this).getBlock() instanceof SlabBlock;
        if (!itemIsSlab) {
            LoweredSideLiveHitRemapRuntimeAudit.recordRemapAttempt(
                    context,
                    false,
                    false,
                    false,
                    false,
                    false,
                    0.0d,
                    false,
                    false,
                    "item_not_slab",
                    null);
            return context;
        }

        Direction side = context.getSide();
        boolean faceHorizontal = side.getAxis().isHorizontal();
        if (!faceHorizontal) {
            LoweredSideLiveHitRemapRuntimeAudit.recordRemapAttempt(
                    context,
                    true,
                    false,
                    false,
                    false,
                    false,
                    0.0d,
                    false,
                    false,
                    "face_not_horizontal",
                    null);
            return context;
        }

        BlockPos targetPos = context.getBlockPos();
        BlockState targetState = context.getWorld().getBlockState(targetPos);
        boolean targetIsSolid = targetState.isSolidBlock(context.getWorld(), targetPos);
        boolean targetHasBlockEntity = LoweredSideLiveHitRemapRuntimeAudit.hasBlockEntityProvider(targetState);
        boolean targetIsCraftingTable = targetState.getBlock() instanceof CraftingTableBlock;
        double yOffset = SlabSupport.getYOffset(context.getWorld(), targetPos, targetState);
        boolean ordinaryLoweredFullBlockGuard = targetIsSolid
                && !targetHasBlockEntity
                && !targetIsCraftingTable
                && yOffset == -0.5d;

        if (!targetIsSolid) {
            LoweredSideLiveHitRemapRuntimeAudit.recordRemapAttempt(
                    context,
                    true,
                    true,
                    false,
                    targetHasBlockEntity,
                    targetIsCraftingTable,
                    yOffset,
                    ordinaryLoweredFullBlockGuard,
                    false,
                    "target_not_solid",
                    null);
            return context;
        }
        if (targetHasBlockEntity) {
            LoweredSideLiveHitRemapRuntimeAudit.recordRemapAttempt(
                    context,
                    true,
                    true,
                    true,
                    true,
                    targetIsCraftingTable,
                    yOffset,
                    ordinaryLoweredFullBlockGuard,
                    false,
                    "target_has_block_entity",
                    null);
            return context;
        }
        if (targetIsCraftingTable) {
            LoweredSideLiveHitRemapRuntimeAudit.recordRemapAttempt(
                    context,
                    true,
                    true,
                    true,
                    false,
                    true,
                    yOffset,
                    ordinaryLoweredFullBlockGuard,
                    false,
                    "target_is_crafting_table",
                    null);
            return context;
        }
        if (yOffset != -0.5d) {
            LoweredSideLiveHitRemapRuntimeAudit.recordRemapAttempt(
                    context,
                    true,
                    true,
                    true,
                    false,
                    false,
                    yOffset,
                    ordinaryLoweredFullBlockGuard,
                    false,
                    "y_offset_not_-0.5",
                    null);
            return context;
        }

        // Keep hit Y just below the slab half-split to avoid TOP reinterpretation.
        Vec3d hitPos = context.getHitPos();
        Vec3d remappedHitPos = new Vec3d(hitPos.x, targetPos.getY() + 0.499d, hitPos.z);
        LoweredSideLiveHitRemapRuntimeAudit.recordRemapAttempt(
                context,
                true,
                true,
                true,
                false,
                false,
                yOffset,
                ordinaryLoweredFullBlockGuard,
                true,
                "none",
                remappedHitPos);
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
