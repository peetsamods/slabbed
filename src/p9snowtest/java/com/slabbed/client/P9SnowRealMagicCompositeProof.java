package com.slabbed.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.platform.NativeImage;
import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.client.model.OffsetBlockStateModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Isolated integration proof for Snow! Real Magic composite slab ownership. */
public final class P9SnowRealMagicCompositeProof {
    private static final String PROPERTY = "slabbed.p9.snow.proof";
    private static final String WORLD_NAME = "p9-snow-proof";
    private static final Path PROOF_DIRECTORY = Path.of("proof");
    private static final ResourceLocation COMPOSITE_ID =
            ResourceLocation.fromNamespaceAndPath("snowrealmagic", "slab");
    private static final double EPSILON = 1.0e-6d;
    private static final int MAX_TICKS = 2_400;

    private static boolean registered;
    private static boolean worldQueued;
    private static boolean fixtureQueued;
    private static boolean removalQueued;
    private static RemovalStage removalStage = RemovalStage.TOP;
    private static boolean removalAttempted;
    private static boolean removalAccepted;
    private static int removalTicks;
    private static int snowballsBeforeRemoval;
    private static boolean topSnowballReceived;
    private static boolean sideSnowballReceived;
    private static boolean terminal;
    private static int ticks;
    private static volatile Fixture fixture;
    private static volatile Removal removal;
    private static volatile String serverFailure;
    private static PreRemoval preRemoval;
    private static CaptureStage captureStage = CaptureStage.CONTROL;
    private static int captureStageTicks;
    private static int[] controlPixels;
    private static int[] topPixels;
    private static boolean visibilityComplete;

    private P9SnowRealMagicCompositeProof() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        if (!Boolean.getBoolean(PROPERTY)) {
            throw new IllegalStateException("The P9 Snow client proof requires its launch property");
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(P9SnowRealMagicCompositeProof::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (terminal) {
            return;
        }
        ticks++;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.pauseOnLostFocus = false;
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
        if (current == null) {
            return;
        }
        if (preRemoval == null && !clientFixtureReady(minecraft, current)) {
            return;
        }

        try {
            if (preRemoval == null) {
                preRemoval = evaluateBeforeRemoval(minecraft, current);
                if (!preRemoval.preVisibilityGreen()) {
                    log(preRemoval, false, preRemoval.reason());
                    fail(minecraft, preRemoval.reason());
                    return;
                }
            }
            if (!visibilityComplete) {
                Visibility visibility = advanceVisibilityProof(minecraft, current);
                if (visibility == null) {
                    return;
                }
                visibilityComplete = true;
                preRemoval = preRemoval.withVisibility(visibility);
                Slabbed.LOGGER.info(
                        "[P9_SNOW_STAGE] stage=visibility visible={} topChangedPixels={} sideChangedPixels={}",
                        visibility.visible(),
                        visibility.topChangedPixels(),
                        visibility.sideChangedPixels());
                if (!preRemoval.green()) {
                    log(preRemoval, false, preRemoval.reason());
                    fail(minecraft, preRemoval.reason());
                    return;
                }
            }
            Removal completedRemoval = advanceRemoval(minecraft, current);
            if (completedRemoval == null) {
                return;
            }

            boolean removalGreen = completedRemoval.topRemoved()
                    && completedRemoval.sideRemoved()
                    && completedRemoval.topRestored()
                    && completedRemoval.sideRestored()
                    && completedRemoval.sentinelsPreserved()
                    && completedRemoval.snowballsReceived()
                    && isBlock(minecraft.level.getBlockState(current.top().support()), Blocks.STONE_SLAB)
                    && isBlock(minecraft.level.getBlockState(current.side().support()), Blocks.STONE_SLAB)
                    && isBlock(minecraft.level.getBlockState(current.control()), Blocks.STONE_SLAB)
                    && isBlock(minecraft.level.getBlockState(current.top().sentinel()), Blocks.GOLD_BLOCK)
                    && isBlock(minecraft.level.getBlockState(current.side().sentinel()), Blocks.GOLD_BLOCK);
            String reason = removalGreen ? "green" : "removal_contract";
            log(preRemoval, removalGreen, reason);
            if (!removalGreen) {
                fail(minecraft, reason);
                return;
            }
            write("p9-snow.ok",
                    "versions=true registry=true top=true side=true identity=true visibility=true removal=true ordinary=true\n");
            Slabbed.LOGGER.info("[P9_SNOW_STAGE] stage=terminal result=green");
            terminal = true;
            minecraft.stop();
        } catch (RuntimeException exception) {
            Slabbed.LOGGER.error("P9 Snow composite proof raised an exception", exception);
            fail(minecraft, "exception_" + exception.getClass().getSimpleName());
        }
    }

    private static void maybeOpenWorld(Minecraft minecraft) {
        if (worldQueued || ticks < 40 || !minecraft.isGameLoadFinished()) {
            return;
        }
        worldQueued = true;
        LevelSettings settings = new LevelSettings(
                "P9 Snow Composite Proof",
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
                P9SnowRealMagicCompositeProof::flatDimensions,
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
                BlockPos origin = player.blockPosition().offset(4, 4, 0);
                clear(world, origin);

                BlockPos topPos = origin.immutable();
                BlockPos sidePos = origin.offset(4, 0, 0);
                BlockPos controlPos = origin.offset(8, 0, 0);
                BlockPos ordinarySupport = origin.offset(12, 0, 0);
                prepareSlab(world, topPos);
                prepareSlab(world, sidePos);
                prepareSlab(world, controlPos);
                prepareSlab(world, ordinarySupport);

                SnowPlacement top = placeSnow(world, player, topPos, Direction.UP);
                SnowPlacement side = placeSnow(world, player, sidePos, Direction.EAST);
                OrdinaryPlacement ordinary = placeGrass(world, player, ordinarySupport);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setShiftKeyDown(true);
                player.teleportTo(
                        world,
                        origin.getX() + 2.5d,
                        origin.getY() + 1.0d,
                        origin.getZ() + 3.5d,
                        180.0f,
                        20.0f);
                fixture = new Fixture(
                        top,
                        side,
                        controlPos,
                        ordinary,
                        version("snowrealmagic").startsWith("12.2.2"),
                        version("kiwi").startsWith("15.8.7"),
                        version("sodium").startsWith("0.6.13"));
            } catch (RuntimeException exception) {
                serverFailure = exception.getClass().getSimpleName() + "_" + safeMessage(exception);
            }
        });
    }

    private static void clear(ServerLevel world, BlockPos origin) {
        for (int dx = -2; dx <= 14; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 3; dy++) {
                    world.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static void prepareSlab(ServerLevel world, BlockPos pos) {
        world.setBlock(pos.below(), Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(pos, Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
    }

    private static SnowPlacement placeSnow(
            ServerLevel world,
            ServerPlayer player,
            BlockPos support,
            Direction face
    ) {
        if (face == Direction.UP) {
            player.setPos(support.getX() + 0.5d, support.getY() + 2.0d, support.getZ() + 2.5d);
        } else {
            player.setPos(support.getX() + 2.5d, support.getY() + 1.0d, support.getZ() + 0.5d);
        }
        ItemStack selected = new ItemStack(Blocks.SNOW, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, selected);
        AtomicInteger placeEvents = new AtomicInteger();
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getLevel() == world && event.getPos().equals(support)) {
                placeEvents.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
        InteractionResult interaction;
        try {
            Vec3 location = face == Direction.UP
                    ? new Vec3(support.getX() + 0.5d, support.getY() + 0.5d, support.getZ() + 0.5d)
                    : new Vec3(support.getX() + 1.0d, support.getY() + 0.49d, support.getZ() + 0.5d);
            BlockHitResult hit = new BlockHitResult(location, face, support, false);
            interaction = selected.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        return new SnowPlacement(
                support,
                support.below(),
                interaction.consumesAction(),
                selected.getCount() == 1,
                placeEvents.get() == 1,
                registryId(world.getBlockState(support)));
    }

    private static OrdinaryPlacement placeGrass(
            ServerLevel world,
            ServerPlayer player,
            BlockPos support
    ) {
        BlockPos subject = support.above();
        player.setPos(support.getX() + 0.5d, support.getY() + 2.0d, support.getZ() + 0.5d);
        ItemStack selected = new ItemStack(Blocks.GRASS_BLOCK, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, selected);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(support).add(0.0d, 0.5d, 0.0d),
                Direction.UP,
                support,
                false);
        InteractionResult interaction = selected.useOn(
                new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        int stored = SlabPlacementHeightAttachment.storedHalfSteps(world.getChunkAt(subject), subject)
                .orElse(Integer.MIN_VALUE);
        return new OrdinaryPlacement(
                support,
                subject,
                interaction.consumesAction(),
                selected.getCount() == 1,
                world.getBlockState(subject).is(Blocks.GRASS_BLOCK),
                stored);
    }

    private static String version(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("missing");
    }

    private static boolean clientFixtureReady(Minecraft minecraft, Fixture current) {
        return COMPOSITE_ID.equals(registryId(minecraft.level.getBlockState(current.top().support())))
                && COMPOSITE_ID.equals(registryId(minecraft.level.getBlockState(current.side().support())))
                && minecraft.level.getBlockState(current.ordinary().subject()).is(Blocks.GRASS_BLOCK)
                && minecraft.player.getMainHandItem().isEmpty()
                && near(SlabPlacementHeightAttachment.storedOffset(
                        minecraft.level, current.ordinary().subject()), -0.5d);
    }

    private static PreRemoval evaluateBeforeRemoval(Minecraft minecraft, Fixture current) {
        BlockState topState = minecraft.level.getBlockState(current.top().support());
        BlockState sideState = minecraft.level.getBlockState(current.side().support());
        BakedModel topModel = minecraft.getBlockRenderer().getBlockModel(topState);
        BakedModel sideModel = minecraft.getBlockRenderer().getBlockModel(sideState);
        boolean versions = current.snowVersion() && current.kiwiVersion() && current.sodiumVersion();
        boolean registry = COMPOSITE_ID.equals(current.top().resultId())
                && COMPOSITE_ID.equals(current.side().resultId())
                && COMPOSITE_ID.equals(registryId(topState))
                && COMPOSITE_ID.equals(registryId(sideState));
        boolean top = snowPlacementGreen(current.top())
                && isBlock(minecraft.level.getBlockState(current.top().sentinel()), Blocks.GOLD_BLOCK);
        boolean side = snowPlacementGreen(current.side())
                && isBlock(minecraft.level.getBlockState(current.side().sentinel()), Blocks.GOLD_BLOCK);

        ModelSignature topSignature = signature(topModel);
        ModelSignature sideSignature = signature(sideModel);
        boolean identity = !(topModel instanceof OffsetBlockStateModel)
                && !(sideModel instanceof OffsetBlockStateModel)
                && isExternalEmitter(topModel)
                && isExternalEmitter(sideModel)
                && SlabbedModelLoadingPlugin.wrapModel(
                        ModelResourceLocation.standalone(COMPOSITE_ID), topModel) == topModel
                && SlabbedModelLoadingPlugin.wrapModel(
                        ModelResourceLocation.standalone(COMPOSITE_ID), sideModel) == sideModel
                && topSignature.equals(sideSignature);

        boolean ordinary = ordinaryGreen(minecraft, current);
        String reason = firstFailure(versions, registry, top, side, identity, true, ordinary);
        return new PreRemoval(
                versions,
                registry,
                top,
                side,
                identity,
                false,
                ordinary,
                topSignature,
                0,
                0,
                reason);
    }

    private static boolean snowPlacementGreen(SnowPlacement placement) {
        return placement.accepted()
                && placement.consumedOne()
                && placement.singlePlaceEvent()
                && COMPOSITE_ID.equals(placement.resultId());
    }

    private static boolean ordinaryGreen(Minecraft minecraft, Fixture current) {
        OrdinaryPlacement ordinary = current.ordinary();
        BlockState subject = minecraft.level.getBlockState(ordinary.subject());
        BlockState control = minecraft.level.getBlockState(current.control());
        if (!ordinary.accepted()
                || !ordinary.consumedOne()
                || !ordinary.placedGrass()
                || ordinary.storedHalfSteps() != -1
                || !subject.is(Blocks.GRASS_BLOCK)
                || !control.is(Blocks.STONE_SLAB)) {
            return false;
        }
        double dy = ClientDy.dyFor(minecraft.level, ordinary.subject(), subject);
        Bounds controlBounds = renderBounds(
                minecraft,
                minecraft.getBlockRenderer().getBlockModel(control),
                control,
                current.control());
        double renderShift = fallbackRenderShift(minecraft, subject, ordinary.subject(), current.control());
        return near(dy, -0.5d) && controlBounds.visible() && near(renderShift, -0.5d);
    }

    private static double fallbackRenderShift(
            Minecraft minecraft,
            BlockState state,
            BlockPos lowered,
            BlockPos flat
    ) {
        BakedModel renderedModel = minecraft.getBlockRenderer().getBlockModel(state);
        BakedModel passthrough = new PassthroughModel(renderedModel);
        Bounds flatBounds = renderBounds(minecraft, passthrough, state, flat);
        Bounds loweredBounds = renderBounds(minecraft, passthrough, state, lowered);
        return loweredBounds.minY() - flatBounds.minY();
    }

    private static Removal advanceRemoval(Minecraft minecraft, Fixture current) {
        Removal completed = removal;
        if (completed != null) {
            if (removalStage == RemovalStage.VALIDATE
                    && (!completed.topRestored()
                    || !completed.sideRestored()
                    || !completed.snowballsReceived())
                    && removalTicks++ < 40) {
                removal = null;
                removalQueued = false;
                return null;
            }
            return completed;
        }
        if (removalStage == RemovalStage.VALIDATE) {
            if (!removalQueued) {
                queueRemovalValidation(minecraft, current);
            }
            return null;
        }
        SnowPlacement placement = removalStage == RemovalStage.TOP ? current.top() : current.side();
        BlockPos target = placement.support();
        int currentSnowballs = minecraft.player.getInventory().countItem(Items.SNOWBALL);
        if (isBlock(minecraft.level.getBlockState(target), Blocks.STONE_SLAB)
                && currentSnowballs == snowballsBeforeRemoval + 1) {
            Slabbed.LOGGER.info(
                    "[P9_SNOW_STAGE] stage={} result=restored snowballs={}",
                    removalStage.name().toLowerCase(),
                    currentSnowballs);
            if (removalStage == RemovalStage.TOP) {
                topSnowballReceived = true;
                removalStage = RemovalStage.SIDE;
                selectEmptyHotbarSlot(minecraft);
                removalAttempted = false;
                removalAccepted = false;
                removalTicks = 0;
                return null;
            }
            sideSnowballReceived = true;
            removalStage = RemovalStage.VALIDATE;
            removalAttempted = false;
            removalTicks = 0;
            return null;
        }
        if (!removalAttempted) {
            if (!minecraft.player.getMainHandItem().isEmpty()) {
                return null;
            }
            minecraft.player.setShiftKeyDown(true);
            snowballsBeforeRemoval = currentSnowballs;
            BlockHitResult hit = new BlockHitResult(
                    new Vec3(target.getX() + 0.5d, target.getY() + 1.0d, target.getZ() + 0.5d),
                    Direction.UP,
                    target,
                    false);
            removalAccepted = minecraft.gameMode.useItemOn(
                    minecraft.player,
                    InteractionHand.MAIN_HAND,
                    hit).consumesAction();
            Slabbed.LOGGER.info(
                    "[P9_SNOW_STAGE] stage={} action=sneak_use accepted={} beforeSnowballs={}",
                    removalStage.name().toLowerCase(),
                    removalAccepted,
                    snowballsBeforeRemoval);
            removalAttempted = true;
            removalTicks = 0;
            return null;
        }
        removalTicks++;
        if (removalTicks < 40) {
            return null;
        }
        Slabbed.LOGGER.info(
                "[P9_SNOW_STAGE] stage={} result=timeout accepted={} state={} snowballs={} shift={}",
                removalStage.name().toLowerCase(),
                removalAccepted,
                minecraft.level.getBlockState(target),
                currentSnowballs,
                minecraft.player.isShiftKeyDown());
        return new Removal(
                topSnowballReceived,
                false,
                isBlock(minecraft.level.getBlockState(current.top().support()), Blocks.STONE_SLAB),
                isBlock(minecraft.level.getBlockState(current.side().support()), Blocks.STONE_SLAB),
                isBlock(minecraft.level.getBlockState(current.top().sentinel()), Blocks.GOLD_BLOCK)
                        && isBlock(minecraft.level.getBlockState(current.side().sentinel()), Blocks.GOLD_BLOCK),
                topSnowballReceived && sideSnowballReceived && removalAccepted);
    }

    private static void selectEmptyHotbarSlot(Minecraft minecraft) {
        for (int slot = 0; slot < 9; slot++) {
            if (!minecraft.player.getInventory().getItem(slot).isEmpty()) {
                continue;
            }
            minecraft.player.getInventory().selected = slot;
            minecraft.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
            return;
        }
        throw new IllegalStateException("empty_hotbar_slot_missing");
    }

    private static void queueRemovalValidation(Minecraft minecraft, Fixture current) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        removalQueued = true;
        server.execute(() -> {
            ServerLevel world = server.overworld();
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            boolean snowballsReceived = !players.isEmpty()
                    && players.getFirst().getInventory().countItem(Items.SNOWBALL) == 2;
            Slabbed.LOGGER.info(
                    "[P9_SNOW_STAGE] stage=server_validation top={} side={} snowballs={}",
                    registryId(world.getBlockState(current.top().support())),
                    registryId(world.getBlockState(current.side().support())),
                    players.isEmpty() ? -1 : players.getFirst().getInventory().countItem(Items.SNOWBALL));
            removal = new Removal(
                    topSnowballReceived,
                    sideSnowballReceived,
                    isBlock(world.getBlockState(current.top().support()), Blocks.STONE_SLAB),
                    isBlock(world.getBlockState(current.side().support()), Blocks.STONE_SLAB),
                    isBlock(world.getBlockState(current.top().sentinel()), Blocks.GOLD_BLOCK)
                            && isBlock(world.getBlockState(current.side().sentinel()), Blocks.GOLD_BLOCK),
                    snowballsReceived);
        });
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
                RandomSource.create(41L),
                state.getSeed(pos),
                0);
        return new Bounds(consumer.minY, consumer.maxY, consumer.vertices);
    }

    private static boolean isExternalEmitter(BakedModel model) {
        return model instanceof FabricBakedModel fabricModel && !fabricModel.isVanillaAdapter();
    }

    private static Visibility advanceVisibilityProof(Minecraft minecraft, Fixture current) {
        BlockPos target = switch (captureStage) {
            case CONTROL -> current.control();
            case TOP -> current.top().support();
            case SIDE -> current.side().support();
        };
        positionCamera(minecraft, target);
        if (captureStageTicks++ == 0) {
            minecraft.levelRenderer.setBlocksDirty(
                    target.getX() - 1,
                    target.getY() - 1,
                    target.getZ() - 1,
                    target.getX() + 1,
                    target.getY() + 1,
                    target.getZ() + 1);
        }
        if (captureStageTicks < 20) {
            return null;
        }

        int[] pixels = captureCenter(minecraft, captureStage.name().toLowerCase());
        captureStageTicks = 0;
        if (captureStage == CaptureStage.CONTROL) {
            controlPixels = pixels;
            captureStage = CaptureStage.TOP;
            return null;
        }
        if (captureStage == CaptureStage.TOP) {
            topPixels = pixels;
            captureStage = CaptureStage.SIDE;
            return null;
        }

        PixelDelta topDelta = pixelDelta(controlPixels, topPixels);
        PixelDelta sideDelta = pixelDelta(controlPixels, pixels);
        int minimumChanged = Math.max(64, controlPixels.length / 100);
        boolean visible = topDelta.changedPixels() >= minimumChanged
                && sideDelta.changedPixels() >= minimumChanged
                && topDelta.meanChannelDelta() >= 1.0d
                && sideDelta.meanChannelDelta() >= 1.0d;
        return new Visibility(visible, topDelta.changedPixels(), sideDelta.changedPixels());
    }

    private static void positionCamera(Minecraft minecraft, BlockPos target) {
        minecraft.options.hideGui = true;
        minecraft.setScreen(null);
        minecraft.setCameraEntity(minecraft.player);
        minecraft.player.setPos(
                target.getX() + 0.5d,
                target.getY(),
                target.getZ() + 3.5d);
        minecraft.player.setYRot(180.0f);
        minecraft.player.yRotO = 180.0f;
        minecraft.player.setYHeadRot(180.0f);
        minecraft.player.yHeadRotO = 180.0f;
        minecraft.player.setXRot(20.0f);
        minecraft.player.xRotO = 20.0f;
    }

    private static int[] captureCenter(Minecraft minecraft, String name) {
        try {
            Files.createDirectories(PROOF_DIRECTORY);
            try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                image.writeToFile(PROOF_DIRECTORY.resolve(name + ".png"));
                int size = Math.min(192, Math.min(image.getWidth(), image.getHeight()) / 2);
                int startX = (image.getWidth() - size) / 2;
                int startY = (image.getHeight() - size) / 2;
                int[] pixels = new int[size * size];
                int index = 0;
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        pixels[index++] = image.getPixelRGBA(startX + x, startY + y);
                    }
                }
                return pixels;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not capture the P9 Snow render frame", exception);
        }
    }

    private static PixelDelta pixelDelta(int[] baseline, int[] candidate) {
        if (baseline == null || candidate == null || baseline.length != candidate.length) {
            return new PixelDelta(0, 0.0d);
        }
        int changed = 0;
        long totalChannelDelta = 0L;
        for (int index = 0; index < baseline.length; index++) {
            int left = baseline[index];
            int right = candidate[index];
            int red = Math.abs((left & 0xFF) - (right & 0xFF));
            int green = Math.abs(((left >>> 8) & 0xFF) - ((right >>> 8) & 0xFF));
            int blue = Math.abs(((left >>> 16) & 0xFF) - ((right >>> 16) & 0xFF));
            if (Math.max(red, Math.max(green, blue)) >= 12) {
                changed++;
            }
            totalChannelDelta += red + green + blue;
        }
        double mean = totalChannelDelta / (double) (baseline.length * 3L);
        return new PixelDelta(changed, mean);
    }

    private static ModelSignature signature(BakedModel model) {
        return new ModelSignature(
                model.getClass().getName(),
                model.isCustomRenderer(),
                model instanceof IDynamicBakedModel,
                model instanceof BakedModelWrapper<?>);
    }

    private static ResourceLocation registryId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static boolean isBlock(BlockState state, Block block) {
        return state.is(block);
    }

    private static String firstFailure(
            boolean versions,
            boolean registry,
            boolean top,
            boolean side,
            boolean identity,
            boolean visibility,
            boolean ordinary
    ) {
        if (!versions) return "dependency_identity";
        if (!registry) return "composite_registry";
        if (!top) return "top_placement";
        if (!side) return "side_placement";
        if (!identity) return "composite_model_identity";
        if (!visibility) return "composite_renderer_emission";
        if (!ordinary) return "ordinary_control";
        return "green";
    }

    private static boolean near(double actual, double expected) {
        return Double.isFinite(actual) && Math.abs(actual - expected) <= EPSILON;
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null ? "no_message" : message.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static void log(PreRemoval result, boolean removalGreen, String reason) {
        Slabbed.LOGGER.info(
                "[P9_SNOW_COMPOSITE_PROOF] result={} versions={} registry={} top={} side={} identity={} visibility={} removal={} ordinary={} custom={} dynamic={} foreignWrapper={} topChangedPixels={} sideChangedPixels={} reason={}",
                result.green() && removalGreen ? "GREEN" : "RED",
                result.versions(),
                result.registry(),
                result.top(),
                result.side(),
                result.identity(),
                result.visibility(),
                removalGreen,
                result.ordinary(),
                result.signature().customRenderer(),
                result.signature().dynamic(),
                result.signature().foreignWrapper(),
                result.topChangedPixels(),
                result.sideChangedPixels(),
                reason);
    }

    private static void fail(Minecraft minecraft, String reason) {
        if (terminal) {
            return;
        }
        terminal = true;
        write("p9-snow.failed", reason + "\n");
        minecraft.stop();
    }

    private static void write(String name, String content) {
        try {
            Files.createDirectories(PROOF_DIRECTORY);
            Files.writeString(PROOF_DIRECTORY.resolve(name), content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write P9 Snow proof receipt", exception);
        }
    }

    private record SnowPlacement(
            BlockPos support,
            BlockPos sentinel,
            boolean accepted,
            boolean consumedOne,
            boolean singlePlaceEvent,
            ResourceLocation resultId
    ) {
    }

    private record OrdinaryPlacement(
            BlockPos support,
            BlockPos subject,
            boolean accepted,
            boolean consumedOne,
            boolean placedGrass,
            int storedHalfSteps
    ) {
    }

    private record Fixture(
            SnowPlacement top,
            SnowPlacement side,
            BlockPos control,
            OrdinaryPlacement ordinary,
            boolean snowVersion,
            boolean kiwiVersion,
            boolean sodiumVersion
    ) {
    }

    private record ModelSignature(
            String className,
            boolean customRenderer,
            boolean dynamic,
            boolean foreignWrapper
    ) {
    }

    private record Bounds(double minY, double maxY, int vertices) {
        boolean visible() {
            return vertices > 0 && Double.isFinite(minY) && Double.isFinite(maxY) && minY <= maxY;
        }
    }

    private record PreRemoval(
            boolean versions,
            boolean registry,
            boolean top,
            boolean side,
            boolean identity,
            boolean visibility,
            boolean ordinary,
            ModelSignature signature,
            int topChangedPixels,
            int sideChangedPixels,
            String reason
    ) {
        boolean preVisibilityGreen() {
            return versions && registry && top && side && identity && ordinary;
        }

        boolean green() {
            return versions && registry && top && side && identity && visibility && ordinary;
        }

        PreRemoval withVisibility(Visibility proof) {
            return new PreRemoval(
                    versions,
                    registry,
                    top,
                    side,
                    identity,
                    proof.visible(),
                    ordinary,
                    signature,
                    proof.topChangedPixels(),
                    proof.sideChangedPixels(),
                    proof.visible() ? "green" : "composite_frame_visibility");
        }
    }

    private enum CaptureStage {
        CONTROL,
        TOP,
        SIDE
    }

    private enum RemovalStage {
        TOP,
        SIDE,
        VALIDATE
    }

    private record PixelDelta(int changedPixels, double meanChannelDelta) {
    }

    private record Visibility(boolean visible, int topChangedPixels, int sideChangedPixels) {
    }

    private record Removal(
            boolean topRemoved,
            boolean sideRemoved,
            boolean topRestored,
            boolean sideRestored,
            boolean sentinelsPreserved,
            boolean snowballsReceived
    ) {
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
