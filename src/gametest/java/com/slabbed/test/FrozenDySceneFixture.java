package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Test-support for fixtures that build their scene with {@code setBlockState} (terrain authoring)
 * instead of the real {@code useOnBlock} placement path.
 *
 * <p>{@code setBlockState} never publishes a {@code PLACEMENT_DY} fact, so under the shipped
 * {@code -Dslabbed.frozenDy=true} configuration {@link SlabSupport#getYOffset} answers with the
 * missing-fact stable-flat {@code 0.0} for every such cell and the fixture's own premise fails before
 * the test exercises anything. {@link #authorScene} publishes the fact each terrain cell would carry
 * if it had been placed at the height it currently reads, so the same scene reads identically with
 * the store on and off.
 *
 * <p>The published value is always taken with the frozen store forced OFF. Inside
 * {@code getYOffsetInner} every neighbour read goes through the public {@link SlabSupport#getYOffset}
 * under the {@code IN_GET_Y_OFFSET} recursion guard, which answers {@code 0.0} while the store is
 * off — so no cell's computed height can depend on another cell's already-published fact. Walk order
 * and partial publication are therefore irrelevant, and the stored value equals, byte for byte, the
 * value the same fixture reads today with the store off.
 *
 * <p>PORT NOTE (1.21.1): {@code TestContext} exposes no public structure-bounds accessor on this
 * line, so the walk uses the same fixed relative interior window every fixture here already assumes
 * for arena clearing ({@code 0..6} x, {@code 0..7} y, {@code 0..6} z, mapped through
 * {@link TestContext#getAbsolutePos}). The window is rotation-symmetric in x/z, so a rotated test
 * structure walks the same cells.
 */
public final class FrozenDySceneFixture {

    private static final int MAX_RELATIVE_XZ = 6;
    private static final int MAX_RELATIVE_Y = 7;

    private FrozenDySceneFixture() {
    }

    /**
     * Publishes a placement fact for every non-air cell in the test's interior window that does not
     * already carry one, using the height that cell reads with the frozen store off. Idempotent:
     * cells that already carry a fact (a real {@code useOnBlock} placement, or an earlier call) keep
     * it.
     *
     * @return the number of facts written
     */
    public static int authorScene(TestContext helper) {
        ServerWorld world = helper.getWorld();
        Map<BlockPos, Long> pending = new LinkedHashMap<>();
        boolean previous = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        try {
            for (int x = 0; x <= MAX_RELATIVE_XZ; x++) {
                for (int y = 0; y <= MAX_RELATIVE_Y; y++) {
                    for (int z = 0; z <= MAX_RELATIVE_XZ; z++) {
                        BlockPos pos = helper.getAbsolutePos(new BlockPos(x, y, z));
                        BlockState state = world.getBlockState(pos);
                        if (state.isAir()) {
                            continue;
                        }
                        if (!Double.isNaN(SlabAnchorAttachment.storedPlacementDy(world, pos))) {
                            continue;
                        }
                        double dy = SlabSupport.getUnstoredYOffset(world, pos, state);
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
        return pending.isEmpty() ? 0 : SlabAnchorAttachment.writePlacementDyBatch(world, pending);
    }

    /**
     * Runs a scene-construction block with the frozen store forced off, then {@link #authorScene}s.
     *
     * <p>Required whenever construction calls an attachment writer ({@code SlabAnchorAttachment.add*},
     * {@code freezeLoweredOnPlace}, or {@code Block#onPlaced}): those qualifiers read
     * {@link SlabSupport#getYOffset} at cells that have no fact yet, so with the store on they
     * classify against {@code 0.0} and silently decline to author the marker/anchor the fixture is
     * building. Real {@code useOnBlock} placements must stay OUTSIDE this window — they are the
     * shipped path under test.
     */
    public static void authored(TestContext helper, Runnable sceneBuilder) {
        authored(helper, () -> {
            sceneBuilder.run();
            return null;
        });
    }

    /** {@link #authored(TestContext, Runnable)} for a builder that returns the scene's subject. */
    public static <T> T authored(TestContext helper, Supplier<T> sceneBuilder) {
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
