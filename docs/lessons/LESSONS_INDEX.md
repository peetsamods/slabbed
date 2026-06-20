# Slabbed Lessons Index

This is the short durable memory for repeated Slabbed mistakes. Before a port, backport, live-client proof, release-candidate proof, or cross-version parity slice, read the relevant rows and then open the linked docs.

## How To Use This File

- Use this as a checklist, not as a substitute for evidence.
- If a lesson applies, name it in the working card or report.
- If a new repeated mistake appears, add a small lesson here and link the evidence doc.
- Keep operational state in `SLABBED_SPINE.md`; keep long evidence in `tmp/` or the specific `docs/porting/*` note.

## Lessons

| ID | Lesson | Repeated failure it prevents | Required action | Evidence pointers |
|---|---|---|---|---|
| S1 | Root/profile drift is expensive. | Working from the wrong Slabbed checkout, wrong Modrinth profile, wrong jar, or shared-worktree branch. | Run Git preflight, confirm profile/jar before live proof, and keep sibling repos read-only unless Julia explicitly asks. | `AGENTS.md`, `HANDOFF.md`, `SLABBED_SPINE.md` |
| S2 | Live proof has its own preflight. | Losing time to keybind state, focus behavior, stale positions, stale anchors, or unverified staged jars. | Read `docs/process/LIVE_DRIVE_PREFLIGHT.md` before live work and record the profile, jar, keybind, fresh positions, and `/slabdy` evidence. | `LIVE-DRIVE-GUIDE.md`, `HANDOFF.md` |
| S3 | Automation green does not close live bugs. | Declaring closure from tests while render, targeting, or placement behavior is still live-red. | When live behavior disagrees with tests, read `docs/process/FALSE_GREEN_CHECKLIST.md`, create a symptom-specific RED proof, then patch one authority boundary. | `docs/porting/WYSIWYG-PLACEMENT-AUDIT.md`, `SLABBED_SPINE.md` |
| S4 | Porting needs an adapter map before code motion. | Copying sibling code without mapping version/API names, source-set differences, mixin registration, or proof gaps. | Read `docs/porting/PORTING_MAP.md`; identify donor, target surface, adapter differences, and proof gate before patching. | `docs/porting/mc-26.1.2-mapping-blocker.md`, `HANDOFF.md` |
| S5 | `/setblock` and old coordinates can lie. | Treating stale anchor/frozen/compound markers as current placement behavior. | Use fresh positions for live placement proof; separate command-created fixtures from player placement proof. | `HANDOFF.md`, `SLABBED_SPINE.md` |
| S6 | One authority boundary at a time. | Broad rescue patches that change geometry, anchors, model render, and placement intent together. | Patch the smallest owner of the symptom: geometry, placement intent, model/outline/raycast, or live harness. Stop after two failed attempts and audit. | `SLABBED_SPINE.md`, `docs/porting/WYSIWYG-PLACEMENT-AUDIT.md` |
| S7 | Visual triad proof is product proof. | Fixing model render while outline/raycast/collision still target the wrong place. | For render/targeting work, prove the model, outline, and raycast/interaction target agree; do not accept a single green visual. | `LIVE-DRIVE-GUIDE.md`, `docs/porting/mc-26.1.2-ghost-lowered-slab-collision-20260531.md` |
| S8 | Backports need explicit source truth. | Assuming 1.21.1, 1.21.11, and 26.1.2 have equivalent mechanisms because the symptom sounds the same. | Identify the shipped donor, the target mechanism, and whether the old fix is still the right fix before porting. | `docs/porting/PORTING_MAP.md`, `HANDOFF.md` |
| S9 | Every version/port runs the standard sanity gate. | Re-deriving the test surface from scratch each release, guessing what to check, missing a silently-regressed family, and "comparing versions" from memory. | Run `docs/process/RELEASE_SANITY_CHECKLIST.md` methodically (Lane 1 `runGameTest`+fingerprint GREEN → Lane 2 smoke → Lane 3 human). Compare versions by diffing the `SLABBED-FP` fingerprint capture, not by recollection. Mandatory on every version bump / release cut / port slice (RULES.md §19). | `docs/process/RELEASE_SANITY_CHECKLIST.md`, `src/gametest/resources/dy-baseline.txt`, `RULES.md` §19 |
| S10 | A gametest that `setBlock`s a survival-dependent block may be measuring AIR. | A "0.0 / flush" green that is actually the absence of the block: double-tall plants (and other blocks needing support/a valid pair) DESPAWN to air on a bare slab when placed via `helper.setBlock` (the neighbour update breaks them), so `getYOffset` reads air → 0.0. A whole "vegetation is flush on slabs" conclusion was wrong because of this. | Assert `getYOffset` on the EXPLICIT BlockState (not the world readback), or place with `Block.UPDATE_NONE` and confirm the block is still present before reading. Verify the fixture actually exists post-placement. The live case (TS sustains the plant) is faithfully modelled by the explicit-state read. | `src/gametest/.../Slabbed2612RestingDyTest.java` (`vegetation_lower_on_slab`), `docs/process/FALSE_GREEN_CHECKLIST.md`, `SLABBED_SPINE.md` 2026-06-19 |

## Add A Lesson Template

```text
ID:
Lesson:
Repeated failure it prevents:
Required action:
Evidence pointers:
```
