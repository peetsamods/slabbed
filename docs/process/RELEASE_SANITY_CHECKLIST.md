# Slabbed — Release Sanity Checklist

Standardized, repeatable regression matrix. Two jobs:

1. **Catch REDs fast** when testing a new build — run the smoke set, then the full matrix for any family that changed.
2. **Compare old vs current** — the dy-fingerprint baseline (§3) turns "a behavior that used to be correct silently changed" into a one-line diff.

Run this *in addition to* the pre-release hygiene gate (§9), not instead of it.

---

## 1. Proof types — what actually proves each row

The single biggest past mistake was stamping everything "live" and then false-greening the visual rows. Every row below is tagged with the **cheapest sufficient proof**:

| Tag | Meaning | Who runs it | Reliable? |
|---|---|---|---|
| **GT** | Headless gametest — `SlabSupport.getYOffset` / `updateShape` / `noCollision` assertion | `./gradlew runGameTest` (Java 25) | Deterministic. Trust it. |
| **DY** | Live `/slabdy` numeric read — the dy value is the pass/fail | Agent can cruise this unattended | Reliable (the number doesn't lie) |
| **VIS** | Live screenshot visual judgment — flush/gap/ghost-window/connection | Human eye, or agent flagged-uncertain | **Unreliable from screenshots** — Julia's eye is the final gate |
| **FEEL** | Live movement/collision — clip-through, landing, pass-through | Human play | Agent cannot *feel* it; infer-from-screenshot only |
| **N/A** | Render-only entity offset — no headless path exists | Human eye only | The checklist is the SOLE safety net here |

### The three lanes

- **Lane 1 — Automated (GT).** ~80% of the dy/compound/freeze/connector/collision-presence rows. The agent runs `runGameTest` and reports pass/fail. No game driving. **This is where regressions should be caught first.**
- **Lane 2 — Live dy-cruise (DY).** The agent *can* run this unattended: `/setblock` fixture → `/tp` aim → screenshot → read `dy=`. One representative per family ≈ 15–20 rows.
- **Lane 3 — Human visual/feel (VIS / FEEL / N/A).** Genuinely needs Julia's eye — flush contact, ghost windows, collision feel, and the three entity render mixins that no gametest can see. The agent may pre-screen but must flag uncertainty, never green it.

---

## 2. Smoke set — run this FIRST on every new build

One representative per behavior family. If any of these is RED, stop and triage before the full matrix. ~15 rows; ≈ the whole product surface in one pass.

| # | Family | Fixture | Expected | Proof |
|---|---|---|---|---|
| S1 | Core lower | `stone` on `stone_slab[bottom]` | `dy=-0.500` | GT + DY |
| S2 | Compound | `slab → stone → slab → stone` (L3) | `dy=-1.000` | GT + DY |
| S3 | Freeze / NEVER-POP | author flat `stone`, then slab below it | stays `0.000 FROZEN-FLAT` | GT + DY |
| S4 | Ceiling raise | `lantern[hanging]` under `oak_slab[top]` | `dy=+0.500`, flush | DY + VIS |
| S5 | Ceiling follow | `hanging_roots` under lowered support | follows to `-0.500` | GT + VIS |
| S6 | Connector break | `oak_fence` slab-height beside ground fence | does NOT connect | GT + VIS |
| S7 | Thin layer | `white_carpet` / `snow` on bottom slab | `dy=0.000` (not lowered) | GT + DY |
| S8 | Door/trapdoor | `oak_trapdoor[top]` under `oak_slab[top]` | flush, no gap | DY + VIS |
| S9 | Bed | bed with one half on a slab | BOTH halves lower | DY + VIS |
| S10 | WYSIWYG | aim UPPER half of `-0.5` block, place slab on side | lands at `-0.500` (not `-1.0`) | DY |
| S11 | Collision | walk into a lowered slab | stops at visual face, no clip | FEEL |
| S12 | DODO cull | lowered block from all angles | renders solid, no ghost window | VIS |
| S13 | Minecart | minecart on rail on bottom slab | rides flush, not floating | **N/A (eye only)** |
| S14 | TS compat | `stone` on a Terrain Slabs slab | `dy=0.000` flush, no world hole | DY + VIS |
| S15 | Reload | relog with an anchored lowered block | dy unchanged after relog | DY |

### 2.1 26.2 manual-failure queue

These rows came from Julia's `Proofs 26.2.pdf` review. They started as exact live/manual symptoms that could not be closed from headless `runGameTest` alone. Julia later found one release-blocking false-green in the chained speleothem family; the current branch has two headless red->green guards for that family staged for live retest, but pre-release hygiene must not resume until that fresh in-game pass is accepted.

| # | Failure | Expected | Proof |
|---|---|---|---|
| P26-1 | Lowered scaffolding acts like a wall | player can use/climb/pass through scaffolding as vanilla, while visual/targeting stay lowered | GT + FEEL + VIS |
| P26-2 | Minecart on lowered rails / lowered rail targeting | minecart renders/rides on the lowered rail, with targetable rail hitbox | GT + **N/A** + VIS |
| P26-3 | Glass panes | visual pane lowers to match the already-lowered raycast/outline | GT + VIS |
| P26-4 | Lowered pointed dripstone / sulfur stone targeting | narrow blocks remain targetable at their visible lowered body | GT + VIS |
| P26-5 | Lowered pointed dripstone / sulfur stone stacking | player can combine/stack the lowered narrow blocks where aimed | GT + DY + VIS |
| P26-6 | Raised chain column | raised chains connect continuously to each other and to the vanilla-dy block without merge/pop artifacts | GT + VIS |
| P26-7 | Raised lantern on raised chain | lantern hangs from the raised chain without smooshing/merging into it | GT + VIS |
| P26-8 | Fence placed against lowered fence | placement obeys the WYSIWYG target while preserving the intentional no-connection-across-step rule | GT + DY + VIS + policy check |

P26-4/P26-5 now have pointed-dripstone and sulfur-spike headless coverage: `Slabbed2612RestingDyTest.upwardPointedDripstoneOnBottomSlabLowersHalf`, `Slabbed2612RestingDyTest.loweredPointedDripstoneLowerBodyHasVisibleOwnerRescue`, `Slabbed2612RestingDyTest.upwardSulfurSpikeOnBottomSlabLowersHalf`, and `Slabbed2612RestingDyTest.loweredSulfurSpikeLowerBodyHasVisibleOwnerRescue`. The visible-owner rescue rows were RED because vanilla world clip missed the visible lower body while the direct outline hit succeeded. The fix routes upward speleothems through the existing lowered visible-owner retarget family. Green proof: `tmp/26-2-proof-fails/runGameTest-sulfur-spike-green.log` passed 116/116.

P26-5 chained downward speleothem follow-up: Julia's pre-release spin found chained speleothems could still merge. First, the red proof `tmp/runGameTest-speleothem-ceiling-bridged-chain-red.log` failed because lower pointed-dripstone and sulfur-spike segments under a ceiling-bridged iron-chain column inherited `dy=+0.5` while the upper segment stayed flush under the visible chain bridge; `tmp/runGameTest-speleothem-ceiling-bridged-chain-green.log` passed 128/128 after that subset was fixed. Julia then caught the direct top-slab speleothem-column case: `tmp/runGameTest-speleothem-direct-top-red.log` failed because lower pointed-dripstone and sulfur-spike descendants inherited `dy=+0.5` from the same top slab; `tmp/runGameTest-speleothem-direct-top-green.log` passed 130/130 after descendants stayed grid-height. Live retest is still required before marking this row release-closed again.

P26-1 now has red/green headless coverage for the two automatable scaffolding layers: `Slabbed2612CollisionDepthTest.loweredScaffoldingDoesNotInjectSolidHangingCollision` and `Slabbed2612CollisionDepthTest.loweredScaffoldingVisualVolumeIsClimbable`. The RED proof showed lowered scaffolding receiving generic solid hanging collision and not counting as climbable inside its lowered visual volume; the GREEN proof passed after scaffolding was excluded from generic hanging collision and lowered visual scaffolding was wired into `LivingEntity.onClimbable` / shift-descent checks. Current-jar live proof in `tmp/26-2-proof-fails/live-scaffolding-chain/` covers survival space/shift traversal: before space `Y=-59.5`, after space `Y=-55.0`, after shift `Y=-59.5`.

P26-2 now has rail-targeting headless coverage: `Slabbed2612RestingDyTest.loweredRailLowerBodyHasVisibleOwnerRescue` was RED because the lowered rail lower body was directly hittable but vanilla world clip resolved `MISS`. The fix routes lowered `BaseRailBlock` states through the existing visible-owner rescue family. Green proof: `tmp/26-2-proof-fails/runGameTest-rail-target-green.log` passed 117/117. The minecart render/ride fit part remains `N/A -- eye`, so keep VIS open for the cart-on-rail view.

P26-6/P26-7 now have red/green render-path dy guards: `Slabbed2612LoweringContractTest.chainColumnUnderTopSlabKeepsDescendantsGridHeight` and `Slabbed2612LoweringContractTest.hangingLanternUnderCeilingBridgedChainStaysGridHeight`. The old "raises together" guard was a false green for the video symptom: the direct top chain is already emitted with the extended ceiling-bridge model, so descendant chains and lanterns must stay grid-height to avoid overlap/merge. Current-jar VIS proof is in `tmp/26-2-proof-fails/live-scaffolding-chain/05-chain-wide-continuity.png` and `06-chain-close-continuity.png`; the 26.2 command fixture uses `minecraft:iron_chain`, not `minecraft:chain`.

P26-6 targeting follow-up now has headless coverage for Julia's screenshot symptom: `Slabbed2612LoweringContractTest.ceilingBridgedChainSelectionExtendsToVisibleBridge` was RED because the direct top `minecraft:iron_chain` model was visible at local `y=0.25` while the shifted outline/raycast proxy missed it. The fix extends only direct ceiling-bridged vertical-chain outline/interaction shapes to the same 1.5-block vertical span as `chain_ceiling_support`. Green proof: `tmp/26-2-proof-fails/runGameTest-chain-selector-final.log` passed 120/120. The selector-fix jar was rebuilt and staged into Modrinth profile `SLABBED-MC 26.2` with SHA-256 `32a6c13c1e305050f96957d5d5d3b9712d218e58f74b41b8bb465e04f1d974a5`; Julia later live-confirmed the in-game target behavior is fixed, so the old exact-window screenshot blocker is no longer a release gate.

P26-8 now has headless coverage for the player-placement path: `Slabbed2612UseOnPlacementTest.useOnFenceClickingLoweredFenceOverAirFollowsToMinusHalf` was RED at `dy=0.0` and GREEN at `dy=-0.5` while preserving the stepped-connection policy checks. Julia later live-confirmed the rendered same-height fence behavior is working too.

Agent live pre-screen screenshots for the full P26 queue are in `tmp/26-2-live-proof-20260620-181328/`: `p26-1-scaffolding-live.png`, `p26-3-glass-panes-live.png`, `p26-4-5-dripstone-sulfur-live.png`, `p26-6-7-chain-lantern-live.png`, and `p26-2-8-minecart-fence-live.png`. The tighter current-jar proof for the video follow-up is in `tmp/26-2-proof-fails/live-scaffolding-chain/`, with numeric scaffolding movement screenshots and chain/lantern continuity screenshots. Julia's later manual play pass is the final live closeout for this queue on the current branch state.

Current queue status after the 2026-06-20 proof pass plus Julia's live confirmation:

| # | Status | Evidence | Remaining gate |
|---|---|---|---|
| P26-1 | Closed: scaffolding solid-collision/climbability red->green and Julia live-confirmed traversal/feel | `runGameTest-scaffolding-red.log`, `runGameTest-scaffolding-chain-final.log`, `01-scaffolding-before-space.png`, `02-scaffolding-after-space.png`, `03-scaffolding-after-shift.png`, Julia live pass | none |
| P26-2 | Closed on current branch state: rail targeting red->green, minecart fit manually confirmed | `runGameTest-rail-target-red.log`, `runGameTest-rail-target-green.log`, `p26-2-8-minecart-fence-live.png`, Julia live pass | none |
| P26-3 | Closed: glass-pane lowered visual-family red->green and visually confirmed | `runGameTest-glass-pane-red.log`, `runGameTest-glass-pane-green.log`, `p26-3-glass-panes-live.png`, Julia live pass | none |
| P26-4 | Closed: pointed dripstone and sulfur spike targeting red->green and visually confirmed | `runGameTest-dripstone-visible-owner-red.log`, `runGameTest-dripstone-visible-owner-green.log`, `runGameTest-sulfur-spike-red.log`, `runGameTest-sulfur-spike-green.log`, `p26-4-5-dripstone-sulfur-live.png`, Julia live pass | none |
| P26-5 | Headless-fixed after Julia found chained speleothem false-greens; staged for live retest | `runGameTest-sulfur-spike-green.log`, `p26-4-5-dripstone-sulfur-live.png`, Julia live pass, `tmp/runGameTest-speleothem-ceiling-bridged-chain-red.log`, `tmp/runGameTest-speleothem-ceiling-bridged-chain-green.log`, `tmp/runGameTest-speleothem-direct-top-red.log`, `tmp/runGameTest-speleothem-direct-top-green.log` | restart `SLABBED-MC 26.2` and live-retest pointed dripstone + sulfur spike chained directly under top slabs |
| P26-6 | Closed: chain continuity and selector targeting red->green, then live-confirmed | `runGameTest-chain-render-red-jdk25.log`, `runGameTest-scaffolding-chain-final.log`, `runGameTest-chain-selection-lower-red.log`, `runGameTest-chain-selector-final.log`, `stage-chain-selector-profile-jar.txt`, `05-chain-wide-continuity.png`, `06-chain-close-continuity.png`, Julia live pass | none |
| P26-7 | Closed: lantern-under-chain no-smoosh path red->green and visually confirmed | `runGameTest-chain-render-red-jdk25.log`, `runGameTest-scaffolding-chain-final.log`, `05-chain-wide-continuity.png`, `06-chain-close-continuity.png`, Julia live pass | none |
| P26-8 | Closed: fence WYSIWYG placement red->green with policy preserved, then live-confirmed | `runGameTest-fence-red.log`, `runGameTest-fence-green.log`, `p26-2-8-minecart-fence-live.png`, Julia live pass | none |

Latest combined proof before the manual closeout: `tmp/26-2-proof-fails/runGameTest-scaffolding-chain-final.log` passed 119/119 required tests and `tmp/26-2-proof-fails/compileClientJava-scaffolding-chain-final.log` passed. Latest source/profile proof after the selector follow-up: `tmp/26-2-proof-fails/runGameTest-chain-selector-final.log` passed 120/120 required tests, `tmp/26-2-proof-fails/build-chain-selector-final-jar.log` built a new jar, and `tmp/26-2-proof-fails/stage-chain-selector-profile-jar.txt` staged SHA-256 `32a6c13c1e305050f96957d5d5d3b9712d218e58f74b41b8bb465e04f1d974a5` into the profile. Fresh branch proof after Julia's live closeout: `tmp/26-2-proof-fails/runGameTest-26-2-post-live-confirm.log` passed compile gates plus `runGameTest`; all 120 required tests passed.

---

## 3. Version comparison — the dy-fingerprint baseline (THE answer to "old vs current")

`SlabSupport.getYOffset(world, pos, state)` is the single dy authority and is **deterministic** for a fixed world + marker state. So a fixed set of synthetic fixtures produces a stable **fingerprint**: `fixture-name → dy → src`. Commit it; diff it; any silent value change shows up as a git diff and a red test.

> **Critical:** `getYOffset` is NOT a pure function of geometry. Anchor / freeze / compound markers (`SlabAnchorAttachment`) are first-class inputs written by the *placement* path (`setPlacedBy`), not by terrain `setBlock`. Every fixture must pin **both** the geometry **and** the marker state, and each baseline line must record the `src` classification (`geometric` / `ANCHORED` / `FROZEN-FLAT` / `compound-side`). A fingerprint over dy alone would miss a freeze/anchor regression that lands on the same number for a different reason.

### Design (status: ✅ IMPLEMENTED — `Slabbed2612DyFingerprintTest`, 19 fixtures, green on `port/mc-26.1.2`)

- **Tier 1 — fingerprint gametest (the gate).** `src/gametest/java/com/slabbed/test/Slabbed2612DyFingerprintTest.java`. One `@GameTest` per fixture (= one isolated structure region each, so no cross-fixture anchor contamination). Each fixture (a) **asserts** the pinned `dy` in-code — a same-version regression goes RED on `runGameTest` — and (b) **logs** a canonical line `SLABBED-FP | <name> | dy=<v> | src=<class>`. Grep a run log for `SLABBED-FP` to get the whole fingerprint as a flat, diffable artifact. Registered in the `include(...)` allowlist in `build.gradle` and the `fabric-gametest` array in `src/gametest/resources/fabric.mod.json`. The committed reference capture is `src/gametest/resources/dy-baseline.txt`.
- Door/trapdoor fixtures additionally assert the robust server-hit **predicates** (`isBeta35LoweredBottomTrapdoorServerHitTarget`, `isBeta35LoweredRegularDoorServerHitTarget`) rather than a raw dy, since their lowering is applied via the server-validation path, not `getYOffset`.
- **Tier 2 — client dump — ✅ BUILT (`DyFingerprintDump`, dev-only, excluded from jar).** Catches the one class the server fingerprint is blind to: **client-vs-server `getYOffset` divergence** (server anchors a value the client reads geometrically → "snaps after a delay"). In a dev client, press **P** (unbound in vanilla; plain letter, no numpad/Fn needed; ignored while a screen is open): it scans a box around the player and logs every lowered/raised block as `SLABBED-FP-CLIENT | pos | block | dy | src` (same `src=` classification as the HUD). Diff a built fixture's client capture against the committed server baseline. (GLFW key-poll, not a Fabric keybind — this Loom setup doesn't expose `fabric-key-binding-api-v1` to the client set.) **Still cannot see render-mesh desync** (dy right, mesh stale) — that stays a human visual check (Lane 3). ✅ Confirmed firing live (2026-06-19: `SLABBED-FP-CLIENT` lines emitted during a runClient session).

To use Tier 2: build the smoke fixtures in a dev world, stand among them, press **P**, then
`grep SLABBED-FP-CLIENT` in `run/logs/latest.log`. Compare those lines to `dy-baseline.txt` (server)
— any block whose client dy differs from the server fixture value is a sync/divergence bug.

### 3.1 To run / compare across versions

```
# Java 25
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home

# Current build vs committed assertions (RED on any drift):
./gradlew runGameTest

# Capture this version's fingerprint as a flat artifact:
./gradlew runGameTest --console plain 2>&1 | grep -o 'SLABBED-FP.*' | sort -u > /tmp/fp-current.txt
```

To compare an **old jar** against current: check out the old tag, capture its fingerprint with the grep above into `/tmp/fp-old.txt`, check out current, capture into `/tmp/fp-current.txt`, then `diff /tmp/fp-old.txt /tmp/fp-current.txt`. Same fixtures on both versions → any line that differs is a behavior change between versions.

**On an INTENTIONAL behavior change:** update the in-code `fingerprint(..., expected)` assertion AND the matching line in `dy-baseline.txt`, in one reviewed commit, so the diff is visible in review. A baseline edit with no corresponding code change (or vice-versa) is a smell.

### 3.2 The fingerprint fixtures (one per historically-regressing family)

Every row maps to a real fix commit — if it silently re-breaks, the golden file shows a one-line diff.

| Fixture | Setup | Expected dy | src | Guards commit |
|---|---|---|---|---|
| `plain_lower` | stone on bottom slab (setBlock) | -0.5 | geometric | core |
| `top_slab_flush` | stone on top slab | 0.0 | - | core |
| `double_slab_flush` | stone on double slab | 0.0 | - | core |
| `compound_vertical` | slab/stone/slab/stone (L3) | -1.0 | geometric | `21af4243` |
| `compound_anchor` | authored log + compound anchor on lowered slab | -1.0 | ANCHORED | beta4 |
| `frozen_flat` | author flat stone, slab added below | 0.0 | FROZEN-FLAT | `8aafd1ff` |
| `freeze_control` | terrain stone, slab below (not authored) | -0.5 | geometric | (freeze is gated) |
| `side_no_contagion` | slab authored on flush ground beside lowered | 0.0 | - | `83afed84` |
| `cantilever_merge` | stone over air beside lowered tower | -0.5 | geometric | `9a24670c` |
| `compound_side_upper` | slab aimed at UPPER half of -0.5 block | -0.5 | compound-side | BUG B (`3ef254e2`) |
| `ceiling_flush` | hanging roots under flush block above | 0.0 | - | `2a50335e` |
| `ceiling_follow` | hanging roots under lowered support above | -0.5 | - | hanger-follow |
| `lantern_smoosh` | hanging lantern under lowered support | -0.5 | - | `bbe3deb9` |
| `chain_raise` | Y-chain under top slab | +0.5 | geometric | ceiling +0.5 |
| `thin_layer` | carpet / snow on bottom slab | 0.0 | - | `isThinTopLayer` |
| `powder_snow` | powder snow on bottom slab | 0.0 | - | `95c0ab12` |
| `bed_either_half` | bed, one half on a slab | both halves -0.5 | - | bed coordination |
| `ts_compat_flush` | stone on Terrain Slabs slab *(TS profile only)* | 0.0 | - | `0bd265dc` |
| `connector_break` | fence A(-0.5) beside B(0.0), `updateShape` | EAST=NONE | n/a | P1 `409bf519` |

> TS rows only fire when Terrain Slabs is on the classpath — gate them and emit `n/a` (or keep a second `dy-baseline.ts.txt`) so the baseline stays stable across both profiles.

### 3.3 What the fingerprint canNOT catch (keep these manual)

- **Render-only desync** — `getYOffset` correct, mesh stale (the "middle pops up" class). Tier-2 client dump + `SlabbedLabRenderRegionLoweredCarrierBridge*` client tests.
- **Client-vs-server divergence** — golden runs server-side; a sync bug (server anchors, client reads geometric → "snaps after a delay") passes a server-only golden. Tier-2 covers this.
- **Collision-follow** — separate output from dy; keep `GhostLoweredCollisionProofTest` + `loweredSlabIsSolidAtVisualLowerHalf`.
- **Entity render mixins** (minecart/item-frame) — no headless render state. Lane 3 only.
- **The placement raycast target** — which cell/half the ray picks is camera-dependent; not a `getYOffset` output.

---

## 4. Full matrix

Run a family's full block list when that family's code changed since last release; otherwise the smoke set + fingerprint covers it.

### A. Core lowering — full blocks on bottom slab

| # | Block | Setup | Expected | Proof |
|---|---|---|---|---|
| A1 | `stone` | on `stone_slab[bottom]` | `dy=-0.500` | GT + DY |
| A2 | `stone` | on `oak_slab[bottom]` | `dy=-0.500` | DY |
| A3 | `stone` | on `oak_slab[top]` | `dy=0.000` (top slab does not lower) | GT + DY |
| A4 | `stone` | on `oak_slab[double]` | `dy=0.000` | GT + DY |
| A5 | `crafting_table` | on bottom slab | `dy=-0.500` | DY |
| A6 | `chest` | on bottom slab | `dy=-0.500` + flush | DY + VIS |
| A7 | `barrel` | on bottom slab | `dy=-0.500` + flush | DY + VIS |
| A8 | `furnace` | on bottom slab | `dy=-0.500` | DY |
| A9 | `bookshelf` | on bottom slab | `dy=-0.500` | DY |
| A10 | `enchanting_table` | on bottom slab | `dy=-0.500` | DY |
| A11 | `stonecutter` | on bottom slab | `dy=-0.500` | DY |
| A12 | `anvil` | on bottom slab | `dy=-0.500` + flush | DY + VIS |
| A13 | `grindstone` | on bottom slab | `dy=-0.500` | DY |
| A14 | `lectern` | on bottom slab | `dy=-0.500` | DY |

### B. Compound stacking

| # | Setup | Expected | Proof |
|---|---|---|---|
| B1 | `slab → stone → slab` (slab on top face of lowered stone) | top slab `dy=-1.000` | GT + DY |
| B2 | two-deep stack `slab/stone/slab/stone` | upper stone `dy=-1.000` | GT + DY |
| B3 | `slab → stone`, place slab to the SIDE (cantilever) | cantilever slab `dy=-0.500` | GT + DY |
| B4 | compound side at deeper depth | follows compound depth | DY |
| B5 | break bottom slab under a compound stack | pops/freezes per NEVER-POP (no spontaneous snap) | VIS |

### C. NEVER-POP / freeze law

| # | Setup | Action | Expected | Proof |
|---|---|---|---|---|
| C1 | player-place `stone` on terrain bottom slab | `/slabdy` | `dy=0.000 FROZEN-FLAT` | GT + DY |
| C2 | player-place `crafting_table` on terrain slab | `/slabdy` | `dy=0.000 FROZEN-FLAT` | DY |
| C3 | A/B: `/setblock` same cell vs placed | compare | setblock `-0.500`, placed `0.000` | DY |
| C4 | break slab under FROZEN-FLAT block | — | block pops | VIS |
| C5 | relog with FROZEN-FLAT block | — | dy stays `0.000` | DY |
| C6 | relog with ANCHORED `-0.5` block | — | dy stays `-0.500` | GT + DY |

### D. Ceiling hanging — pull dy from support ABOVE

| # | Block | Setup | Expected | Proof |
|---|---|---|---|---|
| D1 | `lantern[hanging]` | under `oak_slab[top]` | `dy=+0.500`, flush | DY + VIS |
| D2 | `lantern[hanging]` | under lowered top slab | follows support, no gap | DY + VIS |
| D3 | `lantern[hanging=false]` | on bottom slab | `dy=-0.500` (floor lantern) | DY |
| D4 | `soul_lantern[hanging]` | under top slab | `dy=+0.500`, flush | DY + VIS |
| D5 | `iron_chain` (Y) | under top slab | hangs flush, no gap | VIS |
| D6 | `iron_chain` (Y) | break support (remove slab) | chain REMAINS — vanilla floating, no survival rule (intentional, `df3a0dd4`); pop mixin deleted, pinned by `chainDoesNotPopWhenSupportRemoved` | GT |
| D7 | `hanging_roots` | under flush block above | `dy=0.000` (follows above) | GT |
| D8 | `hanging_roots` | under lowered support | follows to `-0.500` | GT + VIS |
| D9 | `spore_blossom` | under block on slab | flush against block above | VIS |
| D10 | `oak_hanging_sign` | under `oak_slab[top]` | hangs flush; **double-chain (not loop) model** | VIS |
| D11 | `pointed_dripstone` (down) | under ceiling block on slab | follows ceiling | VIS |
| D12 | `cave_vines` | from ceiling block on slab | base flush against block above | VIS |

### E. Floor-standing objects — must CONTACT support (no float gap)

| # | Block | Setup | Expected | Proof |
|---|---|---|---|---|
| E1 | `torch` | on bottom slab | flush, `contactGap=0` | GT + VIS |
| E2 | `torch` | on full block on bottom slab (compound) | flush on lowered block | VIS |
| E3 | `soul_torch` | on bottom slab | flush | VIS |
| E4 | `candle` | on bottom slab | `dy=-1.000` (support−0.5), no gap | GT + DY |
| E5 | `candle` | on full block on slab | follows compound support | DY |
| E6 | `flower_pot` | on bottom slab | placed, survives, flush | GT + VIS |
| E7 | `flower_pot` | break support | ⚠ LIVE-ONLY: flower_pot reports canSurvive even over air (no support rule) — headless can't prove the pop | VIS |
| E8 | `oak_button[FLOOR]` | on bottom slab | flush at slab-top | GT + VIS |
| E9 | `stone_button[FLOOR]` | on bottom slab | flush at slab-top | VIS |
| E10 | `stone_pressure_plate` | on bottom slab | flush, no gap | VIS |
| E11 | `oak_pressure_plate` | on bottom slab | flush, no gap | VIS |
| E12 | `oak_sign` (standing) | on bottom slab | base at slab-top, no gap | GT + VIS |
| E13 | `oak_sign` (standing) | neighbor update | does not pop | GT |
| E14 | `wall_torch` | on wall of lowered block | offsets with block, no detach | VIS |
| E15 | `wall_sign` | on wall of lowered block | correct position | VIS |
| E16 | `wall_banner` | on wall of lowered block | correct position | VIS |
| E17 | `wall_hanging_sign` | on wall of lowered block | correct position | VIS |

> Note: standing-sign contact is keyed on `OAK_SIGN` only (`isBeta35StandingOakSignContactObject`) — other wood signs fall through. Latent, not a blocker; flag if a non-oak standing sign floats.

### F. Doors and trapdoors  *(currently ZERO gametest coverage — highest automation gap)*

| # | Block | Setup | Expected | Proof |
|---|---|---|---|---|
| F1 | `oak_trapdoor[BOTTOM]` | on top face of bottom slab | flush at slab-top | DY + VIS |
| F2 | `oak_trapdoor[TOP]` | under `oak_slab[top]` (ceiling) | flush against underside | DY + VIS |
| F3 | `birch_trapdoor[TOP]` | under lowered top slab | flush against lowered underside | DY + VIS |
| F4 | `mangrove_trapdoor` | as F1 | flush | VIS |
| F5 | `oak_trapdoor` | open→close→open under top slab | stays positioned in all states | VIS |
| F6 | `oak_door` | on bottom slab top face | both halves offset, base at slab-top | DY + VIS |
| F7 | `oak_door` | break slab under door | door pops | GT |
| F8 | `spruce_door` | on bottom slab | both halves offset | VIS |
| F9 | `acacia_door` | on bottom slab | both halves offset | VIS |
| F10 | `oak_door` | open/close on slab | correct height in both states | VIS |

### G. Connectors — fence/wall/pane/bars

**Rule:** must NOT connect across a slab-height step; MUST connect at same level; break a connection without popping.

#### G1 — Fences DO NOT connect across step (one per material)
`oak`, `spruce`, `birch`, `jungle`, `acacia`, `dark_oak`, `bamboo`, `mangrove`, `cherry`, `crimson`, `warped`, `nether_brick` — slab-height fence beside ground fence → **no connection**. Proof: **GT** (logic) + **VIS** (one material live).

#### G2 — Fences DO connect at same level
| G2a | two `oak_fence` on same bottom slab | connect | GT + VIS |
| G2b | two `oak_fence` at ground level | connect (vanilla) | GT |

#### G3 — Break without pop
| G3a | break fence adjacent to slab-height fence | slab fence does NOT pop | VIS |
| G3b | break slab under fence | fence pops (unsupported) | VIS |

#### G4 — Walls DO NOT connect across step (one per material)
`cobblestone`, `stone_brick`, `brick`, `mossy_cobblestone`, `andesite`, `granite`, `diorite`, `cobbled_deepslate`, `polished_blackstone_brick`, `nether_brick` — slab-height wall beside ground wall → **no connection**. Proof: **GT** + **VIS** (one material).

#### G5 — Panes/bars
| G5a | `glass_pane` slab-height beside ground pane | no connection | GT + VIS |
| G5b | `iron_bars` slab-height beside ground bars | no connection | GT |
| G5c | `glass_pane` beside same-height pane | DOES connect | GT |

#### G6 — Fence gates
| G6a | `oak_fence_gate` on bottom slab | `dy=-0.500`, opens/closes at slab height | DY + VIS |
| G6b | `bamboo_fence_gate` on bottom slab | places correctly | VIS |

### H. Stairs (known possible visual quirks)
| H1 | `stone_stairs` on bottom slab | lowers, visual acceptable | DY + VIS |
| H2 | `oak_stairs` on bottom slab | lowers | DY |
| H3 | `birch_stairs` on bottom slab | lowers | DY |

### I. Thin top layers — must NOT lower
| I1 | `snow` layer on bottom slab | `dy=0.000` | GT + DY |
| I2 | `white_carpet` on bottom slab | `dy=0.000` | GT + DY |
| I3 | `moss_carpet` on bottom slab | `dy=0.000` | DY |
| I4 | `white_carpet` — survives on slab top, no pop on neighbor update | survival | GT + VIS |
| I5 | `powder_snow` beside slab | `dy=0.000` (explicit exclusion) | GT + DY |

### J. Terrain Slabs compat — `TEST_ SLABBED 26.1.2` profile only
| J1 | `stone` on any TS `*_slab` | `dy=0.000` (world-hole guard) | DY + VIS |
| J2 | `cobblestone` on TS slab | `dy=0.000` | DY |
| J3 | `crafting_table` on TS bottom-like slab | `dy=-0.500` (curated, P0.4) | DY |
| J4 | `crafting_table` on TS top slab | flush | DY |
| J5 | short grass / vegetation on TS slab | flush, no double-offset, not invisible | VIS |
| J6 | natural TS terrain (sand/dirt/grass terraces), fresh world | no holes, terraces solid | VIS |
| J7 | walk through a TS terrace | no render-region crash (AIOOBE) | FEEL |

### K. Visual triad — model / outline / raycast agree
| K1 | `stone` on bottom slab — look at it | outline AT block, not above | VIS |
| K2 | `stone` on bottom slab — break it | breaks at lowered position | VIS |
| K3 | `crafting_table` on slab — right-click | UI opens (hit at lowered pos) | VIS |
| K4 | `chest` on slab — interact | chest opens | VIS |
| K5 | `oak_fence` on slab — look at it | outline at lowered post height | VIS |
| K6 | lowered block from all angles | renders solid, no ghost window / DODO | VIS |
| K7 | walk away and back | step-face cull stays solid, no flicker | VIS |

### L. WYSIWYG placement — lands where aimed
| L1 | aim UPPER half of `-0.5` block, place slab on side | lands `-0.500` (not `-1.0`) | DY |
| L2 | aim LOWER half of `-0.5` block, place slab on side | correct lower position | DY |
| L3 | aim top of bottom slab, place stone | terrain height (FROZEN-FLAT if placed) | DY |
| L4 | aim side of TS slab, place slab beside | flush at TS height | DY |
| L5 | aim compound `-1.0` block, place slab on top | follows compound | DY |

### M. Collision / ghost
| M1 | walk INTO a lowered slab from the side | stops at visual face, no clip | FEEL |
| M2 | walk ONTO a lowered slab from below | no pass-through | FEEL |
| M3 | jump on a lowered slab | lands, no phase-through | FEEL |
| M4 | stand in the cell BELOW a lowered block | its hanging collision is solid | FEEL |
| M5 | outline box on lowered block | matches visual model height | VIS |
| M6 | `-Dslabbed.collisionFollow=false` kill switch | collision reverts to vanilla (A/B) | GT |

### N. Survival — neighbor update + reload
| N1 | break slab under `-0.5` ANCHORED block | pops | VIS |
| N2 | place block next to ANCHORED lowered block | no snap / dy change | DY |
| N3 | relog with ANCHORED lowered block | dy unchanged | DY |
| N4 | break+replace slab under ANCHORED block | dy updates to new geometry | DY |
| N5 | chunk reload (go far + back) | lowered blocks still correct | DY |

### O. Misc ceiling/wall-attached
| O1 | `bell` (ceiling) under top slab | flush, no gap | VIS |
| O2 | `lever[CEILING]` under top slab | flush | VIS |
| O3 | `oak_button[CEILING]` under top slab | flush | VIS |
| O4 | `lever[WALL]` on wall of lowered block | follows block | VIS |

### P. Slab material sweep — confirm material isn't a regression axis
Behavior keys on slab TYPE, not material — so this is a thin sweep. Best as a parametrized **GT**; spot-check 2–3 live.
`oak`, `stone`, `cobblestone`, `sandstone`, `brick`, `nether_brick`, `quartz`, `prismarine`, `deepslate_tile`, `cut_copper` slab[bottom] under `stone` → each `dy=-0.500`. Proof: **GT** (all) + **DY** (2–3).

### R. Entity render offsets — LIVE-ONLY (no gametest can see these; checklist is the sole safety net)
| R1 | minecart on rail on bottom slab | rides flush, not floating +0.5 | **N/A — eye** |
| R2 | minecart transitions rail-on-slab ↔ rail-on-ground | no jarring snap (known awkward) | **N/A — eye** |
| R3 | item frame on a lowered block | frame tracks the lowered block | **N/A — eye** |
| R4 | item frame on a flush block | unchanged (no spurious offset) | **N/A — eye** |
| R5 | rail itself on a bottom slab | lowers with the slab, no float | **N/A — eye** |

### T. Redstone + double-tall plants
| T1 | `redstone_wire` placed on bottom slab top | places + survives | VIS |
| T2 | `redstone_wire` on top slab top | places + survives (`isRedstoneSupportTopSurface`) | VIS |
| T3 | redstone signal across a slab step | propagates (step-up/down) | VIS |
| T4 | `sunflower` (double-tall) on bottom slab | both halves lower -0.5 on a VANILLA slab (UPPER half TS-gated → 0.0 on a Terrain Slabs surface) | DY + VIS |
| T5 | `large_fern` on bottom slab | both halves lower | VIS |
| T6 | `tall_grass` on bottom slab | both halves lower | VIS |

---

## 9. Pre-release hygiene gate (run in addition, not instead)

| # | Check | Command | Expected |
|---|---|---|---|
| Q1 | No always-on LOGGER.info/warn | `grep -rn "LOGGER\.\(info\|warn\)" src/main src/client \| grep -v "TRACE\|getBoolean\|isEnabled\|isDevelopmentEnvironment"` | 0 (or all gated) |
| Q2 | No System.out | `grep -rn "System\.out" src/main src/client` | 0 |
| Q3 | No debug/dev classes in jar | `unzip -l build/libs/*.jar \| grep -E "debug\|dev"` | 0 |
| Q4 | Gametests pass | `./gradlew runGameTest` (Java 25) | BUILD SUCCESSFUL |
| Q5 | dy-fingerprint matches baseline | `./gradlew runGameTest` (no regen flag) | no baseline diff |
| Q6 | Clean build | `./gradlew clean build` (Java 25) | BUILD SUCCESSFUL |
| Q7 | Jar byte-size | `ls -l build/libs/*.jar` | record + compare to prior release |

---

## Sign-off template

```
Release candidate: <version>+<mc-version>
Jar: <filename> (<bytes>)
Profile(s) tested: <Fabric 26.1.2 | TEST_ SLABBED 26.1.2>
Date / tester:

Lane 1 (GT): runGameTest <pass/fail>, fingerprint diff <clean/changed>
Lane 2 (DY cruise): smoke set S1–S15 <results>
Lane 3 (human VIS/FEEL/N/A): <Julia's results — esp. R1–R5 entity render>

Full-matrix families run (changed since last release): <list>
Failed items (with coords + symptom):
Skipped (with reason):

Verdict: GO / NO-GO   Blocker(s):
```
