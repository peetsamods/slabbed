package com.slabbed.util;

/**
 * The envelope a manually authored placement height must stay inside, and the size of one step.
 *
 * <p><b>The floor is pinned, not chosen.</b> {@code SlabbedOffsetColliderClip#OWNER_REACH} is the
 * number of cells the projectile / line-of-sight / explosion clip supplement searches upward for an
 * owner, so a height deeper than that would leave a block outside its own clip coverage: it would
 * draw lowered while arrows, sight lines and explosions kept seeing the unlowered box. Deriving
 * {@link #MIN_DY} from that field means the two can never diverge silently. This class lives in this
 * package for exactly that reason — {@code OWNER_REACH} is package-private and must stay the single
 * definition (shared-predicate rule: one definition, never a copy per call site).
 *
 * <p><b>The ceiling is 0.0</b> because positive dy is retired on this line. Placement itself reaches
 * these bounds naturally rather than by clamping; a manual nudge is the first writer that can ask for
 * an arbitrary depth, so it clamps explicitly.
 */
public final class ManualDyEnvelope {

    /** One nudge, in blocks. The magnitude never travels on the wire — only a signed direction does. */
    public static final double STEP = 0.5d;

    /** Deepest authorable height. Pinned to the clip supplement's owner search depth. */
    public static final double MIN_DY = -(double) SlabbedOffsetColliderClip.OWNER_REACH;

    /** Shallowest authorable height: flush with the grid cell. */
    public static final double MAX_DY = 0.0d;

    private static final double EPS = 1.0e-9d;

    private ManualDyEnvelope() {
    }

    public static boolean inEnvelope(double dy) {
        return Double.isFinite(dy) && dy >= MIN_DY - EPS && dy <= MAX_DY + EPS;
    }

    /**
     * One step in {@code direction} from an IN-ENVELOPE current value, clamped to the envelope.
     * The caller MUST refuse an out-of-envelope current value first: clamping one would invert the
     * key's direction, because a stored {@code +1.0} stepped "up" would land on {@code 0.0} and move
     * the block DOWN by a full block.
     */
    public static double step(double current, int direction) {
        return Math.min(MAX_DY, Math.max(MIN_DY, current + direction * STEP));
    }
}
