# Forge Ordinary Full Block Proof Harness Audit

Status: audit/proof-route note only

Baseline:

```text
root: /Users/joolmac/CascadeProjects/Slabbed-phase19-integrate
branch: codex/forge-1.20.1-backport-from-neoforge-042-beta2
head at audit start: 496d967d
tag at audit start: save/forge-1-20-1-book-iv-first-proof-route
```

Book IV phase goal:

```text
Identify the narrow Forge-compatible proof harness path for the legal
minecraft:stone ordinary full block anchored on a bottom slab with dy=-0.5,
without starting Java behavior patching, live proof, release work, or savepoint
closure.
```

## Canonical Fixture

Legal state being protected:

```text
ordinary full block anchored on a bottom slab with dy=-0.5
```

Exact fixture for the first Forge proof family:

```text
object block: minecraft:stone
support block: minecraft:stone_slab[type=bottom]
support relation: support slab directly below object block
anchor truth: real Slabbed anchor/capability truth created by the same route a
real placement or authoring path would use, not proof-only manual promotion
expected dy: -0.5
```

Primary layer for this audit:

```text
proof gap
```

## Read-Only Harness Findings

- The current Forge branch has `compileJava`-oriented Book III source inclusion
  plus Forge client/server run configs. It does not define a Forge gametest
  source set, Forge gametest run task, or client proof harness route for Book IV.
- `build.gradle` includes only a deliberate subset of main/client sources for
  the Book III loader/API savepoint. Gametest sources and most legacy proof
  helpers are outside the compiled Forge surface.
- `src/gametest/resources/fabric.mod.json` is still Fabric gametest metadata,
  not a Forge proof descriptor.
- Existing client proof files still depend on Fabric client gametest APIs and
  Yarn-style names. They are obligation donors, not executable Forge proofs.
- Existing server proof files contain useful donor rows for this exact fixture,
  including `A_ordinary_bottom_slab_full_block` and
  `DIRECT_BOTTOM_SLAB_ANCHORED_FULL_BLOCK`, but their annotations are NeoForge
  imports and their template metadata is not yet Forge-ready.
- The local Forge 1.20.1 artifact does include a server gametest surface:
  `net.minecraftforge.gametest.GameTestHolder`,
  `net.minecraftforge.gametest.PrefixGameTestTemplate`,
  `net.minecraftforge.event.RegisterGameTestsEvent`, and
  `net.minecraftforge.gametest.GameTestMain`.

Conclusion:

```text
A Forge-compatible Slabbed Book IV proof harness does not already exist in this
branch. The next executable route must migrate or scaffold the narrow Forge
proof harness before any behavior proof can be run.
```

## First Executable Route

Route name:

```text
forge-1.20.1-ordinary-full-block-anchor-forge-server-gametest-harness-scaffold
```

Route type:

```text
proof-harness migration, not behavior patching
```

Allowed purpose for that follow-up route:

```text
Create the smallest Forge 1.20.1 server gametest path that can execute one
ordinary-full-block-on-bottom-slab fixture and assert server-side legal state,
anchor truth, and dy=-0.5 for minecraft:stone over
minecraft:stone_slab[type=bottom].
```

Minimum source donors to inspect in that follow-up:

```text
src/gametest/java/com/slabbed/test/SlabbedLabFixtureTest.java
src/main/java/com/slabbed/dev/SlabbedLabFixtures.java
build.gradle
src/gametest/resources/fabric.mod.json
```

Expected harness migration work for the follow-up:

- Add or expose a Forge-compatible gametest source/run path for the minimal
  server proof.
- Register the minimal proof class using Forge 1.20.1 gametest APIs, either
  through `RegisterGameTestsEvent` or an equivalent Forge-supported route.
- Replace NeoForge gametest imports with Forge imports only inside the minimal
  proof surface.
- Resolve the empty-template source for Forge before claiming the proof is
  runnable. The current Fabric template namespace is not a Forge proof surface.
- Keep behavior code untouched unless the migrated proof produces a specific
  red failure in a separately authorized implementation slice.

Expected first proof command family after the harness exists:

```text
./gradlew --no-daemon compileJava compileGametestJava
./gradlew --no-daemon <named Forge server gametest task> --console plain
```

The exact gametest task name is part of the harness scaffold route because this
branch does not currently define one.

Stop conditions for the follow-up route:

- Stop if the route requires broad mixin migration.
- Stop if the route requires client triad proof before the server harness exists.
- Stop if the fixture can only pass by manually granting anchor truth that live
  or normal authoring would not create.
- Stop if the route broadens beyond `minecraft:stone` over
  `minecraft:stone_slab[type=bottom]`.

## What This Does Not Prove

This audit does not prove:

- placement behavior
- survival behavior
- neighbor or reload behavior
- outline alignment
- raycast or targeting alignment
- culling or cull-face relocation
- full Visual Triad
- live Minecraft behavior
- behavior parity
- release readiness

## Next Proof Family After Server Harness

After the Forge server gametest harness exists and the exact fixture is green,
the next Book IV proof route should be a client/runtime triad harness for the
same fixture:

```text
modelDy == -0.5
outlineDy == -0.5
raycast/targetDy == -0.5
visible owner == anchored minecraft:stone fixture
```

That later route should preserve the Visual Triad law and still avoid live
proof unless targeting feel, ghosting, moving-up behavior, or a player-visible
contradiction appears.
