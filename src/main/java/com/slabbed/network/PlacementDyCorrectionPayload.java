package com.slabbed.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Author-only C3 response carrying exact server presence/raw-bit facts. */
public record PlacementDyCorrectionPayload(
        PlacementDyPredictionBridge.GroupSignature signature,
        List<CellFact> facts
) implements CustomPacketPayload {
    public static final Type<PlacementDyCorrectionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("slabbed", "placement_dy_correction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlacementDyCorrectionPayload> CODEC =
            StreamCodec.of(PlacementDyCorrectionPayload::write, PlacementDyCorrectionPayload::read);

    public record CellFact(long pos, boolean present, long rawBits) {
        public CellFact {
            if (!present && rawBits != 0L) {
                throw new IllegalArgumentException("absent correction fact must encode zero bits");
            }
            if (present && !Double.isFinite(Double.longBitsToDouble(rawBits))) {
                throw new IllegalArgumentException("present correction fact must be finite");
            }
        }
    }

    public PlacementDyCorrectionPayload {
        Objects.requireNonNull(signature, "signature");
        if (facts == null || facts.isEmpty() || facts.size() > 32) {
            throw new IllegalArgumentException("correction must contain one to 32 cells");
        }
        ArrayList<CellFact> sorted = new ArrayList<>(facts);
        sorted.sort(Comparator.comparingLong(CellFact::pos));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).pos() == sorted.get(i).pos()) {
                throw new IllegalArgumentException("duplicate correction cell");
            }
        }
        facts = List.copyOf(sorted);
    }

    private static void write(RegistryFriendlyByteBuf buf, PlacementDyCorrectionPayload payload) {
        PlacementDyPredictionEnvelopePayload.writeSignature(buf, payload.signature);
        buf.writeVarInt(payload.facts.size());
        for (CellFact fact : payload.facts) {
            buf.writeLong(fact.pos());
            buf.writeBoolean(fact.present());
            buf.writeLong(fact.present() ? fact.rawBits() : 0L);
        }
    }

    private static PlacementDyCorrectionPayload read(RegistryFriendlyByteBuf buf) {
        PlacementDyPredictionBridge.GroupSignature signature = PlacementDyPredictionEnvelopePayload.readSignature(buf);
        int count = buf.readVarInt();
        if (count < 1 || count > 32) {
            throw new IllegalArgumentException("invalid correction count " + count);
        }
        ArrayList<CellFact> facts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long pos = buf.readLong();
            boolean present = buf.readBoolean();
            long rawBits = buf.readLong();
            facts.add(new CellFact(pos, present, rawBits));
        }
        return new PlacementDyCorrectionPayload(signature, facts);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
