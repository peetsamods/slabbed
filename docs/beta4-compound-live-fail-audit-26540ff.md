# Beta4 Compound Live Failure Audit at 26540ff

## Live report from Julia

Julia retested `26540ff` and reported the same beta4 live failures:

- flashing and failure to place on the lower half
- upper-half placement goes above/upward
- breaking the block/source causes a jump

This is a false-green live failure case: automation at `26540ff` is green for the compound matrix rows that the live session contradicts.

## Automation status at 26540ff

Expected HEAD/tag for this audit:

- HEAD: `26540ff`
- tag at HEAD: `save/beta4-compound-matrix-closure`
- matrix status: `[BETA4_COMPOUND_CONTRACT_MATRIX_RED] rows=12 red=0 undecided=0 green=11 notImplemented=1`
- Row 4: lower-half ordinary stone side placement is expected to land at `dy=-1.0` with `compoundFullBlockAnchor=true`
- Row 5: upper-half ordinary stone side placement is expected to land at `dy=-1.0`, with no upward placement
- Row 6/7: slab side placement is expected to reject cleanly, with no ghost slab lane
- Row 9/10: source-slab break is expected to preserve `dy=-1.0`

The copied `clientGameTest` log in the uploaded evidence did not include the compound matrix marker itself, but `SLABBED_SPINE.md` records the matrix closure status above at `26540ff`.

## Evidence files inspected

Audit folder:

- `tmp/beta4-compound-live-fail-audit-26540ff/preflight-status.txt`
- `tmp/beta4-compound-live-fail-audit-26540ff/run_logs_latest_log.copy.log`
- `tmp/beta4-compound-live-fail-audit-26540ff/run_logs_latest_log.extract.txt`
- `tmp/beta4-compound-live-fail-audit-26540ff/build_run_clientGameTest_logs_latest_log.copy.log`
- `tmp/beta4-compound-live-fail-audit-26540ff/build_run_clientGameTest_logs_latest_log.extract.txt`
- `tmp/beta4-compound-live-fail-audit-26540ff/quick-summary.txt`

Uploaded/extracted source evidence was present at `tmp/beta4-live-retest-26540ff/`.

## Live recorder marker availability

| marker family | live availability | notes |
| --- | --- | --- |
| placement author recorder | present | `[BETA4_PLACEMENT_AUTHOR_RECORDER_START]`, `[BETA4_PLACEMENT_AUTHOR_RECORDER]`, and `[BETA4_PLACEMENT_AUTHOR_AFTER_TICK]` all appear in the live log. |
| reload recorder | present | `[BETA4_RELOAD_JUMP_RECORDER_START]` and repeated `[BETA4_RELOAD_JUMP_RECORDER]` lines appear. |
| outline recorder | present | `[BETA4_OUTLINE_RECORDER_START]` and repeated `[BETA4_OUTLINE_RECORDER]` lines appear. |
| row/matrix markers | absent from live log | No live `[BETA4_COMPOUND_CONTRACT_MATRIX...]` row proof markers were present. |
| `compoundFullBlockAnchor=true` text | absent from live log | The live recorder records `persistentFullBlockAnchor`, `dy`, and `sourceMode`, but not the compound sidecar boolean. |

The live session identifies itself as `gitHead=26540ff` in `[SLABBED-INSPECT][SESSION]`.

## Live facts table

| action | clickedPos | clickedFace | hitVec | heldItem | target/support state | dy | compoundFullBlockAnchor | persistentFullBlockAnchor | finalPlacedPos | final state | post-tick state | failure classification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ordinary stone side placement, lower visual hit | `14,-57,0` | `west` | `14.000,-57.776,0.512` | `minecraft:stone` | `Block{minecraft:stone}` source, `sourceMode=dynamicLoweredOrAnchored` | clicked source `-1.0`; placed finalization `-0.5` | not emitted | clicked source `true`; placed after finalization `true` | `13,-57,0` | `Block{minecraft:stone}` | server rejects packet; side slot does not survive authoritatively | Row 4 false green. Live packet validity and finalization differ from matrix. |
| ordinary stone repeat | `14,-57,0` | `west` | `14.000,-57.776,0.512` | `minecraft:stone` | same source | clicked source `-1.0`; placed finalization `-0.5` | not emitted | clicked source `true`; placed `true` | `13,-57,0` | `Block{minecraft:stone}` | server rejects packet again | Repeat confirms the lower-half flicker route. |
| slab side placement, lower/top visual route | `14,-57,0` | `west` | first `14.000,-57.776,0.512`, then placement hit `14.000,-56.501,0.512` | `minecraft:stone_slab` | `Block{minecraft:stone}` source at `dy=-1.0` | placed `-0.5` | not emitted | clicked source `true`; placed slab `false` | `13,-57,0` | bottom or top `minecraft:stone_slab` | server rejects early lower route; later server accepts top slab with `placedDy=-0.5` | Rows 6/7 false green. Live does not cleanly reject the slab route. |
| source/reload observation | center around `14,-58,0` / `14,-59,0` / `14,-57,0` | mostly `up`/crosshair | recorder-driven | `minecraft:stone_slab` | bottom slab carrier at `14,-58,0`, lowered full block below at `14,-59,0`, stone above at `14,-57,0` | carrier `-0.5`, below stone `-0.5`, above stone `-1.0` | not emitted | below/above stones `true` | n/a | mixed live source truth | not enough break-event edge data to prove exact break relation | Rows 9/10 remain live-red, but the recorder does not yet isolate the exact source-break relation. |

## Live vs matrix

| matrix row | automation claim at `26540ff` | live evidence |
| --- | --- | --- |
| Row 4 lower-half stone | side placement lands `dy=-1.0` with `compoundFullBlockAnchor=true` | live clicked a `dy=-1.0` source, but finalization produced placed `dy=-0.5`, and the server emitted `Rejecting UseItemOnPacket ... too far away from hit block BlockPos{x=14, y=-57, z=0}`. |
| Row 5 upper-half stone | side placement lands `dy=-1.0`, no upward placement | live placement recorder shows the same source family producing final placed `dy=-0.5`; Julia reports upward/above behavior. |
| Row 6/7 slab side | slab placement cleanly rejects with no ghost slab lane | live slab-held placement produces bottom/top `minecraft:stone_slab` states at `dy=-0.5`; finalization skips carrier authoring with `skipped_slab_not_persistent_carrier_candidate`. |
| Row 9/10 source break | source break preserves `dy=-1.0` | live reload/source truth shows mixed `dy=-0.5` and `dy=-1.0` neighbors, but the copied logs do not isolate the exact break block/source relation enough to prove the mechanism. |

## Hypotheses A-G

Supported:

- **D. live server packet hit-validity bridge is not firing for actual coordinates.** The live log has repeated `Rejecting UseItemOnPacket ... too far away from hit block BlockPos{x=14, y=-57, z=0}` after the client predicts placement from the lowered visual hit.
- **E. live placement finalization is not seeing/writing the source as compound same-Y source.** The live source is recorded as `dy=-1.0`, but finalization writes side placement as `placedDy=-0.5`; for ordinary stone it records `ran_side_adjacent_lowered_full_anchor`, not a compound lane result.
- **C. live held-item route differs for slab placement.** The matrix says slab side placement rejects cleanly, while live slab-held placement creates bottom/top slabs at `dy=-0.5`.

Partially supported or still unproven:

- **A. live target is not `compoundFullBlockAnchor=true`.** The live recorder does not emit `compoundFullBlockAnchor`, so this cannot be proven directly from the copied logs. The source does emit `dy=-1.0` and `persistentFullBlockAnchor=true`.
- **B. live hit is not on the compound block pos expected by the matrix.** The live hit is on `14,-57,0`; matrix row coordinates are synthetic, so only topology can be compared. The live route is same-Y side placement from a `dy=-1.0` ordinary stone source.
- **F. live source-break jump uses a different break block/source relation.** Likely still open. The reload recorder shows the relevant local source truth, but not a decisive break-edge tuple.
- **G. live recorders were not enabled / not enough data.** Recorders were enabled, but they still omit explicit `compoundFullBlockAnchor` and the exact source-break tuple needed for Rows 9/10.

## Decision

Next step is **B: add/adjust recorder if logs insufficient**, with one narrow exception: the lower-half placement packet rejection and `dy=-0.5` finalization are already proven well enough to design a fix slice later. Do not implement from this audit doc alone because the live source-break relation and explicit compound sidecar state are still under-recorded.

Exact next slice recommendation:

Add a targeted live recorder rerun configuration or one recorder-only patch that logs explicit watch positions and fields for `compoundFullBlockAnchor`, `clickedPos`, `clickedFace`, `hitVec`, `finalPlacedPos`, `placedDy`, server hit-validity bridge decision, finalization branch, source position, and post-break source relation for `14,-57,0`, `13,-57,0`, `14,-58,0`, `14,-59,0`, and adjacent side slots. Keep it recorder-only; no gameplay fix, no guard, no release prep.

Release remains blocked.
