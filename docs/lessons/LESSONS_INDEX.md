# Slabbed Lessons Index

Short durable lessons for repeated Slabbed release and port mistakes.

| ID | Lesson | Required action | Evidence pointers |
|---|---|---|---|
| S1 | Root/profile drift is expensive. | Run Git preflight and verify branch-local client/profile/jar before live proof. | `AGENTS.md`, `SLABBED_SPINE.md` |
| S2 | Automation green does not close live bugs. | Use live `/slabdy`, visual, or feel proof for render, targeting, and placement behavior. | `docs/process/RELEASE_SANITY_CHECKLIST.md` |
| S3 | Every version/port runs the standard sanity gate. | Run `docs/process/RELEASE_SANITY_CHECKLIST.md`; compare versions with `SLABBED-FP` fingerprint diffs. | `src/gametest/resources/dy-baseline.txt` |
| S4 | Live RED status beats stale handoff language. | When Julia says a symptom is still RED, update spine/handoff/current-red before more implementation. | `SLABBED_SPINE.md`, `HANDOFF.md`, `tmp/current-red.md` |
