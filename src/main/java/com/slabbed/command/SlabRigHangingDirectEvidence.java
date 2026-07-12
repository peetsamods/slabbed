package com.slabbed.command;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TextComponentTagVisitor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Exact, registry-aware evidence values shared by B2B1 execution, finalization, and clear validation. */
public final class SlabRigHangingDirectEvidence {

    public static final String ENTITY_STORE = "NOT_APPLICABLE_ENTITY";
    public static final String ENTITY_LIVE_DY = "NOT_APPLICABLE_ENTITY";

    private SlabRigHangingDirectEvidence() {
    }

    public record VecBits(long x, long y, long z) {
        public static VecBits of(Vec3 value) {
            return new VecBits(Double.doubleToRawLongBits(value.x),
                    Double.doubleToRawLongBits(value.y), Double.doubleToRawLongBits(value.z));
        }
    }

    public record BoxBits(long minX, long minY, long minZ,
                          long maxX, long maxY, long maxZ) {
        public static BoxBits of(AABB value) {
            return new BoxBits(Double.doubleToRawLongBits(value.minX),
                    Double.doubleToRawLongBits(value.minY), Double.doubleToRawLongBits(value.minZ),
                    Double.doubleToRawLongBits(value.maxX), Double.doubleToRawLongBits(value.maxY),
                    Double.doubleToRawLongBits(value.maxZ));
        }
    }

    public record CellEvidence(BlockPos pos, String blockState, String blockEntityType,
                               String blockEntityNbtSha256, long liveDyBits, long storedDyBits,
                               String markers) {
        public CellEvidence {
            pos = Objects.requireNonNull(pos, "pos").immutable();
            Objects.requireNonNull(blockState, "blockState");
            Objects.requireNonNull(blockEntityType, "blockEntityType");
            Objects.requireNonNull(blockEntityNbtSha256, "blockEntityNbtSha256");
            Objects.requireNonNull(markers, "markers");
        }
    }

    /** Strict decoded form of the nine-field full-cell artifact. */
    public record StoredCellIdentity(BlockPos pos, String blockState, String blockEntityType,
                                     String blockEntityNbtSha256, long liveDyBits,
                                     long storedDyBits, String markers) {
        public StoredCellIdentity {
            pos = Objects.requireNonNull(pos, "pos").immutable();
            Objects.requireNonNull(blockState, "blockState");
            Objects.requireNonNull(blockEntityType, "blockEntityType");
            Objects.requireNonNull(blockEntityNbtSha256, "blockEntityNbtSha256");
            Objects.requireNonNull(markers, "markers");
        }
    }

    public record PaintingEvidence(UUID uuid, String type, String variantId,
                                   String componentVariantId, BlockPos attachment, String facing,
                                   VecBits position, BoxBits bounds, boolean survives,
                                   boolean alive, boolean removed, String removalReason,
                                   String nbtSha256, String identityFingerprint) {
        public PaintingEvidence {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(variantId, "variantId");
            Objects.requireNonNull(componentVariantId, "componentVariantId");
            attachment = Objects.requireNonNull(attachment, "attachment").immutable();
            Objects.requireNonNull(facing, "facing");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(removalReason, "removalReason");
            Objects.requireNonNull(nbtSha256, "nbtSha256");
            Objects.requireNonNull(identityFingerprint, "identityFingerprint");
        }
    }

    public record ItemEvidence(UUID uuid, String type, String itemId, int count,
                               String stackSha256, VecBits position, BoxBits bounds,
                               boolean alive, boolean removed, String removalReason,
                               String nbtSha256, String identityFingerprint) {
        public ItemEvidence {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(itemId, "itemId");
            if (count < 0) {
                throw new IllegalArgumentException("item count cannot be negative");
            }
            Objects.requireNonNull(stackSha256, "stackSha256");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(removalReason, "removalReason");
            Objects.requireNonNull(nbtSha256, "nbtSha256");
            Objects.requireNonNull(identityFingerprint, "identityFingerprint");
        }
    }

    public static CellEvidence cell(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        String blockEntityType = blockEntity == null ? "NONE"
                : String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()));
        String blockEntityNbt = blockEntity == null ? "NONE"
                : sortedSnbt(blockEntity.saveWithFullMetadata(world.registryAccess()));
        return new CellEvidence(pos, state.toString(), blockEntityType, sha256(blockEntityNbt),
                Double.doubleToRawLongBits(SlabSupport.getYOffset(world, pos, state)),
                Double.doubleToRawLongBits(SlabAnchorAttachment.storedPlacementDy(world, pos)),
                markerFingerprint(world, pos, state));
    }

    /** Stable full cell/store/marker identity used for confirmed-authorship and clear refusal. */
    public static String cellIdentityFingerprint(CellEvidence evidence) {
        return sha256(cellIdentityCanonical(evidence));
    }

    /** Exact attachment/store identity, separate from block deletion authority. */
    public static String attachmentIdentityFingerprint(CellEvidence evidence) {
        return sha256(attachmentIdentityCanonical(evidence));
    }

    /** Canonical full cell evidence bytes addressed by {@link #cellIdentityFingerprint}. */
    public static String cellIdentityCanonical(CellEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        return evidence.pos().getX() + "\0" + evidence.pos().getY() + "\0"
                + evidence.pos().getZ() + "\0" + evidence.blockState() + "\0"
                + evidence.blockEntityType() + "\0" + evidence.blockEntityNbtSha256() + "\0"
                + Long.toUnsignedString(evidence.liveDyBits()) + "\0"
                + Long.toUnsignedString(evidence.storedDyBits()) + "\0" + evidence.markers();
    }

    /** Canonical attachment identity derived from the same full cell evidence. */
    public static String attachmentIdentityCanonical(CellEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        return evidence.pos().getX() + "\0" + evidence.pos().getY() + "\0"
                + evidence.pos().getZ() + "\0"
                + Long.toUnsignedString(evidence.storedDyBits()) + "\0" + evidence.markers();
    }

    /**
     * Strict inverse proof for the one stored full-cell artifact and its paired derived attachment hash.
     * This makes a second attachment artifact redundant without weakening fresh reconstruction.
     */
    public static void verifyCellAndAttachmentArtifact(BlockPos expectedPos,
                                                       String expectedCellFingerprint,
                                                       String expectedAttachmentFingerprint,
                                                       byte[] canonicalBytes) {
        Objects.requireNonNull(expectedPos, "expectedPos");
        Objects.requireNonNull(expectedCellFingerprint, "expectedCellFingerprint");
        Objects.requireNonNull(expectedAttachmentFingerprint, "expectedAttachmentFingerprint");
        Objects.requireNonNull(canonicalBytes, "canonicalBytes");
        if (!SlabRigHangingDirectState.sha256(canonicalBytes).equals(expectedCellFingerprint)) {
            throw new IllegalArgumentException("cell artifact bytes disagree with the stored fingerprint");
        }
        StoredCellIdentity stored = parseCellIdentityArtifact(canonicalBytes);
        if (!stored.pos().equals(expectedPos)) {
            throw new IllegalArgumentException("cell artifact position disagrees with the stored receipt");
        }
        String attachmentCanonical = stored.pos().getX() + "\0" + stored.pos().getY() + "\0"
                + stored.pos().getZ() + "\0" + Long.toUnsignedString(stored.storedDyBits())
                + "\0" + stored.markers();
        if (!sha256(attachmentCanonical).equals(expectedAttachmentFingerprint)) {
            throw new IllegalArgumentException(
                    "derived attachment fingerprint disagrees with full cell evidence");
        }
    }

    /** Strictly decodes and canonicality-checks the full nine-field cell identity grammar. */
    public static StoredCellIdentity parseCellIdentityArtifact(byte[] canonicalBytes) {
        Objects.requireNonNull(canonicalBytes, "canonicalBytes");
        final String canonical;
        try {
            canonical = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(canonicalBytes)).toString();
        } catch (java.nio.charset.CharacterCodingException malformed) {
            throw new IllegalArgumentException("cell artifact is not strict UTF-8", malformed);
        }
        String[] fields = canonical.split("\0", -1);
        if (fields.length != 9) {
            throw new IllegalArgumentException("cell artifact must contain exactly nine fields");
        }
        int x = parseCanonicalInt(fields[0], "x");
        int y = parseCanonicalInt(fields[1], "y");
        int z = parseCanonicalInt(fields[2], "z");
        if (fields[3].isEmpty() || fields[4].isEmpty() || fields[5].isEmpty()
                || fields[8].isEmpty()) {
            throw new IllegalArgumentException("cell artifact contains an empty semantic field");
        }
        long liveDyBits = parseCanonicalUnsignedLong(fields[6], "live-dy bits");
        long storedDyBits = parseCanonicalUnsignedLong(fields[7], "stored-dy bits");
        return new StoredCellIdentity(new BlockPos(x, y, z), fields[3], fields[4], fields[5],
                liveDyBits, storedDyBits, fields[8]);
    }

    private static int parseCanonicalInt(String encoded, String label) {
        try {
            int value = Integer.parseInt(encoded);
            if (!Integer.toString(value).equals(encoded)) {
                throw new IllegalArgumentException("non-canonical cell " + label);
            }
            return value;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid cell " + label, failure);
        }
    }

    private static long parseCanonicalUnsignedLong(String encoded, String label) {
        try {
            long value = Long.parseUnsignedLong(encoded);
            if (!Long.toUnsignedString(value).equals(encoded)) {
                throw new IllegalArgumentException("non-canonical " + label);
            }
            return value;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid " + label, failure);
        }
    }

    /** Whether the cell currently carries any Slabbed store/marker that needs the attachment clear lane. */
    public static boolean hasAttachmentEvidence(CellEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        boolean stored = !Double.isNaN(Double.longBitsToDouble(evidence.storedDyBits()));
        return stored || !evidence.markers().equals("anchored=false,frozen=false,compound=false,"
                + "sideLower=false,sideUpper=false,sideDouble=false,ownerTop=false,"
                + "persistentCarrier=false");
    }

    /** Valid both before ALLOW_LOAD insertion and after tracking confirmation. */
    public static PaintingEvidence painting(ServerLevel world, Painting painting) {
        Holder<PaintingVariant> variant = painting.getVariant();
        ResourceKey<PaintingVariant> variantKey = variant.unwrapKey()
                .orElseThrow(() -> new IllegalStateException("painting holder is not registry-backed"));
        Holder<PaintingVariant> component = painting.get(DataComponents.PAINTING_VARIANT);
        ResourceKey<PaintingVariant> componentKey = component == null ? null
                : component.unwrapKey().orElse(null);
        if (componentKey == null || !variantKey.equals(componentKey)) {
            throw new IllegalStateException("painting final holder/component disagreement");
        }
        String type = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(painting.getType()));
        String variantId = variantKey.identifier().toString();
        String componentId = componentKey.identifier().toString();
        VecBits position = VecBits.of(painting.position());
        BoxBits bounds = BoxBits.of(painting.getBoundingBox());
        String nbtHash = sha256(sortedSnbt(saveEntity(world, painting)));
        String identity = sha256(type + '\0' + painting.getUUID() + '\0' + variantId + '\0'
                + componentId + '\0' + painting.getPos().toShortString() + '\0'
                + painting.getDirection().getName() + '\0' + position + '\0' + bounds);
        return new PaintingEvidence(painting.getUUID(), type, variantId, componentId,
                painting.getPos(), painting.getDirection().getName(), position, bounds,
                painting.survives(), painting.isAlive(), painting.isRemoved(),
                String.valueOf(painting.getRemovalReason()), nbtHash, identity);
    }

    /** Valid before insertion; clear validation later uses the semantic stack fingerprint, not position. */
    public static ItemEvidence item(ServerLevel world, ItemEntity item) {
        ItemStack stack = item.getItem();
        String type = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(item.getType()));
        String itemId = stack.isEmpty() ? "minecraft:air"
                : String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        String stackHash = itemStackSha256(world, stack);
        VecBits position = VecBits.of(item.position());
        BoxBits bounds = BoxBits.of(item.getBoundingBox());
        String nbtHash = sha256(sortedSnbt(saveEntity(world, item)));
        String identity = sha256(type + '\0' + itemId + '\0' + stack.getCount() + '\0' + stackHash);
        return new ItemEvidence(item.getUUID(), type, itemId, stack.getCount(), stackHash,
                position, bounds, item.isAlive(), item.isRemoved(),
                String.valueOf(item.getRemovalReason()), nbtHash, identity);
    }

    public static String itemStackSha256(ServerLevel world, ItemStack stack) {
        Tag encoded = ItemStack.CODEC.encodeStart(
                        world.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack)
                .getOrThrow(error -> new IllegalStateException("item stack encoding failed: " + error));
        return sha256(new TextComponentTagVisitor("", TextComponentTagVisitor.PlainStyling.INSTANCE, true)
                .visit(encoded).getString());
    }

    public static CompoundTag saveEntity(ServerLevel world, Entity entity) {
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        TagValueOutput output = TagValueOutput.createWithContext(problems, world.registryAccess());
        // Entity.save() deliberately returns false for non-saveable removal reasons such as
        // DISCARDED. The drop hook runs after Painting.discard(), so use the public field serializer
        // and add the registry id explicitly; otherwise the exact causal source cannot be evidenced.
        entity.saveWithoutId(output);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("entity NBT save failed: " + problems.getTreeReport());
        }
        CompoundTag result = output.buildResult();
        result.putString("id", String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())));
        return result;
    }

    public static String markerFingerprint(ServerLevel world, BlockPos pos, BlockState state) {
        return "anchored=" + SlabAnchorAttachment.isAnchored(world, pos)
                + ",frozen=" + SlabAnchorAttachment.isFrozenFlat(world, pos)
                + ",compound=" + SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)
                + ",sideLower=" + SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                + ",sideUpper=" + SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)
                + ",sideDouble=" + SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)
                + ",ownerTop=" + SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)
                + ",persistentCarrier="
                + SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state);
    }

    public static String sortedSnbt(CompoundTag tag) {
        return new TextComponentTagVisitor("", TextComponentTagVisitor.PlainStyling.INSTANCE, true)
                .visit(tag).getString();
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
