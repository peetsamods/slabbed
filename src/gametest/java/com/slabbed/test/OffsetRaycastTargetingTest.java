package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

/**
 * MC 1.21.1 port of the targeting-overhaul server gametests: proves
 * {@link SlabbedOffsetRaycast} decides the hit purely from eye + look against the real
 * (offset) outline geometry, across every offset case, and matches vanilla exactly on
 * non-offset blocks. These run on the 1.21.1 server harness ({@code runGameTest}) and
 * verify the util independently of the (not-yet-wired) client redirect.
 *
 * <p>The fence-gate and torch-via-own-shape tests from the 1.21.11 branch are omitted
 * here because they depend on the SlabSupportStateMixin changes that are part of the
 * full activation (see {@code GameRendererPickOffsetRaycastMixin} javadoc).
 */
public final class OffsetRaycastTargetingTest {

    private static final double EPS = 1.0e-6;

    private static BlockHitResult slabbed(ServerWorld world, Vec3d eye, Vec3d end) {
        return SlabbedOffsetRaycast.raycast(world, eye, end, ShapeContext.absent());
    }

    private static HitResult vanilla(ServerWorld world, Vec3d eye, Vec3d end) {
        return world.raycast(new RaycastContext(
                eye, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                ShapeContext.absent()));
    }

    private static Vec3d v(BlockPos origin, double dx, double dy, double dz) {
        return new Vec3d(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
    }

    private static BlockPos buildLoweredFullBlock(ServerWorld world, BlockPos slabPos) {
        world.setBlockState(slabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos fullPos = slabPos.up();
        world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        return fullPos;
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void loweredFullBlockTopFaceTargetsBlock(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos full = buildLoweredFullBlock(world, origin.add(3, 2, 3));
        ctx.assertTrue(SlabSupport.getYOffset(world, full, world.getBlockState(full)) == -0.5,
                "fixture: full block should be lowered -0.5");

        Vec3d eye = v(origin, 3.5, 6.0, 3.5);
        Vec3d end = v(origin, 3.5, 0.0, 3.5);
        BlockHitResult hit = slabbed(world, eye, end);
        ctx.assertTrue(hit.getType() == HitResult.Type.BLOCK, "expected a block hit");
        ctx.assertTrue(hit.getBlockPos().equals(full), "expected hit on lowered full block, got " + hit.getBlockPos());
        ctx.assertTrue(hit.getSide() == Direction.UP, "expected UP face, got " + hit.getSide());
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void loweredFullBlockMidHeightHorizontalRayTargetsBlock(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos full = buildLoweredFullBlock(world, origin.add(3, 2, 3));

        double y = full.getY() - 0.25;
        Vec3d eye = v(origin, 3.5, y - origin.getY(), 0.5);
        Vec3d end = v(origin, 3.5, y - origin.getY(), 7.5);

        BlockHitResult hit = slabbed(world, eye, end);
        ctx.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(full),
                "offset-aware raycast should target the lowered full block, got " + hit.getType() + " " + hit.getBlockPos());
        ctx.assertTrue(hit.getSide() == Direction.NORTH, "expected NORTH face, got " + hit.getSide());

        HitResult van = vanilla(world, eye, end);
        boolean vanillaSawFull = van.getType() == HitResult.Type.BLOCK
                && ((BlockHitResult) van).getBlockPos().equals(full);
        ctx.assertTrue(!vanillaSawFull, "control: vanilla DDA should not see the lowered block at mid-height");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void nonOffsetBlockMatchesVanilla(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos p = origin.add(4, 3, 4);
        world.setBlockState(p, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.getYOffset(world, p, world.getBlockState(p)) == 0.0, "fixture: isolated block dy 0");

        Vec3d centre = v(origin, 4.5, 3.5, 4.5);
        Vec3d[] eyes = {
                v(origin, 4.5, 6.5, 4.5),
                v(origin, 4.5, 3.5, 1.0),
                v(origin, 1.0, 3.5, 4.5),
                v(origin, 1.0, 6.0, 1.0),
                v(origin, 7.5, 3.5, 7.5),
        };
        for (int i = 0; i < eyes.length; i++) {
            Vec3d eye = eyes[i];
            Vec3d end = centre.add(centre.subtract(eye).normalize().multiply(0.5));
            BlockHitResult mine = slabbed(world, eye, end);
            HitResult van = vanilla(world, eye, end);
            ctx.assertTrue(van.getType() == HitResult.Type.BLOCK, "ray " + i + " control: vanilla should hit");
            ctx.assertTrue(mine.getType() == HitResult.Type.BLOCK, "ray " + i + ": offset raycast should hit");
            BlockHitResult vanBlock = (BlockHitResult) van;
            ctx.assertTrue(mine.getBlockPos().equals(vanBlock.getBlockPos()), "ray " + i + ": pos mismatch");
            ctx.assertTrue(mine.getSide() == vanBlock.getSide(), "ray " + i + ": side mismatch");
            ctx.assertTrue(mine.getPos().squaredDistanceTo(vanBlock.getPos()) < EPS, "ray " + i + ": hit point mismatch");
        }
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void nearestVisualSurfaceWins(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos full = buildLoweredFullBlock(world, origin.add(3, 2, 5));
        BlockPos near = origin.add(3, 2, 2);
        world.setBlockState(near, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        double y = full.getY() - 0.25;
        Vec3d eye = v(origin, 3.5, y - origin.getY(), 0.5);
        Vec3d end = v(origin, 3.5, y - origin.getY(), 7.5);
        BlockHitResult hit = slabbed(world, eye, end);
        ctx.assertTrue(hit.getBlockPos().equals(near),
                "nearest block should win: expected " + near + ", got " + hit.getBlockPos());
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void loweredSideSlabTargeted(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos full = buildLoweredFullBlock(world, origin.add(3, 2, 3));
        BlockPos sideSlab = full.east();
        world.setBlockState(sideSlab,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        double slabDy = SlabSupport.getYOffset(world, sideSlab, world.getBlockState(sideSlab));
        ctx.assertTrue(slabDy == -0.5, "fixture: side slab beside lowered FB should inherit -0.5, got " + slabDy);

        double y = sideSlab.getY() - 0.25;
        Vec3d eye = v(origin, 7.5, y - origin.getY(), 3.5);
        Vec3d end = v(origin, 4.0, y - origin.getY(), 3.5);
        BlockHitResult hit = slabbed(world, eye, end);
        ctx.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(sideSlab),
                "offset-aware raycast should target the lowered side slab, got " + hit.getType() + " " + hit.getBlockPos());
        ctx.assertTrue(hit.getSide() == Direction.EAST, "expected EAST face, got " + hit.getSide());
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void compoundMinusOneOwnerTargeted(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos baseSlab = origin.add(3, 2, 3);
        BlockPos loweredFull = buildLoweredFullBlock(world, baseSlab);
        BlockPos sideSlab = loweredFull.east();
        world.setBlockState(sideSlab,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos compound = sideSlab.up();
        world.setBlockState(compound, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        double dy = SlabSupport.getYOffset(world, compound, world.getBlockState(compound));
        ctx.assertTrue(dy == -1.0, "fixture: full block above lowered side slab should be compound -1.0, got " + dy);

        double y = compound.getY() - 0.25;
        Vec3d eye = v(origin, 7.5, y - origin.getY(), sideSlab.getZ() + 0.5 - origin.getZ());
        Vec3d end = v(origin, sideSlab.getX() + 0.0 - origin.getX(), y - origin.getY(), sideSlab.getZ() + 0.5 - origin.getZ());
        BlockHitResult hit = slabbed(world, eye, end);
        ctx.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(compound),
                "±1 window should find the -1.0 compound owner from the cell below, got " + hit.getType() + " " + hit.getBlockPos());
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void ceilingPlusHalfOwnerTargeted(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos topSlab = origin.add(3, 4, 3);
        world.setBlockState(topSlab,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);
        BlockPos lantern = topSlab.down();
        world.setBlockState(lantern,
                Blocks.LANTERN.getDefaultState().with(net.minecraft.state.property.Properties.HANGING, true),
                Block.NOTIFY_LISTENERS);

        double dy = SlabSupport.getYOffset(world, lantern, world.getBlockState(lantern));
        ctx.assertTrue(dy == 0.5, "fixture: hanging lantern under top slab should be +0.5, got " + dy);

        VoxelShape outline = world.getBlockState(lantern).getOutlineShape(world, lantern, ShapeContext.absent());
        ctx.assertTrue(!outline.isEmpty(), "lantern outline non-empty");
        double midY = lantern.getY() + (outline.getBoundingBox().minY + outline.getBoundingBox().maxY) / 2.0;
        Vec3d eye = v(origin, 3.5, midY - origin.getY(), 0.5);
        Vec3d end = v(origin, 3.5, midY - origin.getY(), 7.5);
        BlockHitResult hit = slabbed(world, eye, end);
        ctx.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(lantern),
                "offset-aware raycast should target the +0.5 hanging lantern, got " + hit.getType() + " " + hit.getBlockPos());
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void doubleSlabAndStairsMatchVanilla(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos dbl = origin.add(2, 3, 2);
        world.setBlockState(dbl,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE),
                Block.NOTIFY_LISTENERS);
        BlockPos stair = origin.add(5, 3, 5);
        world.setBlockState(stair, Blocks.STONE_STAIRS.getDefaultState(), Block.NOTIFY_LISTENERS);

        BlockPos[] targets = { dbl, stair };
        for (BlockPos t : targets) {
            ctx.assertTrue(SlabSupport.getYOffset(world, t, world.getBlockState(t)) == 0.0, "fixture: " + t + " non-offset");
            Vec3d centre = v(origin, t.getX() + 0.5 - origin.getX(), t.getY() + 0.5 - origin.getY(), t.getZ() + 0.5 - origin.getZ());
            Vec3d[] eyes = {
                    centre.add(0, 3.0, 0),
                    centre.add(0, 0, -3.0),
                    centre.add(-3.0, 0, 0),
                    centre.add(2.5, 1.5, 2.5),
            };
            for (int i = 0; i < eyes.length; i++) {
                Vec3d eye = eyes[i];
                Vec3d end = centre.add(centre.subtract(eye).normalize().multiply(0.5));
                BlockHitResult mine = slabbed(world, eye, end);
                HitResult van = vanilla(world, eye, end);
                if (van.getType() != HitResult.Type.BLOCK) {
                    ctx.assertTrue(mine.getType() != HitResult.Type.BLOCK || mine.getBlockPos().equals(t),
                            t + " ray " + i + ": unexpected hit " + mine.getBlockPos());
                    continue;
                }
                BlockHitResult vanBlock = (BlockHitResult) van;
                ctx.assertTrue(mine.getType() == HitResult.Type.BLOCK
                                && mine.getBlockPos().equals(vanBlock.getBlockPos())
                                && mine.getSide() == vanBlock.getSide()
                                && mine.getPos().squaredDistanceTo(vanBlock.getPos()) < EPS,
                        t + " ray " + i + ": parity mismatch");
            }
        }
        ctx.complete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 9. BLOCKER GUARD: a render-zeroed connection block must NOT have its outline
    //    offset (else the authoritative raycast targets a phantom shape below the
    //    rendered block). Ported from the 1.21.11 overhaul (commit 39a345e7) and
    //    adapted to 1.21.1: here a glass PANE is the render-zeroed connection block
    //    (OffsetBlockStateModel zeroes its dy because it is NOT an
    //    isBeta35FenceWallVariantContactObject). Fences/walls on 1.21.1 ARE contact
    //    objects and DO lower flush — that consistent case is covered by the matrix.
    // ──────────────────────────────────────────────────────────────────────────
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void connectionBlockOutlineNotOffset(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos slab = origin.add(3, 2, 3);
        world.setBlockState(slab,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos pane = slab.up();
        world.setBlockState(pane, Blocks.GLASS_PANE.getDefaultState(), Block.NOTIFY_LISTENERS);

        // Fixture: the pane must be an offset-eligible (dy<0) yet render-zeroed
        // connection block, so the gate's bail is genuinely exercised.
        double dy = SlabSupport.getYOffset(world, pane, world.getBlockState(pane));
        ctx.assertTrue(dy < 0.0,
                "fixture: glass pane on a bottom slab should be offset-eligible (dy<0), got " + dy);
        ctx.assertFalse(SlabSupport.isBeta35FenceWallVariantContactObject(world.getBlockState(pane)),
                "fixture: a glass pane must be a render-zeroed (non-contact) connection block");

        VoxelShape outline = world.getBlockState(pane).getOutlineShape(world, pane, ShapeContext.absent());
        ctx.assertFalse(outline.isEmpty(), "pane outline should be non-empty");
        double minY = outline.getBoundingBox().minY;
        ctx.assertTrue(minY >= -1.0e-6,
                "render-zeroed pane-on-slab outline must NOT be offset below 0 (got minY=" + minY
                        + "); it renders un-lowered, so the raycast must match");
        ctx.complete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 10. TORCH via its OWN offset shape (proves the slab-side comfort union is not
    //     needed once the nearest-hit raycast is authoritative). Ported from 1.21.11.
    // ──────────────────────────────────────────────────────────────────────────
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void loweredFloorTorchTargetedViaOwnShape(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos slab = origin.add(3, 2, 3);
        world.setBlockState(slab,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos torch = slab.up();
        world.setBlockState(torch, Blocks.TORCH.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(world.getBlockState(torch).isOf(Blocks.TORCH), "fixture: torch must survive on slab top");

        double dy = SlabSupport.getYOffset(world, torch, world.getBlockState(torch));
        ctx.assertTrue(dy == -0.5, "fixture: floor torch on slab should be lowered -0.5, got " + dy);

        // Aim horizontally at the torch column at the lowered comfort-post mid-height.
        double y = torch.getY() - 0.25;
        Vec3d eye = v(origin, 3.5, y - origin.getY(), 0.5);
        Vec3d end = v(origin, 3.5, y - origin.getY(), 7.5);
        BlockHitResult hit = slabbed(world, eye, end);
        ctx.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(torch),
                "offset-aware raycast should target the lowered floor torch " + torch
                        + ", got " + hit.getType() + " " + hit.getBlockPos());
        ctx.complete();
    }
}
