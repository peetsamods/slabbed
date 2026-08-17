package com.slabbed.test;

import static com.slabbed.test.PlacementHarness.mockPlayerHolding;
import static com.slabbed.test.PlacementHarness.useHeldItem;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
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
import net.minecraft.util.shape.VoxelShape;

/** Real-use coverage for the server's frozen placement-height transaction. */
public final class PlacementTransactionGameTest {

    private static final double EPSILON = 1.0e-6d;

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flatPlacementPublishesExplicitZeroFact(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos support = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos placed = support.up();
        world.setBlockState(support, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        PlayerEntity player = mockPlayerHolding(ctx, support.north(3), new ItemStack(Blocks.STONE, 1));
        long legacyFlatBefore = SlabPlacementDyAttachment.legacyFlatPublicationsDuringTransaction();

        ActionResult result = useHeldItem(world, player, support, Direction.UP,
                Vec3d.ofCenter(support).add(0.0d, 0.5d, 0.0d));

        ctx.assertTrue(result.isAccepted(), "fixture: stone placement must succeed");
        ctx.assertTrue(world.getBlockState(placed).isOf(Blocks.STONE),
                "fixture: stone must occupy the vanilla-selected cell");
        double stored = SlabPlacementDyAttachment.storedDy(world, placed);
        ctx.assertTrue(Double.doubleToRawLongBits(stored) == Double.doubleToRawLongBits(0.0d),
                "every new placement needs an explicit frozen fact, including flat 0.0; got " + stored);
        ctx.assertTrue(!SlabAnchorAttachment.isFrozenFlat(world, placed),
                "the transaction-owned numeric fact must not be duplicated by a legacy flat marker");
        ctx.assertTrue(SlabPlacementDyAttachment.legacyFlatPublicationsDuringTransaction() == legacyFlatBefore,
                "the legacy flat writer must not publish during a captured block-item transaction");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredPlacementPublishesOnlyTheFinalHeight(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(new BlockPos(1, 2, 4));
        BlockPos support = ground.up();
        BlockPos placed = support.up();
        world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(support, Blocks.OAK_SLAB.getDefaultState(), Block.NOTIFY_ALL);
        PlayerEntity player = mockPlayerHolding(ctx, support.north(3), new ItemStack(Blocks.STONE, 1));
        long legacyRecordBefore = SlabPlacementDyAttachment.legacyRecordPublicationsDuringTransaction();

        ActionResult result = useHeldItem(world, player, support, Direction.UP,
                new Vec3d(support.getX() + 0.5d, support.getY() + 0.5d, support.getZ() + 0.5d));

        ctx.assertTrue(result.isAccepted(), "fixture: lowered stone placement must succeed");
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, placed),
                "anchor membership remains required while the transaction owns the height value");
        ctx.assertTrue(Double.doubleToRawLongBits(SlabPlacementDyAttachment.storedDy(world, placed))
                        == Double.doubleToRawLongBits(-0.5d),
                "the transaction must publish the resolved lowered height");
        ctx.assertTrue(SlabPlacementDyAttachment.legacyRecordPublicationsDuringTransaction()
                        == legacyRecordBefore,
                "the legacy anchor writer must not publish an interim height");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void doorPlacementPublishesBothHalves(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos support = ctx.getAbsolutePos(new BlockPos(5, 2, 5));
        BlockPos lower = support.up();
        BlockPos upper = lower.up();
        world.setBlockState(support, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        PlayerEntity player = mockPlayerHolding(ctx, support.up(3), new ItemStack(Blocks.OAK_DOOR, 1));

        ActionResult result = useHeldItem(world, player, support, Direction.UP,
                Vec3d.ofCenter(support).add(0.0d, 0.5d, 0.0d));

        ctx.assertTrue(result.isAccepted(), "fixture: door placement must succeed");
        BlockState lowerState = world.getBlockState(lower);
        BlockState upperState = world.getBlockState(upper);
        ctx.assertTrue(lowerState.isOf(Blocks.OAK_DOOR) && upperState.isOf(Blocks.OAK_DOOR),
                "fixture: vanilla must publish both door halves before the height transaction");
        double lowerDy = SlabPlacementDyAttachment.storedDy(world, lower);
        double upperDy = SlabPlacementDyAttachment.storedDy(world, upper);
        ctx.assertTrue(Double.doubleToRawLongBits(lowerDy) == Double.doubleToRawLongBits(0.0d)
                        && Double.doubleToRawLongBits(upperDy) == Double.doubleToRawLongBits(0.0d),
                "a linked placement must publish one shared fact for both halves; lower="
                        + lowerDy + " upper=" + upperDy);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void invalidBatchPublishesNeitherCell(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos first = ctx.getAbsolutePos(new BlockPos(8, 2, 5));
        BlockPos second = first.up();
        Map<BlockPos, Long> batch = new LinkedHashMap<>();
        batch.put(first, Double.doubleToRawLongBits(0.0d));
        batch.put(second, Double.doubleToRawLongBits(-0.3d));

        boolean published = SlabPlacementDyAttachment.writeBatch(world, batch);

        ctx.assertTrue(!published, "an invalid linked height group must be refused as a whole");
        ctx.assertTrue(Double.isNaN(SlabPlacementDyAttachment.storedDy(world, first))
                        && Double.isNaN(SlabPlacementDyAttachment.storedDy(world, second)),
                "a refused linked group must publish neither its valid nor invalid cell");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sameCellSlabUpgradePreservesItsFact(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos slab = ctx.getAbsolutePos(new BlockPos(8, 4, 2));
        double placedDy = -0.5d;
        world.setBlockState(slab,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_ALL);
        ctx.assertTrue(SlabPlacementDyAttachment.writeBatch(
                        world, Map.of(slab, Double.doubleToRawLongBits(placedDy))),
                "fixture: the original slab must accept its frozen height");
        PlayerEntity player = mockPlayerHolding(
                ctx, slab.north(3), new ItemStack(Blocks.OAK_SLAB.asItem(), 1));

        ActionResult result = useHeldItem(world, player, slab, Direction.UP,
                new Vec3d(slab.getX() + 0.5d, slab.getY(), slab.getZ() + 0.5d));

        BlockState after = world.getBlockState(slab);
        ctx.assertTrue(result.isAccepted(), "fixture: same-cell slab combine must succeed");
        ctx.assertTrue(after.isOf(Blocks.OAK_SLAB) && after.get(SlabBlock.TYPE) == SlabType.DOUBLE,
                "fixture: vanilla must upgrade the existing slab to double");
        ctx.assertTrue(Double.doubleToRawLongBits(SlabPlacementDyAttachment.storedDy(world, slab))
                        == Double.doubleToRawLongBits(placedDy),
                "a same-cell upgrade must preserve the original placement height");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directLegacyFreezeStillWorksOutsideTransaction(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos support = ctx.getAbsolutePos(new BlockPos(11, 2, 2));
        BlockPos placed = support.up();
        world.setBlockState(support, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(placed, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);

        SlabAnchorAttachment.freezeLoweredOnPlace(world, placed, world.getBlockState(placed));

        ctx.assertTrue(SlabAnchorAttachment.isFrozenFlat(world, placed),
                "direct non-block-item callers must retain the legacy flat freeze");
        ctx.assertTrue(Double.isNaN(SlabPlacementDyAttachment.storedDy(world, placed)),
                "the preserved legacy path must remain distinct from transaction numeric facts");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabPlacedVerticallyOnVanillaSlabPublishesMinusHalf(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos vanillaSlab = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos terrainSlab = vanillaSlab.up();
        world.setBlockState(vanillaSlab.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(vanillaSlab,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_ALL);
        ctx.assertTrue(SlabSupport.getVisualYOffset(
                        world, vanillaSlab, world.getBlockState(vanillaSlab)) == 0.0d,
                "fixture: the supported vanilla bottom slab must be flat");
        Item terrainItem = TerrainSlabsTestShim.TEST_TS_SLAB_ITEM;
        PlayerEntity player = mockPlayerHolding(
                ctx, vanillaSlab.north(3), new ItemStack(terrainItem, 1));

        ActionResult result = useHeldItem(world, player, vanillaSlab, Direction.UP,
                new Vec3d(vanillaSlab.getX() + 0.5d, vanillaSlab.getY() + 0.5d,
                        vanillaSlab.getZ() + 0.5d));

        ctx.assertTrue(result.isAccepted(),
                "Terrain Slab placement on the vanilla slab must succeed; result=" + result
                        + " destination=" + world.getBlockState(terrainSlab));
        ctx.assertTrue(world.getBlockState(terrainSlab).isOf(((BlockItem) terrainItem).getBlock()),
                "the actual Terrain Slab item must occupy the vertical destination cell");
        assertStoredDy(ctx, world, terrainSlab, -0.5d,
                "vertical Terrain Slab placement on a vanilla bottom slab");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaSlabAboveAuthoredTerrainSlabPublishesMinusOne(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos vanillaBase = ctx.getAbsolutePos(new BlockPos(5, 2, 2));
        BlockPos terrainMiddle = vanillaBase.up();
        BlockPos vanillaTop = terrainMiddle.up();
        world.setBlockState(vanillaBase.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(vanillaBase,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_ALL);
        Item terrainItem = TerrainSlabsTestShim.TEST_TS_SLAB_ITEM;
        PlayerEntity terrainPlayer = mockPlayerHolding(
                ctx, vanillaBase.north(3), new ItemStack(terrainItem, 1));
        ActionResult terrainResult = useHeldItem(world, terrainPlayer, vanillaBase, Direction.UP,
                new Vec3d(vanillaBase.getX() + 0.5d, vanillaBase.getY() + 0.5d,
                        vanillaBase.getZ() + 0.5d));
        ctx.assertTrue(terrainResult.isAccepted(), "fixture: authored Terrain Slab must place");

        PlayerEntity vanillaPlayer = mockPlayerHolding(
                ctx, terrainMiddle.north(3), new ItemStack(Blocks.OAK_SLAB.asItem(), 1));
        ActionResult vanillaResult = useHeldItem(world, vanillaPlayer, terrainMiddle, Direction.UP,
                new Vec3d(terrainMiddle.getX() + 0.5d, terrainMiddle.getY(),
                        terrainMiddle.getZ() + 0.5d));

        ctx.assertTrue(vanillaResult.isAccepted(), "vanilla slab placement above Terrain Slab must succeed");
        ctx.assertTrue(world.getBlockState(vanillaTop).isOf(Blocks.OAK_SLAB),
                "the vanilla slab must occupy the cell above the Terrain Slab");
        assertStoredDy(ctx, world, terrainMiddle, -0.5d,
                "authored Terrain Slab in the middle course");
        assertStoredDy(ctx, world, vanillaTop, -1.0d,
                "vanilla slab above an authored lowered Terrain Slab");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabAboveLoweredVanillaBlockPublishesMinusHalf(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos vanillaSlab = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos vanillaBlock = vanillaSlab.up();
        BlockPos terrainTop = vanillaBlock.up();
        world.setBlockState(vanillaSlab.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(vanillaSlab,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_ALL);
        PlayerEntity blockPlayer = mockPlayerHolding(
                ctx, vanillaSlab.north(3), new ItemStack(Blocks.STRIPPED_OAK_WOOD.asItem(), 1));
        ActionResult blockResult = useHeldItem(world, blockPlayer, vanillaSlab, Direction.UP,
                new Vec3d(vanillaSlab.getX() + 0.5d, vanillaSlab.getY() + 0.5d,
                        vanillaSlab.getZ() + 0.5d));
        ctx.assertTrue(blockResult.isAccepted(), "fixture: lowered vanilla block must place");
        assertStoredDy(ctx, world, vanillaBlock, -0.5d, "lowered vanilla block support");

        Item terrainItem = TerrainSlabsTestShim.TEST_TS_SLAB_ITEM;
        PlayerEntity terrainPlayer = mockPlayerHolding(
                ctx, vanillaBlock.north(3), new ItemStack(terrainItem, 1));
        ActionResult terrainResult = useHeldItem(world, terrainPlayer, vanillaBlock, Direction.UP,
                new Vec3d(vanillaBlock.getX() + 0.5d, vanillaBlock.getY() + 0.5d,
                        vanillaBlock.getZ() + 0.5d));

        ctx.assertTrue(terrainResult.isAccepted(), "Terrain Slab placement on the lowered block must succeed");
        ctx.assertTrue(world.getBlockState(terrainTop).isOf(((BlockItem) terrainItem).getBlock()),
                "the actual Terrain Slab item must occupy the top destination cell");
        assertStoredDy(ctx, world, terrainTop, -0.5d,
                "Terrain Slab above a lowered full-height vanilla support");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void perpendicularTerrainSlabInheritsAuthoredTerrainDy(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos vanillaBase = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos terrainOwner = vanillaBase.up();
        BlockPos terrainSide = terrainOwner.east();
        world.setBlockState(vanillaBase.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(vanillaBase,
                Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_ALL);
        Item terrainItem = TerrainSlabsTestShim.TEST_TS_SLAB_ITEM;
        PlayerEntity verticalPlayer = mockPlayerHolding(
                ctx, vanillaBase.north(3), new ItemStack(terrainItem, 1));
        ActionResult verticalResult = useHeldItem(world, verticalPlayer, vanillaBase, Direction.UP,
                new Vec3d(vanillaBase.getX() + 0.5d, vanillaBase.getY() + 0.5d,
                        vanillaBase.getZ() + 0.5d));
        ctx.assertTrue(verticalResult.isAccepted(), "fixture: authored Terrain Slab owner must place");

        PlayerEntity sidePlayer = mockPlayerHolding(
                ctx, terrainOwner.north(3), new ItemStack(terrainItem, 1));
        ActionResult sideResult = useHeldItem(world, sidePlayer, terrainOwner, Direction.EAST,
                new Vec3d(terrainOwner.getX() + 1.0d, terrainOwner.getY() - 0.25d,
                        terrainOwner.getZ() + 0.5d));

        ctx.assertTrue(sideResult.isAccepted(), "perpendicular Terrain Slab placement must succeed");
        BlockState sideState = world.getBlockState(terrainSide);
        ctx.assertTrue(sideState.isOf(((BlockItem) terrainItem).getBlock())
                        && sideState.contains(SlabBlock.TYPE)
                        && sideState.get(SlabBlock.TYPE) == SlabType.BOTTOM,
                "the perpendicular Terrain Slab must occupy the intended lower visible half");
        assertStoredDy(ctx, world, terrainOwner, -0.5d,
                "authored Terrain Slab side-placement owner");
        assertStoredDy(ctx, world, terrainSide, -0.5d,
                "perpendicular Terrain Slab destination");

        world.setBlockState(terrainOwner, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        assertStoredDy(ctx, world, terrainSide, -0.5d,
                "perpendicular Terrain Slab after owner removal");
        ctx.complete();
    }

    private static void assertStoredDy(
            TestContext ctx,
            ServerWorld world,
            BlockPos pos,
            double expected,
            String label
    ) {
        double stored = SlabPlacementDyAttachment.storedDy(world, pos);
        ctx.assertTrue(Double.doubleToRawLongBits(stored) == Double.doubleToRawLongBits(expected),
                label + " must publish immutable dy=" + expected + "; got " + stored);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabLiteralSidePlacementUsesActiveLoweredEnvelope(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos owner = ctx.getAbsolutePos(new BlockPos(3, 4, 3));
        BlockPos destination = owner.east();
        BlockPos fenceContact = destination.north();
        BlockPos wallContact = destination.south();
        BlockPos paneContact = destination.east();
        Item slabItem = TerrainSlabsTestShim.TEST_TS_SLAB_ITEM;
        Block slab = ((BlockItem) slabItem).getBlock();
        double minimum = SlabSupport.minResolvedDy();

        for (double ownerDy = -0.5d;
                ownerDy >= minimum - EPSILON;
                ownerDy -= 0.5d) {
            for (SlabType expectedType : new SlabType[]{SlabType.BOTTOM, SlabType.TOP}) {
                world.setBlockState(owner, Blocks.STRIPPED_BIRCH_WOOD.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(destination, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(fenceContact, Blocks.OAK_FENCE.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(wallContact, Blocks.COBBLESTONE_WALL.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(paneContact, Blocks.GLASS_PANE.getDefaultState(), Block.NOTIFY_ALL);
                SlabPlacementDyAttachment.clear(world, owner);
                SlabPlacementDyAttachment.clear(world, destination);
                ctx.assertTrue(SlabPlacementDyAttachment.writeBatch(
                                world, Map.of(owner, Double.doubleToRawLongBits(ownerDy))),
                        "fixture: the ordinary owner must accept dy " + ownerDy);

                double halfSplit = owner.getY() + ownerDy + 0.5d;
                double aimY = halfSplit + (expectedType == SlabType.TOP ? 0.25d : -0.25d);
                PlayerEntity player = mockPlayerHolding(
                        ctx, owner.north(3), new ItemStack(slabItem, 1));
                ActionResult result = useHeldItem(world, player, owner, Direction.EAST,
                        new Vec3d(owner.getX() + 1.0d, aimY, owner.getZ() + 0.5d));

                ctx.assertTrue(result.isAccepted(),
                        "Terrain Slab side placement must succeed at in-range dy " + ownerDy
                                + " for " + expectedType + "; got " + result);
                BlockState placed = world.getBlockState(destination);
                ctx.assertTrue(placed.isOf(slab)
                                && placed.contains(SlabBlock.TYPE)
                                && placed.get(SlabBlock.TYPE) == expectedType,
                        "the real Terrain Slab item must place the intended half at dy " + ownerDy
                                + "; got " + placed);
                ctx.assertTrue(Double.doubleToRawLongBits(
                                SlabPlacementDyAttachment.storedDy(world, destination))
                                == Double.doubleToRawLongBits(ownerDy),
                        "Terrain Slab placement must freeze the immutable owner dy " + ownerDy);
                ctx.assertTrue(Double.doubleToRawLongBits(
                                SlabSupport.getYOffset(world, destination, placed))
                                == Double.doubleToRawLongBits(ownerDy),
                        "Terrain Slab placement must resolve at its frozen dy " + ownerDy);

                VoxelShape outline = placed.getOutlineShape(world, destination, ShapeContext.absent());
                double nativeMinY = expectedType == SlabType.TOP ? 0.5d : 0.0d;
                double nativeMaxY = expectedType == SlabType.TOP ? 1.0d : 0.5d;
                ctx.assertTrue(!outline.isEmpty()
                                && Math.abs(outline.getMin(Direction.Axis.Y) - (nativeMinY + ownerDy))
                                <= EPSILON
                                && Math.abs(outline.getMax(Direction.Axis.Y) - (nativeMaxY + ownerDy))
                                <= EPSILON,
                        "Terrain Slab outline must occupy the frozen visible half at dy " + ownerDy
                                + "; got [" + (outline.isEmpty() ? "empty" : outline.getMin(Direction.Axis.Y))
                                + ", " + (outline.isEmpty() ? "empty" : outline.getMax(Direction.Axis.Y))
                                + "]");
                ctx.assertTrue(world.getBlockState(fenceContact).isOf(Blocks.OAK_FENCE)
                                && world.getBlockState(wallContact).isOf(Blocks.COBBLESTONE_WALL)
                                && world.getBlockState(paneContact).isOf(Blocks.GLASS_PANE),
                        "legal fence, wall, and pane contacts must survive the admitted placement");

                world.setBlockState(owner, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                BlockState afterOwnerRemoval = world.getBlockState(destination);
                VoxelShape outlineAfterOwnerRemoval = afterOwnerRemoval.getOutlineShape(
                        world, destination, ShapeContext.absent());
                ctx.assertTrue(afterOwnerRemoval.equals(placed),
                        "removing the clicked owner must not replace the placed Terrain Slab");
                ctx.assertTrue(Double.doubleToRawLongBits(
                                SlabSupport.getYOffset(world, destination, afterOwnerRemoval))
                                == Double.doubleToRawLongBits(ownerDy),
                        "removing the clicked owner must not move the placed Terrain Slab from dy "
                                + ownerDy);
                ctx.assertTrue(Double.doubleToRawLongBits(
                                SlabPlacementDyAttachment.storedDy(world, destination))
                                == Double.doubleToRawLongBits(ownerDy),
                        "removing the clicked owner must preserve the Terrain Slab's frozen fact");
                ctx.assertTrue(!outlineAfterOwnerRemoval.isEmpty()
                                && Math.abs(outlineAfterOwnerRemoval.getMin(Direction.Axis.Y)
                                        - outline.getMin(Direction.Axis.Y)) <= EPSILON
                                && Math.abs(outlineAfterOwnerRemoval.getMax(Direction.Axis.Y)
                                        - outline.getMax(Direction.Axis.Y)) <= EPSILON,
                        "removing the clicked owner must preserve the Terrain Slab outline");
            }
        }
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabLiteralSidePlacementBelowActiveEnvelopeIsRefused(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos owner = ctx.getAbsolutePos(new BlockPos(4, 4, 4));
        BlockPos destination = owner.east();
        double ownerDy = SlabSupport.minResolvedDy() - 0.5d;
        BlockState ownerBefore = Blocks.STRIPPED_BIRCH_WOOD.getDefaultState();
        BlockState destinationBefore = world.getBlockState(destination);

        for (Item slabItem : new Item[]{Blocks.OAK_SLAB.asItem(), TerrainSlabsTestShim.TEST_TS_SLAB_ITEM}) {
            world.setBlockState(owner, ownerBefore, Block.NOTIFY_ALL);
            world.setBlockState(destination, destinationBefore, Block.NOTIFY_ALL);
            SlabPlacementDyAttachment.clear(world, destination);
            ctx.assertTrue(SlabPlacementDyAttachment.writeBatch(
                            world, Map.of(owner, Double.doubleToRawLongBits(ownerDy))),
                    "fixture: the historical owner height must remain storable below the active envelope");
            PlayerEntity player = mockPlayerHolding(ctx, owner.north(3), new ItemStack(slabItem, 1));
            ActionResult result = useHeldItem(world, player, owner, Direction.EAST,
                    new Vec3d(owner.getX() + 1.0d, owner.getY() + ownerDy + 0.5d,
                            owner.getZ() + 0.5d));

            ctx.assertTrue(result == ActionResult.FAIL,
                    "slab placement below the active envelope must return a typed refusal for "
                            + slabItem + "; got " + result);
            ctx.assertTrue(world.getBlockState(owner).equals(ownerBefore)
                            && world.getBlockState(destination).equals(destinationBefore),
                    "a refused slab placement must not mutate either cell for " + slabItem);
            ctx.assertTrue(Double.isNaN(SlabPlacementDyAttachment.storedDy(world, destination)),
                    "a refused slab placement must not publish a destination fact for " + slabItem);
            ctx.assertTrue(player.getMainHandStack().getCount() == 1,
                    "a refused slab placement must not consume the held item " + slabItem);
        }
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void verticalSlabCourseBelowActiveEnvelopeIsRefused(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos owner = ctx.getAbsolutePos(new BlockPos(2, 3, 2));
        BlockPos destination = owner.up();
        BlockState ownerState = TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM);
        double ownerDy = SlabSupport.minResolvedDy();

        for (Item slabItem : new Item[]{Blocks.OAK_SLAB.asItem()}) {
            world.setBlockState(owner, ownerState, Block.NOTIFY_ALL);
            world.setBlockState(destination, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            SlabPlacementDyAttachment.clear(world, destination);
            ctx.assertTrue(SlabPlacementDyAttachment.writeBatch(
                            world, Map.of(owner, Double.doubleToRawLongBits(ownerDy))),
                    "fixture: the authored custom owner must carry the active minimum dy");
            PlayerEntity player = mockPlayerHolding(ctx, owner.north(3), new ItemStack(slabItem, 1));

            ActionResult result = useHeldItem(world, player, owner, Direction.UP,
                    new Vec3d(owner.getX() + 0.5d, owner.getY() + ownerDy + 0.5d,
                            owner.getZ() + 0.5d));

            ctx.assertTrue(result == ActionResult.FAIL,
                    "a new vertical bottom-slab course below the active envelope must be refused for "
                            + slabItem + "; got " + result);
            ctx.assertTrue(world.getBlockState(owner).equals(ownerState)
                            && world.getBlockState(destination).isAir(),
                    "typed refusal must leave the owner and destination cells unchanged for " + slabItem);
            ctx.assertTrue(Double.isNaN(SlabPlacementDyAttachment.storedDy(world, destination)),
                    "typed refusal must publish no destination height for " + slabItem);
            ctx.assertTrue(player.getMainHandStack().getCount() == 1,
                    "typed refusal must not consume " + slabItem);
        }
        ctx.complete();
    }
}
