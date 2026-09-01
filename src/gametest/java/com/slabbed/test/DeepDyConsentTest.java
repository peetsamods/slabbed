package com.slabbed.test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.slabbed.anchor.DeepDyConsentAttachment;
import com.slabbed.anchor.DeepDyConsentAttachment.GrantResult;
import com.slabbed.anchor.DeepDyConsentAttachment.Stamp;
import com.slabbed.anchor.DeepDyConsentAttachment.State;
import com.slabbed.anchor.SlabPlacementHeightAttachment;
import com.slabbed.util.PlacementDepthPolicy;
import com.slabbed.util.SlabSupport;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Save-consent, cap, and placement-permanence contract for the deeper dy alphabet. */
@GameTestHolder("slabbed")
@PrefixGameTestTemplate(false)
public final class DeepDyConsentTest {
    private static final String TEMPLATE = "empty";
    private static final double EPSILON = 1.0e-6d;

    @GameTest(
            batch = "slabbed_deep_consent_legacy",
            template = TEMPLATE)
    public void absentAndDisabledSavesRemainOnTheShippedFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<ServerLevel, Stamp> original = snapshot(level.getServer());
        BlockPos subject = helper.absolutePos(new BlockPos(3, 4, 3));
        try {
            setSaveStamp(level.getServer(), null);
            SlabSupport.armDeepAlphabet(false);
            prepareDeepFactlessSubject(helper, level, subject);

            assertExact(helper, SlabSupport.minResolvedDy(), PlacementDepthPolicy.MIN_TARGETABLE_DY,
                    "the floor no longer varies with consent state");
            // Derived descent floors at the resolved floor, and consent deepens the floor to
            // the envelope (maintainer ruling, 2026-08-21, matching the reference line).
            assertExact(helper, SlabSupport.getYOffset(level, subject, level.getBlockState(subject)),
                    PlacementDepthPolicy.MIN_TARGETABLE_DY,
                    "the floor no longer varies with consent state");
            helper.assertTrue(DeepDyConsentAttachment.state(level.getServer().overworld())
                            == State.ABSENT_LEGACY,
                    "reading a legacy save must not create a consent stamp");

            setSaveStamp(level.getServer(), Stamp.disabled());
            DeepDyConsentAttachment.reconcile(level.getServer());
            assertExact(helper, SlabSupport.minResolvedDy(), PlacementDepthPolicy.MIN_TARGETABLE_DY,
                    "an explicit off stamp must retain the shipped consent cache");
            assertExact(helper, SlabSupport.getYOffset(level, subject, level.getBlockState(subject)),
                    PlacementDepthPolicy.MIN_TARGETABLE_DY,
                    "the floor no longer varies with consent state");
            helper.assertTrue(DeepDyConsentAttachment.state(level.getServer().overworld()) == State.DISABLED,
                    "explicit off and absent must remain distinguishable facts");
        } finally {
            clearSubject(level, subject);
            restore(level.getServer(), original);
        }
        helper.succeed();
    }

    @GameTest(
            batch = "slabbed_deep_consent_new_save",
            template = TEMPLATE)
    public void newSaveInitializationStampsOffWithoutOverwritingAuthority(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Map<ServerLevel, Stamp> original = snapshot(server);
        CompoundTag futureTag = new CompoundTag();
        futureTag.putInt("version", 99);
        futureTag.putBoolean("enabled", true);
        futureTag.putString("future_field", "preserve");
        Stamp unknown = Stamp.fromTag(futureTag);
        try {
            setSaveStamp(server, null);
            SlabSupport.armDeepAlphabet(true);

            helper.assertTrue(DeepDyConsentAttachment.initializeNewSave(server.overworld()),
                    "a genuinely new unstamped save must receive an explicit off stamp");
            assertExact(helper, SlabSupport.minResolvedDy(), PlacementDepthPolicy.MIN_TARGETABLE_DY,
                    "the floor no longer varies with consent state");
            for (ServerLevel loaded : server.getAllLevels()) {
                helper.assertTrue(DeepDyConsentAttachment.state(loaded) == State.DISABLED,
                        "new-save initialization must mirror off to every loaded dimension");
            }
            helper.assertTrue(!DeepDyConsentAttachment.initializeNewSave(server.overworld()),
                    "repeated new-save initialization must be idempotent");

            setSaveStamp(server, unknown);
            helper.assertTrue(!DeepDyConsentAttachment.initializeNewSave(server.overworld()),
                    "new-save initialization must not overwrite unknown future authority");
            for (ServerLevel loaded : server.getAllLevels()) {
                Stamp retained = SlabbedTestAccess.consentStamp(loaded);
                helper.assertTrue(retained != null
                                && retained.state() == State.LOCKED_UNKNOWN
                                && retained.serializedTag().equals(unknown.serializedTag()),
                        "unknown future authority must remain byte-for-byte intact");
            }

            setSaveStamp(server, Stamp.enabled());
            SlabSupport.armDeepAlphabet(true);
            helper.assertTrue(!DeepDyConsentAttachment.initializeNewSave(server.overworld()),
                    "new-save initialization must never revoke enabled authority");
            helper.assertTrue(DeepDyConsentAttachment.state(server.overworld()) == State.ENABLED,
                    "enabled authority must survive an initialization replay");
            assertExact(helper, SlabSupport.minResolvedDy(),
                    PlacementDepthPolicy.MIN_TARGETABLE_DY,
                    "an initialization replay must not disarm an enabled save");
        } finally {
            restore(server, original);
        }
        helper.succeed();
    }

    @GameTest(
            batch = "slabbed_deep_consent_enable",
            template = TEMPLATE)
    public void enablingIsImmediateSaveWideAndOneWay(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Map<ServerLevel, Stamp> original = snapshot(server);
        BlockPos subject = helper.absolutePos(new BlockPos(3, 4, 3));
        try {
            setSaveStamp(server, Stamp.disabled());
            SlabSupport.armDeepAlphabet(false);
            prepareDeepFactlessSubject(helper, helper.getLevel(), subject);
            // This row used to discriminate on the deepening consent caused. Since the floor
            // moved to the envelope for every save (maintainer ruling, 2026-08-23) there is
            // nothing deeper to grant, so what must now be true is the opposite: the grant is
            // geometry-neutral. Measured on both sides of the transition below.
            double subjectBeforeGrant = SlabSupport.getYOffset(
                    helper.getLevel(), subject, helper.getLevel().getBlockState(subject));
            assertExact(helper, subjectBeforeGrant, PlacementDepthPolicy.MIN_TARGETABLE_DY,
                    "a factless subject over a deep support already reaches the envelope");

            helper.assertTrue(DeepDyConsentAttachment.grant(server) == GrantResult.ENABLED_NOW,
                    "the first authorized transition must enable this save");
            assertExact(helper, SlabSupport.minResolvedDy(), PlacementDepthPolicy.MIN_TARGETABLE_DY,
                    "the live server cache stays at the envelope across the transition");
            assertExact(helper,
                    SlabSupport.getYOffset(helper.getLevel(), subject, helper.getLevel().getBlockState(subject)),
                    subjectBeforeGrant,
                    "the grant must not move the subject at all");
            helper.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(
                            helper.getLevel().getChunkAt(subject), subject).isEmpty(),
                    "the deep discriminator must remain factless");

            int dimensions = 0;
            for (ServerLevel loaded : server.getAllLevels()) {
                dimensions++;
                helper.assertTrue(DeepDyConsentAttachment.state(loaded) == State.ENABLED,
                        "every loaded dimension must mirror the overworld authority");
            }
            helper.assertTrue(dimensions > 1,
                    "the save-wide proof requires more than one loaded dimension");
            helper.assertTrue(DeepDyConsentAttachment.grant(server) == GrantResult.ALREADY_ENABLED,
                    "a repeated transition must be idempotent");
            helper.assertTrue(!DeepDyConsentAttachment.initializeNewSave(server.overworld()),
                    "new-save initialization must never revoke an enabled save");
            helper.assertTrue(DeepDyConsentAttachment.state(server.overworld()) == State.ENABLED,
                    "the rejected off transition must leave consent intact");
        } finally {
            clearSubject(helper.getLevel(), subject);
            restore(server, original);
        }
        helper.succeed();
    }

    @GameTest(
            batch = "slabbed_deep_consent_facts",
            template = TEMPLATE)
    public void storedPlacementFactsIgnoreConsentAndSupportChanges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<ServerLevel, Stamp> original = snapshot(level.getServer());
        int[] halfSteps = {0, -1, -2, -4};
        BlockPos[] positions = new BlockPos[halfSteps.length];
        try {
            for (int index = 0; index < halfSteps.length; index++) {
                BlockPos pos = helper.absolutePos(new BlockPos(2 + index * 2, 4, 2));
                positions[index] = pos;
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                helper.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                                level.getChunkAt(pos), pos, halfSteps[index]),
                        "fixture must store every canonical placement fact");
            }

            for (boolean enabled : new boolean[] {false, true}) {
                SlabSupport.armDeepAlphabet(enabled);
                for (int index = 0; index < halfSteps.length; index++) {
                    BlockPos pos = positions[index];
                    level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    assertExact(helper,
                            SlabSupport.getYOffset(level, pos, level.getBlockState(pos)),
                            halfSteps[index] * 0.5d,
                            "stored facts must outrank consent and changed support");
                }
            }
        } finally {
            for (BlockPos pos : positions) {
                if (pos != null) {
                    SlabPlacementHeightAttachment.remove(level.getChunkAt(pos), pos);
                }
            }
            restore(level.getServer(), original);
        }
        helper.succeed();
    }

    @GameTest(
            batch = "slabbed_deep_consent_unknown",
            template = TEMPLATE)
    public void unknownSchemaFailsClosedWithoutBeingOverwritten(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Map<ServerLevel, Stamp> original = snapshot(server);
        CompoundTag raw = new CompoundTag();
        raw.putInt("version", 99);
        raw.putBoolean("enabled", true);
        raw.putString("future_field", "preserve");
        Stamp unknown = Stamp.fromTag(raw);
        try {
            setSaveStamp(server, unknown);
            DeepDyConsentAttachment.reconcile(server);
            helper.assertTrue(DeepDyConsentAttachment.state(server.overworld()) == State.LOCKED_UNKNOWN,
                    "a newer schema must be classified as locked unknown");
            assertExact(helper, SlabSupport.minResolvedDy(), PlacementDepthPolicy.MIN_TARGETABLE_DY,
                    "the floor no longer varies with consent state");
            helper.assertTrue(DeepDyConsentAttachment.grant(server) == GrantResult.LOCKED_UNKNOWN,
                    "this build must not overwrite a newer consent schema");
            helper.assertTrue(unknown.serializedTag().equals(
                            SlabbedTestAccess.consentStamp(
                                    server.overworld()).serializedTag()),
                    "locked data must remain byte-for-byte unchanged");
        } finally {
            restore(server, original);
        }
        helper.succeed();
    }

    @GameTest(
            batch = "slabbed_deep_consent_envelope",
            template = TEMPLATE)
    public void interactionEnvelopeIsUnchangedByConsent(GameTestHelper helper) {
        for (boolean enabled : new boolean[] {false, true}) {
            SlabSupport.armDeepAlphabet(enabled);
            helper.assertTrue(PlacementDepthPolicy.classify(
                            PlacementDepthPolicy.MIN_TARGETABLE_DY)
                            == PlacementDepthPolicy.Decision.SUPPORTED,
                    "exactly the floor must remain legal in both consent states");
            helper.assertTrue(PlacementDepthPolicy.classify(
                            PlacementDepthPolicy.MIN_TARGETABLE_DY - 0.5d)
                            == PlacementDepthPolicy.Decision.REFUSED_BELOW_TARGETABLE_FLOOR,
                    "below-envelope placement must remain refused in both consent states");
            // The radius is the floor's own consequence, not an independent number: a pick
            // window shallower than the floor would draw blocks that refuse to be clicked.
            helper.assertTrue(PlacementDepthPolicy.ownerWindowRadius()
                            == (int) Math.ceil(Math.abs(PlacementDepthPolicy.MIN_TARGETABLE_DY)),
                    "the targeting radius must cover the whole floor, in both consent states");
        }
        DeepDyConsentAttachment.reconcile(helper.getLevel().getServer());
        helper.succeed();
    }

    @GameTest(
            batch = "slabbed_deep_consent_command",
            template = TEMPLATE)
    public void serverCommandRequiresPermissionAndExplicitConfirmation(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Map<ServerLevel, Stamp> original = snapshot(server);
        RecordingCommandSource messages = new RecordingCommandSource();
        RecordingCommandSource otherMessages = new RecordingCommandSource();
        CommandSourceStack owner = server.createCommandSourceStack()
                .withSource(messages)
                .withPermission(Commands.LEVEL_OWNERS);
        CommandSourceStack otherOwner = server.createCommandSourceStack()
                .withSource(otherMessages)
                .withPermission(Commands.LEVEL_OWNERS);
        CommandSourceStack unprivileged = owner.withPermission(Commands.LEVEL_ALL);
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        try {
            setSaveStamp(server, Stamp.disabled());
            DeepDyConsentAttachment.reconcile(server);

            helper.assertTrue(commandIsRejected(dispatcher,
                            "slabbed deep-mode enable", unprivileged),
                    "an unprivileged source must not reach the irreversible command");
            helper.assertTrue(DeepDyConsentAttachment.state(server.overworld()) == State.DISABLED,
                    "permission denial must not change save consent");
            helper.assertTrue(dispatcher.execute("slabbed deep-mode confirm", owner) == 0,
                    "confirmation without a pending warning must be a no-op");

            messages.clear();
            helper.assertTrue(dispatcher.execute("slabbed deep-mode enable", owner) == 1,
                    "an owner must be able to request the warning");
            helper.assertTrue(DeepDyConsentAttachment.state(server.overworld()) == State.DISABLED,
                    "requesting the warning must not itself enable the save");
            assertExact(helper, SlabSupport.minResolvedDy(), PlacementDepthPolicy.MIN_TARGETABLE_DY,
                    "the floor no longer varies with consent state");
            Component warning = messages.last();
            helper.assertTrue(containsTranslation(warning, "commands.slabbed.deep_mode.warning"),
                    "the warning must remain translatable");
            helper.assertTrue(containsRunCommand(warning, "/slabbed deep-mode confirm"),
                    "the warning must expose an explicit permanent-enable action");
            helper.assertTrue(containsRunCommand(warning, "/slabbed deep-mode cancel"),
                    "the warning must expose a true cancel action");

            helper.assertTrue(dispatcher.execute("slabbed deep-mode cancel", owner) == 1,
                    "cancel must consume the pending confirmation");
            helper.assertTrue(dispatcher.execute("slabbed deep-mode confirm", owner) == 0,
                    "a canceled confirmation must not be reusable");
            helper.assertTrue(DeepDyConsentAttachment.state(server.overworld()) == State.DISABLED,
                    "cancel must leave the save explicitly off");

            helper.assertTrue(dispatcher.execute("slabbed deep-mode enable", owner) == 1,
                    "the owner must be able to request a fresh confirmation");
            helper.assertTrue(owner.getTextName().equals(otherOwner.getTextName()),
                    "the caller-identity discriminator requires the same display name");
            helper.assertTrue(dispatcher.execute("slabbed deep-mode confirm", otherOwner) == 0,
                    "a distinct same-name source must not consume another caller's confirmation");
            helper.assertTrue(DeepDyConsentAttachment.state(server.overworld()) == State.DISABLED,
                    "a foreign confirmation attempt must leave the save off");
            helper.assertTrue(dispatcher.execute("slabbed deep-mode confirm", owner) == 1,
                    "a live explicit confirmation must enable the save");
            helper.assertTrue(DeepDyConsentAttachment.state(server.overworld()) == State.ENABLED,
                    "confirmation must persist the enabled authority");
            assertExact(helper, SlabSupport.minResolvedDy(),
                    PlacementDepthPolicy.MIN_TARGETABLE_DY,
                    "confirmation must arm the deep resolver before returning");
            helper.assertTrue(dispatcher.execute("slabbed deep-mode enable", owner) == 1,
                    "requesting enable again must report the idempotent enabled state");
            helper.assertTrue(commandIsRejected(dispatcher,
                            "slabbed deep-mode disable", owner),
                    "the one-way contract must not expose a disable command");
            helper.assertTrue(DeepDyConsentAttachment.state(server.overworld()) == State.ENABLED,
                    "a rejected disable attempt must leave authority unchanged");
        } catch (CommandSyntaxException exception) {
            throw new AssertionError("registered deep-mode command was not executable", exception);
        } finally {
            restore(server, original);
        }
        helper.succeed();
    }

    @GameTest(
            batch = "slabbed_deep_consent_cache",
            template = TEMPLATE)
    public void resolverCapIsCachedAndAllocationFree(GameTestHelper helper) {
        com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        helper.assertTrue(bean.isThreadAllocatedMemorySupported(),
                "the consent cache proof requires per-thread allocation measurement");
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }

        for (int index = 0; index < 200_000; index++) {
            SlabSupport.minResolvedDy();
        }
        long readsBefore = DeepDyConsentAttachment.authoritativeReads();
        long measuredThread = Thread.currentThread().getId();
        long bytesBefore = bean.getThreadAllocatedBytes(measuredThread);
        double sum = 0.0d;
        int calls = 1_000_000;
        for (int index = 0; index < calls; index++) {
            sum += SlabSupport.minResolvedDy();
        }
        long allocated = bean.getThreadAllocatedBytes(measuredThread) - bytesBefore;
        long readsAfter = DeepDyConsentAttachment.authoritativeReads();
        double bytesPerRead = (double) Math.max(0L, allocated) / calls;
        System.out.println("[CONSENT_PERF] calls=" + calls
                + " bytesPerRead=" + bytesPerRead
                + " authoritativeReads=" + readsBefore + "->" + readsAfter
                + " sum=" + sum);

        helper.assertTrue(sum != 0.0d, "the measured cache loop must not be optimized away");
        helper.assertTrue(readsAfter == readsBefore,
                "the resolver hot path must perform zero authoritative consent reads");
        helper.assertTrue(bytesPerRead < 1.0d,
                "the cached cap read must allocate less than one byte per call");
        helper.succeed();
    }

    private static void prepareDeepFactlessSubject(
            GameTestHelper helper,
            ServerLevel level,
            BlockPos subject
    ) {
        BlockPos support = subject.below();
        level.setBlock(support, Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
        level.setBlock(subject, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        // Derived, never a literal: a factless subject on a BOTTOM slab seats half a step
        // below its support, so the support must sit exactly half a step above the floor for
        // the subject to land ON the floor. A literal here would silently stop measuring the
        // boundary the moment the floor moved.
        int supportHalfSteps = (int) Math.round(
                (PlacementDepthPolicy.MIN_TARGETABLE_DY + 0.5d) / 0.5d);
        helper.assertTrue(SlabPlacementHeightAttachment.putHalfSteps(
                        level.getChunkAt(support), support, (byte) supportHalfSteps),
                "fixture must store the support half a step above the floor");
        helper.assertTrue(SlabPlacementHeightAttachment.storedHalfSteps(
                        level.getChunkAt(subject), subject).isEmpty(),
                "the deep subject itself must remain factless");
    }

    private static void clearSubject(ServerLevel level, BlockPos subject) {
        SlabPlacementHeightAttachment.remove(level.getChunkAt(subject.below()), subject.below());
        SlabPlacementHeightAttachment.remove(level.getChunkAt(subject), subject);
    }

    private static Map<ServerLevel, Stamp> snapshot(MinecraftServer server) {
        Map<ServerLevel, Stamp> stamps = new LinkedHashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            stamps.put(level, SlabbedTestAccess.consentStamp(level));
        }
        return stamps;
    }

    private static void setSaveStamp(MinecraftServer server, Stamp stamp) {
        for (ServerLevel level : server.getAllLevels()) {
            if (stamp == null) {
                SlabbedTestAccess.clearConsentStamp(level);
            } else {
                SlabbedTestAccess.putConsentStamp(level, Stamp.fromTag(stamp.serializedTag()));
            }
        }
    }

    private static void restore(MinecraftServer server, Map<ServerLevel, Stamp> original) {
        for (Map.Entry<ServerLevel, Stamp> entry : original.entrySet()) {
            if (entry.getValue() == null) {
                SlabbedTestAccess.clearConsentStamp(entry.getKey());
            } else {
                SlabbedTestAccess.putConsentStamp(entry.getKey(),
                        Stamp.fromTag(entry.getValue().serializedTag()));
            }
        }
        Stamp authority = original.get(server.overworld());
        SlabSupport.armDeepAlphabet(authority != null && authority.state() == State.ENABLED);
    }

    private static void assertExact(
            GameTestHelper helper,
            double actual,
            double expected,
            String message
    ) {
        helper.assertTrue(Math.abs(actual - expected) <= EPSILON,
                message + ": expected " + expected + ", got " + actual);
    }

    private static boolean commandIsRejected(
            CommandDispatcher<CommandSourceStack> dispatcher,
            String command,
            CommandSourceStack source
    ) {
        try {
            dispatcher.execute(command, source);
            return false;
        } catch (CommandSyntaxException expected) {
            return true;
        }
    }

    private static boolean containsTranslation(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents translated
                && translated.getKey().equals(key)) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (containsTranslation(sibling, key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsRunCommand(Component component, String command) {
        ClickEvent click = component.getStyle().getClickEvent();
        if (click != null
                && click.getAction() == ClickEvent.Action.RUN_COMMAND
                && click.getValue().equals(command)) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (containsRunCommand(sibling, command)) {
                return true;
            }
        }
        return false;
    }

    private static final class RecordingCommandSource implements CommandSource {
        private final List<Component> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message);
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

        void clear() {
            messages.clear();
        }

        Component last() {
            if (messages.isEmpty()) {
                throw new AssertionError("expected a command response");
            }
            return messages.get(messages.size() - 1);
        }
    }
}
