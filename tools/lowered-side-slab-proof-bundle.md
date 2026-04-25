# Lowered Side Slab Proof Bundle

Documentation-only checkpoint for the cross-platform proof bundle runners.
Passing this bundle does not authorise unrelated gameplay changes.

---

## Latest trusted savepoint

| Field  | Value                                  |
|--------|----------------------------------------|
| Branch | `test/windows-proof-bundle-runner`     |
| Commit | `5e1011d`                              |
| Tag    | `save/windows-proof-bundle-runner-green` |

Both Mac and Windows runners passed at this savepoint.

---

## Runners

**Mac**
```bash
bash tools/run_lowered_side_slab_proof_bundle.sh
```

**Windows (PowerShell)**
```powershell
.\tools\Run-LoweredSideSlabProofBundle.ps1
```

**Standalone verifier**
```bash
# Mac / Linux
python3 tools/verify_lowered_side_slab_proof_bundle.py

# Windows fallback (matching PowerShell runner behaviour)
python tools/verify_lowered_side_slab_proof_bundle.py
# or
py -3 tools/verify_lowered_side_slab_proof_bundle.py
```

---

## Canonical artifact directory

```
build/run/clientGameTest/screenshots
```

> **Note:** the path is `screenshots`, not `proof-bundle`.

### Required artifact files

| File | Description |
|------|-------------|
| `run_manifest.json` | Full run manifest |
| `proof_summary.json` | Per-proof pass/fail summary |
| `proof_index.json` | Index of captured screenshots |
| `latest_proof_run.json` | Metadata for the most recent run |
| `proof_receipt.md` | Human-readable receipt |

---

## Canonical 9-proof ladder

| # | Proof key |
|---|-----------|
| 1 | `fb_on_bs_lower_half_owner_targeting` |
| 2 | `fb_on_bs_lower_half_side_slab_intent` |
| 3 | `fb_on_bs_repeat_click_no_ghost_face` |
| 4 | `torch_on_fb_on_bs_rescue_targeting` |
| 5 | `bed_on_bs_rescue_targeting` |
| 6 | `full_block_on_full_block_baseline` |
| 7 | `slab_on_normal_vanilla_face_baseline` |
| 8 | `chain_on_fb_on_bs_no_rescue_targeting` |
| 9 | `crafting_table_on_bs_no_rescue_targeting` |

---

## Boundary notes

- **Chain** (#8) and **crafting table** (#9) are audited no-rescue targets.
  Do not broaden rescue from generic slab support.
- Do not touch torch or bed rescue unless a concrete regression is proven.
- This bundle is proof/tooling only.
  It verifies the lowered-side-slab placement behaviour; it is not a licence
  to change SlabSupport, mixins, model/outline/raycast logic, or any other
  gameplay category.
