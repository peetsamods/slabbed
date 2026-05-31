# Template — Savepoint Slice

```text
[SYSTEM: SAVEPOINT ONLY. No gameplay edits. No opportunistic cleanup.]

Repo Root: <path>
Branch: <branch>
Expected HEAD/tag: <hash/tag>

Task:
Savepoint the proven win <name>.

Allowed files to stage:
<exact list>

Forbidden:
all other tracked files, tmp staging, Java edits, docs edits not named here

Preflight:
<commands>

Proof freshness:
Run or verify fresh proof:
<commands>

Commit:
<message>

Tag:
save/<specific-desc>

Push:
git push origin <branch>
git push origin save/<specific-desc>

Final report:
root, branch, old HEAD, new HEAD, tag, proof, files committed, branch pushed, tag pushed, final tree.
```
