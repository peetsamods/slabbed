package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.RailSeatDyHolder;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/**
 * The default minecart's rail snap and slope tilt survive a lowered rail.
 *
 * <p>The renderer resolves the rail from the cart's lerped PHYSICAL position, not through the cart's
 * Y accessor, so for a seated cart the raw resolution answers null and the renderer's null guard
 * drops the lateral snap AND the slope tilt: the cart would sit at the right height but off-centre
 * through curves and flat on slopes. Only a real client can show that the conversion is actually
 * wired into the render state rather than merely composable, which is what this row is for — its
 * headless twin proves the conversion, not the wiring.
 *
 * <p>STAGING: waiting a tick is NOT a barrier against the server. This row polls until the client
 * holds the cart, the rail block, AND a non-zero synced seat, and fails on timeout rather than
 * proceeding into a scene that is not there yet.
 *
 * <p>MUTATION that must redden this test: withhold {@code MinecartRendererRailFrameMixin}.
 */
public final class MinecartRenderRailSnapClientGameTest implements FabricClientGameTest {

    private static final String CLIENT_GAMETEST_PASS =
            "CLIENT_GAMETEST | MinecartRenderRailSnapClientGameTest | PASS";
    private static final double LOWERED_DY = -0.5d;
    private static final double RAIL_LIFT = 0.0625d;
    private static final double EPSILON = 1.0e-6d;
    private static final int RUN_LENGTH = 5;

    private record Fixture(BlockPos rail, UUID cart) {
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            singleplayer.getConnection().waitForChunksDownload();
            ctx.waitFor(client -> client.level != null && client.player != null, 400);

            Fixture fixture = singleplayer.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = server.overworld();
                BlockPos origin = player.blockPosition().relative(player.getDirection(), 4).above(2).immutable();
                return buildFixture(level, origin);
            });

            ctx.waitFor(client -> client.level != null
                    && client.level.getBlockState(fixture.rail()).getBlock() instanceof RailBlock
                    && seatedCart(client.level, fixture.cart()) != null, 400);
            ctx.waitTicks(5);

            ctx.runOnClient(client -> {
                AbstractMinecart cart = seatedCart(client.level, fixture.cart());
                if (cart == null) {
                    throw new AssertionError("the client lost the cart before the render state was read");
                }
                if (!(cart.getBehavior() instanceof OldMinecartBehavior)) {
                    throw new AssertionError("premise: this row exercises the DEFAULT rail solver, but the"
                            + " client cart is using " + cart.getBehavior().getClass().getSimpleName());
                }
                double seat = ((RailSeatDyHolder) cart).slabbed$railSeatDy();
                if (Math.abs(seat - LOWERED_DY) > EPSILON) {
                    throw new AssertionError("premise: the client must see the synced seat " + LOWERED_DY
                            + ", got " + seat);
                }

                MinecartRenderState state = extractRenderState(client, cart);
                if (state.posOnRail == null || state.frontPos == null || state.backPos == null) {
                    throw new AssertionError("a cart on a lowered rail must still resolve its rail in the"
                            + " render state — a null here is the lateral snap and the slope tilt both"
                            + " being dropped; posOnRail=" + state.posOnRail + " frontPos=" + state.frontPos
                            + " backPos=" + state.backPos);
                }
                double drawnSeat = fixture.rail().getY() + RAIL_LIFT + LOWERED_DY;
                if (Math.abs(state.posOnRail.y - drawnSeat) > EPSILON) {
                    throw new AssertionError("the snapped rail position must be the drawn seat " + drawnSeat
                            + ", got " + state.posOnRail.y);
                }
                double probeAverage = (state.frontPos.y + state.backPos.y) / 2.0d;
                if (Math.abs(probeAverage - drawnSeat) > EPSILON) {
                    throw new AssertionError("on a flat run the slope probes must average to the drawn seat "
                            + drawnSeat + ", got " + probeAverage);
                }
                Slabbed.LOGGER.info(CLIENT_GAMETEST_PASS);
            });
        }
    }

    private static Fixture buildFixture(ServerLevel level, BlockPos origin) {
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-1, -2, -2), origin.offset(RUN_LENGTH, 3, 2))) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        for (int i = 0; i < RUN_LENGTH; i++) {
            BlockPos rail = origin.east(i);
            level.setBlock(rail.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(rail,
                    Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST),
                    Block.UPDATE_ALL);
            SlabAnchorAttachment.writePlacementDy(level, rail, LOWERED_DY);
        }
        BlockPos seatRail = origin.east(2);
        AbstractMinecart cart = AbstractMinecart.createMinecart(
                level,
                seatRail.getX() + 0.5d, seatRail.getY() + RAIL_LIFT, seatRail.getZ() + 0.5d,
                EntityTypes.MINECART, EntitySpawnReason.SPAWN_ITEM_USE, ItemStack.EMPTY, null);
        if (cart == null) {
            throw new AssertionError("server fixture: could not create a minecart");
        }
        double expected = seatRail.getY() + RAIL_LIFT + LOWERED_DY;
        if (Math.abs(cart.getY() - expected) > EPSILON) {
            throw new AssertionError("server fixture: the cart must be seated at " + expected
                    + " before the client is asked about it, got " + cart.getY());
        }
        level.addFreshEntity(cart);
        return new Fixture(seatRail, cart.getUUID());
    }

    /** The cart, only once the client has BOTH the entity and a bound seat on it. */
    private static AbstractMinecart seatedCart(Level level, UUID id) {
        if (level == null) {
            return null;
        }
        List<AbstractMinecart> carts = level.getEntitiesOfClass(
                AbstractMinecart.class, new AABB(-3.0e7, -256, -3.0e7, 3.0e7, 512, 3.0e7));
        for (AbstractMinecart cart : carts) {
            if (cart.getUUID().equals(id)
                    && Math.abs(((RailSeatDyHolder) cart).slabbed$railSeatDy() - LOWERED_DY) < EPSILON) {
                return cart;
            }
        }
        return null;
    }

    private static MinecartRenderState extractRenderState(Minecraft client, AbstractMinecart cart) {
        var renderer = client.getEntityRenderDispatcher().getRenderer(cart);
        EntityRenderState state = renderer.createRenderState(cart, 1.0f);
        if (!(state instanceof MinecartRenderState minecartState)) {
            throw new AssertionError("the minecart renderer produced " + state.getClass().getSimpleName()
                    + " rather than a minecart render state");
        }
        return minecartState;
    }
}
