# SLABBED Archive and Prune Map

Status: replacement map for deciding what old `/sources` files to delete, archive, or ignore.
Updated: 2026-05-04.

Julia can remove all previous `/sources` after uploading the replacement pack.

## Keep only the replacement pack active

Active source files after cleanup:

```text
00_SLABBED_SOURCE_INDEX.md
01_SLABBED_CANONICAL_DOCTRINE.md
02_SLABBED_ACTIVE_STATUS.md
03_SLABBED_BUG_BLASTERS.md
04_SLABBED_WORKFLOWS.md
05_SLABBED_SKILLS_AND_COMMANDS.md
06_SLABBED_RESEARCH.md
07_SLABBED_ARCHIVE_AND_PRUNE_MAP.md
```

## Merged / superseded files

| Old file/source type | Disposition | Replacement |
|---|---|---|
| `SLABBED_Constitution_v2.1.md` | Merged and updated | `01_SLABBED_CANONICAL_DOCTRINE.md` |
| `RULES.md` if present | Superseded | `01_SLABBED_CANONICAL_DOCTRINE.md` |
| `WORKFLOW.md` | Merged, category workflow marked paused | `04_SLABBED_WORKFLOWS.md` |
| `WINDSURF CODEMAPS.txt` | Condensed | `04_SLABBED_WORKFLOWS.md`, `05_SLABBED_SKILLS_AND_COMMANDS.md` |
| `SKILL.md` | Generic skill folded in | `05_SLABBED_SKILLS_AND_COMMANDS.md` |
| `slabbed-git-skills.md` | Merged | `05_SLABBED_SKILLS_AND_COMMANDS.md`, `04_SLABBED_WORKFLOWS.md` |
| `ClaudeHandoff.txt` | Merged | `04_SLABBED_WORKFLOWS.md`, `05_SLABBED_SKILLS_AND_COMMANDS.md` |
| `Research-First Rule.MD` | Merged | `06_SLABBED_RESEARCH.md` |
| `Slabbed Research.MD` | Merged | `06_SLABBED_RESEARCH.md` |
| `Research plan and execution checklist for Slabbed Lab v1.pdf` | Converted/condensed | `06_SLABBED_RESEARCH.md` |
| `New-skill-slabbed-client-offset-triad.txt` | Merged | `05_SLABBED_SKILLS_AND_COMMANDS.md`, `01_SLABBED_CANONICAL_DOCTRINE.md` |
| `carpet + global model dy coexistence` | Merged as protected invariant | `01_SLABBED_CANONICAL_DOCTRINE.md`, `05_SLABBED_SKILLS_AND_COMMANDS.md` |
| All Bug Blaster `.txt` files | Consolidated | `03_SLABBED_BUG_BLASTERS.md` |
| `Windows Parity Handoff.txt` duplicates | Historical only | durable facts moved into active/status where relevant |
| `Next Release Outline.txt` duplicates | Historical only | release BB moved to `03`; release workflow moved to `04` |
| `BS-FB Interaction Integrity.txt` duplicates | Historical only | durable BBs/status moved to `03` and `02` |
| `SBSB Live Geometry Proof.txt` duplicates | Historical only | durable BBs/status moved to `03` |
| `Next steps and guidance.txt` duplicates | Historical only | durable BBs/status moved to `03` and `02` |
| `Rollback Verification Process.txt` | Historical plus active baseline | baseline moved to `02`; BB moved to `03` |
| `Slabbed project status.txt` | Historical plus active baseline | `02_SLABBED_ACTIVE_STATUS.md` |
| `Project Status Update.txt` | Historical | latest durable state moved to `02` / `03` |
| `Hitbox Issue Resolution.txt` | Merged latest status | `02_SLABBED_ACTIVE_STATUS.md`, `03_SLABBED_BUG_BLASTERS.md` |

## Why old sources should be removed

The old pile contains duplicates, stale branch states, and old “pending” entries that later became fixed through cleaner paths.

Leaving them active risks future agents treating old WIP as equal authority with current doctrine.

## Specific stale interpretations removed

- selective-only slab support
- category expansion as default next work
- proof-pending Bug Blasters treated as fixed
- debug tools as normal runtime behavior
- rescue expansion before red proof
- placement success treated as survival success
- jar purity treated as contents-only scan

## Historical items intentionally preserved only as case law

The exact old text does not need to remain active. The durable lessons are now in:

- `03_SLABBED_BUG_BLASTERS.md`
- `01_SLABBED_CANONICAL_DOCTRINE.md`

## Pending entries that must not be overclaimed

Do not mark these as final/fixed unless a later savepoint proves closure:

- `Render Region Lowered Slab Carrier Bridge`: proof obtained / savepoint pending
- `Real-Placed Lowered BOTTOM Slab Persistence Gap`: proofed in old dirty WIP unless later integrated
- old Phase18/Phase19 dirty WIP entries: superseded by clean Phase19 savepoint where applicable

## Upload/delete procedure

1. Upload all eight replacement files.
2. Verify they appear in `/sources`.
3. Remove all old Slabbed source files.
4. Ask future agents to read `00_SLABBED_SOURCE_INDEX.md` first.
5. If any old source must be retained for legal/evidence reasons, mark it `ARCHIVE ONLY — NOT ACTIVE DOCTRINE`.
