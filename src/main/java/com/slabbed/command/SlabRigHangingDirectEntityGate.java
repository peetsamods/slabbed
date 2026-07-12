package com.slabbed.command;

import com.slabbed.Slabbed;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Exact, pre-insertion ownership gate for the two entity-producing portions of a direct hanging
 * rig attempt: the placed {@link Painting}, then that painting's dropped {@link ItemEntity}.
 *
 * <p>The gate deliberately has no world-search fallback. A future executor opens a placement scope
 * around the one placement operation. The painting drop mixin opens the corresponding drop scope
 * around the one vanilla {@code spawnAtLocation} invocation. Fabric's {@code ALLOW_LOAD} callback
 * lets the installed handler persist ownership before the entity enters the world; {@code
 * ENTITY_LOAD} confirms only after tracking begins. If another later {@code ALLOW_LOAD} listener
 * vetoes insertion, the accepted-but-unconfirmed outcome remains visible in the closed scope.
 */
public final class SlabRigHangingDirectEntityGate {

    private static final Object REGISTRATION_LOCK = new Object();
    private static final ThreadLocal<ArrayDeque<Frame>> ACTIVE_FRAMES = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<HandlerOverrideFrame>> TEST_HANDLER_OVERRIDES =
            new ThreadLocal<>();

    private static volatile Handler installedHandler;
    private static boolean registered;

    private SlabRigHangingDirectEntityGate() {
    }

    /** The exact entity-producing operation currently owned by the direct-rig executor. */
    public enum CaptureKind {
        PLACEMENT,
        DROP
    }

    /** Stable executor identity handed unchanged through begin, preclaim, and confirmation. */
    public record CaptureKey(String runId, String attemptId) {
        public CaptureKey {
            runId = requireText(runId, "runId");
            attemptId = requireText(attemptId, "attemptId");
        }
    }

    /**
     * Public callback context. World identity is also passed as the exact {@link ServerLevel}
     * callback argument; this value records the dimension and source-painting relationship.
     */
    public record CaptureContext(CaptureKey key, CaptureKind kind,
                                 ResourceKey<Level> dimension,
                                 Optional<UUID> sourcePaintingUuid) {
        public CaptureContext {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(dimension, "dimension");
            sourcePaintingUuid = Objects.requireNonNull(sourcePaintingUuid,
                    "sourcePaintingUuid");
            if (kind == CaptureKind.PLACEMENT && sourcePaintingUuid.isPresent()) {
                throw new IllegalArgumentException("placement context cannot have a source painting");
            }
            if (kind == CaptureKind.DROP && sourcePaintingUuid.isEmpty()) {
                throw new IllegalArgumentException("drop context requires a source painting");
            }
        }
    }

    /** Result of the one pre-insertion handler decision for an exact entity UUID. */
    public enum PreclaimStatus {
        CLAIMED_ALLOW,
        CLAIMED_VETO,
        REJECTED,
        EXCEPTION
    }

    /** Typed durable decision: a rig drop can be evidenced while deliberately never entering the world. */
    public enum PreclaimDecision {
        CLAIM_AND_ALLOW(true),
        CLAIM_AND_VETO(false),
        REJECT(false);

        private final boolean allowInsertion;

        PreclaimDecision(boolean allowInsertion) {
            this.allowInsertion = allowInsertion;
        }

        public boolean allowInsertion() {
            return allowInsertion;
        }
    }

    /** Immutable executor-visible result for one matching entity encountered by a scope. */
    public record EntityOutcome(UUID entityUuid, String entityClass,
                                PreclaimStatus preclaimStatus,
                                boolean confirmationAttempted, boolean confirmed,
                                Optional<String> preclaimFailure,
                                Optional<String> confirmationFailure) {
        public EntityOutcome {
            Objects.requireNonNull(entityUuid, "entityUuid");
            Objects.requireNonNull(entityClass, "entityClass");
            Objects.requireNonNull(preclaimStatus, "preclaimStatus");
            preclaimFailure = Objects.requireNonNull(preclaimFailure, "preclaimFailure");
            confirmationFailure = Objects.requireNonNull(confirmationFailure,
                    "confirmationFailure");
        }
    }

    /** Snapshot available both during and after an executor-owned scope. */
    public record CaptureResult(CaptureContext context, boolean active, boolean closed,
                                List<EntityOutcome> entities) {
        public CaptureResult {
            Objects.requireNonNull(context, "context");
            entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        }
    }

    /**
     * Installed exactly once by the future direct-rig executor.
     *
     * <p>{@link #beginPaintingDrop(Painting, ServerLevel)} runs synchronously inside {@link
     * Painting#dropItem(ServerLevel, Entity)}, before vanilla creates its item. This is the exact
     * boundary at which the executor can snapshot the source painting's post-discard fields (typed
     * removal reason, survival, variant/component, attachment/facing, position, bounds, and NBT)
     * without a world lookup. Returning empty leaves a foreign painting's vanilla drop untouched.
     */
    public interface Handler {
        Optional<CaptureKey> beginPaintingDrop(Painting source, ServerLevel level) throws Exception;

        /**
         * Handles a failure before a drop scope can be opened. Return {@code true} only when the
         * exact source UUID is already owned and the failure was durably quarantined; the gate then
         * vetoes the unevidenced item spawn. Foreign sources return {@code false} and remain vanilla.
         */
        default boolean beginFailure(Painting source, ServerLevel level, Throwable failure) {
            return false;
        }

        PreclaimDecision preclaim(CaptureContext context, Entity entity, ServerLevel level) throws Exception;

        void confirm(CaptureContext context, Entity entity, ServerLevel level) throws Exception;

        /**
         * Receives the closed exact scope, including failures caught by the pre-insertion callbacks.
         * Implementations must absorb their own failures: this callback runs inside vanilla's entity
         * lifecycle and must never throw through the server tick.
         */
        default void complete(CaptureResult result, ServerLevel level) {
        }
    }

    /**
     * Installs the process-wide executor bridge. Reinstalling the same object is idempotent; a
     * different handler is rejected so two owners cannot race to classify the same insertion.
     */
    public static void installHandler(Handler handler) {
        Objects.requireNonNull(handler, "handler");
        synchronized (REGISTRATION_LOCK) {
            if (installedHandler == null) {
                installedHandler = handler;
            } else if (installedHandler != handler) {
                throw new IllegalStateException("direct hanging entity handler is already installed");
            }
            registerLocked();
        }
    }

    /** Registers the Fabric callbacks once, independently of handler installation order. */
    public static void register() {
        synchronized (REGISTRATION_LOCK) {
            registerLocked();
        }
    }

    /**
     * Test-only seam which delegates this exact server-thread/world scope to a fake handler without
     * replacing the process-wide production singleton. Tests must close it with try-with-resources.
     */
    public static HandlerOverride openTestHandlerOverride(ServerLevel level, Handler handler) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(handler, "handler");
        return openTestHandlerFrame(level, handler, false);
    }

    /** Test-only seam proving callers refuse before mutation when no ownership handler is active. */
    public static HandlerOverride openTestHandlerDisabled(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return openTestHandlerFrame(level, null, true);
    }

    private static HandlerOverride openTestHandlerFrame(ServerLevel level, Handler handler,
                                                        boolean disabled) {
        register();
        MinecraftServer server = level.getServer();
        Thread thread = Thread.currentThread();
        if (server.getRunningThread() != thread) {
            throw new IllegalStateException("test handler override must open on the server thread");
        }
        ArrayDeque<HandlerOverrideFrame> stack = TEST_HANDLER_OVERRIDES.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            TEST_HANDLER_OVERRIDES.set(stack);
        }
        HandlerOverrideFrame frame = new HandlerOverrideFrame(handler, disabled, level, server, thread);
        stack.addLast(frame);
        return new HandlerOverride(frame);
    }

    private static void registerLocked() {
        if (registered) {
            return;
        }
        ServerEntityEvents.ALLOW_LOAD.register(SlabRigHangingDirectEntityGate::allowLoad);
        ServerEntityEvents.ENTITY_LOAD.register(SlabRigHangingDirectEntityGate::onLoad);
        registered = true;
    }

    /**
     * Opens the placement context directly around the future executor's one item-use operation.
     * With no installed handler this is an inert scope and vanilla insertion remains untouched.
     */
    public static CaptureScope openPlacement(ServerLevel level, CaptureKey key) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(key, "key");
        register();
        CaptureContext context = new CaptureContext(key, CaptureKind.PLACEMENT, level.dimension(),
                Optional.empty());
        Handler handler = currentHandler(level);
        return handler == null ? CaptureScope.inactive(context) : openActive(level, context, handler);
    }

    /**
     * Called only by the painting-drop mixin around the exact vanilla item-spawn invocation.
     * Absence of a handler, a foreign painting, or a world mismatch runs the original operation
     * without creating a capture context.
     */
    public static ItemEntity capturePaintingDrop(Painting source, ServerLevel level,
                                                  Supplier<ItemEntity> original) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(original, "original");
        register();
        Handler handler = currentHandler(level);
        if (handler == null || source.level() != level) {
            return original.get();
        }

        Optional<CaptureKey> key;
        try {
            key = Objects.requireNonNull(handler.beginPaintingDrop(source, level),
                    "handler returned null drop context");
        } catch (Throwable exception) {
            try {
                return handler.beginFailure(source, level, exception) ? null : original.get();
            } catch (Throwable callbackFailure) {
                if (callbackFailure != exception) {
                    try {
                        exception.addSuppressed(callbackFailure);
                    } catch (Throwable ignored) {
                        // Suppression is diagnostic only; it must not reopen the server-tick failure.
                    }
                }
                Slabbed.LOGGER.error("direct hanging drop begin-failure callback failed; "
                        + "vetoing unevidenced item spawn for source {}", source.getUUID(), exception);
                return null;
            }
        }
        if (key.isEmpty()) {
            return original.get();
        }

        CaptureContext context = new CaptureContext(key.get(), CaptureKind.DROP, level.dimension(),
                Optional.of(source.getUUID()));
        CaptureScope scope = openActive(level, context, handler);
        try {
            try (scope) {
                return original.get();
            }
        } finally {
            try {
                handler.complete(scope.result(), level);
            } catch (Throwable completionFailure) {
                Slabbed.LOGGER.error("direct hanging drop completion callback failed; "
                        + "the pre-insertion decision remains fail-closed for source {}",
                        source.getUUID(), completionFailure);
            }
        }
    }

    private static CaptureScope openActive(ServerLevel level, CaptureContext context,
                                           Handler handler) {
        MinecraftServer server = level.getServer();
        Thread thread = Thread.currentThread();
        if (server.getRunningThread() != thread) {
            throw new IllegalStateException("direct hanging entity context must open on the server thread");
        }

        ArrayDeque<Frame> stack = ACTIVE_FRAMES.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            ACTIVE_FRAMES.set(stack);
        }
        Frame frame = new Frame(context, handler, level, server, thread);
        stack.addLast(frame);
        return new CaptureScope(context, frame);
    }

    private static Handler currentHandler(ServerLevel level) {
        ArrayDeque<HandlerOverrideFrame> stack = TEST_HANDLER_OVERRIDES.get();
        if (stack != null && !stack.isEmpty()) {
            HandlerOverrideFrame frame = stack.peekLast();
            if (!frame.closed
                    && frame.thread == Thread.currentThread()
                    && frame.server == level.getServer()
                    && frame.level == level) {
                return frame.disabled ? null : frame.handler;
            }
        }
        return installedHandler;
    }

    private static boolean allowLoad(Entity entity, ServerLevel level, EntitySpawnReason reason,
                                     boolean loadedFromDisk) {
        // Disk restoration is never owned or vetoed, even if it happens synchronously in a scope.
        if (loadedFromDisk) {
            return true;
        }
        Frame frame = activeMatchingFrame(entity, level);
        if (frame == null) {
            return true;
        }

        UUID uuid = entity.getUUID();
        MutableOutcome existing = frame.outcomes.get(uuid);
        if (existing != null) {
            // The handler is a one-shot preclaim per entity UUID within this exact context.
            return existing.entity == entity && existing.status == PreclaimStatus.CLAIMED_ALLOW;
        }

        MutableOutcome outcome = new MutableOutcome(entity);
        frame.outcomes.put(uuid, outcome);
        try {
            PreclaimDecision decision = Objects.requireNonNull(
                    frame.handler.preclaim(frame.context, entity, level),
                    "handler returned null preclaim decision");
            if (frame.context.kind() == CaptureKind.DROP
                    && decision != PreclaimDecision.CLAIM_AND_VETO) {
                outcome.status = PreclaimStatus.REJECTED;
                outcome.preclaimFailure = "drop context requires exact claim-and-veto";
                return false;
            }
            if (decision == PreclaimDecision.CLAIM_AND_ALLOW) {
                outcome.status = PreclaimStatus.CLAIMED_ALLOW;
                return true;
            }
            if (decision == PreclaimDecision.CLAIM_AND_VETO) {
                outcome.status = PreclaimStatus.CLAIMED_VETO;
                outcome.preclaimFailure = "durably claimed and intentionally vetoed before insertion";
                return false;
            }
            outcome.status = PreclaimStatus.REJECTED;
            outcome.preclaimFailure = "handler rejected durable ownership";
            return false;
        } catch (Throwable exception) {
            outcome.status = PreclaimStatus.EXCEPTION;
            outcome.preclaimFailure = describe(exception);
            return false;
        }
    }

    private static void onLoad(Entity entity, ServerLevel level) {
        Frame frame = activeMatchingFrame(entity, level);
        if (frame == null) {
            return;
        }
        MutableOutcome outcome = frame.outcomes.get(entity.getUUID());
        if (outcome == null || outcome.entity != entity
                || outcome.status != PreclaimStatus.CLAIMED_ALLOW
                || outcome.confirmationAttempted) {
            return;
        }

        outcome.confirmationAttempted = true;
        try {
            frame.handler.confirm(frame.context, entity, level);
            outcome.confirmed = true;
        } catch (Throwable exception) {
            // Tracking has already begun, so confirmation failure is evidence, not a fake veto.
            outcome.confirmationFailure = describe(exception);
        }
    }

    private static Frame activeMatchingFrame(Entity entity, ServerLevel level) {
        ArrayDeque<Frame> stack = ACTIVE_FRAMES.get();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Frame frame = stack.peekLast();
        if (frame.closed
                || frame.thread != Thread.currentThread()
                || frame.server != level.getServer()
                || frame.level != level
                || !frame.context.dimension().equals(level.dimension())) {
            return null;
        }
        return switch (frame.context.kind()) {
            case PLACEMENT -> entity instanceof Painting ? frame : null;
            case DROP -> entity instanceof ItemEntity ? frame : null;
        };
    }

    private static String describe(Throwable exception) {
        String message = exception.getMessage();
        return exception.getClass().getName() + (message == null ? "" : ": " + message);
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }

    /** Nested-safe scope whose result remains inspectable after {@link #close()}. */
    public static final class CaptureScope implements AutoCloseable {
        private final CaptureContext context;
        private final Frame frame;
        private boolean closed;

        private CaptureScope(CaptureContext context, Frame frame) {
            this.context = context;
            this.frame = frame;
        }

        private static CaptureScope inactive(CaptureContext context) {
            return new CaptureScope(context, null);
        }

        public CaptureResult result() {
            List<EntityOutcome> entities = new ArrayList<>();
            if (frame != null) {
                for (MutableOutcome outcome : frame.outcomes.values()) {
                    entities.add(outcome.snapshot());
                }
            }
            return new CaptureResult(context, frame != null, closed, entities);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (frame == null) {
                closed = true;
                return;
            }
            if (Thread.currentThread() != frame.thread) {
                throw new IllegalStateException("direct hanging entity context closed on another thread");
            }

            ArrayDeque<Frame> stack = ACTIVE_FRAMES.get();
            if (stack == null || stack.isEmpty()) {
                throw new IllegalStateException("direct hanging entity context stack is missing");
            }
            Frame removed = stack.removeLast();
            if (removed != frame) {
                stack.clear();
                ACTIVE_FRAMES.remove();
                throw new IllegalStateException("direct hanging entity context closed out of order");
            }
            frame.closed = true;
            closed = true;
            if (stack.isEmpty()) {
                ACTIVE_FRAMES.remove();
            }
        }
    }

    /** Auto-closeable token returned only by {@link #openTestHandlerOverride}. */
    public static final class HandlerOverride implements AutoCloseable {
        private final HandlerOverrideFrame frame;
        private boolean closed;

        private HandlerOverride(HandlerOverrideFrame frame) {
            this.frame = frame;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (Thread.currentThread() != frame.thread) {
                throw new IllegalStateException("test handler override closed on another thread");
            }
            ArrayDeque<HandlerOverrideFrame> stack = TEST_HANDLER_OVERRIDES.get();
            if (stack == null || stack.isEmpty()) {
                throw new IllegalStateException("test handler override stack is missing");
            }
            HandlerOverrideFrame removed = stack.removeLast();
            if (removed != frame) {
                stack.clear();
                TEST_HANDLER_OVERRIDES.remove();
                throw new IllegalStateException("test handler override closed out of order");
            }
            frame.closed = true;
            closed = true;
            if (stack.isEmpty()) {
                TEST_HANDLER_OVERRIDES.remove();
            }
        }
    }

    private static final class Frame {
        private final CaptureContext context;
        private final Handler handler;
        private final ServerLevel level;
        private final MinecraftServer server;
        private final Thread thread;
        private final Map<UUID, MutableOutcome> outcomes = new LinkedHashMap<>();
        private boolean closed;

        private Frame(CaptureContext context, Handler handler, ServerLevel level,
                      MinecraftServer server, Thread thread) {
            this.context = context;
            this.handler = handler;
            this.level = level;
            this.server = server;
            this.thread = thread;
        }
    }

    private static final class HandlerOverrideFrame {
        private final Handler handler;
        private final boolean disabled;
        private final ServerLevel level;
        private final MinecraftServer server;
        private final Thread thread;
        private boolean closed;

        private HandlerOverrideFrame(Handler handler, boolean disabled, ServerLevel level,
                                     MinecraftServer server, Thread thread) {
            this.handler = handler;
            this.disabled = disabled;
            this.level = level;
            this.server = server;
            this.thread = thread;
        }
    }

    private static final class MutableOutcome {
        private final Entity entity;
        private PreclaimStatus status = PreclaimStatus.EXCEPTION;
        private boolean confirmationAttempted;
        private boolean confirmed;
        private String preclaimFailure;
        private String confirmationFailure;

        private MutableOutcome(Entity entity) {
            this.entity = entity;
        }

        private EntityOutcome snapshot() {
            return new EntityOutcome(entity.getUUID(), entity.getClass().getName(), status,
                    confirmationAttempted, confirmed, Optional.ofNullable(preclaimFailure),
                    Optional.ofNullable(confirmationFailure));
        }
    }
}
