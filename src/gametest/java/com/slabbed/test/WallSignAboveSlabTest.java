package com.slabbed.test;

import com.slabbed.Slabbed;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A wall sign is carried by the WALL it hangs on, never by the floor under its cell.
 *
 * <p>The report: a wall sign placed on a wall, in the cell directly above a bottom slab, moved
 * down half a block. A wall sign touches nothing below it — its support is the block behind it —
 * so a slab under its cell is scenery, not support (LAW.md Law 2: eligibility follows the geometry
 * of the block that actually holds the thing up). Three scenes, each placing a real oak sign on the
 * north face of a wall block and reading the seat the placement stored:
 * <ul>
 *   <li>flush wall, bottom slab under the sign's cell — the reported scene — must read 0;</li>
 *   <li>flush wall, stone under the sign's cell — control — must read 0;</li>
 *   <li>a wall block itself lowered onto a slab — the sign follows its wall, must read −0.5.</li>
 * </ul>
 *
 * <p>REACH, one named mutation per asserted value.
 * <ul>
 *   <li>Scenes A and B reach {@code SlabSupport.shouldOffset}: delete the
 *       {@code role == AttachmentRole.SIDE} early return there and the sign in scene A sinks onto
 *       the slab below its cell, which is the reported defect itself.</li>
 *   <li>Scene C reaches the placement record: zero the {@code captureDy} that
 *       {@code BlockItemPlacementIntentMixin} publishes. That publish is the record's single
 *       approved writer — {@code tools/verify-placement-store-writers.py} holds it to exactly one
 *       call site — so nothing else can put scene C's −0.5 there.</li>
 * </ul>
 */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class WallSignAboveSlabTest {

    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6d;

    private static double storedSeat(ServerLevel level, BlockPos pos) {
        double stored = SlabPlacementHeightAttachment.storedOffset(level, pos);
        return Double.isFinite(stored) ? stored : 0.0d;
    }

    /** Uses {@code stack} against {@code face} of {@code target} through the real placement path. */
    private static InteractionResult useOn(Player player, ItemStack stack, BlockPos target, Direction face, double faceDy) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(target)
                .add(face.getStepX() * 0.5d, face.getStepY() * 0.5d + faceDy, face.getStepZ() * 0.5d);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, target, false)));
    }

    /** Places an oak sign on the NORTH face of {@code wall}; returns the sign cell. */
    private static BlockPos hangSign(GameTestHelper ctx, Player player, BlockPos wall) {
        ServerLevel level = ctx.getLevel();
        player.setPos(wall.getX() + 0.5d, wall.getY() - 1.0d, wall.getZ() - 2.5d);
        player.setYRot(0.0f);
        double wallDy = SlabSupport.getYOffset(level, wall, level.getBlockState(wall));
        InteractionResult r = useOn(player, new ItemStack(Items.OAK_SIGN, 16), wall, Direction.NORTH,
                Double.isFinite(wallDy) ? wallDy : 0.0d);
        BlockPos cell = wall.north();
        BlockState sign = level.getBlockState(cell);
        ctx.assertTrue(r.consumesAction() && sign.getBlock() instanceof WallSignBlock,
                "premise: an oak sign on the wall's north face must hang as a wall sign: result=" + r + " cell=" + sign);
        return cell;
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void aWallSignIsCarriedByItsWallNotTheSlabUnderItsCell(GameTestHelper ctx) {
        ServerLevel level = ctx.getLevel();
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        BlockState bottomSlab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);

        // Scene A (the report): flush wall, bottom slab under the sign cell.
        BlockPos wallA = ctx.absolutePos(new BlockPos(1, 3, 6));
        level.setBlock(ctx.absolutePos(new BlockPos(1, 1, 6)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(ctx.absolutePos(new BlockPos(1, 2, 6)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(wallA, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(ctx.absolutePos(new BlockPos(1, 2, 5)), bottomSlab, Block.UPDATE_ALL);
        BlockPos signA = hangSign(ctx, player, wallA);

        // Scene B (control): flush wall, stone under the sign cell.
        BlockPos wallB = ctx.absolutePos(new BlockPos(4, 3, 6));
        level.setBlock(ctx.absolutePos(new BlockPos(4, 1, 6)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(ctx.absolutePos(new BlockPos(4, 2, 6)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(wallB, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(ctx.absolutePos(new BlockPos(4, 2, 5)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        // A fresh builder each time: placing a sign leaves the placing player in a sign-edit session.
        BlockPos signB = hangSign(ctx, ctx.makeMockPlayer(GameType.SURVIVAL), wallB);

        // Scene C: the wall block itself is lowered onto a slab, by a real placement.
        BlockPos slabC = ctx.absolutePos(new BlockPos(7, 2, 6));
        level.setBlock(ctx.absolutePos(new BlockPos(7, 1, 6)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(slabC, bottomSlab, Block.UPDATE_ALL);
        BlockPos wallC = slabC.above();
        Player builder = ctx.makeMockPlayer(GameType.SURVIVAL);
        builder.setPos(wallC.getX() + 0.5d, wallC.getY(), wallC.getZ() + 0.5d);
        ItemStack stone = new ItemStack(Blocks.STONE);
        builder.setItemInHand(InteractionHand.MAIN_HAND, stone);
        // Click the slab's REAL top (the cell centre IS that face), not the cell's full-cube top:
        // a click in the air above a bottom slab is rightly refused by the hit validation.
        InteractionResult rc = stone.useOn(new UseOnContext(builder, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(slabC), Direction.UP, slabC, false)));
        double wallCDy = SlabSupport.getYOffset(level, wallC, level.getBlockState(wallC));
        ctx.assertTrue(level.getBlockState(wallC).is(Blocks.STONE) && Math.abs(wallCDy + 0.5d) <= EPS,
                "premise: the wall block must be lowered onto the slab: " + rc + " dy=" + wallCDy
                        + " wallC=" + level.getBlockState(wallC) + " slabC=" + level.getBlockState(slabC));
        BlockPos signC = hangSign(ctx, ctx.makeMockPlayer(GameType.SURVIVAL), wallC);

        double a = storedSeat(level, signA);
        double b = storedSeat(level, signB);
        double c = storedSeat(level, signC);
        double aRead = SlabSupport.getYOffset(level, signA, level.getBlockState(signA));
        String report = "stored A(slab below, flush wall)=" + a + " B(control)=" + b + " C(lowered wall)=" + c
                + " | read A=" + aRead;
        Slabbed.LOGGER.info("[WALL_SIGN_ABOVE_SLAB] {}", report);
        ctx.assertTrue(Math.abs(b) <= EPS,
                "control: a wall sign on a flush wall over stone must read 0: " + report);
        ctx.assertTrue(Math.abs(c + 0.5d) <= EPS,
                "a wall sign on a LOWERED wall must follow its wall to -0.5: " + report);
        ctx.assertTrue(Math.abs(a) <= EPS && Math.abs(aRead) <= EPS,
                "a wall sign on a FLUSH wall sank because of the slab under its cell; "
                        + "a wall sign hangs on its wall and touches nothing below: " + report);
        ctx.succeed();
    }
}
