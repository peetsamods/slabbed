package com.slabbed.test;

import com.slabbed.util.SlabModelStaleSentinel;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * INSTRUMENT SELF-CHECK (refuter condition 8, "anti-I12"): before any MODEL_STALE red row may be treated
 * as triage evidence, the instrument itself must be validated against a real client on a known-good
 * scene — the /slabdy overlay once double-applied dy to its own display and manufactured a false
 * "client cache staleness" theory that burned three live rounds. Two proofs, both requiring the real
 * renderer (exactly the link lesson S7 says the headless contract suite cannot see):
 *
 * <ol>
 *   <li><b>The feed is alive:</b> a REAL chunk-mesh rebuild of an armed lowered scene must deliver a
 *       bake sample through {@code OffsetBlockStateModel.emitQuads}, and the recorded bakedDy must equal
 *       both the expected geometry (-0.5, the floor-torch lane) and the live logical dy. If the capture
 *       wiring is dead — the "probe silently absent" failure mode that cost an entire campaign with the
 *       recorder — this fails here, in CI, not in Maintainer's session.</li>
 *   <li><b>Zero red rows on a static known-good scene:</b> the sampler judged against the real baked
 *       mesh must stay silent when nothing is wrong.</li>
 * </ol>
 */
public final class ModelStaleSentinelSelfCheckClientGameTest implements FabricClientGameTest {

    private static final double EPS = 1.0e-4;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getClientLevel().waitForChunksRender();

            // Scene cells centered inside one section (local 4..11) so the poke below re-meshes the
            // torch's own section deterministically.
            BlockPos playerPos = ctx.computeOnClient(mc -> mc.player.blockPosition());
            int baseX = (playerPos.getX() & ~15) + 6;
            int baseZ = (playerPos.getZ() & ~15) + 6;
            int baseY = playerPos.getY() + 3;
            if ((baseY & 15) == 15) {
                // Keep support AND torch in the same Y section — otherwise the poke below would re-mesh
                // only the support's section and the torch bake could legitimately never arrive (flake).
                baseY++;
            }
            BlockPos support = new BlockPos(baseX, baseY, baseZ);
            BlockPos torch = support.above();
            BlockPos poke = new BlockPos(baseX + 2, baseY, baseZ);

            // Known-good lowered scene: torch standing on a BOTTOM slab reads dy=-0.5 (the floor-torch
            // lane, pinned by FloorTorchSupportDyTest).
            sp.getServer().runOnServer(server -> {
                var world = server.overworld();
                world.setBlock(support,
                        Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 3);
                world.setBlock(torch, Blocks.TORCH.defaultBlockState(), 3);
            });
            ctx.waitTicks(10);
            sp.getClientLevel().waitForChunksRender();

            List<LinkedHashMap<String, String>> rows = new ArrayList<>();
            try {
                // The test owns the sentinel session (join-grace behavior is the contract suite's job).
                ctx.runOnClient(mc -> {
                    SlabModelStaleSentinel.resetCold();
                    SlabModelStaleSentinel.testSessionOverride = true;
                    SlabModelStaleSentinel.armForTest(mc.level, torch,
                            SlabModelStaleSentinel.REASON_NEIGHBORHOOD, 1_000_000L);
                    SlabModelStaleSentinel.armForTest(mc.level, support,
                            SlabModelStaleSentinel.REASON_NEIGHBORHOOD, 1_000_000L);
                });

                // Poke the section AFTER arming so the resulting rebuild's emitQuads runs armed.
                sp.getServer().runOnServer(server ->
                        server.overworld().setBlock(poke, Blocks.GLASS.defaultBlockState(), 3));
                ctx.waitTicks(15);
                sp.getClientLevel().waitForChunksRender();

                ctx.runOnClient(mc -> {
                    Float bakedDy = SlabModelStaleSentinel.peekBakedDyForTest(torch.asLong());
                    if (bakedDy == null) {
                        throw new AssertionError("[MODEL_STALE_SELF_CHECK_RED] the emitQuads capture fed NO bake "
                                + "for the armed torch after a real section rebuild — the probe wiring is dead");
                    }
                    double liveDy = SlabSupport.getYOffset(mc.level, torch, mc.level.getBlockState(torch));
                    if (Math.abs(liveDy + 0.5) > EPS) {
                        throw new AssertionError("[MODEL_STALE_SELF_CHECK_RED] scene premise broke: torch on "
                                + "bottom slab must read live dy -0.5, got " + liveDy);
                    }
                    if (Math.abs(bakedDy - liveDy) > EPS) {
                        throw new AssertionError("[MODEL_STALE_SELF_CHECK_RED] instrument self-divergence on a "
                                + "known-good scene: bakedDy=" + bakedDy + " liveDy=" + liveDy
                                + " — do NOT trust red rows until this is fixed");
                    }
                    // Zero-red sweep: judged against the REAL baked mesh, a healthy static scene must
                    // stay silent through well past the persistence window.
                    for (long t = 1_000_020L; t <= 1_000_020L + 3L * SlabModelStaleSentinel.RED_PERSIST_TICKS; t += 20) {
                        SlabModelStaleSentinel.samplePass(mc.level, t, pos -> true, rows::add);
                    }
                });
                if (!rows.isEmpty()) {
                    throw new AssertionError("[MODEL_STALE_SELF_CHECK_RED] red/yellow rows on a known-good "
                            + "static scene — the instrument is lying: " + rows);
                }
            } finally {
                SlabModelStaleSentinel.testSessionOverride = false;
                SlabModelStaleSentinel.resetCold();
            }
        }
    }
}
