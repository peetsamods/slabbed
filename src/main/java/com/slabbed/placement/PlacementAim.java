package com.slabbed.placement;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable capture of the placement facts visible before any target remapping occurs.
 *
 * <p>This is an internal contract, not a public mod API. The held stack is defensively copied on
 * both construction and access because {@link ItemStack} itself is mutable.</p>
 */
public record PlacementAim(
        BlockPos visibleOwner,
        Direction face,
        Vec3 hitVector,
        BlockPos vanillaTarget,
        ItemStack heldItem) {

    public PlacementAim {
        visibleOwner = Objects.requireNonNull(visibleOwner, "visibleOwner").immutable();
        face = Objects.requireNonNull(face, "face");
        Vec3 hit = Objects.requireNonNull(hitVector, "hitVector");
        hitVector = new Vec3(hit.x, hit.y, hit.z);
        vanillaTarget = Objects.requireNonNull(vanillaTarget, "vanillaTarget").immutable();
        heldItem = Objects.requireNonNull(heldItem, "heldItem").copy();
        if (heldItem.isEmpty()) {
            throw new IllegalArgumentException("heldItem must not be empty");
        }
    }

    @Override
    public ItemStack heldItem() {
        return heldItem.copy();
    }
}
