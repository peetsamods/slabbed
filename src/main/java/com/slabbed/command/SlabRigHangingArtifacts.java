package com.slabbed.command;

import com.slabbed.util.BuildStamp;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * RIG-3B1's world-free bridge from the static exhaustive hanging catalog to the server's live
 * painting-variant registry.
 *
 * <p>The runtime snapshot deliberately records the full registry and separately marks membership in
 * {@code #minecraft:placeable}. Vanilla unpinned painting placement samples that tag; a painting item
 * carrying {@link DataComponents#PAINTING_VARIANT} can name a registry entry afterward. This artifact
 * proves only the exact catalog/registry/component inputs. It is not live-player placement proof.
 */
public final class SlabRigHangingArtifacts {

    public static final String SCHEMA = "slabbed-rig-hanging-runtime-v1";
    public static final String EXECUTION_CONTRACT =
            "rig3b1-full-painting-registry-and-static-catalog-v1";
    public static final String PLAYER_PROOF = "ABSENT";
    public static final String WORLD_MUTATION = "NONE";
    public static final String BACKING_CONTRACT = "vanilla_hanging_support_box_v1";
    public static final String SELECTION_CONTRACT =
            "tagged_survivors_then_largest_area_then_random_then_component_override_then_survives";
    private static final String DIRECTORY = "hanging-catalogs";
    private static final String PREFIX = "hanging-catalog-";
    private static final String SUFFIX = ".tsv";
    private static final String WALL_DIRECTIONS = "north,east,south,west";
    private SlabRigHangingArtifacts() {
    }

    public record PaintingEntry(int index, String id, boolean randomPlaceable,
                                int widthBlocks, int heightBlocks, int areaBlocks,
                                String assetId, String componentType, String componentValue,
                                String wallDirections, int lateralMin, int lateralMax,
                                int verticalMin, int verticalMax, int backingCellCount,
                                String backingContract) {
    }

    public record RuntimeSnapshot(String schema, String executionIdentity,
                                  String executionContract, String playerProof,
                                  String worldMutation, String catalogHash,
                                  String catalogTsvSha256, long catalogBytes,
                                  String minecraftVersion, String runtimeContentSha256,
                                  String paintingRegistryId, String placeableTagId,
                                  String paintingComponentId, String paintingRegistryHash,
                                  int paintingVariantCount, int randomPlaceableCount,
                                  List<PaintingEntry> paintings, String canonicalTsv) {
        public RuntimeSnapshot {
            paintings = List.copyOf(paintings);
            Objects.requireNonNull(canonicalTsv, "canonicalTsv");
        }
    }

    public record WrittenArtifact(Path path, String executionIdentity,
                                  String fileSha256, long byteCount) {
    }

    /** Takes one fail-closed, deterministically ordered snapshot from the supplied server registry. */
    public static RuntimeSnapshot snapshot(SlabRigHangingCatalog.Snapshot catalog,
                                           RegistryAccess registryAccess) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(registryAccess, "registryAccess");
        validateCatalog(catalog);
        if (!BuildStamp.hasExactRuntimeContent()) {
            throw new IllegalStateException(
                    "exact runtime content digest unavailable; refusing RIG-3B1 evidence");
        }
        String minecraftVersion = SharedConstants.getCurrentVersion().id();

        Registry<PaintingVariant> registry = registryAccess.lookupOrThrow(Registries.PAINTING_VARIANT);
        HolderSet.Named<PaintingVariant> placeable = registry.get(PaintingVariantTags.PLACEABLE)
                .orElseThrow(() -> new IllegalStateException(
                        "painting registry is missing required #placeable tag"));
        if (!placeable.isBound() || placeable.size() == 0) {
            throw new IllegalStateException("painting #placeable tag is unbound or empty");
        }

        Identifier componentIdentifier = BuiltInRegistries.DATA_COMPONENT_TYPE
                .getKey(DataComponents.PAINTING_VARIANT);
        if (componentIdentifier == null) {
            throw new IllegalStateException("painting variant data component is not registered");
        }
        String componentId = componentIdentifier.toString();
        String registryId = Registries.PAINTING_VARIANT.identifier().toString();
        String tagId = PaintingVariantTags.PLACEABLE.location().toString();

        List<Holder.Reference<PaintingVariant>> references = registry.listElements()
                .sorted(Comparator.comparing(reference -> reference.key().identifier().toString()))
                .toList();
        if (references.isEmpty()) {
            throw new IllegalStateException("painting registry is empty");
        }

        List<PaintingEntry> paintings = new ArrayList<>(references.size());
        for (int index = 0; index < references.size(); index++) {
            Holder.Reference<PaintingVariant> reference = references.get(index);
            if (!reference.isBound()) {
                throw new IllegalStateException("unbound painting registry entry: " + reference.key());
            }
            String id = reference.key().identifier().toString();
            PaintingVariant variant = reference.value();
            if (variant.width() < 1 || variant.height() < 1
                    || variant.area() != Math.multiplyExact(variant.width(), variant.height())) {
                throw new IllegalStateException("invalid painting footprint for " + id);
            }

            ItemStack configured = new ItemStack(Items.PAINTING);
            configured.set(DataComponents.PAINTING_VARIANT, reference);
            Holder<PaintingVariant> roundTrip = configured.get(DataComponents.PAINTING_VARIANT);
            if (roundTrip == null || roundTrip.unwrapKey().isEmpty()
                    || !roundTrip.unwrapKey().orElseThrow().equals(reference.key())) {
                throw new IllegalStateException("painting component round-trip failed for " + id);
            }

            paintings.add(new PaintingEntry(index, id, placeable.contains(reference),
                    variant.width(), variant.height(), variant.area(), variant.assetId().toString(),
                    componentId, id, WALL_DIRECTIONS,
                    -((variant.width() - 1) / 2), variant.width() / 2,
                    -((variant.height() - 1) / 2), variant.height() / 2,
                    variant.area(), BACKING_CONTRACT));
        }
        long uniqueIds = paintings.stream().map(PaintingEntry::id).distinct().count();
        int randomPlaceableCount = Math.toIntExact(
                paintings.stream().filter(PaintingEntry::randomPlaceable).count());
        if (uniqueIds != paintings.size() || randomPlaceableCount != placeable.size()) {
            throw new IllegalStateException("painting registry/tag snapshot is incomplete or duplicated");
        }

        String catalogTsv = SlabRigHangingCatalog.catalogTsv(catalog);
        byte[] catalogBytes = catalogTsv.getBytes(StandardCharsets.UTF_8);
        String paintingRows = paintingRows(paintings);
        String paintingHash = sha256(paintingRows);
        String body = canonicalBody(catalog, catalogTsv, sha256(catalogBytes), catalogBytes.length,
                minecraftVersion, BuildStamp.RUNTIME_CONTENT_SHA256, registryId, tagId, componentId,
                paintingHash, randomPlaceableCount, paintings, paintingRows);
        String executionIdentity = sha256(body);
        int firstLineEnd = body.indexOf('\n') + 1;
        String tsv = body.substring(0, firstLineEnd)
                + "execution_identity\t" + executionIdentity + "\n"
                + body.substring(firstLineEnd);
        return new RuntimeSnapshot(SCHEMA, executionIdentity, EXECUTION_CONTRACT, PLAYER_PROOF,
                WORLD_MUTATION, catalog.catalogHash(), sha256(catalogBytes), catalogBytes.length,
                minecraftVersion, BuildStamp.RUNTIME_CONTENT_SHA256, registryId, tagId, componentId,
                paintingHash, paintings.size(),
                randomPlaceableCount, paintings, tsv);
    }

    public static WrittenArtifact write(RuntimeSnapshot snapshot) throws IOException {
        return write(SlabRigCaseArtifacts.defaultRoot(), snapshot);
    }

    /** Test seam; production command input cannot choose this root or any filename/path component. */
    public static WrittenArtifact write(Path root, RuntimeSnapshot snapshot) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(snapshot, "snapshot");
        validateRuntimeSnapshot(snapshot);

        byte[] expected = snapshot.canonicalTsv().getBytes(StandardCharsets.UTF_8);
        Path normalizedRoot = root.toAbsolutePath().normalize();
        prepareDirectory(normalizedRoot);
        Path directory = normalizedRoot.resolve(DIRECTORY);
        prepareDirectory(directory);
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("refusing symlinked hanging artifact directory: " + directory);
        }
        Path rootReal = normalizedRoot.toRealPath();
        Path directoryReal = directory.toRealPath();
        if (!directoryReal.equals(rootReal.resolve(DIRECTORY))) {
            throw new IOException("hanging artifact directory escapes exact rig root: " + directory);
        }

        String expectedName = PREFIX + snapshot.executionIdentity() + SUFFIX;
        Path target = directory.resolve(expectedName).toAbsolutePath().normalize();
        if (!directory.equals(target.getParent()) || !expectedName.equals(target.getFileName().toString())) {
            throw new IOException("invalid hanging artifact target path: " + target);
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            verifyExisting(target, expected);
            return written(target, snapshot, expected);
        }

        Path temporary = directory.resolve("." + expectedName + ".tmp-" + UUID.randomUUID())
                .toAbsolutePath().normalize();
        if (!directory.equals(temporary.getParent())) {
            throw new IOException("invalid hanging artifact temporary path: " + temporary);
        }
        boolean temporaryOwnedByThisCall = false;
        try {
            FileChannel opened = FileChannel.open(temporary,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            temporaryOwnedByThisCall = true;
            try (FileChannel channel = opened) {
                ByteBuffer buffer = ByteBuffer.wrap(expected);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            verifyRegularExact(temporary, expected, "temporary hanging artifact");
            try {
                // Same-directory hard-link publication is atomic and, unlike rename on Unix, can
                // never replace an existing target. The forced temporary already contains all bytes.
                Files.createLink(target, temporary);
            } catch (FileAlreadyExistsException raced) {
                verifyExisting(target, expected);
            }
            // Fail closed without deleting a final path after publication: another process could
            // race between any same-file check and deletion. A failed readback remains conspicuous
            // collision evidence and is never reported as success.
            verifyRegularExact(target, expected, "published hanging artifact");
            return written(target, snapshot, expected);
        } finally {
            if (temporaryOwnedByThisCall) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static String canonicalBody(SlabRigHangingCatalog.Snapshot catalog,
                                        String catalogTsv, String catalogTsvSha256,
                                        long catalogBytes, String minecraftVersion,
                                        String runtimeContentSha256, String registryId, String tagId,
                                        String componentId, String paintingHash,
                                        int randomPlaceableCount, List<PaintingEntry> paintings,
                                        String paintingRows) {
        StringBuilder out = new StringBuilder(catalogTsv.length() + paintingRows.length() + 1024);
        out.append("schema\t").append(SCHEMA).append('\n');
        out.append("execution_contract\t").append(EXECUTION_CONTRACT).append('\n');
        out.append("player_proof\t").append(PLAYER_PROOF).append('\n');
        out.append("proof_scope\tCATALOG_ONLY\n");
        out.append("world_mutation\t").append(WORLD_MUTATION).append('\n');
        out.append("minecraft_version\t").append(minecraftVersion).append('\n');
        out.append("runtime_content_sha256\t").append(runtimeContentSha256).append('\n');
        out.append("catalog_hash\t").append(catalog.catalogHash()).append('\n');
        out.append("catalog_tsv_sha256\t").append(catalogTsvSha256).append('\n');
        out.append("catalog_bytes\t").append(catalogBytes).append('\n');
        out.append("painting_registry\t").append(registryId).append('\n');
        out.append("placeable_tag\t").append(tagId).append('\n');
        out.append("painting_component\t").append(componentId).append('\n');
        out.append("painting_selection_contract\t").append(SELECTION_CONTRACT).append('\n');
        out.append("painting_component_boundary\tregistry_entry_encoded_not_live_placement_proof\n");
        out.append("painting_tooltip_fields\texcluded_non_execution_metadata\n");
        out.append("painting_lateral_axis\tfacing.getCounterClockWise\n");
        out.append("painting_entity_plane_offset\t-0.46875\n");
        out.append("painting_depth\t0.0625\n");
        out.append("painting_even_dimension_center_offset\t0.5\n");
        out.append("painting_support_box_facing_shift\t-0.5\n");
        out.append("painting_support_box_deflate\t0.0000001\n");
        out.append("painting_registry_hash\t").append(paintingHash).append('\n');
        out.append("painting_variant_count\t").append(paintings.size()).append('\n');
        out.append("random_placeable_count\t").append(randomPlaceableCount).append('\n');
        out.append("backing_contract\t").append(BACKING_CONTRACT).append('\n');
        out.append("catalog_tsv_begin\n");
        out.append(catalogTsv);
        if (!catalogTsv.endsWith("\n")) {
            out.append('\n');
        }
        out.append("catalog_tsv_end\n");
        out.append(paintingRows);
        return out.toString();
    }

    private static String paintingRows(List<PaintingEntry> paintings) {
        StringBuilder out = new StringBuilder();
        out.append("painting_columns\tindex\tid\trandom_placeable\twidth_blocks\theight_blocks")
                .append("\tarea_blocks\tasset_id\tcomponent_type\tcomponent_value")
                .append("\twall_directions\tlateral_min\tlateral_max\tvertical_min")
                .append("\tvertical_max\tbacking_cell_count\tbacking_contract\n");
        for (PaintingEntry entry : paintings) {
            out.append("painting\t").append(entry.index()).append('\t').append(entry.id())
                    .append('\t').append(entry.randomPlaceable())
                    .append('\t').append(entry.widthBlocks())
                    .append('\t').append(entry.heightBlocks())
                    .append('\t').append(entry.areaBlocks())
                    .append('\t').append(entry.assetId())
                    .append('\t').append(entry.componentType())
                    .append('\t').append(entry.componentValue())
                    .append('\t').append(entry.wallDirections())
                    .append('\t').append(entry.lateralMin())
                    .append('\t').append(entry.lateralMax())
                    .append('\t').append(entry.verticalMin())
                    .append('\t').append(entry.verticalMax())
                    .append('\t').append(entry.backingCellCount())
                    .append('\t').append(entry.backingContract()).append('\n');
        }
        return out.toString();
    }

    private static WrittenArtifact written(Path target, RuntimeSnapshot snapshot, byte[] expected) {
        return new WrittenArtifact(target, snapshot.executionIdentity(), sha256(expected), expected.length);
    }

    private static void validateCatalog(SlabRigHangingCatalog.Snapshot catalog) {
        if (!SlabRigHangingCatalog.SCHEMA.equals(catalog.schema())) {
            throw new IllegalArgumentException("unexpected hanging catalog schema: " + catalog.schema());
        }
        requireHash(catalog.catalogHash(), "catalog hash");
        String tsv = SlabRigHangingCatalog.catalogTsv(catalog);
        if (!tsv.startsWith("schema\t" + SlabRigHangingCatalog.SCHEMA + "\n")
                || !tsv.contains("\ncatalog_hash\t" + catalog.catalogHash() + "\n")) {
            throw new IllegalArgumentException("hanging catalog serialization does not bind its identity");
        }
    }

    private static void validateRuntimeSnapshot(RuntimeSnapshot snapshot) {
        if (!SCHEMA.equals(snapshot.schema()) || !EXECUTION_CONTRACT.equals(snapshot.executionContract())
                || !PLAYER_PROOF.equals(snapshot.playerProof())
                || !WORLD_MUTATION.equals(snapshot.worldMutation())) {
            throw new IllegalArgumentException("invalid RIG-3B1 runtime snapshot contract");
        }
        requireHash(snapshot.executionIdentity(), "execution identity");
        requireHash(snapshot.catalogHash(), "catalog hash");
        requireHash(snapshot.catalogTsvSha256(), "catalog TSV hash");
        requireHash(snapshot.paintingRegistryHash(), "painting registry hash");
        requireHash(snapshot.runtimeContentSha256(), "runtime content hash");
        if (!BuildStamp.RUNTIME_CONTENT_SHA256.equals(snapshot.runtimeContentSha256())
                || !SharedConstants.getCurrentVersion().id().equals(snapshot.minecraftVersion())
                || !"minecraft:painting_variant".equals(snapshot.paintingRegistryId())
                || !"minecraft:placeable".equals(snapshot.placeableTagId())
                || !"minecraft:painting/variant".equals(snapshot.paintingComponentId())) {
            throw new IllegalArgumentException("RIG-3B1 runtime/registry identity is not current and exact");
        }
        String body = removeExecutionIdentity(snapshot.canonicalTsv(), snapshot.executionIdentity());
        if (!snapshot.executionIdentity().equals(sha256(body))) {
            throw new IllegalArgumentException("RIG-3B1 execution identity does not match canonical bytes");
        }
        if (snapshot.paintingVariantCount() != snapshot.paintings().size()
                || snapshot.randomPlaceableCount() <= 0
                || snapshot.randomPlaceableCount() > snapshot.paintingVariantCount()) {
            throw new IllegalArgumentException("invalid RIG-3B1 painting counters");
        }
    }

    private static String removeExecutionIdentity(String tsv, String identity) {
        String marker = "execution_identity\t" + identity + "\n";
        int firstLineEnd = tsv.indexOf('\n') + 1;
        if (firstLineEnd <= 0 || !tsv.startsWith("schema\t" + SCHEMA + "\n")
                || !tsv.startsWith(marker, firstLineEnd)) {
            throw new IllegalArgumentException("malformed RIG-3B1 canonical TSV");
        }
        return tsv.substring(0, firstLineEnd) + tsv.substring(firstLineEnd + marker.length());
    }

    private static void prepareDirectory(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path))) {
            throw new IOException("refusing non-directory or symlinked rig artifact path: " + path);
        }
        Files.createDirectories(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("rig artifact directory is not an owned real directory: " + path);
        }
    }

    private static void verifyExisting(Path target, byte[] expected) throws IOException {
        if (Files.isSymbolicLink(target)
                || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("content-address target is not a regular owned file: " + target);
        }
        verifyRegularExact(target, expected, "existing hanging artifact");
    }

    private static void verifyRegularExact(Path path, byte[] expected, String label) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " is not a regular file: " + path);
        }
        byte[] actual = Files.readAllBytes(path);
        if (!MessageDigest.isEqual(actual, expected)) {
            throw new IOException("content-address collision/refusal at " + path);
        }
    }

    private static void requireHash(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be 64 lower-case hex characters");
        }
    }

    private static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
