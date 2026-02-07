---
name: slabbed-savepoint-discipline
description: Enforce tiny-diff commits, clean builds, and tagged savepoints during Slabbed development.
---

# Slabbed — Savepoint Discipline (Tiny Diffs, Always Recoverable)

## Goal
Keep Slabbed development safe and reversible:
- one change per commit
- always runnable
- tags for milestones
- never “mystery breakage”

## Core rules (non-negotiable)
1) One variable per commit.
2) Build must pass before commit.
3) Tag every milestone and every “first working” behavior.
4) If you’re unsure, stop and report instead of guessing.


## Start-of-work checklist
Before any edits:
- `git status` must be clean
- record current branch name
- record current HEAD hash
- confirm `./gradlew build` currently passes (skip if already verified in last 10 minutes with no changes)

If any fail: stop and report.


## Commit sizing
A “normal” commit should:
- touch ≤ 3 files (unless it’s generated metadata updates)
- contain ≤ ~150 lines changed
- implement exactly one narrow goal

If it exceeds these, split into multiple commits.


## Mandatory pre-commit checks
Before every commit:
- `./gradlew build` 
- if mixins changed: run client once (`./gradlew runClient`) until main menu
- confirm no new warnings about mixin targets / refmap / access wideners


## Tagging convention
Use lightweight tags.

### Milestone tags
- `slabbed-bootstrap` (already used)
- `slabbed-first-mixin-hook` 
- `slabbed-first-placement-pass` 
- `slabbed-first-survival-pass` 
- `slabbed-alpha-0.1.0` 

### Debug tags (temporary)
- `tmp/<short-desc>` e.g. `tmp/dust-canplaceat` 

Delete tmp tags later if desired.


## Commit message convention
Use these prefixes:
- `chore:` setup, formatting, build files
- `feat:` new behavior
- `fix:` bug fix
- `test:` test infrastructure/world notes
- `docs:` readme/changelog

Examples:
- `feat: allow floor torches on slab tops` 
- `fix: prevent redstone dust popping off on neighbor update` 
- `chore: add slab support helper predicates` 


## Rollback protocol
If something breaks:
1) Do not keep editing randomly.
2) Identify last known good tag/hash.
3) `git reset --hard <tag>` 
4) Re-run `./gradlew build` to confirm.
5) Re-apply changes in smaller pieces.


## Output report format (required)
After every unit of work, report:
- What changed (1 sentence)
- Files touched
- Build status (pass/fail)
- Commit hash + tag (if created)

No extra commentary.


## Stop conditions
- Build fails and you cannot fix in 1–2 focused edits → stop and report.
- Any mixin “no targets” / mapping mismatch appears → stop and run `slabbed-mixin-target-discovery` next.
