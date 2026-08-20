package com.slabbed.client;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.model.OffsetBlockStateModel;
import com.slabbed.util.SlabModelStaleSentinel;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.lang.reflect.Proxy;
import java.util.Map;

/** Pure pins for sentinel throttling and the C5 canonical client dy; no live world is needed. */
public final class SlabModelStaleSentinelClientThrottleClientGameTest implements FabricClientGameTest {

    /**
     * Positive execution evidence. The client suite has no per-entrypoint count gate the way the server
     * suite does, so an entrypoint that never runs is indistinguishable from one that passed: the task
     * simply reports success. Every client entrypoint emits this on its success path, and a green run is
     * proof only when the log carries one line per {@code fabric-client-gametest} entry.
     */
    private static final String CLIENT_GAMETEST_PASS = "CLIENT_GAMETEST | SlabModelStaleSentinelClientThrottleClientGameTest | PASS";
    @Override
    public void runTest(ClientGameTestContext ctx) {
        String red = SlabModelStaleSentinel.KIND_DIVERGENT;
        expect(true, SlabModelStaleSentinelClient.shouldSendChatAlert(red, 1_000L, Long.MIN_VALUE),
                "first real red must alert without Long.MIN_VALUE overflow");
        expect(false, SlabModelStaleSentinelClient.shouldSendChatAlert(red, 1_039L, 1_000L),
                "a red inside the 39-tick window must stay throttled");
        expect(true, SlabModelStaleSentinelClient.shouldSendChatAlert(red, 1_040L, 1_000L),
                "a red at the 40-tick boundary must alert");
        expect(true, SlabModelStaleSentinelClient.shouldSendChatAlert(red, 10L, 1_000L),
                "a world-clock reset must reopen the alert gate");
        expect(false, SlabModelStaleSentinelClient.shouldSendChatAlert(
                        "ENSEMBLE_OCCLUDED_OCCUPANCY", 2_000L, Long.MIN_VALUE),
                "informational occupancy must never chat");
        expect(false, SlabModelStaleSentinelClient.shouldSendChatAlert(
                        SlabModelStaleSentinel.KIND_NO_BAKE_YELLOW, 2_000L, Long.MIN_VALUE),
                "NO_BAKE yellow must never chat");
        expect(false, SlabModelStaleSentinelClient.shouldSendChatAlert(
                        "UNKNOWN_DIAGNOSTIC", 2_000L, Long.MIN_VALUE),
                "unknown diagnostics must never chat");
        ctx.runOnClient(client -> c5CarpetAndPowderUseCanonicalClientDy());
        Slabbed.LOGGER.info(CLIENT_GAMETEST_PASS);
    }

    private static void c5CarpetAndPowderUseCanonicalClientDy() {
        BlockPos pos = new BlockPos(3, 4, 3);
        BlockState slab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState carpet = Blocks.MOSS_CARPET.defaultBlockState();
        BlockState powder = Blocks.POWDER_SNOW.defaultBlockState();
        BlockAndTintGetter view = view(Map.of(pos.below(), slab, pos, carpet));
        SlabAnchorAttachment.ClientPlacementDyFactLookup previousLookup =
                SlabAnchorAttachment.clientEffectivePlacementDyLookup;
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        try {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
            SlabAnchorAttachment.clientEffectivePlacementDyLookup = (world, lookupPos) ->
                    pos.equals(lookupPos)
                            ? SlabAnchorAttachment.PlacementDyFact.present(-1.0d)
                            : SlabAnchorAttachment.PlacementDyFact.absent();

            expectDouble(-1.0d, ClientDy.dyFor(view, pos, carpet),
                    "stored carpet logical client dy");
            expectDouble(-1.0d, OffsetBlockStateModel.liveModelDy(view, pos, carpet),
                    "stored carpet model dy");
            expectDouble(-1.0d, ClientDy.dyFor(view, pos, powder),
                    "stored powder logical client dy");
            expectDouble(-1.0d, OffsetBlockStateModel.liveModelDy(view, pos, powder),
                    "stored powder model dy");

            VoxelShape baseCarpet = Shapes.box(0.0d, 0.0d, 0.0d, 1.0d, 0.0625d, 1.0d);
            VoxelShape shifted = ClientDy.offsetShape(view, pos, carpet, baseCarpet);
            expectDouble(-1.0d, shifted.bounds().minY, "stored carpet outline minY");
            expectDouble(-0.9375d, shifted.bounds().maxY, "stored carpet outline maxY");

            SlabAnchorAttachment.clientEffectivePlacementDyLookup =
                    (world, lookupPos) -> SlabAnchorAttachment.PlacementDyFact.absent();
            expectDouble(0.0d, ClientDy.dyFor(view, pos, carpet),
                    "natural carpet logical dy stays flush");
            expectDouble(0.0d, OffsetBlockStateModel.liveModelDy(view, pos, carpet),
                    "natural carpet model retires -0.5 courtesy");
            expectDouble(0.0d, ClientDy.dyFor(view, pos, powder),
                    "natural powder logical dy stays flush");
            expectDouble(0.0d, OffsetBlockStateModel.liveModelDy(view, pos, powder),
                    "natural powder model stays flush");
            VoxelShape flush = ClientDy.offsetShape(view, pos, carpet, baseCarpet);
            expectDouble(0.0d, flush.bounds().minY, "natural carpet outline stays flush");
            expectDouble(0.0625d, flush.bounds().maxY, "natural carpet outline height stays vanilla");
        } finally {
            SlabAnchorAttachment.clientEffectivePlacementDyLookup = previousLookup;
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
    }

    private static BlockAndTintGetter view(Map<BlockPos, BlockState> states) {
        return (BlockAndTintGetter) Proxy.newProxyInstance(
                BlockAndTintGetter.class.getClassLoader(),
                new Class<?>[]{BlockAndTintGetter.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "C5BlockAndTintGetter";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    if ("getBlockState".equals(method.getName())) {
                        return states.getOrDefault((BlockPos) args[0], Blocks.AIR.defaultBlockState());
                    }
                    if ("getFluidState".equals(method.getName())) {
                        return states.getOrDefault((BlockPos) args[0], Blocks.AIR.defaultBlockState())
                                .getFluidState();
                    }
                    if ("getBlockEntity".equals(method.getName())) {
                        return null;
                    }
                    if ("getBlockTint".equals(method.getName())) {
                        return 0xFFFFFF;
                    }
                    if ("getHeight".equals(method.getName())) {
                        return 384;
                    }
                    if ("getMinY".equals(method.getName())) {
                        return -64;
                    }
                    if ("getShade".equals(method.getName())) {
                        return 1.0f;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == double.class) {
            return 0.0d;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }

    private static void expect(boolean expected, boolean actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void expectDouble(double expected, double actual, String label) {
        if (Double.doubleToRawLongBits(expected) != Double.doubleToRawLongBits(actual)) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }
}
