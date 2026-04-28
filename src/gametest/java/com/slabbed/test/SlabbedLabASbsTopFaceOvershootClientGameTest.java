package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.shape.VoxelShape;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Isolated proof for the live SBS top-face full-block placement overshoot.
 */
public final class SlabbedLabASbsTopFaceOvershootClientGameTest implements FabricClientGameTest {

    private static final BlockPos FIXTURE_ORIGIN = new BlockPos(0, 200, 0);

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            Path screenshotDir = SlabbedLabClientGameTest.resolveClientGameTestScreenshotDir();
            Set<String> knownScreenshotFiles = SlabbedLabClientGameTest.listScreenshotFileNames(screenshotDir);
            List<SlabbedLabClientGameTest.ManifestArtifact> artifacts = new ArrayList<>();
            singleplayer.getClientWorld().waitForChunksRender();
            runProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles, artifacts);
        }
    }

    static void runProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles,
            List<SlabbedLabClientGameTest.ManifestArtifact> artifacts
    ) {
        final String testId = "sbs_top_face_overshoot_isolated";
        final BlockPos s1Pos = FIXTURE_ORIGIN.add(120, 0, 0);
        final BlockPos b1Pos = s1Pos.up();
        final BlockPos s2Pos = b1Pos.east();
        final BlockPos expectedB2Pos = s2Pos.up();
        final BlockPos overshootB2Pos = expectedB2Pos.up();
        final double eyeOffset = 1.62;

        AtomicReference<String> s1StateText = new AtomicReference<>("not_checked");
        AtomicReference<String> s1TypeText = new AtomicReference<>("not_checked");
        AtomicReference<String> b1ActionText = new AtomicReference<>("not_run");
        AtomicReference<String> b1StateText = new AtomicReference<>("not_checked");
        AtomicReference<String> b1DyText = new AtomicReference<>("not_checked");
        AtomicReference<String> b1OutlineText = new AtomicReference<>("not_checked");
        AtomicReference<String> s2HitText = new AtomicReference<>("not_checked");
        AtomicReference<String> s2ActionText = new AtomicReference<>("not_run");
        AtomicReference<String> s2StateText = new AtomicReference<>("not_checked");
        AtomicReference<String> s2TypeText = new AtomicReference<>("not_checked");
        AtomicReference<String> s2DyText = new AtomicReference<>("not_checked");
        AtomicReference<String> s2OutlineText = new AtomicReference<>("not_checked");
        AtomicReference<String> topFaceRaycastText = new AtomicReference<>("not_checked");
        AtomicReference<String> clickText = new AtomicReference<>("not_checked");
        AtomicReference<String> b2StateText = new AtomicReference<>("not_checked");
        AtomicReference<String> b2DyText = new AtomicReference<>("not_checked");
        AtomicReference<String> b2OutlineText = new AtomicReference<>("not_checked");
        AtomicReference<String> expectedB2Text = new AtomicReference<>(expectedB2Pos.toShortString());
        AtomicReference<String> actualB2Text = new AtomicReference<>("not_checked");
        AtomicReference<String> overshootB2StateText = new AtomicReference<>("not_checked");
        AtomicReference<String> overshootB2DyText = new AtomicReference<>("not_checked");
        AtomicReference<String> overshootB2OutlineText = new AtomicReference<>("not_checked");
        AtomicReference<String> gapText = new AtomicReference<>("not_checked");
        AtomicReference<String> verdict = new AtomicReference<>("BLOCKED");
        AtomicReference<BlockHitResult> topFaceHitRef = new AtomicReference<>(null);

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(s1Pos, net.minecraft.block.Blocks.STONE_SLAB.getDefaultState()
                    .with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
            world.setBlockState(b1Pos, net.minecraft.block.Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(s2Pos, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(expectedB2Pos, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(overshootB2Pos, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, b1Pos, world.getBlockState(b1Pos));
            server.getPlayerManager().getPlayerList().get(0).setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
        });
        b1ActionText.set("server_fixture: S1 bottom slab, B1 stone, anchor added");

        ctx.runOnClient(mc -> {
            mc.player.refreshPositionAndAngles(
                    s1Pos.getX() + 0.5,
                    s1Pos.getY() + 2.5,
                    b1Pos.getZ() + 3.25,
                    180.0f,
                    24.0f);
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockHitResult fallbackS2Hit = new BlockHitResult(
                new Vec3d(b1Pos.getX() + 1.0, b1Pos.getY() + 0.5, b1Pos.getZ() + 0.5),
                Direction.EAST,
                b1Pos,
                false,
                false);
        ctx.runOnClient(mc -> {
            Vec3d eye = new Vec3d(b1Pos.getX() + 2.5, b1Pos.getY() + 0.5, b1Pos.getZ() + 0.5);
            Vec3d end = eye.add(-4.5, 0.0, 0.0);
            mc.player.refreshPositionAndAngles(eye.x, eye.y - eyeOffset, eye.z, 90.0f, 0.0f);
            BlockHitResult rayHit = mc.world.raycast(new RaycastContext(
                    eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            BlockHitResult s2Hit = rayHit.getType() == HitResult.Type.BLOCK
                    && rayHit.getBlockPos().equals(b1Pos)
                    && rayHit.getSide() == Direction.EAST
                    ? rayHit
                    : fallbackS2Hit;
            s2HitText.set(formatHit(s2Hit, rayHit == s2Hit ? "raycast" : "fallback"));
            s2ActionText.set(mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, s2Hit).toString());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 8)));
        singleplayer.getServer().runOnServer(server -> server.getPlayerManager().getPlayerList().get(0)
                .setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE, 8)));

        final double topClickFeetX = s2Pos.getX() + 0.5;
        final double topClickFeetY = s2Pos.getY() + 1.25;
        final double topClickFeetZ = s2Pos.getZ() + 1.75;
        ctx.runOnClient(mc -> {
            mc.player.refreshPositionAndAngles(topClickFeetX, topClickFeetY, topClickFeetZ, 180.0f, 45.0f);
            mc.player.setVelocity(Vec3d.ZERO);
        });
        singleplayer.getServer().runOnServer(server -> {
            var p = server.getPlayerManager().getPlayerList().get(0);
            p.refreshPositionAndAngles(topClickFeetX, topClickFeetY, topClickFeetZ, 180.0f, 45.0f);
            p.setVelocity(Vec3d.ZERO);
        });
        ctx.waitTick();

        ctx.runOnClient(mc -> {
            BlockHitResult topFaceHit = new BlockHitResult(
                    new Vec3d(s2Pos.getX() + 0.5, s2Pos.getY(), s2Pos.getZ() + 0.5),
                    Direction.UP,
                    s2Pos,
                    false,
                    false);
            topFaceRaycastText.set(formatHit(topFaceHit, "direct-top-face"));
            topFaceHitRef.set(topFaceHit);
            clickText.set("hitPos=" + topFaceHit.getBlockPos().toShortString()
                    + " face=" + topFaceHit.getSide().asString()
                    + " hitVec=" + String.format("%.4f,%.4f,%.4f", topFaceHit.getPos().x, topFaceHit.getPos().y, topFaceHit.getPos().z));
            clickText.set(clickText.get() + " result=" + mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, topFaceHit));
        });
        ctx.waitTick();
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            BlockState s1State = mc.world.getBlockState(s1Pos);
            BlockState b1State = mc.world.getBlockState(b1Pos);
            BlockState s2State = mc.world.getBlockState(s2Pos);
            BlockState b2State = mc.world.getBlockState(expectedB2Pos);
            BlockState overshootB2State = mc.world.getBlockState(overshootB2Pos);

            s1StateText.set(s1State.toString());
            s1TypeText.set(s1State.get(SlabBlock.TYPE).toString());
            b1StateText.set(b1State.toString());
            s2StateText.set(s2State.toString());
            s2TypeText.set(s2State.contains(SlabBlock.TYPE) ? s2State.get(SlabBlock.TYPE).toString() : "none");
            b2StateText.set(b2State.toString());
            overshootB2StateText.set(overshootB2State.toString());
            double b1Dy = SlabSupport.getYOffset(mc.world, b1Pos, b1State);
            double s2Dy = SlabSupport.getYOffset(mc.world, s2Pos, s2State);
            double b2Dy = SlabSupport.getYOffset(mc.world, expectedB2Pos, b2State);
            double overshootB2Dy = SlabSupport.getYOffset(mc.world, overshootB2Pos, overshootB2State);
            b1DyText.set(Double.toString(b1Dy));
            s2DyText.set(Double.toString(s2Dy));
            b2DyText.set(Double.toString(b2Dy));
            overshootB2DyText.set(Double.toString(overshootB2Dy));

            VoxelShape b1Outline = b1State.getOutlineShape(mc.world, b1Pos, ShapeContext.absent());
            VoxelShape s2Outline = s2State.getOutlineShape(mc.world, s2Pos, ShapeContext.absent());
            VoxelShape b2Outline = b2State.getOutlineShape(mc.world, expectedB2Pos, ShapeContext.absent());
            VoxelShape overshootB2Outline = overshootB2State.getOutlineShape(mc.world, overshootB2Pos, ShapeContext.absent());
            double b1MinY = visualMinY(b1Pos, b1Outline);
            double b1MaxY = visualMaxY(b1Pos, b1Outline);
            double s2MinY = visualMinY(s2Pos, s2Outline);
            double s2MaxY = visualMaxY(s2Pos, s2Outline);
            double b2MinY = visualMinY(expectedB2Pos, b2Outline);
            double b2MaxY = visualMaxY(expectedB2Pos, b2Outline);
            double overshootB2MinY = visualMinY(overshootB2Pos, overshootB2Outline);
            double overshootB2MaxY = visualMaxY(overshootB2Pos, overshootB2Outline);
            b1OutlineText.set(formatYRange(b1MinY, b1MaxY));
            s2OutlineText.set(formatYRange(s2MinY, s2MaxY));
            b2OutlineText.set(formatYRange(b2MinY, b2MaxY));
            overshootB2OutlineText.set(formatYRange(overshootB2MinY, overshootB2MaxY));
            actualB2Text.set(b2State.isAir()
                    ? (overshootB2State.isAir() ? "air" : overshootB2Pos.toShortString())
                    : expectedB2Pos.toShortString());
            double actualB2Bottom = b2State.isAir() && !overshootB2State.isAir() ? overshootB2MinY : b2MinY;
            double gap = actualB2Bottom - s2MaxY;
            gapText.set(Double.toString(gap));
            boolean setupOk = b1State.isOf(Blocks.STONE)
                    && s2State.isOf(Blocks.STONE_SLAB)
                    && s2State.contains(SlabBlock.TYPE)
                    && s2State.get(SlabBlock.TYPE) == SlabType.BOTTOM
                    && Math.abs(s2Dy - (-0.5d)) < 1.0e-6;
            boolean expectedPlaced = b2State.isOf(Blocks.STONE);
            boolean overshootPlaced = overshootB2State.isOf(Blocks.STONE);
            if (topFaceHitRef.get() == null) {
                // Preserve the earlier BLOCKED raycast verdict.
            } else if (!setupOk) {
                verdict.set("BLOCKED: SBS setup did not match live 0.5S geometry");
            } else if (overshootPlaced || ((expectedPlaced || overshootPlaced) && gap > 1.0e-6)) {
                verdict.set("RED: B2 overshoot gap=" + gap);
            } else if (expectedPlaced && Math.abs(gap) <= 1.0e-6) {
                verdict.set("GREEN: B2 placed directly on S2 with gap=" + gap);
            } else {
                verdict.set("BLOCKED: top-face click did not place B2 in expected or overshoot cell");
            }
        });

        ctx.takeScreenshot(testId + "_result");
        singleplayer.getClientWorld().waitForChunksRender();

        writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "sbs top-face overshoot isolated",
                "Live SBS setup should place a full block directly on S2 when clicking S2's top face.",
                testId,
                testId,
                List.of(
                        new NoteField("s1Pos", s1Pos.toShortString()),
                        new NoteField("s1State", s1StateText.get()),
                        new NoteField("s1Type", s1TypeText.get()),
                        new NoteField("b1Pos", b1Pos.toShortString()),
                        new NoteField("b1ActionResult", b1ActionText.get()),
                        new NoteField("b1State", b1StateText.get()),
                        new NoteField("b1Dy", b1DyText.get()),
                        new NoteField("b1OutlineY", b1OutlineText.get()),
                        new NoteField("s2Hit", s2HitText.get()),
                        new NoteField("s2ActionResult", s2ActionText.get()),
                        new NoteField("s2Pos", s2Pos.toShortString()),
                        new NoteField("s2State", s2StateText.get()),
                        new NoteField("s2Type", s2TypeText.get()),
                        new NoteField("s2Dy", s2DyText.get()),
                        new NoteField("s2OutlineY", s2OutlineText.get()),
                        new NoteField("topFaceRaycast", topFaceRaycastText.get()),
                        new NoteField("click", clickText.get()),
                        new NoteField("expectedB2Pos", expectedB2Text.get()),
                        new NoteField("actualB2Pos", actualB2Text.get()),
                        new NoteField("b2State", b2StateText.get()),
                        new NoteField("b2Dy", b2DyText.get()),
                        new NoteField("b2OutlineY", b2OutlineText.get()),
                        new NoteField("overshootB2Pos", overshootB2Pos.toShortString()),
                        new NoteField("overshootB2State", overshootB2StateText.get()),
                        new NoteField("overshootB2Dy", overshootB2DyText.get()),
                        new NoteField("overshootB2OutlineY", overshootB2OutlineText.get()),
                        new NoteField("gap", gapText.get()),
                        new NoteField("verdict", verdict.get())
                ),
                verdict.get().startsWith("GREEN"));

        if (verdict.get().startsWith("RED") || verdict.get().startsWith("BLOCKED")) {
            throw new RuntimeException("[" + testId + "] " + verdict.get());
        }
    }

    private static String formatYRange(double minY, double maxY) {
        return String.format("%.1f..%.1f", minY, maxY);
    }

    private static double visualMinY(BlockPos pos, VoxelShape shape) {
        return pos.getY() + (shape.isEmpty() ? 0.0 : shape.getBoundingBox().minY);
    }

    private static double visualMaxY(BlockPos pos, VoxelShape shape) {
        return pos.getY() + (shape.isEmpty() ? 0.0 : shape.getBoundingBox().maxY);
    }

    private static String formatHit(BlockHitResult hit, String source) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return source + ":MISS";
        }
        return source
                + ":blockPos=" + hit.getBlockPos().toShortString()
                + " face=" + hit.getSide().asString()
                + " hitVec=" + String.format("%.4f,%.4f,%.4f", hit.getPos().x, hit.getPos().y, hit.getPos().z);
    }

    private static void writeInvariantProofNotes(
            Path screenshotDir,
            String noteFileName,
            String testId,
            String proofClass,
            String expectedInvariant,
            String setupProofId,
            String resultProofId,
            List<NoteField> observedFields,
            boolean pass
    ) {
        try {
            Files.createDirectories(screenshotDir);
            Path notesPath = screenshotDir.resolve(noteFileName);
            String setupFile = SlabbedLabClientGameTest.resolveScreenshotFileNameForProofId(screenshotDir, setupProofId);
            String resultFile = SlabbedLabClientGameTest.resolveScreenshotFileNameForProofId(screenshotDir, resultProofId);
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"testId\": \"").append(escapeJson(testId)).append("\",\n");
            sb.append("  \"proofClass\": \"").append(escapeJson(proofClass)).append("\",\n");
            sb.append("  \"expectedInvariant\": \"").append(escapeJson(expectedInvariant)).append("\",\n");
            sb.append("  \"screenshots\": {\n");
            sb.append("    \"setup\": \"").append(escapeJson(nullToEmpty(setupFile))).append("\",\n");
            sb.append("    \"result\": \"").append(escapeJson(nullToEmpty(resultFile))).append("\"\n");
            sb.append("  },\n");
            sb.append("  \"observed\": {\n");
            for (int i = 0; i < observedFields.size(); i++) {
                NoteField field = observedFields.get(i);
                sb.append("    \"")
                        .append(escapeJson(field.key()))
                        .append("\": \"")
                        .append(escapeJson(nullToEmpty(field.value())))
                        .append("\"");
                if (i + 1 < observedFields.size()) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("  },\n");
            sb.append("  \"pass\": ").append(pass).append("\n");
            sb.append("}\n");
            Files.writeString(notesPath, sb.toString());
        } catch (IOException ignored) {
        }
    }

    private record NoteField(String key, String value) {}

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
