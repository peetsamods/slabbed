package com.slabbed.devtools;

import com.slabbed.Slabbed;
import com.slabbed.devtools.client.TargetDyOverlay;
import com.slabbed.util.SlabbedDiagnosticsBridge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/** Forge entrypoint for the optional Slabbed development-tools addon. */
@Mod(SlabbedDevTools.MOD_ID)
public final class SlabbedDevTools {
    public static final String MOD_ID = "slabbed_devtools";

    public SlabbedDevTools() {
        SlabbedDiagnosticsBridge.install(new SlabbedRecorderProvider());
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> TargetDyOverlay.init(MinecraftForge.EVENT_BUS));

        Slabbed.LOGGER.info(
                "Slabbed devtools addon initialized: recorderSchema=6 "
                        + "debugDefault=on-first-world-join recordDefault=on-first-world-join");
    }
}
