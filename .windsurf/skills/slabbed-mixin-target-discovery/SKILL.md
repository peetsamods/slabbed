---
name: slabbed-mixin-target-discovery
description: Identify and lock correct Yarn targets for slab-top placement and survival before writing mixins.
---

# Slabbed — Mixin Target Discovery (MC 1.21.11 / Yarn)

## Goal
Identify and lock the correct **Yarn-named** classes/methods to hook for Slabbed’s core behavior:
- placing things “on top of a slab like ground”
- keeping them from popping off due to survival checks / neighbor updates

No behavior changes in this skill. Output is a target list + plan.

## Non-goals
- No new mixins added
- No injections performed
- No refactors

## Inputs / assumptions
- MC: 1.21.11 (Fabric)
- Yarn mappings in project already working
- Slab types: bottom slab / top slab / double slab
- Rule: treat the **top face** of a slab as “ground”


## Steps

### 0) Safety checkpoint
- `git status` must be clean. If not, stop.

### 1) Make sure sources are available
Run:
- `./gradlew genSources` (if needed)
- Confirm IDE can navigate into net.minecraft sources

### 2) Locate vanilla “support” predicates
Using IDE “Find in files”, search for these terms (in `net.minecraft` sources):
- `canPlaceAt` 
- `getPlacementState` 
- `canPlaceOn` 
- `canPlantOnTop` 
- `isSideSolidFullSquare` 
- `isFullCube` 
- `getCollisionShape` 
- `getOutlineShape` 
- `getSidesShape` 
- `getStateForNeighborUpdate` 

For each promising hit, open call sites and trace who checks “solid top” or “full square”.

### 3) Identify slab shape semantics
Locate the SlabBlock implementation and record:
- property names (e.g., TYPE)
- how top/bottom/double shapes are represented
- helper methods used to determine support on the upper face

Output:
- SlabBlock class name (Yarn)
- SlabType enum name (Yarn)
- Methods used to compute shape/support

### 4) Build a target matrix of “things that pop off”
Pick 6 canonical test blocks (don’t implement, just list):
- torch (floor)
- redstone dust
- repeater
- rail
- pressure plate
- carpet

For each, locate its survival predicate:
- class + method that performs support check (usually `canPlaceAt` or neighbor update path)
- what it checks on the block below (pos.down())

Record for each:
- Yarn class name
- method signature (Yarn)
- the exact condition that fails on slabs

### 5) Classify hooks into two buckets
Bucket A: **Placement-time** override points  
Bucket B: **Survival-time** override points

For each bucket, list 1–3 best candidate targets and explain why:
- stability across versions
- minimal invasiveness
- avoids breaking vanilla rules

### 6) Propose ONE primary strategy (pick one)
Choose the most robust approach and write a short plan:

Possible strategies (examples):
- (S1) Patch “support test” helper(s) that most blocks rely on
- (S2) Patch slab block to present a solid top face to support checks
- (S3) Per-block targeted mixins (last resort; strict visuals may require)

Pick one as “Primary”, and list “Fallbacks”.

### 7) Output deliverable
Produce a **Target Plan** in this exact format:

- **Primary strategy:** S?
- **Placement hooks:**
  - `YarnClass#method(signature)` — why
- **Survival hooks:**
  - `YarnClass#method(signature)` — why
- **Per-block exceptions (if any):**
  - block → target(s)
- **Risk notes:**
  - 3 bullets
- **Next step mixins to create:**
  - filenames under `src/main/java/com/slabbed/mixin/` 

### 8) Stop
Do not implement changes. Do not create mixin files. No commits.


## Pass criteria
- At least 1 credible Primary strategy with concrete Yarn targets for both placement and survival
- At least 6 block examples traced to their predicates
- Output plan is complete and actionable
