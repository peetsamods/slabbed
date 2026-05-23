# SLABBED_SPINE.md - Slabbed 26.1.2 Port Spine

This is the active operating spine for the dedicated Slabbed MC 26.1.2 port checkout. It is local to this tree and is not the phase19 Slabbed spine.

Use it to know the current root, branch, HEAD, base tag, port blocker, proof state, and next safe step.

## Read Order

1. `AGENTS.md`
2. `SLABBED_SPINE.md`
3. Relevant `docs/porting/*` notes for the active blocker

## Canonical Port Root

```text
/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2
```

Do not update other Slabbed checkouts from this file. The phase19 checkout remains a separate source/release line unless Julia explicitly asks to sync something back.

## Current Git Truth

- Branch: `port/mc-26.1.2`
- HEAD: `f9014fb`
- Tag at HEAD: `release/0.2.0-beta.4`
- Base release provenance: `release/0.2.0-beta.4` / `f9014fbfcb15af2716f090d038762fd8d3d460de`
- Current tracked tree: dirty with ongoing 26.1.2 port migration edits
- Current untracked evidence/docs include `docs/porting/` and `tmp/`

This is not a release-ready tree and not a savepoint unless a later commit/tag/push proves it.

## Current Port Status

The 26.1.2 port is an experimental migration workspace. Work so far has focused on release-base provenance, mapping/tooling/classpath proof, Java 25 / Gradle / Loom compatibility, source-set wiring, and narrow source API probes.

The preserved blocker note is:

```text
docs/porting/mc-26.1.2-mapping-blocker.md
```

That note records the original mapping-provider/tooling provenance stop. Later local port forensics indicated the live `compileJava` classpath is Mojang-style rather than Yarn-style, so current source migration should be treated as Mojang-style API drift unless fresh evidence proves otherwise.

## Current Operating Rules

- Keep work local to `/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2`.
- Do not edit `/Users/joolmac/CascadeProjects/Slabbed-phase19-integrate` from this port slice.
- Preserve unrelated dirty source migration work.
- Use grep/classpath/compiler evidence before changing code.
- Prefer one-file mechanical probes for source API drift.
- Run `compileJava` only when the slice calls for it, and interpret success narrowly.
- Do not treat `buildEnvironment` success as compile success.
- Do not make gameplay, release, or broad compatibility claims from this port tree.

## Known Port Evidence

- The dedicated port branch was bootstrapped from the released base `release/0.2.0-beta.4`.
- Java 25 is the known runtime family used for 26.1.2 port proof.
- Gradle 9.4.x and Fabric Loom experiments have been part of the tooling path.
- Broad cache searches are not authoritative for namespace decisions; exact task classpath proof is preferred.
- Optional compatibility families may be deferred when they are not core port scope, but the defer must be explicit and narrow.
- For one-file source probes, success means the target file leaves the compile error stream after one compile gate, not that the full project is ported.

## Current Stop Conditions

Stop and report if:

- the root is not `/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2`
- the requested slice would edit another Slabbed checkout
- two focused attempts fail
- compile evidence points to a different dominant blocker family
- a proposed change widens from mechanical API migration into gameplay behavior
- a savepoint/release claim is being made without commit, annotated tag, branch push, tag push, and clean tracked tree

## Next Safe Action

For documentation-only work, keep edits limited to port-local docs.

For code migration work, classify the current dominant compile/source blocker first, then patch exactly the requested file or the smallest proven mechanism. If the result remains unclear after one compile gate, stop with tried/observed/proven/unproven/next-smallest-audit.
