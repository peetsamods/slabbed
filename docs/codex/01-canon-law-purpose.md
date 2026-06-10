# The Slabbed Canon: Law, Purpose, Case Law, and Operating Doctrine

Prepared for Julia / Slabbed  
Date: 2026-05-29  
Format: Markdown project essay / doctrine reference

## Source basis

This essay is synthesized from the current Slabbed source pack, Notion project pages, and GitHub repository evidence. The highest-authority references are the consolidated Slabbed doctrine/source pack, especially `01_SLABBED_CANONICAL_DOCTRINE.md`, `03_SLABBED_BUG_BLASTERS.md`, and `04_SLABBED_WORKFLOWS.md`. The recent Notion pages add newer case law, especially the Terrain Slabs compatibility line and the 2026-05-27 Bug Blaster updates. GitHub provides code and public-repo evidence: `SUMMARY.md`, `RULES.md`, `SlabSupport.java`, and `SlabSupportStateMixin.java` show the implementation shape that gave rise to the doctrine.

Important caveat: Notion/source-pack records the newer operating truth and case-law history. Public GitHub evidence is still valuable, but it may lag local worktrees, compatibility branches, and recently pushed release branches. When they disagree, the source pack and current Notion status are stronger authority than an older public default-branch snapshot.

---

## 1. What Slabbed is

Slabbed is not merely a mod that makes blocks sit lower. Its real purpose is deeper: it tries to make Minecraft’s slab-supported building behavior feel physically honest. If a block, object, torch, wall, fence, slab lane, or compatible third-party object appears to be supported by a slab, then the player should be able to trust that appearance. It should sit where it appears. Its outline should hug what is visible. The crosshair should target what the player is looking at. Placement should create the state the player thinks they are authoring. Survival/update behavior should not later contradict that state. The game should not show one object, outline another object, click a third object, and save/reload into a fourth.

That is Slabbed’s core product promise: slab support must be visually coherent, mechanically trustworthy, and internally lawful. The mod is about support semantics plus player trust. Its job is not to trick Minecraft into accepting impossible states. Its job is to define which slab-supported states are legal, then make model, outline, raycast, collision, placement, survival, persistence, reload, and live feel all describe the same legal state.

This matters because Minecraft’s vanilla engine is not built around “objects can sit at arbitrary visual heights.” Vanilla block states, collision shapes, outline shapes, raycast traversal, neighbor updates, culling, and placement behavior often assume that a block occupies its native block cell in a familiar way. A bottom slab is half-height. A top slab is upper-half. A double slab behaves like a full block. A normal full block sits on the integer grid. Slabbed intentionally bends some of those assumptions, but it cannot bend them casually. Every bend creates pressure in several systems at once.

The project’s central lesson is that slab support is not one feature. It is a contract across multiple independent surfaces. A block sitting on a bottom slab is not “fixed” just because its rendered model appears lower. If the outline still floats, the fix is fake. If the outline lowers but raycast still hits the old place, the fix is fake. If placement works but the object pops off on a neighbor update, the fix is fake. If a test says green but Julia can still feel the hitbox jump in live play, the fix is not finished.

Slabbed is therefore a law-driven mod. It has a constitution, case law, named legal states, forbidden patterns, proof gates, and savepoint discipline. That may sound dramatic for a Minecraft mod, but the history proves why it became necessary: without law, each local fix creates a new contradiction somewhere else.

---

## 2. The core product intent: global slab support

The current canonical intent is global slab support. Ordinary full blocks anchoring on slabs is not an optional side experiment. It is core product behavior. Earlier “selective-only” interpretations are stale unless explicitly scoped to a future category slice. The basic promise is broad: full blocks and other supported objects should be able to use slabs as support where Slabbed law says they can.

However, global slab support does not mean “everything is a slab now.” It does not authorize a global solidity rewrite. It does not mean stairs, fences, walls, panes, trapdoors, or every third-party partial block automatically become Slabbed support sources. It does not mean rescue logic may grab anything that looks lowered. It does not mean object categories can be expanded casually because they happen to look adjacent to slab behavior.

The correct distinction is this:

- Slabbed’s product scope is globally slab-supporting.
- Slabbed’s implementation must remain explicit, named, and proof-backed.

That distinction is one of the most important parts of the canon. A central authority that defines legal slab support is good. A broad lie that tells vanilla every slab-like or partial block is solid/supporting everywhere is bad. The first is law. The second is a ghost factory.

Global slab support also means the baseline lane is sacred. Ordinary full-block anchoring on slabs must not be sacrificed during later category work, compatibility work, or targeting rescue work. If a change regresses ordinary full-block-on-slab behavior, it is not a harmless side effect. It is a product regression. The slice stops.

---

## 3. Why Slabbed is hard

Slabbed’s difficulty comes from the fact that Minecraft presents a single world to the player, but internally splits that world into many almost-independent questions.

The model asks: where should the mesh render? The outline asks: where should the selection box draw? The raycast asks: what did the crosshair hit? Placement asks: what state should be authored into the world? Collision asks: what does the player and other entities physically collide with? Survival asks: should the block remain after neighbor updates? Persistence asks: will the block still mean the same thing after save, reload, chunk unload, or support removal? Client sync asks: does the client know the same support truth as the server? Render-region lookup asks: can a chunk render view access the same anchor/carrier facts as the real client `World`? Culling asks: should adjacent faces be drawn, or hidden? Release packaging asks: can the mod launch without debug/dev classes that were excluded from the release jar?

Most normal Minecraft behavior survives because those systems make the same broad assumptions. Slabbed breaks just enough of those assumptions that local fixes become dangerous. A block may be visually lowered by `dy=-0.5`, but vanilla ray traversal may still test the native cell. A persistent anchor may exist on the server, but the client mirror may not receive it. A proof may query the real `World`, while live chunk rendering uses a non-`World` `BlockView`. A culling path may treat a custom slab as generic Slabbed support, hiding faces that should remain visible. A slab-held targeting guard may protect one legitimate top-face hit, then incorrectly suppress another visible owner in a nearby seam case.

This is why Slabbed cannot be governed by vibes or broad patches. The only stable approach is named state law, single-source semantics, and narrow proof-backed implementation.

---

## 4. The Canonical State Law

The first law of Slabbed is this: every slab-supported placement must normalize into one legal Slabbed state before it is allowed to complete.

A placement hook has only three honorable options:

1. produce a canonical legal Slabbed state;
2. preserve a canonical vanilla state;
3. reject or defer the placement.

It must not create a weird state and rely on model, outline, raycast, collision, survival, or rescue to explain it later. That pattern is forbidden because it creates “cursed” states: states that look correct from one angle and collapse under another system.

A state that looks contradictory in vanilla terms can still be legal in Slabbed, but only if it is named, documented, and proven. For example, `stone_slab[type=top] + dy=-0.5` looks odd from a vanilla perspective. A top slab is normally the upper half of its block cell. But inside Slabbed’s lowered side-lane grammar, a top slab with `dy=-0.5` can be legal because it represents a named lowered lane state with defined model, outline, raycast, placement, and ownership behavior. Outside that grammar, the same pairing is illegal or suspect.

The point is not that vanilla-looking states are always good or vanilla-unusual states are always bad. The point is that legality comes from Slabbed law, not accident. The question is never merely “does the block state look weird?” The question is “is this a documented legal Slabbed state, and do all required surfaces agree?”

---

## 5. SlabSupport as semantic authority

`SlabSupport` is not just a utility class. It is the semantic authority for slab support. Mixin code may collect context, intercept Minecraft calls, and ask questions, but it must not independently invent lowered lanes, support eligibility, anchor legality, dy compatibility, merge outcomes, or rescue ownership rules.

This authority rule exists because early bugs repeatedly came from scattered local decisions. One mixin would decide that a state should be lowered. Another would decide that it should remain vanilla. A rescue path would infer a target owner from visuals. A placement path would author a different state. A survival path would later reject the result. Each decision was locally plausible, but globally incoherent.

A stable Slabbed system needs central semantics. If a new support law is needed, it belongs in the authority layer first. Hooks can then consult that authority. This is how the project avoids every mixin becoming a tiny constitution of its own.

The public GitHub snapshot reflects this centrality: `SlabSupport.java` is described as the “Central helper for slab support semantics,” and contains the shared support/dy logic for bottom slabs, top slab underside support, ceiling support, chain offset, recursive guards, adjacent side slabs, persistent anchors, and special visual classes. `SlabSupportStateMixin.java` then consumes those semantics for support, outline, and raycast behavior. That division is the right shape: semantics centralized; hooks guarded and subordinate.

---

## 6. The Visual Triad Law

The Visual Triad is the project’s most famous law:

1. model — what the player sees;
2. outline — what the selection wireframe shows;
3. raycast — what the crosshair actually targets.

For any slab-lowered or slab-shifted object, all three must agree. If model changes but outline does not, the feature is broken. If model and outline agree but raycast still hits the old location, the feature is broken. If raycast rescue makes clicking work while the model or outline lies, the feature is broken. There is no partial credit for two out of three.

The canonical client dy authority is `ClientDy.dyFor(world, pos, state)` where client dy logic is needed. In older/current code paths, `SlabSupport.getYOffset(...)` is the semantic source consumed by outline and raycast hooks; the doctrine later consolidates this through the client dy authority. The crucial rule is not the specific filename but the architecture: there must be one dy decision, not three competing guesses.

No duplicate dy logic. No shared mutable “current dy” state. No model-only fix. No outline-only fix. No raycast rescue pretending to solve visual drift. If contextual state is needed, isolate it and clear it safely. If a change updates only one or two triad surfaces, stop.

The Visual Triad is not aesthetic polish. It is correctness. Slabbed exists so the player can believe the world. When the visible body, selection box, and interaction target diverge, player trust collapses.

---

## 7. Collision is not the Triad, but it still matters

A subtle but important point: collision is not the same as model/outline/raycast. Early GitHub summary evidence explicitly notes that collision offsets were removed to avoid player clipping. `SlabSupportStateMixin` also documents that collision shapes are intentionally not offset because offsetting them caused player clipping when walking from slab surface to full block collision.

That is the kind of compromise Slabbed must handle carefully. Visual correctness does not automatically mean collision geometry should be moved. A fence or wall may keep tall collision while its selection/raycast/visual stack aim follows the visible body. A placement-time acting-player collision exception may be legal as a narrow finalization tool, but broad collision lies are not legal.

The rule is: collision, placement, and survival must share the same legal state, but they do not always have to use identical geometry. They must not contradict each other in player-facing behavior. Collision should not make unrelated vanilla behavior silently accept Slabbed states. Placement should not author something survival cannot explain. Survival should not preserve something placement could never lawfully create.

---

## 8. Legal state categories

### 8.1 Legal vanilla slab states

The base vanilla slab states remain legal with vanilla dy and vanilla geometry:

- bottom slab, `dy=0`;
- top slab, `dy=0`;
- double slab, `dy=0`.

These are not Slabbed-lowered states. They are vanilla states Slabbed must preserve unless a named Slabbed interaction law says otherwise.

### 8.2 Legal Slabbed-lowered full-block states

Known legal lowered full-block states include:

- an ordinary full block anchored on a bottom slab with `dy=-0.5`;
- an ordinary full block in a proven lowered vertical chain with `dy=-0.5`;
- an ordinary full block preserved by a valid persistent anchor after original support changes;
- an ordinary full block side-adjacent to a valid anchored/lowered full-block neighbor when authoring creates that lowered state.

These states are central to the product. A full block sitting on a bottom slab is the baseline promise, not a fringe case.

### 8.3 Legal Slabbed-lowered slab lane states

Lowered slab lane states are legal only inside the lowered lane grammar. Examples include:

- side-lane `TOP` slab with `dy=-0.5`;
- side-lane `BOTTOM` slab with `dy=-0.5`;
- side-lane `DOUBLE` slab with `dy=-0.5`;
- lowered `DOUBLE` side target lower-half placement producing `BOTTOM dy=-0.5`;
- lowered `DOUBLE` side target upper-half placement producing `TOP dy=-0.5`;
- lowered side-lane merge producing `DOUBLE dy=-0.5`;
- lowered `TOP` up-click merge producing `DOUBLE dy=-0.5` when proof covers it;
- real-placed legal lowered `BOTTOM` slab above an anchored/lowered ordinary full block, when persistent carrier truth is explicitly proven.

The lowered slab lane grammar was a major turning point. Before it, lowered slabs behaved like a pile of local exceptions. After it, `TOP`, `BOTTOM`, and `DOUBLE` gained named roles in a legal lowered lane, including compatible inheritance and lowered `DOUBLE` ownership.

### 8.4 Legal custom slab compatibility states

Terrain Slabs compatibility created a new category of law: named direct-support compat, not generic promotion.

The Terrain Slabs line taught that third-party custom slab blocks can only participate in Slabbed support through a named, proven, scoped compatibility surface. They must not be promoted into generic Slabbed slab support, recursive carrier scans, lowered slab lane inheritance, or culling-sensitive paths. A valid dry bottom Terrain Slabs state may directly support certain objects through the named compat law, but that does not make every Terrain Slabs state a Slabbed slab.

This is the correct model for compatibility in general: direct, named, narrow, proof-backed, and culling-safe.

### 8.5 Compound lanes and design caution

Compound `dy=-1.0` behavior has appeared as a pressure point, especially in torch and compound support cases. The lesson is not “every deeper offset is automatically legal.” The lesson is that deeper lanes require authored source/depth law. A compound full-block lane may be legal when named and proven, but compound slab lanes are much more dangerous because they expand slab grammar itself. Any future deeper-lane work must explicitly define source identity, lane depth, survival policy, inheritance policy, and max-depth boundaries.

The safe doctrine is: do not treat `dy=-1.0` as “just another offset.” Treat it as a second authored lane that needs its own legal grammar.

---

## 9. Illegal and suspect states

The following are illegal or suspect unless a future architecture slice promotes them with proof:

- any slab type + dy pairing not already listed as legal;
- a slab type whose vanilla vertical meaning conflicts with assigned dy outside lowered lane grammar;
- lowered air as support truth;
- lowered state inferred only from neighboring visuals without a canonical support relationship;
- normal-lane `dy=0` slab produced from a valid lowered-lane interaction;
- any state that exists only because rescue rewrites player targeting later;
- generic third-party slab promotion without a named compat law;
- broad culling disablement as a compatibility fix;
- broad `isSideSolid` or sturdy-face lies;
- rescue based only on generic lowered visuals;
- placement success treated as proof of survival;
- proof that manually promotes source truth not present in the live repro;
- release jars that exclude debug classes but retain production bytecode references to them.

The most important illegal pattern is the “downstream apology” state: a placement creates a cursed state, then model/outline/raycast/rescue are patched to hide the curse. That is not Slabbed law. That is debt.

---

## 10. The no-global-lies doctrine

No global lies is the backbone of the entire project. Slabbed may use central authority, but it must not globally rewrite vanilla truth and hope nothing notices.

Forbidden unless explicitly proven and regression-defended:

- broad `isSideSolid` rewrites;
- broad sturdy-face lies;
- broad shape/support redirects outside slab context;
- global collision changes that silently accept unrelated behavior;
- rescue from generic slab support alone;
- rescue from generic lowered visuals alone.

A central Slabbed authority is not a global lie. It defines Slabbed law and lets guarded hooks consult it. A global lie changes vanilla semantics everywhere and creates hidden consequences. The Terrain Slabs culling leak proved this distinction in compatibility space: treating custom slabs as generic support leaked Slabbed culling/support assumptions into blocks whose model and culling semantics were not compatible, creating visible holes.

The canon therefore favors narrow hooks with tight conditions and early exits. Shared hooks are allowed only when their blast radius is understood and defended by regression proof.

---

## 11. Manual live verification as final authority

Automation is necessary, but it is not sovereign for feel bugs. Slabbed has repeatedly produced false greens: tests passed, but live play immediately showed targeting weirdness, ghost faces, clipping, moving-up behavior, or no meaningful difference.

The doctrine is simple: when the bug is about visual feel, targeting feel, lower-half interaction, rescue behavior, ghost blocks, weird hitboxes, or camera-sensitive ownership, Julia’s live test is the final authority. If automation passes but live play is still red, the next step is not “ship anyway.” The next step is to write a red proof that fails for the same reason live play failed.

This rule is why the project has a Goblin/live testing loop. Live testing records the exact shape, held item, aim location, and wrong behavior. Then the team extracts one repeated illegal state or contradiction, adds or identifies a red proof for that mechanism, fixes one layer only, retests, and savepoints immediately after a confirmed live win.

---

## 12. Savepoint discipline

Slabbed’s savepoint rule exists because too many wins were once stacked into dirty trees, making recovery painful and regressions hard to attribute.

The rule now is:

> One live-confirmed or proof-confirmed win → commit → annotated tag → push branch → push tag → verify final tree → then continue.

A Bug Blaster is not final just because the mechanism is understood. It is not final just because proof passed. It becomes final only after mechanism, invariant, fix, proof, commit, tag, push, and final tree verification.

This is not bureaucracy. It is survival. Slabbed bugs are layered enough that a “working” dirty tree can hide multiple unrelated fixes and regressions. Savepoints preserve the ability to say exactly what changed, exactly what proof passed, and exactly where to roll back.

---

## 13. Bug Blasters as case law

Bug Blasters are Slabbed’s case law. They are not normal release notes. They are doctrine-worthy failures: repeated, expensive, confusing, or layered bugs whose mechanism teaches a reusable invariant.

A Bug Blaster records:

- the protected invariant;
- the root cause mechanism;
- the precise fix;
- proof evidence;
- savepoint commit/tag;
- status.

The core Bug Blaster doctrines are:

- automation passing does not override live failure;
- rescue must be proven before broadening;
- debug tools must not leak into normal runs;
- one symptom, one layer;
- placement is not survival;
- model, outline, and raycast must agree;
- proof without commit/tag/push is not final;
- proof fixtures must mirror live source truth.

Those doctrines are the compressed wisdom of the project. Each one exists because violating it cost real time.

---

## 14. Major problems faced and overcome

### 14.1 The early offset era

The early project goal was broad and intuitive: provide generic slab support so blocks and objects visually anchor to slab surfaces. Public GitHub summary notes show central offset helpers such as `SlabSupport.shouldOffset()` and `SlabSupport.getYOffset()`, with `-0.5` for blocks on or above bottom slabs, `+0.5` for hanging blocks under top slabs, and `0.0` otherwise. Chain stacks, beds, double blocks, wall-mounted blocks, block entity rendering, minecarts, item frames, lanterns, redstone dust, and other categories all entered the support conversation.

That early breadth exposed the first deep lesson: lowering visuals is not enough. Collision offsets caused clipping. Outline and raycast needed to follow model. Categories had separate placement and survival rules. Some vanilla behaviors already worked. Others looked like they worked but lacked proof. The project began moving from “offset things” toward “define legal support states and prove all surfaces.”

### 14.2 The persistent anchor and client mirror era

Ordinary full blocks on bottom slabs needed to remain visually lowered even when original support changed in lawful ways. That required persistent anchor truth. But persistent truth must exist on both server and client. The Client Anchor Mirror Copy-On-Write Sync Bug Blaster captured a classic failure: the server retained `anchored=true` and `fullDy=-0.5`, while the client mirror did not update because the attachment set was mutated in place and Fabric sync did not see a new object reference. The fix was copy-on-write mutation so sync could detect changes.

This case taught that “state exists” is not enough. It has to exist on the side and in the view where the consuming system reads it.

### 14.3 World vs render-region split

World vs Render Region Anchor Split was another foundational case. Persistent anchor truth existed and tests reading the real client `World` passed, but model rendering used a `ChunkRendererRegion` / non-`World` `BlockView`. The render path could not see the same anchor facts, so model dy collapsed to `0.0` while world proof looked green.

The fix added a client render-view bridge so non-`World` render views could resolve anchor truth through the real client world where appropriate. The doctrine became stronger: any persistent anchor or lowered carrier used for model/triad behavior must be readable by every view that needs it — real client `World`, non-`World` render views, model path, outline path, raycast path, and targeting/rescue path.

### 14.4 BS-FB-0.5S and side slab persistence

The shorthand BS-FB-0.5S captured a recurring shape: bottom slab support, full block lowered onto it, side slab sitting lowered at the half-height side position. This shape looked simple to the eye and complicated to the engine.

A lowered side slab attached beside an anchored lowered full block had to preserve `dy=-0.5` as long as the lowered full block remained valid and anchored, even if the original bottom slab support was removed. At first, side-slab lowering recomputed only from physical bottom-slab support, so it lost dy when the original support disappeared. The fix taught that anchored lowered full blocks can be inherited support for adjacent side-lane slabs — but only through named authority, not visual guesswork.

### 14.5 Visible-face ownership

A lowered side slab must own the visible face the player aims at. If model and outline occupy lowered space, targeting must not return `MISS` through that visible surface. The BS-FB-0.5S Side Slab Visible-Face Ownership case showed that vanilla ray targeting could terminate as `MISS` before testing the slab’s offset outline at lowered Y. The fix added narrow retargeting for lowered `BOTTOM` slabs using the existing offset outline shape as ownership source.

The key lesson: raycast ownership must follow visible lowered bodies, but rescue must remain narrow and proof-backed.

### 14.6 Torch and compound dy failures

The torch release closure exposed a layered triad failure. The torch body, flame, outline, raycast, and rescue ownership all had to align. In compound cases, `SlabSupport.getYOffset(...)` capped a floor torch at `-0.5` when `-1.0` was needed. Particles had hardcoded offsets. Rescue accepted only `dy == -0.5`. Wall torch particles were not covered.

The fix required a red proof for compound torch dy, shared dy for particles, widened visual ownership to negative dy, comfort selection, and narrow wall-torch particle correction. This was not one missing line. It was an example of how one visual object can span model, particle, outline, raycast, and rescue layers.

### 14.7 Chain and vertical stack ownership

Vertical chains and stacks revealed another class of problems: correct lowered outlines could be found, then rewritten to the wrong owner by native-cell seam logic. One bug made each lower visible quarter owned by the block below. Another slab-held path stole visible lowered full-block ownership because it over-prioritized slab placement intent.

The fixes narrowed native-cell seam rewrites to the cases where slab-held placement actually needed them and made the retarget path choose the closest valid lowered outline owner. The lesson became: held slab placement priority must not globally steal visible lowered ownership. Slab placement wins only when a real equal-or-closer side-slab placement candidate owns the interaction.

### 14.8 Lowered slab lane grammar

Lowered slab behavior eventually had to become a grammar, not a pile of exceptions. The Lowered Slab Lane Grammar and Double Ownership case established that lowered slab lanes preserve state through compatible chains and merges; lowered `DOUBLE` side-lane slabs own visible lower half; lower-half clicks on lowered `DOUBLE` produce `BOTTOM dy=-0.5`; upper-half clicks produce `TOP dy=-0.5`; merges can produce `DOUBLE dy=-0.5`.

This was a major ratification point. It proved that states which look odd in vanilla terms can be legal if named, documented, and proven.

### 14.9 Lowered DOUBLE recursion and side-lane inheritance

Lowered `DOUBLE` slabs needed to act as side-lane carriers only through recursion-safe support authority. A naive support check re-entered the same side-lane loop and caused `StackOverflowError`. The fix kept a persistent-anchor fast path and only recursed downward with side-lane disabled, proving the boundary in default `runClientGameTest` routing.

This case became a warning about recursive authority. A central helper is good, but it must not call itself through shape/support paths without a guard or narrowed recursion mode.

### 14.10 Phase19 and slab-held top-hit preservation

Phase19 protected a valid top-face hit on an anchored lowered full block. Holding a slab had allowed side-slab retargeting to steal an `UP` hit because the placement-intent guard rejected all vertical faces instead of only `DOWN`. The clean fix preserved slab-held anchored lowered full-block `UP` hits while excluding `DOWN`, with focused proof using the dirty reproducer aim mechanics.

The invariant is precise: a valid top-face hit on an anchored lowered full block remains owned by that block unless a proven equal-or-closer legal side-placement candidate owns the interaction.

### 14.11 Slab-held retarget parity

Slab-held retarget parity addressed item-sensitive targeting. The same camera ray could resolve correctly while holding `stone` but incorrectly while holding `stone_slab`. The root cause was that slab-held targeting suppressed the same lowered-owner correction that worked in the full-block-held path.

The fix narrowed slab-held suppression so true slab placement intent remained protected while visible lowered ownership could still win. Julia live-tested the result and confirmed it felt right enough to save. This case is now central to the doctrine that held item context is important but cannot globally override visible ownership.

### 14.12 Lowered slab face placement inheritance

A visible lowered slab face placement must author the placed object into the correct legal lowered state. The project hit a bug where targeting selected the visible lowered slab face correctly, but placement authored the new adjacent object at vanilla height (`dy=0.0`). For slabs this caused ghost-face mismatch. For ordinary blocks it made a legal-looking action produce a normal-height block.

The proof initially false-greened because it manually promoted the clicked source slab into persistent carrier truth, unlike the live repro. Correcting the proof exposed the actual live source-truth gap. The fix made slab placement inherit dynamic lowered slab lane without making the placed slab persistent, and made ordinary full blocks placed against legal non-persistent lowered bottom slab sources use the anchored full-block path.

This case became one of the clearest examples of the “proof fixtures must mirror live source truth” doctrine.

### 14.13 Server validation for interactive objects

Beta 3.5 produced several server-validation lessons. Trapdoors could look selected correctly on the client but snap closed because the server validation did not use the shifted legal Slabbed target. Doors needed shifted validation for both upper and lower halves. Fence and wall contact taught that collision overhang is not visual triad: tall collision may remain tall, while selection/raycast/stack aim must follow the visible body.

The big lesson: client owner correctness is not enough. For interactive blocks, server validation has to agree with shifted Slabbed legality, especially for two-block paired states.

### 14.14 Seam ownership conflict

A difficult seam ownership issue emerged between an anchored lowered full block and a lowered bottom slab directly above it. Both candidates were legal-looking. Their shifted shapes met at the same world Y plane. Tiny camera movement could flip ownership between anchored full block, upper slab, and air/behind targets.

The important diagnosis was that this was not a random outline bug. The triad machinery was mostly coherent. The unresolved issue was owner priority at a seam. A previous guard preserving anchored `UP` hits was useful for one bug but wrong for screenshot-shaped cases where the visible upper slab should win. Another path could rewrite an upper slab hit down to the anchored owner underneath. The lesson was to stop adding one-off targeting guards and instead classify seam ownership explicitly.

This remains a pattern to remember: when two legal shifted bodies share a boundary, the question is not “which rescue hack fires?” The question is “which visible owner law applies?”

### 14.15 Release jar classpath closure

Release closure produced its own Bug Blaster. A release jar is not clean merely because debug/dev/test/proof classes are excluded from the jar. Packaged production bytecode must not hard-link excluded classes. Otherwise the jar can build and scan as superficially clean while crashing at launch with `NoClassDefFoundError`.

The fix used guarded reflection or production-safe bridges, then scanned packaged production class constant pools for excluded symbols. The release gate now includes clean build, gametest, jar contents scan, and hard-reference scan. This is release hygiene as law, not clerical polish.

### 14.16 Terrain Slabs compatibility

Terrain Slabs became one of the clearest compatibility case-law lines.

First, Terrain Slabs Generic Support Culling Leak showed that third-party custom slab blocks must not be treated as generic Slabbed support sources unless their support, shape, model, and culling assumptions are proven compatible. Counting Terrain Slabs as generic support leaked Slabbed lowered support/culling context into neighboring full sand blocks, creating false neighbor occlusion and visible holes. The fix was subtractive: Terrain Slabs namespace blocks opted out of the generic support-source path.

Then Terrain Slabs Direct Custom Support Surface showed that the subtractive fix was too broad for direct support. Terrain Slabs should not be generic Slabbed slabs, but a proven dry bottom custom slab surface should be able to directly support ordinary blocks through a named compat law. The fix added `CompatSlabSurfaceKind` / `CompatHooks.customSlabSurfaceKind`, keeping Terrain Slabs out of generic `isSupportingSlab` and culling-sensitive paths while allowing direct support for proven states.

Then Terrain Slabs Lowered Object Direct Support showed that recognizing the surface was not enough; supported object classes also had to participate in the direct custom support subject path. The fix let named direct custom slab surfaces support the existing slab-sitting object class through `isSlabSitCandidate(...)`.

Finally, Terrain Slabs Live Placement Direct-Support Compatibility extended the law through live placement and special object paths: full blocks, doors, fences, torches, and redstone torch particles. The scope boundary remained strict: valid dry bottom states only, no generic slab promotion, no culling reopening without fresh proof.

This line is a model for future compatibility: start with culling safety, then add named direct support where proven, never the reverse.

---

## 15. Common issue taxonomy

### Model-only fixes

Symptom: the block looks right but outline or click target is wrong.  
Doctrine response: triad failure. Stop until model, outline, and raycast agree.

### Outline/raycast-only fixes

Symptom: the box or click target moves, but the visible mesh does not.  
Doctrine response: not a product fix; it merely makes interaction lie in a new way.

### Rescue overreach

Symptom: holding a slab, aiming near a seam, or clicking a lower face causes the target to jump to a nearby candidate.  
Doctrine response: rescue must prove visible ownership and must not steal placement or top-hit intent.

### Placement/survival split

Symptom: block places but pops off after neighbor update, support break, reload, or relog.  
Doctrine response: placement success is not survival proof. Add survival/update proof.

### Client/server split

Symptom: client selection looks right but interaction fails, snaps back, or closes.  
Doctrine response: server validation must consume shifted legal Slabbed target state.

### World/render-view split

Symptom: proof reading `World` passes but live rendering jumps or culls incorrectly.  
Doctrine response: make persistent anchor/carrier truth readable by non-`World` render views too.

### Client mirror gap

Symptom: server remains anchored/lowered but client dy resets.  
Doctrine response: verify sync, attachment mutation semantics, and client mirror lookup.

### Recursive dy/support loop

Symptom: `StackOverflowError`, render crash, or guard-dependent collapse to `dy=0.0`.  
Doctrine response: add non-recursive fact paths or scoped recursion modes; do not call full dy authority from inside a guard-dependent branch.

### Compatibility culling leak

Symptom: third-party custom slab support creates missing faces or false occlusion nearby.  
Doctrine response: do not promote custom blocks into generic support. Add named direct support only after culling-safe proof.

### False-green proof

Symptom: gametest passes but live play is still wrong.  
Doctrine response: proof does not mirror live source truth, hit vector, held item, face, support state, or update path. Correct proof before patching again.

### Release classpath leak

Symptom: release jar excludes debug classes but launch fails due to hard reference.  
Doctrine response: scan bytecode constant pools / `jdeps`, not just jar contents.

---

## 16. The workflow law

The standard Slabbed fix workflow is:

1. run preflight: root, status, branch, HEAD, tags;
2. name the player-visible symptom;
3. name the failing layer: state authority, placement, collision, survival, model, outline, raycast, rescue, release hygiene, or proof gap;
4. state the legal Slabbed state being protected;
5. write or identify a red proof where possible;
6. patch only the failing layer;
7. run compile and relevant gametest;
8. live test if the bug involves feel, targeting, ghosting, clipping, or camera-sensitive ownership;
9. commit, annotated tag, push branch, push tag;
10. verify final tree;
11. only then update Bug Blasters / Notion / release status.

After two failed implementation attempts, stop patching. The next slice is audit-only. This prevents the project from turning one failed theory into three stacked speculative fixes.

---

## 17. The layer list

Slabbed debugging must identify the failing layer before patching. The canonical layer list is:

- state authority;
- placement;
- collision;
- survival;
- model;
- outline;
- raycast;
- rescue;
- proof gap;
- release hygiene;
- live feel.

A bug can touch multiple layers, but a slice should not fix multiple layers unless proof shows they are inseparable. Most failures should be reduced to one first failing layer. If the layer cannot be identified, the slice is audit-only.

---

## 18. What legal work looks like

A legal Slabbed fix has a recognizable shape:

It begins with a named symptom. It identifies the failing layer. It states the legal state involved. It does not broaden product scope without permission. It does not alter unrelated vanilla behavior. It does not create a new implicit lane. It does not add rescue because “it feels close.” It proves the bug red where possible. It changes the smallest relevant authority/hook. It verifies triad surfaces. It checks placement and survival when applicable. It runs live testing when the bug is about feel. It savepoints immediately after a win.

A legal fix also says what it did not touch. If the bug is culling, it should not edit placement. If the bug is server validation, it should not rewrite model dy. If the bug is release classpath closure, it should not change `SlabSupport`. If Terrain Slabs direct support is being fixed, culling opt-out must remain protected.

This negative space matters. Slabbed’s history shows that nearby-looking code is often a trap.

---

## 19. What illegal work looks like

Illegal Slabbed work usually has one of these smells:

- “While we’re here…”
- “This should probably fix a bunch of cases.”
- “Just make slabs solid.”
- “Just rescue anything lowered.”
- “The model looks right, so ship it.”
- “The test passed, ignore the live video.”
- “It’s probably fine if the source fixture differs from live.”
- “The jar scan is clean, so bytecode references don’t matter.”
- “This custom slab block looks like a slab, so let it be generic support.”
- “We can call this Bug Blaster fixed before tag/push.”

Illegal work creates debt by mixing intent, implementation, and proof. Legal work separates them.

---

## 20. The purpose behind the law

The law exists because Slabbed’s player-facing goal is emotional as much as technical: the world should feel trustworthy. When a block appears lower, the player should not have to learn an invisible mental model of native cells, DDA traversal, rescue priorities, client/server validation, or render-region lookup. They should be able to look, aim, click, place, break, reload, and continue building with confidence.

That is why “live feel” is not squishy. It is the product. Slabbed is successful when the player’s intuition matches the engine’s result.

The mod’s deeper design philosophy is not “make anything possible.” It is “make the possible things coherent.” Slabbed’s legal states may be unusual, but once legal, they must be complete. They must survive contact with visuals, selection, targeting, placement, survival, updates, reloads, compatibility, and release packaging.

---

## 21. Current high-level canon summary

Slabbed’s canon can be compressed into these laws:

1. Global slab support is the product intent.
2. Global support does not authorize global lies.
3. Legal states come before fixes.
4. `SlabSupport` / designated authority owns slab semantics.
5. Model, outline, and raycast must share one dy truth.
6. Placement, collision, and survival must describe the same legal state.
7. Baseline full-block-on-slab behavior is sacred.
8. Rescue repairs access to legal states; it does not legalize illegal states.
9. Live play outranks automation for feel bugs.
10. Proof fixtures must mirror live source truth.
11. Compatibility is named and direct, not generic promotion.
12. Release purity includes bytecode hard-reference closure.
13. One symptom, one layer, one slice.
14. Proof plus savepoint closure is required before “fixed” case law.
15. If the same failure repeats, turn it into doctrine.

---

## 22. Suggested future use of this essay

This document should be used as a narrative companion to the source pack, not as a replacement for it. The source pack remains the operational authority. This essay is useful for onboarding a new AI thread, explaining why Slabbed’s process is strict, reminding future implementers why one-off patches are dangerous, and preserving the project’s accumulated reasoning in prose.

Future agents should still read the source pack in order:

1. `00_SLABBED_SOURCE_INDEX.md`;
2. `01_SLABBED_CANONICAL_DOCTRINE.md`;
3. `02_SLABBED_ACTIVE_STATUS.md`;
4. then the relevant workflows, skills, research, or Bug Blasters.

This essay answers the “why.” The source pack answers the “what is binding right now.” The repo answers “what code exists at this ref.” Notion answers “what the current project record says happened.” Codex or Claude slices answer “what exact work should happen next.”

---

## Appendix A — Condensed Bug Blaster map

| Case family | Core lesson |
|---|---|
| World vs Render Region Anchor Split | World proof is insufficient if render views cannot see persistent truth. |
| Client Anchor Mirror Copy-On-Write Sync | Server anchor truth must sync to client; in-place mutation can hide changes. |
| Anchored FB Ghost-Hitbox Rescue | Visible lowered bodies must own selection before farther targets, but rescue must not steal placement. |
| BS-FB-0.5S Side Slab Persistence | Adjacent side slabs can inherit lowered truth from valid anchored lowered full blocks. |
| BS-FB-0.5S Visible-Face Ownership | Lowered slabs must own visible faces when model/outline occupy lowered space. |
| Torch Release Closure | Model, particle, outline, raycast, and rescue can all be part of one visual object. |
| Chain / Vertical Stack Ownership | Native-cell seam rewrites must not steal closest visible lowered owner. |
| Lowered Slab Lane Grammar | Odd vanilla-looking states can be legal when named, documented, and proven. |
| Lowered DOUBLE Inheritance | DOUBLE can be a lowered lane carrier only through recursion-safe authority. |
| Phase19 Top-Hit Preservation | Valid anchored lowered full-block UP hits must not be stolen by slab-held retargeting. |
| Slab-Held Retarget Parity | Held slabs protect real slab placement, not every nearby slab-like owner. |
| Lowered Slab Face Placement Inheritance | Targeting a lowered face must author the placed object into the legal lowered state. |
| Proof Gap from Persistent Test Source | A fixture that grants extra source truth can false-green a live failure. |
| Release Jar Classpath Closure | Jar purity requires no packaged hard refs to excluded debug/dev classes. |
| Terrain Slabs Culling Leak | Custom slabs must not be generic support if culling semantics differ. |
| Terrain Slabs Direct Support | Custom slab support must be named, scoped, direct, and proof-backed. |
| Terrain Slabs Live Placement Compatibility | Direct support must cover real placement and special object paths without reopening culling. |

---

## Appendix B — Glossary

**BS-FB-0.5S** — Julia’s shorthand for bottom slab support, full block lowered onto it, and a side slab sitting lowered at the half-height side position.

**ClientDy** — client-side dy authority for visual triad surfaces where client dy logic is needed.

**dy** — vertical offset applied to a model/shape/targeting surface relative to native block position.

**Global lie** — a broad rewrite of vanilla solidity/support/shape truth that makes behavior appear to work by changing unrelated semantics.

**Lowered lane** — a named Slabbed state family where blocks/slabs occupy a shifted visual/support relationship, usually `dy=-0.5`.

**Rescue** — post-vanilla targeting correction that redirects crosshair ownership to the legal visible owner when vanilla traversal misses or chooses the wrong native cell.

**SlabSupport** — central semantic authority for slab support legality and dy support decisions.

**Visual Triad** — model, outline, and raycast agreement.

**Bug Blaster** — doctrine-grade bug case recording invariant, mechanism, fix, proof, savepoint, and status.

**Goblin / live test** — Julia’s live-play verification loop for feel, targeting, hitbox, ghosting, and visual issues.

**Savepoint** — commit + annotated tag + pushed branch/tag + verified final tree after a proof/live win.

---

## Appendix C — Reference anchors used

- Notion: `SLABBED Constitution`, `SLABBED Bug Blasters`, `Slabbed Running Log`, `Slabbed Commit Index`, `Slabbed Release Index`, `SLABBED Hub`.
- Source pack: `00_SLABBED_SOURCE_INDEX.md`, `01_SLABBED_CANONICAL_DOCTRINE.md`, `02_SLABBED_ACTIVE_STATUS.md`, `03_SLABBED_BUG_BLASTERS.md`, `04_SLABBED_WORKFLOWS.md`, `05_SLABBED_SKILLS_AND_COMMANDS.md`, `06_SLABBED_RESEARCH.md`, `07_SLABBED_ARCHIVE_AND_PRUNE_MAP.md`.
- GitHub: `peetsamods/slabbed`; `SUMMARY.md`; `RULES.md`; `src/main/java/com/slabbed/util/SlabSupport.java`; `src/main/java/com/slabbed/mixin/SlabSupportStateMixin.java`; public repo metadata showing `main` as default branch.
