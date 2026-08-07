package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.dev.SlabdyRowFormatter;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * New diagnostic (2026-07-04, built at the maintainer's request while investigating the hanging-lantern
 * gap): {@code /slabdy}'s row output now shows the client-side visual-dy cache's contents next
 * to a fresh recompute, for both the targeted block and the block above it (the common
 * "hanger reads its support" relationship). This lets a live tester point at a support that a
 * hanging decoration disagrees with and directly SEE whether the cache is stale, instead of
 * inferring it from indirect symptoms.
 *
 * <p>The "cache is genuinely stale" branch cannot be exercised headlessly (the cache is only
 * ever written by {@code getVisualYOffset}'s real-{@code ClientWorld} branch or by
 * {@code refreshVisualYOffsetRegion}, which itself no-ops on a non-client world — exactly the
 * structural reason the underlying gap bug resists headless reproduction). This test proves the
 * MISS path (a fresh gametest world has no cache entries) renders correctly and matches a fresh
 * {@link SlabSupport#getYOffset} recompute, so the new line is provably wired to real values, not
 * placeholder text.
 */
public final class SlabdyCacheVsFreshDiagnosticTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cacheLineShowsMissAndMatchingFreshValueOnAServerWorld(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos dirtPos = slabPos.up();

        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtPos, w.getBlockState(dirtPos));

        ctx.assertTrue(SlabSupport.peekCachedClientVisualYOffset(dirtPos) == null,
                "setup: a fresh gametest ServerWorld must never have populated the client cache");

        List<String> lines = SlabdyRowFormatter.formatRow(w, dirtPos, w.getBlockState(dirtPos),
                Direction.UP, new Vec3d(dirtPos.getX() + 0.5, dirtPos.getY() + 0.5, dirtPos.getZ() + 0.5),
                ItemStack.EMPTY, "missing", null);

        String cacheLine = lines.stream().filter(l -> l.startsWith("  cache:")).findFirst().orElse("");
        ctx.assertTrue(cacheLine.contains("target=" + dirtPos.toShortString() + "(minecraft:dirt) cache=MISS fresh=-0.500"),
                "cache line must show MISS for an unpopulated cache and the correct fresh value; got: " + cacheLine);
        ctx.assertTrue(!cacheLine.contains("STALE"),
                "a cache MISS must never be reported as STALE (nothing to compare against); got: " + cacheLine);
        ctx.complete();
    }
}
