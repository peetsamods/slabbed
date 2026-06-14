# HANDOFF — 1.21.11 TS compat: placement fix port + WIP inventory

> Companion to `SLABBED_SPINE.md`. Update this after each session.

---

## 🚦 RELEASE GATE (read this first — applies to Codex / any pre-release check)

> **Canonical, machine-readable copy: [`RELEASE_GATE.md`](RELEASE_GATE.md) at the repo root.**

The release gate for this branch is: **headless `./gradlew runGameTest` (46/46) + live play-testing
(Julia, via the Modrinth `Slabbed+Terrain Slabs` profile) + a clean jar** (no `debug/`/`dev/` package
classes, no `*DebugBridge` / `*AuditBridge` / `*Trace` diagnostic classes, no always-on `LOGGER`
output — see `slabbed-prerelease-hygiene-gate`).

**`runClientGameTest` is NOT a release gate — do NOT block a release on it.** That 10-class
`FabricClientGameTest` harness (`Slabbed*ClientGameTest`) is unmaintained dev-repro scaffolding: it has
been broadly red since before the NEVER-POP freeze law (2026-06-13), several cases assert the obsolete
pre-freeze-law teardown contract or deliberately-BLOCKED features (e.g. bed/torch "rescue", which the
test's own comments mark "currently BLOCKED"), and it was never run during the fix work — only the
headless suite + live play were. The two genuinely-stale lowered-lane cases were reconciled
(`bf248530`); curating the rest is separate, non-release-blocking follow-up. (Decision: Julia, 2026-06-14.)

---

## 🟢 PARITY PORT — 2026-06-13 (Claude, opus): bringing 1.21.11 to "law & canon" with 1.21.1

**Root finding (Julia's live bug-hunt):** the 1.21.11 compat branch and the 1.21.1 release branch
diverged at `f4480ce4`; a whole family of 1.21.1 fixes was **never ported** here, which is why
1.21.11 still shows: invisible grass on TS, ceiling-hanger gap + pop, and placed-block down-pop.
`git merge-base --is-ancestor` confirmed `83afed84 9a24670c 9ec27ca4 efad9e79 434e7c41` are all
MISSING from this branch.

**Ported + headless-proven this session (NOT pushed):**
1. **`21af4243` — vanilla vertical-compound FLOAT** (also live-confirmed 2026-06-12; see below).
2. **`2a50335e` — ceiling-hung decorations take dy from the support ABOVE only** (port of `434e7c41`).
   Hanging roots / spore blossom / hanging signs under a flush slab no longer drop -0.5 (gap) or pop
   on neighbour break: new `isAlwaysCeilingHungDecoration` + `ceilingHungDecorationDy` (a flush TS
   slab is never a lowered support), early-dispatched in `getYOffsetInner`, excluded in `shouldOffset`.
   New gametest `hangingRootsUnderFlushSlabStayFlush`. (pale-hanging-moss helper absent here → scoped
   to roots/spore/sign.)
3. **`8aafd1ff` — NEVER-POP freeze-on-place law** (port of `9ec27ca4` + `efad9e79`). The down-pop
   ("placed slab under a floating log popped it down to flush") is the FROZEN_FLAT case. Added
   `FROZEN_FLAT_TYPE` (persistent+synced), `freezeLoweredOnPlace` (lowered→anchor, flat structural→
   FROZEN_FLAT), `isFrozenFlat`, wired into `BlockOnPlacedAnchorMixin.onPlaced`, read in
   `getYOffsetInner` before geometric lowering, mirrored in `SlabAnchorClientSync`. Two new gametests
   drive the REAL onPlaced path (`frozenFlatBlockStaysFlatWhenSlabAddedBelow` + unfrozen control).

**LIVE-CONFIRMED fixes added 2026-06-14 (Julia driving the Modrinth profile):**
4. **`bbe3deb9` — hanging lanterns hang flush** under a flush TS slab (were dropping -0.5 / gap). A
   HANGING lantern fell through the old "follow lowered support down" tail, which reads
   `getYOffsetInner(above)` — and that bypasses `shouldSkipOffset`, so it saw a flush TS slab as -0.5.
   Fix: route HANGING blocks through `ceilingHungDecorationDy` (has the guard); STANDING lanterns keep
   the rest-on-support path. **Live "green".**
5. **`6f0c73e6` — TS vegetation no longer double-offset** (invisible/sunk grass, fern, tall grass).
   ROOT (proven by instrumenting `emitQuads`): Terrain Slabs already wraps the blocks it positions in
   its own `net.countered.terrainslabs.model.SlabOffsetModel` (offsetting the model) BEFORE Slabbed's
   WRAP_PHASE, and Slabbed wrapped that and offset it a SECOND time (TS -0.5 + Slabbed -0.5 = -1.0 →
   sank into the block below). Fix: `SlabbedModelLoadingPlugin` skips wrapping a model TS already owns
   (its `SlabOffsetModel`) — TS provides the single model offset; Slabbed still drives the matching
   outline via getYOffset. Also hardened `YOffsetEmitter` to shift each quad exactly once. **Live "green".**
6. **`92516668` — GH #21 floating fence posts on VANILLA slabs.** Fence/wall/pane outline lowered
   correctly but the MODEL floated at grid height. ROOT: `OffsetBlockStateModel.emitQuads` forced
   model `dy=0` for connecting blocks unless on a named-custom (TS) surface, so vanilla-slab fences
   never got the offset their outline did. Fix: drop the suppression — connecting-block model now
   always tracks `getYOffset`; `connectingBlockVisualDy` returns the real dy for vanilla carriers too,
   so the step-detected connector-arm break (`FencePaneSlabConnectionMixin`/`WallSlabConnectionMixin`)
   still fires. 1.21.1 already inert here (Julia live-checked it → fine). New gametest
   `fenceConnectionBreaksAcrossVanillaSlabStep`. **Live "green".** + 2nd hygiene pass `c3228bb5`
   (stripped ungated `[Slabbed] AfterBake…` per-model debug logs).

**Headless: 46/46** (`./gradlew runGameTest`). Jar (`slabbed-0.4.0-beta.4.jar`, md5 50179c9d) staged
in the Modrinth `Slabbed+Terrain Slabs` profile **and** at `~/Desktop/Ready Jars/`.

**LIVE-CONFIRMED this arc:** compound float, ceiling-hanger (roots/spore/sign), hanging lantern, TS
vegetation. **Still needs Julia (right-click — computer-use can't drive onPlaced):** freeze-law live
confirm — right-click a log on the ground, then place a bottom slab under it → it must STAY put
(no down-pop). NOTE: a `/setblock`'d block is NOT frozen (onPlaced-only, by design), so test with a
hand-placed block.

**Still open (deferred, distinct work):**
- **Compound -1.0 on right-click placement** — 1.21.11's anchor reads a flat -0.5 (no compound
  sidecar yet), so a *placed* compound stack top freezes at -0.5 not -1.0 (the `21af4243` geometric
  fix only covers setBlockState/terrain). Pre-existing anchor limitation; port the 1.21.1
  `isCompoundFullBlockAnchor` sidecar for full parity.
- **VERSION/BRANCH RECONCILIATION — DONE 2026-06-14 (port + bump; pending live + publish).** Julia
  confirmed canonical = `0.4.0-beta.x`. Ported the two genuine gaps from the release line onto this
  branch (DODO world-hole `42002295` + powder-snow `85537dcd`), adapted to compat's leaner SlabSupport;
  bumped `mod_version` → **`0.4.0-beta.4`**; built+installed `slabbed-0.4.0-beta.4.jar` in the profile.
  Headless **45/45** (added `powderSnowOnSlabStaysFlush` + `naturalCubeOverSolidDoesNotLowerThroughToSlab`).
  - **LIVE-VERIFIED 2026-06-14 (Julia, "green"):** natural TS terrain shows NO see-through holes;
    powder snow stays flush; placed towers still chain. `0.4.0-beta.4` is a fully-verified RC.
  - **LEFT TO SHIP (human only):** publish — push branch + Modrinth/CF upload (Julia's explicit
    go-ahead; NOT pushed yet).
  - NOTE: `git log --cherry-pick --right-only 831983d9...release/mc1.21.11-0.4.0-beta.3` lists ~13
    other release-branch commits "not in compat", but those are the SAME TS-compat fixes implemented in
    parallel on this branch's lineage (mixed-slab -1.0, lantern follow, cantilever, break-jam — all
    present + live-tested here). DODO + powder-snow were the real functional gaps. A full behavior audit
    is possible if ever wanted, but the compat branch is the tested, working line.

---

## 🐛 BUG FOUND + FIXED — 2026-06-12 (Claude, autonomous adversarial pass)

**Ghost-window cull fix was Terrain-Slabs-ONLY — vanilla-slab / compound / cantilever steps were left unfixed (real see-through windows).** `SlabSupport.isSlabHeightStepFace` keyed on `isDirectCustomSlabSupportedObject` (which only counts a TS BOTTOM_LIKE support), so a full block lowered by a *vanilla* bottom slab (dy=-0.5) beside a flat block returned `false` → BOTH cull paths (`BlockRenderInfoCullMixin` + the `YOffsetEmitter` model path, which share this predicate) left the step face culled. **Fixed** by switching the predicate to a dy-difference `|getYOffset(self) − getYOffset(neighbour)| > ε` (covers vanilla + compound + cantilever, mirrors the 1.21.1 port; uses the same getYOffset signal the model renders with; only ever flips cull→draw; kill switch `-Dslabbed.disableStepCull`). Proven by a new failing→passing gametest `advVanillaSlabStepMustUnCull` in `SlabbedLabFixtureTest`. **40/40 headless green.** ⚠️ RENDER change — VISUAL confirm pending (do an A/B with the kill switch in the live client; a lowered cube on a *vanilla* slab beside a flat cube should have no see-through seam). Local commit only.

### ✅ BUG FIXED + LIVE-CONFIRMED — 2026-06-12 (Claude, opus, autonomous): vanilla VERTICAL compound stack FLOAT
A pure-vanilla compound stack — `bottom slab / stone / bottom slab / stone` — left the TOP stone at **dy=-0.5**, floating 0.5 above the lowered L2 slab. **FIXED to dy=-1.0 (flush).**

Root cause: 1.21.11 granted compound -1.0 only when the slab below was `isAdjacentSideSlabLowered` (side-adjacency); a slab lowered **vertically** (resting on a lowered full-block carrier) never qualified, so the object above never compounded.

Fix (`SlabSupport.java`): new recursion-safe reader `loweredBottomSlabSupportDyForCompound` — mirrors the 1.21.1 `floorTorchBottomSlabSupportDy` — detects a bottom slab lowered by anchor / mixed-vanilla-on-TS / lowered-double-slab-carrier / **vertical lowered full-block carrier** / side-adjacency. The `getYOffsetInner` `shouldOffset` compound branch now drops the object `supportDy - 0.5` (clamped to -1.0) instead of the narrow side-adjacency-only -1.0.

Proven 3 ways:
1. **Headless 40/40** — the characterization test was renamed `advVanillaCompoundStackFloatsKnownBug` → **`advVanillaCompoundStackTopMustBeFlush`** and now asserts L1=-0.5, L2=-0.5, **L3=-1.0** (would have failed at the old -0.5).
2. **5-lens adversarial review CLEAN, 0 regressions** (regression-enum / mixed-TS / recursion / DODO-holes / cull+clamp): the new reader is a strict SUPERSET of the old lowering conditions; flush slabs still return -0.5; disjoint from the directCustom lane; all walks bounded by MAX_CHAIN_DEPTH (no `getYOffset` re-entry); only ever deepens an already-lowering object (DODO-clean); `supportDy` is only ever -0.5/0.0/NaN so the -1.0 clamp can't underflow.
3. **LIVE** — Modrinth `Slabbed+Terrain Slabs` profile (`slabbed-0.2.0-beta.4.1.jar` + Sodium + terrain_slabs 3.2.0): `/slabdy` on the top stone reads `200,123,200 Stone  VANILLA · dy=-1.000 LOWERED`, and the `slab/stone/slab/stone` column renders as one continuous flush pillar (no float gap).

Status: local commit only — **NOT pushed** (Julia's standing "don't push without asking"). The shipped jar in the Modrinth profile was backed up to `_slabbed-backup-20260612/`; the patched candidate jar is the active one for continued live testing.

---

## ⚠️ STATUS UPDATE — 2026-06-11 (Claude, autonomous; supersedes the stale inventory below)

**The "uncommitted WIP" inventory below is STALE — it was all committed + pushed.** HEAD is
now **`b231debe` "finish 1.21.11 parity and model cull WIP"** (== `origin`, tree clean except
untracked `tmp/` evidence). That commit swept the exact WIP this doc lists — compound mixed-slab
lowering (−1.0), BUG1, decorative hanger follow-down, lowered-cube cull window fix, stepped
fence/pane, `/slabdy` overlay, **MODEL_PATH step-cull** (`OffsetBlockStateModel`/`YOffsetEmitter`),
dual mod-id refactor, placement-parity/side-lane + render-region crash guard — into git (the docs
were committed inside the same commit, so they describe the pre-commit state). Build is GREEN and
**headless gametests are GREEN at HEAD (`./gradlew runGameTest` → re-confirmed, now 37/37** after
the canopy invariant test added this session, `a7c20bc7`).

**What actually remains (all human-gated or render-internal):**
- **Live visual confirmation** of two open lanes: placement parity (TS slab + lowered full block +
  vanilla side slab → `dy=-0.500` flush; VB+VS vs VS+VB isolation) and the MODEL_PATH step-cull
  (step face visible, flat face not overdrawn). Code committed; acceptance is Julia's, via client.
- **"Middle pops up"**: pinned as a render-region/chunk-mesh desync (NOT a logic gap) — see the new
  `tsCanopyRowAllLowerNoMiddlePop` test + the discriminator below. Decisive next step (no code
  change): run `/slabdy` on the popped middle block. `dy=-0.500` while popped ⇒ render desync (a
  re-mesh — break+replace a neighbour / F3+A / walk away+back — snaps it down); `dy=0.000` ⇒ a real
  logic edge (inspect the support directly below: a TS slab classifying NONE, or a non-lowered full
  cube via `hasNonLoweredFullBlockSupportBelow`, SlabSupport.java ~681).
- **Version-line decision**: this repo is `0.2.0-beta.4.1`; the release holder `~/CascadeProjects/Slabbed`
  is `0.4.0-beta.3`. Pick the canonical next 1.21.11 version + which branch is the release source.
- **Release re-cut**: the shipped `0.4.0-beta.3` jar (world-hole DODO) was PULLED; published 1.21.11
  is only `0.3.0`; fix is at `42002295` in the release holder. Pure release mechanics — human only.

**Do NOT, unsupervised:** touch render internals for the middle-pop without Julia's `/slabdy`
reading; reopen the deferred shadow lane; move release tags; bump the version.

---

## Repo / branch / HEAD

- Root: `~/CascadeProjects/Slabbed-countered-compat-latest`
- Branch: `compat/mc1211-terrain-slabs-named-surface-support`
- Known-good savepoint: `908a3ea3`
- Remote: `origin/compat/mc1211-terrain-slabs-named-surface-support` (ahead 18 — not pushed past savepoint)

---

## What is committed (at 908a3ea3)

All green, live-confirmed:

- Compound mixed-slab lowering to dy=-1.0
- BUG 1: object on vanilla TOP slab capping a TS slab → compounds to -1.0 (was floating at -0.5)
- Decorative hangers follow a lowered support block down (no clip)
- Lowered-cube cull window fix at the real chunk gate (`BlockRenderInfo`)
- Stepped fence/pane connection headless coverage
- Headless compound dy matrix (BUG 1 included)

---

## Working-tree WIP (12 files modified, 3 untracked repo files + `tmp/` evidence — NOT committed)

### 1. `/slabdy` debug overlay (3 files + 1 untracked)
- `src/client/java/com/slabbed/client/TargetDyOverlay.java` — **untracked**, new dev HUD
- `src/client/java/com/slabbed/client/SlabbedClient.java` — wires `TargetDyOverlay.init()` + `/slabdy` command
- `src/client/java/com/slabbed/client/SlabbedClientFlags.java` — adds `TARGET_DY_OVERLAY` JVM flag
- `build.gradle` — forwards `-Dslabbed.targetDyOverlay` / `-Dslabbed.gapfill` from Gradle invocation to game JVM

### 2. MODEL_PATH cull WIP (3 files)
- `src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java` — step-cull-face support: unculls the lowered-step east face via `isSlabHeightStepFace`
- `src/client/java/com/slabbed/client/model/YOffsetEmitter.java` — extended with `clearCullFace` predicate so the emitter can strip `cullFace` on exposed step faces before emission
- `src/gametest/java/com/slabbed/test/TerrainSlabsProofFocus.java` — includes `slabbed.terrainSlabsLoweredCubeCullProof` in the focused proof skip list

  **Status:** cull proof is part of the commit candidate; shadow visual work is
  deferred. Julia rechecked the suspected merge issue and reported it green again.
  Do not keep iterating on shadows in this WIP unless Julia explicitly reopens the lane.

### 3. Dual mod-id refactor (2 files)
- `src/main/java/com/slabbed/compat/terrainslabs/TerrainSlabsCompat.java` — supports both `terrain_slabs` and legacy `terrainslabs` namespace; adds `isLoaded()` + `isTerrainSlabsId()` helper
- `src/main/java/com/slabbed/compat/CompatHooks.java` — drops internal `isModLoaded()` helper, uses `TerrainSlabsCompat.isLoaded()` throughout

  **Status:** compile-clean; tested implicitly by the gametest suite passing against the `terrainslabs` mod-id.

### 4. Placement parity fix (3 files)
- `src/main/java/com/slabbed/mixin/BlockItemPlacementIntentMixin.java` — side-placement on a -0.5 lowered full block now predicts the placed slab's own dy before mapping upper/lower visual half to slab type + cell
- `src/main/java/com/slabbed/util/SlabSupport.java` — bottom-like Terrain Slabs surfaces under a full block now count as lowered full-block carriers for adjacent vanilla slab side lanes; side-lane lowering is blocked when a slab already has a non-lowered full-block support underneath; render-region boundary reads for the custom-below check are guarded
- `src/gametest/java/com/slabbed/test/TerrainSlabsCompoundDyTest.java` — adds the TS+lowered full block+vanilla slab side-lane regression and the VB+VS beside VS+VB false-lowering regression

  **Note:** This was upgraded on 2026-06-10 after Julia's live parity reds. Do not revert to the older geometric-only approach; vanilla slabs beside a TS-lowered full block need side-lane dy=-0.5 parity, while a vanilla slab on its own non-lowered full block must stay dy=0 beside a lowered VS+VB lane.

  **Status:** Headless proof green (`runGameTest`: 36/36 required tests passed). World-load crash recheck is live-green per Julia after the render-region guard; placement visual acceptance still needs clean savepoint review before commit.

### 5. Gametest harness extension (1 file)
- `src/gametest/java/com/slabbed/test/TerrainSlabsDirectSupportClientGameTest.java` — adds `FaceProbe` record + `probeFacesToward` helper for MODEL_PATH cull proof; updates mod-id resolution to `loadedTerrainSlabsNamespace()` throughout

---

## Deferred lane: inappropriate shadows

**Current status:** parked. The visual shadow issue is not final-accepted, but Julia asked to defer it after confirming the merge layout is green again.

What is known:

- External research found no exact off-the-shelf mod fix for Slabbed's shifted-full-cube shadow class. SSPB/Sodium evidence points at renderer smooth-light/metadata behavior, not a broad shader toggle.
- A Slabbed-side metadata experiment proved that shifted opaque full-cube quads can
  clear original-block `cullFace`, preserve `nominalFace`, and disable AO, but that
  experiment is parked outside the commit candidate because visual shadow acceptance
  is not final.
- `diffuseShade` was intentionally left unchanged.
- The suspected "merging" regression was rechecked by Julia and is green again; do not treat `tmp/merge-regression-handoff-20260610.md` as an active red unless a fresh branch-local reproduction says otherwise.

Artifacts:

- `tmp/mc1211-inappropriate-shadows-20260610/triage.md`
- `tmp/mc1211-inappropriate-shadows-20260610/triage.json`
- `tmp/mc1211-inappropriate-shadows-20260610/cullface-metadata-proof/gradle-compileJava-compileClientJava-compileGametestJava.log`
- `tmp/mc1211-inappropriate-shadows-20260610/cullface-metadata-proof/gradle-runClientGameTest.log`
- `tmp/merge-regression-handoff-20260610.md` — superseded by Julia's live "green again" check unless a new red appears.

If reopened, the next legal gate is exactly one fresh branch-local visual repro/triage of the shadow scene from current classes. If still red, diagnose renderer light sampling/metadata. Do not jump straight to Sodium/Indigo mixins, and do not mix with placement/dy work.

---

## What was investigated and NOT ported

### Vegetation double-lower fix (d4dae6c5 on 1.21.1)
**Not applicable to 1.21.11.** The 1.21.1 fix prevents Slabbed from double-lowering vegetation that Terrain Slabs already handles. On 1.21.11, this double-lowering does not occur — TS handles vegetation natively and Slabbed does not interfere. Verified live 2026-06-10. Do NOT port this fix.

---

## Next steps

1. **Julia: live-test patched placement parity** in the branch-local client:
   - TS slab + lowered full block + vanilla slab side placement; `/slabdy` should show the vanilla slab as `dy=-0.500 LOWERED` and visually flush.
   - VB+VS beside VS+VB; the VB+VS slab should stay flush at `dy=0.000` while the VS+VB full block remains lowered.
2. If live placement parity is correct → commit as a clean placement/crash-guard slice after diff review.
3. Keep inappropriate-shadow work deferred unless Julia reopens it with a fresh visual red.
4. Live-confirm MODEL_PATH cull fix only if it remains part of the commit slice (step face visible, flat face not overdrawn).
5. Commit WIP slices in order: dual-mod-id refactor → /slabdy overlay → placement fix → MODEL_PATH cull.
6. Run `./gradlew runGameTest` (36 tests) after each slice.
