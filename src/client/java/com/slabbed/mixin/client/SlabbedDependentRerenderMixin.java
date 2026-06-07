package com.slabbed.mixin.client;

import com.slabbed.anchor.SlabAnchorAttachment;
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
import org.spongepowered.asm.mixin.Shadow;
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
 * bounded dependent region around it via {@link WorldRenderer#scheduleBlockRenders(int, int, int, int, int, int)}.
 * Render-only; the cheap {@link #slabbed$affectsLoweredDependents} gate keeps it
 * inert outside slab/lowering context so ordinary edits pay nothing.
 */
@Mixin(WorldRenderer.class)
public abstract class SlabbedDependentRerenderMixin {

    @Shadow
    public abstract void scheduleBlockRenders(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);

    @Inject(method = "scheduleBlockRerenderIfNeeded", at = @At("TAIL"))
    private void slabbed$rerenderLoweredDependents(BlockPos pos, BlockState old, BlockState updated, CallbackInfo ci) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return;
        }
        if (!slabbed$affectsLoweredDependents(world, pos, old, updated)) {
            return;
        }
        // Bounded dependent region: one cell of horizontal margin (adjacent side
        // slabs / lowered FBs) and the column up to MAX_CHAIN_DEPTH above pos
        // (stacked/column dependents). One AABB call schedules the intersecting
        // sections unconditionally, deduped by the renderer.
        int minX = pos.getX() - 1;
        int maxX = pos.getX() + 1;
        int minZ = pos.getZ() - 1;
        int maxZ = pos.getZ() + 1;
        int minY = pos.getY() - 1;
        int maxY = pos.getY() + SlabSupport.chainRerenderDepth();
        SlabSupport.refreshVisualYOffsetRegion(world, minX, minY, minZ, maxX, maxY, maxZ);
        scheduleBlockRenders(minX, minY, minZ, maxX, maxY, maxZ);
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
        return SlabSupport.hasLoweringSourceInColumnBelow(world, pos);
    }
}
