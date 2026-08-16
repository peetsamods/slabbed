package com.slabbed.test;

import static com.slabbed.test.PlacementHarness.mockPlayerHolding;
import static com.slabbed.test.PlacementHarness.useHeldItem;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Real-use coverage for the server's frozen placement-height transaction. */
public final class PlacementTransactionGameTest {

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
}
