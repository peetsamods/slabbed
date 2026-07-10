package com.slabbed.test;

import com.mojang.brigadier.CommandDispatcher;
import com.slabbed.anchor.SlabAnchorAttachment;
import com.slabbed.command.SlabCheckCommand;
import com.slabbed.command.SlabKitCommand;
import com.slabbed.command.SlabRigCommand;
import com.slabbed.util.SlabSupport;
import com.slabbed.util.SlabTestKit;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Real-dispatcher smoke tests for the test-kit commands {@code /slabkit}, {@code /slabrig mega}, and
 * {@code /slabcheck} — each exercised through a freshly-registered command dispatcher against a
 * positioned player command source, exactly like {@link SlabRigCommandSmokeTest}.
 *
 * <ul>
 *   <li>{@code /slabkit} fills the player's inventory with the placeable kit (a full inventory).</li>
 *   <li>{@code /slabrig mega 1} builds all four support variants, auto-places kit items on each via the
 *       real {@code useOn} path, and self-verifies each row's seat against its sign dy.</li>
 *   <li>{@code /slabcheck} is silent (0 violations) on a clean freshly-placed cell, and reports a
 *       violation when the stored placement height is manufactured to drift from the live reading.</li>
 * </ul>
 */
public final class SlabTestKitCommandsTest {

    private static final double EPS = 1.0e-6;

    // ── /slabkit ────────────────────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabkitFillsInventory(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        CommandSourceStack source = playerSource(w, player, h.absolutePos(new BlockPos(1, 2, 1)));

        exec(h, source, "slabkit", SlabKitCommand::register);

        int kit = SlabTestKit.placeableItems().size();
        int expected = Math.min(kit, 36); // the base inventory holds 36 stacks (hotbar + main)
        int filled = countNonEmpty(player);
        if (filled < expected) {
            throw h.assertionException("slabkit should fill " + expected + " inventory slots, filled " + filled
                    + " (kit=" + kit + ")");
        }
        // The regression-critical items must be among those actually given (they sit within the first 36).
        Item[] critical = {Items.POINTED_DRIPSTONE, Items.POWDER_SNOW_BUCKET, Items.LADDER, Items.RAIL};
        for (Item item : critical) {
            if (!inventoryHas(player, item)) {
                throw h.assertionException("slabkit must hand out the regression-critical item "
                        + item + " (it must sit within the first 36 placeable kit entries)");
            }
        }
        h.succeed();
    }

    // ── /slabrig mega ─────────────────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabrigMegaBuildsAndPlaces(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        // Feet at (1,2,0) facing SOUTH -> base (1,1,3); the 1-column mega footprint fits the 8x8x8 arena.
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        CommandSourceStack source = playerSource(w, player, h.absolutePos(new BlockPos(1, 2, 0)));

        int result = execResult(source, "slabrig mega 1", SlabRigCommand::register);

        Direction facing = SlabRigCommand.rigFacing(source);
        BlockPos base = SlabRigCommand.rigBase(source);
        if (facing != Direction.SOUTH) {
            throw h.assertionException("premise: expected SOUTH facing, got " + facing);
        }

        // The four seats (column 0) — recomputed to match buildMegaColumn — must read their sign dy.
        BlockPos seat0 = base.above(1);                                                    // bare bottom slab
        BlockPos seat1 = base.relative(facing, 1).above(2);                                // slab + block
        BlockPos seat2 = base.relative(facing, 2).above(4).relative(facing.getCounterClockWise()); // compound side slab
        BlockPos seat3 = base.relative(facing, 3).above(2);                                // overhang-and-ceiling
        assertOffset(h, w, seat0, 0.0, "Row 0 seat (bottom slab)");
        assertOffset(h, w, seat1, -0.5, "Row 1 seat (slab+block)");
        assertOffset(h, w, seat2, -1.0, "Row 2 seat (compound side slab)");
        assertOffset(h, w, seat3, -0.5, "Row 3 seat (overhang-and-ceiling)");

        // At least one object auto-placed: the first kit item (a slab) lands on Row 0's seat.
        if (w.getBlockState(seat0.above()).isAir()) {
            throw h.assertionException("mega must auto-place at least one object (Row 0 seat top is air at "
                    + seat0.above().toShortString() + ")");
        }

        // Self-verify passed -> the command reported success.
        if (result != 1) {
            throw h.assertionException("/slabrig mega 1 must report success (self-verify), returned " + result);
        }
        h.succeed();
    }

    // ── /slabcheck ────────────────────────────────────────────────────────────────

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabcheckSilentOnCleanRig(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        // A genuinely-placed cell: build a lowered slab stack and place a torch via the real useOn path,
        // which captures the placement dy. Nothing is mutated, so stored == live.
        BlockPos support = buildLoweredSlabStack(h, w);
        place(h, Items.TORCH.getDefaultInstance(), support, Direction.UP);
        BlockPos placed = support.above();
        if (w.getBlockState(placed).isAir()) {
            throw h.assertionException("premise: torch placement must succeed");
        }
        if (Double.isNaN(SlabAnchorAttachment.storedPlacementDy(w, placed))) {
            throw h.assertionException("premise: the placement must have stored a placement dy (nothing to scan)");
        }

        CommandSourceStack source = positionSource(w, Vec3.atCenterOf(placed));
        int result = execResult(source, "slabcheck 4", SlabCheckCommand::register);
        if (result != 1) {
            throw h.assertionException("/slabcheck on a clean rig must report 0 violations (return 1), got " + result);
        }
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabcheckDetectsManufacturedViolation(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        // Force the frozen-dy read short-circuit OFF for the duration so the live lane genuinely recomputes
        // (the detector compares the stored placement value against the LIVE reading); restore after.
        boolean savedFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = false;
        try {
            // A lowered full block (on a bottom slab) reads -0.5; store that as its placement dy.
            BlockPos ground = h.absolutePos(new BlockPos(3, 1, 3));
            w.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
            BlockPos carrier = ground.above();
            w.setBlock(carrier, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
            BlockPos fb = ground.above(2);
            w.setBlock(fb, Blocks.STONE.defaultBlockState(), 2);
            double placementDy = SlabSupport.getYOffset(w, fb, w.getBlockState(fb));
            if (Math.abs(placementDy + 0.5) > EPS) {
                throw h.assertionException("premise: the lowered full block must read -0.5, got " + placementDy);
            }
            SlabAnchorAttachment.capturePlacementDy(w, fb, w.getBlockState(fb));
            if (Math.abs(SlabAnchorAttachment.storedPlacementDy(w, fb) + 0.5) > EPS) {
                throw h.assertionException("premise: stored placement dy must be -0.5");
            }

            // Swap the carrier from a bottom slab to a full block -> the block is now flush (live 0.0),
            // but its STORED placement value is still -0.5. That drift is the manufactured violation.
            w.setBlock(carrier, Blocks.STONE.defaultBlockState(), 2);
            double liveNow = SlabSupport.getYOffset(w, fb, w.getBlockState(fb));
            if (Math.abs(liveNow - 0.0) > EPS) {
                throw h.assertionException("premise: after the carrier swap the live reading must be 0.0, got " + liveNow);
            }

            CommandSourceStack source = positionSource(w, Vec3.atCenterOf(fb));
            int result = execResult(source, "slabcheck 3", SlabCheckCommand::register);
            if (result != 0) {
                throw h.assertionException("/slabcheck must report the manufactured violation (return 0), got " + result);
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = savedFrozen;
        }
        h.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void slabcheckFrozenOnReportsWouldMoveNotHardViolation(GameTestHelper h) {
        ServerLevel w = h.getLevel();
        // Force frozen-dy ON for the duration: the render authority becomes the store, so a manufactured
        // neighbour-swap can no longer show as a hard store-desync — but it MUST surface as would-move.
        boolean savedFrozen = SlabAnchorAttachment.FROZEN_DY_ENABLED;
        SlabAnchorAttachment.FROZEN_DY_ENABLED = true;
        try {
            // Build a lowered full block (reads -0.5 un-stored) and store -0.5 as its placement height.
            BlockPos ground = h.absolutePos(new BlockPos(3, 1, 3));
            w.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
            BlockPos carrier = ground.above();
            w.setBlock(carrier, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
            BlockPos fb = ground.above(2);
            w.setBlock(fb, Blocks.STONE.defaultBlockState(), 2);
            double unstored = SlabSupport.getUnstoredYOffset(w, fb, w.getBlockState(fb));
            if (Math.abs(unstored + 0.5) > EPS) {
                throw h.assertionException("premise: the lowered full block must read -0.5 un-stored, got " + unstored);
            }
            SlabAnchorAttachment.capturePlacementDy(w, fb, w.getBlockState(fb));

            // Swap the carrier to a full block: un-stored now reads 0.0, but the store still says -0.5.
            // Frozen ON => the drawn (render) height IS the store => no hard desync; would-move sees the drift.
            w.setBlock(carrier, Blocks.STONE.defaultBlockState(), 2);
            if (Math.abs(SlabSupport.getYOffset(w, fb, w.getBlockState(fb)) + 0.5) > EPS) {
                throw h.assertionException("premise: frozen ON, the drawn height must be the stored -0.5");
            }

            StringBuilder chat = new StringBuilder();
            CommandSourceStack source = capturingSource(w, Vec3.atCenterOf(fb), chat);
            int result = execResult(source, "slabcheck 3", SlabCheckCommand::register);

            // Frozen ON => 0 hard store-desyncs => command succeeds (returns 1).
            if (result != 1) {
                throw h.assertionException("/slabcheck frozen ON must report 0 hard violations (return 1), got " + result);
            }
            String out = chat.toString();
            if (!out.contains("store-desyncs: 0")) {
                throw h.assertionException("frozen ON must report 0 store-desyncs; chat was: " + out);
            }
            if (!out.contains("would-move-if-unfrozen: 1")) {
                throw h.assertionException("frozen ON must report would-move-if-unfrozen: 1; chat was: " + out);
            }
        } finally {
            SlabAnchorAttachment.FROZEN_DY_ENABLED = savedFrozen;
        }
        h.succeed();
    }

    // ── harness ────────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface Registrar {
        void register(CommandDispatcher<CommandSourceStack> dispatcher);
    }

    /** A player command source (getEntity() is a Player), positioned facing SOUTH with full permissions. */
    private static CommandSourceStack playerSource(ServerLevel w, Player player, BlockPos feet) {
        return w.getServer().createCommandSourceStack()
                .withLevel(w)
                .withEntity(player)
                .withPosition(Vec3.atBottomCenterOf(feet))
                .withRotation(new Vec2(0.0f, 0.0f))
                .withPermission(PermissionSet.ALL_PERMISSIONS);
    }

    /** A playerless source at a fixed position (enough for /slabcheck, which only reads a radius). */
    private static CommandSourceStack positionSource(ServerLevel w, Vec3 pos) {
        return w.getServer().createCommandSourceStack()
                .withLevel(w)
                .withPosition(pos)
                .withPermission(PermissionSet.ALL_PERMISSIONS);
    }

    /** A positioned source whose success/failure feedback is appended (as plain strings) to {@code sink}. */
    private static CommandSourceStack capturingSource(ServerLevel w, Vec3 pos, StringBuilder sink) {
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(net.minecraft.network.chat.Component message) {
                sink.append(message.getString()).append('\n');
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        return positionSource(w, pos).withSource(capture);
    }

    private static boolean inventoryHas(Player player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }

    private static void exec(GameTestHelper h, CommandSourceStack source, String command, Registrar registrar) {
        if (execResult(source, command, registrar) < 0) {
            throw h.assertionException("/" + command + " threw");
        }
    }

    private static int execResult(CommandSourceStack source, String command, Registrar registrar) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        registrar.register(dispatcher);
        try {
            return dispatcher.execute(command, source);
        } catch (Exception e) {
            return -1;
        }
    }

    private static int countNonEmpty(Player player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (!player.getInventory().getItem(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static void place(GameTestHelper h, ItemStack stack, BlockPos clicked, Direction face) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(clicked).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, clicked, false)));
    }

    /** Ground, slab(0), stone(-0.5), slab(-0.5): the deep lowered-slab support (seat reads -0.5). */
    private static BlockPos buildLoweredSlabStack(GameTestHelper h, ServerLevel w) {
        BlockPos base = h.absolutePos(new BlockPos(3, 1, 2));
        w.setBlock(base, Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(1));
        w.setBlock(base.above(2), Blocks.STONE.defaultBlockState(), 2);
        bottomSlab(w, base.above(3));
        return base.above(3);
    }

    private static void bottomSlab(ServerLevel w, BlockPos pos) {
        w.setBlock(pos, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
    }

    private static void assertOffset(GameTestHelper h, ServerLevel w, BlockPos pos, double expected, String label) {
        double got = SlabSupport.getYOffset(w, pos, w.getBlockState(pos));
        if (Math.abs(got - expected) > EPS) {
            throw h.assertionException(label + " @" + pos.toShortString() + " must read " + expected
                    + " but read " + got);
        }
    }
}
