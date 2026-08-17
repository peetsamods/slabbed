package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementDyAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Authored Terrain slab underside-plane coverage for placed and existing hangers. */
public final class LanternUnderTerrainSlabsHangerTest {

    private static final double EPS = 1.0e-6;

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hangingLanternUsesAuthoredTerrainUndersidePlane(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 4, 2);
        BlockPos lanternPos = support.down();
        double supportDy = SlabSupport.minResolvedDy() <= -1.5d ? -1.5d : -1.0d;

        for (SlabType type : new SlabType[]{SlabType.BOTTOM, SlabType.TOP, SlabType.DOUBLE}) {
            BlockState supportState = TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState()
                    .with(SlabBlock.TYPE, type);
            w.setBlockState(support, supportState, Block.NOTIFY_LISTENERS);
            w.setBlockState(lanternPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabPlacementDyAttachment.clear(w, lanternPos);
            ctx.assertTrue(SlabPlacementDyAttachment.writeBatch(
                            w, java.util.Map.of(support, Double.doubleToRawLongBits(supportDy))),
                    "fixture: authored Terrain support height must publish for " + type);
            PlayerEntity player = PlacementHarness.mockPlayerHolding(
                    ctx, support.north(3), new ItemStack(Blocks.LANTERN.asItem(), 1));
            double undersideOffset = type == SlabType.TOP ? 0.5d : 0.0d;
            Vec3d hit = new Vec3d(
                    support.getX() + 0.5d,
                    support.getY() + supportDy + undersideOffset,
                    support.getZ() + 0.5d);

            ActionResult result = PlacementHarness.useHeldItem(
                    w, player, support, Direction.DOWN, hit);

            ctx.assertTrue(result.isAccepted(),
                    "hanging lantern placement under authored Terrain " + type + " must succeed");
            BlockState lantern = w.getBlockState(lanternPos);
            ctx.assertTrue(lantern.isOf(Blocks.LANTERN)
                            && lantern.contains(Properties.HANGING)
                            && lantern.get(Properties.HANGING),
                    "the lantern must occupy the hanging state for " + type + "; got " + lantern);
            double expectedDy = supportDy + undersideOffset;
            double stored = SlabPlacementDyAttachment.storedDy(w, lanternPos);
            ctx.assertTrue(Double.doubleToRawLongBits(stored)
                            == Double.doubleToRawLongBits(expectedDy),
                    "hanging lantern must freeze to the authored Terrain underside plane for "
                            + type + "; expected " + expectedDy + " got " + stored);
            double visual = SlabSupport.getYOffset(w, lanternPos, lantern);
            ctx.assertTrue(Math.abs(visual - expectedDy) <= EPS,
                    "hanging lantern visual dy must match the Terrain underside for " + type
                            + "; expected " + expectedDy + " got " + visual);
        }
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void existingHangingLanternUsesAuthoredTerrainTopUnderside(TestContext ctx) {
        assertExistingHangerUsesAuthoredTopUnderside(
                ctx,
                Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true),
                "hanging lantern");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void existingHangingRootsUseAuthoredTerrainTopUnderside(TestContext ctx) {
        assertExistingHangerUsesAuthoredTopUnderside(
                ctx,
                Blocks.HANGING_ROOTS.getDefaultState(),
                "hanging roots");
    }

    private static void assertExistingHangerUsesAuthoredTopUnderside(
            TestContext ctx,
            BlockState hangerState,
            String label
    ) {
        ServerWorld w = ctx.getWorld();
        BlockPos support = ctx.getAbsolutePos(BlockPos.ORIGIN).add(5, 4, 2);
        BlockPos hanger = support.down();
        double supportDy = SlabSupport.minResolvedDy() <= -1.5d ? -1.5d : -1.0d;
        BlockState supportState = TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState()
                .with(SlabBlock.TYPE, SlabType.TOP);
        w.setBlockState(support, supportState, Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabPlacementDyAttachment.writeBatch(
                        w, java.util.Map.of(support, Double.doubleToRawLongBits(supportDy))),
                "fixture: authored Terrain TOP height must publish");
        w.setBlockState(hanger, hangerState, Block.NOTIFY_LISTENERS);

        double expectedDy = supportDy + 0.5d;
        double actualDy = SlabSupport.getYOffset(w, hanger, w.getBlockState(hanger));
        ctx.assertTrue(Math.abs(actualDy - expectedDy) <= EPS,
                label + " must use the authored Terrain TOP underside; expected "
                        + expectedDy + " got " + actualDy);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lanternUnderAuthoredTerrainBottomSlabFollowsStoredHeight(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos vanillaBottomSlabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3);
        BlockPos dirtPos = vanillaBottomSlabPos.up();
        BlockPos tsSlabPos = dirtPos.east();      // TS bottom slab, cantilevered, anchors via adjacency to dirt
        BlockPos lanternPos = tsSlabPos.down();   // lantern hangs from the TS slab underside, air below

        w.setBlockState(vanillaBottomSlabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtPos, w.getBlockState(dirtPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, dirtPos), "setup: dirt must anchor on the bottom slab");

        w.setBlockState(tsSlabPos, TerrainSlabsTestShim.TEST_TS_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, tsSlabPos, w.getBlockState(tsSlabPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, tsSlabPos),
                "setup: the cantilevered Terrain bottom slab must be authored and anchored");
        double supportDy = SlabSupport.getYOffset(w, tsSlabPos, w.getBlockState(tsSlabPos));
        ctx.assertTrue(Math.abs(supportDy + 0.5d) <= EPS,
                "setup: the authored Terrain bottom slab must carry dy=-0.5; got " + supportDy);

        w.setBlockState(lanternPos, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true),
                Block.NOTIFY_LISTENERS);
        double lanternDy = SlabSupport.getYOffset(w, lanternPos, w.getBlockState(lanternPos));
        ctx.assertTrue(Math.abs(lanternDy - supportDy) <= EPS,
                "a lantern under an authored Terrain bottom slab must follow its stored underside; "
                        + "support=" + supportDy + " lantern=" + lanternDy);
        ctx.complete();
    }

    // REGRESSION GUARD (the anti-jam behaviour the lane exists for): a lantern under a lowered
    // VANILLA bottom slab must STILL follow it down to -0.5, or the lowered slab underside clips
    // into the lantern top. The fix must change ONLY the Terrain-Slabs case.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lanternUnderLoweredVanillaBottomSlabStillFollows(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos vanillaBottomSlabPos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 4);
        BlockPos dirtPos = vanillaBottomSlabPos.up();
        BlockPos supportSlabPos = dirtPos.east();   // VANILLA bottom slab, cantilevered, anchored via adjacency
        BlockPos lanternPos = supportSlabPos.down();

        w.setBlockState(vanillaBottomSlabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, dirtPos, w.getBlockState(dirtPos));

        w.setBlockState(supportSlabPos, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, supportSlabPos, w.getBlockState(supportSlabPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(w, supportSlabPos),
                "setup: the vanilla support slab must anchor");
        double supportDy = SlabSupport.getYOffset(w, supportSlabPos, w.getBlockState(supportSlabPos));
        ctx.assertTrue(Math.abs(supportDy + 0.5) <= EPS,
                "setup: the vanilla support slab must itself render -0.5 (Slabbed lowers it, unlike TS); got " + supportDy);

        w.setBlockState(lanternPos, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true),
                Block.NOTIFY_LISTENERS);
        double lanternDy = SlabSupport.getYOffset(w, lanternPos, w.getBlockState(lanternPos));
        ctx.assertTrue(Math.abs(lanternDy + 0.5) <= EPS,
                "regression: a lantern under a lowered VANILLA slab must STILL follow it to -0.5 "
                        + "(anti-jam) -- the fix must only change the Terrain-Slabs case; got " + lanternDy);
        ctx.complete();
    }
}
