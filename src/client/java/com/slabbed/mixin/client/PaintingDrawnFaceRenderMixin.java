package com.slabbed.mixin.client;

import com.slabbed.util.HangingSeatDyHolder;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Draws a painting on the face it remembers being hung on (maintainer ruling, 2026-09-13). The
 * entity's real position stays at grid height - see {@code HangingEntityRememberedSeatMixin} - so
 * the drawing is moved by the remembered seat, the same number the bounding box carries.
 *
 * <p>ADDED, not injected: the painting renderer does not declare {@code getRenderOffset} at all,
 * so there is no narrowed override for vanilla's dispatcher to reach and nothing to inject into.
 * The method below is supplied with the ERASED parameter type the dispatcher actually calls
 * ({@code getRenderOffset(Entity, float)}); a narrowed {@code getRenderOffset(Painting, float)}
 * would be a NEW method here rather than an override and would never be called. That is also why
 * the supertype is written raw - it makes the erased signature the one being overridden.
 *
 * <p>The vanilla offset is taken from {@code super} and added to, never replaced: the base answer
 * is zero today, and hardcoding that would silently drop any offset a future patch introduces.
 */
@Mixin(PaintingRenderer.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class PaintingDrawnFaceRenderMixin extends EntityRenderer {

    protected PaintingDrawnFaceRenderMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Vec3 getRenderOffset(Entity entity, float partialTicks) {
        Vec3 vanilla = super.getRenderOffset(entity, partialTicks);
        if (!(entity instanceof Painting painting)) {
            return vanilla;
        }
        // The REMEMBERED seat, the same number the bounding box carries. Never a fresh read of
        // the wall (LAW.md, Law 1; maintainer ruling, 2026-09-13).
        double dy = ((HangingSeatDyHolder) painting).slabbed$hangSeatDy();
        if (Math.abs(dy) < 1.0e-6d) {
            return vanilla;
        }
        return new Vec3(vanilla.x, vanilla.y + dy, vanilla.z);
    }
}
