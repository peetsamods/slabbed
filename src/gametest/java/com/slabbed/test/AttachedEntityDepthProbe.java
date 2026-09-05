package com.slabbed.test;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.nbt.NbtCompound;

/** Checks tracked frame bodies against physical bounds and compares attached renderer matrices. */
public final class AttachedEntityDepthProbe {
    private static volatile Map<Direction, int[]> frameIds = Map.of();
    private static volatile int directMinecartId, slopeMinecartId;
    private static CartCapture directCapture, slopeCapture;
    private static int cartWaitChecks;
    private static final double EPSILON = 1.0e-6d;

    private AttachedEntityDepthProbe() {
    }

    public static void prepareFrames(ServerWorld world, BlockPos origin, double dy) {
        for (int[] ids : frameIds.values()) for (int id : ids) {
            var previous = world.getEntityById(id);
            if (previous != null) previous.discard();
        }
        Map<Direction, int[]> prepared = new HashMap<>();
        for (Direction facing : Direction.values()) {
            int[] ids = new int[2];
            for (int kind = 0; kind < 2; kind++) {
                BlockPos backing = origin.add(facing.ordinal() * 4, 0, -8 - kind * 4);
                world.setBlockState(backing, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                SlabAnchorAttachment.writePlacementDyBatch(world,
                        Map.of(backing, Double.doubleToRawLongBits(dy)));
                ItemFrameEntity frame = kind == 0
                        ? new ItemFrameEntity(world, backing.offset(facing), facing)
                        : new GlowItemFrameEntity(world, backing.offset(facing), facing);
                if (!world.spawnEntity(frame)) throw new IllegalStateException("frame spawn refused");
                ids[kind] = frame.getId();
            }
            prepared.put(facing, ids);
        }
        frameIds = Map.copyOf(prepared);
    }

    public static boolean framesReady(MinecraftClient client, double expectedDy) {
        if (frameIds.size() != Direction.values().length) return false;
        for (int[] ids : frameIds.values()) for (int id : ids) {
            if (!(client.world.getEntityById(id) instanceof ItemFrameEntity frame)) return false;
            double canonicalY = Vec3d.ofCenter(frame.getAttachedBlockPos())
                    .offset(frame.getHorizontalFacing(), -0.46875d).y;
            if (Math.abs(frame.getY() - canonicalY - expectedDy) > EPSILON) return false;
        }
        return true;
    }

    public static void prepareCarts(ServerWorld world, BlockPos direct, BlockPos slope) {
        directMinecartId = prepareCart(world, directMinecartId, direct, false);
        slopeMinecartId = prepareCart(world, slopeMinecartId, slope, true);
        directCapture = null;
        slopeCapture = null;
        cartWaitChecks = 0;
    }

    private static int prepareCart(ServerWorld world, int id, BlockPos rail, boolean slope) {
        MinecartEntity cart;
        if (world.getEntityById(id) instanceof MinecartEntity existing) {
            cart = existing;
            NbtCompound saved = new NbtCompound();
            cart.writeNbt(saved);
            double currentDy = saved.getDouble("slabbed:rail_dy");
            cart.setPosition(rail.getX() + (slope ? 0.875d : 0.5d),
                    rail.getY() + (slope ? 1.0625d : 0.0625d) + currentDy, rail.getZ() + 0.5d);
        } else {
            cart = new MinecartEntity(world, rail.getX() + (slope ? 0.875d : 0.5d),
                    rail.getY() + (slope ? 1.0625d : 0.0625d), rail.getZ() + 0.5d);
            if (!world.spawnEntity(cart)) throw new IllegalStateException("minecart probe spawn refused");
        }
        cart.setVelocity(Vec3d.ZERO);
        return cart.getId();
    }

    public static boolean cartsReady(MinecraftClient client, BlockPos direct, BlockPos slope, double dy) {
        boolean straightReady = cartReady(client, directMinecartId, direct, false, dy);
        boolean slopeReady = cartReady(client, slopeMinecartId, slope, true, dy);
        if ((!straightReady || !slopeReady) && ++cartWaitChecks % 20 == 0) {
            System.out.println("[MINECART_CLIENT_WAIT] expectedDy=" + dy + " straightReady=" + straightReady
                    + " slopeReady=" + slopeReady + " straight=" + client.world.getEntityById(directMinecartId)
                    + " slope=" + client.world.getEntityById(slopeMinecartId));
        }
        return straightReady && slopeReady;
    }

    private static boolean cartReady(MinecraftClient client, int id, BlockPos rail, boolean slope, double dy) {
        if (!(client.world.getEntityById(id) instanceof MinecartEntity cart)) return false;
        NbtCompound saved = new NbtCompound();
        cart.writeNbt(saved);
        double expectedY = rail.getY() + dy + 0.0625d + (slope ? cart.getX() - rail.getX() : 0.0d);
        return saved.getDouble("slabbed:rail_dy") == dy && Math.abs(cart.getY() - expectedY) < 0.001d
                && cart.snapPositionToRail(cart.getX(), cart.getY(), cart.getZ()) != null;
    }

    /**
     * The caller prepares server entities and waits for their tracked client positions. Cart IDs
     * persist across depth stages so vanilla's entity-ID render jitter stays identical.
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

        int[] ids = frameIds.get(frameFacing);
        ItemFrameEntity frame = (ItemFrameEntity) client.world.getEntityById(ids[0]);
        ItemFrameEntity glowFrame = (ItemFrameEntity) client.world.getEntityById(ids[1]);

        AttachedEntityRenderAudit.reset(
                frame.getId(), glowFrame.getId(), directMinecartId, slopeMinecartId);
        renderPhysicalFrame(client, frame);
        renderPhysicalFrame(client, glowFrame);
        if (directCapture == null) {
            directCapture = renderPhysicalCart(client,
                    (MinecartEntity) client.world.getEntityById(directMinecartId), directRailPos);
            slopeCapture = renderPhysicalCart(client,
                    (MinecartEntity) client.world.getEntityById(slopeMinecartId), belowRailPos);
        }

        return new Capture(
                AttachedEntityRenderAudit.snapshot(frame.getId()),
                AttachedEntityRenderAudit.snapshot(glowFrame.getId()),
                directCapture, slopeCapture);
    }

    private static CartCapture renderPhysicalCart(MinecraftClient client, MinecartEntity cart, BlockPos rail) {
        VertexBounds bounds = new VertexBounds();
        var delegate = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumerProvider provider = layer -> new CapturingVertexConsumer(delegate.getBuffer(layer), bounds);
        Vec3d position = cart.getPos().subtract(Vec3d.of(rail));
        Vec3d snapped = cart.snapPositionToRail(cart.getX(), cart.getY(), cart.getZ());
        client.getEntityRenderDispatcher().render(cart, position.x, position.y, position.z,
                cart.getYaw(), 1.0f, new MatrixStack(), provider, 0x00F000F0);
        delegate.draw();
        Box body = bounds.box();
        Vec3d center = body.getCenter().add(Vec3d.of(rail));
        Vec3d start = center.add(0.0d, 0.0d, 2.0d), end = center.add(0.0d, 0.0d, -2.0d);
        var hit = ProjectileUtil.raycast(client.player, start, end,
                new Box(start, end).expand(0.25d), entity -> entity == cart, start.squaredDistanceTo(end));
        if (hit == null || hit.getEntity() != cart) throw new IllegalStateException("visible minecart body ray missed");
        NbtCompound saved = new NbtCompound();
        cart.writeNbt(saved);
        Vec3d snapLocal = snapped.subtract(Vec3d.of(rail));
        CartCapture capture = new CartCapture(cart.getId(), saved.getDouble("slabbed:rail_dy"),
                body.offset(-snapLocal.x, -snapLocal.y, -snapLocal.z),
                cart.getBoundingBox().offset(-cart.getX(), -cart.getY(), -cart.getZ()));
        System.out.println("[MINECART_PHYSICAL_CLIENT] dy=" + capture.dy() + " vertices=" + bounds.count
                + " position=" + position + " body=" + body + " ray=PASS");
        return capture;
    }

    private static void assertCart(CartCapture flat, CartCapture lowered, double dy) {
        if (flat.entityId() != lowered.entityId() || flat.dy() != 0.0d || lowered.dy() != dy
                || !sameBox(flat.bodyFromRail(), lowered.bodyFromRail())
                || !sameBox(flat.boxFromEntity(), lowered.boxFromEntity())) {
            throw new AssertionError("minecart rendered/physical displacement mismatch: flat=" + flat + " deep=" + lowered);
        }
    }

    private static void renderPhysicalFrame(MinecraftClient client, ItemFrameEntity frame) {
        BlockPos origin = frame.getAttachedBlockPos();
        VertexBounds bounds = new VertexBounds();
        var delegate = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumerProvider provider = layer -> new CapturingVertexConsumer(delegate.getBuffer(layer), bounds);
        client.getEntityRenderDispatcher().render(frame,
                frame.getX() - origin.getX(), frame.getY() - origin.getY(), frame.getZ() - origin.getZ(),
                0.0f, 0.0f, new MatrixStack(), provider, 0x00F000F0);
        delegate.draw();
        Box physical = frame.getBoundingBox().offset(-origin.getX(), -origin.getY(), -origin.getZ());
        Box rendered = bounds.box();
        if (!sameBox(physical, rendered)) {
            throw new IllegalStateException("frame rendered body disagrees with physical box: rendered="
                    + rendered + " physical=" + physical);
        }
        Vec3d normal = Vec3d.of(frame.getHorizontalFacing().getVector());
        Vec3d center = frame.getBoundingBox().getCenter();
        Vec3d start = center.add(normal.multiply(2.0d)), end = center.subtract(normal.multiply(2.0d));
        var hit = ProjectileUtil.raycast(client.player, start, end,
                new Box(start, end).expand(0.25d), entity -> entity == frame, start.squaredDistanceTo(end));
        if (hit == null || hit.getEntity() != frame) {
            throw new IllegalStateException("client visible frame ray missed tracked frame");
        }
        System.out.println("[FRAME_PHYSICAL_CLIENT] type=" + frame.getType() + " facing="
                + frame.getHorizontalFacing() + " vertices=" + bounds.count
                + " rendered=" + rendered + " physical=" + physical + " ray=PASS");
    }

    private static boolean sameBox(Box a, Box b) {
        return Math.abs(a.minX - b.minX) < 1.0e-5d && Math.abs(a.minY - b.minY) < 1.0e-5d
                && Math.abs(a.minZ - b.minZ) < 1.0e-5d && Math.abs(a.maxX - b.maxX) < 1.0e-5d
                && Math.abs(a.maxY - b.maxY) < 1.0e-5d && Math.abs(a.maxZ - b.maxZ) < 1.0e-5d;
    }

    private static final class VertexBounds {
        private double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        private double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        private int count;
        private void add(float x, float y, float z) {
            count++;
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        private Box box() {
            if (count == 0) throw new IllegalStateException("frame renderer emitted no vertices");
            return new Box(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private record CapturingVertexConsumer(VertexConsumer delegate, VertexBounds bounds) implements VertexConsumer {
        public VertexConsumer vertex(float x, float y, float z) { bounds.add(x, y, z); delegate.vertex(x, y, z); return this; }
        public VertexConsumer color(int r, int g, int b, int a) { delegate.color(r, g, b, a); return this; }
        public VertexConsumer texture(float u, float v) { delegate.texture(u, v); return this; }
        public VertexConsumer overlay(int u, int v) { delegate.overlay(u, v); return this; }
        public VertexConsumer light(int u, int v) { delegate.light(u, v); return this; }
        public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
    }

    public static void assertStoredDepthDelta(Capture flat, Capture lowered, double expectedDy) {
        assertDelta("item frame", flat.itemFrame(), lowered.itemFrame(), expectedDy);
        assertDelta("glow item frame", flat.glowItemFrame(), lowered.glowItemFrame(), expectedDy);
        assertCart(flat.directMinecart(), lowered.directMinecart(), expectedDy);
        assertCart(flat.belowRailMinecart(), lowered.belowRailMinecart(), expectedDy);
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
            CartCapture directMinecart,
            CartCapture belowRailMinecart) {
    }

    public record CartCapture(int entityId, double dy, Box bodyFromRail, Box boxFromEntity) {
    }
}
