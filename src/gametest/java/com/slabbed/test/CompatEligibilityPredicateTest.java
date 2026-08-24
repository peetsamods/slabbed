package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.placement.LandingResolver;
import com.slabbed.util.SlabSupport;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Makes the SHIPPED Terrain Slabs eligibility predicate executable, and pins how the placement
 * transaction subordinates it.
 *
 * <p>Before this class, {@code TerrainSlabsCompat.shouldSkipOffset} could not run in any test. The
 * chain stops one step short: {@link CompatHooks#shouldSkipOffset} reaches it once the gametest mixin
 * forces {@code isLoaded()}, but the method then guards on its OWN {@code LOADED} field read, which
 * that mixin redirected only inside {@code customSlabSurfaceKind}. So it returned false before
 * reaching {@code isTerrainSlabsId}, and the classification rule was never exercised.
 *
 * <p><b>Why the pre-existing {@code shouldSkipOffsetTestOverride} seam cannot cover this.</b>
 * {@link CompatHooks#shouldSkipOffset} consults the override FIRST and returns on a true answer,
 * before any production compat call. A test driving that seam therefore proves how CONSUMERS react to
 * a compat verdict, never how the verdict is REACHED — the production predicate can be emptied to
 * {@code return false} and every override-driven row stays green. Both proofs are needed and they are
 * not interchangeable; this class owns the second one.
 *
 * <p><b>The predicate is namespace-based and STAYS namespace-based — subordinated, not deleted.</b>
 * The LAW.md clause 2 resolution is the placement TRANSACTION: a slab a player places mints a stored
 * fact like any vanilla slab ({@link LandingResolver#compatOwnsFinalState} carves tagged slabs out),
 * and every read consults the store BEFORE the exclusion. The namespace rule remains as the FACTLESS
 * fall-through gate — worldgen compat terrain never runs a placement transaction, carries no fact,
 * and must keep rendering flush (the world-hole pin). So
 * {@link #twinsDifferOnlyByNamespaceAndTheShippedRuleSplitsThem} keeps pinning the raw predicate: its
 * split is now the proof that the fall-through gate is intact, and a RED there means the world-hole
 * pin lost its gate, not that a law was satisfied.
 *
 * <p>The two slab fixtures are TWINS: same class, same properties copied from {@code STONE_SLAB},
 * same blockstate shape, both tagged into {@code minecraft:slabs} exactly as the real compat jar tags
 * all of its slabs (verified against the shipped data). They differ ONLY in registry namespace, so a
 * row that separates them is reading the namespace rule and nothing else. The CUBE fixture shares the
 * compat namespace but is not a slab — it keeps the carve-out honest.
 */
public final class CompatEligibilityPredicateTest {

    private static final double EPS = 1.0e-6;

    private static final ThreadLocal<Boolean> ELIGIBILITY_CLASSIFIER_TEST_GATE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final Identifier COMPAT_ID =
            Identifier.fromNamespaceAndPath("terrain_slabs", "eligibility_probe_slab");
    private static final Identifier TWIN_ID =
            Identifier.fromNamespaceAndPath("slabbed_gametest", "eligibility_twin_slab");
    private static final Identifier COMPAT_CUBE_ID =
            Identifier.fromNamespaceAndPath("terrain_slabs", "eligibility_probe_cube");

    private static final ResourceKey<Block> COMPAT_KEY = ResourceKey.create(Registries.BLOCK, COMPAT_ID);
    private static final ResourceKey<Block> TWIN_KEY = ResourceKey.create(Registries.BLOCK, TWIN_ID);
    private static final ResourceKey<Block> COMPAT_CUBE_KEY =
            ResourceKey.create(Registries.BLOCK, COMPAT_CUBE_ID);

    private static final Block COMPAT_SLAB = new SlabBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB).setId(COMPAT_KEY));
    private static final Block TWIN_SLAB = new SlabBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB).setId(TWIN_KEY));
    private static final Block COMPAT_CUBE = new Block(
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).setId(COMPAT_CUBE_KEY));

    // Items, so a row can drive a REAL held-item placement through the same transaction the product
    // uses. Without them the only reachable gesture is setBlock, which authors no placement fact and
    // therefore cannot exercise the transaction discriminator at all.
    private static final Item COMPAT_SLAB_ITEM = new BlockItem(COMPAT_SLAB,
            new Item.Properties().setId(ResourceKey.create(Registries.ITEM, COMPAT_ID))
                    .useBlockDescriptionPrefix());
    private static final Item TWIN_SLAB_ITEM = new BlockItem(TWIN_SLAB,
            new Item.Properties().setId(ResourceKey.create(Registries.ITEM, TWIN_ID))
                    .useBlockDescriptionPrefix());

    /** Registers the fixtures before registry freeze. */
    public static final class CompatEligibilityFixtureEntrypoint implements ModInitializer {
        @Override
        public void onInitialize() {
            if (!BuiltInRegistries.BLOCK.containsKey(COMPAT_ID)) {
                Registry.register(BuiltInRegistries.BLOCK, COMPAT_ID, COMPAT_SLAB);
                Registry.register(BuiltInRegistries.ITEM, COMPAT_ID, COMPAT_SLAB_ITEM);
            }
            if (!BuiltInRegistries.BLOCK.containsKey(TWIN_ID)) {
                Registry.register(BuiltInRegistries.BLOCK, TWIN_ID, TWIN_SLAB);
                Registry.register(BuiltInRegistries.ITEM, TWIN_ID, TWIN_SLAB_ITEM);
            }
            if (!BuiltInRegistries.BLOCK.containsKey(COMPAT_CUBE_ID)) {
                Registry.register(BuiltInRegistries.BLOCK, COMPAT_CUBE_ID, COMPAT_CUBE);
            }
        }
    }

    /** GameTest-only access point consumed by the test mixin around the eligibility predicates. */
    public static boolean eligibilityClassifierTestGate() {
        return ELIGIBILITY_CLASSIFIER_TEST_GATE.get();
    }

    private static BlockState compatSlab() {
        return COMPAT_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState twinSlab() {
        return TWIN_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /** Stone support column whose top carries a recorded seat of {@code seatDy}. */
    private static BlockPos loweredSupport(GameTestHelper helper, BlockPos rel, double seatDy) {
        helper.setBlock(rel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(rel, Blocks.STONE.defaultBlockState());
        BlockPos abs = helper.absolutePos(rel);
        SlabAnchorAttachment.writePlacementDy(helper.getLevel(), abs, seatDy);
        return abs;
    }

    /** Fires the real useOn placement path: UP-face click on {@code support}, held {@code item}. */
    private static void placeOnTop(GameTestHelper helper, BlockPos support, Item item) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(support.getX() + 0.5, support.getY() + 1, support.getZ() + 0.5);
        ItemStack stack = new ItemStack(item);
        stack.setCount(4);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = new Vec3(support.getX() + 0.5, support.getY() + 1.0, support.getZ() + 0.5);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, support, false)));
    }

    private static double storedOrFail(GameTestHelper helper, BlockPos placed, Block expected, String what) {
        ServerLevel level = helper.getLevel();
        BlockState state = level.getBlockState(placed);
        if (!state.is(expected)) {
            throw helper.assertionException(helper.relativePos(placed),
                    "premise drift: " + what + " must land " + expected + " in this cell, got " + state);
        }
        double stored = SlabAnchorAttachment.storedPlacementDy(level, placed);
        if (!Double.isFinite(stored)) {
            throw helper.assertionException(helper.relativePos(placed),
                    what + ": no stored fact was minted — the placement transaction is the "
                            + "discriminator, so a transactionless " + what + " means the carve-out "
                            + "never reached the mint");
        }
        return stored;
    }

    /**
     * THE TRIPWIRE, retargeted (same commit as the transaction carve-out, as this row's own contract
     * required). Two blocks indistinguishable by geometry, class and shape; the shipped predicate
     * separates them purely on namespace. That split is DELIBERATELY still asserted: the predicate is
     * now the FACTLESS fall-through gate — the world-hole pin for worldgen compat terrain — and this
     * row failing means that gate stopped firing, which would hand every factless compat cell to
     * Slabbed's generic rules.
     *
     * <p>Teeth: replace {@code isTerrainSlabsId(id)} with {@code false} in
     * {@code TerrainSlabsCompat.shouldSkipOffset} and this row fails. Before this class existed that
     * mutation was invisible to the entire suite.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void twinsDifferOnlyByNamespaceAndTheShippedRuleSplitsThem(GameTestHelper helper) {
        ELIGIBILITY_CLASSIFIER_TEST_GATE.set(Boolean.TRUE);
        try {
            BlockState compat = compatSlab();
            BlockState twin = twinSlab();

            if (compat.getBlock().getClass() != twin.getBlock().getClass()) {
                throw helper.assertionException(
                        "fixture drift: the twins must share a class or the split below could be "
                                + "class-shaped rather than namespace-shaped");
            }

            boolean compatSkipped = CompatHooks.shouldSkipOffset(compat);
            boolean twinSkipped = CompatHooks.shouldSkipOffset(twin);

            if (!compatSkipped) {
                throw helper.assertionException(
                        "the factless fall-through gate did not fire: a terrain_slabs-namespaced "
                                + "slab with no stored fact must still be excluded from generic "
                                + "offset rules, or worldgen compat terrain loses its world-hole pin");
            }
            if (twinSkipped) {
                throw helper.assertionException(
                        "a slab outside the compat namespace was skipped, so the predicate is wider "
                                + "than the namespace rule it is supposed to express");
            }

            boolean compatSupportSkipped = CompatHooks.shouldSkipSlabSupport(compat);
            boolean twinSupportSkipped = CompatHooks.shouldSkipSlabSupport(twin);
            if (!compatSupportSkipped || twinSupportSkipped) {
                throw helper.assertionException(
                        "slab-support eligibility diverged from offset eligibility: compat="
                                + compatSupportSkipped + " twin=" + twinSupportSkipped
                                + " - the shipped code delegates one to the other, so they move together");
            }
        } finally {
            ELIGIBILITY_CLASSIFIER_TEST_GATE.set(Boolean.FALSE);
        }
        helper.succeed();
    }

    /**
     * The negative control that makes the row above mean something. With the gate off, the shipped
     * predicate is inert for BOTH twins because Terrain Slabs is genuinely absent from the gametest
     * classpath. Without this, a row asserting "compat is skipped" could be satisfied by a predicate
     * that skips everything unconditionally.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void withoutTheGateTheShippedPredicateIsInertForBothTwins(GameTestHelper helper) {
        if (CompatHooks.shouldSkipOffset(compatSlab())
                || CompatHooks.shouldSkipOffset(twinSlab())
                || CompatHooks.shouldSkipSlabSupport(compatSlab())
                || CompatHooks.shouldSkipSlabSupport(twinSlab())) {
            throw helper.assertionException(
                    "with the mod absent and no gate, compat eligibility must be false for every "
                            + "state - a true answer here means the predicate is reachable in a "
                            + "configuration that has no compat mod, which would affect real players");
        }
        helper.succeed();
    }

    /**
     * The ownership gate's carve-out, pinned at the predicate: a TAGGED SLAB in the compat namespace
     * is NOT compat-owned (a slab a player places is an ordinary slab whoever registered it — LAW.md
     * clause 2), while a NON-slab block in the same namespace still is. The cube is what keeps this
     * from reading as "the gate is gone": same namespace, not a slab, still owned.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void theOwnershipGateReleasesTaggedSlabsAndKeepsNonSlabCompatBlocks(GameTestHelper helper) {
        ELIGIBILITY_CLASSIFIER_TEST_GATE.set(Boolean.TRUE);
        try {
            if (!SlabSupport.isTaggedSlab(compatSlab())) {
                throw helper.assertionException(
                        "premise: the compat twin must be a tagged slab (SlabBlock + TYPE + "
                                + "minecraft:slabs), exactly as the real compat jar tags all of its "
                                + "slabs — if the tag file is missing the whole carve-out is untestable");
            }
            if (LandingResolver.compatOwnsFinalState(compatSlab())) {
                throw helper.assertionException(
                        "a tagged compat slab must NOT be compat-owned at the placement gate: owning "
                                + "it is what made every placed compat slab mint nothing and render "
                                + "flush regardless of its aimed seat");
            }
            if (LandingResolver.compatOwnsFinalState(twinSlab())) {
                throw helper.assertionException(
                        "the non-compat twin must never be compat-owned");
            }
            if (!LandingResolver.compatOwnsFinalState(COMPAT_CUBE.defaultBlockState())) {
                throw helper.assertionException(
                        "a NON-slab compat block must remain compat-owned: the carve-out is the "
                                + "tagged-slab shape, not the whole namespace");
            }
        } finally {
            ELIGIBILITY_CLASSIFIER_TEST_GATE.set(Boolean.FALSE);
        }
        helper.succeed();
    }

    /**
     * THE PARITY ROW — the maintainer's requirement stated as one measurement: compat slab assets act
     * exactly like their vanilla counterparts. Three identical gestures on three identical lowered
     * supports, through the REAL {@code useOn} transaction with the eligibility gate live: a vanilla
     * stone slab, the non-compat twin, and the compat twin. All three must mint the SAME finite
     * stored seat, and it must be lowered (the scene guarantees it), so the equality cannot be
     * satisfied by three zeros or three missing facts.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aPlacedCompatSlabMintsTheSameSeatAsItsTwinAndVanilla(GameTestHelper helper) {
        ELIGIBILITY_CLASSIFIER_TEST_GATE.set(Boolean.TRUE);
        try {
            BlockPos vanillaSupport = loweredSupport(helper, new BlockPos(1, 2, 1), -1.0d);
            BlockPos twinSupport = loweredSupport(helper, new BlockPos(3, 2, 1), -1.0d);
            BlockPos compatSupport = loweredSupport(helper, new BlockPos(5, 2, 1), -1.0d);

            placeOnTop(helper, vanillaSupport, Blocks.STONE_SLAB.asItem());
            placeOnTop(helper, twinSupport, TWIN_SLAB_ITEM);
            placeOnTop(helper, compatSupport, COMPAT_SLAB_ITEM);

            double vanilla = storedOrFail(helper, vanillaSupport.above(), Blocks.STONE_SLAB,
                    "the vanilla reference slab");
            double twin = storedOrFail(helper, twinSupport.above(), TWIN_SLAB, "the twin slab");
            double compat = storedOrFail(helper, compatSupport.above(), COMPAT_SLAB, "the compat slab");

            if (!(vanilla < -EPS)) {
                throw helper.assertionException(
                        "premise drift: the reference gesture on a -0.5 support must mint a lowered "
                                + "seat, got " + vanilla + " — a flush reference would let the parity "
                                + "assertions below pass on three inert placements");
            }
            if (Math.abs(twin - vanilla) > EPS) {
                throw helper.assertionException(
                        "the non-compat twin diverged from vanilla (" + twin + " vs " + vanilla
                                + ") — the fixture itself is not slab-shaped, fix that before "
                                + "reading the compat assertion");
            }
            if (Math.abs(compat - vanilla) > EPS) {
                throw helper.assertionException(
                        "compat slab minted " + compat + " where vanilla minted " + vanilla
                                + " — a placed compat slab is an ordinary slab, and the placement "
                                + "transaction must mint it the same seat");
            }
        } finally {
            ELIGIBILITY_CLASSIFIER_TEST_GATE.set(Boolean.FALSE);
        }
        helper.succeed();
    }

    /**
     * The other half of the transaction discriminator, and the world-hole pin: a compat slab that
     * arrives WITHOUT a placement transaction — worldgen's shape, here {@code setBlock} — carries no
     * fact, reads flush through the public offset path, and is still excluded by the fall-through
     * gate. If this row ever sees a finite fact or a non-zero read, worldgen compat terrain has
     * started receiving Slabbed geometry, which is the see-through-seam defect class this line
     * already fixed once.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aTransactionlessCompatSlabCarriesNoFactAndReadsFlush(GameTestHelper helper) {
        ELIGIBILITY_CLASSIFIER_TEST_GATE.set(Boolean.TRUE);
        try {
            ServerLevel level = helper.getLevel();
            // A lowered neighbour beside it, so "flush" is measured against live temptation: the
            // generic rules WOULD lower this cell if the gate ever let them see it.
            loweredSupport(helper, new BlockPos(2, 2, 2), -0.5d);
            BlockPos cellRel = new BlockPos(3, 2, 2);
            helper.setBlock(cellRel.below(), Blocks.STONE.defaultBlockState());
            helper.setBlock(cellRel, compatSlab());
            BlockPos cell = helper.absolutePos(cellRel);

            double stored = SlabAnchorAttachment.storedPlacementDy(level, cell);
            if (Double.isFinite(stored)) {
                throw helper.assertionException(helper.relativePos(cell),
                        "a transactionless compat slab minted a fact (" + stored + ") — worldgen "
                                + "cannot forge a placement transaction, so nothing may mint one here");
            }
            double read = SlabSupport.getYOffset(level, cell, level.getBlockState(cell));
            if (Math.abs(read) > EPS) {
                throw helper.assertionException(helper.relativePos(cell),
                        "a factless compat slab read " + read + " through the public offset path — "
                                + "the factless fall-through must stay flush (the world-hole pin)");
            }
        } finally {
            ELIGIBILITY_CLASSIFIER_TEST_GATE.set(Boolean.FALSE);
        }
        helper.succeed();
    }

    /**
     * The OWNER-side read: a placement AIMED AT a placed compat slab measures that slab's recorded
     * seat, exactly as it would a vanilla owner's. Two columns whose OWNERS differ only by registry
     * namespace — the non-compat twin versus the compat twin, both freshly placed on identical
     * lowered supports — then the SAME held item (a vanilla stone slab, deliberately a different
     * item so no same-cell merge fires and the resolver must run) is aimed at each owner's top face.
     * The two new storeys must freeze the same seat.
     *
     * <p>This is the row that pins the store-first owner read ({@code visibleOwnerDy}): zero the
     * compat owner's depth before consulting the store — the pre-fix ordering — and the compat
     * column's new storey freezes a height half a block above the vanilla column's.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aPlacementAimedAtAPlacedCompatSlabInheritsItsSeat(GameTestHelper helper) {
        ELIGIBILITY_CLASSIFIER_TEST_GATE.set(Boolean.TRUE);
        try {
            BlockPos twinSupport = loweredSupport(helper, new BlockPos(1, 2, 3), -1.0d);
            BlockPos compatSupport = loweredSupport(helper, new BlockPos(4, 2, 3), -1.0d);

            placeOnTop(helper, twinSupport, TWIN_SLAB_ITEM);
            placeOnTop(helper, compatSupport, COMPAT_SLAB_ITEM);
            double twinOwner = storedOrFail(helper, twinSupport.above(), TWIN_SLAB, "the twin owner");
            double compatOwner =
                    storedOrFail(helper, compatSupport.above(), COMPAT_SLAB, "the compat owner");
            if (!(twinOwner < -EPS) || Math.abs(compatOwner - twinOwner) > EPS) {
                throw helper.assertionException(
                        "premise drift: both owners must carry the same lowered seat before the "
                                + "aimed gesture (twin=" + twinOwner + " compat=" + compatOwner + ")");
            }

            placeOnTop(helper, twinSupport.above(), Blocks.STONE_SLAB.asItem());
            placeOnTop(helper, compatSupport.above(), Blocks.STONE_SLAB.asItem());

            double aboveTwin = storedOrFail(helper, twinSupport.above(2), Blocks.STONE_SLAB,
                    "the storey above the twin owner");
            double aboveCompat = storedOrFail(helper, compatSupport.above(2), Blocks.STONE_SLAB,
                    "the storey above the compat owner");
            if (Math.abs(aboveCompat - aboveTwin) > EPS) {
                throw helper.assertionException(
                        "aiming at a placed compat slab froze " + aboveCompat + " where the identical "
                                + "gesture on the twin owner froze " + aboveTwin + " — the owner read "
                                + "must consult the store before the compat exclusion");
            }
        } finally {
            ELIGIBILITY_CLASSIFIER_TEST_GATE.set(Boolean.FALSE);
        }
        helper.succeed();
    }
}
