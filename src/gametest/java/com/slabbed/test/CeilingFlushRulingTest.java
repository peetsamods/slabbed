package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * D2 port (audit STATE_DEFENSE_DIVERGENCE_2026-07-07) of the maintainer's 2026-07-03 FLUSH-CEILING ruling:
 * the +0.5 "reach-up" for ceiling-attached objects under a FLUSH top slab is deprecated — everything
 * hangs flush. Donor: {@code isLoweringTopLikeCeiling -> false}, the ONE shared choke point for all
 * three dy-computing ceiling walks. Symptom (audit D2): placing a top slab above a lever / chain /
 * TOP-trapdoor smooshed it +0.5 up into the slab, and breaking the slab dropped it back — an
 * autonomously moving placed block (never-pop violated in the upward direction).
 *
 * <p>What deliberately SURVIVES the ruling (pinned by the controls here): the lowered-top-slab
 * flush-COMPENSATION (`slabDy + 0.5`, which nets &lt;= 0.0 — the maintainer's live-confirmed "trapdoor placed
 * under a lowered slab merges into it" fix, 26.2-native and absent from the donor).
 */
public final class CeilingFlushRulingTest {

    private static final double EPS = 1.0e-6;

    /** Real useOn click on a face of {@code clicked}; {@code yNudge} selects the upper/lower half. */
    private static void place(GameTestHelper helper, ItemStack stack, BlockPos clicked, Direction face,
                              double yNudge) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked)
                .add(face.getStepX() * 0.5, face.getStepY() * 0.5 + yNudge, face.getStepZ() * 0.5);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    private static double dy(ServerLevel w, BlockPos pos) {
        return SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
    }

    /** TOP-half oak trapdoor placed by a real high side-face click against a wall block. */
    private static BlockPos placeTopTrapdoor(GameTestHelper helper, ServerLevel w, BlockPos wall,
                                             BlockPos expected) {
        place(helper, new ItemStack(Items.OAK_TRAPDOOR), wall, Direction.NORTH, 0.3);
        BlockState state = w.getBlockState(expected);
        if (!state.hasProperty(BlockStateProperties.HALF)
                || state.getValue(BlockStateProperties.HALF) != Half.TOP) {
            throw helper.assertionException("scene premise: expected a TOP-half trapdoor at "
                    + expected.toShortString() + ", got " + state);
        }
        return expected;
    }

    private static void assertDy(GameTestHelper helper, ServerLevel w, BlockPos pos, double expected,
                                 String message) {
        double got = dy(w, pos);
        if (Math.abs(got - expected) > EPS) {
            throw helper.assertionException(message + ": expected dy " + expected + ", got " + got);
        }
    }

    /** Walk B (direct): a flush TOP slab appearing above a placed TOP trapdoor must not move it. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void topTrapdoorStaysFlushWhenTopSlabPlacedAbove(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos wall = helper.absolutePos(new BlockPos(2, 2, 3));
        BlockPos trapdoor = helper.absolutePos(new BlockPos(2, 2, 2));
        w.setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
        placeTopTrapdoor(helper, w, wall, trapdoor);
        assertDy(helper, w, trapdoor, 0.0, "premise: the placed trapdoor starts flush");
        w.setBlock(trapdoor.above(),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Block.UPDATE_ALL);
        assertDy(helper, w, trapdoor, 0.0,
                "D2 flush ruling (walk B): a flush top slab placed above must NOT reach the trapdoor +0.5 up");
        helper.succeed();
    }

    /** Walk C (cascading): the lower of two stacked TOP trapdoors under a flush top slab stays flush. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cascadedTopTrapdoorStaysFlushUnderTopSlab(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos wallLow = helper.absolutePos(new BlockPos(2, 2, 3));
        BlockPos wallHigh = helper.absolutePos(new BlockPos(2, 3, 3));
        BlockPos lower = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos upper = helper.absolutePos(new BlockPos(2, 3, 2));
        w.setBlock(wallLow, Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(wallHigh, Blocks.STONE.defaultBlockState(), 2);
        placeTopTrapdoor(helper, w, wallLow, lower);
        placeTopTrapdoor(helper, w, wallHigh, upper);
        assertDy(helper, w, lower, 0.0, "premise: the lower trapdoor starts flush");
        w.setBlock(upper.above(),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Block.UPDATE_ALL);
        assertDy(helper, w, lower, 0.0,
                "D2 flush ruling (walk C): the flush top slab above the CASCADE must not reach the lower trapdoor up");
        assertDy(helper, w, upper, 0.0,
                "D2 flush ruling (walk B via cascade scene): the upper trapdoor must stay flush too");
        helper.succeed();
    }

    /** Walk A (always-hung family): hanging roots under a flush top slab hang flush, not +0.5. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingRootsUnderFlushTopSlabStayFlush(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos roots = slab.below();
        w.setBlock(slab, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        place(helper, new ItemStack(Items.HANGING_ROOTS), slab, Direction.DOWN, 0.0);
        if (!w.getBlockState(roots).is(Blocks.HANGING_ROOTS)) {
            throw helper.assertionException("scene premise: hanging roots must place under the top slab, got "
                    + w.getBlockState(roots));
        }
        assertDy(helper, w, roots, 0.0,
                "D2 flush ruling (walk A): roots under a FLUSH top slab hang flush, not reached +0.5 up");
        helper.succeed();
    }

    /**
     * Ceiling LEVER — a named D2 symptom family with previously zero dy coverage (medium sweeper).
     * SlabSupportStateMixin makes any TOP/DOUBLE slab underside sturdy, so ceiling levers genuinely
     * survive under top slabs in this codebase; the ruling must keep them flush.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void ceilingLeverUnderFlushTopSlabStaysFlush(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos lever = slab.below();
        w.setBlock(slab, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        place(helper, new ItemStack(Items.LEVER), slab, Direction.DOWN, 0.0);
        if (!w.getBlockState(lever).is(Blocks.LEVER)) {
            throw helper.assertionException("scene premise: the lever must place under the top slab, got "
                    + w.getBlockState(lever));
        }
        assertDy(helper, w, lever, 0.0,
                "D2 flush ruling: a ceiling lever under a FLUSH top slab hangs flush, not +0.5");
        helper.succeed();
    }

    /** Ceiling BUTTON — same family, same ruling. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void ceilingButtonUnderFlushTopSlabStaysFlush(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos button = slab.below();
        w.setBlock(slab, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        place(helper, new ItemStack(Items.STONE_BUTTON), slab, Direction.DOWN, 0.0);
        if (!w.getBlockState(button).is(Blocks.STONE_BUTTON)) {
            throw helper.assertionException("scene premise: the button must place under the top slab, got "
                    + w.getBlockState(button));
        }
        assertDy(helper, w, button, 0.0,
                "D2 flush ruling: a ceiling button under a FLUSH top slab hangs flush, not +0.5");
        helper.succeed();
    }

    /**
     * The BREAK direction of the audit symptom ("jump on break"): the dy must stay 0.0 through the
     * whole slab lifecycle — absent, placed above, REMOVED again. Pins the transition itself instead
     * of relying on two static snapshots being the same code path (medium sweeper: a future anchor or
     * cache on the ceiling path could break the transition without breaking either snapshot).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void topTrapdoorHoldsFlushThroughSlabPlaceAndBreak(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos wall = helper.absolutePos(new BlockPos(2, 2, 3));
        BlockPos trapdoor = helper.absolutePos(new BlockPos(2, 2, 2));
        w.setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
        placeTopTrapdoor(helper, w, wall, trapdoor);
        assertDy(helper, w, trapdoor, 0.0, "premise: flush before any slab exists");
        w.setBlock(trapdoor.above(),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Block.UPDATE_ALL);
        assertDy(helper, w, trapdoor, 0.0, "D2: flush while the top slab is above");
        w.setBlock(trapdoor.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        assertDy(helper, w, trapdoor, 0.0, "D2 (jump-on-break): still flush after the slab is broken");
        helper.succeed();
    }

    // ── CONTROLS: the lowered-top-slab paths deliberately SURVIVE the ruling ──

    /** Side-lowered TOP slab (over air, beside a lowered column) for the compensation controls. */
    private static BlockPos buildSideLoweredTopSlab(GameTestHelper helper, ServerLevel w) {
        BlockPos base = helper.absolutePos(new BlockPos(3, 1, 2));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(base.above(1),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        BlockPos slab = helper.absolutePos(new BlockPos(2, 3, 2));
        w.setBlock(slab, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        double slabDy = SlabSupport.getYOffset(w, slab, w.getBlockState(slab));
        if (Math.abs(slabDy + 0.5) > EPS) {
            throw helper.assertionException("scene premise: the side-lowered TOP slab must read -0.5, got " + slabDy);
        }
        return slab;
    }

    /**
     * POST-RULING healing pin (was RED even before the D2 patch, for a different reason): the
     * side-lowered slab's -0.5 comes from the AIR-gated RC2-A branch, and once the trapdoor occupies
     * slab.below() that gate no longer fires — so walk B's nested read of the slab sees 0.0, not
     * -0.5 (a STATE-DEPENDENT blindness, traced by the D2 medium sweeper). Pre-ruling the trapdoor
     * therefore smooshed +0.5 INTO the -0.5 slab. Post-ruling it reads 0.0 via the flush fallback —
     * which happens to equal the correct merge value for a -0.5 slab (slabDy + 0.5 = 0.0), so this
     * pin stays green even if the air-gate blindness is ever fixed (both mechanisms produce 0.0).
     * NOTE: this scene does NOT exercise the aboveDy+0.5 compensation formula — only the marked-upper
     * controls below prove that leg (-1.0 -> -0.5).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void trapdoorUnderSideLoweredTopSlabReadsMergedFlush(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = buildSideLoweredTopSlab(helper, w);
        BlockPos wall = helper.absolutePos(new BlockPos(2, 2, 3));
        BlockPos trapdoor = slab.below();
        w.setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
        placeTopTrapdoor(helper, w, wall, trapdoor);
        assertDy(helper, w, trapdoor, 0.0,
                "post-ruling: under a -0.5 side-lowered top slab the trapdoor reads the merged 0.0 (slabDy + 0.5)");
        helper.succeed();
    }

    /** Same healing pin for walk A (same state-dependent air-gate mechanism; see the trapdoor pin above). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingRootsUnderSideLoweredTopSlabReadMergedFlush(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = buildSideLoweredTopSlab(helper, w);
        BlockPos roots = slab.below();
        place(helper, new ItemStack(Items.HANGING_ROOTS), slab, Direction.DOWN, 0.0);
        if (!w.getBlockState(roots).is(Blocks.HANGING_ROOTS)) {
            throw helper.assertionException("scene premise: hanging roots must place under the lowered top slab, got "
                    + w.getBlockState(roots));
        }
        assertDy(helper, w, roots, 0.0,
                "post-ruling: under a -0.5 side-lowered top slab the roots read the merged 0.0 (slabDy + 0.5)");
        helper.succeed();
    }

    // ── TRUE through-the-change CONTROLS: the compound-visible side-UPPER marker is a pure attachment
    // read (recursion-safe from inside the walks), so the lowered-compensation leg is observable both
    // BEFORE and AFTER the ruling: object dy == slabDy + 0.5, with slabDy = -1.0. ──

    /** Marked side-UPPER TOP slab (-1.0, authored marker beside a genuine compound stack). */
    private static BlockPos buildMarkedUpperTopSlab(GameTestHelper helper, ServerLevel w) {
        BlockPos base = helper.absolutePos(new BlockPos(3, 1, 2));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(base.above(1),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(base.above(3),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        BlockPos fb = base.above(4);
        w.setBlock(fb, Blocks.STONE.defaultBlockState(), 2);
        com.slabbed.anchor.SlabAnchorAttachment.addAnchor(w, fb, w.getBlockState(fb));
        com.slabbed.anchor.SlabAnchorAttachment.addCompoundFullBlockAnchor(w, fb, w.getBlockState(fb));
        BlockPos slab = fb.west();
        w.setBlock(slab, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        com.slabbed.anchor.SlabAnchorAttachment.addCompoundVisibleSideUpperSlab(w, slab, w.getBlockState(slab),
                fb, w.getBlockState(fb));
        double slabDy = SlabSupport.getYOffset(w, slab, w.getBlockState(slab));
        if (Math.abs(slabDy + 1.0) > EPS) {
            throw helper.assertionException("scene premise: the marked side-UPPER top slab must read -1.0, got " + slabDy);
        }
        return slab;
    }

    /**
     * D2 high-sweeper finding 1 (verified, fixed): a CASCADED hanger whose column tops at a lowered
     * TOP slab must read the SAME merged dy as its carrier. Pre-D2 the reach-up return shadowed walk
     * A's lowered tail for every top-slab top; the ruling exposed the tail's missing +0.5 merge
     * compensation and the lower hanger sank to raw -1.0 while its carrier sat at -0.5.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cascadedHangingSignUnderMarkedUpperSlabFollowsCarrier(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = buildMarkedUpperTopSlab(helper, w);
        BlockPos upper = slab.below();
        BlockPos lower = upper.below();
        w.setBlock(upper, Blocks.OAK_HANGING_SIGN.defaultBlockState(), 2);
        w.setBlock(lower, Blocks.OAK_HANGING_SIGN.defaultBlockState(), 2);
        assertDy(helper, w, upper, -0.5,
                "premise/direct leg: the upper hanging sign merges at -0.5 under the -1.0 marked slab");
        assertDy(helper, w, lower, -0.5,
                "D2 cascade tail: the lower hanging sign must match its carrier (-0.5), not sink to raw -1.0");
        helper.succeed();
    }

    /**
     * The GEOMETRIC walk-B compensation leg, pinned on an UNANCHORED trapdoor (setBlock — no
     * placement hooks). A real useOn placement anchors at dy&lt;0 and the anchored branch's -0.5
     * fallback COINCIDES with the compensation value here, so the anchored control below cannot
     * see this leg (exposed when the compensation-drop mutation survived the suite). The geometric
     * leg is what serves support-arrives-later / worldgen / command-placed cells.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unanchoredTrapdoorUnderMarkedUpperSlabReadsGeometricMerge(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = buildMarkedUpperTopSlab(helper, w);
        BlockPos trapdoor = slab.below();
        w.setBlock(trapdoor, Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HALF, Half.TOP), 2);
        if (com.slabbed.anchor.SlabAnchorAttachment.isAnchored(w, trapdoor)) {
            throw helper.assertionException("scene premise: the setBlock trapdoor must be UNANCHORED");
        }
        assertDy(helper, w, trapdoor, -0.5,
                "walk B geometric compensation: unanchored trapdoor under the -1.0 marked slab merges at -0.5");
        helper.succeed();
    }

    /** the maintainer's live-confirmed merge: trapdoor under the -1.0 marked slab reads -0.5 — survives the ruling. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void controlTrapdoorUnderMarkedUpperSlabKeepsCompensation(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = buildMarkedUpperTopSlab(helper, w);
        BlockPos wall = helper.absolutePos(new BlockPos(2, 4, 3));
        BlockPos trapdoor = slab.below();
        w.setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
        placeTopTrapdoor(helper, w, wall, trapdoor);
        assertDy(helper, w, trapdoor, -0.5,
                "control: walk B's lowered-compensation (slabDy -1.0 + 0.5 = -0.5 merge) must survive the ruling");
        helper.succeed();
    }

    /** Walk A compensation control: roots under the -1.0 marked slab read -0.5 — survives the ruling. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void controlHangingRootsUnderMarkedUpperSlabKeepCompensation(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = buildMarkedUpperTopSlab(helper, w);
        BlockPos roots = slab.below();
        place(helper, new ItemStack(Items.HANGING_ROOTS), slab, Direction.DOWN, 0.0);
        if (!w.getBlockState(roots).is(Blocks.HANGING_ROOTS)) {
            throw helper.assertionException("scene premise: hanging roots must place under the marked top slab, got "
                    + w.getBlockState(roots));
        }
        assertDy(helper, w, roots, -0.5,
                "control: walk A's lowered-compensation (supportDy -1.0 + 0.5 = -0.5) must survive the ruling");
        helper.succeed();
    }
}
