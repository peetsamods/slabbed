# Release artifact allowlist

See `LAW.md` — this doc does not redefine the law. It governs only what may reach a published
artifact. Where it appears to touch placement, height or geometry, `LAW.md` is supreme.

**This file is a closed-world allowlist.** Every compilation unit and every resource that reaches a
release artifact must match an entry below. Anything that does not match is a build failure, by
name, at `verifyReleaseAllowlist`.

Two artifacts are covered — both are published, and the palette leak was in both:

| Artifact | Task | Path |
| --- | --- | --- |
| mod jar | `jar` | `build/libs/slabbed-<version>.jar` |
| sources jar | `sourcesJar` | `build/libs/slabbed-<version>-sources.jar` |

(This Loom setup uses official Mojang mappings and registers no `remapJar`/`remapSourcesJar`, so
`jar` and `sourcesJar` *are* the shipped artifacts. Confirmed by `tasks --all`.)

## Why an allowlist and not another denylist

`releaseHygieneForbiddenTokens` in `build.gradle` is a denylist of ~10 hard-coded names. The `jar`
block's `exclude(...)` list is likewise a list of *things to drop*. Neither can see a package nobody
thought to name: `com/slabbed/client/palette/**` and `com/slabbed/command/**` matched nothing in
either, so the `V` palette screen and `/slabrig` shipped in `release/mc262-0.5.0-alpha.1`.

Under a denylist, anything new ships by default. Under this allowlist, anything new **fails** by
default and has to be argued onto this list in a reviewable diff, with a reason.

The denylist is retained as an independent second layer. See "How the two layers interact" below.

## Entry format

One entry per line, as a table row, so every approval is a one-line diff.

| Form | Matches |
| --- | --- |
| `path/to/pkg/**` | that package and **all** its subpackages, recursively |
| `path/to/pkg/*` | direct members of that package only — a **new subpackage is NOT covered** |
| `path/to/pkg/Name` | exactly that one logical unit |

Archive entries are normalised to a *logical unit* before matching, so one entry covers the class,
its nest members and its source file at once:

- `com/slabbed/x/Foo.class`, `com/slabbed/x/Foo$Bar.class`, `com/slabbed/x/Foo.java`
  → all normalise to `com/slabbed/x/Foo`
- any other file keeps its literal path (`fabric.mod.json`, `assets/slabbed/lang/en_us.json`, …)

### Granularity policy

- **Package-level `*` (non-recursive) is the default** for packages that are homogeneous feature
  code. Non-recursive on purpose: it is exactly what makes a brand-new `client/palette/`
  subpackage RED even though `client/` is approved. That is the palette failure encoded as a rule.
- **`**` (recursive) only for resource trees** (`assets/slabbed/**`), where new subdirectories are
  ordinary content, not new behaviour.
- **Class-level entries for genuinely mixed packages** — `com/slabbed/mixin/`, `com/slabbed/util/`,
  `com/slabbed/client/`, `com/slabbed/network/`. Each of these today contains feature code sitting
  next to a diagnostic, capture or test-only seam; a package-level approval there would re-create
  the exact blindness this gate exists to remove. `com/slabbed/mixin/` in particular ships
  `"required": true` behaviour into vanilla for every player, and already hid a rig-capture mixin.

Class-level everywhere would churn on every new file; package-level everywhere stops catching
anything. The split above puts the fine granularity only where a leak has actually happened.

## Approved — mod jar and sources jar

### Metadata and resources

| Entry | Reason |
| --- | --- |
| `META-INF/MANIFEST.MF` | Jar manifest; carries the `Slabbed-Git-Sha` / `Slabbed-Build-Time` identity stamp a jar must be traceable by. |
| `LICENSE_slabbed` | The mod's licence, copied in by the `jar` block. |
| `fabric.mod.json` | Mod descriptor. Required by the loader. |
| `slabbed.mixins.json` | Main mixin config. Required by the loader. |
| `slabbed.client.mixins.json` | Client mixin config. Required by the loader. |
| `assets/slabbed/**` | The mod's own lang file and chain-ceiling-support block models. |

### Core feature code

| Entry | Reason |
| --- | --- |
| `com/slabbed/Slabbed` | Mod entrypoint. |
| `com/slabbed/anchor/*` | The dy anchor attachment and its chunk-position packet codec — the feature's data model. |
| `com/slabbed/placement/*` | Landing resolver, connector settle, landing-hit validation — the placement law. |
| `com/slabbed/client/model/*` | Offset block-state model, Y-offset emitter, chain-ceiling geometry — the lowering render path. |
| `com/slabbed/client/runtime/*` | Lowered side-slab retargeter — client targeting. |
| `com/slabbed/compat/*` | Compat hooks and the slab-surface-kind enum consumed by third-party slab mods. |
| `com/slabbed/compat/terrainslabs/*` | Terrain Slabs compat (dual mod-id gate). |
| `com/slabbed/mixin/client/*` | The 12 client render/interaction mixins declared in `slabbed.client.mixins.json`; every member is a render-offset, remesh or offset-raycast mixin. |
| `com/slabbed/mixin/torch/*` | `TorchBlockMixin` — torch attachment geometry. |

### Main mixins (class-level; `com/slabbed/mixin/` is a mixed package)

| Entry | Reason |
| --- | --- |
| `com/slabbed/mixin/BlockCollisionsLoweredAboveMixin` | Collision against a lowered slab above. |
| `com/slabbed/mixin/BlockItemPlacementIntentMixin` | Captures placement intent for the WYSIWYG placement law. |
| `com/slabbed/mixin/BlockOnPlacedAnchorMixin` | Writes the anchor on placement. |
| `com/slabbed/mixin/BlockOnStateReplacedAnchorMixin` | Clears the anchor on state replacement. |
| `com/slabbed/mixin/BrewingStandParticleMixin` | Particle origin follows the lowered block. |
| `com/slabbed/mixin/CandleParticleMixin` | Particle origin follows the lowered block. |
| `com/slabbed/mixin/CarpetBlockMixin` | Carpet support/shape over a lowered slab. |
| `com/slabbed/mixin/DecoratedPotParticleMixin` | Particle origin follows the lowered block. |
| `com/slabbed/mixin/FencePaneSlabConnectionMixin` | Fence/pane connection against a lowered slab. |
| `com/slabbed/mixin/HangingSignAttachedMixin` | Hanging-sign attachment from above. |
| `com/slabbed/mixin/ItemStackUseCreatedContactMixin` | Use-on contact point for offset placement. |
| `com/slabbed/mixin/LeverParticleMixin` | Particle origin follows the lowered block. |
| `com/slabbed/mixin/LivingEntityLoweredScaffoldingMixin` | Scaffolding movement over lowered geometry. |
| `com/slabbed/mixin/RedstoneTorchParticleMixin` | Particle origin follows the lowered block. |
| `com/slabbed/mixin/RedstoneWireBlockMixin` | Redstone wire connection/support over lowered slabs. |
| `com/slabbed/mixin/ServerInteractBlockHitToleranceMixin` | Server-side hit tolerance for offset targeting. |
| `com/slabbed/mixin/SlabSupportBlockMixin` | Slab support surface. |
| `com/slabbed/mixin/SlabSupportStateMixin` | Slab support state. |
| `com/slabbed/mixin/TorchParticleAccessor` | Accessor supporting the torch particle mixins. |
| `com/slabbed/mixin/TorchParticleMixin` | Particle origin follows the lowered block. |
| `com/slabbed/mixin/WallRedstoneTorchParticleMixin` | Particle origin follows the lowered block. |
| `com/slabbed/mixin/WallSlabConnectionMixin` | Wall connection against a lowered slab. |
| `com/slabbed/mixin/WallTorchParticleMixin` | Particle origin follows the lowered block. |

### Client (class-level; `com/slabbed/client/` is a mixed package)

| Entry | Reason |
| --- | --- |
| `com/slabbed/client/SlabbedClient` | Client entrypoint (fabric.mod.json `client`). The dangling ScreenshotCaptureService/GapFillerOverlay reflective hooks were removed by the allowlist ruling; the remaining dev-only hook (DyFingerprintDump) is gated on `isDevelopmentEnvironment()` and resolves by reflection, so the release class holds no hard link to excluded classes. |
| `com/slabbed/client/PlacementDyPredictionJournal` | The C3 client prediction journal — the shipped prediction/correction apply path. Carries the `debugCell()`/`CellDebug` observation seam on purpose: PlacementDyPredictionClientGameTest asserts journal cell state through it against the REAL shipped path, and there is no equivalent observation point outside the class. Inert unless called; nothing shipped calls it. |
| `com/slabbed/client/BetaNoticeClient` | Shipped one-time beta notice on world join. |
| `com/slabbed/client/BetaNoticeSessionGate` | Per-session gate for the beta notice. |
| `com/slabbed/client/BetaNoticeDismissedWorlds` | Per-world "don't show again" store for the beta notice. |
| `com/slabbed/client/ClientDy` | Client-side dy lookup — the render/collision read path. |
| `com/slabbed/client/SlabAnchorClientSync` | Receives anchor sync from the server. |
| `com/slabbed/client/SlabGeometricRemeshScheduler` | Schedules section remeshes when dy changes. |
| `com/slabbed/client/SlabImportantRemesh` | Important-dirty remesh path. |
| `com/slabbed/client/SlabbedModelLoadingPlugin` | Installs the offset block-state model. |

### Network (class-level; `com/slabbed/network/` is a mixed package)

| Entry | Reason |
| --- | --- |
| `com/slabbed/network/PlacementDyPredictionBridge` | Common/server-safe boundary between vanilla prediction and the C3 client journal — the shipped `openSequence`/`publishClientBatch` wire. Carries the test-trace seam (`traceCorrectionWire`, `markTestPhase`, the snapshot readers) on purpose: the trace hooks are CALLED FROM the shipped SEND/RECEIVE/APPLY path (PlacementDyCorrectionServer, SlabbedClient, the journal), are no-ops until a gametest arms them, and stripping them would remove the only way PlacementDyPredictionClientGameTest and PlacementCaptureBoundaryGameTest observe the real wire. Coverage was ruled worth more than list tidiness. |
| `com/slabbed/network/PlacementDyCorrectionPayload` | Server→client dy correction payload. |
| `com/slabbed/network/PlacementDyCorrectionServer` | Server side of the dy correction wire. |
| `com/slabbed/network/PlacementDyPredictionEnvelopePayload` | Client→server prediction envelope. |

### Util (class-level; `com/slabbed/util/` is a mixed package)

| Entry | Reason |
| --- | --- |
| `com/slabbed/util/BuildStamp` | Reads the manifest identity stamp at runtime; release infrastructure, not diagnostics. |
| `com/slabbed/util/SlabbedDiagnosticsBridge` | The deliberate release-safe no-op boundary: the public mod ships THIS bridge and none of the recorder/overlay/Sentinel implementations behind it. Dev and GameTest runtimes install the real provider; in release every call is a cheap no-op. That is architecture keeping diagnostics OUT of the jar, not diagnostics leaking in. |
| `com/slabbed/util/ChainBridgeTextureVariant` | Chain ceiling-bridge texture selection. |
| `com/slabbed/util/PlacementIntentState` | Placement intent carried across the use-on path. |
| `com/slabbed/util/PlacementVerificationVerdict` | Placement verification result type. |
| `com/slabbed/util/SlabEnsembleCoherence` | Combined-slab ensemble coherence law. |
| `com/slabbed/util/SlabSupport` | Support-surface resolution. |
| `com/slabbed/util/SlabbedOffsetRaycast` | Offset-aware nearest-hit raycast — the targeting overhaul. |

## Ruling executed (2026-08-07)

The original 32-unit RED inventory has been ruled on and executed; the build is GREEN with the
allowlist honestly describing both jars.

- **Approved** (rows above): `SlabbedDiagnosticsBridge`, `PlacementDyPredictionBridge`,
  `SlabbedClient`, `PlacementDyPredictionJournal` — each with the reason on its row, including the
  two deliberately-retained gametest observation seams.
- **Excluded** (the shared `releaseArtifactExclusions` list in `build.gradle`, consumed by BOTH the
  `jar` and `sourcesJar` tasks so the two sets cannot drift again): the entire `/slabrig` /
  `/slabkit` / `/slabcheck` rig family (`com/slabbed/command/**`, `SlabTestKit`), whose registration
  is dev-gated in `Slabbed.initDevFeatures`; `PaintingRigDropCaptureMixin`, moved out of
  `slabbed.mixins.json` into the dev-only `slabbed.rig.mixins.json` carried by the gametest mod, so
  release painting drops are pure vanilla; `SlabdyRowFormatter` (no shipped consumer on this line);
  the never-wired `SlabBlockPlacementFixMixin` (source kept in the repo); `SlabbedClientFlags`; the
  six source-only drift units (`CaptureProfile`, `DyFingerprintDump`, `ScreenshotCaptureContext`,
  the `ScreenshotFlightLock` tombstone, two `.gitkeep`s).

Standing rule note: `/slabdy` and `/slabdev` (ship in every jar, default off) are NOT restructured
by this ruling — on this line neither is registered in release code at all (`/slabdev` lives in the
compile-excluded `com/slabbed/dev/**` and the diagnostics mod; `/slabdy` has no registration
anywhere), which is reported as-is, not "fixed" here.

Anything NEW reaching a release artifact still needs one of the two legitimate responses:

1. **Exclude it** — add it to `releaseArtifactExclusions` in `build.gradle` (one shared list for
   both jars), and gate/remove its registration.
2. **Approve it** — add a row above with a reason that would survive review.

Silently widening a pattern to make the build green is neither.

## How the two layers interact

They are independent and neither subsumes the other. Both must pass.

| | `verifyReleaseAllowlist` (this file) | `verifyReleaseJarHygiene` (`releaseHygieneForbiddenTokens`) |
| --- | --- | --- |
| Model | closed world — deny unless listed | open world — allow unless named |
| Granularity | archive **paths** (which units exist) | file **content** (which symbols are referenced) |
| Catches | unknown-unknowns: a whole new package or class nobody thought about | a listed, approved file that *grows* a reference to a diagnostic seam |
| Misses on its own | an approved class whose body starts calling `TargetDyOverlay` | a brand-new package whose names nobody denylisted |

Concretely: the allowlist would have caught `com/slabbed/client/palette/**` on day one; the denylist
would catch an approved `SlabbedClient` acquiring a `SlabModelStaleSentinelClient` call, or a mixin
JSON listing a debug mixin, without anyone touching this file. Keep both.

## Maintenance

- Allowlist entries that match nothing are reported as `STALE` by `releaseAllowlistReport` and
  printed as a warning by `verifyReleaseAllowlist`. They do not fail the build — a legitimate
  deletion should not be blocked — but a stale entry is how a list rots, so clear them.
- `./gradlew25 releaseAllowlistReport --continue` prints the full unapproved inventory and writes
  `build/reports/release-allowlist/unapproved.txt` without failing *that task*. `--continue` is
  needed only because `verifyReleaseAllowlist` finalizes `jar`/`sourcesJar` and will still fail the
  overall build while anything is unapproved — which is the point. The report is a review aid, not a
  bypass: there is no flag that makes `verifyReleaseAllowlist` pass.
