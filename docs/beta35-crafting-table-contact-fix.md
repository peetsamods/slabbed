# Beta 3.5 Crafting Table Contact Fix

Scope: ordinary full-block representative only, starting with `minecraft:crafting_table`.

No release-readiness audit was run. No release tag was moved. This is not an all-block or all-object support change.

## Previous Failure

The common-object compatibility audit at `9e1348c` classified `minecraft:crafting_table` as `CONTACT_GAP`.

Failed rows:

| Support row | Previous `supportDy` | Previous `objectDy` | Previous `contactGap` |
| --- | ---: | ---: | ---: |
| Plain bottom slab | `-0.500000` | `-0.500000` | `0.500000` |
| Lowered bottom slab | `-1.000000` | `-0.500000` | `1.000000` |

Placement and survival were already GREEN. The failure layer was contact alignment, not placement, survival, fence collision, trapdoor hinge behavior, door multipart behavior, or sign rendering.

## Fix

`SlabSupport` now has a constrained Beta 3.5 ordinary full-block contact dy path. It applies only to `CraftingTableBlock` and solid full-block `BlockEntityProvider` blocks, and only when the block is sitting on a bottom slab support whose visible top is already lowered by Slabbed support truth.

The contact formula is:

```text
objectDy = supportDy - 0.5
```

This makes the full block bottom align with the support slab visible top:

| Object | Support row | New `supportDy` | New `objectDy` | New `contactGap` | Classification |
| --- | --- | ---: | ---: | ---: | --- |
| `minecraft:crafting_table` | vanilla full block | `0.000000` | `0.000000` | `0.000000` | `GREEN_ALREADY_INHERITS` |
| `minecraft:crafting_table` | plain bottom slab | `-0.500000` | `-1.000000` | `0.000000` | `GREEN_ALREADY_INHERITS` |
| `minecraft:crafting_table` | lowered bottom slab | `-1.000000` | `-1.500000` | `0.000000` | `GREEN_ALREADY_INHERITS` |

## Matrix After Fix

`minecraft:furnace` inherited the same contact dy and now reports `contactGap=0.000000` on slab-supported rows, but it is not GREEN because the common-object matrix classifies those rows as `TRIAD_MISMATCH`.

`minecraft:oak_fence`, `minecraft:oak_trapdoor`, `minecraft:oak_door`, and standing `minecraft:oak_sign` remain separate categories:

| Representative | Current status |
| --- | --- |
| `minecraft:oak_fence` | `CONTACT_GAP` plus `COLLISION_SHAPE_RISK` |
| `minecraft:oak_trapdoor` | `CONTACT_GAP` plus `NEEDS_CATEGORY_SLICE` |
| `minecraft:oak_door` | `CONTACT_GAP` plus `MULTIPART_RISK` |
| `minecraft:oak_sign` | `CONTACT_GAP` plus `RENDERER_SPECIAL_CASE` |

## Validation

Passed:

- `./gradlew --no-daemon compileJava compileGametestJava`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CraftingTableContact=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FloorTopObjectFamilyAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`
- `./gradlew --no-daemon runClientGameTest --console plain`
- `git diff --check`

Evidence folder: `tmp/beta35-crafting-table-contact-fix-9e1348c`.

## Release Status

Release remains paused pending Julia decision.

Decision point:

- Stop here and release with documented limitations if `crafting_table` plus the existing floor/top green set is enough.
- Fix one more high-value category before release, likely furnace/block-entity triad parity or `oak_fence` collision/contact.
- Defer Beta 3.5 until the expanded common-object minimum set is green.
