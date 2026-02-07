---
name: slabbed-minimal-hook-implementation-playbook
description: Implement one minimal end-to-end slab support slice (placement plus survival) and tag the result.
---

# Slabbed — Minimal Hook Implementation Playbook (One Block, End-to-End)

## Goal
Implement the smallest complete vertical slice:
- ONE “object” category works on slab tops
- includes both placement and survival (no pop-off)
- strict visuals (correct height/shape)
- minimal invasiveness
- savepoint tagged

Recommended first block category: **floor torch** (small surface + simple support logic)

## Non-goals
- No “all items” yet
- No broad sweeping hooks unless proven safe
- No performance tuning beyond “don’t spam logs”

## Preconditions
- `slabbed-mixin-target-discovery` completed and provides concrete Yarn targets
- `slabbed-test-matrix-and-repro-world` exists and repro lanes are ready
- Working branch is clean
- `./gradlew build` currently passes

## Step 0) Create a working branch
- Branch: `feat/torch-on-slabs` 
- Confirm clean status

## Step 1) Add SlabSupport helper (if not already)
Implement `com.slabbed.init.SlabSupport` per `slabbed-slab-support-helper-contract`.

Do NOT add extra behavior. Keep it generic to “top support”.

## Step 2) Add ONE mixin for placement
Using the Yarn target plan:
- Create ONE mixin file (placement-time for torch)
- Hook should only widen “supported on top” when below is slab top face

Rules:
- Keep injection narrow (one method)
- Avoid redirecting huge helper methods unless necessary
- If you must intercept a predicate, do it at the closest call site for torch placement

## Step 3) Add ONE mixin for survival
Add ONE mixin that prevents torch breaking due to slab support:
- Hook the torch’s `canPlaceAt` (or equivalent survival predicate) to allow slab-top support

Ensure:
- it still breaks when truly unsupported (air, liquids, etc.)
- it still follows vanilla where appropriate

## Step 4) Strict visuals sanity
Validate that the torch visually sits on slab top:
- bottom slab: torch base at y+0.5
- top slab: torch base at y+1.0

If visual mismatch exists due to vanilla model assumptions:
- stop and report the exact mismatch
- do not hack around with unrelated rendering changes yet

## Step 5) Test the vertical slice
Run the test matrix only for the torch category:
- Full blocks lane must still pass
- Bottom slab lane must pass
- Top slab lane must pass
Run update triggers:
- neighbor update
- chunk unload/reload

## Step 6) Savepoint
If torch passes:
- `./gradlew build` 
- commit message: `feat: allow floor torches on slab tops` 
- tag: `slabbed-first-placement-pass` 

If survival also passes after updates:
- add second commit if needed: `fix: prevent torch popping off on slab updates` 
- tag: `slabbed-first-survival-pass` 


## Output report format (required)
- Strategy used (1 line)
- Yarn target(s) touched (class#method)
- Files changed
- Test result summary (torch only)
- Commit hash(es) + tag(s)

## Stop conditions
- If any mixin target is missing (“no targets”): stop and rerun discovery
- If torch works but breaks another block in baseline lane: revert to last tag and reduce scope
