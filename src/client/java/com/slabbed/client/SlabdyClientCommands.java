package com.slabbed.client;

import com.slabbed.dev.SlabdyRowFormatter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * {@code /slabdy} — always present in every build, no gameplay effect on its own.
 *
 * <p>Unlike the old release-stripped design, this class is NEVER excluded from the jar
 * (see build.gradle): a player can always reach it by typing the command. There is no
 * ambient tracing or per-frame cost — {@code row} only computes anything when invoked.
 */
public final class SlabdyClientCommands {

    private SlabdyClientCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        literal("slabdy")
                                .then(literal("row").executes(SlabdyClientCommands::runRow))));
    }

    private static int runRow(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        HitResult target = client.crosshairTarget;

        if (player == null) {
            ctx.getSource().sendError(Text.literal("[slabdy] no player"));
            return 0;
        }
        if (!(target instanceof BlockHitResult blockHit)) {
            ctx.getSource().sendFeedback(Text.literal("[slabdy row] not looking at a block"));
            return 1;
        }

        ItemStack held = player.getMainHandStack();
        String row = SlabdyRowFormatter.formatRow(
                player.getEntityWorld(),
                blockHit.getBlockPos(),
                player.getEntityWorld().getBlockState(blockHit.getBlockPos()),
                blockHit.getSide(),
                blockHit.getPos(),
                held);

        ctx.getSource().sendFeedback(Text.literal("[slabdy row] " + row));
        return 1;
    }
}
