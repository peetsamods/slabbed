# Slabbed — dy Specification (the correctness oracle, NeoForge 1.21.1 line)

> **See [`LAW.md`](LAW.md) — this document does not redefine the law.** LAW.md is supreme; where
> this file conflicts with it, LAW.md wins and this file is wrong.

**What this is.** Slabbed's entire visual behavior reduces to one function: given a block and
its local neighborhood, what vertical offset (`dy`) does it render / outline / raycast /
collide at? That function is a **pure function of local geometry and stored placement facts** —
so it can be *specified as a table* and *verified by enumeration*, instead of discovered by
playing the game. This file is that table, adapted from the canonical spec on the Fabric parity
line to this port's own rulings.

**Version-invariance (with ONE documented exception).** The `dy` *values* below do **not**
change between Minecraft versions or loaders — only the API glue that reads the world changes.
So this spec + its enumeration test (`DySpecificationTest`) is the **portable contract**: a port
is "correct" (for the covered domain) iff it makes the test green. **Exception — L3 (collision)
is NOT invariant:** collision is a per-port design ruling; see L3 below for this line's ruling.
Every *visual* dy row IS invariant.

**How to read a row.** `SPEC-ID | subject block | local config | required dy | law | pinning
test`. The **required dy** is the *product intent* (what it SHOULD be per the laws), not merely
"what the code returns today" — so a row that goes RED is a real finding, not a broken test.
**What the enumeration proves:** `DySpecificationTest` asserts the scalar returned by the shared
authority (`SlabSupport.getYOffset`, which the model, outline, raycast, and collision paths all
consume) — it does not independently re-invoke every application site (see L1 and Coverage).

---

## The laws (named invariants every row obeys)

- **L1 — WYSIWYG (absolute; maintainer ruling, 2026-08-16).** A block placed or authored at X
  is visually and functionally at X: model, outline, raycast, and (on this line) collision
  resolve the same `dy`, with NO stored-fact exception — unstored geometric cells (command-
  and worldgen-authored) render lowered exactly like fact-carrying cells. The consumers never
  disagree. On this line the shared-fact agreement across model, outline, raycast, collision,
  and culling is pinned by `P7VisualParityTest`; the enumeration rows additionally pin the
  shared scalar those consumers read. A violation live-found and repaired 2026-08-16: the
  vanilla composite bakers (weighted and multipart) are loader-marked dynamic, so the model
  wrap policy skipped them and left their blocks with NO dy owner on the chunk-mesh path —
  models rendered at grid while the outline lowered. The repair names those two vanilla
  composites as wrap adapters; the factless geometric phase of the P7 client proof
  (`runP7VisualProof`) pins model-vs-outline agreement on the real chunk-mesh render view.
- **L2 — Never-pop.** A block's `dy` is fixed at placement and does not change when a neighbor
  changes. "Snaps are illegal." (LAW 1 in `LAW.md`; the blocking enforcement is
  `NeighborUpdateInvarianceTest`.) On this line the stored numeric placement fact is the first
  height authority; absence of a fact selects the legacy geometric lane.
- **L3 — Collision (PER-PORT ruling, NOT a universal invariant).** **This line ships
  lowered-collision-follow** ("solid where you see it", `BlockCollisionsLoweredAboveMixin`):
  collision follows the visual `dy`. Other lines legitimately ship collision-never-offset.
  Because collision follows here, WYSIWYG extends to a quad on this line, and stair lowering is
  a closed decision rather than an open one (see the former `OPEN-STAIRS` row below).
- **L4 — Terrain Slabs owns its own offset, and authorship decides who a block belongs to**
  (maintainer ruling, 2026-08-24). Where TS positions a block itself, Slabbed adds **nothing** on
  top — double-offset is the "smoosh". That deferral is `CompatHooks.handlesObjectOffset`, it is
  relation-shaped (an on-top subject over a BOTTOM slab), and it is measured against the real
  mod. It is unchanged and must stay.
  - **As a subject, a TS block is keyed on AUTHORSHIP, not namespace.** A TS slab a player
    *placed* records a placement fact like any other slab and resolves from it. One that world
    generation laid down carries no fact and stays flush — that is L5's world-hole guard on its
    real ground, and it is why `CompatHooks.shouldSkipOffset` is consulted *below* the stored-fact
    read in the funnel rather than above it. Authorship is read from Slabbed's OWN record; TS's
    `generated` flag looks like the answer and is not (its disk and ore features rebuild slabs
    from a default state, its grass decays the flag on random tick, and a DOUBLE merge inherits
    it), so a placement fact — which only a placement transaction can write — is the discriminator.
  - **As a SUPPORT SOURCE, authorship decides too** (maintainer ruling, 2026-08-24: full support
    parity when placed). A PLACED compat slab supports occupants and followers on its real face
    exactly as a vanilla slab does — the generic face lane admits authored compat cells, and the
    named-surface direct seat is relative to the surface's own recorded height rather than a
    flush-assuming constant. A GENERATED surface still supports nothing; `FB-TS-TERRAIN` pins
    that half, and it is L5's world-hole guard.
  - **As a lowering CEILING the exclusion remains total for now** (`CH-TS` pins it), and the
    parity remainder is open by name, not by omission: the ceiling walks, the per-object
    beta35 contact lanes, and the descended-column direct seat (a follower whose column passes
    THROUGH intermediate courses to reach the named surface still gets the constant) all still
    treat every compat cell as unauthored. Each needs its own fixtures before it flips.
  - **The blanket claim this row used to make was wrong on its facts.** It said TS owns the
    offset of its own blocks. Checked against the shipped artifact: TS applies no offset to any
    slab — its only offset path is gated on an on-top predicate whose tag holds exactly one
    vanilla block, and it is client-side only. The premise was true for snow and bushes and false
    for slabs.
  - Headless-provable on this line via the classifier shim mod
    (`TerrainSlabsHeadlessClassifierTest`), which now drives real held-item placements so the
    authored and unauthored halves are pinned by separate rows.
- **L5 — Opaque full cubes never lower onto generated terrain.** Lowering generated terrain
  tears see-through "world holes"; opaque cubes on natural terrain stay flush. Placed
  (non-generated) stacks are the exception, handled by explicit placement facts and anchors.
- **L6 — A lowered carrier shares its `dy` upward.** A block resting on a lowered carrier
  inherits the carrier's `dy` so vertical stacks stay visually continuous.

---

## The decision table

`dy` is in block units. `EPS = 1e-6`. "bottom slab" = vanilla `SlabType.BOTTOM`; "TS slab" =
a Terrain Slabs surface (`terrain_slabs:*` / `terrainslabs:*`), represented headlessly by the
classifier shim's namespaced blocks.

### Full blocks (stone, dirt, log — ordinary solid cubes)

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `FB-BOTTOM` | full block | resting above a vanilla bottom slab | **−0.5** | geometric slab-in-column | `DySpecificationTest#specFbBottom` |
| `FB-FLUSH` | full block | resting on a flush full block (no slab in column) | **0.0** | — (weak control¹) | `DySpecificationTest#specFbFlush` |
| `FB-TS-TERRAIN` | full block (opaque cube) | resting on a TS bottom slab | **0.0** | TS-not-a-support² | `DySpecificationTest#specFbTsTerrain`, `TerrainSlabsHeadlessClassifierTest` |

### Thin surface layers (carpet, snow layer, powder snow)

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `TL-SLAB` | carpet | resting directly on a vanilla bottom slab | **−0.5** | seats on the support's resolved top face | `DySpecificationTest#specTlCarpet`, `LegacySupportSeatResolutionTest` |
| `TL-CARRIER` | carpet | resting on a support that itself renders lowered | **shares carrier dy** | L6 | `LegacySupportSeatResolutionTest` |
| `TL-POWDER-SNOW` | powder snow | resting on a vanilla bottom slab | **−0.5** | no-exceptions lowering law (maintainer ruling, 2026-08-06; adopted on this line 2026-08-16) | `DySpecificationTest#specTlPowderSnow` |

The pale-moss family in the donor table is post-1.21.1 content and has no subject on this line.

### Ceiling-attached blocks (the dynamic ceiling-follower family)

Two routing lanes share the flush ruling: always-hung decorations (hanging roots, spore
blossom, hanging signs) resolve through `ceilingHungDecorationDy`; vertical chains resolve
through the direct and cascading walks in `getYOffsetInner`. All three walks are gated by the
single predicate `isLoweringTopLikeCeiling`, which records the ruling of record.

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `CH-VANILLA-TOP` | hanging roots / lantern / dripstone / chain / lever / button / TOP trapdoor | under a FLUSH vanilla TOP slab | **0.0** (was +0.5) | reach-up deprecated (maintainer ruling, 2026-07-03; adopted on this line 2026-08-16) | `DySpecificationTest#specChVanillaTop`, `CeilingFlushRulingTest` (8 flush rows incl. place-and-break lifecycle) |
| `CH-LOWERED-TOP` | ceiling-attached block | under a vanilla TOP slab that itself renders LOWERED | **slabDy + 0.5** (net ≤ 0.0, merge compensation) | flush against the visible underside, never a reach-up | `DySpecificationTest#specChLoweredTop`, `CeilingFlushRulingTest` (4 control rows on the −1.0 marked rig) |
| `CH-CHAIN-COLUMN` | every member of a ceiling-bridged vertical chain column, and the hung addendum below it | column capped by a TOP/DOUBLE slab | **one shared column dy** — grid height while the cap holds the bridge flush (including the net-zero −0.5 cap), the merge value once the cap lowers past net-zero | column never splits (maintainer ruling, 2026-08-16) | `CeilingFlushRulingTest`, the chain-column coherence rows |
| `CH-TS` | hanging roots | under a TS TOP slab | **0.0** | L4 (no smoosh; a TS cap is never read as lowered) | `DySpecificationTest#specChTs` |
| `CH-FLUSH` | hanging roots | under a flush full block | **0.0** | — (weak control¹) | `DySpecificationTest#specChFlush` |

### Connecting blocks (fence, wall, pane, fence gate)

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `CB-BOTTOM` | fence | on a vanilla bottom slab | **−0.5** | geometric slab-in-column³ | `DySpecificationTest#specCbBottom` |
| `CB-NEVERPOP` | fence | placed −0.5, then support changes | **−0.5** (unchanged) | L2 | `SlabbedLabFixtureTest` fence rows, `NeighborUpdateInvarianceTest` |
| `CB-STEPPED` | pane / wall / fence run | crossing a half-step boundary | **connections break at the step** | stepped runs are not continuous | `SlabbedLabFixtureTest` stepped-connection rows |

### Block entities (hopper, chest, furnace, barrel)

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `BE-BOTTOM` | hopper | on a vanilla bottom slab | **−0.5** | geometric slab-in-column³ | `DySpecificationTest#specBeBottom` |
| `BE-FLAT` | hopper | on a flush full block | **0.0** | — (weak control¹) | `DySpecificationTest#specBeFlat` |
| `BE-NEVERPOP` | block entity | placed −0.5, then column changes | **−0.5** (unchanged) | L2 | **TODO — unpinned on this line** (see Coverage) |

### Placement facts and lifecycle (this line's first height authority)

The stored numeric placement fact (`SlabPlacementHeightAttachment`, quantized to half-steps in
a signed byte) is the first authority for non-follower cells; genuine break or incompatible
replacement clears it; property changes and eligible in-place transforms preserve it. Dynamic
ceiling followers never consult their own fact — they are live followers of the support above.

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `SF-STORED` | any non-follower | cell holds a stored fact | **the stored value, verbatim** | LAW 1 | `SlabPlacementHeightResolverTest`, `SlabPlacementHeightLifecycleTest` |
| `SF-TRANSFORM` | stored cell | property change / eligible in-place transform | **fact preserved** | L2 | `SlabPlacementHeightLifecycleTest` |
| `SF-BREAK` | stored cell | genuine break or incompatible replacement | **fact cleared** | L2 | `SlabPlacementHeightLifecycleTest` |
| `SF-FOLLOWER` | dynamic ceiling follower | cell holds a stale fact | **fact ignored; follows the support** | followers are live | `SlabPlacementHeightResolverTest`, `CeilingFlushRulingTest` |
| `SF-CORRUPT` | any cell | stored byte outside the lowering-only envelope [−2.0, 0.0] | **reads as absent (legacy lane engages); writes decline it; the store is left exactly as found — a read never repairs** | no legitimate capture can produce it | `SlabPlacementHeightSchemaTest` envelope rows |

### Combined-slab stacks and the depth cap

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `CS-CAP` | deep combined-slab tower | stacked lowered | **capped at `SlabSupport.minResolvedDy()`** — −3.0 for every save | window radius still derives from the envelope, so consent-deep values stay targetable | `CombinedSlabChainingMatrixTest`, `DeepDyConsentTest`, `P6InteractionParityTest`, `SlabPlacementHeightLifecycleTest` |

**`CS-CAP` is a NAME, not a number.** The cap is `SlabSupport.minResolvedDy()`, which is
`PlacementDepthPolicy.MIN_TARGETABLE_DY` (−3.0) for every save. It may not go deeper without
moving that constant: the pick window and the collision broadphase are sized to it, so a height
past it would render and then refuse to be clicked or stood on. Both are per-cell scans on the
picking and entity-movement paths and both scale linearly with it, so depth is bought at a
continuous runtime cost — that, not any geometric wall, is what bounds the number.

**Why the cap cannot remove the gap (maintainer ruling, 2026-08-23).** A stacked slab course owes
half a block of descent to the course below it, without limit, because a slab is half a cell tall
while cells are a full block apart. The floor therefore does not decide WHETHER a tall tower
stops descending — it decides at which course, and the course after that one carries a half-block
gap. Held at −1.0 until this ruling, which stopped a slab tower at three flush courses; the floor
moved to the then-envelope, which was five, and a further ruling of the same date moved the
envelope itself to −3.0, which is seven. Note that the LAST course supports nothing: a bottom
slab's top face sits half a step below its own cell top, so seating anything on the floor course
would cost one more half step than the floor allows. Per-save consent chose between the two floors and has
nothing left to choose, so it no longer affects geometry; `capFor` ignores its argument and every
save resolves identically.

This row has moved twice, and both rulings are of record. The flush-landing ruling (maintainer
ruling, 2026-08-17) made flush the seat law and briefly made the envelope the only depth gate.
Live testing rejected the consequence (maintainer ruling, 2026-08-21): with derivation unbounded
below the floor, a mixed slab/block column descends another half block at every slab course — a
real crosshair ray lands on the visible top of a lowered course, so the admitted flush landing is
`ownerDy − 0.5`, and each placement snaps down out from under its preview; worse, once the value
passed −1.0 the burial exclusion bounced placements to the categorical legacy lane and the
column turned incoherent (−0.5, −1.0, −1.0, −0.5). The reference line's floor semantics are
restored: **flush decides where a course seats; the floor decides how deep derivation may carry
it; consent deepens the floor.** Pinned end-to-end by
`SlabPlacementHeightLifecycleTest.stackedSlabCoursesStopDescendingAtTheResolvedFloor`, which
places the mixed column through real visual-plane hits and holds every course at the floor.

**Open row — the resolve budget follows the floor, and consent no longer selects it.**
`SlabSupport.minResolvedDy()` is the sole input to `supportResolveDepthLimit()`, which is
derived (`ceil(|floor| / 0.5) + 2`, so 8 courses at −3.0) rather than compared against a
constant — comparing would SHORTEN the walk the moment the floor and that constant became
equal, which is backwards: a deeper floor needs a longer walk. That is a course-count budget,
not a dy floor, and it bounds how far a stack can inherit its support's height before the
resolver stops walking. Since `capFor` ignores its argument, every save gets the same budget;
the consent attachment persists and syncs but selects no geometry. Whether a straight stack
should spend that budget at all is an open question — a support that merely passes depth along
lowers nothing — and it is the same mechanism the Fabric line's tall-stack fix addresses.

**Footnotes — honest test caveats:**

- **¹ weak control.** These rows expect `0.0`, the trivial default. They pass even if the
  offset logic is a no-op, so they only have force *in contrast* to their non-zero siblings.
- **² TS-not-a-support, not the world-hole guard.** `FB-TS-TERRAIN` is flush because a TS
  surface is excluded from support semantics (`shouldSkipSlabSupport`), not because of L5.
  A dedicated L5 pin remains a TODO on this line as on the donor.
- **³ geometric lane, not the placement lane.** `DySpecificationTest` builds fixtures with
  `setBlock`, which stores **no placement fact**. The `*-BOTTOM` rows exercise the geometric
  read; the placement lane is proven by the real held-item tests
  (`NeighborUpdateInvarianceTest`, `SlabbedLabFixtureTest` useOn rows,
  `SlabPlacementHeightLifecycleTest`).

---

## Known-open / divergent rows (spec says X, an implementation currently does Y)

These are tracked deliberately — the spec is the oracle; the gap is the finding.

| SPEC-ID | case | spec intent | current on this line | status |
|---|---|---|---|---|
| `OPEN-MINUS1` | side-click a −1.0-lowered slab | EXTEND (stay −1.0) | **measured 2026-09-01: grid landing at 0.0** — the WYSIWYG follow gate arms only at exactly −0.5, and the intent path is byte-identical to the donor, so the gap is cross-line (`OpenMinus1MeasurementTest` pins the measured value) | measured; closing the gap widens an owner-tolerance gate and awaits a maintainer ruling |
| `OPEN-STAIRS` | stairs visual lowering vs collision | donor left the choice open | **closed on this line**: collision follows the visual stepable height (`SlabbedLabFixtureTest` stair collision row) | closed by the L3 collision-follow ruling |

---

## Coverage: what is headless-provable vs live-only

**Headless-provable:** every row whose `test` column names a class runs under the canonical
gate — a clean `build/run/gameTest` directory, `./gradlew build runGameTest`, the reported
count matching `tools/expected-gametest-count.py`, and the release allowlists. Registration is
closed-world: a test class must appear in the `gametest` source-set include list in
`build.gradle`, in `tools/gametest-inventory.json`, and in the count script's registered set —
the count gate is what catches a stale run directory or an unregistered class silently
dropping tests. Terrain Slabs rows are headless because the classifier shim mod claims the
real mod id inside the GameTest server and registers namespaced blocks, so the actual
`TerrainSlabsCompat` classification runs, not a stub.

**What the application-site pins actually prove.** L1 names four consumers — model, outline,
raycast, collision. Three were measured on 2026-08-20 by disabling each injection in turn on a
clean tree and counting required failures. Read the counts as **blast radius, not coverage**:
they say how much of the suite depends on a site, not how well that site is pinned.

| consumer | injection | required failures when unwired |
|---|---|---|
| outline | `SlabSupportStateMixin.slabbed$offsetOutline` (`getShape`) | 23 |
| raycast / interaction | `SlabSupportStateMixin.slabbed$offsetRaycast` (`getInteractionShape`) | 2 |
| collision broadphase | `BlockCollisionsLoweredAboveMixin.slabbed$resolveVisualCellCollision` | 2 |
| **model / render** | `OffsetBlockStateModel` | **not headlessly measurable — client-only** |

Three caveats that the counts alone hide, all of them load-bearing:

- **The model site cannot be measured by any `serverGameTest` run.** An unwired model owner
  would leave all 215 headless tests green. `P7VisualParityTest` asserts `ClientDy.dyFor(...)`,
  which is the shared scalar behind a facade, not the model application site. This is exactly
  the consumer the sibling line's coverage note is about, so on that consumer this line's
  evidence has the same shape as the sibling's — the physical-client proof lanes are the only
  route to it.
- **Raycast's low count is masking, not thin testing.** `getInteractionShape` is empty for most
  blocks, and moving an empty shape yields an empty shape, after which the crosshair falls back
  to the outline the other injection is still offsetting. The site is structurally unable to
  fail visibly for most subjects. `SlabbedLabFixtureTest.outlineRaycastParity` uses a composter
  precisely because its interaction shape is a non-empty full cube.
- **The named absolute anchor does not cover raycast.**
  `P7VisualParityTest.numericFactOwnsVisualInteractionCollisionAndCull` did **not** fail under
  the raycast mutant, despite its name: its interaction leg calls the mod's own
  `SlabbedOffsetRaycast` and asserts only the returned block position, never the hit height and
  never `state.getInteractionShape(...)`, on a subject whose interaction shape is empty. Treat
  that leg as vacuous with respect to the raycast application site.

What this does settle: an unwired **outline** or **collision** site turns the suite red on this
line, so the enumeration is not the only thing standing behind those two.

**The record's production owners are pinned by call site.**
`tools/verify-placement-store-writers.py` (wired into `check`) covers every production route
that reaches the stored heights — the two mutators, the public removal wrapper, the attachment
type itself (which hands out the backing map by reference), and the client render lookup — in
qualified, static-import and method-reference form, counted per call site so a second call
inside an already-approved file still trips it. It refuses a source carrying a raw Java unicode
escape and self-checks that the shared lexer still blanks comments and literals. It cannot see a
brand-new mutator added to the owning class itself, or a reflective call; the docstring says so.

**A fixture/shipped divergence that is not a system property.** The `serverGameTest` JVM sets no
product-behaviour flag, but the run loads the Terrain Slabs classifier shim, which claims the
real mod id. `CompatHooks.shouldSkipOffset` branches on that, and it is consulted inside the
eligibility predicate of the single approved writer — so the capture path takes a different
branch under the suite than in a vanilla install. Absence of a flag is not absence of a
divergence.

**The render-region bound is live, and measured.** A chunk render region is a fixed array over a
bounded box, so a resolver walk that leaves it throws rather than falling back to the level — on a
mesh worker, mid-frame. `getYOffsetGuarded` and `isFlushCeilingBridgedVerticalChain` bound that
escape and decline to flush; everywhere else the exception is a real defect and is rethrown.

The bound depends on a client-installed predicate recognising the view, so the predicate was
verified against a real client rather than assumed: the renderer hands the model path a
`net.minecraft.client.renderer.chunk.RenderChunkRegion` and the predicate matches it (traced
2026-08-20 during a p7 section compile). A third-party renderer supplying its own region type does
not match — there the escape rethrows exactly as before the guard existed, and the view type is
named once in the log so widening the predicate is a one-line change rather than an investigation.

Note that the p7 lane's own `bounded=true` flag does NOT exercise this: `boundedViewCheck` installs
a lookup that returns a fact and drives JDK proxy view types, so it pins the model path's
resilience and the pre-existing method-level catches instead.

**A mod id claim is not a behaviour claim.** The headless classifier shim claims the real Terrain
Slabs mod id so the actual classification runs rather than a stub — which is what makes the TS rows
above meaningful. But it ships none of TS's *behaviour*, so any compat rule that DEFERS to TS must
key on TS's data being loaded (its `on_top_blocks` tag), not on `isLoaded()`. Deferring on the id
alone drops Slabbed's own handling under the shim with nothing to replace it; that turned the
double-plant row RED when the deferral was first written. Classification may trust the id;
deferral may not.

**Known coverage gaps on this line (headless, still TODO):**
- **Never-pop breadth.** The donor pins block entities, walls, torches, fence gates, doors,
  and beds against neighbor updates; this line pins two invariance rows plus the fence/carpet
  family. The `BE-NEVERPOP` row above is carried by intent, not by a pin.
- **L5 world-hole guard** has no dedicated pin (footnote ²).
- **Combine-vs-extend on the up-face edge click** (donor's guard family) is unported.
- **Cull matrix breadth** — one control/discriminator pair here vs the donor's 13 rows.
- **The −0.5 net-zero ceiling compensation** is pinned only via the chain-column rows; a
  non-chain subject row would strengthen it.

**Still live-only (irreducible):**
- Rendered *pixel* alignment (the classification/scalar is proven; the final raster is not).
- Particle emit positions (client render thread).
- Sub-frame client placement prediction (the one-frame transient).
- Real Terrain Slabs *interaction* nuances beyond classification (the shim is a namespaced
  slab, not the full mod) — the isolated real-mod client proof covers geometry; subjective
  acceptance stays live.

When a fix closes one of these, add its row, pin it, and move it out of this list.

---

## Using this for a new port

1. Copy `DY_SPEC.md` + `DySpecificationTest` (adjust only the loader/API glue) onto the port.
2. Run the canonical gate. Every RED row is a spec violation — the exact list of what to fix,
   before any live testing.
3. Re-rule the per-port items explicitly: L3 collision, `OPEN-POWDER-SNOW`, and the pick-window
   radius measurement behind `CS-CAP`. Do not inherit them silently.
4. Only then do a live pass — and only for the live-only rows above.

_If you find yourself wanting to change a `dy` value "for this version", stop — either it is a
real product decision (update the spec on every line, with a dated maintainer ruling) or it is
a port bug (fix the port, not the spec)._
