package com.slabbed.rig;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.command.SlabbedOperatorTools;
import com.slabbed.placement.LandingResolution;
import com.slabbed.placement.PlacementAim;
import com.slabbed.util.SlabbedDiagnosticsBridge;
import com.slabbed.util.SlabSupport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;

/**
 * Server-thread authority for the generic developer rig.
 *
 * <p>Rows, tower, and stacks ownership is intentionally launch-local. Cases ownership is separate:
 * its write-ahead evidence survives restart and refuses to guess through an interrupted current
 * case. Direct world writes are confined to {@link #placeFixture}; every subject goes through
 * Forge's real use-on route.</p>
 */
public final class SlabbedRigService {
    public static final int DEFAULT_NUMERIC_TOWER_HEIGHT = 8;
    public static final int MIN_NUMERIC_TOWER_COUNT = 1;
    public static final int MAX_NUMERIC_TOWER_COUNT = 8;
    public static final int MIN_NUMERIC_TOWER_HEIGHT = 1;
    public static final int MAX_NUMERIC_TOWER_HEIGHT = 16;
    public static final int DEFAULT_STACK_MAX_LENGTH = 5;
    public static final int MIN_STACK_MAX_LENGTH = 1;
    public static final int MAX_STACK_MAX_LENGTH = 5;
    public static final int DEFAULT_STACK_PAGE = 1;
    public static final int STACK_GRID_SIZE = 4;
    public static final int STACK_PAGE_SIZE = STACK_GRID_SIZE * STACK_GRID_SIZE;
    public static final int CASES_TILE_SPACING = 8;
    public static final String CASES_LAYOUT_VERSION = "cases-4x4-spacing-8-v1";
    public static final int DEFAULT_MEGA_COLUMNS = SlabbedOperatorTools.paletteItems().size();
    public static final int MIN_MEGA_COLUMNS = 1;
    public static final int MAX_MEGA_COLUMNS = SlabbedOperatorTools.paletteItems().size();
    private static final int NUMERIC_TOWER_SPACING = 2;
    private static final int NUMERIC_TOWER_HEADROOM = 2;
    private static final int STACK_SPACING = 2;
    private static final int STACK_HEADROOM = 2;
    private static final int MEGA_TILE_SPACING = 5;
    private static final int MEGA_SUPPORT_Y = 4;
    private static final List<Double> MEGA_DEPTHS = List.of(
            0.0d, -0.5d, -1.0d, -1.5d, -2.0d,
            -2.5d, -3.0d, -3.5d, -4.0d);
    private static final List<Direction> MEGA_FACES = List.of(
            Direction.UP,
            Direction.DOWN,
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST);
    private static final int MEGA_ROW_COUNT = MEGA_DEPTHS.size() * MEGA_FACES.size();
    private static final double SEAM_EPSILON = 1.0e-6d;
    private static final String[] NUMERIC_TOWER_RECIPES = {"SB", "SSBB", "BS", "S"};
    private static final String[] NUMERIC_TOWER_LABELS = {"SBSB", "SSBB", "BSBS", "SSSS"};
    private static final List<String> CATALOG =
            List.of("rows", "tower", "stacks", "cases", "mega");
    private static final Set<String> KNOWN_MODES =
            Set.of("rows", "tower", "tower.numeric", "stacks", "mega");
    private static final Map<ServerLevel, ActiveRig> ACTIVE = new WeakHashMap<>();

    private SlabbedRigService() {
    }

    public static List<String> catalog() {
        return CATALOG;
    }

    /** Places the rows anchor four cells in front of the operator without steering the camera. */
    public static BlockPos defaultAnchor(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        Direction facing = horizontal(player.getDirection());
        return player.blockPosition().relative(facing, 4).immutable();
    }

    /** Donor-parity base for the separate numeric diagnostic: below feet, three cells forward. */
    public static BlockPos defaultNumericTowerAnchor(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        Direction facing = horizontal(player.getDirection());
        return player.blockPosition().below().relative(facing, 3).immutable();
    }

    /** Standard stack-board base: ground level, three cells in front of the operator. */
    public static BlockPos defaultStacksAnchor(ServerPlayer player) {
        return defaultNumericTowerAnchor(player);
    }

    /** Cases begin below the operator's feet and extend away in a deterministic 4x4 page. */
    public static BlockPos defaultCasesAnchor(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        Direction facing = horizontal(player.getDirection());
        return player.blockPosition().below().relative(facing, 4).immutable();
    }

    /** Mega is centered on the operator so its complete matrix stays inside loaded chunks. */
    public static BlockPos defaultMegaAnchor(ServerPlayer player) {
        return defaultMegaAnchor(player, DEFAULT_MEGA_COLUMNS);
    }

    private static BlockPos defaultMegaAnchor(ServerPlayer player, int columns) {
        Objects.requireNonNull(player, "player");
        Direction facing = horizontal(player.getDirection());
        Direction right = facing.getClockWise();
        int rowHalfSpan = ((MEGA_ROW_COUNT - 1) * MEGA_TILE_SPACING) / 2;
        int columnHalfSpan = ((columns - 1) * MEGA_TILE_SPACING) / 2;
        return player.blockPosition()
                .relative(facing, -rowHalfSpan)
                .relative(right, -columnHalfSpan)
                .immutable();
    }

    public static synchronized BuildResult buildRows(
            ServerLevel world,
            ServerPlayer player,
            boolean force) {
        return buildRowsAt(world, player, defaultAnchor(player), player.getDirection(), force);
    }

    /** Public only for the Forge-native contract; this remains an internal mod surface. */
    public static synchronized BuildResult buildRowsAt(
            ServerLevel world,
            ServerPlayer player,
            BlockPos anchor,
            Direction facing,
            boolean force) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(player, "player");
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        return buildPlanAt(
                world, player, anchor, "rows", rowsPlan(anchor, facing), force);
    }

    public static synchronized BuildResult buildTower(
            ServerLevel world,
            ServerPlayer player,
            boolean force) {
        return buildTowerAt(
                world, player, defaultAnchor(player), player.getDirection(), force);
    }

    /** Public only for the Forge-native contract; this remains an internal mod surface. */
    public static synchronized BuildResult buildTowerAt(
            ServerLevel world,
            ServerPlayer player,
            BlockPos anchor,
            boolean force) {
        return buildTowerAt(world, player, anchor, player.getDirection(), force);
    }

    /** Public only for the Forge-native contract; this remains an internal mod surface. */
    public static synchronized BuildResult buildTowerAt(
            ServerLevel world,
            ServerPlayer player,
            BlockPos anchor,
            Direction facing,
            boolean force) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(player, "player");
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        return buildPlanAt(
                world, player, anchor, "tower", towerPlan(anchor, facing), force);
    }

    public static synchronized BuildResult buildNumericTower(
            ServerLevel world,
            ServerPlayer player,
            int count,
            int height,
            boolean force) {
        return buildNumericTowerAt(
                world,
                player,
                defaultNumericTowerAnchor(player),
                player.getDirection(),
                count,
                height,
                force);
    }

    /** Public only for the Forge-native contract; this remains an internal diagnostic surface. */
    public static synchronized BuildResult buildNumericTowerAt(
            ServerLevel world,
            ServerPlayer player,
            BlockPos anchor,
            Direction facing,
            int count,
            int height,
            boolean force) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(player, "player");
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        return buildExecutionAt(
                world,
                player,
                anchor,
                numericTowerPlan(anchor, facing, count, height),
                force);
    }

    public static synchronized BuildResult buildStacks(
            ServerLevel world,
            ServerPlayer player,
            int maxLength,
            int page,
            boolean force) {
        return buildStacksAt(
                world,
                player,
                defaultStacksAnchor(player),
                player.getDirection(),
                maxLength,
                page,
                force);
    }

    /** Public only for the Forge-native contract; this remains an internal diagnostic surface. */
    public static synchronized BuildResult buildStacksAt(
            ServerLevel world,
            ServerPlayer player,
            BlockPos anchor,
            Direction facing,
            int maxLength,
            int page,
            boolean force) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(player, "player");
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        StackPlan plan;
        try {
            plan = stacksPlan(anchor, facing, maxLength, page);
        } catch (IllegalArgumentException invalid) {
            ActiveRig active = ACTIVE.get(world);
            return BuildResult.failure(
                    BuildOutcome.INVALID_REQUEST,
                    active == null ? null : active.manifest(),
                    List.of(),
                    invalid.getMessage());
        }
        return buildExecutionAt(world, player, anchor, plan, force);
    }

    public static synchronized BuildResult buildMega(
            ServerLevel world,
            ServerPlayer player,
            int columns,
            boolean force) {
        return buildMegaAt(
                world,
                player,
                defaultMegaAnchor(player, columns),
                player.getDirection(),
                columns,
                force);
    }

    /** Public only for the Forge-native coverage contract; this is not an external mod API. */
    public static MegaCoverage megaCoverage(int columns) {
        MegaPlan plan = megaPlan(BlockPos.ZERO, Direction.SOUTH, columns);
        List<String> itemIds = plan.actions().stream()
                .filter(action -> action.row() == 0)
                .map(MegaAction::itemId)
                .toList();
        return new MegaCoverage(
                plan.columns(),
                MEGA_DEPTHS.size(),
                MEGA_FACES.size(),
                plan.actions().size(),
                itemIds,
                MEGA_DEPTHS.stream().map(Double::doubleToRawLongBits).toList(),
                MEGA_FACES,
                plan.actions().stream().map(MegaAction::descriptor).toList());
    }

    /** Public only for the Forge-native contract; this remains an internal diagnostic surface. */
    public static synchronized BuildResult buildMegaAt(
            ServerLevel world,
            ServerPlayer player,
            BlockPos anchor,
            Direction facing,
            int columns,
            boolean force) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(player, "player");
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        MegaPlan plan;
        try {
            plan = megaPlan(anchor, facing, columns);
        } catch (IllegalArgumentException invalid) {
            ActiveRig active = ACTIVE.get(world);
            return BuildResult.failure(
                    BuildOutcome.INVALID_REQUEST,
                    active == null ? null : active.manifest(),
                    List.of(),
                    invalid.getMessage());
        }
        return buildExecutionAt(world, player, anchor, plan, force);
    }

    public static synchronized CasesRunResult runCases(
            ServerLevel world,
            ServerPlayer player,
            boolean force) {
        return runCasesAt(
                world,
                player,
                defaultCasesAnchor(player),
                player.getDirection(),
                force);
    }

    /** Public only for the Forge-native contract; pages still advance canonically. */
    public static synchronized CasesRunResult runCasesAt(
            ServerLevel world,
            ServerPlayer player,
            BlockPos anchor,
            Direction facing,
            boolean force) {
        return runCasesAtInternal(
                world,
                player,
                anchor,
                facing,
                force,
                CasesTransitionTestFault.NONE);
    }

    /**
     * Forge GameTest-only transition seam. It cannot be reached from the command tree and exists
     * solely to freeze one crash boundary without allowing diagnostics to affect product behavior.
     */
    public static synchronized CasesRunResult runCasesAtForGameTest(
            ServerLevel world,
            ServerPlayer player,
            BlockPos anchor,
            Direction facing,
            boolean force,
            CasesTransitionTestFault fault) {
        return runCasesAtInternal(world, player, anchor, facing, force, fault);
    }

    private static CasesRunResult runCasesAtInternal(
            ServerLevel world,
            ServerPlayer player,
            BlockPos anchor,
            Direction facing,
            boolean force,
            CasesTransitionTestFault fault) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(player, "player");
        fault = Objects.requireNonNull(fault, "fault");
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        facing = horizontal(facing);
        SlabbedRigCaseCatalog.Snapshot snapshot = SlabbedRigCaseCatalog.snapshot();
        if (ACTIVE.get(world) != null) {
            return casesRunFailure(
                    CasesRunOutcome.GENERIC_RIG_ACTIVE,
                    0,
                    snapshot.pageCount(),
                    List.of(),
                    "a launch-local rows/tower/stacks rig is active; clear it first");
        }

        SlabbedRigCaseEvidence.Store store =
                SlabbedRigCaseEvidence.open(world, snapshot);
        SlabbedRigCaseEvidence.ResumeView view = store.inspect();
        int nextPage = view.cursor() == null ? 0 : view.cursor().nextPage();
        if (view.status() == SlabbedRigCaseEvidence.ResumeStatus.COMPLETE) {
            return casesRunFailure(
                    CasesRunOutcome.COMPLETE,
                    nextPage,
                    snapshot.pageCount(),
                    List.of(),
                    "every canonical cases page is already complete");
        }
        if (view.status() != SlabbedRigCaseEvidence.ResumeStatus.FRESH
                && view.status() != SlabbedRigCaseEvidence.ResumeStatus.READY) {
            return casesRunFailure(
                    CasesRunOutcome.STORE_NOT_READY,
                    nextPage,
                    snapshot.pageCount(),
                    List.of(),
                    view.status() + ": " + view.detail());
        }

        int page = store.resolveResumePage();
        CasesPagePlan plan = casesPagePlan(snapshot, anchor, facing, page);
        boolean boardPresent = casesBoardPresent(view);
        if (boardPresent && !force) {
            return casesRunFailure(
                    CasesRunOutcome.BOARD_PRESENT_CLEAR_FIRST,
                    page,
                    snapshot.pageCount(),
                    view.presentBoardOwnership().stream()
                            .map(SlabbedRigCaseEvidence.OwnedCellEvidence::pos)
                            .toList(),
                    "the finalized cases board must be exactly cleared or guarded-force replaced");
        }

        CasesAdmission admission = admitCasesDestination(
                world,
                plan.reservedFootprint(),
                boardPresent ? view.presentBoardOwnership() : List.of());
        if (!admission.admitted()) {
            return casesRunFailure(
                    admission.outcome(),
                    page,
                    snapshot.pageCount(),
                    admission.conflicts(),
                    admission.detail());
        }
        if (boardPresent) {
            CasesStatus status = casesStatusFrom(world, snapshot, view);
            if (!status.clearEligible()) {
                return casesRunFailure(
                        CasesRunOutcome.CLEAR_FAILED,
                        page,
                        snapshot.pageCount(),
                        status.conflicts(),
                        "guarded replacement refused before clear: " + status.detail());
            }
        }

        // The exact successor plan becomes durable before any old-board or new-page mutation.
        // PREPARED_NO_MUTATION is a distinct, command-recoverable state; only beginExecution may
        // cross into the existing unknown-ownership interruption boundary.
        SlabbedRigCaseEvidence.PreparedPage prepared;
        try {
            prepared = store.prepare(plan);
        } catch (RuntimeException failure) {
            return casesRunFailure(
                    CasesRunOutcome.STORE_NOT_READY,
                    page,
                    snapshot.pageCount(),
                    List.of(),
                    "successor prepare failed before prior-board clear: "
                            + failureDetail(failure));
        }

        if (fault == CasesTransitionTestFault.AFTER_PREPARE_BEFORE_REPLACEMENT_CLEAR) {
            if (!boardPresent || !force) {
                throw new IllegalArgumentException(
                        "replacement transition fault requires one guarded forced board");
            }
            return casesRunFailure(
                    CasesRunOutcome.TRANSITION_PAUSED,
                    page,
                    snapshot.pageCount(),
                    List.of(),
                    "GameTest paused after durable successor prepare and before prior-board clear");
        }

        if (boardPresent) {
            CasesClearResult clear = clearPresentCasesBoard(world, store, view);
            if (!clear.success()) {
                return casesRunFailure(
                        CasesRunOutcome.CLEAR_FAILED,
                        page,
                        snapshot.pageCount(),
                        clear.residualCells(),
                        "guarded replacement could not exactly release the prior board: "
                                + clear.detail()
                                + "; the successor remains PREPARED_NO_MUTATION and may be"
                                + " aborted with /slabrig cases resume");
            }
            admission = admitCasesDestination(world, plan.reservedFootprint(), List.of());
            if (!admission.admitted()) {
                return casesRunFailure(
                        admission.outcome(),
                        page,
                        snapshot.pageCount(),
                        admission.conflicts(),
                        "destination changed after exact prior-board release: "
                                + admission.detail()
                                + "; the successor remains PREPARED_NO_MUTATION and may be"
                                + " aborted with /slabrig cases resume");
            }
        }

        try {
            prepared = store.beginExecution(prepared);
        } catch (RuntimeException failure) {
            return casesRunFailure(
                    CasesRunOutcome.STORE_NOT_READY,
                    page,
                    snapshot.pageCount(),
                    List.of(),
                    "could not cross the durable cases execution boundary: "
                            + failureDetail(failure));
        }

        int placed = 0;
        int preserved = 0;
        int rejected = 0;
        int lawRed = 0;
        int deferred = 0;
        try {
            for (int ordinal = 0; ordinal < plan.tiles().size(); ordinal++) {
                CasesTilePlan tile = plan.tiles().get(ordinal);
                SlabbedRigCaseEvidence.CaseResult result = executeCasesTile(
                        world, player, plan, tile, ordinal);
                store.checkpoint(prepared, result);
                recordCasesPhase("after_checkpoint", page, ordinal, tile);
                switch (result.outcome()) {
                    case PLACED -> placed++;
                    case PRESERVED_VANILLA -> preserved++;
                    case REJECTED -> rejected++;
                    case LAW_RED -> lawRed++;
                    case DEFERRED -> deferred++;
                }
            }
            store.finish(prepared);
        } catch (RuntimeException failure) {
            SlabbedRigCaseEvidence.ResumeView interrupted = store.inspect();
            return new CasesRunResult(
                    CasesRunOutcome.EXECUTION_INTERRUPTED,
                    page,
                    snapshot.pageCount(),
                    plan.tiles().size(),
                    plan.autoTiles().size(),
                    deferred,
                    placed,
                    preserved,
                    rejected,
                    lawRed,
                    interrupted.completedOwnership().size(),
                    List.of(),
                    failureDetail(failure));
        }

        SlabbedRigCaseEvidence.ResumeView complete = store.inspect();
        return new CasesRunResult(
                CasesRunOutcome.PAGE_COMPLETED,
                page,
                snapshot.pageCount(),
                plan.tiles().size(),
                plan.autoTiles().size(),
                deferred,
                placed,
                preserved,
                rejected,
                lawRed,
                complete.presentBoardOwnership().size(),
                List.of(),
                "cases page checkpointed, finalized, and claimed as the present board");
    }

    public static synchronized CasesStatus casesStatus(ServerLevel world) {
        Objects.requireNonNull(world, "world");
        SlabbedRigCaseCatalog.Snapshot snapshot = SlabbedRigCaseCatalog.snapshot();
        if (!SlabbedRigCaseEvidence.hasStore(world)) {
            return CasesStatus.absent(snapshot.pageCount());
        }
        SlabbedRigCaseEvidence.Store store =
                SlabbedRigCaseEvidence.open(world, snapshot);
        return casesStatusFrom(world, snapshot, store.inspect());
    }

    public static synchronized CasesResumeResult resumeCases(ServerLevel world) {
        Objects.requireNonNull(world, "world");
        CasesStatus before = casesStatus(world);
        if (!before.storePresent()) {
            return new CasesResumeResult(
                    CasesResumeOutcome.NO_REPAIR, before, "no durable cases store exists");
        }
        SlabbedRigCaseEvidence.Store store = SlabbedRigCaseEvidence.open(
                world, SlabbedRigCaseCatalog.snapshot());
        try {
            CasesResumeOutcome outcome;
            if (before.evidenceStatus()
                    == SlabbedRigCaseEvidence.ResumeStatus.PREPARED_NO_MUTATION) {
                store.abortPreparedPage();
                outcome = CasesResumeOutcome.ABORTED_PREPARED_PAGE;
            } else if (before.evidenceStatus()
                    == SlabbedRigCaseEvidence.ResumeStatus.COMPLETED_PENDING_FINAL) {
                store.repairCompletedPage();
                outcome = CasesResumeOutcome.FINALIZED_COMPLETED_PAGE;
            } else if (before.evidenceStatus()
                    == SlabbedRigCaseEvidence.ResumeStatus.FINAL_PENDING_CURSOR) {
                store.repairFinalCursor();
                outcome = CasesResumeOutcome.ADVANCED_SEALED_PAGE;
            } else if (before.releaseRepairEligible()) {
                store.releasePresentBoard();
                outcome = CasesResumeOutcome.RELEASED_EMPTY_BOARD;
            } else {
                return new CasesResumeResult(
                        CasesResumeOutcome.REFUSED,
                        before,
                        "only a mutation-free prepared page, fully checkpointed page, sealed"
                                + " final, or already-empty board has deterministic repair");
            }
            return new CasesResumeResult(
                    outcome, casesStatus(world), "deterministic cases recovery completed");
        } catch (RuntimeException failure) {
            return new CasesResumeResult(
                    CasesResumeOutcome.REFUSED, casesStatus(world), failureDetail(failure));
        }
    }

    public static synchronized CasesClearResult clearCases(ServerLevel world) {
        Objects.requireNonNull(world, "world");
        if (ACTIVE.get(world) != null) {
            return new CasesClearResult(
                    CasesClearOutcome.GENERIC_RIG_ACTIVE,
                    0,
                    List.of(),
                    "a launch-local rows/tower/stacks rig is active; clear it first");
        }
        CasesStatus status = casesStatus(world);
        if (!status.storePresent()) {
            return new CasesClearResult(
                    CasesClearOutcome.NO_ACTIVE, 0, List.of(), "no finalized cases board is present");
        }
        if (status.active()) {
            return new CasesClearResult(
                    CasesClearOutcome.INTERRUPTED,
                    0,
                    status.conflicts(),
                    "active cases evidence must be deterministically repaired or left untouched");
        }
        if (!status.boardPresent()) {
            return new CasesClearResult(
                    CasesClearOutcome.NO_ACTIVE, 0, List.of(), "no finalized cases board is present");
        }
        if (!status.clearEligible()) {
            return new CasesClearResult(
                    CasesClearOutcome.CONFLICT,
                    0,
                    status.conflicts(),
                    "cases clear is exact and all-or-nothing; foreign changes were preserved");
        }
        SlabbedRigCaseEvidence.Store store = SlabbedRigCaseEvidence.open(
                world, SlabbedRigCaseCatalog.snapshot());
        return clearPresentCasesBoard(world, store, store.inspect());
    }

    private static CasesRunResult casesRunFailure(
            CasesRunOutcome outcome,
            int page,
            int pageCount,
            List<BlockPos> conflicts,
            String detail) {
        return new CasesRunResult(
                outcome,
                page,
                pageCount,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                conflicts,
                detail);
    }

    private static CasesStatus casesStatusFrom(
            ServerLevel world,
            SlabbedRigCaseCatalog.Snapshot snapshot,
            SlabbedRigCaseEvidence.ResumeView view) {
        if (view.cursor() == null) {
            return CasesStatus.fault(view.status(), snapshot.pageCount(), view.detail());
        }
        LinkedHashMap<BlockPos, SlabbedRigCaseEvidence.OwnedCellEvidence> tracked =
                new LinkedHashMap<>();
        view.presentBoardOwnership().forEach(cell -> tracked.put(cell.pos(), cell));
        if (view.cursor().active() != null) {
            view.completedOwnership().forEach(cell -> tracked.putIfAbsent(cell.pos(), cell));
        }
        int intact = 0;
        int absent = 0;
        List<BlockPos> conflicts = new ArrayList<>();
        for (SlabbedRigCaseEvidence.OwnedCellEvidence cell : tracked.values()) {
            if (matchesCasesOwned(world, cell)) {
                intact++;
            } else if (casesOwnedAbsent(world, cell)) {
                absent++;
            } else {
                conflicts.add(cell.pos());
            }
        }
        boolean active = view.cursor().active() != null;
        boolean boardPresent = casesBoardPresent(view);
        boolean releaseRepairEligible = boardPresent
                && !active
                && view.presentBoardOwnership().stream()
                        .allMatch(cell -> casesOwnedAbsent(world, cell));
        return new CasesStatus(
                true,
                view.status(),
                view.cursor().nextPage(),
                snapshot.pageCount(),
                active,
                active ? view.cursor().active().nextCaseOrdinal() : 0,
                view.completedOwnership().size(),
                boardPresent,
                view.presentBoardOwnership().size(),
                intact,
                absent,
                distinctPositions(conflicts),
                boardPresent && !active && conflicts.isEmpty(),
                releaseRepairEligible,
                view.detail());
    }

    private static boolean casesBoardPresent(
            SlabbedRigCaseEvidence.ResumeView view) {
        return view.cursor() != null
                && !view.cursor().boardFinalHash().equals(SlabbedRigCaseEvidence.GENESIS);
    }

    private static CasesAdmission admitCasesDestination(
            ServerLevel world,
            List<BlockPos> reserved,
            List<SlabbedRigCaseEvidence.OwnedCellEvidence> allowedOwned) {
        Map<BlockPos, SlabbedRigCaseEvidence.OwnedCellEvidence> allowed =
                new LinkedHashMap<>();
        for (SlabbedRigCaseEvidence.OwnedCellEvidence cell : allowedOwned) {
            allowed.put(cell.pos(), cell);
        }
        List<BlockPos> outOfBounds = new ArrayList<>();
        List<BlockPos> unloaded = new ArrayList<>();
        List<BlockPos> occupied = new ArrayList<>();
        for (BlockPos pos : reserved) {
            if (!world.isInWorldBounds(pos)) {
                outOfBounds.add(pos);
                continue;
            }
            if (!world.hasChunkAt(pos)) {
                unloaded.add(pos);
                continue;
            }
            SlabbedRigCaseEvidence.OwnedCellEvidence owned = allowed.get(pos);
            if (owned != null) {
                if (!matchesCasesOwned(world, owned) && !casesOwnedAbsent(world, owned)) {
                    occupied.add(pos);
                }
            } else if (!world.getBlockState(pos).isAir()
                    || SlabAnchorAttachment.storedPlacementDyFact(world, pos).present()) {
                occupied.add(pos);
            }
        }
        if (!outOfBounds.isEmpty()) {
            return CasesAdmission.refused(
                    CasesRunOutcome.OUT_OF_BOUNDS,
                    outOfBounds,
                    "cases footprint crosses the world build limits; no cells changed");
        }
        if (!unloaded.isEmpty()) {
            return CasesAdmission.refused(
                    CasesRunOutcome.UNLOADED,
                    unloaded,
                    "cases footprint crosses an unloaded chunk; no cells changed");
        }
        if (!occupied.isEmpty()) {
            return CasesAdmission.refused(
                    CasesRunOutcome.OCCUPIED,
                    occupied,
                    "cases footprint contains foreign cells; guarded force never bulldozes them");
        }
        return CasesAdmission.allow();
    }

    private static CasesClearResult clearPresentCasesBoard(
            ServerLevel world,
            SlabbedRigCaseEvidence.Store store,
            SlabbedRigCaseEvidence.ResumeView view) {
        if (view.cursor() == null || view.cursor().active() != null || !casesBoardPresent(view)) {
            return new CasesClearResult(
                    CasesClearOutcome.INTERRUPTED,
                    0,
                    List.of(),
                    "cases board is not in a clearable finalized state");
        }
        List<SlabbedRigCaseEvidence.OwnedCellEvidence> owned =
                store.rehydratePresentBoardOwnership();
        List<BlockPos> conflicts = owned.stream()
                .filter(cell -> !matchesCasesOwned(world, cell)
                        && !casesOwnedAbsent(world, cell))
                .map(SlabbedRigCaseEvidence.OwnedCellEvidence::pos)
                .toList();
        if (!conflicts.isEmpty()) {
            return new CasesClearResult(
                    CasesClearOutcome.CONFLICT,
                    0,
                    conflicts,
                    "cases board contains foreign changes; exact clear changed nothing");
        }

        int removed = 0;
        int alreadyAbsent = 0;
        for (int index = owned.size() - 1; index >= 0; index--) {
            SlabbedRigCaseEvidence.OwnedCellEvidence cell = owned.get(index);
            if (casesOwnedAbsent(world, cell)) {
                alreadyAbsent++;
            } else if (removeCasesOwnedCell(world, cell.pos())) {
                removed++;
            }
        }
        List<BlockPos> residual = owned.stream()
                .filter(cell -> !casesOwnedAbsent(world, cell))
                .map(SlabbedRigCaseEvidence.OwnedCellEvidence::pos)
                .toList();
        if (!residual.isEmpty()) {
            return new CasesClearResult(
                    CasesClearOutcome.RESIDUE,
                    removed,
                    residual,
                    "some exact cases-owned cells remain; durable release was not published");
        }
        try {
            store.releasePresentBoard();
        } catch (RuntimeException failure) {
            return new CasesClearResult(
                    CasesClearOutcome.RELEASE_PENDING,
                    removed,
                    List.of(),
                    "world clear completed but durable release is pending: "
                            + failureDetail(failure));
        }
        return new CasesClearResult(
                CasesClearOutcome.CLEARED,
                removed,
                List.of(),
                "exact cases board cleared and durably released; already-absent="
                        + alreadyAbsent);
    }

    private static SlabbedRigCaseEvidence.CaseResult executeCasesTile(
            ServerLevel world,
            ServerPlayer player,
            CasesPagePlan page,
            CasesTilePlan tile,
            int ordinal) {
        if (!tile.autoEligible()) {
            recordCasesPhase("deferred", page.page().page(), ordinal, tile);
            return new SlabbedRigCaseEvidence.CaseResult(
                    ordinal,
                    tile.definition().id(),
                    SlabbedRigCaseEvidence.StructureStatus.DEFERRED,
                    SlabbedRigCaseEvidence.AttemptStatus.NOT_ATTEMPTED,
                    SlabbedRigCaseEvidence.CaseOutcome.DEFERRED,
                    true,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }

        for (BlockPos pos : tile.reservedCells()) {
            requireEmptyMutationCell(world, pos);
        }
        List<SlabbedRigCaseEvidence.CellEvidence> before =
                snapshotCasesEvidence(world, tile.reservedCells());
        List<ItemStack> inventory = snapshotCasesInventory(player);
        RuntimeException mutationFailure = null;
        InteractionResult[] interaction = {InteractionResult.PASS};
        BlockPos[] resolvedTarget = {null};
        recordCasesPhase("before_fixture", page.page().page(), ordinal, tile);
        try {
            for (RigCase.FixtureCell fixture : tile.fixtures()) {
                placeFixture(world, tile.definition().id(), fixture);
            }
            ResourceLocation itemId = ResourceLocation.tryParse(tile.definition().item().id());
            if (itemId == null) {
                throw new IllegalStateException(
                        "cases item ID is invalid: " + tile.definition().item().id());
            }
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (!(item instanceof BlockItem)
                    || !BuiltInRegistries.ITEM.getKey(item).equals(itemId)) {
                throw new IllegalStateException(
                        "cases item no longer resolves to its exact BlockItem: " + itemId);
            }
            InteractionHand hand = InteractionHand.MAIN_HAND;
            ItemStack proxyStack = new ItemStack(item);
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(tile.cursor()).add(0.0d, 0.5d, 0.0d),
                    Direction.UP,
                    tile.cursor(),
                    false);
            BlockPlaceContext placementContext = new BlockPlaceContext(
                    world, player, hand, proxyStack, hit);
            resolvedTarget[0] = placementContext.getClickedPos().immutable();
            if (!resolvedTarget[0].equals(tile.cursor())
                    && !resolvedTarget[0].equals(tile.target())) {
                throw new IllegalStateException(
                        "cases useOn resolved outside its clicked-or-above placement cells: "
                                + resolvedTarget[0].toShortString());
            }
            player.setItemInHand(hand, proxyStack);
            SlabbedDiagnosticsBridge.withActionOrigin(
                    SlabbedDiagnosticsBridge.AUTO_USEON_PROXY,
                    new SlabbedDiagnosticsBridge.ActionOriginContext(
                            player.getUUID().toString(),
                            world.dimension().location().toString(),
                            resolvedTarget[0]),
                    () -> interaction[0] = ForgeHooks.onPlaceItemIntoWorld(
                            new UseOnContext(player, hand, hit)));
        } catch (RuntimeException failure) {
            mutationFailure = failure;
        } finally {
            restoreCasesInventory(player, inventory);
        }
        recordCasesPhase("after_use_on", page.page().page(), ordinal, tile);

        List<SlabbedRigCaseEvidence.CellEvidence> after =
                snapshotCasesEvidence(world, tile.reservedCells());
        boolean inventoryRestored = casesInventoryMatches(player, inventory);
        List<BlockPos> changed = changedCasesCells(before, after);
        Set<BlockPos> fixturePositions = tile.fixtures().stream()
                .map(RigCase.FixtureCell::pos)
                .collect(java.util.stream.Collectors.toSet());
        List<SlabbedRigCaseEvidence.OwnedCellEvidence> owned = new ArrayList<>();
        for (BlockPos pos : changed) {
            SlabbedRigCaseEvidence.CellEvidence evidence = after.stream()
                    .filter(cell -> cell.pos().equals(pos))
                    .findFirst()
                    .orElseThrow();
            owned.add(new SlabbedRigCaseEvidence.OwnedCellEvidence(
                    pos,
                    evidence.state(),
                    evidence.storedDy(),
                    fixturePositions.contains(pos)
                            ? RigManifest.CellRole.FIXTURE
                            : RigManifest.CellRole.SUBJECT,
                    tile.definition().id()));
        }

        boolean placed = resolvedTarget[0] != null
                && changed.contains(resolvedTarget[0])
                && !world.getBlockState(resolvedTarget[0]).isAir();
        boolean subjectChanged = changed.stream().anyMatch(pos -> !fixturePositions.contains(pos));
        SlabbedRigCaseEvidence.CaseOutcome outcome;
        SlabbedRigCaseEvidence.StructureStatus structure;
        if (mutationFailure != null || !inventoryRestored) {
            outcome = SlabbedRigCaseEvidence.CaseOutcome.LAW_RED;
            structure = SlabbedRigCaseEvidence.StructureStatus.INCOMPLETE;
        } else if (placed && interaction[0].consumesAction()) {
            outcome = SlabbedRigCaseEvidence.CaseOutcome.PLACED;
            structure = SlabbedRigCaseEvidence.StructureStatus.COMPLETE;
        } else if (subjectChanged) {
            outcome = SlabbedRigCaseEvidence.CaseOutcome.PRESERVED_VANILLA;
            structure = SlabbedRigCaseEvidence.StructureStatus.INCOMPLETE;
        } else {
            outcome = SlabbedRigCaseEvidence.CaseOutcome.REJECTED;
            structure = SlabbedRigCaseEvidence.StructureStatus.INCOMPLETE;
        }
        if (mutationFailure != null) {
            SlabbedDiagnosticsBridge.log(
                    "slabrig_cases",
                    "phase=law_red page=" + page.page().page()
                            + " ordinal=" + ordinal
                            + " case=" + tile.definition().id()
                            + " detail=" + failureDetail(mutationFailure));
        }
        return new SlabbedRigCaseEvidence.CaseResult(
                ordinal,
                tile.definition().id(),
                structure,
                SlabbedRigCaseEvidence.AttemptStatus.ATTEMPTED,
                outcome,
                inventoryRestored,
                before,
                after,
                owned,
                List.of());
    }

    private static List<SlabbedRigCaseEvidence.CellEvidence> snapshotCasesEvidence(
            ServerLevel world,
            List<BlockPos> positions) {
        return positions.stream()
                .map(pos -> new SlabbedRigCaseEvidence.CellEvidence(
                        pos,
                        world.getBlockState(pos),
                        SlabAnchorAttachment.storedPlacementDyFact(world, pos)))
                .toList();
    }

    private static List<BlockPos> changedCasesCells(
            List<SlabbedRigCaseEvidence.CellEvidence> before,
            List<SlabbedRigCaseEvidence.CellEvidence> after) {
        List<BlockPos> changed = new ArrayList<>();
        for (int index = 0; index < before.size(); index++) {
            SlabbedRigCaseEvidence.CellEvidence left = before.get(index);
            SlabbedRigCaseEvidence.CellEvidence right = after.get(index);
            if (!left.pos().equals(right.pos())
                    || !left.state().equals(right.state())
                    || !left.storedDy().equals(right.storedDy())) {
                changed.add(left.pos());
            }
        }
        return List.copyOf(changed);
    }

    private static List<ItemStack> snapshotCasesInventory(ServerPlayer player) {
        List<ItemStack> snapshot = new ArrayList<>(player.getInventory().getContainerSize());
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            snapshot.add(player.getInventory().getItem(slot).copy());
        }
        return List.copyOf(snapshot);
    }

    private static void restoreCasesInventory(
            ServerPlayer player,
            List<ItemStack> snapshot) {
        for (int slot = 0; slot < snapshot.size(); slot++) {
            player.getInventory().setItem(slot, snapshot.get(slot).copy());
        }
        player.getInventory().setChanged();
    }

    private static boolean casesInventoryMatches(
            ServerPlayer player,
            List<ItemStack> snapshot) {
        if (player.getInventory().getContainerSize() != snapshot.size()) {
            return false;
        }
        for (int slot = 0; slot < snapshot.size(); slot++) {
            if (!ItemStack.matches(
                    snapshot.get(slot), player.getInventory().getItem(slot))) {
                return false;
            }
        }
        return true;
    }

    private static void recordCasesPhase(
            String phase,
            int page,
            int ordinal,
            CasesTilePlan tile) {
        SlabbedDiagnosticsBridge.log(
                "slabrig_cases",
                "phase=" + requireText(phase, "phase")
                        + " page=" + page
                        + " ordinal=" + ordinal
                        + " case=" + tile.definition().id()
                        + " placement=" + tile.target().toShortString());
    }

    private static boolean matchesCasesOwned(
            ServerLevel world,
            SlabbedRigCaseEvidence.OwnedCellEvidence cell) {
        return world.isInWorldBounds(cell.pos())
                && world.hasChunkAt(cell.pos())
                && world.getBlockState(cell.pos()).equals(cell.expectedState())
                && SlabAnchorAttachment.storedPlacementDyFact(world, cell.pos())
                        .equals(cell.expectedStoredDy());
    }

    private static boolean casesOwnedAbsent(
            ServerLevel world,
            SlabbedRigCaseEvidence.OwnedCellEvidence cell) {
        return world.isInWorldBounds(cell.pos())
                && world.hasChunkAt(cell.pos())
                && world.getBlockState(cell.pos()).isAir()
                && !SlabAnchorAttachment.storedPlacementDyFact(world, cell.pos()).present();
    }

    private static boolean removeCasesOwnedCell(
            ServerLevel world,
            BlockPos pos) {
        if (!world.isInWorldBounds(pos) || !world.hasChunkAt(pos)) {
            return false;
        }
        if (!world.getBlockState(pos).isAir()) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (world.getBlockState(pos).isAir()) {
            SlabAnchorAttachment.clearPlacementTruth(world, pos);
        }
        return world.getBlockState(pos).isAir()
                && !SlabAnchorAttachment.storedPlacementDyFact(world, pos).present();
    }

    private static BuildResult buildPlanAt(
            ServerLevel world,
            ServerPlayer player,
            BlockPos anchor,
            String mode,
            List<RigCase> cases,
            boolean force) {
        return buildExecutionAt(
                world,
                player,
                anchor,
                new CaseExecutionPlan(mode, cases),
                force);
    }

    private static BuildResult buildExecutionAt(
            ServerLevel world,
            ServerPlayer player,
            BlockPos anchor,
            ExecutionPlan plan,
            boolean force) {
        Objects.requireNonNull(plan, "plan");
        if (!KNOWN_MODES.contains(plan.mode()) || plan.caseIds().isEmpty()) {
            throw new IllegalArgumentException("unknown or empty rig mode " + plan.mode());
        }

        List<BlockPos> footprint = plan.footprint();

        ActiveRig previous = ACTIVE.get(world);
        if (previous != null) {
            if (!force) {
                return BuildResult.failure(
                        BuildOutcome.ACTIVE_RIG,
                        previous.manifest(),
                        List.of(),
                        "an owned rig is already active; use " + plan.mode()
                                + " force to replace it");
            }
            if (!previous.restorable()) {
                return BuildResult.failure(
                        BuildOutcome.ACTIVE_RIG,
                        previous.manifest(),
                        previous.manifest().ownedCells().stream()
                                .map(RigManifest.OwnedCell::pos)
                                .toList(),
                        "active rig is rollback residue; clear force before replacement");
            }
            // Guarded replacement is a two-phase operation. Validate the complete destination
            // while the old manifest is still intact; existing cells owned by that manifest are
            // allowed, but an unloaded or foreign destination refuses without clearing anything.
            // Reserved-but-unowned cells from the previous plan must also remain clean: replay
            // rollback is allowed to touch that exact footprint, so admitting a foreign cell there
            // could make a later failed replacement delete somebody else's block.
            BuildResult replacementRefusal =
                    preflightReplacement(world, previous, footprint);
            if (replacementRefusal != null) {
                return replacementRefusal;
            }
            ClearResult cleared = clear(world, true);
            if (!cleared.success()) {
                return BuildResult.failure(
                        BuildOutcome.ROLLBACK_RESIDUE,
                        cleared.manifest(),
                        cleared.residualCells(),
                        "existing owned rig could not be fully force-cleared");
            }
        }

        if (previous == null) {
            List<BlockPos> outOfBounds = new ArrayList<>();
            List<BlockPos> unloaded = new ArrayList<>();
            List<BlockPos> occupied = new ArrayList<>();
            for (BlockPos pos : footprint) {
                if (!world.isInWorldBounds(pos)) {
                    outOfBounds.add(pos);
                    continue;
                }
                if (!world.hasChunkAt(pos)) {
                    unloaded.add(pos);
                    continue;
                }
                if (!world.getBlockState(pos).isAir()
                        || SlabAnchorAttachment.storedPlacementDyFact(world, pos).present()) {
                    occupied.add(pos);
                }
            }
            if (!outOfBounds.isEmpty()) {
                return BuildResult.failure(
                        BuildOutcome.OUT_OF_BOUNDS, null, outOfBounds,
                        "rig footprint crosses the world build limits; no cells changed");
            }
            if (!unloaded.isEmpty()) {
                return BuildResult.failure(
                        BuildOutcome.UNLOADED, null, unloaded,
                        "rig footprint crosses an unloaded chunk; no cells changed");
            }
            if (!occupied.isEmpty()) {
                return BuildResult.failure(
                        BuildOutcome.OCCUPIED, null, occupied,
                        "rig footprint contains foreign cells; force never bulldozes unowned blocks");
            }
        }

        UUID runId = UUID.randomUUID();
        PlanAttempt attempt = executePlan(world, player, plan);
        if (attempt.success()) {
            RigManifest manifest = manifest(
                    runId, world, player, anchor, plan, attempt.created(),
                    attempt.fixtureWrites(), attempt.fixtureTruthWrites(),
                    attempt.subjectUseOnCalls(), attempt.resolutions(),
                    attempt.structuralReport());
            ACTIVE.put(world, ActiveRig.complete(manifest, plan));
            return new BuildResult(
                    BuildOutcome.BUILT, manifest, List.of(), plan.mode() + " rig built");
        }

        RigManifest residueManifest = publishResidue(
                runId, world, player, anchor, plan, attempt);
        if (residueManifest != null) {
            return BuildResult.failure(
                    BuildOutcome.ROLLBACK_RESIDUE,
                    residueManifest,
                    residueManifest.ownedCells().stream()
                            .map(RigManifest.OwnedCell::pos)
                            .toList(),
                    failureDetail(attempt.failure()) + "; rollback residue remains owned");
        }

        if (previous != null) {
            RestoreResult restored = restorePrevious(world, player, previous);
            if (restored.success()) {
                return BuildResult.failure(
                        BuildOutcome.PLACEMENT_FAILED,
                        previous.manifest(),
                        List.of(),
                        failureDetail(attempt.failure()) + "; previous rig restored exactly");
            }
            if (restored.manifest() == null && restored.residualCells().isEmpty()) {
                return BuildResult.failure(
                        BuildOutcome.RESTORE_FAILED,
                        null,
                        List.of(),
                        failureDetail(attempt.failure())
                                + "; previous rig restore failed cleanly: " + restored.detail());
            }
            return BuildResult.failure(
                    BuildOutcome.ROLLBACK_RESIDUE,
                    restored.manifest(),
                    restored.residualCells(),
                    failureDetail(attempt.failure())
                            + "; previous rig restore failed: " + restored.detail());
        }

        return BuildResult.failure(
                BuildOutcome.PLACEMENT_FAILED,
                null,
                List.of(),
                failureDetail(attempt.failure()));
    }

    /** Returns a refusal while preserving {@code active}, or {@code null} when replacement is safe. */
    private static BuildResult preflightReplacement(
            ServerLevel world,
            ActiveRig active,
            List<BlockPos> replacementFootprint) {
        RigManifest activeManifest = active.manifest();
        Set<BlockPos> activePositions = activeManifest.ownedCells().stream()
                .map(RigManifest.OwnedCell::pos)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<BlockPos> outOfBounds = new ArrayList<>();
        List<BlockPos> unloaded = new ArrayList<>();
        List<BlockPos> foreign = new ArrayList<>();
        for (BlockPos pos : replacementFootprint) {
            if (!world.isInWorldBounds(pos)) {
                outOfBounds.add(pos);
                continue;
            }
            if (!world.hasChunkAt(pos)) {
                unloaded.add(pos);
                continue;
            }
            if (!activePositions.contains(pos)
                    && (!world.getBlockState(pos).isAir()
                            || SlabAnchorAttachment.storedPlacementDyFact(world, pos).present())) {
                foreign.add(pos);
            }
        }
        for (BlockPos reserved : active.plan().footprint()) {
            if (activePositions.contains(reserved)) {
                continue;
            }
            if (!world.isInWorldBounds(reserved)) {
                outOfBounds.add(reserved);
            } else if (!world.hasChunkAt(reserved)) {
                unloaded.add(reserved);
            } else if (!world.getBlockState(reserved).isAir()
                    || SlabAnchorAttachment.storedPlacementDyFact(world, reserved).present()) {
                foreign.add(reserved);
            }
        }
        // Force-clear is allowed to remove changed owned cells, but never to partially consume an
        // unloaded manifest, replay across a foreign reserved cell, or clear an unbreakable owned
        // cell before the new rig has been admitted.
        for (RigManifest.OwnedCell cell : activeManifest.ownedCells()) {
            if (!world.isInWorldBounds(cell.pos())) {
                outOfBounds.add(cell.pos());
            } else if (!world.hasChunkAt(cell.pos())) {
                unloaded.add(cell.pos());
            } else {
                BlockState live = world.getBlockState(cell.pos());
                if (!live.isAir() && live.getDestroySpeed(world, cell.pos()) < 0.0f) {
                    foreign.add(cell.pos());
                }
            }
        }
        if (!outOfBounds.isEmpty()) {
            return BuildResult.failure(
                    BuildOutcome.OUT_OF_BOUNDS,
                    activeManifest,
                    distinctPositions(outOfBounds),
                    "replacement refused before clear: destination crosses world build limits");
        }
        if (!unloaded.isEmpty()) {
            return BuildResult.failure(
                    BuildOutcome.UNLOADED,
                    activeManifest,
                    distinctPositions(unloaded),
                    "replacement refused before clear: destination or active rig is unloaded");
        }
        if (!foreign.isEmpty()) {
            return BuildResult.failure(
                    BuildOutcome.OCCUPIED,
                    activeManifest,
                    distinctPositions(foreign),
                    "replacement refused before clear: destination contains foreign cells"
                            + ", the previous replay footprint is occupied,"
                            + " or an owned cell is unbreakable");
        }
        return null;
    }

    /** Deterministic first catalog entry: three independent slab-and-stone rows. */
    public static List<RigCase> rowsPlan(BlockPos anchor, Direction facing) {
        Objects.requireNonNull(anchor, "anchor");
        Direction right = horizontal(facing).getClockWise();
        int[] offsets = {-2, 0, 2};
        String[] labels = {"left", "center", "right"};
        List<RigCase> cases = new ArrayList<>(3);
        for (int index = 0; index < offsets.length; index++) {
            BlockPos support = anchor.relative(right, offsets[index]).immutable();
            BlockPos target = support.above().immutable();
            BlockState slab = Blocks.OAK_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            PlacementAim aim = new PlacementAim(
                    support,
                    Direction.UP,
                    Vec3.atCenterOf(support).add(0.0d, 0.5d, 0.0d),
                    target,
                    new ItemStack(Blocks.STONE));
            cases.add(new RigCase(
                    "rows." + labels[index],
                    List.of(new RigCase.FixtureCell(support, slab)),
                    List.of(new RigCase.SubjectPlacement(
                            aim, Blocks.STONE, LandingResolution.Lane.LOWERED))));
        }
        return List.copyOf(cases);
    }

    /** Canonical bare tower: the compound-visible -1.0 fixture, not the numeric deep tower. */
    public static List<RigCase> towerPlan(BlockPos anchor, Direction facing) {
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        Direction left = horizontal(facing).getCounterClockWise();
        BlockPos compoundFullBlock = anchor.above(4).immutable();
        BlockPos sideSlab = compoundFullBlock.relative(left).immutable();
        BlockState verticalSlab = Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState sideSlabState = Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        return List.of(new RigCase(
                "tower.compound_visible",
                List.of(
                        new RigCase.FixtureCell(anchor, Blocks.STONE.defaultBlockState()),
                        new RigCase.FixtureCell(anchor.above(), verticalSlab),
                        new RigCase.FixtureCell(
                                anchor.above(2), Blocks.STONE.defaultBlockState()),
                        new RigCase.FixtureCell(anchor.above(3), verticalSlab),
                        new RigCase.FixtureCell(
                                compoundFullBlock,
                                Blocks.STONE.defaultBlockState(),
                                RigCase.FixtureAuthorship.compoundFullBlock()),
                        new RigCase.FixtureCell(
                                sideSlab,
                                sideSlabState,
                                RigCase.FixtureAuthorship
                                        .compoundVisibleSideLowerSlab(compoundFullBlock))),
                List.of()));
    }

    /** Full palette-by-depth-by-face diagnostic matrix. It observes law; it does not admit it. */
    private static MegaPlan megaPlan(
            BlockPos anchor,
            Direction facing,
            int requestedColumns) {
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        facing = horizontal(facing);
        if (requestedColumns < MIN_MEGA_COLUMNS || requestedColumns > MAX_MEGA_COLUMNS) {
            throw new IllegalArgumentException(
                    "mega columns must be " + MIN_MEGA_COLUMNS + ".." + MAX_MEGA_COLUMNS);
        }
        List<Item> palette = SlabbedOperatorTools.paletteItems();
        int columns = requestedColumns;
        if (columns == 0) {
            throw new IllegalArgumentException("mega requires at least one palette item");
        }

        Direction right = facing.getClockWise();
        List<MegaAction> actions = new ArrayList<>(columns * MEGA_ROW_COUNT);
        Set<BlockPos> footprint = new LinkedHashSet<>();
        for (int column = 0; column < columns; column++) {
            Item item = palette.get(column);
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
            for (int depthIndex = 0; depthIndex < MEGA_DEPTHS.size(); depthIndex++) {
                double expectedDy = MEGA_DEPTHS.get(depthIndex);
                for (int faceIndex = 0; faceIndex < MEGA_FACES.size(); faceIndex++) {
                    Direction face = MEGA_FACES.get(faceIndex);
                    int row = depthIndex * MEGA_FACES.size() + faceIndex;
                    BlockPos tile = anchor
                            .relative(facing, row * MEGA_TILE_SPACING)
                            .relative(right, column * MEGA_TILE_SPACING)
                            .immutable();
                    BlockPos labelPedestal = tile.above(7).immutable();
                    BlockPos labelPos = tile.above(8).immutable();
                    BlockPos clicked = tile.above(MEGA_SUPPORT_Y).immutable();
                    BlockPos intendedTarget = clicked.relative(face).immutable();
                    String label = String.format(Locale.ROOT, "R%02d/C%02d", row + 1, column + 1);
                    String orientation = orientation(face);
                    String caseId = "mega.r" + twoDigits(row + 1)
                            + ".c" + twoDigits(column + 1)
                            + ".item." + itemId.replace(':', '.')
                            + ".dy." + dyToken(expectedDy)
                            + ".face." + face.getName();
                    List<RigCase.FixtureCell> fixtures = List.of(
                            new RigCase.FixtureCell(
                                    labelPedestal,
                                    Blocks.STONE.defaultBlockState()),
                            new RigCase.FixtureCell(
                                    labelPos,
                                    Blocks.OAK_SIGN.defaultBlockState()),
                            new RigCase.FixtureCell(
                                    clicked,
                                    Blocks.STONE.defaultBlockState(),
                                    RigCase.FixtureAuthorship.storedDy(expectedDy)));
                    MegaAction action = new MegaAction(
                            caseId,
                            label,
                            column,
                            row,
                            itemId,
                            item,
                            fixtures,
                            labelPos,
                            clicked,
                            face,
                            intendedTarget,
                            expectedDy,
                            orientation);
                    actions.add(action);
                    fixtures.forEach(fixture -> footprint.add(fixture.pos()));
                    footprint.addAll(actionEnvelope(clicked, intendedTarget));
                }
            }
        }
        return new MegaPlan(columns, actions, List.copyOf(footprint));
    }

    private static String orientation(Direction face) {
        return switch (face) {
            case UP -> "floor_up";
            case DOWN -> "ceiling_down";
            case NORTH, EAST, SOUTH, WEST -> "wall_" + face.getName();
        };
    }

    private static String dyToken(double dy) {
        return Double.toString(dy).replace('-', 'm').replace('.', 'p');
    }

    private static String twoDigits(int value) {
        return String.format(Locale.ROOT, "%02d", value);
    }

    /** Immutable readback of the actual Mega planner, used by Forge GameTests and artifact review. */
    public record MegaCoverage(
            int columns,
            int depths,
            int faces,
            int attempts,
            List<String> itemIds,
            List<Long> depthDyBits,
            List<Direction> faceOrder,
            List<MegaCaseDescriptor> cases) {
        public MegaCoverage {
            itemIds = List.copyOf(Objects.requireNonNull(itemIds, "itemIds"));
            depthDyBits = List.copyOf(Objects.requireNonNull(depthDyBits, "depthDyBits"));
            faceOrder = List.copyOf(Objects.requireNonNull(faceOrder, "faceOrder"));
            cases = List.copyOf(Objects.requireNonNull(cases, "cases"));
            if (columns < 1
                    || depths != MEGA_DEPTHS.size()
                    || faces != MEGA_FACES.size()
                    || itemIds.size() != columns
                    || depthDyBits.size() != depths
                    || faceOrder.size() != faces
                    || attempts != columns * depths * faces
                    || cases.size() != attempts
                    || new LinkedHashSet<>(itemIds).size() != itemIds.size()
                    || new LinkedHashSet<>(cases.stream().map(MegaCaseDescriptor::caseId).toList())
                            .size() != cases.size()
                    || new LinkedHashSet<>(cases.stream().map(MegaCaseDescriptor::label).toList())
                            .size() != cases.size()) {
                throw new IllegalArgumentException("invalid mega coverage");
            }
        }

        public List<String> caseIds() {
            return cases.stream().map(MegaCaseDescriptor::caseId).toList();
        }
    }

    public record MegaCaseDescriptor(
            String caseId,
            String label,
            String itemId,
            int column,
            int row,
            long expectedDyBits,
            Direction face,
            String orientation) {
        public MegaCaseDescriptor {
            caseId = requireText(caseId, "caseId");
            label = requireText(label, "label");
            itemId = requireText(itemId, "itemId");
            face = Objects.requireNonNull(face, "face");
            orientation = requireText(orientation, "orientation");
            if (column < 0 || row < 0 || row >= MEGA_ROW_COUNT
                    || !Double.isFinite(Double.longBitsToDouble(expectedDyBits))) {
                throw new IllegalArgumentException("invalid mega case descriptor");
            }
        }
    }

    /** Finite donor-parity plan for the separate parameterized diagnostic. */
    public static NumericTowerPlan numericTowerPlan(
            BlockPos anchor,
            Direction facing,
            int count,
            int height) {
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        facing = horizontal(facing);
        if (count < MIN_NUMERIC_TOWER_COUNT || count > MAX_NUMERIC_TOWER_COUNT) {
            throw new IllegalArgumentException(
                    "numeric tower count must be " + MIN_NUMERIC_TOWER_COUNT
                            + ".." + MAX_NUMERIC_TOWER_COUNT);
        }
        if (height < MIN_NUMERIC_TOWER_HEIGHT || height > MAX_NUMERIC_TOWER_HEIGHT) {
            throw new IllegalArgumentException(
                    "numeric tower height must be " + MIN_NUMERIC_TOWER_HEIGHT
                            + ".." + MAX_NUMERIC_TOWER_HEIGHT);
        }

        Direction right = facing.getClockWise();
        List<NumericTowerColumn> towers = new ArrayList<>(count);
        List<String> caseIds = new ArrayList<>(count);
        Set<BlockPos> footprint = new LinkedHashSet<>();
        for (int index = 0; index < count; index++) {
            BlockPos base = anchor.relative(right, index * NUMERIC_TOWER_SPACING).immutable();
            String recipe = NUMERIC_TOWER_RECIPES[index % NUMERIC_TOWER_RECIPES.length];
            String label = NUMERIC_TOWER_LABELS[index % NUMERIC_TOWER_LABELS.length];
            String caseId = "tower.numeric." + (index + 1) + "." + label.toLowerCase();
            List<RigCase.FixtureCell> fixtures = standardLoweredSeed(base);
            NumericTowerColumn tower = new NumericTowerColumn(
                    index, caseId, recipe, label, base, base.above(3), fixtures);
            towers.add(tower);
            caseIds.add(caseId);
            fixtures.forEach(fixture -> footprint.add(fixture.pos()));
            for (int step = 0; step < height; step++) {
                BlockPos clicked = base.above(3 + step);
                BlockPos target = clicked.above();
                footprint.addAll(actionEnvelope(clicked, target));
            }
            BlockPos plannedTop = base.above(3 + height);
            for (int headroom = 1; headroom <= NUMERIC_TOWER_HEADROOM; headroom++) {
                footprint.add(plannedTop.above(headroom));
            }
        }
        return new NumericTowerPlan(
                anchor, facing, count, height, towers, caseIds,
                new ArrayList<>(footprint));
    }

    public record NumericTowerPlan(
            BlockPos anchor,
            Direction facing,
            int count,
            int height,
            List<NumericTowerColumn> towers,
            List<String> caseIds,
            List<BlockPos> footprint) implements ExecutionPlan {
        public NumericTowerPlan {
            anchor = Objects.requireNonNull(anchor, "anchor").immutable();
            facing = horizontal(facing);
            towers = List.copyOf(Objects.requireNonNull(towers, "towers"));
            caseIds = List.copyOf(Objects.requireNonNull(caseIds, "caseIds"));
            footprint = immutablePositions(footprint);
            if (count < MIN_NUMERIC_TOWER_COUNT || count > MAX_NUMERIC_TOWER_COUNT
                    || height < MIN_NUMERIC_TOWER_HEIGHT
                    || height > MAX_NUMERIC_TOWER_HEIGHT
                    || towers.size() != count
                    || caseIds.size() != count
                    || new HashSet<>(footprint).size() != footprint.size()) {
                throw new IllegalArgumentException("invalid numeric tower plan");
            }
        }

        @Override
        public String mode() {
            return "tower.numeric";
        }
    }

    public record NumericTowerColumn(
            int index,
            String caseId,
            String recipe,
            String label,
            BlockPos base,
            BlockPos seat,
            List<RigCase.FixtureCell> fixtures) {
        public NumericTowerColumn {
            if (index < 0) {
                throw new IllegalArgumentException("numeric tower index must be non-negative");
            }
            caseId = requireText(caseId, "caseId");
            recipe = requireText(recipe, "recipe");
            label = requireText(label, "label");
            base = Objects.requireNonNull(base, "base").immutable();
            seat = Objects.requireNonNull(seat, "seat").immutable();
            fixtures = List.copyOf(Objects.requireNonNull(fixtures, "fixtures"));
            if (!seat.equals(base.above(3)) || fixtures.size() != 4) {
                throw new IllegalArgumentException("numeric tower base must own four fixture cells");
            }
        }
    }

    /** Finite page of every non-empty S/B recipe up to {@code maxLength}. */
    public static StackPlan stacksPlan(
            BlockPos anchor,
            Direction facing,
            int maxLength,
            int page) {
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        facing = horizontal(facing);
        if (maxLength < MIN_STACK_MAX_LENGTH || maxLength > MAX_STACK_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "stack max length must be " + MIN_STACK_MAX_LENGTH
                            + ".." + MAX_STACK_MAX_LENGTH);
        }
        List<String> catalog = stackRecipes(maxLength);
        int totalPages = (catalog.size() + STACK_PAGE_SIZE - 1) / STACK_PAGE_SIZE;
        if (page < 1 || page > totalPages) {
            throw new IllegalArgumentException(
                    "stack page must be 1.." + totalPages
                            + " for max length " + maxLength);
        }

        int fromIndex = (page - 1) * STACK_PAGE_SIZE;
        int toIndex = Math.min(fromIndex + STACK_PAGE_SIZE, catalog.size());
        Direction right = facing.getClockWise();
        List<NumericTowerColumn> stacks = new ArrayList<>(toIndex - fromIndex);
        List<String> caseIds = new ArrayList<>(toIndex - fromIndex);
        Set<BlockPos> footprint = new LinkedHashSet<>();
        for (int catalogIndex = fromIndex; catalogIndex < toIndex; catalogIndex++) {
            int pageIndex = catalogIndex - fromIndex;
            int row = pageIndex / STACK_GRID_SIZE;
            int column = pageIndex % STACK_GRID_SIZE;
            BlockPos base = anchor
                    .relative(facing, row * STACK_SPACING)
                    .relative(right, column * STACK_SPACING)
                    .immutable();
            String recipe = catalog.get(catalogIndex);
            String caseId = "stacks." + (catalogIndex + 1) + "." + recipe.toLowerCase();
            List<RigCase.FixtureCell> fixtures = standardLoweredSeed(base);
            NumericTowerColumn stack = new NumericTowerColumn(
                    catalogIndex,
                    caseId,
                    recipe,
                    recipe,
                    base,
                    base.above(3),
                    fixtures);
            stacks.add(stack);
            caseIds.add(caseId);
            fixtures.forEach(fixture -> footprint.add(fixture.pos()));
            for (int step = 0; step < recipe.length(); step++) {
                BlockPos clicked = base.above(3 + step);
                footprint.addAll(actionEnvelope(clicked, clicked.above()));
            }
            BlockPos plannedTop = base.above(3 + recipe.length());
            for (int headroom = 1; headroom <= STACK_HEADROOM; headroom++) {
                footprint.add(plannedTop.above(headroom));
            }
        }
        return new StackPlan(
                anchor,
                facing,
                maxLength,
                page,
                totalPages,
                catalog.size(),
                stacks,
                caseIds,
                new ArrayList<>(footprint));
    }

    public record StackPlan(
            BlockPos anchor,
            Direction facing,
            int maxLength,
            int page,
            int totalPages,
            int totalRecipes,
            List<NumericTowerColumn> stacks,
            List<String> caseIds,
            List<BlockPos> footprint) implements ExecutionPlan {
        public StackPlan {
            anchor = Objects.requireNonNull(anchor, "anchor").immutable();
            facing = horizontal(facing);
            stacks = List.copyOf(Objects.requireNonNull(stacks, "stacks"));
            caseIds = List.copyOf(Objects.requireNonNull(caseIds, "caseIds"));
            footprint = immutablePositions(footprint);
            if (maxLength < MIN_STACK_MAX_LENGTH || maxLength > MAX_STACK_MAX_LENGTH
                    || page < 1 || totalPages < 1 || page > totalPages
                    || totalRecipes < 1
                    || stacks.isEmpty() || stacks.size() > STACK_PAGE_SIZE
                    || caseIds.size() != stacks.size()
                    || new HashSet<>(footprint).size() != footprint.size()) {
                throw new IllegalArgumentException("invalid stack plan");
            }
            for (NumericTowerColumn stack : stacks) {
                if (stack.recipe().length() > maxLength) {
                    throw new IllegalArgumentException(
                            "stack recipe exceeds selected max length");
                }
            }
        }

        @Override
        public String mode() {
            return "stacks";
        }
    }

    /**
     * Pure, world-free blueprint for one cases page. It deliberately does not implement
     * {@link ExecutionPlan}: cases execution is durable and page-oriented rather than launch-local
     * like rows, tower, and stacks.
     */
    public static CasesPagePlan casesPagePlan(
            SlabbedRigCaseCatalog.Snapshot snapshot,
            BlockPos anchor,
            Direction facing,
            int pageNumber) {
        Objects.requireNonNull(snapshot, "snapshot");
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        facing = horizontal(facing);
        SlabbedRigCaseCatalog.CasePage page =
                SlabbedRigCaseCatalog.page(snapshot, pageNumber);
        Direction right = facing.getClockWise();
        int itemStart = page.itemGroup() * SlabbedRigCaseCatalog.PAGE_GRID_SIDE;
        int topologyStart = page.topologyGroup() * SlabbedRigCaseCatalog.PAGE_GRID_SIDE;
        List<CasesTilePlan> tiles = new ArrayList<>(page.cases().size());
        LinkedHashSet<BlockPos> footprint = new LinkedHashSet<>();
        for (SlabbedRigCaseCatalog.CaseDefinition definition : page.cases()) {
            int row = definition.item().index() - itemStart;
            int column = definition.topology().index() - topologyStart;
            BlockPos base = anchor
                    .relative(facing, row * CASES_TILE_SPACING)
                    .relative(right, column * CASES_TILE_SPACING)
                    .immutable();
            List<RigCase.FixtureCell> fixtures = casesTopologyFixtures(
                    base, definition.topology());
            BlockPos cursor = fixtures.get(fixtures.size() - 1).pos();
            BlockPos target = cursor.above().immutable();
            List<BlockPos> effects = actionEnvelope(cursor, target);
            LinkedHashSet<BlockPos> mutations = new LinkedHashSet<>();
            fixtures.forEach(fixture -> mutations.add(fixture.pos()));
            mutations.addAll(effects);
            List<BlockPos> guards = List.of(target.above(2), target.above(3));
            LinkedHashSet<BlockPos> reserved = new LinkedHashSet<>(mutations);
            reserved.addAll(guards);
            CasesTilePlan tile = new CasesTilePlan(
                    definition,
                    row,
                    column,
                    base,
                    cursor,
                    target,
                    fixtures,
                    effects,
                    new ArrayList<>(mutations),
                    guards,
                    new ArrayList<>(reserved));
            for (BlockPos pos : tile.reservedCells()) {
                if (!footprint.add(pos)) {
                    throw new IllegalArgumentException(
                            "cases page tiles overlap at " + pos.toShortString());
                }
            }
            tiles.add(tile);
        }
        return new CasesPagePlan(
                snapshot.schema(),
                snapshot.catalogHash(),
                page,
                anchor,
                facing,
                CASES_LAYOUT_VERSION,
                CASES_TILE_SPACING,
                tiles,
                tiles.stream().filter(CasesTilePlan::autoEligible).toList(),
                tiles.stream().filter(tile -> !tile.autoEligible()).toList(),
                new ArrayList<>(footprint));
    }

    private static List<RigCase.FixtureCell> casesTopologyFixtures(
            BlockPos base,
            SlabbedRigCaseCatalog.Topology topology) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(topology, "topology");
        if (topology.recipe().equals("GROUND")) {
            return List.of(new RigCase.FixtureCell(
                    base, Blocks.STONE.defaultBlockState()));
        }
        if (topology.recipe().equals("SINGLE_SLAB")) {
            return List.of(new RigCase.FixtureCell(
                    base,
                    Blocks.STONE_SLAB.defaultBlockState()
                            .setValue(SlabBlock.TYPE, SlabType.BOTTOM)));
        }
        if (topology.control() || !topology.recipe().matches("[SB]{1,5}")) {
            throw new IllegalArgumentException(
                    "unknown cases topology " + topology.id());
        }
        List<RigCase.FixtureCell> fixtures = new ArrayList<>(topology.recipe().length());
        for (int index = 0; index < topology.recipe().length(); index++) {
            char token = topology.recipe().charAt(index);
            BlockState state = token == 'S'
                    ? Blocks.STONE_SLAB.defaultBlockState()
                            .setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                    : Blocks.STONE.defaultBlockState();
            fixtures.add(new RigCase.FixtureCell(base.above(index), state));
        }
        return List.copyOf(fixtures);
    }

    public record CasesPagePlan(
            String catalogSchema,
            String catalogHash,
            SlabbedRigCaseCatalog.CasePage page,
            BlockPos anchor,
            Direction facing,
            String layoutVersion,
            int tileSpacing,
            List<CasesTilePlan> tiles,
            List<CasesTilePlan> autoTiles,
            List<CasesTilePlan> deferredTiles,
            List<BlockPos> reservedFootprint) {
        public CasesPagePlan {
            catalogSchema = requireText(catalogSchema, "catalogSchema");
            catalogHash = requireText(catalogHash, "catalogHash");
            page = Objects.requireNonNull(page, "page");
            anchor = Objects.requireNonNull(anchor, "anchor").immutable();
            facing = horizontal(facing);
            layoutVersion = requireText(layoutVersion, "layoutVersion");
            tiles = List.copyOf(Objects.requireNonNull(tiles, "tiles"));
            autoTiles = List.copyOf(Objects.requireNonNull(autoTiles, "autoTiles"));
            deferredTiles = List.copyOf(Objects.requireNonNull(deferredTiles, "deferredTiles"));
            reservedFootprint = immutablePositions(reservedFootprint);
            if (!catalogSchema.equals(SlabbedRigCaseCatalog.SCHEMA)
                    || !catalogHash.matches("[0-9a-f]{64}")
                    || !layoutVersion.equals(CASES_LAYOUT_VERSION)
                    || tileSpacing != CASES_TILE_SPACING
                    || tiles.isEmpty()
                    || tiles.size() != page.cases().size()
                    || new HashSet<>(reservedFootprint).size() != reservedFootprint.size()) {
                throw new IllegalArgumentException("invalid cases page plan");
            }
            if (!tiles.stream().map(CasesTilePlan::definition).toList()
                    .equals(page.cases())) {
                throw new IllegalArgumentException(
                        "cases page plan must preserve catalog case order");
            }
            List<CasesTilePlan> expectedAuto =
                    tiles.stream().filter(CasesTilePlan::autoEligible).toList();
            List<CasesTilePlan> expectedDeferred =
                    tiles.stream().filter(tile -> !tile.autoEligible()).toList();
            if (!autoTiles.equals(expectedAuto) || !deferredTiles.equals(expectedDeferred)) {
                throw new IllegalArgumentException(
                        "cases AUTO/deferred partitions must be exact");
            }
            LinkedHashSet<BlockPos> expectedFootprint = new LinkedHashSet<>();
            for (CasesTilePlan tile : tiles) {
                for (BlockPos pos : tile.reservedCells()) {
                    if (!expectedFootprint.add(pos)) {
                        throw new IllegalArgumentException(
                                "cases page contains overlapping tiles at " + pos.toShortString());
                    }
                }
            }
            if (!reservedFootprint.equals(List.copyOf(expectedFootprint))) {
                throw new IllegalArgumentException(
                        "cases page footprint must be the ordered tile union");
            }
        }
    }

    public record CasesTilePlan(
            SlabbedRigCaseCatalog.CaseDefinition definition,
            int row,
            int column,
            BlockPos base,
            BlockPos cursor,
            BlockPos target,
            List<RigCase.FixtureCell> fixtures,
            List<BlockPos> effectCells,
            List<BlockPos> mutationCells,
            List<BlockPos> guardCells,
            List<BlockPos> reservedCells) {
        public CasesTilePlan {
            definition = Objects.requireNonNull(definition, "definition");
            base = Objects.requireNonNull(base, "base").immutable();
            cursor = Objects.requireNonNull(cursor, "cursor").immutable();
            target = Objects.requireNonNull(target, "target").immutable();
            fixtures = List.copyOf(Objects.requireNonNull(fixtures, "fixtures"));
            effectCells = immutablePositions(effectCells);
            mutationCells = immutablePositions(mutationCells);
            guardCells = immutablePositions(guardCells);
            reservedCells = immutablePositions(reservedCells);
            if (row < 0 || row >= SlabbedRigCaseCatalog.PAGE_GRID_SIDE
                    || column < 0 || column >= SlabbedRigCaseCatalog.PAGE_GRID_SIDE
                    || fixtures.isEmpty()
                    || !fixtures.get(0).pos().equals(base)
                    || !fixtures.get(fixtures.size() - 1).pos().equals(cursor)
                    || !target.equals(cursor.above())
                    || !effectCells.equals(actionEnvelope(cursor, target))
                    || !guardCells.equals(List.of(target.above(2), target.above(3)))
                    || mutationCells.stream().anyMatch(guardCells::contains)
                    || new HashSet<>(reservedCells).size() != reservedCells.size()) {
                throw new IllegalArgumentException("invalid cases tile plan");
            }
            LinkedHashSet<BlockPos> expectedMutation = new LinkedHashSet<>();
            fixtures.forEach(fixture -> expectedMutation.add(fixture.pos()));
            expectedMutation.addAll(effectCells);
            if (!mutationCells.equals(List.copyOf(expectedMutation))) {
                throw new IllegalArgumentException(
                        "cases tile mutation cells must equal fixture/effect union");
            }
            LinkedHashSet<BlockPos> expectedReserved = new LinkedHashSet<>(expectedMutation);
            expectedReserved.addAll(guardCells);
            if (!reservedCells.equals(List.copyOf(expectedReserved))) {
                throw new IllegalArgumentException(
                        "cases tile reservation must equal mutation/guard union");
            }
        }

        public boolean autoEligible() {
            return definition.item().disposition()
                            == SlabbedRigCaseCatalog.Disposition.AUTO_FLOOR_UP
                    && definition.item().effectPolicy()
                            == SlabbedRigCaseCatalog.EffectPolicy.LOCAL_TARGET_AND_NEIGHBORS;
        }
    }

    private static List<RigCase.FixtureCell> standardLoweredSeed(BlockPos base) {
        return List.of(
                new RigCase.FixtureCell(base, Blocks.STONE.defaultBlockState()),
                new RigCase.FixtureCell(
                        base.above(),
                        Blocks.STONE_SLAB.defaultBlockState()
                                .setValue(SlabBlock.TYPE, SlabType.BOTTOM)),
                new RigCase.FixtureCell(base.above(2), Blocks.STONE.defaultBlockState()),
                new RigCase.FixtureCell(
                        base.above(3),
                        Blocks.STONE_SLAB.defaultBlockState()
                                .setValue(SlabBlock.TYPE, SlabType.BOTTOM)));
    }

    private static List<String> stackRecipes(int maxLength) {
        List<String> recipes = new ArrayList<>((1 << (maxLength + 1)) - 2);
        for (int length = 1; length <= maxLength; length++) {
            appendStackRecipes(recipes, new StringBuilder(length), length);
        }
        return List.copyOf(recipes);
    }

    private static void appendStackRecipes(
            List<String> recipes,
            StringBuilder prefix,
            int remaining) {
        if (remaining == 0) {
            recipes.add(prefix.toString());
            return;
        }
        prefix.append('S');
        appendStackRecipes(recipes, prefix, remaining - 1);
        prefix.setLength(prefix.length() - 1);
        prefix.append('B');
        appendStackRecipes(recipes, prefix, remaining - 1);
        prefix.setLength(prefix.length() - 1);
    }

    public static synchronized RigStatus status(ServerLevel world) {
        Objects.requireNonNull(world, "world");
        ActiveRig active = ACTIVE.get(world);
        if (active == null) {
            return new RigStatus(false, null, 0, 0, List.of(), false);
        }
        RigManifest manifest = active.manifest();
        List<BlockPos> conflicts = new ArrayList<>();
        int intact = 0;
        for (RigManifest.OwnedCell cell : manifest.ownedCells()) {
            if (matches(world, active, cell)) {
                intact++;
            } else {
                conflicts.add(cell.pos());
            }
        }
        if (active.plan() != null) {
            Set<BlockPos> owned = manifest.ownedCells().stream()
                    .map(RigManifest.OwnedCell::pos)
                    .collect(java.util.stream.Collectors.toSet());
            for (BlockPos reserved : active.plan().footprint()) {
                if (!owned.contains(reserved)
                        && (!world.hasChunkAt(reserved)
                                || !world.getBlockState(reserved).isAir()
                                || SlabAnchorAttachment.storedPlacementDyFact(
                                        world, reserved).present())) {
                    conflicts.add(reserved);
                }
            }
        }
        return new RigStatus(
                true,
                manifest,
                intact,
                manifest.ownedCells().size(),
                distinctPositions(conflicts),
                conflicts.isEmpty());
    }

    public static synchronized ClearResult clear(ServerLevel world, boolean force) {
        Objects.requireNonNull(world, "world");
        ActiveRig active = ACTIVE.get(world);
        if (active == null) {
            return new ClearResult(ClearOutcome.NO_ACTIVE, null, 0, List.of(), "no active rig");
        }
        RigManifest manifest = active.manifest();
        RigStatus status = status(world);
        if (!force && !status.conflicts().isEmpty()) {
            return new ClearResult(
                    ClearOutcome.CONFLICT,
                    manifest,
                    0,
                    status.conflicts(),
                    "owned cells changed; normal clear made no changes");
        }

        int removed = 0;
        List<RigManifest.OwnedCell> residual = new ArrayList<>();
        List<RigManifest.OwnedCell> cells = manifest.ownedCells();
        for (int index = cells.size() - 1; index >= 0; index--) {
            RigManifest.OwnedCell cell = cells.get(index);
            if (removeOwnedCell(world, cell.pos())) {
                removed++;
            } else {
                residual.add(0, cell);
            }
        }
        if (residual.isEmpty()) {
            ACTIVE.remove(world);
            return new ClearResult(
                    ClearOutcome.CLEARED, null, removed, List.of(), "owned rig cleared");
        }
        RigManifest residueManifest = manifest.withResidualOwnedCells(residual);
        ACTIVE.put(world, ActiveRig.residue(residueManifest));
        return new ClearResult(
                ClearOutcome.RESIDUE,
                residueManifest,
                removed,
                residual.stream().map(RigManifest.OwnedCell::pos).toList(),
                "some owned cells were unloaded or could not be removed");
    }

    private static PlanAttempt executePlan(
            ServerLevel world,
            ServerPlayer player,
            ExecutionPlan plan) {
        if (plan instanceof CaseExecutionPlan casePlan) {
            return executeCasePlan(world, player, casePlan.cases());
        }
        if (plan instanceof MegaPlan megaPlan) {
            return executeMegaPlan(world, player, megaPlan);
        }
        if (plan instanceof NumericTowerPlan || plan instanceof StackPlan) {
            return executeVerticalRecipePlan(world, player, plan);
        }
        throw new IllegalArgumentException("unknown rig execution plan " + plan.getClass());
    }

    private static PlanAttempt executeCasePlan(
            ServerLevel world,
            ServerPlayer player,
            List<RigCase> cases) {
        List<RigManifest.OwnedCell> created = new ArrayList<>();
        List<AttemptedCell> attempted = new ArrayList<>();
        List<LandingResolution> resolutions = new ArrayList<>();
        int fixtureWrites = 0;
        int fixtureTruthWrites = 0;
        int subjectUseOnCalls = 0;
        try {
            for (RigCase rigCase : cases) {
                for (RigCase.FixtureCell fixture : rigCase.fixtures()) {
                    requireEmptyMutationCell(world, fixture.pos());
                    attempted.add(new AttemptedCell(
                            fixture.pos(), fixture.state(),
                            RigManifest.CellRole.FIXTURE, rigCase.id()));
                    created.add(placeFixture(world, rigCase.id(), fixture));
                    fixtureWrites++;
                }
            }
            // State first, declared fixture truth second. Compound-visible relationships may name
            // another fixture cell, so every source state must exist before any marker is authored.
            for (RigCase rigCase : cases) {
                for (RigCase.FixtureCell fixture : rigCase.fixtures()) {
                    if (fixture.authorship().kind()
                            != RigCase.FixtureAuthorship.Kind.NONE) {
                        authorFixtureTruth(world, fixture);
                        fixtureTruthWrites++;
                    }
                }
            }
            for (RigCase rigCase : cases) {
                for (RigCase.SubjectPlacement subject : rigCase.subjects()) {
                    requireEmptyMutationCell(world, subject.aim().vanillaTarget());
                    attempted.add(new AttemptedCell(
                            subject.aim().vanillaTarget(),
                            subject.expectedBlock().defaultBlockState(),
                            RigManifest.CellRole.SUBJECT,
                            rigCase.id()));
                    subjectUseOnCalls++;
                    SubjectResult placed = placeSubjectViaUseOn(world, player, subject);
                    resolutions.add(placed.resolution());
                    created.add(new RigManifest.OwnedCell(
                            placed.resolution().targetPos(),
                            placed.state(),
                            placed.storedDy(),
                            RigManifest.CellRole.SUBJECT,
                            rigCase.id()));
                }
            }
            return PlanAttempt.success(
                    created, attempted, fixtureWrites, fixtureTruthWrites,
                    subjectUseOnCalls, resolutions, RigManifest.StructuralReport.none());
        } catch (RuntimeException failure) {
            // Record the intended cell before crossing either mutation door. A placement may
            // change the world successfully and then fail its postcondition; rolling back only
            // fully validated cells would strand that newest mutation outside the manifest.
            List<RigManifest.OwnedCell> residual = rollbackAttempted(world, attempted);
            return PlanAttempt.failure(
                    created, attempted, fixtureWrites, fixtureTruthWrites, subjectUseOnCalls,
                    resolutions, RigManifest.StructuralReport.none(), failure, residual);
        }
    }

    private static PlanAttempt executeMegaPlan(
            ServerLevel world,
            ServerPlayer player,
            MegaPlan plan) {
        List<RigManifest.OwnedCell> created = new ArrayList<>();
        List<AttemptedCell> attempted = new ArrayList<>();
        Set<BlockPos> attemptedPositions = new HashSet<>();
        List<LandingResolution> resolutions = new ArrayList<>();
        List<RigManifest.MegaCaseReadback> readbacks = new ArrayList<>();
        int fixtureWrites = 0;
        int fixtureTruthWrites = 0;
        int subjectUseOnCalls = 0;
        try {
            for (MegaAction action : plan.actions()) {
                for (RigCase.FixtureCell fixture : action.fixtures()) {
                    requireEmptyMutationCell(world, fixture.pos());
                    registerAttempted(
                            attempted, attemptedPositions, fixture.pos(), fixture.state(),
                            RigManifest.CellRole.FIXTURE, action.caseId());
                    created.add(placeFixture(world, action.caseId(), fixture));
                    fixtureWrites++;
                }
                writeMegaLabel(world, action);
            }
            for (MegaAction action : plan.actions()) {
                for (RigCase.FixtureCell fixture : action.fixtures()) {
                    if (fixture.authorship().kind()
                            != RigCase.FixtureAuthorship.Kind.NONE) {
                        authorFixtureTruth(world, fixture);
                        fixtureTruthWrites++;
                    }
                }
            }

            for (MegaAction action : plan.actions()) {
                List<BlockPos> envelope = actionEnvelope(action.clicked(), action.target());
                List<CellSnapshot> before = envelope.stream()
                        .map(pos -> snapshot(world, pos))
                        .toList();
                for (int index = 0; index < envelope.size(); index++) {
                    registerAttempted(
                            attempted,
                            attemptedPositions,
                            envelope.get(index),
                            before.get(index).state(),
                            RigManifest.CellRole.SUBJECT,
                            action.caseId());
                }

                ItemStack original = player.getMainHandItem().copy();
                InteractionResult[] result = {InteractionResult.PASS};
                RuntimeException[] thrown = {null};
                ItemStack proxy = new ItemStack(action.item());
                Vec3 hitVector = Vec3.atCenterOf(action.clicked()).add(
                        action.face().getStepX() * 0.5d,
                        action.face().getStepY() * 0.5d,
                        action.face().getStepZ() * 0.5d);
                BlockHitResult hit = new BlockHitResult(
                        hitVector, action.face(), action.clicked(), false);
                BlockPlaceContext placementContext = new BlockPlaceContext(
                        world, player, InteractionHand.MAIN_HAND, proxy, hit);
                BlockPos resolvedTarget = placementContext.getClickedPos().immutable();
                if (!envelope.contains(resolvedTarget)) {
                    throw new IllegalStateException(
                            "mega placement resolved outside its declared evidence envelope: "
                                    + action.caseId() + " -> " + resolvedTarget.toShortString());
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, proxy);
                subjectUseOnCalls++;
                try {
                    SlabbedDiagnosticsBridge.withActionOrigin(
                            SlabbedDiagnosticsBridge.AUTO_USEON_PROXY,
                            new SlabbedDiagnosticsBridge.ActionOriginContext(
                                    player.getUUID().toString(),
                                    world.dimension().location().toString(),
                                    resolvedTarget,
                                    action.caseId(),
                                    action.label(),
                                    Double.doubleToRawLongBits(action.expectedDy()),
                                    action.face(),
                                    action.orientation()),
                            () -> result[0] = ForgeHooks.onPlaceItemIntoWorld(
                                    new UseOnContext(player, InteractionHand.MAIN_HAND, hit)));
                } catch (RuntimeException failure) {
                    thrown[0] = failure;
                } finally {
                    player.setItemInHand(InteractionHand.MAIN_HAND, original);
                }

                List<BlockPos> changed = new ArrayList<>();
                for (int index = 0; index < envelope.size(); index++) {
                    BlockPos pos = envelope.get(index);
                    CellSnapshot after = snapshot(world, pos);
                    if (!before.get(index).equals(after)) {
                        changed.add(pos);
                        replaceOrAddOwnedCell(created, pos, after, action.caseId());
                    }
                }

                CellSnapshot targetBefore = snapshotAt(envelope, before, resolvedTarget);
                CellSnapshot targetAfter = snapshot(world, resolvedTarget);
                RigManifest.MegaCaseGrade grade;
                String reason;
                long observedDyBits = Double.doubleToRawLongBits(Double.NaN);
                if (!targetAfter.state().isAir()) {
                    observedDyBits = Double.doubleToRawLongBits(SlabSupport.getYOffset(
                            world, resolvedTarget, targetAfter.state()));
                }
                if (changed.isEmpty()) {
                    grade = RigManifest.MegaCaseGrade.REFUSED;
                    reason = "no_world_change_" + result[0].name().toLowerCase(Locale.ROOT);
                    if (thrown[0] != null) {
                        reason += "_" + thrown[0].getClass().getSimpleName();
                    }
                    resolutions.add(new LandingResolution.Reject(reason));
                } else if (targetBefore.equals(targetAfter) || targetAfter.state().isAir()) {
                    grade = RigManifest.MegaCaseGrade.INCONCLUSIVE;
                    reason = "changed_outside_resolved_placement_cell";
                    if (thrown[0] != null) {
                        reason += "_" + thrown[0].getClass().getSimpleName();
                    }
                    resolutions.add(new LandingResolution.PreserveVanilla(reason));
                } else {
                    long expectedBits = Double.doubleToRawLongBits(action.expectedDy());
                    boolean liveExact = observedDyBits == expectedBits;
                    boolean storedExact = targetAfter.storedDy().present()
                            && targetAfter.storedDy().rawBits() == expectedBits;
                    grade = liveExact && storedExact
                            ? RigManifest.MegaCaseGrade.EXACT
                            : RigManifest.MegaCaseGrade.MISMATCH;
                    reason = grade == RigManifest.MegaCaseGrade.EXACT
                            ? "live_and_stored_dy_exact"
                            : "expected=" + Double.toString(action.expectedDy())
                                    + ",live=" + Double.toString(
                                            Double.longBitsToDouble(observedDyBits))
                                    + ",stored=" + (targetAfter.storedDy().present()
                                            ? Double.toString(targetAfter.storedDy().valueOrNaN())
                                            : "absent");
                    if (targetAfter.storedDy().present()) {
                        resolutions.add(new LandingResolution.Place(
                                resolvedTarget,
                                targetAfter.storedDy().rawBits(),
                                LandingResolution.Lane.fromDy(
                                        targetAfter.storedDy().valueOrNaN()),
                                "forge_itemstack_use_on_mega"));
                    } else {
                        resolutions.add(new LandingResolution.PreserveVanilla(
                                "mega_target_changed_without_stored_dy"));
                    }
                }

                RigManifest.MegaCaseReadback readback = new RigManifest.MegaCaseReadback(
                        action.caseId(),
                        action.label(),
                        action.itemId(),
                        action.column(),
                        action.row(),
                        resolvedTarget,
                        Double.doubleToRawLongBits(action.expectedDy()),
                        action.face(),
                        action.orientation(),
                        grade,
                        targetAfter.state().toString(),
                        observedDyBits,
                        targetAfter.storedDy(),
                        reason);
                readbacks.add(readback);
                recordMegaCase(readback, changed, result[0], thrown[0]);
            }

            int exact = (int) readbacks.stream()
                    .filter(one -> one.grade() == RigManifest.MegaCaseGrade.EXACT).count();
            int refused = (int) readbacks.stream()
                    .filter(one -> one.grade() == RigManifest.MegaCaseGrade.REFUSED).count();
            int mismatched = (int) readbacks.stream()
                    .filter(one -> one.grade() == RigManifest.MegaCaseGrade.MISMATCH).count();
            int inconclusive = (int) readbacks.stream()
                    .filter(one -> one.grade() == RigManifest.MegaCaseGrade.INCONCLUSIVE).count();
            boolean complete = exact == plan.actions().size();
            RigManifest.MegaReport report = new RigManifest.MegaReport(
                    plan.columns(),
                    MEGA_DEPTHS.size(),
                    MEGA_FACES.size(),
                    plan.actions().size(),
                    exact,
                    refused,
                    mismatched,
                    inconclusive,
                    readbacks,
                    complete,
                    "exact=" + exact + ",refused=" + refused
                            + ",mismatched=" + mismatched
                            + ",inconclusive=" + inconclusive);
            // Connecting and merge-sensitive families can legitimately settle an earlier owned
            // cell after a later column/row action (for example wire shape, rail slope, or a slab
            // merge). Publish ownership only after the complete sweep has settled. Never claim an
            // empty cell: that would let a later force-clear erase a foreign replacement.
            List<RigManifest.OwnedCell> settled = settleMegaOwnership(world, created);
            return PlanAttempt.success(
                    settled,
                    attempted,
                    fixtureWrites,
                    fixtureTruthWrites,
                    subjectUseOnCalls,
                    resolutions,
                    report);
        } catch (RuntimeException failure) {
            List<RigManifest.OwnedCell> residual = rollbackAttempted(world, attempted);
            return PlanAttempt.failure(
                    created,
                    attempted,
                    fixtureWrites,
                    fixtureTruthWrites,
                    subjectUseOnCalls,
                    resolutions,
                    RigManifest.StructuralReport.none(),
                    failure,
                    residual);
        }
    }

    private static CellSnapshot snapshotAt(
            List<BlockPos> positions,
            List<CellSnapshot> snapshots,
            BlockPos pos) {
        int index = positions.indexOf(pos);
        if (index < 0) {
            throw new IllegalArgumentException("position is outside the evidence envelope: " + pos);
        }
        return snapshots.get(index);
    }

    private static void recordMegaCase(
            RigManifest.MegaCaseReadback readback,
            List<BlockPos> changed,
            InteractionResult result,
            RuntimeException failure) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("rigCaseId", readback.caseId());
        fields.put("rigLabel", readback.label());
        fields.put("itemId", readback.itemId());
        fields.put("column", Integer.toString(readback.column()));
        fields.put("row", Integer.toString(readback.row()));
        fields.put("pos", readback.placementPos().toShortString());
        fields.put("placementPos", readback.placementPos().toShortString());
        fields.put("heldItem", readback.itemId());
        fields.put("expectedDy", Double.toString(readback.expectedDy()));
        fields.put("expectedDyBits", Long.toUnsignedString(readback.expectedDyBits()));
        fields.put("face", readback.face().getName());
        fields.put("orientation", readback.orientation());
        fields.put("grade", readback.grade().name());
        fields.put("observedState", readback.observedState());
        fields.put("observedDy", Double.toString(readback.observedDy()));
        fields.put("observedDyBits", Long.toUnsignedString(readback.observedDyBits()));
        fields.put("observedStoredDy", readback.observedStoredDy().present()
                ? Double.toString(readback.observedStoredDy().valueOrNaN()) : "absent");
        fields.put("observedStoredDyBits", readback.observedStoredDy().present()
                ? Long.toUnsignedString(readback.observedStoredDy().rawBits()) : "absent");
        fields.put("interactionResult", result.name());
        fields.put("changedCells", changed.stream().map(BlockPos::toShortString)
                .collect(java.util.stream.Collectors.joining("|")));
        fields.put("reason", readback.reason());
        fields.put("failureClasses", failure == null
                ? (readback.grade() == RigManifest.MegaCaseGrade.EXACT
                        ? "none" : "MEGA_" + readback.grade().name())
                : failure.getClass().getSimpleName());
        SlabbedDiagnosticsBridge.recordRigCase(fields);
    }

    private static List<RigManifest.OwnedCell> settleMegaOwnership(
            ServerLevel world,
            List<RigManifest.OwnedCell> created) {
        List<RigManifest.OwnedCell> settled = new ArrayList<>(created.size());
        for (RigManifest.OwnedCell cell : created) {
            BlockState live = world.getBlockState(cell.pos());
            SlabAnchorAttachment.PlacementDyFact stored =
                    SlabAnchorAttachment.storedPlacementDyFact(world, cell.pos());
            if (live.isAir()) {
                throw new IllegalStateException(
                        "mega owned cell vanished before final publication at "
                                + cell.pos().toShortString());
            }
            settled.add(new RigManifest.OwnedCell(
                    cell.pos(), live, stored, cell.role(), cell.caseId()));
        }
        return List.copyOf(settled);
    }

    private static void replaceOrAddOwnedCell(
            List<RigManifest.OwnedCell> created,
            BlockPos pos,
            CellSnapshot after,
            String caseId) {
        for (int index = created.size() - 1; index >= 0; index--) {
            RigManifest.OwnedCell existing = created.get(index);
            if (existing.pos().equals(pos)) {
                // A real useOn may merge into a declared fixture cell (notably a slab seat).
                // Once the subject action changes that cell, its final state belongs to the
                // subject case; retaining FIXTURE would incorrectly reapply the pre-use fixture
                // truth contract during status/clear.
                created.set(index, new RigManifest.OwnedCell(
                        pos,
                        after.state(),
                        after.storedDy(),
                        RigManifest.CellRole.SUBJECT,
                        caseId));
                return;
            }
        }
        created.add(new RigManifest.OwnedCell(
                pos,
                after.state(),
                after.storedDy(),
                RigManifest.CellRole.SUBJECT,
                caseId));
    }

    private static PlanAttempt executeVerticalRecipePlan(
            ServerLevel world,
            ServerPlayer player,
            ExecutionPlan plan) {
        NumericTowerPlan numericPlan = plan instanceof NumericTowerPlan numeric ? numeric : null;
        StackPlan stackPlan = plan instanceof StackPlan stacks ? stacks : null;
        if (numericPlan == null && stackPlan == null) {
            throw new IllegalArgumentException("vertical recipe plan is required");
        }
        List<NumericTowerColumn> columns = numericPlan != null
                ? numericPlan.towers() : stackPlan.stacks();
        String evidencePrefix = numericPlan != null ? "numeric_tower" : "stacks";
        List<RigManifest.OwnedCell> created = new ArrayList<>();
        List<AttemptedCell> attempted = new ArrayList<>();
        Set<BlockPos> attemptedPositions = new HashSet<>();
        List<LandingResolution> resolutions = new ArrayList<>();
        List<RigManifest.TowerColumnReport> towerReports = new ArrayList<>();
        List<RigManifest.SeamFinding> seams = new ArrayList<>();
        int fixtureWrites = 0;
        int subjectUseOnCalls = 0;
        try {
            for (NumericTowerColumn tower : columns) {
                for (RigCase.FixtureCell fixture : tower.fixtures()) {
                    requireEmptyMutationCell(world, fixture.pos());
                    registerAttempted(
                            attempted,
                            attemptedPositions,
                            fixture.pos(),
                            fixture.state(),
                            RigManifest.CellRole.FIXTURE,
                            tower.caseId());
                    created.add(placeFixture(world, tower.caseId(), fixture));
                    fixtureWrites++;
                }
            }

            for (NumericTowerColumn tower : columns) {
                BlockPos cursor = tower.seat();
                int slabOrdinal = 0;
                int attempts = 0;
                int built = 0;
                boolean stalled = false;
                List<RigManifest.TowerCellReadback> cells = new ArrayList<>();
                cells.add(readback(world, cursor));

                int plannedSteps = numericPlan != null
                        ? numericPlan.height() : tower.recipe().length();
                for (int step = 0; step < plannedSteps; step++) {
                    char token = tower.recipe().charAt(step % tower.recipe().length());
                    Block expectedBlock;
                    if (token == 'S') {
                        expectedBlock = (slabOrdinal++ & 1) == 0
                                ? Blocks.SMOOTH_STONE_SLAB
                                : Blocks.STONE_SLAB;
                    } else if (token == 'B') {
                        expectedBlock = Blocks.STONE;
                    } else {
                        throw new IllegalStateException(
                                "unknown numeric tower recipe token " + token);
                    }

                    BlockPos intendedTarget = cursor.above().immutable();
                    for (BlockPos cell : actionEnvelope(cursor, intendedTarget)) {
                        registerAttempted(
                                attempted,
                                attemptedPositions,
                                cell,
                                expectedBlock.defaultBlockState(),
                                RigManifest.CellRole.SUBJECT,
                                tower.caseId());
                    }
                    subjectUseOnCalls++;
                    attempts++;
                    NumericActionResult action = placeNumericSubjectViaUseOn(
                            world, player, cursor, intendedTarget, expectedBlock, evidencePrefix);
                    resolutions.add(action.resolution());

                    if (action.kind() == NumericActionKind.ADVANCED) {
                        created.add(new RigManifest.OwnedCell(
                                intendedTarget,
                                action.state(),
                                action.storedDy(),
                                RigManifest.CellRole.SUBJECT,
                                tower.caseId()));
                        cursor = intendedTarget;
                        built++;
                        cells.add(readback(world, cursor));
                    } else if (action.kind() == NumericActionKind.IN_CELL_CHANGE) {
                        replaceOwnedCell(
                                created,
                                cursor,
                                action.state(),
                                action.storedDy());
                        cells.set(cells.size() - 1, readback(world, cursor));
                    } else {
                        stalled = true;
                        break;
                    }
                }

                RigManifest.TowerColumnReport report = new RigManifest.TowerColumnReport(
                        tower.index(),
                        tower.label(),
                        tower.seat(),
                        attempts,
                        built,
                        stalled,
                        cells);
                towerReports.add(report);
                appendSeams(cells, seams);
            }

            long gaps = seams.stream()
                    .filter(seam -> seam.kind() == RigManifest.SeamKind.GAP)
                    .count();
            long overlaps = seams.size() - gaps;
            RigManifest.StructuralReport structuralReport;
            if (numericPlan != null) {
                boolean complete = towerReports.stream()
                        .allMatch(tower -> tower.builtCells() == numericPlan.height())
                        && seams.isEmpty();
                String counts = towerReports.stream()
                        .map(tower -> Integer.toString(tower.builtCells()))
                        .collect(java.util.stream.Collectors.joining("/"));
                long shortTowers = towerReports.stream()
                        .filter(tower -> tower.builtCells() != numericPlan.height())
                        .count();
                structuralReport = new RigManifest.NumericTowerReport(
                        numericPlan.height(),
                        towerReports,
                        seams,
                        complete,
                        "built=" + counts + "/" + numericPlan.height()
                                + ",short=" + shortTowers
                                + ",gaps=" + gaps
                                + ",overlaps=" + overlaps);
            } else {
                List<RigManifest.StackEntryReport> stackReports = new ArrayList<>(columns.size());
                for (int index = 0; index < columns.size(); index++) {
                    NumericTowerColumn stack = columns.get(index);
                    stackReports.add(new RigManifest.StackEntryReport(
                            stack.index(), stack.recipe(), towerReports.get(index)));
                }
                boolean complete = stackReports.stream().allMatch(stack ->
                                stack.column().builtCells() == stack.recipe().length())
                        && seams.isEmpty();
                String counts = stackReports.stream()
                        .map(stack -> stack.column().builtCells() + "/" + stack.recipe().length())
                        .collect(java.util.stream.Collectors.joining(","));
                long shortStacks = stackReports.stream()
                        .filter(stack -> stack.column().builtCells() != stack.recipe().length())
                        .count();
                structuralReport = new RigManifest.StackPageReport(
                        stackPlan.maxLength(),
                        stackPlan.page(),
                        stackPlan.totalPages(),
                        stackPlan.totalRecipes(),
                        stackReports,
                        seams,
                        complete,
                        "built=" + counts
                                + ",short=" + shortStacks
                                + ",gaps=" + gaps
                                + ",overlaps=" + overlaps);
            }
            return PlanAttempt.success(
                    created,
                    attempted,
                    fixtureWrites,
                    0,
                    subjectUseOnCalls,
                    resolutions,
                    structuralReport);
        } catch (RuntimeException failure) {
            List<RigManifest.OwnedCell> residual = rollbackAttempted(world, attempted);
            return PlanAttempt.failure(
                    created,
                    attempted,
                    fixtureWrites,
                    0,
                    subjectUseOnCalls,
                    resolutions,
                    RigManifest.StructuralReport.none(),
                    failure,
                    residual);
        }
    }

    private static void registerAttempted(
            List<AttemptedCell> attempted,
            Set<BlockPos> attemptedPositions,
            BlockPos pos,
            BlockState fallbackState,
            RigManifest.CellRole role,
            String caseId) {
        pos = pos.immutable();
        if (attemptedPositions.add(pos)) {
            attempted.add(new AttemptedCell(pos, fallbackState, role, caseId));
        }
    }

    private static void replaceOwnedCell(
            List<RigManifest.OwnedCell> created,
            BlockPos pos,
            BlockState state,
            SlabAnchorAttachment.PlacementDyFact storedDy) {
        for (int index = created.size() - 1; index >= 0; index--) {
            RigManifest.OwnedCell existing = created.get(index);
            if (existing.pos().equals(pos)) {
                created.set(index, new RigManifest.OwnedCell(
                        pos,
                        state,
                        storedDy,
                        existing.role(),
                        existing.caseId()));
                return;
            }
        }
        throw new IllegalStateException(
                "numeric tower changed an unowned cursor at " + pos.toShortString());
    }

    private static RigManifest.TowerCellReadback readback(
            ServerLevel world,
            BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        double liveDy = SlabSupport.getYOffset(world, pos, state);
        if (state.isAir() || !Double.isFinite(liveDy)) {
            throw new IllegalStateException(
                    "numeric tower readback is invalid at " + pos.toShortString());
        }
        return new RigManifest.TowerCellReadback(
                pos, state, Double.doubleToRawLongBits(liveDy));
    }

    private static void appendSeams(
            List<RigManifest.TowerCellReadback> cells,
            List<RigManifest.SeamFinding> seams) {
        for (int index = 1; index < cells.size(); index++) {
            RigManifest.TowerCellReadback lower = cells.get(index - 1);
            RigManifest.TowerCellReadback upper = cells.get(index);
            double lowerTop = lower.pos().getY() + lower.liveDy()
                    + visibleHeight(lower.state());
            double upperBottom = upper.pos().getY() + upper.liveDy();
            double seam = upperBottom - lowerTop;
            if (seam > SEAM_EPSILON) {
                seams.add(new RigManifest.SeamFinding(
                        RigManifest.SeamKind.GAP,
                        lower.pos(),
                        upper.pos(),
                        Double.doubleToRawLongBits(lowerTop),
                        Double.doubleToRawLongBits(upperBottom),
                        Double.doubleToRawLongBits(seam)));
            } else if (seam < -SEAM_EPSILON) {
                seams.add(new RigManifest.SeamFinding(
                        RigManifest.SeamKind.OVERLAP,
                        lower.pos(),
                        upper.pos(),
                        Double.doubleToRawLongBits(lowerTop),
                        Double.doubleToRawLongBits(upperBottom),
                        Double.doubleToRawLongBits(seam)));
            }
        }
    }

    private static double visibleHeight(BlockState state) {
        if (state.getBlock() instanceof SlabBlock
                && state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE) {
            return 0.5d;
        }
        return 1.0d;
    }

    private static RigManifest publishResidue(
            UUID runId,
            ServerLevel world,
            ServerPlayer player,
            BlockPos anchor,
            ExecutionPlan plan,
            PlanAttempt attempt) {
        if (attempt.residual().isEmpty()) {
            return null;
        }
        RigManifest residue = manifest(
                runId, world, player, anchor, plan, attempt.residual(),
                attempt.fixtureWrites(), attempt.fixtureTruthWrites(),
                attempt.subjectUseOnCalls(), attempt.resolutions(),
                new RigManifest.ResidueStructuralReport());
        ACTIVE.put(world, ActiveRig.residue(residue));
        return residue;
    }

    /** Replays the exact in-memory prior plan through the same real-use path after a clean failure. */
    private static RestoreResult restorePrevious(
            ServerLevel world,
            ServerPlayer player,
            ActiveRig previous) {
        PlanAttempt restored = executePlan(world, player, previous.plan());
        if (!restored.success()) {
            RigManifest residue = publishResidue(
                    previous.manifest().runId(),
                    world,
                    player,
                    previous.manifest().anchor(),
                    previous.plan(),
                    restored);
            return RestoreResult.failure(
                    residue,
                    residue == null ? List.of() : residue.ownedCells().stream()
                            .map(RigManifest.OwnedCell::pos)
                            .toList(),
                    failureDetail(restored.failure()));
        }

        RigManifest.ExecutionReceipt restoredReceipt = new RigManifest.ExecutionReceipt(
                restored.fixtureWrites(),
                restored.fixtureTruthWrites(),
                restored.subjectUseOnCalls(),
                0,
                restored.resolutions());
        boolean cellsMatch = restored.created().equals(previous.manifest().ownedCells());
        boolean receiptMatches = restoredReceipt.equals(previous.manifest().receipt());
        boolean structureMatches = restored.structuralReport().equals(
                previous.manifest().structuralReport());
        if (!cellsMatch || !receiptMatches || !structureMatches) {
            List<RigManifest.OwnedCell> residual =
                    rollbackAttempted(world, restored.attempted());
            RigManifest residueManifest = null;
            if (!residual.isEmpty()) {
                PlanAttempt mismatch = restored.withResidual(
                        new IllegalStateException("restored plan did not match previous manifest"),
                        residual);
                residueManifest = publishResidue(
                        previous.manifest().runId(),
                        world,
                        player,
                        previous.manifest().anchor(),
                        previous.plan(),
                        mismatch);
            }
            return RestoreResult.failure(
                    residueManifest,
                    residueManifest == null ? List.of() : residueManifest.ownedCells().stream()
                            .map(RigManifest.OwnedCell::pos)
                            .toList(),
                    "replayed rig truth differed: cells=" + cellsMatch
                            + ",receipt=" + receiptMatches
                            + ",structure=" + structureMatches);
        }

        ACTIVE.put(world, previous);
        return RestoreResult.restored(previous.manifest());
    }

    private static String failureDetail(RuntimeException failure) {
        if (failure == null) {
            return "unknown placement failure";
        }
        return failure.getMessage() == null
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
    }

    private static void requireEmptyMutationCell(ServerLevel world, BlockPos pos) {
        if (!world.hasChunkAt(pos)) {
            throw new IllegalStateException(
                    "rig mutation cell became unloaded at " + pos.toShortString());
        }
        if (!world.getBlockState(pos).isAir()
                || SlabAnchorAttachment.storedPlacementDyFact(world, pos).present()) {
            throw new IllegalStateException(
                    "rig mutation cell became occupied at " + pos.toShortString());
        }
    }

    private static RigManifest.OwnedCell placeFixture(
            ServerLevel world,
            String caseId,
            RigCase.FixtureCell fixture) {
        // The only direct state-write door in the rig executor. Subject cells never call it.
        boolean wrote = world.setBlock(fixture.pos(), fixture.state(), Block.UPDATE_ALL);
        BlockState actual = world.getBlockState(fixture.pos());
        SlabAnchorAttachment.PlacementDyFact stored =
                SlabAnchorAttachment.storedPlacementDyFact(world, fixture.pos());
        if (!wrote || !actual.equals(fixture.state()) || stored.present()) {
            throw new IllegalStateException("fixture write failed at " + fixture.pos().toShortString());
        }
        return new RigManifest.OwnedCell(
                fixture.pos(),
                actual,
                stored,
                RigManifest.CellRole.FIXTURE,
                caseId);
    }

    private static void writeMegaLabel(ServerLevel world, MegaAction action) {
        BlockEntity blockEntity = world.getBlockEntity(action.labelPos());
        if (!(blockEntity instanceof SignBlockEntity sign)) {
            throw new IllegalStateException(
                    "mega label sign did not create at " + action.labelPos().toShortString());
        }
        String shortItem = action.itemId().substring(action.itemId().indexOf(':') + 1);
        if (shortItem.length() > 15) {
            shortItem = shortItem.substring(0, 15);
        }
        String dyLine = "dy=" + Double.toString(action.expectedDy());
        String faceLine = action.face().getName().toUpperCase(Locale.ROOT);
        String finalShortItem = shortItem;
        sign.updateText(text -> text
                .setMessage(0, Component.literal(action.label()))
                .setMessage(1, Component.literal(finalShortItem))
                .setMessage(2, Component.literal(dyLine))
                .setMessage(3, Component.literal(faceLine)), true);
        sign.updateText(text -> text
                .setMessage(0, Component.literal(action.label()))
                .setMessage(1, Component.literal(finalShortItem))
                .setMessage(2, Component.literal(dyLine))
                .setMessage(3, Component.literal(faceLine)), false);
        sign.setWaxed(true);
        sign.setChanged();
        world.sendBlockUpdated(
                action.labelPos(),
                world.getBlockState(action.labelPos()),
                world.getBlockState(action.labelPos()),
                Block.UPDATE_CLIENTS);
    }

    private static void authorFixtureTruth(
            ServerLevel world,
            RigCase.FixtureCell fixture) {
        RigCase.FixtureAuthorship authorship = fixture.authorship();
        switch (authorship.kind()) {
            case NONE -> {
                return;
            }
            case COMPOUND_FULL_BLOCK -> {
                SlabAnchorAttachment.addAnchor(
                        world, fixture.pos(), world.getBlockState(fixture.pos()));
                SlabAnchorAttachment.addCompoundFullBlockAnchor(
                        world, fixture.pos(), world.getBlockState(fixture.pos()));
            }
            case COMPOUND_VISIBLE_SIDE_LOWER_SLAB -> {
                BlockPos sourcePos = authorship.sourcePos();
                SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(
                        world,
                        fixture.pos(),
                        world.getBlockState(fixture.pos()),
                        sourcePos,
                        world.getBlockState(sourcePos));
            }
            case STORED_DY -> SlabAnchorAttachment.writePlacementDy(
                    world,
                    fixture.pos(),
                    Double.longBitsToDouble(authorship.storedDyBits()));
        }
        if (!fixtureTruthMatches(world, fixture)) {
            throw new IllegalStateException(
                    "fixture truth write failed at " + fixture.pos().toShortString());
        }
    }

    private static SubjectResult placeSubjectViaUseOn(
            ServerLevel world,
            ServerPlayer player,
            RigCase.SubjectPlacement subject) {
        PlacementAim aim = subject.aim();
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack original = player.getItemInHand(hand).copy();
        ItemStack proxyStack = aim.heldItem();
        BlockHitResult hit = new BlockHitResult(
                aim.hitVector(), aim.face(), aim.visibleOwner(), false);
        BlockPlaceContext placementContext = new BlockPlaceContext(
                world, player, hand, proxyStack, hit);
        BlockPos resolvedTarget = placementContext.getClickedPos().immutable();
        if (!resolvedTarget.equals(aim.vanillaTarget())) {
            throw new IllegalStateException(
                    "vanilla target changed from " + aim.vanillaTarget().toShortString()
                            + " to " + resolvedTarget.toShortString());
        }

        InteractionResult[] result = {InteractionResult.PASS};
        player.setItemInHand(hand, proxyStack);
        try {
            SlabbedDiagnosticsBridge.withActionOrigin(
                    SlabbedDiagnosticsBridge.AUTO_USEON_PROXY,
                    new SlabbedDiagnosticsBridge.ActionOriginContext(
                            player.getUUID().toString(),
                            world.dimension().location().toString(),
                            resolvedTarget),
                    () -> result[0] = ForgeHooks.onPlaceItemIntoWorld(
                            new UseOnContext(player, hand, hit)));
        } finally {
            player.setItemInHand(hand, original);
        }
        if (!result[0].consumesAction()) {
            throw new IllegalStateException(
                    "Forge useOn did not consume the subject action at "
                            + resolvedTarget.toShortString());
        }
        BlockState actual = world.getBlockState(resolvedTarget);
        if (!actual.is(subject.expectedBlock())) {
            throw new IllegalStateException(
                    "subject landed as " + actual + " at " + resolvedTarget.toShortString());
        }
        SlabAnchorAttachment.PlacementDyFact stored =
                SlabAnchorAttachment.storedPlacementDyFact(world, resolvedTarget);
        if (!stored.present()) {
            throw new IllegalStateException(
                    "real useOn placement published no stored dy at " + resolvedTarget.toShortString());
        }
        LandingResolution.Lane lane = LandingResolution.Lane.fromDy(stored.valueOrNaN());
        if (lane != subject.expectedLane()) {
            throw new IllegalStateException(
                    "subject lane was " + lane + " instead of " + subject.expectedLane());
        }
        return new SubjectResult(
                actual,
                stored,
                new LandingResolution.Place(
                        resolvedTarget,
                        stored.rawBits(),
                        lane,
                        "forge_itemstack_use_on"));
    }

    private static NumericActionResult placeNumericSubjectViaUseOn(
            ServerLevel world,
            ServerPlayer player,
            BlockPos cursor,
            BlockPos intendedTarget,
            Block expectedBlock,
            String evidencePrefix) {
        evidencePrefix = requireText(evidencePrefix, "evidencePrefix");
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack proxyStack = new ItemStack(expectedBlock);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(cursor).add(0.0d, 0.5d, 0.0d),
                Direction.UP,
                cursor,
                false);
        BlockPlaceContext placementContext = new BlockPlaceContext(
                world, player, hand, proxyStack, hit);
        BlockPos resolvedTarget = placementContext.getClickedPos().immutable();
        if (!resolvedTarget.equals(cursor) && !resolvedTarget.equals(intendedTarget)) {
            throw new IllegalStateException(
                    "numeric tower resolved an unrelated placement cell "
                            + resolvedTarget.toShortString());
        }
        PlacementAim aim = new PlacementAim(
                cursor, Direction.UP, hit.getLocation(), resolvedTarget, proxyStack);

        CellSnapshot beforeCursor = snapshot(world, cursor);
        CellSnapshot beforeTarget = snapshot(world, intendedTarget);
        List<BlockPos> effectPositions = actionEnvelope(cursor, intendedTarget).stream()
                .filter(pos -> !pos.equals(cursor) && !pos.equals(intendedTarget))
                .toList();
        List<CellSnapshot> beforeEffects = effectPositions.stream()
                .map(pos -> snapshot(world, pos))
                .toList();

        ItemStack original = player.getItemInHand(hand).copy();
        InteractionResult[] result = {InteractionResult.PASS};
        RuntimeException[] thrown = {null};
        player.setItemInHand(hand, aim.heldItem());
        try {
            SlabbedDiagnosticsBridge.withActionOrigin(
                    SlabbedDiagnosticsBridge.AUTO_USEON_PROXY,
                    new SlabbedDiagnosticsBridge.ActionOriginContext(
                            player.getUUID().toString(),
                            world.dimension().location().toString(),
                            aim.vanillaTarget()),
                    () -> result[0] = ForgeHooks.onPlaceItemIntoWorld(
                            new UseOnContext(player, hand, hit)));
        } catch (RuntimeException failure) {
            thrown[0] = failure;
        } finally {
            player.setItemInHand(hand, original);
        }

        CellSnapshot afterCursor = snapshot(world, cursor);
        CellSnapshot afterTarget = snapshot(world, intendedTarget);
        for (int index = 0; index < effectPositions.size(); index++) {
            CellSnapshot after = snapshot(world, effectPositions.get(index));
            if (!beforeEffects.get(index).equals(after)) {
                IllegalStateException failure = new IllegalStateException(
                        "numeric tower useOn changed an undeclared effect cell at "
                                + effectPositions.get(index).toShortString());
                if (thrown[0] != null) {
                    failure.addSuppressed(thrown[0]);
                }
                throw failure;
            }
        }

        boolean cursorChanged = !beforeCursor.equals(afterCursor);
        boolean targetChanged = !beforeTarget.equals(afterTarget);
        boolean targetAdvanced = beforeTarget.state().isAir() && !afterTarget.state().isAir();
        if (targetAdvanced) {
            if (cursorChanged) {
                throw numericMutationFailure(
                        "numeric tower useOn changed both cursor and target", thrown[0]);
            }
            if (!afterTarget.state().is(expectedBlock) || !afterTarget.storedDy().present()) {
                throw numericMutationFailure(
                        "numeric tower target lacks expected state or stored truth at "
                                + intendedTarget.toShortString(),
                        thrown[0]);
            }
            LandingResolution.Lane lane = LandingResolution.Lane.fromDy(
                    afterTarget.storedDy().valueOrNaN());
            return new NumericActionResult(
                    NumericActionKind.ADVANCED,
                    afterTarget.state(),
                    afterTarget.storedDy(),
                    new LandingResolution.Place(
                            intendedTarget,
                            afterTarget.storedDy().rawBits(),
                            lane,
                            evidencePrefix.equals("numeric_tower")
                                    ? "forge_itemstack_use_on_numeric"
                                    : "forge_itemstack_use_on_" + evidencePrefix));
        }
        if (targetChanged) {
            throw numericMutationFailure(
                    "numeric tower target changed without becoming a placed cell at "
                            + intendedTarget.toShortString(),
                    thrown[0]);
        }
        if (cursorChanged) {
            if (afterCursor.state().isAir()) {
                throw numericMutationFailure(
                        "numeric tower in-cell action removed its cursor at "
                                + cursor.toShortString(),
                        thrown[0]);
            }
            return new NumericActionResult(
                    NumericActionKind.IN_CELL_CHANGE,
                    afterCursor.state(),
                    afterCursor.storedDy(),
                    new LandingResolution.PreserveVanilla(
                            evidencePrefix + "_in_cell_change"));
        }

        String reason = evidencePrefix + "_stall_" + result[0].name().toLowerCase();
        if (thrown[0] != null) {
            reason += "_" + thrown[0].getClass().getSimpleName();
        }
        return new NumericActionResult(
                NumericActionKind.STALLED,
                afterCursor.state(),
                afterCursor.storedDy(),
                new LandingResolution.Reject(reason));
    }

    private static IllegalStateException numericMutationFailure(
            String detail,
            RuntimeException cause) {
        return cause == null
                ? new IllegalStateException(detail)
                : new IllegalStateException(detail, cause);
    }

    private static CellSnapshot snapshot(ServerLevel world, BlockPos pos) {
        return new CellSnapshot(
                world.getBlockState(pos),
                SlabAnchorAttachment.storedPlacementDyFact(world, pos));
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

    private static RigManifest manifest(
            UUID runId,
            ServerLevel world,
            ServerPlayer player,
            BlockPos anchor,
            ExecutionPlan plan,
            List<RigManifest.OwnedCell> cells,
            int fixtureWrites,
            int fixtureTruthWrites,
            int subjectUseOnCalls,
            List<LandingResolution> resolutions,
            RigManifest.StructuralReport structuralReport) {
        return new RigManifest(
                runId,
                player.getUUID().toString(),
                world.dimension().location().toString(),
                anchor,
                plan.mode(),
                plan.caseIds(),
                cells,
                new RigManifest.ExecutionReceipt(
                        fixtureWrites,
                        fixtureTruthWrites,
                        subjectUseOnCalls,
                        0,
                        resolutions),
                structuralReport);
    }

    private static List<BlockPos> footprint(List<RigCase> cases) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (RigCase rigCase : cases) {
            for (RigCase.FixtureCell fixture : rigCase.fixtures()) {
                if (!positions.add(fixture.pos())) {
                    throw new IllegalArgumentException("duplicate fixture cell " + fixture.pos());
                }
            }
            for (RigCase.SubjectPlacement subject : rigCase.subjects()) {
                if (!positions.add(subject.aim().vanillaTarget())) {
                    throw new IllegalArgumentException(
                            "duplicate subject cell " + subject.aim().vanillaTarget());
                }
            }
        }
        return List.copyOf(positions);
    }

    private static boolean matches(
            ServerLevel world,
            ActiveRig active,
            RigManifest.OwnedCell cell) {
        if (!world.hasChunkAt(cell.pos())
                || !world.getBlockState(cell.pos()).equals(cell.expectedState())
                || !SlabAnchorAttachment.storedPlacementDyFact(world, cell.pos())
                        .equals(cell.expectedStoredDy())) {
            return false;
        }
        if (cell.role() != RigManifest.CellRole.FIXTURE) {
            return true;
        }
        if (active.plan() instanceof CaseExecutionPlan casePlan) {
            for (RigCase rigCase : casePlan.cases()) {
                for (RigCase.FixtureCell fixture : rigCase.fixtures()) {
                    if (fixture.pos().equals(cell.pos())) {
                        return fixtureTruthMatches(world, fixture);
                    }
                }
            }
            return false;
        }
        if (active.plan() instanceof MegaPlan megaPlan) {
            for (MegaAction action : megaPlan.actions()) {
                for (RigCase.FixtureCell fixture : action.fixtures()) {
                    if (fixture.pos().equals(cell.pos())) {
                        return fixtureTruthMatches(world, fixture);
                    }
                }
            }
            return false;
        }
        return true;
    }

    private static boolean fixtureTruthMatches(
            ServerLevel world,
            RigCase.FixtureCell fixture) {
        return switch (fixture.authorship().kind()) {
            case NONE -> true;
            case COMPOUND_FULL_BLOCK ->
                    SlabAnchorAttachment.isAnchored(world, fixture.pos())
                            && SlabAnchorAttachment.isCompoundFullBlockAnchor(
                                    world, fixture.pos())
                            && rawDyEquals(world, fixture.pos(), -1.0d);
            case COMPOUND_VISIBLE_SIDE_LOWER_SLAB ->
                    SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(
                                    world,
                                    fixture.pos(),
                                    world.getBlockState(fixture.pos()))
                            && rawDyEquals(world, fixture.pos(), -1.0d);
            case STORED_DY -> {
                SlabAnchorAttachment.PlacementDyFact stored =
                        SlabAnchorAttachment.storedPlacementDyFact(world, fixture.pos());
                yield stored.present() && stored.rawBits() == fixture.authorship().storedDyBits();
            }
        };
    }

    private static boolean rawDyEquals(
            ServerLevel world,
            BlockPos pos,
            double expected) {
        double actual = SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
        return Double.doubleToRawLongBits(actual)
                == Double.doubleToRawLongBits(expected);
    }

    private static boolean removeOwnedCell(ServerLevel world, BlockPos pos) {
        if (!world.hasChunkAt(pos)) {
            return false;
        }
        if (!world.getBlockState(pos).isAir()) {
            world.destroyBlock(pos, false);
        }
        if (world.getBlockState(pos).isAir()) {
            SlabAnchorAttachment.clearPlacementTruth(world, pos);
        }
        return world.getBlockState(pos).isAir()
                && !SlabAnchorAttachment.storedPlacementDyFact(world, pos).present();
    }

    private static List<RigManifest.OwnedCell> rollbackAttempted(
            ServerLevel world,
            List<AttemptedCell> attempted) {
        List<RigManifest.OwnedCell> residual = new ArrayList<>();
        for (int index = attempted.size() - 1; index >= 0; index--) {
            AttemptedCell cell = attempted.get(index);
            if (!removeOwnedCell(world, cell.pos())) {
                residual.add(0, cell.residual(world));
            }
        }
        return residual;
    }

    private static Direction horizontal(Direction direction) {
        return direction != null && direction.getAxis().isHorizontal()
                ? direction : Direction.SOUTH;
    }

    private sealed interface ExecutionPlan permits
            CaseExecutionPlan, NumericTowerPlan, StackPlan, MegaPlan {
        String mode();

        List<String> caseIds();

        List<BlockPos> footprint();
    }

    private record MegaPlan(
            int columns,
            List<MegaAction> actions,
            List<BlockPos> footprint) implements ExecutionPlan {
        private MegaPlan {
            actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
            footprint = immutablePositions(footprint);
            if (columns < 1 || actions.size() != columns * MEGA_ROW_COUNT
                    || footprint.isEmpty()) {
                throw new IllegalArgumentException("invalid mega execution plan");
            }
        }

        @Override
        public String mode() {
            return "mega";
        }

        @Override
        public List<String> caseIds() {
            return actions.stream().map(MegaAction::caseId).toList();
        }
    }

    private record MegaAction(
            String caseId,
            String label,
            int column,
            int row,
            String itemId,
            Item item,
            List<RigCase.FixtureCell> fixtures,
            BlockPos labelPos,
            BlockPos clicked,
            Direction face,
            BlockPos target,
            double expectedDy,
            String orientation) {
        private MegaAction {
            caseId = requireText(caseId, "caseId");
            label = requireText(label, "label");
            itemId = requireText(itemId, "itemId");
            item = Objects.requireNonNull(item, "item");
            fixtures = List.copyOf(Objects.requireNonNull(fixtures, "fixtures"));
            labelPos = Objects.requireNonNull(labelPos, "labelPos").immutable();
            BlockPos finalLabelPos = labelPos;
            clicked = Objects.requireNonNull(clicked, "clicked").immutable();
            face = Objects.requireNonNull(face, "face");
            target = Objects.requireNonNull(target, "target").immutable();
            orientation = requireText(orientation, "orientation");
            if (column < 0 || row < 0 || row >= MEGA_ROW_COUNT
                    || fixtures.isEmpty() || !Double.isFinite(expectedDy)
                    || fixtures.stream().noneMatch(
                            fixture -> fixture.pos().equals(finalLabelPos))) {
                throw new IllegalArgumentException("invalid mega action");
            }
        }

        private MegaCaseDescriptor descriptor() {
            return new MegaCaseDescriptor(
                    caseId,
                    label,
                    itemId,
                    column,
                    row,
                    Double.doubleToRawLongBits(expectedDy),
                    face,
                    orientation);
        }
    }

    private record CaseExecutionPlan(
            String mode,
            List<RigCase> cases,
            List<String> caseIds,
            List<BlockPos> footprint) implements ExecutionPlan {
        private CaseExecutionPlan(String mode, List<RigCase> cases) {
            this(
                    Objects.requireNonNull(mode, "mode"),
                    List.copyOf(Objects.requireNonNull(cases, "cases")),
                    List.copyOf(cases.stream().map(RigCase::id).toList()),
                    SlabbedRigService.footprint(cases));
        }

        private CaseExecutionPlan {
            mode = Objects.requireNonNull(mode, "mode");
            cases = List.copyOf(Objects.requireNonNull(cases, "cases"));
            caseIds = List.copyOf(Objects.requireNonNull(caseIds, "caseIds"));
            footprint = immutablePositions(footprint);
            if (cases.isEmpty() || caseIds.size() != cases.size()) {
                throw new IllegalArgumentException("case execution plan must not be empty");
            }
        }
    }

    private record ActiveRig(
            RigManifest manifest,
            ExecutionPlan plan,
            boolean restorable) {
        private ActiveRig {
            manifest = Objects.requireNonNull(manifest, "manifest");
            if (restorable && plan == null) {
                throw new IllegalArgumentException("restorable rig requires its exact plan");
            }
            if (!restorable && plan != null) {
                throw new IllegalArgumentException("rollback residue cannot claim a replay plan");
            }
            if (!restorable && !(manifest.structuralReport()
                    instanceof RigManifest.ResidueStructuralReport)) {
                throw new IllegalArgumentException(
                        "rollback residue requires an incomplete structural report");
            }
            if (plan != null && !manifest.mode().equals(plan.mode())) {
                throw new IllegalArgumentException("active rig mode and manifest must agree");
            }
        }

        private static ActiveRig complete(
                RigManifest manifest,
                ExecutionPlan plan) {
            return new ActiveRig(manifest, plan, true);
        }

        private static ActiveRig residue(RigManifest manifest) {
            return new ActiveRig(manifest, null, false);
        }
    }

    private record PlanAttempt(
            List<RigManifest.OwnedCell> created,
            List<AttemptedCell> attempted,
            int fixtureWrites,
            int fixtureTruthWrites,
            int subjectUseOnCalls,
            List<LandingResolution> resolutions,
            RigManifest.StructuralReport structuralReport,
            RuntimeException failure,
            List<RigManifest.OwnedCell> residual) {
        private PlanAttempt {
            created = List.copyOf(created);
            attempted = List.copyOf(attempted);
            resolutions = List.copyOf(resolutions);
            structuralReport = Objects.requireNonNull(structuralReport, "structuralReport");
            residual = List.copyOf(residual);
        }

        private static PlanAttempt success(
                List<RigManifest.OwnedCell> created,
                List<AttemptedCell> attempted,
                int fixtureWrites,
                int fixtureTruthWrites,
                int subjectUseOnCalls,
                List<LandingResolution> resolutions,
                RigManifest.StructuralReport structuralReport) {
            return new PlanAttempt(
                    created, attempted, fixtureWrites, fixtureTruthWrites, subjectUseOnCalls,
                    resolutions, structuralReport, null, List.of());
        }

        private static PlanAttempt failure(
                List<RigManifest.OwnedCell> created,
                List<AttemptedCell> attempted,
                int fixtureWrites,
                int fixtureTruthWrites,
                int subjectUseOnCalls,
                List<LandingResolution> resolutions,
                RigManifest.StructuralReport structuralReport,
                RuntimeException failure,
                List<RigManifest.OwnedCell> residual) {
            return new PlanAttempt(
                    created, attempted, fixtureWrites, fixtureTruthWrites, subjectUseOnCalls,
                    resolutions, structuralReport,
                    Objects.requireNonNull(failure, "failure"), residual);
        }

        private boolean success() {
            return failure == null;
        }

        private PlanAttempt withResidual(
                RuntimeException replacementFailure,
                List<RigManifest.OwnedCell> replacementResidual) {
            return failure(
                    created,
                    attempted,
                    fixtureWrites,
                    fixtureTruthWrites,
                    subjectUseOnCalls,
                    resolutions,
                    structuralReport,
                    replacementFailure,
                    replacementResidual);
        }
    }

    private record RestoreResult(
            boolean success,
            RigManifest manifest,
            List<BlockPos> residualCells,
            String detail) {
        private RestoreResult {
            residualCells = immutablePositions(residualCells);
            detail = Objects.requireNonNull(detail, "detail");
            if (success && (manifest == null || !residualCells.isEmpty())) {
                throw new IllegalArgumentException(
                        "successful restore requires a manifest and no residual cells");
            }
            if (!success && ((manifest == null) != residualCells.isEmpty())) {
                throw new IllegalArgumentException(
                        "failed restore manifest and residual cells must agree");
            }
        }

        private static RestoreResult restored(RigManifest manifest) {
            return new RestoreResult(true, manifest, List.of(), "previous rig restored exactly");
        }

        private static RestoreResult failure(
                RigManifest manifest,
                List<BlockPos> residualCells,
                String detail) {
            return new RestoreResult(false, manifest, residualCells, detail);
        }
    }

    private record SubjectResult(
            BlockState state,
            SlabAnchorAttachment.PlacementDyFact storedDy,
            LandingResolution.Place resolution) {
    }

    private enum NumericActionKind {
        ADVANCED,
        IN_CELL_CHANGE,
        STALLED
    }

    private record NumericActionResult(
            NumericActionKind kind,
            BlockState state,
            SlabAnchorAttachment.PlacementDyFact storedDy,
            LandingResolution resolution) {
        private NumericActionResult {
            kind = Objects.requireNonNull(kind, "kind");
            state = Objects.requireNonNull(state, "state");
            storedDy = Objects.requireNonNull(storedDy, "storedDy");
            resolution = Objects.requireNonNull(resolution, "resolution");
        }
    }

    private record CellSnapshot(
            BlockState state,
            SlabAnchorAttachment.PlacementDyFact storedDy) {
        private CellSnapshot {
            state = Objects.requireNonNull(state, "state");
            storedDy = Objects.requireNonNull(storedDy, "storedDy");
        }
    }

    /** Position-first rollback ledger for mutations that have not passed validation yet. */
    private record AttemptedCell(
            BlockPos pos,
            BlockState fallbackState,
            RigManifest.CellRole role,
            String caseId) {
        private AttemptedCell {
            pos = pos.immutable();
        }

        private RigManifest.OwnedCell residual(ServerLevel world) {
            if (!world.hasChunkAt(pos)) {
                return new RigManifest.OwnedCell(
                        pos,
                        fallbackState,
                        SlabAnchorAttachment.PlacementDyFact.absent(),
                        role,
                        caseId);
            }
            return new RigManifest.OwnedCell(
                    pos,
                    world.getBlockState(pos),
                    SlabAnchorAttachment.storedPlacementDyFact(world, pos),
                    role,
                    caseId);
        }
    }

    private record CasesAdmission(
            CasesRunOutcome outcome,
            List<BlockPos> conflicts,
            String detail) {
        private CasesAdmission {
            conflicts = immutablePositions(conflicts);
            detail = Objects.requireNonNull(detail, "detail");
            if ((outcome == null) != conflicts.isEmpty()) {
                throw new IllegalArgumentException(
                        "cases admission outcome and conflicts must agree");
            }
        }

        private boolean admitted() {
            return outcome == null;
        }

        private static CasesAdmission allow() {
            return new CasesAdmission(null, List.of(), "destination admitted");
        }

        private static CasesAdmission refused(
                CasesRunOutcome outcome,
                List<BlockPos> conflicts,
                String detail) {
            return new CasesAdmission(
                    Objects.requireNonNull(outcome, "outcome"),
                    conflicts,
                    detail);
        }
    }

    public enum CasesRunOutcome {
        PAGE_COMPLETED,
        GENERIC_RIG_ACTIVE,
        STORE_NOT_READY,
        TRANSITION_PAUSED,
        BOARD_PRESENT_CLEAR_FIRST,
        COMPLETE,
        OUT_OF_BOUNDS,
        UNLOADED,
        OCCUPIED,
        CLEAR_FAILED,
        EXECUTION_INTERRUPTED
    }

    public record CasesRunResult(
            CasesRunOutcome outcome,
            int page,
            int pageCount,
            int cases,
            int autoCases,
            int deferredCompleted,
            int placed,
            int preservedVanilla,
            int rejected,
            int lawRed,
            int ownedCells,
            List<BlockPos> conflicts,
            String detail) {
        public CasesRunResult {
            outcome = Objects.requireNonNull(outcome, "outcome");
            conflicts = immutablePositions(conflicts);
            detail = Objects.requireNonNull(detail, "detail");
            if (page < 0 || pageCount < 1
                    || cases < 0 || autoCases < 0 || autoCases > cases
                    || deferredCompleted < 0 || deferredCompleted > cases - autoCases
                    || placed < 0 || preservedVanilla < 0 || rejected < 0 || lawRed < 0
                    || placed + preservedVanilla + rejected + lawRed > autoCases
                    || ownedCells < 0) {
                throw new IllegalArgumentException("invalid cases run counts");
            }
            if (outcome == CasesRunOutcome.PAGE_COMPLETED
                    && (page < 1
                            || cases == 0
                            || deferredCompleted != cases - autoCases
                            || placed + preservedVanilla + rejected + lawRed != autoCases)) {
                throw new IllegalArgumentException(
                        "completed cases page must account for every terminal case");
            }
        }

        public boolean success() {
            return outcome == CasesRunOutcome.PAGE_COMPLETED;
        }
    }

    public record CasesStatus(
            boolean storePresent,
            SlabbedRigCaseEvidence.ResumeStatus evidenceStatus,
            int nextPage,
            int pageCount,
            boolean active,
            int activeOrdinal,
            int completedOwnedCells,
            boolean boardPresent,
            int presentOwnedCells,
            int intactCells,
            int absentCells,
            List<BlockPos> conflicts,
            boolean clearEligible,
            boolean releaseRepairEligible,
            String detail) {
        public CasesStatus {
            evidenceStatus = Objects.requireNonNull(evidenceStatus, "evidenceStatus");
            conflicts = immutablePositions(conflicts);
            detail = Objects.requireNonNull(detail, "detail");
            if (nextPage < 0 || pageCount < 1 || activeOrdinal < 0
                    || completedOwnedCells < 0 || presentOwnedCells < 0
                    || intactCells < 0 || absentCells < 0
                    || intactCells + absentCells + conflicts.size()
                            > completedOwnedCells + presentOwnedCells
                    || active != (activeOrdinal > 0
                            || evidenceStatus
                                    == SlabbedRigCaseEvidence.ResumeStatus.PREPARED_NO_MUTATION
                            || evidenceStatus
                                    == SlabbedRigCaseEvidence.ResumeStatus
                                            .INTERRUPTED_UNKNOWN_OWNERSHIP
                            || evidenceStatus
                                    == SlabbedRigCaseEvidence.ResumeStatus.COMPLETED_PENDING_FINAL
                            || evidenceStatus
                                    == SlabbedRigCaseEvidence.ResumeStatus.FINAL_PENDING_CURSOR)
                    || clearEligible && (!boardPresent || active || !conflicts.isEmpty())
                    || releaseRepairEligible && !clearEligible) {
                throw new IllegalArgumentException("invalid cases status");
            }
        }

        private static CasesStatus absent(int pageCount) {
            return new CasesStatus(
                    false,
                    SlabbedRigCaseEvidence.ResumeStatus.FRESH,
                    1,
                    pageCount,
                    false,
                    0,
                    0,
                    false,
                    0,
                    0,
                    0,
                    List.of(),
                    false,
                    false,
                    "no durable cases store exists");
        }

        private static CasesStatus fault(
                SlabbedRigCaseEvidence.ResumeStatus status,
                int pageCount,
                String detail) {
            return new CasesStatus(
                    true,
                    status,
                    0,
                    pageCount,
                    false,
                    0,
                    0,
                    false,
                    0,
                    0,
                    0,
                    List.of(),
                    false,
                    false,
                    detail);
        }
    }

    public enum CasesResumeOutcome {
        ABORTED_PREPARED_PAGE,
        FINALIZED_COMPLETED_PAGE,
        ADVANCED_SEALED_PAGE,
        RELEASED_EMPTY_BOARD,
        NO_REPAIR,
        REFUSED
    }

    public record CasesResumeResult(
            CasesResumeOutcome outcome,
            CasesStatus status,
            String detail) {
        public CasesResumeResult {
            outcome = Objects.requireNonNull(outcome, "outcome");
            status = Objects.requireNonNull(status, "status");
            detail = Objects.requireNonNull(detail, "detail");
        }

        public boolean success() {
            return outcome == CasesResumeOutcome.ABORTED_PREPARED_PAGE
                    || outcome == CasesResumeOutcome.FINALIZED_COMPLETED_PAGE
                    || outcome == CasesResumeOutcome.ADVANCED_SEALED_PAGE
                    || outcome == CasesResumeOutcome.RELEASED_EMPTY_BOARD;
        }
    }

    public enum CasesTransitionTestFault {
        NONE,
        AFTER_PREPARE_BEFORE_REPLACEMENT_CLEAR
    }

    public enum CasesClearOutcome {
        CLEARED,
        NO_ACTIVE,
        CONFLICT,
        INTERRUPTED,
        GENERIC_RIG_ACTIVE,
        RESIDUE,
        RELEASE_PENDING
    }

    public record CasesClearResult(
            CasesClearOutcome outcome,
            int removedCells,
            List<BlockPos> residualCells,
            String detail) {
        public CasesClearResult {
            outcome = Objects.requireNonNull(outcome, "outcome");
            residualCells = immutablePositions(residualCells);
            detail = Objects.requireNonNull(detail, "detail");
            if (removedCells < 0) {
                throw new IllegalArgumentException("removedCells must be non-negative");
            }
        }

        public boolean success() {
            return outcome == CasesClearOutcome.CLEARED
                    || outcome == CasesClearOutcome.NO_ACTIVE;
        }
    }

    public enum BuildOutcome {
        BUILT,
        INVALID_REQUEST,
        ACTIVE_RIG,
        OCCUPIED,
        OUT_OF_BOUNDS,
        UNLOADED,
        PLACEMENT_FAILED,
        RESTORE_FAILED,
        ROLLBACK_RESIDUE
    }

    public record BuildResult(
            BuildOutcome outcome,
            RigManifest manifest,
            List<BlockPos> conflicts,
            String detail) {
        public BuildResult {
            outcome = Objects.requireNonNull(outcome, "outcome");
            conflicts = immutablePositions(conflicts);
            detail = Objects.requireNonNull(detail, "detail");
            if (outcome == BuildOutcome.ROLLBACK_RESIDUE
                    && (manifest == null
                            || manifest.ownedCells().isEmpty()
                            || conflicts.isEmpty()
                            || !(manifest.structuralReport()
                                    instanceof RigManifest.ResidueStructuralReport))) {
                throw new IllegalArgumentException(
                        "rollback-residue outcome requires an honest residual ownership manifest");
            }
            if (outcome == BuildOutcome.RESTORE_FAILED
                    && (manifest != null || !conflicts.isEmpty())) {
                throw new IllegalArgumentException(
                        "clean restore failure cannot claim owned residual cells");
            }
        }

        public boolean success() {
            return outcome == BuildOutcome.BUILT;
        }

        private static BuildResult failure(
                BuildOutcome outcome,
                RigManifest manifest,
                List<BlockPos> conflicts,
                String detail) {
            return new BuildResult(outcome, manifest, conflicts, detail);
        }
    }

    public record RigStatus(
            boolean active,
            RigManifest manifest,
            int intactCells,
            int ownedCells,
            List<BlockPos> conflicts,
            boolean clearEligible) {
        public RigStatus {
            conflicts = immutablePositions(conflicts);
            if (intactCells < 0 || ownedCells < 0 || intactCells > ownedCells) {
                throw new IllegalArgumentException("invalid rig status counts");
            }
            if (active != (manifest != null)) {
                throw new IllegalArgumentException("active status and manifest must agree");
            }
        }
    }

    public enum ClearOutcome {
        CLEARED,
        NO_ACTIVE,
        CONFLICT,
        RESIDUE
    }

    public record ClearResult(
            ClearOutcome outcome,
            RigManifest manifest,
            int removedCells,
            List<BlockPos> residualCells,
            String detail) {
        public ClearResult {
            outcome = Objects.requireNonNull(outcome, "outcome");
            residualCells = immutablePositions(residualCells);
            detail = Objects.requireNonNull(detail, "detail");
            if (removedCells < 0) {
                throw new IllegalArgumentException("removedCells must be non-negative");
            }
            if (outcome == ClearOutcome.RESIDUE
                    && (manifest == null
                            || residualCells.isEmpty()
                            || !(manifest.structuralReport()
                                    instanceof RigManifest.ResidueStructuralReport))) {
                throw new IllegalArgumentException(
                        "clear residue requires an honest residual ownership manifest");
            }
        }

        public boolean success() {
            return outcome == ClearOutcome.CLEARED || outcome == ClearOutcome.NO_ACTIVE;
        }
    }

    private static List<BlockPos> immutablePositions(List<BlockPos> positions) {
        Objects.requireNonNull(positions, "positions");
        return positions.stream()
                .map(pos -> Objects.requireNonNull(pos, "position").immutable())
                .toList();
    }

    private static List<BlockPos> distinctPositions(List<BlockPos> positions) {
        return immutablePositions(new ArrayList<>(new LinkedHashSet<>(positions)));
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }
}
