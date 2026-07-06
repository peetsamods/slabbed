package com.slabbed.mixin.client;

import com.slabbed.client.SlabGeometricRemeshScheduler;
import com.slabbed.client.SlabGeometricRemeshScheduler.SectionBox;
import com.slabbed.client.SlabImportantRemesh;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-meshes the SECTION neighbourhood around ANY client block change that could geometrically alter a
 * neighbour's Slabbed dy, fixing the mesh-staleness "wrong render height + unselectable at visible
 * position" bug (see {@link SlabGeometricRemeshScheduler} for the full mechanism and the ±1-section
 * radius derivation).
 *
 * <p>{@code sendBlockUpdated} is the single funnel every client-side block change passes through
 * (local {@code Level.setBlock} and every server-pushed block/section-change packet), and it is the
 * vanilla call that dirties the renderer — so injecting at its TAIL runs exactly once per block change,
 * after vanilla has done its own ±1-block dirty. The gate short-circuits the overwhelming majority of
 * changes (anything that is not a lowering source/support) before any section work, so the added cost
 * on an irrelevant change (torch, flower, redstone, crop growth) is a few allocation-free
 * {@code instanceof} checks and nothing more.
 *
 * <p>Recursion-safe: the dirty issued here (via {@code SlabImportantRemesh}, an IMPORTANT-priority
 * variant of the section-dirty call — see {@link SlabGeometricRemeshScheduler#REQUEST_IMPORTANT_REBUILD})
 * only marks section render-dirty flags; it never re-enters {@code sendBlockUpdated}.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelBlockChangeRemeshMixin {

    @Inject(method = "sendBlockUpdated(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;I)V",
            at = @At("TAIL"))
    private void slabbed$remeshGeometricNeighborhood(BlockPos pos,
                                                     BlockState oldState,
                                                     BlockState newState,
                                                     int flags,
                                                     CallbackInfo ci) {
        if (!SlabGeometricRemeshScheduler.shouldRemeshNeighborhood(oldState, newState)) {
            return;
        }
        SectionBox box = SlabGeometricRemeshScheduler.dirtySectionBox(pos.getX(), pos.getY(), pos.getZ());
        // Dirty the box as an IMPORTANT rebuild rather than via setSectionRangeDirty (which hardcodes
        // important=false). Under Sodium, important=false lands in the deferred, per-frame-budgeted queue
        // — the ~30-second-snap delay; important=true reaches the near-immediate presentation path for
        // this near-camera section. See SlabGeometricRemeshScheduler.REQUEST_IMPORTANT_REBUILD.
        SlabImportantRemesh.dirtyImportant((ClientLevel) (Object) this, box);
    }
}
