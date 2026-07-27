package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorClientMirror;
import com.slabbed.anchor.SlabAnchorNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;

/**
 * STAYS Phase 5 contract: stored heights reach the client bit-for-bit, deltas are constant-size,
 * idempotent re-applies publish nothing, and the wire fails loudly rather than truncating.
 *
 * <p>What this headless suite CANNOT prove, stated plainly: real packet delivery to a real
 * client and the render re-mesh on arrival. Those are the BETA 1 live gate — the recorder's
 * side-split check (client-read dy vs server-stored dy per placement, zero divergent rows) on a
 * SLABBED TEST jar. No green here substitutes for that.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ForgePlacementDySyncGameTest {

    private static final ResourceLocation DIM =
            new ResourceLocation("slabbed_test", "sync_dim");
    private static final double[] TABLE = {0.0d, -0.5d, -1.5d, -2.5d, -0.375d};

    @GameTest(template = "empty")
    public void storedHeightsReachTheMirrorBitForBit(GameTestHelper ctx) {
        LongConsumer priorSink = SlabAnchorClientMirror.renderInvalidationSink;
        try {
            runRows(ctx);
        } finally {
            SlabAnchorClientMirror.renderInvalidationSink = priorSink;
            // Mirror hygiene: this test's dimension is synthetic; clear every chunk it touched.
            SlabAnchorClientMirror.clearChunk(DIM, 0, 0);
        }
        ctx.succeed();
    }

    private static void runRows(GameTestHelper ctx) {
        AtomicInteger sinkFires = new AtomicInteger();
        SlabAnchorClientMirror.renderInvalidationSink = packed -> sinkFires.incrementAndGet();

        // --- FULL codec round-trip, raw-bit identity ---
        long[] positions = new long[TABLE.length];
        long[] bits = new long[TABLE.length];
        for (int i = 0; i < TABLE.length; i++) {
            positions[i] = new BlockPos(i, 0, 0).asLong();
            bits[i] = Double.doubleToRawLongBits(TABLE[i]);
        }
        SlabAnchorNetwork.PlacementDyFullSyncPacket full =
                new SlabAnchorNetwork.PlacementDyFullSyncPacket(DIM, 0, 0, positions, bits);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SlabAnchorNetwork.PlacementDyFullSyncPacket.encode(full, buf);
        SlabAnchorNetwork.PlacementDyFullSyncPacket decodedFull =
                SlabAnchorNetwork.PlacementDyFullSyncPacket.decode(buf);
        for (int i = 0; i < TABLE.length; i++) {
            ctx.assertTrue(decodedFull.positions()[i] == positions[i]
                            && decodedFull.bits()[i] == bits[i],
                    "FULL codec raw-bit identity at entry " + i);
        }

        // --- DELTA codec round-trip + constant size ---
        SlabAnchorNetwork.PlacementDyDeltaPacket delta =
                new SlabAnchorNetwork.PlacementDyDeltaPacket(
                        DIM, positions[0], true, Double.doubleToRawLongBits(-0.375d));
        FriendlyByteBuf dbuf = new FriendlyByteBuf(Unpooled.buffer());
        SlabAnchorNetwork.PlacementDyDeltaPacket.encode(delta, dbuf);
        int deltaSize = dbuf.readableBytes();
        ctx.assertTrue(deltaSize <= 64,
                "a DELTA must be constant-size on the wire regardless of chunk contents; got "
                        + deltaSize + " bytes — the donor's whole-map resend shape is forbidden");
        SlabAnchorNetwork.PlacementDyDeltaPacket decodedDelta =
                SlabAnchorNetwork.PlacementDyDeltaPacket.decode(dbuf);
        ctx.assertTrue(decodedDelta.packedPos() == positions[0]
                        && decodedDelta.present()
                        && decodedDelta.rawBits() == Double.doubleToRawLongBits(-0.375d),
                "DELTA codec raw-bit identity");

        // --- malformed wire fails loudly, never truncates ---
        FriendlyByteBuf bad = new FriendlyByteBuf(Unpooled.buffer());
        bad.writeResourceLocation(DIM);
        bad.writeInt(0);
        bad.writeInt(0);
        bad.writeVarInt(Integer.MAX_VALUE);
        boolean threw = false;
        try {
            SlabAnchorNetwork.PlacementDyFullSyncPacket.decode(bad);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        ctx.assertTrue(threw, "an absurd entry count must throw, not allocate or truncate");

        // --- mirror: FULL apply, raw-bit read-back ---
        SlabAnchorClientMirror.applyPlacementDyFull(DIM, 0, 0, positions, bits);
        for (int i = 0; i < TABLE.length; i++) {
            long got = Double.doubleToRawLongBits(
                    SlabAnchorClientMirror.placementDy(DIM, positions[i]));
            ctx.assertTrue(got == bits[i], "mirror raw-bit identity for " + TABLE[i]);
        }
        ctx.assertTrue(Double.isNaN(
                        SlabAnchorClientMirror.placementDy(DIM, new BlockPos(9, 9, 9).asLong())),
                "an unsynced position must read NaN");

        // --- delta overwrite fires the re-mesh sink; idempotent re-apply does NOT ---
        int before = sinkFires.get();
        SlabAnchorClientMirror.applyPlacementDyDelta(
                DIM, positions[1], true, Double.doubleToRawLongBits(-1.0d));
        ctx.assertTrue(sinkFires.get() == before + 1,
                "a real delta must fire exactly one re-mesh invalidation");
        ctx.assertTrue(Double.doubleToRawLongBits(SlabAnchorClientMirror.placementDy(DIM, positions[1]))
                        == Double.doubleToRawLongBits(-1.0d),
                "the delta value must land bit-exact");
        SlabAnchorClientMirror.applyPlacementDyDelta(
                DIM, positions[1], true, Double.doubleToRawLongBits(-1.0d));
        ctx.assertTrue(sinkFires.get() == before + 1,
                "an IDEMPOTENT re-apply must publish nothing and fire no re-mesh");

        // --- delta remove reads absent and fires the sink once ---
        SlabAnchorClientMirror.applyPlacementDyDelta(DIM, positions[1], false, 0L);
        ctx.assertTrue(Double.isNaN(SlabAnchorClientMirror.placementDy(DIM, positions[1])),
                "a remove delta must read back absent");
        ctx.assertTrue(sinkFires.get() == before + 2, "the remove must fire one invalidation");

        // --- a corrupt FULL (length mismatch) is dropped whole, mirror unchanged ---
        long beforeBits = Double.doubleToRawLongBits(
                SlabAnchorClientMirror.placementDy(DIM, positions[0]));
        SlabAnchorClientMirror.applyPlacementDyFull(DIM, 0, 0, positions, new long[1]);
        ctx.assertTrue(Double.doubleToRawLongBits(
                        SlabAnchorClientMirror.placementDy(DIM, positions[0])) == beforeBits,
                "a length-mismatched full sync must be dropped WHOLE, mirror untouched");

        // --- structural: the store emits deltas, the watch sends the full map, the seam is
        //     side-gated. Source inspection, the established pattern. ---
        assertSourceContains(ctx, "src/main/java/com/slabbed/anchor/SlabAnchorStore.java",
                "syncPlacementDyDelta", "the store's mutators must emit deltas");
        assertSourceContains(ctx, "src/main/java/com/slabbed/anchor/SlabAnchorNetwork.java",
                "syncPlacementDyFullToPlayer(event.getPlayer(), event.getChunk())",
                "chunk watch must send the full dy map");
        // LIMIT, on record for the BETA 1 checklist: a string check cannot catch an INVERTED
        // instanceof, and the production lambda's negative half (server-side views reading NaN)
        // is behaviourally untestable headless — the recorder side-split live gate covers it.
        assertSourceContains(ctx, "src/main/java/com/slabbed/anchor/SlabAnchorClientMirrorEvents.java",
                "instanceof net.minecraft.client.renderer.chunk.RenderChunkRegion",
                "the seam must be side-gated to real client render regions");
    }

    private static void assertSourceContains(GameTestHelper ctx, String rel, String needle, String what) {
        Path src = Path.of("..").resolve(rel);
        String text;
        try {
            text = Files.readString(src);
        } catch (IOException e) {
            throw new AssertionError("structural check requires the repo source at " + src, e);
        }
        ctx.assertTrue(text.contains(needle), what + " — expected source to contain: " + needle);
    }
}
