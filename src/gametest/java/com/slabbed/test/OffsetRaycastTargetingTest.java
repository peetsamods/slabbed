package com.slabbed.test;

import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

/**
 * Server GameTests proving that Slabbed targeting is decided purely by eye position +
 * look vector against the real (visually offset) geometry — the core of the targeting
 * overhaul.
 *
 * <p>These tests call {@link SlabbedOffsetRaycast#raycast} directly on the test
 * {@link ServerWorld} (a {@code BlockView} whose outline shapes are dy-offset by the
 * common {@code SlabSupportStateMixin}), so the targeting geometry is verified with no
 * rendered client and no rescue heuristics. Each test that asserts the fix also asserts
 * the matching <em>vanilla</em> {@code world.raycast} result to document the exact
 * divergence the offset-aware raycast corrects.
 */
public final class OffsetRaycastTargetingTest {

    private static final double EPS = 1.0e-6;

    /** Offset-aware nearest-hit raycast (the system under test). */
    private static BlockHitResult slabbed(ServerWorld world, Vec3d eye, Vec3d end) {
        return SlabbedOffsetRaycast.raycast(world, eye, end, ShapeContext.absent());
    }

    /** Stock vanilla outline raycast (no fluids), exactly as the pick path would do it. */
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

    /**
     * Build a lowered full block: a bottom slab with a solid full block directly on top.
     * The full block resolves to dy=-0.5 via live {@link SlabSupport#getYOffset}
     * (FB-on-bottom-slab), so its outline spans world Y [F.y-0.5, F.y+0.5].
     * Returns the full-block position.
     */
    private static BlockPos buildLoweredFullBlock(ServerWorld world, BlockPos slabPos) {
        world.setBlockState(slabPos,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos fullPos = slabPos.up();
        world.setBlockState(fullPos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        return fullPos;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Sanity: aiming straight down at a lowered block's visual top hits it.
    //    (Vanilla already gets this right — the visual top lies inside the logical
    //    cell — so it doubles as a parity check.)
    // ──────────────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredFullBlockTopFaceTargetsBlock(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos full = buildLoweredFullBlock(world, origin.add(3, 2, 3));

        ctx.assertTrue(SlabSupport.getYOffset(world, full, world.getBlockState(full)) == -0.5,
                "fixture invalid: full block should be lowered -0.5");

        // Aim straight down at the visual top centre (world Y = full.y + 0.5).
        Vec3d eye = v(origin, 3.5, 6.0, 3.5);
        Vec3d end = v(origin, 3.5, 0.0, 3.5);
        BlockHitResult hit = slabbed(world, eye, end);

        ctx.assertTrue(hit.getType() == HitResult.Type.BLOCK, "expected a block hit");
        ctx.assertTrue(hit.getBlockPos().equals(full),
                "expected hit on lowered full block " + full + ", got " + hit.getBlockPos());
        ctx.assertTrue(hit.getSide() == Direction.UP, "expected UP face, got " + hit.getSide());
        ctx.assertTrue(Math.abs(hit.getPos().y - (full.getY() + 0.5)) < 1.0e-4,
                "expected hit Y at visual top " + (full.getY() + 0.5) + ", got " + hit.getPos().y);
        ctx.complete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. THE BUG, FIXED: a near-horizontal ray at the lowered block's visual
    //    mid-height never enters the block's logical cell, so vanilla's voxel DDA
    //    cannot see it (returns MISS / the slab). The offset-aware raycast tests the
    //    up-neighbour and hits the block the player is actually looking at.
    // ──────────────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredFullBlockMidHeightHorizontalRayTargetsBlock(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos full = buildLoweredFullBlock(world, origin.add(3, 2, 3));

        // Horizontal ray at world Y = full.y - 0.25 (inside the lowered outline
        // [full.y-0.5, full.y+0.5], but in the cell layer of the slab BELOW).
        double y = full.getY() - 0.25;
        Vec3d eye = v(origin, 3.5, y - origin.getY(), 0.5);
        Vec3d end = v(origin, 3.5, y - origin.getY(), 7.5);

        BlockHitResult hit = slabbed(world, eye, end);
        ctx.assertTrue(hit.getType() == HitResult.Type.BLOCK,
                "offset-aware raycast should hit the lowered block, got " + hit.getType());
        ctx.assertTrue(hit.getBlockPos().equals(full),
                "offset-aware raycast should target the lowered full block " + full
                        + ", got " + hit.getBlockPos());
        ctx.assertTrue(hit.getSide() == Direction.NORTH,
                "expected NORTH face (player-facing), got " + hit.getSide());

        // Negative control: stock vanilla cannot see the block at this aim.
        HitResult van = vanilla(world, eye, end);
        boolean vanillaSawBlock = van.getType() == HitResult.Type.BLOCK
                && ((BlockHitResult) van).getBlockPos().equals(full);
        ctx.assertFalse(vanillaSawBlock,
                "control failed: vanilla DDA unexpectedly hit the lowered block — bug geometry invalid");
        ctx.complete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. NO REGRESSION: for ordinary (non-offset) blocks the offset-aware raycast
    //    returns exactly what vanilla returns, across several aim directions.
    // ──────────────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void nonOffsetBlockMatchesVanilla(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos p = origin.add(4, 3, 4); // isolated, air all around -> dy = 0
        world.setBlockState(p, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.getYOffset(world, p, world.getBlockState(p)) == 0.0,
                "fixture invalid: isolated block must have dy 0");

        Vec3d centre = v(origin, 4.5, 3.5, 4.5);
        Vec3d[] eyes = {
                v(origin, 4.5, 6.5, 4.5),   // straight down -> UP face
                v(origin, 4.5, 3.5, 1.0),   // horizontal -> NORTH face
                v(origin, 1.0, 3.5, 4.5),   // horizontal -> WEST face
                v(origin, 1.0, 6.0, 1.0),   // diagonal from above
                v(origin, 7.5, 3.5, 7.5),   // horizontal from +X+Z corner
        };
        for (int i = 0; i < eyes.length; i++) {
            Vec3d eye = eyes[i];
            Vec3d end = centre.add(centre.subtract(eye).normalize().multiply(0.5));
            BlockHitResult mine = slabbed(world, eye, end);
            HitResult van = vanilla(world, eye, end);
            ctx.assertTrue(van.getType() == HitResult.Type.BLOCK,
                    "ray " + i + " control: vanilla should hit the block");
            ctx.assertTrue(mine.getType() == HitResult.Type.BLOCK,
                    "ray " + i + ": offset raycast should hit the block");
            BlockHitResult vanBlock = (BlockHitResult) van;
            ctx.assertTrue(mine.getBlockPos().equals(vanBlock.getBlockPos()),
                    "ray " + i + ": pos mismatch mine=" + mine.getBlockPos() + " vanilla=" + vanBlock.getBlockPos());
            ctx.assertTrue(mine.getSide() == vanBlock.getSide(),
                    "ray " + i + ": side mismatch mine=" + mine.getSide() + " vanilla=" + vanBlock.getSide());
            ctx.assertTrue(mine.getPos().squaredDistanceTo(vanBlock.getPos()) < EPS,
                    "ray " + i + ": hit point mismatch mine=" + mine.getPos() + " vanilla=" + vanBlock.getPos());
        }
        ctx.complete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. NEAREST WINS: a plain block in front of a lowered block must be selected —
    //    the offset-aware raycast must not over-eagerly grab the offset block.
    // ──────────────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void nearestVisualSurfaceWins(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos full = buildLoweredFullBlock(world, origin.add(3, 2, 5));   // lowered block, far (+Z)
        BlockPos near = origin.add(3, 2, 2);                                  // plain block, near (-Z), dy 0
        world.setBlockState(near, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        // Same mid-height horizontal ray as test 2; the near block intersects first.
        double y = full.getY() - 0.25; // 0.75 within the near block's [y,y+1]
        Vec3d eye = v(origin, 3.5, y - origin.getY(), 0.5);
        Vec3d end = v(origin, 3.5, y - origin.getY(), 7.5);

        BlockHitResult hit = slabbed(world, eye, end);
        ctx.assertTrue(hit.getBlockPos().equals(near),
                "nearest block should win: expected " + near + ", got " + hit.getBlockPos());
        ctx.complete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. LOWERED SIDE SLAB (the handoff's perpendicular case): a bottom slab beside a
    //    lowered full block inherits dy=-0.5; aiming at its visual body targets it,
    //    where vanilla again cannot at mid-height.
    // ──────────────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredSideSlabTargeted(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos full = buildLoweredFullBlock(world, origin.add(3, 2, 3));
        BlockPos sideSlab = full.east(); // (4, full.y, 3)
        world.setBlockState(sideSlab,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);

        double slabDy = SlabSupport.getYOffset(world, sideSlab, world.getBlockState(sideSlab));
        ctx.assertTrue(slabDy == -0.5,
                "fixture invalid: side slab beside lowered FB should inherit dy=-0.5, got " + slabDy);

        // Lowered bottom slab outline spans world Y [slab.y-0.5, slab.y]; aim at its
        // east face at mid-height (slab.y - 0.25), a layer vanilla DDA skips.
        double y = sideSlab.getY() - 0.25;
        Vec3d eye = v(origin, 7.5, y - origin.getY(), 3.5);
        Vec3d end = v(origin, 4.0, y - origin.getY(), 3.5);

        BlockHitResult hit = slabbed(world, eye, end);
        ctx.assertTrue(hit.getType() == HitResult.Type.BLOCK
                        && hit.getBlockPos().equals(sideSlab),
                "offset-aware raycast should target the lowered side slab " + sideSlab
                        + ", got " + hit.getType() + " " + hit.getBlockPos());
        ctx.assertTrue(hit.getSide() == Direction.EAST,
                "expected EAST face, got " + hit.getSide());

        HitResult van = vanilla(world, eye, end);
        boolean vanillaSawSlab = van.getType() == HitResult.Type.BLOCK
                && ((BlockHitResult) van).getBlockPos().equals(sideSlab);
        ctx.assertFalse(vanillaSawSlab,
                "control failed: vanilla unexpectedly hit the lowered side slab at mid-height");
        ctx.complete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. BLOCKER GUARD: a fence on a bottom slab renders un-lowered, so its outline
    //    must NOT be offset (else the authoritative raycast targets a phantom 0.5
    //    below the rendered fence).
    // ──────────────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void connectionBlockOutlineNotOffset(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos slab = origin.add(3, 2, 3);
        world.setBlockState(slab,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos fence = slab.up();
        world.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_LISTENERS);

        VoxelShape outline = world.getBlockState(fence).getOutlineShape(world, fence, ShapeContext.absent());
        ctx.assertFalse(outline.isEmpty(), "fence outline should be non-empty");
        double minY = outline.getBoundingBox().minY;
        ctx.assertTrue(minY >= -1.0e-6,
                "fence-on-slab outline must NOT be offset below 0 (got minY=" + minY
                        + "); it renders un-lowered, so the raycast must match");
        ctx.complete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7. TORCH via its OWN offset shape (proves the slab-side comfort union is not
    //    needed once the nearest-hit raycast is authoritative).
    // ──────────────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
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

    // ──────────────────────────────────────────────────────────────────────────
    // 8. -1.0 COMPOUND owner (±1 window lower extreme): a full block on a lowered
    //    side slab renders at [P.y-1.0, P.y], entirely in the cell below; the window
    //    must still find it.
    // ──────────────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void compoundMinusOneOwnerTargeted(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos baseSlab = origin.add(3, 2, 3);
        BlockPos loweredFull = buildLoweredFullBlock(world, baseSlab); // dy -0.5 at baseSlab.up()
        BlockPos sideSlab = loweredFull.east();                       // adjacent -> dy -0.5
        world.setBlockState(sideSlab,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos compound = sideSlab.up();                            // full block above lowered side slab
        world.setBlockState(compound, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        double dy = SlabSupport.getYOffset(world, compound, world.getBlockState(compound));
        ctx.assertTrue(dy == -1.0,
                "fixture: full block above lowered side slab should be compound dy=-1.0, got " + dy);

        // Compound outline spans [compound.y-1.0, compound.y]; aim at its upper region
        // (compound.y-0.25), which lies in the cell BELOW compound's logical cell.
        double y = compound.getY() - 0.25;
        Vec3d eye = v(origin, 7.5, y - origin.getY(), sideSlab.getZ() + 0.5 - origin.getZ());
        Vec3d end = v(origin, sideSlab.getX() + 0.0 - origin.getX(), y - origin.getY(), sideSlab.getZ() + 0.5 - origin.getZ());
        BlockHitResult hit = slabbed(world, eye, end);
        ctx.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(compound),
                "±1 window should find the -1.0 compound owner " + compound
                        + " from the cell below, got " + hit.getType() + " " + hit.getBlockPos());

        HitResult van = vanilla(world, eye, end);
        boolean vanillaSawCompound = van.getType() == HitResult.Type.BLOCK
                && ((BlockHitResult) van).getBlockPos().equals(compound);
        ctx.assertFalse(vanillaSawCompound,
                "control: vanilla should not see the compound owner at this sub-cell aim");
        ctx.complete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 9. +0.5 CEILING owner (±1 window upper extreme): a hanging lantern under a top
    //    slab floats up +0.5; aim derived from its actual offset outline so the test
    //    is robust to the lantern's exact shape.
    // ──────────────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
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
        ctx.assertTrue(dy == 0.5,
                "fixture: hanging lantern under top slab should be +0.5, got " + dy);

        // Aim horizontally at the centre of the lantern's actual offset outline.
        VoxelShape outline = world.getBlockState(lantern).getOutlineShape(world, lantern, ShapeContext.absent());
        ctx.assertFalse(outline.isEmpty(), "lantern outline non-empty");
        double midY = lantern.getY() + (outline.getBoundingBox().minY + outline.getBoundingBox().maxY) / 2.0;
        Vec3d eye = v(origin, 3.5, midY - origin.getY(), 0.5);
        Vec3d end = v(origin, 3.5, midY - origin.getY(), 7.5);
        BlockHitResult hit = slabbed(world, eye, end);
        ctx.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(lantern),
                "offset-aware raycast should target the +0.5 hanging lantern " + lantern
                        + ", got " + hit.getType() + " " + hit.getBlockPos());
        ctx.complete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 11. LOWERING COVERAGE: an ordinary solid full block (grass) must lower onto a
    //     Terrain Slabs custom BOTTOM_LIKE surface, just like a log does — the player
    //     reported grass not lowering while stripped logs did. Requires Terrain Slabs
    //     loaded (it is, via run/mods + modLocalRuntime).
    // ──────────────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void grassBlockLowersOnTerrainSlabsSurface(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        Block tsGrassSlab = Registries.BLOCK.get(Identifier.of("terrainslabs", "grass_slab"));
        ctx.assertTrue(tsGrassSlab != Blocks.AIR,
                "fixture: terrainslabs:grass_slab must be registered (Terrain Slabs loaded)");

        BlockPos slab = origin.add(3, 2, 3);
        var slabState = tsGrassSlab.getDefaultState();
        if (slabState.contains(SlabBlock.TYPE)) {
            slabState = slabState.with(SlabBlock.TYPE, SlabType.BOTTOM);
        }
        world.setBlockState(slab, slabState, Block.NOTIFY_LISTENERS);
        ctx.assertTrue(
                CompatHooks.customSlabSurfaceKind(world.getBlockState(slab)) == CompatSlabSurfaceKind.BOTTOM_LIKE,
                "fixture: TS grass slab should classify BOTTOM_LIKE");

        // A stripped log (already worked) and a grass block (the bug) — both must lower.
        BlockPos log = slab.up();
        world.setBlockState(log, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState(), Block.NOTIFY_LISTENERS);
        double logDy = SlabSupport.getYOffset(world, log, world.getBlockState(log));
        ctx.assertTrue(logDy == -0.5, "control: stripped log on TS surface should lower -0.5, got " + logDy);

        BlockPos grassSlab2 = origin.add(5, 2, 3);
        world.setBlockState(grassSlab2, slabState, Block.NOTIFY_LISTENERS);
        BlockPos grass = grassSlab2.up();
        world.setBlockState(grass, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
        double grassDy = SlabSupport.getYOffset(world, grass, world.getBlockState(grass));
        ctx.assertTrue(grassDy == -0.5,
                "grass block on Terrain Slabs surface should lower -0.5 (was the bug), got " + grassDy);
        ctx.complete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 10. PARITY on more non-offset geometry: double slab (full cube) and stairs
    //     (non-empty getRaycastShape) must match vanilla field-by-field.
    // ──────────────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
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
            ctx.assertTrue(SlabSupport.getYOffset(world, t, world.getBlockState(t)) == 0.0,
                    "fixture: " + t + " must be non-offset");
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
                    // If vanilla misses (grazing past a non-cube shape), only require the
                    // offset raycaster to also not invent a different block.
                    ctx.assertTrue(mine.getType() != HitResult.Type.BLOCK || mine.getBlockPos().equals(t),
                            t + " ray " + i + ": unexpected hit " + mine.getBlockPos());
                    continue;
                }
                BlockHitResult vanBlock = (BlockHitResult) van;
                ctx.assertTrue(mine.getType() == HitResult.Type.BLOCK
                                && mine.getBlockPos().equals(vanBlock.getBlockPos())
                                && mine.getSide() == vanBlock.getSide()
                                && mine.getPos().squaredDistanceTo(vanBlock.getPos()) < EPS,
                        t + " ray " + i + ": parity mismatch mine=(" + mine.getBlockPos() + "," + mine.getSide()
                                + ") vanilla=(" + vanBlock.getBlockPos() + "," + vanBlock.getSide() + ")");
            }
        }
        ctx.complete();
    }
}
