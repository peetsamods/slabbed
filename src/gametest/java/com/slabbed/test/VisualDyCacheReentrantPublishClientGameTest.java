package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.SlabType;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The shared client visual-dy cache must only ever hold values a REAL resolution
 * produced. While the main thread is resolving one cell's dy (the re-entrancy
 * guard is active), solidity probes of the cells it consults re-enter the
 * visual-dy entry point through the offset shape path; the nested resolution
 * answers the guard's fallback {@code 0.0} — not the probed cell's dy — and
 * publishing that transient answer poisons the cache: a render-worker read then
 * draws the probed cell flush while every main-thread read of the same cell says
 * lowered.
 *
 * <p>Proof shape: a cantilevered stone (air below) beside a column-lowered
 * DYNAMIC-bounds full block ({@code TerrainSlabsTestShim#DYNAMIC_SHAPE_BLOCK})
 * on a bottom slab. Resolving the cantilever runs the adjacency scan, which
 * solidity-probes the lowered neighbour inside the guard; a dynamic-bounds state
 * has no vanilla shape cache, so that probe computes through the offset shape
 * path at runtime and re-enters the visual-dy entry point. (Static-bounds
 * vanilla blocks answer solidity from the state cache and never re-enter, but
 * modded terrain — Terrain Slabs among it — does; the shim block models that
 * class headlessly.) Afterwards the cache's entry for the neighbour (if any) and
 * a render-region view read of it must both agree with its true dy
 * ({@code -0.5}).
 *
 * <p>Runs unconditionally: no screenshots, no extra mods beyond the gametest shim.
 */
public final class VisualDyCacheReentrantPublishClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        final BlockPos slab = new BlockPos(0, 200, 16);
        final BlockPos loweredSupport = slab.up();
        final BlockPos cantilever = loweredSupport.south();

        final AtomicReference<Double> cantileverDy = new AtomicReference<>(Double.NaN);
        final AtomicReference<Double> cachedSupportDy = new AtomicReference<>(Double.NaN);
        final AtomicReference<Double> trueSupportDy = new AtomicReference<>(Double.NaN);
        final AtomicReference<Double> regionSupportDy = new AtomicReference<>(Double.NaN);

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            singleplayer.getServer().runOnServer(server -> {
                var world = server.getOverworld();
                world.setBlockState(slab,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        Block.NOTIFY_LISTENERS);
                world.setBlockState(loweredSupport,
                        TerrainSlabsTestShim.DYNAMIC_SHAPE_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(cantilever.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(cantilever, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            });
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world unavailable for re-entrant publish proof");
                }
                SlabSupport.clearVisualYOffsetCache();

                // The production main-thread entry: resolving the cantilever runs the
                // adjacency scan, which solidity-probes the lowered neighbour while the
                // re-entrancy guard is active.
                BlockState cantileverState = mc.world.getBlockState(cantilever);
                cantileverDy.set(SlabSupport.getVisualYOffset(mc.world, cantilever, cantileverState));

                Double cached = SlabSupport.peekCachedClientVisualYOffset(loweredSupport);
                cachedSupportDy.set(cached == null ? Double.NaN : cached);

                BlockState supportState = mc.world.getBlockState(loweredSupport);
                trueSupportDy.set(SlabSupport.getYOffset(mc.world, loweredSupport, supportState));

                BlockView renderRegionView = new WorldDelegateRegionView(mc.world);
                regionSupportDy.set(SlabSupport.getVisualYOffset(renderRegionView, loweredSupport, supportState));
            });
        }

        String proof = "[visual-dy-reentrant-publish] cantileverDy=" + cantileverDy.get()
                + " cachedSupportDy=" + cachedSupportDy.get()
                + " trueSupportDy=" + trueSupportDy.get()
                + " regionSupportDy=" + regionSupportDy.get();
        System.out.println(proof);

        if (!approx(cantileverDy.get(), -0.5) || !approx(trueSupportDy.get(), -0.5)) {
            throw new RuntimeException(proof
                    + " FIXTURE INVALID: expected the cantilever and its lowered neighbour both at -0.5");
        }
        if (!Double.isNaN(cachedSupportDy.get()) && !approx(cachedSupportDy.get(), trueSupportDy.get())) {
            throw new RuntimeException(proof
                    + " RED: resolving the cantilever published a poisoned dy for the probed neighbour"
                    + " (the re-entrancy guard's fallback, not a real resolution)");
        }
        if (!approx(regionSupportDy.get(), -0.5)) {
            throw new RuntimeException(proof
                    + " RED: render-region read of the probed neighbour drifted from its true dy");
        }
        System.out.println("[visual-dy-reentrant-publish] => GREEN");
    }

    private static boolean approx(double a, double b) {
        return Math.abs(a - b) < 1.0e-6;
    }

    /**
     * Stands in for a {@code ChunkRendererRegion}: a {@link BlockView} that is not a
     * {@code World}, so {@code getVisualYOffset} takes the render-worker read path
     * (published value preferred, fresh computation only on a miss).
     */
    private record WorldDelegateRegionView(BlockView delegate) implements BlockView {
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            BlockState state = getBlockState(pos);
            if (state.getBlock() instanceof BlockEntityProvider) {
                return delegate.getBlockEntity(pos);
            }
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return delegate.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getBottomY() {
            return delegate.getBottomY();
        }
    }
}
