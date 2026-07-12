package com.slabbed.test;

import com.slabbed.command.SlabRigHangingArtifacts;
import com.slabbed.command.SlabRigHangingCatalog;
import com.slabbed.command.SlabRigHangingDirectFixture;
import com.slabbed.command.SlabRigHangingPaintingPlan;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.LinkedHashSet;

/** Pure production-fixture adapter contract; no command, persistence, or world mutation. */
public final class SlabRigHangingDirectFixtureTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directFixtureAdaptsOnlyTheReviewedPageWithExactOwnership(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot catalog = SlabRigHangingCatalog.snapshot();
        SlabRigHangingArtifacts.RuntimeSnapshot runtime = SlabRigHangingArtifacts.snapshot(
                catalog, helper.getLevel().registryAccess());
        SlabRigHangingPaintingPlan.Universe universe =
                SlabRigHangingPaintingPlan.snapshot(catalog, runtime);
        LinkedHashSet<String> selectorIds = new LinkedHashSet<>();
        for (int selectorPage = 1; selectorPage <= 4; selectorPage++) {
            SlabRigHangingPaintingPlan.PagePlan page = SlabRigHangingPaintingPlan.page(universe,
                    SlabRigHangingDirectFixture.ROUTE_INDEX,
                    SlabRigHangingDirectFixture.TOPOLOGY_INDEX, selectorPage);
            BlockPos origin = helper.absolutePos(new BlockPos(8 + selectorPage * 48, 3, 8));
            SlabRigHangingDirectFixture.AbsolutePage adapted =
                    SlabRigHangingDirectFixture.adapt(universe, page, origin);
            int caseCount = page.cases().size();
            int expectedCases = selectorPage == 4 ? 4 : 16;
            if (caseCount != expectedCases || adapted.cases().size() != caseCount
                    || adapted.reservedCells().size() != caseCount * 68
                    || adapted.clearOwnedCells().size() != caseCount * 52
                    || adapted.entityAirCells().size() != caseCount * 16
                    || adapted.bounds().xSize() > 40 || adapted.bounds().ySize() > 20
                    || adapted.bounds().zSize() > 40) {
                throw helper.assertionException(
                        "absolute direct fixture lost page/cardinality counts: " + adapted);
            }
            LinkedHashSet<BlockPos> clear = new LinkedHashSet<>();
            adapted.clearOwnedCells().forEach(cell -> {
                if (!clear.add(cell.pos())
                        || !SlabRigHangingDirectFixture.expectedState(cell.plan().stateRecipe())
                        .getBlock().asItem().equals(
                        SlabRigHangingDirectFixture.itemForRecipe(cell.plan().stateRecipe()))) {
                    throw helper.assertionException("invalid/duplicate authored cell " + cell);
                }
            });
            LinkedHashSet<BlockPos> reserved = new LinkedHashSet<>(adapted.reservedCells());
            LinkedHashSet<BlockPos> airOnly = new LinkedHashSet<>(reserved);
            airOnly.removeAll(clear);
            if (!airOnly.equals(new LinkedHashSet<>(adapted.entityAirCells()))
                    || !reserved.containsAll(clear)) {
                throw helper.assertionException(
                        "reserved/clear/entity-air ownership is not an exact partition");
            }
            for (SlabRigHangingDirectFixture.AbsoluteCase entry : adapted.cases()) {
                if (!selectorIds.add(entry.plan().selector().semanticId())
                        || entry.plan().foundations().size() != 4
                        || entry.topologyCells().size() != 36
                        || entry.backingCells().size() != 16 || entry.supportCells().size() < 1
                        || !entry.anchor().equals(
                        entry.clicked().relative(entry.plan().clickedFace()))) {
                    throw helper.assertionException(
                            "absolute case lost unique selector/SBSBS semantics: "
                                    + entry.plan().attemptId());
                }
            }
        }
        LinkedHashSet<String> expectedSelectors = new LinkedHashSet<>();
        universe.selectors().forEach(selector -> expectedSelectors.add(selector.semanticId()));
        if (selectorIds.size() != 52 || !selectorIds.equals(expectedSelectors)) {
            throw helper.assertionException(
                    "pages 1..4 did not cover the exact 52-selector universe");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directFixtureRejectsEveryUnapprovedAxis(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot catalog = SlabRigHangingCatalog.snapshot();
        SlabRigHangingArtifacts.RuntimeSnapshot runtime = SlabRigHangingArtifacts.snapshot(
                catalog, helper.getLevel().registryAccess());
        SlabRigHangingPaintingPlan.Universe universe =
                SlabRigHangingPaintingPlan.snapshot(catalog, runtime);
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        int otherRoute = universe.paintingRoutes().stream()
                .mapToInt(SlabRigHangingCatalog.Route::index)
                .filter(index -> index != SlabRigHangingDirectFixture.ROUTE_INDEX)
                .findFirst().orElseThrow();
        assertRejected(helper, universe,
                SlabRigHangingPaintingPlan.page(universe, otherRoute, 42, 1), origin);
        assertRejected(helper, universe,
                SlabRigHangingPaintingPlan.page(universe, 6143, 41, 1), origin);
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper,
                                       SlabRigHangingPaintingPlan.Universe universe,
                                       SlabRigHangingPaintingPlan.PagePlan page,
                                       BlockPos origin) {
        try {
            SlabRigHangingDirectFixture.adapt(universe, page, origin);
            throw helper.assertionException("production fixture accepted unapproved page "
                    + page.routeIndex() + '/' + page.topologyIndex() + '/' + page.selectorPage());
        } catch (IllegalArgumentException expected) {
            // Exact rejection is the contract.
        }
    }
}
