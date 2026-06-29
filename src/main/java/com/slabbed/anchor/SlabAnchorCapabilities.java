package com.slabbed.anchor;

import com.slabbed.Slabbed;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class SlabAnchorCapabilities {
    public static final ResourceLocation ANCHOR_STORE_ID =
            new ResourceLocation(Slabbed.MOD_ID, "anchor_store");

    public static final Capability<SlabAnchorStore> SLAB_ANCHOR_STORE =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private SlabAnchorCapabilities() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(SlabAnchorCapabilities::registerCapabilities);
        MinecraftForge.EVENT_BUS.addGenericListener(
                LevelChunk.class,
                SlabAnchorCapabilities::attachChunkCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(SlabAnchorStore.class);
    }

    private static void attachChunkCapabilities(AttachCapabilitiesEvent<LevelChunk> event) {
        SlabAnchorCapabilityProvider provider = new SlabAnchorCapabilityProvider(event.getObject());
        event.addCapability(ANCHOR_STORE_ID, provider);
        event.addListener(provider::invalidate);
    }
}
