package com.slabbed.command;

import com.mojang.brigadier.context.CommandContext;
import com.slabbed.anchor.DeepDyConsentAttachment;
import com.slabbed.anchor.DeepDyConsentAttachment.GrantResult;
import com.slabbed.anchor.DeepDyConsentAttachment.State;
import java.time.Duration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Server-owned, one-way activation command for save-scoped deep placement. */
public final class DeepDyCommand {
    static final long CONFIRMATION_WINDOW_NANOS = Duration.ofSeconds(20L).toNanos();
    private static final ConfirmationStore CONFIRMATIONS = new ConfirmationStore();
    private static boolean initialized;

    private DeepDyCommand() {
    }

    public static void register() {
        if (initialized) {
            return;
        }
        initialized = true;
        NeoForge.EVENT_BUS.addListener(DeepDyCommand::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(DeepDyCommand::onServerStopped);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("slabbed")
                        .requires(source -> source.hasPermission(Commands.LEVEL_OWNERS))
                        .then(Commands.literal("deep-mode")
                                .then(Commands.literal("status").executes(DeepDyCommand::status))
                                .then(Commands.literal("enable").executes(DeepDyCommand::requestEnable))
                                .then(Commands.literal("confirm").executes(DeepDyCommand::confirm))
                                .then(Commands.literal("cancel").executes(DeepDyCommand::cancel)))
        );
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        State state = DeepDyConsentAttachment.state(source.getServer().overworld());
        String key;
        if (state == State.ENABLED) {
            key = "commands.slabbed.deep_mode.status.enabled";
        } else if (state == State.DISABLED) {
            key = "commands.slabbed.deep_mode.status.disabled";
        } else if (state == State.LOCKED_UNKNOWN) {
            key = "commands.slabbed.deep_mode.status.locked";
        } else {
            key = "commands.slabbed.deep_mode.status.legacy";
        }
        source.sendSuccess(() -> Component.translatable(key), false);
        return 1;
    }

    private static int requestEnable(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        State state = DeepDyConsentAttachment.state(source.getServer().overworld());
        if (state == State.ENABLED) {
            CONFIRMATIONS.cancel(source.getServer(), sourceKey(source));
            source.sendSuccess(
                    () -> Component.translatable("commands.slabbed.deep_mode.already_enabled"),
                    false);
            return 1;
        }
        if (state == State.LOCKED_UNKNOWN) {
            CONFIRMATIONS.cancel(source.getServer(), sourceKey(source));
            source.sendFailure(Component.translatable("commands.slabbed.deep_mode.locked"));
            return 0;
        }

        CONFIRMATIONS.arm(
                source.getServer(),
                sourceKey(source),
                System.nanoTime());
        source.sendSystemMessage(warning());
        return 1;
    }

    private static int confirm(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!CONFIRMATIONS.consume(
                source.getServer(),
                sourceKey(source),
                System.nanoTime())) {
            source.sendFailure(Component.translatable("commands.slabbed.deep_mode.no_confirmation"));
            return 0;
        }

        GrantResult result = DeepDyConsentAttachment.grant(source.getServer());
        if (result == GrantResult.ENABLED_NOW) {
            source.sendSuccess(
                    () -> Component.translatable("commands.slabbed.deep_mode.enabled"),
                    true);
            return 1;
        }
        if (result == GrantResult.ALREADY_ENABLED) {
            source.sendSuccess(
                    () -> Component.translatable("commands.slabbed.deep_mode.already_enabled"),
                    false);
            return 1;
        }
        if (result == GrantResult.LOCKED_UNKNOWN) {
            source.sendFailure(Component.translatable("commands.slabbed.deep_mode.locked"));
            return 0;
        }
        source.sendFailure(Component.translatable("commands.slabbed.deep_mode.no_server"));
        return 0;
    }

    private static int cancel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        boolean canceled = CONFIRMATIONS.cancel(source.getServer(), sourceKey(source));
        if (!canceled) {
            source.sendFailure(Component.translatable("commands.slabbed.deep_mode.no_confirmation"));
            return 0;
        }
        source.sendSuccess(
                () -> Component.translatable("commands.slabbed.deep_mode.canceled"),
                false);
        return 1;
    }

    private static MutableComponent warning() {
        MutableComponent confirm = Component.translatable(
                        "commands.slabbed.deep_mode.confirm_button")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND,
                                "/slabbed deep-mode confirm"))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable(
                                        "commands.slabbed.deep_mode.confirm_hover"))));
        MutableComponent cancel = Component.translatable(
                        "commands.slabbed.deep_mode.cancel_button")
                .withStyle(ChatFormatting.RED, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND,
                                "/slabbed deep-mode cancel"))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable(
                                        "commands.slabbed.deep_mode.cancel_hover"))));
        return Component.translatable("commands.slabbed.deep_mode.warning")
                .append("\n")
                .append(confirm)
                .append(" ")
                .append(cancel);
    }

    private static CallerKey sourceKey(CommandSourceStack source) {
        Entity entity = source.getEntity();
        return entity == null
                ? CallerKey.forSource(source.source)
                : CallerKey.forEntity(entity.getUUID());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        CONFIRMATIONS.clear(event.getServer());
    }

    static final class ConfirmationStore {
        private final Map<Object, Map<CallerKey, Long>> pending = new IdentityHashMap<>();

        synchronized void arm(Object authority, CallerKey caller, long currentNanos) {
            pending.computeIfAbsent(authority, ignored -> new HashMap<>())
                    .put(caller, currentNanos);
        }

        synchronized boolean consume(Object authority, CallerKey caller, long currentNanos) {
            Map<CallerKey, Long> callers = pending.get(authority);
            if (callers == null) {
                return false;
            }
            Long issuedAt = callers.remove(caller);
            removeEmpty(authority, callers);
            if (issuedAt == null) {
                return false;
            }
            long age = currentNanos - issuedAt;
            return age >= 0L && age < CONFIRMATION_WINDOW_NANOS;
        }

        synchronized boolean cancel(Object authority, CallerKey caller) {
            Map<CallerKey, Long> callers = pending.get(authority);
            if (callers == null) {
                return false;
            }
            boolean removed = callers.remove(caller) != null;
            removeEmpty(authority, callers);
            return removed;
        }

        synchronized void clear(Object authority) {
            pending.remove(authority);
        }

        private void removeEmpty(Object authority, Map<CallerKey, Long> callers) {
            if (callers.isEmpty()) {
                pending.remove(authority);
            }
        }
    }

    static final class CallerKey {
        private final UUID entityId;
        private final CommandSource source;

        private CallerKey(UUID entityId, CommandSource source) {
            this.entityId = entityId;
            this.source = source;
        }

        static CallerKey forEntity(UUID entityId) {
            return new CallerKey(entityId, null);
        }

        static CallerKey forSource(CommandSource source) {
            return new CallerKey(null, source);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof CallerKey key)) {
                return false;
            }
            if (entityId != null || key.entityId != null) {
                return entityId != null && entityId.equals(key.entityId);
            }
            return source == key.source;
        }

        @Override
        public int hashCode() {
            return entityId != null ? entityId.hashCode() : System.identityHashCode(source);
        }
    }
}
