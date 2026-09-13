package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * A wall sign is carried by the WALL it hangs on, never by the floor under its cell (GH #48).
 *
 * <p>The report: a wall sign placed on a wall, in the cell directly above a bottom slab, moved
 * down half a block. A wall sign touches nothing below it — its support is the block behind it —
 * so a slab under its cell is scenery, not support (LAW 2: eligibility follows the geometry of the
 * block that actually holds the thing up). Three scenes, each placing a real oak sign on the north
 * face of a wall block and reading the height {@code SlabSupport.getYOffset} reports for the placed
 * sign's own cell (this line has no separate placement-dy store to read verbatim, so the live
 * resolver's answer for the sign's cell is the fact under test):
 * <ul>
 *   <li>flush wall, bottom slab under the sign's cell — the reported scene — must read 0;</li>
 *   <li>flush wall, stone under the sign's cell — control — must read 0;</li>
 *   <li>a wall block itself lowered onto a slab — the sign follows its wall, must read −0.5.</li>
 * </ul>
 */
public final class WallSignAboveSlabTest {

    private static final double EPS = 1.0e-6d;

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static double seatOf(ServerWorld world, BlockPos pos) {
        return SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
    }

    /** Places an oak sign on the NORTH face of {@code wall} via the real useOn chain; returns the sign cell. */
    private static BlockPos hangSign(TestContext ctx, PlayerEntity player, BlockPos wall) {
        ServerWorld world = ctx.getWorld();
        player.refreshPositionAndAngles(wall.getX() + 0.5d, wall.getY() - 1.0d, wall.getZ() - 2.5d, 0.0f, 0.0f);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.OAK_SIGN, 16));
        ActionResult r = PlacementHarness.useHeldItem(world, player, wall, Direction.NORTH, Vec3d.ofCenter(wall));
        BlockPos cell = wall.offset(Direction.NORTH);
        BlockState sign = world.getBlockState(cell);
        ctx.assertTrue(r.isAccepted() && sign.getBlock() instanceof WallSignBlock,
                "premise: an oak sign on the wall's north face must hang as a wall sign: result=" + r + " cell=" + sign);
        return cell;
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aWallSignIsCarriedByItsWallNotTheSlabUnderItsCell(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);

        // Scene A (the report): flush wall, bottom slab under the sign cell.
        BlockPos wallA = ctx.getAbsolutePos(new BlockPos(1, 3, 6));
        ctx.setBlockState(new BlockPos(1, 1, 6), Blocks.STONE.getDefaultState());
        ctx.setBlockState(new BlockPos(1, 2, 6), Blocks.STONE.getDefaultState());
        ctx.setBlockState(new BlockPos(1, 3, 6), Blocks.STONE.getDefaultState());
        ctx.setBlockState(new BlockPos(1, 2, 5), bottomSlab());
        BlockPos signA = hangSign(ctx, player, wallA);

        // Scene B (control): flush wall, stone under the sign cell.
        BlockPos wallB = ctx.getAbsolutePos(new BlockPos(4, 3, 6));
        ctx.setBlockState(new BlockPos(4, 1, 6), Blocks.STONE.getDefaultState());
        ctx.setBlockState(new BlockPos(4, 2, 6), Blocks.STONE.getDefaultState());
        ctx.setBlockState(new BlockPos(4, 3, 6), Blocks.STONE.getDefaultState());
        ctx.setBlockState(new BlockPos(4, 2, 5), Blocks.STONE.getDefaultState());
        BlockPos signB = hangSign(ctx, player, wallB);

        // Scene C (the rule the sign must follow): the wall block itself is lowered onto a slab.
        ctx.setBlockState(new BlockPos(7, 1, 6), Blocks.STONE.getDefaultState());
        ctx.setBlockState(new BlockPos(7, 2, 6), bottomSlab());
        BlockPos slabC = ctx.getAbsolutePos(new BlockPos(7, 2, 6));
        // A fresh builder: placing a sign leaves the placing player in a sign-edit session.
        PlayerEntity builder = ctx.createMockPlayer(GameMode.SURVIVAL);
        builder.refreshPositionAndAngles(slabC.getX() + 0.5d, slabC.getY(), slabC.getZ() + 2.5d, 0.0f, 0.0f);
        builder.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 16));
        // Click the slab's REAL top (y + 0.5), not the cell's full-cube face: a click in the air
        // above a bottom slab is rightly refused by the hit validation.
        ActionResult rc = PlacementHarness.useHeldItem(world, builder, slabC, Direction.UP,
                Vec3d.ofBottomCenter(slabC).add(0.0d, 0.5d, 0.0d));
        BlockPos wallC = slabC.up();
        double wallCDy = SlabSupport.getYOffset(world, wallC, world.getBlockState(wallC));
        // The world state is the premise; the result code is reported only (the placement lane may answer FAIL after placing).
        ctx.assertTrue(world.getBlockState(wallC).isOf(Blocks.STONE) && Math.abs(wallCDy + 0.5d) <= EPS,
                "premise: the wall block must be lowered onto the slab: " + rc + " dy=" + wallCDy
                        + " wallC=" + world.getBlockState(wallC) + " slabC=" + world.getBlockState(slabC)
                        + " builderPos=" + builder.getEntityPos() + " slabC=" + slabC);
        BlockPos signC = hangSign(ctx, player, wallC);

        double a = seatOf(world, signA);
        double b = seatOf(world, signB);
        double c = seatOf(world, signC);
        String report = "read A(slab below, flush wall)=" + a + " B(control)=" + b + " C(lowered wall)=" + c;
        System.out.println("[WALL_SIGN_ABOVE_SLAB] " + report);
        ctx.assertTrue(Math.abs(b) <= EPS, "control: a wall sign on a flush wall over stone must read 0: " + report);
        ctx.assertTrue(Math.abs(c + 0.5d) <= EPS, "a wall sign on a LOWERED wall must follow its wall to -0.5: " + report);
        ctx.assertTrue(Math.abs(a) <= EPS,
                "GH #48: a wall sign on a FLUSH wall sank because of the slab under its cell; "
                        + "a wall sign hangs on its wall and touches nothing below: " + report);
        ctx.complete();
    }
}
