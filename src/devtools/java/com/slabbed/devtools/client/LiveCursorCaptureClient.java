package com.slabbed.devtools.client;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.devtools.util.LiveCursorIntentRecorder;
import com.slabbed.util.SlabbedOffsetRaycast;
import com.slabbed.util.SlabSupport;
import java.util.LinkedHashMap;
import java.util.Locale;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/** Captures the active client pick and the actual block-highlight event for the dev recorder. */
public final class LiveCursorCaptureClient {
    private static final double EPSILON = 1.0e-6d;
    /** Float-noise guard for same-ray distance comparisons; never a motion-tolerance knob. */
    private static final double SAME_RAY_MARGIN = 1.0e-4d;
    private static final double CAPTURE_REACH = 6.0d;

    private static ClientLevel lastLevel;
    private static long lastCursorTick = Long.MIN_VALUE;
    private static long lastOutlineTick = Long.MIN_VALUE;

    private LiveCursorCaptureClient() {
    }

    static void onClientTick(Minecraft minecraft) {
        if (!LiveCursorIntentRecorder.enabled()
                || minecraft == null
                || minecraft.level == null
                || minecraft.player == null) {
            return;
        }
        ClientLevel level = minecraft.level;
        if (lastLevel != level) {
            lastLevel = level;
            lastCursorTick = Long.MIN_VALUE;
            lastOutlineTick = Long.MIN_VALUE;
        }
        long tick = level.getGameTime();
        if (tick == lastCursorTick) {
            return;
        }
        lastCursorTick = tick;

        Entity camera = minecraft.getCameraEntity();
        HitResult target = minecraft.hitResult;
        if (camera == null || target == null) {
            return;
        }
        Vec3 eye = camera.getEyePosition();
        Vec3 look = camera.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(CAPTURE_REACH));
        ItemStack held = minecraft.player.getMainHandItem();

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("tick", Long.toString(tick));
        row.put("heldItem", held.isEmpty()
                ? "empty"
                : BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
        row.put("finalHitType", target.getType().name());
        row.put("finalHitVec", formatVec(target.getLocation()));
        if (!(target instanceof BlockHitResult blockHit)
                || target.getType() != HitResult.Type.BLOCK) {
            putNoBlock(row);
            LiveCursorIntentRecorder.recordCursor(row);
            return;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        CollisionContext context = CollisionContext.of(camera);
        VoxelShape outline = state.getShape(level, pos, context);
        VoxelShape collision = SlabSupport.collisionShapeForBroadphaseCell(
                state, level, pos, context);
        VoxelShape rawOutline = state.getShape(
                EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        VoxelShape rawCollision = state.getCollisionShape(
                EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        double logicalDy = ClientDy.dyFor(level, pos, state);
        // minecraft.hitResult is a frame product while this handler runs at tick time, so a bare
        // replay/pick inequality only measures camera motion between the two samples. The tick
        // layer's one same-input proof is the nearer-surface check: both clips below share the
        // exact tick ray, so a raycast that lands strictly beyond the target's own pickable
        // surface has demonstrably skipped it. Everything else stays data, never a verdict.
        BlockHitResult replay = SlabbedOffsetRaycast.raycast(level, eye, end, context);
        boolean replayIsBlock = replay != null && replay.getType() == HitResult.Type.BLOCK;
        boolean replayPosFaceMatch = replayIsBlock
                && pos.equals(replay.getBlockPos())
                && blockHit.getDirection() == replay.getDirection();
        BlockHitResult outlineClip = outline.isEmpty() ? null : outline.clip(eye, end, pos);
        double outlineClipDistance = outlineClip == null
                ? Double.NaN
                : eye.distanceTo(outlineClip.getLocation());
        double replayDistance = replayIsBlock
                ? eye.distanceTo(replay.getLocation())
                : Double.NaN;
        boolean skippedNearerSurface = replaySkippedNearerSurface(
                outlineClip != null, replayIsBlock, outlineClipDistance, replayDistance);

        row.put("finalHitPos", pos.toShortString());
        row.put("finalHitFace", blockHit.getDirection().getSerializedName());
        row.put("finalHitState", state.toString());
        row.put("finalDy", format(logicalDy));
        row.put("finalOwnerLaneKind", laneKind(level, pos, state));
        row.put("outlineOwnerPos", pos.toShortString());
        row.put("outlineOwnerState", state.toString());
        row.put("outlineDy", measuredShift(rawOutline, outline));
        row.put("outlineReplayHit", outlineClip != null ? "hit" : "miss");
        row.put("outlineBounds", formatBounds(outline));
        row.put("outlineClipDistance", format(outlineClipDistance));
        row.put("raycastOwnerPos", pos.toShortString());
        row.put("raycastDy", format(logicalDy));
        row.put("raycastReplayHit", replayPosFaceMatch ? "hit" : "miss");
        row.put("raycastReplayPos", replayIsBlock ? replay.getBlockPos().toShortString() : "none");
        row.put("raycastReplayFace", replayIsBlock
                ? replay.getDirection().getSerializedName()
                : "none");
        row.put("raycastReplayDistance", format(replayDistance));
        row.put("raycastReplayLocationDelta", replayIsBlock
                ? format(replay.getLocation().distanceTo(target.getLocation()))
                : "unknown");
        row.put("raycastSkippedNearerSurface", Boolean.toString(skippedNearerSurface));
        row.put("raycastVerdict", replayPosFaceMatch
                ? "PASS"
                : skippedNearerSurface ? "FAIL" : "unknown");
        row.put("raycastBounds", formatBounds(outline));
        row.put("collisionDy", measuredShift(rawCollision, collision));
        row.put("collisionVerdict", "unknown".equals(row.get("collisionDy")) ? "UNKNOWN" : "PASS");
        row.put("collisionBounds", formatBounds(collision));
        row.put("expectedTargetOwner", pos.toShortString());
        row.put("expectedDy", format(logicalDy));
        row.put("expectedAction", held.getItem() instanceof net.minecraft.world.item.BlockItem
                ? "place_block"
                : "use_block");
        LiveCursorIntentRecorder.recordCursor(row);
    }

    static void onRenderHighlight(RenderHighlightEvent.Block event) {
        if (!LiveCursorIntentRecorder.enabled() || event == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Camera camera = event.getCamera();
        BlockHitResult hit = event.getTarget();
        if (level == null || camera == null || hit == null) {
            return;
        }
        long tick = level.getGameTime();
        if (tick == lastOutlineTick) {
            return;
        }
        lastOutlineTick = tick;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        Entity cameraEntity = camera.getEntity();
        VoxelShape outline = state.getShape(
                level,
                pos,
                cameraEntity == null ? CollisionContext.empty() : CollisionContext.of(cameraEntity));
        AABB worldBounds = outline.isEmpty()
                ? null
                : outline.bounds().move(pos.getX(), pos.getY(), pos.getZ());
        AABB cameraRelative = worldBounds == null
                ? null
                : worldBounds.move(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("outlineRenderRoute", "neoforge_block_highlight_event");
        row.put("renderedOutlinePos", pos.toShortString());
        row.put("renderedOutlineState", state.toString());
        row.put("renderedOutlineBounds", formatBounds(outline));
        row.put("renderedOutlineWorldBounds", formatBounds(worldBounds));
        row.put("renderedOutlineCameraRelativeBounds", formatBounds(cameraRelative));
        row.put("renderedOutlineHitVec", formatVec(hit.getLocation()));
        appendFrameCoherence(row, minecraft, level, cameraEntity, hit,
                event.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        LiveCursorIntentRecorder.recordRenderedOutline(row);
    }

    /**
     * The frame's own pick and a frame replay are the only comparisons that share one sampling
     * instant with the highlight event, so they alone may carry red/green authority for outline
     * coherence. Their scope is deliberately narrow: the event target and the frame pick are the
     * same object on the first-party path, so that comparison verifies event plumbing, not
     * outline geometry; and because the game's pick is itself the offset raycast, the frame
     * replay proves wiring and determinism, not algorithmic correctness — geometric truth stays
     * with the gametests. The replay rebuilds the pick's own double-precision inputs (the camera
     * entity's eye and view vector at the event's partial tick), which hold in every camera mode.
     */
    private static void appendFrameCoherence(
            LinkedHashMap<String, String> row,
            Minecraft minecraft,
            ClientLevel level,
            Entity cameraEntity,
            BlockHitResult eventHit,
            float partialTick) {
        HitResult gamePick = minecraft.hitResult;
        BlockHitResult gamePickBlock =
                gamePick instanceof BlockHitResult block && gamePick.getType() == HitResult.Type.BLOCK
                        ? block
                        : null;
        row.put("frameGamePickType", gamePick == null ? "none" : gamePick.getType().name());
        row.put("frameGamePickPos", gamePickBlock == null
                ? "none"
                : gamePickBlock.getBlockPos().toShortString());
        row.put("frameGamePickFace", gamePickBlock == null
                ? "none"
                : gamePickBlock.getDirection().getSerializedName());

        String frameRaycastVerdict = "not_run";
        String frameReplayPos = "none";
        String frameReplayFace = "none";
        String frameReplayLocationDelta = "unknown";
        if (cameraEntity != null) {
            Vec3 frameEye = cameraEntity.getEyePosition(partialTick);
            Vec3 frameLook = cameraEntity.getViewVector(partialTick);
            Vec3 frameEnd = frameEye.add(frameLook.scale(CAPTURE_REACH));
            BlockHitResult frameReplay = SlabbedOffsetRaycast.raycast(
                    level, frameEye, frameEnd, CollisionContext.of(cameraEntity));
            boolean frameReplayIsBlock =
                    frameReplay != null && frameReplay.getType() == HitResult.Type.BLOCK;
            if (frameReplayIsBlock) {
                frameReplayPos = frameReplay.getBlockPos().toShortString();
                frameReplayFace = frameReplay.getDirection().getSerializedName();
                frameReplayLocationDelta =
                        format(frameReplay.getLocation().distanceTo(eventHit.getLocation()));
            }
            boolean posFaceMatch = frameReplayIsBlock
                    && eventHit.getBlockPos().equals(frameReplay.getBlockPos())
                    && eventHit.getDirection() == frameReplay.getDirection();
            frameRaycastVerdict = posFaceMatch ? "PASS" : "FAIL";
        }
        row.put("frameReplayPos", frameReplayPos);
        row.put("frameReplayFace", frameReplayFace);
        row.put("frameReplayLocationDelta", frameReplayLocationDelta);
        row.put("frameRaycastVerdict", frameRaycastVerdict);
    }

    private static void putNoBlock(LinkedHashMap<String, String> row) {
        row.put("finalHitPos", "none");
        row.put("finalHitFace", "none");
        row.put("finalHitState", "none");
        row.put("finalDy", "unknown");
        row.put("finalOwnerLaneKind", "none");
        row.put("outlineOwnerPos", "none");
        row.put("outlineOwnerState", "none");
        row.put("outlineDy", "unknown");
        row.put("outlineReplayHit", "none");
        row.put("outlineBounds", "none");
        row.put("raycastOwnerPos", "none");
        row.put("raycastDy", "unknown");
        row.put("raycastReplayHit", "none");
        row.put("raycastVerdict", "NOT_RUN");
        row.put("raycastBounds", "none");
        row.put("collisionDy", "unknown");
        row.put("collisionVerdict", "NOT_RUN");
        row.put("collisionBounds", "none");
        row.put("expectedTargetOwner", "MISS");
        row.put("expectedDy", "unknown");
        row.put("expectedAction", "miss");
    }

    private static String laneKind(ClientLevel level, BlockPos pos, BlockState state) {
        if (SlabAnchorAttachment.isFrozenFlat(level, pos)) {
            return "frozen_flat";
        }
        if (SlabAnchorAttachment.isAnchored(level, pos)) {
            return "anchored_full_block";
        }
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(level, pos, state)) {
            return "persistent_lowered_slab_carrier";
        }
        return "geometric_or_flush";
    }

    /**
     * Same-ray proof for a red raycast split: both distances were measured along the identical
     * tick ray, so the replay demonstrably skipped the target's pickable surface only when that
     * surface sits strictly nearer than the replay's own landing (or the replay missed entirely).
     * Equal-within-margin distances are float noise, never a skip.
     */
    static boolean replaySkippedNearerSurface(
            boolean outlineClipped,
            boolean replayIsBlock,
            double outlineClipDistance,
            double replayDistance) {
        return outlineClipped
                && (!replayIsBlock || outlineClipDistance + SAME_RAY_MARGIN < replayDistance);
    }

    private static String measuredShift(VoxelShape raw, VoxelShape live) {
        if (raw == null || live == null || raw.isEmpty() || live.isEmpty()) {
            return "unknown";
        }
        AABB rawBounds = raw.bounds();
        AABB liveBounds = live.bounds();
        double minShift = liveBounds.minY - rawBounds.minY;
        double maxShift = liveBounds.maxY - rawBounds.maxY;
        return Math.abs(minShift - maxShift) <= EPSILON ? format(minShift) : "unknown";
    }

    private static String formatBounds(VoxelShape shape) {
        return shape == null || shape.isEmpty() ? "empty" : formatBounds(shape.bounds());
    }

    private static String formatBounds(AABB box) {
        if (box == null) {
            return "empty";
        }
        return "min=(" + format(box.minX) + ',' + format(box.minY) + ',' + format(box.minZ)
                + "),max=(" + format(box.maxX) + ',' + format(box.maxY) + ',' + format(box.maxZ) + ')';
    }

    private static String formatVec(Vec3 vec) {
        return vec == null
                ? "unknown"
                : format(vec.x) + ',' + format(vec.y) + ',' + format(vec.z);
    }

    private static String format(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.6f", value) : "unknown";
    }
}
