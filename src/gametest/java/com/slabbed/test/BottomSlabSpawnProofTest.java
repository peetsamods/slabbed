package com.slabbed.test;

import com.slabbed.compat.CompatHooks;
import com.slabbed.compat.CompatSlabSurfaceKind;
import com.slabbed.compat.terrainslabs.TerrainSlabsCompat;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * GH #39: Slabbed's broad placement-support rule must not make a vanilla BOTTOM slab a valid
 * ON_GROUND mob-spawn surface. Spawn eligibility and placement support are separate policies.
 */
public final class BottomSlabSpawnProofTest {

    private static final ThreadLocal<Boolean> TERRAIN_SLABS_CLASSIFIER_TEST_GATE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final Identifier TERRAIN_SLAB_ID =
            Identifier.fromNamespaceAndPath("terrain_slabs", "spawn_proof_test_slab");
    private static final ResourceKey<Block> TERRAIN_SLAB_KEY =
            ResourceKey.create(Registries.BLOCK, TERRAIN_SLAB_ID);
    private static final BooleanProperty GENERATED = BooleanProperty.create("generated");
    private static final Block TERRAIN_SLAB = new TerrainSlabsSpawnProofSlab(
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_SLAB).setId(TERRAIN_SLAB_KEY));

    /** GameTest-only stand-in for Terrain Slabs' named generated-double surface route. */
    private static final class TerrainSlabsSpawnProofSlab extends SlabBlock {
        private TerrainSlabsSpawnProofSlab(BlockBehaviour.Properties properties) {
            super(properties);
            registerDefaultState(defaultBlockState().setValue(GENERATED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(GENERATED);
        }
    }

    /** Registers the stand-in before registry freeze for the GameTest-only classifier fixture. */
    public static final class TerrainSlabsSpawnProofTestEntrypoint implements ModInitializer {
        @Override
        public void onInitialize() {
            if (!BuiltInRegistries.BLOCK.containsKey(TERRAIN_SLAB_ID)) {
                Registry.register(BuiltInRegistries.BLOCK, TERRAIN_SLAB_ID, TERRAIN_SLAB);
            }
        }
    }

    private static EntityType<?> vanillaMob(String path) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(path));
        if (type == null) {
            throw new IllegalStateException("setup: missing vanilla entity type " + path);
        }
        return type;
    }

    private static BlockState vanillaSlab(SlabType type, boolean waterlogged) {
        return Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, type)
                .setValue(SlabBlock.WATERLOGGED, waterlogged);
    }

    private static BlockState terrainSlab(SlabType type, boolean generated) {
        return TERRAIN_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, type)
                .setValue(GENERATED, generated);
    }

    /** GameTest-only access point consumed by the test mixin around one synchronous classifier call. */
    public static boolean terrainSlabsClassifierTestGate() {
        return TERRAIN_SLABS_CLASSIFIER_TEST_GATE.get();
    }

    private static void setSupport(GameTestHelper helper, BlockPos relativePos, BlockState state) {
        helper.getLevel().setBlock(helper.absolutePos(relativePos), state, 2);
    }

    private static void assertZombieSpawnSurface(
            GameTestHelper helper,
            BlockPos relativePos,
            boolean expected,
            String label
    ) {
        ServerLevel level = helper.getLevel();
        BlockPos supportPos = helper.absolutePos(relativePos);
        BlockState support = level.getBlockState(supportPos);
        EntityType<?> zombie = vanillaMob("zombie");
        boolean validSpawn = support.isValidSpawn(level, supportPos, zombie);
        boolean onGround = SpawnPlacements.isSpawnPositionOk(zombie, level, supportPos.above());
        if (validSpawn != expected || onGround != expected) {
            throw helper.assertionException(helper.relativePos(supportPos),
                    label + " expected isValidSpawn/ON_GROUND=" + expected + "/" + expected
                            + " but got " + validSpawn + "/" + onGround);
        }
    }

    private static void assertSurfaceKind(
            GameTestHelper helper,
            BlockPos relativePos,
            CompatSlabSurfaceKind expected,
            String label
    ) {
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(relativePos));
        CompatSlabSurfaceKind actual = CompatHooks.customSlabSurfaceKind(state);
        if (actual != expected) {
            throw helper.assertionException(relativePos,
                    label + " expected CompatHooks.customSlabSurfaceKind=" + expected + " but got " + actual);
        }
    }

    private static void assertCowSpawnSurface(
            GameTestHelper helper,
            BlockPos relativePos,
            boolean expected,
            String label
    ) {
        ServerLevel level = helper.getLevel();
        BlockPos supportPos = helper.absolutePos(relativePos);
        BlockState support = level.getBlockState(supportPos);
        EntityType<?> cow = vanillaMob("cow");
        boolean validSpawn = support.isValidSpawn(level, supportPos, cow);
        boolean onGround = SpawnPlacements.isSpawnPositionOk(cow, level, supportPos.above());
        if (validSpawn != expected || onGround != expected) {
            throw helper.assertionException(helper.relativePos(supportPos),
                    label + " expected isValidSpawn/ON_GROUND=" + expected + "/" + expected
                            + " but got " + validSpawn + "/" + onGround);
        }
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaBottomSlabsStayMobProofWhileKeepingPlacementSupport(GameTestHelper helper) {
        BlockPos dry = new BlockPos(3, 2, 3);
        BlockPos waterlogged = dry.east();
        setSupport(helper, dry, vanillaSlab(SlabType.BOTTOM, false));
        setSupport(helper, waterlogged, vanillaSlab(SlabType.BOTTOM, true));

        ServerLevel level = helper.getLevel();
        BlockPos dryPos = helper.absolutePos(dry);
        BlockState dryState = level.getBlockState(dryPos);
        if (!dryState.isFaceSturdy(level, dryPos, Direction.UP, SupportType.FULL)) {
            throw helper.assertionException(dry,
                    "setup: bottom slab must retain Slabbed's full-square UP placement support");
        }
        if (!dryState.isFaceSturdy(level, dryPos, Direction.UP, SupportType.RIGID)) {
            throw helper.assertionException(dry,
                    "setup: bottom slab must retain Slabbed's RIGID UP placement support");
        }

        assertZombieSpawnSurface(helper, dry, false, "dry vanilla bottom slab / zombie");
        assertZombieSpawnSurface(helper, waterlogged, false, "waterlogged vanilla bottom slab / zombie");
        assertCowBottomSlabControl(helper, dry);
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void vanillaTopAndDoubleSlabControlsRemainSpawnEligible(GameTestHelper helper) {
        BlockPos top = new BlockPos(3, 2, 6);
        BlockPos doubled = top.east();
        setSupport(helper, top, vanillaSlab(SlabType.TOP, false));
        setSupport(helper, doubled, vanillaSlab(SlabType.DOUBLE, false));

        assertZombieSpawnSurface(helper, top, true, "vanilla top slab / zombie");
        assertZombieSpawnSurface(helper, doubled, true, "vanilla double slab / zombie");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void fullStoneAndAirControlsRemainUnchanged(GameTestHelper helper) {
        BlockPos stone = new BlockPos(3, 2, 9);
        BlockPos air = stone.east();
        setSupport(helper, stone, Blocks.STONE.defaultBlockState());
        setSupport(helper, air, Blocks.AIR.defaultBlockState());

        assertZombieSpawnSurface(helper, stone, true, "full stone / zombie");
        assertZombieSpawnSurface(helper, air, false, "air / zombie");
        assertCowSpawnSurface(helper, stone, true, "full stone / cow passive control");
        helper.succeed();
    }

    private static void assertCowBottomSlabControl(GameTestHelper helper, BlockPos relativePos) {
        assertCowSpawnSurface(helper, relativePos, false, "dry vanilla bottom slab / cow");
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void terrainSlabsBottomLikeSurfacesKeepNaturalSpawnProofing(GameTestHelper helper) {
        BlockPos bottom = new BlockPos(3, 2, 12);
        BlockPos top = bottom.east();
        BlockPos ordinaryDouble = top.east();
        BlockPos generatedDouble = ordinaryDouble.east();
        setSupport(helper, bottom, terrainSlab(SlabType.BOTTOM, false));
        setSupport(helper, top, terrainSlab(SlabType.TOP, false));
        setSupport(helper, ordinaryDouble, terrainSlab(SlabType.DOUBLE, false));
        setSupport(helper, generatedDouble, terrainSlab(SlabType.DOUBLE, true));

        boolean priorGate = TERRAIN_SLABS_CLASSIFIER_TEST_GATE.get();
        TERRAIN_SLABS_CLASSIFIER_TEST_GATE.set(Boolean.TRUE);
        try {
            assertSurfaceKind(helper, bottom, CompatSlabSurfaceKind.BOTTOM_LIKE, "terrain_slabs bottom");
            assertZombieSpawnSurface(helper, bottom, false, "terrain_slabs bottom / zombie");
            assertSurfaceKind(helper, top, CompatSlabSurfaceKind.NONE, "terrain_slabs top");
            assertZombieSpawnSurface(helper, top, true, "terrain_slabs top / zombie");
            assertSurfaceKind(helper, ordinaryDouble, CompatSlabSurfaceKind.NONE,
                    "terrain_slabs ordinary double");
            assertZombieSpawnSurface(helper, ordinaryDouble, true, "terrain_slabs ordinary double / zombie");
            assertSurfaceKind(helper, generatedDouble, CompatSlabSurfaceKind.BOTTOM_LIKE,
                    "terrain_slabs generated double");
            assertZombieSpawnSurface(helper, generatedDouble, false, "terrain_slabs generated double / zombie");
            assertCowSpawnSurface(helper, top, true, "terrain_slabs top / cow passive control");
        } finally {
            TERRAIN_SLABS_CLASSIFIER_TEST_GATE.set(priorGate);
        }
        helper.succeed();
    }
}
