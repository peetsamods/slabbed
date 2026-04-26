---
name: slabbed-toggle-debug-mixins
description: Temporarily enable or disable debug mixins via mixin config selection without touching production mixin lists.
---

# Goal
Enable debug mixins for one investigation, then disable them again without risk of shipping them.

# Hard stops
- Debug mixins referenced by fabric.mod.json in production.
- Debug mixins left enabled after investigation.

# Steps
1) Ensure debug mixins live in:
   - src/main/resources/slabbed.debug.mixins.json
2) Enable for local investigation ONLY by one of:
   - Add debug mixin json to your dev run config (preferred), OR
   - Temporarily reference it in fabric.mod.json, then immediately revert
3) Run:
   - ./gradlew runClient
4) After investigation:
   - Remove debug mixin enablement
   - Confirm fabric.mod.json references only production mixins
   - Build gate: ./gradlew build
