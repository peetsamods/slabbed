package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Proof-only repro for slab-held upper-half B2 placement in a lowered vertical stack.
 */
public final class SlabbedLabB2UpperHalfGhostWindowClientGameTest implements FabricClientGameTest {
    private static final BlockPos FIXTURE_ORIGIN = new BlockPos(0, 200, 0);
    private static final double EPSILON = 1.0e-6;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            Path screenshotDir = SlabbedLabClientGameTest.resolveClientGameTestScreenshotDir();
            Set<String> knownScreenshotFiles = SlabbedLabClientGameTest.listScreenshotFileNames(screenshotDir);
            singleplayer.getClientWorld().waitForChunksRender();
            runProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles);
        }
    }

    private static void runProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles
    ) {
        final String testId = "slab_held_b2_upper_half_ghost_window";
        final BlockPos sPos = FIXTURE_ORIGIN;
        final BlockPos b1Pos = sPos.up();
        final BlockPos b2Pos = b1Pos.up();
        final BlockPos b3Pos = b2Pos.up();
        final Vec3d aimTarget = new Vec3d(b2Pos.getX() + 1.0, b2Pos.getY() + 0.45, b2Pos.getZ() + 0.5);
        final Vec3d eye = new Vec3d(b2Pos.getX() + 2.75, b2Pos.getY() + 0.45, b2Pos.getZ() + 0.5);

        AtomicReference<Frame> before = new AtomicReference<>(Frame.empty("before"));
        AtomicReference<Frame> afterClick1 = new AtomicReference<>(Frame.empty("afterClick1"));
        AtomicReference<Frame> afterClick2 = new AtomicReference<>(Frame.empty("afterClick2"));
        AtomicReference<String> targetBeforeClick1 = new AtomicReference<>("not_recorded");
        AtomicReference<String> targetBeforeClick2 = new AtomicReference<>("not_recorded");
        AtomicReference<String> click1Result = new AtomicReference<>("not_run");
        AtomicReference<String> click2Result = new AtomicReference<>("not_run");
        AtomicReference<String> expectedTarget = new AtomicReference<>(
                "B2 visible upper-half east face at " + formatVec(aimTarget));
        AtomicReference<String> verdict = new AtomicReference<>("BLOCKED");

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(sPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(b1Pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(b2Pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(b3Pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            clearSlabProbeVolume(world, b2Pos, b3Pos);
            SlabAnchorAttachment.addAnchor(world, b1Pos, world.getBlockState(b1Pos));
            SlabAnchorAttachment.addAnchor(world, b2Pos, world.getBlockState(b2Pos));
            SlabAnchorAttachment.addAnchor(world, b3Pos, world.getBlockState(b3Pos));
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer server player list empty for " + testId);
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(
                    Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
        });
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        before.set(capture(ctx, "before", sPos, b1Pos, b2Pos, b3Pos));
        targetBeforeClick1.set(aimAtAndDescribeCrosshair(ctx, eye, aimTarget));
        ctx.takeScreenshot(testId + "_before_click");
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                click1Result.set("BLOCKED: client interaction path unavailable");
                return;
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            HitResult target = mc.crosshairTarget;
            if (!(target instanceof BlockHitResult hit) || target.getType() != HitResult.Type.BLOCK) {
                click1Result.set("MISS_NO_CLICK target=" + describeHit(target));
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            click1Result.set(result.toString());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        afterClick1.set(capture(ctx, "afterClick1", sPos, b1Pos, b2Pos, b3Pos));
        ctx.takeScreenshot(testId + "_after_click1");

        targetBeforeClick2.set(aimAtAndDescribeCrosshair(ctx, eye, aimTarget));
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                click2Result.set("BLOCKED: client interaction path unavailable");
                return;
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            HitResult target = mc.crosshairTarget;
            if (!(target instanceof BlockHitResult hit) || target.getType() != HitResult.Type.BLOCK) {
                click2Result.set("MISS_NO_CLICK target=" + describeHit(target));
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            click2Result.set(result.toString());
        });
        for (int i = 0; i < 3; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();
        afterClick2.set(capture(ctx, "afterClick2", sPos, b1Pos, b2Pos, b3Pos));
        ctx.takeScreenshot(testId + "_after_click2");

        boolean setupOk = before.get().b1().dy() == -0.5
                && before.get().b2().dy() == -0.5
                && before.get().b3().dy() == -0.5
                && targetBeforeClick1.get().contains("blockPos=" + b2Pos.toShortString());
        boolean firstTargetedB3 = targetBeforeClick1.get().contains("blockPos=" + b3Pos.toShortString());
        boolean firstPlacedOffExpectedB2Side = afterClick1.get().hasSlabAwayFrom(b2Pos.east());
        boolean firstPlacedAgainstB3 = afterClick1.get().hasSlabAt(b3Pos);
        boolean secondMismatch = afterClick2.get().hasGhostWindowGap(b2Pos);
        boolean secondTargetDrifted = !targetBeforeClick2.get().contains("blockPos=" + b2Pos.toShortString())
                && !targetBeforeClick2.get().contains("blockPos=" + b2Pos.east().toShortString());
        if (!setupOk && !firstTargetedB3) {
            verdict.set("BLOCKED: setup or initial B2 upper-half crosshair did not match expected live repro");
        } else if (firstTargetedB3 || firstPlacedOffExpectedB2Side || firstPlacedAgainstB3
                || secondMismatch || secondTargetDrifted) {
            verdict.set("RED: slab-held B2 upper-half placement target/placement drift or ghost-window gap observed");
        } else {
            verdict.set("GREEN: slab-held B2 upper-half click stayed on B2 with no ghost-window gap");
        }

        List<SlabbedLabClientGameTest.NoteField> fields = new ArrayList<>();
        fields.add(new SlabbedLabClientGameTest.NoteField("proofId", testId));
        fields.add(new SlabbedLabClientGameTest.NoteField("expectedPlacementTarget", expectedTarget.get()));
        fields.add(new SlabbedLabClientGameTest.NoteField("sPos", sPos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("b1Pos", b1Pos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("b2Pos", b2Pos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("b3Pos", b3Pos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("beforeStateTable", before.get().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("click1CrosshairTarget", targetBeforeClick1.get()));
        fields.add(new SlabbedLabClientGameTest.NoteField("click1Result", click1Result.get()));
        fields.add(new SlabbedLabClientGameTest.NoteField("afterClick1StateTable", afterClick1.get().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("click1ExpectedSlabPos", b2Pos.east().toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("click1ActualSlabs", afterClick1.get().slabSummary()));
        fields.add(new SlabbedLabClientGameTest.NoteField("click1PlacedOffExpectedB2Side", Boolean.toString(firstPlacedOffExpectedB2Side)));
        fields.add(new SlabbedLabClientGameTest.NoteField("click1PlacedOnOrAgainstB3", Boolean.toString(firstPlacedAgainstB3)));
        fields.add(new SlabbedLabClientGameTest.NoteField("click2CrosshairTarget", targetBeforeClick2.get()));
        fields.add(new SlabbedLabClientGameTest.NoteField("click2Result", click2Result.get()));
        fields.add(new SlabbedLabClientGameTest.NoteField("afterClick2StateTable", afterClick2.get().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("click2ActualSlabs", afterClick2.get().slabSummary()));
        fields.add(new SlabbedLabClientGameTest.NoteField("ghostWindowGapIndicator", Boolean.toString(secondMismatch)));
        fields.add(new SlabbedLabClientGameTest.NoteField("targetDriftedBeforeClick2", Boolean.toString(secondTargetDrifted)));
        fields.add(new SlabbedLabClientGameTest.NoteField("verdict", verdict.get()));
        SlabbedLabClientGameTest.writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "slab-held B2 upper-half ghost-window proof",
                "Holding a slab and aiming at B2's visible upper half should not place against B3 or create a ghost-window gap.",
                testId + "_before_click",
                testId + "_after_click2",
                fields,
                verdict.get().startsWith("GREEN"));

        if (verdict.get().startsWith("RED") || verdict.get().startsWith("BLOCKED")) {
            throw new RuntimeException("[" + testId + "] " + verdict.get()
                    + " click1Target=" + targetBeforeClick1.get()
                    + " click2Target=" + targetBeforeClick2.get());
        }
    }

    private static void clearSlabProbeVolume(net.minecraft.world.World world, BlockPos b2Pos, BlockPos b3Pos) {
        for (BlockPos base : List.of(b2Pos, b3Pos)) {
            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockPos side = base.offset(direction);
                world.setBlockState(side, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(side.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(side.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        world.setBlockState(b3Pos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    private static String aimAtAndDescribeCrosshair(ClientGameTestContext ctx, Vec3d eye, Vec3d target) {
        AtomicReference<String> out = new AtomicReference<>("not_recorded");
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                out.set("null_world_or_player");
                return;
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            resolvePlayerRaycastFromEye(mc, eye, target, 6.0);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            out.set(describeHit(mc.crosshairTarget));
        });
        return out.get();
    }

    private static void resolvePlayerRaycastFromEye(
            net.minecraft.client.MinecraftClient mc, Vec3d eye, Vec3d target, double reach
    ) {
        if (mc.player == null) {
            return;
        }
        Vec3d delta = target.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - mc.player.getStandingEyeHeight();
        mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        mc.player.raycast(reach, 0.0f, false);
    }

    private static Frame capture(ClientGameTestContext ctx, String label, BlockPos sPos, BlockPos b1Pos,
                                 BlockPos b2Pos, BlockPos b3Pos) {
        AtomicReference<Frame> out = new AtomicReference<>(Frame.empty(label));
        ctx.runOnClient(mc -> {
            BoxSample s = sample(mc.world, sPos);
            BoxSample b1 = sample(mc.world, b1Pos);
            BoxSample b2 = sample(mc.world, b2Pos);
            BoxSample b3 = sample(mc.world, b3Pos);
            List<BoxSample> slabs = new ArrayList<>();
            for (BlockPos base : List.of(b2Pos, b3Pos)) {
                for (Direction direction : Direction.Type.HORIZONTAL) {
                    BlockPos side = base.offset(direction);
                    for (BlockPos slabPos : List.of(side.down(), side, side.up())) {
                        BoxSample sample = sample(mc.world, slabPos);
                        if (sample.isSlab() && slabs.stream().noneMatch(existing -> existing.pos().equals(slabPos))) {
                            slabs.add(sample);
                        }
                    }
                }
            }
            out.set(new Frame(label, s, b1, b2, b3, slabs));
        });
        return out.get();
    }

    private static BoxSample sample(net.minecraft.world.BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.absent());
        boolean outlineEmpty = outline.isEmpty();
        double minY = pos.getY() + (outlineEmpty ? 0.0 : outline.getBoundingBox().minY);
        double maxY = pos.getY() + (outlineEmpty ? 0.0 : outline.getBoundingBox().maxY);
        String slabType = state.contains(SlabBlock.TYPE) ? state.get(SlabBlock.TYPE).toString() : "none";
        return new BoxSample(pos, state.toString(), dy, anchored, minY, maxY,
                state.isOf(Blocks.STONE_SLAB), slabType, !state.isAir());
    }

    private static String describeHit(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return hit == null ? "null" : hit.getType().toString();
        }
        return "BLOCK blockPos=" + blockHit.getBlockPos().toShortString()
                + " face=" + blockHit.getSide().asString()
                + " hit=" + formatVec(blockHit.getPos());
    }

    private static String formatVec(Vec3d vec) {
        return String.format("%.4f, %.4f, %.4f", vec.x, vec.y, vec.z);
    }

    private record Frame(String label, BoxSample s, BoxSample b1, BoxSample b2, BoxSample b3,
                         List<BoxSample> slabs) {
        static Frame empty(String label) {
            BoxSample empty = new BoxSample(BlockPos.ORIGIN, "none", 0.0, false, 0.0, 0.0, false, "none", false);
            return new Frame(label, empty, empty, empty, empty, List.of());
        }

        boolean hasSlabAt(BlockPos pos) {
            return slabs.stream().anyMatch(slab -> slab.pos().equals(pos)
                    || slab.pos().equals(pos.east())
                    || slab.pos().equals(pos.west())
                    || slab.pos().equals(pos.north())
                    || slab.pos().equals(pos.south()));
        }

        boolean hasSlabAwayFrom(BlockPos expectedPos) {
            return slabs.stream().anyMatch(slab -> !slab.pos().equals(expectedPos));
        }

        boolean hasGhostWindowGap(BlockPos b2Pos) {
            for (BoxSample slab : slabs) {
                if (Math.abs(slab.minY() - b2.minY()) > EPSILON
                        && slab.minY() > b2.minY() + EPSILON
                        && slab.maxY() <= b2.maxY() + EPSILON
                        && slab.pos().getY() == b2Pos.getY()) {
                    return true;
                }
            }
            return false;
        }

        String slabSummary() {
            return slabs.stream().map(BoxSample::describe).toList().toString();
        }

        String describe() {
            return label
                    + " s={" + s.describe() + "}"
                    + " b1={" + b1.describe() + "}"
                    + " b2={" + b2.describe() + "}"
                    + " b3={" + b3.describe() + "}"
                    + " slabs=" + slabs.stream().map(BoxSample::describe).toList();
        }
    }

    private record BoxSample(BlockPos pos, String state, double dy, boolean anchored, double minY, double maxY,
                             boolean isSlab, String slabType, boolean nonAir) {
        String describe() {
            return "pos=" + pos.toShortString()
                    + " state=" + state
                    + " dy=" + dy
                    + " anchored=" + anchored
                    + " visualY=" + String.format("%.3f..%.3f", minY, maxY)
                    + " isSlab=" + isSlab
                    + " slabType=" + slabType
                    + " nonAir=" + nonAir;
        }
    }
}
