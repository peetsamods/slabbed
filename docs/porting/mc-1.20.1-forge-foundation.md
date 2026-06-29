# MC 1.20.1 Forge Foundation

## Authority

This is the branch foundation document for the first Slabbed Forge project.
It summarizes the port plan; it does not override `AGENTS.md`, `SLABBED_SPINE.md`,
or the Slabbed law docs under `docs/codex/`.

Current branch:

```text
codex/forge-1.20.1-backport-from-neoforge-042-beta2
```

Current root:

```text
/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate
```

Donor source:

```text
release/neoforge-1.21.1-0.4.2-beta.2
HEAD 2a3be274
mod_version 0.4.2-beta.2+1.21.1
```

Target:

```text
Minecraft 1.20.1
Forge
Slabbed Forge artifact/version naming: pending explicit version decision
```

## Scope

Allowed now:

- establish the branch and documentation foundation
- record donor truth
- define the Forge 1.20.1 toolchain questions
- map port risks before code moves

Forbidden now:

- Java/source edits
- Gradle or descriptor conversion
- gametest edits
- release/upload work
- live-profile staging
- behavior changes under the name of porting

## Toolchain Truth

Donor truth at `2a3be274`:

- Minecraft: `1.21.1`
- Loader family: NeoForge
- Build plugin: `net.neoforged.moddev` `2.0.141`
- NeoForge: `21.1.233`
- Java: toolchain and compile release `21`
- Descriptor: `src/main/resources/META-INF/neoforge.mods.toml`
- Compatibility bridge: `org.sinytra.forgified-fabric-api:0.116.7+2.2.4+1.21.1`

Locked Forge 1.20.1 target truth:

- Minecraft: `1.20.1`
- Forge: `47.4.20`
- ForgeGradle: `6.0.54` as the exact reproducible pin; official MDK uses the range `[6.0,6.2)`
- Gradle wrapper: `8.8`
- Java: `17`
- mappings: Mojang official, channel `official`, version `1.20.1`
- dependency coordinate: `net.minecraftforge:forge:${minecraft_version}-${forge_version}`
- descriptor: `src/main/resources/META-INF/mods.toml`
- mod loader: `javafml`
- loader version range: `[47,)`
- Forge dependency range: `[47,)`
- Minecraft dependency range: `[1.20.1,1.21)`
- plugin repositories: Gradle Plugin Portal plus `https://maven.minecraftforge.net/`
- toolchain helper: `org.gradle.toolchains.foojay-resolver-convention` `0.7.0`

Evidence:

```text
Forge Maven metadata:
https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml
-> latest 1.20.1 Forge artifact observed: 1.20.1-47.4.20

ForgeGradle Maven metadata:
https://maven.minecraftforge.net/net/minecraftforge/gradle/ForgeGradle/maven-metadata.xml
-> latest ForgeGradle 6.x artifact observed: 6.0.54

Official Forge MDK:
https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.20/forge-1.20.1-47.4.20-mdk.zip
-> Gradle 8.8, Java 17, official mappings 1.20.1, javafml, loader range [47,)
```

Book II decision:

```text
Do not patch scaffold files in this slice.
```

Reason:

```text
The toolchain truth is locked, but a scaffold patch would not have a clean
compile proof without crossing into Book III Java loader API migration. Current
source imports NeoForge attachment/model/gametest APIs and some preserved
Fabric proof/dev APIs. Changing only build/descriptor files would create a
known compile failure rather than a proof-complete scaffold.
```

## Project Canon

### Book I - Foundation

Purpose:
Set the Forge 1.20.1 lane on the correct donor tag and remove stale active-lane
instructions from the front door.

Proof gate:
`SLABBED_SPINE.md`, `HANDOFF.md`, and this document agree on root, branch, donor
tag, target, allowed work, and forbidden lanes.

Stop condition:
Stop if the branch is not based at `release/neoforge-1.21.1-0.4.2-beta.2` or
if tracked source files become dirty during foundation work.

### Book II - Toolchain And Scaffold

Purpose:
Choose the exact Forge 1.20.1 toolchain from primary Forge evidence or a
known-good local donor, then create the smallest scaffold that can compile.

Status:
Toolchain truth locked from primary Forge evidence. Scaffold patch deferred
until the first Book III loader API migration slice is authorized, because
compile proof requires source API migration.

Subphases:

1. Verify ForgeGradle, Forge, mappings, Java, and Gradle compatibility.
2. Decide artifact/version naming.
3. Convert only build and descriptor scaffolding.
4. Prove the scaffold compiles before moving gameplay behavior.

Proof gate:
`./gradlew --no-daemon compileJava` or the verified Forge equivalent passes
after scaffold conversion.

Stop condition:
Stop on version conflict, mapping uncertainty, or any need to edit gameplay code
before the scaffold compiles.

Scaffold patch plan when implementation is authorized:

1. `settings.gradle`
   Add the Forge Maven plugin repository and the Foojay resolver convention from
   the official MDK.
2. `gradle/wrapper/gradle-wrapper.properties`
   Align the wrapper with the official Forge MDK Gradle `8.8` distribution.
3. `gradle.properties`
   Set Minecraft `1.20.1`, Forge `47.4.20`, official mappings `1.20.1`, Java
   17-era metadata, loader range `[47,)`, and a Forge artifact version name.
4. `build.gradle`
   Replace NeoForge ModDev wiring with ForgeGradle `6.0.54`, official mappings,
   `net.minecraftforge:forge:${minecraft_version}-${forge_version}`, and Forge
   `client`, `server`, and `gameTestServer` run configs.
5. `src/main/resources/META-INF/mods.toml`
   Replace the NeoForge descriptor shape with Forge `javafml` metadata and
   Forge/Minecraft dependency ranges.
6. Proof gate
   Run the verified Forge compile command only after the paired Book III source
   API migration slice has made compile possible:

```text
./gradlew --no-daemon compileJava
git diff --check
```

### Book III - Loader API Migration

Purpose:
Map NeoForge/Fabric-bridge surfaces to Forge 1.20.1 equivalents without changing
Slabbed behavior.

Current slice:
`forge-1.20.1-entrypoint-lifecycle-scaffold`

Status:
Compile-proven.

Allowed:
Forge scaffold files and the minimum `Slabbed.java` entrypoint needed for a
compileable skeleton.

Forbidden:
Attachment/persistence, networking/client sync, model loading, mixins,
gametest/proof harness migration, and gameplay behavior parity.

Proof:

```text
./gradlew --no-daemon compileJava
-> BUILD SUCCESSFUL

git diff --check
-> clean
```

### Chapter 3.1 - Attachment Persistence Decision

Current slice:
`forge-1.20.1-attachment-persistence-decision`

Status:
Decision documented, docs-only.

Decision:
Use a Forge `LevelChunk` capability as the primary replacement for NeoForge
chunk data attachments. Do not use `SavedData` as the primary anchor store.

Reason:
Slabbed's donor anchor truth is chunk-owned: eight per-`LevelChunk`
`LongOpenHashSet` marker sets keyed by packed `BlockPos` longs. Forge chunk
capabilities preserve that ownership model more directly than a global
dimension `SavedData` map. Client sync is not included; it remains the next
separate Book III decision.

Decision record:

```text
docs/porting/mc-1.20.1-forge-attachment-persistence-decision.md
```

High-risk surfaces:

- mod entrypoint and lifecycle events
- client setup and model loading
- mixin config and target descriptors
- attachment/persistent state storage
- networking and client sync
- command registration
- GameTest or replacement proof harness
- render/model/outline/raycast ownership

Regression-risk gate:

```text
docs/porting/mc-1.20.1-forge-regression-risk-checklist.md
```

Required before later networking/client sync, model loading, mixin, gametest,
behavior parity, live-proof, or release slices. The checklist must be answered
inside the active slice report; unanswered rows make the slice audit-only.

Proof gate:
Each migrated surface has a compile proof and a narrow behavioral proof before
the next surface begins.

Stop condition:
Stop if a migration requires changing Slabbed legal state grammar, rescue
behavior, or Visual Triad authority.

### Book IV - Behavior Parity

Purpose:
Bring Slabbed behavior across only after the Forge scaffold and API surfaces are
stable.

Proof gate:
Use the narrowest Forge-compatible proof for placement, survival, collision,
model, outline, and raycast agreement. Live proof is required for feel, targeting,
ghosting, or visible offset behavior.

Live-proof rule:
When a later slice reaches a live gate, Codex must cruise-control the exact real
Minecraft/Modrinth profile, jar, and world. Julia cannot provide manual live
runs while asleep. Auto/dev runs do not count as live proof: `runClient`,
`runServer`, `runGameTest`, `runServerGameTest`, `runClientGameTest`, and similar
automated dev-client routes are support evidence only.

Stop condition:
Stop after two failed fixes or if NeoForge proof is being mistaken for Forge
runtime proof, or if auto/dev-run evidence is being mistaken for live proof.

### Book V - Release Readiness

Purpose:
Prepare a Forge 1.20.1 candidate only after behavior and proof gates are green.

Proof gate:
Clean build, jar contents scan, bytecode hard-reference scan, loader launch
proof, and any Julia-requested live smoke.

Stop condition:
No publication, release tag, Modrinth upload, CurseForge upload, or GitHub
release without Julia's explicit authorization.

## Design Contract

The Forge port must preserve Slabbed's existing product laws:

- global slab support is expressed through named legal Slabbed state grammar
- placement must produce a canonical legal Slabbed state, preserve vanilla, or reject/defer
- model, outline, and raycast must agree for lowered or shifted objects
- placement success is not survival proof
- client visuals, packets, or rescue logic must not become server authority
- no broad solidity or sturdy-face lies
- no hidden tick loops, packet spam, broad scans, or per-block hot-path diagnostics without a gate and proof
- debug/proof tooling must be default-off and absent from public release behavior

## First Implementation Gate

Before any Java gameplay port starts, the next worker must have:

- exact Forge 1.20.1 toolchain versions: complete
- a chosen mappings strategy: complete, Mojang official `1.20.1`
- a minimal build/descriptor scaffold plan: complete, deferred until source API migration is authorized
- a compile proof command
- a list of loader API surfaces to migrate in order
- the Forge regression-risk checklist read and applied
- an explicit statement of what remains unproven

## Next Smallest Slice

Book III next loader API migration slice:

1. Storage-facade savepoint is complete at `c7a57620` with tag
   `save/forge-1-20-1-storage-facade`.
2. View-truth order decision is recorded in
   `docs/porting/mc-1.20.1-forge-view-truth-order-decision.md`.
3. View-truth order decision savepoint is complete at `39d523cb` with tag
   `save/forge-1-20-1-view-truth-order-decision`.
4. Client anchor mirror sync savepoint is complete at `07776aad` with tag
   `save/forge-1-20-1-client-anchor-mirror-sync`.
5. Non-`Level` render-view anchor lookup savepoint is complete at `c69d8665`
   with tag `save/forge-1-20-1-non-level-render-view-anchor-lookup`.
6. Persistence truth is now available through the server capability, client
   mirror, and non-`Level` lookup plumbing, but no mixin, gametest, behavior
   parity, Visual Triad proof, live proof, or release gate is proven.
7. Post-render-lookup roadmap alignment savepoint is complete at `709a50bd`
   with tag `save/forge-1-20-1-post-render-lookup-roadmap-alignment`.
8. Model-loading/render-path decision is recorded in
   `docs/porting/mc-1.20.1-forge-model-loading-render-path-decision.md`.
9. Model-loading/render-path decision savepoint is complete at `68c2c330` with
   tag `save/forge-1-20-1-model-render-path-decision`.
10. Model-wrapper registration scaffold savepoint is complete at `721e589f`
   with tag `save/forge-1-20-1-model-wrapper-scaffold`.
11. Post-model-wrapper roadmap alignment savepoint is complete at `139a15d2`
   with tag
   `save/forge-1-20-1-post-model-wrapper-scaffold-roadmap-alignment`.
12. Model-wrapper registration proof is source-backed `registration-proven` and
   savepointed at `d029de0c` with tag
   `save/forge-1-20-1-model-wrapper-registration-proof`.
13. Post-registration-proof roadmap alignment is docs-proven and savepointed at
   `022320b8` with tag
   `save/forge-1-20-1-post-registration-proof-roadmap-alignment`.
14. Model-wrapper render-view proof is source-backed `render-view-proven` and savepointed at `a3f5bed4` with tag `save/forge-1-20-1-model-wrapper-render-view-proof`.
15. Current legal route is docs-only `forge-1.20.1-post-render-view-proof-roadmap-alignment`.
16. Next legal route after this alignment is savepointed is docs/audit `forge-1.20.1-rendered-model-culling-triad-decision`, before any culling, rendered-model, or Visual Triad implementation/proof execution.
16. Preserve legal state grammar and gameplay behavior.
17. Prove each next slice with the narrow proof required by the risk checklist.
