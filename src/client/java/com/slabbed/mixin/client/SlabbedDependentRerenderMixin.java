package com.slabbed.mixin.client;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.SlabDependentRemeshScheduler;
import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fix 3 (break-jump): when a block changes, Slabbed-lowered DEPENDENTS whose
 * own {@link BlockState} did not change but whose rendered dy did (stacked
 * column blocks, adjacent side slabs) can be left with a stale baked mesh and
 * "pop" later. Vanilla re-meshes only the changed block's section plus the
 * Chebyshev-1 neighborhood, so dependents two or more cells away in a different
 * section go stale. The existing anchor rerender
 * ({@code SlabAnchorClientSync}) (a) only covers anchored positions and (b)
 * calls {@code scheduleBlockRerenderIfNeeded(pos, current, current)} which
 * no-ops on identical old/new refs.
 *
 * <p>This pass hooks the vanilla per-block rerender trigger and, when the change
 * sits in a lowering structure, force-schedules an UNCONDITIONAL rerender of the
 * bounded dependent region around it. Requests are coalesced by section and drained under a
 * fixed per-tick budget, so a placement burst cannot flood the renderer with duplicate rebuilds.
 * Render-only; the cheap {@link #slabbed$affectsLoweredDependents} gate keeps it inert outside
 * slab/lowering context so ordinary edits pay nothing.
 */
@Mixin(WorldRenderer.class)
public abstract class SlabbedDependentRerenderMixin {

    @Inject(method = "scheduleBlockRerenderIfNeeded", at = @At("TAIL"))
    private void slabbed$rerenderLoweredDependents(BlockPos pos, BlockState old, BlockState updated, CallbackInfo ci) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return;
        }
        if (!slabbed$affectsLoweredDependents(world, pos, old, updated)) {
            return;
        }
        // Legacy slabs without stored placement heights can resolve through a horizontal
        // slab chain as long as the resolver's vertical chain. Refresh that entire reach:
        // rebuilding a section alone does not replace stale per-position visual heights.
        // The scheduler still coalesces requests and limits section work per client tick.
        int radius = SlabSupport.chainRerenderDepth();
        int minX = pos.getX() - radius;
        int maxX = pos.getX() + radius;
        int minZ = pos.getZ() - radius;
        int maxZ = pos.getZ() + radius;
        // SYMMETRIC in Y. The upward span covers blocks stacked ON a lowered support. The
        // DOWNWARD span is not 1: a ceiling-attached run (a chain column, and whatever hangs from
        // it) resolves its height from the cap at the TOP of the run, so up to MAX_CHAIN_DEPTH
        // cells BELOW this one depend on it. With a 1-cell margin only the first link refreshed
        // and the rest of the run kept a baked mesh at the old height. Strictly additive, as above.
        int minY = pos.getY() - SlabSupport.chainRerenderDepth();
        int maxY = pos.getY() + SlabSupport.chainRerenderDepth();
        SlabDependentRemeshScheduler.enqueue(world, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean slabbed$affectsLoweredDependents(ClientWorld world, BlockPos pos, BlockState old, BlockState updated) {
        // Placing/breaking a slab changes the lowering it provides to neighbors/column.
        if (old.getBlock() instanceof SlabBlock || updated.getBlock() instanceof SlabBlock) {
            return true;
        }
        // A persistent anchor at this cell backs a lowered full block.
        if (SlabAnchorAttachment.isAnchored(world, pos)) {
            return true;
        }
        // Terrain Slabs custom surfaces drive lowering of blocks on/above them the same
        // way vanilla slabs do, but they are not SlabBlock instances — so without this a
        // block placed on Terrain Slabs terrain never force-refreshes the visual cache and
        // the render worker can bake it un-lowered for a frame (the placement "snap"), and
        // dependents go stale (the "ghost" that fills in later).
        if (CompatHooks.customSlabSurfaceKind(old) != CompatSlabSurfaceKind.NONE
                || CompatHooks.customSlabSurfaceKind(updated) != CompatSlabSurfaceKind.NONE) {
            return true;
        }
        // The newly placed/changed block is itself lowered: refresh the visual cache for
        // its region on the main thread so the very first baked mesh already uses the
        // correct dy instead of snapping down a frame later.
        if (!updated.isAir() && SlabSupport.getYOffset(world, pos, updated) != 0.0) {
            return true;
        }
        // The cell sits in a lowered column (bottom slab / anchor below it), so a
        // change here can disconnect or shift the dy of dependents stacked above.
        if (SlabSupport.hasLoweringSourceInColumnBelow(world, pos)) {
            return true;
        }
        // A change directly beside a lowering structure (a slab, a Terrain Slabs surface,
        // or a cell sitting in a lowered column) can shift the rendered dy / cull state of
        // those adjacent lowered dependents — e.g. a full block placed against a TS+VS
        // edge whose neighbour slab is lowered. Without this the neighbour keeps a stale
        // baked mesh and renders as a "ghost" until a later edit. Bounded to the four
        // horizontal neighbours; all probes are recursion-safe (no getYOffset re-entry
        // beyond the guarded public call). Render-only.
        for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            BlockPos n = pos.offset(dir);
            BlockState ns = world.getBlockState(n);
            if (ns.getBlock() instanceof SlabBlock
                    || CompatHooks.customSlabSurfaceKind(ns) != CompatSlabSurfaceKind.NONE
                    || SlabAnchorAttachment.isAnchored(world, n)
                    || SlabSupport.hasLoweringSourceInColumnBelow(world, n)) {
                return true;
            }
        }
        return false;
    }
}
