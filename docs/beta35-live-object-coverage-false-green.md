# Beta 3.5 live object coverage false-green (a891ba6)

Operating base: HEAD `a891ba6` / `save/beta35-fence-wall-model-render-fix` on `integrate/phase19-into-side-slab-top-support`.

Evidence folder: `tmp/beta35-live-object-coverage-false-green-a891ba6/`.

Source logs scanned (extracts in evidence folder):

- `run/logs/latest.log` — Julia's live runClient session, `gitHead=a891ba6` (`[SLABBED-INSPECT][SESSION] startedAt=2026-05-12T18:49:25.977062Z gitHead=a891ba6 inspect=true`).
- `build/run/clientGameTest/logs/latest.log` — gametest session (no fence model render markers; expected, those are gametest proof markers, not live inspect markers).

No video or zip from Julia is present in the repo or `tmp/`; only Julia's runClient live log is on disk.

## What Julia held in live test

Unique `heldItem=` values observed at `gitHead=a891ba6`:

- `minecraft:birch_fence` (4,305 occurrences as raycast target id; held in 269 retargeting-firing rows and 482 non-firing rows)
- `minecraft:birch_trapdoor` (2,372 target rows; 242 retarget-true / 177 retarget-false)
- `minecraft:spruce_door` (8,196 target rows; 93 retarget-true / 287 retarget-false)
- `minecraft:birch_sign` (971 target rows; 23 retarget-true / 31 retarget-false)
- `minecraft:anvil` (4,828 target rows; 828 retarget-true / 221 retarget-false)
- plus `stone_slab`, `stone`, `torch`, `stripped_jungle_log` as previously cleared support items.

Every one of the five held items produced inspect rows with `warning=BOTTOM_SLAB_UNEXPECTED_DY` on the targeted `stone_slab[type=bottom]`, and several produced `warning=TOP_SLAB_WITH_UNSUPPORTED_NEGATIVE_DY`.

## What the production allowlists actually cover at HEAD a891ba6

From `src/main/java/com/slabbed/util/SlabSupport.java`:

- `isBeta35FenceWallVariantContactObject(BlockState)` — exactly `OAK_FENCE`, `SPRUCE_FENCE`, `NETHER_BRICK_FENCE`, `COBBLESTONE_WALL`. `birch_fence` is **not in this allowlist**.
- `isBeta35OakTrapdoorContactObject(BlockState)` — exactly `OAK_TRAPDOOR` (`BLOCK_HALF=BOTTOM`). `birch_trapdoor` is **not in this allowlist**.
- `isBeta35OakDoorContactObject(BlockState)` — exactly `OAK_DOOR`. `spruce_door` is **not in this allowlist**.
- `isBeta35StandingOakSignContactObject(BlockState)` — exactly `OAK_SIGN`. `birch_sign` is **not in this allowlist**.
- `isBeta35SpecialFullblockContactObject(BlockState)` — includes `CRAFTING_TABLE`, `FURNACE`, `BOOKSHELF`, `CHEST`, `BARREL`, `ENCHANTING_TABLE`, `STONECUTTER`, `ANVIL`, `GRINDSTONE`. `anvil` **is in this allowlist** (contact-dy path).

From `src/client/java/com/slabbed/client/model/OffsetBlockStateModel.java`:

- `emitQuads` forces `dy = 0.0f` for every `FenceBlock | WallBlock | PaneBlock` **unless** `SlabSupport.isBeta35FenceWallVariantContactObject(state)` is true. So `birch_fence` is rendered at `dy = 0.0f` even when the underlying slab has a non-zero offset.

## Per-block classification

| Block tested live | Current support status at a891ba6 | Live evidence | Likely failure layer | Next action |
|---|---|---|---|---|
| `minecraft:birch_fence` | Not in `isBeta35FenceWallVariantContactObject`; render `dy` forced to `0.0f`; contact-dy path returns NaN. | `sideSlabRetargetFired=true` on slab; `warning=BOTTOM_SLAB_UNEXPECTED_DY`; held in 269 retargeting rows. | `EXACT_BLOCK_ALLOWLIST_GAP`, `VARIANT_FAMILY_COVERAGE_GAP`, `FENCE_RENDER_VARIANT_GAP`, `PROOF_HARNESS_UNDER_SCOPE` | Add a new RED proof for `birch_fence` (and at minimum the other wood fence variants) against the existing fence/wall shape-triad and render-quad gates; then extend allowlist + render fix to the explicitly proven wood-fence variants. |
| `minecraft:birch_trapdoor` | Not in `isBeta35OakTrapdoorContactObject`; only `OAK_TRAPDOOR` with `BLOCK_HALF=BOTTOM` is covered. | 242 retarget-true / 177 retarget-false rows; `warning=BOTTOM_SLAB_UNEXPECTED_DY`. | `TRAPDOOR_VARIANT_GAP`, `PROOF_HARNESS_UNDER_SCOPE` | Add a RED proof for `birch_trapdoor` (and other wood trapdoors) mirroring the oak trapdoor contact-dy slice. Do not yet bundle iron/copper trapdoors. |
| `minecraft:spruce_door` | Not in `isBeta35OakDoorContactObject`; only `OAK_DOOR` multipart is covered. | 93 retarget-true / 287 retarget-false rows; `warning=BOTTOM_SLAB_UNEXPECTED_DY`. | `DOOR_MULTIPART_VARIANT_GAP`, `PROOF_HARNESS_UNDER_SCOPE` | Add a RED proof for `spruce_door` mirroring the existing oak-door multipart contact slice (UPPER/LOWER halves on lowered bottom slab). |
| `minecraft:birch_sign` | Not in `isBeta35StandingOakSignContactObject`; only `OAK_SIGN` is covered. Wall signs and hanging signs are explicitly `NOT_COVERED` repo-wide. | 23 retarget-true / 31 retarget-false rows; `warning=BOTTOM_SLAB_UNEXPECTED_DY`. | `SIGN_RENDERER_GAP`, `VARIANT_FAMILY_COVERAGE_GAP`, `PROOF_HARNESS_UNDER_SCOPE` | Add a RED proof for standing `birch_sign` on lowered bottom slab mirroring the oak standing-sign contact slice. Do not yet add wall/hanging signs. |
| `minecraft:anvil` | In `isBeta35SpecialFullblockContactObject`. Render dy uses `SlabSupport.getYOffset` directly (no fence/wall/pane gate). | 828 retarget-true rows; `warning=BOTTOM_SLAB_UNEXPECTED_DY` and `TOP_SLAB_WITH_UNSUPPORTED_NEGATIVE_DY`. | `LIVE_PLACEMENT_TARGETING_GAP` (held-item-agnostic side-slab retargeting); needs confirmation that the actual placement result is broken vs. the inspect warning being benign. | Capture an explicit `[PLACEMENT]` event row at the moment of anvil click, not only `[TARGET]` raycast rows. Confirm whether the placed anvil actually lands on the lowered slab with `contactGap=0` (in which case the live red is inspect-only), or whether the placement actually fails. Do not change anvil allowlist yet. |

## Overall false-green mechanism

The Beta 3.5 fence/wall model render fix at `a891ba6` is **valid for its exact allowlist** (`oak_fence`, `spruce_fence`, `nether_brick_fence`, `cobblestone_wall`) and the matching shape-triad allowlist. It resolves the `MODEL_RENDER_GAP` on those four representatives. It did **not** widen the underlying allowlists, and the proof harness (`runBeta35FenceModelRenderRedProof`, `runBeta35FenceFamilyLiveRedProof`, `runBeta35FenceWallVariantCoverageProof`, `runBeta35CommonObjectCompatibilityAuditProof`) only iterates representative blocks per category, not whole variant families.

The new live false-green has three reinforcing causes:

1. **EXACT_BLOCK_ALLOWLIST_GAP** — production code treats each category as a narrow named-block list (`OAK_FENCE`/`SPRUCE_FENCE`/`NETHER_BRICK_FENCE`/`COBBLESTONE_WALL`; `OAK_TRAPDOOR`; `OAK_DOOR`; `OAK_SIGN`). Variants in the same logical family (`BIRCH_FENCE`, `BIRCH_TRAPDOOR`, `SPRUCE_DOOR`, `BIRCH_SIGN`) are not covered.
2. **PROOF_HARNESS_UNDER_SCOPE** — gametest proofs iterate the representative chosen for each category, not the full family. Variant blocks like `BIRCH_FENCE`/`BIRCH_TRAPDOOR`/`SPRUCE_DOOR`/`BIRCH_SIGN` are never under test, so the current GREEN gates cannot reproduce Julia's live red.
3. **LIVE_PLACEMENT_TARGETING_GAP** (anvil specifically) — `sideSlabRetargetFired` fires on slab top regardless of the held-item placement intent. For allowlisted special-fullblocks like `anvil`, this may be benign or may be the actual live red; the current inspect data shows the warning but does not show the post-placement triad.

## Release impact

- **Beta 3.5 release remains BLOCKED.**
- The `a891ba6` fence model render fix is **not rescinded**: it is correct for its declared allowlist and the proof gates remain GREEN for that allowlist.
- The Beta 3.5 object compatibility claim cannot honestly be called release-ready for live play. Current proofs are under-scoped relative to player expectations.
- No release audit was run for this slice. No release tag was moved. No production gameplay code was edited.

## Next recommended slice order

Smallest next-step RED proofs that capture Julia's actual complaint, in priority order. Each slice should add a single narrow named-block RED before any allowlist expansion.

1. `birch_fence` lowered bottom-slab contact + render-quad RED — mirrors the existing oak/spruce/nether-brick/cobblestone wall fence-family proof, on a wood-fence variant that is in current vanilla content but **not** in the proven allowlist. Smallest amplification of the existing fence proof gate.
2. `birch_trapdoor` (bottom half) contact RED — mirrors the existing oak-trapdoor slice.
3. `spruce_door` multipart contact RED — mirrors the existing oak-door multipart slice.
4. `birch_sign` standing contact RED — mirrors the existing oak-sign slice.
5. Anvil placement-event capture — confirm whether `LIVE_PLACEMENT_TARGETING_GAP` is real for already-allowlisted blocks, or whether the inspect warning is benign.

Do **not** in this slice:

- bundle all fence/wall/trapdoor/door/sign variants globally,
- add `birch_fence` (or any other variant) support before its own RED proof exists,
- touch panes, hanging signs, wall signs, lanterns, chains, redstone, or rails,
- run the Beta 3.5 release audit,
- move any release tag.

## Status flags

- `productionFixImplemented=false`
- `productionGameplayCodeChanged=false`
- `releaseAudit=NOT_RUN`
- `releaseTagMoved=false`
- `canonicalCheckoutModified=false`
- `betaThreeFiveRelease=BLOCKED`
- `currentProofHarnessScope=EXACT_REPRESENTATIVE_ONLY`
- `liveFailureMechanism=EXACT_BLOCK_ALLOWLIST_GAP+PROOF_HARNESS_UNDER_SCOPE (+LIVE_PLACEMENT_TARGETING_GAP for already-allowlisted special-fullblocks)`
