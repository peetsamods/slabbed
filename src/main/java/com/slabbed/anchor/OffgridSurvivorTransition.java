package com.slabbed.anchor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** One exact mixed-container continuation may retain its existing seat (LAW.md). */
public final class OffgridSurvivorTransition implements AutoCloseable {
    private static final ThreadLocal<OffgridSurvivorTransition> ACTIVE = new ThreadLocal<>();
    private final OffgridSurvivorTransition previous;
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState container;
    private final BlockState survivor;
    private final SlabAnchorAttachment.PlacementDyFact fact;
    private boolean consumed;

    private OffgridSurvivorTransition(ServerLevel level, BlockPos pos, BlockState container,
                                     BlockState survivor, SlabAnchorAttachment.PlacementDyFact fact) {
        this.previous = ACTIVE.get();
        this.level = level;
        this.pos = pos.immutable();
        this.container = container;
        this.survivor = survivor;
        this.fact = fact;
        ACTIVE.set(this);
    }

    /** Null is an inert scope: no authoritative finite fact means nothing may be retained. */
    public static OffgridSurvivorTransition open(Level level, BlockPos pos, BlockState container,
                                                 BlockState survivor) {
        if (!(level instanceof ServerLevel server) || pos == null || container == null
                || survivor == null || survivor.isAir()
                || level.getBlockState(pos) != container) return null;
        var fact = SlabAnchorAttachment.rawPlacementDyFact(level, pos);
        if (!fact.present() || !Double.isFinite(fact.valueOrNaN())) return null;
        return new OffgridSurvivorTransition(server, pos, container, survivor, fact);
    }

    /** Consumed before cleanup; callbacks cannot reuse the permission for a later replacement. */
    public static boolean consume(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        var active = ACTIVE.get();
        if (active == null || active.consumed || active.level != level || !active.pos.equals(pos)
                || active.container != oldState || active.survivor != newState
                || !active.fact.equals(SlabAnchorAttachment.rawPlacementDyFact(level, pos))) return false;
        active.consumed = true;
        return true;
    }

    @Override
    public void close() {
        if (ACTIVE.get() != this) throw new IllegalStateException("survivor transitions must close in order");
        if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
    }
}
