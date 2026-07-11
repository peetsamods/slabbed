package com.slabbed.test;

import com.slabbed.command.SlabRigHangingKernelArtifacts;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pure phase, transition, and collision-safe publication proofs for RIG-3B2A. */
public final class SlabRigHangingKernelArtifactsTest {

    private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID FOREIGN = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID CREATED = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final SlabRigHangingKernelArtifacts.Position STRUCTURE = pos(1, 2, 3);
    private static final SlabRigHangingKernelArtifacts.Position BACKING = pos(1, 3, 3);
    private static final SlabRigHangingKernelArtifacts.Position GUARD = pos(1, 4, 3);
    private static final SlabRigHangingKernelArtifacts.Position SECOND_STRUCTURE = pos(5, 2, 3);
    private static final SlabRigHangingKernelArtifacts.Position SECOND_BACKING = pos(5, 3, 3);
    private static final SlabRigHangingKernelArtifacts.Position SECOND_GUARD = pos(5, 4, 3);

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void kernelArtifactsValidateExactThreePhaseChain(GameTestHelper helper) {
        SlabRigHangingKernelArtifacts.PhaseManifest planned = planned();
        SlabRigHangingKernelArtifacts.PhaseManifest immediate = immediate(planned);
        SlabRigHangingKernelArtifacts.PhaseManifest cleared = cleared(immediate);
        SlabRigHangingKernelArtifacts.validateTransition(planned, immediate);
        SlabRigHangingKernelArtifacts.validateTransition(immediate, cleared);

        for (SlabRigHangingKernelArtifacts.PhaseManifest phase :
                List.of(planned, immediate, cleared)) {
            SlabRigHangingKernelArtifacts.validateSelf(phase);
            String tsv = SlabRigHangingKernelArtifacts.canonicalTsv(phase);
            if (!phase.artifactId().matches("[0-9a-f]{64}")
                    || !tsv.contains("proof_scope\tHEADLESS_TEST_KERNEL\n")
                    || !tsv.contains("player_proof\tABSENT\n")
                    || !tsv.contains("production_command\tABSENT\n")
                    || !tsv.contains("progress_eligible\tfalse\n")) {
                throw helper.assertionException("phase blurred B2A proof boundary: " + tsv);
            }
        }
        if (!immediate.ownership().ownedEntityUuids().equals(List.of(CREATED))
                || !cleared.clearResult().removedEntityUuids().equals(List.of(CREATED))) {
            throw helper.assertionException("phase chain lost exact created/removed UUID ownership");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void kernelArtifactsCanonicalizeOrderingWithoutIdentityDrift(GameTestHelper helper) {
        SlabRigHangingKernelArtifacts.PhaseManifest first = planned();
        SlabRigHangingKernelArtifacts.CasePlan original = first.page().cases().getFirst();
        SlabRigHangingKernelArtifacts.CasePlan reversed = new SlabRigHangingKernelArtifacts.CasePlan(
                original.executionIndex(), original.executionCaseId(), original.rig3aCaseId(),
                original.routeId(), original.topologyId(), original.inputModeId(), original.tileBase(),
                original.clicked(), original.clickedFace(), original.hitVector(), original.attachmentPos(),
                reversed(original.plannedStructureCells()), reversed(original.plannedBackingCells()),
                reversed(original.reservedCells()), original.effectBox());
        SlabRigHangingKernelArtifacts.PagePlan reversedPage =
                new SlabRigHangingKernelArtifacts.PagePlan(first.page().pageIdentity(), 1, 4,
                        first.page().routeId(), first.page().base(), first.page().facing(),
                        List.of(reversed));
        SlabRigHangingKernelArtifacts.PhaseManifest second =
                SlabRigHangingKernelArtifacts.planned(first.run(), reversedPage);
        if (!first.artifactId().equals(second.artifactId())
                || !SlabRigHangingKernelArtifacts.canonicalTsv(first)
                .equals(SlabRigHangingKernelArtifacts.canonicalTsv(second))) {
            throw helper.assertionException("input ordering changed canonical phase identity");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void kernelArtifactsBindComponentAndSurvivalEvidenceIntoIdentity(GameTestHelper helper) {
        SlabRigHangingKernelArtifacts.PhaseManifest planned = planned();
        SlabRigHangingKernelArtifacts.PhaseManifest baseline = immediate(planned);
        SlabRigHangingKernelArtifacts.PhaseManifest componentChanged =
                SlabRigHangingKernelArtifacts.immediate(planned, List.of(observation(
                        exactOwnership(),
                        List.of(entity(FOREIGN, "minecraft:aztec", true), entity(CREATED)),
                        List.of(CREATED), List.of())));
        SlabRigHangingKernelArtifacts.PhaseManifest survivalChanged =
                SlabRigHangingKernelArtifacts.immediate(planned, List.of(observation(
                        exactOwnership(),
                        List.of(entity(FOREIGN, "minecraft:kebab", false), entity(CREATED)),
                        List.of(CREATED), List.of())));
        if (baseline.artifactId().equals(componentChanged.artifactId())
                || baseline.artifactId().equals(survivalChanged.artifactId())
                || componentChanged.artifactId().equals(survivalChanged.artifactId())) {
            throw helper.assertionException(
                    "component-holder or survives evidence disappeared from artifact identity");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void kernelArtifactsRejectTamperedPredecessorOwnershipAndClaims(GameTestHelper helper) {
        SlabRigHangingKernelArtifacts.PhaseManifest planned = planned();
        SlabRigHangingKernelArtifacts.PhaseManifest immediate = immediate(planned);

        SlabRigHangingKernelArtifacts.PhaseManifest wrongPredecessor =
                new SlabRigHangingKernelArtifacts.PhaseManifest(
                        immediate.phase(), immediate.artifactId(), immediate.run(), immediate.page(),
                        "0".repeat(64), immediate.proofScope(), immediate.playerProof(),
                        immediate.productionCommand(), immediate.progressEligible(), immediate.ownership(),
                        immediate.observations(), null);
        expectRefusal(helper, () -> SlabRigHangingKernelArtifacts.validateSelf(wrongPredecessor),
                "tampered predecessor");

        SlabRigHangingKernelArtifacts.PhaseManifest falseClaim =
                new SlabRigHangingKernelArtifacts.PhaseManifest(
                        planned.phase(), planned.artifactId(), planned.run(), planned.page(),
                        planned.predecessorArtifactId(), "PLAYER_LIVE", "PRESENT", "AVAILABLE",
                        true, planned.ownership(), planned.observations(), null);
        expectRefusal(helper, () -> SlabRigHangingKernelArtifacts.validateSelf(falseClaim),
                "false proof/progress claims");

        SlabRigHangingKernelArtifacts.Ownership guardOwnership =
                new SlabRigHangingKernelArtifacts.Ownership(List.of(GUARD), List.of(),
                        List.of(GUARD), List.of(CREATED));
        SlabRigHangingKernelArtifacts.CaseObservation escaped = observation(guardOwnership,
                List.of(entity(FOREIGN), entity(CREATED)), List.of(CREATED), List.of());
        expectRefusal(helper, () -> SlabRigHangingKernelArtifacts.immediate(planned, List.of(escaped)),
                "reserved-only guard promoted to ownership");

        SlabRigHangingKernelArtifacts.Ownership underclaimedOwnership =
                new SlabRigHangingKernelArtifacts.Ownership(List.of(STRUCTURE), List.of(),
                        List.of(STRUCTURE), List.of(CREATED));
        SlabRigHangingKernelArtifacts.CaseObservation underclaimed = observation(
                underclaimedOwnership, List.of(entity(FOREIGN), entity(CREATED)),
                List.of(CREATED), List.of());
        expectRefusal(helper, () -> SlabRigHangingKernelArtifacts.immediate(
                planned, List.of(underclaimed)), "omitted planned backing ownership");

        SlabRigHangingKernelArtifacts.CaseObservation mismatchedComponent = observation(
                exactOwnership(), List.of(entity(FOREIGN),
                        entity(CREATED, "minecraft:kebab", true)),
                List.of(CREATED), List.of());
        expectRefusal(helper, () -> SlabRigHangingKernelArtifacts.immediate(
                planned, List.of(mismatchedComponent)), "holder/component disagreement");

        SlabRigHangingKernelArtifacts.CaseObservation falseSurvival = observation(
                exactOwnership(), List.of(entity(FOREIGN),
                        entity(CREATED, "minecraft:pointer", false)),
                List.of(CREATED), List.of());
        expectRefusal(helper, () -> SlabRigHangingKernelArtifacts.immediate(
                planned, List.of(falseSurvival)), "false immediate survival evidence");

        SlabRigHangingKernelArtifacts.CasePlan firstPlan = planned.page().cases().getFirst();
        SlabRigHangingKernelArtifacts.CasePlan overlappingPlan =
                new SlabRigHangingKernelArtifacts.CasePlan(
                        firstPlan.executionIndex() + 1, "attempt:overlap", firstPlan.rig3aCaseId(),
                        firstPlan.routeId(), firstPlan.topologyId(), firstPlan.inputModeId(),
                        firstPlan.tileBase(), firstPlan.clicked(), firstPlan.clickedFace(),
                        firstPlan.hitVector(), firstPlan.attachmentPos(),
                        firstPlan.plannedStructureCells(), firstPlan.plannedBackingCells(),
                        firstPlan.reservedCells(), firstPlan.effectBox());
        expectRefusal(helper, () -> new SlabRigHangingKernelArtifacts.PagePlan(
                planned.page().pageIdentity(), planned.page().page(), planned.page().pageCount(),
                planned.page().routeId(), planned.page().base(), planned.page().facing(),
                List.of(firstPlan, overlappingPlan)), "cross-case reservation overlap");

        SlabRigHangingKernelArtifacts.CasePlan disjointPlan =
                new SlabRigHangingKernelArtifacts.CasePlan(
                        firstPlan.executionIndex() + 1, "attempt:second", firstPlan.rig3aCaseId(),
                        firstPlan.routeId(), firstPlan.topologyId(), firstPlan.inputModeId(),
                        pos(4, 0, 0), SECOND_BACKING, firstPlan.clickedFace(), firstPlan.hitVector(),
                        pos(4, 3, 3), List.of(SECOND_STRUCTURE), List.of(SECOND_BACKING),
                        List.of(SECOND_GUARD, SECOND_BACKING, SECOND_STRUCTURE),
                        firstPlan.effectBox());
        SlabRigHangingKernelArtifacts.PagePlan twoCasePage =
                new SlabRigHangingKernelArtifacts.PagePlan(
                        planned.page().pageIdentity(), planned.page().page(), planned.page().pageCount(),
                        planned.page().routeId(), planned.page().base(), planned.page().facing(),
                        List.of(firstPlan, disjointPlan));
        SlabRigHangingKernelArtifacts.PhaseManifest twoCasePlanned =
                SlabRigHangingKernelArtifacts.planned(planned.run(), twoCasePage);
        SlabRigHangingKernelArtifacts.Ownership secondOwnership =
                new SlabRigHangingKernelArtifacts.Ownership(
                        List.of(SECOND_STRUCTURE, SECOND_BACKING), List.of(),
                        List.of(SECOND_STRUCTURE, SECOND_BACKING), List.of(CREATED));
        SlabRigHangingKernelArtifacts.CaseObservation secondObservation = observation(
                "attempt:second", SECOND_BACKING, secondOwnership,
                List.of(entity(FOREIGN), entity(CREATED)), List.of(CREATED), List.of());
        expectRefusal(helper, () -> SlabRigHangingKernelArtifacts.immediate(twoCasePlanned,
                        List.of(observation(exactOwnership(),
                                List.of(entity(FOREIGN), entity(CREATED)),
                                List.of(CREATED), List.of()), secondObservation)),
                "one created UUID owned by two disjoint cases");

        SlabRigHangingKernelArtifacts.CaseObservation hiddenRemoval = observation(
                exactOwnership(), List.of(entity(CREATED)), List.of(CREATED), List.of());
        expectRefusal(helper, () -> SlabRigHangingKernelArtifacts.immediate(
                planned, List.of(hiddenRemoval)), "hidden removed preexisting UUID");

        SlabRigHangingKernelArtifacts.ClearResult incomplete =
                new SlabRigHangingKernelArtifacts.ClearResult(List.of(CREATED), List.of(), List.of(),
                        List.of(STRUCTURE, BACKING), List.of(STRUCTURE, BACKING), List.of(),
                        List.of(), List.of(), List.of());
        expectRefusal(helper, () -> SlabRigHangingKernelArtifacts.kernelCleared(immediate, incomplete),
                "incomplete UUID clear partition");

        SlabRigHangingKernelArtifacts.PhaseManifest reservedFinal =
                new SlabRigHangingKernelArtifacts.PhaseManifest(
                        SlabRigHangingKernelArtifacts.Phase.FINAL, "0".repeat(64), planned.run(),
                        planned.page(), planned.artifactId(), SlabRigHangingKernelArtifacts.PROOF_SCOPE,
                        SlabRigHangingKernelArtifacts.PLAYER_PROOF,
                        SlabRigHangingKernelArtifacts.PRODUCTION_COMMAND, false,
                        planned.ownership(), List.of(), null);
        expectRefusal(helper, () -> SlabRigHangingKernelArtifacts.validateSelf(reservedFinal),
                "B2B-reserved FINAL phase");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void kernelArtifactPublicationIsAtomicIdempotentAndCollisionSafe(GameTestHelper helper) {
        Path root = Path.of("build", "run", "gameTest", "rig3b2a-artifact-tests",
                UUID.randomUUID().toString()).toAbsolutePath();
        SlabRigHangingKernelArtifacts.PhaseManifest manifest = cleared(immediate(planned()));
        try {
            SlabRigHangingKernelArtifacts.WrittenArtifact first =
                    SlabRigHangingKernelArtifacts.write(root, manifest);
            byte[] expected = Files.readAllBytes(first.path());
            FileTime firstMtime = Files.getLastModifiedTime(first.path());
            SlabRigHangingKernelArtifacts.WrittenArtifact repeat =
                    SlabRigHangingKernelArtifacts.write(root, manifest);
            if (!first.equals(repeat) || !firstMtime.equals(Files.getLastModifiedTime(first.path()))
                    || expected.length != first.byteCount()
                    || !first.fileSha256().matches("[0-9a-f]{64}")) {
                throw helper.assertionException("identical publication rewrote or changed identity");
            }

            Files.writeString(first.path(), "truncated collision");
            byte[] collision = Files.readAllBytes(first.path());
            expectIoRefusal(helper, () -> SlabRigHangingKernelArtifacts.write(root, manifest),
                    "different-byte/truncated final collision");
            if (!java.util.Arrays.equals(collision, Files.readAllBytes(first.path()))) {
                throw helper.assertionException("collision refusal changed the foreign final bytes");
            }

            Path symlinkRoot = root.resolveSibling(root.getFileName() + "-symlink");
            Files.createDirectories(symlinkRoot);
            Path phaseDirectory = symlinkRoot.resolve("hanging-page-artifacts").resolve("planned");
            Files.createDirectories(phaseDirectory.getParent());
            Files.createSymbolicLink(phaseDirectory, root);
            expectIoRefusal(helper, () -> SlabRigHangingKernelArtifacts.write(symlinkRoot, planned()),
                    "symlinked phase directory");

            try (var paths = Files.walk(root)) {
                if (paths.anyMatch(path -> path.getFileName().toString().contains(".tmp-"))) {
                    throw helper.assertionException("publisher left an owned temporary behind");
                }
            }
        } catch (IOException e) {
            throw helper.assertionException("artifact publication setup failed: " + e);
        }
        helper.succeed();
    }

    private static SlabRigHangingKernelArtifacts.PhaseManifest planned() {
        SlabRigHangingKernelArtifacts.RunIdentity run =
                new SlabRigHangingKernelArtifacts.RunIdentity(
                        hex('1'), "abcdef0", hex('2'), "26.2", hex('3'), hex('4'), hex('5'),
                        "gametest:disposable", "minecraft:overworld", PLAYER, true);
        SlabRigHangingKernelArtifacts.CasePlan plan =
                new SlabRigHangingKernelArtifacts.CasePlan(
                        42L, "attempt:test", "case:test", "route:test", "stack:SBSBS",
                        "selector:test", pos(0, 0, 0), BACKING, "west", "0x1.0p-1",
                        pos(0, 3, 3), List.of(STRUCTURE), List.of(BACKING),
                        List.of(GUARD, BACKING, STRUCTURE),
                        SlabRigHangingKernelArtifacts.BoxBits.of(0, 0, 0, 1, 1, 1));
        SlabRigHangingKernelArtifacts.PagePlan page =
                new SlabRigHangingKernelArtifacts.PagePlan(hex('1'), 1, 4, "route:test",
                        pos(0, 0, 0), "west", List.of(plan));
        return SlabRigHangingKernelArtifacts.planned(run, page);
    }

    private static SlabRigHangingKernelArtifacts.PhaseManifest immediate(
            SlabRigHangingKernelArtifacts.PhaseManifest planned) {
        return SlabRigHangingKernelArtifacts.immediate(planned,
                List.of(observation(exactOwnership(), List.of(entity(FOREIGN), entity(CREATED)),
                        List.of(CREATED), List.of())));
    }

    private static SlabRigHangingKernelArtifacts.PhaseManifest cleared(
            SlabRigHangingKernelArtifacts.PhaseManifest immediate) {
        SlabRigHangingKernelArtifacts.ClearResult clear =
                new SlabRigHangingKernelArtifacts.ClearResult(
                        List.of(CREATED), List.of(CREATED), List.of(),
                        List.of(STRUCTURE, BACKING), List.of(STRUCTURE, BACKING), List.of(),
                        List.of(), List.of(), List.of());
        return SlabRigHangingKernelArtifacts.kernelCleared(immediate, clear);
    }

    private static SlabRigHangingKernelArtifacts.Ownership exactOwnership() {
        return new SlabRigHangingKernelArtifacts.Ownership(
                List.of(STRUCTURE, BACKING), List.of(), List.of(STRUCTURE, BACKING),
                List.of(CREATED));
    }

    private static SlabRigHangingKernelArtifacts.CaseObservation observation(
            SlabRigHangingKernelArtifacts.Ownership ownership,
            List<SlabRigHangingKernelArtifacts.EntityObservation> immediateEntities,
            List<UUID> added, List<UUID> removed) {
        return observation("attempt:test", BACKING, ownership, immediateEntities, added, removed);
    }

    private static SlabRigHangingKernelArtifacts.CaseObservation observation(
            String executionCaseId, SlabRigHangingKernelArtifacts.Position backing,
            SlabRigHangingKernelArtifacts.Ownership ownership,
            List<SlabRigHangingKernelArtifacts.EntityObservation> immediateEntities,
            List<UUID> added, List<UUID> removed) {
        return new SlabRigHangingKernelArtifacts.CaseObservation(
                executionCaseId, List.of(cell(backing)), List.of(entity(FOREIGN)), "SUCCESS", true,
                "minecraft:painting[count=1]", "minecraft:painting[count=0]", added, removed,
                List.of(cell(backing)), immediateEntities, ownership,
                "PLACED_SURVIVES", "test-only immediate");
    }

    private static SlabRigHangingKernelArtifacts.EntityObservation entity(UUID uuid) {
        String variant = uuid.equals(CREATED) ? "minecraft:pointer" : "minecraft:kebab";
        return entity(uuid, variant, true);
    }

    private static SlabRigHangingKernelArtifacts.EntityObservation entity(
            UUID uuid, String componentVariant, boolean survives) {
        String variant = uuid.equals(CREATED) ? "minecraft:pointer" : "minecraft:kebab";
        return new SlabRigHangingKernelArtifacts.EntityObservation(uuid, "minecraft:painting",
                variant, componentVariant, pos(0, 3, 3), "west",
                SlabRigHangingKernelArtifacts.Vec3Bits.of(0.5, 0.5, 0.5),
                SlabRigHangingKernelArtifacts.BoxBits.of(0, 0, 0, 1, 1, 1),
                survives, true, false, hex('a'));
    }

    private static SlabRigHangingKernelArtifacts.CellObservation cell(
            SlabRigHangingKernelArtifacts.Position position) {
        return new SlabRigHangingKernelArtifacts.CellObservation(position, "minecraft:stone",
                "NONE", hex('b'), Double.doubleToRawLongBits(-1.0),
                Double.doubleToRawLongBits(-1.0), "anchored=true");
    }

    private static SlabRigHangingKernelArtifacts.Position pos(int x, int y, int z) {
        return new SlabRigHangingKernelArtifacts.Position(x, y, z);
    }

    private static String hex(char digit) {
        return String.valueOf(digit).repeat(64);
    }

    private static <T> List<T> reversed(List<T> input) {
        List<T> copy = new ArrayList<>(input);
        java.util.Collections.reverse(copy);
        return copy;
    }

    private static void expectRefusal(GameTestHelper helper, Runnable action, String label) {
        try {
            action.run();
            throw helper.assertionException("artifact validator accepted " + label);
        } catch (IllegalArgumentException expected) {
            // Expected fail-closed path.
        }
    }

    private static void expectIoRefusal(GameTestHelper helper, IoAction action, String label) {
        try {
            action.run();
            throw helper.assertionException("artifact publisher accepted " + label);
        } catch (IOException expected) {
            // Expected fail-closed path.
        }
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }
}
