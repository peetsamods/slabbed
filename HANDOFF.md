# HANDOFF — 1.21.1 adversarial bug-hunt (2026-06-12)

> Running handoff for the autonomous adversarial session. Updated as commits land so context survives compaction. Companion to `SLABBED_SPINE.md`.

## Repo / branch / HEAD
- Root: `~/CascadeProjects/Slabbed-phase19-integrate`
- Branch: `release/mc1.21.1-0.4.0-beta.3`
- HEAD at session start: `9745adb8` (renderer-agnostic ghost-window cull fix)
- **Nothing is pushed.** Local commits only.

## Mandate (Julia, going to work, unavailable)
Build the messy multi-combined structures players actually build — many slabs + blocks, mixed lowered + vanilla objects — and hunt for: **face-culls, popping, merging-glitches, placement disobedience.** We want BUGS, not validation. Then do the same to get 1.21.11 port-ready. Update this handoff per commit.

## Method
1. Background worktree workflow `slabbed-1211-adversarial-hunt` (task w4d81ztnp): 4 adversarial agents author+run gametests trying to break dy/cull/freeze/merge invariants → skeptic verify → confirmed bugs.
2. Live: Modrinth `Slabbed 1.21.1` profile (Sodium + terrain_slabs). `Use`→`r` rebind for real placement. `/slabdy` overlay. Build structures, screenshot, hunt visual bugs.
3. Triage → write failing gametest (or live repro) → fix → re-verify → commit → update this handoff.

## Live-test rig (see LIVE-DRIVE-GUIDE.md)
- Modrinth Stop/Play; computer set to never sleep (live capture works).
- `Use` is bound to `r` (revert to `key.mouse.right` in profile options.txt line 91 when done; MC must be stopped to edit).
- Aim with `/tp @s x y z yaw pitch`; read `/slabdy` overlay; place with `r`; break with left-click.

## Bugs found (running log)
- **LOGIC SIDE (headless): NO real 1.21.1 bugs found.** 7 adversarial scenarios (compound-stack float, grounded-sink, cantilever 2-out consistency, stale-anchor-after-air, refill-same-cell corruption, geometric recompute after source break, adjacent-compound-column homogenization) all PASS on 1.21.1 @ `062f771f`. The background worktree hunt's "bugs" were against a STALE commit (`6da1643e` = a 1.21.11 line @ `0.3.0-beta.1`, NOT my branch — the linked-worktree isolation branched off the shared repo's HEAD) and do not reproduce on 1.21.1. Notable property confirmed: adjacent compound columns HOMOGENIZE to the same dy (flush, no step) — so the "cull-miss on compound step" concern is moot on 1.21.1 (the dy-difference predicate is also correct regardless).
- **VISUAL SIDE: NOT TESTED — live-blocked** (notifications, see above). Face-cull render / render-popping / merge-render / placement-disobedience / cull-fix visual confirm remain for a live session.

## Commits this session
- `062f771f` docs(1.21.1): handoff savepoint (live-blocker note)
- `b26c1007` test(1.21.1): 7 adversarial regression guards (37/37) — 1.21.1 dy/cull/freeze/merge logic robust, no real bugs

## Session conclusion (1.21.1)
- **Logic side: HARDENED + robust.** Hunted hard (7 adversarial scenarios); 1.21.1 held on all. No real logic bugs.
- **Visual side: NOT done — live-blocked** (recurring macOS notifications stole focus all session). The cull-fix visual confirm + render-cull/pop/merge + placement-disobedience hunt need a live session (enable a Focus/DND mode first).
- MC stopped; keybind reverted to `mouse.right`; hunt worktrees pruned.
- Then moved to 1.21.11 (separate repo `Slabbed-countered-compat-latest`): found+fixed the vanilla/compound ghost-window cull gap (`3a3f57e7`); found+deferred the vanilla vertical-compound FLOAT (`13e42ae3`, characterized). See that repo's HANDOFF.

## ✅ CRUISE UPDATE (2026-06-12 evening, LIVE — visual block resolved)
The "live blocked" notifications were macOS asking Julia to approve each computer-use action (she was at work). Solved by `request_access` ONE-TIME grant up front (Minecraft/Modrinth/Finder, full tier) — then smooth solo driving, no per-action prompts. **LESSON: on every live "Cruise", do `request_access` FIRST thing so the user isn't stuck approving.**

- **✅ 1.21.1 cull fix CONFIRMED working under Sodium.** Built a lowered-vs-flat step against open sky; the step seam renders SOLID stone from every window-exposing angle (SE + NW). Since Sodium *does* cull faces between adjacent opaque cubes, a solid seam proves the model-path `cullFace`-clearing IS honored by Sodium (the key uncertainty). Ghost-window gone. (Gold-standard kill-switch A/B not run — needs a rebuild/relaunch; evidence already strong.)
- **✅ Broad visual hunt — clean SAMPLE (not exhaustive).** One varied mixed structure (lowered stone wall + lowered glass + lantern + flat wall behind, on slabs): glass shows solid interior (no sky-window), lantern seated, step seam solid, no gaps/floats/z-fighting. No bugs. NOTE: only one structure — "build many" sweep + placement-disobedience (needs `r`-keybind) still open.

## State (end of session)
- 1.21.1: HEAD `1b71ccd9`, clean, nothing pushed. Logic hardened (37/37) + cull fix live-confirmed.
- MC: still RUNNING (New World scratch). Keybind reverted to `mouse.right`. Hunt worktrees pruned.
- **Open for next thread:** exhaustive broad visual hunt (more player structures), placement-disobedience live (re-enable `r`), the gold-standard cull kill-switch A/B, and the 1.21.11 cull-gap live confirm (needs compat jar in a TS profile). Plus the deferred 1.21.11 vanilla-compound FLOAT fix (core dy change — port the 1.21.1 vertical-compound branch).
