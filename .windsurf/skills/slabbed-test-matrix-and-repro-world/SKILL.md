---
name: slabbed-test-matrix-and-repro-world
description: Define and run the strict test matrix and reproducible world layout for validating slab support.
---

# Slabbed — Test Matrix + Repro World (Strict)

## Goal
Create a consistent, repeatable way to validate Slabbed:
- placement works on slab tops
- blocks do not pop off after updates
- visuals are correct (strict)

This skill produces a concrete test matrix and a simple “repro world” build plan.

## Non-goals
- No mixin code changes required
- No performance optimization here
- No broad QA; focus on slab-support behaviors

## Assumptions
- MC 1.21.11 Fabric dev run is working
- Rule: treat the **top face** of slabs as ground (bottom slab top and top slab top)

## A) Standard Repro World Setup

### 1) World settings
- Create new singleplayer world: `Slabbed QA` 
- Creative mode
- Cheats: ON
- Flat world preset (superflat) preferred for clarity
- Time: set day and lock if desired

### 2) Build the Repro Platform (coords-based)
At spawn, build a labeled platform with 3 lanes:

Lane 1: **Full blocks** baseline  
Lane 2: **Bottom slabs** (target behavior)  
Lane 3: **Top slabs** (target behavior)

Each lane should be 3 blocks wide and at least 30 blocks long.

### 3) Segment markers
Every 5 blocks, place a sign label:
- `T1 Torch` 
- `T2 Redstone Dust` 
- `T3 Repeater/Comparator` 
- `T4 Rails` 
- `T5 Pressure Plates` 
- `T6 Carpet` 
- `T7 Plants (optional)` 
- `T8 Misc (as needed)` 

## B) Test Matrix (Strict)

For each test item, run in each lane (full / bottom slab / top slab) and record PASS/FAIL.

### Common pass criteria (all)
- Places without forcing weird aim hacks
- Renders at the correct height (flush to slab top surface)
- Does not instantly break
- Survives a neighbor update and chunk reload

### Evidence capture
For every FAIL:
- Screenshot showing placement + block state context
- Save the exact reproduction steps in a note
For every PASS (first time per category):
- One screenshot

## C) Update/Stress Actions (run after placement)

After placing each item, run these update triggers:

1) **Neighbor update**
- Place and break a block adjacent (side)
- Place and break a block beneath (where applicable)

2) **Chunk unload/reload**
- Fly 200 blocks away, return

3) **Fluid interaction** (if relevant)
- Place water adjacent (if item normally interacts)

4) **Redstone tick** (redstone category)
- Power it and observe updates (lever on adjacent full block is acceptable for test)

5) **Piston test** (if relevant)
- Push a neighboring full block to cause updates (don’t require the slabbed item itself to be pushable)

## D) Output format (required)

Produce a test report in this exact structure:

- Build/commit under test: `<hash or local>` 
- World: `Slabbed QA` 
- Results:
  - Torch: Full / BottomSlab / TopSlab
  - Dust: ...
  - Repeater: ...
  - Comparator: ...
  - Rails: ...
  - Pressure plates: ...
  - Carpet: ...
- Notable failures (each):
  - What broke
  - Exact placement context
  - Which update trigger caused it
  - Screenshot filenames/paths
- Next debug targets:
  - 1–3 suspected methods/classes

## E) Stop conditions
If any category fails on slabs:
- Do not expand scope
- Pivot immediately to diagnosis using `slabbed-mixin-target-discovery` 

## Pass criteria for “Alpha Ready”
- Torch, Dust, Repeater/Comparator, Rails, Pressure Plates, Carpet:
  - PASS on bottom slabs and top slabs
  - Survive neighbor updates + chunk reload
  - Visual height is correct (strict)
