package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.client.ClientDy;
import com.slabbed.util.SlabSupport;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.DataMapHooks;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class SlabPlacementHeightLifecycleTest {
    private static final String TEMPLATE = "empty";

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void flatPlacementStoresExplicitZero(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos subject = support.above();
        world.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        placeHeldBlock(ctx, Blocks.STONE.defaultBlockState(), support, Direction.UP, 0.0F);
        ctx.assertTrue(world.getBlockState(subject).is(Blocks.STONE),
                "flat held-item placement must create the subject");
        assertStored(ctx, world, subject, 0, "flat placement must store an explicit zero fact");

        world.setBlock(subject.east(), bottomSlab(), Block.UPDATE_ALL);
        assertStored(ctx, world, subject, 0, "a neighbour edit must not change the stored flat fact");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void loweredAnchorPlacementStoresExactHeight(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos subject = support.above();
        world.setBlock(support, bottomSlab(), Block.UPDATE_ALL);

        placeHeldBlock(ctx, Blocks.STONE.defaultBlockState(), support, Direction.UP, 0.0F);
        ctx.assertTrue(SlabAnchorAttachment.isAnchored(world, subject),
                "lowered full-block placement must retain its legacy anchor");
        assertImmediateHeight(ctx, world, subject, -0.5d);
        assertStored(ctx, world, subject, -1, "lowered anchor must store one negative half-step");

        world.setBlock(support, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        assertStored(ctx, world, subject, -1, "support removal must not change the stored height");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void loweredNonAnchorPlacementStillStoresExactHeight(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        world.setBlock(ctx.absolutePos(new BlockPos(2, 1, 2)),
                Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(2, 2, 2)), bottomSlab(), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(2, 3, 2)),
                Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(ctx.absolutePos(new BlockPos(2, 4, 2)), bottomSlab(), Block.UPDATE_ALL);
        BlockPos owner = ctx.absolutePos(new BlockPos(2, 5, 2));
        world.setBlock(owner, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        assertImmediateHeight(ctx, world, owner, -1.0d);
        BlockPos subject = owner.east();

        Vec3 loweredSideHit = new Vec3(
                owner.getX() + 1.0d,
                owner.getY() - 0.75d,
                owner.getZ() + 0.5d);
        placeHeldBlockAt(ctx, Blocks.STONE_SLAB.defaultBlockState(),
                owner, Direction.EAST, loweredSideHit, 0.0F);
        ctx.assertTrue(world.getBlockState(subject).is(Blocks.STONE_SLAB),
                "lowered non-anchor slab must place through the real held-item route");
        ctx.assertTrue(!SlabAnchorAttachment.isAnchored(world, subject),
                "the numeric fact must not broaden slab anchor eligibility");
        assertImmediateHeight(ctx, world, subject, -1.0d);
        assertStored(ctx, world, subject, -2,
                "a lowered placement that earns no anchor must still store its exact height");

        world.setBlock(subject.east(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        assertStored(ctx, world, subject, -2, "a neighbour edit must not change the unanchored fact");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void transformedScaffoldingWritesActualCellOnly(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos clicked = ctx.absolutePos(new BlockPos(3, 2, 2));
        BlockPos middle = clicked.south();
        BlockPos actual = clicked.south(2);
        world.setBlock(clicked.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(clicked, Blocks.SCAFFOLDING.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(middle, Blocks.SCAFFOLDING.defaultBlockState(), Block.UPDATE_ALL);

        placeHeldBlock(ctx, Blocks.SCAFFOLDING.defaultBlockState(), clicked, Direction.UP, 0.0F);
        ctx.assertTrue(world.getBlockState(actual).is(Blocks.SCAFFOLDING),
                "Scaffolding must transform the clicked context to its actual placed cell");
        assertStored(ctx, world, actual, 0, "the transformed cell must receive the placement fact");
        assertAbsent(ctx, world, clicked, "the original clicked cell must not receive a new fact");
        assertAbsent(ctx, world, middle, "an existing intermediate cell must not receive a new fact");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void hauntedFactIsOverwrittenByRealPlacement(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos subject = support.above();
        world.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(subject, bottomSlab(), Block.UPDATE_ALL);
        injectRawHalfSteps(world, subject, 7);
        ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(
                        world.getChunkAt(subject), subject).orElse(Integer.MIN_VALUE) == 7,
                "test premise must install a stale fact");

        InteractionResult result = useHeldBlock(ctx, bottomSlab(), subject, Direction.UP, 0.0F);
        ctx.assertTrue(result.consumesAction(), "the real held-slab merge must be accepted");
        BlockState merged = world.getBlockState(subject);
        ctx.assertTrue(merged.is(Blocks.STONE_SLAB)
                        && merged.getValue(SlabBlock.TYPE) == SlabType.DOUBLE,
                "the real held-item route must merge the bottom slab in place");
        ctx.assertTrue(Double.doubleToRawLongBits(
                        SlabSupport.getUnstoredYOffset(world, subject, merged))
                        == Double.doubleToRawLongBits(0.0d),
                "the merged slab's store-blind placement height must be flat");
        assertStored(ctx, world, subject, 0,
                "an in-place real placement must overwrite stale truth with its store-blind height");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void connectorPlacementSettlesAfterFactPublication(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos targetSupport = ctx.absolutePos(new BlockPos(3, 2, 3));
        BlockPos target = targetSupport.above();
        BlockPos neighborSupport = targetSupport.east();
        BlockPos neighbor = neighborSupport.above();
        BlockPos sentinelSupport = neighborSupport.east();
        BlockPos sentinel = sentinelSupport.above();
        StringBuilder failures = new StringBuilder();

        Block[] connectorBlocks = {
                Blocks.OAK_FENCE,
                Blocks.COBBLESTONE_WALL,
                Blocks.IRON_BARS
        };
        for (Block connectorBlock : connectorBlocks) {
            exerciseConnectorPlacement(
                    ctx,
                    world,
                    targetSupport,
                    target,
                    neighborSupport,
                    neighbor,
                    sentinelSupport,
                    sentinel,
                    connectorBlock,
                    Blocks.STONE.defaultBlockState(),
                    0,
                    0,
                    true,
                    failures,
                    "same-height");
            exerciseConnectorPlacement(
                    ctx,
                    world,
                    targetSupport,
                    target,
                    neighborSupport,
                    neighbor,
                    sentinelSupport,
                    sentinel,
                    connectorBlock,
                    bottomSlab(),
                    0,
                    -1,
                    false,
                    failures,
                    "stepped");
        }

        clearConnectorFixture(
                world, targetSupport, target, neighborSupport, neighbor, sentinelSupport, sentinel);
        String failureText = failures.length() <= 900
                ? failures.toString()
                : failures.substring(0, 900) + " ...";
        ctx.assertTrue(failures.isEmpty(), "connector settlement mismatches: " + failureText);
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void propertyAndEligibleKindChangesPreserveFact(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos support = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos subject = support.above();
        world.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        placeHeldBlock(ctx, Blocks.GRASS_BLOCK.defaultBlockState(), support, Direction.UP, 0.0F);
        assertStored(ctx, world, subject, 0, "grass placement must start with an explicit flat fact");

        BlockState snowy = world.getBlockState(subject).setValue(BlockStateProperties.SNOWY, true);
        world.setBlock(subject, snowy, Block.UPDATE_ALL);
        assertStored(ctx, world, subject, 0, "a same-block property update must preserve the fact");

        world.setBlock(subject, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        ctx.assertTrue(world.getBlockState(subject).is(Blocks.DIRT),
                "eligible grass-to-dirt transformation must complete");
        assertStored(ctx, world, subject, 0,
                "an eligible in-place block-kind transformation must preserve the fact");
        ctx.assertTrue(SlabAnchorAttachment.isFrozenFlat(world, subject),
                "eligible transformation must preserve the legacy flat marker too");

        BlockPos toolSupport = ctx.absolutePos(new BlockPos(5, 2, 2));
        BlockPos toolSubject = toolSupport.above();
        world.setBlock(toolSupport, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        placeHeldBlock(ctx, Blocks.STONE.defaultBlockState(), toolSupport, Direction.UP, 0.0F);
        assertStored(ctx, world, toolSubject, 0,
                "tool-modification row must start with an explicit flat fact");

        BlockPos decoySupport = ctx.absolutePos(new BlockPos(5, 2, 5));
        BlockPos decoySubject = decoySupport.above();
        world.setBlock(decoySupport, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        placeHeldBlock(ctx, Blocks.STONE.defaultBlockState(), decoySupport, Direction.UP, 0.0F);
        assertStored(ctx, world, decoySubject, 0,
                "tool-token decoy must start with an explicit flat fact");

        postToolModification(ctx, world, toolSubject, Blocks.DIRT.defaultBlockState());
        world.setBlock(decoySubject, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        assertAbsent(ctx, world, decoySubject,
                "a tool token for another cell must not preserve the next replacement");

        postToolModification(ctx, world, toolSubject, Blocks.DIRT.defaultBlockState());
        world.setBlock(toolSubject, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        assertStored(ctx, world, toolSubject, 0,
                "NeoForge-authorized modded tool transformation must preserve the fact");

        BlockPos copperSupport = ctx.absolutePos(new BlockPos(2, 2, 5));
        BlockPos copperSubject = copperSupport.above();
        world.setBlock(copperSupport, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        placeHeldBlock(ctx, Blocks.COPPER_BLOCK.defaultBlockState(), copperSupport, Direction.UP, 0.0F);
        assertStored(ctx, world, copperSubject, 0,
                "data-map transition row must start with an explicit flat fact");
        Block nextCopper = DataMapHooks.getNextOxidizedStage(Blocks.COPPER_BLOCK);
        ctx.assertTrue(nextCopper != null && nextCopper != Blocks.COPPER_BLOCK,
                "NeoForge oxidation data map must provide a distinct next stage");
        world.setBlock(copperSubject, nextCopper.defaultBlockState(), Block.UPDATE_ALL);
        assertStored(ctx, world, copperSubject, 0,
                "NeoForge data-map transformation must preserve the fact");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void breakAndIncompatibleReplacementClearFact(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos firstSupport = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos first = firstSupport.above();
        world.setBlock(firstSupport, bottomSlab(), Block.UPDATE_ALL);
        placeHeldBlock(ctx, Blocks.STONE.defaultBlockState(), firstSupport, Direction.UP, 0.0F);
        assertStored(ctx, world, first, -1, "replacement row requires a stored fact");

        world.setBlock(first, Blocks.OAK_SLAB.defaultBlockState(), Block.UPDATE_ALL);
        assertAbsent(ctx, world, first, "an incompatible occupant replacement must clear the fact");

        BlockPos secondSupport = ctx.absolutePos(new BlockPos(5, 2, 2));
        BlockPos second = secondSupport.above();
        world.setBlock(secondSupport, bottomSlab(), Block.UPDATE_ALL);
        placeHeldBlock(ctx, Blocks.STONE.defaultBlockState(), secondSupport, Direction.UP, 0.0F);
        assertStored(ctx, world, second, -1, "break row requires a stored fact");

        world.destroyBlock(second, false);
        assertAbsent(ctx, world, second, "a genuine break must clear the fact");

        BlockPos thirdSupport = ctx.absolutePos(new BlockPos(2, 2, 5));
        BlockPos third = thirdSupport.above();
        world.setBlock(thirdSupport, bottomSlab(), Block.UPDATE_ALL);
        placeHeldBlock(ctx, Blocks.STONE.defaultBlockState(), thirdSupport, Direction.UP, 0.0F);
        assertStored(ctx, world, third, -1, "unrelated replacement row requires a stored fact");

        world.setBlock(third, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        assertAbsent(ctx, world, third,
                "an unrelated full-block occupant must not inherit the old placement fact");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void failedAndSyntheticPlacementWriteNothing(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos synthetic = ctx.absolutePos(new BlockPos(2, 3, 5));
        world.setBlock(synthetic, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        assertAbsent(ctx, world, synthetic, "direct world mutation is not a player placement");

        BlockPos support = ctx.absolutePos(new BlockPos(5, 2, 2));
        BlockPos blocked = support.above();
        world.setBlock(support, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(blocked, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
        injectRawHalfSteps(world, blocked, 6);
        ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(
                        world.getChunkAt(blocked), blocked).orElse(Integer.MIN_VALUE) == 6,
                "failed-placement row must start with a pre-existing fact");
        InteractionResult result = useHeldBlock(
                ctx, Blocks.STONE.defaultBlockState(), support, Direction.UP, 0.0F);
        ctx.assertTrue(!result.consumesAction(), "blocked placement must be refused");
        assertStored(ctx, world, blocked, 6,
                "a refused placement must neither author nor clear a fact");

        BlockPos directSupport = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos directSubject = directSupport.above();
        world.setBlock(directSupport, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        placeDirectBlock(ctx, Blocks.STONE.defaultBlockState(), directSupport, Direction.UP, 0.0F);
        ctx.assertTrue(world.getBlockState(directSubject).is(Blocks.STONE),
                "direct BlockItem.place control must create its block");
        assertAbsent(ctx, world, directSubject,
                "direct BlockItem.place must remain outside held-use capture authority");

        BlockPos finalSupport = ctx.absolutePos(new BlockPos(5, 2, 5));
        BlockPos finalSubject = finalSupport.above();
        world.setBlock(finalSupport, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        placeHeldBlock(ctx, Blocks.STONE.defaultBlockState(), finalSupport, Direction.UP, 0.0F);
        assertStored(ctx, world, finalSubject, 0,
                "a normal held placement after refusal and direct place must still capture");

        world.setBlock(synthetic, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos carpetSupport = ctx.absolutePos(new BlockPos(2, 2, 5));
        BlockPos carpet = carpetSupport.above();
        world.setBlock(carpetSupport, bottomSlab(), Block.UPDATE_ALL);
        placeHeldBlock(ctx, Blocks.WHITE_CARPET.defaultBlockState(), carpetSupport, Direction.UP, 0.0F);
        ctx.assertTrue(world.getBlockState(carpet).is(Blocks.WHITE_CARPET),
                "the thin-layer control must place through the real held-item route");
        assertAbsent(ctx, world, carpet,
                "a support-relative carpet must not author its own placement-height fact");
        ctx.succeed();
    }

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void doorAndBedRemainExplicitlyLegacy(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos doorSupport = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos doorLower = doorSupport.above();
        BlockPos doorUpper = doorLower.above();
        world.setBlock(doorSupport, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        placeHeldBlock(ctx, Blocks.OAK_DOOR.defaultBlockState(), doorSupport, Direction.UP, 0.0F);
        ctx.assertTrue(world.getBlockState(doorLower).is(Blocks.OAK_DOOR)
                        && world.getBlockState(doorUpper).is(Blocks.OAK_DOOR),
                "door premise must place both companion cells");
        assertAbsent(ctx, world, doorLower, "door lower half must remain on the legacy path");
        assertAbsent(ctx, world, doorUpper, "door upper half must remain on the legacy path");

        BlockPos bedSupport = ctx.absolutePos(new BlockPos(5, 2, 4));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlock(bedSupport.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        BlockPos bedFirst = bedSupport.above();
        placeHeldBlock(ctx, Blocks.WHITE_BED.defaultBlockState(), bedSupport, Direction.UP, 0.0F);
        BlockPos bedSecond = findAdjacent(world, bedFirst, Blocks.WHITE_BED.defaultBlockState());
        ctx.assertTrue(world.getBlockState(bedFirst).is(Blocks.WHITE_BED) && bedSecond != null,
                "bed premise must place a reciprocal pair");
        assertAbsent(ctx, world, bedFirst, "bed first half must remain on the legacy path");
        assertAbsent(ctx, world, bedSecond, "bed companion half must remain on the legacy path");
        ctx.succeed();
    }

    /**
     * A replaceable cell — short grass, ferns, flowers, a single snow layer — is consumed by the
     * placement rather than stood on, so the block lands in the replaced cell's own coordinates
     * and must record the height that cell's support gives it. Recording the height of the cell
     * above or below puts the block a full cell away from where it was aimed.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void placingIntoAReplaceableCellRecordsThatCellsOwnHeight(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        StringBuilder failures = new StringBuilder();

        // Genuinely replaceable subjects only. A flower is NOT replaceable in vanilla, so a
        // flower lane would assert vanilla's behaviour rather than this line's.
        BlockState[] replaceables = {
                Blocks.SHORT_GRASS.defaultBlockState(),
                Blocks.FERN.defaultBlockState(),
                Blocks.DEAD_BUSH.defaultBlockState(),
                Blocks.SNOW.defaultBlockState()
        };
        int lane = 0;
        for (BlockState replaceable : replaceables) {
            BlockPos support = ctx.absolutePos(new BlockPos(3 + lane * 3, 2, 12));
            BlockPos subject = support.above();
            lane++;

            world.setBlock(support, bottomSlab(), Block.UPDATE_ALL);
            world.setBlock(subject, replaceable, Block.UPDATE_ALL);

            placeHeldBlock(ctx, Blocks.STONE.defaultBlockState(), subject, Direction.UP, 0.0f);

            String name = replaceable.getBlock().getDescriptionId();
            if (!world.getBlockState(subject).is(Blocks.STONE)) {
                failures.append(" | ").append(name).append(": the placement did not consume the cell");
                continue;
            }
            OptionalInt actual = stored(world, subject);
            if (actual.isEmpty() || actual.getAsInt() != -1) {
                failures.append(" | ").append(name)
                        .append(": expected half-steps -1 from the slab below, got ").append(actual);
            }
        }

        ctx.assertTrue(failures.isEmpty(),
                "replaceable-cell placement recorded a wrong height:" + failures);
        ctx.succeed();
    }

    /**
     * The compound-stack law, stated the way the reference line ships it: a course that actually
     * DESCENDS onto a half-height seat spends depth; a course that merely rests on the one below
     * passes the depth along; and derived descent never exceeds the resolved floor
     * ({@code SlabSupport.minResolvedDy()}).
     *
     * <p>Without the floor, consecutive bottom-slab courses over a lowered base are a descending
     * staircase — each placement lands half a block deeper than the course below, until the
     * targetable envelope — which in play reads as the placed slab snapping down out from under
     * the aim.
     *
     * <p>Lane A places bottom-slab courses in consecutive cells with the real wall-assisted
     * gesture (clicking the lower half of an adjacent column's side face). Lane B builds the
     * combine-to-double tower with the real top-face gesture; full-height courses inherit, so the
     * whole tower shares its base's depth.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void stackedSlabCoursesStopDescendingAtTheResolvedFloor(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        StringBuilder failures = new StringBuilder();

        // ── Lane A: the mixed slab/block column over a lowered base (the live repro) ─────
        // Alternating courses, each placed with a plain top-face click on the course below.
        // Every SLAB course offers a half-height seat, so without the floor each slab eats
        // another half block and the column descends: -0.5, -1.0, -1.0, -1.5, -1.5, -2.0 —
        // each later placement landing visibly deeper than its preview. The reference line
        // clamps every derived seat at the resolved floor, so the column descends until it
        // reaches that floor and every course from there down shares it.
        BlockPos ground = ctx.absolutePos(new BlockPos(2, 2, 15));
        world.setBlock(ground, bottomSlab(), Block.UPDATE_ALL);
        BlockPos stone = ground.above();
        placeHeldBlock(ctx, Blocks.STONE.defaultBlockState(), ground, Direction.UP, 0.0f);
        assertStored(ctx, world, stone, -1, "lane A base: stone on a bottom slab must land -0.5");

        // Five courses: enough to cross the old descent point twice while staying under
        // the test structure's ceiling (a sixth course reads the template's barrier).
        BlockState[] courses = {
                bottomSlab(), Blocks.STONE.defaultBlockState(),
                bottomSlab(), Blocks.STONE.defaultBlockState(),
                bottomSlab()
        };
        // Only the SLAB courses owe another half step - a full-height course passes its
        // support height along unchanged - so an alternating column descends once per
        // pair. The floor is the envelope, so it reaches one step further than before.
        int[] expectedHalfSteps = {-1, -2, -2, -3, -3};
        BlockPos below = stone;
        for (int course = 0; course < courses.length; course++) {
            BlockPos cell = below.above();
            // A real crosshair ray lands on the VISUAL top plane - on a lowered support that
            // plane sits below the grid top, and the aim law reads the landing off the hit.
            // A grid-plane hit here would be a point no live ray can reach.
            BlockState belowState = world.getBlockState(below);
            double belowTop = (belowState.getBlock() instanceof SlabBlock ? 0.5d : 1.0d)
                    + SlabSupport.getYOffset(world, below, belowState);
            Vec3 visualHit = new Vec3(below.getX() + 0.5d, below.getY() + belowTop, below.getZ() + 0.5d);
            InteractionResult use = useHeldBlockAt(ctx, courses[course], below, Direction.UP, visualHit, 0.0f);
            BlockState placed = world.getBlockState(cell);
            if (!use.consumesAction() || !placed.is(courses[course].getBlock())) {
                failures.append(" | course ").append(course + 1)
                        .append(": expected ").append(courses[course].getBlock())
                        .append(" accepted, got result=").append(use)
                        .append(" cell=").append(placed);
                break;
            }
            OptionalInt storedFact = stored(world, cell);
            if (storedFact.isEmpty() || storedFact.getAsInt() != expectedHalfSteps[course]) {
                failures.append(" | course ").append(course + 1)
                        .append(" (").append(placed.getBlock()).append(")")
                        .append(": expected half-steps ").append(expectedHalfSteps[course])
                        .append(", got ").append(storedFact);
            }
            below = cell;
        }

        // ── Lane B: the combine-to-double tower inherits its base depth, every course ────
        BlockPos groundB = ctx.absolutePos(new BlockPos(6, 2, 15));
        world.setBlock(groundB, bottomSlab(), Block.UPDATE_ALL);
        BlockPos stoneB = groundB.above();
        placeHeldBlock(ctx, Blocks.STONE.defaultBlockState(), groundB, Direction.UP, 0.0f);

        BlockPos cursor = stoneB;
        for (int course = 1; course <= 3; course++) {
            BlockPos cell = cursor.above();
            placeHeldBlock(ctx, bottomSlab(), cursor, Direction.UP, 0.0f);
            BlockState half = world.getBlockState(cell);
            if (!half.is(Blocks.STONE_SLAB)) {
                failures.append(" | tower course ").append(course)
                        .append(": expected a slab above, got ").append(half);
                break;
            }
            OptionalInt halfFact = stored(world, cell);
            if (halfFact.isEmpty() || halfFact.getAsInt() != -1) {
                failures.append(" | tower course ").append(course)
                        .append(" (bottom half): expected half-steps -1, got ").append(halfFact);
            }
            placeHeldBlock(ctx, bottomSlab(), cell, Direction.UP, 0.0f);
            BlockState merged = world.getBlockState(cell);
            if (!merged.is(Blocks.STONE_SLAB) || merged.getValue(SlabBlock.TYPE) != SlabType.DOUBLE) {
                failures.append(" | tower course ").append(course)
                        .append(": top-face combine must yield a double, got ").append(merged);
                break;
            }
            OptionalInt mergedFact = stored(world, cell);
            if (mergedFact.isEmpty() || mergedFact.getAsInt() != -1) {
                failures.append(" | tower course ").append(course)
                        .append(" (combined): the merge must preserve -1, got ").append(mergedFact);
            }
            cursor = cell;
        }

        ctx.assertTrue(failures.isEmpty(),
                "stacked courses must stop at the resolved floor and towers must inherit:"
                        + failures);
        ctx.succeed();
    }

    private static InteractionResult placeHeldBlock(
            GameTestHelper ctx,
            BlockState heldState,
            BlockPos clicked,
            Direction face,
            float yaw
    ) {
        InteractionResult result = useHeldBlock(ctx, heldState, clicked, face, yaw);
        ctx.assertTrue(result.consumesAction(), "held-item placement must be accepted");
        return result;
    }

    private static InteractionResult placeHeldBlockAt(
            GameTestHelper ctx,
            BlockState heldState,
            BlockPos clicked,
            Direction face,
            Vec3 hitLocation,
            float yaw
    ) {
        InteractionResult result = useHeldBlockAt(ctx, heldState, clicked, face, hitLocation, yaw);
        ctx.assertTrue(result.consumesAction(), "held-item placement must be accepted");
        return result;
    }

    private static InteractionResult placeDirectBlock(
            GameTestHelper ctx,
            BlockState heldState,
            BlockPos clicked,
            Direction face,
            float yaw
    ) {
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(clicked.getX() + 0.5d, clicked.getY() + 2.0d, clicked.getZ() + 0.5d);
        player.setYRot(yaw);
        ItemStack stack = new ItemStack(heldState.getBlock());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hitLocation = Vec3.atCenterOf(clicked).add(
                face.getStepX() * 0.5d,
                face.getStepY() * 0.5d,
                face.getStepZ() * 0.5d);
        BlockHitResult hit = new BlockHitResult(hitLocation, face, clicked, false);
        BlockPlaceContext context = new BlockPlaceContext(
                new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        InteractionResult result = ((BlockItem) stack.getItem()).place(context);
        ctx.assertTrue(result.consumesAction(), "direct BlockItem.place control must be accepted");
        return result;
    }

    private static void postToolModification(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos pos,
            BlockState finalState
    ) {
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5d, pos.getY() + 2.0d, pos.getZ() + 0.5d);
        ItemStack tool = new ItemStack(Items.IRON_AXE);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        BlockEvent.BlockToolModificationEvent modification =
                new BlockEvent.BlockToolModificationEvent(
                        world.getBlockState(pos), context, ItemAbilities.AXE_STRIP, false);
        modification.setFinalState(finalState);
        NeoForge.EVENT_BUS.post(modification);
        ctx.assertTrue(modification.getFinalState() == finalState,
                "tool-modification proof requires the requested final state");
    }

    private static InteractionResult useHeldBlock(
            GameTestHelper ctx,
            BlockState heldState,
            BlockPos clicked,
            Direction face,
            float yaw
    ) {
        Vec3 hitLocation = Vec3.atCenterOf(clicked).add(
                face.getStepX() * 0.5d,
                face.getStepY() * 0.5d,
                face.getStepZ() * 0.5d);
        return useHeldBlockAt(ctx, heldState, clicked, face, hitLocation, yaw);
    }

    private static InteractionResult useHeldBlockAt(
            GameTestHelper ctx,
            BlockState heldState,
            BlockPos clicked,
            Direction face,
            Vec3 hitLocation,
            float yaw
    ) {
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(clicked.getX() + 0.5d, clicked.getY() + 2.0d, clicked.getZ() + 0.5d);
        player.setYRot(yaw);
        ItemStack stack = new ItemStack(heldState.getBlock());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(hitLocation, face, clicked, false);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /**
     * The floor itself, pinned where it BINDS rather than where it is merely touched.
     *
     * <p>The stacked-course row above measures the descent RULE and stops at -1.5, half the depth
     * the floor sits at, because a sixth course reads the template's barrier. The deep-consent
     * fixture is exactly TANGENT - it seats a subject precisely AT the floor, so {@code Math.max}
     * is provably a no-op there. Between them the suite reported the floor green while nothing
     * bound it: deleting the clamp from all nine derived-descent sites left the whole suite
     * passing. This row is what that mutation must fail against.
     *
     * <p>Built as a COMPARISON, not an absolute. Two supports half a block apart - one at the
     * floor, one half a step above it - must hand their subjects the SAME height, because the
     * deeper one's derived seat lands past the floor and is held. Unclamped they differ by
     * exactly that half block, so the row fails alone and fails for its own reason. The absolute
     * value is asserted too, but the comparison is the discriminator: it stays meaningful if the
     * floor constant ever moves again, which is precisely how the existing rows went stale.
     *
     * <p>Every depth is derived from {@code MIN_TARGETABLE_DY}. A literal here would stop
     * measuring the boundary the moment the ruling moves it, which is the defect this row exists
     * to close, not to repeat.
     */
    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = TEMPLATE)
    public void aDerivedSeatPastTheFloorIsHeldAtIt(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        double floor = com.slabbed.util.PlacementDepthPolicy.MIN_TARGETABLE_DY;
        int floorHalfSteps = (int) Math.round(floor / 0.5d);

        // At the floor: a bottom slab here offers a seat half a step BELOW the floor, so the
        // subject's derived height is out of bounds before the clamp sees it.
        BlockPos atFloor = ctx.absolutePos(new BlockPos(2, 3, 11));
        // Half a step above it: the tangent case, where the seat lands exactly ON the floor.
        BlockPos aboveFloor = ctx.absolutePos(new BlockPos(5, 3, 11));

        double[] seats = new double[2];
        BlockPos[] cells = {atFloor, aboveFloor};
        int[] supportSteps = {floorHalfSteps, floorHalfSteps + 1};
        for (int i = 0; i < cells.length; i++) {
            BlockPos subject = cells[i];
            BlockPos support = subject.below();
            world.setBlock(support, bottomSlab(), Block.UPDATE_ALL);
            world.setBlock(subject, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                            world.getChunkAt(support), support, supportSteps[i]),
                    "premise: the support depth must be storable - a refusal here means the"
                            + " envelope no longer admits the floor and the row measures nothing;"
                            + " halfSteps=" + supportSteps[i]);
            ctx.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(
                            world.getChunkAt(subject), subject).isEmpty(),
                    "premise: the subject must stay factless so its height is DERIVED, not read");
            seats[i] = SlabSupport.getYOffset(world, subject, world.getBlockState(subject));
        }

        double deepSeat = seats[0];
        double tangentSeat = seats[1];

        ctx.assertTrue(Math.abs(tangentSeat - floor) <= 1.0e-9,
                "calibration: over a support half a step above the floor the derived seat must land"
                        + " exactly ON the floor; a miss means this run is not deriving seats at all"
                        + " and the claim below proves nothing; observed " + tangentSeat);
        ctx.assertTrue(Math.abs(deepSeat - tangentSeat) <= 1.0e-9,
                "two supports half a block apart must seat their subjects at the SAME height once"
                        + " both derivations reach the floor - unclamped the deeper one sinks half a"
                        + " block further; floorSupport=" + deepSeat + " tangentSupport="
                        + tangentSeat);
        ctx.assertTrue(Math.abs(deepSeat - floor) <= 1.0e-9,
                "and that shared height must be the floor itself; floor=" + floor
                        + " observed=" + deepSeat);
        ctx.succeed();
    }

    private static BlockState bottomSlab() {
        return Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    private static void exerciseConnectorPlacement(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos targetSupport,
            BlockPos target,
            BlockPos neighborSupport,
            BlockPos neighbor,
            BlockPos sentinelSupport,
            BlockPos sentinel,
            Block connectorBlock,
            BlockState supportState,
            int staleTargetHalfSteps,
            int expectedTargetHalfSteps,
            boolean expectedConnected,
            StringBuilder failures,
            String row
    ) {
        clearConnectorFixture(
                world, targetSupport, target, neighborSupport, neighbor, sentinelSupport, sentinel);
        world.setBlock(targetSupport, supportState, Block.UPDATE_ALL);
        world.setBlock(neighborSupport, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(sentinelSupport, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        placeHeldBlock(ctx, connectorBlock.defaultBlockState(), neighborSupport, Direction.UP, 0.0F);
        placeHeldBlock(ctx, connectorBlock.defaultBlockState(), sentinelSupport, Direction.UP, 0.0F);
        assertStored(ctx, world, neighbor, 0,
                connectorBlock + " " + row + " neighbor must start with a flat fact");
        assertStored(ctx, world, sentinel, 0,
                connectorBlock + " " + row + " sentinel must start with a flat fact");
        BlockState sentinelBefore = world.getBlockState(sentinel);

        world.setBlock(target, Blocks.SNOW.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        ctx.assertTrue(world.getBlockState(target).is(Blocks.SNOW),
                connectorBlock + " " + row + " requires a replaceable target occupant");

        ctx.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                        world.getChunk(target.getX() >> 4, target.getZ() >> 4),
                        target,
                        staleTargetHalfSteps),
                connectorBlock + " " + row + " must install a conflicting target fact");
        recordExact(failures, SlabPlacementHeightAttachment.storedOffset(world, target), 0.0d,
                connectorBlock + " " + row + " stale target premise");
        recordExact(failures, SlabSupport.getYOffset(
                        world, target, connectorBlock.defaultBlockState()), 0.0d,
                connectorBlock + " " + row + " placement-time stale target premise");
        placeHeldBlock(ctx, connectorBlock.defaultBlockState(), target, Direction.UP, 0.0F);

        BlockState targetState = world.getBlockState(target);
        BlockState neighborState = world.getBlockState(neighbor);
        BlockState sentinelAfter = world.getBlockState(sentinel);
        ctx.assertTrue(targetState.is(connectorBlock)
                        && neighborState.is(connectorBlock)
                        && sentinelAfter.is(connectorBlock),
                connectorBlock + " " + row + " must retain all real placed connectors");
        ctx.assertTrue(sentinelAfter == sentinelBefore,
                connectorBlock + " " + row + " must not cascade into the second-hop sentinel");
        assertStored(ctx, world, target, expectedTargetHalfSteps,
                connectorBlock + " " + row + " must publish the final target fact");
        assertStored(ctx, world, neighbor, 0,
                connectorBlock + " " + row + " must preserve the neighbor fact");
        assertStored(ctx, world, sentinel, 0,
                connectorBlock + " " + row + " must preserve the sentinel fact");

        recordConnection(
                failures,
                horizontalConnection(targetState, Direction.EAST),
                expectedConnected,
                connectorBlock + " " + row + " target east side");
        recordConnection(
                failures,
                horizontalConnection(neighborState, Direction.WEST),
                expectedConnected,
                connectorBlock + " " + row + " neighbor west side");
        recordConnection(
                failures,
                horizontalConnection(neighborState, Direction.EAST),
                true,
                connectorBlock + " " + row + " neighbor east sentinel side");
        recordConnection(
                failures,
                horizontalConnection(sentinelAfter, Direction.WEST),
                true,
                connectorBlock + " " + row + " sentinel west side");

        double targetDy = expectedTargetHalfSteps * 0.5d;
        recordExact(failures, SlabPlacementHeightAttachment.storedOffset(world, target), targetDy,
                connectorBlock + " " + row + " stored dy");
        recordExact(failures, SlabSupport.getYOffset(world, target, targetState), targetDy,
                connectorBlock + " " + row + " server dy");
        recordExact(failures, ClientDy.dyFor(world, target, targetState), targetDy,
                connectorBlock + " " + row + " client dy");
        VoxelShape targetOutline = targetState.getShape(world, target, CollisionContext.empty());
        VoxelShape targetCollision = targetState.getCollisionShape(world, target, CollisionContext.empty());
        recordShapeFloor(ctx, failures,
                targetOutline,
                targetDy,
                connectorBlock + " " + row + " outline");
        recordShapeFloor(ctx, failures,
                targetCollision,
                targetDy,
                connectorBlock + " " + row + " collision");
        if (!expectedConnected) {
            BlockState expectedDisconnected = withHorizontalConnection(
                    targetState, Direction.EAST, false);
            recordShapeMatch(failures,
                    targetOutline,
                    expectedDisconnected.getShape(world, target, CollisionContext.empty()),
                    connectorBlock + " " + row + " outline connection geometry");
            recordShapeMatch(failures,
                    targetCollision,
                    expectedDisconnected.getCollisionShape(world, target, CollisionContext.empty()),
                    connectorBlock + " " + row + " collision connection geometry");
        }
    }

    private static boolean horizontalConnection(BlockState state, Direction direction) {
        return switch (direction) {
            case NORTH -> state.hasProperty(BlockStateProperties.NORTH)
                    ? state.getValue(BlockStateProperties.NORTH)
                    : state.hasProperty(BlockStateProperties.NORTH_WALL)
                            && state.getValue(BlockStateProperties.NORTH_WALL) != WallSide.NONE;
            case EAST -> state.hasProperty(BlockStateProperties.EAST)
                    ? state.getValue(BlockStateProperties.EAST)
                    : state.hasProperty(BlockStateProperties.EAST_WALL)
                            && state.getValue(BlockStateProperties.EAST_WALL) != WallSide.NONE;
            case SOUTH -> state.hasProperty(BlockStateProperties.SOUTH)
                    ? state.getValue(BlockStateProperties.SOUTH)
                    : state.hasProperty(BlockStateProperties.SOUTH_WALL)
                            && state.getValue(BlockStateProperties.SOUTH_WALL) != WallSide.NONE;
            case WEST -> state.hasProperty(BlockStateProperties.WEST)
                    ? state.getValue(BlockStateProperties.WEST)
                    : state.hasProperty(BlockStateProperties.WEST_WALL)
                            && state.getValue(BlockStateProperties.WEST_WALL) != WallSide.NONE;
            default -> false;
        };
    }

    private static BlockState withHorizontalConnection(
            BlockState state,
            Direction direction,
            boolean connected
    ) {
        return switch (direction) {
            case NORTH -> state.hasProperty(BlockStateProperties.NORTH)
                    ? state.setValue(BlockStateProperties.NORTH, connected)
                    : state.setValue(BlockStateProperties.NORTH_WALL,
                            connected ? WallSide.LOW : WallSide.NONE)
                            .setValue(BlockStateProperties.UP, true);
            case EAST -> state.hasProperty(BlockStateProperties.EAST)
                    ? state.setValue(BlockStateProperties.EAST, connected)
                    : state.setValue(BlockStateProperties.EAST_WALL,
                            connected ? WallSide.LOW : WallSide.NONE)
                            .setValue(BlockStateProperties.UP, true);
            case SOUTH -> state.hasProperty(BlockStateProperties.SOUTH)
                    ? state.setValue(BlockStateProperties.SOUTH, connected)
                    : state.setValue(BlockStateProperties.SOUTH_WALL,
                            connected ? WallSide.LOW : WallSide.NONE)
                            .setValue(BlockStateProperties.UP, true);
            case WEST -> state.hasProperty(BlockStateProperties.WEST)
                    ? state.setValue(BlockStateProperties.WEST, connected)
                    : state.setValue(BlockStateProperties.WEST_WALL,
                            connected ? WallSide.LOW : WallSide.NONE)
                            .setValue(BlockStateProperties.UP, true);
            default -> state;
        };
    }

    private static void recordConnection(
            StringBuilder failures,
            boolean actual,
            boolean expected,
            String message
    ) {
        if (actual != expected) {
            recordFailure(failures, message + "; expected=" + expected + " actual=" + actual);
        }
    }

    private static void recordExact(
            StringBuilder failures,
            double actual,
            double expected,
            String message
    ) {
        if (Double.doubleToRawLongBits(actual) != Double.doubleToRawLongBits(expected)) {
            recordFailure(failures, message + "; expected=" + expected + " actual=" + actual);
        }
    }

    private static void recordShapeFloor(
            GameTestHelper ctx,
            StringBuilder failures,
            VoxelShape shape,
            double expected,
            String message
    ) {
        ctx.assertTrue(!shape.isEmpty(), message + " must be non-empty");
        AABB bounds = shape.bounds();
        recordExact(failures, bounds.minY, expected, message + " floor");
    }

    private static void recordShapeMatch(
            StringBuilder failures,
            VoxelShape actual,
            VoxelShape expected,
            String message
    ) {
        if (Shapes.joinIsNotEmpty(actual, expected, BooleanOp.NOT_SAME)) {
            recordFailure(failures, message + " must match the explicitly disconnected state");
        }
    }

    private static void recordFailure(StringBuilder failures, String message) {
        if (!failures.isEmpty()) {
            failures.append(" | ");
        }
        failures.append(message);
    }

    private static void clearConnectorFixture(
            ServerLevel world,
            BlockPos targetSupport,
            BlockPos target,
            BlockPos neighborSupport,
            BlockPos neighbor,
            BlockPos sentinelSupport,
            BlockPos sentinel
    ) {
        SlabPlacementHeightAttachment.remove(
                world.getChunk(target.getX() >> 4, target.getZ() >> 4), target);
        SlabPlacementHeightAttachment.remove(
                world.getChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4), neighbor);
        SlabPlacementHeightAttachment.remove(
                world.getChunk(sentinel.getX() >> 4, sentinel.getZ() >> 4), sentinel);
        world.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(neighbor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(sentinel, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(targetSupport, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(neighborSupport, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        world.setBlock(sentinelSupport, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void assertImmediateHeight(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos pos,
            double expected
    ) {
        double actual = SlabSupport.getYOffset(world, pos, world.getBlockState(pos));
        ctx.assertTrue(Double.doubleToRawLongBits(actual) == Double.doubleToRawLongBits(expected),
                "placement-time height mismatch: expected " + expected + ", got " + actual);
    }

    private static void assertStored(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos pos,
            int expected,
            String message
    ) {
        OptionalInt actual = stored(world, pos);
        ctx.assertTrue(actual.isPresent() && actual.getAsInt() == expected,
                message + "; expected=" + expected + " actual=" + actual);
    }

    private static void assertAbsent(
            GameTestHelper ctx,
            ServerLevel world,
            BlockPos pos,
            String message
    ) {
        ctx.assertTrue(stored(world, pos).isEmpty(), message + "; actual=" + stored(world, pos));
    }

    private static OptionalInt stored(ServerLevel world, BlockPos pos) {
        return SlabPlacementHeightAttachment.storedHalfSteps(
                world.getChunk(pos.getX() >> 4, pos.getZ() >> 4), pos);
    }

    private static BlockPos findAdjacent(ServerLevel world, BlockPos first, BlockState expected) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = first.relative(direction);
            if (world.getBlockState(candidate).is(expected.getBlock())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Raw store injection for premises that deliberately author values the public write API
     * declines (out-of-envelope haunted facts). Reads never repair these; rows prove that.
     */
    private static void injectRawHalfSteps(net.minecraft.server.level.ServerLevel world,
                                           BlockPos pos, int halfSteps) {
        net.minecraft.world.level.chunk.LevelChunk chunk =
                world.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap existing =
                chunk.getExistingDataOrNull(SlabPlacementHeightAttachment.PLACEMENT_DY_TYPE.get());
        it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap facts = existing == null
                ? new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap()
                : new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap(existing);
        facts.put(pos.asLong(), (byte) halfSteps);
        chunk.setData(SlabPlacementHeightAttachment.PLACEMENT_DY_TYPE.get(), facts);
    }
}
