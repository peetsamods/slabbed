package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
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
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

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

    // ── real-useOn placement (matches NeighborUpdateInvarianceTest / DeepCompoundTowerLawTest) ──
    private static void place(GameTestHelper h, Item item, BlockPos clicked, Direction face, double yNudge) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked)
                .add(face.getStepX() * 0.5, face.getStepY() * 0.5 + yNudge, face.getStepZ() * 0.5);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
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
        h.succeed();
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
                    + " (TODAY FOOT -0.5 on half-formed bed, HEAD NaN never captured). Flipped green by C3.");
        }
        h.succeed();
    }

    // ══════════════════════════════ C4 family — objects ══════════════════════════════

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

    // ══════════════════════════════ C5 family — thin layers / powder snow ══════════════════════════════

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
}
