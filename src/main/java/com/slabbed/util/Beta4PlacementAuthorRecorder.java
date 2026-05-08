package com.slabbed.util;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class Beta4PlacementAuthorRecorder {
    private static boolean startLogged;

    private Beta4PlacementAuthorRecorder() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("slabbed.beta4PlacementAuthorRecorder");
    }

    public static void recordUseHead(Identifier blockItemId, boolean heldIsSlab, ItemUsageContext context) {
        if (!enabled() || context == null || context.getWorld() == null) {
            return;
        }
        World world = context.getWorld();
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
            ItemPlacementContext context,
            ActionResult result,
            String finalization
    ) {
        if (!enabled() || context == null || context.getWorld() == null) {
            return;
        }
        World world = context.getWorld();
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
            ItemPlacementContext context,
            ActionResult result,
            String finalization
    ) {
        if (!enabled() || context == null || context.getWorld() == null || context.getWorld().isClient()) {
            return;
        }
        World world = context.getWorld();
        if (world.getServer() == null) {
            return;
        }
        Direction clickedFace = context.getSide();
        BlockPos finalPlacedPos = context.getBlockPos();
        BlockPos clickedPos = finalPlacedPos.offset(clickedFace.getOpposite());
        Vec3d hitVec = context.getHitPos();
        PlayerEntity player = context.getPlayer();
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

    public static void logStartIfNeeded(World world) {
        if (!enabled() || startLogged) {
            return;
        }
        startLogged = true;
        Slabbed.LOGGER.info(
                "[BETA4_PLACEMENT_AUTHOR_RECORDER_START] enabled=true ticks={} watch={} world={}",
                intProperty("slabbed.beta4PlacementAuthorRecorderTicks", 200),
                System.getProperty("slabbed.beta4PlacementAuthorRecorderWatch", ""),
                world == null ? "null" : world.getRegistryKey().getValue());
    }

    private static void record(
            String marker,
            String phase,
            World world,
            Identifier blockItemId,
            boolean heldIsSlab,
            PlayerEntity player,
            BlockPos clickedPos,
            Direction clickedFace,
            Vec3d hitVec,
            BlockPos vanillaPlacePos,
            BlockPos finalPlacedPos,
            ActionResult result,
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
            World world,
            Identifier blockItemId,
            boolean heldIsSlab,
            String playerFacts,
            BlockPos clickedPos,
            Direction clickedFace,
            Vec3d hitVec,
            BlockPos vanillaPlacePos,
            BlockPos finalPlacedPos,
            ActionResult result,
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
    }

    private static String playerFacts(PlayerEntity player) {
        if (player == null) {
            return "player=null eye=null look=null";
        }
        return "playerPos=" + formatVec(new Vec3d(player.getX(), player.getY(), player.getZ()))
                + " eye=" + formatVec(player.getEyePos())
                + " look=" + formatVec(player.getRotationVec(1.0f));
    }

    private static String facts(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return "pos=null";
        }
        BlockState state = world.getBlockState(pos);
        return "pos=" + shortPos(pos)
                + " state=" + state
                + " dy=" + SlabSupport.getYOffset(world, pos, state)
                + " persistentFullBlockAnchor=" + persistentFullBlockAnchor(world, pos, state)
                + " persistentLoweredSlabCarrier=" + SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state)
                + " persistentLoweredBottomSlabCarrier="
                + SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state)
                + " slabType=" + slabType(state)
                + " sourceMode=" + sourceMode(world, pos, state);
    }

    private static String watchFacts(World world) {
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

    private static boolean persistentFullBlockAnchor(World world, BlockPos pos, BlockState state) {
        return SlabAnchorAttachment.isAnchored(world, pos)
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, pos, state);
    }

    private static String sourceMode(World world, BlockPos pos, BlockState state) {
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

    private static String formatVec(Vec3d vec) {
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
