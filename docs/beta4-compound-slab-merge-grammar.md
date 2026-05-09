# Beta4 Compound Slab Merge/Remap Grammar

## Problem

Beta 4 gates passed, but the live/product feel is paused.

The compound ordinary full-block lane at `dy=-1.0` is proven and useful. Slab-held attempts currently reject cleanly by design, which is safe but now feels too restrictive for a mod called Slabbed. The question is not "can we place a slab somehow?" but "what legal stable state should slab placement normalize into?"

## Contract sentence

Compound slab placement should resolve into the nearest legal stable slab result.

## Legal target states

Beta 4 may resolve compound slab interaction into the following legal results only:

- vanilla slab states at `dy=0`
- existing lowered slab lane states at `dy=-0.5`
  - `BOTTOM` `dy=-0.5`
  - `TOP` `dy=-0.5`
  - `DOUBLE` `dy=-0.5`
  - compatible side-lane continuation at `dy=-0.5`
  - merge/completion into `DOUBLE` `dy=-0.5` when proof-covered
- compound ordinary full-block lane at `dy=-1.0`
- ordinary full blocks may remain/use compound `dy=-1.0` lane

## Non-legal states for Beta 4

Beta 4 does not allow:

- no freeform slab type + `dy=-1.0`
- no `dy<-1.0`
- no normal-lane `dy=0` slab produced from valid lowered-lane interaction
- no slab state that only works because rescue/retarget fixes it later
- no broad solidity or hitbox lies

## Player-facing behavior table

| Player action | Required Beta 4 resolution |
| --- | --- |
| full block side-click on compound full block | keep current legal compound full-block behavior, `dy=-1.0` |
| full block top-click on compound full block | same compound lane `dy=-1.0`, not `dy=-1.5` |
| slab side-click on compound full block with no legal lowered slab lane nearby | preserve compound owner or cleanly reject; do not create `dy=-1.0` slab |
| slab side-click where existing legal `dy=-0.5` lowered slab lane can be continued | remap into that `dy=-0.5` legal lane |
| second slab click against compatible lowered slab | merge `BOTTOM`/`TOP` into `DOUBLE` `dy=-0.5` when proof-covered |
| slab top-click on compound full block | only complete if it normalizes into named legal vanilla or lowered slab state; otherwise preserve/reject |
| source/support break | no jump, no ghost, no silent renormalization |
| reload/rejoin | legal states survive; illegal/unproven states must not appear |

## Owner/targeting policy

- If no legal slab placement result exists, compound full block selection should remain preferred over rescue-created slab intent.
- If a legal visible `dy=-0.5` slab lane exists and the player is clearly clicking that lane, the slab lane may win.
- Retargeting must not invent placement legality.
- Targeting, outline, model, placement, survival, and live feel must agree.

## RED proof matrix before implementation

| # | Proof row | Expected initial RED behavior | Intended GREEN result | Forbidden false-green |
| --- | --- | --- | --- | --- |
| 1 | slab lower-half side click on compound full block with no neighboring legal slab lane | clean rejection or owner preservation only; no slab lane appears | preserve compound owner or reject cleanly without new slab state | any `dy=-1.0` slab or rescue-created slab lane |
| 2 | slab upper-half side click on compound full block with no neighboring legal slab lane | same restrictive behavior as row 1 | preserve compound owner or reject cleanly without new slab state | any `dy=-1.0` slab or hidden retarget workaround |
| 3 | slab side click when adjacent legal `dy=-0.5` lowered slab lane exists | still rejects or misroutes until grammar exists | remap into the adjacent legal lowered lane | `dy=0` fallback or compound lane invention |
| 4 | second slab click merging `BOTTOM`/`TOP` -> `DOUBLE` `dy=-0.5` | merge path not yet proven | merge into `DOUBLE` `dy=-0.5` only when proof-covered | `DOUBLE` at `dy=-1.0` or silent recursion |
| 5 | slab top click on compound full block | current clean rejection feels too strict | normalize only into legal vanilla or lowered slab state, otherwise preserve/reject | freeform slab at `dy=-1.0` |
| 6 | no ghost/flicker after tick | visible instability or mismatch may remain | stable post-tick result with no ghost/flicker | visual success that only exists for one frame |
| 7 | source/support break | jump or collapse risk remains | no jump, no ghost, no silent renormalization | survival only because of delayed rescue |
| 8 | reload/rejoin | illegal or unproven state may reappear | only legal states survive reload/rejoin | save/load hiding an illegal slab lane |
| 9 | triad: model/outline/raycast | shape/owner disagreement may remain | model, outline, and raycast agree on the legal result | model-only or outline-only false-green |
| 10 | held slab selection preserves compound owner when no legal slab result exists | selection may drift toward a fake slab lane | keep compound owner until a legal slab result exists | retarget-created slab legality |

## Proof status

These proof rows now exist in the focused harness slice:

- Row 1: GREEN-safe-reject.
- Row 2: GREEN-safe-reject.
- Row 3: GREEN-implemented/proven; automated/focused proof passed and runtime/live-launch logs emitted GREEN; Julia manual live-feel test pending.
- Row 4: TODO.
- Row 5: TODO.
- Row 6: TODO.

Harness/source-truth repair note:

- The failed Row 3 implementation attempt exposed a proof topology gap: Row 1 could report against an authored `dy=-0.5` lowered slab at the side lane instead of proving the clicked source was the compound ordinary full block.
- Before any slab click/remap assertion, Rows 1-3 now prove the clicked source is ordinary stone, `compoundFullBlockAnchor=true`, and `dy=-1.0`.
- Rows 1-2 require zero neighboring legal `dy=-0.5` slab lanes and still safe-reject/preserve the compound source.
- Row 3 requires exactly one legal adjacent `dy=-0.5` slab lane in the intended remap direction. The implemented path now remaps to the continuation cell beyond that lane and authors a legal lowered slab lane at `dy=-0.5`.
- The Row 3 implementation keeps the clicked source as ordinary stone at compound `dy=-1.0` and does not legalize slab type + `dy=-1.0`.

Current proof markers emitted by the gated gametest slice:

- `[JULIA_BETA4_COMPOUND_SLAB_NO_LEGAL_LANE_GREEN]`
- `[JULIA_BETA4_COMPOUND_SLAB_HARNESS_SOURCE_GREEN]`
- `[JULIA_BETA4_COMPOUND_SLAB_LEGAL_REMAP_GREEN]`
- `[JULIA_BETA4_COMPOUND_SLAB_LEGAL_REMAP_PENDING]`
- `[JULIA_BETA4_COMPOUND_SLAB_DOUBLE_MERGE_PENDING]`
- `[JULIA_BETA4_COMPOUND_SLAB_HARNESS_FAIL]`

Rows 4-6 remain pending/TODO in this focused grammar note. Row 4 DOUBLE merge and Row 5 top-click are not implemented by the Row 3 remap slice and remain pending for later beta4 work, but they are not blocking the Row 3 automated/focused proof that passed here; final decision still awaits Julia manual live-feel test.

## Implementation slices after design

1. Add focused RED proofs only.
2. Add authority-level classifier for compound slab merge/remap result.
3. Remap only into existing legal `dy=-0.5` lowered slab grammar.
4. Preserve/reject when no legal stable slab result exists.
5. Add merge completion into `DOUBLE` `dy=-0.5`.
6. Triad and survival proof.
7. Julia live goblin pass.
8. commit/tag/push only after one live-confirmed win.

## Authority placement

The decision must live in Slabbed authority/semantic helper, not scattered inline mixin predicates.

Mixins may gather context and call authority. Authority should return an enum-like decision:

- `KEEP_COMPOUND_OWNER`
- `REMAP_TO_EXISTING_LOWERED_SLAB_LANE`
- `MERGE_TO_LOWERED_DOUBLE`
- `VANILLA_SLAB_RESULT`
- `REJECT_OR_DEFER`

Do not implement this enum in this slice; document only.

## Release impact

Beta 4 artifact/gates may be technically ready.

Public release confidence remains paused because live slab feel is not accepted. Do not move the release tag until Julia explicitly approves.
