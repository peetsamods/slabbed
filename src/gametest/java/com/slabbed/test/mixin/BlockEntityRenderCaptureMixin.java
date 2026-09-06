package com.slabbed.test.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.Slabbed;
import com.slabbed.test.BlockEntityRenderAudit;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Observes the real block-entity dispatch matrices without changing render arguments. */
@Mixin(value = BlockEntityRenderDispatcher.class, priority = 500)
public abstract class BlockEntityRenderCaptureMixin {
    private static final boolean STACK_LOG = Boolean.getBoolean("slabbed.blockEntityStackAudit");
    private static long slabbed$dispatchCount;
    private static long slabbed$unbalancedDispatchCount;

    @WrapMethod(
            method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V"
    )
    private <E extends BlockEntity> void slabbed$captureInitialMatrix(
            E blockEntity, float tickDelta, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, Operation<Void> original
    ) {
        MatrixStack.Entry initialEntry = matrices.peek();
        BlockEntityRenderAudit.begin(blockEntity, matrices);
        try {
            original.call(blockEntity, tickDelta, matrices, vertexConsumers);
        } finally {
            MatrixStack.Entry finalEntry = matrices.peek();
            boolean balanced = finalEntry == initialEntry;
            slabbed$dispatchCount++;
            if (!balanced) {
                slabbed$unbalancedDispatchCount++;
            }
            if (STACK_LOG || !balanced) {
                Slabbed.LOGGER.info(
                        "[BLOCK_ENTITY_STACK] type={} piston={} dispatches={} unbalanced={} balanced={} entryBefore={} entryAfter={}",
                        Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType()),
                        slabbed$pistonIdentity(blockEntity), slabbed$dispatchCount,
                        slabbed$unbalancedDispatchCount, balanced,
                        System.identityHashCode(initialEntry), System.identityHashCode(finalEntry));
            }
            BlockEntityRenderAudit.end();
        }
    }

    private static String slabbed$pistonIdentity(BlockEntity blockEntity) {
        if (!(blockEntity instanceof PistonBlockEntity piston)) {
            return "none";
        }
        return Registries.BLOCK.getId(piston.getPushedBlock().getBlock())
                + "/" + piston.getFacing() + "/source=" + piston.isSource();
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V"
            )
    )
    private static <T extends BlockEntity> void slabbed$captureRendererMatrix(
            BlockEntityRenderer<T> renderer, T blockEntity, float tickDelta,
            MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            int light, int overlay
    ) {
        BlockEntityRenderAudit.observe(blockEntity, matrices);
        renderer.render(blockEntity, tickDelta, matrices, vertexConsumers, light, overlay);
    }
}
