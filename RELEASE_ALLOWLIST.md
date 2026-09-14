# Release artifact allowlist

See `LAW.md` — this doc does not redefine the law. It governs only what may reach a published
artifact. Where anything here appears to touch placement, height or geometry, `LAW.md` is supreme.

**This file is a closed-world allowlist.** Every compilation unit and every resource that reaches a
release artifact must match an entry below. Anything that does not match is a build failure, by
name, at `verifyReleaseAllowlist`.

## Which artifacts this covers

This line uses Yarn mappings and Loom **does** register `remapJar` / `remapSourcesJar`, so `jar` and
`sourcesJar` are *intermediate* products (they land in `build/devlibs/` with a `-dev` classifier).
The artifacts a player or Modrinth ever sees are the REMAPPED ones.

| Artifact | Producing task | Path |
| --- | --- | --- |
| mod jar | `remapJar` | `build/libs/slabbed-<version>.jar` |
| sources jar | `remapSourcesJar` | `build/libs/slabbed-<version>-sources.jar` |

The gate therefore reads the `remapJar` / `remapSourcesJar` outputs, not `jar` / `sourcesJar`. That
also puts it downstream of the `doLast` manifest prune those two tasks carry, so the gate sees the
final bytes and nothing later can touch them.

## Why an allowlist and not a denylist

The `slabbedHygieneExcludes(ext)` list in `build.gradle` is a list of things to **drop**. It cannot
see a package nobody thought to name — that is exactly how the 26.2 line once shipped a whole new
`com/slabbed/client/palette/**` screen and `com/slabbed/command/**` (`/slabrig`) in a release jar,
matching nothing in any exclusion list. Under an exclusion list, anything new ships by default.
Under this allowlist, anything new **fails** by default and has to be argued onto this list in a
reviewable one-line diff, with a reason. The reason is not decorative: a row with an empty reason
cell is itself a build failure.

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
- **Package-level `*` (non-recursive) for homogeneous feature packages.**
- **Class-level rows for genuinely mixed packages** — `com/slabbed/mixin/`, `com/slabbed/client/`
  and `com/slabbed/anchor/`. Each of those contains feature code sitting beside something the
  exclusion list removes, or beside something that turned out to need its own scrutiny (see the
  two ORPHANED entries below). A package-level approval there would rebuild the exact blindness
  this gate exists to remove.

### The manifest is checked too

`verifyReleaseAllowlist` also asserts that every name in the `Fabric-Loom-Client-Only-Entries`
manifest attribute resolves to an entry that is actually present in that archive. Loom derives that
attribute from the client source set output, not from what survives into the artifact, so an
exclusion can leave a manifest advertising a class the archive no longer has. The `doLast` prune on
`remapJar` / `remapSourcesJar` fixes it; this assertion is what keeps it fixed.

## Approved — mod jar and sources jar

### Metadata and resources

| Entry | Reason |
| --- | --- |
| `META-INF/MANIFEST.MF` | Jar manifest. Required by the loader; its `Fabric-Loom-Client-Only-Entries` attribute is separately validated against the archive by this same gate. |
| `LICENSE_slabbed` | The mod's GPL-3.0-only licence, copied in by the `jar` block. Mod jar only. |
| `fabric.mod.json` | Mod descriptor. Required by the loader. |
| `slabbed.mixins.json` | Required main mixin configuration referenced from `fabric.mod.json`. |
| `slabbed.client.mixins.json` | Required client mixin configuration referenced from `fabric.mod.json`. |
| `assets/slabbed/**` | The mod's own lang file and the chain-ceiling-support model. Recursive by policy — asset subdirectories are content, not behaviour. |

### Entrypoints

| Entry | Reason |
| --- | --- |
| `com/slabbed/Slabbed` | Mod entrypoint (`fabric.mod.json` `main`). |
| `com/slabbed/client/SlabbedClient` | Client entrypoint (`fabric.mod.json` `client`). |

### Core feature code (package-level; homogeneous)

| Entry | Reason |
| --- | --- |
| `com/slabbed/compat/*` | Compat dispatch and the slab-surface-kind enum consumed by third-party slab mods. Dispatch is documented subtractive-only and unreachable when the target mod is absent. |
| `com/slabbed/compat/terrainslabs/*` | Terrain Slabs compat, mod-id gated. |
| `com/slabbed/placement/*` | Placement-law decision helpers: `LandingResolver` (one immutable server height from the player's root aim), `LandingHitValidationPolicy` (server-side interact/use validation against a deeply-lowered cell), `ConnectorPlacementSettle` (settles fence/wall/pane/bars connections after a placement's height is published). None does file I/O, registration, rendering or diagnostics. |
| `com/slabbed/client/model/*` | Offset block-state model, the Y-offset emitter, and the alternate chain-ceiling geometry — the lowering render path. |
| `com/slabbed/client/runtime/PistonMovingRenderScope` | Prevents nested piston models from applying the destination height twice. |
| `com/slabbed/mixin/client/*` | Client mixins declared in `slabbed.client.mixins.json`. Non-recursive on purpose. |
| `com/slabbed/mixin/torch/*` | `TorchBlockMixin` — torch attachment geometry. |

### Anchor / storage (class-level; a mixed package)

| Entry | Reason |
| --- | --- |
| `com/slabbed/anchor/SlabAnchorAttachment` | The dy anchor store — the feature's data model. Server-authoritative, persistent, synchronised; writes no file and reaches nothing outside the save. |
| `com/slabbed/anchor/ChunkPositionSetPacketCodec` | Compact per-chunk network codec for packed block positions — the anchor set's sync wire. |
| `com/slabbed/anchor/ChunkPositionDyMapPacketCodec` | Compact per-chunk network codec for the placement-height map — the store's sync wire, the sibling of the row above. Groups by 16-cubed section, stores occupancy as a non-empty-word mask, and stores heights as a per-section palette. Ported from the Fabric 1.21.11 line 2026-09-03 to replace a raw sixteen-bytes-per-height wire form that overflowed Fabric's attachment ceiling at 1,024 stored heights in one chunk; the same measurement now fits 64,000. Pure encoding: no world access, no file or network I/O of its own, no registration. |
| `com/slabbed/anchor/PlacementDyOverlay` | The client-prediction overlay that sits above the authoritative store without writing it — the shared decision logic `PlacementDyPredictionClient` (client) supplies platform wiring for. No file or network access beyond the ordinary sync path. |
| `com/slabbed/anchor/C3TestPhaseTrace` | Inert-by-default ordering sink for the placement-capture path, called from `BlockItemPlacementIntentMixin` at its two ordering-critical points. While no trace span is open — the shipped case — every call is a single boolean read and an immediate return. Production plumbing, not debug tooling, and lives in `src/main` because a `src/gametest` class is not visible to it. |

### Placement provenance policy (class-level)

| Entry | Reason |
| --- | --- |
| `com/slabbed/upgrade/WorldUpgradeDecision` | Immutable, versioned representation of one saved world's mode and backup disposition. On this line only its `Mode` enum is consumed by the placement provenance policy; it performs no file, world or network access and nothing here reads or writes a decision record. |
| `com/slabbed/upgrade/WorldUpgradeRuntimePolicy` | Loader-owned activation seam for an already-recorded world-upgrade mode. It only controls whether accepted placements receive modern provenance; it does not load worlds or import existing cells. |

### Main mixins (class-level; `com/slabbed/mixin/` is a mixed package)

Every row below is declared in `slabbed.mixins.json` **except the two marked ORPHANED**, which
compile and would ship as bytes but are not declared in any mixin config and therefore never load.
Found while authoring this file (2026-09-03); disposition (wire in with proof, or delete) is owed
before this line's behaviour work closes — see the note beneath the table.

| Entry | Reason |
| --- | --- |
| `com/slabbed/mixin/ArmorStandItemOffsetPlacementMixin` | Places armor stands against the stored visible support plane while retaining vanilla item collision and entity-data handling. |
| `com/slabbed/mixin/BlockItemPlacementIntentMixin` | Captures placement intent for the placement law. |
| `com/slabbed/mixin/BlockOnPlacedAnchorMixin` | Writes the dy anchor on placement. |
| `com/slabbed/mixin/BlockOnStateReplacedAnchorMixin` | Clears the dy anchor on state replacement; a same-shape kind change keeps it. |
| `com/slabbed/mixin/CarpetBlockMixin` | Carpet support and shape over a lowered slab. |
| `com/slabbed/mixin/FencePaneSlabConnectionMixin` | Fence and pane connection against a lowered slab. |
| `com/slabbed/mixin/HangingSignAttachedMixin` | Hanging-sign attachment from above. |
| `com/slabbed/mixin/RedstoneWireBlockMixin` | Redstone wire connection and support over lowered slabs. |
| `com/slabbed/mixin/ServerInteractBlockHitToleranceMixin` | Server-side hit tolerance for offset targeting. |
| `com/slabbed/mixin/SlabSupportBlockMixin` | Slab support surface. |
| `com/slabbed/mixin/SlabSupportStateMixin` | Slab support state. |
| `com/slabbed/mixin/BlockCollisionDepthWindowMixin` | Discovers stored collision owners through the supported depth. |
| `com/slabbed/mixin/BoatItemOffsetRaycastMixin` | Lets boat item use target stored visible block surfaces while preserving nearer vanilla block and fluid hits. |
| `com/slabbed/mixin/ItemFramePhysicalOffsetMixin` | Saves and synchronizes frame height so the physical body and rendered position agree. |
| `com/slabbed/mixin/PaintingRememberedSeatMixin` | A painting remembers the drawn face it was hung on: seat minted once, synced, saved, physical (maintainer ruling, 2026-09-13). |
| `com/slabbed/mixin/HangingSurvivalOnGridMixin` | A seated painting judges attachment on the grid cells behind it, not its seated box. |
| `com/slabbed/mixin/MinecartPhysicalOffsetMixin` | Keeps rail coordinates distinct from physical minecart movement, passengers and targeting. |
| `com/slabbed/mixin/PistonPlacementDyTransferMixin` | Carries stored heights through vanilla piston movement. |
| `com/slabbed/mixin/PistonMovingBlockDyMixin` | Preserves moving-cell heights when their final block state is installed. |
| `com/slabbed/mixin/SnowBlockStoredSupportMixin` | Preserves snow support-face checks on translated collision shapes. |
| `com/slabbed/mixin/ScaffoldingLoweredStandMixin` | Measures scaffolding's "entity is above" test against the cell's stored height so a lowered column is stood on, climbed and descended like a flush one. |
| `com/slabbed/mixin/CampfireCookingParticleMixin` | Translates cooking smoke to its stored campfire height. |
| `com/slabbed/mixin/CampfireSmokeParticleMixin` | Translates campfire smoke outside the shared display-tick scope without double-shifting ambient smoke. |
| `com/slabbed/mixin/DecoratedPotParticleMixin` | Translates pot feedback particles to the stored pot height. |
| `com/slabbed/mixin/LeverParticleMixin` | Translates lever feedback particles to the stored lever height. |
| `com/slabbed/mixin/CandleExtinguishParticleMixin` | Translates extinguish smoke to the stored candle height. |
| `com/slabbed/mixin/ParticleUtilOffsetMixin` | Normalizes shape-distributed particle height and translates its origin once to stored dy. |
| `com/slabbed/particle/*` | Shared display-particle height scope and coordinate translation. |
| `com/slabbed/mixin/TorchParticleAccessor` | Accessor supporting the torch particle mixins. |
| `com/slabbed/mixin/TorchParticleMixin` | Particle origin tracks the lowered block. |
| `com/slabbed/mixin/WallSlabConnectionMixin` | Wall connection against a lowered slab. |
| `com/slabbed/mixin/WallTorchParticleMixin` | Particle origin tracks the lowered block. |
| `com/slabbed/mixin/ChainBlockNeighborSurvivalMixin` | **ORPHANED — ships, never applies.** Adds an axis-aware support check so a chain pops when it loses support (vanilla's `AbstractBlock.canPlaceAt` is unconditionally true for chains, so today nothing enforces this). Compiles and would pass the hygiene/allowlist gates, but is not listed in `slabbed.mixins.json` or any other config, so Mixin never loads it. Approved to ship as inert bytes rather than excluded, because excluding it would hide the finding; wiring it in (or deleting it) is a behaviour decision for the placement-work phase, with its own teeth-first proof, not a hygiene-pass edit. |
| `com/slabbed/mixin/SlabBlockPlacementFixMixin` | **ORPHANED — ships, never applies.** Forces a slab placed against the top face of a lowered support to become a TOP slab instead of BOTTOM. Same disposition as above: compiles, ships as inert bytes, is not declared in any mixin config, so it currently does nothing. Its target behaviour — a slab landing at the wrong sub-type on a lowered support — is plausibly related to a cluster of open render-vs-hitbox bug reports on this line; that connection is unverified and must be traced, not assumed, before this mixin is wired in. |

### Client (class-level; `com/slabbed/client/` is a mixed package)

| Entry | Reason |
| --- | --- |
| `com/slabbed/client/ClientDy` | Client-only dy policy for visual alignment of thin carpet layers on bottom slabs. |
| `com/slabbed/client/PlacementDyPredictionClient` | Thin client wiring for `PlacementDyOverlay`: level identity, the raw backing read, targeted rerenders, and lifecycle events. Never writes the chunk's placement-dy attachment itself. |
| `com/slabbed/client/SlabAnchorClientSync` | Receives anchor sync from the server. |
| `com/slabbed/client/SlabbedModelLoadingPlugin` | Installs the offset block-state model and the alternate chain-ceiling geometry. |
| `com/slabbed/client/SlabbedClientFlags` | Sole members are `GAP_FILL` and `TARGET_DY_OVERLAY`, read by shipped client code via `getstatic`. Two constants and a private constructor; both default off (`Boolean.getBoolean`). |
| `com/slabbed/client/TargetDyOverlay` | The client debug overlay. Passive; reads `SlabbedClientFlags.TARGET_DY_OVERLAY`, default off. |

### Util (class-level; `com/slabbed/util/` is a mixed package)

| Entry | Reason |
| --- | --- |
| `com/slabbed/util/HangingSeatDyHolder` | Duck interface exposing a hung decoration's remembered seat. |
| `com/slabbed/util/SlabSupport` | Support-surface resolution and the visual Y offset — the core of the feature. |
| `com/slabbed/util/SlabbedOffsetRaycast` | Offset-aware nearest-hit raycast — the targeting overhaul. |
| `com/slabbed/util/RuntimeDiagnostics` | The release-safe diagnostics boundary: every method is gated behind a `System.getProperty` flag (default off) or an `isEnabled()` check, and is the sole caller shipped code uses to reach recording/inspection behaviour. Architecture keeping diagnostics gated, not diagnostics leaking in — analogous in role to the donor line's `SlabbedAuditBridge`, though this line has not yet consolidated onto that class; see the dev-tooling port phase. |

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

Some sibling lines run a second, independent gate over file *content* (a forbidden-token scan) that
catches an already-approved file that later grows a reference to a diagnostic seam. This line has no
such task. Recorded as port debt, not silently improvised — do not invent one under time pressure;
it wants a token set with a maintainer sign-off, the same way the sibling line's does.

## Known gap on this line: two orphaned mixins ship inert

See the two ORPHANED rows above under "Main mixins". Both compile, both pass every gate here, and
neither has ever executed on this line because neither is declared in a loaded mixin config. This
was found while authoring this file (2026-09-03), by comparing the source tree against every mixin
config in `src/main/resources` and `src/client/resources` — not by running anything. Disposition
(wire in with a teeth-first GameTest row, or delete) belongs to the behaviour-work phase, under the
same law-preflight and reachability discipline as any other placement-affecting change; it is
recorded here rather than acted on now because a hygiene pass is the wrong place to activate
previously-inert placement behaviour.

## Maintenance

- Entries that match nothing in either artifact are reported as `STALE` and printed as a warning by
  `verifyReleaseAllowlist`. They do not fail the build — a legitimate deletion should not be blocked
  — but a stale entry is how a list rots, so clear them.
- `./gradlew releaseAllowlistReport --continue` prints the full unapproved inventory and writes
  `build/reports/release-allowlist/unapproved.txt` without failing *that task*. `--continue` is
  needed only because the verify task finalizes `remapJar`/`remapSourcesJar` and will still fail the
  overall build while anything is unapproved — which is the point. The report is a review aid, not a
  bypass: there is no flag that makes `verifyReleaseAllowlist` pass.
