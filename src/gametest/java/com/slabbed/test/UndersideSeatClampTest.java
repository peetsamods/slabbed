package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabEnsembleCoherence;
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
import net.minecraft.world.level.EmptyBlockGetter;
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
        BlockState ownerState = h.getLevel().getBlockState(owner);
        double bottom = ownerState.getBlock() instanceof SlabBlock
                && ownerState.getValue(SlabBlock.TYPE) == SlabType.TOP ? 0.5d : 0.0d;
        Vec3 hit = new Vec3(owner.getX() + 0.5d,
                owner.getY() + SlabSupport.getYOffset(h.getLevel(), owner, ownerState) + bottom,
                owner.getZ() + 0.5d);
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

    /** The visible post meets the lowered slab; its above-cell movement barrier cannot veto contact. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceConnectsStackToLoweredCantileverTopSlab(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = h.absolutePos(OWNER);
        withFrozen(() -> {
            w.setBlock(owner, slab(Blocks.BIRCH_SLAB, SlabType.TOP), 2);
            forceStore(w, owner, -0.5d);
            BlockPos target = owner.below();
            w.setBlock(target.below(), Blocks.OAK_FENCE.defaultBlockState(), 2);
            w.setBlock(owner.east(), Blocks.OAK_PLANKS.defaultBlockState(), 2);
            w.setBlock(target.east(), Blocks.OAK_PLANKS.defaultBlockState(), 2);
            BlockState post = Blocks.OAK_FENCE.defaultBlockState();
            double postTop = target.getY() + post.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    .max(Direction.Axis.Y);
            double underside = owner.getY() - 0.5d + 0.5d;
            if (Math.abs(postTop - underside) > EPS) {
                throw h.assertionException(target, "premise: visible fence top must meet the slab underside");
            }
            if (SlabEnsembleCoherence.relativeTranslationIncreasesPlacementBodyOverlap(
                    post, target, 0.0d, w.getBlockState(owner), owner, -0.5d)) {
                throw h.assertionException(target,
                        "a visible fence/slab contact is refused by the fence's above-cell movement barrier");
            }
            if (!SlabEnsembleCoherence.relativeTranslationIncreasesBodyOverlap(
                    post, target, 0.0d, w.getBlockState(owner), owner, -0.5d)) {
                throw h.assertionException(target, "the landing clamp must retain the full collision envelope");
            }
            placeUnder(h, Items.OAK_FENCE, owner);
            assertPlacedAt(h, w, target, Blocks.OAK_FENCE, 0.0d,
                    "a fence must bridge the existing stack and the cantilevered slab");
            w.setBlock(owner.east(), Blocks.AIR.defaultBlockState(), 2);
            w.setBlock(target.east(), Blocks.AIR.defaultBlockState(), 2);
            w.setBlock(target.below(), Blocks.AIR.defaultBlockState(), 2);
            assertPlacedAt(h, w, target, Blocks.OAK_FENCE, 0.0d,
                    "neighbor and support removal must preserve the placed fence height");
            if (Math.abs(post.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    .max(Direction.Axis.Y) - 1.5d) > EPS) {
                throw h.assertionException(target, "the fence's movement barrier must remain 1.5 blocks high");
            }
        });
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void wallBarsAndChainKeepLegalUnderSlabContact(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = h.absolutePos(OWNER);
        withFrozen(() -> {
            for (Block body : new Block[] {Blocks.COBBLESTONE_WALL, Blocks.IRON_BARS, Blocks.IRON_CHAIN}) {
                w.setBlock(owner.below(), Blocks.AIR.defaultBlockState(), 2);
                w.setBlock(owner.below(2), body.defaultBlockState(), 2);
                w.setBlock(owner, slab(Blocks.STONE_SLAB, SlabType.TOP), 2);
                forceStore(w, owner, -0.5d);
                if (SlabEnsembleCoherence.relativeTranslationIncreasesPlacementBodyOverlap(
                        body.defaultBlockState(), owner.below(), 0.0d,
                        w.getBlockState(owner), owner, -0.5d)) {
                    throw h.assertionException(owner.below(), "legal under-slab contact was refused for " + body);
                }
                placeUnder(h, body.asItem(), owner);
                assertPlacedAt(h, w, owner.below(), body, 0.0d, "legal under-slab contact");
            }
        });
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceStillRefusesAVisiblyTooShortGap(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = h.absolutePos(OWNER);
        withFrozen(() -> {
            BlockPos target = owner.below();
            w.setBlock(target.below(), Blocks.OAK_FENCE.defaultBlockState(), 2);
            w.setBlock(owner, slab(Blocks.OAK_SLAB, SlabType.TOP), 2);
            forceStore(w, owner, -1.0d);
            if (!SlabEnsembleCoherence.relativeTranslationIncreasesPlacementBodyOverlap(
                    Blocks.OAK_FENCE.defaultBlockState(), target, 0.0d,
                    w.getBlockState(owner), owner, -1.0d)) {
                throw h.assertionException(target, "a post's visible body must not enter the slab");
            }
            placeUnder(h, Items.OAK_FENCE, owner);
            if (!w.getBlockState(target).isAir()) {
                throw h.assertionException(target, "a full fence cannot fit a half-block gap");
            }
        });
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void visibleFloorAndCeilingOverlapRemainUnsafe(GameTestHelper h) {
        BlockPos lower = BlockPos.ZERO;
        BlockPos upper = lower.above();
        if (!SlabEnsembleCoherence.relativeTranslationIncreasesPlacementBodyOverlap(
                Blocks.STONE.defaultBlockState(), lower, 0.0d,
                Blocks.OAK_FENCE.defaultBlockState(), upper, -0.5d)
                || !SlabEnsembleCoherence.relativeTranslationIncreasesPlacementBodyOverlap(
                        Blocks.STONE.defaultBlockState(), lower, 0.0d,
                        Blocks.STONE.defaultBlockState(), upper, -0.5d)) {
            throw h.assertionException("visible placement bodies must not sink into solid floors");
        }
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void topFencePostReachesTheSlabAcrossAHalfCell(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = h.absolutePos(OWNER);
        withFrozen(() -> {
            w.setBlock(owner, slab(Blocks.OAK_SLAB, SlabType.TOP), 2);
            forceStore(w, owner, 0.0d);
            placeUnder(h, Items.OAK_FENCE, owner);
            BlockPos post = owner.below();
            assertPlacedAt(h, w, post, Blocks.OAK_FENCE, 0.0d, "connected post keeps its placed seat");
            var shape = w.getBlockState(post).getShape(w, post);
            double top = post.getY() + shape.max(Direction.Axis.Y);
            double underside = owner.getY() + 0.5d;
            if (Math.abs(top - underside) > EPS) {
                throw h.assertionException(post, "post outline must meet the slab underside; gap=" + (underside-top));
            }
            Vec3 start = new Vec3(post.getX()-1.0d, post.getY()+1.25d, post.getZ()+0.5d);
            Vec3 end = new Vec3(post.getX()+1.0d, post.getY()+1.25d, post.getZ()+0.5d);
            if (shape.clip(start, end, post) == null) {
                throw h.assertionException(post, "the connecting post must be targetable in the half-cell gap");
            }
            w.setBlock(owner, Blocks.AIR.defaultBlockState(), 2);
            assertPlacedAt(h, w, post, Blocks.OAK_FENCE, 0.0d, "removing the ceiling never moves the post");
            if (Math.abs(w.getBlockState(post).getShape(w, post).max(Direction.Axis.Y)-1.0d)>EPS) {
                throw h.assertionException(post, "a post with no ceiling keeps its normal outline");
            }
        });
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fenceCeilingConnectionDoesNotSpanLargerOpenings(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = h.absolutePos(OWNER);
        withFrozen(() -> {
            w.setBlock(owner, slab(Blocks.OAK_SLAB, SlabType.TOP), 2);
            forceStore(w, owner, 0.0d);
            BlockPos post = owner.below();
            w.setBlock(post, Blocks.OAK_FENCE.defaultBlockState(), 2);
            forceStore(w, post, -0.5d);
            double top = w.getBlockState(post).getShape(w, post).max(Direction.Axis.Y);
            if (Math.abs(top-0.5d)>EPS) {
                throw h.assertionException(post, "a full-cell opening must not stretch the post");
            }
            assertPlacedAt(h, w, post, Blocks.OAK_FENCE, -0.5d, "the control keeps its frozen height");
        });
        h.succeed();
    }
}
