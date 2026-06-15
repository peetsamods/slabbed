# HANDOFF — Slabbed MC 26.1.2 port (parity-first restart)

> Written 2026-06-15 for a FRESH thread. Companion to `SLABBED_SPINE.md` (append-only log).
> Read this top-to-bottom before touching anything. Memory pointer: `slabbed-2612-port-plan`.

---

## ✅ STEP 1 — get on the right root + branch (verify BEFORE any work)

```
root:    /Users/joolmac/CascadeProjects/Slabbed-port-26.1.2
branch:  port/mc-26.1.2          (only 26.1.2 branch; == origin/port/mc-26.1.2, 0 ahead / 0 behind)
HEAD:    a10376425f828835a2debd32b07e1fa288325662   "fix: restore dy0 slab shape dispatch"
tag:     save/port-26.1.2-dy0-slab-shape-dispatch   (points at HEAD)
```

Confirm with:
```bash
cd /Users/joolmac/CascadeProjects/Slabbed-port-26.1.2
git rev-parse --show-toplevel        # must be …/Slabbed-port-26.1.2
git rev-parse --abbrev-ref HEAD      # must be port/mc-26.1.2
git rev-parse HEAD                   # must be a10376425…
```

**⚠️ Linked-worktree gotcha:** this dir's `.git` is a file pointing into `…/Slabbed/.git/worktrees/…`.
All the `Slabbed*` checkouts SHARE one object store. So:
- **Work in-place in this dir. NEVER use an `isolation: worktree` agent** — it branches off the shared
  (wrong) HEAD and you'll silently edit the wrong version.
- **Do NOT edit any other Slabbed checkout** from 26.1.2 work (`Slabbed-countered-compat-latest`,
  `Slabbed-phase19-integrate`, `Slabbed/` are other products/versions — read-only references only).

## ✅ STEP 2 — clear the parked WIP (Phase 0) before starting fresh

The working tree is **dirty** with parked collision-forensics WIP (7 tracked files, +375/−19, plus
untracked recorder + 3 porting docs):
```
 M  GameRendererCrosshairRetargetMixin.java, LiveCursorIntentRecorder.java, a recorder gametest,
    slabbed.client.mixins.json, AGENTS.md, SLABBED_SPINE.md, mapping-blocker.md
 ?? LevelRendererRenderedOutlineRecorderMixin.java  + ghost-lowered-slab-collision / manual-video-anomaly
    / rearm-revert-release-cert porting docs
```
This is **diagnostic scaffolding for the collision dead-end we are scrapping** (see below). First action:
**commit it as an archive savepoint** (`git add -A` excluding `tmp/`, commit `chore(port): archive
collision-forensics WIP savepoint`) so it's preserved in history and the tree is clean — then build the
parity work on top. Do NOT keep iterating on these recorders.

---

## WHERE THIS PORT STANDS (vs the shipped product)

- MC **26.1.2**, Java **25**, Gradle **9.4.1**, Loom **1.15.5**, loader **0.19.2**, **Mojang mappings**
  (yarn removed). Version `0.2.0-beta.4+26.1.2-port`. Bootstrapped from `release/0.2.0-beta.4`.
- The mapping/tooling blocker is **historical/superseded** — the port compiles and built a jar (2026-05-31,
  `build/libs/slabbed-0.2.0-beta.4+26.1.2-port.jar`).
- It diverged from the 1.21.11 line at **`f4480ce4`** (2026-05-03) — the SAME split point 1.21.11 had
  before its parity port. **All 8 of the June fix families are MISSING here** (ancestor-checked):
  compound-float, ceiling-hanger, NEVER-POP freeze law, vegetation double-offset, fence-float (GH #21),
  DODO world-hole, powder-snow, hygiene.
- Shipped siblings for reference: 1.21.11 = `~/CascadeProjects/Slabbed-countered-compat-latest`
  (`compat/mc1211-terrain-slabs-named-surface-support`, tag `slabbed-0.4.0-beta.4`); 1.21.1 =
  `~/CascadeProjects/Slabbed-phase19-integrate` (`release/mc1.21.1-0.4.0-beta.3`).

---

## THE DECISION: PARITY-FIRST (Julia, 2026-06-15) — scrap collision-follow

**Root cause of the long "spinning in circles" on clipping/ghosting:** 26.1.2 made an architectural bet
the shipped product never made. Its `SlabSupportStateMixin` injects `getCollisionShape` (+ broad
`getShape`) to push the player's **physical collision** down to the lowered slab position. The shipped
1.21.11/1.21.1 `SlabSupportStateMixin` injects **only** `getOutlineShape` + `getRaycastShape` +
`isSideSolid` — it deliberately does NOT touch movement collision.

**Why collision-follow cannot be band-aided:** MC's movement broadphase (`BlockCollisions` →
`Level.getBlockCollisions`) is **cell-bounded** — it assumes a block's collision lives inside its own
unit cube. A lowered slab's shape (`min y = −0.5`) hangs into the cell below, so the broadphase never
yields it → the player walks through (the "ghost"). The recorder forensics PROVED the shape is correct
(`min=(0,−0.5,0)`) but the movement iterator never samples it → it's the **wrong layer**, not a
`getCollisionShape` bug. The deferred-clear/rearm band-aid confirmed the dead-end (fixed 4 mismatch rows,
introduced **5999** outline-split rows + a multiplayer snapshot leak — reverted, archived in
`tmp/reverted-rearm-wip-20260531-161302/`). **Do not revive the rearm/deferred-clear approach.**

**Decision:** match the shipped product — lower **visual/outline/raycast only**, leave physical collision
**vanilla**. Scrap the collision-follow code. (If collision-follow is ever revisited it must be done at the
broadphase layer — a `BlockCollisions`/movement mixin — NOT per-state `getCollisionShape`. The recorders
already pinned the locus to `BlockCollisions`; that knowledge is the only thing worth keeping from them.)

---

## THE PLAN

**Phase 0 — Preserve & baseline.** Archive the dirty WIP as a savepoint commit (above). Tree clean on
`port/mc-26.1.2`.

**Phase 1 — Scrap collision-follow → match shipped architecture.**
- Diff this repo's `src/main/java/com/slabbed/mixin/SlabSupportStateMixin.java` against the shipped
  reference `~/CascadeProjects/Slabbed-countered-compat-latest/src/main/java/com/slabbed/mixin/SlabSupportStateMixin.java`.
- Remove the collision-offset surface: the `getCollisionShape` injection
  (`slabbed$offsetOakFenceAndGrindstoneCollision`, ~line 342) and any path where `getShape` feeds
  movement collision. Keep outline (`getShape`) + raycast (`getInteractionShape`) + side-solidity only.
  (Mojang→Yarn shape names: `getShape`=outline, `getInteractionShape`=raycast, `getCollisionShape`=collision.)
- Retire the collision recorders to dev-only / exclude from the shipping jar.
- **Verify the ghost is gone by construction** (no sub-cell collision shape → broadphase has nothing to
  drop). Build green; relevant headless tests pass.

**Phase 2 — Forward-port the 8 June fix families.** Port the BEHAVIOR adapted to the 26.1.2 API +
Java-25/Loom-1.15 toolchain — **not** cherry-pick (same playbook that carried 1.21.1→1.21.11). Reference
commits on the 1.21.11 line: compound-float `21af4243`, ceiling-hanger `2a50335e`, NEVER-POP freeze law
`8aafd1ff`, vegetation `6f0c73e6`, fence-float (GH #21) `92516668`, DODO `da8cc3cb`, powder-snow (in
`da8cc3cb`), hygiene `a00543aa`. Headless-verify each.

**Phase 3 — Gate & cut.** Release gate = headless `./gradlew runGameTest` + live play + clean jar (see
`slabbed-prerelease-hygiene-gate`; **`runClientGameTest` is NOT a gate** — unmaintained dev-repro
scaffolding, same as the sibling lines). Version bump + tag when Julia is ready. **Nothing is pushed or
published without Julia's explicit go-ahead.**

---

## GUARDRAILS (carry these from SLABBED_SPINE.md)

- Root must be `Slabbed-port-26.1.2`; never edit another Slabbed checkout; in-place only (no worktree iso).
- Use grep/classpath/compiler evidence before changing code; one-file mechanical probes for API drift.
- Don't make release/savepoint claims without commit + clean tracked tree (+ tag/push when explicit).
- The runtime `isAnchored`/`isFrozenFlat` probe is the gold standard for "stale test vs real bug" when
  porting the freeze law — measure, don't reason in unison.

## FIRST ACTIONS FOR THE FRESH THREAD
1. Verify root/branch/HEAD (Step 1).
2. Phase 0: archive the WIP savepoint → clean tree.
3. Phase 1: diff the two `SlabSupportStateMixin` files, strip the collision-offset, build, verify ghost gone.
