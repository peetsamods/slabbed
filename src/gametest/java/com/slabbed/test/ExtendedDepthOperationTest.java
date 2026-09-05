package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.AfterBatch;
import net.minecraft.test.BeforeBatch;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** State-transition proof for already-placed blocks at the deepest supported lane. */
public final class ExtendedDepthOperationTest {
    private static final double DEEP_DY = -3.0d;
    private static final String BATCH = "slabbed_extended_depth_operations";
    private static boolean frozenBeforeBatch;

    private record ToggleCase(String id, Item item, Block expected, boolean openProperty) {
    }

    private static final ToggleCase[] TOGGLES = {
            new ToggleCase("door", Items.OAK_DOOR, Blocks.OAK_DOOR, true),
            new ToggleCase("trapdoor", Items.OAK_TRAPDOOR, Blocks.OAK_TRAPDOOR, true),
            new ToggleCase("fence_gate", Items.OAK_FENCE_GATE, Blocks.OAK_FENCE_GATE, true),
            new ToggleCase("lever", Items.LEVER, Blocks.LEVER, false)
    };

    @BeforeBatch(batchId = BATCH)
    public static void enableFrozenDyForBatch(ServerWorld world) {
        frozenBeforeBatch = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
    }

    @AfterBatch(batchId = BATCH)
    public static void restoreFrozenDyAfterBatch(ServerWorld world) {
        SlabAnchorAttachment.FROZEN_DY_ENABLED = frozenBeforeBatch;
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = BATCH)
    public void deepOpenablesKeepTheirPlacedHeightThroughRealUse(TestContext h) {
        List<String> failures = new ArrayList<>();
        ServerWorld world = h.getWorld();
        BlockPos support = h.getAbsolutePos(new BlockPos(3, 24, 3));
        for (ToggleCase toggle : TOGGLES) {
            try {
                    clearArena(world, support);
                    seed(world, support, Blocks.STONE.getDefaultState(), DEEP_DY);
                    BlockHitResult placementHit = rayAtFace(world, support, Direction.UP);
                    requireOwnedHit(placementHit, support, Direction.UP, "placement");
                    PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
                    positionAway(player, rayStart(world, support, Direction.UP));
                    ActionResult placement = useItem(player, toggle.item(), placementHit);
                    BlockPos target = support.up();
                    require(placement.isAccepted(), "placement=" + placement);
                    require(world.getBlockState(target).isOf(toggle.expected()),
                            "placed=" + world.getBlockState(target));
                    requireDeep(world, target, toggle.id() + " before use");

                    BlockPos partner = toggle.expected() == Blocks.OAK_DOOR ? target.up() : null;
                    if (partner != null) {
                        require(world.getBlockState(partner).isOf(Blocks.OAK_DOOR), "door upper half missing");
                        requireDeep(world, partner, "door upper before use");
                    }

                    BlockHitResult operationHit = rayAtFace(world, target, Direction.EAST);
                    requireOwnedHit(operationHit, target, Direction.EAST, "operation");
                    player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                    BlockState before = world.getBlockState(target);
                    boolean propertyBefore = toggle.openProperty()
                            ? before.get(Properties.OPEN)
                            : before.get(Properties.POWERED);
                    ActionResult operation = before.onUse(world, player, operationHit);
                    BlockState after = world.getBlockState(target);
                    boolean propertyAfter = toggle.openProperty()
                            ? after.get(Properties.OPEN)
                            : after.get(Properties.POWERED);
                    require(operation.isAccepted(), "operation=" + operation);
                    require(propertyAfter != propertyBefore,
                            "property did not toggle: before=" + propertyBefore + " after=" + propertyAfter);
                    requireDeep(world, target, toggle.id() + " after use");
                    if (partner != null) {
                        require(world.getBlockState(partner).get(Properties.OPEN) == propertyAfter,
                                "door halves disagree after use");
                        requireDeep(world, partner, "door upper after use");
                    }
            } catch (Throwable throwable) {
                failures.add(toggle.id() + ": " + throwable.getClass().getSimpleName()
                        + ": " + throwable.getMessage());
            }
        }
        finish(h, "deep openables", failures);
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = BATCH)
    public void deepStickyPistonMovesItsHeadAndPayloadInTheSameVisibleLane(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos piston = h.getAbsolutePos(new BlockPos(3, 24, 3));
        BlockPos payloadStart = piston.east();
        BlockPos payloadExtended = piston.east(2);
        BlockPos power = piston.west();
        List<String> failures = new ArrayList<>();
        clearArena(world, piston);
        seed(world, piston, Blocks.STICKY_PISTON.getDefaultState()
                .with(PistonBlock.FACING, Direction.EAST), DEEP_DY);
        seed(world, payloadStart, Blocks.STONE.getDefaultState(), DEEP_DY);
        check(failures, deep(world, piston), "piston base was not seeded at -3");
        check(failures, deep(world, payloadStart), "payload was not seeded at -3");

        h.runAtTick(1, () ->
                world.setBlockState(power, Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL));

        h.runAtTick(6, () -> {
            BlockState base = world.getBlockState(piston);
            check(failures, base.isOf(Blocks.STICKY_PISTON)
                            && base.contains(PistonBlock.EXTENDED)
                            && base.get(PistonBlock.EXTENDED),
                    "sticky piston did not settle extended: " + base);
            check(failures, world.getBlockState(payloadStart).isOf(Blocks.PISTON_HEAD),
                    "generated piston head missing: " + world.getBlockState(payloadStart));
            check(failures, world.getBlockState(payloadExtended).isOf(Blocks.STONE),
                    "payload did not move one cell: " + world.getBlockState(payloadExtended));
            check(failures, deep(world, piston), "extended piston base left -3");
            check(failures, deep(world, payloadStart), "generated piston head did not occupy -3");
            check(failures, deep(world, payloadExtended),
                    "horizontally moved payload did not carry its authored -3 height");
            check(failures, sameBits(stored(world, payloadExtended), DEEP_DY),
                    "moved payload has no raw -3 placement fact at its new cell");
            world.setBlockState(power, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        });

        h.runAtTick(12, () -> {
            BlockState base = world.getBlockState(piston);
            check(failures, base.isOf(Blocks.STICKY_PISTON)
                            && base.contains(PistonBlock.EXTENDED)
                            && !base.get(PistonBlock.EXTENDED),
                    "sticky piston did not settle retracted: " + base);
            check(failures, world.getBlockState(payloadStart).isOf(Blocks.STONE),
                    "sticky piston did not return payload: " + world.getBlockState(payloadStart));
            check(failures, world.getBlockState(payloadExtended).isAir(),
                    "extended payload cell did not clear: " + world.getBlockState(payloadExtended));
            check(failures, Double.isNaN(stored(world, payloadExtended)),
                    "extended payload cell retained a stale placement fact");
            check(failures, deep(world, piston), "retracted piston base left -3");
            check(failures, deep(world, payloadStart), "returned payload left -3");
            finish(h, "deep sticky piston operation", failures);
        });
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = BATCH)
    public void flatStickyPistonDoesNotManufacturePlacementFacts(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos piston = h.getAbsolutePos(new BlockPos(3, 24, 3));
        BlockPos payloadStart = piston.east();
        BlockPos payloadExtended = piston.east(2);
        BlockPos power = piston.west();
        List<String> failures = new ArrayList<>();

        clearArena(world, piston);
        world.setBlockState(piston, Blocks.STICKY_PISTON.getDefaultState()
                .with(PistonBlock.FACING, Direction.EAST), Block.NOTIFY_ALL);
        world.setBlockState(payloadStart, Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
        check(failures, Double.isNaN(stored(world, piston)), "flat piston began with a placement fact");
        check(failures, Double.isNaN(stored(world, payloadStart)), "flat payload began with a placement fact");

        h.runAtTick(1, () ->
                world.setBlockState(power, Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL));
        h.runAtTick(6, () -> {
            check(failures, world.getBlockState(payloadStart).isOf(Blocks.PISTON_HEAD),
                    "flat generated head missing");
            check(failures, world.getBlockState(payloadExtended).isOf(Blocks.STONE),
                    "flat payload did not extend");
            check(failures, Double.isNaN(stored(world, piston)), "flat piston gained a placement fact");
            check(failures, Double.isNaN(stored(world, payloadStart)), "flat head gained a placement fact");
            check(failures, Double.isNaN(stored(world, payloadExtended)),
                    "flat moved payload gained a placement fact");
            world.setBlockState(power, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        });
        h.runAtTick(12, () -> {
            check(failures, world.getBlockState(payloadStart).isOf(Blocks.STONE),
                    "flat sticky piston did not return payload");
            check(failures, Double.isNaN(stored(world, piston)),
                    "flat retracted piston gained a placement fact");
            check(failures, Double.isNaN(stored(world, payloadStart)),
                    "flat returned payload gained a placement fact");
            finish(h, "flat sticky piston control", failures);
        });
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty", batchId = BATCH)
    public void deepVerticalStickyPistonTransfersAndClearsTheSameFacts(TestContext h) {
        ServerWorld world = h.getWorld();
        BlockPos piston = h.getAbsolutePos(new BlockPos(3, 22, 3));
        BlockPos payloadStart = piston.up();
        BlockPos payloadExtended = piston.up(2);
        BlockPos power = piston.west();
        List<String> failures = new ArrayList<>();
        clearArena(world, piston);
        seed(world, piston, Blocks.STICKY_PISTON.getDefaultState()
                .with(PistonBlock.FACING, Direction.UP), DEEP_DY);
        seed(world, payloadStart, Blocks.STONE.getDefaultState(), DEEP_DY);

        h.runAtTick(1, () ->
                world.setBlockState(power, Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL));
        h.runAtTick(6, () -> {
            check(failures, world.getBlockState(piston).isOf(Blocks.STICKY_PISTON)
                            && world.getBlockState(piston).get(PistonBlock.EXTENDED),
                    "vertical piston did not settle extended");
            check(failures, world.getBlockState(payloadStart).isOf(Blocks.PISTON_HEAD),
                    "vertical generated head missing");
            check(failures, world.getBlockState(payloadExtended).isOf(Blocks.STONE),
                    "vertical payload did not move");
            check(failures, deep(world, piston), "vertical extended base left -3");
            check(failures, deep(world, payloadStart), "vertical head left -3");
            check(failures, deep(world, payloadExtended), "vertical moved payload left -3");
            check(failures, sameBits(stored(world, payloadExtended), DEEP_DY),
                    "vertical destination lacks transferred raw fact");
            check(failures, Double.isNaN(stored(world, piston.up(3))),
                    "vertical transfer leaked beyond the real destination");
            world.setBlockState(power, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        });
        h.runAtTick(12, () -> {
            check(failures, world.getBlockState(piston).isOf(Blocks.STICKY_PISTON)
                            && !world.getBlockState(piston).get(PistonBlock.EXTENDED),
                    "vertical piston did not settle retracted");
            check(failures, world.getBlockState(payloadStart).isOf(Blocks.STONE),
                    "vertical payload did not return");
            check(failures, world.getBlockState(payloadExtended).isAir(),
                    "vertical old destination did not clear");
            check(failures, deep(world, piston), "vertical retracted base left -3");
            check(failures, deep(world, payloadStart), "vertical returned payload left -3");
            check(failures, Double.isNaN(stored(world, payloadExtended)),
                    "vertical old destination retained a stale fact");
            finish(h, "deep vertical sticky piston operation", failures);
        });
    }

    private static ActionResult useItem(PlayerEntity player, Item item, BlockHitResult hit) {
        ItemStack stack = new ItemStack(item);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        return stack.useOnBlock(new net.minecraft.item.ItemUsageContext(player, Hand.MAIN_HAND, hit));
    }

    private static BlockHitResult rayAtFace(ServerWorld world, BlockPos owner, Direction face) {
        Vec3d start = rayStart(world, owner, face);
        Vec3d center = visibleCenter(world, owner);
        Vec3d end = center.subtract(
                face.getOffsetX() * 0.75d, face.getOffsetY() * 0.75d, face.getOffsetZ() * 0.75d);
        return SlabbedOffsetRaycast.raycast(world, start, end, net.minecraft.block.ShapeContext.absent());
    }

    private static Vec3d rayStart(ServerWorld world, BlockPos owner, Direction face) {
        Vec3d center = visibleCenter(world, owner);
        return center.add(
                face.getOffsetX() * 0.75d, face.getOffsetY() * 0.75d, face.getOffsetZ() * 0.75d);
    }

    private static Vec3d visibleCenter(ServerWorld world, BlockPos pos) {
        VoxelShape outline = world.getBlockState(pos).getOutlineShape(world, pos);
        require(outline != null && !outline.isEmpty(), "ray owner outline is empty");
        net.minecraft.util.math.Box bounds = outline.getBoundingBox();
        return new Vec3d(
                pos.getX() + (bounds.minX + bounds.maxX) * 0.5d,
                pos.getY() + (bounds.minY + bounds.maxY) * 0.5d,
                pos.getZ() + (bounds.minZ + bounds.maxZ) * 0.5d);
    }

    private static void requireOwnedHit(
            BlockHitResult hit, BlockPos owner, Direction face, String label) {
        require(hit.getType() == HitResult.Type.BLOCK
                        && hit.getBlockPos().equals(owner)
                        && hit.getSide() == face,
                label + " hit=" + hit.getType() + " owner=" + hit.getBlockPos()
                        + " face=" + hit.getSide());
    }

    private static void positionAway(PlayerEntity player, Vec3d eye) {
        player.setPosition(eye.x + 8.0d, eye.y - player.getStandingEyeHeight(), eye.z + 8.0d);
    }

    private static void seed(ServerWorld world, BlockPos pos, BlockState state, double dy) {
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
        int writes = SlabAnchorAttachment.writePlacementDyBatch(
                world, Map.of(pos.toImmutable(), Double.doubleToRawLongBits(dy)));
        require(writes == 1, "fixture did not author " + dy + " at " + pos.toShortString());
    }

    private static void requireDeep(ServerWorld world, BlockPos pos, String label) {
        require(deep(world, pos), label + ": stored=" + stored(world, pos)
                + " live=" + SlabSupport.getYOffset(world, pos, world.getBlockState(pos)));
    }

    private static boolean deep(ServerWorld world, BlockPos pos) {
        return sameBits(stored(world, pos), DEEP_DY)
                && sameBits(SlabSupport.getYOffset(world, pos, world.getBlockState(pos)), DEEP_DY);
    }

    private static double stored(ServerWorld world, BlockPos pos) {
        return SlabAnchorAttachment.rawPlacementDyFact(world, pos).valueOrNaN();
    }

    private static boolean sameBits(double left, double right) {
        return Double.doubleToRawLongBits(left) == Double.doubleToRawLongBits(right);
    }

    private static void clearArena(ServerWorld world, BlockPos center) {
        for (int dx = -3; dx <= 4; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!world.getBlockState(pos).isAir()) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }
    }

    private static void check(List<String> failures, boolean condition, String message) {
        if (!condition) {
            failures.add(message);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void finish(TestContext h, String group, List<String> failures) {
        for (String failure : failures) {
            Slabbed.LOGGER.error("[EXTENDED_DEPTH_OPERATION_FAILURE] group={} detail={}", group, failure);
        }
        List<String> first = failures.stream().limit(3)
                .map(failure -> failure.length() <= 220 ? failure : failure.substring(0, 220))
                .toList();
        h.assertTrue(failures.isEmpty(), group + " failures=" + failures.size() + "; first=" + first);
        h.complete();
    }
}
