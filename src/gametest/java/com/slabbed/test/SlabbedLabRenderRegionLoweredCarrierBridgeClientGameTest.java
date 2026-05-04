package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public final class SlabbedLabRenderRegionLoweredCarrierBridgeClientGameTest implements FabricClientGameTest {
    private static final String PROOF = "RENDER_REGION_LOWERED_SLAB_CARRIER_BRIDGE";
    private static final double EPSILON = 1.0e-6d;
    private static final BlockPos BOTTOM_SUPPORT = new BlockPos(4, 200, 4);
    private static final BlockPos BOTTOM_FULL = BOTTOM_SUPPORT.up();
    private static final BlockPos BOTTOM_CARRIER = BOTTOM_FULL.up();
    private static final BlockPos DOUBLE_SUPPORT = new BlockPos(10, 200, 4);
    private static final BlockPos DOUBLE_FULL = DOUBLE_SUPPORT.up();
    private static final BlockPos DOUBLE_CARRIER = DOUBLE_FULL.east();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runBridgeProof(ctx, singleplayer);
        }
    }

    private static void runBridgeProof(ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            clearArea(world, BOTTOM_SUPPORT, 4);
            clearArea(world, DOUBLE_SUPPORT, 4);
            seedAnchoredFullCarrier(world, BOTTOM_SUPPORT, BOTTOM_FULL);
            seedAnchoredFullCarrier(world, DOUBLE_SUPPORT, DOUBLE_FULL);
            seedLoweredBottomCarrier(world, BOTTOM_CARRIER);
            seedLoweredDoubleCarrier(world, DOUBLE_CARRIER);
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                server.getPlayerManager().getPlayerList().get(0)
                        .changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            }
        });
        waitForSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            BlockState bottomState = world.getBlockState(BOTTOM_CARRIER);
            if (!bottomState.isOf(Blocks.STONE_SLAB)
                    || !bottomState.contains(SlabBlock.TYPE)
                    || bottomState.get(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw new RuntimeException("PROOF_GAP: " + PROOF
                        + " expected real placed lowered BOTTOM carrier at "
                        + BOTTOM_CARRIER.toShortString()
                        + " state=" + bottomState);
            }
            SlabAnchorAttachment.removeAnchor(world, BOTTOM_FULL);
            world.setBlockState(BOTTOM_FULL, Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.removeAnchor(world, DOUBLE_FULL);
            world.setBlockState(DOUBLE_FULL, Blocks.AIR.getDefaultState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        });
        waitForSync(ctx);
        singleplayer.getClientWorld().waitForChunksRender();

        assertBothCases(ctx, "after_support_removal");
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        assertBothCases(ctx, "after_rerender");
    }

    private static void assertBothCases(ClientGameTestContext ctx, String phase) {
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException(PROOF + " client world missing for " + phase);
            }
            assertCarrierBridge(mc.world, phase + ":seeded_bottom", BOTTOM_CARRIER);
            assertCarrierBridge(mc.world, phase + ":lowered_double", DOUBLE_CARRIER);
        });
    }

    private static void assertCarrierBridge(World world, String caseName, BlockPos carrierPos) {
        BlockState state = world.getBlockState(carrierPos);
        if (!state.isOf(Blocks.STONE_SLAB) || !state.contains(SlabBlock.TYPE)) {
            throw new RuntimeException("PROOF_GAP: " + PROOF
                    + " expected slab carrier for " + caseName
                    + " at " + carrierPos.toShortString()
                    + " state=" + state);
        }
        BlockView renderView = renderRegionStyleView(world);
        boolean worldPersistent = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, carrierPos, state);
        boolean nonWorldPersistent =
                SlabAnchorAttachment.isPersistentLoweredSlabCarrier(renderView, carrierPos, state);
        double worldDy = SlabSupport.getYOffset(world, carrierPos, state);
        double modelDy = SlabSupport.getYOffset(renderView, carrierPos, state);
        double outlineDy = minY(state.getOutlineShape(renderView, carrierPos, ShapeContext.absent()));
        double raycastDy = minY(state.getRaycastShape(renderView, carrierPos));
        boolean red = worldPersistent
                && (!nonWorldPersistent
                || Math.abs(modelDy + 0.5d) > EPSILON
                || Math.abs(outlineDy + 0.5d) > EPSILON
                || Math.abs(raycastDy + 0.5d) > EPSILON);
        String classification = red ? "RED" : "GREEN";
        System.out.println("[LOWERED_CARRIER_BRIDGE] proof=" + PROOF
                + " case=" + caseName
                + " classification=" + classification
                + " renderView=" + renderView.getClass().getName()
                + " nonWorld=" + !(renderView instanceof World)
                + " pos=" + carrierPos.toShortString()
                + " state=" + state
                + " persistentLoweredSlabCarrierWorld=" + worldPersistent
                + " persistentLoweredSlabCarrierNonWorld=" + nonWorldPersistent
                + " worldDy=" + worldDy
                + " modelDy=" + modelDy
                + " outlineDy=" + outlineDy
                + " raycastDy=" + raycastDy
                + " targetDy=" + modelDy
                + " clientLoweredSlabCarrierLookup="
                + (SlabAnchorAttachment.clientLoweredSlabCarrierLookup != null));
        if (!worldPersistent || Math.abs(worldDy + 0.5d) > EPSILON) {
            throw new RuntimeException("PROOF_GAP: " + PROOF
                    + " expected client World carrier truth/dy for " + caseName
                    + " persistent=" + worldPersistent
                    + " dy=" + worldDy
                    + " state=" + state);
        }
        if (red) {
            throw new RuntimeException("RED: " + PROOF
                    + " World lookup stayed lowered but nonWorld renderView/model path lost carrier truth"
                    + " case=" + caseName
                    + " persistentLoweredSlabCarrierNonWorld=" + nonWorldPersistent
                    + " modelDy=" + modelDy
                    + " outlineDy=" + outlineDy
                    + " raycastDy=" + raycastDy);
        }
    }

    private static void seedAnchoredFullCarrier(World world, BlockPos supportPos, BlockPos carrierPos) {
        world.setBlockState(supportPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        world.setBlockState(carrierPos, Blocks.STONE.getDefaultState(),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(world, carrierPos, world.getBlockState(carrierPos));
    }

    private static void seedLoweredBottomCarrier(World world, BlockPos carrierPos) {
        world.setBlockState(carrierPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, carrierPos, world.getBlockState(carrierPos));
    }

    private static void seedLoweredDoubleCarrier(World world, BlockPos carrierPos) {
        world.setBlockState(carrierPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                net.minecraft.block.Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, carrierPos, world.getBlockState(carrierPos));
    }

    private static void clearArea(World world, BlockPos supportOrigin, int width) {
        for (int x = -1; x <= width; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = supportOrigin.add(x, y, z);
                    SlabAnchorAttachment.removeAnchor(world, pos);
                    SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, pos);
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    private static void waitForSync(ClientGameTestContext ctx) {
        for (int i = 0; i < 5; i++) {
            ctx.waitTick();
        }
    }

    private static BlockView renderRegionStyleView(World world) {
        return new BlockView() {
            @Override
            public net.minecraft.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
                return world.getBlockEntity(pos);
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return world.getBlockState(pos);
            }

            @Override
            public net.minecraft.fluid.FluidState getFluidState(BlockPos pos) {
                return world.getFluidState(pos);
            }

            @Override
            public int getHeight() {
                return world.getHeight();
            }

            @Override
            public int getBottomY() {
                return world.getBottomY();
            }
        };
    }

    private static double minY(net.minecraft.util.shape.VoxelShape shape) {
        return shape.isEmpty() ? Double.NaN : shape.getBoundingBox().minY;
    }
}
