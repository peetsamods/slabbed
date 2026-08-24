package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * THE ROOT AIM IS EVIDENCE ABOUT THE CLICKED CELL, NOT ABOUT WHEREVER THE ITEM DECIDES TO BUILD.
 *
 * <p>An item's own {@code updatePlacementContext} may RELOCATE the placement cell away from the
 * clicked one — vanilla scaffolding walks its column and builds at the far end. The captured root
 * aim still describes the CLICKED cell, so pairing it with the relocated target asks the resolver a
 * question about two unrelated cells and it answers with the distance between them. LAW 1 then
 * freezes that distance as the block's permanent height.
 *
 * <p>These rows measure the STORED fact of the relocated cell through the real item-use path. The
 * discriminator is the OBSERVED relocation — what {@code updatePlacementContext} was handed versus
 * what it returned — and nothing else. <b>It is deliberately NOT "the filled cell is neither the
 * aim's own cell nor its clicked-face neighbour".</b> That geometric form is the one this line
 * REVERTED (see the guard on {@code slabbed$aimForResolve}): it rewrites the aim of an ordinary deep
 * placement, because this line's targeting legitimately seats several steps down a column with no
 * relocation at all. Do not restate it here or reimplement it there.
 *
 * <p>The last row is the SAME item placed WITHOUT relocation onto a lowered support, which must
 * still take its seat from the root aim. Its honest scope: it holds the unrelocated path fixed while
 * the relocated rows move, so the two cannot be collapsed into one unconditional rewrite. It does
 * NOT fail if the substitution is made unconditional — for a placement that never relocates, both
 * aims describe the same cell and agree by construction.
 */
public final class RelocatedPlacementAimTest {

    private static final double EPS = 1.0e-6;

    private static Player scaffoldPlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // Vanilla relocates a TOP-face scaffolding click along the player's facing, so the facing is
        // part of the fixture, not incidental: yaw 0 is SOUTH.
        player.setYRot(0.0f);
        return player;
    }

    private static void useScaffoldingOn(GameTestHelper helper, BlockPos clicked, Direction face) {
        Player player = scaffoldPlayer(helper);
        ItemStack stack = new ItemStack(Blocks.SCAFFOLDING.asItem());
        stack.setCount(16);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked).add(
                face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    /** Builds a flush scaffolding column of {@code height} cells standing on stone. */
    private static BlockPos columnOnStone(GameTestHelper helper, BlockPos baseRel, int height) {
        helper.setBlock(baseRel.below(), Blocks.STONE.defaultBlockState());
        for (int i = 0; i < height; i++) {
            helper.setBlock(baseRel.above(i), Blocks.SCAFFOLDING.defaultBlockState());
        }
        return helper.absolutePos(baseRel);
    }

    private static void assertStored(
            GameTestHelper helper, BlockPos placed, double expected, String what) {
        ServerLevel level = helper.getLevel();
        BlockState state = level.getBlockState(placed);
        if (!state.is(Blocks.SCAFFOLDING)) {
            throw helper.assertionException(helper.relativePos(placed),
                    "premise drift: " + what + " must land scaffolding in this cell, got " + state);
        }
        double stored = SlabAnchorAttachment.storedPlacementDy(level, placed);
        if (!Double.isFinite(stored) || Math.abs(stored - expected) > EPS) {
            throw helper.assertionException(helper.relativePos(placed),
                    what + ": expected the frozen height " + expected + ", got " + stored
                            + " — the root aim describes the CLICKED cell, so pairing it with a "
                            + "relocated target freezes the distance between them.");
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aFlushColumnSideClickStacksFlush(GameTestHelper helper) {
        // GH #65's shape. Side-clicking the BASE of a scaffolding column makes vanilla walk UP the
        // column and build above its top; the aim still points at the base, three cells down.
        BlockPos baseRel = new BlockPos(2, 2, 2);
        BlockPos base = columnOnStone(helper, baseRel, 3);
        useScaffoldingOn(helper, base, Direction.NORTH);
        assertStored(helper, base.above(3), 0.0, "a flush column's side-click stack");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aLoweredColumnSideClickFollowsTheColumnSeat(GameTestHelper helper) {
        // The discriminator against "just force relocated placements to zero": this column carries a
        // recorded seat, and its stack must inherit that seat rather than a constant.
        ServerLevel level = helper.getLevel();
        BlockPos baseRel = new BlockPos(2, 2, 2);
        BlockPos base = columnOnStone(helper, baseRel, 3);
        for (int i = 0; i < 3; i++) {
            SlabAnchorAttachment.writePlacementDy(level, base.above(i), -0.5d);
        }
        useScaffoldingOn(helper, base, Direction.NORTH);
        assertStored(helper, base.above(3), -0.5d, "a lowered column's side-click stack");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aTopFaceClickRelocatesSidewaysAndStaysFlush(GameTestHelper helper) {
        // A different relocation AXIS from the same item: clicking the TOP face walks HORIZONTALLY
        // along the player's facing. The aim's UP face then measures a top plane that has nothing to
        // do with the cell being filled.
        BlockPos baseRel = new BlockPos(2, 2, 2);
        BlockPos base = columnOnStone(helper, baseRel, 1);
        useScaffoldingOn(helper, base, Direction.UP);
        assertStored(helper, base.south(), 0.0, "a top-face click's sideways extension");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aRelocatedPlacementIsNotRefusedByItsOwnPhantomDepth(GameTestHelper helper) {
        // The SECOND consumer of the same stale pairing: the canPlace interpenetration gate resolves
        // the identical aim/cell mismatch, so it judges the placement at a depth the block will never
        // occupy. A sideways extension's phantom seat is +1.0 — one cell ABOVE the destination — so a
        // solid block parked there is the likeliest scene for a phantom depth to veto a legal
        // placement. MEASURED 2026-08-22: it does NOT veto, here or in the column rows. The reason is
        // NOT established and no mechanism for it should be recorded as fact.
        //
        // WHAT THIS ROW DOES AND DOES NOT PROVE: it pins that the placement stays ADMITTED, so it is
        // the tripwire if that ever inverts. It does NOT cover the gate's own substitution — revert
        // that site alone and this row still passes, because it was already admitted before it. The
        // gate substitution is unpinned; see the matching note at its call site.
        // Its counterpart is LandingRuleLawTest TEST 29, which proves the gate still REFUSES a
        // genuine occupancy theft — the two together bound the substitution on both sides.
        BlockPos baseRel = new BlockPos(2, 2, 2);
        BlockPos base = columnOnStone(helper, baseRel, 1);
        helper.setBlock(baseRel.south().above(), Blocks.STONE.defaultBlockState());

        useScaffoldingOn(helper, base, Direction.UP);
        assertStored(helper, base.south(), 0.0,
                "a sideways extension under an occupied cell");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anUnrelocatedPlacementStillSeatsFromTheRootAim(GameTestHelper helper) {
        // THE CONTROL. Same item, no relocation — the clicked cell holds no scaffolding, so vanilla
        // returns the context untouched and the root aim is the only evidence there is. The lowered
        // support's seat must reach the placed cell.
        ServerLevel level = helper.getLevel();
        BlockPos supportRel = new BlockPos(2, 2, 2);
        BlockPos support = helper.absolutePos(supportRel);
        helper.setBlock(supportRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(supportRel, Blocks.STONE.defaultBlockState());
        SlabAnchorAttachment.writePlacementDy(level, support, -0.5d);

        useScaffoldingOn(helper, support, Direction.UP);
        assertStored(helper, support.above(), -0.5d, "an unrelocated placement on a lowered support");
        helper.succeed();
    }
}
