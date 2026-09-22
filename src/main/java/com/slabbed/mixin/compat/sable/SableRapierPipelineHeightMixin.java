package com.slabbed.mixin.compat.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slabbed.compat.sable.SableCellColliderSource;
import com.slabbed.compat.sable.SablePhysicsHeight;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery;
import dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderData;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bakes Sable's world colliders from the collision each cell has under Slabbed.
 *
 * <p>Both upload paths classify a cell with {@code VoxelNeighborhoodState.getState} and then
 * immediately ask the bakery for that same cell's collider. The classification call names the
 * cell's position, so it is where the cell's Slabbed collision is resolved; the collider lookup
 * that follows consumes it. A cell a Slabbed height moves is classified {@code CORNER} (Sable's
 * generic partial-block case) and gets a collider baked for its own shape; a full block beside
 * such a cell is also {@code CORNER}, because the face it would cull against is no longer full.
 * Every other cell keeps Sable's classification and per-state collider exactly. A section upload
 * first asks {@link SablePhysicsHeight#sectionMayBeAltered}; a section nothing can lower skips the
 * per-cell work entirely.
 *
 * <p>Admitted only by {@link SableMixinPlugin}'s byte probe.
 */
@Mixin(value = RapierPhysicsPipeline.class, remap = false)
public abstract class SableRapierPipelineHeightMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Unique
    private SablePhysicsHeight.Memo slabbed$memo;

    @Unique
    private SablePhysicsHeight.CellCollision slabbed$pendingCell;

    @Unique
    private boolean slabbed$pendingSet;

    @Inject(method = SableMixinPlugin.SECTION_ADDITION_METHOD, at = @At("HEAD"))
    private void slabbed$openSectionPass(LevelChunkSection section, int x, int y, int z, boolean uploadDataIfGlobal,
                                         CallbackInfo ci) {
        this.slabbed$memo = SablePhysicsHeight.sectionMayBeAltered(this.level, x, y, z)
                ? new SablePhysicsHeight.Memo(this.level)
                : null;
        this.slabbed$pendingCell = null;
        this.slabbed$pendingSet = false;
    }

    @Inject(method = SableMixinPlugin.BLOCK_CHANGE_METHOD, at = @At("HEAD"))
    private void slabbed$openBlockPass(SectionPos sectionPos, LevelChunkSection section, int x, int y, int z,
                                       BlockState oldState, BlockState newState, CallbackInfo ci) {
        // The changed cell and its six neighbors span this section and the ones above and below.
        this.slabbed$memo = SablePhysicsHeight.sectionsMayBeAltered(
                        this.level, sectionPos.x(), sectionPos.y() - 1, sectionPos.y() + 1, sectionPos.z())
                ? new SablePhysicsHeight.Memo(this.level)
                : null;
        this.slabbed$pendingCell = null;
        this.slabbed$pendingSet = false;
    }

    @Inject(method = {SableMixinPlugin.SECTION_ADDITION_METHOD, SableMixinPlugin.BLOCK_CHANGE_METHOD},
            at = @At("RETURN"))
    private void slabbed$closePass(CallbackInfo ci) {
        this.slabbed$memo = null;
        this.slabbed$pendingCell = null;
        this.slabbed$pendingSet = false;
    }

    @WrapOperation(method = {SableMixinPlugin.SECTION_ADDITION_METHOD, SableMixinPlugin.BLOCK_CHANGE_METHOD},
            at = @At(value = "INVOKE", target = SableMixinPlugin.GET_STATE_CALL))
    private VoxelNeighborhoodState slabbed$classifyCell(LevelAccelerator accelerator, BlockPos pos, LevelChunk chunk,
                                                        Operation<VoxelNeighborhoodState> original) {
        VoxelNeighborhoodState state = original.call(accelerator, pos, chunk);
        SablePhysicsHeight.Memo memo = this.slabbed$memo;
        if (memo == null) {
            return state;
        }
        SablePhysicsHeight.CellCollision cell = memo.cell(pos);
        if (cell != null && accelerator.getBlockState(pos).getBlock() instanceof BlockSubLevelCollisionShape) {
            // A block that ships its own physics shape for Sable keeps it.
            cell = null;
        }
        this.slabbed$pendingCell = cell;
        this.slabbed$pendingSet = true;
        if (cell != null) {
            return cell.shape().isEmpty() ? VoxelNeighborhoodState.EMPTY : VoxelNeighborhoodState.CORNER;
        }
        if ((state == VoxelNeighborhoodState.INTERIOR
                || state == VoxelNeighborhoodState.FACE
                || state == VoxelNeighborhoodState.EDGE)
                && memo.anyNeighborAltered(pos)) {
            return VoxelNeighborhoodState.CORNER;
        }
        return state;
    }

    @WrapOperation(method = {SableMixinPlugin.SECTION_ADDITION_METHOD, SableMixinPlugin.BLOCK_CHANGE_METHOD},
            at = @At(value = "INVOKE", target = SableMixinPlugin.GET_DATA_CALL))
    private RapierVoxelColliderData slabbed$cellCollider(RapierVoxelColliderBakery bakery, BlockState state,
                                                         Operation<RapierVoxelColliderData> original) {
        SablePhysicsHeight.CellCollision cell = this.slabbed$pendingCell;
        boolean paired = this.slabbed$pendingSet;
        this.slabbed$pendingCell = null;
        this.slabbed$pendingSet = false;
        if (!paired || cell == null) {
            return original.call(bakery, state);
        }
        return ((SableCellColliderSource) bakery).slabbed$colliderFor(cell.material(), cell.shape());
    }
}
