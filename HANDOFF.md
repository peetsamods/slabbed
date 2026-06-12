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
_(none confirmed yet — hunt in progress)_

## Commits this session
_(none yet)_

## ⚠️ LIVE-VISUAL TESTING BLOCKED THIS SESSION
macOS is firing system notifications every ~minute; each makes `UserNotificationCenter` frontmost, which (a) pauses singleplayer MC and (b) blocks every computer-use click. Couldn't suppress it: legacy DND flag is ignored on this macOS, `request_access` needs Julia's approval (she's out), quitting WhatsApp/Messages didn't stop them, and Control-Center UI-scripting for Focus was too fragile. So **visual bug-hunting (face-culls, render popping, merge render) + the cull-fix visual confirm are DEFERRED** — they need the GUI. **Recommend:** Julia enables a Focus/Do-Not-Disturb mode (or identifies the chatty app), then the visual pass can run. Everything testable HEADLESS is being done.

## State
- Adversarial headless workflow: RUNNING (w4d81ztnp) — 4 worktree agents authoring+running failing gametests, then skeptic verify.
- Live MC: launched once, now paused/blocked by notifications; keybind = `r` (revert to mouse.right when done).
- Pivoted to: headless hunt + placement-disobedience code analysis + (when hunt returns) triage/fix.
