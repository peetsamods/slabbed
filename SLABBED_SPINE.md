# SLABBED_SPINE.md - Slabbed 26.1.2 Port Spine

This is the active operating spine for the dedicated Slabbed MC 26.1.2 port checkout. It is local to this tree and is not the phase19 Slabbed spine.

## 2026-06-20 — 0.4.2-beta.1+26.2 release metadata bump

- The P26 live-green savepoint is `689a8196`, tag `save/port-26-2-0-4-1-beta-1-p26-live-green`, pushed to
  `origin/port/mc-26.2-0.4.1-beta.1`.
- First proof-only hygiene for intended target `0.4.2-beta.1+26.2` passed compile, `runGameTest`, clean build,
  release-jar purity scan, and hard-reference scan, but stopped with Decision C because the jar still reported
  `0.4.1-beta.1+26.2-port`.
- Current operating action: bump release metadata to `mod_version=0.4.2-beta.1+26.2`, then rerun
  `$slabbed-pre-release-hygiene` from the bumped commit. Do not upload or create a release tag in this slice.

## 2026-06-20 — 26.2 manual queue live-confirmed closed + docs sync

- Julia manually re-tested the active 26.2 branch/client and confirmed the previously open PDF/live rows are now behaving
  correctly in-game: scaffolding, chains, fences, and the rest of the 26.2 follow-up items are working.
- Fresh proof on the current dirty tree is green: `tmp/26-2-proof-fails/runGameTest-26-2-post-live-confirm.log` passed
  compileJava, compileClientJava, compileGametestJava, and `runGameTest`; all 120 required tests passed.
- This closes the stale "manual queue still open" language from the earlier same-day 26.2 notes. Those entries remain as
  the red->green implementation history, but the current operating truth is that Julia's live pass closed the remaining
  release-eye/manual gates for the 26.2 fixes already in this tree.
- Docs sync in this slice updates `HANDOFF.md`, `docs/process/RELEASE_SANITY_CHECKLIST.md`, and a new `docs/binder/`
  running log so the branch state no longer reads as half-open after the live confirmation.
- No savepoint, push, or release was done in this slice.

## 2026-06-20 — 26.2 ceiling-bridged iron_chain targeting follow-up

- Input evidence: Julia's screenshots showed the direct top `minecraft:iron_chain` under a top slab rendered correctly,
  but the crosshair target/outline only covered the upper shifted selector; aiming at the visible lower end reported
  `[slabdy] target: none`.
- Root cause: `ChainCeilingGeometry` emits the direct top chain's 1.5-block bridge at grid height, while the normal
  outline/raycast path still shifted the vanilla chain shape upward by `dy=+0.5`. The model was correct; the selectable
  proxy was missing the lower visible half.
- Fix: `SlabSupport.ceilingBridgedVerticalChainSelectionShape` extends only direct ceiling-bridged vertical-chain
  outline/interaction shapes to the same 1.5-block vertical span as the alternate model; `SlabSupportStateMixin` wires
  that shape into `getShape` and `getInteractionShape`.
- Red proof: `tmp/26-2-proof-fails/runGameTest-chain-selection-lower-red.log` failed one required test,
  `Slabbed2612LoweringContractTest.ceilingBridgedChainSelectionExtendsToVisibleBridge`, at local `y=0.25`.
- Green proof: `tmp/26-2-proof-fails/runGameTest-chain-selector-final.log` passed 120/120 required tests after
  `tmp/26-2-proof-fails/compile-chain-selector-final.log` passed compile gates.
- Jar/profile state: `tmp/26-2-proof-fails/build-chain-selector-final-jar.log` built
  `build/libs/slabbed-0.4.1-beta.1+26.2-port.jar`, SHA-256
  `32a6c13c1e305050f96957d5d5d3b9712d218e58f74b41b8bb465e04f1d974a5`, and
  `tmp/26-2-proof-fails/stage-chain-selector-profile-jar.txt` staged it into Modrinth profile `SLABBED-MC 26.2`.
  The replaced jar SHA was `5270a1bc56283fb419e462939462f56572f2c8eaa36d96a8293f100da0ceeadf`.
- Live launch state: the staged profile launched and `latest.log` proved `Loading Minecraft 26.2`, `slabbed
  0.4.1-beta.1+26.2-port`, and `Slabbed initialized`. Exact-window selector screenshot proof is still open because
  live-control helpers found a visible vanilla Minecraft Launcher window and another visible `Minecraft* 26.1.2`
  Java window, so they refused to select a target.
- No savepoint, push, or release was done in this slice.

## 2026-06-20 — 26.2 video follow-up: scaffolding traversal + chain bridge merge

- Input evidence: `/Users/joolmac/Desktop/Screen Recording 2026-06-20 at 7.23.45 PM.mov`, dissected in
  `tmp/video-dissect-20260620-192345/triage.md`. The video narrowed the still-open rows to P26-1 scaffolding
  traversal and P26-6/P26-7 chain/lantern visual continuity.
- Scaffolding red proof: `tmp/26-2-proof-fails/runGameTest-scaffolding-red.log` failed because lowered scaffolding
  still received generic solid hanging collision and a player inside the lowered visual scaffolding volume did not
  count as climbable.
- Scaffolding fix: `SlabSupport.withHangingLoweredCollisionFromAbove` now skips scaffolding, and
  `LivingEntityLoweredScaffoldingMixin` treats the lowered visual scaffolding volume as climbable/scaffolding for
  vanilla space/shift traversal checks.
- Chain red proof: `tmp/26-2-proof-fails/runGameTest-chain-render-red-jdk25.log` failed because the lower Y-chain
  and lantern under the extended top-chain model both inherited `dy=+0.5`, overlapping/merging with the direct
  top chain's 1.5-block ceiling bridge.
- Chain fix: `SlabSupport.isCeilingBridgedVerticalChainColumnMember` identifies chain columns whose direct top chain
  is rendered by `chain_ceiling_support`; descendant chains and lanterns stay grid-height, while the direct top chain
  keeps its ceiling-attach `+0.5` semantic and the client renderer uses the shared direct-chain predicate.
- Green proof: `tmp/26-2-proof-fails/runGameTest-scaffolding-chain-final.log` passed with 119/119 required tests
  (including the scaffolding rows), and `tmp/26-2-proof-fails/compileClientJava-scaffolding-chain-final.log` passed.
- Current jar proof: `tmp/26-2-proof-fails/build-current-scaffolding-chain-jar.log` built
  `build/libs/slabbed-0.4.1-beta.1+26.2-port.jar` and staged it into Modrinth profile `SLABBED-MC 26.2`; SHA-256
  `5270a1bc56283fb419e462939462f56572f2c8eaa36d96a8293f100da0ceeadf`. The replaced profile jar was backed up under
  that profile's `_codex-backups/` directory.
- Live current-jar proof: `tmp/26-2-proof-fails/live-scaffolding-chain/01-scaffolding-before-space.png` shows the
  lowered scaffolding survival start at `Y=-59.5`; `02-scaffolding-after-space.png` shows space traversal to
  `Y=-55.0`; `03-scaffolding-after-shift.png` shows shift descent back to `Y=-59.5`. Chain VIS proof is
  `05-chain-wide-continuity.png` and `06-chain-close-continuity.png`, using the 26.2 command id `minecraft:iron_chain`
  and showing the top-slab -> chain -> hanging-lantern stack without the prior merge/gap.
- This supersedes the earlier same-day "probe only/no production patch" notes for P26-1/P26-6/P26-7. No savepoint,
  push, or release was done in this slice.

## 2026-06-20 — 26.2 lowered rail target rescue

- P26-2 from `/Users/joolmac/Documents/Proofs 26.2.pdf` has two layers: minecart visual fit on lowered rails, and
  lowered rail targeting. The minecart layer remains VIS/eye-only, but the rail targeting layer is now headless-proven.
- Red proof: `tmp/26-2-proof-fails/runGameTest-rail-target-red.log` failed because the lowered rail lower body was
  directly hittable, but vanilla world clip resolved `MISS`; rails were not in the visible-owner rescue family.
- Fix: `SlabSupport.isBeta35SlabHeightVisibleOwnerObject` now accepts lowered `BaseRailBlock` states, so lowered rails
  use the same visible-owner retarget path as other thin lowered target owners.
- Green proof: `tmp/26-2-proof-fails/runGameTest-rail-target-green.log` passed with 117/117 required tests.
- Combined proof after the rail fix: `tmp/26-2-proof-fails/runGameTest-26-2-full-queue-after-rail.log` passed 117/117
  required tests and `compileClientJava`.
- The matching jar was rebuilt and staged into Modrinth profile `SLABBED-MC 26.2`; SHA-256:
  `3c4b2f5cf8172692bc78541de0592b4b9c7233ce5edd9e8ec34b81f5a3ce04ec`. The previous staged jar was backed up in the
  profile `_codex-backups/` directory.
- Remaining P26-2 gate: live VIS for minecart-on-lowered-rail fit and release-eye confirmation that the rail is easy
  enough to target in the staged Modrinth profile.

## 2026-06-20 — 26.2 sulfur spike + live pre-screen

- The active 26.2 runtime does have the sulfur target from Julia's PDF review:
  `minecraft:sulfur_spike` (`SulfurSpikeBlock`, a `SpeleothemBlock`) exists in the Modrinth profile's 26.2 client jar.
- P26-4/P26-5 now cover both upward pointed dripstone and upward sulfur spike in `Slabbed2612RestingDyTest`.
- Red proof: `tmp/26-2-proof-fails/runGameTest-sulfur-spike-red.log` failed because the lowered sulfur spike lower body
  was directly hittable, but vanilla world clip resolved `MISS` and sulfur was not yet in the visible-owner rescue
  family.
- Fix: `SlabSupport.isBeta35SlabHeightVisibleOwnerObject` now accepts upward `SpeleothemBlock` objects, covering both
  pointed dripstone and sulfur spike through the same visible-owner mechanism.
- Green proof: `tmp/26-2-proof-fails/runGameTest-sulfur-spike-green.log` passed with 116/116 required tests.
- A patched jar was built and staged into Modrinth profile `SLABBED-MC 26.2`; the previous profile jar was backed up
  under that profile's `_codex-backups/` directory instead of left in `mods/`.
- Agent live pre-screen evidence lives in `tmp/26-2-live-proof-20260620-181328/`, including:
  `p26-1-scaffolding-live.png`, `p26-3-glass-panes-live.png`, `p26-4-5-dripstone-sulfur-live.png`,
  `p26-6-7-chain-lantern-live.png`, and `p26-2-8-minecart-fence-live.png`.
- The live screenshots make the fixture row visually plausible, but they do not close human VIS/FEEL gates. P26-1 still
  needs Julia's movement/climb/pass-through check, and P26-2/P26-3/P26-4/P26-5/P26-6/P26-7/P26-8 still need a human
  release-eye pass before any release-ready claim.

## 2026-06-20 — 26.2 pointed-dripstone visible-owner rescue

- P26-4/P26-5 from `/Users/joolmac/Documents/Proofs 26.2.pdf` now have headless pointed-dripstone coverage in
  `Slabbed2612RestingDyTest`.
- First probe was green immediately: upward pointed dripstone on a bottom slab already reads `dy=-0.5` and its direct
  outline follows the lowered body, so the failure was not dy classification or raw outline offset.
- Red proof: `tmp/26-2-proof-fails/runGameTest-dripstone-visible-owner-red.log` failed because a ray aimed through the
  visible lowered lower body hit the pointed-dripstone outline directly, but vanilla world clip resolved `MISS`; the
  block was not in the lowered visible-owner retarget family.
- Fix: `SlabSupport.isBeta35SlabHeightVisibleOwnerObject` now includes upward pointed dripstone, allowing the existing
  client lowered-owner retarget scan to rescue ownership for the visible lowered body.
- Green proof: `tmp/26-2-proof-fails/runGameTest-dripstone-visible-owner-green.log` passed with 111/111 required tests;
  `tmp/26-2-proof-fails/compileClientJava-dripstone-visible-owner.log` also passed. Later 26.2 runtime inspection found
  sulfur spike; see the sulfur spike entry above for the broader speleothem fix and proof.

## 2026-06-20 — 26.2 scaffolding FEEL guard

- P26-1 now has a server-collision regression guard:
  `Slabbed2612CollisionDepthTest.loweredScaffoldingSideInteriorStaysPassThrough`.
- Probe result: `tmp/26-2-proof-fails/runGameTest-scaffolding-feel-probe.log` passed with `passThrough=true`, so the
  current headless server collision path does not reproduce "lowered scaffolding acts like a wall."
- No production patch was applied for scaffolding. Keep P26-1 live FEEL/VIS open; the next live pass must identify
  whether the failure is client movement feel, climb behavior, targeting, or visual interpretation.

## 2026-06-20 — 26.2 raised chain/lantern dy guards

- P26-6/P26-7 now have mechanical dy guards in `Slabbed2612LoweringContractTest`:
  `chainColumnUnderTopSlabRaisesTogether` and `hangingLanternUnderRaisedChainRaisesWithChain`.
- Probe result: `tmp/26-2-proof-fails/runGameTest-chain-lantern-probe.log` passed with 114/114 required tests. No
  production patch was applied.
- Interpretation: the server dy math already raises a chain column together and raises a hanging lantern with the
  raised chain. The remaining P26-6/P26-7 questions are rendered geometry/VIS: continuous chain mesh, no pop artifact,
  and no lantern smoosh. P26-2 minecart remains Lane 3 only because the row is entity-render/visual-fit proof.

## 2026-06-20 — 26.2 fence-against-lowered-fence WYSIWYG harness

- P26-8 from `/Users/joolmac/Documents/Proofs 26.2.pdf` now has a real player-placement gametest:
  `Slabbed2612UseOnPlacementTest.useOnFenceClickingLoweredFenceOverAirFollowsToMinusHalf`.
- Red proof: `tmp/26-2-proof-fails/runGameTest-fence-red.log` failed exactly because a fence placed by clicking an
  already-lowered fence over air landed at `dy=0.0` instead of `dy=-0.5`.
- Mechanism: the connector cantilever BFS only treated lowered full blocks or lowered slabs as genuine source nodes.
  A supported lowered connector already sitting at `dy=-0.5` was not accepted as a source, so the newly placed adjacent
  fence fell through to vanilla height and the no-step connection rule then broke the visual arm.
- Fix: `cantileverLoweredConnectingMagnitude` now accepts a supported lowered connector source through
  `loweredSupportedConnectingMagnitude`, while still treating over-air connectors only as propagation nodes so a lane
  cannot self-sustain without a real lowered source.
- Green proof: `tmp/26-2-proof-fails/runGameTest-fence-green.log` passed with 109/109 required tests, including the
  existing stepped-fence/no-cross-step policy rows. P26-8 still needs VIS/live confirmation before it is fully closed
  as a manual PDF item.

## 2026-06-20 — 26.2 manual proof failure triage

- Branch/root verified: `/Users/joolmac/CascadeProjects/Slabbed` on `port/mc-26.2-0.4.1-beta.1`, HEAD `60cd5cb5`,
  tag-at-HEAD `pre-testing-pass`.
- Input evidence: `/Users/joolmac/Documents/Proofs 26.2.pdf` lists live/manual failures for lowered scaffolding,
  minecart/rail fit and rail targeting, glass pane visual dy, pointed dripstone/sulfur targeting and stacking,
  raised chain connection/pop artifacts, raised lantern-on-chain smoosh, and fence-against-lowered-fence WYSIWYG.
- Headless baseline before patch was green: `./gradlew25 --no-daemon runGameTest --console plain` passed with
  107/107 required tests. This means the PDF findings are live/manual false-green gaps unless a new symptom-specific
  harness row is added.
- Glass-pane root cause was small and harnessable: `SlabSupport.getYOffset` could lower the pane raycast/outline path,
  but the model gate filtered `IronBarsBlock`/pane family out of the lowered connector-contact family, so the visual
  stayed flush. Added `Slabbed2612ConnectorSurvivalTest.glassPaneParticipatesInLoweredConnectorVisualFamily` and widened
  `isBeta35FenceWallVariantContactObject` to include `IronBarsBlock`.
- Proof: the new row failed red in `tmp/26-2-proof-fails/runGameTest-glass-pane-red.log`; after the predicate fix,
  `tmp/26-2-proof-fails/runGameTest-glass-pane-green.log` passed with 108/108 required tests.
- Remaining PDF items are now queued in `docs/process/RELEASE_SANITY_CHECKLIST.md` §2.1 and are not closed by this
  headless fix. Next safe slice is one live/manual symptom at a time, starting with a proof surface that matches the
  failure: FEEL/VIS for scaffolding, N/A/VIS for minecarts, VIS/DY for dripstone/sulfur, VIS for raised chains/lantern,
  and DY/VIS plus a policy check for fence WYSIWYG.

## 2026-06-20 — 26.2 startup compile frontier green

- Branch/root verified: `/Users/joolmac/CascadeProjects/Slabbed` on `port/mc-26.2-0.4.1-beta.1`, HEAD `457b0767`,
  tag-at-HEAD `save/port-26-1-2-0-4-1-beta-1-pre-release-clean`.
- Retargeted metadata to MC `26.2`, loader `0.19.3`, Fabric API `0.152.2+26.2`, mod label
  `0.4.1-beta.1+26.2-port`, and `fabric.mod.json` Minecraft dependency `26.2`.
- Compile blockers resolved mechanically: `Minecraft.screen` moved behind `Minecraft.gui.screen()`;
  client render dirtiness moved from `LevelRenderer` methods to `ClientLevel.setBlocksDirty` /
  `setSectionRangeDirty`; colored block/weathered copper gametest constants now use 26.2 collection accessors.
- Proof green: `./gradlew25 --no-daemon compileJava compileClientJava compileGametestJava --console plain`.
- This is not release-ready or live-ready. Next safe slice is focused `runGameTest`/dy-fingerprint proof against
  the 26.2 target before any jar staging, live-drive, savepoint, or publication work.

## 2026-06-20 — 26.2 porting preflight handoff + map risk capture

- Starting slice is now tracked on branch `port/mc-26.2-0.4.1-beta.1` (from `origin/port/mc-26.1.2`) while
  preserving the 26.1.2 line for release-proven source truth.
- New contributor-facing risk ledger was added to [docs/porting/PORTING_MAP.md](/Users/joolmac/CascadeProjects/Slabbed/docs/porting/PORTING_MAP.md)
  for this 26.2 migration pass: tooling/mappings drift, mixin signature drift, source-set/test drift, stale markers,
  and profile/jar ambiguity.
- No source files were changed in this update; `docs/porting/PORTING_MAP.md` was updated only.

## 2026-06-20 — 0.4.1 beta.1 pre-release savepoint

Julia confirmed **`0.4.1-beta.1+26.1.2-port`** is the right target for this port line. The proof-only
pre-release hygiene pass at HEAD `6384fbb5` passed:

- `./gradlew25 --no-daemon compileJava compileClientJava compileGametestJava --console plain`
- `./gradlew25 --no-daemon runGameTest --console plain` — all 107 required gametests passed
- `./gradlew25 --no-daemon clean build --console plain` — build passed and all 107 required gametests passed
- release jar: `build/libs/slabbed-0.4.1-beta.1+26.1.2-port.jar` (`197778` bytes)
- strict jar scan: no debug/dev/test/gametest/proof classes and no suspicious `jdeps` hard refs
- evidence: `tmp/pre-release-hygiene-6384fbb5/pre-release-hygiene-report.md`

Savepoint tag intended for this clean candidate: `save/port-26-1-2-0-4-1-beta-1-pre-release-clean`.
This is a savepoint candidate, not a Modrinth/CurseForge upload and not a public release tag.

## 2026-06-18 — LIVE SESSION (drove the game myself; Julia "cruise control")

Julia live-tested the `dc4bec2d` jar + Terrain Slabs and reported TWO bugs.

**BUG A "nothing is lowering to TS" — FIXED + LIVE-CONFIRMED (`961249cc`).** Investigated (read-only Workflow + my own
code reading): NOT a regression. The world-hole fix `0bd265dc`'s own commit message says **"P0.4 (curated-object lowering
onto named TS BOTTOM_LIKE surfaces) is not wired"** — `customSlabSurfaceKind`/`CompatSlabSurfaceKind.BOTTOM_LIKE` existed
but were DEAD code; the only TS handling was the column-walk flush guard. **Ported the shipped, live-confirmed 1.21.11
`directCustom` path** (reference: `~/CascadeProjects/Slabbed-countered-compat-latest`): `isVanillaDirectCustomSlabSubject`,
`isDirectCustomSlabSupportSubject` (the world-hole-preserving gate — routes through `isSlabSitCandidate`, which EXCLUDES
opaque full cubes), `isDirectCustomSlabSupportedObject` (MAX_CHAIN_DEPTH column walk to a BOTTOM_LIKE surface, double-block
UPPER follows LOWER), `directCustomSlabSupportDy` → -0.5, and an early-dispatch at the TOP of `getYOffsetInner` (after
ceiling-hung, before the slab/shouldOffset split). GEOMETRIC (client+server agree → no anchor/snap, RC1 stays fixed).
World-hole P0.2 preserved. No-op without TS (customSlabSurfaceKind always NONE → NaN). **LIVE: crafting table on a TS
`rooted_dirt_slab` reads dy=-0.500 LOWERED src=geometric; STONE on a TS slab stays 0.000 flush.** 46/46.

**BUG B "upper-half aim places slab wrong" — REPRODUCED LIVE + FIXED (`3ef254e2`), live GREEN-verify PENDING.** Live RED
(rebound Use→r in-game without relaunching): a slab aimed at the UPPER half of a -0.5 lowered full block placed at
**dy=-1.000** (a full block too low). Root traced by CODE (`isCompoundVisibleSideUpperSlab` is a pure placement-set
attachment lookup, not geometric): `isCompoundVisibleSideUpper/LowerHit` (`SlabSupport.java`, used ONLY in
`findLegalCompoundSlabRemap`) compute the source's visible-body halves from `sourceDy` and fired for ANY lowered source,
so a slab against the side of a -0.5 block got the `COMPOUND_VISIBLE_SIDE_*` marker → the slab branch returned -1.0. **Fix:
gate both hit checks on `sourceDy ≈ -1.0`** (genuine compound only); a -0.5 source falls through to the RC2-A cantilever
lane and merges at -0.5 as the correct top/bottom type. -1.0 sources unchanged. 46/46. Could not finish live GREEN-verify
(small windowed view + floating-block aiming + Modrinth intermittently steals focus) → **Julia confirms:** right-click a
slab against the upper half of a -0.5 lowered block → reads -0.5 not -1.0.

**Live-rig lessons:** the bare `java` MC IS computer-use-drivable (Minecraft grant covers it). **Escape is NOT delivered to
MC** → open the Game Menu by switching focus to Modrinth (MC pauses on focus loss) then clicking the game title bar back;
the Game Menu is then clickable → rebind keys via Options→Controls→Key Binds with NO relaunch (binds live in-memory; "Reset"
restores the default). **F11 = macOS Show Desktop, not MC fullscreen** — drive windowed. `/setblock` test blocks inherit
STALE anchor/frozen/compound markers from prior placements at the same position → use FRESH positions. Build floating tests
high to avoid terrain burying them; eye = feet + 1.62 makes side-aiming finicky.

**State:** HEAD `3ef254e2`, 46 gametests green. Jar (210127 B) staged in `TEST_ SLABBED 26.1.2`; **game CLOSED, keybind
REVERTED to mouse.right** (Julia can play). NOT pushed. NEXT after Julia confirms BUG B: RC3 (compound-side markers,
the -1.0-source UNDERshoot — distinct from BUG B's -0.5-source overshoot) + RC4 + directCustom mixed-slab compound (-1.0).

Use it to know the current root, branch, HEAD, base tag, port blocker, proof state, and next safe step.

## Read Order

1. `AGENTS.md`
2. `HANDOFF.md`
3. `SLABBED_SPINE.md`
4. `docs/lessons/LESSONS_INDEX.md`
5. `docs/porting/PORTING_MAP.md` for port/backport/API/mapping work
6. `docs/process/LIVE_DRIVE_PREFLIGHT.md` before live-client or Modrinth-profile work
7. `docs/process/FALSE_GREEN_CHECKLIST.md` when automation proof and live behavior disagree
8. Relevant `docs/porting/*` notes for the active blocker

## Canonical Port Root

```text
/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2
```

Do not update other Slabbed checkouts from this file. The phase19 checkout remains a separate source/release line unless Julia explicitly asks to sync something back.

## Current Git Truth

- Branch: `port/mc-26.1.2`
- HEAD / tag at HEAD: verify live with `git rev-parse --short HEAD` and `git tag --points-at HEAD`.
- Base release provenance: `release/0.2.0-beta.4` / `f9014fbfcb15af2716f090d038762fd8d3d460de`
- Current source files after the 2026-05-31 re-certification were restored to HEAD `a1037642`; verify live before code edits.
- Tracked documentation edits may exist after handoff/update slices and are not source proof.
- Current untracked evidence is expected under `tmp/` and is not release payload.

Do not rely on this file alone for release proof; Git commands, proof logs, the annotated save tag, and pushed refs are authoritative.

## Current Port Status

Fresh dy1.5-2.0 live-recorder closure proof on 2026-05-28:

- Savepoint intent: `save/port-26.1.2-dy0-slab-shape-dispatch`.
- Active resolved blocker: the live-recorder RED at `4,-60,30` where unnamed vanilla dy0 `minecraft:stone_slab[type=bottom]` had outline/collision bounds but empty interaction/raycast shape.
- Mechanism: `SlabSupportStateMixin` now lets unnamed dy0 vanilla slabs with empty native interaction shape expose vanilla-compatible slab bounds from collision shape, falling back to outline shape. The row remains dy0, unnamed, non-persistent, and non-compound.
- Exact branch-client recorder proof: `tmp/dy15-20-dy0-slab-shape-dispatch-65f9ce54/live-validation-160618-clean-control/exact-pose-player365` has `outlineRaycastSplitRows=0`, `ghostSurfaceRows=0`, `hiddenOwnerRows=0`, `6513` exact target rows for `4,-60,30`, and `LIVE_GREEN_CURSOR_TRIAD`.
- Focused dy15 proof stayed green for the required counts: `liveFailUnauthorizedDyRows=0`, `renderBridgeMismatchRows=0`, `targetBlockNoSurfaceReplayRows=0`, `hiddenOwnerPlacementRows=0`, `loweredSideSlabPlacementVanillaDyRows=0`, `legalLoweredSlabSurfaceMismatchRows=0`.
- Lowered side-slab placement remains green: live recorder action evidence under `tmp/dy15-20-dy0-slab-shape-dispatch-65f9ce54/live-validation-152432/routes/lowered-carrier-placement-window/window-actions.tsv` has `LIVE_GREEN_PLACEMENT_AUTHORING` with `afterDy=-0.500000` and `persistent_lowered_slab_carrier`.
- Fresh gates in `tmp/dy15-20-dy0-slab-shape-dispatch-65f9ce54/final-savepoint-audit-162706/`: compile passed, runner3 passed with `RUNNER3_SUMMARY rows=260 placeRows=260 traceMismatches=44 placeMismatches=44 mismatches=88`, hitbox gate passed, culling visual passed with screenshots, default `runClientGameTest` passed, live-cursor recorder contract passed, and `git diff --check` passed.
- Tracked residual: focused dy15 proof still reports `legalObjectTriadMismatchRows=2` for `dy15_oak_fence_object` and `dy15_cobblestone_wall_object`. This is classified as separate from the `4,-60,30` dy0 slab dispatch regression because those are legal dynamic fence/wall object rows, not unnamed dy0 slabs; hitbox gate separately proves their raycast/collision co-location.

Fresh release re-certification + rearm revert on 2026-05-31 (HEAD `a1037642`):

- A dirty WIP "after-consume rearm" lifecycle (deferred client clear via `clearAfterConsumer` + `consumeAfterConsumeRearmPermit`) was evaluated and reverted. Its own proof (`tmp/placement-intent-rearm-proof-20260531-133850`) fixed chained `placementExpectedDyMismatchRows` 4->0 but introduced `outlineRaycastSplitRows=5999`, all at owner `20,-58,24`. Root cause: deferring the client clear makes the server also consume the same intent snapshot (HEAD has the server see `null`), desyncing the authoritative block from the client outline; it also leaks the client snapshot on real multiplayer. The 3 source files (`PlacementIntentState.java`, `BlockItemPlacementIntentMixin.java`, `GameRendererCrosshairRetargetMixin.java`) were restored to HEAD. WIP archived at `tmp/reverted-rearm-wip-20260531-161302/` (restore via `git apply rearm-wip-3file.diff`).
- Full-suite re-certification of the restored HEAD passed: compile green; runner3 `mismatches=88`; hitbox `raycastCoLocated=yes`/`collisionCoLocated=yes` with 0 `CoLocated=no`; default `runClientGameTest` green; focused dy15 `classification=GREEN_LEGAL_LOWERED_SIDE_SLAB_PLACEMENT_AUTHORING` with all critical counts 0 and only `legalObjectTriadMismatchRows=2`; recorder contract green. Evidence: `tmp/release-cert-20260531-161833/`.
- `legalObjectTriadMismatchRows=2` re-confirmed as the two fence/wall objects (`VALID_NAMED_DYNAMIC_DY15_FENCE_WALL_SUPPORTED_OBJECT`); accepted as vanilla-consistent collision-taller-than-visual geometry, not a clipping/placement defect.
- Release artifact: `clean build` green; `build/libs/slabbed-0.2.0-beta.4+26.1.2-port.jar` (50 classes) passes the documented leakage scan; `git diff --check` clean. Note: `LiveCursorIntentRecorder` ships intentionally (referenced by shipping mixins, gated off by `slabbed.liveCursorIntentRecorder`); it is not a hygiene leak.
- Open gameplay gap (not a release blocker): aggressive chained lowered-side-slab placement can still author `top/dy0` because the server-side `placement_intent_visible_lane` branch is gated on an intent snapshot that is absent server-side in single-player. Do NOT blind-patch the remap result-type; first build a deterministic gametest RED that reproduces the chained route, then fix and prove red->green.
- Durable note: `docs/porting/mc-26.1.2-rearm-revert-release-cert.md`.

Fresh manual video anomaly on 2026-05-31:

- Julia's manual branch-client recording after the release re-certification shows a real giant cyan selection-outline box around `video/frames/frame_020.jpg`; this is live visual evidence, not a compile/gametest artifact.
- Manual recorder evidence: `tmp/manual-live-recorder-20260531-171424/`.
- Recorder summary: `cursorRows=13528`, `actionRows=8`, `outlineRaycastSplitRows=7111`, `placementExpectedDyMismatchRows=0`, `loweredSideSlabPlacementVanillaDyRows=0`, `ghostSurfaceRows=0`, `hiddenOwnerRows=0`.
- The 8 action rows were ordinary `minecraft:stone` placements at dy0 with `marker=none`; do not classify this manual anomaly as placement authoring failure from the current evidence.
- Split rows cover both lowered full-block stone (`dy=-0.5`, outline/collision shifted full cube, interaction/raycast empty) and ordinary dy0 grass/stone (full-cube outline/collision, interaction/raycast empty). The dy0 family means the recorder's interaction/raycast replay may be over-strict, but the giant rendered outline remains unexplained because logged `outlineBounds` are ordinary bounded shapes.
- Recorder tooling was expanded after this finding: `LevelRenderer.renderHitOutline(...)` now writes `rendered_outline` rows and `rendered-outlines.tsv` when `slabbed.liveCursorIntentRecorder=true`. New counters include `renderedOutlineRows`, `renderedOutlineLargeBoundsRows`, `renderedOutlineReplayBoundsSplitRows`, and `renderedOutlineTargetSplitRows`. Static artifact proof: `tmp/recorder-rendered-outline-contract/out-1780263538993/`; runtime smoke `runClientGameTest` passed and confirmed `LevelRendererRenderedOutlineRecorderMixin` applied.
- Durable note: `docs/porting/mc-26.1.2-manual-video-anomaly-20260531.md`.

Julia correction / ghost lowered slab physical-presence slice on 2026-05-31:

- Corrected active symptom: lowered slabs can be visible and targetable while behaving like ghost physical geometry the player can pass through. Do not classify this as rendered-outline-only.
- Latest manual evidence: `tmp/manual-live-recorder-20260531-174825/`.
- Current evidence disproves only the simple client state-shape hypothesis: `tmp/ghost-lowered-slab-collision-authority-20260531-current/lowered-slab-shape-groups.tsv` has `4171` named lowered slab cursor rows grouped into four rows, all with outline, interaction/raycast, and collision bounds aligned and `LIVE_GREEN_CURSOR_TRIAD`.
- Movement collision bytecode audit confirms the physical path uses `BlockCollisions` / `CollisionContext.getCollisionShape(...)` and then yields only shapes intersecting the entity/query shape. The previous recorder sampled `BlockState.getCollisionShape(...)`, not whether `Level.getBlockCollisions(player, queryBox)` actually returned the target lowered slab shape.
- Recorder tooling was expanded again, gated by `slabbed.liveCursorIntentRecorder`, to log `playerBox`, `playerDelta`, `playerCollisionQueryBox`, `targetCollisionWorldBounds`, target/query intersection booleans, block-collision iterator counts/samples, and `playerBlockCollisionTargetExact` / `playerBlockCollisionTargetIntersectsReturned`. New counters: `collisionIteratorTargetMissRows`, `collisionIteratorTargetPresentRows`. New RED marker: `LIVE_COLLISION_ITERATOR_TARGET_MISS`.
- No collision behavior patch was applied. Durable note: `docs/porting/mc-26.1.2-ghost-lowered-slab-collision-20260531.md`.

The 26.1.2 port has reached a placement/culling closure candidate for the slab-held visual-target / placement-intent fault plus the follow-up placement review fallout. Work so far has focused on release-base provenance, mapping/tooling/classpath proof, Java 25 / Gradle / Loom compatibility, source-set wiring, narrow source API probes, the slab-held visual-target failure, the reviewed placement/retargeting guards, and the post-cull visible-face / partial-collision closure.

Resolved blocker: Julia's 2026-05-23 slab-held recording showed the selection outline/target floating above the visible stone body. The port restores the missing 26.1.2 visible-shape path, preserves the visual triad, applies the final-target-unknown placement-intent fix through the validated visible lane, prevents slab-held compound-visible retargeting from stealing nearer vanilla block hits, rejects non-placeable final-target-unknown visible-lane contexts before APPLY, and narrows lowered-side comfort retargeting so border samples outside the visible face do not remain sticky. Temporary `runner3RowId` / provenance audit instrumentation was removed before release proof.

Fresh placement review fallout proof on 2026-05-26:

- Runner3: `tmp/port-26-1-2-placement-review-fallout-proof-98bc0629/clientGameTest-runner3-provenance.log`
- Runner3 metrics with the synced local runner3 harness: `NO_PLACE_BUT_SHOULD_PLACE=19`, `PLACED_RELATIVE_TO_RETARGETED_OWNER=6`, `PLACED_ABOVE_VISIBLE_TARGET=21`, `PLACED_BUT_SHOULD_NOT=16`, `heldStoneVisibleOwnerRemapRegressionRiskRows=4`
- Proof environment note: the local shell default Java was 21, so proof was rerun with `JAVA_HOME=$(/usr/libexec/java_home -v 25)` to match the known 26.1.2 classfile/runtime family.
- Apples-to-apples regression check: removing the two production review-fallout edits under the same synced local runner3 harness produced the same mismatch profile, so the secondary class-count shape is harness-baseline drift rather than a production regression from this patch.
- Hitbox checkerboard scan: `RUNNER3_HITBOX_SCAN_GREEN specs=20 faces=86 samples=4214 anomalies=0`; prior clipping evidence showed `BORDER_FINAL_STICKY_INTENDED_OWNER=60` before the comfort-retarget narrowing.
- Runtime smoke: `tmp/port-26-1-2-placement-review-fallout-proof-98bc0629/runClient-smoke.log`
- Visual check: Julia supplied a live recording showing large/sticky selection clipping. The checkerboard scan reproduced the border-sticky target anomaly and proved it green after the comfort-retarget narrowing; no additional manual visual route was available while she was away.
- Release hygiene: `./gradlew --no-daemon clean build` passed; jar scans found no runner3/gametest/probe/trace/debug/provenance/tmp artifacts.

Fresh placement/culling closure proof on 2026-05-27:

- Compile: `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew --no-daemon compileJava compileClientJava --console plain` passed.
- Runner3: `tmp/port-26-1-2-wip-closure-savepoint-c36611d/final-runner3.log`
- Runner3 metrics: `mismatches=88`, `NO_PLACE_BUT_SHOULD_PLACE=19`, `PLACED_RELATIVE_TO_RETARGETED_OWNER=6`, `PLACED_ABOVE_VISIBLE_TARGET=3`.
- Culling proof marker: `classification=CULLING_VISUAL_REPRO_FIXED_CURRENT_ONLY`, not a literal restored-face marker. Existing proof is in `tmp/port-26-1-2-culling-clipping-fix-c36611d/quad-cullface-visual-ab-summary.txt`: `current_vs_baseline_diff_pixels=88200`, `pre_quadfix_current_vs_baseline_diff_pixels=0`, and the mechanism states that quadfix current renders the missing gold face.
- Collision proof: `tmp/port-26-1-2-wip-closure-savepoint-c36611d/final-hitboxgate.log` has both `oak_fence` and `cobblestone_wall` object rows at `expectedDy=-1.500000` with `raycastCoLocated=yes` and `collisionCoLocated=yes`.
- Runtime forbidden marker scan across runner3, hitboxgate, and culling visual logs found no `Invalid player data`, `InvalidInjectionException`, `MixinApplyError`, `updateCrosshairTarget`, `onPlayerInteractBlock`, or `Vec3d.ofCenter` markers.
- Release hygiene: `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew --no-daemon clean build --console plain` passed; `build/libs/*.jar` leakage scan found no runner3/gametest/probe/trace/debug/provenance/tmp artifacts; `git diff --check` passed.

The preserved historical mapping-provider note is:

```text
docs/porting/mc-26.1.2-mapping-blocker.md
```

That note records the original mapping-provider/tooling provenance stop. It is no longer the active blocker for HEAD `a1037642`: later local proof compiled and built the port on the active checkout. Current source migration should still treat API drift as Mojang-style unless fresh evidence proves otherwise.

## Current Operating Rules

- Keep work local to `/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2`.
- Do not edit `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate` from this port slice.
- Preserve unrelated dirty source migration work.
- Use grep/classpath/compiler evidence before changing code.
- Prefer one-file mechanical probes for source API drift.
- Run `compileJava` only when the slice calls for it, and interpret success narrowly.
- Do not treat `buildEnvironment` success as compile success.
- Do not make gameplay, release, or broad compatibility claims from this port tree.

## Known Port Evidence

- The dedicated port branch was bootstrapped from the released base `release/0.2.0-beta.4`.
- Java 25 is the known runtime family used for 26.1.2 port proof.
- Gradle 9.4.x and Fabric Loom experiments have been part of the tooling path.
- Broad cache searches are not authoritative for namespace decisions; exact task classpath proof is preferred.
- Optional compatibility families may be deferred when they are not core port scope, but the defer must be explicit and narrow.
- For one-file source probes, success means the target file leaves the compile error stream after one compile gate, not that the full project is ported.

## Current Stop Conditions

Stop and report if:

- the root is not `/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2`
- the requested slice would edit another Slabbed checkout
- two focused attempts fail
- compile evidence points to a different dominant blocker family
- a proposed change widens from mechanical API migration into gameplay behavior
- a savepoint/release claim is being made without commit, annotated tag, branch push, tag push, and clean tracked tree

## Next Safe Action

For documentation-only work, keep edits limited to port-local docs.

For code migration work, classify the current dominant compile/source blocker first, then patch exactly the requested file or the smallest proven mechanism. If the result remains unclear after one compile gate, stop with tried/observed/proven/unproven/next-smallest-audit.

`legalObjectTriadMismatchRows=2` is now classified (2026-05-31) as accepted vanilla-consistent fence/wall collision-taller-than-visual geometry, not a bug. Do not reopen the `4,-60,30` unnamed dy0 slab dispatch path unless a fresh recorder RED reproduces it.

The next candidate technical slice from Julia's manual video is a manual `$record` rerun through the ghost lowered slab pass-through route using the expanded physical-collision recorder. Grep `summary.md` and `session.jsonl` for `LIVE_COLLISION_ITERATOR_TARGET_MISS`, `collisionIteratorTargetMissRows`, `targetCollisionWorldBounds`, and `playerBlockCollisionTargetIntersectsReturned`. Do not patch collision behavior unless the live RED names a client collision-iterator target miss or a server authoritative collision/attachment mismatch.

Separate deferred gameplay slice: the chained lowered-side-slab placement gap (server authors `top/dy0` on aggressive chaining) still must begin with a deterministic gametest RED reproducing the chained route before any remap result-type / snapshot-availability patch. The deferred-clear + rearm approach remains rejected (it introduced `outlineRaycastSplitRows=5999` cursor clipping and a multiplayer snapshot leak).

---

## 2026-06-15/16 — PARITY SPRINT (DODO + snap + lantern + merge + chain + freeze families)

Long marathon. HEAD `3222844a`, branch `port/mc-26.1.2`, tree clean, jar **200367 B** staged in
Modrinth `Fabric 26.1.2`. Build/test with **Java 25** (`JAVA_HOME=…/temurin-25.jdk/Contents/Home`). 24
gametests green (`Slabbed2612LoweringContractTest` + `GhostLoweredCollisionProofTest`).

**Method that worked (Julia's law):** for every reported symptom — RED-verify the gap first (gametest
or live), then port the *current* shipped behaviour (not a stale snapshot), Mojang-adapt, prove
RED→GREEN. Render/geometry bugs are render-only → **live A/B is mandatory** (see `LIVE-DRIVE-GUIDE.md`).

Commits this sprint (newest first):
- `3222844a` test: faithful snap repro (slab beside LOWERED SLAB column; CONTROL −0.5, AUTHORED 0.0)
- `ea4eddd7` fix(render): **DODO** — clear step-face cullFace on flat-vs-lowered seam (port of 1.21.1
  clearStepCullFaces). `OffsetBlockStateModel.emitQuads` only wrapped dy≠0 blocks → a FROZEN-FLAT slab
  at a seam kept its baked cullFace → ghost window. Now wraps when `dy≠0 OR anyMismatchedNeighborDy`.
  **LIVE-CONFIRMED solid.**
- `fddd248e` fix: **snap** — placed slab whose −0.5 is side-inherited-only (no lowered carrier below)
  freezes flat (`slabLoweringIsSideInheritedOnly`). **LIVE A/B PROVEN** at (9,−58,5): setblock −0.5
  geometric, r-placed 0.0 FROZEN-FLAT.
- `baef1d03` feat: **chain-ceiling** extended baked model (`ChainCeilingGeometry` + chain_ceiling_support
  .json + addModel + emitQuads hook) — revival of the UNMERGED `fix/chains-*` branches (NEVER shipped in
  any 1.21.x release; verified via merge-base on every tag). Triggers only when a TOP/DOUBLE slab is
  directly above the Y-chain — Julia's windmill chain hangs beside the trunk so it may not trigger
  (separate "chain bottom→ground" geometry, still open).
- `7507029c` fix: **lantern smoosh** — HANGING blocks route through ceilingHungDecorationDy (port of
  bbe3deb9). LIVE.
- `6cb4b909` fix: **compound merge** — stale compound sidecar follows the slab below (max(−1.0, slabDy−
  0.5)) instead of hardcoded −1.0 (no log-sinks-into-flat-slab). gametest-proven.
- `472c7b70` fix: **side-inheritance** off (port 83afed84) + **cantilever** geometric merge (port
  9a24670c). RED→GREEN.
- `2845ba81` NEVER-POP freeze law · `28991e4d` ceiling-hanger · `0bc4ea84` powder-snow · `3d9c49ab`
  collision broadphase (lowered solid where drawn) · `1ff995b1` ghost collision · `b528d734` /slabdy.

**KEY FINDINGS:** (1) the chain-shortening was NEVER released — only on `fix/chains-*`. (2) I'd
forgotten the **keybind rebind** (Use→`r`) that makes placement drivable — now documented in
`LIVE-DRIVE-GUIDE.md`. (3) Julia's EXISTING snapped arm `(-2,-56,-1)` stays −0.5 = stale anchor from the
old jar (break+replace clears it). **KEYBIND IS CURRENTLY REBOUND (Use→r; backup options.txt.slabbed-bak)
— revert before normal play.** Test scratch blocks left at x=8 z=5 column + (9,−58,5) + (8,−60,12).

**GOAL (Julia, 2026-06-16): COMPLETE PARITY with shipped 1.21.1 AND 1.21.11 WITH Terrain Slabs.** The
remaining-gap roadmap is in `HANDOFF.md` (refreshed this date from a 5-reader parity-gap audit). NOT
pushed (local only).

---

## 2026-06-16 (overnight, autonomous — Julia asleep): P1 connect + P0.1 compat + P3 hygiene

Continued the parity sprint per HANDOFF while Julia slept. Three verifiable items landed; the headline
TS work is correctly DEFERRED (blocked on a 26.1.2 terrain_slabs jar) rather than ported blind.

- **P1 connecting blocks (`409bf519`):** `FencePaneSlabConnectionMixin` (@Mixin{FenceBlock,IronBarsBlock})
  + `WallSlabConnectionMixin` (@Mixin WallBlock) break the fence/pane/wall connection across a slab-height
  step (`getStateForPlacement` + `updateShape` RETURN). 4 new gametests drive the real `updateShape` path:
  stepped fence/iron-bars/wall break, flat fence still connects. 28/28. KEY GOTCHAS: real placement method
  is `getStateForPlacement` (the dead `SlabBlockPlacementFixMixin` targets a wrong name + isn't registered);
  both `WallBlock.PROPERTY_BY_DIRECTION` and `CrossCollisionBlock.PROPERTY_BY_DIRECTION` exist in 26.x;
  `WallBlock.updateShape` is overloaded → full-descriptor target.

- **P0.1 dual mod-id TS gate (`b76cccba`):** rewrote `TerrainSlabsCompat` in Mojang mappings (was a
  Yarn-mapped, build-excluded stub that only knew the legacy id), accepting BOTH `terrain_slabs` and
  `terrainslabs`; `CompatHooks` now dispatches the three hooks; `CompatSlabSurfaceKind` added; build
  exclusion removed. Subtractive-only (no TS loaded → no-op → non-TS path byte-identical, 32/32). **P0.2–P0.4
  (the actual world-hole/vegetation/named-surface resolver wiring) are BLOCKED: TS-gated → unverifiable
  without a 26.1.2 TS jar, and the resolver has diverged from 1.21.11. Porting blind would risk the
  live-proven baseline. Exact 1.21.11→26.1.2 call-site map is in HANDOFF §2 P0 for the TS-jar session.**

- **P3 hygiene (`bcfdff7b`,`dbf5215d`):** the only two genuinely always-on emitters fixed — removed
  `RedstoneWireBlockMixin`'s per-canSurvive `LOGGER.info`; gated `ServerInteractBlockHitToleranceMixin`'s 2
  `System.out` probes behind the existing (but unused) `REPEAT_SEAM_TRACE_OPT_IN`. Deleted the empty no-op
  `Beta35FenceWallLiveInspectTickMixin`. All other ~58 logs already opt-in-gated. `Beta4ManualLiveTrace` is
  build-excluded (the old "10 prints ship" note was wrong). Jar `unzip -l` clean.

**State:** HEAD `dbf5215d`, 32 gametests green, jar **207832 B built + STAGED** to the Modrinth profile (old
200367 backed up to `mods/.slabbed-staged-200367.jar.bak`). **Keybind REVERTED to vanilla right-click** so
Julia plays normally. NOT pushed (local only). Next verifiable: P2 hanger underside-follow (verify-first,
may overlap the lantern fix), then P4 targeting (needs live). P0 resumes when the TS jar lands.

---

## 2026-06-17 (autonomous): Julia provided a 26.1.2 TS jar → live TS work unblocked

Julia dropped a real Terrain Slabs 26.1.2 build (`terrain_slabs-fabric-3.3.1`, mod-id `terrain_slabs`) into
a `TEST_ SLABBED 26.1.2` Modrinth profile and said "Continue." Staged my Slabbed jar there, rebound the
keybind, and live-drove. Two showstoppers found + fixed + LIVE-CONFIRMED via `/slabdy` + visual:

- **render-region CRASH (`4d758fe8`) — the mod crashed loading ANY fresh world.** AIOOBE tesselating an
  ordinary `minecraft:stone` at a render-region boundary: SlabSupport's wide column/side-support reads
  exceed the bounds-limited `RenderSectionRegion`, which THROWS on OOB in 26.x (older MC clamped to air →
  the sibling never needed a guard; my DODO neighbour-iterating helper made the latent OOB reliable). Fix =
  catch IndexOutOfBoundsException in the model path (`OffsetBlockStateModel.slabbed$modelDy` +
  `slabbed$neighborModelDy` + the ChainCeilingGeometry probe) → treat as dy=0. World loads now. NOT
  TS-specific. This crash was latent in the prior non-TS jar too (only dodged because I'd tested via
  /setblock in already-loaded chunks, never a fresh world edge).

- **P0.2 world-hole (`0bd265dc`) — LIVE RED→GREEN.** `minecraft:stone` on `terrain_slabs:grass_slab` read
  `dy=-0.500 LOWERED` (RED) → after fix `dy=0.000 flush` (GREEN). Root: 26.1.2 `isBottomSlab()` returns true
  for a TS slab. Fix = terminate both column walks flush at any TS block via
  `CompatHooks.shouldSkipSlabSupport(cur)`. Natural TS sand/badlands terraces render solid (no holes).

- **P0.3 vegetation: VERIFIED NOT AN ISSUE** — P0.2's TS-guard zeroes Slabbed's dy on TS, so no
  double-offset; placed + natural TS vegetation render correctly. Sibling's `6f0c73e6` model-skip noted as
  defensive-only (no RED). **P0.4 named direct-support** = optional polish (curated objects rest flush on TS
  now, acceptable).

**State:** HEAD `0bd265dc`, 29 gametests, jar **208081 B** staged to BOTH Modrinth profiles, keybind
REVERTED in both, NOT pushed. **The headline goal — Slabbed running correctly WITH Terrain Slabs on 26.1.2
(no crash, no world-holes) — is achieved and live-confirmed.** Live-drive lesson: a fresh normal world is
the real crash test; /setblock in loaded chunks hides render-region OOB.

---

## 2026-06-17 PM (live with Julia): WYSIWYG placement law + multi-agent audit

Julia tested the staged TS jar live and reported placement bugs (4 screenshots): placing a slab/fence
against a lowered block's SIDE snaps it up to vanilla; placing a block ON a terrain slab snaps it DOWN
after a short delay. She crystallized the governing **LAW: "a placed block sits exactly where the
crosshair aimed — there is NO case where you aim to place a block and it lands elsewhere."**

**Method:** with the live game finally drivable (had to QUIT STEAM — its notification overlay was eating
every computer-use click — then relaunch FULLSCREEN so the bare `java` window is frontmost; windowed mode
loses focus to Modrinth on every pause), I enabled the existing gated placement trace
(`slabbed.beta4RepeatMergeTrace`, BlockItemPlacementIntentMixin) and reproduced the side-placement bug. The
trace proved the slab lands at the CORRECT cell + CORRECT type but with `placedDy=0.0` (frozen flat) — i.e.
NOT a raycast bug; the dy is just never lowered. Then an 8-agent background Workflow audited the whole
placement pipeline against the law → **4 root causes** (`docs/porting/WYSIWYG-PLACEMENT-AUDIT.md`):

- **RC1 ✅ (`6ba27925`):** block-on-TS-slab snap-down. `hasBottomSlabBelow` (single choke point for all anchor
  sites) treated a TS bottom slab as a lowering support → onPlaced anchored -0.5 server-side while the client
  read geometric 0.0 → snap on sync. Fixed with `CompatHooks.shouldSkipSlabSupport` guard. 29/29, no-op
  without TS.
- **RC2 (NEXT):** cantilever slab/fence/wall beside a lowered block never gets a geometric -0.5 (slabs gated
  by `canUseInheritedSlabLaneYOffset`; fences excluded by `isSolidRender` in `isCantileverFullBlockCandidate`)
  → freezes flat. **KEY lesson: fix the GEOMETRY, not the anchor** — a placement-time anchor still SNAPS
  (client predicts geometric 0, server anchors -0.5, pops on sync), same desync class as RC1. My first
  attempt (placedCantileverMergeDy anchor) was reverted for this reason. 3 air-gated parts; every clause
  `pos.below()==air`-gated to keep the NEVER-POP rail (place on solid ground beside a lowered lane → stays
  flat). The windmill-arm "stays flush" rule and Julia's new "merge" ask are NOT contradictory — both are
  the same law (aim flat → flat; aim at the lowered side → lowered).
- **RC3 (compound -1.0 side markers), RC4 (cantilever-FB on-top edge):** queued.

**State:** HEAD `6ba27925`, 29 gametests, RC1 done. Game running fullscreen (keybind `Use`→`r`). NOT pushed.
`Slabbed2612LoweringContractTest.java` reverted twice under concurrent edits — coordinate before adding RC2
gametests. Lesson: Steam overlay blocks computer-use → quit Steam + relaunch fullscreen restores control.

---

## 2026-06-17 PM (cont.) — RC2 cantilever side-merge DONE + 2 HIGH gaps closed

**RC2 base (`e67bbc6e`):** fixed the GEOMETRY in `getYOffsetInner` (NOT a placement anchor — an anchor still
snaps: client predicts geometric 0, server anchors -0.5). 3 air-gated clauses: RC2-A slab branch
(`isAdjacentLoweredFullBlockSource`), RC2-B connecting BFS (`isCantileverLoweredConnectingObject`), RC2-C
`slabLoweringIsSideInheritedOnly` air-gate so freeze ANCHORS -0.5 not FROZEN-FLAT. Every clause `pos.below()==air`-
gated → NEVER-POP solid-ground rail holds. **LIVE-CONFIRMED both directions** (merge -0.5 no snap; flush on solid
ground). 40 gametests.

**Adversarial review** (8-agent Workflow → `docs/porting/RC2-ADVERSARIAL-REVIEW.md`): verdict "safe to keep, do not
revert", no regressions, no safety issues — and the synthesizer caught two sub-reviewers FABRICATING probe output
(dismissed it, re-ran the build itself: 40/40, 0 probes). Found 2 HIGH completeness gaps, both Julia's "lands too
high" one config deeper.

**Gap fixes (`dc4bec2d`):** GAP-1 = cantilever beside a COMPOUND -1.0 stack merged to -0.5; now reads the
neighbour's ACTUAL magnitude (new `loweredFullBlockMagnitude` = anchor path + geometric `floorTorchBottomSlabSupportDy-0.5`;
new `loweredSlabMagnitude` = compound-visible side slabs; `isAdjacentLoweredFullBlockSource`→`adjacentLoweredSideMagnitude`
and the connecting BFS both carry -1.0 vs -0.5 out). GAP-2 = slab beside a BARE single lowered slab (no column) read
0.0; now a lowered slab neighbour (anchored / side-slab lane / persistent carrier) is a valid source. **One config
DEEPER:** the anchored read-back branches (slab + connecting) also hardcoded -0.5 → would pop -1.0→-0.5 the instant
the anchor set; both now read the live neighbour magnitude, air-gated, falling back to the -0.5 anchored floor on
source removal (NEVER-POP-up preserved). Recursion-safe (no getYOffset), MAX_CHAIN_DEPTH-bounded. **46/46 gametests**
(+6 onPlaced: compound -1.0 slab/fence, bare single slab, 3 solid-ground rails), independently re-verified.

**State:** HEAD `dc4bec2d`, 46 gametests green, RC1+RC2+gaps committed. Gap-fix jar (209275 B) STAGED in
`TEST_ SLABBED 26.1.2` (pre-gap jar → `.jar-backups/`); game STOPPED. **NEXT = RC3** (compound side-placement
markers + TOP/BOTTOM midline split in `BlockItemPlacementIntentMixin`). Gap live-verify pending (steps in the review
doc). GAP-3 (FB-to-FB / mixed fence chains beside compound) deferred. NOT pushed.

---

## 2026-06-18 — Standardized Release Sanity Checklist + dy fingerprint regression suite (process, no behavior change)

Codified a single, repeatable release-readiness gate so testing new versions and porting is methodical
instead of ad-hoc trial-and-error (Julia's ask: "something standardized and repeatable so there is less
guesswork", "always referred to when creating new versions or porting").

**Docs**
- NEW `docs/process/RELEASE_SANITY_CHECKLIST.md` — proof-typed (GT/DY/VIS/FEEL/N/A), three lanes
  (automated / live-dy-cruise / human-visual), a 15-row smoke set, the full per-family matrix, and the
  dy-fingerprint version-comparison design (§3). Coverage gaps from a 3-subagent audit folded in: beds
  (either-half), minecart/item-frame/rail render offsets (§R, live-only — no gametest can see them),
  redstone + double-tall plants (§T), carpet survival, chain break-pop, collision-follow cell-below +
  `collisionFollow` kill switch, door/trapdoor.
- `RULES.md` §19 (NEW) — the checklist is the **mandatory** gate for every version bump / release cut /
  port slice; fixed Lane order; compare versions by diffing the fingerprint, not from memory.
- `HANDOFF.md` §3 — added the STANDARD GATE step to the work loop. `LESSONS_INDEX.md` S9 (NEW).

**Code (gametest only — excluded from the shipped jar)**
- NEW `src/gametest/java/com/slabbed/test/Slabbed2612DyFingerprintTest.java` — 19 fixtures, one
  `@GameTest` per fixture (isolated regions). Each asserts a pinned `dy` (same-version regression → RED on
  `runGameTest`) and logs `SLABBED-FP | name | dy | src` (grep a run log → flat cross-version diff
  artifact). Door/trapdoor assert the robust server-hit predicates
  (`isBeta35Lowered{BottomTrapdoor,RegularDoor}ServerHitTarget`) — this CLOSES the previously-zero
  door/trapdoor gametest coverage. Registered in `build.gradle` include() + `src/gametest/resources/fabric.mod.json`.
- NEW `src/gametest/resources/dy-baseline.txt` — committed reference fingerprint capture (MC 26.1.2).
- **66/66 gametests GREEN** (`./gradlew runGameTest`, Java 25), up from 45.

**Finding surfaced (live-confirm TODO, NOT fixed here):** an anchored `candle` reads `dy=-0.5` via
getYOffset (generic anchor) — the 1.21.x floor-top CONTACT rule (`supportDy-0.5 = -1.0`, the beta35 candle
audit) is NOT wired into 26.1.2's anchored getYOffset path. `trapdoor` (anchored) and `door` (geometric)
also read -0.5. -0.5 is consistent across the three and not an obvious bug, so the fingerprint locks the
OBSERVED value (`candle_contact_OBSERVED`) and flags it for live verification (possible contact-gap).

**State:** HEAD still `e0f5986b`-era working tree + these process/gametest additions; behavior code
UNCHANGED (no `src/main` / `src/client` edits). NOT committed yet, NOT pushed. Build green.

### 2026-06-18 (cont.) — Tier-2 client dy-fingerprint dump (dev-only, jar-excluded)

Committed the checklist+suite as `1a655c21`. Then built Tier-2 (the client-side companion to the
headless fingerprint, RELEASE_SANITY_CHECKLIST §3): `src/client/java/com/slabbed/client/DyFingerprintDump.java`.
- Catches the one class the server gametest fingerprint is blind to: **client-vs-server getYOffset
  divergence** ("snaps after a delay" sync bug). Press P (unbound vanilla, MacBook-friendly; ignored while a screen is open) → scans a box around the player, logs every
  lowered/raised block as `SLABBED-FP-CLIENT | pos | block | dy | src` (same src= as the HUD) → diff
  against the committed server `dy-baseline.txt`.
- **Trigger = GLFW key-poll** in `ClientTickEvents.END_CLIENT_TICK`, rising-edge debounced. The compiler
  proved `fabric-key-binding-api-v1` is NOT on this client set's classpath (only lifecycle / renderer /
  model-loading are), so a registered `KeyMapping`/`KeyBindingHelper` won't compile — polled GLFW directly
  (`client.getWindow().handle()`), mirroring how the HUD avoids fabric-rendering-v1. Also: this port maps
  `ResourceLocation` as `Identifier`; chat `addMessage` is 4-arg only → logger-only output.
- Dev-gated (`isDevelopmentEnvironment`) init from `SlabbedClient`; **excluded from the release jar**
  (build.gradle `DyFingerprintDump*.class`) — verified via `unzip -l` (absent). Render-mesh desync still
  NOT visible here (Lane 3 human check).
- **Status: compile-verified + full build green + 66/66 gametests; jar exclusion confirmed.** ⚠ Needs ONE
  live smoke from Julia (press P in a dev world, confirm SLABBED-FP-CLIENT lines appear) — I can't
  drive the dev client. NOT committed yet in this sub-step.

### 2026-06-18 (cont.) — useOn placement-path gametest coverage + RC3 headless RED-verify

Added `src/gametest/java/com/slabbed/test/Slabbed2612UseOnPlacementTest.java` — drives the REAL
`BlockItem.useOn` placement-intent remap (`BlockItemPlacementIntentMixin`) headlessly via
`helper.makeMockPlayer(GameType.SURVIVAL)` + a hand-built `UseOnContext(player, MAIN_HAND, BlockHitResult)`.
This closes the long-standing "RC2/RC3/RC4 have NO headless coverage" gap (the SlabbedLab useOn tests are
Yarn-excluded). Registered in build.gradle include() + fabric.mod.json. **72/72 gametests green.**

**RC3 headlessly RED-verified (the open WYSIWYG item):**
- **dy DONE:** slab placed via useOn against the side of a compound −1.0 stack reads **dy=−1.000 for BOTH
  upper- and lower-half aims** → the RC2 GAP-1 fix (`dc4bec2d`) already absorbs RC3's dy magnitude (was only
  hypothesised in RC3-LIVE-REDVERIFY-PLAN; now proven). RC2 −0.5 side case confirmed both halves too.
- **TYPE residual is REAL:** every side-merge placement mints `type=TOP` regardless of aimed half, for both
  −0.5 and −1.0. A CONTROL on a flush block proves the harness reproduces vanilla hit-based type
  (upper→TOP/lower→BOTTOM), so always-TOP = the `compoundBelowLaneResultType` midline split, as predicted.
  Test ASSERTS dy only and LOGS type (USEON-FP lines) — the TOP/BOTTOM policy is an unsettled decision.

RC3 residual now precisely scoped: (1) slab-TYPE policy + midline-split fix, (2) client cell-targeting (P4,
genuinely live). dy is no longer in question. Docs updated: RC3-LIVE-REDVERIFY-PLAN.md (result section),
WYSIWYG-PLACEMENT-AUDIT.md (caveat). NOT committed→ committing now. RC4 still has no useOn coverage.

### 2026-06-18 (cont.) — widened the headless net (+23 tests, 95 total) + 5 findings

Three new gametest classes from a 3-agent spec, measure-and-lock verified, **95/95 green**:
- `Slabbed2612RestingDyTest` — special full blocks / floor objects / ceiling-hung / thin layers / 9-material
  slab sweep, dy via getYOffset (batched, clear-between to fit the 8x8x8 region).
- `Slabbed2612ConnectorSurvivalTest` — glass_pane break + flat control, wall UP-post-on-break, extra
  fence/wall materials; carpet/flower_pot/redstone survival via `canSurvive` predicate + direct `updateShape`
  (NOT place-then-remove — neighbour tick may not fire headless).
- extended `Slabbed2612UseOnPlacementTest` — freeze-on-place via real useOn (+isFrozenFlat), full-block on
  slab -0.5, slab-on-compound-top, anchor-survives-source-removal (+isAnchored), slab-on-anchored-cantilever.

**Findings (flagged in-test, observed-locked, NOT faked):**
1. **`ChainBlockNeighborSurvivalMixin` registered in NO mixin config** → chain break-pop was inert.
   **RESOLVED 2026-06-18 (Julia: "clean up, match df3a0dd4"):** the inert state was intentional — commit
   `df3a0dd4` ("restore vanilla floating-chain behavior") deliberately un-registered the pop-off mixin and
   flipped its repro tests to "chain stays". Deleted the dead `ChainBlockNeighborSurvivalMixin.java` + the
   orphaned Yarn-mapped `ChainSurvivalReproTest.java` (was excluded from the gametest `include` list, never
   compiled), rewrote checklist D6 to the vanilla-floating policy, and added
   `chainDoesNotPopWhenSupportRemoved` to `Slabbed2612ConnectorSurvivalTest` to pin it.
2. **Redstone wire `canSurvive`==true over AIR** (vanilla=false) → `RedstoneWireBlockMixin` over-permissive;
   dust could float. → spawned task `task_4c93146d`. **[CORRECTED — FALSE READING; see "redstone over-air
   = false alarm" entry below. Mixin is NOT over-permissive; dust does NOT float.]**
3. **Double-tall plants (sunflower/large_fern/tall_grass) on a slab stay flush 0.0**, not -0.5 (checklist
   T4-6). Likely intentional (veg handled by TS); flagged as a checklist-vs-code discrepancy.
4. **Slab on a compound -1.0 TOP follows ONE step to -0.5**, not -1.0 (checklist L5 ideal). Possible
   deeper-follow gap; locked observed -0.5.
5. **Flower pot survives regardless of support** (no support rule) → checklist E7 "pot pops" is live-only.
6. CORRECTED earlier guesses: RC4 slab-on-cantilever reads -0.5 (correct, NOT the feared 0.0 gap); FLOOR
   bell lowers -0.5 (correct, no gap).

Commit `5fb4bf28`. The running headless suite (excludes the Yarn SlabbedLab files) is now **95**. NOT pushed.

### 2026-06-18 (cont.) — final headless wave (104 total green)

+9 more: stairs (-0.5), ceiling trapdoor[TOP] under top slab (+0.5), bed orderings (head-on-slab lowers
BOTH, bed on top slab flush), floor-torch/wall-torch on a lowered block (rest flush -0.5), compound -1.0
collision presence (solid where drawn, `noCollision`), and a combined-chain MATRIX confirming lowering
CLAMPS at -1.0 (deep stack: g0/slab0/stone-0.5/slab-0.5/stone-1.0/slab-0.5/stone-1.0, never below -1.0).
Corrected guesses along the way (torch-on-lowered-FB = -0.5 flush not -1.0; bell/RC4-cantilever fine).
Commit chain: `5fb4bf28`→`47426827`→(this wave). **Running headless suite = 104** (from 45 at session
start). This is ~the headless ceiling — remaining work is genuinely live-only (render/feel/targeting) +
the 2 spawned bug tasks (redstone over-air, chain mixin) + 2 flagged policy questions (tall plants flush,
compound-top one-step). NOT pushed.

### 2026-06-18 (cont.) — redstone over-air = FALSE ALARM (task_4c93146d resolved, no code bug)

Investigated finding #2 (redstone `canSurvive`==true over air → "mixin over-permissive, dust floats").
**It was a measurement artifact, not a bug.** `RedstoneWireBlockMixin` only injects `canSurvive` and only
overrides it to `true` when `SlabSupport.isRedstoneSupportTopSurface(below)` is true; that helper returns
**false** over air (air is not face-sturdy-up and is not a slab), so over air the mixin defers to vanilla,
which returns false. Proven headlessly:
- `Blocks.REDSTONE_WIRE.defaultBlockState().canSurvive(level, pos)` over air = **false**.
- `isRedstoneSupportTopSurface(air)` = **false**.

Why the old test saw `true`: the test placed the wire, then `setBlock(support, AIR)`. `GameTestHelper.setBlock`
uses flag 3 (shape-update path runs), so the wire **synchronously pops to air** via vanilla
`updateShape -> canSurviveOn` (direction DOWN) — a path the mixin does NOT touch. The subsequent
`level.getBlockState(wirePos).canSurvive(...)` then read **AIR's** default `canSurvive` (=true), not the
wire's. Diagnostic confirmed `atWireAfter = minecraft:air`.

Bonus check (no asymmetry bug): in 26.1.2 **both** bottom and top slabs have a sturdy UP face
(`isFaceSturdy(UP)=true`), so vanilla `canSurviveOn` already accepts them and `updateShape` does NOT pop a
wire resting on either slab type. The mixin's slab clause is therefore redundant-but-harmless here.

Action: **no mixin change** (correct as-is). Tightened the gametest instead — `redstoneWireSurvivesOnSlabTops`
now asserts (a) the placed wire pops when support is removed, and (b) a FRESH wire `canSurvive==false` over
air; kept the two positive slab-top assertions; corrected the misleading docstring. **All 105 tests pass.**
Lesson (again): a "place-then-remove-then-read-the-dependent-block" survival test reads the popped block, not
the subject — assert on a fresh state (or assert the pop). NOT pushed.

### 2026-06-19 — version reconciliation: 0.2.0-beta.4 → 0.4.1-beta.1+26.1.2-port

The 26.1.2 port had been carrying `mod_version=0.2.0-beta.4+26.1.2-port` — it forked from the 0.2.0-beta
line and was NEVER renumbered, so the version string lagged ~two minor lines behind the 0.4.0-beta.3 the
1.21.1/1.21.11 siblings shipped, despite this port carrying the full 0.4.0-era content (forward-ported June
families + TS compat + WYSIWYG fixes). A LABEL bug, not missing content. Julia chose **0.4.1-beta.1** (fresh
0.4.1 line, signalling 26.1.2 is newer than the siblings' 0.4.0-beta.3). Bumped `gradle.properties`; jar now
builds as `slabbed-0.4.1-beta.1+26.1.2-port.jar` and fabric.mod.json bakes the right string (verified via
unzip). CHANGELOG entry added; operational version refs in HANDOFF/LIVE-DRIVE-GUIDE updated (historical
audits/spine left as-is). ⚠ The Modrinth profiles still hold the OLD-named jar — re-stage the 0.4.1 jar
before the next live session. NOT pushed.

### 2026-06-19 (cont.) — recorder hygiene removal + staged 0.4.1 jar

Removed the dev cursor/outline diagnostic recorders from the release jar (the ~28 KB the hygiene gate
flagged). Careful approach: gutted `LiveCursorIntentRecorder` (539→~15 line inert stub, API preserved so
the two complex mixins still compile + zero-cost short-circuit via `enabled()→false`) and deleted
`LevelRendererRenderedOutlineRecorderMixin` + its mixins.json registration — WITHOUT surgery on the
untested 3075-line `GameRendererCrosshairRetargetMixin` (judged not worth the risk for ~1 KB). 105/105
gametests green, runClient load smoke clean (all client mixins apply). Jar **208,215 → 195,665 B**.
Commit (recorder removal) made. **Staged `slabbed-0.4.1-beta.1+26.1.2-port.jar` (195,665 B) into BOTH
Modrinth profiles** (`Fabric 26.1.2` + `TEST_ SLABBED 26.1.2`); old 0.2.0 jars renamed
`.bak-superseded-by-0.4.1` (exactly one active Slabbed jar each; TS rig keeps terrain_slabs-3.3.1 +
architectury + fabric-api + peetsa-cosmetics). Restart the Modrinth instance to load it. NOT pushed.

### 2026-06-19 (cont.) — Julia live pass on the 0.4.1 jar (TEST_ SLABBED 26.1.2)

WYSIWYG LAW reaffirmed (supersedes the 3 policy questions): a placed slab lands EXACTLY where the
crosshair clicked — no exception.

- **RED #2 (top priority) — upper-half slab placement lands 0.5 high.** Placing a slab against the UPPER
  half of a lowered (-0.5) block: one landed `dy=-0.500 ANCHORED` (correct) but another landed
  `dy=0.000 FROZEN-FLAT` (WRONG — 0.5 above where aimed). The freeze-flat NEVER-POP rail is winning when
  the player actually aimed at the lowered surface → WYSIWYG violation. Reproduce headlessly via the
  Slabbed2612UseOnPlacementTest harness (add the SOLID-ground upper-half case → expect it currently
  freezes flat = RED), then fix so a click on the lowered block's face follows to -0.5. Screenshots:
  oak slab at (-1,-59,2)=-0.5 ANCHORED vs (1,-60,2)=0.0 FROZEN-FLAT.
- **RED — vegetation on TS should sit flush on top.** Sunflower on a TS surface reads `dy=-0.500`
  (src=geometric); Julia wants flowers/grass flush on top of TS. (Plants on a VANILLA slab read 0.0 / not
  lowered — so this is TS-specific, the P0.4/directCustom path lowering plants -0.5.) Confirm sunk vs
  floating and make veg flush on TS.
- **MINOR #4 — can't step up a lowered slab like a vanilla slab at the same visual Y.** Expected: collision
  stays vanilla (upper-half cell) by design so you don't clip through lowered blocks; the step-up therefore
  differs. Julia: "not a dealbreaker, just interesting." Logged as known collision/visual tradeoff.
- GREEN (not flagged): render solidity / ghost-windows, targeting+break, TS world (no holes, no crash).

Jar `slabbed-0.4.1-beta.1+26.1.2-port.jar` staged + live-tested. NEXT: WYSIWYG upper-half placement fix.

### 2026-06-19 (cont.) — WYSIWYG side-click follow fix (RED→GREEN, jar re-staged)

Fixed Julia's #1 bug (slab clicking a lowered block's face lands 0.5 high). Root: the side-inherited
freeze-flat rail in `freezeLoweredOnPlace` fired on "solid ground below" regardless of what the player
clicked. Fix (`5383e4a2`): `BlockItemPlacementIntentMixin` useOn HEAD inject marks a slab placed by
clicking the SIDE face of a -0.5 lowered block (thread-local `WYSIWYG_FOLLOW_CLICKED_LOWERED_FACE`,
mirrors the compound-intent bridge; cleared on useOn RETURN); `freezeLoweredOnPlace` anchors that slab
lowered (follows where clicked, holds via anchor = NEVER-POP-down) instead of freezing flat. Gated to a
-0.5 clicked surface (compound -1.0 side stays on the RC3 path). Guard preserved + tested: clicking the
FLAT GROUND's top beside a lowered block still stays 0.0 (A1, you aimed at the ground). Headless RED→GREEN
(`useOnSlabClickingLoweredFaceWithSolidGroundBelowFollowsToMinusHalf` -0.5), **106/106**. Jar 196,360 B
re-staged to BOTH Modrinth profiles (pre-fix 0.4.1 → `.bak-prewysiwyg`). **PENDING: Julia live-confirm
both halves.** Still queued: vegetation-flush-on-TS, step-up-collision (minor).

### 2026-06-19 (cont.) — redstone torch particle + TS vegetation flush (jar re-staged)

Julia LIVE-CONFIRMED the WYSIWYG side-click fix. Two more live findings fixed (`199bc268`):
- **Redstone torch particle 0.5 above the lowered model.** RedstoneTorchBlock extends BaseTorchBlock (own
  animateTick), so TorchParticleMixin (on TorchBlock) never covered it. New `RedstoneTorchParticleMixin`
  (slabbed.mixins.json) re-emits the lit DustParticleOptions.REDSTONE at y+0.7+dy (vanilla jitter), cancels
  vanilla. Wall variant overrides animateTick → unaffected.
- **Vegetation not flush on TS.** P0.4 directCustom lowered VegetationBlock -0.5 AND TS positions it via its
  own SlabOffsetModel = double-offset (sunk). Fix: exclude `VegetationBlock` from
  `isDirectCustomSlabSupportedObject` → getYOffset 0 on TS → TS's offset is the only one (flush). No-op
  without TS; vanilla-slab veg already 0.
Both client-render/TS-only (no headless test). 106/106 gametests, client-load smoke clean (both mixins
apply). Jar 197,733 B re-staged BOTH profiles (prior → `.bak-prevegtorch`). **PENDING Julia live-confirm:
redstone torch dust at the head + flowers/grass flush on TS.** Remaining: step-up-collision (minor).

### 2026-06-19 (cont.) — TS vegetation root cause (UPPER-half split) + a FALSE-GREEN caught

Julia: veg still -0.5 on TS despite the directCustom VegetationBlock exclusion (and confirmed it was the
right jar — the redstone-torch fix worked). A 4-probe + synthesis workflow found the real root cause:
- **The double-tall plant's UPPER half**, not directCustom. `shouldOffset` UPPER branch used a BARE
  `isBottomSlab(pos.below(2))` with NO TS gate; a TS slab extends SlabBlock → returns true →
  `shouldOffset(UPPER)=true` → the geometric lane in getYOffsetInner falls through to a hardcoded
  `return -0.5`. The LOWER half was already TS-gated (hasBottomSlabBelow → shouldSkipSlabSupport → 0.0),
  so the plant SPLIT: lower flush, upper lowered. (The HUD `half=LOWER` was a red herring — it's the
  crosshair-relative half, not DOUBLE_BLOCK_HALF; Julia was targeting the UPPER blockstate.)
- **Fix (`9cce42ce`):** gate the UPPER-half `isBottomSlab` for `VegetationBlock` on a TS surface only
  (`shouldSkipSlabSupport(belowTwo)`) → flush on TS; doors and vanilla slabs unchanged (still -0.5).
- **FALSE GREEN caught:** `doubleTallPlantsOnBottomSlabObservedFlush` read 0.0 because the plant blocks
  DESPAWN to air on a bare slab (setBlock neighbour-break) → it was measuring AIR, not vegetation. The
  real value is -0.5 on a vanilla slab. Test de-false-greened (synthetic-state read) + new fingerprint
  `vegetation_lower_on_slab=-0.5`. Lesson: a plant placed via setBlock on a slab does not survive — assert
  on the explicit BlockState, not the world readback.
107/107 gametests. Jar 197778 B re-staged BOTH profiles (prior → `.bak-prevegupper`). **PENDING Julia
live-confirm: sunflower/tall-grass on TS — BOTH visual halves read dy=0.000 flush.**

### 2026-06-19 (cont.) — VS+TS combining DEFERRED (Julia) + doc sync

Julia: "combine VS and TS?" → recovered the history and DEFERRED (post-release, by design). State:
- **Vanilla-slab-on-TS already combines** (P0.4 directCustom): a vanilla slab on a TS BOTTOM_LIKE surface
  lowers −0.5 (TOP slab → −1.0) and merges into a full-looking block = the "mixed slab".
- **TS-slab combining (TS-on-vanilla, TS+TS, deep chains) deferred.** Reason: TS blocks are categorically
  `TerrainSlabsCompat.shouldSkipOffset` (any `terrain_slabs`/`terrainslabs` id → getYOffset 0). That
  exclusion is load-bearing — it's the world-hole guard (Slabbed lowering TS terrain tore see-through holes).
  Relaxing reaches terrain rendering → scoped post-release feature (selective subject-only un-exclusion +
  flag + heavy live terrain test); −1.0 pick-raycast cap also limits deep chains. (1.21.1 sibling reached
  the same defer.)

**Pre-release status: essentially CLEAN on `0.4.1-beta.1+26.1.2-port`.** WYSIWYG law satisfied (side-click
follow `5383e4a2` live-confirmed), redstone torch (`199bc268`), vegetation flush on TS (`9cce42ce`) — all
Julia LIVE-CONFIRMED. 107 gametests. Jar 197778 B staged both profiles. Remaining = go/no-go + push.
Known minors (deferred): step-up-collision on lowered slabs (collision stays vanilla by design); full VS+TS
combining (above). **Doc sync this turn:** HANDOFF (orientation + CURRENT STATE rewrite + P0.3/P0.4/P4
status), CHANGELOG (0.4.1 fixes + deferred), LESSONS_INDEX (S10 false-green-on-block-despawn),
RELEASE_SANITY_CHECKLIST (Tier-2 live-confirmed). NOTE: a parallel thread also committed the veg fix +
de-false-green; coordinate (working tree had concurrent edits).
