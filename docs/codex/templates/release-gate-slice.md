# Template — Release Gate Slice

```text
[SYSTEM: RELEASE GATE ONLY. No behavior edits unless gate identifies a concrete blocker and user explicitly authorizes a follow-up patch.]

Repo Root: <path>
Branch: <branch>
Expected HEAD/tag: <hash/tag>
Release version/tag: <version>

Task:
Run release gate and report whether this commit is releasable.

Preflight:
<commands>

Required commands:
./gradlew --no-daemon clean build
./gradlew --no-daemon runClientGameTest --console plain
./gradlew --no-daemon runGameTest --console plain
jar tf build/libs/slabbed-*.jar | rg "debug|dev|audit|gametest|test|proof|fixture|lab"
jdeps -recursive -verbose:class build/libs/slabbed-*.jar | rg "com\.slabbed\.(debug|dev)|SlabbedDebug|slabbed\.debug\.mixins|BsFbLiveTrace|ScreenshotCapture|GapFiller|SlabbedLab"

Stop if:
any command fails, jar scan non-empty, jdeps scan non-empty, build output race suspected, live red exists

Final report:
release candidate yes/no, blockers, proof logs, jar path, scans, final tree, next smallest slice.
```
