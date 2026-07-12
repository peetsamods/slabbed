package com.slabbed.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.BuildStamp;
import com.slabbed.util.LiveCursorIntentRecorder;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabTestKit;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code /slabrig <preset>} — builds Slabbed's standard live-test rigs in the world next to the
 * player, instantly and identically every time, so a human tester never rebuilds a test row by hand.
 *
 * <p>This is dev tooling that SHIPS in every jar (the debug-tooling convention: present, default
 * behaviour-neutral, player-invocable). It is a SERVER command (it authors real block state and the
 * genuine Slabbed anchor attachments), so it registers via {@link CommandRegistrationCallback} and
 * lives in a shipping package — NOT {@code com.slabbed.dev}, which the release jar strips.
 *
 * <p>It builds exact planned scenery plus explicitly labelled synthetic {@code useOn} subjects. Every
 * synthetic action is recorder-labelled {@code AUTO_USEON_PROXY}; it is diagnostic setup, never a
 * substitute for raw player-authored WYSIWYG closure. Scenery uses {@code w.setBlock(..)} plus the
 * public attachment-authoring API
 * ({@link SlabAnchorAttachment#addAnchor}, {@link SlabAnchorAttachment#addCompoundFullBlockAnchor},
 * {@link SlabAnchorAttachment#addCompoundVisibleSideLowerSlab}). It never touches height computation,
 * so it cannot change production WYSIWYG behavior. The rigs mirror what the gametest builders author,
 * and their readback reports any scene that does not match its declared structure
 * ({@code NeighborUpdateInvarianceTest.markedSlabRig} /
 * {@code AnchoredDepthReadbackTest.buildCompoundVisibleSupport}).
 *
 * <p>Presets:
 * <ul>
 *   <li>{@code /slabrig tower [force]} — the compound-visible tower: ground, bottom slab, stone,
 *       bottom slab, compound full block (anchored, reads -1.0) with a marked side slab beside it.
 *   <li>{@code /slabrig rows [n] [force]} — n columns (default 16, 2-cell spacing) of lowered -0.5
 *       supports (stone-on-bottom-slab-on-ground), plus a parallel row of genuine -1.0 compound
 *       supports, each row labelled with a sign stating the expected dy.
 *   <li>{@code /slabrig stacks [max_length] [page] [force]} — one exact 4x4 page of every S/B word
 *       through length five, built above separately named standardized lowered seeds.
 *   <li>{@code /slabrig status} — reports the exact server-session manifest and structural state.
 *   <li>{@code /slabrig clear} — directly removes exact cells owned by the last tracked rig; vanilla
 *       neighbor survival may also remove a dependent whose support was one of those cells.
 * </ul>
 *
 * <p>Safety: builders refuse to overwrite non-empty reserved cells (add the literal {@code force} to
 * overwrite direct clear-owned cells), refuse to build below the world's minimum build height,
 * pre-clean every exact clear-owned cell's Slabbed attachments (flag-2 {@code setBlock} does NOT fire
 * the removal hook, so a rig authored over stale markers would be "haunted" — its sign would lie), and read back
 * {@link SlabSupport#getYOffset} after authoring so a rig that does not measure what its sign says
 * warns in red instead of reporting success.
 */
public final class SlabRigCommand {

    private SlabRigCommand() {
    }

    /** Build flag: notify clients, no neighbor updates (matches the gametest rig builders). */
    private static final int FLAG = Block.UPDATE_CLIENTS;

    /**
     * Clear flag: UPDATE_ALL (not UPDATE_CLIENTS) so support-requiring neighbours left behind by a
     * hand-placed subject get their vanilla survival recheck when the rig is torn down.
     */
    private static final int CLEAR_FLAG = Block.UPDATE_ALL;

    /** Cells of headroom tracked/scanned/cleared above the rig top, where the tester places subjects. */
    private static final int HEADROOM = 2;

    private static final int DEFAULT_ROWS = 16;
    private static final int COLUMN_SPACING = 2;
    private static final double EPS = 1.0e-6;

    /** The mega rig's four support variants, one per row (see {@link #buildMegaColumn}). */
    private static final int MEGA_ROW_COUNT = 4;
    /** The overhang_and_ceiling row: its auto-item is a hanging attempt (clicks the ceiling down-face). */
    private static final int MEGA_ROW_HANGING = 3;
    /** Cap the refused-id list echoed in the mega chat summary. */
    private static final int MAX_REFUSED_LISTED = 15;
    /** Default per-variant column count for {@code /slabrig mega} (capped by the placeable-kit size). */
    private static final int DEFAULT_MEGA_COLUMNS = 40;
    /** Expected support-surface dy per mega row, in row order (self-verify against the sign claims). */
    private static final double[] MEGA_ROW_DY = {0.0, -0.5, -1.0, -0.5};
    private static final String[] MEGA_ROW_NAME = {
            "bottom slab", "slab+block", "compound column", "overhang_and_ceiling"};
    /** Highest vertical cell offset any {@code mega} column variant touches (the compound FB / ceiling). */
    private static final int MEGA_TOP_OFFSET = 4;

    /** Default tower count / height for {@code /slabrig tower <n> [height]} (the deep-stack rig). */
    private static final int DEFAULT_TOWER_COUNT = 4;
    private static final int MAX_TOWER_COUNT = 8;
    private static final int DEFAULT_TOWER_HEIGHT = 8;
    private static final int MAX_TOWER_HEIGHT = 16;
    /**
     * Vertical index of the SEAT within each tower's lowered base: the base occupies cell offsets
     * 0..{@value} (ground stone, slab, stone, slab) and the top slab at offset {@value} is the seat the
     * alternating stack builds on.
     */
    private static final int TOWER_SEAT_INDEX = 3;
    /**
     * Grammar bounds for {@code /slabrig platform <y>}: generously wider than any dimension's build
     * range (datapacks max out at [-2032, 2031]), but bounded so the rig-top arithmetic can never be
     * fed an extreme int; the per-world {@link #aboveWorldTop}/{@link #belowWorldBottom} guards do the
     * real limit check.
     */
    private static final int MIN_PLATFORM_Y = -4096;
    private static final int MAX_PLATFORM_Y = 4096;
    private static final int MAX_STACK_LENGTH = 5;
    private static final int DEFAULT_STACK_LENGTH = 5;
    private static final int STACK_PAGE_SIZE = 16;
    private static final int STACK_GRID_SIDE = 4;
    /** RIG-2 visual board: four item columns x four topology rows, with isolated effect guards. */
    private static final int CASE_TILE_SPACING = 8;
    private static final int CASE_MAX_TOP_OFFSET = TOWER_SEAT_INDEX + MAX_STACK_LENGTH + 1 + HEADROOM;
    private static final String EMPTY_CASE_MARKERS = "frozenFlat=false,anchored=false,compoundFull=false,"
            + "sideLower=false,sideUpper=false,sideDouble=false,ownerTop=false,persistentCarrier=false";
    /**
     * The alternating recipes cycled across towers (index {@code i % length}), one token per cell:
     * {@code S} = a slab placed via a real item {@code useOn} click, {@code B} = a full block the same
     * way. Read top-to-bottom with {@link #TOWER_LABELS} for the matching display name.
     */
    private static final String[] TOWER_RECIPES = {"SB", "SSBB", "BS", "S"};
    private static final String[] TOWER_LABELS = {"SBSB", "SSBB", "BSBS", "SSSS"};

    private static final List<String> STACK_RECIPES = buildStackRecipes();

    /**
     * In-memory server-session-bound exact manifests. The key includes the actual server object by
     * identity, so a new integrated-server world can never reuse a prior world's record just because
     * player UUID and dimension id match.
     */
    private static final Map<RigKey, RigManifest> LAST_MANIFEST = new ConcurrentHashMap<>();
    /** Development-only deterministic fault injection, keyed to the exact command session/case. */
    private static final Map<RigKey, CasePostUseTestHook> CASE_POST_USE_TEST_HOOKS =
            new ConcurrentHashMap<>();
    private static final UUID CONSOLE_KEY = new UUID(0L, 0L);
    private static boolean lifecycleHookRegistered;

    private record CasePostUseTestHook(String caseId, Runnable hook) {
    }

    /** Identity of a remembered rig: actual server session, player, and dimension. */
    private static final class RigKey {
        private final MinecraftServer server;
        private final UUID player;
        private final ResourceKey<Level> dimension;

        private RigKey(MinecraftServer server, UUID player, ResourceKey<Level> dimension) {
            this.server = server;
            this.player = player;
            this.dimension = dimension;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof RigKey key
                    && server == key.server
                    && player.equals(key.player)
                    && dimension.equals(key.dimension);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * System.identityHashCode(server) + player.hashCode()) + dimension.hashCode();
        }
    }

    /** One immutable page view of the canonical S/B catalog. */
    public record StackPage(int maxLength, int page, int pageCount, int firstCatalogIndex,
                            List<String> recipes) {
        public StackPage {
            recipes = List.copyOf(recipes);
        }
    }

    private record StackEntry(int catalogIndex, String recipe, BlockPos seed, List<BlockPos> recipeCells) {
        private StackEntry {
            recipeCells = List.copyOf(recipeCells);
        }
    }

    /** Mutable execution row whose immutable snapshots are written before and after page mutation. */
    private static final class CaseRuntimeEntry {
        private final SlabRigCaseCatalog.CaseDefinition definition;
        private final BlockPos tileBase;
        private final List<BlockPos> plannedStructure;
        private final List<BlockPos> reserved;
        private final List<BlockPos> effect;
        private final List<BlockPos> guard;
        private final BlockPos clicked;
        private final BlockPos target;
        private String structureStatus = "PLANNED";
        private String structureDetail = "planned";
        private List<SlabRigCasePageManifest.StructureCellState> actualStructure = List.of();
        private List<SlabRigCasePageManifest.StructureCellState> postActionStructure = List.of();
        private String postActionStructureStatus = "NOT_RUN";
        private String postActionStructureDetail = "action not run";
        private List<SlabRigCasePageManifest.CellState> externalGuardContext = List.of();
        private String attemptStatus = "PLANNED";
        private String outcome = "PLANNED";
        private String actionOrigin = "NOT_RUN";
        private String interactionResult = "NOT_RUN";
        private boolean interactionConsumesAction;
        private String stackItemBefore = "NOT_RUN";
        private int stackBefore;
        private String stackItemAfter = "NOT_RUN";
        private int stackAfter;
        private boolean persistentSubjectPresent;
        private String detail = "planned";
        private List<SlabRigCasePageManifest.CellChange> actualChanges = List.of();

        private CaseRuntimeEntry(SlabRigCaseCatalog.CaseDefinition definition, BlockPos tileBase,
                                 List<BlockPos> plannedStructure, List<BlockPos> reserved,
                                 List<BlockPos> effect, List<BlockPos> guard,
                                 BlockPos clicked, BlockPos target) {
            this.definition = definition;
            this.tileBase = tileBase.immutable();
            this.plannedStructure = List.copyOf(plannedStructure);
            this.reserved = List.copyOf(reserved);
            this.effect = List.copyOf(effect);
            this.guard = List.copyOf(guard);
            this.clicked = clicked.immutable();
            this.target = target.immutable();
        }

        private SlabRigCasePageManifest.CaseAttempt snapshot() {
            return new SlabRigCasePageManifest.CaseAttempt(definition, tileBase, plannedStructure,
                    structureStatus, structureDetail, actualStructure, postActionStructure,
                    postActionStructureStatus, postActionStructureDetail, externalGuardContext,
                    reserved, effect, clicked, Direction.UP.getName(), target, attemptStatus,
                    actionOrigin, outcome,
                    interactionResult, interactionConsumesAction, stackItemBefore, stackBefore,
                    stackItemAfter, stackAfter, persistentSubjectPresent, detail, actualChanges);
        }
    }

    private record PlacementAttempt(Set<BlockPos> changed,
                                    List<SlabRigCasePageManifest.CellChange> changes,
                                    String interactionResult, boolean interactionConsumesAction,
                                    String stackItemBefore, int stackBefore,
                                    String stackItemAfter, int stackAfter,
                                    boolean persistentSubjectPresent,
                                    String error, boolean outsideEffect) {
        private PlacementAttempt {
            changed = Set.copyOf(changed);
            changes = List.copyOf(changes);
        }
    }

    /** Exact result of the real useOn seam; public only for the same-count vanish GameTest. */
    public record CasePlacementProbeForTests(
            String interactionResult,
            boolean interactionConsumesAction,
            String stackItemBefore,
            int stackBefore,
            String stackItemAfter,
            int stackAfter,
            boolean persistentSubjectPresent,
            int observedChangeCount,
            String outcome,
            String error,
            boolean outsideEffect) {
    }

    /** Exact mutation/ownership record. Bounds are derived display metadata only. */
    private static final class RigManifest {
        private final RigKey key;
        private final String preset;
        private final BlockPos base;
        private final Direction facing;
        private final int maxLength;
        private final int page;
        private final int pageCount;
        private final int firstRecipeIndex;
        private final int lastRecipeIndex;
        private final LinkedHashSet<BlockPos> reservedCells = new LinkedHashSet<>();
        private final LinkedHashMap<BlockPos, BlockState> authoredStates = new LinkedHashMap<>();
        private final LinkedHashSet<BlockPos> attachmentCells = new LinkedHashSet<>();
        private final LinkedHashSet<BlockPos> subjectSlots = new LinkedHashSet<>();
        private final LinkedHashSet<BlockPos> clearOwnedCells = new LinkedHashSet<>();
        private final List<StackEntry> stacks = new ArrayList<>();
        private SlabRigCaseCatalog.Snapshot caseSnapshot;
        private SlabRigCaseCatalog.CasePage casePage;
        private boolean caseFrozenDyEnabled;
        private final List<CaseRuntimeEntry> cases = new ArrayList<>();
        private String catalogArtifact = "none";
        private String previousContiguousManifestHash = "unresolved";
        private String plannedCaseArtifact = "none";
        private String finalCaseArtifact = "none";
        private String finalCaseManifestHash = "none";
        private String caseProgressNote = "not-written";
        private SlabRigCasePageManifest.PageManifest finalCaseManifest;
        private boolean structurallyComplete;
        private String structuralNote = "planned";

        private RigManifest(RigKey key, String preset, BlockPos base, Direction facing,
                            int maxLength, int page, int pageCount, int firstRecipeIndex,
                            int lastRecipeIndex) {
            this.key = key;
            this.preset = preset;
            this.base = base.immutable();
            this.facing = facing;
            this.maxLength = maxLength;
            this.page = page;
            this.pageCount = pageCount;
            this.firstRecipeIndex = firstRecipeIndex;
            this.lastRecipeIndex = lastRecipeIndex;
        }

        private void claimStructure(BlockPos pos) {
            BlockPos immutable = pos.immutable();
            reservedCells.add(immutable);
            clearOwnedCells.add(immutable);
        }

        private void reserveSafety(BlockPos pos) {
            reservedCells.add(pos.immutable());
        }

        private void declareSubject(BlockPos pos) {
            BlockPos immutable = pos.immutable();
            reservedCells.add(immutable);
            subjectSlots.add(immutable);
            clearOwnedCells.add(immutable);
        }

        private void touchAttachment(BlockPos pos) {
            attachmentCells.add(pos.immutable());
        }

        private void recordAuthored(BlockPos pos, BlockState before, BlockState after) {
            if (!Objects.equals(before, after)) {
                BlockPos immutable = pos.immutable();
                authoredStates.put(immutable, after);
                clearOwnedCells.add(immutable);
            }
        }

        private Bounds bounds() {
            return Bounds.of(reservedCells);
        }
    }

    // ── registration ─────────────────────────────────────────────────────────

    /** Wires the command into the server dispatcher for every world (integrated and dedicated). */
    public static void register() {
        synchronized (SlabRigCommand.class) {
            if (!lifecycleHookRegistered) {
                lifecycleHookRegistered = true;
                ServerLifecycleEvents.SERVER_STOPPED.register(SlabRigCommand::clearServerSession);
                SlabRigHangingDirectExecutor.register();
            }
        }
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher));
    }

    /** Builds the {@code slabrig} node into {@code dispatcher}. Visible for the smoke test. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("slabrig")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(SlabRigCommand::usage)
                        .then(Commands.literal("tower")
                                .executes(ctx -> tower(ctx, false))
                                .then(Commands.literal("force")
                                        .executes(ctx -> tower(ctx, true)))
                                // /slabrig tower <n> [height] [force] — the alternating deep-stack rig
                                // (distinct from the bare/force compound-tower preset above).
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, MAX_TOWER_COUNT))
                                        .executes(ctx -> towerRig(ctx, IntegerArgumentType.getInteger(ctx, "n"),
                                                DEFAULT_TOWER_HEIGHT, false))
                                        .then(Commands.literal("force")
                                                .executes(ctx -> towerRig(ctx, IntegerArgumentType.getInteger(ctx, "n"),
                                                        DEFAULT_TOWER_HEIGHT, true)))
                                        .then(Commands.argument("height", IntegerArgumentType.integer(1, MAX_TOWER_HEIGHT))
                                                .executes(ctx -> towerRig(ctx, IntegerArgumentType.getInteger(ctx, "n"),
                                                        IntegerArgumentType.getInteger(ctx, "height"), false))
                                                .then(Commands.literal("force")
                                                        .executes(ctx -> towerRig(ctx, IntegerArgumentType.getInteger(ctx, "n"),
                                                                IntegerArgumentType.getInteger(ctx, "height"), true))))))
                        .then(Commands.literal("rows")
                                .executes(ctx -> rows(ctx, DEFAULT_ROWS, false))
                                .then(Commands.literal("force")
                                        .executes(ctx -> rows(ctx, DEFAULT_ROWS, true)))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> rows(ctx, IntegerArgumentType.getInteger(ctx, "count"), false))
                                        .then(Commands.literal("force")
                                                .executes(ctx -> rows(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "count"), true)))))
                        .then(Commands.literal("mega")
                                .executes(ctx -> mega(ctx, DEFAULT_MEGA_COLUMNS, false))
                                .then(Commands.literal("force")
                                        .executes(ctx -> mega(ctx, DEFAULT_MEGA_COLUMNS, true)))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> mega(ctx, IntegerArgumentType.getInteger(ctx, "count"), false))
                                        .then(Commands.literal("force")
                                                .executes(ctx -> mega(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "count"), true)))))
                        .then(Commands.literal("platform")
                                .executes(ctx -> platform(ctx, null, false))
                                .then(Commands.literal("force")
                                        .executes(ctx -> platform(ctx, null, true)))
                                .then(Commands.argument("y", IntegerArgumentType.integer(MIN_PLATFORM_Y, MAX_PLATFORM_Y))
                                        .executes(ctx -> platform(ctx, IntegerArgumentType.getInteger(ctx, "y"), false))
                                        .then(Commands.literal("force")
                                                .executes(ctx -> platform(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "y"), true)))))
                        .then(Commands.literal("stacks")
                                .executes(ctx -> stacks(ctx, DEFAULT_STACK_LENGTH, 1, false))
                                .then(Commands.literal("force")
                                        .executes(ctx -> stacks(ctx, DEFAULT_STACK_LENGTH, 1, true)))
                                .then(Commands.argument("max_length",
                                                IntegerArgumentType.integer(1, MAX_STACK_LENGTH))
                                        .executes(ctx -> stacks(ctx,
                                                IntegerArgumentType.getInteger(ctx, "max_length"), 1, false))
                                        .then(Commands.literal("force")
                                                .executes(ctx -> stacks(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "max_length"), 1, true)))
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes(ctx -> stacks(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "max_length"),
                                                        IntegerArgumentType.getInteger(ctx, "page"), false))
                                                .then(Commands.literal("force")
                                                        .executes(ctx -> stacks(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "max_length"),
                                                                IntegerArgumentType.getInteger(ctx, "page"), true))))))
                        .then(Commands.literal("catalog")
                                .executes(SlabRigCommand::catalog))
                        .then(Commands.literal("hangs")
                                .then(Commands.literal("catalog")
                                        .executes(SlabRigCommand::hangsCatalog))
                                .then(Commands.literal("direct")
                                        .then(Commands.literal("status")
                                                .executes(SlabRigHangingDirectExecutor::status))
                                        .then(Commands.literal("resume")
                                                .executes(SlabRigHangingDirectExecutor::resume))
                                        .then(Commands.literal("clear")
                                                .executes(SlabRigHangingDirectExecutor::clear))
                                        .then(Commands.argument("route_index",
                                                        IntegerArgumentType.integer(6143, 6143))
                                                .then(Commands.literal("topology")
                                                        .then(Commands.argument("topology_index",
                                                                        IntegerArgumentType.integer(42, 42))
                                                                .then(Commands.literal("paintings")
                                                                        .then(Commands.argument("selector_page",
                                                                                        IntegerArgumentType.integer(1, 1))
                                                                                .executes(ctx ->
                                                                                        SlabRigHangingDirectExecutor.start(ctx, false))
                                                                                .then(Commands.literal("force")
                                                                                        .executes(ctx ->
                                                                                                SlabRigHangingDirectExecutor.start(ctx, true))))))))))
                        .then(Commands.literal("cases")
                                .executes(ctx -> cases(ctx, 1, false))
                                .then(Commands.literal("force")
                                        .executes(ctx -> cases(ctx, 1, true)))
                                .then(Commands.literal("resume")
                                        .executes(ctx -> resumeCases(ctx, false))
                                        .then(Commands.literal("force")
                                                .executes(ctx -> resumeCases(ctx, true))))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> cases(ctx,
                                                IntegerArgumentType.getInteger(ctx, "page"), false))
                                        .then(Commands.literal("force")
                                                .executes(ctx -> cases(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "page"), true)))))
                        .then(Commands.literal("status")
                                .executes(SlabRigCommand::status))
                        .then(Commands.literal("clear")
                                .executes(SlabRigCommand::clear))
        );
    }

    private static List<String> buildStackRecipes() {
        List<String> recipes = new ArrayList<>(62);
        for (int length = 1; length <= MAX_STACK_LENGTH; length++) {
            int words = 1 << length;
            for (int bits = 0; bits < words; bits++) {
                StringBuilder recipe = new StringBuilder(length);
                for (int shift = length - 1; shift >= 0; shift--) {
                    recipe.append((bits & (1 << shift)) == 0 ? 'S' : 'B');
                }
                recipes.add(recipe.toString());
            }
        }
        return List.copyOf(recipes);
    }

    /** Canonical immutable prefix through {@code maxLength}; visible for the literal contract test. */
    public static List<String> stackRecipes(int maxLength) {
        if (maxLength < 1 || maxLength > MAX_STACK_LENGTH) {
            throw new IllegalArgumentException("maxLength must be 1.." + MAX_STACK_LENGTH);
        }
        int total = (1 << (maxLength + 1)) - 2;
        return List.copyOf(STACK_RECIPES.subList(0, total));
    }

    /** One-based, dynamically validated page of the stable prefix catalog. */
    public static StackPage stackPage(int maxLength, int page) {
        List<String> recipes = stackRecipes(maxLength);
        int pageCount = (recipes.size() + STACK_PAGE_SIZE - 1) / STACK_PAGE_SIZE;
        if (page < 1 || page > pageCount) {
            throw new IllegalArgumentException("page must be 1.." + pageCount + " for max_length=" + maxLength);
        }
        int from = (page - 1) * STACK_PAGE_SIZE;
        int to = Math.min(from + STACK_PAGE_SIZE, recipes.size());
        return new StackPage(maxLength, page, pageCount, from, recipes.subList(from, to));
    }

    // ── geometry (shared with the smoke test) ─────────────────────────────────

    /** Horizontal direction the source faces (the rig builds this way). */
    public static Direction rigFacing(CommandSourceStack source) {
        return Direction.fromYRot(source.getRotation().y);
    }

    /**
     * Ground-level base cell of the rig: the player's feet {@code y - 1}, three blocks in front.
     * The bottom block of a rig sits here.
     */
    public static BlockPos rigBase(CommandSourceStack source) {
        BlockPos feet = BlockPos.containing(source.getPosition());
        return feet.below().relative(rigFacing(source), 3);
    }

    private static RigManifest planManifest(CommandSourceStack source, String preset, BlockPos base,
                                            Direction facing, Set<BlockPos> structureCells) {
        RigManifest manifest = new RigManifest(keyFor(source), preset, base, facing,
                0, 0, 0, -1, -1);
        for (BlockPos pos : structureCells) {
            manifest.claimStructure(pos);
        }
        declareTopHeadroom(manifest, structureCells);
        return manifest;
    }

    /** Exact headroom columns above actual planned tops; never a filled min/max box. */
    private static void declareTopHeadroom(RigManifest manifest, Set<BlockPos> structureCells) {
        for (BlockPos pos : structureCells) {
            if (!structureCells.contains(pos.above())) {
                for (int y = 1; y <= HEADROOM; y++) {
                    manifest.declareSubject(pos.above(y));
                }
            }
        }
    }

    /** The finite multi-cell envelope a single useOn may change (door upper / bed head included). */
    private static Set<BlockPos> effectEnvelope(BlockPos clicked, BlockPos target) {
        LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
        cells.add(clicked.immutable());
        cells.add(target.immutable());
        cells.add(target.above().immutable());
        cells.add(target.below().immutable());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            cells.add(target.relative(direction).immutable());
        }
        return cells;
    }

    private static void declareProxyEffect(RigManifest manifest, BlockPos clicked, BlockPos target) {
        for (BlockPos pos : effectEnvelope(clicked, target)) {
            if (!manifest.clearOwnedCells.contains(pos)) {
                manifest.declareSubject(pos);
            } else {
                manifest.reserveSafety(pos);
            }
        }
    }

    // ── /slabrig (usage) ──────────────────────────────────────────────────────

    private static int usage(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal(
                "[slabrig] usage: /slabrig <tower [force]|tower <n> [height] [force]|rows [n] [force]"
                        + "|mega [n] [force]|platform [y] [force]|stacks [max_length] [page] [force]"
                        + "|catalog|hangs catalog|hangs direct <6143 topology 42 paintings 1 [force]"
                        + "|status|resume|clear>|cases [page|resume] [force]|status|clear>\n"
                        + "  tower [force]           — compound-visible -1.0 marked tower\n"
                        + "  tower <n> [h] [force]   — n alternating deep-stack towers (default n="
                        + DEFAULT_TOWER_COUNT + ", height=" + DEFAULT_TOWER_HEIGHT + ", cap " + MAX_TOWER_HEIGHT
                        + "); reads back dy per cell and flags disjoints\n"
                        + "  rows [n] [force]        — n columns (default " + DEFAULT_ROWS + ") of -0.5 and -1.0 supports\n"
                        + "  mega [n] [force]        — the everything-rig: n columns x 4 support variants with one\n"
                        + "                            kit item auto-placed on each (default " + DEFAULT_MEGA_COLUMNS + ", capped by kit size)\n"
                        + "  platform [y] [force]    — the mega rig on a floating platform at absolute Y (default your Y)\n"
                        + "  stacks [m] [p] [force]  — exact 4x4 page of all S/B words through length m (default 5)\n"
                        + "  catalog                 — write the exact runtime item/category/topology catalog\n"
                        + "  hangs catalog           — world-free exact hanging catalog + live painting registry export\n"
                        + "  hangs direct 6143 topology 42 paintings 1 [force]\n"
                        + "                          — durable TEST 19 SBSBS/painting page; exact lifecycle evidence\n"
                        + "  hangs direct <status|resume|clear> — inspect, continue, or exact-clear that page\n"
                        + "  cases [p|resume] [force]— 4 items x 4 topologies from the exhaustive BlockItem case space\n"
                        + "  status                  — show the exact tracked manifest and structural state\n"
                        + "  clear                   — remove exact owned cells only\n"
                        + "  (a tracked rig refuses replacement unless force exact-clears it first)"), false);
        return 1;
    }

    // ── /slabrig tower ────────────────────────────────────────────────────────

    private static int tower(CommandContext<CommandSourceStack> ctx, boolean force) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        Direction facing = rigFacing(source);
        BlockPos base = rigBase(source);

        if (belowWorldBottom(source, world, base)) {
            return 0;
        }

        Set<BlockPos> cells = new LinkedHashSet<>();
        collectTowerCells(base, facing, cells);
        RigManifest manifest = planManifest(source, "tower", base, facing, cells);
        if (!prepareManifest(source, world, manifest, force)) {
            return 0;
        }

        buildCompoundTower(world, base, facing, manifest);

        // F4 self-verify: the compound full block and the marked side slab must both read -1.0.
        BlockPos fb = base.above(4);
        BlockPos support = fb.relative(facing.getCounterClockWise());
        List<String> mismatches = new ArrayList<>();
        addIfMismatch(mismatches, world, fb, -1.0, "compound full block");
        addIfMismatch(mismatches, world, support, -1.0, "marked side slab");
        if (!mismatches.isEmpty()) {
            manifest.structuralNote = "self-verify:" + String.join(";", mismatches);
            source.sendFailure(warn("tower", base, facing, mismatches));
            return 0;
        }
        manifest.structurallyComplete = true;
        manifest.structuralNote = "verified";

        source.sendSuccess(() -> Component.literal(
                "[slabrig] tower built at " + base.toShortString() + " facing " + facing.getName()
                        + " — compound full block reads -1.0, marked side slab beside it."), false);
        return 1;
    }

    /**
     * The compound-visible tower — an exact mirror of
     * {@code AnchoredDepthReadbackTest.buildCompoundVisibleSupport}: ground stone, bottom slab, stone,
     * bottom slab, compound full block (anchored + compound-full-block anchor, reads -1.0), and a
     * marked side slab authored against it. Returns the compound full-block cell.
     */
    private static BlockPos buildCompoundTower(ServerLevel world, BlockPos base, Direction facing,
                                                RigManifest manifest) {
        setStone(world, base, manifest);
        bottomSlab(world, base.above(1), manifest);
        setStone(world, base.above(2), manifest);
        bottomSlab(world, base.above(3), manifest);

        BlockPos fb = base.above(4);
        setStone(world, fb, manifest);
        SlabAnchorAttachment.addAnchor(world, fb, world.getBlockState(fb));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(world, fb, world.getBlockState(fb));
        manifest.touchAttachment(fb);

        // Marked side slab beside the compound full block (same Y, one horizontal step). The qualifier
        // is direction-agnostic, so we place it on the player's left for a clean read.
        BlockPos support = fb.relative(facing.getCounterClockWise());
        bottomSlab(world, support, manifest);
        SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(world, support, world.getBlockState(support),
                fb, world.getBlockState(fb));
        manifest.touchAttachment(support);
        return fb;
    }

    /** The cells {@link #buildCompoundTower} touches, computed WITHOUT writing (footprint planning). */
    private static void collectTowerCells(BlockPos base, Direction facing, Set<BlockPos> cells) {
        cells.add(base);
        cells.add(base.above(1));
        cells.add(base.above(2));
        cells.add(base.above(3));
        BlockPos fb = base.above(4);
        cells.add(fb);
        cells.add(fb.relative(facing.getCounterClockWise()));
    }

    // ── /slabrig tower <n> [height] ──────────────────────────────────────────

    /**
     * The deep-stack rig: {@code n} side-by-side alternating towers, each starting on the same lowered
     * -0.5 base the other rigs use (base (ground/slab/stone/slab, seat at offset {@link #TOWER_SEAT_INDEX})) and then
     * {@code height} more cells stacked ABOVE that seat via REAL item {@code useOn} clicks on the
     * previous cell's top face — the same capture/freeze path a hand placement takes — so accumulated
     * depth past the historical floor is reproduced exactly instead of hand-authored as scenery. Every
     * tower cycles a different alternating recipe ({@link #TOWER_RECIPES}) so one command run covers the
     * whole family. After building, every cell in every tower is read back
     * ({@link SlabSupport#getYOffset}) and reported to chat; any adjacent pair whose read-back implies a
     * gap between the lower cell's top and the upper cell's bottom is flagged with both positions.
     */
    private static int towerRig(CommandContext<CommandSourceStack> ctx, int n, int height, boolean force) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        Player player = proxyPlayerOrRefuse(source, "tower <n>");
        if (player == null) {
            return 0;
        }

        Direction facing = rigFacing(source);
        Direction right = facing.getClockWise();
        BlockPos base = rigBase(source);
        if (belowWorldBottom(source, world, base)) {
            return 0;
        }
        if (aboveWorldTop(source, world, base, TOWER_SEAT_INDEX + height + HEADROOM)) {
            return 0;
        }

        BlockPos[] towerBases = new BlockPos[n];
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (int i = 0; i < n; i++) {
            towerBases[i] = base.relative(right, i * COLUMN_SPACING);
            collectTowerRigCells(towerBases[i], height, cells);
        }
        RigManifest manifest = planManifest(source, "tower-deep", base, facing, cells);
        for (BlockPos towerBase : towerBases) {
            BlockPos clicked = towerBase.above(TOWER_SEAT_INDEX);
            for (int h = 0; h < height; h++) {
                BlockPos target = clicked.above();
                declareProxyEffect(manifest, clicked, target);
                clicked = target;
            }
        }
        if (!prepareManifest(source, world, manifest, force)) {
            return 0;
        }

        int gapCount = 0;
        int overlapCount = 0;
        int[] builtCells = new int[n];
        ItemStack held = player.getMainHandItem().copy();
        try {
            for (int i = 0; i < n; i++) {
                String recipe = TOWER_RECIPES[i % TOWER_RECIPES.length];
                String label = TOWER_LABELS[i % TOWER_LABELS.length];
                List<BlockPos> stack = buildAlternatingTower(
                        world, player, towerBases[i], recipe, height, manifest);
                builtCells[i] = stack.size() - 1; // cells actually placed above the seat
                ReadbackTally tally = reportTowerReadback(source, i, label, height, world, stack);
                gapCount += tally.gaps();
                overlapCount += tally.overlaps();
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }
        // Honest summary: report what was ACTUALLY built (a tower can legitimately stall early — e.g.
        // an obstruction or a refused placement), never the requested figure dressed up as fact.
        StringBuilder perTower = new StringBuilder();
        boolean anyShort = false;
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                perTower.append('/');
            }
            perTower.append(builtCells[i]);
            anyShort |= builtCells[i] < height;
        }
        final int nFinal = n;
        final int heightFinal = height;
        final int gapsFinal = gapCount;
        final int overlapsFinal = overlapCount;
        final String builtSummary = perTower.toString();
        final boolean shortFinal = anyShort;
        manifest.structurallyComplete = !anyShort && gapCount == 0 && overlapCount == 0;
        manifest.structuralNote = manifest.structurallyComplete
                ? "verified"
                : "built=" + builtSummary + "/" + height + ",gaps=" + gapCount + ",overlaps=" + overlapCount;
        source.sendSuccess(() -> Component.literal(
                "[slabrig] tower rig built at " + base.toShortString() + " facing " + facing.getName()
                        + " — " + nFinal + " towers, cells built " + builtSummary + " of " + heightFinal
                        + " requested" + (shortFinal ? " (some towers stalled early)" : "")
                        + "; GAPs: " + gapsFinal + ", OVERLAPs: " + overlapsFinal), false);
        return 1;
    }

    /** Per-tower read-back result: seam-open pairs (gaps) and clip pairs (overlaps) counted separately. */
    private record ReadbackTally(int gaps, int overlaps) {
    }

    /**
     * Builds one alternating tower: the lowered base (cells 0..{@link #TOWER_SEAT_INDEX}) (scenery, same path
     * {@link #buildCompoundTower} and Row A use), then {@code height} cells above it, each placed via a
     * REAL item {@code useOn} click on the previous cell's top face per {@code recipe} (cycled, {@code S}
     * = slab, {@code B} = block). Two different slab items alternate for consecutive {@code S} tokens so
     * a same-type slab-on-slab click never converts into a double slab in place of advancing to a new
     * cell. Returns every cell actually authored, seat (the lowered base's top slab) first. A placement
     * that is refused outright stops the tower early (a real build failure, distinct from a read-back
     * disjoint) rather than guessing at a cell that was never authored.
     */
    private static List<BlockPos> buildAlternatingTower(ServerLevel world, Player player, BlockPos towerBase,
                                                         String recipe, int height, RigManifest manifest) {
        setStone(world, towerBase, manifest);
        bottomSlab(world, towerBase.above(1), manifest);
        setStone(world, towerBase.above(2), manifest);
        bottomSlab(world, towerBase.above(3), manifest);
        BlockPos seatTop = towerBase.above(3);

        List<BlockPos> stack = new ArrayList<>();
        stack.add(seatTop);

        BlockPos cursor = seatTop;
        // The lowered base's seat is ALWAYS a Blocks.STONE_SLAB (bottomSlab() above); start the
        // alternation on the OTHER flavor so a recipe that opens with 'S' never clicks a same-type slab
        // top onto a same-type slab top (which would consolidate into a double instead of advancing).
        boolean slabFlavorToggle = true;
        for (int h = 0; h < height; h++) {
            char token = recipe.charAt(h % recipe.length());
            Item item;
            if (token == 'S') {
                item = slabFlavorToggle ? Items.SMOOTH_STONE_SLAB : Items.STONE_SLAB;
                slabFlavorToggle = !slabFlavorToggle;
            } else {
                item = Items.STONE;
            }
            BlockPos target = cursor.above();
            boolean targetWasAir = world.getBlockState(target).isAir();
            BlockState cursorBefore = world.getBlockState(cursor);
            placeVia(world, player, item, cursor, Direction.UP, target, manifest);
            boolean landed = targetWasAir && !world.getBlockState(target).isAir();
            if (landed) {
                cursor = target;
                stack.add(cursor);
            } else if (!world.getBlockState(cursor).equals(cursorBefore)) {
                // Landed back into the same cell (e.g. an unexpected same-type slab consolidation into a
                // double) — keep going from here.
            } else {
                // The item refused outright; stop authoring this tower rather than reporting a phantom cell.
                break;
            }
        }
        return stack;
    }

    /**
     * Reads back every cell in {@code stack} ({@link SlabSupport#getYOffset}, read-only) and reports the
     * whole tower as ONE compact chat line (per-cell type+dy in order from the seat up — never one line
     * per cell, which floods chat at full size), then walks adjacent pairs comparing the lower cell's
     * effective top (its Y + dy + its height fraction) against the upper cell's effective bottom (its Y +
     * dy). A signed mismatch means the two cells do not sit flush; a positive difference (upper bottom
     * above lower top) is a GAP (an air seam), a negative one is an OVERLAP (a clip) — physically
     * distinct failure modes, labelled separately. Anomaly lines are NEVER collapsed or capped: every
     * flagged pair gets its own full line with both coordinates and the signed seam size.
     */
    private static ReadbackTally reportTowerReadback(CommandSourceStack source, int towerIndex, String label,
                                                     int requestedHeight, ServerLevel world, List<BlockPos> stack) {
        double[] dys = new double[stack.size()];
        double[] fracs = new double[stack.size()];
        StringBuilder cellsLine = new StringBuilder();
        for (int i = 0; i < stack.size(); i++) {
            BlockPos pos = stack.get(i);
            BlockState state = world.getBlockState(pos);
            double dy = SlabSupport.getYOffset(world, pos, state);
            dys[i] = dy;
            fracs[i] = cellHeightFraction(state);
            if (i > 0) {
                cellsLine.append(", ");
            }
            cellsLine.append(cellType(state)).append(' ').append(dy);
        }
        final int built = stack.size() - 1;
        final String cellsLineFinal = cellsLine.toString();
        source.sendSuccess(() -> Component.literal(
                "[slabrig] tower" + towerIndex + "(" + label + ") @" + stack.get(0).toShortString()
                        + " cells " + built + "/" + requestedHeight + " (seat first): " + cellsLineFinal), false);

        int gaps = 0;
        int overlaps = 0;
        for (int i = 1; i < stack.size(); i++) {
            BlockPos lower = stack.get(i - 1);
            BlockPos upper = stack.get(i);
            double lowerTop = lower.getY() + dys[i - 1] + fracs[i - 1];
            double upperBottom = upper.getY() + dys[i];
            double seam = upperBottom - lowerTop; // signed: + = air seam (GAP), - = clip (OVERLAP)
            if (Math.abs(seam) > EPS) {
                String kind;
                if (seam > 0) {
                    gaps++;
                    kind = "GAP (air seam)";
                } else {
                    overlaps++;
                    kind = "OVERLAP (clip)";
                }
                source.sendFailure(Component.literal(
                        "[slabrig] " + kind + " tower" + towerIndex + "(" + label + ") between "
                                + lower.toShortString() + " and " + upper.toShortString()
                                + ": lower top=" + lowerTop + " upper bottom=" + upperBottom
                                + " seam=" + seam));
            }
        }
        return new ReadbackTally(gaps, overlaps);
    }

    /** {@code true} if {@code state} is a (non-double) slab — half height for the read-back gap check. */
    private static String cellType(BlockState state) {
        boolean slab = state.getBlock() instanceof SlabBlock
                && state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE;
        return slab ? "slab" : "block";
    }

    private static double cellHeightFraction(BlockState state) {
        return "slab".equals(cellType(state)) ? 0.5 : 1.0;
    }

    /** The cells {@link #buildAlternatingTower} touches (conservatively — the base + every possible cell). */
    private static void collectTowerRigCells(BlockPos towerBase, int height, Set<BlockPos> cells) {
        for (int i = 0; i <= TOWER_SEAT_INDEX + height; i++) {
            cells.add(towerBase.above(i));
        }
    }

    // ── /slabrig rows [n] ─────────────────────────────────────────────────────

    private static int rows(CommandContext<CommandSourceStack> ctx, int count, boolean force) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        Direction facing = rigFacing(source);
        Direction right = facing.getClockWise();
        BlockPos base = rigBase(source);

        if (belowWorldBottom(source, world, base)) {
            return 0;
        }

        Set<BlockPos> cells = new LinkedHashSet<>();
        collectRowsCells(base, facing, count, cells);
        RigManifest manifest = planManifest(source, "rows", base, facing, cells);
        if (!prepareManifest(source, world, manifest, force)) {
            return 0;
        }

        // Row A — lowered -0.5 supports: the deep lowered-slab stack (ground stone, slab, stone, slab),
        // an exact mirror of AnchoredDepthReadbackTest.buildLoweredSlabStack. The TOP slab reads -0.5 and
        // is where the tester places subjects. (A stone-on-slab is NOT lowered — an opaque full cube is
        // excluded as a subject by the world-hole guard, so it stays flush; the slab is the real seat.)
        BlockPos rowAStart = base;
        signAt(world, rowAStart.relative(right.getOpposite(), COLUMN_SPACING),
                "Row A", "dy -0.5", "place on top", manifest);
        for (int i = 0; i < count; i++) {
            BlockPos cell = rowAStart.relative(right, i * COLUMN_SPACING);
            setStone(world, cell, manifest);            // ground
            bottomSlab(world, cell.above(1), manifest); // slab (0)
            setStone(world, cell.above(2), manifest);   // stone (carrier)
            bottomSlab(world, cell.above(3), manifest); // TOP slab -> reads -0.5 (the seat)
        }

        // Row B — genuine -1.0 supports: a compound tower per column, marked side slab authored.
        // Offset 4 cells deeper so the two rows are clearly parallel and separated.
        BlockPos rowBStart = base.relative(facing, 4);
        signAt(world, rowBStart.relative(right.getOpposite(), COLUMN_SPACING),
                "Row B", "dy -1.0", "place on FB/slab", manifest);
        for (int i = 0; i < count; i++) {
            BlockPos cell = rowBStart.relative(right, i * COLUMN_SPACING);
            buildCompoundTower(world, cell, facing, manifest);
        }

        // F4 self-verify: one sample support per row (Row A top slab -0.5, Row B compound FB -1.0).
        BlockPos rowASample = rowAStart.above(3);
        BlockPos rowBSample = rowBStart.above(4);
        List<String> mismatches = new ArrayList<>();
        addIfMismatch(mismatches, world, rowASample, -0.5, "Row A support");
        addIfMismatch(mismatches, world, rowBSample, -1.0, "Row B support");
        if (!mismatches.isEmpty()) {
            manifest.structuralNote = "self-verify:" + String.join(";", mismatches);
            source.sendFailure(warn("rows", base, facing, mismatches));
            return 0;
        }
        manifest.structurallyComplete = true;
        manifest.structuralNote = "verified";

        final int placed = count;
        source.sendSuccess(() -> Component.literal(
                "[slabrig] rows built at " + base.toShortString() + " facing " + facing.getName()
                        + " — " + placed + " columns: Row A -0.5, Row B -1.0."), false);
        return 1;
    }

    /** The cells {@link #rows} touches, computed WITHOUT writing (footprint planning). */
    private static void collectRowsCells(BlockPos base, Direction facing, int count, Set<BlockPos> cells) {
        Direction right = facing.getClockWise();

        BlockPos rowAStart = base;
        collectSignCells(rowAStart.relative(right.getOpposite(), COLUMN_SPACING), cells);
        for (int i = 0; i < count; i++) {
            BlockPos cell = rowAStart.relative(right, i * COLUMN_SPACING);
            cells.add(cell);
            cells.add(cell.above(1));
            cells.add(cell.above(2));
            cells.add(cell.above(3));
        }

        BlockPos rowBStart = base.relative(facing, 4);
        collectSignCells(rowBStart.relative(right.getOpposite(), COLUMN_SPACING), cells);
        for (int i = 0; i < count; i++) {
            collectTowerCells(rowBStart.relative(right, i * COLUMN_SPACING), facing, cells);
        }
    }

    private static void collectSignCells(BlockPos ground, Set<BlockPos> cells) {
        cells.add(ground);
        cells.add(ground.above());
    }

    /** Places a labelled standing sign one block above {@code ground}, on a stone pedestal. */
    private static void signAt(ServerLevel world, BlockPos ground, String l0, String l1, String l2,
                               RigManifest manifest) {
        setStone(world, ground, manifest);
        BlockPos signPos = ground.above();
        BlockState sign = Blocks.OAK_SIGN.defaultBlockState();
        setPlannedBlock(world, signPos, sign, manifest);
        BlockEntity be = world.getBlockEntity(signPos);
        if (be instanceof SignBlockEntity signEntity) {
            signEntity.updateText(text -> text
                    .setMessage(0, Component.literal(l0))
                    .setMessage(1, Component.literal(l1))
                    .setMessage(2, Component.literal(l2)), true);
        }
    }

    // ── /slabrig mega [n] ─────────────────────────────────────────────────────

    /**
     * THE mega rig: {@code n} columns wide, each column carrying the FOUR support variants (one per
     * row, along the facing axis): (0) a bare bottom slab, (1) a bottom slab + full block, (2) the
     * compound column (ground/slab/stone/slab/full block) with its marked side slab, and (3) the
     * overhang-and-ceiling variant (a lowered support with an air cell to the side and a full block
     * overhead for hanging tests). Every column {@code i} is seated with one kit item ({@code i}-th
     * entry of {@link SlabTestKit#placeableItems()}) auto-placed on each variant's surface via the
     * source player's REAL {@code useOn} — so a whole test board of category representatives on every
     * geometry appears in one command. Items that refuse to place are skipped and counted.
     *
     * <p>Scenery is authored with the same {@code setBlock} + anchor-authoring path as {@code tower} /
     * {@code rows} (it cannot touch height computation); only the SUBJECTS go through the real placement
     * path, exactly like the gametest rigs. The player's held item is saved and restored around the
     * auto-placement sweep.
     */
    private static int mega(CommandContext<CommandSourceStack> ctx, int columns, boolean force) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        Player player = proxyPlayerOrRefuse(source, "mega");
        if (player == null) {
            return 0;
        }

        BlockPos base = rigBase(source);
        if (belowWorldBottom(source, world, base)) {
            return 0;
        }
        if (aboveWorldTop(source, world, base, MEGA_TOP_OFFSET + HEADROOM)) {
            return 0;
        }

        return buildMegaRig(source, world, player, base, columns, force, "mega");
    }

    // ── /slabrig platform [y] ─────────────────────────────────────────────────

    /**
     * Rebuilds the standard mega rig on a floating platform at an absolute altitude, so every mega
     * variant (bare slab, slab+block, compound column, overhang-and-ceiling) can be exercised away from
     * ground level. Uses the same {@code base} horizontal offset {@link #rigBase} does, but Y comes from
     * {@code yArg} (or the player's current Y when omitted) instead of {@code feet - 1}. Each column's
     * ground cell is authored as scenery ({@code setBlock}), so it needs no terrain beneath it — the mega
     * rig has never depended on terrain, which is exactly what makes it usable at altitude.
     */
    private static int platform(CommandContext<CommandSourceStack> ctx, Integer yArg, boolean force) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        Player player = proxyPlayerOrRefuse(source, "platform");
        if (player == null) {
            return 0;
        }

        Direction facing = rigFacing(source);
        BlockPos feet = BlockPos.containing(source.getPosition());
        int y = yArg != null ? yArg : feet.getY();
        BlockPos horiz = feet.relative(facing, 3);
        BlockPos base = new BlockPos(horiz.getX(), y, horiz.getZ());

        if (belowWorldBottom(source, world, base)) {
            return 0;
        }
        if (aboveWorldTop(source, world, base, MEGA_TOP_OFFSET + HEADROOM)) {
            return 0;
        }

        return buildMegaRig(source, world, player, base, DEFAULT_MEGA_COLUMNS, force, "platform");
    }

    /**
     * Shared builder behind {@link #mega} and {@link #platform}: {@code n} columns wide (capped by the
     * kit size), four support variants per column, one kit item auto-placed on each via the real
     * placement path, self-verified against the sign claims. {@code label} names the rig in chat
     * ({@code "mega"} or {@code "platform"}).
     */
    private static int buildMegaRig(CommandSourceStack source, ServerLevel world, Player player, BlockPos base,
                                    int columns, boolean force, String label) {
        Direction facing = rigFacing(source);
        Direction right = facing.getClockWise();

        List<Item> kit = SlabTestKit.placeableItems();
        int n = Math.min(columns, kit.size());
        if (n <= 0) {
            source.sendFailure(Component.literal("[slabrig] " + label + " has no kit items to place."));
            return 0;
        }

        Set<BlockPos> cells = new LinkedHashSet<>();
        collectMegaCells(base, facing, right, n, cells);
        RigManifest manifest = planManifest(source, label, base, facing, cells);
        for (int i = 0; i < n; i++) {
            for (int v = 0; v < MEGA_ROW_COUNT; v++) {
                BlockPos seat = plannedMegaSeat(base, facing, right, v, i);
                BlockPos clicked = v == MEGA_ROW_HANGING ? seat.above(2) : seat;
                BlockPos target = seat.above();
                declareProxyEffect(manifest, clicked, target);
                if (v == MEGA_ROW_HANGING) {
                    // Explicit manual overhang slot; it is not an incidental cell from derived bounds.
                    manifest.declareSubject(seat.relative(facing));
                }
            }
        }
        if (!prepareManifest(source, world, manifest, force)) {
            return 0;
        }

        // Build every variant column's scenery, remembering each column's four seat surfaces.
        BlockPos[][] seats = new BlockPos[n][MEGA_ROW_COUNT];
        for (int i = 0; i < n; i++) {
            for (int v = 0; v < MEGA_ROW_COUNT; v++) {
                seats[i][v] = buildMegaColumn(world, base, facing, right, v, i, manifest);
            }
        }
        // One labelled sign per row variant (near end of the row, on the -right side).
        for (int v = 0; v < MEGA_ROW_COUNT; v++) {
            BlockPos signGround = base.relative(facing, v).relative(right.getOpposite(), COLUMN_SPACING);
            signAt(world, signGround, "Row " + v, MEGA_ROW_NAME[v], "dy " + MEGA_ROW_DY[v], manifest);
        }

        // Auto-place one kit item on each seat via the REAL placement path. Save/restore the hand.
        ItemStack held = player.getMainHandItem().copy();
        int placed = 0;
        int refused = 0;
        Set<String> refusedIds = new LinkedHashSet<>();
        try {
            for (int i = 0; i < n; i++) {
                Item item = kit.get(i);
                for (int v = 0; v < MEGA_ROW_COUNT; v++) {
                    BlockPos seat = seats[i][v];
                    // Where the item lands and which face is clicked. Rows 0-2 seat on the support's
                    // up-face. Row 3 (overhang_and_ceiling) is a HANGING attempt: click the ceiling
                    // block's DOWN face so the item lands in the hang gap through the real hanging path.
                    // An item that refuses to hang leaves that gap empty and free for Maintainer to use.
                    BlockPos clicked;
                    Direction face;
                    BlockPos target;
                    if (v == MEGA_ROW_HANGING) {
                        clicked = seat.above(2); // the ceiling block (g.above(4))
                        face = Direction.DOWN;
                        target = seat.above(1);  // the hang gap (g.above(3))
                    } else {
                        clicked = seat;
                        face = Direction.UP;
                        target = seat.above();
                    }
                    BlockState clickedBefore = world.getBlockState(clicked);
                    boolean targetWasAir = world.getBlockState(target).isAir();
                    placeVia(world, player, item, clicked, face, target, manifest);
                    boolean targetFilled = targetWasAir && !world.getBlockState(target).isAir();
                    // An in-cell change (e.g. the clicked slab became a double slab) also counts as placed.
                    boolean clickedChanged = !world.getBlockState(clicked).equals(clickedBefore);
                    if (targetFilled || clickedChanged) {
                        placed++;
                    } else {
                        refused++;
                        refusedIds.add(BuiltInRegistries.ITEM.getKey(item).getPath());
                    }
                }
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }

        // Self-verify one sample seat per row against its sign's dy claim.
        List<String> mismatches = new ArrayList<>();
        for (int v = 0; v < MEGA_ROW_COUNT; v++) {
            addIfMismatch(mismatches, world, seats[0][v], MEGA_ROW_DY[v], "Row " + v + " (" + MEGA_ROW_NAME[v] + ")");
        }
        if (!mismatches.isEmpty()) {
            manifest.structuralNote = "self-verify:" + String.join(";", mismatches);
            source.sendFailure(warn(label, base, facing, mismatches));
            return 0;
        }
        manifest.structurallyComplete = true;
        manifest.structuralNote = "verified;placed=" + placed + ",refused=" + refused;

        final int builtColumns = n;
        final int placedFinal = placed;
        final int refusedFinal = refused;
        final String refusedList = summariseIds(refusedIds, MAX_REFUSED_LISTED);
        source.sendSuccess(() -> Component.literal(
                "[slabrig] " + label + " built at " + base.toShortString() + " facing " + facing.getName()
                        + " — " + builtColumns + " columns x " + MEGA_ROW_COUNT + " variants; placed "
                        + placedFinal + ", refused " + refusedFinal + " (of "
                        + (builtColumns * MEGA_ROW_COUNT) + " attempts)"
                        + (refusedList.isEmpty() ? "." : " (refused: " + refusedList + ").")), false);
        return 1;
    }

    /**
     * Builds one variant column's scenery and returns the SEAT surface a subject is placed on. {@code v}
     * selects the variant, {@code i} the column index (spaced along {@code right}); the column's ground
     * cell sits at {@code base + facing*v + right*(i*spacing)}.
     */
    private static BlockPos buildMegaColumn(ServerLevel world, BlockPos base, Direction facing,
                                            Direction right, int v, int i, RigManifest manifest) {
        BlockPos g = base.relative(facing, v).relative(right, i * COLUMN_SPACING);
        switch (v) {
            case 0 -> {
                // Bare bottom slab on the ground (a flush slab, seat reads 0.0).
                setStone(world, g, manifest);
                bottomSlab(world, g.above(1), manifest);
                return g.above(1);
            }
            case 1 -> {
                // Bottom slab carrying a full block (the lowered full block, seat reads -0.5).
                setStone(world, g, manifest);
                bottomSlab(world, g.above(1), manifest);
                setStone(world, g.above(2), manifest);
                return g.above(2);
            }
            case 2 -> {
                // The compound column + marked side slab (both read -1.0); the side slab is the seat.
                BlockPos fb = buildCompoundTower(world, g, facing, manifest);
                return fb.relative(facing.getCounterClockWise());
            }
            default -> {
                // Overhang-and-ceiling: a lowered support (seat -0.5) with a full block overhead (a
                // ceiling to hang from) and an open air cell to the side for manual overhang tests.
                setStone(world, g, manifest);
                bottomSlab(world, g.above(1), manifest);
                setStone(world, g.above(2), manifest);
                setStone(world, g.above(4), manifest); // ceiling block; g.above(3) stays air (the hang gap)
                return g.above(2);
            }
        }
    }

    private static BlockPos plannedMegaSeat(BlockPos base, Direction facing, Direction right, int v, int i) {
        BlockPos g = base.relative(facing, v).relative(right, i * COLUMN_SPACING);
        return switch (v) {
            case 0 -> g.above(1);
            case 1 -> g.above(2);
            case 2 -> g.above(4).relative(facing.getCounterClockWise());
            default -> g.above(2);
        };
    }

    /** The cells {@link #buildMegaColumn} + {@link #mega}'s signs touch, for footprint planning. */
    private static void collectMegaCells(BlockPos base, Direction facing, Direction right, int n,
                                         Set<BlockPos> cells) {
        for (int i = 0; i < n; i++) {
            for (int v = 0; v < MEGA_ROW_COUNT; v++) {
                BlockPos g = base.relative(facing, v).relative(right, i * COLUMN_SPACING);
                switch (v) {
                    case 0 -> {
                        cells.add(g);
                        cells.add(g.above(1));
                    }
                    case 1 -> {
                        cells.add(g);
                        cells.add(g.above(1));
                        cells.add(g.above(2));
                    }
                    case 2 -> collectTowerCells(g, facing, cells);
                    default -> {
                        cells.add(g);
                        cells.add(g.above(1));
                        cells.add(g.above(2));
                        cells.add(g.above(4)); // ceiling block
                    }
                }
            }
        }
        for (int v = 0; v < MEGA_ROW_COUNT; v++) {
            collectSignCells(base.relative(facing, v).relative(right.getOpposite(), COLUMN_SPACING), cells);
        }
    }

    /**
     * Places {@code item} by clicking {@code clicked}'s {@code face} via the real player {@code useOn}
     * path (the same capture/freeze/marker machinery a hand placement runs). Sets the item in the
     * player's hand for the call; the caller restores the original hand afterward. Never throws — if the
     * item refuses, one bad item never aborts the mega build; the caller decides placed/refused by
     * inspecting the world.
     */
    private static Set<BlockPos> placeVia(ServerLevel world, Player player, Item item, BlockPos clicked,
                                          Direction face, BlockPos target, RigManifest manifest) {
        Set<BlockPos> envelope = effectEnvelope(clicked, target);
        LinkedHashMap<BlockPos, BlockState> before = new LinkedHashMap<>();
        for (BlockPos pos : envelope) {
            before.put(pos, world.getBlockState(pos));
        }
        try {
            ItemStack stack = new ItemStack(item);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            Vec3 hit = Vec3.atCenterOf(clicked).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            LiveCursorIntentRecorder.withActionOrigin(
                    LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                    () -> stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(hit, face, clicked, false))));
        } catch (RuntimeException e) {
            // swallow: a refusing item is detected by the caller as an unchanged world.
        }
        LinkedHashSet<BlockPos> changed = new LinkedHashSet<>();
        for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
            BlockState after = world.getBlockState(entry.getKey());
            if (!after.equals(entry.getValue())) {
                changed.add(entry.getKey());
                manifest.recordAuthored(entry.getKey(), entry.getValue(), after);
                manifest.touchAttachment(entry.getKey());
            }
        }
        return changed;
    }

    /** A comma list of up to {@code cap} ids, with a "+K more" suffix when there are more. */
    private static String summariseIds(Set<String> ids, int cap) {
        if (ids.isEmpty()) {
            return "";
        }
        List<String> shown = new ArrayList<>();
        int extra = 0;
        for (String id : ids) {
            if (shown.size() < cap) {
                shown.add(id);
            } else {
                extra++;
            }
        }
        String joined = String.join(", ", shown);
        return extra > 0 ? joined + ", +" + extra + " more" : joined;
    }

    // ── /slabrig stacks [max_length] [page] ─────────────────────────────────

    private static int stacks(CommandContext<CommandSourceStack> ctx, int maxLength, int page,
                              boolean force) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        StackPage stackPage;
        try {
            stackPage = stackPage(maxLength, page);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("[slabrig] stacks refused: " + e.getMessage()
                    + "; no cells or manifest changed."));
            return 0;
        }
        Player player = proxyPlayerOrRefuse(source, "stacks");
        if (player == null) {
            return 0;
        }

        Direction facing = rigFacing(source);
        Direction right = facing.getClockWise();
        BlockPos base = rigBase(source);
        if (belowWorldBottom(source, world, base)) {
            return 0;
        }
        if (aboveWorldTop(source, world, base,
                TOWER_SEAT_INDEX + maxLength + HEADROOM)) {
            return 0;
        }

        Set<BlockPos> structure = new LinkedHashSet<>();
        List<StackEntry> entries = new ArrayList<>();
        for (int local = 0; local < stackPage.recipes().size(); local++) {
            int row = local / STACK_GRID_SIDE;
            int column = local % STACK_GRID_SIDE;
            BlockPos seedBase = base.relative(right, column * COLUMN_SPACING)
                    .relative(facing, row * COLUMN_SPACING);
            String recipe = stackPage.recipes().get(local);
            for (int y = 0; y <= TOWER_SEAT_INDEX; y++) {
                structure.add(seedBase.above(y));
            }
            List<BlockPos> recipeCells = new ArrayList<>(recipe.length());
            for (int index = 0; index < recipe.length(); index++) {
                BlockPos pos = seedBase.above(TOWER_SEAT_INDEX + 1 + index);
                structure.add(pos);
                recipeCells.add(pos);
            }
            entries.add(new StackEntry(stackPage.firstCatalogIndex() + local, recipe,
                    seedBase.above(TOWER_SEAT_INDEX), recipeCells));
        }

        RigManifest manifest = new RigManifest(keyFor(source), "stacks", base, facing,
                maxLength, page, stackPage.pageCount(), stackPage.firstCatalogIndex(),
                stackPage.firstCatalogIndex() + stackPage.recipes().size() - 1);
        for (BlockPos pos : structure) {
            manifest.claimStructure(pos);
        }
        declareTopHeadroom(manifest, structure);
        manifest.stacks.addAll(entries);
        for (StackEntry entry : entries) {
            BlockPos clicked = entry.seed();
            for (BlockPos target : entry.recipeCells()) {
                declareProxyEffect(manifest, clicked, target);
                clicked = target;
            }
        }
        // Explicit scan-only safety gaps between the four grid columns. They belong to the rig's
        // reserved geometry but never to its clear/preclean ownership.
        for (int row = 0; row < STACK_GRID_SIDE; row++) {
            for (int column = 0; column < STACK_GRID_SIDE - 1; column++) {
                manifest.reserveSafety(base.relative(facing, row * COLUMN_SPACING)
                        .relative(right, column * COLUMN_SPACING + 1));
            }
        }
        if (!prepareManifest(source, world, manifest, force)) {
            return 0;
        }

        ItemStack held = player.getMainHandItem().copy();
        int complete = 0;
        int gaps = 0;
        int overlaps = 0;
        try {
            for (int local = 0; local < entries.size(); local++) {
                StackEntry entry = entries.get(local);
                BlockPos seedBase = entry.seed().below(TOWER_SEAT_INDEX);
                setStone(world, seedBase, manifest);
                bottomSlab(world, seedBase.above(1), manifest);
                setStone(world, seedBase.above(2), manifest);
                bottomSlab(world, entry.seed(), manifest);

                List<BlockPos> stack = new ArrayList<>();
                stack.add(entry.seed());
                BlockPos cursor = entry.seed();
                boolean smoothNext = true;
                boolean entryComplete = true;
                String entryProblem = "none";
                for (int index = 0; index < entry.recipe().length(); index++) {
                    char token = entry.recipe().charAt(index);
                    Item item = token == 'S'
                            ? (smoothNext ? Items.SMOOTH_STONE_SLAB : Items.STONE_SLAB)
                            : Items.STONE;
                    if (token == 'S') {
                        smoothNext = !smoothNext;
                    }
                    BlockPos target = entry.recipeCells().get(index);
                    BlockState cursorBefore = world.getBlockState(cursor);
                    placeVia(world, player, item, cursor, Direction.UP, target, manifest);
                    BlockState actual = world.getBlockState(target);
                    boolean correctType = token == 'S'
                            ? actual.getBlock() instanceof SlabBlock
                            && actual.getValue(SlabBlock.TYPE) != SlabType.DOUBLE
                            : actual.is(Blocks.STONE);
                    if (actual.isAir()) {
                        entryComplete = false;
                        entryProblem = "refused@" + target.toShortString();
                        break;
                    }
                    if (!correctType) {
                        entryComplete = false;
                        entryProblem = "wrong-type@" + target.toShortString() + "=" + actual;
                        break;
                    }
                    if (!world.getBlockState(cursor).equals(cursorBefore)) {
                        entryComplete = false;
                        entryProblem = "wrong-cell/consolidation@" + cursor.toShortString();
                        break;
                    }
                    stack.add(target);
                    cursor = target;
                }
                ReadbackTally tally = reportTowerReadback(source, entry.catalogIndex(),
                        entry.recipe(), entry.recipe().length(), world, stack);
                gaps += tally.gaps();
                overlaps += tally.overlaps();
                if (entryComplete && stack.size() == entry.recipe().length() + 1
                        && tally.gaps() == 0 && tally.overlaps() == 0) {
                    complete++;
                } else if (entryComplete) {
                    entryProblem = "seams:gaps=" + tally.gaps() + ",overlaps=" + tally.overlaps();
                }
                final boolean entryCompleteFinal = entryComplete
                        && stack.size() == entry.recipe().length() + 1
                        && tally.gaps() == 0 && tally.overlaps() == 0;
                final String entryProblemFinal = entryProblem;
                final int built = stack.size() - 1;
                final List<BlockPos> actualRecipeCells = List.copyOf(stack.subList(1, stack.size()));
                source.sendSuccess(() -> Component.literal(
                        "[slabrig] stack#" + (entry.catalogIndex() + 1)
                                + " recipe=" + entry.recipe()
                                + " seed=" + entry.seed().toShortString()
                                + " plannedRecipeCells=" + entry.recipeCells()
                                + " actualRecipeCells=" + actualRecipeCells
                                + " built=" + built + "/" + entry.recipe().length()
                                + " structural=" + (entryCompleteFinal ? "complete" : "incomplete")
                                + (entryCompleteFinal ? "" : " problem=" + entryProblemFinal)), false);
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }

        manifest.structurallyComplete = complete == entries.size() && gaps == 0 && overlaps == 0;
        manifest.structuralNote = "recipes=" + complete + "/" + entries.size()
                + ",gaps=" + gaps + ",overlaps=" + overlaps;
        final int completeFinal = complete;
        final int entriesFinal = entries.size();
        final int gapsFinal = gaps;
        final int overlapsFinal = overlaps;
        source.sendSuccess(() -> Component.literal(
                "[slabrig] stacks page " + page + "/" + stackPage.pageCount()
                        + " max_length=" + maxLength
                        + " catalog=" + (stackPage.firstCatalogIndex() + 1) + "-"
                        + (stackPage.firstCatalogIndex() + entriesFinal) + "/" + stackRecipes(maxLength).size()
                        + " grid=4x4 row-major spacing=2 seed=standard_lowered_slab"
                        + " structural=" + completeFinal + "/" + entriesFinal
                        + " GAPs=" + gapsFinal + " OVERLAPs=" + overlapsFinal
                        + " provenance=" + LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY.wireName()), false);
        return manifest.structurallyComplete ? 1 : 0;
    }

    // ── /slabrig clear ────────────────────────────────────────────────────────

    // ── /slabrig catalog + cases [page|resume] ───────────────────────────────

    private static int catalog(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        SlabRigCaseCatalog.Snapshot snapshot = SlabRigCaseCatalog.snapshot();
        try {
            SlabRigCaseArtifacts.WrittenArtifact artifact = SlabRigCaseArtifacts.writeCatalog(
                    SlabRigCaseArtifacts.defaultRoot(), snapshot);
            source.sendSuccess(() -> Component.literal(
                    "[slabrig] catalog runtimeItems="
                            + (snapshot.items().size() + snapshot.excludedItems().size())
                            + " BlockItems=" + snapshot.items().size()
                            + " explicitNonBlockItems=" + snapshot.excludedItems().size()
                            + " topologies=" + snapshot.topologies().size()
                            + " totalCases=" + snapshot.totalCases()
                            + " pages=" + snapshot.pageCount()
                            + " hash=" + snapshot.catalogHash()
                            + " scope=runtime_BlockItem_x_FLOOR_UP_topology; hanging/nonblock routes deferred"
                            + " artifact=" + artifact.path()), false);
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.literal("[slabrig] catalog write failed before world mutation: "
                    + e.getMessage()));
            return 0;
        }
    }

    /** RIG-3B1: content-addressed catalog/registry evidence only; no player or world write path. */
    private static int hangsCatalog(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            SlabRigHangingCatalog.Snapshot catalog = SlabRigHangingCatalog.snapshot();
            SlabRigHangingArtifacts.RuntimeSnapshot runtime = SlabRigHangingArtifacts.snapshot(
                    catalog, source.getServer().registryAccess());
            SlabRigHangingArtifacts.WrittenArtifact artifact =
                    SlabRigHangingArtifacts.write(runtime);
            source.sendSuccess(() -> Component.literal(
                    "[slabrig] hangs catalog runtimeItems=" + catalog.runtimeItemCount()
                            + " subjects=" + catalog.items().size()
                            + " routes=" + catalog.routes().size()
                            + " cases=" + catalog.totalCases()
                            + " catalogHash=" + catalog.catalogHash()
                            + " minecraft=" + runtime.minecraftVersion()
                            + " runtimeContentSha256=" + runtime.runtimeContentSha256()
                            + " paintingVariants=" + runtime.paintingVariantCount()
                            + " randomPlaceable=" + runtime.randomPlaceableCount()
                            + " paintingRegistry=" + runtime.paintingRegistryId()
                            + " placeableTag=" + runtime.placeableTagId()
                            + " paintingComponent=" + runtime.paintingComponentId()
                            + " paintingHash=" + runtime.paintingRegistryHash()
                            + " executionIdentity=" + runtime.executionIdentity()
                            + " artifactSha256=" + artifact.fileSha256()
                            + " playerProof=" + runtime.playerProof()
                            + " worldMutation=" + runtime.worldMutation()
                            + " artifact=" + artifact.path()), false);
            return 1;
        } catch (IOException | RuntimeException failure) {
            source.sendFailure(Component.literal(
                    "[slabrig] hangs catalog failed before world mutation: " + failure.getMessage()));
            return 0;
        }
    }

    private static int resumeCases(CommandContext<CommandSourceStack> ctx, boolean force) {
        CommandSourceStack source = ctx.getSource();
        boolean frozenDyEnabled = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        Player player = proxyPlayerOrRefuse(source, "cases resume");
        if (player == null) {
            return 0;
        }
        if (!exactCaseIdentityAvailable(source, "cases resume")) {
            return 0;
        }
        SlabRigCaseCatalog.Snapshot snapshot = SlabRigCaseCatalog.snapshot();
        Path progressPath = caseProgressPath(source, player);
        if (!Files.isRegularFile(progressPath)) {
            source.sendFailure(Component.literal("[slabrig] cases resume refused: no durable progress at "
                    + progressPath + "; run /slabrig cases 1 [force]."));
            return 0;
        }
        try {
            SlabRigCaseArtifacts.Progress progress = SlabRigCaseArtifacts.readProgress(progressPath);
            SlabRigCaseArtifacts.validateResumeEvidence(SlabRigCaseArtifacts.defaultRoot(), progress,
                    snapshot, caseWorldKey(source), BuildStamp.GIT_SHA,
                    BuildStamp.RUNTIME_CONTENT_SHA256,
                    frozenDyEnabled,
                    player.getUUID().toString(), source.getLevel().dimension().identifier().toString());
            if (progress.nextPage() == 0) {
                SlabRigCaseArtifacts.validateFullArchiveEvidence(SlabRigCaseArtifacts.defaultRoot(), progress,
                        snapshot, caseWorldKey(source), BuildStamp.GIT_SHA,
                        BuildStamp.RUNTIME_CONTENT_SHA256,
                        frozenDyEnabled,
                        player.getUUID().toString(), source.getLevel().dimension().identifier().toString());
                source.sendFailure(Component.literal(
                        "[slabrig] cases resume: catalog enumeration cursor exhausted and full-chain audit"
                                + " passed; this is not behavior-green or player proof; no cells changed."));
                return 0;
            }
            return buildCases(ctx, snapshot, progress.nextPage(), force, player, frozenDyEnabled);
        } catch (IOException e) {
            source.sendFailure(Component.literal("[slabrig] cases resume refused: " + e.getMessage()
                    + "; no cells changed."));
            return 0;
        }
    }

    private static int cases(CommandContext<CommandSourceStack> ctx, int page, boolean force) {
        CommandSourceStack source = ctx.getSource();
        boolean frozenDyEnabled = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabRigCaseCatalog.Snapshot snapshot = SlabRigCaseCatalog.snapshot();
        try {
            SlabRigCaseCatalog.page(snapshot, page);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("[slabrig] cases refused: " + e.getMessage()
                    + "; no cells, active manifest, or progress changed."));
            return 0;
        }
        Player player = proxyPlayerOrRefuse(source, "cases");
        if (player == null) {
            return 0;
        }
        if (!exactCaseIdentityAvailable(source, "cases")) {
            return 0;
        }
        return buildCases(ctx, snapshot, page, force, player, frozenDyEnabled);
    }

    private static int buildCases(CommandContext<CommandSourceStack> ctx,
                                  SlabRigCaseCatalog.Snapshot snapshot, int page,
                                  boolean force, Player player, boolean frozenDyEnabled) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        SlabRigCaseCatalog.CasePage casePage = SlabRigCaseCatalog.page(snapshot, page);
        Direction facing = rigFacing(source);
        Direction right = facing.getClockWise();
        BlockPos base = rigBase(source);
        if (belowWorldBottom(source, world, base)
                || aboveWorldTop(source, world, base, CASE_MAX_TOP_OFFSET)) {
            return 0;
        }

        RigManifest manifest = new RigManifest(keyFor(source), "cases", base, facing,
                MAX_STACK_LENGTH, page, snapshot.pageCount(),
                Math.toIntExact(casePage.firstCaseIndex()), Math.toIntExact(casePage.lastCaseIndex()));
        manifest.caseSnapshot = snapshot;
        manifest.casePage = casePage;
        manifest.caseFrozenDyEnabled = frozenDyEnabled;
        try {
            manifest.previousContiguousManifestHash = previousContiguousManifestHash(
                    source, player, snapshot, page, force, frozenDyEnabled);
        } catch (IOException e) {
            source.sendFailure(Component.literal("[slabrig] cases refused before world mutation: "
                    + e.getMessage()));
            return 0;
        }

        int itemStart = casePage.itemGroup() * SlabRigCaseCatalog.PAGE_GRID_SIDE;
        int topologyStart = casePage.topologyGroup() * SlabRigCaseCatalog.PAGE_GRID_SIDE;
        for (SlabRigCaseCatalog.CaseDefinition definition : casePage.cases()) {
            int itemLocal = definition.item().index() - itemStart;
            int topologyLocal = definition.topology().index() - topologyStart;
            BlockPos tile = base.relative(right, itemLocal * CASE_TILE_SPACING)
                    .relative(facing, topologyLocal * CASE_TILE_SPACING);
            CaseTopologyPlan plan = planCaseTopology(definition.topology(), tile);
            for (BlockPos pos : plan.structure()) {
                manifest.claimStructure(pos);
            }
            Set<BlockPos> effect = effectEnvelope(plan.clicked(), plan.target());
            declareProxyEffect(manifest, plan.clicked(), plan.target());
            Set<BlockPos> guard = caseGuardEnvelope(plan.target());
            for (BlockPos pos : guard) {
                if (!manifest.clearOwnedCells.contains(pos)) {
                    manifest.reserveSafety(pos);
                }
            }
            LinkedHashSet<BlockPos> reserved = new LinkedHashSet<>(plan.structure());
            reserved.addAll(effect);
            reserved.addAll(guard);
            manifest.cases.add(new CaseRuntimeEntry(definition, tile, plan.structure(),
                    List.copyOf(reserved), List.copyOf(effect), List.copyOf(guard),
                    plan.clicked(), plan.target()));
        }

        if (!prepareManifest(source, world, manifest, force, () -> {
            Path root = SlabRigCaseArtifacts.defaultRoot();
            SlabRigCaseArtifacts.WrittenArtifact catalogArtifact =
                    SlabRigCaseArtifacts.writeCatalog(root, snapshot);
            manifest.catalogArtifact = catalogArtifact.path().toString();
            SlabRigCasePageManifest.PageManifest planned = casePageManifest(source, manifest,
                    "PLANNED", "self:content-addressed-after-serialization");
            SlabRigCaseArtifacts.WrittenArtifact plannedArtifact =
                    SlabRigCaseArtifacts.writeManifest(root, planned);
            manifest.plannedCaseArtifact = plannedArtifact.path().toString();
        })) {
            return 0;
        }

        ItemStack held = player.getMainHandItem().copy();
        int attempted = 0;
        int changed = 0;
        int refused = 0;
        int deferred = 0;
        int infrastructureErrors = 0;
        int topologyLawReds = 0;
        int placedThenVanished = 0;
        int externalGuardCells = 0;
        boolean stop = false;
        try {
            for (CaseRuntimeEntry entry : manifest.cases) {
                if (stop) {
                    entry.attemptStatus = "INTERRUPTED";
                    entry.outcome = "INTERRUPTED_AFTER_INFRASTRUCTURE_RED";
                    entry.detail = "later case not attempted after an exact-envelope or topology error";
                    continue;
                }
                entry.externalGuardContext = entry.guard.stream()
                        .filter(pos -> !manifest.clearOwnedCells.contains(pos))
                        .filter(pos -> caseGuardCarriesContext(world, pos))
                        .map(pos -> new SlabRigCasePageManifest.CellState(pos, world.getBlockState(pos),
                                SlabAnchorAttachment.storedPlacementDy(world, pos),
                                caseMarkerFingerprint(world, pos, world.getBlockState(pos))))
                        .toList();
                externalGuardCells += entry.externalGuardContext.size();
                if (!entry.externalGuardContext.isEmpty()) {
                    entry.structureStatus = "DEFERRED_EXTERNAL_GUARD_CONTEXT";
                    entry.structureDetail = "unrelated state/store/markers preserved; no topology or proxy writes";
                    entry.attemptStatus = "DEFERRED";
                    entry.outcome = "DEFERRED_EXTERNAL_GUARD_CONTEXT";
                    entry.detail = "externalGuardCells=" + entry.externalGuardContext.size();
                    deferred++;
                    continue;
                }
                CaseTopologyResult topologyResult = buildCaseTopology(world, player, entry, manifest);
                entry.actualStructure = observeCaseStructure(world, entry.plannedStructure);
                if (topologyResult.hardProblem() != null) {
                    entry.structureStatus = "ERROR";
                    entry.structureDetail = topologyResult.hardProblem();
                    entry.attemptStatus = "ERROR";
                    entry.outcome = "ERROR_TOPOLOGY_INCOMPLETE";
                    entry.detail = topologyResult.hardProblem();
                    infrastructureErrors++;
                    stop = true;
                    continue;
                }
                boolean topologyCounted = false;
                if (topologyResult.lawRed() != null) {
                    entry.structureStatus = "LAW_RED";
                    entry.structureDetail = topologyResult.lawRed();
                    topologyLawReds++;
                    topologyCounted = true;
                } else {
                    entry.structureStatus = "VERIFIED";
                    entry.structureDetail = "exact state/type and adjacent visible seams verified";
                }
                if (entry.definition.item().disposition() == SlabRigCaseCatalog.Disposition.DEFERRED_ROUTE) {
                    entry.attemptStatus = "DEFERRED";
                    entry.outcome = "DEFERRED_" + entry.definition.item().effectPolicy().name();
                    entry.detail = "indexed but not auto-invoked by bounded FLOOR_UP schema";
                    deferred++;
                    continue;
                }

                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(entry.definition.item().id()));
                entry.actionOrigin = LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY.wireName();
                PlacementAttempt attempt = placeViaDetailed(world, player, item, entry.clicked,
                        Direction.UP, entry.target, entry.effect, entry.guard, manifest,
                        takeCasePostUseTestHook(source, entry.definition.id()));
                entry.postActionStructure = observeCaseStructure(world, entry.plannedStructure);
                String postActionRed = postActionTopologyRed(
                        entry.actualStructure, entry.postActionStructure);
                if (postActionRed == null) {
                    entry.postActionStructureStatus = "STABLE";
                    entry.postActionStructureDetail =
                            "exact state/liveDy/storedDy/markers unchanged after action";
                } else {
                    entry.postActionStructureStatus = "LAW_RED";
                    entry.postActionStructureDetail = postActionRed;
                    if (!topologyCounted) {
                        topologyLawReds++;
                    }
                    entry.structureStatus = "LAW_RED";
                    entry.structureDetail += ";postActionTopologyChanged=" + postActionRed;
                }
                entry.attemptStatus = attempt.error() == null ? "EXECUTED" : "ERROR";
                entry.interactionResult = attempt.interactionResult();
                entry.interactionConsumesAction = attempt.interactionConsumesAction();
                entry.stackItemBefore = attempt.stackItemBefore();
                entry.stackBefore = attempt.stackBefore();
                entry.stackItemAfter = attempt.stackItemAfter();
                entry.stackAfter = attempt.stackAfter();
                entry.persistentSubjectPresent = attempt.persistentSubjectPresent();
                entry.actualChanges = attempt.changes();
                attempted++;
                if (!attempt.changed().isEmpty()) {
                    changed++;
                }
                if (attempt.outsideEffect()) {
                    entry.attemptStatus = "ERROR";
                    entry.outcome = "ERROR_OUT_OF_EFFECT_ENVELOPE";
                    entry.detail = "guard observed a mutation outside the declared local effect contract"
                            + (attempt.error() == null ? "" : "; concurrent exception=" + attempt.error());
                    infrastructureErrors++;
                    stop = true;
                } else if (attempt.error() != null) {
                    entry.outcome = "ERROR_EXCEPTION";
                    entry.detail = attempt.error();
                    infrastructureErrors++;
                } else if (!attempt.persistentSubjectPresent()) {
                    entry.outcome = classifyAbsentSubject(attempt.interactionConsumesAction(),
                            attempt.stackItemBefore(), attempt.stackBefore(),
                            attempt.stackItemAfter(), attempt.stackAfter(),
                            !attempt.changes().isEmpty());
                    if ("PLACED_THEN_VANISHED".equals(entry.outcome)) {
                        entry.detail = "action consumed/transformed or left only non-subject evidence;"
                                + " no persistent subject at clicked/target";
                        placedThenVanished++;
                    } else {
                        entry.detail = "useOn produced no observed world change";
                        refused++;
                    }
                } else if (attempt.changed().size() > 1) {
                    entry.outcome = "PLACED_MULTI_CELL";
                    entry.detail = "changed=" + attempt.changed().size();
                } else if (attempt.changed().contains(entry.target)) {
                    entry.outcome = "PLACED_TARGET";
                    entry.detail = "changed planned target";
                } else if (attempt.changed().contains(entry.clicked)) {
                    entry.outcome = "PLACED_IN_CELL";
                    entry.detail = "changed clicked support cell (merge/in-cell route)";
                } else {
                    entry.outcome = "PLACED_EFFECT_CELL";
                    entry.detail = "changed a declared effect cell other than clicked/target";
                }
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }

        boolean frozenDyChangedDuringPage =
                SlabAnchorAttachment.FROZEN_DY_ENABLED != manifest.caseFrozenDyEnabled;
        if (frozenDyChangedDuringPage) {
            infrastructureErrors++;
        }
        boolean infrastructureClean = infrastructureErrors == 0;
        manifest.structurallyComplete = infrastructureClean
                && topologyLawReds == 0 && placedThenVanished == 0;
        manifest.structuralNote = "cases=" + manifest.cases.size() + ",attempted=" + attempted
                + ",changed=" + changed + ",refused=" + refused + ",deferred=" + deferred
                + ",topologyLawReds=" + topologyLawReds + ",externalGuardCells=" + externalGuardCells
                + ",placedThenVanished=" + placedThenVanished
                + ",infraErrors=" + infrastructureErrors
                + (frozenDyChangedDuringPage ? ",frozenDyChangedDuringPage=true" : "");
        String finalStatus = SlabRigCasePageManifest.finalizedStatus(
                infrastructureErrors, topologyLawReds, placedThenVanished);
        try {
            SlabRigCasePageManifest.PageManifest finalManifest = casePageManifest(source, manifest,
                    finalStatus, manifest.plannedCaseArtifact);
            SlabRigCaseArtifacts.WrittenArtifact finalArtifact = SlabRigCaseArtifacts.writeManifest(
                    SlabRigCaseArtifacts.defaultRoot(), finalManifest);
            manifest.finalCaseArtifact = finalArtifact.path().toString();
            manifest.finalCaseManifestHash = finalArtifact.contentId();
            manifest.finalCaseManifest = finalManifest;
        } catch (IOException e) {
            manifest.structurallyComplete = false;
            manifest.structuralNote += ",artifactError=" + e.getClass().getSimpleName();
            source.sendFailure(Component.literal(
                    "[slabrig] cases page is exact-clearable but evidence/progress finalization failed: "
                            + e.getMessage() + "; planned=" + manifest.plannedCaseArtifact));
            return 0;
        }
        if (infrastructureClean) {
            try {
                manifest.caseProgressNote = updateContiguousCaseProgress(source, player, snapshot,
                        page, force, manifest.finalCaseManifestHash, frozenDyEnabled);
            } catch (IOException e) {
                manifest.caseProgressNote = "ERROR_NOT_ADVANCED:" + e.getClass().getSimpleName();
                source.sendFailure(Component.literal(
                        "[slabrig] page evidence finalized but durable resume did not advance: "
                                + e.getMessage() + "; manifest=" + manifest.finalCaseArtifact));
                return 0;
            }
        }

        final int attemptedFinal = attempted;
        final int changedFinal = changed;
        final int refusedFinal = refused;
        final int deferredFinal = deferred;
        final int errorsFinal = infrastructureErrors;
        final int lawRedsFinal = topologyLawReds;
        final int vanishedFinal = placedThenVanished;
        final int externalGuardFinal = externalGuardCells;
        source.sendSuccess(() -> Component.literal(
                "[slabrig] cases page=" + page + "/" + snapshot.pageCount()
                        + " board=4_items_x_4_topologies cases=" + manifest.cases.size()
                        + " attempted=" + attemptedFinal + " changed=" + changedFinal
                        + " refused=" + refusedFinal + " deferred=" + deferredFinal
                        + " topologyLawReds=" + lawRedsFinal
                        + " placedThenVanished=" + vanishedFinal
                        + " externalGuardCells=" + externalGuardFinal
                        + " infraErrors=" + errorsFinal
                        + " frozenDy=" + manifest.caseFrozenDyEnabled
                        + " catalog=" + snapshot.catalogHash()
                        + " provenance=" + LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY.wireName()
                        + " playerProof=ABSENT"
                        + " resume=" + manifest.caseProgressNote
                        + " manifest=" + manifest.finalCaseArtifact), false);
        return infrastructureClean ? 1 : 0;
    }

    private record CaseTopologyPlan(List<BlockPos> structure, BlockPos clicked, BlockPos target) {
        private CaseTopologyPlan {
            structure = List.copyOf(structure);
            clicked = clicked.immutable();
            target = target.immutable();
        }
    }

    private static CaseTopologyPlan planCaseTopology(SlabRigCaseCatalog.Topology topology, BlockPos tile) {
        List<BlockPos> structure = new ArrayList<>();
        BlockPos clicked;
        if (topology.index() == 0) {
            structure.add(tile);
            clicked = tile;
        } else if (topology.index() == 1) {
            structure.add(tile);
            structure.add(tile.above());
            clicked = tile.above();
        } else {
            for (int y = 0; y <= TOWER_SEAT_INDEX; y++) {
                structure.add(tile.above(y));
            }
            String recipe = topology.recipe();
            for (int i = 0; i < recipe.length(); i++) {
                structure.add(tile.above(TOWER_SEAT_INDEX + 1 + i));
            }
            clicked = tile.above(TOWER_SEAT_INDEX + recipe.length());
        }
        return new CaseTopologyPlan(structure, clicked, clicked.above());
    }

    private static Set<BlockPos> caseGuardEnvelope(BlockPos target) {
        LinkedHashSet<BlockPos> guard = new LinkedHashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    guard.add(target.offset(x, y, z).immutable());
                }
            }
        }
        return guard;
    }

    private record CaseTopologyResult(String hardProblem, String lawRed) {
    }

    /** Separates a malformed fixture from a successfully-built fixture that visibly violates LAW. */
    private static CaseTopologyResult buildCaseTopology(ServerLevel world, Player player,
                                                        CaseRuntimeEntry entry, RigManifest manifest) {
        SlabRigCaseCatalog.Topology topology = entry.definition.topology();
        BlockPos tile = entry.tileBase;
        if (topology.index() == 0) {
            setStone(world, tile, manifest);
            return world.getBlockState(tile).is(Blocks.STONE)
                    ? new CaseTopologyResult(null, null)
                    : new CaseTopologyResult("ground control failed", null);
        }
        if (topology.index() == 1) {
            setStone(world, tile, manifest);
            bottomSlab(world, tile.above(), manifest);
            if (!(world.getBlockState(tile.above()).getBlock() instanceof SlabBlock)) {
                return new CaseTopologyResult("single-slab control failed", null);
            }
            return new CaseTopologyResult(null, topologySeamRed(world, List.of(tile, tile.above())));
        }

        setStone(world, tile, manifest);
        bottomSlab(world, tile.above(1), manifest);
        setStone(world, tile.above(2), manifest);
        bottomSlab(world, tile.above(TOWER_SEAT_INDEX), manifest);
        BlockPos cursor = tile.above(TOWER_SEAT_INDEX);
        boolean smoothNext = true;
        String recipe = topology.recipe();
        for (int i = 0; i < recipe.length(); i++) {
            char token = recipe.charAt(i);
            Item item = token == 'S'
                    ? (smoothNext ? Items.SMOOTH_STONE_SLAB : Items.STONE_SLAB)
                    : Items.STONE;
            if (token == 'S') {
                smoothNext = !smoothNext;
            }
            BlockPos target = tile.above(TOWER_SEAT_INDEX + 1 + i);
            BlockState cursorBefore = world.getBlockState(cursor);
            placeVia(world, player, item, cursor, Direction.UP, target, manifest);
            BlockState actual = world.getBlockState(target);
            boolean correct = token == 'S'
                    ? item instanceof BlockItem blockItem
                    && actual.is(blockItem.getBlock())
                    && actual.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                    : actual.is(Blocks.STONE);
            if (!correct) {
                return new CaseTopologyResult("topology=" + topology.id() + " wrong/refused at "
                        + target.toShortString() + " actual=" + actual, null);
            }
            if (!world.getBlockState(cursor).equals(cursorBefore)) {
                return new CaseTopologyResult("topology=" + topology.id() + " consolidated wrong cell "
                        + cursor.toShortString(), null);
            }
            cursor = target;
        }
        if (!cursor.equals(entry.clicked)) {
            return new CaseTopologyResult("topology terminal mismatch", null);
        }
        List<BlockPos> visibleChain = new ArrayList<>();
        visibleChain.add(tile.above(TOWER_SEAT_INDEX));
        for (int i = 0; i < recipe.length(); i++) {
            visibleChain.add(tile.above(TOWER_SEAT_INDEX + 1 + i));
        }
        return new CaseTopologyResult(null, topologySeamRed(world, visibleChain));
    }

    private static String topologySeamRed(ServerLevel world, List<BlockPos> chain) {
        List<String> reds = new ArrayList<>();
        for (int i = 1; i < chain.size(); i++) {
            BlockPos lower = chain.get(i - 1);
            BlockPos upper = chain.get(i);
            BlockState lowerState = world.getBlockState(lower);
            BlockState upperState = world.getBlockState(upper);
            VoxelShape lowerShape = lowerState.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            VoxelShape upperShape = upperState.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (lowerShape.isEmpty() || upperShape.isEmpty()) {
                reds.add("NO_SHAPE@" + lower.toShortString() + "->" + upper.toShortString());
                continue;
            }
            double lowerTop = lower.getY() + SlabSupport.getYOffset(world, lower, lowerState)
                    + lowerShape.bounds().maxY;
            double upperBottom = upper.getY() + SlabSupport.getYOffset(world, upper, upperState)
                    + upperShape.bounds().minY;
            double seam = upperBottom - lowerTop;
            if (Math.abs(seam) > EPS) {
                reds.add((seam > 0 ? "GAP" : "OVERLAP") + "@" + lower.toShortString()
                        + "->" + upper.toShortString() + " seam=" + seam);
            }
        }
        return reds.isEmpty() ? null : String.join(";", reds);
    }

    private static List<SlabRigCasePageManifest.StructureCellState> observeCaseStructure(
            ServerLevel world, List<BlockPos> cells) {
        return cells.stream().map(pos -> {
            BlockState state = world.getBlockState(pos);
            return new SlabRigCasePageManifest.StructureCellState(pos, state,
                    SlabSupport.getYOffset(world, pos, state),
                    SlabAnchorAttachment.storedPlacementDy(world, pos),
                    caseMarkerFingerprint(world, pos, state));
        }).toList();
    }

    /** Exact no-pop/no-reset comparison for every planned topology cell across one tested action. */
    private static String postActionTopologyRed(
            List<SlabRigCasePageManifest.StructureCellState> before,
            List<SlabRigCasePageManifest.StructureCellState> after) {
        LinkedHashMap<BlockPos, SlabRigCasePageManifest.StructureCellState> afterByPos =
                new LinkedHashMap<>();
        for (SlabRigCasePageManifest.StructureCellState cell : after) {
            if (afterByPos.put(cell.pos(), cell) != null) {
                return "duplicate post-action topology cell @" + cell.pos().toShortString();
            }
        }
        List<String> reds = new ArrayList<>();
        for (SlabRigCasePageManifest.StructureCellState prior : before) {
            SlabRigCasePageManifest.StructureCellState current = afterByPos.remove(prior.pos());
            if (current == null) {
                reds.add("missing@" + prior.pos().toShortString());
                continue;
            }
            List<String> fields = new ArrayList<>();
            if (!prior.state().equals(current.state())) {
                fields.add("state=" + prior.state() + "->" + current.state());
            }
            if (!sameObservedDy(prior.liveDy(), current.liveDy())) {
                fields.add("liveDy=" + prior.liveDy() + "->" + current.liveDy());
            }
            if (!sameObservedDy(prior.storedDy(), current.storedDy())) {
                fields.add("storedDy=" + prior.storedDy() + "->" + current.storedDy());
            }
            if (!prior.markerFingerprint().equals(current.markerFingerprint())) {
                fields.add("markers=" + prior.markerFingerprint() + "->" + current.markerFingerprint());
            }
            if (!fields.isEmpty()) {
                reds.add(prior.pos().toShortString() + "[" + String.join(",", fields) + "]");
            }
        }
        for (BlockPos unexpected : afterByPos.keySet()) {
            reds.add("unexpected@" + unexpected.toShortString());
        }
        return reds.isEmpty() ? null : String.join(";", reds);
    }

    private static boolean sameObservedDy(double first, double second) {
        double normalizedFirst = first == 0.0 ? 0.0 : first;
        double normalizedSecond = second == 0.0 ? 0.0 : second;
        return Double.doubleToLongBits(normalizedFirst) == Double.doubleToLongBits(normalizedSecond);
    }

    private static PlacementAttempt placeViaDetailed(ServerLevel world, Player player, Item item,
                                                      BlockPos clicked, Direction face, BlockPos target,
                                                      List<BlockPos> effectCells, List<BlockPos> guardCells,
                                                      RigManifest manifest) {
        return placeViaDetailed(world, player, item, clicked, face, target, effectCells, guardCells,
                manifest, () -> {
                });
    }

    private static PlacementAttempt placeViaDetailed(ServerLevel world, Player player, Item item,
                                                      BlockPos clicked, Direction face, BlockPos target,
                                                      List<BlockPos> effectCells, List<BlockPos> guardCells,
                                                      RigManifest manifest,
                                                      Runnable afterUseBeforeObservation) {
        LinkedHashSet<BlockPos> observed = new LinkedHashSet<>(guardCells);
        observed.addAll(effectCells);
        LinkedHashMap<BlockPos, BlockState> before = new LinkedHashMap<>();
        LinkedHashMap<BlockPos, Double> storedBefore = new LinkedHashMap<>();
        LinkedHashMap<BlockPos, String> markerBefore = new LinkedHashMap<>();
        for (BlockPos pos : observed) {
            before.put(pos, world.getBlockState(pos));
            storedBefore.put(pos, SlabAnchorAttachment.storedPlacementDy(world, pos));
            markerBefore.put(pos, caseMarkerFingerprint(world, pos, world.getBlockState(pos)));
        }
        ItemStack stack = new ItemStack(item);
        String stackItemBefore = itemId(stack);
        int stackBefore = stack.getCount();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult[] result = {InteractionResult.PASS};
        String[] error = {null};
        try {
            Vec3 hit = Vec3.atCenterOf(clicked).add(face.getStepX() * 0.5,
                    face.getStepY() * 0.5, face.getStepZ() * 0.5);
            LiveCursorIntentRecorder.withActionOrigin(
                    LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY,
                    () -> result[0] = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(hit, face, clicked, false))));
        } catch (RuntimeException e) {
            error[0] = e.getClass().getName() + ":" + String.valueOf(e.getMessage());
        }
        try {
            afterUseBeforeObservation.run();
        } catch (RuntimeException e) {
            if (error[0] == null) {
                error[0] = "POST_USE_OBSERVATION_HOOK:" + e.getClass().getName()
                        + ":" + String.valueOf(e.getMessage());
            }
        }
        ItemStack afterStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        String stackItemAfter = itemId(afterStack);
        int stackAfter = afterStack.getCount();
        Set<BlockPos> effect = Set.copyOf(effectCells);
        LinkedHashSet<BlockPos> changed = new LinkedHashSet<>();
        List<SlabRigCasePageManifest.CellChange> changes = new ArrayList<>();
        boolean outside = false;
        boolean persistentSubjectPresent = false;
        for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
            BlockState after = world.getBlockState(entry.getKey());
            double storedAfter = SlabAnchorAttachment.storedPlacementDy(world, entry.getKey());
            String markerAfter = caseMarkerFingerprint(world, entry.getKey(), after);
            if (SlabRigCasePageManifest.observationChanged(entry.getValue(),
                    storedBefore.get(entry.getKey()), markerBefore.get(entry.getKey()),
                    after, storedAfter, markerAfter)) {
                BlockPos pos = entry.getKey().immutable();
                changed.add(pos);
                boolean declaredEffect = effect.contains(pos);
                if (!declaredEffect) {
                    outside = true;
                }
                if ((pos.equals(clicked) || pos.equals(target))
                        && !entry.getValue().equals(after) && !after.isAir()) {
                    persistentSubjectPresent = true;
                }
                boolean safeToClaim = declaredEffect || !caseObservationCarriesContext(entry.getValue(),
                        storedBefore.get(pos), markerBefore.get(pos));
                if (safeToClaim) {
                    manifest.recordAuthored(pos, entry.getValue(), after);
                    manifest.touchAttachment(pos);
                    manifest.clearOwnedCells.add(pos);
                } else if (error[0] == null) {
                    // Fail closed without making /clear erase a user-owned pre-existing guard cell.
                    error[0] = "PREEXISTING_GUARD_CONTEXT_MUTATED:" + pos.toShortString();
                }
                changes.add(new SlabRigCasePageManifest.CellChange(pos, entry.getValue(), after,
                        storedBefore.get(pos),
                        storedAfter, markerBefore.get(pos), markerAfter));
            }
        }
        return new PlacementAttempt(changed, changes, result[0].toString(), result[0].consumesAction(),
                stackItemBefore, stackBefore, stackItemAfter, stackAfter, persistentSubjectPresent,
                error[0], outside);
    }

    private static Runnable takeCasePostUseTestHook(CommandSourceStack source, String caseId) {
        RigKey key = keyFor(source);
        CasePostUseTestHook candidate = CASE_POST_USE_TEST_HOOKS.get(key);
        if (candidate != null && candidate.caseId().equals(caseId)
                && CASE_POST_USE_TEST_HOOKS.remove(key, candidate)) {
            return candidate.hook();
        }
        return () -> {
        };
    }

    /**
     * Executes the production proxy-use observation path without registering a command manifest.
     * The post-use hook lets the GameTest remove a successfully placed subject before observation,
     * reproducing the exact same-count vanish ambiguity that the manifest must classify as a red.
     */
    public static CasePlacementProbeForTests probeCasePlacementForTests(
            ServerLevel world, Player player, Item item, BlockPos clicked, BlockPos target,
            Runnable afterUseBeforeObservation) {
        Objects.requireNonNull(afterUseBeforeObservation, "afterUseBeforeObservation");
        ItemStack original = player.getMainHandItem().copy();
        RigManifest manifest = new RigManifest(
                new RigKey(world.getServer(), player.getUUID(), world.dimension()),
                "case-placement-probe", clicked, Direction.SOUTH, 0, 0, 0, 0, 0);
        PlacementAttempt attempt;
        try {
            attempt = placeViaDetailed(world, player, item, clicked, Direction.UP, target,
                    List.copyOf(effectEnvelope(clicked, target)),
                    List.copyOf(caseGuardEnvelope(target)), manifest, afterUseBeforeObservation);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, original);
        }
        String outcome;
        if (attempt.outsideEffect()) {
            outcome = "ERROR_OUT_OF_EFFECT_ENVELOPE";
        } else if (attempt.error() != null) {
            outcome = "ERROR_EXCEPTION";
        } else if (!attempt.persistentSubjectPresent()) {
            outcome = classifyAbsentSubject(attempt.interactionConsumesAction(),
                    attempt.stackItemBefore(), attempt.stackBefore(), attempt.stackItemAfter(),
                    attempt.stackAfter(), !attempt.changes().isEmpty());
        } else {
            outcome = "PERSISTENT_SUBJECT";
        }
        CasePlacementProbeForTests probe = new CasePlacementProbeForTests(
                attempt.interactionResult(), attempt.interactionConsumesAction(),
                attempt.stackItemBefore(), attempt.stackBefore(), attempt.stackItemAfter(),
                attempt.stackAfter(), attempt.persistentSubjectPresent(), attempt.changes().size(),
                outcome, attempt.error(), attempt.outsideEffect());
        clearExact(world, manifest);
        return probe;
    }

    /**
     * A missing subject is a WYSIWYG red when the interaction consumed action, transformed/consumed
     * the held stack, or left only non-subject world/store/marker evidence. Count-only comparison is
     * insufficient: survival powder snow changes one filled bucket into one empty bucket, while
     * creative use can consume the action with a byte-identical one-item stack.
     */
    public static String classifyAbsentSubject(boolean interactionConsumesAction,
                                               String stackItemBefore, int stackBefore,
                                               String stackItemAfter, int stackAfter,
                                               boolean observedNonSubjectChange) {
        Objects.requireNonNull(stackItemBefore, "stackItemBefore");
        Objects.requireNonNull(stackItemAfter, "stackItemAfter");
        if (stackBefore < 0 || stackAfter < 0) {
            throw new IllegalArgumentException("stack counts cannot be negative");
        }
        boolean stackTransformed = stackBefore != stackAfter
                || !stackItemBefore.equals(stackItemAfter);
        return interactionConsumesAction || stackTransformed || observedNonSubjectChange
                ? "PLACED_THEN_VANISHED" : "REFUSED_NO_CHANGE";
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String caseMarkerFingerprint(ServerLevel world, BlockPos pos, BlockState state) {
        return "frozenFlat=" + SlabAnchorAttachment.isFrozenFlat(world, pos)
                + ",anchored=" + SlabAnchorAttachment.isAnchored(world, pos)
                + ",compoundFull=" + SlabAnchorAttachment.isCompoundFullBlockAnchor(world, pos)
                + ",sideLower=" + SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(world, pos, state)
                + ",sideUpper=" + SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(world, pos, state)
                + ",sideDouble=" + SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(world, pos, state)
                + ",ownerTop=" + SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(world, pos, state)
                + ",persistentCarrier=" + SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state);
    }

    private static boolean caseGuardCarriesContext(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        double stored = SlabAnchorAttachment.storedPlacementDy(world, pos);
        return caseObservationCarriesContext(state, stored, caseMarkerFingerprint(world, pos, state));
    }

    private static boolean caseObservationCarriesContext(BlockState state, double stored,
                                                         String markerFingerprint) {
        return !state.isAir() || !Double.isNaN(stored)
                || !markerFingerprint.equals(EMPTY_CASE_MARKERS);
    }

    private static SlabRigCasePageManifest.PageManifest casePageManifest(CommandSourceStack source,
                                                                         RigManifest manifest,
                                                                         String status,
                                                                         String plannedArtifact) {
        List<SlabRigCasePageManifest.CaseAttempt> attempts = manifest.cases.stream()
                .map(CaseRuntimeEntry::snapshot).toList();
        String player = source.getEntity() instanceof Player actor
                ? actor.getUUID().toString() : "console";
        return new SlabRigCasePageManifest.PageManifest(status, manifest.caseSnapshot,
                manifest.casePage, caseWorldKey(source), manifest.caseFrozenDyEnabled,
                source.getLevel().dimension().identifier().toString(), player,
                manifest.base, manifest.facing.getName(), manifest.catalogArtifact,
                manifest.previousContiguousManifestHash, plannedArtifact, attempts);
    }

    private static Path caseProgressPath(CommandSourceStack source, Player player) {
        return SlabRigCaseArtifacts.progressPath(SlabRigCaseArtifacts.defaultRoot(),
                player.getUUID().toString(), source.getLevel().dimension().identifier().toString(),
                caseWorldKey(source));
    }

    private static String previousContiguousManifestHash(CommandSourceStack source, Player player,
                                                         SlabRigCaseCatalog.Snapshot snapshot,
                                                         int page, boolean force,
                                                         boolean frozenDyEnabled) throws IOException {
        if (page == 1 && force) {
            return "none";
        }
        Path progressPath = caseProgressPath(source, player);
        if (!Files.isRegularFile(progressPath)) {
            return page == 1 ? "none" : "out-of-order:not-progress-eligible";
        }
        SlabRigCaseArtifacts.Progress progress = SlabRigCaseArtifacts.readProgress(progressPath);
        SlabRigCaseArtifacts.validateResumeEvidence(SlabRigCaseArtifacts.defaultRoot(), progress,
                snapshot, caseWorldKey(source), BuildStamp.GIT_SHA,
                BuildStamp.RUNTIME_CONTENT_SHA256, frozenDyEnabled,
                player.getUUID().toString(),
                source.getLevel().dimension().identifier().toString());
        if (progress.nextPage() == page) {
            return progress.lastCompletedPage() == 0 ? "none" : progress.lastManifestHash();
        }
        return "out-of-order:not-progress-eligible";
    }

    /** Hash of the durable save root plus a tool-owned persistent UUID inside that exact save. */
    public static String caseWorldKey(CommandSourceStack source) {
        Path worldRoot = source.getServer().getWorldPath(LevelResource.ROOT)
                .toAbsolutePath().normalize();
        try {
            return SlabRigCaseArtifacts.loadOrCreateWorldKey(worldRoot);
        } catch (IOException e) {
            return "unavailable";
        }
    }

    private static boolean exactCaseIdentityAvailable(CommandSourceStack source, String operation) {
        if (!BuildStamp.hasExactRuntimeContent()) {
            source.sendFailure(Component.literal("[slabrig] " + operation
                    + " refused: exact runtime content digest is unavailable; no world/progress changed."));
            return false;
        }
        if (!caseWorldKey(source).matches("[0-9a-f]{64}")) {
            source.sendFailure(Component.literal("[slabrig] " + operation
                    + " refused: persistent world identity is unavailable or malformed;"
                    + " no rig/progress changed."));
            return false;
        }
        return true;
    }

    /**
     * Advance only the contiguous campaign prefix. Random inspection/retest pages remain evidenced by
     * their immutable manifests but cannot skip, reset, or falsely complete durable resume progress.
     */
    private static String updateContiguousCaseProgress(CommandSourceStack source, Player player,
                                                       SlabRigCaseCatalog.Snapshot snapshot,
                                                       int completedPage, boolean force,
                                                       String manifestHash,
                                                       boolean frozenDyEnabled)
            throws IOException {
        Path path = caseProgressPath(source, player);
        if (completedPage == 1 && force) {
            int next = snapshot.pageCount() == 1 ? 0 : 2;
            writeEvidenceValidatedProgress(source, player, snapshot, path,
                    progressValue(source, player, snapshot, next, 1, manifestHash,
                            frozenDyEnabled), frozenDyEnabled);
            return next == 0 ? "catalog-enumeration-closed;full-chain-audit=passed;force-restart"
                    : "next=2;force-restart";
        }
        int currentNext = 1;
        if (Files.isRegularFile(path)) {
            SlabRigCaseArtifacts.Progress current = SlabRigCaseArtifacts.readProgress(path);
            boolean identityMatches = current.worldKey().equals(caseWorldKey(source))
                    && current.buildGitSha().equals(BuildStamp.GIT_SHA)
                    && current.runtimeContentSha256().equals(BuildStamp.RUNTIME_CONTENT_SHA256)
                    && current.frozenDyEnabled() == frozenDyEnabled
                    && current.executionContract().equals(SlabRigCaseCatalog.EXECUTION_CONTRACT)
                    && current.catalogHash().equals(snapshot.catalogHash())
                    && current.pageCount() == snapshot.pageCount();
            if (!identityMatches) {
                if (completedPage != 1) {
                    throw new IOException("existing resume cursor belongs to a different world/build/runtime/"
                            + "frozen-mode/contract/catalog;"
                            + " run page 1 force to start a new contiguous campaign");
                }
            } else {
                SlabRigCaseArtifacts.validateResumeEvidence(SlabRigCaseArtifacts.defaultRoot(), current,
                        snapshot, caseWorldKey(source), BuildStamp.GIT_SHA,
                        BuildStamp.RUNTIME_CONTENT_SHA256,
                        frozenDyEnabled,
                        player.getUUID().toString(), source.getLevel().dimension().identifier().toString());
            }
            if (identityMatches && current.nextPage() == 0) {
                SlabRigCaseArtifacts.validateFullArchiveEvidence(SlabRigCaseArtifacts.defaultRoot(), current,
                        snapshot, caseWorldKey(source), BuildStamp.GIT_SHA,
                        BuildStamp.RUNTIME_CONTENT_SHA256,
                        frozenDyEnabled,
                        player.getUUID().toString(), source.getLevel().dimension().identifier().toString());
                return "catalog-enumeration-closed;full-chain-audit=passed;retained";
            } else if (identityMatches) {
                currentNext = current.nextPage();
            }
        }
        if (completedPage != currentNext) {
            if (!Files.isRegularFile(path)) {
                SlabRigCaseArtifacts.writeProgress(path, progressValue(source, player, snapshot,
                        1, 0, "none", frozenDyEnabled));
            }
            return "next=" + currentNext + ";page-evidenced-out-of-order-no-advance";
        }
        int next = completedPage < snapshot.pageCount() ? completedPage + 1 : 0;
        writeEvidenceValidatedProgress(source, player, snapshot, path,
                progressValue(source, player, snapshot, next, completedPage, manifestHash,
                        frozenDyEnabled), frozenDyEnabled);
        return next == 0 ? "catalog-enumeration-closed;full-chain-audit=passed;not-behavior-green"
                : "next=" + next;
    }

    private static void writeEvidenceValidatedProgress(CommandSourceStack source, Player player,
                                                        SlabRigCaseCatalog.Snapshot snapshot, Path path,
                                                        SlabRigCaseArtifacts.Progress candidate,
                                                        boolean frozenDyEnabled)
            throws IOException {
            SlabRigCaseArtifacts.validateResumeEvidence(SlabRigCaseArtifacts.defaultRoot(), candidate,
                    snapshot, caseWorldKey(source), BuildStamp.GIT_SHA,
                    BuildStamp.RUNTIME_CONTENT_SHA256, frozenDyEnabled,
                    player.getUUID().toString(),
                    source.getLevel().dimension().identifier().toString());
        if (candidate.nextPage() == 0) {
            SlabRigCaseArtifacts.validateFullArchiveEvidence(SlabRigCaseArtifacts.defaultRoot(), candidate,
                    snapshot, caseWorldKey(source), BuildStamp.GIT_SHA,
                    BuildStamp.RUNTIME_CONTENT_SHA256, frozenDyEnabled,
                    player.getUUID().toString(),
                    source.getLevel().dimension().identifier().toString());
        }
        SlabRigCaseArtifacts.writeProgress(path, candidate);
    }

    private static SlabRigCaseArtifacts.Progress progressValue(CommandSourceStack source, Player player,
                                                               SlabRigCaseCatalog.Snapshot snapshot,
                                                               int nextPage, int lastCompletedPage,
                                                               String lastManifestHash,
                                                               boolean frozenDyEnabled) {
        return new SlabRigCaseArtifacts.Progress(caseWorldKey(source), BuildStamp.GIT_SHA,
                BuildStamp.RUNTIME_CONTENT_SHA256, frozenDyEnabled,
                SlabRigCaseCatalog.EXECUTION_CONTRACT,
                snapshot.catalogHash(), nextPage,
                snapshot.pageCount(), lastCompletedPage, lastManifestHash);
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        RigKey key = keyFor(source);
        RigManifest manifest = LAST_MANIFEST.get(key);

        if (manifest == null) {
            // Per-dimension records: a rig for this player may live in another dimension — name it
            // rather than silently reporting "nothing to clear".
            RigManifest elsewhere = null;
            for (Map.Entry<RigKey, RigManifest> e : LAST_MANIFEST.entrySet()) {
                if (e.getKey().server == key.server && e.getKey().player.equals(key.player)) {
                    elsewhere = e.getValue();
                    break;
                }
            }
            if (elsewhere != null) {
                final RigManifest other = elsewhere;
                source.sendFailure(Component.literal(
                        "[slabrig] your last rig is in " + other.key.dimension.identifier()
                                + ", not this dimension — clear it there."));
                return 0;
            }
            source.sendFailure(Component.literal("[slabrig] nothing to clear — build a rig first."));
            return 0;
        }

        int cleared = clearExact(world, manifest);
        // Remove the manifest only after the whole clear pass, so an exception/partial pass remains
        // discoverable and retryable instead of orphaning owned cells.
        LAST_MANIFEST.remove(key, manifest);
        final int removed = cleared;
        source.sendSuccess(() -> Component.literal(
                "[slabrig] directly cleared " + removed + " blocks from exact preset=" + manifest.preset
                        + "; no direct block/store cleanup targeted outside its manifest. Vanilla survival"
                        + " updates may also remove unsupported neighboring dependents."), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String status = trackedManifestStatus(source);
        if ("none".equals(status)) {
            source.sendFailure(Component.literal("[slabrig] status: no tracked rig in this server session/dimension."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[slabrig] status: " + status), false);
        return 1;
    }

    /** Compact truthful manifest readback; public only so the registered command contract can pin it. */
    public static String trackedManifestStatus(CommandSourceStack source) {
        RigManifest manifest = LAST_MANIFEST.get(keyFor(source));
        if (manifest == null) {
            return "none";
        }
        ServerLevel world = source.getLevel();
        int present = 0;
        int missing = 0;
        int wrong = 0;
        for (Map.Entry<BlockPos, BlockState> entry : manifest.authoredStates.entrySet()) {
            BlockState actual = world.getBlockState(entry.getKey());
            if (actual.isAir()) {
                missing++;
            } else {
                present++;
                if (!actual.equals(entry.getValue())) {
                    wrong++;
                }
            }
        }
        String pageText;
        if (manifest.casePage != null) {
            pageText = manifest.casePage.page() + "/" + manifest.casePage.pageCount()
                    + " caseIndexes=" + manifest.casePage.cases().stream()
                    .map(c -> Long.toString(c.index() + 1)).collect(java.util.stream.Collectors.joining(","))
                    + "/" + manifest.caseSnapshot.totalCases();
        } else if (manifest.page > 0) {
            pageText = manifest.page + "/" + manifest.pageCount + " recipes="
                    + (manifest.firstRecipeIndex + 1) + "-" + (manifest.lastRecipeIndex + 1)
                    + "/" + stackRecipes(manifest.maxLength).size();
        } else {
            pageText = "n/a";
        }
        boolean completeNow = manifest.structurallyComplete && missing == 0 && wrong == 0;
        return "preset=" + manifest.preset
                + " dimension=" + manifest.key.dimension.identifier()
                + " serverSession=current"
                + " base=" + manifest.base.toShortString()
                + " facing=" + manifest.facing.getName()
                + " page=" + pageText
                + " reserved=" + manifest.reservedCells.size()
                + " authored=" + manifest.authoredStates.size()
                + " attachments=" + manifest.attachmentCells.size()
                + " subjects=" + manifest.subjectSlots.size()
                + " clearOwned=" + manifest.clearOwnedCells.size()
                + " present=" + present + " missing=" + missing + " wrong=" + wrong
                + " bounds=" + manifest.bounds()
                + " structural=" + (completeNow ? "complete" : "incomplete")
                + " note=" + manifest.structuralNote
                + (manifest.caseSnapshot == null ? "" : " catalogHash=" + manifest.caseSnapshot.catalogHash()
                + " caseBoard=4_items_x_4_topologies"
                + " frozenDy=" + manifest.caseFrozenDyEnabled
                + " caseManifest=" + manifest.finalCaseArtifact
                + " resume=" + manifest.caseProgressNote
                + " playerProof=ABSENT")
                + " provenance=" + LiveCursorIntentRecorder.ActionOrigin.AUTO_USEON_PROXY.wireName();
    }

    public static int trackedSubjectSlotCountForTests(CommandSourceStack source) {
        RigManifest manifest = LAST_MANIFEST.get(keyFor(source));
        return manifest == null ? 0 : manifest.subjectSlots.size();
    }

    public static List<BlockPos> trackedReservedCellsForTests(CommandSourceStack source) {
        RigManifest manifest = LAST_MANIFEST.get(keyFor(source));
        return manifest == null ? List.of() : List.copyOf(manifest.reservedCells);
    }

    /** Final canonical RIG-2 page JSON for registered command/clear tests; null for other presets. */
    public static String trackedCaseManifestJsonForTests(CommandSourceStack source) {
        RigManifest manifest = LAST_MANIFEST.get(keyFor(source));
        return manifest == null || manifest.finalCaseManifest == null
                ? null : SlabRigCasePageManifest.canonicalJson(manifest.finalCaseManifest);
    }

    /** One-shot exact-case fault injection for post-use topology reconciliation GameTests. */
    public static void installCasePostUseHookForTests(CommandSourceStack source, String caseId,
                                                      Runnable hook) {
        CASE_POST_USE_TEST_HOOKS.put(keyFor(source),
                new CasePostUseTestHook(Objects.requireNonNull(caseId), Objects.requireNonNull(hook)));
    }

    /** Removes an unconsumed test hook after a failed/refused test setup. */
    public static void clearCasePostUseHookForTests(CommandSourceStack source) {
        CASE_POST_USE_TEST_HOOKS.remove(keyFor(source));
    }

    private static void clearServerSession(MinecraftServer server) {
        LAST_MANIFEST.keySet().removeIf(key -> key.server == server);
        CASE_POST_USE_TEST_HOOKS.keySet().removeIf(key -> key.server == server);
    }

    // ── footprint safety ──────────────────────────────────────────────────────

    /** F6: refuse (with a message) if the rig base would sit below the world's minimum build height. */
    private static boolean belowWorldBottom(CommandSourceStack source, ServerLevel world, BlockPos base) {
        int minY = world.getMinY();
        if (base.getY() < minY) {
            source.sendFailure(Component.literal(
                    "[slabrig] rig base Y=" + base.getY() + " is below the world minimum build height "
                            + minY + " — move up and retry."));
            return true;
        }
        return false;
    }

    /** Player-backed proxy builders must never split scenery/source level from the useOn player's level. */
    private static Player proxyPlayerOrRefuse(CommandSourceStack source, String preset) {
        Player player = source.getEntity() instanceof Player p ? p : null;
        if (player == null) {
            source.sendFailure(Component.literal(
                    "[slabrig] " + preset + " auto-places via your hand — run it as a player, not the console."));
            return null;
        }
        if (player.level() != source.getLevel()) {
            source.sendFailure(Component.literal(
                    "[slabrig] " + preset + " refused: command source level and player level differ; no cells changed."));
            return null;
        }
        return player;
    }

    /**
     * Refuse (with a message) if {@code base + cellsAboveBase} would sit above the world's build height.
     * The sum is computed in {@code long}: an extreme base Y must widen into a clean refusal, never wrap
     * negative and slip past the guard.
     */
    private static boolean aboveWorldTop(CommandSourceStack source, ServerLevel world, BlockPos base,
                                         int cellsAboveBase) {
        int maxY = world.getMaxY();
        long top = (long) base.getY() + cellsAboveBase;
        if (top > maxY) {
            source.sendFailure(Component.literal(
                    "[slabrig] rig top Y=" + top + " would exceed the world build height " + maxY
                            + " — reduce height/columns or move down."));
            return true;
        }
        return false;
    }

    /** Refuses or installs a planned exact manifest before the first write. */
    private static boolean prepareManifest(CommandSourceStack source, ServerLevel world,
                                           RigManifest manifest, boolean force) {
        return prepareManifest(source, world, manifest, force, () -> {
        });
    }

    @FunctionalInterface
    private interface BeforeManifestMutation {
        void run() throws IOException;
    }

    /**
     * Refuses first, then runs the evidence hook, then installs/clears/writes. The hook therefore
     * cannot create progress for a refused board and a write failure cannot strand world mutation.
     */
    private static boolean prepareManifest(CommandSourceStack source, ServerLevel world,
                                           RigManifest manifest, boolean force,
                                           BeforeManifestMutation beforeMutation) {
        RigKey key = manifest.key;
        RigManifest previous = LAST_MANIFEST.get(key);
        if (previous != null && !force) {
            source.sendFailure(Component.literal(
                    "[slabrig] preset=" + previous.preset + " is still tracked at "
                            + previous.base.toShortString() + " — clear it or add 'force'; no cells changed."));
            return false;
        }

        int occupied = 0;
        BlockPos sample = null;
        for (BlockPos pos : manifest.reservedCells) {
            if (caseGuardCarriesContext(world, pos)) {
                occupied++;
                if (sample == null) {
                    sample = pos;
                }
            }
        }
        if (occupied > 0 && !force) {
            final int count = occupied;
            final BlockPos at = sample;
            source.sendFailure(Component.literal(
                    "[slabrig] exact reserved cells carry block/store/marker context (" + count
                            + " cells; sample "
                            + at.toShortString() + ") — add 'force'; no cells or prior manifest changed."));
            return false;
        }

        try {
            beforeMutation.run();
        } catch (IOException e) {
            source.sendFailure(Component.literal(
                    "[slabrig] evidence planning failed before world/manifest mutation: " + e.getMessage()));
            return false;
        }

        if (previous != null) {
            clearExact(world, previous);
            LAST_MANIFEST.remove(key, previous);
        }

        // The plan becomes discoverable BEFORE force-claim/preclean/build writes. A partial build is
        // therefore always exactly clearable.
        LAST_MANIFEST.put(key, manifest);

        if (force) {
            clearCellsTopDown(world, manifest.clearOwnedCells);
        }
        for (BlockPos pos : manifest.clearOwnedCells) {
            SlabAnchorAttachment.removeAnchor(world, pos);
            manifest.touchAttachment(pos);
        }
        return true;
    }

    private static int clearExact(ServerLevel world, RigManifest manifest) {
        for (BlockPos pos : manifest.attachmentCells) {
            // Always clear recorded attachments, even if another action already made the block air.
            SlabAnchorAttachment.removeAnchor(world, pos);
        }
        return clearCellsTopDown(world, manifest.clearOwnedCells);
    }

    private static int clearCellsTopDown(ServerLevel world, Set<BlockPos> cells) {
        List<BlockPos> ordered = new ArrayList<>(cells);
        ordered.sort(Comparator.<BlockPos>comparingInt(pos -> pos.getY()).reversed()
                .thenComparingInt(pos -> pos.getX())
                .thenComparingInt(pos -> pos.getZ()));
        int cleared = 0;
        for (BlockPos pos : ordered) {
            SlabAnchorAttachment.removeAnchor(world, pos);
            if (!world.getBlockState(pos).isAir()) {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), CLEAR_FLAG);
                cleared++;
            }
        }
        return cleared;
    }

    // ── self-verification (F4) ─────────────────────────────────────────────────

    private static void addIfMismatch(List<String> mismatches, ServerLevel world, BlockPos pos,
                                      double expected, String label) {
        double got = SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
        if (Math.abs(got - expected) > EPS) {
            mismatches.add(label + " @" + pos.toShortString() + ": expected " + expected + " but read " + got);
        }
    }

    private static Component warn(String preset, BlockPos base, Direction facing, List<String> mismatches) {
        return Component.literal(
                "[slabrig] " + preset + " built at " + base.toShortString() + " facing " + facing.getName()
                        + " but it does NOT read what its sign says: " + String.join("; ", mismatches))
                .withStyle(ChatFormatting.RED);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void setStone(ServerLevel world, BlockPos pos, RigManifest manifest) {
        setPlannedBlock(world, pos, Blocks.STONE.defaultBlockState(), manifest);
    }

    private static void bottomSlab(ServerLevel world, BlockPos pos, RigManifest manifest) {
        setPlannedBlock(world, pos,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), manifest);
    }

    private static void setPlannedBlock(ServerLevel world, BlockPos pos, BlockState state,
                                        RigManifest manifest) {
        BlockState before = world.getBlockState(pos);
        world.setBlock(pos, state, FLAG);
        manifest.recordAuthored(pos, before, world.getBlockState(pos));
    }

    private static RigKey keyFor(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        UUID id = player != null
                ? player.getUUID()
                : source.getEntity() instanceof Player p ? p.getUUID() : CONSOLE_KEY;
        return new RigKey(source.getServer(), id, source.getLevel().dimension());
    }

    /** Read-only cross-tool allocation guard for the durable direct-hanging executor. */
    static boolean hasTrackedManifestInLevel(MinecraftServer server, ResourceKey<Level> dimension) {
        return LAST_MANIFEST.keySet().stream()
                .anyMatch(key -> key.server == server && key.dimension.equals(dimension));
    }

    /**
     * GameTest-only batch reset for a dedicated serial test environment. The headless runner keeps
     * static Java state after earlier environments finish, so their completed volatile manifests would
     * otherwise contaminate a later production-command integration test. This never clears world cells,
     * attachments, durable state, or a live production command surface.
     */
    public static int clearStaleManifestsForSerialGameTest(
            MinecraftServer server, ResourceKey<Level> dimension) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(dimension, "dimension");
        if (!(server instanceof GameTestServer)) {
            throw new IllegalStateException(
                    "serial GameTest manifest reset is unavailable outside GameTestServer");
        }
        int before = LAST_MANIFEST.size();
        LAST_MANIFEST.keySet().removeIf(
                key -> key.server == server && key.dimension.equals(dimension));
        CASE_POST_USE_TEST_HOOKS.keySet().removeIf(
                key -> key.server == server && key.dimension.equals(dimension));
        return before - LAST_MANIFEST.size();
    }

    /** Derived inclusive display AABB. It has no mutation methods and is never used for ownership. */
    private static final class Bounds {
        private boolean empty = true;
        private int minX, minY, minZ, maxX, maxY, maxZ;

        private static Bounds of(Set<BlockPos> cells) {
            Bounds bounds = new Bounds();
            for (BlockPos pos : cells) {
                bounds.include(pos);
            }
            return bounds;
        }

        void include(BlockPos pos) {
            if (empty) {
                minX = maxX = pos.getX();
                minY = maxY = pos.getY();
                minZ = maxZ = pos.getZ();
                empty = false;
                return;
            }
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        @Override
        public String toString() {
            if (empty) {
                return "<empty>";
            }
            return "[" + minX + "," + minY + "," + minZ + "]..[" + maxX + "," + maxY + "," + maxZ + "]";
        }
    }
}
