package com.slabbed.debug;

import net.minecraft.item.ItemStack;

/**
 * TEMP shared context to gate debug predicate logging for specific placement attempts.
 */
public final class DebugTraceContext {
    private static final ThreadLocal<String> ACTIVE_ITEM = ThreadLocal.withInitial(() -> null);

    private DebugTraceContext() {}

    public static void enter(ItemStack stack) {
        if (stack == null) return;
        ACTIVE_ITEM.set(stack.getItem().toString());
    }

    public static void exit() {
        ACTIVE_ITEM.remove();
    }

    public static boolean isActive() {
        return ACTIVE_ITEM.get() != null;
    }

    public static String getActiveItem() {
        return ACTIVE_ITEM.get();
    }
}
