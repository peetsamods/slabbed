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
HEAD:    dbf5215d   (clean tree, local only — NEVER pushed without explicit Julia go-ahead)
MC 26.1.2 · Java 25 · Gradle 9.4.1 · Loom 1.15.5 · loader 0.19.2 · Mojang mappings · v0.2.0-beta.4+26.1.2-port
```
> **2026-06-16 overnight sprint (autonomous, Julia asleep):** delivered P1 connecting-blocks
> (`409bf519`), P0.1 dual-mod-id TS gate (`b76cccba`), P3 hygiene (`bcfdff7b`,`dbf5215d`). 32 gametests
> green. Jar **207832 B** built+staged. **Keybind REVERTED to vanilla right-click** (so Julia plays
> normally); to live-drive again, re-rebind per LIVE-DRIVE-GUIDE §4. P0.2–P0.4 (TS resolver wiring)
> remain BLOCKED on a 26.1.2 `terrain_slabs` jar — see §2 P0 for the exact call-site map.
- **Build/test with Java 25:** `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home`
  then `./gradlew runGameTest` or `./gradlew build -x runGameTest`. (Java 21 javac can't read the MC named
  jar → "cannot access BlockState".)
- **Staged jar:** `~/Library/Application Support/ModrinthApp/profiles/Fabric 26.1.2/mods/slabbed-…port.jar`
  (currently **200367 B**). Restart the Modrinth instance to load a new build.
- **⚠️ Linked worktree:** this dir's `.git` points into `…/Slabbed/.git/worktrees/…`; all `Slabbed*`
  checkouts share ONE object store. **Work in-place here. NEVER use `isolation:'worktree'` agents** (they
  branch off the shared/wrong HEAD). **Never edit** `Slabbed-countered-compat-latest` (shipped 1.21.11),
  `Slabbed-phase19-integrate` (1.21.1), or `Slabbed/` — read-only references.
- **⚠️ KEYBIND STATE:** as of 2026-06-16 overnight the Use keybind is **REVERTED to vanilla
  `key.mouse.right`** — Julia can play normally. To live-drive again (place via keystroke), re-rebind with
  MC stopped: `sed -i '' 's/^key_key.use:key.mouse.right$/key_key.use:key.keyboard.r/' "<profile>/options.txt"`
  then press `r` to place (LIVE-DRIVE-GUIDE §4). Backups: `options.txt.slabbed-bak`,
  `mods/.slabbed-staged-200367.jar.bak` (the prior staged jar).

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
| **P1** connecting blocks break-across-step (fence/pane/wall) | `409bf519` | gametest (×4) |
| **P0.1** dual mod-id Terrain Slabs gate + compat surface | `b76cccba` | compile + no-regression* |
| **P3** hygiene: gate 2 always-on emitters, drop empty mixin | `bcfdff7b`,`dbf5215d` | gametest + jar `unzip -l` |

\* P0.1 is subtractive-only: with no TS mod loaded every hook is a no-op, so the non-TS path is unchanged
(32/32 gametests). The TS-loaded branch CANNOT be verified on 26.1.2 until a 26.1.2 `terrain_slabs` jar
exists — see §2 P0.

\* chain-ceiling only triggers when a TOP/DOUBLE slab is directly ABOVE the Y-chain. Julia's windmill
chain hangs BESIDE the trunk → may not trigger; the "chain bottom → ground" geometry is still open.

**Live-verified the same day (keybind rig, jar 200367):** DODO ghost-window gone (slab renders solid all
angles); snap fix proven by an A/B at `(9,-58,5)` — `/setblock`=−0.5 geometric, `r`-placed=0.0 FROZEN-FLAT.
Julia's EXISTING snapped arm `(-2,-56,-1)` is still −0.5 = **stale anchor from the old jar** (anchors
persist; break+replace clears it). Scratch test blocks left at x=8 z=5 column + (9,-58,5) + (8,-60,12).

## 2. PARITY ROADMAP — what's still MISSING vs shipped 1.21.1/1.21.11 (+TS)

From a 5-reader parity-gap audit (2026-06-16), **hand-validated** (one reader false-negatived the
cantilever predicates — they ARE present, 8 hits). Port from the shipped siblings; RED-verify each first.

### P0 — Terrain Slabs compat (THE HEADLINE GOAL) — P0.1 DONE, P0.2–P0.4 BLOCKED on a 26.1.2 TS jar
**✅ P0.1 DONE (`b76cccba`):** dual mod-id gate. `TerrainSlabsCompat` rewritten in Mojang mappings (was a
Yarn-mapped, build-excluded stub); `LOADED` + every namespace check accept BOTH `terrain_slabs` and legacy
`terrainslabs`; `CompatHooks` dispatches shouldSkipOffset/shouldSkipSlabSupport/customSlabSurfaceKind; build
exclusion dropped; `CompatSlabSurfaceKind` added. Subtractive-only → non-TS path unchanged (32/32).

**⛔ P0.2–P0.4 BLOCKED — need a 26.1.2 `terrain_slabs` jar before doing them (do NOT port blind):** the only
TS jars on disk are `terrain_slabs-fabric-3.1.2`(1.21.1) / `3.3.0`(1.21.11). The remaining work is TS-gated
so it CANNOT be gametested (no TS in the test env) NOR live-verified here — and it must be woven into a
resolver that has DIVERGED from 1.21.11. Porting it blind risks the live-proven baseline and violates
"RED-verify / prove RED→GREEN". When the jar exists, port these (1.21.11 `Slabbed-countered-compat-latest`
shipped sites in parens; map to 26.1.2's divergent `SlabSupport`):
   - **World-hole core (the actual fix):** TS slabs are mis-classified as vanilla bottom slabs in the column
     walk. In 1.21.11 the walks treat `CompatHooks.customSlabSurfaceKind(cur)==BOTTOM_LIKE` as the found-slab
     (lines ~1234,1271) and `shouldSkipSlabSupport(state)` opts TS blocks out (line ~87). Wire the same into
     26.1.2 `hasSlabInColumn`/`slabColumnYOffset` (~2151/2173) and the slab-below checks.
   - **Cube-stop in the column walks** (1.21.11 ~1249,1279: `if (cur.isOpaqueFullCube()) return false/0.0`).
     26.1.2 equivalent = `cur.isSolidRender()` (no-arg, view-independent — 26.1.2 already uses it for the
     ceiling pin at line 1904 and `isSlabSitCandidate` line 2129, so parts 2&3 of `42002295` are ALREADY
     equivalent here; only the walk-stop is missing). NOTE: alone it does NOT fix the TS hole (a TS slab is
     not a solid cube) and it CHANGES non-TS unanchored-stack behaviour (anchor-first ordering preserves
     placed towers) — so do it WITH the TS jar so the end-to-end can be live-confirmed, not standalone.
   - **Vegetation double-offset** (`6f0c73e6`): `SlabbedModelLoadingPlugin` skip-wraps a model TS already
     owns (its `SlabOffsetModel`). Needs the TS jar to repro the invisible/sunk grass.
   - **Named direct-support** (`4f2ee4f3`): `customSlabSurfaceKind==BOTTOM_LIKE` lets curated objects sit on
     named TS surfaces (1.21.11 lines ~192,205,642). The reader methods are already ported (P0.1) — only the
     SlabSupport call-sites remain.

### P1 — Connecting blocks (fence/wall/pane) — ✅ DONE (`409bf519`)
- `FencePaneSlabConnectionMixin` (@Mixin{FenceBlock,IronBarsBlock}) + `WallSlabConnectionMixin`
  (@Mixin WallBlock), both registered in `slabbed.mixins.json`. Break the connection across a slab-height
  step in `getStateForPlacement` + `updateShape`(RETURN). **Gotchas found:** the real placement method is
  **`getStateForPlacement`** (NOT `getPlacementState` — the dead `SlabBlockPlacementFixMixin` uses the wrong
  name and isn't even registered); `WallBlock.PROPERTY_BY_DIRECTION` (Map<Direction,EnumProperty<WallSide>>)
  AND `CrossCollisionBlock.PROPERTY_BY_DIRECTION` both EXIST in 26.x (no manual switch needed);
  `WallBlock.updateShape` is overloaded → target by full descriptor. Proven by 4 gametests driving the real
  `updateShape` path (stepped fence/bars/wall break; flat fence connects). Break is a blockstate change, so
  gametest is authoritative. Optional remaining: live confirm a fence run down a slab stair = single posts.

### P2 — Hanger underside-follow (lanterns/spore/roots beneath a LOWERED slab/full block)
- `loweredSlabUndersideSupportDy`, `loweredFullBlockUndersideSupportDy`, and the
  `isBeta35Lowered{Slab,FullBlock}UndersideVisibleOwnerObject` predicates (1.21.1 phase19; confirmed 0 hits
  here). **VERIFY FIRST** whether the lantern fix (`7507029c`, HANGING→ceilingHungDecorationDy) already
  covers this case before porting — it may overlap. MAJOR.

### P3 — Render-cull refinement + hygiene
- **`isSlabHeightStepFace` + `STEP_CULL_DISABLED`**: 26.1.2's DODO fix uses `anyMismatchedNeighborDy`
  (live-confirmed working) instead of the canonical `isSlabHeightStepFace`. Centralize for parity/precision
  (LOW priority — current fix works live). MINOR.
- **✅ HYGIENE GATE DONE (`bcfdff7b`,`dbf5215d`):** audited all ~60 LOGGER + System.out in shipping
  `src/main`+`src/client`. Only TWO were genuinely always-on: `RedstoneWireBlockMixin` (removed an
  unconditional `LOGGER.info` per redstone canSurvive) and `ServerInteractBlockHitToleranceMixin` (the
  `REPEAT_SEAM_TRACE_OPT_IN` flag existed but 2 `[JULIA_BETA4_*]` prints didn't check it → now gated). Also
  deleted the empty no-op `Beta35FenceWallLiveInspectTickMixin` (was injecting Minecraft.tick for nothing).
  Everything else already behind opt-in gates (`SlabAnchorAttachment.TRACE`/`beta4*Enabled`,
  `OffsetBlockStateModel.CULL_TRACE`, GameRenderer `Boolean.getBoolean` recorders, BlockItemPlacementIntent
  getBoolean). **CORRECTION to the old note:** `Beta4ManualLiveTrace.java` is **build-EXCLUDED**
  (`sourceSets.main.java exclude`), so its prints never ship — moot. Jar `unzip -l` confirms NO debug/dev
  classes. Residual jar bloat (gametest-only, gated, harmless): `LiveCursorIntentRecorder` +
  `LevelRendererRenderedOutlineRecorderMixin` (~28KB) — exclude from the runtime jar at release time.

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
