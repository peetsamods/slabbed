# Per-version proof harness

See `LAW.md` — this directory proves builds; it does not redefine the law.

This line ships ONE jar for Minecraft 26.2 and 26.3. The jar is compiled against 26.3, so the line's
own `build runGameTest` and `runClientGameTest` prove 26.3. Everything under `harness/` exists to prove
the same bytes on 26.2, where every renamed class or member would otherwise surface only when a
player reaches that code.

## What a 26.2 release claim needs

1. `./gradlew25 build` — the release jar, the test kit (`build/testkit/…-testkit.jar`: the dev-only
   classes the release excludes, which the suite depends on) and the diagnostics companion.
2. `./gradlew25 -p harness/fabric-26.2 runGameTest verifyHarnessGameTestCount` — the shared server
   GameTest suite compiled against 26.2 and run on a 26.2 runtime with the exact release jar loaded
   as a mod. The count gate compares the run's "All N required tests passed" line with the count of
   test annotations in the classes the prepared descriptor registers.
3. `./gradlew25 -p harness/fabric-26.2 runClientGameTest verifyHarnessClientLeg` — the client
   entrypoints on a 26.2 client; proof only when every registered entrypoint logs its PASS line.
4. `python3 tools/linkage-scan.py build/libs/<jar> - <26.2 client jar> --lib <fabric-api 0.152.2 jar>
   --allow-class 'com/slabbed/compat/PlayerSwing$Modern' --allow-class 'com/slabbed/client/model/YOffsetEmitter'`
   and the same against the 26.3 jars with `--allow-class 'com/slabbed/compat/mc262/'`. Every
   isolated class is one that the running-version switch loads only where its references resolve.
5. `python3 tools/mixin-target-scan.py <game jar> src/main/java src/client/java src/diagnostics/java
   --config src/main/resources/slabbed.mixins.json --config src/client/resources/slabbed.client.mixins.json
   --config src/diagnostics/resources/slabbed.diagnostics.client.mixins.json` against each version's
   game jar, skipping (`--skip`) exactly the mixins `SlabbedMixinConfigPlugin` withholds there:
   `LegacyRedStoneWireBlockMixin` on 26.3; `RedstoneWireBlockMixin` and `CushionRestsOnDrawnTopMixin`
   on 26.2.

Loom keeps each version's shipped client jar in the Gradle user home under
`caches/fabric-loom/<version>/minecraft-client.jar` after the first build for that version; the Fabric API
bundle jars sit in the Gradle module cache (`caches/modules-2`).

## What the 26.2 leg deliberately leaves out

- The `/slabrig` rig rows (`SlabRigHanging*Test`, `SlabRigCaseCatalogTest`, `SlabRigCommandSmokeTest`)
  pin exact per-version content goldens of a release-excluded dev tool; they run on the 26.3 native
  leg only.
- `CushionOnLoweredBlockTest`: no cushions before 26.3.
- `support/TestConnections` is replaced by the client-gametest API 5 copy under `src/overrides`.

The prepared sources and descriptor live in `harness/fabric-26.2/build/gametest-src/`; the receipts
in `harness/fabric-26.2/build/harness-receipts/`.

## Honest limits

- The server leg loads the server-side mixins; the client leg loads the client-side ones. A mixin
  whose target class never loads in either session is checked only by the mixin-target scan.
- Neither leg exercises Sodium, Lithium or Terrain Slabs; those compatibility seams are byte-probed
  and skip cleanly when the other mod is absent. A play pass with that version's compat jars remains
  the maintainer's lane.
