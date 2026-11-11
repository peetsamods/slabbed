# Offgrid compatibility harness

See `../../LAW.md`. This standalone Minecraft 26.3 client harness loads the supplied artifacts instead of Slabbed source classes. It requires JDK 25 and verified Fabric Offgrid 1.0.6 artifacts.

From the repository root:

```sh
./gradlew25 -p harness/offgrid -PoffgridJar=/path/to/offgrid.jar -PslabbedJar=/path/to/slabbed.jar runClientGameTest
```

Omit `slabbedJar` for the Offgrid-only control. Use `-Doffgrid.test.modelOnly=true` to isolate pre-break model alignment; the default also requires the surviving member to keep its height after a lower-member player break. These are distinct assertions. The harness creates disposable test worlds, uses actual client item-use and player-break paths, and verifies final Minecraft crosshair ownership. Emitted model geometry is not a screenshot/manual acceptance claim.
