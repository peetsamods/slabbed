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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RED proof for vertical lowered full-block selection ownership.
 */
public final class SlabbedLabVerticalChainHitboxOwnershipClientGameTest implements FabricClientGameTest {
    private static final BlockPos FIXTURE_ORIGIN = new BlockPos(12, 200, 0);

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            Path screenshotDir = SlabbedLabClientGameTest.resolveClientGameTestScreenshotDir();
            singleplayer.getClientWorld().waitForChunksRender();
            runProof(ctx, singleplayer, screenshotDir);
        }
    }

    private static void runProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir
    ) {
        final String testId = "vertical_chain_hitbox_ownership";
        final BlockPos s1Pos = FIXTURE_ORIGIN;
        final BlockPos b1Pos = s1Pos.up();
        final BlockPos b2Pos = b1Pos.up();
        final BlockPos b3Pos = b2Pos.up();

        setupFixture(singleplayer, s1Pos, b1Pos, b2Pos, b3Pos, true);
        settle(ctx, singleplayer, 4);
        Frame presentFrame = captureFrame(ctx, "b1_present", s1Pos, b1Pos, b2Pos, b3Pos);
        ctx.takeScreenshot(testId + "_b1_present");

        List<OwnershipRow> rows = new ArrayList<>();
        rows.add(captureAim(ctx, "b2_lower_quarter_b1_present", "lower quarter of B2 visible box",
                b2Pos, b2Pos, true, presentFrame.b2().yAt(0.25d)));
        rows.add(captureAim(ctx, "b2_center_b1_present", "center of B2 visible box",
                b2Pos, b2Pos, true, presentFrame.b2().yAt(0.50d)));
        rows.add(captureAim(ctx, "b2_upper_quarter_b1_present", "upper quarter of B2 visible box",
                b2Pos, b2Pos, true, presentFrame.b2().yAt(0.75d)));

        rows.add(captureAim(ctx, "b3_lower_quarter_b1_present", "lower quarter of B3 visible box",
                b3Pos, b3Pos, true, presentFrame.b3().yAt(0.25d)));
        rows.add(captureAim(ctx, "b3_center_b1_present", "center of B3 visible box",
                b3Pos, b3Pos, true, presentFrame.b3().yAt(0.50d)));
        rows.add(captureAim(ctx, "b3_upper_quarter_b1_present", "upper quarter of B3 visible box",
                b3Pos, b3Pos, true, presentFrame.b3().yAt(0.75d)));

        setupFixture(singleplayer, s1Pos, b1Pos, b2Pos, b3Pos, false);
        settle(ctx, singleplayer, 4);
        Frame absentFrame = captureFrame(ctx, "b1_removed", s1Pos, b1Pos, b2Pos, b3Pos);
        ctx.takeScreenshot(testId + "_b1_removed");
        rows.add(captureAim(ctx, "b2_lower_quarter_b1_removed", "same lower quarter of B2 visible box after B1 removal",
                b2Pos, b2Pos, false, absentFrame.b2().yAt(0.25d)));

        boolean b2PresentWrong = rows.stream()
                .filter(row -> row.lowerBlockPresent() && row.expectedOwner().equals(b2Pos))
                .anyMatch(row -> !row.actualOwner().equals(b2Pos));
        boolean b2RemovedCorrect = rows.stream()
                .filter(row -> !row.lowerBlockPresent() && row.expectedOwner().equals(b2Pos))
                .anyMatch(row -> row.actualOwner().equals(b2Pos));
        boolean exactHalfBlockShift = rows.stream()
                .anyMatch(row -> row.actualOwner().equals(row.expectedOwner().down()));
        boolean red = (b2PresentWrong && b2RemovedCorrect) || exactHalfBlockShift;
        String verdict = red
                ? "RED: vertical lowered full-block visible ownership resolves to the lower block"
                : "GREEN: B2/B3 visible lower, center, and upper rays resolved to their visible owners";

        List<SlabbedLabClientGameTest.NoteField> fields = new ArrayList<>();
        fields.add(new SlabbedLabClientGameTest.NoteField("proofId", testId));
        fields.add(new SlabbedLabClientGameTest.NoteField("s1Pos", s1Pos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("b1Pos", b1Pos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("b2Pos", b2Pos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("b3Pos", b3Pos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("b1PresentFrame", presentFrame.describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("b1RemovedFrame", absentFrame.describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("b2PresentWrongOwner", Boolean.toString(b2PresentWrong)));
        fields.add(new SlabbedLabClientGameTest.NoteField("b2RemovedLowerQuarterCorrect", Boolean.toString(b2RemovedCorrect)));
        fields.add(new SlabbedLabClientGameTest.NoteField("exactHalfBlockShift", Boolean.toString(exactHalfBlockShift)));
        fields.add(new SlabbedLabClientGameTest.NoteField("ghostWindowPlacementIssue",
                "not proven here; this proof exercises selection ownership only"));
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).appendFields(fields, "row_" + String.format("%02d", i));
        }
        fields.add(new SlabbedLabClientGameTest.NoteField("verdict", verdict));

        SlabbedLabClientGameTest.writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "vertical lowered full-block selection ownership",
                "Visible lower, center, and upper portions of B2/B3 should resolve to the visible full-block owner.",
                testId + "_b1_present",
                testId + "_b1_removed",
                fields,
                !red);

        if (red) {
            throw new RuntimeException("[" + testId + "] " + verdict);
        }
    }

    private static void setupFixture(
            TestSingleplayerContext singleplayer,
            BlockPos s1Pos,
            BlockPos b1Pos,
            BlockPos b2Pos,
            BlockPos b3Pos,
            boolean includeB1
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(s1Pos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            if (includeB1) {
                world.setBlockState(b1Pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.addAnchor(world, b1Pos, world.getBlockState(b1Pos));
            } else {
                world.setBlockState(b1Pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                SlabAnchorAttachment.removeAnchor(world, b1Pos);
            }
            world.setBlockState(b2Pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(b3Pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, b2Pos, world.getBlockState(b2Pos));
            SlabAnchorAttachment.addAnchor(world, b3Pos, world.getBlockState(b3Pos));
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer server player list empty for vertical chain ownership proof");
            }
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        });
    }

    private static void settle(ClientGameTestContext ctx, TestSingleplayerContext singleplayer, int ticks) {
        for (int i = 0; i < ticks; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();
    }

    private static Frame captureFrame(
            ClientGameTestContext ctx,
            String label,
            BlockPos s1Pos,
            BlockPos b1Pos,
            BlockPos b2Pos,
            BlockPos b3Pos
    ) {
        AtomicReference<Frame> out = new AtomicReference<>(Frame.empty(label));
        ctx.runOnClient(mc -> {
            ClientWorld world = mc.world;
            out.set(new Frame(
                    label,
                    sample(world, s1Pos),
                    sample(world, b1Pos),
                    sample(world, b2Pos),
                    sample(world, b3Pos)));
        });
        return out.get();
    }

    private static BoxSample sample(ClientWorld world, BlockPos pos) {
        if (world == null) {
            return BoxSample.empty(pos, "no_client_world");
        }
        BlockState state = world.getBlockState(pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.absent());
        if (outline.isEmpty()) {
            return new BoxSample(pos, state.toString(), anchored, dy, "empty", pos.getY(), pos.getY());
        }
        Box box = outline.getBoundingBox();
        double minY = pos.getY() + box.minY;
        double maxY = pos.getY() + box.maxY;
        return new BoxSample(pos, state.toString(), anchored, dy, formatYRange(minY, maxY), minY, maxY);
    }

    private static OwnershipRow captureAim(
            ClientGameTestContext ctx,
            String label,
            String aimLabel,
            BlockPos targetPos,
            BlockPos expectedOwner,
            boolean lowerBlockPresent,
            double targetY
    ) {
        AtomicReference<OwnershipRow> out = new AtomicReference<>(OwnershipRow.blocked(
                label, aimLabel, expectedOwner, lowerBlockPresent, "not_run"));
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                out.set(OwnershipRow.blocked(label, aimLabel, expectedOwner, lowerBlockPresent,
                        "client_world_or_player_null"));
                return;
            }
            Vec3d eye = new Vec3d(targetPos.getX() + 2.75d, targetY, targetPos.getZ() + 0.5d);
            Vec3d target = new Vec3d(targetPos.getX() + 1.0d, targetY, targetPos.getZ() + 0.5d);
            positionPlayer(mc, eye, target);
            mc.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            Vec3d end = eye.add(target.subtract(eye).normalize().multiply(6.0d));
            HitResult vanilla = mc.world.raycast(new RaycastContext(
                    eye,
                    end,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult actual = mc.crosshairTarget;
            BlockPos actualOwner = owner(actual);
            String verdict = expectedOwner.equals(actualOwner)
                    ? "GREEN"
                    : "RED: expected " + expectedOwner.toShortString() + " got " + ownerLabel(actualOwner);
            out.set(new OwnershipRow(
                    label,
                    aimLabel,
                    fmtVec(eye),
                    fmtVec(target),
                    expectedOwner,
                    actualOwner,
                    describeHit(vanilla),
                    describeHit(actual),
                    lowerBlockPresent,
                    verdict));
        });
        return out.get();
    }

    private static void positionPlayer(MinecraftClient mc, Vec3d eye, Vec3d target) {
        Vec3d delta = target.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - mc.player.getStandingEyeHeight();
        mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        mc.player.raycast(6.0d, 0.0f, false);
    }

    private static BlockPos owner(HitResult hit) {
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos();
        }
        return null;
    }

    private static String describeHit(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return hit == null ? "null" : hit.getType().toString();
        }
        return "BLOCK owner=" + blockHit.getBlockPos().toShortString()
                + " face=" + blockHit.getSide().asString()
                + " hit=" + fmtVec(blockHit.getPos());
    }

    private static String ownerLabel(BlockPos pos) {
        return pos == null ? "none" : pos.toShortString();
    }

    private static String formatYRange(double minY, double maxY) {
        return String.format("%.4f..%.4f", minY, maxY);
    }

    private static String fmtVec(Vec3d vec) {
        return String.format("%.4f,%.4f,%.4f", vec.x, vec.y, vec.z);
    }

    private record Frame(String label, BoxSample s1, BoxSample b1, BoxSample b2, BoxSample b3) {
        static Frame empty(String label) {
            BlockPos zero = BlockPos.ORIGIN;
            return new Frame(
                    label,
                    BoxSample.empty(zero, "empty"),
                    BoxSample.empty(zero, "empty"),
                    BoxSample.empty(zero, "empty"),
                    BoxSample.empty(zero, "empty"));
        }

        String describe() {
            return label + " s1={" + s1.describe()
                    + "} b1={" + b1.describe()
                    + "} b2={" + b2.describe()
                    + "} b3={" + b3.describe() + "}";
        }
    }

    private record BoxSample(
            BlockPos pos,
            String state,
            boolean anchored,
            double dy,
            String visualOutlineY,
            double minY,
            double maxY
    ) {
        static BoxSample empty(BlockPos pos, String reason) {
            return new BoxSample(pos, reason, false, 0.0d, "empty", pos.getY(), pos.getY());
        }

        double yAt(double fraction) {
            return minY + (maxY - minY) * fraction;
        }

        String describe() {
            return "pos=" + pos.toShortString()
                    + " state=" + state
                    + " anchored=" + anchored
                    + " dy=" + dy
                    + " visualOutlineY=" + visualOutlineY;
        }
    }

    private record OwnershipRow(
            String label,
            String aimLabel,
            String eye,
            String target,
            BlockPos expectedOwner,
            BlockPos actualOwner,
            String vanillaOwner,
            String actualCrosshairOwner,
            boolean lowerBlockPresent,
            String verdict
    ) {
        static OwnershipRow blocked(
                String label,
                String aimLabel,
                BlockPos expectedOwner,
                boolean lowerBlockPresent,
                String reason
        ) {
            return new OwnershipRow(label, aimLabel, "none", "none", expectedOwner, null,
                    "not_run", reason, lowerBlockPresent, "BLOCKED: " + reason);
        }

        void appendFields(List<SlabbedLabClientGameTest.NoteField> fields, String prefix) {
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_label", label));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_aimPoint", aimLabel));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_eye", eye));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_target", target));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_expectedOwner", expectedOwner.toShortString()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_actualOwner", ownerLabel(actualOwner)));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_vanillaOwner", vanillaOwner));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_actualCrosshairOwner", actualCrosshairOwner));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_lowerBlockPresent", Boolean.toString(lowerBlockPresent)));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_verdict", verdict));
        }
    }
}
