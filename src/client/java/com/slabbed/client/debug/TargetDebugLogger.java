package com.slabbed.client.debug;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

public final class TargetDebugLogger {
    private static int cooldown = 0;

    private TargetDebugLogger() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(TargetDebugLogger::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;

        // throttle logs so console isn’t spammed
        if (cooldown > 0) { cooldown--; return; }
        cooldown = 10; // once every 10 ticks

        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof BlockHitResult bhr)) return;

        BlockPos pos = bhr.getBlockPos();
        BlockState at = client.world.getBlockState(pos);
        BlockState below = client.world.getBlockState(pos.down());
        BlockState above = client.world.getBlockState(pos.up());

        // Keep it simple and unmistakable
        System.out.println("[SLABBED][TARGET] side=" + bhr.getSide()
                + " pos=" + pos
                + " at=" + at.getBlock()
                + " below=" + below.getBlock()
                + " above=" + above.getBlock());
    }
}
