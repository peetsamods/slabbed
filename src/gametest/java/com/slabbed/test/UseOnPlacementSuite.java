package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;

import static com.slabbed.test.PlacementHarness.describe;
import static com.slabbed.test.PlacementHarness.mockPlayerHolding;
import static com.slabbed.test.PlacementHarness.mockSlabPlayer;
import static com.slabbed.test.PlacementHarness.row;
import static com.slabbed.test.PlacementHarness.useHeldItem;
import static com.slabbed.test.PlacementHarness.useHeldOakSlab;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Placement-path (REAL {@code useOn}) coverage for the slab combine-vs-extend decision on
 * lowered targets — the gap every prior test on this branch left open: the matrix/fixture
 * suites build states via {@code setBlockState} and never exercise
 * {@code SlabBlock.canReplace} / {@code BlockItem.useOnBlock}, so the WYSIWYG remap in
 * {@code BlockItemPlacementIntentMixin#slabbed$remapLoweredFullBlockSideHit} had ZERO
 * placement-path pins.
 *
 * <p>Mechanism: a mock survival player holding an oak slab drives
 * {@code ItemStack.useOnBlock(ItemUsageContext)} with a hand-built {@link BlockHitResult}
 * (the same headless-useOn shape as the 26.1.2 {@code Slabbed2612UseOnPlacementTest} and the
 * Forge 1.20.1 {@code cantileveredTopSlabExtendsSidewaysInsteadOfCombining} harness,
 * re-derived for Yarn/Fabric 1.21.11). This runs the full vanilla decision chain — the
 * intent-mixin remap, {@code ItemPlacementContext} construction, {@code SlabBlock.canReplace}
 * (combine vs extend), {@code getPlacementState} (TOP/BOTTOM discriminator) — not a synthetic
 * state write.
 *
 * <p>Vanilla bug class under WYSIWYG lowering: {@code SlabBlock.canReplace} discriminates on
 * the RAW hit fraction {@code hit.y - clickedPos.y > 0.5}. A vanilla TOP slab rendered
 * lowered -0.5 is VISIBLE in the lower half of its cell, so an honest click on its visible
 * side face carries a raw fraction of ~0.4 — vanilla reads "lower half" and COMBINES the cell
 * into a DOUBLE slab, while the player visibly aimed at the (nominal) top half and wanted to
 * EXTEND the walkway sideways. The intent mixin fixes this at dy == -0.5 by remapping the hit
 * Y to the visible half before the {@code ItemPlacementContext} is built.
 *
 * <p>Cells here are the GREEN (passing-on-HEAD) lanes:
 * <ul>
 *   <li>control: an honest vanilla combine gesture (top face of a flat BOTTOM slab) still
 *       combines — proves the harness drives real placement AND that the remap does not
 *       over-extend un-lowered targets;</li>
 *   <li>-0.5 lane: side click on the visible face of a -0.5-lowered TOP slab EXTENDS
 *       sideways (stays TYPE=TOP, adjacent cell gains a slab) instead of combining.</li>
 * </ul>
 *
 * <p>The -1.0 lane (vanilla TOP slab on a Terrain Slabs bottom, visual dy = -1.0, see
 * {@code OffsetRaycastTargetingTest#vanillaTopSlabOnTerrainLowersFull}) is intentionally NOT
 * here: the intent-mixin gate is {@code getVisualYOffset == -0.5d} exactly, so the raw-fraction
 * misdecision persists there and the cell is RED on HEAD. It lives below as a RED_PENDING_RULING row,
 * skipped unless armed with {@code -Dslabbed.redCells=true}, until the maintainer rules on the
 * -1.0 gate fix (widen the intent-mixin gate vs port the dy-corrected canReplace fraction).
 */
public final class UseOnPlacementSuite {

    // The headless useOn harness lives in PlacementHarness (extracted from here 2026-08-07;
    // four other suites consume it). Static-imported below.

    // ── control: honest combine gesture on a FLAT slab still combines ────
    // Bottom slab on solid ground (dy 0.0), slab item, click the slab's top face centrally.
    // Vanilla law: BOTTOM + side==UP → canReplace true → the cell combines to DOUBLE.
    // The intent mixin must NOT divert this (visual dy is 0.0; central up-face hit is outside
    // the 0.20 edge band). Proves the harness reaches real placement and pins no-over-extend.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnFlatBottomSlabTopFaceStillCombinesToDouble(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        BlockPos ground = origin.add(1, 2, 1);
        world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos slab = ground.up();
        world.setBlockState(slab, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        ctx.assertTrue(SlabSupport.getYOffset(world, slab, world.getBlockState(slab)) == 0.0,
                "fixture: bottom slab on solid ground must be flat (dy 0.0)");

        PlayerEntity player = mockSlabPlayer(ctx, slab.north(3));
        Vec3d hit = new Vec3d(slab.getX() + 0.5, slab.getY() + 0.5, slab.getZ() + 0.5);
        ActionResult result = useHeldOakSlab(world, player, slab, Direction.UP, hit);
        row("control.flatBottomSlab.topFaceCombine", world, slab, slab.up(), result);

        ctx.assertTrue(result.isAccepted(),
                "control: useOn on a flat bottom slab's top face must place, got " + result);
        BlockState after = world.getBlockState(slab);
        ctx.assertTrue(after.isOf(Blocks.OAK_SLAB) && after.get(SlabBlock.TYPE) == SlabType.DOUBLE,
                "control: central top-face slab click on a FLAT bottom slab must COMBINE to DOUBLE "
                        + "(vanilla law preserved through the placement path), got " + describe(world, slab));
        ctx.complete();
    }

    // ── -0.5 lane: side click on a lowered TOP slab EXTENDS, not combines ─
    // Fixture (proven green on HEAD, same shape as objectOnLoweredTopOrDoubleSlabFollows):
    // bottom slab → stone (lowered -0.5) → vanilla TOP slab (renders -0.5, visible span
    // [Y, Y+0.5]). Click the TOP slab's west face on its VISIBLE geometry at raw fraction 0.4:
    // raw vanilla would read "lower half" → COMBINE (the bug); the intent-mixin remap
    // (gate: visual dy == -0.5) rewrites the hit to the visible half → canReplace false →
    // EXTEND into the adjacent cell, minted TYPE=TOP by the same remapped discriminator.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnLoweredTopSlabSideClickExtendsInsteadOfCombining(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        BlockPos base = origin.add(3, 2, 1);
        world.setBlockState(base, Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos fb = base.up();
        world.setBlockState(fb, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos top = fb.up();
        world.setBlockState(top, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);

        double dy = SlabSupport.getYOffset(world, top, world.getBlockState(top));
        ctx.assertTrue(dy == -0.5,
                "fixture: TOP slab on a lowered full block must render lowered -0.5, got " + dy);
        double visualDy = SlabSupport.getVisualYOffset(world, top, world.getBlockState(top));
        ctx.assertTrue(visualDy == -0.5,
                "fixture: intent-mixin gate input getVisualYOffset must read -0.5, got " + visualDy);

        BlockPos extendCell = top.west();
        ctx.assertTrue(world.getBlockState(extendCell).isAir(),
                "fixture: the extend target cell west of the lowered TOP slab must start as air");

        PlayerEntity player = mockSlabPlayer(ctx, top.west(3));
        // West face of the slab's cell, on the VISIBLE lowered geometry: raw fraction 0.4
        // (absolute Y + 0.4), dy-corrected fraction 0.9 — the exact raw-vs-visible flip case.
        Vec3d hit = new Vec3d(top.getX(), top.getY() + 0.4, top.getZ() + 0.5);
        ActionResult result = useHeldOakSlab(world, player, top, Direction.WEST, hit);
        row("minusHalf.loweredTopSlab.sideClick", world, top, extendCell, result);

        ctx.assertTrue(result.isAccepted(),
                "-0.5 lane: useOn on the lowered TOP slab's visible west face must place, got " + result);
        BlockState clickedAfter = world.getBlockState(top);
        ctx.assertTrue(clickedAfter.isOf(Blocks.OAK_SLAB) && clickedAfter.get(SlabBlock.TYPE) == SlabType.TOP,
                "-0.5 lane: the lowered TOP slab must NOT combine into a DOUBLE on a visible side "
                        + "click (WYSIWYG extend intent), got " + describe(world, top));
        BlockState extended = world.getBlockState(extendCell);
        ctx.assertTrue(extended.isOf(Blocks.OAK_SLAB),
                "-0.5 lane: the adjacent cell must gain the extended slab, got " + describe(world, extendCell));
        ctx.assertTrue(extended.get(SlabBlock.TYPE) == SlabType.TOP,
                "-0.5 lane: the extended slab must mint TYPE=TOP from the remapped (visible-half) "
                        + "discriminator, got " + describe(world, extendCell));
        ctx.complete();
    }

    // ── GH #57 lane: "if there is a slab under a full block, the block on it can not place on
    //    the usual position (slab -> half-block-height space -> block)" ────────────────────────
    // Deciding chain: BlockItemPlacementIntentMixin leaves an ordinary full-block item's UP-face
    // click untouched (only slab items get the up-face-edge remap — see
    // slabbed$remapLoweredFullBlockSideHit's "itemIsSlab && originalSide == Direction.UP" gate),
    // so a STONE useOn against a lowered support's up face falls straight through to vanilla
    // ItemPlacementContext, which resolves the placement cell to targetPos.offset(UP). Whether
    // that placed block itself lowers to -0.5 and persists an anchor is then decided entirely by
    // SlabSupport.shouldOffsetDown -> hasSlabInColumn (SlabSupport.java:1684-1715) for the live dy
    // and SlabAnchorAttachment.addAnchor (via BlockOnPlacedAnchorMixin.onPlaced) for the persisted
    // seat. Rows 1-2 below are the GREEN lanes (direct-on-slab, and on-an-already-anchored
    // support); row 3 pins the unanchored-lowered-looking-support edge the #57 report actually
    // hits, whatever that pin turns out to be.

    // ── row 1: useOn a full block against the UP face of a vanilla BOTTOM slab ──────────────
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnFullBlockAboveBottomSlabLowersAndAnchors(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        BlockPos ground = origin.add(1, 2, 4);
        world.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockPos slab = ground.up();
        world.setBlockState(slab, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos above = slab.up();
        ctx.assertTrue(world.getBlockState(above).isAir(),
                "fixture: the cell above the bottom slab must start as air");

        PlayerEntity player = mockPlayerHolding(ctx, slab.north(3), new ItemStack(Blocks.STONE.asItem(), 16));
        Vec3d hit = new Vec3d(slab.getX() + 0.5, slab.getY() + 0.5, slab.getZ() + 0.5);
        ActionResult result = useHeldItem(world, player, slab, Direction.UP, hit);
        row("gh57.row1.fullBlockOnBottomSlab.upFace", world, slab, above, result);

        ctx.assertTrue(result.isAccepted(),
                "row1: useOn stone on a bottom slab's up face must place, got " + result);
        BlockState placed = world.getBlockState(above);
        ctx.assertTrue(placed.isOf(Blocks.STONE),
                "row1: stone must land in the cell ABOVE the bottom slab (vanilla offset cell), got "
                        + describe(world, above));
        double dy = SlabSupport.getYOffset(world, above, placed);
        ctx.assertTrue(dy == -0.5,
                "row1: SlabSupport.getYOffset must resolve -0.5 for a full block placed directly on a "
                        + "bottom slab, got " + dy);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, above),
                "row1: BlockOnPlacedAnchorMixin must persist the anchor for a full block placed "
                        + "directly on a bottom slab (qualifiesForDirectAnchor), got isAnchored=false");
        ctx.complete();
    }

    // ── row 2: useOn a full block against the UP face of an ANCHORED lowered full block ─────
    // Support built the same way SlabOnSlabVerticalAnchorTest builds "anchored dirt on a bottom
    // slab": bottom slab -> dirt, addAnchor called directly (simulating the onPlaced call a real
    // player placement of the dirt would have fired). The NEW stone then goes in the cell above
    // the anchored dirt.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnFullBlockAboveAnchoredLoweredFullBlockLowersAndAnchors(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        BlockPos vanillaBottomSlabPos = origin.add(1, 2, 5);
        world.setBlockState(vanillaBottomSlabPos,
                Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos dirtPos = vanillaBottomSlabPos.up();
        world.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(world, dirtPos, world.getBlockState(dirtPos));
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, dirtPos),
                "setup: dirt must anchor on the bottom slab");
        double dirtDy = SlabSupport.getYOffset(world, dirtPos, world.getBlockState(dirtPos));
        ctx.assertTrue(dirtDy == -0.5, "setup: anchored dirt should render -0.5, got " + dirtDy);

        BlockPos above = dirtPos.up();
        ctx.assertTrue(world.getBlockState(above).isAir(),
                "fixture: the cell above the anchored dirt must start as air");

        PlayerEntity player = mockPlayerHolding(ctx, dirtPos.north(3), new ItemStack(Blocks.STONE.asItem(), 16));
        Vec3d hit = new Vec3d(dirtPos.getX() + 0.5, dirtPos.getY() + 0.5, dirtPos.getZ() + 0.5);
        ActionResult result = useHeldItem(world, player, dirtPos, Direction.UP, hit);
        row("gh57.row2.fullBlockOnAnchoredLoweredDirt.upFace", world, dirtPos, above, result);

        ctx.assertTrue(result.isAccepted(),
                "row2: useOn stone on the anchored lowered dirt's up face must place, got " + result);
        BlockState placed = world.getBlockState(above);
        ctx.assertTrue(placed.isOf(Blocks.STONE),
                "row2: stone must land in the cell above the anchored lowered dirt, got "
                        + describe(world, above));
        double dy = SlabSupport.getYOffset(world, above, placed);
        ctx.assertTrue(dy == -0.5,
                "row2: SlabSupport.getYOffset must resolve -0.5 for a full block placed on an "
                        + "anchored lowered full block, got " + dy);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, above),
                "row2: the newly placed block must itself anchor (column-lowered anchor lane), "
                        + "got isAnchored=false");
        ctx.complete();
    }

    // ── row 3 (DOCUMENTING / PINNING): useOn a full block above a LOWERED-LOOKING support that
    //    carries NO anchor ────────────────────────────────────────────────────────────────────
    // The support here is dirt placed via setBlockState ONLY directly on a bottom slab — no
    // SlabAnchorAttachment.addAnchor call, so it never goes through the real onPlaced anchor
    // lane. Its own one-hop hasSlabInColumn walk still finds the bottom slab immediately below
    // it, so it renders -0.5 live despite carrying no anchor: the "looks lowered, isAnchored
    // false" shape GH #57's report implies could exist. This row PINS whatever
    // SlabSupport/SlabAnchorAttachment currently produce for a full block placed directly on top
    // of THAT support — it is a documentation pin for the GH #57 report, not an assertion that
    // the observed value is desired/correct.
    //
    // MEASURED RESULT (this is what the assertions below actually pin, contrary to the naive
    // "it floats / stays unanchored" guess this row started from): the new stone DOES lower to
    // -0.5 and DOES get its own persisted anchor. Reason: SlabAnchorAttachment.addAnchor's
    // qualifiesForColumnLoweredAnchor lane calls SlabSupport.hasLoweringSourceInColumnBelow,
    // which — unlike the live-render hasSlabInColumn walk used by shouldOffset — has NO
    // "stop at the first opaque full cube" guard, so it walks straight through the unanchored
    // dirt down to the bottom slab and anchors the new stone at placement time. Once that anchor
    // is recorded, getYOffsetInner's ordinary-FB branch checks SlabAnchorAttachment.isAnchored(pos)
    // FIRST (SlabSupport.java ~990) and short-circuits to -0.5 before ever reaching the
    // opaque-stopping hasSlabInColumn walk. So for THIS exact one-hop shape (new block directly
    // on an unanchored-but-itself-lowered full block, which itself sits directly on a bottom
    // slab) the placement lane is actually CORRECT — it does not reproduce a "floats at vanilla
    // height" bug. If GH #57's report is accurate, the real reproducer likely needs a deeper or
    // differently-shaped column (or a timing/client-sync gap this headless harness cannot see);
    // this pin at least rules out the simplest one-hop shape as the cause.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnFullBlockAboveUnanchoredLoweredLookingSupportPinsCurrentBehavior(TestContext ctx) {
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        BlockPos bottomSlabPos = origin.add(1, 2, 6);
        world.setBlockState(bottomSlabPos,
                Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        BlockPos dirtPos = bottomSlabPos.up();
        // setBlockState ONLY — deliberately no SlabAnchorAttachment.addAnchor here. onPlaced (and
        // therefore addAnchor) never fires for a synthetic setBlockState write, so this dirt is
        // lowered-LOOKING (live dy -0.5 via its own one-hop hasSlabInColumn walk) but carries no
        // persisted anchor.
        world.setBlockState(dirtPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(world, dirtPos),
                "fixture: dirt placed via setBlockState only must carry no anchor");
        double dirtDy = SlabSupport.getYOffset(world, dirtPos, world.getBlockState(dirtPos));
        ctx.assertTrue(dirtDy == -0.5,
                "fixture: dirt resting directly on a bottom slab must still render lowered -0.5 "
                        + "live (its own one-hop hasSlabInColumn walk), got " + dirtDy);

        BlockPos above = dirtPos.up();
        ctx.assertTrue(world.getBlockState(above).isAir(),
                "fixture: the cell above the unanchored dirt must start as air");

        PlayerEntity player = mockPlayerHolding(ctx, dirtPos.north(3), new ItemStack(Blocks.STONE.asItem(), 16));
        Vec3d hit = new Vec3d(dirtPos.getX() + 0.5, dirtPos.getY() + 0.5, dirtPos.getZ() + 0.5);
        ActionResult result = useHeldItem(world, player, dirtPos, Direction.UP, hit);
        row("gh57.row3.fullBlockOnUnanchoredLoweredLookingDirt.upFace", world, dirtPos, above, result);

        ctx.assertTrue(result.isAccepted(),
                "row3: useOn stone on the unanchored lowered-looking dirt's up face must place, got "
                        + result);
        BlockState placed = world.getBlockState(above);
        ctx.assertTrue(placed.isOf(Blocks.STONE),
                "row3: stone must land in the cell above the dirt, got " + describe(world, above));

        // PINNING ASSERTIONS — NOT statements of desired behavior, and NOT the outcome this row
        // originally hypothesized. GH #57's reporter describes a block placed above a slab-
        // supported full block floating instead of sitting flush in the half-block-height gap;
        // the naive guess was that an UNANCHORED lowered-looking support would reproduce that as
        // dy=0.0/anchored=false. Measured reality for this exact one-hop shape is the opposite:
        // qualifiesForColumnLoweredAnchor's hasLoweringSourceInColumnBelow walk has no
        // opaque-cube stop, so it reaches past the unanchored dirt to the bottom slab and anchors
        // the new stone anyway. This row records TODAY's actual dy/anchor outcome for that exact
        // shape so a future fix (or a future regression) shows up as a diff against this pin,
        // rather than silently. If either assertion below starts failing, that is NOT necessarily
        // this test regressing — read it as the underlying lane having changed, and update or
        // delete this pin to match, per the maintainer's #57 report.
        double dy = SlabSupport.getYOffset(world, above, placed);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, above);
        ctx.assertTrue(dy == -0.5,
                "PINS CURRENT BEHAVIOR for GH #57 (this one-hop shape does NOT reproduce the "
                        + "reported float): a full block placed directly above an unanchored "
                        + "lowered-looking full block (itself resting on a bottom slab) renders dy=" + dy
                        + " (anchored=" + anchored + ")");
        ctx.assertTrue(anchored,
                "PINS CURRENT BEHAVIOR for GH #57: a full block placed above an unanchored "
                        + "lowered-looking support is anchored=" + anchored + " (dy=" + dy + ") — "
                        + "qualifiesForColumnLoweredAnchor's unbounded-through-solids column walk "
                        + "found the bottom slab beneath the unanchored dirt and anchored it anyway");
        ctx.complete();
    }

    // ═══ RED_PENDING_RULING: the -1.0 combine-vs-extend lane ═══
    // Formerly the deliberately-UNREGISTERED UseOnMinusOneLoweredCombineVsExtendRedTest; now a
    // registered row armed only by -Dslabbed.redCells=true, so the RED inventory is visible in
    // the suite instead of hidden in an unregistered file. THE GAP (code- and run-verified): the
    // intent-mixin remap gate is getVisualYOffset == -0.5 EXACTLY, so a vanilla TOP slab on a
    // Terrain Slabs bottom (visual dy -1.0) falls through unremapped to vanilla canReplace's raw
    // fraction and COMBINES where the player visibly aimed to EXTEND. Sibling lines fixed the
    // full dy range (forge1201 dy-corrected canReplace mixin; cleanpub fc608690); the production
    // fix here is a maintainer decision. Armed on current HEAD: FAILS with the clicked cell
    // reading OAK_SLAB[DOUBLE]. Both fixes must keep the -0.5 lane above green.
    private static final String RED_CELLS_PROPERTY = "slabbed.redCells";


    // -1.0 lane: vanilla TOP slab on a Terrain Slabs bottom (visual dy -1.0, visible span
    // [Y-0.5, Y]). Click its west face on the VISIBLE geometry (raw fraction -0.1,
    // dy-corrected 0.9). WYSIWYG intent: EXTEND sideways, stay TYPE=TOP.
    // Current HEAD: no remap (gate == -0.5) → vanilla raw fraction → COMBINE → DOUBLE. RED.
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void useOnMinusOneLoweredTopSlabSideClickExtendsInsteadOfCombining(TestContext ctx) {
        if (!Boolean.getBoolean(RED_CELLS_PROPERTY)) {
            System.out.println("[USEON] RED_PENDING_RULING skipped — minusOne.loweredTopSlab.sideClick "
                    + "(arm with -Dslabbed.redCells=true; RED on HEAD until the -1.0 gate ruling)");
            ctx.complete();
            return;
        }
        ServerWorld world = ctx.getWorld();
        BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);

        Block ts = Registries.BLOCK.get(Identifier.of("terrainslabs", "grass_slab"));
        ctx.assertTrue(ts != Blocks.AIR, "fixture: Terrain Slabs loaded");
        BlockState tsBottom = ts.getDefaultState();
        if (tsBottom.contains(SlabBlock.TYPE)) {
            tsBottom = tsBottom.with(SlabBlock.TYPE, SlabType.BOTTOM);
        }

        BlockPos tsPos = origin.add(3, 2, 3);
        world.setBlockState(tsPos, tsBottom, Block.NOTIFY_LISTENERS);
        BlockPos top = tsPos.up();
        world.setBlockState(top, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP),
                Block.NOTIFY_LISTENERS);

        double dy = SlabSupport.getYOffset(world, top, world.getBlockState(top));
        ctx.assertTrue(dy == -1.0,
                "fixture: vanilla TOP slab on a Terrain Slabs bottom must render -1.0 "
                        + "(vanillaTopSlabOnTerrainLowersFull lane), got " + dy);
        double visualDy = SlabSupport.getVisualYOffset(world, top, world.getBlockState(top));
        ctx.assertTrue(visualDy == -1.0,
                "fixture: intent-mixin gate input getVisualYOffset must read -1.0, got " + visualDy);

        BlockPos extendCell = top.west();
        ctx.assertTrue(world.getBlockState(extendCell).isAir(),
                "fixture: the extend target cell west of the -1.0 slab must start as air");

        PlayerEntity player = PlacementHarness.mockSlabPlayer(ctx, top.west(3));
        // Visible span is [Y-0.5, Y]; hit its upper region: absolute Y - 0.1 → raw fraction
        // -0.1 (vanilla reads "lower half" → combine), dy-corrected fraction 0.9 (visible
        // upper half → extend intent). BlockHitResult still targets the slab's own cell,
        // exactly as the offset-aware raycast resolves side hits on lowered visuals.
        Vec3d hit = new Vec3d(top.getX(), top.getY() - 0.1, top.getZ() + 0.5);
        ActionResult result = PlacementHarness.useHeldOakSlab(
                world, player, top, Direction.WEST, hit);
        PlacementHarness.row("minusOne.loweredTopSlab.sideClick", world, top, extendCell, result);

        ctx.assertTrue(result.isAccepted(),
                "-1.0 lane: useOn on the -1.0 slab's visible west face must place, got " + result);
        BlockState clickedAfter = world.getBlockState(top);
        ctx.assertTrue(clickedAfter.isOf(Blocks.OAK_SLAB) && clickedAfter.get(SlabBlock.TYPE) == SlabType.TOP,
                "-1.0 lane: the -1.0-lowered TOP slab must NOT combine into a DOUBLE on a visible "
                        + "side click (WYSIWYG extend intent; intent-mixin gate is == -0.5 so the "
                        + "raw-fraction misdecision persists here), got "
                        + PlacementHarness.describe(world, top));
        BlockState extended = world.getBlockState(extendCell);
        ctx.assertTrue(extended.isOf(Blocks.OAK_SLAB),
                "-1.0 lane: the adjacent cell must gain the extended slab, got "
                        + PlacementHarness.describe(world, extendCell));
        ctx.complete();
    }
}
