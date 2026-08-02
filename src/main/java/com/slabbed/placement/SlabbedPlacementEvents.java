package com.slabbed.placement;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabbedDiagnosticsBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

import java.util.LinkedHashMap;

public final class SlabbedPlacementEvents {
    private SlabbedPlacementEvents() {
    }

    public static void register() {
        // LOWEST priority: cancelled events never reach us (receiveCanceled=false) and every
        // higher-priority listener has already run, so what we observe is the placement's final
        // fate. The handler still re-checks the live state before writing — Forge restores every
        // snapshot in reverse on cancellation, and a stored height for a block that no longer
        // exists would be an authored lie.
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                SlabbedPlacementEvents::onBlockPlaced);
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
            boolean recorderOn = SlabbedDiagnosticsBridge.isRecorderEnabled();
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

            // STAYS Phase 4, Stage A — THE WRITER. Freeze the height the lanes would have given,
            // so nothing visibly changes on the placement frame but nothing can ever move it
            // again. getUnstoredYOffset, deliberately: the writer must derive what geometry says
            // WITHOUT reading its own output (and the markers just written above are part of that
            // geometry, exactly as the donor sequences it). Every cell of a multi-cell placement
            // (doors, beds) lands together via EntityMultiPlaceEvent; flat placements store an
            // EXPLICIT +0.0 — that is what makes R3 durable, a slab shoved underneath later can
            // pull nothing down. writePlacementDy itself refuses reader-skipped states (guard
            // symmetry) and the store write is skipped when a later handler already changed the
            // cell (the stored value must describe the block that actually stands there).
            // PLAYER PLACEMENTS ONLY, explicitly (the accepted scope ruling): for non-Player
            // entities Forge sets placedBlock = getReplacedBlock(), which makes any identity
            // check vacuous — endermen and other entity placements are excluded by CHOICE here,
            // not by that accident (adversarial review, Forge event disassembly).
            boolean playerAuthored = event.getEntity() instanceof net.minecraft.world.entity.player.Player;
            if (playerAuthored && event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
                for (net.minecraftforge.common.util.BlockSnapshot snapshot : multi.getReplacedBlockSnapshots()) {
                    storePlacementHeight(level, snapshot.getPos());
                }
            } else if (playerAuthored) {
                if (level.getBlockState(event.getPos()) == event.getPlacedBlock()) {
                    storePlacementHeight(level, event.getPos());
                }
            }
            if (recorderOn && playerAuthored) {
                // Schema-6 authoritative server observation. Fake players remain GAMETEST;
                // non-player entity placement is outside the three-origin evidence contract and
                // is intentionally not recorded as player proof. Aim facts remain absent until
                // Phase 7 captures them on the real server placement path.
                double dy = SlabSupport.getYOffset(level, event.getPos(), event.getPlacedBlock());
                BlockPos below = event.getPos().below();
                BlockState belowState = level.getBlockState(below);
                double storedDy = SlabAnchorAttachment.storedPlacementDy(level, event.getPos());
                LinkedHashMap<String, String> row = new LinkedHashMap<>();
                row.put("side", level.isClientSide() ? "client" : "server");
                row.put("actionType", "place_block");
                row.put("originHint", actionOrigin(event.getEntity()));
                row.put("heldItem", heldItemOf(event.getEntity()));
                row.put("playerUuid", event.getEntity().getUUID().toString());
                row.put("dimensionId", level.dimension().location().toString());
                row.put("clickedOwnerPos", "none");
                row.put("clickedFace", "none");
                row.put("hitVec", "none");
                row.put("placementPos", event.getPos().toShortString());
                row.put("afterState", event.getPlacedBlock().toString());
                row.put("afterDy", Double.toString(dy));
                row.put("afterLaneKind", laneKind(dy));
                row.put("afterStoredDy", Double.toString(storedDy));
                row.put("afterStoredDyBits", Long.toUnsignedString(Double.doubleToRawLongBits(storedDy)));
                row.put("anchoredBefore", Boolean.toString(anchoredBefore));
                row.put("anchoredAfter", Boolean.toString(
                        SlabAnchorAttachment.isAnchored(level, event.getPos())));
                row.put("compoundBefore", Boolean.toString(compoundBefore));
                row.put("compoundAfter", Boolean.toString(
                        SlabAnchorAttachment.isCompoundFullBlockAnchor(level, event.getPos())));
                row.put("carrierBefore", Boolean.toString(carrierBefore));
                row.put("carrierAfter", Boolean.toString(
                        SlabAnchorAttachment.isPersistentLoweredSlabCarrier(
                                level, event.getPos(), event.getPlacedBlock())));
                row.put("supportBelow", belowState.toString());
                row.put("supportBelowDy", Double.toString(
                        SlabSupport.getYOffset(level, below, belowState)));
                row.put("reason", "entity_place_event");
                SlabbedDiagnosticsBridge.recordAction(row);
                // Server aim remains explicitly absent until Phase 7 captures it authoritatively;
                // a client-global target must never grade another player on an integrated server.
            }
        }
    }

    /** Stage A write for one placed cell: freeze exactly what the lanes would answer. */
    private static void storePlacementHeight(Level level, BlockPos pos) {
        BlockState placed = level.getBlockState(pos);
        if (placed.isAir()) {
            return;
        }
        // PRE-PLACE EVENTS EXIST: Frost Walker fires EntityPlaceEvent while the cell is still
        // WATER (verified by disassembly — the event posts before setBlockAndUpdate), so the
        // identity re-check passes vacuously and the live state is a liquid. A liquid can never
        // be the placed block of a real placement; storing its dy would freeze a height authored
        // for water onto the frosted ice that follows. Block-instanceof, not getFluidState():
        // WATERLOGGED placements must keep storing.
        if (placed.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
            return;
        }
        double dy = SlabSupport.getUnstoredYOffset(level, pos, placed);
        if (Double.isFinite(dy)) {
            SlabAnchorAttachment.writePlacementDy(level, pos, dy);
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
            return SlabbedDiagnosticsBridge.GAMETEST;
        }
        if (entity instanceof net.minecraft.world.entity.player.Player) {
            return SlabbedDiagnosticsBridge.PLAYER_AUTHORED;
        }
        return null;
    }

    private static String heldItemOf(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            return player.getMainHandItem().isEmpty()
                    ? "empty" : player.getMainHandItem().getItem().toString();
        }
        return "absent";
    }

    private static String laneKind(double dy) {
        if (!Double.isFinite(dy)) {
            return "INVALID";
        }
        if (dy < -1.0e-6d) {
            return "LOWERED";
        }
        if (dy > 1.0e-6d) {
            return "RAISED";
        }
        return "FLUSH";
    }

    // NOTE: the anchor/freeze CLEAR on removal is handled by BlockOnStateReplacedAnchorMixin
    // (BlockBehaviour.onRemove), NOT a BlockEvent.BreakEvent listener. onRemove fires AFTER the
    // block is actually removed, is immune to break cancellation (protection/claim/adventure
    // mods), and covers every removal path (player break, piston, /setblock, worldgen) — so a
    // cancelled break can never strand a cleared anchor on a surviving block.
}
