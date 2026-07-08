package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * F1+F2 port (audit STATE_DEFENSE_DIVERGENCE_2026-07-07, sweep findings): the "haunted cells" pair.
 *
 * <p>F2 — the LOWERED_SLAB_CARRIER marker was IMMORTAL: {@code removeAnchor} cleared six attachment
 * types but not the carrier, and {@code removePersistentLoweredSlabCarrier} had zero callers — so the
 * marker outlived break/re-place cycles and re-lowered fresh slabs placed at old lane positions
 * (misplacement that accumulates precisely in build-break-rebuild areas — Maintainer's churn).
 *
 * <p>F1 — the false-support contradiction: a slab could be FROZEN_FLAT (renders 0.0) while carrying
 * the carrier marker, and every support READER trusted the marker without consulting the freeze — so
 * objects placed on a visually-flush slab sank 0.5–1.0 into it, route-dependently ("random" merging).
 * The invariant is folded INTO the shared predicate ({@code isPersistentLoweredSlabCarrier} — the
 * shared-predicate half-fix lesson): a frozen-flat slab is NOT a lowered carrier, for every consumer.
 */
public final class HauntedCarrierCellTest {

    private static final double EPS = 1.0e-6;

    private static BlockState bottomSlab() {
        return Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static void place(GameTestHelper helper, ItemStack stack, BlockPos clicked, Direction face) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(
                        net.minecraft.world.phys.Vec3.atCenterOf(clicked).add(0, 0.5, 0), face, clicked, false)));
    }

    private static void onPlaced(ServerLevel w, BlockPos pos) {
        SlabAnchorAttachment.addAnchor(w, pos, w.getBlockState(pos));
        SlabAnchorAttachment.freezeLoweredOnPlace(w, pos, w.getBlockState(pos));
    }

    /** A real lowered slab lane: bottom slab anchored lowered on a lowered-carrier support. */
    private static BlockPos buildLoweredLaneSlab(GameTestHelper helper, ServerLevel w, int x, int z) {
        BlockPos ground = helper.absolutePos(new BlockPos(x, 1, z));
        BlockPos dirt = helper.absolutePos(new BlockPos(x, 2, z));
        BlockPos lane = helper.absolutePos(new BlockPos(x, 3, z));
        w.setBlock(ground, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, dirt, w.getBlockState(dirt));
        w.setBlock(lane, bottomSlab(), 2);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, lane, w.getBlockState(lane));
        return lane;
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void breakingACarrierSlabClearsTheMarker(GameTestHelper helper) {
        // F2: the marker must die with its slab — no haunting.
        ServerLevel w = helper.getLevel();
        BlockPos lane = buildLoweredLaneSlab(helper, w, 2, 2);
        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, lane, w.getBlockState(lane))) {
            throw helper.assertionException("precondition: the lane slab must be a carrier");
        }
        w.destroyBlock(lane, false);
        // Flatten the context FOR REAL: destroy the anchored dirt (same-kind setBlock never fires the
        // removal hook) AND the ground bottom-slab (any full block resting on a bottom slab is
        // live-lowered, which would make the re-placed lane slab legitimately qualify again) — the
        // rebuilt column must be flush stone->dirt->slab so only a stale marker could lower it.
        w.destroyBlock(lane.below(), false);
        w.destroyBlock(lane.below().below(), false);
        w.setBlock(lane.below().below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        w.setBlock(lane.below(), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        w.setBlock(lane, bottomSlab(), Block.UPDATE_CLIENTS);
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, lane, w.getBlockState(lane))) {
            throw helper.assertionException(
                    "F2: a carrier marker MUST be cleared when its slab is broken — a fresh slab at the old "
                            + "position must not inherit phantom lowering (the haunted-cell accumulation)");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void frozenFlatSlabIsNeverALoweredCarrier(GameTestHelper helper) {
        // F1: the false-support contradiction, reconstructed via the REAL placement route (click the
        // top of the ground beside the lane — no WYSIWYG side-click, so the slab freezes FLAT — while
        // the RETURN-hook qualifier writes the carrier marker from the adjacent lane).
        ServerLevel w = helper.getLevel();
        BlockPos lane = buildLoweredLaneSlab(helper, w, 2, 2);
        BlockPos groundBeside = helper.absolutePos(new BlockPos(3, 2, 2));
        w.setBlock(groundBeside, Blocks.STONE.defaultBlockState(), 2);
        BlockPos flat = groundBeside.above();
        place(helper, new ItemStack(Items.OAK_SLAB), groundBeside, Direction.UP);
        if (w.getBlockState(flat).isAir()) {
            throw helper.assertionException("premise: the beside-slab placement must succeed");
        }
        onPlaced(w, flat);
        SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(w, flat, w.getBlockState(flat));
        if (Math.abs(SlabSupport.getYOffset(w, flat, w.getBlockState(flat))) > EPS) {
            throw helper.assertionException("premise: the beside-slab must read FLUSH (frozen flat), got "
                    + SlabSupport.getYOffset(w, flat, w.getBlockState(flat)));
        }
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, flat, w.getBlockState(flat))) {
            throw helper.assertionException(
                    "F1: a FROZEN-FLAT slab must never read as a lowered carrier (support readers trust the "
                            + "marker and sink objects INTO the flush slab)");
        }
        // The observable harm, closed: a torch placed on the flush slab sits ON it (-0.5), never -1.0
        // inside it.
        BlockPos torch = flat.above();
        place(helper, new ItemStack(Items.TORCH), flat, Direction.UP);
        if (w.getBlockState(torch).isAir()) {
            throw helper.assertionException("premise: the torch placement must succeed");
        }
        double dy = SlabSupport.getYOffset(w, torch, w.getBlockState(torch));
        if (Math.abs(dy + 0.5) > EPS) {
            throw helper.assertionException(
                    "F1: a torch on a flush frozen-flat slab must sit ON it at -0.5, got " + dy
                            + " (a deeper value means the false-support marker sank it into the slab)");
        }
        helper.succeed();
    }
}
