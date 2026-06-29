package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SideShapeType;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.Thickness;
import net.minecraft.block.enums.WallShape;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

/**
 * Standard checklist headless expansion for the 1.21.1 line.
 *
 * <p>This class mirrors the portable server-side rows from the 26.1.2 checklist:
 * resting dy, compound matrix, survival predicates, and stepped connector logic.
 * Live-only visual/raycast rows stay in the checklist document, not here.
 */
public final class Slabbed1211StandardChecklistHeadlessTest {
    private static final double EPS = 1.0e-6;
    private static final Identifier TEST_TERRAIN_SLAB_ID = Identifier.of("terrain_slabs", "slabbed_test_slab");
    private static final Block TEST_TERRAIN_SLAB = new SlabBlock(AbstractBlock.Settings.copy(Blocks.STONE_SLAB));

    public static final class TerrainSlabsCompatTestEntrypoint implements ModInitializer {
        @Override
        public void onInitialize() {
            if (!Registries.BLOCK.containsId(TEST_TERRAIN_SLAB_ID)) {
                Registry.register(Registries.BLOCK, TEST_TERRAIN_SLAB_ID, TEST_TERRAIN_SLAB);
            }
        }
    }

    private static BlockState slab(SlabType type) {
        return Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, type);
    }

    private static BlockState downwardPointedDripstone(Thickness thickness) {
        return Blocks.POINTED_DRIPSTONE.getDefaultState()
                .with(Properties.VERTICAL_DIRECTION, Direction.DOWN)
                .with(Properties.THICKNESS, thickness);
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

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
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

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
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

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
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

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void terrainSlabsBottomLikeSurfaceExposesPlacementTopFace(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos support = new BlockPos(2, 1, 2);
        BlockPos supportAbs = ctx.getAbsolutePos(support);
        BlockState supportState = TEST_TERRAIN_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM);

        ctx.setBlockState(support, supportState);
        BlockState actualSupport = world.getBlockState(supportAbs);

        ctx.assertTrue(SlabSupport.isDirectCustomBottomLikeSurface(actualSupport),
                "test terrain slab must classify as a direct bottom-like compat surface: " + actualSupport);
        ctx.assertFalse(SlabSupport.isSupportingSlab(actualSupport),
                "test terrain slab must stay out of generic Slabbed support: " + actualSupport);
        ctx.assertTrue(SlabSupport.canTreatAsSolidTopFace(world, supportAbs),
                "test terrain slab top face must be accepted by Slabbed direct support law");
        ctx.assertTrue(actualSupport.isSideSolid(world, supportAbs, Direction.UP, SideShapeType.CENTER),
                "bottom-like terrain slab top face must be solid for vanilla center placement checks");
        ctx.assertTrue(actualSupport.isSideSolidFullSquare(world, supportAbs, Direction.UP),
                "bottom-like terrain slab top face must be a full square for vanilla placement checks");

        Slabbed.LOGGER.info("[MC1211_TERRAIN_SLAB_OBJECT_PLACEMENT_ROW] customState={} surfaceKind=bottom_like"
                        + " genericSupport={} canTreatTop={} sideSolidCenter={} sideSolidFullSquare={} result=GREEN",
                Registries.BLOCK.getId(actualSupport.getBlock()),
                SlabSupport.isSupportingSlab(actualSupport),
                SlabSupport.canTreatAsSolidTopFace(world, supportAbs),
                actualSupport.isSideSolid(world, supportAbs, Direction.UP, SideShapeType.CENTER),
                actualSupport.isSideSolidFullSquare(world, supportAbs, Direction.UP));
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
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

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void hangingLanternUnderTopSlabBridgedChainStaysFlush(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos chain = new BlockPos(2, 4, 2);
        BlockPos lantern = new BlockPos(2, 3, 2);
        BlockPos chainAbs = ctx.getAbsolutePos(chain);
        BlockPos lanternAbs = ctx.getAbsolutePos(lantern);

        ctx.setBlockState(chain.up(), slab(SlabType.TOP));
        ctx.setBlockState(chain, Blocks.CHAIN.getDefaultState().with(Properties.AXIS, Direction.Axis.Y));
        ctx.setBlockState(lantern, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));

        BlockState chainState = world.getBlockState(chainAbs);
        BlockState lanternState = world.getBlockState(lanternAbs);
        double chainDy = SlabSupport.getYOffset(world, chainAbs, chainState);
        double lanternDy = SlabSupport.getYOffset(world, lanternAbs, lanternState);

        ctx.assertTrue(Math.abs(chainDy - 0.5d) <= EPS,
                "top-slab-bridged Y-chain must keep ceiling dy=0.5, got " + chainDy);
        ctx.assertTrue(Math.abs(lanternDy) <= EPS,
                "hanging lantern below top-slab-bridged chain must stay grid-height, got " + lanternDy);

        VoxelShape chainOutline = chainState.getOutlineShape(world, chainAbs, ShapeContext.absent());
        VoxelShape chainRaycast = chainState.getRaycastShape(world, chainAbs);
        VoxelShape lanternOutline = lanternState.getOutlineShape(world, lanternAbs, ShapeContext.absent());
        VoxelShape lanternRaycast = lanternState.getRaycastShape(world, lanternAbs);
        ctx.assertTrue(!chainOutline.isEmpty(),
                "top-slab-bridged Y-chain must keep a selectable outline");
        ctx.assertTrue(chainOutline.getBoundingBox().maxY > 1.0d,
                "top-slab-bridged Y-chain outline must cover its raised ceiling bridge, maxY="
                        + chainOutline.getBoundingBox().maxY);
        ctx.assertTrue(!chainRaycast.isEmpty(),
                "top-slab-bridged Y-chain must keep a non-empty raycast shape");
        ctx.assertTrue(chainRaycast.getBoundingBox().maxY > 1.0d,
                "top-slab-bridged Y-chain raycast must cover its raised ceiling bridge, maxY="
                        + chainRaycast.getBoundingBox().maxY);
        ctx.assertTrue(!lanternOutline.isEmpty(),
                "hanging lantern below top-slab-bridged chain must keep a selectable outline");
        ctx.assertTrue(lanternOutline.getBoundingBox().maxY > 0.5d,
                "hanging lantern outline upper attachment must stay selectable, maxY="
                        + lanternOutline.getBoundingBox().maxY);
        ctx.assertTrue(!lanternRaycast.isEmpty(),
                "hanging lantern below top-slab-bridged chain must keep a non-empty raycast shape");
        ctx.assertTrue(lanternRaycast.getBoundingBox().maxY > 0.5d,
                "hanging lantern raycast upper attachment must stay selectable, maxY="
                        + lanternRaycast.getBoundingBox().maxY);

        Slabbed.LOGGER.info("[MC1211_LANTERN_CHAIN_ROW] case=top_slab_bridged_chain"
                + " chainDy=" + String.format(java.util.Locale.ROOT, "%.3f", chainDy)
                + " lanternDy=" + String.format(java.util.Locale.ROOT, "%.3f", lanternDy)
                + " chainOutlineY=" + String.format(java.util.Locale.ROOT, "%.3f", chainOutline.getBoundingBox().minY)
                + ".." + String.format(java.util.Locale.ROOT, "%.3f", chainOutline.getBoundingBox().maxY)
                + " chainRaycastY=" + String.format(java.util.Locale.ROOT, "%.3f", chainRaycast.getBoundingBox().minY)
                + ".." + String.format(java.util.Locale.ROOT, "%.3f", chainRaycast.getBoundingBox().maxY)
                + " lanternOutlineY=" + String.format(java.util.Locale.ROOT, "%.3f", lanternOutline.getBoundingBox().minY)
                + ".." + String.format(java.util.Locale.ROOT, "%.3f", lanternOutline.getBoundingBox().maxY)
                + " lanternRaycastY=" + String.format(java.util.Locale.ROOT, "%.3f", lanternRaycast.getBoundingBox().minY)
                + ".." + String.format(java.util.Locale.ROOT, "%.3f", lanternRaycast.getBoundingBox().maxY)
                + " result=GREEN");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void downwardPointedDripstoneColumnUnderTopSlabKeepsDescendantsGridHeight(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos upper = new BlockPos(2, 3, 2);
        BlockPos lower = new BlockPos(2, 2, 2);
        BlockPos upperAbs = ctx.getAbsolutePos(upper);
        BlockPos lowerAbs = ctx.getAbsolutePos(lower);

        ctx.setBlockState(upper.up(), slab(SlabType.TOP));
        ctx.setBlockState(upper, downwardPointedDripstone(Thickness.BASE));
        ctx.setBlockState(lower, downwardPointedDripstone(Thickness.TIP));

        BlockState upperState = world.getBlockState(upperAbs);
        BlockState lowerState = world.getBlockState(lowerAbs);
        double upperDy = SlabSupport.getYOffset(world, upperAbs, upperState);
        double lowerDy = SlabSupport.getYOffset(world, lowerAbs, lowerState);
        VoxelShape upperOutline = upperState.getOutlineShape(world, upperAbs, ShapeContext.absent());
        VoxelShape lowerOutline = lowerState.getOutlineShape(world, lowerAbs, ShapeContext.absent());

        Slabbed.LOGGER.info("[MC1211_DRIPSTONE_NUB_ROW] case=top_slab_downward_column"
                + " upperDy=" + String.format(java.util.Locale.ROOT, "%.3f", upperDy)
                + " lowerDy=" + String.format(java.util.Locale.ROOT, "%.3f", lowerDy)
                + " upperThickness=" + upperState.get(Properties.THICKNESS)
                + " lowerThickness=" + lowerState.get(Properties.THICKNESS)
                + " upperOutlineY=" + String.format(java.util.Locale.ROOT, "%.3f",
                        upperOutline.getBoundingBox().minY)
                + ".." + String.format(java.util.Locale.ROOT, "%.3f",
                        upperOutline.getBoundingBox().maxY)
                + " lowerOutlineY=" + String.format(java.util.Locale.ROOT, "%.3f",
                        lowerOutline.getBoundingBox().minY)
                + ".." + String.format(java.util.Locale.ROOT, "%.3f",
                        lowerOutline.getBoundingBox().maxY));

        ctx.assertTrue(Math.abs(upperDy - 0.5d) <= EPS,
                "direct downward pointed-dripstone segment under a top slab must attach upward; got "
                        + upperDy);
        ctx.assertTrue(Math.abs(lowerDy) <= EPS,
                "dripstone descendant must stay grid-height, not rise into the upper segment as a tiny nub; got "
                        + lowerDy);
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
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

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
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

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
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

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void bedHeadOnSlabLowersBothHalves(TestContext ctx) {
        BlockPos foot = new BlockPos(2, 2, 2);
        BlockPos head = foot.east();
        BlockState footState = Blocks.RED_BED.getDefaultState()
                .with(Properties.BED_PART, BedPart.FOOT)
                .with(Properties.HORIZONTAL_FACING, Direction.EAST);
        BlockState headState = Blocks.RED_BED.getDefaultState()
                .with(Properties.BED_PART, BedPart.HEAD)
                .with(Properties.HORIZONTAL_FACING, Direction.EAST);
        ctx.setBlockState(foot.down(), Blocks.STONE);
        ctx.setBlockState(head.down(), slab(SlabType.BOTTOM));
        ctx.setBlockState(foot, footState);
        ctx.setBlockState(head, headState);
        expectDy(ctx, foot, -0.5, "bed_head_on_slab_foot_follows");
        expectDy(ctx, head, -0.5, "bed_head_on_slab_head");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
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

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
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

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fullBlockStackedOnLoweredFullBlockFollowsOneStep(TestContext ctx) {
        BlockPos base = new BlockPos(2, 1, 2);
        ctx.setBlockState(base, slab(SlabType.BOTTOM));
        ctx.setBlockState(base.up(), Blocks.STONE);
        ctx.setBlockState(base.up(2), Blocks.STONE);
        expectDy(ctx, base.up(), -0.5, "full_on_slab_lower");
        expectDy(ctx, base.up(2), -0.5, "full_on_lowered_full");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void carpetAndRedstoneSurviveOnSlabTops(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos support = new BlockPos(2, 1, 2);
        BlockPos obj = new BlockPos(2, 2, 2);
        BlockPos abs = ctx.getAbsolutePos(obj);

        ctx.setBlockState(support, slab(SlabType.BOTTOM));
        ctx.setBlockState(obj, Blocks.WHITE_CARPET);
        ctx.assertTrue(world.getBlockState(abs).canPlaceAt(world, abs),
                "white_carpet must survive on bottom slab top");
        ctx.setBlockState(obj, Blocks.REDSTONE_WIRE);
        ctx.assertTrue(world.getBlockState(abs).canPlaceAt(world, abs),
                "redstone_wire must survive on bottom slab top");

        ctx.setBlockState(support, slab(SlabType.TOP));
        ctx.setBlockState(obj, Blocks.WHITE_CARPET);
        ctx.assertTrue(world.getBlockState(abs).canPlaceAt(world, abs),
                "white_carpet must survive on top slab top");
        ctx.setBlockState(obj, Blocks.REDSTONE_WIRE);
        ctx.assertTrue(world.getBlockState(abs).canPlaceAt(world, abs),
                "redstone_wire must survive on top slab top");
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void connectorStepRowsObservedConnectedOn1211(TestContext ctx) {
        // 1.21.1 preserves connector arms in this direct server recompute path. Keep this as an
        // explicit observed divergence from the newer connector-break checklist rows.
        checkConnector(ctx, Blocks.OAK_FENCE, true, "oak_fence_step_OBSERVED_1211");
        checkConnector(ctx, Blocks.GLASS_PANE, true, "glass_pane_step_OBSERVED_1211");
        checkConnector(ctx, Blocks.NETHER_BRICK_FENCE, true, "nether_brick_fence_step_OBSERVED_1211");
        checkConnector(ctx, Blocks.COBBLESTONE_WALL, true, "cobblestone_wall_step_OBSERVED_1211");
        checkConnector(ctx, Blocks.STONE_BRICK_WALL, true, "stone_brick_wall_step_OBSERVED_1211");
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
                Direction.EAST, world.getBlockState(absB), world, absA, absB);
        boolean connected = connectedEast(recomputed);
        Slabbed.LOGGER.info("CHECKLIST-CONNECTOR | {} | connected={}", label, connected);
        ctx.assertTrue(connected == flat, label + ": expected connected=" + flat + " got " + connected);
        clear(ctx, a, b, supportA, supportB);
    }

    private static boolean connectedEast(BlockState state) {
        if (state.contains(Properties.EAST)) {
            return Boolean.TRUE.equals(state.get(Properties.EAST));
        }
        if (state.contains(Properties.EAST_WALL_SHAPE)) {
            return state.get(Properties.EAST_WALL_SHAPE) != WallShape.NONE;
        }
        return false;
    }
}
