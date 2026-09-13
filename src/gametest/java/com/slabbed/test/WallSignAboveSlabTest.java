package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * A wall sign is carried by the WALL it hangs on, never by the floor under its cell (GH #48).
 *
 * <p>Three scenes, each placing a real oak sign on the north face of a wall block and reading the
 * seat the placement recorded: flush wall with a bottom slab under the sign's cell (the report),
 * must read 0; flush wall over stone (control), 0; a wall block itself placed lowered onto a slab,
 * −0.5 (the sign follows its wall). The live-lane number for the reported scene is printed for the
 * record, not asserted.
 */
public final class WallSignAboveSlabTest {

    private static final double EPS = 1.0e-6d;

    private static double seatOf(ServerWorld world, BlockPos pos) {
        double stored = SlabAnchorAttachment.storedPlacementDy(world, pos);
        return Double.isFinite(stored) ? stored : 0.0d;
    }

    private static BlockPos hangSign(TestContext ctx, PlayerEntity player, BlockPos wall) {
        ServerWorld world = ctx.getWorld();
        player.setPosition(wall.getX() + 0.5d, wall.getY() - 1.0d, wall.getZ() - 2.5d);
        player.setYaw(0.0f);
        ActionResult r = PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(Items.OAK_SIGN, 16), wall, Direction.NORTH);
        BlockPos cell = wall.north();
        BlockState sign = world.getBlockState(cell);
        if (!r.isAccepted() || !(sign.getBlock() instanceof WallSignBlock)) {
            ctx.throwGameTestException("premise: an oak sign on the wall's north face must hang as a wall sign: result=" + r + " cell=" + sign);
        }
        return cell;
    }

    private static void column(TestContext ctx, int x, int z, BlockState under) {
        ctx.setBlockState(new BlockPos(x, 15, z), Blocks.STONE.getDefaultState());
        ctx.setBlockState(new BlockPos(x, 16, z), Blocks.STONE.getDefaultState());
        ctx.setBlockState(new BlockPos(x, 17, z), Blocks.STONE.getDefaultState());
        ctx.setBlockState(new BlockPos(x, 16, z - 1), under);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void aWallSignIsCarriedByItsWallNotTheSlabUnderItsCell(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
        BlockState bottomSlab = Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
        // Scene A (the report): flush wall, bottom slab under the sign cell.
        column(ctx, 1, 6, bottomSlab);
        BlockPos signA = hangSign(ctx, player, ctx.getAbsolutePos(new BlockPos(1, 17, 6)));
        // Scene B (control): flush wall, stone under the sign cell.
        column(ctx, 4, 6, Blocks.STONE.getDefaultState());
        BlockPos signB = hangSign(ctx, player, ctx.getAbsolutePos(new BlockPos(4, 17, 6)));
        // Scene C: the wall block itself is placed lowered onto a slab, clicked at the slab's REAL top.
        ctx.setBlockState(new BlockPos(7, 15, 6), Blocks.STONE.getDefaultState());
        ctx.setBlockState(new BlockPos(7, 16, 6), bottomSlab);
        BlockPos slabC = ctx.getAbsolutePos(new BlockPos(7, 16, 6));
        PlayerEntity builder = ctx.createMockPlayer(GameMode.SURVIVAL);
        builder.setPosition(slabC.getX() + 0.5d, slabC.getY(), slabC.getZ() + 2.5d);
        ActionResult rc = PlacementCaptureBoundaryGameTest.useOn(builder, new ItemStack(Items.STONE, 16),
                new BlockHitResult(Vec3d.ofBottomCenter(slabC).add(0.0d, 0.5d, 0.0d), Direction.UP, slabC, false));
        BlockPos wallC = slabC.up();
        double wallCDy = SlabSupport.getYOffset(world, wallC, world.getBlockState(wallC));
        if (!world.getBlockState(wallC).isOf(Blocks.STONE) || Math.abs(wallCDy + 0.5d) > EPS) {
            ctx.throwGameTestException("premise: the wall block must be lowered onto the slab: " + rc + " dy=" + wallCDy + " wallC=" + world.getBlockState(wallC));
        }
        BlockPos signC = hangSign(ctx, player, wallC);

        double a = seatOf(world, signA);
        double b = seatOf(world, signB);
        double c = seatOf(world, signC);
        double aRead = SlabSupport.getYOffset(world, signA, world.getBlockState(signA));
        double aLive = SlabSupport.getUnstoredYOffset(world, signA, world.getBlockState(signA));
        String report = "stored A(slab below, flush wall)=" + a + " B(control)=" + b + " C(lowered wall)=" + c
                + " | read A=" + aRead + " live-lane A=" + aLive;
        System.out.println("[WALL_SIGN_ABOVE_SLAB] " + report);
        if (Math.abs(b) > EPS) {
            ctx.throwGameTestException("control: a wall sign on a flush wall over stone must read 0: " + report);
        }
        if (Math.abs(c + 0.5d) > EPS) {
            ctx.throwGameTestException("a wall sign on a LOWERED wall must follow its wall to -0.5: " + report);
        }
        if (Math.abs(a) > EPS || Math.abs(aRead) > EPS) {
            ctx.throwGameTestException("GH #48: a wall sign on a FLUSH wall sank because of the slab under its cell; a wall sign hangs on its wall and touches nothing below: " + report);
        }
        ctx.complete();
    }
}
