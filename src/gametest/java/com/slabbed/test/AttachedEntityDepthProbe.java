package com.slabbed.test;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Uses the real dispatcher and renderer bodies to compare flat and stored-depth model matrices. */
public final class AttachedEntityDepthProbe {
    private static final int ITEM_FRAME_ID = -7101;
    private static final int GLOW_ITEM_FRAME_ID = -7102;
    private static final int DIRECT_MINECART_ID = -7103;
    private static final int BELOW_RAIL_MINECART_ID = -7104;
    private static final double EPSILON = 1.0e-6d;

    private AttachedEntityDepthProbe() {
    }

    /**
     * The caller owns world setup and synchronization. The item-frame cell is one block outward
     * from {@code frameBackingPos}; the two minecart positions exercise direct and below-rail owner
     * lookup without spawning or ticking a second world.
     */
    public static Capture capture(
            MinecraftClient client,
            BlockPos frameBackingPos,
            Direction frameFacing,
            BlockPos directRailPos,
            BlockPos belowRailPos) {
        if (client == null || client.world == null) {
            throw new IllegalStateException("attached-entity render probe requires a client world");
        }

        ItemFrameEntity frame = new ItemFrameEntity(
                client.world, frameBackingPos.offset(frameFacing), frameFacing);
        frame.setId(ITEM_FRAME_ID);

        GlowItemFrameEntity glowFrame = new GlowItemFrameEntity(
                client.world, frameBackingPos.offset(frameFacing), frameFacing);
        glowFrame.setId(GLOW_ITEM_FRAME_ID);

        MinecartEntity directMinecart = new MinecartEntity(
                client.world,
                directRailPos.getX() + 0.5d,
                directRailPos.getY() + 0.0625d,
                directRailPos.getZ() + 0.5d);
        directMinecart.setId(DIRECT_MINECART_ID);

        MinecartEntity belowRailMinecart = new MinecartEntity(
                client.world,
                belowRailPos.getX() + 0.875d,
                belowRailPos.getY() + 1.0625d,
                belowRailPos.getZ() + 0.5d);
        belowRailMinecart.setId(BELOW_RAIL_MINECART_ID);

        AttachedEntityRenderAudit.reset(
                ITEM_FRAME_ID, GLOW_ITEM_FRAME_ID, DIRECT_MINECART_ID, BELOW_RAIL_MINECART_ID);
        render(client, frame);
        render(client, glowFrame);
        render(client, directMinecart);
        render(client, belowRailMinecart);

        return new Capture(
                AttachedEntityRenderAudit.snapshot(ITEM_FRAME_ID),
                AttachedEntityRenderAudit.snapshot(GLOW_ITEM_FRAME_ID),
                AttachedEntityRenderAudit.snapshot(DIRECT_MINECART_ID),
                AttachedEntityRenderAudit.snapshot(BELOW_RAIL_MINECART_ID));
    }

    public static void assertStoredDepthDelta(Capture flat, Capture lowered, double expectedDy) {
        assertDelta("item frame", flat.itemFrame(), lowered.itemFrame(), expectedDy);
        assertDelta("glow item frame", flat.glowItemFrame(), lowered.glowItemFrame(), expectedDy);
        assertDelta("minecart direct rail", flat.directMinecart(), lowered.directMinecart(), expectedDy);
        assertDelta("minecart rail one cell below", flat.belowRailMinecart(), lowered.belowRailMinecart(), expectedDy);
    }

    private static void render(MinecraftClient client, net.minecraft.entity.Entity entity) {
        MatrixStack matrices = new MatrixStack();
        VertexConsumerProvider.Immediate vertices = client.getBufferBuilders().getEntityVertexConsumers();
        client.getEntityRenderDispatcher().render(
                entity, 0.0d, 0.0d, 0.0d, 0.0f, 0.0f, matrices, vertices, 0x00F000F0);
        vertices.draw();
    }

    private static void assertDelta(
            String label,
            AttachedEntityRenderAudit.Sample flat,
            AttachedEntityRenderAudit.Sample lowered,
            double expectedDy) {
        if (!flat.seen() || !lowered.seen() || flat.frames() != 1 || lowered.frames() != 1) {
            throw new AssertionError(label + " final model matrix was not captured exactly once; flat="
                    + flat + " lowered=" + lowered);
        }
        double xDelta = lowered.matrixX() - flat.matrixX();
        double yDelta = lowered.matrixY() - flat.matrixY();
        double zDelta = lowered.matrixZ() - flat.matrixZ();
        if (Math.abs(xDelta) > EPSILON || Math.abs(zDelta) > EPSILON
                || Math.abs(yDelta - expectedDy) > EPSILON) {
            throw new AssertionError(label + " final model matrix depth mismatch: expectedDy="
                    + expectedDy + " actualDelta=(" + xDelta + "," + yDelta + "," + zDelta
                    + ") flat=" + flat + " lowered=" + lowered);
        }
    }

    public record Capture(
            AttachedEntityRenderAudit.Sample itemFrame,
            AttachedEntityRenderAudit.Sample glowItemFrame,
            AttachedEntityRenderAudit.Sample directMinecart,
            AttachedEntityRenderAudit.Sample belowRailMinecart) {
    }
}
