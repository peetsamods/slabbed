package com.slabbed.test;

import com.slabbed.Slabbed;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
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

/** Real-placement coverage for the finite -3 placement-depth envelope. */
public final class ExtendedDepthPlacementTest {
    private static final double EPSILON = 1.0e-6d;
    private static final double[] DEPTHS = {0.0d, -0.5d, -1.0d, -1.5d, -2.0d, -2.5d, -3.0d};

    private record PlacementCase(String id, Item item, Block support) {
        private PlacementCase(String id, Item item) {
            this(id, item, Blocks.STONE);
        }
    }

    private enum VisibleBand {
        LOWER(0.25d),
        UPPER(0.75d);

        private final double fraction;

        VisibleBand(double fraction) {
            this.fraction = fraction;
        }
    }

    private static final PlacementCase[] FLOOR_CASES = {
            new PlacementCase("full_block", Items.STONE),
            new PlacementCase("bottom_slab", Items.STONE_SLAB),
            new PlacementCase("stairs", Items.STONE_STAIRS),
            new PlacementCase("anvil", Items.ANVIL),
            new PlacementCase("chest_block_entity", Items.CHEST),
            new PlacementCase("hopper_block_entity", Items.HOPPER),
            new PlacementCase("bottom_trapdoor", Items.OAK_TRAPDOOR),
            new PlacementCase("candle", Items.CANDLE),
            new PlacementCase("campfire_block_entity", Items.CAMPFIRE),
            new PlacementCase("soul_campfire_block_entity", Items.SOUL_CAMPFIRE),
            new PlacementCase("carpet", Items.WHITE_CARPET),
            new PlacementCase("snow_layer", Items.SNOW),
            new PlacementCase("use_created_powder_snow", Items.POWDER_SNOW_BUCKET),
            new PlacementCase("floor_torch", Items.TORCH),
            new PlacementCase("soul_torch", Items.SOUL_TORCH),
            new PlacementCase("redstone_torch", Items.REDSTONE_TORCH),
            new PlacementCase("rail", Items.RAIL),
            new PlacementCase("redstone_wire", Items.REDSTONE),
            new PlacementCase("repeater", Items.REPEATER),
            new PlacementCase("comparator", Items.COMPARATOR),
            new PlacementCase("standing_sign", Items.OAK_SIGN),
            new PlacementCase("standing_banner", Items.WHITE_BANNER),
            new PlacementCase("floor_skull", Items.SKELETON_SKULL),
            new PlacementCase("floor_button", Items.ACACIA_BUTTON),
            new PlacementCase("floor_lever", Items.LEVER),
            new PlacementCase("floor_bell", Items.BELL),
            new PlacementCase("fence", Items.OAK_FENCE),
            new PlacementCase("wall", Items.COBBLESTONE_WALL),
            new PlacementCase("pane", Items.GLASS_PANE),
            new PlacementCase("fence_gate", Items.OAK_FENCE_GATE),
            new PlacementCase("flower_pot_block_entity", Items.FLOWER_POT),
            new PlacementCase("decorated_pot_block_entity", Items.DECORATED_POT),
            new PlacementCase("cauldron", Items.CAULDRON),
            new PlacementCase("brewing_stand_block_entity", Items.BREWING_STAND),
            new PlacementCase("cake", Items.CAKE),
            new PlacementCase("sea_pickle", Items.SEA_PICKLE, Blocks.PRISMARINE),
            new PlacementCase("turtle_egg", Items.TURTLE_EGG, Blocks.SAND),
            new PlacementCase("lightning_rod", Items.LIGHTNING_ROD),
            new PlacementCase("end_rod", Items.END_ROD),
            new PlacementCase("enchanting_table_block_entity", Items.ENCHANTING_TABLE),
            new PlacementCase("piston", Items.PISTON),
            new PlacementCase("sticky_piston", Items.STICKY_PISTON),
            new PlacementCase("observer", Items.OBSERVER),
            new PlacementCase("small_flower", Items.DANDELION, Blocks.DIRT),
            new PlacementCase("sapling", Items.OAK_SAPLING, Blocks.DIRT),
            new PlacementCase("cactus", Items.CACTUS, Blocks.SAND),
            new PlacementCase("farmland_crop", Items.WHEAT_SEEDS, Blocks.FARMLAND)
    };

    private static final PlacementCase[] WALL_CASES = {
            new PlacementCase("ladder", Items.LADDER),
            new PlacementCase("wall_torch", Items.TORCH),
            new PlacementCase("wall_sign", Items.OAK_SIGN),
            new PlacementCase("wall_banner", Items.WHITE_BANNER),
            new PlacementCase("wall_skull", Items.SKELETON_SKULL),
            new PlacementCase("wall_button", Items.ACACIA_BUTTON),
            new PlacementCase("wall_lever", Items.LEVER),
            new PlacementCase("wall_bell", Items.BELL),
            new PlacementCase("side_trapdoor", Items.OAK_TRAPDOOR)
    };

    private static final PlacementCase[] CEILING_CASES = {
            new PlacementCase("top_slab", Items.STONE_SLAB),
            new PlacementCase("ceiling_chain", Items.CHAIN),
            new PlacementCase("hanging_lantern", Items.LANTERN),
            new PlacementCase("hanging_sign", Items.OAK_HANGING_SIGN),
            new PlacementCase("down_dripstone", Items.POINTED_DRIPSTONE),
            new PlacementCase("ceiling_button", Items.ACACIA_BUTTON),
            new PlacementCase("ceiling_lever", Items.LEVER),
            new PlacementCase("ceiling_bell", Items.BELL),
            new PlacementCase("top_trapdoor", Items.OAK_TRAPDOOR)
    };

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void floorFamiliesReachEveryDepthThroughRealRaycastAndUse(TestContext h) {
        Failures failures = new Failures();
        withFrozenDy(() -> {
            ServerWorld world = h.getWorld();
            BlockPos owner = h.getAbsolutePos(new BlockPos(3, 8, 3));
            for (double depth : DEPTHS) {
                for (PlacementCase placementCase : FLOOR_CASES) {
                    runCase(failures, placementCase.id() + "@" + depth, () -> {
                        clearArena(world, owner);
                        seedOwner(world, owner, placementCase.support().getDefaultState(), depth);
                        prepareFloorFixture(world, owner, placementCase);
                        BlockHitResult hit = rayAtFace(world, owner, Direction.UP);
                        requireHit(hit, owner, Direction.UP, "support");
                        PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
                        positionPlayer(player, rayStart(world, owner, Direction.UP));
                        ActionResult result = use(player, placementCase.item(), hit);
                        BlockPos placed = owner.up();
                        require(result.isAccepted(), "use was not accepted: " + result
                                + floorFailureContext(world, owner, placementCase));
                        require(isPlacedItem(world.getBlockState(placed), placementCase.item()),
                                "wrong final block: " + world.getBlockState(placed));
                        verifyPlaced(world, placed, depth, Direction.UP);
                    });
                }
            }
        });
        failures.finish(h, "floor families");
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void wallAndCeilingAttachmentsReachEveryDepthThroughValidFaces(TestContext h) {
        Failures failures = new Failures();
        withFrozenDy(() -> {
            ServerWorld world = h.getWorld();
            BlockPos owner = h.getAbsolutePos(new BlockPos(3, 8, 3));
            for (double depth : DEPTHS) {
                for (PlacementCase placementCase : WALL_CASES) {
                    runCase(failures, placementCase.id() + "@" + depth, () -> {
                        clearArena(world, owner);
                        seedOwner(world, owner, Blocks.STONE.getDefaultState(), depth);
                        BlockHitResult hit = rayAtFace(world, owner, Direction.EAST);
                        requireHit(hit, owner, Direction.EAST, "support");
                        PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
                        positionPlayer(player, rayStart(world, owner, Direction.EAST));
                        ActionResult result = use(player, placementCase.item(), hit);
                        BlockPos placed = owner.east();
                        require(result.isAccepted(), "use was not accepted: " + result);
                        require(isPlacedItem(world.getBlockState(placed), placementCase.item()),
                                "wrong final block: " + world.getBlockState(placed));
                        verifyPlaced(world, placed, depth, Direction.EAST);
                    });
                }
                for (PlacementCase placementCase : CEILING_CASES) {
                    runCase(failures, placementCase.id() + "@" + depth, () -> {
                        clearArena(world, owner);
                        seedOwner(world, owner, Blocks.STONE.getDefaultState(), depth);
                        BlockHitResult hit = rayAtFace(world, owner, Direction.DOWN);
                        requireHit(hit, owner, Direction.DOWN, "support");
                        PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
                        positionPlayer(player, rayStart(world, owner, Direction.DOWN));
                        ActionResult result = use(player, placementCase.item(), hit);
                        BlockPos placed = owner.down();
                        require(result.isAccepted(), "use was not accepted: " + result);
                        require(isPlacedItem(world.getBlockState(placed), placementCase.item()),
                                "wrong final block: " + world.getBlockState(placed));
                        verifyPlaced(world, placed, depth, Direction.DOWN);
                    });
                }
            }
        });
        failures.finish(h, "wall and ceiling attachments");
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void pairedDoorAndBedPublishEveryOwnedCellAtEveryDepth(TestContext h) {
        Failures failures = new Failures();
        withFrozenDy(() -> {
            ServerWorld world = h.getWorld();
            BlockPos owner = h.getAbsolutePos(new BlockPos(3, 8, 3));
            for (double depth : DEPTHS) {
                runCase(failures, "door@" + depth, () -> {
                    clearArena(world, owner);
                    seedOwner(world, owner, Blocks.STONE.getDefaultState(), depth);
                    PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
                    BlockHitResult hit = rayAtFace(world, owner, Direction.UP);
                    positionPlayer(player, rayStart(world, owner, Direction.UP));
                    ActionResult result = use(player, Items.OAK_DOOR, hit);
                    require(result.isAccepted(), "door use was not accepted: " + result);
                    verifyPair(world, owner.up(), owner.up(2), Blocks.OAK_DOOR, depth);
                });
                runCase(failures, "bed@" + depth, () -> {
                    clearArena(world, owner);
                    seedOwner(world, owner, Blocks.STONE.getDefaultState(), depth);
                    for (Direction direction : Direction.Type.HORIZONTAL) {
                        seedOwner(world, owner.offset(direction), Blocks.STONE.getDefaultState(), depth);
                    }
                    PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
                    player.setYaw(0.0f);
                    BlockHitResult hit = rayAtFace(world, owner, Direction.UP);
                    positionPlayer(player, rayStart(world, owner, Direction.UP));
                    ActionResult result = use(player, Items.RED_BED, hit);
                    require(result.isAccepted(), "bed use was not accepted: " + result);
                    BlockPos foot = owner.up();
                    require(world.getBlockState(foot).isOf(Blocks.RED_BED), "bed foot missing");
                    BlockPos head = foot.offset(world.getBlockState(foot)
                            .get(net.minecraft.state.property.Properties.HORIZONTAL_FACING));
                    verifyPair(world, foot, head, Blocks.RED_BED, depth);
                });
                runCase(failures, "sunflower_tall_pair@" + depth, () -> {
                    clearArena(world, owner);
                    seedOwner(world, owner, Blocks.DIRT.getDefaultState(), depth);
                    PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
                    BlockHitResult hit = rayAtFace(world, owner, Direction.UP);
                    positionPlayer(player, rayStart(world, owner, Direction.UP));
                    ActionResult result = use(player, Items.SUNFLOWER, hit);
                    require(result.isAccepted(), "sunflower use was not accepted: " + result);
                    verifyPair(world, owner.up(), owner.up(2), Blocks.SUNFLOWER, depth);
                });
            }
        });
        failures.finish(h, "paired placements");
    }

    @GameTest(templateName = "fabric-gametest-api-v1:empty")
    public void deepChainsDripstoneAndCantileversRemainContinuous(TestContext h) {
        Failures failures = new Failures();
        withFrozenDy(() -> {
            ServerWorld world = h.getWorld();
            // Keep the dy=-3 visible bodies above the GameTest barrier floor and neighbouring
            // fixtures; the logical cells stay high while the exact stored depth remains unchanged.
            BlockPos owner = h.getAbsolutePos(new BlockPos(3, 24, 3));
            runCase(failures, "chain_chain_lantern@-3", () -> {
                clearArena(world, owner);
                seedOwner(world, owner, Blocks.STONE.getDefaultState(), -3.0d);
                BlockPos first = placeExtension(h, world, owner, Direction.DOWN, Items.CHAIN, -3.0d);
                BlockPos second = placeExtension(h, world, first, Direction.DOWN, Items.CHAIN, -3.0d);
                BlockPos lantern = placeExtension(h, world, second, Direction.DOWN, Items.LANTERN, -3.0d);
                require(sameBits(cellBottomPlane(owner, -3.0d), cellTopPlane(first, -3.0d)),
                        "owner/first-chain cell contact planes differ (not a mesh oracle)");
                require(sameBits(cellBottomPlane(first, -3.0d), cellTopPlane(second, -3.0d)),
                        "chain/chain cell contact planes differ (not a mesh oracle)");
                require(sameBits(cellBottomPlane(second, -3.0d), cellTopPlane(lantern, -3.0d)),
                        "chain/lantern cell contact planes differ (not a mesh oracle)");
            });
            runCase(failures, "dripstone_dripstone@-3", () -> {
                clearArena(world, owner);
                seedOwner(world, owner, Blocks.STONE.getDefaultState(), -3.0d);
                BlockPos first = placeExtension(h, world, owner, Direction.DOWN, Items.POINTED_DRIPSTONE, -3.0d);
                BlockPos second = placeExtension(h, world, first, Direction.DOWN, Items.POINTED_DRIPSTONE, -3.0d);
                require(sameBits(cellBottomPlane(first, -3.0d), cellTopPlane(second, -3.0d)),
                        "dripstone continuation cell contact planes differ (not a mesh oracle)");
            });
            for (Direction direction : Direction.Type.HORIZONTAL) {
                for (VisibleBand band : VisibleBand.values()) {
                    runCase(failures, "mixed_continuous_cantilever@-3/" + direction + "/" + band, () -> {
                        clearCantileverLines(world, owner, 8);
                        seedOwner(world, owner, Blocks.STONE.getDefaultState(), -3.0d);
                        BlockPos cursor = owner;
                        Item[] extensions = {
                                Items.STONE, Items.STONE_SLAB, Items.STONE, Items.STONE_SLAB,
                                Items.STONE, Items.STONE_SLAB, Items.STONE, Items.STONE_SLAB
                        };
                        for (Item extension : extensions) {
                            cursor = placeHorizontalExtension(
                                    h, world, cursor, direction, band, extension, -3.0d);
                        }
                        for (int step = 0; step <= extensions.length; step++) {
                            BlockPos member = owner.offset(direction, step);
                            require(sameBits(stored(world, member), -3.0d),
                                    "cantilever member reverted at extension " + step + ": "
                                            + stored(world, member));
                            require(sameBits(SlabSupport.getYOffset(
                                            world, member, world.getBlockState(member)), -3.0d),
                                    "cantilever live dy reverted at extension " + step);
                        }
                    });
                }
            }
        });
        failures.finish(h, "deep continuity");
    }

    private static BlockPos placeExtension(
            TestContext h, ServerWorld world, BlockPos owner, Direction face, Item item, double expectedDy) {
        BlockHitResult hit = rayAtFace(world, owner, face);
        requireHit(hit, owner, face, "extension owner");
        PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
        positionPlayer(player, rayStart(world, owner, face));
        ActionResult result = use(player, item, hit);
        BlockPos placed = owner.offset(face);
        require(result.isAccepted(), "extension use was not accepted: " + result);
        require(isPlacedItem(world.getBlockState(placed), item),
                "extension placed wrong block: " + world.getBlockState(placed));
        verifyPlaced(world, placed, expectedDy, face);
        return placed;
    }

    private static BlockPos placeHorizontalExtension(
            TestContext h,
            ServerWorld world,
            BlockPos owner,
            Direction face,
            VisibleBand band,
            Item item,
            double expectedDy
    ) {
        BlockHitResult hit = rayAtHorizontalBand(world, owner, face, band);
        requireHit(hit, owner, face, "cantilever owner " + band);
        PlayerEntity player = h.createMockPlayer(GameMode.SURVIVAL);
        positionPlayer(player, rayStartAtHorizontalBand(world, owner, face, band));
        ActionResult result = use(player, item, hit);
        BlockPos placed = owner.offset(face);
        require(result.isAccepted(), "cantilever use was not accepted: " + result);
        require(isPlacedItem(world.getBlockState(placed), item),
                "cantilever placed wrong block: " + world.getBlockState(placed));
        verifyPlaced(world, placed, expectedDy, face);
        return placed;
    }

    private static void verifyPair(
            ServerWorld world, BlockPos first, BlockPos second, Block block, double expectedDy) {
        require(world.getBlockState(first).isOf(block) && world.getBlockState(second).isOf(block),
                "pair cells do not contain " + block);
        verifyPlacedWithoutMutation(world, first, expectedDy, Direction.EAST);
        verifyPlacedWithoutMutation(world, second, expectedDy, Direction.EAST);
        Direction mutationDirection = Direction.EAST;
        if (first.east().equals(second)) {
            mutationDirection = Direction.WEST;
        }
        BlockPos mutation = first.offset(mutationDirection);
        world.setBlockState(mutation, Blocks.GLASS.getDefaultState(), Block.NOTIFY_ALL);
        world.removeBlock(mutation, false);
        require(world.getBlockState(first).isOf(block) && world.getBlockState(second).isOf(block),
                "neighbor edit removed/replaced pair");
        require(sameBits(stored(world, first), expectedDy) && sameBits(stored(world, second), expectedDy),
                "neighbor edit changed pair dy");
    }

    private static void verifyPlaced(ServerWorld world, BlockPos pos, double expectedDy, Direction visibleFace) {
        verifyPlacedWithoutMutation(world, pos, expectedDy, visibleFace);
        BlockState before = world.getBlockState(pos);
        Direction mutationDirection = visibleFace.getAxis() == Direction.Axis.X
                ? Direction.NORTH
                : Direction.EAST;
        BlockPos mutation = pos.offset(mutationDirection);
        world.setBlockState(mutation, Blocks.GLASS.getDefaultState(), Block.NOTIFY_ALL);
        world.removeBlock(mutation, false);
        require(world.getBlockState(pos).getBlock() == before.getBlock(), "neighbor edit removed/replaced block");
        require(sameBits(stored(world, pos), expectedDy)
                        && sameBits(SlabSupport.getYOffset(world, pos, world.getBlockState(pos)), expectedDy),
                "neighbor edit changed dy");
    }

    private static void verifyPlacedWithoutMutation(
            ServerWorld world, BlockPos pos, double expectedDy, Direction visibleFace) {
        BlockState state = world.getBlockState(pos);
        require(sameBits(stored(world, pos), expectedDy),
                "stored dy=" + stored(world, pos) + " wanted=" + expectedDy);
        require(sameBits(SlabSupport.getYOffset(world, pos, state), expectedDy),
                "live dy=" + SlabSupport.getYOffset(world, pos, state) + " wanted=" + expectedDy);
        require(state.canPlaceAt(world, pos), "placed state is not legally supported");
        VoxelShape outline = state.getOutlineShape(world, pos);
        require(outline != null && !outline.isEmpty(), "outline is empty");
        if (!outline.isEmpty()) {
            double minY = outline.getBoundingBox().minY;
            double maxY = outline.getBoundingBox().maxY;
            require(minY >= expectedDy - EPSILON && maxY <= expectedDy + 1.5d + EPSILON,
                    "outline is not in the stored lane: [" + minY + "," + maxY + "] dy=" + expectedDy);
        }
        BlockHitResult targetHit = rayAtFace(world, pos, visibleFace);
        require(targetHit.getType() == HitResult.Type.BLOCK && targetHit.getBlockPos().equals(pos),
                "post-place ray owned by " + targetHit.getBlockPos() + " face=" + targetHit.getSide());
    }

    private static boolean isPlacedItem(BlockState state, Item item) {
        if (item == Items.POWDER_SNOW_BUCKET) {
            return state.isOf(Blocks.POWDER_SNOW);
        }
        if (item == Items.TORCH && state.isOf(Blocks.WALL_TORCH)) {
            return true;
        }
        if (item == Items.OAK_SIGN && state.isOf(Blocks.OAK_WALL_SIGN)) {
            return true;
        }
        if (item == Items.WHITE_BANNER && state.isOf(Blocks.WHITE_WALL_BANNER)) {
            return true;
        }
        if (item == Items.SKELETON_SKULL && state.isOf(Blocks.SKELETON_WALL_SKULL)) {
            return true;
        }
        return item instanceof BlockItem blockItem && state.isOf(blockItem.getBlock());
    }

    private static void prepareFloorFixture(
            ServerWorld world, BlockPos owner, PlacementCase placementCase) {
        if (placementCase.item() == Items.WHEAT_SEEDS) {
            // Crop placement has a vanilla light-or-sky requirement in addition to farmland.
            // Keep a deterministic light source outside the target, support, ray, and mutation cells.
            world.setBlockState(owner.add(2, 1, 0), Blocks.GLOWSTONE.getDefaultState(), Block.NOTIFY_ALL);
        }
    }

    private static String floorFailureContext(
            ServerWorld world, BlockPos owner, PlacementCase placementCase) {
        if (placementCase.item() != Items.WHEAT_SEEDS) {
            return "";
        }
        BlockPos intended = owner.up();
        BlockState crop = Blocks.WHEAT.getDefaultState();
        return " support=" + world.getBlockState(owner)
                + " cropCanPlaceAt=" + crop.canPlaceAt(world, intended)
                + " light=" + world.getLightLevel(intended)
                + " skyVisible=" + world.isSkyVisible(intended);
    }

    private static BlockHitResult rayAtFace(ServerWorld world, BlockPos owner, Direction face) {
        Vec3d start = rayStart(world, owner, face);
        Vec3d center = visibleCenter(world, owner);
        Vec3d end = center.subtract(face.getOffsetX() * 2.0d, face.getOffsetY() * 2.0d,
                face.getOffsetZ() * 2.0d);
        return SlabbedOffsetRaycast.raycast(world, start, end, net.minecraft.block.ShapeContext.absent());
    }

    private static BlockHitResult rayAtHorizontalBand(
            ServerWorld world, BlockPos owner, Direction face, VisibleBand band) {
        Vec3d start = rayStartAtHorizontalBand(world, owner, face, band);
        Vec3d point = visiblePointAtBand(world, owner, band);
        Vec3d end = point;
        return SlabbedOffsetRaycast.raycast(
                world, start, end, net.minecraft.block.ShapeContext.absent());
    }

    private static Vec3d rayStartAtHorizontalBand(
            ServerWorld world, BlockPos owner, Direction face, VisibleBand band) {
        Vec3d point = visiblePointAtBand(world, owner, band);
        // Start one quarter-block outside a full-width face and stop at the visible center.
        // This is a real face-crossing ray without traversing unrelated fixture cells.
        return point.add(face.getOffsetX() * 0.75d, 0.0d, face.getOffsetZ() * 0.75d);
    }

    private static Vec3d visiblePointAtBand(ServerWorld world, BlockPos pos, VisibleBand band) {
        VoxelShape outline = world.getBlockState(pos).getOutlineShape(world, pos);
        require(outline != null && !outline.isEmpty(), "band ray owner outline is empty");
        net.minecraft.util.math.Box bounds = outline.getBoundingBox();
        return new Vec3d(
                pos.getX() + (bounds.minX + bounds.maxX) * 0.5d,
                pos.getY() + bounds.minY + (bounds.maxY - bounds.minY) * band.fraction,
                pos.getZ() + (bounds.minZ + bounds.maxZ) * 0.5d);
    }

    private static Vec3d rayStart(ServerWorld world, BlockPos owner, Direction face) {
        Vec3d center = visibleCenter(world, owner);
        return center.add(face.getOffsetX() * 2.0d, face.getOffsetY() * 2.0d,
                face.getOffsetZ() * 2.0d);
    }

    private static Vec3d visibleCenter(ServerWorld world, BlockPos pos) {
        VoxelShape outline = world.getBlockState(pos).getOutlineShape(world, pos);
        require(outline != null && !outline.isEmpty(), "ray owner outline is empty");
        net.minecraft.util.math.Box bounds = outline.getBoundingBox();
        return new Vec3d(pos.getX() + (bounds.minX + bounds.maxX) * 0.5d,
                pos.getY() + (bounds.minY + bounds.maxY) * 0.5d,
                pos.getZ() + (bounds.minZ + bounds.maxZ) * 0.5d);
    }

    private static void requireHit(BlockHitResult hit, BlockPos owner, Direction face, String label) {
        require(hit.getType() == HitResult.Type.BLOCK, label + " ray missed");
        require(hit.getBlockPos().equals(owner), label + " ray selected " + hit.getBlockPos());
        require(hit.getSide() == face, label + " ray face=" + hit.getSide() + " wanted=" + face);
    }

    private static ActionResult use(PlayerEntity player, Item item, BlockHitResult hit) {
        ItemStack stack = new ItemStack(item);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        return stack.useOnBlock(new net.minecraft.item.ItemUsageContext(player, Hand.MAIN_HAND, hit));
    }

    private static void positionPlayer(PlayerEntity player, Vec3d eye) {
        // The hit result, not the mock player's body, is the placement authority in this fixture.
        // Keep the body well away so deep logical cells cannot reject a valid placement as overlap.
        player.setPosition(eye.x + 8.0d, eye.y - player.getStandingEyeHeight(), eye.z + 8.0d);
    }

    private static void seedOwner(ServerWorld world, BlockPos pos, BlockState state, double dy) {
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
        int writes = SlabAnchorAttachment.writePlacementDyBatch(
                world, Map.of(pos.toImmutable(), Double.doubleToRawLongBits(dy)));
        require(writes == 1, "fixture did not author owner dy=" + dy + " at " + pos.toShortString());
        require(sameBits(stored(world, pos), dy), "fixture owner dy mismatch");
    }

    private static double stored(ServerWorld world, BlockPos pos) {
        return SlabAnchorAttachment.rawPlacementDyFact(world, pos).valueOrNaN();
    }

    private static double cellTopPlane(BlockPos pos, double dy) {
        return pos.getY() + dy + 1.0d;
    }

    private static double cellBottomPlane(BlockPos pos, double dy) {
        return pos.getY() + dy;
    }

    private static boolean sameBits(double left, double right) {
        return Double.doubleToRawLongBits(left) == Double.doubleToRawLongBits(right);
    }

    private static void clearArena(ServerWorld world, BlockPos center) {
        for (int dx = -2; dx <= 8; dx++) {
            for (int dy = -6; dy <= 5; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!world.getBlockState(pos).isAir()) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }
    }

    private static void clearCantileverLines(ServerWorld world, BlockPos owner, int length) {
        for (Direction direction : Direction.Type.HORIZONTAL) {
            for (int step = 0; step <= length; step++) {
                BlockPos pos = owner.offset(direction, step);
                if (!world.getBlockState(pos).isAir()) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
            }
        }
        for (int down = 1; down <= 4; down++) {
            BlockPos pos = owner.down(down);
            if (!world.getBlockState(pos).isAir()) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
    }

    private static void withFrozenDy(Runnable action) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            action.run();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
    }

    private static void runCase(Failures failures, String id, Runnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            failures.add(id + ": " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Failures {
        private static final int MAX_DETAILS = 40;
        private final List<String> details = new ArrayList<>();
        private int total;

        void add(String detail) {
            total++;
            if (details.size() < MAX_DETAILS) {
                details.add(detail);
            }
        }

        void finish(TestContext h, String group) {
            for (String detail : details) {
                Slabbed.LOGGER.error("[EXTENDED_DEPTH_FAILURE] group={} detail={}", group, detail);
            }
            List<String> shortDetails = details.stream().limit(3)
                    .map(detail -> detail.length() <= 220 ? detail : detail.substring(0, 220))
                    .toList();
            h.assertTrue(total == 0, group + " failures=" + total + "; first=" + shortDetails);
            h.complete();
        }
    }
}
