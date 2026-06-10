# HANDOFF — 1.21.11 TS compat: placement fix port + WIP inventory

> Companion to `SLABBED_SPINE.md`. Update this after each session.

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
