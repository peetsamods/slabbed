package com.slabbed.test;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.test.bbe.RenderCapture;
import com.slabbed.test.support.TestConnections;
import com.slabbed.util.SlabSupport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Opt-in real-mod regression. The fixture activates the test-only buffer/submit observers and
 * selects this entrypoint; the ordinary client suite does not silently claim BBE coverage.
 * Static and animated owners are sampled separately, from fresh frames after each transition.
 */
public final class BetterBlockEntitiesClientGameTest implements FabricClientGameTest {
    private static final double EPSILON = 1.0e-4;
    private final List<Map<String, Object>> rows = new ArrayList<>();
    private boolean bbeRequired;
    private boolean sodiumRequired;

    private record Case(String name, BlockPos support, Item item, Block block, boolean lowered) {
        BlockPos pos() { return support.above(); }
        double dy() { return lowered ? -0.5 : 0.0; }
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        requireExpectedRuntime();
        List<Case> cases = new ArrayList<>();
        var sp = ctx.worldBuilder().setUseConsistentSettings(true).create();
        boolean closed = false;
        try {
            TestConnections.waitForChunksDownload(sp);
            ctx.waitFor(c -> c.player != null && c.gameMode != null, 400);
            BlockPos origin = sp.getServer().computeOnServer(s ->
                    s.getPlayerList().getPlayers().getFirst().blockPosition().above(4).immutable());
            int count = Integer.getInteger("probe.count", 6);
            require(count == 2 || count == 6, "Select the chest pair or all three block-entity pairs");
            for (int i = 0; i < count; i++) {
                int kind = i / 2;
                Item item = kind == 0 ? Items.CHEST : kind == 1 ? Items.TRAPPED_CHEST : Items.SHULKER_BOX;
                Block block = kind == 0 ? Blocks.CHEST : kind == 1 ? Blocks.TRAPPED_CHEST : Blocks.SHULKER_BOX;
                boolean lowered = i % 2 == 0;
                Case fixture = new Case(block.getDescriptionId() + (lowered ? "-lowered" : "-flat"),
                        origin.east(i * 5), item, block, lowered);
                cases.add(fixture);
                place(ctx, sp, fixture);
                view(ctx, fixture);
                capture(ctx, fixture, "static");

                ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
                ctx.waitFor(c -> c.gui.screen() != null && (fixture.block() == Blocks.SHULKER_BOX
                        ? c.player.containerMenu instanceof ShulkerBoxMenu
                        : c.player.containerMenu instanceof ChestMenu), 100);
                capture(ctx, fixture, "open");
                ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
                ctx.waitFor(c -> c.gui.screen() == null, 100);
                capture(ctx, fixture, "closed");

                ctx.runOnClient(c -> {
                    c.player.setYRot(c.player.getYRot() + 180);
                    c.player.setXRot(0);
                });
                ctx.waitTicks(35);
                view(ctx, fixture);
                capture(ctx, fixture, "look-away-return");
            }

            var reload = ctx.computeOnClient(Minecraft::reloadResourcePacks);
            ctx.waitFor(c -> reload.isDone(), 600);
            require(!reload.isCompletedExceptionally(), "Resource reload failed");
            for (Case fixture : cases) {
                view(ctx, fixture);
                capture(ctx, fixture, "resource-reload");
            }

            var save = sp.getWorldSave();
            sp.close();
            closed = true;
            try (var reopened = save.open()) {
                TestConnections.waitForChunksDownload(reopened);
                ctx.waitFor(c -> c.level != null && c.player != null, 400);
                for (Case fixture : cases) {
                    view(ctx, fixture);
                    capture(ctx, fixture, "world-reload");
                }
            }
        } finally {
            if (!closed) sp.close();
            ctx.setScreen(TitleScreen::new);
            write();
        }
        verifyPairs(cases);
        List<String> failures = rows.stream().filter(row -> row.entrySet().stream()
                        .anyMatch(e -> e.getKey().endsWith("_passed") && Boolean.FALSE.equals(e.getValue())))
                .map(row -> row.get("case") + "/" + row.get("phase")).toList();
        require(failures.isEmpty(), "Rendering/interaction failures: " + failures);
        System.out.println("CLIENT_GAMETEST | BetterBlockEntitiesClientGameTest | PASS rows=" + rows.size());
    }

    private void requireExpectedRuntime() {
        var expected = new LinkedHashMap<String, String>();
        var loaded = new LinkedHashMap<String, String>();
        var mismatches = new ArrayList<String>();
        for (String id : List.of("minecraft", "fabricloader", "fabric-api", "slabbed",
                "sodium", "betterblockentities")) {
            String version = System.getProperty("probe.expect." + id);
            String actual = FabricLoader.getInstance().getModContainer(id)
                    .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("absent");
            expected.put(id, version);
            loaded.put(id, actual);
            if (version == null || version.isBlank()) {
                mismatches.add(id + ": expected version must be declared (or explicitly absent)");
            } else if (!version.equals(actual)) {
                mismatches.add(id + ": expected " + version + ", loaded " + actual);
            }
        }
        try {
            Path output = Path.of(System.getProperty("probe.output")).resolveSibling("RUNTIME.json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(
                    Map.of("expected", expected, "loaded", loaded, "mismatches", mismatches)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot record runtime dependencies", e);
        }
        require(mismatches.isEmpty(), "Runtime dependency mismatch: " + mismatches);
        bbeRequired = !expected.get("betterblockentities").equals("absent");
        sodiumRequired = !expected.get("sodium").equals("absent");
        System.out.println("BBE_RUNTIME_EXPECTATIONS | PASS | " + loaded);
    }

    private void place(ClientGameTestContext ctx, TestSingleplayerContext sp, Case fixture) {
        BlockPos support = fixture.support();
        BlockPos pos = fixture.pos();
        RenderCapture.watch.add(pos);
        RenderCapture.watch.add(pos.east());
        sp.getServer().runOnServer(s -> {
            var world = s.overworld();
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y <= 3; y++)
                world.setBlock(support.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            world.setBlock(support, fixture.lowered()
                    ? Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                    : Blocks.STONE.defaultBlockState(), 3);
            world.setBlock(support.west().below(), Blocks.GLOWSTONE.defaultBlockState(), 3);
            // An ordinary neighbor catches a shared renderer origin leaking out of BBE's emission.
            world.setBlock(pos.east(), Blocks.STONE.defaultBlockState(), 3);
            var player = s.getPlayerList().getPlayers().getFirst();
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.setNoGravity(true);
            player.onUpdateAbilities();
            player.teleportTo(support.getX() + .5, support.getY() + .15, support.getZ() - 2.5);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(fixture.item(), 16));
            player.inventoryMenu.sendAllDataToRemote();
        });
        ctx.waitFor(c -> c.player.getMainHandItem().is(fixture.item())
                && c.level.getBlockState(support).is(fixture.lowered() ? Blocks.STONE_SLAB : Blocks.STONE), 160);
        ctx.runOnClient(c -> c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(support.getX() + .5,
                        support.getY() + (fixture.lowered() ? .5 : 1), support.getZ() + .5),
                        Direction.UP, support, false)));
        boolean accepted = false;
        for (int tick = 0; tick < 120; tick++) {
            accepted = sp.getServer().computeOnServer(s ->
                    s.overworld().getBlockState(pos).is(fixture.block()));
            if (accepted) break;
            ctx.waitTick();
        }
        require(accepted, "Placement rejected after server acknowledgment: " + fixture.name());
        ctx.waitFor(c -> c.level.getBlockState(pos).is(fixture.block()), 160);
    }

    private void view(ClientGameTestContext ctx, Case fixture) {
        BlockPos pos = fixture.pos();
        ctx.runOnClient(c -> {
            c.player.setPos(pos.getX() + .5, pos.getY() - .85, pos.getZ() - 2.5);
            Vec3 aim = new Vec3(pos.getX() + .5, pos.getY() + (fixture.lowered() ? 0 : .5), pos.getZ() + .5);
            Vec3 delta = aim.subtract(c.player.getEyePosition());
            c.player.setYRot((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90));
            c.player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y,
                    Math.sqrt(delta.x * delta.x + delta.z * delta.z))));
        });
        ctx.waitTicks(5);
    }

    private void capture(ClientGameTestContext ctx, Case fixture, String phase) {
        ctx.runOnClient(c -> {
            RenderCapture.clearSamples();
            for (BlockPos pos : RenderCapture.watch)
                c.level.setSectionDirtyWithNeighbors(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        });
        ctx.waitTicks(60);
        ctx.runOnClient(c -> measure(c, fixture, phase));
        ctx.takeScreenshot(fixture.name() + "-" + phase);
    }

    private void measure(Minecraft client, Case fixture, String phase) {
        BlockPos pos = fixture.pos();
        var state = client.level.getBlockState(pos);
        var outline = state.getShape(client.level, pos).bounds();
        var collision = CollisionContext.of(client.player).getCollisionShape(state, client.level, pos).bounds();
        Double mesh = RenderCapture.mesh.get(pos);
        Double pose = RenderCapture.vanilla.get(pos);
        boolean sodium = sodiumRequired;
        boolean bbe = bbeRequired;
        var row = new LinkedHashMap<String, Object>();
        row.put("case", fixture.name());
        row.put("phase", phase);
        row.put("storedDy", SlabAnchorAttachment.storedPlacementDy(client.level, pos));
        row.put("logicalDy", SlabSupport.getYOffset(client.level, pos, state));
        row.put("outlineMinY", outline.minY);
        row.put("collisionMinY", collision.minY);
        row.put("meshMinY", mesh);
        row.put("meshCount", RenderCapture.meshCount.getOrDefault(pos, 0));
        row.put("mesh_observed_passed", !bbe || phase.equals("open")
                || mesh != null && RenderCapture.meshCount.getOrDefault(pos, 0) > 0);
        row.put("blockPos", List.of(pos.getX(), pos.getY(), pos.getZ()));
        double supportTop = fixture.support().getY() + client.level.getBlockState(fixture.support())
                .getShape(client.level, fixture.support()).bounds().maxY;
        row.put("supportTop", supportTop);
        if (mesh != null) {
            row.put("renderedBottom", pos.getY() + mesh);
            row.put("seatError", pos.getY() + mesh - supportTop);
        }
        // BBE shulker geometry has a sub-milliblock inset; a placement-sized shift is still a failure.
        row.put("flat_mesh_passed", fixture.lowered() || mesh == null || Math.abs(mesh) < .001);
        row.put("vanillaPoseDy", pose);
        row.put("vanillaCount", RenderCapture.vanillaCount.getOrDefault(pos, 0));
        row.put("neighborMeshMinY", RenderCapture.mesh.get(pos.east()));
        row.put("placed_passed", state.is(fixture.block()));
        row.put("anchor_passed", near(SlabSupport.getYOffset(client.level, pos, state), fixture.dy())
                && near(SlabAnchorAttachment.storedPlacementDy(client.level, pos), fixture.dy()));
        row.put("outline_passed", near(outline.minY, fixture.dy()));
        row.put("collision_passed", near(collision.minY, fixture.dy()));
        row.put("raycast_passed", client.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos));
        row.put("observed_passed", mesh != null || pose != null);
        row.put("animation_passed", !phase.equals("open") || pose != null && near(pose, fixture.dy()));
        row.put("pose_passed", pose == null || near(pose, fixture.dy()));
        row.put("neighbor_passed", !sodium || RenderCapture.mesh.containsKey(pos.east())
                && near(RenderCapture.mesh.get(pos.east()), 0));
        rows.add(row);
        write();
    }

    private void verifyPairs(List<Case> cases) {
        // Compare the same model in both lanes: intrinsic BBE mesh insets are not placement offsets.
        for (int i = 0; i < cases.size(); i += 2) {
            Case low = cases.get(i);
            Case flat = cases.get(i + 1);
            for (var row : rows) {
                if (!row.get("case").equals(low.name()) || !(row.get("meshMinY") instanceof Number lowY)) continue;
                var peer = rows.stream().filter(r -> r.get("case").equals(flat.name())
                        && r.get("phase").equals(row.get("phase"))).findFirst().orElseThrow();
                boolean passed = peer.get("meshMinY") instanceof Number flatY
                        && near(lowY.doubleValue() - flatY.doubleValue(), -0.5);
                row.put("mesh_offset_passed", passed);
            }
        }
        write();
    }

    private void write() {
        try {
            Path output = Path.of(System.getProperty("probe.output"));
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(rows));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean near(double actual, double expected) { return Math.abs(actual - expected) < EPSILON; }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
