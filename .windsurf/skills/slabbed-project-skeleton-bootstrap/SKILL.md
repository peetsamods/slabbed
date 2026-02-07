---
name: slabbed-project-skeleton-bootstrap
description: Verify the Slabbed Fabric 1.21.11 project skeleton builds, runs, and is checkpointed safely.
---

# Slabbed — Project Skeleton Bootstrap (MC 1.21.11 / Fabric)

## Goal
Establish and verify a clean Fabric Loom project skeleton for **Slabbed** (MC **1.21.11**, Java **21**). No gameplay logic.

## Non-goals
- No placement logic
- No mixin injections
- No compat work
- No refactors beyond fixing build/metadata

## Required constants
- Mod ID: `slabbed` 
- Mod Name: `Slabbed` 
- Maven Group: `com.slabbed` 
- Entry point class: `com.slabbed.Slabbed` 
- Target MC: `1.21.11` 
- Loader: Fabric
- Java: 21
- License: MIT
- Mod version: `0.1.0-alpha` 
## Steps

### 0) Safety checkpoint
- `git status` must be clean before starting.
- If not clean: stop and report what’s dirty.

### 1) Verify minimum project files
Confirm these exist at repo root:
- `build.gradle` (or `build.gradle.kts`)
- `settings.gradle` (or `.kts`)
- `gradle.properties` 
- `gradlew`, `gradlew.bat` 
- `gradle/wrapper/gradle-wrapper.properties` and `.jar` 

### 2) Verify/normalize source tree
Ensure this exact layout exists (create if missing):
```

src/main/java/com/slabbed/
Slabbed.java
init/
mixin/

src/main/resources/
fabric.mod.json
slabbed.mixins.json
assets/slabbed/lang/en_us.json

```

### 3) Entrypoint class
File: `src/main/java/com/slabbed/Slabbed.java` 

Must:
- implement `net.fabricmc.api.ModInitializer` 
- log exactly once during init: `Slabbed initialized` 

Example structure (do not add more logic):
- `public final class Slabbed implements ModInitializer { ... }` 

### 4) fabric.mod.json correctness
File: `src/main/resources/fabric.mod.json` 

Must include:
- `"id": "slabbed"` 
- `"name": "Slabbed"` 
- `"version": "0.1.0-alpha"` 
- `"environment": "*"` 
- `"entrypoints": { "main": ["com.slabbed.Slabbed"] }` 
- `"mixins": ["slabbed.mixins.json"]` 
- `"depends"` includes:
  - `fabricloader` 
  - `fabric-api` 
  - `minecraft` pinned to `1.21.11` 

### 5) Mixin config stub
File: `src/main/resources/slabbed.mixins.json` 

Must:
- set `"package": "com.slabbed.mixin"` 
- have an empty `"mixins": []` list
- include the usual required keys (compatibilityLevel, required, etc.)
- no client/server mixins yet (empty lists acceptable)

### 6) Language file
File: `src/main/resources/assets/slabbed/lang/en_us.json` 

Allow either:
- minimal key:
```json
{ "mod.slabbed.name": "Slabbed" }
```

or empty JSON `{}` 

### 7) Build verification

Run:

* `./gradlew build` (or Windows equivalent)
* `./gradlew runClient` 

Pass conditions:

* build succeeds
* dev client reaches main menu
* no missing entrypoint / mixin / metadata errors

### 8) Git savepoint

If everything passes:

* `git add -A` 
* `git commit -m "chore: bootstrap Slabbed project skeleton"` 
* `git tag slabbed-bootstrap` 

Stop after tagging.

## Output report format

Return only:

* Build result (success/fail)
* Any files changed
* Commit hash + tag name

```
::contentReference[oaicite:0]{index=0}
```
