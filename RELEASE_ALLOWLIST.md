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
| `com/slabbed/client/SlabbedDebugCommands` | Registers `/slabdy` and `/slabdev` from the shipped client entrypoint — the standing debug-tooling rule under the maintainer's 2026-08-07 reading that the commands must be INVOCABLE on a shipped jar. Wiring only (read the crosshair, print to chat); the node structure and strings are in the headless `SlabbedDebugCommandTree`. Registers ONE callback that builds two Brigadier trees: no tick hook, no HUD element, no lifecycle listener, no world-save write, no disk access, nothing running until someone types the command. Names no excluded class — the debug tools are reached through `SlabbedDebugToolBridge`. |
| `com/slabbed/client/SlabbedDebugToolBridge` | Release-safe client seam between those shipped commands and the two development-only debug tools (target-dy overlay, live-cursor recorder). Same architecture as `SlabbedDiagnosticsBridge`, applied to the command surface: with no provider installed — the release case — `available()` is false and the commands report "not available in this build" and change nothing. Shipping the seam is what keeps the implementations OUT of the jar while leaving the commands honest. |

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
| `com/slabbed/util/SlabbedDebugCommandTree` | The Brigadier node structure and feedback strings for the shipped `/slabdy` and `/slabdev`. Pure Brigadier, generic over the command source, no Minecraft or client type in any signature — deliberately so: a client command tree is unreachable from a headless dedicated-server GameTest, and "the command is invocable" is only worth what its test is worth. `ShippedDebugCommandsTest` registers these exact builders into a real dispatcher and executes real command strings. Inert until invoked: everything runs inside an `executes(...)` body. |
| `com/slabbed/util/SlabdyRowFormatter` | Headless field computation for the `[slabdy]` diagnostic row. **Previously excluded** with the reason "no shipped consumer on this line" — that is no longer true: `/slabdy row` is a shipped consumer, and it is the one debug subcommand that does real work on a release jar (it needs nothing but `SlabAnchorAttachment` and `SlabSupport`, both approved above). Read-only: it computes strings and touches no state. |

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
  release painting drops are pure vanilla; `SlabdyRowFormatter` (no shipped consumer on this line —
  **reversed in the second pass below: `/slabdy row` is now that consumer, and the formatter is
  approved above**); the never-wired `SlabBlockPlacementFixMixin` (source kept in the repo); `SlabbedClientFlags`; the
  six source-only drift units (`CaptureProfile`, `DyFingerprintDump`, `ScreenshotCaptureContext`,
  the `ScreenshotFlightLock` tombstone, two `.gitkeep`s).

Standing rule note (updated 2026-08-07, second pass): the original text of this note reported that
`/slabdy` and `/slabdev` were registered in no release code at all on this line — `/slabdev` only
from the compile-excluded `com/slabbed/dev/**` and from the development-only diagnostics companion,
`/slabdy` from nowhere. That gap has now been closed under the maintainer's ruling that "ships in every jar"
means an operator can actually INVOKE the command on a release build, default off.

- Both commands register from `com/slabbed/client/SlabbedDebugCommands`, called unconditionally by
  `SlabbedClient` — the `client` entrypoint the shipped `fabric.mod.json` declares. No
  `isDevelopmentEnvironment()` guard and no reflective hook, because either one is exactly how the
  commands became unreachable in the first place.
- They are CLIENT commands. Every debug surface on this line is client state (a local HUD overlay,
  a recorder writing to the local game directory), so unlike the 1.21.11 sibling's server-side
  `/slabdev audit` — which writes an audit report into the *server's* game directory and is
  therefore gated at permission level 2 — there is no server-side surface here to op-gate. A Fabric
  client command never reaches the server and cannot affect another player. Gating a local HUD
  toggle at op level would also make it unusable in an ordinary singleplayer world, defeating the
  ruling. Both roots must stay client-side together: Fabric's client dispatcher forwards to the
  server only on "unknown command", so a client root would swallow a same-named server subcommand.
- `/slabdev` keeps this line's live-confirmed spelling exactly (`/slabdev debug on`,
  `/slabdev record on`). Only the registration moved; the implementations stayed in the diagnostics
  companion and are reached through `SlabbedDebugToolBridge`. The command surface is therefore
  identical in dev and release, and every dev use of `/slabdev debug on` exercises the shipped
  registration path.
- Absent implementations degrade honestly, never throw: "not available in this build".
  `/slabdy row` and `build` need nothing but shipped code and work for real on a release jar.
- The `/slabrig` / `/slabkit` / `/slabcheck` family does NOT follow them out of the gate. It stays
  dev-gated in `Slabbed.initDevFeatures` and excluded from both artifacts, unchanged.
- Not shipped, deliberately: the sibling's `/slabdev audit` subcommand. Its harness
  (`com/slabbed/dev/audit/**`) is Yarn-named, does not compile under this line's Mojang mappings and
  is compile-excluded from `main` (`build.gradle`'s trailing `sourceSets.main.java.exclude`), so the
  node could never do anything in ANY build here — shipping a permanently dead node would be
  surface without substance. It gets added when the harness is ported.

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

### Manifest honesty — the third, narrower check

Both layers above ask what an artifact *contains*. Neither asks what it *claims to contain*, and an
artifact can lie about itself: Loom derives `Fabric-Loom-Client-Only-Entries` from the client
source-set output, not from the entries that survive the `jar` task's exclusion filter, and its only
public hook is additive. So every unit `releaseArtifactExclusions` drops stays **named in the
manifest** — the shipped mod jar advertised 42 entries, 5 of which it did not contain
(`SlabbedClientFlags`, `ScreenshotCaptureContext` and its `$State`, `DyFingerprintDump`,
`CaptureProfile`). Both gates were green throughout: the names were correct as names, and the
classes really were gone.

`pruneClientOnlyEntriesManifest` in `build.gradle` fixes it as a `doLast` on `jar` and `sourcesJar`
(this line is Mojang-mapped and has no `remapJar`/`remapSourcesJar`, so those two *are* the shipped
artifacts). It is stated as a **subtraction of names with no matching archive entry**, which is what
makes it safe in both directions: it cannot drop a real entry, and it cannot go stale as this file
and the exclusion list change, because it re-derives the attribute from the finished archive rather
than from a second list to forget. Same defect and same remedy as the 1.21.11 line's `207863f0`
(item V2).

`verifyReleaseJarHygiene` then asserts the result on the finished archives — zero named-but-absent
entries in either jar — so if that `doLast` is ever detached, reordered, or defeated by a new
archive path, the build goes red instead of shipping a manifest that misdescribes its own contents.

## Maintenance

- Allowlist entries that match nothing are reported as `STALE` by `releaseAllowlistReport` and
  printed as a warning by `verifyReleaseAllowlist`. They do not fail the build — a legitimate
  deletion should not be blocked — but a stale entry is how a list rots, so clear them.
- `./gradlew25 releaseAllowlistReport --continue` prints the full unapproved inventory and writes
  `build/reports/release-allowlist/unapproved.txt` without failing *that task*. `--continue` is
  needed only because `verifyReleaseAllowlist` finalizes `jar`/`sourcesJar` and will still fail the
  overall build while anything is unapproved — which is the point. The report is a review aid, not a
  bypass: there is no flag that makes `verifyReleaseAllowlist` pass.
