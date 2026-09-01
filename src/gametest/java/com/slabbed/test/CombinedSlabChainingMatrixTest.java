package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Combined-slab chaining matrix — NeoForge 1.21.1 behavior-port of main1211's
 * {@code CombinedSlabChainingMatrixTest} (Fabric 1.21.11), re-derived for this branch.
 *
 * <p>Purpose here: resolve the deep-stack question for THIS branch (Phase A class 10 —
 * "designed -1.0 bound; whether deep stacks show visible gaps needs a gametest matrix").
 * For every block in each column it records the ACTUAL {@link SlabSupport#getYOffset}
 * as a {@code [MATRIX]} line and compares it to the geometric flush law:
 *
 * <pre>
 * support visual top:  full block / TOP / DOUBLE slab -> Y + 1 + dy;  BOTTOM slab -> Y + 0.5 + dy
 * thing visual bottom: standing block at Y' = Y + 1   -> Y' + dy'
 * flush  =>  EXPECTED dy' = dy_support + (support is BOTTOM slab ? -0.5 : 0)
 * </pre>
 *
 * <p>Donor deviations, all deliberate:
 * <ul>
 *   <li>Terrain Slabs lanes are N/A on this branch: the NeoForge port subtracts TS entirely
 *       (CompatHooks shouldSkipOffset/shouldSkipSlabSupport; named-surface lowering is
 *       deliberately absent per CHANGELOG) and no TS build exists in the server-gametest
 *       classpath. Only the vanilla-expressible donor columns are ported (donor columns
 *       3, 7b, 8b) plus native deep-stack columns this branch's grammar can author.</li>
 *   <li>The donor packed all columns into one Fabric test; here each column family is its
 *       own 8x8x8 {@code empty}-template test so no lane leaves the structure bounds.</li>
 *   <li>Donor's Fabric fixtures used NOTIFY_LISTENERS; sibling tests on this branch use
 *       UPDATE_ALL, kept for consistency.</li>
 * </ul>
 *
 * <p>Assertion policy (donor philosophy): HARD-ASSERT only lanes that are known-green
 * grammar on THIS branch (anchored FB on bottom slab = -0.5; fence follows a lowered FB;
 * the deep lowered-FB chain flush law, which is the class-10 question this port exists to
 * answer) plus the designed global bound {@code dy >= -1.0}. Everything else is recorded
 * and classified ({@code BY-DESIGN: no vertical accumulate} — production propagates a
 * single -0.5 step, never stacks to -1.5) but NOT asserted, so the suite stays green while
 * still producing the full matrix evidence.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class CombinedSlabChainingMatrixTest {

    private static final double EPS = 1.0e-9;
    /** Designed lower bound on this branch: "no recursion below -1.0". */
    private static final double DY_LOWER_BOUND = -1.0;

    private static BlockState vanillaBottom() {
        return Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static boolean approx(double a, double b) {
        return Math.abs(a - b) < EPS;
    }

    private static String id(BlockState s) {
        String base = String.valueOf(BuiltInRegistries.BLOCK.getKey(s.getBlock()));
        if (s.hasProperty(SlabBlock.TYPE)) {
            base += "[" + s.getValue(SlabBlock.TYPE) + "]";
        }
        return base;
    }

    private static void place(ServerLevel w, BlockPos p, BlockState s) {
        w.setBlock(p, s, Block.UPDATE_ALL);
    }

    /** Re-reads dy after forcing a placement anchor on {@code p} (mirrors live BlockItem authoring). */
    private static double anchoredReread(ServerLevel world, BlockPos p) {
        BlockState s = world.getBlockState(p);
        SlabAnchorAttachment.addAnchor(world, p, s);
        return SlabSupport.getYOffset(world, p, s);
    }

    /**
     * Records one matrix cell: prints the [MATRIX] line, enforces the global designed
     * bound dy >= -1.0, and returns the live actual dy. {@code byDesign} marks cells whose
     * law value production deliberately does not produce (single-step, no accumulate).
     */
    private static double record(GameTestHelper ctx, String cfg, String level, ServerLevel world,
                                 BlockPos p, double expected, boolean byDesign, double anchoredDy) {
        BlockState s = world.getBlockState(p);
        double actual = SlabSupport.getYOffset(world, p, s);
        boolean match = approx(actual, expected);
        StringBuilder sb = new StringBuilder("[MATRIX] ");
        sb.append(cfg).append(" | ").append(level)
          .append(" | block=").append(id(s))
          .append(" | expected=").append(expected)
          .append(" | actual=").append(actual)
          .append(" | ").append(match ? "OK" : "MISMATCH");
        if (!match && byDesign) {
            sb.append(" (BY-DESIGN: no vertical accumulate / slab on non-lowered slab)");
        }
        if (!Double.isNaN(anchoredDy) && !approx(anchoredDy, actual)) {
            sb.append(" anchoredDy=").append(anchoredDy);
        }
        System.out.println(sb);
        ctx.assertTrue(actual >= DY_LOWER_BOUND - EPS,
                cfg + " " + level + " violates the designed -1.0 bound: dy=" + actual);
        return actual;
    }

    private static double record(GameTestHelper ctx, String cfg, String level, ServerLevel world,
                                 BlockPos p, double expected, boolean byDesign) {
        return record(ctx, cfg, level, world, p, expected, byDesign, Double.NaN);
    }

    // ── donor column 3 + 7b: pure-vanilla bottom-slab stacks (record-heavy) ──────────
    @GameTest(template = "empty")
    public void vanillaBottomSlabStacks(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos origin = ctx.absolutePos(new BlockPos(1, 1, 1));

        // 3. two stacked vanilla bottom slabs: production lowers a slab only when its
        // support is itself lowered, so the upper slab stays 0.0 (half-block gap is the
        // natural vanilla look, not an artifact). Law -0.5 recorded BY-DESIGN.
        String cfg3 = "3.vanillaBOTTOM/vanillaBOTTOM";
        BlockPos lower = origin;
        place(world, lower, vanillaBottom());
        place(world, lower.above(), vanillaBottom());
        double lowerDy = record(ctx, cfg3, "lowerSlab", world, lower, 0.0, false);
        ctx.assertTrue(approx(lowerDy, 0.0), cfg3 + " grounded lower slab must be 0.0, got " + lowerDy);
        record(ctx, cfg3, "upperSlab", world, lower.above(), -0.5, true);

        // caps on the (un-lowered) upper slab: object flush = its own -0.5 sit. STRICT law.
        BlockPos capL = origin.offset(0, 0, 2);
        place(world, capL, vanillaBottom());
        place(world, capL.above(), vanillaBottom());
        place(world, capL.above(2), Blocks.LANTERN.defaultBlockState());
        record(ctx, cfg3, "cap=lantern", world, capL.above(2), -0.5, false);

        BlockPos capB = origin.offset(0, 0, 4);
        place(world, capB, vanillaBottom());
        place(world, capB.above(), vanillaBottom());
        place(world, capB.above(2), Blocks.STONE.defaultBlockState());
        double fbAnchored = anchoredReread(world, capB.above(2));
        record(ctx, cfg3, "cap=fullBlock", world, capB.above(2), -0.5, false, fbAnchored);

        // 7b. pure-vanilla 3-high bottom-slab stack: no lowered source anywhere, nothing
        // lowers, and the accumulate law (-0.5/-1.0/-1.5) does not apply. BY-DESIGN records.
        String cfg7 = "7b.chain(vanBOTTOM/vanBOTTOM/vanBOTTOM)";
        BlockPos l0 = origin.offset(3, 0, 0);
        place(world, l0, vanillaBottom());
        place(world, l0.above(), vanillaBottom());
        place(world, l0.above(2), vanillaBottom());
        record(ctx, cfg7, "L0", world, l0, 0.0, false);
        record(ctx, cfg7, "L1", world, l0.above(), -0.5, true);
        record(ctx, cfg7, "L2", world, l0.above(2), -1.0, true);

        BlockPos cap7 = origin.offset(3, 0, 3);
        place(world, cap7, vanillaBottom());
        place(world, cap7.above(), vanillaBottom());
        place(world, cap7.above(2), vanillaBottom());
        place(world, cap7.above(3), Blocks.LANTERN.defaultBlockState());
        record(ctx, cfg7, "L3=lantern", world, cap7.above(3), -1.5, true);

        ctx.succeed();
    }

    /**
     * The same lowered vertical chain as {@code loweredFullBlockDeepStack}, carried PAST the
     * resolver's course budget.
     *
     * <p>That test stops at level 4, which is exactly where the shipped budget ran out, so the
     * snap it was meant to catch lived one course beyond its last assertion. A straight stack
     * lowers nothing — each course inherits its support's height and passes it along — so every
     * level must read the same -0.5 no matter how tall the column is. A 0.0 or a deeper value up
     * the column is the budget exhausting mid-descent and dropping the block, which reads in game
     * as the upper part of a tall stack snapping down.
     */
    @GameTest(template = "empty")
    public void loweredChainStaysFlushPastTheCourseBudget(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos base = ctx.absolutePos(new BlockPos(5, 1, 5));
        String cfg = "D.deepChain(vanBOTTOM/FB*10)";

        place(world, base, vanillaBottom());
        BlockPos fb1 = base.above();
        place(world, fb1, Blocks.STONE.defaultBlockState());
        SlabAnchorAttachment.addAnchor(world, fb1, world.getBlockState(fb1));

        double fb1Dy = record(ctx, cfg, "L1(anchoredFB)", world, fb1, -0.5, false);
        ctx.assertTrue(approx(fb1Dy, -0.5),
                cfg + " anchored FB on bottom slab must be -0.5, got " + fb1Dy);

        StringBuilder drift = new StringBuilder();
        BlockPos prev = fb1;
        for (int level = 2; level <= 10; level++) {
            BlockPos p = prev.above();
            place(world, p, Blocks.STONE.defaultBlockState());
            double dy = record(ctx, cfg, "L" + level + "(FB)", world, p, -0.5, false);
            if (!approx(dy, -0.5)) {
                drift.append(" | L").append(level).append("=").append(dy);
            }
            prev = p;
        }

        ctx.assertTrue(drift.isEmpty(),
                cfg + " every course of a straight lowered stack must stay -0.5; a straight stack"
                        + " descends nothing, so no course may spend the resolver's budget."
                        + " Drifted at:" + drift);
        ctx.succeed();
    }

    // ── NATIVE deep-stack resolver (Phase A class 10): lowered-FB vertical chain ──────
    @GameTest(template = "empty")
    public void loweredFullBlockDeepStack(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos base = ctx.absolutePos(new BlockPos(1, 1, 1));

        // bottomSlab -> anchored stone (-0.5, the legal "FB anchored on bottom slab" state)
        // -> 3 more stone above. The legal-state list names "ordinary full block in a proven
        // lowered vertical chain with dy=-0.5"; the flush law demands every level stay -0.5
        // (support is a full block, so no further half-step). A 0.0 anywhere in the chain is
        // a visible half-block window — the class-10 "deep-stack gap".
        String cfg = "D.deepChain(vanBOTTOM/FB*4)";
        place(world, base, vanillaBottom());
        BlockPos fb1 = base.above();
        place(world, fb1, Blocks.STONE.defaultBlockState());
        SlabAnchorAttachment.addAnchor(world, fb1, world.getBlockState(fb1));

        record(ctx, cfg, "L0(bottomSlab)", world, base, 0.0, false);
        double fb1Dy = record(ctx, cfg, "L1(anchoredFB)", world, fb1, -0.5, false);
        ctx.assertTrue(approx(fb1Dy, -0.5),
                cfg + " anchored FB on bottom slab must be -0.5, got " + fb1Dy);

        BlockPos prev = fb1;
        for (int level = 2; level <= 4; level++) {
            BlockPos p = prev.above();
            place(world, p, Blocks.STONE.defaultBlockState());
            double dy = record(ctx, cfg, "L" + level + "(FB)", world, p, -0.5, false);
            ctx.assertTrue(approx(dy, -0.5),
                    cfg + " L" + level + " FB in the lowered vertical chain must stay flush at -0.5"
                            + " (0.0 here is the deep-stack gap), got " + dy);
            prev = p;
        }

        // lantern topping the chain: flush law -0.5 (support is a lowered FB, top-type
        // surface). Recorded, not asserted — decorative followers have their own lanes.
        BlockPos cap = prev.above();
        place(world, cap, Blocks.LANTERN.defaultBlockState());
        record(ctx, cfg, "L5=lantern", world, cap, -0.5, false);

        // Slab-on-lowered-chain variant: bottom slab placed above the anchored FB follows
        // the lowered support one step (-0.5); a second slab above it is the no-accumulate
        // lane. First slab STRICT law; second recorded.
        String cfgS = "D.slabOnLoweredFB";
        BlockPos sBase = ctx.absolutePos(new BlockPos(4, 1, 1));
        place(world, sBase, vanillaBottom());
        BlockPos sFb = sBase.above();
        place(world, sFb, Blocks.STONE.defaultBlockState());
        SlabAnchorAttachment.addAnchor(world, sFb, world.getBlockState(sFb));
        place(world, sFb.above(), vanillaBottom());
        place(world, sFb.above(2), vanillaBottom());
        record(ctx, cfgS, "L0(bottomSlab)", world, sBase, 0.0, false);
        record(ctx, cfgS, "L1(anchoredFB)", world, sFb, -0.5, false);
        record(ctx, cfgS, "L2(bottomSlab)", world, sFb.above(), -0.5, false);
        record(ctx, cfgS, "L3(bottomSlab)", world, sFb.above(2), -1.0, true);

        ctx.succeed();
    }

    // ── donor column 8b: fence on a lowered full block (hard-assert, known-green) ─────
    @GameTest(template = "empty")
    public void fenceOnLoweredFullBlock(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        String cfg = "8b.fence/loweredFullBlock";
        BlockPos base = ctx.absolutePos(new BlockPos(1, 1, 1));
        BlockPos full = base.above();
        BlockPos fence = full.above();
        place(world, base, vanillaBottom());
        place(world, full, Blocks.STONE.defaultBlockState());
        SlabAnchorAttachment.addAnchor(world, full, world.getBlockState(full));
        place(world, fence, Blocks.OAK_FENCE.defaultBlockState());

        record(ctx, cfg, "base(vanillaBOTTOM)", world, base, 0.0, false);
        double fullDy = record(ctx, cfg, "fullBlock", world, full, -0.5, false);
        ctx.assertTrue(approx(fullDy, -0.5),
                cfg + " full block on bottom slab should be -0.5, got " + fullDy);
        double fenceDy = record(ctx, cfg, "fence", world, fence, -0.5, false);
        ctx.assertTrue(approx(fenceDy, -0.5),
                cfg + " fence on a lowered full block should follow to -0.5, got " + fenceDy);
        ctx.succeed();
    }

    /**
     * A column of the same block reads one height from top to bottom.
     *
     * <p>A stacked block of this kind is a member of its column, not a follower seating on a
     * surface: the pieces are contiguous by their own geometry, so the one above takes the height
     * of the one below rather than deriving a fresh seat from it. Deriving instead put every
     * stacked piece back at a single half step no matter how deep its column ran, which opens a
     * gap that grows with depth.
     *
     * <p>The column is built to several depths in one run. A single depth would pass while the
     * bug was present, because the shallowest column is exactly where the derived answer and the
     * inherited one agree.
     */
    @GameTest(template = "empty")
    public void aSameBlockColumnSharesOneHeight(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        StringBuilder failures = new StringBuilder();
        for (int height = 1; height <= 3; height++) {
            BlockPos ground = ctx.absolutePos(new BlockPos(1 + height * 2, 1, 16));
            world.setBlock(ground, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            for (int d = 1; d <= 9; d++) {
                world.setBlock(ground.above(d), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            Player player = ctx.makeMockPlayer();
            player.setPos(ground.getX() + 3.5d, ground.getY() + 3.0d, ground.getZ() + 0.5d);
            for (int course = 1; course <= height; course++) {
                BlockPos owner = ground.above(course - 1);
                BlockState os = world.getBlockState(owner);
                double ody = SlabSupport.getYOffset(world, owner, os);
                double otop = (os.getBlock() instanceof SlabBlock ? 0.5d : 1.0d) + ody;
                useAt(player, owner, otop,
                        course % 2 == 1 ? Blocks.OAK_SLAB : Blocks.STONE_SLAB);
            }
            BlockPos slabTop = ground.above(height);
            double slabDy = SlabSupport.getYOffset(world, slabTop, world.getBlockState(slabTop));
            useAt(player, slabTop, 0.5d + slabDy, Blocks.POINTED_DRIPSTONE);
            BlockPos lower = slabTop.above();
            if (world.getBlockState(lower).isAir()) {
                failures.append(" | depth ").append(slabDy).append(": first piece refused");
                continue;
            }
            double lowerDy = SlabSupport.getYOffset(world, lower, world.getBlockState(lower));
            // Click the piece's own visible tip, which is where a live ray lands on it.
            useAt(player, lower, 0.6875d + lowerDy, Blocks.POINTED_DRIPSTONE);
            BlockPos upper = lower.above();
            if (world.getBlockState(upper).isAir()) {
                failures.append(" | depth ").append(lowerDy).append(": stacked piece refused");
                continue;
            }
            double upperDy = SlabSupport.getYOffset(world, upper, world.getBlockState(upper));
            if (Math.abs(upperDy - lowerDy) > 1.0e-6d) {
                failures.append(" | column at ").append(lowerDy)
                        .append(" split: upper read ").append(upperDy);
            }
        }
        ctx.assertTrue(failures.isEmpty(),
                "every piece of one column must read the column height:" + failures);
        ctx.succeed();
    }

    private static void useAt(Player player, BlockPos clicked, double localTop, Block held) {
        ItemStack stack = new ItemStack(held);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(clicked.getX() + 0.5d, clicked.getY() + localTop,
                                clicked.getZ() + 0.5d),
                        Direction.UP, clicked, false)));
    }

}
