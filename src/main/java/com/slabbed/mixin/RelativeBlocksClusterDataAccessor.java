package com.slabbed.mixin;

import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reads compositor provenance without introducing a required RPB dependency. */
@Pseudo
@Mixin(targets = "com.lukeolafp.relativeblocks.blocks.BlockClusterBlockEntity", remap = false)
public interface RelativeBlocksClusterDataAccessor {
    @Invoker("getLayout")
    int slabbed$clusterLayout();

    @Invoker("getCell")
    BlockState slabbed$clusterCell(int index);
}
