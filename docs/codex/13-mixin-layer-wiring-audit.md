# 13 — Mixin/Event Layer Wiring Audit

Read this before touching targeting, outline, raycast, model-offset, or
survival/never-pop behavior on ANY port (new loader, new MC version, new
branch from a donor). It exists because this exact mistake cost an entire
session on the Forge 1.20.1 backport.

## The incident (2026-06-30)

The Forge 1.20.1 port had visible WYSIWYG breakage: aim at a fence, target
lands on the ground behind it; aim at A, place at B; break one block, a
neighbor pops to vanilla height. Many prior sessions treated each of these as
a separate narrow RED and patched it with new event-listener code
(`OffsetTargetingEvents` grew a brute-force rescue scan, retry guards, owner-
priority heuristics, etc.) — savepointing each narrow "fix" per the discipline
in `05-preflight-savepoint.md`.

The actual root cause was never one of those REDs. It was one fact, knowable
in five minutes by reading `build.gradle` and `mods.toml`:

```text
build.gradle sourceSets.main.resources excluded slabbed.mixins.json and
slabbed.client.mixins.json from the jar.
META-INF/mods.toml had zero [[mixins]] entries.
The mixin .java sources were not even in the compileJava include allowlist.
```

The ENTIRE proven mixin layer — including the already-written, already-
correct `GameRendererPickOffsetRaycastMixin` (the single offset-aware
nearest-hit crosshair authority ported from the donor) — was dead code. Every
session's narrow event-hack patch was reimplementing a worse version of a fix
that already existed in the tree but never ran. No amount of patching the
event hack could converge on WYSIWYG, because the event hack cannot win a
race against `GameRenderer.pick()` overwriting `client.hitResult` every
frame — only replacing that method's behavior (a mixin) can.

This was not subtle. It would have been caught immediately by the checklist
below.

## When to run this audit

Run it BEFORE writing a single line of new targeting/outline/survival code,
whenever any of these are true:

- This is a port to a new loader or MC version (Forge/NeoForge/Fabric, any
  version jump).
- The bug is "a proven Slabbed law (WYSIWYG, visual triad, never-pop) is
  violated" rather than "a specific new block family needs a new case."
- Two or more narrow fixes for the same symptom family have already failed
  or only partially worked (this is also `10-troubleshooting-when-stuck.md`'s
  trigger — treat "mixin layer wiring" as the FIRST thing the mandatory
  audit-only slice checks, not a late item).
- You are about to add a new `OffsetXxxEvents`/post-hoc retarget/rescue class
  and a structurally equivalent mixin already exists in the donor or in this
  repo's own `src/*/mixin/` tree.

## The audit (mechanical, ~10 minutes, no Java edits)

Run these in order. Stop at the first thing that's wrong — that is very
likely the actual root cause, not a downstream symptom.

### 1. Is the mixin layer reachable from the loader's entrypoint at all?

```bash
grep -n "mixins" src/main/resources/META-INF/mods.toml          # Forge
grep -n "MixinConfigs" src/main/resources/META-INF/MANIFEST.MF  # if present pre-build
grep -rn "Mixin" src/main/resources/fabric.mod.json              # Fabric
```

If the loader's descriptor does not list a `[[mixins]]` entry (Forge) or
`"mixins"` array (Fabric) for a config file that exists in the source tree,
**stop here**. Nothing in that config applies, full stop, regardless of how
correct the Java is.

### 2. Does the build actually package what the descriptor expects?

```bash
grep -n "exclude\|include" build.gradle
```

Check every `exclude` against every mixin config filename and every mixin
`.java` path referenced by those configs. A config can be registered in
`mods.toml` and still never load if `build.gradle` excludes it from
`resources{}`, or if its listed mixin classes are missing from the
`sourceSets.*.java.include` allowlist (Forge backports on this repo use a
deliberately narrow allowlist — see the comment in `build.gradle`; that
allowlist is a common place for a mixin to be silently dropped).

### 3. Does the proven fix already exist, dormant, in this tree or the donor?

```bash
ls src/*/mixin/ src/*/mixin/client/ 2>/dev/null
grep -rln "single ownership\|single crosshair\|the proven\|overhaul" docs/ src/*/util/*.java
```

If a class named like `*PickOffsetRaycastMixin`, `*OffsetRaycast`, or similar
already exists, read it before writing any new event-listener retarget code.
A dormant mixin that already encodes "the single ownership rule" (see
`docs/codex/03-visual-triad.md`) is almost always the correct fix; a new
event-hack class next to it is almost always a worse reimplementation. Check
memory/HANDOFF history for "never ported" — this exact trap has recurred
across multiple Slabbed ports (see prior incident notes referenced from
`SLABBED_SPINE.md`).

### 4. If the mixin layer IS wired, does the proven fix's selector still match this version?

`@Inject`/`@Redirect` `method=` strings are version-specific signatures
(e.g. `pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;`
on 1.21.1 vs `pick(F)V` on 1.20.1, because the donor and target MC versions
changed the method shape). Decompile the actual target method on the actual
target MC version and confirm the call site exists before assuming the
selector ports unchanged:

```bash
JAR=$(find ~/.gradle/caches -iname "*mapped_official*<mc_version>*.jar" | head -1)
unzip -o "$JAR" "<path/To/Target.class>" -d /tmp/check
javap -p -c /tmp/check/<path/To/Target.class> | grep -iE "<methodOfInterest>"
```

### 5. Once enabled, does it conflict with an ALREADY-ACTIVE compiled layer?

Before flipping a whole mixin config on, check whether the currently-compiled
(non-mixin) event/model path already performs the same offset the mixin would
add. Enabling a shape-offsetting mixin (`getShape`/`getInteractionShape`)
alongside an active event class that already moves the same `VoxelShape` by
`dy` produces a double-offset, not a fix — a different and harder-to-spot bug
than the one you started with. Identify which ONE layer owns each
responsibility (pick vs. outline vs. model vs. survival) before enabling
anything. See `docs/codex/03-visual-triad.md` for the triad-agreement
requirement this exists to protect.

### 6. After a green compile, verify the SHIPPED ARTIFACT, not just the build log

`BUILD SUCCESSFUL` proves compilation, not that the mixin applies at runtime,
and proves even less about production behavior. Two specific failure classes
that a green `compileJava`/`runGameTestServer` will NOT catch:

- **Stale/orphaned resource copies.** Gradle resource processing can leave a
  previous build's `build/resources/<sourceSet>/<config>.json` on disk under
  a sourceSet that no longer exists. `defaultRequire: 1` in a mixin config
  makes any listed-but-unreachable mixin class fatal. Always inspect what's
  actually in the jar, not what's on disk in `build/`:
  ```bash
  unzip -l build/libs/<jar> | grep -iE "mixin|refmap"
  unzip -p build/libs/<jar> <config>.json
  unzip -p build/libs/<jar> META-INF/MANIFEST.MF | grep -i Mixin
  ```
- **Missing refmap reference (dev-only false green).** MixinGradle may not
  auto-stamp the `"refmap"` field into a mixin config JSON during reobf on
  every ForgeGradle/MixinGradle version combination. The annotation
  processor still generates a correct `slabbed.refmap.json`, and `compileJava`
  and `runGameTestServer` still go green — **dev launch uses a live mapping
  service and applies mixins even with no refmap reference at all.** Only a
  *production* (SRG-obfuscated) launch needs the refmap, and if the config
  doesn't declare it, that launch crashes at `Minecraft.<init>` with
  `InvalidInjectionException: ... No refMap loaded.` This happened on the
  first staged jar for this exact fix. **Fix: hardcode
  `"refmap": "slabbed.refmap.json"` (or the project's refmap filename)
  directly in every mixin config JSON — do not rely on the Gradle plugin to
  stamp it.** Verify by grepping the shipped jar, not the source file:
  ```bash
  unzip -p build/libs/<jar> <config>.json | grep refmap
  ```
  Dev gametests cannot catch this class of bug. The only way to catch it
  pre-live is to inspect the packaged config, or to run a real production-
  mode launch (not `runClient`/`runGameTestServer`).

## What this means for narrow-slice discipline

`05-preflight-savepoint.md`'s narrow-slice-and-savepoint discipline is
correct and should not change. What changes is what counts as "the smallest
next slice" when the symptom is a violated universal law (WYSIWYG, visual
triad, never-pop) rather than a missing per-block-family case: the smallest
correct slice is the wiring audit above, BEFORE any new Java. A savepoint
that ships a new event-hack class while the proven mixin sits dead in the
tree is not a smaller slice — it is the same unsolved problem with more code
on top of it, and the next session inherits a harder-to-untangle codebase
plus the same RED.

## Quick self-check before writing new targeting/survival code

Ask, in order:

1. Did I confirm (not assume) the mixin/event layer is wired end-to-end for
   this loader/version, per steps 1-2 above?
2. Does a structurally equivalent fix already exist, dormant, per step 3?
3. If I'm enabling a previously-dead mixin, did I check it doesn't double up
   with an already-active layer, per step 5?
4. Did I inspect the actual shipped jar (not just the build log), per step 6?

If the answer to (1) is "I didn't check," stop and check it first. It is
almost always faster than the patch you were about to write.
