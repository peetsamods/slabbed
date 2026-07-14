package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.compat.CompatHooks;
import com.slabbed.network.PlacementDyPredictionBridge;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/** C3 route-completeness and sole-capture boundary proofs. */
public final class PlacementCaptureBoundaryGameTest {
    private static final double EPS = 1.0e-6d;
    private static final String PREFIX = "slabbed_gametest:placement_capture_boundary_game_test_";

    private static final Identifier PAIR_BLOCK_ID = id("c3_pair_block");
    private static final ResourceKey<Block> PAIR_BLOCK_KEY =
            ResourceKey.create(Registries.BLOCK, PAIR_BLOCK_ID);

    public static final TestPairBlock PAIR_BLOCK = new TestPairBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).setId(PAIR_BLOCK_KEY));

    private static final PlacementDyPredictionBridge.BlockItemTestFixture PAIR_FIXTURE =
            new PlacementDyPredictionBridge.BlockItemTestFixture() {
                @Override
                public BlockState getPlacementState(BlockItem item, BlockPlaceContext context) {
                    return PAIR_BLOCK.defaultBlockState().setValue(
                            BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
                }

                @Override
                public Boolean placeBlock(BlockItem item, BlockPlaceContext context, BlockState state) {
                    BlockPos lower = context.getClickedPos();
                    BlockState lowerState = state.setValue(
                            BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
                    BlockState partnerState = state.setValue(
                            BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
                    boolean first = context.getLevel().setBlock(lower, lowerState, Block.UPDATE_ALL);
                    return first && context.getLevel().setBlock(lower.above(), partnerState, Block.UPDATE_ALL);
                }
            };

    private static final PlacementDyPredictionBridge.BlockItemTestFixture TRANSFORM_SKIP_FIXTURE =
            new PlacementDyPredictionBridge.BlockItemTestFixture() {
                @Override
                public BlockPlaceContext updatePlacementContext(BlockItem item, BlockPlaceContext context) {
                    return BlockPlaceContext.at(context, context.getClickedPos().east(2), Direction.UP);
                }

                @Override
                public Boolean placeBlock(BlockItem item, BlockPlaceContext context, BlockState state) {
                    return context.getLevel().setBlock(
                            context.getClickedPos(), Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                }
            };

    public static final class TestContentEntrypoint implements ModInitializer {
        @Override
        public void onInitialize() {
            register(BuiltInRegistries.BLOCK, PAIR_BLOCK_ID, PAIR_BLOCK);
        }

        private static <T> void register(
                net.minecraft.core.Registry<T> registry,
                Identifier identifier,
                T value
        ) {
            if (!registry.containsKey(identifier)) {
                net.minecraft.core.Registry.register(registry, identifier, value);
            }
        }
    }

    public static final class TestPairBlock extends Block {
        TestPairBlock(BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(
                    BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BlockStateProperties.DOUBLE_BLOCK_HALF);
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void transformedScaffoldingTarget(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos clicked = h.absolutePos(new BlockPos(3, 3, 3));
        world.setBlock(clicked.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(clicked, Blocks.SCAFFOLDING.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(clicked.south(), Blocks.SCAFFOLDING.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos actual = clicked.south(2);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0f);
        InteractionResult result = useOn(player, new ItemStack(Items.SCAFFOLDING), clicked, Direction.UP);
        if (!result.consumesAction()
                || !world.getBlockState(actual).is(Blocks.SCAFFOLDING)
                || !Double.isFinite(stored(world, actual))
                || !Double.isNaN(stored(world, clicked))) {
            throw h.assertionException(actual, "C3 transformed Scaffolding target was not sole dy target: result="
                    + result + " actual=" + world.getBlockState(actual) + " actualDy=" + stored(world, actual)
                    + " staleDy=" + stored(world, clicked));
        }
        pass(h, "transformed_scaffolding_target");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void successfulWithoutSetPlacedByUsesTransformedTarget(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos owner = flatOwner(h);
        BlockPos stale = owner.above();
        BlockPos actual = stale.east(2);
        PlacementDyPredictionBridge.beginTestPhaseTrace();
        List<String> trace;
        try {
            InteractionResult[] result = new InteractionResult[1];
            withTransformSkipFixture(() -> result[0] = useOn(h.makeMockPlayer(GameType.SURVIVAL),
                    new ItemStack(Items.STONE), owner, Direction.UP));
            trace = PlacementDyPredictionBridge.snapshotTestPhaseTrace();
            if (result[0] == null || !result[0].consumesAction()) {
                throw h.assertionException(actual, "test-only transformed no-setPlacedBy route failed");
            }
        } finally {
            PlacementDyPredictionBridge.endTestPhaseTrace();
        }
        if (!world.getBlockState(actual).is(Blocks.GOLD_BLOCK)
                || !Double.isFinite(stored(world, actual))
                || !Double.isNaN(stored(world, stale))
                || !trace.equals(List.of("markers_complete", "publish:setPlacedBy=false"))) {
            throw h.assertionException(actual, "skipped-setPlacedBy route lost transformed target/diagnostic: "
                    + "actual=" + world.getBlockState(actual) + " actualDy=" + stored(world, actual)
                    + " staleDy=" + stored(world, stale) + " trace=" + trace);
        }
        pass(h, "successful_without_set_placed_by_uses_transformed_target");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void replaceableCellUsesRootAim(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos replaced = h.absolutePos(new BlockPos(3, 4, 3));
        world.setBlock(replaced.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        forceStore(world, replaced.below(), -2.0d);
        world.setBlock(replaced, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_ALL);
        withFrozen(() -> useOn(h.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(Items.STONE), replaced, Direction.UP));
        double dy = stored(world, replaced);
        if (!world.getBlockState(replaced).is(Blocks.STONE)
                || Double.doubleToRawLongBits(dy) != Double.doubleToRawLongBits(1.0d)) {
            throw h.assertionException(replaced, "replaceable-cell placement reconstructed the -2 neighbour "
                    + "instead of preserving the root aim; dy=" + dy);
        }
        pass(h, "replaceable_cell_uses_root_aim");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void noSuperUsesUnstoredFallback(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos owner = flatOwner(h);
        forceStore(world, owner, -1.0d);
        withFrozen(() -> useOn(h.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(Items.FLOWER_POT), owner, Direction.UP));
        BlockPos placed = owner.above();
        double dy = stored(world, placed);
        if (!world.getBlockState(placed).is(Blocks.FLOWER_POT)
                || !Double.isFinite(dy)
                || Math.abs(dy + 1.0d) <= EPS) {
            throw h.assertionException(placed, "unsupported no-super route must capture a finite unstored "
                    + "legacy value without gaining C4 TOP-SEAT; dy=" + dy);
        }
        pass(h, "no_super_uses_unstored_fallback");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directAimlessNoNpe(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos owner = flatOwner(h);
        ItemStack stack = new ItemStack(Items.STONE);
        BlockHitResult hit = hit(owner, Direction.UP);
        InteractionResult[] result = new InteractionResult[1];
        withTransformSkipFixture(() -> result[0] = ((BlockItem) Items.STONE).place(
                new BlockPlaceContext(world, null, InteractionHand.MAIN_HAND, stack, hit)));
        BlockPos actual = owner.above().east(2);
        if (result[0] == null || !result[0].consumesAction() || !Double.isFinite(stored(world, actual))) {
            throw h.assertionException(actual, "direct AIMLESS place must capture finite fallback without NPE; "
                    + "result=" + result[0] + " dy=" + stored(world, actual));
        }
        pass(h, "direct_aimless_no_npe");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void failedPlaceWritesNothing(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos owner = flatOwner(h);
        BlockPos blocked = owner.above();
        world.setBlock(blocked, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        InteractionResult result = useOn(h.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(Items.STONE), owner, Direction.UP);
        if (result.consumesAction() || !Double.isNaN(stored(world, blocked))) {
            throw h.assertionException(blocked, "failed placement authored dy; result=" + result
                    + " dy=" + stored(world, blocked));
        }
        pass(h, "failed_place_writes_nothing");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void nonItemSetBlockWritesNothing(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(3, 3, 3));
        world.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        if (!Double.isNaN(stored(world, pos))) {
            throw h.assertionException(pos, "non-item setBlock unexpectedly entered C3 capture");
        }
        pass(h, "non_item_set_block_writes_nothing");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void hauntedStoreOverwritten(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos owner = flatOwner(h);
        BlockPos target = owner.above();
        forceStore(world, owner, -1.0d);
        forceStore(world, target, 0.25d);
        withFrozen(() -> useOn(h.makeMockPlayer(GameType.SURVIVAL),
                new ItemStack(Items.STONE), owner, Direction.UP));
        double dy = stored(world, target);
        if (Double.doubleToRawLongBits(dy) != Double.doubleToRawLongBits(-1.0d)) {
            throw h.assertionException(target, "aimed resolver re-froze haunted target store; dy=" + dy);
        }
        pass(h, "haunted_store_overwritten");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabsFinalStateWritesNothing(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos owner = flatOwner(h);
        Predicate<BlockState> previous = CompatHooks.shouldSkipSlabSupportTestOverride;
        try {
            CompatHooks.shouldSkipSlabSupportTestOverride = state -> state.getBlock() == PAIR_BLOCK;
            withPairFixture(() -> useOn(h.makeMockPlayer(GameType.SURVIVAL),
                    new ItemStack(Items.STONE), owner, Direction.UP));
        } finally {
            CompatHooks.shouldSkipSlabSupportTestOverride = previous;
        }
        BlockPos lower = owner.above();
        if (!world.getBlockState(lower).is(PAIR_BLOCK)
                || !Double.isNaN(stored(world, lower))
                || !Double.isNaN(stored(world, lower.above()))) {
            throw h.assertionException(lower, "compat-owned final pair entered C3 store");
        }
        pass(h, "terrain_slabs_final_state_writes_nothing");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void markerAuthorsCompleteBeforePublish(GameTestHelper h) {
        ServerLevel world = h.getLevel();
        BlockPos owner = flatOwner(h);
        PlacementDyPredictionBridge.beginTestPhaseTrace();
        List<String> trace;
        try {
            useOn(h.makeMockPlayer(GameType.SURVIVAL), new ItemStack(Items.STONE), owner, Direction.UP);
            trace = PlacementDyPredictionBridge.snapshotTestPhaseTrace();
        } finally {
            PlacementDyPredictionBridge.endTestPhaseTrace();
        }
        if (!trace.equals(List.of("markers_complete", "publish:setPlacedBy=true"))
                || !Double.isFinite(stored(world, owner.above()))) {
            throw h.assertionException(owner.above(), "legacy marker completion did not precede dy publication: "
                    + trace);
        }
        pass(h, "marker_authors_complete_before_publish");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void singleInitialWriter(GameTestHelper h) {
        try {
            Path root = locateProjectRoot();
            String blockMixin = Files.readString(root.resolve(
                    "src/main/java/com/slabbed/mixin/BlockOnPlacedAnchorMixin.java"));
            String itemMixin = Files.readString(root.resolve(
                    "src/main/java/com/slabbed/mixin/BlockItemPlacementIntentMixin.java"));
            if (blockMixin.contains("SlabAnchorAttachment.capturePlacementDy(")
                    || itemMixin.contains("writeClientPredictedPlacementDy(")
                    || occurrences(itemMixin, "SlabAnchorAttachment.writePlacementDyBatch(") != 1) {
                throw h.assertionException("C3 single-writer structure regressed");
            }
        } catch (IOException exception) {
            throw h.assertionException("unable to inspect C3 single-writer sources: " + exception);
        }
        pass(h, "single_initial_writer");
    }

    public static InteractionResult useOn(Player player, ItemStack stack, BlockPos clicked, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(clicked, face)));
    }

    public static void withPairFixture(Runnable body) {
        withBlockItemFixture(PAIR_FIXTURE, body);
    }

    private static void withTransformSkipFixture(Runnable body) {
        withBlockItemFixture(TRANSFORM_SKIP_FIXTURE, body);
    }

    private static void withBlockItemFixture(
            PlacementDyPredictionBridge.BlockItemTestFixture fixture,
            Runnable body
    ) {
        PlacementDyPredictionBridge.installBlockItemTestFixture(fixture);
        try {
            body.run();
        } finally {
            PlacementDyPredictionBridge.clearBlockItemTestFixture();
        }
    }

    public static void forceStore(ServerLevel world, BlockPos pos, double dy) {
        LevelChunk chunk = world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        Long2DoubleOpenHashMap existing = chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE);
        Long2DoubleOpenHashMap copy = existing == null
                ? new Long2DoubleOpenHashMap()
                : new Long2DoubleOpenHashMap(existing);
        copy.defaultReturnValue(Double.NaN);
        copy.put(pos.asLong(), dy);
        chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, copy);
    }

    private static BlockPos flatOwner(GameTestHelper h) {
        BlockPos owner = h.absolutePos(new BlockPos(3, 3, 3));
        h.getLevel().setBlock(owner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        return owner;
    }

    private static double stored(ServerLevel world, BlockPos pos) {
        return SlabAnchorAttachment.storedPlacementDy(world, pos);
    }

    private static BlockHitResult hit(BlockPos clicked, Direction face) {
        Vec3 location = Vec3.atCenterOf(clicked).add(
                face.getStepX() * 0.5d,
                face.getStepY() * 0.5d,
                face.getStepZ() * 0.5d);
        return new BlockHitResult(location, face, clicked, false);
    }

    private static void withFrozen(Runnable body) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            body.run();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
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

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("slabbed_gametest", path);
    }

    private static void pass(GameTestHelper h, String suffix) {
        Slabbed.LOGGER.info("C3_FOCUSED | {}{} | PASS", PREFIX, suffix);
        h.succeed();
    }
}
