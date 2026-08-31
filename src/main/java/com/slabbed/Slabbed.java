package com.slabbed;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

@Mod(Slabbed.MOD_ID)
public class Slabbed {
    public static final String MOD_ID = "slabbed";
    public static final Logger LOGGER = LoggerFactory.getLogger(Slabbed.class);
    private static final String P2B_PHASE_PROPERTY = "slabbed.p2b.phase";
    private static final String P10C_PHASE_PROPERTY = "slabbed.p10c.phase";
    private static final String P7_PROOF_PROPERTY = "slabbed.p7.proof";
    private static final String P8_PROOF_PROPERTY = "slabbed.p8.proof";
    private static final String P9_SNOW_PROOF_PROPERTY = "slabbed.p9.snow.proof";
    private static final String P9_SMOOTH_STEPS_PROOF_PROPERTY = "slabbed.p9.smooth_steps.proof";

    public Slabbed(IEventBus modEventBus) {
        LOGGER.info("Slabbed initialized");
        com.slabbed.anchor.SlabAnchorAttachment.register(modEventBus);
        com.slabbed.command.DeepDyCommand.register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            initClientFeatures(modEventBus);
        }
        if (!FMLLoader.isProduction()) {
            initDevFeatures();
        }
    }

    private static void initClientFeatures(IEventBus modEventBus) {
        try {
            Class<?> hookClass = Class.forName("com.slabbed.client.SlabbedClient");
            hookClass.getMethod("init", IEventBus.class).invoke(null, modEventBus);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Client hook is unavailable in this environment");
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError e) {
            LOGGER.warn("Failed to initialize client hook", e);
        }
    }

    private static void initDevFeatures() {
        registerDevHook("com.slabbed.dev.SlabbedDevCommands", "register");
        registerDevHook("com.slabbed.dev.SlabbedLab", "register");
        if (System.getProperty(P2B_PHASE_PROPERTY) != null) {
            registerRequiredDevHook(
                    "com.slabbed.test.SlabPlacementHeightP2bProof",
                    "register");
            if (FMLEnvironment.dist == Dist.CLIENT) {
                registerRequiredDevHook(
                        "com.slabbed.client.SlabPlacementHeightP2bClientProof",
                        "register");
            }
        }
        if (System.getProperty(P10C_PHASE_PROPERTY) != null) {
            registerRequiredDevHook(
                    "com.slabbed.test.DeepDyConsentP10cServerProof",
                    "register");
            if (FMLEnvironment.dist == Dist.CLIENT) {
                registerRequiredDevHook(
                        "com.slabbed.client.DeepDyConsentP10cClientProof",
                        "register");
            }
        }
        if (Boolean.getBoolean(P7_PROOF_PROPERTY) && FMLEnvironment.dist == Dist.CLIENT) {
            registerRequiredDevHook("com.slabbed.client.P7ClientVisualProof", "register");
        }
        if (Boolean.getBoolean(P8_PROOF_PROPERTY) && FMLEnvironment.dist == Dist.CLIENT) {
            registerRequiredDevHook("com.slabbed.client.P8TerrainSlabsGeometryProof", "register");
        }
        if (Boolean.getBoolean(P9_SNOW_PROOF_PROPERTY) && FMLEnvironment.dist == Dist.CLIENT) {
            registerRequiredDevHook("com.slabbed.client.P9SnowRealMagicCompositeProof", "register");
        }
        if (Boolean.getBoolean(P9_SMOOTH_STEPS_PROOF_PROPERTY) && FMLEnvironment.dist == Dist.CLIENT) {
            registerRequiredDevHook("com.slabbed.client.P9SmoothStepsMovementProof", "register");
        }
    }

    private static void registerDevHook(String className, String methodName) {
        try {
            Class<?> hookClass = Class.forName(className);
            hookClass.getMethod(methodName).invoke(null);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Dev hook {} is unavailable in this environment", className);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | LinkageError e) {
            LOGGER.warn("Failed to initialize dev hook {}", className, e);
        }
    }

    private static void registerRequiredDevHook(String className, String methodName) {
        try {
            Class<?> hookClass = Class.forName(className);
            hookClass.getMethod(methodName).invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                 | InvocationTargetException | LinkageError e) {
            throw new IllegalStateException("Required development proof hook is unavailable", e);
        }
    }
}
