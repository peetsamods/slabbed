package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.test.support.TestConnections;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** A terrain particle in the vacated band must not sample an invisible opaque grid cell. */
public final class LoweredParticleLightClientGameTest implements FabricClientGameTest {
    private static final class LightProbe extends TerrainParticle {
        LightProbe(ClientLevel level, BlockPos pos) {
            super(level, pos.getX()+.5, pos.getY()+.75, pos.getZ()+.5, 0, 0, 0,
                    Blocks.STONE.defaultBlockState());
            lifetime = 200;
            gravity = 0;
            hasPhysics = false;
            scale(3);
        }
        int sample() { return getLightCoords(0); }
    }
    @Override public void runTest(ClientGameTestContext ctx) {
        try (var world = ctx.worldBuilder().setUseConsistentSettings(true).create()) {
            TestConnections.waitForChunksDownload(world);
            BlockPos base = world.getServer().computeOnServer(s -> {
                var level = s.overworld();
                BlockPos p = s.getPlayerList().getPlayers().getFirst().blockPosition().above(12).immutable();
                for (BlockPos q : BlockPos.betweenClosed(p.offset(-3,-4,-3),p.offset(9,4,3)))
                    level.setBlock(q,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
                level.setBlock(p,Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
                SlabAnchorAttachment.writePlacementDy(level,p,-3);
                level.setBlock(p.east(3),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
                SlabAnchorAttachment.writePlacementDy(level,p.east(3),0);
                level.setBlock(p.east(9),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
                SlabAnchorAttachment.writePlacementDy(level,p.east(9),-3);
                level.setBlock(p.east(9).above(),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
                return p;
            });
            ctx.waitFor(c -> c.level != null && c.level.getBlockState(base).is(Blocks.STONE)
                    && SlabAnchorAttachment.storedPlacementDy(c.level,base)==-3,200);
            ctx.waitTicks(40);
            ctx.runOnClient(c -> {
                int lowered = new LightProbe(c.level,base).sample();
                int flat = new LightProbe(c.level,base.east(3)).sample();
                int air = new LightProbe(c.level,base.east(6)).sample();
                int roof = new LightProbe(c.level,base.east(9)).sample();
                Slabbed.LOGGER.info("TerrainParticle lighting lowered={} flat={} air={} roof={}",lowered,flat,air,roof);
                if ((air & 0xF00000)==0 || (flat & 0xF00000)!=0 || (roof & 0xF00000)!=0)
                    throw new AssertionError("particle lighting control fixture invalid");
                if ((lowered & 0xF00000)!=(air & 0xF00000))
                    throw new AssertionError("vacated-band terrain particle samples phantom opacity");
            });
            var feet = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                var at = new net.minecraft.world.phys.Vec3(base.getX()+4.5,
                        base.getY()+.75-player.getEyeHeight(),base.getZ()+10);
                player.teleportTo(at.x,at.y,at.z);
                return at;
            });
            ctx.waitFor(c -> c.player.position().distanceToSqr(feet)<.01,200);
            ctx.runOnClient(c -> {
                c.player.setYRot(180); c.player.setXRot(0);
                for (int dx : new int[] {0,6}) {
                    for (int n=0;n<5;n++) {
                        var particle = new LightProbe(c.level,base.east(dx));
                        particle.setPos(base.getX()+dx+.2+n*.15,base.getY()+.4+n*.12,base.getZ()+.5);
                        c.particleEngine.add(particle);
                    }
                }
            });
            ctx.waitTicks(3);
            ctx.takeScreenshot("lowered-terrain-particle-light");
            Slabbed.LOGGER.info("CLIENT_GAMETEST | LoweredParticleLightClientGameTest | PASS");
        }
    }
}
