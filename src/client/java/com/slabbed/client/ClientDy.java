package com.slabbed.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/**
 * The client's dy seam — and, deliberately, nothing more than a name for
 * {@link SlabSupport#getVisualYOffset}. <b>The client has no dy opinions of its own.</b>
 *
 * <p><b>Why this class is now a pure delegate.</b> It used to hold a carpet special case: "simple
 * geometric check without anchor logic" — read the block directly below, return {@code -0.5} iff
 * that block's top face sits at half height, else {@code 0.0}. That was a SECOND dy authority
 * living beside the shared one, and it is what kept carpets rendering flush after
 * {@code 112d1449} had already fixed the common-side value (live 2026-08-06, recorder
 * {@code 0ba17cf0}: {@code (270,-58,-56) white_carpet dy=-1.000 ANCHORED} and
 * {@code (270,-58,-54) dy=-0.500 ANCHORED}, both still drawn flush). With no anchor logic it could
 * not see a carpet lowered onto a full block, nor any magnitude past one half-block, so both
 * recorded cells resolved to {@code 0.0} in the chunk model and the outline.
 *
 * <p><b>Why it is not simply deleted.</b> Keeping one named client seam gives the suite something
 * to pin: {@code ClientCarpetDyAuthorityTest} asserts {@code dyFor == getVisualYOffset} for carpet,
 * pale moss carpet and a non-carpet control, so any future attempt to re-introduce a client-side
 * per-class opinion fails headlessly instead of surfacing as "it still looks flush" three sessions
 * later. Maintainer's binding law of 2026-08-06: <i>"everything should be able to lower; no
 * exceptions."</i>
 */
public final class ClientDy {
    private ClientDy() {
    }

    public static double dyFor(BlockView world, BlockPos pos, BlockState state) {
        // Null-tolerant so render-path callers never need their own guard; getVisualYOffset
        // applies the identical check.
        return SlabSupport.getVisualYOffset(world, pos, state);
    }
}
