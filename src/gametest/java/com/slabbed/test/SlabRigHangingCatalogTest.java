package com.slabbed.test;

import com.slabbed.command.SlabRigHangingCatalog;
import com.slabbed.command.SlabRigHangingArtifacts;
import com.slabbed.util.BuildStamp;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.io.IOException;

/** Pure RIG-3A catalog plus RIG-3B1 registry/artifact contracts; no world execution. */
public final class SlabRigHangingCatalogTest {

    private static final String EXPECTED_26_2_CATALOG_HASH =
            "db61c584f0f70c1120ec6cf631964cad96f71389dbc23e0b7e20e2c2f3685454";

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingRuntimeSnapshotBindsFullPaintingRegistry(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot catalog = SlabRigHangingCatalog.snapshot();
        SlabRigHangingArtifacts.RuntimeSnapshot first = SlabRigHangingArtifacts.snapshot(
                catalog, helper.getLevel().registryAccess());
        SlabRigHangingArtifacts.RuntimeSnapshot second = SlabRigHangingArtifacts.snapshot(
                catalog, helper.getLevel().registryAccess());
        if (!first.equals(second) || !first.canonicalTsv().equals(second.canonicalTsv())) {
            throw helper.assertionException("RIG-3B1 runtime snapshot/order/hash changed across repeats");
        }
        if (first.paintingVariantCount() != 51 || first.randomPlaceableCount() != 47) {
            throw helper.assertionException("26.2 painting registry expected full=51 #placeable=47, got full="
                    + first.paintingVariantCount() + " placeable=" + first.randomPlaceableCount());
        }
        if (!first.runtimeContentSha256().equals(BuildStamp.RUNTIME_CONTENT_SHA256)
                || !first.minecraftVersion().equals(SharedConstants.getCurrentVersion().id())
                || !"minecraft:painting_variant".equals(first.paintingRegistryId())
                || !"minecraft:placeable".equals(first.placeableTagId())
                || !"minecraft:painting/variant".equals(first.paintingComponentId())) {
            throw helper.assertionException("RIG-3B1 omitted exact runtime/version/registry identities");
        }
        Registry<PaintingVariant> registry = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.PAINTING_VARIANT);
        HolderSet.Named<PaintingVariant> livePlaceable = registry.get(PaintingVariantTags.PLACEABLE)
                .orElseThrow(() -> helper.assertionException("live #placeable painting tag is absent"));
        List<Holder.Reference<PaintingVariant>> liveRows = registry.listElements()
                .sorted(Comparator.comparing(ref -> ref.key().identifier().toString())).toList();
        if (liveRows.size() != first.paintings().size()) {
            throw helper.assertionException("runtime artifact did not snapshot every live painting holder");
        }
        Set<String> untagged = new HashSet<>();
        String previous = "";
        for (int index = 0; index < first.paintings().size(); index++) {
            SlabRigHangingArtifacts.PaintingEntry entry = first.paintings().get(index);
            Holder.Reference<PaintingVariant> liveHolder = liveRows.get(index);
            PaintingVariant liveVariant = liveHolder.value();
            if (entry.index() != index || entry.id().compareTo(previous) <= 0 && index > 0) {
                throw helper.assertionException("painting rows are not unique namespace:path order at " + entry);
            }
            previous = entry.id();
            if (!entry.randomPlaceable()) {
                untagged.add(entry.id());
            }
            if (!"minecraft:painting/variant".equals(entry.componentType())
                    || !entry.id().equals(entry.componentValue())
                    || !"north,east,south,west".equals(entry.wallDirections())
                    || entry.backingCellCount() != entry.areaBlocks()
                    || entry.lateralMin() != -((entry.widthBlocks() - 1) / 2)
                    || entry.lateralMax() != entry.widthBlocks() / 2
                    || entry.verticalMin() != -((entry.heightBlocks() - 1) / 2)
                    || entry.verticalMax() != entry.heightBlocks() / 2
                    || entry.lateralMax() - entry.lateralMin() + 1 != entry.widthBlocks()
                    || entry.verticalMax() - entry.verticalMin() + 1 != entry.heightBlocks()
                    || !entry.id().equals(liveHolder.key().identifier().toString())
                    || entry.randomPlaceable() != livePlaceable.contains(liveHolder)
                    || entry.widthBlocks() != liveVariant.width()
                    || entry.heightBlocks() != liveVariant.height()
                    || entry.areaBlocks() != liveVariant.area()
                    || !entry.assetId().equals(liveVariant.assetId().toString())) {
                throw helper.assertionException("painting component/footprint contract collapsed for " + entry.id());
            }
            ItemStack configured = new ItemStack(Items.PAINTING);
            configured.set(DataComponents.PAINTING_VARIANT, liveHolder);
            Holder<PaintingVariant> roundTrip = configured.get(DataComponents.PAINTING_VARIANT);
            if (roundTrip == null || !roundTrip.is(liveHolder.key())) {
                throw helper.assertionException("painting component did not round-trip live holder " + entry.id());
            }
        }
        Set<String> expectedUntagged = Set.of(
                "minecraft:earth", "minecraft:fire", "minecraft:water", "minecraft:wind");
        if (!untagged.equals(expectedUntagged)) {
            throw helper.assertionException("26.2 exact non-random painting set changed: " + untagged);
        }
        SlabRigHangingArtifacts.PaintingEntry kebab = painting(first, "minecraft:kebab");
        SlabRigHangingArtifacts.PaintingEntry burningSkull = painting(first, "minecraft:burning_skull");
        if (kebab.widthBlocks() != 1 || kebab.heightBlocks() != 1
                || burningSkull.widthBlocks() != 4 || burningSkull.heightBlocks() != 4
                || !"minecraft:kebab".equals(kebab.assetId())
                || !"minecraft:burning_skull".equals(burningSkull.assetId())) {
            throw helper.assertionException("known 1x1/4x4 painting footprint or asset mapping changed");
        }
        String tsv = first.canonicalTsv();
        String identityLine = "execution_identity\t" + first.executionIdentity() + "\n";
        String withoutIdentity = tsv.replace(identityLine, "");
        if (tsv.indexOf(identityLine) != tsv.lastIndexOf(identityLine)
                || !first.executionIdentity().equals(sha256(withoutIdentity))
                || !tsv.contains("catalog_tsv_begin\n" + SlabRigHangingCatalog.catalogTsv(catalog))
                || !tsv.contains("player_proof\tABSENT\n")
                || !tsv.contains("proof_scope\tCATALOG_ONLY\n")
                || !tsv.contains("world_mutation\tNONE\n")
                || !tsv.contains("runtime_content_sha256\t" + BuildStamp.RUNTIME_CONTENT_SHA256 + "\n")
                || !tsv.contains("painting_selection_contract\t"
                + SlabRigHangingArtifacts.SELECTION_CONTRACT + "\n")
                || !tsv.contains("painting_support_box_deflate\t0.0000001\n")
                || !tsv.contains("painting_tooltip_fields\texcluded_non_execution_metadata\n")) {
            throw helper.assertionException("RIG-3B1 canonical bytes omit identity/proof/catalog boundaries");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingRuntimeArtifactIsAtomicIdempotentAndCollisionSafe(GameTestHelper helper) {
        SlabRigHangingArtifacts.RuntimeSnapshot snapshot = SlabRigHangingArtifacts.snapshot(
                SlabRigHangingCatalog.snapshot(), helper.getLevel().registryAccess());
        Path root = null;
        try {
            root = Files.createTempDirectory("slabbed-rig3b1-");
            SlabRigHangingArtifacts.WrittenArtifact first =
                    SlabRigHangingArtifacts.write(root, snapshot);
            byte[] expected = snapshot.canonicalTsv().getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(Files.readAllBytes(first.path()), expected)
                    || !first.path().getParent().equals(root.resolve("hanging-catalogs"))
                    || !first.path().getFileName().toString().equals(
                    "hanging-catalog-" + snapshot.executionIdentity() + ".tsv")
                    || !first.fileSha256().equals(sha256(expected))) {
                throw helper.assertionException("first RIG-3B1 publication bytes/path/hash are not exact");
            }

            FileTime sentinelTime = FileTime.fromMillis(1_234_567_890_000L);
            Files.setLastModifiedTime(first.path(), sentinelTime);
            SlabRigHangingArtifacts.WrittenArtifact repeat =
                    SlabRigHangingArtifacts.write(root, snapshot);
            if (!repeat.equals(new SlabRigHangingArtifacts.WrittenArtifact(first.path(),
                    first.executionIdentity(), first.fileSha256(), first.byteCount()))
                    || !Files.getLastModifiedTime(first.path()).equals(sentinelTime)) {
                throw helper.assertionException("identical RIG-3B1 rerun rewrote bytes or changed identity");
            }

            Files.delete(first.path());
            byte[] collision = "preexisting unrelated evidence\n".getBytes(StandardCharsets.UTF_8);
            Files.write(first.path(), collision);
            boolean refused = false;
            try {
                SlabRigHangingArtifacts.write(root, snapshot);
            } catch (IOException expectedRefusal) {
                refused = true;
            }
            if (!refused || !MessageDigest.isEqual(Files.readAllBytes(first.path()), collision)
                    || hasTemporary(first.path().getParent())) {
                throw helper.assertionException("RIG-3B1 collision was replaced or left partial evidence");
            }

            Files.delete(first.path());
            Path symlinkDestination = root.resolve("outside-evidence.tsv");
            byte[] outside = "outside evidence must remain untouched\n".getBytes(StandardCharsets.UTF_8);
            Files.write(symlinkDestination, outside);
            Files.createSymbolicLink(first.path(), symlinkDestination);
            refused = false;
            try {
                SlabRigHangingArtifacts.write(root, snapshot);
            } catch (IOException expectedRefusal) {
                refused = true;
            }
            if (!refused || !Files.isSymbolicLink(first.path())
                    || !MessageDigest.isEqual(Files.readAllBytes(symlinkDestination), outside)) {
                throw helper.assertionException("RIG-3B1 followed/replaced a symlinked target");
            }

            Files.delete(first.path());
            Files.delete(first.path().getParent());
            Path redirected = root.resolve("redirected-directory");
            Files.createDirectory(redirected);
            Files.createSymbolicLink(root.resolve("hanging-catalogs"), redirected);
            refused = false;
            try {
                SlabRigHangingArtifacts.write(root, snapshot);
            } catch (IOException expectedRefusal) {
                refused = true;
            }
            if (!refused || !Files.isSymbolicLink(root.resolve("hanging-catalogs"))) {
                throw helper.assertionException("RIG-3B1 followed/replaced a symlinked artifact directory");
            }
        } catch (IOException e) {
            throw helper.assertionException("RIG-3B1 artifact proof failed: " + e);
        } finally {
            deleteTree(root);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void runtimeItemPartitionIsTotalAndExact(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot snapshot = SlabRigHangingCatalog.snapshot();
        Set<String> seen = new HashSet<>();
        for (SlabRigHangingCatalog.CatalogItem item : snapshot.items()) {
            if (!seen.add(item.id())) {
                throw helper.assertionException("RIG-3 item appears twice: " + item.id());
            }
        }
        if (snapshot.items().size() != 163) {
            throw helper.assertionException("RIG-3 primary hanging/attachment union expected 163 items, got "
                    + snapshot.items().size());
        }
        long blockItems = snapshot.items().stream().filter(SlabRigHangingCatalog.CatalogItem::blockItem).count();
        if (blockItems != 159L || snapshot.items().size() - blockItems != 4L
                || snapshot.excludedItems().size() != 1374) {
            throw helper.assertionException("RIG-3 primary/remainder cardinality changed: block="
                    + blockItems + " entity=" + (snapshot.items().size() - blockItems)
                    + " excluded=" + snapshot.excludedItems().size());
        }
        for (SlabRigHangingCatalog.ExcludedItem item : snapshot.excludedItems()) {
            if (!seen.add(item.id())) {
                throw helper.assertionException("RIG-3 item is both included and excluded: " + item.id());
            }
            if (item.reason().isBlank() || item.reason().contains("misc")
                    || item.reason().contains("unclassified")) {
                throw helper.assertionException("RIG-3 exclusion is not explicit: " + item);
            }
        }
        if (seen.size() != BuiltInRegistries.ITEM.size()) {
            throw helper.assertionException("RIG-3 item partition size " + seen.size()
                    + " != runtime registry " + BuiltInRegistries.ITEM.size());
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void exactCoreFamilyCountsAreRuntimeDerived(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot snapshot = SlabRigHangingCatalog.snapshot();
        Map<SlabRigHangingCatalog.Family, Integer> counts =
                new EnumMap<>(SlabRigHangingCatalog.Family.class);
        for (SlabRigHangingCatalog.CatalogItem item : snapshot.items()) {
            counts.merge(item.family(), 1, Integer::sum);
        }
        expect(helper, counts, SlabRigHangingCatalog.Family.ENTITY_HANGING, 3);
        expect(helper, counts, SlabRigHangingCatalog.Family.HANGING_SIGN, 12);
        expect(helper, counts, SlabRigHangingCatalog.Family.CHAIN, 9);
        expect(helper, counts, SlabRigHangingCatalog.Family.LANTERN, 10);
        expect(helper, counts, SlabRigHangingCatalog.Family.FACE_ATTACHED, 16);
        expect(helper, counts, SlabRigHangingCatalog.Family.BELL, 1);
        expect(helper, counts, SlabRigHangingCatalog.Family.STANDING_AND_WALL, 49);
        expect(helper, counts, SlabRigHangingCatalog.Family.TRAPDOOR, 21);
        expect(helper, counts, SlabRigHangingCatalog.Family.ROD, 9);
        expect(helper, counts, SlabRigHangingCatalog.Family.AMETHYST_CLUSTER, 4);
        expect(helper, counts, SlabRigHangingCatalog.Family.MULTIFACE, 3);
        expect(helper, counts, SlabRigHangingCatalog.Family.SPELEOTHEM, 2);
        expect(helper, counts, SlabRigHangingCatalog.Family.CEILING_GROWTH, 5);
        expect(helper, counts, SlabRigHangingCatalog.Family.WALL_ATTACHMENT, 4);
        expect(helper, counts, SlabRigHangingCatalog.Family.WALL_CARPET_STATE, 1);
        expect(helper, counts, SlabRigHangingCatalog.Family.GENERATED_HANGING_STATE, 1);
        expect(helper, counts, SlabRigHangingCatalog.Family.SHELF, 12);
        expect(helper, counts, SlabRigHangingCatalog.Family.LEASH_KNOT_ENTITY, 1);
        if (!snapshot.catalogHash().equals(EXPECTED_26_2_CATALOG_HASH)) {
            throw helper.assertionException("RIG-3 26.2 golden catalog hash changed: expected="
                    + EXPECTED_26_2_CATALOG_HASH + " actual=" + snapshot.catalogHash());
        }
        System.out.println("RIG3-CATALOG | hash=" + snapshot.catalogHash()
                + " runtimeItems=" + snapshot.runtimeItemCount()
                + " included=" + snapshot.items().size()
                + " excluded=" + snapshot.excludedItems().size()
                + " routes=" + snapshot.routes().size()
                + " chainTerminals=" + snapshot.chainTerminalRoutes().size()
                + " chainPatterns=" + snapshot.chainPatterns().size()
                + " topologies=" + snapshot.topologies().size()
                + " totalCases=" + snapshot.totalCases()
                + " pages=" + snapshot.pageCount());
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void routeMatrixKeepsStateAndEvidenceOriginsSeparate(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot snapshot = SlabRigHangingCatalog.snapshot();
        Set<String> ids = new HashSet<>();
        for (SlabRigHangingCatalog.Route route : snapshot.routes()) {
            if (!ids.add(route.id())) {
                throw helper.assertionException("duplicate RIG-3 route id " + route.id());
            }
            String expectedOrigin = switch (route.actionKind()) {
                case GENERATED_STATE_ONLY -> "GENERATED_STATE_PROXY";
                case PLAYER_PLACEMENT_DERIVED_SECONDARY -> "PLAYER_PLACEMENT_SECONDARY_PROXY";
                case PLAYER_DERIVED_NEIGHBOR_UPDATE -> "PLAYER_NEIGHBOR_DERIVED_PROXY";
                case BONEMEAL_DERIVED_SECONDARY -> "BONEMEAL_DERIVED_PROXY";
                case RANDOM_TICK_DERIVED_SECONDARY -> "RANDOM_TICK_DERIVED_PROXY";
                default -> "AUTO_USEON_PROXY";
            };
            if (!expectedOrigin.equals(route.actionOrigin())) {
                throw helper.assertionException("RIG-3 origin conflation for " + route.id()
                        + " expected=" + expectedOrigin + " actual=" + route.actionOrigin());
            }
        }
        requireRoute(helper, snapshot.routes(), "family=bell", "mount=ceiling");
        requireRoute(helper, snapshot.routes(), "family=bell", "mount=floor");
        requireRoute(helper, snapshot.routes(), "family=bell", "mount=wall_single");
        requireRoute(helper, snapshot.routes(), "family=bell", "mount=wall_double");
        requireRoute(helper, snapshot.routes(), "family=hanging_sign", "mount=ceiling");
        requireRoute(helper, snapshot.routes(), "family=hanging_sign", "mount=wall_lateral");
        requireRoute(helper, snapshot.routes(), "family=face_attached", "attach_face=ceiling");
        requireRoute(helper, snapshot.routes(), "family=face_attached", "attach_face=floor");
        requireRoute(helper, snapshot.routes(), "family=face_attached", "attach_face=wall");
        requireRoute(helper, snapshot.routes(), "family=generated_hanging_state", "hanging=true");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void exactRouteExpansionsCannotSilentlyCollapse(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot snapshot = SlabRigHangingCatalog.snapshot();
        Map<SlabRigHangingCatalog.Family, Integer> counts =
                new EnumMap<>(SlabRigHangingCatalog.Family.class);
        for (SlabRigHangingCatalog.Route route : snapshot.routes()) {
            counts.merge(route.family(), 1, Integer::sum);
        }
        System.out.println("RIG3-ROUTE-COUNTS | " + counts);
        expect(helper, counts, SlabRigHangingCatalog.Family.ENTITY_HANGING, 64);
        expect(helper, counts, SlabRigHangingCatalog.Family.HANGING_SIGN, 25536);
        expect(helper, counts, SlabRigHangingCatalog.Family.CHAIN, 216);
        expect(helper, counts, SlabRigHangingCatalog.Family.LANTERN, 80);
        expect(helper, counts, SlabRigHangingCatalog.Family.FACE_ATTACHED, 768);
        expect(helper, counts, SlabRigHangingCatalog.Family.BELL, 432);
        expect(helper, counts, SlabRigHangingCatalog.Family.STANDING_AND_WALL, 3080);
        expect(helper, counts, SlabRigHangingCatalog.Family.TRAPDOOR, 1344);
        expect(helper, counts, SlabRigHangingCatalog.Family.ROD, 240);
        expect(helper, counts, SlabRigHangingCatalog.Family.AMETHYST_CLUSTER, 96);
        expect(helper, counts, SlabRigHangingCatalog.Family.MULTIFACE, 441);
        expect(helper, counts, SlabRigHangingCatalog.Family.SPELEOTHEM, 48);
        expect(helper, counts, SlabRigHangingCatalog.Family.CEILING_GROWTH, 2359);
        expect(helper, counts, SlabRigHangingCatalog.Family.WALL_ATTACHMENT, 145);
        expect(helper, counts, SlabRigHangingCatalog.Family.WALL_CARPET_STATE, 416);
        expect(helper, counts, SlabRigHangingCatalog.Family.GENERATED_HANGING_STATE, 18);
        expect(helper, counts, SlabRigHangingCatalog.Family.SHELF, 3456);
        expect(helper, counts, SlabRigHangingCatalog.Family.LEASH_KNOT_ENTITY, 1);
        expectSubjectRoutes(helper, snapshot, "minecraft:painting", 16);
        expectSubjectRoutes(helper, snapshot, "minecraft:item_frame", 24);
        expectSubjectRoutes(helper, snapshot, "minecraft:glow_item_frame", 24);
        expectSubjectRoutes(helper, snapshot, "minecraft:oak_hanging_sign", 2128);
        expectSubjectRoutes(helper, snapshot, "minecraft:oak_sign", 80);
        expectSubjectRoutes(helper, snapshot, "minecraft:white_banner", 80);
        expectSubjectRoutes(helper, snapshot, "minecraft:player_head", 80);
        expectSubjectRoutes(helper, snapshot, "minecraft:torch", 20);
        expectSubjectRoutes(helper, snapshot, "minecraft:brain_coral_fan", 20);
        expectSubjectRoutes(helper, snapshot, "minecraft:vine", 93);
        expectSubjectRoutes(helper, snapshot, "minecraft:cocoa_beans", 20);
        expectSubjectRoutes(helper, snapshot, "minecraft:pale_moss_carpet", 416);
        expectSubjectRoutes(helper, snapshot, "minecraft:mangrove_propagule", 18);
        expectSubjectRoutes(helper, snapshot, "minecraft:pale_hanging_moss", 133);
        expectSubjectRoutes(helper, snapshot, "minecraft:glow_berries", 1328);
        expectSubjectRoutes(helper, snapshot, "minecraft:cave_vines_plant", 8);
        expectSubjectRoutes(helper, snapshot, "minecraft:weeping_vines", 857);
        expectSubjectRoutes(helper, snapshot, "minecraft:weeping_vines_plant", 25);
        expectSubjectRoutes(helper, snapshot, "minecraft:end_rod", 48);
        expectSubjectRoutes(helper, snapshot, "minecraft:oak_shelf", 288);
        expectSubjectRoutes(helper, snapshot, "minecraft:lead", 1);
        if (snapshot.routes().size() != 38_740 || snapshot.chainTerminalRoutes().size() != 9732) {
            throw helper.assertionException("RIG-3 route/terminal expansion changed: routes="
                    + snapshot.routes().size() + " terminals=" + snapshot.chainTerminalRoutes().size());
        }
        if (snapshot.routes().stream().noneMatch(route -> route.subjectId().equals("minecraft:oak_sign")
                && route.tags().contains("rotation=15"))
                || snapshot.routes().stream().anyMatch(route -> route.subjectId().equals("minecraft:torch")
                && route.tags().stream().anyMatch(tag -> tag.startsWith("rotation=")))
                || snapshot.chainTerminalRoutes().stream()
                .anyMatch(route -> route.subjectId().equals("minecraft:mangrove_propagule"))) {
            throw helper.assertionException("RIG-3 rotation/origin route distinctions collapsed");
        }
        String tsv = SlabRigHangingCatalog.catalogTsv(snapshot);
        if (tsv.contains("net.minecraft") || tsv.contains(".class")) {
            throw helper.assertionException("mapping-dependent Java class name leaked into RIG-3 identity");
        }
        SlabRigHangingCatalog.Route sample = snapshot.routes().getFirst();
        String changedTerminal = SlabRigHangingCatalog.semanticRouteId(sample.subjectId(),
                sample.family(), sample.actionKind(), sample.effectKind(), sample.actionOrigin(),
                sample.mount(), sample.clickedFace(), sample.supportFrame(), sample.stateContract(),
                !sample.chainTerminal(), sample.delayedObservation(), sample.tags());
        String changedDelay = SlabRigHangingCatalog.semanticRouteId(sample.subjectId(),
                sample.family(), sample.actionKind(), sample.effectKind(), sample.actionOrigin(),
                sample.mount(), sample.clickedFace(), sample.supportFrame(), sample.stateContract(),
                sample.chainTerminal(), !sample.delayedObservation(), sample.tags());
        if (sample.id().equals(changedTerminal) || sample.id().equals(changedDelay)) {
            throw helper.assertionException("RIG-3 route ID omitted verdict-relevant execution fields");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void wallHangingSignRoutesUseExactLateralGeometry(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot snapshot = SlabRigHangingCatalog.snapshot();
        List<SlabRigHangingCatalog.Route> wall = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:oak_hanging_sign"))
                .filter(route -> route.mount().equals("wall_lateral"))
                .toList();
        if (wall.size() != 336) {
            throw helper.assertionException("oak wall hanging-sign matrix expected 336 routes, got "
                    + wall.size());
        }
        for (SlabRigHangingCatalog.Route route : wall) {
            Direction clicked = direction(route.clickedFace());
            Direction facing = direction(stateValue(route, "facing"));
            Direction look = direction(stateValue(route, "look_direction"));
            Direction clickedSupport = direction(stateValue(route, "clicked_support_direction"));
            Direction otherSupport = direction(stateValue(route, "other_support_direction"));
            if (!clicked.getAxis().isHorizontal() || clicked.getAxis() == facing.getAxis()
                    || look.getOpposite() != facing || clickedSupport != clicked.getOpposite()
                    || clickedSupport.getAxis() == facing.getAxis()
                    || otherSupport != clickedSupport.getOpposite()) {
                throw helper.assertionException("wall hanging-sign route violates vanilla lateral geometry: "
                        + route.stateContract());
            }
            if (route.chainTerminal() || route.supportFrame().contains("ABSENT")) {
                throw helper.assertionException("wall hanging-sign route has impossible terminal/support frame: "
                        + route.id());
            }
        }
        List<SlabRigHangingCatalog.Route> ceiling = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:oak_hanging_sign"))
                .filter(route -> route.mount().equals("ceiling"))
                .filter(route -> route.stateContract().startsWith("ceiling_hanging_sign"))
                .toList();
        if (ceiling.size() != 128 || ceiling.stream().noneMatch(route -> route.tags().contains("rotation=15"))
                || ceiling.stream().anyMatch(route -> route.supportFrame().equals("CEILING_TOP_SLAB")
                && !"false".equals(stateValue(route, "attached")))) {
            throw helper.assertionException("ceiling hanging-sign ROTATION_16/secondary/top-slab contract collapsed");
        }
        long attached = ceiling.stream()
                .filter(route -> Boolean.parseBoolean(stateValue(route, "attached"))).count();
        if (attached != 48L || ceiling.size() - attached != 80L
                || ceiling.stream().anyMatch(route -> !Boolean.parseBoolean(
                        stateValue(route, "secondary_use"))
                        && !route.supportFrame().equals("CEILING_TOP_SLAB")
                        && Integer.parseInt(stateValue(route, "rotation")) % 4 != 0)) {
            throw helper.assertionException("direct ceiling-sign attached/cardinal outcome distribution drifted");
        }

        List<SlabRigHangingCatalog.Route> edges = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:oak_hanging_sign"))
                .filter(route -> route.mount().equals("hanging_sign_edge")).toList();
        List<SlabRigHangingCatalog.Route> columns = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:oak_hanging_sign"))
                .filter(route -> route.mount().equals("ceiling_column")).toList();
        if (edges.size() != 1152 || edges.stream().anyMatch(SlabRigHangingCatalog.Route::chainTerminal)
                || columns.size() != 512
                || columns.stream().anyMatch(route -> !route.chainTerminal())) {
            throw helper.assertionException("hanging-sign edge/recursive-column partition changed");
        }
        Map<String, Set<String>> ceilingEdgeOutcomes = new java.util.HashMap<>();
        for (SlabRigHangingCatalog.Route route : edges) {
            if (stateValue(route, "donor_kind").equals("ceiling")) {
                String key = stateValue(route, "donor_orientation") + '|'
                        + stateValue(route, "secondary_use") + '|'
                        + stateValue(route, "input_rotation");
                ceilingEdgeOutcomes.computeIfAbsent(key, ignored -> new HashSet<>())
                        .add(stateValue(route, "attached") + ':' + stateValue(route, "rotation"));
            }
        }
        if (ceilingEdgeOutcomes.size() != 512
                || ceilingEdgeOutcomes.values().stream().anyMatch(outcomes -> outcomes.size() != 1)) {
            throw helper.assertionException("donor ATTACHED changed a ceiling-sign child outcome");
        }
        for (SlabRigHangingCatalog.Route route : columns) {
            int length = Integer.parseInt(stateValue(route, "length"));
            int attachedSteps = stateValue(route, "attached_trace").split(",").length;
            int rotationSteps = stateValue(route, "rotation_trace").split(",").length;
            if (attachedSteps != length || rotationSteps != length
                    || !route.stateContract().contains("recursion=donor_equals_previous_result")) {
                throw helper.assertionException("hanging-sign column lost ordered donor/result recurrence");
            }
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void shelfAndBellRoutesUseExecutableInputs(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot snapshot = SlabRigHangingCatalog.snapshot();
        List<SlabRigHangingCatalog.Route> shelves = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:oak_shelf")).toList();
        Set<String> shelfFaces = new HashSet<>();
        Set<String> shelfParts = new HashSet<>();
        for (SlabRigHangingCatalog.Route route : shelves) {
            shelfFaces.add(route.clickedFace());
            shelfParts.add(stateValue(route, "side_chain"));
            Direction heading = direction(stateValue(route, "player_heading"));
            Direction facing = direction(stateValue(route, "facing"));
            if (facing != heading.getOpposite()
                    || !route.supportFrame().equals("ORIENTATION_ONLY_NO_REQUIRED_SUPPORT")
                    || route.mount().contains("wall")) {
                throw helper.assertionException("shelf click/heading/support semantics collapsed: "
                        + route.stateContract());
            }
        }
        if (shelves.size() != 288
                || !shelfFaces.equals(Set.of("down", "up", "north", "east", "south", "west"))
                || !shelfParts.equals(Set.of("unconnected", "right", "center", "left"))
                || shelves.stream().noneMatch(route -> route.stateContract().contains("fourth_refused_by_cap"))
                || shelves.stream().noneMatch(route -> route.stateContract().contains("power_down_disconnects_neighbor"))) {
            throw helper.assertionException("shelf six-face/side-chain/boundary matrix is incomplete");
        }

        List<SlabRigHangingCatalog.Route> bells = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:bell")).toList();
        if (bells.size() != 432 || bells.stream().anyMatch(route -> route.clickedFace().equals("side"))) {
            throw helper.assertionException("bell route matrix has wrong count or invented clicked face");
        }
        for (SlabRigHangingCatalog.Route route : bells) {
            if (route.mount().equals("wall_double") || route.mount().equals("wall_single")) {
                Direction clicked = direction(route.clickedFace());
                Direction facing = direction(stateValue(route, "facing"));
                if (facing != clicked.getOpposite()) {
                    throw helper.assertionException("bell wall facing is not clickedFace.opposite: "
                            + route.stateContract());
                }
            }
            if (route.mount().equals("floor") && !route.clickedFace().equals("up")
                    || route.mount().equals("ceiling") && !route.clickedFace().equals("down")) {
                throw helper.assertionException("bell vertical face/attachment mapping collapsed");
            }
        }
        if (bells.stream().noneMatch(route -> route.mount().equals("floor_fallback_from_wall_click"))
                || bells.stream().noneMatch(route -> route.mount().equals("ceiling_fallback_from_wall_click"))
                || bells.stream().noneMatch(route -> route.mount().equals("wall_refusal"))) {
            throw helper.assertionException("bell floor/ceiling/refusal fallback routes are missing");
        }
        long doubles = bells.stream().filter(route -> route.mount().equals("wall_double")).count();
        long singles = bells.stream().filter(route -> route.mount().equals("wall_single")).count();
        long floorFallbacks = bells.stream()
                .filter(route -> route.mount().equals("floor_fallback_from_wall_click")).count();
        long ceilingFallbacks = bells.stream()
                .filter(route -> route.mount().equals("ceiling_fallback_from_wall_click")).count();
        long refusals = bells.stream().filter(route -> route.mount().equals("wall_refusal")).count();
        if (doubles != 16L || singles != 24L || floorFallbacks != 160L
                || ceilingFallbacks != 160L || refusals != 40L) {
            throw helper.assertionException("bell mapped outcome partition changed: double=" + doubles
                    + " single=" + singles + " floor=" + floorFallbacks
                    + " ceiling=" + ceilingFallbacks + " refusal=" + refusals);
        }
        for (SlabRigHangingCatalog.Route route : bells) {
            if (route.mount().contains("fallback_from_wall_click")
                    || route.mount().equals("wall_refusal")) {
                if (!route.stateContract().contains("clicked_support=")
                        || !route.stateContract().contains("opposite_support=")
                        || !route.stateContract().contains("below_support=")
                        || !route.stateContract().contains("above_support=")
                        || !route.stateContract().contains("fallback_order=")) {
                    throw helper.assertionException("bell fallback/refusal lost an exact fixture axis");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void clickedFacesOriginsAndDynamicSubcatalogsStayDistinct(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot snapshot = SlabRigHangingCatalog.snapshot();
        for (SlabRigHangingCatalog.Route route : snapshot.routes()) {
            if ((route.family() == SlabRigHangingCatalog.Family.MULTIFACE
                    || route.subjectId().equals("minecraft:vine"))
                    && (route.actionKind() == SlabRigHangingCatalog.ActionKind.PLAYER_USEON_BLOCK
                    || route.actionKind() == SlabRigHangingCatalog.ActionKind.PLAYER_USEON_BLOCK_SEQUENCE)) {
                String[] stateFaces = stateValue(route, "state_faces").split(",");
                String[] clickedFaces = stateValue(route, "ordered_clicked_faces").split(",");
                if (stateFaces.length != clickedFaces.length) {
                    throw helper.assertionException("multiface state/click sequence cardinality differs");
                }
                for (int i = 0; i < stateFaces.length; i++) {
                    if (direction(clickedFaces[i]) != direction(stateFaces[i]).getOpposite()) {
                        throw helper.assertionException("multiface clicked face was conflated with support face: "
                                + route.stateContract());
                    }
                }
            }
        }
        Set<SlabRigHangingCatalog.ActionKind> carpetOrigins = new HashSet<>();
        for (SlabRigHangingCatalog.Route route : snapshot.routes()) {
            if (route.subjectId().equals("minecraft:pale_moss_carpet")) {
                carpetOrigins.add(route.actionKind());
            }
        }
        if (!carpetOrigins.containsAll(Set.of(
                SlabRigHangingCatalog.ActionKind.PLAYER_USEON_BLOCK,
                SlabRigHangingCatalog.ActionKind.PLAYER_PLACEMENT_DERIVED_SECONDARY,
                SlabRigHangingCatalog.ActionKind.PLAYER_DERIVED_NEIGHBOR_UPDATE,
                SlabRigHangingCatalog.ActionKind.BONEMEAL_DERIVED_SECONDARY,
                SlabRigHangingCatalog.ActionKind.GENERATED_STATE_ONLY))) {
            throw helper.assertionException("pale moss placement/topper/neighbor/bonemeal/worldgen origins collapsed");
        }
        requireRoute(helper, snapshot.routes(), "dynamic_subcatalog=minecraft:painting_variant#placeable",
                "executor_obligation=exact_server_registry_snapshot");
        if (snapshot.routes().stream().filter(route -> route.subjectId().equals("minecraft:lead")).count() != 1
                || snapshot.routes().stream().noneMatch(route -> route.subjectId().equals("minecraft:end_rod")
                && route.stateContract().contains("mode=aligned_extension"))
                || snapshot.chainTerminalRoutes().stream()
                .anyMatch(route -> route.subjectId().equals("minecraft:mangrove_propagule"))) {
            throw helper.assertionException("lead/end-rod/generated-mangrove route distinctions collapsed");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainTerminalsAreDerivedAndDirectMeansZeroChain(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot snapshot = SlabRigHangingCatalog.snapshot();
        for (SlabRigHangingCatalog.Route route : snapshot.routes()) {
            boolean playerExecutable = route.actionKind() == SlabRigHangingCatalog.ActionKind.PLAYER_USEON_BLOCK
                    || route.actionKind() == SlabRigHangingCatalog.ActionKind.PLAYER_USEON_BLOCK_SEQUENCE
                    || route.actionKind() == SlabRigHangingCatalog.ActionKind.PLAYER_USEON_ENTITY_EFFECT;
            boolean expected = playerExecutable
                    && route.family() != SlabRigHangingCatalog.Family.CHAIN
                    && route.mount().startsWith("ceiling");
            if (route.chainTerminal() != expected
                    || route.tags().contains("chain_terminal=" + !expected)) {
                throw helper.assertionException("chain terminal was family-hinted instead of route-derived: "
                        + route.id());
            }
        }
        SlabRigHangingCatalog.CaseDefinition direct = SlabRigHangingCatalog.caseAt(snapshot, 0L);
        if (direct.kind() != SlabRigHangingCatalog.CaseKind.DIRECT
                || direct.chainPattern() != null || !direct.chainSupportFrame().equals("none")) {
            throw helper.assertionException("zero-chain normalization is not the DIRECT case kind");
        }
        if (snapshot.terminalChainPatterns().size() != 49
                || snapshot.terminalChainPatterns().stream()
                .anyMatch(pattern -> !pattern.orientation().equals("down"))) {
            throw helper.assertionException("terminal chains are not the exact downward subset");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingEntityRoutesUseEntityEffects(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot snapshot = SlabRigHangingCatalog.snapshot();
        int items = 0;
        int routes = 0;
        for (SlabRigHangingCatalog.CatalogItem item : snapshot.items()) {
            if (item.family() == SlabRigHangingCatalog.Family.ENTITY_HANGING) {
                items++;
                for (SlabRigHangingCatalog.Route route : snapshot.routes()) {
                    if (route.subjectId().equals(item.id())) {
                        routes++;
                        if (route.actionKind()
                                != SlabRigHangingCatalog.ActionKind.PLAYER_USEON_ENTITY_EFFECT
                                || route.effectKind() != SlabRigHangingCatalog.EffectKind.EXACT_ENTITY) {
                            throw helper.assertionException("hanging entity routed as a block effect: "
                                    + route.id());
                        }
                    }
                }
            }
        }
        if (items != 3 || routes == 0) {
            throw helper.assertionException("expected three entity-hanging items with explicit routes; got items="
                    + items + " routes=" + routes);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainAndTopologyAxesReachTheBoundary(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot snapshot = SlabRigHangingCatalog.snapshot();
        if (snapshot.chainMaterials().size() != 9) {
            throw helper.assertionException("expected every 26.2 chain material (9), got "
                    + snapshot.chainMaterials().size());
        }
        if (!snapshot.chainLengths().equals(List.of(1, 2, 3, 5, 16))) {
            throw helper.assertionException("chain boundary set changed: " + snapshot.chainLengths());
        }
        if (snapshot.chainPatterns().size() != 294
                || snapshot.terminalChainPatterns().size() != 49) {
            throw helper.assertionException("expected 294 six-orientation patterns and 49 downward terminal patterns, got "
                    + snapshot.chainPatterns().size());
        }
        for (String orientation : List.of("down", "up", "north", "east", "south", "west")) {
            long count = snapshot.chainPatterns().stream()
                    .filter(pattern -> pattern.orientation().equals(orientation)).count();
            if (count != 49L || SlabRigHangingCatalog.chainSupportFrames(orientation).size() != 4) {
                throw helper.assertionException("chain orientation lost material/length/frame coverage: "
                        + orientation + " count=" + count);
            }
        }
        if (snapshot.topologies().size() != 64
                || snapshot.topologies().stream().noneMatch(t -> "SBSBS".equals(t.recipe()))
                || snapshot.topologies().stream().noneMatch(t -> "SBBS".equals(t.recipe()))) {
            throw helper.assertionException("RIG-3 lost the exact RIG-2 64-topology/SBSBS/SBBS provenance");
        }
        long expected = Math.addExact(
                Math.multiplyExact((long) snapshot.routes().size(), 64L),
                Math.addExact(
                        Math.multiplyExact((long) snapshot.chainPatterns().size() * 4L, 64L),
                        Math.multiplyExact((long) snapshot.chainTerminalRoutes().size()
                                * snapshot.terminalChainPatterns().size()
                                * SlabRigHangingCatalog.ceilingSupportFrames().size(), 64L)));
        if (snapshot.totalCases() != expected) {
            throw helper.assertionException("RIG-3 case cardinality mismatch expected=" + expected
                    + " actual=" + snapshot.totalCases());
        }
        if (!SlabRigHangingCatalog.ceilingSupportFrames().equals(List.of(
                "CEILING_FULL", "CEILING_BOTTOM_SLAB", "CEILING_TOP_SLAB", "CEILING_DOUBLE_SLAB"))) {
            throw helper.assertionException("RIG-3 ceiling support frames lost a slab owner form: "
                    + SlabRigHangingCatalog.ceilingSupportFrames());
        }
        if (snapshot.totalCases() != 124_632_832L || snapshot.pageCount() != 7_789_552) {
            throw helper.assertionException("RIG-3 exact 26.2 case/page cardinality changed: cases="
                    + snapshot.totalCases() + " pages=" + snapshot.pageCount());
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void semanticCaseIdsAndPagingAreStable(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot first = SlabRigHangingCatalog.snapshot();
        SlabRigHangingCatalog.Snapshot second = SlabRigHangingCatalog.snapshot();
        if (!first.catalogHash().equals(second.catalogHash())) {
            throw helper.assertionException("RIG-3 catalog hash changed across identical snapshots");
        }
        SlabRigHangingCatalog.CaseDefinition case0 = SlabRigHangingCatalog.caseAt(first, 0L);
        SlabRigHangingCatalog.CaseDefinition same = SlabRigHangingCatalog.caseAt(first, 0L);
        SlabRigHangingCatalog.CaseDefinition nextTopology = SlabRigHangingCatalog.caseAt(first, 1L);
        if (!case0.equals(same) || case0.id().equals(nextTopology.id())) {
            throw helper.assertionException("semantic case identity is unstable or omitted topology identity");
        }
        SlabRigHangingCatalog.CasePage page1 = SlabRigHangingCatalog.page(first, 1);
        if (page1.cases().isEmpty() || page1.cases().size() > SlabRigHangingCatalog.PAGE_SIZE
                || !page1.cases().getFirst().equals(case0)) {
            throw helper.assertionException("RIG-3 page 1 did not round-trip case index 0");
        }
        SlabRigHangingCatalog.CasePage last = SlabRigHangingCatalog.page(first, first.pageCount());
        if (last.cases().getLast().index() != first.totalCases() - 1L) {
            throw helper.assertionException("RIG-3 last page did not terminate at the final case");
        }
        long directCount = Math.multiplyExact((long) first.routes().size(), first.topologies().size());
        SlabRigHangingCatalog.CaseDefinition firstChain =
                SlabRigHangingCatalog.caseAt(first, directCount);
        long chainOnlyCount = Math.multiplyExact((long) first.chainPatterns().size() * 4L,
                first.topologies().size());
        SlabRigHangingCatalog.CaseDefinition firstTerminal =
                SlabRigHangingCatalog.caseAt(first, directCount + chainOnlyCount);
        if (firstChain.kind() != SlabRigHangingCatalog.CaseKind.CHAIN_ONLY
                || firstTerminal.kind() != SlabRigHangingCatalog.CaseKind.CHAIN_TERMINAL
                || firstChain.id().equals(firstTerminal.id())) {
            throw helper.assertionException("RIG-3 case-kind boundaries or semantic identity collapsed");
        }
        String recomputed = SlabRigHangingCatalog.semanticCaseId(case0.kind(), case0.route(),
                case0.chainPattern(), case0.chainSupportFrame(), case0.topology());
        if (!case0.id().equals(recomputed)) {
            throw helper.assertionException("RIG-3 semantic ID depends on ordinal/page metadata");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void catalogHashNamesExecutableCaseUniverse(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot snapshot = SlabRigHangingCatalog.snapshot();
        String tsv = SlabRigHangingCatalog.catalogTsv(snapshot);
        int hashLineStart = tsv.indexOf("catalog_hash\t");
        int hashLineEnd = hashLineStart < 0 ? -1 : tsv.indexOf('\n', hashLineStart) + 1;
        if (hashLineStart < 0 || hashLineEnd <= hashLineStart) {
            throw helper.assertionException("RIG-3 catalog artifact omits its hash header");
        }
        String canonicalBody = tsv.substring(0, hashLineStart) + tsv.substring(hashLineEnd);
        if (!snapshot.catalogHash().equals(sha256(canonicalBody))) {
            throw helper.assertionException("RIG-3 catalog hash does not digest its canonical body");
        }

        List<String> requiredIdentityLines = List.of(
                "schema\tslabbed-rig-hanging-catalog-v1",
                "case_index_contract_version\trig3-case-index-v1",
                "page_size\t16",
                "page_count\t7789552",
                "case_kind_order\tDIRECT,CHAIN_ONLY,CHAIN_TERMINAL",
                "case_axes_DIRECT\troute,topology",
                "case_axes_CHAIN_ONLY\tchain_pattern,chain_support_frame,topology",
                "case_axes_CHAIN_TERMINAL\tterminal_route,terminal_pattern,terminal_support_frame,topology",
                "chain_support_frames_down\tCEILING_FULL,CEILING_BOTTOM_SLAB,CEILING_TOP_SLAB,CEILING_DOUBLE_SLAB",
                "chain_support_frames_up\tFLOOR_FULL,FLOOR_BOTTOM_SLAB,FLOOR_TOP_SLAB,FLOOR_DOUBLE_SLAB",
                "chain_support_frames_north\tWALL_FULL,WALL_BOTTOM_SLAB,WALL_TOP_SLAB,WALL_DOUBLE_SLAB",
                "chain_support_frames_east\tWALL_FULL,WALL_BOTTOM_SLAB,WALL_TOP_SLAB,WALL_DOUBLE_SLAB",
                "chain_support_frames_south\tWALL_FULL,WALL_BOTTOM_SLAB,WALL_TOP_SLAB,WALL_DOUBLE_SLAB",
                "chain_support_frames_west\tWALL_FULL,WALL_BOTTOM_SLAB,WALL_TOP_SLAB,WALL_DOUBLE_SLAB",
                "terminal_support_frames\tCEILING_FULL,CEILING_BOTTOM_SLAB,CEILING_TOP_SLAB,CEILING_DOUBLE_SLAB");
        List<String> missing = requiredIdentityLines.stream()
                .filter(line -> !tsv.contains(line + "\n"))
                .toList();
        List<String> terminalRouteRows = tsv.lines()
                .filter(line -> line.startsWith("terminal_route\t")).toList();
        List<String> expectedTerminalRouteRows = new ArrayList<>();
        for (int index = 0; index < snapshot.chainTerminalRoutes().size(); index++) {
            expectedTerminalRouteRows.add("terminal_route\t" + index + "\t"
                    + snapshot.chainTerminalRoutes().get(index).id());
        }
        List<String> terminalPatternRows = tsv.lines()
                .filter(line -> line.startsWith("terminal_pattern\t")).toList();
        List<String> expectedTerminalPatternRows = new ArrayList<>();
        for (int index = 0; index < snapshot.terminalChainPatterns().size(); index++) {
            expectedTerminalPatternRows.add("terminal_pattern\t" + index + "\t"
                    + snapshot.terminalChainPatterns().get(index).id());
        }
        if (!missing.isEmpty() || !terminalRouteRows.equals(expectedTerminalRouteRows)
                || !terminalPatternRows.equals(expectedTerminalPatternRows)) {
            throw helper.assertionException("RIG-3 hash omits executable case-universe axes: missing="
                    + missing + " exactTerminalRouteRows="
                    + terminalRouteRows.equals(expectedTerminalRouteRows)
                    + " exactTerminalPatternRows="
                    + terminalPatternRows.equals(expectedTerminalPatternRows));
        }

        int topologyCount = snapshot.topologies().size();
        long directCount = Math.multiplyExact((long) snapshot.routes().size(), topologyCount);
        SlabRigHangingCatalog.CaseDefinition direct0 = SlabRigHangingCatalog.caseAt(snapshot, 0L);
        SlabRigHangingCatalog.CaseDefinition direct1 = SlabRigHangingCatalog.caseAt(snapshot, 1L);
        SlabRigHangingCatalog.CaseDefinition directLast = SlabRigHangingCatalog.caseAt(
                snapshot, directCount - 1L);
        if (direct0.kind() != SlabRigHangingCatalog.CaseKind.DIRECT
                || !direct0.route().equals(snapshot.routes().getFirst())
                || !direct0.topology().equals(snapshot.topologies().getFirst())
                || !direct1.route().equals(direct0.route())
                || !direct1.topology().equals(snapshot.topologies().get(1))
                || !directLast.route().equals(snapshot.routes().getLast())
                || !directLast.topology().equals(snapshot.topologies().getLast())) {
            throw helper.assertionException("RIG-3 direct enumeration order diverges from hashed axes");
        }
        SlabRigHangingCatalog.CaseDefinition chain0 = SlabRigHangingCatalog.caseAt(snapshot, directCount);
        SlabRigHangingCatalog.CaseDefinition chain1 = SlabRigHangingCatalog.caseAt(
                snapshot, directCount + 1L);
        SlabRigHangingCatalog.CaseDefinition chainNextFrame = SlabRigHangingCatalog.caseAt(
                snapshot, directCount + topologyCount);
        SlabRigHangingCatalog.CaseDefinition chainNextPattern = SlabRigHangingCatalog.caseAt(
                snapshot, directCount + 4L * topologyCount);
        if (!chain0.chainPattern().equals(snapshot.chainPatterns().getFirst())
                || !chain0.chainSupportFrame().equals(SlabRigHangingCatalog.chainSupportFrames(
                chain0.chainPattern().orientation()).getFirst())
                || !chain1.chainPattern().equals(chain0.chainPattern())
                || !chain1.chainSupportFrame().equals(chain0.chainSupportFrame())
                || !chain1.topology().equals(snapshot.topologies().get(1))
                || !chainNextFrame.chainPattern().equals(chain0.chainPattern())
                || !chainNextFrame.chainSupportFrame().equals(SlabRigHangingCatalog.chainSupportFrames(
                chain0.chainPattern().orientation()).get(1))
                || !chainNextPattern.chainPattern().equals(snapshot.chainPatterns().get(1))
                || !chainNextPattern.chainSupportFrame().equals(SlabRigHangingCatalog.chainSupportFrames(
                chainNextPattern.chainPattern().orientation()).getFirst())) {
            throw helper.assertionException("RIG-3 chain-only enumeration order diverges from hashed axes");
        }

        long chainOnlyCount = Math.multiplyExact((long) snapshot.chainPatterns().size() * 4L,
                topologyCount);
        SlabRigHangingCatalog.CaseDefinition chainLast = SlabRigHangingCatalog.caseAt(
                snapshot, directCount + chainOnlyCount - 1L);
        if (chainLast.kind() != SlabRigHangingCatalog.CaseKind.CHAIN_ONLY
                || !chainLast.chainPattern().equals(snapshot.chainPatterns().getLast())
                || !chainLast.chainSupportFrame().equals(SlabRigHangingCatalog.chainSupportFrames(
                chainLast.chainPattern().orientation()).getLast())
                || !chainLast.topology().equals(snapshot.topologies().getLast())) {
            throw helper.assertionException("RIG-3 chain-only final witness diverges from hashed axes");
        }
        long terminalStart = directCount + chainOnlyCount;
        long terminalFrameStride = topologyCount;
        long terminalPatternStride = (long) SlabRigHangingCatalog.ceilingSupportFrames().size()
                * topologyCount;
        long terminalRouteStride = (long) snapshot.terminalChainPatterns().size()
                * terminalPatternStride;
        SlabRigHangingCatalog.CaseDefinition terminal0 = SlabRigHangingCatalog.caseAt(snapshot, terminalStart);
        SlabRigHangingCatalog.CaseDefinition terminal1 = SlabRigHangingCatalog.caseAt(
                snapshot, terminalStart + 1L);
        SlabRigHangingCatalog.CaseDefinition terminalNextFrame = SlabRigHangingCatalog.caseAt(
                snapshot, terminalStart + terminalFrameStride);
        SlabRigHangingCatalog.CaseDefinition terminalNextPattern = SlabRigHangingCatalog.caseAt(
                snapshot, terminalStart + terminalPatternStride);
        SlabRigHangingCatalog.CaseDefinition terminalNextRoute = SlabRigHangingCatalog.caseAt(
                snapshot, terminalStart + terminalRouteStride);
        if (!terminal0.route().equals(snapshot.chainTerminalRoutes().getFirst())
                || !terminal0.chainPattern().equals(snapshot.terminalChainPatterns().getFirst())
                || !terminal0.chainSupportFrame().equals(SlabRigHangingCatalog.ceilingSupportFrames().getFirst())
                || !terminal1.route().equals(terminal0.route())
                || !terminal1.chainPattern().equals(terminal0.chainPattern())
                || !terminal1.chainSupportFrame().equals(terminal0.chainSupportFrame())
                || !terminal1.topology().equals(snapshot.topologies().get(1))
                || !terminalNextFrame.chainSupportFrame().equals(
                SlabRigHangingCatalog.ceilingSupportFrames().get(1))
                || !terminalNextPattern.chainPattern().equals(snapshot.terminalChainPatterns().get(1))
                || !terminalNextRoute.route().equals(snapshot.chainTerminalRoutes().get(1))) {
            throw helper.assertionException("RIG-3 terminal enumeration order diverges from hashed axes");
        }
        SlabRigHangingCatalog.CaseDefinition terminalLast = SlabRigHangingCatalog.caseAt(
                snapshot, snapshot.totalCases() - 1L);
        if (terminalLast.kind() != SlabRigHangingCatalog.CaseKind.CHAIN_TERMINAL
                || !terminalLast.route().equals(snapshot.chainTerminalRoutes().getLast())
                || !terminalLast.chainPattern().equals(snapshot.terminalChainPatterns().getLast())
                || !terminalLast.chainSupportFrame().equals(
                SlabRigHangingCatalog.ceilingSupportFrames().getLast())
                || !terminalLast.topology().equals(snapshot.topologies().getLast())) {
            throw helper.assertionException("RIG-3 terminal final witness diverges from hashed axes");
        }

        requireHashMutation(helper, snapshot, canonicalBody,
                reversePrefixedRows(canonicalBody, "terminal_route\t"),
                "terminal route order");
        requireHashMutation(helper, snapshot, canonicalBody,
                reversePrefixedRows(canonicalBody, "terminal_pattern\t"),
                "terminal pattern order");
        List<String> upwardPatternRows = new ArrayList<>();
        snapshot.chainPatterns().stream()
                .filter(pattern -> pattern.orientation().equals("up"))
                .forEach(pattern -> upwardPatternRows.add("terminal_pattern\t"
                        + upwardPatternRows.size() + "\t" + pattern.id()));
        requireHashMutation(helper, snapshot, canonicalBody,
                replacePrefixedRows(canonicalBody, "terminal_pattern\t", upwardPatternRows),
                "same-size terminal pattern subset");
        requireHashMutation(helper, snapshot, canonicalBody,
                swapExact(canonicalBody,
                        "chain_support_frames_down\tCEILING_FULL,CEILING_BOTTOM_SLAB,CEILING_TOP_SLAB,CEILING_DOUBLE_SLAB",
                        "chain_support_frames_up\tFLOOR_FULL,FLOOR_BOTTOM_SLAB,FLOOR_TOP_SLAB,FLOOR_DOUBLE_SLAB"),
                "direction-to-root-frame mapping");
        requireHashMutation(helper, snapshot, canonicalBody,
                canonicalBody.replace(
                        "terminal_support_frames\tCEILING_FULL,CEILING_BOTTOM_SLAB,CEILING_TOP_SLAB,CEILING_DOUBLE_SLAB",
                        "terminal_support_frames\tCEILING_DOUBLE_SLAB,CEILING_TOP_SLAB,CEILING_BOTTOM_SLAB,CEILING_FULL"),
                "terminal frame order");
        requireHashMutation(helper, snapshot, canonicalBody,
                canonicalBody.replace("case_kind_order\tDIRECT,CHAIN_ONLY,CHAIN_TERMINAL",
                        "case_kind_order\tDIRECT,CHAIN_TERMINAL,CHAIN_ONLY"),
                "case kind order");
        requireHashMutation(helper, snapshot, canonicalBody,
                canonicalBody.replace(
                        "case_axes_CHAIN_ONLY\tchain_pattern,chain_support_frame,topology",
                        "case_axes_CHAIN_ONLY\tchain_support_frame,chain_pattern,topology"),
                "within-kind axis order");
        requireHashMutation(helper, snapshot, canonicalBody,
                canonicalBody.replace("page_size\t16\npage_count\t7789552",
                        "page_size\t8\npage_count\t15579104"),
                "page geometry");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void mappedRuntimeSemanticsRejectImpossibleCatalogClaims(GameTestHelper helper) {
        SlabRigHangingCatalog.Snapshot snapshot = SlabRigHangingCatalog.snapshot();
        List<String> violations = new ArrayList<>();

        List<SlabRigHangingCatalog.Route> oakHangingSigns = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:oak_hanging_sign"))
                .toList();
        for (SlabRigHangingCatalog.Route route : oakHangingSigns) {
            if (route.stateContract().startsWith("ceiling_hanging_sign")) {
                boolean secondary = Boolean.parseBoolean(stateValue(route, "secondary_use"));
                boolean attached = Boolean.parseBoolean(stateValue(route, "attached"));
                int rotation = Integer.parseInt(stateValue(route, "rotation"));
                if (!secondary && !route.supportFrame().equals("CEILING_TOP_SLAB")
                        && (attached || rotation % 4 != 0)) {
                    violations.add("nonsecondary full-face hanging sign exposed impossible attached/rotation");
                    break;
                }
            }
        }
        boolean sequenceMissingMappedInputs = oakHangingSigns.stream()
                .filter(route -> route.stateContract().contains("sequence=hanging_sign_column"))
                .anyMatch(route -> !route.stateContract().contains("donor_kind=")
                        || !route.stateContract().contains("aim_rotation=")
                        || !route.stateContract().contains("secondary_use=")
                        || !route.stateContract().contains("attached=")
                        || !route.stateContract().contains("rotation="));
        if (sequenceMissingMappedInputs) {
            violations.add("hanging-sign sequence omitted mapped donor/aim/secondary/outcome axes");
        }

        boolean shelfMaterialGap = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:oak_shelf"))
                .filter(route -> route.stateContract().contains("sequence=pair")
                        || route.stateContract().contains("sequence=triple")
                        || route.stateContract().contains("sequence=fourth_refused_by_cap"))
                .anyMatch(route -> !route.tags().contains(
                        "neighbor_material=any_minecraft_wooden_shelves"));
        if (shelfMaterialGap) {
            violations.add("positive/cap shelf sequence lost the mixed-wood tag contract");
        }

        boolean trapdoorLifecycleGap = snapshot.routes().stream()
                .filter(route -> route.family() == SlabRigHangingCatalog.Family.TRAPDOOR)
                .anyMatch(route -> !route.stateContract().contains(
                        "post=toggle_open_and_power_and_waterlog"));
        if (trapdoorLifecycleGap) {
            violations.add("trapdoor placement route omitted toggle/power/waterlog lifecycle");
        }

        boolean bellClaimsUnsupportedOutcome = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:bell"))
                .anyMatch(route -> route.mount().equals("wall_double")
                        && (route.supportFrame().contains("BOTTOM_SLAB")
                        || route.supportFrame().contains("TOP_SLAB")));
        boolean bellFallbackMissingFixture = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:bell"))
                .filter(route -> route.mount().contains("fallback_from_wall_click"))
                .anyMatch(route -> !route.stateContract().contains("clicked_support=")
                        || !route.stateContract().contains("opposite_support=")
                        || !route.stateContract().contains("below_support=")
                        || !route.stateContract().contains("above_support=")
                        || !route.stateContract().contains("fallback_order="));
        if (bellClaimsUnsupportedOutcome || bellFallbackMissingFixture) {
            violations.add("bell wall context claimed an outcome without exact side/below/above predicates");
        }

        boolean mossBonemeal = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:pale_hanging_moss"))
                .anyMatch(route -> route.actionKind()
                        == SlabRigHangingCatalog.ActionKind.BONEMEAL_DERIVED_SECONDARY
                        && route.stateContract().contains("tip_transition=old_true_to_false,new_true"));
        boolean mossGenerated = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:pale_hanging_moss"))
                .anyMatch(route -> route.actionKind()
                        == SlabRigHangingCatalog.ActionKind.GENERATED_STATE_ONLY
                        && route.stateContract().contains("tip_pattern="));
        boolean inventedCaveGrowth = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:cave_vines_plant"))
                .anyMatch(route -> route.actionKind()
                        == SlabRigHangingCatalog.ActionKind.BONEMEAL_DERIVED_SECONDARY
                        && route.stateContract().contains("cause=bonemeal_growth"));
        boolean caveBerries = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:cave_vines_plant"))
                .anyMatch(route -> route.actionKind()
                        == SlabRigHangingCatalog.ActionKind.BONEMEAL_DERIVED_SECONDARY
                        && route.stateContract().contains("berries=false_to_true")
                        && route.stateContract().contains("length_delta=0"));
        if (!mossBonemeal || !mossGenerated || inventedCaveGrowth || !caveBerries) {
            violations.add("pale-moss TIP or cave-vines berries origin differs from mapped 26.2 behavior");
        }

        List<SlabRigHangingCatalog.Route> moss = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:pale_hanging_moss")).toList();
        long mossBonemealRoutes = moss.stream().filter(route -> route.actionKind()
                == SlabRigHangingCatalog.ActionKind.BONEMEAL_DERIVED_SECONDARY).count();
        if (moss.size() != 133 || mossBonemealRoutes != 108L
                || moss.stream().filter(route -> route.actionKind()
                == SlabRigHangingCatalog.ActionKind.BONEMEAL_DERIVED_SECONDARY)
                .anyMatch(route -> !route.stateContract().contains("tip_scan=target_to_bottom")
                        || !route.stateContract().contains("growth_cells=exactly_one"))) {
            violations.add("pale-moss target-cell/TIP transition matrix is incomplete");
        }

        List<SlabRigHangingCatalog.Route> caveHead = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:glow_berries")).toList();
        long caveEdges = caveHead.stream()
                .filter(route -> route.mount().equals("cave_vine_edge")).count();
        long caveBonemeal = caveHead.stream().filter(route -> route.actionKind()
                == SlabRigHangingCatalog.ActionKind.BONEMEAL_DERIVED_SECONDARY).count();
        long caveMaxAgeNoops = caveHead.stream().filter(route -> route.actionKind()
                == SlabRigHangingCatalog.ActionKind.RANDOM_TICK_DERIVED_SECONDARY)
                .filter(route -> route.stateContract().contains("old_head_age=25"))
                .filter(route -> route.stateContract().contains("result=no_growth")).count();
        long caveGenerated = caveHead.stream().filter(route -> route.actionKind()
                == SlabRigHangingCatalog.ActionKind.GENERATED_STATE_ONLY).count();
        if (caveHead.size() != 1328 || caveEdges != 200L || caveBonemeal != 52L
                || caveMaxAgeNoops != 8L || caveGenerated != 168L
                || caveHead.stream().filter(route -> route.mount().equals("cave_vine_edge"))
                .anyMatch(route -> !stateValue(route, "old_head_berries")
                        .equals(stateValue(route, "old_body_berries")))
                || caveHead.stream().filter(route -> route.actionKind()
                == SlabRigHangingCatalog.ActionKind.BONEMEAL_DERIVED_SECONDARY)
                .anyMatch(route -> !route.stateContract().contains("length_delta=0")
                        || !route.stateContract().contains("age_delta=0"))
                || caveHead.stream().filter(route -> route.actionKind()
                == SlabRigHangingCatalog.ActionKind.GENERATED_STATE_ONLY)
                .anyMatch(route -> !route.stateContract().contains("feature=cave_vine")
                        && !route.stateContract().contains("feature=cave_vine_in_moss"))) {
            violations.add("cave-vine placement/bonemeal/random/worldgen domains are incomplete");
        }
        long caveBodyBonemeal = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:cave_vines_plant"))
                .filter(route -> route.actionKind()
                        == SlabRigHangingCatalog.ActionKind.BONEMEAL_DERIVED_SECONDARY).count();
        if (caveBodyBonemeal != 2L) {
            violations.add("cave-vine body bonemeal success/no-op pair is incomplete");
        }

        List<SlabRigHangingCatalog.Route> weepingHead = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:weeping_vines")).toList();
        long weepingMaxAgeNoops = weepingHead.stream().filter(route -> route.actionKind()
                == SlabRigHangingCatalog.ActionKind.RANDOM_TICK_DERIVED_SECONDARY)
                .filter(route -> route.stateContract().contains("old_head_age=25"))
                .filter(route -> route.stateContract().contains("result=no_growth")).count();
        List<SlabRigHangingCatalog.Route> weepingGenerated = weepingHead.stream()
                .filter(route -> route.actionKind()
                        == SlabRigHangingCatalog.ActionKind.GENERATED_STATE_ONLY).toList();
        List<SlabRigHangingCatalog.Route> weepingBodyGenerated = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals("minecraft:weeping_vines_plant"))
                .filter(route -> route.actionKind()
                        == SlabRigHangingCatalog.ActionKind.GENERATED_STATE_ONLY).toList();
        if (weepingHead.size() != 857 || weepingMaxAgeNoops != 4L
                || weepingGenerated.size() != 153
                || weepingGenerated.stream().noneMatch(route -> stateValue(route, "length").equals("1")
                && stateValue(route, "head_age").equals("17"))
                || weepingGenerated.stream().noneMatch(route -> stateValue(route, "length").equals("17")
                && stateValue(route, "head_age").equals("25"))
                || weepingBodyGenerated.size() != 16
                || weepingBodyGenerated.stream().anyMatch(route -> stateValue(
                route, "column_length").equals("1"))) {
            violations.add("weeping-vine max-age/worldgen head-body domains are incomplete");
        }

        if (!violations.isEmpty()) {
            throw helper.assertionException("RIG-3 mapped-semantics red: "
                    + String.join(" | ", violations));
        }
        helper.succeed();
    }

    private static void expect(GameTestHelper helper,
                               Map<SlabRigHangingCatalog.Family, Integer> counts,
                               SlabRigHangingCatalog.Family family, int expected) {
        int actual = counts.getOrDefault(family, 0);
        if (actual != expected) {
            throw helper.assertionException("RIG-3 " + family + " count expected=" + expected
                    + " actual=" + actual);
        }
    }

    private static void requireRoute(GameTestHelper helper, List<SlabRigHangingCatalog.Route> routes,
                                     String firstTag, String secondTag) {
        boolean found = routes.stream().anyMatch(route -> route.tags().contains(firstTag)
                && route.tags().contains(secondTag));
        if (!found) {
            throw helper.assertionException("missing RIG-3 route tags " + firstTag + " + " + secondTag);
        }
    }

    private static void expectSubjectRoutes(GameTestHelper helper,
                                            SlabRigHangingCatalog.Snapshot snapshot,
                                            String subjectId, int expected) {
        long actual = snapshot.routes().stream()
                .filter(route -> route.subjectId().equals(subjectId)).count();
        if (actual != expected) {
            throw helper.assertionException("RIG-3 subject route count expected=" + expected
                    + " actual=" + actual + " subject=" + subjectId);
        }
    }

    private static String stateValue(SlabRigHangingCatalog.Route route, String key) {
        String prefix = key + "=";
        for (String token : route.stateContract().split(";")) {
            if (token.startsWith(prefix)) {
                return token.substring(prefix.length());
            }
        }
        throw new IllegalArgumentException("route " + route.id() + " lacks state token " + key);
    }

    private static String reversePrefixedRows(String body, String prefix) {
        List<String> lines = new ArrayList<>(body.lines().toList());
        List<Integer> positions = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).startsWith(prefix)) {
                positions.add(index);
                rows.add(lines.get(index));
            }
        }
        for (int index = 0; index < positions.size(); index++) {
            lines.set(positions.get(index), rows.get(rows.size() - 1 - index));
        }
        return String.join("\n", lines) + "\n";
    }

    private static String replacePrefixedRows(String body, String prefix, List<String> replacements) {
        List<String> lines = new ArrayList<>(body.lines().toList());
        int replacement = 0;
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).startsWith(prefix)) {
                if (replacement >= replacements.size()) {
                    throw new IllegalArgumentException("too few replacement rows for " + prefix);
                }
                lines.set(index, replacements.get(replacement++));
            }
        }
        if (replacement != replacements.size()) {
            throw new IllegalArgumentException("replacement row count changed for " + prefix
                    + ": expected=" + replacement + " actual=" + replacements.size());
        }
        return String.join("\n", lines) + "\n";
    }

    private static String swapExact(String body, String first, String second) {
        int firstTab = first.indexOf('\t');
        int secondTab = second.indexOf('\t');
        if (firstTab < 0 || secondTab < 0 || !body.contains(first) || !body.contains(second)) {
            throw new IllegalArgumentException("cannot swap absent identity rows");
        }
        String firstWithSecondValue = first.substring(0, firstTab + 1)
                + second.substring(secondTab + 1);
        String secondWithFirstValue = second.substring(0, secondTab + 1)
                + first.substring(firstTab + 1);
        String placeholder = "__RIG3_CASE_IDENTITY_SWAP__";
        return body.replace(first, placeholder)
                .replace(second, secondWithFirstValue)
                .replace(placeholder, firstWithSecondValue);
    }

    private static void requireHashMutation(GameTestHelper helper,
                                            SlabRigHangingCatalog.Snapshot snapshot,
                                            String canonicalBody,
                                            String mutation,
                                            String label) {
        if (mutation.equals(canonicalBody)
                || sha256(mutation).equals(snapshot.catalogHash())
                || !mutation.contains("total_cases\t" + snapshot.totalCases() + "\n")) {
            throw helper.assertionException("RIG-3 same-total identity mutation escaped hash: " + label);
        }
    }

    private static Direction direction(String value) {
        return Direction.valueOf(value.toUpperCase(Locale.ROOT));
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

    private static SlabRigHangingArtifacts.PaintingEntry painting(
            SlabRigHangingArtifacts.RuntimeSnapshot snapshot, String id) {
        return snapshot.paintings().stream().filter(entry -> entry.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("missing painting " + id));
    }

    private static boolean hasTemporary(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.anyMatch(path -> path.getFileName().toString().contains(".tmp-"));
        }
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Test-owned temporary evidence is best-effort cleanup after assertions have completed.
        }
    }
}
