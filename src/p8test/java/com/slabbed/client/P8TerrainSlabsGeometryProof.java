package com.slabbed.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Isolated integration proof for Terrain Slabs 3.1.2 geometry coherence. */
public final class P8TerrainSlabsGeometryProof {
    private static final String PROPERTY = "slabbed.p8.proof";
    private static final String WORLD_NAME = "p8-terrain-slabs-proof";
    private static final Path PROOF_DIRECTORY = Path.of("proof");
    private static final ResourceLocation TERRAIN_GRASS_SLAB =
            ResourceLocation.fromNamespaceAndPath("terrain_slabs", "grass_slab");
    /** A Terrain Slabs surface is not a Slabbed support, so its occupant stays at grid. */
    private static final double EXPECTED_SLABBED_TERRAIN_DY = 0.0d;
    /** The deliberate gap that ruling accepts between a TS slab's top and its occupant. */
    private static final double EXPECTED_TS_SUPPORT_GAP = 0.5d;
    /** The vanilla contrast. Every flush assertion is paired with this or it proves nothing. */
    private static final double EXPECTED_VANILLA_DY = -0.5d;
    private static final double EPSILON = 1.0e-6d;
    private static final int MAX_TICKS = 2_400;

    private static boolean registered;
    private static boolean worldQueued;
    private static boolean fixtureQueued;
    private static boolean terminal;
    private static int ticks;
    private static volatile Fixture fixture;
    private static volatile String serverFailure;

    private P8TerrainSlabsGeometryProof() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        if (!Boolean.getBoolean(PROPERTY)) {
            throw new IllegalStateException("The P8 client proof requires its launch property");
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(P8TerrainSlabsGeometryProof::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (terminal) {
            return;
        }
        ticks++;
        Minecraft minecraft = Minecraft.getInstance();
        if (serverFailure != null) {
            fail(minecraft, "server_" + serverFailure);
            return;
        }
        if (ticks > MAX_TICKS) {
            fail(minecraft, "timeout");
            return;
        }
        if (minecraft.level == null || minecraft.player == null || !minecraft.hasSingleplayerServer()) {
            maybeOpenWorld(minecraft);
            return;
        }
        if (!fixtureQueued) {
            queueFixture(minecraft);
            return;
        }
        Fixture current = fixture;
        if (current == null || !clientFactsReady(minecraft, current)) {
            return;
        }

        try {
            Result result = evaluate(minecraft, current);
            Slabbed.LOGGER.info(
                    "[P8_TERRAIN_SLABS_PROOF] result={} versions={} classification={} vanillaSlab={} flat={} visual={} collisionGap={} raycast={} noCompounding={} terrainStoredHalfSteps={} modelShift={} reason={}",
                    result.green() ? "GREEN" : "RED",
                    result.versions(),
                    result.classification(),
                    result.vanillaSlab(),
                    result.flat(),
                    result.visual(),
                    result.collisionGap(),
                    result.raycast(),
                    result.noCompounding(),
                    current.terrain().storedHalfSteps(),
                    format(result.modelShift()),
                    result.reason());
            if (!result.green()) {
                fail(minecraft, result.reason());
                return;
            }
            write("p8-terrain-slabs.ok",
                    "versions=true classification=true vanilla_slab=true flat=true visual=true collision_gap=true raycast=true no_compounding=true\n");
            terminal = true;
            minecraft.stop();
        } catch (RuntimeException exception) {
            Slabbed.LOGGER.error("P8 Terrain Slabs proof raised an exception", exception);
            fail(minecraft, "exception_" + exception.getClass().getSimpleName());
        }
    }

    private static void maybeOpenWorld(Minecraft minecraft) {
        if (worldQueued || ticks < 40 || !minecraft.isGameLoadFinished()) {
            return;
        }
        worldQueued = true;
        LevelSettings settings = new LevelSettings(
                "P8 Terrain Slabs Proof",
                GameType.SURVIVAL,
                false,
                Difficulty.PEACEFUL,
                true,
                new GameRules(),
                WorldDataConfiguration.DEFAULT);
        Screen previous = minecraft.screen;
        minecraft.createWorldOpenFlows().createFreshLevel(
                WORLD_NAME,
                settings,
                new WorldOptions(0L, false, false),
                P8TerrainSlabsGeometryProof::flatDimensions,
                previous);
    }

    private static WorldDimensions flatDimensions(RegistryAccess registryAccess) {
        return registryAccess.registryOrThrow(Registries.WORLD_PRESET)
                .getHolderOrThrow(WorldPresets.FLAT)
                .value()
                .createWorldDimensions();
    }

    private static void queueFixture(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        fixtureQueued = true;
        server.execute(() -> {
            try {
                ServerLevel world = server.overworld();
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                if (players.isEmpty()) {
                    throw new IllegalStateException("player_missing");
                }
                ServerPlayer player = players.getFirst();
                Block terrainBlock = BuiltInRegistries.BLOCK.getOptional(TERRAIN_GRASS_SLAB)
                        .orElseThrow(() -> new IllegalStateException("terrain_grass_slab_missing"));
                BlockState terrainSupport = terrainBlock.defaultBlockState();
                if (!(terrainBlock instanceof SlabBlock)
                        || !terrainSupport.hasProperty(SlabBlock.TYPE)) {
                    throw new IllegalStateException("terrain_grass_slab_contract_changed");
                }
                terrainSupport = terrainSupport.setValue(SlabBlock.TYPE, SlabType.BOTTOM);

                BlockPos origin = player.blockPosition().offset(4, 4, 0);
                BlockPos terrainSupportPos = origin.immutable();
                BlockPos vanillaSupportPos = origin.offset(4, 0, 0);
                BlockPos flatSupportPos = origin.offset(8, 0, 0);
                clear(world, origin);
                world.setBlock(terrainSupportPos, terrainSupport, Block.UPDATE_ALL);
                world.setBlock(vanillaSupportPos, Blocks.STONE_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
                world.setBlock(flatSupportPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

                BlockPos snowSupportPos = origin.offset(0, 0, 2);
                BlockPos bushSupportPos = origin.offset(4, 0, 2);
                BlockState vanillaBottom = Blocks.STONE_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
                world.setBlock(snowSupportPos, vanillaBottom, Block.UPDATE_ALL);
                world.setBlock(bushSupportPos, vanillaBottom, Block.UPDATE_ALL);
                world.setBlock(snowSupportPos.above(), Blocks.SNOW.defaultBlockState(), Block.UPDATE_ALL);
                world.setBlock(bushSupportPos.above(), Blocks.DANDELION.defaultBlockState(), Block.UPDATE_ALL);

                Placement terrain = placeGrass(world, player, terrainSupportPos);
                Placement vanilla = placeGrass(world, player, vanillaSupportPos);
                Placement flat = placeGrass(world, player, flatSupportPos);
                String terrainVersion = version("terrain_slabs");
                String architecturyVersion = version("architectury");
                fixture = new Fixture(
                        terrain,
                        vanilla,
                        flat,
                        snowSupportPos.above(),
                        bushSupportPos.above(),
                        "3.1.2".equals(terrainVersion),
                        "13.0.8".equals(architecturyVersion));
            } catch (RuntimeException exception) {
                serverFailure = exception.getClass().getSimpleName() + "_" + safeMessage(exception);
            }
        });
    }

    private static void clear(ServerLevel world, BlockPos origin) {
        for (int dx = -2; dx <= 10; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 3; dy++) {
                    world.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static Placement placeGrass(ServerLevel world, ServerPlayer player, BlockPos support) {
        BlockPos subject = support.above();
        player.setPos(support.getX() + 0.5d, support.getY() + 2.0d, support.getZ() + 0.5d);
        ItemStack selected = new ItemStack(Blocks.GRASS_BLOCK, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, selected);
        AtomicInteger placeEvents = new AtomicInteger();
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getLevel() == world && event.getPos().equals(subject)) {
                placeEvents.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
        InteractionResult interaction;
        try {
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(support).add(0.0d, 0.5d, 0.0d),
                    Direction.UP,
                    support,
                    false);
            interaction = selected.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        int stored = SlabPlacementHeightAttachment.storedHalfSteps(
                        world.getChunkAt(subject), subject)
                .orElse(Integer.MIN_VALUE);
        return new Placement(
                support,
                subject,
                interaction.consumesAction(),
                selected.getCount() == 2,
                placeEvents.get() == 1,
                world.getBlockState(subject).is(Blocks.GRASS_BLOCK),
                stored);
    }

    private static String version(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("missing");
    }

    private static boolean clientFactsReady(Minecraft minecraft, Fixture current) {
        return clientRowReady(minecraft, current.terrain())
                && clientRowReady(minecraft, current.vanilla())
                && clientRowReady(minecraft, current.flat())
                && presentOnClient(minecraft, current.snowSubject(), Blocks.SNOW)
                && presentOnClient(minecraft, current.bushSubject(), Blocks.DANDELION);
    }

    /**
     * Readiness for the compounding rows. Deliberately NOT fact-based: both subjects are thin
     * top layers, so they are storage-excluded at capture and no placement fact ever arrives.
     * Waiting on one would spend the whole tick budget and report a timeout instead of a verdict.
     */
    private static boolean presentOnClient(Minecraft minecraft, BlockPos pos, Block expected) {
        return minecraft.level != null && minecraft.level.getBlockState(pos).is(expected);
    }

    private static boolean clientRowReady(Minecraft minecraft, Placement placement) {
        if (minecraft.level == null
                || !minecraft.level.getBlockState(placement.subject()).is(Blocks.GRASS_BLOCK)) {
            return false;
        }
        double clientStored = SlabPlacementHeightAttachment.storedOffset(
                minecraft.level, placement.subject());
        return placement.storedHalfSteps() != Integer.MIN_VALUE
                && near(clientStored, placement.storedHalfSteps() * 0.5d);
    }

    private static Result evaluate(Minecraft minecraft, Fixture current) {
        Placement terrain = current.terrain();
        Placement vanilla = current.vanilla();
        Placement flat = current.flat();
        BlockState terrainSubject = minecraft.level.getBlockState(terrain.subject());
        BlockState terrainSupport = minecraft.level.getBlockState(terrain.support());

        boolean versions = current.terrainVersion() && current.architecturyVersion();
        BlockState vanillaSubject = minecraft.level.getBlockState(vanilla.subject());

        // A TS surface is not a Slabbed support: its occupant stays at grid and records a FLAT
        // fact, and the TS slab itself takes no Slabbed offset and carries no fact of its own.
        // Asserted as exactly 0 rather than the headless row's absent-or-zero disjunction, because
        // grass IS storage-eligible here so a fact is definitely written. Do not "harmonise" the
        // two - the tighter assertion is the better proof.
        boolean classification = placementGreen(terrain)
                && terrain.storedHalfSteps() == 0
                && near(SlabSupport.getYOffset(
                        minecraft.level, terrain.support(), terrainSupport), 0.0d)
                && !Double.isFinite(SlabPlacementHeightAttachment.storedOffset(
                        minecraft.level, terrain.support()));

        // Double duty: Slabbed still lowers on a vanilla slab, AND the compat deferral did not
        // OVER-fire - grass is neither on-top-tagged nor a bush, so TS must contribute nothing.
        boolean vanillaGreen = placementGreen(vanilla)
                && vanilla.storedHalfSteps() == -1
                && near(vanillaSubject.getOffset(minecraft.level, vanilla.subject()).y, 0.0d);
        boolean flatGreen = placementGreen(flat) && flat.storedHalfSteps() == 0;

        // Every flush measurement on the TS subject is paired with the same measurement on the
        // vanilla subject in this same run. Flush alone is the trivial default and would stay
        // green with the offset logic deleted; the contrast is what carries the proof.
        //
        // DO NOT REMOVE THE VANILLA HALF AS DUPLICATION. It is load-bearing structure, not
        // style. The check that keeps this lane honest: stub resolveYOffsetWithinRegion to
        // return 0.0 and rerun - vanillaSlab AND visual must both go RED. If they stay green
        // the pairing has been broken and this lane no longer proves anything (verified
        // 2026-08-20).
        double terrainClientDy = ClientDy.dyFor(minecraft.level, terrain.subject(), terrainSubject);
        double terrainOutlineMin = terrainSubject.getShape(
                minecraft.level, terrain.subject(), CollisionContext.empty()).bounds().minY;
        double terrainModelShift = fallbackRenderShift(minecraft, terrainSubject, terrain, flat);
        double vanillaClientDy = ClientDy.dyFor(minecraft.level, vanilla.subject(), vanillaSubject);
        double vanillaOutlineMin = vanillaSubject.getShape(
                minecraft.level, vanilla.subject(), CollisionContext.empty()).bounds().minY;
        double vanillaModelShift = fallbackRenderShift(minecraft, vanillaSubject, vanilla, flat);
        boolean supportModelEmitted = renderBounds(
                minecraft,
                new PassthroughModel(minecraft.getBlockRenderer().getBlockModel(terrainSupport)),
                terrainSupport,
                terrain.support()).vertices() > 0;
        boolean visual = near(terrainClientDy, EXPECTED_SLABBED_TERRAIN_DY)
                && near(terrainOutlineMin, EXPECTED_SLABBED_TERRAIN_DY)
                && near(terrainModelShift, EXPECTED_SLABBED_TERRAIN_DY)
                && near(vanillaClientDy, EXPECTED_VANILLA_DY)
                && near(vanillaOutlineMin, EXPECTED_VANILLA_DY)
                && near(vanillaModelShift, EXPECTED_VANILLA_DY)
                && supportModelEmitted;

        // The gap is deliberate, so assert it is exactly the ruled size rather than absent. Both
        // probes invert with it: the cell above the TS slab is now empty, and the occupant's own
        // cell is now solid.
        double supportTop = terrain.support().getY()
                + terrainSupport.getCollisionShape(
                        minecraft.level, terrain.support(), CollisionContext.empty()).bounds().maxY;
        double subjectBottom = terrain.subject().getY() + terrainOutlineMin;
        AABB gapBody = box(terrain.subject(), terrain.subject().getY() - 0.25d);
        AABB subjectBody = box(terrain.subject(), terrain.subject().getY() + 0.75d);
        boolean collisionGap = near(subjectBottom - supportTop, EXPECTED_TS_SUPPORT_GAP)
                && minecraft.level.noCollision(gapBody)
                && !minecraft.level.noCollision(subjectBody);

        Vec3 start = Vec3.atLowerCornerOf(terrain.subject())
                .add(0.5d, EXPECTED_SLABBED_TERRAIN_DY + 0.25d, 3.0d);
        BlockHitResult hit = SlabbedOffsetRaycast.raycast(
                minecraft.level,
                start,
                start.add(0.0d, 0.0d, -6.0d),
                CollisionContext.empty());
        boolean raycast = hit.getBlockPos().equals(terrain.subject());

        // Only this lane can prove it: where TS positions an object itself, Slabbed must add
        // nothing. Asserted as the PAIR, not as a sum - the sum would be arithmetic composition,
        // not a measurement of what either side contributed.
        boolean noCompounding = contributesNothing(minecraft, current.snowSubject())
                && contributesNothing(minecraft, current.bushSubject());

        String reason = firstFailure(
                versions, classification, vanillaGreen, flatGreen, visual, collisionGap,
                raycast, noCompounding);
        return new Result(
                versions,
                classification,
                vanillaGreen,
                flatGreen,
                visual,
                collisionGap,
                raycast,
                noCompounding,
                terrainModelShift,
                reason);
    }

    /**
     * True when Slabbed contributes no offset of its own at a cell Terrain Slabs positions.
     * Both halves are required: Slabbed silent AND TS actually acting, so the row cannot pass
     * because TS simply did nothing.
     */
    private static boolean contributesNothing(Minecraft minecraft, BlockPos pos) {
        BlockState state = minecraft.level.getBlockState(pos);
        return near(SlabSupport.getYOffset(minecraft.level, pos, state), 0.0d)
                && near(state.getOffset(minecraft.level, pos).y, EXPECTED_VANILLA_DY);
    }

    private static boolean placementGreen(Placement placement) {
        return placement.accepted()
                && placement.consumedOne()
                && placement.singlePlaceEvent()
                && placement.placedGrass();
    }

    private static double fallbackRenderShift(
            Minecraft minecraft,
            BlockState state,
            Placement terrain,
            Placement flat
    ) {
        BakedModel renderedModel = minecraft.getBlockRenderer().getBlockModel(state);
        BakedModel passthrough = new PassthroughModel(renderedModel);
        Bounds flatBounds = renderBounds(minecraft, passthrough, state, flat.subject());
        Bounds terrainBounds = renderBounds(minecraft, passthrough, state, terrain.subject());
        return terrainBounds.minY() - flatBounds.minY();
    }

    private static Bounds renderBounds(
            Minecraft minecraft,
            BakedModel model,
            BlockState state,
            BlockPos pos
    ) {
        CapturingVertexConsumer consumer = new CapturingVertexConsumer();
        minecraft.getBlockRenderer().getModelRenderer().tesselateBlock(
                minecraft.level,
                model,
                state,
                pos,
                new PoseStack(),
                consumer,
                true,
                RandomSource.create(31L),
                state.getSeed(pos),
                0);
        if (consumer.vertices == 0) {
            throw new IllegalStateException("render_emitted_no_vertices");
        }
        return new Bounds(consumer.minY, consumer.maxY, consumer.vertices);
    }

    private static AABB box(BlockPos owner, double centerY) {
        return new AABB(
                owner.getX() + 0.35d, centerY - 0.10d, owner.getZ() + 0.35d,
                owner.getX() + 0.65d, centerY + 0.10d, owner.getZ() + 0.65d);
    }

    private static String firstFailure(
            boolean versions,
            boolean classification,
            boolean vanillaSlab,
            boolean flat,
            boolean visual,
            boolean collisionGap,
            boolean raycast,
            boolean noCompounding
    ) {
        if (!versions) return "dependency_identity";
        if (!classification) return "terrain_classification";
        if (!vanillaSlab) return "vanilla_slab_control";
        if (!flat) return "flat_control";
        if (!visual) return "visual_alignment";
        if (!collisionGap) return "collision_gap";
        if (!raycast) return "raycast_alignment";
        if (!noCompounding) return "compounding_detected";
        return "green";
    }

    private static boolean near(double actual, double expected) {
        return Double.isFinite(actual) && Math.abs(actual - expected) <= EPSILON;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null ? "no_message" : message.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static void fail(Minecraft minecraft, String reason) {
        if (terminal) {
            return;
        }
        terminal = true;
        write("p8-terrain-slabs.failed", reason + "\n");
        minecraft.stop();
    }

    private static void write(String name, String content) {
        try {
            Files.createDirectories(PROOF_DIRECTORY);
            Files.writeString(PROOF_DIRECTORY.resolve(name), content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write P8 proof receipt", exception);
        }
    }

    private record Placement(
            BlockPos support,
            BlockPos subject,
            boolean accepted,
            boolean consumedOne,
            boolean singlePlaceEvent,
            boolean placedGrass,
            int storedHalfSteps
    ) {
    }

    private record Fixture(
            Placement terrain,
            Placement vanilla,
            Placement flat,
            BlockPos snowSubject,
            BlockPos bushSubject,
            boolean terrainVersion,
            boolean architecturyVersion
    ) {
    }

    private record Bounds(double minY, double maxY, int vertices) {
    }

    private record Result(
            boolean versions,
            boolean classification,
            boolean vanillaSlab,
            boolean flat,
            boolean visual,
            boolean collisionGap,
            boolean raycast,
            boolean noCompounding,
            double modelShift,
            String reason
    ) {
        boolean green() {
            return versions && classification && vanillaSlab && flat && visual && collisionGap
                    && raycast && noCompounding;
        }
    }

    private static final class PassthroughModel implements BakedModel {
        private final BakedModel delegate;

        private PassthroughModel(BakedModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction direction, RandomSource random) {
            return delegate.getQuads(state, direction, random);
        }

        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return delegate.isGui3d(); }
        @Override public boolean usesBlockLight() { return delegate.usesBlockLight(); }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return delegate.getParticleIcon(); }
        @Override public ItemOverrides getOverrides() { return delegate.getOverrides(); }
    }

    private static final class CapturingVertexConsumer implements VertexConsumer {
        private double minY = Double.POSITIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private int vertices;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            vertices++;
            return this;
        }

        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
