package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.test.mixin.AbstractArrowLifecycleAccessor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.slabbed.util.SlabbedOffsetColliderClip;
import net.minecraft.world.phys.Vec3;

/**
 * An arrow that lands on a lowered leaf block must come to rest exactly once, on the drawn surface,
 * and leave by the vanilla despawn timer. The flying clip is already offset-aware; this row watches
 * the resting state afterwards (in-ground stability, life counting, natural removal) because a
 * clip/collision disagreement would show up there as an arrow that re-falls every tick and never
 * despawns. The ordinary-height row is the control.
 *
 * <p>Boundary: these rows run under whatever {@code slabbed.frozenDy} the suite was started with
 * (the root server suite defaults it to false; the shipped default is true). A claim about the
 * shipped store path needs the suite run with {@code -Dslabbed.frozenDy=true}; the rows do not
 * flip the flag themselves because other rows share the tick.
 */
public final class ArrowLoweredLeavesLifecycleTest {
    /** Vanilla removes a resting arrow once its life reaches this many ticks. */
    private static final int VANILLA_DESPAWN_LIFE = 1200;
    private static final int SETTLE_TICKS = 60;
    private static final int MAX_TICKS = 1500;

    private static void require(GameTestHelper helper, boolean value, String message) {
        if (!value) {
            throw helper.assertionException(message);
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = MAX_TICKS)
    public void arrowOnLoweredLeavesRestsOnceAndDespawns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(support, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 3);
        BlockPos leaves = placeLeavesByUse(helper, level, support);
        double storedDy = SlabAnchorAttachment.storedPlacementDy(level, leaves);
        require(helper, Double.doubleToRawLongBits(storedDy) == Double.doubleToRawLongBits(-0.5d),
                "premise: leaves placed on a bottom slab must be stored lowered by -0.5, got " + storedDy);
        // Drawn top of the lowered leaves: cell floor + 1 - 0.5.
        runLifecycle(helper, level, leaves, leaves.getY() + 0.5d, "lowered");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = MAX_TICKS)
    public void arrowOnOrdinaryLeavesRestsOnceAndDespawns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        BlockPos leaves = placeLeavesByUse(helper, level, support);
        require(helper, Double.isNaN(SlabAnchorAttachment.storedPlacementDy(level, leaves))
                        || SlabAnchorAttachment.storedPlacementDy(level, leaves) == 0.0d,
                "premise: leaves on a full block must not be lowered");
        runLifecycle(helper, level, leaves, leaves.getY() + 1.0d, "ordinary");
    }

    private static String bounds(VoxelShape shape) {
        return shape.isEmpty() ? "empty"
                : String.format("[%.2f,%.2f]", shape.min(net.minecraft.core.Direction.Axis.Y), shape.max(net.minecraft.core.Direction.Axis.Y));
    }

    private static String describe(BlockHitResult hit) {
        if (hit == null) {
            return "null";
        }
        return hit.getType() + "@" + String.format("%.3f", hit.getLocation().y)
                + (hit.getType() == HitResult.Type.BLOCK && hit.isInside() ? "(inside)" : "")
                + "cell" + hit.getBlockPos().getY();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = MAX_TICKS)
    public void arrowOnLoweredStoneRestsOnceAndDespawns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(support, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 3);
        BlockPos stone = placeByUse(helper, level, support, Items.STONE, Blocks.STONE);
        double storedDy = SlabAnchorAttachment.storedPlacementDy(level, stone);
        require(helper, Double.doubleToRawLongBits(storedDy) == Double.doubleToRawLongBits(-0.5d),
                "premise: stone placed on a bottom slab must be stored lowered by -0.5, got " + storedDy);
        runLifecycle(helper, level, stone, stone.getY() + 0.5d, "lowered-stone");
    }

    /** Places oak leaves on the support's top face through the real item-use path, like a player. */
    private static BlockPos placeLeavesByUse(GameTestHelper helper, ServerLevel level, BlockPos support) {
        return placeByUse(helper, level, support, Items.OAK_LEAVES, Blocks.OAK_LEAVES);
    }

    private static BlockPos placeByUse(GameTestHelper helper, ServerLevel level, BlockPos support,
                                       net.minecraft.world.item.Item item, net.minecraft.world.level.block.Block block) {
        Player mock = helper.makeMockServerPlayer(GameType.SURVIVAL);
        require(helper, mock instanceof ServerPlayer, "premise: placement needs a server player");
        ServerPlayer player = (ServerPlayer) mock;
        ItemStack held = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockState supportState = level.getBlockState(support);
        double top = supportState.getBlock() instanceof SlabBlock
                && supportState.getValue(SlabBlock.TYPE) == SlabType.BOTTOM ? 0.5d : 1.0d;
        Vec3 hit = new Vec3(support.getX() + 0.5d, support.getY() + top, support.getZ() + 0.5d);
        player.setPos(hit.x + 2.0d, support.getY(), hit.z + 2.0d);
        player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, support, false));
        BlockPos leaves = support.above();
        require(helper, level.getBlockState(leaves).is(block),
                "premise: the block must be placed above the support, found " + level.getBlockState(leaves));
        // The placing player leaves the scene so it can neither pick the arrow up nor be hit by it.
        player.setPos(hit.x + 40.0d, support.getY(), hit.z + 40.0d);
        return leaves;
    }

    private static void runLifecycle(GameTestHelper helper, ServerLevel level, BlockPos leaves,
                                     double drawnTop, String label) {
        Arrow arrow = new Arrow(level, leaves.getX() + 0.5d, leaves.getY() + 4.0d, leaves.getZ() + 0.5d,
                new ItemStack(Items.ARROW), null);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(arrow);
        AbstractArrowLifecycleAccessor view = (AbstractArrowLifecycleAccessor) arrow;

        int[] inGroundFlips = {0};
        boolean[] wasInGround = {false};
        double[] restY = {Double.NaN};
        double[] restYMin = {Double.POSITIVE_INFINITY};
        double[] restYMax = {Double.NEGATIVE_INFINITY};
        int[] lifeAtSettle = {-1};
        int[] lifeDecreases = {0};
        int[] lastLife = {Integer.MIN_VALUE};
        long[] startTick = {level.getGameTime()};
        StringBuilder trace = new StringBuilder();
        int[] traced = {0};

        helper.onEachTick(() -> {
            if (arrow.isRemoved()) {
                return;
            }
            boolean inGround = view.slabbed$isInGround();
            long elapsed = level.getGameTime() - startTick[0];
            if (traced[0] < 40) {
                traced[0]++;
                Vec3 from = arrow.position();
                Vec3 to = from.add(arrow.getDeltaMovement());
                String clipNote = "";
                if (!from.equals(to)) {
                    ClipContext probe = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow);
                    BlockHitResult vanilla = level.clip(probe);
                    BlockHitResult slabbed = SlabbedOffsetColliderClip.clipForProjectile(level, probe, vanilla);
                    clipNote = " v=" + describe(vanilla) + " s=" + describe(slabbed);
                }
                // Discriminator: the two-argument (state-cache) collision shape the arrow's embedded-body
                // probe reads versus the three-argument shape every other collision reader gets.
                BlockPos cell = arrow.blockPosition();
                BlockState cellState = level.getBlockState(cell);
                String shapeNote = "";
                if (!cellState.isAir()) {
                    VoxelShape twoArg = cellState.getCollisionShape(level, cell);
                    VoxelShape threeArg = cellState.getCollisionShape(level, cell, CollisionContext.empty());
                    shapeNote = " cell" + cell.getY() + " two=" + bounds(twoArg) + " three=" + bounds(threeArg);
                }
                trace.append(' ').append(elapsed).append(':').append(String.format("%.3f", arrow.getY()))
                        .append(inGround ? "g" : "a").append('/').append(view.slabbed$life())
                        .append(" dy=").append(String.format("%.3f", arrow.getDeltaMovement().y)).append(clipNote)
                        .append(shapeNote);
            }
            if (elapsed > SETTLE_TICKS) {
                if (wasInGround[0] && !inGround) {
                    inGroundFlips[0]++;
                }
                restYMin[0] = Math.min(restYMin[0], arrow.getY());
                restYMax[0] = Math.max(restYMax[0], arrow.getY());
                int life = view.slabbed$life();
                if (lastLife[0] != Integer.MIN_VALUE && life < lastLife[0]) {
                    lifeDecreases[0]++;
                }
                lastLife[0] = life;
                if (lifeAtSettle[0] < 0) {
                    lifeAtSettle[0] = life;
                    restY[0] = arrow.getY();
                }
            }
            wasInGround[0] = inGround;
        });

        helper.startSequence()
                .thenExecuteAfter(SETTLE_TICKS, () -> {
                    helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "[ARROW_TRACE] " + label + trace));
                    require(helper, !arrow.isRemoved(), label + ": the arrow must still exist after settling");
                    require(helper, view.slabbed$isInGround(), label + ": the arrow must be in the ground after "
                            + SETTLE_TICKS + " ticks (y=" + arrow.getY() + ") trace:" + trace);
                    double y = arrow.getY();
                    require(helper, Math.abs(y - drawnTop) <= 0.2d, label + ": the arrow must rest on the drawn top "
                            + drawnTop + ", rests at " + y);
                })
                .thenWaitUntil(() -> require(helper, arrow.isRemoved(), label + ": waiting for the vanilla despawn"))
                .thenExecute(() -> {
                    long elapsed = level.getGameTime() - startTick[0];
                    require(helper, inGroundFlips[0] == 0, label + ": a resting arrow must not leave the ground again ("
                            + inGroundFlips[0] + " re-falls after settling)");
                    require(helper, lifeDecreases[0] == 0, label + ": life must only count up while resting ("
                            + lifeDecreases[0] + " resets)");
                    require(helper, restYMax[0] - restYMin[0] <= 0.05d, label + ": a resting arrow must not move ("
                            + restYMin[0] + " .. " + restYMax[0] + ")");
                    // Removal must be the vanilla timer itself: life reached exactly 1200 (the accessor stays
                    // readable on the removed entity) and no earlier than the timer allows.
                    require(helper, view.slabbed$life() == VANILLA_DESPAWN_LIFE,
                            label + ": removal must come from the life timer, life=" + view.slabbed$life());
                    require(helper, elapsed >= VANILLA_DESPAWN_LIFE
                                    && elapsed <= VANILLA_DESPAWN_LIFE + SETTLE_TICKS + 60,
                            label + ": removal must follow the vanilla life timer, took " + elapsed + " ticks");
                    helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "[ARROW_LIFECYCLE] " + label + " restY=" + restY[0] + " removedAfter=" + elapsed
                                    + " flips=" + inGroundFlips[0] + " lifeAtSettle=" + lifeAtSettle[0]));
                })
                .thenSucceed();
    }
}
