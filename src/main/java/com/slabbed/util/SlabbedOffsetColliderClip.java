package com.slabbed.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Offset-aware block collision for arrows only (maintainer ruling, 2026-08-28).
 *
 * <p><b>Why this exists.</b> A block's per-state collision shape stays UNLOWERED for every
 * {@code ClipContext.Block.COLLIDER} clip except a small named family (StairBlock, the beta35
 * fence/wall/gate contact family, GRINDSTONE) — the entity movement broadphase and this exact
 * shape-producing method are the same call and cannot be told apart, so a per-state fix cannot
 * discriminate its caller. That leaves a real gap: an arrow shot at the visible lower half of a
 * lowered block passes through it, because {@code Level.clip(ClipContext)} never constructs the
 * {@code BlockCollisions} the movement fix patches.
 *
 * <p><b>Why this is safe where a blanket per-state fix is not.</b> {@code Level.clip}'s DDA
 * traversal has no shell/interior classification and no cached "large collision shape" gate — the
 * specific mechanism that let the old walk-through-blocks regression skip an overhanging cell. It
 * visits every cell the ray geometrically crosses, unconditionally, so this class corrects the
 * CLIP RESULT rather than touching the shape vanilla itself resolves — the shared shape method
 * keeps answering un-lowered for the broadphase. Two corrections compose here: an owner-window
 * search for drawn bodies hanging up to {@link SlabbedOffsetRaycast#OWNER_WINDOW_RADIUS} cells
 * into a marched cell (the same window {@link SlabbedOffsetRaycast} already searches for the
 * player's OUTLINE-clip crosshair target), and — maintainer ruling, 2026-08-31 — a band-aware
 * re-march that clears a vanilla hit landing in a dy-shifted cell's VACATED band, so the clip
 * answer matches the drawn world in both directions.
 *
 * <p><b>Attribution, not a union.</b> A candidate hit is clipped against the OWNER's own
 * {@link BlockPos}, never the marched cell's — {@code AbstractArrow} reads the resulting
 * {@code BlockHitResult}'s position to decide what it stuck into. A hit attributed to the wrong
 * cell would be a new wrong answer, not a fix.
 *
 * <p><b>Known, accepted limitation.</b> A ray that never visits the owner's own cell at all — one
 * that stays entirely inside the hang band, or one that starts inside it — still misses. That is
 * a strict improvement over today (today it misses always), never a regression, and it is the
 * same limitation {@link SlabbedOffsetRaycast} already accepts for the outline case.
 */
public final class SlabbedOffsetColliderClip {

    /**
     * Master switch. Default ON. Set {@code -Dslabbed.arrowOffsetClip=false} to fall back to
     * vanilla's unlowered arrow collision for live A/B comparison.
     */
    public static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("slabbed.arrowOffsetClip", "true"));

    /**
     * Independent switch for the OCCLUSION consumers — mob line of sight and explosion shelter.
     * Default ON. Set {@code -Dslabbed.sightOffsetClip=false} to return those two to vanilla while
     * leaving arrows fixed. Deliberately separate from {@link #ENABLED}: these run far more often
     * than arrow ticks, so if their cost ever needs backing out it must be possible to do that
     * without also reverting the live-confirmed arrow behaviour.
     */
    public static final boolean SIGHT_ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("slabbed.sightOffsetClip", "true"));

    private static final double EPS = 1.0e-6d;

    private SlabbedOffsetColliderClip() {
    }

    /**
     * Offset-aware occlusion for consumers that only ask "is the view blocked?" — mob line of
     * sight and explosion shelter. Returns a hit whenever the ray crosses a lowered block's drawn
     * body, so cover that is visibly solid stops behaving as though it were not there.
     *
     * <p>Far cheaper than {@link #clip} despite running far more often, because a boolean consumer
     * cannot tell WHICH obstruction it found: once vanilla has already reported a hit the answer is
     * settled and the owner-window search is pure waste, so it is skipped. In any enclosed space —
     * a cave, a room, terrain — most of these rays already terminate on ordinary geometry and cost
     * exactly nothing extra. Only a ray vanilla thinks is CLEAR pays for the search, which is
     * precisely the ray that might be wrong.
     *
     * <p>Symmetric since the band-clearing ruling (maintainer ruling, 2026-08-31): it adds the
     * obstructions the drawn world has and vanilla missed, AND re-examines a vanilla hit that
     * landed on a dy-shifted cell — whose un-lowered shape claims the VACATED band the drawn
     * world does not have. A hit on ordinary (unshifted) geometry still settles the answer at the
     * cost of one stored-offset read; only rays vanilla resolved against a shifted cell pay the
     * band-aware re-march, and only rays clear after that pay the owner-window search.
     */
    public static BlockHitResult clipForOcclusion(
            BlockGetter world, ClipContext context, Entity viewer, BlockHitResult vanillaHit
    ) {
        if (!SIGHT_ENABLED || world == null || context == null || vanillaHit == null) {
            return vanillaHit;
        }
        if (vanillaHit.getType() != HitResult.Type.MISS) {
            // INVARIANT: the band-clearing re-march must run under exactly the gates of the
            // additive owner-window search it composes with — the master switch included — or a
            // switched-off configuration yields a third behavior that is neither vanilla nor the
            // drawn world (band cleared, hanging bodies not blocking). clip() below enforces
            // ENABLED for the additive half; this mirrors it for the clearing half. No headless
            // venue can toggle a class-load system property per row, so this guard is pinned by
            // this comment and review rather than a test — do not separate the two gates.
            if (!ENABLED) {
                return vanillaHit;
            }
            // Off-thread queries keep vanilla's answer untouched, exactly as clip() below does.
            if (slabbed$isUnsafeAsyncShapeContext(world)) {
                return vanillaHit;
            }
            if (!isDyShiftedCell(world, vanillaHit.getBlockPos())) {
                return vanillaHit;
            }
            CollisionContext shapeContext =
                    viewer != null ? CollisionContext.of(viewer) : CollisionContext.empty();
            BlockHitResult cleared = bandAwareVanillaClip(world, context, shapeContext);
            if (cleared.getType() != HitResult.Type.MISS) {
                return cleared;
            }
            vanillaHit = cleared;
        }
        return clip(world, context, viewer, vanillaHit);
    }

    /**
     * Returns whichever of {@code vanillaHit} and an owner-window collision search is nearer to
     * the clip's own start point. Never returns null; falls back to {@code vanillaHit} whenever
     * the search is disabled, unsafe off-thread, or finds nothing nearer.
     */
    public static BlockHitResult clip(
            BlockGetter world, ClipContext context, Entity shooter, BlockHitResult vanillaHit
    ) {
        if (!ENABLED || world == null || context == null || vanillaHit == null) {
            return vanillaHit;
        }
        // Background server queries must not touch chunk attachments or walk support geometry:
        // the authoritative main-thread tick will resolve and synchronize the visible value.
        if (slabbed$isUnsafeAsyncShapeContext(world)) {
            return vanillaHit;
        }

        Vec3 from = context.getFrom();
        Vec3 to = context.getTo();
        if (from == null || to == null || from.equals(to)) {
            return vanillaHit;
        }

        CollisionContext shapeContext = shooter != null ? CollisionContext.of(shooter) : CollisionContext.empty();
        // Band clearing (maintainer ruling, 2026-08-31): a vanilla hit that resolved against a
        // dy-shifted cell may sit in that cell's VACATED band — space its un-lowered shape claims
        // but the drawn body has left. Re-march with the drawn shape substituted for shifted
        // cells; misses and hits on unshifted geometry pass through untouched, so clearing can
        // never make a plain block permeable.
        if (vanillaHit.getType() == HitResult.Type.BLOCK
                && isDyShiftedCell(world, vanillaHit.getBlockPos())) {
            vanillaHit = bandAwareVanillaClip(world, context, shapeContext);
        }
        OwnerWindowCollector collector = new OwnerWindowCollector(world, from, to, shapeContext);
        BlockGetter.traverseBlocks(
                from,
                to,
                collector,
                (c, cell) -> {
                    c.consumeCell(cell.getX(), cell.getY(), cell.getZ());
                    return null;
                },
                c -> null);

        BlockHitResult candidate = collector.best;
        if (candidate == null) {
            return vanillaHit;
        }
        return (BlockHitResult) SlabbedOffsetRaycast.selectNearestOwnedHit(from, vanillaHit, candidate);
    }

    private static boolean isDyShiftedCell(BlockGetter world, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        return Math.abs(SlabSupport.getYOffset(world, pos, state)) > EPS;
    }

    /**
     * Vanilla's own {@code BlockGetter.clip} per-cell logic, with one substitution: a dy-shifted
     * cell is tested against its DRAWN body ({@link SlabSupport#ownCollisionShape}) instead of
     * the un-lowered shape the shared shape method must keep answering for the movement
     * broadphase. This is the only place the vacated band is cleared — never in
     * {@code getCollisionShape} or any shared shape method, which on this Minecraft version IS
     * the movement broadphase's own overload.
     *
     * <p>Exact for these seams only: fluids are not marched because all three redirected call
     * sites clip with {@code ClipContext.Fluid.NONE}. A future seam with a fluid mode must not
     * route through this without adding the fluid arm.
     *
     * <p>Hit attribution follows vanilla: each hit is produced by
     * {@code clipWithInteractionOverride} against the cell's own position, so a projectile
     * reading {@code getBlockPos()} sees the owner, never an air cell.
     */
    private static BlockHitResult bandAwareVanillaClip(
            BlockGetter world, ClipContext context, CollisionContext shapeContext
    ) {
        Vec3 from = context.getFrom();
        Vec3 to = context.getTo();
        return BlockGetter.traverseBlocks(from, to, context, (c, cell) -> {
            BlockState state = world.getBlockState(cell);
            VoxelShape shape = c.getBlockShape(state, world, cell);
            if (!shape.isEmpty()
                    && !state.isAir()
                    && Math.abs(SlabSupport.getYOffset(world, cell, state)) > EPS) {
                shape = SlabSupport.ownCollisionShape(world, cell, state, shapeContext);
            }
            if (shape.isEmpty()) {
                return null;
            }
            return world.clipWithInteractionOverride(from, to, cell, shape, state);
        }, c -> {
            Vec3 delta = from.subtract(to);
            return BlockHitResult.miss(
                    to, Direction.getNearest(delta.x, delta.y, delta.z), BlockPos.containing(to));
        });
    }

    private static boolean slabbed$isUnsafeAsyncShapeContext(BlockGetter world) {
        if (world instanceof ServerLevel serverLevel) {
            return !serverLevel.getServer().isSameThread();
        }
        String name = Thread.currentThread().getName();
        return name.startsWith("Worker-Main") || name.contains("ForkJoinPool");
    }

    private static final class OwnerWindowCollector {
        private final BlockGetter world;
        private final Vec3 start;
        private final Vec3 end;
        private final CollisionContext shapeContext;

        private BlockHitResult best;
        private double bestDistSq = Double.POSITIVE_INFINITY;

        OwnerWindowCollector(BlockGetter world, Vec3 start, Vec3 end, CollisionContext shapeContext) {
            this.world = world;
            this.start = start;
            this.end = end;
            this.shapeContext = shapeContext;
        }

        void consumeCell(int x, int y, int z) {
            for (int distance = 1; distance <= SlabbedOffsetRaycast.OWNER_WINDOW_RADIUS; distance++) {
                testOwner(x, y - distance, z);
                testOwner(x, y + distance, z);
            }
        }

        private void testOwner(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) {
                return;
            }
            double dy = SlabSupport.getYOffset(world, pos, state);
            if (Math.abs(dy) <= 1.0e-6d) {
                return; // unshifted; already covered when the DDA visits this position directly
            }
            VoxelShape shape = SlabSupport.ownCollisionShape(world, pos, state, shapeContext);
            if (shape.isEmpty()) {
                return;
            }
            BlockHitResult hit = world.clipWithInteractionOverride(start, end, pos, shape, state);
            if (hit == null) {
                return;
            }
            double distSq = hit.getLocation().distanceToSqr(start);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = hit;
            }
        }
    }
}
