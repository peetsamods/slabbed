package com.slabbed.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class OffsetOutlineEvents {
    private static boolean initialized;

    private OffsetOutlineEvents() {
    }

    public static void init(IEventBus eventBus) {
        if (initialized) {
            return;
        }
        initialized = true;
        eventBus.addListener(OffsetOutlineEvents::renderBlockHighlight);
    }

    private static void renderBlockHighlight(RenderHighlightEvent.Block event) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return;
        }

        BlockPos pos = event.getTarget().getBlockPos();
        BlockState state = client.level.getBlockState(pos);
        double dy = ClientDy.dyFor(client.level, pos, state);
        if (dy == 0.0d) {
            return;
        }

        VoxelShape shape = state.getShape(client.level, pos, CollisionContext.of(event.getCamera().getEntity()));
        if (shape.isEmpty()) {
            return;
        }

        VoxelShape shifted = shape.move(0.0d, dy, 0.0d);
        Vec3 camera = event.getCamera().getPosition();
        VertexConsumer buffer = event.getMultiBufferSource().getBuffer(RenderType.lines());
        LevelRenderer.renderVoxelShape(
                event.getPoseStack(),
                buffer,
                shifted,
                pos.getX() - camera.x,
                pos.getY() - camera.y,
                pos.getZ() - camera.z,
                0.0f,
                0.0f,
                0.0f,
                0.4f,
                true);
        event.setCanceled(true);
    }
}
