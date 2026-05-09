# Beta4 Compound Lowered Full Block Contract Audit

Audit-only. No gameplay edits in this slice. Release remains blocked.

Decision cross-reference: `docs/beta4-compound-source-mode-design.md` adopts
A-prime, explicit authored lane/depth source mode for compound ordinary full
blocks. Matrix rows below now have intended beta4 outcomes; implementation is
still blocked behind proof and source-mode work.

The earlier slab reject rows were technically intentional and safe, but Julia's
live review shows that safety alone is not enough for product feel. The new
slab merge/remap grammar lives in
`docs/beta4-compound-slab-merge-grammar.md`. This audit remains a design/proof
record; it does not mark implementation complete.

## Current savepoint

- HEAD: `06724fb`
- Tag: `save/beta4-compound-placement-popoff-fix`
- Branch: `integrate/phase19-into-side-slab-top-support`

## Live result

Total fail. Not release-saveable.

Julia live retest at `06724fb` (evidence harvested in
`tmp/beta4-compound-live-fail-contract-audit-06724fb/`) shows the compound
state is recognized in isolation but is not legal across the broader
placement / survival / support-removal surface.

### Player symptoms (live)

- Aiming at the **bottom half** of the compound `dy=-1.0` full block while
  attempting side placement: flicker / pop-off after the queued tick.
- Aiming at the **top half** of the same compound full block: placement
  goes upward into the wrong column / wrong dy lane.
- **Breaking the lower source slab** under the compound full block: the
  compound full block jumps upward (collapses to `dy=-0.5` or vanilla
  `dy=0`).
- Slab placement adjacent to the compound full block: same flicker / wrong
  column behavior.
- Live feel: not release-saveable.

## What automation proved at `06724fb`

The recent fix stack contributed real, narrow GREEN proofs:

- `[BETA4_COMPOUND_LOWERED_FULL_BLOCK_COLLAPSE_GREEN]` — compound `dy=-1.0`
  is preserved through the anchor finalization for the seeded triad
  (`9bf3bdc`).
- `[BETA4_COMPOUND_LOWERED_FULL_BLOCK_TRIAD_*]` — outline / raycast / model
  shape parity for the compound `dy=-1.0` cell.
- `[BETA4_COMPOUND_PLACEMENT_POPOFF_GREEN]` — a single side-adjacent
  placement against the seeded compound source survives the queued tick in
  the lowered lane (`6e0bd10` / `06724fb`).

These are valid proofs of three isolated slices. They do not prove a full
contract.

## Why automation is insufficient

Automation seeds a perfectly legal triad in a fixed orientation and tests
one face / one half / one neighbor at a time. Live play exposes:

1. **Half / face grammar gap**. The compound `dy=-1.0` outline visually
   spans world cells `y=-58` (bottom half) and `y=-57` (top half). The
   block exists at `y=-57`. Side hits at `y=-58` vs `y=-57` are completely
   different placement targets, and the compound state has no documented
   rule for which column / dy lane each half should produce.
2. **Source-truth not self-describing**. Live evidence
   (`run_logs_latest_log.extract.txt` lines around tick 36127–36128 in
   `tmp/beta4-live-retest-06724fb`) shows the compound block as
   `state=Block{minecraft:stone} dy=-1.000000 anchored=true
   persistentFullBlockAnchor=true sourceMode=normal`, with `dy=-1.0`
   re-derived each lookup from the slab below
   (`pos=14,-58,0 ... persistentLoweredSlabCarrier=true`). The boolean
   `persistentFullBlockAnchor` cannot tell apart "I am a `dy=-0.5` anchor"
   from "I am a `dy=-1.0` anchor over a lowered carrier slab". The depth
   is not stored, it is recomputed.
3. **Per-column lane, not per-row lane**. Compound `dy=-1.0` only exists
   for a column that has a `persistentLoweredSlabCarrier` directly below.
   Lateral placement into an adjacent column with empty space below
   cannot legally inherit `dy=-1.0` because no support source exists in
   that column.
4. **Source-removal recompute hazard**. `SlabSupport.getYOffsetInner`
   re-runs `isAdjacentSideSlabLowered(world, pos.down(), belowSlab)` on
   every query. Breaking / replacing the slab below makes the compound
   query fail and the dy collapses to `-0.5` (anchor branch) or `0`
   (vanilla). This is the live "jump" Julia observes.
5. **Neighbor-update / `canPlaceAt` recompute hazard**. The same
   recompute is what `getStateForNeighborUpdate` and `canPlaceAt` rely
   on. Any neighbor change that breaks the predicate signal flips the
   compound block out of the `dy=-1.0` lane.

The current 06724fb state has no single contract that names what should
happen for each face × half × support-removal × reload combination.

## Legal-state decision table

Decision required for each row before any further implementation. Status
columns:

- **Current observed**: what the live build at `06724fb` does.
- **Intended**: TBD by design owner.
- **Required source truth**: what the state law must store / derive to
  guarantee that intended outcome.
- **Proof status**: which automation case (if any) covers this.

| # | Player action | Current observed | Intended | Required state / source truth | Proof status |
| - | ------------- | ---------------- | -------- | ----------------------------- | ------------ |
| 1 | Select / look at compound `dy=-1.0` block | Outline + raycast at `dy=-1.0` for the seeded triad | (decide) | Triad shape parity | TRIAD_GREEN at `06724fb`, only seeded fixture |
| 2 | Place ordinary stone on TOP of compound | (mostly intercepted by intent mixin; not in primary failure surface) | (decide) | Top-of-compound = next compound `dy=-1.0`? Vanilla `dy=0`? | No live proof |
| 3 | Place full block on SIDE LOWER HALF (hit `y=-58` band of the compound) | Flicker / pop-off | (decide: place at compound column `dy=-1.0`? at adjacent column `dy=-1.0`? at adjacent column `dy=-0.5`? vanilla?) | Half→column mapping rule; compound depth in adjacent column requires its own slab carrier | Seeded GREEN only for one face / one column |
| 4 | Place full block on SIDE UPPER HALF (hit `y=-57` band of the compound) | Upward placement / wrong dy | (decide) | Half→column mapping rule | None |
| 5 | Place slab on SIDE LOWER HALF | Flicker / wrong column | (decide: lowered bottom carrier? lowered top? double? vanilla?) | Slab grammar against compound side | None |
| 6 | Place slab on SIDE UPPER HALF | Wrong column / wrong half | (decide) | Slab grammar against compound side | None |
| 7 | Break compound block itself | (decide if it should drop normal item, drop nothing, leave anchor ghost) | (decide) | Anchor cleanup contract | Existing `removeAnchor` paths, not validated for compound |
| 8 | Break the lower source slab (the `persistentLoweredSlabCarrier` directly under the compound) | Compound block jumps up to `dy=-0.5` or vanilla `dy=0` | (decide A / B / C below) | If A: compound must self-describe its depth; if B: re-normalize rule; if C: explicit pop with item drop | None |
| 9 | Neighbor update on a side neighbor without breaking the source slab | (compound stays for now; not directly observed) | Compound stays at `dy=-1.0` | `getStateForNeighborUpdate` must not call into the compound recompute when the column source is unchanged | None |
| 10 | Save / reload / chunk reload of the compound column | `BETA4_RELOAD_JUMP_SYNC` events fire with `oldCount=0 newCount=0`; compound dy depends on slab carrier still being persisted | Compound stays at `dy=-1.0` | Either both blocks rehydrate from disk before the recompute, or the compound block stores its own depth | Reload jump sync is RECORDER ONLY, no GREEN |
| 11 | Chunk unload then reload only the compound column without the slab column loaded | (untested; possible mismatch window) | (decide) | Cross-chunk source-truth ordering | None |
| 12 | Live triad: source slab broken then re-placed quickly | (untested) | (decide) | Compound recovery rule | None |

Cases 3, 4, 5, 6, 8 are the live failures Julia reproduced. Cases 9–12 are
not yet observed but follow from the same source-truth ambiguity.

## Is `dy=-1.0` viable?

**Risky / undecided.** It is viable as a per-column visual height **only
when** every system that reads dy understands "compound depth comes from
the column source slab right below". Today that is true for
`getYOffsetInner` (centrally fixed), torch/non-anchor compound, and
outline/raycast/model parity for the seeded triad.

It is **not yet viable** as a horizontally extensible lane:

- Lateral side placement assumes the new block can also be `dy=-1.0`. That
  only holds if the new block's column also has a
  `persistentLoweredSlabCarrier` directly below. Otherwise the new block
  must fall back to `dy=-0.5` (lowered single carrier) or `dy=0` (vanilla),
  and the half→column mapping for the click must be defined.
- Source-removal collapses dy because the depth is recomputed, not stored.
  The current `persistentFullBlockAnchor` boolean cannot encode "this
  anchor was authored at depth `-1.0` over a lowered carrier and must
  not silently re-normalize to `-0.5`".
- Save / reload survival of `dy=-1.0` is only correct when the slab below
  rehydrates before any block in the compound column is asked for its
  offset. Cross-chunk ordering is not proven.

The `dy=-1.0` value itself is fine. The viability gap is in the **state
law** that places, supports, and persists it.

## Anchor representation risk

`SlabAnchorAttachment.isAnchored` is a boolean stored per-position. The
current code derives the depth from this boolean plus a live query against
the block below:

- `getYOffsetInner` anchor branch (lines around 651, 674–680 in
  `src/main/java/com/slabbed/util/SlabSupport.java`) returns `-1.0` only
  when `isBottomSlab(belowSlab) && isAdjacentSideSlabLowered(world,
  belowPos, belowSlab)` evaluates true on every call.
- The "non-anchor" compound branch (lines 691–696) does the same.
- `sourceMode=` strings in the live recorder logs (e.g.
  `sourceMode=dynamicLoweredOrAnchored`, `sourceMode=normal`,
  `sourceMode=persistentLoweredSlabCarrier`) are derived labels, not stored
  attachments.

**Risk**: a single boolean cannot encode the difference between an anchor
authored at `dy=-0.5` over a vanilla / dynamic lowered source and an
anchor authored at `dy=-1.0` over a lowered bottom-slab carrier. Any time
the source-below predicate flips (slab broken, slab replaced, chunk loaded
out of order, neighbor update triggers a re-derivation), the depth flips
with it.

**Richer representation options** (not implemented, design only):

- Add a `compoundDepth` byte / enum to `SlabAnchorAttachment` per anchor
  pos, so an anchor authored at `dy=-1.0` is stored as
  `compoundDepth=COMPOUND_LOWERED` and not re-derived from neighbors.
- Or replace the boolean with a `sourceMode` enum
  (`NORMAL_ANCHOR | LOWERED_CARRIER | COMPOUND_LOWERED_OVER_CARRIER`) and
  let `getYOffsetInner` switch on it without querying the block below.
- Either approach must define explicit rules for what happens when the
  recorded source is invalidated (case 8 in the table). Persistent
  storage by itself does not decide whether the compound should jump,
  pop, re-normalize, or persist.

This is exactly what the doctrine warns against fixing with one more
boolean: the next implementation must commit to a richer representation
**only after** the decision table above is filled in.

## Which proofs prove a slice but not the live contract

- `[BETA4_COMPOUND_LOWERED_FULL_BLOCK_COLLAPSE_GREEN]`: proves the seeded
  triad keeps `dy=-1.0` after `Block.onPlaced` syncs the anchor. Does
  not exercise side placement, support removal, neighbor update, or
  reload.
- `[BETA4_COMPOUND_LOWERED_FULL_BLOCK_TRIAD_*]`: proves outline / raycast
  / model shape for the seeded triad cell only.
- `[BETA4_COMPOUND_PLACEMENT_POPOFF_GREEN]`: proves one side-adjacent
  placement against one face of the seeded compound source survives one
  queued tick. Does not exercise both halves, both face axes, slab-held
  adjacency, support-removal, or save/reload.
- `[BETA4_RELOAD_JUMP_RECORDER]` / `[BETA4_OUTLINE_RECORDER]` /
  `[BETA4_PLACEMENT_AUTHOR_RECORDER]`: recorder-only, not proofs.

No proof exists for: side lower-half × side upper-half × slab-held ×
ordinary-held × source-slab-break × neighbor-update × save/reload as a
matrix.

## Recommended next slice

**A. Add a comprehensive RED proof matrix for compound `dy=-1.0` faces /
halves / support removal, before any further implementation.**

Rationale: every prior slice in the recent stack was a single-case fix
followed by a single-case GREEN. The doctrine states a Slabbed state
must be named **and** proven across placement, collision, survival,
neighbor update, reload, triad, and live feel. The compound `dy=-1.0`
state is named but the proof matrix is empty for at least seven of the
twelve decision-table cases.

The matrix must enumerate, with a RED-only opt-in proof per case:

- side LOWER half × N/E/S/W × ordinary-held vs slab-held
- side UPPER half × N/E/S/W × ordinary-held vs slab-held
- TOP-face × ordinary-held vs slab-held
- break source slab below × neighbor update × `getStateForNeighborUpdate`
  recompute
- save / reload / chunk reload (cross-column ordering)
- compound block broken first × source slab broken first

Only after that matrix exists and Julia signs off on the **intended**
column for each case can the team responsibly choose between B (richer
anchor / source mode) and C (revert / narrow `dy=-1.0`). A blind jump to
B risks coding around the wrong cases; a blind jump to C loses a feature
Julia may want.

## Non-negotiables

- No release prep until the matrix is filled, the decisions are made,
  and Julia live-confirms the matrix.
- No retarget / rescue workarounds to compensate for state-law gaps.
- No broad solidity / shape lies. Compound `dy=-1.0` must remain a named,
  documented state.
- No more single-case patches until the contract decision table above
  has explicit "Intended" answers from the design owner.
- Julia live retest remains the final gate. Automation GREEN does not
  unblock release.
- `release/0.2.0-beta.4` stays where it is. Do not move, delete, or
  reassign.

## Matrix proof status (added at `effd6ee`)

A comprehensive opt-in RED proof matrix for the compound `dy=-1.0` lane is
now available. It does not implement gameplay fixes; it enumerates the
current observable behavior of the canonical compound topology
(`STONE` over a `persistentLoweredBottomSlabCarrier` over an anchored
`STONE` over a vanilla bottom slab) across the legal-state decision table
above so a design choice between A / B / C / D can be made on evidence.

- File: `src/gametest/java/com/slabbed/test/SlabbedLabBeta4CompoundContractMatrixClientGameTest.java`
- Property: `-Dslabbed.beta4CompoundContractMatrixRedOnly=true`
- Per-row marker: `[BETA4_COMPOUND_CONTRACT_MATRIX] row=<NN_NAME> ...`
- Final markers:
  - `[BETA4_COMPOUND_CONTRACT_MATRIX_RED]` — at least one row is RED,
    UNDECIDED, or NOT_IMPLEMENTED. Expected current state.
  - `[BETA4_COMPOUND_CONTRACT_MATRIX_GREEN]` — every row GREEN. Do not
    emit until the design table is filled in and every row is decided
    plus implemented.
- Matrix is a no-op when the property is not set. Default
  `runClientGameTest` is unaffected.
- Evidence harvest at `effd6ee`:
  `tmp/beta4-compound-contract-matrix-effd6ee/`.

### Row summary (observed at `effd6ee`, intended by A-prime)

| Row | Name | Observed classification | Live status | Intended beta4 outcome |
| --- | ---- | ----------------------- | ----------- | ---------------------- |
| 1 | `SELECT_EMPTY_HAND_COMPOUND_BODY` | GREEN | automation-only | Compound block owns selection. |
| 2 | `SELECT_STONE_HELD_COMPOUND_BODY` | GREEN | automation-only | Compound block owns selection. |
| 3 | `SELECT_SLAB_HELD_COMPOUND_BODY` | GREEN | automation-only | Compound block owns unless a legal slab-placement face exists; no redirect to beta4-illegal `dy=-1.0` slab placement. |
| 4 | `PLACE_STONE_SIDE_LOWER_HALF` | GREEN | live-confirmed-fail | Packet validity is fixed and downstream side-lane authoring now marks the placed ordinary stone with the compound sidecar at `dy=-1.0`. |
| 5 | `PLACE_STONE_SIDE_UPPER_HALF` | GREEN | live-confirmed-fail | Same as row 4 for full blocks; side slot is ordinary stone with compound sidecar at `dy=-1.0`; no upward/vanilla ghost placement. |
| 6 | `PLACE_SLAB_SIDE_LOWER_HALF` | GREEN | live-confirmed-fail | Clean reject/pass: side slot stays air immediately and after tick; no `dy=-1.0` or ghost `dy=-0.5` slab lane. |
| 7 | `PLACE_SLAB_SIDE_UPPER_HALF` | GREEN | live-confirmed-fail | Clean reject/pass: side slot stays air immediately and after tick; no `dy=-1.0` or ghost `dy=-0.5` slab lane. |
| 8 | `PLACE_BLOCK_ON_TOP` | GREEN | not-yet-live-tested | Place ordinary full block above in same compound lane `dy=-1.0`; do not create `dy=-1.5`. |
| 9 | `SOURCE_SLAB_BREAK` | GREEN (post-sidecar) | live-confirmed-fail (pre-sidecar) | Persistent compound anchor preserves authored `dy=-1.0`; no silent jump to `dy=-0.5`. Sidecar `COMPOUND_FULL_BLOCK_ANCHOR_TYPE` flipped this from RED to GREEN; live retest pending. |
| 10 | `NEIGHBOR_UPDATE_AFTER_SOURCE_BREAK` | GREEN (post-sidecar) | not-yet-live-tested | Same as row 9. Sidecar flipped this from RED to GREEN; live retest pending. |
| 11 | `SAVE_RELOAD_AFTER_COMPOUND` | GREEN | live-confirmed-fail | Preserve authored `dy=-1.0`; should remain green, with live still final. |
| 12 | `CHUNK_UNLOAD_RELOAD_IF_HELPER_EXISTS` | NOT_IMPLEMENTED | not-yet-live-tested | Still not implemented in gametest; live remains final. |

Final marker emitted at `effd6ee`:
`[BETA4_COMPOUND_CONTRACT_MATRIX_RED] rows=12 red=4 undecided=4 green=3 notImplemented=1`.

After the beta4 compound full-block anchor sidecar
(`COMPOUND_FULL_BLOCK_ANCHOR_TYPE` on `SlabAnchorAttachment`) lands:
`[BETA4_COMPOUND_CONTRACT_MATRIX_RED] rows=12 red=2 undecided=4 green=5 notImplemented=1`.
Rows 9 (`SOURCE_SLAB_BREAK`) and 10 (`NEIGHBOR_UPDATE_AFTER_SOURCE_BREAK`)
flipped from RED to GREEN; the row classifiers in
`SlabbedLabBeta4CompoundContractMatrixClientGameTest` were updated to
emit GREEN when post-break compound `dy` stays at `-1.0` (per A-prime in
`docs/beta4-compound-source-mode-design.md`). Rows 4/6 stay RED
(side-half placement is the next slice). Rows 3/5/7/8 stay UNDECIDED
pending Julia intent. Row 11 stays GREEN (automation only, live still
final). Row 12 stays NOT_IMPLEMENTED. Live retest remains the final
gate; the matrix flip is automation-only evidence.

After the Row 4 packet/hit-validity bridge lands:
`[BETA4_COMPOUND_ROW4_HIT_VALIDITY_GREEN]` emits with
`reason=server_hit_validity_bridge_accepted_compound_visual_hit` and
`finalizationServer=observed_after_packet_acceptance`. The full matrix now
reports `[BETA4_COMPOUND_CONTRACT_MATRIX_RED] rows=12 red=1 undecided=5 green=5
notImplemented=1`. Row 4 moved from RED to UNDECIDED because the packet reaches
server placement and the side slot survives, but it lands as ordinary anchored
`dy=-0.5`; downstream Row 4 finalization/authoring is not complete. Row 6 stays
RED, so the beta4-illegal slab-side `dy=-1.0` lane was not legalized.

After the Row 4 lane-authoring fix lands:
`[BETA4_COMPOUND_CONTRACT_MATRIX_RED]` reports `rows=12 red=1 undecided=4 green=6
notImplemented=1`. Row 4 is GREEN because ordinary stone side placement from
the compound source inherits the compound full-block sidecar and stays
`dy=-1.0`; Row 6 stays RED and Row 7 stays UNDECIDED, so beta4 slab-side
compound lanes remain illegal.

After the Row 6 clean-reject fix lands:
`[BETA4_COMPOUND_CONTRACT_MATRIX_RED]` reports `rows=12 red=0 undecided=4 green=7
notImplemented=1`. Row 6 is GREEN because slab-held lower-half side placement
on the compound source returns `Pass[]` with `accepted=false`, `cleanReject=true`,
the side slot air immediately and after tick, and the support still
`compoundFullBlockAnchor=true` at `dy=-1.0`. This does not legalize a `dy=-1.0`
slab lane or the upper-half Row 7 path.

After the compound matrix closure lands:
`[BETA4_COMPOUND_CONTRACT_MATRIX_RED]` reports `rows=12 red=0 undecided=0 green=11
notImplemented=1`. Rows 3/5/7/8 are GREEN under A-prime: slab-held selection
keeps the compound owner, upper-half ordinary stone side placement stays in the
compound `dy=-1.0` lane, slab-held upper-half side placement cleanly rejects, and
top-of-compound ordinary stone placement is authored in the same `dy=-1.0` lane.
Row 12 remains NOT_IMPLEMENTED because the chunk-only unload/reload helper is
still unavailable in the current client gametest API. This closure does not
legalize any beta4 `dy=-1.0` slab lane or any `dy<-1.0` recursion.

The focused compound slab merge/remap proof slice now lives in
`src/gametest/java/com/slabbed/test/SlabbedLabLoweredSidePlacementLiveReproClientGameTest.java`
and emits `[JULIA_BETA4_COMPOUND_SLAB_NO_LEGAL_LANE_GREEN]`,
`[JULIA_BETA4_COMPOUND_SLAB_LEGAL_REMAP_PENDING]`, and
`[JULIA_BETA4_COMPOUND_SLAB_DOUBLE_MERGE_PENDING]`. Those markers keep the
current clean-reject behavior explicit while documenting that a legal `dy=-0.5`
remap path is still product-feel work, not a generalized rescue/retarget fix.

### Compound matrix closure

- **Row 3** `SELECT_SLAB_HELD_COMPOUND_BODY`: GREEN; slab-held selection keeps
  the compound full block selected because there is no legal beta4 slab lane.
- **Row 4** `PLACE_STONE_SIDE_LOWER_HALF`: GREEN after lane authoring; the
  packet/hit-validity bridge accepts the visual lower-half WEST-face hit
  `(8.0, 202.25, 8.5)` and the side slot survives as compound `dy=-1.0`.
- **Row 5** `PLACE_STONE_SIDE_UPPER_HALF`: GREEN; the upper-half WEST-face
  ordinary stone placement uses the same compound sidecar lane at `dy=-1.0`.
- **Row 6** `PLACE_SLAB_SIDE_LOWER_HALF`: GREEN after clean rejection; aiming
  the visual lower half of the compound's EAST face with slab-held leaves the
  side slot air immediately and after tick, with no slab lane authored.
- **Row 7** `PLACE_SLAB_SIDE_UPPER_HALF`: GREEN after clean rejection; aiming
  the visual upper half of the compound's EAST face with slab-held also leaves
  the side slot air immediately and after tick, with no slab lane authored.
- **Row 8** `PLACE_BLOCK_ON_TOP`: GREEN after top-of-compound authoring; the
  top ordinary stone stays in the compound sidecar lane at `dy=-1.0`, with no
  `dy=-1.5`.

### Rows formerly RED, now GREEN after sidecar

- **Row 9** `SOURCE_SLAB_BREAK`: with the
  `COMPOUND_FULL_BLOCK_ANCHOR_TYPE` sidecar, breaking the lower
  `persistentLoweredBottomSlabCarrier` no longer collapses the compound
  block. Authored `dy=-1.0` is preserved; the matrix classifies GREEN
  (`post-break` compound `dy=-1.0`). Live retest still pending.
- **Row 10** `NEIGHBOR_UPDATE_AFTER_SOURCE_BREAK`: the explicit
  `world.updateNeighborsAlways` pulse after source-break also no longer
  collapses the compound block; the sidecar fast-path in
  `SlabSupport.getYOffsetInner` returns `dy=-1.0` directly. Matrix
  classifies GREEN. Live retest still pending.

### Rows classified UNDECIDED

None after compound matrix closure. Row 12 remains NOT_IMPLEMENTED, not
UNDECIDED.

### Rows classified GREEN

- **Row 1** `SELECT_EMPTY_HAND_COMPOUND_BODY`: outline / raycast /
  selected target all own the compound BlockPos (matches the existing
  `BETA4_COMPOUND_LOWERED_FULL_BLOCK_TRIAD` proof).
- **Row 2** `SELECT_STONE_HELD_COMPOUND_BODY`: same triad ownership while
  holding `minecraft:stone`. The slab-held retarget guard does not
  engage for non-slab held items.
- **Row 11** `SAVE_RELOAD_AFTER_COMPOUND`: in this clean automation
  harness, `TestSingleplayerContext.getWorldSave().open()` reload
  preserves both the compound `dy=-1.0` and the carrier slab's
  `persistentLoweredBottomSlabCarrier` truth. **This is automation-only
  evidence**; Julia's live observation of compound jumping after reload
  remains live-confirmed-fail. The matrix records the harness disagreement
  rather than declaring the issue resolved. The audit row 10 hazard
  ("compound dy depends on slab carrier still being persisted") is not
  closed by this row alone.

### Rows NOT_IMPLEMENTED

- **Row 12** `CHUNK_UNLOAD_RELOAD_IF_HELPER_EXISTS`: the current Fabric
  client gametest API (`fabric-client-gametest-api-v1` `4.3.5`) does not
  expose a chunk-only unload/reload primitive. `ChainSurvivalReproTest`
  documents the same caveat. Recorded as helper-absent.

## Recommendation

Do not implement gameplay fixes until Julia decides the intended outcome
for each UNDECIDED row (3, 5, 7, 8) and the design owner picks among
A / B / C / D for the RED rows (4, 6, 9, 10). The matrix is the contract
surface; it is the input to the design decision, not the output. Release
remains blocked.

## Cross-references

- `docs/beta4-compound-lowered-fullblock-height.md` — height fix,
  collapse / placement-popoff proofs.
- `docs/beta4-reload-jump-persistence-audit.md` — prior reload jump
  classifier work.
- `docs/beta4-live-placement-authoring-proof-gap.md` — why placement
  authoring needed a recorder-first path.
- `docs/beta4-seam-ownership-contract.md` — current seam ownership
  contract; not changed by this audit.
- `docs/beta4-compound-source-mode-design.md` — A-prime decision and the
  authored-depth sidecar plan; "Authored compound anchor depth RED proof"
  section captures the row 9/10 structural argument as
  `[BETA4_AUTHORED_COMPOUND_ANCHOR_DEPTH_RED]` at `84bbb81`.
- Live evidence harvest:
  `tmp/beta4-compound-live-fail-contract-audit-06724fb/`.
- Matrix evidence harvest at `effd6ee`:
  `tmp/beta4-compound-contract-matrix-effd6ee/`.
- Authored-compound-anchor-depth RED proof evidence at `84bbb81`:
  `tmp/beta4-authored-compound-anchor-depth-red-84bbb81/`.
