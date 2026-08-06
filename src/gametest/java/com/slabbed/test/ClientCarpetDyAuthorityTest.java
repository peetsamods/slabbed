package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * BUG A, CLIENT HALF (live 2026-08-06, recorder {@code 0ba17cf0-ddb6-45f6-b4b9-921d50c9d2d8}):
 * <b>carpets still render flush after the common-side dy was fixed, because the CLIENT kept a
 * second, independent dy authority.</b>
 *
 * <p><b>Live evidence.</b> {@code 112d1449} fixed the COMMON dy and the recorder proves it landed:
 * {@code (270,-58,-56) white_carpet dy=-1.000 ANCHORED} over a {@code -0.5} {@code stone_slab}, and
 * {@code (270,-58,-54) white_carpet dy=-0.500 ANCHORED}. Maintainer still saw both lying flush, because
 * every CLIENT consumer of a carpet's dy — the chunk model ({@code OffsetBlockStateModel.emitQuads})
 * and the outline ({@code CarpetDyShapeMixin}) — routed carpets through
 * {@code ClientDy.dyFor}, which carried its own carpet special case: a "simple geometric check
 * without anchor logic" returning {@code -0.5} only when the block directly below is a half-height
 * slab surface, and {@code 0.0} otherwise. A carpet resting on a lowered FULL BLOCK, or on a slab
 * that is itself lowered, therefore rendered at {@code 0.0} no matter what the common authority
 * said.
 *
 * <p><b>The invariant these cells pin.</b> The client has no dy opinions of its own:
 * {@code ClientDy.dyFor} must equal {@link SlabSupport#getVisualYOffset} for EVERY block, so model,
 * outline, raycast and collision cannot disagree (this project's "the dy triad must be verified
 * together" law). The cells assert EQUALITY rather than a magnitude on purpose — a future change to
 * the common authority's numbers must not be able to false-green this guard, and any per-class
 * exception re-introduced on the client fails it immediately.
 *
 * <p><b>What is NOT provable here.</b> These are headless server cells: they pin the dy VALUE that
 * the client render path consumes. The pixels themselves — the chunk mesh actually drawn at that
 * offset, and the wireframe drawn by the client-only {@code CarpetDyShapeMixin} — are owed to a
 * live pass.
 */
public final class ClientCarpetDyAuthorityTest {

    private static final double EPS = 1.0e-6;

    /**
     * RED — Maintainer's reported cell. A carpet lying on a stone block that itself renders {@code -0.5}:
     * the common authority says {@code -0.5}, {@code ClientDy} said {@code 0.0} because stone is not
     * a half-height slab surface, so the carpet was drawn floating half a block above the block it
     * lies on.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void carpetClientDyMatchesCommonDyOnLoweredFullBlock(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = loweredStoneSupport(ctx, 1, 1);
        BlockPos subject = support.up();
        place(w, subject, Blocks.WHITE_CARPET.getDefaultState());

        assertClientAgreesWithCommon(ctx, subject, "white_carpet on a -0.5 stone support");
        ctx.complete();
    }

    /** RED — pale moss carpet took the identical {@code ClientDy} branch. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void paleMossCarpetClientDyMatchesCommonDyOnLoweredFullBlock(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = loweredStoneSupport(ctx, 1, 4);
        BlockPos subject = support.up();
        place(w, subject, Blocks.PALE_MOSS_CARPET.getDefaultState());

        assertClientAgreesWithCommon(ctx, subject, "pale_moss_carpet on a -0.5 stone support");
        ctx.complete();
    }

    /**
     * RED — the recorder's {@code (270,-58,-56)} shape: an ANCHORED carpet whose common dy went
     * past {@code -0.5}. {@code ClientDy}'s geometric check has no anchor logic at all and clamped
     * to at most one half-block, so it could never reproduce this value.
     *
     * <p>Reuses the {@code follower_on_minus_one} donor recipe (see
     * {@code AnchoredFollowerSupportDyTest}) to build a support that renders {@code -1.0}.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchoredCarpetClientDyMatchesCommonDyBelowMinusHalf(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = buildMinusOneSupport(ctx, 4, 1);

        BlockPos subject = support.up();
        place(w, subject, Blocks.WHITE_CARPET.getDefaultState());
        double commonDy = SlabSupport.getVisualYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(commonDy <= -0.5 + EPS,
                "fixture: the carpet on a -1.0 support must itself be lowered past -0.5 by the "
                        + "common authority, got " + commonDy);

        assertClientAgreesWithCommon(ctx, subject, "anchored white_carpet on a -1.0 support");
        ctx.complete();
    }

    /**
     * CONTROL — green before AND after. A carpet resting DIRECTLY on a plain bottom slab is the one
     * arrangement {@code ClientDy}'s geometric shortcut happened to get right, which is exactly why
     * the defect read as "some carpets are fine". It must stay right.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void carpetClientDyMatchesCommonDyOnPlainBottomSlab(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(4, 1, 4);
        place(w, ground, Blocks.STONE.getDefaultState());
        BlockPos slab = ground.up();
        place(w, slab, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));

        BlockPos subject = slab.up();
        place(w, subject, Blocks.WHITE_CARPET.getDefaultState());
        assertClientAgreesWithCommon(ctx, subject, "white_carpet directly on a bottom slab");
        ctx.complete();
    }

    /**
     * CONTROL — green before AND after. Every non-carpet block already went through the shared
     * authority; this cell fails if the fix is ever "solved" by giving some other family its own
     * client-side opinion.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void nonCarpetClientDyMatchesCommonDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = loweredStoneSupport(ctx, 6, 6);
        assertClientAgreesWithCommon(ctx, support, "stone on a bottom slab");
        ctx.complete();
    }

    // ------------------------------------------------------------------------

    private void assertClientAgreesWithCommon(TestContext ctx, BlockPos pos, String what) {
        ServerWorld w = ctx.getWorld();
        BlockState state = w.getBlockState(pos);
        double commonDy = SlabSupport.getVisualYOffset(w, pos, state);
        double clientDy = ClientDy.dyFor(w, pos, state);
        ctx.assertTrue(Math.abs(clientDy - commonDy) <= EPS,
                "the client render path must consume the SAME dy as the shared authority for "
                        + what + ": SlabSupport.getVisualYOffset=" + commonDy
                        + " but ClientDy.dyFor=" + clientDy
                        + " — ClientDy carries a carpet special case that bypasses getVisualYOffset "
                        + "entirely (a geometric support check with no anchor logic), so the model "
                        + "and outline draw the carpet flush while the common dy says it is lowered "
                        + "(live 2026-08-06 recorder 0ba17cf0: white_carpet dy=-1.000 ANCHORED and "
                        + "dy=-0.500 ANCHORED, both still rendering flush). Maintainer: 'everything "
                        + "should be able to lower; no exceptions'");
    }

    /** {@code stone_slab(BOTTOM) / stone} — returns the stone, hard-asserting it renders -0.5. */
    private BlockPos loweredStoneSupport(TestContext ctx, int x, int z) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(BlockPos.ORIGIN).add(x, 1, z);
        place(w, slab, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        BlockPos support = slab.up();
        place(w, support, Blocks.STONE.getDefaultState());
        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        ctx.assertTrue(Math.abs(supportDy + 0.5) <= EPS,
                "fixture: the stone support must itself render -0.5, got " + supportDy);
        return support;
    }

    /**
     * The {@code follower_on_minus_one} donor scene at plot-relative {@code (x, z)}, occupying
     * {@code z} and {@code z + 1}; returns the anchored log that renders -1.0.
     */
    private BlockPos buildMinusOneSupport(TestContext ctx, int x, int z) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(x, 1, z);
        BlockPos source = base.add(0, 0, 1);

        place(w, source, Blocks.STONE.getDefaultState());
        place(w, source.up(), Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        BlockPos sourceTop = source.up(2);
        place(w, sourceTop, Blocks.STONE.getDefaultState());
        SlabAnchorAttachment.addAnchor(w, sourceTop, w.getBlockState(sourceTop));

        place(w, base, Blocks.STONE.getDefaultState());
        BlockPos seat = base.up(2);
        place(w, seat, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        SlabAnchorAttachment.addAnchor(w, seat, w.getBlockState(seat));

        BlockPos subject = seat.up();
        place(w, subject, Blocks.STRIPPED_JUNGLE_LOG.getDefaultState());
        SlabAnchorAttachment.addAnchor(w, subject, w.getBlockState(subject));
        double subjectDy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(subjectDy + 1.0) <= EPS,
                "fixture: the subject log must render -1.0, got " + subjectDy);
        return subject;
    }

    private static void place(ServerWorld w, BlockPos pos, BlockState state) {
        w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }
}
