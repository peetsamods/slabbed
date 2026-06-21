# 26.2 offset-raycast targeting overhaul — SHIPPED (0.4.2-beta.1+26.2)

`scope: client targeting` · `status: LIVE-CONFIRMED + shipped in 0.4.2-beta.1+26.2 (targeting + opposite-side fix)` · `branch: port/mc-26.2-0.4.1-beta.1`

> Update: targeting overhaul + opposite-side placement fix LIVE-CONFIRMED and released. The client-prediction
> "pop" fix was attempted and REVERTED (it over-froze cantilever-against-cantilever; server placement is
> correct). On-top flicker + deep-stack gaps are documented KNOWN ISSUES (CHANGELOG) for a future version
> (the deferred slab-combining feature).

## Why

The rollback-baseline live red (S1 displaced outline on lowered columns, S2 displaced outline on lowered
UPPER slabs, S3 `target: none`, S4 slab placed on the OPPOSITE side of a -1.0 compound column) was root-caused
to the targeting ARCHITECTURE, not a single lane. `GameRendererCrosshairRetargetMixin` (@Inject TAIL of
`Minecraft.pick`, ~3075 lines) accepts vanilla's first-cell DDA hit, then post-hoc rewrites `hitResult`
through a cascade of "owner-rescue" lanes — the whole slab-only lane block is gated on `slabHeld`, which is
exactly why WYSIWYG holds for non-slab items and breaks while holding a slab. A targeted slab-held parity
patch was already built, passed server + client gametests, and was LIVE-REJECTED ("worse in general") — proof
that incrementally patching this architecture is a losing game and that the client gametest harness is a
false green for this picking path.

## Fix (the canonical cure, ported from the 1.21.1 sibling)

Replace the post-hoc retarget with an offset-aware nearest-hit raycast — the same `SlabbedOffsetRaycast`
approach that fixed this class of bug on 1.21.1/1.21.11, never previously ported to 26.x.

- NEW `src/main/java/com/slabbed/util/SlabbedOffsetRaycast.java` — Mojang-mapped port (~175 lines). Reuses
  vanilla `BlockGetter.traverseBlocks` DDA but keeps the globally NEAREST hit instead of the first cell,
  testing each marched cell C plus the ±1 vertical neighbours that carry a non-zero `getYOffset`. Clips the
  offset OUTLINE shape (`getShape`, which `SlabSupportStateMixin:400` already moves to match the model) via
  `clipWithInteractionOverride`. So model == outline == pick == placement face by construction.
- NEW `src/client/.../mixin/client/LocalPlayerPickOffsetRaycastMixin.java` — single `@Redirect` on the block
  raycast `Entity.pick(DFZ)` inside the private static `LocalPlayer.pick(Entity,DDF)` (the 26.2 call chain is
  `Minecraft.pick` -> `LocalPlayer.raycastHitResult(F,Entity)` -> `LocalPlayer.pick` -> `Entity.pick(DFZ)`,
  verified by bytecode). Preserves the vanilla block-vs-entity merge, reach clamp, and fluid handling.
- `GameRendererCrosshairRetargetMixin` pick inject now EARLY-RETURNS when the new path is enabled, so the
  legacy lanes can never re-mangle the honest hit. Mutually exclusive, fully reversible.
- Master flag `SlabbedOffsetRaycast.ENABLED` (default ON; `-Dslabbed.offsetRaycast=false` restores the
  rollback retarget lanes). Dev probe `-Dslabbed.offsetRaycast.trace=true` logs vanilla-vs-offset divergence.

## Why placement is safe without the retargeter (verified)

The retargeter was the SOLE producer of `PlacementIntentState` snapshots
(`GameRendererCrosshairRetargetMixin:1148,1190`). With it inert, the snapshot is always null and the
server-side intent-remap returns at `NO_SNAPSHOT` (`BlockItemPlacementIntentMixin:537`). Everything else in
placement is snapshot-INDEPENDENT and reads the (now honest) `UseOnContext`: the WYSIWYG side/up follow
markers (`:865-886` + `SlabAnchorAttachment.freezeLoweredOnPlace`), the `targetSupportsTopMerge` rewrite, the
compound side/top remaps, and the geometric fall-through. So side-click-follow, the lowered top-slab UP stack,
and non-slab placement are unaffected — the only thing that goes dark is the "visible-lane rescue" that
existed purely to compensate for the retargeter, which is redundant under honest targeting. For S4, honest
targeting feeds the compound remap the correct EAST face, so the slab should land on the clicked side (not the
opposite); the opposite-side flip required the snapshot + a displaced raw face, both now gone.

## Proof so far (headless / harness only — LIVE STILL REQUIRED)

- `compileJava` + `compileClientJava` green (all Mojang mappings resolve).
- `runGameTest` 131/131 required server tests pass (no server-side regression).
- Client apply-check: a throwaway `fabric-client-gametest` booted a client into a world and ran
  `Minecraft.pick` through the redirect — `[OFFSET_RAYCAST_APPLY] enabled=true pickRanWithoutCrash`, BUILD
  SUCCESSFUL — confirming the @Redirect binds on 26.2 (registration reverted, test deleted).
- Jar `slabbed-0.4.2-beta.1+26.2.jar` SHA-256 `a9d0a01fdb462fa3e52884c95699a940983e14f012e26b0512bc038ba53ba313`
  staged into Modrinth profile `SLABBED-MC 26.2`. Rollback baseline `ba19ebe6...` backed up as
  `_codex-backups/slabbed-0.4.2-beta.1+26.2.rollback-baseline-ba19ebe-20260621.jar`.

## Next (live, mandatory — headless is a documented false green here)

Restart the `SLABBED-MC 26.2` Modrinth profile and re-test S1-S4 + general WYSIWYG (outline tracks the
crosshair while holding a slab; slab places where aimed) and a non-slab regression spot-check. A/B against the
backed-up rollback jar if needed. Do NOT commit, tag, or claim release-ready until Julia live-confirms.
