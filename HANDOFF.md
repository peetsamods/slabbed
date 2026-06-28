# HANDOFF — 1.21.1 adversarial bug-hunt (2026-06-12)

> Running handoff for the autonomous adversarial session. Updated as commits land so context survives compaction. Companion to `SLABBED_SPINE.md`.

## Current repo / branch / HEAD
- Root: `~/CascadeProjects/Slabbed-phase19-integrate`
- Current branch: `codex/mc1211-042-beta1-release-update`
- Current candidate HEAD: `dae59b90`
- Current status: dirty release-candidate tree with a committed F3/render-bounds fix. Do not treat it as published until the live/manual gate and any separately authorized release work are complete.
- Current live status, 2026-06-27 Julia correction: active REDs remain open. Do not call the Terrain Slabs, chain, WYSIWYG cantilever, or lowered dripstone symptoms fixed from automation or staged jars.
- Historical June 12 session branch: `release/mc1.21.1-0.4.0-beta.3`
- Current release tag: `save/mc1211-0.4.2-beta.2-f3-render-bounds-fix`

## Mandate (Julia, going to work, unavailable)
Build the messy multi-combined structures players actually build — many slabs + blocks, mixed lowered + vanilla objects — and hunt for: **face-culls, popping, merging-glitches, placement disobedience.** We want BUGS, not validation. Then do the same to get 1.21.11 port-ready. Update this handoff per commit.

## Method
1. Background worktree workflow `slabbed-1211-adversarial-hunt` (task w4d81ztnp): 4 adversarial agents author+run gametests trying to break dy/cull/freeze/merge invariants → skeptic verify → confirmed bugs.
2. Live: Modrinth `Slabbed 1.21.1` profile (Sodium + terrain_slabs). `Use`→`r` rebind for real placement. `/slabdy` overlay. Build structures, screenshot, hunt visual bugs.
3. Triage → write failing gametest (or live repro) → fix → re-verify → commit → update this handoff.

## Standard sanity gate
- Checklist: `docs/process/RELEASE_SANITY_CHECKLIST.md`
- Fingerprint baseline: `src/gametest/resources/dy-baseline.txt`
- Expanded headless class: `Slabbed1211StandardChecklistHeadlessTest`
- Current headless proof: `./gradlew --no-daemon runGameTest --console plain` passes 65/65.
- Capture fingerprint lines with `./gradlew runGameTest --console plain 2>&1 | grep -o 'SLABBED-FP.*' | sort -u`.
- Treat the checklist as additive to live proof. It does not close visual/feel bugs by itself.
- Latest technical support row: `MC1211_DRIPSTONE_NUB_ROW` reports `upperDy=0.500 lowerDy=0.000`, but Julia's live/manual dripstone symptoms remain RED until confirmed in the real profile.
- Chain/lantern row is not closure: `MC1211_LANTERN_CHAIN_ROW` reports outline/raycast numbers, but Julia's screenshots still show chain visual-triad and gap REDs. Treat this as a proof gap, not a pass.

## Active live REDs, Julia authority (2026-06-27)

These are all still RED until Julia live-confirms otherwise:

- Lowered pointed dripstone cannot be combined/placed as expected on a slab. The earlier dripstone nub GameTest is supporting evidence only, not live closure.
- Chain visual triad is broken: rendered chain, outline, and target overlay do not agree.
- Chain/lamp gap remains visible after placement.
- Terrain Slabs perpendicular/cantilever placement is WYSIWYG-broken by about +0.5 dy.
- Objects cannot be placed on Terrain Slabs in Julia's live profile.

Repo-local compile/GameTest success and the accidental Modrinth jar staging do not close any of these. The next implementation slice must choose one RED, name one failing layer, write or identify a matching proof, and patch only that layer. No live/profile lane should resume without Julia explicitly authorizing it.

## Live-test rig (see LIVE-DRIVE-GUIDE.md)
- Modrinth Stop/Play; computer set to never sleep (live capture works).
- `Use` is bound to `r` (revert to `key.mouse.right` in profile options.txt line 91 when done; MC must be stopped to edit).
- Aim with `/tp @s x y z yaw pitch`; read `/slabdy` overlay; place with `r`; break with left-click.

## Bugs found (running log)
- **LOGIC SIDE (headless): NO real 1.21.1 bugs found.** 7 adversarial scenarios (compound-stack float, grounded-sink, cantilever 2-out consistency, stale-anchor-after-air, refill-same-cell corruption, geometric recompute after source break, adjacent-compound-column homogenization) all PASS on 1.21.1 @ `062f771f`. The background worktree hunt's "bugs" were against a STALE commit (`6da1643e` = a 1.21.11 line @ `0.3.0-beta.1`, NOT my branch — the linked-worktree isolation branched off the shared repo's HEAD) and do not reproduce on 1.21.1. Notable property confirmed: adjacent compound columns HOMOGENIZE to the same dy (flush, no step) — so the "cull-miss on compound step" concern is moot on 1.21.1 (the dy-difference predicate is also correct regardless).
- **VISUAL SIDE: NOT TESTED — live-blocked** (notifications, see above). Face-cull render / render-popping / merge-render / placement-disobedience / cull-fix visual confirm remain for a live session.

## Commits this session
- `dae59b90` fix(1.21.1): guard render snapshot bounds in step-face cull
- `062f771f` docs(1.21.1): handoff savepoint (live-blocker note)
- `b26c1007` test(1.21.1): 7 adversarial regression guards (37/37) — 1.21.1 dy/cull/freeze/merge logic robust, no real bugs

## Session conclusion (1.21.1)
- **Logic side: HARDENED + robust.** Hunted hard (7 adversarial scenarios); 1.21.1 held on all. No real logic bugs.
- **Visual side: NOT done — live-blocked** (recurring macOS notifications stole focus all session). The cull-fix visual confirm + render-cull/pop/merge + placement-disobedience hunt need a live session (enable a Focus/DND mode first).
- MC stopped; keybind reverted to `mouse.right`; hunt worktrees pruned.
- Then moved to 1.21.11 (separate repo `Slabbed-countered-compat-latest`): found+fixed the vanilla/compound ghost-window cull gap (`3a3f57e7`); found+deferred the vanilla vertical-compound FLOAT (`13e42ae3`, characterized). See that repo's HANDOFF.
- 2026-06-28 follow-up: the staged `0.4.2-beta.2` jar built cleanly, passed `runGameTest` and `runClientGameTest`, and Julia's live smoke plus Spark report looked healthy. GitHub push was blocked by pre-existing oversized tracked log blobs in `tmp/` history, not by the new fix commit.

## ✅ CRUISE UPDATE (2026-06-12 evening, LIVE — visual block resolved)
The "live blocked" notifications were macOS asking Julia to approve each computer-use action (she was at work). Solved by `request_access` ONE-TIME grant up front (Minecraft/Modrinth/Finder, full tier) — then smooth solo driving, no per-action prompts. **LESSON: on every live "Cruise", do `request_access` FIRST thing so the user isn't stuck approving.**

- **✅ 1.21.1 cull fix CONFIRMED working under Sodium.** Built a lowered-vs-flat step against open sky; the step seam renders SOLID stone from every window-exposing angle (SE + NW). Since Sodium *does* cull faces between adjacent opaque cubes, a solid seam proves the model-path `cullFace`-clearing IS honored by Sodium (the key uncertainty). Ghost-window gone. (Gold-standard kill-switch A/B not run — needs a rebuild/relaunch; evidence already strong.)
- **✅ Broad visual hunt — clean SAMPLE (not exhaustive).** One varied mixed structure (lowered stone wall + lowered glass + lantern + flat wall behind, on slabs): glass shows solid interior (no sky-window), lantern seated, step seam solid, no gaps/floats/z-fighting. No bugs. NOTE: only one structure — "build many" sweep + placement-disobedience (needs `r`-keybind) still open.
- **✅ F3/live smoke follow-up — clean.** Julia reported the staged jar seemed fine with no issues, and Spark showed healthy TPS/MSPT.

## State (end of session)
- 1.21.1: HEAD `1b71ccd9`, clean, nothing pushed. Logic hardened (37/37) + cull fix live-confirmed.
- MC: still RUNNING (New World scratch). Keybind reverted to `mouse.right`. Hunt worktrees pruned.
- **Open for next thread:** clean up the oversized `tmp/` log blobs that block GitHub push, then retry branch/tag push if Julia wants the release line continued; otherwise keep the current live-smoke-green candidate parked.
- **UPDATE 2026-06-12 (opus):** the deferred 1.21.11 vanilla-compound FLOAT fix is **DONE + LIVE-CONFIRMED** on the compat repo (`Slabbed-countered-compat-latest` @ `21af4243`, new `loweredBottomSlabSupportDyForCompound` porting 1.21.1's `floorTorchBottomSlabSupportDy`; headless 40/40 + 5-lens adversarial-clean + live `/slabdy dy=-1.000` via the Modrinth `Slabbed+Terrain Slabs` profile). NOT pushed. See that repo's HANDOFF. Live-test route that works = Modrinth jar swap, NOT `runClient` (bare `java`, ungrantable to computer-use).
