package com.slabbed.anchor;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

public final class SlabAnchorCapabilityProvider implements ICapabilitySerializable<CompoundTag> {
    private final SlabAnchorStore store;
    private final LazyOptional<SlabAnchorStore> optionalStore;

    public SlabAnchorCapabilityProvider(LevelChunk chunk) {
        this.store = new SlabAnchorStore(chunk);
        this.optionalStore = LazyOptional.of(() -> store);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        return SlabAnchorCapabilities.SLAB_ANCHOR_STORE.orEmpty(capability, optionalStore);
    }

    @Override
    public CompoundTag serializeNBT() {
        return store.save();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        store.load(tag);
    }

    public void invalidate() {
        optionalStore.invalidate();
    }
}
