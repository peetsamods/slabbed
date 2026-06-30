package com.slabbed.client;

import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class OffsetTargetingEvents {
    private static boolean initialized;

    private OffsetTargetingEvents() {
    }

    public static void init(IEventBus eventBus) {
        if (initialized) {
            return;
        }
        initialized = true;
        eventBus.addListener(OffsetTargetingEvents::clientTick);
        eventBus.addListener(OffsetTargetingEvents::interactionKeyMappingTriggered);
        eventBus.addListener(OffsetTargetingEvents::renderGuiPre);
    }

    private static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            refresh(Minecraft.getInstance(), 1.0f);
        }
    }

    private static void renderGuiPre(RenderGuiEvent.Pre event) {
        Minecraft client = Minecraft.getInstance();
        HitResult current = client == null ? null : client.hitResult;
        BlockHitResult visualOwnerHit = current instanceof BlockHitResult currentBlock
                && current.getType() == HitResult.Type.BLOCK
                ? ordinaryFullBlockVisualHit(client, currentBlock, event.getPartialTick())
                : null;
        if (visualOwnerHit != null) {
            client.hitResult = visualOwnerHit;
            return;
        }
        if (current == null
                || current.getType() == HitResult.Type.MISS) {
            refresh(client, event.getPartialTick());
        }
    }

    private static void interactionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem() || event.isAttack()) {
            refresh(Minecraft.getInstance(), 1.0f);
        }
    }

    private static void refresh(Minecraft client, float partialTick) {
        if (!SlabbedOffsetRaycast.ENABLED
                || client == null
                || client.level == null
                || client.gameMode == null
                || client.getCameraEntity() == null) {
            return;
        }

        Vec3 eye = client.getCameraEntity().getEyePosition(partialTick);
        Vec3 look = client.getCameraEntity().getViewVector(partialTick);
        double range = client.gameMode.getPickRange();
        Vec3 end = eye.add(look.x * range, look.y * range, look.z * range);
        CollisionContext context = client.getCameraEntity() instanceof LocalPlayer player
                ? CollisionContext.of(player)
                : CollisionContext.empty();
        BlockHitResult offsetHit = SlabbedOffsetRaycast.raycast(client.level, eye, end, context);
        HitResult current = client.hitResult;
        if (current != null && current.getType() == HitResult.Type.ENTITY) {
            return;
        }
        if (offsetHit.getType() != HitResult.Type.BLOCK || !isLowered(client, offsetHit.getBlockPos())) {
            if (current instanceof BlockHitResult currentBlock && current.getType() == HitResult.Type.BLOCK) {
                BlockHitResult visualOwnerHit = ordinaryFullBlockVisualHit(client, currentBlock, partialTick);
                if (visualOwnerHit != null) {
                    client.hitResult = visualOwnerHit;
                }
            }
            return;
        }

        double offsetDistance = offsetHit.getLocation().distanceToSqr(eye);
        if (current instanceof BlockHitResult currentBlock && current.getType() == HitResult.Type.BLOCK) {
            double currentDistance = currentBlock.getLocation().distanceToSqr(eye);
            if (!isInvisibleOrdinaryFullBlockHit(client, currentBlock)
                    && currentDistance < offsetDistance - 1.0e-6d) {
                return;
            }
        }

        client.hitResult = offsetHit;
    }

    private static BlockHitResult ordinaryFullBlockVisualHit(
            Minecraft client,
            BlockHitResult currentBlock,
            float partialTick
    ) {
        if (client == null
                || client.level == null
                || client.gameMode == null
                || client.getCameraEntity() == null) {
            return null;
        }

        Vec3 eye = client.getCameraEntity().getEyePosition(partialTick);
        Vec3 look = client.getCameraEntity().getViewVector(partialTick);
        double range = client.gameMode.getPickRange();
        Vec3 end = eye.add(look.x * range, look.y * range, look.z * range);
        CollisionContext context = client.getCameraEntity() instanceof LocalPlayer player
                ? CollisionContext.of(player)
                : CollisionContext.empty();
        double currentDistance = currentBlock.getLocation().distanceToSqr(eye);
        BlockPos base = currentBlock.getBlockPos();
        boolean currentInvisible = isInvisibleOrdinaryFullBlockHit(client, currentBlock);
        if (currentInvisible) {
            BlockHitResult directVisualHit = ordinaryFullBlockVisualHit(client, base, eye, end, context);
            if (directVisualHit != null) {
                return directVisualHit;
            }
        }
        BlockHitResult best = null;
        double bestDistance = currentInvisible ? Double.POSITIVE_INFINITY : currentDistance + 1.0e-6d;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos candidatePos = base.offset(dx, dy, dz);
                    BlockHitResult candidateHit = ordinaryFullBlockVisualHit(
                            client,
                            candidatePos,
                            eye,
                            end,
                            context);
                    if (candidateHit == null) {
                        continue;
                    }
                    double candidateDistance = candidateHit.getLocation().distanceToSqr(eye);
                    if (candidateDistance < bestDistance) {
                        bestDistance = candidateDistance;
                        best = candidateHit;
                    }
                }
            }
        }
        return best;
    }

    private static BlockHitResult ordinaryFullBlockVisualHit(
            Minecraft client,
            BlockPos ownerPos,
            Vec3 eye,
            Vec3 end,
            CollisionContext context
    ) {
        BlockState ownerState = client.level.getBlockState(ownerPos);
        double dy = ClientDy.dyFor(client.level, ownerPos, ownerState);
        if (!isOrdinaryLoweredFullBlock(client, ownerPos, ownerState, dy)) {
            return null;
        }

        VoxelShape shifted = Shapes.block().move(0.0d, dy, 0.0d);
        BlockHitResult hit = client.level.clipWithInteractionOverride(eye, end, ownerPos, shifted, ownerState);
        if (hit == null) {
            return null;
        }
        return preferVisualBoundaryFace(hit, ownerPos, dy);
    }

    private static boolean isInvisibleOrdinaryFullBlockHit(Minecraft client, BlockHitResult hit) {
        if (client == null || client.level == null || hit == null) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = client.level.getBlockState(pos);
        double dy = ClientDy.dyFor(client.level, pos, state);
        if (!isOrdinaryLoweredFullBlock(client, pos, state, dy)) {
            return false;
        }
        Vec3 localHit = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        double epsilon = 1.0e-5d;
        return localHit.x < -epsilon
                || localHit.x > 1.0d + epsilon
                || localHit.y < dy - epsilon
                || localHit.y > 1.0d + dy + epsilon
                || localHit.z < -epsilon
                || localHit.z > 1.0d + epsilon;
    }

    private static boolean isOrdinaryLoweredFullBlock(
            Minecraft client,
            BlockPos pos,
            BlockState state,
            double dy
    ) {
        return dy < -1.0e-6d
                && state != null
                && !state.isAir()
                && !(state.getBlock() instanceof SlabBlock)
                && !(state.getBlock() instanceof EntityBlock)
                && state.isSolidRender(client.level, pos);
    }

    private static BlockHitResult preferVisualBoundaryFace(BlockHitResult hit, BlockPos pos, double dy) {
        Direction direction = hit.getDirection();
        if (!direction.getAxis().isHorizontal()) {
            return hit;
        }
        Vec3 localHit = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        double epsilon = 1.0e-5d;
        Direction visualFace = null;
        if (Math.abs(localHit.y - (1.0d + dy)) <= epsilon) {
            visualFace = Direction.UP;
        } else if (Math.abs(localHit.y - dy) <= epsilon) {
            visualFace = Direction.DOWN;
        }
        return visualFace == null
                ? hit
                : new BlockHitResult(hit.getLocation(), visualFace, pos, hit.isInside());
    }

    private static boolean isLowered(Minecraft client, BlockPos pos) {
        BlockState state = client.level.getBlockState(pos);
        return ClientDy.dyFor(client.level, pos, state) < -1.0e-6d;
    }
}
