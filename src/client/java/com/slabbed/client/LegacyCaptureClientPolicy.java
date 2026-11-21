package com.slabbed.client;

import com.slabbed.anchor.LegacyCaptureLogin;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.mixin.client.ClientLoginNetworkHandlerConnectionAccessor;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;

/** The login decision belongs to its connection and cannot carry over to another server. */
public final class LegacyCaptureClientPolicy {
    private static final Set<ClientConnection> ENABLED_CONNECTIONS = ConcurrentHashMap.newKeySet();

    private LegacyCaptureClientPolicy() {}

    public static void init() {
        SlabAnchorAttachment.clientLegacyCaptureEnabled = LegacyCaptureClientPolicy::enabled;
        ClientLoginNetworking.registerGlobalReceiver(LegacyCaptureLogin.CHANNEL,
                (client, handler, buffer, listenerAdder) -> {
                    if (buffer.readableBytes() != 1 || buffer.readByte() != LegacyCaptureLogin.PROTOCOL) {
                        return CompletableFuture.completedFuture(null);
                    }
                    ENABLED_CONNECTIONS.add(((ClientLoginNetworkHandlerConnectionAccessor) handler).slabbed$getConnection());
                    var response = PacketByteBufs.create();
                    response.writeByte(LegacyCaptureLogin.PROTOCOL);
                    return CompletableFuture.completedFuture(response);
                });
        ClientLoginConnectionEvents.DISCONNECT.register((handler, client) ->
                ENABLED_CONNECTIONS.remove(
                        ((ClientLoginNetworkHandlerConnectionAccessor) handler).slabbed$getConnection()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ENABLED_CONNECTIONS.remove(handler.getConnection()));
    }

    public static boolean enabled() {
        if (ENABLED_CONNECTIONS.isEmpty()) {
            return false;
        }
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        return handler != null && ENABLED_CONNECTIONS.contains(handler.getConnection());
    }
}
