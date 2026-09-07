package com.slabbed.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.slabbed.anchor.SlabAnchorAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A block a piston moves keeps the height it was placed at (LAW 1; maintainer ruling, 2026-09-06).
 *
 * <p>The stored placement height is a fact about a CELL, so a move must carry it from the cell the
 * block leaves to the cell it arrives in. Nothing else writes a fact at a piston destination, and
 * the read path is store-only — a destination with no fact resolves flat with no live fallback — so
 * without this the moved block does not merely pop, it is reset to flat permanently.
 *
 * <p>Invariants this class must keep:
 * <ul>
 *   <li><b>Read before any vanilla write.</b> The capture sits in the {@code resolve()} wrapper,
 *       which is the last point before vanilla's own {@code setBlock} calls. A multi-block push
 *       makes one block's source the next block's destination, and vanilla wipes each cell as it
 *       lays the push down, so a later read would see a half-updated store.</li>
 *   <li><b>Publish only on a move that happened.</b> A refused push (an immovable block, or past the
 *       resolver's push depth) returns false before moving anything and must store nothing.</li>
 *   <li><b>Present facts only.</b> Every destination is wiped by vanilla's own removal hook before
 *       this batch runs, which is the right answer: a height belongs to a block, and the block that
 *       was in the destination has left it.</li>
 * </ul>
 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonMoveDyTransferMixin {

    /**
     * One pending map per {@code moveBlocks} call. Assigned inside the {@code resolve()} wrapper,
     * which runs before any vanilla write, and taken-and-cleared at RETURN, which fires at BOTH
     * return sites. A throwing {@code setBlock} can therefore leave at most a stale map that the
     * next call overwrites or discards on a false return — it can never write a fact to a cell it
     * was not read for, which is why no depth counter is needed here.
     */
    @Unique
    private static final ThreadLocal<Map<BlockPos, Long>> SLABBED$PENDING_MOVES = new ThreadLocal<>();

    /**
     * {@code PistonStructureResolver.resolve()} occurs exactly once in {@code moveBlocks}, so this
     * target needs no ordinal. The four wrapped-method arguments all have distinct types, so the
     * sugar below resolves by type and needs no ordinal either.
     */
    @WrapOperation(
            method = "moveBlocks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;resolve()Z"
            )
    )
    private boolean slabbed$captureMovedFacts(
            PistonStructureResolver resolver,
            Operation<Boolean> original,
            @Local(argsOnly = true) Level world,
            @Local(argsOnly = true) BlockPos pistonPos,
            @Local(argsOnly = true) Direction pistonDirection,
            @Local(argsOnly = true) boolean extending
    ) {
        boolean accepted = original.call(resolver);
        if (!accepted || world == null || world.isClientSide()) {
            return accepted;
        }
        Map<BlockPos, Long> pending = new LinkedHashMap<>();
        Direction motion = resolver.getPushDirection();
        for (BlockPos source : resolver.getToPush()) {
            slabbed$carryPlacementDy(world, source, source.relative(motion), pending);
        }
        // getToDestroy() is deliberately never consulted: a destroyed block does not arrive anywhere,
        // and carrying its height would hand it to whatever lands in the cell beyond it.
        if (extending) {
            // The extended head is the base's own body, so it inherits the base's placed height and
            // the pair stays one object in the picture AND in its shapes (maintainer ruling,
            // 2026-09-06).
            slabbed$carryPlacementDy(world, pistonPos, pistonPos.relative(pistonDirection), pending);
        }
        SLABBED$PENDING_MOVES.set(pending);
        return true;
    }

    @Inject(method = "moveBlocks", at = @At("RETURN"))
    private void slabbed$publishMovedFacts(
            Level world,
            BlockPos pistonPos,
            Direction pistonDirection,
            boolean extending,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Map<BlockPos, Long> pending = SLABBED$PENDING_MOVES.get();
        SLABBED$PENDING_MOVES.remove();
        if (pending == null || pending.isEmpty()) {
            return;
        }
        // A refused push moves nothing and must store nothing.
        if (!Boolean.TRUE.equals(cir.getReturnValue()) || world == null || world.isClientSide()) {
            return;
        }
        // RETURN, not an earlier call site: the destroyed-cell removal hooks run late in the method,
        // so only RETURN is unambiguously after every vanilla write that could clear a cell this
        // batch writes. One attachment publication per chunk, whatever the push size.
        SlabAnchorAttachment.writePlacementDyBatch(world, pending);
    }

    @Unique
    private static void slabbed$carryPlacementDy(
            Level world,
            BlockPos source,
            BlockPos destination,
            Map<BlockPos, Long> pending
    ) {
        SlabAnchorAttachment.PlacementDyFact fact = SlabAnchorAttachment.rawPlacementDyFact(world, source);
        if (fact.present()) {
            // Present-only is sufficient AND correct: vanilla writes its animation stand-in at every
            // destination with an update flag carrying the moved-by-piston bit, which fires the
            // removal hook and wipes whatever the destination held before this batch runs. Do not
            // re-add a "keep what is already there" fallback — a height belongs to a block, and the
            // block that was in the destination has left it (LAW 1).
            pending.put(destination.immutable(), fact.rawBits());
        }
    }
}
