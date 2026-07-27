package com.slabbed.placement;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedRecorderBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
            boolean recorderOn = SlabbedRecorderBridge.isEnabled();
            boolean anchoredBefore = false;
            boolean compoundBefore = false;
            boolean carrierBefore = false;
            if (recorderOn) {
                anchoredBefore = SlabAnchorAttachment.isAnchored(level, event.getPos());
                compoundBefore = SlabAnchorAttachment.isCompoundFullBlockAnchor(level, event.getPos());
                carrierBefore = SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                        level, event.getPos(), event.getPlacedBlock());
            }
            SlabAnchorAttachment.addAnchor(level, event.getPos(), event.getPlacedBlock());
            SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(level, event.getPos(), event.getPlacedBlock());
            if (recorderOn) {
                // Forensic placement row, schema ported from the 26.2 donor's
                // Beta4PlacementAuthorRecorder: action origin labelled trustworthily (a fake
                // player must never inflate a player-placement green count), before/after marker
                // truth, support facts, and explicit "absent" for the aim facts (clicked face /
                // hit vector) that only exist once the Phase 6 aim capture lands. One greppable
                // line per placement; the side-split live gate (Phase 5) reads these rows.
                double dy = SlabSupport.getYOffset(level, event.getPos(), event.getPlacedBlock());
                BlockPos below = event.getPos().below();
                BlockState belowState = level.getBlockState(below);
                SlabbedRecorderBridge.log("place_row",
                        "side=" + (level.isClientSide() ? "CLIENT" : "SERVER")
                        + " origin=" + actionOrigin(event.getEntity())
                        + " heldItem=" + heldItemOf(event.getEntity())
                        + " placePos=" + event.getPos().toShortString()
                        + " finalState=" + event.getPlacedBlock()
                        + " finalDy=" + dy
                        + " anchoredBefore=" + anchoredBefore
                        + " anchoredAfter=" + SlabAnchorAttachment.isAnchored(level, event.getPos())
                        + " compoundBefore=" + compoundBefore
                        + " compoundAfter=" + SlabAnchorAttachment.isCompoundFullBlockAnchor(level, event.getPos())
                        + " carrierBefore=" + carrierBefore
                        + " carrierAfter=" + SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                                level, event.getPos(), event.getPlacedBlock())
                        + " supportBelow=" + belowState
                        + " supportBelowDy=" + SlabSupport.getYOffset(level, below, belowState)
                        + " clickedFace=absent hitVec=absent storedDy=absent"
                        + " reason=entity_place_event");
                SlabbedRecorderBridge.checkPlacement(event.getPos(), event.getPlacedBlock());
            }
        }
    }

    /**
     * Trustworthy action-origin label, per the 26.2 recorder discipline: player-authored actions,
     * automation proxies, and everything else are labelled separately, so a proxy row can never
     * inflate a player-placement green count.
     */
    private static String actionOrigin(net.minecraft.world.entity.Entity entity) {
        if (entity == null) {
            return "unknown";
        }
        if (entity instanceof net.minecraftforge.common.util.FakePlayer) {
            return "fake_player";
        }
        if (entity instanceof net.minecraft.world.entity.player.Player) {
            return "player";
        }
        return entity.getType().toShortString();
    }

    private static String heldItemOf(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            return player.getMainHandItem().isEmpty()
                    ? "empty" : player.getMainHandItem().getItem().toString();
        }
        return "absent";
    }

    // NOTE: the anchor/freeze CLEAR on removal is handled by BlockOnStateReplacedAnchorMixin
    // (BlockBehaviour.onRemove), NOT a BlockEvent.BreakEvent listener. onRemove fires AFTER the
    // block is actually removed, is immune to break cancellation (protection/claim/adventure
    // mods), and covers every removal path (player break, piston, /setblock, worldgen) — so a
    // cancelled break can never strand a cleared anchor on a surviving block.
}
