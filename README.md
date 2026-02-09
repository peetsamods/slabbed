# Slabbed

**Slabbed** is a Fabric mod that allows blocks and items which normally require solid ground to treat the **top surface of slabs** as valid support.

This lets objects behave naturally on slab tops while preserving correct placement height and visuals.

## Features
- Allows placement of supported objects on slab tops
- Objects remain supported after block updates and chunk reloads
- Correct visual alignment with the slab’s actual top surface
- Conservative scope: only slabs, no stairs/fences/walls/etc.

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
- Environment: **Server-side** (works in singleplayer and on servers; clients do not need the mod)

## Installation
1. Install Fabric Loader for the target Minecraft version
2. Install Fabric API
3. Drop the Slabbed `.jar` into your `mods` folder

## Status
Slabbed is currently in **alpha**.  
Behavior is intentionally limited and may expand incrementally as edge cases are validated.

## License
This project is licensed under **GPL-3.0-only**.  
Source code is available at: https://github.com/joolbits/slabbed

## Issues / Feedback
Bug reports and suggestions can be filed on GitHub:  
https://github.com/joolbits/slabbed/issues
