# AGENTS.md — SLABBED (Forge 1.20.1 clean foundation)

This root is an **independent Forge 1.20.1 foundation**. Its history begins at a single parentless
root commit. It carries no NeoForge or Fabric ancestry.

Everything below states **admission contracts** — what a claim must satisfy before it may be made
in this root. Nothing below preclaims a result.

## Source of truth

Use repo docs and code as the default source of truth. Do not rely on chat memory when repo docs
are available.

Repo-local authority order:

1. `AGENTS.md`
2. `SLABBED_SPINE.md`
3. `docs/codex/00-authority-order.md`
4. The narrow relevant guide under `docs/codex/`
5. repo docs and code when a doc points there

If docs conflict, cite exact file paths and sections. Follow documented superseding rules. If no
superseding rule exists, stop and report the conflict before editing.

## Front doors

Read these four before opening any Forge slice:

- `docs/porting/mc-1.20.1-forge-foundation.md`
- `docs/porting/mc-1.20.1-forge-phase-i-law-and-live-identity-rebaseline.md`
- `docs/porting/mc-1.20.1-forge-regression-risk-checklist.md`
- `docs/porting/mc-1.20.1-forge-roadmap.md`

## Document index

The complete governance and porting document set admitted to this root:

- `docs/codex/00-authority-order.md`
- `docs/codex/01-canon-law-purpose.md`
- `docs/codex/02-legal-state-grammar.md`
- `docs/codex/03-visual-triad.md`
- `docs/codex/04-slice-contracts.md`
- `docs/codex/05-preflight-savepoint.md`
- `docs/codex/06-bug-blaster-case-law.md`
- `docs/codex/07-live-test-log-recipes.md`
- `docs/codex/08-compat-contracts.md`
- `docs/codex/09-release-gate.md`
- `docs/codex/10-troubleshooting-when-stuck.md`
- `docs/codex/11-model-thread-policy.md`
- `docs/codex/12-goal-mode.md`
- `docs/codex/13-mixin-layer-wiring-audit.md`
- `docs/codex/14-zoom-out-discipline.md`
- `docs/codex/templates/audit-only-slice.md`
- `docs/codex/templates/failed-live-test-slice.md`
- `docs/codex/templates/implementation-slice.md`
- `docs/codex/templates/release-gate-slice.md`
- `docs/codex/templates/savepoint-slice.md`
- `docs/porting/mc-1.20.1-forge-attachment-persistence-decision.md`
- `docs/porting/mc-1.20.1-forge-book-iv-first-proof-route.md`
- `docs/porting/mc-1.20.1-forge-client-runtime-triad-harness-audit.md`
- `docs/porting/mc-1.20.1-forge-foundation.md`
- `docs/porting/mc-1.20.1-forge-model-loading-render-path-decision.md`
- `docs/porting/mc-1.20.1-forge-model-wrapper-registration-proof.md`
- `docs/porting/mc-1.20.1-forge-model-wrapper-render-view-proof.md`
- `docs/porting/mc-1.20.1-forge-ordinary-full-block-proof-harness-audit.md`
- `docs/porting/mc-1.20.1-forge-phase-i-law-and-live-identity-rebaseline.md`
- `docs/porting/mc-1.20.1-forge-regression-risk-checklist.md`
- `docs/porting/mc-1.20.1-forge-rendered-block-model-evidence-proof.md`
- `docs/porting/mc-1.20.1-forge-rendered-model-culling-triad-decision.md`
- `docs/porting/mc-1.20.1-forge-roadmap.md`
- `docs/porting/mc-1.20.1-forge-view-truth-order-decision.md`

`RULES.md` is admitted and active. Any document not listed above is not part of this root.

## Inactive and historical classifications

These are recorded so that references found inside admitted documents are read correctly. None of
them is an active instruction in this root.

| Item | Classification | Meaning here |
|---|---|---|
| `docs/codex/source-pack/` | **HISTORICAL — not present** | Admitted documents may cite it as archive-only provenance. It is deliberately absent from this root. A citation to it is never an instruction to fetch or restore it. |
| Generic build/run/test commands retained inside admitted documents | **INACTIVE** | Illustrative of the older root they were written for. The executable toolchain for this root is defined in `docs/porting/mc-1.20.1-forge-foundation.md` only. |
| Donor references to 26.2, 1.21.11, 1.21.1, and NeoForge work | **HISTORICAL** | Behaviour and test-design donors only. Never a source of imports, APIs, mappings, registration, or network classes. |
| Older Slabbed roots, including the provisional Forge root and any `phase19` root | **HISTORICAL** | Provenance and evidence only. This root never mutates them and never adopts their dirty state. |
| Any Fabric or NeoForge entrypoint, loader, or API surface | **OUT OF SCOPE** | This root targets Forge 1.20.1 only. |

## Stale-active-value gate

A value that is not current for this root may appear **only** inside an explicitly labelled
`WITHDRAWN` or `SUPERSEDED` historical row.

A noncurrent value must never appear in:

- an executable command,
- an active instruction,
- a proof claim,
- an evidence verdict,
- a commit message.

The root commit SHA of this foundation is **resolved live** — read it from the repository
(`git -C <this root> rev-parse HEAD`). It is deliberately not written into any document, because a
document cannot name a commit that does not exist when the document is authored.

## Product law

1. A placement hook must produce a named legal Slabbed state, preserve vanilla behaviour, or
   reject/defer. It must not create a visually plausible but unnamed hybrid.
2. Resolve the player's visible owner, face, placement cell, and exact `dy` during the real
   placement action.
3. Store the exact value at that moment. A neighbour edit, chunk unload/reload, relog, render view,
   or client correction must not recompute or move the authored object.
4. Model, outline, raycast/targeting, use/place, and break must agree on that same visible truth.
   A model-only or outline-only result is a failure.
5. No broad solidity/sturdy-face lies, global redirects, packet or interact rewrites as a
   substitute for legal state, hidden tick loops, or broad rescue logic.
6. Core vanilla behaviour must work without compatibility mods. Compatibility is additive and
   separately proven.

The minimum supported lowered lane for a release candidate is the named `dy=-0.5` law. Any deeper
lane is exactly one of **supported**, **vanilla/rejected**, or **not in this release**.

## Admission contract for any claim

A claim is admissible in this root only with:

- the legal state it protects, named before the patch;
- one focused change;
- the matching Forge-native proof, and a statement of what that proof does **not** cover;
- for any player-visible claim, a real Forge profile/live result — headless or dev-run evidence
  alone is not sufficient;
- caller and wiring inspection;
- a reviewed diff.

Loader translation is by **behaviour and test intent**, never by mechanical source or API copying.
Re-prove against Java 17, Gradle 8.8, ForgeGradle 6.0.54, Forge 47.4.20, and official 1.20.1
mappings.

## Authorization

Local commit, tag, push, upload, and release each require Maintainer's current explicit approval.
A clean test result is not release authorization.
