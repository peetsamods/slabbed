package com.slabbed.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.util.RuntimeDiagnostics;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.LongToIntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.NeoForge;

/** Isolated physical-client proof for the P7 visual ownership matrix. */
public final class P7ClientVisualProof {
    private static final String PROPERTY = "slabbed.p7.proof";
    private static final String WORLD_NAME = "p7-visual-proof";
    private static final Path PROOF_DIRECTORY = Path.of("proof");
    private static final double EXPECTED_DY = -0.5d;
    private static final double EPSILON = 1.0e-6d;
    private static final int MAX_TICKS = 2_400;
    private static final int SAMPLE_TIMEOUT_TICKS = 240;
    private static final long ALLOCATION_BUDGET_BYTES_PER_CALL = 4_096L;

    private static boolean registered;
    private static boolean worldQueued;
    private static boolean fixtureQueued;
    private static boolean samplesArmed;
    private static boolean factPhaseGreen;
    private static boolean geometricSamplesArmed;
    private static boolean terminal;
    private static int ticks;
    private static int samplesArmedTick;
    private static int geometricSamplesArmedTick;
    private static volatile Fixture fixture;
    private static volatile String serverFailure;

    private P7ClientVisualProof() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        if (!Boolean.getBoolean(PROPERTY)) {
            throw new IllegalStateException("The P7 client proof requires its launch property");
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(P7ClientVisualProof::onClientTick);
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
        if (!samplesArmed) {
            samplesArmed = true;
            samplesArmedTick = ticks;
            OffsetBlockStateModel.resetModelDyOwnerSample(current.lowered());
            OffsetBlockStateModel.resetFullMeshBoundsSample(current.lowered());
            OffsetBlockStateModel.resetStepCullSample(current.lowered());
            RuntimeDiagnostics.resetRenderStepCullTrace(current.lowered());
            markRenderDirty(minecraft, current);
            return;
        }

        if (!factPhaseGreen) {
            OffsetBlockStateModel.ModelDyOwnerSample owner = OffsetBlockStateModel.snapshotModelDyOwnerSample();
            OffsetBlockStateModel.FullMeshBoundsSample mesh = OffsetBlockStateModel.snapshotFullMeshBoundsSample();
            OffsetBlockStateModel.StepCullSample cull = OffsetBlockStateModel.snapshotStepCullSample();
            BakedModel activeModel = minecraft.getBlockRenderer().getBlockModel(
                    minecraft.level.getBlockState(current.lowered()));
            if (activeModel instanceof OffsetBlockStateModel
                    && (!owner.seen() || !mesh.seen() || !cull.seen())
                    && ticks - samplesArmedTick < SAMPLE_TIMEOUT_TICKS) {
                if ((ticks - samplesArmedTick) % 20 == 0) {
                    markRenderDirty(minecraft, current);
                }
                return;
            }

            try {
                Result result = evaluate(minecraft, current, owner, mesh, cull);
                Slabbed.LOGGER.info(
                        "[P7_CLIENT_VISUAL_PROOF] result={} numeric={} model={} singleOwner={} cull={} bounded={} allocation={} allocationBytesPerCall={} fallbackShift={} reason={}",
                        result.green() ? "GREEN" : "RED",
                        result.numeric(),
                        result.model(),
                        result.singleOwner(),
                        result.cull(),
                        result.bounded(),
                        result.allocation(),
                        result.allocationBytesPerCall(),
                        format(result.fallbackShift()),
                        result.reason());
                if (!result.green()) {
                    fail(minecraft, result.reason());
                    return;
                }
                factPhaseGreen = true;
            } catch (RuntimeException exception) {
                Slabbed.LOGGER.error("P7 client visual proof raised an exception", exception);
                fail(minecraft, "exception_" + exception.getClass().getSimpleName());
            }
            return;
        }

        // Absolute-WYSIWYG phase (DY_SPEC L1, maintainer ruling, 2026-08-16): an unstored
        // geometric cell must render its MESH at the same dy the outline authority resolves.
        if (!geometricSamplesArmed) {
            geometricSamplesArmed = true;
            geometricSamplesArmedTick = ticks;
            OffsetBlockStateModel.resetModelDyOwnerSample(current.geometric());
            OffsetBlockStateModel.resetFullMeshBoundsSample(current.geometric());
            markRenderDirty(minecraft, current);
            return;
        }
        OffsetBlockStateModel.ModelDyOwnerSample geometricOwner =
                OffsetBlockStateModel.snapshotModelDyOwnerSample();
        OffsetBlockStateModel.FullMeshBoundsSample geometricMesh =
                OffsetBlockStateModel.snapshotFullMeshBoundsSample();
        BakedModel geometricModel = minecraft.getBlockRenderer().getBlockModel(
                minecraft.level.getBlockState(current.geometric()));
        if (geometricModel instanceof OffsetBlockStateModel
                && (!geometricOwner.seen() || !geometricMesh.seen())
                && ticks - geometricSamplesArmedTick < SAMPLE_TIMEOUT_TICKS) {
            if ((ticks - geometricSamplesArmedTick) % 20 == 0) {
                markRenderDirty(minecraft, current);
            }
            return;
        }

        try {
            GeometricResult result = evaluateGeometric(minecraft, current, geometricOwner, geometricMesh);
            Slabbed.LOGGER.info(
                    "[P7_CLIENT_GEOMETRIC_PROOF] result={} wrapped={} screen={} factAbsent={} clientDy={} outlineMinY={} rayHit={} ownerSeen={} ownerEmitCalls={} ownerAppliedCalls={} ownerLastDy={} ownerView={} meshSeen={} meshShiftMinY={} meshShiftMaxY={} meshReason={} meshView={} reason={}",
                    result.green() ? "GREEN" : "RED",
                    minecraft.getBlockRenderer().getBlockModel(
                            minecraft.level.getBlockState(current.geometric()))
                            instanceof OffsetBlockStateModel,
                    result.screen(),
                    result.factAbsent(),
                    format(result.clientDy()),
                    format(result.outlineMinY()),
                    result.rayHit(),
                    geometricOwner.seen(),
                    geometricOwner.emitCalls(),
                    geometricOwner.appliedCalls(),
                    format(geometricOwner.lastDy()),
                    geometricOwner.viewClass(),
                    geometricMesh.seen(),
                    format(geometricMesh.minAfterY() - geometricMesh.minBeforeY()),
                    format(geometricMesh.maxAfterY() - geometricMesh.maxBeforeY()),
                    geometricMesh.reason(),
                    geometricMesh.viewClass(),
                    result.reason());
            if (!result.green()) {
                fail(minecraft, result.reason());
                return;
            }
            write("p7-client.ok",
                    "numeric=true model=true single_owner=true cull=true bounded=true allocation=true geometric=true\n");
            terminal = true;
            minecraft.stop();
        } catch (RuntimeException exception) {
            Slabbed.LOGGER.error("P7 client geometric proof raised an exception", exception);
            fail(minecraft, "exception_" + exception.getClass().getSimpleName());
        }
    }

    private static void maybeOpenWorld(Minecraft minecraft) {
        if (worldQueued || ticks < 40 || !minecraft.isGameLoadFinished()) {
            return;
        }
        worldQueued = true;
        LevelSettings settings = new LevelSettings(
                "P7 Visual Proof",
                GameType.CREATIVE,
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
                P7ClientVisualProof::flatDimensions,
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
                BlockPos origin = players.isEmpty()
                        ? new BlockPos(4, world.getMinBuildHeight() + 8, 4)
                        : players.getFirst().blockPosition().offset(4, 4, 0);
                BlockPos lowered = origin.immutable();
                BlockPos flat = lowered.east();
                BlockPos geometric = lowered.east(3);
                for (int dx = -1; dx <= 4; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dy = -2; dy <= 2; dy++) {
                            world.setBlock(lowered.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        }
                    }
                }
                world.setBlock(lowered, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                world.setBlock(flat, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                // The geometric cell is deliberately factless: setBlock stores no placement
                // fact, so it must resolve through the legacy geometric lane on every consumer.
                world.setBlock(geometric.below(), Blocks.STONE_SLAB.defaultBlockState(), Block.UPDATE_ALL);
                world.setBlock(geometric, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                if (!SlabPlacementHeightAttachment.putHalfSteps(world.getChunkAt(lowered), lowered, -1)
                        || !SlabPlacementHeightAttachment.putHalfSteps(world.getChunkAt(flat), flat, 0)) {
                    throw new IllegalStateException("fixture_fact_write_failed");
                }
                fixture = new Fixture(lowered, flat, geometric);
            } catch (RuntimeException exception) {
                serverFailure = exception.getClass().getSimpleName();
            }
        });
    }

    private static boolean clientFactsReady(Minecraft minecraft, Fixture current) {
        if (minecraft.level == null
                || !minecraft.level.getBlockState(current.lowered()).is(Blocks.STONE)
                || !minecraft.level.getBlockState(current.flat()).is(Blocks.STONE)
                || !minecraft.level.getBlockState(current.geometric()).is(Blocks.STONE)
                || !SlabSupport.isBottomSlab(minecraft.level.getBlockState(current.geometric().below()))) {
            return false;
        }
        return near(SlabPlacementHeightAttachment.storedOffset(minecraft.level, current.lowered()), EXPECTED_DY)
                && near(SlabPlacementHeightAttachment.storedOffset(minecraft.level, current.flat()), 0.0d)
                && Double.isNaN(SlabPlacementHeightAttachment.storedOffset(minecraft.level, current.geometric()));
    }

    private static Result evaluate(
            Minecraft minecraft,
            Fixture current,
            OffsetBlockStateModel.ModelDyOwnerSample owner,
            OffsetBlockStateModel.FullMeshBoundsSample mesh,
            OffsetBlockStateModel.StepCullSample cull
    ) {
        BlockState loweredState = minecraft.level.getBlockState(current.lowered());
        BlockState flatState = minecraft.level.getBlockState(current.flat());
        double dy = ClientDy.dyFor(minecraft.level, current.lowered(), loweredState);
        double outline = loweredState.getShape(
                minecraft.level, current.lowered(), CollisionContext.empty()).bounds().minY;
        Vec3 start = Vec3.atLowerCornerOf(current.lowered()).add(0.5d, EXPECTED_DY + 0.5d, 3.0d);
        Vec3 end = start.add(0.0d, 0.0d, -6.0d);
        BlockHitResult hit = SlabbedOffsetRaycast.raycast(
                minecraft.level, start, end, CollisionContext.empty());
        boolean numeric = near(dy, EXPECTED_DY)
                && near(outline, EXPECTED_DY)
                && hit.getBlockPos().equals(current.lowered());

        BakedModel renderedModel = minecraft.getBlockRenderer().getBlockModel(loweredState);
        boolean wrappedModelGreen = !(renderedModel instanceof OffsetBlockStateModel)
                || (owner.seen()
                    && owner.appliedCalls() > 0
                    && owner.appliedCalls() == owner.emitCalls()
                    && near(owner.totalAppliedDy(), owner.appliedCalls() * EXPECTED_DY)
                    && mesh.seen()
                    && mesh.verticesVisited() > 0
                    && near(mesh.minAfterY() - mesh.minBeforeY(), EXPECTED_DY)
                    && near(mesh.maxAfterY() - mesh.maxBeforeY(), EXPECTED_DY));
        boolean model = renderedModel != null && wrappedModelGreen;

        double fallbackShift = fallbackRenderShift(minecraft, renderedModel, loweredState, current);
        boolean cullGreen = SlabSupport.isSlabHeightStepFace(
                    minecraft.level, current.lowered(), loweredState, Direction.EAST)
                && SlabSupport.isSlabHeightStepFace(
                    minecraft.level, current.flat(), flatState, Direction.WEST)
                && RuntimeDiagnostics.renderStepCullTraceSeen()
                && RuntimeDiagnostics.renderStepCullTraceRelaxed()
                && (!(renderedModel instanceof OffsetBlockStateModel)
                    || (cull.seen()
                        && cull.clearStepCullFaces()
                        && cull.stepFacesSeen() > 0
                        && cull.stepCullFacesCleared() > 0));

        boolean singleOwner = near(fallbackShift, EXPECTED_DY);
        BoundedResult bounded = boundedViewCheck(loweredState, current.lowered());
        AllocationResult allocation = allocationCheck(loweredState, current.lowered());
        String reason = firstFailure(numeric, model, singleOwner, cullGreen, bounded.green(), allocation.green());
        return new Result(
                numeric,
                model,
                singleOwner,
                cullGreen,
                bounded.green(),
                allocation.green(),
                allocation.bytesPerCall(),
                fallbackShift,
                reason);
    }

    /**
     * The absolute-WYSIWYG gate: the factless geometric cell must resolve the outline triad at
     * {@code -0.5} AND the real chunk-mesh instrumentation must observe the same {@code -0.5}
     * applied to its quads. A grid-height mesh over a lowered outline is the L1 violation.
     */
    private static GeometricResult evaluateGeometric(
            Minecraft minecraft,
            Fixture current,
            OffsetBlockStateModel.ModelDyOwnerSample owner,
            OffsetBlockStateModel.FullMeshBoundsSample mesh
    ) {
        BlockPos pos = current.geometric();
        BlockState state = minecraft.level.getBlockState(pos);
        boolean factAbsent = Double.isNaN(SlabPlacementHeightAttachment.storedOffset(minecraft.level, pos));
        double clientDy = ClientDy.dyFor(minecraft.level, pos, state);
        double outlineMinY = state.getShape(
                minecraft.level, pos, CollisionContext.empty()).bounds().minY;
        Vec3 start = Vec3.atLowerCornerOf(pos).add(0.5d, EXPECTED_DY + 0.5d, 3.0d);
        Vec3 end = start.add(0.0d, 0.0d, -6.0d);
        BlockHitResult hit = SlabbedOffsetRaycast.raycast(
                minecraft.level, start, end, CollisionContext.empty());
        boolean rayHit = hit.getBlockPos().equals(pos);
        boolean numeric = factAbsent
                && near(clientDy, EXPECTED_DY)
                && near(outlineMinY, EXPECTED_DY)
                && rayHit;

        BakedModel renderedModel = minecraft.getBlockRenderer().getBlockModel(state);
        boolean model = renderedModel instanceof OffsetBlockStateModel
                && owner.seen()
                && owner.appliedCalls() > 0
                && owner.appliedCalls() == owner.emitCalls()
                && near(owner.totalAppliedDy(), owner.appliedCalls() * EXPECTED_DY)
                && mesh.seen()
                && mesh.verticesVisited() > 0
                && near(mesh.minAfterY() - mesh.minBeforeY(), EXPECTED_DY)
                && near(mesh.maxAfterY() - mesh.maxBeforeY(), EXPECTED_DY);

        // Tier-0 screen pins: the factless slab-backed cell must be granted mesh work while a
        // plain flush ground cell (away from the floating fixture) must be screened out — the
        // zero-cost guarantee for ordinary terrain.
        BlockPos groundProbe = minecraft.level.getHeightmapPos(
                Heightmap.Types.WORLD_SURFACE, pos.offset(8, 0, 8)).below();
        BlockState groundState = minecraft.level.getBlockState(groundProbe);
        boolean screen = SlabSupport.mayNeedMeshOffsetWork(minecraft.level, pos, state)
                && !groundState.isAir()
                && !SlabSupport.mayNeedMeshOffsetWork(minecraft.level, groundProbe, groundState);

        String reason = !numeric
                ? "geometric_numeric"
                : (!model ? "geometric_model_or_mesh" : (!screen ? "mesh_screen" : "green"));
        return new GeometricResult(numeric, model, screen, factAbsent, clientDy, outlineMinY, rayHit, reason);
    }

    private static double fallbackRenderShift(
            Minecraft minecraft,
            BakedModel renderedModel,
            BlockState state,
            Fixture current
    ) {
        BakedModel passthrough = new PassthroughModel(renderedModel);
        Bounds flat = renderBounds(minecraft, passthrough, state, current.flat());
        Bounds lowered = renderBounds(minecraft, passthrough, state, current.lowered());
        return lowered.minY() - flat.minY();
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
                RandomSource.create(17L),
                state.getSeed(pos),
                0);
        if (consumer.vertices == 0) {
            throw new IllegalStateException("fallback_render_emitted_no_vertices");
        }
        return new Bounds(consumer.minY, consumer.maxY);
    }

    private static BoundedResult boundedViewCheck(BlockState state, BlockPos pos) {
        LongToIntFunction previous = SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(
                packed -> packed == pos.asLong()
                        ? -1
                        : SlabPlacementHeightAttachment.ABSENT_HALF_STEPS);
        try {
            OffsetBlockStateModel wrapper = new OffsetBlockStateModel(new EmptyModel());
            List<BakedQuad> first = renderInBoundedView(wrapper, RegionViewA.class, state, pos);
            List<BakedQuad> second = renderInBoundedView(wrapper, RegionViewB.class, state, pos);
            return new BoundedResult(first.size() == second.size());
        } catch (IndexOutOfBoundsException exception) {
            return new BoundedResult(false);
        } finally {
            SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(previous);
        }
    }

    private static <T extends BlockAndTintGetter> List<BakedQuad> renderInBoundedView(
            OffsetBlockStateModel wrapper,
            Class<T> viewType,
            BlockState state,
            BlockPos pos
    ) {
        T view = boundedView(viewType, state, pos, true);
        ModelData data = wrapper.getModelData(view, pos, state, ModelData.EMPTY);
        return wrapper.getQuads(state, null, RandomSource.create(23L), data, null);
    }

    private static AllocationResult allocationCheck(BlockState state, BlockPos pos) {
        OffsetBlockStateModel.resetFullMeshBoundsSample(null);
        OffsetBlockStateModel.resetModelDyOwnerSample(null);
        OffsetBlockStateModel.resetStepCullSample(null);
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) {
            return new AllocationResult(false, Long.MAX_VALUE);
        }
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        LongToIntFunction previous = SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(
                packed -> packed == pos.asLong()
                        ? -1
                        : SlabPlacementHeightAttachment.ABSENT_HALF_STEPS);
        try {
            OffsetBlockStateModel wrapper = new OffsetBlockStateModel(new EmptyModel());
            RegionViewA view = boundedView(RegionViewA.class, state, pos, false);
            ModelData data = wrapper.getModelData(view, pos, state, ModelData.EMPTY);
            RandomSource random = RandomSource.create(29L);
            for (int index = 0; index < 2_000; index++) {
                wrapper.getQuads(state, null, random, data, null);
            }
            long thread = Thread.currentThread().threadId();
            long before = bean.getThreadAllocatedBytes(thread);
            int calls = 10_000;
            for (int index = 0; index < calls; index++) {
                wrapper.getQuads(state, null, random, data, null);
            }
            long bytesPerCall = Math.max(0L, bean.getThreadAllocatedBytes(thread) - before) / calls;
            Slabbed.LOGGER.info(
                    "[P7_CLIENT_ALLOCATION] result={} calls={} bytesPerCall={} budgetBytesPerCall={}",
                    bytesPerCall <= ALLOCATION_BUDGET_BYTES_PER_CALL ? "GREEN" : "RED",
                    calls,
                    bytesPerCall,
                    ALLOCATION_BUDGET_BYTES_PER_CALL);
            return new AllocationResult(bytesPerCall <= ALLOCATION_BUDGET_BYTES_PER_CALL, bytesPerCall);
        } finally {
            SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(previous);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockAndTintGetter> T boundedView(
            Class<T> type,
            BlockState state,
            BlockPos pos,
            boolean rejectNeighbors
    ) {
        return (T) Proxy.newProxyInstance(
                P7ClientVisualProof.class.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> {
                    String name = method.getName();
                    if ("getBlockState".equals(name)) {
                        BlockPos queried = (BlockPos) arguments[0];
                        if (queried.equals(pos)) {
                            return state;
                        }
                        if (rejectNeighbors) {
                            throw new IndexOutOfBoundsException("bounded render region");
                        }
                        return Blocks.AIR.defaultBlockState();
                    }
                    if ("getFluidState".equals(name)) {
                        BlockPos queried = (BlockPos) arguments[0];
                        if (queried.equals(pos)) {
                            return state.getFluidState();
                        }
                        if (rejectNeighbors) {
                            throw new IndexOutOfBoundsException("bounded render region");
                        }
                        return Blocks.AIR.defaultBlockState().getFluidState();
                    }
                    if ("getBlockEntity".equals(name)) {
                        return null;
                    }
                    if ("getBlockTint".equals(name)) {
                        return 0xFFFFFF;
                    }
                    if ("getHeight".equals(name)) {
                        return 384;
                    }
                    if ("getMinBuildHeight".equals(name)) {
                        return -64;
                    }
                    if ("getShade".equals(name)) {
                        return 1.0f;
                    }
                    if ("toString".equals(name)) {
                        return type.getSimpleName();
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(name)) {
                        return proxy == arguments[0];
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    if (returnType == float.class) {
                        return 0.0f;
                    }
                    if (returnType == double.class) {
                        return 0.0d;
                    }
                    return null;
                });
    }

    private static void markRenderDirty(Minecraft minecraft, Fixture current) {
        minecraft.levelRenderer.setBlocksDirty(
                current.lowered().getX() - 1,
                current.lowered().getY() - 2,
                current.lowered().getZ() - 1,
                current.geometric().getX() + 1,
                current.geometric().getY() + 1,
                current.geometric().getZ() + 1);
    }

    private static String firstFailure(
            boolean numeric,
            boolean model,
            boolean singleOwner,
            boolean cull,
            boolean bounded,
            boolean allocation
    ) {
        if (!numeric) return "numeric_triad";
        if (!model) return "wrapped_model_or_mesh";
        if (!singleOwner) return "fallback_double_translation";
        if (!cull) return "height_cull";
        if (!bounded) return "bounded_render_view";
        if (!allocation) return "client_allocation";
        return "green";
    }

    private static boolean near(double actual, double expected) {
        return Double.isFinite(actual) && Math.abs(actual - expected) <= EPSILON;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static void fail(Minecraft minecraft, String reason) {
        if (terminal) {
            return;
        }
        terminal = true;
        write("p7-client.failed", reason + "\n");
        minecraft.stop();
    }

    private static void write(String name, String content) {
        try {
            Files.createDirectories(PROOF_DIRECTORY);
            Files.writeString(PROOF_DIRECTORY.resolve(name), content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write P7 proof receipt", exception);
        }
    }

    private interface RegionViewA extends BlockAndTintGetter {
    }

    private interface RegionViewB extends BlockAndTintGetter {
    }

    private record Fixture(BlockPos lowered, BlockPos flat, BlockPos geometric) {
    }

    private record Bounds(double minY, double maxY) {
    }

    private record BoundedResult(boolean green) {
    }

    private record GeometricResult(
            boolean numeric,
            boolean model,
            boolean screen,
            boolean factAbsent,
            double clientDy,
            double outlineMinY,
            boolean rayHit,
            String reason
    ) {
        boolean green() {
            return numeric && model && screen;
        }
    }

    private record AllocationResult(boolean green, long bytesPerCall) {
    }

    private record Result(
            boolean numeric,
            boolean model,
            boolean singleOwner,
            boolean cull,
            boolean bounded,
            boolean allocation,
            long allocationBytesPerCall,
            double fallbackShift,
            String reason
    ) {
        boolean green() {
            return numeric && model && singleOwner && cull && bounded && allocation;
        }
    }

    private static final class EmptyModel implements BakedModel {
        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction direction, RandomSource random) {
            return List.of();
        }

        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return false; }
        @Override public boolean usesBlockLight() { return false; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return null; }
        @Override public ItemOverrides getOverrides() { return null; }
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
