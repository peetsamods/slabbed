package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class NeighborUpdateInvarianceTest {
    private static final String TEMPLATE = "empty";
    private static final int SUPPORT_Y = 2;

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void anchoredPlacementSurvivesDirectSupportBreak(GameTestHelper ctx) {
        BlockPos support = ctx.absolutePos(new BlockPos(2, SUPPORT_Y, 2));
        BlockPos subject = support.above();
        ServerLevel world = ctx.getLevel();

        placeBottomSlab(world, support);
        placeStoneWithHeldItem(ctx, subject, support);
        double before = renderedBottomY(world, subject);
        double expectedBottom = subject.getY() - 0.5D;
        ctx.assertTrue(Double.compare(before, expectedBottom) == 0,
                "server-thread outline must retain the placed half-block offset before support removal");
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, subject), "placed block must receive its placement anchor");

        world.setBlock(support, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        double after = renderedBottomY(world, subject);
        boolean preserved = Double.compare(before, after) == 0;
        Slabbed.LOGGER.info("[LAW-GATE] row=anchored-support-break before={} after={} preserved={}", before, after, preserved);

        if (lawGateEnabled()) {
            ctx.assertTrue(Double.compare(after, expectedBottom) == 0,
                    "server-thread outline must retain the placed half-block offset after support removal");
            ctx.assertTrue(preserved, "breaking direct support moved a player-placed block");
        }
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void directSupportBreakReachesUnprotectedResolver(GameTestHelper ctx) {
        BlockPos support = ctx.absolutePos(new BlockPos(2, SUPPORT_Y, 2));
        BlockPos subject = support.above();
        ServerLevel world = ctx.getLevel();

        placeBottomSlab(world, support);
        placeStoneWithHeldItem(ctx, subject, support);
        double before = renderedBottomY(world, subject);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, subject), "counterfactual requires a placement anchor before removal");

        SlabAnchorAttachment.removeAnchor(world, subject);
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(world, subject), "counterfactual must remove the placement anchor");
        ctx.assertTrue(SlabPlacementHeightAttachment.remove(
                        world.getChunk(subject.getX() >> 4, subject.getZ() >> 4), subject),
                "counterfactual must remove the numeric placement-height fact");
        ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(
                        world.getChunk(subject.getX() >> 4, subject.getZ() >> 4), subject).isEmpty(),
                "counterfactual must leave neither placement authority behind");
        world.setBlock(support, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        double after = renderedBottomY(world, subject);
        boolean resolverMoved = Double.compare(before, after) != 0;
        Slabbed.LOGGER.info("[LAW-GATE] row=unprotected-support-break before={} after={} moved={}", before, after, resolverMoved);

        ctx.assertTrue(resolverMoved, "counterfactual did not reach the unprotected neighbor-update resolver");
        ctx.succeed();
    }

    /**
     * REQUIRED LAW GATE, connecting subject ({@code fence_on_lowered_slab}): a fence's placed
     * height and placement fact survive the fence's OWN in-place rewrite.
     *
     * <p>A connecting block (fence, wall, pane, and their kin) is the one subject class whose own
     * cell vanilla rewrites on a neighbour edit: when something it connects to appears beside it,
     * vanilla replaces the fence with the same block in a new connection shape. On this Minecraft
     * version that replacement invokes the state-replaced hook on the server unconditionally, same
     * block or not (vanilla's own "different block?" test lives inside the hook body), so the only
     * thing between that rewrite and a cleared fact is the hook's own same-block guard. Reaching
     * mutations, by name: {@code add_connecting_fence_east} and {@code add_full_block_north} (a
     * full block is a fence connection too). Each mutation is asserted to have reached the
     * subject's own cell (its connection property flipped) before the height is compared, so a
     * green here measures the rewrite path and not a mutation that never arrived.
     *
     * <p>The height read is the shipped store-first resolver; this line ships the placement store
     * on, so this row gates the configuration players run.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void fenceOnLoweredSlabSurvivesConnectingNeighborEdits(GameTestHelper ctx) {
        BlockPos support = ctx.absolutePos(new BlockPos(2, SUPPORT_Y, 2));
        BlockPos subject = support.above();
        ServerLevel world = ctx.getLevel();
        List<String> violations = new ArrayList<>();

        for (ConnectingMutation mutation : CONNECTING_MUTATIONS) {
            BlockPos neighbour = subject.relative(mutation.side());
            // Fresh rig per mutation: clear the subject and both neighbour cells, then rebuild.
            world.setBlock(subject.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            world.setBlock(subject.north(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            world.setBlock(subject, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            placeBottomSlab(world, support);
            placeWithHeldItem(ctx, Items.OAK_FENCE, support, Direction.UP);
            ctx.assertTrue(world.getBlockState(subject).is(Blocks.OAK_FENCE),
                    "premise: the held fence must place on the bottom slab");
            double before = SlabSupport.getYOffset(world, subject, world.getBlockState(subject));
            ctx.assertTrue(Double.isFinite(before) && before < -1.0e-6D,
                    "premise: the fence on the bottom slab should have LANDED lowered, got dy " + before);
            ctx.assertTrue(storedFact(world, subject).isPresent(),
                    "premise: the placed fence must carry a stored placement fact");
            ctx.assertTrue(!world.getBlockState(subject).getValue(mutation.connection()),
                    "premise: the fence must start unconnected on the " + mutation.side() + " side");

            world.setBlock(neighbour, mutation.neighbour(), Block.UPDATE_ALL);

            BlockState rewritten = world.getBlockState(subject);
            ctx.assertTrue(rewritten.is(Blocks.OAK_FENCE),
                    mutation.name() + ": the subject cell must still hold the fence");
            ctx.assertTrue(rewritten.getValue(mutation.connection()),
                    mutation.name() + ": the neighbour edit never reached the subject's own cell"
                            + " (no connection formed) - this row measured nothing");
            double after = SlabSupport.getYOffset(world, subject, rewritten);
            boolean factKept = storedFact(world, subject).isPresent();
            boolean preserved = Double.compare(before, after) == 0 && factKept;
            Slabbed.LOGGER.info("[LAW-GATE] row=fence-connecting-rewrite mutation={} before={} after={} factKept={} preserved={}",
                    mutation.name(), before, after, factKept, preserved);
            if (!preserved) {
                violations.add(mutation.name() + ": dy " + before + " -> " + after
                        + (factKept ? "" : " (placement fact CLEARED)"));
            }
        }

        if (lawGateEnabled()) {
            ctx.assertTrue(violations.isEmpty(),
                    "LAW VIOLATION - subject 'fence_on_lowered_slab' moved on a connecting neighbour edit"
                            + " (placed height must survive the same-block rewrite):\n  "
                            + String.join("\n  ", violations));
        }
        ctx.succeed();
    }

    /** A neighbour edit that makes vanilla rewrite a fence's own state in place. */
    private record ConnectingMutation(String name, Direction side, BooleanProperty connection,
                                      BlockState neighbour) {
    }

    private static final List<ConnectingMutation> CONNECTING_MUTATIONS = List.of(
            new ConnectingMutation("add_connecting_fence_east", Direction.EAST,
                    BlockStateProperties.EAST, Blocks.OAK_FENCE.defaultBlockState()),
            new ConnectingMutation("add_full_block_north", Direction.NORTH,
                    BlockStateProperties.NORTH, Blocks.STONE.defaultBlockState())
    );

    private static OptionalInt storedFact(ServerLevel world, BlockPos pos) {
        return SlabPlacementHeightAttachment.storedHalfSteps(world.getChunkAt(pos), pos);
    }

    private static void placeWithHeldItem(GameTestHelper ctx, Item item, BlockPos clicked, Direction face) {
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked)
                .add(face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    private static void placeBottomSlab(ServerLevel world, BlockPos pos) {
        world.setBlock(pos, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
    }

    private static void placeStoneWithHeldItem(GameTestHelper ctx, BlockPos subject, BlockPos hitPos) {
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(subject.getX() + 0.5D, subject.getY(), subject.getZ() + 0.5D);
        ItemStack stack = new ItemStack(Blocks.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(hitPos),
                Direction.UP,
                hitPos,
                false
        );
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        ctx.assertTrue(result.consumesAction(), "held-item placement must be accepted");
        ctx.assertTrue(ctx.getLevel().getBlockState(subject).is(Blocks.STONE), "held-item placement must create the subject block");
    }

    private static double renderedBottomY(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getShape(world, pos).bounds().minY + pos.getY();
    }

    private static boolean lawGateEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty("slabbed.lawGate", "true"));
    }
}
