package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Test-support for fixtures that build their scene with {@code setBlock} (terrain authoring) instead
 * of the real {@code useOn} placement path.
 *
 * <p>{@code setBlock} never publishes a {@code PLACEMENT_DY} fact, so under the shipped
 * {@code -Dslabbed.frozenDy=true} configuration {@link SlabSupport#getYOffset} returns the
 * missing-fact stable-flat {@code 0.0} for every such cell and the fixture's own premise fails before
 * the test exercises anything. {@link #authorScene} publishes the fact each terrain cell would carry
 * if it had been placed at the height it currently reads, so the same scene reads identically with
 * the store on and off.
 *
 * <p>The published value is always taken with the frozen store forced OFF. Inside
 * {@code getYOffsetInner} every neighbour read goes through the public {@link SlabSupport#getYOffset}
 * under the {@code IN_GET_Y_OFFSET} recursion guard, which returns {@code 0.0} while the store is
 * off — so no cell's computed height can depend on another cell's already-published fact. Walk order
 * and partial publication are therefore irrelevant, and the stored value equals, byte for byte, the
 * value the same fixture reads today with the store off.
 */
public final class FrozenDySceneFixture {

    private FrozenDySceneFixture() {
    }

    /**
     * Publishes a placement fact for every non-air cell in the test's structure bounds that does not
     * already carry one, using the height that cell reads with the frozen store off. Idempotent:
     * cells that already carry a fact (a real {@code useOn} placement, or an earlier call) keep it.
     *
     * @return the number of facts written
     */
    public static int authorScene(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AABB bounds = helper.getBounds();
        int minX = (int) Math.floor(bounds.minX);
        int minY = (int) Math.floor(bounds.minY);
        int minZ = (int) Math.floor(bounds.minZ);
        int maxX = (int) Math.ceil(bounds.maxX) - 1;
        int maxY = (int) Math.ceil(bounds.maxY) - 1;
        int maxZ = (int) Math.ceil(bounds.maxZ) - 1;

        Map<BlockPos, Long> pending = new LinkedHashMap<>();
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        try {
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir()) {
                            continue;
                        }
                        if (!Double.isNaN(SlabAnchorAttachment.storedPlacementDy(level, pos))) {
                            continue;
                        }
                        double dy = SlabSupport.getUnstoredYOffset(level, pos, state);
                        if (!Double.isFinite(dy)) {
                            continue;
                        }
                        pending.put(pos, Double.doubleToRawLongBits(dy));
                    }
                }
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
        return pending.isEmpty() ? 0 : SlabAnchorAttachment.writePlacementDyBatch(level, pending);
    }

    /**
     * Runs a scene-construction block with the frozen store forced off, then {@link #authorScene}s.
     *
     * <p>Required whenever construction calls an attachment writer ({@code SlabAnchorAttachment.add*},
     * {@code freezeLoweredOnPlace}, or {@code Block#setPlacedBy}): those qualifiers read
     * {@link SlabSupport#getYOffset} at cells that have no fact yet, so with the store on they
     * classify against {@code 0.0} and silently decline to author the marker/anchor the fixture is
     * building. Real {@code useOn} placements must stay OUTSIDE this window — they are the shipped
     * path under test.
     */
    public static void authored(GameTestHelper helper, Runnable sceneBuilder) {
        authored(helper, () -> {
            sceneBuilder.run();
            return null;
        });
    }

    /** {@link #authored(GameTestHelper, Runnable)} for a builder that returns the scene's subject. */
    public static <T> T authored(GameTestHelper helper, Supplier<T> sceneBuilder) {
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        T result;
        try {
            result = sceneBuilder.get();
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = previous;
        }
        authorScene(helper);
        return result;
    }
}
