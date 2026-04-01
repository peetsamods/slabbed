package com.slabbed.dev;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fixture generation for Slabbed Lab dev commands.
 * Server-side only; no gameplay logic is modified.
 */
public final class SlabbedLabFixtures {

    private SlabbedLabFixtures() {}

    /** X-spacing between lane centres, in blocks. */
    private static final int LANE_STRIDE = 2;

    /**
     * Blocks checked above each support position before placement.
     * Includes the support block itself (dy=0) through dy=CHECK_HEIGHT.
     */
    private static final int CHECK_HEIGHT = 2;

    // -------------------------------------------------------------------------
    // Canonical footprint definition — shared by place and clear.
    // Both operations must derive from these methods to stay in sync.
    // -------------------------------------------------------------------------

    /**
     * Returns the canonical lane definitions for the basic fixture, in display order.
     * Lane name → expected {@link BlockState} at that lane's support position.
     */
    private static LinkedHashMap<String, BlockState> buildLanes() {
        LinkedHashMap<String, BlockState> lanes = new LinkedHashMap<>();
        lanes.put("FULL",        Blocks.STONE.getDefaultState());
        lanes.put("BOTTOM_SLAB", Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        lanes.put("TOP_SLAB",    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));
        return lanes;
    }

    /**
     * Resolves lane support positions from {@code origin}, in the same order as {@link #buildLanes()}.
     * Lane index × {@value LANE_STRIDE} blocks in +X.
     */
    private static LinkedHashMap<String, BlockPos> buildPositions(BlockPos origin) {
        LinkedHashMap<String, BlockPos> positions = new LinkedHashMap<>();
        int idx = 0;
        for (String name : buildLanes().keySet()) {
            positions.put(name, origin.add(idx * LANE_STRIDE, 0, 0));
            idx++;
        }
        return positions;
    }

    // -------------------------------------------------------------------------
    // Public fixture operations
    // -------------------------------------------------------------------------

    /**
     * Places a compact 3-lane repro pad at {@code origin}.
     *
     * <p>Lanes (1 block each, separated by {@value LANE_STRIDE} blocks in +X):
     * <ul>
     *   <li>FULL       — stone (vanilla baseline)
     *   <li>BOTTOM_SLAB — stone slab, type=bottom
     *   <li>TOP_SLAB    — stone slab, type=top
     * </ul>
     *
     * <p>The entire footprint plus {@value CHECK_HEIGHT} blocks above each support
     * are checked for occupancy before any block is placed. Aborts atomically on conflict.
     *
     * @return {@link PlaceResult} indicating success or the reason for abort
     */
    public static PlaceResult placeBasicFixture(ServerWorld world, BlockPos origin) {
        LinkedHashMap<String, BlockState> lanes     = buildLanes();
        LinkedHashMap<String, BlockPos>   positions = buildPositions(origin);

        // Safety scan: all positions × (support + CHECK_HEIGHT above) must be air.
        // Full scan before any edit — no partial world state on abort.
        for (Map.Entry<String, BlockPos> entry : positions.entrySet()) {
            for (int dy = 0; dy <= CHECK_HEIGHT; dy++) {
                BlockPos check = entry.getValue().up(dy);
                if (!world.getBlockState(check).isAir()) {
                    return PlaceResult.fail(
                            "area occupied at " + check.toShortString()
                            + " (lane " + entry.getKey() + ", dy=" + dy + "). No blocks placed.");
                }
            }
        }

        // Place support blocks quietly — no neighbor cascade during fixture setup.
        for (Map.Entry<String, BlockState> entry : lanes.entrySet()) {
            world.setBlockState(positions.get(entry.getKey()), entry.getValue(), Block.NOTIFY_LISTENERS);
        }

        return PlaceResult.success(origin, positions);
    }

    /**
     * Removes the basic fixture at {@code origin} using exact-match verification.
     *
     * <p>Each support position is checked against its expected {@link BlockState} before
     * any block is removed. If any position does not match exactly, the operation aborts
     * with no world edits.
     *
     * @return {@link PlaceResult} indicating success (with cleared positions) or the reason for abort
     */
    public static PlaceResult clearBasicFixture(ServerWorld world, BlockPos origin) {
        LinkedHashMap<String, BlockState> lanes     = buildLanes();
        LinkedHashMap<String, BlockPos>   positions = buildPositions(origin);

        // Exact-match verification: every support position must hold its expected fixture state.
        // Full scan before any removal — no partial edits on mismatch.
        for (Map.Entry<String, BlockState> entry : lanes.entrySet()) {
            BlockPos   pos      = positions.get(entry.getKey());
            BlockState actual   = world.getBlockState(pos);
            BlockState expected = entry.getValue();

            if (!actual.equals(expected)) {
                return PlaceResult.fail(
                        "unexpected block at " + pos.toShortString()
                        + " (lane " + entry.getKey() + "): expected "
                        + expected.getBlock().getTranslationKey()
                        + ", found " + actual.getBlock().getTranslationKey()
                        + ". No blocks changed.");
            }
        }

        // All positions verified — remove fixture support blocks quietly.
        for (BlockPos pos : positions.values()) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }

        return PlaceResult.success(origin, positions);
    }

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    public record PlaceResult(
            boolean ok,
            BlockPos origin,
            Map<String, BlockPos> positions,
            String error
    ) {
        static PlaceResult fail(String error) {
            return new PlaceResult(false, null, null, error);
        }

        static PlaceResult success(BlockPos origin, Map<String, BlockPos> positions) {
            return new PlaceResult(true, origin, positions, null);
        }
    }
}
