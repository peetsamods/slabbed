# Template — Implementation Slice

```text
[SYSTEM: TOKEN DISCIPLINE ACTIVE. Grep first. Read minimal windows only. Patch only the named failing layer.]

Repo Root: <path>
Branch: <branch>
Expected HEAD/tag: <hash/tag>

Task:
Fix <visible symptom>.

Failing layer:
<layer>

Legal state / invariant protected:
<state + invariant>

Allowed files:
<list>

Forbidden files:
<list>

Preflight:
<commands>

Required proof:
1. RED proof or existing failing evidence
2. compile
3. focused proof
4. full relevant proof
5. live test required? yes/no

STOP IF:
wrong root, dirty staging, unrelated dirt, layer changes, rescue broadening, Visual Triad mismatch, proof green but live red

Final report:
root, branch, old/new HEAD if changed, files touched, files intentionally untouched, proof commands/results, what remains unproven, final tree, next smallest slice.
```
