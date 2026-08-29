package com.slabbed.client;

import com.mojang.brigadier.context.CommandContext;
import com.slabbed.util.BuildStamp;
import com.slabbed.util.SlabbedDebugCommandTree;
import com.slabbed.util.SlabdyRowFormatter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/**
 * Registers the two debug commands that ship in EVERY jar, default off: {@code /slabdy} and
 * {@code /slabdev}. Called unconditionally from {@link SlabbedClient}, the {@code client} entrypoint
 * the shipped {@code fabric.mod.json} declares — no {@code isDevelopmentEnvironment()} guard and no
 * reflection, which is what makes them reachable on a release jar.
 *
 * <p><b>Client commands, deliberately.</b> On this line every debug surface is client state: the
 * target-dy overlay draws on the local HUD and the recorder writes to the local game directory.
 * A Fabric client command never reaches the server, so these cannot affect another player or a
 * server's disk. The 1.21.11 sibling's {@code /slabdev} is a server command gated at permission
 * level 2 because it writes an audit report into the <em>server's</em> game directory; there is no
 * such server-side surface here to gate. Gating a purely local HUD toggle at op level would
 * additionally make it unusable in an ordinary singleplayer world, i.e. it would defeat the ruling
 * these commands exist to satisfy. Both roots must also stay client-side together: Fabric's client
 * dispatcher forwards a command to the server only on "unknown command", so a client root would
 * swallow a same-named server subcommand rather than pass it along.
 *
 * <p><b>Inert until invoked.</b> Registration is one callback that builds two Brigadier trees. No
 * tick hook, no HUD element, no lifecycle listener, no world-save writer, no disk access. Nothing
 * below runs until someone types a command.
 *
 * <p>Node structure and the honest-degradation strings live in {@link SlabbedDebugCommandTree},
 * which is headless and therefore covered by a real GameTest.
 */
public final class SlabbedDebugCommands {

    private SlabbedDebugCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(SlabbedDebugCommandTree.slabdy(SlabbedDebugCommands::session));
            dispatcher.register(SlabbedDebugCommandTree.slabdev(SlabbedDebugCommands::session));
        });
    }

    private static SlabbedDebugCommandTree.Session session(CommandContext<FabricClientCommandSource> ctx) {
        return new ClientSession(ctx.getSource());
    }

    private record ClientSession(FabricClientCommandSource source) implements SlabbedDebugCommandTree.Session {

        @Override
        public void feedback(String line) {
            source.sendFeedback(Component.literal(line));
        }

        /**
         * The crosshair target's diagnostic dump, computed by the shipped headless formatter — so
         * this subcommand is fully functional in a release jar, with no diagnostics companion
         * needed. Empty list when the crosshair is not on a block.
         */
        @Override
        public List<String> targetRow() {
            Minecraft client = source.getClient();
            if (client == null || client.level == null) {
                return List.of();
            }
            HitResult target = client.hitResult;
            if (!(target instanceof BlockHitResult blockHit) || target.getType() != HitResult.Type.BLOCK) {
                return List.of();
            }
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = client.level.getBlockState(pos);
            ItemStack held = client.player == null ? ItemStack.EMPTY : client.player.getMainHandItem();
            return SlabdyRowFormatter.formatRow(
                    client.level, pos, state, blockHit.getDirection(), blockHit.getLocation(), held);
        }

        @Override
        public boolean overlayAvailable() {
            return SlabbedDebugToolBridge.available();
        }

        @Override
        public boolean overlayEnabled() {
            return SlabbedDebugToolBridge.overlayEnabled();
        }

        @Override
        public void setOverlayEnabled(boolean enabled) {
            SlabbedDebugToolBridge.setOverlayEnabled(enabled);
        }

        @Override
        public boolean recorderAvailable() {
            return SlabbedDebugToolBridge.available();
        }

        @Override
        public boolean recorderEnabled() {
            return SlabbedDebugToolBridge.recorderEnabled();
        }

        @Override
        public void setRecorderEnabled(boolean enabled) {
            SlabbedDebugToolBridge.setRecorderEnabled(enabled);
        }

        @Override
        public String recorderStatus() {
            return SlabbedDebugToolBridge.recorderStatus();
        }

        @Override
        public String buildStamp() {
            return BuildStamp.describeShort();
        }

        @Override
        public List<String> chunkGauge() {
            Minecraft client = source.getClient();
            if (client == null || client.level == null || client.player == null) {
                return List.of();
            }
            // The integrated-server chunk when present (the store's authoritative copy), else the
            // client chunk — both carry the synced attachments, and MEASURING is all this does.
            var level = client.getSingleplayerServer() != null
                    ? client.getSingleplayerServer().getLevel(client.level.dimension())
                    : null;
            var world = level != null ? level : client.level;
            return com.slabbed.anchor.ChunkPlacementGauge.report(
                    world.getChunkAt(client.player.blockPosition()),
                    world.registryAccess());
        }
    }
}
