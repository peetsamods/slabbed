# HANDOFF — NeoForge 1.21.1 port proof-boundary state (2026-06-23)

> Current handoff for the dedicated NeoForge 1.21.1 port worktree. This section supersedes the older 2026-06-12 Fabric/release-line handoff below for any work in this checkout.

## Repo / branch / HEAD
- Root: `/Users/joolmac/CascadeProjects/Slabbed-neoforge-1.21.1-port`
- Branch: `port/neoforge-1.21.1`
- HEAD at port start: `3dcab83c`
- Created from: `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate` on `release/mc1.21.1-0.4.0-beta.3`
- Tags at HEAD during preflight: none
- Current tracked dirt is broad NeoForge WIP across `build.gradle`, `gradle.properties`, `settings.gradle`, `src/**`, plus repo-local truth docs. Staged changes are not expected.
- Do not touch the original 1.21.1 checkout (`/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate`) or the canonical 1.21.11 Fabric source (`/Users/joolmac/CascadeProjects/Slabbed`).

## Current state
- Functional NeoForge WIP now exists in this worktree. The tree is intentionally dirty, with no staged files, and remains unsaved.
- Current-tree proof exists under `tmp/neoforge-port-20260623/`:
  - `compile-p26-dripstone-chain-green2.log` → `BUILD SUCCESSFUL`
  - `run-client-neoforge-offset-raycast-current-after-dripstone.log` → GREEN client triad/cull proof with `diagnosticsOnly=true` and `releaseReady=false`
  - `run-server-gametest-final-current.log` and `run-server-gametest-final-current-cleanexit.log` → `All 71 required tests passed :)`
- Server-proof caveat: the fresh rerun reproduced the earlier lingering-wrapper behavior. The green server footer is real, but the Gradle wrapper did not return naturally within the wait window and had to be interrupted after the footer.
- Source-delta caveat: a file-path audit against source tag `slabbed-0.4.2-beta.1+26.2` / `8ba3414f` does not prove literal file-for-file parity in this worktree. Many `26.2` behaviors appear ported through consolidated or NeoForge-specific files, but full path parity is not the right current claim.
- Repo-local truth was stale before this update. This handoff now records the real current proof boundary so later savepoint decisions do not rely on the old bootstrap-only story.

## Preflight for next chat
Run before any mutation:

```bash
git rev-parse --show-toplevel
git status -sb
git branch --show-current
git rev-parse --short HEAD
git tag --points-at HEAD
```

Expected:

```text
root: /Users/joolmac/CascadeProjects/Slabbed-neoforge-1.21.1-port
branch: port/neoforge-1.21.1
HEAD: 3dcab83c, unless Julia later creates a savepoint from this WIP
tracked dirt allowed at handoff start: the existing NeoForge WIP across build/src plus repo-local truth docs; no staged files
tags at HEAD: none unless Julia creates one later
```

Stop if the root is any of these: `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate`, `/Users/joolmac/CascadeProjects/Slabbed`, `/Users/joolmac/CascadeProjects/Slabbed-countered-compat-latest`, or any `.windsurf` worktree.

## Port mandate
Create a NeoForge 1.21.1 port of Slabbed from this branch only.

Important architecture findings to preserve:
- Use FFAPI as API support, not as the loader. Forgified Fabric Loader must not be treated as a NeoForge entrypoint bridge for Fabric `ModInitializer` / `ClientModInitializer`.
- The safer direction is a NeoForge-native shell with `@Mod` plus NeoForge metadata, while keeping FFAPI-backed Fabric API internals only where compile proves they are supported.
- Add native NeoForge metadata, probably `META-INF/neoforge.mods.toml`.
- Wire mixin configs through the NeoForge metadata/build path; do not rely on `fabric.mod.json` for NeoForge mixin loading.
- Treat gametest carryover as unproven. Existing gametest wiring is Fabric-specific.
- Watch the stale tracked top-level `fabric.mod.json`: it says `0.1.2-alpha`, `MIT`, and `1.21.11`; it is not the port descriptor.
- Rendering proof is mandatory later because Slabbed's ghost-window fix depends on the FRAPI render/model path and Sodium behavior.

## Files inspected before stop
- `build.gradle`
- `settings.gradle`
- `gradle.properties`
- `fabric.mod.json`
- `src/main/resources/fabric.mod.json`
- `src/main/resources/slabbed.mixins.json`
- `src/main/resources/slabbed.debug.mixins.json`
- `src/client/resources/slabbed.client.mixins.json`
- `src/main/java/com/slabbed/Slabbed.java`
- `src/client/java/com/slabbed/client/SlabbedClient.java`
- `src/client/java/com/slabbed/client/SlabbedModelLoadingPlugin.java`
- `src/main/java/com/slabbed/anchor/SlabAnchorAttachment.java`
- `src/client/java/com/slabbed/client/SlabAnchorClientSync.java`
- `src/main/java/com/slabbed/dev/SlabbedDevCommands.java`
- `src/main/java/com/slabbed/dev/SlabbedLab.java`

## Key findings from inspection
- The project is still a Fabric Loom build: `fabric-loom`, Yarn mappings, Fabric loader, Fabric API, split client source sets, and Fabric gametest setup.
- Main and client entrypoints are Fabric-specific:
  - `com.slabbed.Slabbed` implements `net.fabricmc.api.ModInitializer`.
  - `com.slabbed.client.SlabbedClient` implements `net.fabricmc.api.ClientModInitializer`.
- The current render/model hook is FFAPI-shaped:
  - `SlabbedModelLoadingPlugin` uses `net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin` and `ModelModifier.WRAP_PHASE`.
- The anchor persistence layer depends on Fabric attachments:
  - `SlabAnchorAttachment` uses `AttachmentRegistry`, `AttachmentType`, and `AttachmentSyncPredicate`.
- Client rerender/sync logic depends on Fabric client events and attachment reads:
  - `ClientChunkEvents`, `ClientTickEvents`, and `chunk.getAttached(...)`.
- Dev commands depend on Fabric command registration and Fabric loader game-dir lookup.

## Suggested next implementation slice
1. Re-run preflight.
2. Treat the current tree as a proof-backed candidate, not a bootstrap stub.
3. Keep the claim bounded to current-tree compile/client/server proof plus the explicit server-wrapper caveat.
4. If Julia wants savepoint closure, do a final savepoint-readiness pass on the exact dirty tree, then commit/tag only after the caveat language and file scope are accepted.
5. If Julia wants stronger proof first, investigate the lingering `runServerGameTest` wrapper exit separately without broad gameplay edits.

## Stop condition reached
Yes. The bootstrap-only story is no longer current; the next gate is honest savepoint-boundary handling for the existing NeoForge WIP.

---

# Historical handoff — 1.21.1 adversarial bug-hunt (2026-06-12)

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
- **Open for next thread:** exhaustive broad visual hunt (more player structures), placement-disobedience live (re-enable `r`), the gold-standard cull kill-switch A/B, and the 1.21.11 cull-gap live confirm (needs compat jar in a TS profile). 
- **UPDATE 2026-06-12 (opus):** the deferred 1.21.11 vanilla-compound FLOAT fix is **DONE + LIVE-CONFIRMED** on the compat repo (`Slabbed-countered-compat-latest` @ `21af4243`, new `loweredBottomSlabSupportDyForCompound` porting 1.21.1's `floorTorchBottomSlabSupportDy`; headless 40/40 + 5-lens adversarial-clean + live `/slabdy dy=-1.000` via the Modrinth `Slabbed+Terrain Slabs` profile). NOT pushed. See that repo's HANDOFF. Live-test route that works = Modrinth jar swap, NOT `runClient` (bare `java`, ungrantable to computer-use).
