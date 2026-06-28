# Slabbed 1.21.1 Release Sanity Checklist

Standardized, repeatable regression matrix for the 1.21.1 release line.

Use this with the repo-local guardrails in `AGENTS.md`, `RULES.md`, `SLABBED_SPINE.md`, and
`docs/codex/09-release-gate.md`.

## 1. Proof Types

| Tag | Meaning | Reliable? |
|---|---|---|
| GT | Headless gametest: `SlabSupport.getYOffset`, `updateShape`, survival, collision, or shape assertion | Yes |
| DY | Live `/slabdy` numeric read | Yes |
| VIS | Live screenshot/visual judgment | Julia is final gate |
| FEEL | Live movement/click/placement feel | Julia is final gate |
| N/A | Render-only entity offset with no headless path | Human-only |

## 2. Three Lanes

1. Automated GT lane: run `./gradlew runGameTest`.
2. Live dy-cruise lane: use the branch-local dev client or the approved Modrinth 1.21.1 profile and read `/slabdy`.
3. Human visual/feel lane: Julia verifies render seams, entity offsets, ghost windows, targeting feel, and placement feel.

## 3. Smoke Set

Run these first on every new build. Stop and triage if any red.

| # | Family | Fixture | Expected | Proof |
|---|---|---|---|---|
| S1 | Core lower | `stone` on `stone_slab[type=bottom]` | `dy=-0.500` | GT + DY |
| S2 | Top slab | `stone` on `stone_slab[type=top]` | `dy=0.000` | GT + DY |
| S3 | Compound | `slab -> stone -> slab -> stone` | top `stone dy=-1.000` | GT + DY |
| S4 | Freeze law | authored flat `stone`, then slab below it | stays `0.000 FROZEN-FLAT` | GT + DY |
| S5 | Ceiling follow | `hanging_roots` under lowered support | follows to `dy=-0.500` | GT + VIS |
| S6 | Hanging lantern | `lantern[hanging=true]` under lowered support | follows to `dy=-0.500` | GT + VIS |
| S7 | Thin layer | `white_carpet` / snow layer on bottom slab | `dy=0.000` | GT + DY |
| S8 | Powder snow | `powder_snow` on bottom slab | `dy=0.000` | GT + DY |
| S9 | Bed | one bed half on a slab | both halves lower | GT + VIS |
| S10 | Side slab lane | vanilla slab beside lowered full block | `dy=-0.500` | GT + DY |
| S11 | Connector step | fence/pane/wall across slab-height step | OBSERVED 1.21.1 server recompute stays connected | GT + VIS |
| S12 | Collision | lowered compound block has collision where drawn | GT + FEEL |
| S13 | Render seam | lowered-vs-flat step face | solid, no ghost window | VIS |
| S14 | Minecart/item-frame | entity render offsets | human-only | N/A |
| S15 | Reload | anchored/frozen placement survives relog | DY |

## 4. Dy Fingerprint

`src/gametest/java/com/slabbed/test/Slabbed1211DyFingerprintTest.java` is the flat cross-version
fingerprint for this branch. It emits:

```text
SLABBED-FP | <name> | dy=<value> | src=<classification>
```

The committed reference capture is `src/gametest/resources/dy-baseline.txt`.

To capture and diff:

```bash
./gradlew runGameTest --console plain 2>&1 | grep -o 'SLABBED-FP.*' | sort -u > /tmp/slabbed-1211-release-fp.txt
```

Any changed line is a behavior change. If intentional, update the in-code assertion and
`dy-baseline.txt` together.

## 5. Expanded Headless Checklist

`src/gametest/java/com/slabbed/test/Slabbed1211StandardChecklistHeadlessTest.java` backfills the
portable server rows from the 26.1.2 checklist: resting dy sweeps, top/double slab controls, floor
objects, ceiling-hung +0.5 rows, thin layers, bed coordination, compound clamp, survival predicates,
pointed-dripstone descendant dy, and connector observations.

Current proof count after this backfill: `./gradlew --no-daemon runGameTest --console plain` passes
all 65 required tests. This is a regression net only; it is not release
readiness while Julia has live REDs open.

Known branch-local observed divergences:

- `ceiling_flush` is `dy=-0.500` on 1.21.1, not `0.000` as on newer lines.
- Direct server recompute connector rows across a slab-height step remain connected on 1.21.1.
- `MC1211_DRIPSTONE_NUB_ROW` is technically green after the downward pointed-dripstone dy fix, but
  Julia/live visual confirmation is still required. It does not close lowered dripstone
  combine/place REDs.

These rows are intentional branch-local baselines. They should still show up in parity comparisons.

Current live RED overlay, 2026-06-27:

- Lowered pointed dripstone on slab combine/place remains RED.
- Chain visual triad remains RED.
- Chain/lamp gap remains RED.
- Terrain Slabs WYSIWYG cantilever placement remains RED.
- Object placement on Terrain Slabs remains RED.

## 6. What Headless Cannot Close

- Render mesh desync, ghost windows, entity render offsets, and targeting feel still require live/manual proof.
- `/setblock` does not prove the freeze-on-place law; use real placement for live confirmation.
- This checklist is a regression net, not a release-ready claim by itself.
- If live/manual evidence contradicts this checklist, update the status docs to RED first.
