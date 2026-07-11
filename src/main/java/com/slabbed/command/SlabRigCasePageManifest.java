package com.slabbed.command;

import com.slabbed.util.BuildStamp;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Deterministic, immutable RIG-2 planned/finalized page evidence. */
public final class SlabRigCasePageManifest {

    public static final String SCHEMA = "slabbed-rig-case-page-v4";
    public static final String RESUME_CONTRACT =
            "RUNTIME_IMMEDIATE_PREDECESSOR_INDUCTIVE_V2;"
                    + "CATALOG_ENUMERATION_CLOSURE_REQUIRES_FULL_CHAIN_AUDIT_V2;"
                    + "EXACT_RUNTIME_CONTENT_SHA256_REQUIRED_V1;"
                    + "EXACT_FROZEN_DY_MODE_REQUIRED_V1";

    private SlabRigCasePageManifest() {
    }

    public record CellState(BlockPos pos, BlockState state, double storedDy, String markerFingerprint) {
        public CellState {
            pos = pos.immutable();
        }
    }

    /** Exact topology cell observation before/after the tested action, including visible live dy. */
    public record StructureCellState(BlockPos pos, BlockState state, double liveDy,
                                     double storedDy, String markerFingerprint) {
        public StructureCellState {
            pos = pos.immutable();
        }
    }

    public record CellChange(BlockPos pos, BlockState before, BlockState after,
                             double storedDyBefore, double storedDyAfter,
                             String markerBefore, String markerAfter) {
        public CellChange {
            pos = pos.immutable();
        }
    }

    public record CaseAttempt(
            SlabRigCaseCatalog.CaseDefinition definition,
            BlockPos tileBase,
            List<BlockPos> plannedStructureCells,
            String structureStatus,
            String structureDetail,
            List<StructureCellState> actualStructureCells,
            List<StructureCellState> postActionStructureCells,
            String postActionStructureStatus,
            String postActionStructureDetail,
            List<CellState> externalGuardContext,
            List<BlockPos> reservedCells,
            List<BlockPos> effectCells,
            BlockPos clicked,
            String face,
            BlockPos plannedTarget,
            String attemptStatus,
            String actionOrigin,
            String outcome,
            String interactionResult,
            boolean interactionConsumesAction,
            String stackItemBefore,
            int stackBefore,
            String stackItemAfter,
            int stackAfter,
            boolean persistentSubjectPresent,
            String detail,
            List<CellChange> actualChanges) {
        public CaseAttempt {
            tileBase = tileBase.immutable();
            plannedStructureCells = immutableSortedPositions(plannedStructureCells);
            List<StructureCellState> structure = new ArrayList<>(actualStructureCells);
            structure.sort(Comparator.comparing(StructureCellState::pos, POSITION_ORDER));
            actualStructureCells = List.copyOf(structure);
            List<StructureCellState> postStructure = new ArrayList<>(postActionStructureCells);
            postStructure.sort(Comparator.comparing(StructureCellState::pos, POSITION_ORDER));
            postActionStructureCells = List.copyOf(postStructure);
            List<CellState> context = new ArrayList<>(externalGuardContext);
            context.sort(Comparator.comparing(CellState::pos, POSITION_ORDER));
            externalGuardContext = List.copyOf(context);
            reservedCells = immutableSortedPositions(reservedCells);
            effectCells = immutableSortedPositions(effectCells);
            clicked = clicked.immutable();
            plannedTarget = plannedTarget.immutable();
            List<CellChange> sorted = new ArrayList<>(actualChanges);
            sorted.sort(Comparator.comparing(CellChange::pos, POSITION_ORDER));
            actualChanges = List.copyOf(sorted);
        }
    }

    public record PageManifest(
            String status,
            SlabRigCaseCatalog.Snapshot snapshot,
            SlabRigCaseCatalog.CasePage page,
            String worldKey,
            boolean frozenDyEnabled,
            String dimension,
            String player,
            BlockPos base,
            String facing,
            String catalogArtifact,
            String previousContiguousManifestHash,
            String plannedArtifact,
            List<CaseAttempt> cases) {
        public PageManifest {
            base = base.immutable();
            cases = List.copyOf(cases);
        }
    }

    public record Coverage(int casesMaterialized, int planned, int proxyExecuted, int deferred,
                           int changed, int refused, int errors, int interrupted,
                           int topologyLawReds, int placedThenVanished, int externalGuardCells) {
    }

    private static final Comparator<BlockPos> POSITION_ORDER = Comparator
            .<BlockPos>comparingInt(pos -> pos.getX())
            .thenComparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ);

    private static List<BlockPos> immutableSortedPositions(List<BlockPos> positions) {
        List<BlockPos> sorted = new ArrayList<>();
        for (BlockPos pos : positions) {
            sorted.add(pos.immutable());
        }
        sorted.sort(POSITION_ORDER);
        return List.copyOf(sorted);
    }

    /** Full JSON with an id derived from the deterministic body (the id is not hashed into itself). */
    public static String canonicalJson(PageManifest manifest) {
        String body = canonicalBody(manifest);
        String id = sha256(body);
        return "{\n  \"manifestId\": \"sha256:" + id + "\",\n" + body.substring(2);
    }

    public static String manifestHash(PageManifest manifest) {
        return sha256(canonicalBody(manifest));
    }

    /** Block-state equality alone is insufficient: store/marker-only mutations are real effects. */
    public static boolean observationChanged(BlockState before, double storedBefore, String markerBefore,
                                             BlockState after, double storedAfter, String markerAfter) {
        return !before.equals(after)
                || Double.doubleToLongBits(storedBefore == 0.0 ? 0.0 : storedBefore)
                != Double.doubleToLongBits(storedAfter == 0.0 ? 0.0 : storedAfter)
                || !markerBefore.equals(markerAfter);
    }

    /** Status counters are a pure seam so validators/tests cannot silently reinterpret ERROR as execution. */
    public static Coverage coverage(PageManifest manifest) {
        int planned = 0;
        int executed = 0;
        int deferred = 0;
        int refused = 0;
        int errors = 0;
        int interrupted = 0;
        int topologyLawReds = 0;
        int placedThenVanished = 0;
        int externalGuardCells = 0;
        int changed = 0;
        for (CaseAttempt attempt : manifest.cases()) {
            switch (attempt.attemptStatus()) {
                case "PLANNED" -> planned++;
                case "DEFERRED" -> deferred++;
                case "EXECUTED" -> executed++;
                case "ERROR" -> errors++;
                case "INTERRUPTED" -> interrupted++;
                default -> errors++;
            }
            if ("LAW_RED".equals(attempt.structureStatus())) {
                topologyLawReds++;
            }
            externalGuardCells += attempt.externalGuardContext().size();
            if (attempt.outcome().startsWith("REFUSED")) {
                refused++;
            }
            if ("PLACED_THEN_VANISHED".equals(attempt.outcome())) {
                placedThenVanished++;
            }
            if (!"ERROR".equals(attempt.attemptStatus()) && attempt.outcome().startsWith("ERROR")) {
                errors++;
            }
            if (!attempt.actualChanges().isEmpty()) {
                changed++;
            }
        }
        return new Coverage(manifest.cases().size(), planned, executed, deferred, changed, refused,
                errors, interrupted, topologyLawReds, placedThenVanished, externalGuardCells);
    }

    /** A vanished subject is a WYSIWYG red even when the bounded proxy invocation itself was clean. */
    public static boolean hasLawReds(Coverage coverage) {
        return coverage.topologyLawReds() > 0 || coverage.placedThenVanished() > 0;
    }

    /** Pure status seam: infrastructure failures are partial; topology or vanished subjects are reds. */
    public static String finalizedStatus(int infrastructureErrors, int topologyLawReds,
                                         int placedThenVanished) {
        if (infrastructureErrors < 0 || topologyLawReds < 0 || placedThenVanished < 0) {
            throw new IllegalArgumentException("RIG-2 status counters cannot be negative");
        }
        if (infrastructureErrors > 0) {
            return "PARTIAL";
        }
        return topologyLawReds > 0 || placedThenVanished > 0
                ? "FINALIZED_WITH_REDS" : "FINALIZED";
    }

    private static String canonicalBody(PageManifest manifest) {
        SlabRigCaseCatalog.Snapshot snapshot = manifest.snapshot();
        SlabRigCaseCatalog.CasePage page = manifest.page();
        Coverage coverage = coverage(manifest);

        StringBuilder out = new StringBuilder();
        out.append("{\n");
        field(out, 1, "schema", SCHEMA, true);
        field(out, 1, "status", manifest.status(), true);
        field(out, 1, "buildGitSha", BuildStamp.GIT_SHA, true);
        field(out, 1, "runtimeContentSha256", BuildStamp.RUNTIME_CONTENT_SHA256, true);
        field(out, 1, "jarFile", BuildStamp.JAR_FILE, true);
        field(out, 1, "minecraftVersion", SharedConstants.getCurrentVersion().name(), true);
        field(out, 1, "catalogSchema", snapshot.schema(), true);
        field(out, 1, "catalogHash", snapshot.catalogHash(), true);
        field(out, 1, "executionContract", SlabRigCaseCatalog.EXECUTION_CONTRACT, true);
        numberField(out, 1, "runtimeBlockItems", snapshot.items().size(), true);
        numberField(out, 1, "explicitNonBlockItems", snapshot.excludedItems().size(), true);
        numberField(out, 1, "topologyCount", snapshot.topologies().size(), true);
        numberField(out, 1, "totalCases", snapshot.totalCases(), true);
        numberField(out, 1, "page", page.page(), true);
        numberField(out, 1, "pageCount", page.pageCount(), true);
        numberField(out, 1, "itemGroup", page.itemGroup(), true);
        numberField(out, 1, "topologyGroup", page.topologyGroup(), true);
        field(out, 1, "pageGeometry", "4_items_x_4_topologies", true);
        field(out, 1, "worldKey", manifest.worldKey(), true);
        booleanField(out, 1, "frozenDyEnabled", manifest.frozenDyEnabled(), true);
        field(out, 1, "dimension", manifest.dimension(), true);
        field(out, 1, "player", manifest.player(), true);
        field(out, 1, "base", pos(manifest.base()), true);
        field(out, 1, "facing", manifest.facing(), true);
        field(out, 1, "catalogArtifact", manifest.catalogArtifact(), true);
        field(out, 1, "previousContiguousManifestHash", manifest.previousContiguousManifestHash(), true);
        field(out, 1, "plannedArtifact", manifest.plannedArtifact(), true);
        field(out, 1, "placementMode", "FLOOR_UP", true);
        field(out, 1, "effectObservationPolicy", "clicked_target_target_above_below_and_horizontal_neighbors_v1", true);
        field(out, 1, "resumeContract", RESUME_CONTRACT, true);
        field(out, 1, "hangingCoverage", "OUT_OF_SCOPE_RIG2_V1_SEPARATE_PASS", true);
        field(out, 1, "playerProof", "ABSENT_PROXY_DIAGNOSTIC_ONLY", true);
        out.append("  \"coverage\": {\n");
        field(out, 2, "registryPartition", "COMPLETE_RUNTIME_ITEM_REGISTRY", true);
        field(out, 2, "blockItemCaseIndex", "COMPLETE", true);
        field(out, 2, "topologyCatalog", "COMPLETE_64", true);
        field(out, 2, "pageStatus", manifest.status(), true);
        numberField(out, 2, "casesMaterialized", coverage.casesMaterialized(), true);
        numberField(out, 2, "planned", coverage.planned(), true);
        numberField(out, 2, "proxyExecuted", coverage.proxyExecuted(), true);
        numberField(out, 2, "deferred", coverage.deferred(), true);
        numberField(out, 2, "changed", coverage.changed(), true);
        numberField(out, 2, "refused", coverage.refused(), true);
        numberField(out, 2, "errors", coverage.errors(), true);
        numberField(out, 2, "interrupted", coverage.interrupted(), true);
        numberField(out, 2, "topologyLawReds", coverage.topologyLawReds(), true);
        numberField(out, 2, "placedThenVanished", coverage.placedThenVanished(), true);
        numberField(out, 2, "externalGuardCells", coverage.externalGuardCells(), true);
        numberField(out, 2, "playerAuthoredPaired", 0, false);
        out.append("  },\n");
        out.append("  \"cases\": [\n");
        for (int index = 0; index < manifest.cases().size(); index++) {
            appendCase(out, manifest.cases().get(index));
            out.append(index + 1 < manifest.cases().size() ? ",\n" : "\n");
        }
        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }

    private static void appendCase(StringBuilder out, CaseAttempt attempt) {
        SlabRigCaseCatalog.CaseDefinition definition = attempt.definition();
        SlabRigCaseCatalog.CatalogItem item = definition.item();
        SlabRigCaseCatalog.Topology topology = definition.topology();
        out.append("    {\n");
        numberField(out, 3, "globalCaseIndex", definition.index(), true);
        field(out, 3, "caseId", definition.id(), true);
        numberField(out, 3, "itemIndex", item.index(), true);
        field(out, 3, "itemId", item.id(), true);
        stringArrayField(out, 3, "categories", item.categories(), true);
        field(out, 3, "disposition", item.disposition().name(), true);
        field(out, 3, "effectPolicy", item.effectPolicy().name(), true);
        numberField(out, 3, "topologyIndex", topology.index(), true);
        field(out, 3, "topologyId", topology.id(), true);
        field(out, 3, "topologyRecipe", topology.recipe(), true);
        field(out, 3, "placementMode", definition.placementMode(), true);
        field(out, 3, "tileBase", pos(attempt.tileBase()), true);
        positionArrayField(out, 3, "plannedStructureCells", attempt.plannedStructureCells(), true);
        field(out, 3, "structureStatus", attempt.structureStatus(), true);
        field(out, 3, "structureDetail", attempt.structureDetail(), true);
        structureCellArrayField(out, 3, "actualStructureCells",
                attempt.actualStructureCells(), true);
        structureCellArrayField(out, 3, "postActionStructureCells",
                attempt.postActionStructureCells(), true);
        field(out, 3, "postActionStructureStatus", attempt.postActionStructureStatus(), true);
        field(out, 3, "postActionStructureDetail", attempt.postActionStructureDetail(), true);
        out.append("      \"externalGuardContext\": [");
        if (!attempt.externalGuardContext().isEmpty()) {
            out.append('\n');
            for (int i = 0; i < attempt.externalGuardContext().size(); i++) {
                CellState cell = attempt.externalGuardContext().get(i);
                out.append("        {\"pos\": \"").append(escape(pos(cell.pos())))
                        .append("\", \"state\": \"").append(escape(cell.state().toString()))
                        .append("\", \"storedDy\": \"").append(formatDy(cell.storedDy()))
                        .append("\", \"markers\": \"").append(escape(cell.markerFingerprint()))
                        .append("\"}");
                out.append(i + 1 < attempt.externalGuardContext().size() ? ",\n" : "\n");
            }
            out.append("      ],\n");
        } else {
            out.append("],\n");
        }
        positionArrayField(out, 3, "reservedCells", attempt.reservedCells(), true);
        positionArrayField(out, 3, "effectCells", attempt.effectCells(), true);
        field(out, 3, "clicked", pos(attempt.clicked()), true);
        field(out, 3, "face", attempt.face(), true);
        field(out, 3, "hitVector", hitVector(attempt.clicked(), attempt.face()), true);
        field(out, 3, "plannedTarget", pos(attempt.plannedTarget()), true);
        field(out, 3, "attemptStatus", attempt.attemptStatus(), true);
        field(out, 3, "actionOrigin", attempt.actionOrigin(), true);
        field(out, 3, "outcome", attempt.outcome(), true);
        field(out, 3, "interactionResult", attempt.interactionResult(), true);
        booleanField(out, 3, "interactionConsumesAction", attempt.interactionConsumesAction(), true);
        field(out, 3, "stackItemBefore", attempt.stackItemBefore(), true);
        numberField(out, 3, "stackBefore", attempt.stackBefore(), true);
        field(out, 3, "stackItemAfter", attempt.stackItemAfter(), true);
        numberField(out, 3, "stackAfter", attempt.stackAfter(), true);
        booleanField(out, 3, "persistentSubjectPresent", attempt.persistentSubjectPresent(), true);
        field(out, 3, "detail", attempt.detail(), true);
        out.append("      \"actualChanges\": [");
        if (!attempt.actualChanges().isEmpty()) {
            out.append('\n');
            for (int i = 0; i < attempt.actualChanges().size(); i++) {
                CellChange change = attempt.actualChanges().get(i);
                out.append("        {\"pos\": \"").append(escape(pos(change.pos())))
                        .append("\", \"before\": \"").append(escape(change.before().toString()))
                        .append("\", \"after\": \"").append(escape(change.after().toString()))
                        .append("\", \"storedDyBefore\": \"").append(formatDy(change.storedDyBefore()))
                        .append("\", \"storedDyAfter\": \"").append(formatDy(change.storedDyAfter()))
                        .append("\", \"markerBefore\": \"").append(escape(change.markerBefore()))
                        .append("\", \"markerAfter\": \"").append(escape(change.markerAfter()))
                        .append("\"}");
                out.append(i + 1 < attempt.actualChanges().size() ? ",\n" : "\n");
            }
            out.append("      ]\n");
        } else {
            out.append("]\n");
        }
        out.append("    }");
    }

    private static void field(StringBuilder out, int indent, String key, String value, boolean comma) {
        out.append("  ".repeat(indent)).append('\"').append(key).append("\": \"")
                .append(escape(value)).append('\"');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void numberField(StringBuilder out, int indent, String key, long value, boolean comma) {
        out.append("  ".repeat(indent)).append('\"').append(key).append("\": ")
                .append(value);
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void booleanField(StringBuilder out, int indent, String key,
                                     boolean value, boolean comma) {
        out.append("  ".repeat(indent)).append('\"').append(key).append("\": ")
                .append(value);
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void structureCellArrayField(StringBuilder out, int indent, String key,
                                                List<StructureCellState> values, boolean comma) {
        out.append("  ".repeat(indent)).append('\"').append(key).append("\": [");
        if (!values.isEmpty()) {
            out.append('\n');
            for (int i = 0; i < values.size(); i++) {
                StructureCellState cell = values.get(i);
                out.append("  ".repeat(indent + 1)).append("{\"pos\": \"")
                        .append(escape(pos(cell.pos())))
                        .append("\", \"state\": \"").append(escape(cell.state().toString()))
                        .append("\", \"liveDy\": \"").append(formatDy(cell.liveDy()))
                        .append("\", \"storedDy\": \"").append(formatDy(cell.storedDy()))
                        .append("\", \"markers\": \"").append(escape(cell.markerFingerprint()))
                        .append("\"}");
                out.append(i + 1 < values.size() ? ",\n" : "\n");
            }
            out.append("  ".repeat(indent)).append(']');
        } else {
            out.append(']');
        }
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void stringArrayField(StringBuilder out, int indent, String key, List<String> values,
                                         boolean comma) {
        out.append("  ".repeat(indent)).append('\"').append(key).append("\": [");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append('\"').append(escape(values.get(i))).append('\"');
        }
        out.append(']');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static void positionArrayField(StringBuilder out, int indent, String key, List<BlockPos> values,
                                           boolean comma) {
        out.append("  ".repeat(indent)).append('\"').append(key).append("\": [");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append('\"').append(pos(values.get(i))).append('\"');
        }
        out.append(']');
        if (comma) {
            out.append(',');
        }
        out.append('\n');
    }

    private static String hitVector(BlockPos clicked, String face) {
        double x = clicked.getX() + 0.5;
        double y = clicked.getY() + 0.5;
        double z = clicked.getZ() + 0.5;
        switch (face) {
            case "up" -> y += 0.5;
            case "down" -> y -= 0.5;
            case "north" -> z -= 0.5;
            case "south" -> z += 0.5;
            case "west" -> x -= 0.5;
            case "east" -> x += 0.5;
            default -> {
            }
        }
        return String.format(Locale.ROOT, "%.3f,%.3f,%.3f", x, y, z);
    }

    private static String pos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String formatDy(double value) {
        // Double.toString is deterministic and round-trips the exact finite IEEE-754 value.
        return Double.isNaN(value) ? "NaN" : Double.toString(value);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
