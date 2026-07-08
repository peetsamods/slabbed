package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * D4 port (audit STATE_DEFENSE_DIVERGENCE_2026-07-07; donor: 1.21.11 isConnectingStructural admission
 * to the freeze-flat gate): a fence/wall/pane/gate placed FLAT must freeze at its placed height like
 * every other structural piece — on unfixed 26.2 the structural gate required isSolidRender, so
 * connecting blocks stayed LIVE forever and sank -0.5 the moment someone edited a slab under them
 * (the maintainer's down-pop while editing beneath existing builds; the exact scenario FROZEN_FLAT exists for).
 * Control pins the feature direction: a fence placed ON a lowered support still follows it down.
 */
public final class ConnectingStructuralFreezeTest {

    private static final double EPS = 1.0e-6;

    private static void place(GameTestHelper helper, ItemStack stack, BlockPos clicked, Direction face) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked).add(0, 0.5, 0), face, clicked, false)));
    }

    private static void onPlaced(ServerLevel w, BlockPos pos) {
        SlabAnchorAttachment.addAnchor(w, pos, w.getBlockState(pos));
        SlabAnchorAttachment.freezeLoweredOnPlace(w, pos, w.getBlockState(pos));
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatPlacedFenceHoldsWhenASlabIsShovedUnder(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos fence = ground.above();
        w.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
        place(helper, new ItemStack(Items.OAK_FENCE), ground, Direction.UP);
        if (w.getBlockState(fence).isAir()) {
            throw helper.assertionException("premise: the fence placement must succeed");
        }
        onPlaced(w, fence);
        if (Math.abs(SlabSupport.getYOffset(w, fence, w.getBlockState(fence))) > EPS) {
            throw helper.assertionException("premise: the flat-placed fence must read 0.0");
        }
        // The edit-under: break the stone, shove a bottom slab under. NEVER-POP: the fence STAYS.
        w.destroyBlock(ground, false);
        w.setBlock(ground, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        double dy = SlabSupport.getYOffset(w, fence, w.getBlockState(fence));
        if (Math.abs(dy) > EPS) {
            throw helper.assertionException(
                    "D4: a FLAT-placed fence must hold 0.0 when a slab is shoved under it (never-pop), got " + dy);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fencePlacedOnALoweredSupportStillFollowsIt(GameTestHelper helper) {
        // Control: the feature direction is untouched — a fence placed ON a bottom slab follows down.
        ServerLevel w = helper.getLevel();
        BlockPos slab = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos fence = slab.above();
        w.setBlock(slab, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        place(helper, new ItemStack(Items.OAK_FENCE), slab, Direction.UP);
        if (w.getBlockState(fence).isAir()) {
            throw helper.assertionException("premise: the fence placement must succeed");
        }
        onPlaced(w, fence);
        double dy = SlabSupport.getYOffset(w, fence, w.getBlockState(fence));
        if (Math.abs(dy + 0.5) > EPS) {
            throw helper.assertionException("a fence placed ON a bottom slab must follow it to -0.5, got " + dy);
        }
        helper.succeed();
    }
}
