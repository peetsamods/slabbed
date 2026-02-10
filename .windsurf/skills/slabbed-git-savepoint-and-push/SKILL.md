---
name: slabbed-git-savepoint-and-push
description: Build-gated savepoint commit with optional tag and push, enforcing Slabbed guardrails.
---

# Slabbed Git Savepoint and Push (build-gated)

## Preconditions (all required before commit)
- `git status` shows only intended files for this slice (one change per commit).
- `./gradlew` exists; on Windows use `./gradlew` or `PowerShell` form `./gradlew` as appropriate.
- `./gradlew clean build -x test` passes.
- Claimed behavior for this slice is verified. Do not commit unverified fixes.
- Debug-only tracers are disabled/removed unless intentionally committing on a `debug/*` branch.
- No tagging until the behavior is verified in-game and build-gated.

## Steps
1) Check working tree
   - Run `git status` to confirm only intended files are modified.
2) Build gate
   - Run `./gradlew clean build -x test` from repo root.
   - If it fails: stop, diagnose, fix, rerun until PASS.
3) Stage intentionally
   - Stage only the intended files for this slice (avoid unrelated churn).
4) Commit
   - Commit with a concise message for the single change slice.
5) Optional tag (only if verified and milestone-worthy)
   - Tag first-working/milestone only after verification; otherwise skip.
6) Optional push
   - Push commit (and tag if created).

## Notes
- One change per commit; avoid bundling multiple fixes/features.
- If debug logs were added for investigation, strip or guard them before committing unless on `debug/*`.
- Re-run build if you touch build scripts/mixins/mappings before committing.

## Quick commands (from repo root)
- Status: `git status`
- Build gate: `./gradlew clean build -x test`
- Stage (example): `git add <files>`
- Commit (example): `git commit -m "<message>"`
- Tag (optional): `git tag <tag>`
- Push (optional): `git push` (and `git push --tags` if tagged)

## Stop condition
- Build gate passed, intended files committed (and optionally tagged/pushed), no stray debug tracers, behavior verified.
