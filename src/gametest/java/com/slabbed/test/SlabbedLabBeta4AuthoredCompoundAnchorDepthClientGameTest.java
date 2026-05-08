package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;

/**
 * Focused opt-in RED proof: the current boolean
 * {@code persistentFullBlockAnchor} representation cannot preserve the
 * authored compound lane depth ({@code dy=-1.0}) once the source slab
 * carrier below the compound block is removed.
 *
 * <p>This proof is intentionally narrower than
 * {@link SlabbedLabBeta4CompoundContractMatrixClientGameTest}: it isolates
 * the authored-depth gap so the contract matrix's row 9
 * ({@code SOURCE_SLAB_BREAK}) becomes a structural argument, not just an
 * observation. It does not implement the
 * {@code COMPOUND_FULL_BLOCK_ANCHOR_TYPE} sidecar attachment recommended in
 * {@code docs/beta4-compound-source-mode-design.md}.
 *
 * <p>Topology mirrors the matrix row 9/10 fixture
 * ({@code BASE_FULL_SUPPORT} bottom slab, {@code BASE_FULL} anchored stone,
 * {@code LOWERED_BOTTOM_SLAB} persistent lowered carrier,
 * {@code COMPOUND} authored {@code dy=-1.0} ordinary stone).
 *
 * <p>Markers:
 * <ul>
 *   <li>{@code [BETA4_AUTHORED_COMPOUND_ANCHOR_DEPTH_RED]} — emitted when
 *       the boolean {@code persistentFullBlockAnchor} remains true after
 *       source removal but the authored {@code dy=-1.0} cannot be
 *       recovered (current state).</li>
 *   <li>{@code [BETA4_AUTHORED_COMPOUND_ANCHOR_DEPTH_GREEN]} — future
 *       marker, emitted only when a richer authored-depth representation
 *       (sidecar {@code COMPOUND_FULL_BLOCK_ANCHOR_TYPE}) preserves
 *       {@code dy=-1.0} across source removal. Do not emit until
 *       implemented.</li>
 *   <li>{@code [BETA4_AUTHORED_COMPOUND_ANCHOR_DEPTH_PROOF_INVALIDATED]}
 *       — emitted if the post-removal compound dy unexpectedly stays at
 *       {@code -1.0} without a sidecar attachment. Stops the proof and
 *       requires investigation.</li>
 * </ul>
 *
 * <p>Property: {@code -Dslabbed.beta4AuthoredCompoundAnchorDepthRedOnly=true}.
 * The proof is a no-op when the property is not set; it does not run as
 * part of the default {@code runClientGameTest} batch.
 */
public final class SlabbedLabBeta4AuthoredCompoundAnchorDepthClientGameTest
        implements FabricClientGameTest {

    private static final String OPT_IN = "slabbed.beta4AuthoredCompoundAnchorDepthRedOnly";
    private static final String MARKER = "BETA4_AUTHORED_COMPOUND_ANCHOR_DEPTH";
    private static final double EPSILON = 1.0e-6d;
    private static final double EXPECTED_AUTHORED_DY = -1.0d;

    // Reuse matrix row 9/10 fixture topology.
    private static final BlockPos BASE_FULL_SUPPORT = new BlockPos(8, 200, 8);
    private static final BlockPos BASE_FULL = BASE_FULL_SUPPORT.up();
    private static final BlockPos LOWERED_BOTTOM_SLAB = BASE_FULL.up();
    private static final BlockPos COMPOUND = LOWERED_BOTTOM_SLAB.up();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!Boolean.getBoolean(OPT_IN)) {
            return;
        }
        TestSingleplayerContext sp = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create();
        try {
            // Compile-time / structural fact about the current attachment
            // surface. ANCHOR_TYPE is a LongOpenHashSet of packed positions
            // with no per-position value; there is no public helper that
            // returns an authored dy or lane depth for a given anchored
            // pos. This is the underlying reason the proof is RED.
            String anchorTypeShape = describeAnchorTypeShape();
            String anchorDepthApiProbe = probeForAuthoredDepthApi();

            // Phase 1: build legal compound state and capture pre-removal
            // truth (authored dy=-1.0, anchored, persistent carrier).
            seedLegalCompound(ctx, sp);
            String preLine = captureSnapshot(sp, "preSourceRemoval");

            // Pre-removal sanity. If the fixture itself is wrong, do not
            // pretend RED.
            double preCompoundDy = readDy(sp, COMPOUND);
            boolean prePersistentFullBlockAnchor = readPersistentFullBlockAnchor(sp, COMPOUND);
            double preSourceDy = readDy(sp, LOWERED_BOTTOM_SLAB);
            boolean preSourceCarrier = readPersistentLoweredSlabCarrier(sp, LOWERED_BOTTOM_SLAB);
            if (Math.abs(preCompoundDy - EXPECTED_AUTHORED_DY) > EPSILON
                    || !prePersistentFullBlockAnchor
                    || Math.abs(preSourceDy + 0.5d) > EPSILON
                    || !preSourceCarrier) {
                System.out.println("[" + MARKER + "_PROOF_INVALIDATED]"
                        + " reason=fixture_did_not_seed_legal_compound"
                        + " preCompoundDy=" + format(preCompoundDy)
                        + " prePersistentFullBlockAnchor=" + prePersistentFullBlockAnchor
                        + " preSourceDy=" + format(preSourceDy)
                        + " preSourceCarrier=" + preSourceCarrier);
                throw new IllegalStateException(
                        "BETA4_AUTHORED_COMPOUND_ANCHOR_DEPTH proof fixture invalid: " + preLine);
            }

            // Phase 2: remove source slab + tick + neighbor-update, then
            // recapture compound state.
            removeSourceSlab(ctx, sp);
            String postLine = captureSnapshot(sp, "postSourceRemoval");

            double postCompoundDy = readDy(sp, COMPOUND);
            boolean postPersistentFullBlockAnchor = readPersistentFullBlockAnchor(sp, COMPOUND);
            boolean postIsAnchored = readIsAnchored(sp, COMPOUND);
            boolean authoredDepthMissing = Math.abs(postCompoundDy - EXPECTED_AUTHORED_DY) > EPSILON;

            // The current ANCHOR_TYPE (LongOpenHashSet of packed positions)
            // structurally cannot expose authored depth. There is no
            // public method on SlabAnchorAttachment that returns a dy/lane
            // value. Confirmed both at compile-time (only boolean
            // isAnchored) and at runtime via reflection probe.
            boolean currentAnchorCanExposeDepth = false;

            // Print required RED facts.
            System.out.println("[" + MARKER + "_FACTS]"
                    + " anchorTypeShape=" + anchorTypeShape
                    + " anchorDepthApiProbe=" + anchorDepthApiProbe
                    + " expectedAuthoredDy=" + format(EXPECTED_AUTHORED_DY)
                    + " currentAnchorCanExposeDepth=" + currentAnchorCanExposeDepth);
            System.out.println("[" + MARKER + "_PHASE] " + preLine);
            System.out.println("[" + MARKER + "_PHASE] " + postLine);
            System.out.println("[" + MARKER + "_SUMMARY]"
                    + " preCompoundDy=" + format(preCompoundDy)
                    + " postCompoundDy=" + format(postCompoundDy)
                    + " prePersistentFullBlockAnchor=" + prePersistentFullBlockAnchor
                    + " postPersistentFullBlockAnchor=" + postPersistentFullBlockAnchor
                    + " postIsAnchored=" + postIsAnchored
                    + " expectedAuthoredDy=" + format(EXPECTED_AUTHORED_DY)
                    + " authoredDepthMissing=" + authoredDepthMissing
                    + " currentAnchorCanExposeDepth=" + currentAnchorCanExposeDepth);

            // Classification.
            if (postPersistentFullBlockAnchor && authoredDepthMissing) {
                System.out.println("[" + MARKER + "_RED]"
                        + " phase=preSourceRemoval"
                        + " placedDy=" + format(preCompoundDy)
                        + " placedPersistentFullBlockAnchor=" + prePersistentFullBlockAnchor
                        + " sourceDy=" + format(preSourceDy)
                        + " sourcePersistentLoweredSlabCarrier=" + preSourceCarrier
                        + " expectedAuthoredDy=" + format(EXPECTED_AUTHORED_DY)
                        + " | phase=postSourceRemoval"
                        + " placedPersistentFullBlockAnchor=" + postPersistentFullBlockAnchor
                        + " actualDy=" + format(postCompoundDy)
                        + " expectedAuthoredDy=" + format(EXPECTED_AUTHORED_DY)
                        + " authoredDepthMissing=" + authoredDepthMissing
                        + " currentAnchorCanExposeDepth=" + currentAnchorCanExposeDepth
                        + " classification=RED");
                // Intentionally fail the gametest so the opt-in run is
                // visibly RED. Default runClientGameTest does not set the
                // opt-in property and so does not run this body.
                throw new AssertionError(
                        "[" + MARKER + "_RED] boolean persistentFullBlockAnchor preserved "
                                + "but authored compound dy=-1.0 lost (postDy="
                                + format(postCompoundDy) + ").");
            }

            if (!postPersistentFullBlockAnchor) {
                // The boolean anchor was lost together with the source
                // slab. That is a different (and not-yet-proven) failure
                // mode and means our RED contract premise is wrong;
                // surface it explicitly rather than emit a fake RED.
                System.out.println("[" + MARKER + "_PROOF_INVALIDATED]"
                        + " reason=boolean_persistent_full_block_anchor_lost_after_source_removal"
                        + " postPersistentFullBlockAnchor=" + postPersistentFullBlockAnchor
                        + " postCompoundDy=" + format(postCompoundDy));
                throw new AssertionError(
                        "[" + MARKER + "_PROOF_INVALIDATED] expected anchor to remain true "
                                + "after source removal, observed postPersistentFullBlockAnchor="
                                + postPersistentFullBlockAnchor);
            }

            // Anchor still true and authored dy preserved without a
            // sidecar. Stop and report; the structural premise is wrong.
            System.out.println("[" + MARKER + "_PROOF_INVALIDATED]"
                    + " reason=authored_dy_preserved_without_sidecar"
                    + " postCompoundDy=" + format(postCompoundDy)
                    + " expectedAuthoredDy=" + format(EXPECTED_AUTHORED_DY)
                    + " currentAnchorCanExposeDepth=" + currentAnchorCanExposeDepth);
            throw new AssertionError(
                    "[" + MARKER + "_PROOF_INVALIDATED] authored compound dy=-1.0 was preserved "
                            + "after source removal without a sidecar attachment; investigate "
                            + "before claiming RED.");
        } finally {
            try {
                sp.close();
            } catch (Throwable ignored) {
                // best-effort cleanup
            }
        }
    }

    // ------------------------------------------------------------------
    // Fixture helpers (mirror matrix row 9/10 topology, see
    // SlabbedLabBeta4CompoundContractMatrixClientGameTest#seedLegalCompound).
    // ------------------------------------------------------------------

    private static void seedLegalCompound(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            clearArea(world);
            world.setBlockState(BASE_FULL_SUPPORT,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(BASE_FULL, Blocks.STONE.getDefaultState(),
                    Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, BASE_FULL, world.getBlockState(BASE_FULL));

            world.setBlockState(LOWERED_BOTTOM_SLAB,
                    Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(world, LOWERED_BOTTOM_SLAB,
                    world.getBlockState(LOWERED_BOTTOM_SLAB));

            world.setBlockState(COMPOUND, Blocks.STONE.getDefaultState(),
                    Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, COMPOUND, world.getBlockState(COMPOUND));
        });
        for (int i = 0; i < 5; i++) {
            ctx.waitTick();
        }
        sp.getClientWorld().waitForChunksRender();
    }

    private static void clearArea(World world) {
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 6; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = BASE_FULL_SUPPORT.add(x, y, z);
                    SlabAnchorAttachment.removeAnchor(world, pos);
                    SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, pos);
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
    }

    private static void removeSourceSlab(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, LOWERED_BOTTOM_SLAB);
            world.setBlockState(LOWERED_BOTTOM_SLAB, Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_LISTENERS);
            // Explicit neighbor-update pulse from the (now-empty) source
            // position to compound; mirrors matrix row 10 hazard.
            world.updateNeighborsAlways(LOWERED_BOTTOM_SLAB, Blocks.AIR, null);
        });
        for (int i = 0; i < 5; i++) {
            ctx.waitTick();
        }
        sp.getClientWorld().waitForChunksRender();
    }

    // ------------------------------------------------------------------
    // Read helpers (server-side authoritative truth).
    // ------------------------------------------------------------------

    private static String captureSnapshot(TestSingleplayerContext sp, String phase) {
        final String[] line = new String[1];
        sp.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            line[0] = "phase=" + phase
                    + " " + describeBlock(world, COMPOUND, world.getBlockState(COMPOUND), "compound")
                    + " " + describeBlock(world, LOWERED_BOTTOM_SLAB,
                            world.getBlockState(LOWERED_BOTTOM_SLAB), "source");
        });
        return line[0];
    }

    private static double readDy(TestSingleplayerContext sp, BlockPos pos) {
        final double[] dy = new double[1];
        sp.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            dy[0] = SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
        });
        return dy[0];
    }

    private static boolean readIsAnchored(TestSingleplayerContext sp, BlockPos pos) {
        final boolean[] v = new boolean[1];
        sp.getServer().runOnServer(server -> {
            v[0] = SlabAnchorAttachment.isAnchored(server.getOverworld(), pos);
        });
        return v[0];
    }

    private static boolean readPersistentFullBlockAnchor(TestSingleplayerContext sp, BlockPos pos) {
        final boolean[] v = new boolean[1];
        sp.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            BlockState state = world.getBlockState(pos);
            boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
            v[0] = anchored
                    && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, pos, state);
        });
        return v[0];
    }

    private static boolean readPersistentLoweredSlabCarrier(TestSingleplayerContext sp, BlockPos pos) {
        final boolean[] v = new boolean[1];
        sp.getServer().runOnServer(server -> {
            World world = server.getOverworld();
            v[0] = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos,
                    world.getBlockState(pos));
        });
        return v[0];
    }

    private static String describeBlock(World world, BlockPos pos, BlockState state, String label) {
        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        boolean persistentFullBlockAnchor = anchored
                && SlabAnchorAttachment.isOrdinaryFullBlockAnchorCandidate(world, pos, state);
        boolean persistentCarrier =
                SlabAnchorAttachment.isPersistentLoweredSlabCarrier(world, pos, state);
        boolean persistentBottomCarrier =
                SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(world, pos, state);
        String slabType = state.contains(SlabBlock.TYPE) ? state.get(SlabBlock.TYPE).asString() : "none";
        return label + " pos=" + pos.toShortString()
                + " state=" + state
                + " dy=" + format(dy)
                + " persistentFullBlockAnchor=" + persistentFullBlockAnchor
                + " persistentLoweredSlabCarrier=" + persistentCarrier
                + " persistentLoweredBottomSlabCarrier=" + persistentBottomCarrier
                + " slabType=" + slabType;
    }

    // ------------------------------------------------------------------
    // Structural probes for the authored-depth API gap.
    // ------------------------------------------------------------------

    /**
     * Reports the static type of the public anchor attachment. Today this
     * is {@code AttachmentType<LongOpenHashSet>} — a packed-pos set with
     * no per-position payload. The reference to
     * {@link SlabAnchorAttachment#ANCHOR_TYPE} ensures the proof would
     * fail to compile if the public attachment shape changes.
     */
    private static String describeAnchorTypeShape() {
        AttachmentType<LongOpenHashSet> anchorType = SlabAnchorAttachment.ANCHOR_TYPE;
        String typeName = anchorType == null
                ? "null"
                : anchorType.getClass().getSimpleName();
        return "AttachmentType<LongOpenHashSet>:" + typeName + ":no_per_position_payload";
    }

    /**
     * Runtime probe for any public depth/lane accessor on
     * {@link SlabAnchorAttachment}. The proof premise requires this to
     * return {@code none}; if a future slice adds an accessor (e.g.
     * {@code getAnchorDy} / {@code getCompoundLaneDy}) this probe will
     * surface it instead of silently keeping the proof RED.
     */
    private static String probeForAuthoredDepthApi() {
        StringBuilder found = new StringBuilder();
        for (Method m : SlabAnchorAttachment.class.getDeclaredMethods()) {
            String name = m.getName();
            String lower = name.toLowerCase();
            boolean returnsDouble = m.getReturnType() == double.class
                    || m.getReturnType() == Double.class;
            boolean depthish = lower.contains("dy")
                    || lower.contains("depth")
                    || lower.contains("lane");
            if (returnsDouble && depthish) {
                if (found.length() > 0) {
                    found.append(',');
                }
                found.append(name);
            }
        }
        return found.length() == 0 ? "none" : found.toString();
    }

    private static String format(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }
}
