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
 * PINS THE COMPAT-OFFSET STORE ORDERING — flipped consciously when the deferral was lifted.
 *
 * <p>THE DEFERRAL IS LIFTED (this line's Terrain Slabs parity slice): the frozen store is consulted
 * BEFORE {@code shouldSkipOffset} in every read, and the placement gate carves TAGGED SLABS out of
 * compat ownership, so a placed compat slab both MINTS a fact and READS it back. The first row now
 * asserts the lifted contract: a stored fact on a compat slab is readable.
 *
 * <p><b>The 2026-08-21 attempt went live-RED</b> (ledger entry of that date) because it reordered
 * the READ without the WRITE: the client predicted a lowered compat slab, the server minted no fact,
 * and the correction reverted it — predict-then-revert interpenetration. That post-mortem's own
 * demand — "a read-path test cannot see a write-path gap" — is why the lift shipped together with
 * {@code CompatEligibilityPredicateTest}, whose parity row drives the REAL {@code useOn} transaction
 * and asserts the STORED fact, on both sides of the mint the 2026-08-21 rows could not see.
 *
 * <p>{@link #worldgenCompatTerrainReadsFlush} is the row that must survive every future change here:
 * factless compat terrain (worldgen's shape) stays flush — the world-hole pin.
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
        // Both seams together, because the real mod gates both: the offset skip AND the slab-support
        // skip. Reproducing a live TS scene with only one of them active answers a question no real
        // configuration ever asks.
        CompatHooks.shouldSkipOffsetTestOverride = on
                ? st -> "terrain_slabs".equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace())
                : null;
        CompatHooks.shouldSkipSlabSupportTestOverride = on
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
    public void theDeferralIsLifted_aStoredFactOnACompatSlabIsReadable(GameTestHelper helper) {
        // THE FLIPPED TRIPWIRE. Until the parity slice, this row asserted 0.0 — the skip ran before
        // the store and even a recorded height was unreadable, which is what made every placed compat
        // slab float. It now asserts the lifted contract. If this row goes red with 0.0, someone has
        // restored the skip above the store read: that re-opens the float AND desyncs the client
        // prediction from the server's stored fact — do not "fix" a compat symptom that way.
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(pos, compatBottomSlab(), 2);
        SlabAnchorAttachment.writePlacementDy(level, pos, -0.5d);

        compatOverride(true);
        try {
            double read = shippedConfigYOffset(level, pos);
            if (Math.abs(read - (-0.5d)) > 1.0e-9) {
                throw helper.assertionException(
                        "a stored fact on a compat slab must be readable (expected -0.5, got " + read
                                + "). The store is consulted before the compat skip; restoring the old "
                                + "ordering re-opens the placed-compat-slab float and the "
                                + "predict-then-revert desync (2026-08-21 ledger entry).");
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

    /**
     * NOT the live float — and that is the finding. A maintainer report (2026-08-22, real Terrain
     * Slabs 3.3.2 in the dev client) has an oak slab clicked onto the centre of a compat slab's top
     * face floating at dy {@code 0.0}. This row runs that EXACT placement through the seams, and the
     * mint comes out {@code -0.5} — the correct continuation seat: the resolver's UP formula seats
     * the slab on the compat owner's visible top even with the owner's dy compat-zeroed, because the
     * arithmetic asks {@code topPlaneOffset} of the owner's SHAPE, which a compat bottom slab answers
     * as {@code 0.5} like any slab.
     *
     * <p>So the Slabbed mint path is NOT where the live float comes from. Whatever produces it needs
     * the real mod's runtime — its {@code generated} blockstate (a different {@code
     * customSlabSurfaceKind}), its own mixins, or the client prediction round trip — none of which a
     * headless seam carries. This row pins the mint's correctness so that when the live divergence is
     * found, it cannot be "fixed" by breaking the part that already works.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaSlabOnCompatSlabTopMintsTheContinuationSeat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos supportRel = new BlockPos(2, 2, 2);
        BlockPos support = helper.absolutePos(supportRel);
        BlockPos placed = support.above();
        helper.setBlock(supportRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(supportRel, compatBottomSlab()); // worldgen-like: no placement fact

        compatOverride(true);
        try {
            net.minecraft.world.entity.player.Player player =
                    helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            net.minecraft.world.item.ItemStack stack =
                    new net.minecraft.world.item.ItemStack(Blocks.OAK_SLAB.asItem());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            net.minecraft.world.phys.Vec3 hit =
                    net.minecraft.world.phys.Vec3.atCenterOf(support).add(0.0, 0.5, 0.0);
            stack.useOn(new net.minecraft.world.item.context.UseOnContext(player,
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(hit,
                            net.minecraft.core.Direction.UP, support, false)));

            BlockState placedState = level.getBlockState(placed);
            if (!placedState.is(Blocks.OAK_SLAB)) {
                throw helper.assertionException(helper.relativePos(placed),
                        "premise drift: expected the oak slab in the cell above the compat slab, got "
                                + placedState + " (same cell holds " + level.getBlockState(support) + ")");
            }
            double stored = SlabAnchorAttachment.storedPlacementDy(level, placed);
            if (!Double.isFinite(stored) || Math.abs(stored - (-0.5d)) > 1.0e-9) {
                throw helper.assertionException(helper.relativePos(placed),
                        "the oak slab must MINT the continuation seat -0.5 on a compat slab top, got "
                                + stored + " — the mint path regressed; the live float (0.0, real-mod "
                                + "runtime) is a SEPARATE divergence and must not be chased here");
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
