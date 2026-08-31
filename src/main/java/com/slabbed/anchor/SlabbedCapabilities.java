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
 * Registration and attachment for the two Slabbed capabilities that stand in for NeoForge data
 * attachments: per-chunk marker and placement storage, and per-level consent storage.
 *
 * <p>Both attach on logical client and server. The client copies stay empty until
 * {@link SlabbedAnchorNetwork} fills them, because a Forge capability carries no sync of its own.
 */
public final class SlabbedCapabilities {
    public static final ResourceLocation CHUNK_STORE_ID =
            new ResourceLocation(Slabbed.MOD_ID, "chunk_store");
    public static final ResourceLocation CONSENT_STORE_ID =
            new ResourceLocation(Slabbed.MOD_ID, "consent_store");

    public static final Capability<SlabbedChunkStore> CHUNK_STORE =
            CapabilityManager.get(new CapabilityToken<>() { });
    public static final Capability<SlabbedConsentStore> CONSENT_STORE =
            CapabilityManager.get(new CapabilityToken<>() { });

    private SlabbedCapabilities() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(SlabbedCapabilities::registerCapabilities);
        MinecraftForge.EVENT_BUS.addGenericListener(
                LevelChunk.class, SlabbedCapabilities::attachChunk);
        MinecraftForge.EVENT_BUS.addGenericListener(
                Level.class, SlabbedCapabilities::attachLevel);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(SlabbedChunkStore.class);
        event.register(SlabbedConsentStore.class);
    }

    /** The chunk's store, or null when the capability is unavailable (an unloaded or foreign chunk). */
    @Nullable
    public static SlabbedChunkStore chunkStore(@Nullable LevelChunk chunk) {
        return chunk == null ? null : chunk.getCapability(CHUNK_STORE).orElse(null);
    }

    @Nullable
    public static SlabbedConsentStore consentStore(@Nullable Level level) {
        return level == null ? null : level.getCapability(CONSENT_STORE).orElse(null);
    }

    private static void attachChunk(AttachCapabilitiesEvent<LevelChunk> event) {
        ChunkStoreProvider provider = new ChunkStoreProvider(event.getObject());
        event.addCapability(CHUNK_STORE_ID, provider);
        event.addListener(provider::invalidate);
    }

    private static void attachLevel(AttachCapabilitiesEvent<Level> event) {
        ConsentStoreProvider provider = new ConsentStoreProvider();
        event.addCapability(CONSENT_STORE_ID, provider);
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

    private static final class ConsentStoreProvider implements ICapabilitySerializable<CompoundTag> {
        private final SlabbedConsentStore store = new SlabbedConsentStore();
        private final LazyOptional<SlabbedConsentStore> optional = LazyOptional.of(() -> store);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            return CONSENT_STORE.orEmpty(capability, optional);
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
