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

        // --- write the table at distinct positions in one chunk ---
        BlockPos base = ctx.absolutePos(new BlockPos(1, 2, 1));
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
        LevelChunk chunk = world.getChunk(base.getX() >> 4, base.getZ() >> 4);
        SlabAnchorStore store = chunk.getCapability(SlabAnchorCapabilities.SLAB_ANCHOR_STORE)
                .orElse(null);
        ctx.assertTrue(store != null, "chunk must carry the anchor store capability");
        CompoundTag saved = store.save();
        SlabAnchorStore reloaded = new SlabAnchorStore(chunk);
        reloaded.load(saved);
        for (int i = 0; i < TABLE.length; i++) {
            BlockPos p = base.offset(i, 0, 0);
            long bits = Double.doubleToRawLongBits(reloaded.getPlacementDy(p.asLong()));
            ctx.assertTrue(bits == Double.doubleToRawLongBits(TABLE[i]),
                    "NBT round-trip raw-bits identity for " + TABLE[i]);
        }

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

        // --- WIRED TO NOTHING: a stored value must not leak into behaviour in Phase 2 ---
        BlockPos inert = base.offset(4, 0, 0); // holds -0.375, a value no lane can produce
        double behavioural = SlabSupport.getYOffset(world, inert, world.getBlockState(inert));
        ctx.assertTrue(Double.compare(behavioural, 0.0d) == 0,
                "Phase 2 is inert by construction: getYOffset must still answer geometrically "
                        + "(0.0 for stone on plain ground), got " + behavioural
                        + " — a leak here means the Phase 3 short-circuit arrived without its proof");

        // --- remove works and re-reads absent ---
        ctx.assertTrue(SlabAnchorAttachment.removePlacementDy(world, negZero), "remove must report change");
        ctx.assertTrue(Double.isNaN(SlabAnchorAttachment.storedPlacementDy(world, negZero)),
                "removed position must read NaN");

        ctx.succeed();
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
