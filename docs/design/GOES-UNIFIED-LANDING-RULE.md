All verification complete. Every load-bearing anchor has been re-read in this change. Producing the design document as my final output.

---

# UNIFIED LANDING RULE — Design Document (Slabbed 26.2)

**Repo:** `<home>/CascadeProjects/Slabbed-port-26.2`, branch `port/mc-26.2-0.4.1-beta.1`
**Status:** Design only — no code changes. All file:line anchors below re-verified in this change against working-tree HEAD (`3c40cf32`). Governing law: `LAW.md:11-33` (height computed ONCE at placement from the aim, then frozen; the aim interpretation is the only legitimate consultation of surrounding geometry). Live evidence base: `docs/process/LIVE_LEDGER.md` TEST 13–17.

---

## 0. The diagnosis in one paragraph

STAYS is solved by the frozen store (`PLACEMENT_DY_TYPE`, `SlabAnchorAttachment.java:254-258`; read short-circuit `SlabSupport.java:1030-1035`). GOES is broken because **there is no landing computation** — the number that gets frozen is whatever the live read-lane patchwork happens to say at `setPlacedBy` time, and that patchwork (a) never sees the aim (the hit vec is discarded inside the `@ModifyArg` remap, `BlockItemPlacementIntentMixin.java:1470-1497`, before any height decision), (b) is pinned to exact magnitudes (−0.5, −1.0) and exact families (stone slab, 10 whitelisted object lanes), and (c) runs **before** the markers that would have corrected it are authored at place-RETURN (`BlockOnPlacedAnchorMixin.java:37` vs `BlockItemPlacementIntentMixin.java:1546-1624` — the capture javadoc at `SlabAnchorAttachment.java:263-266` claims the opposite ordering and is wrong). Every TEST-17 failure family is one of those three causes. The fix is one new component: a **landing resolver** that computes the stored dy from the aim, at placement, for every family — and a capture point that writes the resolver's answer instead of re-reading the lanes.

---

## 1. The One Rule and the Landing Resolver

### 1.1 The rule (normative text)

> **A placement lands on the clicked visible surface.** The placed block's stored dy is the offset that seats the placed block's family-specific attachment plane exactly on the visible plane of the clicked owner that the aim identified — for every item family, every owner shape, every depth, on all six faces. Computed once, written to the frozen store, never recomputed.

### 1.2 The resolver — signature

One server-side (and client-mirrored, §1.6) pure function, invoked once per placement inside `BlockItem.useOn`/`place`:

```
resolve(aim, world, heldFamily) → PlacementResolution { targetCell, landingDy, expectedSlabType?, pairCells[], refusal? }
```

**Inputs (exact):**

| Input | Source | Notes |
|---|---|---|
| `clickedPos`, `clickedFace`, `hitVec` | the `UseOnContext`/`BlockPlaceContext` the server received | Under the honest offset raycast (`SlabbedOffsetRaycast.ENABLED`, default ON, `SlabbedOffsetRaycast.java:61-62`) the client already attributes the hit to the **visible owner** — the aim arrives server-side in the use packet; no client→server side channel is needed. |
| `ownerState` | `world.getBlockState(clickedPos)` | |
| `ownerVisibleDy` | **stored-first**: `SlabAnchorAttachment.storedPlacementDy(world, clickedPos)` (`SlabAnchorAttachment.java:286-300`); if NaN, fall back to the public `SlabSupport.getYOffset` (`SlabSupport.java:1014`) | This is the single "how deep is the surface I clicked" authority. Reading the store first is what generalizes to any depth in one move and is recursion-free (§7 R3). TS gate: if `CompatHooks.shouldSkipOffset(ownerState)` the owner is a TS-owned surface → ownerVisibleDy := 0.0 and the owner is never a lowered support (preserves the ordering at `SlabSupport.java:1021-1023`). |
| `ownerShapePlanes` | from `ownerState`: top-plane offset (0.5 bottom slab; 1.0 full/TOP/DOUBLE — `getSupportYOffset`, `SlabSupport.java:168-177`), bottom-plane offset (0.0 full/bottom; 0.5 TOP slab) | |
| `heldFamily` | the held item's family row from the table in §2 | |
| `route` | AIMED (real player use, hit present) vs AIMLESS (`DirectionalPlaceContext`/null player — dispenser shulker route) | Resolver must tolerate null player (§5, risk R10). |

**Output:** the cell vanilla will fill (reusing the existing remap machinery to steer vanilla there), the dy to store for that cell **and each pair cell** (door UPPER, bed HEAD), the slab TOP/BOTTOM/DOUBLE intent where applicable, or a **refusal** (placement cancelled with PASS, the existing pattern at `BlockItemPlacementIntentMixin.java:910/:923`).

### 1.3 The formula (all six faces, one arithmetic)

Define the **aimed plane** from owner data — never from `hitVec.y`:

- `visibleTop(owner) = ownerY + ownerVisibleDy + topPlaneOffset(ownerState)`
- `visibleBottom(owner) = ownerY + ownerVisibleDy + bottomPlaneOffset(ownerState)`
- `visibleBodyFrame(owner) = ownerVisibleDy` (for side attachment)

Then:

1. **UP face (floor-seated families):** `landingDy = visibleTop(owner) − targetCell.getY()` … minus 0 because the placed block's natural bottom is at its cell floor. For `targetCell = owner.above()` over a full-block owner this is exactly `ownerVisibleDy`; over a bottom-slab owner it is `ownerVisibleDy − 0.5`. This is the generalization of the one already-correct lane in the codebase — the fence formula `supportDy + supportTopOffset − 1.0` at `SlabSupport.java:501-503`. **Caveat verified this change (critic gap #6): that exemplar's `supportDy` input is itself depth-capped** — `beta35FenceWallVisibleSupportDy` (`SlabSupport.java:508-568`) and `floorTorchBottomSlabSupportDy` (`SlabSupport.java:2952-3011`) have the closed codomain {NaN, −1.0, −0.5, 0.0}. The formula shape is right; the input must become the stored/live owner dy.
2. **DOWN face (any family) and hanging families regardless of face:** the attachment plane is `visibleBottom(support above)`; `landingDy = visibleBottom(support) − (targetCell.getY() + 1)`. For a full-block support this is `supportDy`; for a lowered TOP slab it is `supportDy + 0.5` — exactly the compensation `ceilingHungDecorationDy` already computes (`SlabSupport.java:2431-2433, :2456`). Attachment direction is thereby first-class: the same arithmetic, applied to the underside plane.
3. **Horizontal faces (side attachment):** `landingDy = ownerVisibleDy` — the placed block adopts the owner's frame (`dy(placed) = dy(clicked owner)`, the invariant the −1.0 lane proves). TOP/BOTTOM/DOUBLE is classified against the **visible** midline — the math already exists and is depth-correct at `BlockItemPlacementIntentMixin.java:1458-1465` (`loweredVisualMidline = targetY + yOffset + 0.5`); today only the TYPE survives and the depth is dropped. The resolver keeps both.
4. **Same-cell merges (BOTTOM owner + UP click → DOUBLE):** no new cell; `landingDy = owner's stored dy`, explicitly **re-stored** for the merged state (resolves critic A3 — one rule row instead of "right by accident"). Covers both the vanilla merge and the server-direct merge finalization in `ServerInteractBlockHitToleranceMixin.java:45-102`, which calls `setBlock` directly and today never re-captures.
5. **Cell choice & occlusion refusal:** the target cell is the cell whose family-natural span, shifted by `landingDy`, does not intersect any existing **visible** body. Generalizes the not-air refusal at `SlabSupport.java:1216-1223` to visible-body occlusion using the existing classifier (`SlabEnsembleCoherence.isOccludedOccupancy`, consumed at `BlockItemPlacementIntentMixin.java:874`). On refusal: cancel with PASS — never fall through to a flat vanilla placement. This is what closes critic B1's down-face/into-body hole: clicking the visible underside of a −1.0 owner resolves to `owner.below(2)` at dy 0.0 (pure arithmetic: `visibleBottom − (cellY+1) = 0`); clicking into a grid-air cell occupied by a deep owner's protruding body refuses.
6. **Epsilon policy:** `hitVec` is never used to decide **which** surface was clicked — `(clickedPos, clickedFace)` from the honest raycast is authoritative; `hitVec` only classifies position **within** the face (midline halves, §1.3.3). This dissolves the exactly-−1.0 ambiguity (visible top coincides with a grid plane, `hitVec.y` is an exact integer there): face attribution comes from the raycast, not from a Y comparison.
7. **Fluid-blindness:** the resolver ignores fluid state entirely (F5 discipline — waterlogged placements land identically to dry; the store and marker reads are already fluid-blind, e.g. `SlabAnchorAttachment.java:1407-1410`).

### 1.4 What the resolver kills

The resolver replaces **four** independent pin layers that today must all agree (and don't):

| Layer | Sites (all verified) | Pin |
|---|---|---|
| Recognition | `BlockItemPlacementIntentMixin.java:83, :92-93` (`Math.abs(dy+1.0) ≤ ε`); `:949` (`Math.abs(dy+0.5) < ε`); `SlabSupport.java:1207-1213` (`source_not_compound_full_block_dy_-1`) | exact −1.0 / exact −0.5; slab owners excluded at `:78/:88`; EntityBlock owners structurally excluded (can never carry the compound marker, `SlabAnchorAttachment.java:1157-1158`) |
| Authoring | `BlockItemPlacementIntentMixin.java:180, :204, :231` (`is(Blocks.STONE_SLAB)`), `:193-194/:217-218/:245-246` (dy ≈ −1.0 re-checks); `SlabAnchorAttachment.java:1411-1437` (all four marker state predicates stone-slab-only), `:1373-1374` (owner-top source exactly −1.0) | stone slab only; −1.0 only |
| Read-time minting | `SlabSupport.java:2501-2512` (markers → literal −1.0); `:2123-2124` (`loweredFullBlockMagnitude` marker branch → literal −1.0, shadowing its own uncapped branch `:2129-2135`); `:2170-2183` (`loweredSlabMagnitude` → −1.0/−0.5 literals); `:2555`/`:2759` (anchored fallback −0.5); `:3328-3331` (`slabColumnYOffset` presence literals); `:2857-2858` (generic compound −1.0); `:613-614` (torch-only full-block branch, −1.0 literal) | presence → constant, depth-blind |
| **Server hit validation** (new find, this change — no lane covered it) | `ServerInteractBlockHitToleranceMixin.java:33` (`COMPOUND_DY = -1.0`), `:286-289` (accept deep visual hit only when owner dy == exactly −1.0), `:131` (validation center shifted by the −1.0 constant), `:184-189` (per-axis tolerance 1.0000001) | the server's use-packet distance check recognizes deep visible bodies only at exactly −1.0; owners deeper than ~−2.0 approach the per-axis tolerance limit even when the raycast targets them. TEST 17 proves depths to −2.5 passed live, so current geometry fits inside vanilla's budget — but this seam **must** be generalized with the resolver (shift the validation center by the owner's stored/live dy, any depth) or the deepest aims will be silently swallowed server-side. |

---

## 2. Family Table

Seat rule legend — **TOP-SEAT**: formula §1.3.1 (land on visible top). **SIDE**: §1.3.3. **HANG**: §1.3.2 (support above; underside plane). **FLUSH**: always 0.0 by policy. **AIM-KEYED**: TOP-SEAT on aimed placement only; natural/setBlock fill stays flush (live lanes unchanged).

| # | Family (kit members) | Today (verified mechanism) | Resolver rule | Change sites / notes |
|---|---|---|---|---|
| 1 | **Vanilla slabs — UP-click on owner top** (stone/smooth_stone/oak slab) | −1.0 owner → lands 0.0 or −0.5: owner-top marker authored after capture (`BlockItemPlacementIntentMixin.java:1594-1603` vs `BlockOnPlacedAnchorMixin.java:37`); deep-rest excludes exactly −1.0 (`SlabSupport.java:2551`); TOP-slab/hopper owners invisible to `isCompoundTopHit` (`:86-94`); oak refused by stone pins | TOP-SEAT, any owner shape (full block, TOP/DOUBLE/BOTTOM slab, EntityBlock), any depth, any slab family | Kill layer-1/2 pins (§1.4); A7 re-spec (§5) |
| 2 | **Vanilla slabs — side click on lowered owner** | Works only at exactly −1.0 (three-layer coincidence); −1.5 owner → seats −1.0 via `loweredFullBlockMagnitude` clamp (`SlabSupport.java:2123-2124`); chains degrade −1.5→−1.0→−0.5 (`:2178-2183`) | SIDE: `dy(placed)=dy(owner)`, half by visible midline (`BlockItemPlacementIntentMixin.java:1458-1465`); chained side slabs inherit the stored magnitude | Also generalize `isCompoundVisibleSlabLaneOwner` `== −1.0` (`SlabSupport.java:3055`) and the server validation seam (§1.4 row 4) |
| 3 | **Slab merges → DOUBLE** (same cell) | Keeps owner's stored dy by accident; direct server merge (`ServerInteractBlockHitToleranceMixin.java:82`) never re-captures | Merge row: re-store owner's dy for the DOUBLE state at both merge sites | Resolves critic A3; strict-equals follow-marker orphan (`SlabAnchorAttachment.java:391-398`) becomes moot |
| 4 | **Full blocks** (stone, oak_log; craftable full cubes) | Mostly works via anchor+sidecar; sidecar NEVER-POP default −1.0 when below isn't a slab (`SlabSupport.java:2663`), uncapped only through slab-below (`:2670`) | TOP-SEAT / SIDE. Stored value is authority; sidecar/anchor become frozen-OFF fallback | `addTopOfCompoundFullAnchor` (`BlockItemPlacementIntentMixin.java:1628-1641`) kept as compat marker author |
| 5 | **Carpets / snow layers / pale moss carpet** (thin layers) | Never lower, ever: `isThinTopLayer` (`SlabSupport.java:81-86`) vetoes `shouldOffset:891`, flush list `:2876`, anchor candidacy (`SlabAnchorAttachment.java:1151-1155`); carpet render fork lies independently (`ClientDy.java:24-27` bottom-slab-only, owner-dy-blind; client-only shape shift `CarpetDyShapeMixin.java:19-26`) | AIM-KEYED TOP-SEAT. Natural fill keeps flush via untouched live lanes (the snowy-terrain DODO stays protected by ROUTE, not class). Retire the render fork **in lockstep**: `OffsetBlockStateModel.java:183-186` fork, `ClientDy` carpet branch, `CarpetDyShapeMixin` → common source set (dy triad), sentinel policy (`OffsetBlockStateModel.java:146-154`, `SlabModelStaleSentinel.java:102`) collapse to one authority in the same commit | TS gate mandatory: snow layers are in TS's ontop registry by default — seat only when `!CompatHooks.terrainSlabsHandlesObjectOffset(state)` (pattern at `SlabSupport.java:972-975`). Snow-layer `canSurvive` still governs where snow may exist (LAW §4) |
| 6 | **Powder snow (bucket)** | Never lowers: explicit name guards `SlabSupport.java:891, :2875`; fails `isSolidRender` gates (`SlabAnchorAttachment.java:1166`). **Hand path premise corrected:** `SolidBucketItem` extends `BlockItem`, `useOn` is a super call → capture DOES fire and faithfully freezes 0.0 (powder-snow lane bytecode census) | AIM-KEYED TOP-SEAT as a full-cube family, classified by ROLE (full-cube occupancy), never by `isSolidRender` | Pinned tests (`Slabbed2612LoweringContractTest.java:191-200`, `Slabbed2612DyFingerprintTest.java:272-281`) both use `setBlock` → they stay green under AIM-KEYED (they pin the natural-fill route only). Dispenser bucket (`emptyContents` → `setBlock`) stays AIMLESS/flush |
| 7 | **Doors / tall plants / small dripleaf / pitcher crop** | Double hole: `DoorBlock.setPlacedBy` never calls super → no capture (stored NaN; `BlockOnPlacedAnchorMixin.java:24` injects `Block.setPlacedBy` only); AND landing lanes 0.0 on non-slab owners (`floorTorchBottomSlabSupportDy:2955` isBottomSlab gate; `shouldOffset:978` early return). TEST 17: 5× door 0.0-on-−0.5, NaN | TOP-SEAT (LOWER/foot cell), **pair cell stored too**: UPPER := LOWER's dy | Capture at place-RETURN (§4); pair enumeration via `DOUBLE_BLOCK_HALF` |
| 8 | **Beds** | `BedBlock` calls super FIRST, HEAD placed after via `setBlockAndUpdate` → FOOT captured on a half-formed bed, HEAD never captured; landing via depth-blind presence literals (bed −0.5 on exactly-−1.0 stone, TEST 17 2×; bed −1.0 on marked −1.0 slab where flush is −1.5 — unflagged sibling seam) | TOP-SEAT; both cells stored (`BED_PART` pair) | Pre-implementation probe: recorder row for HEAD stored dy (critic A4) |
| 9 | **Torches / pots / candles / decorations** (torch, soul_torch, candle, flower_pot) | Family lanes NaN on full-block supports at any depth except the torch-only −1.0 literal (`SlabSupport.java:613-614`); pot/bed land presence-−0.5 via `slabColumnYOffset:3330` / anchored generic `:2759`; TEST 17: pot/bed −0.5 on exactly-−1.0 supports, work at −1.5 only because a marked slab support feeds `:2981-2983` | TOP-SEAT, depth-agnostic. Exactly −1.0 is **not special for objects** (no legitimate hover reading — pending D4) | Replaces all ten beta35 contact lanes as placement-time authority; lanes stay as frozen-OFF reads |
| 10 | **Banners / standing signs / buttons(floor) / levers / pressure plates / rails / redstone wire / repeaters+comparators / daylight detector / conduit** (untraced kit tail, critic C) | Banners/signs capture fires (no override); repeater/comparator (`DiodeBlock`) are capture holes (NO-SUPER census) AND lane-less; others lane-less → 0.0 everywhere | TOP-SEAT by default under the one rule; wall-attached variants adopt the **mounted-on** owner's frame (wall branch `SlabSupport.java:989-998` becomes a resolver input, not a live veto) | Each gets a sweep row + RED test; any the maintainer-ruled exception moves to the FLUSH list explicitly (D8) |
| 11 | **Fences / walls / iron bars / panes** (connectors) | The one shape-correct lane (`SlabSupport.java:494-506`) but depth-capped inputs (§1.3.1 caveat); connector model gate `OffsetBlockStateModel.java:193-199` | TOP-SEAT with shape-aware plane (already the formula's origin); SIDE for cantilever continuation (stored-magnitude inheritance, no BFS degradation) | Connector compound sidecar authoring (`SlabAnchorAttachment.java:439-441`) becomes compat |
| 12 | **Gates / trapdoors** | Own beta35 lanes, −1.0-capped via `floorTorchBottomSlabSupportDy` | TOP-SEAT | |
| 13 | **Stairs** | Hard-flush list `SlabSupport.java:2871` | **the maintainer policy row (D8)** — recommend TOP-SEAT for consistency; if refused, stays an explicit FLUSH-list entry | |
| 14 | **Hanging / ceiling-attached** (lantern[hanging], chain, hanging signs, hanging roots, spore blossom, dripstone down) | Dispatch `SlabSupport.java:2474-2478` → `ceilingHungDecorationDy:2417-2462`. **A1 resolved by code reading (this change): captured hangers ARE frozen** — capture excludes only null/client/air (`SlabAnchorAttachment.java:269-283`); the `:461-462` carve-out exempts hangers from FROZEN_FLAT marking only; the store short-circuit precedes the ceiling dispatch. TEST 13–17 validated this frozen behavior | HANG (§1.3.2): land relative to `visibleBottom(support above)`, then frozen like everything else. Attachment direction affects the **landing input**, not freeze-vs-follow | authority-inventory's "deliberately unfrozen followers" claim is wrong; design against frozen-hanger reality. Dripstone pointing UP = TOP-SEAT (family row, currently untraced) |
| 15 | **Waterlogged placements** | F5 fluid-blind discipline in reads/writes | Identical to dry (resolver never reads fluid); water-bucket into an existing lowered cell is a property-only change → store survives (removal hook fires on block-kind change only, `BlockOnStateReplacedAnchorMixin.java:17-21`) — pin with a gametest | |
| 16 | **AIMLESS routes** (dispenser `setBlock` variants, bone meal, falling blocks, fluid mechanics, worldgen, mobs, commands) | Never captured → NaN → live lanes forever (`SlabSupport.java:1030-1035` falls through) | Documented default (D7): remain uncaptured/live (LAW §4 vanilla-mechanic exclusion) — **explicitly listed**, no longer silent. Dispenser-shulker (direct `place()`, null player) = captured-but-aimless → resolver's AIMLESS default: flush unless family lanes say otherwise (current behavior, now written down) | |

---

## 3. Capture: the single capture point

**Where:** the tail of the existing `@Inject(method="place", at=@At("RETURN"))` handler — `BlockItemPlacementIntentMixin.java:1546` — after every marker write for that placement (`:1563-1641`). Proven route-complete by the capture-mechanism lane's `javap` sweep: only `BlockItem` declares `place(BlockPlaceContext)`; `DoubleHighBlockItem`/`BedItem` override only `placeBlock`; `SolidBucketItem` (powder snow) and `PlaceOnWaterBlockItem` route through super `useOn` → `place`. This one point therefore covers plain blocks, **all setPlacedBy-override bypassers** (doors, DiodeBlock, CrafterBlock, MossyCarpetBlock, PistonBaseBlock, tripwire hooks, wither skulls…), **both cells of double blocks** (they exist by RETURN), **beds** (HEAD exists by RETURN — kills the half-formed-bed capture), and **bucket placements**.

**What it writes:** `PlacementResolution.landingDy` for `placedPos` and each pair cell — **the resolver's number, not a re-read**. If a placement arrives with no resolution (AIMLESS route), capture computes via `getUnstoredYOffset` (`SlabSupport.java:1057`) — never the store-consulting `getYOffset`, which would re-freeze a stale/haunted entry (`SlabAnchorAttachment.java:273` + `SlabSupport.java:1030` is today's re-freeze trap). Note: resolver-writes-overwrite semantics also **heal the haunted-cell class for aimed placements** (critic B2 — flag-2 `setBlock` removals stranding stored dy, documented at `SlabRigCommand.java:85, :991-993, :1073-1079`): an aimed placement into a haunted cell stores the aim-derived value regardless of the stale entry.

**What happens to the old hooks:** `BlockOnPlacedAnchorMixin.java:37`'s `capturePlacementDy` call is **removed in the same commit** (single-writer; no double-capture ordering hazard — risk R1). `addAnchor`/`freezeLoweredOnPlace` (`:28/:33`) stay untouched as frozen-OFF marker authors. Fix the lying javadoc (`SlabAnchorAttachment.java:263-266`) in the same commit (critic A5).

**Exclusion list (capture must NOT store):**
1. Client side (`world.isClientSide()` — client uses the predicted mirror, §1.6/R2).
2. Air / failed placements (`!consumesAction()`, `:1553`).
3. TS-owned states — hygiene exclusion; the read-side ordering (`shouldSkipOffset` before the store, `SlabSupport.java:1021-1030`) stays load-bearing regardless.
4. Non-item routes (worldgen/`setBlock`/mobs/pistons/commands) — deliberate "terrain stays geometric" invariant (`SlabAnchorAttachment.java:353-354`); `/slabrig` continues authoring via the public attachment API by design (rig owners are marker-authored differently from player-placed owners — disclosed in §6 so TEST evidence is interpreted correctly).
5. Piston arrivals — `moved=true` clears at the source (`BlockOnStateReplacedAnchorMixin.java:36-39`); destination reads live → **visible height pop on pushing a frozen lowered block; disclosed limitation** (D7/risk R11), not silently absorbed.

**Client mirror:** the store syncs via chunk attachment (`syncWith(...all())`, `SlabAnchorAttachment.java:257`; client lookup `SlabAnchorClientSync.java:92-105`). The in-flight window between place and sync is the observed `LIVE_PLACEMENT_SIDE_DY_SPLIT` (server 0.0 / client −0.5, 6× in TEST 17). Fix: the resolver runs identically on the client's `place` call, writing a **client-predicted dy entry** (generalizing the one existing predicted writer, `updateClientPredictedPersistentLoweredSlabCarrier`, `BlockItemPlacementIntentMixin.java:1608-1613`) that the synced value replaces.

---

## 4. Migration — what dies, what stays, and the commit sequence

### 4.1 Replaced as placement-time authority (stay as frozen-OFF/legacy-NaN reads only)

All of `getYOffsetInner`'s lane taxonomy remains **byte-identical** for frozen-OFF worlds and NaN cells: the anchored fallbacks (`SlabSupport.java:2555, :2759`), deep-rest (`:2546-2554`), compound sidecar (`:2661-2678`), beta35 lanes (`:2802-2850`), `shouldOffset` generic (`:2852-2865`), column walks (`:3287-3339`), cantilever BFS readers. The resolver never calls them for aimed placements; they are the compatibility floor. **Live-lane widening is forbidden** — it retroactively moves placed blocks (LAW §3; risk R4).

### 4.2 Markers

Compound-visible markers, anchors, FROZEN_FLAT, carriers keep being authored (from the resolver's decision, not parallel logic) as frozen-OFF belt-and-suspenders for one release cycle (D6). Marker depth vocabulary is never extended — the store is the depth authority; markers stay presence bits.

### 4.3 Pins that die (placement path)

`BlockItemPlacementIntentMixin.java:83, :92-93, :949, :180, :193-194, :204, :217-218, :231, :245-246`; `SlabSupport.java:1210, :3055`; `SlabAnchorAttachment.java:1373-1374, :1411-1437` (widened to any `SlabBlock`); `ServerInteractBlockHitToleranceMixin.java:33/:131/:286-289` (validation center from stored/live owner dy). The dead code inventory (`SlabAnchorAttachment.java:1588` `if (true) return false`; `isLoweringTopLikeCeiling` `SlabSupport.java:2404-2406`; legacy visible-lane hop fenced at `:1280`; the entire legacy retarget + `PlacementIntentState` producer — see §9.1) is deleted **only** with LAW-SIGNOFF plumbing, in a docs/cleanup commit, per the tripwire (`LAW.md:41-43`).

### 4.4 Commit sequence (one behavior commit per live gate — LIVE_LEDGER rule)

Suite note: gametests run frozen-OFF; landing tests flip `FROZEN_DY_ENABLED` in-process (public mutable static; established pattern at `DeepCompoundTowerLawTest.java:339-345`).

| # | Commit | Behavior? | Live gate |
|---|---|---|---|
| C0 | Probes + docs: MossyCarpet/snow-stacking bytecode census on ONE canonical mapped jar (critic A2/D1); bed-HEAD recorder probe (A4); producer post-mortem (§9.1); RED gametests added as expected-red law-matrix rows (pattern: `DeepCompoundTowerLawTest.java:264`); fix capture javadoc | No | — |
| C1 | **FROZEN_DY default flip** to ON + legacy-NaN fallthrough documented (the resolver is invisible without it — critic D4; resolve the "how was it on in TEST jars" question by making the default explicit in code) | Yes (1 line + docs) | TEST 18 (STAYS regression only) |
| C2 | **Resolver core + slab families** (rows 1–3): kill layer-1/2 pins for slab items, capture-at-RETURN for slab placements, client predicted entry, A7 re-spec (pre-approved, §5), server-validation generalization | Yes | TEST 19 |
| C3 | **Capture relocation for all families + doors/beds pair cells** (rows 7–8) | Yes | TEST 20 |
| C4 | **Object TOP-SEAT** (rows 9–12 + untraced tail row 10) replacing beta35 lanes at placement | Yes | TEST 21 |
| C5 | **Thin layers + powder snow AIM-KEYED** + carpet triad unification (fork/mixin/sentinel lockstep) | Yes | TEST 22 |
| C6 | Removal-side hardening: store/marker clear at a non-overridable seam (below `Block` subclass dispatch) for the 16+ `affectNeighborsAfterRemoval` NO-SUPER overriders + flag-2 class; haunted-cell RED test goes green | Yes | TEST 23 |
| C7 | Cleanup: dead code deletion, marker retirement decision (D6), docs | No | — |

Each of C2–C6 regenerates its own baselines in the same commit (§6.3) so the suite is green at every step.

---

## 5. A7 Resolution (explicit)

`useOnSlabOnTopOfCompoundFollowsToMinusOne` (`Slabbed2612UseOnPlacementTest.java:545-561`) **encodes the old rule and contradicts the law for the aimed case**:

- Its fixture is `helper.setBlock`-built (`buildCompoundMinusOne`, `:450-456`) — no anchors, no markers — so it exercises only the marker-missing fallback.
- Its hit is `upHit` = the **logical** plane `ownerY+1.0` (`:106-108`), which the honest offset raycast never produces for a −1.0 owner (the test file itself documents the honest-band split at `:120-129`; TEST 17 live hits arrive at `ownerY+0.0`).
- It asserts −0.5 — the slab's bottom floating 0.5 above the clicked visible surface — and its own comment admits it was "locked to the observed −0.5" (`:554-557`).
- Production code cites it as authority: the deep-rest exclusion comment at `SlabSupport.java:2536-2538` ("the WYSIWYG on-top rule, A7"). Test and code prop each other up.

**Under the unified rule:** aim at the visible top of a −1.0 owner → land flush at −1.0. **the maintainer must rule (D1):** re-specify A7 to assert −1.0 with an aim-honest fixture (real placement pipeline or visible-plane hit). The −0.5 outcome remains reachable only through a *different aim* (side-attach off a −0.5 neighbor), never from the top-face click. The frozen-OFF live lanes keep the old −0.5 at exactly −1.0 (deep-rest threshold `:2551` unchanged) — a **disclosed frozen-OFF divergence**, not a live-lane widening. The sibling boundary rows ride the same ruling: pot/bed flush at exactly −1.0 (D4), and bed on a marked −1.0 slab → −1.5 flush (D1b).

---

## 6. Test Plan

### 6.1 RED-first gametests (added in C0 as expected-red rows; flipped green per commit)

All use the real `useOn` path with `FROZEN_DY_ENABLED` flipped in-process; each asserts the **stored** value and the read-back:

- C2: `slabOnVisibleTopOfMinus1FullBlockLandsFlush` (today 0.0/−0.5); `slabOnMinus1TopSlabOwnerLandsFlush` (today 0.0 + dy-split); `oakSlabParityOnCompoundOwner` (stone pins); `sideSlabOffMinus15OwnerSeatsMinus15` (today −1.0); `chainedSideSlabsInheritStoredMagnitude` (today degrade); `doubleMergeKeepsOwnerStoredDy`; `downFaceUnderLoweredOwnerResolvesOrRefuses` (B1); `waterloggedLandingEqualsDry`.
- C3: `doorBothCellsStoredOnLoweredOwner` (today NaN/0.0); `bedBothCellsStoredAtExactlyMinus1` (today −0.5/NaN); `doorToggleKeepsStore` (property-change pin).
- C4: `potOnMinus1StoneSeatsFlush`, `potOnMinus15StoneSeatsFlush` (critic D3's extrapolation becomes a test); `torchOnDeepStoneUncapped` (today −1.0 literal); `bannerOnLoweredOwner`, `repeaterOnLoweredOwner` (double-broken candidates); `fenceOffDeepOwner`; per-row coverage for the untraced kit tail (row 10).
- C5: `carpetAimedOnLoweredOwnerSeats` / `carpetSetBlockStaysFlush` (route split proves the DODO survives); `powderSnowBucketAimedSeats` / existing `powderSnowOnSlabStaysFlush` stays green; `snowLayerStackingPreservesStore` (closes critic D1 after the C0 census).
- C6: `hauntedCellRePlacementStoresAimValue` (flag-2 removal → re-place); `chestBreakClearsStore` (removal-bypass class).
- Always-on: extend `NeighborUpdateInvarianceTest` (the S-2 gate, `LAW.md:37-40`) with one row per new seat family; `anchoredTopSlabOverCeilingAttachedChainDoesNotRecurse` stays as the TEST-16 pin; a resolver-purity unit ("resolver output independent of fluid state and of neighbor mutations after placement").

### 6.2 Recorder oracle

`LiveCursorIntentRecorder` EXPECTED_DY lane tables update per family commit so `EXPECTED_DY_MISMATCH` rows stay meaningful in TEST 19–23; sentinel (`SlabModelStaleSentinel`) carpet policy collapses in C5 (or it manufactures false DIVERGENT reds — carpet-thin finding).

### 6.3 Baselines (regenerate in the same commit as the behavior they encode)

- **Registry sweep**: in-code `BASELINE` map (`RegistrySweepTest.java:138-161`) + hard gate (`hardGateAllowlistFamilies`, `:694-720`) regenerated from `build/reports/slabbed-sweep.tsv` at C2 (slab rows, e.g. `oak_slab\tmarked_slab` currently pins `0.0000|GAP|1.5000|OK` — the hole is baselined), C4 (object rows incl. the pinned `STAYS_FAIL` frozen-OFF rows), C5 (thin-layer rows).
- **dy fingerprint** (`src/gametest/resources/dy-baseline.txt`, harness `Slabbed2612DyFingerprintTest`): mostly `setBlock`/natural-fill → expected UNCHANGED; verify per commit, regenerate only rows exercising placement-authored state.
- **Law-matrix expected-red subjects** (e.g. `slab_on_deep_lowered_full_block`, `DeepCompoundTowerLawTest.java:264`): flip red→green rows in the commit that fixes them, never earlier.

---

## 7. Risk Register (ranked)

| # | Risk | Mitigation |
|---|---|---|
| R1 | **Capture reorder / double-capture** — a second writer re-freezes a stale or half-formed value (`SlabAnchorAttachment.java:273` + `SlabSupport.java:1030`) | Single-writer: old capture call removed in C3's same commit; resolver value overwrites; AIMLESS fallback uses `getUnstoredYOffset` (`SlabSupport.java:1057`) |
| R2 | **Client/server divergence (RC1 snap class)** — widened predict/sync window as more families go store-authoritative | Resolver runs on client `place`; predicted entry generalizing `:1608-1613`; synced attachment wins (`SlabAnchorClientSync.java:92-105`); dy-split recorder column is the live gate metric |
| R3 | **TEST-16 recursion class** — new support walks re-entering `getYOffsetInner` sideways (crash pin: `SlabSupport.java:2539-2545`) | Resolver reads stored-first, and its live fallback goes through the guarded **public** `getYOffset` from placement context (outside `IN_GET_Y_OFFSET`); no new inner-to-inner calls |
| R4 | **Anchor/lane widening → TS regression** (cross-port law, failure mode 4: smoosh/world-holes) | Resolver consults `CompatHooks.shouldSkipOffset`/`shouldSkipSlabSupport`/`terrainSlabsHandlesObjectOffset` at the owner classification step; the choke points (`SlabSupport.java:158, :1021, :2969; SlabAnchorAttachment.java:1404`) untouched; re-run the anchor-widening audit checklist per commit |
| R5 | **Live-lane widening = law violation** — "fixing" frozen-OFF reads retroactively moves placed blocks | Hard rule: C2–C6 touch placement path + store only; frozen-OFF divergences are disclosed (§5), not patched |
| R6 | **Server hit-validation window** (§1.4 row 4) — deep aims swallowed by the −1.0-pinned center shift / 1.0000001 per-axis tolerance (`ServerInteractBlockHitToleranceMixin.java:131, :184-189, :286-289`) | Generalize the validation center by owner stored/live dy in C2; RED test at −2.0/−2.5 owners; raycast side is already parametric to 16 cells, air-terminated (`SlabbedOffsetRaycast.java:136, :151-155`) — deeper-than-16 disclosed |
| R7 | **Render-region reach / cull** — deep seats render several cells below their grid cell; region-border reads throw (guarded `OffsetBlockStateModel.java:156-168`); cull compares neighbor dy (`:208-244`); carpets seated −1.0 render wholly inside the cell below | Occluded-occupancy classifier must recognize newly-seated families (thin layers) so the up-face click remap (`BlockItemPlacementIntentMixin.java:856-886`) doesn't misroute; remesh band check per family commit; `storedPlacementDy` on non-`Level` views routes through the client lookup (`SlabAnchorAttachment.java:290-291`) — verified wired |
| R8 | **Collision triad** — new logical seats (carpets, powder snow, deep objects) need shape/collision/raycast moving together on ONE authority (dy-triad law); carpet shape mixin is client-only today (`CarpetDyShapeMixin` in `slabbed.client.mixins.json`) | C5 moves it to common; `Slabbed2612CollisionDepthTest`/`GhostLoweredCollisionProofTest` rows extended; powder snow entity-inside behavior gets a live check |
| R9 | **Baseline false-greens** — stale sweep/fingerprint/law-matrix rows masking regressions (the historical failure class) | §6.3 same-commit regeneration rule; hard gate refuses "no baseline" rows (`RegistrySweepTest.java:707`) |
| R10 | **Null-player / aimless capture NPE or misclassification** (dispenser-shulker calls `place()` directly) | Resolver's AIMLESS branch never dereferences player/hit; explicit default documented (§2 row 16) |
| R11 | **Frozen-OFF holes created/healed** — healed: haunted re-freeze (aimed), door NaN, bed half-formed capture, dy-split window; created/kept: exactly-−1.0 deep-rest divergence (§5), piston-destination pop, legacy-NaN worlds keep old seats, pre-fix wrongly-frozen values persist by law (chain-degradation seats stay; the maintainer may perceive "bug persists" — D9 re-seat tool) | Disclosure table in the release notes; DeepCompoundTowerLawTest-style logged-not-asserted rows |
| R12 | **LAW tripwire friction** — resolver terms (follow/inherit/merge) trip `tools/hooks/commit-msg` | Every C2–C6 commit carries `LAW-PREFLIGHT`/`LAW-SIGNOFF` + an invariance row by plan, not ad hoc |

---

## 8. DECISIONS the maintainer MUST MAKE (before implementation)

1. **A7 flip (D1):** aimed top-click on a −1.0 owner lands flush −1.0; A7 re-specified accordingly (§5). Includes **D1b**: bed/pot/objects on a **marked −1.0 bottom slab** seat −1.5 (true flush) instead of today's −1.0.
2. **Powder snow (D2):** hand-bucketed powder snow seats on the clicked lowered surface (AIM-KEYED), while natural/worldgen powder snow stays flush — flipping only the aimed half of the da8cc3cb DODO decision. Yes/no.
3. **Thin layers (D3):** carpets/snow layers become **logically** seated when aimed (outline + collision + raycast, not render courtesy) — including carpet on a PLAIN bottom slab becoming logical −0.5 (today render-only). Yes/no; if yes, confirm the triad move (C5).
4. **Objects at exactly −1.0 (D4):** confirm no legitimate aim wants an object hovering at −0.5 above a −1.0 support → objects seat flush unconditionally; only slab subjects keep aim disambiguation at that depth (§1.3.6).
5. **FROZEN_DY ship default (D5):** flip default ON in C1; legacy worlds' NaN cells stay live-lane forever (no retro-migration). Confirm posture + that TEST jars' flag mechanism gets recorded in the manifest going forward.
6. **Marker retirement (D6):** keep authoring legacy markers from resolver decisions for one release (recommended), or stop immediately at C2.
7. **Aimless routes (D7):** dispenser/bone-meal/falling/fluid/worldgen placements stay uncaptured-live (documented LAW §4 exclusion, recommended) or gain capture-on-creation (bigger blast radius; needs its own regression matrix). Includes accepting the **piston-destination pop** disclosure.
8. **FLUSH-list policy rows (D8):** stairs, panes-as-subjects, levers, pressure plates, rails, redstone wire, repeaters/comparators, daylight detector, conduit — default under the one rule is TOP-SEAT; name any that should stay always-flush.
9. **Existing wrong seats (D9):** frozen values placed before the fix stay by law. Accept, or ship a `/slabrig reseat`-style explicit tool (player-invoked, not automatic).
10. **Hanging items freeze (D10):** confirm frozen-not-following hangers (today's tested reality under frozen mode) is the intent; attachment direction affects only the landing computation.
11. **Legacy retarget + PlacementIntentState (D11):** delete or keep-as-rollback (§9.1). Recommend delete in C7 (it is dead under the shipping config and stone-pinned besides).
12. **DOUBLE-merge row (D12):** confirm merged DOUBLE re-stores the owner's dy at both merge sites (§1.3.4).

---

## 9. Appendix — critic gaps closed by this change's verification

**9.1 The PlacementIntentState producer (critic rank #1) — closed.** The producer is `GameRendererCrosshairRetargetMixin` (client): `slabbed$recordPlacementIntentState` (`:1112-1169`) and the FINAL_TARGET_UNKNOWN variant (`:1171-1212`), invoked only from the legacy post-hoc retarget lanes. **The entire `pick` handler early-returns when `SlabbedOffsetRaycast.ENABLED`** (`GameRendererCrosshairRetargetMixin.java:88-96`), which is default-ON (`SlabbedOffsetRaycast.java:61-62`) and was ON in every TEST 13–17 manifest. Therefore under the shipping configuration the snapshot is **never produced**; the consumer (`BlockItemPlacementIntentMixin.java:545+`) always falls through. It is additionally held-item-pinned (`SlabBlock`/`Blocks.STONE` at `:1091-1100`; `"minecraft:stone"`/`"minecraft:stone_slab"` at `PlacementIntentState.java:182-184`), horizontal-faces-only (`:1138-1141`), a cross-thread static with a 5s lifetime (`PlacementIntentState.java:9`), and single-player-only by construction (client static read by the integrated server thread). **Design consequence:** the resolver must NOT be built on this channel — and does not need to be: the server-received use packet already carries the honest aim (§1.2). The snapshot's `Snapshot` record shape (`:21-39`) is, however, the right shape for the resolver's internal `PlacementResolution` thread-local.

**9.2 A1 (hanger freeze-vs-follow):** resolved in favor of capture-mechanism — captured hangers are frozen (capture exclusions `SlabAnchorAttachment.java:269-283`; store short-circuit precedes the ceiling dispatch; `:461-462` is a FROZEN_FLAT-marker carve-out only). §2 row 14 designs against this.

**9.3 Fence-formula exemplar (critic #6):** confirmed shape-correct but input-capped (`beta35FenceWallVisibleSupportDy` codomain {−1.0, −0.5, NaN}; `floorTorchBottomSlabSupportDy:2952-3011` codomain {NaN, −1.0, −0.5, 0.0}). The resolver adopts the formula, replaces the input (§1.3.1).

**9.4 New change site no lane found:** the server use-packet validation seam (`ServerInteractBlockHitToleranceMixin`) is a fourth −1.0 pin layer and a hard prerequisite for deep-aim parity (§1.4 row 4, risk R6).

**9.5 Remaining pre-implementation probes (C0):** MossyCarpet/snow-stacking census on one canonical jar (A2/D1); bed-HEAD stored-dy recorder row (A4); TS `ModOnTopBlocksRegistry` runtime contents for carpets (row 5 TS gate); confirm the two 07-07/08 dy-split rows' F1-gate provenance (slab-landing open question — does not change the mechanism).