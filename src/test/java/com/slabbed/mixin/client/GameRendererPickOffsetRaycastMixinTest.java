package com.slabbed.mixin.client;

import com.slabbed.util.SlabbedOffsetRaycast;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

final class GameRendererPickOffsetRaycastMixinTest {
    private static final Vec3 EYE = Vec3.ZERO;

    @Test
    void keepsSlabbedOwnershipWhenExternalOwnerIsAbsent() {
        BlockHitResult external = hitAt(1.0d);
        BlockHitResult slabbed = hitAt(2.0d);

        assertSame(slabbed, select(false, external, slabbed));
    }

    @Test
    void keepsOrdinarySlabbedHitWhenExternalOwnerMisses() {
        BlockHitResult externalMiss = missAt(6.0d);
        BlockHitResult slabbed = hitAt(2.0d);

        assertSame(slabbed, select(true, externalMiss, slabbed));
    }

    @Test
    void preservesNearerExternalOwnerHit() {
        BlockHitResult external = hitAt(1.0d);
        BlockHitResult slabbed = hitAt(2.0d);

        assertSame(external, select(true, external, slabbed));
    }

    @Test
    void preservesExternalOwnerHitAtEqualDistance() {
        BlockHitResult external = hitAt(2.0d);
        BlockHitResult slabbed = hitAt(2.0d);

        assertSame(external, select(true, external, slabbed));
    }

    @Test
    void selectsNearerSlabbedHitOverFartherExternalOwner() {
        BlockHitResult external = hitAt(3.0d);
        BlockHitResult slabbed = hitAt(2.0d);

        assertSame(slabbed, select(true, external, slabbed));
    }

    @Test
    void preservesExternalOwnerWhenSlabbedMisses() {
        BlockHitResult external = hitAt(2.0d);
        BlockHitResult slabbedMiss = missAt(6.0d);

        assertSame(external, select(true, external, slabbedMiss));
    }

    private static HitResult select(
            boolean externalOwnerLoaded,
            HitResult external,
            BlockHitResult slabbed
    ) {
        return SlabbedOffsetRaycast.selectNearestOwnedHit(
                EYE, externalOwnerLoaded ? external : null, slabbed);
    }

    private static BlockHitResult hitAt(double z) {
        Vec3 location = new Vec3(0.0d, 0.0d, z);
        return new BlockHitResult(location, Direction.NORTH, BlockPos.containing(location), false);
    }

    private static BlockHitResult missAt(double z) {
        Vec3 location = new Vec3(0.0d, 0.0d, z);
        return BlockHitResult.miss(location, Direction.NORTH, BlockPos.containing(location));
    }
}
