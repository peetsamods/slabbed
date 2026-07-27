package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * THE R2 HARNESS — machine enforcement of the STAYS law (scope Phase 4).
 *
 * <p>A block placed through the REAL placement path must store its height, and that stored raw
 * bits must survive EVERY class of neighbour mutation: support broken, slab shoved under, lowered
 * carrier built beside, adjacent slab flipped, blocks placed and broken above. The one sanctioned
 * carve-out: vanilla removing the subject itself. Additionally, an in-place block-KIND swap that
 * keeps the block lock-eligible (grass→dirt from a random tick, log→stripped, copper oxidation)
 * must KEEP the stored height — the removal hook clears only genuine removals.
 *
 * <p>{@code FROZEN_DY_ENABLED} is forced true in-process so the kill-switch cannot false-green
 * this test. Every stored write is struck in a finally (test-store hygiene law).
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ForgeNeighborUpdateInvarianceGameTest {

    @GameTest(template = "empty")
    public void placedHeightSurvivesEveryNeighbourAttack(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        boolean priorFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        BlockPos support = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos subject = support.above();
        BlockPos grass = ctx.absolutePos(new BlockPos(6, 3, 2));
        BlockPos doorFloor = ctx.absolutePos(new BlockPos(2, 2, 6));
        try {
            runRows(ctx, world, support, subject, grass, doorFloor);
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = priorFrozen;
            SlabAnchorAttachment.removePlacementDy(world, subject);
            SlabAnchorAttachment.removePlacementDy(world, grass);
            SlabAnchorAttachment.removePlacementDy(world, doorFloor.above());
            SlabAnchorAttachment.removePlacementDy(world, doorFloor.above(2));
            SlabAnchorAttachment.removePlacementDy(world, grass.east(2)); // the fence row
        }
        ctx.succeed();
    }

    private static void runRows(GameTestHelper ctx, ServerLevel world,
                                BlockPos support, BlockPos subject, BlockPos grass, BlockPos doorFloor) {
        // --- 1. a REAL placement stores its height ---
        world.setBlock(support, bottomSlab(), Block.UPDATE_ALL);
        placeItem(world, support, Blocks.STONE);
        ctx.assertTrue(world.getBlockState(subject).is(Blocks.STONE),
                "fixture: stone must place at " + subject.toShortString());
        double stored = SlabAnchorAttachment.storedPlacementDy(world, subject);
        ctx.assertTrue(!Double.isNaN(stored),
                "PHASE 4 WRITER: a real placement must store its height — nothing was stored");
        long bits = Double.doubleToRawLongBits(stored);
        ctx.assertTrue(bits == Double.doubleToRawLongBits(-0.5d),
                "stone on a bottom slab must store exactly -0.5, got " + stored);

        // --- 2. the attacks: after each, the stored raw bits and the live answer are identical ---
        attack(ctx, world, subject, bits, "break the support below",
                () -> world.setBlock(support, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        attack(ctx, world, subject, bits, "re-add the slab below",
                () -> world.setBlock(support, bottomSlab(), Block.UPDATE_ALL));
        attack(ctx, world, subject, bits, "build a lowered carrier beside",
                () -> {
                    world.setBlock(subject.west().below(), bottomSlab(), Block.UPDATE_ALL);
                    world.setBlock(subject.west(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                });
        attack(ctx, world, subject, bits, "flip the adjacent slab to TOP",
                () -> world.setBlock(support.east(), Blocks.OAK_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.TOP), Block.UPDATE_ALL));
        attack(ctx, world, subject, bits, "place and break above",
                () -> {
                    world.setBlock(subject.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                    world.setBlock(subject.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                });

        // --- 3. FOLLOWER DIVERGENCE, recorded not asserted (Phase 6 obligation from review) ---
        // The support's stored dy is -0.5 while its geometry may say otherwise after the attacks;
        // a hanging follower derives store-blind today. Log the split for the Phase 6 red.
        world.setBlock(subject.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE);
        double subjectLive = SlabSupport.getYOffset(world, subject, world.getBlockState(subject));
        double subjectGeom = SlabSupport.getUnstoredYOffset(world, subject, world.getBlockState(subject));
        Slabbed.LOGGER.info("[FOLLOWER_DIVERGENCE] subject={} storedLive={} geometric={} split={}",
                subject.toShortString(), subjectLive, subjectGeom,
                Double.compare(subjectLive, subjectGeom) != 0);
        world.setBlock(subject.below(), bottomSlab(), Block.UPDATE_NONE);

        // --- 4. anti-jitter: an in-place kind swap that stays lock-eligible KEEPS the height ---
        world.setBlock(grass.below(), bottomSlab(), Block.UPDATE_ALL);
        placeItem(world, grass.below(), Blocks.GRASS_BLOCK);
        ctx.assertTrue(world.getBlockState(grass).is(Blocks.GRASS_BLOCK),
                "fixture: grass block must place at " + grass.toShortString());
        double grassStored = SlabAnchorAttachment.storedPlacementDy(world, grass);
        ctx.assertTrue(!Double.isNaN(grassStored), "grass placement must store its height");
        long grassBits = Double.doubleToRawLongBits(grassStored);
        world.setBlock(grass, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL); // the random-tick shape
        double afterTick = SlabAnchorAttachment.storedPlacementDy(world, grass);
        ctx.assertTrue(!Double.isNaN(afterTick)
                        && Double.doubleToRawLongBits(afterTick) == grassBits,
                "ANTI-JITTER: grass->dirt in place must KEEP the stored height (a random tick is "
                        + "not a removal); stored was " + grassStored + " now " + afterTick);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, grass),
                "the anchor marker must survive the in-place kind swap too");

        // --- 4b. WATERLOGGING is not a removal: a wet fence is still the fence ---
        BlockPos fence = grass.east(2);
        world.setBlock(fence.below(), bottomSlab(), Block.UPDATE_ALL);
        placeItem(world, fence.below(), Blocks.OAK_FENCE);
        ctx.assertTrue(world.getBlockState(fence).is(Blocks.OAK_FENCE),
                "fixture: fence must place at " + fence.toShortString());
        double fenceStored = SlabAnchorAttachment.storedPlacementDy(world, fence);
        ctx.assertTrue(!Double.isNaN(fenceStored), "fence placement must store its height");
        long fenceBits = Double.doubleToRawLongBits(fenceStored);
        world.setBlock(fence, world.getBlockState(fence)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true),
                Block.UPDATE_ALL);
        double wetStored = SlabAnchorAttachment.storedPlacementDy(world, fence);
        ctx.assertTrue(!Double.isNaN(wetStored)
                        && Double.doubleToRawLongBits(wetStored) == fenceBits,
                "WATERLOGGING: a wet fence is still the fence — the stored height must survive; "
                        + "was " + fenceStored + " now " + wetStored);
        SlabAnchorAttachment.removePlacementDy(world, fence);

        // --- 5. genuine removal clears everything ---
        world.destroyBlock(subject, false);
        ctx.assertTrue(Double.isNaN(SlabAnchorAttachment.storedPlacementDy(world, subject)),
                "genuine removal must clear the stored height");
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(world, subject),
                "genuine removal must clear the anchor marker");

        // --- 6. multi-cell placement: a door stores BOTH halves, at exactly 0.0 (R3) ---
        world.setBlock(doorFloor, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        placeItem(world, doorFloor, Blocks.OAK_DOOR);
        BlockPos lower = doorFloor.above();
        BlockPos upper = lower.above();
        ctx.assertTrue(world.getBlockState(lower).is(Blocks.OAK_DOOR),
                "fixture: door must place at " + lower.toShortString());
        double lowerDy = SlabAnchorAttachment.storedPlacementDy(world, lower);
        double upperDy = SlabAnchorAttachment.storedPlacementDy(world, upper);
        ctx.assertTrue(Double.doubleToRawLongBits(lowerDy) == Double.doubleToRawLongBits(0.0d),
                "MULTI-PLACE: the door's lower half must store exactly +0.0 (R3), got " + lowerDy);
        ctx.assertTrue(Double.doubleToRawLongBits(upperDy) == Double.doubleToRawLongBits(0.0d),
                "MULTI-PLACE: the door's upper half must store exactly +0.0 (R3), got " + upperDy);
    }

    /** One attack: mutate a neighbour, then require byte-identical stored truth and live answer. */
    private static void attack(GameTestHelper ctx, ServerLevel world, BlockPos subject,
                               long expectedBits, String name, Runnable mutation) {
        mutation.run();
        ctx.assertTrue(world.getBlockState(subject).is(Blocks.STONE),
                "attack '" + name + "': the subject itself must survive");
        double stored = SlabAnchorAttachment.storedPlacementDy(world, subject);
        ctx.assertTrue(Double.doubleToRawLongBits(stored) == expectedBits,
                "R2 VIOLATION at attack '" + name + "': stored bits moved — stored=" + stored);
        double live = SlabSupport.getYOffset(world, subject, world.getBlockState(subject));
        ctx.assertTrue(Double.doubleToRawLongBits(live) == expectedBits,
                "R2 VIOLATION at attack '" + name + "': the live answer moved — live=" + live);
    }

    private static void placeItem(ServerLevel world, BlockPos clickedSupport, ItemLike item) {
        Player player = FakePlayerFactory.getMinecraft(world);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(clickedSupport).add(0.0d, 0.5d, 0.0d),
                Direction.UP, clickedSupport, false);
        ForgeHooks.onPlaceItemIntoWorld(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static net.minecraft.world.level.block.state.BlockState bottomSlab() {
        return Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }
}
