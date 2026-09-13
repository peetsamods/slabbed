package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A wall sign is carried by the WALL it hangs on, never by the floor under its cell (GH #48).
 *
 * <p>The report: a wall sign placed on a wall, in the cell directly above a bottom slab, moved
 * down half a block. A wall sign touches nothing below it — its support is the block behind it —
 * so a slab under its cell is scenery, not support (LAW 2: eligibility follows the geometry of the
 * block that actually holds the thing up). Three scenes, each placing a real oak sign on the north
 * face of a wall block and reading the stored seat the placement minted:
 * <ul>
 *   <li>flush wall, bottom slab under the sign's cell — the reported scene — must read 0;</li>
 *   <li>flush wall, stone under the sign's cell — control — must read 0;</li>
 *   <li>a wall block itself lowered onto a slab — the sign follows its wall, must read −0.5.</li>
 * </ul>
 *
 * <p>REACH: the resolver has no wall-attached rule; a side-clicked object's seat is the clicked
 * owner's visible height, and the floor under the target cell never enters, so scene A cannot be
 * reddened by any present store-path seam (it is the tripwire half). Scene C reaches the owner read:
 * MUTATION that must redden this row — zero {@code ownerVisibleDy} in
 * {@code LandingResolver.captureAim}. The legacy recompute lane ({@code hasSlabInColumn(pos)} before
 * the wall-attached branch) answers only with the store OFF; its number is printed, not asserted.
 */
public final class WallSignAboveSlabTest {

    private static final double EPS = 1.0e-6d;

    private static double seatOf(ServerLevel level, BlockPos pos) {
        double stored = SlabAnchorAttachment.storedPlacementDy(level, pos);
        return Double.isFinite(stored) ? stored : 0.0d;
    }

    /** Places an oak sign on the NORTH face of {@code wall}; returns the sign cell. */
    private static BlockPos hangSign(GameTestHelper helper, Player player, BlockPos wall) {
        ServerLevel level = helper.getLevel();
        player.setPos(wall.getX() + 0.5d, wall.getY() - 1.0d, wall.getZ() - 2.5d);
        player.setYRot(0.0f);
        InteractionResult r = PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(Items.OAK_SIGN, 16), wall, Direction.NORTH);
        BlockPos cell = wall.north();
        BlockState sign = level.getBlockState(cell);
        if (!r.consumesAction() || !(sign.getBlock() instanceof WallSignBlock)) {
            throw helper.assertionException(helper.relativePos(cell), "premise: an oak sign on the wall's north face must hang as a wall sign: result=" + r + " cell=" + sign);
        }
        return cell;
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void aWallSignIsCarriedByItsWallNotTheSlabUnderItsCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // Placements run under the test JVM's default store setting (the established pattern on this
        // line); the reads below force the store ON, the way the shipped jar reads.
        {
            // Scene A (the report): flush wall, bottom slab under the sign cell.
            BlockPos wallA = helper.absolutePos(new BlockPos(1, 3, 6));
            helper.setBlock(new BlockPos(1, 1, 6), Blocks.STONE.defaultBlockState());
            helper.setBlock(new BlockPos(1, 2, 6), Blocks.STONE.defaultBlockState());
            helper.setBlock(new BlockPos(1, 3, 6), Blocks.STONE.defaultBlockState());
            helper.setBlock(new BlockPos(1, 2, 5), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            BlockPos signA = hangSign(helper, player, wallA);

            // Scene B (control): flush wall, stone under the sign cell.
            BlockPos wallB = helper.absolutePos(new BlockPos(4, 3, 6));
            helper.setBlock(new BlockPos(4, 1, 6), Blocks.STONE.defaultBlockState());
            helper.setBlock(new BlockPos(4, 2, 6), Blocks.STONE.defaultBlockState());
            helper.setBlock(new BlockPos(4, 3, 6), Blocks.STONE.defaultBlockState());
            helper.setBlock(new BlockPos(4, 2, 5), Blocks.STONE.defaultBlockState());
            BlockPos signB = hangSign(helper, player, wallB);

            // Scene C (the rule the sign must follow): the wall block itself is lowered onto a slab.
            helper.setBlock(new BlockPos(7, 1, 6), Blocks.STONE.defaultBlockState());
            helper.setBlock(new BlockPos(7, 2, 6), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            BlockPos slabC = helper.absolutePos(new BlockPos(7, 2, 6));
            // A fresh builder: placing a sign leaves the placing player in a sign-edit session.
            Player builder = helper.makeMockPlayer(GameType.SURVIVAL);
            builder.setPos(slabC.getX() + 0.5d, slabC.getY(), slabC.getZ() + 2.5d);
            // Click the slab's REAL top (y + 0.5), not the cell's full-cube face: a click in the air
            // above a bottom slab is rightly refused by the hit validation.
            ItemStack stone = new ItemStack(Items.STONE, 16);
            builder.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stone);
            InteractionResult rc = stone.useOn(new net.minecraft.world.item.context.UseOnContext(builder,
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(
                            net.minecraft.world.phys.Vec3.atBottomCenterOf(slabC).add(0.0d, 0.5d, 0.0d),
                            Direction.UP, slabC, false)));
            BlockPos wallC = slabC.above();
            double wallCDy = SlabSupport.getYOffset(level, wallC, level.getBlockState(wallC));
            // The world state is the premise; the result code is reported only (the placement lane may answer FAIL after placing).
            if (!level.getBlockState(wallC).is(Blocks.STONE) || Math.abs(wallCDy + 0.5d) > EPS) {
                StringBuilder dump = new StringBuilder();
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dyy = -1; dyy <= 2; dyy++) {
                        BlockPos q = slabC.offset(0, dyy, dz);
                        dump.append(" [").append(dyy).append(',').append(dz).append("]=").append(level.getBlockState(q).getBlock().getDescriptionId());
                    }
                }
                throw helper.assertionException(helper.relativePos(wallC), "premise: the wall block must be lowered onto the slab: " + rc + " dy=" + wallCDy
                        + " wallC=" + level.getBlockState(wallC) + " slabC=" + level.getBlockState(slabC) + " builderPos=" + builder.position() + " slabC=" + slabC + dump);
            }
            BlockPos signC = hangSign(helper, player, wallC);

            double a = seatOf(level, signA);
            double b = seatOf(level, signB);
            double c = seatOf(level, signC);
            boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
            SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
            double aRead;
            double aLegacy;
            try {
                aRead = SlabSupport.getYOffset(level, signA, level.getBlockState(signA));
                // Information only: what the pre-0.5 recompute (the -Dslabbed.frozenDy=false escape
                // hatch) would answer for the same sign. Not asserted; printed for the ledger.
                SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
                aLegacy = SlabSupport.getYOffset(level, signA, level.getBlockState(signA));
            } finally {
                SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
            }
            String report = "stored A(slab below, flush wall)=" + a + " B(control)=" + b + " C(lowered wall)=" + c
                    + " | read A store-on=" + aRead + " store-off(legacy recompute)=" + aLegacy;
            System.out.println("[WALL_SIGN_ABOVE_SLAB] " + report);
            if (Math.abs(b) > EPS) {
                throw helper.assertionException(helper.relativePos(signB), "control: a wall sign on a flush wall over stone must read 0: " + report);
            }
            if (Math.abs(c + 0.5d) > EPS) {
                throw helper.assertionException(helper.relativePos(signC), "a wall sign on a LOWERED wall must follow its wall to -0.5: " + report);
            }
            if (Math.abs(a) > EPS || Math.abs(aRead) > EPS) {
                throw helper.assertionException(helper.relativePos(signA), "GH #48: a wall sign on a FLUSH wall sank because of the slab under its cell; "
                        + "a wall sign hangs on its wall and touches nothing below: " + report);
            }
            helper.succeed();
        }
    }
}
