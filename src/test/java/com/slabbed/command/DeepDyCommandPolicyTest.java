package com.slabbed.command;

import org.junit.jupiter.api.Test;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DeepDyCommandPolicyTest {
    @Test
    void confirmationExpiresAndCannotBeReplayed() {
        DeepDyCommand.ConfirmationStore store = new DeepDyCommand.ConfirmationStore();
        Object authority = new Object();
        DeepDyCommand.CallerKey caller = DeepDyCommand.CallerKey.forSource(new SilentSource());

        store.arm(authority, caller, 100L);
        assertTrue(store.consume(
                authority,
                caller,
                100L + DeepDyCommand.CONFIRMATION_WINDOW_NANOS - 1L));
        assertFalse(store.consume(authority, caller, 100L));

        store.arm(authority, caller, 200L);
        assertFalse(store.consume(
                authority,
                caller,
                200L + DeepDyCommand.CONFIRMATION_WINDOW_NANOS));
    }

    @Test
    void confirmationIsBoundToAuthorityAndCaller() {
        DeepDyCommand.ConfirmationStore store = new DeepDyCommand.ConfirmationStore();
        Object firstAuthority = new Object();
        Object secondAuthority = new Object();
        DeepDyCommand.CallerKey first = DeepDyCommand.CallerKey.forSource(new SilentSource());
        DeepDyCommand.CallerKey second = DeepDyCommand.CallerKey.forSource(new SilentSource());

        store.arm(firstAuthority, first, 10L);
        assertFalse(store.consume(firstAuthority, second, 10L));
        assertFalse(store.consume(secondAuthority, first, 10L));
        assertTrue(store.cancel(firstAuthority, first));
        assertFalse(store.consume(firstAuthority, first, 10L));
    }

    @Test
    void distinctSameRoleSourcesNeverShareAConfirmation() {
        DeepDyCommand.ConfirmationStore store = new DeepDyCommand.ConfirmationStore();
        Object authority = new Object();
        CommandSource firstSource = new SilentSource();
        CommandSource secondSource = new SilentSource();
        DeepDyCommand.CallerKey first = DeepDyCommand.CallerKey.forSource(firstSource);
        DeepDyCommand.CallerKey second = DeepDyCommand.CallerKey.forSource(secondSource);

        store.arm(authority, first, 50L);
        assertFalse(store.consume(authority, second, 50L));
        assertTrue(store.consume(authority, first, 50L));
    }

    private static final class SilentSource implements CommandSource {
        @Override
        public void sendSystemMessage(Component message) {
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
