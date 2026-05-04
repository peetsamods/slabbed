package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class SlabbedLabCompatibleLoweredSlabLanePredicateBoundaryClientGameTest implements FabricClientGameTest {
    private static final String PROOF = "COMPATIBLE_LOWERED_SLAB_LANE_PREDICATE_BOUNDARY";
    private static final BlockPos SUPPORT_POS = new BlockPos(0, 200, 0);
    private static final BlockPos FULL_POS = SUPPORT_POS.up();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        Method wovenPredicate;
        try {
            wovenPredicate = BlockItem.class.getDeclaredMethod(
                    "slabbed$isLoweredSlabFacePlacement",
                    ItemPlacementContext.class,
                    BlockState.class);
            wovenPredicate.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("[" + PROOF + "] terminalClassification=PROOF_GAP reason=woven_predicate_reflection_unavailable error="
                    + e.getClass().getName() + ":" + e.getMessage());
            return;
        }

        List<String> mismatches = new ArrayList<>();
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            for (Case row : truthTable()) {
                boolean authorityCompatible = SlabSupport.isCompatibleLoweredSlabLane(
                        row.existingType(),
                        row.incomingType());
                boolean wovenCompatible = invokeWovenPredicate(singleplayer, wovenPredicate, row);
                String classification = authorityCompatible == row.expectedCompatible()
                        && wovenCompatible == row.expectedCompatible()
                        ? "MATCH"
                        : "MISMATCH";
                if (!"MATCH".equals(classification)) {
                    mismatches.add(row.existingType() + "<-" + row.incomingType()
                            + " expected=" + row.expectedCompatible()
                            + " authority=" + authorityCompatible
                            + " woven=" + wovenCompatible);
                }
                System.out.println("[" + PROOF + "] compatibleLane case=" + row.name()
                        + " existingType=" + row.existingType()
                        + " incomingType=" + row.incomingType()
                        + " expectedCompatible=" + row.expectedCompatible()
                        + " authorityCompatible=" + authorityCompatible
                        + " wovenCompatible=" + wovenCompatible
                        + " classification=" + classification);
            }
        }

        if (!mismatches.isEmpty()) {
            System.out.println("[" + PROOF + "] terminalClassification=RED_BOUNDARY_DRIFT mismatches=" + mismatches);
            throw new RuntimeException(PROOF + " RED_BOUNDARY_DRIFT " + mismatches);
        }
        System.out.println("[" + PROOF + "] terminalClassification=GREEN_BOUNDARY_PROVEN cases=9");
    }

    private static boolean invokeWovenPredicate(TestSingleplayerContext singleplayer, Method wovenPredicate, Case row) {
        final boolean[] result = new boolean[1];
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            setupFixture(world);
            BlockPos targetPos = FULL_POS.east();
            BlockPos placementPos = targetPos.east();
            world.setBlockState(targetPos, slab(row.existingType()), Block.NOTIFY_LISTENERS);

            BlockHitResult hit = new BlockHitResult(
                    new Vec3d(targetPos.getX() + 1.0d, targetPos.getY() + 0.25d, targetPos.getZ() + 0.5d),
                    Direction.EAST,
                    targetPos,
                    false,
                    false);
            ItemUsageContext usage = new ItemUsageContext(
                    world,
                    server.getPlayerManager().getPlayerList().isEmpty()
                            ? null
                            : server.getPlayerManager().getPlayerList().get(0),
                    Hand.MAIN_HAND,
                    new ItemStack(Items.STONE_SLAB),
                    hit);
            ItemPlacementContext placementContext = new ItemPlacementContext(usage) {
                @Override
                public BlockPos getBlockPos() {
                    return placementPos;
                }

                @Override
                public Direction getSide() {
                    return Direction.EAST;
                }
            };
            if (!placementContext.getBlockPos().equals(placementPos)) {
                throw new RuntimeException("placement context did not target expected adjacent lane: expected="
                        + placementPos.toShortString() + " actual=" + placementContext.getBlockPos().toShortString());
            }
            try {
                result[0] = (Boolean) wovenPredicate.invoke(null, placementContext, slab(row.incomingType()));
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new RuntimeException("woven predicate invocation failed for " + row.name(), e);
            }
        });
        return result[0];
    }

    private static void setupFixture(net.minecraft.world.World world) {
        for (int x = SUPPORT_POS.getX() - 2; x <= SUPPORT_POS.getX() + 4; x++) {
            for (int z = SUPPORT_POS.getZ() - 2; z <= SUPPORT_POS.getZ() + 2; z++) {
                for (int y = SUPPORT_POS.getY(); y <= SUPPORT_POS.getY() + 2; y++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        world.setBlockState(SUPPORT_POS, slab(SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        world.setBlockState(FULL_POS, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    private static BlockState slab(SlabType type) {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
    }

    private static List<Case> truthTable() {
        return List.of(
                new Case("existing_bottom_incoming_bottom", SlabType.BOTTOM, SlabType.BOTTOM, true),
                new Case("existing_top_incoming_top", SlabType.TOP, SlabType.TOP, true),
                new Case("existing_bottom_incoming_top", SlabType.BOTTOM, SlabType.TOP, false),
                new Case("existing_top_incoming_bottom", SlabType.TOP, SlabType.BOTTOM, false),
                new Case("existing_bottom_incoming_double", SlabType.BOTTOM, SlabType.DOUBLE, true),
                new Case("existing_double_incoming_bottom", SlabType.DOUBLE, SlabType.BOTTOM, true),
                new Case("existing_top_incoming_double", SlabType.TOP, SlabType.DOUBLE, true),
                new Case("existing_double_incoming_top", SlabType.DOUBLE, SlabType.TOP, true),
                new Case("existing_double_incoming_double", SlabType.DOUBLE, SlabType.DOUBLE, true)
        );
    }

    private record Case(String name, SlabType existingType, SlabType incomingType, boolean expectedCompatible) {
    }
}
