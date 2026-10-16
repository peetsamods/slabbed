package com.slabbed.test;

import com.slabbed.anchor.PublishedRenderAttachments;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Published render facts retain exact values without sharing mutable attachment storage. */
public final class PublishedRenderAttachmentsTest {
    private static long bits(double value) {
        return Double.doubleToRawLongBits(value);
    }

    private static void require(GameTestHelper helper, boolean value, String message) {
        if (!value) {
            throw helper.assertionException(message);
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void repeatedLevelActivationRetainsAlreadyLoadedFacts(GameTestHelper helper) {
        PublishedRenderAttachments store = new PublishedRenderAttachments();
        Object level = new Object();
        Object chunk = new Object();
        store.activate(level);
        store.load(level, 7, chunk, new long[]{9}, new long[]{bits(-0.5d)}, new long[][]{{9}});
        store.activate(level);
        require(helper, store.capture().fact(level, 7, 9).present(), "same-level activation must not discard loaded facts");
        Object replacement = new Object();
        store.activate(replacement);
        require(helper, !store.capture().fact(level, 7, 9).present(), "level replacement must discard old facts");
        store.load(replacement, 7, chunk, new long[]{9}, new long[]{bits(-1.0d)}, new long[][]{{9}});
        store.reset(null);
        store.activate(replacement);
        require(helper, !store.capture().fact(replacement, 7, 9).present(), "reconnection must not revive disconnected facts");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void capturedFactsAndMarkersAreIndependentOfLaterWrites(GameTestHelper helper) {
        PublishedRenderAttachments store = new PublishedRenderAttachments();
        Object level = new Object();
        Object chunk = new Object();
        store.reset(level);
        long[] positions = {9, 3};
        long[] values = {bits(-0.0d), bits(-1.5d)};
        long[][] markers = {{9}};
        store.load(level, 7, chunk, positions, values, markers);
        PublishedRenderAttachments.View before = store.capture();
        positions[0] = 99;
        values[0] = bits(1.0d);
        markers[0][0] = 99;
        require(helper, before.fact(level, 7, 9).rawBits() == bits(-0.0d), "negative zero must remain exact");
        require(helper, before.fact(level, 7, 3).rawBits() == bits(-1.5d), "sorting must retain each fact's owner");
        require(helper, before.contains(level, 7, 0, 9), "marker storage must be copied");
        require(helper, !before.fact(level, 7, 99).present(), "absence must differ from present zero");
        store.updateDy(level, 7, chunk, new long[]{9}, new long[]{bits(+0.0d)});
        store.updateMarker(level, 7, chunk, 0, new long[0]);
        require(helper, store.capture().fact(level, 7, 9).rawBits() == bits(+0.0d), "replacement must publish exact zero");
        require(helper, before.fact(level, 7, 9).rawBits() == bits(-0.0d), "a captured view must stay immutable");
        require(helper, before.contains(level, 7, 0, 9), "a retained marker view must stay immutable");
        require(helper, !store.capture().contains(level, 7, 0, 9), "removal must publish");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void unloadedAndReplacedChunksCannotRepublishStaleFacts(GameTestHelper helper) {
        PublishedRenderAttachments store = new PublishedRenderAttachments();
        Object level = new Object();
        Object first = new Object();
        Object replacement = new Object();
        store.reset(level);
        store.load(level, 7, first, new long[]{9}, new long[]{bits(-0.5d)}, new long[][]{{9}});
        store.load(level, 7, replacement, new long[]{9}, new long[]{bits(-1.0d)}, new long[][]{{9}});
        store.load(level, -3, first, new long[]{9}, new long[]{bits(-0.5d)}, new long[][]{{9}});
        store.load(level, 13, first, new long[]{9}, new long[]{bits(-0.5d)}, new long[][]{{9}});
        store.updateDy(level, 7, first, new long[]{9}, new long[]{bits(-2.0d)});
        store.unload(level, 7, first);
        require(helper, store.capture().fact(level, 7, 9).rawBits() == bits(-1.0d), "old callbacks must not change a replacement");
        store.unload(level, 7, replacement);
        store.updateDy(level, 7, replacement, new long[]{9}, new long[]{bits(-2.0d)});
        require(helper, !store.capture().fact(level, 7, 9).present(), "unload must remove the chunk and reject late updates");
        require(helper, store.capture().fact(level, -3, 9).present()
                && store.capture().fact(level, 13, 9).present(), "removing a middle chunk must retain its neighbors");
        Object otherLevel = new Object();
        store.reset(otherLevel);
        store.load(level, 7, first, new long[]{9}, new long[]{bits(-0.5d)}, new long[][]{{9}});
        require(helper, !store.capture().fact(otherLevel, 7, 9).present(), "old-level loads must be rejected");
        store.load(otherLevel, 7, replacement, new long[]{9}, new long[]{bits(-1.0d)}, new long[][]{{9}});
        require(helper, !store.capture().fact(level, 7, 9).present(), "a direct old-level reader must never see the new world's fact");
        store.reset(null);
        require(helper, !store.capture().fact(otherLevel, 7, 9).present(), "disconnect must clear render facts");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void workerReadsNeverObserveAPartiallyPublishedChunk(GameTestHelper helper) throws InterruptedException {
        PublishedRenderAttachments store = new PublishedRenderAttachments();
        Object level = new Object();
        Object chunk = new Object();
        store.reset(level);
        store.load(level, 7, chunk, new long[]{9, 3}, new long[]{bits(-0.5d), bits(-0.5d)}, new long[][]{{9}});
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch publishedChange = new CountDownLatch(1);
        CountDownLatch observedChange = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                if (!publishedChange.await(2, TimeUnit.SECONDS)
                        || store.capture().fact(level, 7, 9).rawBits() != bits(-1.0d)) {
                    throw new AssertionError("the worker must observe a main-thread publication");
                }
                observedChange.countDown();
                for (int i = 0; i < 50_000; i++) {
                    PublishedRenderAttachments.View view = store.capture();
                    if (view.fact(level, 7, 9).rawBits() != view.fact(level, 7, 3).rawBits()) {
                        throw new AssertionError("a worker observed only part of one chunk publication");
                    }
                }
            } catch (Throwable failure) {
                error.set(failure);
            }
        }, "slabbed-render-snapshot-test");
        worker.setDaemon(true);
        worker.start();
        store.updateDy(level, 7, chunk, new long[]{9, 3}, new long[]{bits(-1.0d), bits(-1.0d)});
        publishedChange.countDown();
        require(helper, observedChange.await(2, TimeUnit.SECONDS), "the worker must reach changed facts");
        for (int i = 0; i < 2_000; i++) {
            long value = bits((i & 1) == 0 ? -0.5d : -1.0d);
            store.updateDy(level, 7, chunk, new long[]{9, 3}, new long[]{value, value});
        }
        worker.join(2_000);
        require(helper, !worker.isAlive() && error.get() == null, "worker publication checks must complete: " + error.get());
        helper.succeed();
    }
}
