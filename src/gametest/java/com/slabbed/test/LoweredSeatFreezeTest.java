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
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A SEAT IS A FACE, NOT A VOLUME (LAW 2 corollary; upstream ruling of record 2026-08-06): what a
 * placement freezes on top of a lowered support is decided by where that support's TOP FACE actually
 * is — never by the support's block class. A lowered TOP or DOUBLE slab draws its top face at exactly
 * its own cell top, so a block placed on one seats at the support's real surface, exactly as it would
 * on a lowered full block.
 *
 * <p><b>The upstream defect this guards against</b> (found live there, class-reject number NINE of
 * that campaign): a support lane opened with {@code instanceof SlabBlock}, a CLASS test standing in
 * for the top-face question, so everything resting on a lowered TOP/DOUBLE slab took a {@code -0.5}
 * follower floor — and because that read fed placement, the floor was the number LAW 1 froze. The
 * store faithfully preserved a wrong height.
 *
 * <p><b>What these rows measure on this line.</b> Real {@code useOn} placements onto authored lowered
 * supports, asserting the STORED fact of the placed cell — the number LAW 1 freezes — not a live
 * read. The BOTTOM-slab row is the control (upstream's controls were correct at {@code -1.0}); the
 * TOP and DOUBLE rows are the ones a class-shaped seat test fails.
 *
 * <p>Assertions read {@link SlabAnchorAttachment#storedPlacementDy} directly so the rows are valid in
 * both gametest configurations: frozen-OFF (this JVM's pin) changes what {@code getYOffset} answers,
 * but the minted fact is the same either way.
 */
public final class LoweredSeatFreezeTest {

    private static final double EPS = 1.0e-6;

    private static BlockState slab(SlabType type) {
        return Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static void placeStoneOnTop(GameTestHelper h, BlockPos clicked) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(Blocks.STONE.asItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked).add(0.0, 0.5, 0.0);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, clicked, false)));
    }

    private void seatRow(GameTestHelper helper, SlabType supportType, double supportDy, double expectedPlacedDy) {
        ServerLevel level = helper.getLevel();
        BlockPos supportRel = new BlockPos(2, 2, 2);
        BlockPos support = helper.absolutePos(supportRel);
        BlockPos placed = support.above();

        helper.setBlock(supportRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(supportRel, slab(supportType));
        // Author the support as a real lowered placement would have: the fact is what visibleOwnerDy
        // reads at capture time, in either frozen configuration.
        SlabAnchorAttachment.writePlacementDy(level, support, supportDy);

        placeStoneOnTop(helper, support);

        BlockState placedState = level.getBlockState(placed);
        if (!placedState.is(Blocks.STONE)) {
            throw helper.assertionException(helper.relativePos(placed),
                    "premise: the useOn placement must fill the cell above the " + supportType
                            + " slab, got " + placedState);
        }
        double stored = SlabAnchorAttachment.storedPlacementDy(level, placed);
        if (!Double.isFinite(stored)) {
            throw helper.assertionException(helper.relativePos(placed),
                    "premise: the placement must mint a stored fact for the placed stone; none exists");
        }
        if (Math.abs(stored - expectedPlacedDy) > EPS) {
            throw helper.assertionException(helper.relativePos(placed),
                    "stone on a lowered " + supportType + " slab (support dy " + supportDy
                            + ") must FREEZE at the support's real top face, expected " + expectedPlacedDy
                            + " got " + stored + " — a wrong number here is a class-shaped seat test "
                            + "feeding placement, and LAW 1 then preserves the wrong height forever");
        }
        helper.succeed();
    }

    /** The control: upstream's BOTTOM-slab supports were correct. Lowered bottom slab top sits a full
     *  cell below the placed cell's base, so the stone freezes at -1.0. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stoneOnLoweredBottomSlabFreezesAtItsTop(GameTestHelper helper) {
        seatRow(helper, SlabType.BOTTOM, -0.5, -1.0);
    }

    /** The upstream failure case: a lowered TOP slab's top face is at its own cell top, so the stone
     *  freezes at the slab's own dy — not a follower floor. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stoneOnLoweredTopSlabFreezesAtItsTop(GameTestHelper helper) {
        seatRow(helper, SlabType.TOP, -0.5, -0.5);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stoneOnLoweredDoubleSlabFreezesAtItsTop(GameTestHelper helper) {
        seatRow(helper, SlabType.DOUBLE, -0.5, -0.5);
    }

    /**
     * THE DISCRIMINATING PAIR — read this before trusting the three rows above. At support dy
     * {@code -0.5} the correct answer and a hypothetical {@code -0.5} follower floor COINCIDE, so
     * those rows cannot tell a face-shaped seat from a class-reject-with-floor. Upstream's live
     * defect was exactly this shape at depth: a DOUBLE slab at {@code -1.0} froze its rider at
     * {@code -0.5}. These rows author the support at {@code -1.0}, where floor and truth diverge —
     * a {@code -0.5} here is the class-shaped bug, reproduced.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stoneOnDeepLoweredTopSlabDoesNotTakeAFloor(GameTestHelper helper) {
        seatRow(helper, SlabType.TOP, -1.0, -1.0);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stoneOnDeepLoweredDoubleSlabDoesNotTakeAFloor(GameTestHelper helper) {
        seatRow(helper, SlabType.DOUBLE, -1.0, -1.0);
    }
}
