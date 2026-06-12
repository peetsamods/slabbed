# HANDOFF — 1.21.11 TS compat: placement fix port + WIP inventory

> Companion to `SLABBED_SPINE.md`. Update this after each session.

---

## 🐛 BUG FOUND + FIXED — 2026-06-12 (Claude, autonomous adversarial pass)

**Ghost-window cull fix was Terrain-Slabs-ONLY — vanilla-slab / compound / cantilever steps were left unfixed (real see-through windows).** `SlabSupport.isSlabHeightStepFace` keyed on `isDirectCustomSlabSupportedObject` (which only counts a TS BOTTOM_LIKE support), so a full block lowered by a *vanilla* bottom slab (dy=-0.5) beside a flat block returned `false` → BOTH cull paths (`BlockRenderInfoCullMixin` + the `YOffsetEmitter` model path, which share this predicate) left the step face culled. **Fixed** by switching the predicate to a dy-difference `|getYOffset(self) − getYOffset(neighbour)| > ε` (covers vanilla + compound + cantilever, mirrors the 1.21.1 port; uses the same getYOffset signal the model renders with; only ever flips cull→draw; kill switch `-Dslabbed.disableStepCull`). Proven by a new failing→passing gametest `advVanillaSlabStepMustUnCull` in `SlabbedLabFixtureTest`. **40/40 headless green.** ⚠️ RENDER change — VISUAL confirm pending (do an A/B with the kill switch in the live client; a lowered cube on a *vanilla* slab beside a flat cube should have no see-through seam). Local commit only.

### 🐛 BUG FOUND — DEFERRED (needs Julia): vanilla VERTICAL compound stack FLOATS
A pure-vanilla compound stack — `bottom slab / stone / bottom slab / stone` — leaves the TOP stone at **dy=-0.5**, so it **floats 0.5 above** the lowered L2 slab. Correct value is **-1.0** (flush); **1.21.1 produces -1.0 here** (its `advCompoundStackTopMustBeFlush` passes). Root cause: 1.21.11 grants compound -1.0 only when the slab below is `isAdjacentSideSlabLowered` (side-adjacency, `SlabSupport.java ~775`); a slab lowered **vertically** (resting on a lowered full block) is not side-adjacent-lowered, so the block above never compounds. **Not fixed** — it's a core dy-resolution change (port the 1.21.1 vertical-compound handling) + needs a live visual confirm, too risky to land unsupervised. Tracked by characterization gametest `advVanillaCompoundStackFloatsKnownBug` (asserts the current -0.5; FLIP to -1.0 when fixed). Reference fix = the 1.21.1 `getYOffsetInner` compound branch.

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
