package com.slabbed.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.WireConnection;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Regression matrix for GH #57: Slabbed's (now-deleted) redstone-wire connection override made
 * dust visually/logically climb onto adjacent ordinary full blocks (phantom {@link WireConnection#SIDE}
 * arms), which broke piston-based machines — circuits would disconnect/reconnect and turn into
 * unintended clocks (reporter's hopper clock and TNT duper). The underlying fix (#37,
 * {@code 37928aca}) deleted the two {@code getRenderConnectionType} override injectors in
 * {@code RedstoneWireBlockMixin} so vanilla owns connection decisions again.
 *
 * <p>These tests replay the #57 symptom family end-to-end: a dust line runs across ordinary full
 * blocks, past other ordinary full blocks (and, in the second test, a ground-level bottom slab)
 * that must NOT pick up a phantom connection, and into a piston. Once the circuit is powered and
 * has settled, the piston's {@link PistonBlock#EXTENDED} state is sampled across a spread of
 * ticks to prove the circuit does not oscillate — the literal hopper-clock/TNT-duper symptom.
 *
 * <p>Power delivery note: a straight dust line does NOT directly power a plain solid block it
 * merely touches sideways — {@link RedstoneWireBlock#getWeakRedstonePower} gates lateral emission
 * on the wire's own {@code WireConnection} state, and an ordinary {@link PistonBlock} never earns
 * a {@code SIDE} connection (it doesn't override {@code emitsRedstonePower()}), exactly like the
 * plain STONE case {@code RedstoneWireConnectionTest#poweredWireDoesNotEmitTowardAnOrdinarySolidBlock}
 * already proves. The vertical case is unconditional, though, so both scenarios below route the
 * line's final segment to rest directly on the piston's top face — the same "step up onto a
 * taller full block" geometry {@code RedstoneWireConnectionTest#fullBlockStepUpStillConnectsUp}
 * already exercises, just with a piston as the taller block — which reliably powers it.
 */
public final class RedstonePistonCircuitStabilityTest {

    private static final int[] STABILITY_SAMPLE_TICKS = {10, 15, 20, 25, 30};

    /**
     * Straight dust line on ordinary full blocks, past other ordinary full blocks, into a piston.
     * RED on the issue revision: the cobblestone/oak-planks probes would pick up a phantom SIDE
     * arm, and the resulting spurious connection churn is exactly the class of bug that turned
     * the reporter's static circuits into unintended clocks.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 40)
    public void dustLineOnFullBlocksFeedsPistonWithoutPhantomConnectionsOrOscillation(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos floorStart = ctx.getAbsolutePos(new BlockPos(2, 2, 2));

        BlockPos[] wire = buildFloorAndDustLine(world, floorStart, 4);

        BlockPos northProbe = wire[1].north();
        BlockPos southProbe = wire[2].south();
        world.setBlockState(northProbe, Blocks.COBBLESTONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(southProbe, Blocks.OAK_PLANKS.getDefaultState(), Block.NOTIFY_ALL);

        BlockPos pistonPos = floorStart.east(4);
        world.setBlockState(pistonPos,
                Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, Direction.SOUTH),
                Block.NOTIFY_ALL);
        BlockPos topWire = pistonPos.up();
        world.setBlockState(topWire, Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_ALL);

        assertConnection(ctx, world, wire[1], Direction.NORTH, WireConnection.NONE,
                "cobblestone beside the dust line must not create a phantom SIDE arm (GH #57)");
        assertConnection(ctx, world, wire[2], Direction.SOUTH, WireConnection.NONE,
                "oak planks beside the dust line must not create a phantom SIDE arm (GH #57)");
        assertConnection(ctx, world, wire[1], Direction.EAST, WireConnection.SIDE,
                "the dust line itself must keep its straight-line SIDE connection between segments");

        ctx.assertTrue(!world.getBlockState(pistonPos).get(PistonBlock.EXTENDED),
                "piston must start retracted before the circuit is powered");

        BlockPos sourcePos = wire[0].west();
        ctx.runAtTick(1, () ->
                world.setBlockState(sourcePos, Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL));

        boolean[] settledExtended = new boolean[1];

        ctx.runAtTick(5, () -> {
            assertConnection(ctx, world, wire[1], Direction.NORTH, WireConnection.NONE,
                    "cobblestone beside the now-powered dust line must still not create a phantom SIDE arm");
            assertConnection(ctx, world, wire[2], Direction.SOUTH, WireConnection.NONE,
                    "oak planks beside the now-powered dust line must still not create a phantom SIDE arm");

            boolean extended = world.getBlockState(pistonPos).get(PistonBlock.EXTENDED);
            ctx.assertTrue(extended,
                    "piston fed by the dust line must reach the vanilla-expected EXTENDED state once powered");
            settledExtended[0] = extended;
        });

        for (int tick : STABILITY_SAMPLE_TICKS) {
            ctx.runAtTick(tick, () -> {
                boolean extended = world.getBlockState(pistonPos).get(PistonBlock.EXTENDED);
                ctx.assertTrue(extended == settledExtended[0],
                        "piston EXTENDED flipped after settling at tick " + tick
                                + " (GH #57 unintended-clock symptom): settled=" + settledExtended[0]
                                + ", now=" + extended);
            });
        }

        ctx.runAtTick(31, ctx::complete);
    }

    /**
     * Same circuit, but one probe is a ground-level bottom slab (matching the reporter's builds)
     * instead of a full block. Proves slab proximity does not alter vanilla connection ownership
     * any more than an ordinary full block does.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 40)
    public void dustLineBesideGroundLevelSlabFeedsPistonWithoutPhantomConnectionsOrOscillation(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos floorStart = ctx.getAbsolutePos(new BlockPos(2, 2, 5));

        BlockPos[] wire = buildFloorAndDustLine(world, floorStart, 4);

        BlockPos slabPos = wire[1].north();
        BlockPos slabFloorPos = slabPos.down();
        world.setBlockState(slabFloorPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(slabPos,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_ALL);

        BlockPos plainProbe = wire[2].south();
        world.setBlockState(plainProbe, Blocks.OAK_PLANKS.getDefaultState(), Block.NOTIFY_ALL);

        BlockPos pistonPos = floorStart.east(4);
        world.setBlockState(pistonPos,
                Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, Direction.SOUTH),
                Block.NOTIFY_ALL);
        BlockPos topWire = pistonPos.up();
        world.setBlockState(topWire, Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_ALL);

        assertConnection(ctx, world, wire[1], Direction.NORTH, WireConnection.NONE,
                "a ground-level bottom slab beside the dust line must not create a phantom SIDE arm (GH #57)");
        assertConnection(ctx, world, wire[2], Direction.SOUTH, WireConnection.NONE,
                "oak planks beside the dust line must not create a phantom SIDE arm (GH #57)");
        assertConnection(ctx, world, wire[1], Direction.EAST, WireConnection.SIDE,
                "the dust line itself must keep its straight-line SIDE connection between segments");

        ctx.assertTrue(!world.getBlockState(pistonPos).get(PistonBlock.EXTENDED),
                "piston must start retracted before the circuit is powered");

        BlockPos sourcePos = wire[0].west();
        ctx.runAtTick(1, () ->
                world.setBlockState(sourcePos, Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL));

        boolean[] settledExtended = new boolean[1];

        ctx.runAtTick(5, () -> {
            assertConnection(ctx, world, wire[1], Direction.NORTH, WireConnection.NONE,
                    "ground-level bottom slab beside the now-powered dust line must still not create a "
                            + "phantom SIDE arm");
            assertConnection(ctx, world, wire[2], Direction.SOUTH, WireConnection.NONE,
                    "oak planks beside the now-powered dust line must still not create a phantom SIDE arm");

            boolean extended = world.getBlockState(pistonPos).get(PistonBlock.EXTENDED);
            ctx.assertTrue(extended,
                    "piston fed by the dust line must reach the vanilla-expected EXTENDED state once powered, "
                            + "even with a ground-level slab present beside the line");
            settledExtended[0] = extended;
        });

        for (int tick : STABILITY_SAMPLE_TICKS) {
            ctx.runAtTick(tick, () -> {
                boolean extended = world.getBlockState(pistonPos).get(PistonBlock.EXTENDED);
                ctx.assertTrue(extended == settledExtended[0],
                        "piston EXTENDED flipped after settling at tick " + tick
                                + " with a slab present beside the line (GH #57 unintended-clock symptom): "
                                + "settled=" + settledExtended[0] + ", now=" + extended);
            });
        }

        ctx.runAtTick(31, ctx::complete);
    }

    /**
     * Lays {@code segments} STONE floor blocks eastward starting at {@code floorStart}, with
     * REDSTONE_WIRE resting on top of each. Returns the wire positions (index-aligned with the
     * floor blocks).
     */
    private static BlockPos[] buildFloorAndDustLine(ServerWorld world, BlockPos floorStart, int segments) {
        BlockPos[] wire = new BlockPos[segments];
        for (int i = 0; i < segments; i++) {
            BlockPos floorPos = floorStart.east(i);
            world.setBlockState(floorPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            BlockPos wirePos = floorPos.up();
            world.setBlockState(wirePos, Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_ALL);
            wire[i] = wirePos;
        }
        return wire;
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
