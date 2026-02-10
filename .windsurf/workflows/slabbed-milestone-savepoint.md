# Slabbed — Workflow: Milestone Savepoint

## Goal
Create a recoverable, tagged milestone after a major slice lands.

## Steps
1) git status clean
2) ./gradlew clean build
3) runClient smoke + latest.log mixin grep
4) Quick 3-lane repro check
5) Commit
6) Tag
7) Push branch + tags
