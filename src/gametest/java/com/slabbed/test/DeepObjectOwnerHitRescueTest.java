package com.slabbed.test;

import com.slabbed.placement.LandingHitValidationPolicy;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The RESCUE LANE for deep owners that present a full-cube interaction face (maintainer ruling,
 * 2026-08-26). Live defect: side-clicking a scaffolding column rendered at dy -1.0/-1.5/-2.5 was
 * server-rejected with "too far away from hit block" — the block appeared for a frame and vanished.
 *
 * <p><b>The mechanism, measured rather than assumed.</b> Vanilla's {@code handleUseItemOn} accepts a
 * use packet iff every component of {@code hit - Vec3.atCenterOf(pos)} is under {@code 1.0000001}
 * — per-axis, from the block CENTRE. For an owner whose body renders at {@code dy}, a TOP-face hit is
 * pinned to {@code posY + 1 + dy} and survives while {@code dy > -1.5}, but a SIDE-face hit sweeps the
 * whole body down to {@code posY + dy} and starts failing at {@code dy <= -1.0}. That sliding
 * threshold — not a single cliff — is why the live report reads as "sometimes".
 *
 * <p><b>Why the policy declined.</b> {@code Family.FULL_BLOCK} resolves through {@code isSolidRender()},
 * an OCCLUSION property, so scaffolding — which does not occlude but whose {@code getInteractionShape}
 * is a full cube UNCONDITIONALLY — was denied the shift on every face. That is eligibility following a
 * class-shaped proxy instead of geometry, which LAW.md clause 2 forbids.
 *
 * <p><b>Precedent.</b> {@code 26e9b907} broadened this same policy for chain and dripstone owners
 * against this same failure ("vanilla's own hit-distance tolerance rejected the packet before useOn
 * ran"). {@link UpwardContinuationValidationTest} is that fix's regression matrix; its four
 * {@code requireVanillaCenter} rows assert {@code componentDistance <= tolerance} TOGETHER WITH a NaN
 * shift — i.e. "do not shift where vanilla already accepts", NOT "never shift these families". Both
 * halves of the rescue lane's scope exist to keep those rows green, and {@link
 * #aFenceOwnerIsStillDeniedTheShift} is the executable statement of that.
 *
 * <p>The policy is a pure function, so these rows call it directly. That is deliberate: the packet
 * seam it feeds lives in a {@code ServerGamePacketListenerImpl} mixin ABOVE every headless entry
 * point, so no gametest tier can reach the rejection end-to-end — which is exactly why this defect
 * survived 609 green rows. Pinning the pure function is the most that is reachable here; the
 * end-to-end half stays live-only.
 */
public final class DeepObjectOwnerHitRescueTest {

    private static final double OWNER_DY = -1.5d;

    /** A hit low on the owner's TRANSLATED body — the aim vanilla rejects unshifted. */
    private static Vec3 lowSideHit(BlockPos owner) {
        return new Vec3(owner.getX() + 1.0d, owner.getY() + OWNER_DY + 0.1d, owner.getZ() + 0.5d);
    }

    /** A hit on the owner's TRANSLATED top plane — the aim vanilla accepts unshifted. */
    private static Vec3 translatedTopHit(BlockPos owner) {
        return new Vec3(owner.getX() + 0.5d, owner.getY() + 1.0d + OWNER_DY, owner.getZ() + 0.5d);
    }

    private static double policy(BlockPos owner, BlockState ownerState, Direction face, Vec3 hit) {
        return LandingHitValidationPolicy.shiftedCenterDy(
                owner, ownerState, OWNER_DY, face, hit, Blocks.SCAFFOLDING.defaultBlockState());
    }

    /** Guards the premise that the scene actually reproduces the live geometry. */
    private static void requireVanillaWouldReject(GameTestHelper h, BlockPos owner, Vec3 hit, String what) {
        double component = Math.abs(hit.y - (owner.getY() + 0.5d));
        if (component < 1.0000001d) {
            throw h.assertionException(owner, "premise drift: " + what + " must be an aim vanilla REJECTS "
                    + "unshifted (component=" + component + " < 1.0000001) or the row proves nothing");
        }
    }

    /**
     * THE FIX. A scaffolding owner side-clicked low on its deep body gets the shift, so the server
     * validates the packet the player legitimately aimed at.
     *
     * <p>MUTATION that must redden this row alone: delete the rescue lane in
     * {@code LandingHitValidationPolicy} (restore the bare {@code ownerFamily != FULL_BLOCK} bail).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aDeepScaffoldingOwnerSideClickIsRescued(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(2, 2, 2));
        Vec3 hit = lowSideHit(owner);
        requireVanillaWouldReject(h, owner, hit, "the scaffolding side aim");

        double shift = policy(owner, Blocks.SCAFFOLDING.defaultBlockState(), Direction.EAST, hit);
        if (Double.doubleToRawLongBits(shift) != Double.doubleToRawLongBits(OWNER_DY)) {
            throw h.assertionException(owner, "a deep scaffolding owner must receive the shift "
                    + OWNER_DY + ", got " + shift + " — NaN here is the live refusal: vanilla's "
                    + "unshifted centre check kills the packet and the block vanishes after a frame");
        }
        h.succeed();
    }

    /**
     * RESCUE-ONLY. The same owner, aimed at its translated TOP where vanilla already accepts, must
     * still get vanilla's centre. This is what keeps the lane from changing an ADMITTED decision, and
     * it is the property {@link UpwardContinuationValidationTest}'s four rows depend on.
     *
     * <p>MUTATION that must redden this row alone: drop {@code vanillaWouldRejectOnYAlone} from the
     * rescue lane's condition.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anAimVanillaAlreadyAcceptsIsNotShifted(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(2, 2, 2));
        Vec3 hit = translatedTopHit(owner);
        double component = Math.abs(hit.y - (owner.getY() + 0.5d));
        if (component >= 1.0000001d) {
            throw h.assertionException(owner, "premise drift: the translated-top aim must be one vanilla "
                    + "ACCEPTS unshifted (component=" + component + "), or this row is not testing "
                    + "rescue-only behaviour at all");
        }

        double shift = policy(owner, Blocks.SCAFFOLDING.defaultBlockState(), Direction.UP, hit);
        if (!Double.isNaN(shift)) {
            throw h.assertionException(owner, "an aim vanilla already accepts must keep vanilla's centre, "
                    + "got shift " + shift + " — a rescue that also fires on admitted aims is no longer a "
                    + "rescue, and it reddens the upward-continuation matrix");
        }
        h.succeed();
    }

    /**
     * GEOMETRY, NOT CLASS — the narrowness control. An oak fence at the SAME depth with the SAME
     * rejected aim must still be denied, because its interaction shape is vanilla's default EMPTY: it
     * presents no full-cube face to click. Without this row the rescue lane could be widened to every
     * OBJECT owner and nothing here would notice, silently overturning four pinned rows in
     * {@link UpwardContinuationValidationTest}.
     *
     * <p>MUTATION that must redden this row alone: drop {@code presentsFullCubeInteractionFace} from
     * the rescue lane's condition.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aFenceOwnerIsStillDeniedTheShift(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(2, 2, 2));
        Vec3 hit = lowSideHit(owner);
        requireVanillaWouldReject(h, owner, hit, "the fence side aim");

        double shift = policy(owner, Blocks.OAK_FENCE.defaultBlockState(), Direction.EAST, hit);
        if (!Double.isNaN(shift)) {
            throw h.assertionException(owner, "an oak fence presents no full-cube interaction face, so it "
                    + "must keep vanilla's centre; got shift " + shift + ". Widening past the "
                    + "full-cube-face predicate contradicts the four pinned upward-continuation rows.");
        }
        h.succeed();
    }

    /**
     * THE CALIBRATION, in the same run: an ordinary solid owner at the same depth with the same aim
     * already received the shift before this slice, through the pre-existing FULL_BLOCK envelope. If
     * this row ever fails, the scene itself stopped reproducing the geometry and the three rows above
     * are measuring nothing.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aFullBlockOwnerAtTheSameDepthWasAlreadyRescued(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(2, 2, 2));
        Vec3 hit = lowSideHit(owner);
        requireVanillaWouldReject(h, owner, hit, "the stone side aim");

        double shift = policy(owner, Blocks.STONE.defaultBlockState(), Direction.EAST, hit);
        if (Double.doubleToRawLongBits(shift) != Double.doubleToRawLongBits(OWNER_DY)) {
            throw h.assertionException(owner, "calibration: a solid full-block owner at " + OWNER_DY
                    + " must already receive the shift (pre-existing behaviour), got " + shift);
        }
        h.succeed();
    }
}
