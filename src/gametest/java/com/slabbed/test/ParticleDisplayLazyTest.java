package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.particle.BlockDisplayParticleContext;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import java.util.Map;

/** Guards lazy lookup, memoization, nested ownership and exceptional cleanup. */
public final class ParticleDisplayLazyTest {
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void silentTicksNeverResolveHeight(TestContext context) {
        BlockPos pos = context.getAbsolutePos(new BlockPos(2, 4, 2));
        ParticleOffsetLookupProbe.begin();
        try {
            for (int i = 0; i < 1334; i++) {
                BlockDisplayParticleContext.runBlockDisplayTick(Blocks.STONE,
                        Blocks.STONE.getDefaultState(), context.getWorld(), pos, Random.create(i));
            }
            context.assertTrue(ParticleOffsetLookupProbe.count() == 0,
                    "silent display ticks must perform zero height lookups, got " + ParticleOffsetLookupProbe.count());
        } finally { ParticleOffsetLookupProbe.end(); }
        context.complete();
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void repeatedEmissionResolvesOnce(TestContext context) {
        atDepth(context, -3.0, pos -> {
            ParticleOffsetLookupProbe.begin();
            try {
                run(context, pos, () -> {
                    context.assertTrue(BlockDisplayParticleContext.isActive(), "scope must own emission hooks");
                    context.assertTrue(ParticleOffsetLookupProbe.count() == 0, "opening/checking scope must not resolve height");
                    for (int i = 0; i < 8; i++)
                        context.assertTrue(BlockDisplayParticleContext.translateActiveY(10.0) == 7.0, "every emission moves once");
                    context.assertTrue(ParticleOffsetLookupProbe.count() == 1, "one lookup per emitting scope");
                });
                context.assertTrue(!BlockDisplayParticleContext.isActive(), "scope must close");
            } finally { ParticleOffsetLookupProbe.end(); }
        });
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void flatScopePreservesNegativeZero(TestContext context) {
        atDepth(context, 0.0, pos -> {
            run(context, pos, () -> context.assertTrue(
                    Double.doubleToRawLongBits(BlockDisplayParticleContext.translateActiveY(-0.0))
                            == Double.doubleToRawLongBits(-0.0), "zero offset must be bit-identical"));
        });
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void nestedFlatScopeDoesNotBorrowOuterHeight(TestContext context) {
        atDepth(context, -3.0, pos -> {
            BlockPos flat = pos.east();
            context.getWorld().setBlockState(flat, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            run(context, pos, () -> {
                context.assertTrue(BlockDisplayParticleContext.translateActiveY(10) == 7, "outer offset");
                run(context, flat, () -> context.assertTrue(BlockDisplayParticleContext.translateActiveY(10) == 10, "inner flat offset"));
                context.assertTrue(BlockDisplayParticleContext.translateActiveY(10) == 7, "outer offset restored");
            });
        });
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void exceptionsRestoreOuterScopeAndClearTopLevel(TestContext context) {
        atDepth(context, -3.0, pos -> {
            RuntimeException expected = new RuntimeException("expected display tick failure");
            run(context, pos, () -> {
                boolean caught = false;
                try { run(context, pos.east(), () -> { throw expected; }); }
                catch (RuntimeException thrown) { if (thrown != expected) throw thrown; caught = true; }
                context.assertTrue(caught, "nested exception must propagate");
                context.assertTrue(BlockDisplayParticleContext.translateActiveY(10) == 7, "outer survives nested failure");
            });
            boolean caught = false;
            try { run(context, pos, () -> { throw expected; }); }
            catch (RuntimeException thrown) { if (thrown != expected) throw thrown; caught = true; }
            context.assertTrue(caught, "top-level exception must propagate");
            context.assertTrue(!BlockDisplayParticleContext.isActive(), "failure must clear scope");
            context.assertTrue(BlockDisplayParticleContext.translateActiveY(10) == 10, "outside scope is unchanged");
        });
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void reusedFrameClearsStateAndSnapshotsMutablePosition(TestContext context) {
        atDepth(context, -3.0, pos -> {
            BlockPos flat = pos.east();
            context.getWorld().setBlockState(flat, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            BlockPos.Mutable cursor = pos.mutableCopy();
            BlockPos[] retained = new BlockPos[1];
            run(context, cursor, () -> {
                cursor.set(flat);
                context.assertTrue(BlockDisplayParticleContext.translateActiveY(10) == 7, "scope snapshots entry position");
                retained[0] = BlockDisplayParticleContext.activeOwner();
            });
            run(context, flat, () -> {
                context.assertTrue(BlockDisplayParticleContext.activeTranslationCalls() == 0, "reuse resets observations");
                context.assertTrue(Double.isNaN(BlockDisplayParticleContext.activeSourceY()), "reuse resets source Y");
                context.assertTrue(BlockDisplayParticleContext.translateActiveY(10) == 10, "reuse resets cached offset");
            });
            context.assertTrue(pos.equals(retained[0]), "retained owner must not alias a reusable cursor");
        });
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void deepNestingRestoresEveryFrame(TestContext context) {
        atDepth(context, -3.0, pos -> {
            BlockPos flat = pos.east();
            context.getWorld().setBlockState(flat, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            nested(context, pos, flat, 12);
            nested(context, pos, flat, 12);
        });
    }

    private static void nested(TestContext context, BlockPos lowered, BlockPos flat, int depth) {
        double expected = depth % 2 == 0 ? 7 : 10;
        run(context, depth % 2 == 0 ? lowered : flat, () -> {
            context.assertTrue(BlockDisplayParticleContext.translateActiveY(10) == expected, "nested entry offset");
            if (depth > 0) nested(context, lowered, flat, depth - 1);
            context.assertTrue(BlockDisplayParticleContext.translateActiveY(10) == expected, "nested restored offset");
        });
    }

    private static void atDepth(TestContext context, double dy, java.util.function.Consumer<BlockPos> action) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            BlockPos pos = context.getAbsolutePos(new BlockPos(2, 4, 2));
            context.getWorld().setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.writePlacementDyBatch(context.getWorld(), Map.of(pos, Double.doubleToRawLongBits(dy)));
            action.accept(pos);
        } finally { SlabAnchorAttachment.FROZEN_DY_ENABLED = previous; }
        context.complete();
    }

    private static void run(TestContext context, BlockPos pos, Runnable action) {
        Runnable previous = ParticleDisplayLazyFixtures.CALLBACK.get();
        ParticleDisplayLazyFixtures.CALLBACK.set(action);
        try {
            BlockDisplayParticleContext.runBlockDisplayTick(ParticleDisplayLazyFixtures.callbackBlock,
                    Blocks.STONE.getDefaultState(), context.getWorld(), pos, Random.create(0));
        } finally {
            if (previous == null) ParticleDisplayLazyFixtures.CALLBACK.remove();
            else ParticleDisplayLazyFixtures.CALLBACK.set(previous);
        }
    }
}
