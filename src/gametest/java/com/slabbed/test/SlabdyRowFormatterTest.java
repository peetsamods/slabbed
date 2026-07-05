package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.SlabdyRowFormatter;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Headless field-parity + double-offset guard for {@link SlabdyRowFormatter} (the always-shipped
 * {@code /slabdy row} / passive-overlay formatter). Proves every reconciled field is emitted and,
 * critically, that the outline field reflects the dy offset EXACTLY ONCE — the outline shape from
 * {@code getOutlineShape} is already dy-shifted by {@code SlabSupportStateMixin.slabbed$offsetOutline},
 * so the formatter must NOT re-apply it (the sibling's real double-offset bug, kept correct here by
 * construction).
 */
public final class SlabdyRowFormatterTest {

    private static String joined(List<String> lines) {
        return String.join("\n", lines);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void emitsEveryReconciledField(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        List<String> lines = SlabdyRowFormatter.formatRow(
                w, pos, w.getBlockState(pos), Direction.UP, Vec3d.ofCenter(pos),
                new ItemStack(Blocks.OAK_SLAB), "missing", null);
        String out = joined(lines);

        for (String field : new String[]{
                "target=", "owner=", "dy=", "src=", "face=", "half=", "hit=", "local=",
                "outline=", "outlineMinY=", "outlineMaxY=", "modelTrace=", "modelTraceArmed=",
                "held=", "expectedPlace=", "below="}) {
            ctx.assertTrue(out.contains(field), "row must contain field '" + field + "', got:\n" + out);
        }
        // Held item and expected-place position surfaced.
        ctx.assertTrue(out.contains("minecraft:oak_slab"), "held oak slab item id must appear, got:\n" + out);
        ctx.assertTrue(out.contains(pos.up().toShortString()),
                "expectedPlace (UP face) must be the block above, got:\n" + out);
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void loweredBottomSlabOutlineReflectsDyExactlyOnce(TestContext ctx) {
        // A bottom slab resting on a lowered full-block support lowers to -0.5. The outline base is
        // 0, so a correctly single-offset outlineMinY is -0.500. A double-offset would read -1.000.
        ServerWorld w = ctx.getWorld();
        BlockPos support = buildLoweredFullBlockSupport(w, ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 3, 3));
        BlockPos slabPos = support.up();
        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);

        double dy = SlabSupport.getYOffset(w, slabPos, w.getBlockState(slabPos));
        ctx.assertTrue(Math.abs(dy - (-0.5)) < 1.0e-6,
                "setup: the bottom slab over a lowered support must read dy=-0.5, got " + dy);

        List<String> lines = SlabdyRowFormatter.formatRow(
                w, slabPos, w.getBlockState(slabPos), Direction.UP, Vec3d.ofCenter(slabPos),
                ItemStack.EMPTY, "missing", null);
        String out = joined(lines);

        // The outline field must show minY -0.500 (single offset), NEVER -1.000 (double offset).
        ctx.assertTrue(out.contains("outlineMinY=-0.500"),
                "outline must reflect dy exactly once (outlineMinY=-0.500), got:\n" + out);
        ctx.assertTrue(!out.contains("outlineMinY=-1.000"),
                "outline must NOT be double-offset (outlineMinY=-1.000 would be the double-offset bug), got:\n" + out);
        ctx.assertTrue(out.contains("dy=-0.500 LOWERED"),
                "the dy field must report -0.500 LOWERED, got:\n" + out);
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void srcFieldReportsAnchorClassification(TestContext ctx) {
        // A flat stone on the ground: dy 0, not anchored, not frozen → src must be "-".
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        List<String> lines = SlabdyRowFormatter.formatRow(
                w, pos, w.getBlockState(pos), Direction.UP, Vec3d.ofCenter(pos), ItemStack.EMPTY, "missing", null);
        String out = joined(lines);
        ctx.assertTrue(out.contains("dy=0.000 flush"), "flat stone reports flush dy, got:\n" + out);
        ctx.assertTrue(out.contains("src=-"), "unanchored flat stone reports src=-, got:\n" + out);
        ctx.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void cacheLineDocumentedAsNotApplicable(TestContext ctx) {
        // The cache: line is scoped out on 1.21.1 (no client visual-dy cache). The explanation must
        // say so, and the row must NOT contain a fabricated cache line.
        ServerWorld w = ctx.getWorld();
        BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(3, 2, 3);
        w.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
        List<String> lines = SlabdyRowFormatter.formatRow(
                w, pos, w.getBlockState(pos), Direction.UP, Vec3d.ofCenter(pos), ItemStack.EMPTY, "missing", null);
        for (String line : lines) {
            ctx.assertTrue(!line.trim().startsWith("cache:"),
                    "no fabricated cache: line may appear on 1.21.1, got: " + line);
        }
        String why = SlabdyRowFormatter.cacheLineExplanation();
        ctx.assertTrue(why.contains("n/a") && why.contains("no client-side visual-dy cache"),
                "cacheLineExplanation must document why the cache line is inapplicable, got: " + why);
        ctx.complete();
    }

    /**
     * Anchor a lowered full-block support (private local copy of
     * {@code DecorativeObjectSupportAnchorTest}'s helper, matching this suite's per-file-copy
     * convention): a dirt block gets a persistent anchor while resting on a bottom slab, so it reads
     * dy=-0.5, and anything resting on it inherits the lowering.
     */
    private static BlockPos buildLoweredFullBlockSupport(ServerWorld w, BlockPos base) {
        BlockPos slabPos = base;
        BlockPos supportPos = slabPos.up();
        w.setBlockState(slabPos, Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_LISTENERS);
        w.setBlockState(supportPos, Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
        SlabAnchorAttachment.addAnchor(w, supportPos, w.getBlockState(supportPos));
        return supportPos;
    }
}
