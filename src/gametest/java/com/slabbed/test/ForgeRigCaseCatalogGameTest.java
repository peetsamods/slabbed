package com.slabbed.test;

import com.mojang.brigadier.tree.CommandNode;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.rig.RigCase;
import com.slabbed.rig.RigManifest;
import com.slabbed.rig.SlabbedRigCaseCatalog;
import com.slabbed.rig.SlabbedRigCaseCatalog.CaseDefinition;
import com.slabbed.rig.SlabbedRigCaseCatalog.CasePage;
import com.slabbed.rig.SlabbedRigCaseCatalog.CatalogItem;
import com.slabbed.rig.SlabbedRigCaseCatalog.EffectPolicy;
import com.slabbed.rig.SlabbedRigCaseCatalog.ExcludedItem;
import com.slabbed.rig.SlabbedRigCaseCatalog.Snapshot;
import com.slabbed.rig.SlabbedRigCaseCatalog.Topology;
import com.slabbed.rig.SlabbedRigCaseEvidence;
import com.slabbed.rig.SlabbedRigCaseEvidence.AttemptStatus;
import com.slabbed.rig.SlabbedRigCaseEvidence.CaseOutcome;
import com.slabbed.rig.SlabbedRigCaseEvidence.CaseResult;
import com.slabbed.rig.SlabbedRigCaseEvidence.CellEvidence;
import com.slabbed.rig.SlabbedRigCaseEvidence.OwnedCellEvidence;
import com.slabbed.rig.SlabbedRigCaseEvidence.PreparedPage;
import com.slabbed.rig.SlabbedRigCaseEvidence.ReleasedBoard;
import com.slabbed.rig.SlabbedRigCaseEvidence.ResumeStatus;
import com.slabbed.rig.SlabbedRigCaseEvidence.SealedPage;
import com.slabbed.rig.SlabbedRigCaseEvidence.Store;
import com.slabbed.rig.SlabbedRigCaseEvidence.StructureStatus;
import com.slabbed.rig.SlabbedRigService;
import com.slabbed.rig.SlabbedRigService.CasesPagePlan;
import com.slabbed.rig.SlabbedRigService.CasesTilePlan;
import com.slabbed.util.SlabbedDiagnosticsBridge;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Forge-native proof for the Phase 6D cases catalog, durable evidence, and executor lifecycle. */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ForgeRigCaseCatalogGameTest {

    @GameTest(template = "empty")
    public void casesCatalogPartitionsRegistryAndPagesEveryStableIdentity(GameTestHelper ctx) {
        Snapshot snapshot = SlabbedRigCaseCatalog.snapshot();
        Snapshot repeated = SlabbedRigCaseCatalog.snapshot();
        ctx.assertTrue(snapshot.equals(repeated),
                "two live-registry snapshots must be byte-for-byte deterministic");
        ctx.assertTrue(snapshot.schema().equals(SlabbedRigCaseCatalog.SCHEMA)
                        && snapshot.catalogHash().matches("[0-9a-f]{64}"),
                "catalog schema and lowercase raw SHA-256 must remain explicit");

        List<String> liveIds = BuiltInRegistries.ITEM.stream()
                .map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
                .sorted()
                .toList();
        Set<String> liveBlockItemIds = BuiltInRegistries.ITEM.stream()
                .filter(BlockItem.class::isInstance)
                .map(item -> BuiltInRegistries.ITEM.getKey(item).toString())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> includedIds = new HashSet<>();
        Set<String> excludedIds = new HashSet<>();
        for (int index = 0; index < snapshot.items().size(); index++) {
            CatalogItem item = snapshot.items().get(index);
            ctx.assertTrue(item.index() == index
                            && includedIds.add(item.id())
                            && (index == 0
                                    || snapshot.items().get(index - 1).id().compareTo(item.id()) < 0),
                    "included BlockItems must have dense indexes and sorted unique IDs");
            assertSemanticCategories(ctx, item);
        }
        for (int index = 0; index < snapshot.excludedItems().size(); index++) {
            ExcludedItem item = snapshot.excludedItems().get(index);
            ctx.assertTrue(item.index() == index
                            && excludedIds.add(item.id())
                            && item.reason().equals("not_block_item")
                            && (index == 0
                                    || snapshot.excludedItems().get(index - 1).id()
                                            .compareTo(item.id()) < 0),
                    "excluded non-BlockItems must have dense indexes and sorted unique IDs");
        }
        ctx.assertTrue(includedIds.size() + excludedIds.size() == liveIds.size()
                        && includedIds.stream().noneMatch(excludedIds::contains),
                "the two catalog partitions must cover the live registry exactly once");
        for (String id : liveIds) {
            boolean blockItem = liveBlockItemIds.contains(id);
            ctx.assertTrue(includedIds.contains(id) == blockItem
                            && excludedIds.contains(id) != blockItem,
                    "catalog inclusion must be equivalent to BlockItem for " + id);
        }

        assertRepresentativeSemantics(ctx, snapshot);
        assertTopologies(ctx, snapshot);
        assertCasesAndPages(ctx, snapshot);
        assertCasesPlanner(ctx, snapshot);
        assertCasesEvidence(ctx, snapshot);
        assertCasesExecutor(ctx, snapshot);

        String canonical = canonicalCatalog(snapshot);
        String expectedHash = sha256(canonical);
        ctx.assertTrue(snapshot.catalogHash().equals(expectedHash)
                        && SlabbedRigCaseCatalog.catalogTsv(snapshot).equals(
                                "# catalog_hash\t" + expectedHash + '\n' + canonical),
                "catalog hash and TSV must bind the exact deterministic canonical rows");

        CommandNode<CommandSourceStack> slabrig = ctx.getLevel().getServer().getCommands()
                .getDispatcher().getRoot().getChild("slabrig");
        CommandNode<CommandSourceStack> cases = slabrig == null ? null : slabrig.getChild("cases");
        ctx.assertTrue(cases != null
                        && cases.getCommand() != null
                        && cases.getChild("force") != null
                        && cases.getChild("status") != null
                        && cases.getChild("resume") != null
                        && cases.getChild("clear") != null
                        && cases.getChild("clear").getChild("force") == null,
                "cases must expose run/guarded-force/status/resume/exact-clear and no clear force");

        System.out.println("[FORGE_RIG_CASE_CATALOG] items=" + snapshot.items().size()
                + " excluded=" + snapshot.excludedItems().size()
                + " topologies=" + snapshot.topologies().size()
                + " cases=" + snapshot.totalCases()
                + " pages=" + snapshot.pageCount()
                + " hash=" + snapshot.catalogHash()
                + " command=run,force,status,resume,clear");
        ctx.succeed();
    }

    private static void assertSemanticCategories(GameTestHelper ctx, CatalogItem item) {
        List<String> categories = item.categories();
        ctx.assertTrue(!categories.isEmpty()
                        && categories.equals(categories.stream().distinct().sorted().toList()),
                "categories must be nonempty, unique, and sorted for " + item.id());
        ctx.assertTrue(categories.contains("universe:block_item")
                        && categories.stream().filter(tag -> tag.startsWith("namespace:")).count() == 1
                        && categories.stream().filter(tag -> tag.startsWith("route:")).count() == 1
                        && categories.stream().filter(tag -> tag.startsWith("kind:item:")).count() == 1
                        && categories.stream().filter(tag -> tag.startsWith("shape:")).count() == 1
                        && categories.stream().anyMatch(tag -> tag.startsWith("family:")),
                "every BlockItem needs universe, namespace, route, kind, shape, and family tags: "
                        + item.id());
        ctx.assertTrue(categories.stream().noneMatch(tag -> tag.contains("misc")
                                || tag.contains("unclassified")
                                || tag.startsWith("class:")),
                "catalog tags must remain semantic and mapping-stable for " + item.id());
        ctx.assertTrue((item.effectPolicy() == EffectPolicy.LOCAL_TARGET_AND_NEIGHBORS)
                        == (item.disposition()
                                == SlabbedRigCaseCatalog.Disposition.AUTO_FLOOR_UP),
                "effect policy and disposition must agree for " + item.id());
    }

    private static void assertRepresentativeSemantics(GameTestHelper ctx, Snapshot snapshot) {
        assertTags(ctx, included(snapshot, "minecraft:stone"),
                "family:ordinary_full_cube", "shape:full_cube");
        assertTags(ctx, included(snapshot, "minecraft:oak_slab"), "family:slab");
        assertTags(ctx, included(snapshot, "minecraft:oak_door"),
                "family:door", "route:double_high_block_item");
        assertTags(ctx, included(snapshot, "minecraft:lantern"),
                "family:lantern", "family:hanging_capable");
        assertTags(ctx, included(snapshot, "minecraft:chain"),
                "family:chain", "family:hanging_capable");
        assertTags(ctx, included(snapshot, "minecraft:powder_snow_bucket"),
                "route:solid_bucket_block_item");
        assertTags(ctx, included(snapshot, "minecraft:command_block"),
                "route:game_master_block_item");
        CatalogItem lilyPad = included(snapshot, "minecraft:lily_pad");
        ctx.assertTrue(lilyPad.categories().contains("route:place_on_water_block_item")
                        && lilyPad.effectPolicy() == EffectPolicy.DEFERRED_WATER_SURFACE,
                "water-surface placement must stay cataloged but deferred");
        CatalogItem scaffolding = included(snapshot, "minecraft:scaffolding");
        ctx.assertTrue(scaffolding.categories().contains("route:scaffolding_block_item")
                        && scaffolding.effectPolicy() == EffectPolicy.DEFERRED_UNBOUNDED_STACK,
                "unbounded scaffolding placement must stay cataloged but deferred");

        ExcludedItem painting = excluded(snapshot, "minecraft:painting");
        ctx.assertTrue(painting.itemKind().equals("hanging_entity_item")
                        && painting.route().equals("dedicated_hanging_entity"),
                "painting must route to specialized hanging evidence");
        ExcludedItem armorStand = excluded(snapshot, "minecraft:armor_stand");
        ctx.assertTrue(armorStand.itemKind().equals("armor_stand_item")
                        && armorStand.route().equals("dedicated_entity_placement"),
                "armor stands must route to entity placement evidence");
        ExcludedItem waterBucket = excluded(snapshot, "minecraft:water_bucket");
        ctx.assertTrue(waterBucket.itemKind().equals("fluid_container_item")
                        && waterBucket.route().equals("dedicated_fluid_container"),
                "ordinary fluid buckets must not masquerade as BlockItem cases");
    }

    private static void assertTopologies(GameTestHelper ctx, Snapshot snapshot) {
        List<Topology> topologies = snapshot.topologies();
        ctx.assertTrue(topologies.size() == SlabbedRigCaseCatalog.TOPOLOGY_COUNT,
                "cases grammar must contain exactly 64 topologies");
        ctx.assertTrue(topologies.get(0).equals(
                                new Topology(0, "control:ground_full_block", "GROUND", true))
                        && topologies.get(1).equals(
                                new Topology(1, "control:single_slab", "SINGLE_SLAB", true)),
                "the two controls must lead the topology grammar exactly");
        List<String> expectedRecipes = new ArrayList<>(62);
        for (int length = 1; length <= 5; length++) {
            appendRecipes(expectedRecipes, new StringBuilder(length), length);
        }
        ctx.assertTrue(expectedRecipes.size() == 62
                        && topologies.subList(2, topologies.size()).stream()
                                .map(Topology::recipe).toList().equals(expectedRecipes),
                "all nonempty S/B words through length five must be length-major and S-first");
        for (int index = 0; index < topologies.size(); index++) {
            Topology topology = topologies.get(index);
            ctx.assertTrue(topology.index() == index
                            && (topology.control() || topology.id().equals(
                                    "stack:" + topology.recipe())),
                    "topology indexes and stack IDs must remain dense and exact");
        }
    }

    private static void assertCasesAndPages(GameTestHelper ctx, Snapshot snapshot) {
        long expectedCases = (long) snapshot.items().size()
                * SlabbedRigCaseCatalog.TOPOLOGY_COUNT;
        int expectedPages = ((snapshot.items().size()
                + SlabbedRigCaseCatalog.PAGE_GRID_SIDE - 1)
                / SlabbedRigCaseCatalog.PAGE_GRID_SIDE)
                * SlabbedRigCaseCatalog.TOPOLOGY_GROUPS;
        ctx.assertTrue(snapshot.totalCases() == expectedCases
                        && snapshot.pageCount() == expectedPages,
                "case/page totals must derive only from items, topologies, and 4x4 geometry");

        Set<Long> indexes = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (int pageNumber = 1; pageNumber <= snapshot.pageCount(); pageNumber++) {
            CasePage page = SlabbedRigCaseCatalog.page(snapshot, pageNumber);
            int expectedItemGroup = (pageNumber - 1)
                    / SlabbedRigCaseCatalog.TOPOLOGY_GROUPS;
            int expectedTopologyGroup = (pageNumber - 1)
                    % SlabbedRigCaseCatalog.TOPOLOGY_GROUPS;
            ctx.assertTrue(page.itemGroup() == expectedItemGroup
                            && page.topologyGroup() == expectedTopologyGroup
                            && page.cases().size() >= SlabbedRigCaseCatalog.PAGE_GRID_SIDE
                            && page.cases().size() <= SlabbedRigCaseCatalog.PAGE_SIZE,
                    "page groups and bounded 4x4 cardinality must be exact");

            int itemStart = expectedItemGroup * SlabbedRigCaseCatalog.PAGE_GRID_SIDE;
            int topologyStart = expectedTopologyGroup * SlabbedRigCaseCatalog.PAGE_GRID_SIDE;
            int offset = 0;
            for (int itemIndex = itemStart;
                    itemIndex < Math.min(
                            itemStart + SlabbedRigCaseCatalog.PAGE_GRID_SIDE,
                            snapshot.items().size());
                    itemIndex++) {
                for (int topologyIndex = topologyStart;
                        topologyIndex < topologyStart + SlabbedRigCaseCatalog.PAGE_GRID_SIDE;
                        topologyIndex++) {
                    CaseDefinition definition = page.cases().get(offset++);
                    long expectedIndex = (long) itemIndex
                            * SlabbedRigCaseCatalog.TOPOLOGY_COUNT + topologyIndex;
                    ctx.assertTrue(definition.index() == expectedIndex
                                    && definition.item().index() == itemIndex
                                    && definition.topology().index() == topologyIndex
                                    && definition.placementMode().equals(
                                            SlabbedRigCaseCatalog.PLACEMENT_MODE)
                                    && definition.equals(
                                            SlabbedRigCaseCatalog.caseAt(snapshot, expectedIndex))
                                    && indexes.add(expectedIndex)
                                    && ids.add(definition.id()),
                            "page flattening must be item-group, topology-group, item, topology");
                }
            }
            ctx.assertTrue(page.firstCaseIndex() == page.cases().get(0).index()
                            && page.lastCaseIndex()
                                    == page.cases().get(page.cases().size() - 1).index(),
                    "page bounds must name the actual first and last semantic case");
        }
        ctx.assertTrue(indexes.size() == snapshot.totalCases()
                        && ids.size() == snapshot.totalCases()
                        && indexes.contains(0L)
                        && indexes.contains(snapshot.totalCases() - 1),
                "the page union must cover every case exactly once with unique stable IDs");

        CaseDefinition first = SlabbedRigCaseCatalog.caseAt(snapshot, 0);
        CaseDefinition topologyNeighbor = SlabbedRigCaseCatalog.caseAt(snapshot, 1);
        ctx.assertTrue(!first.id().equals(topologyNeighbor.id())
                        && first.id().equals(SlabbedRigCaseCatalog.caseId(
                                first.item().id(), first.topology().id(), first.placementMode()))
                        && first.id().startsWith("case-v1:sha256:"),
                "case IDs must bind schema/item/topology/mode and exclude page/ordinal");
        assertIllegalArgument(ctx, () -> SlabbedRigCaseCatalog.page(snapshot, 0),
                "page zero must fail closed");
        assertIllegalArgument(
                ctx,
                () -> SlabbedRigCaseCatalog.page(snapshot, snapshot.pageCount() + 1),
                "a page past the complete catalog must fail closed");
        assertIllegalArgument(ctx, () -> SlabbedRigCaseCatalog.caseAt(snapshot, -1),
                "negative case indexes must fail closed");
        assertIllegalArgument(
                ctx,
                () -> SlabbedRigCaseCatalog.caseAt(snapshot, snapshot.totalCases()),
                "a case past the complete catalog must fail closed");
    }

    private static void assertCasesPlanner(GameTestHelper ctx, Snapshot snapshot) {
        BlockPos anchor = new BlockPos(40, 8, 40);
        CasesPagePlan plan = SlabbedRigService.casesPagePlan(
                snapshot, anchor, Direction.NORTH, 1);
        CasesPagePlan repeated = SlabbedRigService.casesPagePlan(
                snapshot, anchor, Direction.NORTH, 1);
        ctx.assertTrue(plan.equals(repeated)
                        && plan.page().page() == 1
                        && plan.tileSpacing() == 8
                        && plan.layoutVersion().equals(SlabbedRigService.CASES_LAYOUT_VERSION)
                        && plan.tiles().size() == SlabbedRigCaseCatalog.PAGE_SIZE,
                "cases page planning must be pure, deterministic, and exactly 4x4 at spacing 8");

        LinkedHashSet<BlockPos> footprint = new LinkedHashSet<>();
        for (CasesTilePlan tile : plan.tiles()) {
            BlockPos expectedBase = anchor
                    .relative(Direction.NORTH, tile.row() * plan.tileSpacing())
                    .relative(Direction.EAST, tile.column() * plan.tileSpacing());
            ctx.assertTrue(tile.base().equals(expectedBase)
                            && tile.cursor().equals(
                                    tile.fixtures().get(tile.fixtures().size() - 1).pos())
                            && tile.target().equals(tile.cursor().above())
                            && tile.effectCells().equals(actionEnvelope(
                                    tile.cursor(), tile.target()))
                            && tile.guardCells().equals(
                                    List.of(tile.target().above(2), tile.target().above(3))),
                    "each cases tile must preserve row/column, cursor, target, effect, and guards");
            assertFixtureWord(ctx, tile);
            LinkedHashSet<BlockPos> expectedMutation = new LinkedHashSet<>();
            tile.fixtures().forEach(fixture -> expectedMutation.add(fixture.pos()));
            expectedMutation.addAll(tile.effectCells());
            ctx.assertTrue(tile.mutationCells().equals(List.copyOf(expectedMutation))
                            && tile.guardCells().stream().noneMatch(expectedMutation::contains),
                    "fixture/effect mutation cells must be exact and guard-disjoint");
            for (BlockPos reserved : tile.reservedCells()) {
                ctx.assertTrue(footprint.add(reserved),
                        "the eight-block cases grid must keep tile reservations disjoint");
            }
        }
        ctx.assertTrue(plan.reservedFootprint().equals(List.copyOf(footprint))
                        && plan.autoTiles().equals(plan.tiles().stream()
                                .filter(CasesTilePlan::autoEligible).toList())
                        && plan.deferredTiles().equals(plan.tiles().stream()
                                .filter(tile -> !tile.autoEligible()).toList()),
                "page footprint and AUTO/deferred partitions must be exact ordered unions");

        CasesPagePlan east = SlabbedRigService.casesPagePlan(
                snapshot, anchor, Direction.EAST, 1);
        for (CasesTilePlan tile : east.tiles()) {
            BlockPos expectedBase = anchor
                    .relative(Direction.EAST, tile.row() * east.tileSpacing())
                    .relative(Direction.SOUTH, tile.column() * east.tileSpacing());
            ctx.assertTrue(tile.base().equals(expectedBase),
                    "horizontal facing must rotate the same semantic page without reordering it");
        }

        CatalogItem lilyPad = included(snapshot, "minecraft:lily_pad");
        int lilyPage = (lilyPad.index() / SlabbedRigCaseCatalog.PAGE_GRID_SIDE)
                * SlabbedRigCaseCatalog.TOPOLOGY_GROUPS + 1;
        CasesPagePlan deferredPlan = SlabbedRigService.casesPagePlan(
                snapshot, anchor, Direction.NORTH, lilyPage);
        long lilyTiles = deferredPlan.deferredTiles().stream()
                .filter(tile -> tile.definition().item().id().equals("minecraft:lily_pad"))
                .count();
        ctx.assertTrue(lilyTiles == SlabbedRigCaseCatalog.PAGE_GRID_SIDE,
                "deferred items must remain present and named in every topology column");
    }

    private static void assertFixtureWord(GameTestHelper ctx, CasesTilePlan tile) {
        String recipe = tile.definition().topology().recipe();
        List<RigCase.FixtureCell> fixtures = tile.fixtures();
        if (recipe.equals("GROUND")) {
            ctx.assertTrue(fixtures.size() == 1
                            && fixtures.get(0).state().is(Blocks.STONE),
                    "GROUND control must be one full stone fixture");
            return;
        }
        if (recipe.equals("SINGLE_SLAB")) {
            ctx.assertTrue(fixtures.size() == 1
                            && fixtures.get(0).state().is(Blocks.STONE_SLAB)
                            && fixtures.get(0).state().getValue(SlabBlock.TYPE) == SlabType.BOTTOM,
                    "SINGLE_SLAB control must be one bottom stone slab fixture");
            return;
        }
        ctx.assertTrue(fixtures.size() == recipe.length(),
                "stack fixtures must preserve exact S/B word length");
        for (int index = 0; index < recipe.length(); index++) {
            BlockState state = fixtures.get(index).state();
            boolean matches = recipe.charAt(index) == 'S'
                    ? state.is(Blocks.STONE_SLAB)
                            && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                    : state.is(Blocks.STONE);
            ctx.assertTrue(matches && fixtures.get(index).pos().equals(tile.base().above(index)),
                    "stack fixture cells must preserve exact S/B token order");
        }
    }

    private static List<BlockPos> actionEnvelope(BlockPos cursor, BlockPos target) {
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        positions.add(cursor.immutable());
        positions.add(target.immutable());
        positions.add(target.above().immutable());
        positions.add(target.below().immutable());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            positions.add(target.relative(direction).immutable());
        }
        return List.copyOf(positions);
    }

    private static void assertCasesEvidence(GameTestHelper ctx, Snapshot liveSnapshot) {
        try {
            Snapshot snapshot = evidenceSnapshot(liveSnapshot);
            CasesPagePlan plan = SlabbedRigService.casesPagePlan(
                    snapshot, new BlockPos(80, 8, 80), Direction.NORTH, 1);
            Path worldRoot = ctx.getLevel().getServer().getWorldPath(LevelResource.ROOT);
            Path root = worldRoot.resolve("data").resolve("slabbed")
                    .resolve("rig-cases-contract").resolve(UUID.randomUUID().toString());
            List<String> runtimeClasses = SlabbedRigCaseEvidence.runtimeContentResources();
            ctx.assertTrue(runtimeClasses.contains(
                                    "com/slabbed/rig/SlabbedRigCaseEvidence$Store.class")
                            && runtimeClasses.contains(
                                    "com/slabbed/rig/SlabbedRigService$CasesPagePlan.class")
                            && runtimeClasses.contains(
                                    "com/slabbed/rig/SlabbedRigCaseCatalog$Snapshot.class")
                            && runtimeClasses.contains(
                                    "com/slabbed/rig/RigManifest$OwnedCell.class"),
                    "runtime identity must enumerate the nested bytecode that owns evidence law");
            String buildHash = SlabbedRigCaseEvidence.runtimeContentHash();
            ctx.assertTrue(buildHash.matches("[0-9a-f]{64}")
                            && buildHash.equals(SlabbedRigCaseEvidence.runtimeContentHash()),
                    "runtime identity must hash the exact generated class roster deterministically");
            Store store = SlabbedRigCaseEvidence.open(
                    root, "minecraft:overworld", buildHash, snapshot);
            ctx.assertTrue(store.inspect().status() == ResumeStatus.FRESH,
                    "a new world/dimension identity must begin at a fresh contiguous cursor");
            UUID worldId = store.identity().worldId();

            PreparedPage prepared = store.prepare(plan);
            Path plannedBlob = findBlob(root, prepared.plannedHash());
            Path cursorPath = findNamedFile(root, "cursor.tsv");
            ctx.assertTrue(Files.isRegularFile(plannedBlob)
                            && Files.readString(cursorPath, StandardCharsets.UTF_8)
                                    .contains(prepared.plannedHash()),
                    "prepare may return only after the plan blob and referencing cursor exist");
            ctx.assertTrue(store.inspect().status() == ResumeStatus.PREPARED_NO_MUTATION
                            && store.inspect().cursor().active().nextCaseOrdinal() == 0,
                    "prepare must publish an abortable no-mutation state before execution begins");
            assertContentAddressedBlobs(ctx, root);
            prepared = store.beginExecution(prepared);

            List<OwnedCellEvidence> expectedOwnership = new ArrayList<>();
            Store activeStore = store;
            for (int ordinal = 0; ordinal < plan.tiles().size(); ordinal++) {
                CaseResult result = resultForTile(plan.tiles().get(ordinal), ordinal);
                activeStore.checkpoint(prepared, result);
                expectedOwnership.addAll(result.ownedDelta());
                if (ordinal == 2) {
                    Store interrupted = SlabbedRigCaseEvidence.open(
                            root, "minecraft:overworld", buildHash, snapshot);
                    ctx.assertTrue(interrupted.identity().worldId().equals(worldId)
                                    && interrupted.inspect().status()
                                            == ResumeStatus.INTERRUPTED_UNKNOWN_OWNERSHIP
                                    && interrupted.inspect().completedOwnership()
                                            .equals(List.copyOf(expectedOwnership)),
                            "reopen must preserve world identity and only completed-case ownership");
                    assertIllegalState(ctx, interrupted::resolveResumePage,
                            "an interrupted current page must refuse automatic resume");
                    activeStore = interrupted;
                }
            }

            Store completed = SlabbedRigCaseEvidence.open(
                    root, "minecraft:overworld", buildHash, snapshot);
            ctx.assertTrue(completed.inspect().status() == ResumeStatus.COMPLETED_PENDING_FINAL,
                    "every checkpoint without a final must reopen as deterministic completion");
            completed.repairCompletedPage();
            Store pending = SlabbedRigCaseEvidence.open(
                    root, "minecraft:overworld", buildHash, snapshot);
            SlabbedRigCaseEvidence.ResumeView ready = pending.inspect();
            String firstFinalHash = ready.cursor().boardFinalHash();
            ctx.assertTrue(ready.status() == ResumeStatus.READY
                            && ready.cursor().nextPage() == 2
                            && !firstFinalHash.equals(SlabbedRigCaseEvidence.GENESIS)
                            && ready.presentBoardOwnership()
                                    .equals(List.copyOf(expectedOwnership))
                            && pending.rehydrateCompletedOwnership()
                                    .equals(List.copyOf(expectedOwnership))
                            && pending.rehydratePresentBoardOwnership()
                                    .equals(List.copyOf(expectedOwnership)),
                    "final repair must advance once and retain exact present-board ownership");

            String beforeRejectedPage = ready.cursor().cursorHash();
            CasesPagePlan outOfOrder = SlabbedRigService.casesPagePlan(
                    snapshot, new BlockPos(80, 8, 80), Direction.NORTH, 3);
            assertIllegalState(ctx, () -> pending.prepare(outOfOrder),
                    "a noncontiguous cases page must be refused before mutation");
            ctx.assertTrue(pending.inspect().cursor().cursorHash().equals(beforeRejectedPage),
                    "a refused page must not move the durable cursor");

            CasesPagePlan secondPlan = SlabbedRigService.casesPagePlan(
                    snapshot, new BlockPos(80, 8, 80), Direction.NORTH, 2);
            PreparedPage secondPrepared = pending.prepare(secondPlan);
            secondPrepared = pending.beginExecution(secondPrepared);
            List<OwnedCellEvidence> expectedSecondOwnership = new ArrayList<>();
            for (int ordinal = 0; ordinal < secondPlan.tiles().size(); ordinal++) {
                CaseResult result = resultForTile(secondPlan.tiles().get(ordinal), ordinal);
                pending.checkpoint(secondPrepared, result);
                expectedSecondOwnership.addAll(result.ownedDelta());
            }
            SealedPage secondSealed = pending.seal(secondPrepared);
            assertIllegalState(ctx, () -> pending.advance(secondSealed),
                    "a replacement page must not advance while the prior board remains present");
            SlabbedRigCaseEvidence.ResumeView beforeRelease = pending.inspect();
            ctx.assertTrue(beforeRelease.status() == ResumeStatus.FINAL_PENDING_CURSOR
                            && beforeRelease.cursor().boardFinalHash().equals(firstFinalHash)
                            && beforeRelease.presentBoardOwnership()
                                    .equals(List.copyOf(expectedOwnership)),
                    "a refused advance must retain the complete prior board claim");

            ReleasedBoard firstRelease = pending.releasePresentBoard();
            ctx.assertTrue(firstRelease.release().boardFinalHash().equals(firstFinalHash)
                            && firstRelease.release().cursorBefore().equals(beforeRelease.cursor())
                            && firstRelease.release().activePlannedHash()
                                    .equals(secondPrepared.plannedHash())
                            && firstRelease.release().releasedCells()
                                    .equals(List.copyOf(expectedOwnership)),
                    "page replacement must tombstone the exact prior board and bind its plan");
            SlabbedRigCaseEvidence.ResumeView releasedPending = pending.inspect();
            ctx.assertTrue(releasedPending.status() == ResumeStatus.FINAL_PENDING_CURSOR
                            && releasedPending.cursor().boardFinalHash()
                                    .equals(SlabbedRigCaseEvidence.GENESIS)
                            && releasedPending.cursor().lastReleaseHash()
                                    .equals(firstRelease.releaseHash())
                            && releasedPending.presentBoardOwnership().isEmpty(),
                    "durable release must remove only the prior board claim and preserve progress");

            Store releasedReopen = SlabbedRigCaseEvidence.open(
                    root, "minecraft:overworld", buildHash, snapshot);
            ctx.assertTrue(releasedReopen.inspect().status() == ResumeStatus.FINAL_PENDING_CURSOR
                            && releasedReopen.inspect().presentBoardOwnership().isEmpty(),
                    "release and pending replacement must reopen without guessed ownership");
            releasedReopen.repairFinalCursor();
            SlabbedRigCaseEvidence.ResumeView pageThreeReady = releasedReopen.inspect();
            ctx.assertTrue(pageThreeReady.status() == ResumeStatus.READY
                            && pageThreeReady.cursor().nextPage() == 3
                            && pageThreeReady.cursor().boardFinalHash()
                                    .equals(secondSealed.finalHash())
                            && pageThreeReady.cursor().lastReleaseHash()
                                    .equals(firstRelease.releaseHash())
                            && pageThreeReady.presentBoardOwnership()
                                    .equals(List.copyOf(expectedSecondOwnership))
                            && releasedReopen.rehydrateCompletedOwnership()
                                    .equals(List.copyOf(expectedSecondOwnership)),
                    "released replacement must become the one exact present board after advance");

            ReleasedBoard secondRelease = releasedReopen.releasePresentBoard();
            ctx.assertTrue(secondRelease.release().boardFinalHash()
                                    .equals(secondSealed.finalHash())
                            && secondRelease.release().cursorBefore()
                                    .equals(pageThreeReady.cursor())
                            && secondRelease.release().predecessorReleaseHash()
                                    .equals(firstRelease.releaseHash())
                            && secondRelease.release().activePlannedHash().equals("-")
                            && secondRelease.release().releasedCells()
                                    .equals(List.copyOf(expectedSecondOwnership)),
                    "ordinary clear must extend the exact release chain without an active plan");
            String fullyReleasedCursor = releasedReopen.inspect().cursor().cursorHash();
            assertIllegalState(ctx, releasedReopen::releasePresentBoard,
                    "a board may be released exactly once");
            ctx.assertTrue(releasedReopen.inspect().cursor().cursorHash()
                                    .equals(fullyReleasedCursor)
                            && releasedReopen.inspect().presentBoardOwnership().isEmpty()
                            && releasedReopen.rehydrateCompletedOwnership().isEmpty(),
                    "duplicate release refusal must preserve the empty present-board claim");

            Store releaseChainReopen = SlabbedRigCaseEvidence.open(
                    root, "minecraft:overworld", buildHash, snapshot);
            ctx.assertTrue(releaseChainReopen.inspect().status() == ResumeStatus.READY
                            && releaseChainReopen.inspect().cursor().nextPage() == 3
                            && releaseChainReopen.inspect().cursor().lastReleaseHash()
                                    .equals(secondRelease.releaseHash())
                            && releaseChainReopen.inspect().presentBoardOwnership().isEmpty(),
                    "the complete content-addressed release chain must survive reopen");

            byte[] completeCursor = Files.readAllBytes(cursorPath);
            Files.write(
                    cursorPath,
                    replaceCursorReleaseHead(
                            completeCursor,
                            secondRelease.releaseHash(),
                            firstRelease.releaseHash()),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Store omittedRelease = SlabbedRigCaseEvidence.open(
                    root, "minecraft:overworld", buildHash, snapshot);
            ctx.assertTrue(omittedRelease.inspect().status() == ResumeStatus.CORRUPT,
                    "a validly re-signed cursor may not omit the newest board release");
            Files.write(
                    cursorPath,
                    completeCursor,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Store restoredReleaseChain = SlabbedRigCaseEvidence.open(
                    root, "minecraft:overworld", buildHash, snapshot);
            ctx.assertTrue(restoredReleaseChain.inspect().status() == ResumeStatus.READY,
                    "restoring the exact cursor bytes must restore the complete release chain");

            Store mismatch = SlabbedRigCaseEvidence.open(
                    root, "minecraft:overworld", sha256("different-build"), snapshot);
            ctx.assertTrue(mismatch.inspect().status() == ResumeStatus.IDENTITY_MISMATCH,
                    "world evidence must fail closed under a different runnable build identity");
            assertContentAddressedBlobs(ctx, root);
            Files.writeString(
                    findBlob(root, secondRelease.releaseHash()),
                    "corrupt-release\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Store releaseCorrupt = SlabbedRigCaseEvidence.open(
                    root, "minecraft:overworld", buildHash, snapshot);
            ctx.assertTrue(releaseCorrupt.inspect().status() == ResumeStatus.CORRUPT,
                    "a release blob whose bytes disagree with its hash must fail closed");

            Path omissionRoot = worldRoot.resolve("data").resolve("slabbed")
                    .resolve("rig-cases-contract").resolve(UUID.randomUUID().toString());
            Store omission = SlabbedRigCaseEvidence.open(
                    omissionRoot, "minecraft:overworld", buildHash, snapshot);
            PreparedPage omissionPrepared = omission.beginExecution(omission.prepare(plan));
            String omissionCursor = omission.inspect().cursor().cursorHash();
            CaseResult validFirst = resultForTile(plan.tiles().get(0), 0);
            CaseResult missingChangedOwnership = new CaseResult(
                    validFirst.ordinal(),
                    validFirst.caseId(),
                    validFirst.structureStatus(),
                    validFirst.attemptStatus(),
                    validFirst.outcome(),
                    validFirst.inventoryRestored(),
                    validFirst.before(),
                    validFirst.after(),
                    List.of(),
                    validFirst.outOfEnvelope());
            assertIllegalState(
                    ctx,
                    () -> omission.checkpoint(omissionPrepared, missingChangedOwnership),
                    "a result that omits one persisted changed cell must fail closed");
            ctx.assertTrue(omission.inspect().cursor().cursorHash().equals(omissionCursor)
                            && omission.inspect().cursor().active().nextCaseOrdinal() == 0,
                    "refused incomplete ownership must not publish a result or move the cursor");

            Path corruptRoot = worldRoot.resolve("data").resolve("slabbed")
                    .resolve("rig-cases-contract").resolve(UUID.randomUUID().toString());
            Store corrupt = SlabbedRigCaseEvidence.open(
                    corruptRoot, "minecraft:overworld", buildHash, snapshot);
            PreparedPage corruptPrepared = corrupt.prepare(plan);
            Files.writeString(
                    findBlob(corruptRoot, corruptPrepared.plannedHash()),
                    "corrupt\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Store reopenedCorrupt = SlabbedRigCaseEvidence.open(
                    corruptRoot, "minecraft:overworld", buildHash, snapshot);
            ctx.assertTrue(reopenedCorrupt.inspect().status() == ResumeStatus.CORRUPT,
                    "a referenced blob whose bytes disagree with its hash must fail closed");

            System.out.println("[FORGE_RIG_CASE_EVIDENCE] identity="
                    + store.identityHash()
                    + " plan=" + prepared.plannedHash()
                    + " final=" + firstFinalHash
                    + " ownership=" + expectedOwnership.size()
                    + " releases=2 interruption=refused repair=advanced corruption=refused");
        } catch (IOException failure) {
            throw new IllegalStateException("cases evidence Forge proof could not read its files", failure);
        }
    }

    private static void assertCasesExecutor(GameTestHelper ctx, Snapshot snapshot) {
        ServerLevel world = ctx.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(world);
        List<ItemStack> inventoryBefore = snapshotInventory(player);
        double playerXBefore = player.getX();
        double playerYBefore = player.getY();
        double playerZBefore = player.getZ();
        float playerYRotBefore = player.getYRot();
        float playerXRotBefore = player.getXRot();
        SlabbedDiagnosticsBridge.Provider previous = null;
        BlockPos blocker = null;
        BlockPos sentinel = null;
        LinkedHashSet<BlockPos> cleanup = new LinkedHashSet<>();

        BlockPos plotOrigin = ctx.absolutePos(BlockPos.ZERO);
        BlockPos anchor = new BlockPos(plotOrigin.getX() + 2, 200, plotOrigin.getZ() + 2);
        Direction facing = Direction.SOUTH;
        player.moveTo(
                anchor.getX() + 0.5d,
                anchor.getY() + 1.0d,
                anchor.getZ() - 4.0d + 0.5d,
                0.0f,
                0.0f);
        ctx.assertTrue(player.getDirection() == facing
                        && SlabbedRigService.defaultCasesAnchor(player).equals(anchor),
                "fixture precondition: command and explicit service routes must share one anchor");
        CasesPagePlan firstPlan = SlabbedRigService.casesPagePlan(snapshot, anchor, facing, 1);
        CasesPagePlan secondPlan = SlabbedRigService.casesPagePlan(snapshot, anchor, facing, 2);
        CasesPagePlan thirdPlan = SlabbedRigService.casesPagePlan(snapshot, anchor, facing, 3);
        cleanup.addAll(firstPlan.reservedFootprint());
        cleanup.addAll(secondPlan.reservedFootprint());
        cleanup.addAll(thirdPlan.reservedFootprint());

        AtomicInteger opened = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        List<SlabbedDiagnosticsBridge.ActionOriginContext> origins = new ArrayList<>();
        AtomicBoolean preparedAtOrdinalZero = new AtomicBoolean();
        AtomicBoolean preparedBeforeMutation = new AtomicBoolean();
        AtomicBoolean sabotageArmed = new AtomicBoolean();
        AtomicBoolean sabotageTriggered = new AtomicBoolean();
        BlockPos sabotageGuard = thirdPlan.tiles().get(0).guardCells().get(0);

        try {
            assertAvailableFootprint(ctx, world, cleanup);
            ctx.assertTrue(!thirdPlan.autoTiles().isEmpty()
                            && thirdPlan.autoTiles().get(0).equals(thirdPlan.tiles().get(0)),
                    "page three must begin with an AUTO case for the interruption discriminator");

            SlabbedDiagnosticsBridge.Provider tracking =
                    new SlabbedDiagnosticsBridge.Provider() {
                        @Override
                        public SlabbedDiagnosticsBridge.ActionOriginScope enterActionOrigin(
                                String origin,
                                SlabbedDiagnosticsBridge.ActionOriginContext context) {
                            if (SlabbedDiagnosticsBridge.AUTO_USEON_PROXY.equals(origin)) {
                                origins.add(context);
                                opened.incrementAndGet();
                                return closed::incrementAndGet;
                            }
                            return () -> { };
                        }

                        @Override
                        public void log(String tag, String body) {
                            if (!"slabrig_cases".equals(tag)) {
                                return;
                            }
                            if (body.contains("phase=before_fixture page=1 ordinal=0")) {
                                SlabbedRigCaseEvidence.ResumeView view =
                                        SlabbedRigCaseEvidence.open(world, snapshot).inspect();
                                preparedAtOrdinalZero.set(
                                        view.status()
                                                        == ResumeStatus
                                                                .INTERRUPTED_UNKNOWN_OWNERSHIP
                                                && view.cursor() != null
                                                && view.cursor().active() != null
                                                && view.cursor().active().nextCaseOrdinal() == 0);
                                preparedBeforeMutation.set(world.getBlockState(
                                                firstPlan.tiles().get(0).fixtures().get(0).pos())
                                        .isAir());
                            }
                            if (sabotageArmed.get()
                                    && body.contains("phase=after_use_on page=3 ordinal=0")
                                    && sabotageTriggered.compareAndSet(false, true)) {
                                world.setBlock(
                                        sabotageGuard,
                                        Blocks.GOLD_BLOCK.defaultBlockState(),
                                        Block.UPDATE_ALL);
                            }
                        }
                    };
            previous = SlabbedDiagnosticsBridge.install(tracking);

            CommandSourceStack operator = player.createCommandSourceStack()
                    .withLevel(world)
                    .withPermission(2);
            ctx.assertTrue(run(world, operator, "slabrig cases status") > 0,
                    "the registered cases status command must execute before any store exists");

            blocker = firstPlan.tiles().get(0).guardCells().get(0);
            world.setBlock(blocker, Blocks.EMERALD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            SlabbedRigService.CasesRunResult blocked = SlabbedRigService.runCasesAt(
                    world, player, anchor, facing, false);
            SlabbedRigService.CasesStatus blockedStatus = SlabbedRigService.casesStatus(world);
            ctx.assertTrue(blocked.outcome() == SlabbedRigService.CasesRunOutcome.OCCUPIED
                            && blocked.conflicts().contains(blocker)
                            && blockedStatus.storePresent()
                            && blockedStatus.evidenceStatus() == ResumeStatus.FRESH
                            && !blockedStatus.active()
                            && !blockedStatus.boardPresent()
                            && !preparedAtOrdinalZero.get()
                            && world.getBlockState(blocker).is(Blocks.EMERALD_BLOCK),
                    "destination refusal must happen before prepare or any world mutation");
            world.setBlock(blocker, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            SlabAnchorAttachment.clearPlacementTruth(world, blocker);
            blocker = null;

            int firstOrigin = opened.get();
            SlabbedRigService.CasesRunResult first = SlabbedRigService.runCasesAt(
                    world, player, anchor, facing, false);
            ctx.assertTrue(first.success()
                            && first.page() == 1
                            && first.cases() == firstPlan.tiles().size()
                            && first.autoCases() == firstPlan.autoTiles().size()
                            && first.deferredCompleted() == firstPlan.deferredTiles().size()
                            && first.placed() + first.preservedVanilla()
                                            + first.rejected() + first.lawRed()
                                    == first.autoCases()
                            && preparedAtOrdinalZero.get()
                            && preparedBeforeMutation.get(),
                    "page one must be durably prepared before fixture mutation and account exactly");
            assertProxyContexts(
                    ctx,
                    world,
                    player,
                    firstPlan,
                    origins.subList(firstOrigin, origins.size()),
                    opened.get() - firstOrigin,
                    closed.get() - firstOrigin);
            assertInventory(ctx, inventoryBefore, player,
                    "cases page one must restore every inventory slot");

            SlabbedRigService.CasesStatus firstStatus = SlabbedRigService.casesStatus(world);
            ctx.assertTrue(firstStatus.evidenceStatus() == ResumeStatus.READY
                            && firstStatus.nextPage() == 2
                            && !firstStatus.active()
                            && firstStatus.boardPresent()
                            && firstStatus.presentOwnedCells() == first.ownedCells()
                            && firstStatus.clearEligible(),
                    "a completed page must expose one exact clearable durable board");

            Store firstStore = SlabbedRigCaseEvidence.open(world, snapshot);
            List<OwnedCellEvidence> firstBoard = firstStore.rehydratePresentBoardOwnership();
            List<CellEvidence> firstBoardBefore = snapshotCells(
                    world, firstBoard.stream().map(OwnedCellEvidence::pos).toList());
            SlabbedRigCaseEvidence.ProgressCursor firstCursor = firstStore.inspect().cursor();
            Set<BlockPos> firstOwnedPositions = firstBoard.stream()
                    .map(OwnedCellEvidence::pos)
                    .collect(java.util.stream.Collectors.toSet());
            String cursorBeforeForceRefusal = firstCursor.cursorHash();
            blocker = secondPlan.reservedFootprint().stream()
                    .filter(pos -> !firstOwnedPositions.contains(pos))
                    .filter(pos -> world.getBlockState(pos).isAir())
                    .filter(pos -> !SlabAnchorAttachment.storedPlacementDyFact(world, pos).present())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "page two has no unowned reserved cell for guarded-force proof"));
            world.setBlock(blocker, Blocks.EMERALD_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            int scopesBeforeForceRefusal = opened.get();
            SlabbedRigService.CasesRunResult forceRefusal = SlabbedRigService.runCasesAt(
                    world, player, anchor, facing, true);
            ctx.assertTrue(forceRefusal.outcome()
                                    == SlabbedRigService.CasesRunOutcome.OCCUPIED
                            && forceRefusal.conflicts().contains(blocker)
                            && SlabbedRigCaseEvidence.open(world, snapshot).inspect()
                                    .cursor().cursorHash().equals(cursorBeforeForceRefusal)
                            && firstBoardBefore.equals(snapshotCells(
                                    world,
                                    firstBoard.stream().map(OwnedCellEvidence::pos).toList()))
                            && world.getBlockState(blocker).is(Blocks.EMERALD_BLOCK)
                            && opened.get() == scopesBeforeForceRefusal
                            && closed.get() == scopesBeforeForceRefusal,
                    "foreign next-page admission must preserve the prior board and durable cursor");
            world.setBlock(blocker, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            SlabAnchorAttachment.clearPlacementTruth(world, blocker);
            blocker = null;

            int scopesBeforeTransition = opened.get();
            SlabbedRigService.CasesRunResult transitionPaused =
                    SlabbedRigService.runCasesAtForGameTest(
                            world,
                            player,
                            anchor,
                            facing,
                            true,
                            SlabbedRigService.CasesTransitionTestFault
                                    .AFTER_PREPARE_BEFORE_REPLACEMENT_CLEAR);
            SlabbedRigService.CasesStatus preparedStatus =
                    SlabbedRigService.casesStatus(world);
            SlabbedRigCaseEvidence.ResumeView preparedView =
                    SlabbedRigCaseEvidence.open(world, snapshot).inspect();
            ctx.assertTrue(transitionPaused.outcome()
                                    == SlabbedRigService.CasesRunOutcome.TRANSITION_PAUSED
                            && preparedStatus.evidenceStatus()
                                    == ResumeStatus.PREPARED_NO_MUTATION
                            && preparedStatus.active()
                            && preparedStatus.activeOrdinal() == 0
                            && preparedStatus.boardPresent()
                            && preparedStatus.presentOwnedCells() == firstBoard.size()
                            && !preparedStatus.clearEligible()
                            && preparedView.cursor().active().phase()
                                    == SlabbedRigCaseEvidence.AttemptPhase.PREPARED
                            && preparedView.cursor().active().page() == 2
                            && preparedView.cursor().boardFinalHash()
                                    .equals(firstCursor.boardFinalHash())
                            && preparedView.cursor().lastFinalHash()
                                    .equals(firstCursor.lastFinalHash())
                            && preparedView.cursor().lastReleaseHash()
                                    .equals(firstCursor.lastReleaseHash())
                            && preparedView.presentBoardOwnership().equals(firstBoard)
                            && firstBoardBefore.equals(snapshotCells(
                                    world,
                                    firstBoard.stream().map(OwnedCellEvidence::pos).toList()))
                            && opened.get() == scopesBeforeTransition
                            && closed.get() == scopesBeforeTransition,
                    "a paused forced replacement must durably prepare page two before touching"
                            + " page one or opening any proxy placement scope");

            ctx.assertTrue(run(world, operator, "slabrig cases resume") > 0,
                    "the real cases resume command must abort a mutation-free prepared page");
            SlabbedRigService.CasesStatus afterPreparedAbort =
                    SlabbedRigService.casesStatus(world);
            SlabbedRigCaseEvidence.ResumeView afterPreparedAbortView =
                    SlabbedRigCaseEvidence.open(world, snapshot).inspect();
            ctx.assertTrue(afterPreparedAbort.evidenceStatus() == ResumeStatus.READY
                            && afterPreparedAbort.nextPage() == 2
                            && !afterPreparedAbort.active()
                            && afterPreparedAbort.boardPresent()
                            && afterPreparedAbort.presentOwnedCells() == firstBoard.size()
                            && afterPreparedAbort.clearEligible()
                            && afterPreparedAbortView.cursor().active() == null
                            && afterPreparedAbortView.cursor().boardFinalHash()
                                    .equals(firstCursor.boardFinalHash())
                            && afterPreparedAbortView.cursor().lastFinalHash()
                                    .equals(firstCursor.lastFinalHash())
                            && afterPreparedAbortView.cursor().lastReleaseHash()
                                    .equals(firstCursor.lastReleaseHash())
                            && afterPreparedAbortView.presentBoardOwnership().equals(firstBoard)
                            && firstBoardBefore.equals(snapshotCells(
                                    world,
                                    firstBoard.stream().map(OwnedCellEvidence::pos).toList()))
                            && opened.get() == scopesBeforeTransition
                            && closed.get() == scopesBeforeTransition,
                    "resume must remove only the prepared successor and restore the exact"
                            + " clearable page-one state");

            int scopesBeforeBareRefusal = opened.get();
            ctx.assertTrue(run(world, operator, "slabrig cases") == 0
                            && SlabbedRigService.casesStatus(world).nextPage() == 2
                            && firstBoardBefore.equals(snapshotCells(
                                    world,
                                    firstBoard.stream().map(OwnedCellEvidence::pos).toList()))
                            && opened.get() == scopesBeforeBareRefusal
                            && closed.get() == scopesBeforeBareRefusal,
                    "bare cases must route to the guarded board-present refusal without mutation");

            OwnedCellEvidence conflictCell = firstBoard.stream()
                    .filter(cell -> cell.role() == RigManifest.CellRole.FIXTURE)
                    .filter(cell -> !cell.expectedStoredDy().present())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "cases page one contains no fixture with absent stored truth"));
            world.setBlock(
                    conflictCell.pos(),
                    Blocks.EMERALD_BLOCK.defaultBlockState(),
                    Block.UPDATE_CLIENTS);
            SlabAnchorAttachment.clearPlacementTruth(world, conflictCell.pos());
            SlabbedRigService.CasesClearResult conflict = SlabbedRigService.clearCases(world);
            ctx.assertTrue(conflict.outcome() == SlabbedRigService.CasesClearOutcome.CONFLICT
                            && conflict.residualCells().contains(conflictCell.pos())
                            && world.getBlockState(conflictCell.pos()).is(Blocks.EMERALD_BLOCK),
                    "exact cases clear must refuse a foreign change without touching the board");
            world.setBlock(
                    conflictCell.pos(), conflictCell.expectedState(), Block.UPDATE_CLIENTS);
            SlabAnchorAttachment.clearPlacementTruth(world, conflictCell.pos());
            ctx.assertTrue(SlabbedRigService.casesStatus(world).clearEligible(),
                    "restoring exact board truth must restore clear eligibility");

            sentinel = anchor.offset(-4, 0, -4);
            ctx.assertTrue(!cleanup.contains(sentinel),
                    "fixture precondition: unrelated sentinel must be outside every page footprint");
            world.setBlock(sentinel, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);

            int secondOrigin = opened.get();
            ctx.assertTrue(run(world, operator, "slabrig cases force") > 0,
                    "guarded force must execute the exact next page through the command route");
            SlabbedRigService.CasesStatus secondStatus = SlabbedRigService.casesStatus(world);
            Store secondStore = SlabbedRigCaseEvidence.open(world, snapshot);
            ctx.assertTrue(secondStatus.evidenceStatus() == ResumeStatus.READY
                            && secondStatus.nextPage() == 3
                            && secondStatus.boardPresent()
                            && secondStatus.clearEligible()
                            && !secondStore.inspect().cursor().lastReleaseHash()
                                    .equals(SlabbedRigCaseEvidence.GENESIS)
                            && world.getBlockState(sentinel).is(Blocks.GOLD_BLOCK),
                    "guarded force must release only page one and publish page two as present");
            assertProxyContexts(
                    ctx,
                    world,
                    player,
                    secondPlan,
                    origins.subList(secondOrigin, origins.size()),
                    opened.get() - secondOrigin,
                    closed.get() - secondOrigin);
            assertInventory(ctx, inventoryBefore, player,
                    "guarded force replacement must restore every inventory slot");

            List<OwnedCellEvidence> secondBoard =
                    secondStore.rehydratePresentBoardOwnership();
            ctx.assertTrue(run(world, operator, "slabrig cases clear") > 0,
                    "the exact cases clear command must clear and durably release page two");
            assertOwnedAbsent(ctx, world, secondBoard);
            SlabbedRigService.CasesStatus afterClear = SlabbedRigService.casesStatus(world);
            ctx.assertTrue(afterClear.evidenceStatus() == ResumeStatus.READY
                            && afterClear.nextPage() == 3
                            && !afterClear.active()
                            && !afterClear.boardPresent()
                            && world.getBlockState(sentinel).is(Blocks.GOLD_BLOCK),
                    "exact clear must preserve unrelated cells and leave page three ready");

            sabotageArmed.set(true);
            int thirdOrigin = opened.get();
            SlabbedRigService.CasesRunResult interrupted = SlabbedRigService.runCasesAt(
                    world, player, anchor, facing, false);
            SlabbedRigService.CasesStatus interruptedStatus = SlabbedRigService.casesStatus(world);
            ctx.assertTrue(sabotageTriggered.get()
                            && interrupted.outcome()
                                    == SlabbedRigService.CasesRunOutcome.EXECUTION_INTERRUPTED
                            && interruptedStatus.evidenceStatus()
                                    == ResumeStatus.INTERRUPTED_UNKNOWN_OWNERSHIP
                            && interruptedStatus.active()
                            && interruptedStatus.activeOrdinal() == 0
                            && opened.get() - thirdOrigin == 1
                            && closed.get() - thirdOrigin == 1
                            && world.getBlockState(sabotageGuard).is(Blocks.GOLD_BLOCK),
                    "a changed read-only guard must leave page three interrupted at ordinal zero");
            assertInventory(ctx, inventoryBefore, player,
                    "an interrupted cases action must still restore every inventory slot");

            List<CellEvidence> interruptedWorld =
                    snapshotCells(world, thirdPlan.reservedFootprint());
            SlabbedRigService.CasesRunResult repeat = SlabbedRigService.runCasesAt(
                    world, player, anchor, facing, false);
            SlabbedRigService.CasesResumeResult resume = SlabbedRigService.resumeCases(world);
            SlabbedRigService.CasesClearResult interruptedClear =
                    SlabbedRigService.clearCases(world);
            ctx.assertTrue(repeat.outcome() == SlabbedRigService.CasesRunOutcome.STORE_NOT_READY
                            && resume.outcome() == SlabbedRigService.CasesResumeOutcome.REFUSED
                            && interruptedClear.outcome()
                                    == SlabbedRigService.CasesClearOutcome.INTERRUPTED
                            && interruptedWorld.equals(
                                    snapshotCells(world, thirdPlan.reservedFootprint()))
                            && world.getBlockState(sentinel).is(Blocks.GOLD_BLOCK),
                    "unknown current-case ownership must refuse run/resume/clear without mutation");

            System.out.println("[FORGE_RIG_CASE_EXECUTOR] pages=2"
                    + " page1_auto=" + firstPlan.autoTiles().size()
                    + " page2_auto=" + secondPlan.autoTiles().size()
                    + " prepared_before_mutation=true"
                    + " replacement_prepare_abort=exact"
                    + " clear=exact"
                    + " interruption=refused");
        } finally {
            if (previous != null) {
                SlabbedDiagnosticsBridge.install(previous);
            }
            List<BlockPos> cleanupOrder = new ArrayList<>(cleanup);
            for (int index = cleanupOrder.size() - 1; index >= 0; index--) {
                BlockPos pos = cleanupOrder.get(index);
                if (world.isInWorldBounds(pos) && world.hasChunkAt(pos)) {
                    world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    SlabAnchorAttachment.clearPlacementTruth(world, pos);
                }
            }
            if (blocker != null && world.hasChunkAt(blocker)) {
                world.setBlock(blocker, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                SlabAnchorAttachment.clearPlacementTruth(world, blocker);
            }
            if (sentinel != null && world.hasChunkAt(sentinel)) {
                world.setBlock(sentinel, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                SlabAnchorAttachment.clearPlacementTruth(world, sentinel);
            }
            restoreInventory(player, inventoryBefore);
            player.moveTo(
                    playerXBefore,
                    playerYBefore,
                    playerZBefore,
                    playerYRotBefore,
                    playerXRotBefore);
        }
    }

    private static void assertAvailableFootprint(
            GameTestHelper ctx,
            ServerLevel world,
            Iterable<BlockPos> positions) {
        for (BlockPos pos : positions) {
            ctx.assertTrue(world.isInWorldBounds(pos)
                            && world.hasChunkAt(pos)
                            && world.getBlockState(pos).isAir()
                            && !SlabAnchorAttachment.storedPlacementDyFact(world, pos).present(),
                    "cases footprint must begin loaded, in bounds, empty, and unstored at "
                            + pos.toShortString());
        }
    }

    private static void assertProxyContexts(
            GameTestHelper ctx,
            ServerLevel world,
            ServerPlayer player,
            CasesPagePlan plan,
            List<SlabbedDiagnosticsBridge.ActionOriginContext> contexts,
            int opened,
            int closed) {
        ctx.assertTrue(contexts.size() == plan.autoTiles().size()
                        && opened == plan.autoTiles().size()
                        && closed == plan.autoTiles().size(),
                "every AUTO cases tile must open and close exactly one proxy scope");
        for (int index = 0; index < contexts.size(); index++) {
            SlabbedDiagnosticsBridge.ActionOriginContext context = contexts.get(index);
            CasesTilePlan tile = plan.autoTiles().get(index);
            ctx.assertTrue(context.playerUuid().equals(player.getUUID().toString())
                            && context.dimensionId()
                                    .equals(world.dimension().location().toString())
                            && (context.placementPos().equals(tile.cursor())
                                    || context.placementPos().equals(tile.target())),
                    "cases proxy scope must bind player, dimension, and clicked-or-above cell");
        }
    }

    private static List<ItemStack> snapshotInventory(ServerPlayer player) {
        List<ItemStack> snapshot = new ArrayList<>(player.getInventory().getContainerSize());
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            snapshot.add(player.getInventory().getItem(slot).copy());
        }
        return List.copyOf(snapshot);
    }

    private static void restoreInventory(
            ServerPlayer player,
            List<ItemStack> snapshot) {
        for (int slot = 0; slot < snapshot.size(); slot++) {
            player.getInventory().setItem(slot, snapshot.get(slot).copy());
        }
        player.getInventory().setChanged();
    }

    private static void assertInventory(
            GameTestHelper ctx,
            List<ItemStack> expected,
            ServerPlayer player,
            String detail) {
        ctx.assertTrue(player.getInventory().getContainerSize() == expected.size(), detail);
        for (int slot = 0; slot < expected.size(); slot++) {
            ctx.assertTrue(ItemStack.matches(
                            expected.get(slot), player.getInventory().getItem(slot)),
                    detail + " (slot " + slot + ")");
        }
    }

    private static List<CellEvidence> snapshotCells(
            ServerLevel world,
            List<BlockPos> positions) {
        return positions.stream()
                .map(pos -> new CellEvidence(
                        pos,
                        world.getBlockState(pos),
                        SlabAnchorAttachment.storedPlacementDyFact(world, pos)))
                .toList();
    }

    private static void assertOwnedAbsent(
            GameTestHelper ctx,
            ServerLevel world,
            List<OwnedCellEvidence> owned) {
        for (OwnedCellEvidence cell : owned) {
            ctx.assertTrue(world.getBlockState(cell.pos()).isAir()
                            && !SlabAnchorAttachment.storedPlacementDyFact(world, cell.pos())
                                    .present(),
                    "exact cases clear must remove block and stored truth at "
                            + cell.pos().toShortString());
        }
    }

    private static int run(
            ServerLevel world,
            CommandSourceStack source,
            String command) {
        return world.getServer().getCommands().performPrefixedCommand(source, command);
    }

    private static Snapshot evidenceSnapshot(Snapshot liveSnapshot) {
        List<CatalogItem> selected = new ArrayList<>(liveSnapshot.items().stream()
                .filter(item -> item.disposition()
                        == SlabbedRigCaseCatalog.Disposition.AUTO_FLOOR_UP)
                .limit(3)
                .toList());
        selected.add(included(liveSnapshot, "minecraft:lily_pad"));
        selected.sort(Comparator.comparing(CatalogItem::id));
        List<CatalogItem> reindexed = new ArrayList<>(selected.size());
        for (int index = 0; index < selected.size(); index++) {
            CatalogItem item = selected.get(index);
            reindexed.add(new CatalogItem(
                    index, item.id(), item.categories(), item.disposition(), item.effectPolicy()));
        }
        long totalCases = (long) reindexed.size() * SlabbedRigCaseCatalog.TOPOLOGY_COUNT;
        int pageCount = SlabbedRigCaseCatalog.TOPOLOGY_GROUPS;
        Snapshot draft = new Snapshot(
                SlabbedRigCaseCatalog.SCHEMA,
                "0".repeat(64),
                reindexed,
                List.of(),
                liveSnapshot.topologies(),
                totalCases,
                pageCount);
        return new Snapshot(
                SlabbedRigCaseCatalog.SCHEMA,
                sha256(canonicalCatalog(draft)),
                reindexed,
                List.of(),
                liveSnapshot.topologies(),
                totalCases,
                pageCount);
    }

    private static CaseResult resultForTile(CasesTilePlan tile, int ordinal) {
        if (!tile.autoEligible()) {
            return new CaseResult(
                    ordinal,
                    tile.definition().id(),
                    StructureStatus.DEFERRED,
                    AttemptStatus.NOT_ATTEMPTED,
                    CaseOutcome.DEFERRED,
                    true,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }
        Map<BlockPos, BlockState> fixtureStates = new HashMap<>();
        for (RigCase.FixtureCell fixture : tile.fixtures()) {
            fixtureStates.put(fixture.pos(), fixture.state());
        }
        SlabAnchorAttachment.PlacementDyFact absent =
                SlabAnchorAttachment.PlacementDyFact.absent();
        List<CellEvidence> before = tile.reservedCells().stream()
                .map(pos -> new CellEvidence(pos, Blocks.AIR.defaultBlockState(), absent))
                .toList();
        List<CellEvidence> after = tile.reservedCells().stream()
                .map(pos -> new CellEvidence(
                        pos,
                        fixtureStates.getOrDefault(pos, Blocks.AIR.defaultBlockState()),
                        absent))
                .toList();
        List<OwnedCellEvidence> owned = tile.fixtures().stream()
                .map(fixture -> new OwnedCellEvidence(
                        fixture.pos(),
                        fixture.state(),
                        absent,
                        RigManifest.CellRole.FIXTURE,
                        tile.definition().id()))
                .toList();
        return new CaseResult(
                ordinal,
                tile.definition().id(),
                StructureStatus.INCOMPLETE,
                AttemptStatus.ATTEMPTED,
                CaseOutcome.REJECTED,
                true,
                before,
                after,
                owned,
                List.of());
    }

    private static Path findBlob(Path root, String hash) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(hash + ".tsv"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("missing evidence blob " + hash));
        }
    }

    private static Path findNamedFile(Path root, String name) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("missing evidence file " + name));
        }
    }

    private static byte[] replaceCursorReleaseHead(
            byte[] original,
            String expectedHead,
            String replacementHead) {
        String text = new String(original, StandardCharsets.UTF_8);
        String hashMarker = "cursor_hash\t";
        int hashOffset = text.lastIndexOf(hashMarker);
        if (hashOffset < 0 || !text.endsWith("\n")) {
            throw new IllegalStateException("cases cursor is not canonically signed");
        }
        String core = text.substring(0, hashOffset);
        String expectedRow = "last_release_hash\t" + expectedHead + "\n";
        if (core.indexOf(expectedRow) < 0
                || core.indexOf(expectedRow) != core.lastIndexOf(expectedRow)) {
            throw new IllegalStateException("cases cursor release head is not unique");
        }
        String replaced = core.replace(
                expectedRow,
                "last_release_hash\t" + replacementHead + "\n");
        return (replaced + hashMarker
                + sha256(replaced.getBytes(StandardCharsets.UTF_8)) + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void assertContentAddressedBlobs(GameTestHelper ctx, Path root)
            throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            List<Path> blobs = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().contains("/blobs/"))
                    .toList();
            ctx.assertTrue(!blobs.isEmpty(), "cases evidence must publish immutable blobs");
            for (Path blob : blobs) {
                String filename = blob.getFileName().toString();
                int dot = filename.indexOf('.');
                String claimed = dot < 0 ? filename : filename.substring(0, dot);
                ctx.assertTrue(claimed.matches("[0-9a-f]{64}")
                                && claimed.equals(sha256(Files.readAllBytes(blob))),
                        "every evidence blob filename must equal SHA-256 of its exact bytes");
            }
        }
    }

    private static void assertTags(
            GameTestHelper ctx,
            CatalogItem item,
            String... required) {
        for (String tag : required) {
            ctx.assertTrue(item.categories().contains(tag),
                    item.id() + " must carry semantic tag " + tag);
        }
    }

    private static CatalogItem included(Snapshot snapshot, String id) {
        return snapshot.items().stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing included item " + id));
    }

    private static ExcludedItem excluded(Snapshot snapshot, String id) {
        return snapshot.excludedItems().stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing excluded item " + id));
    }

    private static void appendRecipes(
            List<String> recipes,
            StringBuilder prefix,
            int remaining) {
        if (remaining == 0) {
            recipes.add(prefix.toString());
            return;
        }
        prefix.append('S');
        appendRecipes(recipes, prefix, remaining - 1);
        prefix.setLength(prefix.length() - 1);
        prefix.append('B');
        appendRecipes(recipes, prefix, remaining - 1);
        prefix.setLength(prefix.length() - 1);
    }

    private static String canonicalCatalog(Snapshot snapshot) {
        StringBuilder text = new StringBuilder();
        text.append("schema\t").append(SlabbedRigCaseCatalog.SCHEMA).append('\n');
        text.append("page_geometry\t")
                .append(SlabbedRigCaseCatalog.PAGE_GEOMETRY).append('\n');
        text.append("execution_contract\t")
                .append(SlabbedRigCaseCatalog.EXECUTION_CONTRACT).append('\n');
        for (CatalogItem item : snapshot.items()) {
            text.append("item\t")
                    .append(item.index()).append('\t')
                    .append(item.id()).append('\t')
                    .append(String.join(",", item.categories())).append('\t')
                    .append("disposition=").append(item.disposition())
                    .append(",effect=").append(item.effectPolicy()).append('\n');
        }
        for (ExcludedItem item : snapshot.excludedItems()) {
            text.append("excluded\t")
                    .append(item.index()).append('\t')
                    .append(item.id()).append('\t')
                    .append("kind:item:").append(item.itemKind())
                    .append(",reason=").append(item.reason()).append('\t')
                    .append(item.route()).append('\n');
        }
        for (Topology topology : snapshot.topologies()) {
            text.append("topology\t")
                    .append(topology.index()).append('\t')
                    .append(topology.id()).append('\t')
                    .append(topology.control() ? "control" : "stack").append('\t')
                    .append(topology.recipe()).append('\n');
        }
        return text.toString();
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void assertIllegalArgument(
            GameTestHelper ctx,
            Runnable action,
            String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        ctx.assertTrue(rejected, message);
    }

    private static void assertIllegalState(
            GameTestHelper ctx,
            Runnable action,
            String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        ctx.assertTrue(rejected, message);
    }
}
