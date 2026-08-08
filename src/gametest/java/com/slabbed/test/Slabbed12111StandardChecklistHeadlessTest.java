package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.WallShape;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Standard checklist headless expansion for the 1.21.11 Terrain Slabs compat line.
 */
public final class Slabbed12111StandardChecklistHeadlessTest {
    private static final double EPS = 1.0e-6;

    private static BlockState slab(SlabType type) {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
    }

    private static double dy(TestContext ctx, BlockPos rel) {
        ServerWorld world = ctx.getWorld();
        BlockPos abs = ctx.getAbsolutePos(rel);
        return SlabSupport.getYOffset(world, abs, world.getBlockState(abs));
    }

    private static void expectDy(TestContext ctx, BlockPos rel, double want, String label) {
        double got = dy(ctx, rel);
        Slabbed.LOGGER.info("CHECKLIST-FP | {} | dy={}",
                label, String.format(java.util.Locale.ROOT, "%.3f", got));
        ctx.assertTrue(Math.abs(got - want) <= EPS,
                label + ": expected dy=" + want + " got " + got);
    }

    private static void clear(TestContext ctx, BlockPos... rels) {
        for (BlockPos rel : rels) {
            ctx.setBlockState(rel, Blocks.AIR);
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void restingFullBlocksOnBottomSlabLowerHalf(TestContext ctx) {
        BlockPos slab = new BlockPos(2, 1, 2);
        BlockPos obj = new BlockPos(2, 2, 2);
        Block[] blocks = {
                Blocks.CHEST, Blocks.BARREL, Blocks.FURNACE, Blocks.BOOKSHELF, Blocks.ENCHANTING_TABLE,
                Blocks.STONECUTTER, Blocks.ANVIL, Blocks.GRINDSTONE, Blocks.LECTERN, Blocks.CRAFTING_TABLE
        };
        for (Block block : blocks) {
            ctx.setBlockState(slab, slab(SlabType.BOTTOM));
            ctx.setBlockState(obj, block.getDefaultState());
            expectDy(ctx, obj, -0.5, "resting_" + block.getTranslationKey() + "_bottom_slab");
            clear(ctx, obj, slab);
        }
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void restingTopAndDoubleSlabsStayFlush(TestContext ctx) {
        BlockPos slab = new BlockPos(2, 1, 2);
        BlockPos obj = new BlockPos(2, 2, 2);
        Block[] blocks = {Blocks.STONE, Blocks.CHEST};
        for (Block block : blocks) {
            ctx.setBlockState(slab, slab(SlabType.TOP));
            ctx.setBlockState(obj, block.getDefaultState());
            expectDy(ctx, obj, 0.0, "resting_" + block.getTranslationKey() + "_top_slab");
            clear(ctx, obj, slab);
            ctx.setBlockState(slab, slab(SlabType.DOUBLE));
            ctx.setBlockState(obj, block.getDefaultState());
            expectDy(ctx, obj, 0.0, "resting_" + block.getTranslationKey() + "_double_slab");
            clear(ctx, obj, slab);
        }
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floorObjectsOnBottomSlabLowerHalf(TestContext ctx) {
        BlockPos slab = new BlockPos(2, 1, 2);
        BlockPos obj = new BlockPos(2, 2, 2);
        BlockState[] states = {
                Blocks.TORCH.getDefaultState(),
                Blocks.SOUL_TORCH.getDefaultState(),
                Blocks.STONE_PRESSURE_PLATE.getDefaultState(),
                Blocks.OAK_PRESSURE_PLATE.getDefaultState(),
                Blocks.OAK_FENCE_GATE.getDefaultState(),
                Blocks.LANTERN.getDefaultState().with(Properties.HANGING, false),
                Blocks.OAK_SIGN.getDefaultState()
        };
        for (BlockState state : states) {
            ctx.setBlockState(slab, slab(SlabType.BOTTOM));
            ctx.setBlockState(obj, state);
            expectDy(ctx, obj, -0.5, "floor_" + state.getBlock().getTranslationKey() + "_bottom_slab");
            clear(ctx, obj, slab);
        }
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void ceilingHungUnderTopSlabRaisesHalf(TestContext ctx) {
        BlockPos ceiling = new BlockPos(2, 3, 2);
        BlockPos obj = new BlockPos(2, 2, 2);
        BlockState[] states = {
                Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true),
                Blocks.SOUL_LANTERN.getDefaultState().with(Properties.HANGING, true),
                Blocks.SPORE_BLOSSOM.getDefaultState()
        };
        for (BlockState state : states) {
            ctx.setBlockState(ceiling, slab(SlabType.TOP));
            ctx.setBlockState(obj, state);
            expectDy(ctx, obj, 0.5, "ceiling_" + state.getBlock().getTranslationKey() + "_top_slab");
            clear(ctx, obj, ceiling);
        }
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void thinLayersOnBottomSlabStayFlush(TestContext ctx) {
        BlockPos slab = new BlockPos(2, 1, 2);
        BlockPos obj = new BlockPos(2, 2, 2);
        BlockState[] states = {Blocks.SNOW.getDefaultState(), Blocks.MOSS_CARPET.getDefaultState()};
        for (BlockState state : states) {
            ctx.setBlockState(slab, slab(SlabType.BOTTOM));
            ctx.setBlockState(obj, state);
            expectDy(ctx, obj, 0.0, "thin_" + state.getBlock().getTranslationKey() + "_bottom_slab");
            clear(ctx, obj, slab);
        }
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabMaterialSweepAllLowerHalf(TestContext ctx) {
        BlockPos slab = new BlockPos(2, 1, 2);
        BlockPos obj = new BlockPos(2, 2, 2);
        Block[] slabs = {
                Blocks.OAK_SLAB, Blocks.COBBLESTONE_SLAB, Blocks.SANDSTONE_SLAB, Blocks.BRICK_SLAB,
                Blocks.NETHER_BRICK_SLAB, Blocks.QUARTZ_SLAB, Blocks.PRISMARINE_SLAB,
                Blocks.DEEPSLATE_TILE_SLAB, Blocks.CUT_COPPER_SLAB
        };
        for (Block block : slabs) {
            ctx.setBlockState(slab, block.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM));
            ctx.setBlockState(obj, Blocks.STONE);
            expectDy(ctx, obj, -0.5, "material_" + block.getTranslationKey() + "_bottom_slab");
            clear(ctx, obj, slab);
        }
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stairsOnBottomSlabLowerHalf(TestContext ctx) {
        BlockPos slab = new BlockPos(2, 1, 2);
        BlockPos obj = new BlockPos(2, 2, 2);
        Block[] stairs = {Blocks.STONE_STAIRS, Blocks.OAK_STAIRS, Blocks.BIRCH_STAIRS};
        for (Block block : stairs) {
            ctx.setBlockState(slab, slab(SlabType.BOTTOM));
            ctx.setBlockState(obj, block.getDefaultState());
            expectDy(ctx, obj, -0.5, "stairs_" + block.getTranslationKey() + "_bottom_slab");
            clear(ctx, obj, slab);
        }
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bedHeadOnSlabLowersBothHalves(TestContext ctx) {
        BlockPos foot = new BlockPos(2, 2, 2);
        BlockPos head = foot.east();
        ctx.setBlockState(foot.down(), Blocks.STONE);
        ctx.setBlockState(head.down(), slab(SlabType.BOTTOM));
        ctx.setBlockState(foot, Blocks.RED_BED.getDefaultState()
                .with(Properties.BED_PART, BedPart.FOOT)
                .with(Properties.HORIZONTAL_FACING, Direction.EAST));
        ctx.setBlockState(head, Blocks.RED_BED.getDefaultState()
                .with(Properties.BED_PART, BedPart.HEAD)
                .with(Properties.HORIZONTAL_FACING, Direction.EAST));
        expectDy(ctx, foot, -0.5, "bed_head_on_slab_foot_follows");
        expectDy(ctx, head, -0.5, "bed_head_on_slab_head");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bedOnTopSlabStaysFlush(TestContext ctx) {
        BlockPos foot = new BlockPos(2, 2, 2);
        BlockPos head = foot.east();
        ctx.setBlockState(foot.down(), slab(SlabType.TOP));
        ctx.setBlockState(head.down(), slab(SlabType.TOP));
        ctx.setBlockState(foot, Blocks.RED_BED.getDefaultState()
                .with(Properties.BED_PART, BedPart.FOOT)
                .with(Properties.HORIZONTAL_FACING, Direction.EAST));
        ctx.setBlockState(head, Blocks.RED_BED.getDefaultState()
                .with(Properties.BED_PART, BedPart.HEAD)
                .with(Properties.HORIZONTAL_FACING, Direction.EAST));
        expectDy(ctx, foot, 0.0, "bed_top_slab_foot");
        expectDy(ctx, head, 0.0, "bed_top_slab_head");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void compoundDeepStackClampsAtMinusOne(TestContext ctx) {
        BlockPos base = new BlockPos(2, 1, 2);
        ctx.setBlockState(base, Blocks.STONE);
        ctx.setBlockState(base.up(), slab(SlabType.BOTTOM));
        ctx.setBlockState(base.up(2), Blocks.STONE);
        ctx.setBlockState(base.up(3), slab(SlabType.BOTTOM));
        ctx.setBlockState(base.up(4), Blocks.STONE);
        ctx.setBlockState(base.up(5), slab(SlabType.BOTTOM));
        ctx.setBlockState(base.up(6), Blocks.STONE);
        expectDy(ctx, base.up(), 0.0, "compound_l1_slab");
        expectDy(ctx, base.up(2), -0.5, "compound_l2_stone");
        expectDy(ctx, base.up(3), -0.5, "compound_l3_slab");
        expectDy(ctx, base.up(4), -1.0, "compound_l4_stone");
        for (int i = 1; i <= 6; i++) {
            double got = dy(ctx, base.up(i));
            ctx.assertTrue(got >= -1.0 - EPS && got <= EPS,
                    "compound clamp violation at level " + i + ": dy=" + got);
        }
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullBlockStackedOnLoweredFullBlockObservedFlushOn12111(TestContext ctx) {
        BlockPos base = new BlockPos(2, 1, 2);
        ctx.setBlockState(base, slab(SlabType.BOTTOM));
        ctx.setBlockState(base.up(), Blocks.STONE);
        ctx.setBlockState(base.up(2), Blocks.STONE);
        expectDy(ctx, base.up(), -0.5, "full_on_slab_lower");
        expectDy(ctx, base.up(2), 0.0, "full_on_lowered_full_OBSERVED_12111_DODO");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void carpetAndRedstoneSurviveOnSlabTops(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos support = new BlockPos(2, 1, 2);
        BlockPos obj = new BlockPos(2, 2, 2);
        BlockPos abs = ctx.getAbsolutePos(obj);
        ctx.setBlockState(support, slab(SlabType.BOTTOM));
        ctx.setBlockState(obj, Blocks.WHITE_CARPET);
        ctx.assertTrue(world.getBlockState(abs).canPlaceAt(world, abs), "white_carpet survives on bottom slab");
        ctx.setBlockState(obj, Blocks.REDSTONE_WIRE);
        ctx.assertTrue(world.getBlockState(abs).canPlaceAt(world, abs), "redstone_wire survives on bottom slab");
        ctx.setBlockState(support, slab(SlabType.TOP));
        ctx.setBlockState(obj, Blocks.WHITE_CARPET);
        ctx.assertTrue(world.getBlockState(abs).canPlaceAt(world, abs), "white_carpet survives on top slab");
        ctx.setBlockState(obj, Blocks.REDSTONE_WIRE);
        ctx.assertTrue(world.getBlockState(abs).canPlaceAt(world, abs), "redstone_wire survives on top slab");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void steppedConnectorsBreakAndFlatConnectorsRemain(TestContext ctx) {
        checkConnector(ctx, Blocks.OAK_FENCE, false, "oak_fence_step");
        checkConnector(ctx, Blocks.GLASS_PANE, false, "glass_pane_step");
        checkConnector(ctx, Blocks.NETHER_BRICK_FENCE, false, "nether_brick_fence_step");
        checkConnector(ctx, Blocks.COBBLESTONE_WALL, false, "cobblestone_wall_step");
        checkConnector(ctx, Blocks.STONE_BRICK_WALL, false, "stone_brick_wall_step");
        checkConnector(ctx, Blocks.OAK_FENCE, true, "oak_fence_flat");
        checkConnector(ctx, Blocks.GLASS_PANE, true, "glass_pane_flat");
        checkConnector(ctx, Blocks.COBBLESTONE_WALL, true, "cobblestone_wall_flat");
        ctx.complete();
    }

    private static void checkConnector(TestContext ctx, Block connector, boolean flat, String label) {
        ServerWorld world = ctx.getWorld();
        BlockPos supportA = new BlockPos(2, 1, 2);
        BlockPos supportB = new BlockPos(3, 1, 2);
        BlockPos a = supportA.up();
        BlockPos b = supportB.up();
        ctx.setBlockState(supportA, flat ? Blocks.STONE.getDefaultState() : slab(SlabType.BOTTOM));
        ctx.setBlockState(supportB, Blocks.STONE);
        ctx.setBlockState(a, connector.getDefaultState());
        ctx.setBlockState(b, connector.getDefaultState());
        BlockPos absA = ctx.getAbsolutePos(a);
        BlockPos absB = ctx.getAbsolutePos(b);
        BlockState recomputed = world.getBlockState(absA).getStateForNeighborUpdate(
                world, world, absA, Direction.EAST, absB, world.getBlockState(absB), world.getRandom());
        boolean connected = connectedEast(recomputed);
        Slabbed.LOGGER.info("CHECKLIST-CONNECTOR | {} | connected={}", label, connected);
        ctx.assertTrue(connected == flat, label + ": expected connected=" + flat + " got " + connected);
        clear(ctx, a, b, supportA, supportB);
    }

    private static boolean connectedEast(BlockState state) {
        BooleanProperty boolProp = net.minecraft.block.ConnectingBlock.FACING_PROPERTIES.get(Direction.EAST);
        if (state.contains(boolProp)) {
            return Boolean.TRUE.equals(state.get(boolProp));
        }
        EnumProperty<WallShape> wallProp = WallBlock.WALL_SHAPE_PROPERTIES_BY_DIRECTION.get(Direction.EAST);
        if (state.contains(wallProp)) {
            return state.get(wallProp) != WallShape.NONE;
        }
        return false;
    }
}
