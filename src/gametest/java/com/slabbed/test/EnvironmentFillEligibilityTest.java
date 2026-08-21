package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * LAW 2 on this line: eligibility to lower follows GEOMETRY and BEHAVIOUR, never a block-class list.
 *
 * <p>THE VIOLATION THIS PINS: {@code shouldOffset} excluded a classname family — snow layers, carpet,
 * pale moss carpet, powder snow — from lowering at all. Carpet is player-placed and never
 * weather-deposited, so the hazard that justified the exclusion never applied to it; the only effect
 * was a carpet floating half a block above the lowered block it was lying on.
 *
 * <p>The exclusion is now {@link SlabSupport#isEnvironmentDepositedSurfaceFill}, which asks what a
 * block IS — does it carry a layer count, is it powder snow — rather than what it is called.
 *
 * <p><b>Read the two halves together.</b> The excluded rows alone would pass a predicate that
 * excludes everything; the admitted rows alone would pass one that excludes nothing. Only the pair
 * pins the boundary, and the carpet row is the one that fails if anyone reinstates the class family.
 */
public final class EnvironmentFillEligibilityTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void weatherDepositedFillIsExcluded(GameTestHelper helper) {
        // Snow layers carry LAYERS; powder snow is a full cube and carries none, so it needs the
        // second clause. Both are laid by weather across whole biomes — that is the hazard.
        if (!SlabSupport.isEnvironmentDepositedSurfaceFill(Blocks.SNOW.defaultBlockState())) {
            throw helper.assertionException("a snow layer is weather-deposited fill and must be excluded");
        }
        if (!SlabSupport.isEnvironmentDepositedSurfaceFill(Blocks.POWDER_SNOW.defaultBlockState())) {
            throw helper.assertionException(
                    "powder snow is weather-deposited fill and must be excluded — it is a full cube, so "
                            + "a layer-count test alone does not catch it");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void playerPlacedBlocksAreNotExcluded(GameTestHelper helper) {
        // THE ANTI-REGRESSION ROW. Carpet was excluded by class name and floated above its support.
        // Reinstating the class family fails here and nowhere else.
        if (SlabSupport.isEnvironmentDepositedSurfaceFill(Blocks.MOSS_CARPET.defaultBlockState())) {
            throw helper.assertionException(
                    "carpet is player-placed, never weather-deposited — excluding it is the block-class "
                            + "reasoning LAW 2 forbids, and it makes carpet float above its support");
        }
        if (SlabSupport.isEnvironmentDepositedSurfaceFill(Blocks.STONE.defaultBlockState())) {
            throw helper.assertionException("a plain full cube is not weather-deposited fill");
        }
        if (SlabSupport.isEnvironmentDepositedSurfaceFill(Blocks.STONE_SLAB.defaultBlockState())) {
            throw helper.assertionException("a slab is not weather-deposited fill");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void thicknessPredicateStillCoversCarpet(GameTestHelper helper) {
        // isThinTopLayer keeps its THICKNESS role — the column walks still terminate on a carpet,
        // because whatever rests on a carpet rests on the carpet. Narrowing eligibility must not have
        // narrowed this: if it did, a block on a carpet would inherit the slab under the carpet.
        if (!SlabSupport.isThinTopLayer(Blocks.MOSS_CARPET.defaultBlockState())) {
            throw helper.assertionException(
                    "carpet must still count as a thin top layer for column-walk termination");
        }
        if (!SlabSupport.isThinTopLayer(Blocks.SNOW.defaultBlockState())) {
            throw helper.assertionException("a snow layer must still count as a thin top layer");
        }
        helper.succeed();
    }
}
