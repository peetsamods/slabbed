package com.slabbed.rig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.GameMasterBlockItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.ScaffoldingBlockItem;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Pure, deterministic catalog contract for the {@code /slabrig cases} executor.
 *
 * <p>This class deliberately performs no world mutation. Its case IDs match the behavior
 * contract, while the catalog hash binds the executor's checkpoint, interruption, exact-clear,
 * guarded-replacement, and deterministic-repair rules.
 */
public final class SlabbedRigCaseCatalog {
    public static final String SCHEMA = "slabbed-rig-case-catalog-v1";
    public static final String PLACEMENT_MODE = "FLOOR_UP";
    public static final String PAGE_GEOMETRY = "4_items_x_4_topologies";
    public static final String EXECUTION_CONTRACT =
            "cases-executor-v1;checkpoint=per-case;interruption=refuse;"
                    + "clear=exact-release;resume=deterministic-only;force=guarded-exact";
    public static final int PAGE_GRID_SIDE = 4;
    public static final int PAGE_SIZE = PAGE_GRID_SIDE * PAGE_GRID_SIDE;
    public static final int TOPOLOGY_COUNT = 64;
    public static final int TOPOLOGY_GROUPS = TOPOLOGY_COUNT / PAGE_GRID_SIDE;

    private static final String CASE_ID_PREFIX = "case-v1:sha256:";
    private static final Set<Class<?>> KNOWN_LOCAL_BLOCK_ITEM_CLASSES = Set.of(
            BlockItem.class,
            BannerItem.class,
            BedItem.class,
            DoubleHighBlockItem.class,
            GameMasterBlockItem.class,
            HangingSignItem.class,
            PlayerHeadItem.class,
            SignItem.class,
            SolidBucketItem.class,
            StandingAndWallBlockItem.class);
    private static final List<Topology> TOPOLOGIES = createTopologies();

    private SlabbedRigCaseCatalog() {
    }

    /** Builds a fresh snapshot from the live Forge item registry. */
    public static Snapshot snapshot() {
        List<RegistryItem> registryItems = BuiltInRegistries.ITEM.stream()
                .map(item -> new RegistryItem(
                        Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(item),
                                "unkeyed registry item"),
                        item))
                .sorted(Comparator.comparing(entry -> entry.id().toString()))
                .toList();

        List<CatalogItem> items = new ArrayList<>();
        List<ExcludedItem> excludedItems = new ArrayList<>();
        for (RegistryItem entry : registryItems) {
            if (entry.item() instanceof BlockItem blockItem) {
                EffectPolicy effectPolicy = effectPolicy(entry.id(), blockItem);
                items.add(new CatalogItem(
                        items.size(),
                        entry.id().toString(),
                        categories(entry.id(), blockItem),
                        effectPolicy == EffectPolicy.LOCAL_TARGET_AND_NEIGHBORS
                                ? Disposition.AUTO_FLOOR_UP
                                : Disposition.DEFERRED_ROUTE,
                        effectPolicy));
            } else {
                Exclusion exclusion = exclusion(entry.item());
                excludedItems.add(new ExcludedItem(
                        excludedItems.size(),
                        entry.id().toString(),
                        exclusion.itemKind(),
                        "not_block_item",
                        exclusion.route()));
            }
        }

        String catalogHash = sha256(canonicalCatalog(items, excludedItems, TOPOLOGIES));
        long totalCases = Math.multiplyExact((long) items.size(), TOPOLOGY_COUNT);
        int itemGroups = (items.size() + PAGE_GRID_SIDE - 1) / PAGE_GRID_SIDE;
        int pageCount = Math.multiplyExact(itemGroups, TOPOLOGY_GROUPS);
        return new Snapshot(
                SCHEMA,
                catalogHash,
                items,
                excludedItems,
                TOPOLOGIES,
                totalCases,
                pageCount);
    }

    public static List<Topology> topologies() {
        return TOPOLOGIES;
    }

    /** Resolves the stable item-major/topology-minor case at a canonical index. */
    public static CaseDefinition caseAt(Snapshot snapshot, long index) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (index < 0 || index >= snapshot.totalCases()) {
            throw new IllegalArgumentException("case index out of range: " + index);
        }
        int itemIndex = Math.toIntExact(index / TOPOLOGY_COUNT);
        int topologyIndex = (int) (index % TOPOLOGY_COUNT);
        return definition(snapshot.items().get(itemIndex), snapshot.topologies().get(topologyIndex));
    }

    /** Resolves one deterministic 4-item by 4-topology page. */
    public static CasePage page(Snapshot snapshot, int page) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (page < 1 || page > snapshot.pageCount()) {
            throw new IllegalArgumentException(
                    "case page must be in 1.." + snapshot.pageCount() + ": " + page);
        }
        int itemGroup = (page - 1) / TOPOLOGY_GROUPS;
        int topologyGroup = (page - 1) % TOPOLOGY_GROUPS;
        int itemStart = itemGroup * PAGE_GRID_SIDE;
        int topologyStart = topologyGroup * PAGE_GRID_SIDE;
        List<CaseDefinition> cases = new ArrayList<>(PAGE_SIZE);
        for (int itemIndex = itemStart;
                itemIndex < Math.min(itemStart + PAGE_GRID_SIDE, snapshot.items().size());
                itemIndex++) {
            CatalogItem item = snapshot.items().get(itemIndex);
            for (int topologyIndex = topologyStart;
                    topologyIndex < topologyStart + PAGE_GRID_SIDE;
                    topologyIndex++) {
                cases.add(definition(item, snapshot.topologies().get(topologyIndex)));
            }
        }
        return new CasePage(
                page,
                snapshot.pageCount(),
                itemGroup,
                topologyGroup,
                cases.get(0).index(),
                cases.get(cases.size() - 1).index(),
                cases);
    }

    /** Stable identity excludes page and ordinal by design. */
    public static String caseId(String itemId, String topologyId, String placementMode) {
        requireText(itemId, "itemId");
        requireText(topologyId, "topologyId");
        requireText(placementMode, "placementMode");
        return CASE_ID_PREFIX + sha256(
                SCHEMA + '\0' + itemId + '\0' + topologyId + '\0' + placementMode);
    }

    /** Deterministic, read-only catalog artifact for the future durable executor. */
    public static String catalogTsv(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return "# catalog_hash\t" + snapshot.catalogHash() + '\n'
                + canonicalCatalog(snapshot.items(), snapshot.excludedItems(), snapshot.topologies());
    }

    private static CaseDefinition definition(CatalogItem item, Topology topology) {
        long index = Math.addExact(
                Math.multiplyExact((long) item.index(), TOPOLOGY_COUNT), topology.index());
        return new CaseDefinition(
                index,
                caseId(item.id(), topology.id(), PLACEMENT_MODE),
                item,
                topology,
                PLACEMENT_MODE);
    }

    private static List<String> categories(ResourceLocation id, BlockItem item) {
        TreeSet<String> categories = new TreeSet<>();
        String route = itemRoute(item);
        String shape = shapeTag(item.getBlock());
        categories.add("universe:block_item");
        categories.add("namespace:" + id.getNamespace());
        categories.add("route:" + route);
        categories.add("kind:item:" + route);
        categories.add(shape);

        Block block = item.getBlock();
        boolean family = false;
        family |= addFamily(categories, block instanceof SlabBlock, "slab");
        family |= addFamily(categories, block instanceof StairBlock, "stair");
        family |= addFamily(categories, block instanceof DoorBlock, "door");
        family |= addFamily(categories, block instanceof TrapDoorBlock, "trapdoor");
        family |= addFamily(categories, block instanceof BedBlock, "bed");
        family |= addFamily(categories, block instanceof FenceBlock, "fence");
        family |= addFamily(categories, block instanceof FenceGateBlock, "fence_gate");
        family |= addFamily(categories, block instanceof WallBlock, "wall");
        family |= addFamily(categories, block instanceof IronBarsBlock, "pane_or_bars");
        family |= addFamily(categories, block instanceof CarpetBlock, "thin_layer");
        family |= addFamily(
                categories, block instanceof BasePressurePlateBlock, "surface_component");
        family |= addFamily(categories, block instanceof BaseRailBlock, "rail");
        family |= addFamily(
                categories,
                block instanceof TorchBlock || block instanceof WallTorchBlock,
                "torch");
        family |= addFamily(categories, block instanceof LanternBlock, "lantern");
        family |= addFamily(categories, block instanceof ChainBlock, "chain");
        boolean hangingSign = block instanceof CeilingHangingSignBlock
                || block instanceof WallHangingSignBlock;
        family |= addFamily(
                categories,
                block instanceof ChainBlock || block instanceof LanternBlock || hangingSign,
                "hanging_capable");
        family |= addFamily(categories, block instanceof SignBlock, "sign");
        family |= addFamily(categories, hangingSign, "hanging_sign");
        family |= addFamily(categories, block instanceof AbstractBannerBlock, "banner");
        family |= addFamily(categories, block instanceof AbstractSkullBlock, "skull_or_head");
        family |= addFamily(categories, block instanceof FlowerPotBlock, "flower_pot");
        family |= addFamily(categories, block instanceof AbstractCandleBlock, "candle");
        family |= addFamily(categories, block instanceof ButtonBlock, "button");
        family |= addFamily(categories, block instanceof LadderBlock, "wall_attached");
        family |= addFamily(categories, block instanceof BushBlock, "plant");
        family |= addFamily(categories, block instanceof FallingBlock, "falling_block");
        family |= addFamily(
                categories,
                block instanceof DiodeBlock
                        || block instanceof RedStoneWireBlock
                        || block instanceof BasePressurePlateBlock
                        || block instanceof ButtonBlock
                        || block instanceof TripWireBlock
                        || block instanceof TripWireHookBlock,
                "redstone");
        family |= addFamily(categories, block instanceof EntityBlock, "block_entity");

        if (!family) {
            categories.add(switch (shape) {
                case "shape:full_cube" -> "family:ordinary_full_cube";
                case "shape:partial" -> "family:ordinary_partial_shape";
                default -> "family:ordinary_contextual_shape";
            });
        }
        return List.copyOf(categories);
    }

    private static boolean addFamily(Set<String> categories, boolean matches, String family) {
        if (matches) {
            categories.add("family:" + family);
        }
        return matches;
    }

    private static String shapeTag(Block block) {
        try {
            VoxelShape shape = block.defaultBlockState()
                    .getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (shape.isEmpty()) {
                return "shape:empty_or_contextual";
            }
            if (Block.isShapeFullBlock(shape)) {
                return "shape:full_cube";
            }
            return "shape:partial";
        } catch (RuntimeException exception) {
            return "shape:dynamic_or_contextual";
        }
    }

    private static String itemRoute(BlockItem item) {
        if (item instanceof BedItem) {
            return "bed_block_item";
        }
        if (item instanceof HangingSignItem) {
            return "hanging_sign_block_item";
        }
        if (item instanceof DoubleHighBlockItem) {
            return "double_high_block_item";
        }
        if (item instanceof SolidBucketItem) {
            return "solid_bucket_block_item";
        }
        if (item instanceof PlaceOnWaterBlockItem) {
            return "place_on_water_block_item";
        }
        if (item instanceof GameMasterBlockItem) {
            return "game_master_block_item";
        }
        if (item instanceof ScaffoldingBlockItem) {
            return "scaffolding_block_item";
        }
        if (item instanceof StandingAndWallBlockItem) {
            return "standing_and_wall_block_item";
        }
        return "standard_block_item";
    }

    private static EffectPolicy effectPolicy(ResourceLocation id, BlockItem item) {
        if (!"minecraft".equals(id.getNamespace())) {
            return EffectPolicy.DEFERRED_UNKNOWN_EFFECT;
        }
        if (item instanceof PlaceOnWaterBlockItem) {
            return EffectPolicy.DEFERRED_WATER_SURFACE;
        }
        if (item instanceof ScaffoldingBlockItem) {
            return EffectPolicy.DEFERRED_UNBOUNDED_STACK;
        }
        if (KNOWN_LOCAL_BLOCK_ITEM_CLASSES.contains(item.getClass())) {
            return EffectPolicy.LOCAL_TARGET_AND_NEIGHBORS;
        }
        return EffectPolicy.DEFERRED_UNKNOWN_EFFECT;
    }

    private static Exclusion exclusion(Item item) {
        if (item instanceof HangingEntityItem) {
            return new Exclusion("hanging_entity_item", "dedicated_hanging_entity");
        }
        if (item instanceof ArmorStandItem) {
            return new Exclusion("armor_stand_item", "dedicated_entity_placement");
        }
        if (item instanceof BoatItem) {
            return new Exclusion("boat_item", "dedicated_entity_placement");
        }
        if (item instanceof MinecartItem) {
            return new Exclusion("minecart_item", "dedicated_entity_placement");
        }
        if (item instanceof EndCrystalItem) {
            return new Exclusion("end_crystal_item", "dedicated_entity_placement");
        }
        if (item instanceof BucketItem) {
            return new Exclusion("fluid_container_item", "dedicated_fluid_container");
        }
        return new Exclusion(
                "inventory_or_interaction_item", "non_block_interaction_or_inventory");
    }

    private static List<Topology> createTopologies() {
        List<Topology> topologies = new ArrayList<>(TOPOLOGY_COUNT);
        topologies.add(new Topology(0, "control:ground_full_block", "GROUND", true));
        topologies.add(new Topology(1, "control:single_slab", "SINGLE_SLAB", true));
        for (int length = 1; length <= 5; length++) {
            appendTopologies(topologies, new StringBuilder(length), length);
        }
        if (topologies.size() != TOPOLOGY_COUNT) {
            throw new IllegalStateException("cases topology grammar must contain exactly 64 rows");
        }
        return List.copyOf(topologies);
    }

    private static void appendTopologies(
            List<Topology> topologies,
            StringBuilder prefix,
            int remaining) {
        if (remaining == 0) {
            String recipe = prefix.toString();
            topologies.add(new Topology(
                    topologies.size(), "stack:" + recipe, recipe, false));
            return;
        }
        prefix.append('S');
        appendTopologies(topologies, prefix, remaining - 1);
        prefix.setLength(prefix.length() - 1);
        prefix.append('B');
        appendTopologies(topologies, prefix, remaining - 1);
        prefix.setLength(prefix.length() - 1);
    }

    private static String canonicalCatalog(
            List<CatalogItem> items,
            List<ExcludedItem> excludedItems,
            List<Topology> topologies) {
        StringBuilder text = new StringBuilder();
        text.append("schema\t").append(SCHEMA).append('\n');
        text.append("page_geometry\t").append(PAGE_GEOMETRY).append('\n');
        text.append("execution_contract\t").append(EXECUTION_CONTRACT).append('\n');
        for (CatalogItem item : items) {
            text.append("item\t")
                    .append(item.index()).append('\t')
                    .append(item.id()).append('\t')
                    .append(String.join(",", item.categories())).append('\t')
                    .append("disposition=").append(item.disposition())
                    .append(",effect=").append(item.effectPolicy()).append('\n');
        }
        for (ExcludedItem item : excludedItems) {
            text.append("excluded\t")
                    .append(item.index()).append('\t')
                    .append(item.id()).append('\t')
                    .append("kind:item:").append(item.itemKind())
                    .append(",reason=").append(item.reason()).append('\t')
                    .append(item.route()).append('\n');
        }
        for (Topology topology : topologies) {
            text.append("topology\t")
                    .append(topology.index()).append('\t')
                    .append(topology.id()).append('\t')
                    .append(topology.control() ? "control" : "stack").append('\t')
                    .append(topology.recipe()).append('\n');
        }
        return text.toString();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM lacks SHA-256", exception);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static List<String> requireSortedDistinct(List<String> values, String name) {
        List<String> copy = List.copyOf(Objects.requireNonNull(values, name));
        if (copy.isEmpty() || copy.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(name + " must contain nonblank values");
        }
        List<String> normalized = copy.stream().distinct().sorted().toList();
        if (!copy.equals(normalized)) {
            throw new IllegalArgumentException(name + " must be sorted and distinct");
        }
        return copy;
    }

    private static void requireDenseSortedItems(List<CatalogItem> items) {
        String previous = null;
        for (int index = 0; index < items.size(); index++) {
            CatalogItem item = items.get(index);
            if (item.index() != index || (previous != null && previous.compareTo(item.id()) >= 0)) {
                throw new IllegalArgumentException("catalog items must be dense and ID-sorted");
            }
            previous = item.id();
        }
    }

    private static void requireDenseSortedExcluded(List<ExcludedItem> items) {
        String previous = null;
        for (int index = 0; index < items.size(); index++) {
            ExcludedItem item = items.get(index);
            if (item.index() != index || (previous != null && previous.compareTo(item.id()) >= 0)) {
                throw new IllegalArgumentException("excluded items must be dense and ID-sorted");
            }
            previous = item.id();
        }
    }

    public enum Disposition {
        AUTO_FLOOR_UP,
        DEFERRED_ROUTE
    }

    public enum EffectPolicy {
        LOCAL_TARGET_AND_NEIGHBORS,
        DEFERRED_WATER_SURFACE,
        DEFERRED_UNBOUNDED_STACK,
        DEFERRED_UNKNOWN_EFFECT
    }

    public record CatalogItem(
            int index,
            String id,
            List<String> categories,
            Disposition disposition,
            EffectPolicy effectPolicy) {
        public CatalogItem {
            if (index < 0) {
                throw new IllegalArgumentException("item index must be nonnegative");
            }
            id = requireText(id, "id");
            categories = requireSortedDistinct(categories, "categories");
            disposition = Objects.requireNonNull(disposition, "disposition");
            effectPolicy = Objects.requireNonNull(effectPolicy, "effectPolicy");
            if ((effectPolicy == EffectPolicy.LOCAL_TARGET_AND_NEIGHBORS)
                    != (disposition == Disposition.AUTO_FLOOR_UP)) {
                throw new IllegalArgumentException("disposition must agree with effect policy");
            }
        }
    }

    public record ExcludedItem(
            int index,
            String id,
            String itemKind,
            String reason,
            String route) {
        public ExcludedItem {
            if (index < 0) {
                throw new IllegalArgumentException("excluded index must be nonnegative");
            }
            id = requireText(id, "id");
            itemKind = requireText(itemKind, "itemKind");
            reason = requireText(reason, "reason");
            route = requireText(route, "route");
        }
    }

    public record Topology(int index, String id, String recipe, boolean control) {
        public Topology {
            if (index < 0) {
                throw new IllegalArgumentException("topology index must be nonnegative");
            }
            id = requireText(id, "id");
            recipe = requireText(recipe, "recipe");
            if (control == id.startsWith("stack:")) {
                throw new IllegalArgumentException("topology kind and ID disagree");
            }
            if (!control && !recipe.matches("[SB]{1,5}")) {
                throw new IllegalArgumentException("stack topology recipe is invalid");
            }
        }
    }

    public record Snapshot(
            String schema,
            String catalogHash,
            List<CatalogItem> items,
            List<ExcludedItem> excludedItems,
            List<Topology> topologies,
            long totalCases,
            int pageCount) {
        public Snapshot {
            if (!SCHEMA.equals(schema)) {
                throw new IllegalArgumentException("unexpected cases catalog schema");
            }
            if (catalogHash == null || !catalogHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("catalog hash must be lowercase SHA-256");
            }
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            excludedItems = List.copyOf(Objects.requireNonNull(excludedItems, "excludedItems"));
            topologies = List.copyOf(Objects.requireNonNull(topologies, "topologies"));
            requireDenseSortedItems(items);
            requireDenseSortedExcluded(excludedItems);
            if (items.isEmpty()
                    || topologies.size() != TOPOLOGY_COUNT
                    || new HashSet<>(topologies).size() != TOPOLOGY_COUNT) {
                throw new IllegalArgumentException("catalog requires items and 64 unique topologies");
            }
            for (int index = 0; index < topologies.size(); index++) {
                if (topologies.get(index).index() != index) {
                    throw new IllegalArgumentException("topology indexes must be dense");
                }
            }
            long expectedCases = Math.multiplyExact((long) items.size(), TOPOLOGY_COUNT);
            int expectedPages = Math.multiplyExact(
                    (items.size() + PAGE_GRID_SIDE - 1) / PAGE_GRID_SIDE,
                    TOPOLOGY_GROUPS);
            if (totalCases != expectedCases || pageCount != expectedPages) {
                throw new IllegalArgumentException("catalog totals disagree with its contents");
            }
        }
    }

    public record CaseDefinition(
            long index,
            String id,
            CatalogItem item,
            Topology topology,
            String placementMode) {
        public CaseDefinition {
            item = Objects.requireNonNull(item, "item");
            topology = Objects.requireNonNull(topology, "topology");
            placementMode = requireText(placementMode, "placementMode");
            long expectedIndex = Math.addExact(
                    Math.multiplyExact((long) item.index(), TOPOLOGY_COUNT), topology.index());
            String expectedId = caseId(item.id(), topology.id(), placementMode);
            if (index != expectedIndex || !expectedId.equals(id)) {
                throw new IllegalArgumentException("case identity disagrees with semantic inputs");
            }
        }
    }

    public record CasePage(
            int page,
            int pageCount,
            int itemGroup,
            int topologyGroup,
            long firstCaseIndex,
            long lastCaseIndex,
            List<CaseDefinition> cases) {
        public CasePage {
            cases = List.copyOf(Objects.requireNonNull(cases, "cases"));
            if (page < 1 || pageCount < 1 || page > pageCount
                    || itemGroup != (page - 1) / TOPOLOGY_GROUPS
                    || topologyGroup != (page - 1) % TOPOLOGY_GROUPS
                    || cases.isEmpty() || cases.size() > PAGE_SIZE
                    || firstCaseIndex != cases.get(0).index()
                    || lastCaseIndex != cases.get(cases.size() - 1).index()) {
                throw new IllegalArgumentException("invalid cases page");
            }
        }
    }

    private record RegistryItem(ResourceLocation id, Item item) {
    }

    private record Exclusion(String itemKind, String route) {
    }
}
