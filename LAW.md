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

## Law 1 corollary — hung and wall-mounted things remember too (maintainer ruling, 2026-09-13)

> **Where I place it is where it stays.** Absolute, for every Slabbed version. The moment of
> placement is the only moment the surroundings are consulted; from then on the thing remembers.

- A wall-mounted block (sign, banner, torch, hanging sign, lever, button) takes its seat from the
  block it is MOUNTED ON at placement — never from the floor under its cell — and keeps it.
- A hung decoration (item frame, glow frame, painting) is an entity with no block store, so it
  carries its own remembered seat: minted once when it is hung, from the face it hangs on, synced
  and saved with the entity. Rebuilding the wall behind it at another height changes the wall,
  not the decoration. "Follows the support" is the same violation as Law 1 above.
- Any lane that re-derives a height from neighbours on read is outside this law. Such a lane
  exists only for worlds older than the placement record and is not a second definition of
  correct behaviour.

## Law 2 — lowering eligibility is geometric

Eligibility to lower an unplaced structural block comes from the intended placement
geometry. It must not depend on a block class, namespace, or compatibility
implementation name.

Every test row must name the reachable mutation it exercises. A passing row
proves only that named boundary, not a broader gameplay claim.
