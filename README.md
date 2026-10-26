# Slabbed

Slabbed is a NeoForge mod for Minecraft 26.2 and 26.3 (this line; the Fabric line serves the same two versions) that makes slab-supported placement, stored height,
targeting, and rendering agree. Its governing invariant is simple: a placed block keeps the height
chosen when it was placed; a later neighbour edit must not move it. See `LAW.md` for the exact rule.

## This line

- Minecraft 26.2 on NeoForge 26.2.0.88 or newer, and Minecraft 26.3 on NeoForge 26.3.0.48-beta or newer, one jar
  each. No Fabric API or Forgified Fabric API. Java 25 builds and runs it.
- Version: see `gradle.properties`; the current cycle is `0.6.2-alpha.1+26.2-neoforge` and `0.6.2-alpha.1+26.3-neoforge`.
- Both jars come from this one source: the 26.2 build uses the pins in `gradle.properties`, the 26.3 build
  overrides them on the command line (`-Pminecraft_version=26.3 '-Pminecraft_version_range=[26.3,26.4)'
  -Pneo_version=26.3.0.48-beta -Pmod_version=0.6.2-alpha+26.3-neoforge`). The suite runs through
  `./gradlew runGameTestServer`; the model layer uses NeoForge's block-model hooks (`com.slabbed.client.model`).
- Other Minecraft versions and loaders live on their own branches (`port/...`) and reach the same
  features when they are ported; this branch is where 0.6 is developed first.
- `CHANGELOG.md` describes what the current build changes for players, including its known
  limitations. A green build here is an automated result, not a live acceptance.

## What Slabbed changes

- Places and stores supported blocks at the aimed visible height, then preserves that stored height.
- Keeps targeting, outline, collision, models, particles and block-entity rendering aligned with the
  stored position, and seats entities that rest on lowered blocks (item frames, minecarts, armor
  stands) where the block is drawn.
- Carries a block's stored height through piston moves.
- Ships a small settings file (`config/slabbed.json`) and an in-game settings screen (`/slabdy
  settings`) for the options builders asked for, starting with the flower-pot seat.
- Ships two unbound key bindings (Options > Controls, "Slabbed" category) that nudge a placed
  block's stored height by half a block; the server decides and permission-checks every nudge.

## Installation

Install the Slabbed NeoForge jar. Keep client and server Slabbed versions aligned for a
real multiplayer deployment, and never use a TEST jar as a release artifact. The
`-Dslabbed.frozenDy` compatibility flag for worlds built before 0.5 must match on both sides as well;
the CHANGELOG's Known limitations explain what it does and what it costs.

## Diagnostics

Two commands ship in every jar and do nothing until you type them. `/slabdy row` prints the full
diagnostic readout for the block under the crosshair, `/slabdy chunk` prints the standing chunk's
placement-data gauge, `/slabdy settings` opens the settings screen, and `/slabdy build` (or
`/slabdev build`) prints the exact build you are running, which is what a bug report needs. The
overlay and recorder behind `/slabdev debug` and `/slabdev record` are development tools and stay
switched off in a normal download.

## Building and testing

`./gradlew build runGameTestServer` on a clean `build/run/gameTest` runs the server suite (JDK 25; `./gradlew25`
pins one); the reported count must match `python3 tools/expected-gametest-count.py`.
`./gradlew runClient -Dslabbed.neoforge.clientWorldProof=true` opens a fresh flat world in the dev client, places
a block on a bottom slab through the real item path and prints one `NF26_CLIENT_WORLD_PROOF_SUMMARY` line that
must read `result=GREEN`. `CLAUDE.md` / `AGENTS.md` hold the rules for coding agents.

## License and feedback

Slabbed is licensed under **GPL-3.0-only** — see [LICENSE](LICENSE). Report bugs or ideas at
<https://github.com/peetsamods/slabbed/issues>.
