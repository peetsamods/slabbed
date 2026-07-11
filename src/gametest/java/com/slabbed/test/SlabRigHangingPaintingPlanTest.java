package com.slabbed.test;

import com.slabbed.command.SlabRigHangingArtifacts;
import com.slabbed.command.SlabRigHangingCatalog;
import com.slabbed.command.SlabRigHangingPaintingPlan;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure, registered RIG-3B2A planner contract tests. */
public final class SlabRigHangingPaintingPlanTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void paintingPlannerBindsCompleteRuntimeUniverse(GameTestHelper helper) {
        SlabRigHangingPaintingPlan.Universe first = universe(helper);
        SlabRigHangingPaintingPlan.Universe second = universe(helper);
        if (!first.equals(second)
                || first.paintingRoutes().size() != 16
                || first.topologies().size() != 64
                || first.selectors().size() != 52
                || first.totalAttempts() != 53_248L
                || first.selectorPageCount() != 4
                || first.pageCount() != 4_096
                || !first.universeHash().matches("[0-9a-f]{64}")
                || !first.canonicalTsv().contains(
                "footprint_expansion\trepeat_topology_per_lateral_column\n")) {
            throw helper.assertionException("RIG-3B2A universe identity/counters drifted: " + first);
        }

        SlabRigHangingPaintingPlan.Selector unpinned = first.selectors().getFirst();
        if (unpinned.index() != 0
                || unpinned.kind() != SlabRigHangingPaintingPlan.SelectorKind.UNPINNED
                || unpinned.registryIndex() != -1
                || !"ABSENT".equals(unpinned.componentType())
                || !"ABSENT".equals(unpinned.componentValue())
                || !"#minecraft:placeable".equals(unpinned.expectedDomain())) {
            throw helper.assertionException("selector zero is not the explicit unpinned control: " + unpinned);
        }
        List<SlabRigHangingPaintingPlan.Selector> pinned = first.selectors().subList(1, 52);
        if (pinned.stream().filter(selector -> selector.kind()
                == SlabRigHangingPaintingPlan.SelectorKind.PINNED_TAGGED).count() != 47
                || pinned.stream().filter(selector -> selector.kind()
                == SlabRigHangingPaintingPlan.SelectorKind.PINNED_UNTAGGED).count() != 4
                || pinned.stream().map(SlabRigHangingPaintingPlan.Selector::variantId)
                .distinct().count() != 51) {
            throw helper.assertionException("pinned tagged/untagged selector partition drifted");
        }
        List<String> untagged = pinned.stream().filter(selector -> selector.kind()
                        == SlabRigHangingPaintingPlan.SelectorKind.PINNED_UNTAGGED)
                .map(SlabRigHangingPaintingPlan.Selector::variantId).toList();
        if (!untagged.equals(List.of("minecraft:earth", "minecraft:fire",
                "minecraft:water", "minecraft:wind"))) {
            throw helper.assertionException("untagged selector rows drifted: " + untagged);
        }
        System.out.println("RIG3B2A-UNIVERSE | hash=" + first.universeHash()
                + " routes=" + first.paintingRoutes().size()
                + " selectors=" + first.selectors().size()
                + " topologies=" + first.topologies().size()
                + " attempts=" + first.totalAttempts() + " pages=" + first.pageCount());
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void paintingPlannerPagesOneExactBaseCaseAsSixteenSixteenSixteenFour(GameTestHelper helper) {
        SlabRigHangingPaintingPlan.Universe universe = universe(helper);
        int[] expectedSizes = {16, 16, 16, 4};
        List<Integer> selectors = new ArrayList<>();
        Set<String> attemptIds = new HashSet<>();
        for (int pageNumber = 1; pageNumber <= 4; pageNumber++) {
            SlabRigHangingPaintingPlan.PagePlan first =
                    SlabRigHangingPaintingPlan.page(universe, 6143, 42, pageNumber);
            SlabRigHangingPaintingPlan.PagePlan repeat =
                    SlabRigHangingPaintingPlan.page(universe, 6143, 42, pageNumber);
            if (!first.equals(repeat) || first.cases().size() != expectedSizes[pageNumber - 1]
                    || first.routeIndex() != 6143 || first.topologyIndex() != 42
                    || first.selectorPage() != pageNumber
                    || !first.planHash().matches("[0-9a-f]{64}")) {
                throw helper.assertionException("fixed-case selector page drifted: " + first);
            }
            for (SlabRigHangingPaintingPlan.CasePlan entry : first.cases()) {
                selectors.add(entry.selector().index());
                if (!attemptIds.add(entry.attemptId())
                        || !entry.baseCaseId().equals(first.cases().getFirst().baseCaseId())) {
                    throw helper.assertionException("attempt/base-case identity collapsed on page " + pageNumber);
                }
            }
        }
        List<Integer> expected = java.util.stream.IntStream.range(0, 52).boxed().toList();
        if (!selectors.equals(expected) || attemptIds.size() != 52) {
            throw helper.assertionException("selector page round-trip omitted/reordered attempts: " + selectors);
        }

        Set<Integer> addressablePages = new HashSet<>();
        for (int routeOrdinal = 0; routeOrdinal < universe.paintingRoutes().size(); routeOrdinal++) {
            for (int topology = 0; topology < 64; topology++) {
                for (int selectorPage = 1; selectorPage <= 4; selectorPage++) {
                    int addressable = routeOrdinal * 64 * 4 + topology * 4 + selectorPage;
                    if (!addressablePages.add(addressable)) {
                        throw helper.assertionException("addressable page identity collision: "
                                + addressable);
                    }
                }
            }
        }
        SlabRigHangingPaintingPlan.PagePlan firstAddress = SlabRigHangingPaintingPlan.page(universe,
                universe.paintingRoutes().getFirst().index(), 0, 1);
        SlabRigHangingPaintingPlan.PagePlan lastAddress = SlabRigHangingPaintingPlan.page(universe,
                universe.paintingRoutes().getLast().index(), 63, 4);
        if (addressablePages.size() != 4_096
                || addressablePages.stream().min(Integer::compareTo).orElseThrow() != 1
                || addressablePages.stream().max(Integer::compareTo).orElseThrow() != 4_096
                || firstAddress.addressablePage() != 1 || lastAddress.addressablePage() != 4_096) {
            throw helper.assertionException("complete addressable page universe is not 1..4096");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void paintingPlannerMaterializesExactFourByFourWallOverRepeatedSbsbs(GameTestHelper helper) {
        SlabRigHangingPaintingPlan.Universe universe = universe(helper);
        int pointerSelector = universe.selectors().stream()
                .filter(selector -> "minecraft:pointer".equals(selector.variantId()))
                .findFirst().orElseThrow(() -> helper.assertionException("missing pointer selector"))
                .index();
        SlabRigHangingPaintingPlan.PagePlan page = SlabRigHangingPaintingPlan.page(
                universe, 6143, 42, pointerSelector / 16 + 1);
        SlabRigHangingPaintingPlan.CasePlan pointer = page.cases().stream()
                .filter(entry -> entry.selector().index() == pointerSelector)
                .findFirst().orElseThrow(() -> helper.assertionException("pointer page omitted pointer"));

        if (!"stack:SBSBS".equals(pointer.topology().id())
                || !"SBSBS".equals(pointer.topology().recipe())
                || pointer.clickedFace() != Direction.WEST
                || pointer.lateralDirection() != Direction.SOUTH
                || !pointer.anchor().equals(pointer.clicked().west())
                || pointer.foundations().size() != 4
                || pointer.topologyCells().size() != 36
                || pointer.backingCells().size() != 16
                || pointer.supportCells().size() != 16
                || pointer.entityAirCells().size() != 16
                || pointer.clearOwnedCells().size() != 52
                || pointer.reservedCells().size() != 68
                || !SlabRigHangingPaintingPlan.FOOTPRINT_EXPANSION
                .equals(pointer.footprintExpansion())) {
            throw helper.assertionException("route6143/topology42 fixture geometry drifted: " + pointer);
        }
        if (!pointer.foundations().stream().map(foundation -> foundation.lateralIndex())
                .toList().equals(List.of(-1, 0, 1, 2))) {
            throw helper.assertionException("4x4 wall lost exact lateral foundation indices");
        }
        for (SlabRigHangingPaintingPlan.FoundationPlan foundation : pointer.foundations()) {
            if (foundation.topologyCells().size() != 9 || foundation.backingCells().size() != 4
                    || foundation.backingCells().stream()
                    .anyMatch(cell -> !"minecraft:stone".equals(cell.stateRecipe())
                            || !"PLAYER_ITEM_USEON".equals(cell.placementMethod()))) {
                throw helper.assertionException("SBSBS/backing column is not exact: " + foundation);
            }
        }
        Set<BlockPos> clear = pointer.clearOwnedCells().stream()
                .map(SlabRigHangingPaintingPlan.CellPlan::relativePos)
                .collect(java.util.stream.Collectors.toSet());
        Set<BlockPos> reserved = new HashSet<>(pointer.reservedCells());
        Set<BlockPos> air = new HashSet<>(pointer.entityAirCells());
        Set<BlockPos> overlap = new HashSet<>(clear);
        overlap.retainAll(air);
        if (clear.size() != 52 || reserved.size() != 68 || !reserved.containsAll(clear)
                || !reserved.containsAll(air) || !overlap.isEmpty()) {
            throw helper.assertionException("clear ownership and reserved entity-air cells blurred");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void paintingPlannerRejectsIdentityGeometryAndRangeTampering(GameTestHelper helper) {
        SlabRigHangingPaintingPlan.Universe universe = universe(helper);
        SlabRigHangingPaintingPlan.PagePlan page =
                SlabRigHangingPaintingPlan.page(universe, 6143, 42, 1);

        SlabRigHangingPaintingPlan.Universe badUniverse = new SlabRigHangingPaintingPlan.Universe(
                universe.schema(), universe.catalogHash(), universe.topologyCatalogHash(),
                universe.runtimeExecutionIdentity(), universe.runtimeContentSha256(),
                universe.paintingRegistryHash(), universe.paintingRoutes(), universe.topologies(),
                universe.selectors(), universe.totalAttempts(), universe.selectorPageCount(),
                universe.pageCount(), "0".repeat(64), universe.canonicalTsv());
        expectRefusal(helper, () -> SlabRigHangingPaintingPlan.page(badUniverse, 6143, 42, 1),
                "tampered universe hash");
        expectRefusal(helper, () -> SlabRigHangingPaintingPlan.page(universe, 6143, 42, 0),
                "selector page zero");
        expectRefusal(helper, () -> SlabRigHangingPaintingPlan.page(universe, 999_999, 42, 1),
                "unknown route");

        List<SlabRigHangingPaintingPlan.CasePlan> missing = new ArrayList<>(page.cases());
        missing.removeLast();
        SlabRigHangingPaintingPlan.PagePlan badPage = new SlabRigHangingPaintingPlan.PagePlan(
                page.schema(), page.universeHash(), page.addressablePage(), page.pageCount(),
                page.routeOrdinal(), page.routeIndex(), page.topologyIndex(), page.selectorPage(),
                page.selectorPageCount(), page.semanticPageId(), missing, page.planHash(),
                page.canonicalTsv());
        expectRefusal(helper, () -> SlabRigHangingPaintingPlan.validatePage(universe, badPage),
                "omitted planned case");

        SlabRigHangingPaintingPlan.PagePlan badCounters = new SlabRigHangingPaintingPlan.PagePlan(
                page.schema(), page.universeHash(), page.addressablePage(), page.pageCount() + 1,
                page.routeOrdinal(), page.routeIndex(), page.topologyIndex(), page.selectorPage(),
                page.selectorPageCount() + 1, page.semanticPageId(), page.cases(), page.planHash(),
                page.canonicalTsv());
        expectRefusal(helper, () -> SlabRigHangingPaintingPlan.validatePage(universe, badCounters),
                "self-hashed but false page counters");

        SlabRigHangingPaintingPlan.PagePlan badAddress = new SlabRigHangingPaintingPlan.PagePlan(
                page.schema(), page.universeHash(), page.addressablePage() + 1, page.pageCount(),
                page.routeOrdinal(), page.routeIndex(), page.topologyIndex(), page.selectorPage(),
                page.selectorPageCount(), page.semanticPageId(), page.cases(), page.planHash(),
                page.canonicalTsv());
        expectRefusal(helper, () -> SlabRigHangingPaintingPlan.validatePage(universe, badAddress),
                "self-hashed but false addressable page");

        SlabRigHangingPaintingPlan.PagePlan badSemantic = new SlabRigHangingPaintingPlan.PagePlan(
                page.schema(), page.universeHash(), page.addressablePage(), page.pageCount(),
                page.routeOrdinal(), page.routeIndex(), page.topologyIndex(), page.selectorPage(),
                page.selectorPageCount(), "painting-page-v1:sha256:" + "0".repeat(64),
                page.cases(), page.planHash(), page.canonicalTsv());
        expectRefusal(helper, () -> SlabRigHangingPaintingPlan.validatePage(universe, badSemantic),
                "self-hashed but false semantic page ID");
        helper.succeed();
    }

    private static SlabRigHangingPaintingPlan.Universe universe(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot catalog = SlabRigHangingCatalog.snapshot();
        SlabRigHangingArtifacts.RuntimeSnapshot runtime =
                SlabRigHangingArtifacts.snapshot(catalog, helper.getLevel().registryAccess());
        return SlabRigHangingPaintingPlan.snapshot(catalog, runtime);
    }

    private static void expectRefusal(GameTestHelper helper, Runnable action, String label) {
        try {
            action.run();
            throw helper.assertionException("planner accepted " + label);
        } catch (IllegalArgumentException expected) {
            // Expected fail-closed path.
        }
    }
}
