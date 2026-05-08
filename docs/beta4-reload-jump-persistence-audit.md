# Beta4 Reload Jump Persistence Audit

Current savepoint: `e787bf1` / `save/beta4-live-retarget-source-recorder`

Live symptom: Julia observed that after loading back in or after a chunk reload, the ordinary block/log that had been visually lowered jumps up into vanilla position.

Why targeting is paused: retarget, outline, and owner rules cannot safely fix a block whose lowered state does not survive reload. Reload/chunk-jump persistence is state authority first, targeting second.

Evidence folder: `tmp/beta4-reload-jump-persistence-audit-e787bf1`

## Source-Truth Facts

- The named recorder folder `tmp/beta4-stone-held-source-recorder-e787bf1` is absent in this checkout, so those exact uploaded recorder files could not be verified locally.
- Harvested current log extracts did contain `[BETA4_LIVE_RETARGET_SOURCE_TRUTH]` lines before save/shutdown.
- Pre-save `initialSourceTruth` shows ordinary `Block{minecraft:stone}` at `pos=14, -59, 0` with `dy=-0.500000`, `anchored=true`, `persistentFullBlockAnchor=true`, `persistentLoweredSlabCarrier=false`, and `sourceMode=dynamicLoweredOrAnchored`.
- Pre-save `finalSourceTruth` shows `Block{minecraft:stone_slab}[type=bottom,waterlogged=false]` at `pos=14, -58, 0` with `dy=-0.500000`, `anchored=false`, `persistentLoweredSlabCarrier=true`, `persistentLoweredBottomSlabCarrier=true`, and `sourceMode=persistentLoweredSlabCarrier`.
- The same source-truth line shows the ordinary stones below and above that slab carrier, `pos=14, -59, 0` and `pos=14, -57, 0`, both with `dy=-0.500000`, `anchored=true`, `persistentFullBlockAnchor=true`, and `sourceMode=dynamicLoweredOrAnchored`.
- The harvested log then reaches normal world/chunk save lines, but no local post-reload source-truth line proves whether those same attachment facts survive.

## Audit Answers

1. Which block jumps after reload: live observation says the ordinary block/log full block jumps. Local pre-save source truth shows ordinary full blocks and a lowered bottom slab carrier in the topology, but no local post-reload line isolates whether only the ordinary full block jumps or the carrier also loses state.
2. Expected state: the ordinary stones at `14, -59, 0` and `14, -57, 0` are expected to behave as persistent full-block anchors. The bottom slab at `14, -58, 0` is expected to behave as a persistent lowered slab carrier. Non-persistent dynamic lowered lanes are not enough to explain a legal reload-stable state.
3. Recorder truth before reload: yes in the harvested current log, not from the missing named recorder folder. It shows `persistentFullBlockAnchor=true` for ordinary stone and `persistentLoweredSlabCarrier=true` for the bottom slab carrier before save.
4. Expected survival: if the state is legal, the full-block anchor and lowered slab carrier are expected to survive chunk unload/reload because the attachment types are registered as persistent and synced, and `SlabSupport` applies `dy=-0.5` from those authorities.
5. Existing test coverage: not exact. Existing persistence tests cover real-placed lowered bottom slab carrier survival after bridge break and under-placement, and anchored full-block survival after support removal. They do not faithfully reproduce this live save/load or chunk-reload jump topology.
6. Likely failure class: unknown. The first split must prove server attachment persistence vs client mirror/sync/rerender vs render-view lookup after reload. A `SlabSupport` dy recomputation gap is only proven if the post-reload attachment facts remain true while `dy` becomes `0.0`.
7. Exact RED proof needed: an opt-in client gametest or live recorder route that creates the same legal topology, records server and client pre-reload facts, forces the existing save/load or chunk unload/reload path, then records server and client post-reload facts for the same positions.

## Required RED Proof

Suggested property: `slabbed.beta4ReloadJumpRedOnly`

Suggested marker: `[BETA4_RELOAD_JUMP_PERSISTENCE_RED]`

The proof must establish the ordinary full block and lowered bottom slab carrier legally, not by faking dy. It must print the block pos/state, pre and post dy, pre and post `persistentFullBlockAnchor`, pre and post `persistentLoweredSlabCarrier`, support topology, and source mode before and after reload. It should fail RED if an ordinary persistent full-block anchor or persistent lowered slab carrier has `dy=0.0` after reload when the legal expectation is `dy=-0.5`.

Follow-up proof attempt from `7647501`: an opt-in client-gametest save/close/open route was tried locally with property `slabbed.beta4ReloadJumpRedOnly`, but it did not reproduce Julia's jump. The temporary proof created legal source truth at `tracked=14, 201, 0` and `carrier=14, 202, 0`, then used `TestWorldSave.open()` after an explicit server save and world close.

Observed local result from `build/run/clientGameTest/logs/latest.log`:

- Pre-reload server and client tracked stone: `dy=-0.5`, `persistentFullBlockAnchor=true`, `sourceMode=dynamicLoweredOrAnchored`.
- Pre-reload server and client carrier bottom slab: `dy=-0.5`, `persistentLoweredSlabCarrier=true`, `persistentLoweredBottomSlabCarrier=true`, `sourceMode=persistentLoweredSlabCarrier`.
- Post-reload server and client tracked stone: `dy=-0.5`, `persistentFullBlockAnchor=true`.
- Post-reload server and client carrier bottom slab: `dy=-0.5`, `persistentLoweredSlabCarrier=true`, `persistentLoweredBottomSlabCarrier=true`.
- Classification was `GREEN_STILL_LOWERED_AFTER_RELOAD`, so no accepted `[BETA4_RELOAD_JUMP_PERSISTENCE_RED]` exists from this route.

Exact missing mechanism: the clean singleplayer save/close/open path near the harness spawn does not reproduce the live block jump. The next RED must use either Julia's actual live world/reload path or a more faithful chunk unload/reload path that exercises the same already-built live seam topology and render-view timing. If server/client world facts stay green while live visuals jump, the likely missing bucket is render-view lookup or initial chunk-render timing rather than saved attachment persistence.

Follow-up recorder from `e82abfb`: a default-off live recorder was added behind `slabbed.beta4ReloadJumpRecorder=true` to capture the suspected post-join/chunk-render timing path without changing persistence, dy authority, retargeting, or render behavior.

Recorder markers:

- `[BETA4_RELOAD_JUMP_RECORDER_START]` prints the enabled state, source head, tick budget, radius, and world.
- `[BETA4_RELOAD_JUMP_RECORDER]` prints one compact sample per client world tick while the tick budget remains. It records player/camera/look, crosshair/outline target, client dy, `persistentFullBlockAnchor`, `persistentLoweredSlabCarrier`, `persistentLoweredBottomSlabCarrier`, support topology around the current crosshair target or player block, and optional configured watch positions.
- `[BETA4_RELOAD_JUMP_SYNC]` prints client chunk-load, attachment-update, initial-rerender-check, and scheduled-rerender timing for the anchor and lowered-slab-carrier attachment mirrors.

Suggested launch properties: `slabbed.beta4ReloadJumpRecorder=true`, `slabbed.beta4ReloadJumpRecorderTicks=500`, and `slabbed.beta4ReloadJumpRecorderRadius=6`. Optional absolute watch positions can be supplied with `slabbed.beta4ReloadJumpRecorderWatch=x,y,z;x,y,z`, but the default route follows the current crosshair target so Julia can stand near the live seam and look at the visually jumped block.

## Non-Negotiables

- No retarget fix before reload jump is classified.
- No rescue broadening.
- No release prep.
- Preserve global slab support.
- Preserve existing savepoints and old evidence tags.
