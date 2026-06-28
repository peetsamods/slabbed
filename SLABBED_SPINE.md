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
port/mc-1.21.1
```

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

Keep the MC 1.21.1 port on the pushed hanger closure plus tracked side-carrier
commits, with the VS/lowered-full-block merge issue accepted as resolved by
Julia's 2026-06-10 manual branch-local check. The remaining pictured face
culling/shadow artifact is deferred for post-beta follow-up. Current work is
the beta.3 release/manual-Modrinth-test gate Julia explicitly requested.

## Current blocker

Visible symptom:

```text
deferred pictured face culling/shadow artifact beside VS/lowered full-block
checkerboard setup
```

Failing layer:

```text
render/culling surface; not placement/rescue/survival
```

Protected invariant:

```text
Model, outline, raycast, placement, and post-settle behavior must agree on the
same live-equivalent dy and owner. Julia accepted the former merge issue as
resolved; the pictured face culling/shadow artifact is explicitly deferred.
No render/culling production patch is allowed unless a fresh RED names culling
and proves the active runtime render path first.
```

Latest proof:

```text
2026-06-10 release hygiene and beta.2 version proof passed on port/mc-1.21.1.
Julia later reported the merge issue resolved and requested beta.3 parity with
the MC 1.21.11 port, with the pictured culling/shadow issue deferred.
```

Live status:

```text
hanger local-live confirmed; merge issue resolved by Julia; pictured
render/culling artifact deferred
```

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
savepoint-then-raycast-proof
```

Allowed files:

```text
SLABBED_SPINE.md, docs/codex/06-bug-blaster-case-law.md,
docs/codex/source-pack/02_SLABBED_ACTIVE_STATUS.md, proof logs under tmp/
```

Forbidden files:

```text
src/**, build.gradle, settings.gradle, gradle.properties, fabric.mod.json, *.mixins.json, release/version/changelog files
```

Required proof:

```text
Use the branch-local current-HEAD Gradle dev client only. Do not use
Applications/Minecraft, stale jars, wrong-head jars, or the vanilla launcher.
After hanger closure, first bug slice is raycast-only proof for
TARGETING_DID_NOT_HIT_BOTTOM_SLAB_UNDERSIDE.
```

Stop condition:

```text
Unexpected tracked dirt in the savepoint worktree, stale/wrong-head proof
source, production render/culling edit without fresh culling RED, or any attempt
to call the port release-ready before the release gate.
```

## Do not touch boundaries

- Do not touch culling or shadow/render-worker shape unless fresh RED says culling.
- Do not promote Terrain Slabs into generic Slabbed support.
- Do not broaden rescue without RED proof.
- Do not move release tags unless explicitly running release correction.
- Do not use dirty/archive roots unless recovery is explicitly requested.
- Do not edit multiple layers in one slice.

## 2026-06-12 (Claude, autonomous adversarial + live Cruise)

Branch `release/mc1.21.1-0.4.0-beta.3`, HEAD `1b71ccd9` (clean, NOT pushed). **1.21.1 LOGIC HARDENED:** hunted hard with 7 adversarial gametests (`b26c1007`, in SlabbedLabFixtureTest — compound-stack float, grounded-sink, cantilever 2-out consistency, stale-anchor, refill corruption, geometric recompute, adjacent-compound homogenization); ALL PASS (37/37). No real logic bugs. **CULL FIX LIVE-CONFIRMED under Sodium:** lowered-vs-flat step seam renders SOLID from every window-exposing angle → the model-path `cullFace`-clear engages under Sodium; ghost-window gone. Broad visual hunt = one clean varied structure (more structures + placement-disobedience still open). GOTCHA: this repo is a linked worktree sharing `Slabbed/.git`, so `isolation:'worktree'` agents branched off the SHARED HEAD (`6da1643e`, a 1.21.11 commit) not this branch — verify worktree HEAD or run gametests in-repo. CRUISE GOTCHA: do `request_access` FIRST so the user isn't stuck approving each action. Remaining to release-ready (next thread, opus ultracode): exhaustive visual sweep, placement live, gold-standard cull A/B, then 1.21.11 (see that repo). See HANDOFF.md Cruise update.

## 2026-06-27 (Claude/Opus, lag crisis + clean rebuild)

Branch **`claude/mc1211-clean-rebuild`** (worktree `Slabbed-laghotfix`). PUSHED `c43b6a76` + tag
`save/mc1211-clean-rebuild-dripstone` to origin = LAG SPIKE FIXED (GH #27/#28/#29: per-block
ClassNotFoundException render storm from hygiene commit `098769f8` — negative-cache + flag-gate the
diagnostics) + chain gap/triad (elongated `ChainCeilingGeometry`) + dripstone combine (server hit-tolerance
shift, Julia-confirmed) + TS subtractive dual-id + richer /slabdy (4 lines). Squashed onto origin `56a98575`
because the `3dcab83c` lineage has >100MB tmp logs (`9ec27ca4`) GitHub rejects; tmp/ now gitignored.
Discarded Codex's broken WIP (`codex/mc1211-042-beta1-release-update`). UNCOMMITTED on top = TS full-cube
lowering WIP — STILL BROKEN per Julia (CLEAN4): (1) full-block stack on TS bottom gaps, (2) objects on TS
TOP slabs wrongly lowered -0.5, (3) block on a lowered support doesn't inherit the drop, (4) TS cantilever
renders full/odd; world-hole risk unconfirmed. NEXT: fix one RED at a time (top-slab over-lower first), then
push. See HANDOFF.md for full per-RED detail + the live-rig notes (bare-java, screencapture only).

---

## 2026-06-27 (cont.) — TS REDs #1/#2/#3 fixed in code (CLEAN5), live-pending

Branch `claude/lag-hotfix-perf` (HEAD lineage authoritative; the `claude/mc1211-clean-rebuild` name in older
notes is stale). Two local commits on top of `c43b6a76` (NOT pushed — gated on Julia's live confirm):

- `1c6da070` **RED#2** — the "smooshed lantern on a TS top slab". ROOT CAUSE was NOT the directCustom lane
  (it correctly excludes TS TOP) but `freezeLoweredOnPlace` anchoring decorative followers (outside the anchor
  scope, "no torch interaction"): a follower lowered onto a TS BOTTOM slab froze at -0.5, then went STALE when
  the surface became a flush TOP. Fix: gate BOTH freeze branches on `structural` (ordinary full block || slab);
  followers stay geometric and recompute to 0 on a flush/top surface. HEADLESS-PROVEN via
  `loweredFollowerStaysGeometricNotAnchored` (vanilla BOTTOM→TOP retype — exact mechanism analogue).
- `b5bd1fc9` **RED#1 + #3** — full-cube stacks gapped / objects floated above a TS-lowered cube. ROOT CAUSE:
  the full-cube lane lowers a cube to -0.5 but leaves it frozen-flat/un-anchored, so the compound walk never
  saw it as a lowering support. Fix: `isTerrainSlabLoweredFullCube` wired into `hasSlabInColumn` +
  `slabColumnYOffset` → -0.5 carried up the column uniformly. Non-TS paths unchanged (38/38 headless).

KEY LESSON: on 1.21.1 the TS surface path is NOT headless-testable (the working TS client-gametests in
COMPAT_REF need the real TS mod + the 1.21.11 client-gametest harness, which is broken here). BUT the
freeze/anchor + compound-walk LOGIC is TS-independent, so vanilla analogues red-proof the mechanism. RED#4
(TS slab rendering as a full cube at dy=0) looks TS-side (Slabbed is subtractive there), DEFERRED.

Active jar = `slabbed-1.21.1-0.4.0-beta.3-CLEAN5.jar` (CLEAN4 → `_ab-backup/`). Next: Julia live-validates
CLEAN5; if green, push `c43b6a76..b5bd1fc9` + tag. See HANDOFF.md for the live-test script.

## 2026-06-28 — TS object WYSIWYG LIVE-CONFIRMED (jar CLEAN7)
Pushed `claude/lag-hotfix-perf` @ `d44f1d30` + tag `save/mc1211-ts-object-wysiwyg-live-confirmed`.
All floor-standing objects (vertical chain, upward dripstone, floor torch, floor/wall bell·lever·button, standing
lantern) now lower -0.5 flush on placed TS bottom slabs; genuinely-hung objects + chain ceiling geometry unchanged.
Two fixes: `c6346af1` placement (OR `customSlabSurfaceKind==BOTTOM_LIKE` into the 3 UP-face support paths so vanilla
`canPlaceAt` accepts a flat TS bottom slab) + `d44f1d30` lowering matrix (directCustom gates on new
`isCeilingHungFromAbove` mirroring vanilla `shouldOffset` nuance, NOT blunt `isCeilingAttached`; chain ceiling geometry
preserved via `isVerticalChainDirectlyUnderCeilingSupport` TOP|DOUBLE). Build + 39/39 headless gametests green.
LESSON: principle over per-symptom — enumerate the full object matrix, mirror the vanilla path, lock parity with
headless vanilla-slab gametests (TS itself not headless-testable). RED#4 (TS slab self-render) DEFERRED (TS-side).

## 2026-06-28 — RED#4 struck (stale)
Julia confirmed the "TS slab renders as a full cube" note was a stale pre-fix screenshot artifact, NOT a live bug.
No open TS REDs remain. Outstanding item is non-code: published Modrinth/CF 0.4.0-beta.3 is still the OLD laggy build;
this branch (`claude/lag-hotfix-perf`) is the fix but has not been cut as a release.
