package com.slabbed.client;

import com.slabbed.compat.MinecraftVersions;
import com.slabbed.util.SlabSupport;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Screen overlays sample occupied cells in their stored coordinate frame, not their empty raw cells. */
public final class LoweredBlockOverlay {
    private static final int OWNER_DEPTH = 16;

    private LoweredBlockOverlay() {}

    public static BlockState find(Player player, AABB nearPlane) {
        if (player.noPhysics) return null;
        var world = player.level();
        for (int sample = 0; sample < 8; sample++) {
            double x = player.getX() + ((sample & 1) - 0.5F) * player.getBbWidth() * 0.8F;
            double y = player.getEyeY() + (((sample >> 1) & 1) - 0.5F) * 0.1F * player.getScale();
            double z = player.getZ() + (((sample >> 2) & 1) - 0.5F) * player.getBbWidth() * 0.8F;
            BlockPos cell = BlockPos.containing(x, y, z);
            for (int depth = 0; depth <= OWNER_DEPTH; depth++) {
                BlockPos owner = cell.above(depth);
                BlockState state = world.getBlockState(owner);
                if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) continue;
                double dy = SlabSupport.getYOffset(world, owner, state);
                if (depth > 0 && dy >= -1.0e-6) continue;
                // Inverse-transform the vanilla sample. The block's own view-blocking predicate
                // retains material/shape eligibility, including version-specific near-plane rules.
                if (!BlockPos.containing(x, y - dy, z).equals(owner)) continue;
                AABB originalPlane = nearPlane == null ? null : nearPlane.move(0, -dy, 0);
                if (blocksView(state, world, owner, originalPlane)) return state;
            }
        }
        return null;
    }

    private static boolean blocksView(BlockState state, BlockGetter world, BlockPos pos, AABB plane) {
        return MinecraftVersions.AT_LEAST_26_3
                ? Modern.blocksView(state, world, pos, plane) : Legacy.blocksView(state, world, pos);
    }

    /** Isolated so 26.2 never links the near-plane signature introduced in 26.3. */
    static final class Modern {
        static boolean blocksView(BlockState state, BlockGetter world, BlockPos pos, AABB plane) {
            return state.isViewBlocking(world, pos, plane);
        }
    }

    static final class Legacy {
        private static final MethodHandle VIEW_BLOCKING;
        static {
            try {
                VIEW_BLOCKING = MethodHandles.publicLookup().findVirtual(BlockState.class, "isViewBlocking",
                        MethodType.methodType(boolean.class, BlockGetter.class, BlockPos.class));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Legacy view-blocking predicate is missing", e);
            }
        }
        static boolean blocksView(BlockState state, BlockGetter world, BlockPos pos) {
            try {
                return (boolean) VIEW_BLOCKING.invokeExact(state, world, pos);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
