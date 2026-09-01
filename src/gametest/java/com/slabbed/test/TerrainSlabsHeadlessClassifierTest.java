package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.util.SlabEnsembleCoherence;
import com.slabbed.util.SlabSupport;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Headless proof of the Terrain Slabs compatibility gate, driven by the classifier
 * shim mod that claims the Terrain Slabs mod id in the GameTest server. Before this
 * class existed, the gate's only detector required the real optional mod in a client
 * run, so a classification regression could not turn the required suite red.
 *
 * <p>Each row self-calibrates: the control cell must actually lower, so a row whose
 * environment stopped producing offsets fails instead of passing vacuously.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class TerrainSlabsHeadlessClassifierTest {
    private static final String TEMPLATE = "empty";
    private static final String CANONICAL_NAMESPACE = "terrain_slabs";
    private static final String LEGACY_NAMESPACE = "terrainslabs";
    private static final String CONTROL_NAMESPACE = "slabbed_ts_shim";
    private static final String SHIM_BLOCK_NAME = "shim_terrain_slab";
    private static final String UNSUFFIXED_BLOCK_NAME = "shim_terrain_step";
    private static final double EPS = 1.0e-9;

    @GameTest(template = TEMPLATE)
    public void classifierFollowsOwnedNamespaceNotBlockClass(GameTestHelper ctx) {
        BlockState canonical = shimState(ctx, CANONICAL_NAMESPACE);
        BlockState legacy = shimState(ctx, LEGACY_NAMESPACE);
        BlockState control = shimState(ctx, CONTROL_NAMESPACE);

        ctx.assertTrue(CompatHooks.shouldSkipOffset(canonical),
                "a terrain_slabs-namespace state must skip Slabbed offsets while the mod is loaded");
        ctx.assertTrue(CompatHooks.shouldSkipOffset(legacy),
                "a terrainslabs legacy-namespace state must skip Slabbed offsets while the mod is loaded");
        ctx.assertTrue(!CompatHooks.shouldSkipOffset(control),
                "an identical block class outside the owned namespaces must keep Slabbed behavior");
        ctx.assertTrue(!CompatHooks.shouldSkipOffset(Blocks.STONE_SLAB.defaultBlockState()),
                "a vanilla slab must keep Slabbed behavior while the shim is loaded");
        ctx.assertTrue(CompatHooks.shouldSkipSlabSupport(canonical),
                "a terrain_slabs-namespace state must stay out of Slabbed support-source rules");
        ctx.assertTrue(!CompatHooks.shouldSkipSlabSupport(control),
                "the control-namespace twin must remain a normal support candidate");
        ctx.succeed();
    }

    /**
     * The world-hole pin: a compat surface Slabbed never authored stays flush even when what it
     * rests on is lowered. Written with setBlock and no placement, which is what generated ground
     * looks like to Slabbed.
     *
     * <p>Named for what it actually measures. It used to be called for the blanket law - that a
     * compat block may never receive an offset - and it kept passing when that law was narrowed
     * to authorship, because it only ever drove the unauthored half. A pin that survives a law
     * change without going red may simply be measuring something narrower than its name claims;
     * the authored half is pinned separately below.
     */
    @GameTest(template = TEMPLATE)
    public void anUnauthoredTerrainSlabStaysFlushOnALoweredSupport(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos terrainSupport = ctx.absolutePos(new BlockPos(1, 2, 1));
        BlockPos controlSupport = ctx.absolutePos(new BlockPos(3, 2, 1));
        authorLoweredStone(ctx, world, terrainSupport);
        authorLoweredStone(ctx, world, controlSupport);

        BlockPos terrainSubject = terrainSupport.above();
        BlockPos controlSubject = controlSupport.above();
        world.setBlockAndUpdate(terrainSubject, shimState(ctx, CANONICAL_NAMESPACE));
        world.setBlockAndUpdate(controlSubject, shimState(ctx, CONTROL_NAMESPACE));

        double controlDy = SlabSupport.getYOffset(world, controlSubject, world.getBlockState(controlSubject));
        ctx.assertTrue(controlDy < -EPS,
                "the control twin must follow the lowered support; a flush control means this row lost its teeth");
        double terrainDy = SlabSupport.getYOffset(world, terrainSubject, world.getBlockState(terrainSubject));
        ctx.assertTrue(Math.abs(terrainDy) <= EPS,
                "an unauthored compat surface must stay flush over a lowered support; observed "
                        + terrainDy);
        ctx.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void terrainSlabSupportDoesNotContaminateTheOccupantAbove(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos terrainSupport = ctx.absolutePos(new BlockPos(1, 2, 1));
        BlockPos vanillaSupport = ctx.absolutePos(new BlockPos(3, 2, 1));
        world.setBlockAndUpdate(terrainSupport, shimState(ctx, CANONICAL_NAMESPACE));
        world.setBlockAndUpdate(vanillaSupport, Blocks.STONE_SLAB.defaultBlockState());

        BlockPos terrainOccupant = terrainSupport.above();
        BlockPos vanillaOccupant = vanillaSupport.above();
        placeStoneWithHeldItem(ctx, terrainOccupant, terrainSupport);
        placeStoneWithHeldItem(ctx, vanillaOccupant, vanillaSupport);

        double vanillaDy = SlabSupport.getYOffset(world, vanillaOccupant, world.getBlockState(vanillaOccupant));
        ctx.assertTrue(vanillaDy < -EPS,
                "the vanilla bottom-slab control must lower its occupant; a flush control means this row lost its teeth");
        double terrainDy = SlabSupport.getYOffset(world, terrainOccupant, world.getBlockState(terrainOccupant));
        ctx.assertTrue(Math.abs(terrainDy) <= EPS,
                "a block placed on bare Terrain Slabs terrain must stay flush; observed " + terrainDy);
        OptionalInt storedFact = SlabPlacementHeightAttachment.storedHalfSteps(
                world.getChunkAt(terrainOccupant), terrainOccupant);
        ctx.assertTrue(storedFact.isEmpty() || storedFact.getAsInt() == 0,
                "a flush occupant on Terrain Slabs terrain must not acquire a lowering fact");

        world.setBlockAndUpdate(terrainSupport.north(), Blocks.STONE.defaultBlockState());
        world.removeBlock(terrainSupport.north(), false);
        double terrainDyAfterUpdate = SlabSupport.getYOffset(world, terrainOccupant, world.getBlockState(terrainOccupant));
        ctx.assertTrue(Math.abs(terrainDyAfterUpdate) <= EPS,
                "the occupant must not snap to a new height after a neighbor update; observed " + terrainDyAfterUpdate);

        ctx.succeed();
    }

    /**
     * A named Terrain surface is a DIRECT seat (maintainer ruling, 2026-08-21, the reference
     * line's lane): a vanilla slab placed on it lowers half a block and records that height, and
     * a curated standing object seats the same way - while a plain full block on the same surface
     * stays flush, the world-hole-safe answer the row above already pins, and a log-family
     * block seats as the one curated opaque-cube exception. The flush contrast is asserted here
     * too, in the same run, so this row cannot pass with the lane deleted.
     */
    @GameTest(template = TEMPLATE)
    public void namedTerrainSurfaceIsADirectSeatForSlabsAndObjects(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slabSupport = ctx.absolutePos(new BlockPos(1, 2, 5));
        BlockPos torchSupport = ctx.absolutePos(new BlockPos(3, 2, 5));
        BlockPos cubeSupport = ctx.absolutePos(new BlockPos(5, 2, 5));
        BlockPos logSupport = ctx.absolutePos(new BlockPos(7, 2, 5));
        world.setBlockAndUpdate(slabSupport, shimState(ctx, CANONICAL_NAMESPACE));
        world.setBlockAndUpdate(torchSupport, shimState(ctx, CANONICAL_NAMESPACE));
        world.setBlockAndUpdate(cubeSupport, shimState(ctx, CANONICAL_NAMESPACE));
        world.setBlockAndUpdate(logSupport, shimState(ctx, CANONICAL_NAMESPACE));

        BlockPos slabSubject = slabSupport.above();
        placeWithHeldItem(ctx, Blocks.STONE_SLAB, slabSubject, slabSupport);
        ctx.assertTrue(world.getBlockState(slabSubject).is(Blocks.STONE_SLAB),
                "a vanilla slab must place onto the named Terrain surface");
        double slabDy = SlabSupport.getYOffset(world, slabSubject, world.getBlockState(slabSubject));
        ctx.assertTrue(Math.abs(slabDy + 0.5) <= EPS,
                "a vanilla slab on a named Terrain surface must lower half a block"
                        + " (the reverse-works rule); observed " + slabDy);
        ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(
                        world.getChunkAt(slabSubject), slabSubject).orElse(Integer.MIN_VALUE) == -1,
                "the lowered slab must record its height");

        BlockPos torchSubject = torchSupport.above();
        placeWithHeldItem(ctx, Blocks.TORCH, torchSubject, torchSupport);
        double torchDy = SlabSupport.getYOffset(world, torchSubject, world.getBlockState(torchSubject));
        ctx.assertTrue(Math.abs(torchDy + 0.5) <= EPS,
                "a curated standing object must seat on the named Terrain surface; observed " + torchDy);

        BlockPos cubeSubject = cubeSupport.above();
        placeStoneWithHeldItem(ctx, cubeSubject, cubeSupport);
        double cubeDy = SlabSupport.getYOffset(world, cubeSubject, world.getBlockState(cubeSubject));
        ctx.assertTrue(Math.abs(cubeDy) <= EPS,
                "the flush contrast: a plain full block on the same surface must stay flush;"
                        + " observed " + cubeDy);

        // The log family is the ONE opaque-full-cube family that seats. It is a curated
        // exception to the flush rule the row above pins, and it is what separates a built
        // object from natural terrain: a log is always player-placed, so lowering it cannot
        // tear the world-hole seam that pinning plain cubes exists to prevent. Asserted in
        // the same run as the flush contrast so neither answer can drift into the other.
        BlockPos logSubject = logSupport.above();
        placeWithHeldItem(ctx, Blocks.OAK_LOG, logSubject, logSupport);
        ctx.assertTrue(world.getBlockState(logSubject).is(Blocks.OAK_LOG),
                "a log must place onto the named Terrain surface");
        double logDy = SlabSupport.getYOffset(world, logSubject, world.getBlockState(logSubject));
        ctx.assertTrue(Math.abs(logDy + 0.5) <= EPS,
                "a log-family block on a named Terrain surface must seat on it, not float;"
                        + " observed " + logDy);
        ctx.succeed();
    }

    /**
     * The compat gate discriminates AUTHORSHIP, not namespace. A compat surface the world
     * generator laid down stays flush - lowering generated ground tears see-through world holes
     * - but one a player placed is an ordinary slab and seats like any other.
     *
     * <p>Authorship is read from Slabbed's OWN placement record, never from the other mod's
     * state. The mod carries a {@code generated} flag that looks like the answer and is not:
     * its disk and ore worldgen features rebuild a slab from {@code defaultBlockState}, and its
     * grass die-back and spread do the same, so generated ground decays to {@code generated=false}
     * on a random tick. A placement fact is written only by a real placement transaction, so
     * world generation cannot forge one.
     *
     * <p>All three cells resolve in one run: without the unauthored cell the row would pass with
     * the world-hole pin deleted, and without the control-namespace cell it would pass in an
     * environment that stopped producing offsets at all.
     */
    @GameTest(template = TEMPLATE)
    public void anAuthoredCompatSlabSeatsAndAnUnauthoredOneStaysFlush(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos authoredSupport = ctx.absolutePos(new BlockPos(1, 2, 1));
        BlockPos unauthoredSupport = ctx.absolutePos(new BlockPos(3, 2, 1));
        BlockPos controlSupport = ctx.absolutePos(new BlockPos(5, 2, 1));
        authorLoweredStone(ctx, world, authoredSupport);
        authorLoweredStone(ctx, world, unauthoredSupport);
        authorLoweredStone(ctx, world, controlSupport);

        Block canonicalShim = shimState(ctx, CANONICAL_NAMESPACE).getBlock();
        Block controlShim = shimState(ctx, CONTROL_NAMESPACE).getBlock();
        ctx.assertTrue(canonicalShim.asItem() != net.minecraft.world.item.Items.AIR,
                "the shim block needs an item, or this row cannot drive a real placement");

        BlockPos authored = authoredSupport.above();
        placeWithHeldItem(ctx, canonicalShim, authored, authoredSupport);

        BlockPos control = controlSupport.above();
        placeWithHeldItem(ctx, controlShim, control, controlSupport);

        // Never placed, only written - this is what generated terrain looks like to Slabbed.
        BlockPos unauthored = unauthoredSupport.above();
        world.setBlockAndUpdate(unauthored, shimState(ctx, CANONICAL_NAMESPACE));

        double controlDy = SlabSupport.getYOffset(world, control, world.getBlockState(control));
        ctx.assertTrue(controlDy < -EPS,
                "calibration: the control-namespace twin must seat on the lowered support;"
                        + " a flush control means this row lost its teeth, observed " + controlDy);

        double unauthoredDy = SlabSupport.getYOffset(
                world, unauthored, world.getBlockState(unauthored));
        ctx.assertTrue(Math.abs(unauthoredDy) <= EPS,
                "world-hole pin: an unauthored compat surface must stay flush; observed "
                        + unauthoredDy);

        OptionalInt authoredFact = SlabPlacementHeightAttachment.storedHalfSteps(
                world.getChunkAt(authored), authored);
        ctx.assertTrue(authoredFact.isPresent(),
                "a placed compat slab must record its placement height like any other slab");
        double authoredDy = SlabSupport.getYOffset(world, authored, world.getBlockState(authored));
        ctx.assertTrue(Math.abs(authoredDy - controlDy) <= EPS,
                "a placed compat slab must seat exactly where its control twin seats; control="
                        + controlDy + " compat=" + authoredDy);
        ctx.succeed();
    }

    /**
     * A column standing on a compat surface owes half a block of descent PER COURSE, exactly as
     * it would over a vanilla bottom slab. The direct seat answered one constant regardless of
     * how far the column had climbed, so the second course sat where a slab on a full block sits
     * - half a block proud of the course below it.
     *
     * <p>Both courses resolve in one run against a vanilla-rooted twin of the same height. A row
     * that checked only the first course would pass with the depth term deleted, because one
     * course is exactly where the constant and the correct answer agree.
     */
    @GameTest(template = TEMPLATE)
    public void aColumnOnACompatSurfaceDescendsOncePerCourse(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos compatRoot = ctx.absolutePos(new BlockPos(1, 2, 1));
        BlockPos vanillaRoot = ctx.absolutePos(new BlockPos(4, 2, 1));
        world.setBlockAndUpdate(compatRoot, shimState(ctx, CANONICAL_NAMESPACE));
        world.setBlockAndUpdate(vanillaRoot, Blocks.STONE_SLAB.defaultBlockState());

        // Placed, never written: a factless column does not accumulate by design, so a setBlock
        // fixture would measure a different lane than the one a player drives.
        for (BlockPos root : new BlockPos[] {compatRoot, vanillaRoot}) {
            placeCourseOnTop(ctx, world, root, Blocks.OAK_SLAB);
            placeCourseOnTop(ctx, world, root.above(), Blocks.SPRUCE_SLAB);
        }

        double vanillaFirst = SlabSupport.getYOffset(
                world, vanillaRoot.above(), world.getBlockState(vanillaRoot.above()));
        double vanillaSecond = SlabSupport.getYOffset(
                world, vanillaRoot.above(2), world.getBlockState(vanillaRoot.above(2)));
        ctx.assertTrue(vanillaSecond < vanillaFirst - EPS,
                "calibration: over a VANILLA root the second course must sit below the first;"
                        + " first=" + vanillaFirst + " second=" + vanillaSecond);

        double compatFirst = SlabSupport.getYOffset(
                world, compatRoot.above(), world.getBlockState(compatRoot.above()));
        double compatSecond = SlabSupport.getYOffset(
                world, compatRoot.above(2), world.getBlockState(compatRoot.above(2)));
        ctx.assertTrue(Math.abs(compatFirst - vanillaFirst) <= EPS,
                "first course over a compat root must match the vanilla twin; compat="
                        + compatFirst + " vanilla=" + vanillaFirst);
        ctx.assertTrue(Math.abs(compatSecond - vanillaSecond) <= EPS,
                "second course over a compat root must match the vanilla twin; compat="
                        + compatSecond + " vanilla=" + vanillaSecond);
        ctx.succeed();
    }

    /**
     * The direct seat admits a slab because it IS a slab, not because of who registered it
     * (LAW.md clause 2). The gate used to require the literal {@code minecraft} namespace, so a
     * modded slab was refused a seat that an identical vanilla one was granted - the eligibility
     * decision the law names outright.
     *
     * <p>Membership of the vanilla slabs tag is the opt-in, and it is a real one: the compat mod
     * adds all of its slabs to that tag additively, and a mod that does not tag its slabs simply
     * is not admitted, without needing a name anywhere in this repo.
     *
     * <p>The vanilla twin in the same run is the calibration: if the seat lane stops working
     * altogether, the row fails rather than passing because both cells agree on nothing.
     */
    @GameTest(template = TEMPLATE)
    public void aModdedSlabGetsTheSameDirectSeatAsAVanillaOne(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos moddedSurface = ctx.absolutePos(new BlockPos(1, 2, 1));
        BlockPos vanillaSurface = ctx.absolutePos(new BlockPos(3, 2, 1));
        world.setBlockAndUpdate(moddedSurface, shimState(ctx, CANONICAL_NAMESPACE));
        world.setBlockAndUpdate(vanillaSurface, shimState(ctx, CANONICAL_NAMESPACE));

        BlockState moddedSlab = shimState(ctx, CONTROL_NAMESPACE);
        ctx.assertTrue(moddedSlab.is(net.minecraft.tags.BlockTags.SLABS),
                "premise: the control slab must be in the vanilla slabs tag, or this row proves"
                        + " nothing about tag-based admission");
        ctx.assertTrue(!"minecraft".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(moddedSlab.getBlock()).getNamespace()),
                "premise: the control slab must NOT be in the minecraft namespace");

        BlockPos moddedSubject = moddedSurface.above();
        BlockPos vanillaSubject = vanillaSurface.above();
        world.setBlockAndUpdate(moddedSubject, moddedSlab);
        world.setBlockAndUpdate(vanillaSubject, Blocks.OAK_SLAB.defaultBlockState());

        double vanillaDy = SlabSupport.getYOffset(
                world, vanillaSubject, world.getBlockState(vanillaSubject));
        ctx.assertTrue(vanillaDy < -EPS,
                "calibration: a vanilla slab must take the direct seat on a compat surface;"
                        + " a flush control means this row lost its teeth, observed " + vanillaDy);
        double moddedDy = SlabSupport.getYOffset(
                world, moddedSubject, world.getBlockState(moddedSubject));
        ctx.assertTrue(Math.abs(moddedDy - vanillaDy) <= EPS,
                "a tagged modded slab must take the same direct seat as the vanilla twin;"
                        + " vanilla=" + vanillaDy + " modded=" + moddedDy);
        ctx.succeed();
    }

    /**
     * A compat slab a player PLACED supports its occupant exactly as a vanilla slab does, and one
     * the world generator laid down supports nothing (maintainer ruling, 2026-08-24: full support
     * parity when placed; the unauthored exclusion is L5's world-hole guard and stays).
     *
     * <p>This row previously pinned the OPPOSITE for the authored half - support exclusion total,
     * authored or not. That totality was this port's conservatism, not a ruling, and the ruling
     * has now been taken the other way; the flip is deliberate and cited, not a regression.
     *
     * <p>The authored and unauthored halves are ordered authored-first so the mutation that
     * closes the support lane reddens the parity claim before the unauthored contrast can run.
     */
    @GameTest(template = TEMPLATE)
    public void aPlacedCompatSlabSupportsItsOccupantAndAGeneratedOneDoesNot(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos ground = ctx.absolutePos(new BlockPos(1, 1, 1));
        world.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        BlockPos support = ground.above();
        placeCourseOnTop(ctx, world, ground, shimState(ctx, CANONICAL_NAMESPACE).getBlock());
        ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(
                        world.getChunkAt(support), support).isPresent(),
                "premise: the compat support must actually be AUTHORED, or this row silently"
                        + " repeats the unauthored case below");

        BlockPos vanillaGround = ctx.absolutePos(new BlockPos(4, 1, 1));
        world.setBlockAndUpdate(vanillaGround, Blocks.STONE.defaultBlockState());
        placeCourseOnTop(ctx, world, vanillaGround, Blocks.STONE_SLAB);
        BlockPos vanillaOccupant = vanillaGround.above(2);
        placeStoneWithHeldItem(ctx, vanillaOccupant, vanillaGround.above());
        double vanillaDy = SlabSupport.getYOffset(
                world, vanillaOccupant, world.getBlockState(vanillaOccupant));
        ctx.assertTrue(vanillaDy < -EPS,
                "calibration: a vanilla slab support must lower its occupant; a flush control"
                        + " means this row lost its teeth, observed " + vanillaDy);

        BlockPos occupant = support.above();
        placeStoneWithHeldItem(ctx, occupant, support);
        double occupantDy = SlabSupport.getYOffset(world, occupant, world.getBlockState(occupant));
        ctx.assertTrue(Math.abs(occupantDy - vanillaDy) <= EPS,
                "an occupant of a PLACED compat slab must ride its face like the vanilla twin;"
                        + " vanilla=" + vanillaDy + " compat=" + occupantDy);

        BlockPos generatedGround = ctx.absolutePos(new BlockPos(7, 1, 1));
        world.setBlockAndUpdate(generatedGround, Blocks.STONE.defaultBlockState());
        BlockPos generatedSupport = generatedGround.above();
        world.setBlockAndUpdate(generatedSupport, shimState(ctx, CANONICAL_NAMESPACE));
        BlockPos generatedOccupant = generatedSupport.above();
        placeStoneWithHeldItem(ctx, generatedOccupant, generatedSupport);
        double generatedDy = SlabSupport.getYOffset(
                world, generatedOccupant, world.getBlockState(generatedOccupant));
        ctx.assertTrue(Math.abs(generatedDy) <= EPS,
                "world-hole pin: an occupant of GENERATED compat ground stays flush; observed "
                        + generatedDy);
        ctx.succeed();
    }

    /**
     * A live-resolving follower on a placed compat slab rides its REAL face. The named-surface
     * direct seat answered a constant half step - correct while every compat surface was flush,
     * and half a block short the moment a placed one sank: the follower floated above the slab
     * it stood on. The seat is now relative to the surface's own recorded height, read from the
     * store rather than the resolver so the armed direct lane never re-enters itself.
     *
     * <p>Own row so the direct-lane mutation reddens it alone: the occupant row above goes
     * through the generic face lane, not this one.
     */
    @GameTest(template = TEMPLATE)
    public void aFollowerOnAPlacedCompatSlabRidesItsRealFace(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos compatSupport = ctx.absolutePos(new BlockPos(1, 2, 1));
        BlockPos vanillaSupport = ctx.absolutePos(new BlockPos(3, 2, 1));
        BlockPos flushSupport = ctx.absolutePos(new BlockPos(5, 2, 1));
        world.setBlockAndUpdate(compatSupport, shimState(ctx, CANONICAL_NAMESPACE));
        world.setBlockAndUpdate(vanillaSupport, Blocks.STONE_SLAB.defaultBlockState());
        world.setBlockAndUpdate(flushSupport, shimState(ctx, CANONICAL_NAMESPACE));
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                        world.getChunkAt(compatSupport), compatSupport, -1),
                "premise: the compat support must carry an authored lowered fact");
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                        world.getChunkAt(vanillaSupport), vanillaSupport, -1),
                "premise: the vanilla twin must carry the same lowered fact");

        world.setBlockAndUpdate(compatSupport.above(), Blocks.TORCH.defaultBlockState());
        world.setBlockAndUpdate(vanillaSupport.above(), Blocks.TORCH.defaultBlockState());
        world.setBlockAndUpdate(flushSupport.above(), Blocks.TORCH.defaultBlockState());

        double vanillaTorch = SlabSupport.getYOffset(
                world, vanillaSupport.above(), world.getBlockState(vanillaSupport.above()));
        ctx.assertTrue(vanillaTorch < -0.5d - EPS,
                "calibration: a torch on a lowered vanilla slab must ride below the half step;"
                        + " a shallow control means this row lost its teeth, observed " + vanillaTorch);
        double compatTorch = SlabSupport.getYOffset(
                world, compatSupport.above(), world.getBlockState(compatSupport.above()));
        ctx.assertTrue(Math.abs(compatTorch - vanillaTorch) <= EPS,
                "a follower on a placed compat slab must ride the same face as the vanilla twin;"
                        + " vanilla=" + vanillaTorch + " compat=" + compatTorch);
        double flushTorch = SlabSupport.getYOffset(
                world, flushSupport.above(), world.getBlockState(flushSupport.above()));
        ctx.assertTrue(Math.abs(flushTorch - (-0.5d)) <= EPS,
                "an unauthored flush compat surface keeps the named-surface seat; observed "
                        + flushTorch);
        ctx.succeed();
    }

    /**
     * A compat slab placed on a FLUSH single slab seats half a block down, exactly as a vanilla
     * slab does. Found live: every earlier authored-seat row built its support LOWERED, so the
     * aim lane admitted a landing and the placement never consulted the fallback seat — which,
     * for a compat state, answered the unauthored-surface flush and left the slab floating half
     * a block above the face it was placed on.
     *
     * <p>This is the row the removed placement-seat bypass needed. The bypass was cut as
     * unexercised — correctly, by the discipline — but "no row reaches it" meant the FIXTURES
     * were incomplete, not that the code was wrong. The flush-support case is where the aim lane
     * has no opinion (a flush owner is VANILLA_OWNED) and the seat derivation is the only author.
     *
     * <p>Also pins the freeze twin: the anchor writer runs inside the same transaction and must
     * read the same seat, or it stamps FROZEN-FLAT over a lowered fact and every follower that
     * consults the stamped face floats. Asserted after the seat so each claim fails for its own
     * reason under its own mutation — the seat mutation reddens the first assert, the freeze
     * mutation passes it and reddens the stamp assert.
     */
    @GameTest(template = TEMPLATE)
    public void aCompatSlabPlacedOnAFlushSlabSeatsLikeAVanillaOne(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos compatGround = ctx.absolutePos(new BlockPos(1, 1, 1));
        BlockPos vanillaGround = ctx.absolutePos(new BlockPos(4, 1, 1));
        world.setBlockAndUpdate(compatGround, Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(vanillaGround, Blocks.STONE.defaultBlockState());
        BlockPos compatSupport = compatGround.above();
        BlockPos vanillaSupport = vanillaGround.above();
        world.setBlockAndUpdate(compatSupport, Blocks.OAK_SLAB.defaultBlockState());
        world.setBlockAndUpdate(vanillaSupport, Blocks.OAK_SLAB.defaultBlockState());

        BlockPos vanillaSubject = vanillaSupport.above();
        placeCourseOnTop(ctx, world, vanillaSupport, Blocks.STONE_SLAB);
        double vanillaDy = SlabSupport.getYOffset(
                world, vanillaSubject, world.getBlockState(vanillaSubject));
        ctx.assertTrue(vanillaDy < -EPS,
                "calibration: a vanilla slab on a flush slab must seat on its face; a flush"
                        + " control means this row lost its teeth, observed " + vanillaDy);

        BlockPos compatSubject = compatSupport.above();
        placeCourseOnTop(ctx, world, compatSupport,
                shimState(ctx, CANONICAL_NAMESPACE).getBlock());
        ctx.assertTrue(!world.getBlockState(compatSubject).isAir(),
                "premise: the compat slab must place at all");
        double compatDy = SlabSupport.getYOffset(
                world, compatSubject, world.getBlockState(compatSubject));
        ctx.assertTrue(Math.abs(compatDy - vanillaDy) <= EPS,
                "a compat slab placed on a flush slab must seat exactly like the vanilla twin;"
                        + " vanilla=" + vanillaDy + " compat=" + compatDy);
        ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(
                        world.getChunkAt(compatSubject), compatSubject).isPresent(),
                "the seat must be recorded as a placement fact, not derived live");
        ctx.assertTrue(!com.slabbed.anchor.SlabAnchorAttachment.isFrozenFlat(world, compatSubject),
                "the freeze twin must not stamp FLAT over a lowered seat");
        ctx.assertTrue(com.slabbed.anchor.SlabAnchorAttachment.isAnchored(world, compatSubject),
                "the freeze twin must anchor the lowered seat exactly as it does for vanilla");

        // The overcorrection guard, stated as such: a compat slab whose seat genuinely IS flush
        // (placed on a full block) must still freeze FLAT, never anchor. This assert does not
        // depend on the fix - the blanket freeze also passes it - it guards the OPPOSITE failure,
        // a placement-seat read that starts reporting depth where there is none. An anchor reads
        // as a half-step, so overcorrecting here would sink flush compat slabs by marker alone.
        BlockPos fullBlockGround = ctx.absolutePos(new BlockPos(7, 1, 1));
        world.setBlockAndUpdate(fullBlockGround, Blocks.STONE.defaultBlockState());
        BlockPos flushCompat = fullBlockGround.above();
        placeCourseOnTop(ctx, world, fullBlockGround,
                shimState(ctx, CANONICAL_NAMESPACE).getBlock());
        double flushCompatDy = SlabSupport.getYOffset(
                world, flushCompat, world.getBlockState(flushCompat));
        ctx.assertTrue(Math.abs(flushCompatDy) <= EPS,
                "a compat slab on a full block is genuinely flush; observed " + flushCompatDy);
        ctx.assertTrue(com.slabbed.anchor.SlabAnchorAttachment.isFrozenFlat(world, flushCompat),
                "a genuinely flush compat slab must still freeze FLAT");
        ctx.assertTrue(!com.slabbed.anchor.SlabAnchorAttachment.isAnchored(world, flushCompat),
                "a genuinely flush compat slab must never be anchored - an anchor reads as depth");
        // Deliberately NOT asserted: the placed slab's own top face as a support for followers.
        // The support role is excluded totally on this line (DY_SPEC L4), authored or not, so a
        // follower reading this face today gets no answer. Whether an AUTHORED compat slab should
        // present its real face is the next law decision, and it is a ruling, not a test's call.
        ctx.succeed();
    }

    /**
     * The ensemble classifier must SEE an authored compat slab. Its guard excluded every compat
     * block as "outside Slabbed's offset authority", which stopped being true when a placed
     * compat slab began recording a height: the live recorder would report such a block coherent
     * no matter how badly it clashed, and the recorder is the instrument a live pass is judged by.
     *
     * <p>Three cells, one run, because the guard is the only difference between them. The vanilla
     * cell calibrates the clash geometry, the unauthored cell holds the exclusion that must
     * remain, and the authored cell is the claim. The offsets are passed in rather than resolved,
     * so this measures the classifier and nothing upstream of it.
     */
    @GameTest(template = TEMPLATE)
    public void anAuthoredCompatSlabIsClassifiedForVerticalCoherence(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos vanillaLower = ctx.absolutePos(new BlockPos(1, 2, 1));
        BlockPos unauthoredLower = ctx.absolutePos(new BlockPos(3, 2, 1));
        BlockPos authoredLower = ctx.absolutePos(new BlockPos(5, 2, 1));
        for (BlockPos lower : new BlockPos[] {vanillaLower, unauthoredLower, authoredLower}) {
            world.setBlockAndUpdate(lower, Blocks.STONE.defaultBlockState());
        }
        world.setBlockAndUpdate(vanillaLower.above(), Blocks.OAK_SLAB.defaultBlockState());
        world.setBlockAndUpdate(unauthoredLower.above(), shimState(ctx, CANONICAL_NAMESPACE));
        world.setBlockAndUpdate(authoredLower.above(), shimState(ctx, CANONICAL_NAMESPACE));
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                        world.getChunkAt(authoredLower.above()), authoredLower.above(), -1),
                "premise: the authored cell must actually carry a placement fact");

        SlabEnsembleCoherence.Verdict vanilla =
                SlabEnsembleCoherence.classifyVerticalPair(world, vanillaLower, 0.0d, -0.5d);
        ctx.assertTrue(vanilla.kind() == SlabEnsembleCoherence.Kind.INTERPENETRATION,
                "calibration: this geometry must read as a clash for a vanilla slab, or the row"
                        + " proves nothing; got " + vanilla.kind());

        SlabEnsembleCoherence.Verdict unauthored =
                SlabEnsembleCoherence.classifyVerticalPair(world, unauthoredLower, 0.0d, -0.5d);
        ctx.assertTrue(unauthored.kind() == SlabEnsembleCoherence.Kind.COHERENT,
                "an unauthored compat surface stays outside the classifier; got "
                        + unauthored.kind());

        SlabEnsembleCoherence.Verdict authored =
                SlabEnsembleCoherence.classifyVerticalPair(world, authoredLower, 0.0d, -0.5d);
        ctx.assertTrue(authored.kind() == vanilla.kind(),
                "an authored compat slab must be classified exactly as its vanilla twin;"
                        + " vanilla=" + vanilla.kind() + " compat=" + authored.kind());
        ctx.succeed();
    }

    /**
     * Same claim for the single-block occupancy rule, in its own row. Folding it into the row
     * above would let that row's earlier assertion fail first and shadow this one, so the
     * mutation meant to prove this claim could never reach it.
     */
    @GameTest(template = TEMPLATE)
    public void anAuthoredCompatSlabIsClassifiedForOccludedOccupancy(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos vanilla = ctx.absolutePos(new BlockPos(1, 3, 1));
        BlockPos unauthored = ctx.absolutePos(new BlockPos(3, 3, 1));
        BlockPos authored = ctx.absolutePos(new BlockPos(5, 3, 1));
        world.setBlockAndUpdate(vanilla, Blocks.OAK_SLAB.defaultBlockState());
        world.setBlockAndUpdate(unauthored, shimState(ctx, CANONICAL_NAMESPACE));
        world.setBlockAndUpdate(authored, shimState(ctx, CANONICAL_NAMESPACE));
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                        world.getChunkAt(authored), authored, -1),
                "premise: the authored cell must actually carry a placement fact");

        ctx.assertTrue(SlabEnsembleCoherence.isOccludedOccupancy(world, vanilla, -0.5d),
                "calibration: a vanilla slab pushed to its own cell floor occupies nothing"
                        + " visible; a false here means the row lost its teeth");
        ctx.assertTrue(!SlabEnsembleCoherence.isOccludedOccupancy(world, unauthored, -0.5d),
                "an unauthored compat surface stays outside the occupancy rule");
        ctx.assertTrue(SlabEnsembleCoherence.isOccludedOccupancy(world, authored, -0.5d),
                "an authored compat slab must be judged for occupancy like its vanilla twin");
        ctx.succeed();
    }

    /**
     * Between placing a compat slab and its height fact arriving from the server, the client must
     * resolve it exactly as it resolves a vanilla slab (maintainer ruling, 2026-08-25: compat slabs
     * follow vanilla slabs, they are meant to be the same).
     *
     * <p>Found live: in that window the compat cell held no fact, so it fell to the unauthored-flush
     * law while a vanilla slab in the identical position fell through to the geometric lanes and
     * seated correctly. The block therefore DREW lowered (the mesh consults the prediction) while
     * its outline, collision and targeting sat half a block higher - the pinpoint hit region and the
     * cantilevered edge placements, both of which vanish once the two agree.
     *
     * <p>What the prediction contributes here is AUTHORSHIP, never a height. The gate asks only
     * "did this client just place this?"; the height still comes from the ordinary lanes, so the
     * mesh-only law on predicted HEIGHTS is intact - interaction still never reads a predicted
     * number. Absent both a fact and a prediction the cell is generated ground and stays flush,
     * which is what keeps world holes shut.
     */
    @GameTest(template = TEMPLATE)
    public void aPredictedCompatSlabResolvesLikeAVanillaOneBeforeItsFactArrives(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        com.slabbed.anchor.ClientRenderDyPrediction.clear();
        try {
            BlockPos vanillaGround = ctx.absolutePos(new BlockPos(1, 1, 1));
            BlockPos generatedGround = ctx.absolutePos(new BlockPos(4, 1, 1));
            BlockPos predictedGround = ctx.absolutePos(new BlockPos(7, 1, 1));
            for (BlockPos g : new BlockPos[] {vanillaGround, generatedGround, predictedGround}) {
                world.setBlockAndUpdate(g, Blocks.STONE.defaultBlockState());
                world.setBlockAndUpdate(g.above(), Blocks.OAK_SLAB.defaultBlockState());
            }
            // No facts anywhere: this is the pre-sync window for all three cells.
            world.setBlockAndUpdate(vanillaGround.above(2), Blocks.STONE_SLAB.defaultBlockState());
            world.setBlockAndUpdate(generatedGround.above(2), shimState(ctx, CANONICAL_NAMESPACE));
            world.setBlockAndUpdate(predictedGround.above(2), shimState(ctx, CANONICAL_NAMESPACE));

            double vanillaDy = SlabSupport.getYOffset(world, vanillaGround.above(2),
                    world.getBlockState(vanillaGround.above(2)));
            ctx.assertTrue(vanillaDy < -EPS,
                    "calibration: a factless vanilla slab on a flush slab must still seat via the"
                            + " geometric lanes; a flush control means this row lost its teeth,"
                            + " observed " + vanillaDy);

            double generatedDy = SlabSupport.getYOffset(world, generatedGround.above(2),
                    world.getBlockState(generatedGround.above(2)));
            ctx.assertTrue(Math.abs(generatedDy) <= EPS,
                    "world-hole pin: a compat cell with neither fact nor prediction is generated"
                            + " ground and stays flush; observed " + generatedDy);

            BlockPos predicted = predictedGround.above(2);
            com.slabbed.anchor.ClientRenderDyPrediction.record(predicted.asLong(), -1);
            double predictedDy = SlabSupport.getYOffset(
                    world, predicted, world.getBlockState(predicted));
            ctx.assertTrue(Math.abs(predictedDy - vanillaDy) <= EPS,
                    "a compat slab this client just placed must resolve exactly like the vanilla"
                            + " twin while its fact is in flight; vanilla=" + vanillaDy
                            + " compat=" + predictedDy);
        } finally {
            com.slabbed.anchor.ClientRenderDyPrediction.clear();
        }
        ctx.succeed();
    }

    /**
     * Calibration twin, green today and after: an oblique ray crossing a LOWERED VANILLA slab's
     * top plane reports the face it actually crossed. Its own row so a break here reads as
     * "the environment stopped producing correct faces" and never as the compat claim below.
     */
    @GameTest(template = TEMPLATE)
    public void aLoweredVanillaSlabReportsTheFaceTheRayCrossed(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos subject = ctx.absolutePos(new BlockPos(3, 3, 3));
        double dy = authorLoweredSlabAt(ctx, world, subject, Blocks.STONE_SLAB);
        ctx.assertTrue(dy < -EPS, "premise: the vanilla twin must be lowered; observed " + dy);
        BlockHitResult hit = obliqueTopPlaneRay(world, subject, dy);
        ctx.assertTrue(hit != null && hit.getType() == HitResult.Type.BLOCK
                        && subject.equals(hit.getBlockPos()),
                "premise: the ray must actually hit the vanilla twin");
        ctx.assertTrue(hit.getDirection() == Direction.UP,
                "a ray crossing a lowered vanilla slab's TOP plane must report UP; got "
                        + hit.getDirection());
        ctx.succeed();
    }

    /**
     * A ray that crosses a lowered COMPAT slab's top plane must report the face it crossed, and
     * the block it then places must land on top rather than beside.
     *
     * <p>Found live: the reported face was horizontal while the hit point sat exactly on the top
     * plane, so placement - which uses clickedPos.relative(face) - put the block in a side
     * neighbour. The maintainer saw blocks landing cantilevered when aiming squarely at a top
     * surface, with only a pinpoint region in the middle of the face working (a near-vertical ray
     * enters the oversized basis through its TOP, so the donated face is UP and the graft is a
     * no-op).
     *
     * <p>The face assertion comes FIRST on purpose: it is the one that names the defect. A
     * shape-internals check reading "basisTop=0.5 outlineTop=0.0" would be the first red a reader
     * saw otherwise, and it does not mention targeting at all.
     */
    @GameTest(template = TEMPLATE)
    public void aLoweredCompatSlabReportsTheFaceTheRayCrossed(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos subject = ctx.absolutePos(new BlockPos(3, 3, 3));
        double dy = authorLoweredSlabAt(
                ctx, world, subject, shimState(ctx, CANONICAL_NAMESPACE).getBlock());
        ctx.assertTrue(dy < -EPS, "premise: the compat slab must be lowered; observed " + dy);
        ctx.assertTrue(com.slabbed.anchor.SlabAnchorAttachment.isAnchored(world, subject),
                "premise: the compat slab must be ANCHORED, or the raycast-basis branch this row"
                        + " exists to pin is never entered and the row passes vacuously");

        BlockHitResult hit = obliqueTopPlaneRay(world, subject, dy);
        ctx.assertTrue(hit != null && hit.getType() == HitResult.Type.BLOCK
                        && subject.equals(hit.getBlockPos()),
                "premise: the ray must actually hit the compat slab");
        double localY = hit.getLocation().y - subject.getY();
        ctx.assertTrue(hit.getDirection() == Direction.UP,
                "a ray crossing a lowered compat slab's TOP plane must report UP, not a side:"
                        + " got " + hit.getDirection() + " at local y " + localY
                        + " (outline top " + dy + ") - a side face here sends the placement to"
                        + " clickedPos.relative(face), i.e. cantilevered off the side");

        // The consequence, not just the number: a wrong face is only a defect because of where it
        // puts the block. Drive the real transaction from the raycast's own hit.
        Player player = ctx.makeMockPlayer();
        player.setPos(subject.getX() + 3.5D, subject.getY() + 1.0D, subject.getZ() + 0.5D);
        ItemStack stack = new ItemStack(Blocks.STONE_SLAB);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        ctx.assertTrue(!world.getBlockState(subject.above()).isAir(),
                "the block must land ON the compat slab; the cell above is empty, so it went"
                        + " somewhere else");
        ctx.succeed();
    }


    /**
     * Calibration twin, green before and after: a hanging stalactite attaches to a VANILLA top
     * slab's underside. Vanilla alone refuses that attachment - a top slab's DOWN face sits at
     * y=0.5 and is not a full square - so this row also proves the underside-support lane is
     * reachable at all. Its own row, so a break here reads as "the ceiling lane stopped working"
     * and never as the compat claim below.
     */
    @GameTest(template = TEMPLATE)
    public void aVanillaTopSlabUndersideHoldsAHangingDripstone(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos slab = ctx.absolutePos(new BlockPos(3, 5, 3));
        seatCeilingSlab(world, slab, Blocks.STONE_SLAB.defaultBlockState());

        BlockState slabState = world.getBlockState(slab);
        ctx.assertTrue(slabState.isFaceSturdy(world, slab, Direction.DOWN),
                "a top slab must present a sturdy underside, or nothing can hang from it");

        placeAgainstUndersideWithHeldItem(ctx, Blocks.POINTED_DRIPSTONE, slab);
        BlockState hung = world.getBlockState(slab.below());
        ctx.assertTrue(hung.is(Blocks.POINTED_DRIPSTONE),
                "a dripstone aimed at a vanilla top slab's underside must land underneath it;"
                        + " found " + hung.getBlock().getName().getString());
        ctx.assertTrue(hung.getValue(BlockStateProperties.VERTICAL_DIRECTION) == Direction.DOWN,
                "it must hang as a stalactite, not stand as a stalagmite");
        ctx.succeed();
    }

    /**
     * The support question is geometric (LAW.md clause 2): a slab holds what hangs from it because
     * of where its underside is, never because of who registered it. A compat top slab is a slab -
     * it extends the vanilla slab block, carries the slab type property, and is in the vanilla
     * slabs tag - so its underside must hold a stalactite exactly as a vanilla one does.
     *
     * <p>The consequence, not just the predicate: vanilla decides a hanging tip by asking the
     * block above for a sturdy DOWN face, and when that answer is no it retries the opposite
     * direction - which stands the dripstone up on whatever is below instead, or refuses the
     * placement outright. Both halves are asserted here, including that the cell ABOVE the slab
     * stays empty, because "it went somewhere else" is the reported shape of this defect.
     *
     * <p>The control-namespace twin is an identical block class outside the owned namespaces. It
     * makes registry namespace the only variable in the row, and a red there means the row lost
     * its teeth rather than that the claim regressed.
     */
    @GameTest(template = TEMPLATE)
    public void aCompatTopSlabUndersideHoldsAHangingDripstone(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos compatSlab = ctx.absolutePos(new BlockPos(3, 5, 3));
        BlockPos controlSlab = ctx.absolutePos(new BlockPos(6, 5, 3));
        BlockState compatTop = shimState(ctx, CANONICAL_NAMESPACE)
                .setValue(SlabBlock.TYPE, SlabType.TOP);
        BlockState controlTop = shimState(ctx, CONTROL_NAMESPACE)
                .setValue(SlabBlock.TYPE, SlabType.TOP);

        ctx.assertTrue(CompatHooks.shouldSkipOffset(compatTop),
                "premise: the subject must be a compat-owned state, or this row measures nothing");
        ctx.assertTrue(compatTop.is(net.minecraft.tags.BlockTags.SLABS),
                "premise: the subject must be in the vanilla slabs tag, which is the geometric"
                        + " opt-in this claim rests on");

        seatCeilingSlab(world, compatSlab, compatTop);
        seatCeilingSlab(world, controlSlab, controlTop);

        ctx.assertTrue(world.getBlockState(controlSlab).isFaceSturdy(world, controlSlab, Direction.DOWN),
                "calibration: the control-namespace twin must present a sturdy underside;"
                        + " a false here means the row lost its teeth");

        ctx.assertTrue(world.getBlockState(compatSlab).isFaceSturdy(world, compatSlab, Direction.DOWN),
                "a compat top slab's underside must be sturdy, exactly as its control twin's is:"
                        + " vanilla reads this face to decide whether a stalactite may hang");

        placeAgainstUndersideWithHeldItem(ctx, Blocks.POINTED_DRIPSTONE, compatSlab);
        BlockState hung = world.getBlockState(compatSlab.below());
        ctx.assertTrue(hung.is(Blocks.POINTED_DRIPSTONE),
                "a dripstone aimed at a compat top slab's underside must land underneath it;"
                        + " found " + hung.getBlock().getName().getString());
        ctx.assertTrue(hung.getValue(BlockStateProperties.VERTICAL_DIRECTION) == Direction.DOWN,
                "it must hang as a stalactite; an UP tip means the placement fell through to the"
                        + " opposite direction because the underside was refused");
        ctx.assertTrue(world.getBlockState(compatSlab.above()).isAir(),
                "and it must not land on TOP of the slab that was aimed at from below");
        ctx.succeed();
    }

    /**
     * The top face is a fact about geometry, not about how the other mod spells its block names.
     * The named-surface arm that admits a compat slab today matches on a registry path suffix, so
     * an otherwise identical compat slab named anything else presents no standable top face at
     * all - eligibility decided by a compatibility implementation name, one level down from the
     * namespace test and forbidden by the same LAW.md clause 2.
     *
     * <p>Both compat cells are in the vanilla slabs tag and identical in every way a player can
     * see; only the registry path differs. The suffix-matching twin calibrates in the same run, so
     * a row that stopped reaching the compat lane at all fails rather than passing quietly.
     */
    @GameTest(template = TEMPLATE)
    public void aCompatBottomSlabPresentsAStandableTopFaceWhateverItIsNamed(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos vanillaSlab = ctx.absolutePos(new BlockPos(1, 2, 1));
        BlockPos suffixedSlab = ctx.absolutePos(new BlockPos(3, 2, 1));
        BlockPos unsuffixedSlab = ctx.absolutePos(new BlockPos(5, 2, 1));
        world.setBlockAndUpdate(vanillaSlab, Blocks.STONE_SLAB.defaultBlockState());
        world.setBlockAndUpdate(suffixedSlab, shimState(ctx, CANONICAL_NAMESPACE));
        world.setBlockAndUpdate(unsuffixedSlab, shimState(ctx, CANONICAL_NAMESPACE, UNSUFFIXED_BLOCK_NAME));

        BlockState unsuffixed = world.getBlockState(unsuffixedSlab);
        ctx.assertTrue(unsuffixed.is(net.minecraft.tags.BlockTags.SLABS),
                "premise: the unsuffixed cell must be in the vanilla slabs tag, which is the"
                        + " geometric opt-in this claim rests on");
        ctx.assertTrue(CompatHooks.shouldSkipOffset(unsuffixed),
                "premise: it must be a compat-owned state, or this row measures nothing");

        ctx.assertTrue(Block.canSupportCenter(world, vanillaSlab, Direction.UP),
                "calibration: a vanilla bottom slab must support a centred object;"
                        + " a false here means the row lost its teeth");
        ctx.assertTrue(Block.canSupportCenter(world, suffixedSlab, Direction.UP),
                "calibration: and so must the suffix-matching compat twin");

        ctx.assertTrue(Block.canSupportCenter(world, unsuffixedSlab, Direction.UP),
                "a compat bottom slab must support a centred object whatever its registry path is"
                        + " spelled; the two compat cells differ only in name");
        ctx.assertTrue(unsuffixed.isFaceSturdy(world, unsuffixedSlab, Direction.UP),
                "and its top face must read sturdy through the face overload as well");
        ctx.succeed();
    }

    /**
     * A hanging sign under a compat top slab must attach to it exactly as under a vanilla one:
     * survive there at all, and take the ceiling model rather than the mid-chain loop. Vanilla
     * decides survival by asking the block above for a sturdy underside and picks the model from
     * whether that block's collision face is full, which a slab's never is - so both answers come
     * from Slabbed, and both must be blind to who registered the slab.
     */
    @GameTest(template = TEMPLATE)
    public void aHangingSignAttachesToACompatTopSlabAsToAVanillaOne(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos vanillaSlab = ctx.absolutePos(new BlockPos(2, 5, 3));
        BlockPos compatSlab = ctx.absolutePos(new BlockPos(5, 5, 3));
        seatCeilingSlab(world, vanillaSlab,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        seatCeilingSlab(world, compatSlab,
                shimState(ctx, CANONICAL_NAMESPACE).setValue(SlabBlock.TYPE, SlabType.TOP));

        placeAgainstUndersideWithHeldItem(ctx, Blocks.OAK_HANGING_SIGN, vanillaSlab);
        BlockState vanillaSign = world.getBlockState(vanillaSlab.below());
        ctx.assertTrue(vanillaSign.is(Blocks.OAK_HANGING_SIGN),
                "calibration: a hanging sign must survive under a vanilla top slab;"
                        + " a false here means the row lost its teeth");
        ctx.assertTrue(!vanillaSign.getValue(CeilingHangingSignBlock.ATTACHED),
                "calibration: and take the ceiling model directly under it");

        placeAgainstUndersideWithHeldItem(ctx, Blocks.OAK_HANGING_SIGN, compatSlab);
        BlockState compatSign = world.getBlockState(compatSlab.below());
        ctx.assertTrue(compatSign.is(Blocks.OAK_HANGING_SIGN),
                "a hanging sign must survive under a compat top slab, exactly as under its"
                        + " vanilla twin");
        ctx.assertTrue(!compatSign.getValue(CeilingHangingSignBlock.ATTACHED),
                "and take the ceiling model rather than the mid-chain loop");
        ctx.succeed();
    }



    /**
     * What an EXISTING world does. A chain placed under a compat top slab before the support
     * question became geometric was not ceiling-attached, so it was not excluded from the store and
     * a durable height is sitting in its chunk. The obvious worry is that such a chain keeps the
     * older height while a freshly placed one follows its ceiling - the same arrangement behaving
     * two ways depending on when it was built.
     *
     * <p>It does not, and the reason is that the stored-height read is ITSELF gated on the
     * ceiling-follower predicate: a subject the ceiling role owns never consults the store at all.
     * The older fact goes inert rather than authoritative, so the world converts to the vanilla
     * behaviour instead of splitting from it. Pinned here because nothing about a fresh placement
     * can reach this, and every world that already exists is on this path.
     *
     * <p>The general form of this - a ceiling follower ignoring a stale fact - is already pinned
     * for a VANILLA ceiling in the resolver suite. What is new is that a compat ceiling confers
     * the role at all, so this path did not exist to be walked before; the row extends an existing
     * invariant to a newly reachable subject rather than discovering one.
     *
     * <p>The control cell is the calibration and is not optional: it proves a written fact governs
     * an ordinary subject in this run, so the claim below reads as "the ceiling role declines it"
     * rather than "the store was not working".
     */
    @GameTest(template = TEMPLATE)
    public void aStoredHeightUnderACompatCeilingYieldsToTheCeilingRole(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();

        BlockPos controlGround = ctx.absolutePos(new BlockPos(1, 2, 1));
        world.setBlockAndUpdate(controlGround, Blocks.STONE.defaultBlockState());
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                        world.getChunkAt(controlGround), controlGround, -1),
                "premise: writing a placement fact must succeed");
        double controlDy = SlabSupport.getYOffset(
                world, controlGround, world.getBlockState(controlGround));
        ctx.assertTrue(Math.abs(controlDy + 0.5) <= EPS,
                "calibration: a written fact must govern an ordinary subject; a miss here means the"
                        + " store is not live in this run and the claim below proves nothing;"
                        + " observed " + controlDy);

        BlockPos ceiling = ctx.absolutePos(new BlockPos(5, 4, 3));
        BlockPos subject = ceiling.below();
        BlockPos floor = subject.below();
        world.setBlockAndUpdate(floor, Blocks.STONE_SLAB.defaultBlockState());
        world.setBlockAndUpdate(subject, Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(ceiling,
                shimState(ctx, CANONICAL_NAMESPACE).setValue(SlabBlock.TYPE, SlabType.TOP));
        placeWithHeldItem(ctx, Blocks.CHAIN, subject, floor);
        ctx.assertTrue(SlabSupport.isDynamicCeilingFollower(world, subject, world.getBlockState(subject)),
                "premise: the compat ceiling must own the subject, or this row is not the old-world"
                        + " path it exists for");
        ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(
                        world.getChunkAt(subject), subject).isEmpty(),
                "premise: a ceiling follower must not be given a fact by its own placement, which is"
                        + " what makes the written one below stand in for an older world");
        double factless = SlabSupport.getYOffset(world, subject, world.getBlockState(subject));

        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                        world.getChunkAt(subject), subject, -1),
                "premise: the older world's height must be written onto the subject");
        double withFact = SlabSupport.getYOffset(world, subject, world.getBlockState(subject));

        ctx.assertTrue(Math.abs(withFact - factless) <= EPS,
                "a ceiling-attached subject must resolve the same with or without a stored height,"
                        + " so an existing world converts to the ceiling behaviour instead of"
                        + " splitting from a fresh one; factless=" + factless
                        + " withFact=" + withFact);
        ctx.succeed();
    }

    /**
     * Baseline for the row below, and the proof that its scene is live: a factless chain under a
     * VANILLA top slab is ceiling-attached, so it does not inherit the lowered floor beneath it -
     * and breaking that ceiling hands it back. The subject MOVES, and that is long-standing vanilla-
     * side behaviour this campaign did not introduce and does not change. A chain receives no
     * placement anchor, so nothing protects it here; the law's protected set is elsewhere.
     *
     * <p>Asserted as a movement rather than a height so the row states the mechanism it exists to
     * establish. Without a lowered floor beneath the subject both classifications resolve flush,
     * the break has no height to disturb, and a row built that way measures nothing.
     */
    @GameTest(template = TEMPLATE)
    public void aVanillaCeilingBreakHandsAFactlessChainBackToTheFloor(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos ceiling = ctx.absolutePos(new BlockPos(3, 4, 3));
        double[] seen = ceilingBreakHeights(ctx, world, ceiling,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        ctx.assertTrue(Math.abs(seen[0] - seen[1]) > EPS,
                "a factless chain must be handed back to the floor when its vanilla ceiling breaks;"
                        + " before=" + seen[0] + " after=" + seen[1] + " - equal values mean this"
                        + " scene never reached the resolver and pins nothing");
        ctx.succeed();
    }

    /**
     * A compat ceiling must behave on a neighbour update exactly as its vanilla twin does. Before
     * the support question became geometric a compat top slab conferred no ceiling role at all, so
     * the subject beneath it kept inheriting the floor and the break changed nothing - a compat
     * ceiling was silently a different kind of ceiling.
     *
     * <p>Both scenes resolve in one run and are compared to each other rather than to a constant,
     * so this row cannot pass by both sides agreeing on nothing: the vanilla twin's own row above
     * establishes that the movement is real.
     */
    @GameTest(template = TEMPLATE)
    public void aCompatCeilingBreakMatchesItsVanillaTwin(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos vanillaCeiling = ctx.absolutePos(new BlockPos(2, 4, 3));
        BlockPos compatCeiling = ctx.absolutePos(new BlockPos(6, 4, 3));

        double[] vanilla = ceilingBreakHeights(ctx, world, vanillaCeiling,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        double[] compat = ceilingBreakHeights(ctx, world, compatCeiling,
                shimState(ctx, CANONICAL_NAMESPACE).setValue(SlabBlock.TYPE, SlabType.TOP));

        ctx.assertTrue(Math.abs(compat[0] - vanilla[0]) <= EPS,
                "under an intact compat ceiling the subject must sit where its vanilla twin sits;"
                        + " vanilla=" + vanilla[0] + " compat=" + compat[0]);
        ctx.assertTrue(Math.abs(compat[1] - vanilla[1]) <= EPS,
                "and after the break it must land where its vanilla twin lands; vanilla="
                        + vanilla[1] + " compat=" + compat[1]);
        ctx.succeed();
    }

    /**
     * A hanger tracks the REAL underside of the ceiling it hangs from, whoever registered that
     * ceiling. The tracking leg was gated on the compat namespace, so a hanger under an authored -
     * and therefore lowered - compat surface fell past the leg to grid height and interpenetrated
     * the very surface it hung from. Admission was already fixed; where it lands was not.
     *
     * <p>Namespace is the wrong discriminator here for the same reason it is everywhere else: an
     * unauthored compat surface stays flush and so carries nothing anywhere, while an authored one
     * holds a height that everything attached to it owes. Authorship answers both without naming a
     * mod.
     *
     * <p>Compared against a vanilla twin resolved in the same run, with the twin's own descent
     * asserted first: two zeroes agreeing is exactly the failure this row exists to catch.
     */
    @GameTest(template = TEMPLATE)
    public void aHangerUnderAnAuthoredCompatCeilingTracksItLikeAVanillaOne(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        double vanilla = hangerDyUnderAuthoredCeiling(ctx, world, ctx.absolutePos(new BlockPos(1, 4, 1)),
                Blocks.STONE_SLAB.defaultBlockState(), -2);
        double compat = hangerDyUnderAuthoredCeiling(ctx, world, ctx.absolutePos(new BlockPos(4, 4, 1)),
                shimState(ctx, CANONICAL_NAMESPACE), -2);

        ctx.assertTrue(vanilla < -EPS,
                "calibration: under a lowered VANILLA ceiling the hanger must descend with it; a"
                        + " flush answer means this run resolves no ceiling at all and the claim"
                        + " below proves nothing; observed " + vanilla);
        ctx.assertTrue(Math.abs(compat - vanilla) <= EPS,
                "a hanger under an authored compat ceiling must hang where its vanilla twin hangs;"
                        + " vanilla=" + vanilla + " compat=" + compat);
        ctx.succeed();
    }

    /**
     * The half-block merge a TOP-slab ceiling owes its hanger, on a compat ceiling. A TOP slab's
     * underside sits half a block above its cell floor, so a hanger tracking it needs {@code +0.5}
     * against the slab's own descent - and that compensation was keyed on the same support-admission
     * predicate the compat gate suppresses.
     *
     * <p>Deliberately separate from the row above, which uses a BOTTOM-slab ceiling and therefore
     * needs no compensation. Converting only the gate and leaving the compensation keyed on
     * admission produces a hanger half a block low here while the row above stays green - the
     * half-fix this row exists to fail.
     */
    @GameTest(template = TEMPLATE)
    public void aHangerUnderAnAuthoredCompatTopSlabTakesTheSameMergeAsAVanillaOne(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockState vanillaTop = Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.TOP);
        BlockState compatTop = shimState(ctx, CANONICAL_NAMESPACE)
                .setValue(SlabBlock.TYPE, SlabType.TOP);
        double vanilla = hangerDyUnderAuthoredCeiling(
                ctx, world, ctx.absolutePos(new BlockPos(1, 4, 3)), vanillaTop, -2);
        double compat = hangerDyUnderAuthoredCeiling(
                ctx, world, ctx.absolutePos(new BlockPos(4, 4, 3)), compatTop, -2);

        ctx.assertTrue(vanilla < -EPS,
                "calibration: a lowered VANILLA top slab must still move its hanger; observed "
                        + vanilla);
        ctx.assertTrue(vanilla > -1.0d + EPS,
                "calibration: and it must move it by LESS than the slab's own descent, or the merge"
                        + " compensation is not in this run and the comparison below is blind to it;"
                        + " observed " + vanilla);
        ctx.assertTrue(Math.abs(compat - vanilla) <= EPS,
                "a hanger under an authored compat TOP slab must take the same merge its vanilla"
                        + " twin takes; vanilla=" + vanilla + " compat=" + compat);
        ctx.succeed();
    }

    /**
     * The second hanger in a column follows the first. The cursor walk that carries a cascade up to
     * its real ceiling reads the same namespace gate as the direct leg, so under a compat ceiling
     * the whole column stayed at grid height - which is how one wrong height spreads: the flat
     * cascade then becomes a flat surface for anything placed against it.
     *
     * <p>Both courses resolve in one run against a vanilla-rooted twin. A row that measured only
     * the first hanger would pass with the cursor walk deleted, because the direct leg answers the
     * first one.
     */
    @GameTest(template = TEMPLATE)
    public void aCascadedHangerUnderACompatCeilingFollowsLikeAVanillaOne(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        double[] vanilla = cascadedHangerDysUnderAuthoredCeiling(ctx, world,
                ctx.absolutePos(new BlockPos(1, 5, 5)), Blocks.STONE_SLAB.defaultBlockState(), -2);
        double[] compat = cascadedHangerDysUnderAuthoredCeiling(ctx, world,
                ctx.absolutePos(new BlockPos(4, 5, 5)), shimState(ctx, CANONICAL_NAMESPACE), -2);

        ctx.assertTrue(vanilla[1] < -EPS,
                "calibration: the SECOND hanger under a lowered vanilla ceiling must descend too;"
                        + " observed " + vanilla[1]);
        ctx.assertTrue(Math.abs(compat[0] - vanilla[0]) <= EPS,
                "the first hanger must match its vanilla twin; vanilla=" + vanilla[0]
                        + " compat=" + compat[0]);
        ctx.assertTrue(Math.abs(compat[1] - vanilla[1]) <= EPS,
                "and the cascaded one must match too, or a compat column flattens below its own"
                        + " ceiling; vanilla=" + vanilla[1] + " compat=" + compat[1]);
        ctx.succeed();
    }

    /**
     * The live configuration, arm by arm. The rows above author their ceiling with a placement
     * FACT; the world that reported this defect did not - its compat ceilings carried a
     * FREEZE-ON-PLACE stamp or an anchor as authorship, and a compound-side mark as height.
     * Those are different arms of the same authorship test, and per-term mutation found the
     * fact arm was the only one any row exercised. This row drives the STAMP arm.
     *
     * <p>The stamp and the mark answer different questions and neither substitutes for the
     * other. Alone, the stamp pins the cell FLAT - a fixture of just a stamped ceiling is
     * vacuous, hanger at zero either way, which is why the obvious row never existed. It is
     * the compound-side mark, read before the stamp's flat return, that lowers the cell; the
     * stamp's remaining job is to be AUTHORSHIP, the thing that lets the compat gate treat the
     * cell as this mod's own work. Delete the stamp's arm from the authorship test and the
     * ceiling resolves lowered while its hanger is refused the tracking leg - the exact split
     * this row exists to fail on.
     */
    @GameTest(template = TEMPLATE)
    public void aStampAuthoredCompatCeilingWithACompoundHeightTracksLikeAVanillaOne(
            GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        double vanilla = hangerDyUnderStampAuthoredCompoundCeiling(ctx, world,
                ctx.absolutePos(new BlockPos(1, 4, 1)), Blocks.STONE_SLAB.defaultBlockState());
        double compat = hangerDyUnderStampAuthoredCompoundCeiling(ctx, world,
                ctx.absolutePos(new BlockPos(5, 4, 1)), shimState(ctx, CANONICAL_NAMESPACE));

        ctx.assertTrue(vanilla < -EPS,
                "calibration: the hanger under the stamped-and-marked VANILLA ceiling must descend"
                        + " with it; observed " + vanilla);
        ctx.assertTrue(Math.abs(compat - vanilla) <= EPS,
                "a compat ceiling whose authorship is a freeze-on-place stamp must be tracked"
                        + " exactly as its vanilla twin is - a stamp is authorship no less than a"
                        + " stored fact, and it is what the reported world's ceilings actually"
                        + " carried; vanilla=" + vanilla + " compat=" + compat);
        ctx.succeed();
    }

    /**
     * Twin of the row above for the ANCHOR arm. The anchor is earned through a real placement
     * onto a lowered support - the only production door that writes one for a slab - and the
     * placement fact is then removed, which is what a world whose store predates the fact
     * schema looks like: the anchor is the only authorship left. The support is removed too,
     * so nothing below can seat the hanger by geometry and the ceiling alone answers.
     */
    @GameTest(template = TEMPLATE)
    public void anAnchorAuthoredCompatCeilingWithACompoundHeightTracksLikeAVanillaOne(
            GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        double vanilla = hangerDyUnderAnchorAuthoredCompoundCeiling(ctx, world,
                ctx.absolutePos(new BlockPos(1, 3, 5)), Blocks.STONE_SLAB);
        double compat = hangerDyUnderAnchorAuthoredCompoundCeiling(ctx, world,
                ctx.absolutePos(new BlockPos(5, 3, 5)),
                shimState(ctx, CANONICAL_NAMESPACE).getBlock());

        ctx.assertTrue(vanilla < -EPS,
                "calibration: the hanger under the anchored-and-marked VANILLA ceiling must descend"
                        + " with it; observed " + vanilla);
        ctx.assertTrue(Math.abs(compat - vanilla) <= EPS,
                "a compat ceiling whose only authorship is an anchor must be tracked exactly as its"
                        + " vanilla twin is; vanilla=" + vanilla + " compat=" + compat);
        ctx.succeed();
    }

    /**
     * Variant A scaffold: the seat is WRITTEN (generated-looking, no fact), stamped flat by the
     * production freeze hook, then lowered by a compound-side mark. Premises pin the exact
     * authorship configuration - stamp present, anchor absent, fact absent - so each compound
     * row discriminates one arm and a mutation of the other arm cannot redden it.
     */
    private static double hangerDyUnderStampAuthoredCompoundCeiling(
            GameTestHelper ctx, ServerLevel world, BlockPos seat, BlockState seatState) {
        BlockPos hangerPos = seat.below();
        world.setBlockAndUpdate(hangerPos, Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(seat, seatState);
        SlabAnchorAttachment.freezeLoweredOnPlace(world, seat, world.getBlockState(seat));
        ctx.assertTrue(SlabAnchorAttachment.isFrozenFlat(world, seat),
                "premise: the flush seat must take the freeze-on-place FLAT stamp");
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(world, seat),
                "premise: no anchor may exist here, or this row measures the anchor arm too and"
                        + " the per-term discrimination is lost");
        ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(
                        world.getChunkAt(seat), seat).isEmpty(),
                "premise: a written seat must hold no placement fact");
        return compoundMarkAndHang(ctx, world, seat, seat.south());
    }

    /**
     * Variant B scaffold: the seat is PLACED onto a lowered support so the freeze hook writes a
     * real anchor, then the fact is stripped and the support removed - an anchor-only cell over
     * open air, with the compound mark as its height.
     */
    private static double hangerDyUnderAnchorAuthoredCompoundCeiling(
            GameTestHelper ctx, ServerLevel world, BlockPos seat, Block slab) {
        BlockPos support = seat.below();
        BlockPos ground = seat.below(2);
        world.setBlockAndUpdate(seat, Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        authorLoweredStone(ctx, world, support);
        placeWithHeldItem(ctx, slab, seat, support);

        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, seat),
                "premise: placement onto a lowered support must anchor the seat");
        ctx.assertTrue(!SlabAnchorAttachment.isFrozenFlat(world, seat),
                "premise: no FLAT stamp may exist here, or this row measures the stamp arm too");
        ctx.assertTrue(SlabPlacementHeightAttachment.remove(world.getChunkAt(seat), seat),
                "premise: the placement must have written a fact for this row to strip");
        ctx.assertTrue(!Double.isFinite(SlabPlacementHeightAttachment.storedOffset(world, seat)),
                "premise: no fact may remain, or this row measures the fact arm again");

        // Clear the support AND its authoring fact, so the ceiling alone answers for the hanger.
        world.setBlockAndUpdate(support, Blocks.AIR.defaultBlockState());
        SlabPlacementHeightAttachment.remove(world.getChunkAt(support), support);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, seat),
                "premise: the anchor must survive its support's removal, or the arm under test is"
                        + " gone before the measurement");
        return compoundMarkAndHang(ctx, world, seat, seat.south());
    }

    /**
     * Shared tail: authors a compound-side source beside the seat, applies the mark, asserts the
     * seat resolves a FULL BLOCK down THROUGH its stamp - the exact coexistence the live capture
     * shows - and returns the resolved dy of a dripstone hung beneath it.
     */
    private static double compoundMarkAndHang(
            GameTestHelper ctx, ServerLevel world, BlockPos seat, BlockPos sourcePos) {
        String twin = CompatHooks.shouldSkipOffset(world.getBlockState(seat))
                ? "COMPAT twin" : "VANILLA twin";
        world.setBlockAndUpdate(sourcePos, Blocks.STONE.defaultBlockState());
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                        world.getChunkAt(sourcePos), sourcePos, -2),
                "premise: the compound source's height must be writable");
        double sourceDy = SlabSupport.getYOffset(
                world, sourcePos, world.getBlockState(sourcePos));
        ctx.assertTrue(Math.abs(sourceDy + 1.0) <= EPS,
                "premise: the compound source must read a full block down, or the mark writer"
                        + " refuses it; observed " + sourceDy);
        SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(world, seat,
                world.getBlockState(seat), sourcePos, world.getBlockState(sourcePos));
        ctx.assertTrue(SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(
                        world, seat, world.getBlockState(seat)),
                "premise: the mark writer must accept this scene - a refusal here means the"
                        + " qualifier wants something the source premise did not measure");
        double seatDy = SlabSupport.getYOffset(world, seat, world.getBlockState(seat));
        ctx.assertTrue(Math.abs(seatDy + 1.0) <= EPS,
                twin + " premise: the compound mark must lower the seat THROUGH its stamp - the live"
                        + " capture shows exactly this coexistence, and a flush seat here means"
                        + " the scene is not the one the defect ran through; observed " + seatDy);

        BlockPos hangerPos = seat.below();
        world.setBlockAndUpdate(hangerPos, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN));
        ctx.assertTrue(world.getBlockState(hangerPos).is(Blocks.POINTED_DRIPSTONE),
                "premise: the hanger must survive under this ceiling");
        ctx.assertTrue(SlabSupport.isDynamicCeilingFollower(
                        world, hangerPos, world.getBlockState(hangerPos)),
                "premise: the hanger must be ceiling-owned, or this row measures the floor lane");
        return SlabSupport.getYOffset(world, hangerPos, world.getBlockState(hangerPos));
    }

    /**
     * Hangs a downward dripstone under {@code ceilingState} and returns its resolved dy, having
     * first made the ceiling authored AND lowered.
     *
     * <p>The height is written onto the ceiling rather than placed into it because the ceiling is
     * the thing under test: aiming a real transaction at it from below would decide the hanger's
     * lane at the same time. A written fact is the same authorship record a placement leaves, and
     * the row asserts the ceiling actually resolved lowered before hanging anything from it.
     */
    private static double hangerDyUnderAuthoredCeiling(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos ceiling,
            BlockState ceilingState,
            int ceilingHalfSteps) {
        return cascadedHangerDysUnderAuthoredCeiling(
                ctx, world, ceiling, ceilingState, ceilingHalfSteps)[0];
    }

    /** As above, returning the direct hanger's dy and the one cascaded below it. */
    private static double[] cascadedHangerDysUnderAuthoredCeiling(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos ceiling,
            BlockState ceilingState,
            int ceilingHalfSteps) {
        BlockPos first = ceiling.below();
        BlockPos second = ceiling.below(2);
        world.setBlockAndUpdate(first, Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(second, Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(ceiling, ceilingState);
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                        world.getChunkAt(ceiling), ceiling, ceilingHalfSteps),
                "premise: the ceiling must be able to hold a placement height");
        double ceilingDy = SlabSupport.getYOffset(world, ceiling, world.getBlockState(ceiling));
        ctx.assertTrue(ceilingDy < -EPS,
                "premise: the ceiling must resolve lowered, or its hanger has nothing to track;"
                        + " observed " + ceilingDy);

        BlockState hanger = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN);
        world.setBlockAndUpdate(first, hanger);
        world.setBlockAndUpdate(second, hanger);
        for (BlockPos pos : new BlockPos[] {first, second}) {
            ctx.assertTrue(world.getBlockState(pos).is(Blocks.POINTED_DRIPSTONE),
                    "premise: the hanger must survive under this ceiling; it did not at " + pos);
            ctx.assertTrue(SlabSupport.isDynamicCeilingFollower(
                            world, pos, world.getBlockState(pos)),
                    "premise: the hanger must be ceiling-owned, or this row measures the floor lane");
        }
        return new double[] {
                SlabSupport.getYOffset(world, first, world.getBlockState(first)),
                SlabSupport.getYOffset(world, second, world.getBlockState(second)),
        };
    }

    /**
     * Places a chain on a lowered floor under {@code ceilingState} and returns its rendered bottom
     * before and after the ceiling is broken. Both classification premises are asserted around the
     * break, so a caller cannot read two equal numbers out of a scene that never classified at all.
     */
    private static double[] ceilingBreakHeights(
            GameTestHelper ctx, ServerLevel world, BlockPos ceiling, BlockState ceilingState) {
        BlockPos subject = ceiling.below();
        BlockPos floor = subject.below();
        world.setBlockAndUpdate(floor, Blocks.STONE_SLAB.defaultBlockState());
        world.setBlockAndUpdate(subject, Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(ceiling, ceilingState);
        placeWithHeldItem(ctx, Blocks.CHAIN, subject, floor);
        ctx.assertTrue(world.getBlockState(subject).is(Blocks.CHAIN),
                "premise: the chain must place on the lowered floor");
        ctx.assertTrue(SlabSupport.isDynamicCeilingFollower(world, subject, world.getBlockState(subject)),
                "premise: the ceiling must classify the subject as ceiling-attached, or the break"
                        + " below has nothing to undo");

        double before = renderedBottomY(world, subject);
        world.setBlockAndUpdate(ceiling, Blocks.AIR.defaultBlockState());
        ctx.assertTrue(!SlabSupport.isDynamicCeilingFollower(world, subject, world.getBlockState(subject)),
                "premise: breaking the ceiling must un-classify the subject, or the scene is inert");
        double after = renderedBottomY(world, subject);
        // Logged for the same reason the law rows log: a reader auditing this pair needs the two
        // numbers, not just the verdict, to see that the scene moved at all.
        Slabbed.LOGGER.info("[COMPAT-CEILING] ceiling={} before={} after={}",
                ceilingState.getBlock().getName().getString(), before, after);
        return new double[] {before, after};
    }

    private static double renderedBottomY(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getShape(world, pos).bounds().minY + pos.getY();
    }

    /** Clears a column and seats {@code ceiling} at {@code pos} with open air beneath it. */
    private static void seatCeilingSlab(ServerLevel world, BlockPos pos, BlockState ceiling) {
        for (int d = 1; d <= 4; d++) {
            world.setBlockAndUpdate(pos.below(d), Blocks.AIR.defaultBlockState());
        }
        world.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        world.setBlockAndUpdate(pos, ceiling);
    }

    /**
     * Aims at the DOWN face of {@code supportPos} from below. The look vector matters as much as
     * the hit: the vertical placement lane reads the nearest looking vertical direction and hangs
     * the tip the other way, and a default-rotation mock player is looking level, which is not a
     * vertical aim at all. No result assertion here - a refused placement is one of the outcomes
     * a caller needs to be able to observe.
     */
    private static void placeAgainstUndersideWithHeldItem(
            GameTestHelper ctx, Block held, BlockPos supportPos) {
        Player player = ctx.makeMockPlayer();
        player.setPos(supportPos.getX() + 0.5D, supportPos.getY() - 3.0D, supportPos.getZ() + 0.5D);
        player.setXRot(-90.0F);
        player.setYRot(0.0F);
        ItemStack stack = new ItemStack(held);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(supportPos.getX() + 0.5D, supportPos.getY() + 0.5D,
                                supportPos.getZ() + 0.5D),
                        Direction.DOWN, supportPos, false)));
    }

    /** Places a slab at {@code pos} through the real transaction and returns its resolved dy. */
    private static double authorLoweredSlabAt(
            GameTestHelper ctx, ServerLevel world, BlockPos pos, Block slab) {
        world.setBlockAndUpdate(pos.below(2), Blocks.STONE.defaultBlockState());
        world.setBlockAndUpdate(pos.below(), Blocks.OAK_SLAB.defaultBlockState());
        for (int d = 0; d <= 3; d++) {
            world.setBlockAndUpdate(pos.above(d), Blocks.AIR.defaultBlockState());
        }
        placeCourseOnTop(ctx, world, pos.below(), slab);
        return SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
    }

    /**
     * A shallow ray that ends BELOW the outline top plane and crosses it inside the cell - the
     * live aim. Built from absolute positions in a rotation-free template, so absolute +X is the
     * structure's +X.
     */
    private static BlockHitResult obliqueTopPlaneRay(
            ServerLevel world, BlockPos subject, double dy) {
        double topY = subject.getY() + 0.5d + dy;
        Vec3 from = new Vec3(subject.getX() - 1.0d, topY + 0.40d, subject.getZ() + 0.5d);
        Vec3 to = new Vec3(subject.getX() + 1.5d, topY - 0.40d, subject.getZ() + 0.5d);
        return world.clip(new net.minecraft.world.level.ClipContext(
                from, to, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                (net.minecraft.world.entity.Entity) null));
    }

    /** Places one course on the resolved TOP FACE of {@code support}, where a real ray lands. */
    private static void placeCourseOnTop(
            GameTestHelper ctx, ServerLevel world, BlockPos support, Block held) {
        BlockState supportState = world.getBlockState(support);
        double supportDy = SlabSupport.getYOffset(world, support, supportState);
        double localTop = (supportState.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
                ? 0.5d : 1.0d) + supportDy;
        Player player = ctx.makeMockPlayer();
        player.setPos(support.getX() + 2.5D, support.getY() + 1.0D, support.getZ() + 0.5D);
        ItemStack stack = new ItemStack(held);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        new Vec3(support.getX() + 0.5D, support.getY() + localTop,
                                support.getZ() + 0.5D),
                        Direction.UP, support, false)));
    }

    private static void placeWithHeldItem(GameTestHelper ctx, Block held, BlockPos subject, BlockPos hitPos) {
        Player player = ctx.makeMockPlayer();
        // Stand beside the target, not inside it: a slab in the player's own feet cell is an
        // obstructed placement and vanilla refuses it before any Slabbed code runs.
        player.setPos(subject.getX() + 2.5D, subject.getY(), subject.getZ() + 0.5D);
        ItemStack stack = new ItemStack(held);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(hitPos),
                Direction.UP,
                hitPos,
                false
        );
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        ctx.assertTrue(result.consumesAction(), "held-item placement must be accepted");
    }

    private static BlockState shimState(GameTestHelper ctx, String namespace) {
        return shimState(ctx, namespace, SHIM_BLOCK_NAME);
    }

    private static BlockState shimState(GameTestHelper ctx, String namespace, String path) {
        Block block = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath(namespace, path));
        ctx.assertTrue(block != Blocks.AIR,
                "the " + namespace + ":" + path + " shim block must be registered;"
                        + " is the classifier shim mod loaded in this run?");
        return block.defaultBlockState();
    }

    private static void authorLoweredStone(GameTestHelper ctx, ServerLevel world, BlockPos pos) {
        world.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(world.getChunkAt(pos), pos, -1),
                "authoring a lowered support fact must succeed");
    }

    private static void placeStoneWithHeldItem(GameTestHelper ctx, BlockPos subject, BlockPos hitPos) {
        Player player = ctx.makeMockPlayer();
        player.setPos(subject.getX() + 0.5D, subject.getY(), subject.getZ() + 0.5D);
        ItemStack stack = new ItemStack(Blocks.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(hitPos),
                Direction.UP,
                hitPos,
                false
        );
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        ctx.assertTrue(result.consumesAction(), "held-item placement must be accepted");
        ctx.assertTrue(ctx.getLevel().getBlockState(subject).is(Blocks.STONE),
                "held-item placement must create the subject block");
    }
}
