# Slabbed

**Slabbed** is a Fabric mod that allows blocks and items which normally require solid ground to treat the **top surface of slabs** as valid support.

This lets objects behave naturally on slab tops while preserving correct placement height and visuals.

## Features
- Allows placement of supported objects on slab tops
- Lanterns, torches, chains, and other supported objects lower to sit flush on slab tops
- Objects remain supported after block updates and chunk reloads
- Correct visual alignment with the slab’s actual top surface
- Optional compatibility with Countered’s Terrain Slabs (see below)
- Working on expanding slab compatibility—stairs not currently supported.

## Slab Semantics
Slabbed treats the **top face** of the following as valid ground:
- Bottom slabs (y + 0.5)
- Top slabs (y + 1.0)
- Double slabs (y + 1.0)

Other partial blocks (stairs, fences, walls, trapdoors, panes) are intentionally **not** affected.

## Compatibility
- Minecraft: **1.21.11**
- Loader: **Fabric**
- Java: **21**
- Environment: **Client + server** — install on the client for the visual lowering; works in singleplayer and on servers.

### Limitations
- Carpet and snow layers cannot coexist with slab placement in the same block space; placing a slab will replace them (vanilla behavior). Slabbed prevents ghosting by excluding thin top-layer blocks from visual offsets.
- Hanging roots follow vanilla survival rules; no special slab support yet.

### Terrain Slabs compatibility
- **Countered’s Terrain Slabs (`terrainslabs`)**: blocks, objects (lanterns, torches, chains), and vanilla slabs placed on Terrain Slabs surfaces lower to sit flush, forming continuous combined-slab surfaces. The compatibility is optional and runtime-gated — Slabbed runs unchanged when Terrain Slabs is not installed.
  - Known limitations: custom (Terrain Slabs) slabs do not yet lower when placed directly onto a vanilla slab, and deep (3+ high) combined-slab towers cap at a one-block visual offset to keep block targeting reliable.

## Installation
1. Install Fabric Loader for the target Minecraft version
2. Install Fabric API
3. Drop the Slabbed `.jar` into your `mods` folder

## Status
Slabbed is currently in **beta**.  
Behavior expands incrementally as edge cases are validated.

## License
This project is licensed under **GPL-3.0-only**.  
Source code is available at: https://github.com/joolbits/slabbed

## Lowered Side Slab Proof Bundle

The current Mac/Windows one-shot proof runners and artifact contract are documented in:
`tools/lowered-side-slab-proof-bundle.md`

Canonical proof artifacts are written under:
`build/run/clientGameTest/screenshots`

## Issues / Feedback
Bug reports and suggestions can be filed on GitHub:  
https://github.com/joolbits/slabbed/issues
