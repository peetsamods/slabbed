package com.slabbed.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Clicking a thin block's OWN hitbox keeps the placement in that block's cell.
 *
 * <p>Vanilla treats a snow layer, a candle, a sea pickle and a pink-petal patch as REPLACEABLE by
 * the right item: aim at the thin block itself and the new piece lands in the SAME cell — dirt
 * swallows the snow, a second candle joins the first. The reported defect (GH #72, #73): with the
 * mod installed that same aim put the piece one cell ABOVE, hitbox and all, while aiming at the
 * support underneath still placed normally. These rows click each thin block at its REAL top (a
 * snow layer's top is an eighth of a block up, not the cell's full-cube face) and require the
 * vanilla same-cell result, on flush ground and on a lowered support.
 *
 * <p>REACH, measured not argued: on this line NO mod code decides the CELL for a vertical click
 * into a replaceable block — the intent lane's clicked-cell checks return early for vertical faces
 * (forcing them to "not replaceable" left every case green), and for a full-cube support the
 * resolver's merge branch and its same-cell replacement branch compute the same seat, so no
 * resolver mutation reddens these rows either. They are a REGRESSION TRIPWIRE for the vanilla
 * same-cell contract plus the merged piece's stored seat on a lowered support, not a pin on a
 * present seam. A future change that relocates the cell or re-mints the seat on this path is what
 * turns them red.
 */
public final class ThinLayerOwnCellPlacementTest {

    /** One case: the thin block already in the cell, the item clicked onto it, the state expected. */
    private record Case(String name, Item thin, Item clicked, Block expectedBlock, String property, int expectedValue) {
    }

    private static final Case[] CASES = {
            new Case("snow layer + dirt", Items.SNOW, Items.DIRT, Blocks.DIRT, null, 0),
            new Case("snow layer + snow", Items.SNOW, Items.SNOW, Blocks.SNOW, "layers", 2),
            new Case("candle + candle", Items.CANDLE, Items.CANDLE, Blocks.CANDLE, "candles", 2),
            new Case("sea pickle + sea pickle", Items.SEA_PICKLE, Items.SEA_PICKLE, Blocks.SEA_PICKLE, "pickles", 2),
            new Case("pink petals + pink petals", Items.PINK_PETALS, Items.PINK_PETALS, Blocks.PINK_PETALS, "flower_amount", 2),
    };

    /** Click {@code clicked}'s {@code face} exactly where that block's own shape ends on that face. */
    private static InteractionResult clickRealFace(ServerLevel level, Player player, ItemStack stack, BlockPos clicked, Direction face) {
        BlockState state = level.getBlockState(clicked);
        double top = state.isAir() ? 1.0d : state.getShape(level, clicked).max(Direction.Axis.Y);
        Vec3 location = face == Direction.UP
                ? Vec3.atBottomCenterOf(clicked).add(0.0d, top, 0.0d)
                : Vec3.atCenterOf(clicked).add(face.getStepX() * 0.5d, face.getStepY() * 0.5d, face.getStepZ() * 0.5d);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(location, face, clicked, false)));
    }

    private static int intProperty(BlockState state, String name) {
        return state.getProperties().stream()
                .filter(p -> p.getName().equals(name))
                .map(p -> ((Number) state.getValue(p)).intValue())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no property " + name + " on " + state));
    }

    /**
     * Runs every case with the thin block sitting on {@code support} (already in the world) and
     * requires the click on the thin block to land in the thin block's cell.
     */
    private static void runCases(GameTestHelper helper, java.util.function.Function<Integer, BlockPos> supportAt, String scene) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < CASES.length; i++) {
            Case c = CASES[i];
            BlockPos support = supportAt.apply(i);
            BlockPos cell = support.above();
            player.setPos(support.getX() + 0.5d, support.getY() + 1.0d, support.getZ() + 2.5d);
            player.setYRot(180.0f);
            // Premise: the thin block itself lands in the cell above the support when aimed at the support.
            InteractionResult first = clickRealFace(level, player, new ItemStack(c.thin(), 16), support, Direction.UP);
            BlockState thin = level.getBlockState(cell);
            if (!first.consumesAction() || thin.isAir() || thin.is(level.getBlockState(support).getBlock())) {
                throw helper.assertionException(helper.relativePos(cell), scene + " / " + c.name()
                        + ": premise: the thin block must land on the support: result=" + first + " cell=" + thin);
            }
            // The measurement: aim at the thin block's OWN top and place the second item.
            InteractionResult second = clickRealFace(level, player, new ItemStack(c.clicked(), 16), cell, Direction.UP);
            BlockState result = level.getBlockState(cell);
            BlockState above = level.getBlockState(cell.above());
            String report = scene + " / " + c.name() + ": result=" + second + " cell=" + result + " above=" + above;
            if (!second.consumesAction()) {
                throw helper.assertionException(helper.relativePos(cell), "the click on the thin block was refused: " + report);
            }
            if (!above.isAir()) {
                throw helper.assertionException(helper.relativePos(cell.above()),
                        "the piece landed one cell ABOVE the thin block instead of in its cell: " + report);
            }
            if (!result.is(c.expectedBlock())) {
                throw helper.assertionException(helper.relativePos(cell), "wrong block in the thin block's cell: " + report);
            }
            if (c.property() != null && intProperty(result, c.property()) != c.expectedValue()) {
                throw helper.assertionException(helper.relativePos(cell),
                        "the stack did not grow in place (expected " + c.property() + "=" + c.expectedValue() + "): " + report);
            }
            // The merged or replacing piece keeps the cell's seat: the support's drawn top.
            double supportDy = com.slabbed.util.SlabSupport.getYOffset(level, support, level.getBlockState(support));
            double stored = com.slabbed.anchor.SlabAnchorAttachment.storedPlacementDy(level, cell);
            double seat = Double.isFinite(stored) ? stored : 0.0d;
            if (Math.abs(seat - supportDy) > 1.0e-6d) {
                throw helper.assertionException(helper.relativePos(cell),
                        "the piece in the thin block's cell must seat on the support's drawn top (" + supportDy
                                + "), stored " + stored + ": " + report);
            }
        }
    }

    /** Flush ground: every thin block sits on plain dirt (petals refuse stone). Vanilla's own behaviour is the oracle. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void clickingAThinBlockOnFlushGroundPlacesIntoItsOwnCell(GameTestHelper helper) {
        runCases(helper, i -> {
            BlockPos rel = new BlockPos(1 + i * 2, 1, 2);
            helper.setBlock(rel, Blocks.DIRT.defaultBlockState());
            return helper.absolutePos(rel);
        }, "flush");
        helper.succeed();
    }

    /**
     * Lowered support: dirt placed on a bottom slab, so it carries a recorded half-block seat. The
     * thin block on top is the mod's own case; the click on it must still stay in its cell.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void clickingAThinBlockOnALoweredSupportPlacesIntoItsOwnCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player builder = helper.makeMockPlayer(GameType.SURVIVAL);
        runCases(helper, i -> {
            BlockPos rel = new BlockPos(1 + i * 2, 1, 6);
            helper.setBlock(rel, Blocks.STONE.defaultBlockState());
            helper.setBlock(rel.above(), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            BlockPos slab = helper.absolutePos(rel.above());
            builder.setPos(slab.getX() + 0.5d, slab.getY(), slab.getZ() + 2.5d);
            InteractionResult r = clickRealFace(level, builder, new ItemStack(Items.DIRT, 16), slab, Direction.UP);
            BlockPos stone = slab.above();
            if (!r.consumesAction() || !level.getBlockState(stone).is(Blocks.DIRT)) {
                throw helper.assertionException(helper.relativePos(stone), "premise: dirt on the slab: " + r + " " + level.getBlockState(stone));
            }
            double dy = com.slabbed.util.SlabSupport.getYOffset(level, stone, level.getBlockState(stone));
            if (Math.abs(dy + 0.5d) > 1.0e-6d) {
                throw helper.assertionException(helper.relativePos(stone), "premise: the support must be lowered by half a block, dy=" + dy);
            }
            return stone;
        }, "lowered");
        helper.succeed();
    }
}
