package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.LongPredicate;
import java.util.function.LongToIntFunction;
import java.util.function.Predicate;

/**
 * The chunk-level presence probe may only change the COST of the tier-0 mesh screen, never its
 * answer. A non-level view (so the mesh bridges apply) is driven over seeded columns — solid
 * ground, slabs, thin layers, air gaps, non-occluding and connecting blocks — with facts and
 * anchors scattered across some chunks and none in others. Every cell must answer exactly as the
 * per-cell reads alone would, with both answers exercised.
 *
 * <p>MUTATION that must redden this row: make the chunk probe answer false for a chunk that
 * holds a marker (the probed pass then misses that marker's cells).
 */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class MeshScreenChunkPresenceTest {
    private static final int SIZE = 48;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 96;

    @GameTest(batch = "slabbed_mesh_screen_presence",
            templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void chunkPresenceNeverChangesTheScreenAnswer(GameTestHelper helper) {
        LongToIntFunction previousLookup = SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(null);
        LongPredicate previousFactPresence = SlabPlacementHeightAttachment.installClientRenderChunkFactPresence(null);
        Predicate<BlockPos> previousAnchor = SlabAnchorAttachment.clientAnchorLookup;
        LongPredicate previousAnchorPresence = SlabAnchorAttachment.clientChunkAnchorPresence;
        try {
            Random random = new Random(0x51ABBEDL);
            FakeView view = new FakeView(random);
            Map<Long, Byte> facts = new HashMap<>();
            LongOpenHashSet anchors = new LongOpenHashSet();
            // Markers in two of the nine chunks only; every other chunk is marker-free.
            for (int i = 0; i < 40; i++) {
                BlockPos pos = new BlockPos(random.nextInt(16), MIN_Y + random.nextInt(MAX_Y - MIN_Y), random.nextInt(16));
                if (random.nextBoolean()) {
                    facts.put(pos.asLong(), (byte) -1);
                } else {
                    anchors.add(pos.asLong());
                }
            }
            for (int i = 0; i < 10; i++) {
                BlockPos pos = new BlockPos(32 + random.nextInt(16), MIN_Y + random.nextInt(MAX_Y - MIN_Y), 32 + random.nextInt(16));
                anchors.add(pos.asLong());
            }
            SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(packed -> {
                Byte fact = facts.get(packed);
                return fact == null ? SlabPlacementHeightAttachment.ABSENT_HALF_STEPS : fact;
            });
            SlabAnchorAttachment.clientAnchorLookup = pos -> anchors.contains(pos.asLong());

            // Reference pass: no presence probes, so every cell takes the per-cell reads.
            SlabPlacementHeightAttachment.installClientRenderChunkFactPresence(null);
            SlabAnchorAttachment.clientChunkAnchorPresence = null;
            boolean[] reference = screenAll(view);

            // Probed pass: presence derived from the same marker sets, per chunk.
            SlabPlacementHeightAttachment.installClientRenderChunkFactPresence(chunkKey -> facts.keySet().stream()
                    .anyMatch(packed -> chunkKeyOf(packed) == chunkKey));
            SlabAnchorAttachment.clientChunkAnchorPresence = chunkKey -> anchors.longStream()
                    .anyMatch(packed -> chunkKeyOf(packed) == chunkKey);
            boolean[] probed = screenAll(view);

            int trues = 0;
            for (int i = 0; i < reference.length; i++) {
                if (reference[i] != probed[i]) {
                    throw new net.minecraft.gametest.framework.GameTestAssertException("cell index " + i + " answered differently with the chunk probe");
                }
                if (reference[i]) {
                    trues++;
                }
            }
            if (trues == 0 || trues == reference.length) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("the seeded view must exercise both answers, got " + trues);
            }
            helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[MESH_SCREEN_PRESENCE] cells=" + reference.length + " true=" + trues + " identical=yes"));
        } finally {
            SlabPlacementHeightAttachment.installClientRenderHalfStepsLookup(previousLookup);
            SlabPlacementHeightAttachment.installClientRenderChunkFactPresence(previousFactPresence);
            SlabAnchorAttachment.clientAnchorLookup = previousAnchor;
            SlabAnchorAttachment.clientChunkAnchorPresence = previousAnchorPresence;
        }
        helper.succeed();
    }

    private static long chunkKeyOf(long packed) {
        BlockPos pos = BlockPos.of(packed);
        return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static boolean[] screenAll(FakeView view) {
        boolean[] answers = new boolean[SIZE * SIZE * (MAX_Y - MIN_Y)];
        int i = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                for (int y = MIN_Y; y < MAX_Y; y++) {
                    cursor.set(x, y, z);
                    answers[i++] = SlabSupport.mayNeedMeshOffsetWork(view, cursor, view.getBlockState(cursor));
                }
            }
        }
        return answers;
    }

    /** A bounded, deterministic block view: never a Level, so it takes the mesh bridges. */
    private static final class FakeView implements BlockGetter {
        private final BlockState[] states;
        private static final BlockState[] PALETTE = {
                Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                Blocks.DIRT.defaultBlockState(), Blocks.AIR.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState(),
                Blocks.SNOW.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState(), Blocks.GLASS.defaultBlockState(),
                Blocks.OAK_FENCE.defaultBlockState(), Blocks.OAK_TRAPDOOR.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState()
        };

        FakeView(Random random) {
            states = new BlockState[SIZE * SIZE * (MAX_Y - MIN_Y)];
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    int surface = 40 + random.nextInt(20);
                    for (int y = MIN_Y; y < MAX_Y; y++) {
                        BlockState state;
                        if (y > surface) {
                            state = random.nextInt(12) == 0 ? PALETTE[random.nextInt(PALETTE.length)] : Blocks.AIR.defaultBlockState();
                        } else if (random.nextInt(8) == 0) {
                            state = PALETTE[random.nextInt(PALETTE.length)];
                        } else {
                            state = Blocks.STONE.defaultBlockState();
                        }
                        states[index(x, y, z)] = state;
                    }
                }
            }
        }

        private static int index(int x, int y, int z) {
            return ((x * SIZE) + z) * (MAX_Y - MIN_Y) + (y - MIN_Y);
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (pos.getX() < 0 || pos.getX() >= SIZE || pos.getZ() < 0 || pos.getZ() >= SIZE
                    || pos.getY() < MIN_Y || pos.getY() >= MAX_Y) {
                return Blocks.AIR.defaultBlockState();
            }
            return states[index(pos.getX(), pos.getY(), pos.getZ())];
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return MAX_Y - MIN_Y;
        }

        @Override
        public int getMinBuildHeight() {
            return MIN_Y;
        }
    }
}
