package com.slabbed.mixin.client;

import com.slabbed.client.ClientDy;
import net.minecraft.block.BlockState;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.PaleMossCarpetBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * THE CARPET OUTLINE LIVES HERE, AND ONLY HERE.
 *
 * <p>Two mixins could offset a carpet's outline and exactly one must:
 * <ul>
 *   <li>this one, on {@code CarpetBlock/PaleMossCarpetBlock.getOutlineShape} — CLIENT-only
 *       ({@code slabbed.client.mixins.json});</li>
 *   <li>{@code SlabSupportStateMixin.slabbed$offsetOutline}, on
 *       {@code AbstractBlockState.getOutlineShape} — COMMON, and it explicitly skips carpets.</li>
 * </ul>
 * With both live the shape is offset twice ({@code AbstractBlockState.getOutlineShape} calls
 * {@code block.getOutlineShape}), i.e. a carpet on a bottom slab lands at {@code -1.0}. That is
 * what {@code SlabbedLabFixtureTest#carpetOutlineNotDoubled} exists to catch.
 *
 * <p><b>Why the CLIENT owns it rather than the common layer.</b> Vanilla's
 * {@code AbstractBlock.getCollisionShape} is {@code collidable ? state.getOutlineShape(world,pos)
 * : empty}, and {@code CarpetBlock} does not override {@code getCollisionShape} — a carpet's
 * collision box IS its outline. Moving the offset to the common mixin would therefore move the
 * carpet's collision box on the SERVER: physics, smuggled in under a rendering fix, and precisely
 * the "outline dy bled into movement collision" regression this project already shipped once
 * (26.1.2 ghost). Keeping it client-side leaves the authoritative server geometry byte-identical to
 * vanilla, which is what {@code carpetOutlineNotDoubled} asserts ({@code minY == 0.0} server-side).
 * The client is also the only consumer that needs the outline to agree with the model — the
 * wireframe and the pick raycast, which reads {@code state.getOutlineShape} in
 * {@code SlabbedOffsetRaycast} — and {@code ServerInteractBlockHitToleranceMixin} already absorbs
 * the resulting client/server hit-position delta.
 *
 * <p><b>What changed for BUG A.</b> Nothing here. This mixin always deferred to
 * {@link ClientDy#dyFor}; the defect was that {@code ClientDy} carried its own anchor-blind carpet
 * shortcut. {@code ClientDy.dyFor} is now a pure delegate to
 * {@code SlabSupport.getVisualYOffset}, so this outline, the chunk model and the common raycast all
 * read one number.
 */
@Mixin({CarpetBlock.class, PaleMossCarpetBlock.class})
public class CarpetDyShapeMixin {

    @Inject(method = "getOutlineShape", at = @At("RETURN"), cancellable = true)
    private void slabbed$offsetCarpetOutline(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx,
                                             CallbackInfoReturnable<VoxelShape> cir) {
        // RAW-SHAPE PROBE: SlabSupport is measuring this block's own un-offset outline to decide
        // where its top face is. Offsetting it here would feed the answer back into the question.
        // See SlabSupport.isRawShapeProbeActive.
        if (com.slabbed.util.SlabSupport.isRawShapeProbeActive()) {
            return;
        }
        double dy = ClientDy.dyFor(world, pos, state);
        if (dy != 0.0) {
            cir.setReturnValue(cir.getReturnValue().offset(0.0, dy, 0.0));
        }
    }
}
