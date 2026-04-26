# Lowered Side Slab Live Manual Checklist

This checklist verifies live player feel for lowered-side-slab targeting behavior.
It complements, but does not replace, the automated proof bundle.

Passing the automated proof bundle is a prerequisite before running this checklist.
If automated proof fails, stop — do not attempt live verification.

---

## Starting point

- Run from a clean trusted savepoint.
- Prefer the latest proof bundle tag/savepoint.
- Run the automated proof bundle first:

  **Mac**
  ```bash
  bash tools/run_lowered_side_slab_proof_bundle.sh
  ```

  **Windows**
  ```powershell
  .\tools\Run-LoweredSideSlabProofBundle.ps1
  ```

- Confirm the verifier reports PASS before proceeding to live checks.

---

## World / test setup

- Use the Slabbed Lab / test world or an equivalent controlled flat test area.
- Keep test lanes visually simple: full block, bottom slab, top slab.
- Avoid extra mods unless specifically testing mod compatibility.
- Stand at a comfortable viewing distance — the crosshair, outline box, and placed block must all be clearly visible.

---

## Manual live checks (aligned to the 9-proof ladder)

Work through these items in order. Note PASS or FAIL for each.

### 1 — Full block on bottom slab: lower-half owner targeting

Place a full block on the top face of a bottom slab (lowered position).
- **PASS:** the crosshair highlights the lowered block, the outline sits at the lowered position, and clicking feels like interacting with the visible block — not empty air above it.
- **FAIL:** you must aim at invisible air, the outline floats above the actual block, or the click registers on the slab instead.

### 2 — Full block on bottom slab: lower-half side placement intent

With a full block sitting lowered on a bottom slab, place a slab against its side face.
- **PASS:** the side slab appears where you aimed, at the height that visually matches the lowered block's side.
- **FAIL:** the slab appears above or below the visible side face, or placement fails unexpectedly.

### Aim guidance for lowered side placement

- The lowered visible block can be missed if you aim horizontally from normal eye height.
- For side placement checks, aim at the **visible lowered side face itself**, not the upper vanilla-space area above it.
- Prefer testing from a position where the crosshair outline visibly hugs the lowered block.
- If one face misses, rotate around and try another side face before marking a production failure.
- Do not count a miss as a placement failure unless the crosshair is visibly outlining the lowered block side.
- Check 3 repeat-click should only be judged after Check 2 successfully placed the side slab.
- If the outline/crosshair cannot be made to hug the lowered side from any face, record **FAIL** as ray acquisition / targeting feel.

### 3 — Repeat click: no ghost-face behavior

After placing against the lowered side, click the same face again.
- **PASS:** second click either completes the double-slab or does nothing — no phantom placement appears on an invisible face.
- **FAIL:** a ghost face triggers, or a second slab appears in mid-air with no visible surface to attach to.

### 4 — Torch on full block on bottom slab: rescue targeting

Place a torch on the side or top of a lowered full block.
- **PASS:** the torch attaches to the lowered block, the outline is on the correct face, and the interaction does not steal a nearby unrelated target.
- **FAIL:** the torch attaches to the wrong block, the outline is on the slab instead of the lowered block, or an unrelated nearby block is incorrectly targeted.

### 5 — Bed on bottom slab: rescue targeting

Place a bed with its foot at a position over a bottom slab.
- **PASS:** the bed places at the lowered position, rescue targeting feels correct, bed head/foot orientation looks sane, and both halves sit at the expected height.
- **FAIL:** the bed pops off, floats, appears at the wrong height, or the head lands in the wrong block.

### 6 — Full block on full block baseline

Place a full block on a normal full block (no slab involved).
- **PASS:** vanilla-feeling placement — nothing Slabbed does should alter this lane.
- **FAIL:** any targeting, outline, or placement anomaly that would not occur in vanilla.

### 7 — Slab on normal vanilla face baseline

Place a slab against a normal full-block face (no lowered surface involved).
- **PASS:** ordinary slab placement, identical to vanilla behavior.
- **FAIL:** any unexpected targeting, model offset, or placement failure in this non-slab-lowered lane.

### 8 — Chain on full block on bottom slab: confirmed no-rescue

Attempt to place a chain against a lowered full block.
- **PASS:** chain behaves as vanilla — no rescue targeting, no special crosshair rewrite. If it fails to place or targets normally, that is correct and expected.
- **FAIL:** rescue behavior appears for chain. **Do not fix this.** Chain is an audited no-rescue target. If you see rescue here, stop and file it as a boundary violation.

### 9 — Crafting table on bottom slab: confirmed no-rescue

Place a crafting table on a bottom slab.
- **PASS:** crafting table places via vanilla logic, no rescue targeting.
- **FAIL:** rescue behavior appears for crafting table. **Do not fix this.** Crafting table is an audited no-rescue target. Same stop rule as chain.

---

## Pass / fail definitions

| Verdict | Meaning |
|---------|---------|
| **PASS** | The crosshair, outline box, visible model, and resulting placement all feel like the same object. What you aim at is what you get. |
| **FAIL** | The player must aim at invisible air; the outline suggests a different target than what is actually clicked; a ghost face appears; or rescue behavior occurs where none is intended. |

---

## Stop conditions

- If live play contradicts the automated proof result, **trust live play and stop.**
- If the same symptom survives two separate attempts, stop implementation and run a static audit (confirm the fix is present, validate mixins.json, check fabric.mod.json registration).
- **Do not broaden rescue as a shortcut.**
- **Do not touch torch or bed rescue** without a concrete, reproducible regression in hand.
- **Do not add chain or crafting-table rescue** — they are audited no-rescue targets.
- Items 8 and 9 failing means a rescue boundary violation — escalate, do not patch.

---

## Result record

Copy and fill this table after each live verification session:

| Field | Value |
|-------|-------|
| Date | |
| Branch | |
| HEAD | |
| Tag | |
| Proof runner result | PASS / FAIL |
| Live checklist result | PASS / FAIL |
| Failed item(s) | (list item numbers, or "none") |
| Notes / screenshots path | |
