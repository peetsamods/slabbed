package com.slabbed.command;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.HangingSignBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * RIG-2's deterministic runtime item x topology authority.
 *
 * <p>The scope is deliberately mechanical and exact: every runtime {@link BlockItem} is present in
 * the base {@code FLOOR_UP} case space, while every non-BlockItem is still recorded with an explicit
 * reason and a dedicated-route disposition. This class never calls an item and never mutates a world.
 * The command layer may defer a case whose route is not safely bounded, but it may not make the item
 * disappear from the catalog.
 */
public final class SlabRigCaseCatalog {

    public static final String SCHEMA = "slabbed-rig-case-catalog-v1";
    public static final String EXECUTION_CONTRACT = "rig2-floor-up-v1;board=4x4;tile=8;"
            + "effect=local-neighbors-v1;guard=radius2-y0..2;topology=lowered-seed-and-shape-seams-v1;"
            + "post-action-topology=state-live-store-marker-v1;"
            + "config=frozen-dy-evidence-v1;"
            + "resume=immediate-predecessor-inductive-v2;closure=full-chain-audit-v2;"
            + "runtime=content-sha256-v1;world=persistent-uuid-plus-path-v1;"
            + "subject-anomaly=consumed-or-transformed-absent-subject-red-v2";
    public static final int PAGE_SIZE = 16;
    public static final int PAGE_GRID_SIDE = 4;
    private static final int TOPOLOGY_GROUPS = 16;

    private SlabRigCaseCatalog() {
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

    private static final Set<Class<?>> KNOWN_LOCAL_BLOCK_ITEM_CLASSES = Set.of(
            BlockItem.class, BannerItem.class, BedItem.class, DoubleHighBlockItem.class,
            GameMasterBlockItem.class, HangingSignItem.class, PlayerHeadItem.class,
            SignItem.class, SolidBucketItem.class, StandingAndWallBlockItem.class);

    public record CatalogItem(int index, String id, List<String> categories,
                              Disposition disposition, EffectPolicy effectPolicy) {
        public CatalogItem {
            categories = List.copyOf(categories);
        }
    }

    public record ExcludedItem(int index, String id, String itemKind, String reason, String route) {
    }

    public record Topology(int index, String id, String recipe, boolean control) {
    }

    public record Snapshot(String schema, String catalogHash, List<CatalogItem> items,
                           List<ExcludedItem> excludedItems, List<Topology> topologies,
                           long totalCases, int pageCount) {
        public Snapshot {
            items = List.copyOf(items);
            excludedItems = List.copyOf(excludedItems);
            topologies = List.copyOf(topologies);
        }
    }

    public record CaseDefinition(long index, String id, CatalogItem item, Topology topology,
                                 String placementMode) {
    }

    /** One 4-item x 4-topology board. Global case indexes are explicit and not assumed contiguous. */
    public record CasePage(int page, int pageCount, int itemGroup, int topologyGroup,
                           long firstCaseIndex, long lastCaseIndex, List<CaseDefinition> cases) {
        public CasePage {
            cases = List.copyOf(cases);
        }
    }

    /** Rebuild from the live built-in item registry so the snapshot itself proves the runtime scope. */
    public static Snapshot snapshot() {
        List<Item> runtime = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            runtime.add(item);
        }
        runtime.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));

        List<CatalogItem> included = new ArrayList<>();
        List<ExcludedItem> excluded = new ArrayList<>();
        for (Item item : runtime) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (item instanceof BlockItem blockItem) {
                EffectPolicy effectPolicy = effectPolicy(item, id);
                Disposition disposition = effectPolicy == EffectPolicy.LOCAL_TARGET_AND_NEIGHBORS
                        ? Disposition.AUTO_FLOOR_UP : Disposition.DEFERRED_ROUTE;
                included.add(new CatalogItem(included.size(), id, categories(blockItem),
                        disposition, effectPolicy));
            } else {
                excluded.add(new ExcludedItem(excluded.size(), id, excludedKind(item),
                        "not_block_item",
                        excludedRoute(item)));
            }
        }

        List<Topology> topologies = buildTopologies();
        long totalCases = Math.multiplyExact((long) included.size(), (long) topologies.size());
        int itemGroups = (included.size() + PAGE_GRID_SIDE - 1) / PAGE_GRID_SIDE;
        int pageCount = Math.multiplyExact(itemGroups, TOPOLOGY_GROUPS);
        return new Snapshot(SCHEMA, catalogHash(SCHEMA, included, excluded, topologies),
                included, excluded, topologies,
                totalCases, pageCount);
    }

    /** Public pure hash seam so tests/consumers can prove tags, dispositions, exclusions, and schema. */
    public static String catalogHash(String schema, List<CatalogItem> included,
                                     List<ExcludedItem> excluded, List<Topology> topologies) {
        String canonical = "schema\t" + schema + "\n"
                + "page_geometry\t4_items_x_4_topologies\n"
                + "execution_contract\t" + EXECUTION_CONTRACT + "\n"
                + canonicalCatalogBody(included, excluded, topologies);
        return sha256(canonical);
    }

    public static CaseDefinition caseAt(Snapshot snapshot, long index) {
        if (index < 0 || index >= snapshot.totalCases()) {
            throw new IllegalArgumentException("case index must be 0.." + (snapshot.totalCases() - 1));
        }
        int topologyCount = snapshot.topologies().size();
        int itemIndex = Math.toIntExact(index / topologyCount);
        int topologyIndex = (int) (index % topologyCount);
        CatalogItem item = snapshot.items().get(itemIndex);
        Topology topology = snapshot.topologies().get(topologyIndex);
        return new CaseDefinition(index, caseId(item.id(), topology.id(), "FLOOR_UP"),
                item, topology, "FLOOR_UP");
    }

    /** Stable semantic identity: display order/page/ordinal are deliberately absent. */
    public static String caseId(String itemId, String topologyId, String placementMode) {
        String identity = SCHEMA + "\0" + itemId + "\0" + topologyId + "\0" + placementMode;
        return "case-v1:sha256:" + sha256(identity);
    }

    /**
     * Visual pagination packs four consecutive items across four consecutive topologies. Page order
     * advances topology groups first, then item groups; this gives the maintainer a mixed object/structure grid
     * while case identity remains the canonical item-major cross product.
     */
    public static CasePage page(Snapshot snapshot, int page) {
        if (page < 1 || page > snapshot.pageCount()) {
            throw new IllegalArgumentException("page must be 1.." + snapshot.pageCount());
        }
        int zero = page - 1;
        int itemGroup = zero / TOPOLOGY_GROUPS;
        int topologyGroup = zero % TOPOLOGY_GROUPS;
        int itemFrom = itemGroup * PAGE_GRID_SIDE;
        int itemTo = Math.min(itemFrom + PAGE_GRID_SIDE, snapshot.items().size());
        int topologyFrom = topologyGroup * PAGE_GRID_SIDE;
        int topologyTo = Math.min(topologyFrom + PAGE_GRID_SIDE, snapshot.topologies().size());
        List<CaseDefinition> cases = new ArrayList<>();
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        for (int itemIndex = itemFrom; itemIndex < itemTo; itemIndex++) {
            for (int topologyIndex = topologyFrom; topologyIndex < topologyTo; topologyIndex++) {
                long global = Math.addExact(Math.multiplyExact((long) itemIndex,
                        snapshot.topologies().size()), topologyIndex);
                CaseDefinition definition = caseAt(snapshot, global);
                cases.add(definition);
                first = Math.min(first, global);
                last = Math.max(last, global);
            }
        }
        return new CasePage(page, snapshot.pageCount(), itemGroup, topologyGroup,
                first, last, cases);
    }

    /** Canonical UTF-8/LF catalog artifact; its content hash is computed over the data rows below. */
    public static String catalogTsv(Snapshot snapshot) {
        StringBuilder out = new StringBuilder();
        out.append("# schema\t").append(snapshot.schema()).append('\n');
        out.append("# catalog_hash\t").append(snapshot.catalogHash()).append('\n');
        out.append("# included_block_items\t").append(snapshot.items().size()).append('\n');
        out.append("# excluded_non_block_items\t").append(snapshot.excludedItems().size()).append('\n');
        out.append("# topologies\t").append(snapshot.topologies().size()).append('\n');
        out.append("# total_cases\t").append(snapshot.totalCases()).append('\n');
        out.append("# page_geometry\t4_items_x_4_topologies\n");
        out.append("# page_count\t").append(snapshot.pageCount()).append('\n');
        out.append("record_type\tindex\tid\tcategories_or_reason\troute_or_recipe\n");
        out.append(canonicalCatalogBody(snapshot.items(), snapshot.excludedItems(), snapshot.topologies()));
        return out.toString();
    }

    private static String canonicalCatalogBody(List<CatalogItem> included, List<ExcludedItem> excluded,
                                               List<Topology> topologies) {
        StringBuilder out = new StringBuilder();
        for (CatalogItem item : included) {
            out.append("item\t").append(item.index()).append('\t').append(item.id()).append('\t')
                    .append(String.join(",", item.categories())).append('\t')
                    .append("disposition=").append(item.disposition())
                    .append(",effect=").append(item.effectPolicy()).append('\n');
        }
        for (ExcludedItem item : excluded) {
            out.append("excluded\t").append(item.index()).append('\t').append(item.id()).append('\t')
                    .append("kind:item:").append(item.itemKind()).append(",reason=")
                    .append(item.reason()).append('\t').append(item.route()).append('\n');
        }
        for (Topology topology : topologies) {
            out.append("topology\t").append(topology.index()).append('\t').append(topology.id()).append('\t')
                    .append(topology.control() ? "control" : "stack").append('\t')
                    .append(topology.recipe()).append('\n');
        }
        return out.toString();
    }

    private static List<Topology> buildTopologies() {
        List<Topology> result = new ArrayList<>();
        result.add(new Topology(0, "control:ground_full_block", "GROUND", true));
        result.add(new Topology(1, "control:single_slab", "SINGLE_SLAB", true));
        for (int length = 1; length <= 5; length++) {
            for (int bits = 0; bits < (1 << length); bits++) {
                StringBuilder word = new StringBuilder(length);
                for (int shift = length - 1; shift >= 0; shift--) {
                    word.append((bits & (1 << shift)) == 0 ? 'S' : 'B');
                }
                result.add(new Topology(result.size(), "stack:" + word, word.toString(), false));
            }
        }
        return List.copyOf(result);
    }

    private static List<String> categories(BlockItem item) {
        TreeSet<String> tags = new TreeSet<>();
        tags.add("universe:block_item");
        tags.add("namespace:" + BuiltInRegistries.ITEM.getKey(item).getNamespace());
        tags.add("route:" + route(item));
        tags.add("kind:item:" + route(item));
        Block block = item.getBlock();
        BlockState state = block.defaultBlockState();
        VoxelShape shape;
        try {
            shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (shape.isEmpty()) {
                tags.add("shape:empty_or_contextual");
            } else if (Block.isShapeFullBlock(shape)) {
                tags.add("shape:full_cube");
            } else {
                tags.add("shape:partial");
            }
        } catch (RuntimeException ignored) {
            tags.add("shape:dynamic_or_contextual");
        }

        Set<String> families = new LinkedHashSet<>();
        addFamily(families, block instanceof SlabBlock, "slab");
        addFamily(families, block instanceof StairBlock, "stair");
        addFamily(families, block instanceof DoorBlock, "door");
        addFamily(families, block instanceof TrapDoorBlock, "trapdoor");
        addFamily(families, block instanceof BedBlock, "bed");
        addFamily(families, block instanceof FenceBlock, "fence");
        addFamily(families, block instanceof FenceGateBlock, "fence_gate");
        addFamily(families, block instanceof WallBlock, "wall");
        addFamily(families, block instanceof IronBarsBlock, "pane_or_bars");
        addFamily(families, block instanceof CarpetBlock, "thin_layer");
        addFamily(families, block instanceof BasePressurePlateBlock, "surface_component");
        addFamily(families, block instanceof BaseRailBlock, "rail");
        addFamily(families, block instanceof BaseTorchBlock, "torch");
        addFamily(families, block instanceof LanternBlock, "lantern");
        addFamily(families, block instanceof ChainBlock, "chain");
        addFamily(families, block instanceof ChainBlock || block instanceof LanternBlock
                || block instanceof HangingSignBlock, "hanging_capable");
        addFamily(families, block instanceof SignBlock, "sign");
        addFamily(families, block instanceof HangingSignBlock, "hanging_sign");
        addFamily(families, block instanceof AbstractBannerBlock, "banner");
        addFamily(families, block instanceof AbstractSkullBlock, "skull_or_head");
        addFamily(families, block instanceof FlowerPotBlock, "flower_pot");
        addFamily(families, block instanceof AbstractCandleBlock, "candle");
        addFamily(families, block instanceof ButtonBlock, "button");
        addFamily(families, block instanceof LadderBlock, "wall_attached");
        addFamily(families, block instanceof BushBlock, "plant");
        addFamily(families, block instanceof FallingBlock, "falling_block");
        addFamily(families, block instanceof DiodeBlock || block instanceof RedStoneWireBlock
                || block instanceof BasePressurePlateBlock || block instanceof ButtonBlock
                || block instanceof TripWireBlock || block instanceof TripWireHookBlock, "redstone");
        addFamily(families, block instanceof EntityBlock, "block_entity");
        if (families.isEmpty()) {
            if (tags.contains("shape:full_cube")) {
                families.add("ordinary_full_cube");
            } else if (tags.contains("shape:partial")) {
                families.add("ordinary_partial_shape");
            } else {
                families.add("ordinary_contextual_shape");
            }
        }
        for (String family : families) {
            tags.add("family:" + family);
        }
        return List.copyOf(tags);
    }

    private static void addFamily(Set<String> families, boolean condition, String family) {
        if (condition) {
            families.add(family);
        }
    }

    private static String route(Item item) {
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

    private static EffectPolicy effectPolicy(Item item, String id) {
        if (!id.startsWith("minecraft:")) {
            return EffectPolicy.DEFERRED_UNKNOWN_EFFECT;
        }
        if (item instanceof PlaceOnWaterBlockItem) {
            return EffectPolicy.DEFERRED_WATER_SURFACE;
        }
        if (item instanceof ScaffoldingBlockItem) {
            return EffectPolicy.DEFERRED_UNBOUNDED_STACK;
        }
        return KNOWN_LOCAL_BLOCK_ITEM_CLASSES.contains(item.getClass())
                ? EffectPolicy.LOCAL_TARGET_AND_NEIGHBORS
                : EffectPolicy.DEFERRED_UNKNOWN_EFFECT;
    }

    private static String excludedRoute(Item item) {
        if (item instanceof HangingEntityItem) {
            return "dedicated_hanging_entity";
        }
        if (item instanceof ArmorStandItem || item instanceof BoatItem || item instanceof MinecartItem
                || item instanceof EndCrystalItem) {
            return "dedicated_entity_placement";
        }
        if (item instanceof BucketItem) {
            return "dedicated_fluid_container";
        }
        return "non_block_interaction_or_inventory";
    }

    /** Mapping-stable semantic kind; runtime Java class names are not artifact identity. */
    private static String excludedKind(Item item) {
        if (item instanceof HangingEntityItem) {
            return "hanging_entity_item";
        }
        if (item instanceof ArmorStandItem) {
            return "armor_stand_item";
        }
        if (item instanceof BoatItem) {
            return "boat_item";
        }
        if (item instanceof MinecartItem) {
            return "minecart_item";
        }
        if (item instanceof EndCrystalItem) {
            return "end_crystal_item";
        }
        if (item instanceof BucketItem) {
            return "fluid_container_item";
        }
        return "inventory_or_interaction_item";
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
