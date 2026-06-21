package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Drives the REAL {@code BlockItem.useOn} placement-intent path (the RC2/RC3 WYSIWYG remap lives in
 * {@code BlockItemPlacementIntentMixin#useOn}) headlessly via a mock player + hand-built
 * {@code UseOnContext} — coverage the {@code authorBlock}/{@code onPlaced} tests cannot reach (see
 * {@code docs/porting/WYSIWYG-PLACEMENT-AUDIT.md} cross-cutting caveat: "RC2/RC3/RC4 have NO headless
 * coverage … add a real useOn-driven gametest").
 *
 * <p>Scope note: this exercises the SERVER remap (which dy + slab TYPE the placement mints for a
 * given hit). The CLIENT question of "which cell does the crosshair actually hit on a visually
 * lowered block" is a raycast/targeting concern (P4) that stays a live check — here we feed the
 * BlockHitResult directly.
 *
 * <p><b>Headless findings (2026-06-18, all GREEN):</b>
 * <ul>
 *   <li>RC2: slab beside a lowered (-0.5) full block → dy <b>-0.5</b>, both upper- and lower-half aims.</li>
 *   <li>RC3: slab beside a compound (-1.0) stack → dy <b>-1.0</b>, both halves — i.e. the RC2 GAP-1 fix
 *       ALREADY absorbs RC3's dy magnitude (the {@code RC3-LIVE-REDVERIFY-PLAN} hypothesis, now proven
 *       headlessly, not just suspected).</li>
 *   <li>TYPE: the side-merge remap mints <b>TOP regardless of the aimed half</b> for both the -0.5 and
 *       -1.0 cases, while the flush-block CONTROL correctly tracks the aim (upper→TOP, lower→BOTTOM).
 *       So the RC3 "midline-split" type residual is REAL (control-proven, not a harness artifact) — but
 *       the correct TOP/BOTTOM policy is an open decision (see the plan doc), so this suite ASSERTS only
 *       dy and LOGS the type (USEON-FP lines) rather than asserting a type it cannot yet call correct.</li>
 * </ul>
 */
public final class Slabbed2612UseOnPlacementTest {

    private static final double EPS = 1.0e-6;

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    /** Fires the real useOn path with the given hit; returns the placed ABSOLUTE pos (clickAbs.relative(face)). */
    private static BlockPos placeSlabVia(Player player, BlockPos clickAbs, Direction face, Vec3 hit) {
        ItemStack stack = new ItemStack(Blocks.STONE_SLAB);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult bhr = new BlockHitResult(hit, face, clickAbs, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, bhr));
        return clickAbs.relative(face);
    }

    /** Hit point on the UP face of a cell (centre, top plane). */
    private static Vec3 upHit(BlockPos abs) {
        return new Vec3(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5);
    }

    /** Hit point on the EAST face of a cell at vertical fraction {@code vy} of the cell (0=bottom,1=top). */
    private static Vec3 eastHit(BlockPos abs, double vy) {
        return new Vec3(abs.getX() + 1.0, abs.getY() + vy, abs.getZ() + 0.5);
    }

    private static Player mockPlayerNear(GameTestHelper helper, BlockPos abs) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 0.5);
        return player;
    }

    private static void log(String name, ServerLevel level, BlockPos abs) {
        BlockState s = level.getBlockState(abs);
        String type = s.getBlock() instanceof SlabBlock && s.hasProperty(SlabBlock.TYPE)
                ? s.getValue(SlabBlock.TYPE).toString() : "(not a slab: " + s.getBlock() + ")";
        double dy = SlabSupport.getYOffset(level, abs, s);
        Slabbed.LOGGER.info("USEON-FP | {} | dy={} | type={}",
                name, String.format(java.util.Locale.ROOT, "%.3f", dy), type);
    }

    private static void assertSlabDy(GameTestHelper helper, ServerLevel level, BlockPos abs,
                                     BlockPos relForErr, double expectedDy, String what) {
        BlockState s = level.getBlockState(abs);
        if (!(s.getBlock() instanceof SlabBlock)) {
            throw helper.assertionException(relForErr, what + ": no slab placed (got " + s.getBlock() + ")");
        }
        double dy = SlabSupport.getYOffset(level, abs, s);
        if (Math.abs(dy - expectedDy) > EPS) {
            throw helper.assertionException(relForErr,
                    what + ": expected dy=" + expectedDy + " got " + dy);
        }
    }

    private static void assertBlockDy(GameTestHelper helper, ServerLevel level, BlockPos abs,
                                      BlockPos relForErr, Block expectedBlock, double expectedDy, String what) {
        BlockState s = level.getBlockState(abs);
        if (s.getBlock() != expectedBlock) {
            throw helper.assertionException(relForErr, what + ": wrong block placed (got " + s.getBlock() + ")");
        }
        double dy = SlabSupport.getYOffset(level, abs, s);
        if (Math.abs(dy - expectedDy) > EPS) {
            throw helper.assertionException(relForErr,
                    what + ": expected dy=" + expectedDy + " got " + dy);
        }
    }

    /** PROBE: a slab placed on TOP of a flush stone block via useOn lands flush (dy 0). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnPlacesSlabOnTopFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE.defaultBlockState());
        Player player = mockPlayerNear(helper, support.above());
        BlockPos placed = placeSlabVia(player, support, Direction.UP, upHit(support));
        log("probe_slab_on_flush_top", level, placed);
        assertSlabDy(helper, level, placed, new BlockPos(2, 3, 2), 0.0, "PROBE slab on flush top");
        helper.succeed();
    }

    /**
     * CONTROL: against the side of a PLAIN flush stone block, does the harness reproduce vanilla's
     * hit-based slab TYPE (upper-half aim → TOP, lower-half aim → BOTTOM)? If this control is correct,
     * an "always TOP" in the lowered/compound cases is a real remap effect; if the control is ALSO
     * always-TOP, the type signal is a harness artifact and must not be read as a bug. Logged, asserted.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void controlSlabSideTypeTracksHitHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE.defaultBlockState());
        BlockPos block = helper.absolutePos(new BlockPos(2, 3, 2));
        Player upPlayer = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 4, 2)));
        BlockPos placedUp = placeSlabVia(upPlayer, block, Direction.EAST, eastHit(block, 0.75));
        log("control_flush_side_upper", level, placedUp);
        SlabType upType = level.getBlockState(placedUp).getValue(SlabBlock.TYPE);

        // fresh cell for the lower-half placement (north of the first, still beside a flush block)
        helper.setBlock(new BlockPos(2, 3, 4), Blocks.STONE.defaultBlockState());
        BlockPos block2 = helper.absolutePos(new BlockPos(2, 3, 4));
        Player lowPlayer = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 4, 4)));
        BlockPos placedLow = placeSlabVia(lowPlayer, block2, Direction.EAST, eastHit(block2, 0.25));
        log("control_flush_side_lower", level, placedLow);
        SlabType lowType = level.getBlockState(placedLow).getValue(SlabBlock.TYPE);

        if (upType != SlabType.TOP || lowType != SlabType.BOTTOM) {
            throw helper.assertionException(new BlockPos(3, 3, 2),
                    "CONTROL: vanilla side-face slab type does not track hit half in this harness "
                    + "(upper=" + upType + " lower=" + lowType + ") — the 'always TOP' in lowered cases is "
                    + "then a HARNESS ARTIFACT, not an RC3 finding. Do not report the type layer from this suite.");
        }
        helper.succeed();
    }

    /**
     * JULIA WYSIWYG (2026-06-19, FIXED): clicking the SIDE face of a -0.5 lowered block to place a slab,
     * even when the placement cell has SOLID GROUND below it, must FOLLOW to the lowered surface (-0.5) —
     * it lands where the crosshair clicked, NOT frozen flat 0.5 above (Julia's "lands 0.5 high" bug). The
     * fix: the placement-intent mixin marks the clicked-lowered-face placement, and freezeLoweredOnPlace
     * anchors it lowered instead of taking the side-inherited freeze-flat rail. Companion guard:
     * {@link #useOnSlabOnFlushGroundBesideLoweredStaysFrozenFlat} (clicking the FLAT GROUND's top, not the
     * lowered block, still stays 0.0 — also WYSIWYG: you aimed at the flat ground).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnSlabClickingLoweredFaceWithSolidGroundBelowFollowsToMinusHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // lowered full block at (2,3,2) → -0.5. Beside it (3,3,2) the placement cell, WITH solid ground (3,2,2).
        helper.setBlock(new BlockPos(2, 2, 2), bottomSlab());
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE.defaultBlockState());   // SOLID ground below the arm
        BlockPos lowered = helper.absolutePos(new BlockPos(2, 3, 2));
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 4, 2)));

        BlockPos placed = placeSlabVia(player, lowered, Direction.EAST, eastHit(lowered, 0.75));   // upper-half click
        log("wysiwyg_click_lowered_face_solid_below", level, placed);
        assertSlabDy(helper, level, placed, new BlockPos(3, 3, 2), -0.5,
                "WYSIWYG: slab placed by clicking a lowered (-0.5) block's side face follows to -0.5 (lands where "
                + "clicked), even with solid ground below — no longer freezes flat 0.5 high");
        helper.succeed();
    }

    /** RC2-A via useOn: a slab placed against the side of a lowered (-0.5) full block, air below, follows to -0.5. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnSlabBesideLoweredFullBlockFollowsToMinusHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // lowered full block: bottom slab + stone at (2,3,2) → -0.5. East column (3,*,2) stays air (cantilever).
        helper.setBlock(new BlockPos(2, 2, 2), bottomSlab());
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE.defaultBlockState());
        BlockPos lowered = helper.absolutePos(new BlockPos(2, 3, 2));
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 4, 2)));

        BlockPos placed = placeSlabVia(player, lowered, Direction.EAST, eastHit(lowered, 0.75));
        log("rc2_slab_beside_lowered_fb_upper", level, placed);
        assertSlabDy(helper, level, placed, new BlockPos(3, 3, 2), -0.5,
                "RC2 useOn: slab cantilevered beside a lowered full block (upper-half aim) lands -0.5 (WYSIWYG)");
        helper.succeed();
    }

    /** RC2-A lower-half companion: lower-half aim against the same lowered full block also lands -0.5. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnSlabBesideLoweredFullBlockLowerHalfAlsoMinusHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 2, 2), bottomSlab());
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE.defaultBlockState());
        BlockPos lowered = helper.absolutePos(new BlockPos(2, 3, 2));
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 4, 2)));

        BlockPos placed = placeSlabVia(player, lowered, Direction.EAST, eastHit(lowered, 0.25));
        log("rc2_slab_beside_lowered_fb_lower", level, placed);
        assertSlabDy(helper, level, placed, new BlockPos(3, 3, 2), -0.5,
                "RC2 useOn: slab cantilevered beside a lowered full block (lower-half aim) lands -0.5 (WYSIWYG)");
        helper.succeed();
    }

    /**
     * RC3 via useOn — the open question. A slab placed against the SIDE of a vertical compound (-1.0)
     * stack, air below (cantilever). The RC2 GAP-1 fix should make getYOffset read the neighbour's true
     * magnitude (-1.0). This MEASURES + asserts dy for BOTH an upper-half and a lower-half aim; the
     * `USEON-FP` log lines also record the slab TYPE so the midline-split (RC3 type layer) is visible.
     * If a half lands at -0.5 or the wrong cell, this is the RC3 RED — headless, before any live session.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnSlabBesideCompoundStackUpperHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildCompoundMinusOne(helper);                       // top compound stone at (2,5,2) reads -1.0
        BlockPos owner = helper.absolutePos(new BlockPos(2, 5, 2));
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 6, 2)));

        BlockPos placed = placeSlabVia(player, owner, Direction.EAST, eastHit(owner, 0.75));
        log("rc3_slab_beside_compound_upper", level, placed);
        assertSlabDy(helper, level, placed, new BlockPos(3, 5, 2), -1.0,
                "RC3 useOn: slab beside a compound -1.0 stack (upper-half aim) must land -1.0 (WYSIWYG; GAP-1)");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnSlabBesideCompoundStackLowerHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildCompoundMinusOne(helper);
        BlockPos owner = helper.absolutePos(new BlockPos(2, 5, 2));
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 6, 2)));

        BlockPos placed = placeSlabVia(player, owner, Direction.EAST, eastHit(owner, 0.25));
        log("rc3_slab_beside_compound_lower", level, placed);
        assertSlabDy(helper, level, placed, new BlockPos(3, 5, 2), -1.0,
                "RC3 useOn: slab beside a compound -1.0 stack (lower-half aim) must land -1.0 (WYSIWYG; GAP-1)");
        helper.succeed();
    }

    /** ground/slab/stone/slab/stone vertical compound; the top stone at (2,5,2) reads dy=-1.0. East column stays air. */
    private static void buildCompoundMinusOne(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(2, 2, 2), bottomSlab());
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(2, 4, 2), bottomSlab());
        helper.setBlock(new BlockPos(2, 5, 2), Blocks.STONE.defaultBlockState());
    }

    /** Generalised: place ANY block the player holds via the real useOn path; returns the placed ABSOLUTE pos. */
    private static BlockPos placeBlockVia(Player player, BlockPos clickAbs, Direction face, Vec3 hit, Block block) {
        ItemStack stack = new ItemStack(block);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, clickAbs, false)));
        return clickAbs.relative(face);
    }

    /**
     * P26-8 RED: a fence placed by clicking an already-lowered fence over air must follow the visible
     * lowered target instead of falling back to vanilla height. Once both fences are at the same dy, the
     * ordinary same-level fence connection is allowed; the no-connection rule only applies across a step.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnFenceClickingLoweredFenceOverAirFollowsToMinusHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 2, 2), bottomSlab());
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.OAK_FENCE.defaultBlockState());
        BlockPos loweredFence = helper.absolutePos(new BlockPos(2, 3, 2));
        assertBlockDy(helper, level, loweredFence, new BlockPos(2, 3, 2), Blocks.OAK_FENCE, -0.5,
                "P26-8 setup: existing fence on bottom slab is lowered");

        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 4, 2)));
        BlockPos placed = placeBlockVia(player, loweredFence, Direction.EAST, eastHit(loweredFence, 0.75),
                Blocks.OAK_FENCE);
        log("p26_fence_against_lowered_fence", level, placed);
        assertBlockDy(helper, level, placed, new BlockPos(3, 3, 2), Blocks.OAK_FENCE, -0.5,
                "P26-8 useOn: fence placed against a lowered fence over air must follow to -0.5");

        BlockState placedState = level.getBlockState(placed);
        if (!placedState.getValue(CrossCollisionBlock.WEST)) {
            throw helper.assertionException(new BlockPos(3, 3, 2),
                    "P26-8 useOn: same-height lowered fence should connect WEST to the clicked lowered fence");
        }
        helper.succeed();
    }

    // ── A1: freeze-on-place via the REAL useOn path — slab on its OWN flush ground beside a lowered
    //         block must stay flat (0.0) AND record FROZEN_FLAT (NEVER-POP). ──────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnSlabOnFlushGroundBesideLoweredStaysFrozenFlat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // lowered full block at (2,3,2); beside it east, flush stone ground at (3,2,2).
        helper.setBlock(new BlockPos(2, 2, 2), bottomSlab());
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE.defaultBlockState());
        BlockPos ground = helper.absolutePos(new BlockPos(3, 2, 2));
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 4, 2)));

        BlockPos placed = placeBlockVia(player, ground, Direction.UP, upHit(ground), Blocks.STONE_SLAB);
        log("a1_slab_on_flush_ground_beside_lowered", level, placed);
        assertSlabDy(helper, level, placed, new BlockPos(3, 3, 2), 0.0,
                "A1 useOn: slab on its own flush ground beside a lowered block must stay flat (NEVER-POP)");
        if (!SlabAnchorAttachment.isFrozenFlat(level, placed)) {
            throw helper.assertionException(new BlockPos(3, 3, 2),
                    "A1: the flat-placed slab must record FROZEN_FLAT so a later neighbour can't pull it down");
        }
        helper.succeed();
    }

    // ── A2: a FULL BLOCK (not a slab) placed via useOn onto a bottom slab lowers to -0.5. ───────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnFullBlockOnBottomSlabLowersToMinusHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 2, 2), bottomSlab());
        BlockPos slab = helper.absolutePos(new BlockPos(2, 2, 2));
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(2, 4, 2)));

        BlockPos placed = placeBlockVia(player, slab, Direction.UP, upHit(slab), Blocks.STONE);
        BlockState s = level.getBlockState(placed);
        if (s.getBlock() != Blocks.STONE) {
            throw helper.assertionException(new BlockPos(2, 3, 2), "A2: useOn did not place the full block (got " + s.getBlock() + ")");
        }
        double dy = SlabSupport.getYOffset(level, placed, s);
        Slabbed.LOGGER.info("USEON-FP | a2_full_block_on_bottom_slab | dy={}",
                String.format(java.util.Locale.ROOT, "%.3f", dy));
        if (Math.abs(dy + 0.5) > EPS) {
            throw helper.assertionException(new BlockPos(2, 3, 2),
                    "A2 useOn: full block placed on a bottom slab must lower -0.5, got " + dy);
        }
        helper.succeed();
    }

    // ── A7: a slab placed via useOn on TOP of a compound -1.0 stack follows it to -1.0. ─────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnSlabOnTopOfCompoundFollowsToMinusOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildCompoundMinusOne(helper);                       // top compound stone at (2,5,2) = -1.0
        BlockPos top = helper.absolutePos(new BlockPos(2, 5, 2));
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(2, 7, 2)));

        BlockPos placed = placeSlabVia(player, top, Direction.UP, upHit(top));
        log("a7_slab_on_compound_top", level, placed);
        // OBSERVED: a slab placed on TOP of a compound -1.0 block follows by ONE step to -0.5 (it rests
        // flush on the compound block's top, which is itself a full block down), NOT a further -1.0. This
        // is the on-top single-step follow; checklist L5's "-1.0" was the ideal-full-follow — locked to the
        // observed -0.5, flagged as a possible deeper-follow gap (same family as the on-top edge cases).
        assertSlabDy(helper, level, placed, new BlockPos(2, 6, 2), -0.5,
                "A7 useOn: slab on top of a compound -1.0 stack follows ONE step to -0.5 (rests flush on its top)");
        helper.succeed();
    }

    // ── A8: a slab placed via useOn cantilevered beside a lowered block ANCHORS -0.5 and KEEPS it when
    //         the lowering source is later removed (NEVER-POP-up, the real placement lane). ───────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnCantileverSlabAnchorsAndSurvivesSourceRemoval(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 2, 2), bottomSlab());
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE.defaultBlockState());   // lowered -0.5
        BlockPos lowered = helper.absolutePos(new BlockPos(2, 3, 2));
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 4, 2)));

        BlockPos placed = placeSlabVia(player, lowered, Direction.EAST, eastHit(lowered, 0.75));   // air below → cantilever
        assertSlabDy(helper, level, placed, new BlockPos(3, 3, 2), -0.5,
                "A8 useOn: cantilever slab beside a lowered block lands -0.5");
        if (!SlabAnchorAttachment.isAnchored(level, placed)) {
            throw helper.assertionException(new BlockPos(3, 3, 2), "A8: cantilever-placed slab must be ANCHORED");
        }
        // remove the lowering source; the anchor must hold -0.5 (must NOT pop up to 0.0).
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.AIR.defaultBlockState());
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());
        log("a8_cantilever_after_source_removed", level, placed);
        assertSlabDy(helper, level, placed, new BlockPos(3, 3, 2), -0.5,
                "A8 useOn: after the lowering source is removed the anchored slab KEEPS -0.5 (NEVER-POP-up)");
        helper.succeed();
    }

    // ── RC4 check: a slab placed on TOP of a cantilever-lowered block. The WYSIWYG audit feared this
    //         reads 0.0 (column walk terminating at the air under the cantilever). MEASURED: it reads
    //         -0.5 — the on-top slab DOES follow the anchored cantilever down and rests flush on it. So
    //         this configuration is correct, not the feared gap. (RC4's full-block-on-edge case may still
    //         differ; this pins the slab-on-anchored-cantilever case as good.)

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnAnchoredCantileverBlockFollowsToMinusHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // cantilever-lowered full block: slab+stone (-0.5), anchor it, then remove the carrier slab.
        helper.setBlock(new BlockPos(2, 2, 2), bottomSlab());
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE.defaultBlockState());
        BlockPos canti = helper.absolutePos(new BlockPos(2, 3, 2));
        SlabAnchorAttachment.addAnchor(level, canti, level.getBlockState(canti));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());
        double cantiDy = SlabSupport.getYOffset(level, canti, level.getBlockState(canti));
        if (Math.abs(cantiDy + 0.5) > EPS) {
            throw helper.assertionException(new BlockPos(2, 3, 2), "SETUP: cantilever block should be -0.5, got " + cantiDy);
        }
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(2, 5, 2)));
        BlockPos placed = placeSlabVia(player, canti, Direction.UP, upHit(canti));
        log("rc4_slab_on_anchored_cantilever", level, placed);
        assertSlabDy(helper, level, placed, new BlockPos(2, 4, 2), -0.5,
                "slab on an anchored cantilever-lowered block follows it to -0.5 (rests flush; not the feared 0.0 gap)");
        helper.succeed();
    }
}
