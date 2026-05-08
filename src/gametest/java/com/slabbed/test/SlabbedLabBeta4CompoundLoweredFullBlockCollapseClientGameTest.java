package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Opt-in RED/GREEN proof for the named legal state
 * <em>Compound Lowered Full Block on Lowered Bottom Slab Carrier</em>.
 *
 * <p>Live recorder evidence at base {@code 9bf3bdc} captured a freshly placed
 * ordinary {@code minecraft:stone} on a legal lowered bottom slab carrier
 * showing {@code placedDy=-1.0} at the {@code BlockItem.place} return and then
 * collapsing to {@code placedDy=-0.5} once {@code Block.onPlaced} recorded the
 * persistent full-block anchor. The visible jump matches Julia's symptom.
 *
 * <p>Topology mirrored here:
 * <ul>
 *   <li>{@code BASE_FULL_SUPPORT}: ordinary bottom slab support</li>
 *   <li>{@code BASE_FULL}: anchored ordinary full block (dy=-0.5)</li>
 *   <li>{@code LOWERED_BOTTOM_SLAB}: bottom slab on top of an anchored
 *       lowered full block — registers as a
 *       {@code persistentLoweredBottomSlabCarrier}, dy=-0.5</li>
 *   <li>{@code PLACED_FULL}: ordinary stone placed directly above the lowered
 *       bottom slab carrier; mirrors {@code BlockOnPlacedAnchorMixin} by
 *       calling {@link SlabAnchorAttachment#addAnchor} after the
 *       {@code setBlockState}</li>
 * </ul>
 *
 * <p>Markers:
 * <ul>
 *   <li>{@code [BETA4_COMPOUND_LOWERED_FULL_BLOCK_COLLAPSE_RED]} —
 *       observed compound case collapsed to {@code dy=-0.5}</li>
 *   <li>{@code [BETA4_COMPOUND_LOWERED_FULL_BLOCK_COLLAPSE_GREEN]} —
 *       compound case preserved at {@code dy=-1.0}</li>
 * </ul>
 *
 * <p>Property: {@code -Dslabbed.beta4CompoundLoweredFullBlockCollapseRedOnly=true}.
 * The proof is a no-op when the property is not set; it does not run as part
 * of the default {@code runClientGameTest} batch.
 *
 * <p>This gametest does not change placement, persistence, retargeting,
 * raycast, outline, model, or {@code SlabSupport} grammar; it is evidence
 * only.
 */
public final class SlabbedLabBeta4CompoundLoweredFullBlockCollapseClientGameTest
        implements FabricClientGameTest {

    private static final String PROOF = "BETA4_COMPOUND_LOWERED_FULL_BLOCK_COLLAPSE";
    private static final String TRIAD_PROOF = "BETA4_COMPOUND_LOWERED_FULL_BLOCK_TRIAD";
    private static final String OPT_IN_PROPERTY =
            "slabbed.beta4CompoundLoweredFullBlockCollapseRedOnly";
    private static final String TRIAD_OPT_IN_PROPERTY =
            "slabbed.beta4CompoundLoweredFullBlockTriadRedOnly";
    private static final double EPSILON = 1.0e-6d;

    private static final BlockPos BASE_FULL_SUPPORT = new BlockPos(8, 200, 8);
    private static final BlockPos BASE_FULL = BASE_FULL_SUPPORT.up();
    private static final BlockPos LOWERED_BOTTOM_SLAB = BASE_FULL.up();
    private static final BlockPos PLACED_FULL = LOWERED_BOTTOM_SLAB.up();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        boolean collapseProof = Boolean.getBoolean(OPT_IN_PROPERTY);
        boolean triadProof = Boolean.getBoolean(TRIAD_OPT_IN_PROPERTY);
        if (!collapseProof && !triadProof) {
            return;
        }
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            runProof(ctx, singleplayer, triadProof);
        }
    }

    private static void runProof(ClientGameTestContext ctx, TestSingleplayerContext singleplayer, boolean triadProof) {
        singleplayer.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            clearArea(world);

            // Seed a legal anchored lowered full block under a lowered bottom
            // slab carrier so the slab carrier qualifies as a real
            // persistentLoweredBottomSlabCarrier rather than a synthetic
            // attachment-only fixture.
            world.setBlockState(
                    BASE_FULL_SUPPORT,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(BASE_FULL, Blocks.STONE.getDefaultState(),
                    Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, BASE_FULL,
                    world.getBlockState(BASE_FULL));

            world.setBlockState(
                    LOWERED_BOTTOM_SLAB,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, LOWERED_BOTTOM_SLAB,
                    world.getBlockState(LOWERED_BOTTOM_SLAB));

            // Mirror the live placement flow: setBlockState then addAnchor in
            // the same way BlockOnPlacedAnchorMixin runs at Block.onPlaced
            // HEAD inside BlockItem.place. This is the exact authoring path
            // that turned dy=-1.0 (place-return) into dy=-0.5 (after-tick) in
            // the live recorder run.
            world.setBlockState(PLACED_FULL, Blocks.STONE.getDefaultState(),
                    Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, PLACED_FULL,
                    world.getBlockState(PLACED_FULL));
        });

        for (int i = 0; i < 5; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        singleplayer.getServer().runOnServer(server -> assertCompound(server.getOverworld(), "server-stable"));
        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("PROOF_GAP: " + PROOF + " client world null");
            }
            assertCompound(mc.world, "client-stable");
        });
        if (triadProof) {
            syncHeldMainHand(ctx, singleplayer, ItemStack.EMPTY);
            syncPlayerPosition(ctx, singleplayer);
            ctx.runOnClient(mc -> {
                if (mc.world == null || mc.player == null || mc.gameRenderer == null) {
                    throw new RuntimeException("PROOF_GAP: " + TRIAD_PROOF + " client not ready");
                }
                mc.gameRenderer.updateCrosshairTarget(0.0f);
                assertCompoundTriad(mc.world, mc.player, mc.crosshairTarget);
            });
        }
    }

    private static void assertCompound(World world, String phase) {
        BlockState carrierSlab = world.getBlockState(LOWERED_BOTTOM_SLAB);
        BlockState placedFull = world.getBlockState(PLACED_FULL);

        boolean carrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                world, LOWERED_BOTTOM_SLAB, carrierSlab);
        boolean carrierBottom = SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(
                world, LOWERED_BOTTOM_SLAB, carrierSlab);
        double sourceDy = SlabSupport.getYOffset(world, LOWERED_BOTTOM_SLAB, carrierSlab);
        double placedDy = SlabSupport.getYOffset(world, PLACED_FULL, placedFull);
        boolean placedAnchored = SlabAnchorAttachment.isAnchored(world, PLACED_FULL);
        boolean placedFullAnchorTruth = placedAnchored
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, PLACED_FULL, placedFull);
        String placedSourceMode;
        if (carrier && carrierBottom) {
            placedSourceMode = placedAnchored
                    ? "dynamicLoweredOrAnchored"
                    : (Math.abs(placedDy + 1.0d) <= EPSILON ? "compoundLoweredFullBlock" : "normal");
        } else {
            placedSourceMode = placedAnchored ? "dynamicLoweredOrAnchored" : "normal";
        }

        double expectedDy = -1.0d;
        boolean red = Math.abs(placedDy - expectedDy) > EPSILON;
        String classification = red ? "RED" : "GREEN";
        String fullMarker = "[" + PROOF + "_" + classification + "]";
        String side = world.isClient() ? "CLIENT" : "SERVER";

        System.out.println(fullMarker
                + " phase=" + phase
                + " side=" + side
                + " sourceSlabPos=" + LOWERED_BOTTOM_SLAB.toShortString()
                + " sourceSlabState=" + carrierSlab
                + " sourceDy=" + sourceDy
                + " sourcePersistentLoweredSlabCarrier=" + carrier
                + " sourcePersistentLoweredBottomSlabCarrier=" + carrierBottom
                + " placedPos=" + PLACED_FULL.toShortString()
                + " placedState=" + placedFull
                + " placedDy=" + placedDy
                + " placedPersistentFullBlockAnchor=" + placedFullAnchorTruth
                + " placedSourceMode=" + placedSourceMode
                + " expectedDy=" + expectedDy
                + " classification=" + classification
                + " anchorFinalization="
                + (placedAnchored ? "after_block_on_placed_anchor_added" : "no_anchor"));

        if (!carrier || !carrierBottom || Math.abs(sourceDy + 0.5d) > EPSILON) {
            throw new RuntimeException("PROOF_GAP: " + PROOF
                    + " expected legal lowered bottom slab carrier source for " + phase
                    + " carrier=" + carrier
                    + " carrierBottom=" + carrierBottom
                    + " sourceDy=" + sourceDy
                    + " state=" + carrierSlab);
        }
        if (!placedFull.isOf(Blocks.STONE)) {
            throw new RuntimeException("PROOF_GAP: " + PROOF
                    + " expected real placed STONE at " + PLACED_FULL.toShortString()
                    + " state=" + placedFull
                    + " phase=" + phase);
        }
        if (!placedAnchored) {
            throw new RuntimeException("PROOF_GAP: " + PROOF
                    + " expected placed full block to be anchored after Block.onPlaced authoring path"
                    + " (mirrors BlockOnPlacedAnchorMixin) for " + phase
                    + " state=" + placedFull);
        }
        if (red) {
            throw new RuntimeException(fullMarker
                    + " phase=" + phase
                    + " ordinary full block above lowered bottom slab carrier collapsed to dy=" + placedDy
                    + " expected " + expectedDy
                    + " sourceDy=" + sourceDy
                    + " placedState=" + placedFull
                    + " sourcePersistentLoweredBottomSlabCarrier=" + carrierBottom);
        }
    }

    private static void assertCompoundTriad(World world, Entity player, HitResult selectedTarget) {
        BlockState sourceSlab = world.getBlockState(LOWERED_BOTTOM_SLAB);
        BlockState placedFull = world.getBlockState(PLACED_FULL);
        double sourceDy = SlabSupport.getYOffset(world, LOWERED_BOTTOM_SLAB, sourceSlab);
        double placedDy = SlabSupport.getYOffset(world, PLACED_FULL, placedFull);
        double modelDy = ClientDy.dyFor(world, PLACED_FULL, placedFull);
        boolean sourceCarrier = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                world, LOWERED_BOTTOM_SLAB, sourceSlab);
        boolean sourceBottomCarrier = SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(
                world, LOWERED_BOTTOM_SLAB, sourceSlab);
        boolean placedAnchor = SlabAnchorAttachment.isAnchored(world, PLACED_FULL);
        boolean persistentFullBlockAnchor = placedAnchor
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, PLACED_FULL, placedFull);

        Vec3d rayStart = player.getCameraPosVec(0.0f);
        Vec3d rayEnd = rayStart.add(player.getRotationVec(0.0f).multiply(6.0d));
        VoxelShape outlineShape = placedFull.getOutlineShape(world, PLACED_FULL);
        VoxelShape raycastShape = placedFull.getRaycastShape(world, PLACED_FULL);
        BlockHitResult outlineHit = outlineShape.raycast(rayStart, rayEnd, PLACED_FULL);
        BlockHitResult raycastHit = raycastShape.raycast(rayStart, rayEnd, PLACED_FULL);
        HitResult vanillaOutlineTarget = world.raycast(new RaycastContext(
                rayStart,
                rayEnd,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player));

        String expectedOwner = PLACED_FULL.toShortString();
        String outlineOwner = ownerOf(outlineHit);
        String raycastOwner = ownerOf(raycastHit);
        String vanillaOutlineOwner = ownerOf(vanillaOutlineTarget);
        String selectedOwner = ownerOf(selectedTarget);
        Box outlineBox = outlineShape.isEmpty() ? null : outlineShape.getBoundingBox();
        Box raycastBox = raycastShape.isEmpty() ? null : raycastShape.getBoundingBox();
        boolean outlineShifted = matchesBox(outlineBox, 0.0d, -1.0d, 0.0d, 1.0d, 0.0d, 1.0d);
        boolean raycastShifted = matchesBox(raycastBox, 0.0d, -1.0d, 0.0d, 1.0d, 0.0d, 1.0d);
        boolean outlineHitOwner = expectedOwner.equals(outlineOwner);
        boolean raycastHitOwner = expectedOwner.equals(raycastOwner);
        boolean selectedOwnerMatches = expectedOwner.equals(selectedOwner);
        boolean legalCompound = placedFull.isOf(Blocks.STONE)
                && Math.abs(sourceDy + 0.5d) <= EPSILON
                && sourceCarrier
                && sourceBottomCarrier
                && Math.abs(placedDy + 1.0d) <= EPSILON
                && Math.abs(modelDy + 1.0d) <= EPSILON
                && persistentFullBlockAnchor;

        String facts = " sourceSlabPos=" + LOWERED_BOTTOM_SLAB.toShortString()
                + " sourceSlabState=" + sourceSlab
                + " sourceDy=" + sourceDy
                + " sourcePersistentLoweredSlabCarrier=" + sourceCarrier
                + " sourcePersistentLoweredBottomSlabCarrier=" + sourceBottomCarrier
                + " placedPos=" + PLACED_FULL.toShortString()
                + " placedState=" + placedFull
                + " placedDy=" + placedDy
                + " modelDy=" + modelDy
                + " persistentFullBlockAnchor=" + persistentFullBlockAnchor
                + " expectedDy=-1.0"
                + " outlineShape=" + formatBox(outlineBox)
                + " outlineShiftedDyMinusOne=" + outlineShifted
                + " outlineHit=" + (outlineHit != null)
                + " outlineHitDesc=" + describeHit(outlineHit)
                + " outlineOwner=" + outlineOwner
                + " raycastShape=" + formatBox(raycastBox)
                + " raycastShapeEmpty=" + raycastShape.isEmpty()
                + " raycastShiftedDyMinusOne=" + raycastShifted
                + " raycastHit=" + (raycastHit != null)
                + " raycastHitDesc=" + describeHit(raycastHit)
                + " raycastOwner=" + raycastOwner
                + " selectedOwner=" + selectedOwner
                + " selectedOwnerExpected=" + expectedOwner
                + " selectedOwnerMatches=" + selectedOwnerMatches
                + " selectedTargetType=" + (selectedTarget == null ? "null" : selectedTarget.getType())
                + " vanillaOutlineOwner=" + vanillaOutlineOwner
                + " vanillaOutlineHitType=" + (vanillaOutlineTarget == null ? "null" : vanillaOutlineTarget.getType())
                + " rayStart=" + rayStart
                + " rayEnd=" + rayEnd;

        if (legalCompound && outlineShifted && outlineHitOwner
                && (!raycastShifted || !raycastHitOwner || raycastShape.isEmpty()
                || !selectedOwnerMatches)) {
            String classifiedFacts = " classification=TRIAD_RED" + facts;
            System.out.println("[" + TRIAD_PROOF + "_RED]" + classifiedFacts);
            throw new RuntimeException("[" + TRIAD_PROOF + "_RED]" + classifiedFacts);
        }

        if (legalCompound && outlineShifted && raycastShifted
                && outlineHitOwner && raycastHitOwner && selectedOwnerMatches) {
            System.out.println("[" + TRIAD_PROOF + "_GREEN]"
                    + " classification=TRIAD_GREEN" + facts);
            return;
        }

        throw new RuntimeException("PROOF_GAP: " + TRIAD_PROOF + facts
                + " legalCompound=" + legalCompound
                + " outlineShifted=" + outlineShifted
                + " raycastShifted=" + raycastShifted
                + " outlineHitOwner=" + outlineHitOwner
                + " raycastHitOwner=" + raycastHitOwner
                + " selectedOwnerMatches=" + selectedOwnerMatches);
    }

    private static String ownerOf(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            return "MISS";
        }
        return blockHit.getBlockPos().toShortString();
    }

    private static String describeHit(BlockHitResult hit) {
        if (hit == null) {
            return "MISS";
        }
        return hit.getBlockPos().toShortString() + "/" + hit.getSide().asString()
                + "@" + hit.getPos();
    }

    private static String formatBox(Box box) {
        if (box == null) {
            return "empty";
        }
        return "min=(" + box.minX + "," + box.minY + "," + box.minZ + ")"
                + ",max=(" + box.maxX + "," + box.maxY + "," + box.maxZ + ")";
    }

    private static boolean matchesBox(
            Box box,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        return box != null
                && Math.abs(box.minX - minX) <= EPSILON
                && Math.abs(box.minY - minY) <= EPSILON
                && Math.abs(box.minZ - minZ) <= EPSILON
                && Math.abs(box.maxX - maxX) <= EPSILON
                && Math.abs(box.maxY - maxY) <= EPSILON
                && Math.abs(box.maxZ - maxZ) <= EPSILON;
    }

    private static void syncPlayerPosition(ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        double x = PLACED_FULL.getX() - 1.25d;
        double y = PLACED_FULL.getY() - 2.12d;
        double z = PLACED_FULL.getZ() + 0.5d;
        float yaw = -90.0f;
        float pitch = 0.0f;
        singleplayer.getServer().runOnServer(server -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                return;
            }
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.refreshPositionAndAngles(x, y, z, yaw, pitch);
            serverPlayer.setVelocity(Vec3d.ZERO);
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.refreshPositionAndAngles(x, y, z, yaw, pitch);
                mc.player.setVelocity(Vec3d.ZERO);
            }
        });
        ctx.waitTick();
    }

    private static void syncHeldMainHand(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            ItemStack stack
    ) {
        ItemStack held = stack == null ? ItemStack.EMPTY : stack.copy();
        singleplayer.getServer().runOnServer(server -> {
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                server.getPlayerManager().getPlayerList().get(0).setStackInHand(Hand.MAIN_HAND, held.copy());
            }
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.setStackInHand(Hand.MAIN_HAND, held.copy());
            }
        });
        ctx.waitTick();
    }

    private static void clearArea(World world) {
        for (int x = -1; x <= 2; x++) {
            for (int y = -1; y <= 5; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = BASE_FULL_SUPPORT.add(x, y, z);
                    SlabAnchorAttachment.removeAnchor(world, pos);
                    SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, pos);
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
    }
}
