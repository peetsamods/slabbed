package com.slabbed.placement;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedRecorderBridge;
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
            // WHAT THIS LISTENER ACTUALLY DOES TODAY: two BOOLEAN marker writes, nothing else.
            //   addAnchor records the ANCHOR marker (plus the compound sidecar when the piece
            //     qualifies); updatePersistentLoweredSlabCarrier maintains the carrier marker.
            // No HEIGHT is stored anywhere on this path — the markers say WHICH lane, never HOW
            // FAR, and every reader re-derives the magnitude from live neighbour geometry
            // (Phase I §A audit: docs/porting/mc-1.20.1-forge-phase-i-a-stored-value-audit.md).
            //
            // An earlier revision of this comment claimed the placed piece's height was frozen
            // here and the never-pop law enforced. That was FALSE: freezeLoweredOnPlace has
            // exactly one caller — predictClientPlacement, which is client-only — so the
            // FROZEN_FLAT bucket is never written on a server, and nothing on this path stops
            // a later neighbour change from re-deriving a different height. The STAYS law lands
            // via the stored placement-dy plan (Phase 4 of
            // docs/porting/mc-1.20.1-forge-stays-law-implementation-scope.md); this comment must
            // not preclaim it.
            //
            // Still true and load-bearing: EntityPlaceEvent fires only for entity placement, so
            // natural/terrain/setBlock pieces stay geometric by design.
            SlabAnchorAttachment.addAnchor(level, event.getPos(), event.getPlacedBlock());
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(level, event.getPos(), event.getPlacedBlock());
            if (SlabbedRecorderBridge.isEnabled()) {
                double dy = SlabSupport.getYOffset(level, event.getPos(), event.getPlacedBlock());
                SlabbedRecorderBridge.log("place", "pos=" + event.getPos().toShortString()
                        + " state=" + event.getPlacedBlock()
                        + " dy=" + dy
                        + " anchored=" + SlabAnchorAttachment.isAnchored(level, event.getPos()));
                SlabbedRecorderBridge.checkPlacement(event.getPos(), event.getPlacedBlock());
            }
        }
    }

    // NOTE: the anchor/freeze CLEAR on removal is handled by BlockOnStateReplacedAnchorMixin
    // (BlockBehaviour.onRemove), NOT a BlockEvent.BreakEvent listener. onRemove fires AFTER the
    // block is actually removed, is immune to break cancellation (protection/claim/adventure
    // mods), and covers every removal path (player break, piston, /setblock, worldgen) — so a
    // cancelled break can never strand a cleared anchor on a surviving block.
}
