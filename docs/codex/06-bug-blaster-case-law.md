# 06 — Bug Blaster Case Law

Bug Blasters preserve doctrine-grade failure patterns. They are not general notes. A final Bug Blaster requires mechanism, invariant, fix, proof, savepoint, pushed branch/tag, and final tree closure.

## Doctrine index

| Doctrine | Rule |
|---|---|
| False-Green Live Failure | Automation passing does not override live red. |
| Triad Or It Did Not Happen | Model, outline, and raycast must agree. |
| Red Proof Before Rescue Expansion | Rescue must be proven necessary before broadening. |
| Placement Is Not Survival | Placement alone never proves survival. |
| One Symptom, One Layer | Name and fix exactly one failing layer. |
| Savepoint Before Final BB | Proof without commit/tag/push is not final. |
| Proof Fixtures Must Mirror Live Source Truth | Fixtures must match live source state. |
| Release Classpath Closure | Jar purity requires no packaged debug/proof classes and no production hard-links to excluded classes. |
| Named Compat Law | Third-party slabs need named direct support, not generic promotion. |

## False-Green Live Failure

If automated proof passes but live play still shows the bug, stop implementation immediately.

Required sequence:

1. record live failure exactly
2. audit why automation missed it
3. identify failing layer
4. add RED proof that fails for same reason
5. patch smallest layer
6. rerun proof
7. live retest remains final authority

## Rescue Can Steal Placement

Crosshair rescue may repair access to a legal visible owner. It may not define product physics or steal legal placement intent. No rescue from generic slab support alone. No rescue from generic lowered visuals alone.

## Placement Is Not Survival

A block that places successfully is not proven fixed. Trigger neighbor updates, break/replace support where relevant, reload/relog where relevant, and confirm unsupported cases still fail.

## Proof Fixtures Must Mirror Live Source Truth

A proof can be false-green if it manually grants persistent state that live does not have. Before trusting a proof, confirm source block state, persistent anchor/carrier truth, support below/around source, held item, face, hit vector, and teardown expectations match the live repro.

## Terrain Slabs case law

Terrain Slabs must not be treated as generic Slabbed slabs. They may participate only through named, proven direct-support compat law.

Fixed lessons:

- generic support culling leak: custom slabs counted as generic support caused missing faces
- direct support surface: valid dry bottom Terrain Slabs can support named subjects
- lowered object support: supported object class must participate in direct custom support law
- live placement support: doors, fences, torches, and redstone particles needed the same named contract

Boundary:

```text
Do not promote Terrain Slabs into generic Slabbed support.
Do not reopen culling unless fresh RED proves culling is the failing layer.
Keep custom slab support scoped to normal dry bottom states unless new RED proof justifies generated/snowy/waterlogged states.
```

## Release gate case law

A release jar is not clean merely because debug/proof classes are absent. Production runtime bytecode must also not hard-link excluded classes. Run both jar contents scan and `jdeps` hard-reference scan.

## MC1211 decorative hanger lowered-support follow-down

```text
❗ BUG BLASTER ❗
Title: MC1211 decorative hangers clipped or gapped under lowered supports
Invariant: Decorative hangers must inherit the support block underside that is actually rendered. Full lowered supports and lowered top slabs need distinct recursion-safe dy handling; chains remain excluded.
Root cause: The 1.21.1 port had two hanger dy gaps. Full-block supports could not safely call through the normal dy path from inside SlabSupport.getYOffset because of the IN_GET_Y_OFFSET recursion guard, so decorative hangers did not follow lowered full-block undersides. Separately, the existing slab-hanger branch returned the slab dy for every slab type; lowered TOP slabs need supportDy + 0.5 because their underside sits half a block higher than bottom/double slab undersides.
Fix: Commit 94a5643e adds recursion-safe loweredFullBlockUndersideSupportDy and isBeta35LoweredFullBlockUndersideVisibleOwnerObject, wired additively into SlabSupport.java's ceiling section, and corrects the slab-hanger branch to use TYPE == TOP ? supportDy + 0.5 : supportDy. The supported decorative set is lantern, soul lantern, spore blossom, hanging roots, and pale hanging moss; chains are unchanged.
Proof: Compile passed; focused SBBS goblin route stayed GREEN with lanternUnderDy=-0.5, chainLanternDy=0, and failureLayer=NONE. Julia live-confirmed on 2026-06-03 under Indigo that lanterns and spore blossoms hang flush under warped-slab lowered top ceilings and lowered full-block stone supports. Live/manual proof must come from the branch-local current-HEAD Gradle dev client, never Applications/Minecraft, a stale jar, a wrong-head jar, or the vanilla launcher.
Savepoint: behavior commit 94a5643e, spine/docs head 20a5ac28, closure tag save/mc1211-decorative-hanger-followdown-live-confirmed. Branch pushed: yes. Tag pushed: yes. Unrelated LoweredSideSlabRetargeter.java and other dirty WIP were deliberately excluded.
Status: Fixed for the decorative hanger follow-down slice only. Do not promote to release-ready or whole-geometry fixed; the active raycast RED and SBBS manual-live proof gap remain separate.
```

## Bug Blaster template

```text
❗ BUG BLASTER ❗
Title:
Invariant:
Root cause:
Fix:
Proof:
Savepoint: commit `<hash>`, tag `<tag>`. Branch pushed: yes/no. Tag pushed: yes/no.
Status: Fixed / partial / proof pending / savepoint pending / reverted / suspect
```
