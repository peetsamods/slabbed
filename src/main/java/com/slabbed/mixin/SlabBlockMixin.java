package com.slabbed.mixin;

import com.slabbed.Slabbed;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SlabBlock.class)
public abstract class SlabBlockMixin extends Block
{
    private SlabBlockMixin(Settings settings)
    {
        super(settings);
    }

    @Inject(method = "<init>", at = @At(value = "TAIL"))
    public void slabbed$SlabBlock(AbstractBlock.Settings settings, CallbackInfo info)
    {
        setDefaultState(getDefaultState().with(Slabbed.IS_SLABBED, false));
    }

    @Inject(method = "appendProperties", at = @At(value = "TAIL"))
    public void slabbed$appendProperties(StateManager.Builder<Block, BlockState> builder, CallbackInfo info)
    {
        builder.add(Slabbed.IS_SLABBED);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit)
    {
        Thread.dumpStack();
        if (player.isSneaking())
        {
            boolean newState = !state.get(Slabbed.IS_SLABBED);
            world.setBlockState(pos, state.with(Slabbed.IS_SLABBED, newState), 3);

            Random random = world.getRandom();

            for (int i = 0; i < 16; ++i)
            {
                double h = random.nextGaussian() * 0.02;
                double j = random.nextGaussian() * 0.02;
                double k = random.nextGaussian() * 0.02;
                int color = newState ? 0x00ff00 : 0xff0000;
                world.addParticleClient(
                    new DustParticleEffect(color, 0.92f),
                    pos.getX() + random.nextDouble() * 1.2, pos.getY() + random.nextDouble() * 1.2, pos.getZ() + random.nextDouble() * 1.2,
                    h, j, k);
            }

            return ActionResult.SUCCESS;
        }

        return super.onUse(state, world, pos, player, hit);
    }
}
