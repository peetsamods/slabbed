# SLABBED_SPINE

This file is the current repo-local truth for Codex. Keep it short. Update it after every proof-confirmed or live-confirmed savepoint.

## Current active root

```text
/Users/joolmac/CascadeProjects/Slabbed-countered-compat-latest
```

Stop if the actual root does not match the intended current root.

## Current branch

```text
compat/mc1211-terrain-slabs-named-surface-support
```

## Current known-good savepoint

Commit:

```text
908a3ea3
```

Tag:

```text
(untagged — local commits on compat/mc1211-terrain-slabs-named-surface-support, ahead 18)
```

Pushed branch: yes (origin/compat/mc1211-terrain-slabs-named-surface-support, 18 commits ahead)
Pushed tag: n/a

Live-confirmed at 908a3ea3: TS compat compound stacking, BUG 1 fix (vanilla TOP slab
capping TS slab → -1.0), decorative hanger follow-down, cull window fix. See HANDOFF.md
for WIP state and what is not yet committed.

## Current objective

Finish live inspection of the upgraded 1.21.11 placement parity fixes, then commit the
accumulated WIP as clean slices. Veg fix determined NOT applicable (TS handles
vegetation natively on 1.21.11). TS side-lane parity and VB+VS/VS+VB isolation are
headless-green; the render-region world-load crash guard is live-green. The suspected
merge regression was rechecked live by Julia and is green again. See HANDOFF.md.

## Current blocker

Placement parity/crash-guard WIP is green but uncommitted. Do not commit before final
diff review and Julia's explicit savepoint instruction.

Deferred lane: inappropriate shadows on shifted opaque full-cube quads. The
all-face `cullFace`/AO metadata experiment was automation-green but not
visual-accepted, so it is parked outside the commit candidate. Do not continue
shadow work unless Julia reopens it; next gate is one fresh branch-local visual
repro/triage, not a Sodium/Indigo renderer mixin.

## Do not touch boundaries

- Do not touch culling unless fresh RED says culling.
- Do not continue deferred shadow/lighting work unless Julia explicitly reopens it.
- Do not promote Terrain Slabs into generic Slabbed support.
- Do not broaden rescue without RED proof.
- Do not move release tags unless explicitly running release correction.
- Do not use dirty/archive roots unless recovery is explicitly requested.
- Do not edit multiple layers in one slice.

## 2026-06-11 (Claude, autonomous)

HEAD `b231debe` == origin, tree clean (only untracked `tmp/`). The HANDOFF "uncommitted WIP"
inventory is STALE — that WIP is all committed+pushed at `b231debe`. Build green; headless
gametests **37/37** (`./gradlew runGameTest`, terrainslabs loaded). Added `a7c20bc7`:
`tsCanopyRowAllLowerNoMiddlePop` — pins the canopy steady-state invariant (3- and 5-wide rows of
objects each on their own TS bottom slab all read getYOffset=−0.5, no middle deviation). This
proves the "middle pops up" symptom is a render-region/chunk-mesh desync, NOT a dy-logic gap.
Decisive next step needs Julia: `/slabdy` on the popped middle — `−0.500` while popped ⇒ render
desync; `0.000` ⇒ logic edge (`hasNonLoweredFullBlockSupportBelow`). Remaining to finish: live
visual accept of placement-parity + MODEL_PATH step-cull, version-line decision, release re-cut —
all human-gated. See HANDOFF.md status banner.

## 2026-06-12 (Claude, autonomous adversarial pass) — TWO real bugs found

HEAD `13e42ae3` (clean, NOT pushed). Same adversarial gametest probes that proved 1.21.1 robust found two real 1.21.11 bugs:
1. **FIXED `3a3f57e7` — ghost-window cull was Terrain-Slabs-ONLY.** `isSlabHeightStepFace` keyed on `isDirectCustomSlabSupportedObject` (only TS BOTTOM_LIKE support), so a VANILLA-slab-lowered cube beside a flat cube returned false → both cull paths (BlockRenderInfoCullMixin + YOffsetEmitter model path) left a see-through seam. Fixed → dy-difference `|getYOffset(self)−getYOffset(neighbour)|>ε` (covers vanilla + compound + cantilever; mirrors 1.21.1; only flips cull→draw; kill switch `-Dslabbed.disableStepCull`). Proven by gametest `advVanillaSlabStepMustUnCull`. 40/40 green. ⚠️ RENDER change — live A/B visual confirm still pending (the same mechanism was live-confirmed working under Sodium on 1.21.1, so strong indirect backing).
2. **FOUND + DEFERRED `13e42ae3` — vanilla VERTICAL compound stack FLOATS.** `slab/stone/slab/stone` → top stone reads −0.5 not −1.0 (floats 0.5 above the L2 slab). 1.21.1 gets this right. Root: compound −1.0 only via `isAdjacentSideSlabLowered` (side-adjacency, ~775); a vertically-lowered vanilla slab doesn't qualify. Core dy change + needs visual confirm → not landed unsupervised. Characterized by `advVanillaCompoundStackFloatsKnownBug` (asserts current −0.5; FLIP to −1.0 when fixed). Reference fix = the 1.21.1 `getYOffsetInner` vertical-compound branch. **This is the top release-blocker to fix next.** See HANDOFF.md bug banner.

## 2026-06-12 (Claude, opus ultracode, autonomous) — compound FLOAT FIXED + LIVE-CONFIRMED

The deferred vanilla vertical-compound FLOAT (bug 2 above) is **FIXED**. New recursion-safe reader
`loweredBottomSlabSupportDyForCompound` in `SlabSupport.java` (ports the 1.21.1
`floorTorchBottomSlabSupportDy` semantics) detects a bottom slab lowered vertically (resting on a
lowered full-block carrier) — plus anchor / mixed-TS / lowered-double-slab / side-adjacency — and the
`shouldOffset` compound branch now returns `supportDy − 0.5` (clamped to −1.0). The characterization
test was flipped+renamed to `advVanillaCompoundStackTopMustBeFlush` (asserts L3 = −1.0).

Proven: headless **40/40** (`./gradlew runGameTest`, run in the REAL worktree — NOT an isolation
worktree, per the linked-worktree gotcha); **5-lens adversarial review CLEAN / 0 regressions**; **LIVE**
in the Modrinth `Slabbed+Terrain Slabs` profile (`slabbed-0.2.0-beta.4.1.jar`) — `/slabdy` reads the top
stone `dy=-1.000 LOWERED VANILLA`, column renders flush (no float). Live route = Modrinth-launched game
(the Loom `runClient` dev client is a bare `java` process with no bundle id → NOT grantable to
computer-use; the Modrinth game's window IS drivable). **Local commit only — NOT pushed.**

## 2026-06-13 (Claude, opus) — PARITY PORT: law & canon from 1.21.1

Julia's live bug-hunt exposed that 1.21.11 diverged from 1.21.1 at `f4480ce4` and never got a family
of fixes (confirmed: `83afed84 9a24670c 9ec27ca4 efad9e79 434e7c41` all NOT ancestors of HEAD).
Ported + headless-proven (43/43, NOT pushed):
- `2a50335e` ceiling-hung decorations (roots/spore/sign) take dy from the support ABOVE only — fixes
  the hanger gap + pop-on-break (port of `434e7c41`, adapted to the leaner 1.21.11 SlabSupport).
- `8aafd1ff` NEVER-POP freeze-on-place law — `FROZEN_FLAT_TYPE` + `freezeLoweredOnPlace` + getYOffset
  reads + onPlaced mixin + client sync; fixes the placed-block down-pop (port of `9ec27ca4`+`efad9e79`).
  Two gametests drive the REAL onPlaced path.
Jar staged in Modrinth profile. **Pending Julia (right-click): freeze-law live confirm.** Deferred:
invisible-grass-on-TS (render-path, needs live A/B) and compound -1.0 on right-click placement (needs
the 1.21.1 compound-anchor sidecar). Do NOT push.

## 2026-06-14 (Claude, opus) — vegetation + hanging-lantern LIVE-CONFIRMED

Julia live-drove the Modrinth profile. Two more fixes, both **live "green"** (NOT pushed):
- `bbe3deb9` hanging lanterns hang flush under a flush TS slab (route HANGING blocks through
  `ceilingHungDecorationDy`, which has the `shouldSkipOffset` guard the old tail branch lacked).
- `6f0c73e6` TS vegetation no longer double-offset. ROOT (proven by instrumenting `emitQuads`):
  Terrain Slabs already wraps vegetation in its OWN `net.countered.terrainslabs.model.SlabOffsetModel`
  and offsets it; Slabbed wrapped that and offset AGAIN (2× → sank invisible). Fix:
  `SlabbedModelLoadingPlugin` skips wrapping a model TS already owns; `YOffsetEmitter` shifts once.

LIVE-CONFIRMED this arc: compound float, ceiling-hanger, hanging lantern, vegetation. Remaining:
freeze-law right-click live confirm (Julia; onPlaced-only — a /setblock block is NOT frozen, by
design), the compound-on-placement anchor sidecar, and the 0.4.0-beta version/branch reconciliation
(port DODO `42002295` + powder-snow onto this branch, bump, cut release). Do NOT push.

## 2026-06-14 (Claude, opus) — RECONCILED to 0.4.0-beta.4 (release candidate)

All five live-confirmed (compound float, ceiling-hanger, hanging lantern, vegetation, AND the
NEVER-POP freeze law — Julia confirmed the right-click test "green too"). Then reconciled to the
canonical release line:
- Ported the two real gaps from `Slabbed/ release/mc1.21.11-0.4.0-beta.3` onto this branch: DODO
  world-hole (`42002295`) + powder-snow (`85537dcd`), adapted to compat's SlabSupport. The dual-mod-id
  gate (DODO primary root) was already here; the missing parts were the view-independent
  `isOpaqueFullCube()` terrain pin (vs view-dependent isSolidBlock — render-thread divergence) and the
  column-walk "stop at a solid cube" guards, plus powder-snow exclusion.
- Bumped `mod_version` → `0.4.0-beta.4`; built+installed `slabbed-0.4.0-beta.4.jar` in the Modrinth profile.
- Headless **45/45** (+`powderSnowOnSlabStaysFlush`, +`naturalCubeOverSolidDoesNotLowerThroughToSlab`).

LEFT TO SHIP (human): live-verify natural TS terrain (no holes) + powder snow + placed-tower chaining;
then publish (push + Modrinth/CF). Standing rule: NOT pushed. The other ~13 release-branch commits that
`--cherry-pick` flags are the same TS-compat fixes implemented in parallel here (not real gaps).

## 2026-06-14 (Claude, opus) — 0.4.0-beta.4 FULLY LIVE-VERIFIED

Julia live-verified the terrain changes ("green"): natural TS terrain = no see-through holes, powder
snow flush, placed towers still chain. All 5 parity fixes + DODO + powder-snow now live-confirmed.
`0.4.0-beta.4` is a fully-verified release candidate. ONLY remaining = publish (push + Modrinth/CF),
which needs Julia's explicit go-ahead. Still NOT pushed (15 commits ahead of origin).

## 2026-06-14 (Claude, opus) — RC + changelog staged in Ready Jars

`slabbed-0.4.0-beta.4.jar` (md5 c9098b56, == the live-verified jar) staged at `~/Desktop/Ready Jars/slabbed-1.21.11-0.4.0-beta.4.jar`; the 1.21.1 build `slabbed-1.21.1-0.4.0-beta.3.jar` (fresh build of `release/mc1.21.1-0.4.0-beta.3`, NOT re-tested this session) staged alongside; the pulled 1.21.11 beta.3 removed. Full user-facing changelog (since 0.3.0) written to `~/Desktop/Ready Jars/CHANGELOG-0.4.0-beta.md` + committed here as `CHANGELOG.md`. Still NOT pushed; publish (push+tag+upload) awaits Julia.

## 2026-06-14 (Claude, opus) — pre-release HYGIENE pass (caught by Julia)

Julia flagged that I had NOT done a hygiene pass before staging the RC. Did it: two debug traces
fired unconditionally and shipped — `RedstoneWireBlockMixin` (3 always-on LOGGER.info on every
redstone canPlaceAt/connection) and `GameRendererCrosshairRetargetMixin.slabbed$traceTargeting`
(ungated targeting dump). Fixed in `badf117a` (redstone logs removed, logic kept; traceTargeting
gated behind `-Dslabbed.targetTrace`). Verified: dev/debug packages excluded from jar by build.gradle;
all other LOGGER.info gated (TRACE / isEnabled / TorchParticleTrace.enabled); dev hooks
isDevelopmentEnvironment-gated; no println/printStackTrace; my [GD] debug fully removed; /slabdy
overlay intentionally kept (also in the reference release). Cleaned jar md5 5af1e4c1 re-staged in
Ready Jars + profile (logging-only change, functionally identical to the live-verified jar). 45/45.

## 2026-06-14 (Claude, opus) — GH #21 floating fence posts FIXED + live-confirmed, RC re-staged

GitHub issue #21: fence/wall/pane on a slab rendered FLOATING — `/slabdy` showed the outline/raycast
correctly lowered (`VANILLA dy=-0.500 LOWERED`) but the MODEL stayed at grid height. Reproduced on
VANILLA slabs (oak/smooth-stone/sandstone), NOT on Terrain Slabs surfaces. 1.21.1 was checked live
by Julia and is FINE.

Root: `OffsetBlockStateModel.emitQuads` forced the model `dy=0` for fence/wall/pane unless the
support was a named-custom (Terrain Slabs) surface (`isDirectCustomSlabSupportedObject`) — so on a
vanilla slab the model never got the offset its own outline did. 1.21.1's analogous guard is already
effectively inert (gated on the always-true `isBeta35FenceWallVariantContactObject`), so 1.21.1 needs
no change. Fix `92516668`: drop the suppression — the connecting-block model now always tracks
`getYOffset`; `connectingBlockVisualDy` returns the real dy for vanilla carriers too, so the
connector-arm break (`isSteppedConnectingNeighbor` → `FencePaneSlabConnectionMixin` /
`WallSlabConnectionMixin`) still detects the step and doesn't draw an arm across it. New gametest
`fenceConnectionBreaksAcrossVanillaSlabStep`; suite 46/46. **Julia LIVE-confirmed green** (flush on
vanilla slab, connections clean, TS unchanged).

Second hygiene pass (per the standing gate): re-grepped shipping code; all `[ANCHOR]` logs are gated
behind `SlabAnchorAttachment.TRACE` and the crosshair dump behind `slabbed.targetTrace`, but two
ungated `[Slabbed] AfterBake…` per-model-bake debug logs + the verbose ModelLoadingPlugin init line
were still shipping — stripped in `c3228bb5` (functional wrapping + TS-skip logic unchanged).

Re-staged: `~/Desktop/Ready Jars/slabbed-1.21.11-0.4.0-beta.4.jar` (md5 1a0bca6b, == the staged
Modrinth-profile jar Julia live-verified) and `CHANGELOG-0.4.0-beta.md` (fence fix + 46-test count
folded in); repo `CHANGELOG.md` synced. 1.21.1 jar unchanged. Still NOT pushed — publish (push + tag
`slabbed-0.4.0-beta.4` + Modrinth/CF upload) awaits Julia's go-ahead. Nothing has been released yet.

## 2026-06-14 (Claude, opus) — drop Indium recommendation, rebuild RC

Julia OK'd removing the `recommends: indium` hint from both `fabric.mod.json` (Sodium 0.6+ ships
its own FRAPI on 1.21.11, so Indium is no longer needed). Committed `(indium-removal)`, rebuilt —
metadata-only, no runtime change, so the live-green verdict stands. New jar `slabbed-0.4.0-beta.4.jar`
md5 02ea50ec re-staged identically to the Modrinth `Slabbed+Terrain Slabs` profile and
`~/Desktop/Ready Jars/slabbed-1.21.11-0.4.0-beta.4.jar`. Still NOT pushed.

## 2026-06-14 (Claude, opus) — Codex release block resolved (jar hygiene + stale client gametests)

Codex pre-release hygiene blocked the push. Two real, independent findings (NEITHER from the fence-fix
RC itself; both pre-existing):

1. **Jar hygiene gap (my miss).** The 1.21.11 jar shipped diagnostic bridge classes — `SlabbedDebugBridge`,
   `SlabbedAuditBridge`, `TorchParticleTrace`, `BlockItemPlaceTraceMixin`, `ClientWorldParticleTraceMixin` —
   in `com/slabbed/util` + `com/slabbed/mixin`, which the build.gradle debug-package excludes don't cover
   (my earlier grep matched `SlabbedDebug.class` but not the `*Bridge` suffix). Fixed `a00543aa`: exclude
   from jar + remove every production reference (verified no dangling ref → no NoClassDefFoundError; all
   removals pure trace, functional logic intact). 1.21.1 jar was already clean (different lineage).

2. **`runClientGameTest` red.** Reproduced on CLEAN `1d2b39c9` (not Codex's patch). Root: this 10-class
   client-gametest harness is unmaintained dev-repro scaffolding, broadly red since before the freeze law
   (2026-06-13). Two lowered-lane cases asserted the obsolete pre-freeze-law "orphaned lane normalizes to 0"
   contract — PROVEN stale (runtime `tailAnchored=true` probe + 3 adversarial judges + 2 code-trace lenses:
   a hand-placed lowered slab is anchored at placement and stays -0.5 by NEVER-POP). Reconciled `bf248530`
   (no production change): orphaned case asserts the honest provenance split (frozen hand-placed tail stays
   -0.5; setBlockState seed renormalizes to 0); `assertNoLoweredRemainderWithoutSupport` made anchor-aware.
   The NEXT failure (`SlabbedLabClientGameTest` bed "rescue") is for a feature the test's own comments mark
   "currently BLOCKED" — confirming the harness is scaffolding, not a gate.

**Gate decision (Julia, 2026-06-14): release gate = headless `runGameTest` (46/46) + live play + clean jar;
`runClientGameTest` is NOT a release gate** (documented at the top of HANDOFF.md so Codex respects it).
Re-staged `slabbed-0.4.0-beta.4.jar` md5 50179c9d (now 109 KB, bridge classes excluded) to the Modrinth
profile + `~/Desktop/Ready Jars/`. The `slabbed-0.4.0-beta.4` tag will be moved to the new HEAD (no binary
was ever distributed under it). Nothing uploaded — Julia does Modrinth/CF.
