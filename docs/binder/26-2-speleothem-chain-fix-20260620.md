# 26.2 speleothem chain fix (2026-06-20)

`scope: release blocker` · `status: staged for live retest` · `branch: port/mc-26.2-0.4.1-beta.1`

## Why this note exists

After the 26.2 manual queue was marked live-closed, Julia re-tested before release and found a remaining merge glitch for
chained downward speleothems: pointed dripstone, and likely sulfur spike, under the ceiling-bridged iron-chain setup.
Release hygiene and publication stay blocked until this new jar gets a fresh live pass.

## Current truth recorded

- Repo root: `/Users/joolmac/CascadeProjects/Slabbed`
- Branch / HEAD: `port/mc-26.2-0.4.1-beta.1` / `c2f3dc94`
- Commit chain:
  - `fcd8aba3` — `Fix lowered stair step collision`
  - `c2f3dc94` — `Fix chained speleothem bridge offsets`
- Red proof: `tmp/runGameTest-speleothem-ceiling-bridged-chain-red.log` failed because the lower pointed-dripstone and
  sulfur-spike segments under a ceiling-bridged iron-chain column inherited `dy=+0.5` and rose into the upper segment.
- Green proof: `tmp/runGameTest-speleothem-ceiling-bridged-chain-green.log` passed 128/128 required tests.
- Build proof: `tmp/build-speleothem-bridge-fix.log` built `build/libs/slabbed-0.4.2-beta.1+26.2.jar`.
- Profile jar staged: `SLABBED-MC 26.2` now has SHA-256
  `d1bd572d26c9cd58ea5591bf6fc02280b7dd9899088ef78654f4a5e2392d3c62`.
- Replaced profile jar backup:
  `mods/_codex-backups/slabbed-0.4.2-beta.1+26.2.before-speleothem-bridge-fix-20260620-2241.jar`
  with SHA-256 `903dcc5e9df5e3876d79a05123554ad8f01dbf9f2e431e5edfaebe37c1c434bc`.

## Mechanism

`ceilingHungDecorationDy` already pinned the block immediately under a ceiling-bridged vertical chain to grid height, but
the next speleothem segment skipped through the upper speleothem, climbed to the top slab, and inherited `dy=+0.5`.
The fix stops the hanger walk when it encounters the ceiling-bridged chain column, so every downward speleothem segment in
that visible chain path uses the same grid-height bridge endpoint.

## Boundaries preserved

This was not a release, upload, tag, or hygiene pass. The jar is staged for Julia's next in-game retest; the current
running Minecraft session must be restarted to load it.

## Resume pointer

Restart the `SLABBED-MC 26.2` Modrinth profile, retest the ceiling-bridged pointed-dripstone and sulfur-spike stacks, then
resume `$slabbed-pre-release-hygiene` only if the live retest is accepted.
