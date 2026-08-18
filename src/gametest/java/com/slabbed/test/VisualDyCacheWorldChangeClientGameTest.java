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
 * The shared client visual-dy cache is keyed by packed {@link BlockPos} alone — no
 * world or dimension discriminator — so an entry published while one world was
 * current is indistinguishable from the same coordinates in the next world. The
 * cache must therefore be EMPTIED whenever the client world goes away or changes;
 * otherwise a render-worker read (a {@code ChunkRendererRegion}-style non-World
 * view, which prefers the published value) serves the PREVIOUS world's dy at those
 * coordinates until the main thread happens to republish them.
 *
 * <p>Proof shape: world A publishes a real lowered dy ({@code -0.5}) for a cell via
 * the production main-thread entry point; the test then leaves world A and creates
 * world B, where that cell is air. A render-region view read of the cell in world B
 * must answer {@code 0.0} (world B's truth), and the cache must not still hold
 * world A's {@code -0.5}.
 *
 * <p>Runs unconditionally: vanilla blocks only, no screenshots, no extra mods.
 */
public final class VisualDyCacheWorldChangeClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        final BlockPos slab = new BlockPos(0, 200, 8);
        final BlockPos block = slab.up();

        final AtomicReference<Double> publishedDy = new AtomicReference<>(Double.NaN);

        try (TestSingleplayerContext worldA = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            worldA.getServer().runOnServer(server -> {
                var world = server.getOverworld();
                world.setBlockState(slab,
                        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                        Block.NOTIFY_LISTENERS);
                world.setBlockState(block, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            });
            ctx.waitTick();
            worldA.getClientWorld().waitForChunksRender();

            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world unavailable while publishing in world A");
                }
                BlockState state = mc.world.getBlockState(block);
                publishedDy.set(SlabSupport.getVisualYOffset(mc.world, block, state));
            });
        }
        if (!approx(publishedDy.get(), -0.5)) {
            throw new RuntimeException("[visual-dy-world-change] FIXTURE INVALID: world A did not publish"
                    + " a lowered dy for the probe cell; publishedDy=" + publishedDy.get());
        }

        final AtomicReference<String> worldBState = new AtomicReference<>("not_read");
        final AtomicReference<Double> retainedCacheDy = new AtomicReference<>(Double.NaN);
        final AtomicReference<Double> regionDy = new AtomicReference<>(Double.NaN);

        try (TestSingleplayerContext worldB = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            worldB.getClientWorld().waitForChunksRender();

            ctx.runOnClient(mc -> {
                if (mc.world == null) {
                    throw new RuntimeException("client world unavailable while reading in world B");
                }
                BlockState state = mc.world.getBlockState(block);
                worldBState.set(state.toString());
                Double cached = SlabSupport.peekCachedClientVisualYOffset(block);
                retainedCacheDy.set(cached == null ? Double.NaN : cached);
                BlockView renderRegionView = new WorldDelegateRegionView(mc.world);
                regionDy.set(SlabSupport.getVisualYOffset(renderRegionView, block, state));
            });
        }

        String proof = "[visual-dy-world-change] publishedDyWorldA=" + publishedDy.get()
                + " worldBState=" + worldBState.get()
                + " retainedCacheDy=" + retainedCacheDy.get()
                + " regionDyWorldB=" + regionDy.get();
        System.out.println(proof);

        if (!worldBState.get().contains("air")) {
            throw new RuntimeException(proof
                    + " FIXTURE INVALID: probe cell is not air in world B, coordinates collided with terrain");
        }
        if (approx(retainedCacheDy.get(), publishedDy.get())) {
            throw new RuntimeException(proof
                    + " RED: cache still holds world A's dy for these coordinates after the world change");
        }
        if (!approx(regionDy.get(), 0.0)) {
            throw new RuntimeException(proof
                    + " RED: render-region read in world B served a stale dy from the previous world;"
                    + " expected 0.0 for air");
        }
        System.out.println("[visual-dy-world-change] => GREEN");
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
