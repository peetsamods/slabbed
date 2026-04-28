package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client proof for update-jump behavior in slab-supported vertical full-block chains.
 */
public final class SlabbedLabSbMixedStackBreakClientGameTest implements FabricClientGameTest {
    private static final BlockPos FIXTURE_ORIGIN = new BlockPos(0, 200, 0);
    private static final double EPSILON = 1.0e-6;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext singleplayer = ctx.worldBuilder()
                .setUseConsistentSettings(true)
                .create()) {
            Path screenshotDir = SlabbedLabClientGameTest.resolveClientGameTestScreenshotDir();
            Set<String> knownScreenshotFiles = SlabbedLabClientGameTest.listScreenshotFileNames(screenshotDir);
            singleplayer.getClientWorld().waitForChunksRender();
            runProof(ctx, singleplayer, screenshotDir, knownScreenshotFiles);
        }
    }

    private static void runProof(
            ClientGameTestContext ctx,
            TestSingleplayerContext singleplayer,
            Path screenshotDir,
            Set<String> knownScreenshotFiles
    ) {
        List<CaseDef> cases = List.of(
                new CaseDef(
                        "same_material_sa_ba_ba2_ba3_break_ba2",
                        FIXTURE_ORIGIN,
                        Blocks.STONE_SLAB,
                        Blocks.STONE,
                        Blocks.STONE,
                        Blocks.STONE,
                        "Sa + Ba + Ba2 + Ba3",
                        "Ba2"),
                new CaseDef(
                        "mixed_material_sa_bb_bc_bb2_break_bc",
                        FIXTURE_ORIGIN.add(4, 0, 0),
                        Blocks.STONE_SLAB,
                        Blocks.OAK_PLANKS,
                        Blocks.STONE,
                        Blocks.OAK_PLANKS,
                        "Sa + Bb + Bc + Bb2",
                        "Bc"),
                new CaseDef(
                        "mixed_tail_sa_ba_bb_bc_break_bb",
                        FIXTURE_ORIGIN.add(8, 0, 0),
                        Blocks.STONE_SLAB,
                        Blocks.STONE,
                        Blocks.OAK_PLANKS,
                        Blocks.DEEPSLATE,
                        "Sa + Ba + Bb + Bc",
                        "Bb"));
        List<CaseResult> results = new ArrayList<>();

        for (CaseDef def : cases) {
            results.add(runCase(ctx, singleplayer, def));
        }

        ctx.takeScreenshot("sb_material_matrix_update_jump_result");
        singleplayer.getClientWorld().waitForChunksRender();

        List<SlabbedLabClientGameTest.NoteField> fields = new ArrayList<>();
        for (CaseResult result : results) {
            result.appendFields(fields);
        }
        boolean pass = results.stream().allMatch(CaseResult::green);
        SlabbedLabClientGameTest.writeInvariantProofNotes(
                screenshotDir,
                "sb_material_matrix_update_jump_notes.json",
                "sb_material_matrix_update_jump",
                "SB material-matrix update-jump proof",
                "After breaking an intermediate full block, the observed upper full block must not jump upward, "
                        + "visually intersect the wrong space, or lose its full outline/hitbox.",
                "sb_material_matrix_update_jump_result",
                "sb_material_matrix_update_jump_result",
                fields,
                pass);

        if (!pass) {
            throw new RuntimeException("[sb_material_matrix_update_jump] " + summarizeRed(results));
        }
    }

    private static CaseResult runCase(ClientGameTestContext ctx, TestSingleplayerContext singleplayer, CaseDef def) {
        BlockPos sPos = def.sPos();
        BlockPos b1Pos = sPos.up();
        BlockPos b2Pos = b1Pos.up();
        BlockPos b3Pos = b2Pos.up();

        singleplayer.getServer().runOnServer(server -> {
            var world = server.getOverworld();
            world.setBlockState(sPos, def.slab().getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(b1Pos, def.b1().getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(b2Pos, def.b2().getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(b3Pos, def.b3().getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(world, b1Pos, world.getBlockState(b1Pos));
            SlabAnchorAttachment.addAnchor(world, b2Pos, world.getBlockState(b2Pos));
            SlabAnchorAttachment.addAnchor(world, b3Pos, world.getBlockState(b3Pos));
        });
        for (int i = 0; i < 4; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();

        Sample before = capture(ctx, def.id() + "_before", sPos, b1Pos, b2Pos, b3Pos);

        singleplayer.getServer().runOnServer(server -> server.getOverworld().breakBlock(b2Pos, false));
        Sample after0 = capture(ctx, def.id() + "_after0", sPos, b1Pos, b2Pos, b3Pos);
        ctx.waitTick();
        singleplayer.getClientWorld().waitForChunksRender();
        Sample after1 = capture(ctx, def.id() + "_after1", sPos, b1Pos, b2Pos, b3Pos);
        for (int i = 0; i < 5; i++) {
            ctx.waitTick();
        }
        singleplayer.getClientWorld().waitForChunksRender();
        Sample after6 = capture(ctx, def.id() + "_after6", sPos, b1Pos, b2Pos, b3Pos);

        return CaseResult.from(def, sPos, b1Pos, b2Pos, b3Pos, before, after0, after1, after6);
    }

    private static Sample capture(ClientGameTestContext ctx, String label, BlockPos sPos, BlockPos b1Pos,
                                  BlockPos b2Pos, BlockPos b3Pos) {
        AtomicReference<Sample> out = new AtomicReference<>(Sample.empty(label));
        ctx.runOnClient(mc -> {
            BoxSample s = sample(mc.world, sPos);
            BoxSample b1 = sample(mc.world, b1Pos);
            BoxSample b2 = sample(mc.world, b2Pos);
            BoxSample b3 = sample(mc.world, b3Pos);
            boolean b3IntersectsBrokenB2Space = b3.intersectsBlockSpace(b2Pos);
            boolean b3IntersectsNonAirNeighbor = b3.overlaps(b2) || b3.overlaps(b1);
            out.set(new Sample(label, s, b1, b2, b3, b3IntersectsBrokenB2Space, b3IntersectsNonAirNeighbor));
        });
        return out.get();
    }

    private static BoxSample sample(net.minecraft.world.BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        double dy = SlabSupport.getYOffset(world, pos, state);
        boolean anchored = SlabAnchorAttachment.isAnchored(world, pos);
        VoxelShape outline = state.getOutlineShape(world, pos, ShapeContext.absent());
        boolean outlineEmpty = outline.isEmpty();
        double minY = pos.getY() + (outlineEmpty ? 0.0 : outline.getBoundingBox().minY);
        double maxY = pos.getY() + (outlineEmpty ? 0.0 : outline.getBoundingBox().maxY);
        double height = outlineEmpty ? 0.0 : maxY - minY;
        boolean limited = !state.isAir() && Math.abs(height - 1.0) > EPSILON;
        return new BoxSample(pos.toShortString(), state.toString(), dy, anchored, minY, maxY, height,
                !state.isAir(), limited);
    }

    private static String summarizeRed(List<CaseResult> results) {
        StringBuilder sb = new StringBuilder("RED cases:");
        for (CaseResult result : results) {
            if (!result.green()) {
                sb.append(' ')
                        .append(result.def().id())
                        .append(" verdict=")
                        .append(result.verdict())
                        .append(" jumpDelta=")
                        .append(result.jumpDelta());
            }
        }
        return sb.toString();
    }

    private record CaseDef(String id, BlockPos sPos, Block slab, Block b1, Block b2, Block b3,
                           String materialSequence, String brokenBlockName) {
    }

    private record CaseResult(
            CaseDef def,
            BlockPos sPos,
            BlockPos b1Pos,
            BlockPos b2Pos,
            BlockPos b3Pos,
            Sample before,
            Sample after0,
            Sample after1,
            Sample after6,
            double jumpDelta,
            double dyDelta,
            boolean upwardJump,
            boolean brokenSpaceOverlap,
            boolean neighborIntersection,
            boolean limitedOutline,
            String verdict
    ) {
        static CaseResult from(CaseDef def, BlockPos sPos, BlockPos b1Pos, BlockPos b2Pos, BlockPos b3Pos,
                               Sample before, Sample after0, Sample after1, Sample after6) {
            double maxAfterMinY = Math.max(after0.b3().minY(), Math.max(after1.b3().minY(), after6.b3().minY()));
            double maxAfterDy = Math.max(after0.b3().dy(), Math.max(after1.b3().dy(), after6.b3().dy()));
            double jumpDelta = maxAfterMinY - before.b3().minY();
            double dyDelta = maxAfterDy - before.b3().dy();
            boolean setupOk = before.s().state().contains(def.slab().getTranslationKey().replace("block.minecraft.", ""))
                    && before.b1().state().contains(blockStateName(def.b1()))
                    && before.b2().state().contains(blockStateName(def.b2()))
                    && before.b3().state().contains(blockStateName(def.b3()))
                    && after6.b2().state().contains("air");
            boolean upwardJump = jumpDelta > EPSILON || dyDelta > EPSILON;
            boolean brokenSpaceOverlap = after0.b3IntersectsBrokenB2Space()
                    || after1.b3IntersectsBrokenB2Space()
                    || after6.b3IntersectsBrokenB2Space();
            boolean neighborIntersection = after0.b3IntersectsNonAirNeighbor()
                    || after1.b3IntersectsNonAirNeighbor()
                    || after6.b3IntersectsNonAirNeighbor();
            boolean limitedOutline = after0.b3().limitedOutline()
                    || after1.b3().limitedOutline()
                    || after6.b3().limitedOutline();
            String verdict;
            if (!setupOk) {
                verdict = "BLOCKED: setup states did not match expected material sequence";
            } else if (upwardJump) {
                verdict = "RED: upper observed block jumped upward after intermediate break";
            } else if (neighborIntersection) {
                verdict = "RED: upper observed block intersected a non-air neighbor after intermediate break";
            } else if (limitedOutline) {
                verdict = "RED: upper observed block lost full expected outline/hitbox after intermediate break";
            } else {
                verdict = "GREEN: no upper jump, wrong-space intersection, or limited outline after intermediate break";
            }
            return new CaseResult(def, sPos, b1Pos, b2Pos, b3Pos, before, after0, after1, after6,
                    jumpDelta, dyDelta, upwardJump, brokenSpaceOverlap, neighborIntersection, limitedOutline, verdict);
        }

        boolean green() {
            return verdict.startsWith("GREEN");
        }

        void appendFields(List<SlabbedLabClientGameTest.NoteField> fields) {
            String prefix = def.id() + ".";
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "caseId", def.id()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "materialSequence", def.materialSequence()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "sPos", sPos.toShortString()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "b1Pos", b1Pos.toShortString()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "b2IntermediatePos", b2Pos.toShortString()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "b3ObservedPos", b3Pos.toShortString()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "statesBeforeBreak", before.describe()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "dyBeforeBreak",
                    "b1=" + before.b1().dy() + " b2=" + before.b2().dy() + " b3=" + before.b3().dy()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "outlineBeforeBreak",
                    "b1=" + before.b1().yRange() + " b2=" + before.b2().yRange()
                            + " b3=" + before.b3().yRange()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "brokenBlock", def.brokenBlockName()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "statesAfterBreak",
                    "after0={" + after0.describe() + "} after1={" + after1.describe()
                            + "} after6={" + after6.describe() + "}"));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "dyAfterBreakUpperObserved",
                    "after0=" + after0.b3().dy() + " after1=" + after1.b3().dy()
                            + " after6=" + after6.b3().dy()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "outlineAfterBreakUpperObserved",
                    "after0=" + after0.b3().yRange() + " after1=" + after1.b3().yRange()
                            + " after6=" + after6.b3().yRange()));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "jumpDelta", Double.toString(jumpDelta)));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "dyDelta", Double.toString(dyDelta)));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "intersectionCheckAgainstBrokenSpace",
                    Boolean.toString(brokenSpaceOverlap)));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "intersectionCheckAgainstNonAirNeighbor",
                    Boolean.toString(neighborIntersection)));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "limitedOrMismatchedOutline",
                    Boolean.toString(limitedOutline)));
            fields.add(new SlabbedLabClientGameTest.NoteField(prefix + "verdict", verdict));
        }
    }

    private record Sample(String label, BoxSample s, BoxSample b1, BoxSample b2, BoxSample b3,
                          boolean b3IntersectsBrokenB2Space, boolean b3IntersectsNonAirNeighbor) {
        static Sample empty(String label) {
            BoxSample empty = new BoxSample("none", "none", 0.0, false, 0.0, 0.0, 0.0, false, false);
            return new Sample(label, empty, empty, empty, empty, false, false);
        }

        String describe() {
            return label
                    + " s={" + s.describe() + "}"
                    + " b1={" + b1.describe() + "}"
                    + " b2={" + b2.describe() + "}"
                    + " b3={" + b3.describe() + "}"
                    + " b3IntersectsBrokenB2Space=" + b3IntersectsBrokenB2Space
                    + " b3IntersectsNonAirNeighbor=" + b3IntersectsNonAirNeighbor;
        }
    }

    private record BoxSample(String pos, String state, double dy, boolean anchored, double minY, double maxY,
                             double height, boolean nonAir, boolean limitedOutline) {
        boolean intersectsBlockSpace(BlockPos blockPos) {
            return nonAir && minY < blockPos.getY() + 1.0 && blockPos.getY() < maxY;
        }

        boolean overlaps(BoxSample other) {
            return nonAir && other.nonAir() && minY < other.maxY() && other.minY() < maxY;
        }

        String yRange() {
            return String.format("%.3f..%.3f", minY, maxY);
        }

        String describe() {
            return "pos=" + pos
                    + " state=" + state
                    + " dy=" + dy
                    + " anchored=" + anchored
                    + " visualY=" + yRange()
                    + " height=" + height
                    + " limitedOutline=" + limitedOutline;
        }
    }

    private static String blockStateName(Block block) {
        return block.getTranslationKey().replace("block.minecraft.", "");
    }
}
