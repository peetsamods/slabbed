package com.slabbed.test;

import com.slabbed.util.SlabSupport;
import java.lang.reflect.Constructor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Deterministic regression proof for GitHub issue #34's Ponder render-view slowdown. */
@GameTestHolder("fabric-gametest-api-v1")
@PrefixGameTestTemplate(false)
public final class Issue34PonderRenderViewProof {
    private static final String PONDER_LEVEL_CLASS_NAME =
            "net.createmod.ponder.api.level.PonderLevel";
    private static final String DERIVED_PONDER_LEVEL_CLASS_NAME =
            "net.createmod.ponder.api.level.Issue34DerivedPonderLevel";

    @GameTest(templateNamespace = "fabric-gametest-api-v1", template = "empty")
    public void ponderViewSkipsSlabbedOffsetPolicy(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();
        BlockPos supportPos = ctx.absolutePos(new BlockPos(2, 2, 2));
        BlockPos objectPos = supportPos.above();

        world.setBlock(
                supportPos,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                Block.UPDATE_ALL);
        world.setBlock(objectPos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        BlockState objectState = world.getBlockState(objectPos);
        double ordinaryDy = SlabSupport.getYOffset(world, objectPos, objectState);
        ctx.assertTrue(Double.compare(ordinaryDy, -0.5d) == 0,
                "ordinary world baseline must remain lowered; dy=" + ordinaryDy);

        FixtureClassLoader fixtureLoader = new FixtureClassLoader(getClass().getClassLoader());
        Class<? extends PonderLevelFixtureBase> ponderType = fixtureLoader.definePonderType(
                PONDER_LEVEL_CLASS_NAME,
                Type.getInternalName(PonderLevelFixtureBase.class));
        Class<? extends PonderLevelFixtureBase> derivedPonderType = fixtureLoader.definePonderType(
                DERIVED_PONDER_LEVEL_CLASS_NAME,
                PONDER_LEVEL_CLASS_NAME.replace('.', '/'));

        PonderLevelFixtureBase ponder = newFixture(ponderType, world);
        double ponderDy = SlabSupport.getYOffset(ponder, objectPos, objectState);
        ctx.assertTrue(Double.compare(ponderDy, 0.0d) == 0,
                "Ponder render view must keep authored geometry; dy=" + ponderDy);
        ctx.assertTrue(ponder.blockStateReads() == 0,
                "Ponder render entered Slabbed world-query policy; blockStateReads="
                        + ponder.blockStateReads());

        PonderLevelFixtureBase derivedPonder = newFixture(derivedPonderType, world);
        double derivedPonderDy = SlabSupport.getYOffset(derivedPonder, objectPos, objectState);
        ctx.assertTrue(Double.compare(derivedPonderDy, 0.0d) == 0,
                "Ponder subclasses must keep authored geometry; dy=" + derivedPonderDy);
        ctx.assertTrue(derivedPonder.blockStateReads() == 0,
                "Ponder subclass entered Slabbed world-query policy; blockStateReads="
                        + derivedPonder.blockStateReads());

        BlockPos chainPos = ctx.absolutePos(new BlockPos(5, 2, 2));
        BlockPos ceilingPos = chainPos.above();
        BlockState chainState = Blocks.CHAIN.defaultBlockState()
                .setValue(ChainBlock.AXIS, Direction.Axis.Y);
        world.setBlock(
                ceilingPos,
                Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Block.UPDATE_ALL);
        world.setBlock(chainPos, chainState, Block.UPDATE_ALL);

        ctx.assertTrue(SlabSupport.isVerticalChainDirectlyUnderCeilingSupport(
                        world, chainPos, chainState),
                "ordinary chain-under-ceiling baseline must remain active");
        VoxelShape ordinaryChainShape = chainState.getShape(
                world, chainPos, CollisionContext.empty());
        ctx.assertTrue(ordinaryChainShape.bounds().maxY > 1.0d,
                "ordinary chain selection must retain its ceiling bridge; maxY="
                        + ordinaryChainShape.bounds().maxY);

        int readsBeforeChain = ponder.blockStateReads();
        ctx.assertTrue(!SlabSupport.isVerticalChainDirectlyUnderCeilingSupport(
                        ponder, chainPos, chainState),
                "Ponder must not substitute Slabbed chain-ceiling geometry");
        VoxelShape ponderChainShape = chainState.getShape(
                ponder, chainPos, CollisionContext.empty());
        ctx.assertTrue(ponderChainShape.bounds().maxY <= 1.0d,
                "Ponder chain selection must remain authored/vanilla; maxY="
                        + ponderChainShape.bounds().maxY);
        ctx.assertTrue(ponder.blockStateReads() == readsBeforeChain,
                "Ponder chain path queried virtual neighbours; before=" + readsBeforeChain
                        + " after=" + ponder.blockStateReads());

        System.out.println("[ISSUE34_PONDER_RENDER_VIEW_GREEN]"
                + " ponderDy=" + ponderDy
                + " ponderBlockStateReads=" + ponder.blockStateReads()
                + " derivedPonderBlockStateReads=" + derivedPonder.blockStateReads()
                + " ordinaryDy=" + ordinaryDy
                + " ordinaryChainMaxY=" + ordinaryChainShape.bounds().maxY
                + " ponderChainMaxY=" + ponderChainShape.bounds().maxY
                + " ordinaryPolicyActive=true");
        ctx.succeed();
    }

    private static PonderLevelFixtureBase newFixture(
            Class<? extends PonderLevelFixtureBase> type,
            BlockAndTintGetter delegate
    ) {
        try {
            Constructor<? extends PonderLevelFixtureBase> constructor =
                    type.getConstructor(BlockAndTintGetter.class);
            return constructor.newInstance(delegate);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not create issue #34 Ponder fixture", exception);
        }
    }

    /** Safe source-level base; the exact Ponder FQN is generated only while this test is running. */
    public static class PonderLevelFixtureBase implements BlockAndTintGetter {
        private final BlockAndTintGetter delegate;
        private int blockStateReads;

        public PonderLevelFixtureBase(BlockAndTintGetter delegate) {
            this.delegate = delegate;
        }

        public int blockStateReads() {
            return blockStateReads;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return delegate.getBlockEntity(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            blockStateReads++;
            return delegate.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return delegate.getFluidState(pos);
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return delegate.getMinBuildHeight();
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return delegate.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return delegate.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return delegate.getBlockTint(pos, resolver);
        }
    }

    private static final class FixtureClassLoader extends ClassLoader {
        private FixtureClassLoader(ClassLoader parent) {
            super(parent);
        }

        @SuppressWarnings("unchecked")
        private Class<? extends PonderLevelFixtureBase> definePonderType(
                String className,
                String superclassInternalName
        ) {
            String internalName = className.replace('.', '/');
            ClassWriter writer = new ClassWriter(0);
            writer.visit(
                    Opcodes.V21,
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                    internalName,
                    null,
                    superclassInternalName,
                    null);
            MethodVisitor constructor = writer.visitMethod(
                    Opcodes.ACC_PUBLIC,
                    "<init>",
                    "(Lnet/minecraft/world/level/BlockAndTintGetter;)V",
                    null,
                    null);
            constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            constructor.visitVarInsn(Opcodes.ALOAD, 1);
            constructor.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    superclassInternalName,
                    "<init>",
                    "(Lnet/minecraft/world/level/BlockAndTintGetter;)V",
                    false);
            constructor.visitInsn(Opcodes.RETURN);
            constructor.visitMaxs(2, 2);
            constructor.visitEnd();
            writer.visitEnd();

            byte[] bytecode = writer.toByteArray();
            return (Class<? extends PonderLevelFixtureBase>) defineClass(
                    className, bytecode, 0, bytecode.length);
        }
    }
}
