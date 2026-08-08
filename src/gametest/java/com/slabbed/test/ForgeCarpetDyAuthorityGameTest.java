package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 1 fix: carpet's canonical dy is -0.5 on a bottom slab, decided by ONE authority.
 *
 * <p>Before this fix the two dy authorities disagreed for exactly one block family. Every visual
 * lane (model, outline, crosshair raycast, /slabdev debug overlay) read carpet through
 * {@code ClientDy.dyFor}'s private special case ({@code hasBottomSlabBelow ? -0.5 : 0.0}), while
 * {@code SlabSupport.getYOffset} — the authority for placement, survival and every server-side
 * decision — excluded carpet via {@code isThinTopLayer} and said 0.0. Players SAW a sunken carpet
 * that the click-handling treated as flush.
 *
 * <p>maintainer ruling (2026-07-27): carpet is -0.5 — match what players already see. The fix
 * RELOCATES ClientDy's rule byte-identically into {@code getYOffset} (below the recursion guard,
 * so nested queries keep returning 0.0 exactly as today) and makes ClientDy a pure delegate.
 * No other block family moves; the snow-layer sibling is pinned flat by this test precisely
 * because this project has been burned by exclusion sets widening past their target.
 *
 * <p>Fixtures use {@code UPDATE_NONE}: these rows pin the dy AUTHORITY, not vanilla survival.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ForgeCarpetDyAuthorityGameTest {

    @GameTest(template = "empty")
    public void carpetDyIsCanonicalMinusHalfOnBottomSlab(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockState bottomSlab = Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState carpet = Blocks.RED_CARPET.defaultBlockState();
        BlockState snow = Blocks.SNOW.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();

        // A: carpet directly on a bottom slab — the canonical case. -0.5.
        BlockPos a = ctx.absolutePos(new BlockPos(1, 2, 1));
        world.setBlock(a.below(), bottomSlab, Block.UPDATE_NONE);
        world.setBlock(a, carpet, Block.UPDATE_NONE);

        // B: carpet on full stone standing on more stone — no slab anywhere below. 0.0.
        BlockPos b = ctx.absolutePos(new BlockPos(3, 2, 1));
        world.setBlock(b.below(), stone, Block.UPDATE_NONE);
        world.setBlock(b.below(2), stone, Block.UPDATE_NONE);
        world.setBlock(b, carpet, Block.UPDATE_NONE);

        // C: carpet on stone on a bottom slab — 0.0. hasBottomSlabBelow is a DIRECT-below check
        // ("the block immediately below is a bottom slab"), not a column walk, so ClientDy's
        // historical rule never followed lowered columns and the relocation must not either.
        // KNOWN VISUAL WART, unchanged by this fix: the stone here reads -0.5 (anchored full
        // block on a slab) while the carpet on it reads 0.0, so the carpet renders floating
        // half a block above its sunken support — exactly as it did before. Carpet's placed
        // height becomes exact when Phase 6's aim-derived stored dy lands. An earlier revision
        // of this row expected -0.5 from an assumed column walk; the RED run refuted it.
        BlockPos c = ctx.absolutePos(new BlockPos(5, 3, 1));
        world.setBlock(c.below(2), bottomSlab, Block.UPDATE_NONE);
        world.setBlock(c.below(), stone, Block.UPDATE_NONE);
        world.setBlock(c, carpet, Block.UPDATE_NONE);

        // D: snow LAYER on a bottom slab — the isThinTopLayer sibling. MUST stay 0.0: the ruling
        // covers carpet only, and an exclusion-set widening here is exactly the recorded
        // failure mode this suite exists to catch.
        BlockPos d = ctx.absolutePos(new BlockPos(7, 2, 1));
        world.setBlock(d.below(), bottomSlab, Block.UPDATE_NONE);
        world.setBlock(d, snow, Block.UPDATE_NONE);

        assertDy(ctx, world, a, -0.5d, "carpet on a bottom slab");
        assertDy(ctx, world, b, 0.0d, "carpet on full ground (no slab below)");
        assertDy(ctx, world, c, 0.0d, "carpet on stone-on-slab (direct-below rule, no column walk)");
        assertDy(ctx, world, d, 0.0d, "snow layer sibling must NOT be widened");
        ctx.succeed();
    }

    /**
     * Structural half: ClientDy must be a pure delegate — no private carpet special case left.
     * Follows the 26.2 donor's source-inspection pattern (PlacementCaptureBoundaryGameTest).
     * The gametest server runs from {@code run/}, so the source tree sits one level up.
     */
    @GameTest(template = "empty")
    public void clientDyIsAPureDelegate(GameTestHelper ctx) {
        Path src = Path.of("..", "src", "client", "java", "com", "slabbed", "client", "ClientDy.java");
        String text;
        try {
            text = Files.readString(src);
        } catch (IOException e) {
            throw new AssertionError("structural test requires the repo source tree at " + src
                    + " (gametest cwd is run/); refusing to skip silently", e);
        }
        ctx.assertTrue(!text.contains("CarpetBlock"),
                "ClientDy must carry NO carpet special case — the dy authority is "
                        + "SlabSupport.getYOffset alone");
        ctx.assertTrue(text.contains("SlabSupport.getYOffset"),
                "ClientDy must delegate to SlabSupport.getYOffset");
        ctx.succeed();
    }

    private static void assertDy(GameTestHelper ctx, ServerLevel world, BlockPos pos,
                                 double expected, String what) {
        double dy = SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
        ctx.assertTrue(Double.compare(dy, expected) == 0,
                what + ": expected dy=" + expected + " got dy=" + dy
                        + " state=" + world.getBlockState(pos));
    }
}
