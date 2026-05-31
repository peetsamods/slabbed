# SLABBED Research Notes

Status: consolidated replacement for Research-First Rule, Slabbed Research, and Slabbed Lab v1 PDF.
Updated: 2026-05-04.

## Research-first rule

Before any new architecture, new category family, refactor that changes state law, or novel support behavior, perform external ecosystem research first.

Purpose:

- avoid reinventing failed approaches
- identify known engine limitations
- learn proven and avoided patterns
- ground design in modding history, not theory

Required sources:

1. Modrinth
2. CurseForge
3. GitHub source
4. Fabric / Yarn / Mojang documentation when relevant
5. known reference mods

Required output:

- prior art
- solution patterns used
- what those projects avoid
- known constraints/failure modes
- decision gate: direct precedent, adjacent precedent, or novel exploration

If no prior art exists, state:

```text
No known mod solves this directly; proceeding as novel exploration.
```

## Prior Art — Ceiling Attachments Under TOP/DOUBLE Slabs

Scope: allow vanilla ceiling-attached blocks to hang from underside of TOP or DOUBLE slabs.

Target examples:

- hanging signs
- chains
- lanterns when suspended
- other ceiling-attached vanilla blocks

Non-negotiables:

- no global slab solidity override
- preserve vanilla outside slab contexts
- prefer targeted block-specific logic
- bottom slabs are not valid ceiling support unless explicitly scoped later

### Key finding

No confirmed open-source mod explicitly enables vanilla hanging signs or chains to attach to underside of TOP/DOUBLE slabs.

However, strong adjacent prior art exists.

### TorchSlab

Strategy: replacement/fake blocks.

Relevance:

- proves slab-capable torch/lantern/chain behavior can be achieved with replacement blocks
- does not preserve vanilla blocks
- fallback only, not preferred Slabbed architecture

### Amendments

Strategy: per-block mixins plus renderer logic.

Useful patterns:

- hanging sign placement/survival/shape hooks
- lantern survival/placement overrides
- renderer alignment logic
- whitelisting non-standard supports such as fences/walls

Missing:

- no explicit slab-underside support found

### Supplementaries

Strategy: per-block overrides and visuals.

Useful patterns:

- wall-mounted lanterns
- hanging sign enhancements
- ceiling decoration logic
- special support allowlists

Missing:

- no slab-underside ceiling support found

### Common patterns

What works:

- targeted mixins into placement and survival methods
- explicit support allowlists
- survival separate from placement
- renderer alignment as separate concern

What is avoided:

- global slab solidity overrides
- broad `isSideSolid` rewrites
- ShapeCache mutation as general solution

Repeated barriers:

- multiple predicates for placement/survival/update
- slab undersides fail vanilla solid ceiling checks
- forced placement can still pop on survival

Decision gate:

```text
NOVEL EXPLORATION — PROCEED WITH CAUTION
```

## Slabbed Lab v1 Research

Goal: create repeatable, evidence-producing fixtures for Slabbed testing.

Highest-value v1:

```text
/slablab fixture basic
```

It should place a repeatable three-lane pad:

- full block lane
- bottom slab lane
- top slab lane

Preferred v1 implementation:

- Fabric command registration
- server world placement
- dev-gated command
- permission-gated invocation
- safe area scan before placing
- no gameplay changes
- no renderer/mixin rewrite
- no UI editor

Why not GameTest first:

GameTest is valuable for automation and evidence, but v1 can be lower risk as a command-based fixture. Add GameTest once fixture logic stabilizes.

## Slabbed Lab v1 design constraints

- one owner module for commands/fixtures
- dev-only registration
- server-side only for fixture placement
- no new dependencies unless justified
- fail safely if target area occupied
- output coordinates and lane IDs

Suggested files:

```text
src/main/java/.../dev/SlabbedLabCommands.java
src/main/java/.../dev/SlabbedLabFixtures.java
```

Command shape:

```text
/slablab fixture basic
```

Outputs:

- origin BlockPos
- footprint size
- lane mapping
- dev-only reminder

Validation:

```bash
./gradlew clean build
jar tf build/libs/*.jar | rg -n "(SlabbedLabCommands|SlabbedLabFixtures|slablab)"
```

Smoke test:

- command fails safely in occupied area
- command succeeds in empty area
- three lanes match expected geometry

## Research evaluation rubric

| Criterion | Good | Risk |
|---|---|---|
| Compatibility | same Fabric/Minecraft target | unstable internals or brittle mappings |
| Safety | dev-gated, minimal side effects | default behavior changes |
| Fidelity | real game loop/world | simulator diverges from Minecraft |
| Maintenance | few files, official APIs | bespoke framework or deep render injections |
| CI suitability | logs/screenshots/reports | manual-only proof |
| Evidence quality | repro artifacts | subjective “feels right” only |

## Future research topics

- ceiling attachment under top/double slabs
- redstone on lowered slab lanes
- rails on slab tops/lowered lanes
- Sodium/Indium render-path compatibility
- release jar purity automation
- multi-version runtime testing
