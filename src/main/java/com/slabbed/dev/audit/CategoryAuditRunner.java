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
 * Three verdicts per lane:
 *   placementPass      — canPlaceAt returns true before any block is set
 *   neighborUpdatePass — block remains and still satisfies the probe after a controlled neighbor update
 *   reloadPass         — block persists through a save flush and post-save re-check
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
                report.add(new CategoryAuditResult(
                        categoryId,
                        lane,
                        false,
                        false,
                        false,
                        AuditFailureStage.PLACEMENT,
                        AuditFailureReason.EXCEPTION,
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

            boolean placementPass = false;
            boolean neighborUpdatePass = false;
            boolean reloadPass = false;
            AuditFailureStage failureStage = AuditFailureStage.NONE;
            AuditFailureReason failureReason = AuditFailureReason.NONE;
            String notes = auditCase.expectedSupportKind();

            try {
                // --- PLACEMENT VERDICT ---
                BlockState probeState = probe.getProbeState(world, candidatePos);
                if (probeState == null) {
                    failureStage = AuditFailureStage.PLACEMENT;
                    failureReason = AuditFailureReason.STATE_NULL_ON_PLACEMENT;
                    notes += " [probe state null]";
                } else {
                    placementPass = probe.canAttemptPlacement(world, candidatePos);
                    if (!placementPass) {
                        failureStage = AuditFailureStage.PLACEMENT;
                        failureReason = AuditFailureReason.CANNOT_PLACE_AT_FALSE;
                        notes += " [placement blocked]";
                    } else {
                        world.setBlockState(candidatePos, probeState, Block.NOTIFY_ALL);
                        boolean placed = world.getBlockState(candidatePos).isOf(probeState.getBlock());
                        if (!placed) {
                            failureStage = AuditFailureStage.PLACEMENT;
                            failureReason = AuditFailureReason.STATE_NULL_ON_PLACEMENT;
                            notes += " [state missing right after placement]";
                        } else {
                            // --- NEIGHBOR-UPDATE VERDICT ---
                            world.setBlockState(supportPos, supportState, Block.NOTIFY_ALL);
                            world.updateNeighborsAlways(candidatePos, supportState.getBlock(), null);
                            boolean stillPresent = world.getBlockState(candidatePos).isOf(probeState.getBlock());
                            boolean stillValid = stillPresent && probe.survivesAfterPlacement(world, candidatePos, probeState);
                            neighborUpdatePass = stillPresent && stillValid;

                            if (!neighborUpdatePass) {
                                failureStage = AuditFailureStage.NEIGHBOR_UPDATE;
                                failureReason = stillPresent
                                        ? AuditFailureReason.VANILLA_SUPPORT_PATH_STILL_ACTIVE
                                        : AuditFailureReason.BLOCK_DROPPED_AFTER_NEIGHBOR_UPDATE;
                                notes += " [failed after neighbor update]";
                            } else {
                                // --- RELOAD-PERSISTENCE VERDICT ---
                                reloadPass = runReloadPersistenceCheck(world, candidatePos, probeState, probe);
                                if (!reloadPass) {
                                    failureStage = AuditFailureStage.RELOAD;
                                    failureReason = AuditFailureReason.BLOCK_MISSING_AFTER_RELOAD;
                                    notes += " [missing after reload check]";
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (failureStage == AuditFailureStage.NONE) {
                    failureStage = AuditFailureStage.PLACEMENT;
                }
                failureReason = AuditFailureReason.EXCEPTION;
                notes += " [exception: " + e.getClass().getSimpleName() + "]";
            } finally {
                world.setBlockState(candidatePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(supportPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }

            report.add(new CategoryAuditResult(
                    categoryId,
                    auditCase.lane(),
                    placementPass,
                    neighborUpdatePass,
                    reloadPass,
                    failureStage,
                    failureReason,
                    notes));

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

    private static boolean runReloadPersistenceCheck(ServerWorld world, BlockPos candidatePos, BlockState expected, CategoryProbe probe) {
        // v2 conservative path: flush world save, then re-fetch state and probe survival conditions.
        world.getServer().save(false, true, false);
        BlockState persisted = world.getBlockState(candidatePos);
        if (!persisted.isOf(expected.getBlock())) {
            return false;
        }
        return probe.survivesAfterPlacement(world, candidatePos, persisted);
    }
}
