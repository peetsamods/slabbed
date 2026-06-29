package com.slabbed.client;

import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class OffsetTargetingEvents {
    private static boolean initialized;

    private OffsetTargetingEvents() {
    }

    public static void init(IEventBus eventBus) {
        if (initialized) {
            return;
        }
        initialized = true;
        eventBus.addListener(OffsetTargetingEvents::clientTick);
        eventBus.addListener(OffsetTargetingEvents::renderGuiPre);
    }

    private static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            refresh(Minecraft.getInstance(), 1.0f);
        }
    }

    private static void renderGuiPre(RenderGuiEvent.Pre event) {
        refresh(Minecraft.getInstance(), event.getPartialTick());
    }

    private static void refresh(Minecraft client, float partialTick) {
        if (!SlabbedOffsetRaycast.ENABLED
                || client == null
                || client.level == null
                || client.gameMode == null
                || client.getCameraEntity() == null) {
            return;
        }

        Vec3 eye = client.getCameraEntity().getEyePosition(partialTick);
        Vec3 look = client.getCameraEntity().getViewVector(partialTick);
        double range = client.gameMode.getPickRange();
        Vec3 end = eye.add(look.x * range, look.y * range, look.z * range);
        CollisionContext context = client.getCameraEntity() instanceof LocalPlayer player
                ? CollisionContext.of(player)
                : CollisionContext.empty();
        BlockHitResult offsetHit = SlabbedOffsetRaycast.raycast(client.level, eye, end, context);
        if (offsetHit.getType() != HitResult.Type.BLOCK || !isLowered(client, offsetHit.getBlockPos())) {
            return;
        }

        double offsetDistance = offsetHit.getLocation().distanceToSqr(eye);
        HitResult current = client.hitResult;
        if (current != null && current.getType() == HitResult.Type.ENTITY) {
            return;
        }
        if (current instanceof BlockHitResult currentBlock && current.getType() == HitResult.Type.BLOCK) {
            double currentDistance = currentBlock.getLocation().distanceToSqr(eye);
            if (currentDistance < offsetDistance - 1.0e-6d) {
                return;
            }
        }

        client.hitResult = offsetHit;
    }

    private static boolean isLowered(Minecraft client, BlockPos pos) {
        BlockState state = client.level.getBlockState(pos);
        return ClientDy.dyFor(client.level, pos, state) < -1.0e-6d;
    }
}
