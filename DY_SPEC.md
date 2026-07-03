# Slabbed — dy Specification (the correctness oracle)

**What this is.** Slabbed's entire visual behavior reduces to one function: given a block and
its local neighborhood, what vertical offset (`dy`) does it render / outline / raycast at?
That function is a **pure function of local geometry** — so it can be *specified as a table* and
*verified by enumeration*, instead of discovered by playing the game. This file is that table.

**Why it exists.** Bug-hunting Slabbed by hand feels unscientific because the spec was implicit
(buried in ~3000 lines of branches in `SlabSupport.java`) and verification was empirical (does it
look right in-game?). Making the spec **explicit** and verification **exhaustive** turns live
testing from *discovery* into *confirmation*. Every historical fix is a row here; every port must
satisfy every row.

**Version-invariance.** The `dy` values below do **not** change between MC 1.21.1, 1.21.11, 26.1.2,
26.2, or across Fabric / NeoForge / Forge. Only the API glue that reads the world changes. So this
one spec + its enumeration test (`DySpecificationTest`) is the **portable contract**: a new port is
"correct" (for the covered domain) iff it makes the test green. See [PORT_FIX_MATRIX.md](PORT_FIX_MATRIX.md)
for per-branch conformance.

**How to read a row.** `SPEC-ID | subject block | local config | required dy | law | pinning test`.
The **required dy** is the *product intent* (what it SHOULD be per the laws), not merely "what the
code returns today" — so a row that goes RED is a real finding, not a broken test.

---

## The laws (named invariants every row obeys)

- **L1 — WYSIWYG.** A placed block renders, outlines, and raycasts at the same `dy`. The three
  never disagree. (`RULES.md` §6.)
- **L2 — Never-pop.** A block's `dy` is fixed at placement and does not change when a neighbor
  changes. "Snaps are illegal." (`RULES.md` §… never-pop law.)
- **L3 — Collision never offsets.** Movement collision stays at the vanilla grid position even when
  the visual `dy` is lowered. (Deliberate; outline/raycast follow the model, collision does not.)
- **L4 — Terrain Slabs owns its own offset.** When Terrain Slabs is present and handles a block's
  offset (`CompatHooks.shouldSkipOffset`), Slabbed adds **nothing** on top — double-offset is the
  "smoosh". A TS surface is never treated as a lowered support or a top-like ceiling.
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
| `FB-BOTTOM` | full block | resting above a vanilla bottom slab | **−0.5** | anchor on slab | `DySpecificationTest`, `SlabbedLabFixtureTest` |
| `FB-FLUSH` | full block | resting on a flush full block (no slab in column) | **0.0** | — | `DySpecificationTest` |
| `FB-TS-TERRAIN` | full block (opaque cube) | resting on a TS bottom slab | **0.0** | L5 (world-hole guard) | `DySpecificationTest`, `TerrainSlabsHotfixTest` |
| `FB-TS-CARRIER` | full block | resting on a lowered carrier (log) that sits on a TS slab | **shares carrier dy (−0.5)** | L6 | `TerrainSlabsHotfixTest` (GH #22) |

### Ceiling-hung decorations (hanging roots, spore blossom, hanging sign, pale hanging moss)

Offset decided SOLELY by the support directly ABOVE — never dragged down by a carrier below.

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `CH-VANILLA-TOP` | hanging roots | under a vanilla TOP slab | **+0.5** | raised-attach baseline | `DySpecificationTest`, `SmooshUnderTerrainSlabsTest` |
| `CH-TS` | hanging roots | under a TS TOP/DOUBLE slab | **0.0** | L4 (no smoosh) | `SmooshUnderTerrainSlabsTest` |
| `CH-FLUSH` | hanging roots | under a flush full block | **0.0** | — | `DySpecificationTest`, `SmooshUnderTerrainSlabsTest` |

### Connecting blocks (fence, wall, pane, fence gate)

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `CB-BOTTOM` | fence/wall | on a vanilla bottom slab | **−0.5** | anchor on slab | `DySpecificationTest`, `FenceNeverPopTest` |
| `CB-NEVERPOP` | fence | placed −0.5, then support slab removed | **−0.5** (unchanged) | L2 | `FenceNeverPopTest`, `NeverPopMatrixTest` |

### Block entities (hopper, chest, furnace, barrel)

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `BE-BOTTOM` | hopper | on a vanilla bottom slab | **−0.5** | anchor on slab | `DySpecificationTest`, `BlockEntityNeverPopTest` |
| `BE-FLAT` | hopper | on a flush full block | **0.0** | — | `DySpecificationTest`, `BlockEntityNeverPopTest` |
| `BE-NEVERPOP` | hopper | placed −0.5, then column below changes | **−0.5** (unchanged) | L2 | `BlockEntityNeverPopTest` |

### State-change (in-place block transform)

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `SC-TRANSFORM` | anchored block | in-place transform (grass→dirt) at same pos | **dy preserved** | L2 | `StateChangeAnchorTest` |
| `SC-BREAK` | anchored block | genuine break/replace | **anchor cleared** | L2 | `StateChangeAnchorTest` |

### Combined-slab stacks

| SPEC-ID | subject | local config | dy | law | test |
|---|---|---|---|---|---|
| `CS-CAP` | deep 3+ combined-slab tower | stacked lowered | **capped at −1.0** | load-bearing w/ ±1 pick window | `CombinedSlabChainingMatrixTest` |

---

## Known-open / divergent rows (spec says X, an implementation currently does Y)

These are tracked deliberately — the spec is the oracle; the gap is the finding.

| SPEC-ID | case | spec intent | current | where |
|---|---|---|---|---|
| `OPEN-MINUS1` | side-click a −1.0-lowered slab (vanilla TOP on TS bottom) | EXTEND (stay −1.0) | COMBINES to a double AND pops +0.5 | `UseOnMinusOneLoweredCombineVsExtendRedTest` (unregistered RED); `HANDOFF.md` Q6 |
| `OPEN-STAIRS` | stairs visual lowering vs collision | decide: collision-follow OR exclude | visual −0.5 with vanilla collision (WYSIWYG mismatch) | `HANDOFF.md` Q3 |

---

## Coverage: what is headless-provable vs live-only

**Headless-provable** (the goal — no live testing needed): every row above whose `test` column
names a `…Test` runs under `./gradlew runGameTest`. **Terrain Slabs rows included** — the
`TerrainSlabsTestShim` registers real `terrain_slabs:*` blocks so the actual `TerrainSlabsCompat`
classification runs headlessly (this is why `CH-TS` / `FB-TS-*` are provable, not live-only).

**Still live-only** (cannot yet be enumerated headlessly — the honest gaps):
- Rendered *pixel* alignment (the model shader offset) — the math is proven, the final raster is not.
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
3. Update the port's column in [PORT_FIX_MATRIX.md](PORT_FIX_MATRIX.md).
4. Only then do a live pass — and only for the live-only rows above.

_This file lives on `main` as the canonical spec. It is version-invariant by design; if you find
yourself wanting to change a `dy` value "for this version", stop — either it's a real product
decision (update the spec + every port) or it's a port bug (fix the port, not the spec)._
