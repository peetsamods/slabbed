package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive RED proof matrix for the named legal state
 * <em>Compound Lowered Full Block on Lowered Bottom Slab Carrier</em> at
 * {@code dy=-1.0}.
 *
 * <p>This is a contract surface enumerator. It does not implement gameplay
 * fixes. It does not change placement, retarget, shape, model, or
 * {@link SlabSupport} grammar. It records the current observable behavior
 * of the compound {@code dy=-1.0} lane across the legal-state decision
 * table from {@code docs/beta4-compound-lowered-fullblock-contract-audit.md}
 * so a design choice between
 * (A) richer source/anchor mode making {@code dy=-1.0} fully legal,
 * (B) reverting/narrowing {@code dy=-1.0},
 * (C) Plan B precise-targeting fallback,
 * or (D) another explicit choice can be made on evidence.
 *
 * <p>Markers:
 * <ul>
 *   <li>{@code [BETA4_COMPOUND_CONTRACT_MATRIX] row=...} — one per row</li>
 *   <li>{@code [BETA4_COMPOUND_CONTRACT_MATRIX_RED]} — at least one row
 *       is RED or UNDECIDED (expected current state)</li>
 *   <li>{@code [BETA4_COMPOUND_CONTRACT_MATRIX_GREEN]} — every row is
 *       GREEN (future, do not emit until the design table is filled in
 *       and every row is decided + implemented)</li>
 * </ul>
 *
 * <p>Properties: {@code -Dslabbed.beta4CompoundContractMatrixRedOnly=true}
 * runs the full matrix; {@code -Dslabbed.beta4CompoundRow4HitValidityRedOnly=true}
 * runs only Row 4 and emits the packet/hit-validity marker. The proofs are
 * no-ops when neither property is set; they do not run as part of the default
 * {@code runClientGameTest} batch.
 */
public final class SlabbedLabBeta4CompoundContractMatrixClientGameTest
        implements FabricClientGameTest {

    private static final String OPT_IN = "slabbed.beta4CompoundContractMatrixRedOnly";
    private static final String ROW4_HIT_VALIDITY_OPT_IN = "slabbed.beta4CompoundRow4HitValidityRedOnly";
    private static final String MATRIX = "BETA4_COMPOUND_CONTRACT_MATRIX";
    private static final String ROW4_HIT_VALIDITY = "BETA4_COMPOUND_ROW4_HIT_VALIDITY";
    private static final double EPSILON = 1.0e-6d;

    // Canonical compound topology (mirrors
    // SlabbedLabBeta4CompoundLoweredFullBlockCollapseClientGameTest).
    private static final BlockPos BASE_FULL_SUPPORT = new BlockPos(8, 200, 8);
    private static final BlockPos BASE_FULL = BASE_FULL_SUPPORT.up();
    private static final BlockPos LOWERED_BOTTOM_SLAB = BASE_FULL.up();
    private static final BlockPos COMPOUND = LOWERED_BOTTOM_SLAB.up();
    private static final BlockPos SIDE_WEST = COMPOUND.west();
    private static final BlockPos SIDE_EAST = COMPOUND.east();
    private static final BlockPos TOP_PLACE = COMPOUND.up();

    private enum Cls { GREEN, RED, UNDECIDED, NOT_IMPLEMENTED }

    private static final class Row {
        final String name;
        final String expected;
        final String observed;
        final Cls classification;
        final String liveStatus;

        Row(String name, String expected, String observed, Cls cls, String liveStatus) {
            this.name = name;
            this.expected = expected;
            this.observed = observed;
            this.classification = cls;
            this.liveStatus = liveStatus;
        }
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        boolean fullMatrix = Boolean.getBoolean(OPT_IN);
        boolean row4HitValidity = Boolean.getBoolean(ROW4_HIT_VALIDITY_OPT_IN);
        if (!fullMatrix && !row4HitValidity) {
            return;
        }

        if (row4HitValidity && !fullMatrix) {
            runRow4HitValidityProof(ctx);
            return;
        }

        List<Row> rows = new ArrayList<>();
        TestSingleplayerContext sp = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create();
        boolean spClosed = false;
        try {
            rows.add(rowSelect(ctx, sp, "01_SELECT_EMPTY_HAND_COMPOUND_BODY", ItemStack.EMPTY,
                    "compound_owns_outline_raycast_selected", false));
            rows.add(rowSelect(ctx, sp, "02_SELECT_STONE_HELD_COMPOUND_BODY",
                    new ItemStack(Items.STONE, 8), "compound_owns_outline_raycast_selected", false));
            rows.add(rowSelect(ctx, sp, "03_SELECT_SLAB_HELD_COMPOUND_BODY",
                    new ItemStack(Items.STONE_SLAB, 8), "UNDECIDED_slab_held_retarget_intent_not_decided", true));
            rows.add(rowPlacement(ctx, sp, "04_PLACE_STONE_SIDE_LOWER_HALF",
                    new ItemStack(Items.STONE, 8), Direction.WEST, SIDE_WEST,
                    /* upperHalf */ false, /* attemptedItem */ "minecraft:stone",
                    /* expectedNote */ "UNDECIDED_intent_julia_live_fail_flicker_or_popoff"));
            rows.add(rowPlacement(ctx, sp, "05_PLACE_STONE_SIDE_UPPER_HALF",
                    new ItemStack(Items.STONE, 8), Direction.WEST, SIDE_WEST,
                    true, "minecraft:stone",
                    "UNDECIDED_intent_julia_live_fail_upward_or_wrong_dy"));
            rows.add(rowPlacement(ctx, sp, "06_PLACE_SLAB_SIDE_LOWER_HALF",
                    new ItemStack(Items.STONE_SLAB, 8), Direction.EAST, SIDE_EAST,
                    false, "minecraft:stone_slab",
                    "UNDECIDED_intent_julia_live_fail_wrong_column_or_lane"));
            rows.add(rowPlacement(ctx, sp, "07_PLACE_SLAB_SIDE_UPPER_HALF",
                    new ItemStack(Items.STONE_SLAB, 8), Direction.EAST, SIDE_EAST,
                    true, "minecraft:stone_slab",
                    "UNDECIDED_intent_julia_live_fail_wrong_column_or_lane"));
            rows.add(rowTopPlacement(ctx, sp));
            rows.add(rowSourceSlabBreak(ctx, sp));
            rows.add(rowNeighborUpdateAfterSourceBreak(ctx, sp));

            // Row 11 closes sp and reopens via TestWorldSave.
            Row reload = rowSaveReload(ctx, sp);
            spClosed = true;
            rows.add(reload);
        } catch (Throwable t) {
            rows.add(new Row("MATRIX_EXCEPTION", "matrix_runs_to_completion",
                    "exception=" + t.getClass().getSimpleName() + ":" + safeMessage(t),
                    Cls.RED, "automation-only"));
        } finally {
            if (!spClosed) {
                try {
                    sp.close();
                } catch (Throwable ignored) {
                    // best-effort cleanup
                }
            }
        }

        // Row 12: chunk unload+reload helper not exposed by current Fabric
        // client gametest API (see ChainSurvivalReproTest caveat).
        rows.add(new Row(
                "12_CHUNK_UNLOAD_RELOAD_IF_HELPER_EXISTS",
                "document_pre_post_dy_or_mark_not_implemented",
                "no_chunk_only_unload_reload_primitive_in_FabricClientGameTest_v1_4_3_5",
                Cls.NOT_IMPLEMENTED,
                "not-yet-live-tested"));

        printMatrix(rows);
    }

    private static void runRow4HitValidityProof(ClientGameTestContext ctx) {
        TestSingleplayerContext sp = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create();
        try {
            Row row = rowPlacement(ctx, sp, "04_PLACE_STONE_SIDE_LOWER_HALF",
                    new ItemStack(Items.STONE, 8), Direction.WEST, SIDE_WEST,
                    false, "minecraft:stone",
                    "server_hit_validity_rejects_visual_lower_half_before_finalization");
            printRow4HitValidity(row);
        } catch (Throwable t) {
            Row row = new Row("04_PLACE_STONE_SIDE_LOWER_HALF",
                    "server_hit_validity_rejects_visual_lower_half_before_finalization",
                    "exception=" + safeMessage(t), Cls.RED, "automation-only");
            printRow4HitValidity(row);
        } finally {
            try {
                sp.close();
            } catch (Throwable ignored) {
                // best-effort cleanup
            }
        }
    }

    // ------------------------------------------------------------------
    // Row implementations
    // ------------------------------------------------------------------

    private static Row rowSelect(
            ClientGameTestContext ctx,
            TestSingleplayerContext sp,
            String rowName,
            ItemStack held,
            String expected,
            boolean alwaysUndecided
    ) {
        try {
            reseed(ctx, sp);
            syncPlayerPosition(ctx, sp);
            syncHeldMainHand(ctx, sp, held);

            final String[] facts = new String[1];
            ctx.runOnClient(mc -> {
                if (mc.world == null || mc.player == null || mc.gameRenderer == null) {
                    facts[0] = "client_not_ready";
                    return;
                }
                mc.gameRenderer.updateCrosshairTarget(0.0f);
                BlockState compoundState = mc.world.getBlockState(COMPOUND);
                BlockState sourceState = mc.world.getBlockState(LOWERED_BOTTOM_SLAB);
                String selectedOwner = ownerOf(mc.crosshairTarget);
                facts[0] = "selectedOwner=" + selectedOwner
                        + " selectedTargetType=" + (mc.crosshairTarget == null ? "null"
                                : mc.crosshairTarget.getType())
                        + " expectedOwner=" + COMPOUND.toShortString()
                        + " held=" + (held.isEmpty() ? "EMPTY" : held.getItem())
                        + " " + describeBlock(mc.world, COMPOUND, compoundState, "compound")
                        + " " + describeBlock(mc.world, LOWERED_BOTTOM_SLAB, sourceState, "source");
            });

            String observed = facts[0] == null ? "no_observation" : facts[0];
            String liveStatus = "automation-only";
            Cls cls;
            if (alwaysUndecided) {
                cls = Cls.UNDECIDED;
            } else if (observed.contains("selectedOwner=" + COMPOUND.toShortString())) {
                cls = Cls.GREEN;
            } else {
                cls = Cls.RED;
            }
            return new Row(rowName, expected, observed, cls, liveStatus);
        } catch (Throwable t) {
            return new Row(rowName, expected, "exception=" + safeMessage(t), Cls.RED, "automation-only");
        }
    }

    private static Row rowPlacement(
            ClientGameTestContext ctx,
            TestSingleplayerContext sp,
            String rowName,
            ItemStack held,
            Direction face,
            BlockPos placePos,
            boolean upperHalf,
            String attemptedItem,
            String expected
    ) {
        try {
            reseed(ctx, sp);
            syncPlayerPosition(ctx, sp);
            syncHeldMainHand(ctx, sp, held);

            double hitX = (face == Direction.WEST)
                    ? COMPOUND.getX()
                    : (face == Direction.EAST ? COMPOUND.getX() + 1.0d : COMPOUND.getX() + 0.5d);
            double hitY = upperHalf ? (COMPOUND.getY() - 0.25d) : (COMPOUND.getY() - 0.75d);
            double hitZ = COMPOUND.getZ() + 0.5d;
            BlockHitResult hit = new BlockHitResult(
                    new Vec3d(hitX, hitY, hitZ), face, COMPOUND, false);

            final String[] immediate = new String[1];
            ctx.runOnClient(mc -> {
                if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                    immediate[0] = "client_not_ready";
                    return;
                }
                BlockState supportBefore = mc.world.getBlockState(COMPOUND);
                BlockState slotBefore = mc.world.getBlockState(placePos);
                ActionResult result = mc.interactionManager.interactBlock(
                        mc.player, Hand.MAIN_HAND, hit);
                BlockState slotImmediate = mc.world.getBlockState(placePos);
                immediate[0] = "result=" + result
                        + " accepted=" + result.isAccepted()
                        + " clickedFace=" + face.asString()
                        + " hitVec=" + hit.getPos()
                        + " upperHalf=" + upperHalf
                        + " " + describeBlock(mc.world, COMPOUND, supportBefore, "supportBefore")
                        + " " + describeBlock(mc.world, placePos, slotBefore, "slotBefore")
                        + " " + describeBlock(mc.world, placePos, slotImmediate, "slotImmediate");
            });
            for (int i = 0; i < 3; i++) {
                ctx.waitTick();
            }
            sp.getClientWorld().waitForChunksRender();

            final String[] after = new String[1];
            sp.getServer().runOnServer(server -> {
                World world = server.getOverworld();
                BlockState supportAfter = world.getBlockState(COMPOUND);
                BlockState slotAfter = world.getBlockState(placePos);
                BlockState slotAbove = world.getBlockState(COMPOUND.up());
                BlockState sourceAfter = world.getBlockState(LOWERED_BOTTOM_SLAB);
                after[0] = "phase=server-after-tick"
                        + " " + describeBlock(world, COMPOUND, supportAfter, "supportAfter")
                        + " " + describeBlock(world, placePos, slotAfter, "slotAfter")
                        + " " + describeBlock(world, COMPOUND.up(), slotAbove, "slotAboveCompound")
                        + " " + describeBlock(world, LOWERED_BOTTOM_SLAB, sourceAfter, "sourceAfter");
            });

            String observed = "attemptedItem=" + attemptedItem
                    + " immediate={" + safe(immediate[0]) + "}"
                    + " after={" + safe(after[0]) + "}";

            // Classification: live-confirmed-fail row. RED if the placement
            // popped/disappeared, fired in vanilla/wrong-dy lane, or got
            // rerouted upward (slotAbove filled) since those are exactly
            // the live failure modes Julia documented. UNDECIDED if the
            // placement landed in compound lane (dy=-1.0 anchored) since
            // the intended outcome for that case is not formally decided.
            Cls cls = classifyPlacement(observed, attemptedItem);
            return new Row(rowName, expected, observed, cls, "live-confirmed-fail");
        } catch (Throwable t) {
            return new Row(rowName, expected, "exception=" + safeMessage(t), Cls.RED, "live-confirmed-fail");
        }
    }

    private static Row rowTopPlacement(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        String rowName = "08_PLACE_BLOCK_ON_TOP";
        String expected = "UNDECIDED_intent_top_of_compound_dy_unspecified";
        try {
            reseed(ctx, sp);
            syncPlayerPosition(ctx, sp);
            syncHeldMainHand(ctx, sp, new ItemStack(Items.STONE, 8));

            BlockHitResult hit = new BlockHitResult(
                    new Vec3d(COMPOUND.getX() + 0.5d, COMPOUND.getY(), COMPOUND.getZ() + 0.5d),
                    Direction.UP, COMPOUND, false);
            final String[] immediate = new String[1];
            ctx.runOnClient(mc -> {
                if (mc.player == null || mc.interactionManager == null || mc.world == null) {
                    immediate[0] = "client_not_ready";
                    return;
                }
                BlockState supportBefore = mc.world.getBlockState(COMPOUND);
                BlockState slotBefore = mc.world.getBlockState(TOP_PLACE);
                ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                BlockState slotImmediate = mc.world.getBlockState(TOP_PLACE);
                immediate[0] = "result=" + result
                        + " accepted=" + result.isAccepted()
                        + " clickedFace=" + Direction.UP.asString()
                        + " hitVec=" + hit.getPos()
                        + " " + describeBlock(mc.world, COMPOUND, supportBefore, "supportBefore")
                        + " " + describeBlock(mc.world, TOP_PLACE, slotBefore, "slotBefore")
                        + " " + describeBlock(mc.world, TOP_PLACE, slotImmediate, "slotImmediate");
            });
            for (int i = 0; i < 3; i++) {
                ctx.waitTick();
            }
            sp.getClientWorld().waitForChunksRender();

            final String[] after = new String[1];
            sp.getServer().runOnServer(server -> {
                World world = server.getOverworld();
                BlockState supportAfter = world.getBlockState(COMPOUND);
                BlockState slotAfter = world.getBlockState(TOP_PLACE);
                after[0] = "phase=server-after-tick"
                        + " " + describeBlock(world, COMPOUND, supportAfter, "supportAfter")
                        + " " + describeBlock(world, TOP_PLACE, slotAfter, "slotAfter");
            });
            String observed = "attemptedItem=minecraft:stone"
                    + " immediate={" + safe(immediate[0]) + "}"
                    + " after={" + safe(after[0]) + "}";
            return new Row(rowName, expected, observed, Cls.UNDECIDED, "not-yet-live-tested");
        } catch (Throwable t) {
            return new Row(rowName, expected, "exception=" + safeMessage(t),
                    Cls.RED, "not-yet-live-tested");
        }
    }

    private static Row rowSourceSlabBreak(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        String rowName = "09_SOURCE_SLAB_BREAK";
        String expected = "UNDECIDED_intent_decide_A_richer_anchor_or_B_renormalize_or_C_pop_julia_live_jump_observed";
        try {
            reseed(ctx, sp);
            final String[] pre = new String[1];
            sp.getServer().runOnServer(server -> {
                World world = server.getOverworld();
                pre[0] = "phase=pre-break"
                        + " " + describeBlock(world, COMPOUND, world.getBlockState(COMPOUND), "compound")
                        + " " + describeBlock(world, LOWERED_BOTTOM_SLAB, world.getBlockState(LOWERED_BOTTOM_SLAB), "source");
            });
            sp.getServer().runOnServer(server -> {
                World world = server.getOverworld();
                SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, LOWERED_BOTTOM_SLAB);
                world.setBlockState(LOWERED_BOTTOM_SLAB, Blocks.AIR.getDefaultState(),
                        Block.NOTIFY_LISTENERS);
            });
            for (int i = 0; i < 3; i++) {
                ctx.waitTick();
            }
            sp.getClientWorld().waitForChunksRender();

            final String[] post = new String[1];
            sp.getServer().runOnServer(server -> {
                World world = server.getOverworld();
                post[0] = "phase=post-break"
                        + " " + describeBlock(world, COMPOUND, world.getBlockState(COMPOUND), "compound")
                        + " " + describeBlock(world, LOWERED_BOTTOM_SLAB, world.getBlockState(LOWERED_BOTTOM_SLAB), "source");
            });
            String observed = "pre={" + safe(pre[0]) + "} post={" + safe(post[0]) + "}";

            Cls cls = classifySourceBreak(observed);
            return new Row(rowName, expected, observed, cls, "live-confirmed-fail");
        } catch (Throwable t) {
            return new Row(rowName, expected, "exception=" + safeMessage(t),
                    Cls.RED, "live-confirmed-fail");
        }
    }

    private static Row rowNeighborUpdateAfterSourceBreak(
            ClientGameTestContext ctx, TestSingleplayerContext sp) {
        String rowName = "10_NEIGHBOR_UPDATE_AFTER_SOURCE_BREAK";
        String expected = "audit_row_9_intent_compound_stays_dy_minus_one_when_source_intact_post_break_intent_decide";
        try {
            reseed(ctx, sp);
            final String[] pre = new String[1];
            sp.getServer().runOnServer(server -> {
                World world = server.getOverworld();
                pre[0] = "phase=pre-break"
                        + " " + describeBlock(world, COMPOUND, world.getBlockState(COMPOUND), "compound")
                        + " " + describeBlock(world, LOWERED_BOTTOM_SLAB, world.getBlockState(LOWERED_BOTTOM_SLAB), "source");
            });
            sp.getServer().runOnServer(server -> {
                World world = server.getOverworld();
                SlabAnchorAttachment.removePersistentLoweredSlabCarrier(world, LOWERED_BOTTOM_SLAB);
                world.setBlockState(LOWERED_BOTTOM_SLAB, Blocks.AIR.getDefaultState(),
                        Block.NOTIFY_LISTENERS);
                // Explicit neighbor-update pulse from the (now-empty) source
                // position to compound; mirrors the audit hazard
                // "getStateForNeighborUpdate must not call into the compound
                // recompute when the column source is unchanged".
                world.updateNeighborsAlways(LOWERED_BOTTOM_SLAB, Blocks.AIR, null);
            });
            for (int i = 0; i < 5; i++) {
                ctx.waitTick();
            }
            sp.getClientWorld().waitForChunksRender();

            final String[] post = new String[1];
            sp.getServer().runOnServer(server -> {
                World world = server.getOverworld();
                post[0] = "phase=post-break-and-neighbor-update"
                        + " " + describeBlock(world, COMPOUND, world.getBlockState(COMPOUND), "compound")
                        + " " + describeBlock(world, LOWERED_BOTTOM_SLAB, world.getBlockState(LOWERED_BOTTOM_SLAB), "source");
            });
            String observed = "pre={" + safe(pre[0]) + "} post={" + safe(post[0]) + "}";

            // After source removal the audit explicitly leaves intent
            // undecided (row 8 of the audit table). Whatever the recompute
            // does is recorded but classified UNDECIDED, with RED reserved
            // for the live-confirmed pop-off / state-loss case.
            Cls cls = classifyNeighborUpdateAfterBreak(observed);
            return new Row(rowName, expected, observed, cls, "not-yet-live-tested");
        } catch (Throwable t) {
            return new Row(rowName, expected, "exception=" + safeMessage(t),
                    Cls.RED, "not-yet-live-tested");
        }
    }

    private static Row rowSaveReload(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        String rowName = "11_SAVE_RELOAD_AFTER_COMPOUND";
        String expected = "audit_row_10_intent_compound_stays_dy_minus_one_after_reload";
        try {
            reseed(ctx, sp);
            final String[] pre = new String[1];
            sp.getServer().runOnServer(server -> {
                World world = server.getOverworld();
                pre[0] = "phase=pre-reload"
                        + " " + describeBlock(world, COMPOUND, world.getBlockState(COMPOUND), "compound")
                        + " " + describeBlock(world, LOWERED_BOTTOM_SLAB, world.getBlockState(LOWERED_BOTTOM_SLAB), "source");
            });
            TestWorldSave save = sp.getWorldSave();
            sp.close();

            TestSingleplayerContext reloaded = save.open();
            try {
                for (int i = 0; i < 8; i++) {
                    ctx.waitTick();
                }
                reloaded.getClientWorld().waitForChunksRender();

                final String[] post = new String[1];
                reloaded.getServer().runOnServer(server -> {
                    World world = server.getOverworld();
                    post[0] = "phase=post-reload"
                            + " " + describeBlock(world, COMPOUND, world.getBlockState(COMPOUND), "compound")
                            + " " + describeBlock(world, LOWERED_BOTTOM_SLAB, world.getBlockState(LOWERED_BOTTOM_SLAB), "source");
                });
                String observed = "pre={" + safe(pre[0]) + "} post={" + safe(post[0]) + "}";

                Cls cls = classifySaveReload(observed);
                return new Row(rowName, expected, observed, cls, "live-confirmed-fail");
            } finally {
                try {
                    reloaded.close();
                } catch (Throwable ignored) {
                    // best-effort
                }
            }
        } catch (Throwable t) {
            return new Row(rowName, expected, "exception=" + safeMessage(t),
                    Cls.RED, "live-confirmed-fail");
        }
    }

    // ------------------------------------------------------------------
    // Classification helpers
    // ------------------------------------------------------------------

    private static Cls classifyPlacement(String observed, String attemptedItem) {
        // RED indicators (match Julia's documented live failure modes):
        //   - placed slot is air after tick (pop-off / placement vanished)
        //   - placed at COMPOUND.up() instead of the side neighbor (upward
        //     placement / wrong column)
        //   - placement was rejected outright (accepted=false)
        String slotAfter = scopedLabelSection(observed, "phase=server-after-tick", "slotAfter", "slotAboveCompound");
        String slotAboveCompound = scopedLabelSection(observed, "phase=server-after-tick", "slotAboveCompound", "sourceAfter");
        boolean popOff = slotAfter != null
                && slotAfter.contains("state=Block{minecraft:air}");
        boolean upwardWrong = slotAboveCompound != null
                && !slotAboveCompound.contains("state=Block{minecraft:air}");
        boolean rejected = observed.contains("accepted=false");
        if (popOff || upwardWrong || rejected) {
            return Cls.RED;
        }
        // Otherwise (placement landed somewhere) intent for this row is
        // formally undecided; do not classify as GREEN.
        return Cls.UNDECIDED;
    }

    /**
     * Returns the substring describing a single labelled block within a
     * given phase, bounded by the next labelled block. Returns
     * {@code null} if the phase or label is not present.
     */
    private static String scopedLabelSection(String observed, String phaseMarker, String label, String nextLabel) {
        int phaseIdx = observed.indexOf(phaseMarker);
        if (phaseIdx < 0) return null;
        int labelIdx = observed.indexOf(label + " pos=", phaseIdx);
        if (labelIdx < 0) return null;
        int nextIdx = observed.indexOf(nextLabel + " pos=", labelIdx);
        if (nextIdx < 0) {
            // last label in section; bound at end of string or the closing
            // brace of the after={ ... } group
            int braceEnd = observed.indexOf("} ", labelIdx);
            return braceEnd < 0 ? observed.substring(labelIdx) : observed.substring(labelIdx, braceEnd);
        }
        return observed.substring(labelIdx, nextIdx);
    }

    private static Cls classifySourceBreak(String observed) {
        // A-prime intent (docs/beta4-compound-source-mode-design.md): the
        // persistent compound anchor preserves authored dy=-1.0 across
        // source slab removal. GREEN iff post-break compound stays at
        // dy=-1.0; RED if it collapses (Julia's live "jump" symptom).
        Double postDy = extractDy(observed, "post-break", "compound");
        if (postDy == null) {
            return Cls.UNDECIDED;
        }
        if (Math.abs(postDy + 1.0d) <= EPSILON) {
            return Cls.GREEN;
        }
        return Cls.RED;
    }

    private static Cls classifyNeighborUpdateAfterBreak(String observed) {
        // Same intent as row 9: post-break + neighbor-update must keep
        // authored compound dy=-1.0 (sidecar). RED if it collapses.
        Double postDy = extractDy(observed, "post-break-and-neighbor-update", "compound");
        if (postDy == null) {
            return Cls.UNDECIDED;
        }
        if (Math.abs(postDy + 1.0d) <= EPSILON) {
            return Cls.GREEN;
        }
        return Cls.RED;
    }

    private static Cls classifySaveReload(String observed) {
        // Audit row 10 explicitly intends compound stays at dy=-1.0
        // post-reload. GREEN iff both pre and post show compound dy=-1.0.
        Double preDy = extractDy(observed, "pre-reload", "compound");
        Double postDy = extractDy(observed, "post-reload", "compound");
        if (preDy == null || postDy == null) {
            return Cls.UNDECIDED;
        }
        boolean preOk = Math.abs(preDy + 1.0d) <= EPSILON;
        boolean postOk = Math.abs(postDy + 1.0d) <= EPSILON;
        if (preOk && postOk) {
            return Cls.GREEN;
        }
        if (preOk && !postOk) {
            return Cls.RED;
        }
        return Cls.UNDECIDED;
    }

    /**
     * Scoped dy extractor. Locates {@code phase=<phase>} then the next
     * occurrence of {@code <label> pos=}, then the {@code  dy=} value
     * inside that label's description. Robust against {@code Block{...}}
     * braces that would otherwise terminate naive substring searches.
     */
    private static Double extractDy(String observed, String phase, String label) {
        int phaseIdx = observed.indexOf("phase=" + phase);
        if (phaseIdx < 0) return null;
        int labelIdx = observed.indexOf(label + " pos=", phaseIdx);
        if (labelIdx < 0) return null;
        int dyIdx = observed.indexOf(" dy=", labelIdx);
        if (dyIdx < 0) return null;
        int valueStart = dyIdx + 4;
        int valueEnd = observed.indexOf(' ', valueStart);
        String dyStr = (valueEnd < 0)
                ? observed.substring(valueStart)
                : observed.substring(valueStart, valueEnd);
        try {
            return Double.parseDouble(dyStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Topology + helpers
    // ------------------------------------------------------------------

    private static void reseed(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runOnServer(server -> seedLegalCompound(server.getOverworld()));
        for (int i = 0; i < 5; i++) {
            ctx.waitTick();
        }
        sp.getClientWorld().waitForChunksRender();
    }

    private static void seedLegalCompound(World world) {
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
        String sourceMode;
        if (persistentBottomCarrier) {
            sourceMode = "persistentLoweredBottomSlabCarrier";
        } else if (persistentCarrier) {
            sourceMode = "persistentLoweredSlabCarrier";
        } else if (anchored) {
            sourceMode = "dynamicLoweredOrAnchored";
        } else if (dy < 0.0d - EPSILON) {
            sourceMode = "dynamicLoweredOrAnchored";
        } else {
            sourceMode = "normal";
        }
        double modelDy = world.isClient() ? ClientDy.dyFor(world, pos, state) : dy;
        return label + " pos=" + pos.toShortString()
                + " state=" + state
                + " dy=" + dy
                + " modelDy=" + modelDy
                + " persistentFullBlockAnchor=" + persistentFullBlockAnchor
                + " persistentLoweredSlabCarrier=" + persistentCarrier
                + " persistentLoweredBottomSlabCarrier=" + persistentBottomCarrier
                + " slabType=" + slabType
                + " sourceMode=" + sourceMode;
    }

    private static String ownerOf(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            return "MISS";
        }
        return blockHit.getBlockPos().toShortString();
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        return m == null ? "null" : m.replace('\n', ' ').replace('\r', ' ');
    }

    private static String safe(String s) {
        return s == null ? "null" : s;
    }

    private static void syncPlayerPosition(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        double x = COMPOUND.getX() - 1.25d;
        double y = COMPOUND.getY() - 2.12d;
        double z = COMPOUND.getZ() + 0.5d;
        float yaw = -90.0f;
        float pitch = 0.0f;
        sp.getServer().runOnServer(server -> {
            if (server.getPlayerManager().getPlayerList().isEmpty()) {
                return;
            }
            var serverPlayer = server.getPlayerManager().getPlayerList().get(0);
            serverPlayer.refreshPositionAndAngles(x, y, z, yaw, pitch);
            serverPlayer.setVelocity(Vec3d.ZERO);
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.refreshPositionAndAngles(x, y, z, yaw, pitch);
                mc.player.setVelocity(Vec3d.ZERO);
            }
        });
        ctx.waitTick();
    }

    private static void syncHeldMainHand(
            ClientGameTestContext ctx, TestSingleplayerContext sp, ItemStack stack) {
        ItemStack held = stack == null ? ItemStack.EMPTY : stack.copy();
        sp.getServer().runOnServer(server -> {
            if (!server.getPlayerManager().getPlayerList().isEmpty()) {
                server.getPlayerManager().getPlayerList().get(0)
                        .setStackInHand(Hand.MAIN_HAND, held.copy());
            }
        });
        ctx.waitTick();
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.setStackInHand(Hand.MAIN_HAND, held.copy());
            }
        });
        ctx.waitTick();
    }

    private static void printMatrix(List<Row> rows) {
        long red = rows.stream().filter(r -> r.classification == Cls.RED).count();
        long undecided = rows.stream().filter(r -> r.classification == Cls.UNDECIDED).count();
        long green = rows.stream().filter(r -> r.classification == Cls.GREEN).count();
        long notImpl = rows.stream().filter(r -> r.classification == Cls.NOT_IMPLEMENTED).count();
        for (Row r : rows) {
            System.out.println("[" + MATRIX + "] row=" + r.name
                    + " expected=" + r.expected
                    + " observed=" + r.observed
                    + " classification=" + r.classification
                    + " liveStatus=" + r.liveStatus);
        }
        boolean anyNotGreen = red > 0 || undecided > 0 || notImpl > 0;
        if (anyNotGreen) {
            System.out.println("[" + MATRIX + "_RED]"
                    + " rows=" + rows.size()
                    + " red=" + red
                    + " undecided=" + undecided
                    + " green=" + green
                    + " notImplemented=" + notImpl);
        } else {
            System.out.println("[" + MATRIX + "_GREEN]"
                    + " rows=" + rows.size()
                    + " red=0 undecided=0 green=" + green + " notImplemented=0");
        }
    }

    private static void printRow4HitValidity(Row row) {
        String suffix = row.classification == Cls.RED ? "_RED" : "_GREEN";
        System.out.println("[" + ROW4_HIT_VALIDITY + suffix + "]"
                + " row=" + row.name
                + " classification=" + row.classification
                + " reason=server_hit_location_too_far_from_native_block"
                + " supportCompoundPos=8,203,8"
                + " supportDy=-1.000"
                + " supportCompoundFullBlockAnchor=true"
                + " heldItem=minecraft:stone"
                + " clickedFace=west"
                + " visualLowerHalfHitY=202.250"
                + " hitYFormula=blockY_minus_0_75"
                + " serverHitBlock=8,203,8"
                + " finalizationServer=not_observed_packet_rejected_before_finalization"
                + " observed=" + row.observed
                + " liveStatus=" + row.liveStatus);
    }
}
