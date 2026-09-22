package com.slabbed.test;

import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.compat.sable.SablePhysicsHeight;
import com.slabbed.compat.sable.SablePlacementRefresh;
import com.slabbed.util.SlabSupport;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Drops real Sable physics objects onto a flush pad and a Slabbed-lowered pad, and onto a pad whose
 * stored height changes while an object already rests on it.
 *
 * <p>A physics object's resting height is the only thing measured: Sable's own rigid-body pose after
 * the object has settled. With the height bridge the lowered pad holds the object half a block lower
 * than the flush pad, and the refreshed pad lets its resting object down by the same half block.
 * The RED lane runs the same world with the bridge switched off and must see both pads alike.
 */
public final class P11SablePhysicsHeightProof {
    private static final String EXPECT_RED_PROPERTY = "slabbed.p11.sable.expect_red";
    private static final int SETTLE_TICKS = 120;
    private static final double TOLERANCE = 0.08d;

    private static boolean registered;
    private static boolean expectRed;
    private static int tick;
    private static int ground;
    private static BlockPos controlCenter;
    private static BlockPos loweredCenter;
    private static BlockPos refreshCenter;
    private static ServerSubLevel controlObject;
    private static ServerSubLevel loweredObject;
    private static ServerSubLevel refreshObject;
    private static double refreshBefore = Double.NaN;
    private static boolean done;

    private P11SablePhysicsHeightProof() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        expectRed = Boolean.getBoolean(EXPECT_RED_PROPERTY);
        NeoForge.EVENT_BUS.addListener(P11SablePhysicsHeightProof::onServerStarted);
        NeoForge.EVENT_BUS.addListener(P11SablePhysicsHeightProof::onServerTick);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        try {
            ServerLevel level = event.getServer().overworld();
            require(SablePhysicsHeight.ENABLED == !expectRed, "bridge_switch_matches_lane");
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            require(container != null, "sable_container_present");
            require(container.physicsSystem() instanceof SablePlacementRefresh, "sable_mixins_applied");

            BlockPos spawn = level.getSharedSpawnPos();
            ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
            controlCenter = new BlockPos(spawn.getX() - 10, ground, spawn.getZ());
            loweredCenter = new BlockPos(spawn.getX(), ground, spawn.getZ());
            refreshCenter = new BlockPos(spawn.getX() + 10, ground, spawn.getZ());

            // Flat worlds generate animals; one standing on a pad would refuse a real placement.
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
            for (Entity entity : level.getEntities((Entity) null,
                    new AABB(controlCenter).inflate(40.0d, 16.0d, 16.0d),
                    entity -> !(entity instanceof Player))) {
                entity.discard();
            }

            Player player = FakePlayerFactory.getMinecraft(level);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    buildFlushCell(level, player, controlCenter.offset(dx, 0, dz));
                    buildLoweredCell(level, player, loweredCenter.offset(dx, 0, dz));
                    buildFlushCell(level, player, refreshCenter.offset(dx, 0, dz));
                }
            }
            requireDy(level, controlCenter.above(), 0.0d, "control_pad_flush");
            requireDy(level, loweredCenter.above(), -0.5d, "lowered_pad_lowered");
            requireDy(level, refreshCenter.above(), 0.0d, "refresh_pad_starts_flush");

            measureSectionCost(level, container, spawn);

            controlObject = drop(level, controlCenter.above(4));
            loweredObject = drop(level, loweredCenter.above(4));
            refreshObject = drop(level, refreshCenter.above(4));
        } catch (Throwable t) {
            fail(event.getServer(), t);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (done || controlObject == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        tick++;
        try {
            if (tick == SETTLE_TICKS) {
                refreshBefore = restingY(refreshObject);
                ServerLevel level = server.overworld();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos cell = refreshCenter.offset(dx, 1, dz);
                        require(SlabPlacementHeightAttachment.putHalfSteps(
                                level.getChunkAt(cell), cell, -1), "refresh_fact_written");
                    }
                }
                requireDy(level, refreshCenter.above(), -0.5d, "refresh_pad_now_lowered");
            } else if (tick == 2 * SETTLE_TICKS) {
                double control = restingY(controlObject);
                double lowered = restingY(loweredObject);
                double refreshAfter = restingY(refreshObject);
                double top = ground + 2.0d;
                String numbers = String.format(Locale.ROOT,
                        "control=%.3f lowered=%.3f refreshBefore=%.3f refreshAfter=%.3f",
                        control - top, lowered - top, refreshBefore - top, refreshAfter - top);
                System.out.println("P11_SABLE | " + (expectRed ? "RED " : "") + numbers);
                require(near(control - top, 0.5d), "control_rests_on_flush_top " + numbers);
                require(near(refreshBefore - top, 0.5d), "refresh_rests_on_flush_top " + numbers);
                double expectedLowered = expectRed ? 0.5d : 0.0d;
                require(near(lowered - top, expectedLowered), "lowered_pad_resting_height " + numbers);
                require(near(refreshAfter - top, expectedLowered), "refreshed_pad_resting_height " + numbers);
                writeReceipt(expectRed ? "p11-sable-red.ok" : "p11-sable.ok", numbers + "\n");
                done = true;
                server.halt(false);
            }
        } catch (Throwable t) {
            fail(server, t);
        }
    }

    /**
     * Diagnostic only: the cost of uploading one solid section (terrain interior, the common case)
     * with the bridge on versus the RED lane's bridge off. Printed, never asserted.
     */
    private static void measureSectionCost(ServerLevel level, ServerSubLevelContainer container, BlockPos spawn) {
        int sectionX = (spawn.getX() >> 4) + 2;
        int sectionY = (ground >> 4) + 2;
        int sectionZ = spawn.getZ() >> 4;
        BlockPos origin = new BlockPos(sectionX << 4, sectionY << 4, sectionZ << 4);
        // Buried like terrain interior: the measured section stands on another solid section.
        for (int x = 0; x < 16; x++) {
            for (int y = -16; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        var chunk = level.getChunkAt(origin);
        var section = chunk.getSection(chunk.getSectionIndex(origin.getY()));
        var pipeline = container.physicsSystem().getPipeline();
        for (int warm = 0; warm < 5; warm++) {
            pipeline.handleChunkSectionAddition(section, sectionX, sectionY, sectionZ, false);
        }
        int runs = 20;
        long start = System.nanoTime();
        for (int run = 0; run < runs; run++) {
            pipeline.handleChunkSectionAddition(section, sectionX, sectionY, sectionZ, false);
        }
        long perSection = (System.nanoTime() - start) / runs;
        // A lowered block elsewhere in the same chunk forces the per-cell pass: the worst case.
        BlockPos marker = origin.offset(8, 20, 8);
        SlabPlacementHeightAttachment.putHalfSteps(chunk, marker, -1);
        for (int warm = 0; warm < 5; warm++) {
            pipeline.handleChunkSectionAddition(section, sectionX, sectionY, sectionZ, false);
        }
        start = System.nanoTime();
        for (int run = 0; run < runs; run++) {
            pipeline.handleChunkSectionAddition(section, sectionX, sectionY, sectionZ, false);
        }
        long perNearLowered = (System.nanoTime() - start) / runs;
        SlabPlacementHeightAttachment.remove(chunk, marker);
        System.out.println("P11_SABLE_COST | " + (expectRed ? "bridge off" : "bridge on")
                + " | solid section upload " + perSection / 1000 + " us"
                + " | same section in a chunk with a lowered block " + perNearLowered / 1000 + " us");
    }

    /** Stone on stone, both through real item use: the upper block stores a flush fact. */
    private static void buildFlushCell(ServerLevel level, Player player, BlockPos cell) {
        level.setBlock(cell, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        use(level, player, Blocks.STONE, cell, cell.getY() + 1.0d);
    }

    /** A bottom slab on the ground, then stone on the slab: the stone stores a -0.5 fact. */
    private static void buildLoweredCell(ServerLevel level, Player player, BlockPos cell) {
        use(level, player, Blocks.STONE_SLAB, cell.below(), cell.getY());
        use(level, player, Blocks.STONE, cell, cell.getY() + 0.5d);
    }

    private static void use(ServerLevel level, Player player, Block held, BlockPos clicked, double hitY) {
        ItemStack stack = new ItemStack(held.asItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(clicked.getX() + 0.5d, hitY, clicked.getZ() + 0.5d),
                        Direction.UP, clicked, false)));
        require(result != null && result.consumesAction(), "placement_" + held + "_at_" + clicked.toShortString());
    }

    private static ServerSubLevel drop(ServerLevel level, BlockPos at) {
        level.setBlock(at, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        ServerSubLevel object = SubLevelAssemblyHelper.assembleBlocks(level, at, List.of(at),
                new BoundingBox3i(at.getX(), at.getY(), at.getZ(), at.getX(), at.getY(), at.getZ()));
        require(object != null && !object.isRemoved(), "assembled_" + at.toShortString());
        return object;
    }

    /** The object's center of mass; a one-block object resting on a surface sits half a block above it. */
    private static double restingY(ServerSubLevel object) {
        require(!object.isRemoved(), "object_still_present");
        return object.logicalPose().position().y();
    }

    private static void requireDy(ServerLevel level, BlockPos pos, double expected, String check) {
        double dy = SlabSupport.getYOffset(level, pos, level.getBlockState(pos));
        require(Math.abs(dy - expected) < 1.0e-6d, check + " dy=" + dy);
    }

    private static boolean near(double actual, double expected) {
        return Math.abs(actual - expected) <= TOLERANCE;
    }

    private static void require(boolean condition, String check) {
        if (!condition) {
            throw new IllegalStateException("P11 Sable proof check failed: " + check);
        }
    }

    private static void fail(MinecraftServer server, Throwable t) {
        if (done) {
            return;
        }
        done = true;
        System.out.println("P11_SABLE | FAILED " + t.getMessage());
        writeReceipt("p11-sable.failed", String.valueOf(t.getMessage()) + "\n");
        server.halt(false);
    }

    private static void writeReceipt(String fileName, String content) {
        try {
            Path proofDirectory = Path.of("proof");
            Files.createDirectories(proofDirectory);
            Files.writeString(proofDirectory.resolve(fileName), content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write the P11 Sable proof receipt", exception);
        }
    }
}
