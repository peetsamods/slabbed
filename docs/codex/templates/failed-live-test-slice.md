# Template — Failed Live Test Slice

```text
[SYSTEM: LIVE RED RECOVERY. No behavior patch until RED proof explains the live failure.]

Repo Root: <path>
Branch: <branch>
Expected HEAD/tag: <hash/tag>

Live failure:
<shape, held item, aim, expected owner, actual owner>

Task:
Preserve evidence and create/identify RED proof matching live failure.

Forbidden:
behavior patch first, rescue broadening, speculative guard edit, commit/tag/push

Evidence folder:
tmp/<issue>-fail-<HEAD-short>

Required:
1. preflight
2. copy logs
3. extract markers
4. inspect proof gap
5. write or identify RED proof
6. report next patch slice only if layer is proven

Final report:
root, branch, HEAD, evidence files, why automation missed it, failing layer, RED proof marker, next smallest patch slice.
```
