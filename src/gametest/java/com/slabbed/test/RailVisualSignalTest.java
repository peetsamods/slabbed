package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

/**
 * A powered rail seated on a slab is powered by what visibly touches it (maintainer ruling,
 * 2026-09-28) — {@link com.slabbed.util.RailVisualSignal} and its two hooks.
 *
 * <p>The scene is always the same column: a floor, a support at row 2, the rail at row 3. On a
 * bottom slab the rail is drawn inside row 2, so a source standing on the floor beside the slab
 * touches it; on a full block the rail is drawn in row 3 and that same source is a diagonal, as in
 * vanilla. Seats are authored and read with the store ON, the shipped configuration.
 *
 * <p>MUTATIONS that must redden this class: withhold {@code PoweredRailVisualSignalMixin} (rows 1,
 * 4, 5, 7, 8, 10), hook only the rail's own update and not the chain check (rows 7, 8), or withhold
 * {@code LoweredRailSupportNeighborMixin} (row 5, the lever).
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

    private static void withFrozen(FrozenBody body) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            body.run();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    private static void floor(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE.defaultBlockState());
            }
        }
    }

    /**
     * A powered rail at row 3 over a support at row 2: a bottom slab (the rail is seated -0.5 and
     * drawn inside the slab's cell) or stone (the rail is flush, drawn in its own cell).
     *
     * @return the ABSOLUTE rail position
     */
    private static BlockPos poweredRail(GameTestHelper helper, int x, int z, boolean onSlab) {
        ServerLevel level = helper.getLevel();
        BlockPos support = new BlockPos(x, SUPPORT_Y, z);
        BlockPos rail = new BlockPos(x, RAIL_Y, z);
        helper.setBlock(support, onSlab
                ? Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                : Blocks.STONE.defaultBlockState());
        helper.setBlock(rail, Blocks.POWERED_RAIL.defaultBlockState());
        BlockPos abs = helper.absolutePos(rail);
        if (onSlab) {
            SlabAnchorAttachment.writePlacementDy(level, abs, LOWERED);
        }
        double seat = SlabSupport.getYOffset(level, abs, level.getBlockState(abs));
        double expected = onSlab ? LOWERED : 0.0d;
        if (Math.abs(seat - expected) > EPS) {
            throw helper.assertionException("premise: the rail at " + abs + " must read seat " + expected
                    + ", got " + seat);
        }
        return abs;
    }

    private static boolean powered(GameTestHelper helper, BlockPos abs) {
        BlockState state = helper.getLevel().getBlockState(abs);
        if (!(state.getBlock() instanceof PoweredRailBlock)) {
            throw helper.assertionException("premise: expected a powered rail at " + abs + ", found " + state);
        }
        return state.getValue(PoweredRailBlock.POWERED);
    }

    private static void expectPowered(GameTestHelper helper, BlockPos abs, boolean expected, String why) {
        if (powered(helper, abs) != expected) {
            throw helper.assertionException("the rail at " + abs + " must be " + (expected ? "powered" : "unpowered")
                    + ": " + why);
        }
    }

    private static void torchOnFloor(GameTestHelper helper, int x, int z) {
        helper.setBlock(new BlockPos(x, SUPPORT_Y, z), Blocks.REDSTONE_TORCH.defaultBlockState());
    }

    // ── row 1: a torch standing beside the slab powers the rail drawn inside that slab's cell ────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aTorchBesideTheSlabPowersTheLoweredRail(GameTestHelper helper) {
        withFrozen(() -> {
            floor(helper, 1, 4, 1, 3);
            BlockPos rail = poweredRail(helper, 2, 2, true);
            expectPowered(helper, rail, false, "nothing feeds it yet");
            torchOnFloor(helper, 3, 2);
            expectPowered(helper, rail, true, "a torch beside the slab touches the drawn rail");
            helper.succeed();
        });
    }

    // ── row 10: a torch under the slab powers the rail drawn on it, as under a full block ────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aTorchUnderTheSlabPowersTheLoweredRail(GameTestHelper helper) {
        withFrozen(() -> {
            floor(helper, 1, 4, 1, 3);
            helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE.defaultBlockState());
            BlockPos rail = poweredRail(helper, 2, 2, true);
            expectPowered(helper, rail, false, "nothing feeds it yet");
            helper.setBlock(new BlockPos(2, FLOOR_Y, 2), Blocks.REDSTONE_TORCH.defaultBlockState());
            expectPowered(helper, rail, true, "a torch under the slab touches the drawn rail from below");
            helper.setBlock(new BlockPos(2, FLOOR_Y, 2), Blocks.AIR.defaultBlockState());
            expectPowered(helper, rail, false, "the torch is gone");
            helper.succeed();
        });
    }

    // ── row 2: a flush rail keeps vanilla's reach — the same torch is a diagonal to it ───────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aFlushRailKeepsVanillaReach(GameTestHelper helper) {
        withFrozen(() -> {
            floor(helper, 1, 4, 1, 3);
            BlockPos rail = poweredRail(helper, 2, 2, false);
            torchOnFloor(helper, 3, 2);
            expectPowered(helper, rail, false, "a torch one row below a flush rail is a diagonal, as in vanilla");
            helper.succeed();
        });
    }

    // ── row 3: a source at the rail's own grid row still powers a lowered rail ──────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void theRailsOwnRowStillPowersALoweredRail(GameTestHelper helper) {
        withFrozen(() -> {
            floor(helper, 1, 4, 1, 3);
            BlockPos rail = poweredRail(helper, 2, 2, true);
            helper.setBlock(new BlockPos(3, SUPPORT_Y, 2), Blocks.STONE.defaultBlockState());
            helper.setBlock(new BlockPos(3, RAIL_Y, 2), Blocks.REDSTONE_TORCH.defaultBlockState());
            expectPowered(helper, rail, true, "vanilla's own row is kept");
            helper.succeed();
        });
    }

    // ── row 4: removing the torch unpowers the rail again ────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void removingTheTorchUnpowersTheLoweredRail(GameTestHelper helper) {
        withFrozen(() -> {
            floor(helper, 1, 4, 1, 3);
            BlockPos rail = poweredRail(helper, 2, 2, true);
            torchOnFloor(helper, 3, 2);
            expectPowered(helper, rail, true, "premise: the torch powers it first");
            helper.setBlock(new BlockPos(3, SUPPORT_Y, 2), Blocks.AIR.defaultBlockState());
            expectPowered(helper, rail, false, "the torch is gone");
            helper.succeed();
        });
    }

    // ── row 5: a lever beside the slab reaches the rail — the slab's notification is forwarded ───

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aLeverBesideTheSlabReachesTheLoweredRail(GameTestHelper helper) {
        withFrozen(() -> {
            ServerLevel level = helper.getLevel();
            floor(helper, 1, 4, 1, 3);
            BlockPos rail = poweredRail(helper, 2, 2, true);
            BlockPos leverRel = new BlockPos(3, SUPPORT_Y, 2);
            helper.setBlock(leverRel, Blocks.LEVER.defaultBlockState()
                    .setValue(LeverBlock.FACE, AttachFace.FLOOR)
                    .setValue(LeverBlock.FACING, Direction.NORTH));
            BlockPos lever = helper.absolutePos(leverRel);
            expectPowered(helper, rail, false, "the lever is off");
            ((LeverBlock) Blocks.LEVER).pull(level.getBlockState(lever), level, lever, null);
            expectPowered(helper, rail, true, "the lever beside the slab was pulled on");
            ((LeverBlock) Blocks.LEVER).pull(level.getBlockState(lever), level, lever, null);
            expectPowered(helper, rail, false, "the lever beside the slab was pulled off again");
            helper.succeed();
        });
    }

    /** Five powered rails in one row, x = 1..5, the middle one on a slab, the rest on stone. */
    private static BlockPos[] chain(GameTestHelper helper) {
        floor(helper, 0, 6, 1, 3);
        BlockPos[] rails = new BlockPos[5];
        for (int i = 0; i < 5; i++) {
            rails[i] = poweredRail(helper, 1 + i, 2, i == 2);
        }
        return rails;
    }

    // ── row 6: a chain fed at one end still crosses the lowered rail ─────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aPoweredChainCrossesALoweredRail(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos[] rails = chain(helper);
            helper.setBlock(new BlockPos(0, RAIL_Y, 2), Blocks.REDSTONE_BLOCK.defaultBlockState());
            for (int i = 0; i < 5; i++) {
                expectPowered(helper, rails[i], true, "the chain runs through the lowered rail");
            }
            helper.succeed();
        });
    }

    // ── row 7: a lowered rail fed beside its slab feeds its whole chain ──────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aLoweredRailFedBesideItsSlabFeedsItsChain(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos[] rails = chain(helper);
            torchOnFloor(helper, 3, 1);
            for (int i = 0; i < 5; i++) {
                expectPowered(helper, rails[i], true, "rail " + i + " is chained to the lowered rail the torch feeds");
            }
            helper.succeed();
        });
    }

    // ── row 8: a cart rolls through; without the feed the lowered rail brakes it ─────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aCartRollsThroughALoweredPoweredRail(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos[] rails = chain(helper);
            torchOnFloor(helper, 3, 1);
            double reached = roll(helper, rails[0]);
            if (reached < rails[4].getX() + 0.3d) {
                throw helper.assertionException("a cart on a chain fed beside the lowered rail must roll past the"
                        + " last rail (x >= " + (rails[4].getX() + 0.3d) + "), got x " + reached);
            }
        });
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void withoutTheFeedTheLoweredRailBrakesTheCart(GameTestHelper helper) {
        withFrozen(() -> {
            BlockPos[] rails = chain(helper);
            for (int i = 0; i < 5; i++) {
                expectPowered(helper, rails[i], false, "premise: nothing feeds the chain");
            }
            double reached = roll(helper, rails[0]);
            if (reached >= rails[4].getX()) {
                throw helper.assertionException("premise: an unpowered chain must brake the cart before the last"
                        + " rail, but it reached x " + reached + " — the control cannot discriminate");
            }
        });
        helper.succeed();
    }

    /** Spawns a cart on {@code start} rolling east and ticks it by hand; returns the farthest x reached. */
    private static double roll(GameTestHelper helper, BlockPos start) {
        ServerLevel level = helper.getLevel();
        AbstractMinecart cart = AbstractMinecart.createMinecart(level,
                start.getX() + 0.5d, start.getY() + RAIL_LIFT, start.getZ() + 0.5d,
                EntityTypes.MINECART, EntitySpawnReason.SPAWN_ITEM_USE, ItemStack.EMPTY, null);
        if (cart == null) {
            throw helper.assertionException("premise: could not create a minecart");
        }
        level.addFreshEntity(cart);
        cart.setDeltaMovement(new Vec3(0.3d, 0.0d, 0.0d));
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
