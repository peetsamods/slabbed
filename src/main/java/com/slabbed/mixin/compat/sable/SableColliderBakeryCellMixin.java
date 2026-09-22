package com.slabbed.mixin.compat.sable;

import com.slabbed.compat.sable.SableCellColliderSource;
import com.slabbed.compat.sable.SableCellKey;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Bakes a collider for one cell's own shape, with the same physics material and the same
 * unit-cube clamp Sable applies to every block collider. Colliders are shared by identical
 * (material, boxes) pairs for the bakery's lifetime, as Sable shares its per-state colliders.
 *
 * <p>Admitted only by {@link SableMixinPlugin}'s byte probe.
 */
@Mixin(value = RapierVoxelColliderBakery.class, remap = false)
public abstract class SableColliderBakeryCellMixin implements SableCellColliderSource {
    @Unique
    private static final double SLABBED$EPS = 1.0e-7d;

    @Unique
    private final Map<SableCellKey, RapierVoxelColliderData> slabbed$cellColliders = new HashMap<>();

    @Override
    public RapierVoxelColliderData slabbed$colliderFor(BlockState material, VoxelShape shape) {
        if (material == null || shape == null || shape.isEmpty()) {
            return null;
        }
        List<AABB> boxes = new ArrayList<>();
        for (AABB box : shape.toAabbs()) {
            AABB clamped = new AABB(
                    Math.max(box.minX, 0.0d), Math.max(box.minY, 0.0d), Math.max(box.minZ, 0.0d),
                    Math.min(box.maxX, 1.0d), Math.min(box.maxY, 1.0d), Math.min(box.maxZ, 1.0d));
            if (clamped.maxX - clamped.minX > SLABBED$EPS
                    && clamped.maxY - clamped.minY > SLABBED$EPS
                    && clamped.maxZ - clamped.minZ > SLABBED$EPS) {
                boxes.add(clamped);
            }
        }
        if (boxes.isEmpty()) {
            return null;
        }
        return this.slabbed$cellColliders.computeIfAbsent(new SableCellKey(material, List.copyOf(boxes)), key -> {
            RapierVoxelColliderData entry = Rapier3D.createVoxelColliderEntry(
                    PhysicsBlockPropertyHelper.getFriction(key.material()),
                    PhysicsBlockPropertyHelper.getVolume(key.material()),
                    PhysicsBlockPropertyHelper.getRestitution(key.material()),
                    false,
                    BlockWithSubLevelCollisionCallback.sable$getCallback(key.material()));
            for (AABB box : key.boxes()) {
                entry.addBox(new Vector3d(box.minX, box.minY, box.minZ), new Vector3d(box.maxX, box.maxY, box.maxZ));
            }
            return entry;
        });
    }
}
