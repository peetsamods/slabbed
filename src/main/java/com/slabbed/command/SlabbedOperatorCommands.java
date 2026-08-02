package com.slabbed.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.slabbed.command.SlabbedOperatorTools.Finding;
import com.slabbed.command.SlabbedOperatorTools.KitResult;
import com.slabbed.command.SlabbedOperatorTools.ScanReport;
import com.slabbed.rig.RigManifest;
import com.slabbed.rig.SlabbedRigService;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Registers the quiet, permission-gated operator tools that are safe to ship in the core jar. */
public final class SlabbedOperatorCommands {
    private static final int REQUIRED_PERMISSION = 2;

    private SlabbedOperatorCommands() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(SlabbedOperatorCommands::onRegisterCommands);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        registerTree(event.getDispatcher());
    }

    public static void registerTree(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("slabkit")
                .requires(source -> source.hasPermission(REQUIRED_PERMISSION))
                .executes(context -> runKit(context.getSource())));

        dispatcher.register(Commands.literal("slabcheck")
                .requires(source -> source.hasPermission(REQUIRED_PERMISSION))
                .executes(context -> runCheck(
                        context.getSource(), SlabbedOperatorTools.DEFAULT_RADIUS))
                .then(Commands.argument(
                                "radius",
                                IntegerArgumentType.integer(
                                        SlabbedOperatorTools.MIN_RADIUS,
                                        SlabbedOperatorTools.MAX_RADIUS))
                        .executes(context -> runCheck(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "radius")))));

        LiteralArgumentBuilder<CommandSourceStack> slabrig = Commands.literal("slabrig")
                .requires(source -> source.hasPermission(REQUIRED_PERMISSION))
                .executes(context -> runRigCatalog(context.getSource()));
        slabrig.then(Commands.literal("catalog")
                .executes(context -> runRigCatalog(context.getSource())));
        slabrig.then(Commands.literal("status")
                .executes(context -> runRigStatus(context.getSource())));
        slabrig.then(Commands.literal("clear")
                .executes(context -> runRigClear(context.getSource(), false))
                .then(Commands.literal("force")
                        .executes(context -> runRigClear(context.getSource(), true))));
        slabrig.then(Commands.literal("rows")
                .executes(context -> runRigRows(context.getSource(), false))
                .then(Commands.literal("force")
                        .executes(context -> runRigRows(context.getSource(), true))));
        LiteralArgumentBuilder<CommandSourceStack> tower = Commands.literal("tower")
                .executes(context -> runRigTower(context.getSource(), false))
                .then(Commands.literal("force")
                        .executes(context -> runRigTower(context.getSource(), true)));
        tower.then(Commands.argument(
                        "n",
                        IntegerArgumentType.integer(
                                SlabbedRigService.MIN_NUMERIC_TOWER_COUNT,
                                SlabbedRigService.MAX_NUMERIC_TOWER_COUNT))
                .executes(context -> runRigNumericTower(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "n"),
                        SlabbedRigService.DEFAULT_NUMERIC_TOWER_HEIGHT,
                        false))
                .then(Commands.literal("force")
                        .executes(context -> runRigNumericTower(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "n"),
                                SlabbedRigService.DEFAULT_NUMERIC_TOWER_HEIGHT,
                                true)))
                .then(Commands.argument(
                                "height",
                                IntegerArgumentType.integer(
                                        SlabbedRigService.MIN_NUMERIC_TOWER_HEIGHT,
                                        SlabbedRigService.MAX_NUMERIC_TOWER_HEIGHT))
                        .executes(context -> runRigNumericTower(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "n"),
                                IntegerArgumentType.getInteger(context, "height"),
                                false))
                        .then(Commands.literal("force")
                                .executes(context -> runRigNumericTower(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "n"),
                                        IntegerArgumentType.getInteger(context, "height"),
                                        true)))));
        slabrig.then(tower);
        LiteralArgumentBuilder<CommandSourceStack> stacks = Commands.literal("stacks")
                .executes(context -> runRigStacks(
                        context.getSource(),
                        SlabbedRigService.DEFAULT_STACK_MAX_LENGTH,
                        SlabbedRigService.DEFAULT_STACK_PAGE,
                        false))
                .then(Commands.literal("force")
                        .executes(context -> runRigStacks(
                                context.getSource(),
                                SlabbedRigService.DEFAULT_STACK_MAX_LENGTH,
                                SlabbedRigService.DEFAULT_STACK_PAGE,
                                true)));
        stacks.then(Commands.argument(
                        "max_length",
                        IntegerArgumentType.integer(
                                SlabbedRigService.MIN_STACK_MAX_LENGTH,
                                SlabbedRigService.MAX_STACK_MAX_LENGTH))
                .executes(context -> runRigStacks(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "max_length"),
                        SlabbedRigService.DEFAULT_STACK_PAGE,
                        false))
                .then(Commands.literal("force")
                        .executes(context -> runRigStacks(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "max_length"),
                                SlabbedRigService.DEFAULT_STACK_PAGE,
                                true)))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> runRigStacks(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "max_length"),
                                IntegerArgumentType.getInteger(context, "page"),
                                false))
                        .then(Commands.literal("force")
                                .executes(context -> runRigStacks(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "max_length"),
                                        IntegerArgumentType.getInteger(context, "page"),
                                        true)))));
        slabrig.then(stacks);
        LiteralArgumentBuilder<CommandSourceStack> mega = Commands.literal("mega")
                .executes(context -> runRigMega(
                        context.getSource(),
                        SlabbedRigService.DEFAULT_MEGA_COLUMNS,
                        false))
                .then(Commands.literal("force")
                        .executes(context -> runRigMega(
                                context.getSource(),
                                SlabbedRigService.DEFAULT_MEGA_COLUMNS,
                                true)));
        mega.then(Commands.argument(
                        "count",
                        IntegerArgumentType.integer(
                                SlabbedRigService.MIN_MEGA_COLUMNS,
                                SlabbedRigService.MAX_MEGA_COLUMNS))
                .executes(context -> runRigMega(
                        context.getSource(),
                        IntegerArgumentType.getInteger(context, "count"),
                        false))
                .then(Commands.literal("force")
                        .executes(context -> runRigMega(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "count"),
                                true))));
        slabrig.then(mega);
        slabrig.then(Commands.literal("cases")
                .executes(context -> runRigCases(context.getSource(), false))
                .then(Commands.literal("force")
                        .executes(context -> runRigCases(context.getSource(), true)))
                .then(Commands.literal("status")
                        .executes(context -> runRigCasesStatus(context.getSource())))
                .then(Commands.literal("resume")
                        .executes(context -> runRigCasesResume(context.getSource())))
                .then(Commands.literal("clear")
                        .executes(context -> runRigCasesClear(context.getSource()))));
        dispatcher.register(slabrig);
    }

    private static int runKit(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        KitResult result = SlabbedOperatorTools.grantMissing(player.getInventory());
        Component summary = Component.literal(
                "Slabbed kit: palette=" + result.paletteSize()
                        + " added=" + result.added()
                        + " already-present=" + result.alreadyPresent()
                        + " no-room=" + result.noRoom()
                        + "; existing inventory preserved.");
        if (result.noRoom() > 0) {
            summary = summary.copy().withStyle(ChatFormatting.YELLOW);
        }
        Component finalSummary = summary;
        source.sendSuccess(() -> finalSummary, false);
        return 1;
    }

    private static int runCheck(CommandSourceStack source, int radius) {
        BlockPos center = BlockPos.containing(source.getPosition());
        ScanReport report = SlabbedOperatorTools.scan(source.getLevel(), center, radius);
        if (report.largeScanWarning()) {
            long width = radius * 2L + 1L;
            long maximumCells = width * width * width;
            source.sendSuccess(
                    () -> Component.literal(
                                    "Slabbed check warning: radius " + radius
                                            + " may inspect up to " + maximumCells + " loaded cells.")
                            .withStyle(ChatFormatting.YELLOW),
                    false);
        }
        source.sendSuccess(
                () -> Component.literal(
                        "Slabbed check: center=" + report.center().toShortString()
                                + " radius=" + report.radius()
                                + " visited=" + report.visitedCells()
                                + " skipped-unloaded=" + report.skippedUnloadedCells()
                                + " examined=" + report.examinedCells()
                                + " hard-desync=" + report.hardDesync()
                                + " would-move=" + report.wouldMove()
                                + " unpinned-lowered=" + report.unpinnedLowered()),
                false);
        for (Finding finding : report.samples()) {
            source.sendSuccess(
                    () -> Component.literal(
                            "  " + finding.classification().labels()
                                    + " at " + finding.pos().toShortString()
                                    + " stored=" + formatStored(finding)
                                    + " authority=" + finding.classification().authorityDy()
                                    + " geometry=" + finding.classification().geometricDy()),
                    false);
        }
        return 1;
    }

    private static String formatStored(Finding finding) {
        return finding.classification().storedDy().present()
                ? Double.toString(finding.classification().storedDy().valueOrNaN())
                : "absent";
    }

    private static int runRigCatalog(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal(
                        "Slabbed rig catalog: available="
                                + String.join(",", SlabbedRigService.catalog())
                                + " (tower=bare|numeric); remaining=platform"
                                + " (not installed)."),
                false);
        return 1;
    }

    private static int runRigStatus(CommandSourceStack source) {
        SlabbedRigService.RigStatus status = SlabbedRigService.status(source.getLevel());
        if (!status.active()) {
            SlabbedRigService.CasesStatus cases =
                    SlabbedRigService.casesStatus(source.getLevel());
            if (cases.storePresent()) {
                source.sendSuccess(() -> casesStatusSummary(cases), false);
                return 1;
            }
            source.sendSuccess(() -> Component.literal("Slabbed rig status: no active owned rig."), false);
            return 1;
        }
        RigManifest manifest = status.manifest();
        Component summary = Component.literal(
                "Slabbed rig status: run=" + manifest.runId()
                        + " mode=" + manifest.mode()
                        + " anchor=" + manifest.anchor().toShortString()
                        + " intact=" + status.intactCells() + "/" + status.ownedCells()
                        + " conflicts=" + status.conflicts().size()
                        + " clear-eligible=" + status.clearEligible()
                        + " structural="
                        + (manifest.structuralReport().complete() ? "complete" : "incomplete")
                        + " note=" + manifest.structuralReport().note());
        if (!status.clearEligible()) {
            summary = summary.copy().withStyle(ChatFormatting.YELLOW);
        }
        Component finalSummary = summary;
        source.sendSuccess(() -> finalSummary, false);
        return 1;
    }

    private static int runRigCases(CommandSourceStack source, boolean force)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SlabbedRigService.CasesRunResult result =
                SlabbedRigService.runCases(source.getLevel(), player, force);
        if (!result.success()) {
            source.sendFailure(Component.literal(
                    "Slabbed rig cases refused: " + result.outcome()
                            + " page=" + result.page() + "/" + result.pageCount()
                            + " cells=" + result.conflicts().size()
                            + "; " + result.detail()));
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal(
                        "Slabbed rig cases completed: page="
                                + result.page() + "/" + result.pageCount()
                                + " cases=" + result.cases()
                                + " auto=" + result.autoCases()
                                + " deferred=" + result.deferredCompleted()
                                + " placed=" + result.placed()
                                + " preserved=" + result.preservedVanilla()
                                + " rejected=" + result.rejected()
                                + " law-red=" + result.lawRed()
                                + " owned=" + result.ownedCells()
                                + " force=" + force),
                false);
        return 1;
    }

    private static int runRigCasesStatus(CommandSourceStack source) {
        SlabbedRigService.CasesStatus status =
                SlabbedRigService.casesStatus(source.getLevel());
        source.sendSuccess(() -> casesStatusSummary(status), false);
        return status.evidenceStatus()
                        == com.slabbed.rig.SlabbedRigCaseEvidence.ResumeStatus.CORRUPT
                || status.evidenceStatus()
                        == com.slabbed.rig.SlabbedRigCaseEvidence.ResumeStatus.IDENTITY_MISMATCH
                ? 0 : 1;
    }

    private static Component casesStatusSummary(
            SlabbedRigService.CasesStatus status) {
        Component summary = Component.literal(
                "Slabbed cases status: evidence=" + status.evidenceStatus()
                        + " next-page=" + status.nextPage() + "/" + status.pageCount()
                        + " active=" + status.active()
                        + " ordinal=" + status.activeOrdinal()
                        + " completed-owned=" + status.completedOwnedCells()
                        + " board-present=" + status.boardPresent()
                        + " present-owned=" + status.presentOwnedCells()
                        + " intact=" + status.intactCells()
                        + " absent=" + status.absentCells()
                        + " conflicts=" + status.conflicts().size()
                        + " clear-eligible=" + status.clearEligible()
                        + " release-repair=" + status.releaseRepairEligible()
                        + "; " + status.detail());
        if (!status.conflicts().isEmpty() || status.active()) {
            summary = summary.copy().withStyle(ChatFormatting.YELLOW);
        }
        return summary;
    }

    private static int runRigCasesResume(CommandSourceStack source) {
        SlabbedRigService.CasesResumeResult result =
                SlabbedRigService.resumeCases(source.getLevel());
        if (!result.success()) {
            source.sendFailure(Component.literal(
                    "Slabbed rig cases resume refused: " + result.outcome()
                            + "; " + result.detail()));
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal(
                        "Slabbed rig cases resume: " + result.outcome()
                                + " evidence=" + result.status().evidenceStatus()
                                + " next-page=" + result.status().nextPage()),
                false);
        return 1;
    }

    private static int runRigCasesClear(CommandSourceStack source) {
        SlabbedRigService.CasesClearResult result =
                SlabbedRigService.clearCases(source.getLevel());
        if (!result.success()) {
            source.sendFailure(Component.literal(
                    "Slabbed rig cases clear refused: " + result.outcome()
                            + " cells=" + result.residualCells().size()
                            + "; " + result.detail()));
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal(
                        "Slabbed rig cases clear: " + result.outcome()
                                + " removed=" + result.removedCells()),
                false);
        return 1;
    }

    private static int runRigRows(CommandSourceStack source, boolean force)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SlabbedRigService.BuildResult result =
                SlabbedRigService.buildRows(source.getLevel(), player, force);
        if (!result.success()) {
            source.sendFailure(Component.literal(
                    "Slabbed rig rows refused: " + result.outcome()
                            + " cells=" + result.conflicts().size()
                            + "; " + result.detail()));
            return 0;
        }
        RigManifest manifest = result.manifest();
        source.sendSuccess(
                () -> Component.literal(
                        "Slabbed rig rows built: run=" + manifest.runId()
                                + " cases=" + manifest.caseIds().size()
                                + " owned=" + manifest.ownedCells().size()
                                + " proxy-useOn=" + manifest.receipt().subjectUseOnCalls()
                                + " anchor=" + manifest.anchor().toShortString()),
                false);
        return 1;
    }

    private static int runRigTower(CommandSourceStack source, boolean force)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SlabbedRigService.BuildResult result =
                SlabbedRigService.buildTower(source.getLevel(), player, force);
        if (!result.success()) {
            source.sendFailure(Component.literal(
                    "Slabbed rig tower refused: " + result.outcome()
                            + " cells=" + result.conflicts().size()
                            + "; " + result.detail()));
            return 0;
        }
        RigManifest manifest = result.manifest();
        source.sendSuccess(
                () -> Component.literal(
                        "Slabbed rig tower built: run=" + manifest.runId()
                                + " cases=" + manifest.caseIds().size()
                                + " owned=" + manifest.ownedCells().size()
                                + " fixture-truth="
                                + manifest.receipt().fixtureTruthWrites()
                                + " compound-dy=-1.0"
                                + " anchor=" + manifest.anchor().toShortString()),
                false);
        return 1;
    }

    private static int runRigNumericTower(
            CommandSourceStack source,
            int count,
            int height,
            boolean force) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SlabbedRigService.BuildResult result = SlabbedRigService.buildNumericTower(
                source.getLevel(), player, count, height, force);
        if (!result.success()) {
            source.sendFailure(Component.literal(
                    "Slabbed rig numeric tower refused: " + result.outcome()
                            + " cells=" + result.conflicts().size()
                            + "; " + result.detail()));
            return 0;
        }
        RigManifest.NumericTowerReport report =
                (RigManifest.NumericTowerReport) result.manifest().structuralReport();
        for (RigManifest.TowerColumnReport tower : report.towers()) {
            String cells = tower.cells().stream()
                    .map(cell -> BuiltInRegistries.BLOCK.getKey(cell.state().getBlock())
                            + " " + formatDouble(cell.liveDy()))
                    .collect(java.util.stream.Collectors.joining(", "));
            source.sendSuccess(
                    () -> Component.literal(
                            "tower" + (tower.index() + 1) + "(" + tower.label() + ") @"
                                    + tower.seat().toShortString()
                                    + " cells " + tower.builtCells() + "/"
                                    + report.requestedHeight()
                                    + " (seat first): " + cells),
                    false);
        }
        for (RigManifest.SeamFinding seam : report.seams()) {
            source.sendFailure(Component.literal(
                    seam.kind() + " " + seam.lowerPos().toShortString()
                            + " -> " + seam.upperPos().toShortString()
                            + " lowerTop=" + formatDouble(seam.lowerTop())
                            + " upperBottom=" + formatDouble(seam.upperBottom())
                            + " seam=" + formatSigned(seam.seam())));
        }
        String counts = report.towers().stream()
                .map(tower -> Integer.toString(tower.builtCells()))
                .collect(java.util.stream.Collectors.joining("/"));
        boolean shortTower = report.towers().stream()
                .anyMatch(tower -> tower.builtCells() != report.requestedHeight());
        source.sendSuccess(
                () -> Component.literal(
                        "Slabbed numeric tower built: run=" + result.manifest().runId()
                                + " anchor=" + result.manifest().anchor().toShortString()
                                + " towers=" + report.towers().size()
                                + " cells-built=" + counts + " of "
                                + report.requestedHeight()
                                + (shortTower ? " (some towers stalled or stayed in-cell)" : "")
                                + " gaps=" + report.gaps()
                                + " overlaps=" + report.overlaps()
                                + " structural="
                                + (report.complete() ? "complete" : "incomplete")),
                false);
        return 1;
    }

    private static int runRigStacks(
            CommandSourceStack source,
            int maxLength,
            int page,
            boolean force) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SlabbedRigService.BuildResult result = SlabbedRigService.buildStacks(
                source.getLevel(), player, maxLength, page, force);
        if (!result.success()) {
            source.sendFailure(Component.literal(
                    "Slabbed rig stacks refused: " + result.outcome()
                            + " cells=" + result.conflicts().size()
                            + "; " + result.detail()));
            return 0;
        }
        RigManifest.StackPageReport report =
                (RigManifest.StackPageReport) result.manifest().structuralReport();
        for (RigManifest.StackEntryReport stack : report.stacks()) {
            String entrySummary = stackEntrySummary(report, stack);
            source.sendSuccess(
                    () -> Component.literal(entrySummary),
                    false);
        }
        String pageSummary = stackPageSummary(result.manifest(), report);
        source.sendSuccess(
                () -> Component.literal(pageSummary),
                false);
        return 1;
    }

    private static int runRigMega(
            CommandSourceStack source,
            int columns,
            boolean force) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SlabbedRigService.BuildResult result = SlabbedRigService.buildMega(
                source.getLevel(), player, columns, force);
        if (!result.success()) {
            source.sendFailure(Component.literal(
                    "Slabbed rig mega refused: " + result.outcome()
                            + " cells=" + result.conflicts().size()
                            + "; " + result.detail()));
            return 0;
        }
        RigManifest manifest = result.manifest();
        RigManifest.MegaReport report =
                (RigManifest.MegaReport) manifest.structuralReport();
        String refused = report.refusedItemIds().isEmpty()
                ? "none" : String.join(",", report.refusedItemIds());
        Component summary = Component.literal(
                "Slabbed mega result: run=" + manifest.runId()
                        + " anchor=" + manifest.anchor().toShortString()
                        + " columns=" + report.columns()
                        + " variants=4 attempts=" + report.attempts()
                        + " placed=" + report.placed()
                        + " refused=" + report.refused()
                        + " refused-ids=" + refused
                        + " seat-check=" + (report.complete() ? "green" : "RED")
                        + " provenance=AUTO_USEON_PROXY");
        if (!report.complete()) {
            source.sendFailure(summary.copy().withStyle(ChatFormatting.RED));
            return 0;
        }
        source.sendSuccess(() -> summary, false);
        return 1;
    }

    /** Public only for the Forge-native command-output contract; this is not an external API. */
    public static String stackEntrySummary(
            RigManifest.StackPageReport report,
            RigManifest.StackEntryReport stack) {
        if (!report.stacks().contains(stack)) {
            throw new IllegalArgumentException("stack entry must belong to the reported page");
        }
        RigManifest.TowerColumnReport column = stack.column();
        List<RigManifest.SeamFinding> entrySeams = stack.seams(report.seams());
        long gaps = entrySeams.stream()
                .filter(seam -> seam.kind() == RigManifest.SeamKind.GAP)
                .count();
        long overlaps = entrySeams.size() - gaps;
        boolean complete = stack.complete(report.seams());
        String problem = complete ? "" : column.stalled()
                ? " problem=stalled"
                : column.builtCells() != stack.recipe().length()
                        ? " problem=short"
                        : " problem=seam";
        return "stack#" + (stack.catalogIndex() + 1)
                + " recipe=" + stack.recipe()
                + " seed=" + column.seat().toShortString()
                + " planned=" + stack.recipe().length()
                + " actual=" + column.builtCells()
                + " attempts=" + column.attempts()
                + " gaps=" + gaps
                + " overlaps=" + overlaps
                + " structural=" + (complete ? "complete" : "incomplete")
                + problem;
    }

    /** Public only for the Forge-native command-output contract; this is not an external API. */
    public static String stackPageSummary(
            RigManifest manifest,
            RigManifest.StackPageReport report) {
        if (!manifest.mode().equals("stacks") || !manifest.structuralReport().equals(report)) {
            throw new IllegalArgumentException("stack page summary requires its exact manifest");
        }
        return "Slabbed stacks result: run=" + manifest.runId()
                + " anchor=" + manifest.anchor().toShortString()
                + " max-length=" + report.maxLength()
                + " page=" + report.page() + "/" + report.totalPages()
                + " recipes=" + report.stacks().size() + "/" + report.totalRecipes()
                + " grid=4x4 row-major spacing=2"
                + " seed=standard_lowered_slab"
                + " gaps=" + report.gaps()
                + " overlaps=" + report.overlaps()
                + " structural=" + (report.complete() ? "complete" : "incomplete")
                + " provenance=AUTO_USEON_PROXY";
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String formatSigned(double value) {
        return String.format(Locale.ROOT, "%+.6f", value);
    }

    private static int runRigClear(CommandSourceStack source, boolean force) {
        SlabbedRigService.RigStatus generic = SlabbedRigService.status(source.getLevel());
        if (!generic.active()) {
            SlabbedRigService.CasesStatus cases =
                    SlabbedRigService.casesStatus(source.getLevel());
            if (cases.storePresent() && (cases.boardPresent() || cases.active())) {
                if (force) {
                    source.sendFailure(Component.literal(
                            "Slabbed rig clear force refused: durable cases ownership"
                                    + " permits exact clear only."));
                    return 0;
                }
                return runRigCasesClear(source);
            }
        }
        SlabbedRigService.ClearResult result = SlabbedRigService.clear(source.getLevel(), force);
        if (!result.success()) {
            source.sendFailure(Component.literal(
                    "Slabbed rig clear refused: " + result.outcome()
                            + " cells=" + result.residualCells().size()
                            + "; " + result.detail()));
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal(
                        "Slabbed rig clear: " + result.outcome()
                                + " removed=" + result.removedCells()
                                + (force ? " force=true" : " force=false")),
                false);
        return 1;
    }
}
