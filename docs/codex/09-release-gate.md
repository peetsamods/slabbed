# 09 — Release Gate and Jar Closure

Release claims require proof beyond compile success.

## Standard release gate

```bash
./gradlew --no-daemon clean build
./gradlew --no-daemon runClientGameTest --console plain
./gradlew --no-daemon runGameTest --console plain
jar tf build/libs/slabbed-*.jar | rg "debug|dev|audit|gametest|test|proof|fixture|lab"
jdeps -recursive -verbose:class build/libs/slabbed-*.jar | rg "com\.slabbed\.(debug|dev)|SlabbedDebug|slabbed\.debug\.mixins|BsFbLiveTrace|ScreenshotCapture|GapFiller|SlabbedLab"
```

If the jar scan or `jdeps` scan emits hits, release is blocked unless every hit is explicitly expected and safe.

## Release hard rules

- Public release jar must not package dev/debug/test/proof tooling.
- Production bytecode must not hard-link excluded classes.
- Debug bridges must be reflection-gated or production-safe.
- Release tag movement requires final proof and explicit release correction task.
- No release prep while behavior is still live-red.

## Release final report

```text
Release version:
Root:
Branch:
Commit:
Release tag:
Build result:
Client gametest result:
Server gametest result:
Jar contents scan:
Jdeps hard-reference scan:
Private launch/smoke:
Known limitations:
Upload candidate path:
Final tree:
```

## Common release false-green

A jar contents scan can be clean while production bytecode still references excluded debug classes. That is not release-clean. Always run bytecode hard-reference scan.
