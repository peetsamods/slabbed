package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;

/**
 * Placement-landing regressions from the first live pass of the per-cell default (maintainer report,
 * 2026-09-07). Each row pins the value the placement-time landing must write for a scene the live
 * read lanes used to decide, now that a placed cell reads its stored height.
 */
public final class PlacementLandingRegressionTest {


    private static ActionResult useOnHit(PlayerEntity player, ItemStack stack, net.minecraft.util.hit.BlockHitResult hit) {
        player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, stack);
        return stack.useOnBlock(new net.minecraft.item.ItemUsageContext(player, net.minecraft.util.Hand.MAIN_HAND, hit));
    }

    private static boolean same(ServerWorld world, BlockPos pos, double expected) {
        return Math.abs(SlabSupport.getYOffset(world, pos, world.getBlockState(pos)) - expected) < 1.0e-6;
    }

    private static String cell(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        var fact = SlabAnchorAttachment.rawPlacementDyFact(world, pos);
        return pos.toShortString() + " state=" + state.getBlock()
                + " getYOffset=" + SlabSupport.getYOffset(world, pos, state)
                + " unstored=" + SlabSupport.getUnstoredYOffset(world, pos, state)
                + " fact=" + (fact.present() ? fact.valueOrNaN() : "none")
                + " modern=" + SlabAnchorAttachment.isModernPlacement(world, pos);
    }

    /** TS-VB: a full cube placed on a placed bottom-half Terrain Slabs slab must sit flush (-0.5). */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void fullCubeOnPlacedTerrainSlabBottomLandsLowered(TestContext h) {
        ServerWorld world = h.getWorld();
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        try {
            BlockPos ground = h.getAbsolutePos(new BlockPos(2, 1, 2));
            BlockPos ts = ground.up();
            BlockPos placed = ts.up();
            world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(ts, TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState()
                    .with(SlabBlock.TYPE, SlabType.BOTTOM).with(TerrainSlabsTestShim.GENERATED, false), Block.NOTIFY_ALL);
            PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
            player.setPosition(ts.getX() + 3.5d, ts.getY(), ts.getZ() + 0.5d);
            ActionResult result = PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(Items.DIRT), ts, Direction.UP);
            String report = "result=" + result + " | TS " + cell(world, ts) + " | placed " + cell(world, placed);
            System.out.println("[LANDING_REGRESSION] ts_full_cube " + report);
            h.assertTrue(result.isAccepted() && world.getBlockState(placed).isOf(Blocks.DIRT),
                    "premise: dirt did not place on the TS slab: " + report);
            double dy = SlabSupport.getYOffset(world, placed, world.getBlockState(placed));
            h.assertTrue(Math.abs(dy - (-0.5d)) < 1.0e-6,
                    "TS-VB landing: full cube on placed TS bottom slab reads " + dy + " (live rule -0.5): " + report);
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
        h.complete();
    }

    private static BlockState tsBottom() {
        return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.BOTTOM).with(TerrainSlabsTestShim.GENERATED, false);
    }

    /** The "TS-VB-TS" stack: a Terrain Slabs slab, a vanilla block on it, and pieces placed on or beside that block. */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void terrainSlabsStackMatrix(TestContext h) {
        ServerWorld world = h.getWorld();
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        try {
            PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
            // Column A: TS slab -> log placed on it (lowered) -> TS slab placed on the log's top face.
            BlockPos gA = h.getAbsolutePos(new BlockPos(1, 1, 1));
            world.setBlockState(gA, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(gA.up(), tsBottom(), Block.NOTIFY_ALL);
            player.setPosition(gA.getX() + 3.5d, gA.getY(), gA.getZ() + 0.5d);
            ActionResult a1 = PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(Items.OAK_LOG), gA.up(), Direction.UP);
            BlockPos log = gA.up(2);
            ActionResult a2 = PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(Items.STONE_SLAB), log, Direction.UP);
            System.out.println("[LANDING_REGRESSION] A_log_on_ts a1=" + a1 + " | " + cell(world, log));
            System.out.println("[LANDING_REGRESSION] A_vslab_on_log a2=" + a2 + " | " + cell(world, log.up()));
            // Column B: vanilla stone slab on top of the lowered log instead (vanilla slab control).
            BlockPos gB = h.getAbsolutePos(new BlockPos(4, 1, 1));
            world.setBlockState(gB, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(gB.up(), tsBottom(), Block.NOTIFY_ALL);
            player.setPosition(gB.getX() + 3.5d, gB.getY(), gB.getZ() + 0.5d);
            ActionResult b1 = PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(Items.OAK_LOG), gB.up(), Direction.UP);
            ActionResult b2 = PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(Items.STONE_SLAB), gB.up(2), Direction.UP);
            System.out.println("[LANDING_REGRESSION] B_vslab_on_log b1=" + b1 + " b2=" + b2 + " | log " + cell(world, gB.up(2)) + " | slab " + cell(world, gB.up(3)));
            // Column C: full block placed against the SIDE of the lowered log, aiming at its lower half.
            BlockPos gC = h.getAbsolutePos(new BlockPos(1, 1, 5));
            world.setBlockState(gC, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(gC.up(), tsBottom(), Block.NOTIFY_ALL);
            player.setPosition(gC.getX() + 3.5d, gC.getY(), gC.getZ() + 0.5d);
            ActionResult c1 = PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(Items.OAK_LOG), gC.up(), Direction.UP);
            BlockPos logC = gC.up(2);
            net.minecraft.util.hit.BlockHitResult sideLow = new net.minecraft.util.hit.BlockHitResult(
                    new net.minecraft.util.math.Vec3d(logC.getX() + 1.0d, logC.getY() + 0.2d, logC.getZ() + 0.5d), Direction.EAST, logC, false);
            ActionResult c2 = useOnHit(player, new ItemStack(Items.DIRT), sideLow);
            System.out.println("[LANDING_REGRESSION] C_side_low_on_log c1=" + c1 + " c2=" + c2 + " | log " + cell(world, logC) + " | side " + cell(world, logC.east()));
            // Column D: TS slab placed against the SIDE of the lowered log, aiming at its lower half.
            BlockPos gD = h.getAbsolutePos(new BlockPos(4, 1, 5));
            world.setBlockState(gD, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(gD.up(), tsBottom(), Block.NOTIFY_ALL);
            player.setPosition(gD.getX() + 3.5d, gD.getY(), gD.getZ() + 0.5d);
            ActionResult d1 = PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(Items.OAK_LOG), gD.up(), Direction.UP);
            BlockPos logD = gD.up(2);
            net.minecraft.util.hit.BlockHitResult sideLowD = new net.minecraft.util.hit.BlockHitResult(
                    new net.minecraft.util.math.Vec3d(logD.getX() + 1.0d, logD.getY() + 0.2d, logD.getZ() + 0.5d), Direction.EAST, logD, false);
            ActionResult d2 = useOnHit(player, new ItemStack(Items.STONE_SLAB), sideLowD);
            System.out.println("[LANDING_REGRESSION] D_vslab_side_low_on_log d1=" + d1 + " d2=" + d2 + " | log " + cell(world, logD) + " | side " + cell(world, logD.east()));
            // Column E: a Terrain Slabs slab ITEM placed on the lowered log's top face (TS-VB-TS).
            BlockPos gE = h.getAbsolutePos(new BlockPos(1, 1, 9));
            world.setBlockState(gE, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(gE.up(), tsBottom(), Block.NOTIFY_ALL);
            player.setPosition(gE.getX() + 3.5d, gE.getY(), gE.getZ() + 0.5d);
            ActionResult e1 = PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(Items.OAK_LOG), gE.up(), Direction.UP);
            BlockPos logE = gE.up(2);
            ActionResult e2 = PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(TerrainSlabsTestShim.TEST_TS_SLAB.asItem()), logE, Direction.UP);
            System.out.println("[LANDING_REGRESSION] E_tsslab_on_log e1=" + e1 + " e2=" + e2 + " | log " + cell(world, logE) + " | top " + cell(world, logE.up()) + " " + world.getBlockState(logE.up()));
            // Column F: a Terrain Slabs slab ITEM placed against the lowered log's side, lower half.
            BlockPos gF = h.getAbsolutePos(new BlockPos(4, 1, 9));
            world.setBlockState(gF, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(gF.up(), tsBottom(), Block.NOTIFY_ALL);
            player.setPosition(gF.getX() + 3.5d, gF.getY(), gF.getZ() + 0.5d);
            ActionResult f1 = PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(Items.OAK_LOG), gF.up(), Direction.UP);
            BlockPos logF = gF.up(2);
            ActionResult f2 = useOnHit(player, new ItemStack(TerrainSlabsTestShim.TEST_TS_SLAB.asItem()), new net.minecraft.util.hit.BlockHitResult(
                    new net.minecraft.util.math.Vec3d(logF.getX() + 1.0d, logF.getY() + 0.2d, logF.getZ() + 0.5d), Direction.EAST, logF, false));
            System.out.println("[LANDING_REGRESSION] F_tsslab_side_low_on_log f1=" + f1 + " f2=" + f2 + " | log " + cell(world, logF) + " | side " + cell(world, logF.east()) + " " + world.getBlockState(logF.east()));
            // Contract: vanilla blocks and vanilla slabs placed on or beside a Terrain-Slabs-lowered
            // full cube land in its frame (-0.5); Terrain Slabs' own slabs are never offset by Slabbed
            // (compat law: their height is theirs), so they read 0.0 wherever they are placed.
            h.assertTrue(same(world, log, -0.5d) && same(world, log.up(), -0.5d), "A: log on TS slab / vanilla slab on that log");
            h.assertTrue(same(world, gB.up(2), -0.5d) && same(world, gB.up(3), -0.5d), "B: vanilla slab on the lowered log");
            h.assertTrue(same(world, logC, -0.5d) && same(world, logC.east(), -0.5d), "C: full block beside the lowered log, lower half");
            h.assertTrue(same(world, logD, -0.5d) && same(world, logD.east(), -0.5d), "D: vanilla slab beside the lowered log, lower half");
            h.assertTrue(world.getBlockState(logE.up()).isOf(TerrainSlabsTestShim.TEST_TS_SLAB) && same(world, logE.up(), 0.0d),
                    "E: a Terrain Slabs slab on the lowered log stays at its own (un-offset) height");
            h.assertTrue(world.getBlockState(logF.east()).isOf(TerrainSlabsTestShim.TEST_TS_SLAB) && same(world, logF.east(), 0.0d),
                    "F: a Terrain Slabs slab beside the lowered log stays at its own (un-offset) height");
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
        h.complete();
    }

    private static BlockState ts(SlabType type, boolean generated) {
        return TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, type).with(TerrainSlabsTestShim.GENERATED, generated);
    }

    /**
     * Natural Terrain Slabs terrain: a full cube placed on a GENERATED bottom slab stays at grid height
     * (the world-hole guard), a full cube on a PLACED bottom slab lowers -0.5, a full cube on a generated
     * double stays flat, and objects on a generated bottom slab still sit on its half-height surface.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void naturalTerrainSlabsKeepTheWorldHoleGuard(TestContext h) {
        ServerWorld world = h.getWorld();
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        try {
            PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
            Object[][] cases = {
                {"cube_on_generated_bottom", ts(SlabType.BOTTOM, true), Items.DIRT, 0.0d},
                {"cube_on_placed_bottom", ts(SlabType.BOTTOM, false), Items.DIRT, -0.5d},
                {"cube_on_generated_double", ts(SlabType.DOUBLE, true), Items.DIRT, 0.0d},
                {"pot_on_generated_bottom", ts(SlabType.BOTTOM, true), Items.FLOWER_POT, -0.5d},
                {"torch_on_generated_bottom", ts(SlabType.BOTTOM, true), Items.TORCH, -0.5d},
                {"vanilla_slab_on_generated_bottom", ts(SlabType.BOTTOM, true), Items.STONE_SLAB, -0.5d},
                // A generated double is a bottom-like surface to the compat classifier: objects and slabs
                // seat on its half-height plane; a full cube never lowers on natural terrain.
                {"pot_on_generated_double", ts(SlabType.DOUBLE, true), Items.FLOWER_POT, -0.5d},
                {"torch_on_generated_double", ts(SlabType.DOUBLE, true), Items.TORCH, -0.5d},
                {"vanilla_slab_on_generated_double", ts(SlabType.DOUBLE, true), Items.STONE_SLAB, -0.5d},
                {"chest_on_generated_double", ts(SlabType.DOUBLE, true), Items.CHEST, -0.5d},
                {"pot_on_placed_top", ts(SlabType.TOP, false), Items.FLOWER_POT, 0.0d},
                {"cube_on_placed_top", ts(SlabType.TOP, false), Items.DIRT, 0.0d},
            };
            int i = 0;
            for (Object[] c : cases) {
                BlockPos g = h.getAbsolutePos(new BlockPos(1 + (i % 4) * 3, 1, 1 + (i / 4) * 3));
                i++;
                world.setBlockState(g, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                world.setBlockState(g.up(), (BlockState) c[1], Block.NOTIFY_ALL);
                player.setPosition(g.getX() + 3.5d, g.getY(), g.getZ() + 0.5d);
                ActionResult r = PlacementCaptureBoundaryGameTest.useOn(player,
                        new ItemStack((net.minecraft.item.Item) c[2]), g.up(), Direction.UP);
                BlockPos placed = g.up(2);
                double expected = (Double) c[3];
                double live = SlabSupport.getUnstoredYOffset(world, placed, world.getBlockState(placed));
                String report = c[0] + " r=" + r + " | owner " + cell(world, g.up()) + " | placed " + cell(world, placed);
                System.out.println("[LANDING_REGRESSION] " + report);
                h.assertTrue(r.isAccepted() && !world.getBlockState(placed).isAir(), "premise: " + report);
                h.assertTrue(same(world, placed, expected) && Math.abs(live - expected) < 1.0e-6,
                        c[0] + ": expected " + expected + " (stored read must equal the live rule): " + report);
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
        h.complete();
    }

    /**
     * A placement that REPLACES the owner's own cell (into grass or a snow layer, or a same-item merge
     * such as a second candle) lands at the owner cell's own height, never a cell above it.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void replacingTheOwnerCellLandsAtItsOwnHeight(TestContext h) {
        ServerWorld world = h.getWorld();
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        try {
            PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
            // {name, pedestal (null = a real dirt placement onto a bottom slab, -0.5), cell content, item, expected}
            Object[][] cases = {
                {"dirt_into_grass_on_dirt", Blocks.DIRT.getDefaultState(), Blocks.SHORT_GRASS.getDefaultState(), Items.DIRT, 0.0d},
                {"dirt_into_snow_layer_on_stone", Blocks.STONE.getDefaultState(), Blocks.SNOW.getDefaultState(), Items.DIRT, 0.0d},
                {"snow_onto_snow_layer_on_stone", Blocks.STONE.getDefaultState(), Blocks.SNOW.getDefaultState(), Items.SNOW, 0.0d},
                {"candle_onto_candle_on_stone", Blocks.STONE.getDefaultState(), Blocks.CANDLE.getDefaultState(), Items.CANDLE, 0.0d},
                {"torch_into_grass_on_dirt", Blocks.DIRT.getDefaultState(), Blocks.SHORT_GRASS.getDefaultState(), Items.TORCH, 0.0d},
                {"dirt_into_grass_on_lowered_dirt", null, Blocks.SHORT_GRASS.getDefaultState(), Items.DIRT, -0.5d},
                {"candle_onto_candle_on_bottom_slab", Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Blocks.CANDLE.getDefaultState(), Items.CANDLE, -0.5d},
            };
            int i = 0;
            for (Object[] c : cases) {
                BlockPos g = h.getAbsolutePos(new BlockPos(1 + (i % 4) * 3, 1, 1 + (i / 4) * 3));
                i++;
                world.setBlockState(g, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                BlockPos pedestal = g.up(), target = g.up(2);
                if (c[1] == null) {
                    world.setBlockState(pedestal, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_ALL);
                    player.setPosition(g.getX() + 3.5d, g.getY(), g.getZ() + 0.5d);
                    PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(Items.DIRT), pedestal, Direction.UP);
                    pedestal = g.up(2);
                    target = g.up(3);
                } else {
                    world.setBlockState(pedestal, (BlockState) c[1], Block.NOTIFY_ALL);
                }
                world.setBlockState(target, (BlockState) c[2], Block.NOTIFY_ALL);
                player.setPosition(g.getX() + 3.5d, g.getY(), g.getZ() + 0.5d);
                ActionResult r = PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack((net.minecraft.item.Item) c[3]), target, Direction.UP);
                double expected = (Double) c[4];
                double live = SlabSupport.getUnstoredYOffset(world, target, world.getBlockState(target));
                String report = c[0] + " r=" + r + " | pedestal " + cell(world, pedestal) + " | cell " + cell(world, target);
                System.out.println("[LANDING_REGRESSION] " + report);
                h.assertTrue(r.isAccepted() && world.getBlockState(target.up()).isAir(), "premise: " + report);
                h.assertTrue(same(world, target, expected) && Math.abs(live - expected) < 1.0e-6,
                        c[0] + ": expected " + expected + " (stored read must equal the live rule): " + report);
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
        h.complete();
    }

    /** Scaffolding placed on a bottom slab must stack upward when the column is clicked again. */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void scaffoldingStacksUpwardOnSlab(TestContext h) {
        ServerWorld world = h.getWorld();
        boolean previousFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        try {
            BlockPos ground = h.getAbsolutePos(new BlockPos(2, 1, 2));
            BlockPos slab = ground.up();
            BlockPos first = slab.up();
            BlockPos second = first.up();
            world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(slab, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_ALL);
            PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
            player.setPosition(slab.getX() + 3.5d, slab.getY(), slab.getZ() + 0.5d);
            ActionResult r1 = PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(Items.SCAFFOLDING, 16), slab, Direction.UP);
            String after1 = "r1=" + r1 + " | slab " + cell(world, slab) + " | first " + cell(world, first);
            System.out.println("[LANDING_REGRESSION] scaffold_step1 " + after1);
            h.assertTrue(r1.isAccepted() && world.getBlockState(first).isOf(Blocks.SCAFFOLDING),
                    "premise: scaffolding did not place on the slab: " + after1);
            // Vanilla: a SIDE-face click on scaffolding climbs the column and places on top (an UP-face
            // click extends sideways). Aim at the lowered piece's visible body.
            net.minecraft.util.hit.BlockHitResult sideHit = new net.minecraft.util.hit.BlockHitResult(
                    new net.minecraft.util.math.Vec3d(first.getX() + 1.0d, first.getY() + 0.2d, first.getZ() + 0.5d),
                    Direction.EAST, first, false);
            ActionResult r2 = useOnHit(player, new ItemStack(Items.SCAFFOLDING, 16), sideHit);
            StringBuilder scan = new StringBuilder();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 3; dy++) {
                BlockPos q = slab.add(dx, dy, dz);
                if (world.getBlockState(q).isOf(Blocks.SCAFFOLDING)) scan.append(" scaffold@").append(q.toShortString())
                        .append(world.getBlockState(q).toString().replace("Block{minecraft:scaffolding}", ""));
            }
            String after2 = "r2=" + r2 + " | first " + cell(world, first) + " | second " + cell(world, second) + " | scan:" + scan;
            System.out.println("[LANDING_REGRESSION] scaffold_step2 " + after2);
            h.assertTrue(r2.isAccepted() && world.getBlockState(second).isOf(Blocks.SCAFFOLDING),
                    "scaffolding did not stack upward on a slab column: " + after2);
            double baseDy = SlabSupport.getYOffset(world, first, world.getBlockState(first));
            double topDy = SlabSupport.getYOffset(world, second, world.getBlockState(second));
            h.assertTrue(Math.abs(baseDy - topDy) < 1.0e-6,
                    "climbed scaffolding is not in its base's frame: base=" + baseDy + " top=" + topDy + " | " + after2);
            // Control on flat stone.
            BlockPos flat = h.getAbsolutePos(new BlockPos(5, 1, 5));
            world.setBlockState(flat, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
            ActionResult c1 = PlacementCaptureBoundaryGameTest.useOn(player, new ItemStack(Items.SCAFFOLDING, 16), flat, Direction.UP);
            ActionResult c2 = useOnHit(player, new ItemStack(Items.SCAFFOLDING, 16),
                    new net.minecraft.util.hit.BlockHitResult(new net.minecraft.util.math.Vec3d(flat.getX() + 1.0d, flat.getY() + 1.5d, flat.getZ() + 0.5d),
                            Direction.EAST, flat.up(), false));
            String control = "c1=" + c1 + " c2=" + c2 + " | " + cell(world, flat.up()) + " | " + cell(world, flat.up(2));
            System.out.println("[LANDING_REGRESSION] scaffold_control " + control);
            h.assertTrue(world.getBlockState(flat.up(2)).isOf(Blocks.SCAFFOLDING), "control: scaffolding did not stack on flat stone: " + control);
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previousFrozen;
        }
        h.complete();
    }
}
