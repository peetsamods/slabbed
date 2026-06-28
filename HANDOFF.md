# HANDOFF — Slabbed 1.21.1 Fabric clean rebuild (2026-06-27)

## Where the work lives
- **Worktree:** `~/CascadeProjects/Slabbed-laghotfix` — branch **`claude/lag-hotfix-perf`** (NOT `claude/mc1211-clean-rebuild`; that name in older docs is stale — the HEAD/commit lineage below is authoritative).
- **Pushed:** commit `c43b6a76` + tag `save/mc1211-clean-rebuild-dripstone` are on origin (peetsamods/slabbed).
  That commit = lag fix + chain + dripstone + richer /slabdy + TS subtractive (the "dripstone push").
- **LOCAL commits on top of c43b6a76 (NOT pushed — gated on Julia's live confirmation):**
  - `1c6da070` **RED#2** — decorative followers stay geometric, never freeze a stale anchor
    (freezeLoweredOnPlace gates BOTH branches on `structural`; the "smooshed lantern on a TS top slab"
    fix). HEADLESS-PROVEN: gametest `loweredFollowerStaysGeometricNotAnchored` (vanilla BOTTOM→TOP retype).
  - `b5bd1fc9` **RED#1+#3** — TS full-cube lowering (`placedTerrainSlabBottomFullCubeDy`) + compound
    inheritance (`isTerrainSlabLoweredFullCube` wired into `hasSlabInColumn`/`slabColumnYOffset`) so
    stacked cubes don't gap and objects on a -0.5 cube don't float. Non-TS paths unchanged; TS surface
    behavior is LIVE-PENDING (no headless TS harness on 1.21.1).
- **Base:** branch is `56a98575` (origin/release/mc1.21.1-0.4.0-beta.3) + ONE squashed commit. The local
  `3dcab83c` lineage has >100MB `tmp/` logs (commit `9ec27ca4`) GitHub rejects — that's why we squashed onto
  `56a98575` and gitignored `tmp/`. NEVER push the 3dcab83c lineage directly.
- **Reference (LIVE-CONFIRMED, read via `git show <tag>:` — working trees are dirty/wrong-version):**
  NeoForge `~/CascadeProjects/Slabbed-neoforge-1.21.1-port` tags `save/neoforge-1-21-1-lantern-chain-live-confirmed`
  + `save/neoforge-1-21-1-wysiwyg-parity-green`. 1.21.11 Fabric compat `~/CascadeProjects/Slabbed-countered-compat-latest`
  (has WORKING TS client-gametests — but they need the real TS mod + the 1.21.11 client-gametest harness,
  which is BROKEN on 1.21.1, so the TS path cannot be unit-tested here; the 1.21.1 freeze/anchor + compound
  walk logic IS headless-testable via vanilla analogues, which is how RED#2 was red-proofed).
- **Live-test jar:** profile `~/Library/Application Support/ModrinthApp/profiles/Slabbed 1.21.1/mods/` —
  active = **`slabbed-1.21.1-0.4.0-beta.3-CLEAN5.jar`** (RED#2 + RED#1/#3). CLEAN4 and earlier in `mods/_ab-backup/`.
- **Build/test:** `cd ~/CascadeProjects/Slabbed-laghotfix && ./gradlew --no-daemon runGameTest` →
  "All 38 required tests passed". Jar: `./gradlew --no-daemon build -x runGameTest -x runClientGameTest`
  → `build/libs/slabbed-0.4.0-beta.3.jar`. Codex's broken WIP is the OTHER worktree
  `~/CascadeProjects/Slabbed-phase19-integrate` (branch `codex/mc1211-042-beta1-release-update`) — DISCARDED.

## DONE + live-confirmed
1. **Lag spike (GH #27/#28/#29) FIXED** — per-block `Class.forName` on a release-EXCLUDED class
   (`ModelDyTranslateTraceBridge`) in the render loop → ClassNotFoundException storm, added by "hygiene"
   commit `098769f8`. Fix: `RuntimeDiagnostics` negative-cache + `BETA4_MODEL_DY_RECORDER` gate;
   `OffsetBlockStateModel` cached trace flags + recordMc1211 early-return; `SlabAnchorAttachment` cached flag;
   `SlabSupport.isSlabHeightStepFace` self-dy overload. Microbench 3048ns/call → gone.
2. **Chain gap + visual triad FIXED** — elongated chain model at dy=0: `ChainCeilingGeometry.java` +
   `assets/slabbed/models/block/chain_ceiling_support.json` + render hook in `OffsetBlockStateModel` +
   `BlockModelDyTranslateMixin` bypass + `ceilingBridgedVerticalChainSelectionShape` outline union.
   Fabric model API here = `ModelLoadingPlugin.Context.addModels(Identifier)` + `FabricBakedModelManager.getModel(Identifier)`.
3. **Dripstone combine FIXED (Julia confirmed)** — `ServerInteractBlockHitToleranceMixin` shift gate includes
   lowered pointed dripstone (`SlabSupport.isLoweredPointedDripstoneServerHitTarget`).
4. **TS subtractive + dual mod-id** (`terrain_slabs` + legacy `terrainslabs`) — no world holes; TS slab itself dy=0.
5. **Richer /slabdy** — `TargetDyOverlay` prints 4 lines: pos/name; source/dy/status/side/half;
   src=(FROZEN-FLAT|ANCHORED|compound-side|geometric); below=<block> type=<slabtype> belowDy=<dy>.

## TS object/full-block lowering — RED#1/#2/#3 FIXED in code (CLEAN5), live-pending
Architecture: `directCustom` lane in `getYOffsetInner` (LATE, after cantilever, before shouldOffset) lowers
OBJECTS (non-full-cubes) onto TS BOTTOM_LIKE surfaces; `isSlabSitCandidate` uses VIEW-INDEPENDENT
`isOpaqueFullCubeViewIndependent` (`isOpaque() && isFullCube(EmptyBlockView.INSTANCE, ORIGIN)`) — NEVER
`isSolidBlock(world,pos)` (DODO anti-pattern). `customSlabSurfaceKind` → BOTTOM_LIKE for TS type=BOTTOM and
DOUBLE+generated. `placedTerrainSlabBottomFullCubeDy` (in `getYOffset`, INSIDE the IN_GET_Y_OFFSET recursion
guard) lowers a PLACED full cube -0.5 on a placed TS type=BOTTOM (generated!=true) slab.

### FIXED (committed; live-pending Julia's confirmation)
- **RED#2 — object on TS TOP wrongly lowered (the "smooshed lantern")** — `1c6da070`. ROOT CAUSE: not the
  directCustom lane (it correctly excludes TS TOP) but `freezeLoweredOnPlace` anchoring decorative followers
  — outside the anchor scope ("no torch interaction"). A follower lowered onto a TS BOTTOM slab was frozen at
  -0.5; when the TS surface later changed to a flush TOP, the STALE anchor kept it smooshed. Fix: gate BOTH
  freeze branches on `structural` (ordinary full block || slab). Followers stay geometric → on a TOP/flush
  surface they recompute to 0. HEADLESS-PROVEN (`loweredFollowerStaysGeometricNotAnchored`, vanilla BOTTOM→TOP).
  NOTE: pre-existing stale follower anchors in an OLD world heal only on re-place (the gate stops NEW ones).
- **RED#1 + RED#3 — full-cube stack gaps / object-on-lowered-cube floats** — `b5bd1fc9`. ROOT CAUSE: the
  full-cube lane lowers a cube to -0.5 but leaves it frozen-flat / un-anchored, so the compound walk
  (`hasSlabInColumn`/`slabColumnYOffset`) never recognised it as a lowering support. Fix: new recursion-safe
  `isTerrainSlabLoweredFullCube` predicate wired into both walks → a TS-lowered cube carries its -0.5 up the
  column uniformly (stacks stay flush; dripstone/object on a -0.5 cube inherits the drop). Non-TS paths
  unchanged. NOT headless-testable (needs the real TS mod); LIVE-PENDING.

### STILL OPEN
4. **TS prismarine cantilever renders FULL/odd** — img2: `34,-56,16 Terrain Prismarine Slab dy=0.000 flush
   below=Air` renders as a full cube. Slabbed reports dy=0 (correct, subtractive), so this looks like a
   TS-side model/cull issue (generated-double vs placed-bottom render), NOT a Slabbed dy bug. DEFERRED —
   needs live diagnosis; may be out of Slabbed scope.

WORLD-HOLE check (Julia accepted the risk for full cubes on placed TS bottom slabs): NOT yet confirmed clean —
verify no see-through holes around TS builds AND natural terrain (generated) never lowered.

## NEXT (one RED at a time, red-proof first — Julia's law; do NOT bundle)
LIVE-VALIDATE CLEAN5 (RED#1/#2/#3) with Julia, then push c43b6a76..b5bd1fc9 (+tag) if confirmed. Then:
D) #4 TS-side cantilever render. Older notes:
A) #2 (objects on TS TOP wrongly lowered — likely a regression, smallest). B) #3 (compound inheritance on
lowered supports). C) #1 (full-block compound stack). D) world-hole confirm. Then commit + push the TS fix.

## Live rig (CRITICAL)
Modrinth game = bare `java` (bundleID null) → computer-use CANNOT see/drive it; osascript keystrokes don't land.
Read state via `screencapture` (downscale frames <=900px — image API many-image limit) + profile `logs/latest.log`.
Movement/placement must be Julia; she reports /slabdy. Memory: [[slabbed-1211-parity-backport-diagnosis]],
[[slabbed-1211-lag-cnfe-render-storm]].
