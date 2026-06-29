# Forge Client Runtime Triad Harness Audit

Status: audit/proof-route note only

Baseline:

```text
root: /Users/joolmac/CascadeProjects/Slabbed-phase19-integrate
branch: codex/forge-1.20.1-backport-from-neoforge-042-beta2
head at audit start: 6f05c9e8
tag at audit start: save/forge-1-20-1-ordinary-full-block-anchor-behavior
```

Book IV phase goal:

```text
Identify the narrow Forge-compatible client/runtime Visual Triad proof harness
path for the saved minecraft:stone ordinary full block anchored on a bottom
slab with dy=-0.5, without starting client harness implementation, live proof,
culling work, mixin migration, release work, or savepoint closure.
```

## Canonical Fixture

Legal state being protected:

```text
ordinary full block anchored on a bottom slab with dy=-0.5
```

Exact fixture for the first client/runtime triad proof family:

```text
object block: minecraft:stone
support block: minecraft:stone_slab[type=bottom]
support relation: support slab directly below object block
anchor truth: real Forge server/client/render truth from the savepointed item
placement path, not proof-only manual promotion
expected dy: -0.5
```

Primary layer for this audit:

```text
proof gap
```

## Current Saved Truth

The immediately prior Book IV savepoint proves only the exact Forge server
fixture:

```text
commit: 6f05c9e8f751fe9ac10ea893714dd723a580676a
tag: save/forge-1-20-1-ordinary-full-block-anchor-behavior
fixture: minecraft:stone over minecraft:stone_slab[type=bottom]
route: Forge item placement through ForgeHooks.onPlaceItemIntoWorld
server result: anchored=true, dy=-0.500000, supportDy=0.000000, overlap=0.000000
```

That savepoint does not prove client/runtime Visual Triad, outline, raycast,
live behavior, culling, broad gametest coverage, behavior parity, or release
readiness.

## Read-Only Harness Findings

- `build.gradle` now has a Forge `gametest` source set and `gameTestServer`
  run config for the one server fixture. It includes only
  `src/gametest/java/com/slabbed/test/ForgeOrdinaryFullBlockAnchorGameTest.java`
  in the active gametest compile surface.
- `ForgeOrdinaryFullBlockAnchorGameTest` is a server fixture proof. It creates
  the saved anchor truth through Forge item placement, then checks server
  `SlabAnchorAttachment` and `SlabSupport` dy. It does not launch a client or
  measure model, outline, or raycast surfaces.
- `src/gametest/resources/fabric.mod.json` is still Fabric gametest metadata.
  The legacy client proof classes still depend on Fabric client gametest APIs
  and Yarn names; they are donor obligations, not active Forge proofs.
- The active Forge client entrypoint is limited to
  `SlabbedClient.init(modEventBus)`, which currently initializes model loading.
  It does not register a default-off client runtime proof event, route flag,
  launcher, or log extractor.
- `src/client/java/com/slabbed/client/NeoForgeClientWorldProof.java` is the
  closest donor for this exact client proof family, including row fields for
  `modelDy`, `outlineDy`, `targetDy`, target owner, and client/server anchor
  truth. It is not Forge-runnable as-is: it imports NeoForge client events, is
  not included in the active Forge compile surface, is not wired by
  `SlabbedClient`, and bundles a culling summary that is out of scope for the
  first client triad harness.
- The current Forge branch has no compiled or runnable client/runtime harness
  that can create the saved fixture, wait for client mirror truth, force or
  observe model rendering, and capture model, outline, and raycast agreement
  from the same position/state/support source.

Conclusion:

```text
A Forge-compatible client/runtime Visual Triad proof harness does not already
exist for the saved minecraft:stone over bottom stone slab fixture. The next
executable route must scaffold or port that narrow client runtime harness
before any client triad proof can be run.
```

## First Executable Client Route

Route name:

```text
forge-1.20.1-ordinary-full-block-client-runtime-triad-harness-scaffold
```

Route type:

```text
client/runtime proof-harness scaffold, not behavior patching
```

Allowed purpose for that follow-up route:

```text
Create the smallest default-off Forge 1.20.1 client runtime proof route that
can reuse the savepointed server placement path for minecraft:stone over
minecraft:stone_slab[type=bottom], observe the synced client anchor truth, and
emit one row tying model/rendered dy, outline dy, raycast/target dy, and target
owner to the same anchored fixture.
```

Minimum source donors to inspect in that follow-up:

```text
build.gradle
src/main/java/com/slabbed/Slabbed.java
src/client/java/com/slabbed/client/SlabbedClient.java
src/client/java/com/slabbed/client/NeoForgeClientWorldProof.java
src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java
src/main/java/com/slabbed/util/SlabbedOffsetRaycast.java
src/gametest/java/com/slabbed/test/ForgeOrdinaryFullBlockAnchorGameTest.java
```

Expected harness scaffold work for the follow-up:

- Add a Forge client-runtime proof entrypoint or proof helper behind an explicit
  default-off system property.
- Port only the needed client event wiring from the donor to Forge 1.20.1 APIs.
- Keep the fixture scope to `minecraft:stone` over
  `minecraft:stone_slab[type=bottom]`.
- Reuse real server/client anchor truth from the savepointed placement path.
- Capture rendered-model evidence from the `OffsetBlockStateModel` route for
  the exact object position, not only a `ClientDy` proxy.
- Capture outline dy and raycast/target owner from the same client position,
  state, support source, and expected dy.
- Split or defer donor culling checks; culling and cull-face relocation are not
  part of the first client triad harness scaffold.

Expected first proof command family after the harness exists:

```text
./gradlew --no-daemon compileJava compileGametestJava
./gradlew --no-daemon runClient --console plain
```

The `runClient` invocation must include the new default-off proof flag and must
be reported as automated dev-client support evidence only, not live Minecraft
proof.

Stop conditions for the follow-up route:

- Stop if the route requires broad mixin migration before the harness can be
  scaffolded.
- Stop if model evidence can only be proxy `ClientDy` rather than an observed
  `OffsetBlockStateModel` render sample.
- Stop if outline or raycast proof requires behavior patching rather than
  exposing a RED.
- Stop if the route drifts into culling, cull-face relocation, live profile
  driving, behavior parity, release work, or extra fixture families.

## What This Does Not Prove

This audit does not prove:

- client/runtime triad behavior
- rendered model dy at runtime for this saved server fixture
- outline alignment
- raycast or targeting alignment
- culling or cull-face relocation
- full Visual Triad
- live Minecraft behavior
- behavior parity
- release readiness
