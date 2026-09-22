package com.slabbed.mixin.compat.sable;

import com.slabbed.compat.sable.SablePhysicsHeight;
import com.slabbed.compat.sable.SablePlacementRefresh;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-bakes the cells a stored placement height affects once the height is final.
 *
 * <p>Sable re-bakes a cell and its six neighbors inside the block change itself, but a placed
 * block's height is stored after that change returns, so the bake can predate the fact. The refresh
 * goes to the collider pipeline directly: the block did not change, so Sable's mass bookkeeping
 * for block changes must not run again. Sub-levels touching a refreshed cell are woken so a resting
 * object settles onto the corrected collider.
 *
 * <p>Admitted only by {@link SableMixinPlugin}'s byte probe.
 */
@Mixin(value = SubLevelPhysicsSystem.class, remap = false)
public abstract class SablePhysicsSystemRefreshMixin implements SablePlacementRefresh {
    @Shadow
    @Final
    private PhysicsPipeline pipeline;

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    public abstract void wakeUpObjectsAt(int x, int y, int z);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void slabbed$registerRefresh(ServerLevel level, CallbackInfo ci) {
        SablePhysicsHeight.registerRefresher(level, this);
    }

    @Override
    public void slabbed$refreshPlacementHeight(BlockPos pos, int depth) {
        if (SubLevelPhysicsSystem.IN_PHYSICS_STEP || !this.level.getServer().isSameThread()) {
            return;
        }
        for (int delta = 0; delta <= depth; delta++) {
            BlockPos cell = pos.below(delta);
            if (this.level.isOutsideBuildHeight(cell)) {
                break;
            }
            LevelChunk chunk = this.level.getChunkSource().getChunkNow(cell.getX() >> 4, cell.getZ() >> 4);
            if (chunk == null) {
                return;
            }
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(cell.getY()));
            BlockState state = section.getBlockState(cell.getX() & 15, cell.getY() & 15, cell.getZ() & 15);
            this.pipeline.handleBlockChange(SectionPos.of(cell), section,
                    cell.getX() & 15, cell.getY() & 15, cell.getZ() & 15, state, state);
            this.wakeUpObjectsAt(cell.getX(), cell.getY(), cell.getZ());
        }
    }
}
