package com.slabbed.test;

import com.slabbed.util.DependentRemeshQueue;
import com.slabbed.util.DependentRemeshQueue.BlockRegion;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.test.TestContext;

import java.util.ArrayList;
import java.util.List;

/** Regression coverage for the client dependent-remesh scheduling policy. */
public final class DependentRemeshQueueTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void repeatedChangesInOneSectionCoalesce(TestContext ctx) {
        DependentRemeshQueue queue = new DependentRemeshQueue(8);
        queue.enqueueBlockRegion(1, 2, 3, 4, 5, 6);
        queue.enqueueBlockRegion(3, 1, 2, 8, 7, 9);

        ctx.assertTrue(queue.pendingSectionCount() == 1,
                "two overlapping changes in one section must queue one rebuild, got "
                        + queue.pendingSectionCount());
        List<BlockRegion> drained = new ArrayList<>();
        int count = queue.drain((sectionX, sectionY, sectionZ, region) -> drained.add(region));
        ctx.assertTrue(count == 1 && drained.size() == 1,
                "the coalesced section must drain exactly once, got " + count);
        BlockRegion region = drained.getFirst();
        ctx.assertTrue(region.equals(new BlockRegion(1, 1, 2, 8, 7, 9)),
                "the one rebuild must refresh the union of both changes, got " + region);
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void sectionBoundaryRegionSplitsWithoutDuplicates(TestContext ctx) {
        DependentRemeshQueue queue = new DependentRemeshQueue(8);
        queue.enqueueBlockRegion(15, 15, 15, 16, 16, 16);
        queue.enqueueBlockRegion(15, 15, 15, 16, 16, 16);

        ctx.assertTrue(queue.pendingSectionCount() == 8,
                "a two-block cube crossing all three section boundaries must touch eight sections, got "
                        + queue.pendingSectionCount());
        int count = queue.drain((sectionX, sectionY, sectionZ, region) -> {
            ctx.assertTrue(region.minX() >= sectionX * 16 && region.maxX() <= sectionX * 16 + 15,
                    "x cache region escaped its scheduled section: " + region);
            ctx.assertTrue(region.minY() >= sectionY * 16 && region.maxY() <= sectionY * 16 + 15,
                    "y cache region escaped its scheduled section: " + region);
            ctx.assertTrue(region.minZ() >= sectionZ * 16 && region.maxZ() <= sectionZ * 16 + 15,
                    "z cache region escaped its scheduled section: " + region);
        });
        ctx.assertTrue(count == 8 && queue.pendingSectionCount() == 0,
                "all eight unique sections must drain once under an eight-section budget");
        ctx.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void burstDrainHonorsPerTickBudgetAndPriority(TestContext ctx) {
        int budget = DependentRemeshQueue.MAX_SECTION_REBUILDS_PER_TICK;
        DependentRemeshQueue queue = new DependentRemeshQueue(budget);
        for (int sectionX = 0; sectionX <= budget; sectionX++) {
            int blockX = sectionX * 16;
            queue.enqueueBlockRegion(blockX, 0, 0, blockX, 0, 0);
        }

        int firstTick = queue.drain((sectionX, sectionY, sectionZ, region) -> { });
        ctx.assertTrue(firstTick == budget && queue.pendingSectionCount() == 1,
                "the first tick must stop at the section budget and retain one, got processed="
                        + firstTick + " pending=" + queue.pendingSectionCount());
        int secondTick = queue.drain((sectionX, sectionY, sectionZ, region) -> { });
        ctx.assertTrue(secondTick == 1 && queue.pendingSectionCount() == 0,
                "the retained section must drain on the next tick");
        ctx.assertTrue(DependentRemeshQueue.REQUEST_IMPORTANT_REBUILD,
                "Slabbed's own visible-change rebuilds must request the important renderer lane");
        ctx.complete();
    }
}
