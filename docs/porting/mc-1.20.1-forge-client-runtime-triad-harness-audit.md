# Forge Client Runtime Triad Harness Audit

Status: audit/proof-route note only, superseded for route execution

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

## Superseding Route Correction

Julia rejected the automatic/dev-client route after the audit savepoint. For
this Book IV Visual Triad/client-runtime lane:

```text
Do not use runClient, automatic dev-client output, or similar automatic/dev
runs as useful proof.
Do not savepoint or revive the abandoned automatic scaffold dirt.
Preserve any tmp evidence as abandoned/obsolete evidence until an explicit
cleanup or replacement route says otherwise.
```

The audit finding remains useful only as a negative finding: no acceptable
Forge-compatible proof route already exists. The first executable route must be
a real client/live-authority RED or proof capture for the same exact fixture,
not another automatic client scaffold.

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
A Forge-compatible and accepted client/runtime Visual Triad proof route does
not already exist for the saved minecraft:stone over bottom stone slab fixture.
The automatic/dev-client scaffold path is abandoned and must not be used as
Book IV proof. The next executable route must capture the real client/live
authority triad RED or proof for the exact fixture before any triad patching.
```

## First Executable Client Route After Correction

Route name:

```text
forge-1.20.1-ordinary-full-block-live-client-triad-red-capture
```

Route type:

```text
real client/live-authority triad RED/proof capture, not behavior patching
```

Allowed purpose for that follow-up route:

```text
Use the exact real Minecraft/Modrinth Forge 1.20.1 profile, jar, world, and
fixture action to capture the Visual Triad evidence row for minecraft:stone over
minecraft:stone_slab[type=bottom]: rendered model dy, outline dy, raycast/target
dy, vanilla target, final target, held item, face, hit vector, expected owner,
and actual owner. The route may produce a RED; that RED names the next single
triad layer.
```

Minimum local inputs to inspect in that follow-up:

```text
AGENTS.md
RULES.md
SLABBED_SPINE.md
HANDOFF.md
docs/codex/03-visual-triad.md
docs/porting/mc-1.20.1-forge-regression-risk-checklist.md
docs/porting/mc-1.20.1-forge-client-runtime-triad-harness-audit.md
the exact staged/installed Forge 1.20.1 jar/profile/world evidence for the
live-client route
```

Expected live/client capture work for the follow-up:

- Keep the fixture scope to `minecraft:stone` over
  `minecraft:stone_slab[type=bottom]`.
- Reuse real server/client anchor truth from the savepointed placement path.
- Capture or extract rendered-model evidence from the visible/runtime renderer,
  not only a `ClientDy` proxy.
- Capture outline dy and raycast/target owner from the same client position,
  state, support source, held item, face, and hit vector.
- Split or defer culling checks; culling and cull-face relocation are not part
  of the first client triad RED capture.

Forbidden proof substitutions:

```text
./gradlew --no-daemon runClient --console plain
automatic dev-client proof flags
runServer / runGameTest / runServerGameTest / runClientGameTest as live proof
```

Stop conditions for the follow-up route:

- Stop if exact real profile/jar/world identity cannot be established.
- Stop if model evidence can only be proxy `ClientDy` rather than real rendered
  evidence.
- Stop if outline or raycast proof requires behavior patching rather than first
  exposing a RED.
- Stop if the route drifts into culling, cull-face relocation, live profile
  staging/release, behavior parity, release work, or extra fixture families.

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
