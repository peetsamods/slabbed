package com.slabbed.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.slabbed.util.SlabTestKit;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /slabkit} — fills the running player's inventory with one stack of every representative item in
 * the shared {@link SlabTestKit} palette, so a live-test session starts with the whole category board in
 * hand rather than fishing items out of the creative menu one at a time.
 *
 * <p>Dev tooling that SHIPS in every jar (present, behaviour-neutral until invoked, op-gated), mirroring
 * {@link SlabRigCommand}. It is creative-agnostic: it writes the items straight into the server-side
 * inventory (no creative-menu interaction), then broadcasts the container so the client sees them. It
 * clears nothing — it only adds — so a tester can top up mid-session without losing what they built.
 */
public final class SlabKitCommand {

    private SlabKitCommand() {
    }

    /** Wires {@code /slabkit} into the server dispatcher for every world. */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                register(dispatcher));
    }

    /** Builds the {@code slabkit} node into {@code dispatcher}. Visible for the smoke test. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("slabkit")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(SlabKitCommand::give));
    }

    private static int give(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Player player = source.getEntity() instanceof Player p ? p : null;
        if (player == null) {
            source.sendFailure(Component.literal(
                    "[slabkit] fills your inventory — run it as a player, not the console."));
            return 0;
        }

        List<Item> kit = SlabTestKit.placeableItems();
        int given = 0;
        List<String> skipped = new ArrayList<>();
        for (Item item : kit) {
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) {
                continue;
            }
            // Hotbar first, then the remaining inventory (Inventory#add fills selected → hotbar → main).
            // add returns false once the 36 inventory slots are full — those items are skipped for capacity.
            if (player.getInventory().add(stack)) {
                given++;
            } else {
                skipped.add(BuiltInRegistries.ITEM.getKey(item).getPath());
            }
        }
        // Push the mutated inventory to the real client (no-op for the mock player in tests).
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
        }

        final int count = given;
        String message = "[slabkit] gave " + count + " test items";
        if (!skipped.isEmpty()) {
            message += " (skipped: " + String.join(", ", skipped) + ")";
        }
        final String messageFinal = message + ".";
        source.sendSuccess(() -> Component.literal(messageFinal), false);
        return given > 0 ? 1 : 0;
    }
}
