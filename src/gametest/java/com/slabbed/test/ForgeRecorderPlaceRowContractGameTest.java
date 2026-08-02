package com.slabbed.test;

import com.slabbed.util.SlabbedDiagnosticsBridge;
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

import java.util.concurrent.atomic.AtomicReference;
import java.util.LinkedHashMap;

/**
 * Contract for the core-to-diagnostics placement row seam.
 *
 * <p>Drives a REAL placement (fake player, real {@code useOn} path) through a temporary in-memory
 * provider and asserts the emitted {@code place_row} carries the load-bearing fields. This test
 * intentionally does not import the addon recorder: a green core GameTest must not depend on a
 * class that is absent from the release-shaped core jar. Packaged addon availability is a separate
 * artifact-boundary proof.
 */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class ForgeRecorderPlaceRowContractGameTest {

    @GameTest(template = "empty")
    public void placeRowCarriesTheForensicSchema(GameTestHelper ctx) {
        ServerLevel world = ctx.getLevel();

        // Failure containment is part of the core contract: a broken optional addon may not stop
        // or repeat a product action. Exercise every new schema-6 wrapper before installing the
        // capturing provider below.
        SlabbedDiagnosticsBridge.Provider previous = SlabbedDiagnosticsBridge.install(
                new SlabbedDiagnosticsBridge.Provider() {
                    @Override
                    public boolean recorderEnabled() {
                        throw new IllegalStateException("deliberate diagnostics failure");
                    }

                    @Override
                    public void recordAction(LinkedHashMap<String, String> fields) {
                        throw new IllegalStateException("deliberate diagnostics failure");
                    }
                });
        ctx.assertTrue(!SlabbedDiagnosticsBridge.isRecorderEnabled(),
                "a failing optional provider must degrade to recorder-off");
        SlabbedDiagnosticsBridge.recordAction(new LinkedHashMap<>());

        AtomicReference<LinkedHashMap<String, String>> lastPlaceRow = new AtomicReference<>();
        SlabbedDiagnosticsBridge.install(
                new SlabbedDiagnosticsBridge.Provider() {
                    @Override
                    public boolean available() {
                        return true;
                    }

                    @Override
                    public boolean recorderEnabled() {
                        return true;
                    }

                    @Override
                    public void recordAction(LinkedHashMap<String, String> fields) {
                        lastPlaceRow.set(new LinkedHashMap<>(fields));
                    }
                });
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

            LinkedHashMap<String, String> row = lastPlaceRow.get();
            ctx.assertTrue(row != null,
                    "an action row for " + objectPos.toShortString() + " must be recorded");

            assertEquals(ctx, row, "originHint", SlabbedDiagnosticsBridge.GAMETEST,
                    "fake-player actions must stay in the GameTest evidence class");
            assertEquals(ctx, row, "side", "server", "side label");
            assertEquals(ctx, row, "afterDy", "-0.5", "final dy of stone on a bottom slab");
            assertEquals(ctx, row, "anchoredBefore", "false", "pre-placement anchor truth");
            assertEquals(ctx, row, "anchoredAfter", "true", "post-placement anchor truth");
            assertEquals(ctx, row, "clickedFace", "none",
                    "aim facts must be explicitly absent until the Phase 6 aim capture");
            assertEquals(ctx, row, "afterStoredDy", "-0.5",
                    "since the Phase 4 writer, the row must carry the freshly stored height");
            assertEquals(ctx, row, "reason", "entity_place_event", "reason code");
        } finally {
            SlabbedDiagnosticsBridge.install(previous);
            // Test-store hygiene law: since the Phase 4 writer, this placement stores a height.
            com.slabbed.anchor.SlabAnchorAttachment.removePlacementDy(world,
                    ctx.absolutePos(new BlockPos(1, 1, 1)).above());
        }
        ctx.succeed();
    }

    private static void assertEquals(
            GameTestHelper ctx,
            LinkedHashMap<String, String> row,
            String key,
            String expected,
            String what) {
        ctx.assertTrue(expected.equals(row.get(key)),
                what + ": field '" + key + "' must be '" + expected + "' — row was: " + row);
    }
}
