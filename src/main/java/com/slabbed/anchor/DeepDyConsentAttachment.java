package com.slabbed.anchor;

import com.mojang.serialization.Codec;
import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * THE CONSENT STATE — whether the world currently open has agreed to the deeper dy alphabet.
 *
 * <p>See {@code LAW.md}; this class does not redefine the placement law. It decides only which
 * <em>cap</em> the resolver refuses to go past, and it exists because that cap stopped being a
 * property of the build and became a property of the world.
 *
 * <h2>Why per-world, and why consent rather than migration</h2>
 *
 * <p>Deepening the cap changes where cells that hold no stored placement height resolve to —
 * worldgen, {@code /setblock}, and every cell saved before the placement store existed. Those cells
 * would move under a player who did nothing but update the mod, which is the one thing LAW 1 is
 * about. The maintainer ruling of 2026-08-09: <b>nothing moves unless someone says move it.</b> So
 * the alphabet is not migrated in; it is consented to, one world at a time, and the absence of
 * consent is the shipped behaviour byte for byte.
 *
 * <h2>The three states, and what "absent" means</h2>
 *
 * <ul>
 *   <li><b>No stamp at all</b> — a world that existed before this landed. LEGACY. The cap is
 *       {@link SlabSupport#SHIPPED_MIN_RESOLVED_DY} and nothing in that world can move. Legacy is
 *       recognised by the ABSENCE of the stamp and by nothing else: no content is inspected, no
 *       age is inferred, no heuristic is applied. Absence is a fact the save either has or has not
 *       got, and it is the only fact consulted.</li>
 *   <li><b>Stamped {@code false}</b> — a world created after this landed that is not (yet) on the
 *       deeper alphabet. Behaves exactly like legacy today, and is distinguishable from it forever,
 *       which is the point: "was this world born under the store?" is a fact that can only be
 *       captured at birth and can never be recovered afterwards.</li>
 *   <li><b>Stamped {@code true}</b> — consented. The cap is
 *       {@link com.slabbed.util.SlabbedOffsetRaycast#DEEPEST_TARGETABLE_DY}.</li>
 * </ul>
 *
 * <h2>New worlds currently remain on the shipped alphabet</h2>
 *
 * <p>A brand-new world could adopt the deeper alphabet without moving older placements, but the
 * deep pass-through depth-budget defect is still pinned by
 * {@code DyWindowSuite#passThroughTowerNeverCliffsPastWhatItsSeatCanLower} and
 * {@code DyWindowSuite#passThroughTowerPastTheBudgetNeverRises}. New worlds remain OFF until that
 * behavior is corrected; then {@link #NEW_WORLD_DEFAULT_DEEP_DY} is the single default to change.
 *
 * <h2>Storage: a persistent, synchronized WORLD attachment</h2>
 *
 * <p>Server-side and durable, keyed to the world, and readable before the first block resolves. The
 * alternatives were considered and rejected: a client config is the {@code BetaNoticeDismissedWorlds}
 * shape, which is right for a popup and wrong for geometry (the client would be deciding what the
 * server's blocks are); a chunk attachment would answer the question once per chunk for a fact that
 * has one value per world; a mod-wide config file is not keyed to the world at all.
 *
 * <p>A world attachment gets three things for free that a hand-rolled store would have to earn:
 * Fabric persists it per {@code ServerWorld} in the save's own data, it is delivered to a joining
 * client by Fabric's own sync (see {@link #installClientListener}), and the write path is the same
 * one {@link SlabAnchorAttachment} and {@link SlabPlacementDyAttachment} already use here.
 *
 * <p><b>Per-dimension storage, one value per SAVE.</b> World attachments are per-dimension; consent
 * is not. The OVERWORLD is the authority. {@link #onWorldLoad} mirrors the overworld's value into
 * every other dimension as it loads, so each dimension self-describes (and therefore syncs to a
 * client standing in it) while never being an independent opinion.
 *
 * <h2>Perf: the value is cached, never looked up</h2>
 *
 * <p>{@link SlabSupport#minResolvedDy()} is on the resolver's hot path. This class NEVER reads the
 * attachment from there. It reads it on world load and pushes the answer into
 * {@link SlabSupport#armDeepAlphabet(boolean)}, a single {@code volatile double}. Every
 * authoritative read is counted in {@link #authoritativeReads()} so a regression test can assert
 * the hot path performs exactly zero of them.
 */
public final class DeepDyConsentAttachment {

    private DeepDyConsentAttachment() {
    }

    /**
     * THE ONE CONSTANT. What a world created after this landed is stamped with.
     *
     * <p>{@code false} while the deep pass-through depth-budget defect described in the class
     * javadoc remains open. This is a named constant so the default and its regression test change
     * together.
     */
    public static final boolean NEW_WORLD_DEFAULT_DEEP_DY = false;

    private static final Identifier CONSENT_ID = Identifier.of(Slabbed.MOD_ID, "deep_dy_consent");

    /**
     * Counts reads of the AUTHORITATIVE store — the attachment itself, as opposed to the cached
     * value the resolver uses. The regression gate asserts this does not move while a pick battery
     * runs; see {@code DeepDyConsentTest}.
     */
    private static final AtomicLong AUTHORITATIVE_READS = new AtomicLong();

    private static final PacketCodec<RegistryByteBuf, Boolean> PACKET_CODEC =
            PacketCodecs.BOOLEAN.cast();

    /**
     * The stamp. Present means "this world was created after the consent stage landed"; its value
     * is the consent itself.
     *
     * <p>Synced with {@link AttachmentSyncPredicate#all()} for the same reason the anchor set is:
     * the client draws blocks at the height this decides, so a client that does not know the value
     * would draw them somewhere else. See {@code 4e55cc4c} for what that costs.
     */
    static final AttachmentType<Boolean> CONSENT_TYPE =
            AttachmentRegistry.<Boolean>create(CONSENT_ID, builder -> builder
                    .persistent(Codec.BOOL)
                    .syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())
            );

    /** Touches the class so the attachment registers, and wires the server lifecycle hooks. */
    public static void register() {
        if (CONSENT_TYPE == null) {
            throw new IllegalStateException("DeepDyConsentAttachment failed to register");
        }
        ServerWorldEvents.LOAD.register(DeepDyConsentAttachment::onWorldLoad);
        // A dedicated server JVM can host one save and then, in tests, another. Whatever a stopped
        // server said must not survive it: the next thing this JVM resolves may be a legacy world.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> resetToLegacyDefault());
    }

    // ── the authoritative store ───────────────────────────────────────────────────────────────

    /**
     * The world's own answer. {@code null} means NO STAMP — legacy — and is never confused with a
     * stamped {@code false}; the two are the same behaviour today and different facts forever.
     */
    public static Boolean stamp(World world) {
        if (world == null) {
            return null;
        }
        AUTHORITATIVE_READS.incrementAndGet();
        return world.getAttached(CONSENT_TYPE);
    }

    /** True when {@code world} carries a stamp that says yes. Absent stamp answers false. */
    public static boolean consented(World world) {
        return Boolean.TRUE.equals(stamp(world));
    }

    /**
     * Writes the save's stamp. Server-side only and intentionally one-way once consent is granted.
     *
     * <p>A {@code false} write is initialization: it stamps only the supplied dimension and is
     * rejected if the save has already consented. A {@code true} write is the consent transition:
     * it stamps every loaded dimension and refreshes the server's cached cap before returning.
     * Keeping those effects in the only writer prevents a caller from persisting consent while the
     * live resolver continues using the shipped cap until restart.
     */
    public static void stampWorld(ServerWorld world, boolean deepDy) {
        if (world == null) {
            return;
        }

        MinecraftServer server = world.getServer();
        ServerWorld authority = server == null ? null : server.getOverworld();
        if (!deepDy) {
            if (Boolean.TRUE.equals(stamp(authority == null ? world : authority))) {
                throw new IllegalStateException(
                        "Deep-dy consent is one-way; revoking it would move unstored cells");
            }
            world.setAttached(CONSENT_TYPE, false);
            return;
        }

        if (server == null) {
            throw new IllegalStateException("Cannot grant deep-dy consent without a server");
        }
        for (ServerWorld loadedWorld : server.getWorlds()) {
            loadedWorld.setAttached(CONSENT_TYPE, true);
        }
        SlabSupport.armDeepAlphabet(true);
    }

    /** How many times the authoritative store has been read. The perf gate's instrument. */
    public static long authoritativeReads() {
        return AUTHORITATIVE_READS.get();
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────────────────────

    /**
     * Fires as each {@code ServerWorld} is put into the server's world map, which is BEFORE the
     * server sets up spawn and long before any chunk is sent — so the cached cap is correct on the
     * first resolve after world load, not merely soon after it.
     *
     * <p><b>Detecting "created now".</b> {@code ServerWorldProperties.isInitialized()} is
     * {@code level.dat}'s own "this save has been started before" bit — the same fact vanilla
     * itself consults to decide whether to generate a spawn — and at this point in
     * {@code createWorlds} it is still {@code false} for a brand-new save and already {@code true}
     * for every existing one. That is a property of the SAVE, not an inference from its contents.
     */
    private static void onWorldLoad(MinecraftServer server, ServerWorld world) {
        if (server == null || world == null) {
            return;
        }
        boolean overworld = world.getRegistryKey().equals(World.OVERWORLD);

        if (overworld && stamp(world) == null
                && !server.getSaveProperties().getMainWorldProperties().isInitialized()) {
            stampWorld(world, NEW_WORLD_DEFAULT_DEEP_DY);
            Slabbed.LOGGER.info("[DEEP-DY] new world stamped deepDy={}", NEW_WORLD_DEFAULT_DEEP_DY);
        }

        ServerWorld authority = server.getOverworld();
        boolean armed = consented(authority == null ? world : authority);

        // Mirror the save-wide answer into this dimension so a client standing in it receives the
        // value from Fabric's own world-attachment sync rather than from a second mechanism.
        if (!overworld && authority != null && stamp(authority) != null
                && !Boolean.valueOf(armed).equals(stamp(world))) {
            stampWorld(world, armed);
        }

        SlabSupport.armDeepAlphabet(armed);
        Slabbed.LOGGER.info("[DEEP-DY] {} consent={} cap={}",
                world.getRegistryKey().getValue(), armed, SlabSupport.minResolvedDy());
    }

    /**
     * Drops back to the shipped cap. Called when a server stops and when a client leaves a world.
     *
     * <p><b>This is the assertion that a stale value cannot follow you.</b> An ABSENT attachment
     * sends no sync change — Fabric only transmits attachments that exist — so a client that had
     * been on a consented world and then joined a legacy one would never be told to go back. It is
     * told here instead, before it can render anything of the new world.
     */
    public static void resetToLegacyDefault() {
        SlabSupport.armDeepAlphabet(false);
    }

    /**
     * CLIENT SIDE. Adopts the value the server sent, and keeps adopting it if it changes.
     *
     * <p>The client never decides this. It has no branch that could decide it: the only path that
     * writes the client's cached cap is this listener, and the only thing it can write is what
     * arrived in the world attachment. That is the structural reason a client/server split of the
     * kind fixed at {@code 4e55cc4c} cannot reappear here — there is no second opinion to diverge
     * FROM. Ordering is covered too: Fabric sends a world's attachments from its player-join hook,
     * which runs before the first chunk packet, and anything that arrives later still lands here.
     */
    public static void installClientListener(World clientWorld) {
        if (clientWorld == null) {
            return;
        }
        SlabSupport.armDeepAlphabet(consented(clientWorld));
        clientWorld.onAttachedSet(CONSENT_TYPE).register((oldValue, newValue) ->
                SlabSupport.armDeepAlphabet(Boolean.TRUE.equals(newValue)));
    }
}
