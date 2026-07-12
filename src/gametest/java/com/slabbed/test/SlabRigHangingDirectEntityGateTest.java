package com.slabbed.test;

import com.slabbed.command.SlabRigHangingDirectEntityGate;
import com.slabbed.command.SlabRigHangingDirectEvidence;
import com.slabbed.command.SlabRigHangingDirectEntityGate.CaptureContext;
import com.slabbed.command.SlabRigHangingDirectEntityGate.CaptureKey;
import com.slabbed.command.SlabRigHangingDirectEntityGate.CaptureKind;
import com.slabbed.command.SlabRigHangingDirectEntityGate.CaptureResult;
import com.slabbed.command.SlabRigHangingDirectEntityGate.CaptureScope;
import com.slabbed.command.SlabRigHangingDirectEntityGate.EntityOutcome;
import com.slabbed.command.SlabRigHangingDirectEntityGate.PreclaimStatus;
import com.slabbed.command.SlabRigHangingDirectEntityGate.PreclaimDecision;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Focused lifecycle contract for direct-rig painting and dropped-item ownership. */
public final class SlabRigHangingDirectEntityGateTest {

    private static final Set<UUID> LATER_VETO_UUIDS = ConcurrentHashMap.newKeySet();
    private static boolean laterVetoRegistered;

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placementScopePreclaimsConfirmsNestsAndFinallyClears(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        TestHandler handler = new TestHandler();
        try (var ignored = SlabRigHangingDirectEntityGate.openTestHandlerOverride(world, handler)) {
            // Repeated registration must not duplicate either callback.
            SlabRigHangingDirectEntityGate.register();
            SlabRigHangingDirectEntityGate.register();

            CaptureKey directKey = new CaptureKey("gate-test", "placement-direct");
            Painting direct = painting(world, helper.absolutePos(new BlockPos(1, 3, 1)));
            CaptureScope directScope = SlabRigHangingDirectEntityGate.openPlacement(world, directKey);
            try (directScope) {
                if (!world.addFreshEntity(direct)) {
                    throw helper.assertionException("accepted placement was not inserted");
                }
            }
            assertAcceptedAndConfirmed(helper, directScope.result(), directKey, direct);
            if (handler.preclaimsFor(directKey).size() != 1
                    || handler.confirmationsFor(directKey).size() != 1
                    || handler.preclaimsFor(directKey).getFirst().context()
                    != directScope.result().context()
                    || handler.preclaimsFor(directKey).getFirst().level() != world) {
                throw helper.assertionException("one registration produced duplicate callbacks");
            }

            CaptureKey outerKey = new CaptureKey("gate-test", "nested-outer");
            CaptureKey innerKey = new CaptureKey("gate-test", "nested-inner");
            Painting outerPainting = painting(world, helper.absolutePos(new BlockPos(2, 3, 1)));
            Painting innerPainting = painting(world, helper.absolutePos(new BlockPos(3, 3, 1)));
            CaptureScope outer = SlabRigHangingDirectEntityGate.openPlacement(world, outerKey);
            try (outer) {
                CaptureScope inner = SlabRigHangingDirectEntityGate.openPlacement(world, innerKey);
                try (inner) {
                    if (!world.addFreshEntity(innerPainting)) {
                        throw helper.assertionException("nested inner placement was not inserted");
                    }
                }
                assertAcceptedAndConfirmed(helper, inner.result(), innerKey, innerPainting);
                if (!world.addFreshEntity(outerPainting)) {
                    throw helper.assertionException("restored outer placement was not inserted");
                }
            }
            assertAcceptedAndConfirmed(helper, outer.result(), outerKey, outerPainting);

            CaptureKey throwingKey = new CaptureKey("gate-test", "finally-cleanup");
            CaptureScope throwing = SlabRigHangingDirectEntityGate.openPlacement(world, throwingKey);
            RuntimeException marker = new RuntimeException("scope marker");
            try {
                try (throwing) {
                    throw marker;
                }
            } catch (RuntimeException caught) {
                if (caught != marker) {
                    throw caught;
                }
            }
            if (!throwing.result().closed()) {
                throw helper.assertionException("throwing placement scope did not close in finally");
            }
            int preclaimsBefore = handler.preclaims.size();
            Painting afterFinally = painting(world, helper.absolutePos(new BlockPos(4, 3, 1)));
            if (!ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
                    afterFinally, world, null, false)) {
                throw helper.assertionException("closed context leaked a veto outside its scope");
            }
            if (handler.preclaims.size() != preclaimsBefore) {
                throw helper.assertionException("closed ThreadLocal context leaked after finally");
            }

            direct.discard();
            innerPainting.discard();
            outerPainting.discard();
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void defaultDiskMismatchAndVetoBoundariesStayExact(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        TestHandler handler = new TestHandler();
        try (var ignored = SlabRigHangingDirectEntityGate.openTestHandlerOverride(world, handler)) {
            ItemEntity noContext = item(world, helper.absolutePos(new BlockPos(1, 3, 3)));
            if (!ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
                    noContext, world, null, false) || !handler.preclaims.isEmpty()) {
                throw helper.assertionException("no-context entity load was not default-allow/no-op");
            }

            CaptureKey mismatchKey = new CaptureKey("gate-test", "mismatch");
            handler.decisions.put(mismatchKey, Decision.THROW);
            CaptureScope mismatch = SlabRigHangingDirectEntityGate.openPlacement(world, mismatchKey);
            try (mismatch) {
                ItemEntity foreignType = item(world, helper.absolutePos(new BlockPos(2, 3, 3)));
                if (!ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
                        foreignType, world, null, false)) {
                    throw helper.assertionException("mismatched ItemEntity was vetoed by placement context");
                }
            }
            if (!mismatch.result().entities().isEmpty()
                    || !handler.preclaimsFor(mismatchKey).isEmpty()) {
                throw helper.assertionException("mismatched entity reached the handler");
            }

            CaptureKey diskKey = new CaptureKey("gate-test", "loaded-disk");
            handler.decisions.put(diskKey, Decision.THROW);
            Painting diskPainting = painting(world, helper.absolutePos(new BlockPos(3, 3, 3)));
            CaptureScope disk = SlabRigHangingDirectEntityGate.openPlacement(world, diskKey);
            try (disk) {
                if (!ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(
                        diskPainting, world, EntitySpawnReason.LOAD, true)) {
                    throw helper.assertionException("loaded-from-disk painting was vetoed");
                }
            }
            if (!disk.result().entities().isEmpty() || !handler.preclaimsFor(diskKey).isEmpty()) {
                throw helper.assertionException("loaded-from-disk painting reached preclaim");
            }

            CaptureKey falseKey = new CaptureKey("gate-test", "false-veto");
            handler.decisions.put(falseKey, Decision.REJECT);
            Painting rejected = painting(world, helper.absolutePos(new BlockPos(4, 3, 3)));
            CaptureScope falseScope = SlabRigHangingDirectEntityGate.openPlacement(world, falseKey);
            boolean falseInserted;
            try (falseScope) {
                falseInserted = world.addFreshEntity(rejected);
            }
            assertVeto(helper, world, falseScope.result(), falseKey, rejected,
                    falseInserted, PreclaimStatus.REJECTED);

            CaptureKey claimedVetoKey = new CaptureKey("gate-test", "claimed-veto");
            handler.decisions.put(claimedVetoKey, Decision.CLAIM_VETO);
            Painting claimedVeto = painting(world, helper.absolutePos(new BlockPos(4, 3, 4)));
            CaptureScope claimedVetoScope = SlabRigHangingDirectEntityGate.openPlacement(
                    world, claimedVetoKey);
            boolean claimedVetoInserted;
            try (claimedVetoScope) {
                claimedVetoInserted = world.addFreshEntity(claimedVeto);
            }
            assertVeto(helper, world, claimedVetoScope.result(), claimedVetoKey, claimedVeto,
                    claimedVetoInserted, PreclaimStatus.CLAIMED_VETO);

            CaptureKey throwKey = new CaptureKey("gate-test", "exception-veto");
            handler.decisions.put(throwKey, Decision.THROW);
            Painting threw = painting(world, helper.absolutePos(new BlockPos(5, 3, 3)));
            CaptureScope throwScope = SlabRigHangingDirectEntityGate.openPlacement(world, throwKey);
            boolean throwInserted;
            try (throwScope) {
                throwInserted = world.addFreshEntity(threw);
            }
            assertVeto(helper, world, throwScope.result(), throwKey, threw,
                    throwInserted, PreclaimStatus.EXCEPTION);

            CaptureKey errorKey = new CaptureKey("gate-test", "error-veto");
            handler.decisions.put(errorKey, Decision.ERROR_PRECLAIM);
            Painting errored = painting(world, helper.absolutePos(new BlockPos(5, 3, 4)));
            CaptureScope errorScope = SlabRigHangingDirectEntityGate.openPlacement(world, errorKey);
            boolean errorInserted;
            try (errorScope) {
                errorInserted = world.addFreshEntity(errored);
            }
            assertVeto(helper, world, errorScope.result(), errorKey, errored,
                    errorInserted, PreclaimStatus.EXCEPTION);

            CaptureKey confirmErrorKey = new CaptureKey("gate-test", "confirmation-error");
            handler.decisions.put(confirmErrorKey, Decision.ERROR_CONFIRM);
            Painting confirmationErrored = painting(world, helper.absolutePos(new BlockPos(6, 3, 4)));
            CaptureScope confirmErrorScope = SlabRigHangingDirectEntityGate.openPlacement(
                    world, confirmErrorKey);
            try (confirmErrorScope) {
                if (!world.addFreshEntity(confirmationErrored)) {
                    throw helper.assertionException("confirmation-error premise failed to insert");
                }
            }
            EntityOutcome confirmationError = onlyOutcome(helper, confirmErrorScope.result(),
                    confirmErrorKey);
            if (confirmationError.preclaimStatus() != PreclaimStatus.CLAIMED_ALLOW
                    || !confirmationError.confirmationAttempted() || confirmationError.confirmed()
                    || confirmationError.confirmationFailure().isEmpty()) {
                throw helper.assertionException("confirmation Error escaped typed failure evidence: "
                        + confirmationError);
            }
            confirmationErrored.discard();

            registerLaterVeto();
            CaptureKey laterKey = new CaptureKey("gate-test", "later-listener-veto");
            Painting later = painting(world, helper.absolutePos(new BlockPos(6, 3, 3)));
            LATER_VETO_UUIDS.add(later.getUUID());
            CaptureScope laterScope = SlabRigHangingDirectEntityGate.openPlacement(world, laterKey);
            boolean laterInserted;
            try (laterScope) {
                laterInserted = world.addFreshEntity(later);
            }
            if (laterInserted || world.getEntity(later.getUUID()) != null) {
                throw helper.assertionException("conditional later listener did not veto insertion");
            }
            EntityOutcome laterOutcome = onlyOutcome(helper, laterScope.result(), laterKey);
            if (laterOutcome.preclaimStatus() != PreclaimStatus.CLAIMED_ALLOW
                    || laterOutcome.confirmationAttempted() || laterOutcome.confirmed()) {
                throw helper.assertionException(
                        "later-listener veto was not observable as accepted-but-unconfirmed: "
                                + laterOutcome);
            }
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void paintingDropMixinHandsOffExactSourceAndLeavesForeignOperationNeutral(
            GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        TestHandler handler = new TestHandler();
        try (var ignored = SlabRigHangingDirectEntityGate.openTestHandlerOverride(world, handler)) {
            CaptureKey dropKey = new CaptureKey("gate-test", "owned-drop");
            Painting source = painting(world, helper.absolutePos(new BlockPos(2, 4, 5)));
            source.discard();
            handler.ownedDrops.put(source.getUUID(), dropKey);
            handler.decisions.put(dropKey, Decision.CLAIM_VETO);

            source.dropItem(world, null);

            if (handler.beginCallbacksFor(dropKey).size() != 1
                    || handler.beginCallbacksFor(dropKey).getFirst().source() != source
                    || handler.beginCallbacksFor(dropKey).getFirst().level() != world
                    || handler.beginCallbacksFor(dropKey).getFirst().removalReason()
                    != Entity.RemovalReason.DISCARDED
                    || !handler.beginCallbacksFor(dropKey).getFirst().evidence().uuid()
                    .equals(source.getUUID())
                    || !handler.beginCallbacksFor(dropKey).getFirst().evidence().removed()
                    || !"DISCARDED".equals(handler.beginCallbacksFor(dropKey).getFirst()
                    .evidence().removalReason())) {
                throw helper.assertionException(
                        "drop begin callback did not receive the exact post-discard source painting");
            }
            List<Callback> preclaims = handler.preclaimsFor(dropKey);
            List<Callback> confirmations = handler.confirmationsFor(dropKey);
            List<CompletionCallback> completions = handler.completionsFor(dropKey);
            if (preclaims.size() != 1 || !confirmations.isEmpty()
                    || completions.size() != 1 || completions.getFirst().level() != world
                    || !completions.getFirst().result().closed()
                    || completions.getFirst().result().entities().size() != 1
                    || completions.getFirst().result().entities().getFirst().preclaimStatus()
                    != PreclaimStatus.CLAIMED_VETO
                    || !(preclaims.getFirst().entity() instanceof ItemEntity dropped)
                    || preclaims.getFirst().context().kind() != CaptureKind.DROP
                    || !preclaims.getFirst().context().sourcePaintingUuid()
                    .equals(Optional.of(source.getUUID()))
                    || world.getEntity(dropped.getUUID()) != null) {
                throw helper.assertionException(
                        "drop mixin did not claim-and-veto the exact ItemEntity before insertion: pre="
                                + preclaims + " confirm=" + confirmations);
            }
            SlabRigHangingDirectEvidence.ItemEvidence itemEvidence =
                    SlabRigHangingDirectEvidence.item(world, dropped);
            if (!itemEvidence.uuid().equals(dropped.getUUID())
                    || !"minecraft:item".equals(itemEvidence.type())
                    || !"minecraft:painting".equals(itemEvidence.itemId())
                    || itemEvidence.count() != 1
                    || !itemEvidence.stackSha256().matches("[0-9a-f]{64}")
                    || !itemEvidence.nbtSha256().matches("[0-9a-f]{64}")
                    || !itemEvidence.identityFingerprint().matches("[0-9a-f]{64}")
                    || !itemEvidence.position().equals(
                    SlabRigHangingDirectEvidence.VecBits.of(dropped.position()))
                    || !itemEvidence.bounds().equals(
                    SlabRigHangingDirectEvidence.BoxBits.of(dropped.getBoundingBox()))
                    || !itemEvidence.equals(SlabRigHangingDirectEvidence.item(world, dropped))) {
                throw helper.assertionException(
                        "vetoed drop did not retain stable full ItemEvidence: " + itemEvidence);
            }

            CaptureKey errorKey = new CaptureKey("gate-test", "owned-drop-error");
            Painting errorSource = painting(world, helper.absolutePos(new BlockPos(3, 4, 5)));
            errorSource.discard();
            handler.ownedDrops.put(errorSource.getUUID(), errorKey);
            handler.decisions.put(errorKey, Decision.ERROR_PRECLAIM);
            errorSource.dropItem(world, null);
            List<CompletionCallback> errorCompletions = handler.completionsFor(errorKey);
            if (errorCompletions.size() != 1
                    || errorCompletions.getFirst().result().entities().size() != 1
                    || errorCompletions.getFirst().result().entities().getFirst().preclaimStatus()
                    != PreclaimStatus.EXCEPTION
                    || errorCompletions.getFirst().result().entities().getFirst()
                    .preclaimFailure().isEmpty()) {
                throw helper.assertionException(
                        "caught drop-preclaim Error was not reported through the closed scope: "
                                + errorCompletions);
            }

            CaptureKey beginErrorKey = new CaptureKey("gate-test", "owned-drop-begin-error");
            Painting beginErrorSource = painting(world, helper.absolutePos(new BlockPos(3, 4, 6)));
            beginErrorSource.discard();
            handler.ownedDrops.put(beginErrorSource.getUUID(), beginErrorKey);
            handler.beginErrors.add(beginErrorSource.getUUID());
            AtomicInteger beginErrorOriginalCalls = new AtomicInteger();
            ItemEntity beginErrorResult = SlabRigHangingDirectEntityGate.capturePaintingDrop(
                    beginErrorSource, world, () -> {
                        beginErrorOriginalCalls.incrementAndGet();
                        return item(world, helper.absolutePos(new BlockPos(3, 4, 7)));
                    });
            if (beginErrorResult != null || beginErrorOriginalCalls.get() != 0
                    || handler.beginFailuresFor(beginErrorKey).size() != 1
                    || !(handler.beginFailuresFor(beginErrorKey).getFirst().failure()
                    instanceof AssertionError)) {
                throw helper.assertionException(
                        "owned begin-drop Error escaped or spawned an unevidenced item: "
                                + handler.beginFailuresFor(beginErrorKey));
            }

            Painting foreignBeginError = painting(world,
                    helper.absolutePos(new BlockPos(3, 4, 10)));
            handler.beginErrors.add(foreignBeginError.getUUID());
            ItemEntity foreignBeginSentinel = item(world,
                    helper.absolutePos(new BlockPos(3, 4, 11)));
            AtomicInteger foreignBeginOriginalCalls = new AtomicInteger();
            ItemEntity foreignBeginResult = SlabRigHangingDirectEntityGate.capturePaintingDrop(
                    foreignBeginError, world, () -> {
                        foreignBeginOriginalCalls.incrementAndGet();
                        return foreignBeginSentinel;
                    });
            if (foreignBeginResult != foreignBeginSentinel
                    || foreignBeginOriginalCalls.get() != 1) {
                throw helper.assertionException(
                        "foreign begin-drop failure did not remain vanilla-neutral");
            }

            CaptureKey selfSuppressKey = new CaptureKey("gate-test", "owned-drop-self-suppress");
            Painting selfSuppressSource = painting(world,
                    helper.absolutePos(new BlockPos(3, 4, 8)));
            selfSuppressSource.discard();
            handler.ownedDrops.put(selfSuppressSource.getUUID(), selfSuppressKey);
            handler.beginErrors.add(selfSuppressSource.getUUID());
            handler.beginFailureRethrows.add(selfSuppressSource.getUUID());
            AtomicInteger selfSuppressOriginalCalls = new AtomicInteger();
            ItemEntity selfSuppressResult = SlabRigHangingDirectEntityGate.capturePaintingDrop(
                    selfSuppressSource, world, () -> {
                        selfSuppressOriginalCalls.incrementAndGet();
                        return item(world, helper.absolutePos(new BlockPos(3, 4, 9)));
                    });
            if (selfSuppressResult != null || selfSuppressOriginalCalls.get() != 0) {
                throw helper.assertionException(
                        "same-Throwable begin-failure callback escaped or spawned an item");
            }

            CaptureKey wrongAllowKey = new CaptureKey("gate-test", "owned-drop-wrong-allow");
            Painting wrongAllowSource = painting(world,
                    helper.absolutePos(new BlockPos(4, 4, 8)));
            wrongAllowSource.discard();
            handler.ownedDrops.put(wrongAllowSource.getUUID(), wrongAllowKey);
            wrongAllowSource.dropItem(world, null);
            Callback wrongAllowPreclaim = handler.preclaimsFor(wrongAllowKey).getFirst();
            EntityOutcome wrongAllowOutcome = handler.completionsFor(wrongAllowKey).getFirst()
                    .result().entities().getFirst();
            if (wrongAllowOutcome.preclaimStatus() != PreclaimStatus.REJECTED
                    || wrongAllowOutcome.preclaimFailure().isEmpty()
                    || world.getEntity(wrongAllowPreclaim.entity().getUUID()) != null) {
                throw helper.assertionException(
                        "DROP handler ALLOW escaped mandatory claim-and-veto backstop: "
                                + wrongAllowOutcome);
            }

            CaptureKey completionErrorKey = new CaptureKey(
                    "gate-test", "owned-drop-completion-error");
            Painting completionErrorSource = painting(world,
                    helper.absolutePos(new BlockPos(4, 4, 9)));
            completionErrorSource.discard();
            handler.ownedDrops.put(completionErrorSource.getUUID(), completionErrorKey);
            handler.decisions.put(completionErrorKey, Decision.CLAIM_VETO);
            handler.completionErrors.add(completionErrorKey);
            completionErrorSource.dropItem(world, null);
            Callback completionErrorPreclaim = handler.preclaimsFor(completionErrorKey).getFirst();
            if (world.getEntity(completionErrorPreclaim.entity().getUUID()) != null) {
                throw helper.assertionException(
                        "completion Error reopened a vetoed drop insertion");
            }

            int callbacksBeforeForeign = handler.preclaims.size();
            Painting foreign = painting(world, helper.absolutePos(new BlockPos(4, 4, 5)));
            ItemEntity sentinel = item(world, helper.absolutePos(new BlockPos(4, 4, 6)));
            AtomicInteger originalCalls = new AtomicInteger();
            ItemEntity returned = SlabRigHangingDirectEntityGate.capturePaintingDrop(
                    foreign, world, () -> {
                        originalCalls.incrementAndGet();
                        return sentinel;
                    });
            if (returned != sentinel || originalCalls.get() != 1
                    || handler.preclaims.size() != callbacksBeforeForeign) {
                throw helper.assertionException(
                        "foreign/no-matching-handler drop did not remain a neutral original call");
            }

            ItemEntity afterDrop = item(world, helper.absolutePos(new BlockPos(5, 4, 6)));
            if (!ServerEntityEvents.ALLOW_LOAD.invoker().onAllowLoad(afterDrop, world, null, false)
                    || handler.preclaims.size() != callbacksBeforeForeign) {
                throw helper.assertionException("drop context leaked beyond exact spawnAtLocation call");
            }
        }
        helper.succeed();
    }

    private static synchronized void registerLaterVeto() {
        if (laterVetoRegistered) {
            return;
        }
        ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, loadedFromDisk) ->
                loadedFromDisk || !LATER_VETO_UUIDS.remove(entity.getUUID()));
        laterVetoRegistered = true;
    }

    private static void assertAcceptedAndConfirmed(GameTestHelper helper, CaptureResult result,
                                                   CaptureKey key, Entity entity) {
        EntityOutcome outcome = onlyOutcome(helper, result, key);
        if (!result.active() || !result.closed() || !outcome.entityUuid().equals(entity.getUUID())
                || outcome.preclaimStatus() != PreclaimStatus.CLAIMED_ALLOW
                || !outcome.confirmationAttempted() || !outcome.confirmed()
                || outcome.preclaimFailure().isPresent()
                || outcome.confirmationFailure().isPresent()) {
            throw helper.assertionException("accepted entity outcome drifted: " + result);
        }
    }

    private static void assertVeto(GameTestHelper helper, ServerLevel world, CaptureResult result,
                                   CaptureKey key, Entity entity, boolean inserted,
                                   PreclaimStatus expected) {
        EntityOutcome outcome = onlyOutcome(helper, result, key);
        if (inserted || world.getEntity(entity.getUUID()) != null
                || outcome.preclaimStatus() != expected
                || outcome.confirmationAttempted() || outcome.confirmed()
                || outcome.preclaimFailure().isEmpty()) {
            throw helper.assertionException("preclaim veto did not stop exact insertion: " + result);
        }
    }

    private static EntityOutcome onlyOutcome(GameTestHelper helper, CaptureResult result,
                                             CaptureKey key) {
        if (!result.context().key().equals(key) || result.entities().size() != 1) {
            throw helper.assertionException("expected one exact outcome for " + key + ": " + result);
        }
        return result.entities().getFirst();
    }

    private static Painting painting(ServerLevel world, BlockPos anchor) {
        Registry<PaintingVariant> registry = world.registryAccess()
                .lookupOrThrow(Registries.PAINTING_VARIANT);
        Identifier kebab = Identifier.parse("minecraft:kebab");
        Holder.Reference<PaintingVariant> variant = registry.get(
                        ResourceKey.create(Registries.PAINTING_VARIANT, kebab))
                .orElseThrow(() -> new IllegalStateException("missing minecraft:kebab"));
        return new Painting(world, anchor, Direction.NORTH, variant);
    }

    private static ItemEntity item(ServerLevel world, BlockPos pos) {
        return new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                new ItemStack(Items.PAINTING));
    }

    private enum Decision {
        ACCEPT,
        CLAIM_VETO,
        REJECT,
        THROW,
        ERROR_PRECLAIM,
        ERROR_CONFIRM
    }

    private record Callback(CaptureContext context, Entity entity, ServerLevel level) {
    }

    private record BeginCallback(Painting source, ServerLevel level,
                                 Entity.RemovalReason removalReason,
                                 SlabRigHangingDirectEvidence.PaintingEvidence evidence) {
    }

    private record CompletionCallback(CaptureResult result, ServerLevel level) {
    }

    private record BeginFailureCallback(Painting source, ServerLevel level, Throwable failure) {
    }

    private static final class TestHandler implements SlabRigHangingDirectEntityGate.Handler {
        private final Map<CaptureKey, Decision> decisions = new HashMap<>();
        private final Map<UUID, CaptureKey> ownedDrops = new HashMap<>();
        private final Map<CaptureKey, List<BeginCallback>> beginCallbacks = new HashMap<>();
        private final Map<CaptureKey, List<BeginFailureCallback>> beginFailures = new HashMap<>();
        private final Set<UUID> beginErrors = ConcurrentHashMap.newKeySet();
        private final Set<UUID> beginFailureRethrows = ConcurrentHashMap.newKeySet();
        private final Set<CaptureKey> completionErrors = ConcurrentHashMap.newKeySet();
        private final List<Callback> preclaims = new ArrayList<>();
        private final List<Callback> confirmations = new ArrayList<>();
        private final List<CompletionCallback> completions = new ArrayList<>();

        @Override
        public Optional<CaptureKey> beginPaintingDrop(Painting source, ServerLevel level) {
            CaptureKey key = ownedDrops.get(source.getUUID());
            if (beginErrors.contains(source.getUUID())) {
                throw new AssertionError("synthetic begin-drop Error");
            }
            if (key != null) {
                beginCallbacks.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(new BeginCallback(source, level, source.getRemovalReason(),
                                SlabRigHangingDirectEvidence.painting(level, source)));
            }
            return Optional.ofNullable(key);
        }

        @Override
        public boolean beginFailure(Painting source, ServerLevel level, Throwable failure) {
            if (beginFailureRethrows.contains(source.getUUID())) {
                if (failure instanceof Error error) {
                    throw error;
                }
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(failure);
            }
            CaptureKey key = ownedDrops.get(source.getUUID());
            if (key == null) {
                return false;
            }
            beginFailures.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new BeginFailureCallback(source, level, failure));
            return true;
        }

        @Override
        public PreclaimDecision preclaim(CaptureContext context, Entity entity, ServerLevel level) {
            preclaims.add(new Callback(context, entity, level));
            return switch (decisions.getOrDefault(context.key(), Decision.ACCEPT)) {
                case ACCEPT -> PreclaimDecision.CLAIM_AND_ALLOW;
                case CLAIM_VETO -> PreclaimDecision.CLAIM_AND_VETO;
                case REJECT -> PreclaimDecision.REJECT;
                case THROW -> throw new IllegalStateException("synthetic preclaim failure");
                case ERROR_PRECLAIM -> throw new AssertionError("synthetic preclaim Error");
                case ERROR_CONFIRM -> PreclaimDecision.CLAIM_AND_ALLOW;
            };
        }

        @Override
        public void confirm(CaptureContext context, Entity entity, ServerLevel level) {
            if (decisions.get(context.key()) == Decision.ERROR_CONFIRM) {
                throw new AssertionError("synthetic confirmation Error");
            }
            confirmations.add(new Callback(context, entity, level));
        }

        @Override
        public void complete(CaptureResult result, ServerLevel level) {
            completions.add(new CompletionCallback(result, level));
            if (completionErrors.contains(result.context().key())) {
                throw new AssertionError("synthetic completion Error");
            }
        }

        private List<Callback> preclaimsFor(CaptureKey key) {
            return preclaims.stream().filter(callback -> callback.context().key().equals(key)).toList();
        }

        private List<Callback> confirmationsFor(CaptureKey key) {
            return confirmations.stream()
                    .filter(callback -> callback.context().key().equals(key)).toList();
        }

        private List<BeginCallback> beginCallbacksFor(CaptureKey key) {
            return beginCallbacks.getOrDefault(key, List.of());
        }

        private List<CompletionCallback> completionsFor(CaptureKey key) {
            return completions.stream()
                    .filter(callback -> callback.result().context().key().equals(key)).toList();
        }


        private List<BeginFailureCallback> beginFailuresFor(CaptureKey key) {
            return beginFailures.getOrDefault(key, List.of());
        }
    }
}
