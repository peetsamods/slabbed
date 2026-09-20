package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The UNDERSIDE clamp (maintainer ruling, 2026-09-20): a DOWN-face landing is honored to the
 * physical limit of the landing cell, stepped toward grid height until it stops pushing the body
 * into the owner above or the support below, and never past grid height.
 *
 * <p>Live defect this pins: no fence or wall could be placed under any flush TOP slab. The
 * underside formula (design §1.3.2) seats the post half a block up, its 1.5-high body then enters
 * the slab, and the translated-occupancy gate refused the click. Every row here runs FROZEN-ON in
 * process, because the gate only exists in the shipped stored-height mode and the headless venue
 * defaults to the legacy mode — a frozen-OFF row cannot observe the defect at all.
 *
 * <p>Mutation that reddens the placement rows alone: drop {@code clampToUndersideLimit} from the
 * resolver's DOWN branch (the fence and wall rows refuse; the lantern and refusal rows stay green).
 */
public final class UndersideSeatClampTest {

    private static final double EPS = 1.0e-6d;

    private static final BlockPos OWNER = new BlockPos(2, 3, 2);

    private static BlockState slab(Block block, SlabType type) {
        return block.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    /** Real useOn click on the owner's DOWN face, from the same mock-player harness the law rows use. */
    private static void placeUnder(GameTestHelper h, Item item, BlockPos owner) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(owner).add(0.0, -0.5, 0.0);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.DOWN, owner, false)));
    }

    /** Test-only stored fact, mirroring the production write path (see LandingRuleLawTest#forceStore). */
    private static void forceStore(ServerLevel w, BlockPos pos, double dy) {
        LevelChunk chunk = w.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        Long2DoubleOpenHashMap existing = chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
        Long2DoubleOpenHashMap map = existing == null
                ? new Long2DoubleOpenHashMap()
                : new Long2DoubleOpenHashMap(existing);
        map.defaultReturnValue(Double.NaN);
        map.put(pos.asLong(), dy);
        chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, map);
    }

    private interface Body {
        void run();
    }

    /** Synchronous bodies only: the flag is a static global shared with rows that start in the same tick. */
    private static void withFrozen(Body body) {
        boolean prev = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            body.run();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = prev;
        }
    }

    private static void assertPlacedAt(GameTestHelper h, ServerLevel w, BlockPos target, Block expected,
                                       double expectedDy, String why) {
        BlockState placed = w.getBlockState(target);
        if (!placed.is(expected)) {
            throw h.assertionException(target, why + ": expected " + expected + " to be placed, got " + placed);
        }
        double stored = SlabAnchorAttachment.storedPlacementDy(w, target);
        double live = SlabSupport.getYOffset(w, target, placed);
        if (!(Math.abs(stored - expectedDy) <= EPS)) {
            throw h.assertionException(target, why + ": expected stored dy " + expectedDy + ", got " + stored);
        }
        if (!(Math.abs(live - expectedDy) <= EPS)) {
            throw h.assertionException(target, why + ": expected live dy " + expectedDy + ", got " + live);
        }
    }

    /** The reported scene: a fence post clicked onto the underside of a flush TOP slab seats at grid height. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceUnderFlushTopSlabSeatsAtGridHeight(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = h.absolutePos(OWNER);
        withFrozen(() -> {
            w.setBlock(owner, slab(Blocks.BIRCH_SLAB, SlabType.TOP), 2);
            placeUnder(h, Items.BIRCH_FENCE, owner);
            assertPlacedAt(h, w, owner.below(), Blocks.BIRCH_FENCE, 0.0d,
                    "underside clamp: a 1.5-high post cannot rise into the slab, so it seats at grid height");
        });
        h.succeed();
    }

    /** Same family, same clamp: a wall post under a flush TOP slab. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void wallUnderFlushTopSlabSeatsAtGridHeight(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = h.absolutePos(OWNER);
        withFrozen(() -> {
            w.setBlock(owner, slab(Blocks.STONE_SLAB, SlabType.TOP), 2);
            placeUnder(h, Items.COBBLESTONE_WALL, owner);
            assertPlacedAt(h, w, owner.below(), Blocks.COBBLESTONE_WALL, 0.0d,
                    "underside clamp: a wall post under a flush TOP slab seats at grid height");
        });
        h.succeed();
    }

    /** A lowered BOTTOM slab over a solid floor: the -0.5 underside landing would sink the post; it seats flush. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceUnderLoweredBottomSlabOverFloorSeatsAtGridHeight(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = h.absolutePos(OWNER);
        BlockPos target = owner.below();
        withFrozen(() -> {
            w.setBlock(owner, slab(Blocks.BIRCH_SLAB, SlabType.BOTTOM), 2);
            forceStore(w, owner, -0.5d);
            w.setBlock(target.below(), Blocks.STONE.defaultBlockState(), 2);
            placeUnder(h, Items.BIRCH_FENCE, owner);
            assertPlacedAt(h, w, target, Blocks.BIRCH_FENCE, 0.0d,
                    "underside clamp: a post cannot sink into the floor, so it seats at grid height");
        });
        h.succeed();
    }

    /** Open descent control: the same lowered owner over AIR keeps the aimed -0.5 verbatim. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceUnderLoweredBottomSlabOverAirKeepsAim(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = h.absolutePos(OWNER);
        withFrozen(() -> {
            w.setBlock(owner, slab(Blocks.BIRCH_SLAB, SlabType.BOTTOM), 2);
            forceStore(w, owner, -0.5d);
            placeUnder(h, Items.BIRCH_FENCE, owner);
            assertPlacedAt(h, w, owner.below(), Blocks.BIRCH_FENCE, -0.5d,
                    "underside clamp must not raise an open-descent landing that collides with nothing");
        });
        h.succeed();
    }

    /**
     * Scope control: the clamp is overlap-gated, not class-gated. A hanging body that already fits under
     * the slab's visible underside is returned verbatim by the §1.3.2 formula (+0.5 for a flush TOP
     * owner). This row pins that the clamp leaves a fitting body alone; the +0.5 policy itself is the
     * design's, not this row's.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lanternUnderFlushTopSlabIsNotClamped(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = h.absolutePos(OWNER);
        withFrozen(() -> {
            w.setBlock(owner, slab(Blocks.BIRCH_SLAB, SlabType.TOP), 2);
            placeUnder(h, Items.LANTERN, owner);
            assertPlacedAt(h, w, owner.below(), Blocks.LANTERN, 0.5d,
                    "underside clamp must leave a body that fits under the visible underside untouched");
        });
        h.succeed();
    }

    /**
     * The honest limit: the clamp never steps PAST grid height. Under a lowered TOP slab the underside
     * landing is already 0.0, the slab's body sits inside the post's 1.5-high span, and nothing short of
     * sinking the post could make that physical — so the gate keeps refusing.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceUnderLoweredTopSlabStaysRefused(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = h.absolutePos(OWNER);
        withFrozen(() -> {
            w.setBlock(owner, slab(Blocks.BIRCH_SLAB, SlabType.TOP), 2);
            forceStore(w, owner, -0.5d);
            placeUnder(h, Items.BIRCH_FENCE, owner);
            BlockState placed = w.getBlockState(owner.below());
            if (!placed.isAir()) {
                throw h.assertionException(owner.below(),
                        "the clamp must not sink a post below grid height to fit under a lowered TOP slab; got "
                                + placed);
            }
        });
        h.succeed();
    }
}
