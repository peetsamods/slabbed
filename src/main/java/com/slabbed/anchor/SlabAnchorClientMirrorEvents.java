package com.slabbed.anchor;

import com.slabbed.Slabbed;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only lifecycle hooks for the anchor mirror and non-Level lookup bridge.
 * The common mirror/network classes remain safe to load on dedicated servers;
 * this subscriber is registered only on the physical client.
 */
@Mod.EventBusSubscriber(modid = Slabbed.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SlabAnchorClientMirrorEvents {
    private SlabAnchorClientMirrorEvents() {
    }

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        SlabAnchorClientMirror.clearAll();
        installClientMirrorLookups();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearClientMirrorLookups();
        SlabAnchorClientMirror.clearAll();
    }

    private static void installClientMirrorLookups() {
        SlabAnchorAttachment.clientAnchorLookup = lookup(SlabAnchorMarker.ANCHOR);
        SlabAnchorAttachment.clientFrozenFlatLookup = lookup(SlabAnchorMarker.FROZEN_FLAT);
        SlabAnchorAttachment.clientLoweredSlabCarrierLookup = lookup(SlabAnchorMarker.LOWERED_SLAB_CARRIER);
        SlabAnchorAttachment.clientCompoundFullBlockAnchorLookup =
                lookup(SlabAnchorMarker.COMPOUND_FULL_BLOCK_ANCHOR);
        SlabAnchorAttachment.clientCompoundVisibleSideLowerSlabLookup =
                lookup(SlabAnchorMarker.COMPOUND_VISIBLE_SIDE_LOWER_SLAB);
        SlabAnchorAttachment.clientCompoundVisibleSideUpperSlabLookup =
                lookup(SlabAnchorMarker.COMPOUND_VISIBLE_SIDE_UPPER_SLAB);
        SlabAnchorAttachment.clientCompoundVisibleSideDoubleSlabLookup =
                lookup(SlabAnchorMarker.COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB);
        SlabAnchorAttachment.clientCompoundVisibleOwnerTopSlabLookup =
                lookup(SlabAnchorMarker.COMPOUND_VISIBLE_OWNER_TOP_SLAB);
    }

    private static void clearClientMirrorLookups() {
        SlabAnchorAttachment.clientAnchorLookup = null;
        SlabAnchorAttachment.clientFrozenFlatLookup = null;
        SlabAnchorAttachment.clientLoweredSlabCarrierLookup = null;
        SlabAnchorAttachment.clientCompoundFullBlockAnchorLookup = null;
        SlabAnchorAttachment.clientCompoundVisibleSideLowerSlabLookup = null;
        SlabAnchorAttachment.clientCompoundVisibleSideUpperSlabLookup = null;
        SlabAnchorAttachment.clientCompoundVisibleSideDoubleSlabLookup = null;
        SlabAnchorAttachment.clientCompoundVisibleOwnerTopSlabLookup = null;
    }

    private static Predicate<BlockPos> lookup(SlabAnchorMarker marker) {
        return pos -> SlabAnchorClientMirror.contains(currentDimension(), marker, pos);
    }

    private static ResourceLocation currentDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? null : minecraft.level.dimension().location();
    }
}
