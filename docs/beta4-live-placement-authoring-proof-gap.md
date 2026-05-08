# Beta4 Live Placement Authoring Proof Gap

Current base: `af47e99` / `save/beta4-outline-raycast-shape-parity-fix`.

The fresh ordinary block reload-jump harness route was archived as unfaithful evidence. It broke and re-placed `Block{minecraft:stone}` at `2,201,0` against a dynamic non-persistent lowered bottom slab source at `1,201,0`; immediate and post-reload source truth stayed valid with `dy=-0.5` and `persistentFullBlockAnchor=true`.

That GREEN proves only one nearby fixture route. It does not mirror Julia's live failure closely enough to justify a placement or persistence fix.

## Recorder

Default-off property: `slabbed.beta4PlacementAuthorRecorder=true`.

Optional:

- `slabbed.beta4PlacementAuthorRecorderTicks=200`
- `slabbed.beta4PlacementAuthorRecorderWatch=14,-57,0;14,-58,0;14,-59,0;14,-58,-1;14,-59,-1;16,-59,-1;16,-58,-1`

Markers:

- `[BETA4_PLACEMENT_AUTHOR_RECORDER_START]`
- `[BETA4_PLACEMENT_AUTHOR_RECORDER]`
- `[BETA4_PLACEMENT_AUTHOR_AFTER_TICK]`

The recorder logs held item, slab-vs-ordinary item, player position/eye/look, clicked position, clicked face, hit vector, placement position, final placed state, placed/source dy, persistent full-block anchor truth, lowered slab carrier truth, source mode, surrounding topology, watched positions, and placement-finalization reason. It does not change placement, persistence, retarget, raycast, outline, model, `SlabSupport`, or anchor semantics.

Release remains blocked pending a live capture of Julia's exact break/re-place/reload sequence and a later faithful RED/GREEN proof.
