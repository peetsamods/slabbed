package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Headless re-repro harness for the Book IV blocker "occupied lowered-slab-lane
 * target hijack" (HANDOFF.md 2026-06-29, live jar {@code fb875193…}, retired
 * pre-{@code 0ef13fb1} targeting stack).
 *
 * <p><b>Historical fixture</b> (live world coords 116/-60..-58/120, rebuilt here at
 * gametest-relative coords): flat BOTTOM oak-slab support (dy=0), anchored lowered
 * {@code minecraft:gold_block} on its top (dy=-0.5), and an oak BOTTOM slab authored
 * into the lowered lane above the gold (dy=-0.5, persistent carrier). Visible bands
 * (worldY relative to the support cell base y0): support [y0, y0+0.5], gold
 * [y0+0.5, y0+1.5], lane slab [y0+1.5, y0+2.0].
 *
 * <p><b>Historical RED row</b>: crosshair on the lane slab's visible body reported
 * {@code target=gold_block face=up expectedPlace=<occupied lane cell>} — the hidden
 * gold up-face hijacked ownership from the visible lane owner.
 *
 * <p><b>Proof #1</b> drives {@link SlabbedOffsetRaycast#raycast} (the single pick
 * authority since {@code 0ef13fb1}, pure server-callable) with an aim grid across the
 * slab band, the gold/slab seam, the visible top, and two standing-player eye lines
 * replicating the documented live aim, asserting the lane OWNER wins everywhere it is
 * visibly aimed at and the hijack signature (gold + UP) never appears. Each ray also
 * records what the VANILLA outline clip returns for contrast, and asserts the honest
 * hit would survive the server's {@code handleUseItemOn} per-axis 1.0000001 check.
 *
 * <p><b>Proof #2</b> feeds the honest visible-top hit into the REAL item-use path
 * ({@link ForgeHooks#onPlaceItemIntoWorld}, 26.2-style useOn) and asserts the
 * placement mutates the VISIBLE lane (same-cell lowered combine per
 * SlabCanBeReplacedDyMixin's face==UP branch, or a lane-following slab in the cell
 * above), never a hijacked/hidden cell, and that the authored lane never pops.
 *
 * <p><b>Scope honesty</b>: this proves the RAYCAST GEOMETRY and the SERVER USE path
 * on the current architecture. It does NOT prove the live client pipeline
 * ({@code Minecraft.hitResult} wiring, anchor mirror sync — divergence D1) — that
 * half remains live-only via Maintainer's Modrinth jar-swap route.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ForgeOccupiedLoweredLaneTargetHijackGameTest {
    private static final double EPSILON = 1.0e-6d;
    /** Server per-axis use validation bound (ServerGamePacketListenerImpl.handleUseItemOn). */
    private static final double SERVER_USE_TOLERANCE = 1.0000001d;

    /** Fixture handle: support (flat slab), gold (lowered full block), lane (occupying lowered slab). */
    private record Fixture(BlockPos support, BlockPos gold, BlockPos lane) {
    }

    // ── PROOF #1: raycast owner identity across the aim grid ─────────────────
    @GameTest(template = "empty")
    public void occupiedLaneRaycastResolvesLaneOwnerNotHiddenGoldFace(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        Fixture f = buildOccupiedLaneFixture(ctx, world);
        Player player = FakePlayerFactory.getMinecraft(world);

        double y0 = f.support().getY();
        double cx = f.support().getX() + 0.5d;
        double cz = f.support().getZ() + 0.5d;
        double westPlane = f.support().getX();
        double southPlane = f.support().getZ() + 1.0d;

        // A) side sweep across the slab's visible band (the exact region the historical
        //    crosshair sat in when the row reported gold face=up), from a west eye above.
        Vec3 westEye = new Vec3(f.support().getX() - 3.5d, y0 + 2.2d, cz);
        double[] bandY = {1.55d, 1.65d, 1.75d, 1.85d, 1.95d};
        double[] zJitter = {-0.3d, 0.0d, 0.3d};
        for (double by : bandY) {
            for (double zj : zJitter) {
                Vec3 target = new Vec3(westPlane, y0 + by, cz + zj);
                assertOwnedHit(ctx, world, player, f, westEye, target,
                        f.lane(), Direction.WEST, "A.slabBand[y+" + by + ",z" + zj + "]");
            }
        }

        // B) seam band: just above the gold/slab seam (y0+1.5) the slab owns; just below,
        //    and in the gold body, the gold's truly-visible west face owns.
        assertOwnedHit(ctx, world, player, f, westEye, new Vec3(westPlane, y0 + 1.505d, cz),
                f.lane(), Direction.WEST, "B.seam+0.005");
        assertOwnedHit(ctx, world, player, f, westEye, new Vec3(westPlane, y0 + 1.495d, cz),
                f.gold(), Direction.WEST, "B.seam-0.005");
        assertOwnedHit(ctx, world, player, f, westEye, new Vec3(westPlane, y0 + 1.2d, cz),
                f.gold(), Direction.WEST, "B.goldBand[y+1.2]");
        assertOwnedHit(ctx, world, player, f, westEye, new Vec3(westPlane, y0 + 0.8d, cz),
                f.gold(), Direction.WEST, "B.goldBand[y+0.8]");

        // C) the slab's visible TOP (worldY = y0+2.0): straight-down grid. This is the
        //    aim whose WYSIWYG row must be target=lane face=up (expectedPlace above),
        //    NOT target=gold face=up (expectedPlace = the occupied lane cell).
        double laneVisibleTop = y0 + 2.0d;
        double[] xzOff = {-0.3d, 0.0d, 0.3d};
        for (double xo : xzOff) {
            for (double zo : xzOff) {
                Vec3 eye = new Vec3(cx + xo, y0 + 4.0d, cz + zo);
                Vec3 target = new Vec3(cx + xo, laneVisibleTop, cz + zo);
                BlockHitResult hit = assertOwnedHit(ctx, world, player, f, eye, target,
                        f.lane(), Direction.UP, "C.visibleTop[x" + xo + ",z" + zo + "]");
                ctx.assertTrue(Math.abs(hit.getLocation().y - laneVisibleTop) <= 1.0e-4d,
                        "visible-top hit must land on the moved top plane y=" + text(laneVisibleTop)
                                + ", got " + text(hit.getLocation().y));
            }
        }

        // D) standing-player eye lines from the south (replicating the documented live
        //    aim: crosshair on the lane body / "gold top-face region" from ground level
        //    and from one block up looking down).
        Vec3 groundEye = new Vec3(cx, y0 + 1.62d, cz + 3.5d);
        for (double by : new double[]{1.6d, 1.75d, 1.95d}) {
            assertOwnedHit(ctx, world, player, f, groundEye, new Vec3(cx, y0 + by, southPlane),
                    f.lane(), Direction.SOUTH, "D.groundEye[y+" + by + "]");
        }
        Vec3 raisedEye = new Vec3(cx, y0 + 3.12d, cz + 3.5d);
        for (double by : new double[]{1.55d, 1.7d, 1.9d}) {
            assertOwnedHit(ctx, world, player, f, raisedEye, new Vec3(cx, y0 + by, southPlane),
                    f.lane(), Direction.SOUTH, "D.raisedEye[y+" + by + "]");
        }

        ctx.succeed();
    }

    // ── PROOF #2: UseOnContext-driven placement into the visible lane ────────
    @GameTest(template = "empty")
    public void occupiedLaneVisibleTopUseOnPlacesInVisibleLane(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        Fixture f = buildOccupiedLaneFixture(ctx, world);

        double y0 = f.support().getY();
        double cx = f.support().getX() + 0.5d;
        double cz = f.support().getZ() + 0.5d;
        double laneVisibleTop = y0 + 2.0d;

        // Honest hit from the single pick authority, aimed at the lane slab's visible top.
        Vec3 eye = new Vec3(cx, y0 + 4.0d, cz);
        BlockHitResult honest = offsetRay(world, eye, new Vec3(cx, laneVisibleTop, cz));
        ctx.assertTrue(honest.getType() == HitResult.Type.BLOCK,
                "visible-top aim must produce a block hit");
        ctx.assertTrue(honest.getBlockPos().equals(f.lane()),
                "visible-top aim must target the lane owner " + shortPos(f.lane())
                        + ", got " + shortPos(honest.getBlockPos()) + " (hijack if gold "
                        + shortPos(f.gold()) + ")");
        ctx.assertTrue(honest.getDirection() == Direction.UP,
                "visible-top aim must report face=up, got " + honest.getDirection());
        ctx.assertTrue(withinServerUseTolerance(honest),
                "honest -0.5-lane hit must survive the server 1.0000001 per-axis use check, hit="
                        + honest.getLocation() + " pos=" + shortPos(honest.getBlockPos()));

        // Drive the REAL item-use path with the honest hit (26.2-style useOn).
        Player player = FakePlayerFactory.getMinecraft(world);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.OAK_SLAB));
        InteractionResult result = ForgeHooks.onPlaceItemIntoWorld(
                new UseOnContext(player, InteractionHand.MAIN_HAND, honest));
        ctx.assertTrue(result.consumesAction(),
                "use on the visible lane top must not be silently dropped, result=" + result);

        BlockState laneAfter = world.getBlockState(f.lane());
        BlockState aboveAfter = world.getBlockState(f.lane().above());
        double laneDyAfter = SlabSupport.getYOffset(world, f.lane(), laneAfter);
        double aboveDyAfter = SlabSupport.getYOffset(world, f.lane().above(), aboveAfter);

        String outcome;
        if (laneAfter.getBlock() instanceof SlabBlock
                && laneAfter.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
            outcome = "COMBINE_DOUBLE_AT_LANE";
            // The branch's own design intent (SlabCanBeReplacedDyMixin face==UP branch):
            // same-cell combine. The authored lane must NOT pop (WYSIWYG persistence law).
            ctx.assertTrue(near(laneDyAfter, -0.5d),
                    "combined double at the lane cell must keep the authored -0.5 lane"
                            + " (popping law), got " + text(laneDyAfter));
            ctx.assertTrue(aboveAfter.isAir(),
                    "combine outcome must not also mutate the cell above, got " + aboveAfter);
        } else if (laneAfter.getBlock() instanceof SlabBlock
                && laneAfter.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                && aboveAfter.getBlock() instanceof SlabBlock) {
            outcome = "PLACE_ABOVE_LANE";
            // 26.2-canonical alternative (UP-face-on-lowered-slab intent marks above()):
            // the new piece rides the lane in the cell above. Lane itself must be untouched.
            ctx.assertTrue(near(laneDyAfter, -0.5d),
                    "lane slab must keep -0.5 when the new piece goes above, got " + text(laneDyAfter));
            // aboveDy is RECORDED, not asserted: -0.5 (lane-following) vs 0.0 is the exact
            // canonical choice Maintainer has to pin (research step 4). Bare record below.
        } else {
            outcome = "RED_UNEXPECTED";
        }

        System.out.println("[HIJACK_USEON]"
                + " fixture=support" + shortPos(f.support()) + "/gold" + shortPos(f.gold())
                + "/lane" + shortPos(f.lane())
                + " honestHit=" + shortPos(honest.getBlockPos()) + "/" + honest.getDirection()
                + " outcome=" + outcome
                + " laneAfter=" + laneAfter.getBlock() + "[" + slabTypeText(laneAfter) + "]"
                + " laneDyAfter=" + text(laneDyAfter)
                + " aboveAfter=" + aboveAfter.getBlock()
                + " aboveDyAfter=" + text(aboveDyAfter)
                + " proofScope=forge_server_gametest_useOn_only_no_client_pipeline");

        ctx.assertTrue(!"RED_UNEXPECTED".equals(outcome),
                "use on the visible lane top must combine at the lane or place above it, got lane="
                        + laneAfter + " above=" + aboveAfter);

        // The hidden owner and the support must be untouched (no hijacked mutation).
        BlockState goldAfter = world.getBlockState(f.gold());
        ctx.assertTrue(goldAfter.is(Blocks.GOLD_BLOCK),
                "gold owner cell must be untouched by the use, got " + goldAfter);
        double goldDyAfter = SlabSupport.getYOffset(world, f.gold(), goldAfter);
        ctx.assertTrue(near(goldDyAfter, -0.5d),
                "gold must stay in its -0.5 lane after the use, got " + text(goldDyAfter));
        ctx.assertTrue(world.getBlockState(f.support()).is(Blocks.OAK_SLAB),
                "support slab must be untouched by the use");

        ctx.succeed();
    }

    // ── fixture ───────────────────────────────────────────────────────────────

    /**
     * Rebuilds the exact historical fixture with REAL item placements (EntityPlaceEvent
     * anchor + carrier authoring fires as in live play): flat support slab (setBlock,
     * inert base), gold block item-placed on its top (anchored, dy=-0.5), oak slab
     * item-placed on the gold's VISIBLE top (the documented live authoring aim,
     * worldY = goldCellBase + 0.5), landing in the lowered lane at dy=-0.5.
     */
    private static Fixture buildOccupiedLaneFixture(GameTestHelper ctx, ServerLevel world) {
        BlockPos support = ctx.absolutePos(new BlockPos(2, 1, 2));
        world.setBlock(support, Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);

        // Click the flat BOTTOM slab's REAL visible top face (worldY = supportY+0.5 =
        // Vec3.atCenterOf(support).y — a bottom slab's shape tops out at half height),
        // exactly where the live hand-placement crosshair hit lands.
        BlockPos gold = support.above();
        BlockState goldState = placeBlockItem(world, Blocks.GOLD_BLOCK,
                new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false),
                gold);
        ctx.assertTrue(goldState.is(Blocks.GOLD_BLOCK),
                "fixture gold must place at " + shortPos(gold));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, gold),
                "fixture gold must be anchored (src=ANCHORED in the live row)");
        double goldDy = SlabSupport.getYOffset(world, gold, goldState);
        ctx.assertTrue(near(goldDy, -0.5d),
                "fixture gold must be lowered dy=-0.5, got " + text(goldDy));

        // Author the lane occupant by clicking the gold's VISIBLE top face
        // (world y = goldCellBase + 0.5 = Vec3.atCenterOf(gold).y — the lowered top).
        BlockPos lane = gold.above();
        BlockState laneState = placeBlockItem(world, Blocks.OAK_SLAB,
                new BlockHitResult(Vec3.atCenterOf(gold), Direction.UP, gold, false),
                lane);
        ctx.assertTrue(laneState.getBlock() instanceof SlabBlock
                        && laneState.getValue(SlabBlock.TYPE) == SlabType.BOTTOM,
                "fixture lane occupant must be a BOTTOM slab at " + shortPos(lane)
                        + ", got " + laneState);
        double laneDy = SlabSupport.getYOffset(world, lane, laneState);
        ctx.assertTrue(near(laneDy, -0.5d),
                "fixture lane slab must ride the lowered lane at dy=-0.5, got " + text(laneDy));

        System.out.println("[HIJACK_FIXTURE]"
                + " support=" + shortPos(support) + " dy=0.000000"
                + " gold=" + shortPos(gold) + " dy=" + text(goldDy)
                + " lane=" + shortPos(lane) + " dy=" + text(laneDy)
                + " visibleBands=[support:y0..y0+0.5][gold:y0+0.5..y0+1.5][lane:y0+1.5..y0+2.0]");
        return new Fixture(support, gold, lane);
    }

    // ── ray helpers ───────────────────────────────────────────────────────────

    /** Runs the pick authority from eye through target, ending just past the target plane. */
    private static BlockHitResult offsetRay(ServerLevel world, Vec3 eye, Vec3 target) {
        Vec3 dir = target.subtract(eye).normalize();
        Vec3 end = target.add(dir.scale(1.5d)); // short overshoot: stays clear of neighbour structures
        return SlabbedOffsetRaycast.raycast(world, eye, end, CollisionContext.empty());
    }

    /**
     * Asserts the offset raycast resolves {@code expectedPos}/{@code expectedFace} and that
     * the hijack signature (gold owner + face=up) never appears; records the vanilla
     * outline clip for the same ray as contrast, and checks the server use tolerance.
     */
    private static BlockHitResult assertOwnedHit(
            GameTestHelper ctx, ServerLevel world, Player player, Fixture f,
            Vec3 eye, Vec3 target, BlockPos expectedPos, Direction expectedFace, String label) {
        BlockHitResult hit = offsetRay(world, eye, target);
        String vanilla = vanillaClipText(world, player, eye, target);
        boolean isBlock = hit.getType() == HitResult.Type.BLOCK;
        System.out.println("[HIJACK_RAY] " + label
                + " eye=" + vec(eye) + " target=" + vec(target)
                + " slabbed=" + (isBlock ? shortPos(hit.getBlockPos()) + "/" + hit.getDirection() : "MISS")
                + " vanilla=" + vanilla
                + " expected=" + shortPos(expectedPos) + "/" + expectedFace);
        ctx.assertTrue(isBlock, label + ": ray must hit a block");
        ctx.assertTrue(!(hit.getBlockPos().equals(f.gold()) && hit.getDirection() == Direction.UP),
                label + ": HIJACK signature — hidden gold up-face won ownership (historical RED row)");
        ctx.assertTrue(hit.getBlockPos().equals(expectedPos),
                label + ": owner must be " + shortPos(expectedPos)
                        + ", got " + shortPos(hit.getBlockPos()));
        ctx.assertTrue(hit.getDirection() == expectedFace,
                label + ": face must be " + expectedFace + ", got " + hit.getDirection());
        ctx.assertTrue(withinServerUseTolerance(hit),
                label + ": honest hit must survive the server 1.0000001 per-axis use check, hit="
                        + hit.getLocation());
        return hit;
    }

    /** What the VANILLA outline clip says for the same ray (recorded contrast only). */
    private static String vanillaClipText(ServerLevel world, Player player, Vec3 eye, Vec3 target) {
        Vec3 dir = target.subtract(eye).normalize();
        Vec3 end = target.add(dir.scale(1.5d));
        BlockHitResult v = world.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return v.getType() == HitResult.Type.BLOCK
                ? shortPos(v.getBlockPos()) + "/" + v.getDirection()
                : "MISS";
    }

    /** Mirrors ServerGamePacketListenerImpl.handleUseItemOn's per-axis validation. */
    private static boolean withinServerUseTolerance(BlockHitResult hit) {
        Vec3 center = Vec3.atCenterOf(hit.getBlockPos());
        Vec3 loc = hit.getLocation();
        return Math.abs(loc.x - center.x) < SERVER_USE_TOLERANCE
                && Math.abs(loc.y - center.y) < SERVER_USE_TOLERANCE
                && Math.abs(loc.z - center.z) < SERVER_USE_TOLERANCE;
    }

    // ── placement helper (real item path; proven fixture idiom) ──────────────

    private static BlockState placeBlockItem(
            ServerLevel world, Block itemBlock, BlockHitResult hit, BlockPos resultPos) {
        Player player = FakePlayerFactory.getMinecraft(world);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(itemBlock));
        InteractionResult result = ForgeHooks.onPlaceItemIntoWorld(
                new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        if (!result.consumesAction()) {
            throw new AssertionError("item placement did not consume action, item=" + itemBlock
                    + " hit=" + hit.getBlockPos() + "/" + hit.getDirection() + " result=" + result);
        }
        return world.getBlockState(resultPos);
    }

    // ── tiny helpers ──────────────────────────────────────────────────────────

    private static String slabTypeText(BlockState state) {
        return state.hasProperty(SlabBlock.TYPE)
                ? state.getValue(SlabBlock.TYPE).toString() : "n/a";
    }

    private static boolean near(double actual, double expected) {
        return Double.isFinite(actual)
                && Double.isFinite(expected)
                && Math.abs(actual - expected) <= EPSILON;
    }

    private static String text(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        return String.format(java.util.Locale.ROOT, "%.6f", value);
    }

    private static String vec(Vec3 v) {
        return String.format(java.util.Locale.ROOT, "(%.3f,%.3f,%.3f)", v.x, v.y, v.z);
    }

    private static String shortPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
