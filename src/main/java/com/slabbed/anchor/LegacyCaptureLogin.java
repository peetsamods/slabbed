package com.slabbed.anchor;

import com.slabbed.Slabbed;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Negotiates experimental legacy capture before the server sends any world data. */
public final class LegacyCaptureLogin {
    public static final Identifier CHANNEL = new Identifier(Slabbed.MOD_ID, "legacy_height_capture");
    // Both peers must support column prediction and the shared placement marker.
    public static final byte PROTOCOL = 2;

    private LegacyCaptureLogin() {}

    public static void init() {
        if (!SlabAnchorAttachment.CAPTURE_LEGACY_BEFORE_SUPPORT_EDIT) {
            return;
        }
        ServerLoginNetworking.registerGlobalReceiver(CHANNEL,
                (server, handler, understood, buffer, synchronizer, sender) -> {
                    if (!understood || buffer.readableBytes() != 1 || buffer.readByte() != PROTOCOL) {
                        handler.disconnect(Text.literal(
                                "This server enables Slabbed legacy height capture. "
                                        + "A compatible Slabbed client is required."));
                    }
                });
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            var request = PacketByteBufs.create();
            request.writeByte(PROTOCOL);
            sender.sendPacket(CHANNEL, request);
        });
    }
}
