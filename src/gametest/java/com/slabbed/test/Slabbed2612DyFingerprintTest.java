package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Slabbed dy FINGERPRINT — the version-comparison regression baseline.
 *
 * <p>Each {@code @GameTest} below pins ONE canonical fixture from a distinct behavior family to its
 * expected {@code dy} and {@code src} classification (see
 * {@code docs/process/RELEASE_SANITY_CHECKLIST.md} §3). Every fixture maps to a historical fix
 * commit; if that behavior silently re-breaks, its fixture goes RED — that is the "a behavior that
 * used to be correct silently changed" catch.
 *
 * <p>Each fixture also emits one canonical line to the log:
 * <pre>SLABBED-FP | &lt;name&gt; | dy=&lt;value&gt; | src=&lt;class&gt;</pre>
 * Grep a run's log for {@code SLABBED-FP} to get the whole fingerprint as a flat, diffable artifact
 * — run it on two jar versions and diff the two captures to localize a cross-version behavior change.
 * The committed reference capture lives at {@code src/gametest/resources/dy-baseline.txt}.
 *
 * <p>{@code getYOffset} is NOT a pure function of geometry — anchor / freeze / compound markers
 * (written by the real {@code setPlacedBy} placement path, NOT terrain {@code setBlock}) are
 * first-class inputs. Fixtures therefore pin BOTH geometry AND marker state via {@link #author} vs
 * {@code helper.setBlock}, and each line records the {@code src} classification so a freeze/anchor
 * regression that lands on the same number for a different reason still shows as a diff.
 *
 * <p>One fixture per {@code @GameTest} method (= one isolated structure region each) so a stray
 * anchor in one fixture can never contaminate the next.
 */
public final class Slabbed2612DyFingerprintTest {

    private static final double EPS = 1.0e-6;

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState topSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    /** Real placement path: setBlock + setPlacedBy → fires BlockOnPlacedAnchorMixin (addAnchor + freeze). */
    private static void author(GameTestHelper helper, ServerLevel level, BlockPos rel, BlockState state) {
        BlockPos abs = helper.absolutePos(rel);
        level.setBlock(abs, state, Block.UPDATE_ALL);
        state.getBlock().setPlacedBy(level, abs, level.getBlockState(abs), null, ItemStack.EMPTY);
    }

    /** Classify the dy source the way the /slabdy HUD's {@code src=} field does, for the fingerprint line. */
    private static String src(ServerLevel level, BlockPos abs, BlockState state) {
        if (SlabAnchorAttachment.isFrozenFlat(level, abs)) {
            return "FROZEN-FLAT";
        }
        if (!(state.getBlock() instanceof SlabBlock) && SlabAnchorAttachment.isCompoundFullBlockAnchor(level, abs)) {
            return "compound-anchor";
        }
        if (SlabAnchorAttachment.isCompoundVisibleSideUpperSlab(level, abs, state)
                || SlabAnchorAttachment.isCompoundVisibleSideLowerSlab(level, abs, state)
                || SlabAnchorAttachment.isCompoundVisibleSideDoubleSlab(level, abs, state)
                || SlabAnchorAttachment.isCompoundVisibleOwnerTopSlab(level, abs, state)) {
            return "compound-side";
        }
        if (SlabAnchorAttachment.isAnchored(level, abs)) {
            return "ANCHORED";
        }
        return "geometric";
    }

    /** Emit the canonical fingerprint line AND assert the pinned dy. */
    private static void fingerprint(GameTestHelper helper, ServerLevel level, BlockPos rel,
                                    String name, double expected) {
        BlockPos abs = helper.absolutePos(rel);
        BlockState state = level.getBlockState(abs);
        double dy = SlabSupport.getYOffset(level, abs, state);
        Slabbed.LOGGER.info("SLABBED-FP | {} | dy={} | src={}",
                name, String.format(java.util.Locale.ROOT, "%.3f", dy), src(level, abs, state));
        if (Math.abs(dy - expected) > EPS) {
            throw helper.assertionException(rel,
                    "FINGERPRINT DRIFT [" + name + "]: expected dy=" + expected + " got " + dy
                    + " — a behavior changed vs the committed baseline. If intended, update dy-baseline.txt.");
        }
    }

    // ── A. core lower ─────────────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpPlainLower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos b = new BlockPos(2, 2, 2);
        helper.setBlock(b.below(), bottomSlab());
        helper.setBlock(b, Blocks.STONE.defaultBlockState());
        fingerprint(helper, level, b, "plain_lower", -0.5);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpTopSlabFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos b = new BlockPos(2, 2, 2);
        helper.setBlock(b.below(), topSlab());
        helper.setBlock(b, Blocks.STONE.defaultBlockState());
        fingerprint(helper, level, b, "top_slab_flush", 0.0);
        helper.succeed();
    }

    // ── B. compound ───────────────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpCompoundVertical(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(1), bottomSlab());
        helper.setBlock(base.above(2), Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(3), bottomSlab());
        helper.setBlock(base.above(4), Blocks.STONE.defaultBlockState());
        fingerprint(helper, level, base.above(4), "compound_vertical", -1.0);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpCompoundAnchor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(1), bottomSlab());
        helper.setBlock(base.above(2), Blocks.STONE.defaultBlockState());
        helper.setBlock(base.above(3), bottomSlab());
        BlockPos logRel = base.above(4);
        helper.setBlock(logRel, Blocks.SPRUCE_LOG.defaultBlockState());
        BlockPos logAbs = helper.absolutePos(logRel);
        SlabAnchorAttachment.addAnchor(level, logAbs, level.getBlockState(logAbs));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(level, logAbs, level.getBlockState(logAbs));
        fingerprint(helper, level, logRel, "compound_anchor", -1.0);
        helper.succeed();
    }

    // ── C. freeze / NEVER-POP ─────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpFrozenFlat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos b = new BlockPos(2, 3, 2);
        author(helper, level, b, Blocks.STONE.defaultBlockState());
        helper.setBlock(b.below(), bottomSlab());
        fingerprint(helper, level, b, "frozen_flat", 0.0);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpFreezeControl(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos b = new BlockPos(2, 3, 2);
        helper.setBlock(b.below(), bottomSlab());
        helper.setBlock(b, Blocks.STONE.defaultBlockState());
        fingerprint(helper, level, b, "freeze_control", -0.5);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpSideNoContagion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos carrier = new BlockPos(2, 2, 2);
        helper.setBlock(carrier, bottomSlab());
        helper.setBlock(carrier.above(), Blocks.STONE.defaultBlockState());
        BlockPos sourceAbs = helper.absolutePos(carrier.above());
        BlockState sourceState = level.getBlockState(sourceAbs);
        BlockPos besideRel = carrier.above().east();
        helper.setBlock(besideRel.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(besideRel, Blocks.STONE.defaultBlockState());
        BlockPos besideAbs = helper.absolutePos(besideRel);
        SlabAnchorAttachment.addSideAdjacentLoweredFullAnchor(
                level, besideAbs, level.getBlockState(besideAbs), sourceAbs, sourceState);
        fingerprint(helper, level, besideRel, "side_no_contagion", 0.0);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpCantileverMerge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slab = new BlockPos(2, 2, 2);
        helper.setBlock(slab, bottomSlab());
        helper.setBlock(slab.above(), Blocks.STONE.defaultBlockState());
        BlockPos cantileverRel = slab.above().east();   // air below → cantilever
        helper.setBlock(cantileverRel, Blocks.STONE.defaultBlockState());
        fingerprint(helper, level, cantileverRel, "cantilever_merge", -0.5);
        helper.succeed();
    }

    // ── D. ceiling-hung ───────────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpCeilingFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos roots = new BlockPos(2, 3, 2);
        helper.setBlock(roots.above(), bottomSlab());               // flush ceiling slab
        helper.setBlock(roots, Blocks.HANGING_ROOTS.defaultBlockState());
        helper.setBlock(roots.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(roots.below(2), bottomSlab());
        fingerprint(helper, level, roots, "ceiling_flush", 0.0);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpCeilingFollow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = new BlockPos(2, 3, 2);
        helper.setBlock(support.below(), bottomSlab());
        helper.setBlock(support, Blocks.STONE.defaultBlockState());
        BlockPos supportAbs = helper.absolutePos(support);
        SlabAnchorAttachment.addAnchor(level, supportAbs, level.getBlockState(supportAbs));
        helper.setBlock(support.below(), Blocks.AIR.defaultBlockState());
        BlockPos roots = support.below();
        helper.setBlock(roots, Blocks.HANGING_ROOTS.defaultBlockState());
        fingerprint(helper, level, roots, "ceiling_follow", -0.5);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpLanternSmoosh(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos support = new BlockPos(2, 3, 2);
        helper.setBlock(support.below(), bottomSlab());
        helper.setBlock(support, Blocks.STONE.defaultBlockState());
        BlockPos supportAbs = helper.absolutePos(support);
        SlabAnchorAttachment.addAnchor(level, supportAbs, level.getBlockState(supportAbs));
        helper.setBlock(support.below(), Blocks.AIR.defaultBlockState());
        BlockPos lantern = support.below();
        helper.setBlock(lantern, Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
        fingerprint(helper, level, lantern, "lantern_smoosh", -0.5);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpChainRaise(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chain = new BlockPos(2, 3, 2);
        helper.setBlock(chain.above(), topSlab());
        helper.setBlock(chain, Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        fingerprint(helper, level, chain, "chain_raise", 0.5);
        helper.succeed();
    }

    // ── I. thin layers (must NOT lower) ───────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpThinLayerCarpet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos b = new BlockPos(2, 2, 2);
        helper.setBlock(b.below(), bottomSlab());
        helper.setBlock(b, Blocks.WHITE_CARPET.defaultBlockState());
        fingerprint(helper, level, b, "thin_layer_carpet", 0.0);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpPowderSnow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos b = new BlockPos(2, 2, 2);
        helper.setBlock(b.below(), bottomSlab());
        helper.setBlock(b, Blocks.POWDER_SNOW.defaultBlockState());
        fingerprint(helper, level, b, "powder_snow", 0.0);
        helper.succeed();
    }

    // ── bed (either-half coordination) ────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpBedEitherHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos foot = new BlockPos(2, 2, 2);
        BlockPos head = foot.east();   // FOOT.relative(facing=EAST) = head
        BlockState footState = Blocks.RED_BED.defaultBlockState()
                .setValue(BlockStateProperties.BED_PART, BedPart.FOOT)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        BlockState headState = Blocks.RED_BED.defaultBlockState()
                .setValue(BlockStateProperties.BED_PART, BedPart.HEAD)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        helper.setBlock(foot.below(), bottomSlab());                    // slab under FOOT only
        helper.setBlock(head.below(), Blocks.STONE.defaultBlockState());
        helper.setBlock(foot, footState);
        helper.setBlock(head, headState);
        // Either-half rule: the HEAD (no slab in its own column) still lowers because the foot's does.
        fingerprint(helper, level, foot, "bed_foot_on_slab", -0.5);
        fingerprint(helper, level, head, "bed_head_follows_foot", -0.5);
        helper.succeed();
    }

    // ── candle floor-top contact ──────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpCandleContact(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos b = new BlockPos(2, 2, 2);
        helper.setBlock(b.below(), bottomSlab());
        author(helper, level, b, Blocks.CANDLE.defaultBlockState());
        // OBSERVED 26.1.2 value: an anchored candle falls through to the generic -0.5 anchor; the
        // 1.21.x floor-top CONTACT rule (objectDy = supportDy - 0.5 = -1.0, the beta35 candle audit)
        // is NOT wired into 26.1.2's anchored getYOffset path. -0.5 is consistent with door/trapdoor
        // (also -0.5 here). ⚠ LIVE-CONFIRM whether the candle visually contacts the slab top at -0.5
        // or needs the -1.0 contact (a possible contact-gap). Locked to the observed value so a future
        // change (wiring the contact rule, or a regression to 0.0) shows as a fingerprint diff.
        fingerprint(helper, level, b, "candle_contact_OBSERVED", -0.5);
        helper.succeed();
    }

    // ── trapdoor (bottom) — server-hit-target predicate coverage (closes the door/trapdoor gap) ──

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpBottomTrapdoorLoweredOnSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos b = new BlockPos(2, 2, 2);
        BlockState td = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HALF, Half.BOTTOM);
        helper.setBlock(b.below(), bottomSlab());
        author(helper, level, b, td);
        BlockPos abs = helper.absolutePos(b);
        BlockState st = level.getBlockState(abs);
        if (!SlabSupport.isBeta35LoweredBottomTrapdoorServerHitTarget(level, abs, st)) {
            throw helper.assertionException(b,
                    "oak_trapdoor[BOTTOM] on a bottom slab MUST be a lowered server-hit target (door/trapdoor coverage)");
        }
        double dy = SlabSupport.getYOffset(level, abs, st);
        Slabbed.LOGGER.info("SLABBED-FP | trapdoor_bottom_on_slab | dy={} | src={}",
                String.format(java.util.Locale.ROOT, "%.3f", dy), src(level, abs, st));
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpBottomTrapdoorFlushIsNotLoweredTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos b = new BlockPos(2, 2, 2);
        BlockState td = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HALF, Half.BOTTOM);
        helper.setBlock(b.below(), Blocks.STONE.defaultBlockState());   // flush ground
        author(helper, level, b, td);
        BlockPos abs = helper.absolutePos(b);
        BlockState st = level.getBlockState(abs);
        if (SlabSupport.isBeta35LoweredBottomTrapdoorServerHitTarget(level, abs, st)) {
            throw helper.assertionException(b,
                    "oak_trapdoor[BOTTOM] on flush ground MUST NOT be a lowered server-hit target (control)");
        }
        helper.succeed();
    }

    // ── door — server-hit-target predicate coverage ───────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpDoorLoweredOnSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lower = new BlockPos(2, 2, 2);
        BlockPos upper = lower.above();
        BlockState lowerState = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        BlockState upperState = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        helper.setBlock(lower.below(), bottomSlab());
        author(helper, level, lower, lowerState);
        helper.setBlock(upper, upperState);                            // pair the door
        BlockPos abs = helper.absolutePos(lower);
        BlockState st = level.getBlockState(abs);
        if (!SlabSupport.isBeta35LoweredRegularDoorServerHitTarget(level, abs, st)) {
            throw helper.assertionException(lower,
                    "oak_door[LOWER] on a bottom slab MUST be a lowered server-hit target (door/trapdoor coverage)");
        }
        double dy = SlabSupport.getYOffset(level, abs, st);
        Slabbed.LOGGER.info("SLABBED-FP | door_lower_on_slab | dy={} | src={}",
                String.format(java.util.Locale.ROOT, "%.3f", dy), src(level, abs, st));
        helper.succeed();
    }

    // ── vegetation — double-tall plant over a slab (UPPER half is TS-gated) ────

    /**
     * A double-tall plant over a bottom slab lowers BOTH halves -0.5 on a VANILLA slab.
     *
     * <p>⚠ Tall plants do NOT survive on a bare slab top — placing them with {@code setBlock} pops them
     * to AIR (reading dy 0.0, a false green). Compute {@code getYOffset} on the SYNTHETIC plant states
     * directly. The recent SlabSupport fix TS-gates the UPPER half for vegetation only, so it reads 0.0
     * on a Terrain Slabs surface but is UNCHANGED at -0.5 on a vanilla slab (no TS in this runtime).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpVegetationLowerOnSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lower = new BlockPos(2, 2, 2);
        BlockPos upper = lower.above();
        helper.setBlock(lower.below(), bottomSlab());
        BlockState lowerState = Blocks.SUNFLOWER.defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        BlockState upperState = Blocks.SUNFLOWER.defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        double dyLower = SlabSupport.getYOffset(level, helper.absolutePos(lower), lowerState);
        double dyUpper = SlabSupport.getYOffset(level, helper.absolutePos(upper), upperState);
        Slabbed.LOGGER.info("SLABBED-FP | vegetation_lower_on_slab | dy={} | src=geometric",
                String.format(java.util.Locale.ROOT, "%.3f", dyLower));
        if (Math.abs(dyLower - (-0.5)) > EPS || Math.abs(dyUpper - (-0.5)) > EPS) {
            throw helper.assertionException(lower,
                    "FINGERPRINT DRIFT [vegetation_lower_on_slab]: expected both halves dy=-0.5 got lower="
                    + dyLower + " upper=" + dyUpper + " — if intended, update dy-baseline.txt.");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fpDoorFlushIsNotLoweredTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lower = new BlockPos(2, 2, 2);
        BlockPos upper = lower.above();
        BlockState lowerState = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        BlockState upperState = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        helper.setBlock(lower.below(), Blocks.STONE.defaultBlockState());
        author(helper, level, lower, lowerState);
        helper.setBlock(upper, upperState);
        BlockPos abs = helper.absolutePos(lower);
        BlockState st = level.getBlockState(abs);
        if (SlabSupport.isBeta35LoweredRegularDoorServerHitTarget(level, abs, st)) {
            throw helper.assertionException(lower,
                    "oak_door[LOWER] on flush ground MUST NOT be a lowered server-hit target (control)");
        }
        helper.succeed();
    }
}
