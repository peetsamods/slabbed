package com.slabbed.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Reads a use-item-on packet's three fields on 26.2 ({@code getHand()}, {@code getHitResult()},
 * {@code getSequence()}) and on 26.3 and newer, where the packet became a record ({@code hand()},
 * {@code hitResult()}, {@code sequence()}).
 *
 * <p>The accessor is looked up once by name; every read is then an exact-typed method-handle call,
 * which the JIT treats as a direct call. These run once per interaction packet, never per quad.
 */
public final class UseItemOnPacketAccess {

    private static final MethodHandle HAND = find("hand", "getHand", InteractionHand.class);
    private static final MethodHandle HIT_RESULT = find("hitResult", "getHitResult", BlockHitResult.class);
    private static final MethodHandle SEQUENCE = find("sequence", "getSequence", int.class);

    private UseItemOnPacketAccess() {
    }

    public static InteractionHand hand(ServerboundUseItemOnPacket packet) {
        try {
            return (InteractionHand) HAND.invokeExact(packet);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static BlockHitResult hitResult(ServerboundUseItemOnPacket packet) {
        try {
            return (BlockHitResult) HIT_RESULT.invokeExact(packet);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    public static int sequence(ServerboundUseItemOnPacket packet) {
        try {
            return (int) SEQUENCE.invokeExact(packet);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    private static MethodHandle find(String modern, String legacy, Class<?> returnType) {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodType type = MethodType.methodType(returnType);
        for (String name : new String[] {modern, legacy}) {
            try {
                return lookup.findVirtual(ServerboundUseItemOnPacket.class, name, type);
            } catch (NoSuchMethodException | IllegalAccessException ignored) {
                // try the other spelling
            }
        }
        throw new IllegalStateException("ServerboundUseItemOnPacket has neither " + modern + " nor " + legacy);
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException r) {
            return r;
        }
        if (t instanceof Error e) {
            throw e;
        }
        return new IllegalStateException(t);
    }
}
