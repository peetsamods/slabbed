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

## 2026-06-20 — 26.2 manual queue closed, docs caught up

- Current branch/root truth for this checkout is `/Users/joolmac/CascadeProjects/Slabbed` on
  `port/mc-26.2-0.4.1-beta.1`, HEAD `60cd5cb5`, tag `pre-testing-pass`.
- The 26.2 follow-up families that were still reading as live/manual-open earlier today are now closed by Julia's own
  in-game confirmation: scaffolding traversal/feel, chain continuity and targeting, fence WYSIWYG, and the rest of the
  `Proofs 26.2.pdf` queue are working in the current branch state.
- Fresh branch proof also re-ran green on the current dirty tree:
  `tmp/26-2-proof-fails/runGameTest-26-2-post-live-confirm.log` shows compile gates plus `runGameTest` all green with
  120/120 required tests passed.
- The release/process docs were stale after the code and live proof moved ahead. This docs-sync slice exists to move
  `SLABBED_SPINE.md`, `docs/process/RELEASE_SANITY_CHECKLIST.md`, and a new local `docs/binder/` note to the real
  branch truth.
- Boundaries preserved: no savepoint, push, release, or publication happened here. This is status/docs catch-up only.

## 2026-06-20 — 26.2 startup compile frontier green

- First 26.2 source retarget is compile-green only, not runtime/gameplay/release proof.
- Updated build metadata to MC `26.2`, loader `0.19.3`, Fabric API `0.152.2+26.2`, and jar label
  `0.4.1-beta.1+26.2-port`; `fabric.mod.json` now declares Minecraft `26.2`.
- Mechanical 26.2 API fixes: screen-open guard now uses `Minecraft.gui.screen()`, client rerender refresh now routes
  through `ClientLevel.setBlocksDirty` / `setSectionRangeDirty`, and gametest fixtures use 26.2 collection constants
  for white carpet, red bed, and unweathered cut copper slab.
- Proof passed: `./gradlew25 --no-daemon compileJava compileClientJava compileGametestJava --console plain`.
- Next boundary is runtime/proof behavior: run the focused gametest lane before any jar staging or live profile work.

## 2026-06-20 — 26.2 port docs update

- Operating branch is now [port/mc-26.2-0.4.1-beta.1](/Users/joolmac/CascadeProjects/Slabbed) (from
  `origin/port/mc-26.1.2`) and this Handoff update is docs-only.
- Added the "Anticipated Problems and Risks" section to [docs/porting/PORTING_MAP.md](/Users/joolmac/CascadeProjects/Slabbed/docs/porting/PORTING_MAP.md)
  before continuing further 26.2 port implementation.
- Captured this as the working handoff baseline for the next agent so the 26.2 migration risks stay visible:
  toolchain/mapping drift, mixin/API signature drift, source-set/test naming drift, and live profile/jar ambiguity.

---

## 0. Get oriented (verify BEFORE touching anything)

```
root:    /Users/joolmac/CascadeProjects/Slabbed-port-26.1.2
branch:  port/mc-26.1.2   (ahead of origin by the 2026-06-19 session commits — NEVER push without explicit Julia go-ahead)
HEAD:    3c27bbd0   (clean tree)
MC 26.1.2 · Java 25 · Gradle 9.4.1 · Loom 1.15.5 · loader 0.19.2 · Mojang mappings · v0.4.1-beta.1+26.1.2-port
```
> ## CURRENT STATE — 2026-06-19: pre-release is essentially CLEAN on `0.4.1-beta.1+26.1.2-port`
>
> The whole WYSIWYG / placement / TS-compat arc is DONE and Julia LIVE-CONFIRMED. What remains before a
> release is the go/no-go + push — no open engineering. Earlier "active work" narratives (RC1–RC4, BUG A/B,
> the P0 sprint) are now history; their fixes all landed and are folded into the parity table below.
>
> **Fixed + LIVE-CONFIRMED this arc (the headline product law — "a placed block / slab sits exactly where the
> crosshair aimed" — now holds):**
> - **WYSIWYG side-click follow (`5383e4a2`):** a slab placed by clicking a lowered block's SIDE face now
>   FOLLOWS to that lowered surface (anchored −0.5) instead of freezing flat 0.5 high. The freeze-flat
>   NEVER-POP rail still holds when you click the FLAT GROUND beside a lowered block (you aimed at the ground).
>   This was the real remaining WYSIWYG bug; RC1 (`6ba27925`), RC2+gaps (`e67bbc6e`+`dc4bec2d`), BUG A
>   (`961249cc`), BUG B (`3ef254e2`) all landed before it, and RC3's dy is RED-verified DONE headlessly via the
>   useOn harness (residual RC3 was only a slab-TYPE policy + client cell-targeting, subsumed by the law).
> - **Redstone torch particle (`199bc268`):** the lit dust now emits at the lowered torch head (RedstoneTorchBlock
>   has its own animateTick, not covered by TorchParticleMixin → new `RedstoneTorchParticleMixin`).
> - **Vegetation flush on Terrain Slabs (`9cce42ce`):** double-tall plants (sunflower/tall grass) were SPLITTING
>   on TS — lower half flush, UPPER half −0.5 — because `shouldOffset`'s UPPER branch used a bare
>   `isBottomSlab(below(2))` with no TS gate (a TS slab extends SlabBlock). Gated it for VegetationBlock on TS.
>   (This fix also exposed + de-false-greened a gametest: tall plants DESPAWN to air on a bare slab, so the old
>   test was measuring air — see `docs/lessons/LESSONS_INDEX.md`.)
> - All the prior showstoppers stay fixed: render-region crash (`4d758fe8`), TS world-hole (`0bd265dc`),
>   P0.4 TS object/vanilla-slab lowering (`961249cc`).
>
> **Release mechanics done:** version reconciled `0.2.0-beta.4` → **`0.4.1-beta.1+26.1.2-port`** (`135f1c69`,
> Julia's choice); 28 KB dev recorders removed from the jar (`ec6e2429`); standardized **Release Sanity
> Checklist** + a **dy fingerprint** regression suite (`docs/process/RELEASE_SANITY_CHECKLIST.md`,
> `src/gametest/resources/dy-baseline.txt`) — **107 headless gametests green**. `./gradlew25` wraps Java 25.
>
> **DEFERRED (post-release, Julia's explicit call — NOT pre-release blockers):**
> - **Full VS+TS slab combining.** What WORKS today: a VANILLA slab placed ON a TS slab lowers −0.5 and merges
>   into a full-looking block (the "mixed slab", P0.4 directCustom). What's DEFERRED: a *TS* slab itself
>   lowering/combining (TS-on-vanilla, TS+TS, deep chains). Reason: TS blocks are categorically
>   `shouldSkipOffset` (any `terrain_slabs`/`terrainslabs` id → getYOffset returns 0), and that exclusion is
>   load-bearing — it's exactly what stops Slabbed tearing see-through world-holes in TS natural terrain.
>   Relaxing it reaches into terrain rendering. Would be a scoped post-release feature (selective subject-only
>   un-exclusion behind a flag + heavy live terrain testing); the −1.0 pick-raycast cap also limits deep chains.
> - **Step-up onto a lowered slab feels different from a vanilla slab.** By design: collision stays at the
>   vanilla cell height (so you can't clip through lowered blocks), so a lowered slab's step-up differs. Julia:
>   "not a dealbreaker, just interesting." Known minor quirk.
>
> **Live rig:** jar **`slabbed-0.4.1-beta.1+26.1.2-port.jar` (197778 B)** STAGED in BOTH Modrinth profiles
> (`Fabric 26.1.2`, `TEST_ SLABBED 26.1.2`); prior jars backed up alongside as `.bak-*`. **Keybind = vanilla
> `mouse.right`** (Julia can play). Restart the Modrinth instance to load a fresh build. **Live-rig lessons:**
> Escape is NOT delivered to MC → open the Game Menu via a Modrinth focus-flip, rebind via Options→Controls→Key
> Binds (no relaunch); F11 = macOS Show-Desktop; `/setblock` inherits stale anchor/compound markers (use FRESH
> positions); mods load only at LAUNCH (swapping the jar mid-session does nothing — relaunch).
> **2026-06-16/17 sprint (history):** P1 connecting-blocks (`409bf519`), P0.1 dual-mod-id TS gate
> (`b76cccba`), P3 hygiene (`bcfdff7b`,`dbf5215d`), P2 verified-closed (`7a9d9f01`).
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
then proven RED→GREEN (gametest) and/or live. (Headless suite has since grown to **107 gametests green** —
see `docs/process/RELEASE_SANITY_CHECKLIST.md`; the table below is the original forward-port sprint.)

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
- **✅ P0.3 vegetation flush on TS — FIXED + LIVE-CONFIRMED (`9cce42ce`).** (Superseded the earlier
  "not-an-issue" note: P0.4's directCustom lowering DID lower vegetation, and double-tall plants then SPLIT
  — lower half flush, UPPER half −0.5 — because `shouldOffset`'s UPPER branch used a bare
  `isBottomSlab(below(2))` with no TS gate.) Fix = TS-gate that UPPER check for `VegetationBlock` on a TS
  surface → both halves flush; doors/vanilla slabs unchanged. Also de-false-greened the resting test
  (plants despawn on a bare slab → was measuring air).
- **✅ P0.4 TS direct object/vanilla-slab lowering — DONE + LIVE-CONFIRMED (`961249cc`).** Ported the
  shipped 1.21.11 `directCustom` early-dispatch in `getYOffsetInner`: an OBJECT or a VANILLA slab resting on
  a named TS `BOTTOM_LIKE` surface lowers −0.5 to sit ON it (vanilla TOP slab → −1.0, clamped) — this IS the
  "mixed slab" combine (vanilla-on-TS). GEOMETRIC (no anchor → no snap); world-hole (P0.2) preserved (opaque
  full cubes are not subjects). **Full TS-SLAB combining (TS-on-vanilla, TS+TS, deep chains) is DEFERRED** —
  see the CURRENT STATE block at the top of this file for the reason (TS `shouldSkipOffset` is load-bearing).

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
  classes. **✅ Residual recorder bloat REMOVED (`ec6e2429`):** `LiveCursorIntentRecorder` gutted to an inert
  stub + `LevelRendererRenderedOutlineRecorderMixin` deleted/unregistered (~28 KB; jar 208,215 → 195,665 B).

### P4 — Targeting — ✅ NOT REPRODUCED (placement is WYSIWYG-confirmed live)
- 26.1.2 keeps the large `GameRendererCrosshairRetargetMixin` (~3075 lines) + `LoweredSideSlabRetargeter`
  (a divergence from 1.21.1's `SlabbedOffsetRaycast`, not a bug). Across the 2026-06-18/19 live sessions Julia
  did NOT report crosshair mistargeting; placement now lands exactly where aimed (WYSIWYG law satisfied,
  `5383e4a2`). So the offset-raycast overhaul port is NOT needed. (If mistargeting ever resurfaces, the
  overhaul is the known cure — memory `slabbed-targeting-root-cause-and-overhaul`.)

### P4-OLD — Targeting (legacy note, superseded by the line above)
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
