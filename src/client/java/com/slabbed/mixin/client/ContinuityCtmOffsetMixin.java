package com.slabbed.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.slabbed.client.ClientDy;
import com.slabbed.client.model.ContinuityModelAdmission;
import com.slabbed.client.model.ContinuityQuadOffset;
import com.slabbed.compat.CompatHooks;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

/** CTM keeps its identity; only positively admitted vanilla delegates gain a translation owner. */
@Pseudo
@Mixin(targets = "me.pepperbell.continuity.client.model.CtmBakedModel", remap = false)
public abstract class ContinuityCtmOffsetMixin {
    @Unique
    private volatile Boolean slabbed$vanillaDelegateTree;

    @WrapMethod(method = "emitBlockQuads")
    private void slabbed$translateCtm(BlockAndTintGetter view, BlockState state, BlockPos pos,
                                     Supplier<RandomSource> random, RenderContext context,
                                     Operation<Void> original) {
        if (!getClass().getName().equals("me.pepperbell.continuity.client.model.CtmBakedModel")
                || CompatHooks.shouldSkipOffsetView(view)) {
            original.call(view, state, pos, random, context);
            return;
        }
        Boolean admitted = slabbed$vanillaDelegateTree;
        if (admitted == null) {
            admitted = ContinuityModelAdmission.canTranslate(((ForwardingBakedModel) (Object) this).getWrappedModel());
            slabbed$vanillaDelegateTree = admitted;
        }
        if (!admitted) {
            original.call(view, state, pos, random, context);
            return;
        }
        float dy = (float) ClientDy.dyFor(view, pos, state);
        ContinuityQuadOffset.emit(context, dy, () -> original.call(view, state, pos, random, context));
    }
}
