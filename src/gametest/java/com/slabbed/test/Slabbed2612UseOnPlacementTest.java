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

    /** Fires useOn and returns the actual nearby slab cell whose blockstate changed. */
    private static BlockPos placeSlabViaAndFindChangedSlab(
            GameTestHelper helper,
            Player player,
            BlockPos clickAbs,
            Direction face,
            Vec3 hit
    ) {
        ServerLevel level = helper.getLevel();
        BlockPos[] candidates = new BlockPos[]{
                clickAbs,
                clickAbs.relative(face),
                clickAbs.above(),
                clickAbs.below(),
                clickAbs.north(),
                clickAbs.south(),
                clickAbs.east(),
                clickAbs.west()
        };
        BlockState[] before = new BlockState[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            before[i] = level.getBlockState(candidates[i]);
        }

        placeSlabVia(player, clickAbs, face, hit);

        for (int i = 0; i < candidates.length; i++) {
            BlockState after = level.getBlockState(candidates[i]);
            if (!after.equals(before[i]) && after.getBlock() instanceof SlabBlock) {
                return candidates[i];
            }
        }
        throw helper.assertionException(
                helper.relativePos(clickAbs),
                "useOn did not mutate a nearby slab cell for click=" + clickAbs.toShortString()
                        + " face=" + face + " hit=" + hit);
    }

    /** Hit point on the UP face of a cell (centre, top plane). */
    private static Vec3 upHit(BlockPos abs) {
        return new Vec3(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5);
    }

    /** Hit point on the visible UP face of a lowered TOP slab (its model is shifted down by 0.5). */
    private static Vec3 loweredTopSlabVisibleUpHit(BlockPos abs, double localX, double localZ) {
        return new Vec3(abs.getX() + localX, abs.getY() + 0.5, abs.getZ() + localZ);
    }

    /** Hit point on the EAST face of a cell at vertical fraction {@code vy} of the cell (0=bottom,1=top). */
    private static Vec3 eastHit(BlockPos abs, double vy) {
        return new Vec3(abs.getX() + 1.0, abs.getY() + vy, abs.getZ() + 0.5);
    }

    /**
     * Hit on the EAST face at the VISIBLE band of a lowered block: its visible body is shifted by
     * {@code sourceDy}, so the honest offset raycast reports hit Y at {@code abs.y + sourceDy + vy} (a full
     * cell below the logical cell for a -1.0 block). This is what crosshair targeting now feeds placement;
     * the legacy {@link #eastHit} fed the LOGICAL band, which is why the logical-band tests were green while
     * live (honest-band) placement floated.
     */
    private static Vec3 eastHitOffset(BlockPos abs, double sourceDy, double vy) {
        return new Vec3(abs.getX() + 1.0, abs.getY() + sourceDy + vy, abs.getZ() + 0.5);
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
     * the maintainer WYSIWYG (2026-06-19, FIXED): clicking the SIDE face of a -0.5 lowered block to place a slab,
     * even when the placement cell has SOLID GROUND below it, must FOLLOW to the lowered surface (-0.5) —
     * it lands where the crosshair clicked, NOT frozen flat 0.5 above (the maintainer's "lands 0.5 high" bug). The
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

    /**
     * P26-9 RED: with honest (offset-raycast) targeting, a slab placed by clicking the EAST face of a -1.0
     * COMPOUND owner must land on the EAST (clicked) side — NEVER flip to the WEST (opposite) side. The legacy
     * {@code replaceabilityGuard} "visible lane" walk to {@code originalSide.getOpposite()} existed only to
     * chase the old post-hoc retargeter's lie about the visible owner; once compound markers/lanes exist it
     * flipped a side placement to the wrong side (the maintainer's live build: aim east, slab lands west). The compound
     * markers here reproduce a genuine built compound owner (the bare-column tests above reject the remap and
     * place via vanilla, so they never exercised the flip).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnSlabBesideCompoundOwnerPlacesClickedSideNotOpposite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildCompoundMinusOne(helper);                       // top compound stone at (2,5,2) reads -1.0
        BlockPos owner = helper.absolutePos(new BlockPos(2, 5, 2));
        // Mark it a genuine compound owner (as a built compound stack is live) so the compound side remap engages.
        SlabAnchorAttachment.addAnchor(level, owner, level.getBlockState(owner));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(level, owner, level.getBlockState(owner));

        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 6, 2)));
        placeSlabVia(player, owner, Direction.EAST, eastHit(owner, 0.25));   // aim the EAST face, lower half

        BlockPos eastCell = helper.absolutePos(new BlockPos(3, 5, 2));       // clicked side
        BlockPos westCell = helper.absolutePos(new BlockPos(1, 5, 2));       // opposite side
        boolean east = level.getBlockState(eastCell).getBlock() instanceof SlabBlock;
        boolean west = level.getBlockState(westCell).getBlock() instanceof SlabBlock;
        log("p26_compound_side_clicked_east", level, eastCell);
        if (west) {
            throw helper.assertionException(new BlockPos(1, 5, 2),
                    "P26-9 useOn: slab placed on the OPPOSITE (west) side of the clicked east face"
                            + " (legacy replaceabilityGuard flip must not fire under honest targeting)");
        }
        if (!east) {
            throw helper.assertionException(new BlockPos(3, 5, 2),
                    "P26-9 useOn: slab clicking the east face of a -1.0 compound owner must land on the EAST (clicked) side");
        }
        helper.succeed();
    }

    /**
     * P26-10 RED: honest-band repro of the live "floating / misplaced slab" against a -1.0 compound. The
     * crosshair raycast now reports the hit at the VISIBLE band (a full cell below logical), so a slab placed
     * against the EAST face of a -1.0 column must STILL land at the east logical cell (3,5,2) lowered to -1.0
     * (flush beside the body) — not float in a wrong cell. Mirrors the green logical-band tests but with the
     * honest hit Y. Geometric (un-anchored) compound owner.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnSlabBesideCompoundHonestBandLowerHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildCompoundMinusOne(helper);
        BlockPos owner = helper.absolutePos(new BlockPos(2, 5, 2));
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 6, 2)));
        BlockPos placed = placeSlabViaAndFindChangedSlab(helper, player, owner, Direction.EAST,
                eastHitOffset(owner, -1.0, 0.25));            // honest visible lower-half hit (= owner.y-0.75)
        log("p26_honest_band_lower", level, placed);
        BlockPos eastCell = helper.absolutePos(new BlockPos(3, 5, 2));
        if (!placed.equals(eastCell)) {
            throw helper.assertionException(new BlockPos(3, 5, 2),
                    "P26-10 honest-band: slab landed at " + helper.relativePos(placed).toShortString()
                            + " not the east cell (3,5,2)");
        }
        assertSlabDy(helper, level, eastCell, new BlockPos(3, 5, 2), -1.0,
                "P26-10 honest-band lower-half: slab beside a -1.0 compound must land -1.0 (flush, no gap)");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnSlabBesideCompoundHonestBandUpperHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildCompoundMinusOne(helper);
        BlockPos owner = helper.absolutePos(new BlockPos(2, 5, 2));
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 6, 2)));
        BlockPos placed = placeSlabViaAndFindChangedSlab(helper, player, owner, Direction.EAST,
                eastHitOffset(owner, -1.0, 0.75));            // honest visible upper-half hit (= owner.y-0.25)
        log("p26_honest_band_upper", level, placed);
        BlockPos eastCell = helper.absolutePos(new BlockPos(3, 5, 2));
        if (!placed.equals(eastCell)) {
            throw helper.assertionException(new BlockPos(3, 5, 2),
                    "P26-10 honest-band: slab landed at " + helper.relativePos(placed).toShortString()
                            + " not the east cell (3,5,2)");
        }
        assertSlabDy(helper, level, eastCell, new BlockPos(3, 5, 2), -1.0,
                "P26-10 honest-band upper-half: slab beside a -1.0 compound must land -1.0 (flush, no gap)");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnSlabBesideCompoundAnchoredHonestBand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildCompoundMinusOne(helper);
        BlockPos owner = helper.absolutePos(new BlockPos(2, 5, 2));
        SlabAnchorAttachment.addAnchor(level, owner, level.getBlockState(owner));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(level, owner, level.getBlockState(owner));
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 6, 2)));
        BlockPos placed = placeSlabViaAndFindChangedSlab(helper, player, owner, Direction.EAST,
                eastHitOffset(owner, -1.0, 0.25));            // honest visible band, compound-anchored owner
        log("p26_honest_band_anchored", level, placed);
        BlockPos eastCell = helper.absolutePos(new BlockPos(3, 5, 2));
        if (!placed.equals(eastCell)) {
            throw helper.assertionException(new BlockPos(3, 5, 2),
                    "P26-10 honest-band (anchored): slab landed at " + helper.relativePos(placed).toShortString()
                            + " not the east cell (3,5,2)");
        }
        assertSlabDy(helper, level, eastCell, new BlockPos(3, 5, 2), -1.0,
                "P26-10 honest-band (anchored): slab beside a -1.0 compound must land -1.0 (flush)");
        helper.succeed();
    }

    /**
     * P26-12 RED: cantilever-against-cantilever. Slab A is placed beside a lowered block (anchored -0.5).
     * Placing slab B by clicking A's lowered side face must ALSO land -0.5 (WYSIWYG follow) and must NOT pop
     * A back up to 0.0. the maintainer's live report: placing B sent BOTH to vanilla 0.0 (0.5 high). Drives the real
     * useOn path on the SERVER (the authoritative dy the client now mirrors). Honest (visible-band) hit.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnCantileverSlabAgainstCantileverSlabBothStayMinusHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 2, 2), bottomSlab());
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE.defaultBlockState());   // lowered -0.5 full block
        BlockPos lowered = helper.absolutePos(new BlockPos(2, 3, 2));
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 5, 2)));

        // Slab A: cantilever beside the lowered full block (air below) -> -0.5 anchored.
        BlockPos a = placeSlabViaAndFindChangedSlab(helper, player, lowered, Direction.EAST, eastHit(lowered, 0.75));
        assertSlabDy(helper, level, a, new BlockPos(3, 3, 2), -0.5, "P26-12: first cantilever slab A must be -0.5");

        // Slab B: cantilever beside A by clicking A's lowered EAST face (honest visible band: A is at -0.5).
        BlockPos b = placeSlabViaAndFindChangedSlab(helper, player, a, Direction.EAST, eastHitOffset(a, -0.5, 0.75));
        log("p26_cantilever_vs_cantilever_B", level, b);
        assertSlabDy(helper, level, b, new BlockPos(4, 3, 2), -0.5,
                "P26-12: second cantilever slab B (clicking A's lowered face) must FOLLOW to -0.5, not freeze flat");
        // A must NOT have popped up.
        assertSlabDy(helper, level, a, new BlockPos(3, 3, 2), -0.5,
                "P26-12: placing B must NOT pop the existing cantilever A back up to 0.0 (NEVER-POP)");
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

    /**
     * REGRESSION (the maintainer live report, 2026-08-04) — {@code RELEASE_SANITY_CHECKLIST.md} §G row G2a:
     * two {@code oak_fence} on two side-by-side bottom slabs at the SAME height MUST connect. In game
     * they did not: both arms were cut.
     *
     * <p>Why the sibling above ({@link #useOnFenceClickingLoweredFenceOverAirFollowsToMinusHalf})
     * could not catch it: the gametest JVM pins {@code slabbed.frozenDy=false} (build.gradle, the
     * frozen-OFF compatibility floor), and the whole bug only exists when the SHIPPED default
     * {@code FROZEN_DY_ENABLED=true} is in force. Under frozen-ON a connector's height read is the
     * stored placement fact, and a real player placement publishes that fact only AFTER vanilla's
     * {@code place()} returns — i.e. after the new fence's own {@code getStateForPlacement} and after
     * the existing neighbour's {@code setBlock}-driven {@code updateShape}. At both of those decision
     * points the new fence had no fact and read stable-flat {@code 0.0} while its neighbour read its
     * true {@code -0.5}, so the stepped-connection rule fired on BOTH ends and the cut arms were baked
     * into the persisted blockstate. This test therefore forces frozen-ON locally, exactly as
     * {@code UpwardContinuationValidationTest} / {@code NeighborUpdateInvarianceTest} do, and drives
     * BOTH fences through the real {@code useOn} path so both actually earn a published fact.
     *
     * <p>The fix is a timing correction only — {@code ConnectorPlacementSettle} re-runs vanilla's own
     * arm derivation once the fact exists. The complementary break rule (§G row G1 / S6: fences must
     * NOT connect across a slab-height step) is unaffected, because after the settle the two cells
     * still read genuinely different frozen heights; that direction is covered by
     * {@code Slabbed2612ConnectorSurvivalTest}'s stepped fence/pane/wall rows.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnTwoFencesOnAdjacentBottomSlabsConnectUnderFrozenDy(GameTestHelper helper) {
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            ServerLevel level = helper.getLevel();
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE.defaultBlockState());
            helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE.defaultBlockState());
            helper.setBlock(new BlockPos(2, 2, 2), bottomSlab());
            helper.setBlock(new BlockPos(3, 2, 2), bottomSlab());
            BlockPos westSlab = helper.absolutePos(new BlockPos(2, 2, 2));
            BlockPos eastSlab = helper.absolutePos(new BlockPos(3, 2, 2));
            Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(2, 5, 2)));

            BlockPos westFence = placeBlockVia(player, westSlab, Direction.UP, upHit(westSlab), Blocks.OAK_FENCE);
            log("g2a_west_fence_on_bottom_slab", level, westFence);
            assertBlockDy(helper, level, westFence, new BlockPos(2, 3, 2), Blocks.OAK_FENCE, -0.5,
                    "G2a premise: the first fence placed on a bottom slab freezes at -0.5");

            BlockPos eastFence = placeBlockVia(player, eastSlab, Direction.UP, upHit(eastSlab), Blocks.OAK_FENCE);
            log("g2a_east_fence_on_bottom_slab", level, eastFence);
            assertBlockDy(helper, level, eastFence, new BlockPos(3, 3, 2), Blocks.OAK_FENCE, -0.5,
                    "G2a premise: the second fence placed on the adjacent bottom slab also freezes at -0.5");

            BlockState eastState = level.getBlockState(eastFence);
            BlockState westState = level.getBlockState(westFence);
            if (!eastState.getValue(CrossCollisionBlock.WEST)) {
                throw helper.assertionException(new BlockPos(3, 3, 2),
                        "G2a: the newly placed fence must connect WEST to the same-height fence beside it — "
                        + "its arm was decided before its own placement height was published");
            }
            if (!westState.getValue(CrossCollisionBlock.EAST)) {
                throw helper.assertionException(new BlockPos(2, 3, 2),
                        "G2a: the already-placed fence must connect EAST to the new same-height fence — "
                        + "its updateShape ran mid-placement, while the new cell still had no published height");
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
        helper.succeed();
    }

    /**
     * REGRESSION (shipped-config only): the placement-time attachment classifiers must see the REAL
     * height of the placement they are classifying, not the pre-publication stand-in.
     *
     * <p>{@code SlabAnchorAttachment.freezeLoweredOnPlace} runs at {@code Block.setPlacedBy} HEAD,
     * which is strictly BEFORE C3 publishes the placement fact for that cell (C3 publishes after
     * {@code BlockItem.place} returns). Under the SHIPPED default {@code FROZEN_DY_ENABLED=true} the
     * public height read at a fact-less cell is the stable-flat {@code 0.0}, so every genuinely
     * lowered placement was filed as {@code FROZEN_FLAT} instead of as an anchor. Height itself stayed
     * correct (C3's store is authoritative for height), but {@code isAnchored}/{@code isFrozenFlat}
     * and the carrier/marker family are consumed by never-pop, carrier qualification and the compound
     * markers — so the WRONG family was recorded for the whole shipped configuration.
     *
     * <p>The gametest JVM pins {@code slabbed.frozenDy=false} (build.gradle, the frozen-OFF
     * compatibility floor), where the two height entries are the same code and the bug cannot exist —
     * hence the local force, exactly as the G2a connector scene above does.
     *
     * <p>Subject choice: the full block on a bottom slab pins the stored height, but its anchor is
     * ALSO reachable through {@code qualifiesForAnchor}'s pure-state {@code hasBottomSlabBelow} lane,
     * so it cannot discriminate. The SLAB on the lowered full block can: slabs are rejected by
     * {@code isOrdinaryFullBlockAnchorCandidate}, so the classifier's own height branch is the only
     * thing that can anchor it. Both are driven through the real {@code useOn} path so every cell in
     * the stack earns a published fact, the way a player's would.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnLoweredPlacementIsClassifiedAnchoredUnderFrozenDy(GameTestHelper helper) {
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            ServerLevel level = helper.getLevel();
            helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE.defaultBlockState());
            BlockPos ground = helper.absolutePos(new BlockPos(2, 1, 2));
            Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(2, 5, 2)));

            BlockPos slab = placeBlockVia(player, ground, Direction.UP, upHit(ground), Blocks.STONE_SLAB);
            assertStoredDy(helper, level, slab, new BlockPos(2, 2, 2), 0.0,
                    "premise: the bottom slab placed on flush ground stores 0.0");

            // (a) the full block on that bottom slab lands and STORES -0.5 ...
            BlockPos fullBlock = placeBlockVia(player, slab, Direction.UP, upHit(slab), Blocks.STONE);
            assertBlockDy(helper, level, fullBlock, new BlockPos(2, 3, 2), Blocks.STONE, -0.5,
                    "premise: the full block placed on a bottom slab is drawn at -0.5");
            assertStoredDy(helper, level, fullBlock, new BlockPos(2, 3, 2), -0.5,
                    "(a): the full block on a bottom slab stores -0.5");
            // ... and (b) is filed as ANCHORED, never as frozen-flat.
            assertAnchoredNotFrozenFlat(helper, level, fullBlock, new BlockPos(2, 3, 2),
                    "(b): the full block placed on a bottom slab");

            // The discriminating subject: a SLAB on that lowered full block. Nothing but the
            // classifier's own height branch can anchor this one.
            BlockPos rider = placeBlockVia(player, fullBlock, Direction.UP, upHit(fullBlock),
                    Blocks.STONE_SLAB);
            log("frozen_slab_on_lowered_full_block", level, rider);
            if (!(level.getBlockState(rider).getBlock() instanceof SlabBlock)) {
                throw helper.assertionException(new BlockPos(2, 4, 2),
                        "premise: useOn must place a slab on the lowered full block, got "
                                + level.getBlockState(rider).getBlock());
            }
            assertStoredDy(helper, level, rider, new BlockPos(2, 4, 2), -0.5,
                    "(a): the slab riding the lowered full block stores -0.5");
            assertAnchoredNotFrozenFlat(helper, level, rider, new BlockPos(2, 4, 2),
                    "(b): the slab placed on a lowered full block");
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
        helper.succeed();
    }

    /** Asserts the C3-stored placement height at {@code abs} (the frozen-ON render authority). */
    private static void assertStoredDy(GameTestHelper helper, ServerLevel level, BlockPos abs,
                                       BlockPos relForErr, double expectedDy, String what) {
        double stored = SlabAnchorAttachment.storedPlacementDy(level, abs);
        if (Double.isNaN(stored)) {
            throw helper.assertionException(relForErr, what + ": no placement height was stored at all");
        }
        if (Math.abs(stored - expectedDy) > EPS) {
            throw helper.assertionException(relForErr,
                    what + ": expected stored dy=" + expectedDy + " got " + stored);
        }
    }

    /** The classifier saw the real height: the cell is anchored and is NOT filed flat. */
    private static void assertAnchoredNotFrozenFlat(GameTestHelper helper, ServerLevel level, BlockPos abs,
                                                    BlockPos relForErr, String what) {
        if (SlabAnchorAttachment.isFrozenFlat(level, abs)) {
            throw helper.assertionException(relForErr, what + " must NOT be filed FROZEN_FLAT — the "
                    + "classifier read the pre-publication stand-in instead of its real placement height");
        }
        if (!SlabAnchorAttachment.isAnchored(level, abs)) {
            throw helper.assertionException(relForErr, what + " must be ANCHORED — a lowered placement "
                    + "records an anchor so a later support removal cannot pop it");
        }
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

    // ── A7 (RE-SPEC'D — GOES C2, design §5, the maintainer-ruled D1): a slab placed via useOn on the visible top
    //         of a compound -1.0 owner lands FLUSH at -1.0. ──────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnSlabOnTopOfCompoundFollowsToMinusOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildCompoundMinusOne(helper);                       // top compound stone at (2,5,2) = -1.0
        BlockPos top = helper.absolutePos(new BlockPos(2, 5, 2));
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(2, 7, 2)));

        BlockPos placed = placeSlabVia(player, top, Direction.UP, upHit(top));
        log("a7_slab_on_compound_top", level, placed);
        // GOES unified rule (design §1.3.1 + §5): the placement lands on the owner's VISIBLE top. Over a
        // -1.0 full-block owner that visible top coincides with the owner's grid floor, so the slab seats
        // FLUSH at -1.0. This is the LANDING (the value the resolver freezes at placement) — asserted on
        // the STORED dy, the authority under the shipping frozen-ON config.
        //
        // PRE-RESPEC HISTORY: this test asserted the LIVE-lane -0.5 ("single-step on-top follow") and its
        // old comment ("locked to the observed -0.5") was exactly the A7 prop-up the design names (§5) —
        // an aim-blind read of a fixture whose hit the honest raycast never produces. Under frozen-OFF the
        // live lane STILL reads -0.5 at exactly -1.0 (the deep-rest threshold is unchanged; SlabSupport
        // deep-rest comment); that frozen-OFF -0.5 is a DISCLOSED divergence, not a regression.
        double stored = SlabAnchorAttachment.storedPlacementDy(level, placed);
        BlockState s = level.getBlockState(placed);
        if (!(s.getBlock() instanceof SlabBlock)) {
            throw helper.assertionException(new BlockPos(2, 6, 2),
                    "A7 useOn: no slab placed (got " + s.getBlock() + ")");
        }
        if (Math.abs(stored + 1.0) > EPS) {
            throw helper.assertionException(new BlockPos(2, 6, 2),
                    "A7 useOn (re-spec, D1): a slab on the visible top of a -1.0 owner must LAND flush -1.0; "
                    + "stored=" + stored);
        }
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

    /**
     * the maintainer live RED (2026-06-21): clicking the visible UP face of an anchored lowered TOP slab must stack
     * a new bottom slab on that lowered surface. It must not reinterpret the UP hit as a DOWN merge and
     * fill the lower half underneath the clicked slab.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnSlabOnTopOfLoweredTopSlabPlacesAboveVisibleFace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 2, 2), bottomSlab());
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE.defaultBlockState());
        BlockPos loweredSource = helper.absolutePos(new BlockPos(2, 3, 2));

        Player sourcePlayer = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 4, 2)));
        BlockPos support = placeSlabVia(sourcePlayer, loweredSource, Direction.EAST, eastHit(loweredSource, 0.75));
        assertSlabDy(helper, level, support, new BlockPos(3, 3, 2), -0.5,
                "SETUP: side-placed top slab support should be lowered before placing on its visible UP face");
        if (level.getBlockState(support).getValue(SlabBlock.TYPE) != SlabType.TOP) {
            throw helper.assertionException(new BlockPos(3, 3, 2),
                    "SETUP: upper-half side placement should create a TOP slab support");
        }

        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 5, 2)));
        BlockPos placed = placeSlabViaAndFindChangedSlab(
                helper,
                player,
                support,
                Direction.UP,
                loweredTopSlabVisibleUpHit(support, 0.5, 0.5));
        log("wysiwyg_slab_on_lowered_top_slab_up_face", level, placed);

        if (!placed.equals(support.above())) {
            throw helper.assertionException(new BlockPos(3, 4, 2),
                    "WYSIWYG: UP-face placement on a lowered top slab must choose the cell above, got "
                            + placed.toShortString());
        }
        assertSlabDy(helper, level, placed, new BlockPos(3, 4, 2), -0.5,
                "WYSIWYG: slab placed on the visible top of a lowered top slab must sit on that lowered surface");
        if (level.getBlockState(support).getValue(SlabBlock.TYPE) != SlabType.TOP) {
            throw helper.assertionException(new BlockPos(3, 3, 2),
                    "WYSIWYG: clicked lowered top slab must not merge into a double slab underneath");
        }
        helper.succeed();
    }

    // ── RC4 check: a slab placed on TOP of a cantilever-lowered block. The WYSIWYG audit feared this
    //         reads 0.0 (column walk terminating at the air under the cantilever). MEASURED: it reads
    //         -0.5 — the on-top slab DOES follow the anchored cantilever down and rests flush on it. So
    //         this configuration is correct, not the feared gap. (RC4's full-block-on-edge case may still
    //         differ; this pins the slab-on-anchored-cantilever case as good.)

    /**
     * L8 REAL-PLACEMENT COVERAGE (cross-phase-review Finding 3, correcting L8 f70eec96): the L8 fix's
     * HEADLINE layer was widening {@code BlockItemPlacementIntentMixin}'s below-support placement gate so
     * a slab placed VERTICALLY on a lowered TOP/DOUBLE slab support actually reaches
     * {@code updatePersistentLoweredSlabCarrier} from real player placement — but the only L8 test
     * ({@code SlabOnSlabVerticalAnchorTest}) calls {@code updatePersistentLoweredSlabCarrier} DIRECTLY,
     * bypassing the mixin gate entirely, so a regression in the gate would not be caught by anything in
     * the suite. This test drives the upper slab through the REAL {@code useOn}/{@code BlockItem.place}
     * path ({@code placeSlabViaAndFindChangedSlab}), then breaks the support, then asserts the upper slab
     * stays at its lowered dy (does NOT pop back to flush) — the actual live-reported "pop upon breaking
     * at the end". Mutation-proven RED against a revert of just the mixin-gate widening.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnSlabOnLoweredDoubleSlabSupportPersistsAndDoesNotPopWhenSupportBroken(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Build a genuinely-lowered + PERSISTED DOUBLE support the way real gameplay does (mirrors
        // SlabOnSlabVerticalAnchorTest's scene): anchored dirt -> persisted lowered BOTTOM slab ->
        // DOUBLE support beside it inheriting via the horizontal slab side-lane.
        helper.setBlock(new BlockPos(2, 1, 2),
                Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.DIRT.defaultBlockState());
        SlabAnchorAttachment.addAnchor(level, dirt, level.getBlockState(dirt));

        BlockPos loweredBottom = helper.absolutePos(new BlockPos(2, 3, 2));
        helper.setBlock(new BlockPos(2, 3, 2),
                Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(level, loweredBottom, level.getBlockState(loweredBottom));

        BlockPos support = helper.absolutePos(new BlockPos(3, 3, 2)); // DOUBLE support beside the lowered slab
        helper.setBlock(new BlockPos(3, 3, 2),
                Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(level, support, level.getBlockState(support));
        double supportDy = SlabSupport.getYOffset(level, support, level.getBlockState(support));
        if (Math.abs(supportDy + 0.5) > EPS) {
            throw helper.assertionException(new BlockPos(3, 3, 2),
                    "SETUP: DOUBLE support beside the lowered slab must render -0.5, got " + supportDy);
        }
        if (!SlabSupport.isLoweredTopLikeSlabCarrier(level, support, level.getBlockState(support))) {
            throw helper.assertionException(new BlockPos(3, 3, 2),
                    "SETUP: lowered DOUBLE support must be a top-like carrier for the upper slab to inherit");
        }

        // THE REAL PATH: place the upper slab by clicking the support's UP face — this flows through
        // BlockItem.useOn -> BlockItem.place -> the widened below-support gate -> updatePersistentLoweredSlabCarrier.
        Player player = mockPlayerNear(helper, helper.absolutePos(new BlockPos(3, 5, 2)));
        BlockPos upper = placeSlabViaAndFindChangedSlab(helper, player, support, Direction.UP, upHit(support));
        if (!upper.equals(support.above())) {
            throw helper.assertionException(helper.relativePos(support.above()),
                    "L8 useOn: UP-face placement on the lowered DOUBLE support must land in the cell above, got "
                            + helper.relativePos(upper).toShortString());
        }
        assertSlabDy(helper, level, upper, helper.relativePos(upper), -0.5,
                "L8 useOn: slab placed on the lowered DOUBLE support must render -0.5 (rests flush on the recessed top)");
        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(level, upper, level.getBlockState(upper))) {
            throw helper.assertionException(helper.relativePos(upper),
                    "L8 useOn (THE FIX): a slab placed via REAL useOn on a lowered TOP/DOUBLE support must PERSIST a "
                            + "lowered-slab-carrier anchor through the placement mixin gate — the L8 headline layer. "
                            + "Without the mixin-gate widening this placement never reaches updatePersistentLoweredSlabCarrier.");
        }

        // Break the support — the upper slab must NOT pop back to flush.
        helper.setBlock(new BlockPos(3, 3, 2), Blocks.AIR.defaultBlockState());
        double upperDyAfter = SlabSupport.getYOffset(level, upper, level.getBlockState(upper));
        log("l8_useon_upper_after_support_broken", level, upper);
        if (Math.abs(upperDyAfter + 0.5) > EPS) {
            throw helper.assertionException(helper.relativePos(upper),
                    "L8 useOn NEVER-POP: the useOn-placed upper slab popped from -0.5 to " + upperDyAfter
                            + " after its support was broken (the live-reported 'pop upon breaking at the end')");
        }
        helper.succeed();
    }

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
