package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Native-client proof for player-used boats, chest boats, and armor stands on stored surfaces. */
public final class SurfaceEntityClientProbe {
    private static final double EPSILON = 1.0e-4d;
    private static final int CASE_TIMEOUT = 260;
    private static final List<Case> CASES = cases();
    private static final Map<String, Capture> FLAT_BASELINES = new LinkedHashMap<>();
    private static final List<String> FAILURES = new ArrayList<>();

    private static Stage stage = Stage.PREPARE;
    private static int caseIndex;
    private static int stageTick;
    private static boolean fixturePending;
    private static volatile boolean fixtureReady;
    private static boolean serverSamplePending;
    private static volatile ServerSample serverSample = ServerSample.missing();
    private static int entityId = -1;
    private static Box initialClientBox;
    private static double initialContactError = Double.NaN;
    private static Path observations;
    private static boolean evidenceFailed;
    private static volatile long renderedFrame;
    private static volatile RenderedCamera renderedCamera = RenderedCamera.missing();
    private static long captureStartFrame;
    private static Vec3d captureEye;
    private static float captureYaw;
    private static float capturePitch;

    private SurfaceEntityClientProbe() {
    }

    public static void onWorldRendered(Camera camera) {
        if (camera == null || !camera.isReady()) return;
        renderedCamera = new RenderedCamera(true, ++renderedFrame, camera.getPos(),
                camera.getYaw(), camera.getPitch());
    }

    public static Result tick(MinecraftClient client, BlockPos origin, Path evidenceDir) {
        if (observations == null) initializeEvidence(evidenceDir);
        if (evidenceFailed) return new Result(Status.RED, caseIndex, "evidence write failed");
        if (caseIndex >= CASES.size()) {
            return FAILURES.isEmpty()
                    ? new Result(Status.GREEN, CASES.size(), "all required components passed")
                    : new Result(Status.RED, caseIndex, String.join("; ", FAILURES));
        }
        if (stageTick++ > CASE_TIMEOUT) {
            fail("timeout at " + stage);
            nextCase();
            return new Result(Status.PENDING, caseIndex, "continuing after timeout");
        }
        Case probe = CASES.get(caseIndex);
        BlockPos support = origin.add((caseIndex % 4) * 8, 0, (caseIndex / 4) * 8);
        try {
            switch (stage) {
                case PREPARE -> prepare(client, probe, support);
                case SYNC -> sync(client, probe, support);
                case AIM -> aim(client, probe, support);
                case USE -> use(client, probe, support);
                case INITIAL -> initial(client, probe, support);
                case SETTLE -> settle(client, probe, support);
                case CAPTURE_WAIT -> captureWait(client, probe, support);
            }
        } catch (RuntimeException exception) {
            fail(exception.getClass().getSimpleName() + ": " + exception.getMessage());
            nextCase();
        }
        return new Result(Status.PENDING, caseIndex, "running " + probe.id());
    }

    private static void initializeEvidence(Path evidenceDir) {
        try {
            observations = evidenceDir.resolve("surface-entities.tsv");
            Files.writeString(observations,
                    "case\tcomponent\tverdict\tdetail\n", StandardOpenOption.CREATE_NEW);
        } catch (IOException | RuntimeException exception) {
            evidenceFailed = true;
        }
    }

    private static void prepare(MinecraftClient client, Case probe, BlockPos support) {
        if (fixturePending) return;
        fixturePending = true;
        fixtureReady = false;
        entityId = -1;
        initialClientBox = null;
        initialContactError = Double.NaN;
        serverSample = ServerSample.missing();
        MinecraftServer server = client.getServer();
        var dimension = client.world.getRegistryKey();
        server.execute(() -> {
            ServerWorld world = server.getWorld(dimension);
            for (Entity entity : world.getOtherEntities(null, new Box(support).expand(3.0d, 6.0d, 3.0d))) {
                if (entity instanceof BoatEntity || entity instanceof ArmorStandEntity) entity.discard();
            }
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = -4; y <= 3; y++) {
                BlockPos pos = support.add(x, y, z);
                SlabAnchorAttachment.removeAnchor(world, pos);
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
            world.setBlockState(support, probe.support(), Block.NOTIFY_ALL);
            SlabAnchorAttachment.writePlacementDyBatch(world,
                    Map.of(support.toImmutable(), Double.doubleToRawLongBits(probe.dy())));
            // Fixture cells carry the provenance an item placement would have written.
            SlabAnchorAttachment.markPostPolicyPlacements(world, java.util.List.of(support.toImmutable()));
            world.getChunkManager().markForUpdate(support);
            fixtureReady = true;
        });
        stage = Stage.SYNC;
        stageTick = 0;
    }

    private static void sync(MinecraftClient client, Case probe, BlockPos support) {
        var fact = SlabAnchorAttachment.rawPlacementDyFact(client.world, support);
        if (!fixtureReady || !client.world.getBlockState(support).isOf(probe.support().getBlock())
                || !fact.present() || !near(fact.valueOrNaN(), probe.dy())) return;
        syncPlayer(client, probe, visibleTarget(client.world, support));
        stage = Stage.AIM;
        stageTick = 0;
    }

    private static void aim(MinecraftClient client, Case probe, BlockPos support) {
        Vec3d target = visibleTarget(client.world, support);
        syncPlayer(client, probe, target);
        if (stageTick < 3) return;
        client.gameRenderer.updateCrosshairTarget(0.0f);
        if (!(client.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(support)
                || hit.getSide() != Direction.UP) {
            fail("placement crosshair did not own visible support: " + client.crosshairTarget);
            nextCase();
            return;
        }
        row(probe, "PLACEMENT_TARGET", "PASS", "support=" + support + " hitY=" + number(hit.getPos().y));
        stage = Stage.USE;
        stageTick = 0;
    }

    private static void use(MinecraftClient client, Case probe, BlockPos support) {
        syncPlayer(client, probe, visibleTarget(client.world, support));
        client.gameRenderer.updateCrosshairTarget(0.0f);
        ActionResult result;
        if (probe.kind() == Kind.ARMOR_STAND) {
            result = client.interactionManager.interactBlock(
                    client.player, Hand.MAIN_HAND, (BlockHitResult) client.crosshairTarget);
        } else {
            result = client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
        }
        row(probe, "NORMAL_CLIENT_USE", result.isAccepted() ? "PASS" : "FAIL", result.toString());
        if (!result.isAccepted()) {
            fail("normal client interaction refused: " + result);
            nextCase();
            return;
        }
        stage = Stage.INITIAL;
        stageTick = 0;
    }

    private static void initial(MinecraftClient client, Case probe, BlockPos support) {
        requestServerSample(client, probe, support);
        ServerSample server = serverSample;
        if (!server.present() || server.count() != 1 || server.age() <= 0) return;
        Entity entity = client.world.getEntityById(server.entityId());
        if (!matches(probe, entity)) return;
        entityId = server.entityId();
        initialClientBox = entity.getBoundingBox();
        initialContactError = initialClientBox.minY - visibleTop(client.world, support);
        row(probe, "SERVER_HANDOFF", "PASS", "entityId=" + entityId + " serverAge=" + server.age());
        row(probe, "INITIAL_CONTACT", contactVerdict(probe, initialContactError),
                "clientError=" + number(initialContactError) + " serverError=" + number(server.contactError()));
        if (probe.kind() == Kind.ARMOR_STAND
                && (!near(initialContactError, 0.0d) || !near(server.contactError(), 0.0d))) {
            fail("armor stand initial contact was not on the visible support plane");
            nextCase();
            return;
        }
        stage = Stage.SETTLE;
        stageTick = 0;
    }

    private static void settle(MinecraftClient client, Case probe, BlockPos support) {
        requestServerSample(client, probe, support);
        if (stageTick < 8 || !serverSample.present() || serverSample.entityId() != entityId) return;
        Entity entity = client.world.getEntityById(entityId);
        if (!matches(probe, entity)) return;
        Box clientBox = entity.getBoundingBox();
        ServerSample server = serverSample;
        double contactError = clientBox.minY - visibleTop(client.world, support);
        boolean identity = nearBox(clientBox, server.box(), 0.06d);
        boolean stable = nearBox(initialClientBox, clientBox, 0.10d);
        Capture capture = render(client, entity);
        row(probe, "CLIENT_SERVER_IDENTITY", identity ? "PASS" : "FAIL",
                "entityId=" + entityId + " clientBox=" + clientBox + " serverBox=" + server.box());
        row(probe, "STABILITY", stable ? "PASS" : "FAIL",
                "initial=" + initialClientBox + " settled=" + clientBox);
        row(probe, "MODEL_BODY", capture.vertices() > 0 ? "PASS" : "FAIL",
                "vertices=" + capture.vertices() + " model=" + capture.worldModel() + " body=" + clientBox);
        String baselineKey = probe.kind() + ":" + probe.supportClass();
        if (probe.dy() == 0.0d) {
            FLAT_BASELINES.put(baselineKey, capture.withBody(clientBox, contactError));
        } else {
            compareDeep(probe, capture.withBody(clientBox, contactError), FLAT_BASELINES.get(baselineKey));
        }
        beginCapture(client, entity);
        client.gameRenderer.updateCrosshairTarget(0.0f);
        boolean targeted = client.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity().getId() == entityId;
        row(probe, "ENTITY_TARGET", targeted ? "PASS" : "FAIL",
                targeted ? "entityId=" + entityId : String.valueOf(client.crosshairTarget));
        if (!identity || !stable || capture.vertices() == 0 || !targeted) {
            fail("one or more settled client components failed");
            nextCase();
            return;
        }
        captureStartFrame = renderedFrame;
        stage = Stage.CAPTURE_WAIT;
        stageTick = 0;
    }

    private static void captureWait(MinecraftClient client, Case probe, BlockPos support) {
        Entity entity = client.world.getEntityById(entityId);
        if (!matches(probe, entity)) return;
        holdObservationPlayer(client, captureEye, captureYaw, capturePitch);
        if (renderedFrame < captureStartFrame + 2) return;
        RenderedCamera actual = renderedCamera;
        boolean cameraBound = actual.present() && actual.frame() >= captureStartFrame + 2
                && actual.pos().squaredDistanceTo(captureEye) <= 1.0e-4d
                && angleNear(actual.yaw(), captureYaw) && Math.abs(actual.pitch() - capturePitch) <= 0.1f;
        client.gameRenderer.updateCrosshairTarget(0.0f);
        boolean targeted = client.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity().getId() == entityId;
        boolean framed = cameraBound && framed(actual, entity.getBoundingBox(), support);
        row(probe, "RENDERED_CAMERA", cameraBound && targeted && framed ? "PASS" : "FAIL",
                "frame=" + actual.frame() + " cameraBound=" + cameraBound
                        + " targetId=" + (targeted ? entityId : -1) + " fullEntityAndContactInFrame=" + framed);
        if (!cameraBound || !targeted || !framed) {
            fail("actual rendered camera did not bind the named entity and support contact");
            nextCase();
            return;
        }
        String screenshot = ExtendedDepthClientProbe.screenshot(client, "surface-entity-" + probe.id());
        row(probe, "SCREENSHOT", screenshot.startsWith("ERROR") ? "FAIL" : "PASS", screenshot);
        if (screenshot.startsWith("ERROR")) fail("settled rendered-frame screenshot failed");
        nextCase();
    }

    private static void compareDeep(Case probe, Capture deep, Capture flat) {
        if (flat == null) {
            fail("flat baseline missing");
            return;
        }
        boolean bodyDelta = near(deep.body().minY - flat.body().minY, -3.0d)
                && near(deep.body().maxY - flat.body().maxY, -3.0d);
        boolean modelDelta = near(deep.worldModel().minY - flat.worldModel().minY, -3.0d)
                && near(deep.worldModel().maxY - flat.worldModel().maxY, -3.0d)
                && nearBox(deep.localModel(), flat.localModel(), 0.001d);
        boolean contact = near(deep.contactError(), flat.contactError());
        row(probe, "DEPTH_DELTA", bodyDelta && modelDelta ? "PASS" : "FAIL",
                "bodyMinDelta=" + number(deep.body().minY - flat.body().minY)
                        + " modelMinDelta=" + number(deep.worldModel().minY - flat.worldModel().minY));
        row(probe, "WORLD_CONTACT", contact ? "PASS" : "FAIL",
                "flatError=" + number(flat.contactError()) + " deepError=" + number(deep.contactError()));
        if (!bodyDelta || !modelDelta || !contact) fail("deep model/body/contact did not match flat baseline shifted by -3");
    }

    private static void requestServerSample(MinecraftClient client, Case probe, BlockPos support) {
        if (serverSamplePending) return;
        serverSamplePending = true;
        MinecraftServer server = client.getServer();
        var dimension = client.world.getRegistryKey();
        server.execute(() -> {
            ServerWorld world = server.getWorld(dimension);
            List<? extends Entity> entities = probe.kind() == Kind.ARMOR_STAND
                    ? world.getEntitiesByClass(ArmorStandEntity.class, new Box(support).expand(3.0d, 6.0d, 3.0d), Entity::isAlive)
                    : world.getEntitiesByClass(BoatEntity.class, new Box(support).expand(3.0d, 6.0d, 3.0d),
                    entity -> entity.isAlive() && (probe.kind() == Kind.CHEST_BOAT) == (entity instanceof ChestBoatEntity));
            if (entities.size() == 1) {
                Entity entity = entities.getFirst();
                double error = entity.getBoundingBox().minY - visibleTop(world, support);
                serverSample = new ServerSample(true, entities.size(), entity.getId(), entity.age,
                        entity.getBoundingBox(), error);
            } else {
                serverSample = new ServerSample(false, entities.size(), -1, -1, new Box(0, 0, 0, 0, 0, 0), Double.NaN);
            }
            serverSamplePending = false;
        });
    }

    private static void syncPlayer(MinecraftClient client, Case probe, Vec3d target) {
        Vec3d eye = target.add(0.0d, 0.65d, 3.0d);
        Vec3d delta = target.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        double feetY = eye.y - client.player.getStandingEyeHeight();
        requireFinite("placement aim", target.x, target.y, target.z,
                eye.x, eye.y, eye.z, feetY, yaw, pitch);
        ItemStack stack = new ItemStack(probe.item());
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.getAbilities().allowFlying = true;
        client.player.getAbilities().flying = true;
        client.player.sendAbilitiesUpdate();
        client.player.setStackInHand(Hand.MAIN_HAND, stack);
        MinecraftServer server = client.getServer();
        server.execute(() -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) return;
            var player = server.getPlayerManager().getPlayerList().getFirst();
            player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            player.setVelocity(Vec3d.ZERO);
            player.changeGameMode(GameMode.CREATIVE);
            player.getAbilities().allowFlying = true;
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
            player.setStackInHand(Hand.MAIN_HAND, stack.copy());
        });
    }

    private static void beginCapture(MinecraftClient client, Entity entity) {
        Vec3d target = entity.getBoundingBox().getCenter();
        Vec3d eye = target.add(0.0d, 0.15d, 5.0d);
        Vec3d delta = target.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        requireFinite("entity aim", target.x, target.y, target.z,
                eye.x, eye.y, eye.z, yaw, pitch);
        captureEye = eye;
        captureYaw = yaw;
        capturePitch = pitch;
        holdObservationPlayer(client, eye, yaw, pitch);
    }

    private static void holdObservationPlayer(MinecraftClient client, Vec3d eye, float yaw, float pitch) {
        double feetY = eye.y - client.player.getStandingEyeHeight();
        requireFinite("observation camera", eye.x, eye.y, eye.z, feetY, yaw, pitch);
        client.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        client.player.setVelocity(Vec3d.ZERO);
        client.player.getAbilities().allowFlying = true;
        client.player.getAbilities().flying = true;
        client.player.sendAbilitiesUpdate();
        MinecraftServer server = client.getServer();
        server.execute(() -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) return;
            var player = server.getPlayerManager().getPlayerList().getFirst();
            player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
            player.setVelocity(Vec3d.ZERO);
            player.changeGameMode(GameMode.CREATIVE);
            player.getAbilities().allowFlying = true;
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
        });
    }

    private static boolean framed(RenderedCamera camera, Box entity, BlockPos support) {
        Vec3d forward = Vec3d.fromPolar(camera.pitch(), camera.yaw()).normalize();
        List<Vec3d> required = new ArrayList<>();
        for (double x : new double[]{entity.minX, entity.maxX})
            for (double y : new double[]{entity.minY, entity.maxY})
                for (double z : new double[]{entity.minZ, entity.maxZ}) required.add(new Vec3d(x, y, z));
        double contactY = visibleTop(MinecraftClient.getInstance().world, support) + 0.01d;
        for (double x : new double[]{support.getX() + 0.05d, support.getX() + 0.95d})
            for (double z : new double[]{support.getZ() + 0.05d, support.getZ() + 0.95d})
                required.add(new Vec3d(x, contactY, z));
        double minimumDot = Math.cos(Math.toRadians(28.0d));
        for (Vec3d point : required) {
            Vec3d delta = point.subtract(camera.pos());
            if (delta.lengthSquared() <= EPSILON || delta.normalize().dotProduct(forward) < minimumDot) return false;
        }
        return true;
    }

    private static Capture render(MinecraftClient client, Entity entity) {
        VertexBounds bounds = new VertexBounds();
        var delegate = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumerProvider provider = layer -> new CapturingVertexConsumer(delegate.getBuffer(layer), bounds);
        client.getEntityRenderDispatcher().render(entity, 0.0d, 0.0d, 0.0d,
                entity.getYaw(), 1.0f, new MatrixStack(), provider, 0x00F000F0);
        delegate.draw();
        Box local = bounds.box();
        return new Capture(bounds.count, local, local.offset(entity.getPos()), entity.getBoundingBox(), Double.NaN);
    }

    private static Vec3d visibleTarget(World world, BlockPos support) {
        Vec3d target = new Vec3d(
                support.getX() + 0.5d, visibleTop(world, support), support.getZ() + 0.5d);
        requireFinite("visible target", target.x, target.y, target.z);
        return target;
    }

    private static double visibleTop(World world, BlockPos support) {
        return support.getY() + world.getBlockState(support)
                .getCollisionShape(world, support).getMax(Direction.Axis.Y);
    }

    private static boolean matches(Case probe, Entity entity) {
        if (probe.kind() == Kind.ARMOR_STAND) return entity instanceof ArmorStandEntity;
        return entity instanceof BoatEntity && ((probe.kind() == Kind.CHEST_BOAT) == (entity instanceof ChestBoatEntity));
    }

    private static String contactVerdict(Case probe, double error) {
        return probe.kind() != Kind.ARMOR_STAND || near(error, 0.0d) ? "PASS" : "FAIL";
    }

    private static void nextCase() {
        caseIndex++;
        stage = Stage.PREPARE;
        stageTick = 0;
        fixturePending = false;
        fixtureReady = false;
        serverSamplePending = false;
        serverSample = ServerSample.missing();
        captureEye = null;
    }

    private static void fail(String detail) {
        String id = caseIndex < CASES.size() ? CASES.get(caseIndex).id() : "all";
        FAILURES.add(id + ": " + detail);
        row(CASES.get(Math.min(caseIndex, CASES.size() - 1)), "CASE", "FAIL", detail);
    }

    private static void row(Case probe, String component, String verdict, String detail) {
        if (observations == null || evidenceFailed) return;
        try {
            Files.writeString(observations, probe.id() + "\t" + component + "\t" + verdict + "\t"
                    + detail.replace('\t', ' ') + "\n", StandardOpenOption.APPEND);
        } catch (IOException exception) {
            evidenceFailed = true;
        }
    }

    private static List<Case> cases() {
        List<Case> result = new ArrayList<>();
        for (Kind kind : Kind.values()) for (SupportClass support : SupportClass.values()) {
            result.add(new Case(id(kind, support, 0.0d), kind, support, 0.0d));
            result.add(new Case(id(kind, support, -3.0d), kind, support, -3.0d));
        }
        return List.copyOf(result);
    }

    private static String id(Kind kind, SupportClass support, double dy) {
        return kind.name().toLowerCase(Locale.ROOT) + "-" + support.name().toLowerCase(Locale.ROOT)
                + (dy == 0.0d ? "-flat" : "-m3");
    }

    private static boolean near(double a, double b) {
        return Double.isFinite(a) && Double.isFinite(b) && Math.abs(a - b) <= EPSILON;
    }

    private static boolean nearBox(Box a, Box b, double tolerance) {
        return a != null && b != null
                && Math.abs(a.minX - b.minX) <= tolerance && Math.abs(a.minY - b.minY) <= tolerance
                && Math.abs(a.minZ - b.minZ) <= tolerance && Math.abs(a.maxX - b.maxX) <= tolerance
                && Math.abs(a.maxY - b.maxY) <= tolerance && Math.abs(a.maxZ - b.maxZ) <= tolerance;
    }

    private static String number(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.6f", value) : "NaN";
    }

    private static void requireFinite(String label, double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) throw new IllegalStateException(label + " was not finite");
        }
    }

    private static boolean angleNear(float actual, float expected) {
        float difference = Math.abs(actual - expected) % 360.0f;
        return Math.min(difference, 360.0f - difference) <= 0.1f;
    }

    private enum Stage { PREPARE, SYNC, AIM, USE, INITIAL, SETTLE, CAPTURE_WAIT }
    private enum Kind {
        BOAT(Items.OAK_BOAT), CHEST_BOAT(Items.OAK_CHEST_BOAT), ARMOR_STAND(Items.ARMOR_STAND);
        private final Item item;
        Kind(Item item) { this.item = item; }
    }
    private enum SupportClass {
        FULL(Blocks.STONE.getDefaultState()), BOTTOM_SLAB(Blocks.STONE_SLAB.getDefaultState());
        private final BlockState state;
        SupportClass(BlockState state) { this.state = state; }
    }
    private record Case(String id, Kind kind, SupportClass supportClass, double dy) {
        Item item() { return kind.item; }
        BlockState support() { return supportClass.state; }
    }
    private record ServerSample(boolean present, int count, int entityId, int age, Box box, double contactError) {
        static ServerSample missing() { return new ServerSample(false, 0, -1, -1, new Box(0, 0, 0, 0, 0, 0), Double.NaN); }
    }
    private record Capture(int vertices, Box localModel, Box worldModel, Box body, double contactError) {
        Capture withBody(Box value, double error) { return new Capture(vertices, localModel, worldModel, value, error); }
    }
    private record RenderedCamera(boolean present, long frame, Vec3d pos, float yaw, float pitch) {
        static RenderedCamera missing() { return new RenderedCamera(false, -1L, Vec3d.ZERO, 0.0f, 0.0f); }
    }
    public enum Status { PENDING, GREEN, RED }
    public record Result(Status status, int cases, String detail) { }

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
            if (count == 0) throw new IllegalStateException("entity renderer emitted no vertices");
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
}
