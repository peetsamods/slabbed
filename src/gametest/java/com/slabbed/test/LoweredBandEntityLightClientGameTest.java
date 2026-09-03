package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * An entity resting on a lowered block's drawn top is lit like the open air it visibly sits in
 * (maintainer ruling, 2026-09-03). Vanilla samples entity light at the cell containing the light
 * probe; a lowered opaque block keeps its state in that cell, so a cushion or dropped item on its
 * drawn top rendered black (live, 26.3-pre-1). The renderer's packed light for a cushion on a
 * lowered stone must equal the packed light of the same cushion on a flush stone beside it.
 *
 * <p>MUTATION that must redden this test: withhold {@code EntityLightProbeLoweredBandMixin}.
 */
public final class LoweredBandEntityLightClientGameTest implements FabricClientGameTest {

    private static final String CLIENT_GAMETEST_PASS =
            "CLIENT_GAMETEST | LoweredBandEntityLightClientGameTest | PASS";
    private static final double LOWERED_DY = -0.5d;
    private static final double EPSILON = 1.0e-9d;

    private record Fixture(BlockPos loweredStone, BlockPos flushStone, UUID loweredCushion, UUID flushCushion) {
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
                BlockPos origin = player.blockPosition().relative(player.getDirection(), 3).above(2).immutable();
                return buildFixture(level, origin);
            });

            ctx.waitFor(client -> client.level != null
                    && findCushion(client.level, fixture.loweredCushion()) != null
                    && findCushion(client.level, fixture.flushCushion()) != null, 400);
            ctx.waitTicks(5);
            ctx.runOnClient(client -> {
                Cushion lowered = findCushion(client.level, fixture.loweredCushion());
                Cushion flush = findCushion(client.level, fixture.flushCushion());
                int loweredLight = packedLight(client, lowered);
                int flushLight = packedLight(client, flush);
                int loweredSky = LightCoordsUtil.sky(loweredLight);
                int flushSky = LightCoordsUtil.sky(flushLight);
                if (flushSky == 0) {
                    throw new AssertionError("control: a cushion on a flush stone in the open must see sky light, got "
                            + describe(flushLight));
                }
                if (loweredLight != flushLight) {
                    throw new AssertionError("an entity on a lowered block's drawn top must be lit like the open air"
                            + " above it: lowered=" + describe(loweredLight) + " flush=" + describe(flushLight));
                }
                Slabbed.LOGGER.info(CLIENT_GAMETEST_PASS);
            });
        }
    }

    private static Fixture buildFixture(ServerLevel level, BlockPos origin) {
        BlockPos loweredStone = origin;
        BlockPos flushStone = origin.east(3);
        // Open air around both subjects, a slab under the lowered stone, plain ground under the control.
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-2, -3, -2), origin.offset(5, 4, 2))) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(loweredStone.below(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(loweredStone.below(),
                Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.UPDATE_ALL);
        level.setBlock(loweredStone, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        SlabAnchorAttachment.writePlacementDy(level, loweredStone, LOWERED_DY);
        double dy = SlabSupport.getYOffset(level, loweredStone, level.getBlockState(loweredStone));
        if (Math.abs(dy - LOWERED_DY) > EPSILON) {
            throw new AssertionError("server fixture: lowered stone must read " + LOWERED_DY + ", got " + dy);
        }
        level.setBlock(flushStone.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(flushStone, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        UUID lowered = spawnCushion(level, Vec3.atCenterOfWithY(loweredStone.above(), loweredStone.getY() + 1.0 + LOWERED_DY));
        UUID flush = spawnCushion(level, Vec3.atCenterOfWithY(flushStone.above(), flushStone.getY() + 1.0));
        return new Fixture(loweredStone, flushStone, lowered, flush);
    }

    private static UUID spawnCushion(ServerLevel level, Vec3 pos) {
        Cushion cushion = EntityTypes.CUSHION.create(level, EntitySpawnReason.COMMAND);
        if (cushion == null) {
            throw new AssertionError("could not create a cushion");
        }
        cushion.snapTo(pos, 0.0f, 0.0f);
        if (!cushion.survives()) {
            throw new AssertionError("server fixture: cushion at " + pos + " must survive its own support check");
        }
        level.addFreshEntity(cushion);
        return cushion.getUUID();
    }

    private static Cushion findCushion(net.minecraft.world.level.Level level, UUID id) {
        if (level == null) {
            return null;
        }
        List<Cushion> all = level.getEntitiesOfClass(Cushion.class, new AABB(-3.0e7, -256, -3.0e7, 3.0e7, 512, 3.0e7));
        for (Cushion c : all) {
            if (c.getUUID().equals(id)) {
                return c;
            }
        }
        return null;
    }

    private static int packedLight(Minecraft client, Cushion cushion) {
        var renderer = client.getEntityRenderDispatcher().getRenderer(cushion);
        return renderer.getPackedLightCoords(cushion, 1.0f);
    }

    private static String describe(int packed) {
        return "packed=" + packed + " block=" + LightCoordsUtil.block(packed) + " sky=" + LightCoordsUtil.sky(packed);
    }
}
