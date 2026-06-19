# HANDOFF — Slabbed MC 26.1.2 port

> Refreshed **2026-06-16** (replaces the 2026-06-15 "parity-first restart" version, now history).
> Companion to `SLABBED_SPINE.md` (append-only log) + `docs/lessons/LESSONS_INDEX.md` (durable lessons) +
> `docs/porting/PORTING_MAP.md` (port/backport map) + `docs/process/*` (proof checklists) +
> `LIVE-DRIVE-GUIDE.md` (how to test live) + `RULES.md` (guardrails). Memory index:
> `slabbed-2612-session-20260615`.
>
> **SELF-INSTRUCTION for the next thread:** read this file top-to-bottom, then `SLABBED_SPINE.md`
> (tail) + `docs/lessons/LESSONS_INDEX.md` + `docs/porting/PORTING_MAP.md`; for live/client proof also read
> `docs/process/LIVE_DRIVE_PREFLIGHT.md`, `docs/process/FALSE_GREEN_CHECKLIST.md`, `docs/process/RELEASE_SANITY_CHECKLIST.md`, and `LIVE-DRIVE-GUIDE.md`.
> Then **keep going** on the roadmap below. The GOAL is
> **COMPLETE PARITY with the shipped 1.21.1 AND 1.21.11 builds, WITH Terrain Slabs.** Do not stop early;
> you have full control — build, place (keybind §4 of the drive guide), live-A/B, commit. RED-verify
> every gap before porting; prove RED→GREEN. Fan-out audits CAN be wrong — validate by hand.

---

## 0. Get oriented (verify BEFORE touching anything)

```
root:    /Users/joolmac/CascadeProjects/Slabbed-port-26.1.2
branch:  port/mc-26.1.2
HEAD:    3ef254e2   (clean tree, local only — NEVER pushed without explicit Julia go-ahead)
MC 26.1.2 · Java 25 · Gradle 9.4.1 · Loom 1.15.5 · loader 0.19.2 · Mojang mappings · v0.2.0-beta.4+26.1.2-port
```
> **2026-06-17 PM — WYSIWYG placement law (active work).** Julia's LAW: "a placed block sits exactly where
> the crosshair aimed; there is NO case where you aim and it lands elsewhere." She reported placement bugs
> (place against a lowered block's side → snaps to vanilla; place on a terrain slab → snaps down). An 8-agent
> background audit mapped them to **4 root causes** (full report: `docs/porting/WYSIWYG-PLACEMENT-AUDIT.md`):
> - **RC1 ✅ DONE+verified (`6ba27925`):** block-on-TS-slab snap-down — `hasBottomSlabBelow` anchored onto TS
>   slabs server-side while client read flush. Fixed with a `shouldSkipSlabSupport` guard. (= Julia Problem #1.)
> - **RC2 ✅ DONE+gametest-proven (`e67bbc6e` base + `dc4bec2d` gaps); base LIVE-confirmed:** cantilever
>   slab/fence/wall/bars beside a lowered block now gets a geometric merge dy. **Fixed the GEOMETRY in
>   `getYOffsetInner`, not the anchor** (an anchor still snaps: client predicts 0, server anchors −0.5).
>   Base = 3 air-gated clauses (RC2-A/B/C). **Adversarial review** (`docs/porting/RC2-ADVERSARIAL-REVIEW.md`)
>   found 2 HIGH gaps = Julia's "lands too high" one config deeper, **now fixed (`dc4bec2d`):** GAP-1 (read the
>   neighbour's true magnitude → −1.0 beside a compound stack, not hardcoded −0.5; new `loweredFullBlockMagnitude`/
>   `loweredSlabMagnitude` + bool→double on the source scan & BFS + the anchored read-back branches), GAP-2 (a
>   BARE single lowered slab is now a valid source → −0.5 not 0.0). Recursion-safe, MAX_CHAIN_DEPTH-bounded, every
>   clause air-gated (NEVER-POP rail). **46/46** gametests (+6 onPlaced). **Gap live-verify pending** (steps in
>   `RC2-ADVERSARIAL-REVIEW.md` / memory). GAP-3 (FB-to-FB / mixed fence chains beside compound) deferred.
> - **RC3 ⏳ NEXT (compound −1.0 side markers):** two remap reasons (`COMPOUND_BELOW_LANE_SIDE_SLAB`,
>   `COMPOUND_SUPPORT_MISSING_VISIBLE_OWNER_SIDE_SLAB`) never author their intent marker in
>   `BlockItemPlacementIntentMixin` (~:1128) + a TOP/BOTTOM midline split bug. **RC4 (cantilever-FB on-top edge):**
>   queued, see audit doc.
>
> **2026-06-18 LIVE SESSION (drove the game; Julia "cruise control").** Two new bugs on the `dc4bec2d` jar+TS:
> - **BUG A "nothing is lowering to TS" ✅ FIXED + LIVE-CONFIRMED (`961249cc`):** the **P0.4** curated-object lowering
>   onto named TS BOTTOM_LIKE surfaces was **never wired** (`0bd265dc`'s own msg: "P0.4 is not wired"); `customSlabSurfaceKind`
>   was dead code. Ported the shipped 1.21.11 `directCustom` path (early-dispatch in `getYOffsetInner`, GEOMETRIC). LIVE:
>   crafting table on a TS `rooted_dirt_slab` reads **-0.500 LOWERED**; STONE on a TS slab stays **0.000 flush** (world-hole
>   P0.2 preserved via `isSlabSitCandidate`). No-op without TS.
> - **BUG B "upper-half aim places slab wrong" ⏳ FIXED (`3ef254e2`), live GREEN-verify PENDING:** live RED = slab aimed at
>   the upper half of a -0.5 lowered block placed at **-1.000** (overshoot). Root: `isCompoundVisibleSideUpper/LowerHit`
>   (SlabSupport, used only in `findLegalCompoundSlabRemap`) fired for ANY lowered source → compound-visible marker → -1.0.
>   Fixed by gating both on `sourceDy ≈ -1.0`. 46/46. **Julia to confirm:** right-click a slab against the upper half of a
>   -0.5 lowered block → reads -0.5 not -1.0. (Distinct from RC3's -1.0-source UNDERshoot "+0.5 above".)
>
> **Live rig:** game CLOSED; current jar (`3ef254e2`, 210127 B) STAGED in the `TEST_ SLABBED 26.1.2` Modrinth profile
> (prior jars backed up to `.jar-backups/`). **keybind REVERTED to `mouse.right`** (Julia can play). Test blocks left in the
> world as proof (bug-A demo ~`90-92,66,50`; bug-B stone `110,101,50` floating -0.5). **Live-rig lessons:** Escape is NOT
> delivered to MC → open the Game Menu by switching focus to Modrinth & back, then rebind keys via Options→Controls→Key Binds
> with NO relaunch (Reset restores Right Button); F11 = macOS Show-Desktop not MC-fullscreen; `/setblock` test blocks inherit
> stale anchor/compound markers (use FRESH positions). Debug placement trace = system prop `slabbed.beta4RepeatMergeTrace`
> (BlockItemPlacementIntentMixin) — logs incoming hit-Y/dy + outgoing remap + placed dy to latest.log; used to
> pinpoint RC2. **NOTE: `Slabbed2612LoweringContractTest.java` was reverting under concurrent edits — coordinate
> with Julia before adding RC2 gametests.** RC2/RC3/RC4 have ZERO headless coverage (the SlabbedLab tests are
> Yarn-named, excluded) — add Mojang-named `onPlaced`-driven tests for each before declaring closed.
> **2026-06-16/17 sprint (autonomous):** P1 connecting-blocks (`409bf519`), P0.1 dual-mod-id TS gate
> (`b76cccba`), P3 hygiene (`bcfdff7b`,`dbf5215d`), P2 verified-closed (`7a9d9f01`). **THEN Julia provided
> a 26.1.2 Terrain Slabs build** (`terrain_slabs-fabric-3.3.1`, profile `TEST_ SLABBED 26.1.2`), unblocking
> live TS work → **two showstoppers found + fixed + LIVE-CONFIRMED:** render-region crash (`4d758fe8`, the
> mod crashed loading ANY fresh world) and the TS world-hole (`0bd265dc`, terrain now flush on TS). 29
> gametests green. Jar **208081 B** built+staged to BOTH profiles. **Keybind REVERTED to vanilla
> right-click** in both profiles. See §2 P0 for what's done vs still-optional (P0.4).
- **Build/test with Java 25 — use `./gradlew25` (committed wrapper):** plain `./gradlew` uses your shell
  default (Java 21 here) and FAILS with "cannot access BlockState / class file has wrong version 69.0,
  should be 65.0" — the MC named jar is class version 69 (Java 25). `./gradlew25` resolves JDK 25 via
  `/usr/libexec/java_home -v 25` and forwards args: `./gradlew25 runClient`, `./gradlew25 runGameTest`,
  `./gradlew25 build -x runGameTest`. (Equivalent manual form:
  `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home` then `./gradlew …`.)
- **Staged jar (208081 B)** in TWO Modrinth profiles: `Fabric 26.1.2` (no TS) and **`TEST_ SLABBED 26.1.2`
  (Slabbed + Terrain Slabs 3.3.1 + architectury + fabric-api — the LIVE TS TEST RIG Julia provided)**.
  Restart the Modrinth instance to load a new build. The 26.1.2 `terrain_slabs` jar that was the P0 blocker
  now EXISTS at `…/TEST_ SLABBED 26.1.2/mods/terrain_slabs-fabric-3.3.1.jar` (mod-id `terrain_slabs`,
  model class `net.countered.terrainslabs.fabric.model.SlabOffsetModel`).
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

### P0 — Terrain Slabs compat (THE HEADLINE GOAL) — CRITICAL PATH DONE + LIVE-CONFIRMED
Live-verified 2026-06-17 with **Terrain Slabs 3.3.1** in the `TEST_ SLABBED 26.1.2` profile (rig Julia gave).
- **✅ render-region CRASH (`4d758fe8`) — SHOWSTOPPER, LIVE-CONFIRMED.** The mod crashed loading ANY fresh
  world (AIOOBE tesselating ordinary stone at a render-region edge): SlabSupport's wide column/side-support
  reads exceed the bounds-limited `RenderSectionRegion`, which THROWS on OOB in 26.x (older MC clamped to
  air — why the sibling never needed this). Fix = treat OOB model-path reads as dy=0 in
  `OffsetBlockStateModel` (`slabbed$modelDy` + `slabbed$neighborModelDy` + the ChainCeilingGeometry probe).
  NOT TS-specific — crashed in any world; just surfaced first in the fresh TS world. World loads now.
- **✅ P0.1 dual mod-id gate (`b76cccba`)** + **✅ P0.2 world-hole (`0bd265dc`) — LIVE RED→GREEN.** A
  `minecraft:stone` on a `terrain_slabs:grass_slab` read `dy=-0.500 LOWERED` (the hole); after the fix
  `dy=0.000 flush`. Root: 26.1.2 `isBottomSlab()` returns true for a TS slab (extends SlabBlock), so the
  column walk lowered terrain onto it. Fix = terminate `hasSlabInColumn`→false / `slabColumnYOffset`→0.0 at
  any TS block via `CompatHooks.shouldSkipSlabSupport(cur)` (a TS slab is a self-rendering surface; rest
  terrain flush on it). No-op without TS. Natural TS sand/badlands terraces render solid (no holes), TS
  grass_slab top renders correctly.
- **✅ P0.3 vegetation double-offset — VERIFIED NOT AN ISSUE in 26.1.2 (no port).** The plugin DOES
  double-wrap TS's `SlabOffsetModel`, but P0.2's column-walk TS-guard makes Slabbed's getYOffset return 0
  for blocks on TS, so there is no second offset — placed short-grass and natural TS vegetation render at
  correct height live. The sibling's `6f0c73e6` model-skip (skip wrapping a `SlabOffsetModel` whose package
  contains `terrainslabs` — matches TS 3.3.1's `net.countered.terrainslabs.fabric.model.SlabOffsetModel`)
  is a DEFENSIVE option if a vegetation issue ever surfaces; not ported (no RED).
- **⏳ P0.4 named direct-support (OPTIONAL, not release-critical):** lets CURATED objects (logs, etc.) lower
  onto named TS `BOTTOM_LIKE` surfaces to form combined slabs (1.21.11 `4f2ee4f3`, lines ~192,205,642). The
  reader methods are ported (P0.1); only the SlabSupport call-sites remain. With P0.2, curated objects now
  rest FLUSH on TS instead of lowering — acceptable; this is polish, do it WITH live A/B if pursued.

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

### P2 — Hanger underside-follow — ✅ VERIFIED ALREADY COVERED (`7a9d9f01`, no port needed)
- RED-verified 2026-06-16: the lantern fix (`7507029c`) unified ALL ceiling-hung dy through
  `ceilingHungDecorationDy`, which reads the support ABOVE and follows its dy. The always-ceiling-hung
  family (hanging roots/spore/sign) under a LOWERED support follows it to -0.5 (the roots-droop/sign-smoosh
  case) — proven by new gametest `hangingRootsFollowLoweredSupportAbove` (29/29). The 1.21.1 underside
  readers (`loweredSlabUndersideSupportDy` et al., 0 hits here) are NOT needed — porting them would be
  redundant with the cleaner unified approach. CLOSED.

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

### P4 — Targeting (verify, don't assume) — NEEDS A LIVE SESSION (not doable overnight)
- 1.21.1 has `SlabbedOffsetRaycast`; 26.1.2 instead has a large `GameRendererCrosshairRetargetMixin`
  (~3075 lines) + `LoweredSideSlabRetargeter`. A DIVERGENCE, not necessarily a bug — **live-test crosshair
  targeting on lowered/offset blocks** before porting. If mistargeting reproduces, the offset-raycast
  overhaul is the known cure (memory `slabbed-targeting-root-cause-and-overhaul`). Deferred from the
  2026-06-16 overnight sprint because it requires live verification (keybind was reverted; do not port the
  ~3k-line overhaul blind). Pick this up in a live session with Julia: aim at lowered/offset blocks via
  `/tp`, read `/slabdy`, check the crosshair targets the visually-correct block.

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

### STANDARD GATE — before calling any version/port "done" (RULES.md §19)
Run `docs/process/RELEASE_SANITY_CHECKLIST.md`, methodically, in this order — every time you bump a
version, cut/stage a release, or finish a port slice:
1. **Lane 1:** `./gradlew runGameTest` (Java 25). The dy **fingerprint** must be GREEN
   (`Slabbed2612DyFingerprintTest` asserts 19 fixtures; `src/gametest/resources/dy-baseline.txt` is the
   committed capture). A red fingerprint line = a behavior change → treat as a regression until proven
   intentional. **Compare versions** by grepping `SLABBED-FP` on old vs new jars and diffing (checklist §3.1).
2. **Lane 2:** live dy-cruise smoke set (§2) — RED stops the release before the full matrix.
3. **Lane 3:** Julia's eye on VIS/FEEL/N/A rows — especially §R entity-render (minecart/item-frame) which no
   gametest can see.
This gate is **additional to** the pre-release hygiene gate, not a substitute.

## 4. Guardrails (also see RULES.md)
- Global slab support is product intent (full blocks anchor on slabs). Single-source through `SlabSupport`.
- Narrow `@Inject` over broad `@Redirect` on shared helpers. A placed block must NEVER autonomously pop
  (NEVER-POP). Don't break the full-block baseline lane.
- Collision: lower visual/outline/raycast ONLY; physical movement collision stays vanilla (per-state
  `getCollisionShape` must NOT follow — the 3d9c49ab broadphase mixin is the only place collision-follow
  lives, gated). Don't revive the deferred-clear/rearm approach.
- Local only; ask before push/release. Don't claim release-ready without the hygiene gate + a built/tagged jar.
