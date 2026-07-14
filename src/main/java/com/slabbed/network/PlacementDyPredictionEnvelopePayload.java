package com.slabbed.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Client-declared C3 prediction cells, sent before the matching vanilla use-item packet. */
public record PlacementDyPredictionEnvelopePayload(
        PlacementDyPredictionBridge.GroupSignature signature,
        List<Long> positions
) implements CustomPacketPayload {
    public static final Type<PlacementDyPredictionEnvelopePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("slabbed", "placement_dy_prediction_envelope"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlacementDyPredictionEnvelopePayload> CODEC =
            StreamCodec.of(PlacementDyPredictionEnvelopePayload::write, PlacementDyPredictionEnvelopePayload::read);

    public PlacementDyPredictionEnvelopePayload {
        Objects.requireNonNull(signature, "signature");
        if (positions == null || positions.isEmpty() || positions.size() > 2) {
            throw new IllegalArgumentException("prediction envelope must contain one or two cells");
        }
        ArrayList<Long> sorted = new ArrayList<>(positions);
        sorted.sort(Comparator.naturalOrder());
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).longValue() == sorted.get(i).longValue()) {
                throw new IllegalArgumentException("duplicate prediction envelope cell");
            }
        }
        positions = List.copyOf(sorted);
    }

    private static void write(RegistryFriendlyByteBuf buf, PlacementDyPredictionEnvelopePayload payload) {
        writeSignature(buf, payload.signature);
        buf.writeVarInt(payload.positions.size());
        payload.positions.forEach(buf::writeLong);
    }

    private static PlacementDyPredictionEnvelopePayload read(RegistryFriendlyByteBuf buf) {
        PlacementDyPredictionBridge.GroupSignature signature = readSignature(buf);
        int count = buf.readVarInt();
        if (count < 1 || count > 2) {
            throw new IllegalArgumentException("invalid prediction envelope count " + count);
        }
        ArrayList<Long> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(buf.readLong());
        }
        return new PlacementDyPredictionEnvelopePayload(signature, positions);
    }

    static void writeSignature(RegistryFriendlyByteBuf buf, PlacementDyPredictionBridge.GroupSignature signature) {
        buf.writeUtf(signature.dimension(), 256);
        buf.writeVarInt(signature.sequence());
        buf.writeByte(signature.hand().ordinal());
        buf.writeUtf(signature.heldItemId(), 256);
        buf.writeLong(signature.originalHitBlockPos());
        buf.writeByte(signature.originalHitFace().ordinal());
    }

    static PlacementDyPredictionBridge.GroupSignature readSignature(RegistryFriendlyByteBuf buf) {
        String dimension = buf.readUtf(256);
        int sequence = buf.readVarInt();
        int handOrdinal = buf.readUnsignedByte();
        if (handOrdinal >= InteractionHand.values().length) {
            throw new IllegalArgumentException("invalid interaction hand");
        }
        String heldItemId = buf.readUtf(256);
        long hitPos = buf.readLong();
        int faceOrdinal = buf.readUnsignedByte();
        if (faceOrdinal >= net.minecraft.core.Direction.values().length) {
            throw new IllegalArgumentException("invalid hit face");
        }
        return new PlacementDyPredictionBridge.GroupSignature(
                dimension,
                sequence,
                InteractionHand.values()[handOrdinal],
                heldItemId,
                hitPos,
                net.minecraft.core.Direction.values()[faceOrdinal]);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
