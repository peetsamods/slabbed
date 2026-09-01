package com.slabbed.anchor;

import com.slabbed.Slabbed;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Registration and attachment for the chunk capability that stands in for the NeoForge per-chunk
 * data attachments, plus the accessor seam for per-level consent. Only the chunk storage is a
 * capability; consent rides {@code SavedData}, for the reason recorded on {@link #consentStore}.
 *
 * <p>The chunk capability attaches on both logical sides, and the client copy stays empty for the
 * whole life of the chunk. A Forge capability carries no synchronization of its own, so
 * {@link SlabbedAnchorNetwork} fills {@link SlabbedClientMirror} instead and never writes here.
 * Every client-side read therefore belongs in the mirror: one that reaches this capability
 * resolves against storage nothing populates.
 */
public final class SlabbedCapabilities {
    public static final ResourceLocation CHUNK_STORE_ID =
            new ResourceLocation(Slabbed.MOD_ID, "chunk_store");

    public static final Capability<SlabbedChunkStore> CHUNK_STORE =
            CapabilityManager.get(new CapabilityToken<>() { });

    private SlabbedCapabilities() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(SlabbedCapabilities::registerCapabilities);
        MinecraftForge.EVENT_BUS.addGenericListener(
                LevelChunk.class, SlabbedCapabilities::attachChunk);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(SlabbedChunkStore.class);
    }

    /** The chunk's store, or null when the capability is unavailable (an unloaded or foreign chunk). */
    @Nullable
    public static SlabbedChunkStore chunkStore(@Nullable LevelChunk chunk) {
        return chunk == null ? null : chunk.getCapability(CHUNK_STORE).orElse(null);
    }

    /**
     * The level's consent store, or null on the logical client.
     *
     * <p>NOT a capability, deliberately. Forge 1.20.1 never serializes a {@code Level}
     * capability, so consent stored that way would reset on every restart; the store rides
     * {@code SavedData} instead, and this accessor keeps the seam in one place so callers
     * cannot tell the difference. Client reads go through {@link SlabbedClientMirror}.
     */
    @Nullable
    public static SlabbedConsentStore consentStore(@Nullable Level level) {
        return level instanceof net.minecraft.server.level.ServerLevel serverLevel
                ? SlabbedConsentStore.of(serverLevel)
                : null;
    }

    private static void attachChunk(AttachCapabilitiesEvent<LevelChunk> event) {
        ChunkStoreProvider provider = new ChunkStoreProvider(event.getObject());
        event.addCapability(CHUNK_STORE_ID, provider);
        event.addListener(provider::invalidate);
    }


    private static final class ChunkStoreProvider implements ICapabilitySerializable<CompoundTag> {
        private final SlabbedChunkStore store;
        private final LazyOptional<SlabbedChunkStore> optional;

        private ChunkStoreProvider(LevelChunk chunk) {
            this.store = new SlabbedChunkStore(chunk);
            this.optional = LazyOptional.of(() -> store);
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            return CHUNK_STORE.orEmpty(capability, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return store.save();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            store.load(tag);
        }

        private void invalidate() {
            optional.invalidate();
        }
    }

}
