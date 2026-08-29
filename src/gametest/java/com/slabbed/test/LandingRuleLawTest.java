package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.placement.LandingHitValidationPolicy;
import com.slabbed.placement.LandingResolver;
import com.slabbed.util.SlabEnsembleCoherence;
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
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
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

    /** Returns the largest Y depth shared by boxes that overlap strictly in all three axes. */
    private static double strictPositiveAabbOverlapDepthY(VoxelShape first, VoxelShape second) {
        double deepest = 0.0d;
        for (AABB firstBox : first.toAabbs()) {
            for (AABB secondBox : second.toAabbs()) {
                double xDepth = Math.min(firstBox.maxX, secondBox.maxX) - Math.max(firstBox.minX, secondBox.minX);
                double yDepth = Math.min(firstBox.maxY, secondBox.maxY) - Math.max(firstBox.minY, secondBox.minY);
                double zDepth = Math.min(firstBox.maxZ, secondBox.maxZ) - Math.max(firstBox.minZ, secondBox.minZ);
                if (xDepth > EPS && yDepth > EPS && zDepth > EPS) {
                    deepest = Math.max(deepest, yDepth);
                }
            }
        }
        return deepest;
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
                            EmptyBlockGetter.INSTANCE, aim, owner.above(), held,
                            LandingResolver.Family.PAIRED_FLOOR_SEAT);
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
                    EmptyBlockGetter.INSTANCE, aim, owner.above(), object, family);
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
        forceStore(world, owner, -1.0d);
        BlockPos pot = owner.above();
        long expectedBits = Double.doubleToRawLongBits(-1.5d);
        long supportBits = Double.doubleToRawLongBits(-1.0d);

        withFrozen(() -> {
            double supportStored = storedDy(world, owner);
            double supportLive = liveDy(world, owner);
            if (Double.doubleToRawLongBits(supportStored) != supportBits
                    || Double.doubleToRawLongBits(supportLive) != supportBits) {
                throw h.assertionException(owner, "premise: marked side-lower slab must have exact dy=-1.0; stored="
                        + supportStored + " live=" + supportLive);
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
                LandingResolver.resolve(EmptyBlockGetter.INSTANCE, upAim, owner.above(), carpet, carpetFamily);
        LandingResolver.PlacementResolution carpetSide =
                LandingResolver.resolve(EmptyBlockGetter.INSTANCE, sideAim, owner.south(), carpet, carpetFamily);
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
                LandingResolver.resolve(EmptyBlockGetter.INSTANCE, upAim, owner.above(), powder, powderFamily);
        LandingResolver.PlacementResolution powderSide =
                LandingResolver.resolve(EmptyBlockGetter.INSTANCE, sideAim, owner.south(), powder, powderFamily);
        LandingResolver.PlacementResolution powderReplacement =
                LandingResolver.resolve(EmptyBlockGetter.INSTANCE, replacementAim, owner, powder, powderFamily);
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
                        LandingResolver.resolve(EmptyBlockGetter.INSTANCE, upAim, owner.above(), state, family);
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

    /** TEST 29: a translated held-slab body must not steal a lowered target's occupied visible lane. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void test29HeldSlabTranslatedOccupancyIsRefused(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        double targetDy = -1.5d;
        double supportDy = -1.0d;
        double placementDy = -2.5d;
        BlockState heldSlabState = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        List<String> violations = new ArrayList<>();

        // Target-use control: an exact visible hit still belongs to the lowered trapdoor, not the held slab.
        BlockPos controlTarget = h.absolutePos(new BlockPos(8, 3, 3));
        BlockState controlState = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.HALF, Half.BOTTOM)
                .setValue(BlockStateProperties.OPEN, false)
                .setValue(BlockStateProperties.POWERED, false);
        w.setBlock(controlTarget, controlState, 3);
        w.setBlock(controlTarget.above(), Blocks.AIR.defaultBlockState(), 3);
        forceStore(w, controlTarget, targetDy);

        withFrozen(() -> {
            if (Double.doubleToRawLongBits(storedDy(w, controlTarget)) != Double.doubleToRawLongBits(targetDy)
                    || Double.doubleToRawLongBits(liveDy(w, controlTarget)) != Double.doubleToRawLongBits(targetDy)) {
                throw h.assertionException(controlTarget, "wrong-red premise: control trapdoor must begin at exact "
                        + "stored/live dy=-1.5; stored=" + storedDy(w, controlTarget)
                        + " live=" + liveDy(w, controlTarget));
            }

            Player mock = h.makeMockServerPlayer(GameType.SURVIVAL);
            if (!(mock instanceof ServerPlayer player)) {
                throw h.assertionException(controlTarget, "wrong-red premise: control did not create a ServerPlayer");
            }
            ItemStack held = new ItemStack(Items.OAK_SLAB);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            int heldBefore = held.getCount();
            Vec3 controlHit = new Vec3(
                    controlTarget.getX() + 0.600729d,
                    controlTarget.getY() - 1.312500d,
                    controlTarget.getZ() + 0.566410d);
            InteractionResult controlResult = player.gameMode.useItemOn(
                    player,
                    w,
                    held,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(controlHit, Direction.UP, controlTarget, false));
            BlockState controlAfter = w.getBlockState(controlTarget);
            if (controlResult == null
                    || !controlResult.consumesAction()
                    || !controlAfter.hasProperty(BlockStateProperties.OPEN)
                    || !controlAfter.getValue(BlockStateProperties.OPEN)
                    || held.getCount() != heldBefore
                    || player.getItemInHand(InteractionHand.MAIN_HAND).getCount() != heldBefore
                    || !w.getBlockState(controlTarget.above()).isAir()
                    || Double.doubleToRawLongBits(storedDy(w, controlTarget))
                    != Double.doubleToRawLongBits(targetDy)
                    || Double.doubleToRawLongBits(liveDy(w, controlTarget))
                    != Double.doubleToRawLongBits(targetDy)) {
                throw h.assertionException(controlTarget, "wrong-red control: lowered trapdoor use must consume, open, "
                        + "retain the held slab and exact dy=-1.5, and place nothing; result=" + controlResult
                        + " state=" + controlAfter + " held=" + held.getCount()
                        + " stored=" + storedDy(w, controlTarget) + " live=" + liveDy(w, controlTarget));
            }
        });

        BlockPos gateSupport = h.absolutePos(new BlockPos(3, 3, 3));
        BlockPos gateTarget = gateSupport.above();
        BlockPos gatePlacement = gateSupport.above(2);
        BlockState gateState = Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(BlockStateProperties.OPEN, false)
                .setValue(BlockStateProperties.POWERED, false);
        BlockPos trapdoorSupport = h.absolutePos(new BlockPos(3, 3, 7));
        BlockPos trapdoorTarget = trapdoorSupport.above();
        BlockPos trapdoorPlacement = trapdoorSupport.above(2);
        BlockState trapdoorState = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.HALF, Half.BOTTOM)
                .setValue(BlockStateProperties.OPEN, false)
                .setValue(BlockStateProperties.POWERED, false);

        BlockPos[] supports = {gateSupport, trapdoorSupport};
        BlockPos[] targets = {gateTarget, trapdoorTarget};
        BlockPos[] placements = {gatePlacement, trapdoorPlacement};
        BlockState[] targetStates = {gateState, trapdoorState};
        Vec3[] placementHits = {
                new Vec3(gateSupport.getX() + 0.993312d, gateSupport.getY() - 0.500000d,
                        gateSupport.getZ() + 0.360199d),
                new Vec3(trapdoorSupport.getX() + 0.600505d, trapdoorSupport.getY() - 0.500000d,
                        trapdoorSupport.getZ() + 0.529366d)
        };
        String[] fixtureNames = {"gate", "trapdoor"};

        withFrozen(() -> {
            for (int i = 0; i < supports.length; i++) {
                BlockPos support = supports[i];
                BlockPos target = targets[i];
                BlockPos placement = placements[i];
                BlockState targetState = targetStates[i];
                w.setBlock(support, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 3);
                w.setBlock(target, targetState, 3);
                w.setBlock(placement, Blocks.AIR.defaultBlockState(), 3);
                forceStore(w, support, supportDy);
                forceStore(w, target, targetDy);

                double storedSupport = storedDy(w, support);
                double liveSupport = liveDy(w, support);
                double storedTarget = storedDy(w, target);
                double liveTarget = liveDy(w, target);
                SlabAnchorAttachment.PlacementDyFact beforePlacementFact =
                        SlabAnchorAttachment.rawPlacementDyFact(w, placement);
                if (Double.doubleToRawLongBits(storedSupport) != Double.doubleToRawLongBits(supportDy)
                        || Double.doubleToRawLongBits(liveSupport) != Double.doubleToRawLongBits(supportDy)
                        || Double.doubleToRawLongBits(storedTarget) != Double.doubleToRawLongBits(targetDy)
                        || Double.doubleToRawLongBits(liveTarget) != Double.doubleToRawLongBits(targetDy)
                        || !w.getBlockState(placement).isAir()
                        || beforePlacementFact.present()) {
                    throw h.assertionException(target, "wrong-red premise: " + fixtureNames[i]
                            + " fixture must start with support exact dy=-1.0, target exact dy=-1.5, and empty P; "
                            + "support=[" + storedSupport + "," + liveSupport + "] target=[" + storedTarget + ","
                            + liveTarget + "] P=" + w.getBlockState(placement) + " PFact=" + beforePlacementFact.present());
                }

                // UNLOWERED reads: this premise applies targetDy/placementDy itself, exactly as the
                // production gate does. Since 2026-08-28 the ordinary getCollisionShape already returns
                // the shape at its visual position, so reading it here would offset twice and the two
                // volumes would no longer meet. Mirrors SlabSupport#unloweredCollisionShape at the gate.
                VoxelShape translatedTargetShape = SlabSupport.unloweredCollisionShape(targetState, w, target)
                        .move(target.getX(), target.getY() + targetDy, target.getZ());
                VoxelShape translatedPlacementShape = SlabSupport.unloweredCollisionShape(heldSlabState, w, placement)
                        .move(placement.getX(), placement.getY() + placementDy, placement.getZ());
                if (!Shapes.joinIsNotEmpty(translatedTargetShape, translatedPlacementShape, BooleanOp.AND)) {
                    throw h.assertionException(target, "wrong-red premise: " + fixtureNames[i]
                            + " target shape at dy=-1.5 must intersect candidate bottom oak slab at P dy=-2.5");
                }

                Player mock = h.makeMockServerPlayer(GameType.SURVIVAL);
                if (!(mock instanceof ServerPlayer player)) {
                    throw h.assertionException(target, "wrong-red premise: " + fixtureNames[i]
                            + " fixture did not create a ServerPlayer");
                }
                ItemStack held = new ItemStack(Items.OAK_SLAB);
                player.setItemInHand(InteractionHand.MAIN_HAND, held);
                int heldBefore = held.getCount();
                player.gameMode.useItemOn(
                        player,
                        w,
                        held,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(placementHits[i], Direction.UP, support, false));

                BlockState placed = w.getBlockState(placement);
                BlockState targetAfter = w.getBlockState(target);
                double storedTargetAfter = storedDy(w, target);
                double liveTargetAfter = liveDy(w, target);
                double storedPlacementAfter = storedDy(w, placement);
                double livePlacementAfter = liveDy(w, placement);
                SlabAnchorAttachment.PlacementDyFact placementFact =
                        SlabAnchorAttachment.rawPlacementDyFact(w, placement);
                if (!placed.isAir()
                        || held.getCount() != heldBefore
                        || player.getItemInHand(InteractionHand.MAIN_HAND).getCount() != heldBefore
                        || placementFact.present()
                        || !targetAfter.equals(targetState)
                        || Double.doubleToRawLongBits(storedTargetAfter) != Double.doubleToRawLongBits(targetDy)
                        || Double.doubleToRawLongBits(liveTargetAfter) != Double.doubleToRawLongBits(targetDy)) {
                    violations.add("OCCUPANCY_THEFT_" + fixtureNames[i].toUpperCase()
                            + " P must remain air and factless; actual=" + placed
                            + " held=" + held.getCount()
                            + " PStored=" + storedPlacementAfter + " PLive=" + livePlacementAfter
                            + " PFact=" + (placementFact.present()
                            ? Double.longBitsToDouble(placementFact.rawBits()) : "absent")
                            + " target=" + targetAfter + " targetStored=" + storedTargetAfter
                            + " targetLive=" + liveTargetAfter);
                }
            }
        });

        if (!violations.isEmpty()) {
            throw h.assertionException(gatePlacement, "TEST 29 translated placement occupancy theft:\n  "
                    + String.join("\n  ", violations));
        }
        h.succeed();
    }

    /**
     * TEST 30: a second lowered trapdoor must be refused when its legal OPEN state would strictly
     * overlap the existing lowered trapdoor, even though their CLOSED bodies are disjoint.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void test30OpenTransitionOccupancyIsRefused(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        double upperDy = -2.0d;
        double lowerDy = -1.5d;
        double supportDy = -1.0d;
        BlockPos upper = h.absolutePos(new BlockPos(3, 5, 3));
        BlockPos lower = upper.below();
        BlockPos support = lower.below();
        BlockState trapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.HALF, Half.BOTTOM)
                .setValue(BlockStateProperties.OPEN, false)
                .setValue(BlockStateProperties.POWERED, false);
        BlockState openTrapdoor = trapdoor.setValue(BlockStateProperties.OPEN, true);

        // Separate equal-dy control: canonical OPEN envelopes only face-contact, so placement is legal.
        double controlDy = -1.5d;
        BlockPos controlUpper = h.absolutePos(new BlockPos(8, 5, 3));
        BlockPos controlLower = controlUpper.below();
        BlockPos controlSupport = controlLower.below();

        w.setBlock(support, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 3);
        w.setBlock(upper, trapdoor, 3);
        w.setBlock(lower, Blocks.AIR.defaultBlockState(), 3);
        forceStore(w, support, supportDy);
        forceStore(w, upper, upperDy);
        w.setBlock(controlSupport,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 3);
        w.setBlock(controlUpper, trapdoor, 3);
        w.setBlock(controlLower, Blocks.AIR.defaultBlockState(), 3);
        forceStore(w, controlSupport, supportDy);
        forceStore(w, controlUpper, controlDy);

        withFrozen(() -> {
            double storedSupport = storedDy(w, support);
            double liveSupport = liveDy(w, support);
            double storedUpper = storedDy(w, upper);
            double liveUpper = liveDy(w, upper);
            SlabAnchorAttachment.PlacementDyFact lowerFactBefore =
                    SlabAnchorAttachment.rawPlacementDyFact(w, lower);
            if (Double.doubleToRawLongBits(storedSupport) != Double.doubleToRawLongBits(supportDy)
                    || Double.doubleToRawLongBits(liveSupport) != Double.doubleToRawLongBits(supportDy)
                    || Double.doubleToRawLongBits(storedUpper) != Double.doubleToRawLongBits(upperDy)
                    || Double.doubleToRawLongBits(liveUpper) != Double.doubleToRawLongBits(upperDy)
                    || !w.getBlockState(lower).isAir()
                    || lowerFactBefore.present()) {
                throw h.assertionException(lower, "wrong-red premise: support must be exact -1.0, upper exact -2.0, "
                        + "and lower air/factless; support=[" + storedSupport + "," + liveSupport + "] upper=["
                        + storedUpper + "," + liveUpper + "] lower=" + w.getBlockState(lower)
                        + " lowerFact=" + lowerFactBefore.present());
            }

            VoxelShape upperClosed = trapdoor.getCollisionShape(
                            EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
                    .move(upper.getX(), upper.getY() + upperDy, upper.getZ());
            VoxelShape lowerClosed = trapdoor.getCollisionShape(
                            EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
                    .move(lower.getX(), lower.getY() + lowerDy, lower.getZ());
            VoxelShape upperOpen = openTrapdoor.getCollisionShape(
                            EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
                    .move(upper.getX(), upper.getY() + upperDy, upper.getZ());
            VoxelShape lowerOpen = openTrapdoor.getCollisionShape(
                            EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
                    .move(lower.getX(), lower.getY() + lowerDy, lower.getZ());
            double closedDepth = strictPositiveAabbOverlapDepthY(upperClosed, lowerClosed);
            double futureOpenDepth = strictPositiveAabbOverlapDepthY(
                    Shapes.or(upperClosed, upperOpen), Shapes.or(lowerClosed, lowerOpen));
            double dyContactGapDepth = lowerDy - upperDy;
            VoxelShape equalDyUpperOpen = openTrapdoor.getCollisionShape(
                            EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
                    .move(upper.getX(), upper.getY() + lowerDy, upper.getZ());
            double equalDyOpenDepth = strictPositiveAabbOverlapDepthY(equalDyUpperOpen, lowerOpen);
            if (closedDepth > EPS
                    || futureOpenDepth <= EPS
                    || Math.abs(dyContactGapDepth - 0.5d) > EPS
                    || equalDyOpenDepth > EPS) {
                throw h.assertionException(lower, "wrong-red premise: closed depth must be 0, future OPEN-envelope "
                        + "depth positive, dy contact gap exactly 0.5, and equal-dy open control only face-contact; "
                        + "closed=" + closedDepth + " futureOpen=" + futureOpenDepth + " dyGap=" + dyContactGapDepth
                        + " equalDyOpen=" + equalDyOpenDepth);
            }

            double storedControlSupport = storedDy(w, controlSupport);
            double liveControlSupport = liveDy(w, controlSupport);
            double storedControlUpper = storedDy(w, controlUpper);
            double liveControlUpper = liveDy(w, controlUpper);
            SlabAnchorAttachment.PlacementDyFact controlLowerFactBefore =
                    SlabAnchorAttachment.rawPlacementDyFact(w, controlLower);
            VoxelShape controlUpperOpen = openTrapdoor.getCollisionShape(
                            EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
                    .move(controlUpper.getX(), controlUpper.getY() + controlDy, controlUpper.getZ());
            VoxelShape controlLowerOpen = openTrapdoor.getCollisionShape(
                            EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
                    .move(controlLower.getX(), controlLower.getY() + controlDy, controlLower.getZ());
            double controlOpenDepth = strictPositiveAabbOverlapDepthY(controlUpperOpen, controlLowerOpen);
            if (Double.doubleToRawLongBits(storedControlSupport) != Double.doubleToRawLongBits(supportDy)
                    || Double.doubleToRawLongBits(liveControlSupport) != Double.doubleToRawLongBits(supportDy)
                    || Double.doubleToRawLongBits(storedControlUpper) != Double.doubleToRawLongBits(controlDy)
                    || Double.doubleToRawLongBits(liveControlUpper) != Double.doubleToRawLongBits(controlDy)
                    || !w.getBlockState(controlLower).isAir()
                    || controlLowerFactBefore.present()
                    || controlOpenDepth > EPS) {
                throw h.assertionException(controlLower, "wrong-green premise: equal-dy control must start with "
                        + "support exact -1.0, upper exact -1.5, lower air/factless, and OPEN face-contact only; "
                        + "support=[" + storedControlSupport + "," + liveControlSupport + "] upper=["
                        + storedControlUpper + "," + liveControlUpper + "] lower="
                        + w.getBlockState(controlLower) + " lowerFact=" + controlLowerFactBefore.present()
                        + " openDepth=" + controlOpenDepth);
            }

            Player controlMock = h.makeMockServerPlayer(GameType.SURVIVAL);
            if (!(controlMock instanceof ServerPlayer controlPlayer)) {
                throw h.assertionException(controlLower, "wrong-green premise: equal-dy control did not create a "
                        + "ServerPlayer");
            }
            ItemStack controlHeld = new ItemStack(Items.OAK_TRAPDOOR);
            controlHeld.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY
                    .with(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                    .with(BlockStateProperties.HALF, Half.BOTTOM));
            controlPlayer.setItemInHand(InteractionHand.MAIN_HAND, controlHeld);
            int controlHeldBefore = controlHeld.getCount();
            Vec3 visibleControlSupportTop = new Vec3(controlSupport.getX() + 0.5d,
                    controlSupport.getY() - 0.5d, controlSupport.getZ() + 0.5d);
            InteractionResult controlResult = controlPlayer.gameMode.useItemOn(
                    controlPlayer,
                    w,
                    controlHeld,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(visibleControlSupportTop, Direction.UP, controlSupport, false));
            BlockState controlLowerAfter = w.getBlockState(controlLower);
            BlockState controlUpperAfter = w.getBlockState(controlUpper);
            double storedControlLowerAfter = storedDy(w, controlLower);
            double liveControlLowerAfter = liveDy(w, controlLower);
            double storedControlUpperAfter = storedDy(w, controlUpper);
            double liveControlUpperAfter = liveDy(w, controlUpper);
            SlabAnchorAttachment.PlacementDyFact controlLowerFactAfter =
                    SlabAnchorAttachment.rawPlacementDyFact(w, controlLower);
            if (controlResult == null
                    || !controlResult.consumesAction()
                    || !controlLowerAfter.is(Blocks.OAK_TRAPDOOR)
                    || Double.doubleToRawLongBits(storedControlLowerAfter) != Double.doubleToRawLongBits(controlDy)
                    || Double.doubleToRawLongBits(liveControlLowerAfter) != Double.doubleToRawLongBits(controlDy)
                    || !controlLowerFactAfter.present()
                    || controlHeld.getCount() != controlHeldBefore - 1
                    || controlPlayer.getItemInHand(InteractionHand.MAIN_HAND).getCount() != controlHeldBefore - 1
                    || !controlUpperAfter.equals(trapdoor)
                    || Double.doubleToRawLongBits(storedControlUpperAfter) != Double.doubleToRawLongBits(controlDy)
                    || Double.doubleToRawLongBits(liveControlUpperAfter) != Double.doubleToRawLongBits(controlDy)) {
                throw h.assertionException(controlLower, "TEST 30 equal-dy OPEN face-contact control must place the "
                        + "lower oak trapdoor at exact -1.5, consume one in survival, and preserve the upper; "
                        + "result=" + controlResult + " lower=" + controlLowerAfter + " lowerStored="
                        + storedControlLowerAfter + " lowerLive=" + liveControlLowerAfter + " lowerFact="
                        + (controlLowerFactAfter.present()
                        ? Double.longBitsToDouble(controlLowerFactAfter.rawBits()) : "absent") + " held="
                        + controlHeld.getCount() + " upper=" + controlUpperAfter + " upperStored="
                        + storedControlUpperAfter + " upperLive=" + liveControlUpperAfter);
            }

            Player mock = h.makeMockServerPlayer(GameType.SURVIVAL);
            if (!(mock instanceof ServerPlayer player)) {
                throw h.assertionException(lower, "wrong-red premise: fixture did not create a ServerPlayer");
            }
            ItemStack held = new ItemStack(Items.OAK_TRAPDOOR);
            held.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY
                    .with(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                    .with(BlockStateProperties.HALF, Half.BOTTOM));
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            int heldBefore = held.getCount();
            Vec3 visibleSupportTop = new Vec3(support.getX() + 0.5d, support.getY() - 0.5d, support.getZ() + 0.5d);
            InteractionResult result = player.gameMode.useItemOn(
                    player,
                    w,
                    held,
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(visibleSupportTop, Direction.UP, support, false));

            BlockState lowerAfter = w.getBlockState(lower);
            double storedLowerAfter = storedDy(w, lower);
            double liveLowerAfter = liveDy(w, lower);
            if (!lowerAfter.isAir() && !lowerAfter.is(Blocks.OAK_TRAPDOOR)) {
                throw h.assertionException(lower, "wrong-red premise: visible-support use wrote an unexpected lower "
                        + "block; result=" + result + " lower=" + lowerAfter);
            }
            if (lowerAfter.is(Blocks.OAK_TRAPDOOR)) {
                if (Double.doubleToRawLongBits(storedLowerAfter) != Double.doubleToRawLongBits(lowerDy)
                        || Double.doubleToRawLongBits(liveLowerAfter) != Double.doubleToRawLongBits(lowerDy)) {
                    throw h.assertionException(lower, "wrong-red premise: real lower placement must land at exact -1.5; "
                            + "stored=" + storedLowerAfter + " live=" + liveLowerAfter + " result=" + result);
                }
                throw h.assertionException(lower, "TEST 30 state-transition occupancy: unsafe lower trapdoor was "
                        + "placed through ServerPlayer.gameMode.useItemOn; futureOpenOverlapDepth=" + futureOpenDepth
                        + " (expected 0.3125), result=" + result + " lower=" + lowerAfter);
            }

            SlabAnchorAttachment.PlacementDyFact lowerFactAfter =
                    SlabAnchorAttachment.rawPlacementDyFact(w, lower);
            BlockState upperAfter = w.getBlockState(upper);
            double storedUpperAfter = storedDy(w, upper);
            double liveUpperAfter = liveDy(w, upper);
            if (lowerFactAfter.present()
                    || held.getCount() != heldBefore
                    || player.getItemInHand(InteractionHand.MAIN_HAND).getCount() != heldBefore
                    || !upperAfter.equals(trapdoor)
                    || Double.doubleToRawLongBits(storedUpperAfter) != Double.doubleToRawLongBits(upperDy)
                    || Double.doubleToRawLongBits(liveUpperAfter) != Double.doubleToRawLongBits(upperDy)) {
                throw h.assertionException(lower, "TEST 30 refusal contract failed: lower must remain air/factless, "
                        + "held unchanged, and upper unchanged; lowerFact=" + lowerFactAfter.present()
                        + " held=" + held.getCount() + " upper=" + upperAfter + " upperStored="
                        + storedUpperAfter + " upperLive=" + liveUpperAfter);
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

    /**
     * TEST 32 Gate A: real flint-and-steel must author fire at the visible top-seat of the clicked
     * owner. This is deliberately an aggregate RED: both historical lowered geometries execute
     * before a contact mismatch is reported.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void flintAndSteelFireOnFrozenMinusOneSupportsHasExactTopSeat(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        List<FireContactProbe> probes = new ArrayList<>();

        withFrozen(() -> {
            probes.add(test32UseFlintAndSteel(
                    h, world, "full_stone_minus1", h.absolutePos(new BlockPos(3, 4, 3)),
                    Blocks.STONE.defaultBlockState(), -1.0d, 0.0d, -1.0d));
            probes.add(test32UseFlintAndSteel(
                    h, world, "bottom_stone_slab_minus1", h.absolutePos(new BlockPos(8, 4, 3)),
                    Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                    -1.0d, -0.5d, -1.5d));
            probes.add(test32UseFlintAndSteel(
                    h, world, "flat_full_stone_control", h.absolutePos(new BlockPos(13, 4, 3)),
                    Blocks.STONE.defaultBlockState(), 0.0d, 0.0d, 0.0d));
        });

        List<String> reports = probes.stream().map(FireContactProbe::report).toList();
        FireContactProbe full = probes.get(0);
        FireContactProbe bottomSlab = probes.get(1);
        FireContactProbe flat = probes.get(2);

        if (full.primaryContract && bottomSlab.primaryContract && flat.primaryContract
                && full.matchesCurrentRed && bottomSlab.matchesCurrentRed && flat.matchesCurrentRed) {
            throw h.assertionException(full.created,
                    "TEST32_FIRE_CONTACT_RED: real flint-and-steel created fire above both exact frozen "
                            + "dy=-1.0 owners without a raw fire anchor; each visible fire base is exactly "
                            + "+1.0 above the visible support top; full live=0.0 (want -1.0), bottom-slab "
                            + "live=-0.5 (want -1.5), flat control is flush. observations=" + reports);
        }

        if (full.primaryContract && bottomSlab.primaryContract && flat.primaryContract
                && full.matchesFutureGreen && bottomSlab.matchesFutureGreen && flat.matchesFutureGreen) {
            h.succeed();
            return;
        }

        throw h.assertionException(full.created,
                "TEST32_FIRE_CONTACT_WRONG_RED: Gate A requires either the exact current unanchored +1.0 "
                        + "signature or exact raw/live anchored zero-contact GREEN; observations=" + reports);
    }

    /** TEST 32 Gate C: fire charge uses the same created-fire contact authority as flint and steel. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fireChargeFireOnFrozenMinusOneFullSupportHasExactTopSeat(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        FireContactProbe[] probe = new FireContactProbe[1];
        withFrozen(() -> probe[0] = test32UseFireItem(
                h, world, "fire_charge_full_stone_minus1", Items.FIRE_CHARGE,
                h.absolutePos(new BlockPos(3, 4, 3)), Blocks.STONE.defaultBlockState(),
                -1.0d, 0.0d, -1.0d));
        if (!probe[0].primaryContract || !probe[0].matchesFutureGreen) {
            throw h.assertionException(probe[0].created,
                    "TEST32_FIRE_CHARGE_CONTACT: fire charge must create top-seated anchored fire; "
                            + probe[0].report);
        }
        h.succeed();
    }

    /** TEST 32 Gate C: the generic hook also preserves soul-fire state and its visible top-seat. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void soulFireOnFrozenMinusOneSoulSoilHasExactTopSeat(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        FireContactProbe[] probe = new FireContactProbe[1];
        withFrozen(() -> probe[0] = test32UseFireItem(
                h, world, "soul_fire_full_soul_soil_minus1", Items.FLINT_AND_STEEL,
                h.absolutePos(new BlockPos(3, 4, 3)), Blocks.SOUL_SOIL.defaultBlockState(),
                -1.0d, 0.0d, -1.0d));
        BlockState created = world.getBlockState(probe[0].created);
        if (!probe[0].primaryContract || !probe[0].matchesFutureGreen || !created.is(Blocks.SOUL_FIRE)) {
            throw h.assertionException(probe[0].created,
                    "TEST32_SOUL_FIRE_CONTACT: flint and steel on lowered soul soil must retain soul fire "
                            + "and its exact anchor; created=" + created + " " + probe[0].report);
        }
        h.succeed();
    }

    /** TEST 32 Gate C: an occupied target neither gains nor overwrites a created-fire anchor. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void occupiedOrFailedFireUseDoesNotPublishPlacementDy(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos owner = h.absolutePos(new BlockPos(3, 4, 3));
        BlockPos target = owner.above();
        withFrozen(() -> {
            world.setBlock(owner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            forceStore(world, owner, -1.0d);
            world.setBlock(target, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            SlabAnchorAttachment.PlacementDyFact targetBefore = SlabAnchorAttachment.rawPlacementDyFact(world, target);
            InteractionResult result = test32UseItemOn(h, Items.FLINT_AND_STEEL, owner, Direction.UP,
                    new Vec3(owner.getX() + 0.5d, owner.getY(), owner.getZ() + 0.5d));
            SlabAnchorAttachment.PlacementDyFact targetAfter = SlabAnchorAttachment.rawPlacementDyFact(world, target);
            if (result == null || world.getBlockState(target).getBlock() != Blocks.STONE
                    || !test32SameFact(targetBefore, targetAfter) || targetAfter.present()) {
                throw h.assertionException(target, "TEST32_OCCUPIED_FIRE_USE: rejected occupied target must stay "
                        + "stone with no placement fact; result=" + result + " before=" + test32Fact(targetBefore)
                        + " after=" + test32Fact(targetAfter));
            }

            forceStore(world, target, -7.0d);
            SlabAnchorAttachment.PlacementDyFact sentinelBefore = SlabAnchorAttachment.rawPlacementDyFact(world, target);
            InteractionResult secondResult = test32UseItemOn(h, Items.FLINT_AND_STEEL, owner, Direction.UP,
                    new Vec3(owner.getX() + 0.5d, owner.getY(), owner.getZ() + 0.5d));
            SlabAnchorAttachment.PlacementDyFact sentinelAfter = SlabAnchorAttachment.rawPlacementDyFact(world, target);
            if (secondResult == null || world.getBlockState(target).getBlock() != Blocks.STONE
                    || !test32SameFact(sentinelBefore, sentinelAfter)) {
                throw h.assertionException(target, "TEST32_OCCUPIED_FIRE_USE: rejected target fact was overwritten; "
                        + "result=" + secondResult + " before=" + test32Fact(sentinelBefore)
                        + " after=" + test32Fact(sentinelAfter));
            }
        });
        h.succeed();
    }

    /** TEST 32 Gate C: lighting a candle is not a new-fire placement and publishes no fire anchor. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void lightingExistingCandleDoesNotPublishFireContact(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos owner = h.absolutePos(new BlockPos(3, 4, 3));
        BlockPos candle = owner.above();
        withFrozen(() -> {
            world.setBlock(owner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            forceStore(world, owner, -1.0d);
            world.setBlock(candle, Blocks.CANDLE.defaultBlockState(), Block.UPDATE_ALL);
            InteractionResult result = test32UseItemOn(h, Items.FLINT_AND_STEEL, candle, Direction.UP,
                    new Vec3(candle.getX() + 0.5d, candle.getY() + 0.5d, candle.getZ() + 0.5d));
            BlockState litCandle = world.getBlockState(candle);
            SlabAnchorAttachment.PlacementDyFact candleFact = SlabAnchorAttachment.rawPlacementDyFact(world, candle);
            SlabAnchorAttachment.PlacementDyFact adjacentFact =
                    SlabAnchorAttachment.rawPlacementDyFact(world, candle.above());
            if (result == null || !litCandle.is(Blocks.CANDLE)
                    || !litCandle.getValue(BlockStateProperties.LIT)
                    || candleFact.present() || adjacentFact.present()) {
                throw h.assertionException(candle, "TEST32_CANDLE_LIGHTING: lighting must only mutate the candle; "
                        + "result=" + result + " candle=" + litCandle + " candleFact=" + test32Fact(candleFact)
                        + " adjacentFact=" + test32Fact(adjacentFact));
            }
        });
        h.succeed();
    }

    /** TEST 32 Gate C: horizontal and underside uses cannot inherit the clicked owner's lowered dy. */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sideAndDownFireUsesDoNotStealClickedOwnerDy(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        withFrozen(() -> {
            BlockPos sideOwner = h.absolutePos(new BlockPos(3, 5, 3));
            BlockPos sideTarget = sideOwner.east();
            world.setBlock(sideOwner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            forceStore(world, sideOwner, -1.0d);
            world.setBlock(sideTarget.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            world.setBlock(sideTarget, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            InteractionResult sideResult = test32UseItemOn(h, Items.FLINT_AND_STEEL, sideOwner, Direction.EAST,
                    new Vec3(sideOwner.getX() + 1.0d, sideOwner.getY() - 0.5d, sideOwner.getZ() + 0.5d));
            SlabAnchorAttachment.PlacementDyFact sideFact = SlabAnchorAttachment.rawPlacementDyFact(world, sideTarget);
            if (sideResult == null || !(world.getBlockState(sideTarget).getBlock() instanceof BaseFireBlock)
                    || sideFact.present()) {
                throw h.assertionException(sideTarget, "TEST32_SIDE_FIRE_USE: side-created fire cannot inherit "
                        + "clicked owner dy; result=" + sideResult + " state=" + world.getBlockState(sideTarget)
                        + " fact=" + test32Fact(sideFact));
            }

            BlockPos downOwner = h.absolutePos(new BlockPos(8, 5, 3));
            BlockPos downTarget = downOwner.below();
            world.setBlock(downOwner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            forceStore(world, downOwner, -1.0d);
            world.setBlock(downTarget.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            world.setBlock(downTarget, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            InteractionResult downResult = test32UseItemOn(h, Items.FLINT_AND_STEEL, downOwner, Direction.DOWN,
                    new Vec3(downOwner.getX() + 0.5d, downOwner.getY() - 1.0d, downOwner.getZ() + 0.5d));
            SlabAnchorAttachment.PlacementDyFact downFact = SlabAnchorAttachment.rawPlacementDyFact(world, downTarget);
            if (downResult == null || !(world.getBlockState(downTarget).getBlock() instanceof BaseFireBlock)
                    || downFact.present()) {
                throw h.assertionException(downTarget, "TEST32_DOWN_FIRE_USE: underside-created fire cannot inherit "
                        + "clicked owner dy; result=" + downResult + " state=" + world.getBlockState(downTarget)
                        + " fact=" + test32Fact(downFact));
            }
        });
        h.succeed();
    }

    private record FireContactProbe(
            String name,
            BlockPos created,
            boolean primaryContract,
            boolean matchesCurrentRed,
            boolean matchesFutureGreen,
            String report
    ) {
    }

    private static FireContactProbe test32UseFlintAndSteel(
            GameTestHelper h,
            ServerLevel world,
            String name,
            BlockPos owner,
            BlockState ownerState,
            double ownerDy,
            double expectedCurrentLiveDy,
            double expectedGreenDy
    ) {
        return test32UseFireItem(h, world, name, Items.FLINT_AND_STEEL, owner, ownerState, ownerDy,
                expectedCurrentLiveDy, expectedGreenDy);
    }

    private static FireContactProbe test32UseFireItem(
            GameTestHelper h,
            ServerLevel world,
            String name,
            Item fireItem,
            BlockPos owner,
            BlockState ownerState,
            double ownerDy,
            double expectedCurrentLiveDy,
            double expectedGreenDy
    ) {
        BlockPos created = owner.above();
        BlockPos editedNeighbor = created.east();
        List<String> failures = new ArrayList<>();

        world.setBlock(owner, ownerState, Block.UPDATE_ALL);
        world.setBlock(created, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(editedNeighbor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        if (Double.doubleToRawLongBits(ownerDy) != Double.doubleToRawLongBits(0.0d)) {
            forceStore(world, owner, ownerDy);
        }

        BlockState ownerBefore = world.getBlockState(owner);
        SlabAnchorAttachment.PlacementDyFact ownerFactBefore = SlabAnchorAttachment.rawPlacementDyFact(world, owner);
        double ownerLiveBefore = liveDy(world, owner);
        boolean expectedOwnerFact = Double.doubleToRawLongBits(ownerDy) == Double.doubleToRawLongBits(0.0d)
                ? !ownerFactBefore.present()
                : ownerFactBefore.present()
                && ownerFactBefore.rawBits() == Double.doubleToRawLongBits(ownerDy);
        if (!ownerBefore.equals(ownerState)
                || !expectedOwnerFact
                || Double.doubleToRawLongBits(ownerLiveBefore) != Double.doubleToRawLongBits(ownerDy)) {
            failures.add("owner-premise state=" + ownerBefore + " rawFact=" + test32Fact(ownerFactBefore)
                    + " live=" + ownerLiveBefore + " expectedDy=" + ownerDy);
        }

        VoxelShape ownerShape = ownerBefore.getShape(world, owner, CollisionContext.empty());
        if (ownerShape.isEmpty()) {
            failures.add("owner-shape-empty");
        }
        double supportTopBefore = ownerShape.isEmpty()
                ? Double.NaN
                : owner.getY() + ownerShape.bounds().maxY;
        Vec3 visibleUpHit = new Vec3(owner.getX() + 0.5d, supportTopBefore, owner.getZ() + 0.5d);

        InteractionResult result = null;
        Player mock = h.makeMockServerPlayer(GameType.SURVIVAL);
        if (mock instanceof ServerPlayer player) {
            result = test32UseItemOn(h, fireItem, owner, Direction.UP, visibleUpHit, player);
        } else {
            failures.add("real-use fixture did not create a ServerPlayer");
        }

        BlockState ownerAfterUse = world.getBlockState(owner);
        SlabAnchorAttachment.PlacementDyFact ownerFactAfterUse = SlabAnchorAttachment.rawPlacementDyFact(world, owner);
        double ownerLiveAfterUse = liveDy(world, owner);
        BlockState fireStateBeforeNeighbor = world.getBlockState(created);
        SlabAnchorAttachment.PlacementDyFact fireFactBeforeNeighbor =
                SlabAnchorAttachment.rawPlacementDyFact(world, created);
        double fireStoredBeforeNeighbor = storedDy(world, created);
        double fireLiveBeforeNeighbor = liveDy(world, created);
        VoxelShape fireShapeBeforeNeighbor = fireStateBeforeNeighbor.getShape(world, created, CollisionContext.empty());
        double fireBaseBeforeNeighbor = fireShapeBeforeNeighbor.isEmpty()
                ? Double.NaN
                : created.getY() + fireShapeBeforeNeighbor.bounds().minY;
        double seatErrorBeforeNeighbor = fireBaseBeforeNeighbor - supportTopBefore;

        if (result == null || !result.consumesAction()) {
            failures.add("use-result=" + result);
        }
        if (!ownerAfterUse.equals(ownerBefore)
                || !test32SameFact(ownerFactBefore, ownerFactAfterUse)
                || Double.doubleToRawLongBits(ownerLiveAfterUse) != Double.doubleToRawLongBits(ownerLiveBefore)) {
            failures.add("clicked-owner-mutated state=" + ownerAfterUse + " rawFact="
                    + test32Fact(ownerFactAfterUse) + " live=" + ownerLiveAfterUse);
        }
        if (!(fireStateBeforeNeighbor.getBlock() instanceof BaseFireBlock)) {
            failures.add("created-cell=" + created.toShortString() + " state=" + fireStateBeforeNeighbor);
        }
        if (!Double.isFinite(fireLiveBeforeNeighbor) || fireShapeBeforeNeighbor.isEmpty()
                || !Double.isFinite(fireBaseBeforeNeighbor) || !Double.isFinite(seatErrorBeforeNeighbor)) {
            failures.add("fire-contact-unreadable rawFact=" + test32Fact(fireFactBeforeNeighbor)
                    + " stored=" + fireStoredBeforeNeighbor + " live=" + fireLiveBeforeNeighbor
                    + " shape=" + fireShapeBeforeNeighbor + " base=" + fireBaseBeforeNeighbor
                    + " supportTop=" + supportTopBefore + " error=" + seatErrorBeforeNeighbor);
        }

        world.setBlock(editedNeighbor, Blocks.GLASS.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(editedNeighbor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        BlockState ownerAfterNeighbor = world.getBlockState(owner);
        SlabAnchorAttachment.PlacementDyFact ownerFactAfterNeighbor =
                SlabAnchorAttachment.rawPlacementDyFact(world, owner);
        double ownerLiveAfterNeighbor = liveDy(world, owner);
        BlockState fireStateAfterNeighbor = world.getBlockState(created);
        SlabAnchorAttachment.PlacementDyFact fireFactAfterNeighbor =
                SlabAnchorAttachment.rawPlacementDyFact(world, created);
        double fireStoredAfterNeighbor = storedDy(world, created);
        double fireLiveAfterNeighbor = liveDy(world, created);
        VoxelShape ownerShapeAfterNeighbor = ownerAfterNeighbor.getShape(world, owner, CollisionContext.empty());
        VoxelShape fireShapeAfterNeighbor = fireStateAfterNeighbor.getShape(world, created, CollisionContext.empty());
        double supportTopAfterNeighbor = ownerShapeAfterNeighbor.isEmpty()
                ? Double.NaN
                : owner.getY() + ownerShapeAfterNeighbor.bounds().maxY;
        double fireBaseAfterNeighbor = fireShapeAfterNeighbor.isEmpty()
                ? Double.NaN
                : created.getY() + fireShapeAfterNeighbor.bounds().minY;
        double seatErrorAfterNeighbor = fireBaseAfterNeighbor - supportTopAfterNeighbor;

        if (!ownerAfterNeighbor.equals(ownerBefore)
                || !test32SameFact(ownerFactBefore, ownerFactAfterNeighbor)
                || Double.doubleToRawLongBits(ownerLiveAfterNeighbor) != Double.doubleToRawLongBits(ownerLiveBefore)) {
            failures.add("neighbor-edit changed owner state=" + ownerAfterNeighbor + " rawFact="
                    + test32Fact(ownerFactAfterNeighbor) + " live=" + ownerLiveAfterNeighbor);
        }
        if (!(fireStateAfterNeighbor.getBlock() instanceof BaseFireBlock)
                || !test32SameFact(fireFactBeforeNeighbor, fireFactAfterNeighbor)
                || Double.doubleToRawLongBits(fireStoredAfterNeighbor)
                != Double.doubleToRawLongBits(fireStoredBeforeNeighbor)
                || Double.doubleToRawLongBits(fireLiveAfterNeighbor)
                != Double.doubleToRawLongBits(fireLiveBeforeNeighbor)
                || Double.doubleToRawLongBits(supportTopAfterNeighbor)
                != Double.doubleToRawLongBits(supportTopBefore)
                || Double.doubleToRawLongBits(fireBaseAfterNeighbor)
                != Double.doubleToRawLongBits(fireBaseBeforeNeighbor)
                || Double.doubleToRawLongBits(seatErrorAfterNeighbor)
                != Double.doubleToRawLongBits(seatErrorBeforeNeighbor)) {
            failures.add("neighbor-edit changed fire rawFact=" + test32Fact(fireFactAfterNeighbor)
                    + " stored=" + fireStoredAfterNeighbor + " live=" + fireLiveAfterNeighbor
                    + " supportTop=" + supportTopAfterNeighbor + " fireBase=" + fireBaseAfterNeighbor
                    + " error=" + seatErrorAfterNeighbor);
        }

        boolean primaryContract = failures.isEmpty();
        boolean lowered = Double.doubleToRawLongBits(ownerDy) == Double.doubleToRawLongBits(-1.0d);
        boolean matchesCurrentRed = primaryContract
                && !fireFactBeforeNeighbor.present()
                && Double.isNaN(fireStoredBeforeNeighbor)
                && Double.doubleToRawLongBits(fireLiveBeforeNeighbor)
                == Double.doubleToRawLongBits(expectedCurrentLiveDy)
                && Math.abs(seatErrorBeforeNeighbor - (lowered ? 1.0d : 0.0d)) <= EPS;
        boolean matchesFutureGreen = primaryContract
                && fireFactBeforeNeighbor.present()
                && fireFactBeforeNeighbor.rawBits() == Double.doubleToRawLongBits(expectedGreenDy)
                && Double.doubleToRawLongBits(fireStoredBeforeNeighbor)
                == Double.doubleToRawLongBits(expectedGreenDy)
                && Double.doubleToRawLongBits(fireLiveBeforeNeighbor)
                == Double.doubleToRawLongBits(expectedGreenDy)
                && Math.abs(seatErrorBeforeNeighbor) <= EPS;
        String report = name + "{owner=" + owner.toShortString() + " created=" + created.toShortString()
                + " result=" + result + " ownerRaw=" + test32Fact(ownerFactBefore)
                + " fireRaw=" + test32Fact(fireFactBeforeNeighbor) + " stored=" + fireStoredBeforeNeighbor
                + " live=" + fireLiveBeforeNeighbor + " supportTop=" + supportTopBefore
                + " fireBase=" + fireBaseBeforeNeighbor + " seatError=" + seatErrorBeforeNeighbor
                + " failures=" + failures + "}";
        return new FireContactProbe(name, created, primaryContract, matchesCurrentRed,
                matchesFutureGreen, report);
    }

    private static InteractionResult test32UseItemOn(
            GameTestHelper h,
            Item item,
            BlockPos clicked,
            Direction face,
            Vec3 hit
    ) {
        Player mock = h.makeMockServerPlayer(GameType.SURVIVAL);
        if (!(mock instanceof ServerPlayer player)) {
            return null;
        }
        return test32UseItemOn(h, item, clicked, face, hit, player);
    }

    private static InteractionResult test32UseItemOn(
            GameTestHelper h,
            Item item,
            BlockPos clicked,
            Direction face,
            Vec3 hit,
            ServerPlayer player
    ) {
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    private static boolean test32SameFact(
            SlabAnchorAttachment.PlacementDyFact first,
            SlabAnchorAttachment.PlacementDyFact second
    ) {
        return first.present() == second.present()
                && (!first.present() || first.rawBits() == second.rawBits());
    }

    private static String test32Fact(SlabAnchorAttachment.PlacementDyFact fact) {
        return fact.present() ? Double.toString(Double.longBitsToDouble(fact.rawBits())) : "absent";
    }

    /**
     * TEST37 server-admission RED: Minecraft's packet handler validates a use hit against the vanilla
     * block center before {@code BlockItem.useOn}. A DOWN-face hit on a -0.5 chain remains within the
     * mapped component tolerance without help; the recorder's -1.0 chain hit is 1.5 below its vanilla
     * center and must therefore receive the owner's exact frozen offset from the pure policy.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void chainOwnerFollowerHitShiftsServerValidationCenter(GameTestHelper h) {
        final double serverComponentTolerance = 1.0000001d;
        BlockState chainOwner = Blocks.IRON_CHAIN.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
        BlockState heldLantern = Blocks.LANTERN.defaultBlockState();

        // TEST37 PASS owner 1031,-59,665, owner-local DOWN-face hit Y=-0.5 (absolute -59.5).
        BlockPos controlOwner = new BlockPos(1031, -59, 665);
        Vec3 controlHit = new Vec3(1031.5d, -59.5d, 665.5d);
        double controlDelta = Math.abs(controlHit.y - Vec3.atCenterOf(controlOwner).y);
        if (Double.doubleToRawLongBits(controlDelta) != Double.doubleToRawLongBits(1.0d)
                || controlDelta > serverComponentTolerance) {
            throw h.assertionException(BlockPos.ZERO,
                    "TEST37 control premise: -0.5 chain DOWN hit must remain within vanilla component tolerance; "
                            + "delta=" + controlDelta + " tolerance=" + serverComponentTolerance);
        }

        // TEST37 RED owner 1030,-58,666, owner-local DOWN-face hit Y=-1.0 (absolute -59.0).
        BlockPos redOwner = new BlockPos(1030, -58, 666);
        Vec3 redHit = new Vec3(1030.5d, -59.0d, 666.5d);
        double unshiftedDelta = Math.abs(redHit.y - Vec3.atCenterOf(redOwner).y);
        if (unshiftedDelta <= serverComponentTolerance) {
            throw h.assertionException(BlockPos.ZERO,
                    "TEST37 RED premise contradicted: -1.0 chain DOWN hit must be outside vanilla component tolerance; "
                            + "delta=" + unshiftedDelta + " tolerance=" + serverComponentTolerance);
        }

        BlockState horizontalChainOwner = chainOwner.setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        BlockState nonChainObjectOwner = Blocks.OAK_FENCE.defaultBlockState();
        Vec3 outsideTranslatedX = new Vec3(redOwner.getX() + 1.5d, redHit.y, redHit.z);
        Vec3 outsideTranslatedY = new Vec3(redHit.x, redOwner.getY() - 1.25d, redHit.z);

        double horizontalChain = LandingHitValidationPolicy.shiftedCenterDy(
                redOwner, horizontalChainOwner, -1.0d, Direction.DOWN, redHit, heldLantern);
        double nonChainObject = LandingHitValidationPolicy.shiftedCenterDy(
                redOwner, nonChainObjectOwner, -1.0d, Direction.DOWN, redHit, heldLantern);
        double upFace = LandingHitValidationPolicy.shiftedCenterDy(
                redOwner, chainOwner, -1.0d, Direction.UP, redHit, heldLantern);
        double horizontalFace = LandingHitValidationPolicy.shiftedCenterDy(
                redOwner, chainOwner, -1.0d, Direction.EAST, redHit, heldLantern);
        double outsideX = LandingHitValidationPolicy.shiftedCenterDy(
                redOwner, chainOwner, -1.0d, Direction.DOWN, outsideTranslatedX, heldLantern);
        double outsideY = LandingHitValidationPolicy.shiftedCenterDy(
                redOwner, chainOwner, -1.0d, Direction.DOWN, outsideTranslatedY, heldLantern);
        double flatOwner = LandingHitValidationPolicy.shiftedCenterDy(
                redOwner, chainOwner, 0.0d, Direction.DOWN, redHit, heldLantern);
        double nullHeld = LandingHitValidationPolicy.shiftedCenterDy(
                redOwner, chainOwner, -1.0d, Direction.DOWN, redHit, null);
        double unsupportedHeld = LandingHitValidationPolicy.shiftedCenterDy(
                redOwner, chainOwner, -1.0d, Direction.DOWN, redHit, Blocks.AIR.defaultBlockState());

        java.util.function.Predicate<BlockState> previousCompatOverride =
                LandingResolver.compatFinalStateTestOverride;
        double compatHeld;
        try {
            LandingResolver.compatFinalStateTestOverride = state -> state.is(Blocks.LANTERN);
            compatHeld = LandingHitValidationPolicy.shiftedCenterDy(
                    redOwner, chainOwner, -1.0d, Direction.DOWN, redHit, heldLantern);
        } finally {
            LandingResolver.compatFinalStateTestOverride = previousCompatOverride;
        }

        if (!Double.isNaN(horizontalChain)
                || !Double.isNaN(nonChainObject)
                || !Double.isNaN(upFace)
                || !Double.isNaN(horizontalFace)
                || !Double.isNaN(outsideX)
                || !Double.isNaN(outsideY)
                || !Double.isNaN(flatOwner)
                || !Double.isNaN(nullHeld)
                || !Double.isNaN(unsupportedHeld)
                || !Double.isNaN(compatHeld)) {
            throw h.assertionException(BlockPos.ZERO,
                    "TEST37 hit-policy negative control widened: horizontalChain=" + horizontalChain
                            + " nonChainObject=" + nonChainObject + " upFace=" + upFace
                            + " horizontalFace=" + horizontalFace + " outsideX=" + outsideX
                            + " outsideY=" + outsideY + " flatOwner=" + flatOwner + " nullHeld=" + nullHeld
                            + " unsupportedHeld=" + unsupportedHeld + " compatHeld=" + compatHeld);
        }
        Slabbed.LOGGER.info(
                "TEST37-HIT-POLICY-NEGATIVES | PASS horizontalChain nonChainObject upFace horizontalFace "
                        + "outsideX outsideY flatOwner nullHeld unsupportedHeld compatHeld");

        double shiftedMinus1 = LandingHitValidationPolicy.shiftedCenterDy(
                redOwner, chainOwner, -1.0d, Direction.DOWN, redHit, heldLantern);

        // TEST37 deeper lantern RED: owner 1031,-57,667; translated DOWN hit at exact dy=-1.5.
        BlockPos minus15Owner = new BlockPos(1031, -57, 667);
        Vec3 minus15Hit = new Vec3(1031.464385d, -58.5d, 667.502332d);
        double minus15UnshiftedDelta = Math.abs(minus15Hit.y - Vec3.atCenterOf(minus15Owner).y);
        if (Double.doubleToRawLongBits(minus15UnshiftedDelta) != Double.doubleToRawLongBits(2.0d)
                || minus15UnshiftedDelta <= serverComponentTolerance) {
            throw h.assertionException(BlockPos.ZERO,
                    "TEST37 -1.5 premise: translated chain hit must be exactly 2.0 from vanilla center "
                            + "and outside tolerance; delta=" + minus15UnshiftedDelta
                            + " tolerance=" + serverComponentTolerance);
        }
        double shiftedMinus15 = LandingHitValidationPolicy.shiftedCenterDy(
                minus15Owner, chainOwner, -1.5d, Direction.DOWN, minus15Hit, heldLantern);

        // TEST37 chain-on-chain corroboration: owner 1037,-52,665; translated DOWN hit at exact dy=-4.0.
        BlockPos minus4Owner = new BlockPos(1037, -52, 665);
        Vec3 minus4Hit = new Vec3(1037.522279d, -56.0d, 665.501499d);
        double minus4UnshiftedDelta = Math.abs(minus4Hit.y - Vec3.atCenterOf(minus4Owner).y);
        if (Double.doubleToRawLongBits(minus4UnshiftedDelta) != Double.doubleToRawLongBits(4.5d)
                || minus4UnshiftedDelta <= serverComponentTolerance) {
            throw h.assertionException(BlockPos.ZERO,
                    "TEST37 -4.0 premise: translated chain hit must be exactly 4.5 from vanilla center "
                            + "and outside tolerance; delta=" + minus4UnshiftedDelta
                            + " tolerance=" + serverComponentTolerance);
        }
        double shiftedMinus4 = LandingHitValidationPolicy.shiftedCenterDy(
                minus4Owner, chainOwner, -4.0d, Direction.DOWN, minus4Hit, chainOwner);

        long minus1ExpectedBits = Double.doubleToRawLongBits(-1.0d);
        long minus15ExpectedBits = Double.doubleToRawLongBits(-1.5d);
        long minus4ExpectedBits = Double.doubleToRawLongBits(-4.0d);
        Slabbed.LOGGER.info(
                "TEST37-HIT-POLICY-POSITIVES | controlDy=-0.5 controlDelta={} "
                        + "ownerDy=-1.0 delta={} shift={} raw={} "
                        + "ownerDy=-1.5 delta={} shift={} raw={} "
                        + "ownerDy=-4.0 delta={} shift={} raw={}",
                controlDelta,
                unshiftedDelta, shiftedMinus1,
                String.format("%016x", Double.doubleToRawLongBits(shiftedMinus1)),
                minus15UnshiftedDelta, shiftedMinus15,
                String.format("%016x", Double.doubleToRawLongBits(shiftedMinus15)),
                minus4UnshiftedDelta, shiftedMinus4,
                String.format("%016x", Double.doubleToRawLongBits(shiftedMinus4)));
        if (Double.doubleToRawLongBits(shiftedMinus1) != minus1ExpectedBits) {
            throw h.assertionException(BlockPos.ZERO,
                    "TEST37 chain-owner follower hit policy must shift the server validation center by exact -1.0; "
                            + "observed=" + shiftedMinus1 + " raw="
                            + String.format("%016x", Double.doubleToRawLongBits(shiftedMinus1))
                            + " expectedRaw=" + String.format("%016x", minus1ExpectedBits));
        }
        if (Double.doubleToRawLongBits(shiftedMinus15) != minus15ExpectedBits) {
            throw h.assertionException(BlockPos.ZERO,
                    "TEST37 chain-owner follower hit policy must follow exact owner dy=-1.5; observed="
                            + shiftedMinus15 + " raw="
                            + String.format("%016x", Double.doubleToRawLongBits(shiftedMinus15))
                            + " expectedRaw=" + String.format("%016x", minus15ExpectedBits));
        }
        if (Double.doubleToRawLongBits(shiftedMinus4) != minus4ExpectedBits) {
            throw h.assertionException(BlockPos.ZERO,
                    "TEST37 chain-on-chain hit policy must follow exact owner dy=-4.0; observed="
                            + shiftedMinus4 + " raw="
                            + String.format("%016x", Double.doubleToRawLongBits(shiftedMinus4))
                            + " expectedRaw=" + String.format("%016x", minus4ExpectedBits));
        }
        h.succeed();
    }

    /**
     * TEST39 RED 1: the downward pointed-dripstone follower hit recorded at frozen dy=-1.5 must
     * reach the server's shifted validation center. This is intentionally a policy-level fixture:
     * GameTest can invoke {@link ItemStack#useOn(UseOnContext)}, but not the preceding server packet
     * component-distance guard which rejects this hit before {@code useOn} is reached.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void pointedDripstoneFollowerHitAtMinusOnePointFiveIsAdmitted(GameTestHelper h) {
        final double serverComponentTolerance = 1.0000001d;
        BlockPos owner = new BlockPos(1014, -53, 663);
        BlockState downwardTip = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN);
        BlockState heldDripstone = Blocks.POINTED_DRIPSTONE.defaultBlockState();
        Vec3 translatedDownFace = new Vec3(owner.getX() + 0.5d, owner.getY() - 1.5d, owner.getZ() + 0.5d);
        double unshiftedDelta = Math.abs(translatedDownFace.y - Vec3.atCenterOf(owner).y);
        if (unshiftedDelta <= serverComponentTolerance) {
            throw h.assertionException(owner,
                    "TEST39 pointed-dripstone premise: dy=-1.5 DOWN hit must be outside vanilla component "
                            + "tolerance; delta=" + unshiftedDelta + " tolerance=" + serverComponentTolerance);
        }

        double admitted = LandingHitValidationPolicy.shiftedCenterDy(
                owner, downwardTip, -1.5d, Direction.DOWN, translatedDownFace, heldDripstone);
        double nonDripstoneOwner = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.OAK_FENCE.defaultBlockState(),
                -1.5d, Direction.DOWN, translatedDownFace, heldDripstone);
        double upwardTipOwner = LandingHitValidationPolicy.shiftedCenterDy(
                owner, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                        .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP),
                -1.5d, Direction.DOWN, translatedDownFace, heldDripstone);
        double nonDownFace = LandingHitValidationPolicy.shiftedCenterDy(
                owner, downwardTip, -1.5d, Direction.UP, translatedDownFace, heldDripstone);
        double outsideTranslatedY = LandingHitValidationPolicy.shiftedCenterDy(
                owner, downwardTip, -1.5d, Direction.DOWN,
                new Vec3(translatedDownFace.x, owner.getY() - 2.6d, translatedDownFace.z), heldDripstone);
        double outsideTranslatedX = LandingHitValidationPolicy.shiftedCenterDy(
                owner, downwardTip, -1.5d, Direction.DOWN,
                new Vec3(owner.getX() + 1.25d, translatedDownFace.y, translatedDownFace.z), heldDripstone);
        double flatOwner = LandingHitValidationPolicy.shiftedCenterDy(
                owner, downwardTip, 0.0d, Direction.DOWN, translatedDownFace, heldDripstone);
        double nullHeld = LandingHitValidationPolicy.shiftedCenterDy(
                owner, downwardTip, -1.5d, Direction.DOWN, translatedDownFace, null);
        double unsupportedHeld = LandingHitValidationPolicy.shiftedCenterDy(
                owner, downwardTip, -1.5d, Direction.DOWN, translatedDownFace, Blocks.AIR.defaultBlockState());

        if (!Double.isNaN(nonDripstoneOwner)
                || !Double.isNaN(upwardTipOwner)
                || !Double.isNaN(nonDownFace)
                || !Double.isNaN(outsideTranslatedY)
                || !Double.isNaN(outsideTranslatedX)
                || !Double.isNaN(flatOwner)
                || !Double.isNaN(nullHeld)
                || !Double.isNaN(unsupportedHeld)) {
            throw h.assertionException(owner, "TEST39 pointed-dripstone admission negative control widened: "
                    + "nonDripstoneOwner=" + nonDripstoneOwner + " nonDownFace=" + nonDownFace
                    + " upwardTipOwner=" + upwardTipOwner
                    + " outsideTranslatedY=" + outsideTranslatedY + " outsideTranslatedX=" + outsideTranslatedX
                    + " flatOwner=" + flatOwner + " nullHeld=" + nullHeld + " unsupportedHeld=" + unsupportedHeld);
        }
        if (Double.doubleToRawLongBits(admitted) != Double.doubleToRawLongBits(-1.5d)) {
            throw h.assertionException(owner,
                    "TEST39 pointed-dripstone follower: dy=-1.5 DOWN hit must shift the server validation "
                            + "center by raw-exact -1.5; observed=" + admitted + " raw="
                            + String.format("%016x", Double.doubleToRawLongBits(admitted)));
        }
        h.succeed();
    }

    /**
     * TEST39: a high-interior side click stays in the translated visible BOTTOM half of a frozen
     * dy=-1.0 slab while preserving that exact frozen landing height.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredSideHighInteriorPlacementStaysInTranslatedVisibleBottomHalf(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = h.absolutePos(new BlockPos(3, 4, 3));
        BlockPos highInteriorTarget = owner.east();
        BlockPos lowerControlOwner = h.absolutePos(new BlockPos(8, 4, 3));
        BlockPos lowerTarget = lowerControlOwner.east();
        BlockPos flatOwner = h.absolutePos(new BlockPos(13, 4, 3));
        BlockPos flatTarget = flatOwner.east();
        BlockState ownerState = Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);

        w.setBlock(owner, ownerState, 3);
        w.setBlock(highInteriorTarget, Blocks.AIR.defaultBlockState(), 3);
        w.setBlock(lowerControlOwner, ownerState, 3);
        w.setBlock(lowerTarget, Blocks.AIR.defaultBlockState(), 3);
        w.setBlock(flatOwner, ownerState, 3);
        w.setBlock(flatTarget, Blocks.AIR.defaultBlockState(), 3);
        forceStore(w, owner, -1.0d);
        forceStore(w, lowerControlOwner, -1.0d);

        withFrozen(() -> {
            if (!w.getBlockState(highInteriorTarget).isAir()
                    || !w.getBlockState(lowerTarget).isAir()
                    || !w.getBlockState(flatTarget).isAir()
                    || Double.doubleToRawLongBits(liveDy(w, owner)) != Double.doubleToRawLongBits(-1.0d)
                    || Double.doubleToRawLongBits(liveDy(w, lowerControlOwner)) != Double.doubleToRawLongBits(-1.0d)) {
                throw h.assertionException(owner, "TEST39 slab premise: targets must start air and lowered owners "
                        + "must have raw-exact dy=-1.0");
            }

            // Visible translated BOTTOM body spans owner-local Y [-1.0, -0.5]; -0.6 is high interior.
            Vec3 highInteriorHit = new Vec3(owner.getX() + 1.0d, owner.getY() - 0.6d, owner.getZ() + 0.5d);
            Vec3 lowerHit = new Vec3(lowerControlOwner.getX() + 1.0d, lowerControlOwner.getY() - 0.9d,
                    lowerControlOwner.getZ() + 0.5d);
            Vec3 flatHit = new Vec3(flatOwner.getX() + 1.0d, flatOwner.getY() + 0.4d, flatOwner.getZ() + 0.5d);
            test32UseItemOn(h, Items.BIRCH_SLAB, owner, Direction.EAST, highInteriorHit);
            test32UseItemOn(h, Items.BIRCH_SLAB, lowerControlOwner, Direction.EAST, lowerHit);
            test32UseItemOn(h, Items.BIRCH_SLAB, flatOwner, Direction.EAST, flatHit);

            BlockState highInteriorPlaced = w.getBlockState(highInteriorTarget);
            BlockState lowerPlaced = w.getBlockState(lowerTarget);
            BlockState flatPlaced = w.getBlockState(flatTarget);
            if (highInteriorPlaced.getBlock() != Blocks.BIRCH_SLAB
                    || lowerPlaced.getBlock() != Blocks.BIRCH_SLAB
                    || flatPlaced.getBlock() != Blocks.BIRCH_SLAB) {
                throw h.assertionException(owner, "TEST39 slab premise: real useOn must place birch slabs in all "
                        + "three adjacent air targets; highInterior=" + highInteriorPlaced + " lower=" + lowerPlaced
                        + " flat=" + flatPlaced);
            }
            if (lowerPlaced.getValue(SlabBlock.TYPE) != SlabType.BOTTOM
                    || flatPlaced.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw h.assertionException(owner, "TEST39 slab controls changed: lower-half and flat-owner clicks "
                        + "must remain BOTTOM; lower=" + lowerPlaced + " flat=" + flatPlaced);
            }
            if (Double.doubleToRawLongBits(storedDy(w, highInteriorTarget)) != Double.doubleToRawLongBits(-1.0d)
                    || Double.doubleToRawLongBits(liveDy(w, highInteriorTarget)) != Double.doubleToRawLongBits(-1.0d)) {
                throw h.assertionException(highInteriorTarget, "TEST39 slab height regression: high-interior side placement "
                        + "must retain raw-exact dy=-1.0; stored=" + storedDy(w, highInteriorTarget)
                        + " live=" + liveDy(w, highInteriorTarget));
            }
            if (highInteriorPlaced.getValue(SlabBlock.TYPE) != SlabType.BOTTOM) {
                throw h.assertionException(highInteriorTarget, "TEST39 high-interior side placement: visible BOTTOM "
                        + "body click on a dy=-1.0 slab must place BOTTOM, not "
                        + highInteriorPlaced.getValue(SlabBlock.TYPE));
            }
        });
        h.succeed();
    }

    /**
     * TEST39 adjacent-merge regression: a visible-upper-half side click on a frozen dy=-1.0 slab
     * owner must merge the compatible slab already occupying the intended adjacent cell, without
     * merging the clicked owner or spilling into the next outward cell.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredSidePlacementMergesCompatibleAdjacentTarget(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = h.absolutePos(new BlockPos(3, 4, 3));
        BlockPos target = owner.east();
        BlockPos outward = target.east();
        BlockState bottomBirch = Blocks.BIRCH_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM);

        w.setBlock(owner, bottomBirch, 3);
        w.setBlock(target, bottomBirch, 3);
        w.setBlock(outward, Blocks.AIR.defaultBlockState(), 3);
        forceStore(w, owner, -1.0d);
        forceStore(w, target, -1.0d);

        withFrozen(() -> {
            if (!w.getBlockState(owner).equals(bottomBirch)
                    || !w.getBlockState(target).equals(bottomBirch)
                    || !w.getBlockState(outward).isAir()
                    || Double.doubleToRawLongBits(storedDy(w, owner)) != Double.doubleToRawLongBits(-1.0d)
                    || Double.doubleToRawLongBits(liveDy(w, owner)) != Double.doubleToRawLongBits(-1.0d)
                    || Double.doubleToRawLongBits(storedDy(w, target)) != Double.doubleToRawLongBits(-1.0d)
                    || Double.doubleToRawLongBits(liveDy(w, target)) != Double.doubleToRawLongBits(-1.0d)) {
                throw h.assertionException(owner, "TEST39 adjacent-merge premise: owner and intended target must "
                        + "begin as non-DOUBLE bottom birch slabs at raw-exact dy=-1.0, with outward air");
            }

            Vec3 upperHit = new Vec3(
                    owner.getX() + 1.0d, owner.getY() - 0.6d, owner.getZ() + 0.5d);
            InteractionResult result = test32UseItemOn(
                    h, Items.BIRCH_SLAB, owner, Direction.EAST, upperHit);
            BlockState ownerAfter = w.getBlockState(owner);
            BlockState targetAfter = w.getBlockState(target);
            BlockState outwardAfter = w.getBlockState(outward);

            if (result == null || !result.consumesAction()) {
                throw h.assertionException(target,
                        "TEST39 adjacent-merge premise: real useOn did not consume the compatible merge; result="
                                + result);
            }
            if (!ownerAfter.equals(bottomBirch)
                    || Double.doubleToRawLongBits(storedDy(w, owner)) != Double.doubleToRawLongBits(-1.0d)
                    || Double.doubleToRawLongBits(liveDy(w, owner)) != Double.doubleToRawLongBits(-1.0d)) {
                throw h.assertionException(owner, "TEST39 adjacent-merge: clicked owner must remain its original "
                        + "non-DOUBLE state at raw-exact dy=-1.0; state=" + ownerAfter
                        + " stored=" + storedDy(w, owner) + " live=" + liveDy(w, owner));
            }
            if (targetAfter.getBlock() != Blocks.BIRCH_SLAB
                    || targetAfter.getValue(SlabBlock.TYPE) != SlabType.DOUBLE
                    || Double.doubleToRawLongBits(storedDy(w, target)) != Double.doubleToRawLongBits(-1.0d)
                    || Double.doubleToRawLongBits(liveDy(w, target)) != Double.doubleToRawLongBits(-1.0d)) {
                throw h.assertionException(target, "TEST39 adjacent-merge: compatible intended target must become "
                        + "DOUBLE and retain raw-exact dy=-1.0; state=" + targetAfter
                        + " stored=" + storedDy(w, target) + " live=" + liveDy(w, target));
            }
            if (!outwardAfter.isAir()
                    || SlabAnchorAttachment.rawPlacementDyFact(w, outward).present()) {
                throw h.assertionException(outward, "TEST39 adjacent-merge: next outward cell must remain "
                        + "unchanged air with no stored dy; state=" + outwardAfter);
            }
        });
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void missingAdjacentMergeTargetFactStaysStableFlat(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = h.absolutePos(new BlockPos(3, 4, 3));
        BlockPos target = owner.east();
        BlockPos outward = target.east();
        BlockState bottomBirch = Blocks.BIRCH_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        long negativeOneBits = Double.doubleToRawLongBits(-1.0d);
        long positiveZeroBits = Double.doubleToRawLongBits(0.0d);

        w.setBlock(owner, bottomBirch, 3);
        w.setBlock(target, bottomBirch, 3);
        w.setBlock(outward, Blocks.AIR.defaultBlockState(), 3);
        forceStore(w, owner, -1.0d);

        withFrozen(() -> {
            if (!w.getBlockState(owner).equals(bottomBirch)
                    || !w.getBlockState(target).equals(bottomBirch)
                    || !w.getBlockState(outward).isAir()
                    || Double.doubleToRawLongBits(storedDy(w, owner)) != negativeOneBits
                    || Double.doubleToRawLongBits(liveDy(w, owner)) != negativeOneBits
                    || SlabAnchorAttachment.rawPlacementDyFact(w, target).present()
                    || Double.doubleToRawLongBits(liveDy(w, target)) != positiveZeroBits
                    || SlabAnchorAttachment.rawPlacementDyFact(w, outward).present()) {
                throw h.assertionException(target, "missing-target merge premise: owner must be raw -1.0, target "
                        + "must be occupied/factless at raw positive 0.0, and outward must be air/factless");
            }

            Vec3 upperHit = new Vec3(
                    owner.getX() + 1.0d, owner.getY() - 0.6d, owner.getZ() + 0.5d);
            InteractionResult result = test32UseItemOn(
                    h, Items.BIRCH_SLAB, owner, Direction.EAST, upperHit);
            BlockState targetAfter = w.getBlockState(target);
            if (result == null || !result.consumesAction()) {
                throw h.assertionException(target,
                        "missing-target merge premise: real useOn did not consume; result=" + result);
            }
            if (!w.getBlockState(owner).equals(bottomBirch)
                    || Double.doubleToRawLongBits(storedDy(w, owner)) != negativeOneBits
                    || Double.doubleToRawLongBits(liveDy(w, owner)) != negativeOneBits) {
                throw h.assertionException(owner, "missing-target merge: clicked owner changed from raw -1.0");
            }
            if (!w.getBlockState(outward).isAir()
                    || SlabAnchorAttachment.rawPlacementDyFact(w, outward).present()) {
                throw h.assertionException(outward, "missing-target merge: outward must remain air/factless");
            }
            SlabAnchorAttachment.PlacementDyFact targetFactAfter =
                    SlabAnchorAttachment.rawPlacementDyFact(w, target);
            double targetStoredAfter = storedDy(w, target);
            double targetLiveAfter = liveDy(w, target);
            if (targetAfter.getBlock() != Blocks.BIRCH_SLAB
                    || targetAfter.getValue(SlabBlock.TYPE) != SlabType.DOUBLE
                    || !targetFactAfter.present()
                    || targetFactAfter.rawBits() != positiveZeroBits
                    || Double.doubleToRawLongBits(targetStoredAfter) != positiveZeroBits
                    || Double.doubleToRawLongBits(targetLiveAfter) != positiveZeroBits) {
                throw h.assertionException(target, "missing-target merge must become DOUBLE at raw positive 0.0; "
                        + "state=" + targetAfter + " factPresent=" + targetFactAfter.present()
                        + " factRaw=" + Long.toHexString(targetFactAfter.rawBits())
                        + " stored=" + targetStoredAfter + " live=" + targetLiveAfter);
            }
        });
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void topAdjacentMergeTargetPreservesExplicitDy(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos owner = h.absolutePos(new BlockPos(3, 4, 3));
        BlockPos target = owner.east();
        BlockPos outward = target.east();
        BlockState bottomBirch = Blocks.BIRCH_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState topBirch = Blocks.BIRCH_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.TOP);
        long negativeOneBits = Double.doubleToRawLongBits(-1.0d);

        w.setBlock(owner, bottomBirch, 3);
        w.setBlock(target, topBirch, 3);
        w.setBlock(outward, Blocks.AIR.defaultBlockState(), 3);
        forceStore(w, owner, -1.0d);
        forceStore(w, target, -1.0d);

        withFrozen(() -> {
            if (!w.getBlockState(owner).equals(bottomBirch)
                    || !w.getBlockState(target).equals(topBirch)
                    || !w.getBlockState(outward).isAir()
                    || Double.doubleToRawLongBits(storedDy(w, owner)) != negativeOneBits
                    || Double.doubleToRawLongBits(liveDy(w, owner)) != negativeOneBits
                    || Double.doubleToRawLongBits(storedDy(w, target)) != negativeOneBits
                    || Double.doubleToRawLongBits(liveDy(w, target)) != negativeOneBits) {
                throw h.assertionException(target, "TOP-target merge premise: owner and target must begin at raw -1.0");
            }

            Vec3 lowerHit = new Vec3(
                    owner.getX() + 1.0d, owner.getY() - 0.9d, owner.getZ() + 0.5d);
            InteractionResult result = test32UseItemOn(
                    h, Items.BIRCH_SLAB, owner, Direction.EAST, lowerHit);
            BlockState targetAfter = w.getBlockState(target);
            if (result == null || !result.consumesAction()) {
                throw h.assertionException(target,
                        "TOP-target merge premise: real useOn did not consume; result=" + result);
            }
            if (!w.getBlockState(owner).equals(bottomBirch)
                    || Double.doubleToRawLongBits(storedDy(w, owner)) != negativeOneBits
                    || Double.doubleToRawLongBits(liveDy(w, owner)) != negativeOneBits) {
                throw h.assertionException(owner, "TOP-target merge: clicked owner changed from raw -1.0");
            }
            if (targetAfter.getBlock() != Blocks.BIRCH_SLAB
                    || targetAfter.getValue(SlabBlock.TYPE) != SlabType.DOUBLE
                    || Double.doubleToRawLongBits(storedDy(w, target)) != negativeOneBits
                    || Double.doubleToRawLongBits(liveDy(w, target)) != negativeOneBits) {
                throw h.assertionException(target, "TOP-target merge must become DOUBLE at raw -1.0; state="
                        + targetAfter + " stored=" + storedDy(w, target) + " live=" + liveDy(w, target));
            }
            if (!w.getBlockState(outward).isAir()
                    || SlabAnchorAttachment.rawPlacementDyFact(w, outward).present()) {
                throw h.assertionException(outward, "TOP-target merge: outward must remain air/factless");
            }
        });
        h.succeed();
    }

    /**
     * TEST39 recorded DOUBLE-owner regression: horizontal placement must interpret the player's
     * upper/lower aim inside the owner's translated visible full-height body, not its vanilla cell.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredDoubleSideUpperHalfPlacementKeepsTopSlabType(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        BlockPos upperOwner = h.absolutePos(new BlockPos(3, 4, 3));
        BlockPos upperTarget = upperOwner.east();
        BlockPos lowerOwner = h.absolutePos(new BlockPos(9, 4, 3));
        BlockPos lowerTarget = lowerOwner.east();
        BlockState doubleBirch = Blocks.BIRCH_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.DOUBLE);

        w.setBlock(upperOwner, doubleBirch, 3);
        w.setBlock(upperTarget, Blocks.AIR.defaultBlockState(), 3);
        w.setBlock(lowerOwner, doubleBirch, 3);
        w.setBlock(lowerTarget, Blocks.AIR.defaultBlockState(), 3);
        forceStore(w, upperOwner, -1.0d);
        forceStore(w, lowerOwner, -1.0d);

        withFrozen(() -> {
            if (!w.getBlockState(upperOwner).equals(doubleBirch)
                    || !w.getBlockState(lowerOwner).equals(doubleBirch)
                    || !w.getBlockState(upperTarget).isAir()
                    || !w.getBlockState(lowerTarget).isAir()
                    || Double.doubleToRawLongBits(storedDy(w, upperOwner)) != Double.doubleToRawLongBits(-1.0d)
                    || Double.doubleToRawLongBits(liveDy(w, upperOwner)) != Double.doubleToRawLongBits(-1.0d)
                    || Double.doubleToRawLongBits(storedDy(w, lowerOwner)) != Double.doubleToRawLongBits(-1.0d)
                    || Double.doubleToRawLongBits(liveDy(w, lowerOwner)) != Double.doubleToRawLongBits(-1.0d)) {
                throw h.assertionException(upperOwner, "TEST39 DOUBLE-owner premise: both owners must begin "
                        + "DOUBLE at raw-exact dy=-1.0 with adjacent air");
            }

            Vec3 upperHit = new Vec3(
                    upperOwner.getX() + 1.0d, upperOwner.getY() - 0.124d, upperOwner.getZ() + 0.5d);
            Vec3 lowerHit = new Vec3(
                    lowerOwner.getX() + 1.0d, lowerOwner.getY() - 0.75d, lowerOwner.getZ() + 0.5d);
            InteractionResult upperResult = test32UseItemOn(
                    h, Items.BIRCH_SLAB, upperOwner, Direction.EAST, upperHit);
            InteractionResult lowerResult = test32UseItemOn(
                    h, Items.BIRCH_SLAB, lowerOwner, Direction.EAST, lowerHit);

            BlockState upperOwnerAfter = w.getBlockState(upperOwner);
            BlockState upperPlaced = w.getBlockState(upperTarget);
            BlockState lowerOwnerAfter = w.getBlockState(lowerOwner);
            BlockState lowerPlaced = w.getBlockState(lowerTarget);
            if (upperResult == null || !upperResult.consumesAction()
                    || lowerResult == null || !lowerResult.consumesAction()) {
                throw h.assertionException(upperOwner, "TEST39 DOUBLE-owner premise: both real useOn calls must "
                        + "consume; upperResult=" + upperResult + " lowerResult=" + lowerResult);
            }
            if (!upperOwnerAfter.equals(doubleBirch)
                    || Double.doubleToRawLongBits(storedDy(w, upperOwner)) != Double.doubleToRawLongBits(-1.0d)
                    || Double.doubleToRawLongBits(liveDy(w, upperOwner)) != Double.doubleToRawLongBits(-1.0d)) {
                throw h.assertionException(upperOwner, "TEST39 DOUBLE-owner upper scene: clicked owner must remain "
                        + "DOUBLE at raw-exact dy=-1.0; state=" + upperOwnerAfter
                        + " stored=" + storedDy(w, upperOwner) + " live=" + liveDy(w, upperOwner));
            }
            if (upperPlaced.getBlock() != Blocks.BIRCH_SLAB
                    || upperPlaced.getValue(SlabBlock.TYPE) != SlabType.TOP
                    || Double.doubleToRawLongBits(storedDy(w, upperTarget)) != Double.doubleToRawLongBits(-1.0d)
                    || Double.doubleToRawLongBits(liveDy(w, upperTarget)) != Double.doubleToRawLongBits(-1.0d)) {
                throw h.assertionException(upperTarget, "TEST39 DOUBLE-owner upper-half side placement must place "
                        + "TOP at raw-exact dy=-1.0; state=" + upperPlaced
                        + " stored=" + storedDy(w, upperTarget) + " live=" + liveDy(w, upperTarget));
            }
            if (!lowerOwnerAfter.equals(doubleBirch)
                    || Double.doubleToRawLongBits(storedDy(w, lowerOwner)) != Double.doubleToRawLongBits(-1.0d)
                    || Double.doubleToRawLongBits(liveDy(w, lowerOwner)) != Double.doubleToRawLongBits(-1.0d)) {
                throw h.assertionException(lowerOwner, "TEST39 DOUBLE-owner lower scene: clicked owner must remain "
                        + "DOUBLE at raw-exact dy=-1.0; state=" + lowerOwnerAfter
                        + " stored=" + storedDy(w, lowerOwner) + " live=" + liveDy(w, lowerOwner));
            }
            if (lowerPlaced.getBlock() != Blocks.BIRCH_SLAB
                    || lowerPlaced.getValue(SlabBlock.TYPE) != SlabType.BOTTOM
                    || Double.doubleToRawLongBits(storedDy(w, lowerTarget)) != Double.doubleToRawLongBits(-1.0d)
                    || Double.doubleToRawLongBits(liveDy(w, lowerTarget)) != Double.doubleToRawLongBits(-1.0d)) {
                throw h.assertionException(lowerTarget, "TEST39 DOUBLE-owner lower-half control must place BOTTOM "
                        + "at raw-exact dy=-1.0; state=" + lowerPlaced
                        + " stored=" + storedDy(w, lowerTarget) + " live=" + liveDy(w, lowerTarget));
            }
        });
        h.succeed();
    }

    /**
     * TEST42 RED: two strict interior side hits inside one translated visible half-slab body must
     * author the same half. The full translated cell midpoint, rather than the midpoint of an
     * individual half-body, is the placement-time boundary.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredSlabSideHitsStayInVisibleAuthoredHalf(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        double[] depths = {-1.0d, -1.5d};
        SlabType[] ownerTypes = {SlabType.BOTTOM, SlabType.TOP};
        String[] interiors = {"low-interior", "high-interior"};
        double[][] hitOffsets = {
                {-0.875d, -0.625d, -0.375d, -0.125d},
                {-1.375d, -1.125d, -0.875d, -0.625d}
        };
        BlockPos[] owners = new BlockPos[8];
        BlockPos[] targets = new BlockPos[8];
        SlabType[] expectedTypes = new SlabType[8];
        double[] expectedDys = new double[8];
        String[] rows = new String[8];

        int row = 0;
        for (int depthIndex = 0; depthIndex < depths.length; depthIndex++) {
            for (int ownerIndex = 0; ownerIndex < ownerTypes.length; ownerIndex++) {
                for (int interiorIndex = 0; interiorIndex < interiors.length; interiorIndex++) {
                    BlockPos owner = h.absolutePos(new BlockPos(3 + ownerIndex * 6 + interiorIndex * 2,
                            4, 3 + depthIndex * 6));
                    owners[row] = owner;
                    targets[row] = owner.east();
                    expectedTypes[row] = ownerTypes[ownerIndex];
                    expectedDys[row] = depths[depthIndex];
                    rows[row] = "dy=" + depths[depthIndex] + " " + ownerTypes[ownerIndex] + " "
                            + interiors[interiorIndex];
                    w.setBlock(owner, Blocks.BIRCH_SLAB.defaultBlockState()
                            .setValue(SlabBlock.TYPE, ownerTypes[ownerIndex]), 3);
                    w.setBlock(targets[row], Blocks.AIR.defaultBlockState(), 3);
                    forceStore(w, owner, depths[depthIndex]);
                    row++;
                }
            }
        }

        withFrozen(() -> {
            for (int index = 0; index < owners.length; index++) {
                if (!w.getBlockState(targets[index]).isAir()
                        || Double.doubleToRawLongBits(storedDy(w, owners[index]))
                        != Double.doubleToRawLongBits(expectedDys[index])
                        || Double.doubleToRawLongBits(liveDy(w, owners[index]))
                        != Double.doubleToRawLongBits(expectedDys[index])) {
                    throw h.assertionException(owners[index], "TEST42 premise: owner and target must begin at "
                            + rows[index] + " with raw-exact frozen dy=" + expectedDys[index]);
                }
            }

            for (int index = 0; index < owners.length; index++) {
                int depthIndex = index / 4;
                int ownerIndex = (index / 2) % 2;
                int interiorIndex = index % 2;
                double hitY = owners[index].getY() + hitOffsets[depthIndex][ownerIndex * 2 + interiorIndex];
                InteractionResult result = test32UseItemOn(h, Items.BIRCH_SLAB, owners[index], Direction.EAST,
                        new Vec3(owners[index].getX() + 1.0d, hitY, owners[index].getZ() + 0.5d));
                BlockState placed = w.getBlockState(targets[index]);
                if (result == null || !result.consumesAction() || placed.getBlock() != Blocks.BIRCH_SLAB) {
                    throw h.assertionException(targets[index], "TEST42 premise: real useOn must place the held "
                            + "birch slab for " + rows[index] + "; result=" + result + " state=" + placed);
                }
                if (placed.getValue(SlabBlock.TYPE) != expectedTypes[index]) {
                    throw h.assertionException(targets[index], "TEST42 side-aim state: both strict interior hits "
                            + "in the translated visible " + expectedTypes[index] + " body must author "
                            + expectedTypes[index] + "; row=" + rows[index] + " actual="
                            + placed.getValue(SlabBlock.TYPE));
                }
                if (Double.doubleToRawLongBits(storedDy(w, targets[index]))
                        != Double.doubleToRawLongBits(expectedDys[index])
                        || Double.doubleToRawLongBits(liveDy(w, targets[index]))
                        != Double.doubleToRawLongBits(expectedDys[index])) {
                    throw h.assertionException(targets[index], "TEST42 height: target must retain raw-exact owner "
                            + "dy for " + rows[index] + "; stored=" + storedDy(w, targets[index])
                            + " live=" + liveDy(w, targets[index]));
                }
            }

            for (int index = 0; index < targets.length; index++) {
                BlockState stateBeforeNeighbor = w.getBlockState(targets[index]);
                long storedBeforeNeighbor = Double.doubleToRawLongBits(storedDy(w, targets[index]));
                long liveBeforeNeighbor = Double.doubleToRawLongBits(liveDy(w, targets[index]));
                w.setBlock(targets[index].above(), Blocks.STONE.defaultBlockState(), 3);
                if (!w.getBlockState(targets[index]).equals(stateBeforeNeighbor)
                        || Double.doubleToRawLongBits(storedDy(w, targets[index])) != storedBeforeNeighbor
                        || Double.doubleToRawLongBits(liveDy(w, targets[index])) != liveBeforeNeighbor) {
                    throw h.assertionException(targets[index], "TEST42 stability: harmless neighbor edit changed "
                            + "the exact target state or raw-exact target dy for " + rows[index]
                            + "; before=" + stateBeforeNeighbor + " after=" + w.getBlockState(targets[index]));
                }
            }
        });
        h.succeed();
    }

    /**
     * TEST43 RED: a downward pointed-dripstone candidate must be refused when its frozen
     * translated body would strictly interpenetrate an already-frozen downward neighbor.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void translatedVerticalPointedDripstoneInterpenetrationIsRefused(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        final double upperDy = -1.5d;
        final double sideOwnerDy = -1.0d;
        final double candidateDy = -1.0d;
        BlockPos upper = h.absolutePos(new BlockPos(3, 5, 3));
        BlockPos candidate = upper.below();
        BlockPos sideOwner = candidate.west();
        BlockState downwardTip = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN);

        world.setBlock(upper, downwardTip, Block.UPDATE_ALL);
        world.setBlock(sideOwner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(candidate, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        forceStore(world, upper, upperDy);
        forceStore(world, sideOwner, sideOwnerDy);

        withFrozen(() -> {
            BlockState upperBefore = world.getBlockState(upper);
            BlockState sideOwnerBefore = world.getBlockState(sideOwner);
            long upperStoredBefore = Double.doubleToRawLongBits(storedDy(world, upper));
            long upperLiveBefore = Double.doubleToRawLongBits(liveDy(world, upper));
            long sideOwnerStoredBefore = Double.doubleToRawLongBits(storedDy(world, sideOwner));
            long sideOwnerLiveBefore = Double.doubleToRawLongBits(liveDy(world, sideOwner));
            SlabAnchorAttachment.PlacementDyFact candidateFactBefore =
                    SlabAnchorAttachment.rawPlacementDyFact(world, candidate);
            if (!upperBefore.equals(downwardTip)
                    || !sideOwnerBefore.is(Blocks.STONE)
                    || !world.getBlockState(candidate).isAir()
                    || upperStoredBefore != Double.doubleToRawLongBits(upperDy)
                    || upperLiveBefore != Double.doubleToRawLongBits(upperDy)
                    || sideOwnerStoredBefore != Double.doubleToRawLongBits(sideOwnerDy)
                    || sideOwnerLiveBefore != Double.doubleToRawLongBits(sideOwnerDy)
                    || candidateFactBefore.present()) {
                throw h.assertionException(candidate,
                        "TEST43 premise: upper downward tip must be frozen at -1.5, separate side owner at -1.0, "
                                + "and candidate air/factless; upper=" + upperBefore + " side=" + sideOwnerBefore
                                + " candidate=" + world.getBlockState(candidate));
            }

            VoxelShape upperShape = upperBefore.getCollisionShape(
                            EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
                    .move(upper.getX(), upper.getY() + upperDy, upper.getZ());
            VoxelShape candidateShape = downwardTip.getCollisionShape(
                            EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
                    .move(candidate.getX(), candidate.getY() + candidateDy, candidate.getZ());
            double overlapDepth = strictPositiveAabbOverlapDepthY(upperShape, candidateShape);
            double heightInversionDepth = candidateDy - upperDy;
            if (overlapDepth <= EPS
                    || Double.doubleToRawLongBits(heightInversionDepth)
                    != Double.doubleToRawLongBits(0.5d)
                    || Math.abs(heightInversionDepth - 0.5d) > EPS) {
                throw h.assertionException(candidate,
                        "TEST43 premise: translated downward pointed-dripstone collision overlap must be strict and "
                                + "the recorder-equivalent height inversion must be exactly 0.5; collisionDepth="
                                + overlapDepth + " heightInversionDepth=" + heightInversionDepth);
            }
            if (!SlabEnsembleCoherence.relativeTranslationIncreasesBodyOverlap(
                    downwardTip, candidate, candidateDy, upperBefore, upper, upperDy)) {
                throw h.assertionException(candidate,
                        "TEST43 baseline-delta authority: translated candidate/upper pair must be unsafe");
            }

            Player mock = h.makeMockServerPlayer(GameType.SURVIVAL);
            if (!(mock instanceof ServerPlayer player)) {
                throw h.assertionException(candidate, "TEST43 premise: fixture did not create a ServerPlayer");
            }
            ItemStack held = new ItemStack(Items.POINTED_DRIPSTONE);
            held.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY
                    .with(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN));
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            int heldBefore = held.getCount();
            var heldItemBefore = held.getItem();
            var heldBlockStateBefore = held.get(DataComponents.BLOCK_STATE);
            InteractionResult result = held.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(new Vec3(sideOwner.getX() + 1.0d, sideOwner.getY() - 0.5d,
                            sideOwner.getZ() + 0.5d), Direction.EAST, sideOwner, false)));

            BlockState candidateAfter = world.getBlockState(candidate);
            SlabAnchorAttachment.PlacementDyFact candidateFactAfter =
                    SlabAnchorAttachment.rawPlacementDyFact(world, candidate);
            ItemStack playerHeldAfter = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (candidateAfter.is(Blocks.POINTED_DRIPSTONE)) {
                if (candidateAfter.getValue(PointedDripstoneBlock.TIP_DIRECTION) != Direction.DOWN
                        || Double.doubleToRawLongBits(storedDy(world, candidate))
                        != Double.doubleToRawLongBits(candidateDy)
                        || Double.doubleToRawLongBits(liveDy(world, candidate))
                        != Double.doubleToRawLongBits(candidateDy)
                        || !candidateFactAfter.present()) {
                    throw h.assertionException(candidate,
                            "TEST43 premise: real side useOn must create the downward candidate at raw-exact -1.0; "
                                    + "result=" + result + " state=" + candidateAfter + " stored="
                                    + storedDy(world, candidate) + " live=" + liveDy(world, candidate));
                }
                SlabEnsembleCoherence.Verdict ensemble = SlabEnsembleCoherence.classifyVerticalPair(
                        world, candidate, candidateDy, upperDy);
                if (ensemble.kind() != SlabEnsembleCoherence.Kind.INTERPENETRATION
                        || Math.abs(ensemble.depth() - 0.5d) > EPS) {
                    throw h.assertionException(candidate,
                            "TEST43 premise: live two-cell ensemble must report strict vertical interpenetration "
                                    + "depth 0.5; verdict=" + ensemble);
                }
                throw h.assertionException(candidate,
                        "TEST43 translated vertical interpenetration: unsafe downward pointed-dripstone candidate "
                                + "was placed through ItemStack.useOn; collisionOverlapDepth=" + overlapDepth
                                + " ensembleDepth=" + ensemble.depth() + " (expected refusal), result=" + result
                                + " state=" + candidateAfter);
            }

            if (!candidateAfter.isAir()
                    || candidateFactAfter.present()
                    || held.getCount() != heldBefore
                    || playerHeldAfter.getCount() != heldBefore
                    || held.getItem() != heldItemBefore
                    || !held.get(DataComponents.BLOCK_STATE).equals(heldBlockStateBefore)
                    || playerHeldAfter.getItem() != heldItemBefore
                    || !playerHeldAfter.get(DataComponents.BLOCK_STATE).equals(heldBlockStateBefore)
                    || !world.getBlockState(upper).equals(upperBefore)
                    || !world.getBlockState(sideOwner).equals(sideOwnerBefore)
                    || Double.doubleToRawLongBits(storedDy(world, upper)) != upperStoredBefore
                    || Double.doubleToRawLongBits(liveDy(world, upper)) != upperLiveBefore
                    || Double.doubleToRawLongBits(storedDy(world, sideOwner)) != sideOwnerStoredBefore
                    || Double.doubleToRawLongBits(liveDy(world, sideOwner)) != sideOwnerLiveBefore) {
                throw h.assertionException(candidate,
                        "TEST43 refusal contract failed: candidate must remain air/factless, both held stacks "
                                + "must retain item identity/BLOCK_STATE/count, and both existing owners byte-identical "
                                + "with raw-exact frozen dy; result=" + result
                                + " candidate=" + candidateAfter + " candidateFact=" + candidateFactAfter.present());
            }
        });
        h.succeed();
    }

    /**
     * TEST43 RED: a horizontal click strictly inside a translated pointed-dripstone body must
     * continue that visible vertical column in the tip direction, rather than target horizontal air.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void pointedDripstoneSideHitContinuesVisibleColumn(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        final double[] depths = {-1.0d, -1.5d, -2.0d};
        final Direction horizontalFace = Direction.EAST;

        // The legal control is deliberately separate from the side-hit matrix: ordinary end-face
        // continuation at the recorder-backed -1.5 depth already works before the focused fix.
        BlockPos controlOwner = h.absolutePos(new BlockPos(3, 8, 3));
        BlockPos controlCandidate = controlOwner.below();
        BlockState controlTip = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN);
        world.setBlock(controlOwner, controlTip, Block.UPDATE_ALL);
        world.setBlock(controlCandidate, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        forceStore(world, controlOwner, -1.5d);

        withFrozen(() -> {
            ItemStack controlHeld = new ItemStack(Items.POINTED_DRIPSTONE);
            controlHeld.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY
                    .with(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN));
            Player controlMock = h.makeMockServerPlayer(GameType.SURVIVAL);
            if (!(controlMock instanceof ServerPlayer controlPlayer)) {
                throw h.assertionException(controlOwner, "TEST43 control premise: fixture did not create a ServerPlayer");
            }
            controlPlayer.setItemInHand(InteractionHand.MAIN_HAND, controlHeld);
            InteractionResult controlResult = controlHeld.useOn(new UseOnContext(controlPlayer, InteractionHand.MAIN_HAND,
                    new BlockHitResult(new Vec3(controlOwner.getX() + 0.5d, controlOwner.getY() - 1.5d,
                            controlOwner.getZ() + 0.5d), Direction.DOWN, controlOwner, false)));
            BlockState controlAfter = world.getBlockState(controlCandidate);
            if (controlResult == null || !controlResult.consumesAction()
                    || !controlAfter.is(Blocks.POINTED_DRIPSTONE)
                    || controlAfter.getValue(PointedDripstoneBlock.TIP_DIRECTION) != Direction.DOWN
                    || Double.doubleToRawLongBits(storedDy(world, controlCandidate))
                    != Double.doubleToRawLongBits(-1.5d)
                    || Double.doubleToRawLongBits(liveDy(world, controlCandidate))
                    != Double.doubleToRawLongBits(-1.5d)) {
                throw h.assertionException(controlCandidate,
                        "TEST43 vertical end-face control: ordinary downward continuation at raw-exact dy=-1.5 "
                                + "must remain legal; result=" + controlResult + " state=" + controlAfter
                                + " stored=" + storedDy(world, controlCandidate)
                                + " live=" + liveDy(world, controlCandidate));
            }

            int row = 0;
            for (Direction direction : new Direction[]{Direction.DOWN, Direction.UP}) {
                for (double depth : depths) {
                    BlockPos owner = h.absolutePos(new BlockPos(8 + row * 4, 8, 8));
                    BlockPos expectedCandidate = owner.relative(direction);
                    BlockPos horizontalAttempt = owner.relative(horizontalFace);
                    // The production interpenetration guard (slabbed$hasUnsafeVerticalTranslationOverlap)
                    // inspects exactly candidatePos.below()/above() — i.e. one more step past the
                    // candidate in the continuation direction. GameTest's ambient platform can leave a
                    // solid block there even though the fixture never places one; force it to air so the
                    // guard evaluates the intended open scene rather than an accidental one.
                    BlockPos beyondCandidate = expectedCandidate.relative(direction);
                    BlockState ownerState = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                            .setValue(PointedDripstoneBlock.TIP_DIRECTION, direction);
                    world.setBlock(owner, ownerState, Block.UPDATE_ALL);
                    world.setBlock(expectedCandidate, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    world.setBlock(horizontalAttempt, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    world.setBlock(beyondCandidate, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    forceStore(world, owner, depth);

                    long ownerBits = Double.doubleToRawLongBits(depth);
                    if (!world.getBlockState(owner).equals(ownerState)
                            || !world.getBlockState(expectedCandidate).isAir()
                            || !world.getBlockState(horizontalAttempt).isAir()
                            || !world.getBlockState(beyondCandidate).isAir()
                            || SlabAnchorAttachment.rawPlacementDyFact(world, expectedCandidate).present()
                            || SlabAnchorAttachment.rawPlacementDyFact(world, horizontalAttempt).present()
                            || Double.doubleToRawLongBits(storedDy(world, owner)) != ownerBits
                            || Double.doubleToRawLongBits(liveDy(world, owner)) != ownerBits) {
                        throw h.assertionException(owner, "TEST43 side premise: fresh owner/vertical/horizontal/"
                                + "beyondCandidate cells must begin isolated at raw-exact dy=" + depth
                                + " direction=" + direction);
                    }

                    ItemStack held = new ItemStack(Items.POINTED_DRIPSTONE);
                    held.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY
                            .with(PointedDripstoneBlock.TIP_DIRECTION, direction));
                    Player mock = h.makeMockServerPlayer(GameType.SURVIVAL);
                    if (!(mock instanceof ServerPlayer player)) {
                        throw h.assertionException(owner, "TEST43 side premise: fixture did not create a ServerPlayer");
                    }
                    player.setItemInHand(InteractionHand.MAIN_HAND, held);
                    Vec3 visibleSideHit = new Vec3(owner.getX() + 1.0d, owner.getY() + depth + 0.5d,
                            owner.getZ() + 0.5d);
                    InteractionResult result = held.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(visibleSideHit, horizontalFace, owner, false)));

                    BlockState candidateAfter = world.getBlockState(expectedCandidate);
                    if (!candidateAfter.is(Blocks.POINTED_DRIPSTONE)) {
                        throw h.assertionException(expectedCandidate,
                                "TEST43 pointed-dripstone side continuation: horizontal side hit did not create "
                                        + "the expected vertical continuation; row=direction=" + direction
                                        + " depth=" + depth + " result=" + result + " candidate=" + candidateAfter
                                        + " horizontal=" + world.getBlockState(horizontalAttempt));
                    }
                    if (candidateAfter.getValue(PointedDripstoneBlock.TIP_DIRECTION) != direction
                            || Double.doubleToRawLongBits(storedDy(world, expectedCandidate)) != ownerBits
                            || Double.doubleToRawLongBits(liveDy(world, expectedCandidate)) != ownerBits
                            || !world.getBlockState(horizontalAttempt).isAir()
                            || SlabAnchorAttachment.rawPlacementDyFact(world, horizontalAttempt).present()
                            || !world.getBlockState(owner).getValue(PointedDripstoneBlock.TIP_DIRECTION).equals(direction)
                            || Double.doubleToRawLongBits(storedDy(world, owner)) != ownerBits
                            || Double.doubleToRawLongBits(liveDy(world, owner)) != ownerBits) {
                        throw h.assertionException(expectedCandidate,
                                "TEST43 side continuation contract: candidate direction/dy, horizontal air, and "
                                        + "frozen owner must remain exact; row=direction=" + direction + " depth=" + depth
                                        + " candidate=" + candidateAfter + " stored=" + storedDy(world, expectedCandidate)
                                        + " live=" + liveDy(world, expectedCandidate));
                    }
                    row++;
                }
            }
        });
        h.succeed();
    }

    /**
     * TEST43 packet RED: translated horizontal pointed-dripstone side hits require the owner's
     * already-frozen dy as their server validation-center shift.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void pointedDripstoneSideHitUsesFrozenValidationCenter(GameTestHelper h) {
        final double serverComponentTolerance = 1.0000001d;
        final double[] depths = {-1.0d, -1.5d, -2.0d};
        final Direction horizontalFace = Direction.EAST;
        final BlockState nonPointedOwner = Blocks.OAK_FENCE.defaultBlockState();
        final BlockState noPointedHeldBlock = Blocks.AIR.defaultBlockState();

        int row = 0;
        for (Direction direction : new Direction[]{Direction.DOWN, Direction.UP}) {
            for (double depth : depths) {
                BlockPos owner = new BlockPos(1030 + row * 3, -58, 666);
                BlockState pointedOwner = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                        .setValue(PointedDripstoneBlock.TIP_DIRECTION, direction);
                BlockState pointedHeld = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                        .setValue(PointedDripstoneBlock.TIP_DIRECTION, direction);
                Vec3 translatedSideHit = new Vec3(owner.getX() + 1.0d, owner.getY() + depth + 0.25d,
                        owner.getZ() + 0.5d);
                Vec3 vanillaCenter = Vec3.atCenterOf(owner);
                double unshiftedDistance = translatedSideHit.distanceTo(vanillaCenter);
                if (unshiftedDistance <= serverComponentTolerance) {
                    throw h.assertionException(owner,
                            "TEST43 packet premise: recorder-equivalent horizontal side hit must be outside "
                                    + "the unshifted server component tolerance; row=direction=" + direction
                                    + " depth=" + depth + " distance=" + unshiftedDistance);
                }

                double actual = LandingHitValidationPolicy.shiftedCenterDy(
                        owner, pointedOwner, depth, horizontalFace, translatedSideHit, pointedHeld);
                double flatOwner = LandingHitValidationPolicy.shiftedCenterDy(
                        owner, pointedOwner, 0.0d, horizontalFace, translatedSideHit, pointedHeld);
                double unrelatedOwner = LandingHitValidationPolicy.shiftedCenterDy(
                        owner, nonPointedOwner, depth, horizontalFace, translatedSideHit, pointedHeld);
                double noPointedHeld = LandingHitValidationPolicy.shiftedCenterDy(
                        owner, pointedOwner, depth, horizontalFace, translatedSideHit, noPointedHeldBlock);
                double outsideTranslatedY = LandingHitValidationPolicy.shiftedCenterDy(
                        owner, pointedOwner, depth, horizontalFace,
                        new Vec3(translatedSideHit.x, owner.getY() + depth - 0.01d, translatedSideHit.z), pointedHeld);
                double outsideCellX = LandingHitValidationPolicy.shiftedCenterDy(
                        owner, pointedOwner, depth, horizontalFace,
                        new Vec3(owner.getX() + 1.01d, translatedSideHit.y, translatedSideHit.z), pointedHeld);
                double outsideCellZ = LandingHitValidationPolicy.shiftedCenterDy(
                        owner, pointedOwner, depth, horizontalFace,
                        new Vec3(translatedSideHit.x, translatedSideHit.y, owner.getZ() + 1.01d), pointedHeld);
                double ordinaryNonBlockUse = LandingHitValidationPolicy.shiftedCenterDy(
                        owner, pointedOwner, depth, horizontalFace, translatedSideHit, null);
                if (!Double.isNaN(flatOwner)
                        || !Double.isNaN(unrelatedOwner)
                        || !Double.isNaN(noPointedHeld)
                        || !Double.isNaN(outsideTranslatedY)
                        || !Double.isNaN(outsideCellX)
                        || !Double.isNaN(outsideCellZ)
                        || !Double.isNaN(ordinaryNonBlockUse)) {
                    throw h.assertionException(owner,
                            "TEST43 packet negative controls widened: flatOwner=" + flatOwner
                                    + " unrelatedOwner=" + unrelatedOwner + " noPointedHeld=" + noPointedHeld
                                    + " outsideY=" + outsideTranslatedY + " outsideX=" + outsideCellX
                                    + " outsideZ=" + outsideCellZ + " ordinaryNonBlockUse=" + ordinaryNonBlockUse);
                }

                long expectedBits = Double.doubleToRawLongBits(depth);
                if (Double.doubleToRawLongBits(actual) != expectedBits) {
                    throw h.assertionException(owner,
                            "TEST43 pointed-dripstone side packet validation: horizontal side hit must return "
                                    + "the raw-exact frozen owner dy instead of NaN; row=direction=" + direction
                                    + " depth=" + depth + " observed=" + actual + " raw="
                                    + String.format("%016x", Double.doubleToRawLongBits(actual)));
                }

                double shiftedDistance = translatedSideHit.distanceTo(vanillaCenter.add(0.0d, actual, 0.0d));
                if (shiftedDistance > serverComponentTolerance) {
                    throw h.assertionException(owner,
                            "TEST43 packet shifted-center contract: returned frozen dy must put the same side hit "
                                    + "inside server component tolerance; row=direction=" + direction + " depth=" + depth
                                    + " distance=" + shiftedDistance);
                }
                row++;
            }
        }
        h.succeed();
    }
}
