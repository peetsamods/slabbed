package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Live-reported bug (2026-07-04, "Decorated pot particles when activating object are in vanilla
 * position 0.5+ when they should be lowered like the pot") — confirmed the trigger by asking:
 * "Particles generate when right clicking pot or stashing item." Real bytecode (confirmed via
 * {@code javap}) shows {@code DecoratedPotBlock.onUseWithItem} (the "stash an item" interaction)
 * spawns a {@code DUST_PLUME} particle via {@code ServerWorld.spawnParticles(effect, pos.getX()
 * + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, ...)} — grid height, no dy awareness. (The plain
 * right-click-to-eject path, {@code onUse}, only plays a sound and triggers the wobble animation
 * with no particle of its own — this redirect only needs to cover {@code onUseWithItem}.)
 *
 * <p>Different disease shape from the candle/redstone-torch/brewing-stand particle fixes: those
 * are client-only {@code randomDisplayTick} ambient particles redirected via {@code
 * World.addParticleClient}. This one is a SERVER-side, interaction-triggered burst via {@code
 * ServerWorld.spawnParticles} (a generic method over {@code ParticleEffect}), replicated to
 * clients through the normal particle packet — same fix shape (redirect + add dy), different
 * call site and method.
 */
@Mixin(DecoratedPotBlock.class)
public abstract class DecoratedPotParticleMixin {

    @Redirect(
            method = "onUseWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;spawnParticles(Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I"
            )
    )
    private <T extends ParticleEffect> int slabbed$offsetDecoratedPotInsertParticle(
            ServerWorld redirectWorld, T effect, double x, double y, double z,
            int count, double dx, double dy, double dz, double speed,
            ItemStack stack, BlockState state, World world, BlockPos pos,
            PlayerEntity player, Hand hand, BlockHitResult hitResult) {
        double visualDy = SlabSupport.getVisualYOffset(world, pos, state);
        return redirectWorld.spawnParticles(effect, x, y + visualDy, z, count, dx, dy, dz, speed);
    }
}
