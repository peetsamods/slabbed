# HANDOFF — Slabbed MC 26.1.2 port

> Refreshed **2026-06-16** (replaces the 2026-06-15 "parity-first restart" version, now history).
> Companion to `SLABBED_SPINE.md` (append-only log) + `LIVE-DRIVE-GUIDE.md` (how to test live) +
> `RULES.md` (guardrails). Memory index: `slabbed-2612-session-20260615`.
>
> **SELF-INSTRUCTION for the next thread:** read this file top-to-bottom, then `SLABBED_SPINE.md`
> (tail) + `LIVE-DRIVE-GUIDE.md` + `RULES.md`, then **keep going** on the roadmap below. The GOAL is
> **COMPLETE PARITY with the shipped 1.21.1 AND 1.21.11 builds, WITH Terrain Slabs.** Do not stop early;
> you have full control — build, place (keybind §4 of the drive guide), live-A/B, commit. RED-verify
> every gap before porting; prove RED→GREEN. Fan-out audits CAN be wrong — validate by hand.

---

## 0. Get oriented (verify BEFORE touching anything)

```
root:    /Users/joolmac/CascadeProjects/Slabbed-port-26.1.2
branch:  port/mc-26.1.2
HEAD:    3222844a   (clean tree, local only — NEVER pushed without explicit Julia go-ahead)
MC 26.1.2 · Java 25 · Gradle 9.4.1 · Loom 1.15.5 · loader 0.19.2 · Mojang mappings · v0.2.0-beta.4+26.1.2-port
```
- **Build/test with Java 25:** `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home`
  then `./gradlew runGameTest` or `./gradlew build -x runGameTest`. (Java 21 javac can't read the MC named
  jar → "cannot access BlockState".)
- **Staged jar:** `~/Library/Application Support/ModrinthApp/profiles/Fabric 26.1.2/mods/slabbed-…port.jar`
  (currently **200367 B**). Restart the Modrinth instance to load a new build.
- **⚠️ Linked worktree:** this dir's `.git` points into `…/Slabbed/.git/worktrees/…`; all `Slabbed*`
  checkouts share ONE object store. **Work in-place here. NEVER use `isolation:'worktree'` agents** (they
  branch off the shared/wrong HEAD). **Never edit** `Slabbed-countered-compat-latest` (shipped 1.21.11),
  `Slabbed-phase19-integrate` (1.21.1), or `Slabbed/` — read-only references.
- **⚠️ KEYBIND STATE:** the test profile's `options.txt` currently has **Use rebound to `r`** (so an agent
  can place blocks live). Backup: `options.txt.slabbed-bak`. Revert before Julia plays normally:
  `sed -i '' 's/^key_key.use:key.keyboard.r$/key_key.use:key.mouse.right/' "<profile>/options.txt"` (MC stopped).

## 1. What this sprint DELIVERED (all local, on `port/mc-26.1.2`)

Forward-ported the shipped 1.21.1/1.21.11 fix families + revived an unmerged one. Every fix RED-verified
then proven RED→GREEN (gametest) and/or live. 24 gametests green.

| Fix | commit | verified |
|---|---|---|
| ghost collision (lowered movement stays vanilla) | `1ff995b1` | gametest |
| collision broadphase (lowered solid where drawn) | `3d9c49ab` | live |
| `/slabdy` HUD overlay (+ src=/half=) | `b528d734`,`6e2b5e8d` | live |
| NEVER-POP freeze-on-place law | `2845ba81` | gametest |
| ceiling-hung decorations (dy from above) | `28991e4d` | gametest |
| powder-snow never offsets | `0bc4ea84` | gametest |
| stop full-block side-inheritance + cantilever merge | `472c7b70` | gametest |
| compound merge (sidecar follows slab below) | `6cb4b909` | gametest |
| hanging-lantern smoosh (follow support) | `7507029c` | live |
| chain-ceiling extended model (revived unmerged `fix/chains-*`) | `baef1d03` | partial* |
| snap freeze-guard (side-inherited-only → flat) | `fddd248e` | **live A/B + gametest** |
| DODO step-face cull (flat-vs-lowered seam) | `ea4eddd7` | **live (solid)** |

\* chain-ceiling only triggers when a TOP/DOUBLE slab is directly ABOVE the Y-chain. Julia's windmill
chain hangs BESIDE the trunk → may not trigger; the "chain bottom → ground" geometry is still open.

**Live-verified the same day (keybind rig, jar 200367):** DODO ghost-window gone (slab renders solid all
angles); snap fix proven by an A/B at `(9,-58,5)` — `/setblock`=−0.5 geometric, `r`-placed=0.0 FROZEN-FLAT.
Julia's EXISTING snapped arm `(-2,-56,-1)` is still −0.5 = **stale anchor from the old jar** (anchors
persist; break+replace clears it). Scratch test blocks left at x=8 z=5 column + (9,-58,5) + (8,-60,12).

## 2. PARITY ROADMAP — what's still MISSING vs shipped 1.21.1/1.21.11 (+TS)

From a 5-reader parity-gap audit (2026-06-16), **hand-validated** (one reader false-negatived the
cantilever predicates — they ARE present, 8 hits). Port from the shipped siblings; RED-verify each first.

### P0 — Terrain Slabs compat (THE HEADLINE GOAL; 26.1.2 does NOT yet achieve TS parity)
1. **Dual mod-id gate** — `TerrainSlabsCompat` only checks `MOD_ID="terrainslabs"` (legacy); never the
   modern `terrain_slabs`. So a modern TS build → ALL compat hooks short-circuit false. Port commit
   `42002295` gate (`isModLoaded(MOD_ID) || isModLoaded(LEGACY_MOD_ID)` + BOTH namespace checks). BLOCKER.
2. **World-hole DODO fix** — `SlabSupport.hasSlabInColumn()` walks DOWN through ALL blocks; on TS surfaces
   it tunnels through solid terrain → see-through world holes. Port `42002295`: stop at `isOpaqueFullCube()`,
   view-independent flush, `isSlabSitCandidate` excludes opaque cubes. BLOCKER.
3. **Vegetation double-offset** — `SlabbedModelLoadingPlugin` wraps ALL FabricBlockStateModels, re-offsetting
   models TS already wrapped in its `SlabOffsetModel` (grass invisible/sunk). Port `6f0c73e6`: skip wrapping
   a model TS already owns (check `modelClass` name/package). MAJOR.
4. **`shouldSkipSlabSupport()` + `CompatSlabSurfaceKind` + named direct-support surfaces** (commits
   `4f2ee4f3` et al.) — TS-named slabs as direct support; TS blocks opt out of generic slab support. MAJOR.
5. **Get terrain_slabs INTO the test profile** — it's NOT in `Fabric 26.1.2/mods/` today, so none of this is
   live-testable. Stage the 26.1.2 terrain_slabs jar (Julia's "Countered Terrain Slabs Fabric 0.4.0" on the
   Desktop?) alongside Slabbed. ALL TS work is **live-render verify** (TS loaded).

### P1 — Connecting blocks (fence/wall/pane) — absent entirely
- `SlabSupport.connectingBlockVisualDy` + `isSteppedConnectingNeighbor` (a fence/wall/pane pair is "stepped"
  when visual dy differs). + mixins **`WallSlabConnectionMixin`** (WallShape.NONE/UP across a step) and
  **`FencePaneSlabConnectionMixin`** (clear FACING props across a step). Copy from
  `Slabbed-countered-compat-latest` (Mojang-adapt names). Without these, fences/walls/panes draw connector
  arms across slab-height steps (GH#21). gametest + live.

### P2 — Hanger underside-follow (lanterns/spore/roots beneath a LOWERED slab/full block)
- `loweredSlabUndersideSupportDy`, `loweredFullBlockUndersideSupportDy`, and the
  `isBeta35Lowered{Slab,FullBlock}UndersideVisibleOwnerObject` predicates (1.21.1 phase19; confirmed 0 hits
  here). **VERIFY FIRST** whether the lantern fix (`7507029c`, HANGING→ceilingHungDecorationDy) already
  covers this case before porting — it may overlap. MAJOR.

### P3 — Render-cull refinement + hygiene
- **`isSlabHeightStepFace` + `STEP_CULL_DISABLED`**: 26.1.2's DODO fix uses `anyMismatchedNeighborDy`
  (live-confirmed working) instead of the canonical `isSlabHeightStepFace`. Centralize for parity/precision
  (LOW priority — current fix works live). MINOR.
- **HYGIENE GATE (mandatory before any release claim):** confirmed offenders — `Beta4ManualLiveTrace.java`
  ships **10 ungated `System.out.println`** in `src/main` (`[JULIA_BETA4_MANUAL_LIVE_*]`); 2 more in
  `ServerInteractBlockHitToleranceMixin` + `BlockItemPlacementIntentMixin`; audit ~74 `LOGGER.info/warn`
  (many likely behind `SlabAnchorAttachment.TRACE` — confirm). Flag-gate or remove; `unzip -l` the jar to
  confirm no debug/dev classes. See memory `slabbed-prerelease-hygiene-gate`.

### P4 — Targeting (verify, don't assume)
- 1.21.1 has `SlabbedOffsetRaycast`; 26.1.2 instead has a large `GameRendererCrosshairRetargetMixin`
  (~3075 lines) + `LoweredSideSlabRetargeter`. A DIVERGENCE, not necessarily a bug — **live-test crosshair
  targeting on lowered/offset blocks** before porting. If mistargeting reproduces, the offset-raycast
  overhaul is the known cure (memory `slabbed-targeting-root-cause-and-overhaul`).

## 3. How to work (the loop that worked)

1. Pick a gap. **RED-verify** it's real (gametest for logic/placement, live for render/geometry — the audit
   labels each `verify: gametest|live-render|both`). Fan-out audits can be wrong → validate by hand.
2. Read the shipped impl (`git -C <sibling> show <commit>` / read the file). Mojang-adapt: World→Level,
   BlockView→BlockGetter, Identifier=`net.minecraft.resources.Identifier`, `state.contains/get`→
   `hasProperty/getValue`, `Direction.Type.HORIZONTAL`→`Direction.Plane.HORIZONTAL`, `pos.down()`→`below()`,
   `state.isSolidBlock(w,p)`→`isSolidRender()`, `setPlacedBy`/`onPlaced` (26.x uses onPlaced),
   fabric-model-loading 8.0.3 renamed `FabricBakedModelManager`→`FabricModelManager`.
3. Implement; prove RED→GREEN (gametest) and/or live A/B (`LIVE-DRIVE-GUIDE.md` — keybind place + terrain-vs-
   placed A/B is the gold standard). Build (Java 25), stage, restart, verify.
4. Commit locally (message ends `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`).
   **Never push.** Append to `SLABBED_SPINE.md`; record durable gotchas to memory.

## 4. Guardrails (also see RULES.md)
- Global slab support is product intent (full blocks anchor on slabs). Single-source through `SlabSupport`.
- Narrow `@Inject` over broad `@Redirect` on shared helpers. A placed block must NEVER autonomously pop
  (NEVER-POP). Don't break the full-block baseline lane.
- Collision: lower visual/outline/raycast ONLY; physical movement collision stays vanilla (per-state
  `getCollisionShape` must NOT follow — the 3d9c49ab broadphase mixin is the only place collision-follow
  lives, gated). Don't revive the deferred-clear/rearm approach.
- Local only; ask before push/release. Don't claim release-ready without the hygiene gate + a built/tagged jar.
