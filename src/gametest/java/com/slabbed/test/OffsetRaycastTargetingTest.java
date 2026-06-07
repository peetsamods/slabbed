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

    // A standing lantern on a vanilla BOTTOM slab on a Terrain Slabs slab (a "mixed slab")
    // must lower the FULL -1.0 to sit flush: the oak slab itself drops -0.5 to cap the
    // half-height Terrain Slabs surface, so its visual top sits a half block lower than a
    // normal bottom slab, and the lantern must follow that -0.5 in ADDITION to its own
    // -0.5 sit-on-bottom-slab drop. A flat -0.5 (the previous, incorrect expectation —
    // this test had asserted the bug) leaves the lantern floating half a block above the
    // mixed slab's lowered top.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lanternOnTerrainSlabsLowers(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        Block ts = Registries.BLOCK.get(Identifier.of("terrainslabs", "grass_slab"));
        ctx.assertTrue(ts != Blocks.AIR, "fixture: Terrain Slabs loaded");

        BlockPos tsPos = origin.add(3, 2, 3);
        var tss = ts.getDefaultState();
        if (tss.contains(SlabBlock.TYPE)) {
            tss = tss.with(SlabBlock.TYPE, SlabType.BOTTOM);
        }
        world.setBlockState(tsPos, tss, Block.NOTIFY_LISTENERS);
        BlockPos sPos = tsPos.up();
        world.setBlockState(sPos, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        BlockPos lPos = sPos.up();
        world.setBlockState(lPos, Blocks.LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);

        ctx.assertTrue(SlabSupport.getYOffset(world, sPos, world.getBlockState(sPos)) == -0.5,
                "vanilla bottom slab on Terrain Slabs should lower -0.5");
        double lDy = SlabSupport.getYOffset(world, lPos, world.getBlockState(lPos));
        ctx.assertTrue(lDy == -1.0,
                "lantern on a vanilla BOTTOM slab on Terrain Slabs (mixed slab) must lower -1.0 to sit "
                        + "flush (slab's own -0.5 + sit -0.5), was floating at " + lDy);
        ctx.complete();
    }

    // A full BLOCK (e.g. crafting table) placed on a mixed slab must lower -1.0 AND STAY there
    // — both via the live path (first client frame, no anchor yet) and via its persisted
    // placement anchor. Before the fix the live path gave the correct -1.0 but the direct
    // anchor pinned a flat -0.5 the moment it synced, so the block "briefly lowered then popped
    // up" half a block (the reported crafting-table-on-mixed-slab pop). Both states must match.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockOnMixedSlabLowersAndStays(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        Block ts = Registries.BLOCK.get(Identifier.of("terrainslabs", "grass_slab"));
        ctx.assertTrue(ts != Blocks.AIR, "fixture: Terrain Slabs loaded");

        BlockPos tsPos = origin.add(3, 2, 3);
        var tss = ts.getDefaultState();
        if (tss.contains(SlabBlock.TYPE)) {
            tss = tss.with(SlabBlock.TYPE, SlabType.BOTTOM);
        }
        world.setBlockState(tsPos, tss, Block.NOTIFY_LISTENERS);
        BlockPos sPos = tsPos.up();
        world.setBlockState(sPos, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        BlockPos fbPos = sPos.up();
        world.setBlockState(fbPos, Blocks.CRAFTING_TABLE.getDefaultState(), Block.NOTIFY_LISTENERS);

        ctx.assertTrue(SlabSupport.getYOffset(world, sPos, world.getBlockState(sPos)) == -0.5,
                "fixture: vanilla bottom slab on Terrain Slabs should lower -0.5");
        // Live path (no anchor yet — the transient first-frame value).
        double liveDy = SlabSupport.getYOffset(world, fbPos, world.getBlockState(fbPos));
        ctx.assertTrue(liveDy == -1.0,
                "full block on a mixed slab must lower -1.0 on the live path, got " + liveDy);
        // Persisted anchor (the steady-state value after placement syncs) must MATCH — no pop.
        com.slabbed.anchor.SlabAnchorAttachment.addAnchor(world, fbPos, world.getBlockState(fbPos));
        ctx.assertTrue(com.slabbed.anchor.SlabAnchorAttachment.isAnchored(world, fbPos),
                "fixture: full block on a bottom slab must anchor on placement");
        double anchoredDy = SlabSupport.getYOffset(world, fbPos, world.getBlockState(fbPos));
        ctx.assertTrue(anchoredDy == -1.0,
                "anchored full block on a mixed slab must STAY at -1.0 (no pop up to -0.5), got " + anchoredDy);
        ctx.complete();
    }

    // A vanilla TOP slab placed on a Terrain Slabs bottom surface must lower the FULL -1.0, not
    // -0.5: a top slab caps from the UPPER half of its own block, so to sit flush on the
    // half-height terrain it needs the full block drop. A flat -0.5 leaves a half-block gap
    // underneath (the combined-slab matrix's one genuine vanilla bug, case 5a).
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaTopSlabOnTerrainLowersFull(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        Block ts = Registries.BLOCK.get(Identifier.of("terrainslabs", "grass_slab"));
        ctx.assertTrue(ts != Blocks.AIR, "fixture: Terrain Slabs loaded");

        BlockPos tsPos = origin.add(3, 2, 3);
        var tss = ts.getDefaultState();
        if (tss.contains(SlabBlock.TYPE)) {
            tss = tss.with(SlabBlock.TYPE, SlabType.BOTTOM);
        }
        world.setBlockState(tsPos, tss, Block.NOTIFY_LISTENERS);
        BlockPos topPos = tsPos.up();
        world.setBlockState(topPos, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP), Block.NOTIFY_LISTENERS);

        double dy = SlabSupport.getYOffset(world, topPos, world.getBlockState(topPos));
        ctx.assertTrue(dy == -1.0,
                "vanilla TOP slab on a Terrain Slabs bottom must lower -1.0 to sit flush (was a "
                        + "half-block gap at -0.5), got " + dy);
        ctx.complete();
    }

    // A vanilla BOTTOM slab stacked on a mixed slab (vanilla bottom slab on terrain) must chain:
    // lower -1.0 to sit flush on the mixed slab's lowered top, not float at -0.5. The compound is
    // vanilla-only (terrain slabs are skip-offset). Deeper stacks settle at the -1.0 raycast cap.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaSlabStackedOnMixedSlabChains(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        Block ts = Registries.BLOCK.get(Identifier.of("terrainslabs", "grass_slab"));
        ctx.assertTrue(ts != Blocks.AIR, "fixture: Terrain Slabs loaded");

        BlockPos tsPos = origin.add(3, 2, 3);
        var tss = ts.getDefaultState();
        if (tss.contains(SlabBlock.TYPE)) {
            tss = tss.with(SlabBlock.TYPE, SlabType.BOTTOM);
        }
        world.setBlockState(tsPos, tss, Block.NOTIFY_LISTENERS);
        BlockPos l1 = tsPos.up(); // vanilla bottom slab capping terrain -> the mixed slab (-0.5)
        world.setBlockState(l1, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        BlockPos l2 = l1.up();    // vanilla bottom slab stacked on the mixed slab -> must chain to -1.0
        world.setBlockState(l2, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);

        ctx.assertTrue(SlabSupport.getYOffset(world, l1, world.getBlockState(l1)) == -0.5,
                "fixture: mixed slab (vanilla bottom on terrain) is -0.5");
        double l2Dy = SlabSupport.getYOffset(world, l2, world.getBlockState(l2));
        ctx.assertTrue(l2Dy == -1.0,
                "vanilla slab stacked on a mixed slab must chain to -1.0 (was floating at -0.5), got " + l2Dy);
        ctx.complete();
    }

    // An object on a CANTILEVERED lowered support (a block beside a lowered block, air below
    // it) must lower to match — otherwise it floats above its lowered support (the reported
    // floating-lantern bug, since the support-column walk stops at the air gap).
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lanternOnCantileveredSupportLowers(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos slab = origin.add(3, 2, 3);
        world.setBlockState(slab, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        BlockPos fb = slab.up(); // lowered full block (stone on bottom slab)
        world.setBlockState(fb, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos cantilever = fb.south(); // beside the lowered FB, air below it
        world.setBlockState(cantilever, Blocks.OAK_PLANKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos lantern = cantilever.up();
        world.setBlockState(lantern, Blocks.LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);

        ctx.assertTrue(SlabSupport.getYOffset(world, cantilever, world.getBlockState(cantilever)) == -0.5,
                "fixture: cantilevered support should be lowered -0.5");
        double lDy = SlabSupport.getYOffset(world, lantern, world.getBlockState(lantern));
        ctx.assertTrue(lDy == -0.5,
                "lantern on a cantilevered lowered support must lower to match (was floating at 0), got " + lDy);
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

    // ──────────────────────────────────────────────────────────────────────────
    // REGRESSION (TS+VS float fix): a non-solid object (lantern / chain) standing on
    // a vanilla TOP or DOUBLE slab that is itself rendered lowered must follow that
    // slab down to its lowered top face instead of floating a full step above it.
    // This was the reported "TS + VS + object floats" bug: a vanilla TOP/DOUBLE slab
    // on a lowered column (Terrain-Slabs surface or full-block-on-bottom-slab) lowers,
    // but the object on top stayed at dy 0.0 (hasSlabInColumn terminated at the
    // non-bottom slab without recognising it as a lowered support).
    // ──────────────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void objectOnLoweredTopOrDoubleSlabFollows(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        // Helper stacks live in a tight footprint near origin to avoid overlapping
        // neighbouring test structures in the shared gametest world.

        // 1) Lantern on a vanilla TOP slab that sits on a lowered full block.
        BlockPos a0 = origin.add(1, 2, 1);
        world.setBlockState(a0, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        BlockPos aFb = a0.up();
        world.setBlockState(aFb, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos aTop = aFb.up();
        world.setBlockState(aTop, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP), Block.NOTIFY_LISTENERS);
        BlockPos aLant = aTop.up();
        world.setBlockState(aLant, Blocks.LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.getYOffset(world, aTop, world.getBlockState(aTop)) == -0.5,
                "fixture: TOP slab on a lowered full block must render lowered -0.5");
        double aLantDy = SlabSupport.getYOffset(world, aLant, world.getBlockState(aLant));
        ctx.assertTrue(aLantDy == -0.5,
                "lantern on a lowered TOP slab must follow it down (was floating at 0.0), got " + aLantDy);

        // 2) Chain on the same lowered-TOP-slab arrangement (chains are excluded from the
        //    overhead hanger-follow path, so this exercises the sit-on-slab path directly).
        BlockPos b0 = origin.add(3, 2, 1);
        world.setBlockState(b0, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        BlockPos bFb = b0.up();
        world.setBlockState(bFb, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos bTop = bFb.up();
        world.setBlockState(bTop, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP), Block.NOTIFY_LISTENERS);
        BlockPos bChain = bTop.up();
        world.setBlockState(bChain, Blocks.IRON_CHAIN.getDefaultState(), Block.NOTIFY_LISTENERS);
        double bChainDy = SlabSupport.getYOffset(world, bChain, world.getBlockState(bChain));
        ctx.assertTrue(bChainDy == -0.5,
                "chain on a lowered TOP slab must follow it down (was floating at 0.0), got " + bChainDy);

        // 3) Lantern on a vanilla DOUBLE slab that sits on a lowered full block.
        BlockPos c0 = origin.add(5, 2, 1);
        world.setBlockState(c0, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        BlockPos cFb = c0.up();
        world.setBlockState(cFb, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos cDbl = cFb.up();
        world.setBlockState(cDbl, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE), Block.NOTIFY_LISTENERS);
        BlockPos cLant = cDbl.up();
        world.setBlockState(cLant, Blocks.LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.getYOffset(world, cDbl, world.getBlockState(cDbl)) == -0.5,
                "fixture: DOUBLE slab on a lowered full block must render lowered -0.5");
        double cLantDy = SlabSupport.getYOffset(world, cLant, world.getBlockState(cLant));
        ctx.assertTrue(cLantDy == -0.5,
                "lantern on a lowered DOUBLE slab must follow it down (was floating at 0.0), got " + cLantDy);

        // 4) Lantern on a vanilla TOP slab placed directly on a Terrain Slabs BOTTOM_LIKE
        //    surface (the exact TS+VS combo from the report).
        Block ts = Registries.BLOCK.get(Identifier.of("terrainslabs", "grass_slab"));
        ctx.assertTrue(ts != Blocks.AIR, "fixture: Terrain Slabs loaded");
        var tsBottom = ts.getDefaultState();
        if (tsBottom.contains(SlabBlock.TYPE)) {
            tsBottom = tsBottom.with(SlabBlock.TYPE, SlabType.BOTTOM);
        }
        BlockPos d0 = origin.add(7, 2, 1);
        world.setBlockState(d0, tsBottom, Block.NOTIFY_LISTENERS);
        BlockPos dTop = d0.up();
        world.setBlockState(dTop, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP), Block.NOTIFY_LISTENERS);
        BlockPos dLant = dTop.up();
        world.setBlockState(dLant, Blocks.LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
        double dLantDy = SlabSupport.getYOffset(world, dLant, world.getBlockState(dLant));
        ctx.assertTrue(dLantDy == -0.5,
                "lantern on a TOP slab on a Terrain Slabs surface must lower -0.5, got " + dLantDy);
        ctx.complete();
    }

    // NO REGRESSION: a non-solid object on a NON-lowered TOP/DOUBLE slab (a normal floor)
    // must NOT lower — the fix only applies when the slab support is itself rendered lowered.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void objectOnNormalTopOrDoubleSlabDoesNotLower(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        // TOP slab on solid ground (not lowered) -> lantern stays at 0.0.
        BlockPos a0 = origin.add(2, 2, 2);
        world.setBlockState(a0, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos aTop = a0.up();
        world.setBlockState(aTop, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP), Block.NOTIFY_LISTENERS);
        BlockPos aLant = aTop.up();
        world.setBlockState(aLant, Blocks.LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.getYOffset(world, aTop, world.getBlockState(aTop)) == 0.0,
                "fixture: TOP slab on solid ground must be non-lowered");
        double aLantDy = SlabSupport.getYOffset(world, aLant, world.getBlockState(aLant));
        ctx.assertTrue(aLantDy == 0.0,
                "lantern on a normal (non-lowered) TOP slab must stay at 0.0, got " + aLantDy);

        // DOUBLE slab on solid ground (not lowered) -> lantern stays at 0.0.
        BlockPos b0 = origin.add(5, 2, 2);
        world.setBlockState(b0, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos bDbl = b0.up();
        world.setBlockState(bDbl, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE), Block.NOTIFY_LISTENERS);
        BlockPos bLant = bDbl.up();
        world.setBlockState(bLant, Blocks.LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.getYOffset(world, bDbl, world.getBlockState(bDbl)) == 0.0,
                "fixture: DOUBLE slab on solid ground must be non-lowered");
        double bLantDy = SlabSupport.getYOffset(world, bLant, world.getBlockState(bLant));
        ctx.assertTrue(bLantDy == 0.0,
                "lantern on a normal (non-lowered) DOUBLE slab must stay at 0.0, got " + bLantDy);
        ctx.complete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Still-floating-lantern regression (from the 2026-06-07 video): a standing
    // lantern sitting directly ON TOP of a FULL BLOCK (grass/dirt/planks) that is
    // itself lowered must inherit the support's -0.5 and sit flush — even when the
    // lantern's own shouldOffset column walk cannot reach the lowering source
    // (support lowered via a persisted ANCHOR with a full-height solid block below
    // it, so there is NO slab anywhere in the lantern's column). Before the fix the
    // lantern stayed at dy=0 and floated above the lowered support.
    // ──────────────────────────────────────────────────────────────────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lanternOnAnchorLoweredFullBlockSolidBelowLowers(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        // Build the video shape: lowered FB (stone on a bottom slab) with a grass block
        // beside it sitting on a SOLID full-height plank, anchored on placement via the
        // adjacent-lowered-FB rule. Then remove the lowered FB + its slab so the grass keeps
        // only its persisted anchor — exactly the "grass on planks, no slab in column" shape.
        BlockPos slab = origin.add(3, 2, 3);
        world.setBlockState(slab, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_LISTENERS);
        BlockPos loweredFb = slab.up();
        world.setBlockState(loweredFb, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos planksBelow = loweredFb.south().down();
        world.setBlockState(planksBelow, Blocks.OAK_PLANKS.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos grass = loweredFb.south();
        world.setBlockState(grass, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
        com.slabbed.anchor.SlabAnchorAttachment.addAnchor(world, grass, world.getBlockState(grass));
        world.setBlockState(loweredFb, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.setBlockState(slab, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);

        ctx.assertTrue(com.slabbed.anchor.SlabAnchorAttachment.isAnchored(world, grass),
                "fixture: grass must stay anchored after the adjacent lowered FB is removed");
        double supportDy = SlabSupport.getYOffset(world, grass, world.getBlockState(grass));
        ctx.assertTrue(supportDy == -0.5,
                "fixture: anchor-lowered grass support must render -0.5, got " + supportDy);
        ctx.assertTrue(!world.getBlockState(grass.down()).getBlock().equals(Blocks.STONE_SLAB),
                "fixture: support's column below must be a non-slab solid block (no slab in lantern column)");

        BlockPos lantern = grass.up();
        world.setBlockState(lantern, Blocks.LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
        double lanternDy = SlabSupport.getYOffset(world, lantern, world.getBlockState(lantern));
        ctx.assertTrue(lanternDy == -0.5,
                "standing lantern on an anchor-lowered full block (solid below) must lower -0.5 "
                        + "to sit flush, was floating at " + lanternDy);
        ctx.complete();
    }

    // A standing lantern on a COMPOUND-lowered full block (dy=-1.0: a full block above a lowered
    // side slab) must inherit the support's full -1.0, not a flat -0.5. The old sit-branch
    // returned a fixed -0.5 for any adjacent-lowered support, which left this lantern floating
    // half a block above the -1.0 support's top face. The fix resolves the support's actual dy.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lanternOnCompoundMinusOneSupportInheritsMinusOne(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
        BlockPos baseSlab = origin.add(3, 2, 3);
        BlockPos loweredFull = buildLoweredFullBlock(world, baseSlab);
        BlockPos sideSlab = loweredFull.east();
        world.setBlockState(sideSlab,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos compound = sideSlab.up(); // full block above lowered side slab -> dy -1.0
        world.setBlockState(compound, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        double supportDy = SlabSupport.getYOffset(world, compound, world.getBlockState(compound));
        ctx.assertTrue(supportDy == -1.0,
                "fixture: compound support must be dy=-1.0, got " + supportDy);

        BlockPos lantern = compound.up();
        world.setBlockState(lantern, Blocks.LANTERN.getDefaultState(), Block.NOTIFY_LISTENERS);
        double lanternDy = SlabSupport.getYOffset(world, lantern, world.getBlockState(lantern));
        ctx.assertTrue(lanternDy == -1.0,
                "standing lantern on a compound -1.0 support must inherit -1.0 (not a flat -0.5), got " + lanternDy);
        ctx.complete();
    }
}
