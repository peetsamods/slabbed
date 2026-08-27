package com.slabbed.test;

import com.slabbed.placement.LandingHitValidationPolicy;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * The HANGING-UNDERSIDE lane (maintainer ruling, 2026-08-26).
 *
 * <p>Live defect: hanging a chain under a LOWERED LANTERN was refused. A hanging lantern's body
 * starts {@code 0.0625} above its own cell floor, so at {@code dy -1.0} its visible underside sits at
 * {@code posY-0.9375}; the honest click lands {@code 1.4375} below the cell centre and vanilla's
 * unshifted per-axis check kills the use packet before {@code useOn} runs. Measured live: 8 of 8
 * rejections at exactly that hit Y.
 *
 * <p><b>Why this lane is narrow, and why that is the finding rather than the caution.</b> The obvious
 * fix — generalise the vertical-chain lane to "any owner on a DOWN face" — was MEASURED before being
 * proposed: applied with zero test edits against the full suite, it contradicts FIVE pinned
 * assertions. It is also forbidden in writing by
 * {@code handoffs/packages/PKG-20260727-chain-follower-shift-policy.md}: "recognize the owner only
 * through isBeta35VerticalChainVisibleOwnerObject", "Do not ... admit general OBJECT owners", and its
 * acceptance criteria name horizontal chain and non-chain object as required refusals. So the five
 * stay refused, and the rows below are the executable statement of that — they are not decoration,
 * they are the boundary the spec draws.
 *
 * <p>The separation is GEOMETRIC, not a class list (LAW.md clause 2): the owner's body must HANG
 * CLEAR of its own cell floor (a hanging lantern does, at 0.0625; a fence, a vertical chain and a
 * tip-up pointed dripstone all start at 0.0) AND the hit must lie ON that underside plane (a
 * horizontal chain hangs clear too, but its pinned hit sits 0.40625 BELOW its body, not on it).
 *
 * <p>These rows call the policy directly because it is a pure function and the packet seam it feeds
 * sits in a {@code ServerGamePacketListenerImpl} mixin ABOVE every headless entry point — no gametest
 * tier can reach the rejection end to end. That structural gap is why the original defect survived
 * 613 green rows, and it is why the live half stays owed.
 */
public final class HangingUndersideHitRescueTest {

    private static final double OWNER_DY = -1.0d;
    private static final double VANILLA_TOLERANCE = 1.0000001d;

    private static BlockState hangingLantern() {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, Boolean.TRUE);
    }

    /** The exact underside plane of {@code state}'s body once translated by {@link #OWNER_DY}. */
    private static double undersideOf(BlockState state, BlockPos owner) {
        return owner.getY() + OWNER_DY
                + state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).min(Direction.Axis.Y);
    }

    private static double policy(BlockPos owner, BlockState ownerState, Direction face, Vec3 hit) {
        return LandingHitValidationPolicy.shiftedCenterDy(
                owner, ownerState, OWNER_DY, face, hit, Blocks.IRON_CHAIN.defaultBlockState());
    }

    private static void requireVanillaWouldReject(GameTestHelper h, BlockPos owner, Vec3 hit, String what) {
        double component = Math.abs(hit.y - (owner.getY() + 0.5d));
        if (component < VANILLA_TOLERANCE) {
            throw h.assertionException(owner, "premise drift: " + what + " must be an aim vanilla "
                    + "REJECTS unshifted (component=" + component + "), or the row proves nothing");
        }
    }

    /**
     * THE FIX — the live gesture. A chain hung under a lantern that renders a block low.
     *
     * <p>MUTATION that must redden this row alone: delete the hanging-underside lane.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aChainUnderALoweredLanternIsRescued(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(2, 2, 2));
        BlockState lantern = hangingLantern();
        Vec3 hit = new Vec3(owner.getX() + 0.5d, undersideOf(lantern, owner), owner.getZ() + 0.5d);
        requireVanillaWouldReject(h, owner, hit, "the lantern underside aim");

        double shift = policy(owner, lantern, Direction.DOWN, hit);
        if (Double.doubleToRawLongBits(shift) != Double.doubleToRawLongBits(OWNER_DY)) {
            throw h.assertionException(owner, "a chain hung under a lowered hanging lantern must "
                    + "receive the shift " + OWNER_DY + ", got " + shift + " — NaN here is the live "
                    + "refusal: vanilla's unshifted centre check kills the packet and the block "
                    + "flickers out. Live hit was posY-0.9375, 8/8 rejections at that value.");
        }
        h.succeed();
    }

    /**
     * THE HANG TERM. A STANDING lantern — same block, same depth, same aim at its own body bottom —
     * does not hang clear of its cell floor, so it is not admitted. This is what stops the lane
     * degenerating into "any lantern", and it keeps the predicate geometric rather than class-keyed.
     *
     * <p>MUTATION that must redden this row alone: drop the {@code bodyMinY > EPSILON} term.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aStandingLanternIsNotRescued(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(2, 2, 2));
        BlockState standing = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, Boolean.FALSE);
        if (standing.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).min(Direction.Axis.Y) > 1.0e-6d) {
            throw h.assertionException(owner, "premise drift: a STANDING lantern must rest on its own "
                    + "cell floor, or this row is not testing the hang term");
        }
        Vec3 hit = new Vec3(owner.getX() + 0.5d, undersideOf(standing, owner), owner.getZ() + 0.5d);
        requireVanillaWouldReject(h, owner, hit, "the standing-lantern aim");

        double shift = policy(owner, standing, Direction.DOWN, hit);
        if (!Double.isNaN(shift)) {
            throw h.assertionException(owner, "a standing lantern rests ON its cell floor — there is no "
                    + "gap beneath it to aim into, so it must keep vanilla's centre; got " + shift);
        }
        h.succeed();
    }

    /**
     * THE UNDERSIDE TERM, and the guard on the spec's boundary. A pointed dripstone with its tip UP
     * rests on its cell floor, so it is excluded by the hang term — this is one of the five
     * assertions {@code PKG-20260727} requires to stay refused, restated here at the lane that could
     * most plausibly have widened onto it.
     *
     * <p>MUTATION that must redden this row alone: drop the {@code bodyMinY > EPSILON} term (it
     * shares that mutation with the standing-lantern row above — both are the hang term, stated for
     * two different families, and either failing alone still localises the break).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aTipUpDripstoneIsStillDeniedTheShift(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(2, 2, 2));
        BlockState tipUp = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP);
        Vec3 hit = new Vec3(owner.getX() + 0.5d, owner.getY() + OWNER_DY, owner.getZ() + 0.5d);
        requireVanillaWouldReject(h, owner, hit, "the tip-up dripstone aim");

        double shift = policy(owner, tipUp, Direction.DOWN, hit);
        if (!Double.isNaN(shift)) {
            throw h.assertionException(owner, "a tip-UP pointed dripstone rests on its cell floor and "
                    + "is a pinned refusal (PKG-20260727 acceptance criteria); got " + shift
                    + ". Widening onto it contradicts a written specification boundary.");
        }
        h.succeed();
    }

    /**
     * THE FENCE, restated at this lane. An oak fence starts its body at its cell floor, so the hang
     * term excludes it — the same answer {@code LandingRuleLawTest} pins twice (at -1.0 with a held
     * lantern, and at -1.5 with held dripstone) and which the spec names as {@code non-chain object}.
     * Stated here as well so a future edit to THIS lane cannot quietly widen onto it while the two
     * distant pins keep passing for their own unrelated reasons.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anOakFenceIsStillDeniedTheShift(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(2, 2, 2));
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        Vec3 hit = new Vec3(owner.getX() + 0.5d, owner.getY() + OWNER_DY, owner.getZ() + 0.5d);
        requireVanillaWouldReject(h, owner, hit, "the fence underside aim");

        double shift = policy(owner, fence, Direction.DOWN, hit);
        if (!Double.isNaN(shift)) {
            throw h.assertionException(owner, "an oak fence rests on its cell floor and is a pinned "
                    + "refusal in two places plus the PKG-20260727 acceptance criteria; got " + shift);
        }
        h.succeed();
    }

    /**
     * THE RESCUE-ONLY TERM. The same lantern aimed where vanilla ALREADY accepts keeps vanilla's
     * centre — the lane rescues refusals, it never re-decides an admitted aim. Uses a shallow owner
     * whose underside stays inside the tolerance window.
     *
     * <p>MUTATION that must redden this row alone: drop {@code vanillaWouldRejectOnYAlone}.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anAimVanillaAlreadyAcceptsKeepsVanillaCentre(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(2, 2, 2));
        BlockState lantern = hangingLantern();
        double shallowDy = -0.5d;
        double underside = owner.getY() + shallowDy
                + lantern.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).min(Direction.Axis.Y);
        Vec3 hit = new Vec3(owner.getX() + 0.5d, underside, owner.getZ() + 0.5d);
        double component = Math.abs(hit.y - (owner.getY() + 0.5d));
        if (component >= VANILLA_TOLERANCE) {
            throw h.assertionException(owner, "premise drift: this aim must be one vanilla ACCEPTS "
                    + "unshifted (component=" + component + "), or the row is not testing rescue-only");
        }

        double shift = LandingHitValidationPolicy.shiftedCenterDy(
                owner, lantern, shallowDy, Direction.DOWN, hit, Blocks.IRON_CHAIN.defaultBlockState());
        if (!Double.isNaN(shift)) {
            throw h.assertionException(owner, "an aim vanilla already accepts must keep vanilla's "
                    + "centre, got " + shift + " — a rescue that fires on admitted aims is not a rescue");
        }
        h.succeed();
    }
}
