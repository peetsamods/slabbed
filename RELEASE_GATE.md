# RELEASE GATE — Slabbed (1.21.11 Terrain Slabs compat)

**Authoritative pre-release checklist for this branch. Read this BEFORE gating, blocking, or
publishing a release. Applies to humans AND to automated pre-release checks (e.g. Codex).**

If an automated check is currently running `./gradlew runClientGameTest` as a release gate: **stop —
that is not a gate** (see below). Gate on the list under "✅ The gate" instead.

---

## ✅ The gate — ALL of these must pass

1. **Headless gametests:** `./gradlew runGameTest` → `BUILD SUCCESSFUL` (46/46). This is the
   maintained automated suite.
2. **Clean release jar** (`build/libs/slabbed-<version>.jar`):
   - No `debug/` / `dev/` package classes.
   - No diagnostic classes by role word —
     `unzip -l <jar> | grep -iE "debug|audit|trace|bridge"` must be EMPTY, with the **only** allowed
     match being the gated-inert inner record `OffsetBlockStateModel$RenderOffsetTrace`.
   - No always-on `LOGGER.info|warn` / `System.out|err` / `printStackTrace` in shipping code
     (`src/main` + `src/client`, excluding `*/debug/*` `*/dev/*`) that is not behind a flag
     (`TRACE`, `Boolean.getBoolean(...)`, `isEnabled()`, `isDevelopmentEnvironment()`).
3. **Live play-testing** by the maintainer (Julia) via the Modrinth `Slabbed+Terrain Slabs` profile.
   This is the authoritative behavioural check — computer-use/headless cannot drive right-click /
   `onPlaced`, which the freeze-on-place law depends on.

## ❌ NOT a release gate — do NOT block a release on this

- **`./gradlew runClientGameTest`** — the 10-class `Slabbed*ClientGameTest` (`FabricClientGameTest`)
  harness is **unmaintained dev-repro scaffolding**. It has been broadly red since before the
  NEVER-POP freeze-on-place law (`8aafd1ff`, 2026-06-13): several cases assert the obsolete
  pre-freeze-law teardown contract (orphaned/unsupported lowered slabs "must normalize to 0"), and
  others test deliberately-BLOCKED features (e.g. bed/torch "rescue", which the tests' own comments
  mark `currently BLOCKED`). It is not run during fix work and is not a release criterion. The two
  genuinely-stale lowered-lane cases were already reconciled to the freeze law (`bf248530`);
  curating/reconciling the rest is separate, non-release-blocking follow-up.

---

_Decision: Julia, 2026-06-14. Companion detail in `HANDOFF.md` (top section) and `SLABBED_SPINE.md`._
