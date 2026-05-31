# 05 — Preflight and Savepoint Discipline

## Universal preflight

Run before edits, builds, proofs, commits, tags, or release work:

```bash
git rev-parse --show-toplevel
git status -sb
git branch --show-current
git rev-parse --short HEAD
git tag --points-at HEAD
```

Stop unless root, branch, HEAD/tag, and dirt match the task.

## Dirty tree rules

- Dirty staging is a hard stop.
- Unexpected tracked dirt is a hard stop.
- `tmp/` may remain untracked if intentionally preserved.
- Do not auto-stash.
- Do not auto-reset.
- Do not clean untracked evidence.

## Standard proof commands

```bash
./gradlew --no-daemon compileJava compileGametestJava
./gradlew --no-daemon runClientGameTest --console plain
```

Use branch/project-specific proof commands when `SLABBED_SPINE.md` names them.

## Savepoint sequence

```bash
git status -sb
git add <intended files only>
git diff --cached --check
git commit -m "<type>: <specific slice>"
git tag -a save/<specific-desc> -m "save: <specific-desc>"
git push origin <branch>
git push origin save/<specific-desc>
git status -sb
git tag --points-at HEAD
```

## Savepoint final report

```text
Verdict: complete / blocked
Root:
Branch:
Old HEAD:
New HEAD:
Tag:
Files committed:
Files intentionally untouched:
Proof commands/results:
Branch pushed: yes/no
Tag pushed: yes/no
Final tree:
Next smallest slice:
```

## Do not stack wins

Testing too much was not the problem. Not saving proven wins before testing more was the problem.

One live-confirmed or proof-confirmed win → commit → annotated tag → push branch → push tag → verify → then continue.
