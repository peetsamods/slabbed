package com.slabbed.client.model;

import com.slabbed.util.FenceCeilingConnection;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import java.util.function.Supplier;

/** Adds a same-texture post segment without moving the seat or horizontal rails. */
public final class FenceCeilingGeometry {
    private static final float EPS=1.0e-5f;
    private FenceCeilingGeometry() { }

    public static boolean emitIfConnected(BakedModel wrapped,BlockRenderView view,BlockState state,
            BlockPos pos,Supplier<Random> randomSupplier,RenderContext context,float seat) {
        if (!(state.getBlock() instanceof FenceBlock)) return false;
        float extension;
        try { extension=(float)FenceCeilingConnection.postTop(view,pos,state,seat)-1.0f; }
        catch (IndexOutOfBoundsException outsideRenderRegion) { return false; }
        Renderer renderer=RendererAccess.INSTANCE.getRenderer();
        if (extension<=EPS || renderer==null) return false;
        MeshBuilder captured=renderer.meshBuilder();
        QuadEmitter capture=captured.getEmitter();
        context.pushTransform(quad -> { capture.copyFrom(quad);capture.emit();return false; });
        try {
            FabricBakedModel fabric = wrapped; // every BakedModel is one through interface injection
            if (!fabric.isVanillaAdapter()) {
                fabric.emitBlockQuads(view,state,pos,randomSupplier,context);
            } else context.bakedModelConsumer().accept(wrapped,state);
        } finally { context.popTransform(); }
        captured.build().forEach(quad -> emit(context.getEmitter(),quad,seat,extension));
        return true;
    }

    private static void emit(QuadEmitter out, QuadView quad, float seat, float extension) {
        boolean post = true;
        float low = Float.POSITIVE_INFINITY;
        float high = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            post &= quad.x(i) >= 0.375f-EPS && quad.x(i) <= 0.625f+EPS
                    && quad.z(i) >= 0.375f-EPS && quad.z(i) <= 0.625f+EPS;
            low = Math.min(low, quad.y(i));
            high = Math.max(high, quad.y(i));
        }
        boolean top = post && Math.abs(low-1.0f) <= EPS && Math.abs(high-1.0f) <= EPS;
        out.copyFrom(quad);
        for (int i = 0; i < 4; i++) {
            out.pos(i, quad.x(i), quad.y(i)+seat+(top ? extension : 0.0f), quad.z(i));
        }
        if (top) out.cullFace(null);
        out.emit();
        if (!post || Math.abs(low) > EPS || Math.abs(high-1.0f) > EPS) {
            return;
        }
        out.copyFrom(quad);
        out.cullFace(null);
        for (int i = 0; i < 4; i++) {
            out.pos(i, quad.x(i), 1.0f+seat+quad.y(i)*extension, quad.z(i));
            if (quad.y(i) > EPS) {
                for (int j = 0; j < 4; j++) {
                    if (quad.y(j) <= EPS && Math.abs(quad.x(i)-quad.x(j)) <= EPS
                            && Math.abs(quad.z(i)-quad.z(j)) <= EPS) {
                        out.uv(i, quad.u(j)+(quad.u(i)-quad.u(j))*extension,
                                quad.v(j)+(quad.v(i)-quad.v(j))*extension);
                        break;
                    }
                }
            }
        }
        out.emit();
    }
}
