# Frozen-dy restoration plan — making the code obey the WYSIWYG law

**Status: INVESTIGATION / PLAN ONLY. No code changed. Awaiting Maintainer's review of the vision.**

## The law (already documented, already the spec)

> Where I put it is where it goes and stays. dy is decided once, by where you aimed, and then it is
> frozen. A neighbor update must never change it. If I want to place something at dy 0.5, that's where
> it goes. No exceptions — except a specific vanilla gameplay mechanism.

Documented as NEVER-POP / WYSIWYG (RULES.md §9, §18; CHANGELOG "NEVER-POP freeze"; ported in commit
`2845ba81` "NEVER-POP freeze-on-place law").

## Where the code stopped obeying it

Commit `472c7b70` ("stop full-block side-inheritance; **geometric cantilever merge**"), five commits
after the law was ported, **deleted** the frozen side-lowering system and replaced it with *"geometric,
propagating cantilever lowering ('the consistent merge')."* Its own justification for ripping out the
frozen path was:

> "(b) went stale after the source was removed (**anchors never recompute**)"

That "stale anchor" **was the law working** — a placed block keeping its height after a neighbor is
removed is exactly "where I put it stays." The commit mistook the law for a bug and engineered it out
in the name of visual continuity. (The mod was born geometric at commit #1; the freeze law was a late
veneer at #421; #426 sided with geometry. It rode forward from the 1.21.1 line via `83afed84`/`9a24670c`.)

## What the code actually does today (verified, three read-only investigations, HEAD 0e9cb681)

Three facts, each confirmed against source:

1. **The attachment layer is FLAGS, not VALUES.** All 8 attachment types are `LongOpenHashSet` — a set
   of positions. None stores a dy number. A marker's *identity* implies a dy that the reader
   reconstitutes (`COMPOUND_FULL_BLOCK_ANCHOR` "means" −1.0 only because the reader returns −1.0 when it
   sees the flag). `SlabAnchorAttachment.java`, all types.

2. **The lifecycle is already frozen-correct.** Every marker is written ONLY at placement
   (`setPlacedBy` → `freezeLoweredOnPlace`; the `place`-RETURN write-belt) and removed ONLY when the
   marked block itself leaves its cell (`affectNeighborsAfterRemoval` → `removeAnchor`). **No
   neighbor-update / tick / block-update path ever writes, updates, or removes a marker.** So the flags
   themselves don't churn.

3. **The dy NUMBER is recomputed from neighbors on every read.** `freezeLoweredOnPlace` computes the
   aimed height (`double dy = SlabSupport.getYOffset(...)`, line 300) and **throws it away** — it stores
   only a presence flag. Every later `getYOffset` re-derives the magnitude by walking current neighbors.
   The anchored-slab branch even says so in-code: *"the freeze-on-place anchor only records PRESENCE, so
   the magnitude is read live from the current neighbour"* (`SlabSupport.java:2446`) — and the branch
   labeled *"reads its anchor and never recomputes"* calls `adjacentLoweredSideMagnitude(...)` (a live
   neighbor walk) two lines later.

**So the anchor is a NEVER-POP *latch*, not a stored height.** It guarantees a block stays *some* kind
of lowered (won't pop to 0), but the actual number (−0.5 / −1.0 / −1.5) is re-guessed from whatever is
currently around it. That re-guess is the law violation, and it's why neighbor edits move your blocks.

## The lane map — every height decision in `getYOffsetInner` (lines 2393–2840)

**FROZEN → constant (already law-compliant — flag in, fixed number out):**
- Compound-visible ×4 (slab) → −1.0 (rows 3)
- Frozen-flat (slab & non-slab) → 0.0 (rows 5, 12)
- Persistent bottom carrier → −0.5 (row 6)
- Anchored fallback → −0.5 (row 18)

**FROZEN-gated but GEOMETRIC magnitude (VIOLATION — has a flag, still recomputes the number):**
- Anchored slab, air-below → `adjacentLoweredSideMagnitude` neighbor walk (row 4)
- Anchored stacked connector → `beta35FenceWallVariantContactDy` support read (row 13)
- Anchored compound-FB sidecar → `getYOffsetInner(below) − 0.5` recursion (row 14)
- Anchored floor-contact ×9 (torch/gate/candle/pot/button/trapdoor/door/sign/special) → support reads (row 15)
- Anchored compound-below / cantilever-connector → below+lane / BFS (rows 16, 17)

**Pure GEOMETRIC (VIOLATION — no flag, fully neighbor-derived):**
- Ceiling-hung dispatch + the two ceiling walks (rows 1, 27, 28, 29)
- TS direct-surface lowering (row 2)
- RC2-A cantilever slab (row 7)
- slab-on-slab carrier / carrier-below / adjacent-side (rows 9, 10, 11)
- cantilever full-block / connector / block-entity (rows 19, 20, 21)
- floor-contact ×10 unanchored (row 22)
- shouldOffset compound / column / fallback (rows 23, 24, 25)

Any block that reaches a VIOLATION lane moves when a neighbor changes. That is the bulk of the ~30
lanes, and it matches the live recorder: hundreds of gaps/interpenetrations, ~half of placements
logging a height split.

## The fix — one principle

**Store the height as a NUMBER at placement; return that number verbatim; never recompute.**

Concretely:
1. **Capture the value.** `freezeLoweredOnPlace` already computes the aimed `dy` at placement. Instead of
   discarding it, store the actual `double` in a new position→double attachment (e.g. `PLACEMENT_DY`).
   The placement-intent remap (which already interprets "which cell / which half / which lane" from the
   aim) is the *legitimate* one-time geometry consult — its result becomes the stored number.
2. **Read the value.** `getYOffset(pos)` = `storedDy(pos)` if present, else `0.0`. That's the whole read
   path.
3. **Demote the geometry.** Every VIOLATION lane above moves to *placement-time only* (it helps compute
   the number to store) and is removed from the read path. The FROZEN-constant lanes collapse into the
   value store (the constant just becomes the stored number).

This reuses the machinery that's already correct (placement-only writes, self-removal-only clears — fact
2 above) and changes only what's broken (store a value, not a flag). It also grants arbitrary heights —
"if I want 0.5, that's where it goes" — which the discrete flag set cannot express today.

## Migration — live-gated, reversible first step (no big swing)

- **Step 0 (additive, behind a flag, zero deletion):** store `PLACEMENT_DY` alongside today's markers,
  and add a `getYOffset` short-circuit `if (hasStoredDy(pos)) return storedDy(pos)` gated by
  `-Dslabbed.frozenDy=true` (default off). Ship a jar. You A/B it live: flag on, does "where I put it
  stays" hold? Nothing is removed; flipping the flag off restores today exactly. **This proves the whole
  model with no risk.**
- **Step 1+:** once you confirm the feel, make frozen the default and delete the VIOLATION lanes in small
  batches, each with a jar for you before the next. The geometric code that survives lives only in the
  placement-capture path.

Each step is one behavior, one jar, your veto before the next. No stacking.

## Open decisions for you (these shape the rebuild — please rule)

1. **Auto-compounding goes away.** Today, a block on a slab that's on a lowered thing auto-deepens to
   −1.0 as you build the stack. Under the law, a block sits at the height you *aimed*, and building
   around it does not move it — if you want −1.0 you aim at −1.0. Confirm you want the auto-compound /
   "consistent merge" behavior **gone** (I believe your law says yes, but it deletes a lot of
   visible behavior, so I want it explicit).

2. **Blocks with no aim = flush.** Natural/worldgen blocks and anything placed with no lowered intent get
   dy 0 (vanilla flush) and never move. Correct?

3. **Client prediction.** Freeze is server-only today; the client reads a synced mirror, so there's a
   one-tick "pop" as the value syncs. Under the value model I can have the client compute-and-cache the
   *same* value locally at placement (both sides run the same aim-interpretation), which should remove
   the one-tick pop. Want me to include that, or keep server-authoritative + sync?

4. **Genuine vanilla exceptions.** The only "block changes on neighbor update" we keep are real vanilla
   mechanics (e.g. a block that vanilla itself breaks when unsupported). Slabbed's dy stays frozen
   through waterlog / redstone / connection state changes. Agree?

5. **Scope of the very first jar.** Step 0 above (flag-gated, additive, reversible) — is that the right
   first thing to build once you've signed off the vision, or do you want the investigation extended
   anywhere first?
