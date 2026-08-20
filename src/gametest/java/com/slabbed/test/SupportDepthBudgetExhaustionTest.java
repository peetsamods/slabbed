package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/**
 * INTERPENETRATION AT THE DEPTH BUDGET — a lowered tower whose courses all sit on each other's real
 * top face reads one continuous height, until the course that exhausts
 * {@link SlabSupport#MAX_SUPPORT_RESOLVE_DEPTH} reads {@link SlabSupport#minResolvedDy()} instead
 * and sinks INTO the course below it.
 *
 * <p><b>The mechanism, stated as the arithmetic rather than as a story.</b> One unit of the budget
 * is spent per COURSE the support walk descends. The half-height arm of {@code supportSeatDy}
 * deepens by {@code 0.5} per unit it spends, and the budget is sized on exactly that assumption
 * ("{@code ceil(-cap / 0.5)} courses to SATURATE, {@code + 2} headroom"). The FULL-HEIGHT arm
 * spends the same unit and deepens by ZERO — it passes its support's height through unchanged,
 * which is what makes a stack of blocks on a lowered block one continuous tower instead of a
 * staircase. A tower of full-height courses therefore consumes the whole budget without descending
 * anything, and {@code loweredFollowerDy}'s exhaustion arm answers "at least this deep, and I
 * stopped counting" — {@code minResolvedDy()} — about a walk that in fact descended nowhere.
 *
 * <p><b>Which cells this can reach, so the row is not over-read.</b> A cell holding a stored
 * placement height answers from it and the walk terminates there, so a tower built click-by-click
 * in a world that has the placement store never recurses far enough to exhaust anything. The
 * reachable population is cells that render lowered while holding NO stored height: every cell of
 * every world saved before the store existed (the migration case this line's resolver explicitly
 * contracts to keep identical), authored and command-written cells, and worldgen. This fixture
 * builds exactly that state — anchors present, stored heights cleared — and asserts it before
 * measuring.
 *
 * <p><b>Cap-independent by construction.</b> The property asserted is that the tower is CONTINUOUS,
 * never that a particular number appears, so the row reads the same way at the shipped cap (where
 * the sink is half a block) and with the deeper alphabet armed (where it is a block and a half).
 * Do not rewrite it against a literal.
 */
public final class SupportDepthBudgetExhaustionTest {

    private static final double EPS = 1.0e-6;

    /**
     * Two courses past the budget: one to cross it, one to prove the reading past it does not
     * recover on its own.
     */
    private static final int COURSES = SlabSupport.MAX_SUPPORT_RESOLVE_DEPTH + 2;

    /**
     * THE RESOLVER ROW. Every course of a continuous lowered tower must read the height of the
     * course beneath it, because a full-height seat passes its height through unchanged.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void continuousLoweredTowerKeepsOneHeightPastTheDepthBudget(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos[] courses = buildPreStoreLoweredTower(ctx, 1, 1);

        StringBuilder tower = new StringBuilder();
        double[] dy = new double[courses.length];
        for (int i = 0; i < courses.length; i++) {
            dy[i] = SlabSupport.getYOffset(w, courses[i], w.getBlockState(courses[i]));
            tower.append(' ').append(dy[i]);
        }

        // NON-VACUITY, asserted before the property: the tower must be long enough to spend the
        // whole budget, and its first course must actually be lowered. A tower that never reaches
        // the budget, or one that was never lowered at all, would pass the continuity assertion
        // while proving nothing.
        ctx.assertTrue(courses.length > SlabSupport.MAX_SUPPORT_RESOLVE_DEPTH,
                "FIXTURE IS NOT LOAD-BEARING: the tower is " + courses.length + " courses and the "
                        + "budget is " + SlabSupport.MAX_SUPPORT_RESOLVE_DEPTH
                        + " — it cannot exhaust anything. Lengthen it; do NOT relax the property.");
        ctx.assertTrue(dy[0] < -EPS,
                "FIXTURE IS NOT LOAD-BEARING: the first course must render lowered, got " + dy[0]);

        for (int i = 1; i < courses.length; i++) {
            ctx.assertTrue(Math.abs(dy[i] - dy[i - 1]) <= EPS,
                    "course " + (i + 1) + " reads " + dy[i] + " while the course it stands on reads "
                            + dy[i - 1] + " — a full-height seat passes its height through, so the "
                            + "tower must be one continuous height. It sinks "
                            + (dy[i - 1] - dy[i]) + " INTO its own support at exactly the course "
                            + "that exhausts the depth budget ("
                            + SlabSupport.MAX_SUPPORT_RESOLVE_DEPTH + "), whose exhaustion arm "
                            + "answers minResolvedDy() = " + SlabSupport.minResolvedDy()
                            + " about a walk that descended nowhere. tower:" + tower);
        }
        ctx.complete();
    }

    /**
     * THE OUTLINE LEG of the triad — the shape the player sees as the wireframe, offset by the
     * resolved height, so a course reading deeper than its support is DRAWN inside it.
     *
     * <p>Asserted as "the shapes do not overlap", which is the player-visible claim, rather than as
     * the height comparison of the row above restated in other units.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void noCourseOutlineOverlapsTheCourseBeneathIt(TestContext ctx) {
        assertNoOverlap(ctx, ShapeLeg.OUTLINE);
    }

    /**
     * THE COLLISION LEG of the triad, in its own row on purpose: folded in beside the outline it
     * would never execute once the outline assertion fired, and any claim about collision would be
     * attached to a row that stopped early.
     *
     * <p><b>It measures GREEN, and that is the finding, not a formality.</b> Collision is not
     * offset by the resolved height on this line — the separation established when an outline dy
     * bled into movement collision — so the tower a player walks on stays where the cells are while
     * the tower they SEE and CLICK sinks. The row is kept as a passing assertion so that a repair
     * of the resolver cannot quietly start moving collision as a side effect, and so the scope of
     * this defect stays measured rather than assumed.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void noCourseCollisionOverlapsTheCourseBeneathIt(TestContext ctx) {
        assertNoOverlap(ctx, ShapeLeg.COLLISION);
    }

    private enum ShapeLeg { OUTLINE, COLLISION }

    private void assertNoOverlap(TestContext ctx, ShapeLeg leg) {
        ServerWorld w = ctx.getWorld();
        BlockPos[] courses = buildPreStoreLoweredTower(ctx, 1, 1);

        for (int i = 1; i < courses.length; i++) {
            BlockPos upper = courses[i];
            BlockPos lower = courses[i - 1];
            // Absolute world-space top of the lower course and bottom of the upper one. Each cell's
            // shape is expressed in its own cell, so the cell's Y is added back to compare them.
            double lowerTop = lower.getY() + shape(w, lower, leg).getMax(Direction.Axis.Y);
            double upperBottom = upper.getY() + shape(w, upper, leg).getMin(Direction.Axis.Y);
            ctx.assertTrue(upperBottom >= lowerTop - EPS,
                    leg + " INTERPENETRATION at course " + (i + 1) + ": its bottom is at "
                            + upperBottom + " while the top of the course it stands on is at "
                            + lowerTop + " — it is " + (lowerTop - upperBottom)
                            + " blocks inside its own support.");
        }
        ctx.complete();
    }

    private static VoxelShape shape(ServerWorld w, BlockPos pos, ShapeLeg leg) {
        BlockState state = w.getBlockState(pos);
        return leg == ShapeLeg.OUTLINE
                ? state.getOutlineShape(w, pos)
                : state.getCollisionShape(w, pos);
    }

    /**
     * THE RAYCAST LEG of the triad — the offset-aware pick this line replaced vanilla DDA with.
     * The outline row proves the drawn wireframe moved; only this one proves the player's aim moved
     * with it, which is the difference between "it looks wrong" and "it is wrong where I click".
     *
     * <p>Aimed straight down the tower's own column from above. <b>It measures GREEN, and that is
     * the finding:</b> the pick meets the top course exactly at the face that course is DRAWN at,
     * so the model, the outline and the aim are displaced together rather than disagreeing with
     * each other. The defect is one wrong height consumed consistently by all three, not a triad
     * split — which is why a player sees a solid-looking block sunk into its support rather than a
     * block they cannot click. Kept as a passing assertion so a repair cannot move one leg alone.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aimingDownTheTowerHitsTheTopCourseAtItsOwnTopFace(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos[] courses = buildPreStoreLoweredTower(ctx, 1, 1);
        BlockPos top = courses[courses.length - 1];
        BlockPos below = courses[courses.length - 2];

        double expectedTopFace = top.getY()
                + w.getBlockState(top).getOutlineShape(w, top).getMax(Direction.Axis.Y);
        Vec3d centre = new Vec3d(top.getX() + 0.5, 0.0, top.getZ() + 0.5);
        Vec3d from = centre.add(0.0, top.getY() + 4.0, 0.0);
        Vec3d to = centre.add(0.0, below.getY() - 1.0, 0.0);

        BlockHitResult hit = SlabbedOffsetRaycast.raycast(w, from, to, ShapeContext.absent());
        ctx.assertTrue(hit != null && hit.getType() == HitResult.Type.BLOCK,
                "fixture: aiming straight down the tower must hit something");
        ctx.assertTrue(top.equals(hit.getBlockPos()),
                "the pick aimed down the tower must land on its TOP course " + top.toShortString()
                        + ", got " + hit.getBlockPos().toShortString());
        ctx.assertTrue(Math.abs(hit.getPos().y - expectedTopFace) <= EPS,
                "RAYCAST follows the sunken height: the pick met the top course at y="
                        + hit.getPos().y + " while its own top face is at y=" + expectedTopFace
                        + " — the aim is displaced by " + (expectedTopFace - hit.getPos().y)
                        + " blocks, so this is not a model-only defect.");
        ctx.complete();
    }

    /**
     * THE REPORTED SUBJECT, literally: a SLAB capping the tower rather than another full block.
     *
     * <p>The rows above build the tower out of ordinary full blocks because that is the shortest
     * scene that spends the budget. A slab reaches the same walk by a different door — the slab
     * branch's lowered-support-below mirror rather than the anchor branch — so pinning it
     * separately is what stops a repair that fixes one door and leaves the other. It also matches
     * the shape the defect was reported against.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aSlabCappingTheTowerSitsOnItRatherThanInsideIt(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos[] courses = buildPreStoreLoweredTower(ctx, 1, 1);
        BlockPos support = courses[courses.length - 1];
        BlockPos cap = support.up();
        place(w, cap, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));

        double supportDy = SlabSupport.getYOffset(w, support, w.getBlockState(support));
        double capDy = SlabSupport.getYOffset(w, cap, w.getBlockState(cap));
        ctx.assertTrue(Math.abs(capDy - supportDy) <= EPS,
                "a slab capping the tower must take the height of the course it rests on ("
                        + supportDy + "), got " + capDy + " — it sinks " + (supportDy - capDy)
                        + " into its own support.");
        ctx.complete();
    }

    /**
     * THE INVARIANT THAT MAKES TWO COUNTERS SOUND, asserted rather than left to the javadoc: the
     * walk bound must be strictly larger than the descent budget.
     *
     * <p>If it were not, a genuine STAIRCASE — every course descending half a block — would run out
     * of cells before it ran out of descent, and would take the walk bound's {@code -0.5} floor
     * instead of the cap it has actually earned: the pop-UP the descent arm's exhaustion answer
     * exists to prevent. The two bounds answer differently ON PURPOSE, so which one a descending
     * walk reaches first is load-bearing and may not be left to whichever numbers happen to be in
     * the file.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void theWalkBoundMustNotPreemptTheDescentBudget(TestContext ctx) {
        ctx.assertTrue(SlabSupport.MAX_SUPPORT_WALK_STEPS > SlabSupport.MAX_SUPPORT_RESOLVE_DEPTH,
                "the walk bound (" + SlabSupport.MAX_SUPPORT_WALK_STEPS + ") must exceed the "
                        + "descent budget (" + SlabSupport.MAX_SUPPORT_RESOLVE_DEPTH + "), or a "
                        + "descending tower reaches the floor-answering bound before the "
                        + "cap-answering one and pops UP instead of saturating.");
        ctx.complete();
    }

    /**
     * Builds a continuous lowered tower in the PRE-STORE state: ground, one bottom slab as the
     * lowering source, then {@link #COURSES} ordinary full blocks each anchored exactly as
     * {@code Block.onPlaced} anchors a real click, with the placement height then cleared.
     *
     * <p>The clear is what makes this the migration case rather than a synthetic one:
     * {@code addAnchor} records a height as a side effect, and a recorded height terminates the
     * support walk at the first course, so a fixture that skipped the clear would build a tower the
     * budget can never be spent on and FALSE-GREEN.
     */
    private BlockPos[] buildPreStoreLoweredTower(TestContext ctx, int x, int z) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(x, 1, z);
        place(w, ground, Blocks.STONE.getDefaultState());

        BlockPos source = ground.up();
        place(w, source, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));

        BlockPos[] courses = new BlockPos[COURSES];
        for (int i = 0; i < COURSES; i++) {
            BlockPos course = source.up(i + 1);
            courses[i] = course;
            place(w, course, Blocks.STONE.getDefaultState());
            SlabAnchorAttachment.addAnchor(w, course, w.getBlockState(course));
            ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, course),
                    "fixture: course " + (i + 1) + " must anchor, or the tower is not a lowered "
                            + "column and no support walk descends it");
        }

        for (BlockPos course : courses) {
            SlabPlacementDyAttachment.clear(w, course);
            ctx.assertTrue(!SlabPlacementDyAttachment.hasStoredDy(w, course),
                    "fixture: the tower must hold NO stored heights — a stored height terminates "
                            + "the support walk and the budget can never be spent");
        }
        return courses;
    }

    private static void place(ServerWorld w, BlockPos pos, BlockState state) {
        w.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }
}
