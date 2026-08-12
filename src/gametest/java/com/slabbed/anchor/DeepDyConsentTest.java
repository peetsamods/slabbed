package com.slabbed.anchor;

import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Contract proofs for {@code slabbed:deep_dy_consent} — the per-world state that decides which dy
 * cap the resolver refuses to go past.
 *
 * <p>See {@code LAW.md}; nothing here redefines the placement law. Consent decides only the cap.
 *
 * <p>What this file has to establish, and why each one needs its own cell:
 *
 * <ul>
 *   <li><b>The wiring is LIVE.</b> A cap that came from a system property and a cap that came from
 *       a world's consent are the same number in a suite where nothing consents, so "the tests are
 *       green" proves nothing on its own. The end-to-end cell asserts the cap in force EQUALS the
 *       one the world's own stamp derives, in either flag leg.</li>
 *   <li><b>Absent is not the same fact as stamped-false.</b> They behave identically today and must
 *       stay distinguishable forever — "was this world born under the placement store?" cannot be
 *       recovered later by any amount of looking at its contents.</li>
 *   <li><b>The cap is CACHED, not looked up.</b> Two cells: writing the authoritative store does
 *       not move the resolver's answer at all (so it cannot be being read per call), and a whole
 *       resolver battery performs exactly ZERO authoritative reads while provably reaching the
 *       cap.</li>
 *   <li><b>Reading the cap allocates nothing.</b> Measured in bytes, not argued. This project has
 *       shipped a perf regression twice and the standing lesson is that the instrument is an
 *       allocation-regression cell, so that is what this is.</li>
 *   <li><b>The new-world default is pinned where it can be seen.</b> It stays on the shipped
 *       alphabet while the deep pass-through depth-budget defect remains open.</li>
 * </ul>
 *
 * <p><b>Why no cell mutates the live cap.</b> Every other test in the suite reads it, gametests in
 * a batch tick concurrently, and an order-dependent law suite is a false green waiting to happen.
 * The derivation is exercised through {@link SlabSupport#capFor(boolean)} — the same expression
 * {@code armDeepAlphabet} uses, as a pure function — and the store through write-then-restore,
 * which provably cannot reach the cap (that is one of the assertions).
 */
public final class DeepDyConsentTest {

    private static final double EPS = 1.0e-6;

    // ── 1. the wiring is live ─────────────────────────────────────────────────────────────────

    /**
     * <b>The cap in force is the one this world's consent derives.</b> Runs in both flag legs and
     * means something different in each, which is the point: with the developer override off, the
     * suite's world does not consent and the cap must be the shipped one; with it on, the cap must
     * be the deep one even though the world still does not consent (the override may only ever arm,
     * never disarm — that is what keeps both suite legs runnable from one tree).
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void theCapInForceIsTheOneThisWorldsConsentDerives(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        boolean consented = DeepDyConsentAttachment.consented(w);
        double inForce = SlabSupport.minResolvedDy();
        double derived = SlabSupport.capFor(consented);

        String measured = "consented=" + consented + " stamp=" + DeepDyConsentAttachment.stamp(w)
                + " override=" + SlabSupport.DEEP_DY_ALPHABET + " capInForce=" + inForce
                + " capDerived=" + derived;

        ctx.assertTrue(Math.abs(inForce - derived) <= EPS,
                "THE CONSENT WIRING IS NOT LIVE: the cap the resolver is using is not the cap this "
                        + "world's own state derives. Either the world-load hook did not run or "
                        + "something else is writing the cached cap. " + measured);

        if (SlabSupport.DEEP_DY_ALPHABET) {
            ctx.assertTrue(Math.abs(inForce - SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY) <= EPS,
                    "the developer override must force the deep cap regardless of world state — "
                            + measured);
        } else {
            ctx.assertTrue(Math.abs(inForce - SlabSupport.SHIPPED_MIN_RESOLVED_DY) <= EPS,
                    "NO WORLD IN THIS SUITE CONSENTS, so the cap must be the shipped one and the "
                            + "whole suite must be byte-identical to the build before consent "
                            + "existed. " + measured);
        }
        ctx.complete();
    }

    /**
     * <b>The derivation, both answers, without touching the live cap.</b> Armed, the cap IS
     * {@link SlabbedOffsetRaycast#DEEPEST_TARGETABLE_DY} — the same field the pick window sizes
     * itself from, not a second copy of the number — so the standing identity
     * {@code cap == -(window radius)} cannot be written wrong. Unarmed it is the shipped cap,
     * unless the developer override is on, in which case it may not fall.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void consentDerivesTheWindowsOwnContractFieldAndNeverASecondCopyOfIt(TestContext ctx) {
        double armed = SlabSupport.capFor(true);
        double unarmed = SlabSupport.capFor(false);

        ctx.assertTrue(armed == SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY,
                "a consented world's cap must BE SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY, not a "
                        + "value that happens to equal it — got " + armed + " against "
                        + SlabbedOffsetRaycast.DEEPEST_TARGETABLE_DY);
        ctx.assertTrue((int) Math.ceil(-armed) == SlabbedOffsetRaycast.WINDOW_RADIUS,
                "the standing identity cap == -(window radius) must hold for a consented world, or "
                        + "a consenting player gets blocks drawn where they cannot aim: cap="
                        + armed + " radius=" + SlabbedOffsetRaycast.WINDOW_RADIUS);

        if (SlabSupport.DEEP_DY_ALPHABET) {
            ctx.assertTrue(unarmed == armed,
                    "the developer override may only ever ARM. An unconsented world must not be "
                            + "able to pull the cap back to " + unarmed + " while the deep leg is "
                            + "running, or the deep leg would test nothing outside consented "
                            + "worlds.");
        } else {
            ctx.assertTrue(unarmed == SlabSupport.SHIPPED_MIN_RESOLVED_DY,
                    "an unconsented world must read the shipped cap ("
                            + SlabSupport.SHIPPED_MIN_RESOLVED_DY + "), got " + unarmed);
            ctx.assertTrue(unarmed > armed,
                    "fixture: the two legs must be different numbers or this cell proves nothing — "
                            + unarmed + " vs " + armed);
        }
        ctx.complete();
    }

    // ── 2. absent is a different fact from stamped-false ──────────────────────────────────────

    /**
     * <b>Three states, not two.</b> No stamp (legacy — a world that existed before consent did),
     * stamped {@code false} (born after, not consenting), stamped {@code true} (consenting). The
     * middle one behaves exactly like the first today, which is precisely why the distinction has
     * to be asserted: it is a fact about the world's ORIGIN, capturable only at birth, and a store
     * that collapsed it into "false" would destroy it silently.
     *
     * <p><b>This cell also proves the cap is not a lookup.</b> It writes the authoritative store
     * three times and asserts the resolver's answer never moves. If anything on the read path
     * consulted the store, this would fail — and it is also what makes the cell safe to run beside
     * every other test in the suite.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void absentStampAndStampedFalseAreDifferentFactsAndNeitherReachesTheCap(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        Boolean original = DeepDyConsentAttachment.stamp(w);
        double capBefore = SlabSupport.minResolvedDy();
        try {
            w.removeAttached(DeepDyConsentAttachment.CONSENT_TYPE);
            ctx.assertTrue(DeepDyConsentAttachment.stamp(w) == null,
                    "LEGACY is the ABSENCE of the stamp, and absence must be readable as absence — "
                            + "no content is inspected and no age is inferred anywhere in this "
                            + "class, so if absence is not distinguishable there is no legacy "
                            + "state at all.");
            ctx.assertTrue(!DeepDyConsentAttachment.consented(w),
                    "an absent stamp must never read as consent");

            w.setAttached(DeepDyConsentAttachment.CONSENT_TYPE, false);
            ctx.assertTrue(Boolean.FALSE.equals(DeepDyConsentAttachment.stamp(w)),
                    "a world stamped false must read back as stamped false, NOT as absent — the "
                            + "two are the same behaviour and different facts");
            ctx.assertTrue(!DeepDyConsentAttachment.consented(w),
                    "stamped false must not read as consent");

            w.setAttached(DeepDyConsentAttachment.CONSENT_TYPE, true);
            ctx.assertTrue(Boolean.TRUE.equals(DeepDyConsentAttachment.stamp(w))
                            && DeepDyConsentAttachment.consented(w),
                    "a world stamped true must read back as consenting");

            ctx.assertTrue(SlabSupport.minResolvedDy() == capBefore,
                    "THE CAP IS BEING LOOKED UP, NOT CACHED. Writing the authoritative store three "
                            + "times moved the resolver's answer from " + capBefore + " to "
                            + SlabSupport.minResolvedDy() + ". The cap is read on the resolver's "
                            + "hot path and must be a cached value refreshed on world load — a "
                            + "per-call store read is the perf class this project has already "
                            + "shipped twice.");
        } finally {
            if (original == null) {
                w.removeAttached(DeepDyConsentAttachment.CONSENT_TYPE);
            } else {
                w.setAttached(DeepDyConsentAttachment.CONSENT_TYPE, original);
            }
            SlabSupport.armDeepAlphabet(DeepDyConsentAttachment.consented(w));
        }
        ctx.complete();
    }

    // ── 3. THE PERF GATE ──────────────────────────────────────────────────────────────────────

    /**
     * Consent is a save-wide, one-way transition. It must update every loaded dimension and the
     * server's cached cap before returning; otherwise attachment sync can make a remote client use
     * the deep cap while the server continues resolving against the shipped cap until restart.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void consentTransitionIsSaveWideImmediateAndOneWay(TestContext ctx) {
        ServerWorld testWorld = ctx.getWorld();
        Map<ServerWorld, Boolean> originalStamps = new LinkedHashMap<>();
        for (ServerWorld world : testWorld.getServer().getWorlds()) {
            originalStamps.put(world, DeepDyConsentAttachment.stamp(world));
        }

        try {
            DeepDyConsentAttachment.stampWorld(testWorld, true);

            for (ServerWorld world : originalStamps.keySet()) {
                ctx.assertTrue(Boolean.TRUE.equals(DeepDyConsentAttachment.stamp(world)),
                        "consent must be mirrored to every loaded dimension before the transition "
                                + "returns; missing " + world.getRegistryKey().getValue());
            }
            ctx.assertTrue(SlabSupport.minResolvedDy() == SlabSupport.capFor(true),
                    "consent persisted but the server cache still uses cap "
                            + SlabSupport.minResolvedDy() + " instead of " + SlabSupport.capFor(true));

            boolean reversalRejected = false;
            try {
                DeepDyConsentAttachment.stampWorld(testWorld, false);
            } catch (IllegalStateException expected) {
                reversalRejected = true;
            }
            ctx.assertTrue(reversalRejected,
                    "deep-dy consent must be one-way because revoking it would move unstored cells");
            for (ServerWorld world : originalStamps.keySet()) {
                ctx.assertTrue(Boolean.TRUE.equals(DeepDyConsentAttachment.stamp(world)),
                        "a rejected reversal must leave every dimension consented; changed "
                                + world.getRegistryKey().getValue());
            }
            ctx.assertTrue(SlabSupport.minResolvedDy() == SlabSupport.capFor(true),
                    "a rejected reversal changed the live cap");
        } finally {
            for (Map.Entry<ServerWorld, Boolean> entry : originalStamps.entrySet()) {
                if (entry.getValue() == null) {
                    entry.getKey().removeAttached(DeepDyConsentAttachment.CONSENT_TYPE);
                } else {
                    entry.getKey().setAttached(DeepDyConsentAttachment.CONSENT_TYPE, entry.getValue());
                }
            }
            ServerWorld authority = testWorld.getServer().getOverworld();
            SlabSupport.armDeepAlphabet(authority != null
                    && Boolean.TRUE.equals(originalStamps.get(authority)));
        }
        ctx.complete();
    }

    /**
     * <b>A whole resolver battery reaches the cap and reads the authoritative store ZERO times.</b>
     *
     * <p>Stated as an exact identity on a counter rather than as a wall-clock budget, so it holds
     * on any machine and cannot be satisfied by a faster CPU — the same discipline as
     * {@code DyWindowSuite#pickWindowCostIsPinnedRelativeToTheWindowItReplaced}, which this cell is
     * modelled on.
     *
     * <p>The vacuity half matters as much as the gate: a battery that never resolved anything deep
     * enough to consult the cap would read zero store hits trivially. So the tower is built tall
     * enough to saturate, and the cell asserts the saturated reading was actually produced before
     * it asserts the counter did not move.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void theResolverHotPathNeverReadsTheAuthoritativeConsentStore(TestContext ctx) {
        ServerWorld w = ctx.getWorld();
        BlockPos ground = ctx.getAbsolutePos(BlockPos.ORIGIN).add(2, 1, 2);
        w.setBlockState(ground, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS);

        // The saturating pre-store tower, the same shape DyWindowSuite measures the cap with: a
        // column of anchored bottom slabs with every stored height dropped, so each course deepens
        // half a block off the one below until the CAP refuses. Its top courses are decided by the
        // cap and by nothing else, which is what makes this battery a measurement of the cap read.
        int courses = Math.max(6, (int) Math.ceil(-SlabSupport.minResolvedDy() / 0.5) + 3);
        BlockPos[] rungs = new BlockPos[courses];
        for (int i = 0; i < courses; i++) {
            rungs[i] = ground.up(i + 1);
            w.setBlockState(rungs[i],
                    Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                    Block.NOTIFY_LISTENERS);
            SlabAnchorAttachment.addAnchor(w, rungs[i], w.getBlockState(rungs[i]));
        }
        for (BlockPos rung : rungs) {
            SlabPlacementDyAttachment.clear(w, rung);
        }

        long storeReadsBefore = DeepDyConsentAttachment.authoritativeReads();
        int resolutions = 0;
        double deepest = 0.0;
        for (int pass = 0; pass < 8; pass++) {
            for (BlockPos rung : rungs) {
                double dy = SlabSupport.getYOffset(w, rung, w.getBlockState(rung));
                deepest = Math.min(deepest, dy);
                resolutions++;
            }
        }
        long storeReadsAfter = DeepDyConsentAttachment.authoritativeReads();

        String measured = "resolutions=" + resolutions + " deepest=" + deepest
                + " cap=" + SlabSupport.minResolvedDy()
                + " storeReads=" + storeReadsBefore + " -> " + storeReadsAfter;
        System.out.println("[CONSENT-PERF] " + measured);

        ctx.assertTrue(resolutions > 0,
                "vacuity guard: the battery must actually resolve something — " + measured);
        ctx.assertTrue(Math.abs(deepest - SlabSupport.minResolvedDy()) <= EPS,
                "VACUITY GUARD, and it is the whole point of this cell: the battery must SATURATE "
                        + "at the cap, or it never consulted the value whose read cost is being "
                        + "measured and the zero below would be zero for the wrong reason. "
                        + "Expected the deepest reading to be the cap — " + measured);

        ctx.assertTrue(storeReadsAfter == storeReadsBefore,
                "PERF GATE: " + (storeReadsAfter - storeReadsBefore) + " authoritative consent-store"
                        + " reads happened while resolving " + resolutions + " cells. The cap is on "
                        + "the resolver's hot path — the path Stage 1 measured growing 3.06x on the "
                        + "pick alone — and it must be a cached value written on world load, never "
                        + "a per-call world/attachment/map lookup. This is the assertion that "
                        + "exists because this project has shipped a perf regression twice. Do not "
                        + "relax it; find what started reading the store. " + measured);
        ctx.complete();
    }

    /** Reads per measured loop in {@link #readingTheCapAllocatesNothingPerRead}. */
    private static final int ALLOCATION_LOOP_READS = 1_000_000;

    /**
     * The gate, in bytes PER READ. Any object on this path — a {@code Boolean} or {@code Double}
     * box, a map entry, an {@code Optional} — is at least 16 bytes each and every call, so the
     * regression this catches sits 16x above this line while the measured value sits far below it.
     *
     * <p><b>Why a rate and not zero.</b> Measured on this tree: 6,544 bytes across the whole
     * million-read loop, i.e. 0.0065 bytes per read — allocation from the surrounding runtime (the
     * counter is sampled around code this cell does not own), not from the read. Demanding a flat
     * zero would be pinning the JVM's housekeeping, which is exactly the kind of assertion that
     * gets relaxed later and then means nothing. A per-read rate says the thing that is actually
     * true and stays true: <em>this read does not allocate</em>.
     */
    private static final double MAX_ALLOCATED_BYTES_PER_READ = 1.0;

    /**
     * <b>Reading the cap allocates nothing per read.</b> Measured with the JVM's own per-thread
     * allocation counter, against a control loop that does the same arithmetic without the call —
     * not estimated, and not a wall-clock timing that a fast machine could satisfy.
     *
     * <p>The standing lesson from the two shipped perf regressions is that the right instrument is
     * an allocation-regression cell, so this is that instrument aimed at the one value this stage
     * added a read of.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void readingTheCapAllocatesNothingPerRead(TestContext ctx) {
        com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        ctx.assertTrue(threads.isThreadAllocatedMemorySupported(),
                "this cell needs the JVM's per-thread allocation counter; without it there is no "
                        + "measurement and a green would be a false one");
        threads.setThreadAllocatedMemoryEnabled(true);
        long id = Thread.currentThread().threadId();

        double controlSink = 0.0;
        double measuredSink = 0.0;
        for (int i = 0; i < 200_000; i++) {
            measuredSink += SlabSupport.minResolvedDy();
            controlSink += SlabSupport.SHIPPED_MIN_RESOLVED_DY;
        }

        long controlBefore = threads.getThreadAllocatedBytes(id);
        for (int i = 0; i < ALLOCATION_LOOP_READS; i++) {
            controlSink += SlabSupport.SHIPPED_MIN_RESOLVED_DY;
        }
        long controlAfter = threads.getThreadAllocatedBytes(id);

        long before = threads.getThreadAllocatedBytes(id);
        for (int i = 0; i < ALLOCATION_LOOP_READS; i++) {
            measuredSink += SlabSupport.minResolvedDy();
        }
        long allocated = threads.getThreadAllocatedBytes(id) - before;
        long control = controlAfter - controlBefore;
        double perRead = (double) allocated / ALLOCATION_LOOP_READS;

        String measured = "reads=" + ALLOCATION_LOOP_READS + " allocatedBytes=" + allocated
                + " controlBytes=" + control + " bytesPerRead=" + perRead
                + " measuredSink=" + measuredSink + " controlSink=" + controlSink;
        System.out.println("[CONSENT-PERF] " + measured);

        ctx.assertTrue(measuredSink != 0.0 && controlSink != 0.0,
                "vacuity guard: a loop was optimised away, so nothing was measured — " + measured);
        ctx.assertTrue(perRead < MAX_ALLOCATED_BYTES_PER_READ,
                "PERF GATE: reading the dy cap allocated " + perRead + " bytes per read, past the "
                        + MAX_ALLOCATED_BYTES_PER_READ + " this gate allows. It must allocate "
                        + "NOTHING — one volatile double load. A box, a map lookup or an Optional "
                        + "on this path is at least 16 bytes EVERY call, so a real regression "
                        + "lands 16x or more above this line. This assertion exists because this "
                        + "project has shipped a perf regression twice: do not raise the number, "
                        + "find what started allocating. " + measured);
        ctx.complete();
    }

    // ── 4. the pinned new-world default ──────────────────────────────────────────────────────

    /**
     * New worlds stay on the shipped alphabet while a tall pass-through stack can resolve a full
     * block too deep under the deeper cap. The public regression cells are
     * {@code DyWindowSuite#passThroughTowerNeverCliffsPastWhatItsSeatCanLower} and
     * {@code DyWindowSuite#passThroughTowerPastTheBudgetNeverRises}.
     */
    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void newWorldsStayOnShippedAlphabetWhileDeepPassThroughDefectIsOpen(TestContext ctx) {
        ctx.assertTrue(!DeepDyConsentAttachment.NEW_WORLD_DEFAULT_DEEP_DY,
                "NEW_WORLD_DEFAULT_DEEP_DY cannot be true while the deep pass-through "
                        + "depth-budget tests still characterize a wrong answer; correct those "
                        + "tests and update this cell with the default.");
        ctx.assertTrue(SlabSupport.capFor(DeepDyConsentAttachment.NEW_WORLD_DEFAULT_DEEP_DY)
                        == SlabSupport.capFor(false),
                "while the new-world default is the shipped alphabet, a brand-new world and a "
                        + "legacy world must derive the SAME cap — otherwise this stage has changed "
                        + "behaviour for somebody, which it undertook not to do");
        ctx.complete();
    }
}
