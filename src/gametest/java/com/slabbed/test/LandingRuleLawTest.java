package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.placement.LandingHitValidationPolicy;
import com.slabbed.placement.LandingResolver;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * THE GOES LANDING-RULE MATRIX (unified-landing-rule campaign, commit C0).
 *
 * <p>Companion to {@link NeighborUpdateInvarianceTest} (which pins STAYS — the S-2 law gate) and
 * {@link DeepCompoundTowerLawTest} (which pins deep-stack accumulation). This class pins GOES: the
 * unified landing rule of {@code docs/design/GOES-UNIFIED-LANDING-RULE.md} — <b>a placement lands on
 * the clicked visible surface</b>, for every item family, every owner shape, every depth (§1.1).
 *
 * <p><b>EXPECTED STATE: these rows are RED on the current build</b> (the resolver of §1.2 does not
 * exist yet; today's landing is whatever the live read-lane patchwork froze at capture time, §0).
 * Each row is added RED-first following the established convention (see
 * {@code DeepCompoundTowerLawTest}'s javadoc and {@code NeighborUpdateInvarianceTest}'s
 * expected-red pinning of {@code slab_on_deep_lowered_full_block}); its javadoc names the design
 * section it pins and the commit (C2–C6, §4.4) expected to flip it green. A row that is already
 * GREEN today is a documented surprise, called out in its javadoc — that is signal, not noise.
 *
 * <p>Placement uses the real {@code useOn} path wherever possible (the {@code place()} /
 * {@code buildTower()} pattern of the sibling tests — never {@code setBlock} + a hand-rolled state,
 * the false-green shape the law post-mortem names). {@code FROZEN_DY_ENABLED} is flipped in-process
 * (public mutable static, try/finally — the established pattern at
 * {@code DeepCompoundTowerLawTest.java:339-345}) only where a scene needs a synthetic owner to read
 * its deep dy at placement time, or where a read-back is asserted. Rows assert the STORED value
 * ({@link SlabAnchorAttachment#storedPlacementDy}) — capture writes the store regardless of the
 * flag, so a wrong landing is visible as a wrong stored dy.
 */
public final class LandingRuleLawTest {

    private static final double EPS = 1.0e-6;
    private static final String C3_CROSS_CHUNK_STRUCTURE = "slabbed_gametest:c3_cross_chunk";
    private static final int C3_CROSS_CHUNK_FIXTURE_SIZE = 20;

    /**
     * TEST 28 adversarial fixture: its matching descriptor has a different name and does not
     * override Minecraft's real no-item use hook.
     */
    private static final class DescriptorDecoy {
        @SuppressWarnings("unused")
        private static InteractionResult differentlyNamedDescriptor(
                BlockState state,
                Level level,
                BlockPos pos,
                Player player,
                BlockHitResult hit
        ) {
            return InteractionResult.PASS;
        }
    }

    // ── real-useOn placement (matches NeighborUpdateInvarianceTest / DeepCompoundTowerLawTest) ──
    private static void place(GameTestHelper h, Item item, BlockPos clicked, Direction face, double yNudge) {
        placeStack(h, new ItemStack(item), clicked, face, yNudge);
    }

    private static void placeStack(
            GameTestHelper h,
            ItemStack stack,
            BlockPos clicked,
            Direction face,
            double yNudge
    ) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked)
                .add(face.getStepX() * 0.5, face.getStepY() * 0.5 + yNudge, face.getStepZ() * 0.5);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    /** Direct BlockItem.place call with no outer useOn scope: the explicit AIMLESS capture route. */
    private static void placeAimless(GameTestHelper h, Item item, BlockPos clicked, Direction face) {
        if (!(item instanceof BlockItem blockItem)) {
            throw h.assertionException(clicked, "premise: AIMLESS C5 item is not a BlockItem: " + item);
        }
        ItemStack stack = new ItemStack(item);
        Vec3 hit = Vec3.atCenterOf(clicked)
                .add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        InteractionResult result = blockItem.place(new BlockPlaceContext(
                h.getLevel(),
                null,
                InteractionHand.MAIN_HAND,
                stack,
                new BlockHitResult(hit, face, clicked, false)));
        if (result == null || !result.consumesAction()) {
            throw h.assertionException(clicked, "premise: AIMLESS C5 placement failed for " + item
                    + "; result=" + result);
        }
    }

    private static double storedDy(ServerLevel w, BlockPos p) {
        return SlabAnchorAttachment.storedPlacementDy(w, p);
    }

    private static double liveDy(ServerLevel w, BlockPos p) {
        return SlabSupport.getYOffset(w, p, w.getBlockState(p));
    }

    private static void bslab(ServerLevel w, BlockPos p) {
        w.setBlock(p, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
    }

    /**
     * Forces a stored placement dy at {@code pos} to an arbitrary value — the test-only analogue of
     * {@link SlabAnchorAttachment#capturePlacementDy}, but with a chosen value instead of a live
     * read. Used to synthesize a deep owner (or a cantilevered deep body) that real placement cannot
     * yet produce; combined with {@code FROZEN_DY_ENABLED} so {@code getYOffset} returns it. Mirrors
     * the production write path exactly (public {@code PLACEMENT_DY_TYPE}, NaN default-return).
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

    /**
     * Builds an alternating SBSB…S tower via real useOn placement (identical to
     * {@code DeepCompoundTowerLawTest#buildTower}). Index 0 is the first slab on the ground; even
     * indices are bottom slabs, odd indices are full stone blocks. Returns each placed cell,
     * bottom-to-top. Dy sequence (uncapped, per the depth-cap-removal pass that shipped in TEST 17):
     * slab0=0.0, stone1=-0.5, slab2=-0.5, stone3(index3)=-1.0, slab4(index4)=-1.0, stone5(index5)=-1.5.
     */
    private static List<BlockPos> buildTower(GameTestHelper h, ServerLevel w, BlockPos groundAbs, int cellCount) {
        w.setBlock(groundAbs, Blocks.STONE.defaultBlockState(), 2);
        List<BlockPos> cells = new ArrayList<>();
        BlockPos cursor = groundAbs;
        for (int i = 0; i < cellCount; i++) {
            boolean isSlab = (i % 2 == 0);
            Item item = isSlab ? Items.STONE_SLAB : Items.STONE;
            place(h, item, cursor, Direction.UP, 0.0);
            BlockPos placed = cursor.above();
            boolean placedRight = isSlab
                    ? w.getBlockState(placed).getBlock() == Blocks.STONE_SLAB
                    : w.getBlockState(placed).getBlock() == Blocks.STONE;
            if (!placedRight) {
                throw h.assertionException(placed, "premise: tower cell " + i + " (" + item
                        + ") failed to place on " + cursor + " — got " + w.getBlockState(placed).getBlock());
            }
            cells.add(placed);
            cursor = placed;
        }
        return cells;
    }

    /** A real-placed full-block compound owner reading -1.0 (SBSB tower cell index 3). */
    private static BlockPos minus1FullBlockOwner(GameTestHelper h, ServerLevel w) {
        List<BlockPos> cells = buildTower(h, w, h.absolutePos(new BlockPos(3, 1, 3)), 4);
        BlockPos owner = cells.get(3);
        double dy = liveDy(w, owner);
        if (Math.abs(dy + 1.0) > EPS) {
            throw h.assertionException(owner, "premise: -1.0 owner should read -1.0, got " + dy);
        }
        return owner;
    }

    /** A real-placed full-block compound owner reading -1.5 (SBSB tower cell index 5). */
    private static BlockPos minus15FullBlockOwner(GameTestHelper h, ServerLevel w) {
        List<BlockPos> cells = buildTower(h, w, h.absolutePos(new BlockPos(3, 0, 3)), 6);
        BlockPos owner = cells.get(5);
        double dy = liveDy(w, owner);
        if (Math.abs(dy + 1.5) > EPS) {
            throw h.assertionException(owner, "premise: -1.5 owner should read -1.5, got " + dy);
        }
        return owner;
    }

    private interface FrozenBody {
        void run();
    }

    private static void withFrozen(FrozenBody body) {
        boolean prev = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            body.run();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = prev;
        }
    }

    // ═══════════════════════════════ C1 — shipped flag default ═══════════════════════════════

    /**
     * C1 (design D5, §4.4 row C1): the SHIPPED default of
     * {@link SlabAnchorAttachment#FROZEN_DY_ENABLED} is ON.
     *
     * <p>This row cannot read the live field: the gametest JVM forwards {@code -Dslabbed.frozenDy=false}
     * (build.gradle — the frozen-OFF compatibility floor of design §4.1), so the field is {@code false}
     * inside the suite by design. Instead it asserts the field initializer's <em>semantics</em>: with
     * the property absent, the exact expression the field uses at {@code SlabAnchorAttachment.java}
     * ({@code Boolean.parseBoolean(System.getProperty("slabbed.frozenDy", "true"))}) evaluates
     * {@code true}. This mirror MUST be kept in sync with that initializer; flipping the default literal
     * there to {@code "false"} without updating this row is exactly what this test guards against.
     *
     * <p>GREEN as of C1 (this is not an expected-red landing row — it pins the C1 flag flip itself).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void shippedFrozenDyDefaultIsOn(GameTestHelper h) {
        String saved = System.getProperty("slabbed.frozenDy");
        boolean shippedDefault;
        try {
            System.clearProperty("slabbed.frozenDy");
            shippedDefault = Boolean.parseBoolean(System.getProperty("slabbed.frozenDy", "true"));
        } finally {
            if (saved != null) {
                System.setProperty("slabbed.frozenDy", saved);
            }
        }
        Slabbed.LOGGER.info("LANDING-RULE | shipped frozenDy default (property absent) = {}", shippedDefault);
        if (!shippedDefault) {
            throw h.assertionException(BlockPos.ZERO,
                    "C1/D5: the shipped default of FROZEN_DY_ENABLED must be ON. The field initializer at "
                    + "SlabAnchorAttachment.java must read Boolean.parseBoolean(System.getProperty("
                    + "\"slabbed.frozenDy\", \"true\")); got default=false with the property absent.");
        }
        h.succeed();
    }

    // ══════════════════════════════════ C2 family — slabs ══════════════════════════════════

    /**
     * §1.3.1 / family row 1 (C2). Aim at the visible top of a -1.0 full-block compound owner with a
     * stone slab held → must land FLUSH at -1.0. TODAY: the owner-top -1.0 marker is authored at
     * place-RETURN AFTER capture already froze -0.5 (deep-rest excludes exactly -1.0), so it stores
     * -0.5 — the slab floats 0.5 above the aimed surface (6 TEST-17 owner-top rows, all -0.5).
     * EXPECTED RED (stored -0.5, want -1.0).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnVisibleTopOfMinus1FullBlockLandsFlush(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = minus1FullBlockOwner(h, w);
        place(h, Items.STONE_SLAB, owner, Direction.UP, 0.0);
        BlockPos placed = owner.above();
        double stored = storedDy(w, placed);
        Slabbed.LOGGER.info("LANDING-RULE | slab on -1.0 full block: stored={} (want -1.0)", stored);
        if (Math.abs(stored + 1.0) > EPS) {
            throw h.assertionException(placed, "GOES §1.3.1: stone slab must land flush -1.0 on the "
                    + "visible top of a -1.0 owner; stored=" + stored + " (TODAY: -0.5, marker shadowed "
                    + "by capture ordering). Flipped green by C2.");
        }
        h.succeed();
    }

    /**
     * §1.3 TOP-owner+UP-click precedence (A-5 spec gap a) / family row 1 (C2). This test DEFINES the
     * precedence: aiming UP on the visible top of a lowered TOP-type slab owner lands FLUSH on that
     * visible top (rule 1), NOT a DOUBLE merge (rule 4 is BOTTOM+UP only). Owner synthesized: a TOP
     * slab force-stored -1.0 with frozen ON (real placement cannot reliably mint a -1.0 TOP owner —
     * that is the dy-split geometry itself). TODAY the fresh slab reads 0.0 (the carrier lane sees
     * only a lowered FULL BLOCK below, never a slab owner) and the client predicts -0.5 =
     * LIVE_PLACEMENT_SIDE_DY_SPLIT. EXPECTED RED (stored 0.0, want -1.0).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabOnMinus1TopSlabOwnerLandsFlush(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos support = h.absolutePos(new BlockPos(3, 2, 3));
        w.setBlock(support.below(), Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(support, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
        forceStore(w, support, -1.0);
        double[] stored = new double[1];
        withFrozen(() -> {
            if (Math.abs(liveDy(w, support) + 1.0) > EPS) {
                throw h.assertionException(support, "premise: synthetic TOP owner should read -1.0, got "
                        + liveDy(w, support));
            }
            place(h, Items.STONE_SLAB, support, Direction.UP, 0.999);
            BlockPos placed = support.above();
            if (w.getBlockState(placed).getBlock() != Blocks.STONE_SLAB) {
                throw h.assertionException(placed, "premise: slab failed to place above the TOP owner — got "
                        + w.getBlockState(placed).getBlock());
            }
            stored[0] = storedDy(w, placed);
        });
        Slabbed.LOGGER.info("LANDING-RULE | slab on -1.0 TOP-slab owner: stored={} (want -1.0, TODAY 0.0+split)",
                stored[0]);
        if (Math.abs(stored[0] + 1.0) > EPS) {
            throw h.assertionException(support.above(), "GOES §1.3 TOP-owner+UP precedence: land flush "
                    + "-1.0 on the visible top; stored=" + stored[0] + " (TODAY 0.0 server / -0.5 client split). "
                    + "Flipped green by C2.");
        }
        h.succeed();
    }

    /**
     * Family row 1 parity (C2). An OAK slab must land -1.0 on the -1.0 compound owner exactly like a
     * stone slab — the "any slab family" clause of §2 row 1. TODAY every compound-visible marker
     * family is hardcoded {@code Blocks.STONE_SLAB} ({@code SlabAnchorAttachment} isCompound*State),
     * so an oak slab can never take the marker path and lands -0.5 via the carrier fallback.
     * EXPECTED RED (stored -0.5, want -1.0).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void oakSlabParityOnCompoundOwner(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = minus1FullBlockOwner(h, w);
        place(h, Items.OAK_SLAB, owner, Direction.UP, 0.0);
        BlockPos placed = owner.above();
        double stored = storedDy(w, placed);
        Slabbed.LOGGER.info("LANDING-RULE | OAK slab on -1.0 compound owner: stored={} (want -1.0)", stored);
        if (Math.abs(stored + 1.0) > EPS) {
            throw h.assertionException(placed, "GOES row 1 parity: an oak slab must land -1.0 like stone; "
                    + "stored=" + stored + " (TODAY -0.5, stone-only marker pins). Flipped green by C2.");
        }
        h.succeed();
    }

    /**
     * §1.3.3 / family row 2 (C2). Side-click a slab off a -1.5 owner → the placed slab adopts the
     * owner's frame: {@code dy(placed) = dy(owner) = -1.5}. TODAY {@code loweredFullBlockMagnitude}
     * clamps the side lane to -1.0. EXPECTED RED (stored -1.0, want -1.5).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sideSlabOffMinus15OwnerSeatsMinus15(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = minus15FullBlockOwner(h, w);
        // lower-half side click on the WEST face (air to the west of the column) -> BOTTOM slab beside it
        place(h, Items.STONE_SLAB, owner, Direction.WEST, -0.25);
        BlockPos placed = owner.west();
        if (w.getBlockState(placed).getBlock() != Blocks.STONE_SLAB) {
            throw h.assertionException(placed, "premise: side slab failed to place west of the -1.5 owner — got "
                    + w.getBlockState(placed).getBlock());
        }
        double stored = storedDy(w, placed);
        Slabbed.LOGGER.info("LANDING-RULE | side slab off -1.5 owner: stored={} (want -1.5)", stored);
        if (Math.abs(stored + 1.5) > EPS) {
            throw h.assertionException(placed, "GOES §1.3.3: a side slab off a -1.5 owner must seat -1.5; "
                    + "stored=" + stored + " (TODAY -1.0, magnitude clamp). Flipped green by C2.");
        }
        h.succeed();
    }

    /**
     * §1.3.4 / family row 3 (C2). Clicking UP on the top of a lowered BOTTOM slab with a matching
     * slab held merges to DOUBLE in the SAME cell; the merged DOUBLE must keep the owner's stored dy
     * (-0.5 here, from a real-placed slab2). Design note: "keeps owner's stored dy by accident".
     * <b>CONFIRMED GREEN today (C0 run: ownerStored=-0.5, mergedStored=-0.5)</b> — the pos-keyed store
     * survives the same-cell state swap. C2 makes this a rule (explicit re-store) rather than luck.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void doubleMergeKeepsOwnerStoredDy(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        // SBS tower: slab0(0.0) stone1(-0.5) slab2(-0.5). slab2 is a real-placed BOTTOM slab at -0.5.
        List<BlockPos> cells = buildTower(h, w, h.absolutePos(new BlockPos(3, 1, 3)), 3);
        BlockPos ownerSlab = cells.get(2);
        double ownerStored = storedDy(w, ownerSlab);
        if (Math.abs(ownerStored + 0.5) > EPS) {
            throw h.assertionException(ownerSlab, "premise: owner slab2 should be stored -0.5, got " + ownerStored);
        }
        place(h, Items.STONE_SLAB, ownerSlab, Direction.UP, 0.0); // merge to DOUBLE in the SAME cell
        BlockState merged = w.getBlockState(ownerSlab);
        if (merged.getBlock() != Blocks.STONE_SLAB || merged.getValue(SlabBlock.TYPE) != SlabType.DOUBLE) {
            throw h.assertionException(ownerSlab, "premise: UP-click should merge to DOUBLE, got " + merged);
        }
        double stored = storedDy(w, ownerSlab);
        Slabbed.LOGGER.info("LANDING-RULE | DOUBLE merge: ownerStored={} mergedStored={} (want -0.5)",
                ownerStored, stored);
        if (Math.abs(stored + 0.5) > EPS) {
            throw h.assertionException(ownerSlab, "GOES §1.3.4: the merged DOUBLE must keep the owner's -0.5; "
                    + "stored=" + stored + ". Re-stored explicitly by C2.");
        }
        h.succeed();
    }

    // ══════════════════════════════ C3 family — doors / beds ══════════════════════════════

    /**
     * §1.3 / family row 7 (C3). A door on a -1.0 owner must store the SAME dy on BOTH cells (LOWER
     * foot and UPPER): UPPER := LOWER. TODAY {@code DoorBlock.setPlacedBy} never calls super, so the
     * setPlacedBy-HEAD capture never fires for either cell → both stored NaN. EXPECTED RED
     * (stored NaN/NaN, want -1.0/-1.0). Flipped green by C3 (capture at place-RETURN + pair cells).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void doorBothCellsStoredOnLoweredOwner(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = minus1FullBlockOwner(h, w);
        place(h, Items.OAK_DOOR, owner, Direction.UP, 0.0);
        BlockPos lower = owner.above();
        BlockPos upper = lower.above();
        if (!(w.getBlockState(lower).getBlock() instanceof net.minecraft.world.level.block.DoorBlock)) {
            throw h.assertionException(lower, "premise: door LOWER failed to place — got "
                    + w.getBlockState(lower).getBlock());
        }
        double lowerStored = storedDy(w, lower);
        double upperStored = storedDy(w, upper);
        Slabbed.LOGGER.info("LANDING-RULE | door on -1.0 owner: LOWER stored={} UPPER stored={} (want -1.0/-1.0)",
                lowerStored, upperStored);
        List<String> bad = new ArrayList<>();
        // NaN-safe: an uncaptured cell reads NaN, which must FAIL (Math.abs(NaN+1.0) > EPS is false —
        // the false-green the law post-mortem names). Phrase as !(… <= EPS) so NaN is flagged.
        if (!(Math.abs(lowerStored + 1.0) <= EPS)) bad.add("LOWER=" + lowerStored);
        if (!(Math.abs(upperStored + 1.0) <= EPS)) bad.add("UPPER=" + upperStored);
        if (!bad.isEmpty()) {
            throw h.assertionException(lower, "GOES row 7: both door cells must store -1.0; " + bad
                    + " (TODAY NaN/NaN, setPlacedBy no-super capture hole). Flipped green by C3.");
        }
        c3Pass(h, "landing_rule_law_test_door_both_cells_stored_on_lowered_owner");
    }

    /**
     * §1.3 / family row 8 (C3) + A-4/critic-A4 bed-HEAD probe. A bed on an exactly -1.0 surface must
     * store -1.0 on BOTH the FOOT and the HEAD cell. Owner surface synthesized (a 3×3 -1.0 platform,
     * force-stored + frozen) because a bed needs two supported horizontal cells and real deep towers
     * are single-column. TODAY {@code BedBlock.setPlacedBy} supers FIRST then places HEAD via
     * setBlockAndUpdate → the FOOT is captured on a half-formed bed and the HEAD is never captured
     * (NaN). This test LOGS the HEAD stored dy explicitly (the A-4 probe). <b>C0 verified: FOOT
     * captured 0.0 (not -0.5 — the synthetic force-stored platform reads a flat live value for the
     * half-formed bed foot), HEAD=NaN.</b> The NaN-safe assertion (see below) flags the uncaptured
     * HEAD instead of letting it slip. EXPECTED RED (FOOT 0.0, HEAD NaN; want -1.0/-1.0). Green by C3.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bedBothCellsStoredAtExactlyMinus1(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        // 3x3 synthetic -1.0 platform at y=5 (support below at y=4), all force-stored -1.0.
        BlockPos center = h.absolutePos(new BlockPos(3, 5, 3));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos top = center.offset(dx, 0, dz);
                w.setBlock(top.below(), Blocks.STONE.defaultBlockState(), 2);
                w.setBlock(top, Blocks.STONE.defaultBlockState(), 2);
                forceStore(w, top, -1.0);
            }
        }
        double[] footStored = new double[1];
        double[] headStored = new double[]{Double.NaN};
        BlockPos[] parts = new BlockPos[2];
        withFrozen(() -> {
            place(h, Items.BED.red(), center, Direction.UP, 0.0);
            BlockPos foot = center.above();
            BlockState footState = w.getBlockState(foot);
            if (!(footState.getBlock() instanceof BedBlock)) {
                throw h.assertionException(foot, "premise: bed failed to place on the -1.0 platform — got "
                        + footState.getBlock());
            }
            Direction facing = footState.getValue(BlockStateProperties.HORIZONTAL_FACING);
            BedPart part = footState.getValue(BlockStateProperties.BED_PART);
            BlockPos head = (part == BedPart.FOOT) ? foot.relative(facing) : foot.relative(facing.getOpposite());
            parts[0] = foot;
            parts[1] = head;
            footStored[0] = storedDy(w, foot);
            headStored[0] = storedDy(w, head);
        });
        Slabbed.LOGGER.info("LANDING-RULE | bed on exactly -1.0: FOOT({}) stored={} HEAD({}) stored={} (want -1.0/-1.0)",
                parts[0], footStored[0], parts[1], headStored[0]);
        List<String> bad = new ArrayList<>();
        // NaN-safe (see door test): the uncaptured HEAD reads NaN and must be flagged, not slip through.
        if (!(Math.abs(footStored[0] + 1.0) <= EPS)) bad.add("FOOT=" + footStored[0]);
        if (!(Math.abs(headStored[0] + 1.0) <= EPS)) bad.add("HEAD=" + headStored[0]);
        if (!bad.isEmpty()) {
            throw h.assertionException(parts[0], "GOES row 8: both bed cells must store exactly -1.0; " + bad
                    + " (TODAY FOOT 0.0 on half-formed bed, HEAD NaN never captured). Flipped green by C3.");
        }
        c3Pass(h, "landing_rule_law_test_bed_both_cells_stored_at_exactly_minus1");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void doorToggleKeepsBothStoreBits(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos owner = minus1FullBlockOwner(h, world);
        place(h, Items.OAK_DOOR, owner, Direction.UP, 0.0d);
        BlockPos lower = owner.above();
        BlockPos upper = lower.above();
        long lowerBits = requiredStoredBits(h, world, lower);
        long upperBits = requiredStoredBits(h, world, upper);
        world.setBlock(lower, world.getBlockState(lower)
                .setValue(BlockStateProperties.OPEN, true)
                .setValue(BlockStateProperties.POWERED, true), 3);
        world.setBlock(upper, world.getBlockState(upper)
                .setValue(BlockStateProperties.OPEN, true)
                .setValue(BlockStateProperties.POWERED, true), 3);
        if (requiredStoredBits(h, world, lower) != lowerBits
                || requiredStoredBits(h, world, upper) != upperBits) {
            throw h.assertionException(lower, "door toggle changed frozen pair raw bits");
        }
        c3Pass(h, "landing_rule_law_test_door_toggle_keeps_both_store_bits");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void doorPairNormalizesFromEitherHalf(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos owner = minus1FullBlockOwner(h, world);
        place(h, Items.OAK_DOOR, owner, Direction.UP, 0.0d);
        BlockPos lower = owner.above();
        BlockPos upper = lower.above();
        if (!normalizeDoubleHalf(world, lower).equals(List.of(lower, upper))
                || !normalizeDoubleHalf(world, upper).equals(List.of(lower, upper))
                || requiredStoredBits(h, world, lower) != requiredStoredBits(h, world, upper)) {
            throw h.assertionException(lower, "door pair did not normalize identically from LOWER and UPPER");
        }
        c3Pass(h, "landing_rule_law_test_door_pair_normalizes_from_either_half");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void doorMalformedPairWritesNeitherCell(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos owner = h.absolutePos(new BlockPos(3, 3, 3));
        world.setBlock(owner, Blocks.STONE.defaultBlockState(), 3);
        ItemStack malformed = new ItemStack(Items.OAK_DOOR);
        malformed.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(
                BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        placeStack(h, malformed, owner, Direction.UP, 0.0d);
        BlockPos primary = owner.above();
        BlockPos secondary = primary.above();
        if (!(world.getBlockState(primary).getBlock() instanceof DoorBlock)
                || !(world.getBlockState(secondary).getBlock() instanceof DoorBlock)
                || world.getBlockState(primary).getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                != DoubleBlockHalf.UPPER
                || world.getBlockState(secondary).getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                != DoubleBlockHalf.UPPER
                || !Double.isNaN(storedDy(world, primary))
                || !Double.isNaN(storedDy(world, secondary))) {
            throw h.assertionException(primary, "malformed same-half door must write neither cell");
        }
        c3Pass(h, "landing_rule_law_test_door_malformed_pair_writes_neither_cell");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bedNormalizesFromEitherPartAndBlockStateFacingOverride(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos support = h.absolutePos(new BlockPos(3, 4, 3));
        world.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        world.setBlock(support.east(), Blocks.STONE.defaultBlockState(), 3);
        forceStore(world, support, -1.0d);
        forceStore(world, support.east(), -1.0d);
        ItemStack overridden = new ItemStack(Items.BED.red());
        overridden.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(
                BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        withFrozen(() -> placeStack(h, overridden, support, Direction.UP, 0.0d));
        BlockPos foot = support.above();
        BlockPos head = foot.east();
        if (!world.getBlockState(foot).hasProperty(BlockStateProperties.BED_PART)
                || world.getBlockState(foot).getValue(BlockStateProperties.BED_PART) != BedPart.FOOT
                || !world.getBlockState(head).hasProperty(BlockStateProperties.BED_PART)
                || world.getBlockState(head).getValue(BlockStateProperties.BED_PART) != BedPart.HEAD
                || world.getBlockState(foot).getValue(BlockStateProperties.HORIZONTAL_FACING) != Direction.EAST
                || !normalizeBed(world, foot).equals(List.of(foot, head))
                || !normalizeBed(world, head).equals(List.of(foot, head))
                || requiredStoredBits(h, world, foot) != requiredStoredBits(h, world, head)) {
            throw h.assertionException(foot, "bed final-facing override did not select/normalize EAST head");
        }
        c3Pass(h, "landing_rule_law_test_bed_normalizes_from_either_part_and_block_state_facing_override");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void bedMalformedPairWritesNeitherCell(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos support = h.absolutePos(new BlockPos(3, 4, 3));
        world.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        world.setBlock(support.east(), Blocks.STONE.defaultBlockState(), 3);
        ItemStack malformed = new ItemStack(Items.BED.red());
        malformed.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY
                .with(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .with(BlockStateProperties.BED_PART, BedPart.HEAD));
        placeStack(h, malformed, support, Direction.UP, 0.0d);
        BlockPos primary = support.above();
        BlockPos secondary = primary.east();
        if (!(world.getBlockState(primary).getBlock() instanceof BedBlock)
                || !(world.getBlockState(secondary).getBlock() instanceof BedBlock)
                || world.getBlockState(primary).getValue(BlockStateProperties.BED_PART) != BedPart.HEAD
                || world.getBlockState(secondary).getValue(BlockStateProperties.BED_PART) != BedPart.HEAD
                || !Double.isNaN(storedDy(world, primary))
                || !Double.isNaN(storedDy(world, secondary))) {
            throw h.assertionException(primary, "malformed same-part bed must write neither cell");
        }
        c3Pass(h, "landing_rule_law_test_bed_malformed_pair_writes_neither_cell");
    }

    @GameTest(structure = C3_CROSS_CHUNK_STRUCTURE)
    public void bedCrossChunkPairCopiesRawBits(GameTestHelper h) {
        PairScene scene = crossChunkBed(h, false);
        if ((scene.primary().getX() >> 4) == (scene.partner().getX() >> 4)
                || requiredStoredBits(h, scene.world(), scene.primary())
                != requiredStoredBits(h, scene.world(), scene.partner())) {
            throw h.assertionException(scene.primary(), "cross-chunk bed did not copy one exact raw dy");
        }
        c3Pass(h, "landing_rule_law_test_bed_cross_chunk_pair_copies_raw_bits");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void c3_pair_generic_double_block_copies_raw_bits(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos owner = h.absolutePos(new BlockPos(3, 3, 3));
        world.setBlock(owner, Blocks.STONE.defaultBlockState(), 3);
        forceStore(world, owner, -1.0d);
        withFrozen(() -> PlacementCaptureBoundaryGameTest.withPairFixture(() -> placeStack(h,
                new ItemStack(Items.STONE), owner, Direction.UP, 0.0d)));
        BlockPos lower = owner.above();
        BlockPos upper = lower.above();
        if (!world.getBlockState(lower).is(PlacementCaptureBoundaryGameTest.PAIR_BLOCK)
                || !world.getBlockState(upper).is(PlacementCaptureBoundaryGameTest.PAIR_BLOCK)
                || requiredStoredBits(h, world, lower) != requiredStoredBits(h, world, upper)) {
            throw h.assertionException(lower, "generic DOUBLE_BLOCK_HALF pair did not copy primary raw bits");
        }
        c3Pass(h, "landing_rule_law_test_c3_pair_generic_double_block_copies_raw_bits");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void c3_pair_same_chunk_one_publication(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos owner = h.absolutePos(new BlockPos(3, 3, 3));
        world.setBlock(owner, Blocks.STONE.defaultBlockState(), 3);
        forceStore(world, owner, -1.0d);
        BlockPos lower = owner.above();
        long publicationProbe = SlabAnchorAttachment.beginC3PublicationProbeForTests(lower, lower.above());
        try {
            withFrozen(() -> PlacementCaptureBoundaryGameTest.withPairFixture(() -> placeStack(h,
                    new ItemStack(Items.STONE), owner, Direction.UP, 0.0d)));
            if (SlabAnchorAttachment.c3PublicationCountForTests(publicationProbe) != 1
                    || requiredStoredBits(h, world, lower) != requiredStoredBits(h, world, lower.above())) {
                throw h.assertionException(lower, "same-chunk pair must publish one complete dy map; count="
                        + SlabAnchorAttachment.c3PublicationCountForTests(publicationProbe));
            }
        } finally {
            SlabAnchorAttachment.stopC3PublicationProbeForTests(publicationProbe);
        }
        c3Pass(h, "landing_rule_law_test_c3_pair_same_chunk_one_publication");
    }

    @GameTest(structure = C3_CROSS_CHUNK_STRUCTURE)
    public void c3_pair_cross_chunk_one_publication_per_chunk(GameTestHelper h) {
        PairScene scene = crossChunkBed(h, true);
        int firstX = scene.primary().getX() >> 4;
        int firstZ = scene.primary().getZ() >> 4;
        int secondX = scene.partner().getX() >> 4;
        int secondZ = scene.partner().getZ() >> 4;
        try {
            if (SlabAnchorAttachment.c3PublicationCountForTests(scene.publicationProbe()) != 2
                    || SlabAnchorAttachment.c3PublicationCountForTests(
                            scene.publicationProbe(), firstX, firstZ) != 1
                    || SlabAnchorAttachment.c3PublicationCountForTests(
                            scene.publicationProbe(), secondX, secondZ) != 1) {
                throw h.assertionException(scene.primary(), "cross-chunk pair must publish once per chunk; total="
                        + SlabAnchorAttachment.c3PublicationCountForTests(scene.publicationProbe()));
            }
        } finally {
            SlabAnchorAttachment.stopC3PublicationProbeForTests(scene.publicationProbe());
        }
        c3Pass(h, "landing_rule_law_test_c3_pair_cross_chunk_one_publication_per_chunk");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void c3_pair_owner_shape_depth_placement_and_validation_matrix(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(3, 6, 3));
        BlockState[] owners = {
                Blocks.STONE.defaultBlockState(),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE),
                Blocks.CHEST.defaultBlockState()
        };
        BlockState[] heldPairs = {Blocks.OAK_DOOR.defaultBlockState(), Blocks.BED.red().defaultBlockState()};
        for (BlockState ownerState : owners) {
            for (double depth : new double[]{-1.0d, -2.0d}) {
                for (BlockState held : heldPairs) {
                    Vec3 hit = new Vec3(owner.getX() + 0.5d, owner.getY() + depth + 0.25d,
                            owner.getZ() + 0.5d);
                    LandingResolver.PlacementAim aim = new LandingResolver.PlacementAim(
                            owner, ownerState, depth, Direction.UP, hit, false);
                    LandingResolver.PlacementResolution resolution = LandingResolver.resolve(
                            aim, owner.above(), held, LandingResolver.Family.PAIRED_FLOOR_SEAT);
                    double expected = depth + ((ownerState.getBlock() instanceof SlabBlock
                            && ownerState.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) ? -0.5d : 0.0d);
                    double validation = LandingHitValidationPolicy.shiftedCenterDy(
                            owner, ownerState, depth, Direction.UP, hit, held);
                    if (resolution == null
                            || Double.doubleToRawLongBits(resolution.landingDy())
                            != Double.doubleToRawLongBits(expected)
                            || Double.doubleToRawLongBits(validation) != Double.doubleToRawLongBits(depth)) {
                        throw h.assertionException(owner, "C3 owner/depth matrix failed owner=" + ownerState
                                + " depth=" + depth + " held=" + held + " resolution=" + resolution
                                + " validation=" + validation + " expected=" + expected);
                    }
                }
            }
        }
        c3Pass(h, "landing_rule_law_test_c3_pair_owner_shape_depth_placement_and_validation_matrix");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void c3_pair_validation_negative_controls(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(3, 5, 3));
        Vec3 inside = new Vec3(owner.getX() + 0.5d, owner.getY() - 1.5d, owner.getZ() + 0.5d);
        BlockState door = Blocks.OAK_DOOR.defaultBlockState();
        double positive = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.STONE.defaultBlockState(), -2.0d, Direction.UP, inside, door);
        double nonUp = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.STONE.defaultBlockState(), -2.0d, Direction.NORTH, inside, door);
        double partial = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.OAK_FENCE.defaultBlockState(), -2.0d, Direction.UP, inside, door);
        double outside = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.STONE.defaultBlockState(), -2.0d, Direction.UP,
                new Vec3(owner.getX() + 1.5d, owner.getY() - 1.5d, owner.getZ() + 0.5d), door);
        double air = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.AIR.defaultBlockState(), -2.0d, Direction.UP, inside, door);
        java.util.function.Predicate<BlockState> previous =
                com.slabbed.compat.CompatHooks.shouldSkipSlabSupportTestOverride;
        double compat;
        try {
            com.slabbed.compat.CompatHooks.shouldSkipSlabSupportTestOverride =
                    state -> state.getBlock() == PlacementCaptureBoundaryGameTest.PAIR_BLOCK;
            compat = LandingHitValidationPolicy.shiftedCenterDy(
                    owner, Blocks.STONE.defaultBlockState(), -2.0d, Direction.UP, inside,
                    PlacementCaptureBoundaryGameTest.PAIR_BLOCK.defaultBlockState());
        } finally {
            com.slabbed.compat.CompatHooks.shouldSkipSlabSupportTestOverride = previous;
        }
        if (Double.doubleToRawLongBits(positive) != Double.doubleToRawLongBits(-2.0d)
                || !Double.isNaN(nonUp)
                || !Double.isNaN(partial)
                || !Double.isNaN(outside)
                || !Double.isNaN(air)
                || !Double.isNaN(compat)) {
            throw h.assertionException(owner, "C3 validation widened: positive=" + positive + " nonUp=" + nonUp
                    + " partial=" + partial + " outside=" + outside + " air=" + air + " compat=" + compat);
        }
        c3Pass(h, "landing_rule_law_test_c3_pair_validation_negative_controls");
    }

    private record PairScene(ServerLevel world, BlockPos primary, BlockPos partner, long publicationProbe) {
    }

    private static PairScene crossChunkBed(GameTestHelper h, boolean trackPublications) {
        ServerLevel world = h.getLevel();
        BlockPos firstCorner = h.absolutePos(BlockPos.ZERO);
        BlockPos oppositeCorner = h.absolutePos(new BlockPos(
                C3_CROSS_CHUNK_FIXTURE_SIZE - 1,
                7,
                C3_CROSS_CHUNK_FIXTURE_SIZE - 1));
        int minX = Math.min(firstCorner.getX(), oppositeCorner.getX());
        int minY = Math.min(firstCorner.getY(), oppositeCorner.getY());
        int minZ = Math.min(firstCorner.getZ(), oppositeCorner.getZ());
        int boundaryX = minX + Math.floorMod(15 - minX, 16);
        BlockPos support = new BlockPos(boundaryX, minY + 4, minZ + 2);
        world.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        world.setBlock(support.east(), Blocks.STONE.defaultBlockState(), 3);
        forceStore(world, support, -1.0d);
        forceStore(world, support.east(), -1.0d);
        BlockPos primary = support.above();
        BlockPos partner = primary.east();
        long publicationProbe = trackPublications
                ? SlabAnchorAttachment.beginC3PublicationProbeForTests(primary, partner)
                : -1L;
        ItemStack bed = new ItemStack(Items.BED.red());
        bed.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(
                BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        try {
            withFrozen(() -> placeStack(h, bed, support, Direction.UP, 0.0d));
        } catch (RuntimeException | Error failure) {
            if (publicationProbe >= 0L) {
                SlabAnchorAttachment.stopC3PublicationProbeForTests(publicationProbe);
            }
            throw failure;
        }
        return new PairScene(world, primary, partner, publicationProbe);
    }

    private static List<BlockPos> normalizeDoubleHalf(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return List.of();
        }
        BlockPos lower = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                ? pos : pos.below();
        return List.of(lower, lower.above());
    }

    private static List<BlockPos> normalizeBed(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)
                || !state.hasProperty(BlockStateProperties.BED_PART)) {
            return List.of();
        }
        BlockPos foot = state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT
                ? pos
                : pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        return List.of(foot, foot.relative(world.getBlockState(foot)
                .getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    private static long requiredStoredBits(GameTestHelper h, ServerLevel world, BlockPos pos) {
        SlabAnchorAttachment.PlacementDyFact fact = SlabAnchorAttachment.rawPlacementDyFact(world, pos);
        if (!fact.present()) {
            throw h.assertionException(pos, "required C3 stored fact is absent");
        }
        return fact.rawBits();
    }

    private static void c3Pass(GameTestHelper h, String methodId) {
        Slabbed.LOGGER.info("C3_FOCUSED | slabbed_gametest:{} | PASS", methodId);
        h.succeed();
    }

    // ══════════════════════════════ C4 family — objects ══════════════════════════════

    /**
     * C4 shared-cause discriminator: unrelated ordinary object families must enter the same
     * placement-time aim authority and the same deep-hit validation authority. C5 remains a distinct
     * AIM-KEYED family.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void c4ObjectsShareLandingAndHitValidationAuthority(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(3, 5, 3));
        BlockState ownerState = Blocks.STONE.defaultBlockState();
        double ownerDy = -1.5d;
        Vec3 hit = new Vec3(owner.getX() + 0.5d, owner.getY() + ownerDy + 0.25d,
                owner.getZ() + 0.5d);
        LandingResolver.PlacementAim aim = new LandingResolver.PlacementAim(
                owner, ownerState, ownerDy, Direction.UP, hit, false);
        BlockState[] c4Objects = {
                Blocks.FLOWER_POT.defaultBlockState(),
                Blocks.OAK_FENCE_GATE.defaultBlockState(),
                Blocks.ACACIA_BUTTON.defaultBlockState(),
                Blocks.CONDUIT.defaultBlockState(),
                Blocks.LADDER.defaultBlockState(),
                Blocks.OAK_HANGING_SIGN.defaultBlockState()
        };

        LandingResolver.Family sharedFamily = LandingResolver.classify(c4Objects[0]);
        List<String> violations = new ArrayList<>();
        for (BlockState object : c4Objects) {
            LandingResolver.Family family = LandingResolver.classify(object);
            LandingResolver.PlacementResolution resolution = LandingResolver.resolve(
                    aim, owner.above(), object, family);
            double validation = LandingHitValidationPolicy.shiftedCenterDy(
                    owner, ownerState, ownerDy, Direction.UP, hit, object);
            if (family == LandingResolver.Family.UNSUPPORTED
                    || family != sharedFamily
                    || resolution == null
                    || Double.doubleToRawLongBits(resolution.landingDy())
                    != Double.doubleToRawLongBits(ownerDy)
                    || Double.doubleToRawLongBits(validation)
                    != Double.doubleToRawLongBits(ownerDy)) {
                violations.add(object.getBlock() + ": family=" + family
                        + " resolution=" + resolution + " validation=" + validation);
            }
        }

        LandingResolver.Family carpetFamily =
                LandingResolver.classify(Blocks.MOSS_CARPET.defaultBlockState());
        LandingResolver.Family powderFamily =
                LandingResolver.classify(Blocks.POWDER_SNOW.defaultBlockState());
        if (carpetFamily != LandingResolver.Family.AIM_KEYED_FLOOR_SEAT
                || powderFamily != LandingResolver.Family.USE_CREATED_FULL_CUBE_CONTACT
                || carpetFamily == sharedFamily
                || powderFamily == sharedFamily) {
            violations.add("C4/C5 family boundary collapsed: C4=" + sharedFamily
                    + " carpet=" + carpetFamily + " powder=" + powderFamily);
        }
        if (!violations.isEmpty()) {
            throw h.assertionException(owner, "C4 ordinary objects do not share one landing/hit authority:\n  "
                    + String.join("\n  ", violations));
        }
        h.succeed();
    }

    /**
     * §1.3.1 / family row 9 (C4). A flower pot on a -1.0 owner must seat FLUSH at -1.0. TODAY it
     * lands -0.5 — the deliberate exactly--1.0 deep-rest exclusion leaks for objects (TEST-17 GAP:
     * "flower_pot at -0.5 on EXACTLY -1.0 supports"). EXPECTED RED (stored -0.5, want -1.0).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void potOnMinus1StoneSeatsFlush(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = minus1FullBlockOwner(h, w);
        place(h, Items.FLOWER_POT, owner, Direction.UP, 0.0);
        BlockPos placed = owner.above();
        double stored = storedDy(w, placed);
        Slabbed.LOGGER.info("LANDING-RULE | pot on -1.0 owner: stored={} (want -1.0)", stored);
        if (Math.abs(stored + 1.0) > EPS) {
            throw h.assertionException(placed, "GOES row 9: pot must seat flush -1.0 on a -1.0 owner; stored="
                    + stored + " (TODAY -0.5, exactly--1.0 boundary leak). Flipped green by C4.");
        }
        h.succeed();
    }

    /**
     * §1.3.1 / family row 9 (C4). A flower pot on a -1.5 owner must seat FLUSH at -1.5. The TEST-17
     * ledger reports objects at -1.5 "work" via the deep-rest lane — but <b>C0 SURPRISE: this is RED
     * today (stored=-0.5) on an SBSB real-placed -1.5 tower top</b>. The ledger's "-1.5 works" evidence
     * was on a marked-slab rig owner, not an SBSB full-block tower top — the pot's own landing lane
     * does not pick up the deep-rest -1.5 through this owner shape. Signal for C4: the object deep-rest
     * pickup is owner-shape-dependent, not depth-uniform. Flipped green by C4.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void potOnMinus15StoneSeatsFlush(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = minus15FullBlockOwner(h, w);
        place(h, Items.FLOWER_POT, owner, Direction.UP, 0.0);
        BlockPos placed = owner.above();
        double stored = storedDy(w, placed);
        Slabbed.LOGGER.info("LANDING-RULE | pot on -1.5 owner: stored={} (want -1.5; ledger says WORKS)", stored);
        if (Math.abs(stored + 1.5) > EPS) {
            throw h.assertionException(placed, "GOES row 9: pot must seat flush -1.5 on a -1.5 owner; stored="
                    + stored + ". Flipped green by C4 (or already green — report).");
        }
        h.succeed();
    }

    /**
     * Live RED: inserting a cornflower is an in-place flower-pot block-kind transition, not a new
     * placement. The occupied cell must keep the exact frozen height authored when the empty pot was
     * placed; otherwise the potted variant falls back from -1.5 to -1.0 and visibly jumps half a block.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void pottedCornflowerUsePreservesExactMinus15Dy(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos base = h.absolutePos(new BlockPos(3, 1, 3));
        world.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bslab(world, base.above(1));
        world.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        bslab(world, base.above(3));
        BlockPos compoundSource = base.above(4);
        world.setBlock(compoundSource, Blocks.STONE.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(world, compoundSource, world.getBlockState(compoundSource));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(
                world, compoundSource, world.getBlockState(compoundSource));
        BlockPos owner = compoundSource.west();
        bslab(world, owner);
        SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(
                world,
                owner,
                world.getBlockState(owner),
                compoundSource,
                world.getBlockState(compoundSource));
        BlockPos pot = owner.above();
        long expectedBits = Double.doubleToRawLongBits(-1.5d);
        long supportBits = Double.doubleToRawLongBits(-1.0d);

        withFrozen(() -> {
            double supportLive = liveDy(world, owner);
            if (Double.doubleToRawLongBits(supportLive) != supportBits) {
                throw h.assertionException(owner, "premise: marked side-lower slab must read exact dy=-1.0; live="
                        + supportLive);
            }
            place(h, Items.FLOWER_POT, owner, Direction.UP, 0.0d);
            if (!world.getBlockState(pot).is(Blocks.FLOWER_POT)) {
                throw h.assertionException(pot, "premise: real-use flower pot placement failed");
            }
            double emptyStored = storedDy(world, pot);
            double emptyLive = liveDy(world, pot);
            if (Double.doubleToRawLongBits(emptyStored) != expectedBits
                    || Double.doubleToRawLongBits(emptyLive) != expectedBits) {
                throw h.assertionException(pot, "premise: empty pot must begin at exact dy=-1.5; stored="
                        + emptyStored + " live=" + emptyLive);
            }

            Player mock = h.makeMockServerPlayer(GameType.SURVIVAL);
            if (!(mock instanceof ServerPlayer player)) {
                throw h.assertionException(pot, "premise: real-use fixture did not create a ServerPlayer");
            }
            ItemStack cornflower = new ItemStack(Items.CORNFLOWER);
            player.setItemInHand(InteractionHand.MAIN_HAND, cornflower);
            Vec3 visiblePotHit = Vec3.atCenterOf(pot).add(0.0d, -1.5d, 0.0d);
            InteractionResult result = player.gameMode.useItemOn(
                    player,
                    world,
                    cornflower,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(visiblePotHit, Direction.UP, pot, false));
            if (result == null || !result.consumesAction()
                    || !world.getBlockState(pot).is(Blocks.POTTED_CORNFLOWER)) {
                throw h.assertionException(pot, "premise: real cornflower-on-pot use failed; result="
                        + result + " state=" + world.getBlockState(pot));
            }

            double pottedStored = storedDy(world, pot);
            double pottedLive = liveDy(world, pot);
            if (Double.doubleToRawLongBits(pottedStored) != expectedBits
                    || Double.doubleToRawLongBits(pottedLive) != expectedBits) {
                if (!Double.isNaN(pottedStored)
                        || Double.doubleToRawLongBits(pottedLive) != supportBits) {
                    throw h.assertionException(pot, "wrong RED shape: expected deleted store and exact "
                            + "fallback dy=-1.0; stored=" + pottedStored + " live=" + pottedLive);
                }
                throw h.assertionException(pot, "POTTED_CORNFLOWER_HEIGHT_JUMP_RED: in-place pot use must "
                        + "preserve exact dy=-1.5; stored=" + pottedStored + " live=" + pottedLive);
            }

            List<Block> potVariants = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block instanceof FlowerPotBlock) {
                    potVariants.add(block);
                }
            }
            if (potVariants.size() < 2
                    || !potVariants.contains(Blocks.FLOWER_POT)
                    || !potVariants.contains(Blocks.POTTED_CORNFLOWER)) {
                throw h.assertionException(pot, "premise: registered FlowerPotBlock family is incomplete; count="
                        + potVariants.size());
            }

            Block firstVariant = potVariants.get(0);
            world.setBlock(pot, firstVariant.defaultBlockState(), Block.UPDATE_ALL);
            for (int i = 0; i < potVariants.size(); i++) {
                Block oldVariant = potVariants.get(i);
                Block newVariant = potVariants.get((i + 1) % potVariants.size());
                if (!world.getBlockState(pot).is(oldVariant)) {
                    throw h.assertionException(pot, "premise: pot-family sweep lost its old variant at index "
                            + i + "; expected=" + BuiltInRegistries.BLOCK.getKey(oldVariant)
                            + " actual=" + world.getBlockState(pot));
                }
                world.setBlock(pot, newVariant.defaultBlockState(), Block.UPDATE_ALL);
                double variantStored = storedDy(world, pot);
                double variantLive = liveDy(world, pot);
                if (!world.getBlockState(pot).is(newVariant)
                        || Double.doubleToRawLongBits(variantStored) != expectedBits
                        || Double.doubleToRawLongBits(variantLive) != expectedBits) {
                    throw h.assertionException(pot, "registered pot-family transition must preserve exact "
                            + "dy=-1.5; old=" + BuiltInRegistries.BLOCK.getKey(oldVariant)
                            + " new=" + BuiltInRegistries.BLOCK.getKey(newVariant)
                            + " stored=" + variantStored + " live=" + variantLive);
                }
            }

            world.setBlock(pot, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            if (Double.doubleToRawLongBits(storedDy(world, pot)) != expectedBits) {
                throw h.assertionException(pot, "premise: existing full-block replacement preservation changed");
            }
            world.setBlock(pot, Blocks.FLOWER_POT.defaultBlockState(), Block.UPDATE_ALL);
            if (!Double.isNaN(storedDy(world, pot))) {
                throw h.assertionException(pot, "non-pot to flower-pot replacement must not preserve stored dy");
            }
        });
        h.succeed();
    }

    // ══════════════════════════════ C5 family — thin layers / powder snow ══════════════════════════════

    /**
     * C5/TEST 25 boundary: carpet remains UP-only while use-created powder snow owns all-face
     * full-cube contact. The same final-state compat gate still preserves Terrain Slabs' ownership.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void c5AimKeyedFamilySharesLandingValidationAndCompatAuthority(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(3, 4, 3));
        BlockState ownerState = Blocks.STONE.defaultBlockState();
        double ownerDy = -1.0d;
        Vec3 hit = new Vec3(owner.getX() + 0.5d, owner.getY() - 0.5d, owner.getZ() + 0.5d);
        LandingResolver.PlacementAim upAim = new LandingResolver.PlacementAim(
                owner, ownerState, ownerDy, Direction.UP, hit, false);
        LandingResolver.PlacementAim sideAim = new LandingResolver.PlacementAim(
                owner, ownerState, ownerDy, Direction.SOUTH, hit, false);
        LandingResolver.PlacementAim replacementAim = new LandingResolver.PlacementAim(
                owner, ownerState, ownerDy, Direction.UP, hit, true);
        BlockState carpet = Blocks.MOSS_CARPET.defaultBlockState();
        BlockState powder = Blocks.POWDER_SNOW.defaultBlockState();
        List<String> violations = new ArrayList<>();
        LandingResolver.Family carpetFamily = LandingResolver.classify(carpet);
        LandingResolver.PlacementResolution carpetUp =
                LandingResolver.resolve(upAim, owner.above(), carpet, carpetFamily);
        LandingResolver.PlacementResolution carpetSide =
                LandingResolver.resolve(sideAim, owner.south(), carpet, carpetFamily);
        double carpetValidationUp = LandingHitValidationPolicy.shiftedCenterDy(
                owner, ownerState, ownerDy, Direction.UP, hit, carpet);
        double carpetValidationSide = LandingHitValidationPolicy.shiftedCenterDy(
                owner, ownerState, ownerDy, Direction.SOUTH, hit, carpet);
        if (carpetFamily != LandingResolver.Family.AIM_KEYED_FLOOR_SEAT
                || carpetUp == null
                || Double.doubleToRawLongBits(carpetUp.landingDy())
                != Double.doubleToRawLongBits(ownerDy)
                || carpetSide != null
                || Double.doubleToRawLongBits(carpetValidationUp)
                != Double.doubleToRawLongBits(ownerDy)
                || !Double.isNaN(carpetValidationSide)) {
            violations.add("carpet: family=" + carpetFamily + " up=" + carpetUp + " side=" + carpetSide
                    + " validationUp=" + carpetValidationUp + " validationSide=" + carpetValidationSide);
        }

        LandingResolver.Family powderFamily = LandingResolver.classify(powder);
        LandingResolver.PlacementResolution powderUp =
                LandingResolver.resolve(upAim, owner.above(), powder, powderFamily);
        LandingResolver.PlacementResolution powderSide =
                LandingResolver.resolve(sideAim, owner.south(), powder, powderFamily);
        LandingResolver.PlacementResolution powderReplacement =
                LandingResolver.resolve(replacementAim, owner, powder, powderFamily);
        LandingResolver.PlacementResolution powderLegacyWorldResolution =
                LandingResolver.resolve(
                        EmptyBlockGetter.INSTANCE, owner.above(), powder, Direction.UP, powderFamily);
        double powderValidationUp = LandingHitValidationPolicy.shiftedCenterDy(
                owner, ownerState, ownerDy, Direction.UP, hit, powder);
        double powderValidationSide = LandingHitValidationPolicy.shiftedCenterDy(
                owner, ownerState, ownerDy, Direction.SOUTH, hit, powder);
        if (powderFamily != LandingResolver.Family.USE_CREATED_FULL_CUBE_CONTACT
                || powderUp == null
                || powderSide == null
                || powderReplacement == null
                || !powderReplacement.merge()
                || powderLegacyWorldResolution != null
                || Double.doubleToRawLongBits(powderUp.landingDy())
                != Double.doubleToRawLongBits(ownerDy)
                || Double.doubleToRawLongBits(powderSide.landingDy())
                != Double.doubleToRawLongBits(ownerDy)
                || Double.doubleToRawLongBits(powderReplacement.landingDy())
                != Double.doubleToRawLongBits(ownerDy)
                || Double.doubleToRawLongBits(powderValidationUp)
                != Double.doubleToRawLongBits(ownerDy)
                || Double.doubleToRawLongBits(powderValidationSide)
                != Double.doubleToRawLongBits(ownerDy)) {
            violations.add("powder: family=" + powderFamily + " up=" + powderUp + " side=" + powderSide
                    + " replacement=" + powderReplacement + " legacy=" + powderLegacyWorldResolution
                    + " validationUp=" + powderValidationUp
                    + " validationSide=" + powderValidationSide);
        }

        java.util.function.Predicate<BlockState> previous = LandingResolver.compatFinalStateTestOverride;
        try {
            LandingResolver.compatFinalStateTestOverride =
                    state -> state.is(Blocks.MOSS_CARPET) || state.is(Blocks.POWDER_SNOW);
            for (BlockState state : new BlockState[]{carpet, powder}) {
                LandingResolver.Family family = LandingResolver.classify(state);
                LandingResolver.PlacementResolution resolution =
                        LandingResolver.resolve(upAim, owner.above(), state, family);
                double validation = LandingHitValidationPolicy.shiftedCenterDy(
                        owner, ownerState, ownerDy, Direction.UP, hit, state);
                if (resolution != null || !Double.isNaN(validation)) {
                    violations.add("compat-owned " + state.getBlock() + " authored C5 dy: resolution="
                            + resolution + " validation=" + validation);
                }
            }
        } finally {
            LandingResolver.compatFinalStateTestOverride = previous;
        }

        if (!violations.isEmpty()) {
            throw h.assertionException(owner, "C5/TEST 25 contact authority boundary failed:\n  "
                    + String.join("\n  ", violations));
        }
        h.succeed();
    }

    /** Real bucket world-space contact matrix for every face and representative non-stone supports. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void powderSnowUseCreatedContactCoversAllFacesAndSupportShapes(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        double ownerDy = -1.5d;
        BlockState powder = Blocks.POWDER_SNOW.defaultBlockState();
        LandingResolver.Family powderFamily = LandingResolver.classify(powder);
        BlockState[] ownerStates = {
                Blocks.OAK_PLANKS.defaultBlockState(),
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE)
        };
        List<String> violations = new ArrayList<>();

        withFrozen(() -> {
            for (int supportIndex = 0; supportIndex < ownerStates.length; supportIndex++) {
                BlockState ownerState = ownerStates[supportIndex];
                for (Direction face : Direction.values()) {
                    BlockPos owner = h.absolutePos(new BlockPos(
                            3 + supportIndex * 6, 8, 3 + face.ordinal() * 6));
                    BlockPos target = owner.relative(face);
                    w.setBlock(owner, ownerState, 3);
                    w.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
                    forceStore(w, owner, ownerDy);

                    place(h, Items.POWDER_SNOW_BUCKET, owner, face, 0.0d);
                    if (!w.getBlockState(target).is(Blocks.POWDER_SNOW)) {
                        throw h.assertionException(target, "TEST 25 premise: real powder bucket on "
                                + ownerState + " " + face + " did not place in vanilla adjacent target "
                                + target + "; got " + w.getBlockState(target).getBlock());
                    }

                    VoxelShape ownerShape = w.getBlockState(owner).getShape(
                            EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
                    VoxelShape powderShape = w.getBlockState(target).getShape(
                            EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
                    var ownerBounds = ownerShape.bounds();
                    var powderBounds = powderShape.bounds();
                    double ownerLiveDy = liveDy(w, owner);
                    double powderLiveDy = liveDy(w, target);

                    double hitX = owner.getX() + 0.5d;
                    double hitY = owner.getY() + ownerLiveDy
                            + (ownerBounds.minY + ownerBounds.maxY) * 0.5d;
                    double hitZ = owner.getZ() + 0.5d;
                    switch (face) {
                        case UP -> hitY = owner.getY() + ownerLiveDy + ownerBounds.maxY;
                        case DOWN -> hitY = owner.getY() + ownerLiveDy + ownerBounds.minY;
                        case EAST -> hitX = owner.getX() + ownerBounds.maxX;
                        case WEST -> hitX = owner.getX() + ownerBounds.minX;
                        case SOUTH -> hitZ = owner.getZ() + ownerBounds.maxZ;
                        case NORTH -> hitZ = owner.getZ() + ownerBounds.minZ;
                    }
                    Vec3 hit = new Vec3(hitX, hitY, hitZ);
                    double validation = LandingHitValidationPolicy.shiftedCenterDy(
                            owner, ownerState, ownerLiveDy, face, hit, powder);

                    double ownerContact;
                    double powderContact;
                    double frameError = 0.0d;
                    switch (face) {
                        case UP -> {
                            ownerContact = owner.getY() + ownerLiveDy + ownerBounds.maxY;
                            powderContact = target.getY() + powderLiveDy + powderBounds.minY;
                        }
                        case DOWN -> {
                            ownerContact = owner.getY() + ownerLiveDy + ownerBounds.minY;
                            powderContact = target.getY() + powderLiveDy + powderBounds.maxY;
                        }
                        case EAST -> {
                            ownerContact = owner.getX() + ownerBounds.maxX;
                            powderContact = target.getX() + powderBounds.minX;
                            frameError = target.getY() + powderLiveDy - (owner.getY() + ownerLiveDy);
                        }
                        case WEST -> {
                            ownerContact = owner.getX() + ownerBounds.minX;
                            powderContact = target.getX() + powderBounds.maxX;
                            frameError = target.getY() + powderLiveDy - (owner.getY() + ownerLiveDy);
                        }
                        case SOUTH -> {
                            ownerContact = owner.getZ() + ownerBounds.maxZ;
                            powderContact = target.getZ() + powderBounds.minZ;
                            frameError = target.getY() + powderLiveDy - (owner.getY() + ownerLiveDy);
                        }
                        case NORTH -> {
                            ownerContact = owner.getZ() + ownerBounds.minZ;
                            powderContact = target.getZ() + powderBounds.maxZ;
                            frameError = target.getY() + powderLiveDy - (owner.getY() + ownerLiveDy);
                        }
                        default -> throw new IllegalStateException("Unexpected face " + face);
                    }
                    double contactError = powderContact - ownerContact;
                    double storedBeforeNeighborEdit = storedDy(w, target);
                    BlockPos editedNeighbor = target.east().equals(owner)
                            ? target.north()
                            : target.east();
                    w.setBlock(editedNeighbor, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                    double storedAfterNeighborEdit = storedDy(w, target);
                    if (Math.abs(contactError) > EPS
                            || Math.abs(frameError) > EPS
                            || Double.doubleToRawLongBits(ownerLiveDy)
                            != Double.doubleToRawLongBits(ownerDy)
                            || Double.doubleToRawLongBits(validation)
                            != Double.doubleToRawLongBits(ownerLiveDy)
                            || Double.doubleToRawLongBits(storedBeforeNeighborEdit)
                            != Double.doubleToRawLongBits(storedAfterNeighborEdit)
                            || !w.getBlockState(target).is(Blocks.POWDER_SNOW)) {
                        violations.add(ownerState + " " + face + ": powderDy=" + powderLiveDy
                                + " contactError=" + contactError + " frameError=" + frameError
                                + " validation=" + validation + " storedBefore=" + storedBeforeNeighborEdit
                                + " storedAfter=" + storedAfterNeighborEdit);
                    }
                }
            }
        });
        if (powderFamily != LandingResolver.Family.USE_CREATED_FULL_CUBE_CONTACT
                || !violations.isEmpty()) {
            throw h.assertionException(h.absolutePos(new BlockPos(3, 8, 3)),
                    "TEST 25 real-bucket all-face/support contact matrix failed; family="
                    + powderFamily + ":\n  " + String.join("\n  ", violations));
        }
        h.succeed();
    }

    /**
     * §1.3.1 / family row 5 (C5). A carpet AIMED at a lowered owner's visible top must become
     * logically seated (stored -1.0), not render-only courtesy. TODAY {@code isThinTopLayer} vetoes
     * all lowering, so the carpet stores flush 0.0. EXPECTED RED (stored 0.0, want -1.0). Flipped
     * green by C5 (AIM-KEYED thin-layer seat + carpet triad unification).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void carpetAimedOnLoweredOwnerSeats(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = minus1FullBlockOwner(h, w);
        place(h, Items.MOSS_CARPET, owner, Direction.UP, 0.0);
        BlockPos placed = owner.above();
        if (w.getBlockState(placed).getBlock() != Blocks.MOSS_CARPET) {
            throw h.assertionException(placed, "premise: carpet failed to place on the -1.0 owner — got "
                    + w.getBlockState(placed).getBlock());
        }
        double stored = storedDy(w, placed);
        Slabbed.LOGGER.info("LANDING-RULE | carpet aimed on -1.0 owner: stored={} (want -1.0)", stored);
        if (Math.abs(stored + 1.0) > EPS) {
            throw h.assertionException(placed, "GOES row 5: an aimed carpet must logically seat -1.0; stored="
                    + stored + " (TODAY 0.0, isThinTopLayer veto). Flipped green by C5.");
        }
        double[] logical = new double[1];
        withFrozen(() -> logical[0] = liveDy(w, placed));
        if (Math.abs(logical[0] + 1.0) > EPS) {
            throw h.assertionException(placed, "C5 carpet stored/model/shape authority must read logical -1.0; got "
                    + logical[0]);
        }
        h.succeed();
    }

    /**
     * §1.3.1 / family row 6 (C5), decision D2. Hand-bucketed powder snow AIMED at a lowered owner
     * must seat (stored -1.0). TODAY explicit name guards keep powder snow flush → stored 0.0.
     * EXPECTED RED (stored 0.0, want -1.0). Flipped green by C5 (powder snow AIM-KEYED as full-cube).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void powderSnowBucketAimedSeats(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = minus1FullBlockOwner(h, w);
        place(h, Items.POWDER_SNOW_BUCKET, owner, Direction.UP, 0.0);
        BlockPos placed = owner.above();
        if (w.getBlockState(placed).getBlock() != Blocks.POWDER_SNOW) {
            throw h.assertionException(placed, "premise: powder snow failed to place on the -1.0 owner — got "
                    + w.getBlockState(placed).getBlock());
        }
        double stored = storedDy(w, placed);
        Slabbed.LOGGER.info("LANDING-RULE | powder snow bucket aimed on -1.0 owner: stored={} (want -1.0)", stored);
        if (Math.abs(stored + 1.0) > EPS) {
            throw h.assertionException(placed, "GOES row 6: aimed powder snow must seat -1.0; stored=" + stored
                    + " (TODAY 0.0, name guard). Flipped green by C5.");
        }
        withFrozen(() -> {
            double logical = liveDy(w, placed);
            VoxelShape outline = w.getBlockState(placed).getShape(w, placed, CollisionContext.empty());
            if (Math.abs(logical + 1.0) > EPS
                    || outline.isEmpty()
                    || Math.abs(outline.bounds().minY + 1.0d) > EPS
                    || Math.abs(outline.bounds().maxY) > EPS) {
                throw h.assertionException(placed, "C5 powder logical/outline authority mismatch: dy="
                        + logical + " outline=" + outline);
            }
            Vec3 start = new Vec3(placed.getX() - 0.5d, placed.getY() - 0.5d, placed.getZ() + 0.5d);
            Vec3 end = new Vec3(placed.getX() + 1.5d, placed.getY() - 0.5d, placed.getZ() + 0.5d);
            BlockHitResult ray = SlabbedOffsetRaycast.raycast(w, start, end, CollisionContext.empty());
            if (ray.getType() != HitResult.Type.BLOCK || !ray.getBlockPos().equals(placed)) {
                throw h.assertionException(placed, "C5 powder raycast did not consume the lowered outline; hit="
                        + ray.getType() + "@" + ray.getBlockPos());
            }
        });
        h.succeed();
    }

    /**
     * Player-authored mega-rig RED: a powder-snow bucket used on the DOWN face of a frozen -0.5
     * stone owner must place a body whose visible top contacts the owner's visible underside.
     * TODAY it creates powder snow in {@code owner.below()} at dy=0.0: full cubes overlap by 0.5.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void powderSnowBucketDownContactMatchesMinusHalfStoneUnderside(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = h.absolutePos(new BlockPos(3, 5, 3));
        w.setBlock(owner, Blocks.STONE.defaultBlockState(), 2);
        forceStore(w, owner, -0.5d);
        BlockPos placed = owner.below();
        double[] contact = new double[5];
        withFrozen(() -> {
            place(h, Items.POWDER_SNOW_BUCKET, owner, Direction.DOWN, 0.0d);
            if (!w.getBlockState(placed).is(Blocks.POWDER_SNOW)) {
                throw h.assertionException(placed, "premise: DOWN powder-snow bucket did not create powder snow in "
                        + "owner.below(); got " + w.getBlockState(placed).getBlock());
            }

            VoxelShape ownerVanillaShape = w.getBlockState(owner).getShape(
                    EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
            VoxelShape powderVanillaShape = w.getBlockState(placed).getShape(
                    EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
            contact[0] = owner.getY() + ownerVanillaShape.bounds().minY + liveDy(w, owner);
            contact[1] = placed.getY() + powderVanillaShape.bounds().maxY + liveDy(w, placed);
            contact[2] = contact[1] - contact[0];
            contact[3] = storedDy(w, placed);
            w.setBlock(placed.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            contact[4] = storedDy(w, placed);
        });
        double expectedOwnerUnderside = contact[0];
        double actualPowderTop = contact[1];
        double contactError = contact[2];
        if (!(Math.abs(contactError) <= EPS)) {
            throw h.assertionException(placed, "DOWN-CONTACT-RED expectedOwnerUnderside=" + expectedOwnerUnderside
                    + " actualPowderTop=" + actualPowderTop + " signedError(actual-expected)=" + contactError
                    + " EPS=" + EPS);
        }
        if (Double.doubleToRawLongBits(contact[3]) != Double.doubleToRawLongBits(-0.5d)
                || Double.doubleToRawLongBits(contact[4]) != Double.doubleToRawLongBits(contact[3])) {
            throw h.assertionException(placed, "TEST 25 frozen placement dy changed across neighbor edit: before="
                    + contact[3] + " after=" + contact[4]);
        }
        h.succeed();
    }

    /**
     * AIM is the discriminator: direct BlockItem.place and natural/setBlock routes have no captured
     * root aim, so carpet and powder snow stay exactly flush even beside slab geometry.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void c5AimlessAndSetBlockRoutesStayFlush(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos carpetOwner = h.absolutePos(new BlockPos(1, 3, 1));
        BlockPos powderOwner = h.absolutePos(new BlockPos(4, 3, 1));
        w.setBlock(carpetOwner, Blocks.STONE.defaultBlockState(), 2);
        w.setBlock(powderOwner, Blocks.STONE.defaultBlockState(), 2);
        forceStore(w, carpetOwner, -1.0d);
        forceStore(w, powderOwner, -1.0d);
        double[] aimless = new double[4];
        withFrozen(() -> {
            placeAimless(h, Items.MOSS_CARPET, carpetOwner, Direction.UP);
            placeAimless(h, Items.POWDER_SNOW_BUCKET, powderOwner, Direction.UP);
            aimless[0] = storedDy(w, carpetOwner.above());
            aimless[1] = liveDy(w, carpetOwner.above());
            aimless[2] = storedDy(w, powderOwner.above());
            aimless[3] = liveDy(w, powderOwner.above());
        });

        BlockPos naturalCarpet = h.absolutePos(new BlockPos(1, 3, 4));
        BlockPos naturalPowder = h.absolutePos(new BlockPos(4, 3, 4));
        bslab(w, naturalCarpet.below());
        bslab(w, naturalPowder.below());
        w.setBlock(naturalCarpet, Blocks.MOSS_CARPET.defaultBlockState(), 2);
        w.setBlock(naturalPowder, Blocks.POWDER_SNOW.defaultBlockState(), 2);
        double[] natural = new double[2];
        withFrozen(() -> {
            natural[0] = liveDy(w, naturalCarpet);
            natural[1] = liveDy(w, naturalPowder);
        });

        if (!w.getBlockState(carpetOwner.above()).is(Blocks.MOSS_CARPET)
                || !w.getBlockState(powderOwner.above()).is(Blocks.POWDER_SNOW)
                || Double.doubleToRawLongBits(aimless[0]) != Double.doubleToRawLongBits(0.0d)
                || Double.doubleToRawLongBits(aimless[1]) != Double.doubleToRawLongBits(0.0d)
                || Double.doubleToRawLongBits(aimless[2]) != Double.doubleToRawLongBits(0.0d)
                || Double.doubleToRawLongBits(aimless[3]) != Double.doubleToRawLongBits(0.0d)
                || !Double.isNaN(storedDy(w, naturalCarpet))
                || !Double.isNaN(storedDy(w, naturalPowder))
                || Double.doubleToRawLongBits(natural[0]) != Double.doubleToRawLongBits(0.0d)
                || Double.doubleToRawLongBits(natural[1]) != Double.doubleToRawLongBits(0.0d)) {
            throw h.assertionException(naturalCarpet, "C5 aimless/natural routes must remain dy=0.0: aimless="
                    + java.util.Arrays.toString(aimless) + " natural=" + java.util.Arrays.toString(natural)
                    + " naturalStores=[" + storedDy(w, naturalCarpet) + ", "
                    + storedDy(w, naturalPowder) + "]");
        }
        h.succeed();
    }

    // ══════════════════════════ A-1 — cantilevered deep side slab targetability ══════════════════════════

    /**
     * Amendment A-1's RED row (the raycast-contiguity hole the design's own SIDE rule creates). §2
     * row 2 promises chained side slabs inherit stored magnitude at ANY depth, cantilevered over air.
     * But {@code SlabbedOffsetRaycast}'s deep-owner probe air-terminates walking up from the marched
     * cell ({@code SlabbedOffsetRaycast.java:182-183}), on the in-code INVARIANT that "real placement
     * always accumulates depth through a contiguous support column". A side-inherited slab deeper than
     * -2.0 over air has an air cell between its visible body and its grid cell → the probe breaks →
     * the body is UNTARGETABLE: you can place it but never aim at it again.
     *
     * <p>Real placement cannot yet make this shape (that is the whole point), so it is synthesized:
     * a lone BOTTOM slab at grid cell G with air below it, force-stored dy=-2.5 with frozen ON so
     * {@code getYOffset(G)} returns -2.5. Its visible body then renders in world-band [G.y-2.5, G.y-2.0]
     * (cell G.y-3), with an AIR gap at G.y-1 and G.y-2. A near-horizontal ray through that band is
     * asserted to hit G. TODAY the k=2 deep probe from the marched cell (G.y-3) tests G.y-1, finds
     * air, and breaks before ever reaching G → MISS. EXPECTED RED. Flipped green by the store-aware
     * deep probe (or SIDE depth cap) the A-1 amendment requires.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void cantileveredDeepSideSlabRemainsTargetable(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos g = h.absolutePos(new BlockPos(3, 6, 3));
        // Cantilever: the slab's own grid cell is solid-below-less (air under it), the deep body floats.
        w.setBlock(g, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        forceStore(w, g, -2.5);
        boolean[] hitBody = new boolean[1];
        BlockHitResult[] result = new BlockHitResult[1];
        withFrozen(() -> {
            double dy = liveDy(w, g);
            if (Math.abs(dy + 2.5) > EPS) {
                throw h.assertionException(g, "premise: synthetic cantilever slab should read -2.5, got " + dy);
            }
            // Ray through the visible body band [g.y-2.5, g.y-2.0] at world-y g.y-2.25, z through the footprint.
            double y = g.getY() - 2.25;
            double z = g.getZ() + 0.5;
            Vec3 start = new Vec3(g.getX() - 1.5, y, z);
            Vec3 end = new Vec3(g.getX() + 0.9, y, z);
            result[0] = SlabbedOffsetRaycast.raycast(w, start, end, CollisionContext.empty());
            hitBody[0] = result[0].getType() == HitResult.Type.BLOCK && result[0].getBlockPos().equals(g);
        });
        Slabbed.LOGGER.info("LANDING-RULE | cantilever -2.5 side slab targetable: hit={} type={} pos={}",
                hitBody[0], result[0].getType(),
                result[0].getType() == HitResult.Type.BLOCK ? result[0].getBlockPos().toShortString() : "-");
        if (!hitBody[0]) {
            throw h.assertionException(g, "A-1: the raycast must attribute a hit to the visible body of a "
                    + "cantilevered -2.5 side slab at " + g.toShortString() + ", got type=" + result[0].getType()
                    + " pos=" + (result[0].getType() == HitResult.Type.BLOCK
                            ? result[0].getBlockPos().toShortString() : "miss")
                    + " (TODAY: air-termination breaks the deep probe before reaching the owner). "
                    + "Fixed by the store-aware deep probe / SIDE depth cap the A-1 amendment requires.");
        }
        h.succeed();
    }

    /**
     * TEST 19 live RED (C2 server validation): the real player aimed a stone slab at the SOUTH face
     * of an ordinary full-block owner stored at {@code dy=-2.0}. The client predicted the correct
     * -2.0 placement, but the server rejected the packet as too far because its slab-eligibility
     * qualifier still delegated to the legacy exact--1 compound-remap grammar.
     *
     * <p>The absolute live coordinates were owner {@code 512,-39,509}, hit
     * {@code 512.5,-40.45004642009735,510.0}; this fixture preserves their owner-relative geometry.
     * The RED phase called the legacy qualifier and failed with
     * {@code source_not_compound_full_block_dy_-1}. The green proof calls the same pure C2 policy used
     * by the server mixin; the placement remap grammar itself remains unchanged.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deepSideServerValidationAcceptsResolverOwnedSlab(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos base = h.absolutePos(new BlockPos(3, 1, 3));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bslab(w, base.above(1));
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        bslab(w, base.above(3));
        BlockPos owner = base.above(4);
        w.setBlock(owner, Blocks.STONE.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, owner, w.getBlockState(owner));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(w, owner, w.getBlockState(owner));
        if (!SlabAnchorAttachment.isCompoundFullBlockAnchor(w, owner)) {
            throw h.assertionException(owner, "premise: owner must retain the legacy compound marker");
        }
        forceStore(w, owner, -2.0d);

        double[] shiftedDy = new double[1];
        withFrozen(() -> {
            double ownerDy = liveDy(w, owner);
            if (Double.doubleToRawLongBits(ownerDy) != Double.doubleToRawLongBits(-2.0d)) {
                throw h.assertionException(owner, "premise: owner must read exact stored dy=-2.0, got " + ownerDy);
            }
            Vec3 liveLikeHit = new Vec3(
                    owner.getX() + 0.5d,
                    owner.getY() - 1.45004642009735d,
                    owner.getZ() + 1.0d);
            shiftedDy[0] = LandingHitValidationPolicy.shiftedCenterDy(
                    owner,
                    w.getBlockState(owner),
                    ownerDy,
                    Direction.SOUTH,
                    liveLikeHit,
                    Blocks.STONE_SLAB.defaultBlockState());
        });

        if (Double.doubleToRawLongBits(shiftedDy[0]) != Double.doubleToRawLongBits(-2.0d)) {
            throw h.assertionException(owner, "TEST 19: resolver-owned slab side hit must shift the server "
                    + "validation center by exact dy=-2.0, got " + shiftedDy[0]);
        }
        h.succeed();
    }

    /** TEST 26: generic resolver-held placement validates against a lowered slab's occupied body. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void test26SlabOwnerServerValidationUsesTranslatedOccupiedShape(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(3, 4, 3));
        double ownerDy = -1.5d;
        BlockState heldTorch = Blocks.TORCH.defaultBlockState();
        BlockState oakBottom = Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        Vec3 bottomTopHit = new Vec3(
                owner.getX() + 0.5d,
                owner.getY() + ownerDy + 0.5d,
                owner.getZ() + 0.5d);

        double oakBottomActual = LandingHitValidationPolicy.shiftedCenterDy(
                owner, oakBottom, ownerDy, Direction.UP, bottomTopHit, heldTorch);
        if (Double.doubleToRawLongBits(oakBottomActual)
                != Double.doubleToRawLongBits(ownerDy)) {
            throw h.assertionException(owner, "TEST 26 RED: torch on the visible top of an oak bottom "
                    + "slab at exact dy=-1.5 must shift the server validation center; actual="
                    + oakBottomActual + " expected=" + ownerDy);
        }

        List<String> violations = new ArrayList<>();
        int vanillaSlabCount = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (!(block instanceof SlabBlock) || !blockId.startsWith("minecraft:")) {
                continue;
            }
            vanillaSlabCount++;
            BlockState bottomState = block.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
            double actual = LandingHitValidationPolicy.shiftedCenterDy(
                    owner, bottomState, ownerDy, Direction.UP, bottomTopHit, heldTorch);
            if (Double.doubleToRawLongBits(actual) != Double.doubleToRawLongBits(ownerDy)) {
                violations.add(blockId + " BOTTOM actual=" + actual + " expected=" + ownerDy);
            }
        }
        if (vanillaSlabCount == 0) {
            violations.add("no registered minecraft SlabBlock states were exercised");
        }

        Vec3 fullHeightTopHit = new Vec3(
                owner.getX() + 0.5d,
                owner.getY() + ownerDy + 1.0d,
                owner.getZ() + 0.5d);
        double topActual = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                ownerDy,
                Direction.UP,
                fullHeightTopHit,
                heldTorch);
        double doubleActual = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE),
                ownerDy,
                Direction.UP,
                fullHeightTopHit,
                heldTorch);
        double bottomEmptyHalf = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                oakBottom,
                ownerDy,
                Direction.UP,
                new Vec3(owner.getX() + 0.5d, owner.getY() + ownerDy + 0.75d,
                        owner.getZ() + 0.5d),
                heldTorch);
        double topEmptyHalf = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                ownerDy,
                Direction.UP,
                new Vec3(owner.getX() + 0.5d, owner.getY() + ownerDy + 0.25d,
                        owner.getZ() + 0.5d),
                heldTorch);
        double outsideXZ = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                oakBottom,
                ownerDy,
                Direction.UP,
                new Vec3(owner.getX() + 1.25d, bottomTopHit.y, owner.getZ() + 0.5d),
                heldTorch);
        double unsupportedHeld = LandingHitValidationPolicy.shiftedCenterDy(
                owner, oakBottom, ownerDy, Direction.UP, bottomTopHit,
                Blocks.AIR.defaultBlockState());

        if (Double.doubleToRawLongBits(topActual) != Double.doubleToRawLongBits(ownerDy)) {
            violations.add("representative TOP actual=" + topActual + " expected=" + ownerDy);
        }
        if (Double.doubleToRawLongBits(doubleActual) != Double.doubleToRawLongBits(ownerDy)) {
            violations.add("representative DOUBLE actual=" + doubleActual + " expected=" + ownerDy);
        }
        if (!Double.isNaN(bottomEmptyHalf)
                || !Double.isNaN(topEmptyHalf)
                || !Double.isNaN(outsideXZ)
                || !Double.isNaN(unsupportedHeld)) {
            violations.add("negative controls widened: bottomEmptyHalf=" + bottomEmptyHalf
                    + " topEmptyHalf=" + topEmptyHalf + " outsideXZ=" + outsideXZ
                    + " unsupportedHeld=" + unsupportedHeld);
        }
        if (!violations.isEmpty()) {
            throw h.assertionException(owner, "TEST 26 slab-owner shape matrix failed across "
                    + vanillaSlabCount + " vanilla slab blocks:\n  "
                    + String.join("\n  ", violations));
        }
        h.succeed();
    }

    /**
     * TEST 21/24: target-owned use validates against an interactive target's translated owner cell
     * with either an empty hand or the held item vanilla needs for the target interaction.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deepUseServerValidationAcceptsLoweredInteractiveTarget(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(3, 4, 3));
        double ownerDy = -1.5d;
        Vec3 packetLikeHit = new Vec3(
                owner.getX() + 0.5d,
                owner.getY() + ownerDy + 0.5d,
                owner.getZ() + 0.5d);

        double shiftedDy = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                Blocks.CHEST.defaultBlockState(),
                ownerDy,
                Direction.UP,
                packetLikeHit,
                null,
                true);

        if (Double.doubleToRawLongBits(shiftedDy) != Double.doubleToRawLongBits(ownerDy)) {
            throw h.assertionException(owner, "TEST 21: lowered interactive target use must shift the server "
                    + "validation center by exact dy=-1.5; actual=" + shiftedDy + " expected=" + ownerDy);
        }

        double heldNonBlockActivation = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                Blocks.CHEST.defaultBlockState(),
                ownerDy,
                Direction.UP,
                packetLikeHit,
                null,
                true);
        double heldBlockBedActivation = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                Blocks.BED.red().defaultBlockState(),
                ownerDy,
                Direction.UP,
                packetLikeHit,
                Blocks.STONE.defaultBlockState(),
                false);
        double flowerPotInsertion = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                Blocks.FLOWER_POT.defaultBlockState(),
                ownerDy,
                Direction.UP,
                packetLikeHit,
                Blocks.POPPY.defaultBlockState(),
                false);
        double candleLighting = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                Blocks.CANDLE.defaultBlockState(),
                ownerDy,
                Direction.UP,
                packetLikeHit,
                null,
                true);
        Vec3 outsideTranslatedCell = new Vec3(
                owner.getX() + 1.25d,
                owner.getY() + ownerDy + 0.5d,
                owner.getZ() + 0.5d);
        double outsideOrdinaryUse = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                Blocks.CHEST.defaultBlockState(),
                ownerDy,
                Direction.UP,
                outsideTranslatedCell,
                null,
                true);
        double flatOrdinaryUse = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                Blocks.CHEST.defaultBlockState(),
                0.0d,
                Direction.UP,
                new Vec3(owner.getX() + 0.5d, owner.getY() + 0.5d, owner.getZ() + 0.5d),
                null,
                true);
        List<String> violations = new ArrayList<>();
        if (Double.doubleToRawLongBits(heldNonBlockActivation)
                != Double.doubleToRawLongBits(ownerDy)) {
            violations.add("HELD_NON_BLOCK_ACTIVATION actual=" + heldNonBlockActivation
                    + " expected=" + ownerDy);
        }
        if (Double.doubleToRawLongBits(heldBlockBedActivation)
                != Double.doubleToRawLongBits(ownerDy)) {
            violations.add("HELD_BLOCK_BED_ACTIVATION actual=" + heldBlockBedActivation
                    + " expected=" + ownerDy);
        }
        if (Double.doubleToRawLongBits(flowerPotInsertion)
                != Double.doubleToRawLongBits(ownerDy)) {
            violations.add("FLOWER_POT_INSERTION actual=" + flowerPotInsertion
                    + " expected=" + ownerDy);
        }
        if (Double.doubleToRawLongBits(candleLighting)
                != Double.doubleToRawLongBits(ownerDy)) {
            violations.add("CANDLE_LIGHTING actual=" + candleLighting
                    + " expected=" + ownerDy);
        }
        if (!Double.isNaN(outsideOrdinaryUse)
                || !Double.isNaN(flatOrdinaryUse)) {
            violations.add("VANILLA_BOUNDARY outside=" + outsideOrdinaryUse + " flat=" + flatOrdinaryUse);
        }
        if (!violations.isEmpty()) {
            throw h.assertionException(owner, "TEST 24: translated held-item target use failed:\n  "
                    + String.join("\n  ", violations));
        }
        h.succeed();
    }

    /**
     * TEST 28: held-block use shifts only stateful OBJECT targets, not the whole placement family.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deepHeldStatefulObjectUseValidationMatrix(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(3, 4, 3));
        double ownerDy = -1.5d;
        Vec3 inside = new Vec3(
                owner.getX() + 0.5d,
                owner.getY() + ownerDy + 0.5d,
                owner.getZ() + 0.5d);
        Vec3 outside = new Vec3(
                owner.getX() + 1.25d,
                owner.getY() + ownerDy + 0.5d,
                owner.getZ() + 0.5d);
        BlockState heldFlower = Blocks.CORNFLOWER.defaultBlockState();

        BlockState[] statefulTargets = {
                Blocks.OAK_FENCE_GATE.defaultBlockState(),
                Blocks.LEVER.defaultBlockState(),
                Blocks.OAK_TRAPDOOR.defaultBlockState(),
                Blocks.STONE_BUTTON.defaultBlockState()
        };
        List<String> violations = new ArrayList<>();
        for (BlockState target : statefulTargets) {
            LandingResolver.Family family = LandingResolver.classify(target);
            double actual = LandingHitValidationPolicy.shiftedCenterDy(
                    owner, target, ownerDy, Direction.UP, inside, heldFlower, false);
            if (family != LandingResolver.Family.OBJECT
                    || (!target.hasProperty(BlockStateProperties.OPEN)
                    && !target.hasProperty(BlockStateProperties.POWERED))
                    || Double.doubleToRawLongBits(actual)
                    != Double.doubleToRawLongBits(ownerDy)) {
                violations.add("stateful target=" + target + " family=" + family
                        + " actual=" + actual + " expected=" + ownerDy);
            }
        }

        BlockState pressurePlate = Blocks.OAK_PRESSURE_PLATE.defaultBlockState();
        LandingResolver.Family pressurePlateFamily = LandingResolver.classify(pressurePlate);
        double pressurePlateActual = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                pressurePlate,
                ownerDy,
                Direction.UP,
                inside,
                Blocks.OAK_PLANKS.defaultBlockState(),
                false);
        if (pressurePlateFamily != LandingResolver.Family.OBJECT
                || !pressurePlate.hasProperty(BlockStateProperties.POWERED)
                || !Double.isNaN(pressurePlateActual)) {
            violations.add("PRESSURE_PLATE_NON_TARGET_USE family=" + pressurePlateFamily
                    + " powered=" + pressurePlate.hasProperty(BlockStateProperties.POWERED)
                    + " actual=" + pressurePlateActual + " expected=NaN");
        }

        BlockState poweredRail = Blocks.POWERED_RAIL.defaultBlockState();
        LandingResolver.Family poweredRailFamily = LandingResolver.classify(poweredRail);
        double poweredRailActual = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                poweredRail,
                ownerDy,
                Direction.UP,
                inside,
                Blocks.OAK_PLANKS.defaultBlockState(),
                false);
        if (poweredRailFamily != LandingResolver.Family.OBJECT
                || !poweredRail.hasProperty(BlockStateProperties.POWERED)
                || !Double.isNaN(poweredRailActual)) {
            violations.add("POWERED_RAIL_NON_TARGET_USE family=" + poweredRailFamily
                    + " powered=" + poweredRail.hasProperty(BlockStateProperties.POWERED)
                    + " actual=" + poweredRailActual + " expected=NaN");
        }

        try {
            java.lang.reflect.Method classifier = LandingHitValidationPolicy.class.getDeclaredMethod(
                    "declaresDirectNoItemUseOverride", Class.class);
            classifier.setAccessible(true);
            boolean descriptorDecoyActual = (boolean) classifier.invoke(null, DescriptorDecoy.class);
            if (descriptorDecoyActual) {
                violations.add("DESCRIPTOR_DECOY_CLASSIFIER actual=true expected=false");
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            violations.add("DESCRIPTOR_DECOY_WRONG_RED reflection="
                    + exception.getClass().getSimpleName());
        }

        double fenceWithHeldDoor = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                Blocks.OAK_FENCE.defaultBlockState(),
                ownerDy,
                Direction.UP,
                inside,
                Blocks.OAK_DOOR.defaultBlockState(),
                false);
        double flatGate = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                Blocks.OAK_FENCE_GATE.defaultBlockState(),
                0.0d,
                Direction.UP,
                new Vec3(owner.getX() + 0.5d, owner.getY() + 0.5d, owner.getZ() + 0.5d),
                heldFlower,
                false);
        double outsideLever = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                Blocks.LEVER.defaultBlockState(),
                ownerDy,
                Direction.UP,
                outside,
                heldFlower,
                false);
        if (!Double.isNaN(fenceWithHeldDoor)
                || !Double.isNaN(flatGate)
                || !Double.isNaN(outsideLever)) {
            violations.add("vanilla boundary fence=" + fenceWithHeldDoor + " flatGate="
                    + flatGate + " outsideLever=" + outsideLever);
        }

        if (!violations.isEmpty()) {
            throw h.assertionException(owner, "TEST 28 stateful target-use matrix failed:\n  "
                    + String.join("\n  ", violations));
        }
        h.succeed();
    }

    /** TEST 28: a real direct-use state transition must not rewrite a frozen target's height. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deepDirectUseStateTransitionPreservesFrozenDy(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos target = h.absolutePos(new BlockPos(3, 4, 3));
        BlockPos editedNeighbor = target.east();
        double expectedDy = -1.5d;
        w.setBlock(target, Blocks.OAK_FENCE_GATE.defaultBlockState(), 3);
        forceStore(w, target, expectedDy);

        withFrozen(() -> {
            double storedBefore = storedDy(w, target);
            double liveBefore = liveDy(w, target);
            if (Double.doubleToRawLongBits(storedBefore) != Double.doubleToRawLongBits(expectedDy)
                    || Double.doubleToRawLongBits(liveBefore) != Double.doubleToRawLongBits(expectedDy)) {
                throw h.assertionException(target, "premise: fence gate must begin with exact frozen dy=-1.5; "
                        + "stored=" + storedBefore + " live=" + liveBefore);
            }

            Player mock = h.makeMockServerPlayer(GameType.SURVIVAL);
            if (!(mock instanceof ServerPlayer player)) {
                throw h.assertionException(target, "premise: direct-use fixture did not create a ServerPlayer");
            }
            ItemStack heldFlower = new ItemStack(Items.CORNFLOWER);
            player.setItemInHand(InteractionHand.MAIN_HAND, heldFlower);
            Vec3 visibleTargetHit = Vec3.atCenterOf(target).add(0.0d, expectedDy, 0.0d);
            InteractionResult result = player.gameMode.useItemOn(
                    player,
                    w,
                    heldFlower,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(visibleTargetHit, Direction.UP, target, false));
            BlockState toggled = w.getBlockState(target);
            double storedAfterToggle = storedDy(w, target);
            double liveAfterToggle = liveDy(w, target);

            w.setBlock(editedNeighbor, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            double storedAfterNeighbor = storedDy(w, target);
            double liveAfterNeighbor = liveDy(w, target);
            if (result == null
                    || !result.consumesAction()
                    || !toggled.hasProperty(BlockStateProperties.OPEN)
                    || !toggled.getValue(BlockStateProperties.OPEN)
                    || Double.doubleToRawLongBits(storedAfterToggle)
                    != Double.doubleToRawLongBits(expectedDy)
                    || Double.doubleToRawLongBits(liveAfterToggle)
                    != Double.doubleToRawLongBits(expectedDy)
                    || Double.doubleToRawLongBits(storedAfterNeighbor)
                    != Double.doubleToRawLongBits(expectedDy)
                    || Double.doubleToRawLongBits(liveAfterNeighbor)
                    != Double.doubleToRawLongBits(expectedDy)) {
                throw h.assertionException(target, "TEST 28 direct-use state transition must preserve exact "
                        + "dy=-1.5; result=" + result + " state=" + toggled
                        + " storedAfterToggle=" + storedAfterToggle + " liveAfterToggle=" + liveAfterToggle
                        + " storedAfterNeighbor=" + storedAfterNeighbor + " liveAfterNeighbor=" + liveAfterNeighbor);
            }
        });
        h.succeed();
    }

    /** Shared server-policy controls: powder contact is all-face; carpet and unrelated lanes stay narrow. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void deepServerValidationRejectsUnsupportedAndOutOfEnvelopeHits(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(3, 4, 3));
        Vec3 inside = new Vec3(owner.getX() + 0.5d, owner.getY() - 1.45d, owner.getZ() + 1.0d);

        double fullBlockPositive = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.STONE.defaultBlockState(), -2.0d, Direction.SOUTH, inside,
                Blocks.STONE.defaultBlockState());
        if (Double.doubleToRawLongBits(fullBlockPositive) != Double.doubleToRawLongBits(-2.0d)) {
            throw h.assertionException(owner, "C2 full-block control must retain the exact -2.0 center shift");
        }

        double objectPositive = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.STONE.defaultBlockState(), -2.0d, Direction.SOUTH, inside,
                Blocks.FLOWER_POT.defaultBlockState());
        double entityObjectPositive = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.STONE.defaultBlockState(), -2.0d, Direction.SOUTH, inside,
                Blocks.CONDUIT.defaultBlockState());
        double carpetSideHeld = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.STONE.defaultBlockState(), -2.0d, Direction.SOUTH, inside,
                Blocks.MOSS_CARPET.defaultBlockState());
        double powderSnowHeld = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.STONE.defaultBlockState(), -2.0d, Direction.SOUTH, inside,
                Blocks.POWDER_SNOW.defaultBlockState());
        double powderSnowSlabOwner = LandingHitValidationPolicy.shiftedCenterDy(
                owner,
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                -2.0d,
                Direction.SOUTH,
                inside,
                Blocks.POWDER_SNOW.defaultBlockState());
        double flatOwner = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.STONE.defaultBlockState(), 0.0d, Direction.SOUTH, inside,
                Blocks.STONE_SLAB.defaultBlockState());
        double partialOwner = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.STONE_SLAB.defaultBlockState(), -2.0d, Direction.SOUTH, inside,
                Blocks.STONE_SLAB.defaultBlockState());
        double unsupportedPowderOwner = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.MOSS_CARPET.defaultBlockState(), -2.0d, Direction.SOUTH, inside,
                Blocks.POWDER_SNOW.defaultBlockState());
        Vec3 outside = new Vec3(owner.getX() + 1.25d, owner.getY() - 1.45d, owner.getZ() + 1.0d);
        double outsideEnvelope = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.STONE.defaultBlockState(), -2.0d, Direction.SOUTH, outside,
                Blocks.POWDER_SNOW.defaultBlockState());

        if (Double.doubleToRawLongBits(objectPositive) != Double.doubleToRawLongBits(-2.0d)
                || Double.doubleToRawLongBits(entityObjectPositive) != Double.doubleToRawLongBits(-2.0d)
                || !Double.isNaN(carpetSideHeld)
                || Double.doubleToRawLongBits(powderSnowHeld) != Double.doubleToRawLongBits(-2.0d)
                || Double.doubleToRawLongBits(powderSnowSlabOwner) != Double.doubleToRawLongBits(-2.0d)
                || !Double.isNaN(flatOwner)
                || !Double.isNaN(partialOwner)
                || !Double.isNaN(unsupportedPowderOwner)
                || !Double.isNaN(outsideEnvelope)) {
            throw h.assertionException(owner, "server validation policy boundary failed: object="
                    + objectPositive + " entityObject=" + entityObjectPositive + " unsupported="
                    + carpetSideHeld + " powderSnow=" + powderSnowHeld + " flatOwner=" + flatOwner
                    + " powderSnowSlabOwner=" + powderSnowSlabOwner + " partialOwner=" + partialOwner
                    + " unsupportedPowderOwner=" + unsupportedPowderOwner
                    + " outsideEnvelope=" + outsideEnvelope);
        }
        h.succeed();
    }
}
