# Slabbed

**Slabbed** lets supported blocks occupy the visible top surface of slabs and
compatible partial terrain while keeping placement, rendering, targeting, and
collision aligned.

This branch targets **Minecraft 1.20.1 on Forge**.

## Features

- Stores the exact half-step height chosen by a real placement and keeps that
  height stable across neighbor updates, support changes, saves, and reloads.
  This is governed by the repository's [placement permanence contract](LAW.md).
- Uses one numeric height for the block model, selection outline, raycast,
  collision, culling, and client synchronization.
- Supports ordinary slabs plus classified Terrain Slabs surfaces without
  replacing the external mod's model or geometry ownership.
- Preserves custom inventory and composite-renderer model identity.
- Leaves vanilla redstone topology and free-floating chain behavior under
  vanilla ownership.

## Requirements

- Minecraft **1.20.1**
- Forge **47.4.20 or newer**
- Java **17**
- Slabbed installed on both the client and server for multiplayer

Compatibility mods referenced by the automated integration checks are optional
and are not bundled with Slabbed.

## Installation

1. Install Forge for Minecraft 1.20.1.
2. Place the Slabbed JAR in the instance's `mods` folder.
3. For multiplayer, install the same Slabbed build on the server and every
   connecting client.

## Optional deep mode

The shipped default keeps unauthored legacy geometry within the established
shallow range. A server owner can run `/slabbed deep-mode enable` and follow the
confirmation prompt to permanently allow the deeper supported range for that
save.

Deep mode is save-wide and intentionally has no disable command. Make a backup
before enabling it. Existing stored placements do not move when it is enabled.

## Compatibility notes for this line

- Terrain Slabs support on Minecraft 1.20.1 targets releases up to **3.1.1**,
  the newest 1.20.1 build that publishes the block data this integration reads.
  Newer Terrain Slabs releases on 1.20.1 are not yet supported.
- The isolated integration proofs the sibling lines run against Sable,
  Snow! Real Magic, and Smooth Steps have not been carried to this line. No
  compatibility claim is made for those mods here.

## Project status

The Forge 1.20.1 port is pre-live. The headless suite passes in full, and the
repository keeps its current version label until the combined gameplay
acceptance pass and an explicit cross-platform parity ruling are complete.

Known external reports that still require current-artifact live evidence are
tracked in the project's [GitHub issues](https://github.com/peetsamods/slabbed/issues).

## License

Slabbed is licensed under **GPL-3.0-only**.
