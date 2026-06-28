# HANDOFF — NeoForge 1.21.1 release-readiness candidate (2026-06-26)

> Current handoff for the dedicated NeoForge 1.21.1 port worktree. This section supersedes the older 2026-06-23 port-boundary handoff and the historical 2026-06-12 Fabric/release-line handoff below for any work in this checkout.

## Repo / branch / HEAD
- Root: `/Users/joolmac/CascadeProjects/Slabbed-neoforge-1.21.1-port`
- Branch: `port/neoforge-1.21.1`
- Current HEAD: `8f1819b0` (2026-06-28: version bump beta.2). Lineage on top of the
  `255f9c84` 0.4.2 parity candidate: `24aea038` render-trace hygiene fix → `8f1819b0` version bump.
- Version: `0.4.2-beta.2+1.21.1` (was `0.4.2-beta.1+26.2`; build metadata corrected to the target MC version).
- Tag at HEAD: none yet (push + release tag pending Julia's go).
- Created from: `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate` on `release/mc1.21.1-0.4.0-beta.3`
- Current tracked dirt is the release-readiness candidate: WYSIWYG/parity source changes, release jar hygiene in `build.gradle` and `SlabbedClient.java`, repo-local truth doc updates, and evidence logs under `tmp/`. Staged changes are not expected unless Julia explicitly authorizes a savepoint.
- Do not touch the original 1.21.1 checkout (`/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate`) or the canonical 1.21.11 Fabric source (`/Users/joolmac/CascadeProjects/Slabbed`).

## Current state (2026-06-28 — beta.2 render-perf cleanup)
- Builds on the WYSIWYG/parity work (`save/neoforge-1-21-1-wysiwyg-parity-green`) + the `255f9c84`
  0.4.2 parity candidate. Two new commits on top: `24aea038` (render-trace hygiene) + `8f1819b0` (version bump).
- **Lag investigation (CurseForge report "similar lag to Fabric 1.21.1"):** the exact Fabric bug
  (per-block `Class.forName` on a release-excluded class → CNFE storm) is **structurally absent** on
  NeoForge — `RuntimeDiagnostics` is a no-op stub, the render path does zero reflection, the excluded
  `ModelDyTranslateTraceBridge` has zero callers. Found + fixed the generalized cousin: always-on
  per-block work in `OffsetBlockStateModel.getQuads` gated AFTER it ran (`recordMc1211FullMeshBoundsSample`
  registry lookup + ~6 string allocs + atomic; the `measureBounds` per-vertex loop; 3–4 uncached
  `Boolean.getBoolean`). Fixed in `24aea038` (cache flags at class-load, gate `measureBounds`+sampler on
  `slabbed$fullMeshBoundsTraceArmed()`; `render.offset.trace` left live but reordered cheap-first because
  client gametests setProperty it). Zero behavior change; build green. Matters most under Sodium (bypasses
  the mixin path → `getQuads` is the sole per-block cost).
- **Tick spikes:** two Spark profiles (superflat + normal, decoded from bytebin protobuf) showed the
  **Server thread mostly idle/parked**; Slabbed self-time **0.02% superflat / 2.2% normal**, the 2.2%
  almost entirely the collision→getYOffset path (`BlockCollisionsLoweredAboveMixin` →
  `withHangingLoweredCollisionFromAbove` → `getYOffset`, fired per collision-block while moving). NOT a
  spike cause. Julia then couldn't reproduce the spikes → concluded transient worldgen/chunk-load hitches,
  not Slabbed. A cheap inline gate for the collision path is UNSOUND (geometric column-lowering means the
  answer needs the column scan); the only sound optimization is memoizing `getYOffset` (deferred — modest
  2.2%, not justified yet). If spikes recur: `/spark profiler --timeout 60 --only-ticks-over 50` during it.
- Active jar in the Modrinth profile (`SLABBED neoforge 1.21.1/mods/`): `slabbed-0.4.2-beta.2+1.21.1.jar`
  (render fix verified present). BEFORE/AFTER + original jars in `mods/_ab-backup/`.
- **PENDING (Julia's go):** push `port/neoforge-1.21.1` + tag the release (proposed
  `release/neoforge-1.21.1-0.4.2-beta.2`), then upload to Modrinth/CurseForge.

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
HEAD: b634da07 unless Julia creates a newer release-readiness savepoint
tracked dirt allowed at handoff start: the current release-readiness candidate files; no staged files
tags at HEAD: save/neoforge-1-21-1-wysiwyg-parity-green unless Julia creates a newer savepoint
```

Stop if the root is any of these: `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate`, `/Users/joolmac/CascadeProjects/Slabbed`, `/Users/joolmac/CascadeProjects/Slabbed-countered-compat-latest`, or any `.windsurf` worktree.

## Release-readiness proof bundle
- Compile: `tmp/neoforge-port-20260626/release-readiness/compile-release-hygiene-available.log` → `BUILD SUCCESSFUL`
- Build: `tmp/neoforge-port-20260626/release-readiness/clean-build-release-hygiene.log` → `BUILD SUCCESSFUL`
- Server GameTest: `tmp/neoforge-port-20260626/release-readiness/run-server-gametest-release-readiness.log` → `All 95 required tests passed :)`
- Server caveat: the wrapper lingered after the green footer and was interrupted, so this is not a natural zero-exit proof.
- Public jar actual leak scan: `tmp/neoforge-port-20260626/release-readiness/jar-actual-leak-scan-after-hygiene.txt` → zero lines
- Sources jar actual leak scan: `tmp/neoforge-port-20260626/release-readiness/sources-jar-actual-leak-scan-after-hygiene.txt` → zero lines
- Specific removed-class scan: `tmp/neoforge-port-20260626/release-readiness/specific-removed-classes-scan-after-hygiene.txt` → zero lines
- Bytecode hard-reference scan: `tmp/neoforge-port-20260626/release-readiness/jdeps-suspicious-refs-after-hygiene.txt` → zero lines
- `SlabbedClient` bytecode scan: `tmp/neoforge-port-20260626/release-readiness/javap-slabbedclient-suspicious-after-hygiene.txt` → zero lines
- Fabric/Quilt scans: `tmp/neoforge-port-20260626/release-readiness/*fabric-scan-after-hygiene.txt` → zero lines
- Style: `git diff --check` and `git diff --cached --check` → clean
- Post-hygiene live smoke:
  - `tmp/neoforge-port-20260626/release-readiness/live-post-hygiene-first-window.png`
  - `tmp/neoforge-port-20260626/release-readiness/live-post-hygiene-dripstone-fixture.png`
  - `tmp/neoforge-port-20260626/release-readiness/live-post-hygiene-profile-log-markers.txt`
  - `tmp/neoforge-port-20260626/release-readiness/live-post-hygiene-proof-debug-log-scan.txt` → zero lines
  - `tmp/neoforge-port-20260626/release-readiness/live-post-hygiene-error-scan.txt` → zero lines

## Important caveats
- This NeoForge Gradle task graph does not expose `runClientGameTest` or `runGameTest`; the available release proof route is `runServerGameTest`.
- Post-hygiene live smoke confirms the cleaned staged jar launches and renders existing dripstone fixtures in the intended Modrinth world. This is smoke coverage, not a full replay of every prior manual scenario.
- Do not claim public release complete until Julia authorizes the release/upload gate.

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

## Suggested next slice
1. If Julia accepts this candidate, do a final diff-scope review.
2. Commit only the intended release-readiness source/doc files.
3. Create an annotated release-readiness/savepoint tag.
4. Push branch and tag if Julia authorizes.
5. Only after that, ask separately before any public Modrinth/GitHub release upload.

## Stop condition reached
Yes. Release-readiness candidate is staged and proven locally; public release/upload remains approval-gated.

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
