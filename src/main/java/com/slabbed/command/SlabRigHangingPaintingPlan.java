package com.slabbed.command;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure RIG-3B2A planner for the complete direct-painting input universe.
 *
 * <p>The planner does not read or mutate a world and does not register a command. It binds the exact
 * RIG-3A direct painting routes, RIG-2 topologies, and RIG-3B1 live painting selectors to deterministic
 * 4x4 selector pages. World/session identity and observed outcomes belong to the separate kernel artifact
 * layer.
 */
public final class SlabRigHangingPaintingPlan {

    public static final String SCHEMA = "slabbed-rig-hanging-painting-plan-v1";
    public static final String EXECUTION_CONTRACT =
            "rig3b2a-fixed-route-topology-selector-pages-v1";
    public static final String FOOTPRINT_EXPANSION =
            "repeat_topology_per_lateral_column";
    public static final String UNPINNED_DOMAIN = "#minecraft:placeable";
    public static final int PAGE_SIZE = 16;
    public static final int BOARD_SIDE = 4;
    public static final int TILE_PITCH = 8;
    public static final int BACKING_SIDE = 4;
    public static final int EXPECTED_PAINTING_ROUTES = 16;
    public static final int EXPECTED_TOPOLOGIES = 64;
    public static final int EXPECTED_REGISTRY_VARIANTS = 51;
    public static final int EXPECTED_SELECTORS = 52;
    public static final int EXPECTED_SELECTOR_PAGES = 4;
    public static final long EXPECTED_TOTAL_ATTEMPTS = 53_248L;
    public static final int EXPECTED_TOTAL_PAGES = 4_096;

    private static final Comparator<BlockPos> POSITION_ORDER =
            Comparator.<BlockPos>comparingInt(pos -> pos.getX())
                    .thenComparingInt(BlockPos::getY)
                    .thenComparingInt(BlockPos::getZ);
    private static final Set<String> WALL_SUPPORT_FRAMES = Set.of(
            "WALL_FULL", "WALL_BOTTOM_SLAB", "WALL_TOP_SLAB", "WALL_DOUBLE_SLAB");

    private SlabRigHangingPaintingPlan() {
    }

    public enum SelectorKind {
        UNPINNED,
        PINNED_TAGGED,
        PINNED_UNTAGGED
    }

    /** One input selector. Row zero is the unpinned control; remaining rows are registry-backed. */
    public record Selector(int index, SelectorKind kind, int registryIndex, String variantId,
                           boolean randomPlaceable, int width, int height,
                           int lateralMin, int lateralMax, int verticalMin, int verticalMax,
                           String componentType, String componentValue, String expectedDomain,
                           String semanticId) {
        public Selector {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(variantId, "variantId");
            Objects.requireNonNull(componentType, "componentType");
            Objects.requireNonNull(componentValue, "componentValue");
            Objects.requireNonNull(expectedDomain, "expectedDomain");
            Objects.requireNonNull(semanticId, "semanticId");
        }
    }

    /** One planned authored cell, relative to the page origin. */
    public record CellPlan(BlockPos relativePos, String role, String stateRecipe,
                           String placementMethod) {
        public CellPlan {
            relativePos = relativePos.immutable();
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(stateRecipe, "stateRecipe");
            Objects.requireNonNull(placementMethod, "placementMethod");
        }
    }

    /** One of four repeated topology foundations and its separate wall column. */
    public record FoundationPlan(int lateralIndex, List<CellPlan> topologyCells,
                                 List<CellPlan> backingCells) {
        public FoundationPlan {
            topologyCells = List.copyOf(topologyCells);
            backingCells = List.copyOf(backingCells);
        }
    }

    /** One selector attempt in a fixed route/topology page. */
    public record CasePlan(int ordinal, long executionIndex, long baseCaseIndex,
                           String baseCaseId, String attemptId,
                           SlabRigHangingCatalog.Route route,
                           SlabRigCaseCatalog.Topology topology, Selector selector,
                           BlockPos tileOrigin, Direction clickedFace,
                           Direction backingDirection, Direction lateralDirection,
                           BlockPos clicked, BlockPos anchor,
                           List<FoundationPlan> foundations, List<CellPlan> topologyCells,
                           List<CellPlan> backingCells, List<BlockPos> supportCells,
                           List<BlockPos> entityAirCells, List<CellPlan> clearOwnedCells,
                           List<BlockPos> reservedCells, String footprintExpansion) {
        public CasePlan {
            Objects.requireNonNull(baseCaseId, "baseCaseId");
            Objects.requireNonNull(attemptId, "attemptId");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(topology, "topology");
            Objects.requireNonNull(selector, "selector");
            tileOrigin = tileOrigin.immutable();
            Objects.requireNonNull(clickedFace, "clickedFace");
            Objects.requireNonNull(backingDirection, "backingDirection");
            Objects.requireNonNull(lateralDirection, "lateralDirection");
            clicked = clicked.immutable();
            anchor = anchor.immutable();
            foundations = List.copyOf(foundations);
            topologyCells = List.copyOf(topologyCells);
            backingCells = List.copyOf(backingCells);
            supportCells = immutablePositions(supportCells);
            entityAirCells = immutablePositions(entityAirCells);
            clearOwnedCells = List.copyOf(clearOwnedCells);
            reservedCells = immutablePositions(reservedCells);
            Objects.requireNonNull(footprintExpansion, "footprintExpansion");
        }
    }

    /** Immutable authoritative axes and their content identity. */
    public record Universe(String schema, String catalogHash, String topologyCatalogHash,
                           String runtimeExecutionIdentity, String runtimeContentSha256,
                           String paintingRegistryHash,
                           List<SlabRigHangingCatalog.Route> paintingRoutes,
                           List<SlabRigCaseCatalog.Topology> topologies,
                           List<Selector> selectors, long totalAttempts,
                           int selectorPageCount, int pageCount, String universeHash,
                           String canonicalTsv) {
        public Universe {
            paintingRoutes = List.copyOf(paintingRoutes);
            topologies = List.copyOf(topologies);
            selectors = List.copyOf(selectors);
            Objects.requireNonNull(canonicalTsv, "canonicalTsv");
        }
    }

    /** One relative 4x4 page for an exact route, topology, and selector page. */
    public record PagePlan(String schema, String universeHash, int addressablePage,
                           int pageCount, int routeOrdinal, int routeIndex,
                           int topologyIndex, int selectorPage, int selectorPageCount,
                           String semanticPageId, List<CasePlan> cases,
                           String planHash, String canonicalTsv) {
        public PagePlan {
            cases = List.copyOf(cases);
            Objects.requireNonNull(canonicalTsv, "canonicalTsv");
        }
    }

    /** Reconstructs every planner axis from authoritative RIG-3A/RIG-3B1 snapshots. */
    public static Universe snapshot(SlabRigHangingCatalog.Snapshot catalog,
                                    SlabRigHangingArtifacts.RuntimeSnapshot runtime) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(runtime, "runtime");
        if (!catalog.catalogHash().equals(runtime.catalogHash())) {
            throw new IllegalArgumentException("RIG-3A/RIG-3B1 catalog identity disagreement");
        }
        if (!SlabRigHangingCatalog.SCHEMA.equals(catalog.schema())
                || !SlabRigHangingArtifacts.SCHEMA.equals(runtime.schema())) {
            throw new IllegalArgumentException("unsupported RIG-3A/RIG-3B1 schema");
        }

        List<SlabRigHangingCatalog.Route> routes = catalog.routes().stream()
                .filter(route -> "minecraft:painting".equals(route.subjectId()))
                .sorted(Comparator.comparingInt(SlabRigHangingCatalog.Route::index))
                .toList();
        validateRoutes(routes);
        List<SlabRigCaseCatalog.Topology> topologies = catalog.topologies();
        validateTopologies(topologies);
        List<Selector> selectors = selectors(runtime);

        long totalAttempts = Math.multiplyExact(
                Math.multiplyExact((long) routes.size(), (long) topologies.size()),
                (long) selectors.size());
        int selectorPages = (selectors.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int pages = Math.multiplyExact(Math.multiplyExact(routes.size(), topologies.size()),
                selectorPages);
        if (totalAttempts != EXPECTED_TOTAL_ATTEMPTS || selectorPages != EXPECTED_SELECTOR_PAGES
                || pages != EXPECTED_TOTAL_PAGES) {
            throw new IllegalStateException("RIG-3B2A universe drift: attempts=" + totalAttempts
                    + " selectorPages=" + selectorPages + " pages=" + pages);
        }

        String body = canonicalUniverseBody(catalog, runtime, routes, topologies, selectors,
                totalAttempts, selectorPages, pages);
        String universeHash = sha256(body);
        String tsv = insertIdentity(body, "universe_hash", universeHash);
        Universe universe = new Universe(SCHEMA, catalog.catalogHash(), catalog.topologyCatalogHash(),
                runtime.executionIdentity(), runtime.runtimeContentSha256(),
                runtime.paintingRegistryHash(), routes, topologies, selectors, totalAttempts,
                selectorPages, pages, universeHash, tsv);
        validateUniverse(universe);
        return universe;
    }

    /** Plans one fixed-route/fixed-topology selector page using only relative coordinates. */
    public static PagePlan page(Universe universe, int routeIndex, int topologyIndex,
                                int selectorPage) {
        validateUniverse(universe);
        int routeOrdinal = -1;
        SlabRigHangingCatalog.Route route = null;
        for (int i = 0; i < universe.paintingRoutes().size(); i++) {
            if (universe.paintingRoutes().get(i).index() == routeIndex) {
                routeOrdinal = i;
                route = universe.paintingRoutes().get(i);
                break;
            }
        }
        if (route == null) {
            throw new IllegalArgumentException("painting route index is not in RIG-3B2A: " + routeIndex);
        }
        if (topologyIndex < 0 || topologyIndex >= universe.topologies().size()
                || universe.topologies().get(topologyIndex).index() != topologyIndex) {
            throw new IllegalArgumentException("topology index is not canonical: " + topologyIndex);
        }
        if (selectorPage < 1 || selectorPage > universe.selectorPageCount()) {
            throw new IllegalArgumentException("selector page must be 1.."
                    + universe.selectorPageCount() + ", got " + selectorPage);
        }
        SlabRigCaseCatalog.Topology topology = universe.topologies().get(topologyIndex);
        Direction face = horizontalDirection(route.clickedFace());
        int from = (selectorPage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, universe.selectors().size());
        List<CasePlan> cases = new ArrayList<>(to - from);
        for (int selectorIndex = from; selectorIndex < to; selectorIndex++) {
            int ordinal = selectorIndex - from;
            int row = ordinal / BOARD_SIDE;
            int column = ordinal % BOARD_SIDE;
            BlockPos tileOrigin = BlockPos.ZERO
                    .relative(face.getCounterClockWise(), column * TILE_PITCH)
                    .relative(face.getOpposite(), row * TILE_PITCH);
            cases.add(planCase(universe, routeOrdinal, route, topology,
                    universe.selectors().get(selectorIndex), ordinal, tileOrigin, face));
        }
        validatePageCases(cases, to - from);

        int addressablePage = routeOrdinal * universe.topologies().size()
                * universe.selectorPageCount()
                + topologyIndex * universe.selectorPageCount() + selectorPage;
        String baseCaseId = SlabRigHangingCatalog.semanticCaseId(
                SlabRigHangingCatalog.CaseKind.DIRECT, route, null, "none", topology);
        String semanticPageId = "painting-page-v1:sha256:" + sha256(SCHEMA + '\0'
                + universe.universeHash() + '\0' + baseCaseId + '\0' + selectorPage);
        String body = canonicalPageBody(universe, addressablePage, routeOrdinal, route,
                topology, selectorPage, semanticPageId, cases);
        String planHash = sha256(body);
        PagePlan page = new PagePlan(SCHEMA, universe.universeHash(), addressablePage,
                universe.pageCount(), routeOrdinal, route.index(), topology.index(), selectorPage,
                universe.selectorPageCount(), semanticPageId, cases, planHash,
                insertIdentity(body, "plan_hash", planHash));
        validatePage(universe, page);
        return page;
    }

    /** Revalidates a page against its universe and canonical bytes. */
    public static void validatePage(Universe universe, PagePlan page) {
        validateUniverse(universe);
        Objects.requireNonNull(page, "page");
        if (!SCHEMA.equals(page.schema()) || !universe.universeHash().equals(page.universeHash())
                || page.pageCount() != universe.pageCount()
                || page.selectorPageCount() != universe.selectorPageCount()
                || page.routeOrdinal() < 0
                || page.routeOrdinal() >= universe.paintingRoutes().size()
                || page.topologyIndex() < 0
                || page.topologyIndex() >= universe.topologies().size()
                || page.selectorPage() < 1
                || page.selectorPage() > universe.selectorPageCount()) {
            throw new IllegalArgumentException("page/universe identity disagreement");
        }
        validatePageCases(page.cases(), page.selectorPage() == universe.selectorPageCount()
                ? universe.selectors().size() - (page.selectorPage() - 1) * PAGE_SIZE : PAGE_SIZE);
        SlabRigHangingCatalog.Route route = universe.paintingRoutes().get(page.routeOrdinal());
        SlabRigCaseCatalog.Topology topology = universe.topologies().get(page.topologyIndex());
        int expectedAddressablePage = page.routeOrdinal() * universe.topologies().size()
                * universe.selectorPageCount()
                + page.topologyIndex() * universe.selectorPageCount() + page.selectorPage();
        String baseCaseId = SlabRigHangingCatalog.semanticCaseId(
                SlabRigHangingCatalog.CaseKind.DIRECT, route, null, "none", topology);
        String expectedSemanticPageId = "painting-page-v1:sha256:" + sha256(SCHEMA + '\0'
                + universe.universeHash() + '\0' + baseCaseId + '\0' + page.selectorPage());
        if (route.index() != page.routeIndex()
                || page.addressablePage() != expectedAddressablePage
                || !expectedSemanticPageId.equals(page.semanticPageId())) {
            throw new IllegalArgumentException("page route/address/semantic identity disagreement");
        }
        String body = canonicalPageBody(universe, page.addressablePage(), page.routeOrdinal(), route,
                topology, page.selectorPage(), page.semanticPageId(), page.cases());
        if (!sha256(body).equals(page.planHash())
                || !insertIdentity(body, "plan_hash", page.planHash()).equals(page.canonicalTsv())) {
            throw new IllegalArgumentException("page canonical identity mismatch");
        }
    }

    private static CasePlan planCase(Universe universe, int routeOrdinal,
                                     SlabRigHangingCatalog.Route route,
                                     SlabRigCaseCatalog.Topology topology, Selector selector,
                                     int ordinal, BlockPos tileOrigin, Direction face) {
        Direction lateral = face.getCounterClockWise();
        List<FoundationPlan> foundations = new ArrayList<>(BACKING_SIDE);
        List<CellPlan> topologyCells = new ArrayList<>();
        List<CellPlan> backingCells = new ArrayList<>(BACKING_SIDE * BACKING_SIDE);
        for (int lateralIndex = -1; lateralIndex <= 2; lateralIndex++) {
            BlockPos columnBase = tileOrigin.relative(lateral, lateralIndex);
            List<CellPlan> columnTopology = topologyCells(topology, columnBase, lateralIndex);
            int terminalY = columnTopology.stream().map(CellPlan::relativePos)
                    .mapToInt(BlockPos::getY).max().orElseThrow();
            List<CellPlan> columnBacking = new ArrayList<>(BACKING_SIDE);
            for (int vertical = 0; vertical < BACKING_SIDE; vertical++) {
                BlockPos pos = new BlockPos(columnBase.getX(), terminalY + 1 + vertical,
                        columnBase.getZ());
                columnBacking.add(new CellPlan(pos,
                        "painting_backing_column=" + lateralIndex + ";row=" + vertical,
                        backingRecipe(route.supportFrame()), "PLAYER_ITEM_USEON"));
            }
            foundations.add(new FoundationPlan(lateralIndex, columnTopology, columnBacking));
            topologyCells.addAll(columnTopology);
            backingCells.addAll(columnBacking);
        }
        assertUniqueCells(topologyCells, "topology");
        assertUniqueCells(backingCells, "backing");

        int terminalY = foundations.get(1).topologyCells().stream().map(CellPlan::relativePos)
                .mapToInt(BlockPos::getY).max().orElseThrow();
        BlockPos clicked = new BlockPos(tileOrigin.getX(), terminalY + 2, tileOrigin.getZ());
        BlockPos anchor = clicked.relative(face);
        List<BlockPos> support = requiredSupportCells(selector, clicked, lateral, backingCells);
        List<BlockPos> entityAir = backingCells.stream()
                .map(cell -> cell.relativePos().relative(face).immutable())
                .sorted(POSITION_ORDER).toList();
        List<CellPlan> clearOwned = new ArrayList<>(topologyCells.size() + backingCells.size());
        clearOwned.addAll(topologyCells);
        clearOwned.addAll(backingCells);
        clearOwned.sort(Comparator.comparing(CellPlan::relativePos, POSITION_ORDER));
        assertUniqueCells(clearOwned, "clear-owned");
        LinkedHashSet<BlockPos> reserved = new LinkedHashSet<>();
        clearOwned.forEach(cell -> reserved.add(cell.relativePos()));
        reserved.addAll(entityAir);
        List<BlockPos> reservedSorted = reserved.stream().sorted(POSITION_ORDER).toList();

        long baseCaseIndex = Math.addExact(
                Math.multiplyExact((long) route.index(), (long) EXPECTED_TOPOLOGIES),
                topology.index());
        String baseCaseId = SlabRigHangingCatalog.semanticCaseId(
                SlabRigHangingCatalog.CaseKind.DIRECT, route, null, "none", topology);
        long executionIndex = Math.addExact(
                Math.multiplyExact(Math.addExact(
                        Math.multiplyExact((long) routeOrdinal, universe.topologies().size()),
                        topology.index()), universe.selectors().size()), selector.index());
        String attemptId = "painting-attempt-v1:sha256:" + sha256(SCHEMA + '\0'
                + baseCaseId + '\0' + selector.semanticId() + '\0'
                + universe.runtimeExecutionIdentity());
        return new CasePlan(ordinal, executionIndex, baseCaseIndex, baseCaseId, attemptId,
                route, topology, selector, tileOrigin, face, face.getOpposite(), lateral,
                clicked, anchor, foundations, topologyCells, backingCells, support, entityAir,
                clearOwned, reservedSorted, FOOTPRINT_EXPANSION);
    }

    private static List<CellPlan> topologyCells(SlabRigCaseCatalog.Topology topology,
                                                BlockPos base, int lateralIndex) {
        List<CellPlan> cells = new ArrayList<>();
        String prefix = "foundation_column=" + lateralIndex + ';';
        if (topology.index() == 0) {
            cells.add(new CellPlan(base, prefix + "control_ground", "minecraft:stone",
                    "DIRECT_FIXTURE_SET"));
            return List.copyOf(cells);
        }
        cells.add(new CellPlan(base, prefix + "bootstrap_ground", "minecraft:stone",
                "DIRECT_FIXTURE_SET"));
        cells.add(new CellPlan(base.above(), prefix + "bootstrap_lower_slab",
                "minecraft:stone_slab[type=bottom]", "PLAYER_ITEM_USEON"));
        if (topology.index() == 1) {
            return List.copyOf(cells);
        }
        cells.add(new CellPlan(base.above(2), prefix + "bootstrap_full", "minecraft:stone",
                "DIRECT_FIXTURE_SET"));
        cells.add(new CellPlan(base.above(3), prefix + "bootstrap_seat_slab",
                "minecraft:stone_slab[type=bottom]", "PLAYER_ITEM_USEON"));
        boolean smooth = true;
        String recipe = topology.recipe();
        for (int i = 0; i < recipe.length(); i++) {
            char token = recipe.charAt(i);
            if (token == 'S') {
                String slab = smooth ? "minecraft:smooth_stone_slab[type=bottom]"
                        : "minecraft:stone_slab[type=bottom]";
                smooth = !smooth;
                cells.add(new CellPlan(base.above(4 + i), prefix + "recipe=" + i + ":S",
                        slab, "PLAYER_ITEM_USEON"));
            } else if (token == 'B') {
                cells.add(new CellPlan(base.above(4 + i), prefix + "recipe=" + i + ":B",
                        "minecraft:stone", "PLAYER_ITEM_USEON"));
            } else {
                throw new IllegalArgumentException("unsupported topology token " + token
                        + " in " + topology.id());
            }
        }
        return List.copyOf(cells);
    }

    private static List<BlockPos> requiredSupportCells(Selector selector, BlockPos clicked,
                                                       Direction lateral,
                                                       List<CellPlan> backingCells) {
        if (selector.kind() == SelectorKind.UNPINNED) {
            return backingCells.stream().map(CellPlan::relativePos)
                    .sorted(POSITION_ORDER).toList();
        }
        List<BlockPos> support = new ArrayList<>();
        for (int y = selector.verticalMin(); y <= selector.verticalMax(); y++) {
            for (int x = selector.lateralMin(); x <= selector.lateralMax(); x++) {
                support.add(clicked.relative(lateral, x).above(y).immutable());
            }
        }
        Set<BlockPos> backing = backingCells.stream().map(CellPlan::relativePos)
                .collect(java.util.stream.Collectors.toSet());
        if (!backing.containsAll(support)) {
            throw new IllegalArgumentException("painting selector footprint exceeds fixed 4x4 backing: "
                    + selector.variantId());
        }
        support.sort(POSITION_ORDER);
        return List.copyOf(support);
    }

    private static List<Selector> selectors(SlabRigHangingArtifacts.RuntimeSnapshot runtime) {
        if (runtime.paintingVariantCount() != runtime.paintings().size()
                || runtime.paintings().size() != EXPECTED_REGISTRY_VARIANTS) {
            throw new IllegalArgumentException("RIG-3B2A expects the exact 51-row RIG-3B1 snapshot");
        }
        List<Selector> selectors = new ArrayList<>(EXPECTED_SELECTORS);
        String unpinnedIdentity = selectorId(runtime.executionIdentity(), SelectorKind.UNPINNED,
                "ABSENT", "ABSENT", UNPINNED_DOMAIN);
        selectors.add(new Selector(0, SelectorKind.UNPINNED, -1, "ABSENT", false,
                0, 0, -1, 2, -1, 2, "ABSENT", "ABSENT", UNPINNED_DOMAIN,
                unpinnedIdentity));

        String previous = null;
        for (int registryIndex = 0; registryIndex < runtime.paintings().size(); registryIndex++) {
            SlabRigHangingArtifacts.PaintingEntry entry = runtime.paintings().get(registryIndex);
            if (entry.index() != registryIndex || (previous != null && previous.compareTo(entry.id()) >= 0)) {
                throw new IllegalArgumentException("painting registry rows are not canonical at " + entry.id());
            }
            if (entry.widthBlocks() > BACKING_SIDE || entry.heightBlocks() > BACKING_SIDE) {
                throw new IllegalArgumentException("painting exceeds fixed 4x4 fixture: " + entry.id());
            }
            SelectorKind kind = entry.randomPlaceable()
                    ? SelectorKind.PINNED_TAGGED : SelectorKind.PINNED_UNTAGGED;
            String semanticId = selectorId(runtime.executionIdentity(), kind, entry.id(),
                    entry.componentType(), entry.componentValue());
            selectors.add(new Selector(registryIndex + 1, kind, registryIndex, entry.id(),
                    entry.randomPlaceable(), entry.widthBlocks(), entry.heightBlocks(),
                    entry.lateralMin(), entry.lateralMax(), entry.verticalMin(), entry.verticalMax(),
                    entry.componentType(), entry.componentValue(), "EXACT_REGISTRY_HOLDER", semanticId));
            previous = entry.id();
        }
        if (selectors.size() != EXPECTED_SELECTORS
                || selectors.stream().map(Selector::semanticId).distinct().count() != selectors.size()) {
            throw new IllegalStateException("painting selector axis is incomplete or duplicated");
        }
        return List.copyOf(selectors);
    }

    private static String selectorId(String runtimeIdentity, SelectorKind kind, String variant,
                                     String componentType, String componentValue) {
        return "painting-selector-v1:sha256:" + sha256(SCHEMA + '\0' + runtimeIdentity + '\0'
                + kind + '\0' + variant + '\0' + componentType + '\0' + componentValue);
    }

    private static void validateRoutes(List<SlabRigHangingCatalog.Route> routes) {
        if (routes.size() != EXPECTED_PAINTING_ROUTES) {
            throw new IllegalArgumentException("expected 16 exact painting routes, got " + routes.size());
        }
        Set<String> faceFrames = new HashSet<>();
        int previousIndex = -1;
        for (SlabRigHangingCatalog.Route route : routes) {
            if (route.index() <= previousIndex
                    || route.family() != SlabRigHangingCatalog.Family.ENTITY_HANGING
                    || route.actionKind() != SlabRigHangingCatalog.ActionKind.PLAYER_USEON_ENTITY_EFFECT
                    || route.effectKind() != SlabRigHangingCatalog.EffectKind.EXACT_ENTITY
                    || !"AUTO_USEON_PROXY".equals(route.actionOrigin())
                    || !"wall".equals(route.mount())
                    || !WALL_SUPPORT_FRAMES.contains(route.supportFrame())) {
                throw new IllegalArgumentException("unsupported or noncanonical painting route: " + route);
            }
            horizontalDirection(route.clickedFace());
            if (!faceFrames.add(route.clickedFace() + '\0' + route.supportFrame())) {
                throw new IllegalArgumentException("duplicate painting face/support route: " + route);
            }
            previousIndex = route.index();
        }
        if (faceFrames.size() != 16) {
            throw new IllegalArgumentException("painting routes do not cover four faces x four frames");
        }
        SlabRigHangingCatalog.Route witness = routes.stream()
                .filter(route -> route.index() == 6143).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("missing TEST 19 painting route 6143"));
        if (!"west".equals(witness.clickedFace()) || !"WALL_FULL".equals(witness.supportFrame())) {
            throw new IllegalArgumentException("route 6143 semantic drift: " + witness);
        }
    }

    private static void validateTopologies(List<SlabRigCaseCatalog.Topology> topologies) {
        if (topologies.size() != EXPECTED_TOPOLOGIES) {
            throw new IllegalArgumentException("expected 64 exact topologies, got " + topologies.size());
        }
        for (int i = 0; i < topologies.size(); i++) {
            if (topologies.get(i).index() != i) {
                throw new IllegalArgumentException("topology order/index drift at " + i);
            }
        }
        SlabRigCaseCatalog.Topology witness = topologies.get(42);
        if (!"stack:SBSBS".equals(witness.id()) || !"SBSBS".equals(witness.recipe())) {
            throw new IllegalArgumentException("topology 42 semantic drift: " + witness);
        }
    }

    private static void validateUniverse(Universe universe) {
        Objects.requireNonNull(universe, "universe");
        if (!SCHEMA.equals(universe.schema())
                || universe.paintingRoutes().size() != EXPECTED_PAINTING_ROUTES
                || universe.topologies().size() != EXPECTED_TOPOLOGIES
                || universe.selectors().size() != EXPECTED_SELECTORS
                || universe.totalAttempts() != EXPECTED_TOTAL_ATTEMPTS
                || universe.selectorPageCount() != EXPECTED_SELECTOR_PAGES
                || universe.pageCount() != EXPECTED_TOTAL_PAGES) {
            throw new IllegalArgumentException("invalid RIG-3B2A universe counters/schema");
        }
        String body = canonicalUniverseBody(universe);
        if (!sha256(body).equals(universe.universeHash())
                || !insertIdentity(body, "universe_hash", universe.universeHash())
                .equals(universe.canonicalTsv())) {
            throw new IllegalArgumentException("RIG-3B2A universe canonical identity mismatch");
        }
    }

    private static void validatePageCases(List<CasePlan> cases, int expectedSize) {
        if (cases.size() != expectedSize || cases.isEmpty() || cases.size() > PAGE_SIZE) {
            throw new IllegalArgumentException("invalid selector page case count: " + cases.size());
        }
        Set<String> ids = new HashSet<>();
        Set<BlockPos> pageReserved = new HashSet<>();
        for (int ordinal = 0; ordinal < cases.size(); ordinal++) {
            CasePlan entry = cases.get(ordinal);
            if (entry.ordinal() != ordinal || !FOOTPRINT_EXPANSION.equals(entry.footprintExpansion())
                    || entry.foundations().size() != BACKING_SIDE
                    || entry.backingCells().size() != BACKING_SIDE * BACKING_SIDE
                    || entry.entityAirCells().size() != BACKING_SIDE * BACKING_SIDE
                    || !entry.anchor().equals(entry.clicked().relative(entry.clickedFace()))
                    || !ids.add(entry.attemptId())) {
                throw new IllegalArgumentException("invalid or duplicate painting case plan: " + entry);
            }
            Set<BlockPos> clear = entry.clearOwnedCells().stream().map(CellPlan::relativePos)
                    .collect(java.util.stream.Collectors.toSet());
            if (clear.size() != entry.clearOwnedCells().size()
                    || !new HashSet<>(entry.reservedCells()).containsAll(clear)
                    || !new HashSet<>(entry.reservedCells()).containsAll(entry.entityAirCells())) {
                throw new IllegalArgumentException("case ownership/reservation mismatch: " + entry.attemptId());
            }
            for (BlockPos pos : entry.reservedCells()) {
                if (!pageReserved.add(pos)) {
                    throw new IllegalArgumentException("selector tiles overlap at " + pos);
                }
            }
        }
    }

    private static String backingRecipe(String supportFrame) {
        return switch (supportFrame) {
            case "WALL_FULL" -> "minecraft:stone";
            case "WALL_BOTTOM_SLAB" -> "minecraft:stone_slab[type=bottom]";
            case "WALL_TOP_SLAB" -> "minecraft:stone_slab[type=top]";
            case "WALL_DOUBLE_SLAB" -> "minecraft:stone_slab[type=double]";
            default -> throw new IllegalArgumentException("unsupported wall support frame " + supportFrame);
        };
    }

    private static Direction horizontalDirection(String name) {
        for (Direction direction : Direction.values()) {
            if (direction.getName().equals(name) && direction.getAxis().isHorizontal()) {
                return direction;
            }
        }
        throw new IllegalArgumentException("painting route has non-horizontal clicked face " + name);
    }

    private static void assertUniqueCells(List<CellPlan> cells, String label) {
        if (cells.stream().map(CellPlan::relativePos).distinct().count() != cells.size()) {
            throw new IllegalArgumentException("duplicate " + label + " cell");
        }
    }

    private static List<BlockPos> immutablePositions(List<BlockPos> positions) {
        return positions.stream().map(BlockPos::immutable).toList();
    }

    private static String canonicalUniverseBody(SlabRigHangingCatalog.Snapshot catalog,
                                                SlabRigHangingArtifacts.RuntimeSnapshot runtime,
                                                List<SlabRigHangingCatalog.Route> routes,
                                                List<SlabRigCaseCatalog.Topology> topologies,
                                                List<Selector> selectors, long totalAttempts,
                                                int selectorPages, int pages) {
        return canonicalUniverseBody(catalog.catalogHash(), catalog.topologyCatalogHash(),
                runtime.executionIdentity(), runtime.runtimeContentSha256(),
                runtime.paintingRegistryHash(), routes, topologies, selectors,
                totalAttempts, selectorPages, pages);
    }

    private static String canonicalUniverseBody(Universe universe) {
        return canonicalUniverseBody(universe.catalogHash(), universe.topologyCatalogHash(),
                universe.runtimeExecutionIdentity(), universe.runtimeContentSha256(),
                universe.paintingRegistryHash(), universe.paintingRoutes(), universe.topologies(),
                universe.selectors(), universe.totalAttempts(), universe.selectorPageCount(),
                universe.pageCount());
    }

    private static String canonicalUniverseBody(String catalogHash, String topologyHash,
                                                String runtimeIdentity, String runtimeContent,
                                                String paintingHash,
                                                List<SlabRigHangingCatalog.Route> routes,
                                                List<SlabRigCaseCatalog.Topology> topologies,
                                                List<Selector> selectors, long totalAttempts,
                                                int selectorPages, int pages) {
        StringBuilder out = new StringBuilder(16_384);
        out.append("schema\t").append(SCHEMA).append('\n');
        out.append("execution_contract\t").append(EXECUTION_CONTRACT).append('\n');
        out.append("catalog_hash\t").append(catalogHash).append('\n');
        out.append("topology_catalog_hash\t").append(topologyHash).append('\n');
        out.append("runtime_execution_identity\t").append(runtimeIdentity).append('\n');
        out.append("runtime_content_sha256\t").append(runtimeContent).append('\n');
        out.append("painting_registry_hash\t").append(paintingHash).append('\n');
        out.append("painting_routes\t").append(routes.size()).append('\n');
        out.append("painting_registry_variants\t").append(selectors.size() - 1).append('\n');
        out.append("painting_execution_selectors\t").append(selectors.size()).append('\n');
        out.append("topologies\t").append(topologies.size()).append('\n');
        out.append("total_attempts\t").append(totalAttempts).append('\n');
        out.append("selector_page_size\t").append(PAGE_SIZE).append('\n');
        out.append("selector_page_count\t").append(selectorPages).append('\n');
        out.append("addressable_page_count\t").append(pages).append('\n');
        out.append("board_side\t").append(BOARD_SIDE).append('\n');
        out.append("tile_pitch\t").append(TILE_PITCH).append('\n');
        out.append("backing_side\t").append(BACKING_SIDE).append('\n');
        out.append("footprint_expansion\t").append(FOOTPRINT_EXPANSION).append('\n');
        for (SlabRigHangingCatalog.Route route : routes) {
            out.append("route\t").append(route.index()).append('\t').append(route.id()).append('\t')
                    .append(route.clickedFace()).append('\t').append(route.supportFrame()).append('\t')
                    .append(route.actionOrigin()).append('\n');
        }
        for (SlabRigCaseCatalog.Topology topology : topologies) {
            out.append("topology\t").append(topology.index()).append('\t')
                    .append(topology.id()).append('\t').append(topology.recipe()).append('\t')
                    .append(topology.control()).append('\n');
        }
        for (Selector selector : selectors) {
            out.append("selector\t").append(selector.index()).append('\t').append(selector.kind())
                    .append('\t').append(selector.registryIndex()).append('\t')
                    .append(selector.variantId()).append('\t').append(selector.randomPlaceable())
                    .append('\t').append(selector.width()).append('\t').append(selector.height())
                    .append('\t').append(selector.lateralMin()).append('\t').append(selector.lateralMax())
                    .append('\t').append(selector.verticalMin()).append('\t').append(selector.verticalMax())
                    .append('\t').append(selector.componentType()).append('\t')
                    .append(selector.componentValue()).append('\t').append(selector.expectedDomain())
                    .append('\t').append(selector.semanticId()).append('\n');
        }
        return out.toString();
    }

    private static String canonicalPageBody(Universe universe, int addressablePage,
                                            int routeOrdinal,
                                            SlabRigHangingCatalog.Route route,
                                            SlabRigCaseCatalog.Topology topology,
                                            int selectorPage, String semanticPageId,
                                            List<CasePlan> cases) {
        StringBuilder out = new StringBuilder(24_000);
        out.append("schema\t").append(SCHEMA).append('\n');
        out.append("execution_contract\t").append(EXECUTION_CONTRACT).append('\n');
        out.append("universe_hash\t").append(universe.universeHash()).append('\n');
        out.append("addressable_page\t").append(addressablePage).append('\n');
        out.append("addressable_page_count\t").append(universe.pageCount()).append('\n');
        out.append("route_ordinal\t").append(routeOrdinal).append('\n');
        out.append("route_index\t").append(route.index()).append('\n');
        out.append("route_id\t").append(route.id()).append('\n');
        out.append("topology_index\t").append(topology.index()).append('\n');
        out.append("topology_id\t").append(topology.id()).append('\n');
        out.append("selector_page\t").append(selectorPage).append('\n');
        out.append("selector_page_count\t").append(universe.selectorPageCount()).append('\n');
        out.append("semantic_page_id\t").append(semanticPageId).append('\n');
        out.append("case_count\t").append(cases.size()).append('\n');
        out.append("footprint_expansion\t").append(FOOTPRINT_EXPANSION).append('\n');
        for (CasePlan entry : cases) {
            out.append("case\t").append(entry.ordinal()).append('\t')
                    .append(entry.executionIndex()).append('\t').append(entry.baseCaseIndex())
                    .append('\t').append(entry.baseCaseId()).append('\t').append(entry.attemptId())
                    .append('\t').append(entry.selector().index()).append('\t')
                    .append(entry.tileOrigin().toShortString()).append('\t')
                    .append(entry.clickedFace().getName()).append('\t')
                    .append(entry.lateralDirection().getName()).append('\t')
                    .append(entry.clicked().toShortString()).append('\t')
                    .append(entry.anchor().toShortString()).append('\t')
                    .append(entry.clearOwnedCells().size()).append('\t')
                    .append(entry.reservedCells().size()).append('\n');
            for (CellPlan cell : entry.clearOwnedCells()) {
                out.append("cell\t").append(entry.attemptId()).append('\t')
                        .append(cell.relativePos().toShortString()).append('\t')
                        .append(escape(cell.role())).append('\t').append(escape(cell.stateRecipe()))
                        .append('\t').append(cell.placementMethod()).append('\n');
            }
            for (BlockPos pos : entry.supportCells()) {
                out.append("support\t").append(entry.attemptId()).append('\t')
                        .append(pos.toShortString()).append('\n');
            }
            for (BlockPos pos : entry.entityAirCells()) {
                out.append("entity_air\t").append(entry.attemptId()).append('\t')
                        .append(pos.toShortString()).append('\n');
            }
        }
        return out.toString();
    }

    private static String insertIdentity(String body, String key, String identity) {
        int firstLineEnd = body.indexOf('\n') + 1;
        if (firstLineEnd <= 0) {
            throw new IllegalArgumentException("canonical body lacks schema line");
        }
        return body.substring(0, firstLineEnd) + key + '\t' + identity + '\n'
                + body.substring(firstLineEnd);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
