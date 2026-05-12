# Beta 3.5 Furnace Triad Fix

Scope: ordinary full-block sibling triad only, represented by `minecraft:furnace`.

No release-readiness audit was run. No release tag was moved. This is not fence, trapdoor, door, sign, hanging, rail, redstone, all-block, or all-object support.

## Previous Failure

After the crafting table contact fix, `minecraft:furnace` inherited the ordinary full-block contact dy and reported `contactGap=0.000000` on slab-supported rows, but the common-object matrix still classified those rows as `TRIAD_MISMATCH`.

The failure layer was raycast parity: the furnace model/outline used the lowered dy, while the native furnace raycast basis could be empty instead of matching the lowered full-cube outline.

## Fix

`SlabSupportStateMixin` now lets `minecraft:furnace` use the lowered full-block raycast basis when its resolved dy is negative and the native raycast shape is empty. The dy authority remains `SlabSupport`; model, outline, and raycast now share the same lowered bounds.

This is a narrow furnace allowlist in the existing lowered full-block raycast-basis path. It does not change global slab solidity, sturdy-face truth, placement law, or any fence/trapdoor/door/sign behavior.

## Proof

Focused gate:

```text
JAVA_TOOL_OPTIONS="-Dslabbed.beta35FurnaceTriad=true" ./gradlew --no-daemon runClientGameTest --console plain
```

Result marker:

```text
JULIA_BETA35_FURNACE_TRIAD_SUMMARY failureLayer=NONE objectId=minecraft:furnace rows=3 expectedRowsGreen=true
```

Rows:

| Support row | `supportDy` | `objectDy` | `contactGap` | Triad | Classification |
| --- | ---: | ---: | ---: | --- | --- |
| Vanilla full block | `0.000000` | `0.000000` | `0.000000` | GREEN | `GREEN_ALREADY_INHERITS` |
| Plain bottom slab | `-0.500000` | `-1.000000` | `0.000000` | GREEN | `GREEN_ALREADY_INHERITS` |
| Lowered bottom slab | `-1.000000` | `-1.500000` | `0.000000` | GREEN | `GREEN_ALREADY_INHERITS` |

The common-object matrix now reports `minecraft:furnace` as GREEN on the ordinary full-block rows. `minecraft:crafting_table` remains GREEN.

## Still Separate

- `minecraft:oak_fence`: `CONTACT_GAP` plus `COLLISION_SHAPE_RISK`
- `minecraft:oak_trapdoor`: `CONTACT_GAP` plus `NEEDS_CATEGORY_SLICE`
- `minecraft:oak_door`: `CONTACT_GAP` plus `MULTIPART_RISK`
- standing `minecraft:oak_sign`: `CONTACT_GAP` plus `RENDERER_SPECIAL_CASE`

## Validation

Passed:

- `./gradlew --no-daemon compileJava compileGametestJava`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FurnaceTriad=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CraftingTableContact=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FloorTopObjectFamilyAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `./gradlew --no-daemon runClientGameTest --console plain`
- `git diff --check`

Evidence folder: `tmp/beta35-furnace-triad-fix-3712a37`.

## Release Status

Release remains paused pending Julia decision on whether the ordinary full-block representatives plus the existing floor/top green set are enough, or whether fence, trapdoor, door, sign, or other categories must be handled before Beta 3.5.
