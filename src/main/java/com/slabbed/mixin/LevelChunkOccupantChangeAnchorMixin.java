package com.slabbed.mixin;

import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A cell that changes occupant never keeps the height stored for the occupant that left.
 *
 * <p>The clear sits at the ONE write funnel every removal passes, not at the removal hook the
 * departing block happens to run: 28 vanilla block classes override
 * {@code affectNeighborsAfterRemoval} without calling the base version, and vanilla reaches that
 * hook at all only when {@code (flags & UPDATE_NEIGHBORS) != 0 || movedByPiston}, so a class-keyed
 * or hook-keyed clear is blind twice over (maintainer ruling, 2026-09-06).
 *
 * <p>Invariants:
 * <ul>
 *   <li><b>Block kind, never properties.</b> A property-only rewrite is the same block still
 *       standing there; touching its height would be the LAW 1 violation. Do not re-add vanilla's
 *       own {@code || newBlock instanceof BaseRailBlock} clause here — it would judge a rail on its
 *       own shape rewrite and clear a rail that never moved.</li>
 *   <li><b>Update flags do not gate it.</b> Flags govern notification, not the store. Do not re-add
 *       a flag gate: {@code /setblock ... strict} and the piston's own low-flag writes empty a cell
 *       without any of the bits vanilla's hook gate tests.</li>
 *   <li><b>HEAD, not a call site.</b> Every input is an argument plus one state read, so no ordinal
 *       and no captured local can be invalidated by a bytecode reshuffle.</li>
 *   <li><b>Only this cell.</b> The decision reads {@code pos} and nothing around it, so no neighbour
 *       edit can move a placed block (LAW 1).</li>
 * </ul>
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkOccupantChangeAnchorMixin {

    @Inject(
            method = "setBlockState(Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;I)"
                    + "Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"))
    private void slabbed$clearDepartedOccupantFact(
            BlockPos pos, BlockState newState, int flags,
            CallbackInfoReturnable<BlockState> cir) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState oldState = chunk.getBlockState(pos);
        if (oldState.is(newState.getBlock())) {
            return;
        }
        SlabAnchorAttachment.clearFactForDepartedOccupant(
                level, chunk, pos, oldState, newState,
                (flags & Block.UPDATE_MOVE_BY_PISTON) != 0);
    }
}
