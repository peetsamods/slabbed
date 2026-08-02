package com.slabbed.rig;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.placement.LandingResolution;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Exact immutable ownership ledger for one in-memory generic rig run. */
public record RigManifest(
        UUID runId,
        String ownerUuid,
        String dimensionId,
        BlockPos anchor,
        String mode,
        List<String> caseIds,
        List<OwnedCell> ownedCells,
        ExecutionReceipt receipt,
        StructuralReport structuralReport) {

    public RigManifest {
        runId = Objects.requireNonNull(runId, "runId");
        ownerUuid = requireText(ownerUuid, "ownerUuid");
        dimensionId = requireText(dimensionId, "dimensionId");
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        mode = requireText(mode, "mode");
        caseIds = List.copyOf(Objects.requireNonNull(caseIds, "caseIds"));
        ownedCells = List.copyOf(Objects.requireNonNull(ownedCells, "ownedCells"));
        receipt = Objects.requireNonNull(receipt, "receipt");
        structuralReport = Objects.requireNonNull(structuralReport, "structuralReport");
        if (caseIds.isEmpty() || ownedCells.isEmpty()) {
            throw new IllegalArgumentException("manifest must own at least one case and cell");
        }
        Set<String> uniqueCases = new HashSet<>();
        for (String caseId : caseIds) {
            String normalized = requireText(caseId, "caseId");
            if (!uniqueCases.add(normalized)) {
                throw new IllegalArgumentException("duplicate case id " + normalized);
            }
        }
        Set<BlockPos> uniqueCells = new HashSet<>();
        for (OwnedCell cell : ownedCells) {
            if (!uniqueCases.contains(cell.caseId())) {
                throw new IllegalArgumentException("owned cell names unknown case " + cell.caseId());
            }
            if (!uniqueCells.add(cell.pos())) {
                throw new IllegalArgumentException("duplicate owned cell " + cell.pos());
            }
        }
        if (structuralReport instanceof NumericTowerReport towerReport) {
            if (!mode.equals("tower.numeric")) {
                throw new IllegalArgumentException(
                        "numeric tower report requires tower.numeric mode");
            }
            int reportedAttempts = towerReport.towers().stream()
                    .mapToInt(TowerColumnReport::attempts)
                    .sum();
            long reportedBuilt = towerReport.towers().stream()
                    .mapToLong(TowerColumnReport::builtCells)
                    .sum();
            long placedResolutions = receipt.resolutions().stream()
                    .filter(LandingResolution.Place.class::isInstance)
                    .count();
            long ownedSubjects = ownedCells.stream()
                    .filter(cell -> cell.role() == CellRole.SUBJECT)
                    .count();
            if (reportedAttempts != receipt.subjectUseOnCalls()
                    || receipt.resolutions().size() != receipt.subjectUseOnCalls()) {
                throw new IllegalArgumentException(
                        "numeric tower report must account for every useOn attempt");
            }
            if (reportedBuilt != placedResolutions || placedResolutions != ownedSubjects) {
                throw new IllegalArgumentException(
                        "numeric tower placed outcomes and owned subject cells must agree");
            }
        } else if (structuralReport instanceof StackPageReport stackReport) {
            if (!mode.equals("stacks")) {
                throw new IllegalArgumentException("stack page report requires stacks mode");
            }
            int reportedAttempts = stackReport.stacks().stream()
                    .map(StackEntryReport::column)
                    .mapToInt(TowerColumnReport::attempts)
                    .sum();
            long reportedBuilt = stackReport.stacks().stream()
                    .map(StackEntryReport::column)
                    .mapToLong(TowerColumnReport::builtCells)
                    .sum();
            long placedResolutions = receipt.resolutions().stream()
                    .filter(LandingResolution.Place.class::isInstance)
                    .count();
            long ownedSubjects = ownedCells.stream()
                    .filter(cell -> cell.role() == CellRole.SUBJECT)
                    .count();
            if (reportedAttempts != receipt.subjectUseOnCalls()
                    || receipt.resolutions().size() != receipt.subjectUseOnCalls()) {
                throw new IllegalArgumentException(
                        "stack page report must account for every useOn attempt");
            }
            if (reportedBuilt != placedResolutions || placedResolutions != ownedSubjects) {
                throw new IllegalArgumentException(
                        "stack placed outcomes and owned subject cells must agree");
            }
        } else if (structuralReport instanceof MegaReport megaReport) {
            if (!mode.equals("mega")) {
                throw new IllegalArgumentException("mega report requires mega mode");
            }
            if (megaReport.attempts() != receipt.subjectUseOnCalls()
                    || receipt.resolutions().size() != receipt.subjectUseOnCalls()) {
                throw new IllegalArgumentException(
                        "mega report must account for every useOn attempt");
            }
        }
    }

    /**
     * Narrows this ledger after a partial clear without pretending the original structural run
     * still describes the remaining cells.
     */
    public RigManifest withResidualOwnedCells(List<OwnedCell> residualCells) {
        return new RigManifest(
                runId, ownerUuid, dimensionId, anchor, mode,
                caseIds, residualCells, receipt, new ResidueStructuralReport());
    }

    public enum CellRole {
        FIXTURE,
        SUBJECT
    }

    public record OwnedCell(
            BlockPos pos,
            BlockState expectedState,
            SlabAnchorAttachment.PlacementDyFact expectedStoredDy,
            CellRole role,
            String caseId) {
        public OwnedCell {
            pos = Objects.requireNonNull(pos, "pos").immutable();
            expectedState = Objects.requireNonNull(expectedState, "expectedState");
            expectedStoredDy = Objects.requireNonNull(expectedStoredDy, "expectedStoredDy");
            role = Objects.requireNonNull(role, "role");
            caseId = requireText(caseId, "caseId");
        }
    }

    /** Audit of the only two permitted execution paths. Subject direct writes must stay zero. */
    public record ExecutionReceipt(
            int fixtureDirectWrites,
            int fixtureTruthWrites,
            int subjectUseOnCalls,
            int subjectDirectStateWrites,
            List<LandingResolution> resolutions) {
        public ExecutionReceipt {
            resolutions = List.copyOf(Objects.requireNonNull(resolutions, "resolutions"));
            if (fixtureDirectWrites < 0 || fixtureTruthWrites < 0
                    || subjectUseOnCalls < 0 || subjectDirectStateWrites < 0) {
                throw new IllegalArgumentException("execution counts must be non-negative");
            }
            if (fixtureTruthWrites > fixtureDirectWrites) {
                throw new IllegalArgumentException(
                        "fixture truth writes cannot exceed fixture state writes");
            }
            if (subjectDirectStateWrites != 0) {
                throw new IllegalArgumentException("subject direct-state writes are forbidden");
            }
            if (resolutions.size() > subjectUseOnCalls) {
                throw new IllegalArgumentException("resolutions cannot exceed useOn calls");
            }
        }
    }

    /** Structural readback is diagnostics evidence, never a product-law admission. */
    public sealed interface StructuralReport permits
            NoStructuralReport, ResidueStructuralReport, NumericTowerReport, StackPageReport,
            MegaReport {
        boolean complete();

        String note();

        static StructuralReport none() {
            return new NoStructuralReport();
        }
    }

    public record NoStructuralReport() implements StructuralReport {
        @Override
        public boolean complete() {
            return true;
        }

        @Override
        public String note() {
            return "not-applicable";
        }
    }

    /** A partial clear owns real residue but no longer claims the original run is structurally complete. */
    public record ResidueStructuralReport() implements StructuralReport {
        @Override
        public boolean complete() {
            return false;
        }

        @Override
        public String note() {
            return "rollback-residue";
        }
    }

    /** Exact numeric-tower outcome: actual cells, short columns, and every seam anomaly. */
    public record NumericTowerReport(
            int requestedHeight,
            List<TowerColumnReport> towers,
            List<SeamFinding> seams,
            boolean complete,
            String note) implements StructuralReport {
        public NumericTowerReport {
            if (requestedHeight < 1) {
                throw new IllegalArgumentException("requestedHeight must be positive");
            }
            towers = List.copyOf(Objects.requireNonNull(towers, "towers"));
            seams = List.copyOf(Objects.requireNonNull(seams, "seams"));
            note = requireText(note, "note");
            if (towers.isEmpty()) {
                throw new IllegalArgumentException("numeric tower report must name a tower");
            }
            Set<Integer> indices = new HashSet<>();
            for (TowerColumnReport tower : towers) {
                if (!indices.add(tower.index())) {
                    throw new IllegalArgumentException(
                            "duplicate numeric tower index " + tower.index());
                }
            }
            boolean derivedComplete = towers.stream()
                    .allMatch(tower -> tower.builtCells() == requestedHeight)
                    && seams.isEmpty();
            if (complete != derivedComplete) {
                throw new IllegalArgumentException(
                        "numeric tower completeness must match built cells and seams");
            }
        }

        public long gaps() {
            return seams.stream().filter(seam -> seam.kind() == SeamKind.GAP).count();
        }

        public long overlaps() {
            return seams.stream().filter(seam -> seam.kind() == SeamKind.OVERLAP).count();
        }
    }

    /** Exact paged stack-catalog outcome; every recipe keeps its own planned length. */
    public record StackPageReport(
            int maxLength,
            int page,
            int totalPages,
            int totalRecipes,
            List<StackEntryReport> stacks,
            List<SeamFinding> seams,
            boolean complete,
            String note) implements StructuralReport {
        public StackPageReport {
            if (maxLength < 1 || page < 1 || totalPages < 1 || page > totalPages
                    || totalRecipes < 1) {
                throw new IllegalArgumentException("invalid stack page bounds");
            }
            stacks = List.copyOf(Objects.requireNonNull(stacks, "stacks"));
            seams = List.copyOf(Objects.requireNonNull(seams, "seams"));
            note = requireText(note, "note");
            if (stacks.isEmpty()) {
                throw new IllegalArgumentException("stack page report must name a stack");
            }
            Set<Integer> catalogIndices = new HashSet<>();
            for (StackEntryReport stack : stacks) {
                if (!catalogIndices.add(stack.catalogIndex())) {
                    throw new IllegalArgumentException(
                            "duplicate stack catalog index " + stack.catalogIndex());
                }
            }
            for (SeamFinding seam : seams) {
                long owners = stacks.stream().filter(stack -> stack.contains(seam)).count();
                if (owners != 1) {
                    throw new IllegalArgumentException(
                            "each stack seam must belong to exactly one catalog entry");
                }
            }
            boolean derivedComplete = true;
            for (StackEntryReport stack : stacks) {
                if (!stack.complete(seams)) {
                    derivedComplete = false;
                    break;
                }
            }
            if (complete != derivedComplete) {
                throw new IllegalArgumentException(
                        "stack page completeness must match built cells and seams");
            }
        }

        public long gaps() {
            return seams.stream().filter(seam -> seam.kind() == SeamKind.GAP).count();
        }

        public long overlaps() {
            return seams.stream().filter(seam -> seam.kind() == SeamKind.OVERLAP).count();
        }
    }

    /** Four-row everything-rig outcome, including honest refused actions and seat readback. */
    public record MegaReport(
            int columns,
            int attempts,
            int placed,
            int refused,
            List<String> refusedItemIds,
            List<MegaSeatReadback> sampleSeats,
            boolean complete,
            String note) implements StructuralReport {
        public MegaReport {
            refusedItemIds = List.copyOf(Objects.requireNonNull(
                    refusedItemIds, "refusedItemIds"));
            sampleSeats = List.copyOf(Objects.requireNonNull(sampleSeats, "sampleSeats"));
            note = requireText(note, "note");
            if (columns < 1 || attempts != columns * 4 || placed < 0 || refused < 0
                    || placed + refused != attempts || sampleSeats.size() != 4) {
                throw new IllegalArgumentException("invalid mega report counts");
            }
            Set<Integer> rows = new HashSet<>();
            for (MegaSeatReadback seat : sampleSeats) {
                if (!rows.add(seat.row())) {
                    throw new IllegalArgumentException("duplicate mega sample row " + seat.row());
                }
            }
            boolean derivedComplete = sampleSeats.stream().allMatch(MegaSeatReadback::matches);
            if (complete != derivedComplete) {
                throw new IllegalArgumentException(
                        "mega completeness must match the four sample seats");
            }
        }
    }

    public record MegaSeatReadback(
            int row,
            String label,
            BlockPos pos,
            long expectedDyBits,
            long liveDyBits) {
        public MegaSeatReadback {
            label = requireText(label, "label");
            pos = Objects.requireNonNull(pos, "pos").immutable();
            if (row < 0 || row >= 4
                    || !Double.isFinite(Double.longBitsToDouble(expectedDyBits))
                    || !Double.isFinite(Double.longBitsToDouble(liveDyBits))) {
                throw new IllegalArgumentException("invalid mega seat readback");
            }
        }

        public boolean matches() {
            return expectedDyBits == liveDyBits;
        }

        public double expectedDy() {
            return Double.longBitsToDouble(expectedDyBits);
        }

        public double liveDy() {
            return Double.longBitsToDouble(liveDyBits);
        }
    }

    public record StackEntryReport(
            int catalogIndex,
            String recipe,
            TowerColumnReport column) {
        public StackEntryReport {
            if (catalogIndex < 0) {
                throw new IllegalArgumentException("stack catalog index must be non-negative");
            }
            recipe = requireText(recipe, "recipe");
            column = Objects.requireNonNull(column, "column");
            for (int index = 0; index < recipe.length(); index++) {
                char token = recipe.charAt(index);
                if (token != 'S' && token != 'B') {
                    throw new IllegalArgumentException("stack recipe must contain only S/B");
                }
            }
            if (column.attempts() > recipe.length()
                    || (!column.stalled() && column.attempts() != recipe.length())) {
                throw new IllegalArgumentException(
                        "stack attempts must remain within the exact recipe");
            }
            if (column.index() != catalogIndex || !column.label().equals(recipe)) {
                throw new IllegalArgumentException(
                        "stack report must bind its catalog index and recipe exactly");
            }
        }

        public boolean contains(SeamFinding seam) {
            Objects.requireNonNull(seam, "seam");
            Set<BlockPos> positions = column.cells().stream()
                    .map(TowerCellReadback::pos)
                    .collect(java.util.stream.Collectors.toSet());
            return positions.contains(seam.lowerPos()) && positions.contains(seam.upperPos());
        }

        public List<SeamFinding> seams(List<SeamFinding> pageSeams) {
            return List.copyOf(Objects.requireNonNull(pageSeams, "pageSeams").stream()
                    .filter(this::contains)
                    .toList());
        }

        public boolean complete(List<SeamFinding> pageSeams) {
            return column.builtCells() == recipe.length()
                    && !column.stalled()
                    && seams(pageSeams).isEmpty();
        }
    }

    public record TowerColumnReport(
            int index,
            String label,
            BlockPos seat,
            int attempts,
            int builtCells,
            boolean stalled,
            List<TowerCellReadback> cells) {
        public TowerColumnReport {
            if (index < 0 || attempts < 0 || builtCells < 0 || builtCells > attempts) {
                throw new IllegalArgumentException("invalid numeric tower column counts");
            }
            label = requireText(label, "label");
            seat = Objects.requireNonNull(seat, "seat").immutable();
            cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
            if (cells.size() != builtCells + 1 || !cells.get(0).pos().equals(seat)) {
                throw new IllegalArgumentException(
                        "tower readback must contain the seat plus every built cell");
            }
        }
    }

    public record TowerCellReadback(
            BlockPos pos,
            BlockState state,
            long liveDyBits) {
        public TowerCellReadback {
            pos = Objects.requireNonNull(pos, "pos").immutable();
            state = Objects.requireNonNull(state, "state");
            if (state.isAir() || !Double.isFinite(Double.longBitsToDouble(liveDyBits))) {
                throw new IllegalArgumentException(
                        "tower readback must name a non-air cell with finite live dy");
            }
        }

        public double liveDy() {
            return Double.longBitsToDouble(liveDyBits);
        }
    }

    public enum SeamKind {
        GAP,
        OVERLAP
    }

    public record SeamFinding(
            SeamKind kind,
            BlockPos lowerPos,
            BlockPos upperPos,
            long lowerTopBits,
            long upperBottomBits,
            long seamBits) {
        public SeamFinding {
            kind = Objects.requireNonNull(kind, "kind");
            lowerPos = Objects.requireNonNull(lowerPos, "lowerPos").immutable();
            upperPos = Objects.requireNonNull(upperPos, "upperPos").immutable();
            double lowerTop = Double.longBitsToDouble(lowerTopBits);
            double upperBottom = Double.longBitsToDouble(upperBottomBits);
            double seam = Double.longBitsToDouble(seamBits);
            if (!Double.isFinite(lowerTop)
                    || !Double.isFinite(upperBottom)
                    || !Double.isFinite(seam)
                    || (kind == SeamKind.GAP && seam <= 0.0d)
                    || (kind == SeamKind.OVERLAP && seam >= 0.0d)) {
                throw new IllegalArgumentException("invalid numeric tower seam finding");
            }
        }

        public double lowerTop() {
            return Double.longBitsToDouble(lowerTopBits);
        }

        public double upperBottom() {
            return Double.longBitsToDouble(upperBottomBits);
        }

        public double seam() {
            return Double.longBitsToDouble(seamBits);
        }
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }
}
