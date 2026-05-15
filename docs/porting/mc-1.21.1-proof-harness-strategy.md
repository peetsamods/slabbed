# MC 1.21.1 Proof Harness Strategy

## Decision

Chosen strategy: Option C - replace the unavailable Fabric client-gametest route with an explicit local dev runtime proof harness for MC 1.21.1, while preserving the server-compatible subset under the available 1.21.1 server gametest API.

Status: documented strategy only. No production gameplay code was patched in this slice. No gametest classes were deleted, disabled, or edited.

Release status: blocked. The port is not release-ready until the deferred client proof coverage has an explicit runnable replacement and the compile/proof gate is green.

## Dependency Evidence

Active dependency line:

- `minecraft_version=1.21.1`
- `yarn_mappings=1.21.1+build.3`
- `loader_version=0.17.3`
- `fabric_version=0.115.6+1.21.1`

Local evidence captured under `tmp/mc1211-proof-harness-strategy-f9014fb/`:

- `gametestCompileClasspath-dependencies-offline.txt` resolves `fabric-api:0.115.6+1.21.1`.
- The resolved active line includes `fabric-gametest-api-v1:2.0.5+6fc22b9919`.
- The resolved active line does not include `fabric-client-gametest-api-v1`.
- The cached `fabric-api-0.115.6+1.21.1.pom` lists `fabric-gametest-api-v1` and does not list `fabric-client-gametest-api-v1`.
- Cached `fabric-client-gametest-api-v1` jars exist, and they contain the old client symbols, but local POM evidence ties the cached versions to other Fabric API lines, not the active MC 1.21.1 line.

Decision consequence: Option A is not verified. Adding a cached client-gametest coordinate to the 1.21.1 port would be a guess, not a verified dependency correction.

## Harness Inventory

Evidence files:

- `tmp/mc1211-proof-harness-strategy-f9014fb/gametest-harness-inventory.txt`
- `tmp/mc1211-proof-harness-strategy-f9014fb/gametest-classification-signals.txt`
- `tmp/mc1211-proof-harness-strategy-f9014fb/client-only-signal-lines.txt`
- `tmp/mc1211-proof-harness-strategy-f9014fb/gametest-proof-classification.md`

Classification:

- 2 server-compatible proof classes:
  - `src/gametest/java/com/slabbed/test/ChainSurvivalReproTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabFixtureTest.java`
- 16 client proof classes implementing `FabricClientGameTest` and using `ClientGameTestContext`, `TestSingleplayerContext`, and sometimes `TestWorldSave`.
- 1 client helper:
  - `src/gametest/java/com/slabbed/client/runtime/SlabbedRetargetTestHooks.java`

The client proof suite contains singleplayer, crosshair, render, client-world sync, and reload semantics. These are proof semantics, not cosmetic harness details.

## Options

### Option A - Verified client gametest dependency correction

Rejected for this slice.

Why:

- No `fabric-client-gametest-api-v1` module is resolved by `fabric-api:0.115.6+1.21.1`.
- Local cached client-gametest jars exist, but they are not verified as compatible with the active 1.21.1 dependency line.
- The active Fabric API POM and offline Gradle report support server gametest only.

Allowed later only if a verified MC 1.21.1-compatible client gametest coordinate is found from local or primary metadata and compile proof confirms the API shape.

### Option B - Split proof harness

Partially safe, but incomplete by itself.

Safe part:

- Migrate the 2 server-compatible proof classes to the available 1.21.1 server gametest API in a later narrow implementation slice.
- Keep those proofs compiling under `fabric-gametest-api-v1:2.0.5+6fc22b9919`.

Unsafe part:

- Treating the 16 client proofs as server GameTest equivalents would weaken coverage.
- Deleting, commenting out, or unregistering client proofs just to pass compile would hide the proof gap.

### Option C - Local dev runtime proof harness

Chosen.

Required meaning:

- Preserve the client proof classes and markers as proof obligations.
- Replace the missing compile-time Fabric client-gametest API with a dev-gated runtime proof route that can launch the client, build or enter a controlled world, drive the same actions, and extract the same GREEN/RED markers.
- Keep the proof gap visible until this exists and passes.
- Do not claim release readiness from compile-only success.

Minimum next implementation shape:

- First slice: server-compatible gametest adaptation only, limited to the 2 server-compatible classes and gametest metadata/build wiring if required.
- Second slice: design the dev runtime proof launcher/log extraction path for the client-only proof obligations, preserving marker names and pass/fail semantics.
- Third slice: migrate one high-value client proof through the runtime harness, verify the same marker semantics, then repeat by bucket.

### Option D - Stop

Not chosen as final strategy, because a safe path exists. The safe path is not a compile shortcut; it is an explicit proof-harness replacement plan with release still blocked.

## Non-Changes

- No gameplay behavior changed.
- No `SlabSupport` change.
- No `ClientDy` change.
- No rescue, placement, collision, or survival change.
- No proof markers removed.
- No tests deleted or disabled.
- No release, changelog, or Modrinth files changed.

## Release Gate

The MC 1.21.1 port remains blocked until:

- `compileJava` passes.
- `compileClientJava` passes.
- server-compatible gametest proofs compile/run under the verified 1.21.1 server gametest API.
- client-only proof obligations have a verified client runtime proof route or a verified compatible Fabric client-gametest dependency.
- deferred proofs are documented as deferred and are not counted as green.

## 2026-05-15 server-compatible migration + deferred-client split (f9014fb)

- Chosen action: B (explicit deferred-client-proof source-set split + mechanical server-class migration).
- Server-compatible classes migrated to verified server API:
  - `src/gametest/java/com/slabbed/test/ChainSurvivalReproTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabFixtureTest.java`
- Mechanical migration details:
  - `GameTest` import changed to `net.minecraft.test.GameTest`.
  - `@GameTest(structure=...)` changed to `@GameTest(templateName=...)` using `fabric-gametest-api-v1:empty`.
- Deferred client proof classes (still in-tree, not deleted):
  - `src/gametest/java/com/slabbed/test/SlabbedLabBeta4AuthoredCompoundAnchorDepthClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabBsFbAdjacentPlacementProofClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabModelDySingleOwnerAuditClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabCompatibleLoweredSlabLanePredicateBoundaryClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabLoweredSidePlacementLiveReproClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabLoweredSideSlabPlacementClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabBeta4CompoundContractMatrixClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabSbMixedStackBreakClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabB2UpperHalfGhostWindowClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabBeta4CompoundLoweredFullBlockCollapseClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabRenderRegionLoweredCarrierBridgeClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabBeta4LiveShapeGoblinClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabVanillaIllegalSlabToSlabPlacementClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabUltraGoblin2StressClientGameTest.java`
  - `src/gametest/java/com/slabbed/test/SlabbedLabVerticalChainHitboxOwnershipClientGameTest.java`
- Deferred helper preserved:
  - `src/gametest/java/com/slabbed/client/runtime/SlabbedRetargetTestHooks.java`
- Source-set split policy:
  - `build.gradle` now explicitly compiles only the 2 server-compatible gametest classes in `sourceSets.gametest.java`.
  - This split is explicit and reversible, and is named/documented as deferred client proof harness treatment.
- Coverage statement:
  - `compileGametestJava` passing under this split does **not** mean client proof coverage is complete.
  - Client runtime proof route remains unimplemented in this line.
- Release gate status:
  - Release remains blocked on deferred client proof obligations.
