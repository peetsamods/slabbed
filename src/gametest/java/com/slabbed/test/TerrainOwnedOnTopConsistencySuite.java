package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Terrain-Slabs-owned on-top objects get exactly ONE offset — Terrain Slabs' own.
 *
 * <p>The placement transaction already honors that ownership law on the fact-minting side: an
 * UP-face placement whose clicked owner classifies {@code BOTTOM_LIKE} and whose final state
 * Terrain Slabs owns (vegetation, snow) mints NO Slabbed height fact
 * ({@code BlockItemPlacementIntentMixin}'s Terrain-owned gate). This suite pins the companion
 * invariant on the LIVE lanes: Slabbed's own resolved contribution for such an object must be
 * ZERO whether the Terrain surface below is NATIVE (worldgen-shaped, no stored fact, no anchor)
 * or AUTHORED (player-placed through the real item path, with a frozen height and whatever
 * anchors its placement earned). The ownership answer must not depend on surface authorship — a
 * split there is a half-block float or a double offset on the object, depending on which side
 * renders it.
 *
 * <p>The torch row is the deliberate contrast: a NON-owned object over the same authored surface
 * seats via Slabbed's transaction at the authored surface plane, so the zero contract above can
 * never be satisfied by blanket-suppressing everything that sits on a Terrain Slabs surface.
 *
 * <p>Fixture note: the gametest data pack adds the shim slab to the vanilla
 * {@code snow_layer_can_survive_on} block tag, mirroring real Terrain Slabs surfaces (which host
 * weather snow as a first-class feature); without it vanilla refuses the real-item snow
 * placement outright and the transaction under test never runs.
 */
public final class TerrainOwnedOnTopConsistencySuite {

    private static final double EPS = 1.0e-6d;

    /**
     * NATIVE control: a worldgen-shaped Terrain surface holds no Slabbed fact and no anchor, and
     * the Terrain-owned snow layer placed on it through the real item path must end with zero
     * Slabbed contribution — no stored fact, resolved dy 0.0.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void nativeTerrainSurfaceOwnedObjectGetsNoSlabbedContribution(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
        BlockPos surface = ground.up();
        BlockPos object = surface.up();
        world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(surface, TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_ALL);
        ctx.assertTrue(!SlabPlacementDyAttachment.rawFact(world, surface).present(),
                "fixture: a NATIVE Terrain surface must hold no stored fact");
        ctx.assertTrue(CompatHooks.terrainSlabsHandlesObjectOffset(Blocks.SNOW.getDefaultState()),
                "fixture: the ownership predicate must cover the snow layer");

        PlayerEntity player = PlacementHarness.mockPlayerHolding(
                ctx, surface.north(3), new ItemStack(Blocks.SNOW.asItem(), 1));
        ActionResult result = PlacementHarness.useHeldItem(world, player, surface, Direction.UP,
                new Vec3d(surface.getX() + 0.5d, surface.getY() + 0.5d, surface.getZ() + 0.5d));

        ctx.assertTrue(result.isAccepted(),
                "A1: snow onto the native Terrain surface must place; got " + result);
        ctx.assertTrue(world.getBlockState(object).isOf(Blocks.SNOW),
                "A1: the snow layer must occupy the cell above the Terrain surface; got "
                        + PlacementHarness.describe(world, object));
        assertZeroSlabbedContribution(ctx, world, object, "A1 native-surface snow layer");
        ctx.complete();
    }

    /**
     * AUTHORED surface: the same Terrain slab placed through the real item path onto a lowered
     * vanilla owner freezes its authored height (readback-asserted), and the same Terrain-owned
     * snow layer on top must still end with zero Slabbed contribution — Terrain Slabs owns any
     * visual seating, so the authored surface's stored fact and placement anchors must not leak
     * a Slabbed dy into the object through the live column/anchor lanes.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void authoredTerrainSurfaceOwnedObjectGetsNoSlabbedContribution(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos terrain = authoredTerrainSlab(ctx, 2, 2);
        BlockPos object = terrain.up();
        ctx.assertTrue(CompatHooks.terrainSlabsHandlesObjectOffset(Blocks.SNOW.getDefaultState()),
                "fixture: the ownership predicate must cover the snow layer");

        PlayerEntity player = PlacementHarness.mockPlayerHolding(
                ctx, terrain.north(3), new ItemStack(Blocks.SNOW.asItem(), 1));
        ActionResult result = PlacementHarness.useHeldItem(world, player, terrain, Direction.UP,
                new Vec3d(terrain.getX() + 0.5d, terrain.getY() + 0.5d, terrain.getZ() + 0.5d));

        ctx.assertTrue(result.isAccepted(),
                "B1: snow onto the authored Terrain surface must place; got " + result);
        ctx.assertTrue(world.getBlockState(object).isOf(Blocks.SNOW),
                "B1: the snow layer must occupy the cell above the authored Terrain surface; got "
                        + PlacementHarness.describe(world, object));
        assertZeroSlabbedContribution(ctx, world, object, "B1 authored-surface snow layer");
        ctx.complete();
    }

    /**
     * CONTRAST: a torch is NOT Terrain-owned, so over the very same authored Terrain surface it
     * must seat via Slabbed's transaction on the authored surface plane — the authored slab's
     * frozen {@code -0.5} puts its top face a full block below the object cell's floor, so the
     * torch freezes and resolves at {@code -1.0}. This row keeps the zero contract above honest:
     * a fix may not blanket-suppress every Slabbed contribution over Terrain Slabs surfaces.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void nonOwnedObjectOverAuthoredTerrainSlabSeatsAtAuthoredSurface(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos terrain = authoredTerrainSlab(ctx, 5, 2);
        BlockPos object = terrain.up();
        ctx.assertTrue(!CompatHooks.terrainSlabsHandlesObjectOffset(Blocks.TORCH.getDefaultState()),
                "fixture: the ownership predicate must NOT cover the torch");

        PlayerEntity player = PlacementHarness.mockPlayerHolding(
                ctx, terrain.north(3), new ItemStack(Blocks.TORCH.asItem(), 1));
        ActionResult result = PlacementHarness.useHeldItem(world, player, terrain, Direction.UP,
                new Vec3d(terrain.getX() + 0.5d, terrain.getY() + 0.5d, terrain.getZ() + 0.5d));

        ctx.assertTrue(result.isAccepted(),
                "C1: torch onto the authored Terrain surface must place; got " + result);
        ctx.assertTrue(world.getBlockState(object).isOf(Blocks.TORCH),
                "C1: the torch must occupy the cell above the authored Terrain surface; got "
                        + PlacementHarness.describe(world, object));
        double stored = SlabPlacementDyAttachment.storedDy(world, object);
        System.out.println("[TS-OWNED] C1 torch stored=" + stored + " "
                + PlacementHarness.describe(world, object));
        ctx.assertTrue(Double.doubleToRawLongBits(stored) == Double.doubleToRawLongBits(-1.0d),
                "C1: a non-owned object over the authored -0.5 Terrain surface must freeze the "
                        + "authored seat -1.0; got " + stored);
        double resolved = SlabSupport.getYOffset(world, object, world.getBlockState(object));
        ctx.assertTrue(Math.abs(resolved + 1.0d) <= EPS,
                "C1: the torch must resolve at its frozen authored seat -1.0; got " + resolved);
        ctx.complete();
    }

    /**
     * Builds the authored Terrain surface through the real item path only: a vanilla bottom slab
     * (set) carries a lowered vanilla owner (item-placed, frozen {@code -0.5}), and the shim
     * Terrain slab item is clicked onto that owner's top face, freezing the authored
     * {@code -0.5} Terrain height. Every step readback-asserts its frozen fact so the rows above
     * cannot go green against a fixture that silently failed to author.
     */
    private static BlockPos authoredTerrainSlab(TestContext ctx, int x, int z) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(x, 1, z));
        BlockPos vanillaSlab = base.up();
        BlockPos owner = vanillaSlab.up();
        BlockPos terrain = owner.up();
        world.setBlockState(base, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(vanillaSlab,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_ALL);

        PlayerEntity ownerPlayer = PlacementHarness.mockPlayerHolding(
                ctx, vanillaSlab.north(3), new ItemStack(Blocks.STRIPPED_OAK_WOOD.asItem(), 1));
        ActionResult ownerResult = PlacementHarness.useHeldItem(
                world, ownerPlayer, vanillaSlab, Direction.UP,
                new Vec3d(vanillaSlab.getX() + 0.5d, vanillaSlab.getY() + 0.5d,
                        vanillaSlab.getZ() + 0.5d));
        ctx.assertTrue(ownerResult.isAccepted(),
                "fixture: the lowered vanilla owner must place; got " + ownerResult);
        double ownerStored = SlabPlacementDyAttachment.storedDy(world, owner);
        ctx.assertTrue(Double.doubleToRawLongBits(ownerStored) == Double.doubleToRawLongBits(-0.5d),
                "fixture: the lowered vanilla owner must freeze -0.5; got " + ownerStored);

        Item terrainItem = TerrainSlabsTestShim.TEST_TS_SLAB_ITEM;
        PlayerEntity terrainPlayer = PlacementHarness.mockPlayerHolding(
                ctx, owner.north(3), new ItemStack(terrainItem, 1));
        ActionResult terrainResult = PlacementHarness.useHeldItem(
                world, terrainPlayer, owner, Direction.UP,
                new Vec3d(owner.getX() + 0.5d, owner.getY() + 0.5d, owner.getZ() + 0.5d));
        ctx.assertTrue(terrainResult.isAccepted(),
                "fixture: the authored Terrain slab must place; got " + terrainResult);
        ctx.assertTrue(world.getBlockState(terrain).isOf(((BlockItem) terrainItem).getBlock()),
                "fixture: the Terrain slab item must occupy the authored cell; got "
                        + PlacementHarness.describe(world, terrain));
        double terrainStored = SlabPlacementDyAttachment.storedDy(world, terrain);
        ctx.assertTrue(Double.doubleToRawLongBits(terrainStored)
                        == Double.doubleToRawLongBits(-0.5d),
                "fixture: the authored Terrain slab must freeze -0.5; got " + terrainStored);
        return terrain;
    }

    /**
     * The zero-Slabbed-contribution contract for a Terrain-owned object: no stored fact (the
     * transaction must not mint one) and a resolved dy of exactly zero (no live lane may
     * contribute one). Terrain Slabs owns any visual seating of its own objects.
     */
    private static void assertZeroSlabbedContribution(
            TestContext ctx,
            ServerWorld world,
            BlockPos object,
            String label
    ) {
        double stored = SlabPlacementDyAttachment.storedDy(world, object);
        double resolved = SlabSupport.getYOffset(world, object, world.getBlockState(object));
        System.out.println("[TS-OWNED] " + label + " stored=" + stored + " resolved=" + resolved);
        ctx.assertTrue(!SlabPlacementDyAttachment.rawFact(world, object).present(),
                label + ": the transaction must mint no Slabbed fact for a Terrain-owned object; "
                        + "got stored=" + stored);
        ctx.assertTrue(Math.abs(resolved) <= EPS,
                label + ": Slabbed's live contribution for a Terrain-owned object must be zero "
                        + "over native AND authored surfaces alike; got " + resolved);
    }
}
