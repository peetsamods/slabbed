package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.compat.CompatHooks;
import com.slabbed.placement.LandingHitValidationPolicy;
import com.slabbed.placement.LandingResolver;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * THE GOES LANDING-RULE MATRIX — the executable spec of the landing rule: <b>a placement lands on the
 * clicked visible surface</b>, for every item family, every owner shape, every depth.
 *
 * <p>Companion to {@link NeighborUpdateInvarianceTest}, which pins STAYS (the S-2 law gate). These
 * rows pin GOES. Every row here is PURE MATH: it synthesises a {@link LandingResolver.PlacementAim}
 * directly and asserts the resolver's number and the server hit-admission policy's answer for the same
 * aim. No world is built, no block is placed, no store is touched — so a failure names the arithmetic,
 * never a fixture.
 *
 * <p>Landing heights are compared by RAW BITS, not by epsilon: the whole point of the store is that a
 * placed block reads back the exact value it was given.
 *
 * <p>PORT NOTES (1.21.1 vs the 26.2 donor):
 * <ul>
 *   <li>The donor's legacy {@code BlockGetter} overload of {@code LandingResolver.resolve} is not
 *       ported to this line, so the C5 row's {@code powderLegacyWorldResolution} probe (which asserted
 *       that overload refuses use-created contact) is dropped with it.</li>
 *   <li>The donor drives its compat-boundary controls through a custom paired test block
 *       ({@code PlacementCaptureBoundaryGameTest.PAIR_BLOCK}) that does not exist here; the same
 *       boundary is driven through the compat test-override seam applied to a vanilla block, matching
 *       {@link PlacementCaptureBoundaryGameTest}'s own house pattern.</li>
 *   <li>No 26.2-only block was needed by any ported row: {@code Blocks.BED.red()} becomes
 *       {@code Blocks.RED_BED}, and every other subject exists unchanged on 1.21.1.</li>
 * </ul>
 */
public final class LandingRuleLawTest {

    private static boolean sameBits(double a, double b) {
        return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b);
    }

    private static void pass(TestContext h, String id) {
        Slabbed.LOGGER.info("C3_FOCUSED | slabbed_gametest:{} | PASS", id);
        h.complete();
    }

    /**
     * Owner shape x depth x reciprocal-pair matrix. A door or a bed clicked onto the visible top of a
     * lowered owner lands on that visible top, whatever the owner's shape and however deep it is: a
     * bottom slab's top is half a block down, everything else's top is the cell top. The same aim must
     * be ADMITTED by the server's shifted-center policy at exactly the owner's own depth, or the
     * placement is silently thrown away before the resolver is ever consulted.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void c3_pair_owner_shape_depth_placement_and_validation_matrix(TestContext h) {
        BlockPos owner = h.getAbsolutePos(new BlockPos(3, 6, 3));
        BlockState[] owners = {
                Blocks.STONE.getDefaultState(),
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                Blocks.CHEST.getDefaultState()
        };
        BlockState[] heldPairs = {
                Blocks.OAK_DOOR.getDefaultState(),
                Blocks.RED_BED.getDefaultState()
        };
        List<String> violations = new ArrayList<>();
        for (BlockState ownerState : owners) {
            for (double depth : new double[]{-1.0d, -2.0d}) {
                for (BlockState held : heldPairs) {
                    Vec3d hit = new Vec3d(owner.getX() + 0.5d, owner.getY() + depth + 0.25d,
                            owner.getZ() + 0.5d);
                    LandingResolver.PlacementAim aim = new LandingResolver.PlacementAim(
                            owner, ownerState, depth, Direction.UP, hit, false);
                    LandingResolver.PlacementResolution resolution = LandingResolver.resolve(
                            aim, owner.up(), held, LandingResolver.Family.PAIRED_FLOOR_SEAT);
                    boolean ownerIsBottomSlab = ownerState.getBlock() instanceof SlabBlock
                            && ownerState.get(SlabBlock.TYPE) == SlabType.BOTTOM;
                    double expected = depth + (ownerIsBottomSlab ? -0.5d : 0.0d);
                    double validation = LandingHitValidationPolicy.shiftedCenterDy(
                            owner, ownerState, depth, Direction.UP, hit, held);
                    if (resolution == null
                            || !sameBits(resolution.landingDy(), expected)
                            || !sameBits(validation, depth)) {
                        violations.add("owner=" + ownerState + " depth=" + depth + " held=" + held
                                + " resolution=" + resolution + " validation=" + validation
                                + " expected=" + expected);
                    }
                }
            }
        }
        h.assertTrue(violations.isEmpty(),
                "C3 owner/depth landing+admission matrix failed:\n  " + String.join("\n  ", violations));
        pass(h, "landing_rule_law_test_c3_pair_owner_shape_depth_placement_and_validation_matrix");
    }

    /**
     * The admission policy must stay NARROW. Each control below is a hit the policy must refuse, so a
     * widened server tolerance shows up here rather than as blocks landing in places nobody aimed at.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void c3_pair_validation_negative_controls(TestContext h) {
        BlockPos owner = h.getAbsolutePos(new BlockPos(3, 5, 3));
        Vec3d inside = new Vec3d(owner.getX() + 0.5d, owner.getY() - 1.5d, owner.getZ() + 0.5d);
        BlockState door = Blocks.OAK_DOOR.getDefaultState();
        double positive = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.STONE.getDefaultState(), -2.0d, Direction.UP, inside, door);
        double nonUp = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.STONE.getDefaultState(), -2.0d, Direction.NORTH, inside, door);
        double partial = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.OAK_FENCE.getDefaultState(), -2.0d, Direction.UP, inside, door);
        double outside = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.STONE.getDefaultState(), -2.0d, Direction.UP,
                new Vec3d(owner.getX() + 1.5d, owner.getY() - 1.5d, owner.getZ() + 0.5d), door);
        double air = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.AIR.getDefaultState(), -2.0d, Direction.UP, inside, door);
        Predicate<BlockState> previous = CompatHooks.shouldSkipSlabSupportTestOverride;
        double compat;
        try {
            CompatHooks.shouldSkipSlabSupportTestOverride = state -> state.isOf(Blocks.GOLD_BLOCK);
            compat = LandingHitValidationPolicy.shiftedCenterDy(
                    owner, Blocks.STONE.getDefaultState(), -2.0d, Direction.UP, inside,
                    Blocks.GOLD_BLOCK.getDefaultState());
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = previous;
        }
        h.assertTrue(sameBits(positive, -2.0d)
                        && Double.isNaN(nonUp)
                        && Double.isNaN(partial)
                        && Double.isNaN(outside)
                        && Double.isNaN(air)
                        && Double.isNaN(compat),
                "C3 validation widened: positive=" + positive + " nonUp=" + nonUp
                        + " partial=" + partial + " outside=" + outside + " air=" + air
                        + " compat=" + compat);
        pass(h, "landing_rule_law_test_c3_pair_validation_negative_controls");
    }

    /**
     * Shared-cause discriminator for ordinary objects: unrelated object families (a pot, a gate, a
     * button, a block-entity block, a wall-mounted ladder, a hanging sign) must all enter the SAME
     * placement-time aim authority and the SAME deep-hit admission authority — one rule, not ten
     * hand-written lanes that agree only by luck. Thin layers and use-created full cubes stay distinct
     * families; that boundary is asserted here too so a future edit cannot quietly collapse them.
     *
     * <p>This row also pins the view-free full-cube substitution {@link LandingResolver#classify}
     * makes on 1.21.1: every subject below must classify as an OBJECT, and plain stone must not.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void c4ObjectsShareLandingAndHitValidationAuthority(TestContext h) {
        BlockPos owner = h.getAbsolutePos(new BlockPos(3, 5, 3));
        BlockState ownerState = Blocks.STONE.getDefaultState();
        double ownerDy = -1.5d;
        Vec3d hit = new Vec3d(owner.getX() + 0.5d, owner.getY() + ownerDy + 0.25d, owner.getZ() + 0.5d);
        LandingResolver.PlacementAim aim = new LandingResolver.PlacementAim(
                owner, ownerState, ownerDy, Direction.UP, hit, false);
        BlockState[] objects = {
                Blocks.FLOWER_POT.getDefaultState(),
                Blocks.OAK_FENCE_GATE.getDefaultState(),
                Blocks.ACACIA_BUTTON.getDefaultState(),
                Blocks.CONDUIT.getDefaultState(),
                Blocks.LADDER.getDefaultState(),
                Blocks.OAK_HANGING_SIGN.getDefaultState()
        };

        LandingResolver.Family sharedFamily = LandingResolver.classify(objects[0]);
        List<String> violations = new ArrayList<>();
        for (BlockState object : objects) {
            LandingResolver.Family family = LandingResolver.classify(object);
            LandingResolver.PlacementResolution resolution =
                    LandingResolver.resolve(aim, owner.up(), object, family);
            double validation = LandingHitValidationPolicy.shiftedCenterDy(
                    owner, ownerState, ownerDy, Direction.UP, hit, object);
            if (family == LandingResolver.Family.UNSUPPORTED
                    || family != sharedFamily
                    || resolution == null
                    || !sameBits(resolution.landingDy(), ownerDy)
                    || !sameBits(validation, ownerDy)) {
                violations.add(object.getBlock() + ": family=" + family
                        + " resolution=" + resolution + " validation=" + validation);
            }
        }

        if (sharedFamily != LandingResolver.Family.OBJECT) {
            violations.add("ordinary objects no longer classify as OBJECT: " + sharedFamily);
        }
        LandingResolver.Family stoneFamily = LandingResolver.classify(Blocks.STONE.getDefaultState());
        if (stoneFamily != LandingResolver.Family.FULL_BLOCK) {
            violations.add("plain stone must classify as FULL_BLOCK, got " + stoneFamily);
        }

        // PORT ADDITION (not in the donor row): runtime proof that the admission policy's no-item-use
        // reflection probe actually RESOLVED on this line instead of failing closed. A lowered fence
        // gate is an ordinary object carrying OPEN/POWERED whose class declares Minecraft's own
        // no-item use hook, so aiming into its translated cell while HOLDING a block is target-owned
        // use and must be admitted at the owner's depth. Had the probe found no method name, this same
        // call answers NaN — an object owner has no other admission branch — so the row is decisive.
        double statefulOwnerAdmission = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.OAK_FENCE_GATE.getDefaultState(), ownerDy, Direction.UP, hit,
                Blocks.STONE.getDefaultState());
        if (!sameBits(statefulOwnerAdmission, ownerDy)) {
            violations.add("no-item-use reflection probe failed closed: stateful object owner"
                    + " admission=" + statefulOwnerAdmission + " (wanted " + ownerDy + ")");
        }

        LandingResolver.Family carpetFamily =
                LandingResolver.classify(Blocks.MOSS_CARPET.getDefaultState());
        LandingResolver.Family powderFamily =
                LandingResolver.classify(Blocks.POWDER_SNOW.getDefaultState());
        if (carpetFamily != LandingResolver.Family.AIM_KEYED_FLOOR_SEAT
                || powderFamily != LandingResolver.Family.USE_CREATED_FULL_CUBE_CONTACT
                || carpetFamily == sharedFamily
                || powderFamily == sharedFamily) {
            violations.add("thin-layer / use-created family boundary collapsed: objects=" + sharedFamily
                    + " carpet=" + carpetFamily + " powder=" + powderFamily);
        }
        h.assertTrue(violations.isEmpty(),
                "ordinary objects do not share one landing/hit authority:\n  "
                        + String.join("\n  ", violations));
        pass(h, "landing_rule_law_test_c4_objects_share_landing_and_hit_validation_authority");
    }

    /**
     * Thin layers stay UP-only (a carpet has no side to attach to), while use-created full cubes own
     * contact on all six faces and may also land in the clicked cell itself when that cell is
     * replaceable. The same final-state compat gate keeps a compat mod's own blocks out of both.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void c5AimKeyedFamilySharesLandingValidationAndCompatAuthority(TestContext h) {
        BlockPos owner = h.getAbsolutePos(new BlockPos(3, 4, 3));
        BlockState ownerState = Blocks.STONE.getDefaultState();
        double ownerDy = -1.0d;
        Vec3d hit = new Vec3d(owner.getX() + 0.5d, owner.getY() - 0.5d, owner.getZ() + 0.5d);
        LandingResolver.PlacementAim upAim = new LandingResolver.PlacementAim(
                owner, ownerState, ownerDy, Direction.UP, hit, false);
        LandingResolver.PlacementAim sideAim = new LandingResolver.PlacementAim(
                owner, ownerState, ownerDy, Direction.SOUTH, hit, false);
        LandingResolver.PlacementAim replacementAim = new LandingResolver.PlacementAim(
                owner, ownerState, ownerDy, Direction.UP, hit, true);
        BlockState carpet = Blocks.MOSS_CARPET.getDefaultState();
        BlockState powder = Blocks.POWDER_SNOW.getDefaultState();
        List<String> violations = new ArrayList<>();

        LandingResolver.Family carpetFamily = LandingResolver.classify(carpet);
        LandingResolver.PlacementResolution carpetUp =
                LandingResolver.resolve(upAim, owner.up(), carpet, carpetFamily);
        LandingResolver.PlacementResolution carpetSide =
                LandingResolver.resolve(sideAim, owner.south(), carpet, carpetFamily);
        double carpetValidationUp = LandingHitValidationPolicy.shiftedCenterDy(
                owner, ownerState, ownerDy, Direction.UP, hit, carpet);
        double carpetValidationSide = LandingHitValidationPolicy.shiftedCenterDy(
                owner, ownerState, ownerDy, Direction.SOUTH, hit, carpet);
        if (carpetFamily != LandingResolver.Family.AIM_KEYED_FLOOR_SEAT
                || carpetUp == null
                || !sameBits(carpetUp.landingDy(), ownerDy)
                || carpetSide != null
                || !sameBits(carpetValidationUp, ownerDy)
                || !Double.isNaN(carpetValidationSide)) {
            violations.add("carpet: family=" + carpetFamily + " up=" + carpetUp + " side=" + carpetSide
                    + " validationUp=" + carpetValidationUp + " validationSide=" + carpetValidationSide);
        }

        LandingResolver.Family powderFamily = LandingResolver.classify(powder);
        LandingResolver.PlacementResolution powderUp =
                LandingResolver.resolve(upAim, owner.up(), powder, powderFamily);
        LandingResolver.PlacementResolution powderSide =
                LandingResolver.resolve(sideAim, owner.south(), powder, powderFamily);
        LandingResolver.PlacementResolution powderReplacement =
                LandingResolver.resolve(replacementAim, owner, powder, powderFamily);
        double powderValidationUp = LandingHitValidationPolicy.shiftedCenterDy(
                owner, ownerState, ownerDy, Direction.UP, hit, powder);
        double powderValidationSide = LandingHitValidationPolicy.shiftedCenterDy(
                owner, ownerState, ownerDy, Direction.SOUTH, hit, powder);
        if (powderFamily != LandingResolver.Family.USE_CREATED_FULL_CUBE_CONTACT
                || powderUp == null
                || powderSide == null
                || powderReplacement == null
                || !powderReplacement.sameCellUpgrade()
                || !sameBits(powderUp.landingDy(), ownerDy)
                || !sameBits(powderSide.landingDy(), ownerDy)
                || !sameBits(powderReplacement.landingDy(), ownerDy)
                || !sameBits(powderValidationUp, ownerDy)
                || !sameBits(powderValidationSide, ownerDy)) {
            violations.add("powder: family=" + powderFamily + " up=" + powderUp + " side=" + powderSide
                    + " replacement=" + powderReplacement
                    + " validationUp=" + powderValidationUp
                    + " validationSide=" + powderValidationSide);
        }

        Predicate<BlockState> previous = LandingResolver.compatFinalStateTestOverride;
        try {
            LandingResolver.compatFinalStateTestOverride =
                    state -> state.isOf(Blocks.MOSS_CARPET) || state.isOf(Blocks.POWDER_SNOW);
            for (BlockState state : new BlockState[]{carpet, powder}) {
                LandingResolver.Family family = LandingResolver.classify(state);
                LandingResolver.PlacementResolution resolution =
                        LandingResolver.resolve(upAim, owner.up(), state, family);
                double validation = LandingHitValidationPolicy.shiftedCenterDy(
                        owner, ownerState, ownerDy, Direction.UP, hit, state);
                if (resolution != null || !Double.isNaN(validation)) {
                    violations.add("compat-owned " + state.getBlock() + " was authored anyway: resolution="
                            + resolution + " validation=" + validation);
                }
            }
        } finally {
            LandingResolver.compatFinalStateTestOverride = previous;
        }

        h.assertTrue(violations.isEmpty(),
                "thin-layer / use-created contact authority boundary failed:\n  "
                        + String.join("\n  ", violations));
        pass(h, "landing_rule_law_test_c5_aim_keyed_family_shares_landing_validation_and_compat_authority");
    }
}
