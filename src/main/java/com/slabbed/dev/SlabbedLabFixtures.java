package com.slabbed.dev;

import com.slabbed.debug.BsFbLiveTrace;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixture generation and action methods for Slabbed Lab dev commands.
 * Server-side only; no gameplay logic is modified.
 */
public final class SlabbedLabFixtures {

    private SlabbedLabFixtures() {}

    private static BlockPos activeOrigin = null;

    /** X-spacing between lane centres, in blocks. */
    private static final int LANE_STRIDE = 2;

    /**
     * Blocks checked above each support position before placement.
     * Includes the support block itself (dy=0) through dy=CHECK_HEIGHT.
     */
    private static final int CHECK_HEIGHT = 2;

    // -------------------------------------------------------------------------
    // Canonical footprint definition — all fixture and action methods derive
    // from these helpers. Adding a lane here propagates everywhere automatically.
    // -------------------------------------------------------------------------

    /**
     * Returns the canonical lane definitions for the basic fixture, in display order.
     * Lane name → expected {@link BlockState} at that lane's support position.
     *
     * <p>Valid lane names: {@code FULL}, {@code BOTTOM_SLAB}, {@code TOP_SLAB}.
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

    public static void rememberOrigin(BlockPos origin) {
        activeOrigin = origin;
    }

    public static BlockPos activeOriginOr(BlockPos fallback) {
        return activeOrigin != null ? activeOrigin : fallback;
    }

    public static LinkedHashMap<String, BlockPos> lanePositions(BlockPos origin) {
        return buildPositions(origin);
    }

    public static BlockPos laneSupportPos(BlockPos origin, String laneName) {
        LinkedHashMap<String, BlockPos> positions = buildPositions(origin);
        return positions.get(laneName.toUpperCase());
    }

    /**
     * Resolves the target lane set from a nullable lane selector.
     *
     * @param laneName command-provided lane name (case-insensitive), or {@code null} for all lanes
     * @return the filtered lane map, or {@code null} if {@code laneName} is not a known lane
     */
    private static LinkedHashMap<String, BlockState> resolveLanes(String laneName) {
        LinkedHashMap<String, BlockState> all = buildLanes();
        if (laneName == null) return all;

        String key = laneName.toUpperCase();
        BlockState state = all.get(key);
        if (state == null) return null;

        LinkedHashMap<String, BlockState> single = new LinkedHashMap<>();
        single.put(key, state);
        return single;
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

        rememberOrigin(origin);

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
    // Public action operations — trigger neighbor updates intentionally.
    // These reproduce support-loss/restore behavior; NOTIFY_ALL is correct here.
    // -------------------------------------------------------------------------

    /**
     * Removes the support block(s) for the targeted lane(s) at {@code origin},
     * triggering full neighbor notification so blocks above react as they would
     * to real support loss.
     *
     * <p>Before any edit, all targeted positions are verified to hold their expected
     * fixture support state. Aborts atomically on mismatch.
     *
     * @param laneName case-insensitive lane selector ({@code "full"}, {@code "bottom_slab"},
     *                 {@code "top_slab"}), or {@code null} for all lanes
     * @return {@link PlaceResult} with affected positions on success, or fail reason
     */
    public static PlaceResult breakSupport(ServerWorld world, BlockPos origin, String laneName) {
        LinkedHashMap<String, BlockState> targetLanes = resolveLanes(laneName);
        if (targetLanes == null) {
            return PlaceResult.fail("unknown lane '" + laneName + "'. Valid: full, bottom_slab, top_slab.");
        }

        LinkedHashMap<String, BlockPos> allPositions = buildPositions(origin);

        // Full verification pass before any edits — no partial world state on mismatch.
        for (Map.Entry<String, BlockState> entry : targetLanes.entrySet()) {
            BlockPos   pos      = allPositions.get(entry.getKey());
            BlockState actual   = world.getBlockState(pos);
            BlockState expected = entry.getValue();

            if (!actual.equals(expected)) {
                return PlaceResult.fail(
                        laneMismatchMessage("break", entry.getKey(), pos, expected, actual));
            }
        }

        // BS-FB Live Trace: capture before breaking support (Moment A)
        for (String name : targetLanes.keySet()) {
            BlockPos supportPos = allPositions.get(name);
            BlockPos fullPos = supportPos.up();
            BsFbLiveTrace.capture(world, supportPos, fullPos, "BEFORE_BREAK_" + name);
            BsFbLiveTrace.captureClient(supportPos, fullPos, "BEFORE_BREAK_" + name);
        }

        // Remove support blocks with full neighbor notification — reproduces real support loss.
        LinkedHashMap<String, BlockPos> affected = new LinkedHashMap<>();
        for (String name : targetLanes.keySet()) {
            BlockPos pos = allPositions.get(name);
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            affected.put(name, pos);
        }

        // BS-FB Live Trace: capture immediately after breaking support (Moment B)
        for (String name : targetLanes.keySet()) {
            BlockPos supportPos = allPositions.get(name);
            BlockPos fullPos = supportPos.up();
            BsFbLiveTrace.capture(world, supportPos, fullPos, "AFTER_BREAK_" + name);
            BsFbLiveTrace.captureClient(supportPos, fullPos, "AFTER_BREAK_" + name);
        }

        // BS-FB Live Trace: schedule delayed captures (Moments C: 1, 2, 5 ticks after break)
        if (BsFbLiveTrace.ENABLED) {
            long currentTick = world.getServer().getTicks();
            int[] delays = new int[] {1, 2, 5};
            for (int d : delays) {
                final int finalDelay = d;
                world.getServer().execute(new Runnable() {
                    @Override
                    public void run() {
                        if (world.getServer().getTicks() >= currentTick + finalDelay) {
                            for (String name : targetLanes.keySet()) {
                                BlockPos supportPos = allPositions.get(name);
                                BlockPos fullPos = supportPos.up();
                                String label = "DELAY_" + finalDelay + "TICKS_AFTER_BREAK_" + name;
                                BsFbLiveTrace.capture(world, supportPos, fullPos, label);
                                BsFbLiveTrace.captureClient(supportPos, fullPos, label);
                            }
                        }
                    }
                });
            }
        }

        return PlaceResult.success(origin, affected);
    }

    /**
     * Restores the support block(s) for the targeted lane(s) at {@code origin},
     * triggering full neighbor notification so blocks above react as they would
     * to real support restoration.
     *
     * <p>Before any edit, all targeted positions are verified to be air.
     * Aborts atomically if any position is occupied by an unexpected block.
     *
     * @param laneName case-insensitive lane selector ({@code "full"}, {@code "bottom_slab"},
     *                 {@code "top_slab"}), or {@code null} for all lanes
     * @return {@link PlaceResult} with affected positions on success, or fail reason
     */
    public static PlaceResult restoreSupport(ServerWorld world, BlockPos origin, String laneName) {
        LinkedHashMap<String, BlockState> targetLanes = resolveLanes(laneName);
        if (targetLanes == null) {
            return PlaceResult.fail("unknown lane '" + laneName + "'. Valid: full, bottom_slab, top_slab.");
        }

        LinkedHashMap<String, BlockPos> allPositions = buildPositions(origin);

        // Full verification pass before any edits: each target position must be air.
        for (Map.Entry<String, BlockState> entry : targetLanes.entrySet()) {
            BlockPos   pos    = allPositions.get(entry.getKey());
            BlockState actual = world.getBlockState(pos);

            if (!actual.isAir()) {
                return PlaceResult.fail(
                        laneMismatchMessage("restore", entry.getKey(), pos, Blocks.AIR.getDefaultState(), actual));
            }
        }

        // Restore support blocks with full neighbor notification.
        LinkedHashMap<String, BlockPos> affected = new LinkedHashMap<>();
        for (Map.Entry<String, BlockState> entry : targetLanes.entrySet()) {
            BlockPos pos = allPositions.get(entry.getKey());
            world.setBlockState(pos, entry.getValue(), Block.NOTIFY_ALL);
            affected.put(entry.getKey(), pos);
        }

        // BS-FB Live Trace: capture immediately after placement (Moment D)
        for (String name : targetLanes.keySet()) {
            BlockPos supportPos = allPositions.get(name);
            BlockPos fullPos = supportPos.up();
            BsFbLiveTrace.capture(world, supportPos, fullPos, "AFTER_PLACEMENT_" + name);
        }

        // BS-FB Live Trace: schedule delayed captures (Moments E: 2-10 ticks after placement)
        if (BsFbLiveTrace.ENABLED) {
            long currentTick = world.getServer().getTicks();
            for (int delay = 2; delay <= 10; delay++) {
                final int finalDelay = delay;
                world.getServer().execute(new Runnable() {
                    @Override
                    public void run() {
                        if (world.getServer().getTicks() >= currentTick + finalDelay) {
                            for (String name : targetLanes.keySet()) {
                                BlockPos supportPos = allPositions.get(name);
                                BlockPos fullPos = supportPos.up();
                                BsFbLiveTrace.capture(world, supportPos, fullPos, "DELAY_" + finalDelay + "TICKS_AFTER_PLACE_" + name);
                            }
                        }
                    }
                });
            }
        }

        return PlaceResult.success(origin, affected);
    }

    /**
     * Performs a minimal, reversible neighbor-update pulse adjacent to the candidate
     * cell of each targeted lane.
     *
     * <p>Pulse position: one block <em>south</em> of each candidate cell
     * ({@code supportPos.up().south()}). South (+Z) is chosen because the fixture
     * sits at origin.Z, the player stands at origin.Z-3 (north), and south is the
     * direction away from the player — clear of the fixture footprint and the operator.
     *
     * <p>Pulse sequence per lane:
     * <ol>
     *   <li>Place {@link Blocks#STONE} at the pulse position with {@link Block#NOTIFY_ALL}
     *       → triggers {@code getStateForNeighborUpdate} on the candidate cell.
     *   <li>Remove it immediately with {@link Block#NOTIFY_ALL} → second notification.
     * </ol>
     *
     * <p>The pulse position is verified to be air before any edit. Aborts atomically
     * if any targeted pulse position is occupied.
     *
     * @param laneName case-insensitive lane selector ({@code "full"}, {@code "bottom_slab"},
     *                 {@code "top_slab"}), or {@code null} for all lanes
     * @return {@link PlaceResult} with candidate positions on success, or fail reason
     */
    public static PlaceResult neighborUpdatePulse(ServerWorld world, BlockPos origin, String laneName) {
        LinkedHashMap<String, BlockState> targetLanes = resolveLanes(laneName);
        if (targetLanes == null) {
            return PlaceResult.fail("unknown lane '" + laneName + "'. Valid: full, bottom_slab, top_slab.");
        }

        LinkedHashMap<String, BlockPos> allPositions = buildPositions(origin);

        // Resolve pulse positions: south of candidate cell for each targeted lane.
        LinkedHashMap<String, BlockPos> pulsePositions = new LinkedHashMap<>();
        for (String name : targetLanes.keySet()) {
            pulsePositions.put(name, allPositions.get(name).up().south());
        }

        // Safety scan: all pulse positions must be air before any edit.
        for (Map.Entry<String, BlockPos> entry : pulsePositions.entrySet()) {
            if (!world.getBlockState(entry.getValue()).isAir()) {
                return PlaceResult.fail(
                        "pulse position occupied at " + entry.getValue().toShortString()
                        + " (lane " + entry.getKey() + "). No edits made.");
            }
        }

        // Execute pulse: place then remove stone at each pulse position with NOTIFY_ALL.
        // Stone is used as it is stable (non-falling), neutral, and already present in the fixture.
        for (BlockPos pulsePos : pulsePositions.values()) {
            world.setBlockState(pulsePos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(pulsePos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }

        // Return candidate positions (supportPos.up()) — the cells that received the updates.
        LinkedHashMap<String, BlockPos> candidatePositions = new LinkedHashMap<>();
        for (String name : targetLanes.keySet()) {
            candidatePositions.put(name, allPositions.get(name).up());
        }

        return PlaceResult.success(origin, candidatePositions);
    }

    /**
     * Returns the current world state for each targeted lane at {@code origin}.
     * Read-only; no world edits are made.
     *
     * <p>Data captured per lane:
     * <ul>
     *   <li>support position, expected state, actual state, match flag
     *   <li>candidate cell position (supportPos.up()), whether it is air
     *   <li>pulse position (candidatePos.south()), whether it is air
     * </ul>
     *
     * @param laneName case-insensitive lane selector, or {@code null} for all lanes
     * @return ordered list of {@link LaneStatus} entries, or {@code null} if {@code laneName}
     *         is not a known lane
     */
    public static List<LaneStatus> queryStatus(ServerWorld world, BlockPos origin, String laneName) {
        LinkedHashMap<String, BlockState> targetLanes = resolveLanes(laneName);
        if (targetLanes == null) return null;

        LinkedHashMap<String, BlockPos> allPositions = buildPositions(origin);

        List<LaneStatus> result = new ArrayList<>();
        for (Map.Entry<String, BlockState> entry : targetLanes.entrySet()) {
            String     name            = entry.getKey();
            BlockState expectedSupport = entry.getValue();
            BlockPos   supportPos      = allPositions.get(name);
            BlockState actualSupport   = world.getBlockState(supportPos);
            BlockPos   candidatePos    = supportPos.up();
            boolean    candidateFree   = world.getBlockState(candidatePos).isAir();
            BlockPos   pulsePos        = candidatePos.south();
            boolean    pulseFree       = world.getBlockState(pulsePos).isAir();

            result.add(new LaneStatus(name, supportPos, expectedSupport, actualSupport,
                    candidatePos, candidateFree, pulsePos, pulseFree));
        }

        return result;
    }

    public static String describeLane(ServerWorld world, BlockPos origin, String laneName) {
        LinkedHashMap<String, BlockState> targetLanes = resolveLanes(laneName);
        if (targetLanes == null) {
            return "unknown lane '" + laneName + "'. Valid: full, bottom_slab, top_slab.";
        }
        BlockState expected = targetLanes.get(laneName.toUpperCase());
        BlockPos supportPos = buildPositions(origin).get(laneName.toUpperCase());
        BlockState actual = world.getBlockState(supportPos);
        StringBuilder sb = new StringBuilder();
        sb.append("expected lane=").append(laneName.toUpperCase())
          .append(" supportPos=").append(supportPos.toShortString())
          .append(" actualState=").append(actual)
          .append(" expectedState=").append(expected)
          .append(" suggestion=");
        if (actual.isAir()) {
            sb.append("/slablab fixture basic");
        } else {
            sb.append("/slablab fixture clear");
        }
        return sb.toString();
    }

    public static String describeLaneInspection(ServerWorld world, BlockPos origin, String laneName) {
        LinkedHashMap<String, BlockState> targetLanes = resolveLanes(laneName);
        if (targetLanes == null) {
            return "unknown lane '" + laneName + "'. Valid: full, bottom_slab, top_slab.";
        }

        String key = laneName.toUpperCase();
        BlockPos supportPos = buildPositions(origin).get(key);
        BlockPos fullPos = supportPos.up();
        BlockPos slabPos = supportPos;

        BlockState supportState = world.getBlockState(supportPos);
        BlockState fullState = world.getBlockState(fullPos);
        boolean anchored = com.slabbed.anchor.SlabAnchorAttachment.isAnchored(world, fullPos);
        double fullDy = com.slabbed.util.SlabSupport.getYOffset(world, fullPos, fullState);
        double slabDy = com.slabbed.util.SlabSupport.getYOffset(world, slabPos, supportState);

        StringBuilder sb = new StringBuilder();
        sb.append("lane=").append(key)
          .append(" supportPos=").append(supportPos.toShortString())
          .append(" supportState=").append(supportState)
          .append(" fullPos=").append(fullPos.toShortString())
          .append(" fullState=").append(fullState)
          .append(" slabPos=").append(slabPos.toShortString())
          .append(" slabPosUp=").append(fullPos.toShortString())
          .append(" anchor=").append(anchored)
          .append(" fullDy=").append(fullDy)
          .append(" slabDy=").append(slabDy);

        if (supportState.getBlock() instanceof SlabBlock && supportState.contains(SlabBlock.TYPE)) {
            sb.append(" slabTypeAtSupport=").append(supportState.get(SlabBlock.TYPE));
        }
        if (fullState.getBlock() instanceof SlabBlock && fullState.contains(SlabBlock.TYPE)) {
            sb.append(" slabTypeAtFull=").append(fullState.get(SlabBlock.TYPE));
        }

        return sb.toString();
    }

    private static String laneMismatchMessage(String action, String laneName, BlockPos pos, BlockState expected, BlockState actual) {
        StringBuilder sb = new StringBuilder();
        sb.append("cannot ").append(action).append(' ')
          .append("lane=").append(laneName)
          .append(" supportPos=").append(pos.toShortString())
          .append(" actualState=").append(actual)
          .append(" expectedState=").append(expected)
          .append(" suggestion=");
        if (actual.isAir()) {
            sb.append("/slablab fixture basic");
        } else {
            sb.append("/slablab fixture clear");
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Result and status types
    // -------------------------------------------------------------------------

    /**
     * Immutable snapshot of one lane's world state relative to the basic fixture definition.
     * Produced by {@link #queryStatus}; never causes world edits.
     */
    public record LaneStatus(
            String     laneName,
            BlockPos   supportPos,
            BlockState expectedSupport,
            BlockState actualSupport,
            BlockPos   candidatePos,
            boolean    candidateFree,
            BlockPos   pulsePos,
            boolean    pulseFree
    ) {
        /** True when the world block at the support position exactly matches the expected fixture state. */
        public boolean supportMatch() {
            return actualSupport.equals(expectedSupport);
        }
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
