# Beta 3.5 Common Object Compatibility Audit

## Scope

Audit-only representative matrix for Julia's expanded Beta 3.5 common-object scope.

Operating base: `e8f5fdb` / `save/beta35-scoped-release-readiness-audit`

Gate: `-Dslabbed.beta35CommonObjectCompatibilityAudit=true`

Evidence folder: `tmp/beta35-common-object-compatibility-audit-e8f5fdb`

No production behavior fix was implemented. No release audit was run. No release tag was moved.

## Current Proven Green Set

- `minecraft:torch` / floor torch
- `minecraft:candle`
- `minecraft:flower_pot`
- `minecraft:crafting_table`
- `minecraft:furnace`
- `minecraft:oak_fence`

These remain green on the audited slab-supported rows:

- `PLAIN_BOTTOM_DY_MINUS_HALF`
- `LOWERED_BOTTOM_DY_MINUS_ONE`

## Validation

- `./gradlew --no-daemon compileJava compileGametestJava`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35CommonObjectCompatibilityAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `JAVA_TOOL_OPTIONS="-Dslabbed.beta35FloorTopObjectFamilyAudit=true" ./gradlew --no-daemon runClientGameTest --console plain`: PASS
- `./gradlew --no-daemon runClientGameTest --console plain`: PASS

## Matrix Summary

Summary marker:

Original audit summary marker:

`JULIA_BETA35_COMMON_OBJECT_SUMMARY rows=27 greenAlreadyInherits=11 placementFailure=0 survivalFailure=0 contactGap=12 triadMismatch=0 collisionShapeRisk=1 multipartRisk=1 rendererSpecialCase=1 ceilingAttachmentRisk=0 outOfScopeForBeta35=0 needsCategorySlice=1 lantern=NOT_AUDITED_CEILING_HANGING_CATEGORY chain=NOT_AUDITED_CEILING_HANGING_CATEGORY redstone_wire=NOT_AUDITED_SPECIAL_FLOOR_LOGIC rail=NOT_AUDITED_SPECIAL_FLOOR_LOGIC releaseAudit=NOT_RUN releasePrep=PAUSED`

Post crafting-table contact fix summary marker:

`JULIA_BETA35_COMMON_OBJECT_SUMMARY rows=27 greenAlreadyInherits=13 placementFailure=0 survivalFailure=0 contactGap=8 triadMismatch=2 collisionShapeRisk=1 multipartRisk=1 rendererSpecialCase=1 ceilingAttachmentRisk=0 outOfScopeForBeta35=0 needsCategorySlice=1 lantern=NOT_AUDITED_CEILING_HANGING_CATEGORY chain=NOT_AUDITED_CEILING_HANGING_CATEGORY redstone_wire=NOT_AUDITED_SPECIAL_FLOOR_LOGIC rail=NOT_AUDITED_SPECIAL_FLOOR_LOGIC releaseAudit=NOT_RUN releasePrep=PAUSED`

Post furnace triad fix summary marker:

`JULIA_BETA35_COMMON_OBJECT_SUMMARY rows=27 greenAlreadyInherits=15 placementFailure=0 survivalFailure=0 contactGap=8 triadMismatch=0 collisionShapeRisk=1 multipartRisk=1 rendererSpecialCase=1 ceilingAttachmentRisk=0 outOfScopeForBeta35=0 needsCategorySlice=1 lantern=NOT_AUDITED_CEILING_HANGING_CATEGORY chain=NOT_AUDITED_CEILING_HANGING_CATEGORY redstone_wire=NOT_AUDITED_SPECIAL_FLOOR_LOGIC rail=NOT_AUDITED_SPECIAL_FLOOR_LOGIC releaseAudit=NOT_RUN releasePrep=PAUSED`

Post oak-fence partial-collision contact fix summary marker:

`JULIA_BETA35_COMMON_OBJECT_SUMMARY rows=27 greenAlreadyInherits=18 placementFailure=0 survivalFailure=0 contactGap=6 triadMismatch=0 collisionShapeRisk=0 multipartRisk=1 rendererSpecialCase=1 ceilingAttachmentRisk=0 outOfScopeForBeta35=0 needsCategorySlice=1 lantern=NOT_AUDITED_CEILING_HANGING_CATEGORY chain=NOT_AUDITED_CEILING_HANGING_CATEGORY redstone_wire=NOT_AUDITED_SPECIAL_FLOOR_LOGIC rail=NOT_AUDITED_SPECIAL_FLOOR_LOGIC releaseAudit=NOT_RUN releasePrep=PAUSED`

Post oak-trapdoor contact fix summary marker:

`JULIA_BETA35_COMMON_OBJECT_SUMMARY rows=27 greenAlreadyInherits=21 placementFailure=0 survivalFailure=0 contactGap=4 triadMismatch=0 collisionShapeRisk=0 multipartRisk=1 rendererSpecialCase=1 ceilingAttachmentRisk=0 outOfScopeForBeta35=0 needsCategorySlice=0 lantern=NOT_AUDITED_CEILING_HANGING_CATEGORY chain=NOT_AUDITED_CEILING_HANGING_CATEGORY redstone_wire=NOT_AUDITED_SPECIAL_FLOOR_LOGIC rail=NOT_AUDITED_SPECIAL_FLOOR_LOGIC releaseAudit=NOT_RUN releasePrep=PAUSED`

| Representative | Family | Vanilla full block | Plain bottom slab `supportDy=-0.5` | Lowered bottom slab `supportDy=-1.0` | Classification |
| --- | --- | --- | --- | --- | --- |
| `minecraft:torch` | floor/top decor control | GREEN | GREEN, `contactGap=0.000000` | GREEN, `contactGap=0.000000` | `GREEN_ALREADY_INHERITS` |
| `minecraft:candle` | floor/top decor control | GREEN | GREEN, `contactGap=0.000000` | GREEN, `contactGap=0.000000` | `GREEN_ALREADY_INHERITS` |
| `minecraft:flower_pot` | floor/top decor control | GREEN | GREEN, `contactGap=0.000000` | GREEN, `contactGap=0.000000` | `GREEN_ALREADY_INHERITS` |
| `minecraft:crafting_table` | ordinary full block | GREEN | GREEN, `contactGap=0.000000` | GREEN, `contactGap=0.000000` | `GREEN_ALREADY_INHERITS` |
| `minecraft:furnace` | ordinary full block | GREEN | GREEN, `contactGap=0.000000`, `triadCoLocated=yes` | GREEN, `contactGap=0.000000`, `triadCoLocated=yes` | `GREEN_ALREADY_INHERITS` |
| `minecraft:oak_fence` | partial collision block | GREEN | GREEN, `contactGap=0.000000`, `triadCoLocated=yes`, `collisionCoLocated=yes` | GREEN, `contactGap=0.000000`, `triadCoLocated=yes`, `collisionCoLocated=yes` | `GREEN_ALREADY_INHERITS` for oak fence only |
| `minecraft:oak_trapdoor` | attachment / hinge block | GREEN | GREEN, `contactGap=0.000000`, `triadCoLocated=yes`, `collisionCoLocated=yes` | GREEN, `contactGap=0.000000`, `triadCoLocated=yes`, `collisionCoLocated=yes` | `GREEN_ALREADY_INHERITS` for oak trapdoor only |
| `minecraft:oak_door` | multipart block | GREEN control | GREEN, `contactGap=0.000000`, `triadCoLocated=yes`, `collisionCoLocated=yes` | GREEN, `contactGap=0.000000`, `triadCoLocated=yes`, `collisionCoLocated=yes` | `GREEN_ALREADY_INHERITS` for oak door only |
| `minecraft:oak_sign` | renderer / block entity standing sign | `RENDERER_SPECIAL_CASE` | `CONTACT_GAP=0.500000` | `CONTACT_GAP=1.000000` | `CONTACT_GAP` plus renderer risk |

## Grouped Findings

The expanded matrix is not release-green for common objects beyond the current floor/top decor controls.

All required representatives placed on the audited rows. No row produced `PLACEMENT_FAILURE` or `SURVIVAL_FAILURE`.

The shared slab-supported failure layer is contact alignment:

- `crafting_table` is now GREEN for the ordinary-full-block representative contact slice. It places and survives on vanilla, plain bottom slab support, and lowered bottom slab support; slab-supported rows now report `contactGap=0.000000` and `triadCoLocated=yes`.
- `furnace` is now GREEN for the ordinary-full-block sibling triad slice. It inherited the contact dy (`contactGap=0.000000`) and now reports matching model, outline, and raycast bounds on slab-supported rows.
- `oak_fence` is now GREEN for the single oak-fence partial-collision representative. It uses oak-fence-only contact dy plus lowered outline/raycast/collision shape alignment; no all-fence, wall, pane, trapdoor, door, sign, or global sturdy-face/solidity behavior was added.
- `oak_trapdoor` is now GREEN for the single bottom-half oak-trapdoor interactive hinge representative. It uses oak-trapdoor-only contact dy plus an outline-backed raycast basis for lowered bottom-half trapdoors with empty vanilla raycast. Open/close remained GREEN. This does not claim all trapdoors.
- `oak_door` is now GREEN for the single oak-door multipart representative. It uses oak-door-only bottom/top contact dy plus an outline-backed raycast basis for lowered oak-door halves with empty vanilla raycast. Bottom/top halves remain coherent and open/close remained GREEN. This does not claim all doors.
- renderer-special representatives still show the slab-supported contact-gap pattern before their family-specific risks can honestly be called green.
- standing `oak_sign` remains the known renderer/block-entity special case.

Ceiling/hanging and special floor-logic categories were not audited in this slice:

- `minecraft:lantern`: `NOT_AUDITED_CEILING_HANGING_CATEGORY`
- `minecraft:chain`: `NOT_AUDITED_CEILING_HANGING_CATEGORY`
- `minecraft:redstone_wire`: `NOT_AUDITED_SPECIAL_FLOOR_LOGIC`
- `minecraft:rail`: `NOT_AUDITED_SPECIAL_FLOOR_LOGIC`

## Release Decision Point

A. Stop here and release with documented object limitations:

Not recommended unless Julia accepts that Beta 3.5 only promises `floor_torch + candle + flower_pot` and explicitly excludes ordinary full blocks, fences, trapdoors, doors, standing signs, ceiling/hanging objects, rails, and redstone.

B. Fix one more high-value family before release:

The ordinary full-block representatives (`minecraft:crafting_table` and `minecraft:furnace`), the single partial-collision representative (`minecraft:oak_fence`), the single interactive hinge representative (`minecraft:oak_trapdoor`), and the single multipart representative (`minecraft:oak_door`) are now fixed. Recommended next implementation category, if Julia wants one more common-object family, is an explicitly chosen remaining category slice such as standing sign. Do not bundle signs, hanging objects, rails, redstone, all doors, or all-item support.

C. Defer Beta 3.5 release until common object matrix minimum set is green:

Required if Julia wants expanded common-object compatibility to be a Beta 3.5 release criterion.

## Status

Release remains paused pending Julia decision.

Post-fix status: `minecraft:crafting_table` and `minecraft:furnace` are GREEN ordinary full-block representatives; `minecraft:oak_fence` is GREEN for the oak-fence-only partial-collision representative; `minecraft:oak_trapdoor` is GREEN for the oak-trapdoor-only bottom-half interactive hinge representative; `minecraft:oak_door` is GREEN for the oak-door-only multipart representative; standing `minecraft:oak_sign` remains a separate category. Release remains paused pending Julia decision.

## Special Fullblock Anvil Follow-up

The anvil slice at `9f3bacf` / `save/beta35-special-fullblock-helper-consolidation` did not change this common-object matrix. The matrix remains PASS with unchanged summary: `rows=27 greenAlreadyInherits=21 placementFailure=0 survivalFailure=0 contactGap=4 triadMismatch=0 collisionShapeRisk=0 multipartRisk=1 rendererSpecialCase=1 ceilingAttachmentRisk=0 outOfScopeForBeta35=0 needsCategorySlice=0`.

Special-fullblock status changed only for `minecraft:anvil`: focused anvil proof and the special-fullblock matrix classify it GREEN on valid slab-supported rows. This is not new common-object support, not grindstone support, and not lectern support.

No release audit was run. No release tag was moved. Canonical checkout was not modified. Door/trapdoor/sign/lantern/chain/end-rod/redstone/rail implementation was not touched.

## Special Fullblock Grindstone Follow-up

The grindstone slice at `805b070` / `save/beta35-anvil-contact` did not change this common-object matrix. The matrix remains PASS with unchanged summary: `rows=27 greenAlreadyInherits=21 placementFailure=0 survivalFailure=0 contactGap=4 triadMismatch=0 collisionShapeRisk=0 multipartRisk=1 rendererSpecialCase=1 ceilingAttachmentRisk=0 outOfScopeForBeta35=0 needsCategorySlice=0`.

Special-fullblock status changed only for `minecraft:grindstone`: focused grindstone proof and the special-fullblock matrix classify it GREEN on valid slab-supported rows, with tested state `face=floor,facing=south`, `contactGap=0.000000`, `triadCoLocated=yes`, and co-located model/outline/raycast/collision bounds. This is not new common-object support, not lectern support, and not door/trapdoor/sign/lantern/chain/end-rod/redstone/rail support.

No release audit was run. No release tag was moved. Canonical checkout was not modified.

## Trapdoor / Door Category Audit Follow-up

Follow-up audit at `f88afb7` / `save/beta35-oak-fence-contact-integrated` added `-Dslabbed.beta35TrapdoorDoorAudit=true` with no production behavior changes.

At audit time, `minecraft:oak_trapdoor` placed and survived on slab-supported rows, and open/close interaction was exercised successfully, but slab-supported rows reported `CONTACT_GAP` (`0.500000` on plain bottom support, `1.000000` on lowered bottom support). Exact failure layer was `TRAPDOOR_CONTACT_GAP`. This historical audit result is superseded by the oak trapdoor contact fix follow-up below.

`minecraft:oak_door` places both bottom and top halves and both halves survive, with matching `bottomDy`/`topDy` on audited rows. Slab-supported rows remain `CONTACT_GAP` (`0.500000` plain bottom, `1.000000` lowered bottom) and the category remains multipart-risky. Exact failure layer: `DOOR_MULTIPART_CONTACT_GAP`. Recommended status: defer door unless Julia authorizes a separate multipart slice.

At audit time, the green set was `minecraft:torch`, `minecraft:candle`, `minecraft:flower_pot`, `minecraft:crafting_table`, `minecraft:furnace`, and `minecraft:oak_fence`. No release audit ran. No release tag moved. Signs, lanterns, chains, redstone, and rails were not touched.

## Oak Trapdoor Contact Fix Follow-up

Follow-up implementation at `2300229` / `save/beta35-trapdoor-door-category-audit` added `-Dslabbed.beta35OakTrapdoorContact=true` for `minecraft:oak_trapdoor` only.

Focused proof reports `failureLayer=NONE` for valid slab-supported bottom-half oak trapdoor rows. Plain bottom slab support and lowered bottom slab support both report `contactGap=0.000000`, `triadCoLocated=yes`, collision bounds co-located, and `openCloseResult=Success->Success`.

This historical `minecraft:oak_door` status is superseded by the oak door multipart contact fix follow-up below. Signs, lanterns, chains, redstone, and rails remain not covered by the trapdoor slice. No release audit ran. No release tag moved.

Current green set is now `minecraft:torch`, `minecraft:candle`, `minecraft:flower_pot`, `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:oak_fence`, and `minecraft:oak_trapdoor`.

## Oak Door Multipart Contact Fix Follow-up

Follow-up implementation at `d6c10d8` / `save/beta35-oak-trapdoor-contact` added `-Dslabbed.beta35OakDoorContact=true` for `minecraft:oak_door` only.

Focused proof reports `failureLayer=NONE` for valid slab-supported oak-door multipart rows. Plain bottom slab support and lowered bottom slab support both report `bottomContactGap=0.000000`, `topAlignment=GREEN`, `triadCoLocated=yes`, collision bounds co-located, and `openCloseResult=Success->Success`.

`minecraft:oak_trapdoor` remains GREEN. Signs, lanterns, chains, redstone, and rails remain not covered by this slice. No release audit ran. No release tag moved.

Current green set is now `minecraft:torch`, `minecraft:candle`, `minecraft:flower_pot`, `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:oak_fence`, `minecraft:oak_trapdoor`, and `minecraft:oak_door`.

## Standing Oak Sign Contact Fix Follow-up

Follow-up implementation at `dc1076c` / `save/beta35-oak-door-contact` added `-Dslabbed.beta35StandingOakSignContact=true` for standing `minecraft:oak_sign` only.

Previous standing sign status was `CONTACT_GAP` plus `RENDERER_SPECIAL_CASE`. The first focused attempt proved contact was fixed but exposed `STANDING_OAK_SIGN_TRIAD_MISMATCH` because slab-supported standing sign raycast bounds were empty. The final fix keeps the contact dy path exact to `Blocks.OAK_SIGN` and adds only an outline-backed raycast fallback for lowered standing oak signs when vanilla raycast is empty.

Focused proof reports `failureLayer=NONE`: standing oak sign placement and survival are GREEN, `blockEntityPresent=true`, slab-supported rows report `contactGap=0.000000`, and model/render proxy, outline, and raycast bounds are co-located. Renderer offset remains through the existing `BlockEntityOffsetMixin`.

Updated matrix summary is now `rows=27 greenAlreadyInherits=27 placementFailure=0 survivalFailure=0 contactGap=0 triadMismatch=0 collisionShapeRisk=0 multipartRisk=0 rendererSpecialCase=0`.

Current green set is now `minecraft:torch`, `minecraft:candle`, `minecraft:flower_pot`, `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:oak_fence`, `minecraft:oak_trapdoor`, `minecraft:oak_door`, and standing `minecraft:oak_sign`. Wall signs, hanging signs, lanterns, chains, redstone, and rails remain not covered by this slice. No release audit ran. No release tag moved.
## Special Fullblock Compatibility Audit Follow-up

Follow-up audit at `d6c10d8` / `save/beta35-oak-trapdoor-contact` was run in the separate worktree `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree` on branch `work/beta35-special-fullblock-compat`.

New gated audit: `-Dslabbed.beta35SpecialFullblockCompatibilityAudit=true`; markers `JULIA_BETA35_SPECIAL_FULLBLOCK_MATRIX_START`, `JULIA_BETA35_SPECIAL_FULLBLOCK_ROW`, and `JULIA_BETA35_SPECIAL_FULLBLOCK_SUMMARY`.

Summary: `rows=30 greenAlreadyInherits=7 placementFailure=0 survivalFailure=0 contactGap=14 triadMismatch=2 blockEntityRisk=2 specialRendererRisk=2 needsCategorySlice=3 outOfScopeForBeta35=0`.

Controls remained GREEN: `minecraft:crafting_table` and `minecraft:furnace`. New ordinary-full-block sibling `minecraft:bookshelf` places and survives but shows slab-supported `CONTACT_GAP` (`0.500000` plain bottom, `1.000000` lowered bottom). Special/block-entity rows need separate category handling: `minecraft:enchanting_table`, `minecraft:lectern`, `minecraft:barrel`, and `minecraft:chest`. Optional special-shape rows `minecraft:stonecutter`, `minecraft:grindstone`, and `minecraft:anvil` were audited cheaply and classified as category slices.

No production behavior fix was implemented. No release audit was run. No release tag was moved. Door/trapdoor/sign/lantern/chain/end-rod/redstone/rail implementation was not touched.

Recommended next implementation slice, if Julia authorizes one: `minecraft:bookshelf` ordinary-full-block contact/dy proof and fix only.

## Bookshelf Contact Fix Follow-up

Follow-up implementation at `e0da848` / `save/beta35-special-fullblock-compatibility-audit` was run in the separate worktree `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree` on branch `work/beta35-special-fullblock-compat`.

Focused gate: `-Dslabbed.beta35BookshelfContact=true`; markers `JULIA_BETA35_BOOKSHELF_CONTACT_GREEN` and `JULIA_BETA35_BOOKSHELF_CONTACT_SUMMARY`.

`minecraft:bookshelf` is now GREEN for the ordinary-full-block contact representative. Slab-supported rows report `contactGap=0.000000`, `triadCoLocated=yes`, placement GREEN, and survival GREEN. The fix is exact `Blocks.BOOKSHELF` contact/dy only via `SlabSupport`; it does not implement enchanting table, lectern, chest, barrel, stonecutter, grindstone, anvil, door, trapdoor, signs, lanterns, chains, end rods, redstone, or rails.

Special-fullblock matrix follow-up now reports `greenAlreadyInherits=9`, `contactGap=12`, `triadMismatch=2`, `blockEntityRisk=2`, `specialRendererRisk=2`, and `needsCategorySlice=3`. Controls `minecraft:crafting_table` and `minecraft:furnace` remain GREEN. `minecraft:enchanting_table`, `minecraft:lectern`, `minecraft:chest`, `minecraft:stonecutter`, `minecraft:grindstone`, and `minecraft:anvil` remain contact-gap/category rows; `minecraft:barrel` remains a triad category row.

Focused common-object matrix remains passing for this worktree and preserves its existing door/sign classifications (`contactGap=4`, `multipartRisk=1`, `rendererSpecialCase=1`). No release audit ran. No release tag moved. Canonical checkout was not modified.

## Stonecutter Contact Fix Follow-up

Follow-up implementation at `99b03ed` / `save/beta35-enchanting-table-contact` was run in the separate worktree `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree` on branch `work/beta35-special-fullblock-compat`.

Focused gate: `-Dslabbed.beta35StonecutterContact=true`; markers `JULIA_BETA35_STONECUTTER_CONTACT_GREEN` and `JULIA_BETA35_STONECUTTER_CONTACT_SUMMARY`.

`minecraft:stonecutter` is now GREEN for the focused special-shape contact representative. Slab-supported rows report `contactGap=0.000000`, `triadCoLocated=yes`, placement GREEN, and survival GREEN. The fix is exact `Blocks.STONECUTTER` contact/dy plus a stonecutter-only lowered raycast fallback to the lowered outline when vanilla stonecutter raycast is empty; it does not implement lectern, grindstone, anvil, door, trapdoor, signs, lanterns, chains, end rods, redstone, or rails.

Special-fullblock matrix follow-up now reports `greenAlreadyInherits=21`, `contactGap=6`, `triadMismatch=0`, `blockEntityRisk=1`, `specialRendererRisk=0`, and `needsCategorySlice=2`. Controls `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:bookshelf`, `minecraft:chest`, `minecraft:barrel`, and `minecraft:enchanting_table` remain GREEN. `minecraft:lectern` remains an interactive block-entity contact row; `minecraft:grindstone` and `minecraft:anvil` remain special-shape category rows.

Focused common-object matrix remains passing for this worktree and preserves its existing door/sign classifications (`contactGap=4`, `multipartRisk=1`, `rendererSpecialCase=1`). No release audit ran. No release tag moved. Canonical checkout was not modified.

## Special Fullblock Helper Consolidation Follow-up

Follow-up implementation at `d854e2b` / `save/beta35-stonecutter-contact` was run in the separate worktree `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree` on branch `work/beta35-special-fullblock-compat`.

Consolidation performed: yes. `SlabSupport` now uses explicit already-green Beta 3.5 special-fullblock contact helpers, and `SlabSupportStateMixin` now uses a separate explicit raycast fallback helper for the already-proven empty-native-raycast representatives.

No new common-object support was added. The common-object matrix remains unchanged: `rows=27 greenAlreadyInherits=21 placementFailure=0 survivalFailure=0 contactGap=4 triadMismatch=0 collisionShapeRisk=0 multipartRisk=1 rendererSpecialCase=1 ceilingAttachmentRisk=0 outOfScopeForBeta35=0 needsCategorySlice=0`.

Door and standing sign classifications are unchanged. Lanterns, chains, redstone, and rails remain not covered. No release audit ran. No release tag moved. Canonical checkout was not modified.

## Enchanting Table Contact Fix Follow-up

Follow-up implementation at `e46cd26` / `save/beta35-barrel-triad` was run in the separate worktree `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree` on branch `work/beta35-special-fullblock-compat`.

Focused gate: `-Dslabbed.beta35EnchantingTableContact=true`; markers `JULIA_BETA35_ENCHANTING_TABLE_CONTACT_GREEN` and `JULIA_BETA35_ENCHANTING_TABLE_CONTACT_SUMMARY`.

`minecraft:enchanting_table` is now GREEN for the focused special-renderer contact representative. Slab-supported rows report `contactGap=0.000000`, `triadCoLocated=yes`, `blockEntityPresent=true`, placement GREEN, and survival GREEN. The fix is exact `Blocks.ENCHANTING_TABLE` contact/dy plus an enchanting-table-only lowered raycast fallback to the lowered outline when vanilla enchanting-table raycast is empty; it does not implement lectern, stonecutter, grindstone, anvil, door, trapdoor, signs, lanterns, chains, end rods, redstone, or rails.

Special-fullblock matrix follow-up now reports `greenAlreadyInherits=18`, `contactGap=8`, `triadMismatch=0`, `blockEntityRisk=1`, `specialRendererRisk=0`, and `needsCategorySlice=3`. Controls `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:bookshelf`, `minecraft:chest`, and `minecraft:barrel` remain GREEN. `minecraft:lectern` remains an interactive block-entity contact row; `minecraft:stonecutter`, `minecraft:grindstone`, and `minecraft:anvil` remain special-shape category rows.

Focused common-object matrix remains passing for this worktree and preserves its existing door/sign classifications (`contactGap=4`, `multipartRisk=1`, `rendererSpecialCase=1`). No release audit ran. No release tag moved. Canonical checkout was not modified.

## Barrel Triad Fix Follow-up

Follow-up implementation at `0ee0ab3` / `save/beta35-chest-contact` was run in the separate worktree `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree` on branch `work/beta35-special-fullblock-compat`.

Focused gate: `-Dslabbed.beta35BarrelTriad=true`; markers `JULIA_BETA35_BARREL_TRIAD_GREEN` and `JULIA_BETA35_BARREL_TRIAD_SUMMARY`.

`minecraft:barrel` is now GREEN for the focused block-entity fullblock triad representative. Slab-supported rows report `contactGap=0.000000`, `triadCoLocated=yes`, `blockEntityPresent=true`, placement GREEN, and survival GREEN. The fix is a barrel-only lowered raycast fallback to the lowered outline when vanilla barrel raycast is empty; it does not implement enchanting table, lectern, stonecutter, grindstone, anvil, door, trapdoor, signs, lanterns, chains, end rods, redstone, or rails.

Special-fullblock matrix follow-up now reports `greenAlreadyInherits=15`, `contactGap=10`, `triadMismatch=0`, `blockEntityRisk=1`, `specialRendererRisk=1`, and `needsCategorySlice=3`. Controls `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:bookshelf`, and `minecraft:chest` remain GREEN. `minecraft:enchanting_table`, `minecraft:lectern`, `minecraft:stonecutter`, `minecraft:grindstone`, and `minecraft:anvil` remain contact-gap/category rows.

Focused common-object matrix remains passing for this worktree and preserves its existing door/sign classifications (`contactGap=4`, `multipartRisk=1`, `rendererSpecialCase=1`). No release audit ran. No release tag moved. Canonical checkout was not modified.

## Chest Contact Fix Follow-up

Follow-up implementation at `baf09f0` / `save/beta35-bookshelf-contact` was run in the separate worktree `/Users/joolmac/CascadeProjects/Slabbed-beta35-special-fullblock-worktree` on branch `work/beta35-special-fullblock-compat`.

Focused gate: `-Dslabbed.beta35ChestContact=true`; markers `JULIA_BETA35_CHEST_CONTACT_GREEN` and `JULIA_BETA35_CHEST_CONTACT_SUMMARY`.

`minecraft:chest` is now GREEN for the focused block-entity fullblock contact representative. Slab-supported rows report `contactGap=0.000000`, `triadCoLocated=yes`, `blockEntityPresent=true`, placement GREEN, and survival GREEN. The fix is exact `Blocks.CHEST` contact/dy plus a chest-only lowered raycast fallback to the lowered outline; it does not implement barrel, enchanting table, lectern, stonecutter, grindstone, anvil, door, trapdoor, signs, lanterns, chains, end rods, redstone, or rails.

Special-fullblock matrix follow-up now reports `greenAlreadyInherits=12`, `contactGap=10`, `triadMismatch=2`, `blockEntityRisk=2`, `specialRendererRisk=1`, and `needsCategorySlice=3`. Controls `minecraft:crafting_table`, `minecraft:furnace`, and `minecraft:bookshelf` remain GREEN. `minecraft:barrel` remains a triad category row; `minecraft:enchanting_table`, `minecraft:lectern`, `minecraft:stonecutter`, `minecraft:grindstone`, and `minecraft:anvil` remain contact-gap/category rows.

Focused common-object matrix remains passing for this worktree and preserves its existing door/sign classifications (`contactGap=4`, `multipartRisk=1`, `rendererSpecialCase=1`). No release audit ran. No release tag moved. Canonical checkout was not modified.

## Special Fullblock Integration Follow-up

The proven special-fullblock work from `work/beta35-special-fullblock-compat` through `348ac65` / `save/beta35-grindstone-contact` has been integrated into canonical `integrate/phase19-into-side-slab-top-support`.

Current special-fullblock GREEN set: `minecraft:crafting_table`, `minecraft:furnace`, `minecraft:bookshelf`, `minecraft:chest`, `minecraft:barrel`, `minecraft:enchanting_table`, `minecraft:stonecutter`, `minecraft:anvil`, and `minecraft:grindstone`.

`minecraft:lectern` remains open / not fixed as an interactive block-entity contact slice. No release audit was run. No release tag was moved.
