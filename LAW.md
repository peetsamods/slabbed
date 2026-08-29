# LAW.md — Slabbed placement law

## Law 1 — placement is permanent

Once a player places an eligible structural block on a slab-supported surface,
later neighbor changes must not move it. The
`NeighborUpdateInvarianceTest` is the blocking automated enforcement for this
rule. It uses the real held-item placement path, then removes the direct support
that would otherwise make the resolver lower the block.

The default gate rejects a failed preservation row. `-Dslabbed.lawGate=false`
may be used only to characterize a known work-in-progress failure; it is never
an acceptable setting for landing a violation.

## Law 2 — lowering eligibility is geometric

Eligibility to lower an unplaced structural block comes from the intended placement
geometry. It must not depend on a block class, namespace, or compatibility
implementation name.

Every test row must name the reachable mutation it exercises. A passing row
proves only that named boundary, not a broader gameplay claim.
