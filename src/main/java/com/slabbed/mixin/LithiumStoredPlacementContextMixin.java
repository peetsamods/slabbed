package com.slabbed.mixin;

import com.slabbed.placement.PlacementCollisionShapes;
import com.slabbed.placement.StoredCollisionSource;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

/** Reads Lithium's query context from the class that declares it. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper", remap = false)
public abstract class LithiumStoredPlacementContextMixin implements StoredCollisionSource {
    @Shadow(remap = false) @Final protected World world;
    @Shadow(remap = false) @Final protected ShapeContext context;
    @Shadow(remap = false) @Final protected Box box;

    @Override
    public List<PlacementCollisionShapes.Contact> slabbed$storedContacts() {
        return PlacementCollisionShapes.above(world, context, box, false);
    }
}
