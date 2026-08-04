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
import net.minecraft.world.level.block.state.properties.Half;
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
        place(helper, stack, clicked, face, false);
    }

    /**
     * @param aimAtLoweredSourceFace aim the click Y at the source's TRANSLATED visible face instead of
     *         the vanilla grid-face center. The offset-aware raycast can only return a hit on a lowered
     *         source's translated body, so against a negative-dy source the vanilla-center aim is a
     *         coordinate no player can produce — and the side-hit SlabType classifier now reads it
     *         against the translated midpoint. Only the F5c side-carrier scenes opt in.
     */
    private static void place(GameTestHelper helper, ItemStack stack, BlockPos clicked, Direction face,
                              boolean aimAtLoweredSourceFace) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked)
                .add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        if (aimAtLoweredSourceFace) {
            double sourceDy = dy(helper.getLevel(), clicked);
            hit = new Vec3(hit.x, clicked.getY() + sourceDy + 0.25, hit.z);
        }
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

    // ── F5b (deep-sweep tranche): the fluid-blind invariant extends to WRITERS, lane/support-role
    // derivations, and interaction surfaces — the two-authority split had moved one hop away. ──

    /** Owner-top marker family (the surviving-mutant rider from F5a): waterlog moves nothing. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void waterloggingAnOwnerTopSlabMovesNothing(GameTestHelper helper) {
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
        BlockPos slab = fb.above();
        bottomSlab(w, slab);
        SlabAnchorAttachment.addCompoundVisibleOwnerTopSlab(w, slab, w.getBlockState(slab),
                fb, w.getBlockState(fb));
        double slabDy = dy(w, slab);
        if (Math.abs(slabDy + 1.0) > EPS) {
            throw helper.assertionException("scene premise: the owner-top slab must read -1.0, got " + slabDy);
        }
        setWaterlogged(w, slab, true);
        assertDy(helper, w, slab, -1.0,
                "F5b: waterlogging the owner-top slab must not pop it (owner-top predicate fluid-blind)");
        helper.succeed();
    }

    /**
     * WRITER symmetry (deep-sweep finding 1): a slab PLACED underwater on a lowered support reads
     * -0.5 (fluid-blind reads) but the freeze-on-place writer refused waterlogged states — so it was
     * never anchored and popped flush when the support broke, while its dry twin held. Never-pop must
     * not depend on the water the slab was placed into.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabPlacedUnderwaterOnLoweredSupportNeverPops(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(1));
        BlockPos support = base.above(2);
        w.setBlock(support, Blocks.STONE.defaultBlockState(), 2);
        double supportDy = dy(w, support);
        if (Math.abs(supportDy + 0.5) > EPS) {
            throw helper.assertionException("scene premise: the support must read -0.5, got " + supportDy);
        }
        BlockPos placedPos = support.above();
        w.setBlock(placedPos, Blocks.WATER.defaultBlockState(), 2);
        place(helper, new ItemStack(Items.STONE_SLAB), support, Direction.UP);
        BlockState placed = w.getBlockState(placedPos);
        if (!(placed.getBlock() instanceof SlabBlock)
                || !placed.getValue(BlockStateProperties.WATERLOGGED)) {
            throw helper.assertionException("scene premise: a WATERLOGGED slab must place into the water cell, got "
                    + placed);
        }
        assertDy(helper, w, placedPos, -0.5, "premise: the underwater-placed slab reads -0.5");
        if (!SlabAnchorAttachment.isAnchored(w, placedPos)) {
            throw helper.assertionException(
                    "F5b WRITE half: the underwater placement must ANCHOR like its dry twin (writer fluid-blind)");
        }
        w.destroyBlock(support, false);
        assertDy(helper, w, placedPos, -0.5,
                "F5b: breaking the support must not pop the underwater-placed slab (never-pop)");
        helper.succeed();
    }

    /**
     * Vertical-lane SUPPORT role (deep-sweep finding 2): bucketing a lowered TOP-type support must
     * not pop the unmarked slab riding it (isLoweredTopLikeSlabCarrier's gate fired before its
     * marker read).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void waterloggingALoweredTopSupportKeepsTheSlabAboveLowered(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(3, 1, 2));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(1));
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        BlockPos topSupport = helper.absolutePos(new BlockPos(2, 3, 2));
        w.setBlock(topSupport, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        double supportDy = dy(w, topSupport);
        if (Math.abs(supportDy + 0.5) > EPS) {
            throw helper.assertionException("scene premise: the side-lowered TOP support must read -0.5, got " + supportDy);
        }
        // DRY BASELINE first: if the rider does not follow a DRY side-lowered TOP support either,
        // the gap is a pre-existing lane hole, NOT a fluid asymmetry — and this scene must not
        // blame F5 for it.
        BlockPos rider = topSupport.above();
        place(helper, new ItemStack(Items.STONE_SLAB), topSupport, Direction.UP);
        if (!(w.getBlockState(rider).getBlock() instanceof SlabBlock)) {
            throw helper.assertionException("scene premise: the rider slab must place above the support, got "
                    + w.getBlockState(rider));
        }
        double dryRiderDy = dy(w, rider);
        if (Math.abs(dryRiderDy + 0.5) > EPS) {
            throw helper.assertionException(
                    "DRY BASELINE: the rider on a DRY side-lowered TOP support reads " + dryRiderDy
                            + " (pre-existing lane gap, not a fluid asymmetry)");
        }
        w.destroyBlock(rider, false);
        setWaterlogged(w, topSupport, true);
        place(helper, new ItemStack(Items.STONE_SLAB), topSupport, Direction.UP);
        if (!(w.getBlockState(rider).getBlock() instanceof SlabBlock)) {
            throw helper.assertionException("scene premise: the wet-case rider must place above the support, got "
                    + w.getBlockState(rider));
        }
        assertDy(helper, w, rider, -0.5,
                "F5b: a slab placed ON a waterlogged lowered TOP support must follow it down like the dry twin");
        helper.succeed();
    }

    /**
     * Lane-OWNER role (deep-sweep finding 3a): bucketing a marked lane owner must not pop an
     * unmarked side member inheriting -0.5 through the live side-lane BFS.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void waterloggingALaneOwnerKeepsSideMembersLowered(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos owner = buildMarkedLowerSlab(helper, w);
        BlockPos member = owner.west();
        bottomSlab(w, member);   // over AIR: the live cantilever/side lane, no attachments
        double memberDy = dy(w, member);
        if (memberDy > -EPS) {
            throw helper.assertionException("scene premise: the side member must inherit a lowered dy, got " + memberDy);
        }
        setWaterlogged(w, owner, true);
        assertDy(helper, w, member, memberDy,
                "F5b: bucketing the lane owner must not pop the inheriting side member (owner role fluid-blind)");
        helper.succeed();
    }

    /**
     * Anchored deep-cantilever magnitude (deep-sweep finding 5a): bucketing an anchored -1.0
     * cantilever slab beside a compound stack must not half-pop it to -0.5.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void waterloggingAnAnchoredCantileverKeepsItsDepth(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos support = buildMarkedLowerSlab(helper, w);
        BlockPos fb = support.east();
        BlockPos placedPos = fb.north();
        place(helper, new ItemStack(Items.STONE_SLAB), fb, Direction.NORTH);
        BlockState placed = w.getBlockState(placedPos);
        if (!(placed.getBlock() instanceof SlabBlock)) {
            throw helper.assertionException("scene premise: the side placement must land at fb.north(), got " + placed);
        }
        double placedDy = dy(w, placedPos);
        if (placedDy > -1.0 + EPS) {
            throw helper.assertionException("scene premise: the cantilever slab must read the deep -1.0, got " + placedDy);
        }
        setWaterlogged(w, placedPos, true);
        assertDy(helper, w, placedPos, placedDy,
                "F5b: bucketing the anchored cantilever must not half-pop it (deep-magnitude read fluid-blind)");
        helper.succeed();
    }

    /**
     * Interaction-surface triad (deep-sweep finding 4): a waterlogged marked slab renders and
     * outlines lowered — its interaction shape must agree (the dy-triad law), not refuse the
     * lawful-lowered fallback because of the fluid.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void waterloggedMarkedSlabInteractionShapeMatchesOutline(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = buildMarkedLowerSlab(helper, w);
        setWaterlogged(w, slab, true);
        BlockState state = w.getBlockState(slab);
        var outline = state.getShape(w, slab);
        var interaction = state.getInteractionShape(w, slab);
        if (outline.isEmpty()) {
            throw helper.assertionException("premise: the outline must be non-empty, got " + outline);
        }
        if (interaction.isEmpty()) {
            throw helper.assertionException("F5b dy-triad: the waterlogged marked slab's interaction shape is "
                    + "EMPTY while its outline renders lowered — the block is untargetable");
        }
        if (Math.abs(outline.bounds().minY - interaction.bounds().minY) > EPS
                || Math.abs(outline.bounds().maxY - interaction.bounds().maxY) > EPS) {
            throw helper.assertionException("F5b dy-triad: the waterlogged marked slab's interaction shape ["
                    + interaction.bounds().minY + "," + interaction.bounds().maxY
                    + "] must match its outline [" + outline.bounds().minY + "," + outline.bounds().maxY + "]");
        }
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

    // ── F5c: marker AUTHORING symmetry (the split moved one more hop — reads went fluid-blind in
    // F5/F5b, but the placement mixin's candidate predicates still refused waterlogged placements,
    // so an underwater placement read lowered yet never persisted its marker) + the ceiling-subject
    // walk-B/C fluid gate (walk A is the fluid-blind reference). ──

    /** useOn with an explicit hit vector (the compound-side visible-band legs are hit-Y-sensitive). */
    private static void placeWithHit(GameTestHelper helper, ItemStack stack, BlockPos clicked,
                                     Direction face, Vec3 hit) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    /** The genuine compound stack WITHOUT a side slab: ground, slab, stone, slab, marked FB (-1.0). */
    private static BlockPos buildCompoundSourceFb(GameTestHelper helper, ServerLevel w) {
        BlockPos base = helper.absolutePos(new BlockPos(3, 1, 2));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(1));
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(3));
        BlockPos fb = base.above(4);
        w.setBlock(fb, Blocks.STONE.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, fb, w.getBlockState(fb));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(w, fb, w.getBlockState(fb));
        double fbDy = dy(w, fb);
        if (Math.abs(fbDy + 1.0) > EPS) {
            throw helper.assertionException("scene premise: compound source FB must read -1.0, got " + fbDy);
        }
        return fb;
    }

    /**
     * F5c side-LOWER authoring: a slab useOn into a WATER cell beside the -1.0 compound FB (hit in
     * the FB's lower visible band) must author the side-LOWER marker exactly like its dry twin —
     * reads are fluid-blind since F5, so without the marker the slab reads lowered but never
     * persists, and breaking the source FB pops it (never-pop broken underwater).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void underwaterCompoundSidePlacementAuthorsLowerMarker(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos fb = buildCompoundSourceFb(helper, w);
        BlockPos cell = fb.west();
        Vec3 lowerBandHit = new Vec3(fb.getX(), fb.getY() - 0.75, fb.getZ() + 0.5);
        // DRY BASELINE first: if the dry twin does not author the marker either, the gap is a
        // pre-existing authoring hole, NOT a fluid asymmetry — this scene must not blame F5c for it.
        placeWithHit(helper, new ItemStack(Items.STONE_SLAB), fb, Direction.WEST, lowerBandHit);
        BlockState dryPlaced = w.getBlockState(cell);
        if (!(dryPlaced.getBlock() instanceof SlabBlock)
                || dryPlaced.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
            throw helper.assertionException(
                    "DRY BASELINE premise: the lower-band side placement must land a BOTTOM slab, got " + dryPlaced);
        }
        if (!SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(w, cell, dryPlaced)) {
            throw helper.assertionException(
                    "DRY BASELINE: the dry twin must author the side-LOWER marker (pre-existing authoring hole)");
        }
        w.destroyBlock(cell, false);
        if (SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(w, cell, w.getBlockState(cell))) {
            throw helper.assertionException("scene premise: the marker must die with the dry baseline slab");
        }
        w.setBlock(cell, Blocks.WATER.defaultBlockState(), 2);
        placeWithHit(helper, new ItemStack(Items.STONE_SLAB), fb, Direction.WEST, lowerBandHit);
        BlockState placed = w.getBlockState(cell);
        if (!(placed.getBlock() instanceof SlabBlock)
                || placed.getValue(SlabBlock.TYPE) != SlabType.BOTTOM
                || !placed.getValue(BlockStateProperties.WATERLOGGED)) {
            throw helper.assertionException(
                    "scene premise: a WATERLOGGED BOTTOM slab must place into the water cell, got " + placed);
        }
        if (!SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(w, cell, placed)) {
            throw helper.assertionException(
                    "F5c: the underwater compound-side placement must author the side-LOWER marker like its dry twin");
        }
        assertDy(helper, w, cell, -1.0, "F5c: the underwater-placed marked slab reads the compound -1.0");
        w.destroyBlock(fb, false);
        assertDy(helper, w, cell, -1.0,
                "F5c: breaking the source FB must not pop the underwater-placed marked slab (never-pop)");
        helper.succeed();
    }

    /**
     * F5c side-UPPER authoring: the same water-cell placement with the hit in the FB's UPPER visible
     * band must land a TOP slab carrying the side-UPPER marker, like its dry twin.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void underwaterCompoundSideUpperPlacementAuthorsUpperMarker(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos fb = buildCompoundSourceFb(helper, w);
        BlockPos cell = fb.west();
        Vec3 upperBandHit = new Vec3(fb.getX(), fb.getY() - 0.25, fb.getZ() + 0.5);
        placeWithHit(helper, new ItemStack(Items.STONE_SLAB), fb, Direction.WEST, upperBandHit);
        BlockState dryPlaced = w.getBlockState(cell);
        if (!(dryPlaced.getBlock() instanceof SlabBlock)
                || dryPlaced.getValue(SlabBlock.TYPE) != SlabType.TOP) {
            throw helper.assertionException(
                    "DRY BASELINE premise: the upper-band side placement must land a TOP slab, got " + dryPlaced);
        }
        if (!SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(w, cell, dryPlaced)) {
            throw helper.assertionException(
                    "DRY BASELINE: the dry twin must author the side-UPPER marker (pre-existing authoring hole)");
        }
        w.destroyBlock(cell, false);
        if (SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(w, cell, w.getBlockState(cell))) {
            throw helper.assertionException("scene premise: the marker must die with the dry baseline slab");
        }
        w.setBlock(cell, Blocks.WATER.defaultBlockState(), 2);
        placeWithHit(helper, new ItemStack(Items.STONE_SLAB), fb, Direction.WEST, upperBandHit);
        BlockState placed = w.getBlockState(cell);
        if (!(placed.getBlock() instanceof SlabBlock)
                || !placed.getValue(BlockStateProperties.WATERLOGGED)) {
            throw helper.assertionException(
                    "scene premise: a WATERLOGGED slab must place into the water cell, got " + placed);
        }
        if (placed.getValue(SlabBlock.TYPE) != SlabType.TOP) {
            throw helper.assertionException(
                    "F5c: the underwater upper-band placement must land a TOP slab like its dry twin, got " + placed);
        }
        if (!SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(w, cell, placed)) {
            throw helper.assertionException(
                    "F5c: the underwater compound-side placement must author the side-UPPER marker like its dry twin");
        }
        assertDy(helper, w, cell, -1.0, "F5c: the underwater-placed marked TOP slab reads the compound -1.0");
        w.destroyBlock(fb, false);
        assertDy(helper, w, cell, -1.0,
                "F5c: breaking the source FB must not pop the underwater-placed marked TOP slab (never-pop)");
        helper.succeed();
    }

    /**
     * F5c bottom-carrier READ symmetry pin (GREEN pre-fix, empirical correction to the plan): the
     * :118 candidate gate skips the placement-time carrier WRITE underwater, but
     * isPersistentLoweredSlabCarrier live-derives the bottom-on-lowered-FB lane via its
     * NonRecursive fallback (fluid-blind since F5), so the underwater placement READS as a carrier
     * like its dry twin anyway. This scene pins that healed symmetry — it REDs if the fallback or
     * the shared state predicate ever grows a fluid gate back.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void underwaterSlabOnLoweredFullBlockAuthorsCarrier(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(1));
        BlockPos support = base.above(2);
        w.setBlock(support, Blocks.STONE.defaultBlockState(), 2);
        double supportDy = dy(w, support);
        if (Math.abs(supportDy + 0.5) > EPS) {
            throw helper.assertionException("scene premise: the support must read -0.5, got " + supportDy);
        }
        BlockPos cell = support.above();
        place(helper, new ItemStack(Items.STONE_SLAB), support, Direction.UP);
        BlockState dryPlaced = w.getBlockState(cell);
        if (!(dryPlaced.getBlock() instanceof SlabBlock)) {
            throw helper.assertionException("DRY BASELINE premise: the dry placement must succeed, got " + dryPlaced);
        }
        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, cell, dryPlaced)) {
            throw helper.assertionException(
                    "DRY BASELINE: the dry twin must author the carrier marker (pre-existing authoring hole)");
        }
        w.destroyBlock(cell, false);
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, cell, w.getBlockState(cell))) {
            throw helper.assertionException("scene premise: the carrier must die with the dry baseline slab");
        }
        w.setBlock(cell, Blocks.WATER.defaultBlockState(), 2);
        place(helper, new ItemStack(Items.STONE_SLAB), support, Direction.UP);
        BlockState placed = w.getBlockState(cell);
        if (!(placed.getBlock() instanceof SlabBlock)
                || !placed.getValue(BlockStateProperties.WATERLOGGED)) {
            throw helper.assertionException(
                    "scene premise: a WATERLOGGED slab must place into the water cell, got " + placed);
        }
        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, cell, placed)) {
            throw helper.assertionException(
                    "F5c pin: the underwater slab-on-lowered-FB placement must READ as a carrier like its dry twin"
                            + " (attachment or the fluid-blind NonRecursive fallback)");
        }
        helper.succeed();
    }

    /**
     * F5c :118 PROBE (the L8 vertical lane, where NO live read fallback exists): an underwater
     * rider placed on a side-lowered TOP support must survive the support break like its dry twin.
     * If this scene is green with the :118 fluid term still in place, the term is SHADOWED (the
     * F5b fluid-blind freeze writer anchors the rider) and stays under the surviving-mutant rule.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void underwaterRiderOnLoweredTopSupportSurvivesSupportBreak(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(3, 1, 2));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(1));
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        BlockPos topSupport = helper.absolutePos(new BlockPos(2, 3, 2));
        w.setBlock(topSupport, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        double supportDy = dy(w, topSupport);
        if (Math.abs(supportDy + 0.5) > EPS) {
            throw helper.assertionException("scene premise: the side-lowered TOP support must read -0.5, got "
                    + supportDy);
        }
        BlockPos rider = topSupport.above();
        // DRY BASELINE: the L8 lane persists through the support break for a dry rider.
        place(helper, new ItemStack(Items.STONE_SLAB), topSupport, Direction.UP);
        if (!(w.getBlockState(rider).getBlock() instanceof SlabBlock)) {
            throw helper.assertionException("DRY BASELINE premise: the dry rider must place, got "
                    + w.getBlockState(rider));
        }
        assertDy(helper, w, rider, -0.5, "DRY BASELINE premise: the dry rider follows the support down");
        w.destroyBlock(topSupport, false);
        assertDy(helper, w, rider, -0.5,
                "DRY BASELINE: the dry rider must survive the support break (L8 persistence)");
        w.destroyBlock(rider, false);
        w.setBlock(topSupport, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        double rebuiltDy = dy(w, topSupport);
        if (Math.abs(rebuiltDy + 0.5) > EPS) {
            throw helper.assertionException("scene premise: the rebuilt TOP support must read -0.5, got "
                    + rebuiltDy);
        }
        // WET twin: place into a water cell, then break the support.
        w.setBlock(rider, Blocks.WATER.defaultBlockState(), 2);
        place(helper, new ItemStack(Items.STONE_SLAB), topSupport, Direction.UP);
        BlockState placed = w.getBlockState(rider);
        if (!(placed.getBlock() instanceof SlabBlock)
                || !placed.getValue(BlockStateProperties.WATERLOGGED)) {
            throw helper.assertionException(
                    "scene premise: a WATERLOGGED rider must place into the water cell, got " + placed);
        }
        assertDy(helper, w, rider, -0.5, "scene premise: the underwater rider follows the support down");
        w.destroyBlock(topSupport, false);
        assertDy(helper, w, rider, -0.5,
                "F5c probe: the underwater rider must survive the support break like its dry twin (never-pop)");
        helper.succeed();
    }

    /**
     * F5c side-carrier authoring, placed-state gate (:132): a slab placed into a WATER cell beside a
     * marked lane slab must author the carrier marker like its dry twin.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void underwaterSlabBesideLaneAuthorsSideCarrier(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos source = buildMarkedLowerSlab(helper, w);
        BlockPos cell = source.west();
        place(helper, new ItemStack(Items.STONE_SLAB), source, Direction.WEST, true);
        BlockState dryPlaced = w.getBlockState(cell);
        if (!(dryPlaced.getBlock() instanceof SlabBlock)) {
            throw helper.assertionException("DRY BASELINE premise: the dry side placement must succeed, got "
                    + dryPlaced);
        }
        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, cell, dryPlaced)) {
            throw helper.assertionException(
                    "DRY BASELINE: the dry twin must author the side-carrier marker (pre-existing authoring hole)");
        }
        w.destroyBlock(cell, false);
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, cell, w.getBlockState(cell))) {
            throw helper.assertionException("scene premise: the carrier must die with the dry baseline slab");
        }
        w.setBlock(cell, Blocks.WATER.defaultBlockState(), 2);
        place(helper, new ItemStack(Items.STONE_SLAB), source, Direction.WEST, true);
        BlockState placed = w.getBlockState(cell);
        if (!(placed.getBlock() instanceof SlabBlock)
                || !placed.getValue(BlockStateProperties.WATERLOGGED)) {
            throw helper.assertionException(
                    "scene premise: a WATERLOGGED slab must place into the water cell, got " + placed);
        }
        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, cell, placed)) {
            throw helper.assertionException(
                    "F5c: the underwater slab-beside-lane placement must author the side-carrier marker like its dry twin");
        }
        helper.succeed();
    }

    /**
     * F5c side-carrier authoring, lane-SOURCE gate (:140): a dry placement beside a WATERLOGGED lane
     * source must author the carrier marker — the source's water is height-neutral (F5 core scene)
     * and must not blind the lane extension.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabBesideWaterloggedLaneSourceAuthorsSideCarrier(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos source = buildMarkedLowerSlab(helper, w);
        BlockPos cell = source.west();
        place(helper, new ItemStack(Items.STONE_SLAB), source, Direction.WEST, true);
        BlockState dryPlaced = w.getBlockState(cell);
        if (!(dryPlaced.getBlock() instanceof SlabBlock)
                || !SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, cell, dryPlaced)) {
            throw helper.assertionException(
                    "DRY BASELINE: the dry-source dry-cell twin must author the side-carrier marker, got " + dryPlaced);
        }
        w.destroyBlock(cell, false);
        if (SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, cell, w.getBlockState(cell))) {
            throw helper.assertionException("scene premise: the carrier must die with the dry baseline slab");
        }
        setWaterlogged(w, source, true);
        assertDy(helper, w, source, -1.0,
                "scene premise: the waterlogged lane source keeps -1.0 (F5 core — height-neutral flip)");
        place(helper, new ItemStack(Items.STONE_SLAB), source, Direction.WEST, true);
        BlockState placed = w.getBlockState(cell);
        if (!(placed.getBlock() instanceof SlabBlock)) {
            throw helper.assertionException("scene premise: the dry-cell placement must succeed, got " + placed);
        }
        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, cell, placed)) {
            throw helper.assertionException(
                    "F5c: a placement beside a WATERLOGGED lane source must author the side-carrier marker");
        }
        helper.succeed();
    }

    /**
     * F5c side-carrier authoring, FULLY submerged (:140's proving scene): waterlogged lane source
     * AND a water placement cell. Here the bottom-carrier candidate is blocked by its own placed-
     * fluid gate (kept, shadowed elsewhere), so the SIDE candidate's source term alone decides —
     * the mutation that re-adds the source fluid term REDs exactly this scene (the dry-cell twin
     * above is bottom-route-shadowed and pins the lane instead).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void submergedSlabBesideWaterloggedLaneSourceAuthorsSideCarrier(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos source = buildMarkedLowerSlab(helper, w);
        BlockPos cell = source.west();
        setWaterlogged(w, source, true);
        assertDy(helper, w, source, -1.0,
                "scene premise: the waterlogged lane source keeps -1.0 (F5 core — height-neutral flip)");
        w.setBlock(cell, Blocks.WATER.defaultBlockState(), 2);
        place(helper, new ItemStack(Items.STONE_SLAB), source, Direction.WEST, true);
        BlockState placed = w.getBlockState(cell);
        if (!(placed.getBlock() instanceof SlabBlock)
                || !placed.getValue(BlockStateProperties.WATERLOGGED)) {
            throw helper.assertionException(
                    "scene premise: a WATERLOGGED slab must place into the water cell, got " + placed);
        }
        if (!SlabAnchorAttachment.isPersistentLoweredSlabCarrier(w, cell, placed)) {
            throw helper.assertionException(
                    "F5c: the fully submerged lane extension must author the side-carrier marker");
        }
        helper.succeed();
    }

    /** Marked side-UPPER TOP slab rig (D2's CeilingFlushRulingTest rig): -1.0, authored marker. */
    private static BlockPos buildMarkedUpperTopSlabRig(GameTestHelper helper, ServerLevel w) {
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
        double slabDy = dy(w, slab);
        if (Math.abs(slabDy + 1.0) > EPS) {
            throw helper.assertionException("scene premise: the marked side-UPPER top slab must read -1.0, got "
                    + slabDy);
        }
        return slab;
    }

    /**
     * F5c ceiling-subject symmetry (walk B): bucketing a Y-chain under a -1.0 marked TOP slab must
     * not pop it — the ceiling-family exclusion list kicked any waterlogged subject to 0.0 while
     * walk A (lanterns) ignores fluid.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void waterloggedChainUnderMarkedUpperSlabKeepsMergedDy(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = buildMarkedUpperTopSlabRig(helper, w);
        BlockPos chain = slab.below();
        w.setBlock(chain, Blocks.IRON_CHAIN.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Y), 2);
        assertDy(helper, w, chain, -0.5,
                "scene premise: the dry chain merges at -0.5 under the -1.0 marked slab (walk B compensation)");
        setWaterlogged(w, chain, true);
        assertDy(helper, w, chain, -0.5,
                "F5c: bucketing the chain must not pop it +0.5 — the ceiling walk must be fluid-blind like walk A");
        helper.succeed();
    }

    /** F5c ceiling-subject symmetry, TOP-trapdoor twin (the other walk-B/C-only waterloggable family). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void waterloggedTopTrapdoorUnderMarkedUpperSlabKeepsMergedDy(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = buildMarkedUpperTopSlabRig(helper, w);
        BlockPos trapdoor = slab.below();
        w.setBlock(trapdoor, Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HALF, Half.TOP), 2);
        assertDy(helper, w, trapdoor, -0.5,
                "scene premise: the dry TOP trapdoor merges at -0.5 under the -1.0 marked slab");
        setWaterlogged(w, trapdoor, true);
        assertDy(helper, w, trapdoor, -0.5,
                "F5c: waterlogging the TOP trapdoor must not pop it — walk B fluid-blind");
        helper.succeed();
    }

    /**
     * The walk-A symmetry REFERENCE (green before and after): a HANGING lantern in the same spot
     * routes through ceilingHungDecorationDy, which never had a subject fluid gate — this is the
     * asymmetry the chain/trapdoor scenes close.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void waterloggedHangingLanternUnderMarkedUpperSlabReference(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos slab = buildMarkedUpperTopSlabRig(helper, w);
        BlockPos lantern = slab.below();
        w.setBlock(lantern, Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), 2);
        assertDy(helper, w, lantern, -0.5,
                "scene premise: the dry hanging lantern merges at -0.5 under the -1.0 marked slab (walk A)");
        setWaterlogged(w, lantern, true);
        assertDy(helper, w, lantern, -0.5,
                "reference: walk A ignores fluid — the waterlogged lantern holds (the symmetry target)");
        helper.succeed();
    }
}
