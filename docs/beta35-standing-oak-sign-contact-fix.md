# Beta 3.5 Standing Oak Sign Contact Fix

Scope: standing `minecraft:oak_sign` only.

Operating base: `dc1076c` / `save/beta35-oak-door-contact`

Gate: `-Dslabbed.beta35StandingOakSignContact=true`

Evidence folder: `tmp/beta35-standing-oak-sign-contact-fix-dc1076c`

## Previous Status

The common-object matrix classified standing `minecraft:oak_sign` as `CONTACT_GAP` plus `RENDERER_SPECIAL_CASE`.

First focused implementation attempt fixed contact dy but exposed the exact remaining layer:

`JULIA_BETA35_STANDING_OAK_SIGN_CONTACT_SUMMARY failureLayer=STANDING_OAK_SIGN_TRIAD_MISMATCH`

The failing rows had placement and survival GREEN, `blockEntityPresent=true`, `contactGap=0.000000`, lowered outline/render proxy bounds, and empty raycast bounds.

## Fix

`SlabSupport.getYOffsetInner(...)` now has an exact `Blocks.OAK_SIGN` contact dy rule over valid lowered bottom slab support truth.

`SlabSupportStateMixin` now gives lowered standing oak signs an outline-backed raycast basis when vanilla raycast is empty.

The renderer path was not rewritten. Standing sign block entities continue to render through the existing `BlockEntityOffsetMixin`, which consumes the same `SlabSupport.getYOffset(...)` value.

## Result

Focused proof is GREEN:

`JULIA_BETA35_STANDING_OAK_SIGN_CONTACT_SUMMARY failureLayer=NONE objectId=minecraft:oak_sign family=standing_sign rows=3 expectedRowsGreen=true rendererPath=BlockEntityOffsetMixin`

Slab-supported sign rows report:

- placement GREEN
- survival GREEN
- `blockEntityPresent=true`
- `contactGap=0.000000`
- model/render proxy, outline, and raycast bounds co-located

Common-object matrix is GREEN:

`JULIA_BETA35_COMMON_OBJECT_SUMMARY rows=27 greenAlreadyInherits=27 placementFailure=0 survivalFailure=0 contactGap=0 triadMismatch=0 collisionShapeRisk=0 multipartRisk=0 rendererSpecialCase=0`

## Boundaries

Not touched: wall signs, hanging signs, lanterns, chains, redstone, rails, all-sign support, all-block-entity support, all-object support, release metadata, release tags.

No release audit ran. No release tag moved.

Validation passed:

- `./gradlew --no-daemon compileJava compileGametestJava`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35StandingOakSignContact=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35TrapdoorDoorAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `./gradlew --no-daemon runClientGameTest --console plain`
- `git diff --check`
