# HANDOFF — 1.21.1 port: decorative hanger clip/gap under lowered blocks

> Untracked artifact. Safe to delete. Companion to memory `slabbed-1211-port-hanger-follow.md`.
> Recommended model for this work: **Opus, maximum reasoning / extended thinking** (deep, architecturally subtle).

## Goal / desired behavior
Make decorative hangers — **lantern, soul lantern, spore blossom, hanging roots, pale hanging moss (NOT chains)** — hang **flush** under their support block, per-block:
- Support renders **lowered** → hanger currently **clips up into it** → must drop to match the lowered underside.
- Support renders **normal** → hanger hangs **flush** (already correct; don't disturb).
- **Chains** keep clipping up to connect → exclude them.
- **Top-slab `+0.5` adherence MUST be preserved** (do not reintroduce the gap the user originally flagged).

## Repo / branch / HEAD / constraints
- `~/CascadeProjects/Slabbed-phase19-integrate` — THE canonical active root (confirm via `SLABBED_SPINE.md`).
- Branch `port/mc-1.21.1`, HEAD `06acec0f` at handoff.
- **Do NOT commit to this root without user review.** Preserve the user's uncommitted WIP: `src/client/java/com/slabbed/client/runtime/LoweredSideSlabRetargeter.java`.

## The crux (why every attempt failed)
The hanger must inherit its support block's **exact rendered dy, per block** (it varies by anchor state — that's why no single uniform offset works). Computing that from inside the hanger's own `SlabSupport.getYOffset` is blocked by the `IN_GET_Y_OFFSET` ThreadLocal **recursion guard**.

Failed (all produced non-render-matching values → gap in one direction or clip in the other):
- `getYOffsetInner(support)` — over-reports under the guard → gap on non-anchored overhangs.
- `isAnchored` / `hasBottomSlabBelow` — false positives → still gaps.
- A uniform `−0.5` — wrong for any block whose real render dy ≠ −0.5.

The existing **working SLAB case** (lantern under a lowered slab) does it right via specialized recursion-safe helpers: `loweredSlabUndersideSupportDy`, plus the family `beta35FenceWallVisibleSupportDy`, `isOrdinaryFullBlockWithCompoundDy`, `isCompoundVisible*` (all in `SlabSupport.java`). The **full-block analogue must be equally precise** — it must equal `getYOffset(support)` exactly, computed with recursion-safe predicates, not a guess.

## Key code locations (`SlabSupport.java`)
- `getYOffsetInner` ceiling section (~1655–1740): the existing `isBeta35LoweredSlabUndersideVisibleOwnerObject` → `loweredSlabUndersideSupportDy` block, then the top-slab `+0.5` adherence. Insert the full-block follow-down here, AFTER the slab-underside block and BEFORE the `+0.5` block.
- `loweredSlabUndersideSupportDy` (~426) and `beta35FenceWallVisibleSupportDy` (~487) — the recursion-safe support-dy helpers to mirror for full blocks. Note `beta35FenceWallVisibleSupportDy` returns −1.0 for full blocks only via `isOrdinaryFullBlockWithCompoundDy`, else NaN.
- `IN_GET_Y_OFFSET` guard (~864); `getYOffsetInner` invoked at ~881.
- Decorative-hanger set lives in `isBeta35LoweredSlabUndersideVisibleOwnerObject` (~375): `isOf(LANTERN) || isOf(SOUL_LANTERN) || SporeBlossomBlock || HangingRootsBlock || isPaleHangingMossBlock`.
- Render path: `src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java` — applies dy in `emitBlockQuads`; carries a `liveModelTrace` system (`MODEL_TRACE_LOWER/HIGHER/MATCHES_THAN_OUTLINE`) that compares model dy vs outline dy — built for exactly this bug.

## Test harness reality
- Standard `fabric-client-gametest` API is **BROKEN on 1.21.1** (port author's own note). `FabricClientGameTest` impls will NOT run — don't waste time registering one.
- Working harness: `Mc1211GoblinRouteClientEntrypoint` (`src/gametest`, ~6887 lines) — tick-driven `ClientModInitializer`, gated by `-Dslabbed.mc1211.<route>` props, **log-only (no screenshots)**.
- `SlabAnchorAttachment.addAnchor(world, pos, state)` exists for anchoring in scenes.
- Baseline (working slab case): `./gradlew runClientGameTest` with `-Dslabbed.mc1211.goblinOnly=true -Dslabbed.mc1211.sbbsFinalSlabTargetingRed=true -Dfabric.client.gametest.disableNetworkSynchronizer=true` → emits `[MC1211_SBBS_FINAL_SLAB_TARGET_ROW]` (supportDy=-0.5, lanternUnderDy=-0.5 flush, chainLanternDy=0).
- Bug diagnostic: add `-Dslabbed.mc1211.liveModelTrace=true` → logs the model-vs-outline dy deltas per block.

## Workflow (user preference — IMPORTANT)
- For live testing, **launch the dev client**: `./gradlew runClient` (background). Do NOT build a jar and ask the user to copy it into a mods folder.
- Dev client runs **Indigo** (not Sodium). The bug is **mod-logic** and reproduces under BOTH Indigo and the user's Sodium — so Indigo dev is a faithful repro and you can iterate without the user.
- Stop the background `runClient` when done (it spams per-tick retargeter logs while a slab is held).

## Recommended plan
1. `runClient` (Indigo) + `-Dslabbed.mc1211.liveModelTrace=true`. Place a lantern/spore under (a) a lowered/anchored block and (b) a normal block; read the `MODEL_TRACE_*` deltas to get each support's exact rendered dy.
2. Write a recursion-safe `loweredFullBlockUndersideSupportDy` returning EXACTLY `getYOffset(support)`'s rendered value (mirroring the slab helper's precision). Route the decorative-hanger set (excl. chains) through it in the ceiling section.
3. Verify via the goblin SBBS dy log + the user's live `runClient`.
4. If the recursion-guard problem stays intractable, it's Codex's architectural domain (they built the guard + trace infra) — say so rather than burn cycles.

## Other Slabbed context
- 1.21.11 compat (`~/CascadeProjects/Slabbed-countered-compat-latest`, HEAD `43da3f5d`) is DONE/clean: window fix + hanger fix committed; "gaps everywhere" was a stale pre-`cb0a950c` jar (already fixed). See memory `slabbed-terrain-gaps-resolved.md`.
- Read `MEMORY.md` and the three Slabbed memory notes before starting.
