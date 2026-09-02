package com.slabbed.test;

import com.slabbed.dev.SlabbedTestAccess;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * DY_SPEC {@code OPEN-MINUS1}, CLOSED (maintainer ruling, 2026-09-01: WYSIWYG at any depth):
 * side-clicking a −1.0-lowered slab lands the new slab at the clicked face's −1.0 exactly.
 * The class name records the row's origin — this began as the measurement that exposed the
 * pre-ruling grid landing — and is kept for suite-history continuity.
 *
 * <p>Ruled (maintainer ruling, 2026-09-01): WYSIWYG applies at any depth — the landing takes
 * the clicked face's height exactly. This row asserts the ruling; the 0.0 grid landing it
 * originally pinned was the measured pre-ruling gap.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class OpenMinus1MeasurementTest {
    private static final String TEMPLATE = "empty";
    private static final double EPS = 1.0e-6;

    @GameTest(template = TEMPLATE)
    public void sideClickOnMinus1SlabExtendsAtMinus1(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos anchor = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos landing = anchor.east();

        // Floor under both cells so the placement has real support scenery.
        world.setBlockAndUpdate(anchor.below(), Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(landing.below(), Blocks.STONE.defaultBlockState());

        BlockState bottomSlab = Blocks.OAK_SLAB.defaultBlockState();
        world.setBlockAndUpdate(anchor, bottomSlab);

        // Author the clicked slab's fact directly: −1.0 is −2 half-steps.
        Long2ByteOpenHashMap facts = new Long2ByteOpenHashMap();
        facts.put(anchor.asLong(), (byte) -2);
        SlabbedTestAccess.putPlacementFacts(world.getChunkAt(anchor), facts);
        double authored = SlabSupport.getYOffset(world, anchor, world.getBlockState(anchor));
        ctx.assertTrue(Math.abs(authored - (-1.0d)) < EPS,
                "fixture must resolve at -1.0, got " + authored);

        // Side-click the slab's east face where it is DRAWN: the −1.0 fact moves the visible
        // face a full block down, and the offset-aware raycast reports hits on the visual
        // geometry. A grid-height hit here would be a click no player can make.
        Player player = ctx.makeMockPlayer();
        player.setPos(landing.getX() + 0.5d, landing.getY() + 2.0d, landing.getZ() + 0.5d);
        ItemStack stack = new ItemStack(bottomSlab.getBlock());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = new Vec3(anchor.getX() + 1.0d, anchor.getY() + 0.2d - 1.0d, anchor.getZ() + 0.5d);
        InteractionResult result = stack.useOn(new UseOnContext(
                player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.EAST, anchor, false)));
        ctx.assertTrue(result.consumesAction(), "side-click placement must be accepted");

        BlockState landed = world.getBlockState(landing);
        ctx.assertTrue(landed.getBlock() instanceof SlabBlock,
                "expected a slab in the landing cell, got " + landed);
        double dy = SlabSupport.getYOffset(world, landing, landed);
        ctx.assertTrue(Math.abs(dy - (-1.0d)) < EPS,
                "OPEN-MINUS1: landing dy = " + dy + " (ruled EXTEND = -1.0)");
        ctx.succeed();
    }
}
