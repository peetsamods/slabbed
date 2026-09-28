package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A powered rail seated on a slab is powered by what visibly touches it (maintainer ruling,
 * 2026-09-28) — {@link com.slabbed.util.RailVisualSignal} and its two hooks.
 *
 * <p>The scene is always the same column: a floor, a support at row 2, the rail at row 3. On a
 * bottom slab the rail is drawn inside row 2, so a source standing on the floor beside the slab
 * touches it; on a full block the rail is drawn in row 3 and that same source is a diagonal, as in
 * vanilla. Seats are AUTHORED into the placement-height store and read with the store forced ON (this
 * venue runs it off), the way the line's other stored-height rows do; every rail checks its seat as
 * a premise.
 *
 * <p>MUTATIONS that must redden this class: withhold {@code PoweredRailVisualSignalMixin} (rows 1,
 * 4, 5, 7, 8, 10), hook only the rail's own update and not the chain check (rows 7, 8), or withhold
 * {@code LoweredRailSupportNeighborMixin} (row 5, the lever). The cart rows also pin the ride-along
 * in {@code MinecartPhysicalOffsetMixin}: make its drawn seat ignore the cart's place along the rail
 * (rows 8, 11), or run the collision sweep at the bound seat instead of the top of the drawn profile
 * (row 8).
 */
public final class RailVisualSignalTest {

    private static final double EPS = 1.0e-6d;
    private static final double LOWERED = -0.5d;
    private static final double RAIL_LIFT = 0.0625d;
    private static final int FLOOR_Y = 1;
    private static final int SUPPORT_Y = 2;
    private static final int RAIL_Y = 3;

    private interface FrozenBody {
        void run();
    }

    /** Reads with the store ON, the way a player-placed rail reads. */
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

    private static void floor(TestContext ctx, int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ctx.setBlockState(new BlockPos(x, FLOOR_Y, z), Blocks.STONE.getDefaultState());
            }
        }
    }

    /**
     * A powered rail at row 3 over a support at row 2: a bottom slab (the rail is seated -0.5 and
     * drawn inside the slab's cell) or stone (the rail is flush, drawn in its own cell).
     *
     * @return the ABSOLUTE rail position
     */
    private static BlockPos poweredRail(TestContext ctx, int x, int z, boolean onSlab) {
        ServerWorld world = ctx.getWorld();
        BlockPos support = new BlockPos(x, SUPPORT_Y, z);
        BlockPos rail = new BlockPos(x, RAIL_Y, z);
        ctx.setBlockState(support, onSlab
                ? Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM)
                : Blocks.STONE.getDefaultState());
        ctx.setBlockState(rail, Blocks.POWERED_RAIL.getDefaultState());
        BlockPos abs = ctx.getAbsolutePos(rail);
        if (onSlab) {
            Map<BlockPos, Long> seats = new LinkedHashMap<>();
            seats.put(abs, Double.doubleToRawLongBits(LOWERED));
            SlabAnchorAttachment.writePlacementDyBatch(world, seats);
        }
        double seat = SlabSupport.getYOffset(world, abs, world.getBlockState(abs));
        double expected = onSlab ? LOWERED : 0.0d;
        if (Math.abs(seat - expected) > EPS) {
            throw failure("premise: the rail at " + abs + " must read seat " + expected + ", got " + seat);
        }
        return abs;
    }

    private static boolean powered(TestContext ctx, BlockPos abs) {
        BlockState state = ctx.getWorld().getBlockState(abs);
        if (!(state.getBlock() instanceof PoweredRailBlock)) {
            throw failure("premise: expected a powered rail at " + abs + ", found " + state);
        }
        return state.get(PoweredRailBlock.POWERED);
    }

    private static void expectPowered(TestContext ctx, BlockPos abs, boolean expected, String why) {
        if (powered(ctx, abs) != expected) {
            throw failure("the rail at " + abs + " must be " + (expected ? "powered" : "unpowered") + ": " + why);
        }
    }

    private static void torchOnFloor(TestContext ctx, int x, int z) {
        ctx.setBlockState(new BlockPos(x, SUPPORT_Y, z), Blocks.REDSTONE_TORCH.getDefaultState());
    }

    // ── row 1: a torch standing beside the slab powers the rail drawn inside that slab's cell ────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aTorchBesideTheSlabPowersTheLoweredRail(TestContext ctx) {
        withFrozen(() -> {
            floor(ctx, 1, 4, 1, 3);
            BlockPos rail = poweredRail(ctx, 2, 2, true);
            expectPowered(ctx, rail, false, "nothing feeds it yet");
            torchOnFloor(ctx, 3, 2);
            expectPowered(ctx, rail, true, "a torch beside the slab touches the drawn rail");
        });
        ctx.complete();
    }

    // ── row 10: a torch under the slab powers the rail drawn on it, as under a full block ────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aTorchUnderTheSlabPowersTheLoweredRail(TestContext ctx) {
        withFrozen(() -> {
            floor(ctx, 1, 4, 1, 3);
            ctx.setBlockState(new BlockPos(2, 0, 2), Blocks.STONE.getDefaultState());
            BlockPos rail = poweredRail(ctx, 2, 2, true);
            expectPowered(ctx, rail, false, "nothing feeds it yet");
            ctx.setBlockState(new BlockPos(2, FLOOR_Y, 2), Blocks.REDSTONE_TORCH.getDefaultState());
            expectPowered(ctx, rail, true, "a torch under the slab touches the drawn rail from below");
            ctx.setBlockState(new BlockPos(2, FLOOR_Y, 2), Blocks.AIR.getDefaultState());
            expectPowered(ctx, rail, false, "the torch is gone");
        });
        ctx.complete();
    }

    // ── row 2: a flush rail keeps vanilla's reach — the same torch is a diagonal to it ───────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aFlushRailKeepsVanillaReach(TestContext ctx) {
        withFrozen(() -> {
            floor(ctx, 1, 4, 1, 3);
            BlockPos rail = poweredRail(ctx, 2, 2, false);
            torchOnFloor(ctx, 3, 2);
            expectPowered(ctx, rail, false, "a torch one row below a flush rail is a diagonal, as in vanilla");
        });
        ctx.complete();
    }

    // ── row 3: a source at the rail's own grid row still powers a lowered rail ──────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void theRailsOwnRowStillPowersALoweredRail(TestContext ctx) {
        withFrozen(() -> {
            floor(ctx, 1, 4, 1, 3);
            BlockPos rail = poweredRail(ctx, 2, 2, true);
            ctx.setBlockState(new BlockPos(3, SUPPORT_Y, 2), Blocks.STONE.getDefaultState());
            ctx.setBlockState(new BlockPos(3, RAIL_Y, 2), Blocks.REDSTONE_TORCH.getDefaultState());
            expectPowered(ctx, rail, true, "vanilla's own row is kept");
        });
        ctx.complete();
    }

    // ── row 4: removing the torch unpowers the rail again ────────────────────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void removingTheTorchUnpowersTheLoweredRail(TestContext ctx) {
        withFrozen(() -> {
            floor(ctx, 1, 4, 1, 3);
            BlockPos rail = poweredRail(ctx, 2, 2, true);
            torchOnFloor(ctx, 3, 2);
            expectPowered(ctx, rail, true, "premise: the torch powers it first");
            ctx.setBlockState(new BlockPos(3, SUPPORT_Y, 2), Blocks.AIR.getDefaultState());
            expectPowered(ctx, rail, false, "the torch is gone");
        });
        ctx.complete();
    }

    // ── row 5: a lever beside the slab reaches the rail — the slab's notification is forwarded ───

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aLeverBesideTheSlabReachesTheLoweredRail(TestContext ctx) {
        withFrozen(() -> {
            ServerWorld world = ctx.getWorld();
            floor(ctx, 1, 4, 1, 3);
            BlockPos rail = poweredRail(ctx, 2, 2, true);
            BlockPos leverRel = new BlockPos(3, SUPPORT_Y, 2);
            ctx.setBlockState(leverRel, Blocks.LEVER.getDefaultState()
                    .with(LeverBlock.FACE, BlockFace.FLOOR)
                    .with(LeverBlock.FACING, Direction.NORTH));
            BlockPos lever = ctx.getAbsolutePos(leverRel);
            expectPowered(ctx, rail, false, "the lever is off");
            ((LeverBlock) Blocks.LEVER).togglePower(world.getBlockState(lever), world, lever, null);
            expectPowered(ctx, rail, true, "the lever beside the slab was pulled on");
            ((LeverBlock) Blocks.LEVER).togglePower(world.getBlockState(lever), world, lever, null);
            expectPowered(ctx, rail, false, "the lever beside the slab was pulled off again");
        });
        ctx.complete();
    }

    /** Five powered rails in one row, x = 1..5, the middle one on a slab, the rest on stone. */
    private static BlockPos[] chain(TestContext ctx) {
        floor(ctx, 0, 6, 1, 3);
        BlockPos[] rails = new BlockPos[5];
        for (int i = 0; i < 5; i++) {
            rails[i] = poweredRail(ctx, 1 + i, 2, i == 2);
        }
        return rails;
    }

    // ── row 6: a chain fed at one end still crosses the lowered rail ─────────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aPoweredChainCrossesALoweredRail(TestContext ctx) {
        withFrozen(() -> {
            BlockPos[] rails = chain(ctx);
            ctx.setBlockState(new BlockPos(0, RAIL_Y, 2), Blocks.REDSTONE_BLOCK.getDefaultState());
            for (int i = 0; i < 5; i++) {
                expectPowered(ctx, rails[i], true, "the chain runs through the lowered rail");
            }
        });
        ctx.complete();
    }

    // ── row 7: a lowered rail fed beside its slab feeds its whole chain ──────────────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aLoweredRailFedBesideItsSlabFeedsItsChain(TestContext ctx) {
        withFrozen(() -> {
            BlockPos[] rails = chain(ctx);
            torchOnFloor(ctx, 3, 1);
            for (int i = 0; i < 5; i++) {
                expectPowered(ctx, rails[i], true, "rail " + i + " is chained to the lowered rail the torch feeds");
            }
        });
        ctx.complete();
    }

    // ── row 8: a cart rolls through; without the feed the lowered rail brakes it ─────────────────

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aCartRollsThroughALoweredPoweredRail(TestContext ctx) {
        withFrozen(() -> {
            BlockPos[] rails = chain(ctx);
            torchOnFloor(ctx, 3, 1);
            double reached = roll(ctx, rails[0]);
            if (reached < rails[4].getX() + 0.3d) {
                throw failure("a cart on a chain fed beside the lowered rail must roll past the"
                        + " last rail (x >= " + (rails[4].getX() + 0.3d) + "), got x " + reached);
            }
        });
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void withoutTheFeedTheLoweredRailBrakesTheCart(TestContext ctx) {
        withFrozen(() -> {
            BlockPos[] rails = chain(ctx);
            for (int i = 0; i < 5; i++) {
                expectPowered(ctx, rails[i], false, "premise: nothing feeds the chain");
            }
            double reached = roll(ctx, rails[0]);
            if (reached >= rails[4].getX()) {
                throw failure("premise: an unpowered chain must brake the cart before the last"
                        + " rail, but it reached x " + reached + " — the control cannot discriminate");
            }
        });
        ctx.complete();
    }

    // ── row 11: a cart rides the drawn V of a lowered rail between two flush rails ──────────────

    /**
     * At the middle of the lowered rail the cart sits on the slab; near either edge it is nearly
     * flush. The seat follows the fitted slope at the cart's place along the rail, not one number per
     * cell (maintainer ruling, 2026-09-28). At rest on an unpowered chain, so nothing but the seat
     * moves it.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aCartAtRestRidesTheDrawnVBetweenFlushRails(TestContext ctx) {
        withFrozen(() -> {
            ServerWorld world = ctx.getWorld();
            BlockPos[] rails = chain(ctx);
            BlockPos dip = rails[2];
            AbstractMinecartEntity cart = AbstractMinecartEntity.create(world,
                    dip.getX() + 0.5d, dip.getY() + RAIL_LIFT, dip.getZ() + 0.5d,
                    AbstractMinecartEntity.Type.RIDEABLE, ItemStack.EMPTY, null);
            if (cart == null) {
                throw failure("premise: could not create a minecart");
            }
            world.spawnEntity(cart);
            try {
                double base = dip.getY() + RAIL_LIFT;
                double[][] places = {{0.5d, LOWERED}, {0.1d, LOWERED + 0.4d}, {0.9d, LOWERED + 0.4d},
                        {0.3d, LOWERED + 0.2d}};
                for (double[] place : places) {
                    double x = dip.getX() + place[0];
                    cart.setPosition(x, cart.getY(), cart.getZ());
                    cart.setVelocity(Vec3d.ZERO);
                    cart.tick();
                    if (Math.abs(cart.getX() - x) > 1.0e-3d) {
                        throw failure("premise: a cart at rest must stay at x " + x + ", got " + cart.getX());
                    }
                    double expected = base + place[1];
                    if (Math.abs(cart.getY() - expected) > EPS) {
                        throw failure("at " + place[0] + " along the lowered rail the cart must ride the drawn"
                                + " V at y " + expected + ", got " + cart.getY());
                    }
                }
            } finally {
                cart.discard();
            }
        });
        ctx.complete();
    }

    /** Spawns a cart on {@code start} rolling east and ticks it by hand; returns the farthest x reached. */
    private static double roll(TestContext ctx, BlockPos start) {
        ServerWorld world = ctx.getWorld();
        AbstractMinecartEntity cart = AbstractMinecartEntity.create(world,
                start.getX() + 0.5d, start.getY() + RAIL_LIFT, start.getZ() + 0.5d,
                AbstractMinecartEntity.Type.RIDEABLE, ItemStack.EMPTY, null);
        if (cart == null) {
            throw failure("premise: could not create a minecart");
        }
        world.spawnEntity(cart);
        cart.setVelocity(new Vec3d(0.3d, 0.0d, 0.0d));
        double farthest = cart.getX();
        for (int tick = 0; tick < 60; tick++) {
            cart.tick();
            farthest = Math.max(farthest, cart.getX());
            if (farthest > start.getX() + 5.5d) {
                break;
            }
        }
        cart.discard();
        return farthest;
    }
}
