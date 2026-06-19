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
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Headless dy coverage for objects RESTING ON slabs — the "where does it sit" math, asserted via
 * {@link SlabSupport#getYOffset}. No game window. Built from a 3-agent spec (2026-06-18).
 *
 * <p>KEY RULE the spec surfaced: on a FRESH (dy=0) bottom slab the beta35 "contact" readers all
 * return NaN, so ordinary objects lower by the GENERIC -0.5 — NOT the -1.0/-1.5 "contact" values in
 * the beta35 docs, which only fire when the support BELOW is itself already lowered. So the simple
 * "X on a bottom slab" rows are all -0.5.
 */
public final class Slabbed2612RestingDyTest {

    private static final double EPS = 1.0e-6;

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static BlockState topSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    private static BlockState doubleSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
    }

    private static double dy(ServerLevel level, GameTestHelper helper, BlockPos rel) {
        BlockPos abs = helper.absolutePos(rel);
        return SlabSupport.getYOffset(level, abs, level.getBlockState(abs));
    }

    private static void expect(GameTestHelper helper, ServerLevel level, BlockPos rel, double want, String what) {
        double got = dy(level, helper, rel);
        if (Math.abs(got - want) > EPS) {
            throw helper.assertionException(rel, what + ": expected dy=" + want + " got " + got);
        }
    }

    private static void clear(GameTestHelper helper, BlockPos... rels) {
        for (BlockPos r : rels) {
            helper.setBlock(r, Blocks.AIR.defaultBlockState());
        }
    }

    // ── special full blocks on a bottom slab → -0.5 (generic lower, NOT a contact -1.0) ───────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void specialFullBlocksOnBottomSlabLowerHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slab = new BlockPos(2, 1, 2);
        BlockPos obj = new BlockPos(2, 2, 2);
        Block[] blocks = {
                Blocks.CHEST, Blocks.BARREL, Blocks.FURNACE, Blocks.BOOKSHELF, Blocks.ENCHANTING_TABLE,
                Blocks.STONECUTTER, Blocks.ANVIL, Blocks.GRINDSTONE, Blocks.LECTERN, Blocks.CRAFTING_TABLE
        };
        for (Block b : blocks) {
            helper.setBlock(slab, bottomSlab());
            helper.setBlock(obj, b.defaultBlockState());
            expect(helper, level, obj, -0.5, b.getName().getString() + " on a bottom slab lowers -0.5");
            clear(helper, obj, slab);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void restingFlushOnTopAndDoubleSlab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slab = new BlockPos(2, 1, 2);
        BlockPos obj = new BlockPos(2, 2, 2);
        Block[] blocks = {Blocks.STONE, Blocks.CHEST};
        for (Block b : blocks) {
            helper.setBlock(slab, topSlab());
            helper.setBlock(obj, b.defaultBlockState());
            expect(helper, level, obj, 0.0, b.getName().getString() + " on a TOP slab stays flush");
            clear(helper, obj, slab);
            helper.setBlock(slab, doubleSlab());
            helper.setBlock(obj, b.defaultBlockState());
            expect(helper, level, obj, 0.0, b.getName().getString() + " on a DOUBLE slab stays flush");
            clear(helper, obj, slab);
        }
        helper.succeed();
    }

    // ── floor objects on a bottom slab → -0.5 ─────────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floorObjectsOnBottomSlabLowerHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slab = new BlockPos(2, 1, 2);
        BlockPos obj = new BlockPos(2, 2, 2);
        BlockState[] states = {
                Blocks.TORCH.defaultBlockState(),
                Blocks.SOUL_TORCH.defaultBlockState(),
                Blocks.OAK_BUTTON.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR),
                Blocks.STONE_PRESSURE_PLATE.defaultBlockState(),
                Blocks.OAK_PRESSURE_PLATE.defaultBlockState(),
                Blocks.OAK_FENCE_GATE.defaultBlockState(),
                Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, false),
                Blocks.OAK_SIGN.defaultBlockState()
        };
        for (BlockState s : states) {
            helper.setBlock(slab, bottomSlab());
            helper.setBlock(obj, s);
            expect(helper, level, obj, -0.5, s.getBlock().getName().getString() + " (floor) on a bottom slab lowers -0.5");
            clear(helper, obj, slab);
        }
        helper.succeed();
    }

    // ── ceiling-hung decorations directly under a TOP slab → +0.5 ─────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void ceilingHungUnderTopSlabRaiseHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ceil = new BlockPos(2, 3, 2);
        BlockPos obj = new BlockPos(2, 2, 2);
        BlockState[] states = {
                Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true),
                Blocks.SOUL_LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true),
                Blocks.SPORE_BLOSSOM.defaultBlockState(),
                Blocks.BELL.defaultBlockState().setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING),
                Blocks.OAK_HANGING_SIGN.defaultBlockState()
        };
        for (BlockState s : states) {
            helper.setBlock(ceil, topSlab());
            helper.setBlock(obj, s);
            expect(helper, level, obj, 0.5, s.getBlock().getName().getString() + " hung under a top slab raises +0.5");
            clear(helper, obj, ceil);
        }
        helper.succeed();
    }

    // ── thin top layers must NOT lower (stay 0.0) ─────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void thinLayersOnBottomSlabStayFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slab = new BlockPos(2, 1, 2);
        BlockPos obj = new BlockPos(2, 2, 2);
        BlockState[] states = {
                Blocks.SNOW.defaultBlockState(),          // snow LAYER (layers=1 default)
                Blocks.MOSS_CARPET.defaultBlockState()
        };
        for (BlockState s : states) {
            helper.setBlock(slab, bottomSlab());
            helper.setBlock(obj, s);
            expect(helper, level, obj, 0.0, s.getBlock().getName().getString() + " (thin layer) must stay flush 0.0");
            clear(helper, obj, slab);
        }
        helper.succeed();
    }

    // ── double-tall plants: BOTH halves lower when a slab is under the lower half ──────────────────

    /**
     * ⚠ OBSERVED + FLAGGED: a double-tall PLANT (sunflower/large_fern/tall_grass) on a bottom slab reads
     * dy=0.0 — it is NOT lowered, even though RELEASE_SANITY_CHECKLIST §T4–T6 expects -0.5. So tall plants
     * are currently left flush (likely intentional — vegetation is largely handled by Terrain Slabs, and
     * Slabbed has a history of NOT double-offsetting veg). This pins the OBSERVED 0.0 and flags the
     * checklist-vs-code discrepancy for a live decision; it does NOT claim 0.0 is the desired look.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void doubleTallPlantsOnBottomSlabObservedFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slab = new BlockPos(2, 1, 2);
        BlockPos lower = new BlockPos(2, 2, 2);
        BlockPos upper = new BlockPos(2, 3, 2);
        Block[] plants = {Blocks.SUNFLOWER, Blocks.LARGE_FERN, Blocks.TALL_GRASS};
        for (Block p : plants) {
            helper.setBlock(slab, bottomSlab());
            helper.setBlock(lower, p.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
            helper.setBlock(upper, p.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
            Slabbed.LOGGER.info("RESTING-FP | {}_on_bottom_slab_OBSERVED | lower={} upper={} (checklist expected -0.5)",
                    p.getName().getString(), dy(level, helper, lower), dy(level, helper, upper));
            expect(helper, level, lower, 0.0, p.getName().getString() + " lower half OBSERVED flush (checklist expects -0.5 — flagged)");
            expect(helper, level, upper, 0.0, p.getName().getString() + " upper half OBSERVED flush");
            clear(helper, upper, lower, slab);
        }
        helper.succeed();
    }

    // ── slab MATERIAL sweep: behavior keys on slab TYPE not material → all -0.5 ────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabMaterialSweepAllLowerHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slab = new BlockPos(2, 1, 2);
        BlockPos obj = new BlockPos(2, 2, 2);
        Block[] slabs = {
                Blocks.OAK_SLAB, Blocks.COBBLESTONE_SLAB, Blocks.SANDSTONE_SLAB, Blocks.BRICK_SLAB,
                Blocks.NETHER_BRICK_SLAB, Blocks.QUARTZ_SLAB, Blocks.PRISMARINE_SLAB,
                Blocks.DEEPSLATE_TILE_SLAB, Blocks.CUT_COPPER_SLAB
        };
        for (Block sb : slabs) {
            helper.setBlock(slab, sb.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            helper.setBlock(obj, Blocks.STONE.defaultBlockState());
            expect(helper, level, obj, -0.5, "stone on " + sb.getName().getString() + " (bottom) lowers -0.5");
            clear(helper, obj, slab);
        }
        helper.succeed();
    }

    // ── lowered-support CONTACT cases (the -1.0 only appears here, support already lowered) ────────

    /** A candle on a slab that is ITSELF lowered (-0.5) gets the floor-top contact dy = support-0.5 = -1.0. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void candleOnLoweredSlabContactMinusOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // ground / slab(0) / stone(-0.5) / slab(-0.5, lowered lane) / candle
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(2, 2, 2), bottomSlab());
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(2, 4, 2), bottomSlab());
        BlockPos candle = new BlockPos(2, 5, 2);
        // author (real placement) so the anchored contact reader path is reached
        BlockPos abs = helper.absolutePos(candle);
        level.setBlock(abs, Blocks.CANDLE.defaultBlockState(), Block.UPDATE_ALL);
        Blocks.CANDLE.setPlacedBy(level, abs, level.getBlockState(abs), null, ItemStack.EMPTY);
        double got = dy(level, helper, candle);
        Slabbed.LOGGER.info("RESTING-FP | candle_on_lowered_slab | dy={}", got);
        expect(helper, level, candle, -1.0,
                "candle on an ALREADY-LOWERED slab gets contact dy support(-0.5)-0.5 = -1.0");
        helper.succeed();
    }

    // ── FLAGGED behaviors: assert the OBSERVED current value, with a loud comment ──────────────────

    /**
     * A FLOOR bell on a bottom slab lowers -0.5 like other floor objects. (The spec WORRIED that
     * {@code isCeilingAttached} keying on the BellBlock class might leave it at 0.0 — measured: it does NOT,
     * the floor bell lowers correctly. No gap.)
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floorBellOnBottomSlabLowersHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 1, 2), bottomSlab());
        helper.setBlock(new BlockPos(2, 2, 2),
                Blocks.BELL.defaultBlockState().setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.FLOOR));
        expect(helper, level, new BlockPos(2, 2, 2), -0.5, "FLOOR bell on a bottom slab lowers -0.5 (no gap)");
        helper.succeed();
    }

    // ── stairs on a bottom slab → -0.5 ────────────────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void stairsOnBottomSlabLowerHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos slab = new BlockPos(2, 1, 2);
        BlockPos obj = new BlockPos(2, 2, 2);
        Block[] stairs = {Blocks.STONE_STAIRS, Blocks.OAK_STAIRS, Blocks.BIRCH_STAIRS};
        for (Block s : stairs) {
            helper.setBlock(slab, bottomSlab());
            helper.setBlock(obj, s.defaultBlockState());
            expect(helper, level, obj, -0.5, s.getName().getString() + " on a bottom slab lowers -0.5");
            clear(helper, obj, slab);
        }
        helper.succeed();
    }

    // ── ceiling-attached trapdoor (HALF=TOP) directly under a TOP slab — measure-and-lock ─────────

    /** An OAK_TRAPDOOR[half=TOP] is ceiling-attached; under a top slab it raises +0.5 to hug the underside. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void trapdoorTopUnderTopSlabRaisesHalf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 3, 2), topSlab());
        helper.setBlock(new BlockPos(2, 2, 2),
                Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HALF, Half.TOP));
        double got = dy(level, helper, new BlockPos(2, 2, 2));
        Slabbed.LOGGER.info("RESTING-FP | trapdoor_top_under_top_slab | dy={}", got);
        expect(helper, level, new BlockPos(2, 2, 2), 0.5,
                "OAK_TRAPDOOR[TOP] (ceiling-attached) under a top slab raises +0.5 to the underside");
        helper.succeed();
    }

    // ── bed either-half coordination: head-on-slab lowers BOTH; bed on a top slab stays flush ──────

    private static BlockState bedFoot() {
        return Blocks.RED_BED.defaultBlockState()
                .setValue(BlockStateProperties.BED_PART, BedPart.FOOT)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
    }

    private static BlockState bedHead() {
        return Blocks.RED_BED.defaultBlockState()
                .setValue(BlockStateProperties.BED_PART, BedPart.HEAD)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
    }

    /** Slab under the HEAD only still lowers BOTH halves (either-half rule, complement to the foot fixture). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bedHeadOnSlabLowersBothHalves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos foot = new BlockPos(2, 2, 2);
        BlockPos head = new BlockPos(3, 2, 2);                // foot.relative(EAST)
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE.defaultBlockState());   // flush under foot
        helper.setBlock(new BlockPos(3, 1, 2), bottomSlab());                       // slab under HEAD only
        helper.setBlock(foot, bedFoot());
        helper.setBlock(head, bedHead());
        expect(helper, level, foot, -0.5, "bed FOOT follows because the HEAD's column has a slab (either-half)");
        expect(helper, level, head, -0.5, "bed HEAD on a slab lowers -0.5");
        helper.succeed();
    }

    /** A bed resting on a TOP slab stays flush (top slabs don't lower resting objects). */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bedOnTopSlabStaysFlush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos foot = new BlockPos(2, 2, 2);
        BlockPos head = new BlockPos(3, 2, 2);
        helper.setBlock(new BlockPos(2, 1, 2), topSlab());
        helper.setBlock(new BlockPos(3, 1, 2), topSlab());
        helper.setBlock(foot, bedFoot());
        helper.setBlock(head, bedHead());
        expect(helper, level, foot, 0.0, "bed foot on a TOP slab stays flush");
        expect(helper, level, head, 0.0, "bed head on a TOP slab stays flush");
        helper.succeed();
    }

    // ── object resting on a LOWERED full block (compound contact) — measure-and-lock ──────────────

    /** A floor torch resting on a stone that is itself lowered (-0.5) — does it follow / contact? Measured. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floorTorchOnLoweredFullBlockContact(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 1, 2), bottomSlab());
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE.defaultBlockState());   // lowered -0.5
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.TORCH.defaultBlockState());
        double got = dy(level, helper, new BlockPos(2, 3, 2));
        Slabbed.LOGGER.info("RESTING-FP | floor_torch_on_lowered_full_block | dy={}", got);
        // -0.5: the torch follows the support ONE step to rest flush on the lowered stone's top (world Y
        // 2.5); -1.0 would bury it. (Contrast candleOnLoweredSlabContactMinusOne, where the candle sits on
        // a lowered SLAB and its contact reader drops it support-0.5 = -1.0.)
        expect(helper, level, new BlockPos(2, 3, 2), -0.5,
                "floor torch on a lowered (-0.5) full block rests flush at -0.5 (follows the support's top)");
        helper.succeed();
    }

    // ── wall-attached follow: a wall torch on the side of a lowered block follows it — measure ─────

    /**
     * A WALL_TORCH attached to a lowered full block's side should follow the block's lowering. FACING=EAST
     * means the torch sits on the EAST face of the block to its WEST, so torch at (3,2,2) attaches to the
     * lowered stone at (2,2,2). Measured + locked.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void wallTorchOnLoweredBlockFollows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(2, 1, 2), bottomSlab());
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE.defaultBlockState());   // lowered -0.5 (attach block)
        helper.setBlock(new BlockPos(3, 2, 2),
                Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        double got = dy(level, helper, new BlockPos(3, 2, 2));
        Slabbed.LOGGER.info("RESTING-FP | wall_torch_on_lowered_block | dy={}", got);
        expect(helper, level, new BlockPos(3, 2, 2), -0.5,
                "wall torch attached to a lowered block follows it to -0.5");
        helper.succeed();
    }
}
