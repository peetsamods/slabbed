package com.slabbed.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Predicate;

/**
 * Emitter wrapper that shifts vertex Y and clears step-seam cull faces, delegating everything else.
 *
 * <p><b>PERF (render-path fix-round F2).</b> This used to be a JDK dynamic {@link java.lang.reflect.Proxy}.
 * Every single {@code QuadEmitter} call the wrapped model made — {@code fromBakedQuad}, {@code
 * materialBake}, {@code color}, {@code emit}, all of it — went through {@code InvocationHandler.invoke}
 * with a boxed {@code Object[]} argument array and a {@code Method.invoke} reflective dispatch the JIT
 * cannot inline. It is engaged for any offset block and for any flat block adjacent to one, i.e. most
 * blocks in a built-up world, on the chunk-mesh worker threads. Hand-writing the delegation removes the
 * reflection, the boxing and the per-call array, and lets the whole emit path inline again.
 *
 * <p><b>The behaviour is a literal transcription of the old handler</b>, quirks included, because
 * nothing in the test suite pins this class:
 * <ul>
 *   <li>{@link #pos(int, float, float, float)} adds {@code dy} to the supplied Y — exactly the old
 *       {@code args[2] = y + dy} branch, which matched by name/arity and therefore also fired for the
 *       {@code MutableQuadView} bridge. A hand-written covariant override generates that same bridge.</li>
 *   <li>{@link #emit()} pushes the cull-clearing transform, rewrites all four vertices from the
 *       delegate's own current positions with {@code y + dy}, emits, then pops — and returns the
 *       delegate's raw return value <em>without</em> the self-return rewrap the other methods do,
 *       because the old handler returned early from its {@code emit} branch and never reached the
 *       rewrap. Preserved deliberately; changing it is a rendering change, not a cleanup.</li>
 *   <li>Every other method delegates to the delegate directly and is rewrapped only when the delegate
 *       returned <em>itself</em>, matching the old {@code result == delegate → proxy} rule. This is why
 *       every {@code default} method of the interface is overridden here: an un-overridden default would
 *       run its body against {@code this}, re-entering the shifting {@link #pos} four times and
 *       double-offsetting the quad.</li>
 * </ul>
 */
public final class YOffsetEmitter implements QuadEmitter {
    private final QuadEmitter delegate;
    private final float dy;
    /** Null only when no cull-face clearing was requested; the old handler skipped push/pop then. */
    private final QuadTransform clearCullTransform;

    private YOffsetEmitter(QuadEmitter delegate, float dy, QuadTransform clearCullTransform) {
        this.delegate = delegate;
        this.dy = dy;
        this.clearCullTransform = clearCullTransform;
    }

    public static QuadEmitter wrap(QuadEmitter delegate, float dy) {
        return wrap(delegate, dy, direction -> false);
    }

    public static QuadEmitter wrap(QuadEmitter delegate, float dy, Predicate<Direction> clearCullFace) {
        return new YOffsetEmitter(delegate, dy,
                clearCullFace == null ? null : clearCullFaceTransform(clearCullFace));
    }

    /**
     * Zero-extra-allocation entry point: the caller supplies a transform it already owns (fix-round F3
     * hands in the per-block seam state, which is both the cull predicate and this transform).
     *
     * <p>Deliberately not an overload of {@code wrap}: {@link Predicate} and {@link QuadTransform} are
     * both {@code (something) -> boolean}, so a lambda argument would be ambiguous at the call site.
     */
    public static QuadEmitter wrapWithTransform(QuadEmitter delegate, float dy,
                                                QuadTransform clearCullTransform) {
        return new YOffsetEmitter(delegate, dy, clearCullTransform);
    }

    private static QuadTransform clearCullFaceTransform(Predicate<Direction> clearCullFace) {
        return quad -> {
            Direction cullFace = quad.cullFace();
            if (cullFace != null && clearCullFace.test(cullFace)) {
                quad.cullFace(null);
                quad.nominalFace(cullFace);
            }
            return true;
        };
    }

    /** The old handler's {@code result == delegate ? proxy : result} rule, for fluent self-returns. */
    private QuadEmitter rewrap(QuadEmitter result) {
        return result == delegate ? this : result;
    }

    private MutableQuadView rewrapView(MutableQuadView result) {
        return result == delegate ? this : result;
    }

    // ── the two methods that are not plain delegation ────────────────────────────────────────────────

    @Override
    public QuadEmitter pos(int vertexIndex, float x, float y, float z) {
        return rewrap(delegate.pos(vertexIndex, x, y + dy, z));
    }

    @Override
    public QuadEmitter emit() {
        if (clearCullTransform != null) {
            delegate.pushTransform(clearCullTransform);
        }
        try {
            for (int i = 0; i < 4; i++) {
                float x = delegate.x(i);
                float y = delegate.y(i);
                float z = delegate.z(i);
                delegate.pos(i, x, y + dy, z);
            }
            return delegate.emit();
        } finally {
            if (clearCullTransform != null) {
                delegate.popTransform();
            }
        }
    }

    // ── QuadEmitter / MutableQuadView: plain delegation ──────────────────────────────────────────────

    @Override
    public QuadEmitter pos(int vertexIndex, Vector3f pos) {
        return rewrap(delegate.pos(vertexIndex, pos));
    }

    @Override
    public QuadEmitter pos(int vertexIndex, Vector3fc pos) {
        return rewrap(delegate.pos(vertexIndex, pos));
    }

    @Override
    public QuadEmitter translate(float x, float y, float z) {
        return rewrap(delegate.translate(x, y, z));
    }

    @Override
    public QuadEmitter color(int vertexIndex, int color) {
        return rewrap(delegate.color(vertexIndex, color));
    }

    @Override
    public QuadEmitter color(int c0, int c1, int c2, int c3) {
        return rewrap(delegate.color(c0, c1, c2, c3));
    }

    @Override
    public QuadEmitter multiplyColor(int color) {
        return rewrap(delegate.multiplyColor(color));
    }

    @Override
    public QuadEmitter uv(int vertexIndex, float u, float v) {
        return rewrap(delegate.uv(vertexIndex, u, v));
    }

    @Override
    public QuadEmitter uv(int vertexIndex, Vector2f uv) {
        return rewrap(delegate.uv(vertexIndex, uv));
    }

    @Override
    public QuadEmitter uv(int vertexIndex, Vector2fc uv) {
        return rewrap(delegate.uv(vertexIndex, uv));
    }

    @Override
    public MutableQuadView uvUnitSquare() {
        return rewrapView(delegate.uvUnitSquare());
    }

    @Override
    public QuadEmitter materialBake(Material.Baked material, int bakeFlags) {
        return rewrap(delegate.materialBake(material, bakeFlags));
    }

    @Override
    public QuadEmitter postMaterialBake(Material.Baked material) {
        return rewrap(delegate.postMaterialBake(material));
    }

    @Override
    public QuadEmitter lightmap(int vertexIndex, int lightmap) {
        return rewrap(delegate.lightmap(vertexIndex, lightmap));
    }

    @Override
    public QuadEmitter lightmap(int l0, int l1, int l2, int l3) {
        return rewrap(delegate.lightmap(l0, l1, l2, l3));
    }

    @Override
    public QuadEmitter minLightmap(int lightmap) {
        return rewrap(delegate.minLightmap(lightmap));
    }

    @Override
    public QuadEmitter normal(int vertexIndex, float x, float y, float z) {
        return rewrap(delegate.normal(vertexIndex, x, y, z));
    }

    @Override
    public QuadEmitter normal(int vertexIndex, Vector3f normal) {
        return rewrap(delegate.normal(vertexIndex, normal));
    }

    @Override
    public QuadEmitter normal(int vertexIndex, Vector3fc normal) {
        return rewrap(delegate.normal(vertexIndex, normal));
    }

    @Override
    public QuadEmitter nominalFace(Direction face) {
        return rewrap(delegate.nominalFace(face));
    }

    @Override
    public QuadEmitter cullFace(Direction face) {
        return rewrap(delegate.cullFace(face));
    }

    @Override
    public QuadEmitter atlas(QuadAtlas atlas) {
        return rewrap(delegate.atlas(atlas));
    }

    @Override
    public QuadEmitter chunkLayer(ChunkSectionLayer layer) {
        return rewrap(delegate.chunkLayer(layer));
    }

    @Override
    public QuadEmitter itemRenderType(RenderType renderType) {
        return rewrap(delegate.itemRenderType(renderType));
    }

    @Override
    public QuadEmitter emissive(boolean emissive) {
        return rewrap(delegate.emissive(emissive));
    }

    @Override
    public QuadEmitter diffuseShade(boolean diffuseShade) {
        return rewrap(delegate.diffuseShade(diffuseShade));
    }

    @Override
    public QuadEmitter ambientOcclusion(TriState ambientOcclusion) {
        return rewrap(delegate.ambientOcclusion(ambientOcclusion));
    }

    @Override
    public QuadEmitter foilType(ItemStackRenderState.FoilType foilType) {
        return rewrap(delegate.foilType(foilType));
    }

    @Override
    public QuadEmitter shadeMode(ShadeMode shadeMode) {
        return rewrap(delegate.shadeMode(shadeMode));
    }

    @Override
    public QuadEmitter animated(boolean animated) {
        return rewrap(delegate.animated(animated));
    }

    @Override
    public QuadEmitter tintIndex(int tintIndex) {
        return rewrap(delegate.tintIndex(tintIndex));
    }

    @Override
    public QuadEmitter tag(int tag) {
        return rewrap(delegate.tag(tag));
    }

    @Override
    public QuadEmitter copyFrom(QuadView quad) {
        return rewrap(delegate.copyFrom(quad));
    }

    @Override
    public QuadEmitter fromBakedQuad(BakedQuad quad) {
        return rewrap(delegate.fromBakedQuad(quad));
    }

    @Override
    public QuadEmitter clear() {
        return rewrap(delegate.clear());
    }

    @Override
    public QuadEmitter square(Direction nominalFace, float left, float bottom, float right, float top,
                              float depth) {
        return rewrap(delegate.square(nominalFace, left, bottom, right, top, depth));
    }

    @Override
    public void pushTransform(QuadTransform transform) {
        delegate.pushTransform(transform);
    }

    @Override
    public void popTransform() {
        delegate.popTransform();
    }

    // ── QuadView: plain delegation ───────────────────────────────────────────────────────────────────

    @Override
    public float x(int vertexIndex) {
        return delegate.x(vertexIndex);
    }

    @Override
    public float y(int vertexIndex) {
        return delegate.y(vertexIndex);
    }

    @Override
    public float z(int vertexIndex) {
        return delegate.z(vertexIndex);
    }

    @Override
    public float posByIndex(int vertexIndex, int coordinateIndex) {
        return delegate.posByIndex(vertexIndex, coordinateIndex);
    }

    @Override
    public Vector3f copyPos(int vertexIndex, Vector3f target) {
        return delegate.copyPos(vertexIndex, target);
    }

    @Override
    public int color(int vertexIndex) {
        return delegate.color(vertexIndex);
    }

    @Override
    public float u(int vertexIndex) {
        return delegate.u(vertexIndex);
    }

    @Override
    public float v(int vertexIndex) {
        return delegate.v(vertexIndex);
    }

    @Override
    public Vector2f copyUv(int vertexIndex, Vector2f target) {
        return delegate.copyUv(vertexIndex, target);
    }

    @Override
    public int lightmap(int vertexIndex) {
        return delegate.lightmap(vertexIndex);
    }

    @Override
    public boolean hasNormal(int vertexIndex) {
        return delegate.hasNormal(vertexIndex);
    }

    @Override
    public float normalX(int vertexIndex) {
        return delegate.normalX(vertexIndex);
    }

    @Override
    public float normalY(int vertexIndex) {
        return delegate.normalY(vertexIndex);
    }

    @Override
    public float normalZ(int vertexIndex) {
        return delegate.normalZ(vertexIndex);
    }

    @Override
    public Vector3f copyNormal(int vertexIndex, Vector3f target) {
        return delegate.copyNormal(vertexIndex, target);
    }

    @Override
    public Vector3fc faceNormal() {
        return delegate.faceNormal();
    }

    @Override
    public Direction lightFace() {
        return delegate.lightFace();
    }

    @Override
    public Direction nominalFace() {
        return delegate.nominalFace();
    }

    @Override
    public Direction cullFace() {
        return delegate.cullFace();
    }

    @Override
    public QuadAtlas atlas() {
        return delegate.atlas();
    }

    @Override
    public ChunkSectionLayer chunkLayer() {
        return delegate.chunkLayer();
    }

    @Override
    public RenderType itemRenderType() {
        return delegate.itemRenderType();
    }

    @Override
    public boolean emissive() {
        return delegate.emissive();
    }

    @Override
    public boolean diffuseShade() {
        return delegate.diffuseShade();
    }

    @Override
    public TriState ambientOcclusion() {
        return delegate.ambientOcclusion();
    }

    @Override
    public ItemStackRenderState.FoilType foilType() {
        return delegate.foilType();
    }

    @Override
    public ShadeMode shadeMode() {
        return delegate.shadeMode();
    }

    @Override
    public boolean animated() {
        return delegate.animated();
    }

    @Override
    public int tintIndex() {
        return delegate.tintIndex();
    }

    @Override
    public int tag() {
        return delegate.tag();
    }

    @Override
    public BakedQuad toBakedQuad(TextureAtlasSprite sprite) {
        return delegate.toBakedQuad(sprite);
    }

    @Override
    public void buffer(int vertexIndex, VertexConsumer consumer) {
        delegate.buffer(vertexIndex, consumer);
    }

    @Override
    public void buffer(int vertexIndex, PoseStack.Pose pose, VertexConsumer consumer) {
        delegate.buffer(vertexIndex, pose, consumer);
    }

    // ── Object: the dynamic proxy routed these to the delegate too ───────────────────────────────────

    @Override
    public boolean equals(Object other) {
        return delegate.equals(other);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
