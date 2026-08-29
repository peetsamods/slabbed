package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.util.PlacementDepthPolicy;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Proves the targeting, held-use, and depth-admission boundaries owned by parity phase P6. */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class P6InteractionParityTest {
    private static final String TEMPLATE = "empty";
    private static final double EPSILON = 1.0e-6d;

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void supportedDepthPickWindowReturnsActualNearestOwner(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos deepOwner = ctx.absolutePos(new BlockPos(4, 5, 3));
        BlockPos fartherFlatDecoy = ctx.absolutePos(new BlockPos(6, 3, 3));
        world.setBlock(deepOwner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(fartherFlatDecoy, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        putFact(ctx, world, deepOwner, -4, "the deep target owner");

        for (double visibleBand : new double[]{0.25d, 0.75d}) {
            double rayY = deepOwner.getY() - 2.0d + visibleBand;
            Vec3 start = new Vec3(deepOwner.getX() - 1.5d, rayY, deepOwner.getZ() + 0.5d);
            Vec3 end = new Vec3(fartherFlatDecoy.getX() + 1.5d, rayY, deepOwner.getZ() + 0.5d);
            BlockHitResult hit = SlabbedOffsetRaycast.raycast(world, start, end, CollisionContext.empty());
            ctx.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(deepOwner),
                    "each visible depth band must retain the supported-depth owner; band="
                            + visibleBand + " got " + describe(hit));
            ctx.assertTrue(hit.getDirection() == Direction.WEST,
                    "the nearest owner's west face must own the increasing-X ray; got "
                            + hit.getDirection());
            Vec3 expectedHit = new Vec3(deepOwner.getX(), rayY, deepOwner.getZ() + 0.5d);
            assertVec(ctx, hit.getLocation(), expectedHit,
                    "deep nearest-owner hit point at visible band " + visibleBand);
            ctx.assertTrue(Math.abs(hit.getLocation().distanceToSqr(start)
                            - expectedHit.distanceToSqr(start)) <= EPSILON,
                    "the selected owner must be compared by its actual hit distance");
        }
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void translatedOwnerInsideHitRetainsLogicalOwner(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos deepOwner = ctx.absolutePos(new BlockPos(4, 5, 3));
        world.setBlock(deepOwner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        putFact(ctx, world, deepOwner, -4, "the translated eye-inside owner");
        double rayY = deepOwner.getY() - 1.5d;
        Vec3 insideStart = new Vec3(
                deepOwner.getX() + 0.5d, rayY, deepOwner.getZ() + 0.5d);
        BlockHitResult insideHit = SlabbedOffsetRaycast.raycast(
                world,
                insideStart,
                new Vec3(deepOwner.getX() + 2.0d, rayY, deepOwner.getZ() + 0.5d),
                CollisionContext.empty());
        ctx.assertTrue(insideHit.getType() == HitResult.Type.BLOCK
                        && insideHit.getBlockPos().equals(deepOwner),
                "an eye inside a translated owner must retain that logical owner; got "
                        + describe(insideHit));
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void ownerTieAndShallowWindowRemainStable(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos tieFlatOwner = ctx.absolutePos(new BlockPos(3, 3, 9));
        BlockPos tieDeepOwner = tieFlatOwner.above(2);
        world.setBlock(tieFlatOwner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(tieDeepOwner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        putFact(ctx, world, tieDeepOwner, -4, "the exact-distance translated tie owner");
        Vec3 tieStart = new Vec3(tieFlatOwner.getX() - 1.5d, tieFlatOwner.getY() + 0.5d,
                tieFlatOwner.getZ() + 0.5d);
        BlockHitResult tieHit = SlabbedOffsetRaycast.raycast(
                world,
                tieStart,
                new Vec3(tieFlatOwner.getX() + 2.0d, tieFlatOwner.getY() + 0.5d,
                        tieFlatOwner.getZ() + 0.5d),
                CollisionContext.empty());
        ctx.assertTrue(tieHit.getBlockPos().equals(tieFlatOwner),
                "equal-distance overlapping shapes must retain the marched primary owner; got "
                        + describe(tieHit));

        BlockPos shallowOwner = ctx.absolutePos(new BlockPos(8, 6, 3));
        BlockPos shallowDecoy = ctx.absolutePos(new BlockPos(10, 5, 3));
        for (int x = -2; x <= 4; x++) {
            for (int y = -2; y <= 2; y++) {
                world.setBlock(shallowOwner.offset(x, y, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        world.setBlock(shallowOwner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(shallowDecoy, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        putFact(ctx, world, shallowOwner, -1, "the existing shallow-window owner");
        ctx.assertTrue(world.getBlockState(shallowOwner.below()).isAir(),
                "the shallow neutrality corridor must not contain a competing lower-cell block");
        double shallowRayY = shallowOwner.getY() - 0.25d;
        Vec3 shallowStart = new Vec3(shallowOwner.getX() - 1.5d, shallowRayY,
                shallowOwner.getZ() + 0.5d);
        BlockHitResult shallowHit = SlabbedOffsetRaycast.raycast(
                world,
                shallowStart,
                new Vec3(shallowDecoy.getX() + 1.5d, shallowRayY, shallowOwner.getZ() + 0.5d),
                CollisionContext.empty());
        ctx.assertTrue(shallowHit.getBlockPos().equals(shallowOwner)
                        && shallowHit.getDirection() == Direction.WEST,
                "widening the owner window must remain neutral for an established -0.5 target; got "
                        + describe(shallowHit) + " expected owner=" + shallowOwner);
        assertVec(ctx, shallowHit.getLocation(),
                new Vec3(shallowOwner.getX(), shallowRayY, shallowOwner.getZ() + 0.5d),
                "shallow-window neutrality hit point");

        BlockPos flatOwner = ctx.absolutePos(new BlockPos(4, 3, 6));
        world.setBlock(flatOwner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        Vec3 flatStart = new Vec3(flatOwner.getX() - 1.5d, flatOwner.getY() + 0.5d,
                flatOwner.getZ() + 0.5d);
        Vec3 flatEnd = new Vec3(flatOwner.getX() + 2.0d, flatOwner.getY() + 0.5d,
                flatOwner.getZ() + 0.5d);
        BlockHitResult flatHit = SlabbedOffsetRaycast.raycast(
                world, flatStart, flatEnd, CollisionContext.empty());
        BlockHitResult flatOracle = world.getBlockState(flatOwner).getShape(
                world, flatOwner, CollisionContext.empty()).clip(flatStart, flatEnd, flatOwner);
        ctx.assertTrue(flatOracle != null && flatHit.getBlockPos().equals(flatOwner)
                        && flatHit.getDirection() == flatOracle.getDirection(),
                "the flat control must retain vanilla outline ownership");
        assertVec(ctx, flatHit.getLocation(), flatOracle.getLocation(), "flat hit point");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void heldUseCombinesOrExtendsOnceFromEitherHand(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        InteractionHand[] hands = {InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND};
        for (int index = 0; index < hands.length; index++) {
            BlockPos combineOwner = ctx.absolutePos(new BlockPos(2 + index * 3, 3, 2));
            prepareLoweredSlab(ctx, world, combineOwner);
            UseOutcome combine = useSlab(
                    ctx,
                    world,
                    combineOwner,
                    Direction.UP,
                    new Vec3(combineOwner.getX() + 0.5d, combineOwner.getY(), combineOwner.getZ() + 0.5d),
                    hands[index],
                    Set.of(combineOwner, combineOwner.above()));
            BlockState combined = world.getBlockState(combineOwner);
            ctx.assertTrue(combine.result().consumesAction()
                            && combined.is(Blocks.STONE_SLAB)
                            && combined.getValue(SlabBlock.TYPE) == SlabType.DOUBLE,
                    hands[index] + " combine must use vanilla's same-cell slab mutation");
            ctx.assertTrue(world.getBlockState(combineOwner.above()).isAir(),
                    hands[index] + " combine must not also extend into another cell");
            assertSingleConsumption(ctx, combine, hands[index] + " combine");

            BlockPos extendOwner = ctx.absolutePos(new BlockPos(2 + index * 3, 3, 6));
            prepareLoweredSlab(ctx, world, extendOwner);
            BlockPos extended = extendOwner.east();
            UseOutcome extend = useSlab(
                    ctx,
                    world,
                    extendOwner,
                    Direction.EAST,
                    new Vec3(extendOwner.getX() + 1.0d, extendOwner.getY() - 0.25d,
                            extendOwner.getZ() + 0.5d),
                    hands[index],
                    Set.of(extendOwner, extended));
            ctx.assertTrue(extend.result().consumesAction()
                            && world.getBlockState(extendOwner).getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                            && world.getBlockState(extended).is(Blocks.STONE_SLAB),
                    hands[index] + " extend must preserve the owner and fill exactly its side cell");
            assertSingleConsumption(ctx, extend, hands[index] + " extend");
            ctx.assertTrue(stored(world, extended) != SlabPlacementHeightAttachment.ABSENT_HALF_STEPS,
                    hands[index] + " extend must publish one placement-height fact");
        }
        ctx.succeed();
    }

    /**
     * Deep rows run consent-armed: derived descent floors at the resolved floor, and the
     * deep alphabet is what carries these depths (maintainer ruling, 2026-08-21, matching
     * the reference line). The law under test is unchanged; only its arming moved.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void exactDeepBoundaryIsLegalAndFrozen(GameTestHelper ctx) {
        SlabSupport.armDeepAlphabet(true);
        try {
            slabbedDeepArmedExactDeepBoundaryIsLegalAndFrozen(ctx);
        } finally {
            SlabSupport.armDeepAlphabet(false);
        }
    }

    private void slabbedDeepArmedExactDeepBoundaryIsLegalAndFrozen(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos legalOwner = ctx.absolutePos(new BlockPos(2, 5, 3));
        world.setBlock(legalOwner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        putFact(ctx, world, legalOwner, -4, "the exact supported-depth owner");
        BlockPos legalTarget = legalOwner.east();
        UseOutcome legal = useSlab(
                ctx,
                world,
                legalOwner,
                Direction.EAST,
                new Vec3(legalOwner.getX() + 1.0d, legalOwner.getY() - 1.75d,
                        legalOwner.getZ() + 0.5d),
                InteractionHand.MAIN_HAND,
                Set.of(legalOwner, legalTarget));
        ctx.assertTrue(legal.result().consumesAction() && world.getBlockState(legalTarget).is(Blocks.STONE_SLAB),
                "exactly -2.0 must remain a legal held-item placement");
        assertSingleConsumption(ctx, legal, "exactly -2.0 placement");
        ctx.assertTrue(stored(world, legalTarget) == -4,
                "the legal deep placement must freeze the same exact -2.0 aim");

        BlockPos flatOwner = ctx.absolutePos(new BlockPos(5, 3, 4));
        world.setBlock(flatOwner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        UseOutcome flat = useSlab(
                ctx,
                world,
                flatOwner,
                Direction.EAST,
                new Vec3(flatOwner.getX() + 1.0d, flatOwner.getY() + 0.25d,
                        flatOwner.getZ() + 0.5d),
                InteractionHand.MAIN_HAND,
                Set.of(flatOwner, flatOwner.east()));
        ctx.assertTrue(flat.result().consumesAction()
                        && world.getBlockState(flatOwner.east()).is(Blocks.STONE_SLAB),
                "the ordinary flat control must remain vanilla-placeable");
        assertSingleConsumption(ctx, flat, "flat control");
        ctx.succeed();
    }

    /**
     * The targeting overhaul must SHIP on, and this row binds the literal that decides it.
     *
     * <p>{@link SlabbedOffsetRaycast#ENABLED} is the master switch for offset-aware targeting.
     * Its only consumer is a client render mixin, which sits above every headless entry point, so
     * no server-side row can reach the BEHAVIOUR - and none did: flipping the shipped default to
     * {@code false} left all 250 rows green while a built jar lost offset-aware targeting whole.
     * A flag whose default nothing binds is one careless edit from shipping off.
     *
     * <p>Read through the PRODUCTION field, never a local copy of its initializer. A row that
     * re-evaluates {@code System.getProperty(..., "true")} for itself is unconditionally true and
     * proves only that the test can read its own literal - the exact vacuity this row exists not
     * to be. Referencing the field means a change to that literal changes this row's answer.
     *
     * <p>The unset assertion is the anti-masking clause and is not decoration. This flag is forced
     * in no venue today, which is the only reason the field carries the shipped default here. If
     * some future venue starts forcing it, this row would silently begin measuring the venue
     * instead of the default - so it fails loudly at that moment rather than going quietly vacuous.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void theOffsetTargetingOverhaulShipsEnabled(GameTestHelper ctx) {
        ctx.assertTrue(System.getProperty("slabbed.offsetRaycast") == null,
                "premise: this venue must not force the switch, or this row measures the venue"
                        + " rather than the shipped default; observed "
                        + System.getProperty("slabbed.offsetRaycast"));
        ctx.assertTrue(SlabbedOffsetRaycast.ENABLED,
                "the offset-aware targeting overhaul must ship ENABLED - read from the production"
                        + " field, so flipping its default turns this row red");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void deepBoundaryRefusalIsTypedAndAtomic(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();

        // A stored below-envelope owner can no longer exist legitimately: the read envelope
        // treats it as corruption and resolves the cell on the legacy lane. A real placement
        // against such a cell therefore proceeds like ordinary legacy stone — and must leave
        // the corrupt byte exactly as found (a read, even inside a real transaction, never
        // repairs the store). The typed deep refusal itself is proven by the computed
        // prospective landing below.
        BlockPos refusedOwner = ctx.absolutePos(new BlockPos(2, 5, 6));
        world.setBlock(refusedOwner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        // Derived from the floor, never a literal: one half step past it is the shallowest
        // byte that cannot have been captured legitimately.
        int floorHalfSteps = (int) Math.round(PlacementDepthPolicy.MIN_TARGETABLE_DY / 0.5d);
        putFact(ctx, world, refusedOwner, floorHalfSteps - 1, "the corrupt below-envelope owner");
        BlockPos refusedTarget = refusedOwner.east();
        UseOutcome corruptOwnerUse = useSlab(
                ctx,
                world,
                refusedOwner,
                Direction.EAST,
                new Vec3(refusedOwner.getX() + 1.0d, refusedOwner.getY() - 2.25d,
                        refusedOwner.getZ() + 0.5d),
                InteractionHand.OFF_HAND,
                Set.of(refusedOwner, refusedTarget));
        ctx.assertTrue(corruptOwnerUse.result().consumesAction(),
                "a corrupt owner resolves on the legacy lane, so the placement proceeds; got "
                        + corruptOwnerUse.result());
        ctx.assertTrue(stored(world, refusedOwner) == floorHalfSteps - 1,
                "the real transaction must leave the corrupt byte exactly as found");

        BlockPos landingOwner = ctx.absolutePos(new BlockPos(5, 5, 6));
        BlockPos landingTarget = landingOwner.above();
        world.setBlock(landingOwner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        putFact(ctx, world, landingOwner, floorHalfSteps,
                "the legal root owner for landing refusal");
        LevelChunk landingOwnerChunk = world.getChunk(
                landingOwner.getX() >> 4, landingOwner.getZ() >> 4);
        LevelChunk landingTargetChunk = world.getChunk(
                landingTarget.getX() >> 4, landingTarget.getZ() >> 4);
        landingOwnerChunk.setUnsaved(false);
        landingTargetChunk.setUnsaved(false);
        BlockState landingOwnerBefore = world.getBlockState(landingOwner);
        BlockState landingTargetBefore = world.getBlockState(landingTarget);
        int landingOwnerFactBefore = stored(world, landingOwner);
        int landingTargetFactBefore = stored(world, landingTarget);
        UseOutcome landingRefused = useSlab(
                ctx,
                world,
                landingOwner,
                Direction.UP,
                new Vec3(landingOwner.getX() + 0.5d,
                        landingOwner.getY() + PlacementDepthPolicy.MIN_TARGETABLE_DY + 0.5d,
                        landingOwner.getZ() + 0.5d),
                InteractionHand.MAIN_HAND,
                Set.of(landingOwner, landingTarget));
        ctx.assertTrue(landingRefused.result() == InteractionResult.FAIL,
                "a legal root whose prospective landing is one half step past the floor must"
                        + " return typed FAIL, got "
                        + landingRefused.result());
        ctx.assertTrue(world.getBlockState(landingOwner) == landingOwnerBefore
                        && world.getBlockState(landingTarget) == landingTargetBefore,
                "landing refusal must leave the legal owner and prospective target unchanged");
        ctx.assertTrue(stored(world, landingOwner) == landingOwnerFactBefore
                        && stored(world, landingTarget) == landingTargetFactBefore,
                "landing refusal must leave both placement facts unchanged");
        ctx.assertTrue(landingRefused.selectedCount() == 3 && landingRefused.otherCount() == 5,
                "landing refusal must not consume either hand");
        ctx.assertTrue(landingRefused.placeEvents() == 0,
                "landing refusal must occur before vanilla emits a placement event");
        ctx.assertTrue(!landingOwnerChunk.isUnsaved() && !landingTargetChunk.isUnsaved(),
                "landing refusal must not dirty the owner or prospective target chunk");
        ctx.succeed();
    }

    private static void prepareLoweredSlab(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos owner
    ) {
        world.setBlock(owner,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.UPDATE_ALL);
        putFact(ctx, world, owner, -1, "the lowered slab owner");
    }

    private static UseOutcome useSlab(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos owner,
            Direction face,
            Vec3 hitLocation,
            InteractionHand selectedHand,
            Set<BlockPos> watched
    ) {
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(owner.getX() + 0.5d, owner.getY() + 3.0d, owner.getZ() + 0.5d);
        ItemStack selected = new ItemStack(Items.STONE_SLAB, 3);
        ItemStack other = new ItemStack(Items.DIRT, 5);
        player.setItemInHand(selectedHand, selected);
        player.setItemInHand(otherHand(selectedHand), other);

        AtomicInteger placeEvents = new AtomicInteger();
        Consumer<BlockEvent.EntityPlaceEvent> listener = event -> {
            if (event.getLevel() == world && watched.contains(event.getPos())) {
                placeEvents.incrementAndGet();
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, listener);
        InteractionResult result;
        try {
            result = selected.useOn(new UseOnContext(
                    player,
                    selectedHand,
                    new BlockHitResult(hitLocation, face, owner, false)));
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        return new UseOutcome(result, selected.getCount(), other.getCount(), placeEvents.get());
    }

    private static InteractionHand otherHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private static void assertSingleConsumption(GameTestHelper ctx, UseOutcome outcome, String row) {
        ctx.assertTrue(outcome.selectedCount() == 2,
                row + " must consume exactly one selected-hand item");
        ctx.assertTrue(outcome.otherCount() == 5,
                row + " must not touch the non-selected hand");
        ctx.assertTrue(outcome.placeEvents() == 1,
                row + " must emit exactly one vanilla placement event, got " + outcome.placeEvents());
    }

    private static void putFact(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos pos,
            int halfSteps,
            String row
    ) {
        // Raw store injection: the public write API declines out-of-envelope values by design,
        // and this premise deliberately authors owners the envelope forbids (the refusal rows).
        LevelChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap existing =
                chunk.getExistingDataOrNull(SlabPlacementHeightAttachment.PLACEMENT_DY_TYPE.get());
        it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap facts = existing == null
                ? new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap()
                : new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap(existing);
        facts.put(pos.asLong(), (byte) halfSteps);
        chunk.setData(SlabPlacementHeightAttachment.PLACEMENT_DY_TYPE.get(), facts);
        ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(chunk, pos)
                        .orElse(Integer.MIN_VALUE) == halfSteps,
                "test premise must author " + row + " at halfSteps=" + halfSteps);
    }

    private static int stored(ServerLevel world, BlockPos pos) {
        return SlabPlacementHeightAttachment.storedHalfSteps(
                        world.getChunk(pos.getX() >> 4, pos.getZ() >> 4), pos)
                .orElse(SlabPlacementHeightAttachment.ABSENT_HALF_STEPS);
    }

    private static void assertVec(GameTestHelper ctx, Vec3 actual, Vec3 expected, String row) {
        ctx.assertTrue(actual.distanceToSqr(expected) <= EPSILON * EPSILON,
                row + " expected " + expected + ", got " + actual);
    }

    private static String describe(BlockHitResult hit) {
        return hit.getType() + " owner=" + hit.getBlockPos()
                + " face=" + hit.getDirection() + " point=" + hit.getLocation();
    }

    private record UseOutcome(
            InteractionResult result,
            int selectedCount,
            int otherCount,
            int placeEvents
    ) {
    }
}
