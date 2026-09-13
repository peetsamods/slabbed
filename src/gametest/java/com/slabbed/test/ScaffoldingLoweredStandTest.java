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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A lowered scaffolding column must be stood on at its drawn top, not half a block inside it.
 *
 * <p>Scaffolding's only collision is a standing layer gated on the entity being above the cell's
 * full-cube top. Without {@code ScaffoldingLoweredStandMixin} the gate compares against the
 * unlowered cube, so an entity standing on the drawn top of a lowered column is "inside" and gets no
 * layer at all. This row asks the block for its collision with a player's feet on the drawn top and
 * again with the feet inside the body, on a lowered column and on a flush control.
 *
 * <p>MUTATION that must redden this row alone: remove {@code ScaffoldingLoweredStandMixin} from
 * {@code slabbed.mixins.json}.
 */
public final class ScaffoldingLoweredStandTest {

    private static final double EPS = 1.0e-6;

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 60)
    public void loweredScaffoldingIsStoodOnAtItsRealTop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        try {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            BlockPos g = helper.absolutePos(new BlockPos(2, 1, 2));
            level.setBlock(g, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(g.above(), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
            player.setPos(g.getX() + 3.5d, g.getY(), g.getZ() + 0.5d);
            InteractionResult r = PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(Items.SCAFFOLDING, 16), g.above(), Direction.UP);
            BlockPos cell = g.above(2);
            BlockState state = level.getBlockState(cell);
            double dy = SlabSupport.getYOffset(level, cell, state);
            if (!r.consumesAction() || !state.is(Blocks.SCAFFOLDING) || Math.abs(dy + 0.5d) > EPS) {
                throw helper.assertionException("premise: lowered scaffolding on the slab: result=" + r + " state=" + state + " dy=" + dy);
            }
            // Feet on the drawn top (y + 0.5): the standing layer must exist and end at the real top.
            player.setPos(cell.getX() + 0.5d, cell.getY() + 0.5d, cell.getZ() + 0.5d);
            VoxelShape standing = state.getCollisionShape(level, cell, CollisionContext.of(player));
            // Feet inside the lowered body (y + 0.2): no standing layer, the player is inside the column.
            player.setPos(cell.getX() + 0.5d, cell.getY() + 0.2d, cell.getZ() + 0.5d);
            VoxelShape inside = state.getCollisionShape(level, cell, CollisionContext.of(player));
            // Control: a flush column on stone keeps vanilla's behaviour (stood on at y + 1.0).
            BlockPos f = helper.absolutePos(new BlockPos(5, 1, 5));
            level.setBlock(f, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            player.setPos(f.getX() + 3.5d, f.getY(), f.getZ() + 0.5d);
            PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(Items.SCAFFOLDING, 16), f, Direction.UP);
            BlockPos flat = f.above();
            BlockState flatState = level.getBlockState(flat);
            if (!flatState.is(Blocks.SCAFFOLDING)) {
                throw helper.assertionException("premise: flush scaffolding on stone: " + flatState);
            }
            player.setPos(flat.getX() + 0.5d, flat.getY() + 1.0d, flat.getZ() + 0.5d);
            VoxelShape flatStanding = flatState.getCollisionShape(level, flat, CollisionContext.of(player));
            player.setPos(flat.getX() + 0.5d, flat.getY() + 0.5d, flat.getZ() + 0.5d);
            VoxelShape flatInside = flatState.getCollisionShape(level, flat, CollisionContext.of(player));
            String report = "lowered standing=" + (standing.isEmpty() ? "empty" : standing.bounds()) + " inside=" + inside
                    + " | flat standing=" + (flatStanding.isEmpty() ? "empty" : flatStanding.bounds()) + " inside=" + flatInside;
            System.out.println("[SCAFFOLD_STAND] " + report);
            if (standing.isEmpty() || Math.abs(standing.max(Direction.Axis.Y) - 0.5d) > EPS || !inside.isEmpty()) {
                throw helper.assertionException("lowered scaffolding is not stood on at its real top: " + report);
            }
            if (flatStanding.isEmpty() || Math.abs(flatStanding.max(Direction.Axis.Y) - 1.0d) > EPS || !flatInside.isEmpty()) {
                throw helper.assertionException("control: flush scaffolding changed: " + report);
            }
            helper.succeed();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
    }
}
