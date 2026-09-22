package com.slabbed.compat.sable;

import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented on Sable's collider bakery by the compatibility mixin. Loaded only from mixin code,
 * which only exists when Sable does.
 */
public interface SableCellColliderSource {
    /** A collider for one cell's own shape, clamped to the cell as Sable clamps; null when empty. */
    @Nullable RapierVoxelColliderData slabbed$colliderFor(BlockState material, VoxelShape shape);
}
