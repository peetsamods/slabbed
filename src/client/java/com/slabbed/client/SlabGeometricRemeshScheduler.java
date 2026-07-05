package com.slabbed.client;

import com.slabbed.compat.CompatHooks;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides whether — and over what SECTION range — the client must re-mesh when an ARBITRARY block
 * changes on the client, so that a geometrically-lowered SUBJECT whose height depends on a NEIGHBOUR
 * (not on any attachment recorded on the subject itself) does not render stale.
 *
 * <h2>Why this exists (the mesh-staleness bug)</h2>
 * The chunk-mesh worker bakes a snapshot of {@link com.slabbed.util.SlabSupport#getYOffset} into a
 * section mesh (see {@code OffsetBlockStateModel#slabbed$modelDy}, which reads the render-region view
 * on the mesh thread). That mesh only rebuilds when the section is marked dirty. The vanilla client
 * dirty path for a single block change ({@code ClientLevel.sendBlockUpdated} →
 * {@code LevelExtractor.blockChanged} → an internal {@code setBlockDirty} that loops only ±1 BLOCK
 * around the changed position) therefore dirties, in the common mid-section case, ONLY the changed
 * block's own section.
 *
 * <p>But several lowering branches in {@code SlabSupport.getYOffsetInner} are PURELY GEOMETRIC: the
 * subject reads a neighbour's live state (the cantilever/connecting-block/block-entity BFS
 * traversals — bounded by {@code MAX_CHAIN_DEPTH} — the column walk in {@code slabColumnYOffset}, and
 * the floor-contact readers) with NO attachment recorded on the subject. When a support is placed or
 * broken up to {@code MAX_CHAIN_DEPTH} cells from such a subject, and the subject lives in a DIFFERENT
 * section than the changed support, nothing dirties the subject's section — its baked mesh stays at
 * the old (often flush) height while the live-recomputed raycast/outline correctly shows the new
 * lowered height. Result: a block rendered at the wrong height, unselectable at its visible position.
 *
 * <p>{@link com.slabbed.client.SlabAnchorClientSync} only re-meshes positions inside its own
 * attachment sets, so it never covers these attachment-less geometric subjects.
 *
 * <h2>Scope decision (radius)</h2>
 * The geometric readers reach at most {@code MAX_CHAIN_DEPTH} = 16 blocks from a subject. Because a
 * section is 16 blocks wide, a subject within 16 blocks of the changed support is ALWAYS within the
 * support's section ±1 section (proven for every intra-section support alignment). So dirtying the
 * changed block's section ±{@link #SECTION_RADIUS} = ±1 section (a 3×3×3 = 27-section box) covers the
 * full {@code MAX_CHAIN_DEPTH} reach in every direction — it is the minimal section radius that
 * guarantees coverage, not a guess. A smaller BLOCK radius (±2/±3) would collapse to the SAME set of
 * sections vanilla already touches on a boundary and add nothing across a section boundary — where the
 * bug actually lives — so a sub-section block radius is not a meaningful cheaper option here.
 *
 * <h2>Cost gate</h2>
 * A block change dirties the 27-section box ONLY when the change could plausibly alter a neighbour's
 * dy — i.e. when the removed or added block is a potential lowering SOURCE/SUPPORT
 * ({@link #isPotentialLoweringSource}). A torch/flower/redstone/crop placement or growth (never a
 * support) short-circuits on a handful of allocation-free {@code instanceof}/{@code isSolidRender}
 * checks with zero section work — see {@link #shouldRemeshNeighborhood}.
 *
 * <p>This class is pure logic (no world/renderer dependency) so the scheduling DECISION and the dirty
 * SECTION RANGE are unit-testable headlessly, independent of whether a real client renderer executes
 * the resulting dirty. The actual dirty is issued by {@code ClientLevelBlockChangeRemeshMixin}.
 *
 * <p>Lives in the client source set (only ever invoked from the client-only mixin, and only shipped
 * client-side via that source set), but is deliberately NOT {@code @Environment(CLIENT)}: it is pure,
 * dependency-free logic with no client-only references, and the annotation would make the headless
 * server gametest (which pins this exact decision logic) unable to load it. Mirrors
 * {@code BetaNoticeSessionGate}, which is client-source but un-annotated for the same reason.
 */
public final class SlabGeometricRemeshScheduler {

    /**
     * Section radius dirtied around a relevant block change. 1 section = the full
     * {@code SlabSupport.MAX_CHAIN_DEPTH} (16-block) geometric reach in every direction; see the class
     * javadoc for the derivation. Kept in lockstep with {@code MAX_CHAIN_DEPTH}: if that constant ever
     * exceeds 16 (one section width), this must grow to {@code ceil(MAX_CHAIN_DEPTH / 16)}.
     */
    public static final int SECTION_RADIUS = 1;

    /**
     * Immutable inclusive SECTION-coordinate box: the region to mark dirty. Fields are SECTION coords
     * (block {@code >> 4}), ready to hand straight to {@code ClientLevel.setSectionRangeDirty}.
     */
    public record SectionBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    private SlabGeometricRemeshScheduler() {
    }

    /**
     * True when a change from {@code oldState} to {@code newState} could geometrically alter a
     * neighbouring block's Slabbed dy, so the surrounding neighbourhood must be re-meshed. Pure,
     * allocation-free, no world read. The change matters iff EITHER endpoint is a potential lowering
     * source/support — a support appearing (air→slab/cube) or disappearing (slab/cube→air) both flip a
     * dependent neighbour's height; a change that never was and never becomes a source (flower→air,
     * redstone dust state change, crop growth) can never affect a neighbour's dy and is skipped.
     */
    public static boolean shouldRemeshNeighborhood(BlockState oldState, BlockState newState) {
        return isPotentialLoweringSource(oldState) || isPotentialLoweringSource(newState);
    }

    /**
     * True when {@code state} is a block that could act as a Slabbed lowering source or support surface
     * for a geometric neighbour: a slab (bottom-slab support / column member / adjacency source), a full
     * opaque cube ({@code isSolidRender} — a cantilever/column full-block source or a support below), a
     * block entity (hopper/chest cantilever lane), or a Terrain-Slabs surface (its presence terminates a
     * column walk flush). {@code null}/air is never a source (it is only ever a transition ENDPOINT, and
     * {@link #shouldRemeshNeighborhood} inspects the non-air side). All checks are allocation-free and
     * read no world state.
     */
    public static boolean isPotentialLoweringSource(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (state.getBlock() instanceof SlabBlock) {
            return true;
        }
        if (state.getBlock() instanceof EntityBlock) {
            return true;
        }
        if (CompatHooks.shouldSkipSlabSupport(state)) {
            return true;
        }
        return state.isSolidRender();
    }

    /**
     * The inclusive SECTION-coordinate box to dirty for a change at block position
     * ({@code blockX},{@code blockY},{@code blockZ}): the changed block's section expanded by
     * {@link #SECTION_RADIUS} on every axis. Pure function of the coordinates.
     */
    public static SectionBox dirtySectionBox(int blockX, int blockY, int blockZ) {
        int sx = SectionPos.blockToSectionCoord(blockX);
        int sy = SectionPos.blockToSectionCoord(blockY);
        int sz = SectionPos.blockToSectionCoord(blockZ);
        return new SectionBox(
                sx - SECTION_RADIUS, sy - SECTION_RADIUS, sz - SECTION_RADIUS,
                sx + SECTION_RADIUS, sy + SECTION_RADIUS, sz + SECTION_RADIUS);
    }
}
