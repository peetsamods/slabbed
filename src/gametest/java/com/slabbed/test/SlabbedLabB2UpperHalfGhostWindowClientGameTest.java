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
 * B2's visual outline top (202.5), B2 misses but B3's outline (202.5–203.5)
 * hits via the {@code .up()} probe.  The scan returns B3 immediately.
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
 *   <li>Sub-test B: aims at Y=202.55 — the "B2-1" trigger zone just above
 *       B2's outline top — asserts crosshair resolves to B3 (the bug), then
 *       clicks and asserts slab lands at B3-0.5 instead of B2's east.</li>
 *   <li>Sub-test C: second click from same aim, asserts ghost-window gap.</li>
 * </ul>
 */
public final class SlabbedLabB2UpperHalfGhostWindowClientGameTest implements FabricClientGameTest {
    private static final BlockPos FIXTURE_ORIGIN = new BlockPos(0, 200, 0);
    private static final double EPSILON = 1.0e-6;

    // B2 visual outline top = b2Pos.getY() + 0.5 = 202.5.
    // Aim just above it to enter the anchored-FB scan's samplePos.up() B3-hit zone.
    private static final double SB_AIM_Y     = 201.75;   // SB1B2-0.5 visible east face mid
    private static final double B2_UPPER_Y   = 202.45;   // inside B2's visual outline (control)
    private static final double BUG_TRIGGER_Y = 202.55;  // above B2 outline top → B3 via .up()

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
        final BlockPos sPos  = FIXTURE_ORIGIN;
        final BlockPos b1Pos = sPos.up();
        final BlockPos b2Pos = b1Pos.up();
        final BlockPos b3Pos = b2Pos.up();
        // SB1B2-0.5: BOTTOM slab east of B2; inherits dy=-0.5 from adjacent anchored B2.
        // Visual east face at X=2.0, visual Y span [201.5, 202.0].
        final BlockPos sbPos = b2Pos.east();

        // Eye is to the east of the stack (player side).
        final double eyeX = sbPos.getX() + 1.75;  // 2.75
        final Vec3d eyeSb    = new Vec3d(eyeX, sbPos.getY() + 1.5, sbPos.getZ() + 0.5);
        final Vec3d eyeB2up  = new Vec3d(eyeX, B2_UPPER_Y,  sbPos.getZ() + 0.5);
        final Vec3d eyeBug   = new Vec3d(eyeX, BUG_TRIGGER_Y, sbPos.getZ() + 0.5);

        // Aim at B2's physical east face (X=1.0) except for sbAim which aims through SB1B2-0.5's visible body.
        final Vec3d aimSb   = new Vec3d(sbPos.getX() + 0.5, SB_AIM_Y,      sbPos.getZ() + 0.5);
        final Vec3d aimB2up = new Vec3d(b2Pos.getX() + 1.0, B2_UPPER_Y,    sbPos.getZ() + 0.5);
        final Vec3d aimBug  = new Vec3d(b2Pos.getX() + 1.0, BUG_TRIGGER_Y, sbPos.getZ() + 0.5);

        AtomicReference<String> targetAtSb   = new AtomicReference<>("not_recorded");
        AtomicReference<String> targetAtB2up = new AtomicReference<>("not_recorded");
        AtomicReference<String> targetAtBug  = new AtomicReference<>("not_recorded");
        AtomicReference<String> click1Result = new AtomicReference<>("not_run");
        AtomicReference<String> click2Result = new AtomicReference<>("not_run");
        AtomicReference<Frame>  before       = new AtomicReference<>(Frame.empty("before"));
        AtomicReference<Frame>  afterClick1  = new AtomicReference<>(Frame.empty("afterClick1"));
        AtomicReference<Frame>  afterClick2  = new AtomicReference<>(Frame.empty("afterClick2"));
        AtomicReference<String> verdict      = new AtomicReference<>("BLOCKED");

        // ── server-side fixture ───────────────────────────────────────────────
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(sPos,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(b1Pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(b2Pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(b3Pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            // Clear all other horizontal neighbours so the only slab side is sbPos.
            clearSideSurface(world, b2Pos, b3Pos, sbPos);
            // SB1B2-0.5: place after clearing so b3.east().down() cleanup cannot erase it.
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
        for (int i = 0; i < 4; i++) ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        before.set(capture(ctx, "before", sPos, b1Pos, b2Pos, b3Pos, sbPos));
        ctx.takeScreenshot(testId + "_before");
        singleplayer.getClientWorld().waitForChunksRender();

        // ── sub-test A: SB1B2-0.5 selectability ──────────────────────────────
        // Aim at SB1B2-0.5's visible east face. Should resolve to sbPos, not MISS.
        targetAtSb.set(aimAndDescribeCrosshair(ctx, eyeSb, aimSb));

        // ── sub-test B control: B2 upper body (inside outline) ───────────────
        // At Y=202.45 B2's outline hits (maxY=202.5). Crosshair must be B2.
        targetAtB2up.set(aimAndDescribeCrosshair(ctx, eyeB2up, aimB2up));

        // ── sub-test B bug: Y just above B2's outline top ────────────────────
        // At Y=202.55 B2's outline misses; the anchored-FB scan finds B3 via
        // samplePos.up() and returns it.  This is the "B2-1" trigger zone.
        targetAtBug.set(aimAndDescribeCrosshair(ctx, eyeBug, aimBug));
        ctx.takeScreenshot(testId + "_before_click");
        singleplayer.getClientWorld().waitForChunksRender();

        // ── click 1 from bug-trigger aim ─────────────────────────────────────
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                click1Result.set("BLOCKED");
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
        afterClick1.set(capture(ctx, "afterClick1", sPos, b1Pos, b2Pos, b3Pos, sbPos));
        ctx.takeScreenshot(testId + "_after_click1");

        // ── click 2 from same aim ─────────────────────────────────────────────
        aimAndDescribeCrosshair(ctx, eyeBug, aimBug);
        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null || mc.interactionManager == null) {
                click2Result.set("BLOCKED");
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
        for (int i = 0; i < 3; i++) ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        afterClick2.set(capture(ctx, "afterClick2", sPos, b1Pos, b2Pos, b3Pos, sbPos));
        ctx.takeScreenshot(testId + "_after_click2");

        // ── verdict ────────────────────────────────────────────────────────────
        // RED conditions (any one is sufficient):
        // A) SB1B2-0.5 not selectable: crosshair did not resolve to sbPos
        // B) Bug-trigger crosshair is B3 (anchored-FB scan samplePos.up() error)
        // C) Placement after bug-trigger click went to B3.east=(1,203,0) not B2.east=(1,202,0)
        // D) Repeat click created ghost-window gap
        boolean sbSelectable   = targetAtSb.get().contains("blockPos=" + sbPos.toShortString());
        boolean b2upCorrect    = targetAtB2up.get().contains("blockPos=" + b2Pos.toShortString());
        boolean bugCrosshairB3 = targetAtBug.get().contains("blockPos=" + b3Pos.toShortString());
        boolean placedAtB3side = afterClick1.get().hasSlabAt(b3Pos);
        boolean placedAtB2side = afterClick1.get().hasSlabAt(sbPos);
        boolean ghostWindowGap = afterClick2.get().hasGhostWindowGap(b2Pos, b3Pos);

        if (!b2upCorrect) {
            verdict.set("BLOCKED: control aim at B2 upper body (Y=202.45) did not resolve to B2 — "
                    + "fixture or retarget mixin not functioning; target=" + targetAtB2up.get());
        } else if (!sbSelectable || bugCrosshairB3 || placedAtB3side || ghostWindowGap) {
            StringBuilder reasons = new StringBuilder("RED:");
            if (!sbSelectable)   reasons.append(" SB1B2-0.5-not-selectable(target=").append(targetAtSb.get()).append(")");
            if (bugCrosshairB3)  reasons.append(" bug-trigger-crosshair-is-B3(").append(targetAtBug.get()).append(")");
            if (placedAtB3side)  reasons.append(" click1-placed-at-B3-side(B3-0.5)");
            if (!placedAtB2side && !placedAtB3side) reasons.append(" click1-placed-nowhere-expected");
            if (ghostWindowGap)  reasons.append(" click2-ghost-window-gap");
            verdict.set(reasons.toString());
        } else {
            verdict.set("GREEN: SB1B2-0.5 selectable, bug-trigger aim resolves to B2 not B3, "
                    + "placement on B2 east, no ghost-window gap");
        }

        // ── artifact ───────────────────────────────────────────────────────────
        List<SlabbedLabClientGameTest.NoteField> fields = new ArrayList<>();
        fields.add(new SlabbedLabClientGameTest.NoteField("proofId", testId));
        fields.add(new SlabbedLabClientGameTest.NoteField("falsGreenMechanism",
                "prior proof cleared SB1B2-0.5 and aimed at Y=202.45 (inside B2 outline); "
                        + "upperVisibleHitBelongsToAboveLoweredFullBlock fix never triggered (needs hitY>=203)"));
        fields.add(new SlabbedLabClientGameTest.NoteField("rootCause",
                "anchored-FB scan samplePos.up() returns B3 when ray Y>202.5 (above B2 outline top); "
                        + "BlockItemPlacementIntentMixin sees B3 with hitY<203 => BOTTOM => B3.east=B3-0.5"));
        fields.add(new SlabbedLabClientGameTest.NoteField("sPos",  sPos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("b1Pos", b1Pos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("b2Pos", b2Pos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("b3Pos", b3Pos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("sbPos", sbPos.toShortString()));
        fields.add(new SlabbedLabClientGameTest.NoteField("beforeStateTable", before.get().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("subTestA_sbAimY",   String.valueOf(SB_AIM_Y)));
        fields.add(new SlabbedLabClientGameTest.NoteField("subTestA_sbTarget", targetAtSb.get()));
        fields.add(new SlabbedLabClientGameTest.NoteField("subTestA_sbSelectable", Boolean.toString(sbSelectable)));
        fields.add(new SlabbedLabClientGameTest.NoteField("subTestB_controlAimY",   String.valueOf(B2_UPPER_Y)));
        fields.add(new SlabbedLabClientGameTest.NoteField("subTestB_controlTarget", targetAtB2up.get()));
        fields.add(new SlabbedLabClientGameTest.NoteField("subTestB_controlCorrect", Boolean.toString(b2upCorrect)));
        fields.add(new SlabbedLabClientGameTest.NoteField("subTestB_bugAimY",    String.valueOf(BUG_TRIGGER_Y)));
        fields.add(new SlabbedLabClientGameTest.NoteField("subTestB_bugTarget",  targetAtBug.get()));
        fields.add(new SlabbedLabClientGameTest.NoteField("subTestB_bugCrosshairIsB3", Boolean.toString(bugCrosshairB3)));
        fields.add(new SlabbedLabClientGameTest.NoteField("click1Result",  click1Result.get()));
        fields.add(new SlabbedLabClientGameTest.NoteField("click1PlacedAtB2side", Boolean.toString(placedAtB2side)));
        fields.add(new SlabbedLabClientGameTest.NoteField("click1PlacedAtB3side", Boolean.toString(placedAtB3side)));
        fields.add(new SlabbedLabClientGameTest.NoteField("afterClick1StateTable", afterClick1.get().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("click2Result",  click2Result.get()));
        fields.add(new SlabbedLabClientGameTest.NoteField("ghostWindowGapIndicator", Boolean.toString(ghostWindowGap)));
        fields.add(new SlabbedLabClientGameTest.NoteField("afterClick2StateTable", afterClick2.get().describe()));
        fields.add(new SlabbedLabClientGameTest.NoteField("verdict", verdict.get()));

        SlabbedLabClientGameTest.writeInvariantProofNotes(
                screenshotDir,
                testId + "_notes.json",
                testId,
                "live-accurate B2-upper-half ghost-window proof",
                "With SB1B2-0.5 present: aiming just above B2 outline top should not route to B3.",
                testId + "_before",
                testId + "_after_click2",
                fields,
                verdict.get().startsWith("GREEN"));

        if (verdict.get().startsWith("RED") || verdict.get().startsWith("BLOCKED")) {
            throw new RuntimeException("[" + testId + "] " + verdict.get()
                    + " sbTarget=" + targetAtSb.get()
                    + " b2upTarget=" + targetAtB2up.get()
                    + " bugTarget=" + targetAtBug.get()
                    + " click1=" + click1Result.get());
        }
    }

    // ── fixture helpers ───────────────────────────────────────────────────────

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
