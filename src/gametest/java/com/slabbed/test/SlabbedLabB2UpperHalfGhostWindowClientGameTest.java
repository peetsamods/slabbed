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
 * Live-accurate proof for the B2-upper-half placement bug.
 *
 * <p>Root cause (confirmed by audit of 0d62de3):
 * {@code slabbed$retargetAnchoredLoweredFullBlock} samples along the ray and
 * checks {@code samplePos.up()} at each step.  When the ray Y is just above
 * B2's visual outline top (202.5), B2 misses and B3's outline (202.5–203.5)
 * is the visible owner.
 * {@code BlockItemPlacementIntentMixin} then sees targetPos=B3 with
 * hitY=202.55 < B3.Y=203 → BOTTOM intent → places at B3.east = "B3-0.5".
 *
 * <p>The fix in 0d62de3 ({@code upperVisibleHitBelongsToAboveLoweredFullBlock})
 * is dead code for this geometry: it fires only when hitY ≥ abovePos.Y=203,
 * which can never happen on a hit coming off B2's outline (maxY=202.5).
 *
 * <p>The prior proof was false-green because:
 * <ol>
 *   <li>{@code clearSlabProbeVolume} removed SB1B2-0.5, so no side-slab
 *       competition existed.</li>
 *   <li>The aim was at Y=202.45 (inside B2's outline), so the bug zone
 *       (Y≥202.5) was never entered.</li>
 *   <li>The new fix code was never triggered (hitY=202.45 < 203).</li>
 * </ol>
 *
 * <p>This replacement proof:
 * <ul>
 *   <li>Places SB1B2-0.5 at b2Pos.east()=(1,202,0) as BOTTOM slab.</li>
 *   <li>Sub-test A: aims at SB1B2-0.5's visible body (Y=201.75) and
 *       asserts the crosshair lands on it.</li>
 *   <li>Sub-test B: aims at Y=202.55 — the B3-visible seam band just above
 *       B2's outline top — asserts crosshair resolves to B3, then clicks and
 *       asserts slab placement does not create B3-0.5.</li>
 *   <li>Sub-test C: second click from same aim asserts no ghost-window gap.</li>
 * </ul>
 */
public final class SlabbedLabB2UpperHalfGhostWindowClientGameTest implements FabricClientGameTest {
    private static final BlockPos FIXTURE_ORIGIN = new BlockPos(0, 200, 0);
    private static final double EPSILON = 1.0e-6;

    // B2 visual outline top = b2Pos.getY() + 0.5 = 202.5.
    // Aim just above it to enter B3's visible lower band.
    private static final double SB_AIM_Y     = 201.75;   // SB1B2-0.5 visible east face mid
    private static final double B2_UPPER_Y   = 202.45;   // inside B2's visual outline (control)
    private static final double BUG_TRIGGER_Y = 202.55;  // above B2 outline top → B3 via .up()

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (TerrainSlabsProofFocus.skipUnrelatedClientGameTest(getClass().getSimpleName())) {
            return;
        }

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
        final BlockPos sPos  = FIXTURE_ORIGIN;
        final BlockPos b1Pos = sPos.up();
        final BlockPos b2Pos = b1Pos.up();
        final BlockPos b3Pos = b2Pos.up();
        final BlockPos sbPos = b2Pos.east();
        final double eyeX = sbPos.getX() + 1.75;  // 2.75
        List<AimPoint> aimPoints = List.of(
                aim("sb_lower_visible_horizontal", eyeX, SB_AIM_Y - 0.20, sbPos.getX() + 1.0,
                        SB_AIM_Y - 0.20, sbPos, null, true, "SB1B2-0.5 lower visible side body"),
                aim("sb_center_visible_horizontal", eyeX, SB_AIM_Y, sbPos.getX() + 1.0,
                        SB_AIM_Y, sbPos, null, true, "SB1B2-0.5 center visible side body"),
                aim("sb_upper_visible_horizontal", eyeX, SB_AIM_Y + 0.20, sbPos.getX() + 1.0,
                        SB_AIM_Y + 0.20, sbPos, null, true, "SB1B2-0.5 upper visible side body"),
                aim("b2_05_just_below_halfline", eyeX, b2Pos.getY() - 0.025, b2Pos.getX() + 1.0,
                        b2Pos.getY() - 0.025, sbPos, b2Pos, true, "B2-0.5 just below B2 half-line"),
                aim("b2_halfline_seam", eyeX, b2Pos.getY(), b2Pos.getX() + 1.0,
                        b2Pos.getY(), sbPos, b2Pos, true, "B2 0.5/1S placement seam"),
                aim("b2_1_just_above_halfline", eyeX, b2Pos.getY() + 0.025, b2Pos.getX() + 1.0,
                        b2Pos.getY() + 0.025, b2Pos, null, true, "B2-1 just above placement seam"),
                aim("b2_1_center", eyeX, b2Pos.getY() + 0.25, b2Pos.getX() + 1.0,
                        b2Pos.getY() + 0.25, b2Pos, null, true, "B2-1 center visible body"),
                aim("b2_1_just_below_top", eyeX, b2Pos.getY() + 0.475, b2Pos.getX() + 1.0,
                        b2Pos.getY() + 0.475, b2Pos, null, true, "B2-1 just below visual top"),
                aim("b2_top_seam", eyeX, b2Pos.getY() + 0.50, b2Pos.getX() + 1.0,
                        b2Pos.getY() + 0.50, b2Pos, b3Pos, true, "B2/B3 outline seam"),
                aim("b2_top_just_above_seam", eyeX, b2Pos.getY() + 0.525, b2Pos.getX() + 1.0,
                        b2Pos.getY() + 0.525, b3Pos, null, true, "B3 visible seam band just above B2 top"),
                aim("b2_top_trigger_202_55", eyeX, BUG_TRIGGER_Y, b2Pos.getX() + 1.0,
                        BUG_TRIGGER_Y, b3Pos, null, true, "B3 visible live trigger height"),
                aim("b3_05_center_observation", eyeX, b3Pos.getY() - 0.25, b3Pos.getX() + 1.0,
                        b3Pos.getY() - 0.25, b3Pos, null, false, "B3-0.5 observation band")
        );

        List<SweepRow> rows = new ArrayList<>();
        for (AimPoint aim : aimPoints) {
            resetFixture(singleplayer, testId, sPos, b1Pos, b2Pos, b3Pos, sbPos);
            for (int i = 0; i < 4; i++) ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();

            Frame before = capture(ctx, aim.label() + "_before", sPos, b1Pos, b2Pos, b3Pos, sbPos);
            HitSnapshot hit1 = aimAndCaptureHit(ctx, aim.eye(), aim.target());
            String click1 = clickCurrentTarget(ctx);
            ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            Frame afterClick1 = capture(ctx, aim.label() + "_afterClick1", sPos, b1Pos, b2Pos, b3Pos, sbPos);

            HitSnapshot hit2 = aimAndCaptureHit(ctx, aim.eye(), aim.target());
            String click2 = clickCurrentTarget(ctx);
            for (int i = 0; i < 3; i++) ctx.waitTick();
            singleplayer.getClientWorld().waitForChunksRender();
            Frame afterClick2 = capture(ctx, aim.label() + "_afterClick2", sPos, b1Pos, b2Pos, b3Pos, sbPos);

            rows.add(SweepRow.from(aim, hit1, click1, hit2, click2, before, afterClick1, afterClick2,
                    b2Pos, b3Pos, sbPos));
        }

        ctx.takeScreenshot(testId + "_sweep_result");

        List<String> redRows = rows.stream()
                .filter(row -> row.verdict().startsWith("RED"))
                .map(row -> row.label() + "=" + row.verdict())
                .toList();
        String verdict = redRows.isEmpty()
                ? "GREEN: B2/SB1B2 visible ownership sweep stayed on expected owners with no B3 placement or ghost window"
                : "RED: B2/SB1B2 visible ownership sweep failed " + redRows;

        List<SlabbedLabClientGameTest.NoteField> fields = new ArrayList<>();
        fields.add(new SlabbedLabClientGameTest.NoteField("proofId", testId));
        fields.add(new SlabbedLabClientGameTest.NoteField("falsGreenMechanism",
                "6d9c106 sampled one SB point and two B2 seam points; this sweep records horizontal live-like "
                        + "aim heights across SB1B2-0.5, B2-1, the B2/B3 seam, and B3-0.5."));
        fields.add(new SlabbedLabClientGameTest.NoteField("sPos",  sPos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("b1Pos", b1Pos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("b2Pos", b2Pos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("b3Pos", b3Pos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("sbPos", sbPos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("sweepCount", Integer.toString(rows.size())));
        for (int i = 0; i < rows.size(); i++) {
            SweepRow row = rows.get(i);
            String prefix = String.format("sweep_%02d", i);
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_label", row.label()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_aimLabel", row.aimLabel()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_eye", row.eye()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_target", row.target()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_heldItem", "minecraft:stone_slab"));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_expectedVisibleOwner", row.expectedVisibleOwner()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_actualCrosshairOwner", row.actualCrosshairOwner()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_hitFace", row.hitFace()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_hitVec", row.hitVec()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_click1Result", row.click1Result()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_click1Placement", row.click1Placement()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_click2Result", row.click2Result()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_ghostWindowGapIndicator", row.ghostWindowGap()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_b2Box", row.b2Box()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_sideSlabBox", row.sideSlabBox()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_b3Box", row.b3Box()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "_verdict", row.verdict()));
        }
        fields.add(new SlabbedLabClientGameTest.NoteField("verdict", verdict));

        SlabbedLabClientGameTest.writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "live-accurate B2 seam aim-height sweep proof",
                "Every visible SB1B2-0.5/B2/B3 height should resolve to the visible owner without B3-side placement or a ghost window.",
                testId + "_sweep_result",
                testId + "_sweep_result",
                fields,
                verdict.startsWith("GREEN"));

        if (verdict.startsWith("RED")) {
            throw new RuntimeException("[" + testId + "] " + verdict);
        }
    }

    // ── fixture helpers ───────────────────────────────────────────────────────

    private static AimPoint aim(String label, double eyeX, double eyeY, double targetX, double targetY,
                                BlockPos expectedOwner, BlockPos alternateOwner, boolean redOnMismatch,
                                String aimLabel) {
        Vec3d eye = new Vec3d(eyeX, eyeY, FIXTURE_ORIGIN.getZ() + 0.5);
        Vec3d target = new Vec3d(targetX, targetY, FIXTURE_ORIGIN.getZ() + 0.5);
        return new AimPoint(label, aimLabel, eye, target, expectedOwner, alternateOwner, redOnMismatch);
    }

    private static void resetFixture(TestSingleplayerContext singleplayer, String testId,
                                     BlockPos sPos, BlockPos b1Pos, BlockPos b2Pos, BlockPos b3Pos,
                                     BlockPos sbPos) {
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(sPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(b1Pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(b2Pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(b3Pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            clearSideSurface(world, b2Pos, b3Pos, sbPos);
            for (BlockPos pos : List.of(sbPos.east(), sbPos.east().up(), sbPos.east().down())) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
            world.setBlockState(sbPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, b1Pos, world.getBlockState(b1Pos));
            SlabAnchorAttachment.addAnchor(world, b2Pos, world.getBlockState(b2Pos));
            SlabAnchorAttachment.addAnchor(world, b3Pos, world.getBlockState(b3Pos));
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                throw new RuntimeException("singleplayer server player list empty for " + testId);
            }
            server.getPlayerManager().getPlayerList().get(0)
                    .setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
        });
    }

    /** Clear all horizontal neighbours of b2/b3, except the preserved side sbPos. */
    private static void clearSideSurface(net.minecraft.world.World world,
                                          BlockPos b2Pos, BlockPos b3Pos, BlockPos keepPos) {
        for (BlockPos base : List.of(b2Pos, b3Pos)) {
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos side = base.offset(dir);
                if (side.equals(keepPos)) continue;
                world.setBlockState(side,       Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(side.up(),  Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                world.setBlockState(side.down(),Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        world.setBlockState(b3Pos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    // ── crosshair helpers ─────────────────────────────────────────────────────

    private static String aimAndDescribeCrosshair(ClientGameTestContext ctx, Vec3d eye, Vec3d target) {
        AtomicReference<String> out = new AtomicReference<>("not_recorded");
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) { out.set("null_world_or_player"); return; }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            positionPlayer(mc, eye, target);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            out.set(describeHit(mc.crosshairTarget));
        });
        return out.get();
    }

    private static HitSnapshot aimAndCaptureHit(ClientGameTestContext ctx, Vec3d eye, Vec3d target) {
        AtomicReference<HitSnapshot> out = new AtomicReference<>(HitSnapshot.miss("not_recorded"));
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                out.set(HitSnapshot.miss("null_world_or_player"));
                return;
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            positionPlayer(mc, eye, target);
            mc.gameRenderer.updateCrosshairTarget(0.0f);
            out.set(HitSnapshot.from(mc.crosshairTarget));
        });
        return out.get();
    }

    private static String clickCurrentTarget(ClientGameTestContext ctx) {
        AtomicReference<String> out = new AtomicReference<>("not_run");
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                out.set("BLOCKED");
                return;
            }
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE_SLAB, 8));
            HitResult target = mc.crosshairTarget;
            if (!(target instanceof BlockHitResult hit) || target.getType() != HitResult.Type.BLOCK) {
                out.set("MISS_NO_CLICK target=" + describeHit(target));
                return;
            }
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            out.set(result.toString());
        });
        return out.get();
    }

    private static void positionPlayer(net.minecraft.client.MinecraftClient mc, Vec3d eye, Vec3d target) {
        if (mc.player == null) return;
        Vec3d delta  = target.subtract(eye);
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw    = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch  = (float) (-Math.toDegrees(Math.atan2(delta.y, horiz)));
        double feetY = eye.y - mc.player.getStandingEyeHeight();
        mc.player.refreshPositionAndAngles(eye.x, feetY, eye.z, yaw, pitch);
        mc.player.raycast(6.0, 0.0f, false);
    }

    private static String describeHit(HitResult hit) {
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) {
            return hit == null ? "null" : hit.getType().toString();
        }
        return "BLOCK blockPos=" + bh.getBlockPos().toShortString()
                + " face=" + bh.getSide().asString()
                + " hit=" + fmtVec(bh.getPos());
    }

    // ── capture ───────────────────────────────────────────────────────────────

    private static Frame capture(ClientGameTestContext ctx, String label,
                                  BlockPos sPos, BlockPos b1Pos, BlockPos b2Pos,
                                  BlockPos b3Pos, BlockPos sbPos) {
        AtomicReference<Frame> out = new AtomicReference<>(Frame.empty(label));
        ctx.runOnClient(mc -> {
            BoxSample s  = sample(mc.world, sPos);
            BoxSample b1 = sample(mc.world, b1Pos);
            BoxSample b2 = sample(mc.world, b2Pos);
            BoxSample b3 = sample(mc.world, b3Pos);
            BoxSample sb = sample(mc.world, sbPos);
            // Also scan b3's east for "B3-0.5" placement evidence.
            List<BoxSample> extra = new ArrayList<>();
            for (BlockPos base : List.of(b2Pos, b3Pos)) {
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    BlockPos side = base.offset(dir);
                    for (BlockPos p : List.of(side.down(), side, side.up())) {
                        BoxSample sp = sample(mc.world, p);
                        if (sp.isSlab() && extra.stream().noneMatch(e -> e.pos().equals(p))) {
                            extra.add(sp);
                        }
                    }
                }
            }
            out.set(new Frame(label, s, b1, b2, b3, sb, extra));
        });
        return out.get();
    }

    private static BoxSample sample(net.minecraft.world.BlockView world, BlockPos pos) {
        if (world == null) {
            return new BoxSample(pos, "null_world", 0.0, false, 0.0, 0.0, false, "none", false);
        }
        BlockState state  = world.getBlockState(pos);
        double dy         = SlabSupport.getYOffset(world, pos, state);
        boolean anchored  = SlabAnchorAttachment.isAnchored(world, pos);
        VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.absent());
        boolean empty     = outline.isEmpty();
        double minY = pos.getY() + (empty ? 0.0 : outline.getBoundingBox().minY);
        double maxY = pos.getY() + (empty ? 0.0 : outline.getBoundingBox().maxY);
        String slabType = state.contains(SlabBlock.TYPE) ? state.get(SlabBlock.TYPE).toString() : "none";
        return new BoxSample(pos, state.toString(), dy, anchored, minY, maxY,
                state.isOf(Blocks.STONE_SLAB), slabType, !state.isAir());
    }

    // ── records ───────────────────────────────────────────────────────────────

    private record AimPoint(String label, String aimLabel, Vec3d eye, Vec3d target,
                            BlockPos expectedOwner, BlockPos alternateOwner, boolean redOnMismatch) {
        String expectedOwnerLabel() {
            String primary = expectedOwner == null ? "none" : expectedOwner.toShortString();
            if (alternateOwner == null) {
                return primary;
            }
            return primary + " or " + alternateOwner.toShortString();
        }
    }

    private record HitSnapshot(String type, BlockPos blockPos, String face, String hitVec) {
        static HitSnapshot from(HitResult hit) {
            if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
                return miss(hit == null ? "null" : hit.getType().toString());
            }
            return new HitSnapshot("BLOCK", blockHit.getBlockPos(), blockHit.getSide().asString(),
                    fmtVec(blockHit.getPos()));
        }

        static HitSnapshot miss(String type) {
            return new HitSnapshot(type, null, "none", "none");
        }

        boolean matches(BlockPos pos) {
            return pos != null && pos.equals(blockPos);
        }

        String owner() {
            return blockPos == null ? type : blockPos.toShortString();
        }

        String describe() {
            return blockPos == null
                    ? type
                    : "BLOCK blockPos=" + blockPos.toShortString() + " face=" + face + " hit=" + hitVec;
        }
    }

    private record SweepRow(String label, String aimLabel, String eye, String target,
                            String expectedVisibleOwner, String actualCrosshairOwner,
                            String hitFace, String hitVec, String click1Result,
                            String click1Placement, String click2Result,
                            String ghostWindowGap, String b2Box, String sideSlabBox,
                            String b3Box, String verdict) {
        static SweepRow from(AimPoint aim, HitSnapshot hit1, String click1Result,
                             HitSnapshot hit2, String click2Result, Frame before,
                             Frame afterClick1, Frame afterClick2,
                             BlockPos b2Pos, BlockPos b3Pos, BlockPos sbPos) {
            boolean ownerOk = hit1.matches(aim.expectedOwner())
                    || (aim.alternateOwner() != null && hit1.matches(aim.alternateOwner()));
            boolean placedAtB3side = afterClick1.hasSlabAt(b3Pos);
            boolean ghostWindowGap = afterClick2.hasGhostWindowGap(b2Pos, b3Pos);
            List<String> reasons = new ArrayList<>();
            if (aim.redOnMismatch() && !ownerOk) {
                reasons.add("owner=" + hit1.owner() + " expected=" + aim.expectedOwnerLabel());
            }
            if (aim.redOnMismatch() && placedAtB3side) {
                reasons.add("click1-placed-B3-0.5");
            }
            if (aim.redOnMismatch() && ghostWindowGap) {
                reasons.add("ghost-window-gap");
            }
            String verdict = reasons.isEmpty()
                    ? (aim.redOnMismatch() ? "GREEN" : "OBSERVED")
                    : "RED: " + String.join("; ", reasons);
            String placement = placedAtB3side
                    ? "B3-0.5"
                    : (afterClick1.sb().isSlab()
                    ? "B2-side " + afterClick1.sb().slabType()
                    : "none");
            return new SweepRow(
                    aim.label(),
                    aim.aimLabel(),
                    fmtVec(aim.eye()),
                    fmtVec(aim.target()),
                    aim.expectedOwnerLabel(),
                    hit1.describe(),
                    hit1.face(),
                    hit1.hitVec(),
                    click1Result,
                    placement,
                    click2Result + " secondTarget=" + hit2.describe(),
                    Boolean.toString(ghostWindowGap),
                    before.b2().describe(),
                    before.sb().describe(),
                    before.b3().describe(),
                    verdict);
        }
    }

    private record Frame(String label,
                         BoxSample s, BoxSample b1, BoxSample b2, BoxSample b3,
                         BoxSample sb, List<BoxSample> extra) {

        static Frame empty(String label) {
            BoxSample e = new BoxSample(BlockPos.ORIGIN, "none", 0.0, false, 0.0, 0.0, false, "none", false);
            return new Frame(label, e, e, e, e, e, List.of());
        }

        /** Returns true if any slab in the extended scan is adjacent to (or at) the given pos. */
        boolean hasSlabAt(BlockPos pos) {
            return extra.stream().anyMatch(slab ->
                    slab.pos().equals(pos) || isHorizontalNeighbour(slab.pos(), pos));
        }

        private static boolean isHorizontalNeighbour(BlockPos a, BlockPos b) {
            int dx = Math.abs(a.getX() - b.getX());
            int dy = Math.abs(a.getY() - b.getY());
            int dz = Math.abs(a.getZ() - b.getZ());
            return (dx + dz == 1) && dy == 0;
        }

        /**
         * Ghost-window gap: a slab at b3Pos's Y whose visual minY differs from b3's minY,
         * sitting between b2's visual top and b3's visual bottom — indicating a placement
         * that created a floating disconnected gap.
         */
        boolean hasGhostWindowGap(BlockPos b2Pos, BlockPos b3Pos) {
            double b2MaxY = b2.maxY();
            double b3MinY = b3.minY();
            for (BoxSample slab : extra) {
                if (slab.pos().getY() == b3Pos.getY()
                        && slab.minY() > b2MaxY - EPSILON
                        && slab.minY() < b3MinY + EPSILON
                        && Math.abs(slab.minY() - b3MinY) > EPSILON) {
                    return true;
                }
            }
            return false;
        }

        String describe() {
            return label
                    + " s={" + s.describe() + "}"
                    + " b1={" + b1.describe() + "}"
                    + " b2={" + b2.describe() + "}"
                    + " b3={" + b3.describe() + "}"
                    + " sb={" + sb.describe() + "}"
                    + " extra=" + extra.stream().map(BoxSample::describe).toList();
        }
    }

    private record BoxSample(BlockPos pos, String state, double dy, boolean anchored,
                             double minY, double maxY,
                             boolean isSlab, String slabType, boolean nonAir) {
        String describe() {
            return "pos=" + pos.toShortString()
                    + " dy=" + dy
                    + " anchored=" + anchored
                    + " visualY=" + String.format("%.3f..%.3f", minY, maxY)
                    + " slabType=" + slabType
                    + " state=" + state;
        }
    }

    private static String fmtVec(Vec3d v) {
        return String.format("%.4f,%.4f,%.4f", v.x, v.y, v.z);
    }
}
