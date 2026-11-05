package com.slabbed.test;

import com.slabbed.anchor.ClientRenderDyPrediction;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.util.MeshColumnCache;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongPredicate;
import java.util.function.LongToIntFunction;
import java.util.function.Predicate;

/** Differential checks use the same predicate with memoization disabled as the reference. */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class MeshColumnCacheTest {
    private static final BlockPos SUBJECT = new BlockPos(15, 64, 0);

    @GameTest(batch = "slabbed_mesh_column_cache", templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void repeatedStaticColumnsReduceReadsWithoutChangingAnswers(GameTestHelper helper) {
        try (Markers markers = new Markers()) {
            for (BlockPos origin : new BlockPos[]{new BlockPos(32, 64, 48), new BlockPos(-32, -64, -48)}) {
                View view = new View();
                long referenceReads;
                try (MeshColumnCache.Scope ignored = MeshColumnCache.open(null)) {
                    scanSolid(view, origin);
                    referenceReads = view.reads;
                }
                view.reads = 0;
                try (MeshColumnCache.Scope ignored = MeshColumnCache.open(view)) {
                    scanSolid(view, origin);
                }
                require(referenceReads == 344064, "reference must reach all five bounded columns");
                require(view.reads < referenceReads / 2, "the memo must actually remove repeated state reads");
                long cachedReads = view.reads;
                view.reads = 0;
                scanSolid(view, origin);
                require(view.reads == referenceReads && cachedReads < view.reads, "entries must not survive the compile");
            }
        }
        helper.succeed();
    }

    @GameTest(batch = "slabbed_mesh_column_cache", templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void currentMarkersAndPredictionsBypassWarmedColumns(GameTestHelper helper) {
        try (Markers markers = new Markers()) {
            View view = new View();
            BlockPos neighbor = SUBJECT.east();
            try (MeshColumnCache.Scope ignored = MeshColumnCache.open(view)) {
                require(!sameAsReference(view, SUBJECT, Blocks.STONE.defaultBlockState()), "marker-free premise");
                for (byte height : new byte[]{0, -1, -2}) {
                    markers.facts.put(neighbor.below(5).asLong(), height);
                    require(sameAsReference(view, SUBJECT, Blocks.STONE.defaultBlockState()), "neighbor-chunk fact must remain visible, including zero");
                    markers.facts.clear();
                    require(!sameAsReference(view, SUBJECT, Blocks.STONE.defaultBlockState()), "removed fact must not persist");
                }
                markers.anchors.add(neighbor.below(14).asLong());
                require(sameAsReference(view, SUBJECT, Blocks.STONE.defaultBlockState()), "deep neighbor anchor must bypass a warmed false column");
                markers.anchors.clear();
                ClientRenderDyPrediction.record(neighbor.below(3).asLong(), -1);
                require(sameAsReference(view, SUBJECT, Blocks.STONE.defaultBlockState()), "prediction before authoritative fact must reach mesh");
                markers.facts.put(neighbor.below(3).asLong(), (byte) 0);
                require(sameAsReference(view, SUBJECT, Blocks.STONE.defaultBlockState()), "authoritative zero remains a marker");
                require(ClientRenderDyPrediction.isEmpty(), "authoritative lookup must consume prediction fallback");
                markers.facts.clear();
                ClientRenderDyPrediction.record(neighbor.below(3).asLong(), -1);
                for (int i = 0; i < 128; i++) ClientRenderDyPrediction.advanceTick();
                require(ClientRenderDyPrediction.isEmpty(), "prediction expiry premise");
                require(!sameAsReference(view, SUBJECT, Blocks.STONE.defaultBlockState()), "expired prediction must not survive in memo");
                markers.facts.put(neighbor.below(16).asLong(), (byte) -1);
                SlabPlacementHeightAttachment.installClientRenderChunkFactPresence(null);
                SlabAnchorAttachment.clientChunkAnchorPresence = null;
                require(sameAsReference(view, SUBJECT, Blocks.STONE.defaultBlockState()), "unknown presence must remain conservative");
                SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(null);
                ClientRenderDyPrediction.record(SUBJECT.below().asLong(), -1);
                require(sameAsReference(view, SUBJECT, Blocks.STONE.defaultBlockState()), "absent bridge must retain direct prediction fallback");
            }
        }
        helper.succeed();
    }

    @GameTest(batch = "slabbed_mesh_column_cache", templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void signedGeometryAndPackedKeysMatchTheReference(GameTestHelper helper) {
        BlockState[] palette = {
                Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE),
                Blocks.SNOW.defaultBlockState(), Blocks.GLASS.defaultBlockState(),
                Blocks.OAK_LEAVES.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState(),
                Blocks.COBBLESTONE_WALL.defaultBlockState(), Blocks.CHAIN.defaultBlockState(),
                Blocks.RAIL.defaultBlockState(), Blocks.OAK_TRAPDOOR.defaultBlockState()
        };
        try (Markers markers = new Markers()) {
            int trueAnswers = 0, falseAnswers = 0;
            for (int x : new int[]{-17, -1, 0, 15, 16, 31}) {
                for (int y : new int[]{-128, -127, -120, -119, -112, -64, 0, 64}) {
                    for (BlockState below : palette) {
                        View view = new View();
                        BlockPos top = new BlockPos(x, y, -x);
                        for (int depth : new int[]{1, 8, 9, 16, 17}) {
                            view.special.clear();
                            view.special.put(top.below(depth).asLong(), below);
                            try (MeshColumnCache.Scope ignored = MeshColumnCache.open(view)) {
                                for (BlockState subject : palette) {
                                    boolean result = sameAsReference(view, top, subject);
                                    sameAsReference(view, top, subject);
                                    if (result) trueAnswers++; else falseAnswers++;
                                }
                            }
                        }
                    }
                }
            }
            require(trueAnswers > 0 && falseAnswers > 0, "both conservative outcomes must be exercised");
            for (BlockPos far : new BlockPos[]{SUBJECT.offset(1 << 26, 0, 0), SUBJECT.offset(0, 1 << 12, 0), SUBJECT.offset(0, 0, 1 << 26)}) {
                View view = new View();
                view.farTop = far;
                try (MeshColumnCache.Scope ignored = MeshColumnCache.open(view)) {
                    require(!sameAsReference(view, SUBJECT, Blocks.STONE.defaultBlockState()), "representable key premise");
                    require(sameAsReference(view, far, Blocks.STONE.defaultBlockState()), "out-of-envelope coordinates must not alias a packed key");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(batch = "slabbed_mesh_column_cache", templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void dynamicShapesAndBoundsRetainTheOriginalPath(GameTestHelper helper) {
        try (Markers markers = new Markers()) {
            View dynamic = new View();
            BlockState moving = Blocks.MOVING_PISTON.defaultBlockState();
            require(moving.getBlock().hasDynamicShape(), "registered dynamic-shape premise");
            dynamic.special.put(SUBJECT.below().asLong(), moving);
            try (MeshColumnCache.Scope ignored = MeshColumnCache.open(dynamic)) {
                boolean first = screen(dynamic, SUBJECT);
                long initialReads = dynamic.reads;
                dynamic.reads = 0;
                require(screen(dynamic, SUBJECT) == first, "dynamic answer must match reference");
                require(initialReads > 0 && dynamic.reads == initialReads, "dynamic results must never enter the memo");
                sameAsReference(dynamic, SUBJECT, Blocks.STONE.defaultBlockState());
            }
            View bounded = new View();bounded.throwAtEdge = true;
            try (MeshColumnCache.Scope ignored = MeshColumnCache.open(bounded)) {
                require(sameAsReference(bounded, new BlockPos(0, 64, 0), Blocks.STONE.defaultBlockState()), "bounds uncertainty must answer true");
                require(sameAsReference(bounded, new BlockPos(0, 64, 0), Blocks.STONE.defaultBlockState()), "bounds exceptions must not poison the memo");
            }
        }
        helper.succeed();
    }

    @GameTest(batch = "slabbed_mesh_column_cache", templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void nestedThreadsExceptionsAndLaterCompilesDoNotShareEntries(GameTestHelper helper) {
        try (Markers markers = new Markers()) {
            View first = new View(), second = new View();second.special.put(SUBJECT.below().asLong(), Blocks.OAK_SLAB.defaultBlockState());
            MeshColumnCache.Scope outer = MeshColumnCache.open(first);
            try {
                require(!screen(first, SUBJECT), "outer premise");
                first.reads = 0;require(!screen(first, SUBJECT) && first.reads == 4, "outer memo must be active");
                require(screen(second, SUBJECT), "different view identity must not use outer memo");
                try {
                    try (MeshColumnCache.Scope inner = MeshColumnCache.open(second)) {
                        require(screen(second, SUBJECT), "nested view premise");
                        boolean rejected = false;
                        try { outer.close(); } catch (IllegalStateException expected) { rejected = true; }
                        require(rejected, "out-of-order close must not discard a nested scope");
                        throw new ExpectedExit();
                    }
                } catch (ExpectedExit expected) { }
                first.reads = 0;require(!screen(first, SUBJECT) && first.reads == 4, "exception must restore outer scope");
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Thread other = new Thread(() -> {
                    try {
                        boolean rejected = false;
                        try { outer.close(); } catch (IllegalStateException expected) { rejected = true; }
                        require(rejected, "foreign thread must not close owner scope");
                        first.reads = 0;require(!screen(first, SUBJECT) && first.reads == 84, "foreign thread must not inherit memo");
                        try (MeshColumnCache.Scope own = MeshColumnCache.open(first)) {
                            screen(first, SUBJECT);first.reads = 0;
                            require(!screen(first, SUBJECT) && first.reads == 4, "foreign worker owns its own scope");
                        }
                    } catch (Throwable t) { failure.set(t); }
                }, "mesh-column-check");
                other.start();try { other.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); }
                if (failure.get() != null) throw new IllegalStateException(failure.get());
                first.reads = 0;require(!screen(first, SUBJECT) && first.reads == 4, "foreign exit must not clear outer scope");
            } finally { outer.close(); }
            first.reads = 0;require(!screen(first, SUBJECT) && first.reads == 84, "closed entries must be unreachable");
            first.special.put(SUBJECT.below().asLong(), Blocks.OAK_SLAB.defaultBlockState());
            try (MeshColumnCache.Scope next = MeshColumnCache.open(first)) {
                require(screen(first, SUBJECT), "later compile must observe changed geometry");
            }
            try (MeshColumnCache.Scope level = MeshColumnCache.open(helper.getLevel())) {
                try {
                    var current = MeshColumnCache.class.getDeclaredMethod("current", BlockGetter.class);
                    current.setAccessible(true);
                    require(current.invoke(null, helper.getLevel()) == null, "Level must not become an active snapshot");
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        helper.succeed();
    }

    private static void scanSolid(View view, BlockPos origin) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
            pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            require(!screen(view, pos), "solid marker-free screen must remain false");
        }
    }

    private static boolean screen(BlockGetter view, BlockPos pos) {
        return SlabSupport.mayNeedMeshOffsetWork(view, pos, Blocks.STONE.defaultBlockState());
    }

    private static boolean sameAsReference(BlockGetter view, BlockPos pos, BlockState subject) {
        boolean reference;
        try (MeshColumnCache.Scope disabled = MeshColumnCache.open(null)) {
            reference = SlabSupport.mayNeedMeshOffsetWork(view, pos, subject);
        }
        boolean actual = SlabSupport.mayNeedMeshOffsetWork(view, pos, subject);
        require(reference == actual, "cached and uncached mesh screens disagree");
        return actual;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    private static final class ExpectedExit extends RuntimeException { }

    private static class View implements BlockGetter {
        final Map<Long, BlockState> special = new HashMap<>();
        long reads;boolean throwAtEdge;BlockPos farTop;
        @Override public BlockState getBlockState(BlockPos pos) {
            reads++;
            if (throwAtEdge && (pos.getX() < 0 || pos.getZ() < 0)) throw new IndexOutOfBoundsException("bounded snapshot");
            if (farTop != null && pos.equals(farTop.below())) return Blocks.OAK_SLAB.defaultBlockState();
            return special.getOrDefault(pos.asLong(), Blocks.STONE.defaultBlockState());
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }
        @Override public int getHeight() { return 10000; }
        @Override public int getMinBuildHeight() { return -128; }
    }

    private static final class Markers implements AutoCloseable {
        final Map<Long, Byte> facts = new HashMap<>();
        final Set<Long> anchors = new HashSet<>();
        final LongToIntFunction oldLookup;
        final LongPredicate oldPresence, oldAnchorPresence;
        final Predicate<BlockPos> oldAnchor;
        Markers() {
            require(ClientRenderDyPrediction.isEmpty(), "prediction baseline must be empty");
            oldLookup = SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(packed -> {
                Byte fact = facts.get(packed);
                if (fact != null) { ClientRenderDyPrediction.forget(packed);return fact; }
                return SlabPlacementHeightAttachment.ABSENT_HALF_STEPS;
            });
            oldPresence = SlabPlacementHeightAttachment.installClientRenderChunkFactPresence(chunk -> !ClientRenderDyPrediction.isEmpty() || facts.keySet().stream().anyMatch(p -> chunk(p) == chunk));
            oldAnchor = SlabAnchorAttachment.clientAnchorLookup;
            oldAnchorPresence = SlabAnchorAttachment.clientChunkAnchorPresence;
            SlabAnchorAttachment.clientAnchorLookup = pos -> anchors.contains(pos.asLong());
            SlabAnchorAttachment.clientChunkAnchorPresence = chunk -> anchors.stream().anyMatch(p -> chunk(p) == chunk);
        }
        static long chunk(long packed) { return ChunkPos.asLong(BlockPos.getX(packed) >> 4, BlockPos.getZ(packed) >> 4); }
        @Override public void close() {
            ClientRenderDyPrediction.clear();
            SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(oldLookup);
            SlabPlacementHeightAttachment.installClientRenderChunkFactPresence(oldPresence);
            SlabAnchorAttachment.clientAnchorLookup = oldAnchor;
            SlabAnchorAttachment.clientChunkAnchorPresence = oldAnchorPresence;
        }
    }
}
