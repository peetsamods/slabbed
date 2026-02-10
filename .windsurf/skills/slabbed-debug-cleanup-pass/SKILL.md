---
name: slabbed-debug-cleanup-pass
description: Disable or remove all temporary debug instrumentation (debug mixins, debug helpers, and log spam) and verify a clean production run.
---

# Goal
Perform a “debug cleanup pass” so production builds ship without debug mixins, debug spam, or debug-only helpers.

# Hard stops
- Working tree not clean at start.
- Any mixin apply error after cleanup.
- Build fails after cleanup.

# Steps
1) Inventory debug artifacts
   - Locate debug code folders, typically:
     - src/main/java/com/slabbed/debug/
     - src/main/java/com/slabbed/mixin/debug/
     - any debug accessor packages
   - Locate debug mixin json entries in:
     - src/main/resources/slabbed.mixins.json

2) Choose mode (default to SAFE-KEEP mode)
   SAFE-KEEP mode:
   - Keep debug sources, but make them disabled by default.
   - Ensure slabbed.mixins.json does not load debug mixins by default.
   - If you want optional debug, put debug mixins into a separate config file.

   DELETE mode (only if explicitly requested):
   - Delete debug sources and remove all references.

3) SAFE-KEEP mode implementation
   - Create new mixin config:
     - src/main/resources/slabbed.debug.mixins.json
   - Move all debug mixin entries from slabbed.mixins.json into slabbed.debug.mixins.json
   - Ensure fabric.mod.json does NOT reference slabbed.debug.mixins.json

4) Verify no debug prints remain
   - Grep for:
     - "[slabbed][" prefixes used by debug tracers
     - "STDOUT" patterns from System.out.println
     - LOGGER lines marked debug-only

5) Build + runtime smoke
   - ./gradlew clean build
   - ./gradlew runClient (brief smoke)
   - Validate logs: no debug spam, no mixin errors

6) Output report
   - List files moved/changed
   - Confirm debug mixins are not loaded by default
   - Confirm build + runClient pass
