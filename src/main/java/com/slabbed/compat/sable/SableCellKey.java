package com.slabbed.compat.sable;

import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Identity of a baked cell collider: its physics material and its boxes, clamped to the cell. */
public record SableCellKey(BlockState material, List<AABB> boxes) {
}
