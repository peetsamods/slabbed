package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Q6 (targeting phase, audit STATE_DEFENSE_DIVERGENCE_2026-07-07): the offset-aware nearest-hit
 * raycast's ±1 window (each DDA cell C tests C, C.below(), C.above()) predates the dy=-1.5 marker
 * lanes — a -1.5-rendered owner's lower half lies TWO cells below it, so a near-horizontal ray
 * crossing only that band never tested the owner. The dy-triad law says model, outline, and raycast
 * move together; the marker phases moved the first two and owed this leg.
 *
 * <p>SUBJECT CHOICE (learned RED-first): floor TORCHES are the wrong subject — the support slab
 * deliberately unions a torch COMFORT OVERLAY into its own outline (slabbed$slabTorchComfortOverlay),
 * so deep-torch aims already resolve to the SUPPORT as a designed proxy (pinned below). The genuine
 * untargetable class is every deep object WITHOUT a proxy — fence gates here.
 *
 * <p>Tests call {@link SlabbedOffsetRaycast#raycast} directly — the exact unit the client pick
 * mixins consume (server {@code Level.clip} is deliberately vanilla).
 */
public final class OffsetRaycastDeepLaneTest {

    private static final double EPS = 1.0e-6;

    private static void place(GameTestHelper helper, ItemStack stack, BlockPos clicked, Direction face) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked).add(0, 0.5, 0), face, clicked, false)));
    }

    private static void bottomSlab(ServerLevel w, BlockPos pos) {
        w.setBlock(pos, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
    }

    /** The D3/F4 marked-slab rig + a real-useOn subject at -1.5 on it. Returns the subject pos. */
    private static BlockPos buildDeepSubject(GameTestHelper helper, ServerLevel w, ItemStack item) {
        BlockPos base = helper.absolutePos(new BlockPos(3, 1, 2));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(1));
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(3));
        BlockPos fb = base.above(4);
        w.setBlock(fb, Blocks.STONE.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, fb, w.getBlockState(fb));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(w, fb, w.getBlockState(fb));
        BlockPos support = fb.west();
        bottomSlab(w, support);
        SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(w, support, w.getBlockState(support),
                fb, w.getBlockState(fb));
        BlockPos subject = support.above();
        place(helper, item, support, Direction.UP);
        if (w.getBlockState(subject).isAir()) {
            throw helper.assertionException("scene premise: the subject must place on the marked slab");
        }
        double dy = SlabSupport.getYOffset(w, subject, w.getBlockState(subject));
        if (Math.abs(dy + 1.5) > EPS) {
            throw helper.assertionException("scene premise: the subject must read -1.5, got " + dy);
        }
        return subject;
    }

    /** A near-horizontal ray along +X at the given absolute Y and Z, stopping short of the fb column. */
    private static BlockHitResult clipAlongX(ServerLevel w, BlockPos subject, double y, double z) {
        Vec3 start = new Vec3(subject.getX() - 1.5, y, z);
        Vec3 end = new Vec3(subject.getX() + 0.9, y, z);
        return SlabbedOffsetRaycast.raycast(w, start, end, CollisionContext.empty());
    }

    /**
     * Q6 core RED: a ray through the DEEP band [gate.y-1.5, gate.y-1.0) — entirely inside
     * gate.below(2)'s cell — must hit the gate. The ±1 window never tested the owner from there,
     * and the marked support's outline ([-1.0,-0.5] shifted) sits below this band: total miss.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deepBandRayHitsTheFenceGate(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos gate = buildDeepSubject(helper, w, new ItemStack(Items.OAK_FENCE_GATE));
        // Placed facing the +Z mock player default: the bar runs full-x, z in [0.375, 0.625].
        BlockHitResult hit = clipAlongX(w, gate, gate.getY() - 1.25, gate.getZ() + 0.5);
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(gate)) {
            throw helper.assertionException("Q6: the deep-band ray must hit the -1.5 gate at "
                    + gate.toShortString() + ", got type=" + hit.getType()
                    + " pos=" + hit.getBlockPos().toShortString()
                    + " (the owner two cells up was outside the raycast window)");
        }
        helper.succeed();
    }

    /** Pin (green before AND after): the band inside gate.below() is covered by the ±1 window. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void upperBandRayAlreadyHitsTheFenceGate(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos gate = buildDeepSubject(helper, w, new ItemStack(Items.OAK_FENCE_GATE));
        BlockHitResult hit = clipAlongX(w, gate, gate.getY() - 0.95, gate.getZ() + 0.5);
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(gate)) {
            throw helper.assertionException("pin: the upper-band ray must hit the gate, got type="
                    + hit.getType() + " pos=" + hit.getBlockPos().toShortString());
        }
        helper.succeed();
    }

    /** No false positives: a deep-band ray outside the gate's z-footprint must not hit the gate. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deepBandRayPastTheFootprintStillMisses(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos gate = buildDeepSubject(helper, w, new ItemStack(Items.OAK_FENCE_GATE));
        BlockHitResult hit = clipAlongX(w, gate, gate.getY() - 1.25, gate.getZ() + 0.85);
        if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(gate)) {
            throw helper.assertionException(
                    "control: a deep-band ray outside the gate footprint must not hit the gate");
        }
        helper.succeed();
    }

    /**
     * Documentation pin (green before AND after): deep-torch aims resolve to the SUPPORT — the
     * designed comfort proxy (the slab unions the torch comfort box into its own outline). The
     * window widening must not flip this: ties keep the first-recorded hit (the support).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deepTorchRayResolvesToTheComfortProxySupport(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos torch = buildDeepSubject(helper, w, new ItemStack(Items.TORCH));
        BlockPos support = torch.below();
        BlockHitResult hit = clipAlongX(w, torch, torch.getY() - 1.25, torch.getZ() + 0.5);
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(support)) {
            throw helper.assertionException("comfort-proxy pin: the deep-torch ray must resolve to the "
                    + "SUPPORT at " + support.toShortString() + ", got type=" + hit.getType()
                    + " pos=" + hit.getBlockPos().toShortString());
        }
        helper.succeed();
    }
}
