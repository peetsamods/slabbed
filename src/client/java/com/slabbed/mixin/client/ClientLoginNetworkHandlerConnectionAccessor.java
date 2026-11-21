package com.slabbed.mixin.client;

import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientLoginNetworkHandler.class)
public interface ClientLoginNetworkHandlerConnectionAccessor {
    @Accessor("connection")
    ClientConnection slabbed$getConnection();
}
