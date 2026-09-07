# Slabbed

Slabbed is a Fabric mod for Minecraft 26.3 that makes slab-supported placement, stored height,
targeting, and rendering agree. Its governing invariant is simple: a placed block keeps the height
chosen when it was placed; a later neighbour edit must not move it. See `LAW.md` for the exact rule.

## This line

- Minecraft 26.3 on Fabric. Fabric Loader and Fabric API are required. Java 25 builds and runs it.
- Version: see `gradle.properties`; the current cycle is `0.6.0-alpha.1+26.3`.
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

Install the normal Slabbed jar and Fabric API. Keep client and server Slabbed versions aligned for a
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

`./gradlew25 build runGameTest` on a clean `build/run/gameTest` runs the server suite; the reported
count must match `python3 tools/expected-gametest-count.py`. `./gradlew25 runClientGameTest` runs
the client suite; it is green only when the log shows one `CLIENT_GAMETEST | <class> | PASS` line
per registered entrypoint. `CLAUDE.md` / `AGENTS.md` hold the rules for coding agents.

## License and feedback

Slabbed is licensed under **GPL-3.0-only** — see [LICENSE](LICENSE). Report bugs or ideas at
<https://github.com/peetsamods/slabbed/issues>.
