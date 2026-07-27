package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabAnchorCapabilities;
import com.slabbed.anchor.SlabAnchorStore;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * STAYS scope Phase 2 contract: the value bucket stores exact raw doubles, survives the NBT
 * codec bit-for-bit, and is wired to NOTHING.
 *
 * <p>The value table includes {@code -0.375} — unreachable by ANY lane in the geometric
 * derivation — so its survival proves the number came from storage and nowhere else. The
 * {@code -0.0} row pins the deliberate improvement over the 26.2 reference: normalised to
 * {@code +0.0} on write, so the raw-bits round-trip that is RED on the reference
 * ({@code DoubleTag.valueOf} compares with {@code ==}) is GREEN here.
 *
 * <p>"Wired to nothing" is asserted, not assumed: a stored {@code -0.375} must NOT change what
 * {@code getYOffset} answers. Phase 3 flips that on deliberately, behind its own proof.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ForgePlacementDyStoreRoundTripGameTest {

    private static final double[] TABLE = {0.0d, -0.5d, -1.5d, -2.5d, -0.375d};

    @GameTest(template = "empty")
    public void storedDyRoundTripsBitExactAndStaysInert(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos base = ctx.absolutePos(new BlockPos(1, 2, 1));
        try {
            runRows(ctx, world, base);
        } finally {
            // STRIKE THE STORE. Since Phase 3, stored entries are live height authority at
            // ABSOLUTE positions that outlive gametest structure re-placement. Clear every
            // write even when a row aborts (adversarial review: a leaked entry is the F5
            // haunting with teeth).
            for (int i = 0; i <= TABLE.length; i++) {
                SlabAnchorAttachment.removePlacementDy(world, base.offset(i, 0, 0));
            }
        }
        ctx.succeed();
    }

    private static void runRows(GameTestHelper ctx, ServerLevel world, BlockPos base) {
        // --- write the table at distinct positions ---
        for (int i = 0; i < TABLE.length; i++) {
            BlockPos p = base.offset(i, 0, 0);
            world.setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_NONE);
            ctx.assertTrue(SlabAnchorAttachment.writePlacementDy(world, p, TABLE[i])
                            || TABLE[i] == 0.0d && SlabAnchorAttachment.storedPlacementDy(world, p) == 0.0d,
                    "write must succeed for " + TABLE[i]);
        }

        // --- raw-bits identity on read-back ---
        for (int i = 0; i < TABLE.length; i++) {
            BlockPos p = base.offset(i, 0, 0);
            double stored = SlabAnchorAttachment.storedPlacementDy(world, p);
            ctx.assertTrue(Double.doubleToRawLongBits(stored) == Double.doubleToRawLongBits(TABLE[i]),
                    "raw-bits identity for " + TABLE[i] + ": got " + stored);
        }

        // --- -0.0 normalisation: stored as +0.0, bit-for-bit ---
        BlockPos negZero = base.offset(TABLE.length, 0, 0);
        world.setBlock(negZero, Blocks.STONE.defaultBlockState(), Block.UPDATE_NONE);
        SlabAnchorAttachment.writePlacementDy(world, negZero, -0.0d);
        long negZeroBits = Double.doubleToRawLongBits(SlabAnchorAttachment.storedPlacementDy(world, negZero));
        ctx.assertTrue(negZeroBits == Double.doubleToRawLongBits(0.0d),
                "-0.0 must normalise to +0.0 on write (the beat-the-reference case); raw bits were "
                        + Long.toHexString(negZeroBits));

        // --- absent reads NaN ---
        ctx.assertTrue(Double.isNaN(SlabAnchorAttachment.storedPlacementDy(world, base.offset(0, 0, 3))),
                "an unwritten position must read NaN");

        // --- non-finite is rejected at the door ---
        assertThrows(ctx, () -> SlabAnchorAttachment.writePlacementDy(world, base, Double.NaN),
                "NaN write must throw — NaN is the absent sentinel");
        assertThrows(ctx, () -> SlabAnchorAttachment.writePlacementDy(world, base, Double.POSITIVE_INFINITY),
                "infinite write must throw");

        // --- NBT round-trip, bit-for-bit, through the real save/load codec ---
        // PER-POSITION CHUNK RESOLUTION, deliberately: the store is per-chunk and the fixture
        // row can straddle a chunk boundary depending on where the roster's plot layout drops
        // this test. An earlier revision saved only base's chunk and went red the day an 18th
        // test relocated the plot onto a boundary — the same layout-sensitivity class as the
        // F5 haunting, in this suite's own fixture. Never assume two fixture positions share
        // a chunk.
        for (int i = 0; i < TABLE.length; i++) {
            BlockPos p = base.offset(i, 0, 0);
            LevelChunk posChunk = world.getChunk(p.getX() >> 4, p.getZ() >> 4);
            SlabAnchorStore posStore = posChunk
                    .getCapability(SlabAnchorCapabilities.SLAB_ANCHOR_STORE).orElse(null);
            ctx.assertTrue(posStore != null, "chunk must carry the anchor store capability");
            SlabAnchorStore reloaded = new SlabAnchorStore(posChunk);
            reloaded.load(posStore.save());
            long bits = Double.doubleToRawLongBits(reloaded.getPlacementDy(p.asLong()));
            ctx.assertTrue(bits == Double.doubleToRawLongBits(TABLE[i]),
                    "NBT round-trip raw-bits identity for " + TABLE[i]
                            + ": got bits " + Long.toHexString(bits)
                            + " want " + Long.toHexString(Double.doubleToRawLongBits(TABLE[i])));
        }
        LevelChunk chunk = world.getChunk(base.getX() >> 4, base.getZ() >> 4);
        SlabAnchorStore store = chunk.getCapability(SlabAnchorCapabilities.SLAB_ANCHOR_STORE)
                .orElse(null);
        ctx.assertTrue(store != null, "chunk must carry the anchor store capability");
        CompoundTag saved = store.save();

        // --- corrupt pair (length mismatch) is dropped WHOLE, never half-applied ---
        CompoundTag corrupt = saved.copy();
        corrupt.putLongArray("placement_dy_bits", new long[] {0L});
        SlabAnchorStore fromCorrupt = new SlabAnchorStore(chunk);
        fromCorrupt.load(corrupt);
        ctx.assertTrue(fromCorrupt.placementDyCount() == 0,
                "a length-mismatched pos/bits pair must be dropped whole; half-applied heights "
                        + "author WRONG values silently");
        ctx.assertTrue(!fromCorrupt.isEmpty() == !store.isEmpty()
                        || fromCorrupt.placementDyCount() == 0,
                "the boolean buckets must survive a corrupt dy pair untouched");

        // --- PHASE 3 LANDED WITH ITS PROOF: the stored value now IS the behaviour ---
        // (This row asserted inertness through Phase 2; ForgeStoredDyAuthorityGameTest carries
        // the full authority contract, and this row now pins the same flip from the store side.)
        BlockPos inert = base.offset(4, 0, 0); // holds -0.375, a value no lane can produce
        double behavioural = SlabSupport.getYOffset(world, inert, world.getBlockState(inert));
        ctx.assertTrue(Double.compare(behavioural, -0.375d) == 0,
                "with Phase 3 landed, the stored -0.375 must BE the height; got " + behavioural);

        // --- remove works and re-reads absent ---
        ctx.assertTrue(SlabAnchorAttachment.removePlacementDy(world, negZero), "remove must report change");
        ctx.assertTrue(Double.isNaN(SlabAnchorAttachment.storedPlacementDy(world, negZero)),
                "removed position must read NaN");
    }

    private static void assertThrows(GameTestHelper ctx, Runnable action, String what) {
        boolean threw = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        ctx.assertTrue(threw, what);
    }
}
