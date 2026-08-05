package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.C3TestPhaseTrace;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.util.SlabSupport;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/**
 * C3 route-completeness and sole-capture boundary proofs (Slice 2b), translated from the 26.2 donor.
 *
 * <p>Each row pins one boundary of the ONE placement-capture seam: the store must land on the cell
 * vanilla actually filled, must stay empty when nothing was placed, must overwrite a stale value, and
 * must never be written from anywhere but the single call site inside
 * {@code BlockItemPlacementIntentMixin}.
 *
 * <p>ADAPTED FOR THIS LINE: the donor's resolver-value rows ({@code replaceableCellUsesRootAim},
 * {@code noSuperUsesUnstoredFallback}) are dropped — the landing resolver they assert is Slice 2d and
 * does not exist here. The donor's {@code successfulWithoutSetPlacedByUsesTransformedTarget} row is
 * also dropped: it needs a gametest-side {@code BlockItem} mixin fixture (a whole extra mixin config
 * on this source set) purely to skip {@code onPlaced}, and the transformed-target property it proves
 * is already covered by {@link #transformedScaffoldingTarget} against real vanilla Scaffolding.
 * {@link #terrainSlabsFinalStateWritesNothing} likewise drops the donor's custom paired test block in
 * favour of the same compat-override seam applied to a vanilla block.
 */
public final class PlacementCaptureBoundaryGameTest {

    private static final String PREFIX = "slabbed_gametest:placement_capture_boundary_game_test_";

    /** The transformed target must be the sole cell that receives a fact — the clicked cell gets none. */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void transformedScaffoldingTarget(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos clicked = h.getAbsolutePos(new BlockPos(3, 3, 3));
        world.setBlockState(clicked.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(clicked, Blocks.SCAFFOLDING.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(clicked.south(), Blocks.SCAFFOLDING.getDefaultState(), Block.NOTIFY_ALL);
        BlockPos actual = clicked.south(2);
        PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
        player.setYaw(0.0f);
        ActionResult result = useOn(player, new ItemStack(Items.SCAFFOLDING), clicked, Direction.UP);
        h.assertTrue(result.isAccepted()
                        && world.getBlockState(actual).isOf(Blocks.SCAFFOLDING)
                        && Double.isFinite(stored(world, actual))
                        && Double.isNaN(stored(world, clicked)),
                "C3 transformed Scaffolding target was not the sole dy target: result=" + result
                        + " actual=" + world.getBlockState(actual)
                        + " actualDy=" + stored(world, actual)
                        + " staleDy=" + stored(world, clicked));
        pass(h, "transformed_scaffolding_target");
    }

    /**
     * A direct {@code BlockItem.place} with no player never passes through {@code useOnBlock}, so the
     * frame has no aim at all. It must still capture a finite height and must not throw.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void directAimlessNoNpe(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos owner = flatOwner(h);
        BlockPos target = owner.up();
        ItemStack stack = new ItemStack(Items.STONE);
        ItemUsageContext usage =
                new ItemUsageContext(world, null, Hand.MAIN_HAND, stack, hit(owner, Direction.UP)) {
                };
        ActionResult result = ((BlockItem) Items.STONE).place(new ItemPlacementContext(usage));
        h.assertTrue(result != null
                        && result.isAccepted()
                        && Double.isFinite(stored(world, target)),
                "direct AIMLESS place must capture a finite height without throwing; result=" + result
                        + " dy=" + stored(world, target));
        pass(h, "direct_aimless_no_npe");
    }

    /** A refused placement writes nothing: no block changed, so no height was decided. */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void failedPlaceWritesNothing(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos owner = flatOwner(h);
        BlockPos blocked = owner.up();
        world.setBlockState(blocked, Blocks.OBSIDIAN.getDefaultState(), Block.NOTIFY_ALL);
        ActionResult result = useOn(h.createMockPlayer(GameMode.SURVIVAL),
                new ItemStack(Items.STONE), owner, Direction.UP);
        h.assertTrue(!result.isAccepted() && Double.isNaN(stored(world, blocked)),
                "failed placement authored a height; result=" + result + " dy=" + stored(world, blocked));
        pass(h, "failed_place_writes_nothing");
    }

    /** {@code setBlockState} is not a placement: worldgen, structures and commands author no fact. */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void nonItemSetBlockWritesNothing(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos pos = h.getAbsolutePos(new BlockPos(3, 3, 3));
        world.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        h.assertTrue(Double.isNaN(stored(world, pos)),
                "non-item setBlockState unexpectedly entered C3 capture; dy=" + stored(world, pos));
        pass(h, "non_item_set_block_writes_nothing");
    }

    /**
     * A stale ("haunted") value left in the store at a cell must be replaced bit-for-bit by the real
     * placement's own captured height — never averaged with it, never preserved. The support is a
     * bottom slab so the expected height is a genuine {@code -0.5}, and the expectation is taken from
     * the same public entry the capture used rather than hardcoded.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void hauntedStoreOverwritten(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos owner = h.getAbsolutePos(new BlockPos(3, 3, 3));
        world.setBlockState(owner.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(owner,
                Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.NOTIFY_ALL);
        BlockPos target = owner.up();
        forceStore(world, target, 0.25d);
        useOn(h.createMockPlayer(GameMode.SURVIVAL), new ItemStack(Items.STONE), owner, Direction.UP);
        h.assertTrue(world.getBlockState(target).isOf(Blocks.STONE),
                "premise: stone failed to place on the bottom slab at " + target);
        double expected = SlabSupport.getUnstoredYOffset(world, target, world.getBlockState(target));
        double actual = stored(world, target);
        h.assertTrue(Math.abs(expected + 0.5d) <= 1.0e-9d,
                "premise: placement on a bottom slab should read -0.5, got " + expected);
        h.assertTrue(Double.doubleToRawLongBits(actual) == Double.doubleToRawLongBits(expected),
                "real placement did not overwrite the haunted store bit-exactly; stored=" + actual
                        + " expected=" + expected);
        pass(h, "haunted_store_overwritten");
    }

    /**
     * A final state a compat mod owns never enters the store. Terrain Slabs is not on the dev
     * classpath, so the compat boundary is driven through its test-override seam.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void terrainSlabsFinalStateWritesNothing(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos owner = flatOwner(h);
        BlockPos target = owner.up();
        Predicate<BlockState> previous = CompatHooks.shouldSkipSlabSupportTestOverride;
        try {
            CompatHooks.shouldSkipSlabSupportTestOverride = state -> state.isOf(Blocks.GOLD_BLOCK);
            useOn(h.createMockPlayer(GameMode.SURVIVAL),
                    new ItemStack(Items.GOLD_BLOCK), owner, Direction.UP);
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = previous;
        }
        h.assertTrue(world.getBlockState(target).isOf(Blocks.GOLD_BLOCK),
                "premise: compat-owned block failed to place at " + target);
        h.assertTrue(Double.isNaN(stored(world, target)),
                "compat-owned final state entered the C3 store; dy=" + stored(world, target));

        // Control: the same placement without the override DOES author a fact, so the row above
        // proves the compat bail rather than a broken capture.
        BlockPos controlOwner = h.getAbsolutePos(new BlockPos(5, 3, 5));
        world.setBlockState(controlOwner, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        useOn(h.createMockPlayer(GameMode.SURVIVAL),
                new ItemStack(Items.GOLD_BLOCK), controlOwner, Direction.UP);
        h.assertTrue(Double.isFinite(stored(world, controlOwner.up())),
                "control: an un-owned placement must still author a fact");
        pass(h, "terrain_slabs_final_state_writes_nothing");
    }

    /**
     * ORDERING LAW: every legacy marker author must have finished before the placement fact is
     * published, or the store would freeze a height taken before the markers that decide it exist.
     *
     * <p>PORT NOTE: the donor's publish phase carries its {@code setPlacedBy} observation
     * ({@code "publish:setPlacedBy=true"}). This line does not wrap {@code Block.onPlaced} — the five
     * C3 seams here are place / getPlacementContext / getPlacementState / decrementUnlessCreative /
     * place-RETURN — so the publish phase is the constant {@code "publish"} and the asserted trace is
     * exactly {@code [markers_complete, publish]}.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void markerAuthorsCompleteBeforePublish(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos owner = flatOwner(h);
        C3TestPhaseTrace.beginTestPhaseTrace();
        List<String> trace;
        try {
            useOn(h.createMockPlayer(GameMode.SURVIVAL), new ItemStack(Items.STONE), owner, Direction.UP);
            trace = C3TestPhaseTrace.snapshotTestPhaseTrace();
        } finally {
            C3TestPhaseTrace.endTestPhaseTrace();
        }
        h.assertTrue(trace.equals(List.of("markers_complete", "publish"))
                        && Double.isFinite(stored(world, owner.up())),
                "legacy marker completion did not precede dy publication: trace=" + trace
                        + " dy=" + stored(world, owner.up()));
        pass(h, "marker_authors_complete_before_publish");
    }

    /**
     * SINGLE-WRITER structure proof (source text): the placement-time anchor mixin must not have
     * regained its own capture call, and the placement mixin must carry exactly ONE batch write.
     */
    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void singleInitialWriter(TestContext h) {
        try {
            Path root = locateProjectRoot();
            String blockMixin = Files.readString(root.resolve(
                    "src/main/java/com/slabbed/mixin/BlockOnPlacedAnchorMixin.java"));
            String itemMixin = Files.readString(root.resolve(
                    "src/main/java/com/slabbed/mixin/BlockItemPlacementIntentMixin.java"));
            h.assertTrue(!blockMixin.contains("SlabAnchorAttachment.capturePlacementDy("),
                    "C3 single-writer structure regressed: BlockOnPlacedAnchorMixin regained a capture call");
            h.assertTrue(occurrences(itemMixin, "SlabAnchorAttachment.writePlacementDyBatch(") == 1,
                    "C3 single-writer structure regressed: BlockItemPlacementIntentMixin must contain "
                            + "exactly one writePlacementDyBatch call, found "
                            + occurrences(itemMixin, "SlabAnchorAttachment.writePlacementDyBatch("));
        } catch (IOException exception) {
            h.assertTrue(false, "unable to inspect C3 single-writer sources: " + exception);
        }
        pass(h, "single_initial_writer");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────

    static ActionResult useOn(PlayerEntity player, ItemStack stack, BlockPos clicked, Direction face) {
        player.setStackInHand(Hand.MAIN_HAND, stack);
        return stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit(clicked, face)));
    }

    /** Test-only fixture: plant an exact raw value in the store without going through a placement. */
    static void forceStore(ServerWorld world, BlockPos pos, double dy) {
        WorldChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        Long2DoubleOpenHashMap existing = chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
        Long2DoubleOpenHashMap copy = existing == null
                ? new Long2DoubleOpenHashMap()
                : new Long2DoubleOpenHashMap(existing);
        copy.defaultReturnValue(Double.NaN);
        copy.put(pos.asLong(), dy);
        chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, copy);
    }

    static void withFrozen(Runnable body) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            body.run();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    private static BlockPos flatOwner(TestContext h) {
        BlockPos owner = h.getAbsolutePos(new BlockPos(3, 3, 3));
        h.getWorld().setBlockState(owner, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        return owner;
    }

    private static double stored(ServerWorld world, BlockPos pos) {
        return SlabAnchorAttachment.storedPlacementDy(world, pos);
    }

    private static BlockHitResult hit(BlockPos clicked, Direction face) {
        Vec3d location = Vec3d.ofCenter(clicked).add(
                face.getOffsetX() * 0.5d,
                face.getOffsetY() * 0.5d,
                face.getOffsetZ() * 0.5d);
        return new BlockHitResult(location, face, clicked, false);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int index = 0; (index = text.indexOf(needle, index)) >= 0; index += needle.length()) {
            count++;
        }
        return count;
    }

    private static Path locateProjectRoot() throws IOException {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            if (Files.isRegularFile(cursor.resolve(
                    "src/main/java/com/slabbed/mixin/BlockOnPlacedAnchorMixin.java"))) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        throw new IOException("project root not found above " + Path.of("").toAbsolutePath());
    }

    private static void pass(TestContext h, String suffix) {
        Slabbed.LOGGER.info("C3_FOCUSED | {}{} | PASS", PREFIX, suffix);
        h.complete();
    }
}
