package com.slabbed.anchor;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent save consent for the deeper factless-placement alphabet.
 *
 * <p>The overworld is the sole authority. No attachment means a legacy save, an explicit false
 * stamp means the save is eligible but has not consented, and an explicit true stamp is permanent.
 * Existing placement-height facts do not consult this state.
 */
public final class DeepDyConsentAttachment {
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String VERSION_KEY = "version";
    private static final String ENABLED_KEY = "enabled";

    private static final AtomicLong AUTHORITATIVE_READS = new AtomicLong();
    private static volatile Consumer<Boolean> CLIENT_STATE_OBSERVER = enabled -> { };

    private DeepDyConsentAttachment() {
    }

    /**
     * Registers authoritative server lifecycle hooks.
     *
     * <p>Storage itself is the level capability registered by {@link SlabbedCapabilities}; the
     * stamp reaches clients through {@link SlabbedAnchorNetwork} rather than an attachment sync.
     */
    public static void register(IEventBus modEventBus) {
        MinecraftForge.EVENT_BUS.addListener(DeepDyConsentAttachment::onLevelLoad);
        MinecraftForge.EVENT_BUS.addListener(DeepDyConsentAttachment::onCreateSpawnPosition);
        MinecraftForge.EVENT_BUS.addListener(DeepDyConsentAttachment::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(DeepDyConsentAttachment::onServerStopped);
    }

    /** Reads the stored stamp without creating one. */
    @Nullable
    public static Stamp stamp(Level level) {
        if (level == null) {
            return null;
        }
        AUTHORITATIVE_READS.incrementAndGet();
        if (level.isClientSide()) {
            return SlabbedClientMirror.consentStamp();
        }
        SlabbedConsentStore store = SlabbedCapabilities.consentStore(level);
        return store == null ? null : store.stampOrNull();
    }

    /** Reads the four-state save classification without creating storage. */
    public static State state(Level level) {
        Stamp stamp = stamp(level);
        return stamp == null ? State.ABSENT_LEGACY : stamp.state();
    }

    /** Returns the number of authoritative attachment reads performed by this class. */
    public static long authoritativeReads() {
        return AUTHORITATIVE_READS.get();
    }

    /** Installs the logical-client refresh owner without loading client classes on a server. */
    public static void installClientStateObserver(Consumer<Boolean> observer) {
        CLIENT_STATE_OBSERVER = Objects.requireNonNull(observer, "observer");
    }

    /** Enables deep factless resolution save-wide. The transition is immediate and one-way. */
    public static GrantResult grant(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return GrantResult.NO_SERVER;
        }
        State authority = state(server.overworld());
        if (authority == State.LOCKED_UNKNOWN) {
            return GrantResult.LOCKED_UNKNOWN;
        }
        if (authority == State.ENABLED) {
            SlabSupport.armDeepAlphabet(true);
            mirrorAuthority(server, Stamp.enabled());
            return GrantResult.ALREADY_ENABLED;
        }

        mirrorAuthority(server, Stamp.enabled());
        SlabSupport.armDeepAlphabet(true);
        return GrantResult.ENABLED_NOW;
    }

    /** Stamps a newly created save off without changing an existing or unknown stamp. */
    public static boolean initializeNewSave(ServerLevel level) {
        if (level == null || level.dimension() != Level.OVERWORLD || stamp(level) != null) {
            return false;
        }
        mirrorAuthority(level.getServer(), Stamp.disabled());
        SlabSupport.armDeepAlphabet(false);
        return true;
    }

    /** Re-applies the overworld authority after all dimensions are available. */
    public static void reconcile(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            SlabSupport.armDeepAlphabet(false);
            return;
        }
        Stamp authority = stamp(server.overworld());
        mirrorAuthority(server, authority);
        SlabSupport.armDeepAlphabet(authority != null && authority.state() == State.ENABLED);
    }

    private static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (level.dimension() == Level.OVERWORLD) {
            Stamp authority = stamp(level);
            SlabSupport.armDeepAlphabet(authority != null && authority.state() == State.ENABLED);
            return;
        }
        if (server.overworld() != null) {
            mirror(level, stamp(server.overworld()));
        }
    }

    private static void onCreateSpawnPosition(LevelEvent.CreateSpawnPosition event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            initializeNewSave(level);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        reconcile(event.getServer());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        SlabSupport.armDeepAlphabet(false);
    }

    private static void mirrorAuthority(MinecraftServer server, @Nullable Stamp authority) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            mirror(level, authority);
        }
    }

    private static void mirror(ServerLevel level, @Nullable Stamp authority) {
        SlabbedConsentStore store = SlabbedCapabilities.consentStore(level);
        if (store == null) {
            return;
        }
        Stamp existing = store.stampOrNull();
        if (authority == null) {
            if (existing != null) {
                store.putStamp(null);
                SlabbedAnchorNetwork.syncConsent(level, null);
            }
        } else if (!authority.equals(existing)) {
            Stamp copy = authority.copy();
            store.putStamp(copy);
            SlabbedAnchorNetwork.syncConsent(level, copy);
        }
    }

    public enum State {
        ABSENT_LEGACY(-1),
        DISABLED(0),
        ENABLED(1),
        LOCKED_UNKNOWN(2);

        private final int wireCode;

        State(int wireCode) {
            this.wireCode = wireCode;
        }

        int wireCode() {
            return wireCode;
        }
    }

    public enum GrantResult {
        ENABLED_NOW,
        ALREADY_ENABLED,
        LOCKED_UNKNOWN,
        NO_SERVER
    }

    /**
     * Applies a stamp that arrived from the server on the logical client.
     *
     * <p>This is the half of the old sync handler that was not pure serialization: arming the
     * deep alphabet and notifying the client observer. {@link SlabbedAnchorNetwork} calls it
     * through {@link SlabbedClientMirror} once the payload is decoded.
     */
    public static void acceptClientStamp(@Nullable Stamp stamp) {
        boolean enabled = stamp != null && stamp.state() == State.ENABLED;
        SlabSupport.armDeepAlphabet(enabled);
        CLIENT_STATE_OBSERVER.accept(enabled);
    }

    /** Immutable persisted stamp. Unknown schema data is preserved byte-for-byte on re-save. */
    public static final class Stamp {
        private final Tag serialized;
        private final State state;
        private final int wireVersion;

        private Stamp(Tag serialized, State state, int wireVersion) {
            this.serialized = serialized.copy();
            this.state = state;
            this.wireVersion = wireVersion;
        }

        public static Stamp disabled() {
            return recognized(false);
        }

        public static Stamp enabled() {
            return recognized(true);
        }

        public static Stamp fromTag(Tag tag) {
            if (tag instanceof CompoundTag compound
                    && compound.contains(VERSION_KEY, Tag.TAG_INT)
                    && compound.contains(ENABLED_KEY, Tag.TAG_BYTE)) {
                int version = compound.getInt(VERSION_KEY);
                if (version == CURRENT_SCHEMA_VERSION) {
                    return new Stamp(
                            compound,
                            compound.getBoolean(ENABLED_KEY) ? State.ENABLED : State.DISABLED,
                            version);
                }
                return new Stamp(compound, State.LOCKED_UNKNOWN, version);
            }
            return new Stamp(tag, State.LOCKED_UNKNOWN, -1);
        }

        static Stamp fromWire(int version, int stateCode) {
            if (version == CURRENT_SCHEMA_VERSION && stateCode == State.DISABLED.wireCode()) {
                return disabled();
            }
            if (version == CURRENT_SCHEMA_VERSION && stateCode == State.ENABLED.wireCode()) {
                return enabled();
            }
            CompoundTag unknown = new CompoundTag();
            unknown.putInt(VERSION_KEY, version);
            unknown.putInt("wire_state", stateCode);
            return new Stamp(unknown, State.LOCKED_UNKNOWN, version);
        }

        private static Stamp recognized(boolean enabled) {
            CompoundTag tag = new CompoundTag();
            tag.putInt(VERSION_KEY, CURRENT_SCHEMA_VERSION);
            tag.putBoolean(ENABLED_KEY, enabled);
            return new Stamp(tag, enabled ? State.ENABLED : State.DISABLED, CURRENT_SCHEMA_VERSION);
        }

        public State state() {
            return state;
        }

        public Tag serializedTag() {
            return serialized.copy();
        }

        int wireVersion() {
            return wireVersion;
        }

        Stamp copy() {
            return new Stamp(serialized, state, wireVersion);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Stamp stamp
                    && state == stamp.state
                    && wireVersion == stamp.wireVersion
                    && serialized.equals(stamp.serialized);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serialized, state, wireVersion);
        }
    }
}
