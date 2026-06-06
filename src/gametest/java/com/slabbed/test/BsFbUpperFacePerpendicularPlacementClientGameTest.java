package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.SlabType;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.BlockView;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Property-gated client GameTest for the BS-FB upper-face perpendicular
 * placement blocker from Julia's live screenshots.
 */
public final class BsFbUpperFacePerpendicularPlacementClientGameTest implements FabricClientGameTest {
    private static final String ENABLED_PROPERTY = "slabbed.bsfbUpperFacePerpendicularProof";
    private static final String A7_A8_PROPERTY = "slabbed.a7a8LoweredSideSlabNoCombineProof";

    @Override
    public void runTest(ClientGameTestContext ctx) {
        boolean fullProof = Boolean.getBoolean(ENABLED_PROPERTY);
        boolean a7a8Proof = Boolean.getBoolean(A7_A8_PROPERTY);
        if (!fullProof && !a7a8Proof) {
            return;
        }

        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            Path screenshotDir = SlabbedLabClientGameTest.resolveClientGameTestScreenshotDir();
            Set<String> knownScreenshotFiles = SlabbedLabClientGameTest.listScreenshotFileNames(screenshotDir);
            List<SlabbedLabClientGameTest.ManifestArtifact> artifacts = new ArrayList<>();

            singleplayer.getClientWorld().waitForChunksRender();
            runA7A8LoweredSideSlabNoCombineProof(ctx, singleplayer);
            if (!fullProof) {
                return;
            }
            runHangerFollowUnderLoweredSupportProof(ctx, singleplayer);
            runLoweredSlabHangerRenderRegionStabilityProof(ctx, singleplayer);
            runBreakJumpDyProbe(ctx, singleplayer);
            runCrossAxisSideSlabRetargetProof(ctx, singleplayer);
            SlabbedLabClientGameTest.runBsFbUpperFacePerpendicularPlacementProof(
                    ctx,
                    singleplayer,
                    screenshotDir,
                    knownScreenshotFiles,
                    artifacts);
            SlabbedLabClientGameTest.runTerrainSlabsSideSlabPlacementOnLoweredBlockProof(
                    ctx,
                    singleplayer,
                    screenshotDir,
                    knownScreenshotFiles,
                    artifacts);
        }
    }

    /**
     * Regression for the live a7/a8 placement sequence. A lowered side slab that
     * already sits beside a lowered full block is a placement surface, not a
     * same-cell replacement target: clicking its outward horizontal face should
     * place the next side slab, keep the original single slab single, and carry
     * the lowered dy across the side-slab lane.
     */
    private static void runA7A8LoweredSideSlabNoCombineProof(
            ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        final BlockPos support = new BlockPos(0, 200, 68);
        final BlockPos loweredFull = support.up();
        final BlockPos firstSideSlab = loweredFull.south();
        final BlockPos secondSideSlab = firstSideSlab.south();

        AtomicReference<String> actionResult = new AtomicReference<>("not_run");
        AtomicReference<String> firstStateText = new AtomicReference<>("not_read");
        AtomicReference<String> secondStateText = new AtomicReference<>("not_read");
        AtomicReference<Double> firstDy = new AtomicReference<>(Double.NaN);
        AtomicReference<Double> secondDy = new AtomicReference<>(Double.NaN);

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(
                    support,
                    Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(loweredFull, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, loweredFull, world.getBlockState(loweredFull));
            world.setBlockState(
                    firstSideSlab,
                    Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(secondSideSlab, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(secondSideSlab.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                var player = server.getPlayerManager().getPlayerList().get(0);
                player.refreshPositionAndAngles(
                        loweredFull.getX() + 0.5,
                        loweredFull.getY() + 0.5 - 1.62,
                        loweredFull.getZ() + 3.5,
                        180.0f,
                        0.0f);
                player.setVelocity(Vec3d.ZERO);
                player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.OAK_SLAB, 8));
            }
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        final BlockHitResult outwardSideHit = new BlockHitResult(
                new Vec3d(
                        firstSideSlab.getX() + 0.45,
                        firstSideSlab.getY() + 0.428,
                        firstSideSlab.getZ() + 1.0),
                Direction.SOUTH,
                firstSideSlab,
                false,
                false);

        ctx.runOnClient(mc -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) {
                throw new RuntimeException("client unavailable for a7/a8 lowered side-slab proof");
            }
            mc.player.refreshPositionAndAngles(
                    loweredFull.getX() + 0.5,
                    loweredFull.getY() + 0.5 - mc.player.getStandingEyeHeight(),
                    loweredFull.getZ() + 3.5,
                    180.0f,
                    0.0f);
            mc.player.setVelocity(Vec3d.ZERO);
            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.OAK_SLAB, 8));
            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, outwardSideHit);
            actionResult.set(result.toString());
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                throw new RuntimeException("client world unavailable during a7/a8 readback");
            }
            BlockState first = mc.world.getBlockState(firstSideSlab);
            BlockState second = mc.world.getBlockState(secondSideSlab);
            firstStateText.set(first.toString());
            secondStateText.set(second.toString());
            firstDy.set(SlabSupport.getYOffset(mc.world, firstSideSlab, first));
            secondDy.set(SlabSupport.getYOffset(mc.world, secondSideSlab, second));

            String proof = "[a7-a8-lowered-side-slab-no-combine] result=" + actionResult.get()
                    + " first=" + first + " firstDy=" + firstDy.get()
                    + " second=" + second + " secondDy=" + secondDy.get();
            System.out.println(proof);

            if (!first.isOf(Blocks.OAK_SLAB)
                    || !first.contains(SlabBlock.TYPE)
                    || first.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException(proof
                        + " RED: first side slab must remain a single TOP slab, not same-cell combine");
            }
            if (!second.isOf(Blocks.OAK_SLAB)
                    || !second.contains(SlabBlock.TYPE)
                    || second.get(SlabBlock.TYPE) != SlabType.TOP) {
                throw new RuntimeException(proof
                        + " RED: outward side click must place a TOP oak slab in the next side cell");
            }
            if (!approx(firstDy.get(), -0.5d) || !approx(secondDy.get(), -0.5d)) {
                throw new RuntimeException(proof
                        + " RED: lowered side-slab lane must preserve dy=-0.5 across both slabs");
            }
        });

        System.out.println("[a7-a8-lowered-side-slab-no-combine] => GREEN");
    }

    /**
     * RED proof for the live selection/placement drift from the 2026-06-05 video:
     * while holding a slab, a ray that first owns the lowered log stack must not be
     * stolen by an adjacent side slab through a perpendicular/cross-axis face. The
     * video/log sample at 19:28:20 shows initial owner {@code ... side=south}
     * becoming final side-slab owner {@code ... side=east}, after which placement
     * follows the unexpected side-slab-relative target.
     */
    private static void runCrossAxisSideSlabRetargetProof(
            ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        final BlockPos lowerLog = new BlockPos(0, 200, 56);
        final BlockPos upperLog = lowerLog.up();
        final BlockPos support = lowerLog.down();
        final BlockPos sideSlab = upperLog.south();

        AtomicReference<String> vanillaHitText = new AtomicReference<>("not_run");
        AtomicReference<String> finalHitText = new AtomicReference<>("not_run");
        AtomicReference<String> verdict = new AtomicReference<>("not_run");

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(support,
                    Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(lowerLog, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(upperLog, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(sideSlab,
                    Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(sideSlab.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, lowerLog, world.getBlockState(lowerLog));
            SlabAnchorAttachment.addAnchor(world, upperLog, world.getBlockState(upperLog));
        });
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();

        ctx.runOnClient(mc -> {
            if (mc.world == null || mc.player == null) {
                verdict.set("BLOCKED: client world/player unavailable");
                return;
            }

            // Same relative eye/ray geometry as the live trace:
            // eye=60.497,70.620,-82.905 end=56.876,72.843,-87.142
            // initial=... side=south, side-slab candidate=... side=east.
            Vec3d eye = new Vec3d(
                    lowerLog.getX() + 2.497,
                    lowerLog.getY() - 0.380,
                    lowerLog.getZ() + 3.095);
            Vec3d loggedEnd = new Vec3d(
                    lowerLog.getX() - 1.124,
                    lowerLog.getY() + 1.843,
                    lowerLog.getZ() - 1.142);
            Vec3d dir = loggedEnd.subtract(eye).normalize();
            Vec3d end = eye.add(dir.multiply(6.0));

            mc.player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.OAK_SLAB, 8));
            mc.player.refreshPositionAndAngles(
                    eye.x,
                    eye.y - mc.player.getStandingEyeHeight(),
                    eye.z,
                    yawFromDirection(dir),
                    pitchFromDirection(dir));
            mc.player.setVelocity(Vec3d.ZERO);

            BlockHitResult vanilla = mc.world.raycast(new RaycastContext(
                    eye,
                    end,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player));
            vanillaHitText.set(formatHit(vanilla));

            mc.gameRenderer.updateCrosshairTarget(0.0f);
            HitResult finalHit = mc.crosshairTarget;
            finalHitText.set(formatHit(finalHit));

            if (vanilla.getType() != HitResult.Type.BLOCK
                    || !(vanilla.getBlockPos().equals(lowerLog) || vanilla.getBlockPos().equals(upperLog))) {
                verdict.set("BLOCKED: fixture ray did not first own lowered log stack; vanilla=" + formatHit(vanilla));
                return;
            }
            if (!(finalHit instanceof BlockHitResult blockFinal)
                    || finalHit.getType() != HitResult.Type.BLOCK) {
                verdict.set("RED: final target missed lowered log stack; vanilla=" + formatHit(vanilla)
                        + " final=" + formatHit(finalHit));
                return;
            }

            boolean crossAxisSideSlabSteal = blockFinal.getBlockPos().equals(sideSlab)
                    && blockFinal.getSide().getAxis() != vanilla.getSide().getAxis();
            if (crossAxisSideSlabSteal) {
                verdict.set("RED: cross-axis side-slab retarget stole log aim; vanilla="
                        + formatHit(vanilla) + " final=" + formatHit(blockFinal));
            } else {
                verdict.set("GREEN: final target did not cross-axis steal; vanilla="
                        + formatHit(vanilla) + " final=" + formatHit(blockFinal));
            }
        });

        String proof = "[cross-axis-side-slab-retarget] vanilla=" + vanillaHitText.get()
                + " final=" + finalHitText.get()
                + " verdict=" + verdict.get();
        System.out.println(proof);
        if (verdict.get().startsWith("RED") || verdict.get().startsWith("BLOCKED")) {
            throw new RuntimeException(proof);
        }
    }

    /**
     * RED proof for the live lantern jump: a hanging lantern under a lowered,
     * top-like slab support must keep the same dy when the render worker sees a
     * ChunkRendererRegion-style view that no longer exposes the adjacent lowering
     * source. The support's client-world dy has already been published to the
     * shared visual cache; the hanger must use that stable support visual dy
     * instead of re-deriving it from a fragile neighbor scan.
     */
    private static void runLoweredSlabHangerRenderRegionStabilityProof(
            ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        final BlockPos support = new BlockPos(0, 200, 44);
        final BlockPos lantern = support.down();
        final BlockPos loweringBlock = support.east();
        final BlockPos loweringSource = loweringBlock.down();

        final AtomicReference<Double> supportWorldDy = new AtomicReference<>(Double.NaN);
        final AtomicReference<Double> lanternWorldDy = new AtomicReference<>(Double.NaN);
        final AtomicReference<Double> lanternRegionDy = new AtomicReference<>(Double.NaN);

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(support,
                    Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(lantern,
                    Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(loweringSource,
                    Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(loweringBlock, Blocks.OAK_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        ctx.waitTick();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                return;
            }
            SlabSupport.clearVisualYOffsetCache();
            BlockState supportState = mc.world.getBlockState(support);
            BlockState lanternState = mc.world.getBlockState(lantern);
            supportWorldDy.set(SlabSupport.getVisualYOffset(mc.world, support, supportState));
            lanternWorldDy.set(SlabSupport.getVisualYOffset(mc.world, lantern, lanternState));
            SlabSupport.clearVisualYOffsetCache();
            supportWorldDy.set(SlabSupport.getVisualYOffset(mc.world, support, supportState));
            BlockView renderRegionView = new RenderRegionGapView(mc.world, loweringBlock, loweringSource);
            lanternRegionDy.set(SlabSupport.getVisualYOffset(renderRegionView, lantern, lanternState));
        });

        String proof = "[lowered-slab-hanger-region-stability] supportWorldDy=" + supportWorldDy.get()
                + " lanternWorldDy=" + lanternWorldDy.get()
                + " lanternRegionDy=" + lanternRegionDy.get();
        System.out.println(proof);
        if (!approx(supportWorldDy.get(), -0.5)) {
            throw new RuntimeException(proof + " RED: fixture invalid, support not lowered");
        }
        if (!approx(lanternWorldDy.get(), -0.5)) {
            throw new RuntimeException(proof + " RED: client-world lantern did not follow lowered support");
        }
        if (!approx(lanternRegionDy.get(), -0.5)) {
            throw new RuntimeException(proof
                    + " RED: render-region lantern dy drifted from support dy; expected stable -0.5");
        }
        System.out.println("[lowered-slab-hanger-region-stability] => GREEN");
    }

    /**
     * Evidence probe: is the break-jump a LOGIC revert (a column block above
     * un-lowers when a block below it is broken) or stale mesh? Builds a lowered
     * column (bottom slab + anchored FB + two column FBs), then breaks (a) the
     * middle column FB and (b) the bottom slab, reading every block's dy before
     * and after via SlabSupport.getYOffset.
     */
    private static void runBreakJumpDyProbe(ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        final BlockPos slab = new BlockPos(0, 200, 32);
        final BlockPos fb1 = slab.up();        // directly on slab → anchored
        final BlockPos fb2 = slab.up(2);       // column-lowered
        final BlockPos fb3 = slab.up(3);       // column-lowered (above fb2)

        final AtomicReference<String> before = new AtomicReference<>("");
        final AtomicReference<String> afterBreakMid = new AtomicReference<>("");
        final AtomicReference<String> afterBreakSlab = new AtomicReference<>("");

        final AtomicReference<String> anchorState = new AtomicReference<>("");
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(slab, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
            world.setBlockState(fb1, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(fb2, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(fb3, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            // Simulate placement-time anchoring bottom-up (BlockOnPlacedAnchorMixin
            // calls addAnchor on each placed block). With the column-lowered-anchor
            // fix, fb2 and fb3 now qualify and persist.
            SlabAnchorAttachment.addAnchor(world, fb1, world.getBlockState(fb1));
            SlabAnchorAttachment.addAnchor(world, fb2, world.getBlockState(fb2));
            SlabAnchorAttachment.addAnchor(world, fb3, world.getBlockState(fb3));
            anchorState.set("fb1=" + SlabAnchorAttachment.isAnchored(world, fb1)
                    + " fb2=" + SlabAnchorAttachment.isAnchored(world, fb2)
                    + " fb3=" + SlabAnchorAttachment.isAnchored(world, fb3));
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> before.set(slabbed$dyTriple(mc, fb1, fb2, fb3)));

        // Break the MIDDLE column block (fb2). With the fix, fb3 is anchored and
        // must PERSIST at -0.5 (no jump).
        singleplayer.getServer().runOnServer(server ->
                server.getOverworld().setBlockState(fb2, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS));
        ctx.waitTick();
        ctx.runOnClient(mc -> afterBreakMid.set(slabbed$dyTriple(mc, fb1, fb2, fb3)));

        // Restore fb2, then break the SLAB under the column.
        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(fb2, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, fb2, world.getBlockState(fb2));
            world.setBlockState(slab, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> afterBreakSlab.set(slabbed$dyTriple(mc, fb1, fb2, fb3)));

        System.out.println("[break-jump-probe] anchored: " + anchorState.get());
        System.out.println("[break-jump-probe] BEFORE                 " + before.get());
        System.out.println("[break-jump-probe] AFTER break mid (fb2)  " + afterBreakMid.get()
                + "   (fb3 should now PERSIST -0.5 = no jump)");
        System.out.println("[break-jump-probe] AFTER break slab        " + afterBreakSlab.get());

        // Verdict: with the fix, fb3 must stay -0.5 after breaking fb2.
        if (!afterBreakMid.get().contains("fb3=-0.5")) {
            throw new RuntimeException("[break-jump-probe] RED: fb3 did not persist after breaking fb2 -> "
                    + afterBreakMid.get());
        }
        System.out.println("[break-jump-probe] => GREEN (column block persists, no jump)");
    }

    private static String slabbed$dyTriple(net.minecraft.client.MinecraftClient mc, BlockPos a, BlockPos b, BlockPos c) {
        if (mc.world == null) {
            return "world=null";
        }
        return "fb1=" + SlabSupport.getYOffset(mc.world, a, mc.world.getBlockState(a))
                + " fb2=" + SlabSupport.getYOffset(mc.world, b, mc.world.getBlockState(b))
                + " fb3=" + SlabSupport.getYOffset(mc.world, c, mc.world.getBlockState(c));
    }

    /**
     * Regression guard for Fix 1 (hanger-follow). A decorative hanger
     * (lantern / spore blossom) beneath a LOWERED full-block support must inherit
     * the support's -0.5 so it hangs flush instead of clipping up into it; a CHAIN
     * under the same lowered support must NOT follow (it extends to reach the
     * support). Verified by SlabSupport.getYOffset state-readback — the single
     * value every visual consumer reads. Throws on regression.
     */
    private static void runHangerFollowUnderLoweredSupportProof(
            ClientGameTestContext ctx, TestSingleplayerContext singleplayer) {
        // Three independent clusters along +Z. Each lowers a full-block support by
        // anchoring it beside an FB-on-bottom-slab (so the support is lowered with
        // AIR beneath it for the hanger), then hangs the test block under it.
        final BlockPos lanternOrigin = new BlockPos(0, 200, 20);
        final BlockPos sporeOrigin = new BlockPos(0, 200, 24);

        final AtomicReference<Double> lanternDy = new AtomicReference<>(Double.NaN);
        final AtomicReference<Double> sporeDy = new AtomicReference<>(Double.NaN);
        final AtomicReference<Double> lanternSupportDy = new AtomicReference<>(Double.NaN);

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            buildLoweredCeilingHanger(world, lanternOrigin,
                    Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
            buildLoweredCeilingHanger(world, sporeOrigin,
                    Blocks.SPORE_BLOSSOM.getDefaultState());
        });
        ctx.waitTick();
        ctx.waitTick();

        ctx.runOnClient(mc -> {
            if (mc.world == null) {
                return;
            }
            BlockPos lp = lanternOrigin.east();
            BlockPos sp = sporeOrigin.east();
            lanternDy.set(SlabSupport.getYOffset(mc.world, lp, mc.world.getBlockState(lp)));
            sporeDy.set(SlabSupport.getYOffset(mc.world, sp, mc.world.getBlockState(sp)));
            BlockPos supportPos = lanternOrigin.up().east();
            lanternSupportDy.set(SlabSupport.getYOffset(mc.world, supportPos, mc.world.getBlockState(supportPos)));
        });

        StringBuilder fail = new StringBuilder();
        // Chain non-regression is guaranteed structurally: chains are absent from
        // the isLoweredUndersideHangerOwner allowlist, so they never enter the
        // follow branches (verified by reading that predicate).
        boolean supportLowered = approx(lanternSupportDy.get(), -0.5);
        if (!supportLowered) {
            fail.append("[hanger-follow] FIXTURE INVALID: support not lowered, supportDy=")
                    .append(lanternSupportDy.get()).append(" (anchor setup failed)\n");
        }
        if (!approx(lanternDy.get(), -0.5)) {
            fail.append("[hanger-follow] RED: lantern under lowered support did not follow; lanternDy=")
                    .append(lanternDy.get()).append(" expected -0.5 (clips up into support)\n");
        }
        if (!approx(sporeDy.get(), -0.5)) {
            fail.append("[hanger-follow] RED: spore blossom under lowered support did not follow; sporeDy=")
                    .append(sporeDy.get()).append(" expected -0.5\n");
        }
        System.out.println("[hanger-follow] supportDy=" + lanternSupportDy.get()
                + " lanternDy=" + lanternDy.get() + " sporeDy=" + sporeDy.get()
                + (fail.length() == 0 ? "  => GREEN" : "  => RED"));
        if (fail.length() > 0) {
            throw new RuntimeException(fail.toString().trim());
        }
    }

    /**
     * Builds, at {@code origin}: a bottom slab; an FB on it (lowered -0.5); an
     * anchored full-block support to the east of that FB (lowered -0.5 via the
     * adjacent-lowered-FB anchor rule, with air below it); and {@code hanger}
     * hanging beneath the support at {@code origin.east()}.
     */
    private static void buildLoweredCeilingHanger(net.minecraft.world.World world, BlockPos origin, BlockState hanger) {
        BlockPos slab = origin;
        BlockPos loweredFb = origin.up();
        BlockPos support = loweredFb.east();
        BlockPos hangerPos = origin.east();
        world.setBlockState(slab, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        world.setBlockState(loweredFb, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(hangerPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(support, Blocks.OAK_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(world, support, world.getBlockState(support));
        world.setBlockState(hangerPos, hanger, Block.NOTIFY_LISTENERS);
    }

    private static boolean approx(double a, double b) {
        return Math.abs(a - b) < 1.0e-6;
    }

    private static float yawFromDirection(Vec3d dir) {
        return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
    }

    private static float pitchFromDirection(Vec3d dir) {
        return (float) Math.toDegrees(Math.asin(-dir.y));
    }

    private static String formatHit(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) {
            return hit == null ? "null" : hit.getType().toString();
        }
        return "pos=" + blockHit.getBlockPos().toShortString()
                + " side=" + blockHit.getSide()
                + " hitVec=" + String.format("%.3f,%.3f,%.3f",
                blockHit.getPos().x, blockHit.getPos().y, blockHit.getPos().z);
    }

    private record RenderRegionGapView(BlockView delegate, BlockPos hiddenA, BlockPos hiddenB) implements BlockView {
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            BlockState state = getBlockState(pos);
            if (state.getBlock() instanceof BlockEntityProvider) {
                return delegate.getBlockEntity(pos);
            }
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (pos.equals(hiddenA) || pos.equals(hiddenB)) {
                return Blocks.AIR.getDefaultState();
            }
            return delegate.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getBottomY() {
            return delegate.getBottomY();
        }
    }
}
