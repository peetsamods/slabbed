package com.slabbed.util;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

public final class Beta4PlacementAuthorRecorder {
    private static boolean startLogged;

    private Beta4PlacementAuthorRecorder() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("slabbed.beta4PlacementAuthorRecorder");
    }

    public static boolean compoundLivePathEnabled() {
        return enabled() || Boolean.getBoolean("slabbed.beta4CompoundLivePathRecorder");
    }

    public static void recordUseHead(Identifier blockItemId, boolean heldIsSlab, UseOnContext context) {
        if (!compoundLivePathEnabled() || context == null || context.getWorld() == null) {
            return;
        }
        Level world = context.getWorld();
        Direction clickedFace = context.getSide();
        BlockPos clickedPos = context.getBlockPos();
        BlockPos vanillaPlacePos = clickedPos.offset(clickedFace);
        record(
                "BETA4_PLACEMENT_AUTHOR_RECORDER",
                "useOnBlock-head",
                world,
                blockItemId,
                heldIsSlab,
                context.getPlayer(),
                clickedPos,
                clickedFace,
                context.getHitPos(),
                vanillaPlacePos,
                vanillaPlacePos,
                null,
                "anchorFinalization=not_yet_in_place");
    }

    public static void recordPlace(
            String phase,
            Identifier blockItemId,
            boolean heldIsSlab,
            BlockPlaceContext context,
            InteractionResult result,
            String finalization
    ) {
        if (!compoundLivePathEnabled() || context == null || context.getWorld() == null) {
            return;
        }
        Level world = context.getWorld();
        Direction clickedFace = context.getSide();
        BlockPos finalPlacedPos = context.getBlockPos();
        BlockPos clickedPos = finalPlacedPos.offset(clickedFace.getOpposite());
        record(
                "BETA4_PLACEMENT_AUTHOR_RECORDER",
                phase,
                world,
                blockItemId,
                heldIsSlab,
                context.getPlayer(),
                clickedPos,
                clickedFace,
                context.getHitPos(),
                finalPlacedPos,
                finalPlacedPos,
                result,
                finalization);
    }

    public static void recordAfterTick(
            Identifier blockItemId,
            boolean heldIsSlab,
            BlockPlaceContext context,
            InteractionResult result,
            String finalization
    ) {
        if (!compoundLivePathEnabled() || context == null || context.getWorld() == null || context.getWorld().isClient()) {
            return;
        }
        Level world = context.getWorld();
        if (world.getServer() == null) {
            return;
        }
        Direction clickedFace = context.getSide();
        BlockPos finalPlacedPos = context.getBlockPos();
        BlockPos clickedPos = finalPlacedPos.offset(clickedFace.getOpposite());
        Vec3 hitVec = context.getHitPos();
        Player player = context.getPlayer();
        String playerFacts = playerFacts(player);
        world.getServer().execute(() -> record(
                "BETA4_PLACEMENT_AUTHOR_AFTER_TICK",
                "server-after-queued-tick",
                world,
                blockItemId,
                heldIsSlab,
                playerFacts,
                clickedPos,
                clickedFace,
                hitVec,
                finalPlacedPos,
                finalPlacedPos,
                result,
                finalization));
    }

    public static void logStartIfNeeded(Level world) {
        if (!compoundLivePathEnabled() || startLogged) {
            return;
        }
        startLogged = true;
        Slabbed.LOGGER.info(
                "[BETA4_PLACEMENT_AUTHOR_RECORDER_START] enabled={} compoundLivePathEnabled={} ticks={} watch={} world={}",
                enabled(),
                compoundLivePathEnabled(),
                intProperty("slabbed.beta4PlacementAuthorRecorderTicks", 200),
                System.getProperty("slabbed.beta4PlacementAuthorRecorderWatch", ""),
                world == null ? "null" : world.getResourceKey().getValue());
    }

    public static void recordCompoundFinalization(
            String phase,
            Identifier blockItemId,
            boolean heldIsSlab,
            BlockPlaceContext context,
            InteractionResult result,
            String branch,
            BlockPos sourcePos,
            boolean compoundSidecarBefore,
            boolean compoundSidecarAfter,
            boolean persistentAnchorBefore,
            boolean persistentAnchorAfter,
            String reason
    ) {
        if (!compoundLivePathEnabled() || context == null || context.getWorld() == null) {
            return;
        }
        Level world = context.getWorld();
        logStartIfNeeded(world);
        BlockPos finalPlacedPos = context.getBlockPos();
        Direction clickedFace = context.getSide();
        BlockPos clickedPos = finalPlacedPos.offset(clickedFace.getOpposite());
        BlockState finalPlacedState = world.getBlockState(finalPlacedPos);
        Slabbed.LOGGER.info(
                "[BETA4_COMPOUND_FINALIZATION_PATH] phase={} side={} serverFinalizationRan={} heldItem={} heldIsSlab={} clickedPos={} clickedState={} clickedFace={} hitVec={} nativeBlockPos={} placePos={} finalPlacedPos={} finalPlacedState={} placementResult={} finalPlacedDy={} finalPlacedPersistentFullBlockAnchor={} finalPlacedCompoundFullBlockAnchor={} finalPlacedPersistentLoweredSlabCarrier={} finalPlacedSourceMode={} branch={} compoundSidecarBefore={} compoundSidecarAfter={} compoundSidecarAdded={} persistentAnchorBefore={} persistentAnchorAfter={} sourcePos={} sourceFacts={} supportBelowFacts={} aboveFacts={} reason={}",
                phase,
                world.isClient() ? "CLIENT" : "SERVER",
                !world.isClient(),
                blockItemId,
                heldIsSlab,
                shortPos(clickedPos),
                world.getBlockState(clickedPos),
                clickedFace,
                formatVec(context.getHitPos()),
                shortPos(clickedPos),
                shortPos(finalPlacedPos),
                shortPos(finalPlacedPos),
                finalPlacedState,
                result,
                SlabSupport.getYOffset(world, finalPlacedPos, finalPlacedState),
                persistentFullBlockAnchor(world, finalPlacedPos, finalPlacedState),
                compoundFullBlockAnchor(world, finalPlacedPos),
                SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, finalPlacedPos, finalPlacedState),
                sourceMode(world, finalPlacedPos, finalPlacedState),
                branch,
                compoundSidecarBefore,
                compoundSidecarAfter,
                !compoundSidecarBefore && compoundSidecarAfter,
                persistentAnchorBefore,
                persistentAnchorAfter,
                shortPos(sourcePos),
                facts(world, sourcePos),
                facts(world, finalPlacedPos.down()),
                facts(world, finalPlacedPos.up()),
                reason);
    }

    private static void record(
            String marker,
            String phase,
            Level world,
            Identifier blockItemId,
            boolean heldIsSlab,
            Player player,
            BlockPos clickedPos,
            Direction clickedFace,
            Vec3 hitVec,
            BlockPos vanillaPlacePos,
            BlockPos finalPlacedPos,
            InteractionResult result,
            String finalization
    ) {
        record(
                marker,
                phase,
                world,
                blockItemId,
                heldIsSlab,
                playerFacts(player),
                clickedPos,
                clickedFace,
                hitVec,
                vanillaPlacePos,
                finalPlacedPos,
                result,
                finalization);
    }

    private static void record(
            String marker,
            String phase,
            Level world,
            Identifier blockItemId,
            boolean heldIsSlab,
            String playerFacts,
            BlockPos clickedPos,
            Direction clickedFace,
            Vec3 hitVec,
            BlockPos vanillaPlacePos,
            BlockPos finalPlacedPos,
            InteractionResult result,
            String finalization
    ) {
        logStartIfNeeded(world);
        BlockState placedState = world.getBlockState(finalPlacedPos);
        Slabbed.LOGGER.info(
                "[{}] phase={} side={} heldItem={} heldIsSlab={} blockItem={} {} clickedPos={} clickedFace={} hitVec={} vanillaPlacePos={} finalPlacedPos={} finalPlacedState={} placementResult={} placedDy={} placedPersistentFullBlockAnchor={} placedSourceMode={} {} clickedFacts={} placedFacts={} below={} above={} north={} south={} east={} west={} watch={}",
                marker,
                phase,
                world.isClient() ? "CLIENT" : "SERVER",
                blockItemId,
                heldIsSlab,
                blockItemId,
                playerFacts,
                shortPos(clickedPos),
                clickedFace,
                formatVec(hitVec),
                shortPos(vanillaPlacePos),
                shortPos(finalPlacedPos),
                placedState,
                result,
                SlabSupport.getYOffset(world, finalPlacedPos, placedState),
                persistentFullBlockAnchor(world, finalPlacedPos, placedState),
                sourceMode(world, finalPlacedPos, placedState),
                finalization == null ? "anchorFinalization=unknown" : finalization,
                facts(world, clickedPos),
                facts(world, finalPlacedPos),
                facts(world, finalPlacedPos.down()),
                facts(world, finalPlacedPos.up()),
                facts(world, finalPlacedPos.north()),
                facts(world, finalPlacedPos.south()),
                facts(world, finalPlacedPos.east()),
                facts(world, finalPlacedPos.west()),
                watchFacts(world));
        if (compoundLivePathEnabled()) {
            Slabbed.LOGGER.info(
                    "[BETA4_COMPOUND_LIVE_PATH] phase={} side={} heldItem={} heldIsSlab={} clickedPos={} clickedState={} clickedFace={} hitVec={} nativeBlockPos={} targetDy={} targetPersistentFullBlockAnchor={} targetCompoundFullBlockAnchor={} targetPersistentLoweredSlabCarrier={} targetPersistentLoweredBottomSlabCarrier={} targetSourceMode={} placePos={} finalPlacedPos={} finalPlacedState={} placementResult={} finalPlacedDy={} finalPlacedPersistentFullBlockAnchor={} finalPlacedCompoundFullBlockAnchor={} finalPlacedPersistentLoweredSlabCarrier={} finalPlacedSourceMode={} afterOneTickQueued={} {} clickedFacts={} finalPlacedFacts={} sourceBelowFacts={} aboveFacts={} north={} south={} east={} west={} watch={}",
                    phase,
                    world.isClient() ? "CLIENT" : "SERVER",
                    blockItemId,
                    heldIsSlab,
                    shortPos(clickedPos),
                    world.getBlockState(clickedPos),
                    clickedFace,
                    formatVec(hitVec),
                    shortPos(clickedPos),
                    SlabSupport.getYOffset(world, clickedPos, world.getBlockState(clickedPos)),
                    persistentFullBlockAnchor(world, clickedPos, world.getBlockState(clickedPos)),
                    compoundFullBlockAnchor(world, clickedPos),
                    SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, clickedPos, world.getBlockState(clickedPos)),
                    SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, clickedPos, world.getBlockState(clickedPos)),
                    sourceMode(world, clickedPos, world.getBlockState(clickedPos)),
                    shortPos(vanillaPlacePos),
                    shortPos(finalPlacedPos),
                    placedState,
                    result,
                    SlabSupport.getYOffset(world, finalPlacedPos, placedState),
                    persistentFullBlockAnchor(world, finalPlacedPos, placedState),
                    compoundFullBlockAnchor(world, finalPlacedPos),
                    SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, finalPlacedPos, placedState),
                    sourceMode(world, finalPlacedPos, placedState),
                    "BETA4_PLACEMENT_AUTHOR_AFTER_TICK".equals(marker),
                    finalization == null ? "anchorFinalization=unknown" : finalization,
                    facts(world, clickedPos),
                    facts(world, finalPlacedPos),
                    facts(world, finalPlacedPos.down()),
                    facts(world, finalPlacedPos.up()),
                    facts(world, finalPlacedPos.north()),
                    facts(world, finalPlacedPos.south()),
                    facts(world, finalPlacedPos.east()),
                    facts(world, finalPlacedPos.west()),
                    watchFacts(world));
        }
    }

    private static String playerFacts(Player player) {
        if (player == null) {
            return "player=null eye=null look=null";
        }
        return "playerPos=" + formatVec(new Vec3(player.getX(), player.getY(), player.getZ()))
                + " eye=" + formatVec(player.getEyePos())
                + " look=" + formatVec(player.getRotationVec(1.0f));
    }

    private static String facts(Level world, BlockPos pos) {
        if (world == null || pos == null) {
            return "pos=null";
        }
        BlockState state = world.getBlockState(pos);
        return "pos=" + shortPos(pos)
                + " state=" + state
                + " dy=" + SlabSupport.getYOffset(world, pos, state)
                + " persistentFullBlockAnchor=" + persistentFullBlockAnchor(world, pos, state)
                + " compoundFullBlockAnchor=" + compoundFullBlockAnchor(world, pos)
                + " persistentLoweredSlabCarrier=" + SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)
                + " persistentLoweredBottomSlabCarrier="
                + SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state)
                + " slabType=" + slabType(state)
                + " sourceMode=" + sourceMode(world, pos, state);
    }

    private static String watchFacts(Level world) {
        String raw = System.getProperty("slabbed.beta4PlacementAuthorRecorderWatch", "");
        if (raw.isBlank()) {
            return "none";
        }
        StringBuilder out = new StringBuilder();
        String[] parts = raw.split(";");
        for (String part : parts) {
            BlockPos pos = parsePos(part);
            if (pos == null) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append("|");
            }
            out.append(facts(world, pos));
        }
        return out.isEmpty() ? "none" : out.toString();
    }

    private static BlockPos parsePos(String raw) {
        String[] pieces = raw.trim().split(",");
        if (pieces.length != 3) {
            return null;
        }
        try {
            return new BlockPos(
                    Integer.parseInt(pieces[0].trim()),
                    Integer.parseInt(pieces[1].trim()),
                    Integer.parseInt(pieces[2].trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean persistentFullBlockAnchor(Level world, BlockPos pos, BlockState state) {
        return SlabAnchorAttachment.isAnchored(world, pos)
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, pos, state);
    }

    private static boolean compoundFullBlockAnchor(Level world, BlockPos pos) {
        return world != null && pos != null && SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos);
    }

    private static String sourceMode(Level world, BlockPos pos, BlockState state) {
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)) {
            return "persistentLoweredSlabCarrier";
        }
        if (SlabAnchorAttachment.isAnchored(world, pos)
                || Math.abs(SlabSupport.getYOffset(world, pos, state) + 0.5d) <= 1.0e-6d) {
            return "dynamicLoweredOrAnchored";
        }
        return "normal";
    }

    private static String slabType(BlockState state) {
        return state.contains(SlabBlock.TYPE) ? state.get(SlabBlock.TYPE).asString() : "none";
    }

    private static String shortPos(BlockPos pos) {
        return pos == null ? "null" : pos.toShortString();
    }

    private static String formatVec(Vec3 vec) {
        if (vec == null) {
            return "null";
        }
        return String.format("%.3f,%.3f,%.3f", vec.x, vec.y, vec.z);
    }

    private static int intProperty(String key, int fallback) {
        try {
            return Integer.parseInt(System.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
