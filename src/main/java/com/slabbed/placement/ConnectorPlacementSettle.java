package com.slabbed.placement;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;

/**
 * Settles connector arms (fence / wall / iron-bars / glass-pane) immediately AFTER C3 publishes a
 * placement's frozen height, so the stepped-connection rule decides from the published height rather
 * than from the absent-fact stand-in.
 *
 * <p><b>Why this exists (regression 2026-08-04, live: two same-height fences on two bottom slabs
 * refused to join).</b> {@link SlabSupport#isSteppedConnectingNeighbor} compares the two cells'
 * frozen heights. Both of its decision points run BEFORE the placement fact exists:
 * {@code getStateForPlacement} runs before the block is even set, and the existing neighbour's
 * {@code updateShape} is fired by the {@code setBlock} inside the same vanilla {@code place} call —
 * while C3 publishes the fact only after {@code place} returns. So the new cell read the stable-flat
 * {@code 0.0} while its already-placed neighbour read its true {@code -0.5}, the pair looked stepped
 * to both ends, and the cut arms were baked into the persisted blockstate with nothing to revisit
 * them. This pass IS that revisit.
 *
 * <p><b>Why the height read was left alone.</b> Teaching the connector read to fall back to an
 * unstored placement-time height would also change the answer for cells that legitimately have no
 * fact — legacy worlds (there is no retro-migration; see {@code SlabAnchorAttachment#FROZEN_DY_ENABLED}),
 * worldgen, structures, {@code /setblock}. Those cells DRAW at {@code 0.0}, because the model reads the
 * same stored authority. A connector reading anything else would cut arms between posts that visibly
 * sit at the same height. Changing WHEN the decision runs keeps the connector and the model in exact
 * agreement for every cell, and corrects only real player placements — the ones that do get a fact.
 *
 * <p><b>LAW.md.</b> Nothing here reads, writes, or re-derives a height. The store is untouched; only
 * vanilla's own connection properties are re-derived, through vanilla's own
 * {@link Block#updateFromNeighbourShapes} entry point, which is the specific vanilla gameplay
 * mechanism that owns fence/wall/pane arms. The rewrite is refused unless the block at the cell is
 * unchanged, so this can never remove, replace, or relocate a placed block.
 */
public final class ConnectorPlacementSettle {

    /**
     * {@code UPDATE_CLIENTS} so the corrected arms reach players; {@code UPDATE_KNOWN_SHAPE} so the
     * write does not launch a second shape pass, and no {@code UPDATE_NEIGHBORS} so it raises no
     * neighbour-notification cascade. This pass already visits both ends of every pair it can affect
     * (the placed cell and its four horizontal neighbours), so no cascade is needed to be complete.
     */
    private static final int SETTLE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private ConnectorPlacementSettle() {
    }

    /**
     * Re-derives connector arms at each just-published cell and at its four horizontal neighbours.
     * Server-side only: the client receives the settled state as an ordinary block update, so it
     * never needs (and must not run) a second, independent decision.
     */
    public static void settlePublishedPlacements(Level world, Collection<BlockPos> publishedPositions) {
        if (world == null || world.isClientSide() || publishedPositions == null || publishedPositions.isEmpty()) {
            return;
        }
        for (BlockPos published : publishedPositions) {
            if (published == null) {
                continue;
            }
            settleCell(world, published);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                settleCell(world, published.relative(direction));
            }
        }
    }

    private static void settleCell(Level world, BlockPos pos) {
        BlockState current = world.getBlockState(pos);
        if (!SlabSupport.isSteppedConnectionFamily(current)) {
            return;
        }
        BlockState settled = Block.updateFromNeighbourShapes(current, world, pos);
        if (settled == current || settled.getBlock() != current.getBlock()) {
            // Identical state: nothing to do. Different BLOCK (or air): vanilla judged the cell
            // unsurvivable, which is a removal decision this pass has no business making — a
            // placement-time arm refresh must never take a block away.
            return;
        }
        world.setBlock(pos, settled, SETTLE_FLAGS);
    }
}
