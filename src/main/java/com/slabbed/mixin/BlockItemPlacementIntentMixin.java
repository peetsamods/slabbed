package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedAuditBridge;
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

    private static final double UP_FACE_EDGE_BAND = 0.20d;

    private static Direction slabbed$inferLoweredSideFromUpFaceHit(Vec3d hitPos, BlockPos targetPos) {
        double localX = hitPos.x - targetPos.getX();
        double localZ = hitPos.z - targetPos.getZ();
        if (localX < 0.0d || localX > 1.0d || localZ < 0.0d || localZ > 1.0d) {
            return null;
        }

        double distWest = localX;
        double distEast = 1.0d - localX;
        double distNorth = localZ;
        double distSouth = 1.0d - localZ;

        double min = distWest;
        Direction nearest = Direction.WEST;
        if (distEast < min) {
            min = distEast;
            nearest = Direction.EAST;
        }
        if (distNorth < min) {
            min = distNorth;
            nearest = Direction.NORTH;
        }
        if (distSouth < min) {
            min = distSouth;
            nearest = Direction.SOUTH;
        }

        return min <= UP_FACE_EDGE_BAND ? nearest : null;
    }

    private static final Class<?>[] REMAP_ATTEMPT_PARAM_TYPES = new Class<?>[]{
            ItemUsageContext.class,
            boolean.class,
            boolean.class,
            boolean.class,
            boolean.class,
            boolean.class,
            double.class,
            boolean.class,
            boolean.class,
            String.class,
            Vec3d.class,
            Direction.class,
            String.class
    };

    private static void slabbed$recordRemapAttempt(
            ItemUsageContext context,
            boolean itemIsSlab,
            boolean faceHorizontal,
            boolean targetIsSolid,
            boolean targetHasBlockEntity,
            boolean targetIsCraftingTable,
            double yOffset,
            boolean ordinaryLoweredFullBlockGuard,
            boolean remapped,
            String rejectionReason,
            Vec3d remappedHitPos,
            Direction effectiveSide,
            String hitDescriptor) {
        SlabbedAuditBridge.invoke(
                "recordRemapAttempt",
                REMAP_ATTEMPT_PARAM_TYPES,
                context,
                itemIsSlab,
                faceHorizontal,
                targetIsSolid,
                targetHasBlockEntity,
                targetIsCraftingTable,
                yOffset,
                ordinaryLoweredFullBlockGuard,
                remapped,
                rejectionReason,
                remappedHitPos,
                effectiveSide,
                hitDescriptor);
    }

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
            slabbed$recordRemapAttempt(
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
                    null,
                    null,
                    "none");
            return context;
        }

        Direction originalSide = context.getSide();
        Vec3d originalHitPos = context.getHitPos();
        Direction effectiveSide = originalSide;
        boolean inferredUpFaceLoweredSide = false;
        if (originalSide == Direction.UP) {
            Direction inferred = slabbed$inferLoweredSideFromUpFaceHit(originalHitPos, context.getBlockPos());
            if (inferred != null) {
                effectiveSide = inferred;
                inferredUpFaceLoweredSide = true;
            }
        }

        boolean faceHorizontal = effectiveSide.getAxis().isHorizontal();
        if (!faceHorizontal) {
            slabbed$recordRemapAttempt(
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
                    null,
                    null,
                    "none");
            return context;
        }

        BlockPos targetPos = context.getBlockPos();
        BlockState targetState = context.getWorld().getBlockState(targetPos);
        boolean targetIsSolid = targetState.isSolidBlock(context.getWorld(), targetPos);
        boolean targetHasBlockEntity = targetState.getBlock() instanceof BlockEntityProvider;
        boolean targetIsCraftingTable = targetState.getBlock() instanceof CraftingTableBlock;
        double yOffset = SlabSupport.getYOffset(context.getWorld(), targetPos, targetState);
        boolean ordinaryLoweredFullBlockGuard = targetIsSolid
                && !targetHasBlockEntity
                && !targetIsCraftingTable
                && yOffset == -0.5d;

        if (!targetIsSolid) {
            slabbed$recordRemapAttempt(
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
                    null,
                    effectiveSide,
                    inferredUpFaceLoweredSide ? "up_face_edge" : "horizontal_face");
            return context;
        }
        if (targetHasBlockEntity) {
            slabbed$recordRemapAttempt(
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
                    null,
                    effectiveSide,
                    inferredUpFaceLoweredSide ? "up_face_edge" : "horizontal_face");
            return context;
        }
        if (targetIsCraftingTable) {
            slabbed$recordRemapAttempt(
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
                    null,
                    effectiveSide,
                    inferredUpFaceLoweredSide ? "up_face_edge" : "horizontal_face");
            return context;
        }
        if (yOffset != -0.5d) {
            slabbed$recordRemapAttempt(
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
                    null,
                    effectiveSide,
                    inferredUpFaceLoweredSide ? "up_face_edge" : "horizontal_face");
            return context;
        }

        // Decide BOTTOM vs TOP from the *original* hit Y relative to the lowered
        // FB visual half-split. Lowered FB visual spans world Y ∈
        // [targetPos.y - 0.5, targetPos.y + 0.5], so targetPos.y is the half-line.
        //   originalHitPos.y < targetPos.y  → lower visual half  → BS-FB-0.5S (BOTTOM)
        //   originalHitPos.y >= targetPos.y → upper visual half  → BS-FB-1S  (TOP)
        // The remapped Y is then clamped just inside the matching half so that
        // vanilla SlabBlock.getPlacementState's `hit.y - placePos.y <= 0.5`
        // discriminator picks the desired SlabType. (placePos.y == targetPos.y
        // because the side offset is horizontal.)
        boolean upperHalfIntent = originalHitPos.y >= targetPos.getY();
        double remappedY = upperHalfIntent
                ? targetPos.getY() + 0.501d   // > 0.5 → vanilla → TOP
                : targetPos.getY() + 0.499d;  // ≤ 0.5 → vanilla → BOTTOM
        Vec3d remappedHitPos = new Vec3d(originalHitPos.x, remappedY, originalHitPos.z);
        slabbed$recordRemapAttempt(
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
                remappedHitPos,
                effectiveSide,
                inferredUpFaceLoweredSide ? "up_face_edge" : "horizontal_face");
        BlockHitResult remappedHit = new BlockHitResult(
                remappedHitPos,
                effectiveSide,
                targetPos,
                context.hitsInsideBlock(),
                false
        );

        return new ItemUsageContext(context.getWorld(), context.getPlayer(), context.getHand(), context.getStack(), remappedHit) {
        };
    }
}
