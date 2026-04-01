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
        // Lane definitions in display order.
        LinkedHashMap<String, BlockState> lanes = new LinkedHashMap<>();
        lanes.put("FULL",        Blocks.STONE.getDefaultState());
        lanes.put("BOTTOM_SLAB", Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
        lanes.put("TOP_SLAB",    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP));

        // Resolve positions: lane index × LANE_STRIDE in +X from origin.
        LinkedHashMap<String, BlockPos> positions = new LinkedHashMap<>();
        int idx = 0;
        for (String name : lanes.keySet()) {
            positions.put(name, origin.add(idx * LANE_STRIDE, 0, 0));
            idx++;
        }

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
