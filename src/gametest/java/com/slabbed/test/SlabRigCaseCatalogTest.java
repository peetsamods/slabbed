package com.slabbed.test;

import com.google.gson.JsonParser;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.command.SlabRigCaseCatalog;
import com.slabbed.command.SlabRigCaseArtifacts;
import com.slabbed.command.SlabRigCasePageManifest;
import com.slabbed.util.BuildStamp;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Contract gates for RIG-2's runtime-derived item x topology case catalog. */
public final class SlabRigCaseCatalogTest {

    private static final class RuntimeDigestFixture {
        private static final class NestedBytecode {
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigDevRuntimeDigestRequiresAndHashesResources(GameTestHelper h) {
        Path parent = Path.of(System.getProperty("user.dir", "."), "build", "tmp",
                "rig2-runtime-digest-contract");
        Path root = null;
        try {
            java.nio.file.Files.createDirectories(parent);
            root = java.nio.file.Files.createTempDirectory(parent, "runtime-");
            Path classes = root.resolve("classes");
            Path resources = root.resolve("resources");
            java.nio.file.Files.createDirectories(classes.resolve("com/slabbed"));
            java.nio.file.Files.createDirectories(resources);
            Path classFixture = classes.resolve("com/slabbed/Fixture.class");
            java.nio.file.Files.writeString(classFixture, "class-byte-fixture-v1", StandardCharsets.UTF_8);
            Path mixins = resources.resolve("slabbed.mixins.json");
            java.nio.file.Files.writeString(mixins, "{\"required\":true}\n", StandardCharsets.UTF_8);
            String first = BuildStamp.devRuntimeContentSha256(classes, resources);

            Path mirrorClasses = root.resolve("different-absolute-root/classes");
            Path mirrorResources = root.resolve("different-absolute-root/resources");
            java.nio.file.Files.createDirectories(mirrorClasses.resolve("com/slabbed"));
            java.nio.file.Files.createDirectories(mirrorResources);
            java.nio.file.Files.writeString(mirrorClasses.resolve("com/slabbed/Fixture.class"),
                    "class-byte-fixture-v1", StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(mirrorResources.resolve("slabbed.mixins.json"),
                    "{\"required\":true}\n", StandardCharsets.UTF_8);
            String relocated = BuildStamp.devRuntimeContentSha256(mirrorClasses, mirrorResources);
            if (!first.equals(relocated)) {
                throw h.assertionException(
                        "dev runtime identity must be independent of its absolute classes/resources paths");
            }

            java.nio.file.Files.writeString(mixins, "{\"required\":false}\n", StandardCharsets.UTF_8);
            String resourceChanged = BuildStamp.devRuntimeContentSha256(classes, resources);
            if (!first.matches("[0-9a-f]{64}")
                    || !resourceChanged.matches("[0-9a-f]{64}")
                    || first.equals(resourceChanged)) {
                throw h.assertionException(
                        "dev runtime identity must include exact resource bytes, not classes alone");
            }
            java.nio.file.Files.writeString(mixins, "{\"required\":true}\n", StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(classFixture, "class-byte-fixture-v2", StandardCharsets.UTF_8);
            String classChanged = BuildStamp.devRuntimeContentSha256(classes, resources);
            if (!classChanged.matches("[0-9a-f]{64}") || first.equals(classChanged)) {
                throw h.assertionException(
                        "dev runtime identity must include exact main class bytes");
            }
            if (!"unavailable".equals(BuildStamp.devRuntimeContentSha256(
                    classes, root.resolve("missing-resources")))) {
                throw h.assertionException(
                        "dev runtime identity must fail closed when its resource root is missing");
            }
            Path emptyResources = root.resolve("empty-resources");
            java.nio.file.Files.createDirectories(emptyResources);
            if (!"unavailable".equals(BuildStamp.devRuntimeContentSha256(classes, emptyResources))) {
                throw h.assertionException(
                        "dev runtime identity must fail closed when its resource root is empty");
            }
            Path wrongResources = root.resolve("nonempty-without-unique-marker");
            java.nio.file.Files.createDirectories(wrongResources);
            java.nio.file.Files.writeString(wrongResources.resolve("fabric.mod.json"),
                    "{}\n", StandardCharsets.UTF_8);
            if (!"unavailable".equals(BuildStamp.devRuntimeContentSha256(classes, wrongResources))) {
                throw h.assertionException(
                        "dev runtime identity must require Slabbed's unique resource marker");
            }
        } catch (java.io.IOException e) {
            throw h.assertionException("dev runtime digest fixture failed: " + e.getMessage());
        } finally {
            if (root != null) {
                try (var paths = java.nio.file.Files.walk(root)) {
                    for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                        java.nio.file.Files.deleteIfExists(path);
                    }
                } catch (java.io.IOException e) {
                    throw h.assertionException("dev runtime digest fixture cleanup failed: " + e.getMessage());
                }
            }
        }
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigCaseCatalogAccountsForEveryRuntimeItem(GameTestHelper h) {
        if (!BuildStamp.hasExactRuntimeContent()
                || !BuildStamp.RUNTIME_CONTENT_SHA256.matches("[0-9a-f]{64}")) {
            throw h.assertionException(
                    "RIG-2 requires an exact runtime jar/dev-tree digest, got "
                            + BuildStamp.RUNTIME_CONTENT_SHA256);
        }
        String recursiveDigest = BuildStamp.extendRuntimeContentSha256(RuntimeDigestFixture.class);
        String explicitNestedDigest = BuildStamp.extendRuntimeContentSha256(
                RuntimeDigestFixture.class, RuntimeDigestFixture.NestedBytecode.class);
        if (!recursiveDigest.matches("[0-9a-f]{64}")
                || !recursiveDigest.equals(explicitNestedDigest)
                || !"unavailable".equals(BuildStamp.extendRuntimeContentSha256())) {
            throw h.assertionException(
                    "exact implementation hashing must require classes and recursively include nested bytes");
        }
        SlabRigCaseCatalog.Snapshot first = SlabRigCaseCatalog.snapshot();
        SlabRigCaseCatalog.Snapshot second = SlabRigCaseCatalog.snapshot();
        if (!first.equals(second)) {
            throw h.assertionException("runtime catalog must be byte/order stable across repeated snapshots");
        }
        if (!first.catalogHash().matches("[0-9a-f]{64}")) {
            throw h.assertionException("catalog hash must be lowercase SHA-256: " + first.catalogHash());
        }
        if (!first.catalogHash().equals(SlabRigCaseCatalog.catalogHash(first.schema(), first.items(),
                first.excludedItems(), first.topologies()))) {
            throw h.assertionException("snapshot hash must cover its exact canonical components");
        }
        List<SlabRigCaseCatalog.CatalogItem> tagMutation = new ArrayList<>(first.items());
        SlabRigCaseCatalog.CatalogItem original = tagMutation.get(0);
        List<String> changedTags = new ArrayList<>(original.categories());
        changedTags.remove(changedTags.size() - 1);
        tagMutation.set(0, new SlabRigCaseCatalog.CatalogItem(original.index(), original.id(), changedTags,
                original.disposition(), original.effectPolicy()));
        if (first.catalogHash().equals(SlabRigCaseCatalog.catalogHash(first.schema(), tagMutation,
                first.excludedItems(), first.topologies()))
                || first.catalogHash().equals(SlabRigCaseCatalog.catalogHash(first.schema() + "-changed",
                first.items(), first.excludedItems(), first.topologies()))) {
            throw h.assertionException("catalog hash must change when a tag or schema byte changes");
        }
        List<SlabRigCaseCatalog.ExcludedItem> exclusionMutation = new ArrayList<>(first.excludedItems());
        SlabRigCaseCatalog.ExcludedItem excludedOriginal = exclusionMutation.get(0);
        exclusionMutation.set(0, new SlabRigCaseCatalog.ExcludedItem(excludedOriginal.index(),
                excludedOriginal.id(), excludedOriginal.itemKind(),
                excludedOriginal.reason() + "-changed", excludedOriginal.route()));
        List<SlabRigCaseCatalog.Topology> topologyMutation = new ArrayList<>(first.topologies());
        SlabRigCaseCatalog.Topology topologyOriginal = topologyMutation.get(0);
        topologyMutation.set(0, new SlabRigCaseCatalog.Topology(topologyOriginal.index(),
                topologyOriginal.id() + "-changed", topologyOriginal.recipe(), topologyOriginal.control()));
        if (first.catalogHash().equals(SlabRigCaseCatalog.catalogHash(first.schema(), first.items(),
                exclusionMutation, first.topologies()))
                || first.catalogHash().equals(SlabRigCaseCatalog.catalogHash(first.schema(), first.items(),
                first.excludedItems(), topologyMutation))) {
            throw h.assertionException("catalog hash must change with exclusion reasons or topology identity");
        }

        Map<String, SlabRigCaseCatalog.CatalogItem> included = first.items().stream()
                .collect(Collectors.toMap(SlabRigCaseCatalog.CatalogItem::id, Function.identity()));
        Map<String, SlabRigCaseCatalog.ExcludedItem> excluded = first.excludedItems().stream()
                .collect(Collectors.toMap(SlabRigCaseCatalog.ExcludedItem::id, Function.identity()));
        if (included.size() + excluded.size() != BuiltInRegistries.ITEM.size()) {
            throw h.assertionException("included + explicitly excluded must account for the exact runtime registry: "
                    + included.size() + "+" + excluded.size() + " != " + BuiltInRegistries.ITEM.size());
        }

        List<String> runtimeIds = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            runtimeIds.add(id);
            boolean isBlockItem = item instanceof BlockItem;
            if (isBlockItem != included.containsKey(id)) {
                throw h.assertionException("BlockItem inclusion authority disagrees for " + id);
            }
            if (isBlockItem == excluded.containsKey(id)) {
                throw h.assertionException("registry item must appear in exactly one partition: " + id);
            }
        }
        runtimeIds.sort(String::compareTo);

        List<String> partitionIds = new ArrayList<>();
        partitionIds.addAll(included.keySet());
        partitionIds.addAll(excluded.keySet());
        partitionIds.sort(String::compareTo);
        if (!runtimeIds.equals(partitionIds)) {
            throw h.assertionException("catalog partitions must round-trip the exact sorted runtime item ids");
        }

        List<String> orderedIncluded = first.items().stream()
                .map(SlabRigCaseCatalog.CatalogItem::id).toList();
        List<String> sortedIncluded = orderedIncluded.stream().sorted().toList();
        if (!orderedIncluded.equals(sortedIncluded)) {
            throw h.assertionException("included runtime BlockItems must be registry-id sorted");
        }
        for (int index = 0; index < first.items().size(); index++) {
            SlabRigCaseCatalog.CatalogItem entry = first.items().get(index);
            if (entry.index() != index) {
                throw h.assertionException("item index must be dense and stable at " + entry);
            }
            List<String> tags = entry.categories();
            if (tags.isEmpty() || !tags.equals(tags.stream().sorted().toList())
                    || new HashSet<>(tags).size() != tags.size()) {
                throw h.assertionException("categories must be nonempty, unique, sorted: " + entry);
            }
            if (tags.stream().anyMatch(tag -> tag.contains("misc") || tag.contains("unclassified"))) {
                throw h.assertionException("silent catch-all categories are forbidden: " + entry);
            }
            long shapeTags = tags.stream().filter(tag -> tag.startsWith("shape:")).count();
            long routeTags = tags.stream().filter(tag -> tag.startsWith("route:")).count();
            long familyTags = tags.stream().filter(tag -> tag.startsWith("family:")).count();
            if (shapeTags != 1 || routeTags != 1 || familyTags < 1
                    || !tags.contains("universe:block_item")) {
                throw h.assertionException(
                        "every included item needs one shape, one route, family tags, and universe tag: " + entry);
            }
            Set<String> ordinaryFamilies = Set.of("family:ordinary_full_cube",
                    "family:ordinary_partial_shape", "family:ordinary_contextual_shape");
            if (tags.stream().filter(tag -> tag.startsWith("family:ordinary_")).anyMatch(
                    tag -> !ordinaryFamilies.contains(tag))) {
                throw h.assertionException("fallback families must stay in the three named ordinary buckets: "
                        + entry);
            }
            if (tags.stream().anyMatch(tag -> tag.startsWith("class:"))) {
                throw h.assertionException("mapping-dependent runtime class names cannot enter catalog identity: "
                        + entry);
            }
        }
        for (SlabRigCaseCatalog.ExcludedItem entry : first.excludedItems()) {
            if (entry.itemKind().isBlank() || entry.reason().isBlank() || entry.route().isBlank()
                    || entry.itemKind().endsWith("Item")) {
                throw h.assertionException(
                        "every non-BlockItem exclusion needs a mapping-stable kind, reason, and route: " + entry);
            }
        }
        long unknownEffects = first.items().stream()
                .filter(item -> item.effectPolicy() == SlabRigCaseCatalog.EffectPolicy.DEFERRED_UNKNOWN_EFFECT)
                .count();
        if (unknownEffects != 0) {
            throw h.assertionException("current vanilla runtime introduced unreviewed BlockItem effects: "
                    + first.items().stream()
                    .filter(item -> item.effectPolicy() == SlabRigCaseCatalog.EffectPolicy.DEFERRED_UNKNOWN_EFFECT)
                    .map(SlabRigCaseCatalog.CatalogItem::id).toList());
        }

        assertTags(h, included, "minecraft:stone", "family:ordinary_full_cube", "shape:full_cube");
        assertTags(h, included, "minecraft:oak_slab", "family:slab");
        assertTags(h, included, "minecraft:oak_door", "family:door", "route:double_high_block_item");
        assertTags(h, included, "minecraft:lantern", "family:hanging_capable");
        assertTags(h, included, "minecraft:iron_chain", "family:chain", "family:hanging_capable");
        assertTags(h, included, "minecraft:powder_snow_bucket", "route:solid_bucket_block_item");
        assertTags(h, included, "minecraft:command_block", "route:game_master_block_item");
        assertExcludedRoute(h, excluded, "minecraft:painting", "dedicated_hanging_entity");
        assertExcludedRoute(h, excluded, "minecraft:armor_stand", "dedicated_entity_placement");
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigCaseCatalogTopologyAndPagingContract(GameTestHelper h) {
        SlabRigCaseCatalog.Snapshot snapshot = SlabRigCaseCatalog.snapshot();
        List<SlabRigCaseCatalog.Topology> topologies = snapshot.topologies();
        if (topologies.size() != 64
                || !"control:ground_full_block".equals(topologies.get(0).id())
                || !"control:single_slab".equals(topologies.get(1).id())) {
            throw h.assertionException("topology catalog must start with two explicit controls and total 64");
        }

        List<String> expectedWords = new ArrayList<>();
        for (int length = 1; length <= 5; length++) {
            for (int bits = 0; bits < (1 << length); bits++) {
                StringBuilder word = new StringBuilder(length);
                for (int shift = length - 1; shift >= 0; shift--) {
                    word.append((bits & (1 << shift)) == 0 ? 'S' : 'B');
                }
                expectedWords.add(word.toString());
            }
        }
        List<String> actualWords = topologies.subList(2, topologies.size()).stream()
                .map(SlabRigCaseCatalog.Topology::recipe).toList();
        if (!expectedWords.equals(actualWords) || new HashSet<>(actualWords).size() != 62) {
            throw h.assertionException("topology words must be the exact 62 length-major S-before-B catalog");
        }
        for (int index = 0; index < topologies.size(); index++) {
            if (topologies.get(index).index() != index) {
                throw h.assertionException("topology indexes must be dense: " + topologies.get(index));
            }
        }

        long expectedTotal = Math.multiplyExact((long) snapshot.items().size(), 64L);
        if (snapshot.totalCases() != expectedTotal) {
            throw h.assertionException("case total must be exact item x topology cardinality");
        }
        int expectedPages = Math.multiplyExact(
                (snapshot.items().size() + SlabRigCaseCatalog.PAGE_GRID_SIDE - 1)
                        / SlabRigCaseCatalog.PAGE_GRID_SIDE,
                snapshot.topologies().size() / SlabRigCaseCatalog.PAGE_GRID_SIDE);
        if (snapshot.pageCount() != expectedPages) {
            throw h.assertionException("page count must exactly cover the lazy cross product");
        }

        Set<String> ids = new HashSet<>();
        String semantic = SlabRigCaseCatalog.caseId("minecraft:stone", "stack:SBSBS", "FLOOR_UP");
        if (!semantic.equals(SlabRigCaseCatalog.caseId(
                "minecraft:stone", "stack:SBSBS", "FLOOR_UP"))
                || semantic.equals(SlabRigCaseCatalog.caseId(
                "minecraft:dirt", "stack:SBSBS", "FLOOR_UP"))
                || semantic.equals(SlabRigCaseCatalog.caseId(
                "minecraft:stone", "stack:SBBS", "FLOOR_UP"))
                || semantic.equals(SlabRigCaseCatalog.caseId(
                "minecraft:stone", "stack:SBSBS", "SIDE_CLICK"))) {
            throw h.assertionException("semantic case IDs must exclude ordinal but cover item/topology/mode");
        }
        for (long index = 0; index < expectedTotal; index++) {
            SlabRigCaseCatalog.CaseDefinition c = SlabRigCaseCatalog.caseAt(snapshot, index);
            if (c.index() != index || !ids.add(c.id())) {
                throw h.assertionException("case indexes/ids must be one-to-one at " + index);
            }
            String expectedId = "case-v1:sha256:" + sha256(SlabRigCaseCatalog.SCHEMA + "\0"
                    + c.item().id() + "\0" + c.topology().id() + "\0FLOOR_UP");
            if (!expectedId.equals(c.id())) {
                throw h.assertionException("case identity must derive from item+topology+mode, not ordinal");
            }
            long expectedItem = index / 64;
            int expectedTopology = (int) (index % 64);
            if (c.item().index() != expectedItem || c.topology().index() != expectedTopology) {
                throw h.assertionException("case arithmetic must be item-major/topology-minor at " + index);
            }
        }

        SlabRigCaseCatalog.CasePage first = SlabRigCaseCatalog.page(snapshot, 1);
        SlabRigCaseCatalog.CasePage last = SlabRigCaseCatalog.page(snapshot, snapshot.pageCount());
        if (first.firstCaseIndex() != 0 || first.cases().size() != SlabRigCaseCatalog.PAGE_SIZE
                || last.lastCaseIndex() != expectedTotal - 1 || last.cases().isEmpty()) {
            throw h.assertionException("first/last page boundaries must cover every case exactly");
        }
        Set<String> paged = new HashSet<>();
        List<Long> flattened = new ArrayList<>();
        for (int page = 1; page <= snapshot.pageCount(); page++) {
            SlabRigCaseCatalog.CasePage board = SlabRigCaseCatalog.page(snapshot, page);
            if (board.cases().isEmpty() || board.cases().size() > SlabRigCaseCatalog.PAGE_SIZE) {
                throw h.assertionException("every visual page must contain 1..16 explicit cases: " + board);
            }
            for (SlabRigCaseCatalog.CaseDefinition c : board.cases()) {
                if (!paged.add(c.id())) {
                    throw h.assertionException("visual page packing duplicated case " + c.id());
                }
                flattened.add(c.index());
            }
        }
        if (!paged.equals(ids)) {
            throw h.assertionException("concatenated 4x4 visual boards must round-trip the full case set");
        }
        List<Long> expectedFlattened = new ArrayList<>();
        int itemGroups = (snapshot.items().size() + 3) / 4;
        for (int itemGroup = 0; itemGroup < itemGroups; itemGroup++) {
            for (int topologyGroup = 0; topologyGroup < 16; topologyGroup++) {
                for (int itemIndex = itemGroup * 4;
                     itemIndex < Math.min(itemGroup * 4 + 4, snapshot.items().size()); itemIndex++) {
                    for (int topologyIndex = topologyGroup * 4; topologyIndex < topologyGroup * 4 + 4;
                         topologyIndex++) {
                        expectedFlattened.add((long) itemIndex * 64L + topologyIndex);
                    }
                }
            }
        }
        if (!expectedFlattened.equals(flattened)) {
            throw h.assertionException("visual pages must preserve exact item-group/topology-group order");
        }
        expectInvalidPage(h, snapshot, 0);
        expectInvalidPage(h, snapshot, snapshot.pageCount() + 1);
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigCaseCatalogSerializationIsDeterministicAndComplete(GameTestHelper h) {
        SlabRigCaseCatalog.Snapshot snapshot = SlabRigCaseCatalog.snapshot();
        String first = SlabRigCaseCatalog.catalogTsv(snapshot);
        String second = SlabRigCaseCatalog.catalogTsv(SlabRigCaseCatalog.snapshot());
        if (!first.equals(second)) {
            throw h.assertionException("catalog TSV must be byte-identical for the same runtime registry");
        }
        for (String required : new String[]{
                "# schema\t", "# catalog_hash\t" + snapshot.catalogHash(),
                "# included_block_items\t" + snapshot.items().size(),
                "# excluded_non_block_items\t" + snapshot.excludedItems().size(),
                "# topologies\t64", "# total_cases\t" + snapshot.totalCases(),
                "record_type\tindex\tid\tcategories_or_reason\troute_or_recipe"}) {
            if (!first.contains(required)) {
                throw h.assertionException("catalog TSV missing required deterministic field: " + required);
            }
        }
        long dataRows = first.lines().filter(line -> line.startsWith("item\t")
                || line.startsWith("excluded\t") || line.startsWith("topology\t")).count();
        long expectedRows = (long) snapshot.items().size() + snapshot.excludedItems().size() + 64L;
        if (dataRows != expectedRows) {
            throw h.assertionException("serialized catalog must contain every item partition + topology row");
        }
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigCasePageArtifactsAndResumeCursorFailClosed(GameTestHelper h) {
        SlabRigCaseCatalog.Snapshot snapshot = SlabRigCaseCatalog.snapshot();
        SlabRigCaseCatalog.CasePage page = SlabRigCaseCatalog.page(snapshot, 1);
        List<SlabRigCasePageManifest.CaseAttempt> plannedAttempts = new ArrayList<>();
        List<SlabRigCasePageManifest.CaseAttempt> finalizedAttempts = new ArrayList<>();
        int local = 0;
        for (SlabRigCaseCatalog.CaseDefinition definition : page.cases()) {
            BlockPos base = new BlockPos((local % 4) * 8, 4, (local / 4) * 8);
            plannedAttempts.add(plannedAttempt(definition, base));
            finalizedAttempts.add(finalizedAttempt(definition, base, Double.NaN));
            local++;
        }
        Path root = Path.of(System.getProperty("user.dir", "."), "build", "tmp", "rig2-artifact-contract");
        String catalogPath = root.resolve("catalogs")
                .resolve("catalog-" + snapshot.catalogHash() + ".tsv").toString();
        String worldKey = "a".repeat(64);
        boolean frozenMode = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        try {
            SlabRigCaseArtifacts.WrittenArtifact catalog = SlabRigCaseArtifacts.writeCatalog(root, snapshot);
            SlabRigCasePageManifest.PageManifest planned = new SlabRigCasePageManifest.PageManifest(
                    "PLANNED", snapshot, page, worldKey, frozenMode,
                    "minecraft:overworld", "test-player",
                    new BlockPos(0, 4, 0), "south", catalogPath, "none",
                    "self:content-addressed-after-serialization", plannedAttempts);
            SlabRigCaseArtifacts.WrittenArtifact plannedArtifact =
                    SlabRigCaseArtifacts.writeManifest(root, planned);
            SlabRigCasePageManifest.PageManifest manifest = new SlabRigCasePageManifest.PageManifest(
                    "FINALIZED", snapshot, page, worldKey, frozenMode,
                    "minecraft:overworld", "test-player",
                    new BlockPos(0, 4, 0), "south", catalogPath, "none",
                    plannedArtifact.path().toString(), finalizedAttempts);
            String first = SlabRigCasePageManifest.canonicalJson(manifest);
            String second = SlabRigCasePageManifest.canonicalJson(manifest);
            if (!first.equals(second) || !first.contains("\"manifestId\": \"sha256:")
                    || !first.contains("\"playerProof\": \"ABSENT_PROXY_DIAGNOSTIC_ONLY\"")
                    || !first.contains("\"hangingCoverage\": \"OUT_OF_SCOPE_RIG2_V1_SEPARATE_PASS\"")
                    || !first.contains("\"runtimeContentSha256\": \""
                    + BuildStamp.RUNTIME_CONTENT_SHA256 + "\"")
                    || !first.contains("\"frozenDyEnabled\": " + frozenMode)
                    || !first.contains(SlabRigCasePageManifest.RESUME_CONTRACT)) {
                throw h.assertionException(
                        "case-page JSON must be deterministic and explicit about proof/resume limits");
            }
            try {
                var parsed = JsonParser.parseString(first).getAsJsonObject();
                if (!("sha256:" + SlabRigCasePageManifest.manifestHash(manifest))
                        .equals(parsed.get("manifestId").getAsString())) {
                    throw h.assertionException("embedded manifestId must equal the canonical body hash");
                }
            } catch (RuntimeException e) {
                throw h.assertionException("case-page artifact must be valid JSON: " + e.getMessage());
            }
            SlabRigCaseArtifacts.WrittenArtifact pageArtifact = SlabRigCaseArtifacts.writeManifest(root, manifest);
            if (!java.nio.file.Files.isRegularFile(catalog.path())
                    || !java.nio.file.Files.isRegularFile(plannedArtifact.path())
                    || !java.nio.file.Files.isRegularFile(pageArtifact.path())) {
                throw h.assertionException("content-addressed catalog/plan/final files must exist");
            }
            SlabRigCaseArtifacts.WrittenArtifact repeated = SlabRigCaseArtifacts.writeManifest(root, manifest);
            if (!pageArtifact.equals(repeated)
                    || !pageArtifact.path().getFileName().toString().contains(pageArtifact.contentId())) {
                throw h.assertionException("idempotent page write/path must preserve the manifest content id");
            }
            SlabRigCasePageManifest.PageManifest changedManifest = new SlabRigCasePageManifest.PageManifest(
                    "PARTIAL", snapshot, page, worldKey, frozenMode,
                    "minecraft:overworld", "test-player",
                    new BlockPos(0, 4, 0), "south", catalogPath, "none",
                    plannedArtifact.path().toString(), finalizedAttempts);
            if (pageArtifact.contentId().equals(SlabRigCasePageManifest.manifestHash(changedManifest))) {
                throw h.assertionException("page manifest id must cover status/content changes");
            }
            Path collisionRoot = root.resolve("collision-contract");
            Path collisionPath = collisionRoot.resolve("case-pages")
                    .resolve("case-page-" + pageArtifact.contentId() + ".json");
            java.nio.file.Files.createDirectories(collisionPath.getParent());
            java.nio.file.Files.writeString(collisionPath, "wrong-bytes", StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            try {
                SlabRigCaseArtifacts.writeManifest(collisionRoot, manifest);
                throw h.assertionException("content-address collision must fail closed");
            } catch (java.nio.file.FileAlreadyExistsException impossible) {
                throw h.assertionException("collision must be classified by artifact writer");
            } catch (java.io.IOException expectedCollision) {
                // expected
            }
            Path progress = root.resolve("progress-contract.tsv");
            SlabRigCaseArtifacts.Progress expected = new SlabRigCaseArtifacts.Progress(
                    worldKey, BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                    frozenMode, SlabRigCaseCatalog.EXECUTION_CONTRACT,
                    snapshot.catalogHash(), 2, snapshot.pageCount(), 1, pageArtifact.contentId());
            SlabRigCaseArtifacts.writeProgress(progress, expected);
            if (!expected.equals(SlabRigCaseArtifacts.readProgress(progress))) {
                throw h.assertionException("durable resume cursor must round-trip byte/exact values");
            }
            SlabRigCaseArtifacts.validateResumeEvidence(root, expected, snapshot, worldKey,
                    BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                    frozenMode, "test-player", "minecraft:overworld");
            String foreignRuntimeDigest = (BuildStamp.RUNTIME_CONTENT_SHA256.charAt(0) == '0' ? "1" : "0")
                    + BuildStamp.RUNTIME_CONTENT_SHA256.substring(1);
            try {
                SlabRigCaseArtifacts.validateResumeEvidence(root, expected, snapshot, worldKey,
                        BuildStamp.GIT_SHA, foreignRuntimeDigest,
                        frozenMode, "test-player", "minecraft:overworld");
                throw h.assertionException(
                        "same Git/catalog with different exact runtime digest must be rejected");
            } catch (java.io.IOException expectedForeignRuntime) {
                // expected fail-closed provenance result
            }
            SlabRigCaseArtifacts.Progress foreignModeCursor = new SlabRigCaseArtifacts.Progress(
                    worldKey, BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                    !frozenMode, SlabRigCaseCatalog.EXECUTION_CONTRACT,
                    snapshot.catalogHash(), 1, snapshot.pageCount(), 0, "none");
            try {
                SlabRigCaseArtifacts.validateResumeEvidence(root, foreignModeCursor, snapshot,
                        worldKey, BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                        frozenMode, "test-player", "minecraft:overworld");
                throw h.assertionException(
                        "same world/build/runtime/catalog cursor from another frozen mode must be rejected");
            } catch (java.io.IOException expectedForeignMode) {
                // expected fail-closed configuration provenance result
            }

            SlabRigCasePageManifest.PageManifest foreignModePlan =
                    new SlabRigCasePageManifest.PageManifest(
                            "PLANNED", snapshot, page, worldKey, !frozenMode,
                            "minecraft:overworld", "test-player", new BlockPos(0, 4, 0), "south",
                            catalogPath, "none", "self:content-addressed-after-serialization",
                            plannedAttempts);
            SlabRigCaseArtifacts.WrittenArtifact foreignModePlanArtifact =
                    SlabRigCaseArtifacts.writeManifest(root, foreignModePlan);
            SlabRigCasePageManifest.PageManifest foreignModeFinal =
                    new SlabRigCasePageManifest.PageManifest(
                            "FINALIZED", snapshot, page, worldKey, !frozenMode,
                            "minecraft:overworld", "test-player", new BlockPos(0, 4, 0), "south",
                            catalogPath, "none", foreignModePlanArtifact.path().toString(),
                            finalizedAttempts);
            SlabRigCaseArtifacts.WrittenArtifact foreignModeFinalArtifact =
                    SlabRigCaseArtifacts.writeManifest(root, foreignModeFinal);
            expectResumeInvalid(h, root, new SlabRigCaseArtifacts.Progress(
                            worldKey, BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                            frozenMode, SlabRigCaseCatalog.EXECUTION_CONTRACT, snapshot.catalogHash(),
                            2, snapshot.pageCount(), 1, foreignModeFinalArtifact.contentId()),
                    snapshot, "matching final+plan from another frozen mode");

            SlabRigCasePageManifest.PageManifest currentModeFinalWithForeignPlan =
                    new SlabRigCasePageManifest.PageManifest(
                            "FINALIZED", snapshot, page, worldKey, frozenMode,
                            "minecraft:overworld", "test-player", new BlockPos(0, 4, 0), "south",
                            catalogPath, "none", foreignModePlanArtifact.path().toString(),
                            finalizedAttempts);
            SlabRigCaseArtifacts.WrittenArtifact currentModeFinalWithForeignPlanArtifact =
                    SlabRigCaseArtifacts.writeManifest(root, currentModeFinalWithForeignPlan);
            expectResumeInvalid(h, root, new SlabRigCaseArtifacts.Progress(
                            worldKey, BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                            frozenMode, SlabRigCaseCatalog.EXECUTION_CONTRACT, snapshot.catalogHash(),
                            2, snapshot.pageCount(), 1,
                            currentModeFinalWithForeignPlanArtifact.contentId()),
                    snapshot, "linked plan from another frozen mode");

            List<SlabRigCasePageManifest.CaseAttempt> vanishedAttempts =
                    new ArrayList<>(finalizedAttempts);
            vanishedAttempts.set(0, vanishedAttempt(page.cases().get(0), plannedAttempts.get(0).tileBase()));
            SlabRigCasePageManifest.PageManifest falseGreenVanished =
                    new SlabRigCasePageManifest.PageManifest(
                            "FINALIZED", snapshot, page, worldKey, frozenMode,
                            "minecraft:overworld", "test-player",
                            new BlockPos(0, 4, 0), "south", catalogPath, "none",
                            plannedArtifact.path().toString(), vanishedAttempts);
            SlabRigCaseArtifacts.WrittenArtifact falseGreenVanishedArtifact =
                    SlabRigCaseArtifacts.writeManifest(root, falseGreenVanished);
            expectResumeInvalid(h, root, new SlabRigCaseArtifacts.Progress(
                            worldKey, BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                            frozenMode, SlabRigCaseCatalog.EXECUTION_CONTRACT, snapshot.catalogHash(),
                            2, snapshot.pageCount(), 1, falseGreenVanishedArtifact.contentId()),
                    snapshot, "placed-then-vanished hidden inside FINALIZED");

            SlabRigCasePageManifest.PageManifest truthfulVanished =
                    new SlabRigCasePageManifest.PageManifest(
                            "FINALIZED_WITH_REDS", snapshot, page, worldKey, frozenMode,
                            "minecraft:overworld", "test-player", new BlockPos(0, 4, 0), "south",
                            catalogPath, "none", plannedArtifact.path().toString(), vanishedAttempts);
            SlabRigCaseArtifacts.WrittenArtifact truthfulVanishedArtifact =
                    SlabRigCaseArtifacts.writeManifest(root, truthfulVanished);
            SlabRigCaseArtifacts.Progress truthfulVanishedProgress =
                    new SlabRigCaseArtifacts.Progress(
                            worldKey, BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                            frozenMode, SlabRigCaseCatalog.EXECUTION_CONTRACT, snapshot.catalogHash(),
                            2, snapshot.pageCount(), 1, truthfulVanishedArtifact.contentId());
            SlabRigCaseArtifacts.validateResumeEvidence(root, truthfulVanishedProgress, snapshot,
                    worldKey, BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                    frozenMode, "test-player", "minecraft:overworld");

            List<SlabRigCasePageManifest.CaseAttempt> unsupportedVanishedAttempts =
                    new ArrayList<>(finalizedAttempts);
            unsupportedVanishedAttempts.set(0, unsupportedVanishedAttempt(
                    page.cases().get(0), plannedAttempts.get(0).tileBase()));
            SlabRigCasePageManifest.PageManifest unsupportedVanished =
                    new SlabRigCasePageManifest.PageManifest(
                            "FINALIZED_WITH_REDS", snapshot, page, worldKey, frozenMode,
                            "minecraft:overworld", "test-player", new BlockPos(0, 4, 0), "south",
                            catalogPath, "none", plannedArtifact.path().toString(),
                            unsupportedVanishedAttempts);
            SlabRigCaseArtifacts.WrittenArtifact unsupportedVanishedArtifact =
                    SlabRigCaseArtifacts.writeManifest(root, unsupportedVanished);
            expectResumeInvalid(h, root, new SlabRigCaseArtifacts.Progress(
                            worldKey, BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                            frozenMode, SlabRigCaseCatalog.EXECUTION_CONTRACT, snapshot.catalogHash(),
                            2, snapshot.pageCount(), 1, unsupportedVanishedArtifact.contentId()),
                    snapshot, "vanished outcome without consumed/transformed/residual evidence");

            List<SlabRigCasePageManifest.CaseAttempt> consumedRefusalAttempts =
                    new ArrayList<>(finalizedAttempts);
            consumedRefusalAttempts.set(0, consumedRefusalAttempt(
                    page.cases().get(0), plannedAttempts.get(0).tileBase()));
            SlabRigCasePageManifest.PageManifest consumedRefusal =
                    new SlabRigCasePageManifest.PageManifest(
                            "FINALIZED", snapshot, page, worldKey, frozenMode,
                            "minecraft:overworld", "test-player", new BlockPos(0, 4, 0), "south",
                            catalogPath, "none", plannedArtifact.path().toString(),
                            consumedRefusalAttempts);
            SlabRigCaseArtifacts.WrittenArtifact consumedRefusalArtifact =
                    SlabRigCaseArtifacts.writeManifest(root, consumedRefusal);
            expectResumeInvalid(h, root, new SlabRigCaseArtifacts.Progress(
                            worldKey, BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                            frozenMode, SlabRigCaseCatalog.EXECUTION_CONTRACT, snapshot.catalogHash(),
                            2, snapshot.pageCount(), 1, consumedRefusalArtifact.contentId()),
                    snapshot, "refusal outcome hiding consumed-action evidence");

            List<SlabRigCasePageManifest.CaseAttempt> hiddenTopologyChangeAttempts =
                    new ArrayList<>(finalizedAttempts);
            hiddenTopologyChangeAttempts.set(0, hiddenTopologyChangeAttempt(
                    page.cases().get(0), plannedAttempts.get(0).tileBase()));
            SlabRigCasePageManifest.PageManifest hiddenTopologyChange =
                    new SlabRigCasePageManifest.PageManifest(
                            "FINALIZED", snapshot, page, worldKey, frozenMode,
                            "minecraft:overworld", "test-player",
                            new BlockPos(0, 4, 0), "south", catalogPath, "none",
                            plannedArtifact.path().toString(), hiddenTopologyChangeAttempts);
            SlabRigCaseArtifacts.WrittenArtifact hiddenTopologyChangeArtifact =
                    SlabRigCaseArtifacts.writeManifest(root, hiddenTopologyChange);
            expectResumeInvalid(h, root, new SlabRigCaseArtifacts.Progress(
                            worldKey, BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                            frozenMode, SlabRigCaseCatalog.EXECUTION_CONTRACT, snapshot.catalogHash(),
                            2, snapshot.pageCount(), 1, hiddenTopologyChangeArtifact.contentId()),
                    snapshot, "post-action topology live-dy change hidden inside FINALIZED");

            SlabRigCasePageManifest.PageManifest finalizedShell = new SlabRigCasePageManifest.PageManifest(
                    "FINALIZED", snapshot, page, worldKey, frozenMode,
                    "minecraft:overworld", "test-player",
                    new BlockPos(0, 4, 0), "south", catalogPath, "none",
                    plannedArtifact.path().toString(), plannedAttempts);
            SlabRigCaseArtifacts.WrittenArtifact shellArtifact =
                    SlabRigCaseArtifacts.writeManifest(root, finalizedShell);
            expectResumeInvalid(h, root, new SlabRigCaseArtifacts.Progress(
                    worldKey, BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                    frozenMode, SlabRigCaseCatalog.EXECUTION_CONTRACT,
                    snapshot.catalogHash(), 2, snapshot.pageCount(), 1, shellArtifact.contentId()),
                    snapshot, "finalized shell with PLANNED rows");

            Path missingPlan = root.resolve("case-pages").resolve("case-page-" + "0".repeat(64) + ".json");
            SlabRigCasePageManifest.PageManifest unlinkedFinal = new SlabRigCasePageManifest.PageManifest(
                    "FINALIZED", snapshot, page, worldKey, frozenMode,
                    "minecraft:overworld", "test-player",
                    new BlockPos(0, 4, 0), "south", catalogPath, "none",
                    missingPlan.toString(), finalizedAttempts);
            SlabRigCaseArtifacts.WrittenArtifact unlinkedArtifact =
                    SlabRigCaseArtifacts.writeManifest(root, unlinkedFinal);
            expectResumeInvalid(h, root, new SlabRigCaseArtifacts.Progress(
                    worldKey, BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                    frozenMode, SlabRigCaseCatalog.EXECUTION_CONTRACT,
                    snapshot.catalogHash(), 2, snapshot.pageCount(), 1, unlinkedArtifact.contentId()),
                    snapshot, "missing planned artifact");

            List<SlabRigCasePageManifest.CaseAttempt> exactA = new ArrayList<>(finalizedAttempts);
            List<SlabRigCasePageManifest.CaseAttempt> exactB = new ArrayList<>(finalizedAttempts);
            BlockPos firstBase = plannedAttempts.get(0).tileBase();
            exactA.set(0, finalizedAttempt(page.cases().get(0), firstBase, -0.5));
            exactB.set(0, finalizedAttempt(page.cases().get(0), firstBase, Math.nextUp(-0.5)));
            SlabRigCasePageManifest.PageManifest exactManifestA = new SlabRigCasePageManifest.PageManifest(
                    "FINALIZED", snapshot, page, worldKey, frozenMode,
                    "minecraft:overworld", "test-player",
                    new BlockPos(0, 4, 0), "south", catalogPath, "none",
                    plannedArtifact.path().toString(), exactA);
            SlabRigCasePageManifest.PageManifest exactManifestB = new SlabRigCasePageManifest.PageManifest(
                    "FINALIZED", snapshot, page, worldKey, frozenMode,
                    "minecraft:overworld", "test-player",
                    new BlockPos(0, 4, 0), "south", catalogPath, "none",
                    plannedArtifact.path().toString(), exactB);
            if (SlabRigCasePageManifest.manifestHash(exactManifestA)
                    .equals(SlabRigCasePageManifest.manifestHash(exactManifestB))
                    || !SlabRigCasePageManifest.canonicalJson(exactManifestB)
                    .contains(Double.toString(Math.nextUp(-0.5)))) {
                throw h.assertionException("stored dy evidence must preserve exact adjacent double values");
            }
            if (SlabRigCaseArtifacts.progressPath(root, "p", "a:b", worldKey)
                    .equals(SlabRigCaseArtifacts.progressPath(root, "p", "a_b", worldKey))) {
                throw h.assertionException("progress filenames must hash exact identities, not sanitize aliases");
            }
            if (!SlabRigCasePageManifest.observationChanged(Blocks.STONE.defaultBlockState(), 0.0,
                    "marker=none", Blocks.STONE.defaultBlockState(), -0.5, "marker=none")
                    || !SlabRigCasePageManifest.observationChanged(Blocks.STONE.defaultBlockState(), 0.0,
                    "marker=none", Blocks.STONE.defaultBlockState(), 0.0, "marker=anchored")
                    || SlabRigCasePageManifest.observationChanged(Blocks.STONE.defaultBlockState(), 0.0,
                    "marker=none", Blocks.STONE.defaultBlockState(), -0.0, "marker=none")) {
                throw h.assertionException("effect diff must detect store/marker-only changes and normalize -0.0");
            }
        } catch (java.io.IOException e) {
            throw h.assertionException("artifact contract I/O failed: " + e.getMessage());
        }
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigWorldIdentitySurvivesSaveAndRejectsRecreatedSamePath(GameTestHelper h) {
        Path parent = Path.of(System.getProperty("user.dir", "."), "build", "tmp", "rig2-world-id-contract");
        try {
            java.nio.file.Files.createDirectories(parent);
            Path worldRoot = java.nio.file.Files.createTempDirectory(parent, "world-");
            String first = SlabRigCaseArtifacts.loadOrCreateWorldKey(worldRoot);
            String repeated = SlabRigCaseArtifacts.loadOrCreateWorldKey(worldRoot);
            if (!first.equals(repeated) || !first.matches("[0-9a-f]{64}")) {
                throw h.assertionException("persistent world UUID must produce one stable exact world key");
            }

            String oldUuid = java.nio.file.Files.readAllLines(
                    SlabRigCaseArtifacts.worldIdentityPath(worldRoot), StandardCharsets.UTF_8)
                    .get(1).substring("uuid\t".length());
            String replacementUuid = java.util.UUID.randomUUID().toString();
            String oldKey = SlabRigCaseArtifacts.worldKey(worldRoot, oldUuid);
            String replacementKey = SlabRigCaseArtifacts.worldKey(worldRoot, replacementUuid);
            if (oldKey.equals(replacementKey)) {
                throw h.assertionException(
                        "same save path with a different persistent world UUID must reject the old cursor");
            }
        } catch (java.io.IOException e) {
            throw h.assertionException("world identity contract I/O failed: " + e.getMessage());
        }
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigCaseManifestMixedStatusCountersAreExact(GameTestHelper h) {
        SlabRigCaseCatalog.Snapshot snapshot = SlabRigCaseCatalog.snapshot();
        SlabRigCaseCatalog.CasePage page = SlabRigCaseCatalog.page(snapshot, 1);
        List<SlabRigCasePageManifest.CaseAttempt> rows = new ArrayList<>();
        rows.add(statusAttempt(page.cases().get(0), new BlockPos(0, 4, 0),
                "LAW_RED", "EXECUTED", "AUTO_USEON_PROXY", "REFUSED_NO_CHANGE", List.of()));
        rows.add(statusAttempt(page.cases().get(1), new BlockPos(8, 4, 0),
                "VERIFIED", "DEFERRED", "NOT_RUN", "DEFERRED_ROUTE",
                List.of(new SlabRigCasePageManifest.CellState(new BlockPos(9, 4, 0),
                        Blocks.DIAMOND_BLOCK.defaultBlockState(), Math.nextUp(-0.5), "anchored=true"))));
        rows.add(statusAttempt(page.cases().get(2), new BlockPos(16, 4, 0),
                "PLANNED", "PLANNED", "NOT_RUN", "PLANNED", List.of()));
        rows.add(statusAttempt(page.cases().get(3), new BlockPos(24, 4, 0),
                "ERROR", "ERROR", "NOT_RUN", "ERROR_TOPOLOGY", List.of()));
        rows.add(statusAttempt(page.cases().get(4), new BlockPos(0, 4, 8),
                "PLANNED", "INTERRUPTED", "NOT_RUN", "INTERRUPTED_AFTER_RED", List.of()));
        rows.add(statusAttempt(page.cases().get(5), new BlockPos(8, 4, 8),
                "VERIFIED", "EXECUTED", "AUTO_USEON_PROXY", "PLACED_THEN_VANISHED", List.of()));
        SlabRigCasePageManifest.PageManifest mixed = new SlabRigCasePageManifest.PageManifest(
                "PARTIAL", snapshot, page, "b".repeat(64),
                SlabAnchorAttachment.FROZEN_DY_ENABLED,
                "minecraft:overworld", "test-player",
                new BlockPos(0, 4, 0), "south", "catalog", "none", "plan", rows);
        SlabRigCasePageManifest.Coverage coverage = SlabRigCasePageManifest.coverage(mixed);
        if (coverage.casesMaterialized() != 6 || coverage.proxyExecuted() != 2
                || coverage.deferred() != 1 || coverage.planned() != 1
                || coverage.errors() != 1 || coverage.interrupted() != 1
                || coverage.topologyLawReds() != 1 || coverage.placedThenVanished() != 1
                || !SlabRigCasePageManifest.hasLawReds(coverage)
                || !"FINALIZED_WITH_REDS".equals(
                SlabRigCasePageManifest.finalizedStatus(0, 0, 1))
                || !"FINALIZED".equals(SlabRigCasePageManifest.finalizedStatus(0, 0, 0))
                || !"PARTIAL".equals(SlabRigCasePageManifest.finalizedStatus(1, 0, 1))
                || coverage.externalGuardCells() != 1) {
            throw h.assertionException("mixed status counters must not count error/interrupted as proxy execution: "
                    + coverage);
        }
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigCaseFullArchiveAuditDistinguishesRuntimeInductionFromClosure(GameTestHelper h) {
        List<SlabRigCaseCatalog.CatalogItem> items = List.of(new SlabRigCaseCatalog.CatalogItem(
                0, "minecraft:stone",
                List.of("family:ordinary_full_cube", "kind:item:standard_block_item",
                        "namespace:minecraft", "route:standard_block_item", "shape:full_cube",
                        "universe:block_item"),
                SlabRigCaseCatalog.Disposition.AUTO_FLOOR_UP,
                SlabRigCaseCatalog.EffectPolicy.LOCAL_TARGET_AND_NEIGHBORS));
        List<SlabRigCaseCatalog.Topology> topologies = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            topologies.add(new SlabRigCaseCatalog.Topology(index, "fixture:" + index,
                    "FIXTURE_" + index, index < 2));
        }
        String hash = SlabRigCaseCatalog.catalogHash(SlabRigCaseCatalog.SCHEMA,
                items, List.of(), topologies);
        SlabRigCaseCatalog.Snapshot snapshot = new SlabRigCaseCatalog.Snapshot(
                SlabRigCaseCatalog.SCHEMA, hash, items, List.of(), topologies, 12, 3);
        Path root = Path.of(System.getProperty("user.dir", "."), "build", "tmp",
                "rig2-full-archive-contract");
        String worldKey = "c".repeat(64);
        boolean frozenMode = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        try {
            SlabRigCaseArtifacts.writeCatalog(root, snapshot);
            String page1 = writeFinalizedFixturePage(
                    root, snapshot, 1, "none", worldKey, frozenMode);
            String page2 = writeFinalizedFixturePage(
                    root, snapshot, 2, page1, worldKey, frozenMode);
            String page3 = writeFinalizedFixturePage(
                    root, snapshot, 3, page2, worldKey, frozenMode);
            SlabRigCaseArtifacts.Progress closed = new SlabRigCaseArtifacts.Progress(
                    worldKey, BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                    frozenMode,
                    SlabRigCaseCatalog.EXECUTION_CONTRACT,
                    snapshot.catalogHash(), 0, 3, 3, page3);
            SlabRigCaseArtifacts.validateResumeEvidence(root, closed, snapshot, worldKey,
                    BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                    frozenMode,
                    "test-player", "minecraft:overworld");
            SlabRigCaseArtifacts.validateFullArchiveEvidence(root, closed, snapshot, worldKey,
                    BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                    frozenMode,
                    "test-player", "minecraft:overworld");

            String missingPage1 = "f".repeat(64);
            String brokenPage2 = writeFinalizedFixturePage(
                    root, snapshot, 2, missingPage1, worldKey, frozenMode);
            String brokenPage3 = writeFinalizedFixturePage(
                    root, snapshot, 3, brokenPage2, worldKey, frozenMode);
            SlabRigCaseArtifacts.Progress inductiveOnly = new SlabRigCaseArtifacts.Progress(
                    worldKey, BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                    frozenMode,
                    SlabRigCaseCatalog.EXECUTION_CONTRACT,
                    snapshot.catalogHash(), 0, 3, 3, brokenPage3);
            // Runtime resume intentionally checks N and N-1; closure must additionally walk N..1.
            SlabRigCaseArtifacts.validateResumeEvidence(root, inductiveOnly, snapshot, worldKey,
                    BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                    frozenMode,
                    "test-player", "minecraft:overworld");
            try {
                SlabRigCaseArtifacts.validateFullArchiveEvidence(root, inductiveOnly, snapshot, worldKey,
                        BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                        frozenMode,
                        "test-player", "minecraft:overworld");
                throw h.assertionException("full archive audit must reject a missing older chain link");
            } catch (java.io.IOException expected) {
                // expected distinction
            }

        } catch (java.io.IOException e) {
            throw h.assertionException("full archive contract I/O failed: " + e.getMessage());
        }
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigCaseFullArchiveRejectsOlderFrozenMode(GameTestHelper h) {
        List<SlabRigCaseCatalog.CatalogItem> items = List.of(new SlabRigCaseCatalog.CatalogItem(
                0, "minecraft:stone",
                List.of("family:ordinary_full_cube", "kind:item:standard_block_item",
                        "namespace:minecraft", "route:standard_block_item", "shape:full_cube",
                        "universe:block_item"),
                SlabRigCaseCatalog.Disposition.AUTO_FLOOR_UP,
                SlabRigCaseCatalog.EffectPolicy.LOCAL_TARGET_AND_NEIGHBORS));
        List<SlabRigCaseCatalog.Topology> topologies = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            topologies.add(new SlabRigCaseCatalog.Topology(index, "fixture:" + index,
                    "FIXTURE_" + index, index < 2));
        }
        String hash = SlabRigCaseCatalog.catalogHash(SlabRigCaseCatalog.SCHEMA,
                items, List.of(), topologies);
        SlabRigCaseCatalog.Snapshot snapshot = new SlabRigCaseCatalog.Snapshot(
                SlabRigCaseCatalog.SCHEMA, hash, items, List.of(), topologies, 12, 3);
        Path root = Path.of(System.getProperty("user.dir", "."), "build", "tmp",
                "rig2-full-archive-frozen-mode-contract");
        String worldKey = "d".repeat(64);
        boolean frozenMode = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        try {
            SlabRigCaseArtifacts.writeCatalog(root, snapshot);
            String foreignModePage1 = writeFinalizedFixturePage(
                    root, snapshot, 1, "none", worldKey, !frozenMode);
            String currentModePage2 = writeFinalizedFixturePage(
                    root, snapshot, 2, foreignModePage1, worldKey, frozenMode);
            String currentModePage3 = writeFinalizedFixturePage(
                    root, snapshot, 3, currentModePage2, worldKey, frozenMode);
            SlabRigCaseArtifacts.Progress progress = new SlabRigCaseArtifacts.Progress(
                    worldKey, BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                    frozenMode, SlabRigCaseCatalog.EXECUTION_CONTRACT,
                    snapshot.catalogHash(), 0, 3, 3, currentModePage3);
            SlabRigCaseArtifacts.validateResumeEvidence(root, progress, snapshot, worldKey,
                    BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                    frozenMode, "test-player", "minecraft:overworld");
            try {
                SlabRigCaseArtifacts.validateFullArchiveEvidence(root, progress, snapshot, worldKey,
                        BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                        frozenMode, "test-player", "minecraft:overworld");
                throw h.assertionException(
                        "full archive closure must reject a frozen-mode mismatch hidden before N-1");
            } catch (java.io.IOException expected) {
                // expected: bounded resume sees pages 3 and 2; closure reaches foreign page 1.
            }
        } catch (java.io.IOException e) {
            throw h.assertionException("frozen-mode archive contract I/O failed: " + e.getMessage());
        }
        h.succeed();
    }

    private static String writeFinalizedFixturePage(Path root,
                                                    SlabRigCaseCatalog.Snapshot snapshot,
                                                    int pageNumber, String previousHash,
                                                    String worldKey,
                                                    boolean frozenMode) throws java.io.IOException {
        SlabRigCaseCatalog.CasePage page = SlabRigCaseCatalog.page(snapshot, pageNumber);
        List<SlabRigCasePageManifest.CaseAttempt> plans = new ArrayList<>();
        List<SlabRigCasePageManifest.CaseAttempt> finals = new ArrayList<>();
        for (int index = 0; index < page.cases().size(); index++) {
            BlockPos base = new BlockPos(index * 8, 4, pageNumber * 8);
            plans.add(plannedAttempt(page.cases().get(index), base));
            finals.add(finalizedAttempt(page.cases().get(index), base, Double.NaN));
        }
        String catalogPath = root.resolve("catalogs")
                .resolve("catalog-" + snapshot.catalogHash() + ".tsv").toString();
        SlabRigCasePageManifest.PageManifest planned = new SlabRigCasePageManifest.PageManifest(
                "PLANNED", snapshot, page, worldKey, frozenMode,
                "minecraft:overworld", "test-player",
                new BlockPos(0, 4, 0), "south", catalogPath, previousHash,
                "self:content-addressed-after-serialization", plans);
        SlabRigCaseArtifacts.WrittenArtifact planArtifact = SlabRigCaseArtifacts.writeManifest(root, planned);
        SlabRigCasePageManifest.PageManifest finalized = new SlabRigCasePageManifest.PageManifest(
                "FINALIZED", snapshot, page, worldKey, frozenMode,
                "minecraft:overworld", "test-player",
                new BlockPos(0, 4, 0), "south", catalogPath, previousHash,
                planArtifact.path().toString(), finals);
        return SlabRigCaseArtifacts.writeManifest(root, finalized).contentId();
    }

    private static SlabRigCasePageManifest.CaseAttempt plannedAttempt(
            SlabRigCaseCatalog.CaseDefinition definition, BlockPos base) {
        return new SlabRigCasePageManifest.CaseAttempt(definition, base,
                List.of(base), "PLANNED", "planned", List.of(), List.of(),
                "NOT_RUN", "action not run", List.of(),
                List.of(base, base.above()), List.of(base, base.above()),
                base, "up", base.above(), "PLANNED", "NOT_RUN", "PLANNED",
                "NOT_RUN", false, "NOT_RUN", 0, "NOT_RUN", 0, false,
                "planned", List.of());
    }

    private static SlabRigCasePageManifest.CaseAttempt finalizedAttempt(
            SlabRigCaseCatalog.CaseDefinition definition, BlockPos base, double storedDy) {
        return new SlabRigCasePageManifest.CaseAttempt(definition, base,
                List.of(base), "VERIFIED", "fixture structure verified",
                structureFixture(base, storedDy), structureFixture(base, storedDy),
                "STABLE", "fixture topology unchanged", List.of(),
                List.of(base, base.above()), List.of(base, base.above()),
                base, "up", base.above(), "EXECUTED", "AUTO_USEON_PROXY", "REFUSED_NO_CHANGE",
                "PASS", false, definition.item().id(), 1, definition.item().id(), 1, false,
                "fixture no-change execution", List.of());
    }

    private static SlabRigCasePageManifest.CaseAttempt vanishedAttempt(
            SlabRigCaseCatalog.CaseDefinition definition, BlockPos base) {
        return new SlabRigCasePageManifest.CaseAttempt(definition, base,
                List.of(base), "VERIFIED", "fixture structure verified",
                structureFixture(base, Double.NaN), structureFixture(base, Double.NaN),
                "STABLE", "fixture topology unchanged", List.of(),
                List.of(base, base.above()), List.of(base, base.above()),
                base, "up", base.above(), "EXECUTED", "AUTO_USEON_PROXY",
                "PLACED_THEN_VANISHED", "SUCCESS", true,
                definition.item().id(), 1, "minecraft:bucket", 1, false,
                "stack consumed but subject vanished", List.of());
    }

    private static SlabRigCasePageManifest.CaseAttempt unsupportedVanishedAttempt(
            SlabRigCaseCatalog.CaseDefinition definition, BlockPos base) {
        return new SlabRigCasePageManifest.CaseAttempt(definition, base,
                List.of(base), "VERIFIED", "fixture structure verified",
                structureFixture(base, Double.NaN), structureFixture(base, Double.NaN),
                "STABLE", "fixture topology unchanged", List.of(),
                List.of(base, base.above()), List.of(base, base.above()),
                base, "up", base.above(), "EXECUTED", "AUTO_USEON_PROXY",
                "PLACED_THEN_VANISHED", "PASS", false,
                definition.item().id(), 1, definition.item().id(), 1, false,
                "forged vanished row with no supporting action evidence", List.of());
    }

    private static SlabRigCasePageManifest.CaseAttempt consumedRefusalAttempt(
            SlabRigCaseCatalog.CaseDefinition definition, BlockPos base) {
        return new SlabRigCasePageManifest.CaseAttempt(definition, base,
                List.of(base), "VERIFIED", "fixture structure verified",
                structureFixture(base, Double.NaN), structureFixture(base, Double.NaN),
                "STABLE", "fixture topology unchanged", List.of(),
                List.of(base, base.above()), List.of(base, base.above()),
                base, "up", base.above(), "EXECUTED", "AUTO_USEON_PROXY",
                "REFUSED_NO_CHANGE", "SUCCESS", true,
                definition.item().id(), 1, definition.item().id(), 1, false,
                "forged refusal row hiding consumed action", List.of());
    }

    private static SlabRigCasePageManifest.CaseAttempt hiddenTopologyChangeAttempt(
            SlabRigCaseCatalog.CaseDefinition definition, BlockPos base) {
        List<SlabRigCasePageManifest.StructureCellState> before = structureFixture(base, Double.NaN);
        List<SlabRigCasePageManifest.StructureCellState> after = List.of(
                new SlabRigCasePageManifest.StructureCellState(base,
                        Blocks.STONE.defaultBlockState(), -0.5, Double.NaN, "marker=none"));
        return new SlabRigCasePageManifest.CaseAttempt(definition, base,
                List.of(base), "VERIFIED", "forged stable structure",
                before, after, "STABLE", "forged hidden live-dy movement", List.of(),
                List.of(base, base.above()), List.of(base, base.above()),
                base, "up", base.above(), "EXECUTED", "AUTO_USEON_PROXY",
                "REFUSED_NO_CHANGE", "PASS", false,
                definition.item().id(), 1, definition.item().id(), 1, false,
                "fixture no-change execution", List.of());
    }

    private static SlabRigCasePageManifest.CaseAttempt statusAttempt(
            SlabRigCaseCatalog.CaseDefinition definition, BlockPos base,
            String structureStatus, String attemptStatus, String actionOrigin, String outcome,
            List<SlabRigCasePageManifest.CellState> externalContext) {
        boolean executed = "EXECUTED".equals(attemptStatus);
        boolean vanished = "PLACED_THEN_VANISHED".equals(outcome);
        return new SlabRigCasePageManifest.CaseAttempt(definition, base,
                List.of(base), structureStatus, "mixed status fixture",
                List.of(), List.of(), executed ? "STABLE" : "NOT_RUN",
                executed ? "mixed fixture stable" : "action not run",
                externalContext, List.of(base), List.of(base),
                base, "up", base.above(), attemptStatus, actionOrigin, outcome,
                executed ? (vanished ? "SUCCESS" : "PASS") : "NOT_RUN",
                vanished,
                executed ? definition.item().id() : "NOT_RUN", executed ? 1 : 0,
                executed ? definition.item().id() : "NOT_RUN", executed ? 1 : 0,
                false, "mixed status fixture", List.of());
    }

    private static List<SlabRigCasePageManifest.StructureCellState> structureFixture(
            BlockPos base, double storedDy) {
        return List.of(new SlabRigCasePageManifest.StructureCellState(base,
                Blocks.STONE.defaultBlockState(), 0.0, storedDy, "marker=none"));
    }

    private static void expectResumeInvalid(GameTestHelper h, Path root,
                                            SlabRigCaseArtifacts.Progress progress,
                                            SlabRigCaseCatalog.Snapshot snapshot,
                                            String label) {
        try {
            SlabRigCaseArtifacts.validateResumeEvidence(root, progress, snapshot, progress.worldKey(),
                    BuildStamp.GIT_SHA, BuildStamp.RUNTIME_CONTENT_SHA256,
                    progress.frozenDyEnabled(), "test-player", "minecraft:overworld");
            throw h.assertionException(label + " must be progress-ineligible");
        } catch (java.io.IOException expected) {
            // expected fail-closed result
        }
    }

    private static void assertTags(GameTestHelper h, Map<String, SlabRigCaseCatalog.CatalogItem> entries,
                                   String id, String... tags) {
        SlabRigCaseCatalog.CatalogItem entry = entries.get(id);
        if (entry == null) {
            throw h.assertionException("required runtime BlockItem missing from catalog: " + id);
        }
        for (String tag : tags) {
            if (!entry.categories().contains(tag)) {
                throw h.assertionException(id + " missing category " + tag + ": " + entry.categories());
            }
        }
    }

    private static void assertExcludedRoute(GameTestHelper h,
                                            Map<String, SlabRigCaseCatalog.ExcludedItem> entries,
                                            String id, String route) {
        SlabRigCaseCatalog.ExcludedItem entry = entries.get(id);
        if (entry == null || !route.equals(entry.route())) {
            throw h.assertionException(id + " must be explicitly routed as " + route + ": " + entry);
        }
    }

    private static void expectInvalidPage(GameTestHelper h, SlabRigCaseCatalog.Snapshot snapshot, int page) {
        try {
            SlabRigCaseCatalog.page(snapshot, page);
            throw h.assertionException("invalid page must fail closed before any materialization: " + page);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
