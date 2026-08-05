package com.slabbed.test;

import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SideShapeType;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * GH #39: Slabbed's placement-support override must not make a bottom slab a valid
 * ON_GROUND mob-spawn surface.
 *
 * <p>The placement contract remains intentionally broad: a bottom slab's UP face still
 * reports full-square/RIGID support. Spawn eligibility is a separate policy and must keep
 * vanilla's bottom-slab mob-proofing.
 */
public final class BottomSlabSpawnProofTest {

    private static BlockState vanillaSlab(SlabType type, boolean waterlogged) {
        return Blocks.STONE_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, type)
                .with(SlabBlock.WATERLOGGED, waterlogged);
    }

    private static BlockState terrainSlab(SlabType type) {
        return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, type);
    }

    private static void setSupport(TestContext ctx, BlockPos relativePos, BlockState state) {
        ctx.getWorld().setBlockState(ctx.getAbsolutePos(relativePos), state, Block.NOTIFY_LISTENERS);
    }

    private static void assertSpawnSurface(
            TestContext ctx,
            BlockPos relativePos,
            EntityType<?> entityType,
            boolean expected,
            String label
    ) {
        ServerWorld world = ctx.getWorld();
        BlockPos supportPos = ctx.getAbsolutePos(relativePos);
        BlockState support = world.getBlockState(supportPos);
        boolean allows = support.allowsSpawning(world, supportPos, entityType);
        boolean onGround = SpawnRestriction.isSpawnPosAllowed(entityType, world, supportPos.up());
        ctx.assertTrue(allows == expected && onGround == expected,
                label + " expected allowsSpawning/ON_GROUND=" + expected + "/" + expected
                        + " but got " + allows + "/" + onGround);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaBottomSlabsStayMobProofWhileKeepingPlacementSupport(TestContext ctx) {
        BlockPos dry = new BlockPos(3, 2, 3);
        BlockPos wet = dry.east();
        setSupport(ctx, dry, vanillaSlab(SlabType.BOTTOM, false));
        setSupport(ctx, wet, vanillaSlab(SlabType.BOTTOM, true));

        ServerWorld world = ctx.getWorld();
        BlockPos dryAbsolute = ctx.getAbsolutePos(dry);
        BlockState dryState = world.getBlockState(dryAbsolute);
        ctx.assertTrue(dryState.isSideSolidFullSquare(world, dryAbsolute, Direction.UP),
                "setup: bottom slab must retain Slabbed's full-square UP placement support");
        ctx.assertTrue(dryState.isSideSolid(world, dryAbsolute, Direction.UP, SideShapeType.RIGID),
                "setup: bottom slab must retain Slabbed's RIGID UP placement support");

        assertSpawnSurface(ctx, dry, EntityType.ZOMBIE, false, "dry vanilla bottom slab / zombie");
        assertSpawnSurface(ctx, dry, EntityType.COW, false, "dry vanilla bottom slab / cow");
        assertSpawnSurface(ctx, wet, EntityType.ZOMBIE, false, "waterlogged vanilla bottom slab / zombie");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaTopAndDoubleSlabControlsRemainSpawnable(TestContext ctx) {
        BlockPos top = new BlockPos(3, 2, 6);
        BlockPos topWet = top.east();
        BlockPos doubled = top.east(2);
        setSupport(ctx, top, vanillaSlab(SlabType.TOP, false));
        setSupport(ctx, topWet, vanillaSlab(SlabType.TOP, true));
        setSupport(ctx, doubled, vanillaSlab(SlabType.DOUBLE, false));

        assertSpawnSurface(ctx, top, EntityType.ZOMBIE, true, "dry vanilla top slab");
        assertSpawnSurface(ctx, topWet, EntityType.ZOMBIE, true, "waterlogged vanilla top slab");
        assertSpawnSurface(ctx, doubled, EntityType.ZOMBIE, true, "vanilla double slab");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabBottomStaysMobProof(TestContext ctx) {
        BlockPos bottom = new BlockPos(3, 2, 3);
        setSupport(ctx, bottom, terrainSlab(SlabType.BOTTOM));

        ctx.assertTrue(
                CompatHooks.customSlabSurfaceKind(ctx.getWorld().getBlockState(ctx.getAbsolutePos(bottom)))
                        == CompatSlabSurfaceKind.BOTTOM_LIKE,
                "setup: terrain_slabs bottom fixture must classify BOTTOM_LIKE");
        assertSpawnSurface(ctx, bottom, EntityType.ZOMBIE, false, "terrain_slabs bottom slab");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabTopAndDoubleControlsRemainSpawnable(TestContext ctx) {
        BlockPos top = new BlockPos(4, 2, 3);
        BlockPos doubled = top.east();
        setSupport(ctx, top, terrainSlab(SlabType.TOP));
        setSupport(ctx, doubled, terrainSlab(SlabType.DOUBLE));

        assertSpawnSurface(ctx, top, EntityType.ZOMBIE, true, "terrain_slabs top slab");
        assertSpawnSurface(ctx, doubled, EntityType.ZOMBIE, true, "terrain_slabs double slab");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockAndAirControlsRemainUnchanged(TestContext ctx) {
        BlockPos stone = new BlockPos(3, 2, 3);
        BlockPos air = stone.east();
        setSupport(ctx, stone, Blocks.STONE.getDefaultState());
        setSupport(ctx, air, Blocks.AIR.getDefaultState());

        assertSpawnSurface(ctx, stone, EntityType.ZOMBIE, true, "stone control");
        assertSpawnSurface(ctx, air, EntityType.ZOMBIE, false, "air control");
        ctx.complete();
    }
}
