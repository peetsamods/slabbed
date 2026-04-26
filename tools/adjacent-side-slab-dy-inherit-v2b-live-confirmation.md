# Adjacent Side Slab Dy V2B Live Confirmation

## Fix identity

- **Branch:** fix/adjacent-side-slab-dy-inherit-v2b
- **Commit:** ba409c3
- **Tag:** save/adjacent-side-slab-dy-inherit-v2b

## Automated proof

| Check | Result |
|---|---|
| compileJava | PASS |
| compileGametestJava | PASS |
| runClientGameTest | PASS |
| verifier (verify_lowered_side_slab_proof_bundle.py) | PASS |
| bottom-slab dy assertion | PASS |
| double-slab dy assertion | PASS |

## Live retest

| Check | Result |
|---|---|
| First side slab placement visually aligns with lowered block face | PASS |
| Repeat click / double slab stays visually lowered | PASS |
| Old vanilla-height jump on repeat click | FIXED |

## Final verdict

Adjacent side slab dy inheritance v2b is live-confirmed.

Checks 2 and 3 from the lowered-side-slab live checklist are fixed:
- A bottom slab placed at the side of a lowered full block now correctly inherits dy -0.5.
- A repeat click that promotes the slab to a double slab also correctly inherits dy -0.5.

## Separate unresolved issue

Side torch flame visual offset remains a separate, unresolved issue. It was not touched in this fix.
