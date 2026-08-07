package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Live-reported bug (2026-07-03, recorder-traced): "terrain slabs cantilevered uses old broken
 * logic — placing another TS slab on top of the other one creates a gap and ghost-face DODO
 * depending on the angle you click." Traced via the recorder's own {@code same_cell_double_combine}
 * flag (already an existing red counter) to the following ROOT MECHANISM (verified against real
 * 1.21.11 bytecode, not reconstructed from memory). This test reproduces the mechanism precisely;
 * its own numbers land the resulting double on the CLICKED cell rather than the neighbour cell as
 * in the original live trace (a boundary-exact-hit special case in the up-face-edge inference
 * picks BOTTOM-intent instead of TOP-intent) — a different manifestation of the SAME gap, not a
 * claim of byte-identical coordinates to the live session:
 *
 * <ul>
 *   <li>Player holds a slab, clicks the TOP face of a LOWERED (visualDy=-0.5) single slab, near
 *       its edge. {@code slabbed$inferLoweredSideFromUpFaceHit} (added in {@code 80ac7737} so a
 *       player can place a NEW slab beside a lowered one) infers a horizontal
 *       {@code effectiveSide} from the edge proximity.</li>
 *   <li>The inferred neighbour cell ALREADY holds a pre-existing single slab of the SAME
 *       material (verified via real 1.21.11 bytecode: once {@code ItemPlacementContext} routes
 *       there — because {@code canReplace} on the ORIGINAL target fails for an up-face click —
 *       {@code SlabBlock.getPlacementState}'s {@code blockState.isOf(this)} branch combines
 *       UNCONDITIONALLY, with no half/Y check at all).</li>
 *   <li>Result: an unrelated pre-existing slab silently becomes a DOUBLE — the "gap + ghost-face
 *       DODO" the maintainer saw, and the {@code same_cell_double_combine} mismatch the recorder flagged.</li>
 * </ul>
 *
 * <p>Fix: {@code BlockItemPlacementIntentMixin} now skips the up-face-edge remap specifically
 * when it would land on an occupied same-material neighbour (a literal, deliberate horizontal
 * click against an occupied neighbour is untouched — this only suppresses the INFERRED case).
 */
public final class UpFaceEdgeCombineGuardTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void upFaceEdgeClickNearOccupiedNeighborDoesNotCombine(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        // Same fixture shape as UseOnCombineVsExtendPlacementTest's -0.5 lane: bottom slab ->
        // stone (lowered -0.5) -> TOP slab (renders -0.5, visible span [Y, Y+0.5]).
        BlockPos base = origin.add(3, 2, 1);
        world.setBlockState(base, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos fb = base.up();
        world.setBlockState(fb, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos top = fb.up();
        world.setBlockState(top, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);

        double visualDy = SlabSupport.getVisualYOffset(world, top, world.getBlockState(top));
        ctx.assertTrue(visualDy == -0.5,
                "fixture: TOP slab on a lowered full block must render lowered -0.5, got " + visualDy);

        // A PRE-EXISTING single slab (same material) in the direction the edge-click will infer.
        BlockPos neighbor = top.west();
        world.setBlockState(neighbor, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);

        PlayerEntity player = PlacementHarness.mockSlabPlayer(ctx, top.west(3));
        // TOP face (side=UP) near the WEST edge (localX=0.05 <= the 0.20 edge band), on the
        // slab's VISIBLE lowered top surface (world Y = top.getY() + 0.5).
        Vec3d edgeHit = new Vec3d(top.getX() + 0.05, top.getY() + 0.5, top.getZ() + 0.5);
        ActionResult result = PlacementHarness.useHeldOakSlab(
                world, player, top, Direction.UP, edgeHit);
        System.out.println("[USEON] upFaceEdge.occupiedNeighbor | clicked="
                + PlacementHarness.describe(world, top) + " | neighbor="
                + PlacementHarness.describe(world, neighbor) + " | result=" + result);

        BlockState neighborAfter = world.getBlockState(neighbor);
        ctx.assertTrue(!(neighborAfter.contains(SlabBlock.TYPE) && neighborAfter.get(SlabBlock.TYPE) == SlabType.DOUBLE),
                "the up-face-edge inference must NOT silently combine an unrelated pre-existing "
                        + "neighbour slab into a DOUBLE (live 'gap + ghost-face DODO' bug); got "
                        + PlacementHarness.describe(world, neighbor));
        BlockState clickedAfter = world.getBlockState(top);
        ctx.assertTrue(!(clickedAfter.contains(SlabBlock.TYPE) && clickedAfter.get(SlabBlock.TYPE) == SlabType.DOUBLE),
                "the originally-clicked lowered slab must also not silently combine; got "
                        + PlacementHarness.describe(world, top));
        ctx.complete();
    }

    // SECOND, DISTINCT failure surface (live TEST 20, actionId a9 in the recorder trace): the
    // inferred side-neighbour is EMPTY (unlike the test above), yet the up-face-edge inference's
    // own (effectiveSide, remappedY) combo makes vanilla's ItemPlacementContext decide the
    // ORIGINAL clicked slab is replaceable, combining it to DOUBLE in place — never routing to
    // the (empty) neighbour at all. The first guard (checking the neighbour's occupancy) cannot
    // catch this, since the neighbour was never occupied to begin with.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void upFaceEdgeClickWithEmptyNeighborDoesNotCombineClickedSlab(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        BlockPos base = origin.add(3, 2, 4);
        world.setBlockState(base, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos fb = base.up();
        world.setBlockState(fb, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos top = fb.up();
        world.setBlockState(top, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);

        double visualDy = SlabSupport.getVisualYOffset(world, top, world.getBlockState(top));
        ctx.assertTrue(visualDy == -0.5,
                "fixture: TOP slab on a lowered full block must render lowered -0.5, got " + visualDy);

        BlockPos neighbor = top.west();
        ctx.assertTrue(world.getBlockState(neighbor).isAir(),
                "fixture: the west neighbour must start EMPTY (isolates this from the occupied-neighbour case)");

        PlayerEntity player = PlacementHarness.mockSlabPlayer(ctx, top.west(3));
        // Exact-boundary edge hit (localX=0.05, y = the visible lowered top surface exactly):
        // forces the BOTTOM-intent branch of the remap, which — for an existing TYPE=TOP target
        // clicked from a horizontal side — satisfies vanilla's own combine condition directly.
        Vec3d edgeHit = new Vec3d(top.getX() + 0.05, top.getY() + 0.5, top.getZ() + 0.5);
        ActionResult result = PlacementHarness.useHeldOakSlab(
                world, player, top, Direction.UP, edgeHit);
        System.out.println("[USEON] upFaceEdge.emptyNeighbor | clicked="
                + PlacementHarness.describe(world, top) + " | neighbor="
                + PlacementHarness.describe(world, neighbor) + " | result=" + result);

        BlockState clickedAfter = world.getBlockState(top);
        ctx.assertTrue(!(clickedAfter.contains(SlabBlock.TYPE) && clickedAfter.get(SlabBlock.TYPE) == SlabType.DOUBLE),
                "the up-face-edge inference must NOT combine the CLICKED slab itself even when "
                        + "the inferred neighbour is empty (live 'placed under instead of on top' "
                        + "bug — a9 in the recorder trace); got "
                        + PlacementHarness.describe(world, top));
        ctx.complete();
    }

    // REGRESSION GUARD: a LITERAL, deliberate horizontal click (not an up-face-edge inference)
    // against an occupied same-material neighbour keeps vanilla's normal combine behaviour —
    // the fix must NOT suppress genuine, direct side-placement combines.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void literalHorizontalClickStillCombinesNormally(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        BlockPos ground = origin.add(5, 2, 1);
        world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos slab = ground.up();
        world.setBlockState(slab, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);

        PlayerEntity player = PlacementHarness.mockSlabPlayer(ctx, slab.north(3));
        // Literal horizontal (north) face click, upper-half fraction (bl=true per vanilla's
        // own canReplace: BOTTOM + horizontal + bl combines IN PLACE) — a genuine vanilla
        // combine gesture, unrelated to the up-face-edge (side=UP) inference path.
        Vec3d hit = new Vec3d(slab.getX() + 0.5, slab.getY() + 0.75, slab.getZ());
        ActionResult result = PlacementHarness.useHeldOakSlab(
                world, player, slab, Direction.NORTH, hit);
        ctx.assertTrue(result.isAccepted(), "literal horizontal-face click must place, got " + result);
        BlockState after = world.getBlockState(slab);
        ctx.assertTrue(after.get(SlabBlock.TYPE) == SlabType.DOUBLE,
                "a literal horizontal click against a BOTTOM slab at a lower-half fraction must "
                        + "still combine to DOUBLE (unaffected by the up-face-edge guard); got "
                        + PlacementHarness.describe(world, slab));
        ctx.complete();
    }
}
