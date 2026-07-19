package com.slabbed.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.WireConnection;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Regression matrix for GH #37: Slabbed's redstone-wire connection override used the
 * "can this block support dust?" predicate as though it meant "should dust connect to this
 * block?". Every ordinary full cube therefore changed a vanilla {@link WireConnection#NONE}
 * result to {@link WireConnection#SIDE}.
 *
 * <p>These tests drive {@link BlockState#getStateForNeighborUpdate} rather than calling a
 * Slabbed helper. That public vanilla transition is the real path that asks
 * {@code RedstoneWireBlock#getRenderConnectionType} to recalculate one horizontal arm.
 */
public final class RedstoneWireConnectionTest {

    private static final BlockPos RELATIVE_WIRE_POS = new BlockPos(3, 3, 3);

    /**
     * RED on the issue revision: all three ordinary building blocks produce a false SIDE arm.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void ordinarySolidBlocksDoNotCreatePhantomConnections(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wirePos = absoluteWirePos(ctx);
        StringBuilder failures = new StringBuilder();

        for (Block ordinarySolid : new Block[]{Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.STONE}) {
            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockPos sidePos = wirePos.offset(direction);
                clearConnectionCell(world, wirePos, direction);
                world.setBlockState(sidePos, ordinarySolid.getDefaultState(), Block.NOTIFY_LISTENERS);

                WireConnection actual = connectionToward(world, wirePos, direction);
                if (actual != WireConnection.NONE) {
                    if (!failures.isEmpty()) {
                        failures.append("; ");
                    }
                    failures.append(ordinarySolid.getTranslationKey())
                            .append(' ')
                            .append(direction)
                            .append(" -> ")
                            .append(actual);
                }
            }
        }

        ctx.assertTrue(failures.isEmpty(),
                "ordinary solid blocks with no redstone on, beside, or below them must remain "
                        + "disconnected; phantom results: " + failures);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sameLevelWireStillConnectsSideways(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wirePos = absoluteWirePos(ctx);
        BlockPos sidePos = wirePos.east();

        clearConnectionCell(world, wirePos, Direction.EAST);
        world.setBlockState(sidePos.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(sidePos, Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_LISTENERS);

        assertConnection(ctx, world, wirePos, Direction.EAST, WireConnection.SIDE,
                "same-level redstone dust must retain its vanilla side connection");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockStepUpStillConnectsUp(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wirePos = absoluteWirePos(ctx);
        BlockPos sidePos = wirePos.east();

        clearConnectionCell(world, wirePos, Direction.EAST);
        world.setBlockState(sidePos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(sidePos.up(), Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_LISTENERS);

        assertConnection(ctx, world, wirePos, Direction.EAST, WireConnection.UP,
                "dust must retain its vanilla upward connection over a full block");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabStepUpStillConnectsToDustAbove(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wirePos = absoluteWirePos(ctx);
        BlockPos sidePos = wirePos.east();

        clearConnectionCell(world, wirePos, Direction.EAST);
        world.setBlockState(sidePos,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        world.setBlockState(sidePos.up(), Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_LISTENERS);

        assertConnection(ctx, world, wirePos, Direction.EAST, WireConnection.SIDE,
                "dust above a supported bottom slab must remain connected at the slab-height step");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabsStepUpStillConnectsToDustAbove(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wirePos = absoluteWirePos(ctx);
        BlockPos sidePos = wirePos.east();

        clearConnectionCell(world, wirePos, Direction.EAST);
        world.setBlockState(sidePos,
                TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        world.setBlockState(sidePos.up(), Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_LISTENERS);

        assertConnection(ctx, world, wirePos, Direction.EAST, WireConnection.SIDE,
                "dust above a Terrain Slabs bottom-like surface must remain connected");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void blockedHeadroomStillPreventsStepUp(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wirePos = absoluteWirePos(ctx);
        BlockPos sidePos = wirePos.east();

        clearConnectionCell(world, wirePos, Direction.EAST);
        world.setBlockState(wirePos.up(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(sidePos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(sidePos.up(), Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_LISTENERS);

        assertConnection(ctx, world, wirePos, Direction.EAST, WireConnection.NONE,
                "a solid block over the source dust must keep blocking upward redstone travel");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stepDownStillConnectsToDustBelow(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wirePos = absoluteWirePos(ctx);
        BlockPos sidePos = wirePos.east();

        clearConnectionCell(world, wirePos, Direction.EAST);
        world.setBlockState(sidePos.down().down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(sidePos.down(), Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_LISTENERS);

        assertConnection(ctx, world, wirePos, Direction.EAST, WireConnection.SIDE,
                "dust must retain its vanilla downward step connection");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void visualHalfStepDownOntoSlabSupportedDustStillConnects(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wirePos = absoluteWirePos(ctx);
        BlockPos sidePos = wirePos.east();

        clearConnectionCell(world, wirePos, Direction.EAST);
        world.setBlockState(wirePos.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        for (Block slabSupport : new Block[]{Blocks.OAK_SLAB, TerrainSlabsTestShim.TEST_TS_SLAB}) {
            world.setBlockState(sidePos.down(),
                    slabSupport.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(sidePos, Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_LISTENERS);

            assertConnection(ctx, world, wirePos, Direction.EAST, WireConnection.SIDE,
                    "flat dust must connect across the visual half-step down to dust lowered on "
                            + slabSupport.getTranslationKey());
            ctx.assertTrue(weakPowerToward(world, wirePos, Direction.EAST) == 15,
                    "powered flat dust must emit across the visual half-step down to dust lowered on "
                            + slabSupport.getTranslationKey());

            world.setBlockState(sidePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(sidePos.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void solidBlockStillOccludesDustBelowIt(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wirePos = absoluteWirePos(ctx);
        BlockPos sidePos = wirePos.east();

        clearConnectionCell(world, wirePos, Direction.EAST);
        world.setBlockState(sidePos.down().down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(sidePos.down(), Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(sidePos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        assertConnection(ctx, world, wirePos, Direction.EAST, WireConnection.NONE,
                "a full solid side block must continue to occlude redstone dust below it");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void repeaterConnectionStillRespectsItsAxis(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wirePos = absoluteWirePos(ctx);
        BlockPos sidePos = wirePos.east();

        clearConnectionCell(world, wirePos, Direction.EAST);
        world.setBlockState(sidePos.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(sidePos,
                Blocks.REPEATER.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST),
                Block.NOTIFY_LISTENERS);
        assertConnection(ctx, world, wirePos, Direction.EAST, WireConnection.SIDE,
                "dust must connect to a repeater along the repeater's input/output axis");

        world.setBlockState(sidePos,
                Blocks.REPEATER.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH),
                Block.NOTIFY_LISTENERS);
        assertConnection(ctx, world, wirePos, Direction.EAST, WireConnection.NONE,
                "dust must not connect to the side of a perpendicular repeater");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullCubeObserverConnectionStillRespectsItsFacing(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wirePos = absoluteWirePos(ctx);
        BlockPos sidePos = wirePos.east();

        clearConnectionCell(world, wirePos, Direction.EAST);
        world.setBlockState(sidePos,
                Blocks.OBSERVER.getDefaultState().with(Properties.FACING, Direction.EAST),
                Block.NOTIFY_LISTENERS);
        assertConnection(ctx, world, wirePos, Direction.EAST, WireConnection.SIDE,
                "dust must connect to the observer face vanilla exposes toward it");
        ctx.assertTrue(weakPowerToward(world, wirePos, Direction.EAST) == 15,
                "powered dust must emit toward the observer face vanilla exposes");

        world.setBlockState(sidePos,
                Blocks.OBSERVER.getDefaultState().with(Properties.FACING, Direction.NORTH),
                Block.NOTIFY_LISTENERS);
        assertConnection(ctx, world, wirePos, Direction.EAST, WireConnection.NONE,
                "dust must not connect to a perpendicular observer face merely because the "
                        + "observer is a full cube");
        ctx.assertTrue(weakPowerToward(world, wirePos, Direction.EAST) == 0,
                "powered dust must not emit toward a perpendicular observer face");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void poweredWireDoesNotEmitTowardAnOrdinarySolidBlock(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wirePos = absoluteWirePos(ctx);
        BlockPos sidePos = wirePos.east();

        clearConnectionCell(world, wirePos, Direction.EAST);
        world.setBlockState(sidePos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        ctx.assertTrue(weakPowerToward(world, wirePos, Direction.EAST) == 0,
                "GH #37 is functional as well as visual: powered dust must not emit directional "
                        + "power toward an ordinary stone block with no redstone component");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void poweredWireStillRespectsRealDirectionalConnections(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos wirePos = absoluteWirePos(ctx);
        BlockPos sidePos = wirePos.east();

        clearConnectionCell(world, wirePos, Direction.EAST);
        world.setBlockState(sidePos.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(sidePos, Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(weakPowerToward(world, wirePos, Direction.EAST) == 15,
                "powered dust must still emit toward same-level redstone dust");

        world.setBlockState(sidePos,
                Blocks.REPEATER.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST),
                Block.NOTIFY_LISTENERS);
        ctx.assertTrue(weakPowerToward(world, wirePos, Direction.EAST) == 15,
                "powered dust must still emit along an aligned repeater's axis");

        world.setBlockState(sidePos,
                Blocks.REPEATER.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH),
                Block.NOTIFY_LISTENERS);
        ctx.assertTrue(weakPowerToward(world, wirePos, Direction.EAST) == 0,
                "powered dust must not emit into the side of a perpendicular repeater");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dustPlacementSupportRemainsAvailableOnSlabTopSurfaces(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos vanillaDustPos = ctx.getAbsolutePos(new BlockPos(2, 3, 2));
        BlockPos terrainDustPos = ctx.getAbsolutePos(new BlockPos(5, 3, 2));

        world.setBlockState(vanillaDustPos.down(),
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        world.setBlockState(terrainDustPos.down(),
                TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);

        ctx.assertTrue(Blocks.REDSTONE_WIRE.getDefaultState().canPlaceAt(world, vanillaDustPos),
                "removing the connection overrides must not remove dust placement support on "
                        + "vanilla bottom slabs");
        ctx.assertTrue(Blocks.REDSTONE_WIRE.getDefaultState().canPlaceAt(world, terrainDustPos),
                "removing the connection overrides must not remove dust placement support on "
                        + "Terrain Slabs bottom-like surfaces");
        ctx.assertTrue(!Blocks.REDSTONE_WIRE.getDefaultState().canPlaceAt(world, wirePosOverAir(ctx)),
                "dust must retain vanilla survival behavior and reject air as a support surface");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void dustPopsWhenItsSupportIsRemoved(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos dustPos = ctx.getAbsolutePos(new BlockPos(3, 3, 3));
        BlockPos supportPos = dustPos.down();

        for (Block slabSupport : new Block[]{Blocks.OAK_SLAB, TerrainSlabsTestShim.TEST_TS_SLAB}) {
            world.setBlockState(supportPos,
                    slabSupport.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_ALL);
            world.setBlockState(dustPos, Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_ALL);
            ctx.assertTrue(world.getBlockState(dustPos).isOf(Blocks.REDSTONE_WIRE),
                    "setup: dust must survive on " + slabSupport.getTranslationKey()
                            + " before that support is removed");

            world.setBlockState(supportPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            ctx.assertTrue(world.getBlockState(dustPos).isAir(),
                    "redstone dust must pop immediately when "
                            + slabSupport.getTranslationKey() + " support is removed");
        }
        ctx.complete();
    }

    private static BlockPos absoluteWirePos(TestContext ctx) {
        return ctx.getAbsolutePos(RELATIVE_WIRE_POS);
    }

    private static BlockPos wirePosOverAir(TestContext ctx) {
        return ctx.getAbsolutePos(new BlockPos(3, 5, 5));
    }

    private static void clearConnectionCell(ServerWorld world, BlockPos wirePos, Direction direction) {
        BlockPos sidePos = wirePos.offset(direction);
        world.setBlockState(wirePos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(sidePos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(sidePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(sidePos.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(sidePos.down().down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    private static WireConnection connectionToward(ServerWorld world, BlockPos wirePos, Direction direction) {
        BlockPos neighborPos = wirePos.offset(direction);
        BlockState recalculated = Blocks.REDSTONE_WIRE.getDefaultState().getStateForNeighborUpdate(
                world,
                world,
                wirePos,
                direction,
                neighborPos,
                world.getBlockState(neighborPos),
                world.getRandom());
        EnumProperty<WireConnection> property =
                RedstoneWireBlock.DIRECTION_TO_WIRE_CONNECTION_PROPERTY.get(direction);
        return recalculated.get(property);
    }

    private static int weakPowerToward(ServerWorld world, BlockPos wirePos, Direction direction) {
        BlockState poweredWire = Blocks.REDSTONE_WIRE.getDefaultState()
                .with(RedstoneWireBlock.POWER, 15);
        return poweredWire.getWeakRedstonePower(world, wirePos, direction.getOpposite());
    }

    private static void assertConnection(
            TestContext ctx,
            ServerWorld world,
            BlockPos wirePos,
            Direction direction,
            WireConnection expected,
            String message
    ) {
        WireConnection actual = connectionToward(world, wirePos, direction);
        ctx.assertTrue(actual == expected, message + "; expected " + expected + ", got " + actual);
    }
}
