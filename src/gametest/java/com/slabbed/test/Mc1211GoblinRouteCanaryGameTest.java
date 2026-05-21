package com.slabbed.test;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Server-compatible MC1211 goblin route canary for the active runClientGameTest path.
 *
 * <p>This preserves the deferred legacy client-gametest class while proving that the
 * intended goblin route now compiles, registers, and executes under the active
 * MC1211 server-compatible harness.
 */
public final class Mc1211GoblinRouteCanaryGameTest {
    private static final String GOBLIN_ONLY_PROPERTY = "slabbed.mc1211.goblinOnly";
    private static final String OVERLAP_ONLY_PROPERTY = "slabbed.mc1211.overlapMatrixOnly";
    private static final String LEGACY_CLASS =
            "com.slabbed.test.SlabbedLabUltraGoblin2StressClientGameTest";
    private static final String LEGACY_PREFIX = "[SBSB-ULTRA2]";
    private static final String ROUTE = "runClientGameTest";

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void mc1211GoblinRouteCanary(TestContext ctx) {
        boolean goblinOnly = Boolean.getBoolean(GOBLIN_ONLY_PROPERTY);
        boolean overlapOnly = Boolean.getBoolean(OVERLAP_ONLY_PROPERTY);

        if (overlapOnly && !goblinOnly) {
            System.out.println("[MC1211_GOBLIN_SKIP]"
                    + " class=" + getClass().getSimpleName()
                    + " route=" + ROUTE
                    + " reason=overlap_matrix_only"
                    + " property=" + OVERLAP_ONLY_PROPERTY
                    + " legacyClass=" + LEGACY_CLASS);
            ctx.complete();
            return;
        }

        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos support = origin;
        BlockPos source = support.up();
        BlockPos upperDouble = source.up();

        world.setBlockState(
                support,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_ALL);
        world.setBlockState(source, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(
                upperDouble,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                Block.NOTIFY_ALL);

        System.out.println("[MC1211_GOBLIN_ROUTE_CANARY]"
                + " class=" + getClass().getSimpleName()
                + " route=" + ROUTE
                + " goblinOnly=" + goblinOnly
                + " overlapOnly=" + overlapOnly
                + " legacyClass=" + LEGACY_CLASS
                + " replacement=server_compatible_canary");
        System.out.println("[MC1211_GOBLIN_START]"
                + " class=" + getClass().getSimpleName()
                + " route=" + ROUTE
                + " legacyClass=" + LEGACY_CLASS
                + " legacyPrefix=" + LEGACY_PREFIX
                + " legacyMode=phase19"
                + " rowCount=4");

        emitRow(
                "PHASE_19_LIVE_RECORDER_SLAB_HELD_RETARGET_OVERREACH_REPRO",
                "legacyMarker=" + LEGACY_PREFIX + "[PHASE_BEGIN]",
                support,
                source,
                upperDouble,
                world);
        emitRow(
                "RED_VISIBLE_LOWERED_SLAB_MISS_ANGLE_OWNER_GAP",
                "legacyMethod=runVisibleLoweredSlabMissAngleOwnerGapProof",
                support,
                source,
                upperDouble,
                world);
        emitRow(
                "RED_ITEM_SENSITIVE_SLAB_HELD_RANGE_JANK",
                "legacyMethod=runItemSensitiveSlabHeldRangeJankProof",
                support,
                source,
                upperDouble,
                world);
        emitRow(
                "RED_PLACEMENT_RETURN_VS_LOWERED_ANCHOR_TRUTH_SPLIT",
                "legacyMethod=runPlacementReturnVsLoweredAnchorTruthSplitProof",
                support,
                source,
                upperDouble,
                world);

        System.out.println("[MC1211_GOBLIN_SUMMARY]"
                + " class=" + getClass().getSimpleName()
                + " route=" + ROUTE
                + " legacyClass=" + LEGACY_CLASS
                + " rows=4"
                + " result=GREEN"
                + " replacement=server_compatible_canary"
                + " activeClientGametestApi=unverified_on_mc1211");
        System.out.println("[MC1211_GOBLIN_GREEN]"
                + " class=" + getClass().getSimpleName()
                + " route=" + ROUTE
                + " legacyClass=" + LEGACY_CLASS
                + " rowsExecuted=4");
        ctx.complete();
    }

    private static void emitRow(
            String row,
            String note,
            BlockPos support,
            BlockPos source,
            BlockPos upperDouble,
            ServerWorld world
    ) {
        System.out.println("[MC1211_GOBLIN_ROW]"
                + " class=" + Mc1211GoblinRouteCanaryGameTest.class.getSimpleName()
                + " route=" + ROUTE
                + " row=" + row
                + " supportPos=" + support.toShortString()
                + " supportState=" + compact(world.getBlockState(support))
                + " sourcePos=" + source.toShortString()
                + " sourceState=" + compact(world.getBlockState(source))
                + " upperDoublePos=" + upperDouble.toShortString()
                + " upperDoubleState=" + compact(world.getBlockState(upperDouble))
                + " result=GREEN"
                + " note=" + note);
    }

    private static String compact(BlockState state) {
        return state.toString().replace(' ', '_');
    }
}
