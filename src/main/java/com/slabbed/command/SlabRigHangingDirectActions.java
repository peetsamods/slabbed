package com.slabbed.command;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Real world-action seam for the exact B2B1 page; durable ownership remains an executor obligation. */
public final class SlabRigHangingDirectActions {

    private static final Stat<net.minecraft.world.item.Item> PAINTING_USED =
            Stats.ITEM_USED.get(Items.PAINTING);

    private SlabRigHangingDirectActions() {
    }

    public record FixtureBuild(List<SlabRigHangingDirectEvidence.CellEvidence> cells,
                               int directFixtureWrites, int playerUseOnWrites,
                               boolean playerInventoryAndStatsUntouched) {
        public FixtureBuild {
            cells = List.copyOf(cells);
        }
    }

    /** One post-write/readback receipt; the executor durably appends it before authoring the next cell. */
    public record FixtureCellWrite(SlabRigHangingDirectEvidence.CellEvidence evidence,
                                   String placementMethod,
                                   boolean playerInventoryAndStatsUntouched) {
        public FixtureCellWrite {
            Objects.requireNonNull(evidence, "evidence");
            Objects.requireNonNull(placementMethod, "placementMethod");
        }
    }

    public record PaintingAttempt(String attemptId, String interactionResult,
                                  boolean consumesAction, String stackBefore,
                                  String stackAfter, int statBefore, int statAfter,
                                  boolean playerInventoryAndStatsUntouched,
                                  SlabRigHangingDirectEntityGate.CaptureResult capture,
                                  List<SlabRigHangingDirectEvidence.PaintingEvidence> paintings,
                                  String outcome, String detail) {
        public PaintingAttempt {
            Objects.requireNonNull(attemptId, "attemptId");
            Objects.requireNonNull(interactionResult, "interactionResult");
            Objects.requireNonNull(stackBefore, "stackBefore");
            Objects.requireNonNull(stackAfter, "stackAfter");
            Objects.requireNonNull(capture, "capture");
            paintings = List.copyOf(paintings);
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /**
     * Test/batch convenience that authors every reserved fixture cell in deterministic bottom-up order
     * and readback-verifies it. Reservations are not clear authority; production persists each exact
     * post-write cell/attachment receipt before that cell may later be cleared.
     */
    public static FixtureBuild buildFixture(ServerLevel world, ServerPlayer player,
                                            SlabRigHangingDirectFixture.AbsolutePage page) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(page, "page");
        if (player.level() != world) {
            throw new IllegalArgumentException("direct fixture player/world mismatch");
        }
        for (var cell : page.clearOwnedCells()) {
            if (!world.getBlockState(cell.pos()).isAir()) {
                throw new IllegalStateException("direct fixture refuses non-air owned cell " + cell.pos());
            }
        }

        String playerBefore = playerStateFingerprint(player);
        int direct = 0;
        int proxy = 0;
        List<SlabRigHangingDirectEvidence.CellEvidence> evidence = new ArrayList<>();
        for (SlabRigHangingDirectFixture.AbsoluteCell cell : fixtureCellsInAuthoringOrder(page)) {
            FixtureCellWrite write = authorFixtureCell(world, player, cell);
            evidence.add(write.evidence());
            if ("DIRECT_FIXTURE_SET".equals(write.placementMethod())) {
                direct++;
            } else {
                proxy++;
            }
        }
        boolean untouched = playerBefore.equals(playerStateFingerprint(player));
        if (!untouched) {
            throw new IllegalStateException("fixture build changed the source player's inventory/stat state");
        }
        return new FixtureBuild(evidence, direct, proxy, true);
    }

    /**
     * Exact dependency-safe order shared by production and tests. The returned list contains every
     * clear-owned cell once; no bounds-derived or reconstructed cell may enter fixture authorship.
     */
    public static List<SlabRigHangingDirectFixture.AbsoluteCell> fixtureCellsInAuthoringOrder(
            SlabRigHangingDirectFixture.AbsolutePage page) {
        Objects.requireNonNull(page, "page");
        List<SlabRigHangingDirectFixture.AbsoluteCell> result = new ArrayList<>(
                page.clearOwnedCells().size());
        Comparator<SlabRigHangingDirectFixture.AbsoluteCell> order = Comparator
                .comparingInt((SlabRigHangingDirectFixture.AbsoluteCell cell) -> cell.pos().getY())
                .thenComparingInt(cell -> cell.pos().getX())
                .thenComparingInt(cell -> cell.pos().getZ());
        for (SlabRigHangingDirectFixture.AbsoluteCase entry : page.cases()) {
            List<SlabRigHangingDirectFixture.AbsoluteCell> oneCase = new ArrayList<>();
            oneCase.addAll(entry.topologyCells());
            oneCase.addAll(entry.backingCells());
            oneCase.sort(order);
            result.addAll(oneCase);
        }
        if (result.size() != page.clearOwnedCells().size()
                || result.stream().map(cell -> cell.pos()).distinct().count() != result.size()
                || !result.stream().map(cell -> cell.pos()).collect(java.util.stream.Collectors.toSet())
                .equals(page.clearOwnedPositionSet())) {
            throw new IllegalArgumentException("fixture authoring order lost exact clear ownership");
        }
        return List.copyOf(result);
    }

    /** Authors and readback-verifies exactly one already-reserved cell using an isolated stack. */
    public static FixtureCellWrite authorFixtureCell(ServerLevel world, ServerPlayer player,
                                                     SlabRigHangingDirectFixture.AbsoluteCell cell) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(cell, "cell");
        if (player.level() != world) {
            throw new IllegalArgumentException("fixture cell player/world mismatch");
        }
        String playerBefore = playerStateFingerprint(player);
        String method = cell.plan().placementMethod();
        if ("DIRECT_FIXTURE_SET".equals(method)) {
            setDirect(world, cell);
        } else if ("PLAYER_ITEM_USEON".equals(method)) {
            placeFixtureViaPlayer(world, player, cell);
        } else {
            throw new IllegalStateException("unknown fixture placement method " + method);
        }
        BlockState expected = SlabRigHangingDirectFixture.expectedState(cell.plan().stateRecipe());
        BlockState actual = world.getBlockState(cell.pos());
        if (!actual.equals(expected)) {
            throw new IllegalStateException("fixture readback mismatch at " + cell.pos()
                    + " expected=" + expected + " actual=" + actual);
        }
        boolean untouched = playerBefore.equals(playerStateFingerprint(player));
        if (!untouched) {
            throw new IllegalStateException("fixture cell changed source player state at " + cell.pos());
        }
        return new FixtureCellWrite(SlabRigHangingDirectEvidence.cell(world, cell.pos()), method, true);
    }

    /** Executes the exact real painting-item useOn route under the pre-insertion entity gate. */
    public static PaintingAttempt placePainting(ServerLevel world, ServerPlayer player,
                                                SlabRigHangingDirectFixture.AbsoluteCase planned,
                                                SlabRigHangingDirectEntityGate.CaptureKey captureKey) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(planned, "planned");
        Objects.requireNonNull(captureKey, "captureKey");
        if (player.level() != world || !planned.plan().attemptId().equals(captureKey.attemptId())) {
            throw new IllegalArgumentException("painting attempt player/world/capture identity mismatch");
        }

        String playerBefore = playerStateFingerprint(player);
        ItemStack attemptStack = paintingStack(world, planned.plan().selector());
        String stackBefore = stackEvidence(attemptStack);
        int statBefore = player.getStats().getValue(PAINTING_USED);
        InteractionResult result = InteractionResult.PASS;
        SlabRigHangingDirectEntityGate.CaptureScope scope =
                SlabRigHangingDirectEntityGate.openPlacement(world, captureKey);
        Throwable failure = null;
        if (!scope.result().active()) {
            // Ownership must exist before vanilla can create an entity; an inactive scope is a
            // precondition refusal, never a post-use diagnostic.
            scope.close();
        } else {
            try (scope) {
                result = attemptStack.getItem().useOn(new ExplicitStackUseOnContext(world, null,
                        InteractionHand.MAIN_HAND, attemptStack,
                        new BlockHitResult(planned.hitVector(), planned.plan().clickedFace(),
                                planned.clicked(), false)));
            } catch (Throwable throwable) {
                failure = throwable;
            }
        }

        SlabRigHangingDirectEntityGate.CaptureResult capture = scope.result();
        int statAfter = player.getStats().getValue(PAINTING_USED);
        String stackAfter = stackEvidence(attemptStack);
        boolean playerUntouched = playerBefore.equals(playerStateFingerprint(player));
        List<SlabRigHangingDirectEvidence.PaintingEvidence> paintings = new ArrayList<>();
        for (SlabRigHangingDirectEntityGate.EntityOutcome entityOutcome : capture.entities()) {
            if (entityOutcome.preclaimStatus()
                    != SlabRigHangingDirectEntityGate.PreclaimStatus.CLAIMED_ALLOW
                    || !entityOutcome.confirmed()) {
                continue;
            }
            Entity exact = world.getEntity(entityOutcome.entityUuid());
            if (exact instanceof Painting painting) {
                paintings.add(SlabRigHangingDirectEvidence.painting(world, painting));
            }
        }
        paintings.sort(Comparator.comparing(evidence -> evidence.uuid().toString()));

        String outcome;
        String detail;
        if (failure != null) {
            outcome = "QUARANTINED_THROWABLE";
            detail = failure.getClass().getName() + ":" + String.valueOf(failure.getMessage());
        } else if (!playerUntouched) {
            outcome = "ERROR_PLAYER_STATE_CHANGED";
            detail = "explicit-stack item use changed inventory or item-used statistics";
        } else if (!capture.active()) {
            outcome = "ERROR_ENTITY_GATE_INACTIVE";
            detail = "production placement ran without a durable ownership handler";
        } else if (capture.entities().stream().anyMatch(entity ->
                entity.preclaimStatus() != SlabRigHangingDirectEntityGate.PreclaimStatus.CLAIMED_ALLOW)) {
            outcome = "ERROR_PRECLAIM_VETOED";
            detail = capture.entities().toString();
        } else if (capture.entities().stream().anyMatch(entity -> !entity.confirmed())) {
            outcome = "ERROR_PRECLAIM_NOT_CONFIRMED";
            detail = capture.entities().toString();
        } else if (capture.entities().size() > 1) {
            outcome = "BOUNDED_FAILURE_MULTIPLE_ENTITIES";
            detail = "preclaimed=" + capture.entities().size();
        } else if (paintings.size() == 1) {
            SlabRigHangingDirectEvidence.PaintingEvidence evidence = paintings.getFirst();
            boolean exactAttachment = evidence.attachment().equals(planned.anchor())
                    && evidence.facing().equals(planned.plan().clickedFace().getName());
            outcome = exactAttachment && evidence.survives() ? "PLACED_SURVIVES" : "PLACED_LAW_RED";
            detail = "exactAttachment=" + exactAttachment + ";survives=" + evidence.survives();
        } else if (capture.entities().isEmpty()) {
            outcome = "VANILLA_REFUSAL";
            detail = "useOn created no insertable painting";
        } else {
            outcome = "ERROR_CONFIRMED_ENTITY_MISSING";
            detail = "confirmed preclaim no longer resolves to Painting";
        }
        return new PaintingAttempt(planned.plan().attemptId(), interactionName(result),
                result.consumesAction(), stackBefore, stackAfter, statBefore, statAfter,
                playerUntouched, capture, paintings, outcome, detail);
    }

    private static void setDirect(ServerLevel world,
                                  SlabRigHangingDirectFixture.AbsoluteCell cell) {
        if (!world.getBlockState(cell.pos()).isAir()) {
            throw new IllegalStateException("direct fixture set refuses non-air " + cell.pos());
        }
        world.setBlock(cell.pos(), SlabRigHangingDirectFixture.expectedState(cell.plan().stateRecipe()), 3);
    }

    private static void placeFixtureViaPlayer(ServerLevel world, ServerPlayer player,
                                              SlabRigHangingDirectFixture.AbsoluteCell cell) {
        if (!world.getBlockState(cell.pos()).isAir()) {
            throw new IllegalStateException("fixture useOn refuses non-air " + cell.pos());
        }
        var clicked = cell.pos().below();
        if (world.getBlockState(clicked).isAir()) {
            throw new IllegalStateException("fixture useOn has no support below " + cell.pos());
        }
        ItemStack stack = new ItemStack(SlabRigHangingDirectFixture.itemForRecipe(
                cell.plan().stateRecipe()));
        InteractionResult result = stack.getItem().useOn(new ExplicitStackUseOnContext(world, null,
                InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(clicked).add(0.0, 0.5, 0.0),
                        net.minecraft.core.Direction.UP, clicked, false)));
        if (!result.consumesAction()) {
            throw new IllegalStateException("fixture player useOn refused " + cell.pos()
                    + " result=" + result);
        }
    }

    private static ItemStack paintingStack(ServerLevel world,
                                           SlabRigHangingPaintingPlan.Selector selector) {
        ItemStack stack = new ItemStack(Items.PAINTING);
        if (selector.kind() != SlabRigHangingPaintingPlan.SelectorKind.UNPINNED) {
            Registry<PaintingVariant> registry = world.registryAccess()
                    .lookupOrThrow(Registries.PAINTING_VARIANT);
            Identifier id = Identifier.parse(selector.variantId());
            Holder.Reference<PaintingVariant> holder = registry.get(
                            ResourceKey.create(Registries.PAINTING_VARIANT, id))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "missing planned painting variant " + selector.variantId()));
            stack.set(DataComponents.PAINTING_VARIANT, holder);
        }
        return stack;
    }

    private static String stackEvidence(ItemStack stack) {
        String item = stack.isEmpty() ? "minecraft:air"
                : String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        Holder<PaintingVariant> component = stack.get(DataComponents.PAINTING_VARIANT);
        String variant = component == null ? "ABSENT" : component.unwrapKey()
                .map(key -> key.identifier().toString()).orElse("UNKEYED");
        return "item=" + item + ";count=" + stack.getCount()
                + ";minecraft:painting/variant=" + variant;
    }

    private static String interactionName(InteractionResult result) {
        if (result == InteractionResult.SUCCESS) {
            return "SUCCESS";
        }
        if (result == InteractionResult.CONSUME) {
            return "CONSUME";
        }
        if (result == InteractionResult.FAIL) {
            return "FAIL";
        }
        if (result == InteractionResult.PASS) {
            return "PASS";
        }
        return result.toString();
    }

    private static String playerStateFingerprint(ServerPlayer player) {
        StringBuilder out = new StringBuilder();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            out.append(slot).append('=').append(stackEvidence(stack)).append(';')
                    .append(stack.getComponentsPatch()).append('\n');
        }
        out.append("paintingUsed=").append(player.getStats().getValue(PAINTING_USED));
        return SlabRigHangingDirectEvidence.sha256(out.toString());
    }

    /** Protected vanilla constructor exposed only to supply an isolated synthetic stack. */
    private static final class ExplicitStackUseOnContext extends UseOnContext {
        private ExplicitStackUseOnContext(Level level, Player player, InteractionHand hand,
                                          ItemStack stack, BlockHitResult hit) {
            super(level, player, hand, stack, hit);
        }
    }
}
