# Slabbed — dy Specification (the correctness oracle)

> **See [`LAW.md`](LAW.md) — this document does not redefine the law.** LAW.md is supreme; where this file conflicts with it, LAW.md wins and this file is wrong.

**What this is.** Slabbed's entire visual behavior reduces to one function: given a block and
its local neighborhood, what vertical offset (`dy`) does it render / outline / raycast at?
That function is a **pure function of local geometry** — so it can be *specified as a table* and
*verified by enumeration*, instead of discovered by playing the game. This file is that table.

**Why it exists.** Bug-hunting Slabbed by hand feels unscientific because the spec was implicit
(buried in ~3000 lines of branches in `SlabSupport.java`) and verification was empirical (does it
look right in-game?). Making the spec **explicit** and verification **exhaustive** turns live
testing from *discovery* into *confirmation*. Every historical fix is a row here; every port must
satisfy every row.

**Version-invariance (with ONE documented exception).** The `dy` *values* below do **not** change
between MC 1.21.1, 1.21.11, 26.1.2, 26.2, or across Fabric / NeoForge / Forge — only the API glue
that reads the world changes. So this spec + its enumeration test (`DySpecificationTest`) is the
**portable contract**: a new port is "correct" (for the covered domain) iff it makes the test green.
**Exception — L3 (collision) is NOT invariant:** the 26.1.2 port deliberately ships lowered-
collision-follow (`BlockCollisionsLoweredAboveMixin`), the opposite of main's collision-never-offset.
Collision is a per-port design ruling; do not treat a collision-follow port as non-conformant. Every
*visual* dy row IS invariant. See the internal port-fix matrix (maintainer notes), row 7.

**How to read a row.** `SPEC-ID | subject block | local config | required dy | law | pinning test`.
The **required dy** is the *product intent* (what it SHOULD be per the laws), not merely "what the
code returns today" — so a row that goes RED is a real finding, not a broken test. **What the
enumeration proves:** `DySpecificationTest` asserts the scalar returned by the shared authority
(`getYOffset`, which the model, outline, and raycast all consume) — it does NOT independently
re-invoke the three triad application sites (see L1 and the Coverage section for that gap).

---

## The laws (named invariants every row obeys)

- **L1 — WYSIWYG.** A placed block renders, outlines, and raycasts at the same `dy`. The three
  never disagree. (`LAW.md`, WYSIWYG.) NOTE: the enumeration test pins the shared `getYOffset` scalar
  the three consume; it does not yet independently assert each application site (outline/raycast
  `VoxelShape` min-Y, model mesh offset). A refactor that unwired one mixin could break the triad
  without a RED here — that narrow gap is listed under Coverage.
- **L2 — Never-pop.** A block's `dy` is fixed at placement and does not change when a neighbor
  changes. "Snaps are illegal." (LAW 1, `LAW.md`.)
- **L3 — Collision (PER-PORT ruling, NOT a universal invariant).** On main / 1.21.1 collision stays
  at the vanilla grid position while the visual `dy` lowers. On **26.1.2** collision deliberately
  FOLLOWS the visual ("solid where you see it", `BlockCollisionsLoweredAboveMixin`). Forge 1.20.1
  is an open decision. This is the one law a port may legitimately differ on.
- **L4 — Terrain Slabs owns its own offset.** When Terrain Slabs handles a block's offset
  (`CompatHooks.shouldSkipOffset`), Slabbed adds **nothing** on top — double-offset is the "smoosh".
  A TS surface is never treated as a lowered support or a top-like ceiling — for the WHOLE
  `isCeilingAttached` family (hanging roots/blossom/sign/moss AND lanterns, chains, dripstone, cave
  vines, trapdoors, bells/levers/buttons), enforced in one place by `isLoweringTopLikeCeiling`. TS
  may still SUPPORT an attachment (a lantern attaches to a TS underside); only its dy stays flush.
- **L5 — Opaque full cubes never lower onto a slab surface.** Lowering generated terrain tears
  see-through "world holes" (DODOs); opaque full cubes stay flush. Placed (non-generated) stacks
  are the exception, handled by explicit anchors.
- **L6 — A lowered carrier shares its `dy` upward.** A block resting on a lowered carrier (log,
  full block) inherits the carrier's `dy` so vertical stacks stay visually continuous (no seam).

---

## The decision table

`dy` is in block units. `EPS = 1e-6`. "bottom slab" = vanilla `SlabType.BOTTOM`; "TS slab" =
a Terrain Slabs surface (`terrain_slabs:*` / `terrainslabs:*`).

### Full blocks (stone, dirt, log, jukebox, composter — ordinary solid cubes)

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `FB-BOTTOM` | full block | resting above a vanilla bottom slab | **−0.5** | geometric slab-in-column | `DySpecificationTest` (geometric lane), `SlabbedLabFixtureTest` |
| `FB-FLUSH` | full block | resting on a flush full block (no slab in column) | **0.0** | — (weak control¹) | `DySpecificationTest` |
| `FB-TS-TERRAIN` | full block (opaque cube) | resting on a TS bottom slab | **0.0** | TS-not-a-support² | `DySpecificationTest`, `TerrainSlabsHotfixTest` |
| `FB-TS-CARRIER` | full block | resting on a lowered carrier (log) that sits on a TS slab | **shares carrier dy (−0.5)** | L6 | `TerrainSlabsHotfixTest` (GH #22) |

### Thin surface layers (carpet, pale moss carpet, snow layer, powder snow)

These had a categorical lowering exclusion for most of the project's life — first a CLASSNAME
family (`isThinTopLayer`, `8d3f105f`), then a narrowed BEHAVIOUR predicate
(`isEnvironmentDepositedSurfaceFill`, `112d1449`). **Both are gone as of 2026-08-06** (maintainer
ruling, binding law: *"everything should be able to lower; no exceptions"*, ruled twice — carpet
first, then snow). There is no thin-layer term in any dy lane now; these rows are pure geometry, and they
exist mainly so a future port does not re-derive the exclusion from the old commits.

The snowy-terrain hazard the exclusion protected is carried by **L5** instead: the column walks
stop on the first opaque full cube below the subject, so weather snow on grass/dirt/stone cannot
see a slab buried under that terrain at all.

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `TL-SLAB` | carpet / pale moss carpet / snow layer / powder snow | resting directly on a vanilla bottom slab | **−0.5** | geometric slab-in-column | `ThinTopLayerLoweringTest` |
| `TL-CARRIER` | carpet / pale moss carpet / snow layer | resting on a full block that itself renders lowered | **shares carrier dy** | L6 | `ThinTopLayerLoweringTest` |
| `TL-TS` | carpet / snow layer | resting on a TS `BOTTOM_LIKE` surface | **−0.5** | seats on the half-height top face | `ThinTopLayerLoweringTest` |
| `TL-TERRAIN` | snow layer | resting on natural opaque terrain, with a slab buried below it | **0.0** | **L5** | `ThinTopLayerLoweringTest#snowLayerOverNaturalTerrainStaysFlush` |
| `TL-SNOWBLOCK` | `minecraft:snow_block` | any | **use the full-block rows** — it is an ordinary opaque cube, never in either predicate | L5 / L6 | `ThinTopLayerLoweringTest#snowBlockBehavesExactlyLikeAnyFullBlock` (asserts equality with stone) |

**Client note (not a dy row, but the reason `TL-*` was invisible for a session):** a port must also
check that the CLIENT consumes these numbers. On this line `ClientDy.dyFor` held a second,
anchor-blind carpet authority that the chunk model and the outline both read, so carpets rendered
flush while every row above was already correct. It is now a pure delegate to `getVisualYOffset`,
pinned by `ClientCarpetDyAuthorityTest`. Exactly ONE layer may offset a carpet's OUTLINE
(`CarpetDyShapeMixin`, client) — a standing maintainer rule (internal notes).

### Ceiling-attached blocks (the full `isCeilingAttached` family)

Two routing lanes share law L4: the "always-hung" decorations (hanging roots, spore blossom,
hanging sign, pale moss) go through `ceilingHungDecorationDy`; everything else ceiling-attached
(hanging lanterns, Y-axis chains, pointed dripstone, cave vines, top-half trapdoors,
bells/levers/buttons) goes through `getYOffsetInner`'s two ceiling walks. Both lanes are tested —
an adversarial review found the fix originally covered only the first lane, so lanterns/chains/
dripstone still smooshed while the always-hung test stayed green.

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `CH-VANILLA-TOP` | hanging roots / lantern / dripstone / chain | under a vanilla TOP slab | **0.0** (was +0.5) | reach-up DEPRECATED 2026-07-03 (maintainer live ruling — flush looked better; provisional, may regress) | `DySpecificationTest`, `SmooshUnderTerrainSlabsTest` (roots + lantern + dripstone + chain, all flush) |
| `CH-TS` | hanging roots (always-hung lane) | under a TS TOP/DOUBLE slab | **0.0** | L4 (no smoosh) | `SmooshUnderTerrainSlabsTest` |
| `CH-TS-OBJECT` | hanging lantern / Y-chain / pointed dripstone (`getYOffsetInner` lane) | under a TS TOP/DOUBLE slab | **0.0** | L4 (no smoosh) | `SmooshUnderTerrainSlabsTest` (lantern TOP+DOUBLE, chain TOP, dripstone TOP) |
| `CH-FLUSH` | hanging roots | under a flush full block | **0.0** | — (weak control¹) | `DySpecificationTest`, `SmooshUnderTerrainSlabsTest` |

### Connecting blocks (fence, wall, pane, fence gate)

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `CB-BOTTOM` | fence/wall | on a vanilla bottom slab | **−0.5** | geometric slab-in-column³ | `DySpecificationTest` (geometric lane), `FenceNeverPopTest` |
| `CB-NEVERPOP` | fence | placed −0.5, then support slab removed | **−0.5** (unchanged) | L2 (anchor lane) | `FenceNeverPopTest`, `NeverPopMatrixTest` |

### Block entities (hopper, chest, furnace, barrel)

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `BE-BOTTOM` | hopper | on a vanilla bottom slab | **−0.5** | geometric slab-in-column³ | `DySpecificationTest` (geometric lane), `BlockEntityNeverPopTest` |
| `BE-FLAT` | hopper | on a flush full block | **0.0** | — (weak control¹) | `DySpecificationTest`, `BlockEntityNeverPopTest` |
| `BE-NEVERPOP` | hopper | placed −0.5, then column below changes | **−0.5** (unchanged) | L2 (anchor lane) | `BlockEntityNeverPopTest` |

### State-change (in-place block transform)

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `SC-TRANSFORM` | anchored block | in-place transform (grass→dirt) at same pos | **dy preserved** | L2 | `StateChangeAnchorTest` |
| `SC-BREAK` | anchored block | genuine break/replace | **anchor cleared** | L2 | `StateChangeAnchorTest` |

### Anchored followers (the anchor lane's dy MAGNITUDE)

The persistent anchor is a boolean membership set, **not a stored height**. A follower that carries
one therefore resolves its dy from the support below it at read time, and it must resolve from that
support's *actual* dy — never from the *kind* of support. Live-reported 2026-08-05 (0.5 floating gap
across `birch_slab` / `birch_fence` / `lantern` / `oak_sign`); recorded in the internal live ledger.

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `AF-HALF-SEAT` | anchored follower | on a vanilla bottom slab that is itself flush | **−0.5** | half-height seat | `AnchoredFollowerSupportDyTest` |
| `AF-FULL-SEAT` | anchored follower (slab / fence / lantern / sign) | on a solid non-slab support already at −1.0 | **−1.0** (inherits the support's dy) | full-height seat | `AnchoredFollowerSupportDyTest` |
| `AF-GEOMETRIC` | un-anchored follower | same scene, no anchor anywhere | **−1.0** (identical to `AF-FULL-SEAT`) | anchor lane == geometric lane | `AnchoredFollowerSupportDyTest`, `OffsetRaycastTargetingTest` |
| `AF-CLAMP` | anchored follower | on a bottom slab that is itself already **at the cap** (raw seat `cap − 0.5`) | **`SlabSupport.minResolvedDy()`** | `CS-CAP` | `AnchoredFollowerSupportDyTest` |

### Combined-slab stacks

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `CS-CAP` | deep 3+ combined-slab tower | stacked lowered | **capped at `SlabSupport.minResolvedDy()`** — −1.0 unless the world has consented (or `slabbed.deepDyAlphabet` forces it), −2.0 when it has | cap == −(pick window radius) | `CombinedSlabChainingMatrixTest`, `ClampUnificationTest`, `AnchoredFollowerSupportDyTest` (`AF-CLAMP`), `DeepDyConsentTest` |

**`CS-CAP` is a NAME, not a number (Stage 4, 2026-08-07).** The cap is
`SlabSupport.minResolvedDy()`, and it is derived rather than chosen: a block drawn at `dy` is
clickable only if the offset-aware pick window tests the cell layers its shape occupies, and
`SlabbedOffsetRaycast` derives `WINDOW_RADIUS = ceil(-DEEPEST_TARGETABLE_DY)`. The standing identity
is `minResolvedDy() >= DEEPEST_TARGETABLE_DY`; it is an INEQUALITY with no consent (cap −1.0
against a window built for −2.0, deliberate slack so the pick-path cost ships alone) and closes to
EQUALITY once a world consents, where `minResolvedDy()` literally READS `DEEPEST_TARGETABLE_DY`. So
`minResolvedDy() == -(window radius)` is derivable, which is the maintainer's stated reason (2026-08-06) for
ruling the cap −2.0 rather than the −1.5 originally asked for: Stage 0 MEASURED the required radius
as 1 at −1.0 and 2 at BOTH −1.5 and −2.0, so −1.5 would pay the whole radius-2 cost and leave the
constant magic anyway.

**The cap is PER-WORLD CONSENT STATE, and it defaults to the shipped −1.0 (maintainer ruling,
2026-08-09).** Cells holding a stored placement height never move when it changes (LAW 1 —
`storedDy` is returned verbatim); cells without one take the live resolver and would drop half a
block on first re-render, so the deeper alphabet is never migrated in — a world has to say yes.
`com.slabbed.anchor.DeepDyConsentAttachment` holds that answer: a persistent, synchronized world
attachment whose ABSENCE means "a world older than consent", which is legacy and therefore −1.0.
`slabbed.deepDyAlphabet` survives as a developer override that forces the deep leg on regardless of
world state; it may only ever arm, never disarm. New worlds are stamped, but stamped with the
SHIPPED alphabet while the deep pass-through depth-budget defect remains pinned by
`DyWindowSuite#passThroughTowerNeverCliffsPastWhatItsSeatCanLower` and
`DyWindowSuite#passThroughTowerPastTheBudgetNeverRises`; see
`DeepDyConsentAttachment.NEW_WORLD_DEFAULT_DEEP_DY`. Measured blast radius: the 196-column
shallow battery in `ClampUnificationTest` is byte-identical in both legs, and so is
`CombinedSlabChainingMatrixTest`'s 38-row combined-slab matrix. Only columns deep enough to
saturate move — `ClampUnificationTest#deepColumnsAreTheOnlyShapesTheDeeperAlphabetMoves` pins both
legs of that.

**Footnotes — honest test caveats (surfaced by adversarial review):**

- **¹ weak control.** These rows expect `0.0`, the trivial default. They pass even if the offset
  logic is a no-op, so they only have force *in contrast* to their non-zero siblings (e.g. `FB-FLUSH`
  vs `FB-BOTTOM`). They are kept as controls, not treated as independent proofs.
- **² TS-not-a-support, not the world-hole guard.** `FB-TS-TERRAIN` is flush because a TS slab is
  excluded from slab-support semantics (`shouldSkipSlabSupport`), NOT because of the opaque-full-cube
  world-hole guard (L5). Deleting the L5 guard leaves this row green — so it does not pin L5. The L5
  opaque-cube guard is exercised separately (`isSlabSitCandidate` excludes opaque cubes); a dedicated
  L5 pin is a `TODO` (see Coverage).
- **³ geometric lane, not the anchor lane.** `DySpecificationTest` builds fixtures with
  `setBlockState`, which creates **no placement anchor**. So `FB-BOTTOM` / `CB-BOTTOM` / `BE-BOTTOM`
  exercise the *geometric* slab-in-column read, not the `SlabAnchorAttachment` lane that real
  placement uses and that the never-pop fixes changed. The anchor lane is proven by the `*-NEVERPOP`
  rows (`FenceNeverPopTest` / `BlockEntityNeverPopTest`), which place-then-break, and — since
  2026-08-05 — by the `AF-*` rows, which are the only ones that pin the anchor lane's dy
  **magnitude** (the `*-NEVERPOP` rows pin that it does not CHANGE, not that it is right). A
  never-pop/anchor regression would NOT be caught by the `*-BOTTOM` rows alone.

---

## Known-open / divergent rows (spec says X, an implementation currently does Y)

These are tracked deliberately — the spec is the oracle; the gap is the finding.

| SPEC-ID | case | spec intent | current | where |
|---|---|---|---|---|
| `OPEN-MINUS1` | side-click a −1.0-lowered slab (vanilla TOP on TS bottom) | EXTEND (stay −1.0) | COMBINES to a double AND pops +0.5 | `UseOnMinusOneLoweredCombineVsExtendRedTest` — **intentionally NOT registered** in `fabric.mod.json` (RED-cell discipline: it fails by design until the fix lands; register it then). Internal notes, Q6 |
| `OPEN-STAIRS` | stairs visual lowering vs collision | decide: collision-follow OR exclude | visual −0.5 with vanilla collision (WYSIWYG mismatch) | Internal notes, Q3 |

---

## Coverage: what is headless-provable vs live-only

**Headless-provable** (the goal — no live testing needed): every row above whose `test` column
names a `…Test` runs under `./gradlew runGameTest`. **Terrain Slabs rows included** — the
`TerrainSlabsTestShim` registers real `terrain_slabs:*` blocks so the actual `TerrainSlabsCompat`
*classification* runs headlessly (this is why `CH-TS*` / `FB-TS-*` are provable, not live-only).

**Honest scope of the enumeration.** `DySpecificationTest` pins **8** rows directly; the rest are
carried by the specialized tests named in each row's `test` column (e.g. never-pop, combine-vs-
extend, chaining-matrix). So "the suite is green" means *the pinned rows and their specialized
tests pass* — NOT that every conceivable dy is enumerated. The `DySpecificationTest` javadoc phrase
"one @GameTest per pinnable spec row" describes the *intent*, not full coverage.

**Known coverage gaps (headless, still TODO — not yet pinned):**
- **Triad application sites.** Rows assert the shared `getYOffset` scalar, not the three consumers
  independently. A `VoxelShape` min-Y assertion on `SlabSupportStateMixin.getOutlineShape` /
  `getRaycastShape` for a lowered fixture would close L1 (a mixin becoming unwired would then RED).
- **L5 world-hole guard.** `FB-TS-TERRAIN` does not pin it (footnote ²); a `isSlabSitCandidate`
  false-on-opaque-cube assertion or a non-full-cube-outline `BlockView` stub would.
- **Anchor lane on the `*-BOTTOM` rows** (footnote ³) — covered indirectly by the `*-NEVERPOP` rows.

**Still live-only** (cannot be enumerated headlessly at all — irreducible):
- Rendered *pixel* alignment (the model shader offset) — the classification/scalar is proven, the
  final raster is not. The shim proves TS is *classified* correctly; it does NOT reproduce the real
  TS neighbor-dependent render wrapper, so the flush-vs-smoosh *visual* still wants one live look.
- Particle emit positions (client render thread).
- Sub-frame client placement prediction ("snap" flash) — server dy is correct/locked; the one-frame
  client transient is irreducible.
- Real Terrain Slabs *interaction* nuances beyond classification (the shim is a namespaced slab, not
  the full TS mod).

When you add a fix that closes one of these, add its row + pin it, and move it out of this list.

---

## Using this for a new port

1. Copy `DY_SPEC.md` + `DySpecificationTest` (adjust only the MC-API glue) onto the port.
2. Run `runGameTest`. Every RED row is a spec violation — the exact list of what to fix, before
   any live testing.
3. Update the port's column in the internal port-fix matrix (maintainer notes).
4. Only then do a live pass — and only for the live-only rows above.

_This file lives on `main` as the canonical spec. It is version-invariant by design; if you find
yourself wanting to change a `dy` value "for this version", stop — either it's a real product
decision (update the spec + every port) or it's a port bug (fix the port, not the spec)._
