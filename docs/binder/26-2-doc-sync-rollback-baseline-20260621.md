# 26.2 docs sync after rollback-baseline targeting cleanup (2026-06-21)

`scope: docs-only sync` · `status: recorded` · `branch: port/mc-26.2-0.4.1-beta.1`

## Why this note exists

The 26.2 front-door docs had drifted into a mixed story after the slab-held targeting false green. They correctly
recorded the live rejection of candidate jar `27a03f...`, but they still read as if the rejected slab-held targeting
experiment was active in source and as if the next step were more work on that exact branch.

That is no longer true. Active source is back to the rollback baseline, and the live blocker has to be described from
that current state.

## Current truth recorded

- Repo root: `/Users/joolmac/CascadeProjects/Slabbed`
- Branch / HEAD / tag: `port/mc-26.2-0.4.1-beta.1` / `793a8fe7` / `save/port-26-2-0-4-2-beta-1-speleothem-green`
- Active profile truth: `SLABBED-MC 26.2` is on rollback jar SHA-256
  `ba19ebe6f1f8a75ce3de123bcd7179fbf5b062233c01cc4a49de865684e182db`
- Rejected candidate preserved:
  `mods/_codex-backups/slabbed-0.4.2-beta.1+26.2.bad-targeting-27a03f-20260621-120417.jar`
- Active source no longer contains the rejected slab-held owner-guard experiment. Cleanup proof:
  `tmp/compile-source-aligned-targeting-rollback.log`

## What changed in docs

- `HANDOFF.md` now opens with the rollback-baseline targeting state instead of pointing the next worker back at the
  rejected raw-owner experiment.
- `SLABBED_SPINE.md` now records that source/profile alignment has been restored and that release hygiene is still
  blocked on the rollback-baseline live red.
- `docs/process/RELEASE_SANITY_CHECKLIST.md` now distinguishes source-fixed-but-not-profile-active rows from the real
  current blocker, P26-9 on the rollback baseline.
- Binder index entries were filled in for the 2026-06-21 notes so the chronology is navigable again.

## Live blocker state

The current blocker is still slab-held WYSIWYG / selector truth on the rollback baseline, not the removed candidate
path. Julia's current evidence includes:

- selector/crosshair drift and `target: none` on lowered upper slab targeting
- microscopic snap windows where one exact aim spot places correctly and nearby spots jump to a wrong visible cell
- wrong-side placement / slab not targetable on rollback-jar fixtures

## Boundaries preserved

This was a docs-only sync. No source behavior changed, no jar was restaged, and no release/push/tag action happened.

## Resume pointer

Resume from `HANDOFF.md` first. The next safe slice is a rollback-baseline live/classification pass that proves where
visible model, selectable outline, and final `Minecraft.pick` / placement target disagree while slab-held placement is
active.
