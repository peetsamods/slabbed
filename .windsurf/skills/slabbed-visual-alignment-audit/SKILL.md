---
name: slabbed-visual-alignment-audit
description: Audit and enforce strict visual alignment for slab-supported placements before tagging a category pass.
---

# Slabbed — Visual Alignment Audit (Strict)

## Goal
Ensure anything that “works” on slab tops also looks correct:
- model sits on the slab top surface
- outline/selection box aligns to the surface
- hitbox/collision behavior matches expectation
Functional-but-visually-wrong is a failure.

## When to run
- After any new category passes placement + survival
- Before tagging `slabbed-<category>-pass` 
- After any change touching shapes, placement offsets, or support predicates

## Setup
Use the standard repro world lanes:
- Full blocks (baseline)
- Bottom slabs
- Top slabs

Test each target in all three lanes.

## Checklist (per target)

### A) Placement height (must match slab top)
- Bottom slab: object base should align at y + 0.5
- Top slab: object base should align at y + 1.0
- Double slab/full block: identical to vanilla

PASS only if it visually appears grounded on the surface with no floating or clipping.

### B) Outline/selection shape
Hover and verify the white outline box:
- is not hovering above the slab
- is not sunken into the slab
- is consistent across camera angles
- matches vanilla behavior on the full-block lane

If outline is wrong, treat as FAIL even if functionality works.

### C) Interaction “feel”
Verify interaction uses the expected aim point:
- placing adjacent items isn’t unusually hard
- breaking the object feels normal
- no weird “must aim at air” placements

### D) Block state transitions
Trigger common state changes and re-check visuals:
- neighbor update (place/break adjacent block)
- chunk reload (fly away and return)
- redstone toggle (if relevant)
- water adjacency (if relevant)

### E) Known offenders (extra scrutiny)
Pay extra attention to:
- redstone dust height and connection visuals
- rails shape and slope behavior
- carpets level alignment
- pressure plate height and click feel
- torches and lantern attachment points

## Evidence capture
For any FAIL:
- take 1 screenshot showing the mismatch
- note exact lane (full/bottom/top slab)
- note exact reproduction steps

## Stop conditions
- Any visual FAIL blocks a `*-pass` tag
- Do not “ship ugly” with strict visuals enabled
- If the issue requires broad rendering hacks, stop and reassess hook strategy

## Output report format
- Category tested:
- Results by lane (Full / BottomSlab / TopSlab):
- Visual failures (if any):
  - what looks wrong
  - which lane
  - screenshot name
- Suspected cause (1–2 lines):
- Next action:
