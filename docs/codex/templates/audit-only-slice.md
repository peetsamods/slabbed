# Template — Audit-Only Slice

```text
[SYSTEM: TOKEN DISCIPLINE ACTIVE. Grep first. Read minimal windows only. No Java edits. No gametest edits unless explicitly requested. No commit/tag/push.]

Repo Root: <path>
Branch: <branch>
Expected HEAD/tag: <hash/tag>

Task:
Audit <visible symptom> and identify the failing layer. Do not patch.

Failing layer candidate:
unknown / <layer>

Protected invariant:
<invariant>

Allowed files:
read-only; write only tmp/<issue>-audit-<HEAD-short> evidence

Forbidden:
Java edits, gametest edits, build.gradle edits, fabric.mod.json edits, release/version edits, commits, tags, pushes

Preflight:
<commands>

Minimum search/read list:
<rg terms first>

Evidence folder:
tmp/<issue>-audit-<HEAD-short>

Stop if:
wrong root/branch/HEAD, dirty staging, unrelated dirt, proof ambiguity requiring patch

Final report:
root, branch, HEAD, files inspected, evidence written, suspected layer, what is proven, what remains unproven, next smallest slice.
```
