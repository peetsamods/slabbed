# Slabbed — Changelog

## 0.4.0-beta (changes since 0.3.0)

**Builds in this release**
- `slabbed-1.21.11-0.4.0-beta.4.jar` — Minecraft **1.21.11**
- `slabbed-1.21.1-0.4.0-beta.3.jar` — Minecraft **1.21.1**

0.4.0-beta is a large step up from 0.3.0: a ground-up overhaul of the **Terrain Slabs**
compatibility layer, plus a long list of positioning, rendering, and stability fixes — including
several found and confirmed during live play-testing. The 1.21.11 build carries the newest fixes
(`-beta.4`); the 1.21.1 build (`-beta.3`) shares the same core behaviour.

---

### Terrain Slabs compatibility — overhaul
- **Objects sit correctly on Terrain Slabs surfaces**, exactly as they do on vanilla slabs: torches,
  lanterns, fences, walls, glass panes, signs, chests / hoppers / furnaces and other block-entities,
  the crafting table, and a curated set of decorative full cubes (pumpkin, carved pumpkin, jack
  o'lantern, melon, bookshelf, hay bale, note block, dried kelp block, sponge/wet sponge, target,
  redstone lamp, smithing/fletching/cartography tables, loom).
- **Full range of Terrain Slabs surfaces recognized** (named/extended support surfaces), and works
  with **both** the modern `terrain_slabs` and the legacy `terrainslabs` mod id.
- **Natural terrain stays put.** Plain opaque world cubes (stone, dirt, grass block, sand, ore, …)
  are no longer lowered onto Terrain Slabs surfaces — what keeps natural terrain hole-free.
- **Stacking on slabs**: objects stack correctly over a slab (a torch on a fence, a fence on a
  fence, …).
- **Connections respect height steps**: fences, walls, and panes no longer connect across a Slabbed
  height step, and stay as single posts down a slab.

### Vegetation — FIXED (new in -beta.4, 1.21.11)
- **Short grass, ferns, and tall grass no longer render invisible or sunk** on Terrain Slabs terrain.
  They now sit correctly on the slab surface. (Root cause: Slabbed and Terrain Slabs were each
  offsetting the same plant model, double-lowering it out of sight — fixed so Terrain Slabs owns the
  vegetation offset and Slabbed does not add a second.)

### Decorations & hangers
- **Hanging roots, spore blossom, and hanging signs hang flush** under a slab — no gap — and no
  longer pop up when a neighbouring block is broken.
- **Hanging lanterns hang flush** under a flush slab (previously dropped with a visible gap). *(new
  in -beta.4)*
- **Decorative hangers follow a lowered support block down** instead of clipping into it.

### Stacks & compound (mixed) slabs
- **Vertical compound stacks sit flush.** A `slab / block / slab / block` tower no longer leaves the
  top block floating half a block above the slab — it compounds to sit flush. *(new in -beta.4)*
- **Mixed slabs** (a vanilla slab capping a Terrain Slabs surface) **compound to the full lowered
  height**, and objects resting on them follow down with them.
- An object on a vanilla **top** slab capping a Terrain Slabs slab now sits flush instead of floating.

### "Never pop" placement law
- **A block you place stays exactly where you put it.** Slabbed no longer recomputes a placed
  block's height on the fly, so adding or removing a neighbouring slab can no longer make a placed
  block autonomously pop **up or down**. *Note:* build support-first — a block placed in mid-air will
  not retroactively drop when you add support under it later; place it on its support (or break and
  replace).

### Rendering
- **Fence, wall, and glass-pane posts sit flush on vanilla slabs** (GitHub #21). Previously the post's
  *model* floated at full height while its outline/selection box lowered correctly — so the post
  appeared to hover above its own hitbox on a vanilla slab (it already worked on Terrain Slabs
  surfaces). The model now renders at the same lowered position as its outline on every slab, and
  connection arms still break correctly across a height step. *(new in -beta.4)*
- **Ghost-window / see-through seams on lowered cubes are gone** — the exposed step faces between a
  lowered block and a flush neighbour render solid now, across vanilla, compound, and cantilever
  lowering, on both the Sodium and the vanilla render paths.
- **World-hole "DODO" fixed** — natural Terrain Slabs terrain no longer tears see-through holes (the
  bug that pulled the previous `0.4.0-beta.3`). The terrain-flush test is now view-independent, so it
  can't disagree between the render thread and the main thread.
- **Powder snow stays flush** on and beside Terrain Slabs terrain (no half-block step across snowy
  ground).

### Targeting & interaction
- **Lowered chests, torches, and beds target correctly** — the outline/selection box and the raycast
  align with where the block is actually drawn.
- **Slabs place correctly against lowered slab and lowered full-block faces.**

### Dev / debug
- `/slabdy` in-game overlay shows the targeted block's computed vertical offset and its source
  (vanilla vs Terrain Slabs) — for diagnosing positioning.

---

### Stability / under the hood
- Headless game-test suite expanded substantially (compound, cull, freeze, hanger, vegetation,
  powder-snow, terrain-hole, fence-connection, and adversarial-regression guards) — **46 server
  tests** green on the 1.21.11 build.
- Render-region boundary lookups guarded against world-load crashes.
- Release jars exclude dev/debug entrypoints, and all remaining diagnostic logging is gated off by
  default (a pre-release hygiene pass stripped the last always-on debug traces).

*Compatibility: Minecraft 1.21.11 (Fabric) with Countered's Terrain Slabs; a separate Minecraft
1.21.1 build is included. Slabbed is inert when Terrain Slabs is not installed (vanilla slab
behaviour only).*
