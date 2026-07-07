package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Port of the 1.21.11 decorated-pot particle fix (donor {@code DecoratedPotParticleMixin}, commit
 * {@code 5e5dd0ef}, live-reported "particles when activating object are in vanilla position 0.5+ when
 * they should be lowered like the pot"; trigger = stashing an item). Different shape from the
 * candle/torch/brewing-stand fixes: this is a SERVER-side interaction burst — 26.2 bytecode (javap)
 * shows {@code DecoratedPotBlock.useItemOn} spawning DUST_PLUME via
 * {@code ServerLevel.sendParticles(ParticleOptions,DDDIDDDD)I} at :247, grid height, no dy awareness —
 * replicated to clients through the normal particle packet. Same fix shape (redirect + add dy),
 * different call site and side.
 */
@Mixin(DecoratedPotBlock.class)
public abstract class DecoratedPotParticleMixin {

    @Redirect(
            method = "useItemOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"
            )
    )
    private <T extends ParticleOptions> int slabbed$offsetDecoratedPotInsertParticle(
            ServerLevel redirectLevel, T effect, double x, double y, double z,
            int count, double dx, double dySpread, double dz, double speed,
            ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        double visualDy = SlabSupport.getYOffset(level, pos, state);
        return redirectLevel.sendParticles(effect, x, y + visualDy, z, count, dx, dySpread, dz, speed);
    }
}
