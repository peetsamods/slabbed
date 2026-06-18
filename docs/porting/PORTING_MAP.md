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
