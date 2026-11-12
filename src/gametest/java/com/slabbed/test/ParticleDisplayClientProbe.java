package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.particle.BlockDisplayParticleContext;
import com.slabbed.util.SlabSupport;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import java.util.*;

/** Exercises the real client dispatcher and final particle renderer sink. */
public final class ParticleDisplayClientProbe implements ClientModInitializer {
    private static final double[] HEIGHTS = {0, -0.5, -3};
    private static final BlockState[] STATES = {
        Blocks.STONE.getDefaultState(), Blocks.TORCH.getDefaultState(),
        Blocks.WALL_TORCH.getDefaultState(), Blocks.SOUL_TORCH.getDefaultState(),
        Blocks.REDSTONE_TORCH.getDefaultState().with(Properties.LIT, true),
        Blocks.CANDLE.getDefaultState().with(Properties.LIT, true),
        Blocks.CAMPFIRE.getDefaultState().with(Properties.LIT, true),
        Blocks.SOUL_CAMPFIRE.getDefaultState().with(Properties.LIT, true),
        Blocks.END_ROD.getDefaultState()
    };
    private int ticks, phase;
    private String successSummary;
    private boolean requested;
    private volatile boolean prepared;
    private volatile Throwable failure;
    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("slabbed.particleClientProbe")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try { tick(client); }
            catch (Throwable error) {
                error.printStackTrace();
                System.err.println("[PARTICLE_LAZY] RED " + error);
                phase = 99; client.scheduleStop();
            }
        });
    }
    private static BlockPos pos(int type, int height) { return new BlockPos(4 + type * 2, 120, 4 + height * 3); }
    private void tick(MinecraftClient client) throws Exception {
        if (phase == 99) return;
        if (++ticks > 2400) throw new AssertionError("client proof timeout");
        if (failure != null) throw new AssertionError("fixture failed", failure);
        if (!requested && client.isFinishedLoading()) {
            if (client.world != null) throw new AssertionError("existing world is protected");
            requested = true;
            client.createIntegratedServerLoader().createAndStart("particle-lazy-" + Long.toUnsignedString(System.nanoTime()),
                new LevelInfo("Particle Display Proof", GameMode.CREATIVE, false, Difficulty.PEACEFUL,
                    true, new GameRules(), DataConfiguration.SAFE_MODE), new GeneratorOptions(0L,false,false),
                registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(), null);
            return;
        }
        if (client.world == null || client.player == null || client.getServer() == null) return;
        client.options.pauseOnLostFocus = false;
        if (client.currentScreen != null) client.setScreen(null);
        client.player.getAbilities().flying = true;
        client.player.refreshPositionAndAngles(12, 120, -4, 0, 12);
        if (phase == 0) {
            phase = 1;
            client.getServer().execute(() -> {
                try {
                    var world = client.getServer().getOverworld();
                    var player = world.getPlayers().getFirst();
                    player.teleport(world, 12, 120, -4, 0, 12);
                    Map<BlockPos,Long> heights = new LinkedHashMap<>();
                    for (int type=0;type<STATES.length;type++) for (int height=0;height<HEIGHTS.length;height++) {
                        BlockPos pos = pos(type,height);
                        world.setBlockState(pos.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                        world.setBlockState(pos.south(), Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                        world.setBlockState(pos, STATES[type], Block.NOTIFY_LISTENERS);
                        heights.put(pos,Double.doubleToRawLongBits(HEIGHTS[height]));
                    }
                    SlabAnchorAttachment.writePlacementDyBatch(world,heights);
                    prepared = true;
                } catch (Throwable error) { failure = error; }
            });
            return;
        }
        if (!prepared) return;
        for (int type=0;type<STATES.length;type++) for (int height=0;height<HEIGHTS.length;height++) {
            var p=pos(type,height);
            if (!client.world.getBlockState(p).equals(STATES[type])
                || SlabSupport.getYOffset(client.world,p,STATES[type]) != HEIGHTS[height]) return;
        }
        if (phase == 1) {
            int totalParticles=0, totalLookups=0;
            for (int type=0;type<STATES.length;type++) {
                List<ParticleSinkSamples.Sample> flat = null;
                for (int height=0;height<HEIGHTS.length;height++) {
                    BlockPos owner=pos(type,height);
                    List<ParticleSinkSamples.Sample> samples=new ArrayList<>();
                    int lookups=0, emittingTicks=0;
                    int iterations=type==0?1334:256;
                    for (int seed=0;seed<iterations;seed++) {
                        long mixedSeed = seed * 0x9E3779B97F4A7C15L;
                        client.world.random.setSeed(mixedSeed);
                        ParticleSinkSamples.begin(); ParticleOffsetLookupProbe.begin();
                        ParticleSinkAudit.reset();
                        List<ParticleSinkSamples.Sample> emitted;
                        int count;
                        try {
                            client.world.randomBlockDisplayTick(owner.getX(),owner.getY(),owner.getZ(),
                                1, Random.create(mixedSeed), Blocks.AIR,new BlockPos.Mutable());
                            count=ParticleOffsetLookupProbe.count();
                        } finally { ParticleOffsetLookupProbe.end(); emitted=ParticleSinkSamples.finish(); }
                        if (count != (emitted.isEmpty()?0:1))
                            throw new AssertionError("lookup count type="+type+" height="+height+" seed="+seed+" count="+count+" emitted="+emitted.size());
                        if (!emitted.isEmpty()) {
                            emittingTicks++;
                            if (height>0 && !ParticleSinkAudit.snapshot(owner).exactOnceGreen())
                                throw new AssertionError("sink not translated exactly once");
                        }
                        if (BlockDisplayParticleContext.isActive()) throw new AssertionError("leaked scope");
                        lookups+=count; samples.addAll(emitted);
                    }
                    if ((type==0 || type==7) && (!samples.isEmpty()||lookups!=0)) throw new AssertionError("silent block work");
                    // Soul campfire display ticks do not emit the regular campfire lava particle.
                    if (type>0 && type!=7 && samples.isEmpty()) throw new AssertionError("emitter silent type="+type);
                    if (height==0) flat=samples;
                    else compare(flat,samples,pos(type,0),owner,HEIGHTS[height]);
                    totalParticles+=samples.size();totalLookups+=lookups;
                    System.out.println("[PARTICLE_LAZY] ROW type="+type+" dy="+HEIGHTS[height]+" ticks="+iterations
                        +" emittingTicks="+emittingTicks+" lookups="+lookups+" particles="+samples.size()+" exact=true");
                }
            }
            successSummary = "[PARTICLE_LAZY] GREEN rows="+(STATES.length*HEIGHTS.length)
                +" particles="+totalParticles+" lookups="+totalLookups+" silentTicks=4002";
            phase=2; return;
        }
        if (phase==2) {
            try (var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                image.writeTo(client.runDirectory.toPath().resolve("particle-proof.png"));
            }
            System.out.println(successSummary);
            phase=99;client.scheduleStop();
        }
    }
    private static void compare(List<ParticleSinkSamples.Sample> flat,List<ParticleSinkSamples.Sample> lowered,
                                BlockPos flatPos,BlockPos pos,double dy) {
        if (flat.size()!=lowered.size()) throw new AssertionError("particle count changed");
        for(int i=0;i<flat.size();i++) {
            var a=flat.get(i);var b=lowered.get(i);
            if (a.type()!=b.type()||a.important()!=b.important()||a.alwaysSpawn()!=b.alwaysSpawn()
                || Math.abs((b.y()-a.y())-dy)>1e-9 || Math.abs((b.x()-a.x())-(pos.getX()-flatPos.getX()))>1e-9
                || Math.abs((b.z()-a.z())-(pos.getZ()-flatPos.getZ()))>1e-9
                || a.vx()!=b.vx()||a.vy()!=b.vy()||a.vz()!=b.vz()) throw new AssertionError("particle payload changed index="+i);
        }
    }
}
