package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * The headless useOn harness — this line's proven way to drive the REAL placement chain
 * ({@code BlockItem.useOnBlock} → intent mixin → {@code canReplace} → place) from a server
 * gametest, instead of a synthetic {@code setBlockState} that skips every placement decision.
 *
 * <p>Extracted from {@code UseOnCombineVsExtendPlacementTest}, where it lived as package-visible
 * statics that four other suites reached into; it is infrastructure, so it now has an
 * infrastructure home. Same shape as the 26.1.2 and Forge 1.20.1 harnesses, re-derived for
 * Yarn/Fabric 1.21.11.
 *
 * <p>Use via {@code import static com.slabbed.test.PlacementHarness.*;}.
 */
final class PlacementHarness {

    private PlacementHarness() {
    }

    /** Mock survival player holding 16 oak slabs, stood at {@code standAbs}. */
    static PlayerEntity mockSlabPlayer(TestContext ctx, BlockPos standAbs) {
        return mockPlayerHolding(ctx, standAbs, new ItemStack(Blocks.OAK_SLAB.asItem(), 16));
    }

    /** Mock survival player holding an arbitrary stack, stood at {@code standAbs}. */
    static PlayerEntity mockPlayerHolding(TestContext ctx, BlockPos standAbs, ItemStack stack) {
        PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
        player.refreshPositionAndAngles(standAbs.getX() + 0.5, standAbs.getY(), standAbs.getZ() + 0.5, 0.0f, 0.0f);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        return player;
    }

    /**
     * Fires the REAL useOn chain for whatever is in {@code Hand.MAIN_HAND}
     * (BlockItem.useOnBlock → intent mixin → canReplace → place).
     */
    static ActionResult useHeldItem(ServerWorld world, PlayerEntity player,
                                    BlockPos clickAbs, Direction face, Vec3d hitAbs) {
        ItemStack held = player.getStackInHand(Hand.MAIN_HAND);
        BlockHitResult hit = new BlockHitResult(hitAbs, face, clickAbs, false);
        return held.useOnBlock(new ItemUsageContext(world, player, Hand.MAIN_HAND, held, hit));
    }

    /** Historical name for {@link #useHeldItem} — item-agnostic; kept for readable call sites. */
    static ActionResult useHeldOakSlab(ServerWorld world, PlayerEntity player,
                                       BlockPos clickAbs, Direction face, Vec3d hitAbs) {
        return useHeldItem(world, player, clickAbs, face, hitAbs);
    }

    /** One-line cell description: block[, slab type], live dy. */
    static String describe(ServerWorld world, BlockPos pos) {
        BlockState s = world.getBlockState(pos);
        String base = s.getBlock().toString();
        if (s.contains(SlabBlock.TYPE)) {
            base += "[" + s.get(SlabBlock.TYPE) + "]";
        }
        return base + " dy=" + SlabSupport.getYOffset(world, pos, s);
    }

    /** Greppable {@code [USEON]} row line for a placement attempt. */
    static void row(String cfg, ServerWorld world, BlockPos clicked, BlockPos side, ActionResult result) {
        System.out.println("[USEON] " + cfg
                + " | clicked=" + describe(world, clicked)
                + " | side=" + describe(world, side)
                + " | result=" + result);
    }
}
