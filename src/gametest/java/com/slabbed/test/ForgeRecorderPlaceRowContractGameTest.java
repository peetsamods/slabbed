package com.slabbed.test;

import com.slabbed.util.SlabbedRecorder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Contract for the forensic placement row — the 26.2 recorder schema ported to Forge.
 *
 * <p>Drives a REAL placement (fake player, real {@code useOn} path) with the recorder enabled and
 * asserts the emitted {@code place_row} carries the schema's load-bearing fields: a trustworthy
 * action-origin label ({@code fake_player}, never inflating a player count), before/after marker
 * truth, the final dy, and explicit {@code absent} for the aim facts that only exist once the
 * Phase 6 aim capture lands. The recorder is dev-only (Model A — excluded from release jars);
 * this test runs against the dev classpath where it is present.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ForgeRecorderPlaceRowContractGameTest {

    @GameTest(template = "empty")
    public void placeRowCarriesTheForensicSchema(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();

        boolean toggledHere = false;
        if (!SlabbedRecorder.isEnabled()) {
            ctx.assertTrue(SlabbedRecorder.toggle(), "recorder must enable for the contract run");
            toggledHere = true;
        }
        try {
            BlockPos supportPos = ctx.absolutePos(new BlockPos(1, 1, 1));
            BlockPos objectPos = supportPos.above();
            world.setBlock(supportPos, Blocks.OAK_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);

            // Real placement through the real path — the same idiom the anchor gametests use.
            Player player = FakePlayerFactory.getMinecraft(world);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.STONE));
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(supportPos).add(0.0d, 0.5d, 0.0d),
                    Direction.UP, supportPos, false);
            ForgeHooks.onPlaceItemIntoWorld(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

            ctx.assertTrue(world.getBlockState(objectPos).is(Blocks.STONE),
                    "fixture: the stone must actually place at " + objectPos.toShortString());

            String log;
            try {
                log = Files.readString(SlabbedRecorder.currentLogPath(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new AssertionError("recorder log must be readable at "
                        + SlabbedRecorder.currentLogPath(), e);
            }
            String row = log.lines()
                    .filter(l -> l.contains("[place_row]"))
                    .filter(l -> l.contains("placePos=" + objectPos.toShortString()))
                    .reduce((first, second) -> second)
                    .orElse(null);
            ctx.assertTrue(row != null,
                    "a place_row for " + objectPos.toShortString() + " must be recorded");

            assertContains(ctx, row, "origin=fake_player",
                    "action origin must label the proxy honestly");
            assertContains(ctx, row, "side=SERVER", "side label");
            assertContains(ctx, row, "finalDy=-0.5", "final dy of stone on a bottom slab");
            assertContains(ctx, row, "anchoredBefore=false", "pre-placement anchor truth");
            assertContains(ctx, row, "anchoredAfter=true", "post-placement anchor truth");
            assertContains(ctx, row, "clickedFace=absent",
                    "aim facts must be explicitly absent until the Phase 6 aim capture");
            assertContains(ctx, row, "storedDy=absent",
                    "stored dy must be explicitly absent until the Phase 2 value bucket");
            assertContains(ctx, row, "reason=entity_place_event", "reason code");
        } finally {
            if (toggledHere && SlabbedRecorder.isEnabled()) {
                SlabbedRecorder.toggle();
            }
        }
        ctx.succeed();
    }

    private static void assertContains(GameTestHelper ctx, String row, String needle, String what) {
        ctx.assertTrue(row.contains(needle),
                what + ": row must contain '" + needle + "' — row was: " + row);
    }
}
