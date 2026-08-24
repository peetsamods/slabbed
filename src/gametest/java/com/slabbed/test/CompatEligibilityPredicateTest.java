package com.slabbed.test;

import com.slabbed.compat.CompatHooks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Makes the SHIPPED Terrain Slabs eligibility predicate executable.
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
 * <p>The rows below pin the rule as it ships TODAY: eligibility is decided by REGISTRY NAMESPACE. That
 * is the LAW 2 violation (LAW.md: eligibility follows geometry, "never a block-class allow-list, a
 * namespace string"), recorded here as an executable fact rather than a comment. A change that makes
 * eligibility follow behaviour MUST flip {@link #twinsDifferOnlyByNamespaceAndTheShippedRuleSplitsThem}
 * — that row is the tripwire, and its flip is the intended signal, not a regression. Update it in the
 * same commit that changes the predicate, and say so in the message.
 *
 * <p>The two fixtures are TWINS: same class, same properties copied from {@code STONE_SLAB}, same
 * blockstate shape. They differ ONLY in registry namespace. Nothing about geometry, class, or shape
 * can tell them apart, so a row that separates them is reading the namespace rule and nothing else.
 */
public final class CompatEligibilityPredicateTest {

    private static final ThreadLocal<Boolean> ELIGIBILITY_CLASSIFIER_TEST_GATE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final Identifier COMPAT_ID =
            Identifier.fromNamespaceAndPath("terrain_slabs", "eligibility_probe_slab");
    private static final Identifier TWIN_ID =
            Identifier.fromNamespaceAndPath("slabbed_gametest", "eligibility_twin_slab");

    private static final ResourceKey<Block> COMPAT_KEY = ResourceKey.create(Registries.BLOCK, COMPAT_ID);
    private static final ResourceKey<Block> TWIN_KEY = ResourceKey.create(Registries.BLOCK, TWIN_ID);

    private static final Block COMPAT_SLAB = new SlabBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB).setId(COMPAT_KEY));
    private static final Block TWIN_SLAB = new SlabBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB).setId(TWIN_KEY));

    /** Registers both twins before registry freeze. */
    public static final class CompatEligibilityFixtureEntrypoint implements ModInitializer {
        @Override
        public void onInitialize() {
            if (!BuiltInRegistries.BLOCK.containsKey(COMPAT_ID)) {
                Registry.register(BuiltInRegistries.BLOCK, COMPAT_ID, COMPAT_SLAB);
            }
            if (!BuiltInRegistries.BLOCK.containsKey(TWIN_ID)) {
                Registry.register(BuiltInRegistries.BLOCK, TWIN_ID, TWIN_SLAB);
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

    /**
     * THE TRIPWIRE. Two blocks indistinguishable by geometry, class and shape; the shipped rule
     * separates them purely on namespace. Deliberately asserts the CURRENT behaviour so that the LAW 2
     * fix cannot land silently — when eligibility becomes behaviour-driven, the compat twin stops
     * being skipped on identity alone and this row goes RED on purpose.
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
                        "the shipped eligibility predicate did not reach its namespace rule: a "
                                + "terrain_slabs-namespaced slab must be skipped while the rule is "
                                + "identity-based. If eligibility is now behaviour-based, this row is "
                                + "the intended tripwire - retarget it in the same commit.");
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
}
