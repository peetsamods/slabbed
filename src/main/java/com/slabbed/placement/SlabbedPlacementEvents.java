package com.slabbed.placement;

import com.slabbed.anchor.SlabAnchorAttachment;
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
        }
    }
}
