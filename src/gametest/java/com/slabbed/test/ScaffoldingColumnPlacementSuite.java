package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ScaffoldingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Scaffolding placement discriminator (GH #65).
 *
 * <p>Vanilla {@code ScaffoldingItem.getPlacementContext} REDIRECTS the placement cell away from
 * the clicked cell: {@code ScaffoldingBlock.canReplace} accepts the scaffolding item, so a click
 * on a column keeps the clicked scaffolding cell as the context target, and the item then walks —
 * UP the column for a non-sneak side or bottom-face click, HORIZONTALLY along the player's facing
 * for a non-sneak top-face click — and lands on the first replaceable cell it finds, possibly
 * several cells from the original aim. The height recorded for that placement must describe the
 * REDIRECTED destination cell (a flush column extension is flush at its own cell); a height
 * derived from the pre-redirect aim owner is wrong by the full length of the walk. See
 * {@code LAW.md}: the height recorded at placement is permanent, which is exactly why recording a
 * wrong one here matters — a record deeper than
 * {@code SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY} is outside the pick window and the block
 * becomes untargetable.
 *
 * <p><b>Seat contract ({@link #assertSeated}):</b> the destination cell holds scaffolding; the
 * stored placement dy is absent or exactly the expected seat (raw-bit identity, -0.0 normalised);
 * {@code SlabSupport.getYOffset} agrees; the outline occupies the expected seat; and the
 * offset-aware raycast targets the cell with a horizontal ray at the expected top-plank height.
 * All legs are evaluated and reported together so a failure names every observed value.
 *
 * <p><b>Fixture rule:</b> authored fixture columns are grounded on stone and authored with
 * {@code DISTANCE=0, BOTTOM=false} — the states vanilla itself computes for a grounded column —
 * so the scheduled distance ticks scaffolding runs after every neighbor change cannot collapse
 * them mid-test (an authored default-state column would carry {@code DISTANCE=7} and fall).
 */
public final class ScaffoldingColumnPlacementSuite {

    private static final double EPS = 1.0e-6;

    /** Centre height of scaffolding's 14..16px top plank, relative to the cell's visual bottom. */
    private static final double PLANK_AIM = 0.9375;

    /** Raw-bit height identity with -0.0 normalised to 0.0. */
    private static boolean sameBits(double a, double b) {
        return Double.doubleToRawLongBits(a == 0.0 ? 0.0 : a)
                == Double.doubleToRawLongBits(b == 0.0 ? 0.0 : b);
    }

    /** Grounded-column cell state; see the class javadoc's fixture rule. */
    private static BlockState stableScaffoldState() {
        return Blocks.SCAFFOLDING.getDefaultState()
                .with(ScaffoldingBlock.DISTANCE, 0)
                .with(ScaffoldingBlock.BOTTOM, false);
    }

    /**
     * Stone at plot (4,0,3) with {@code height} scaffolding cells on top of it.
     * Returns the base (lowest scaffolding) cell.
     */
    private static BlockPos column(TestContext ctx, int height) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(new BlockPos(4, 0, 3));
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        for (int i = 1; i <= height; i++) {
            w.setBlockState(ground.up(i), stableScaffoldState(), Block.NOTIFY_LISTENERS);
        }
        return ground.up();
    }

    private static PlayerEntity scaffoldPlayer(TestContext ctx, BlockPos standAbs) {
        return PlacementHarness.mockPlayerHolding(ctx, standAbs, new ItemStack(Items.SCAFFOLDING, 16));
    }

    private static ActionResult click(ServerWorld w, PlayerEntity player,
                                      BlockPos clickAbs, Direction face, Vec3d hitAbs) {
        return PlacementHarness.useHeldItem(w, player, clickAbs, face, hitAbs);
    }

    private static Vec3d faceCentre(BlockPos pos, Direction face) {
        return Vec3d.ofCenter(pos)
                .add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
    }

    private static int stackCount(PlayerEntity player) {
        return player.getStackInHand(Hand.MAIN_HAND).getCount();
    }

    /**
     * The shared seat-contract assertion. Every leg is evaluated; failures are aggregated into
     * one message carrying the observed stored bits, live dy, outline min-Y, and raycast result,
     * so a red row states the actual recorded height on its face.
     *
     * <p>The targetability leg fires a horizontal ray from 3 cells west of the destination at the
     * expected top-plank height. The plank spans the full cell in X/Z, so the ray is guaranteed to
     * intersect it when the block really sits at the expected seat; it deliberately does NOT aim
     * at cell-centre height, where a horizontal ray passes between scaffolding's corner posts and
     * would miss even a correctly seated block.
     */
    private static void assertSeated(TestContext ctx, String row, BlockPos pos, double expectedDy) {
        ServerWorld w = ctx.getWorld();
        BlockState state = w.getBlockState(pos);
        List<String> problems = new ArrayList<>();
        if (!state.isOf(Blocks.SCAFFOLDING)) {
            problems.add("expected scaffolding, got " + state);
        } else {
            double stored = SlabPlacementDyAttachment.storedDy(w, pos);
            boolean storedOk = expectedDy == 0.0
                    ? (Double.isNaN(stored) || sameBits(stored, 0.0))
                    : sameBits(stored, expectedDy);
            if (!storedOk) {
                problems.add("stored dy: expected "
                        + (expectedDy == 0.0 ? "absent-or-0.0" : String.valueOf(expectedDy))
                        + ", observed " + stored
                        + " (raw 0x" + Long.toHexString(Double.doubleToRawLongBits(stored)) + ")");
            }
            double live = SlabSupport.getYOffset(w, pos, state);
            if (!sameBits(live, expectedDy)) {
                problems.add("getYOffset: expected " + expectedDy + ", observed " + live);
            }
            VoxelShape outline = state.getOutlineShape(w, pos, ShapeContext.absent());
            if (outline.isEmpty()) {
                problems.add("outline: empty");
            } else {
                double minY = outline.getMin(Direction.Axis.Y);
                if (Math.abs(minY - expectedDy) > EPS) {
                    problems.add("outline minY: expected " + expectedDy + ", observed " + minY);
                }
            }
            double rayY = pos.getY() + expectedDy + PLANK_AIM;
            Vec3d eye = new Vec3d(pos.getX() + 0.5 - 3.0, rayY, pos.getZ() + 0.5);
            Vec3d end = new Vec3d(pos.getX() + 0.5, rayY, pos.getZ() + 0.5);
            BlockHitResult hit = SlabbedOffsetRaycast.raycast(w, eye, end, ShapeContext.absent());
            boolean hitOk = hit != null
                    && hit.getType() == HitResult.Type.BLOCK
                    && hit.getBlockPos().equals(pos);
            if (!hitOk) {
                problems.add("raycast at expected plank height " + rayY + " must hit " + pos
                        + ", got " + (hit == null
                        ? "null"
                        : hit.getType() + (hit.getType() == HitResult.Type.BLOCK
                                ? " at " + hit.getBlockPos()
                                : "")));
            }
        }
        ctx.assertTrue(problems.isEmpty(), row + ": seat contract at " + pos
                + " (expected dy " + expectedDy + "): " + String.join("; ", problems));
    }

    // ── row 1: non-sneak side click on the BASE of a 3-high column stacks flush on top ────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sideClickOnColumnStacksOnTopFlush(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = column(ctx, 3);
        BlockPos dest = base.up(3);
        PlayerEntity player = scaffoldPlayer(ctx, base.west(2));
        ActionResult result = click(w, player, base, Direction.WEST, faceCentre(base, Direction.WEST));
        ctx.assertTrue(result.isAccepted(),
                "side click on the column base must be accepted, got " + result);
        ctx.assertTrue(w.getBlockState(dest).isOf(Blocks.SCAFFOLDING),
                "the redirected placement must land on the column top " + dest + ", got "
                        + w.getBlockState(dest));
        // Kills the diagonal-float shape: nothing above the new top, nothing beside it.
        ctx.assertTrue(w.getBlockState(dest.up()).isAir(),
                "the cell above the new top must stay air, got " + w.getBlockState(dest.up()));
        for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos beside = dest.offset(side);
            ctx.assertTrue(w.getBlockState(beside).isAir(),
                    "horizontal neighbor " + beside + " of the new top must stay air, got "
                            + w.getBlockState(beside));
        }
        ctx.assertTrue(stackCount(player) == 15,
                "one scaffolding must be consumed, stack now " + stackCount(player));
        assertSeated(ctx, "sideClickOnColumnStacksOnTopFlush", dest, 0.0);
        ctx.complete();
    }

    // ── row 2: minimal repro — side click on a single grounded scaffold stacks flush above ────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sideClickSingleScaffoldStacksFlush(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = column(ctx, 1);
        BlockPos dest = base.up();
        PlayerEntity player = scaffoldPlayer(ctx, base.west(2));
        ActionResult result = click(w, player, base, Direction.WEST, faceCentre(base, Direction.WEST));
        ctx.assertTrue(result.isAccepted(),
                "side click on the single scaffold must be accepted, got " + result);
        ctx.assertTrue(w.getBlockState(dest).isOf(Blocks.SCAFFOLDING),
                "the redirected placement must land at " + dest + ", got " + w.getBlockState(dest));
        ctx.assertTrue(stackCount(player) == 15,
                "one scaffolding must be consumed, stack now " + stackCount(player));
        assertSeated(ctx, "sideClickSingleScaffoldStacksFlush", dest, 0.0);
        ctx.complete();
    }

    // ── row 3: sneak top-face click stacks directly above (the reported working lane) ─────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sneakTopFaceStacksDirectlyAbove(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = column(ctx, 2);
        BlockPos top = base.up(1);
        BlockPos dest = top.up();
        PlayerEntity player = scaffoldPlayer(ctx, base.west(2));
        player.setSneaking(true);
        // Premise probe: the mock player's sneak flag must actually reach the placement context's
        // cancel-interaction query, or this row would silently test the non-sneak lane instead.
        ctx.assertTrue(player.shouldCancelInteraction(),
                "premise probe: setSneaking(true) must make the mock player report "
                        + "shouldCancelInteraction; it does not, so the sneak lane cannot be "
                        + "driven this way");
        ActionResult result = click(w, player, top, Direction.UP, faceCentre(top, Direction.UP));
        ctx.assertTrue(result.isAccepted(),
                "sneak top-face click must be accepted, got " + result);
        ctx.assertTrue(w.getBlockState(dest).isOf(Blocks.SCAFFOLDING),
                "sneak top-face click must stack directly above at " + dest + ", got "
                        + w.getBlockState(dest));
        ctx.assertTrue(stackCount(player) == 15,
                "one scaffolding must be consumed, stack now " + stackCount(player));
        assertSeated(ctx, "sneakTopFaceStacksDirectlyAbove", dest, 0.0);
        ctx.complete();
    }

    // ── row 4: non-sneak top-face click extends horizontally at the SAME Y ────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void topFaceClickExtendsHorizontallySameY(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = column(ctx, 3);
        BlockPos top = base.up(2);
        // Mock-player yaw 0 = facing SOUTH, so the horizontal walk goes to top.south().
        PlayerEntity player = scaffoldPlayer(ctx, base.north(2).up(2));
        ActionResult result = click(w, player, top, Direction.UP, faceCentre(top, Direction.UP));
        BlockPos dest = top.south();
        ctx.assertTrue(result.isAccepted(),
                "non-sneak top-face click must be accepted, got " + result);
        ctx.assertTrue(w.getBlockState(dest).isOf(Blocks.SCAFFOLDING),
                "non-sneak top-face click must extend horizontally to " + dest + " at the same Y, "
                        + "got " + w.getBlockState(dest));
        // Kills the diagonal-float shape: neither the clicked top nor the new cell gains a block above.
        ctx.assertTrue(w.getBlockState(top.up()).isAir(),
                "the cell above the clicked top must stay air, got " + w.getBlockState(top.up()));
        ctx.assertTrue(w.getBlockState(dest.up()).isAir(),
                "the cell above the horizontal extension must stay air, got "
                        + w.getBlockState(dest.up()));
        ctx.assertTrue(stackCount(player) == 15,
                "one scaffolding must be consumed, stack now " + stackCount(player));
        assertSeated(ctx, "topFaceClickExtendsHorizontallySameY", dest, 0.0);
        ctx.complete();
    }

    // ── row 5: bottom-face click from inside the column lands on top, visible AND targetable ──

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void insideColumnUpwardPlacementIsVisibleAndTargetable(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = column(ctx, 3);
        BlockPos dest = base.up(3);
        // The player stands inside the column at base level — the real inside-the-scaffold pose —
        // and clicks the underside centre of the cell overhead. Vanilla walks the column and
        // places on top; the destination must be seated flush there, including the raycast leg:
        // a record deeper than the pick window's DEEPEST_TARGETABLE_DY is an invisible,
        // untargetable block, which is the exact live report this row discriminates.
        PlayerEntity player = scaffoldPlayer(ctx, base);
        ActionResult result = click(w, player, base.up(1), Direction.DOWN,
                faceCentre(base.up(1), Direction.DOWN));
        ctx.assertTrue(result.isAccepted(),
                "inside-column bottom-face click must be accepted, got " + result);
        ctx.assertTrue(w.getBlockState(dest).isOf(Blocks.SCAFFOLDING),
                "the redirected placement must land on the column top " + dest + ", got "
                        + w.getBlockState(dest));
        ctx.assertTrue(stackCount(player) == 15,
                "one scaffolding must be consumed, stack now " + stackCount(player));
        assertSeated(ctx, "insideColumnUpwardPlacementIsVisibleAndTargetable", dest, 0.0);
        ctx.complete();
    }

    // ── row 6: horizontal extension consumes the DISTANCE mechanic, not a height record ───────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void horizontalExtensionConsumesDistanceNotDy(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = column(ctx, 1);
        PlayerEntity player = scaffoldPlayer(ctx, base.north(2));
        Vec3d topHit = faceCentre(base, Direction.UP);

        ActionResult first = click(w, player, base, Direction.UP, topHit);
        BlockPos cell1 = base.south();
        ctx.assertTrue(first.isAccepted(),
                "first top-face click must be accepted, got " + first);
        BlockState state1 = w.getBlockState(cell1);
        ctx.assertTrue(state1.isOf(Blocks.SCAFFOLDING),
                "first extension must land at " + cell1 + ", got " + state1);
        ctx.assertTrue(state1.get(ScaffoldingBlock.DISTANCE) == 1,
                "first extension must carry DISTANCE 1 (vanilla's own stability mechanic), got "
                        + state1.get(ScaffoldingBlock.DISTANCE));
        assertSeated(ctx, "horizontalExtensionConsumesDistanceNotDy(first)", cell1, 0.0);

        // Second click on the SAME column top: the horizontal walk skips the cell placed above
        // and lands one further, consuming one more unit of DISTANCE and still no height record.
        ActionResult second = click(w, player, base, Direction.UP, topHit);
        BlockPos cell2 = base.south(2);
        ctx.assertTrue(second.isAccepted(),
                "second top-face click must be accepted, got " + second);
        BlockState state2 = w.getBlockState(cell2);
        ctx.assertTrue(state2.isOf(Blocks.SCAFFOLDING),
                "second extension must land at " + cell2 + ", got " + state2);
        ctx.assertTrue(state2.get(ScaffoldingBlock.DISTANCE) == 2,
                "second extension must carry DISTANCE 2, got "
                        + state2.get(ScaffoldingBlock.DISTANCE));
        assertSeated(ctx, "horizontalExtensionConsumesDistanceNotDy(second)", cell2, 0.0);

        ctx.assertTrue(stackCount(player) == 14,
                "two scaffolding must be consumed, stack now " + stackCount(player));
        ctx.complete();
    }

    // ── row 7: a blocked redirect walk fails cleanly, changing nothing ────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void outOfRangeNullContextFailsCleanly(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = column(ctx, 1);
        BlockPos blocked = base.up();
        // Unreplaceable stone where the upward walk would land: ScaffoldingItem's redirect finds
        // no replaceable cell and returns a null context, and the whole use must fail closed.
        w.setBlockState(blocked, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        PlayerEntity player = scaffoldPlayer(ctx, base.west(2));
        ActionResult result = click(w, player, base, Direction.WEST, faceCentre(base, Direction.WEST));
        ctx.assertTrue(!result.isAccepted(),
                "a blocked redirect walk must not be accepted, got " + result);
        ctx.assertTrue(stackCount(player) == 16,
                "no scaffolding may be consumed on a failed placement, stack now "
                        + stackCount(player));
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        int scaffoldCells = 0;
        for (int x = 0; x <= 7; x++) {
            for (int y = 0; y <= 7; y++) {
                for (int z = 0; z <= 7; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (w.getBlockState(pos).isOf(Blocks.SCAFFOLDING)) {
                        scaffoldCells++;
                    }
                    ctx.assertTrue(!SlabPlacementDyAttachment.hasStoredDy(w, pos),
                            "a failed placement must record no height anywhere; found one at "
                                    + pos);
                }
            }
        }
        ctx.assertTrue(scaffoldCells == 1,
                "the arena must still hold exactly the one fixture scaffold, found "
                        + scaffoldCells);
        ctx.assertTrue(w.getBlockState(blocked).isOf(Blocks.STONE),
                "the blocking stone must be untouched, got " + w.getBlockState(blocked));
        ctx.complete();
    }

    // ── row 8: a neighbor pulse never changes the recorded height of a placed cell ────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void supportNeighborPulseKeepsFrozenHeight(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos base = column(ctx, 2);
        BlockPos dest = base.up(2);
        PlayerEntity player = scaffoldPlayer(ctx, base.west(2));
        ActionResult result = click(w, player, base, Direction.WEST, faceCentre(base, Direction.WEST));
        ctx.assertTrue(result.isAccepted(),
                "premise: the stacking click must be accepted, got " + result);
        ctx.assertTrue(w.getBlockState(dest).isOf(Blocks.SCAFFOLDING),
                "premise: the stacked cell must be scaffolding at " + dest + ", got "
                        + w.getBlockState(dest));
        // Whatever the placement recorded — right or wrong — a neighbor pulse must not change it
        // (LAW.md, LAW 1). This row asserts invariance of the record, not its value, so it holds
        // on either side of the placement fix the red rows above demand.
        long storedBefore = Double.doubleToRawLongBits(SlabPlacementDyAttachment.storedDy(w, dest));
        long liveBefore = Double.doubleToRawLongBits(
                SlabSupport.getYOffset(w, dest, w.getBlockState(dest)));
        BlockPos pulse = dest.east();
        ctx.runAtTick(1, () ->
                w.setBlockState(pulse, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL));
        ctx.runAtTick(3, () -> w.breakBlock(pulse, false));
        ctx.runAtTick(8, () -> {
            BlockState after = w.getBlockState(dest);
            ctx.assertTrue(after.isOf(Blocks.SCAFFOLDING),
                    "the pulse must not collapse the stacked cell, got " + after);
            ctx.assertTrue(w.getBlockState(base).isOf(Blocks.SCAFFOLDING)
                            && w.getBlockState(base.up()).isOf(Blocks.SCAFFOLDING),
                    "the pulse must not collapse the column");
            long storedAfter = Double.doubleToRawLongBits(
                    SlabPlacementDyAttachment.storedDy(w, dest));
            ctx.assertTrue(storedAfter == storedBefore,
                    "stored dy raw bits must survive the neighbor pulse: before=0x"
                            + Long.toHexString(storedBefore) + " after=0x"
                            + Long.toHexString(storedAfter));
            long liveAfter = Double.doubleToRawLongBits(SlabSupport.getYOffset(w, dest, after));
            ctx.assertTrue(liveAfter == liveBefore,
                    "getYOffset must survive the neighbor pulse: before="
                            + Double.longBitsToDouble(liveBefore) + " after="
                            + Double.longBitsToDouble(liveAfter));
            ctx.complete();
        });
    }

    // ── row 9: control — plain placement on stone, isolating redirect-lane failures ───────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaFlushControlOnStone(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos stone = ctx.getAbsolutePos(new BlockPos(4, 1, 3));
        w.setBlockState(stone, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        PlayerEntity player = scaffoldPlayer(ctx, stone.west(2).up());
        ActionResult result = click(w, player, stone, Direction.UP, faceCentre(stone, Direction.UP));
        BlockPos dest = stone.up();
        ctx.assertTrue(result.isAccepted(),
                "placement on plain stone must be accepted, got " + result);
        BlockState placed = w.getBlockState(dest);
        ctx.assertTrue(placed.isOf(Blocks.SCAFFOLDING),
                "scaffolding must land at " + dest + ", got " + placed);
        ctx.assertTrue(placed.get(ScaffoldingBlock.DISTANCE) == 0,
                "grounded scaffolding must carry DISTANCE 0, got "
                        + placed.get(ScaffoldingBlock.DISTANCE));
        // Vanilla's own bottom rule: BOTTOM is true only for a floating cell (distance > 0 with
        // no scaffolding below). A grounded DISTANCE=0 cell is BOTTOM=false.
        ctx.assertTrue(!placed.get(ScaffoldingBlock.BOTTOM),
                "grounded DISTANCE=0 scaffolding must carry BOTTOM=false, got BOTTOM="
                        + placed.get(ScaffoldingBlock.BOTTOM));
        assertSeated(ctx, "vanillaFlushControlOnStone", dest, 0.0);
        ctx.complete();
    }

    // ── row 10: scaffolding on a lowered bottom slab seats on the slab's REAL surface ─────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void scaffoldingOnBottomSlabSeatsSupported(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(new BlockPos(4, 1, 3));
        w.setBlockState(slab,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabPlacementDyAttachment.record(w, slab, -0.5),
                "fixture: the placement store must accept -0.5 for the seat slab");
        double slabDy = SlabSupport.getYOffset(w, slab, w.getBlockState(slab));
        ctx.assertTrue(sameBits(slabDy, -0.5),
                "fixture: the authored slab height must read back verbatim, wrote -0.5, read "
                        + slabDy);
        PlayerEntity player = scaffoldPlayer(ctx, slab.west(2).up());
        // Click the slab's VISIBLE top face centre (native top 0.5 above the cell floor, lowered
        // by the authored -0.5).
        Vec3d hit = new Vec3d(slab.getX() + 0.5, slab.getY() + (-0.5) + 0.5, slab.getZ() + 0.5);
        ActionResult result = click(w, player, slab, Direction.UP, hit);
        BlockPos dest = slab.up();
        ctx.assertTrue(result.isAccepted(),
                "placement on the lowered bottom slab must be accepted, got " + result);
        BlockState placed = w.getBlockState(dest);
        ctx.assertTrue(placed.isOf(Blocks.SCAFFOLDING),
                "scaffolding must land at " + dest + ", got " + placed);
        // The slab's top face counts as full support (the slab-top side-solid override), so the
        // cell is grounded, not floating.
        ctx.assertTrue(placed.get(ScaffoldingBlock.DISTANCE) == 0,
                "a slab-seated scaffold must carry DISTANCE 0 via the slab-top support override, "
                        + "got " + placed.get(ScaffoldingBlock.DISTANCE));
        // Seat contract: a follower on a bottom slab seats on the slab's REAL surface. The slab's
        // visible top sits at its own cell floor (native +0.5, authored -0.5), so the scaffold in
        // the cell above records -1.0 — the same compounding every follower-on-lowered-bottom-slab
        // row in this suite's siblings records. This row exists so a later fix for the redirect
        // rows cannot blanket-exempt scaffolding from the height store: this cell MUST hold a
        // record, and it must be the seat, not flush.
        assertSeated(ctx, "scaffoldingOnBottomSlabSeatsSupported", dest, -1.0);
        double slabDyAfter = SlabSupport.getYOffset(w, slab, w.getBlockState(slab));
        ctx.assertTrue(sameBits(slabDyAfter, -0.5),
                "the seat slab's own record must be untouched by the placement, got "
                        + slabDyAfter);
        ctx.complete();
    }

    // ── row 11: side-click stacking on a LOWERED column follows the column's real seat ────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sideClickOnLoweredColumnFollowsColumnSeat(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos stone = ctx.getAbsolutePos(new BlockPos(4, 0, 3));
        BlockPos slab = stone.up();
        w.setBlockState(stone, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        w.setBlockState(slab,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabPlacementDyAttachment.record(w, slab, -0.5),
                "fixture: the placement store must accept -0.5 for the seat slab");
        PlayerEntity player = scaffoldPlayer(ctx, slab.west(2).up());
        // Column seed via the real item path — row 10's action: a click on the lowered slab's
        // visible top seats the column base at -1.0.
        Vec3d slabHit = new Vec3d(slab.getX() + 0.5, slab.getY() + (-0.5) + 0.5, slab.getZ() + 0.5);
        ActionResult seed = click(w, player, slab, Direction.UP, slabHit);
        BlockPos columnTop = slab.up();
        ctx.assertTrue(seed.isAccepted(),
                "premise: placement on the lowered slab must be accepted, got " + seed);
        ctx.assertTrue(w.getBlockState(columnTop).isOf(Blocks.SCAFFOLDING),
                "premise: the column base must be scaffolding at " + columnTop + ", got "
                        + w.getBlockState(columnTop));
        double seatDy = SlabPlacementDyAttachment.storedDy(w, columnTop);
        ctx.assertTrue(sameBits(seatDy, -1.0),
                "premise: the column base must hold stored dy -1.0, observed " + seatDy
                        + " (raw 0x" + Long.toHexString(Double.doubleToRawLongBits(seatDy)) + ")");
        // The discriminated action: a side click on the lowered column top redirects the
        // placement to the cell above, and the new cell must FOLLOW the column's real seat —
        // the walk source's own recorded surface — neither a blanket flush 0.0 nor a fresh
        // aim-distance derivation.
        ActionResult result = click(w, player, columnTop, Direction.WEST,
                faceCentre(columnTop, Direction.WEST));
        BlockPos dest = columnTop.up();
        ctx.assertTrue(result.isAccepted(),
                "side click on the lowered column must be accepted, got " + result);
        ctx.assertTrue(w.getBlockState(dest).isOf(Blocks.SCAFFOLDING),
                "the redirected placement must land directly above at " + dest + ", got "
                        + w.getBlockState(dest));
        ctx.assertTrue(stackCount(player) == 14,
                "two scaffolding must be consumed across both placements, stack now "
                        + stackCount(player));
        assertSeated(ctx, "sideClickOnLoweredColumnFollowsColumnSeat", dest, -1.0);
        double columnTopAfter = SlabPlacementDyAttachment.storedDy(w, columnTop);
        ctx.assertTrue(sameBits(columnTopAfter, -1.0),
                "the walk source's own record must be untouched, observed " + columnTopAfter
                        + " (raw 0x"
                        + Long.toHexString(Double.doubleToRawLongBits(columnTopAfter)) + ")");
        double slabAfter = SlabSupport.getYOffset(w, slab, w.getBlockState(slab));
        ctx.assertTrue(sameBits(slabAfter, -0.5),
                "the seat slab's own record must be untouched, observed " + slabAfter);
        ctx.complete();
    }
}
