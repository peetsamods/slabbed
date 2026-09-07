package com.slabbed.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client notice of which way the server's stored-height compatibility flag
 * ({@code SlabAnchorAttachment.FROZEN_DY_ENABLED}) is set, sent once when a player joins.
 *
 * <p>Purely informational: nothing on either side changes behaviour because of it. The client
 * compares it against its own copy of the flag and warns the player when the two disagree, because
 * a disagreement means the heights the client draws and the heights the server collides against can
 * differ (maintainer ruling, 2026-09-06).
 *
 * <p>One boolean, deliberately: this payload must stay cheap enough to send unconditionally on every
 * join, and it must never grow into a channel that carries height DATA — heights travel on the
 * anchor/placement wires, not here.
 */
public record FrozenDyModePayload(boolean frozenDyEnabled) implements CustomPacketPayload {
    public static final Type<FrozenDyModePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("slabbed", "frozen_dy_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FrozenDyModePayload> CODEC =
            StreamCodec.of(FrozenDyModePayload::write, FrozenDyModePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, FrozenDyModePayload payload) {
        buf.writeBoolean(payload.frozenDyEnabled());
    }

    private static FrozenDyModePayload read(RegistryFriendlyByteBuf buf) {
        return new FrozenDyModePayload(buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
