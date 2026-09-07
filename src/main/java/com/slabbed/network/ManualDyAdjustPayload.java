package com.slabbed.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client request to step the stored placement height of ONE cell by a single half-step.
 *
 * <p>{@code stateId} is {@code Block.getId(BlockState)} for the block the player was aiming at when
 * the key fired. The server refuses the request when the cell no longer holds that state, so a
 * break-and-replace that races the packet cannot land a nudge on a block the player never aimed at —
 * the same drift class the prediction wire guards with its dimension check.
 *
 * <p>The step MAGNITUDE never travels: only a signed direction does, and the magnitude lives in
 * {@code com.slabbed.util.ManualDyEnvelope}. A client cannot express a multi-step or zero-step
 * request at all, because such a packet fails to decode.
 */
public record ManualDyAdjustPayload(long pos, byte direction, int stateId) implements CustomPacketPayload {

    public static final Type<ManualDyAdjustPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("slabbed", "manual_dy_adjust"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ManualDyAdjustPayload> CODEC =
            StreamCodec.of(ManualDyAdjustPayload::write, ManualDyAdjustPayload::read);

    public ManualDyAdjustPayload {
        if (direction != 1 && direction != -1) {
            throw new IllegalArgumentException("manual dy direction must be +1 or -1");
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, ManualDyAdjustPayload payload) {
        buf.writeLong(payload.pos);
        buf.writeByte(payload.direction);
        buf.writeVarInt(payload.stateId);
    }

    private static ManualDyAdjustPayload read(RegistryFriendlyByteBuf buf) {
        long pos = buf.readLong();
        byte direction = buf.readByte();
        int stateId = buf.readVarInt();
        return new ManualDyAdjustPayload(pos, direction, stateId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
