package com.slabbed.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Pins the vanilla-owned downstream policies restored together in parity phase P5. */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class VanillaDownstreamOwnershipTest {
    private static final String TEMPLATE = "empty";
    private static final BlockPos RELATIVE_WIRE_POS = new BlockPos(3, 3, 3);

    @GameTest(template = TEMPLATE)
    public void redstoneTopologyRemainsVanillaOwned(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos wirePos = ctx.absolutePos(RELATIVE_WIRE_POS);
        Direction direction = Direction.EAST;
        BlockPos sidePos = wirePos.relative(direction);

        resetRedstoneLane(world, wirePos, direction);
        world.setBlock(sidePos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockState ordinarySolid = recalculatedWire(world, wirePos, direction);
        ctx.assertTrue(connectionToward(ordinarySolid, direction) == RedstoneSide.NONE,
                "ordinary stone must not create a phantom redstone arm");
        ctx.assertTrue(signalToward(ordinarySolid, world, wirePos, direction) == 0,
                "powered dust must not emit directional power toward ordinary stone");

        resetRedstoneLane(world, wirePos, direction);
        world.setBlock(sidePos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(sidePos, Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
        ctx.assertTrue(connectionToward(recalculatedWire(world, wirePos, direction), direction)
                        == RedstoneSide.SIDE,
                "same-level dust must keep its vanilla side connection");

        resetRedstoneLane(world, wirePos, direction);
        world.setBlock(sidePos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(sidePos.above(), Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
        ctx.assertTrue(connectionToward(recalculatedWire(world, wirePos, direction), direction)
                        == RedstoneSide.UP,
                "dust must keep its vanilla rise over a full block");

        world.setBlock(wirePos.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        ctx.assertTrue(connectionToward(recalculatedWire(world, wirePos, direction), direction)
                        == RedstoneSide.NONE,
                "blocked headroom must still prevent the rise");

        resetRedstoneLane(world, wirePos, direction);
        world.setBlock(sidePos,
                Blocks.REPEATER.defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH),
                Block.UPDATE_ALL);
        ctx.assertTrue(connectionToward(recalculatedWire(world, wirePos, direction), direction)
                        == RedstoneSide.NONE,
                "dust must not connect to the side of a perpendicular repeater");

        world.setBlock(sidePos,
                Blocks.REPEATER.defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST),
                Block.UPDATE_ALL);
        ctx.assertTrue(connectionToward(recalculatedWire(world, wirePos, direction), direction)
                        == RedstoneSide.SIDE,
                "dust must keep the vanilla connection along a repeater axis");

        resetRedstoneLane(world, wirePos, direction);
        world.setBlock(sidePos,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.UPDATE_ALL);
        world.setBlock(sidePos.above(), Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
        ctx.assertTrue(connectionToward(recalculatedWire(world, wirePos, direction), direction)
                        != RedstoneSide.NONE,
                "removing the custom topology override must preserve slab-supported dust discovery");
        ctx.assertTrue(Blocks.REDSTONE_WIRE.defaultBlockState().canSurvive(world, sidePos.above()),
                "redstone dust must retain placement support on a bottom slab");

        ctx.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void bottomSlabsRemainMobProof(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos dryBottom = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos wetBottom = dryBottom.east();
        BlockPos top = dryBottom.east(2);
        BlockPos doubled = dryBottom.east(3);
        BlockPos stone = dryBottom.east(4);
        BlockPos air = dryBottom.east(5);

        world.setBlock(dryBottom, slab(SlabType.BOTTOM, false), Block.UPDATE_ALL);
        world.setBlock(wetBottom, slab(SlabType.BOTTOM, true), Block.UPDATE_ALL);
        world.setBlock(top, slab(SlabType.TOP, false), Block.UPDATE_ALL);
        world.setBlock(doubled, slab(SlabType.DOUBLE, false), Block.UPDATE_ALL);
        world.setBlock(stone, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(air, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        ctx.assertTrue(world.getBlockState(dryBottom).isFaceSturdy(world, dryBottom, Direction.UP),
                "setup: Slabbed placement support must remain available on a bottom slab");
        assertSpawnSurface(ctx, world, dryBottom, false, "dry bottom slab");
        assertSpawnSurface(ctx, world, wetBottom, false, "waterlogged bottom slab");
        assertSpawnSurface(ctx, world, top, true, "top slab control");
        assertSpawnSurface(ctx, world, doubled, true, "double slab control");
        assertSpawnSurface(ctx, world, stone, true, "stone control");
        assertSpawnSurface(ctx, world, air, false, "air control");
        ctx.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void chainSurvivalOverrideRemainsUnregistered(GameTestHelper ctx) {
        try (InputStream stream = VanillaDownstreamOwnershipTest.class.getClassLoader()
                .getResourceAsStream("slabbed.mixins.json")) {
            ctx.assertTrue(stream != null, "the runtime mixin configuration must be present");
            String config = new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
            ctx.assertTrue(!config.contains("\"ChainBlockNeighborSurvivalMixin\""),
                    "the stale chain survival override must remain unregistered");
        } catch (IOException exception) {
            throw new IllegalStateException("could not read the runtime mixin configuration", exception);
        }
        ctx.succeed();
    }

    private static BlockState slab(SlabType type, boolean waterlogged) {
        return Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, type)
                .setValue(SlabBlock.WATERLOGGED, waterlogged);
    }

    private static void assertSpawnSurface(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos supportPos,
            boolean expected,
            String label
    ) {
        boolean direct = world.getBlockState(supportPos)
                .isValidSpawn(world, supportPos, EntityType.ZOMBIE);
        boolean onGround = net.minecraft.world.level.NaturalSpawner.isSpawnPositionOk(
                SpawnPlacements.Type.ON_GROUND, world, supportPos.above(), EntityType.ZOMBIE);
        ctx.assertTrue(direct == expected && onGround == expected,
                label + " expected direct/ON_GROUND spawn verdicts "
                        + expected + "/" + expected + " but got " + direct + "/" + onGround);
    }

    private static void resetRedstoneLane(ServerLevel world, BlockPos wirePos, Direction direction) {
        BlockPos sidePos = wirePos.relative(direction);
        world.setBlock(wirePos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(wirePos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(sidePos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(sidePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(sidePos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(sidePos.below(2), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static BlockState recalculatedWire(ServerLevel world, BlockPos wirePos, Direction direction) {
        BlockPos neighborPos = wirePos.relative(direction);
        return Blocks.REDSTONE_WIRE.defaultBlockState().updateShape(
                direction, world.getBlockState(neighborPos), world, wirePos, neighborPos);
    }

    private static RedstoneSide connectionToward(BlockState wire, Direction direction) {
        EnumProperty<RedstoneSide> property = RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(direction);
        return wire.getValue(property);
    }

    private static int signalToward(
            BlockState wire,
            ServerLevel world,
            BlockPos wirePos,
            Direction direction
    ) {
        return wire.setValue(RedStoneWireBlock.POWER, 15)
                .getSignal(world, wirePos, direction.getOpposite());
    }
}
