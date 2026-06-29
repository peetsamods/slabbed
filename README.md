![Slabbed — torches, lanterns, a bed, a crafting table, a flower pot, a door, and a chicken all sitting happily on stone slabs.](https://cdn.modrinth.com/data/cached_images/ffc6c8b69a39c52f24087f4bd01078cfe40fe231.png)

# Slabbed

> **Put things on slabs!**

[![Modrinth](https://img.shields.io/badge/Modrinth-Download-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/slabbed)
[![CurseForge](https://img.shields.io/badge/CurseForge-Download-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/slabbed)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1%20%7C%201.21.11%20%7C%2026.x-62B47A)](https://modrinth.com/mod/slabbed/versions)
[![Loader](https://img.shields.io/badge/Loader-Fabric%20%7C%20NeoForge-DBD0B4)](https://modrinth.com/mod/slabbed/versions)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue)](LICENSE)

**Slabbed** lets blocks and objects that normally need solid ground treat the **top surface of slabs** as valid support. Torches, lanterns, chains, flower pots, and many other objects sit flush on slab tops; full blocks stack on slabs; and the crosshair targets what you actually see — no floating, no ghosting, no fighting the vanilla placement rules.

> Things should sit where they look like they sit. Minecraft just makes that surprisingly complicated.

## Download

Each Minecraft version has its **own** file — grab the one tagged for your game:

- 💚 **[Modrinth](https://modrinth.com/mod/slabbed)**
- 🔶 **[CurseForge](https://www.curseforge.com/minecraft/mc-mods/slabbed)**

## Features

- Place supported objects directly on slab tops.
- Objects (lanterns, torches, chains, flower pots, …) lower to sit **flush** on the slab surface instead of hovering.
- Full blocks stack on slabs and stay where you placed them.
- **Offset-aware targeting** — the crosshair and the selection outline agree on where a lowered block actually is.
- Placements stay put across block updates and chunk reloads.
- **Optional [Countered's Terrain Slabs](https://modrinth.com/mod/countereds-terrain-slabs) compatibility** (see below).

## Slab semantics

Slabbed treats the **top face** of these as valid ground:

- Bottom slabs (`y + 0.5`)
- Top slabs (`y + 1.0`)
- Double slabs (`y + 1.0`)

Other partial blocks (stairs, fences, walls, trapdoors, panes) are intentionally **not** treated as ground.

## Compatibility

| Minecraft | Loader(s) | Java |
|:---|:---|:---|
| 1.21.1 | Fabric · NeoForge | 21 |
| 1.21.11 | Fabric | 21 |
| 26.1 / 26.1.2 / 26.2 | Fabric | 25 |

- **Environment:** client + server. Install on the client for the visual lowering and correct targeting; works in singleplayer and on servers. On multiplayer, install on both so visuals, outlines, and targeting line up.
- **Fabric** builds require **Fabric API**. **NeoForge** builds are standalone.

> Loader and file availability are listed per version on [Modrinth](https://modrinth.com/mod/slabbed/versions) and [CurseForge](https://www.curseforge.com/minecraft/mc-mods/slabbed/files) — always pick the file that matches your Minecraft version.

### Terrain Slabs compatibility

With **[Countered's Terrain Slabs](https://modrinth.com/mod/countereds-terrain-slabs)** installed, terrain slabs become first-class support:

- Blocks, objects (lanterns, torches, chains), and vanilla slabs placed on Terrain Slabs surfaces lower to sit flush, forming continuous "combined slab" surfaces.
- Completely **optional** and runtime-gated — Slabbed runs exactly the same when Terrain Slabs is not installed.

**Known limitations:**

- A *custom* (Terrain Slabs) slab placed directly **on top of** a vanilla slab doesn't lower yet. The reverse — a vanilla slab on top of a Terrain Slabs slab — works.
- Very tall (3+) combined-slab towers cap how far they lower, on purpose, to keep block targeting reliable.

## Still rough

These haven't had focused work yet and may misbehave:

- Rails and redstone — visual / connection edge cases.
- Heavily custom modded blocks (custom shapes, rendering, collision, or models) may need dedicated compatibility work.

## Installation

1. Install the loader for your Minecraft version (Fabric or NeoForge — see the table above).
2. For Fabric builds, install **Fabric API**.
3. Drop the Slabbed `.jar` for your Minecraft version into your `mods` folder.

## Reporting bugs

Bug reports and suggestions are very welcome on GitHub:
<https://github.com/peetsamods/slabbed/issues>

The most useful reports include:

- Minecraft, Slabbed, loader, and Fabric API versions
- Other installed mods (especially Terrain Slabs)
- Exact placement steps, plus what you expected vs. what happened
- A short screenshot or video — especially of the crosshair, placement attempt, or visual mismatch

## Status

Slabbed is currently in **beta**. Behavior expands carefully — category by category, by proven behavior.

## License

Licensed under **GPL-3.0-only**. Source: <https://github.com/peetsamods/slabbed>
