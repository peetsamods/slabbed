package com.slabbed.test;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Test-only callback block registered before the block registry freezes. */
public final class ParticleDisplayLazyFixtures implements ModInitializer {
    public static Block callbackBlock;
    public static final ThreadLocal<Runnable> CALLBACK = new ThreadLocal<>();
    @Override public void onInitialize() {
        callbackBlock = Registry.register(Registries.BLOCK, Identifier.of("slabbed_gametest", "display_callback"),
                new Block(AbstractBlock.Settings.create()) {
                    @Override public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
                        Runnable callback = CALLBACK.get();
                        if (callback != null) callback.run();
                    }
                });
    }
}
