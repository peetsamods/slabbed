---
name: slabbed-release-sanity-check
description: Run the minimum production sanity checks before tagging a release or milestone.
---

# Goal
Catch the common “oops” issues before a tag or release: mixin errors, debug enabled, visual regression.

# Hard stops
- Any mixin apply error
- Any baseline lane regression
- Visual alignment fails (strict visuals)

# Steps
1) Clean build gate
   - ./gradlew clean build

2) Client smoke test
   - ./gradlew runClient
   - Confirm no mixin warnings/errors in latest.log

3) Repro world quick sweep (3 lanes)
   - Full blocks lane: unchanged vanilla behavior
   - Bottom slabs lane: top-surface support still correct
   - Top slabs lane: underside support + visuals correct

4) Minimum placement checks
   - Torch/crafting table (known top-surface offset)
   - Chain + hanging sign (underside anchoring + visuals)
   - One each: dripstone, spore blossom, hanging roots, cave vines

5) Update cascade checks
   - Neighbor update: place/break adjacent
   - Break supporting slab: dependent pops
   - Chunk unload/reload: still attached correctly

6) Output report
   - PASS/FAIL per check
   - latest.log grep results for mixin errors
   - Ready-to-tag verdict
