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
 * PINS THE COMPAT-OFFSET DEFERRAL AS IT STANDS — deliberately, as a tripwire.
 *
 * <p>The deferral (CHANGELOG, "TS-on-vanilla, TS+TS, deep chains"): compat-owned blocks are
 * categorically {@code shouldSkipOffset}, and that test runs BEFORE the frozen store in
 * {@code getYOffset}, so even a stored height is unreadable for them. Compound stacks collapse to
 * flush the moment a compat block enters them. That is a known, documented limitation.
 *
 * <p><b>An attempt to lift it went live-RED on 2026-08-21</b> (ledger entry of that date): moving the
 * skip below the store let the CLIENT predict a lowered compat slab while the server still minted no
 * fact, so placements predicted-then-reverted, leaving the block above interpenetrating — strictly
 * worse than not lowering. Lifting the deferral needs the whole chain (server-side fact minting, a
 * depth authority, the placement refusal), not a read-path reorder.
 *
 * <p><b>So the deferral-pin row here is EXPECTED to fail when someone lifts the deferral — that is
 * its job.</b> Whoever flips it must do so consciously, with that ledger entry in front of them,
 * shipping the write path along with the read path.
 *
 * <p><b>Why the seam exists.</b> The compat mod is absent in tests, so {@code shouldSkipOffset}
 * answered {@code false} for everything and every compat-ownership branch was unreachable headlessly —
 * a full green said nothing about a change to them. {@code shouldSkipOffsetTestOverride} makes the
 * branch reachable; these rows are also the proof that the seam itself works.
 */
public final class CompatOffsetDeferralPinTest {

    private static final Identifier COMPAT_SLAB_ID =
            Identifier.fromNamespaceAndPath("terrain_slabs", "compat_offset_deferral_pin_slab");
    private static final ResourceKey<Block> COMPAT_SLAB_KEY =
            ResourceKey.create(Registries.BLOCK, COMPAT_SLAB_ID);
    private static final Block COMPAT_SLAB =
            new SlabBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB).setId(COMPAT_SLAB_KEY));

    /** Registers the compat-namespaced stand-in slab before registry freeze, via the main entrypoint. */
    public static final class CompatOffsetDeferralPinTestEntrypoint implements ModInitializer {
        @Override
        public void onInitialize() {
            if (!BuiltInRegistries.BLOCK.containsKey(COMPAT_SLAB_ID)) {
                Registry.register(BuiltInRegistries.BLOCK, COMPAT_SLAB_ID, COMPAT_SLAB);
            }
        }
    }

    private static void compatOverride(boolean on) {
        CompatHooks.shouldSkipOffsetTestOverride = on
                ? st -> "terrain_slabs".equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace())
                : null;
    }

    private static BlockState compatBottomSlab() {
        return COMPAT_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /** Reads {@code getYOffset} as the SHIPPED jar does: the gametest JVM pins frozen-dy OFF, and the
     *  deferral's decisive ordering (skip before store) only exists on the store branch. */
    private static double shippedConfigYOffset(ServerLevel level, BlockPos pos) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            return SlabSupport.getYOffset(level, pos, level.getBlockState(pos));
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void theDeferralStands_aStoredFactOnACompatSlabIsUnreadable(GameTestHelper helper) {
        // THE TRIPWIRE ROW. Reads 0.0 today BECAUSE the compat skip runs before the store. If this
        // row goes red with -0.5, someone has lifted the deferral: read the 2026-08-21 live-RED
        // ledger entry before celebrating, and ship the server-side write path with it.
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, compatBottomSlab(), 2);
        SlabAnchorAttachment.writePlacementDy(level, pos, -0.5d);

        compatOverride(true);
        try {
            double read = shippedConfigYOffset(level, pos);
            if (Math.abs(read) > 1.0e-9) {
                throw helper.assertionException(
                        "the compat-offset deferral appears LIFTED (stored fact now readable, got " + read
                                + "). That may be intended — but it went live-RED once: predict-then-revert "
                                + "interpenetration, because the read path was reordered without the write "
                                + "path. See the 2026-08-21 ledger entry, then update this pin consciously.");
            }
        } finally {
            compatOverride(false);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void worldgenCompatTerrainReadsFlush(GameTestHelper helper) {
        // The world-hole guard: compat terrain with no fact must read flush regardless of what ever
        // happens to the deferral. This row must stay green through any future lift.
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, compatBottomSlab(), 2);

        compatOverride(true);
        try {
            double read = shippedConfigYOffset(level, pos);
            if (Math.abs(read) > 1.0e-9) {
                throw helper.assertionException(
                        "compat terrain with no placement fact must read flush 0.0, got " + read);
            }
        } finally {
            compatOverride(false);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void theSeamGatesByNamespaceAndOnlyWhenSet(GameTestHelper helper) {
        // The seam's own contract: on, it marks exactly the compat namespace; off, the real hook
        // answers false with the mod absent. Without this pair the two rows above could pass with a
        // seam that matches nothing (deferral row) or everything (worldgen row).
        compatOverride(true);
        try {
            if (!CompatHooks.shouldSkipOffset(compatBottomSlab())) {
                throw helper.assertionException("with the seam on, a compat-namespaced state must be owned");
            }
            if (CompatHooks.shouldSkipOffset(Blocks.STONE_SLAB.defaultBlockState())) {
                throw helper.assertionException("with the seam on, a vanilla state must NOT be owned");
            }
        } finally {
            compatOverride(false);
        }
        if (CompatHooks.shouldSkipOffset(compatBottomSlab())) {
            throw helper.assertionException(
                    "with the seam off and the compat mod absent, nothing is owned — a leaked override "
                            + "would poison every later test in the JVM");
        }
        helper.succeed();
    }
}
