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
 * A legacy cell carries NO stored height: its lowering is answered by the live lanes. Dropping a
 * second slab into that same cell makes it a DOUBLE, and LAW.md forbids that upgrade from installing
 * a height of its own over the live answer, because a stored fact outranks the live read and the
 * cell would rise half a step and stay risen for good (LAW 1, the placement law).
 *
 * <p>The scene is the reported one, authored with {@code setBlockState} so the target cell is
 * deliberately factless, exactly like a build made before the store existed: a bottom slab whose
 * live answer is a lowered half step because a lowered double-slab carrier sits underneath it. The
 * upgrade runs through the real {@code useOnBlock} placement path, so the placement capture seam is
 * the code under test.
 *
 * <p>MUTATION that must redden this row alone: in {@code BlockItemPlacementIntentMixin}, restore the
 * same-cell DOUBLE upgrade's pre-2026-09-15 expression, which published
 * {@code Double.doubleToRawLongBits(0.0d)} whenever the prior cell carried no finite fact.
 */
public final class SameCellDoubleUpgradeKeepsLegacySeatTest {

    private static final double EPS = 1.0e-6d;

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void sameCellDoubleUpgradeOfAFactlessLoweredSlabInstallsNoHeight(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos base = ctx.getAbsolutePos(new BlockPos(3, 2, 3)).toImmutable();
        BlockPos carrierSupport = base.up();
        BlockPos carrier = carrierSupport.up();
        BlockPos cell = carrier.up();

        // A lowered double-slab carrier, built from the ground up: a bottom slab, the solid block it
        // lowers, and the double slab resting on that block. Nothing here authors a placement fact.
        world.setBlockState(base, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_ALL);
        world.setBlockState(carrierSupport, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(carrier, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE), Block.NOTIFY_ALL);
        world.setBlockState(cell, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_ALL);
        world.setBlockState(cell.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);

        // PREMISE, asserted loudly rather than assumed: the target cell must be factless and its
        // lowering must come from the live lanes. A scene that fails to produce that would let this
        // row pass while measuring nothing.
        boolean factBefore = SlabAnchorAttachment.rawPlacementDyFact(world, cell).present();
        boolean modernBefore = SlabAnchorAttachment.isModernPlacement(world, cell);
        double liveBefore = SlabSupport.getUnstoredYOffset(world, cell, world.getBlockState(cell));
        double readBefore = SlabSupport.getYOffset(world, cell, world.getBlockState(cell));
        if (factBefore || modernBefore || Math.abs(liveBefore + 0.5d) > EPS || Math.abs(readBefore + 0.5d) > EPS) {
            ctx.throwGameTestException("premise: the target cell must be a factless slab the live lanes lower half a step;"
                    + " storedFact=" + factBefore + " modernProvenance=" + modernBefore
                    + " live=" + liveBefore + " read=" + readBefore);
        }

        // The upgrade, through the real placement path: a matching slab item on the cell's up face.
        PlayerEntity player = ctx.createMockPlayer(GameMode.SURVIVAL);
        ActionResult result = PlacementCaptureBoundaryGameTest.useOn(
                player, new ItemStack(Items.STONE_SLAB), cell, Direction.UP);
        BlockState upgraded = world.getBlockState(cell);
        boolean sameCellDouble = upgraded.getBlock() == Blocks.STONE_SLAB
                && upgraded.contains(SlabBlock.TYPE)
                && upgraded.get(SlabBlock.TYPE) == SlabType.DOUBLE;
        if (!result.isAccepted() || !sameCellDouble) {
            ctx.throwGameTestException("premise: the click must upgrade the SAME cell to a double slab;"
                    + " result=" + result + " state=" + upgraded + " above=" + world.getBlockState(cell.up()));
        }

        // THE RULING: the upgrade publishes nothing, so the cell stays factless and the live lanes
        // keep answering for it.
        SlabAnchorAttachment.PlacementDyFact factAfter = SlabAnchorAttachment.rawPlacementDyFact(world, cell);
        boolean modernAfter = SlabAnchorAttachment.isModernPlacement(world, cell);
        if (factAfter.present() || modernAfter) {
            ctx.throwGameTestException("a same-cell double upgrade of a FACTLESS legacy cell must install no height:"
                    + " storedFact=" + factAfter.present() + " storedValue=" + factAfter.valueOrNaN()
                    + " modernProvenance=" + modernAfter
                    + " — a stored fact outranks the live read, so this legacy cell is now frozen at the stored value");
        }

        // THE PLAYER-VISIBLE CONSEQUENCE: the legacy build must not rise. The live answer governs and
        // is still at least the half step it was before the upgrade.
        double liveAfter = SlabSupport.getUnstoredYOffset(world, cell, upgraded);
        double readAfter = SlabSupport.getYOffset(world, cell, upgraded);
        if (Math.abs(readAfter - liveAfter) > EPS || readAfter > -0.5d + EPS) {
            ctx.throwGameTestException("the upgraded cell must read the live answer and stay lowered:"
                    + " readBefore=" + readBefore + " readAfter=" + readAfter + " liveAfter=" + liveAfter
                    + " — the legacy build rose half a block when the player merged the slab");
        }
        ctx.complete();
    }
}
