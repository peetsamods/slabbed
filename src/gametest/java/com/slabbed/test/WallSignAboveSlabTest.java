package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A wall sign is carried by the WALL it hangs on, never by the floor under its cell (GH #48).
 *
 * <p>The report: a wall sign placed on a wall, in the cell directly above a bottom slab, moved
 * down half a block. A wall sign touches nothing below it - its support is the block behind it -
 * so a slab under its cell is scenery, not support (LAW.md, Law 2: eligibility follows the
 * geometry of the block that actually holds the thing up). Three scenes, each placing a real oak
 * sign on the north face of a wall block through the real {@code useOn} path and reading the seat
 * the game answers for it:
 * <ul>
 *   <li>flush wall, bottom slab under the sign's cell - the reported scene - must read 0;</li>
 *   <li>flush wall, stone under the sign's cell - control - must read 0;</li>
 *   <li>a wall block itself lowered onto a slab - the sign follows its wall, must read -0.5.</li>
 * </ul>
 *
 * <p>REACH on this line, measured from the source of the two lanes involved (both rows are
 * reachable here; do not weaken either expectation to make a red go away):
 * <ul>
 *   <li>Scene A: the capture answers 0 because {@code SlabSupport.placementSeatDy} short-circuits
 *       a horizontal click whose clicked owner reads flush. MUTATION that must redden this row:
 *       delete that flush-owner short circuit. The capture then falls through to
 *       {@code getUnstoredYOffset}, whose column walk starts at {@code pos.below()} and finds the
 *       slab under the sign's cell, and the sign is stored at -0.5.</li>
 *   <li>Scene C: the -0.5 comes from the wall-attached branch of the eligibility walk, which
 *       checks the column under the block the sign is MOUNTED ON. MUTATION that must redden this
 *       row: remove that branch (the {@code WallSignBlock}/{@code WallBannerBlock}/
 *       {@code WallTorchBlock}/{@code WallHangingSignBlock} case) so the sign reads 0.</li>
 * </ul>
 *
 * <p>The seat asserted here is {@code SlabSupport.getYOffset} - the number the game itself reads
 * for the sign. The stored placement fact is printed beside it rather than asserted, so a row that
 * reds says which of the two lanes moved.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class WallSignAboveSlabTest {

    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6d;

    private static double seatOf(ServerLevel world, BlockPos pos) {
        return SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
    }

    private static String storedOf(ServerLevel world, BlockPos pos) {
        double stored = SlabPlacementHeightAttachment.storedOffset(world, pos);
        return Double.isFinite(stored) ? Double.toString(stored) : "none";
    }

    /** A real held-item click on {@code face} of {@code clicked}, at that face's centre. */
    private static InteractionResult useOn(Player player, ItemStack stack, BlockPos clicked, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked)
                .add(face.getStepX() * 0.5d, face.getStepY() * 0.5d, face.getStepZ() * 0.5d);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    /** Places an oak sign on the NORTH face of {@code wall}; returns the sign cell. */
    private static BlockPos hangSign(GameTestHelper ctx, Player player, BlockPos wall) {
        ServerLevel world = ctx.getLevel();
        player.setPos(wall.getX() + 0.5d, wall.getY() - 1.0d, wall.getZ() - 2.5d);
        player.setYRot(0.0f);
        InteractionResult r = useOn(player, new ItemStack(Items.OAK_SIGN, 16), wall, Direction.NORTH);
        BlockPos cell = wall.north();
        BlockState sign = world.getBlockState(cell);
        ctx.assertTrue(r.consumesAction() && sign.getBlock() instanceof WallSignBlock,
                "premise: an oak sign on the wall's north face must hang as a wall sign: result="
                        + r + " cell=" + sign);
        return cell;
    }

    @GameTest(template = TEMPLATE)
    public void aWallSignIsCarriedByItsWallNotTheSlabUnderItsCell(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        Player player = ctx.makeMockSurvivalPlayer();

        // Scene A (the report): flush wall, bottom slab under the sign cell.
        BlockPos wallA = ctx.absolutePos(new BlockPos(1, 3, 6));
        ctx.setBlock(new BlockPos(1, 1, 6), Blocks.STONE.defaultBlockState());
        ctx.setBlock(new BlockPos(1, 2, 6), Blocks.STONE.defaultBlockState());
        ctx.setBlock(new BlockPos(1, 3, 6), Blocks.STONE.defaultBlockState());
        ctx.setBlock(new BlockPos(1, 2, 5), Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        BlockPos signA = hangSign(ctx, player, wallA);

        // Scene B (control): flush wall, stone under the sign cell.
        BlockPos wallB = ctx.absolutePos(new BlockPos(4, 3, 6));
        ctx.setBlock(new BlockPos(4, 1, 6), Blocks.STONE.defaultBlockState());
        ctx.setBlock(new BlockPos(4, 2, 6), Blocks.STONE.defaultBlockState());
        ctx.setBlock(new BlockPos(4, 3, 6), Blocks.STONE.defaultBlockState());
        ctx.setBlock(new BlockPos(4, 2, 5), Blocks.STONE.defaultBlockState());
        BlockPos signB = hangSign(ctx, player, wallB);

        // Scene C (the rule the sign must follow): the wall block itself is lowered onto a slab.
        ctx.setBlock(new BlockPos(7, 1, 6), Blocks.STONE.defaultBlockState());
        ctx.setBlock(new BlockPos(7, 2, 6), Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        BlockPos slabC = ctx.absolutePos(new BlockPos(7, 2, 6));
        // A fresh builder: placing a sign can leave the placing player in a sign-edit session.
        Player builder = ctx.makeMockSurvivalPlayer();
        builder.setPos(slabC.getX() + 0.5d, slabC.getY(), slabC.getZ() + 2.5d);
        // Click the slab's REAL top (y + 0.5), not the cell's full-cube face: a click in the air
        // above a bottom slab is rightly refused by the hit validation.
        ItemStack stone = new ItemStack(Items.STONE, 16);
        builder.setItemInHand(InteractionHand.MAIN_HAND, stone);
        InteractionResult rc = stone.useOn(new UseOnContext(builder, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atBottomCenterOf(slabC).add(0.0d, 0.5d, 0.0d),
                        Direction.UP, slabC, false)));
        BlockPos wallC = slabC.above();
        double wallCDy = SlabSupport.getYOffset(world, wallC, world.getBlockState(wallC));
        // The world state is the premise; the result code is reported only (the placement lane may
        // answer FAIL after placing).
        ctx.assertTrue(world.getBlockState(wallC).is(Blocks.STONE) && Math.abs(wallCDy + 0.5d) <= EPS,
                "premise: the wall block must be lowered onto the slab: " + rc + " dy=" + wallCDy
                        + " wallC=" + world.getBlockState(wallC) + " slabC=" + world.getBlockState(slabC));
        BlockPos signC = hangSign(ctx, player, wallC);

        double a = seatOf(world, signA);
        double b = seatOf(world, signB);
        double c = seatOf(world, signC);
        String report = "read A(slab below, flush wall)=" + a + " B(control)=" + b
                + " C(lowered wall)=" + c
                + " | stored A=" + storedOf(world, signA) + " B=" + storedOf(world, signB)
                + " C=" + storedOf(world, signC);
        System.out.println("[WALL_SIGN_ABOVE_SLAB] " + report);

        ctx.assertTrue(Math.abs(b) <= EPS,
                "control: a wall sign on a flush wall over stone must read 0: " + report);
        ctx.assertTrue(Math.abs(c + 0.5d) <= EPS,
                "a wall sign on a LOWERED wall must follow its wall to -0.5: " + report);
        ctx.assertTrue(Math.abs(a) <= EPS,
                "GH #48: a wall sign on a FLUSH wall sank because of the slab under its cell; "
                        + "a wall sign hangs on its wall and touches nothing below: " + report);
        ctx.succeed();
    }
}
