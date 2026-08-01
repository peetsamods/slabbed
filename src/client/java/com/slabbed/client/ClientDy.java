package com.slabbed.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

/**
 * Client-side dy accessor. A PURE DELEGATE to {@link SlabSupport#getYOffset} — deliberately.
 *
 * <p>This class once carried a private carpet special case, which made it a second dy authority:
 * model, outline, raycast and the /slabdev debug overlay showed carpet sunken while the server-side
 * authority said flush. That split is the recorded root of the carpet WYSIWYG disagreement.
 * The rule now lives in {@code SlabSupport.getYOffset} itself (Maintainer's 2026-07-27 ruling:
 * carpet is -0.5, matching what players see), and this class must never re-grow a divergent
 * branch — {@code ForgeCarpetDyAuthorityGameTest} enforces that structurally.
 */
public final class ClientDy {
    private ClientDy() {
    }

    public static double dyFor(BlockGetter world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return 0.0;
        }
        return SlabSupport.getYOffset(world, pos, state);
    }
}
