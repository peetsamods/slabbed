package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

public final class LoweredCarpetVibrationTest {
    private static final ThreadLocal<GameEvent.Context> LAST_STEP = new ThreadLocal<>();
    private static final ThreadLocal<Entity> EXPECTED_ENTITY = new ThreadLocal<>();
    private static final ThreadLocal<java.util.List<GameEvent.Context>> MOVEMENT_STEPS = new ThreadLocal<>();

    public static void capture(Entity source, GameEvent.Context context) {
        if (source == EXPECTED_ENTITY.get()) {
            LAST_STEP.set(context);
            var movementSteps = MOVEMENT_STEPS.get();
            if (movementSteps != null) movementSteps.add(context);
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredWoolCarpetMufflesActualStepEvent(GameTestHelper helper) {
        check(helper, true, -0.5d, 0.0d, false);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bareBottomSlabStillEmitsVibration(GameTestHelper helper) {
        check(helper, false, 0.0d, 0.0d, true);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void carpetAboveFeetDoesNotMuffleSlab(GameTestHelper helper) {
        check(helper, true, 0.0d, 0.0d, true);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredCarpetAwayFromFeetDoesNotMuffle(GameTestHelper helper) {
        check(helper, true, -0.5d, 0.2d, true);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaCarpetStillMuffles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, woolCarpet());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5d, pos.getY() + 0.0625d, pos.getZ() + 0.5d);
        GameEvent.Context event = emitStep(player, pos, level.getBlockState(pos));
        assertVibration(helper, event, false);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void walkingAcrossLoweredCarpetMufflesNativeSteps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean previousFrozen = com.slabbed.anchor.SlabAnchorAttachment.FROZEN_DY_ENABLED;
        com.slabbed.anchor.SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        try {
            for (int x = 1; x <= 5; x++) {
                BlockPos slab = helper.absolutePos(new BlockPos(x, 2, 2));
                level.setBlockAndUpdate(slab, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
                level.setBlockAndUpdate(slab.above(), woolCarpet());
                com.slabbed.anchor.SlabAnchorAttachment.writePlacementDy(level, slab.above(), -0.5d);
            }
            BlockPos start = helper.absolutePos(new BlockPos(1, 2, 2));
            player.setPos(start.getX() + 0.2d, start.getY() + 0.5625d, start.getZ() + 0.5d);
            EXPECTED_ENTITY.set(player);
            LAST_STEP.remove();
            MOVEMENT_STEPS.set(new java.util.ArrayList<>());
            for (int step = 0; step < 35; step++) {
                player.setOnGround(true);
                player.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0.1d, -0.05d, 0.0d));
                if (Math.abs(player.getY() - (start.getY() + 0.5625d)) > 1.0e-6d) {
                    throw helper.assertionException(new BlockPos(1, 2, 2), "Walking must stay on the carpet collision top");
                }
            }
            if (Math.abs(player.getX() - (start.getX() + 3.7d)) > 1.0e-5d
                    || Math.abs(player.getZ() - (start.getZ() + 0.5d)) > 1.0e-5d) {
                throw helper.assertionException(new BlockPos(1, 2, 2), "Walking must cross the whole carpet lane");
            }
            if (MOVEMENT_STEPS.get().isEmpty()) {
                throw helper.assertionException(new BlockPos(1, 2, 2), "Actual player movement must emit at least one STEP");
            }
            for (GameEvent.Context event : MOVEMENT_STEPS.get()) assertVibration(helper, event, false);
            helper.succeed();
        } finally {
            EXPECTED_ENTITY.remove();
            LAST_STEP.remove();
            MOVEMENT_STEPS.remove();
            com.slabbed.anchor.SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
    }

    private static BlockState woolCarpet() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                net.minecraft.resources.Identifier.withDefaultNamespace("white_carpet")).defaultBlockState();
    }

    private static void check(GameTestHelper helper, boolean carpet, double dy,
                              double footDelta, boolean audible) {
        boolean previousFrozen = com.slabbed.anchor.SlabAnchorAttachment.FROZEN_DY_ENABLED;
        com.slabbed.anchor.SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            ServerLevel level = helper.getLevel();
            BlockPos rel = new BlockPos(2, 2, 2);
            BlockPos slab = helper.absolutePos(rel);
            level.setBlockAndUpdate(slab, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
            BlockPos top = slab.above();
            if (carpet) {
                Player placer = helper.makeMockPlayer(GameType.SURVIVAL);
                placer.setPos(slab.getX() + 0.5d, slab.getY() + 2.0d, slab.getZ() + 0.5d);
                ItemStack stack = new ItemStack(woolCarpet().getBlock());
                placer.setItemInHand(InteractionHand.MAIN_HAND, stack);
                var result = stack.useOn(new UseOnContext(placer, InteractionHand.MAIN_HAND,
                        new BlockHitResult(new Vec3(slab.getX() + 0.5d, slab.getY() + 0.5d,
                                slab.getZ() + 0.5d), Direction.UP, slab, false)));
                if (!result.consumesAction() || !level.getBlockState(top).is(woolCarpet().getBlock())
                        || Math.abs(SlabSupport.getYOffset(level, top, level.getBlockState(top)) + 0.5d) > 1.0e-6d) {
                    throw helper.assertionException(rel, "Real carpet placement must produce the lowered seat");
                }
                if (dy == 0.0d) com.slabbed.anchor.SlabAnchorAttachment.writePlacementDy(level, top, 0.0d);
            }
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            double footY = slab.getY() + 0.5d + (carpet ? 0.0625d : 0.0d) + footDelta;
            player.setPos(slab.getX() + 0.5d, footY, slab.getZ() + 0.5d);
            GameEvent.Context event = emitStep(player, slab, level.getBlockState(slab));
            assertVibration(helper, event, audible);
            if (carpet && Math.abs(SlabSupport.getYOffset(level, top, level.getBlockState(top)) - dy) > 1.0e-6d) {
                throw helper.assertionException(rel, "Footstep handling must not change the remembered carpet seat");
            }
            helper.succeed();
        } finally {
            com.slabbed.anchor.SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
    }

    private static GameEvent.Context emitStep(Player player, BlockPos pos, BlockState state) {
        EXPECTED_ENTITY.set(player);
        LAST_STEP.remove();
        player.setOnGround(true);
        try {
            Method method = Entity.class.getDeclaredMethod("vibrationAndSoundEffectsFromBlock",
                    BlockPos.class, BlockState.class, boolean.class, boolean.class, Vec3.class);
            method.setAccessible(true);
            if (!Boolean.TRUE.equals(method.invoke(player, pos, state, false, true, new Vec3(0.3d, 0.0d, 0.0d)))) {
                throw new AssertionError("Native footstep emission did not execute");
            }
            GameEvent.Context event = LAST_STEP.get();
            if (event == null) throw new AssertionError("No real STEP dispatch was captured");
            return event;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not drive native footstep emission", failure);
        } finally {
            EXPECTED_ENTITY.remove();
            LAST_STEP.remove();
        }
    }

    private static void assertVibration(GameTestHelper helper, GameEvent.Context event, boolean expected) {
        SculkSensorBlockEntity sensor = new SculkSensorBlockEntity(helper.absolutePos(new BlockPos(4, 2, 2)),
                Blocks.SCULK_SENSOR.defaultBlockState());
        boolean actual = sensor.getVibrationUser().isValidVibration(GameEvent.STEP, event);
        if (actual != expected) {
            throw helper.assertionException(new BlockPos(2, 2, 2),
                    "Native sensor footstep acceptance expected=" + expected + " actual=" + actual
                            + " emittedState=" + event.affectedState());
        }
    }
}
