package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.WireConnection;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A redstone wire beside a lowered half-step reads the wire drawn on the slab next to it (maintainer
 * ruling, 2026-09-28) — {@link com.slabbed.util.WireVisualSignal} and its hook in the wire's
 * incoming-signal read.
 *
 * <p>The scene is a stone floor at row 1. The reader wire sits on stone at row 2 (wire at row 3);
 * the column beside it carries a bottom slab at row 3 with a wire at row 4 seated -0.5, drawn inside
 * the slab's cell half a block above the reader. A redstone block beside the lowered wire feeds it.
 * Seats are AUTHORED into the placement-height store and read with the store forced ON, the way a
 * player-placed wire reads, and every wire premise-checks its seat. Wires are placed and then
 * re-shaped from their surroundings, the way placement does in play, so connection arms are the
 * game's own answer. The reader's arm toward the slab IS vanilla's own answer: a bottom slab's top
 * counts as a surface a wire can stand on, so the wire beside it already connected toward the slab —
 * only the power read was missing. The arm rows pin that no connection hook is needed on this line; a
 * port whose slab top is not such a surface must add one.
 *
 * <p>MUTATIONS that must redden this class: withhold {@code RedstoneWireVisualStepMixin} (rows 1, 4,
 * 5, 7, 8); drop the drawn-cell test in {@code WireVisualSignal.wireDrawnIn} (row 2, the top-slab
 * control); drop its one-block bound (row 9).
 */
public final class RedstoneWireVisualStepTest {

    private static final double EPS = 1.0e-6d;
    private static final double FLUSH = 0.0d;
    private static final double LOWERED = -0.5d;
    private static final double LOWERED_TWICE = -1.0d;
    private static final int FLOOR_Y = 1;
    private static final int Z = 2;

    private interface FrozenBody {
        void run();
    }

    /** Reads with the store ON, the way a player-placed wire reads. */
    private static void withFrozen(FrozenBody body) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            body.run();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    private static GameTestException failure(String message) {
        return new GameTestException(message);
    }

    private static BlockState bottomSlab() {
        return Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState topSlab() {
        return Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
    }

    private static void floor(TestContext ctx, int minX, int maxX) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = Z - 1; z <= Z + 1; z++) {
                ctx.setBlockState(new BlockPos(x, FLOOR_Y, z), Blocks.STONE.getDefaultState());
            }
        }
    }

    private static void stone(TestContext ctx, int x, int y) {
        ctx.setBlockState(new BlockPos(x, y, Z), Blocks.STONE.getDefaultState());
    }

    private static void slab(TestContext ctx, int x, int y, BlockState slab, double seat) {
        BlockPos rel = new BlockPos(x, y, Z);
        ctx.setBlockState(rel, slab);
        seat(ctx.getWorld(), ctx.getAbsolutePos(rel), seat);
    }

    private static void source(TestContext ctx, int x, int y) {
        ctx.setBlockState(new BlockPos(x, y, Z), Blocks.REDSTONE_BLOCK.getDefaultState());
    }

    /**
     * Places a wire at the given seat and re-shapes it from its surroundings, as placement does.
     *
     * @return the ABSOLUTE wire position
     */
    private static BlockPos wire(TestContext ctx, int x, int y, double seat) {
        return wire(ctx, x, y, Z, seat);
    }

    /**
     * A flush wire one cell north of ({@code x}, {@code y}) on its own stone. An isolated wire is
     * drawn by vanilla as a straight line, with an arm on BOTH sides of one axis whether or not
     * anything is there; a real branch on the other axis switches that rule off, so the arm toward
     * the slab then reports the game's actual connection and nothing else.
     */
    private static BlockPos branchNorth(TestContext ctx, int x, int y) {
        ctx.setBlockState(new BlockPos(x, y - 1, Z - 1), Blocks.STONE.getDefaultState());
        return wire(ctx, x, y, Z - 1, FLUSH);
    }

    private static BlockPos wire(TestContext ctx, int x, int y, int z, double seat) {
        ServerWorld world = ctx.getWorld();
        BlockPos rel = new BlockPos(x, y, z);
        BlockPos abs = ctx.getAbsolutePos(rel);
        ctx.setBlockState(rel, Blocks.REDSTONE_WIRE.getDefaultState());
        seat(world, abs, seat);
        world.setBlockState(abs, Block.postProcessState(world.getBlockState(abs), world, abs), Block.NOTIFY_ALL);
        seat(world, abs, seat);
        BlockState placed = world.getBlockState(abs);
        if (!(placed.getBlock() instanceof RedstoneWireBlock)) {
            throw failure("premise: the wire at " + abs + " did not survive placement, found " + placed);
        }
        double read = SlabSupport.getYOffset(world, abs, placed);
        if (Math.abs(read - seat) > EPS) {
            throw failure("premise: the wire at " + abs + " must read seat " + seat + ", got " + read);
        }
        return abs;
    }

    private static void seat(ServerWorld world, BlockPos abs, double seat) {
        if (seat < 0.0d) {
            Map<BlockPos, Long> seats = new LinkedHashMap<>();
            seats.put(abs, Double.doubleToRawLongBits(seat));
            SlabAnchorAttachment.writePlacementDyBatch(world, seats);
        }
    }

    private static BlockState wireState(TestContext ctx, BlockPos abs) {
        BlockState state = ctx.getWorld().getBlockState(abs);
        if (!(state.getBlock() instanceof RedstoneWireBlock)) {
            throw failure("premise: expected a wire at " + abs + ", found " + state);
        }
        return state;
    }

    private static void expectPower(TestContext ctx, BlockPos abs, int expected, String why) {
        int power = wireState(ctx, abs).get(RedstoneWireBlock.POWER);
        if (power != expected) {
            throw failure("the wire at " + abs + " must carry power " + expected + ", got " + power
                    + ": " + why);
        }
    }

    private static void expectSide(TestContext ctx, BlockPos abs, Direction toward, WireConnection expected, String why) {
        WireConnection side = wireState(ctx, abs).get(RedstoneWireBlock.DIRECTION_TO_WIRE_CONNECTION_PROPERTY.get(toward));
        if (side != expected) {
            throw failure("the wire at " + abs + " must connect " + expected + " toward " + toward
                    + ", got " + side + ": " + why);
        }
    }

    /**
     * Reader on stone at x=2 (row 3) with a branch to its north; a bottom slab at x=3 row 3 with the
     * lowered wire at row 4.
     */
    private static BlockPos[] halfStep(TestContext ctx, double readerSeat) {
        floor(ctx, 0, 5);
        if (readerSeat < 0.0d) {
            slab(ctx, 2, 2, bottomSlab(), FLUSH);
        } else {
            stone(ctx, 2, 2);
        }
        BlockPos reader = wire(ctx, 2, 3, readerSeat);
        branchNorth(ctx, 2, 3);
        stone(ctx, 3, 2);
        slab(ctx, 3, 3, bottomSlab(), FLUSH);
        BlockPos lowered = wire(ctx, 3, 4, LOWERED);
        expectSide(ctx, reader, Direction.NORTH, WireConnection.SIDE, "premise: the branch switches the line rule off");
        expectSide(ctx, reader, Direction.WEST, WireConnection.NONE, "premise: nothing west of the reader");
        return new BlockPos[] {reader, lowered};
    }

    // ── row 1: the wire beside the slab reads the wire drawn on it ───────────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aWireBesideAHalfStepReadsTheWireDrawnOnTheSlab(TestContext ctx) {
        withFrozen(() -> {
            BlockPos[] wires = halfStep(ctx, FLUSH);
            expectPower(ctx, wires[0], 0, "nothing feeds either wire yet");
            stone(ctx, 4, 3);
            source(ctx, 4, 4);
            expectPower(ctx, wires[1], 15, "premise: the redstone block feeds the lowered wire");
            expectPower(ctx, wires[0], 14, "the wire beside the slab reads the wire drawn half a block above it");
            expectSide(ctx, wires[0], Direction.EAST, WireConnection.SIDE, "the reader's arm reaches the slab");
            expectSide(ctx, wires[1], Direction.WEST, WireConnection.SIDE, "vanilla's own step-down arm");
        });
        ctx.complete();
    }

    // ── row 2: a wire on a top slab is drawn in its own cell — vanilla's one-way step is kept ────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aWireOnATopSlabKeepsVanillasOneWayStep(TestContext ctx) {
        withFrozen(() -> {
            floor(ctx, 0, 5);
            stone(ctx, 2, 2);
            BlockPos reader = wire(ctx, 2, 3, FLUSH);
            stone(ctx, 3, 2);
            slab(ctx, 3, 3, topSlab(), FLUSH);
            BlockPos upper = wire(ctx, 3, 4, FLUSH);
            stone(ctx, 4, 3);
            source(ctx, 4, 4);
            expectPower(ctx, upper, 15, "premise: the redstone block feeds the wire on the top slab");
            expectPower(ctx, reader, 0, "a wire on a top slab is a full step up and drawn in its own cell:"
                    + " vanilla never steps up onto a non-conductor, and that stays");
        });
        ctx.complete();
    }

    // ── row 3: power still climbs the half-step, as it always did ───────────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void powerStillClimbsTheHalfStep(TestContext ctx) {
        withFrozen(() -> {
            BlockPos[] wires = halfStep(ctx, FLUSH);
            stone(ctx, 1, 2);
            source(ctx, 1, 3);
            expectPower(ctx, wires[0], 15, "premise: the redstone block feeds the reader");
            expectPower(ctx, wires[1], 14, "the lowered wire reads down past the air over the reader, as in vanilla");
        });
        ctx.complete();
    }

    // ── row 4: the read follows the source and the lowered wire away again ──────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void theReadFollowsTheSourceAndTheLoweredWireAway(TestContext ctx) {
        withFrozen(() -> {
            BlockPos[] wires = halfStep(ctx, FLUSH);
            stone(ctx, 4, 3);
            source(ctx, 4, 4);
            expectPower(ctx, wires[0], 14, "premise: the reader reads the lowered wire");
            expectSide(ctx, wires[0], Direction.EAST, WireConnection.SIDE, "premise: the reader's arm reaches the slab");
            ctx.setBlockState(new BlockPos(4, 4, Z), Blocks.AIR.getDefaultState());
            expectPower(ctx, wires[1], 0, "premise: the source is gone");
            expectPower(ctx, wires[0], 0, "the reader follows the lowered wire down to zero");
            source(ctx, 4, 4);
            expectPower(ctx, wires[0], 14, "and back up when the source returns");
            ctx.setBlockState(new BlockPos(3, 4, Z), Blocks.AIR.getDefaultState());
            expectPower(ctx, wires[0], 0, "the lowered wire is gone");
            expectSide(ctx, wires[0], Direction.EAST, WireConnection.NONE, "nothing is drawn beside the reader any more");
            expectSide(ctx, wires[0], Direction.NORTH, WireConnection.SIDE, "the branch is still there");
        });
        ctx.complete();
    }

    /** Four wires descending in half-steps from x=1 (row 3, flush) to x=4 (row 2, lowered). */
    private static BlockPos[] staircase(TestContext ctx) {
        floor(ctx, 0, 6);
        stone(ctx, 1, 2);
        BlockPos first = wire(ctx, 1, 3, FLUSH);
        slab(ctx, 2, 2, bottomSlab(), FLUSH);
        BlockPos second = wire(ctx, 2, 3, LOWERED);
        BlockPos third = wire(ctx, 3, 2, FLUSH);
        branchNorth(ctx, 3, 2);
        slab(ctx, 4, 1, bottomSlab(), FLUSH);
        BlockPos fourth = wire(ctx, 4, 2, LOWERED);
        expectSide(ctx, third, Direction.NORTH, WireConnection.SIDE, "premise: the branch switches the line rule off");
        return new BlockPos[] {first, second, third, fourth};
    }

    // ── rows 5 and 6: a staircase of half-steps carries power down as well as up ────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aStaircaseOfHalfStepsCarriesPowerDown(TestContext ctx) {
        withFrozen(() -> {
            BlockPos[] wires = staircase(ctx);
            stone(ctx, 0, 2);
            source(ctx, 0, 3);
            expectPower(ctx, wires[0], 15, "premise: the top of the stairs is fed");
            expectPower(ctx, wires[1], 14, "flat onto the first slab");
            expectPower(ctx, wires[2], 13, "down the half-step: the wire below reads the wire drawn on the slab");
            expectPower(ctx, wires[3], 12, "flat onto the second slab");
            expectSide(ctx, wires[2], Direction.WEST, WireConnection.SIDE, "the arm below the half-step reaches the slab");
        });
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aStaircaseOfHalfStepsCarriesPowerUp(TestContext ctx) {
        withFrozen(() -> {
            BlockPos[] wires = staircase(ctx);
            source(ctx, 5, 2);
            expectPower(ctx, wires[3], 15, "premise: the foot of the stairs is fed");
            expectPower(ctx, wires[2], 14, "flat off the second slab");
            expectPower(ctx, wires[1], 13, "up the half-step, as vanilla always allowed");
            expectPower(ctx, wires[0], 12, "flat off the first slab");
        });
        ctx.complete();
    }

    // ── row 7: a wire lowered a whole block, drawn level with the reader, connects flat ─────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aWireLoweredAWholeBlockConnectsFlatWithTheWireBesideIt(TestContext ctx) {
        withFrozen(() -> {
            floor(ctx, 0, 5);
            stone(ctx, 2, 2);
            BlockPos reader = wire(ctx, 2, 3, FLUSH);
            branchNorth(ctx, 2, 3);
            slab(ctx, 3, 2, bottomSlab(), FLUSH);
            slab(ctx, 3, 3, bottomSlab(), LOWERED);
            BlockPos lowered = wire(ctx, 3, 4, LOWERED_TWICE);
            expectSide(ctx, reader, Direction.NORTH, WireConnection.SIDE, "premise: the branch switches the line rule off");
            stone(ctx, 4, 3);
            source(ctx, 4, 4);
            expectPower(ctx, lowered, 15, "premise: the redstone block feeds the lowered wire");
            expectPower(ctx, reader, 14, "a wire drawn level with the reader is read like a wire in that cell");
            expectSide(ctx, reader, Direction.EAST, WireConnection.SIDE, "the reader's arm reaches the slab");
        });
        ctx.complete();
    }

    // ── row 8: two lowered wires a whole drawn block apart connect like a vanilla full step ─────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void twoLoweredWiresAWholeBlockApartConnectLikeAFullStep(TestContext ctx) {
        withFrozen(() -> {
            BlockPos[] wires = halfStep(ctx, LOWERED);
            stone(ctx, 4, 3);
            source(ctx, 4, 4);
            expectPower(ctx, wires[1], 15, "premise: the redstone block feeds the upper lowered wire");
            expectPower(ctx, wires[0], 14, "a whole drawn block is vanilla's own step, so the lower wire reads it");
            expectSide(ctx, wires[0], Direction.EAST, WireConnection.SIDE, "the lower wire's arm reaches the slab");
        });
        ctx.complete();
    }

    // ── row 9: a wire drawn a block and a half below the lowered wire does not read it ──────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aWireDrawnMoreThanABlockBelowDoesNotReadTheLoweredWire(TestContext ctx) {
        withFrozen(() -> {
            floor(ctx, 0, 5);
            slab(ctx, 2, 1, bottomSlab(), FLUSH);
            slab(ctx, 2, 2, bottomSlab(), LOWERED);
            BlockPos reader = wire(ctx, 2, 3, LOWERED_TWICE);
            stone(ctx, 3, 2);
            slab(ctx, 3, 3, bottomSlab(), FLUSH);
            BlockPos lowered = wire(ctx, 3, 4, LOWERED);
            stone(ctx, 4, 3);
            source(ctx, 4, 4);
            expectPower(ctx, lowered, 15, "premise: the redstone block feeds the lowered wire");
            expectPower(ctx, reader, 0, "a wire drawn a block and a half below the lowered wire is out of reach:"
                    + " vanilla's own step is one block");
            expectPower(ctx, lowered, 15, "and the lowered wire is not disturbed by the wire below it");
        });
        ctx.complete();
    }
}
