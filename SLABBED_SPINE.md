# SLABBED_SPINE

This file is the current repo-local truth for Codex. Keep it short. Update it after every proof-confirmed or live-confirmed savepoint.

## Current active root

```text
/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate
```

Known alternate active port root when explicitly working MC 26.1.2:

```text
/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2
```

Stop if the actual root does not match the intended current root.

## Current branch

```text
codex/mc1211-042-beta1-release-update
```

Current working candidate is a dirty release-candidate tree at
HEAD `dae59b90`. Local tag at HEAD:

```text
save/mc1211-0.4.2-beta.2-f3-render-bounds-fix
```

Do not treat it as published until the relevant proof/live gate has passed and
Julia explicitly authorizes commit/tag/push work.

## Current known-good savepoint

Commit:

```text
94a5643e
```

Tag:

```text
save/mc1211-decorative-hanger-followdown-live-confirmed
```

Pushed branch: yes
Pushed tag: yes

Live-confirmed 2026-06-03: decorative-hanger follow-down under lowered FULL blocks
AND lowered TOP slabs (SlabSupport.java; lantern/soul lantern/spore blossom/
hanging roots/pale hanging moss, chains excluded). Supersedes prior tagged+pushed
savepoint `eab0880a` (tag `save/mc1211-sbbs-underside-pre-manual-testing`), which
remains in history. Historical note: 94a5643e deliberately excluded the
then-uncommitted `LoweredSideSlabRetargeter.java` WIP; that later work is
tracked in commit `817f1cc0` and is not part of the hanger savepoint tag.

## Current objective

Bring the latest Slabbed work in this dirty 1.21.1 Fabric candidate to a
proofed, savepoint-ready state without broadening into release publication.
The current technical candidate now includes the F3/render snapshot bounds fix
that shipped as `0.4.2-beta.2`, plus standardized sanity GameTests and the
older uncommitted work around Terrain Slabs, chain/lantern, and
pointed-dripstone. Julia's 2026-06-27 live/manual screenshots still override
the green proxy rows for those older symptoms: they remain RED until
live-confirmed in the real profile. Do not call the port complete or
savepoint-ready while those REDs are open.

## Current blocker

Visible symptoms:

```text
Julia's live/manual Fabric 1.21.1 profile still shows:
- lowered pointed dripstone cannot be combined/placed as expected on a slab
- chain visual triad law broken
- visible chain/lamp gap
- Terrain Slabs perpendicular/cantilever placement about 0.5 dy too high
- broad inability to place objects on Terrain Slabs
```

Failing layer:

```text
not yet narrowed. Each RED needs its own failing-layer classification before
another Java patch: blockstate/placement, model, outline, raycast, dy/triad, or
a named proof gap that directly explains Julia's live screenshot.
```

Protected invariant:

```text
Model, outline, raycast, placement, survival, and post-settle behavior must
agree on the same live-equivalent dy and owner. Automation green for
`MC1211_DRIPSTONE_NUB_ROW` or `MC1211_LANTERN_CHAIN_ROW` does not close Julia's
live visual symptoms. If live play is RED, the docs and active slice must say
RED before any further implementation.
```

Latest repo-local support proof:

```text
2026-06-27 `./gradlew runGameTest` passed all 65 required tests in
tmp/mc1211-chain-lantern-port-audit-3dcab83c/gradle-runGameTest.log. The dripstone
row flipped from `lowerDy=0.500 lowerOutlineY=0.813..1.500` to
`lowerDy=0.000 lowerOutlineY=0.313..1.000`. This is supporting evidence only.
It does not prove Julia's live dripstone, chain, Terrain Slabs, or WYSIWYG
symptoms fixed.
```

Live status:

```text
active RED stack is Julia/live authoritative. Hanger local-live remains
historical truth; VS/lowered-full-block merge issue was previously accepted by
Julia. The current 2026-06-27 live RED stack is not closed.
```

## Release-candidate note

```text
2026-06-28: the F3/render snapshot bounds crash is fixed in `dae59b90` and
tagged locally as `save/mc1211-0.4.2-beta.2-f3-render-bounds-fix`. Clean build,
runGameTest, runClientGameTest, and live smoke all looked good. GitHub push is
currently blocked by pre-existing oversized tracked log blobs in `tmp/`
history, so the branch and tag are not yet published upstream.
```

## Active live REDs, 2026-06-27

Treat this list as the current work queue until Julia updates it:

- Lowered pointed dripstone on slab: still RED for combine/place behavior.
- Chain visual triad: still RED; rendered model, outline, and targeting do not
  align in Julia's screenshots.
- Chain/lamp contact: still RED; visible gap remains.
- Terrain Slabs WYSIWYG cantilever: still RED; perpendicular/cantilever result
  placed about 0.5 dy too high.
- Terrain Slabs object placement: still RED; Julia reports she cannot place any
  objects on Terrain Slabs.

The accidental Modrinth profile jar staging is not proof. No live/profile lane
should resume unless Julia explicitly authorizes it. The next code slice must
select exactly one RED and one failing layer.

## Deferred visual/culling REDs

### MC 1.21.1 VS/lowered full-block adjacency

Visible symptom:

```text
Branch-local manual screenshots on 2026-06-10 originally showed
VS/lowered-full-block checkerboards and full-block-adjacent setups visually
merging. Julia later accepted the merge issue as resolved. The remaining
pictured issue is the culled/missing face / shadow artifact at the lowered
full-block / vanilla slab boundary.
```

Failing layer:

```text
render/culling surface, not placement/rescue/survival
```

Status:

```text
Merge issue resolved by Julia for MC 1.21.1. The remaining pictured
culling/shadow artifact is deferred. The render-worker outline-shape experiment
in SlabSupportStateMixin.java was reversed and must not be restored unless a
fresh culling-specific RED proves the active runtime render path and names the
exact hook.
```

Artifacts:

```text
tmp/mc1211-vbvs-shadow-checkerboard/
tmp/mc1211-vbvs-shadow-audit-20260610/
tmp/mc1211-vbvs-vsvb-merge-red-20260610/
tmp/mc1211-vbvs-vsvb-merge-red-20260610-rerun/
tmp/mc1211-vbvs-vsvb-merge-red-20260610-matrix-red/
```

## Next legal slice

Type:

```text
docs-corrected-red-audit-next
```

Allowed files:

```text
docs/status only for the current instruction. After that, read-only inspection
is allowed only to prepare one narrow RED classification unless Julia explicitly
authorizes implementation or live/profile work.
```

Forbidden files:

```text
new Java/code changes, builds/proof runs, live/profile control, Modrinth jar
staging, build/release metadata, commits, tags, pushes, culling or raycast
changes, and connector/no-snap continuation without a fresh active RED
```

Required proof:

```text
For docs/status-only work, the gate is that all status docs call the current
symptoms RED. For any future code slice, reset `tmp/active-slice.md` and
`tmp/current-red.md`, choose one RED, name one failing layer, produce or identify
a matching red proof, then patch only that layer.
```

Stop condition:

```text
Unexpected tracked dirt outside the known candidate set, staged changes,
production edit during docs-only scope, automation green but live red, or any
attempt to call the port release-ready before live/manual gates and release
gates are complete.
```

## Do not touch boundaries

- Do not touch culling or shadow/render-worker shape unless fresh RED says culling.
- Do not promote Terrain Slabs into generic Slabbed support.
- Do not broaden rescue without RED proof.
- Do not move release tags unless explicitly running release correction.
- Do not use dirty/archive roots unless recovery is explicitly requested.
- Do not edit multiple layers in one slice.

## Later docket / parked user requests

These are not authorized current-slice work. Treat them as future planning
handles only until Julia explicitly opens one.

- Configurable supported-block categories: user request captured 2026-06-26
  asks whether Slabbed can keep support for hanging lanterns/chains from slabs
  while disabling fences for farm compatibility. Future slice must design this
  narrowly, preserve current default behavior, and prove the affected categories
  with placement/survival/visual checks before release.
- MC 1.20.1 Forge port: parked as a future port request. Requires a separate
  port plan, loader/API compatibility audit, and proof gate before any code work.
- MC 26.1 port: user-requested port line. Clarify exact Minecraft/modloader
  target before implementation; do not assume it is the same as the existing
  26.1.2 root.

## 2026-06-12 (Claude, autonomous adversarial + live Cruise)

Branch `release/mc1.21.1-0.4.0-beta.3`, HEAD `1b71ccd9` (clean, NOT pushed). **1.21.1 LOGIC HARDENED:** hunted hard with 7 adversarial gametests (`b26c1007`, in SlabbedLabFixtureTest — compound-stack float, grounded-sink, cantilever 2-out consistency, stale-anchor, refill corruption, geometric recompute, adjacent-compound homogenization); ALL PASS (37/37). No real logic bugs. **CULL FIX LIVE-CONFIRMED under Sodium:** lowered-vs-flat step seam renders SOLID from every window-exposing angle → the model-path `cullFace`-clear engages under Sodium; ghost-window gone. Broad visual hunt = one clean varied structure (more structures + placement-disobedience still open). GOTCHA: this repo is a linked worktree sharing `Slabbed/.git`, so `isolation:'worktree'` agents branched off the SHARED HEAD (`6da1643e`, a 1.21.11 commit) not this branch — verify worktree HEAD or run gametests in-repo. CRUISE GOTCHA: do `request_access` FIRST so the user isn't stuck approving each action. Remaining to release-ready (next thread, opus ultracode): exhaustive visual sweep, placement live, gold-standard cull A/B, then 1.21.11 (see that repo). See HANDOFF.md Cruise update.

## 2026-06-19 — Standardized sanity checklist backport proof passed

Added the standardized sanity surface to this 1.21.1 release line: `docs/process/RELEASE_SANITY_CHECKLIST.md`,
`docs/lessons/LESSONS_INDEX.md`, `RULES.md` §19, and a server-side `Slabbed1211DyFingerprintTest`
registered in the maintained `runGameTest` suite. The fingerprint emits `SLABBED-FP` lines and is paired
with `src/gametest/resources/dy-baseline.txt`. Also corrected the stale top-level branch line above to
match Git and the 2026-06-12 session entry.

Proof: `./gradlew --no-daemon runGameTest --console plain` passed all 50 required tests on 2026-06-19.

Follow-up expansion: added `Slabbed1211StandardChecklistHeadlessTest` and registered it in the server
gametest lane. The expanded lane covers portable resting dy, floor/ceiling/thin-layer rows, bed
coordination, compound clamp, survival predicates, and connector observations. Proof:
`./gradlew --no-daemon runGameTest --console plain` passed all 63 required tests on 2026-06-19.
Known branch-local divergences remain explicit: `ceiling_flush=-0.500` and direct server connector
step rows stay connected on 1.21.1.

## 2026-06-27 — Pointed-dripstone row green, but live REDs still open

Julia's live/manual Fabric 1.21.1 screenshots showed newly placed pointed
dripstone rendering as a tiny nub compared with vanilla. The active technical
red was captured as `MC1211_DRIPSTONE_NUB_ROW`: before patch, a downward
pointed-dripstone descendant under a top-slab-rooted column inherited
`lowerDy=0.500` and its lower outline occupied `0.813..1.500`, visually
collapsing into the segment above. The scoped fix routes downward pointed
dripstone through ceiling-hung support logic and keeps top-slab-rooted
descendants at grid height.

Proof: `tmp/mc1211-chain-lantern-port-audit-3dcab83c/gradle-runGameTest.log`
passed `./gradlew runGameTest` with all 65 required tests. Final dripstone green
marker: `upperDy=0.500 lowerDy=0.000 lowerOutlineY=0.313..1.000`. The same
run directly proved the chain/lantern ceiling-bridge selection row:
`chainOutlineY=0.000..1.500 chainRaycastY=0.000..1.500 lanternDy=0.000`.

Status correction from Julia: these rows do not close the live profile. Lowered
pointed-dripstone combine/place, chain visual triad, chain/lamp gap, Terrain
Slabs WYSIWYG cantilever, and Terrain Slabs object placement all remain RED.
