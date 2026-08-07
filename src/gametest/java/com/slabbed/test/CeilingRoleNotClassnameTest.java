package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.Attachment;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Lane C — {@code SlabSupport.isCeilingAttached} asks whether a block IS hanging, not whether its
 * TYPE could be.
 *
 * <p>The predicate used to be a list of block TYPES: {@code LeverBlock}, {@code ButtonBlock},
 * {@code BellBlock}, {@code PointedDripstoneBlock}, any Y-axis {@code ChainBlock}, any TOP-half
 * {@code TrapdoorBlock}. Every one of those classes has a state that hangs from the ceiling AND a
 * state that stands on the floor, so a floor lever, a floor button, a floor stalagmite and a chain
 * STANDING on a block all matched. Matching meant being denied a height anchor and left deriving
 * height live from the block BELOW — so they popped the instant that block was broken. That was
 * S-2's standing RED, {@code chain_on_lowered_support_ceiling_scenery}: {@code break_directly_below:
 * dy -0.5 -> 0.0}. It is LAW 2's exclude-by-BEHAVIOUR rule, sixth instance this campaign.
 *
 * <p>This class pins BOTH halves, because the half that must not move is the dangerous one:
 * <ol>
 *   <li><b>Floor-mounted subjects now lock</b> — a floor lever, a floor button and a standing chain
 *       on a lowered support anchor at placement and survive their support being broken.</li>
 *   <li><b>Genuine hangers are untouched</b> — a hanging lantern under a lowered block still tracks
 *       it down to -0.5 and still refuses an anchor; a CEILING lever, a CEILING bell, a
 *       down-pointing stalactite, a hung chain and a TOP-half trapdoor under a ceiling are all
 *       still ceiling-attached. This is the project's standing "ceiling hangers attach from above"
 *       ruling, and this change is precisely the one that could have broken it.</li>
 * </ol>
 *
 * <p>Supports are removed with {@code setBlockState(AIR)} rather than {@code breakBlock} for the
 * same reason {@code DecorativeObjectSupportAnchorTest} does it: a floor lever legitimately cannot
 * survive losing its support, and vanilla removing the block is the LAW 1 carve-out, not the pop
 * under test. What is under test is the HEIGHT of a subject that is still there.
 */
public final class CeilingRoleNotClassnameTest {

    private static final double EPS = 1.0e-6;

    // ── 1. FLOOR-MOUNTED SUBJECTS: they hang from nothing, so they must lock ──────────────────

    // A lever on the FLOOR (BLOCK_FACE=FLOOR) matched the old LeverBlock classname entry, lost its
    // anchor, and popped when the slab under it went away. Real-click reachable.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floorLeverOnLoweredSupportAnchorsAndKeepsItsHeight(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 2);
        BlockPos leverPos = slabPos.up();

        w.setBlockState(slabPos, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(leverPos,
                Blocks.LEVER.getDefaultState().with(Properties.BLOCK_FACE, BlockFace.FLOOR),
                Block.NOTIFY_LISTENERS);
        assertRoleIsStanding(ctx, w, leverPos, "a FLOOR lever");

        SlabAnchorAttachment.addAnchor(w, leverPos, w.getBlockState(leverPos));
        assertLocksAndSurvivesSupportRemoval(ctx, w, leverPos, slabPos, "floor lever");
        ctx.complete();
    }

    // A button on the FLOOR matched the old ButtonBlock classname entry the same way.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floorButtonOnLoweredSupportAnchorsAndKeepsItsHeight(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos buttonPos = slabPos.up();

        w.setBlockState(slabPos, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(buttonPos,
                Blocks.STONE_BUTTON.getDefaultState().with(Properties.BLOCK_FACE, BlockFace.FLOOR),
                Block.NOTIFY_LISTENERS);
        assertRoleIsStanding(ctx, w, buttonPos, "a FLOOR button");

        SlabAnchorAttachment.addAnchor(w, buttonPos, w.getBlockState(buttonPos));
        assertLocksAndSurvivesSupportRemoval(ctx, w, buttonPos, slabPos, "floor button");
        ctx.complete();
    }

    // THE S-2 RED, as a standalone unit: a Y-axis chain STANDING on a support with open air above
    // it. Vanilla gives a chain no property that separates hung from standing, so the role answer
    // comes from the world query: nothing above to hang from means it is standing.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void standingChainOnLoweredSupportAnchorsAndKeepsItsHeight(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 4);
        BlockPos chainPos = slabPos.up();

        w.setBlockState(slabPos, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(chainPos, Blocks.IRON_CHAIN.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(w.getBlockState(chainPos.up()).isAir(),
                "premise: the standing chain must have OPEN AIR above it, or it is a hung chain");
        assertRoleIsStanding(ctx, w, chainPos, "a chain standing with air above");

        SlabAnchorAttachment.addAnchor(w, chainPos, w.getBlockState(chainPos));
        assertLocksAndSurvivesSupportRemoval(ctx, w, chainPos, slabPos, "standing chain");
        ctx.complete();
    }

    // A TOP-half trapdoor is hinged to a horizontal neighbour and needs nothing above it. With open
    // air above, it is not hanging from anything and must be allowed to lock.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void topHalfTrapdoorWithAirAboveAnchorsAndKeepsItsHeight(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 5);
        BlockPos trapdoorPos = slabPos.up();

        w.setBlockState(slabPos, Blocks.BIRCH_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(trapdoorPos,
                Blocks.BIRCH_TRAPDOOR.getDefaultState().with(Properties.BLOCK_HALF, BlockHalf.TOP),
                Block.NOTIFY_LISTENERS);
        ctx.assertTrue(w.getBlockState(trapdoorPos.up()).isAir(),
                "premise: the TOP-half trapdoor must have OPEN AIR above it");
        assertRoleIsStanding(ctx, w, trapdoorPos, "a TOP-half trapdoor with air above");

        SlabAnchorAttachment.addAnchor(w, trapdoorPos, w.getBlockState(trapdoorPos));
        assertLocksAndSurvivesSupportRemoval(ctx, w, trapdoorPos, slabPos, "TOP-half trapdoor");
        ctx.complete();
    }

    // A stalagmite points UP and grows off the floor; only the DOWN-pointing stalactite hangs.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void upwardDripstoneIsStandingNotHanging(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 6);
        w.setBlockState(pos,
                Blocks.POINTED_DRIPSTONE.getDefaultState().with(Properties.VERTICAL_DIRECTION, Direction.UP),
                Block.NOTIFY_LISTENERS);
        assertRoleIsStanding(ctx, w, pos, "an UP-pointing stalagmite");
        ctx.complete();
    }

    // ── 2. GENUINE HANGERS: the half that must NOT change ─────────────────────────────────────

    // THE NON-NEGOTIABLE. "Ceiling hangers attach from above" is a standing project ruling: a
    // hanging lantern under a lowered block must follow it DOWN, and must NOT be frozen by an
    // anchor, or it detaches from the block it hangs on. Recipe is the proven cantilever: a bottom
    // slab, anchored dirt on it, stone beside the dirt (anchored, air below, so it reads -0.5).
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingLanternUnderLoweredBlockStillFollowsItDown(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 3, 2);
        BlockPos dirtPos = slabPos.up();
        BlockPos supportPos = dirtPos.east();
        BlockPos lanternPos = supportPos.down();

        loweredCantileveredSupport(ctx, w, slabPos, dirtPos, supportPos);

        w.setBlockState(lanternPos, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true),
                Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.isCeilingAttached(w, lanternPos, w.getBlockState(lanternPos)),
                "a HANGING lantern is an intrinsic hanger and must still read as ceiling-attached "
                        + "in every state — the role rewrite must not have touched it");

        double lanternDy = SlabSupport.getYOffset(w, lanternPos, w.getBlockState(lanternPos));
        ctx.assertTrue(Math.abs(lanternDy + 0.5) <= EPS,
                "NON-NEGOTIABLE: a hanging lantern under a lowered block must follow it down to "
                        + "-0.5, or it detaches from what it hangs on; got " + lanternDy);

        SlabAnchorAttachment.addAnchor(w, lanternPos, w.getBlockState(lanternPos));
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, lanternPos),
                "a hanging lantern must keep tracking its support, never freeze at a placement-time "
                        + "anchor — freezing it is the other way to break the same ruling");
        ctx.complete();
    }

    // The other half of the chain's world query: a chain with a block ABOVE it is a hung chain and
    // must stay ceiling-attached (and anchor-free), exactly as before. This is the classic player
    // build — block, chain, hanging lantern.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hungChainUnderLoweredBlockStaysCeilingAttached(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos slabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 3, 4);
        BlockPos dirtPos = slabPos.up();
        BlockPos supportPos = dirtPos.east();
        BlockPos chainPos = supportPos.down();
        BlockPos lanternPos = chainPos.down();

        loweredCantileveredSupport(ctx, w, slabPos, dirtPos, supportPos);

        w.setBlockState(chainPos, Blocks.IRON_CHAIN.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.isCeilingAttached(w, chainPos, w.getBlockState(chainPos)),
                "a chain with a real block above it hangs from that block and must stay "
                        + "ceiling-attached — the world query must answer YES here");
        SlabAnchorAttachment.addAnchor(w, chainPos, w.getBlockState(chainPos));
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(w, chainPos),
                "a hung chain must keep tracking the block it hangs from, never freeze");

        // A second chain BELOW the first is still hung: the walk steps over same-family members and
        // is answered by the run's terminator, not by its first cell.
        w.setBlockState(lanternPos, Blocks.IRON_CHAIN.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.isCeilingAttached(w, lanternPos, w.getBlockState(lanternPos)),
                "the second chain down a hung run must also read as hanging — the run walk must "
                        + "step over the chain above it and find the block that caps the run");
        ctx.complete();
    }

    // A CEILING lever / CEILING bell / DOWN-pointing stalactite must still be ceiling-attached.
    // Proves the fix narrowed these families by STATE and did not simply delete them from the list.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void ceilingMountedStatesAreStillCeilingAttached(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos leverPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 3, 6);
        BlockPos bellPos = leverPos.east();
        BlockPos dripPos = bellPos.east();

        w.setBlockState(leverPos,
                Blocks.LEVER.getDefaultState().with(Properties.BLOCK_FACE, BlockFace.CEILING),
                Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.isCeilingAttached(w, leverPos, w.getBlockState(leverPos)),
                "a CEILING lever genuinely hangs from the block above and must stay ceiling-attached");

        w.setBlockState(bellPos,
                Blocks.BELL.getDefaultState().with(Properties.ATTACHMENT, Attachment.CEILING),
                Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.isCeilingAttached(w, bellPos, w.getBlockState(bellPos)),
                "a CEILING-attached bell genuinely hangs and must stay ceiling-attached");

        w.setBlockState(dripPos,
                Blocks.POINTED_DRIPSTONE.getDefaultState().with(Properties.VERTICAL_DIRECTION, Direction.DOWN),
                Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.isCeilingAttached(w, dripPos, w.getBlockState(dripPos)),
                "a DOWN-pointing stalactite hangs from the ceiling and must stay ceiling-attached");
        ctx.complete();
    }

    /**
     * c611b60f added TOP-half trapdoors to this predicate so a 2x ceiling-trapdoor stack under a
     * top slab participated in the ceiling cascade. That behaviour is state-correct — those
     * trapdoors DO have something above them — and the role rewrite must keep it, so this pins the
     * role for BOTH members of the stack (the lower one is answered by the run's terminator, not by
     * the trapdoor immediately above it).
     *
     * <p>It pins the ROLE and the live consumer, not the old +0.5 magnitude: the reach-up itself is
     * DEPRECATED by the maintainer's 2026-07-03 live ruling (everything hangs flush now —
     * {@code isLoweringTopLikeCeiling} returns false unconditionally), so the dy here is 0.0 today
     * and c611b60f's cascade is dormant by that ruling, not by this change. The consumer still
     * live for a ceiling role is {@code shouldOffset}'s guard: a ceiling-attached subject under a
     * top-like ceiling must not ALSO take a -0.5 from below.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void topHalfTrapdoorStackUnderTopSlabStaysCeilingAttached(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos bottomSlabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(5, 2, 3);
        BlockPos lowerTrapdoorPos = bottomSlabPos.up();
        BlockPos upperTrapdoorPos = lowerTrapdoorPos.up();
        BlockPos topSlabPos = upperTrapdoorPos.up();

        w.setBlockState(bottomSlabPos, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(topSlabPos, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(upperTrapdoorPos,
                Blocks.BIRCH_TRAPDOOR.getDefaultState().with(Properties.BLOCK_HALF, BlockHalf.TOP),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(lowerTrapdoorPos,
                Blocks.BIRCH_TRAPDOOR.getDefaultState().with(Properties.BLOCK_HALF, BlockHalf.TOP),
                Block.NOTIFY_LISTENERS);

        ctx.assertTrue(SlabSupport.isCeilingAttached(w, upperTrapdoorPos, w.getBlockState(upperTrapdoorPos)),
                "the upper TOP-half trapdoor sits under a top slab and must stay ceiling-attached");
        ctx.assertTrue(SlabSupport.isCeilingAttached(w, lowerTrapdoorPos, w.getBlockState(lowerTrapdoorPos)),
                "the lower TOP-half trapdoor of the stack must stay ceiling-attached — the run walk "
                        + "must step over the trapdoor above it (c611b60f's cascade)");

        // The live consumer: the ceiling guard still suppresses the -0.5-from-below for the upper
        // trapdoor even though a bottom slab sits at the base of this column.
        ctx.assertTrue(!SlabSupport.shouldOffset(w, upperTrapdoorPos, w.getBlockState(upperTrapdoorPos)),
                "a ceiling-attached subject directly under a top slab must not ALSO take the -0.5 "
                        + "from below — shouldOffset's ceiling guard is the still-live consumer");
        ctx.complete();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    /** The predicate must answer "not hanging" for this cell. */
    private static void assertRoleIsStanding(TestContext ctx, ServerWorld w, BlockPos pos, String what) {
        ctx.assertTrue(!SlabSupport.isCeilingAttached(w, pos, w.getBlockState(pos)),
                "role, not classname: " + what + " hangs from nothing and must NOT read as "
                        + "ceiling-attached — the old block-TYPE list is what denied it an anchor");
    }

    /**
     * The lane-C contract: the subject reads -0.5, holds a real anchor, and still reads -0.5 after
     * its support below is gone. Without the anchor this is the -0.5 -> 0.0 pop.
     */
    private static void assertLocksAndSurvivesSupportRemoval(
            TestContext ctx, ServerWorld w, BlockPos subject, BlockPos support, String what) {
        double before = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(before + 0.5) <= EPS,
                "premise: the " + what + " resting on a bottom slab should render -0.5, got " + before);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, subject),
                "THE FIX: a " + what + " hangs from nothing, so it must anchor at placement like "
                        + "any other object resting on a lowered support");

        w.setBlockState(support, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        double after = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        ctx.assertTrue(Math.abs(after + 0.5) <= EPS,
                "never-pop violation: the " + what + " popped from -0.5 to " + after
                        + " after its support was removed, though it was never re-placed");
    }

    /**
     * Builds the proven cantilever: bottom slab, anchored dirt above it, and a stone beside the
     * dirt with air below — which reads -0.5 and anchors via the adjacent-lowered-full-block lane.
     * Returns the support position.
     */
    private static BlockPos loweredCantileveredSupport(
            TestContext ctx, ServerWorld w, BlockPos slabPos, BlockPos dirtPos, BlockPos supportPos) {
        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtPos, w.getBlockState(dirtPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, dirtPos),
                "setup: dirt must anchor on the bottom slab");

        w.setBlockState(supportPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, supportPos, w.getBlockState(supportPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, supportPos),
                "setup: the cantilevered stone must anchor via the adjacent-lowered-full-block lane");
        double supportDy = SlabSupport.getYOffset(w, supportPos, w.getBlockState(supportPos));
        ctx.assertTrue(Math.abs(supportDy + 0.5) <= EPS,
                "setup: the cantilevered stone support must itself read -0.5, got " + supportDy);
        return supportPos;
    }
}
