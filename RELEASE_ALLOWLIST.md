# Release artifact allowlist

See `LAW.md` — this doc does not redefine the law. It governs only what may reach a published
artifact. Where anything here appears to touch placement, height or geometry, `LAW.md` is supreme.

**This file is a closed-world allowlist.** Every compilation unit and every resource that reaches a
release artifact must match an entry below. Anything that does not match is a build failure, by
name, at `verifyReleaseAllowlist`.

## Which artifacts this covers

This line uses Yarn mappings and Loom **does** register `remapJar` / `remapSourcesJar`, so `jar` and
`sourcesJar` are *intermediate* products (they land in `build/devlibs/` with a `-dev` classifier).
The artifacts a player or Modrinth ever sees are the REMAPPED ones. Confirmed by `./gradlew tasks
--all`, which lists `remapJar` ("Remaps the built project jar to intermediary mappings") and
`remapSourcesJar`, and by `build/libs/` containing only their outputs.

| Artifact | Producing task | Path |
| --- | --- | --- |
| mod jar | `remapJar` | `build/libs/slabbed-<version>.jar` |
| sources jar | `remapSourcesJar` | `build/libs/slabbed-<version>-sources.jar` |

The gate therefore reads the `remapJar` / `remapSourcesJar` outputs, not `jar` / `sourcesJar`. That
also puts it downstream of the `doLast` manifest prune those two tasks carry, so the gate sees the
final bytes and nothing later can touch them.

**This differs from the 26.2 line on purpose.** 26.2 uses official Mojang mappings and registers no
remap tasks there, so on that line `jar`/`sourcesJar` *are* the shipped artifacts and its gate reads
those. Pointing this line's gate at `jar`/`sourcesJar` would check a file nobody ships.

## Why an allowlist and not a denylist

The `slabbedHygieneExcludes(ext)` list in `build.gradle` is a list of things to **drop**. It cannot
see a package nobody thought to name. On the 26.2 line exactly that happened: a whole new
`com/slabbed/client/palette/**` screen and `com/slabbed/command/**` (`/slabrig`) matched nothing in
any exclusion or denylist and shipped in `release/mc262-0.5.0-alpha.1`.

Under an exclusion list, anything new ships by default. Under this allowlist, anything new **fails**
by default and has to be argued onto this list in a reviewable one-line diff, with a reason. The
reason is not decorative: a row with an empty reason cell is itself a build failure.

`slabbedHygieneExcludes` is **retained unchanged** and remains the mechanism for *removing* content.
The two are complementary, not alternatives — see "How exclusion and approval interact" below.

## Entry format

One entry per line, as a table row, so every approval is a one-line diff.

| Form | Matches |
| --- | --- |
| `path/to/pkg/**` | that package and **all** its subpackages, recursively |
| `path/to/pkg/*` | direct members of that package only — a **new subpackage is NOT covered** |
| `path/to/pkg/Name` | exactly that one logical unit |

Archive entries are normalised to a *logical unit* before matching, so one entry covers the class,
its nest members and its source file at once, and one list covers both jars:

- `com/slabbed/x/Foo.class`, `com/slabbed/x/Foo$Bar.class`, `com/slabbed/x/Foo.java`
  → all normalise to `com/slabbed/x/Foo`
- any other file keeps its literal path (`fabric.mod.json`, `assets/slabbed/lang/en_us.json`, …)

### Granularity policy

- **`**` (recursive) only for resource trees** (`assets/slabbed/**`), where a new subdirectory is
  ordinary content, not new behaviour.
- **Package-level `*` (non-recursive) for homogeneous feature packages.** Non-recursive on purpose:
  it is exactly what makes a brand-new subpackage RED even though its parent is approved. That is
  the palette failure encoded as a rule.
- **Class-level rows for genuinely mixed packages** — `com/slabbed/mixin/`, `com/slabbed/client/`,
  `com/slabbed/dev/` and `com/slabbed/util/`. Every one of those contains feature or shipped-debug
  code sitting directly beside something the exclusion list removes (`mixin/recorder/**`,
  `client/GapFillerOverlay`, `dev/audit/**`, `util/SlabTestKit`). A package-level approval there
  would rebuild the exact blindness this gate exists to remove. `com/slabbed/mixin/` in particular
  ships `"required": true` behaviour into vanilla for every player.

Class-level everywhere would churn on every new file; package-level everywhere stops catching
anything. The split above puts the fine granularity only where a leak has actually happened.

### The manifest is checked too

`verifyReleaseAllowlist` also asserts that every name in the `Fabric-Loom-Client-Only-Entries`
manifest attribute resolves to an entry that is actually present in that archive. Loom derives that
attribute from the client source set **output**, not from what survives into the artifact, so before
`207863f0` the released manifest advertised five classes the exclusion list had already removed. The
`doLast` prune on `remapJar` / `remapSourcesJar` fixes it; this assertion is what keeps it fixed. A
jar that lies about its own contents is a release defect even when every entry in it is approved.

## Approved — mod jar and sources jar

### Metadata and resources

| Entry | Reason |
| --- | --- |
| `META-INF/MANIFEST.MF` | Jar manifest. Required by the loader; its `Fabric-Loom-Client-Only-Entries` attribute is separately validated against the archive by this same gate. |
| `LICENSE_slabbed` | The mod's GPL-3.0 licence, copied in by the `jar` block. Mod jar only. |
| `fabric.mod.json` | Mod descriptor. Required by the loader. |
| `slabbed.mixins.json` | Main mixin config. Declares the 20 shipped main mixins; referenced from `fabric.mod.json`. |
| `slabbed.client.mixins.json` | Client mixin config. Declares the 7 shipped client mixins; referenced from `fabric.mod.json`. |
| `assets/slabbed/**` | The mod's own lang file. Recursive by policy — asset subdirectories are content, not behaviour. |

### Entrypoints

| Entry | Reason |
| --- | --- |
| `com/slabbed/Slabbed` | Mod entrypoint (`fabric.mod.json` `main`). |
| `com/slabbed/client/SlabbedClient` | Client entrypoint (`fabric.mod.json` `client`). Its two reflective hooks (`GapFillerOverlay`, `ScreenshotCaptureService`) both target excluded classes and both resolve by `Class.forName` behind a flag or an `isDevelopmentEnvironment()` check, so the release class holds no hard link to anything absent — see the port-debt note below. (A third hook, the BS-FB live trace, was deleted 2026-08-07 with its target classes — a closed investigation's TEMPORARY tooling.) |
| `com/slabbed/SlabbedDevMixinBootstrap` | `preLaunch` entrypoint named in the shipped `fabric.mod.json`, so the loader requires it to be present. Its whole body returns immediately unless `isDevelopmentEnvironment()`, and even then it only arms the two excluded recorder mixin configs after a classpath-presence check. Removing it from the jar would mean removing it from the descriptor, which is a functional change, not hygiene. It is what keeps the five recorder mixins out of `"required": true` release wiring. |

### Core feature code

| Entry | Reason |
| --- | --- |
| `com/slabbed/anchor/*` | The dy anchor attachments, their chunk-position packet codecs, and the per-world deep-dy consent stamp — the feature's data model and its sync wire. All three are server-authoritative, persistent and synchronized; none writes a file or reaches outside the save. |
| `com/slabbed/client/model/*` | Offset block-state model and Y-offset emitter — the lowering render path. |
| `com/slabbed/compat/*` | Compat hooks and the slab-surface-kind enum consumed by third-party slab mods. |
| `com/slabbed/compat/terrainslabs/*` | Terrain Slabs compat, dual mod-id gated. |
| `com/slabbed/placement/LandingResolver` | Placement-law decision helper used by `BlockItemPlacementIntentMixin` to derive one immutable server height from the player's root aim. It performs no file I/O, registration, rendering or diagnostics. |
| `com/slabbed/mixin/client/*` | The 7 client mixins declared in `slabbed.client.mixins.json`; every member is a render-offset, cull, remesh or offset-raycast mixin. Non-recursive, so the excluded `mixin/client/recorder/**` cannot creep back under this row. |
| `com/slabbed/mixin/torch/*` | `TorchBlockMixin` — torch attachment geometry. |

### Main mixins (class-level; `com/slabbed/mixin/` is a mixed package)

Every row below is declared in `slabbed.mixins.json`. The config and the archive agree exactly:
19 direct members here plus `torch/TorchBlockMixin`, and the config lists exactly those 20. There is
no inert, undeclared mixin shipping on this line.

| Entry | Reason |
| --- | --- |
| `com/slabbed/mixin/BlockItemPlacementIntentMixin` | Captures placement intent for the placement law. |
| `com/slabbed/mixin/BlockOnPlacedAnchorMixin` | Writes the dy anchor on placement. |
| `com/slabbed/mixin/BlockOnStateReplacedAnchorMixin` | Clears the dy anchor on state replacement. |
| `com/slabbed/mixin/BrewingStandParticleMixin` | Particle origin tracks the lowered block. |
| `com/slabbed/mixin/CandleParticleMixin` | Particle origin tracks the lowered block. |
| `com/slabbed/mixin/CarpetBlockMixin` | Carpet support and shape over a lowered slab. |
| `com/slabbed/mixin/DecoratedPotParticleMixin` | Particle origin tracks the lowered block. |
| `com/slabbed/mixin/FencePaneSlabConnectionMixin` | Fence and pane connection against a lowered slab. |
| `com/slabbed/mixin/HangingSignAttachedMixin` | Hanging-sign attachment from above. |
| `com/slabbed/mixin/RedstoneTorchParticleMixin` | Particle origin tracks the lowered block. |
| `com/slabbed/mixin/RedstoneWireBlockMixin` | Redstone wire connection and support over lowered slabs. |
| `com/slabbed/mixin/ServerInteractBlockHitToleranceMixin` | Server-side hit tolerance for offset targeting. |
| `com/slabbed/mixin/SlabSupportBlockMixin` | Slab support surface. |
| `com/slabbed/mixin/SlabSupportStateMixin` | Slab support state. |
| `com/slabbed/mixin/TorchParticleAccessor` | Accessor supporting the torch particle mixins. |
| `com/slabbed/mixin/TorchParticleMixin` | Particle origin tracks the lowered block. |
| `com/slabbed/mixin/WallRedstoneTorchParticleMixin` | Particle origin tracks the lowered block. |
| `com/slabbed/mixin/WallSlabConnectionMixin` | Wall connection against a lowered slab. |
| `com/slabbed/mixin/WallTorchParticleMixin` | Particle origin tracks the lowered block. |

### Client (class-level; `com/slabbed/client/` is a mixed package)

| Entry | Reason |
| --- | --- |
| `com/slabbed/client/BetaNoticeClient` | Shipped one-time beta notice on world join. |
| `com/slabbed/client/BetaNoticeSessionGate` | Per-session gate for the beta notice. |
| `com/slabbed/client/BetaNoticeDismissedWorlds` | Per-world "don't show again" store for the beta notice. |
| `com/slabbed/client/ClientDy` | Client-side dy lookup — the render and collision read path. |
| `com/slabbed/client/SlabAnchorClientSync` | Receives anchor sync from the server. |
| `com/slabbed/client/SlabbedModelLoadingPlugin` | Installs the offset block-state model. |
| `com/slabbed/client/SlabbedClientFlags` | Sole member is `GAP_FILL`, read by a `getstatic` in the shipped `SlabbedClient.initGapFillerOverlay()`. Excluding this class while shipping `SlabbedClient` would be a release `NoClassDefFoundError` on client init. Two constants and a private constructor; defaults off. |

### Shipped debug surface — `/slabdy` and `/slabdev`

Standing maintainer rule: **the debug tooling ships in EVERY jar, default off, op-invocable.** These
rows exist because of that rule, not in spite of it — do not "clean" them out. This is the sharpest
difference from the 26.2 line, whose allowlist excludes `com/slabbed/dev/**` and the debug commands
wholesale (on that line neither command is registered from release code at all, which its own ruling
records as an unresolved gap). Here both are registered unconditionally and reachable in a release
build, which is what the rule actually asks for; it was implemented at `207863f0`.

None of these four does file I/O, and none allocates per block or per frame.

| Entry | Reason |
| --- | --- |
| `com/slabbed/client/SlabdyClientCommands` | `/slabdy` — the client debug command and its passive overlay. Registered unconditionally by `SlabbedClient.onInitializeClient()`. Overlay defaults OFF; a bare `/slabdy` toggles it, so no player sees anything they did not ask for. |
| `com/slabbed/dev/SlabbedDevCommands` | `/slabdev` — the server debug command. Registered unconditionally by `Slabbed.initShippedDebugCommands()` and gated at `PermissionLevel.GAMEMASTERS` (op level 2): not player-toggleable, op-invocable, inert until invoked. Its `audit` subcommand reaches the excluded dev harness reflectively and reports "not available in this build", which is the release case. |
| `com/slabbed/dev/SlabbedDiagnostics` | The pure, server-computable analysis layer behind `/slabdy`'s overlay and row dump — the dy triad (outline / raycast / model) with its two distinct "nothing was seen" sentinels. Also the type of the `Sample` that `SlabbedAuditBridge` takes as a hard parameter, so the shipped bridge cannot link without it. No client-only or filesystem dependency. |
| `com/slabbed/dev/SlabdyRowFormatter` | Formats `SlabbedDiagnostics` output into the `/slabdy row` chat lines. Has a shipped consumer here (`SlabdyClientCommands`), which is why this line approves it where 26.2 excluded it — on that line the same class had no shipped caller at all. |

### Util (class-level; `com/slabbed/util/` is a mixed package)

| Entry | Reason |
| --- | --- |
| `com/slabbed/util/SlabSupport` | Support-surface resolution and the visual Y offset — the core of the feature. |
| `com/slabbed/util/SlabbedOffsetRaycast` | Offset-aware nearest-hit raycast — the targeting overhaul. |
| `com/slabbed/util/SlabbedServerHitValidation` | Server-side hit validation for offset placement; the sole consumer of `ServerInteractBlockHitToleranceMixin`'s widened tolerance. |
| `com/slabbed/util/SlabbedAuditBridge` | The deliberate release-safe boundary, and the reason the excluded harnesses can stay excluded: shipped code (`Slabbed`, `SlabAnchorAttachment`, `BlockItemPlacementIntentMixin`, `SlabdyClientCommands`) calls THIS, never `com.slabbed.dev.audit.**` or `com.slabbed.debug.**` directly, so those packages leave the jar without a single hard link breaking. In a release build `RECORDER_CLASS` resolves to null once at clinit and every method is a null-check return. That single cached resolve — rather than a `Class.forName` per call — is deliberate: `isRecorderEnabled()` is polled every client tick, and per-tick reflection by string name is the exact shape of the lag that has shipped twice on this project. This is architecture keeping diagnostics OUT of the jar, not diagnostics leaking in. |

## How exclusion and approval interact

They are different operations on the same question and both are needed.

| | `slabbedHygieneExcludes` (`build.gradle`) | `verifyReleaseAllowlist` (this file) |
| --- | --- | --- |
| Direction | removes content from both artifacts | asserts what remains was reviewed |
| Model | open world — everything ships unless named | closed world — nothing ships unless listed |
| Catches | things somebody already knows are dev-only | unknown-unknowns: a package or class nobody thought about |
| Misses on its own | a brand-new package nobody excluded | nothing about *content* — it only sees paths |

Two legitimate responses to a RED unit, and only two:

1. **Exclude it** — add it to `slabbedHygieneExcludes` in `build.gradle` (one list, both artifacts),
   and gate or remove whatever registers it.
2. **Approve it** — add a row above with a reason that would survive review.

Widening an existing pattern purely to make the build green is neither.

## Known gap on this line: there is no content denylist layer

The 26.2 line runs a second, independent gate — `verifyReleaseJarHygiene`, an open-world denylist
over file *content* (`releaseHygieneForbiddenTokens`) — which catches an already-approved file that
later *grows* a reference to a diagnostic seam. **This line has no such task**; `grep` for
`releaseHygiene` / `verifyRelease` in this tree before this change returned nothing but comment
prose. Nothing was deleted to install this allowlist.

It was not invented here either, because on this line a naive port would be RED on day one for
reasons that need a ruling rather than a build file: the shipped **sources** jar legitimately
contains the strings `GapFillerOverlay` and `ScreenshotCaptureService` (the
reflective hooks in `SlabbedClient`, pointing at classes this build deliberately excludes)
and `TargetDyOverlay`, `SlabbedLab` and `SlabRigCommand` (prose in shipped javadoc). A content
denylist that fires on a javadoc sentence teaches people to widen it. Porting it wants a token set
with a maintainer sign-off, plus a decision on whether the dangling hooks should remain. Recorded
here as port debt, not silently improvised.

## Maintenance

- Entries that match nothing in either artifact are reported as `STALE` and printed as a warning by
  `verifyReleaseAllowlist`. They do not fail the build — a legitimate deletion should not be blocked
  — but a stale entry is how a list rots, so clear them.
- `./gradlew releaseAllowlistReport --continue` prints the full unapproved inventory and writes
  `build/reports/release-allowlist/unapproved.txt` without failing *that task*. `--continue` is
  needed only because the verify task finalizes `remapJar`/`remapSourcesJar` and will still fail the
  overall build while anything is unapproved — which is the point. The report is a review aid, not a
  bypass: there is no flag that makes `verifyReleaseAllowlist` pass.
