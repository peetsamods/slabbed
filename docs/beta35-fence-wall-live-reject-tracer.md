# Beta 3.5 Fence/Wall Live Reject Tracer

Diagnostics-only slice after `57d651a` / `save/beta35-fence-wall-contact-hitbox`.

Evidence folder: `tmp/beta35-fence-wall-live-reject-tracer-57d651a/`.

## Why

The `57d651a` proof savepoint is not live-accepted yet. Julia's latest live acceptance zip emitted no fence/wall live contact markers:

- `FENCE_WALL_CONTACT_HITBOX = 0`
- `LIVE_HITBOX = 0`
- `LIVE_INSPECT = 0`
- `CONTACT_GREEN = 0`
- `CONTACT_GAP = 0`
- `TRIAD_MISMATCH = 0`
- `HITBOX_SHAPE_OFFSET = 0`

The same live log did emit server rejects:

- `Rejecting UseItemOnPacket ... Location (44.625, -56.673780620098114, 89.6164071559906) too far away from hit block BlockPos{x=44, y=-56, z=89}`
- `Rejecting UseItemOnPacket ... Location (44.582998633384705, -55.74407768249512, 88.75) too far away from hit block BlockPos{x=44, y=-55, z=88}`

Current classification: `LIVE_TRACE_MISSING_PLUS_SERVER_HIT_TOLERANCE_REJECT`.

## Flag

Run client with:

```bash
JAVA_TOOL_OPTIONS="-Dslabbed.beta35FenceWallLiveInspect=true" ./gradlew --no-daemon runClient --console plain
```

Startup marker:

```text
[JULIA_BETA35_FENCE_WALL_LIVE_INSPECT] enabled=true
```

## Markers

Client marker:

```text
[JULIA_BETA35_FENCE_WALL_LIVE_INSPECT]
```

Client rows include held item, initial/final crosshair target, target object state and dy, support state and dy, model/outline/raycast/collision min/max Y, `supportVisibleTopY`, `objectModelBottomY`, `contactGap`, and classifications:

- `LIVE_CONTACT_GREEN`
- `LIVE_CONTACT_GAP`
- `LIVE_TRIAD_MISMATCH`
- `LIVE_OWNER_GAP`
- `TRACE_ACTIVE_NO_TARGET`

Server marker:

```text
[JULIA_BETA35_FENCE_WALL_LIVE_SERVER]
```

Server rows include held item, packet hit block, face, hit vector, vanilla validation center, validation delta, tolerance, target state/dy, object/support dy, `contactGap`, and:

- `SERVER_HIT_TOO_FAR`
- `SERVER_HIT_WITHIN_TOLERANCE`
- `SERVER_SHIFTED_HIT_GREEN` after the owner/server-hit fix when a legal Slabbed-lowered target validates against the shifted center while staying inside vanilla component tolerance.

## Scope

No gameplay fix is implemented. The server tracer records the vanilla packet validation center and predicted component-tolerance result; it does not widen tolerance, rewrite packets, change contact dy, change render/model dy, or retarget owners.

No release audit was run. No release tag was moved. Scope remains fence/wall/anvil diagnostics plus existing floor_torch/candle/flower_pot regression status. Standing signs, lanterns, chains, redstone, rails, buttons/levers, wall/hanging signs, panes, doors, and trapdoors remain out of scope.

## Follow-Up: Owner / Server-Hit Fix

Julia's live capture after this tracer savepoint proved contact and triad were green, but still showed `LIVE_OWNER_GAP=1495` and two `SERVER_HIT_TOO_FAR` rows.

The follow-up fix keeps the tracer flag and adds the focused proof `-Dslabbed.beta35FenceWallOwnerServerHit=true`. The reproduced row now reports `ownerClassification=LIVE_OWNER_GREEN`, `finalDecision=object-shape-owner-preserve`, `SERVER_SHIFTED_HIT_GREEN`, and `failureLayer=NONE`.

No contact dy rewrite, global hit tolerance widening, release audit, release tag movement, or all-item expansion is included.

## Follow-Up: Stack Contact

The live tracer after `aa66efd` proved server validation remained green (`SERVER_HIT_TOO_FAR=0`) and isolated the next concrete failure to stacked wall/fence-family contact. The repeated contact-gap bucket was wall over lowered wall support with `contactGap=0.500000`, while contact on slab/full-block supports stayed green.

The follow-up stack-contact proof uses `-Dslabbed.beta35FenceWallStackContact=true -Dslabbed.beta35FenceWallLiveInspect=true` and now reports `JULIA_BETA35_FENCE_WALL_STACK_CONTACT_GREEN`. Residual connected-wall triad rows are classified as `TRACE_FALSE_POSITIVE` / vanilla connected-wall shape limit, and remaining owner gaps are classified as out-of-scope held-item/category rows or a future focused RED target.
