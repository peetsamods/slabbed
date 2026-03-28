package com.slabbed;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.tick.WorldTickScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Slabbed implements ModInitializer
{
    public static final String MOD_ID = "slabbed";
    public static final Logger LOGGER = LoggerFactory.getLogger(Slabbed.class);

    public static final BooleanProperty IS_SLABBED = BooleanProperty.of("is_slabbed");

    // Render Thread doesnt support Gizmos so we have a little workaround
    private static final List<Runnable> GIZMOS = new ArrayList<>();
    private static final boolean ENABLE_DEBUG = false;

    @Override
    public void onInitialize()
    {
        LOGGER.info("Slabbed initialized");
        if (ENABLE_DEBUG)
        {
            ServerTickEvents.START_WORLD_TICK.register((world) ->
            {
                synchronized (GIZMOS)
                {
                    for (Runnable gizmo : GIZMOS)
                    {
                        gizmo.run();
                    }
                    GIZMOS.clear();
                }
            });
        }
    }

    public static void gizmo(Runnable runnable)
    {
        if (!ENABLE_DEBUG)
            return;

        synchronized (GIZMOS)
        {
            GIZMOS.add(runnable);
        }
    }
}
