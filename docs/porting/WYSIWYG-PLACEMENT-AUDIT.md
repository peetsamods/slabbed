Confirmed: only `GhostLoweredCollisionProofTest` and `Slabbed2612LoweringContractTest` compile/run on 26.1.2 (build.gradle:114-115). The rest are Yarn-named and excluded — so the slab-side / compound-slab / TS-block scenarios have ZERO automated coverage, matching the audits. I have everything I need.

All six audited root causes verified against actual HEAD `a0faa029` source. Key correction to the audits: NO "parallel agent" air-gate exists at `slabLoweringIsSideInheritedOnly:1561-1569` — that method (now at 1553-1561) is unguarded. Producing the final report.

---

# Slabbed 26.1.2 — Consolidated WYSIWYG Violation Report (verified vs HEAD `a0faa029`)

All six audits verified against the live `src/` tree. Line numbers below are the ACTUAL current ones (audits were ~off by a few lines). Six scenarios collapse into **4 distinct root causes**. The two "on-top" / "compound full-block-held" cases were correctly audited as already-WYSIWYG and are excluded except for one narrow edge.

Files (all absolute):
- `/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2/src/main/java/com/slabbed/util/SlabSupport.java`
- `/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2/src/main/java/com/slabbed/anchor/SlabAnchorAttachment.java`
- `/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2/src/main/java/com/slabbed/mixin/BlockItemPlacementIntentMixin.java`

---

## ROOT CAUSE 1 — `hasBottomSlabBelow` has no Terrain Slabs guard → block-on-TS-slab snaps DOWN 0.5 (server/client desync)
**Confidence: HIGH. Impact: HIGHEST** (only TS-interop violation; visible sink into a TS surface, and the world-hole family is the headline 26.1.2 goal).

- **Breaks scenario:** `block-on-terrain-slab` (place stone/full block on a Terrain Slabs bottom slab → client predicts flush dy=0.0, then snaps to dy=-0.5 sinking halfway into the TS slab).
- **Root (file:line):** `SlabSupport.hasBottomSlabBelow` at `SlabSupport.java:138-143` returns `isBottomSlab(world.getBlockState(pos.below()))` with **no `CompatHooks.shouldSkipSlabSupport` guard**. A TS bottom slab is `instanceof SlabBlock` with `TYPE=BOTTOM`, so it returns true. The geometric column walks `hasSlabInColumn` (`:2160`) and `slabColumnYOffset` (`:2187`) already terminate flush at a TS slab — but `hasBottomSlabBelow` is the choke point feeding all five anchor sites: `SlabAnchorAttachment.java:864` (`qualifiesForAnchor`), `:902` (`qualifiesForDirectAnchor`), `:1152`, `:1213`, `:1224`. So `addAnchor` fires SERVER-side (line 219), the read-back anchor branch at `getYOffsetInner` returns -0.5 (`SlabSupport.java:1789`), while the CLIENT (isClientSide early-return at `SlabAnchorAttachment.java:208`) uses the geometric path which correctly reads 0.0 → the visible snap when `ANCHOR_TYPE` syncs.
- **Minimal fix:** add the TS guard at the single choke point — in `hasBottomSlabBelow` (`SlabSupport.java:138`):
  ```java
  public static boolean hasBottomSlabBelow(BlockGetter world, BlockPos pos) {
      if (world == null || pos == null) return false;
      BlockState below = world.getBlockState(pos.below());
      if (CompatHooks.shouldSkipSlabSupport(below)) return false;   // TS surface: never anchor onto it
      return isBottomSlab(below);
  }
  ```
  No-op without Terrain Slabs (`shouldSkipSlabSupport` → false). Belt-and-suspenders for stale anchors: before the non-slab anchor read-back at `SlabSupport.java:1721`, add `if (!(state.getBlock() instanceof SlabBlock) && CompatHooks.shouldSkipSlabSupport(world.getBlockState(pos.below()))) return 0.0;`.
- **Regression risk:** `hasBottomSlabBelow` also feeds the legitimate VANILLA bottom-slab anchor. The guard keys only on `terrain_slabs`/`terrainslabs` states (via `TerrainSlabsCompat.isTerrainSlabsId`), so vanilla bottom slabs are untouched. The neighbour probes at `:1152/:1213/:1224` (cantilever/side propagation) will also stop treating a TS slab as a lowering source — that is the desired flush behaviour, but confirm a vanilla mixed canopy still settles.
- **Live test (Modrinth TS profile, `/slabdy`):**
  1. Place stone on a **VANILLA** bottom slab → still lowers, `/slabdy` reads **-0.500** (no regression).
  2. Place stone on a **TS** bottom slab → stays flush, `/slabdy` reads **0.000**, no snap after the sync delay (FIX).
  3. Place a vanilla mixed canopy (full blocks + slabs off a real lowered tower) → still settles at one lowered level.
  Then `./gradlew runGameTest` with terrainslabs loaded.

---

## ROOT CAUSE 2 — Cantilever slab/fence/wall beside a lowered neighbour never gets a geometric lowered dy → floats 0.5 above the aimed surface (freeze-flat is the SYMPTOM, geometry is the cause)
**Confidence: HIGH. Impact: HIGH** (this is the single biggest cluster — it breaks every "place against the SIDE of a lowered thing over air" case; matches Julia's Pic-1 "snaps up to vanilla").

This merges what the audits split across three scenarios and corrects their framing: the proposed "fix `slabLoweringIsSideInheritedOnly` to return false on air-below" is **INERT** because `getYOffset` already returns 0.0 before freeze ever runs. The real cause is that the **geometric dy is never -0.5 in the first place**.

- **Breaks scenarios:** `slab-side-lowered-fullblock` (dy=-0.5 case, both halves), `slab-side-lowered-slab` (cantilever sub-case a/b), `fence-fullblock-on-lowered` (the AGAINST-over-air combinations: fence/stone beside a lowered fence/full block).
- **Root (file:line):** three sibling gaps in `getYOffsetInner` (`SlabSupport.java:1636`), all when `pos.below()` is air:
  1. **Slab beside a lowered FULL BLOCK:** the slab branch's gate `canUseInheritedSlabLaneYOffset` (`:1682`, def `:1935`) requires `isNamedLoweredSlabLane` OR `isPersistentLoweredSlabCarrier`. A freshly placed plain slab carries no marker → returns 0.0 at `:1683` **before** the `isAdjacentSideSlabLowered` branch at `:1702`. And even past the gate, `isAdjacentSideSlabLowered` (`:1535`) → `hasLoweredSlabLaneSupport` only recognizes lowered **slab** lanes, never a lowered full-block neighbour, and `canUseDirectLoweredSolidSideSupportForSlabLane` (`:1508`) only accepts a BOTTOM slab — so a TOP slab can't lower at all (the upper-half "deeper" cause).
  2. **Fence/wall beside a lowered neighbour:** the only cantilever-merge path is `isCantileverLoweredFullBlock` (`:1380`), gated by `isCantileverFullBlockCandidate` (`:1332`) which **requires `state.isSolidRender()`** (`:1340`). A fence is not solid-render → excluded → falls through to `shouldOffset`→`hasSlabInColumn` which walks down from air and returns false → dy=0.0.
  3. Consequently `freezeLoweredOnPlace` (`SlabAnchorAttachment.java:236`) reads `dy=getYOffset=0.0` (`:244`), the `dy<0` branch (`:245`) where `slabLoweringIsSideInheritedOnly` lives is **never entered**, and it records FROZEN_FLAT via the `dy≈0` structural branch (`:264`). **The audits' claim that an air-gate already exists at `slabLoweringIsSideInheritedOnly:1561-1569` is FALSE on this HEAD** — that method (now `:1553-1561`) only checks `hasLoweredCarrierBelow`, no `pos.below()` air-gate.
- **Minimal fix (air-gated geometric merge, additive):**
  - **Part A — slab beside a lowered full block.** In the slab branch, BEFORE the `:1682` gate, add: if `pos.below()` is air AND a same-Y horizontal neighbour is a genuinely- or cantilever-lowered full block (`isGenuinelyLoweredFullBlockSource` or `isCantileverLoweredFullBlock`), return -0.5 for either TYPE (so TOP slabs lower too). Place it AFTER the compound markers (`:1654-1664`) so -1.0 compound hits still win.
  - **Part B — fence/wall beside a lowered neighbour.** Add `isCantileverLoweredConnectingObject` mirroring `isCantileverLoweredFullBlock` but dropping `isSolidRender` from the START candidate (keep: air-below, not-slab, not-EntityBlock) and accepting a lowered fence/wall as a source; call it before `beta35FenceWallVariantContactDy` (`:1842`). Keep it inside the `IN_GET_Y_OFFSET` guard, `MAX_CHAIN_DEPTH`-bounded, and exclude cantilever members as sources (as `isGenuinelyLoweredFullBlockSource` already does) to avoid a self-sustaining lane.
  - **Part C — freeze correctness.** Once A/B make `getYOffset` return -0.5, `freezeLoweredOnPlace`'s `dy<0` branch runs; `slabLoweringIsSideInheritedOnly` (`:1553`) must return false here so it ANCHORS -0.5 (not FROZEN_FLAT). Add the `pos.below()`-is-air → false guard the audits intended — it is genuinely missing and is needed so the cantilever anchors instead of freezing flat.
- **Regression risk (the NEVER-POP rail):** every clause MUST be `pos.below()==air`-gated. A slab/fence placed on SOLID ground beside a lowered lane must keep `getYOffset=0.0` and stay FROZEN_FLAT (Julia's law — aim was the flat ground). The existing `SlabbedLabBsFbAdjacentPlacementProofClientGameTest` "side slab beside a recursive lowered DOUBLE carrier" contract (expects -0.5) routes through the slab-below carrier path, not the new air-below branch — confirm unchanged. Re-run the -1.0 compound matrix to confirm the new pre-gate branch doesn't intercept compound hits (compound markers checked first at `:1654-1664`).
- **Live test (`/slabdy`):**
  1. Aim LOWER half of a -0.5 full block's side, place slab over air → BOTTOM slab, `/slabdy` **-0.500**, flush; break the neighbour → must NOT pop (stays -0.500).
  2. Aim UPPER half → TOP slab, `/slabdy` **-0.500**, flush.
  3. Fence beside a lowered fence over air → `/slabdy` **-0.500**; stone beside a lowered fence over air → -0.500.
  4. **Regression:** place a flat slab/fence on stone directly beside a lowered block → `/slabdy` **0.000**, must NOT snap down.

---

## ROOT CAUSE 3 — Slab placed against the SIDE of a VERTICAL compound (-1.0) stack lands at -0.5 (marker never authored for two legal remap reasons)
**Confidence: MEDIUM** (audit could not compile on 26.1.2; this case has ZERO gametest coverage). **Impact: MEDIUM** (narrow: slab-held against a vertical stone-on-lowered-slab stack with no horizontal lane neighbour).

- **Breaks scenario:** `compound-stacked-lowered` sub-case (B) — holding a SLAB against the side of a compound full block whose source has no horizontal lowered side-lane: lands at the correct cell but dy=-0.5 instead of the aimed -1.0 (floats a half-block above). Sub-case (A), holding a full block, already obeys WYSIWYG.
- **Root (file:line):** `findLegalCompoundSlabRemap` (`SlabSupport.java:907`) returns `legal()=true` with reasons `COMPOUND_BELOW_LANE_SIDE_SLAB` (`:1064`) and `COMPOUND_SUPPORT_MISSING_VISIBLE_OWNER_SIDE_SLAB` (`:1083`). But the marker-write switch in `BlockItemPlacementIntentMixin.java:1128-1140` only handles `COMPOUND_VISIBLE_SIDE_LOWER_SLAB` / `_UPPER_SLAB` / `_DOUBLE_SLAB`. The two reasons fall through with NO `COMPOUND_VISIBLE_SIDE_*_INTENT` set, so `getYOffsetInner` (`:1657-1664`) can't return -1.0 and the slab falls to the geometric `isAdjacentSideSlabLowered` branch → -0.5. Secondary: `compoundBelowLaneResultType` (`:1106-1111`) splits TOP/BOTTOM at `hitY >= sourcePos.getY()` (the visual TOP) instead of the visual MID `sourceY-0.5` used by `isCompoundVisibleSideUpperHit/LowerHit` (`:1124/:1138`), so the slab TYPE can also be wrong for a lower-half aim.
- **Minimal fix:** in `BlockItemPlacementIntentMixin.java:1128-1140`, extend the switch: when `remapDecision.reason()` is `COMPOUND_BELOW_LANE_SIDE_SLAB` or `COMPOUND_SUPPORT_MISSING_VISIBLE_OWNER_SIDE_SLAB`, set `COMPOUND_VISIBLE_SIDE_UPPER_INTENT` when `resultType()==TOP` else `COMPOUND_VISIBLE_SIDE_LOWER_INTENT` (sourcePos + candidatePlacementPos), and ensure the place-RETURN result predicates persist that candidate so `getYOffsetInner` reads -1.0. Separately fix `compoundBelowLaneResultType` (`:1107`) to split at the visual mid: `return (hitPos.y >= sourcePos.getY() - 0.5) ? TOP : BOTTOM;`.
- **Regression risk:** the full-block side case (A) routes through the LOWER/UPPER branches (`:986-1043`), unaffected — these two reasons only arise for slab items at `legalLaneCount==0` (a legit continuation lane is handled earlier at COMPOUND_HORIZONTAL_CONTINUATION_LANE). The midline change could flip TOP/BOTTOM near the boundary; verify against the compound resting-state contract tests.
- **Live test (`/slabdy`):** build a vertical stone-on-lowered-slab compound (-1.0). Aim its side LOWER half, place slab → `/slabdy` **-1.000**, BOTTOM, flush. Aim UPPER half → `/slabdy` **-1.000**, TOP, flush. Re-run `vanillaVerticalCompoundStackTopMustBeFlush` and `compoundAnchorBlockMustFollowSlabBelowNotSinkWhenFlushed`. Add a real `useOn` gametest asserting placed dy==-1.0 for both halves (this case is otherwise uncovered).

---

## ROOT CAUSE 4 — (narrow edge) Block placed ON TOP of a CANTILEVER-lowered full block reads 0.0 instead of -0.5
**Confidence: HIGH. Impact: LOW** (rare sub-case; the standard on-top scenario already obeys WYSIWYG — do not touch it).

- **Breaks scenario:** `slab-and-block-on-top-lowered` ONLY when the target below is a CANTILEVER-lowered full block (air directly beneath it, not anchored, not on a slab). Its outline is moved -0.5 so the crosshair aims at Y+0.5, but a block placed above reads dy=0.0 and sits at Y+1.0.
- **Root (file:line):** the downward walks `hasSlabInColumn` (`:2173`) and `slabColumnYOffset` (`:2197`) TERMINATE at air; `isPersistentLoweredBottomSlabCarrierNonRecursive` requires `isAnchored`/`hasBottomSlabBelow` on the cell directly below — a cantilever FB satisfies none. No notion that the immediate support is itself cantilever-lowered.
- **Minimal fix (optional, air-gated):** make the "lowered support directly below" probe recognise a cantilever-lowered FB. Safest scope: do NOT broaden `isLoweredFullBlockCarrier`; instead add an explicit air-gated cantilever-support clause in `slabColumnYOffset` (`:2197`) and in `isPersistentLoweredBottomSlabCarrierNonRecursive`'s qualifier (`SlabAnchorAttachment.java:1152`) that also accepts `isCantileverLoweredFullBlock(below)`.
- **Regression risk:** over-broadening the carrier risks the vertical compound stack contract (L0=0.0,L1=-0.5,L2=-0.5,L3=-1.0) and the NEVER-POP freeze-flat law. Keep strictly air-gated to the support below.
- **Live test (`/slabdy`):** build a cantilever FB (stone on a slab carrier, remove carrier so stone cantilevers -0.5 with air below), right-click a bottom slab on its top → `/slabdy` on the placed slab expects **-0.500** (currently 0.0). Re-run all gametests for L0..L3.

---

## Ordered fix plan

1. **Root Cause 1 (TS guard in `hasBottomSlabBelow`).** Highest impact + lowest risk + smallest diff (one method, single choke point). Closes the only TS-interop WYSIWYG violation and reinforces the world-hole headline goal. Ship first; live-verify vanilla-vs-TS slab with `/slabdy`.
2. **Root Cause 2 (cantilever side-merge: slab+fence/wall, Parts A/B/C).** Biggest behavioural cluster; fixes Julia's Pic-1. Land Part A (slab) and Part C (freeze air-gate) together first — they share the `pos.below()`-air rail and are testable via `/slabdy`. Then Part B (fence/wall) as a follow-on. Every clause air-gated; re-run the BsFb proof test and the -1.0 compound matrix.
3. **Root Cause 3 (compound-side slab marker authoring + midline split).** Medium confidence, zero current coverage — gate behind a NEW real-`useOn` gametest asserting dy==-1.0 for both halves before calling it done.
4. **Root Cause 4 (cantilever-FB-target on-top edge).** Optional/last; narrow, and the common on-top path already works. Only attempt with the full 29-test suite green and explicit air-gating.

Cross-cutting caveat: only `GhostLoweredCollisionProofTest` + `Slabbed2612LoweringContractTest` compile on 26.1.2 (`build.gradle:114-115`); the other ~20 SlabbedLab tests are Yarn-named and excluded, so RC2/RC3/RC4 long had NO headless coverage. **UPDATE 2026-06-18:** `Slabbed2612DyFingerprintTest` (onPlaced/getYOffset) + `Slabbed2612UseOnPlacementTest` (real `useOn` via mock player) now cover RC2 (dy −0.5) and RC3 (dy −1.0, both halves) headlessly; RC3's residual is narrowed to the slab-TYPE policy (midline split, control-proven real) + client cell-targeting. RC4 still has no useOn coverage. Live-confirm the type policy + targeting in the Modrinth TS profile with `/slabdy`.
