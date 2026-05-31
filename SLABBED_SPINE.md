# SLABBED_SPINE

This file is the current repo-local truth for Codex. Keep it short. Update it after every proof-confirmed or live-confirmed savepoint.

## Current active root

```text
/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate
```

Known alternate active port root when explicitly working MC 26.1.2:

```text
/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2
```

Stop if the actual root does not match the intended current root.

## Current branch

```text
port/mc-1.21.1
```

## Current known-good savepoint

Commit:

```text
eab0880a
```

Tag:

```text
save/mc1211-sbbs-underside-pre-manual-testing
```

Pushed branch: yes
Pushed tag: yes

## Current objective

The SBBS underside automation fixtures are saved and pushed. Keep the authority order current, preserve the savepoint, and prepare the next manual live acceptance rerun for the slab-held lowered-side rescue lane.

## Current blocker

Visible symptom:

```text
Manual slab-held live acceptance has not yet been re-proven after the savepoint.
```

Failing layer:

```text
proof gap
```

Protected invariant:

```text
The new SBBS markers must come from the manual runClient lane and not from gameplay changes.
```

Latest proof:

```text
compileGametest and runClientGameTest passed for the SBBS underside fixture update; commit and annotated tag were created and pushed.
```

Live status:

```text
manual rerun pending
```

## Next legal slice

Type:

```text
manual-proof
```

Allowed files:

```text
run/logs/latest.log, build/run/clientGameTest/logs/latest.log
```

Forbidden files:

```text
src/**, build.gradle, settings.gradle, gradle.properties, fabric.mod.json, *.mixins.json, release/version/changelog files
```

Required proof:

```text
Manual `runClient` pass with a slab in hand on the lowered-side rescue setup, followed by log classification from `run/logs/latest.log`.
```

Stop condition:

```text
Unexpected live lane mismatch, any gameplay edit, or any non-doc file touched.
```

## Do not touch boundaries

- Do not touch culling unless fresh RED says culling.
- Do not promote Terrain Slabs into generic Slabbed support.
- Do not broaden rescue without RED proof.
- Do not move release tags unless explicitly running release correction.
- Do not use dirty/archive roots unless recovery is explicitly requested.
- Do not edit multiple layers in one slice.
