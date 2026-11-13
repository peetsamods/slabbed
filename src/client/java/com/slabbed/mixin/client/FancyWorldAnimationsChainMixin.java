package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.client.model.ChainCeilingGeometry;
import com.slabbed.util.ChainBridgeTextureVariant;
import com.slabbed.util.SlabSupport;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The top bridge segment's model, swing pivot and descent share one extended length. */
@Pseudo
@Mixin(targets = "fr.madu59.fwa.anims.AbstractChainAnimation", remap = false)
public abstract class FancyWorldAnimationsChainMixin {
    @Shadow protected int chainCount;

    @Unique private float slabbed$bridgeExtension() {
        var world = Minecraft.getInstance().level;
        if (world == null || chainCount < 1) return 0;
        BlockPos top = ((FancyWorldAnimationsAccessor) this).slabbed$getPos().above(chainCount - 1);
        var state = world.getBlockState(top);
        return ChainCeilingGeometry.usesAlternateGeometry(world, top, state,
                SlabSupport.getYOffset(world, top, state)) ? 0.5f : 0;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0), index = 1)
    private float slabbed$bridgePivot(float y) { return y + slabbed$bridgeExtension(); }

    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 1), index = 1)
    private float slabbed$bridgeModelOrigin(float y, @Local(index = 8) int segment) {
        return segment == chainCount - 1 ? y - slabbed$bridgeExtension() : y;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 2), index = 1)
    private float slabbed$bridgeDescent(float y, @Local(index = 8) int segment) {
        return segment == chainCount - 1 ? y - slabbed$bridgeExtension() : y;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;collectParts(Lnet/minecraft/util/RandomSource;Ljava/util/List;)V"))
    private void slabbed$bridgeModel(BlockStateModel model, RandomSource random,
            List<BlockStateModelPart> parts, Operation<Void> original,
            @Local BlockPos.MutableBlockPos pos, @Local BlockState state) {
        var world = Minecraft.getInstance().level;
        if (world != null && ChainCeilingGeometry.usesAlternateGeometry(world, pos, state,
                SlabSupport.getYOffset(world, pos, state))) {
            var alternate = ChainCeilingGeometry.alternateModel(ChainBridgeTextureVariant.forBlock(state));
            if (alternate != null) model = alternate;
        }
        original.call(model, random, parts);
    }
}
