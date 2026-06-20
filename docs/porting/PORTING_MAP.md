# Slabbed Porting Map

Use this before moving behavior between Slabbed versions or backporting a fix. The purpose is to make the hidden adapter work explicit before code changes begin.

## Required Read Order

1. `AGENTS.md`
2. `HANDOFF.md`
3. `SLABBED_SPINE.md`
4. `docs/lessons/LESSONS_INDEX.md`
5. This file
6. The active blocker note under `docs/porting/*`
7. `docs/process/LIVE_DRIVE_PREFLIGHT.md` for live proof
8. `docs/process/FALSE_GREEN_CHECKLIST.md` when proof surfaces disagree

## Six-Line Port Card

```text
Objective:
Root/profile:
Allowed work:
Forbidden lanes:
Proof gate:
Stop condition:
```

For a port/backport, the card must name the donor version, target version, exact behavior being carried, and the proof that will close the slice.

## Known Lines

| Line | Role | Status for this checkout |
|---|---|---|
| `Slabbed-port-26.1.2` | Active MC 26.1.2 port target. | Mutate only after Git preflight and scope check. |
| `Slabbed-phase19-integrate` | 1.21.1 source/release reference. | Read-only unless Julia explicitly asks. |
| `Slabbed-countered-compat-latest` | 1.21.11/Terrain Slabs source reference. | Read-only unless Julia explicitly asks. |
| Modrinth `Fabric 26.1.2` | Non-Terrain-Slabs live profile. | Verify jar/profile before proof. |
| Modrinth `TEST_ SLABBED 26.1.2` | Terrain Slabs live profile. | Verify jar/profile before proof. |

## Adapter Checklist

| Surface | What to map before patching | Common failure |
|---|---|---|
| Toolchain/mappings | Java version, Gradle/Loom, Mojang vs Yarn names, method rename, descriptor changes. | Green dependency query mistaken for compile proof. |
| Source and mixin registration | Target file, mixin JSON, source set, package name, injected method descriptor. | Ported class exists but is never registered. |
| SlabSupport authority | Whether behavior belongs in geometric dy, anchor/persistence, placement intent, model render, outline, raycast, or collision. | One symptom patched in the wrong authority layer. |
| Terrain Slabs compat | Mod id, class/package names, direct custom support, skip guards, no-op path when TS is absent. | World-hole fixes break named TS lowering, or vice versa. |
| Placement intent | Hit face, hit Y, source dy, fresh position, client/server marker state. | Upper/lower aim bugs hidden by stale markers. |
| Proof harness | Gametest coverage, live A/B coverage, recorder coverage, screenshot/triad coverage. | Closing a live bug from headless-only proof. |
| Release/profile | Built jar, staged jar, profile mods list, duplicate jars, keybind state. | Testing the wrong jar or profile. |

## Port Loop

1. RED-verify the symptom on the target line when practical.
2. Read the shipped donor implementation and identify the exact mechanism, not just the file name.
3. Fill the adapter checklist for only the targeted behavior.
4. Patch one authority boundary.
5. Run the narrow proof named in the working card.
6. If proof and live behavior disagree, stop and use `docs/process/FALSE_GREEN_CHECKLIST.md`.
7. Record durable lessons here or in `docs/lessons/LESSONS_INDEX.md` only when the mistake is likely to repeat.

## Stop Rules

- Stop if the root, branch, profile, jar, or donor line is ambiguous.
- Stop if the active symptom changes.
- Stop after two failed patch attempts and run a read-only audit.
- Stop before broad parity sweeps unless Julia explicitly asks for that scope.

## Anticipated Problems and Risks

The 26.2 increment is likely to fail first on plumbing assumptions, not behavior logic. Use this section as a pre-mortem for each slice.

### 1) High-Risk Technical Surfaces

| Failure surface | Why it hurts this port | Hard symptom |
|---|---|---|
| Toolchain/mappings drift | 26.x dependency shifts can make the existing 26.1.2 baseline incompatible before behavior review | Compile errors before source diff review |
| Signature drift in Mojang-mapped APIs | Mixins and authority classes depend on exact method/field signatures | Mixin application failures, runtime injector crashes |
| `AGENTS.md` scope mismatch | Local canonical docs currently point to `/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2`, while we are on `/Users/joolmac/CascadeProjects/Slabbed` | Wrong preflight assumptions, ambiguous stop condition |
| Source-set/test drift | Gametests and baselines are 26.1.2-named (`Slabbed2612*`) and must be updated intentionally for 26.2 | Proof runs against mixed-version semantics |
| Profile/jar ambiguity | Wrong Modrinth profile or stale jar selection hides live regressions or false-green behavior | Duplicate jars, stale behavior evidence |
| Marker/state contamination | Stale placement anchors/freeze markers can make the same fixture behave differently across runs | Inconsistent dy, placement, or connector results |

### 2) Lessons carried from recent port runs

- Validate proofs on both layers: headless + live; never use only one lane to close placement/culling bugs.
- Do not equate dependency resolution with correctness. Dependency lookup must be followed by compile/proof of changed authority.
- Use fresh test coordinates for `/slabdy`/live fixture checks so stale markers or saved state do not mask behavior.
- Keep proof scoped to one symptom per slice; avoid broad parity sweeps unless they are explicitly requested.
- Record `Root/profile` explicitly before live actions, and stop immediately if root/profile/jar is unclear.
- Pause and update `SLABBED_SPINE.md` when operating truth changes (branch, blocker, proof frontier).

### 3) Risk Gate Checklist (quick, before touching source)

- Confirm target mappings/model stack before edits.
- Keep a single working card per slice with one narrow mechanism target.
- Verify branch/profile/jar and marker assumptions before any live-driving claim.
- Keep deferred/optional families outside the slice unless explicitly approved.
- Treat a green dry run as partial until the targeted lane passes its named proof command.
