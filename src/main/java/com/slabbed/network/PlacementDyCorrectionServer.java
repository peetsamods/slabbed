package com.slabbed.network;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ScaffoldingBlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-side bounded declaration buffer and one-response packet scope for C3. */
public final class PlacementDyCorrectionServer {
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };
    private static final Map<UUID, Map<PlacementDyPredictionBridge.GroupSignature,
            PlacementDyPredictionEnvelopePayload>> DECLARATIONS = new HashMap<>();
    private static final ThreadLocal<PacketScope> ACTIVE_SCOPE = new ThreadLocal<>();

    private PlacementDyCorrectionServer() {
    }

    private static final class PacketScope {
        final ServerPlayer player;
        final PlacementDyPredictionBridge.GroupSignature signature;
        final LinkedHashSet<Long> cells = new LinkedHashSet<>();
        boolean sent;

        PacketScope(ServerPlayer player, PlacementDyPredictionBridge.GroupSignature signature) {
            this.player = player;
            this.signature = signature;
        }

        void add(BlockPos pos) {
            if (pos != null && cells.size() < 32) {
                cells.add(pos.asLong());
            }
        }
    }

    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(
                PlacementDyPredictionEnvelopePayload.TYPE,
                (payload, context) -> buffer(context.player(), payload));
    }

    private static void buffer(ServerPlayer player, PlacementDyPredictionEnvelopePayload payload) {
        if (player == null || payload == null) {
            return;
        }
        PlacementDyPredictionBridge.GroupSignature signature = payload.signature();
        if (!player.level().dimension().toString().equals(signature.dimension())) {
            Slabbed.LOGGER.warn("[C3] dropping prediction envelope with mismatched dimension");
            return;
        }
        String currentDimension = player.level().dimension().toString();
        Map<PlacementDyPredictionBridge.GroupSignature, PlacementDyPredictionEnvelopePayload> playerEntries =
                DECLARATIONS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        playerEntries.entrySet().removeIf(entry ->
                isStaleDeclaration(entry.getKey(), currentDimension, signature.sequence()));
        if (playerEntries.size() >= 2 || playerEntries.putIfAbsent(signature, payload) != null) {
            Slabbed.LOGGER.warn("[C3] dropping duplicate/excess prediction envelope sequence={}", signature.sequence());
        }
    }

    public static void arm(ServerPlayer player, ServerboundUseItemOnPacket packet) {
        if (player == null || packet == null || ACTIVE_SCOPE.get() != null) {
            return;
        }
        ItemStack held = player.getItemInHand(packet.getHand());
        if (held == null || !(held.getItem() instanceof BlockItem)) {
            return;
        }
        BlockHitResult hit = packet.getHitResult();
        PlacementDyPredictionBridge.GroupSignature signature = new PlacementDyPredictionBridge.GroupSignature(
                player.level().dimension().toString(),
                packet.getSequence(),
                packet.getHand(),
                BuiltInRegistries.ITEM.getKey(held.getItem()).toString(),
                hit.getBlockPos().asLong(),
                hit.getDirection());
        PacketScope scope = new PacketScope(player, signature);
        Map<PlacementDyPredictionBridge.GroupSignature, PlacementDyPredictionEnvelopePayload> entries =
                DECLARATIONS.get(player.getUUID());
        PlacementDyPredictionEnvelopePayload declared = entries == null ? null : entries.remove(signature);
        BlockState clickedState = player.level().getBlockState(hit.getBlockPos());
        boolean clickedIsHeldScaffolding = held.getItem() instanceof ScaffoldingBlockItem scaffolding
                && clickedState.is(scaffolding.getBlock());
        addResponseCensus(
                player.level(),
                scope::add,
                hit.getBlockPos(),
                hit.getDirection(),
                held,
                clickedIsHeldScaffolding,
                player.isSecondaryUseActive(),
                hit.isInside(),
                player.getDirection(),
                declared == null ? List.of() : declared.positions());
        ACTIVE_SCOPE.set(scope);
    }

    public static void observe(BlockPos pos) {
        PacketScope scope = ACTIVE_SCOPE.get();
        if (scope != null) {
            scope.add(pos);
        }
    }

    public static void finishNormalReturn() {
        PacketScope scope = ACTIVE_SCOPE.get();
        if (scope == null || scope.sent || scope.cells.isEmpty()
                || !ServerPlayNetworking.canSend(scope.player, PlacementDyCorrectionPayload.TYPE)) {
            return;
        }
        ArrayList<PlacementDyCorrectionPayload.CellFact> facts = new ArrayList<>(scope.cells.size());
        for (long packed : scope.cells) {
            SlabAnchorAttachment.PlacementDyFact fact =
                    SlabAnchorAttachment.rawPlacementDyFact(scope.player.level(), BlockPos.of(packed));
            facts.add(new PlacementDyCorrectionPayload.CellFact(packed, fact.present(), fact.rawBits()));
        }
        ServerPlayNetworking.send(scope.player, new PlacementDyCorrectionPayload(scope.signature, facts));
        PlacementDyPredictionBridge.traceCorrectionWire("SEND", scope.signature);
        scope.sent = true;
    }

    public static void clearScope() {
        ACTIVE_SCOPE.remove();
    }

    public static void clearPlayer(ServerPlayer player) {
        if (player != null) {
            DECLARATIONS.remove(player.getUUID());
        }
    }

    private static void addLocalCensus(
            Consumer<BlockPos> sink,
            BlockPos clicked,
            Direction face,
            ItemStack held
    ) {
        BlockPos adjacent = clicked.relative(face);
        sink.accept(clicked);
        sink.accept(adjacent);
        for (BlockPos root : new BlockPos[]{clicked, adjacent}) {
            sink.accept(root.above());
            sink.accept(root.below());
            for (Direction direction : HORIZONTAL) {
                sink.accept(root.relative(direction));
            }
        }
    }

    private static void addBoundedGeometry(
            Level level,
            Consumer<BlockPos> sink,
            BlockPos clicked,
            Direction face,
            ItemStack held,
            boolean clickedIsHeldScaffolding,
            boolean secondaryUseActive,
            boolean insideHit,
            Direction horizontalDirection
    ) {
        addLocalCensus(sink, clicked, face, held);
        if (held.getItem() instanceof ScaffoldingBlockItem && clickedIsHeldScaffolding) {
            Direction routeDirection = scaffoldingRouteDirection(
                    face, secondaryUseActive, insideHit, horizontalDirection);
            if (routeDirection.getAxis().isHorizontal()) {
                for (int i = 1; i <= 7; i++) {
                    BlockPos routeCell = clicked.relative(routeDirection, i);
                    if (!level.isInWorldBounds(routeCell)) {
                        break;
                    }
                    addScaffoldingRouteEnvelope(level, sink, clicked, routeDirection, routeCell);
                }
            } else {
                BlockPos.MutableBlockPos routeCell = clicked.mutable().move(routeDirection);
                while (level.isInWorldBounds(routeCell)) {
                    addScaffoldingRouteEnvelope(
                            level, sink, clicked, routeDirection, routeCell.immutable());
                    routeCell.move(routeDirection);
                }
            }
        }
    }

    private static void addResponseCensus(
            Level level,
            Consumer<BlockPos> sink,
            BlockPos clicked,
            Direction face,
            ItemStack held,
            boolean clickedIsHeldScaffolding,
            boolean secondaryUseActive,
            boolean insideHit,
            Direction horizontalDirection,
            List<Long> declaredPositions
    ) {
        addLocalCensus(sink, clicked, face, held);
        for (long packed : declaredPositions) {
            BlockPos pos = BlockPos.of(packed);
            if (isWithinBoundedGeometry(
                    level,
                    clicked,
                    face,
                    held,
                    clickedIsHeldScaffolding,
                    secondaryUseActive,
                    insideHit,
                    horizontalDirection,
                    pos)) {
                sink.accept(pos);
            } else {
                Slabbed.LOGGER.warn("[C3] prediction envelope cell outside bounded geometry: {}", pos);
            }
        }
    }

    public static List<Long> responseCellCensusForTests(
            Level level,
            BlockPos clicked,
            Direction face,
            ItemStack held,
            boolean clickedIsHeldScaffolding,
            boolean secondaryUseActive,
            boolean insideHit,
            Direction horizontalDirection,
            List<Long> declaredPositions
    ) {
        LinkedHashSet<Long> cells = new LinkedHashSet<>();
        addResponseCensus(level, pos -> {
            if (cells.size() < 32) {
                cells.add(pos.asLong());
            }
        }, clicked, face, held, clickedIsHeldScaffolding, secondaryUseActive, insideHit,
                horizontalDirection, declaredPositions);
        return List.copyOf(cells);
    }

    public static List<PlacementDyPredictionBridge.GroupSignature> retainedDeclarationSignaturesForTests(
            List<PlacementDyPredictionBridge.GroupSignature> signatures,
            String currentDimension,
            int incomingSequence
    ) {
        return signatures.stream()
                .filter(signature -> !isStaleDeclaration(signature, currentDimension, incomingSequence))
                .toList();
    }

    private static boolean isWithinBoundedGeometry(
            Level level,
            BlockPos clicked,
            Direction face,
            ItemStack held,
            boolean clickedIsHeldScaffolding,
            boolean secondaryUseActive,
            boolean insideHit,
            Direction horizontalDirection,
            BlockPos candidate
    ) {
        LinkedHashSet<BlockPos> allowed = new LinkedHashSet<>();
        addBoundedGeometry(level, allowed::add, clicked, face, held, clickedIsHeldScaffolding,
                secondaryUseActive, insideHit, horizontalDirection);
        return allowed.contains(candidate);
    }

    private static void addScaffoldingRouteEnvelope(
            Level level,
            Consumer<BlockPos> sink,
            BlockPos clicked,
            Direction routeDirection,
            BlockPos routeCell
    ) {
        addWorldBoundedRouteCell(level, sink, clicked, routeDirection, routeCell);
        addWorldBoundedRouteCell(level, sink, clicked, routeDirection, routeCell.above());
        addWorldBoundedRouteCell(level, sink, clicked, routeDirection, routeCell.below());
        for (Direction direction : HORIZONTAL) {
            addWorldBoundedRouteCell(
                    level, sink, clicked, routeDirection, routeCell.relative(direction));
        }
    }

    private static void addWorldBoundedRouteCell(
            Level level,
            Consumer<BlockPos> sink,
            BlockPos clicked,
            Direction routeDirection,
            BlockPos candidate
    ) {
        if (!level.isInWorldBounds(candidate)) {
            return;
        }
        if (routeDirection.getAxis().isHorizontal()) {
            int progress = (candidate.getX() - clicked.getX()) * routeDirection.getStepX()
                    + (candidate.getZ() - clicked.getZ()) * routeDirection.getStepZ();
            if (progress < 1 || progress > 7) {
                return;
            }
        }
        sink.accept(candidate);
    }

    private static Direction scaffoldingRouteDirection(
            Direction face,
            boolean secondaryUseActive,
            boolean insideHit,
            Direction horizontalDirection
    ) {
        if (secondaryUseActive) {
            return insideHit ? face.getOpposite() : face;
        }
        return face == Direction.UP
                ? (horizontalDirection == null ? Direction.NORTH : horizontalDirection)
                : Direction.UP;
    }

    private static boolean isStaleDeclaration(
            PlacementDyPredictionBridge.GroupSignature signature,
            String currentDimension,
            int incomingSequence
    ) {
        return !currentDimension.equals(signature.dimension())
                || signature.sequence() < incomingSequence;
    }
}
