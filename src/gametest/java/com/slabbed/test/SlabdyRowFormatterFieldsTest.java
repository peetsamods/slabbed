package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabdyRowFormatter;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Phase 6 (Part 2) — field-parity coverage for {@link SlabdyRowFormatter}, the headless formatter the
 * {@code /slabdev debug} target-dy overlay ({@code TargetDyOverlay}) now delegates its line-building to.
 *
 * <p>This pins the fields brought up to parity with the 1.21.11 sibling's {@code /slabdy} row dump —
 * {@code hit=}, {@code local=}, {@code outline=}/{@code outlineMinY=}/{@code outlineMaxY=}, {@code held=},
 * {@code expectedPlace=}, and the {@code below=} line — so a future terse re-cut of the overlay (like the
 * 3-line version this replaced) is caught RED here rather than silently dropping the extra diagnostics.
 * Because the formatter has no client-only dependency, the exact strings a player sees on the HUD are the
 * exact strings this gametest asserts.
 *
 * <p>RED proof: each new field is asserted with a precisely-known value in a real lowered scene. If the
 * formatter reverts to the old 3-line shape (no {@code hit}/{@code local}/{@code outline}/{@code held}/
 * {@code expectedPlace}/{@code below}), every one of the field assertions below fails — the added lines
 * are load-bearing, not decorative.
 */
public final class SlabdyRowFormatterFieldsTest {

    private static final double EPS = 1.0e-6;

    private static String line(List<String> lines, String needle, GameTestHelper helper, BlockPos where) {
        for (String l : lines) {
            if (l.contains(needle)) {
                return l;
            }
        }
        throw helper.assertionException(helper.relativePos(where),
                "slabdy row is missing the '" + needle + "' field — got:\n" + String.join("\n", lines));
    }

    private static void mustContain(String haystack, String needle, GameTestHelper helper, BlockPos where) {
        if (!haystack.contains(needle)) {
            throw helper.assertionException(helper.relativePos(where),
                    "expected the slabdy row to contain \"" + needle + "\", got: " + haystack);
        }
    }

    /**
     * An anchored full block reading -0.5 (this branch's simplest, most reliable "own dy is lowered"
     * target — {@code addAnchor} makes a full block read -0.5 for its own {@code getYOffset}), targeted
     * on its UP face with a stone item held. Asserts every field parity-added this phase resolves to its
     * known value. (A bottom slab is NOT used as the target: a fresh bottom slab's OWN dy is 0.0 — it
     * only lowers objects resting ON it — so it would not exercise the LOWERED path.)
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void loweredTargetRowCarriesAllParityFields(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos below = helper.absolutePos(new BlockPos(2, 1, 2));  // a plain slab beneath
        BlockPos target = helper.absolutePos(new BlockPos(2, 2, 2)); // the anchored -0.5 target
        BlockPos above = helper.absolutePos(new BlockPos(2, 3, 2));  // pos.relative(UP) == expectedPlace

        w.setBlock(below, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
        w.setBlock(target, Blocks.DIRT.defaultBlockState(), 2);
        SlabAnchorAttachment.addAnchor(w, target, w.getBlockState(target));

        BlockState targetState = w.getBlockState(target);
        double dy = SlabSupport.getYOffset(w, target, targetState);
        if (Math.abs(dy + 0.5) > EPS) {
            throw helper.assertionException(helper.relativePos(target),
                    "setup: the anchored target must read -0.5 (got " + dy + ") so the row shows LOWERED");
        }

        // A concrete, non-trivial hit somewhere on the target's top face.
        Vec3 hit = new Vec3(target.getX() + 0.25, target.getY() + 0.5, target.getZ() + 0.75);
        ItemStack held = new ItemStack(Items.STONE);

        List<String> lines = SlabdyRowFormatter.formatRow(w, target, targetState, Direction.UP, hit, held);

        // target line: pos + block id.
        String targetLine = line(lines, "[slabdy] target=", helper, target);
        mustContain(targetLine, target.toShortString(), helper, target);
        mustContain(targetLine, "minecraft:dirt", helper, target);

        // dy/status/src line: LOWERED with a negative dy, classified ANCHORED.
        String dyLine = line(lines, "dy=", helper, target);
        mustContain(dyLine, "VANILLA", helper, target);
        mustContain(dyLine, "dy=-0.500 LOWERED", helper, target);
        mustContain(dyLine, "src=ANCHORED", helper, target);

        // face/half/hit/local line — the parity-added hit and local vectors.
        String faceLine = line(lines, "face=", helper, target);
        mustContain(faceLine, "face=up", helper, target);
        mustContain(faceLine, "hit=" + String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f", hit.x, hit.y, hit.z),
                helper, target);
        mustContain(faceLine, "local=0.250,", helper, target); // hit.x - pos.x == 0.25 regardless of absolute pos

        // outline line — the parity-added box + min/max Y.
        String outlineLine = line(lines, "outline=", helper, target);
        mustContain(outlineLine, "outlineMinY=", helper, target);
        mustContain(outlineLine, "outlineMaxY=", helper, target);
        // A full block's outline is NOT empty.
        if (outlineLine.contains("outline=empty")) {
            throw helper.assertionException(helper.relativePos(target),
                    "the target's outline must be non-empty: " + outlineLine);
        }

        // held / expectedPlace line — the parity-added held item + the neighbour the click would place into.
        String heldLine = line(lines, "held=", helper, target);
        mustContain(heldLine, "held=minecraft:stone", helper, target);
        mustContain(heldLine, "expectedPlace=" + above.toShortString(), helper, target);

        // below line — the parity-added support readout (the plain slab beneath).
        String belowLine = line(lines, "below=", helper, target);
        mustContain(belowLine, "below=" + below.toShortString(), helper, target);
        mustContain(belowLine, "minecraft:stone_slab", helper, target);

        helper.succeed();
    }

    /**
     * Degenerate inputs (no face, no hit, empty hand) must still produce every parity line without NPE,
     * with the documented fallbacks ({@code face=none}, {@code hit=none}, {@code held=empty},
     * {@code expectedPlace=none}). Guards the null-handling the overlay relies on when the crosshair data
     * is incomplete.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void degenerateInputsStillCarryEveryLineWithFallbacks(GameTestHelper helper) {
        ServerLevel w = helper.getLevel();
        BlockPos stone = helper.absolutePos(new BlockPos(2, 1, 2));
        w.setBlock(stone, Blocks.STONE.defaultBlockState(), 2);
        BlockState state = w.getBlockState(stone);

        List<String> lines = SlabdyRowFormatter.formatRow(w, stone, state, null, null, ItemStack.EMPTY);

        String faceLine = line(lines, "face=", helper, stone);
        mustContain(faceLine, "face=none", helper, stone);
        mustContain(faceLine, "hit=none", helper, stone);
        mustContain(faceLine, "local=none", helper, stone);

        String heldLine = line(lines, "held=", helper, stone);
        mustContain(heldLine, "held=empty", helper, stone);
        mustContain(heldLine, "expectedPlace=none", helper, stone);

        // below line still present even with degenerate target inputs.
        line(lines, "below=", helper, stone);

        helper.succeed();
    }
}
