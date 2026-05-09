# Beta4 Compound Live Pass Audit at 26540ff

## Julia live verdict

Julia live-tested the compound beta4 contract after the `26540ff` matrix closure and the `3cef02c` recorder pass:

> Everything worked. Slab placement rejected cleanly with no flash.

The slab rejection is intended beta4 behavior, not a failure. Under A-prime, ordinary full-block side/top routes may use the compound `dy=-1.0` lane, but slab-held side/half placement from compound full blocks must reject cleanly and preserve selection. Beta4 does not legalize a `dy=-1.0` slab lane or a new `dy=-0.5` slab lane from this route.

## Automation baseline

- Baseline closure: `26540ff` / `save/beta4-compound-matrix-closure`.
- Recorder pass HEAD: `3cef02c` / `save/beta4-compound-live-path-recorder`.
- Matrix status at closure: `rows=12 red=0 undecided=0 green=11 notImplemented=1`.
- Row 12 remains `NOT_IMPLEMENTED` because the chunk-only helper is absent.

## Live evidence

Evidence harvested into `tmp/beta4-compound-live-pass-audit-26540ff/` from local live logs.

Stone side placement on the compound full block is log-proven:

- `clickedPos=14,-57,0`
- `clickedFace=west`
- `targetDy=-1.0`
- `targetCompoundFullBlockAnchor=true`
- `heldItem=minecraft:stone`
- `bridgeAccepted=true`
- `finalPlacedPos=13,-57,0`
- `finalPlacedState=Block{minecraft:stone}`
- `finalPlacedDy=-1.0`
- `finalPlacedPersistentFullBlockAnchor=true`
- `finalPlacedCompoundFullBlockAnchor=true`
- `anchorFinalization=ran_side_adjacent_lowered_full_anchor`

The successful ordinary-stone route no longer shows the old post-bridge server rejection for the successful placement path.

Slab-held placement from the same compound target is intentionally rejected:

- `heldItem=minecraft:stone_slab`
- `heldIsSlab=true`
- target `compoundFullBlockAnchor=true`
- `bridgeAccepted=false`
- `rejectionReason=held_item_slab`
- live-path recorder shows the side target as air at use-on-block head
- Julia reported no ghost slab and no flash

No live-path recorder evidence supports a legal `dy=-1.0` slab lane from this compound route. No `dy<-1.0` compound recursion is part of the accepted beta4 contract.

## Source break and reload

Julia also live-tested side/top placement, rejection, source break, and reload as "everything worked." The harvested local logs include reload/source-truth recorder chatter, but the pass classification for source break/reload is live-pass-by-report rather than fully isolated by a source-break tuple in the copied log evidence.

## Release artifact closure

Follow-up release artifact closure at `3cef02c` completed the release-readiness scan cleanup without a gameplay fix. The live pass above remains valid.

- `compileJava compileGametestJava`: passed
- `runClientGameTest --console plain`: passed
- `clean build`: passed
- release jar sensitive-name scan: passed for `Beta4`, `Recorder`, `RetargetTestHooks`, `SlabbedAuditBridge`, `debug`, `dev`, `audit`, `gametest`, `test`, `proof`, and `fixture`
- `jdeps` sensitive hard-reference scan: passed for `Beta4`, `Recorder`, `RetargetTestHooks`, `SlabbedAuditBridge`, `com.slabbed.debug`, and `com.slabbed.dev`

The exact broad scan pattern still reports false positives when it includes `lab`, because `lab` matches the project package path `com/slabbed` and `com.slabbed`. The sensitive release blockers are gone.

The release tag was not moved, and no upload was performed by this audit.

## Recommendation

Proceed only to an explicit release metadata/tag/upload decision slice if Julia authorizes it. Do not upload beta4 or move `release/0.2.0-beta.4` without that release/upload slice.
