# Slabbed

**Slabbed** is a Fabric mod that allows blocks and items which normally require solid ground to treat the **top surface of slabs** as valid support.

This lets objects behave naturally on slab tops while preserving correct placement height and visuals.

## Features
- Allows placement of supported objects on slab tops
- For now, only lanterns successfully place underneath top slab.
- Objects remain supported after block updates and chunk reloads
- Correct visual alignment with the slab’s actual top surface
- Working on expanding slab compatibility—stairs not currently supported.

## Slab Semantics
Slabbed treats the **top face** of the following as valid ground:
- Bottom slabs (y + 0.5)
- Top slabs (y + 1.0)
- Double slabs (y + 1.0)

Other partial blocks (stairs, fences, walls, trapdoors, panes) are intentionally **not** affected.

## Compatibility
- Minecraft: **1.21.1**
- Loader: **Fabric**
- Java: **21**
- Environment: **Client and server** for multiplayer; singleplayer works through the local integrated server.

### Limitations
- Carpet and snow layers cannot coexist with slab placement in the same block space; placing a slab will replace them (vanilla behavior). Slabbed prevents ghosting by excluding thin top-layer blocks from visual offsets.
- Hanging roots follow vanilla survival rules; no special slab support yet.

### Compatibility / Known incompatibilities
- **Countered’s Terrain Slabs (`terrainslabs`)**: Slabbed includes a **gated compat veto** that prevents applying **visual Y-offsets** to `terrainslabs:*` blocks to avoid see-through/ghost terrain artifacts.
  - Scope: offsets only (does not change physics/worldgen).
  - If artifacts persist, disable Terrain Slabs or accept terrain visuals may be inconsistent.

## Installation
1. Install Fabric Loader for the target Minecraft version
2. Install Fabric API
3. Drop the Slabbed `.jar` into your `mods` folder

## How placement heights work (0.5.2 and later)

Every block you place records its exact height at placement time and keeps it: breaking its
support, building next to it, reloading, or restarting changes nothing. See LAW.md — this
document does not redefine that rule.

Blocks that already exist in a world from an earlier version are left exactly as they are. They
keep the earlier behavior until you break and re-place them; nothing moves when an older world is
loaded, and there is no conversion, prompt, or backup step.

Placement reaches through three blocks of depth on a sixteenth-of-a-block grid. Item frames,
minecarts, boats, and armor stands on blocks placed in this version sit at the block's real height.

`-Dslabbed.frozenDy=true` (a JVM option) makes every block, old or new, read its stored height;
a block with no stored height then reads flat. It exists for testing, not for playing older worlds.

## Status
Slabbed is currently in **alpha**: the placement core was rebuilt around permanent heights and
verified by the automated suite and a native client proof, but it has not yet had wide play-testing.

## License
This project is licensed under **GPL-3.0-only**.  
Source code is available at: https://github.com/peetsamods/slabbed

## Lowered Side Slab Proof Bundle

The current Mac/Windows one-shot proof runners and artifact contract are documented in:
`tools/lowered-side-slab-proof-bundle.md`

Canonical proof artifacts are written under:
`build/run/clientGameTest/screenshots`

## Issues / Feedback
Bug reports and suggestions can be filed on GitHub:  
https://github.com/peetsamods/slabbed/issues
