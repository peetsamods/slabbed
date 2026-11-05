package com.slabbed.util;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/** Geometry-only column decisions owned by one native snapshot compile. */
public final class MeshColumnCache {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private static final int MAX_COLUMNS = (16 * 16 + 4 * 16) * 16;

    private MeshColumnCache() { }

    /**
     * The client compiler hook supplies its exact native snapshot, or null for an unsupported
     * view. This utility has no client-class dependency and never accepts a Level as a snapshot.
     */
    public static Scope open(BlockGetter snapshot) {
        Scope scope = new Scope(snapshot instanceof Level ? null : snapshot, CURRENT.get());
        CURRENT.set(scope);
        return scope;
    }

    static Scope current(BlockGetter view) {
        Scope scope = CURRENT.get();
        return scope != null && scope.view == view && view != null ? scope : null;
    }

    public static final class Scope implements AutoCloseable {
        private BlockGetter view;
        private Scope previous;
        private Thread owner;
        private Long2ByteOpenHashMap columns;
        private long firstKey;
        private byte firstValue;
        private boolean closed;

        private Scope(BlockGetter view, Scope previous) {
            this.view = view;
            this.previous = previous;
            this.owner = Thread.currentThread();
        }

        byte lookup(BlockPos top) {
            if (columns == null && firstValue == 0) {
                return 0;
            }
            long key = top.asLong();
            if (!representable(top, key)) {
                return 0;
            }
            return columns == null ? (firstKey == key ? firstValue : 0) : columns.get(key);
        }

        void remember(BlockPos top, boolean suspicious) {
            long key = top.asLong();
            if (!representable(top, key)) {
                return;
            }
            byte value = (byte) (suspicious ? 2 : 1);
            // A sparse compile can ask just one column; retain that entry without a table.
            if (columns == null && firstValue == 0) {
                firstKey = key;
                firstValue = value;
                return;
            }
            if (columns == null) {
                columns = new Long2ByteOpenHashMap(32);
                columns.put(firstKey, firstValue);
            }
            if (columns.size() < MAX_COLUMNS) {
                columns.put(key, value);
            }
        }

        private static boolean representable(BlockPos pos, long key) {
            return BlockPos.getX(key) == pos.getX()
                    && BlockPos.getY(key) == pos.getY()
                    && BlockPos.getZ(key) == pos.getZ();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (owner != Thread.currentThread() || CURRENT.get() != this) {
                throw new IllegalStateException("Mesh column scopes must close on their owning thread in nesting order");
            }
            Scope parent = previous;
            if (parent == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(parent);
            }
            closed = true;
            columns = null;
            firstKey = 0;
            firstValue = 0;
            view = null;
            previous = null;
            owner = null;
        }
    }
}
