package com.slabbed.placement;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedRecorder;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

public final class SlabbedPlacementEvents {
    private SlabbedPlacementEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(SlabbedPlacementEvents::onBlockPlaced);
    }

    private static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level) {
            SlabAnchorAttachment.addAnchor(level, event.getPos(), event.getPlacedBlock());
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(level, event.getPos(), event.getPlacedBlock());
            // Freeze the placed piece's height (lowered lane or flat structural) so a later
            // neighbour change cannot pull it down or pop it up. This is the never-pop law:
            // a placed block stays exactly where it was authored. Mirrors the donor's
            // Block.setPlacedBy anchor write, here driven by the Forge place event (which,
            // like setPlacedBy, fires only for entity placement — natural/terrain pieces
            // stay geometric by design).
            if (SlabbedRecorder.isEnabled()) {
                double dy = SlabSupport.getYOffset(level, event.getPos(), event.getPlacedBlock());
                SlabbedRecorder.log("place", "pos=" + event.getPos().toShortString()
                        + " state=" + event.getPlacedBlock()
                        + " dy=" + dy
                        + " anchored=" + SlabAnchorAttachment.isAnchored(level, event.getPos()));
                SlabbedRecorder.checkPlacement(event.getPos(), event.getPlacedBlock());
            }
        }
    }

    // NOTE: the anchor/freeze CLEAR on removal is handled by BlockOnStateReplacedAnchorMixin
    // (BlockBehaviour.onRemove), NOT a BlockEvent.BreakEvent listener. onRemove fires AFTER the
    // block is actually removed, is immune to break cancellation (protection/claim/adventure
    // mods), and covers every removal path (player break, piston, /setblock, worldgen) — so a
    // cancelled break can never strand a cleared anchor on a surviving block.
}
