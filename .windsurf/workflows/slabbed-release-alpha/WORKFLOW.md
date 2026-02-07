---
name: slabbed-release-alpha
description: Build, smoke test, and tag an alpha release jar for Slabbed with strict test and visual gates.
---

# Slabbed — Workflow: Release Alpha

## Goal
Produce a clean alpha release (jar) with confidence:
- strict tests pass
- strict visuals pass
- build is clean
- tag created for the release point

## Inputs
- Release version: `0.1.0-alpha` (or as decided)
- List of categories that must be supported for this alpha (explicit)

## Hard constraints
- No uncommitted changes
- No “maybe it’s fine” releases
- Strict visuals are mandatory
- If any gate fails: STOP and fix before releasing

---

## Step 0 — Safety checkpoint
1) `git status` must be clean. If not: STOP.
2) Confirm you’re on the intended branch (usually `main` or a release branch).
3) Pull latest (if applicable) and ensure no conflicts.
4) Run `./gradlew build`. If fail: STOP.

---

## Step 1 — Verify category pass tags exist
Confirm each required category has a pass tag:
- `slabbed-<category>-pass` 

If any are missing: STOP (do not release).

---

## Step 2 — Full repro sweep (all required categories)
Use: `slabbed-test-matrix-and-repro-world` 

For each required category, verify in all lanes:
- Full blocks lane: PASS
- Bottom slab lane: PASS
- Top slab lane: PASS
And triggers:
- neighbor update: PASS
- chunk unload/reload: PASS

If any FAIL: STOP.

---

## Step 3 — Visual audit sweep (all required categories)
Use: `slabbed-visual-alignment-audit` 

Must pass:
- placement height correct on slab tops
- outline/selection box correct
- interaction feel normal
- no visual shifting after triggers

If any FAIL: STOP.

---

## Step 4 — Version metadata check
Verify:
- `fabric.mod.json` version matches the release version
- mod id/name correct
- dependencies correct for MC 1.21.11
If changes are needed:
- make a single commit `chore: prepare alpha release metadata` 
- rebuild and re-run Step 0 build

---

## Step 5 — Build the release jar
Run:
- `./gradlew clean build` 

Locate the jar under:
- `build/libs/` 

Confirm:
- jar name contains `slabbed` and the version string
- no “dev” jar is used for release unless intended

---

## Step 6 — Smoke test in a clean client profile
Create a clean Fabric instance with:
- Fabric Loader for 1.21.11
- Fabric API
- Slabbed jar

Smoke test:
- game reaches main menu
- create a fresh world
- place at least 3 representative categories on bottom + top slabs
- confirm no crash, no obvious pop-off, visuals acceptable

If any issues: STOP.

---

## Step 7 — Release tag
Tag the release commit:
- `slabbed-0.1.0-alpha` 

Optional: also tag the jar build point if you keep separate tags.

---

## Step 8 — Output report (required)
- Release version:
- Required categories list:
- Tags confirmed:
- Build result:
- Jar path:
- Smoke test summary:
- Release tag:
- Commit hash:
