# Beta 3.5 Floor Torch Support Source Truth Audit

## Status

Audit only — source-truth mismatch diagnosis for `save/beta35-floor-torch-source-truth-mismatch` (HEAD `9ac16f2`).
No production gameplay fix is implemented. Beta 3.5 release prep remains **PAUSED**.
`wall_torch`, `lantern`, `signs`, and `chains` remain `NOT_COVERED`.

## Audit question

Why does Julia's live floor-torch support slab resolve as `supportDy=-0.500` while the replay
fixture support slab resolves as `supportDy=0.000`?

## Proven live values

```
supportCandidateState=Block{minecraft:stone_slab}[type=bottom,waterlogged=false]
supportDy=-0.500000
torchDy=-1.000000
contactGap=-1.500000
fixtureMatchesLiveDyStack=false
failureLayer=LIVE_FLOOR_TORCH_WRONG_SUPPORT_OWNER
```

## Replay fixture values

```
supportDy=0.000
torchDy=-0.500
contactGap=0.000000
fixtureMatchesLiveDyStack=false
failureLayer=LIVE_FLOOR_TORCH_WRONG_SUPPORT_OWNER
```

## Root cause: slab in isolation, no qualifying carrier context

### How `supportDy=-0.500` is computed for a bottom slab

`SlabSupport.getYOffsetInner` returns `-0.5` for a `stone_slab[type=bottom]` only when at
least one of these paths is true (in priority order):

1. **Persistent carrier mark present** — `SlabAnchorAttachment.isPersistentLoweredBottomSlabCarrierNonRecursive`
   reads the `LOWERED_SLAB_CARRIER_TYPE` chunk attachment set; if `pos.asLong()` is in that set,
   return `-0.5` immediately.

2. **Structural: anchored/lowered full block below (NonRecursive)** —
   `qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlockNonRecursive`:
   block at `pos.down()` must be an `isOrdinaryFullBlockAnchorCandidate` AND
   (`isAnchored(world, belowPos)` OR `hasBottomSlabBelow(world, belowPos)`).

3. **Structural: adjacent lowered bridge support (NonRecursive)** —
   `qualifiesForPersistentLoweredBottomSlabOnAdjacentLoweredBridgeSupportNonRecursive`:
   a horizontal neighbour of `pos.down()` must be an anchored (or `hasBottomSlabBelow`) ordinary
   full block, AND `pos.down()` itself must be anchored, `hasBottomSlabBelow`, or air.

4. **Structural: lowered carrier below** — block below is a lowered double-slab carrier or
   `hasLoweredCarrierBelow` returns true.

5. **Structural: adjacent-side-slab lowered** — `isAdjacentSideSlabLowered` (which itself checks
   for a persistent carrier mark or a `hasLoweredSlabLaneSupport` chain reaching a lowered anchor).

### What happens in the live world

Julia's slab at `(51,-56,89)` had its `LOWERED_SLAB_CARRIER_TYPE` mark written to the chunk
attachment during real in-game play. `updatePersistentLoweredSlabCarrier` is called at slab
placement time; at that moment the surrounding world context made
`qualifiesForPersistentLoweredSlabCarrier` return `true` (most likely path 2 or 3 above — an
anchored or `hasBottomSlabBelow`-backed full block at `(51,-57,89)`, or an adjacent lowered
bridge support).

The Fabric attachment is declared `.persistent(SET_CODEC).syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())`,
so the mark persists across world saves and syncs to all watching clients. When the live recorder
ran (client-side), `isPersistentLoweredBottomSlabCarrierNonRecursive(mc.world, pos, state)` hit
path 1 (mark in chunk attachment) → true → `getYOffset` returned `-0.5`.

### What happens in the fixture

The fixture (`runBeta35LiveFloorTorchSourceTruthParityProof`) does:

```java
world.setBlockState(supportCandidatePos,
        Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM),
        Block.NOTIFY_LISTENERS);
SlabAnchorAttachment.updatePersistentLoweredSlabCarrier(
        world, supportCandidatePos, world.getBlockState(supportCandidatePos));
```

`updatePersistentLoweredSlabCarrier` evaluates `qualifiesForPersistentLoweredSlabCarrier`, which
requires one of:
- `SlabSupport.isLoweredSideLaneSlabCarrier` — no adjacent lowered slabs in fixture
- `qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlock` — requires an anchored or
  `hasBottomSlabBelow`-backed ordinary full block at `supportCandidatePos.down()` — none placed
- `qualifiesForPersistentLoweredBottomSlabOnAdjacentLoweredBridgeSupport` — requires anchored
  horizontal neighbours — none placed

**Result: `qualifies=false`.** The code then calls
`removeFromAttachment(world, pos, LOWERED_SLAB_CARRIER_TYPE, ...)` — no mark is added to the
server chunk attachment, nothing syncs to the client, and the client chunk attachment is empty for
that position.

Then `getYOffset` on `mc.world`:
- Path 1 (attachment mark): not present → false
- Path 2 (NonRecursive full-block-below structural): block at `(51,-57,89)` is whatever is in
  the gametest world — not an ordinary full block anchor candidate in a qualifying context → false
- Path 3 (bridge support NonRecursive): no qualifying neighbours → false
- Path 4 (lowered carrier below): no lowered carrier below → false
- Path 5 (adjacent-side-slab): no adjacent slabs → false

**Result: `getYOffset = 0.000`.**

## Is there a client/server dy split?

No. Both the live recorder and the parity proof measure from `mc.world` (ClientWorld). The
`ClientWorld` is a `World` subclass, so `isPersistentLoweredBottomSlabCarrierNonRecursive` takes
the `world instanceof World` branch (reads chunk attachment directly), not the
`clientLoweredSlabCarrierLookup` fallback (which is for non-World render-region views like
`ChunkRendererRegion`). The dy is computed identically on both sides — the difference is purely
in the **chunk attachment state** that was written to (or omitted from) the server-side chunk
before sync.

## Which method returns the dy and which side

- Method: `SlabSupport.getYOffset(mc.world, supportCandidatePos, supportCandidateState)`
- Called from: `ctx.runOnClient` lambda — CLIENT side
- WorldClass: `net.minecraft.client.world.ClientWorld` (a `World` subclass)
- Attachment read: `WorldChunk.getAttached(LOWERED_SLAB_CARRIER_TYPE)` on the client's chunk

## What fixture mutation would create supportDy=-0.500

The fixture needs to recreate the structural context that qualifies the slab as a persistent
lowered carrier. The minimal change (no production code modifications):

**Option A — anchored full block below:**
Before placing the support slab, place an ordinary full block (e.g. `Blocks.STONE`) at
`supportCandidatePos.down()` and write its anchor mark via `setAnchor`. This satisfies
`qualifiesForPersistentLoweredBottomSlabOnLoweredFullBlockNonRecursive` → `qualifies=true` →
carrier mark written → synced to client → `supportDy=-0.500`.

**Option B — bottom slab below full block:**
Place `Blocks.STONE_SLAB[type=bottom]` at `supportCandidatePos.down().down()` and a full block
at `supportCandidatePos.down()`. `hasBottomSlabBelow(world, belowPos)` returns true → same
result.

Either option makes `updatePersistentLoweredSlabCarrier` compute `qualifies=true`, writes the
carrier mark, and the subsequent client measurement returns `supportDy=-0.500`.

## Diagnostic marker contract

Added to `runBeta35LiveFloorTorchSourceTruthParityProof`:

- `[JULIA_BETA35_FLOOR_TORCH_SUPPORT_SOURCE_AUDIT]` — logs carrier mark presence, attachment
  state, adjacent block states, and which dy source is active
- `[JULIA_BETA35_FLOOR_TORCH_SUPPORT_SOURCE_MISSING]` — emitted when `fixtureMatchesLiveDyStack=false`,
  names the exact missing source truth
- `[JULIA_BETA35_FLOOR_TORCH_SUPPORT_SOURCE_SUMMARY]` — end-of-proof summary with next-slice guidance

## Next recommended slice

A **better RED fixture** that places an anchored full block (or `hasBottomSlabBelow`-backed full
block) at `supportCandidatePos.down()` so the parity proof can drive `fixtureMatchesLiveDyStack=true`.
This is a fixture-only change — no production code required.

A client/server dy parity proof is not needed: both sides already measure from the same
`mc.world` path.

## Release and coverage status

- Beta 3.5 release prep: **PAUSED**
- `productionGameplayFixApplied=false`
- `wall_torch=NOT_COVERED`
- `lantern=NOT_COVERED`
- `signs=NOT_COVERED`
- `chains=NOT_COVERED`
- No release tag moved
