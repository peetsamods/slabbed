package com.slabbed.test;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** GameTest-owned hooks and bookkeeping for SlabRig's real proxy-use seam. */
public final class SlabRigGameTestSeams {
    private static final Map<InvocationKey, ProbeRequest> PROBE_REQUESTS = new HashMap<>();

    private SlabRigGameTestSeams() {
    }

    public static void installPostUseHook(
            ServerLevel world,
            Player player,
            BlockPos target,
            Runnable hook) {
        installPlacementProbe(world, player, target, null, hook);
    }

    public static synchronized void installPlacementProbe(
            ServerLevel world,
            Player player,
            BlockPos target,
            Item replacementItem,
            Runnable hook) {
        InvocationKey key = key(world, player, target);
        if (PROBE_REQUESTS.containsKey(key)) {
            throw new IllegalStateException(
                    "SlabRig GameTest probe already installed for " + target.toShortString());
        }
        PROBE_REQUESTS.put(key, new ProbeRequest(
                replacementItem,
                Objects.requireNonNull(hook, "hook")));
    }

    public static synchronized void clearPostUseHook(
            ServerLevel world,
            Player player,
            BlockPos target) {
        PROBE_REQUESTS.remove(key(world, player, target));
    }

    public static synchronized Item placementItem(
            ServerLevel world,
            Player player,
            BlockPos target,
            Item productionItem) {
        ProbeRequest request = PROBE_REQUESTS.get(key(world, player, target));
        return request == null || request.replacementItem == null
                ? productionItem
                : request.replacementItem;
    }

    public static void afterProductionProxyUse(
            ServerLevel world,
            Player player,
            BlockPos target) {
        Runnable hook;
        synchronized (SlabRigGameTestSeams.class) {
            ProbeRequest request = PROBE_REQUESTS.get(key(world, player, target));
            if (request == null || request.postUseHook == null) {
                return;
            }
            hook = request.postUseHook;
            request.postUseHook = null;
        }
        hook.run();
    }

    public static synchronized void captureProductionAttempt(
            ServerLevel world,
            Player player,
            BlockPos target,
            Object attempt) {
        ProbeRequest request = PROBE_REQUESTS.get(key(world, player, target));
        if (request == null) {
            return;
        }
        if (request.captured != null) {
            throw new IllegalStateException(
                    "SlabRig GameTest attempt captured twice for " + target.toShortString());
        }
        request.captured = decodeProductionAttempt(attempt);
    }

    public static synchronized PlacementProbe takePlacementProbe(
            ServerLevel world,
            Player player,
            BlockPos target) {
        ProbeRequest request = PROBE_REQUESTS.remove(key(world, player, target));
        if (request == null || request.captured == null) {
            throw new IllegalStateException(
                    "SlabRig production attempt was not captured for " + target.toShortString());
        }
        return request.captured;
    }

    public record PlacementProbe(
            String interactionResult,
            boolean interactionConsumesAction,
            String stackItemBefore,
            int stackBefore,
            String stackItemAfter,
            int stackAfter,
            boolean persistentSubjectPresent,
            int observedChangeCount,
            String error,
            boolean outsideEffect) {
    }

    private static PlacementProbe decodeProductionAttempt(Object attempt) {
        Objects.requireNonNull(attempt, "attempt");
        Set<?> changed = (Set<?>) accessor(attempt, "changed");
        return new PlacementProbe(
                (String) accessor(attempt, "interactionResult"),
                (Boolean) accessor(attempt, "interactionConsumesAction"),
                (String) accessor(attempt, "stackItemBefore"),
                (Integer) accessor(attempt, "stackBefore"),
                (String) accessor(attempt, "stackItemAfter"),
                (Integer) accessor(attempt, "stackAfter"),
                (Boolean) accessor(attempt, "persistentSubjectPresent"),
                changed.size(),
                (String) accessor(attempt, "error"),
                (Boolean) accessor(attempt, "outsideEffect"));
    }

    private static Object accessor(Object target, String name) {
        try {
            Method method = target.getClass().getDeclaredMethod(name);
            if (!method.trySetAccessible()) {
                throw new IllegalStateException(
                        "cannot inspect SlabRig production attempt accessor " + name);
            }
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException(
                    "cannot inspect SlabRig production attempt accessor " + name,
                    exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException(
                    "SlabRig production attempt accessor failed " + name,
                    exception.getCause());
        }
    }

    private static InvocationKey key(ServerLevel world, Player player, BlockPos target) {
        return new InvocationKey(
                Objects.requireNonNull(world, "world").getServer(),
                world,
                Objects.requireNonNull(player, "player").getUUID(),
                Objects.requireNonNull(target, "target").immutable());
    }

    private static final class ProbeRequest {
        private final Item replacementItem;
        private Runnable postUseHook;
        private PlacementProbe captured;

        private ProbeRequest(Item replacementItem, Runnable postUseHook) {
            this.replacementItem = replacementItem;
            this.postUseHook = postUseHook;
        }
    }

    private static final class InvocationKey {
        private final MinecraftServer server;
        private final ServerLevel world;
        private final UUID player;
        private final BlockPos target;

        private InvocationKey(
                MinecraftServer server,
                ServerLevel world,
                UUID player,
                BlockPos target) {
            this.server = Objects.requireNonNull(server, "server");
            this.world = world;
            this.player = player;
            this.target = target;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof InvocationKey key
                    && server == key.server
                    && world == key.world
                    && player.equals(key.player)
                    && target.equals(key.target);
        }

        @Override
        public int hashCode() {
            int hash = System.identityHashCode(server);
            hash = 31 * hash + System.identityHashCode(world);
            hash = 31 * hash + player.hashCode();
            return 31 * hash + target.hashCode();
        }
    }
}
