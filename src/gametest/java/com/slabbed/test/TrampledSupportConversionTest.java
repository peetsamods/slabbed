package com.slabbed.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A trampled sub-full support (dirt path / farmland, 15/16 tall) converts to dirt under a
 * placement this transaction manages, before the block lands (maintainer ruling, 2026-09-01)
 * — vanilla converts only under solid full blocks. The conversion is VIABILITY-GATED: a
 * placement that NEEDS the trampled block to survive (crops on farmland) converts nothing and
 * proceeds vanilla — the unguarded form converted the farmland first, the crop then refused
 * to sit on dirt, and every planting click destroyed one farmland block. A placement that is
 * not viable at all (a torch, which cannot survive on the path pre-conversion) also converts
 * nothing and keeps vanilla's refusal — the conversion serves placements, it does not create
 * them.
 */
public final class TrampledSupportConversionTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void placingSlabOnDirtPathConvertsItToDirt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pathRel = new BlockPos(2, 2, 2);
        helper.setBlock(pathRel.below(), Blocks.DIRT.defaultBlockState());
        helper.setBlock(pathRel, Blocks.DIRT_PATH.defaultBlockState());
        BlockPos path = helper.absolutePos(pathRel);

        Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(path.getX() + 0.5d, path.getY() + 2.0d, path.getZ() + 0.5d);
        ItemStack slab = new ItemStack(Items.STONE_SLAB);
        player.setItemInHand(InteractionHand.MAIN_HAND, slab);
        // A dirt path's real top face sits at 15/16; a raycast never reports a grid-height hit.
        Vec3 hit = new Vec3(path.getX() + 0.5d, path.getY() + 0.9375d, path.getZ() + 0.5d);
        InteractionResult result = slab.useOn(new net.minecraft.world.item.context.UseOnContext(
                player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, path, false)));

        if (!result.consumesAction()) {
            throw helper.assertionException(pathRel, "placing a slab on a dirt path must be accepted");
        }
        if (!level.getBlockState(path).is(Blocks.DIRT)) {
            throw helper.assertionException(pathRel,
                    "the dirt path must convert to dirt under the managed placement, got "
                            + level.getBlockState(path));
        }
        if (!level.getBlockState(path.above()).is(Blocks.STONE_SLAB)) {
            throw helper.assertionException(pathRel.above(),
                    "the slab must land on the converted support, got " + level.getBlockState(path.above()));
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void plantingSeedsKeepsFarmland(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos farmlandRel = new BlockPos(2, 2, 2);
        helper.setBlock(farmlandRel.below(), Blocks.DIRT.defaultBlockState());
        helper.setBlock(farmlandRel, Blocks.FARMLAND.defaultBlockState());
        BlockPos farmland = helper.absolutePos(farmlandRel);
        BlockPos cropPos = farmland.above();
        // Crops refuse to plant below light 8, the gametest scene carries no daylight
        // guarantee, and light propagates asynchronously — so the light source is part of the
        // fixture AND the planting waits for the engine to catch up.
        helper.setBlock(farmlandRel.above().west(), Blocks.GLOWSTONE.defaultBlockState());

        helper.runAfterDelay(10, () -> {
            Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            player.setPos(farmland.getX() + 0.5d, farmland.getY() + 2.0d, farmland.getZ() + 0.5d);
            ItemStack seeds = new ItemStack(Items.WHEAT_SEEDS);
            player.setItemInHand(InteractionHand.MAIN_HAND, seeds);
            // Farmland's real top face sits at 15/16; a raycast never reports a grid-height hit.
            Vec3 hit = new Vec3(
                    farmland.getX() + 0.5d, farmland.getY() + 0.9375d, farmland.getZ() + 0.5d);
            InteractionResult result = seeds.useOn(new net.minecraft.world.item.context.UseOnContext(
                    player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(hit, Direction.UP, farmland, false)));

            if (!result.consumesAction()) {
                throw helper.assertionException(farmlandRel, "planting on farmland must be accepted");
            }
            if (!level.getBlockState(farmland).is(Blocks.FARMLAND)) {
                throw helper.assertionException(farmlandRel,
                        "the farmland must survive the planting, got " + level.getBlockState(farmland));
            }
            if (!level.getBlockState(cropPos).is(Blocks.WHEAT)) {
                throw helper.assertionException(farmlandRel.above(),
                        "the crop must actually be planted, got " + level.getBlockState(cropPos));
            }
            helper.succeed();
        });
    }
}
