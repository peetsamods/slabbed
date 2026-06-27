package com.slabbed.client.model;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Alternate baked geometry for a Y-axis chain hanging under a slab CEILING (a TOP or DOUBLE slab
 * directly above it). The vanilla chain model stops at the cell boundary, so a chain raised +0.5 to
 * meet the slab's lowered underside leaves a gap to the chain below; this model extends the chain to
 * y=24 (an extra 0.5 block) so the column connects continuously — the "top chain shortened/extended
 * so chains chain fully" behaviour. Chains are a chainable that connects across the step; lanterns are
 * not emitted here, but their dy follows the same visible chain-column contract in {@code SlabSupport}.
 *
 * <p>Port of the proofed-but-unmerged 1.21.11 {@code fix/chains-*} branches (b95a742d + a56fc90b +
 * 57704624), adapted to the 26.x render path: emitted directly into {@code OffsetBlockStateModel#
 * emitQuads} (26.1.2 has no {@code BlockModelDyTranslateMixin}). The bridge reuses the already-baked
 * vanilla chain model, then emits the upper half-block extension locally so Sodium/Fabric do not need
 * to discover a standalone Slabbed extra model during resource reload.
 */
public final class ChainCeilingGeometry {
    private static final float CENTER = 0.5f;
    private static final float HALF_WIDTH = 1.5f / 16.0f;
    private static final float ROTATED_HALF_WIDTH = (float) (HALF_WIDTH / Math.sqrt(2.0d));
    private static final float BRIDGE_BOTTOM = 1.0f;
    private static final float BRIDGE_TOP = 1.5f;

    private ChainCeilingGeometry() {
    }

    /** True for a Y-axis chain whose block directly above is a ceiling support (TOP/DOUBLE slab). */
    public static boolean usesAlternateGeometry(BlockGetter world, BlockPos pos, BlockState state) {
        return SlabSupport.isVerticalChainDirectlyUnderCeilingSupport(world, pos, state);
    }

    /**
     * Emits the extended chain geometry instead of the wrapped model when this chain hangs under a
     * slab ceiling. Returns true when it handled the emit (caller must then skip the normal path).
     */
    public static boolean emitIfPresent(FabricBlockStateModel fallback, QuadEmitter emitter,
                                        BlockAndTintGetter world, BlockPos pos, BlockState state,
                                        RandomSource random, Predicate<Direction> cullTest) {
        if (!usesAlternateGeometry(world, pos, state)) {
            return false;
        }
        if (fallback == null) {
            return false;
        }

        fallback.emitQuads(emitter, world, pos, state, random, cullTest);
        emitUpperBridge(emitter, fallback.particleMaterial(world, pos, state));
        return true;
    }

    private static void emitUpperBridge(QuadEmitter emitter, Material.Baked material) {
        if (emitter == null || material == null) {
            return;
        }

        float a0x = CENTER - ROTATED_HALF_WIDTH;
        float a0z = CENTER + ROTATED_HALF_WIDTH;
        float a1x = CENTER + ROTATED_HALF_WIDTH;
        float a1z = CENTER - ROTATED_HALF_WIDTH;
        emitBridgeFace(emitter, material, Direction.NORTH, a0x, a0z, a1x, a1z, 3.0f, 8.0f, 0.0f, 0.0f);
        emitBridgeFace(emitter, material, Direction.SOUTH, a1x, a1z, a0x, a0z, 0.0f, 8.0f, 3.0f, 0.0f);

        float b0x = CENTER - ROTATED_HALF_WIDTH;
        float b0z = CENTER - ROTATED_HALF_WIDTH;
        float b1x = CENTER + ROTATED_HALF_WIDTH;
        float b1z = CENTER + ROTATED_HALF_WIDTH;
        emitBridgeFace(emitter, material, Direction.WEST, b0x, b0z, b1x, b1z, 6.0f, 8.0f, 3.0f, 0.0f);
        emitBridgeFace(emitter, material, Direction.EAST, b1x, b1z, b0x, b0z, 3.0f, 8.0f, 6.0f, 0.0f);
    }

    private static void emitBridgeFace(QuadEmitter emitter, Material.Baked material, Direction nominalFace,
                                       float x0, float z0, float x1, float z1,
                                       float u0, float v0, float u1, float v1) {
        emitter.clear();
        emitter.pos(0, x0, BRIDGE_BOTTOM, z0).uv(0, u0, v0);
        emitter.pos(1, x1, BRIDGE_BOTTOM, z1).uv(1, u1, v0);
        emitter.pos(2, x1, BRIDGE_TOP, z1).uv(2, u1, v1);
        emitter.pos(3, x0, BRIDGE_TOP, z0).uv(3, u0, v1);
        emitter.nominalFace(nominalFace);
        emitter.cullFace(null);
        emitter.materialBake(material, 0);
        emitter.emit();
    }
}
