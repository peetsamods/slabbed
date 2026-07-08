package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * F5 port (audit STATE_DEFENSE_DIVERGENCE_2026-07-07): WATERLOG READ SYMMETRY. Anchor-family reads
 * (isAnchored / isFrozenFlat / isCompoundFullBlockAnchor) ignore fluid — an anchored-lowered slab
 * keeps its dy when waterlogged. Marker/carrier reads were fluid-gated, so a BUCKET at a marked slab
 * popped the slab to flush and popped its dependents (torch -1.5 -> -0.5) with no player action at
 * their cells — Maintainer's never-pop law violated by a water flip. The waterlog property transform
 * itself PRESERVES the attachments (audit Verified-CLEAN); the defects were read-side fluid gates
 * plus ONE write-belt effect (the carrier state predicate's fluid term de-qualified the carrier on
 * the flip), so the fix makes every attachment read AND the shared state predicates fluid-blind,
 * matching the anchor reference.
 */
public final class WaterlogReadSymmetryTest {

    private static final double EPS = 1.0e-6;

    private static void place(GameTestHelper helper, ItemStack stack, BlockPos clicked, Direction face) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked)
                .add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    private static double dy(ServerLevel w, BlockPos pos) {
        return SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
    }

    private static void assertDy(GameTestHelper helper, ServerLevel w, BlockPos pos, double expected,
                                 String message) {
        double got = dy(w, pos);
        if (Math.abs(got - expected) > EPS) {
            throw helper.assertionException(message + ": expected dy " + expected + ", got " + got);
        }
    }

    /** The same WATERLOGGED property flip a bucket/sponge performs at the cell. */
    private static void setWaterlogged(ServerLevel w, BlockPos pos, boolean waterlogged) {
        BlockState state = w.getBlockState(pos);
        w.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, waterlogged), Block.UPDATE_ALL);
    }

    private static void bottomSlab(ServerLevel w, BlockPos pos) {
        w.setBlock(pos, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
    }

    /** Compound-visible STONE bottom slab (-1.0) beside a genuine compound stack (D3/F4 scene). */
    private static BlockPos buildMarkedLowerSlab(GameTestHelper helper, ServerLevel w) {
        BlockPos base = helper.absolutePos(new BlockPos(3, 1, 2));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(1));
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(3));
        BlockPos fb = base.above(4);
        w.setBlock(fb, Blocks.STONE.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, fb, w.getBlockState(fb));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(w, fb, w.getBlockState(fb));
        BlockPos support = fb.west();
        bottomSlab(w, support);
        SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(w, support, w.getBlockState(support),
                fb, w.getBlockState(fb));
        double supportDy = dy(w, support);
        if (Math.abs(supportDy + 1.0) > EPS) {
            throw helper.assertionException("scene premise: marked slab must read -1.0, got " + supportDy);
        }
        return support;
    }

    /** F5 core: waterlogging the MARKED slab must move NOTHING — not the slab, not its dependent. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void waterloggingAMarkedSlabMovesNothing(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = buildMarkedLowerSlab(helper, w);
        BlockPos torch = slab.above();
        place(helper, new ItemStack(Items.TORCH), slab, Direction.UP);
        if (w.getBlockState(torch).isAir()) {
            throw helper.assertionException("premise: the torch placement must succeed");
        }
        assertDy(helper, w, torch, -1.5, "premise: torch seats at -1.5 on the marked slab");
        setWaterlogged(w, slab, true);
        assertDy(helper, w, slab, -1.0,
                "F5: a bucket at the marked slab must not pop the slab (anchor reads ignore fluid — symmetry)");
        assertDy(helper, w, torch, -1.5,
                "F5: the dependent torch must not move when the slab below is waterlogged");
        helper.succeed();
    }

    /** Fence-gate dependent (the fenceWall support-helper gate): waterlogging the marked slab moves nothing. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceGateDependentSurvivesWaterloggedMarkedSlab(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = buildMarkedLowerSlab(helper, w);
        BlockPos gate = slab.above();
        place(helper, new ItemStack(Items.OAK_FENCE_GATE), slab, Direction.UP);
        if (w.getBlockState(gate).isAir()) {
            throw helper.assertionException("premise: the fence gate placement must succeed");
        }
        assertDy(helper, w, gate, -1.5, "premise: the gate seats at -1.5 on the marked slab");
        setWaterlogged(w, slab, true);
        assertDy(helper, w, gate, -1.5,
                "F5: the dependent gate must not move when the marked slab below is waterlogged");
        helper.succeed();
    }

    /** Floor-button dependent (the fenceWall support-helper gate at its own choke point). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floorButtonDependentSurvivesWaterloggedMarkedSlab(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = buildMarkedLowerSlab(helper, w);
        BlockPos button = slab.above();
        place(helper, new ItemStack(Items.STONE_BUTTON), slab, Direction.UP);
        if (w.getBlockState(button).isAir()) {
            throw helper.assertionException("premise: the button placement must succeed");
        }
        assertDy(helper, w, button, -1.5, "premise: the floor button seats at -1.5 on the marked slab");
        setWaterlogged(w, slab, true);
        assertDy(helper, w, button, -1.5,
                "F5: the dependent button must not move when the marked slab below is waterlogged");
        helper.succeed();
    }

    /** The side-UPPER marker predicate: a waterlogged marked TOP slab keeps -1.0. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void waterloggingAMarkedUpperSlabMovesNothing(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(3, 1, 2));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(1));
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(3));
        BlockPos fb = base.above(4);
        w.setBlock(fb, Blocks.STONE.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, fb, w.getBlockState(fb));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(w, fb, w.getBlockState(fb));
        BlockPos slab = fb.west();
        w.setBlock(slab, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        SlabAnchorAttachment.addCompoundVisibleSideUpperSlab(w, slab, w.getBlockState(slab),
                fb, w.getBlockState(fb));
        assertDy(helper, w, slab, -1.0, "premise: the marked side-UPPER top slab reads -1.0");
        setWaterlogged(w, slab, true);
        assertDy(helper, w, slab, -1.0,
                "F5: waterlogging the marked upper slab must not pop it (side-UPPER predicate fluid-blind)");
        helper.succeed();
    }

    /** De-waterlog (sponge) direction: the round-trip is a geometric no-op. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void spongeRoundTripIsAGeometricNoOp(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = buildMarkedLowerSlab(helper, w);
        setWaterlogged(w, slab, true);
        setWaterlogged(w, slab, false);
        assertDy(helper, w, slab, -1.0,
                "F5 control: after waterlog+sponge the marked slab reads -1.0 again (attachment survived)");
        helper.succeed();
    }

    /** Carrier symmetry: a persistent-lowered CARRIER slab keeps -0.5 when waterlogged. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void waterloggingACarrierSlabKeepsItLowered(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        // Genuine lowered stack: ground, slab(0), stone(-0.5), slab(-0.5 via carrier-below lane).
        BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(1));
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        BlockPos carrier = base.above(3);
        bottomSlab(w, carrier);
        assertDy(helper, w, carrier, -0.5, "premise: the stacked slab reads -0.5");
        BlockPos torch = carrier.above();
        place(helper, new ItemStack(Items.TORCH), carrier, Direction.UP);
        if (w.getBlockState(torch).isAir()) {
            throw helper.assertionException("premise: the torch placement must succeed");
        }
        assertDy(helper, w, torch, -1.0, "premise: torch seats at -1.0 on the -0.5 slab");
        setWaterlogged(w, carrier, true);
        assertDy(helper, w, carrier, -0.5,
                "F5: waterlogging the lowered slab must not pop it to flush");
        assertDy(helper, w, torch, -1.0,
                "F5: the dependent torch must not move when its support slab is waterlogged (floorTorch helper gate)");
        helper.succeed();
    }

    /**
     * The symmetry REFERENCE (green before and after): an ANCHORED lowered slab ignores fluid. A slab
     * must be genuinely LOWERED at anchor time (a flush slab freeze-flats instead), so anchor the
     * -0.5 slab atop a lowered stack — the isAnchored leg of the slab branch has no fluid gate.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void anchoredSlabWaterlogReferenceStaysLowered(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(1));
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        BlockPos slab = base.above(3);
        bottomSlab(w, slab);
        SlabAnchorAttachment.freezeLoweredOnPlace(w, slab, w.getBlockState(slab));
        if (!SlabAnchorAttachment.isAnchored(w, slab)) {
            throw helper.assertionException("scene premise: freezeLoweredOnPlace must ANCHOR the lowered slab");
        }
        double anchoredDy = dy(w, slab);
        if (Math.abs(anchoredDy + 0.5) > EPS) {
            throw helper.assertionException("scene premise: the anchored lowered slab must read -0.5, got " + anchoredDy);
        }
        setWaterlogged(w, slab, true);
        if (!SlabAnchorAttachment.isAnchored(w, slab)) {
            throw helper.assertionException(
                    "F5 write half: the ANCHOR must survive the waterlog flip (the strip, not a read gate)");
        }
        assertDy(helper, w, slab, -0.5,
                "reference: anchor reads ignore fluid — the anchored slab stays lowered when waterlogged");
        helper.succeed();
    }

    /** No over-lowering: an UNMARKED, unanchored waterlogged slab on flush ground stays flush. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void plainWaterloggedSlabStaysFlushControl(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos slab = ground.above();
        w.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, slab);
        setWaterlogged(w, slab, true);
        assertDy(helper, w, slab, 0.0,
                "control: a plain waterlogged bottom slab on flush ground stays flush (no over-lowering)");
        helper.succeed();
    }
}
