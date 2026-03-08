package com.slabbed.dev.audit;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

/**
 * Runs a headless category audit against a 3-lane test matrix:
 *   Lane FULL_BLOCK  — stone support (vanilla baseline)
 *   Lane BOTTOM_SLAB — oak bottom slab (Slabbed slab support)
 *   Lane TOP_SLAB    — oak top slab   (Slabbed slab support)
 *
 * Each lane is placed at a fixed offset from the supplied anchor position.
 * Support blocks are cleared and replaced per-lane; the area is cleaned up
 * after each evaluation so the test leaves no permanent world state.
 *
 * Two verdicts per lane:
 *   placementPass — canPlaceAt returns true before any block is set
 *   survivalPass  — canPlaceAt returns true after the block is placed
 *                   (mirrors the logic in CarpetBlockMixin.getStateForNeighborUpdate)
 *
 * TODO(next slice): trigger an actual getStateForNeighborUpdate call via
 *   world.updateBlock to capture survival failures caused by scheduled ticks.
 * TODO(next slice): add reload/relog verification lane.
 */
public class CategoryAuditRunner {

    private static final Map<String, CategoryProbe> PROBES = Map.of(
            "carpet", new CarpetCategoryProbe()
    );

    /** X spacing between lanes, in blocks. */
    private static final int LANE_SPACING = 3;

    /** How many blocks above the support base to clear before each test. */
    private static final int CLEAR_HEIGHT = 4;

    /**
     * @param world      the server world to run the test in
     * @param anchor     base position for lane 0 (FULL_BLOCK); other lanes offset +X
     * @param categoryId registered probe id (e.g. "carpet")
     */
    public static CategoryAuditReport run(ServerWorld world, BlockPos anchor, String categoryId) {
        CategoryAuditReport report = new CategoryAuditReport(categoryId);

        CategoryProbe probe = PROBES.get(categoryId);
        if (probe == null) {
            for (TestLane lane : TestLane.values()) {
                report.add(new CategoryAuditResult(categoryId, lane, false, false,
                        "Unknown category: " + categoryId));
            }
            return report;
        }

        List<CategoryAuditCase> cases = List.of(
                new CategoryAuditCase(categoryId, TestLane.FULL_BLOCK,
                        "minecraft:stone", "vanilla baseline"),
                new CategoryAuditCase(categoryId, TestLane.BOTTOM_SLAB,
                        "minecraft:oak_slab[type=bottom]", "slab support via SlabSupport"),
                new CategoryAuditCase(categoryId, TestLane.TOP_SLAB,
                        "minecraft:oak_slab[type=top]", "slab support via SlabSupport")
        );

        int laneIdx = 0;
        for (CategoryAuditCase auditCase : cases) {
            BlockPos supportPos   = anchor.add(laneIdx * LANE_SPACING, 0, 0);
            BlockPos candidatePos = supportPos.up();

            BlockState supportState = supportStateFor(auditCase.lane());

            // clear column so previous blocks don't affect this lane
            clearColumn(world, supportPos, CLEAR_HEIGHT);

            // place the support block quietly (no neighbor cascade during setup)
            world.setBlockState(supportPos, supportState, Block.NOTIFY_LISTENERS);

            // --- PLACEMENT VERDICT ---
            BlockState probeState = probe.getProbeState(world, candidatePos);
            boolean placementPass = probe.canAttemptPlacement(world, candidatePos);

            // --- SURVIVAL VERDICT ---
            boolean survivalPass = false;
            String notes = auditCase.expectedSupportKind();

            if (placementPass) {
                world.setBlockState(candidatePos, probeState, Block.NOTIFY_LISTENERS);
                survivalPass = probe.survivesAfterPlacement(world, candidatePos, probeState);
                world.setBlockState(candidatePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            } else {
                notes += " [placement blocked]";
            }

            // restore support slot to air
            world.setBlockState(supportPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);

            report.add(new CategoryAuditResult(categoryId, auditCase.lane(),
                    placementPass, survivalPass, notes));

            laneIdx++;
        }

        return report;
    }

    private static BlockState supportStateFor(TestLane lane) {
        return switch (lane) {
            case FULL_BLOCK  -> Blocks.STONE.getDefaultState();
            case BOTTOM_SLAB -> Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);
            case TOP_SLAB    -> Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
        };
    }

    private static void clearColumn(ServerWorld world, BlockPos base, int height) {
        for (int dy = 0; dy < height; dy++) {
            world.setBlockState(base.up(dy), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
    }
}
