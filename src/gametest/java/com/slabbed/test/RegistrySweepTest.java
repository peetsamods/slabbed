package com.slabbed.test;

import com.mojang.logging.LogUtils;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * THE REGISTRY SWEEP — the maintainer's entire manual block-family sweep, mechanized.
 *
 * <p>Places <em>every placeable {@link BlockItem} in the game</em> onto the three standard rigs via
 * the REAL {@code useOn} path (never {@code setBlock} + a hand-rolled onPlaced — the documented
 * false-green), and emits a per-item TSV report at {@code build/reports/slabbed-sweep.tsv}:
 * landing dy (GOES), the frozen {@link SlabAnchorAttachment#storedPlacementDy stored placement dy},
 * a family-agnostic SEAT verdict computed from the actual outline shapes (this is the maintainer's eyeball
 * mechanized: FLUSH / GAP / OVERLAP with the delta), and a STAYS verdict (LAW.md: a placed block's
 * height must survive a neighbor edit byte-identical, or the block itself vanishes by a genuine
 * vanilla mechanic). One gradle run replaces days of manual testing; the report is the GOES
 * punch-list that drives later, separately-reviewed lane fixes.
 *
 * <h2>SEAT metric — measured from the VANILLA (un-shifted) shapes, dy applied exactly once</h2>
 * <p>On this branch {@link BlockState#getShape(net.minecraft.world.level.BlockGetter, BlockPos)
 * getShape(world, pos)} is ALREADY dy-shifted by {@code SlabSupportStateMixin} (it injects at RETURN
 * and moves the outline by the block's yOffset) and may additionally carry the torch <em>comfort
 * overlay</em> (an extra voxel band that pollutes {@code maxY}). Reading that world-context shape and
 * then <em>also</em> adding dy double-counts the offset — the exact {@code 0bf59d56} disease the
 * ensemble classifier ({@link com.slabbed.util.SlabEnsembleCoherence}) was bitten by and cured with
 * {@link EmptyBlockGetter}. The seat metric therefore takes both sides from the context-free vanilla
 * shape ({@code state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)} — no mixin dy, no overlay)
 * and applies dy exactly once:
 * <pre>
 *   supTop = clicked.getY()   + vanillaShape(supState).maxY   + supDy
 *   plBot  = placedPos.getY() + vanillaShape(placedState).minY + placedDy
 *   seatDelta = plBot - supTop     // FLUSH |δ|&lt;1e-4 / GAP δ&gt;0 / OVERLAP δ&lt;0
 * </pre>
 * Hand-check (marked_slab rig, support = a bottom slab reading supDy=-1.0, vanilla slab maxY=0.5, so
 * its visible top sits at clickedY-0.5):
 * <ul>
 *   <li><b>torch on marked slab</b> — vanilla torch minY=0, placedDy=-1.5, placedY=clickedY+1 →
 *       plBot = clickedY+1+0-1.5 = clickedY-0.5 = supTop → <b>FLUSH 0.0000</b> (was a fabricated
 *       OVERLAP -1.1250 under the old double-counting metric).</li>
 *   <li><b>stone on marked slab</b> — vanilla stone minY=0, placedDy=-1.5 (depth-cap-removal: the full
 *       cube now accumulates its true -1.5 on the marked slab instead of the old -1.0 depth-floor) →
 *       plBot = clickedY+1-1.5 = clickedY-0.5 = supTop → <b>FLUSH 0.0000</b>. The old GAP 0.5000 was the
 *       depth-cap bug (half a block short); the gap now closes, exactly like torch/gate/candle on this
 *       rig. Under frozen-dy OFF it shows the same break_below STAYS_FAIL those siblings already pin.</li>
 * </ul>
 *
 * <p><b>This is a report generator, not a gate</b> for the 8 registry shards. The single gate is
 * {@link #hardGateAllowlistFamilies} — a small allowlist of already-green families (stone, oak_slab,
 * torch, oak_fence_gate, candle) pinned to their current verdicts. That gate is only 15 measures
 * (seconds) so it runs BY DEFAULT, independent of the opt-in below; the 8 shards stay opt-in.
 *
 * <p><b>The full sweep is SLOW (hundreds of items x 3 rigs) and runs only when opted in.</b> When the
 * opt-in is absent the shard methods succeed immediately ("sweep skipped") so the default suite time
 * is unchanged. The reliable opt-in is the {@code -D} property, forwarded to the forked gametest JVM
 * by the {@code gameTest} run config in {@code build.gradle} (loom {@code RunConfigSettings.property}):
 * <pre>
 *   JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home \
 *       ./gradlew runGameTest -Dslabbed.sweep=true
 * </pre>
 * A {@code SLABBED_SWEEP} env var is also honoured, but note it reads the <em>gradle daemon's</em>
 * environment (the JVM that forks the game), not your interactive shell — so unless the daemon was
 * launched with it exported, it will NOT reach the game. Use the {@code -D} route; the env var is a
 * convenience for daemon-level configuration only, not a rescue when {@code -D} "does not work".
 */
public final class RegistrySweepTest {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Number of alphabetical shards. One structure timeout cannot kill more than 1/8 of the sweep. */
    private static final int SHARD_COUNT = 8;

    private static final String[] RIGS = {"flush_ground", "lowered_stack", "marked_slab"};

    /** Expected support dy per rig — the rig premise (asserted after every build; see {@link #assertRigPremise}). */
    private static double expectedSupportDy(String rig) {
        switch (rig) {
            case "flush_ground":  return 0.0;
            case "lowered_stack": return -0.5;
            case "marked_slab":   return -1.0;
            default: throw new IllegalArgumentException("unknown rig " + rig);
        }
    }

    /** Hard-gate families. The gate pins their CURRENT verdicts (below) and reds on ANY drift. */
    private static final List<Item> ALLOWLIST = List.of(
            Items.STONE, Items.OAK_SLAB, Items.TORCH, Items.OAK_FENCE_GATE, Items.CANDLE);

    /**
     * The hard gate is a REGRESSION gate, not an absolute all-green assertion. Per the package:
     * "no STAYS failure, no seat regression <b>vs their current verdicts</b>." In the default build
     * frozen-dy is OFF ({@code -Dslabbed.frozenDy}), so {@code getYOffset} re-derives height from
     * neighbours and several of these families legitimately show a STAYS_FAIL on the lowered/marked
     * rigs today (oak_fence_gate &amp; candle on break_below: -1.5/-1.0 -> -0.5) — the very same law-lane
     * hole the S-2 gate ({@code NeighborUpdateInvarianceTest}) already reds on. Pinning the exact
     * current verdict makes the gate GREEN now and RED on any future movement of these already-known
     * cells; when a deliberate behaviour change lands (e.g. frozen-dy becomes default) this table is
     * regenerated from the fresh report in that same reviewed pass. Signature = placedDy|seat|delta|stays.
     */
    private static final Map<String, String> BASELINE = new LinkedHashMap<>();

    static {
        // Regenerated 2026-07-09 with the fixed (single-count, vanilla-shape) seat metric. Torch/gate/
        // candle flipped from fabricated OVERLAP to their true FLUSH seat; stone/marked keeps its REAL
        // GAP 0.5 (the -1.0 full-block depth floor, half a block short of the marked slab's visible top);
        // oak_slab/marked corrected 2.5->1.5. The composite stays battery now records BOTH legs, so the
        // fence_gate/candle marked_slab rows surface an additional (previously short-circuited) neighbor
        // leg. Full-block cohort seats unchanged in kind.
        BASELINE.put("minecraft:stone\tflush_ground",           "0.0000|FLUSH|0.0000|OK");
        BASELINE.put("minecraft:stone\tlowered_stack",          "-1.0000|FLUSH|0.0000|OK");
        BASELINE.put("minecraft:stone\tmarked_slab",            "-1.5000|FLUSH|0.0000|STAYS_FAIL:break_below -1.5->-1.0");
        BASELINE.put("minecraft:oak_slab\tflush_ground",        "0.0000|FLUSH|0.0000|OK");
        BASELINE.put("minecraft:oak_slab\tlowered_stack",       "-0.5000|GAP|0.5000|OK");
        BASELINE.put("minecraft:oak_slab\tmarked_slab",         "0.0000|GAP|1.5000|OK");
        BASELINE.put("minecraft:torch\tflush_ground",           "0.0000|FLUSH|0.0000|VANISHED");
        BASELINE.put("minecraft:torch\tlowered_stack",          "-1.0000|FLUSH|0.0000|VANISHED");
        BASELINE.put("minecraft:torch\tmarked_slab",            "-1.5000|FLUSH|0.0000|VANISHED");
        BASELINE.put("minecraft:oak_fence_gate\tflush_ground",  "0.0000|FLUSH|0.0000|OK");
        BASELINE.put("minecraft:oak_fence_gate\tlowered_stack", "-1.0000|FLUSH|0.0000|STAYS_FAIL:break_below -1.0->-0.5");
        BASELINE.put("minecraft:oak_fence_gate\tmarked_slab",   "-1.5000|FLUSH|0.0000|STAYS_FAIL:break_below -1.5->-0.5; neighbor_north -1.5->-1.0");
        BASELINE.put("minecraft:candle\tflush_ground",          "0.0000|FLUSH|0.0000|OK");
        BASELINE.put("minecraft:candle\tlowered_stack",         "-1.0000|FLUSH|0.0000|STAYS_FAIL:break_below -1.0->-0.5");
        BASELINE.put("minecraft:candle\tmarked_slab",           "-1.5000|FLUSH|0.0000|STAYS_FAIL:break_below -1.5->-0.5; neighbor_north -1.5->-1.0");
    }

    private static String signature(RigResult r) {
        return fmt(r.placedDy) + "|" + r.seat + "|" + fmt(r.seatDelta) + "|" + r.stays;
    }

    // ── opt-in gate (shards only; the hard gate runs unconditionally) ─────────
    private static boolean sweepEnabled() {
        String p = System.getProperty("slabbed.sweep");
        if (p == null || p.isEmpty()) {
            // Daemon-env convenience only (NOT a rescue for -D; see class javadoc).
            p = System.getenv("SLABBED_SWEEP");
        }
        return "true".equalsIgnoreCase(p) || "1".equals(p);
    }

    // ── report files (walk up to the project root, then build/reports) ────────
    private static Path reportsDir() {
        Path start = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath();
        Path root = start;
        for (Path p = start; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve("settings.gradle")) || Files.exists(p.resolve("build.gradle"))) {
                root = p;
                break;
            }
        }
        return root.resolve("build").resolve("reports");
    }

    private static Path reportPath() {
        return reportsDir().resolve("slabbed-sweep.tsv");
    }

    private static Path shardPath(int shard) {
        return reportsDir().resolve("slabbed-sweep.shard" + shard + ".tsv");
    }

    private static final Object FILE_LOCK = new Object();
    private static final String HEADER = "item\trig\tplaced_dy\tstored_dy\tseat\tseat_delta\tstays\tnotes";

    /** Write one shard's rows to its own file (resilience: a timeout in another shard cannot lose these). */
    private static void writeShardFile(int shard, List<String> rows) {
        Path out = shardPath(shard);
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, rows.isEmpty() ? "" : String.join("\n", rows) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new RuntimeException("slabbed-sweep: cannot write shard file " + out, e);
        }
    }

    /**
     * Concatenate every shard file, sort all rows, and write the single authoritative report. Sorting
     * by construction makes the TSV byte-identical run-to-run regardless of how the gametest framework
     * batches/orders shard execution (the diffability requirement). Row keys (item\trig) are unique, so
     * a plain lexicographic sort is a total order.
     */
    private static void assembleReport() {
        synchronized (FILE_LOCK) {
            List<String> all = new ArrayList<>();
            for (int s = 0; s < SHARD_COUNT; s++) {
                Path sp = shardPath(s);
                if (!Files.exists(sp)) {
                    continue;
                }
                try {
                    for (String line : Files.readAllLines(sp, StandardCharsets.UTF_8)) {
                        if (!line.isEmpty()) {
                            all.add(line);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException("slabbed-sweep: cannot read shard file " + sp, e);
                }
            }
            all.sort(Comparator.naturalOrder());
            Path out = reportPath();
            try {
                Files.createDirectories(out.getParent());
                StringBuilder sb = new StringBuilder(HEADER).append('\n');
                for (String line : all) {
                    sb.append(line).append('\n');
                }
                Files.writeString(out, sb.toString(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                LOGGER.info("[slabbed-sweep] report -> {} ({} rows)", out, all.size());
            } catch (IOException e) {
                throw new RuntimeException("slabbed-sweep: cannot write report " + out, e);
            }
        }
    }

    // ── running summary (aggregated across shards) ──────────────────────────
    // per rig: [total, flush, gap, overlap, not_placed, stays_fail]
    private static final Map<String, long[]> SUMMARY = new LinkedHashMap<>();

    static {
        for (String rig : RIGS) {
            SUMMARY.put(rig, new long[6]);
        }
    }

    private static void tally(String rig, RigResult r) {
        synchronized (SUMMARY) {
            long[] s = SUMMARY.get(rig);
            s[0]++;
            if (!r.placed) {
                s[4]++;
            }
            // IN_CELL self-merge rows carry seat=N/A and must NOT pollute the seat tallies.
            if ("FLUSH".equals(r.seat)) {
                s[1]++;
            } else if ("GAP".equals(r.seat)) {
                s[2]++;
            } else if ("OVERLAP".equals(r.seat)) {
                s[3]++;
            }
            if (r.stays != null && r.stays.startsWith("STAYS_FAIL")) {
                s[5]++;
            }
        }
    }

    private static final AtomicInteger SHARDS_DONE = new AtomicInteger();

    private static void logSummary(String tag) {
        synchronized (SUMMARY) {
            for (String rig : RIGS) {
                long[] s = SUMMARY.get(rig);
                LOGGER.info("[slabbed-sweep] {} rig={} total={} flush={} gap={} overlap={} not_placed={} stays_fail={}",
                        tag, rig, s[0], s[1], s[2], s[3], s[4], s[5]);
            }
        }
    }

    // ── real-useOn placement (copied idiom from NeighborUpdateInvarianceTest) ─
    /** Places {@code item} on the UP face of {@code clicked}; returns true if the held stack was consumed. */
    private static boolean place(GameTestHelper h, Item item, BlockPos clicked, Direction face) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked)
                .add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
        // Survival BlockItem.place shrinks the stack on a successful placement; an empty/short stack
        // therefore means the item WAS consumed even if the resulting block is now air (popped).
        ItemStack after = player.getItemInHand(InteractionHand.MAIN_HAND);
        return after.isEmpty() || after.getCount() < 1;
    }

    private static double dy(ServerLevel w, BlockPos p, BlockState s) {
        return SlabSupport.getYOffset(w, p, s);
    }

    /** Exact height identity (byte-identical intent), with -0.0 normalized to 0.0. */
    private static boolean sameHeight(double a, double b) {
        return Double.doubleToRawLongBits(a == 0.0 ? 0.0 : a)
                == Double.doubleToRawLongBits(b == 0.0 ? 0.0 : b);
    }

    /**
     * VANILLA-space shape, deliberately queried context-free (empty getter, origin pos): carries no
     * {@code SlabSupportStateMixin} dy shift and no torch comfort overlay, so the seat metric can add
     * dy exactly once. Mirrors {@code SlabEnsembleCoherence.vanillaShape}.
     */
    private static VoxelShape vanillaShape(BlockState state) {
        return state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    private static void bslab(ServerLevel w, BlockPos p) {
        w.setBlock(p, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
    }

    /**
     * Reset the arena to pristine world state between items. Setting AIR alone is NOT enough:
     * {@code getYOffset} re-derives magnitude from the persistent {@link SlabAnchorAttachment} marker
     * SETS and the stored-placement-dy map, and {@code setBlock(AIR, flag=2)} does not fire the removal
     * hook that clears them — so a prior item's markers leak into the next item placed in the same cell
     * and make the measurement order-dependent. {@link SlabAnchorAttachment#removeAnchor} drops every
     * marker type plus the stored-dy entry, restoring determinism (identical TSV run-to-run).
     */
    private static void clearArena(GameTestHelper h, ServerLevel w) {
        for (int x = 0; x <= 6; x++) {
            for (int y = 0; y <= 11; y++) {
                for (int z = 0; z <= 6; z++) {
                    BlockPos p = h.absolutePos(new BlockPos(x, y, z));
                    w.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                    SlabAnchorAttachment.removeAnchor(w, p);
                }
            }
        }
    }

    // ── rigs: each returns the SUPPORT cell whose UP face the subject is clicked on ──
    private static BlockPos buildRig(GameTestHelper h, ServerLevel w, String rig) {
        switch (rig) {
            case "flush_ground": {
                BlockPos g = h.absolutePos(new BlockPos(3, 1, 3));
                w.setBlock(g, Blocks.STONE.defaultBlockState(), 2);
                return g;
            }
            case "lowered_stack":
                return loweredStackRig(h, w);
            case "marked_slab":
                return markedSlabRig(h, w);
            default:
                throw new IllegalArgumentException("unknown rig " + rig);
        }
    }

    /**
     * Rig premise: a silently-degraded rig would poison a thousand rows. After building each rig, the
     * support MUST read its expected dy (0.0 flush_ground, -0.5 lowered_stack, -1.0 marked_slab); on a
     * mismatch throw a loud {@code assertionException} (this escapes {@code measure}'s per-item catch
     * because it runs BEFORE the try — a broken rig fails the whole shard, it is never demoted to an
     * ERROR row).
     */
    private static void assertRigPremise(GameTestHelper h, ServerLevel w, BlockPos support, String rig) {
        double expected = expectedSupportDy(rig);
        double actual = dy(w, support, w.getBlockState(support));
        if (!sameHeight(expected, actual)) {
            throw h.assertionException("rig premise FAILED — rig '" + rig + "' support at " + support
                    + " expected dy " + expected + " but read " + actual
                    + " (a degraded rig must not silently poison the sweep)");
        }
    }

    /** Bottom slab that reads -0.5 via carrier-below (copied idiom). */
    private static BlockPos loweredStackRig(GameTestHelper h, ServerLevel w) {
        BlockPos base = h.absolutePos(new BlockPos(3, 1, 3));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bslab(w, base.above(1));
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        bslab(w, base.above(3)); // reads -0.5 via carrier-below
        return base.above(3);
    }

    /** Compound-visible marked bottom slab that reads -1.0 (copied idiom). */
    private static BlockPos markedSlabRig(GameTestHelper h, ServerLevel w) {
        BlockPos base = h.absolutePos(new BlockPos(3, 1, 3));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bslab(w, base.above(1));
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        bslab(w, base.above(3));
        BlockPos fb = base.above(4);
        w.setBlock(fb, Blocks.STONE.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, fb, w.getBlockState(fb));
        SlabAnchorAttachment.addCompoundFullBlockAnchor(w, fb, w.getBlockState(fb));
        BlockPos support = fb.west();
        bslab(w, support);
        SlabAnchorAttachment.addCompoundVisibleSideLowerSlab(w, support, w.getBlockState(support),
                fb, w.getBlockState(fb));
        return support;
    }

    // ── the measurement ─────────────────────────────────────────────────────
    private static final class RigResult {
        final double placedDy;
        final double storedDy;
        final String seat;
        final double seatDelta;
        final String stays;
        final String notes;
        final boolean placed;

        RigResult(double placedDy, double storedDy, String seat, double seatDelta,
                  String stays, String notes, boolean placed) {
            this.placedDy = placedDy;
            this.storedDy = storedDy;
            this.seat = seat;
            this.seatDelta = seatDelta;
            this.stays = stays;
            this.notes = notes;
            this.placed = placed;
        }
    }

    /** Which cell holds the freshly placed subject (above the clicked face, or the clicked cell for in-place merges). */
    private static BlockPos detectPlaced(ServerLevel w, BlockPos clicked, BlockState supBefore, BlockState aboveBefore) {
        BlockState nowAbove = w.getBlockState(clicked.above());
        if (!nowAbove.isAir() && !nowAbove.equals(aboveBefore)) {
            return clicked.above();
        }
        BlockState nowClicked = w.getBlockState(clicked);
        if (!nowClicked.isAir() && !nowClicked.equals(supBefore)) {
            return clicked;
        }
        return null;
    }

    private static RigResult measure(GameTestHelper h, ServerLevel w, Item item, String rig) {
        // Rig build + premise assertion live OUTSIDE the per-item catch: a degraded rig must fail loudly,
        // never be demoted to an ERROR row.
        clearArena(h, w);
        BlockPos clicked = buildRig(h, w, rig);
        assertRigPremise(h, w, clicked, rig);

        try {
            BlockState supportBefore = w.getBlockState(clicked);
            BlockState aboveBefore = w.getBlockState(clicked.above());

            boolean consumed;
            try {
                consumed = place(h, item, clicked, Direction.UP);
            } catch (Throwable t) {
                return new RigResult(Double.NaN, Double.NaN, "-", Double.NaN, "N/A",
                        "ERROR:" + t.getClass().getSimpleName(), false);
            }

            BlockPos placedPos = detectPlaced(w, clicked, supportBefore, aboveBefore);
            if (placedPos == null) {
                // Distinguish a rejected placement (stack untouched) from one that placed then popped
                // (stack consumed, cell now air) — the latter is a law-relevant event.
                String note = consumed ? "PLACED_THEN_POPPED" : "NOT_PLACED";
                return new RigResult(Double.NaN, Double.NaN, "N/A", Double.NaN, "N/A", note, false);
            }

            BlockState placedState = w.getBlockState(placedPos);
            double placedDy = dy(w, placedPos, placedState);
            double storedDy = SlabAnchorAttachment.storedPlacementDy(w, placedPos);
            boolean inCell = placedPos.equals(clicked);
            String notes = inCell ? "IN_CELL" : "";

            // SEAT verdict from the VANILLA (un-shifted) shapes, dy applied exactly once. An IN_CELL
            // self-merge (placedPos == clicked) has no meaningful support-vs-placed comparison — record
            // N/A and exclude it from the seat tallies.
            String seat;
            double seatDelta;
            if (inCell) {
                seat = "N/A";
                seatDelta = Double.NaN;
            } else {
                BlockState supState = w.getBlockState(clicked);
                double supDy = dy(w, clicked, supState);
                VoxelShape supShape = vanillaShape(supState);
                VoxelShape plShape = vanillaShape(placedState);
                if (plShape.isEmpty() || supShape.isEmpty()) {
                    seat = "NO_SHAPE";
                    seatDelta = Double.NaN;
                } else {
                    double supTop = clicked.getY() + supShape.bounds().maxY + supDy;
                    double plBot = placedPos.getY() + plShape.bounds().minY + placedDy;
                    seatDelta = plBot - supTop;
                    seat = Math.abs(seatDelta) < 1e-4 ? "FLUSH" : (seatDelta > 0 ? "GAP" : "OVERLAP");
                }
            }

            String stays = stays(h, w, item, rig, clicked, placedPos, placedDy);

            return new RigResult(placedDy, storedDy, seat, seatDelta, stays, notes, true);
        } catch (Throwable t) {
            return new RigResult(Double.NaN, Double.NaN, "-", Double.NaN, "N/A",
                    "ERROR:" + t.getClass().getSimpleName(), false);
        }
    }

    /**
     * STAYS battery (LAW.md): a placed block's height must survive a neighbour edit byte-identical, or
     * the block itself vanishes by a genuine vanilla mechanic. Two legs:
     * <ol>
     *   <li><b>break_below</b> — destroy the support directly below. If the subject vanishes that is an
     *       allowed vanilla pop; otherwise its dy must be unchanged. The below cell's ORIGINAL state is
     *       captured and restored (NOT hardcoded stone) so the next leg measures exactly one edit.</li>
     *   <li><b>neighbor_north</b> — add a stone block to the north; dy must be unchanged. If the subject
     *       VANISHED on break_below it still gets this leg: the rig is rebuilt fresh, the subject
     *       re-placed, the break skipped, and only the north edit applied — decorations must not silently
     *       lose neighbour coverage.</li>
     * </ol>
     * Returns {@code OK} (no fail, survived), {@code VANISHED} (popped on break_below, neighbour leg
     * clean), or {@code STAYS_FAIL:<legs>} if any leg moved the height.
     */
    private static String stays(GameTestHelper h, ServerLevel w, Item item, String rig,
                                BlockPos clicked, BlockPos placedPos, double before) {
        List<String> fails = new ArrayList<>();

        // ── leg 1: break the support directly below ──
        BlockPos below = placedPos.below();
        BlockState belowOriginal = w.getBlockState(below);
        w.destroyBlock(below, false);
        BlockState afterBreak = w.getBlockState(placedPos);
        boolean vanishedBelow = afterBreak.isAir();
        if (!vanishedBelow) {
            double afterBreakDy = dy(w, placedPos, afterBreak);
            if (!sameHeight(before, afterBreakDy)) {
                fails.add("break_below " + before + "->" + afterBreakDy);
            }
            // Restore the ORIGINAL below state so the neighbour leg is exactly one edit.
            w.setBlock(below, belowOriginal, 2);
        }

        // ── leg 2: neighbor_north ──
        if (!vanishedBelow) {
            w.setBlock(placedPos.north(), Blocks.STONE.defaultBlockState(), 2);
            BlockState afterNbr = w.getBlockState(placedPos);
            if (!afterNbr.isAir()) {
                double afterNbrDy = dy(w, placedPos, afterNbr);
                if (!sameHeight(before, afterNbrDy)) {
                    fails.add("neighbor_north " + before + "->" + afterNbrDy);
                }
            }
        } else {
            // Subject popped on break_below — rebuild fresh, re-place, skip the break, apply ONLY north.
            clearArena(h, w);
            BlockPos clicked2 = buildRig(h, w, rig);
            BlockState sup2 = w.getBlockState(clicked2);
            BlockState above2 = w.getBlockState(clicked2.above());
            place(h, item, clicked2, Direction.UP);
            BlockPos re = detectPlaced(w, clicked2, sup2, above2);
            if (re != null) {
                double reBefore = dy(w, re, w.getBlockState(re));
                w.setBlock(re.north(), Blocks.STONE.defaultBlockState(), 2);
                BlockState afterNbr = w.getBlockState(re);
                if (!afterNbr.isAir()) {
                    double afterNbrDy = dy(w, re, afterNbr);
                    if (!sameHeight(reBefore, afterNbrDy)) {
                        fails.add("neighbor_north " + reBefore + "->" + afterNbrDy);
                    }
                }
            }
        }

        if (!fails.isEmpty()) {
            return "STAYS_FAIL:" + String.join("; ", fails);
        }
        return vanishedBelow ? "VANISHED" : "OK";
    }

    // ── formatting ──────────────────────────────────────────────────────────
    private static String fmt(double d) {
        return Double.isNaN(d) ? "NaN" : String.format(Locale.ROOT, "%.4f", d);
    }

    /** One TSV row (no trailing newline — the writer joins rows). */
    private static String row(String item, String rig, RigResult r) {
        return item + "\t" + rig + "\t" + fmt(r.placedDy) + "\t" + fmt(r.storedDy) + "\t"
                + r.seat + "\t" + fmt(r.seatDelta) + "\t" + r.stays + "\t" + r.notes;
    }

    // ── the placeable-item universe (sorted for deterministic, clean-diffing shards) ──
    private static List<Item> placeableItems() {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BlockItem)) {
                continue; // spawn eggs, tools, etc. are not placeable blocks
            }
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            String path = id.getPath();
            if (path.equals("air")
                    || path.contains("command_block")
                    || path.contains("jigsaw")
                    || path.contains("structure_block")
                    || path.contains("structure_void")) {
                continue; // technical/unplaceable
            }
            items.add(item);
        }
        items.sort(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
        return items;
    }

    private static void runShard(GameTestHelper h, int shard) {
        if (!sweepEnabled()) {
            LOGGER.info("[slabbed-sweep] shard {} skipped (set -Dslabbed.sweep=true)", shard);
            h.succeed();
            return;
        }
        ServerLevel w = h.getLevel();
        List<Item> all = placeableItems();
        List<String> rows = new ArrayList<>();
        for (int i = shard; i < all.size(); i += SHARD_COUNT) {
            Item item = all.get(i);
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            for (String rig : RIGS) {
                RigResult r = measure(h, w, item, rig);
                rows.add(row(id, rig, r));
                tally(rig, r);
            }
        }
        rows.sort(Comparator.naturalOrder());
        writeShardFile(shard, rows);
        logSummary("shard=" + shard + " cumulative");
        if (SHARDS_DONE.incrementAndGet() == SHARD_COUNT) {
            logSummary("TOTAL");
            assembleReport();
        }
        h.succeed();
    }

    // ── shard methods (alphabetical stride; one timeout kills at most 1/8) ───
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sweepShard0(GameTestHelper h) {
        runShard(h, 0);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sweepShard1(GameTestHelper h) {
        runShard(h, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sweepShard2(GameTestHelper h) {
        runShard(h, 2);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sweepShard3(GameTestHelper h) {
        runShard(h, 3);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sweepShard4(GameTestHelper h) {
        runShard(h, 4);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sweepShard5(GameTestHelper h) {
        runShard(h, 5);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sweepShard6(GameTestHelper h) {
        runShard(h, 6);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sweepShard7(GameTestHelper h) {
        runShard(h, 7);
    }

    // ── the hard gate: already-green families must place + never drift (runs BY DEFAULT) ──
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hardGateAllowlistFamilies(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        List<String> violations = new ArrayList<>();
        for (Item item : ALLOWLIST) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            for (String rig : RIGS) {
                RigResult r = measure(h, w, item, rig);
                String key = id + "\t" + rig;
                String expected = BASELINE.get(key);
                String actual = signature(r);
                if (expected == null) {
                    violations.add(id + " on " + rig + ": no baseline (actual " + actual + ")");
                } else if (!expected.equals(actual)) {
                    violations.add(id + " on " + rig + ": expected [" + expected + "] got [" + actual + "]");
                }
            }
        }
        if (!violations.isEmpty()) {
            throw h.assertionException("HARD-GATE REGRESSION — a pinned family drifted from its current"
                    + " verdict (regenerate BASELINE from build/reports/slabbed-sweep.tsv only for a"
                    + " deliberate, reviewed behaviour change):\n  " + String.join("\n  ", violations));
        }
        h.succeed();
    }
}
