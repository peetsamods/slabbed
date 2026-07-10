See LAW.md — this doc does not redefine the law; where anything below conflicts with LAW.md, LAW.md wins.

All verification done. Here is the completeness-critic report.

# Completeness Critic — Gaps, Contradictions, Unverified Claims

Verification notes: I re-read `SlabSupport.java:1014-1046`, `SlabAnchorAttachment.java:260-300, 440-472`, `SlabRigCommand.java:54-1001`, and `SlabTestKit.java:50-100` to check the claims below. The kit is 40 real items padded to 45 (`SlabTestKit.java:50-100`).

## A. Contradictions BETWEEN dossiers (design-changing first)

**A1. Ceiling-hung freeze-vs-follow — capture-mechanism vs authority-inventory (HIGH, changes the rule design).**
capture-mechanism (finding at SAA:444) says captured hanging decorations ARE frozen: capture stores every non-air server placement, and the frozen short-circuit (SS:1030-1035) runs before the ceiling-hung dispatch (SS:2474). authority-inventory says L1 hangers are "kept live by design... deliberately unfrozen followers" citing SAA:461-462. **I verified the code: capture-mechanism is right.** `capturePlacementDy` (SAA:269-283) excludes only null/client/air; the SAA:461-462 carve-out exempts hangers only from the FROZEN_FLAT *marker*, not from the dy *store*. So a hand-placed hanging lantern/hanging sign is frozen at its placement-time dy and will NOT follow a later-lowered support. The unified rule's attachment-direction table must be designed against the real behavior, and one dossier has it wrong.

**A2. MossyCarpetBlock capture — carpet-thin vs capture-mechanism (MEDIUM).**
carpet-thin's trace step 2 asserts setPlacedBy hooks fire for carpets including MossyCarpetBlock (frozen 0.0). capture-mechanism's bytecode census puts MossyCarpetBlock in the NO-SUPER setPlacedBy list (never captured, stored NaN). Both cannot be true. Landing is 0.0 either way today, but frozen-0.0 vs live-NaN diverge the moment the rule fixes the landing lane, and mossy carpet's above-topper is a second-cell case. Note the two lanes cite **different mapped jars** (`loom-cache/minecraftMaven/minecraft-common-043a8b3edf` vs `<home>/.gradle/caches/fabric-loom/.../minecraft-merged-deobf`) — the census disagreement may be a jar-identity issue (a known failure family in this project). Re-run one census on one canonical jar.

**A3. DOUBLE-merge stored-dy semantics — slab-landing vs authority-inventory (MEDIUM).**
slab-landing case (a): BOTTOM→DOUBLE merge "keeps the owner's already-stored dy, so the visible result is right by accident." authority-inventory open question: the same merge "may preserve the old stored value for a new geometry" — i.e., a hazard. Same fact, opposite verdicts, and neither traced whether vanilla merge fires setPlacedBy on the merged state (it does not per capture-mechanism's bytecode read, but only side-parity's SS:1277-1286 covers the *marked side-slab* merge). The rule needs one explicit row: merged DOUBLE's dy := owner's stored dy, re-captured or not.

**A4. Bed TEST-17 mechanism — boundary-seam vs capture-mechanism (LOW-MEDIUM).**
boundary-seam attributes bed -0.5 rows to the depth-blind presence literals (SS:2759/:3330). capture-mechanism attributes bed weirdness to FOOT-captured-on-half-formed-bed + HEAD never captured (BedBlock super-first ordering). Compatible but unreconciled: nobody states what the HEAD cell's stored dy is in the TEST-17 rows (NaN predicted) or whether the two bed halves can render split today. One recorder probe settles it.

**A5. Doc-vs-code contradiction inside the codebase itself (supporting evidence, worth citing in the design doc).**
The javadoc on `capturePlacementDy` (SAA:263-266) claims it is "Called from the placement hook AFTER the existing markers are written." Three dossiers (slab-landing step 7-8, side-parity finding 6, authority-inventory finding 1) prove the opposite: capture runs at setPlacedBy, markers at place-RETURN. The ordering bug is so load-bearing that the code's own documentation asserts the intended (never-implemented) ordering.

## B. Placement routes no lane covered

**B1. DOWN-face and into-visible-body aims (HIGH — the rule text is incomplete without them).**
Every trace is an UP-click or horizontal side-click. Nothing covers: (i) clicking the visible UNDERSIDE of a lowered owner (the ceiling of the cell below sits 0.5-1.0 low — where does a slab/lantern/hanging sign land?); (ii) placing into a cell that is grid-air but visually occupied by a deep owner's protruding body (slab-landing only notes the remap-refusal SS:1216-1223 "must not fall through"). "Lands on the clicked visible surface" must define all six faces; two are specified.

**B2. flag-2 setBlock removals leave haunted stored dy — a bypass class broader than the subclass census (HIGH for the store's soundness).**
`SlabRigCommand.java:74, 991-993, 1071-1073` documents *empirically* that flag-2 setBlock does NOT fire the attachment-removal hook, and works around it by calling `removeAnchor` explicitly before clearing. capture-mechanism's removal-hole finding covers only subclass overrides of `affectNeighborsAfterRemoval`. The flag-based bypass generalizes it: any worldgen/command/mod replacement using UPDATE flags without neighbor updates can strand a stored dy that the next placement's capture re-freezes (SAA:273 + SS:1030). No dossier owns this route.

**B3. /slabrig clear itself: covered in code, uncovered in dossiers (LOW).**
Verified: `clear` calls `SlabAnchorAttachment.removeAnchor` per cell before setBlock (`SlabRigCommand.java:991-994`), so the undo path is sound — but since TEST evidence is interpreted against rig-built fixtures, the design doc should state that rig scenery is authored via setBlock + public attachment API, i.e. rig owners are marker-authored *differently* from player-placed owners (slab-landing's A7 analysis touches this; nobody audited the rig's authoring calls for parity with the placement pipeline).

**B4. Waterlogging (MEDIUM).**
Only glancing mentions (F5 "fluid-blind" discipline; F5c open-cell allowance SS:1409-1412 in side-parity). Untraced: water-bucket into a lowered slab's cell (property-only change — does the store survive? capture-mechanism proved property changes don't fire the removal mixin for doors, but nobody applied it to waterlogging); placing against/under water; whether the fluid renders at grid height above a sunken slab (visual seam the rule doc should disclose).

**B5. Piston end-to-end (MEDIUM).**
Fragments exist (PistonBaseBlock in the NO-SUPER capture list; moved=true clears markers+store at the source, BlockOnStateReplacedAnchorMixin:36-39). Untraced consequence: a frozen lowered block pushed by a piston loses its stored dy and reads live at the destination → visible height pop, a LAW-relevant behavior that the unified-rule doc must either fix or disclose. No dossier states the destination-cell outcome.

**B6. Covered adequately, for the record:** falling blocks, dispenser routes, bone meal, fluid mechanics (powder-snow lane routes A-I); worldgen/structure placement (deliberate "terrain stays geometric," SAA:353-354); creative pick-block (non-gap — placement of a picked item still routes through BlockItem.place); entities out of scope.

## C. Kit families no lane accounts for

The real kit (`SlabTestKit.java:50-100`, 40 items). Traced families: slabs (stone/smooth_stone/oak), stone/oak_log, torch/soul_torch, candle, flower_pot, doors, oak_trapdoor, fences/gates/walls (partially — see below), carpet, bed, oak_sign, buttons, hopper/chest (as owner/removal), powder_snow_bucket, pointed_dripstone (downward only), lantern/chain (dispatch only, see A1). **Untraced kit members — each needs a family-table row (MEDIUM in aggregate, because the unified rule's stated goal is "every family"):**

- **oak_stairs** — in the L11 hard-flush list (SS:2869-2886); never-lowers is asserted nowhere as policy vs oversight.
- **glass_pane, iron_bars** — as placed subjects (iron_bars is in the L11 flush list; panes are isConnectingStructural — do they take the fence lane?).
- **white_banner** (+ wall banner) — capture fires (no setPlacedBy override), but NO landing lane claims banners; which lane produces its dy is unknown.
- **lever, stone_pressure_plate, redstone wire, repeater/comparator, rail, ladder** — appear only in the removal-bypass or NO-SUPER capture lists; zero landing traces. Repeater/comparator (DiodeBlock) are double-broken candidates: capture hole AND no landing lane. Wall-attached lever/ladder exercise the shouldOffset wall branch (SS:989-998) nobody traced.
- **daylight_detector, conduit** — EntityBlocks with partial shapes; possibly the ordinaryFullBlockContact lane (:708 accepts EntityBlock) but untraced, and conduit is nowhere.
- **pointed_dripstone pointing UP** (standing on a lowered support) — only the downward speleothem dispatch is traced.
- **soul_lantern/lantern standing vs hanging** — landing on a lowered support (standing) and under a lowered support (hanging, with the A1 freeze contradiction) both lack an end-to-end trace with the frozen store.
- **fence/gate/wall at depth** — boundary-seam holds up the fence lane formula (SS:502-503) as "the correct generalization," but that formula's supportDy input comes from the closed-codomain reader (floorTorchBottomSlabSupportDy, codomain {-1.0,-0.5,0.0,NaN} per authority-inventory). If so, the rule's own exemplar is depth-capped — nobody checked. Verify before canonizing it.

## D. Asserted-not-traced claims (spot audit)

1. carpet-thin: "snow-layer STACKING re-runs setPlacedBy" — no bytecode anchor; if wrong, layer growth bypasses re-capture. One javap of SnowLayerBlock/BlockItem replace-path settles it.
2. slab-landing variant 2 (iv): the client dy-split *timing* story ("client store not yet synced") is inferred from marker-write ordering, not traced through the sync code (`SlabAnchorClientSync.java:92-103` was cited by authority-inventory but the in-flight window was never measured).
3. boundary-seam Case B's "a stone at -1.5 would fail exactly like Case A" — extrapolated, no live row or gametest (self-acknowledged: "TEST 17 simply had no pot-on-deeper-stone sample"). Fine as prediction; needs a RED test, not a citation.
4. FROZEN_DY_ENABLED in the TEST jars: verified default-off in code (SAA:261, `Boolean.getBoolean`); how it was ON in TEST 13-17 remains unexplained across all seven lanes. The whole unified rule presumes store authority — **the flag's ship-default and the legacy-NaN-world story have no owning lane (HIGH design dependency)**.
5. **PlacementIntentState producer: unaudited by ALL lanes** (side-parity and authority-inventory both flag it as an open question; nobody closed it). Since the unified rule wants to carry the aim as a value from click to capture, the one existing aim-snapshot producer is exactly the component the designer must know — this is the largest shared blind spot (HIGH).

## E. Ranked summary (by ability to change the unified-rule design)

| Rank | Gap | Type |
|---|---|---|
| 1 | PlacementIntentState producer never audited (D5) | coverage hole |
| 2 | Down-face / into-visible-body aims undefined (B1) | route hole |
| 3 | Hanger freeze-vs-follow contradiction, authority-inventory wrong (A1) | contradiction |
| 4 | FROZEN_DY ship-default + legacy-NaN migration unowned (D4) | design dependency |
| 5 | flag-2 setBlock haunted-store bypass class (B2) | route hole |
| 6 | Fence-formula exemplar may itself be depth-capped (C, last bullet) | unverified premise |
| 7 | ~14 kit families with no landing trace (C) | family coverage |
| 8 | Waterlogging + piston end-to-end (B4, B5) | route holes |
| 9 | MossyCarpet capture contradiction + dual-jar census hygiene (A2) | contradiction |
| 10 | DOUBLE-merge verdict split (A3), bed mechanism split (A4), snow-stacking assertion (D1) | reconciliation |