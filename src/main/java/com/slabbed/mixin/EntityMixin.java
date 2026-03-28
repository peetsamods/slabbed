package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiFunction;
import java.util.function.Function;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityInvoker
{
    @Unique
    private static final boolean DEBUG_RAYCAST = false;

    @Inject(method = "raycast", at = @At(value = "RETURN"), cancellable = true)
    public void slabbed$raycast(double maxDistance, float tickProgress, boolean includeFluids, CallbackInfoReturnable<HitResult> cir)
    {
        Vec3d cameraPos = slabbed$getCameraPosVec(tickProgress).add(0, 0.5, 0);
        Vec3d vec3d2 = slabbed$getRotationVec(tickProgress);
        Vec3d vec3d3 = cameraPos.add(vec3d2.x * maxDistance, vec3d2.y * maxDistance, vec3d2.z * maxDistance);
        World world = slabbed$getEntityWorld();

        HitResult highResult = raycast(
            world,
            new RaycastContext(
                cameraPos,
                vec3d3,
                RaycastContext.ShapeType.OUTLINE,
                includeFluids ? RaycastContext.FluidHandling.ANY : RaycastContext.FluidHandling.NONE,
                (Entity) (Object) this)
        );

        if (!(highResult instanceof BlockHitResult bhr && SlabSupport.shouldOffsetDown(world, bhr.getBlockPos(), world.getBlockState(bhr.getBlockPos()))))
        {
            return;
        }

        HitResult returnValue = cir.getReturnValue();

        final double miss = 0.02;
        final double hit = 0.05;

        if (returnValue.getPos().distanceTo(cameraPos) > highResult.getPos().distanceTo(cameraPos))
        {
            cir.setReturnValue(highResult);

            if (DEBUG_RAYCAST)
            {
                GizmoDrawing.box(new Box(returnValue.getPos().add(-miss, -miss, -miss), returnValue.getPos().add(miss, miss, miss)), DrawStyle.filled(0x20ff00ff));
                GizmoDrawing.box(new Box(highResult.getPos().add(-hit, -hit, -hit), highResult.getPos().add(hit, hit, hit)), DrawStyle.filled(0x8000ff00));
            }
        } else
        {
            if (DEBUG_RAYCAST)
            {
                GizmoDrawing.box(new Box(returnValue.getPos().add(-hit, -hit, -hit), returnValue.getPos().add(hit, hit, hit)), DrawStyle.filled(0x80ff00ff));
                GizmoDrawing.box(new Box(highResult.getPos().add(-miss, -miss, -miss), highResult.getPos().add(miss, miss, miss)), DrawStyle.filled(0x2000ff00));
            }
        }
    }

    private static BlockHitResult raycast(BlockView blockView, RaycastContext context)
    {
        return raycast(context.getStart(), context.getEnd(), context, (innerContext, pos) ->
        {
            BlockState blockState = blockView.getBlockState(pos);
            FluidState fluidState = blockView.getFluidState(pos);
            Vec3d vec3d = innerContext.getStart();
            Vec3d vec3d2 = innerContext.getEnd();
            VoxelShape voxelShape = innerContext.getBlockShape(blockState, blockView, pos);
            double yOff = SlabSupport.getYOffset(blockView, pos, blockState);
            if (yOff != 0.0) {
                voxelShape = voxelShape.offset(0, -yOff, 0);
            }
            BlockHitResult blockHitResult = blockView.raycastBlock(vec3d, vec3d2, pos, voxelShape, blockState);
            VoxelShape voxelShape2 = innerContext.getFluidShape(fluidState, blockView, pos);
            BlockHitResult blockHitResult2 = voxelShape2.raycast(vec3d, vec3d2, pos);
            double d = blockHitResult == null ? Double.MAX_VALUE : innerContext.getStart().squaredDistanceTo(blockHitResult.getPos());
            double e = blockHitResult2 == null ? Double.MAX_VALUE : innerContext.getStart().squaredDistanceTo(blockHitResult2.getPos());
            return d <= e ? blockHitResult : blockHitResult2;
        }, innerContext ->
        {
            Vec3d vec3d = innerContext.getStart().subtract(innerContext.getEnd());
            return BlockHitResult.createMissed(innerContext.getEnd(), Direction.getFacing(vec3d.x, vec3d.y, vec3d.z), BlockPos.ofFloored(innerContext.getEnd()));
        });
    }

    private static <T, C> T raycast(Vec3d start, Vec3d end, C context, BiFunction<C, BlockPos, T> blockHitFactory, Function<C, T> missFactory)
    {
        if (start.equals(end))
        {
            return missFactory.apply(context);
        } else
        {
            double d = MathHelper.lerp(-1.0E-7, end.x, start.x);
            double e = MathHelper.lerp(-1.0E-7, end.y, start.y);
            double f = MathHelper.lerp(-1.0E-7, end.z, start.z);
            double g = MathHelper.lerp(-1.0E-7, start.x, end.x);
            double h = MathHelper.lerp(-1.0E-7, start.y, end.y);
            double i = MathHelper.lerp(-1.0E-7, start.z, end.z);
            int j = MathHelper.floor(g);
            int k = MathHelper.floor(h);
            int l = MathHelper.floor(i);
            BlockPos.Mutable mutable = new BlockPos.Mutable(j, k, l);
            T object = (T) blockHitFactory.apply(context, mutable);
            if (object != null)
            {
                return object;
            } else
            {
                double m = d - g;
                double n = e - h;
                double o = f - i;
                int p = MathHelper.sign(m);
                int q = MathHelper.sign(n);
                int r = MathHelper.sign(o);
                double s = p == 0 ? Double.MAX_VALUE : p / m;
                double t = q == 0 ? Double.MAX_VALUE : q / n;
                double u = r == 0 ? Double.MAX_VALUE : r / o;
                double v = s * (p > 0 ? 1.0 - MathHelper.fractionalPart(g) : MathHelper.fractionalPart(g));
                double w = t * (q > 0 ? 1.0 - MathHelper.fractionalPart(h) : MathHelper.fractionalPart(h));
                double x = u * (r > 0 ? 1.0 - MathHelper.fractionalPart(i) : MathHelper.fractionalPart(i));

                while (v <= 1.0 || w <= 1.0 || x <= 1.0)
                {
                    if (v < w)
                    {
                        if (v < x)
                        {
                            j += p;
                            v += s;
                        } else
                        {
                            l += r;
                            x += u;
                        }
                    } else if (w < x)
                    {
                        k += q;
                        w += t;
                    } else
                    {
                        l += r;
                        x += u;
                    }

                    T object2 = blockHitFactory.apply(context, mutable.set(j, k, l));
                    if (object2 != null)
                    {
                        return object2;
                    }
                }

                return missFactory.apply(context);
            }
        }
    }
}