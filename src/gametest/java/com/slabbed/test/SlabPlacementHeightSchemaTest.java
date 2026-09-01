package com.slabbed.test;

import com.slabbed.dev.SlabbedTestAccess;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.SlabBlock;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class SlabPlacementHeightSchemaTest {
    private static final String TEMPLATE = "empty";

    @GameTest(template = TEMPLATE)
    public void exactFiniteHalfStepsRoundTrip(GameTestHelper ctx) {
        assertHalfSteps(ctx, -1.0d, -2);
        assertHalfSteps(ctx, -0.5d, -1);
        assertHalfSteps(ctx, -0.0d, 0);
        assertHalfSteps(ctx, 0.0d, 0);
        assertHalfSteps(ctx, 0.5d, 1);
        assertHalfSteps(ctx, -64.0d, Byte.MIN_VALUE);
        assertHalfSteps(ctx, 63.5d, Byte.MAX_VALUE);
        ctx.assertTrue(SlabPlacementHeightAttachment.exactHalfSteps(-0.3d).isEmpty(),
                "off-grid height must be declined");
        ctx.assertTrue(SlabPlacementHeightAttachment.exactHalfSteps(Double.NaN).isEmpty(),
                "NaN must be declined");
        ctx.assertTrue(SlabPlacementHeightAttachment.exactHalfSteps(Double.POSITIVE_INFINITY).isEmpty(),
                "infinite height must be declined");
        ctx.assertTrue(SlabPlacementHeightAttachment.exactHalfSteps(-64.5d).isEmpty()
                        && SlabPlacementHeightAttachment.exactHalfSteps(64.0d).isEmpty(),
                "heights immediately beyond signed-byte half steps must be declined");
        ctx.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void codecRoundTripsInCanonicalPositionOrder(GameTestHelper ctx) {
        Long2ByteOpenHashMap first = new Long2ByteOpenHashMap();
        first.put(BlockPos.asLong(16, -1, -16), (byte) 1);
        first.put(BlockPos.asLong(-1, 0, 15), (byte) -2);
        first.put(BlockPos.asLong(15, 16, 16), (byte) 0);
        first.put(BlockPos.asLong(-16, 1, -17), (byte) -1);

        Long2ByteOpenHashMap second = new Long2ByteOpenHashMap();
        second.put(BlockPos.asLong(-16, 1, -17), (byte) -1);
        second.put(BlockPos.asLong(15, 16, 16), (byte) 0);
        second.put(BlockPos.asLong(-1, 0, 15), (byte) -2);
        second.put(BlockPos.asLong(16, -1, -16), (byte) 1);

        Tag firstTag = encoded(first);
        Tag secondTag = encoded(second);
        ctx.assertTrue(firstTag.equals(secondTag),
                "equal facts inserted in different orders must encode identically");
        Long2ByteOpenHashMap decoded = SlabPlacementHeightAttachment.codec()
                .parse(NbtOps.INSTANCE, firstTag)
                .result()
                .orElseThrow(() -> new AssertionError("canonical schema must decode"));
        ctx.assertTrue(decoded.equals(first), "canonical schema must round-trip every fact");
        ctx.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void codecRejectsMalformedPairedArrays(GameTestHelper ctx) {
        CompoundTag unequal = new CompoundTag();
        unequal.putLongArray("positions", new long[] {BlockPos.asLong(1, 2, 3)});
        unequal.putIntArray("height_half_steps", new int[] {});
        ctx.assertTrue(SlabPlacementHeightAttachment.codec().parse(NbtOps.INSTANCE, unequal).result().isEmpty(),
                "unequal paired arrays must be rejected");

        long duplicate = BlockPos.asLong(4, 5, 6);
        CompoundTag duplicates = new CompoundTag();
        duplicates.putLongArray("positions", new long[] {duplicate, duplicate});
        duplicates.putIntArray("height_half_steps", new int[] {-1, -2});
        ctx.assertTrue(SlabPlacementHeightAttachment.codec().parse(NbtOps.INSTANCE, duplicates).result().isEmpty(),
                "duplicate packed positions must be rejected");

        CompoundTag outOfRange = new CompoundTag();
        outOfRange.putLongArray("positions", new long[] {BlockPos.asLong(7, 8, 9)});
        outOfRange.putIntArray("height_half_steps", new int[] {128});
        ctx.assertTrue(SlabPlacementHeightAttachment.codec().parse(NbtOps.INSTANCE, outOfRange).result().isEmpty(),
                "out-of-byte half-step values must be rejected");
        ctx.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void absentAttachmentMeansLegacyLookup(GameTestHelper ctx) {
        BlockPos pos = ctx.absolutePos(new BlockPos(3, 3, 3));
        LevelChunk chunk = ctx.getLevel().getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(chunk, pos).isEmpty(),
                "a chunk without placement-height data must remain an explicit legacy absence");
        ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(null, pos).isEmpty(),
                "a missing chunk must not fabricate a zero-height fact");

        Long2ByteOpenHashMap facts = new Long2ByteOpenHashMap();
        facts.put(pos.offset(1, 0, 0).asLong(), (byte) -1);
        SlabbedTestAccess.putPlacementFacts(chunk, facts);
        try {
            ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(chunk, pos).isEmpty(),
                    "an existing map without this position must remain explicitly absent");
            facts.put(pos.asLong(), (byte) 0);
            ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(chunk, pos).orElse(1) == 0,
                    "a stored zero-height fact must remain distinguishable from absence");
        } finally {
            SlabbedTestAccess.clearPlacementFacts(chunk);
        }
        ctx.succeed();
    }

    private static void assertHalfSteps(GameTestHelper ctx, double offset, int expected) {
        int actual = SlabPlacementHeightAttachment.exactHalfSteps(offset)
                .orElseThrow(() -> new AssertionError("canonical half step must be accepted"));
        ctx.assertTrue(actual == expected, "half-step conversion returned the wrong count");
        ctx.assertTrue(Double.doubleToRawLongBits(SlabPlacementHeightAttachment.offsetForHalfSteps((byte) actual))
                        == Double.doubleToRawLongBits(expected * 0.5d),
                "half-step conversion must round-trip exactly");
    }

    private static Tag encoded(Long2ByteOpenHashMap facts) {
        return SlabPlacementHeightAttachment.codec()
                .encodeStart(NbtOps.INSTANCE, facts)
                .result()
                .orElseThrow(() -> new AssertionError("canonical schema must encode"));
    }

    @GameTest(template = TEMPLATE)
    public void outOfEnvelopeWritesAreDeclined(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos pos = ctx.absolutePos(new BlockPos(2, 2, 6));
        world.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        LevelChunk chunk = world.getChunkAt(pos);
        ctx.assertTrue(!SlabPlacementHeightAttachment.putHalfSteps(chunk, pos, -100),
                "a write below the placement envelope must be declined");
        ctx.assertTrue(!SlabPlacementHeightAttachment.putHalfSteps(chunk, pos, 1),
                "the placement alphabet is lowering-only; a positive write must be declined");
        ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(chunk, pos).isEmpty(),
                "declined writes must leave no fact behind");
        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(chunk, pos, -4),
                "the exact envelope floor (-2.0) must remain a legal write");
        ctx.assertTrue(SlabPlacementHeightAttachment.remove(chunk, pos), "cleanup");
        ctx.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void outOfEnvelopeStoredBytesReadAsAbsentAndAreNeverRepaired(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(2, 2, 8));
        BlockPos subject = support.above();
        world.setBlock(support, Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
        world.setBlock(subject, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        LevelChunk chunk = world.getChunkAt(subject);

        Long2ByteOpenHashMap existing = SlabbedTestAccess.placementFacts(chunk);
        Long2ByteOpenHashMap corrupted = existing == null
                ? new Long2ByteOpenHashMap()
                : new Long2ByteOpenHashMap(existing);
        corrupted.put(subject.asLong(), (byte) -100);
        SlabbedTestAccess.putPlacementFacts(chunk, corrupted);

        double resolved = SlabSupport.getYOffset(world, subject, world.getBlockState(subject));
        ctx.assertTrue(Math.abs(resolved + 0.5d) <= 1.0e-9,
                "an out-of-envelope stored byte must read as ABSENT so the legacy geometric lane"
                        + " engages (-0.5 on this slab); got " + resolved);
        ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(chunk, subject)
                        .orElse(Integer.MIN_VALUE) == -100,
                "a read must never repair, rewrite, or re-derive the store");

        world.setBlock(subject.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.removeBlock(subject.east(), false);
        double afterMutation = SlabSupport.getYOffset(world, subject, world.getBlockState(subject));
        ctx.assertTrue(Math.abs(afterMutation - resolved) <= 1.0e-9,
                "the fail-closed resolution must be stable across a neighbor mutation");
        ctx.succeed();
    }
}
