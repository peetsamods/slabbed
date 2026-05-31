# SLABBED Bug Blasters

Status: consolidated replacement for all scattered Bug Blaster sources.
Updated: 2026-05-07.

This file contains two kinds of entries:

1. **Bug Blaster Doctrine** — reusable rules learned from recurring failures.
2. **Bug Blaster Cases** — concrete doctrine-grade bugs with mechanism, invariant, fix, proof, savepoint, and status.

Do not declare a Bug Blaster final/fixed until proof has been obtained and a savepoint has been completed.

## Doctrine Index

| Doctrine | Core rule |
|---|---|
| False-Green Live Failure Rule | Automation passing does not override live failure. |
| Red Proof Before Rescue Expansion | Rescue must be proven necessary before broadening. |
| Debug Flags Must Not Leak | Debug tools off by default; no release leakage. |
| One Symptom, One Layer | Name and fix exactly one failing layer. |
| Placement Is Not Survival | Placement alone never proves survival. |
| Triad Or It Did Not Happen | Model, outline, and raycast must agree. |
| Savepoint Before Final BB | Proof without commit/tag/push is not final. |
| Proof Fixtures Must Mirror Live Source Truth | A proof must use the same source truth as the live repro, not manually promoted or artificial support state. |

## Doctrine — False-Green Live Failure Rule

If automated proof passes but live play still shows the bug, stop implementation immediately.

Required sequence:

1. Record the live failure exactly.
2. Audit why automation missed it.
3. Identify failing layer: placement, survival, model, outline, raycast, dy, rescue, or proof gap.
4. Roll back any fix that passed automation but did not change live behavior.
5. Add a red proof that fails for the same reason live play failed.
6. Implement the smallest fix that turns it green.
7. Run proof again.
8. Live retest remains final authority for visual, targeting, and feel bugs.

A test that only proves “the block exists” is not enough for wrong visual height, outline mismatch, raycast mismatch, wrong-surface placement feel, repeat-click/ghost-face behavior, or live geometry mismatch.

## Doctrine — Red Proof Before Rescue Expansion

Do not broaden rescue logic just because a lowered object looks wrong or feels hard to click.

Before adding or expanding rescue, prove:

1. The bug is truly a targeting ownership problem.
2. Visible object, outline, and intended interaction disagree.
3. Existing dy / shape / placement logic cannot explain the failure.
4. The target class has a real ownership signal.
5. The rescue will not steal unrelated nearby targets.
6. Known no-rescue boundaries remain intact.

No rescue from generic slab support alone. No rescue from generic lowered visuals alone. No packet/interact rewrite as substitute for ownership proof.

## Doctrine — Debug Flags Must Not Leak Into Normal Runs

Debug tools may exist, but must not be silently enabled during normal play or normal `runClient` unless explicitly required.

Before committing any debug overlay, trace, renderer, fixture, probe, or visual helper, verify:

1. explicit flag or development-only gate
2. default state off
3. no hardcoded normal Gradle run args
4. no gameplay behavior changes
5. deliberate enable path
6. disabling leaves selection, targeting, placement, and rendering intact

Normal play should show the mod, not the scaffolding.

## Doctrine — One Symptom, One Layer

Do not treat every visible weirdness near slabs as the same bug.

Name the failing layer:

- placement
- survival
- model
- outline
- raycast
- crosshair rescue
- block entity renderer
- item/use behavior
- debug overlay/tooling
- proof gap

Forbidden: “while we’re here” patches, bundled torch/slab/dy/debug changes, rescue bundled into triad alignment, survival bundled into placement unless the pop-off proof requires it.

If the layer is uncertain, the next slice is audit-only.

## Doctrine — Placement Is Not Survival

A block that places successfully is not proven fixed.

Required proof:

1. Place successfully.
2. Trigger neighbor update.
3. Break/replace nearby support where relevant.
4. Reload or relog for delayed-failure categories.
5. Confirm it remains only when genuinely supported.
6. Confirm it still fails when truly unsupported.

No tag from placement alone.

## Doctrine — Triad Or It Did Not Happen

For slab-lowered or slab-shifted behavior, visual correctness is not one surface.

All three must agree:

1. Model
2. Outline
3. Raycast

No model-only offset fix. No outline-only feel fix. No raycast rescue pretending to solve visual drift. No duplicate dy logic. No tag if any triad surface is unproven.


## Doctrine — Proof Fixtures Must Mirror Live Source Truth

A green proof can be false if the fixture gives the target extra authority that the live repro does not have.

Before trusting a proof, confirm:

1. source block state matches the live source state
2. persistent anchor/carrier truth is not manually added unless live has it
3. support below/around the source matches live
4. held item, face, hit vector, and place position match live
5. teardown expectations still pass

If live uses a dynamic lowered lane source, the proof must not promote that source into persistent carrier truth.

## Doctrine — Savepoint Before Final Bug Blaster

A Bug Blaster may be a candidate with proof pending or savepoint pending.

It is only final/fixed after:

- mechanism known
- invariant named
- fix implemented
- proof obtained
- commit created
- annotated tag created
- branch pushed if required
- tag pushed if required
- final tree verified

## Case Index

| Case | Status | Savepoint |
|---|---|---|
| World vs Render Region Anchor Split | Fixed | `7a83fa5`, `save/persistent-direct-slab-anchor-bridge` |
| Client Anchor Mirror Copy-On-Write Sync | Fixed | `7429aec`, `save/client-anchor-mirror-fix` |
| Client Anchor Mirror Gap | Superseded by copy-on-write sync | pending old diagnostic |
| Anchored FB Ghost-Hitbox Rescue | Fixed | `408873f`, `save/anchored-fb-interaction-stack` |
| Slab-Held Anchored FB Ownership Guard | Fixed | `a1d8dd5`, `save/bs-fb-slab-held-fb-selection-fix` |
| BS-FB-0.5S Side Slab Dy Persistence | Fixed | `930ffb3`, `save/bs-fb-05s-side-slab-persistence` |
| BS-FB-0.5S Side Slab Visible-Face Ownership | Fixed | `f57c0ab`, `save/bs-fb-05s-side-slab-comfort-fix` |
| Torch BS-FB-0.5S Release Closure | Fixed | `369f244`, `save/torch-bs-fb-05s-release-closure`; main `5ad71ca` |
| 0.5S + Chain Raycast Ownership | Fixed | `9026674`, `save/05s-plus-chain-raycast-ownership` |
| Vertical Stack Update Jump Fix | Fixed | `1b74eac`, `save/vertical-stack-update-jump-fix` |
| Vertical Chain Hitbox Ownership Fix | Fixed | `64db1b1`, `save/vertical-chain-hitbox-ownership-fix` |
| Slab-Held Vertical Chain Ownership Fix | Fixed | `eb8dd50`, `save/slab-held-vertical-chain-ownership-fix` |
| Lowered Slab Face Placement Acceptance | Fixed | `ddd8cea`, `save/lowered-slab-face-placement-acceptance` |
| Lowered Slab Lane Grammar and Double Ownership | Fixed | `61561ef`, `save/lowered-slab-lane-grammar` |
| BSFB Adjacent Full-Block Lowered Lane Inheritance | Fixed | `7f5df3d`, `save/bsfb-adjacent-fullblock-inheritance` |
| Top-Face Slab Click Remapped Into Side Placement | Superseded by clean Phase19 path | old dirty WIP pending |
| Slab-Held Side-Slab Retarget Stole Top-Face Full-Block Hit | Superseded by clean Phase19 path | old dirty WIP pending |
| Real-Placed Lowered BOTTOM Slab Persistence Gap | Fixed | `fe07714`, `save/real-placed-lowered-bottom-slab-persistence` |
| Double Slab Full-Height Carrier Parity | Fixed | `f2d7e1c03ca2cc9442bbfc00a73ed140503bed33`, `save/double-slab-full-height-carrier` |
| Phase19 Slab-Held Top-Hit Preservation | Fixed | `6e774bdfab5ea73ead7a2379cac1df4d8527dd6a`, `save/phase19-slab-held-top-hit-preservation` |
| Lowered DOUBLE Side-Lane Inheritance | Fixed | `401d7b8`, `save/lowered-double-side-lane-inheritance` |
| Slab-Held Lowered-Owner Retarget | Fixed | `5060f9a5454bdeb4c5ca2a41e03327da91692548`, `save/slab-held-lowered-owner-retarget` |
| Render Region Lowered Slab Carrier Bridge | Fixed | `4eab0d8`, `save/lowered-carrier-render-view-bridge` |
| Slab-Held Retarget Parity Improvements | Fixed | `571ba89`, `save/slab-held-retarget-parity-improvements` |
| Lowered Slab Face Placement Inheritance | Fixed | `04744e1`, `save/lowered-slab-face-placement-inheritance` |
| Release Jar Classpath Closure | Fixed | `d1417ff`, `save/main-release-classpath-closure` |
| Release Debug Bridge Scout | Historical partial | `c24d343`, branch `work`, tag not reported |

---

## World vs Render Region Anchor Split

Invariant: Any persistent anchor used for lowered visuals must be readable by every visual-triad surface that needs it: model, outline, raycast, and targeting. A proof that queries `World` is not enough if the live render path uses a different view type.

Root cause: Persistent direct slab anchors were stored and synced correctly, but model rendering read through a `ChunkRendererRegion` / non-`World` `BlockView`, so anchor lookup returned false there while `mc.world` proofs passed.

Fix: Added client render-view bridge. Common anchor code keeps a nullable client lookup hook; client code resolves anchors through `MinecraftClient.world` when incoming view is not `World`.

Proof: Live trace showed model path got `ChunkRendererRegion` with `dy=0.0` while real client world had anchor. After bridge, model trace showed `dy=-0.5`; Julia confirmed BS → FB → break BS stayed visually lowered. `clean build` and `runClientGameTest` passed.

Savepoint: commit `7a83fa5`, tag `save/persistent-direct-slab-anchor-bridge`.

Status: Fixed.

## Client Anchor Mirror Gap

Invariant: A persistent lowered full-block anchor must exist on client whenever server says the block is anchored.

Root cause: Server stayed anchored after support break, but client mirror did not receive/expose that anchor. Client looked correct only while physical bottom-slab fallback existed; after support removal, client dy collapsed to `0.0`.

Fix: Pending in this old diagnostic entry; superseded by Client Anchor Mirror Copy-On-Write Sync.

Proof: Live diagnostic showed server `anchored=true`, `fullDy=-0.5`; client `clientAnchored=false`, `clientDy=0.0`, outline `[0.000, 1.000]`.

Savepoint: pending old diagnostic.

Status: Mechanism identified; superseded by fixed copy-on-write sync.

## Client Anchor Mirror Copy-On-Write Sync

Invariant: Persistent lowered full-block anchors must mirror to client whenever server anchor changes.

Root cause: `SlabAnchorAttachment.java` mutated the existing `LongOpenHashSet` in place, then passed the same object reference back to `chunk.setAttached(...)`. Fabric attachment sync compared old/new values and skipped sync because the same mutated set instance looked unchanged.

Fix: Changed anchor add/remove to copy-on-write: copy set, mutate copy, store/remove new value.

Proof: Before fix, server stayed `anchored=true` and `fullDy=-0.5` while client showed `clientAnchored=false`, `clientDy=0.0`. After fix, client showed `clientAnchored=true`, `clientDy=-0.5`, outline `[-0.500,0.500]`. `compileJava compileGametestJava`, `runClientGameTest`, and live smoke passed.

Savepoint: commit `7429aec`, tag `save/client-anchor-mirror-fix`.

Status: Fixed.

## Anchored FB Ghost-Hitbox Rescue

Invariant: If a full block remains visually lowered by persistent slab anchoring, its visible lowered body must own selection before farther blocks behind it. Rescue must protect visible ownership without stealing slab-placement intent.

Root cause: Vanilla targeting often hit a real block behind the lowered FB instead of `MISS`; first rescue only handled `MISS`. Broader rescue then hijacked slab placement while holding slabs.

Fix: Scan ray for anchored lowered full-block candidates and retarget only when lowered outline hit is closer than vanilla target. Add slab-item guard to preserve BS-FB-0.5S placement intent.

Proof: Live repro showed lower half selected behind block. After fix, compile/gametest passed and Julia confirmed lower-half selection worked and slab-held placement was restored.

Savepoint: commit `408873f`, tag `save/anchored-fb-interaction-stack`.

Status: Fixed.

## Slab-Held Anchored FB Ownership Guard

Invariant: Holding a slab item must not globally disable lowered full-block ownership. Slab placement intent wins only when an actual side-slab candidate is equal-or-closer.

Root cause: Broad slab-held guard skipped anchored-FB rescue whenever player held slab item, leaving targeting on wrong underneath/behind block.

Fix: Replaced broad item bailout with tie-break-aware rescue chooser.

Proof: Case F resolved slab-held lower-half FB rays to anchored FB; Case A placement and Case E side-slab targeting stayed green. Build and client gametest passed; Julia live-tested after savepoint.

Savepoint: commit `a1d8dd5`, tag `save/bs-fb-slab-held-fb-selection-fix`.

Status: Fixed.

## BS-FB-0.5S Side Slab Dy Persistence

Invariant: A side slab attached beside an anchored lowered full block must preserve `dy=-0.5` as long as the lowered full block remains valid and anchored, even if original bottom slab support is removed.

Root cause: `SlabSupport.java` recomputed adjacent side-slab lowering only from physical bottom-slab support. It did not recognize anchored lowered full block as inherited support.

Fix: Widen adjacent-side-slab lowering helper so neighboring solid full block counts if still on bottom slab or already anchored.

Proof: Before fix `postBreakSlabDy=0.0`; after fix `postBreakSlabDy=-0.5`. Compile and client gametest passed.

Savepoint: commit `930ffb3`, tag `save/bs-fb-05s-side-slab-persistence`.

Status: Fixed.

## BS-FB-0.5S Side Slab Visible-Face Ownership

Invariant: A lowered side slab must own the visible face the player aims at. If model and outline occupy lowered space, targeting must not return `MISS` through that visible surface.

Root cause: Vanilla ray targeting could terminate as `MISS` before testing the slab’s offset outline at lowered Y.

Fix: Added narrow client retarget for lowered `BOTTOM` slabs using existing offset outline shape as ownership source. Corrected Case E eye point.

Proof: Case E rays went from `MISS` to side slab target. Compile and client gametest passed.

Savepoint: commit `f57c0ab`, tag `save/bs-fb-05s-side-slab-comfort-fix`.

Status: Fixed.

## Torch BS-FB-0.5S Release Closure

Invariant: Torch body, flame, outline, raycast, and rescue ownership must align on lowered slab assemblies.

Root cause: Layered triad failure. `SlabSupport.getYOffset(...)` capped compound floor torch at `-0.5` when `-1.0` was needed; particles hardcoded `-0.5`; rescue accepted only `dy == -0.5`; wall torch particles were not covered.

Fix: Red proof for compound torch dy, update `SlabSupport` for compound `dy=-1.0`, make torch particles use `SlabSupport.getYOffset(...)`, widen visual ownership to negative dy, add comfort selection coverage, and narrow wall torch particle correction.

Proof: Red proof failed then passed; compile/gametest passed; Julia confirmed floor torch selection, wall torch flame, and acceptable reach.

Savepoint: commit `369f244`, tag `save/torch-bs-fb-05s-release-closure`; main merge commit `5ad71ca`, tag `save/main-torch-bs-fb-05s-release-closure`.

Status: Fixed.

## 0.5S + Chain Raycast Ownership

Invariant: Visual ownership must follow visible lowered body when vanilla ray traversal skips native voxel; rescue only for proven narrow ownership case.

Root cause: Lowered chain model/outline occupied `201.0..202.0`, but vanilla DDA hit neighboring lowered assembly at world Y `201` and never visited chain native cell `85,202,0`.

Fix: Chain-only crosshair retarget for vertical chains with negative dy on lowered BOTTOM 0.5S support beside anchored lowered FB; disabled while holding slabs and gated by actual lowered-outline hit/closeness.

Proof: Lower, center, and top rays resolved to chain; no-rescue chain-on-FB and crafting-table cases remained slab-owned. Compile/gametest passed.

Savepoint: commit `9026674`, tag `save/05s-plus-chain-raycast-ownership`.

Status: Fixed.

## Vertical Stack Update Jump Fix

Invariant: Slab-supported vertical full-block chains must preserve coherent lowered anchor truth through neighbor updates.

Root cause: Upper blocks were transiently lowered by support-column scan; breaking intermediate block made air stop scan and upper block jumped upward.

Fix: Extended persistent anchor qualification narrowly to ordinary solid full blocks placed on already lowered ordinary full-block slab chain.

Proof: Material matrix kept upper `dy=-0.5`, visual box stable, `jumpDelta=0.0`; compile/gametest passed.

Savepoint: commit `1b74eac`, tag `save/vertical-stack-update-jump-fix`.

Status: Fixed.

## Vertical Chain Hitbox Ownership Fix

Invariant: Empty-hand selection must preserve actual closest lowered outline owner; native-cell seam rewrites may only run where slab-held placement intent needs them.

Root cause: Correct upper block lowered outline was found, then native-cell seam rewrite reassigned hit down to anchored lower block, making each lower visible quarter owned by block below.

Fix: Anchored full-block retarget chooses closest valid lowered outline hit; old native-cell seam rewrite limited to slab-held placement.

Proof: B2/B3 lower/center/upper rays resolved to expected owners; ghost-window sweep green; compile/gametest passed.

Savepoint: commit `64db1b1`, tag `save/vertical-chain-hitbox-ownership-fix`.

Status: Fixed.

## Slab-Held Vertical Chain Ownership Fix

Invariant: Held slab placement priority must not steal visible lowered full-block ownership unless a real equal-or-closer side-slab placement candidate owns the interaction.

Root cause: Slab-held targeting rewrote valid upper lowered-outline hits back down to native lower cell.

Fix: Removed slab-held native-cell rewrite from anchored full-block retarget; kept real side-slab placement priority in candidate path.

Proof: Held-item ownership table green for B2/B3 lower-quarter rays with empty hand, stone, and stone slab; ghost-window and material matrix green; compile/gametest passed.

Savepoint: commit `eb8dd50`, tag `save/slab-held-vertical-chain-ownership-fix`.

Status: Fixed.

## Lowered Slab Face Placement Acceptance

Invariant: A slab placed against visible lowered slab face must inherit lowered lane and be accepted when only collision blocker is placing player.

Root cause: Placement remap skipped non-solid slab targets; lowered-lane placement was then rejected because future lowered slab collision intersected only acting player.

Fix: Narrow lowered-slab horizontal-face remap, same-type lowered slab lane inheritance, and acting-player-only placement acceptance.

Proof: `vanilla_illegal_slab_to_slab_placement` green with `dy=-0.5`; ghost-window green; compile/gametest passed.

Savepoint: commit `ddd8cea`, tag `save/lowered-slab-face-placement-acceptance`.

Status: Fixed.

## Lowered Slab Lane Grammar and Double Ownership

Invariant: Lowered slab lanes must preserve state through compatible chains and merges. Lowered `DOUBLE` side-lane slabs must own visible lower half. Lower-half clicks on lowered `DOUBLE` must produce `BOTTOM dy=-0.5` before merging to `DOUBLE dy=-0.5`.

Root cause: Lowered slab behavior was split across partial local rules: adjacent lowering only direct support, lowered `DOUBLE` excluded from retarget gate, lowered `DOUBLE` side-hit placement fell through generic logic.

Fix: Canonical lowered slab lane grammar for TOP/BOTTOM/DOUBLE; compatible inheritance through DOUBLE; lowered DOUBLE retarget ownership; lowered-DOUBLE placement branch mapping lower half to BOTTOM and upper half to TOP.

Proof: compile/gametest passed; live goblin found no spooky ghosts; inspect logs showed lowered full block, side TOP, lowered DOUBLE, first-click BOTTOM, second-click DOUBLE, continued chain placements at `dy=-0.5`.

Savepoint: commit `61561ef`, tag `save/lowered-slab-lane-grammar`.

Status: Fixed.

## BSFB Adjacent Full-Block Lowered Lane Inheritance

Invariant: Side-placed ordinary full block may inherit lowered lane truth only from valid anchored/lowered ordinary full-block neighbor. Placement authoring must create legal lowered state before render/outline/raycast/rescue explain it.

Root cause: Held ordinary full blocks never entered lowered side-placement authoring path. Slab-only remap meant holding `STONE` against anchored lowered `STONE` fell through vanilla placement and produced normal height `dy=0.0`.

Fix: Narrow side-adjacent lowered full-block anchor finalization path for successful ordinary full-block placements against already anchored `dy=-0.5` full-block neighbor.

Proof: Compile/gametest passed. Isolated red proof passed with `stone dy=-0.5 anchored=true lowered=true`. Julia confirmed adjacent full-block jumping resolved.

Savepoint: commit `7f5df3d`, tag `save/bsfb-adjacent-fullblock-inheritance`.

Status: Fixed.

## Top-Face Slab Click Remapped Into Side Placement

Invariant: A top-face click must preserve top-face placement intent. Slabbed may remap slab-held placement to side lane only after proving player targeted a side/lower-band surface.

Root cause: `BlockItemPlacementIntentMixin` inferred horizontal direction from `UP` hits near X/Z edge before proving true side/lower-band placement.

Fix: Tightened placement-intent remap guard so `UP` hits on ordinary lowered full blocks keep top intent.

Proof: Ultra2 Phase18 reproduced then passed. Compile/gametest passed.

Savepoint: old dirty WIP pending; superseded by clean Phase19 route.

Status: Historical / superseded by clean Phase19 preservation.

## Slab-Held Side-Slab Retarget Stole Top-Face Full-Block Hit

Invariant: Valid `UP` hit on anchored/lowered ordinary full block must own target. Slab-held side-slab retarget may only win for valid side/lower-band slab placement surface.

Root cause: Retarget tiebreak ran after vanilla had valid `UP` hit on anchored/lowered full block; existing guard rejected vertical faces, so hit was stolen by lowered `DOUBLE`.

Fix: Retarget-layer guard preserving slab-held `BLOCK` hits where `face == UP` and target is anchored/lowered ordinary full block.

Proof: Ultra2 Phase19 reproduced then passed; full Ultra2 and Mega passed; compile/gametest passed.

Savepoint: old dirty WIP pending; superseded by clean Phase19 preservation.

Status: Historical / superseded by clean Phase19 preservation.

## Real-Placed Lowered BOTTOM Slab Persistence Gap

Invariant: Persistent lowered carrier truth must be consumed by dy authority without re-entering dy calculation. Legal real-placed lowered `BOTTOM` slab carriers must continue to read `dy=-0.5` for model, outline, and targeting.

Root cause: A real-placed lowered `BOTTOM` slab above a lowered bridge gained persistent carrier truth after support break, but `SlabSupport.getYOffsetInner(...)` asked for that truth through a guard-dependent path, so dy authority still returned `0.0` and the slab jumped.

Fix: Added `SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive(...)` for the bottom-slab persistence case, and updated the `SlabSupport.getYOffsetInner(...)` bottom-slab branch to use that non-recursive fact. This avoided recursive `SlabSupport.getYOffset(...)` / side-lane guard dependency while preserving placement, rescue, model, outline, and raycast behavior.

Proof: `REAL_PLACED_LOWERED_BOTTOM_SLAB_PERSISTENCE_AFTER_BRIDGE_BREAK` reproduced RED from Julia’s live micro test. After the fix it was green with `slabAbove state=stone_slab[type=bottom]`, `persistentLoweredSlabCarrier=true`, `dy=-0.5`, `lowered=true`, `modelDy=-0.5`, `outlineDy=-0.5`, and `targetDy=-0.5`. `compileJava compileGametestJava`, `runClientGameTest --console plain`, and `git diff --check` passed.

Savepoint: commit `fe07714`, tag `save/real-placed-lowered-bottom-slab-persistence`, branch `integrate/phase19-into-side-slab-top-support`; branch pushed yes, tag pushed yes.

Status: Fixed.

## Double Slab Full-Height Carrier Parity

Invariant: `DOUBLE` slab may act as full-height/full-cube-equivalent only inside proven lowered carrier/support authority, not through global solidity/shape/model/outline/raycast/rescue lies.

Root cause: Ordinary full blocks and `DOUBLE` slabs diverged at Slabbed carrier predicate; visually full-height `DOUBLE` slabs did not qualify like full blocks in lowered bridge/adjacency decisions.

Fix: Added `SlabSupport.isFullHeightLoweredCarrier(...)`, admitting `DOUBLE` only when already proven lowered carrier; reused in `SlabAnchorAttachment`; added A/B/C proof for full+full, full+DOUBLE, DOUBLE+DOUBLE.

Proof: compile/gametest passed; Julia live-tested three bridge setups and confirmed double-slab cases felt good.

Savepoint: commit `f2d7e1c03ca2cc9442bbfc00a73ed140503bed33`, tag `save/double-slab-full-height-carrier`.

Status: Fixed.

## Phase19 Slab-Held Top-Hit Preservation

Invariant: Valid top-face hit on anchored lowered full block must remain owned by that block unless proven equal-or-closer legal side-placement candidate owns interaction.

Root cause: Slab-held side-slab retargeting could steal valid `UP` hit because placement-intent guard rejected all vertical faces instead of only `DOWN`.

Fix: Clean Phase19 guard preserving slab-held anchored lowered full-block `UP` hits while excluding `DOWN`; narrow proof fixture using dirty reproducer aim mechanics.

Proof: compile/gametest passed; isolated Phase19 passed with vanilla and final target both `24,201,0/up`.

Savepoint: commit `6e774bdfab5ea73ead7a2379cac1df4d8527dd6a`, tag `save/phase19-slab-held-top-hit-preservation`.

Status: Fixed.

## Lowered DOUBLE Side-Lane Inheritance

Invariant: Lowered `DOUBLE` slabs may act as side-lane carriers only through recursion-safe support authority. Lowered slab lanes preserve `dy=-0.5` through compatible side placements and merges without global solidity or self-looping carrier checks.

Root cause: Lowered DOUBLE side-support checks re-entered same side-lane support loop, causing `StackOverflowError`; original side-lane bug let adjacent slabs beside lowered DOUBLEs collapse to `dy=0.0`.

Fix: In `SlabSupport.isLoweredDoubleSlabCarrierForSideSupport(...)`, keep persistent-anchor fast path, then recurse only downward with `allowSideLane=false`; add boundary proof to default `runClientGameTest` routing.

Proof: compile/gametest passed; default logs confirmed `[LOWERED_DOUBLE_BOUNDARY_GREEN]`; no `StackOverflowError`; savepoint completed clean.

Savepoint: commit `401d7b8`, tag `save/lowered-double-side-lane-inheritance`.

Status: Fixed.

## Slab-Held Lowered-Owner Retarget

Invariant: Holding a slab may protect real slab-placement intent, but must not suppress valid lowered-owner correction when same visible owner is proven by full-block-held path.

Root cause: Slab-held targeting suppressed the same lowered-owner retarget that worked while holding full block, so identical camera ray had normal range with `stone` held but wrong owner with `stone_slab` held.

Fix: Narrowed slab-held suppression in `SlabbedRetargetTestHooks` so same-block face stability remains protected while different valid lowered owner can still win. Preserved MISS-angle comfort targeting, lowered DOUBLE ownership, and slab placement priority.

Proof: `RED_ITEM_SENSITIVE_SLAB_HELD_RANGE_JANK` reproduced live A/B; after fix both stone-held and slab-held resolved to same owner. compile/gametest passed. Julia live-tested and said “Yes, perfect!”

Savepoint: commit `5060f9a5454bdeb4c5ca2a41e03327da91692548`, tag `save/slab-held-lowered-owner-retarget`.

Status: Fixed.

## Render Region Lowered Slab Carrier Bridge

Invariant: Any persistent lowered carrier used for slab-lane visuals must be readable by every visual-triad surface that needs it: World, non-World render view, model, outline, raycast, and targeting.

Root cause: Persistent lowered slab carrier truth existed in real client `World` lookup but was not mirrored into non-`World` render-view/model paths, so legal lowered carrier states could collapse to `dy=0.0` for model/triad reads.

Fix: Wired `clientLoweredSlabCarrierLookup` in `SlabAnchorClientSync` and added missing `LOWERED_SLAB_CARRIER_TYPE` rerender handling, mirroring the existing persistent-anchor bridge pattern without changing placement grammar, rescue, global solidity, or model-dy ownership.

Proof: `RENDER_REGION_LOWERED_SLAB_CARRIER_BRIDGE` went RED before the patch: World lookup stayed lowered while non-World/render-view/model path lost carrier truth and `modelDy` collapsed to `0.0`. After the patch, both seeded legal BOTTOM carrier and lowered DOUBLE carrier cases were GREEN with `persistentLoweredSlabCarrierWorld=true`, `persistentLoweredSlabCarrierNonWorld=true`, `worldDy=-0.5`, `modelDy=-0.5`, `outlineDy=-0.5`, `targetDy=-0.5`, and `clientLoweredSlabCarrierLookup=true`. `compileJava compileGametestJava`, `runClientGameTest --console plain`, and `git diff --check` passed.

Savepoint: commit `4eab0d8`, tag `save/lowered-carrier-render-view-bridge`, branch `integrate/phase19-into-side-slab-top-support`; branch pushed yes, tag pushed yes.

Status: Fixed.

## Slab-Held Retarget Parity Improvements

Invariant: Holding a slab must not globally suppress valid lowered owner targeting. Slab-held protection may preserve true top/slab-placement intent only when no proven same-ray visible owner should win.

Root cause: Slab-held targeting preserved old lowered slab / `UP` / `MISS` paths before comparing a proven visible lowered owner. Normal block-held targeting could reach `scan-side-slab-fired` or anchored full-block rescue, but `stone_slab` held could suppress the same owner.

Fix: Added proof-backed `sideOwnerWouldWin` and anchored full-block owner comparisons so slab-held targeting can let the same visible owner win while preserving Phase19 true-top protection. The fix explicitly avoided nearest-edge ownership and avoided broad slab-held rescue expansion.

Proof: `compileJava compileGametestJava` passed, `runClientGameTest --console plain` passed, and Julia live-tested the difficult slab-vs-block targeting lanes and reported the major mismatch was fixed enough to save. Phase19 true-top protection remained green.

Savepoint: commit `571ba89`, tag `save/slab-held-retarget-parity-improvements`; branch pushed yes, tag pushed yes.

Status: Fixed.

## Lowered Slab Face Placement Inheritance

Invariant: A visible lowered slab face placement must author the placed object into the correct legal lowered state. Placement success is not enough; placed dy/anchor truth, preview/placement agreement, orphan teardown, and live feel must agree.

Root cause: Targeting correctly selected a visible lowered slab face, but placement authored adjacent blocks in vanilla height. For slabs, placed slab initially became `dy=0.0`, causing a ghost-face mismatch. For ordinary blocks/logs/grass, placement also authored `dy=0.0` until the source qualifier was corrected. A false-green proof had made the clicked source slab persistent, while the live source was non-persistent but legally lowered by a carrier below.

Fix: Slab placement against a lowered slab face now inherits the dynamic lowered slab lane without making the placed slab persistent. Ordinary full blocks placed against a legal non-persistent lowered bottom slab source now use the established anchored full-block path. Post-placement slab-held targeting now respects the newly placed anchored full-block owner instead of preserving the old lowered slab face. The proof fixture was corrected so it no longer manually promotes the clicked source slab into persistent carrier truth.

Proof: `LIVE_CLICK_PAIR_BOTTOM_SLAB_LANE_INHERITANCE` passed with placed slab `dy=-0.5` and `persistentLoweredSlabCarrier=false`. `LIVE_CLICK_PAIR_FULL_BLOCK_LANE_INHERITANCE` passed with `sourcePersistentLoweredSlabCarrier=false`, placed full block `dy=-0.5`, and `anchored=true`. `runOrphanedLoweredLaneSupportRemovalCase` stayed green with orphaned lane teardown returning to `dy=0.0`. Phase19 true-top protection remained green. Julia live-tested and said “Perfect! Save.”

Savepoint: commit `04744e1`, tag `save/lowered-slab-face-placement-inheritance`; branch pushed yes, tag pushed yes.

Status: Fixed.

## Proof Gap from Persistent Test Source

Invariant: Proof fixtures must mirror live source truth. A proof that adds persistent state not present in the live repro can turn a real live failure into a false green.

Root cause: The first full-block lane inheritance proof manually promoted the clicked lowered slab source to `persistentLoweredSlabCarrier=true`. Julia’s live source was instead a non-persistent lowered bottom slab backed by a lowered carrier below. The proof passed but live full-block placement still authored `dy=0.0`.

Fix: Removed the forced persistent source promotion from the proof shape, asserted `sourcePersistentLoweredSlabCarrier=false`, and added the narrow non-persistent source qualifier required by live.

Proof: After the proof was corrected, both slab and full-block lane inheritance proofs passed, orphan teardown stayed green, and Julia live-tested the fixed behavior successfully.

Savepoint: Folded into commit `04744e1`, tag `save/lowered-slab-face-placement-inheritance`.

Status: Fixed as part of Lowered Slab Face Placement Inheritance.

## Release Jar Classpath Closure

Invariant: Public release jar must not only exclude dev/debug classes; packaged runtime entrypoints, mixins, and production classes must avoid hard-linking excluded classes.

Root cause: Release jar excluded dev/debug classes, but production runtime classes still directly referenced excluded classes (`SlabbedDevCommands`, `GapFillerOverlay`, `ScreenshotCaptureService`, debug helpers), causing `NoClassDefFoundError` at launch.

Fix: Replace direct runtime refs with guarded reflection or production-safe bridges; scan packaged production class constant pools for excluded symbols.

Proof: Private Modrinth launch failed on excluded classes before. After closure, `clean build`, `runClientGameTest`, jar purity, hard-reference scan, metadata, and private Modrinth launch all passed.

Savepoint: commit `d1417ff`, tag `save/main-release-classpath-closure`; release tag `release/0.2.0-beta.2` moved to `d1417ff`.

Status: Fixed.

## Release Debug Bridge Scout

Invariant: Release artifact purity requires classpath closure; packaged runtime bytecode must not hard-link excluded dev/debug classes.

Root cause: Production runtime classes directly referenced intentionally excluded debug classes.

Fix: Added `BsFbLiveTraceBridge`, removed direct `com.slabbed.debug.*` references from `SlabAnchorAttachment`, routed debug init through reflection, excluded `com/slabbed/client/debug/**` from release jars.

Proof: `compileJava`, `jar`, `remapJar`, and `jdeps` scan passed.

Savepoint: commit `c24d343`, branch `work`, tag pending/not reported.

Status: Historical partial; superseded by final Release Jar Classpath Closure case.
