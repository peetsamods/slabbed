# 26.2 speleothem chain fix (2026-06-20)

`scope: release blocker` · `status: staged for live retest` · `branch: port/mc-26.2-0.4.1-beta.1`

## Why this note exists

After the 26.2 manual queue was marked live-closed, Julia re-tested before release and found a remaining merge glitch for
chained downward speleothems: pointed dripstone, and likely sulfur spike. The first follow-up fixed the ceiling-bridged
iron-chain subset, but Julia's next spin showed the direct top-slab speleothem-column case was still glitched. Release
hygiene and publication stay blocked until this new jar gets a fresh live pass.

## Current truth recorded

- Repo root: `/Users/joolmac/CascadeProjects/Slabbed`
- Branch: `port/mc-26.2-0.4.1-beta.1`
- Commit chain:
  - `fcd8aba3` — `Fix lowered stair step collision`
  - `c2f3dc94` — `Fix chained speleothem bridge offsets`
  - follow-up direct top-slab speleothem-chain fix pending/current in this slice
- Red proof: `tmp/runGameTest-speleothem-ceiling-bridged-chain-red.log` failed because the lower pointed-dripstone and
  sulfur-spike segments under a ceiling-bridged iron-chain column inherited `dy=+0.5` and rose into the upper segment.
- Green proof: `tmp/runGameTest-speleothem-ceiling-bridged-chain-green.log` passed 128/128 required tests.
- New direct top-slab red proof: `tmp/runGameTest-speleothem-direct-top-red.log` failed because lower pointed-dripstone
  and sulfur-spike descendants directly under a top-slab-rooted speleothem column inherited `dy=+0.5`.
- New direct top-slab green proof: `tmp/runGameTest-speleothem-direct-top-green.log` passed 130/130 required tests.
- Build proof: `tmp/build-speleothem-direct-top-fix.log` built `build/libs/slabbed-0.4.2-beta.1+26.2.jar`.
- Profile jar staged: `SLABBED-MC 26.2` now has SHA-256
  `fc600d3f7ac502b52289e7e34bb37c8a2172b62267073333453a0856250423b1`.
- Replaced profile jar backup:
  `mods/_codex-backups/slabbed-0.4.2-beta.1+26.2.before-direct-top-speleothem-fix-20260620-230527.jar`
  with SHA-256 `d1bd572d26c9cd58ea5591bf6fc02280b7dd9899088ef78654f4a5e2392d3c62`.

## Mechanism

`ceilingHungDecorationDy` already pinned the block immediately under a ceiling-bridged vertical chain to grid height, but
the next speleothem segment skipped through the upper speleothem, climbed to the top slab, and inherited `dy=+0.5`.
The fix stops the hanger walk when it encounters the ceiling-bridged chain column, so every downward speleothem segment in
that visible chain path uses the same grid-height bridge endpoint.

The direct top-slab case needed its own stop rule. The segment directly under a top slab may still attach upward, but
descendant pointed-dripstone/sulfur-spike segments in the same downward speleothem column must stay grid-height instead
of independently climbing to the top slab and visually merging into the segment above.

## Boundaries preserved

This was not a release, upload, tag, or hygiene pass. The jar is staged for Julia's next in-game retest; the current
running Minecraft session must be restarted to load it.

## Resume pointer

Restart the `SLABBED-MC 26.2` Modrinth profile, retest the direct top-slab pointed-dripstone and sulfur-spike chained
stacks, then resume `$slabbed-pre-release-hygiene` only if the live retest is accepted.
