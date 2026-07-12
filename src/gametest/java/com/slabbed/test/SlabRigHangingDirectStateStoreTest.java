package com.slabbed.test;

import com.slabbed.command.SlabRigHangingDirectEvidence;
import com.slabbed.command.SlabRigHangingDirectState;
import com.slabbed.command.SlabRigHangingDirectStateStore;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Pure registered-style tests for the RIG-3B2B1 durable state seam.
 *
 * <p>The registered methods use no game-world mutation and run in the standard empty structure.
 */
public final class SlabRigHangingDirectStateStoreTest {

    private static final String NONE = SlabRigHangingDirectState.NO_VALUE;

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directStateCanonicalRoundTripAcceptsExactDevSentinel(GameTestHelper helper) {
        SlabRigHangingDirectState.State state = initial(sha("planned"), owner(1));
        String canonical = SlabRigHangingDirectState.canonicalTsv(state);
        SlabRigHangingDirectState.State parsed = SlabRigHangingDirectState.parse(canonical);
        if (!state.equals(parsed) || !"unknown".equals(parsed.run().buildGitSha())
                || !canonical.equals(SlabRigHangingDirectState.canonicalTsv(parsed))) {
            throw helper.assertionException("canonical state/dev provenance did not round-trip exactly");
        }
        expectRuntimeFailure(helper,
                () -> SlabRigHangingDirectState.parse(new byte[]{(byte) 0xc3, (byte) 0x28}),
                "malformed UTF-8 ledger bytes");
        String forgedGenesisBody = canonical
                .replaceFirst("state_hash\\t[0-9a-f]{64}\\n", "")
                .replace("phase\tPLANNED\n", "phase\tQUARANTINED\n");
        String forgedGenesis = forgedGenesisBody.replaceFirst("\\n", "\nstate_hash\t"
                + sha(forgedGenesisBody) + "\n");
        expectRuntimeFailure(helper, () -> SlabRigHangingDirectState.parse(forgedGenesis),
                "non-PLANNED sequence-zero genesis");
        SlabRigHangingDirectEvidence.CellEvidence cellEvidence = syntheticCell(
                new SlabRigHangingDirectState.Position(22, 64, -7));
        byte[] cellCanonical = SlabRigHangingDirectEvidence.cellIdentityCanonical(cellEvidence)
                .getBytes(StandardCharsets.UTF_8);
        String cellHash = SlabRigHangingDirectEvidence.cellIdentityFingerprint(cellEvidence);
        String attachmentHash =
                SlabRigHangingDirectEvidence.attachmentIdentityFingerprint(cellEvidence);
        SlabRigHangingDirectEvidence.verifyCellAndAttachmentArtifact(cellEvidence.pos(),
                cellHash, attachmentHash, cellCanonical);
        expectRuntimeFailure(helper, () -> SlabRigHangingDirectEvidence
                        .verifyCellAndAttachmentArtifact(cellEvidence.pos().east(), cellHash,
                                attachmentHash, cellCanonical),
                "full cell artifact cannot derive an attachment for the wrong position");
        expectRuntimeFailure(helper, () -> SlabRigHangingDirectEvidence
                        .verifyCellAndAttachmentArtifact(cellEvidence.pos(), cellHash,
                                sha("wrong attachment"), cellCanonical),
                "full cell artifact cannot derive the wrong attachment hash");
        byte[] malformedCell = new byte[]{(byte) 0xc3, (byte) 0x28};
        expectRuntimeFailure(helper, () -> SlabRigHangingDirectEvidence
                        .verifyCellAndAttachmentArtifact(cellEvidence.pos(),
                                SlabRigHangingDirectState.sha256(malformedCell), attachmentHash,
                                malformedCell),
                "full cell artifact requires strict UTF-8");
        SlabRigHangingDirectState.CaseState exactCase = state.cases().getFirst();
        expectRuntimeFailure(helper, () -> new SlabRigHangingDirectState.CaseState(
                        exactCase.ordinal(), exactCase.attemptId(), exactCase.selectorId(),
                        "not-a-hash\twith-a-column-break", exactCase.phase(), exactCase.outcome(),
                        exactCase.immediateObservationId()),
                "non-canonical case component fingerprint");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directStoreAppendsChainAndFreshProcessIgnoresOnlyTempRemainder(GameTestHelper helper) {
        Path root = null;
        try {
            root = Files.createTempDirectory("slabbed-direct-store-chain-");
            SlabRigHangingDirectStateStore store = new SlabRigHangingDirectStateStore(root);
            String planned = store.writeArtifact("planned-chain").hash();
            List<SlabRigHangingDirectStateStore.WrittenArtifact> batch = store.writeArtifacts(
                    List.of("batch-artifact-a", "batch-artifact-b", "batch-artifact-a"));
            if (batch.size() != 3 || !batch.get(0).hash().equals(batch.get(2).hash())
                    || batch.get(0).hash().equals(batch.get(1).hash())
                    || !new String(store.readArtifact(batch.get(1).hash()), StandardCharsets.UTF_8)
                    .equals("batch-artifact-b")) {
                throw helper.assertionException("batched artifact publication changed order/identity");
            }
            SlabRigHangingDirectState.State first = initial(planned, owner(2));
            store.append(null, first);
            SlabRigHangingDirectState.State authoring = fixtureAuthoring(first);
            store.append(first, authoring);

            Path ledger = store.ledgerPath(first.owner());
            Path temp = ledger.resolve("." + store.statePath(authoring).getFileName()
                    + ".tmp-00000000-0000-0000-0000-000000000002");
            Files.writeString(temp, "orphaned producer temp", StandardCharsets.UTF_8);

            // New object is the process-reconstruction proof; no prior State/Store cache is reused.
            SlabRigHangingDirectStateStore fresh = new SlabRigHangingDirectStateStore(root);
            SlabRigHangingDirectStateStore.Reconstruction rebuilt = fresh.reconstruct(first.owner());
            if (rebuilt.states().size() != 2 || rebuilt.ignoredTemporaryFiles().size() != 1
                    || !rebuilt.latestOrNull().stateHash().equals(authoring.stateHash())) {
                throw helper.assertionException("fresh-process ledger reconstruction lost chain/temp boundary");
            }
            SlabRigHangingDirectState.State quarantined = authoring.successor(
                    SlabRigHangingDirectState.Phase.QUARANTINED,
                    authoring.nextCaseOrdinal(), authoring.authoredCells(),
                    authoring.authoredAttachments(), authoring.cases(), authoring.entities(),
                    authoring.scheduler(), authoring.clear(), authoring.artifacts(),
                    "independent verifier appended-tail proof");
            store.append(authoring, quarantined);
            long beforeTailReuse = fresh.verifiedPrefixReuseCount();
            List<SlabRigHangingDirectStateStore.Reconstruction> extended = fresh.reconstructAll();
            if (extended.size() != 1 || !quarantined.equals(extended.getFirst().latestOrNull())
                    || fresh.verifiedPrefixReuseCount() != beforeTailReuse + 1) {
                throw helper.assertionException(
                        "independent reconstructAll did not verify cached prefix plus appended tail");
            }

            Path emptyPrefixRoot = root.resolve("empty-prefix-store");
            SlabRigHangingDirectStateStore writer =
                    new SlabRigHangingDirectStateStore(emptyPrefixRoot);
            SlabRigHangingDirectStateStore emptyPrefixVerifier =
                    new SlabRigHangingDirectStateStore(emptyPrefixRoot);
            SlabRigHangingDirectState.Owner emptyPrefixOwner = owner(33);
            if (!emptyPrefixVerifier.reconstruct(emptyPrefixOwner).isEmpty()) {
                throw helper.assertionException("empty-prefix verifier premise was not empty");
            }
            SlabRigHangingDirectState.State genesis = initial(
                    writer.writeArtifact("empty-prefix-genesis").hash(), emptyPrefixOwner);
            writer.append(null, genesis);
            long beforeGenesisExtension = emptyPrefixVerifier.verifiedPrefixReuseCount();
            List<SlabRigHangingDirectStateStore.Reconstruction> genesisExtension =
                    emptyPrefixVerifier.reconstructAll();
            if (genesisExtension.size() != 1
                    || !genesis.equals(genesisExtension.getFirst().latestOrNull())
                    || emptyPrefixVerifier.verifiedPrefixReuseCount()
                    != beforeGenesisExtension + 1) {
                throw helper.assertionException(
                        "empty cached prefix could not extend through an independently appended genesis");
            }

            Path world = Files.createDirectory(root.resolve("world"));
            String created = SlabRigHangingDirectStateStore.createWorldKey(world);
            if (!created.equals(SlabRigHangingDirectStateStore.readWorldKey(world))) {
                throw helper.assertionException("strict create/read world identity disagreed");
            }

            SlabRigHangingDirectStateStore malformedStore =
                    new SlabRigHangingDirectStateStore(root.resolve("malformed-store"));
            String malformedPlanned = malformedStore.writeArtifact(
                    "planned-malformed-unicode").hash();
            List<SlabRigHangingDirectState.Position> malformedCells = List.of(
                    new SlabRigHangingDirectState.Position(22, 64, 22));
            SlabRigHangingDirectState.State malformed = SlabRigHangingDirectState.State.initial(
                    owner(22), run(22), malformedCells, malformedCells, pendingCases(),
                    malformedPlanned, "unpaired-surrogate-\ud800");
            try {
                malformedStore.append(null, malformed);
                throw helper.assertionException(
                        "candidate that changes under UTF-8 encoding unexpectedly published");
            } catch (IOException expected) {
                if (!expected.getMessage().contains("semantic readback")) {
                    throw helper.assertionException(
                            "malformed candidate failed for the wrong reason: " + expected);
                }
            }
            if (Files.exists(malformedStore.statePath(malformed))
                    || malformedStore.reconstruct(malformed.owner()).latestOrNull() != null) {
                throw helper.assertionException(
                        "semantic readback failure published an authoritative sequence");
            }
        } catch (IOException failure) {
            throw helper.assertionException("direct store chain proof failed: " + failure);
        } finally {
            deleteTree(root);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directStoreSuccessfulExistingPublicationRetriesRepairDirectoryDurability(
            GameTestHelper helper) {
        Path root = null;
        try {
            root = Files.createTempDirectory("slabbed-direct-store-retry-sync-");
            SlabRigHangingDirectStateStore store = new SlabRigHangingDirectStateStore(root);

            SlabRigHangingDirectStateStore.WrittenArtifact single =
                    store.writeArtifact("retry-single");
            long afterSingleCreate = store.directorySyncCount();
            store.writeArtifact("retry-single");
            if (store.directorySyncCount() != afterSingleCreate + 1) {
                throw helper.assertionException(
                        "existing single-artifact retry returned without repairing directory durability");
            }

            store.writeArtifacts(List.of("retry-batch-a", "retry-batch-b", "retry-batch-a"));
            long afterBatchCreate = store.directorySyncCount();
            store.writeArtifacts(List.of("retry-batch-a", "retry-batch-b", "retry-batch-a"));
            if (store.directorySyncCount() != afterBatchCreate + 1) {
                throw helper.assertionException(
                        "all-existing artifact-batch retry returned without repairing directory durability");
            }

            SlabRigHangingDirectState.State state = initial(single.hash(), owner(30));
            store.append(null, state);
            long afterStateCreate = store.directorySyncCount();
            store.append(null, state);
            if (store.directorySyncCount() != afterStateCreate + 1) {
                throw helper.assertionException(
                        "existing state retry returned without repairing ledger directory durability");
            }
        } catch (IOException failure) {
            throw helper.assertionException("existing-publication retry proof failed: " + failure);
        } finally {
            deleteTree(root);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directStoreCollisionTamperSymlinkAndVerifiedPrefixFailClosed(GameTestHelper helper) {
        Path root = null;
        Path symlinkRoot = null;
        try {
            root = Files.createTempDirectory("slabbed-direct-store-corrupt-");
            SlabRigHangingDirectStateStore store = new SlabRigHangingDirectStateStore(root);
            byte[] intended = "artifact-collision".getBytes(StandardCharsets.UTF_8);
            String intendedHash = SlabRigHangingDirectState.sha256(intended);
            Files.createDirectories(root.resolve("artifacts"));
            Files.writeString(store.artifactPath(intendedHash), "wrong bytes", StandardCharsets.UTF_8);
            expectIoFailure(helper, () -> store.writeArtifact(intended), "artifact collision");
            Files.delete(store.artifactPath(intendedHash));

            String planned = store.writeArtifact("planned-corrupt").hash();
            SlabRigHangingDirectState.State first = initial(planned, owner(3));
            SlabRigHangingDirectState.State second = fixtureAuthoring(first);
            store.append(null, first);
            store.append(first, second);
            Files.writeString(store.statePath(second), "tampered authoritative tail", StandardCharsets.UTF_8);
            try {
                new SlabRigHangingDirectStateStore(root).reconstruct(first.owner());
                throw helper.assertionException("tampered authoritative tail was accepted");
            } catch (SlabRigHangingDirectStateStore.CorruptLedgerException expected) {
                if (expected.verifiedPrefix().states().size() != 1
                        || !expected.verifiedPrefix().latestOrNull().equals(first)) {
                    throw helper.assertionException("corruption did not preserve exact verified prefix");
                }
            }

            SlabRigHangingDirectStateStore cachedStore =
                    new SlabRigHangingDirectStateStore(root.resolve("cached-artifact-tamper"));
            String cachedPlanned = cachedStore.writeArtifact("cached-planned").hash();
            SlabRigHangingDirectState.State cachedFirst = initial(cachedPlanned, owner(31));
            SlabRigHangingDirectState.State cachedSecond = fixtureAuthoring(cachedFirst);
            cachedStore.append(null, cachedFirst);
            cachedStore.append(cachedFirst, cachedSecond);
            SlabRigHangingDirectState.State cachedCandidate = cachedSecond.successor(
                    SlabRigHangingDirectState.Phase.QUARANTINED,
                    cachedSecond.nextCaseOrdinal(), cachedSecond.authoredCells(),
                    cachedSecond.authoredAttachments(), cachedSecond.cases(), cachedSecond.entities(),
                    cachedSecond.scheduler(), cachedSecond.clear(), cachedSecond.artifacts(),
                    "cached artifact tamper must block append");
            Files.delete(cachedStore.artifactPath(cachedPlanned));
            expectIoFailure(helper, () -> cachedStore.append(cachedSecond, cachedCandidate),
                    "cached linked-artifact deletion");
            if (Files.exists(cachedStore.statePath(cachedCandidate))) {
                throw helper.assertionException(
                        "cached linked-artifact deletion still published a new state");
            }

            symlinkRoot = Files.createTempDirectory("slabbed-direct-store-symlink-");
            Path outside = Files.createDirectory(symlinkRoot.resolve("outside"));
            Path escapedRoot = Files.createDirectory(symlinkRoot.resolve("store"));
            Files.createSymbolicLink(escapedRoot.resolve("artifacts"), outside);
            SlabRigHangingDirectStateStore escaped = new SlabRigHangingDirectStateStore(escapedRoot);
            expectIoFailure(helper, () -> escaped.writeArtifact("must-refuse"), "symlinked artifact directory");
        } catch (IOException failure) {
            throw helper.assertionException("collision/tamper/symlink setup failed: " + failure);
        } finally {
            deleteTree(root);
            deleteTree(symlinkRoot);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directStoreCacheRejectsSameSizeRestoredMtimeArtifactTamper(GameTestHelper helper) {
        Path root = null;
        try {
            root = Files.createTempDirectory("slabbed-direct-store-cache-ctime-");
            SlabRigHangingDirectStateStore store = new SlabRigHangingDirectStateStore(root);
            String planned = store.writeArtifact("cached-planned-same-size").hash();
            SlabRigHangingDirectState.State first = initial(planned, owner(32));
            SlabRigHangingDirectState.State second = fixtureAuthoring(first);
            store.append(null, first);
            store.append(first, second);

            long beforeReconstructAllReuse = store.verifiedPrefixReuseCount();
            List<SlabRigHangingDirectStateStore.Reconstruction> cachedAll = store.reconstructAll();
            if (cachedAll.size() != 1 || !second.equals(cachedAll.getFirst().latestOrNull())
                    || store.verifiedPrefixReuseCount() != beforeReconstructAllReuse + 1) {
                throw helper.assertionException(
                        "reconstructAll did not reuse the exact verified owner cache");
            }

            Path artifact = store.artifactPath(planned);
            BasicFileAttributes before = Files.readAttributes(artifact, BasicFileAttributes.class);
            byte[] tampered = Files.readAllBytes(artifact);
            tampered[tampered.length / 2] ^= 0x01;
            Files.write(artifact, tampered);
            Files.setLastModifiedTime(artifact, before.lastModifiedTime());
            BasicFileAttributes after = Files.readAttributes(artifact, BasicFileAttributes.class);
            if (!String.valueOf(before.fileKey()).equals(String.valueOf(after.fileKey()))
                    || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
                throw helper.assertionException(
                        "test filesystem could not preserve the legacy fileKey/size/mtime cache stamp");
            }

            SlabRigHangingDirectState.State candidate = second.successor(
                    SlabRigHangingDirectState.Phase.QUARANTINED,
                    second.nextCaseOrdinal(), second.authoredCells(), second.authoredAttachments(),
                    second.cases(), second.entities(), second.scheduler(), second.clear(),
                    second.artifacts(), "same-size restored-mtime tamper must block append");
            expectIoFailure(helper, store::reconstructAll,
                    "same-size restored-mtime cached reconstructAll tamper");
            expectIoFailure(helper, () -> store.append(second, candidate),
                    "same-size restored-mtime cached artifact tamper");
            if (Files.exists(store.statePath(candidate))) {
                throw helper.assertionException(
                        "same-size restored-mtime tamper still published a new state");
            }
        } catch (IOException failure) {
            throw helper.assertionException("same-size cache tamper proof failed: " + failure);
        } finally {
            deleteTree(root);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directStateRejectsReplayDuplicateOwnershipAndForeignClearAuthority(GameTestHelper helper) {
        SlabRigHangingDirectState.State ready = fixtureReady(initial(sha("planned-safety"), owner(4)));
        SlabRigHangingDirectState.State inFlight = beginCase(ready, 0);
        List<SlabRigHangingDirectState.CaseState> missingOwnership = new ArrayList<>(inFlight.cases());
        missingOwnership.set(0, missingOwnership.getFirst().immediate(
                SlabRigHangingDirectState.CaseOutcome.PLACED, sha("missing-ownership")));
        expectRuntimeFailure(helper, () -> inFlight.successor(
                        SlabRigHangingDirectState.Phase.IMMEDIATE_PARTIAL, 1,
                        inFlight.authoredCells(), inFlight.authoredAttachments(), missingOwnership,
                        inFlight.entities(), inFlight.scheduler(), inFlight.clear(),
                        inFlight.artifacts(), "must reject missing ownership"),
                "immediate case cannot omit its painting preclaim/load confirmation");
        List<SlabRigHangingDirectState.CaseState> lawfulRefusal = new ArrayList<>(inFlight.cases());
        lawfulRefusal.set(0, lawfulRefusal.getFirst().immediate(
                SlabRigHangingDirectState.CaseOutcome.VANILLA_REFUSAL,
                sha("vanilla-refusal-observation")));
        SlabRigHangingDirectState.State refused = inFlight.successor(
                SlabRigHangingDirectState.Phase.IMMEDIATE_PARTIAL, 1,
                inFlight.authoredCells(), inFlight.authoredAttachments(), lawfulRefusal,
                inFlight.entities(), inFlight.scheduler(), inFlight.clear(),
                inFlight.artifacts(), "lawful zero-UUID vanilla refusal");
        if (refused.cases().getFirst().outcome()
                != SlabRigHangingDirectState.CaseOutcome.VANILLA_REFUSAL) {
            throw helper.assertionException("lawful vanilla refusal outcome was not retained");
        }
        SlabRigHangingDirectState.EntityOwnership painting = paintingPreclaim(inFlight, 0);
        expectRuntimeFailure(helper, () -> new SlabRigHangingDirectState.EntityOwnership(
                        painting.uuid(), painting.role(), painting.expectedType(), painting.caseOrdinal(),
                        painting.attemptId(), painting.sourcePaintingUuid(),
                        SlabRigHangingDirectState.Acquisition.PRECLAIMED, painting.decision(),
                        SlabRigHangingDirectState.EntityDisposition.IN_WORLD, painting.fingerprint(),
                        painting.evidenceArtifact(), painting.position(), painting.aabb(),
                        painting.transferTargetUuid(), painting.transferDetail()),
                "painting preclaim cannot skip load confirmation");
        SlabRigHangingDirectState.State preclaimed = inFlight.withPreclaimedEntity(painting, "preclaim");
        expectRuntimeFailure(helper, () -> preclaimed.successor(
                        SlabRigHangingDirectState.Phase.IMMEDIATE_PARTIAL, 1,
                        preclaimed.authoredCells(), preclaimed.authoredAttachments(), missingOwnership,
                        preclaimed.entities(), preclaimed.scheduler(), preclaimed.clear(),
                        preclaimed.artifacts(), "must reject unconfirmed ownership"),
                "immediate case cannot retain a PREINSERTION painting");
        expectRuntimeFailure(helper,
                () -> preclaimed.withPreclaimedEntity(painting, "duplicate"),
                "duplicate entity UUID");

        SlabRigHangingDirectState.State quarantined = inFlight.successor(
                SlabRigHangingDirectState.Phase.QUARANTINED, 0,
                inFlight.authoredCells(), inFlight.authoredAttachments(), inFlight.cases(),
                inFlight.entities(), inFlight.scheduler(), inFlight.clear(), inFlight.artifacts(),
                "clear-only quarantine");
        expectRuntimeFailure(helper, () -> quarantined.withPreclaimedEntity(painting,
                        "foreign UUID adoption after reconstruction"),
                "quarantine cannot adopt a painting UUID");
        List<SlabRigHangingDirectState.CaseState> quarantinedReplay =
                new ArrayList<>(quarantined.cases());
        quarantinedReplay.set(0, quarantinedReplay.getFirst().immediate(
                SlabRigHangingDirectState.CaseOutcome.VANILLA_REFUSAL,
                sha("quarantined replay")));
        expectRuntimeFailure(helper, () -> quarantined.successor(
                        SlabRigHangingDirectState.Phase.QUARANTINED, 1,
                        quarantined.authoredCells(), quarantined.authoredAttachments(),
                        quarantinedReplay, quarantined.entities(), quarantined.scheduler(),
                        quarantined.clear(), quarantined.artifacts(), "forbidden replay"),
                "quarantine cannot advance a case");
        expectRuntimeFailure(helper, () -> quarantined.successor(
                        SlabRigHangingDirectState.Phase.CLEARING_ENTITIES, 1,
                        quarantined.authoredCells(), quarantined.authoredAttachments(),
                        quarantinedReplay, quarantined.entities(), quarantined.scheduler(),
                        SlabRigHangingDirectState.ClearProgress.begin(quarantined),
                        quarantined.artifacts(), "forbidden replay hidden in clear entry"),
                "quarantine clear entry cannot replay or advance a case");
        expectRuntimeFailure(helper, () -> inFlight.successor(
                        SlabRigHangingDirectState.Phase.CLEARING_ENTITIES, 1,
                        inFlight.authoredCells(), inFlight.authoredAttachments(),
                        quarantinedReplay, inFlight.entities(), inFlight.scheduler(),
                        SlabRigHangingDirectState.ClearProgress.begin(inFlight),
                        inFlight.artifacts(), "forbidden in-flight advancement hidden in clear entry"),
                "active clear entry cannot replay or advance a case");

        List<SlabRigHangingDirectState.CaseState> replayed = new ArrayList<>(preclaimed.cases());
        replayed.set(0, pendingCase(0));
        expectRuntimeFailure(helper, () -> preclaimed.successor(
                SlabRigHangingDirectState.Phase.CASE_IN_FLIGHT, 0,
                preclaimed.authoredCells(), preclaimed.authoredAttachments(), replayed,
                preclaimed.entities(), preclaimed.scheduler(), preclaimed.clear(),
                preclaimed.artifacts(), "replay"), "in-flight replay");

        UUID foreign = UUID.nameUUIDFromBytes("foreign-clear".getBytes(StandardCharsets.UTF_8));
        SlabRigHangingDirectState.ClearProgress bad = new SlabRigHangingDirectState.ClearProgress(true,
                List.of(painting.uuid(), foreign), 0, List.of(), List.of(), List.of(),
                preclaimed.authoredAttachments().stream()
                        .map(SlabRigHangingDirectState.AttachmentOwnership::pos).toList(),
                0, List.of(), List.of(), List.of(),
                List.of(new SlabRigHangingDirectState.Position(999, 99, 999)),
                0, List.of(), List.of(), List.of());
        expectRuntimeFailure(helper, () -> preclaimed.successor(
                SlabRigHangingDirectState.Phase.CLEARING_ENTITIES, 0,
                preclaimed.authoredCells(), preclaimed.authoredAttachments(), preclaimed.cases(),
                preclaimed.entities(), preclaimed.scheduler(), bad, preclaimed.artifacts(),
                "foreign clear"), "foreign clear authority");
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directFixtureAuthorityEmptyClearAndTopDownOrderAreExact(GameTestHelper helper) {
        SlabRigHangingDirectState.State initial = initial(sha("planned-fixture"), owner(5));
        SlabRigHangingDirectState.State authoring = fixtureAuthoring(initial);
        List<SlabRigHangingDirectState.CellOwnership> cells = confirmedCells(initial);
        SlabRigHangingDirectState.Position manufacturedPos = initial.plannedAuthoredCells().getFirst();
        SlabRigHangingDirectState.CellOwnership manufacturedCell =
                new SlabRigHangingDirectState.CellOwnership(manufacturedPos, sha("manufactured cell"));
        SlabRigHangingDirectState.AttachmentOwnership manufacturedAttachment =
                new SlabRigHangingDirectState.AttachmentOwnership(
                        manufacturedPos, sha("manufactured attachment"));
        expectRuntimeFailure(helper, () -> initial.successor(
                        SlabRigHangingDirectState.Phase.QUARANTINED, 0,
                        List.of(manufacturedCell), List.of(manufacturedAttachment), initial.cases(),
                        List.of(), initial.scheduler(), initial.clear(), initial.artifacts(),
                        "manufactured post-plan clear authority"),
                "non-authoring phase cannot promote a reservation into clear authority");
        SlabRigHangingDirectState.AttachmentOwnership foreignAttachment =
                new SlabRigHangingDirectState.AttachmentOwnership(
                        new SlabRigHangingDirectState.Position(100, 100, 100), sha("foreign attachment"));
        expectRuntimeFailure(helper, () -> authoring.successor(
                SlabRigHangingDirectState.Phase.FIXTURE_READY, 0, cells,
                List.of(foreignAttachment), authoring.cases(), List.of(),
                authoring.scheduler(), authoring.clear(), authoring.artifacts(), "foreign attachment"),
                "unplanned attachment");
        expectRuntimeFailure(helper, () -> authoring.successor(
                        SlabRigHangingDirectState.Phase.FIXTURE_READY, 0, cells,
                        List.of(), authoring.cases(), List.of(), authoring.scheduler(),
                        authoring.clear(), authoring.artifacts(), "missing attachments"),
                "confirmed cells cannot omit same-position attachment receipts");

        SlabRigHangingDirectState.ClearProgress empty = SlabRigHangingDirectState.ClearProgress.begin(initial);
        SlabRigHangingDirectState.State clearingEntities = initial.successor(
                SlabRigHangingDirectState.Phase.CLEARING_ENTITIES, 0, List.of(), List.of(),
                initial.cases(), List.of(), initial.scheduler(), empty, initial.artifacts(), "empty entities");
        List<SlabRigHangingDirectState.CaseState> clearReplay =
                new ArrayList<>(clearingEntities.cases());
        clearReplay.set(0, clearReplay.getFirst().inFlight());
        expectRuntimeFailure(helper, () -> clearingEntities.successor(
                        SlabRigHangingDirectState.Phase.CLEARING_ENTITIES, 0, List.of(), List.of(),
                        clearReplay, List.of(), clearingEntities.scheduler(), empty,
                        clearingEntities.artifacts(), "forbidden execution during clear"),
                "clear phase cannot mutate execution evidence");
        SlabRigHangingDirectState.State clearingAttachments = clearingEntities.successor(
                SlabRigHangingDirectState.Phase.CLEARING_ATTACHMENTS, 0, List.of(), List.of(),
                clearingEntities.cases(), List.of(), clearingEntities.scheduler(), empty,
                clearingEntities.artifacts(), "empty attachments");
        SlabRigHangingDirectState.State clearingCells = clearingAttachments.successor(
                SlabRigHangingDirectState.Phase.CLEARING_CELLS, 0, List.of(), List.of(),
                clearingAttachments.cases(), List.of(), clearingAttachments.scheduler(), empty,
                clearingAttachments.artifacts(), "empty cells");
        SlabRigHangingDirectState.ArtifactLinks clearedLinks = new SlabRigHangingDirectState.ArtifactLinks(
                initial.artifacts().planned(), NONE, NONE, sha("empty cleared"));
        SlabRigHangingDirectState.State cleared = clearingCells.successor(
                SlabRigHangingDirectState.Phase.CLEARED, 0, List.of(), List.of(),
                clearingCells.cases(), List.of(), clearingCells.scheduler(), empty,
                clearedLinks, "empty clear complete");
        if (cleared.phase() != SlabRigHangingDirectState.Phase.CLEARED) {
            throw helper.assertionException("zero-ownership PLANNED state was not safely retireable");
        }

        SlabRigHangingDirectState.State ready = fixtureReady(initial(sha("planned-order"), owner(6)));
        List<SlabRigHangingDirectState.Position> requested =
                SlabRigHangingDirectState.ClearProgress.begin(ready).requestedCells();
        if (requested.size() < 2 || requested.get(0).y() <= requested.get(1).y()) {
            throw helper.assertionException("confirmed cell clear order is not top-down");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directSchedulerResetsOnUnloadAndFinalStillCapturesCausalDrop(GameTestHelper helper) {
        SlabRigHangingDirectState.State immediate = completePage(
                fixtureReady(initial(sha("planned-delay"), owner(7))));
        List<SlabRigHangingDirectState.TickCredit> baseline = new ArrayList<>();
        List<UUID> paintings = immediate.entities().stream()
                .filter(entity -> entity.role() == SlabRigHangingDirectState.EntityRole.PAINTING)
                .map(SlabRigHangingDirectState.EntityOwnership::uuid).sorted().toList();
        for (int i = 0; i < paintings.size(); i++) {
            baseline.add(new SlabRigHangingDirectState.TickCredit(paintings.get(i), 0,
                    i != 0, 0, i == 0 ? -1 : 0));
        }
        SlabRigHangingDirectState.Scheduler armed = new SlabRigHangingDirectState.Scheduler(
                UUID.nameUUIDFromBytes("epoch-1".getBytes(StandardCharsets.UTF_8)).toString(),
                1, baseline);
        expectRuntimeFailure(helper, () -> new SlabRigHangingDirectState.TickCredit(
                        paintings.getFirst(), 0, true, 0, -1),
                "loaded painting cannot use unloaded -1 raw-tick sentinel");
        SlabRigHangingDirectState.State waiting = immediate.successor(
                SlabRigHangingDirectState.Phase.WAITING_DELAYED, 16,
                immediate.authoredCells(), immediate.authoredAttachments(), immediate.cases(),
                immediate.entities(), armed, immediate.clear(), immediate.artifacts(), "armed after reconstruction");

        List<SlabRigHangingDirectState.TickCredit> reloaded = new ArrayList<>(baseline);
        reloaded.set(0, new SlabRigHangingDirectState.TickCredit(paintings.get(0), 0, true, 0, 0));
        SlabRigHangingDirectState.State loaded = waiting.successor(
                SlabRigHangingDirectState.Phase.WAITING_DELAYED, 16,
                waiting.authoredCells(), waiting.authoredAttachments(), waiting.cases(), waiting.entities(),
                new SlabRigHangingDirectState.Scheduler(armed.processEpoch(), 1, reloaded),
                waiting.clear(), waiting.artifacts(), "reload baseline");

        List<SlabRigHangingDirectState.TickCredit> forgedGlobalCredit = new ArrayList<>(reloaded);
        SlabRigHangingDirectState.TickCredit unchangedRaw = forgedGlobalCredit.getFirst();
        forgedGlobalCredit.set(0, new SlabRigHangingDirectState.TickCredit(
                unchangedRaw.paintingUuid(), 102, true, unchangedRaw.unloadResets(),
                unchangedRaw.lastObservedEntityTick()));
        expectRuntimeFailure(helper, () -> loaded.successor(
                        SlabRigHangingDirectState.Phase.WAITING_DELAYED, 16,
                        loaded.authoredCells(), loaded.authoredAttachments(), loaded.cases(),
                        loaded.entities(), new SlabRigHangingDirectState.Scheduler(
                                armed.processEpoch(), 1, forgedGlobalCredit),
                        loaded.clear(), loaded.artifacts(), "forged global tick credit"),
                "entity credit cannot advance when raw tickCount is unchanged");

        List<SlabRigHangingDirectState.TickCredit> advanced = allCredits(reloaded, 10, true, 10);
        SlabRigHangingDirectState.State ten = loaded.successor(
                SlabRigHangingDirectState.Phase.WAITING_DELAYED, 16, loaded.authoredCells(),
                loaded.authoredAttachments(), loaded.cases(), loaded.entities(),
                new SlabRigHangingDirectState.Scheduler(armed.processEpoch(), 1, advanced),
                loaded.clear(), loaded.artifacts(), "ten entity ticks");
        List<SlabRigHangingDirectState.TickCredit> unloaded = new ArrayList<>(advanced);
        unloaded.set(0, new SlabRigHangingDirectState.TickCredit(paintings.get(0), 0, false, 1, -1));
        SlabRigHangingDirectState.State paused = ten.successor(
                SlabRigHangingDirectState.Phase.WAITING_DELAYED, 16, ten.authoredCells(),
                ten.authoredAttachments(), ten.cases(), ten.entities(),
                new SlabRigHangingDirectState.Scheduler(armed.processEpoch(), 1, unloaded),
                ten.clear(), ten.artifacts(), "unload reset");
        List<SlabRigHangingDirectState.TickCredit> duplicateReset = new ArrayList<>(unloaded);
        duplicateReset.set(0, new SlabRigHangingDirectState.TickCredit(
                paintings.get(0), 0, false, 2, -1));
        expectRuntimeFailure(helper, () -> paused.successor(
                        SlabRigHangingDirectState.Phase.WAITING_DELAYED, 16,
                        paused.authoredCells(), paused.authoredAttachments(), paused.cases(),
                        paused.entities(), new SlabRigHangingDirectState.Scheduler(
                                armed.processEpoch(), 1, duplicateReset),
                        paused.clear(), paused.artifacts(), "duplicate unload reset"),
                "already-paused credit cannot gain a duplicate unload reset");
        List<SlabRigHangingDirectState.TickCredit> twiceUnloaded = new ArrayList<>(unloaded);
        twiceUnloaded.set(1, new SlabRigHangingDirectState.TickCredit(
                paintings.get(1), 0, false, 1, -1));
        SlabRigHangingDirectState.State twoPaused = paused.successor(
                SlabRigHangingDirectState.Phase.WAITING_DELAYED, 16, paused.authoredCells(),
                paused.authoredAttachments(), paused.cases(), paused.entities(),
                new SlabRigHangingDirectState.Scheduler(armed.processEpoch(), 1, twiceUnloaded),
                paused.clear(), paused.artifacts(), "second unload preserves first paused row");
        if (twoPaused.scheduler().credits().stream().filter(credit -> !credit.loaded()).count() != 2) {
            throw helper.assertionException("sequential unload did not preserve two paused credits");
        }
        List<SlabRigHangingDirectState.TickCredit> restarted = new ArrayList<>(unloaded);
        restarted.set(0, new SlabRigHangingDirectState.TickCredit(paintings.get(0), 0, true, 1, 0));
        SlabRigHangingDirectState.State restartedState = paused.successor(
                SlabRigHangingDirectState.Phase.WAITING_DELAYED, 16, paused.authoredCells(),
                paused.authoredAttachments(), paused.cases(), paused.entities(),
                new SlabRigHangingDirectState.Scheduler(armed.processEpoch(), 1, restarted),
                paused.clear(), paused.artifacts(), "raw entity tick restarts at zero");

        List<SlabRigHangingDirectState.TickCredit> satisfied = allCredits(restarted, 102, true, 102);
        SlabRigHangingDirectState.ArtifactLinks finalLinks = new SlabRigHangingDirectState.ArtifactLinks(
                immediate.artifacts().planned(), immediate.artifacts().immediate(), sha("final"), NONE);
        SlabRigHangingDirectState.EntityOwnership unexplainedSource = restartedState.entities().stream()
                .filter(entity -> entity.uuid().equals(paintings.getFirst())).findFirst().orElseThrow()
                .removed(SlabRigHangingDirectState.RemovalCause.UNEXPLAINED,
                        sha("unexplained removal receipt"));
        expectRuntimeFailure(helper, () -> restartedState.successor(
                        SlabRigHangingDirectState.Phase.FINAL, 16, restartedState.authoredCells(),
                        restartedState.authoredAttachments(), restartedState.cases(),
                        replaceEntity(restartedState.entities(), unexplainedSource),
                        new SlabRigHangingDirectState.Scheduler(armed.processEpoch(), 1, satisfied),
                        restartedState.clear(), finalLinks, "unexplained removal false final"),
                "unexplained removal cannot bypass FINAL tick proof");
        SlabRigHangingDirectState.State finalState = restartedState.successor(
                SlabRigHangingDirectState.Phase.FINAL, 16, restartedState.authoredCells(),
                restartedState.authoredAttachments(), restartedState.cases(), restartedState.entities(),
                new SlabRigHangingDirectState.Scheduler(armed.processEpoch(), 1, satisfied),
                restartedState.clear(), finalLinks, "final");
        SlabRigHangingDirectState.EntityOwnership source = finalState.entities().stream()
                .filter(entity -> entity.role() == SlabRigHangingDirectState.EntityRole.PAINTING)
                .findFirst().orElseThrow();
        SlabRigHangingDirectState.EntityOwnership prematureDrop = dropPreclaim(finalState, source);
        expectRuntimeFailure(helper, () -> finalState.withPreclaimedEntity(prematureDrop,
                        "drop whose source remains in world"),
                "drop preclaim requires a causally removed source painting");
        SlabRigHangingDirectState.EntityOwnership removedSource = source.removed(
                SlabRigHangingDirectState.RemovalCause.SUPPORT_LOSS_DROP_EXPECTED,
                sha("support-loss removal receipt"));
        expectRuntimeFailure(helper, () -> finalState.successor(
                        SlabRigHangingDirectState.Phase.FINAL, 16, finalState.authoredCells(),
                        finalState.authoredAttachments(), finalState.cases(),
                        replaceEntity(finalState.entities(), removedSource), finalState.scheduler(),
                        finalState.clear(), finalState.artifacts(), "missing expected drop row"),
                "support-loss drop expectation cannot reach FINAL without its causal item row");
        SlabRigHangingDirectState.EntityOwnership noDropSource = source.removed(
                SlabRigHangingDirectState.RemovalCause.SUPPORT_LOSS_NO_DROP,
                sha("entity-drops-false removal receipt"));
        SlabRigHangingDirectState.State noDropFinal = finalState.successor(
                SlabRigHangingDirectState.Phase.FINAL, 16, finalState.authoredCells(),
                finalState.authoredAttachments(), finalState.cases(),
                replaceEntity(finalState.entities(), noDropSource), finalState.scheduler(),
                finalState.clear(), finalState.artifacts(), "ENTITY_DROPS=false no-item outcome");
        if (noDropFinal.phase() != SlabRigHangingDirectState.Phase.FINAL) {
            throw helper.assertionException("typed no-drop support loss did not remain FINAL");
        }
        SlabRigHangingDirectState.EntityOwnership drop = dropPreclaim(finalState, removedSource);
        List<SlabRigHangingDirectState.EntityOwnership> atomicRemoval =
                new ArrayList<>(replaceEntity(finalState.entities(), removedSource));
        atomicRemoval.add(drop);
        SlabRigHangingDirectState.State postFinalDrop = finalState.successor(
                SlabRigHangingDirectState.Phase.FINAL, 16, finalState.authoredCells(),
                finalState.authoredAttachments(), finalState.cases(), atomicRemoval,
                finalState.scheduler(), finalState.clear(), finalState.artifacts(),
                "atomic post-final removal plus claim-and-veto drop");
        if (postFinalDrop.phase() != SlabRigHangingDirectState.Phase.FINAL
                || postFinalDrop.entities().stream().noneMatch(entity -> entity.uuid().equals(drop.uuid()))) {
            throw helper.assertionException("post-FINAL causal drop escaped same-phase durable ownership");
        }
        if (postFinalDrop.activePaintingUuidSet().contains(removedSource.uuid())
                || postFinalDrop.activePaintingUuidSet().contains(drop.uuid())
                || postFinalDrop.activePaintingUuidSet().size()
                != postFinalDrop.entities().stream().filter(entity -> entity.role()
                == SlabRigHangingDirectState.EntityRole.PAINTING
                && entity.disposition()
                == SlabRigHangingDirectState.EntityDisposition.IN_WORLD).count()) {
            throw helper.assertionException(
                    "inactive removed/vetoed UUID still received live construction authority");
        }
        SlabRigHangingDirectState.State quarantinedFinal = finalState.successor(
                SlabRigHangingDirectState.Phase.QUARANTINED, 16, finalState.authoredCells(),
                finalState.authoredAttachments(), finalState.cases(), finalState.entities(),
                finalState.scheduler(), finalState.clear(), finalState.artifacts(),
                "clear-only quarantine before later exact drop");
        SlabRigHangingDirectState.State quarantinedDrop = quarantinedFinal.successor(
                SlabRigHangingDirectState.Phase.QUARANTINED, 16,
                quarantinedFinal.authoredCells(), quarantinedFinal.authoredAttachments(),
                quarantinedFinal.cases(), atomicRemoval, quarantinedFinal.scheduler(),
                quarantinedFinal.clear(), quarantinedFinal.artifacts(),
                "atomic post-quarantine removal plus claim-and-veto drop");
        if (quarantinedDrop.entities().stream()
                .noneMatch(entity -> entity.uuid().equals(drop.uuid()))) {
            throw helper.assertionException(
                    "post-QUARANTINE exact causal drop escaped durable ownership");
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directStoreReusesVerifiedCellProofsAcrossClearedToPlanned(GameTestHelper helper) {
        Path root = null;
        try {
            root = Files.createTempDirectory("slabbed-direct-store-reuse-");
            SlabRigHangingDirectStateStore store = new SlabRigHangingDirectStateStore(root);
            String firstPlannedArtifact = store.writeArtifact("planned-cache-run-one").hash();
            SlabRigHangingDirectState.State first = initial(firstPlannedArtifact, owner(41));
            store.append(null, first);
            SlabRigHangingDirectState.State authoring = fixtureAuthoring(first);
            store.append(first, authoring);
            for (SlabRigHangingDirectState.Position position : authoring.plannedAuthoredCells()) {
                store.writeArtifact(SlabRigHangingDirectEvidence.cellIdentityCanonical(
                        syntheticCell(position)));
            }
            SlabRigHangingDirectState.State ready = fixtureReadyFromAuthoring(authoring);
            store.append(authoring, ready);

            SlabRigHangingDirectState.ClearProgress started =
                    SlabRigHangingDirectState.ClearProgress.begin(ready);
            SlabRigHangingDirectState.State clearingEntities = ready.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_ENTITIES, ready.nextCaseOrdinal(),
                    ready.authoredCells(), ready.authoredAttachments(), ready.cases(),
                    ready.entities(), ready.scheduler(), started, ready.artifacts(),
                    "cache proof clear entities");
            store.append(ready, clearingEntities);
            SlabRigHangingDirectState.State clearingAttachments = clearingEntities.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_ATTACHMENTS,
                    clearingEntities.nextCaseOrdinal(), clearingEntities.authoredCells(),
                    clearingEntities.authoredAttachments(), clearingEntities.cases(),
                    clearingEntities.entities(), clearingEntities.scheduler(), started,
                    clearingEntities.artifacts(), "cache proof clear attachments");
            store.append(clearingEntities, clearingAttachments);

            SlabRigHangingDirectState.ClearProgress attachmentsDone =
                    new SlabRigHangingDirectState.ClearProgress(true,
                            started.requestedEntities(), started.requestedEntities().size(),
                            List.of(), started.requestedEntities(), List.of(),
                            started.requestedAttachments(), started.requestedAttachments().size(),
                            started.requestedAttachments(), List.of(), List.of(),
                            started.requestedCells(), 0, List.of(), List.of(), List.of());
            SlabRigHangingDirectState.State attachmentReceipts = clearingAttachments.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_ATTACHMENTS,
                    clearingAttachments.nextCaseOrdinal(), clearingAttachments.authoredCells(),
                    clearingAttachments.authoredAttachments(), clearingAttachments.cases(),
                    clearingAttachments.entities(), clearingAttachments.scheduler(), attachmentsDone,
                    clearingAttachments.artifacts(), "cache proof attachments complete");
            store.append(clearingAttachments, attachmentReceipts);
            SlabRigHangingDirectState.State clearingCells = attachmentReceipts.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_CELLS,
                    attachmentReceipts.nextCaseOrdinal(), attachmentReceipts.authoredCells(),
                    attachmentReceipts.authoredAttachments(), attachmentReceipts.cases(),
                    attachmentReceipts.entities(), attachmentReceipts.scheduler(), attachmentsDone,
                    attachmentReceipts.artifacts(), "cache proof clear cells");
            store.append(attachmentReceipts, clearingCells);

            SlabRigHangingDirectState.ClearProgress allDone =
                    new SlabRigHangingDirectState.ClearProgress(true,
                            started.requestedEntities(), started.requestedEntities().size(),
                            List.of(), started.requestedEntities(), List.of(),
                            started.requestedAttachments(), started.requestedAttachments().size(),
                            started.requestedAttachments(), List.of(), List.of(),
                            started.requestedCells(), started.requestedCells().size(),
                            started.requestedCells(), List.of(), List.of());
            SlabRigHangingDirectState.State cellReceipts = clearingCells.successor(
                    SlabRigHangingDirectState.Phase.CLEARING_CELLS,
                    clearingCells.nextCaseOrdinal(), clearingCells.authoredCells(),
                    clearingCells.authoredAttachments(), clearingCells.cases(),
                    clearingCells.entities(), clearingCells.scheduler(), allDone,
                    clearingCells.artifacts(), "cache proof cells complete");
            store.append(clearingCells, cellReceipts);
            String clearedArtifact = store.writeArtifact("cleared-cache-run-one").hash();
            SlabRigHangingDirectState.ArtifactLinks clearedLinks =
                    new SlabRigHangingDirectState.ArtifactLinks(
                            cellReceipts.artifacts().planned(), cellReceipts.artifacts().immediate(),
                            cellReceipts.artifacts().finalArtifact(), clearedArtifact);
            SlabRigHangingDirectState.State cleared = cellReceipts.successor(
                    SlabRigHangingDirectState.Phase.CLEARED, cellReceipts.nextCaseOrdinal(),
                    cellReceipts.authoredCells(), cellReceipts.authoredAttachments(),
                    cellReceipts.cases(), cellReceipts.entities(), cellReceipts.scheduler(),
                    allDone, clearedLinks, "cache proof cleared");
            store.append(cellReceipts, cleared);

            String secondPlannedArtifact = store.writeArtifact("planned-cache-run-two").hash();
            SlabRigHangingDirectState.State replanned = SlabRigHangingDirectState.State.afterCleared(
                    cleared, run(42), first.reservedCells(), first.plannedAuthoredCells(),
                    pendingCases(), secondPlannedArtifact, "cache proof replanned");
            long beforeReuse = store.verifiedPrefixReuseCount();
            store.append(cleared, replanned);
            SlabRigHangingDirectState.State secondAuthoring = fixtureAuthoring(replanned);
            store.append(replanned, secondAuthoring);
            SlabRigHangingDirectState.State secondReady =
                    fixtureReadyFromAuthoring(secondAuthoring);
            store.append(secondAuthoring, secondReady);
            long afterReuse = store.verifiedPrefixReuseCount();
            if (afterReuse - beforeReuse != 3L) {
                throw helper.assertionException(
                        "CLEARED->PLANNED same-position proof did not use three exact cache hits: "
                                + beforeReuse + " -> " + afterReuse);
            }
            SlabRigHangingDirectState.State rebuilt =
                    new SlabRigHangingDirectStateStore(root).reconstruct(first.owner()).latestOrNull();
            if (!secondReady.equals(rebuilt)) {
                throw helper.assertionException(
                        "fresh reconstruction disagreed after cached same-position proof reuse");
            }
        } catch (IOException failure) {
            throw helper.assertionException("CLEARED->PLANNED cache proof failed: " + failure);
        } finally {
            deleteTree(root);
        }
        helper.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty")
    public void directStoreRejectsCrossOwnerPageAndMissingObservationKeepsPrefix(GameTestHelper helper) {
        Path root = null;
        try {
            root = Files.createTempDirectory("slabbed-direct-store-global-");
            SlabRigHangingDirectStateStore store = new SlabRigHangingDirectStateStore(root);
            String planned = store.writeArtifact("planned-global").hash();
            SlabRigHangingDirectState.State firstOwner = initial(planned, owner(8));
            store.append(null, firstOwner);
            SlabRigHangingDirectState.State secondOwner = initial(planned, owner(9));
            expectIoFailure(helper, () -> store.append(null, secondOwner),
                    "cross-owner world/dimension allocation");

            SlabRigHangingDirectState.State authoring = fixtureAuthoring(firstOwner);
            store.append(firstOwner, authoring);
            SlabRigHangingDirectState.State ready = fixtureReadyFromAuthoring(authoring);
            expectIoFailure(helper, () -> store.append(authoring, ready),
                    "missing linked confirmed-cell receipt");
            for (SlabRigHangingDirectState.CellOwnership cell : ready.authoredCells()) {
                store.writeArtifact(SlabRigHangingDirectEvidence.cellIdentityCanonical(
                        syntheticCell(cell.pos())));
            }
            List<SlabRigHangingDirectState.CellOwnership> aliasedCells =
                    new ArrayList<>(ready.authoredCells());
            List<SlabRigHangingDirectState.AttachmentOwnership> aliasedAttachments =
                    new ArrayList<>(ready.authoredAttachments());
            aliasedCells.set(1, new SlabRigHangingDirectState.CellOwnership(
                    aliasedCells.get(1).pos(), aliasedCells.getFirst().fingerprint()));
            aliasedAttachments.set(1, new SlabRigHangingDirectState.AttachmentOwnership(
                    aliasedAttachments.get(1).pos(), aliasedAttachments.getFirst().fingerprint()));
            SlabRigHangingDirectState.State aliasedReady = authoring.successor(
                    SlabRigHangingDirectState.Phase.FIXTURE_READY, 0, aliasedCells,
                    aliasedAttachments, ready.cases(), ready.entities(), ready.scheduler(),
                    ready.clear(), ready.artifacts(), "copied cell proof must refuse");
            expectIoFailure(helper, () -> store.append(authoring, aliasedReady),
                    "cell/attachment hash copied to a second position");
            if (Files.exists(store.statePath(aliasedReady))) {
                throw helper.assertionException("aliased cell proof published a state");
            }

            List<SlabRigHangingDirectState.CellOwnership> genericHashCells =
                    new ArrayList<>(ready.authoredCells());
            genericHashCells.set(0, new SlabRigHangingDirectState.CellOwnership(
                    genericHashCells.getFirst().pos(), planned));
            SlabRigHangingDirectState.State genericHashReady = authoring.successor(
                    SlabRigHangingDirectState.Phase.FIXTURE_READY, 0, genericHashCells,
                    ready.authoredAttachments(), ready.cases(), ready.entities(), ready.scheduler(),
                    ready.clear(), ready.artifacts(), "generic artifact role reuse must refuse");
            expectIoFailure(helper, () -> store.append(authoring, genericHashReady),
                    "planned artifact hash reused as cell proof");
            if (Files.exists(store.statePath(genericHashReady))) {
                throw helper.assertionException("generic artifact role reuse published a state");
            }

            List<SlabRigHangingDirectState.AttachmentOwnership> wrongAttachments =
                    new ArrayList<>(ready.authoredAttachments());
            SlabRigHangingDirectState.AttachmentOwnership firstAttachment =
                    wrongAttachments.getFirst();
            wrongAttachments.set(0, new SlabRigHangingDirectState.AttachmentOwnership(
                    firstAttachment.pos(), sha("wrong derived attachment")));
            SlabRigHangingDirectState.State wrongReady = authoring.successor(
                    SlabRigHangingDirectState.Phase.FIXTURE_READY, 0,
                    ready.authoredCells(), wrongAttachments, ready.cases(), ready.entities(),
                    ready.scheduler(), ready.clear(), ready.artifacts(),
                    "wrong derived attachment must refuse");
            expectIoFailure(helper, () -> store.append(authoring, wrongReady),
                    "wrong derived attachment receipt");
            store.append(authoring, ready);
            SlabRigHangingDirectState.State inFlight = beginCase(ready, 0);
            store.append(ready, inFlight);
            SlabRigHangingDirectState.State preclaimed = inFlight.withPreclaimedEntity(
                    paintingPreclaim(inFlight, 0), "preclaimed");
            expectIoFailure(helper, () -> store.append(inFlight, preclaimed),
                    "missing linked full entity-evidence receipt");
            SlabRigHangingDirectState.EntityOwnership preclaimedPainting =
                    preclaimed.entities().getFirst();
            SlabRigHangingDirectStateStore.WrittenArtifact entityEvidence = store.writeArtifact(
                    "painting-evidence-" + preclaimedPainting.uuid());
            if (!entityEvidence.hash().equals(preclaimedPainting.evidenceArtifact())) {
                throw helper.assertionException("test entity-evidence hash disagreed with state link");
            }
            store.append(inFlight, preclaimed);
            SlabRigHangingDirectState.State confirmed = preclaimed.withConfirmedEntity(
                    preclaimed.entities().getFirst().uuid(), "confirmed");
            store.append(preclaimed, confirmed);

            SlabRigHangingDirectStateStore.WrittenArtifact observation =
                    store.writeArtifact("case-zero-observation");
            SlabRigHangingDirectState.State partial = completeCase(confirmed, 0, observation.hash());
            store.append(confirmed, partial);
            SlabRigHangingDirectState.EntityOwnership removed = partial.entities().getFirst()
                    .removed(SlabRigHangingDirectState.RemovalCause.SUPPORT_LOSS_DROP_EXPECTED,
                            sha("missing removal artifact"));
            SlabRigHangingDirectState.State removalQuarantine = partial.successor(
                    SlabRigHangingDirectState.Phase.QUARANTINED, 1,
                    partial.authoredCells(), partial.authoredAttachments(), partial.cases(),
                    replaceEntity(partial.entities(), removed), partial.scheduler(), partial.clear(),
                    partial.artifacts(), "removal receipt must exist");
            expectIoFailure(helper, () -> store.append(partial, removalQuarantine),
                    "missing linked painting-removal receipt");
            Files.delete(store.artifactPath(observation.hash()));
            try {
                new SlabRigHangingDirectStateStore(root).reconstruct(firstOwner.owner());
                throw helper.assertionException("missing linked case observation was accepted");
            } catch (SlabRigHangingDirectStateStore.CorruptLedgerException expected) {
                if (expected.verifiedPrefix().latestOrNull() == null
                        || !expected.verifiedPrefix().latestOrNull().stateHash()
                        .equals(confirmed.stateHash())) {
                    throw helper.assertionException("missing observation lost the exact verified prefix");
                }
            }
        } catch (IOException failure) {
            throw helper.assertionException("global/observation proof failed: " + failure);
        } finally {
            deleteTree(root);
        }
        helper.succeed();
    }

    private static SlabRigHangingDirectState.Owner owner(int id) {
        // Same exact world/dimension intentionally lets the global allocation test compare two players.
        return new SlabRigHangingDirectState.Owner(sha("world"), "minecraft:overworld",
                UUID.nameUUIDFromBytes(("player-" + id).getBytes(StandardCharsets.UTF_8)));
    }

    private static SlabRigHangingDirectState.RunIdentity run(int id) {
        return new SlabRigHangingDirectState.RunIdentity(sha("run-" + id),
                UUID.nameUUIDFromBytes(("nonce-" + id).getBytes(StandardCharsets.UTF_8)),
                "unknown", sha("runtime"), "26.2", sha("catalog"), sha("topologies"),
                sha("rig3b1"), sha("painting registry"), sha("universe"), sha("plan"),
                "painting-page-v1:sha256:" + sha("semantic"), 6143, 42, 1, true,
                new SlabRigHangingDirectState.Position(10 + id, 64, 10), "west");
    }

    private static SlabRigHangingDirectState.State initial(String planned,
                                                            SlabRigHangingDirectState.Owner owner) {
        List<SlabRigHangingDirectState.Position> plannedCells = List.of(
                new SlabRigHangingDirectState.Position(0, 64, 0),
                new SlabRigHangingDirectState.Position(0, 65, 0));
        List<SlabRigHangingDirectState.Position> reserved = List.of(
                plannedCells.get(0), plannedCells.get(1),
                new SlabRigHangingDirectState.Position(1, 65, 0));
        int id = Math.abs(owner.playerUuid().hashCode());
        return SlabRigHangingDirectState.State.initial(owner, run(id), reserved, plannedCells,
                pendingCases(), planned, "planned");
    }

    private static List<SlabRigHangingDirectState.CaseState> pendingCases() {
        List<SlabRigHangingDirectState.CaseState> result = new ArrayList<>();
        for (int ordinal = 0; ordinal < 16; ordinal++) {
            result.add(pendingCase(ordinal));
        }
        return List.copyOf(result);
    }

    private static SlabRigHangingDirectState.CaseState pendingCase(int ordinal) {
        return new SlabRigHangingDirectState.CaseState(ordinal, "attempt-" + ordinal,
                "selector-" + ordinal, sha("component-" + ordinal),
                SlabRigHangingDirectState.CasePhase.PENDING,
                SlabRigHangingDirectState.CaseOutcome.NONE, NONE);
    }

    private static SlabRigHangingDirectState.State fixtureAuthoring(
            SlabRigHangingDirectState.State state) {
        return state.successor(SlabRigHangingDirectState.Phase.FIXTURE_AUTHORING, 0,
                List.of(), List.of(), state.cases(), state.entities(), state.scheduler(), state.clear(),
                state.artifacts(), "fixture authoring");
    }

    private static SlabRigHangingDirectState.State fixtureReady(
            SlabRigHangingDirectState.State initial) {
        return fixtureReadyFromAuthoring(fixtureAuthoring(initial));
    }

    private static SlabRigHangingDirectState.State fixtureReadyFromAuthoring(
            SlabRigHangingDirectState.State authoring) {
        List<SlabRigHangingDirectState.CellOwnership> cells = confirmedCells(authoring);
        List<SlabRigHangingDirectState.AttachmentOwnership> attachments =
                authoring.plannedAuthoredCells().stream().map(pos ->
                        new SlabRigHangingDirectState.AttachmentOwnership(
                                pos, SlabRigHangingDirectEvidence.attachmentIdentityFingerprint(
                                syntheticCell(pos)))).toList();
        return authoring.successor(SlabRigHangingDirectState.Phase.FIXTURE_READY, 0,
                cells, attachments, authoring.cases(), authoring.entities(), authoring.scheduler(),
                authoring.clear(), authoring.artifacts(), "fixture ready");
    }

    private static List<SlabRigHangingDirectState.CellOwnership> confirmedCells(
            SlabRigHangingDirectState.State state) {
        return state.plannedAuthoredCells().stream().map(pos ->
                new SlabRigHangingDirectState.CellOwnership(pos,
                        SlabRigHangingDirectEvidence.cellIdentityFingerprint(
                                syntheticCell(pos)))).toList();
    }

    private static SlabRigHangingDirectEvidence.CellEvidence syntheticCell(
            SlabRigHangingDirectState.Position pos) {
        return new SlabRigHangingDirectEvidence.CellEvidence(pos.toBlockPos(),
                "Block{minecraft:stone}", "NONE", sha("NONE"),
                Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(Double.NaN),
                "anchored=false,frozen=false,compound=false,sideLower=false,sideUpper=false,"
                        + "sideDouble=false,ownerTop=false,persistentCarrier=false");
    }

    private static SlabRigHangingDirectState.State beginCase(
            SlabRigHangingDirectState.State state, int ordinal) {
        List<SlabRigHangingDirectState.CaseState> cases = new ArrayList<>(state.cases());
        cases.set(ordinal, cases.get(ordinal).inFlight());
        return state.successor(SlabRigHangingDirectState.Phase.CASE_IN_FLIGHT, ordinal,
                state.authoredCells(), state.authoredAttachments(), cases, state.entities(),
                state.scheduler(), state.clear(), state.artifacts(), "case " + ordinal + " in flight");
    }

    private static SlabRigHangingDirectState.State completeCase(
            SlabRigHangingDirectState.State state, int ordinal, String observationId) {
        List<SlabRigHangingDirectState.CaseState> cases = new ArrayList<>(state.cases());
        cases.set(ordinal, cases.get(ordinal).immediate(
                SlabRigHangingDirectState.CaseOutcome.PLACED, observationId));
        boolean last = ordinal == 15;
        SlabRigHangingDirectState.ArtifactLinks artifacts = last
                ? new SlabRigHangingDirectState.ArtifactLinks(state.artifacts().planned(),
                sha("immediate-page"), NONE, NONE)
                : state.artifacts();
        return state.successor(last ? SlabRigHangingDirectState.Phase.IMMEDIATE
                        : SlabRigHangingDirectState.Phase.IMMEDIATE_PARTIAL,
                ordinal + 1, state.authoredCells(), state.authoredAttachments(), cases,
                state.entities(), state.scheduler(), state.clear(), artifacts,
                "case " + ordinal + " immediate");
    }

    private static SlabRigHangingDirectState.State completePage(
            SlabRigHangingDirectState.State ready) {
        SlabRigHangingDirectState.State state = ready;
        for (int ordinal = 0; ordinal < 16; ordinal++) {
            state = beginCase(state, ordinal);
            SlabRigHangingDirectState.EntityOwnership preclaim = paintingPreclaim(state, ordinal);
            state = state.withPreclaimedEntity(preclaim, "painting preclaim");
            state = state.withConfirmedEntity(preclaim.uuid(), "painting loaded");
            state = completeCase(state, ordinal, sha("observation-" + ordinal));
        }
        return state;
    }

    private static SlabRigHangingDirectState.EntityOwnership paintingPreclaim(
            SlabRigHangingDirectState.State state, int ordinal) {
        UUID uuid = UUID.nameUUIDFromBytes((state.run().runId() + "|painting|" + ordinal)
                .getBytes(StandardCharsets.UTF_8));
        return new SlabRigHangingDirectState.EntityOwnership(uuid,
                SlabRigHangingDirectState.EntityRole.PAINTING, "minecraft:painting", ordinal,
                state.cases().get(ordinal).attemptId(), NONE,
                SlabRigHangingDirectState.Acquisition.PRECLAIMED,
                SlabRigHangingDirectState.PreclaimDecision.ALLOW_AND_CONFIRM,
                SlabRigHangingDirectState.EntityDisposition.PREINSERTION,
                sha("painting-fingerprint-" + uuid),
                sha("painting-evidence-" + uuid),
                new SlabRigHangingDirectState.Vec3Bits(0, 0, 0),
                new SlabRigHangingDirectState.BoxBits(0, 0, 0, 1, 1, 1),
                NONE, NONE);
    }

    private static SlabRigHangingDirectState.EntityOwnership dropPreclaim(
            SlabRigHangingDirectState.State state,
            SlabRigHangingDirectState.EntityOwnership source) {
        UUID uuid = UUID.nameUUIDFromBytes((state.run().runId() + "|drop|" + source.uuid())
                .getBytes(StandardCharsets.UTF_8));
        return new SlabRigHangingDirectState.EntityOwnership(uuid,
                SlabRigHangingDirectState.EntityRole.DROPPED_ITEM, "minecraft:item",
                source.caseOrdinal(), source.attemptId(), source.uuid().toString(),
                SlabRigHangingDirectState.Acquisition.DROP_PRECLAIM,
                SlabRigHangingDirectState.PreclaimDecision.CLAIM_AND_VETO,
                SlabRigHangingDirectState.EntityDisposition.VETOED_BEFORE_INSERTION,
                sha("drop-fingerprint-" + uuid), sha("drop-evidence-" + uuid),
                source.position(), source.aabb(), NONE,
                "vetoed before insertion; no pickup/merge/container transfer possible");
    }

    private static List<SlabRigHangingDirectState.TickCredit> allCredits(
            List<SlabRigHangingDirectState.TickCredit> source, int ticks, boolean loaded,
            long lastTick) {
        return source.stream().map(credit -> new SlabRigHangingDirectState.TickCredit(
                credit.paintingUuid(), ticks, loaded, credit.unloadResets(),
                lastTick)).toList();
    }

    private static List<SlabRigHangingDirectState.EntityOwnership> replaceEntity(
            List<SlabRigHangingDirectState.EntityOwnership> entities,
            SlabRigHangingDirectState.EntityOwnership replacement) {
        return entities.stream().map(entity -> entity.uuid().equals(replacement.uuid())
                ? replacement : entity).toList();
    }

    private static String sha(String value) {
        return SlabRigHangingDirectState.sha256(value);
    }

    private static void expectRuntimeFailure(GameTestHelper helper, Runnable action, String label) {
        try {
            action.run();
            throw helper.assertionException(label + " unexpectedly succeeded");
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // Expected fail-closed contract.
        }
    }

    private static void expectIoFailure(GameTestHelper helper, IoAction action, String label) {
        try {
            action.run();
            throw helper.assertionException(label + " unexpectedly succeeded");
        } catch (IOException expected) {
            // Expected fail-closed filesystem contract.
        }
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Test cleanup must not mask the assertion that already failed.
        }
    }
}
