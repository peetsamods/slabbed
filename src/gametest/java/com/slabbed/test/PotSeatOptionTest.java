package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.config.SlabbedConfig;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * THE FLOWER-POT SEAT OPTION (maintainer ruling, 2026-09-06).
 *
 * <p>Slabbed normally seats a flower pot flush on the surface it was placed against. The option lets
 * a player ask for vanilla's gap instead: a pot sits one vanilla gap above its support's VISIBLE top
 * plane — half a block over a bottom slab, nothing over a full block — at any depth. At grid height
 * that is bit-identical to vanilla; at depth it keeps the same relationship instead of leaving the
 * pot hanging in air.
 *
 * <p><b>Every row pins the option explicitly, including the flush ones.</b> That is deliberate: it
 * leaves {@code SlabbedConfigFileTest#shippedPotSeatDefaultIsFlush} as the only row that reads the
 * shipped default, so flipping the shipped literal reddens that row alone.
 *
 * <p><b>{@link #withFrozen} is mandatory on every placement row.</b> The game-test JVM forces the
 * frozen-dy property OFF, and without the flag these rows would measure the legacy live lane rather
 * than the shipped store the option writes into.
 *
 * <p><b>Both test seams are STATIC GLOBALS.</b> The runner starts several tests in one tick, so every
 * body here is synchronous inside try/finally and never spans a tick.
 */
public final class PotSeatOptionTest {

    private static final double EPS = 1.0e-6d;

    // ── seams and helpers, mirroring LandingRuleLawTest's private ones ──

    private interface Body {
        void run();
    }

    private static void withFrozen(Body body) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            body.run();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    private static void withPotSeat(SlabbedConfig.PotSeat seat, Body body) {
        SlabbedConfig previous = SlabbedConfig.setActiveForTesting(SlabbedConfig.withPotSeat(seat));
        try {
            body.run();
        } finally {
            SlabbedConfig.setActiveForTesting(previous);
        }
    }

    private static void place(GameTestHelper h, Item item, BlockPos clicked, Direction face) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked)
                .add(face.getStepX() * 0.5d, face.getStepY() * 0.5d, face.getStepZ() * 0.5d);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    private static double storedDy(ServerLevel w, BlockPos p) {
        return SlabAnchorAttachment.storedPlacementDy(w, p);
    }

    private static void bottomSlab(ServerLevel w, BlockPos p) {
        w.setBlock(p, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
    }

    /**
     * Forces a stored placement dy at {@code pos} — the test-only way to synthesize an owner at a
     * depth real placement cannot produce inside one arena. Mirrors the production write path
     * (public attachment type, NaN default-return).
     */
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

    /** Ground plus an ordinary UNLOWERED bottom slab at {@code column}; returns the slab cell. */
    private static BlockPos unloweredSlabOwner(GameTestHelper h, ServerLevel w, int x, int z) {
        BlockPos ground = h.absolutePos(new BlockPos(x, 1, z));
        w.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
        BlockPos owner = ground.above();
        bottomSlab(w, owner);
        return owner;
    }

    /** Ground plus a bottom slab force-stored at {@code dy}; returns the slab cell. */
    private static BlockPos loweredSlabOwner(GameTestHelper h, ServerLevel w, int x, int z, double dy) {
        BlockPos owner = unloweredSlabOwner(h, w, x, z);
        forceStore(w, owner, dy);
        return owner;
    }

    /** Ground plus an ordinary UNLOWERED full stone block; returns the block cell. */
    private static BlockPos flatFullBlockOwner(GameTestHelper h, ServerLevel w, int x, int z) {
        BlockPos ground = h.absolutePos(new BlockPos(x, 1, z));
        w.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
        BlockPos owner = ground.above();
        w.setBlock(owner, Blocks.STONE.defaultBlockState(), 2);
        return owner;
    }

    /** Ground plus a full stone block force-stored at {@code dy}; returns the block cell. */
    private static BlockPos loweredFullBlockOwner(GameTestHelper h, ServerLevel w, int x, int z, double dy) {
        BlockPos owner = flatFullBlockOwner(h, w, x, z);
        forceStore(w, owner, dy);
        return owner;
    }

    private static BlockPos placePotOnTop(GameTestHelper h, ServerLevel w, BlockPos owner) {
        place(h, Items.FLOWER_POT, owner, Direction.UP);
        BlockPos pot = owner.above();
        if (!w.getBlockState(pot).is(Blocks.FLOWER_POT)) {
            throw h.assertionException(pot, "premise: the real-use flower pot placement failed; got "
                    + w.getBlockState(pot));
        }
        return pot;
    }

    private static void mustStore(GameTestHelper h, ServerLevel w, BlockPos pot, double expected, String what) {
        double stored = storedDy(w, pot);
        if (Double.doubleToRawLongBits(stored) != Double.doubleToRawLongBits(expected)) {
            throw h.assertionException(pot, what + ": expected an exact stored dy of " + expected
                    + ", got " + stored);
        }
    }

    // ══════════════════════════════════════ the rows ══════════════════════════════════════

    /**
     * CONTROL. With the flush seat — the shipped behaviour — a pot on an ordinary bottom slab seats
     * on the slab's surface at -0.5. This is what the mod does today and must keep doing by default.
     *
     * <p>MUTATION that must redden this row alone: make the seat adjustment ignore the option and
     * always add the gap. Only a flush row can see that: the side row is guarded by the seat check
     * and the full-block row's gap is zero.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void potOnBottomSlabSeatsFlushUnderFlush(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        withPotSeat(SlabbedConfig.PotSeat.FLUSH, () -> withFrozen(() -> {
            BlockPos owner = unloweredSlabOwner(h, w, 3, 3);
            BlockPos pot = placePotOnTop(h, w, owner);
            Slabbed.LOGGER.info("POT-SEAT | flush on an unlowered slab: stored={}", storedDy(w, pot));
            mustStore(h, w, pot, -0.5d, "the flush seat must put a pot on the slab's surface");
        }));
        h.succeed();
    }

    /**
     * The vanilla case the option exists for: with the vanilla gap, a pot on an ordinary bottom slab
     * sits at grid height, exactly where vanilla puts it.
     *
     * <p>A fact IS minted, and it is 0.0. Present-and-zero is the point: the stored fact is LAW 1
     * pinning the float, so a slab built underneath later cannot pull the pot down.
     *
     * <p>MUTATION that must redden this row alone: clamp the adjusted value to at most -0.5 ("a pot
     * never mints 0.0"). The depth row expects -0.5, the potted-transition row expects -0.5 and the
     * full-block row expects -1.0, so only this row sees it.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floatPotOnUnloweredBottomSlabSitsAtGrid(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        withPotSeat(SlabbedConfig.PotSeat.VANILLA_FLOAT, () -> withFrozen(() -> {
            BlockPos owner = unloweredSlabOwner(h, w, 3, 3);
            BlockPos pot = placePotOnTop(h, w, owner);
            Slabbed.LOGGER.info("POT-SEAT | vanilla gap on an unlowered slab: stored={}", storedDy(w, pot));
            mustStore(h, w, pot, 0.0d, "the vanilla gap must put a pot on an ordinary slab at grid height");
            if (!SlabAnchorAttachment.rawPlacementDyFact(w, pot).present()) {
                throw h.assertionException(pot, "a floating pot must still MINT its height. A stored 0.0 is "
                        + "what pins the float under LAW 1; with no fact at all a slab built underneath "
                        + "later would pull the pot down.");
            }
        }));
        h.succeed();
    }

    /**
     * THE DEPTH ROW. Over a lowered support the pot keeps ONE vanilla gap above the surface the
     * player can actually see — not a gap measured from the grid. The flush seat would give -1.0
     * here; the vanilla gap must give exactly -0.5. It must NOT be 0.0: that would leave a whole
     * block of air under the pot.
     *
     * <p>MUTATION that must redden this row alone: drop the owner's visible depth from the seat
     * expression (compare against the support's UNLOWERED top). The unlowered-slab row's support has
     * depth 0, so its comparison is unchanged and it stays green; this row's comparison then fails,
     * no adjustment is applied, and it reddens alone.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floatPotOnLoweredBottomSlabKeepsOneVanillaGap(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        withFrozen(() -> {
            BlockPos flushOwner = loweredSlabOwner(h, w, 3, 3, -0.5d);
            withPotSeat(SlabbedConfig.PotSeat.FLUSH, () -> {
                BlockPos pot = placePotOnTop(h, w, flushOwner);
                mustStore(h, w, pot, -1.0d, "control: the flush seat on a -0.5 slab");
            });
            BlockPos floatOwner = loweredSlabOwner(h, w, 6, 3, -0.5d);
            withPotSeat(SlabbedConfig.PotSeat.VANILLA_FLOAT, () -> {
                BlockPos pot = placePotOnTop(h, w, floatOwner);
                Slabbed.LOGGER.info("POT-SEAT | vanilla gap on a -0.5 slab: stored={}", storedDy(w, pot));
                mustStore(h, w, pot, -0.5d, "the vanilla gap over a lowered slab must be half a block above "
                        + "the slab's VISIBLE top, not a block of air above it");
            });
        });
        h.succeed();
    }

    /**
     * Vanilla leaves NO gap above a full block, so the option must not invent one: over an ordinary
     * lowered full block both seats store the same value. This is the row that rejects a fixed
     * "+0.5" adjustment, and it is why the existing pot-on-full-block rows need no pin at all.
     *
     * <p>MUTATION that must redden this row alone: replace the computed gap with the constant 0.5.
     * Every other row's support is a bottom slab, where the constant equals the formula.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floatPotOnFullBlockOwnerMatchesFlush(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        withFrozen(() -> {
            BlockPos flushOwner = loweredFullBlockOwner(h, w, 3, 3, -1.0d);
            withPotSeat(SlabbedConfig.PotSeat.FLUSH, () -> {
                BlockPos pot = placePotOnTop(h, w, flushOwner);
                mustStore(h, w, pot, -1.0d, "control: the flush seat on a -1.0 full block");
            });
            BlockPos floatOwner = loweredFullBlockOwner(h, w, 6, 3, -1.0d);
            withPotSeat(SlabbedConfig.PotSeat.VANILLA_FLOAT, () -> {
                BlockPos pot = placePotOnTop(h, w, floatOwner);
                Slabbed.LOGGER.info("POT-SEAT | vanilla gap on a -1.0 full block: stored={}", storedDy(w, pot));
                mustStore(h, w, pot, -1.0d, "vanilla leaves no gap above a full block, so the option must "
                        + "not lift a pot off one");
            });
        });
        h.succeed();
    }

    /**
     * LAW 1 FOR THE OPTION. A pot that is already placed never moves when the setting changes: a
     * config flip is a neighbour-class event, and a placed block's height is its stored fact for its
     * whole life.
     *
     * <p>MUTATION that must redden this row alone: call the seat adjustment from a READ path — for
     * example applying it to the value the public height read returns from the store. That is the
     * single failure mode this row exists to catch, and no other row would notice it.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void existingPotKeepsItsExactFactWhenTheOptionFlips(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        withFrozen(() -> {
            BlockPos owner = loweredSlabOwner(h, w, 3, 3, -0.5d);
            BlockPos[] pot = new BlockPos[1];
            withPotSeat(SlabbedConfig.PotSeat.FLUSH, () -> pot[0] = placePotOnTop(h, w, owner));

            long factBefore = Double.doubleToRawLongBits(storedDy(w, pot[0]));
            long readBefore = Double.doubleToRawLongBits(
                    SlabSupport.getYOffset(w, pot[0], w.getBlockState(pot[0])));

            withPotSeat(SlabbedConfig.PotSeat.VANILLA_FLOAT, () -> {
                long factAfter = Double.doubleToRawLongBits(storedDy(w, pot[0]));
                long readAfter = Double.doubleToRawLongBits(
                        SlabSupport.getYOffset(w, pot[0], w.getBlockState(pot[0])));
                if (factAfter != factBefore || readAfter != readBefore) {
                    throw h.assertionException(pot[0], "LAW 1: flipping the flower-pot seat must not move a pot "
                            + "that is already placed. fact " + Double.longBitsToDouble(factBefore) + " -> "
                            + Double.longBitsToDouble(factAfter) + ", read "
                            + Double.longBitsToDouble(readBefore) + " -> "
                            + Double.longBitsToDouble(readAfter));
                }
            });
        });
        h.succeed();
    }

    /**
     * THE TWO-WRITER PIN. One placement transaction has two writers — the height fact and the anchor
     * marker — and they must judge the placement by the SAME value. A floating pot's fact says flat,
     * so the marker writer must not classify the same placement as lowered.
     *
     * <p>Compared against a genuinely flat control placement rather than against a hardcoded
     * {@code false}, so the row pins agreement and not merely "no anchor".
     *
     * <p>MUTATION that must redden this row alone: remove the seat adjustment from the marker writer.
     * It is the only row that reads the marker.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floatPotMarkerAgreesWithTheFact(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        withFrozen(() -> {
            BlockPos controlOwner = flatFullBlockOwner(h, w, 6, 3);
            BlockPos[] control = new BlockPos[1];
            withPotSeat(SlabbedConfig.PotSeat.FLUSH, () -> {
                control[0] = placePotOnTop(h, w, controlOwner);
                mustStore(h, w, control[0], 0.0d, "premise: the control pot must be a genuinely flat placement");
            });
            boolean controlAnchored = SlabAnchorAttachment.isAnchored(w, control[0]);
            boolean controlFrozenFlat = SlabAnchorAttachment.isFrozenFlat(w, control[0]);

            BlockPos owner = unloweredSlabOwner(h, w, 3, 3);
            withPotSeat(SlabbedConfig.PotSeat.VANILLA_FLOAT, () -> {
                BlockPos pot = placePotOnTop(h, w, owner);
                mustStore(h, w, pot, 0.0d, "premise: the floating pot must have minted a flat fact");
                boolean anchored = SlabAnchorAttachment.isAnchored(w, pot);
                boolean frozenFlat = SlabAnchorAttachment.isFrozenFlat(w, pot);
                if (anchored) {
                    throw h.assertionException(pot, "the two writers of one placement disagree: the fact says "
                            + "0.0 but the marker writer classified this pot as lowered and anchored it.");
                }
                if (anchored != controlAnchored || frozenFlat != controlFrozenFlat) {
                    throw h.assertionException(pot, "a floating pot must carry the same markers as a genuinely "
                            + "flat pot placement: anchored=" + anchored + "/" + controlAnchored
                            + " frozenFlat=" + frozenFlat + "/" + controlFrozenFlat);
                }
            });
        });
        h.succeed();
    }

    /**
     * SCOPE. A side placement inherits the clicked face's height at any depth (the WYSIWYG any-depth
     * ruling, 2026-09-01) and the option must not touch it: the seat adjustment changes where a pot
     * RESTS, never where a pot FOLLOWS. The scene deliberately puts a non-air block under the landing
     * cell — a slab whose visible top sits BELOW the aimed depth — so a naive "there is something
     * below, add the gap" rule would fire while the real seat expression does not match.
     *
     * <p>MUTATION that must redden this row alone: delete the seat-match guard (adjust whenever the
     * support below is not air). Every other row genuinely seats on the support below, so their
     * values are unchanged.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sidePlacedPotIgnoresThePotSeatOption(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        double[] side = new double[2];
        withFrozen(() -> {
            SlabbedConfig.PotSeat[] seats = {SlabbedConfig.PotSeat.FLUSH, SlabbedConfig.PotSeat.VANILLA_FLOAT};
            for (int i = 0; i < seats.length; i++) {
                final int index = i;
                // Two independent columns, one per option state, so neither reads the other's facts.
                BlockPos ground = h.absolutePos(new BlockPos(3, 1, 2 + index * 4));
                w.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
                BlockPos owner = ground.above(2);
                w.setBlock(owner, Blocks.STONE.defaultBlockState(), 2);
                forceStore(w, owner, -0.5d);
                // A lowered slab under the SIDE landing cell: its visible top sits BELOW the aimed
                // depth, so the landing is an open descent the seat expression cannot match.
                BlockPos sideSupport = owner.east().below();
                bottomSlab(w, sideSupport);
                forceStore(w, sideSupport, -0.5d);

                withPotSeat(seats[index], () -> {
                    place(h, Items.FLOWER_POT, owner, Direction.EAST);
                    BlockPos sidePot = owner.east();
                    if (!w.getBlockState(sidePot).is(Blocks.FLOWER_POT)) {
                        throw h.assertionException(sidePot, "premise: the side-face pot placement failed; got "
                                + w.getBlockState(sidePot));
                    }
                    side[index] = storedDy(w, sidePot);
                });
            }
        });
        Slabbed.LOGGER.info("POT-SEAT | side placement: flush={} vanilla gap={}", side[0], side[1]);
        if (!(side[0] < -EPS)) {
            throw h.assertionException("premise: the side-inherited landing must actually be lowered, else this "
                    + "row could pass while measuring nothing; got " + side[0]);
        }
        if (Double.doubleToRawLongBits(side[0]) != Double.doubleToRawLongBits(side[1])) {
            throw h.assertionException("a SIDE placement inherits the clicked face's height at any depth and the "
                    + "flower-pot seat option must not touch it: flush=" + side[0] + " vanilla gap=" + side[1]);
        }
        h.succeed();
    }

    /**
     * Putting a flower into a pot is an in-place block-kind transition, not a new placement, so the
     * potted variant keeps the exact height the empty pot was given — under the vanilla gap as well
     * as under the flush seat.
     *
     * <p>NOT ISOLATED, and said plainly: deleting the flower-pot arm of the replacement-preservation
     * rule reddens this row AND
     * {@code LandingRuleLawTest#pottedCornflowerUsePreservesExactMinus15Dy}. The two rows cover the
     * two option states of one arm; neither alone proves the arm handles both. This row is not
     * vacuous — it reaches a preserved gap-seated fact no other row can — but it has no private
     * mutation.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void floatPotKeepsItsFactThroughThePottedTransition(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        withPotSeat(SlabbedConfig.PotSeat.VANILLA_FLOAT, () -> withFrozen(() -> {
            BlockPos owner = loweredSlabOwner(h, w, 3, 3, -0.5d);
            BlockPos pot = placePotOnTop(h, w, owner);
            mustStore(h, w, pot, -0.5d, "premise: the empty pot must begin one vanilla gap above the slab");
            long expected = Double.doubleToRawLongBits(-0.5d);

            Player mock = h.makeMockServerPlayer(GameType.SURVIVAL);
            if (!(mock instanceof ServerPlayer player)) {
                throw h.assertionException(pot, "premise: the fixture did not create a ServerPlayer");
            }
            ItemStack cornflower = new ItemStack(Items.CORNFLOWER);
            player.setItemInHand(InteractionHand.MAIN_HAND, cornflower);
            Vec3 visiblePotHit = Vec3.atCenterOf(pot).add(0.0d, -0.5d, 0.0d);
            InteractionResult result = player.gameMode.useItemOn(
                    player,
                    w,
                    cornflower,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(visiblePotHit, Direction.UP, pot, false));
            if (result == null || !result.consumesAction() || !w.getBlockState(pot).is(Blocks.POTTED_CORNFLOWER)) {
                throw h.assertionException(pot, "premise: the real cornflower-on-pot use failed; result="
                        + result + " state=" + w.getBlockState(pot));
            }
            if (Double.doubleToRawLongBits(storedDy(w, pot)) != expected) {
                throw h.assertionException(pot, "an in-place pot transition must preserve the exact height the "
                        + "empty pot was given; expected -0.5, got " + storedDy(w, pot));
            }
        }));
        h.succeed();
    }
}
