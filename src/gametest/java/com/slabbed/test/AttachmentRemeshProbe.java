package com.slabbed.test;

import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.client.ClientDy;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Controlled delayed-attachment probe using the ordinary client renderer. */
public final class AttachmentRemeshProbe implements ClientModInitializer {
    private static final BlockPos TARGET = new BlockPos(8, 200, 8);
    private int ticks, phase, since;
    private boolean requested;
    private volatile boolean prepared;
    private Long2ByteOpenHashMap dy;
    private LongOpenHashSet anchors, modern, flat;
    private int initialPixels, missingPixels, autoPixels;

    public void onInitializeClient() {
        if (!Boolean.getBoolean("slabbed.attachmentRemeshProbe")
                && !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("slabbed_attachment_proof")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try { tick(client); }
            catch (Throwable e) {
                System.err.println("[ATTACHMENT_REMESH] RED " + e);
                e.printStackTrace(); phase = 99; client.scheduleStop();
            }
        });
    }

    private void tick(MinecraftClient client) throws Exception {
        if (phase == 99) return;
        if (++ticks > 2400) throw new AssertionError("bounded client proof timed out");
        if (!requested && client.isFinishedLoading()) {
            if (client.world != null) throw new AssertionError("existing world is protected");
            requested = true;
            String worldName = "attachment-remesh-proof-" + Long.toUnsignedString(System.nanoTime());
            client.createIntegratedServerLoader().createAndStart(worldName,
                    new LevelInfo("Attachment Remesh Proof", GameMode.CREATIVE, false,
                            Difficulty.PEACEFUL, true, new GameRules(), DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(0L, false, false),
                    registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT)
                            .createDimensionsRegistryHolder(), null);
            return;
        }
        if (client.world == null || client.player == null || client.getServer() == null) return;
        client.options.pauseOnLostFocus = false;
        client.options.hudHidden = true;
        if (client.currentScreen != null) client.setScreen(null);
        client.player.getAbilities().flying = true;
        client.player.setVelocity(Vec3d.ZERO);
        client.player.refreshPositionAndAngles(8.5, 199.75 - client.player.getStandingEyeHeight(), 3.0, 0, 0);
        client.player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        if (phase == 0) {
            phase = 1; since = ticks;
            client.getServer().execute(() -> {
                var world = client.getServer().getOverworld();
                var player = world.getPlayers().getFirst();
                BlockPos support = TARGET.down();
                world.setBlockState(support, Blocks.STONE_SLAB.getDefaultState()
                        .with(SlabBlock.TYPE, SlabType.BOTTOM), Block.NOTIFY_ALL);
                var stack = new ItemStack(Items.RED_CONCRETE);
                player.setStackInHand(Hand.MAIN_HAND, stack);
                var hit = new BlockHitResult(new Vec3d(8.5, 199.5, 8.5), Direction.UP, support, false);
                var result = stack.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, hit));
                if (!result.isAccepted() || !world.getBlockState(TARGET).isOf(Blocks.RED_CONCRETE))
                    throw new AssertionError("real placement failed");
                world.setBlockState(support, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    BlockPos neighbor = TARGET.offset(dir);
                    world.setBlockState(neighbor.down(), Blocks.STONE.getDefaultState(), Block.NOTIFY_ALL);
                    var gray = new ItemStack(Items.GRAY_CONCRETE);
                    player.setStackInHand(Hand.MAIN_HAND, gray);
                    var grayHit = new BlockHitResult(new Vec3d(neighbor.getX() + .5,
                            neighbor.getY(), neighbor.getZ() + .5), Direction.UP, neighbor.down(), false);
                    var grayResult = gray.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, grayHit));
                    if (!grayResult.isAccepted() || SlabAnchorAttachment.storedPlacementDy(world, neighbor) != 0)
                        throw new AssertionError("occluder must be placed and retain zero height");
                    world.setBlockState(neighbor.down(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
                player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                System.out.println("[ATTACHMENT_REMESH] server permanent-dy="
                        + SlabAnchorAttachment.storedPlacementDy(world, TARGET));
                prepared = true;
            });
            return;
        }
        if (!prepared || ticks - since < 80) return;
        var chunk = client.world.getWorldChunk(TARGET);
        if (phase == 1) {
            client.worldRenderer.reload(); phase = 2; since = ticks; return;
        }
        if (phase == 2) {
            requireDy(client, -0.5);
            initialPixels = capture(client, "initial_visible");
            if (initialPixels < 50) throw new AssertionError("visible-scene control invalid: " + initialPixels);
            dy = new Long2ByteOpenHashMap(chunk.getAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE));
            anchors = copy(chunk.getAttached(SlabAnchorAttachment.ANCHOR_TYPE));
            modern = copy(chunk.getAttached(SlabAnchorAttachment.MODERN_PLACEMENT_TYPE));
            flat = copy(chunk.getAttached(SlabAnchorAttachment.FROZEN_FLAT_TYPE));
            var withheldDy = new Long2ByteOpenHashMap(dy); withheldDy.remove(TARGET.asLong());
            chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, withheldDy);
            chunk.setAttached(SlabAnchorAttachment.ANCHOR_TYPE, without(anchors));
            chunk.setAttached(SlabAnchorAttachment.MODERN_PLACEMENT_TYPE, without(modern));
            chunk.setAttached(SlabAnchorAttachment.FROZEN_FLAT_TYPE, without(flat));
            requireDy(client, 0);
            client.worldRenderer.reload(); phase = 3; since = ticks; return;
        }
        if (phase == 3) {
            missingPixels = capture(client, "before_attachment");
            if (missingPixels > initialPixels / 4) throw new AssertionError("withheld-height control still visible");
            chunk.setAttached(SlabAnchorAttachment.PLACEMENT_DY_TYPE, dy);
            chunk.setAttached(SlabAnchorAttachment.ANCHOR_TYPE, anchors);
            chunk.setAttached(SlabAnchorAttachment.MODERN_PLACEMENT_TYPE, modern);
            chunk.setAttached(SlabAnchorAttachment.FROZEN_FLAT_TYPE, flat);
            phase = 4; since = ticks; return;
        }
        if (phase == 4) {
            requireDy(client, -0.5);
            autoPixels = capture(client, "after_attachment");
            client.worldRenderer.reload(); phase = 5; since = ticks; return;
        }
        if (phase == 5) {
            requireDy(client, -0.5);
            int forced = capture(client, "after_manual_refresh");
            System.out.println("[ATTACHMENT_REMESH] initial=" + initialPixels + " withheld=" + missingPixels
                    + " automatic=" + autoPixels + " forced=" + forced);
            if (forced < initialPixels / 2) throw new AssertionError("manual-refresh control failed");
            if (autoPixels < forced / 2) throw new AssertionError("height synced, mesh stayed invisible until manual refresh");
            System.out.println("[ATTACHMENT_REMESH] GREEN mesh refreshed after attachment with unchanged block state");
            phase = 99; client.scheduleStop();
        }
    }

    private static LongOpenHashSet copy(LongOpenHashSet set) {
        return set == null ? new LongOpenHashSet() : new LongOpenHashSet(set);
    }

    private static LongOpenHashSet without(LongOpenHashSet set) {
        var result = copy(set); result.remove(TARGET.asLong()); return result;
    }

    private static void requireDy(MinecraftClient client, double expected) {
        double actual = ClientDy.dyFor(client.world, TARGET, client.world.getBlockState(TARGET));
        System.out.println("[ATTACHMENT_REMESH] client-dy=" + actual + " expected=" + expected);
        if (actual != expected) throw new AssertionError("height premise differs");
    }

    private static int capture(MinecraftClient client, String name) throws Exception {
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            image.writeTo(client.runDirectory.toPath().resolve("attachment-" + name + ".png"));
            int count = 0;
            for (int x = image.getWidth() * 3 / 10; x < image.getWidth() * 7 / 10; x++)
                for (int y = image.getHeight() * 3 / 10; y < image.getHeight() * 7 / 10; y++) {
                    int rgb = image.getColor(x, y);
                    int red = rgb & 255, green = (rgb >> 8) & 255, blue = (rgb >> 16) & 255;
                    if (red > 60 && red > green * 1.4 && red > blue * 1.4) count++;
                }
            System.out.println("[ATTACHMENT_REMESH] frame=" + name + " red-pixels=" + count);
            return count;
        }
    }
}
