package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.util.SlabSupport;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Phase 6 — a Terrain-Slabs-owned slab's OWN dy / carrier-anchor state must be unaffected by Slabbed's
 * subtractive lowering (a DIFFERENT angle from {@code 6a3f2859}, which stopped a TS slab leaking its dy
 * to a NEIGHBORING object through the three cantilever BFS traversals). This is the SUBJECT-TS-slab
 * reader: what does Slabbed think the TS slab's own height / carrier role is?
 *
 * <p><b>The gap (found empirically, throwaway probe driving the real {@code getYOffsetInner} path under
 * the {@code shouldSkipSlabSupportTestOverride} seam, deleted after use).</b> A TS-owned slab reached
 * {@code SlabAnchorAttachment.qualifiesForPersistentLoweredSlabCarrier} / the two
 * {@code isPersistentLoweredBottomSlabCarrierNonRecursive} live-qualifier helpers with NO subject
 * namespace guard, so — in three distinct configs a genuine vanilla slab would legitimately be lowered in —
 * a TS-owned slab BOTH live-qualified as a Slabbed "persistent lowered slab carrier" AND read its OWN
 * {@code getYOffsetInner} dy as {@code -0.5}. That is Slabbed treating a self-positioning TS surface as if
 * IT were subtractively lowered — the same category as the world-hole bug: a TS block must be treated as
 * flush/vanilla-solid from Slabbed's perspective, full stop. Measured before the fix, all three subjects:
 * {@code dy=-0.5, carrier=true, carrierNonRecursive=true} with the TS override active; after: {@code dy=0.0,
 * carrier=false}. The three reachable lanes:
 * <ol>
 *   <li><b>side lane</b> — a TS BOTTOM slab side-lane beside a vanilla lowered bottom slab; the carrier
 *   marker persisted (written while the override was OFF, as real gameplay would place a genuine slab) and
 *   was then read back regardless of the override via the persisted attachment set;</li>
 *   <li><b>bottom-on-lowered-full-block</b> (Lane B / {@code ...OnLoweredFullBlockNonRecursive}) — a TS
 *   BOTTOM slab resting on an anchored lowered full block; LIVE-qualified (no persisted marker needed);</li>
 *   <li><b>vertical-on-top-like-support</b> (Lane D) — a TS slab resting on a lowered TOP/DOUBLE slab
 *   support; LIVE-qualified.</li>
 * </ol>
 *
 * <p><b>The fix.</b> Fold the invariant into the single shared STATE gate every carrier read/write path
 * routes through — {@code SlabAnchorAttachment.isPersistentLoweredSlabCarrierState} (and, delegating to it,
 * {@code isBottomPersistentLoweredSlabCarrierState}). It now returns false first for a TS-owned slab
 * ({@code !CompatHooks.shouldSkipSlabSupport(state)}), so a TS slab can never become a Slabbed carrier in
 * its own right — closing both the marker-WRITE path (all four
 * {@code qualifiesForPersistentLoweredSlabCarrier} lanes) and the marker-READ path (both
 * {@code isPersistentLoweredSlabCarrier} variants + the persisted-set lookup) in one place. Reuses the one
 * shared {@code shouldSkipSlabSupport} choke point (no new mechanism), the
 * {@code 5304e4b3}/{@code 68088bc6}/{@code c7a19048}/{@code 6a3f2859} precedent; NARROWING only, keyed on
 * the {@code terrain_slabs}/{@code terrainslabs} namespace → byte-identical without Terrain Slabs loaded.
 *
 * <p>(Note: the public {@code SlabSupport.getYOffset} entry ALSO short-circuits a TS subject to flush via
 * its own {@code CompatHooks.shouldSkipOffset(state)} check, so the render dy was already correct in
 * production; this fix closes the carrier-MARKER vector so no stale/inert TS marker can be written or read
 * by any un-{@code shouldSkipOffset}-guarded consumer — the internal {@code getYOffsetInner} recursions and
 * every direct {@code isPersistentLoweredSlabCarrier} reader.)
 *
 * <p>Test seam: Terrain Slabs is not on the gametest classpath, so the TS-owned verdict is forced for the
 * {@code terrain_slabs}-namespaced stand-in slab via {@code CompatHooks.shouldSkipSlabSupportTestOverride}.
 * Each FIX scene proves the RED WITNESS (WITHOUT the override the subject reads {@code -0.5} and IS a
 * carrier — the scene genuinely reaches the lowered path) and the GREEN (WITH the override the subject is
 * flush {@code 0.0} and NOT a carrier); a paired ANTI-JAM scene proves the guard is namespace-scoped (a
 * genuine vanilla slab in the same config STILL lowers / stays a carrier while the override is active).
 */
public final class TerrainSlabsOwnSlabDyGuardTest {

    private static final double EPS = 1.0e-6;

    private static final Identifier TS_SLAB_ID =
            Identifier.fromNamespaceAndPath("terrain_slabs", "own_slab_dy_guard_test_slab");
    private static final ResourceKey<Block> TS_SLAB_KEY = ResourceKey.create(Registries.BLOCK, TS_SLAB_ID);
    private static final Block TS_SLAB =
            new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB).setId(TS_SLAB_KEY));

    /** Registers the TS-namespaced stand-in slab (before registry freeze, via the main entrypoint). */
    public static final class TerrainSlabsOwnSlabDyGuardTestEntrypoint implements ModInitializer {
        @Override
        public void onInitialize() {
            if (!BuiltInRegistries.BLOCK.containsKey(TS_SLAB_ID)) {
                Registry.register(BuiltInRegistries.BLOCK, TS_SLAB_ID, TS_SLAB);
            }
        }
    }

    private static void tsOverride(boolean on) {
        CompatHooks.shouldSkipSlabSupportTestOverride = on
                ? st -> "terrain_slabs".equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace())
                : null;
    }

    private static double dy(ServerLevel w, BlockPos p) {
        return SlabSupport.getYOffset(w, p, w.getBlockState(p));
    }

    private static boolean carrier(ServerLevel w, BlockPos p) {
        return SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, p, w.getBlockState(p));
    }

    private static BlockState slabOf(Block block, SlabType type) {
        return block.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Lane B: a BOTTOM slab resting on an anchored lowered full block. LIVE-qualifies (no marker needed),
    // so the override changes the verdict in real time — the cleanest RED/GREEN of the SUBJECT own-dy gap.
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /** subject = TS bottom slab on a lowered full block. */
    private BlockPos buildBottomSlabOnLoweredFullBlock(GameTestHelper helper, boolean tsOwned) {
        ServerLevel w = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos fb = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos subj = helper.absolutePos(new BlockPos(2, 4, 2));

        w.setBlock(ground, slabOf(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM), 2);
        w.setBlock(fb, Blocks.STONE.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, fb, w.getBlockState(fb));
        if (Math.abs(dy(w, fb) + 0.5) > EPS) {
            throw helper.assertionException(helper.relativePos(fb),
                    "setup: the full block must anchor lowered -0.5, got " + dy(w, fb));
        }
        w.setBlock(subj, slabOf(tsOwned ? TS_SLAB : Blocks.BIRCH_SLAB, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, subj, w.getBlockState(subj));
        return subj;
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabsBottomSlabOnLoweredFullBlockReadsFlush(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos subj = buildBottomSlabOnLoweredFullBlock(helper, true);

        // RED WITNESS: without the override the TS-namespaced stand-in behaves like a vanilla slab and
        // genuinely lowers -0.5 and IS a carrier — proving the scene reaches the lowered SUBJECT path.
        if (Math.abs(dy(w, subj) + 0.5) > EPS || !carrier(w, subj)) {
            throw helper.assertionException(helper.relativePos(subj),
                    "RED WITNESS: without the TS override the subject slab on a lowered full block must read "
                            + "-0.5 AND be a carrier (dy=" + dy(w, subj) + ", carrier=" + carrier(w, subj) + ")");
        }
        tsOverride(true);
        try {
            if (Math.abs(dy(w, subj)) > EPS) {
                throw helper.assertionException(helper.relativePos(subj),
                        "THE FIX (Lane B own-dy): a Terrain-Slabs-owned slab's OWN dy must be flush 0.0 — TS "
                                + "positions its own surface; Slabbed must not subtractively lower it (got " + dy(w, subj) + ")");
            }
            if (carrier(w, subj)) {
                throw helper.assertionException(helper.relativePos(subj),
                        "THE FIX (Lane B carrier): a Terrain-Slabs-owned slab must NEVER be a Slabbed persistent "
                                + "lowered slab carrier in its own right");
            }
        } finally {
            tsOverride(false);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaBottomSlabOnLoweredFullBlockStillLowersWhileTsOverrideActive(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos subj = buildBottomSlabOnLoweredFullBlock(helper, false);

        tsOverride(true);
        try {
            if (Math.abs(dy(w, subj) + 0.5) > EPS || !carrier(w, subj)) {
                throw helper.assertionException(helper.relativePos(subj),
                        "ANTI-JAM (Lane B): a genuine VANILLA slab on a lowered full block must STILL read -0.5 "
                                + "AND stay a carrier while the TS-exclusion override is active (dy=" + dy(w, subj)
                                + ", carrier=" + carrier(w, subj) + ") — the guard must be namespace-scoped");
            }
        } finally {
            tsOverride(false);
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Lane D: a slab resting VERTICALLY on a lowered TOP/DOUBLE slab support. LIVE-qualifies.
    // Scene mirrors SlabOnSlabVerticalAnchorTest (anchored dirt -> lowered BOTTOM slab -> DOUBLE support
    // side-lane -> subject slab on the support).
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    /** subject = slab on a lowered DOUBLE support. */
    private BlockPos buildSlabOnVerticalLoweredSupport(GameTestHelper helper, boolean tsOwned) {
        ServerLevel w = helper.getLevel();
        BlockPos groundSlab = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 4, 2));
        BlockPos support = helper.absolutePos(new BlockPos(3, 4, 2)); // DOUBLE, side-lane beside the lowered bottom
        BlockPos subj = helper.absolutePos(new BlockPos(3, 5, 2));    // slab resting on the support

        w.setBlock(groundSlab, slabOf(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(loweredBottom, slabOf(Blocks.OAK_SLAB, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));
        w.setBlock(support, slabOf(Blocks.OAK_SLAB, SlabType.DOUBLE), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, support, w.getBlockState(support));
        if (!SlabSupport.isLoweredTopLikeSlabCarrier(w, support, w.getBlockState(support))) {
            throw helper.assertionException(helper.relativePos(support),
                    "setup: the DOUBLE support beside the lowered bottom slab must be a lowered top-like carrier");
        }
        w.setBlock(subj, slabOf(tsOwned ? TS_SLAB : Blocks.BIRCH_SLAB, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, subj, w.getBlockState(subj));
        return subj;
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabsSlabOnVerticalLoweredSupportReadsFlush(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos subj = buildSlabOnVerticalLoweredSupport(helper, true);

        if (Math.abs(dy(w, subj) + 0.5) > EPS || !carrier(w, subj)) {
            throw helper.assertionException(helper.relativePos(subj),
                    "RED WITNESS: without the TS override the subject slab on a lowered TOP/DOUBLE support must "
                            + "read -0.5 AND be a carrier (dy=" + dy(w, subj) + ", carrier=" + carrier(w, subj) + ")");
        }
        tsOverride(true);
        try {
            if (Math.abs(dy(w, subj)) > EPS) {
                throw helper.assertionException(helper.relativePos(subj),
                        "THE FIX (Lane D own-dy): a Terrain-Slabs-owned slab resting on a lowered TOP/DOUBLE "
                                + "support must read its OWN dy as flush 0.0 (got " + dy(w, subj) + ")");
            }
            if (carrier(w, subj)) {
                throw helper.assertionException(helper.relativePos(subj),
                        "THE FIX (Lane D carrier): a Terrain-Slabs-owned slab must NEVER be a Slabbed vertical "
                                + "lowered-slab carrier in its own right");
            }
        } finally {
            tsOverride(false);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaSlabOnVerticalLoweredSupportStillLowersWhileTsOverrideActive(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos subj = buildSlabOnVerticalLoweredSupport(helper, false);

        tsOverride(true);
        try {
            if (Math.abs(dy(w, subj) + 0.5) > EPS || !carrier(w, subj)) {
                throw helper.assertionException(helper.relativePos(subj),
                        "ANTI-JAM (Lane D): a genuine VANILLA slab on a lowered TOP/DOUBLE support must STILL "
                                + "read -0.5 AND stay a carrier while the TS-exclusion override is active (dy="
                                + dy(w, subj) + ", carrier=" + carrier(w, subj) + ")");
            }
        } finally {
            tsOverride(false);
        }
        helper.succeed();
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────────
    // Side lane / persisted-marker vector: the carrier marker is WRITTEN while the override is OFF (as real
    // gameplay would place a genuine slab), then read back. Proves the READ path is also closed for a TS
    // subject: even a persisted attachment-set marker must not survive the namespace guard.
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabsSideLaneCarrierMarkerNotReadForTsSubject(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos groundSlab = helper.absolutePos(new BlockPos(2, 3, 3));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 4, 3));
        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 5, 3)); // vanilla side-lane owner
        BlockPos carrierGround = helper.absolutePos(new BlockPos(3, 4, 3));
        BlockPos subj = helper.absolutePos(new BlockPos(3, 5, 3));          // TS side-lane carrier subject

        w.setBlock(groundSlab, slabOf(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(loweredBottom, slabOf(Blocks.OAK_SLAB, SlabType.BOTTOM), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, loweredBottom, w.getBlockState(loweredBottom));
        w.setBlock(carrierGround, slabOf(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM), 2);
        w.setBlock(subj, slabOf(TS_SLAB, SlabType.BOTTOM), 2);
        // Write the marker WITHOUT the override active (marker gets persisted to the attachment set).
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, subj, w.getBlockState(subj));

        // RED WITNESS: without the override the TS-namespaced subject reads -0.5 AND is a carrier.
        if (Math.abs(dy(w, subj) + 0.5) > EPS || !carrier(w, subj)) {
            throw helper.assertionException(helper.relativePos(subj),
                    "RED WITNESS: without the TS override the side-lane subject slab must read -0.5 AND be a "
                            + "carrier (dy=" + dy(w, subj) + ", carrier=" + carrier(w, subj) + ")");
        }
        tsOverride(true);
        try {
            // Even with a marker already persisted to the set, the namespace guard in the carrier-state gate
            // makes the READ return false for a TS subject, and getYOffsetInner reads flush.
            if (carrier(w, subj)) {
                throw helper.assertionException(helper.relativePos(subj),
                        "THE FIX (read path): a persisted lowered-carrier marker must NOT be READ back for a "
                                + "Terrain-Slabs-owned subject slab (the namespace guard is on the shared carrier-"
                                + "state gate, so it overrides even a stale attachment-set marker)");
            }
            if (Math.abs(dy(w, subj)) > EPS) {
                throw helper.assertionException(helper.relativePos(subj),
                        "THE FIX (read path own-dy): a Terrain-Slabs-owned side-lane subject slab must read its "
                                + "OWN dy flush 0.0 (got " + dy(w, subj) + ")");
            }
        } finally {
            tsOverride(false);
        }
        helper.succeed();
    }
}
