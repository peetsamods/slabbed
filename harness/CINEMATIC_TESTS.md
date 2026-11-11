# Cinematic Interactions table compatibility checks

See `LAW.md`; these checks do not redefine placement or saved-height rules.

The optional client adapter is enabled only for Cinematic Interactions 0.18.28
on Minecraft 26.3. It translates table scene geometry and table-item culling
bounds using the owner's saved height. It does not modify stored positions,
layout configuration, reach limits, or global raycasts. Other Cinematic versions
and Minecraft 26.2 leave the adapter disabled.

Obtain the official Fabric 26.3 Cinematic Interactions 0.18.28 JAR and set
`CINEMATIC_JAR` to its local path. Its SHA-256 is
`71ffd95cbdd3a56d5c4d381556f6cebada9af3386188290ff752c68e8092ad84`.
Then run from this checkout:

```sh
./gradlew25 -I harness/cinematic-tests.gradle \
  -PcinematicTestJar="$CINEMATIC_JAR" \
  -Pmod_version=0.0.0-dev.cinematic+26.3 \
  -Ploader_version=0.20.1 -Pfabric_version=0.165.2+26.3 \
  runClientGameTest
```

The init script includes only the optional compatibility test and adds its
third-party dependency to the test classpath. It selects that test in generated
resources, not the checked-in mod descriptor. This is a focused test run, not
the complete client or server suite. Run ordinary checks without the init script.

Coverage is deliberately bounded to unlowered and half-slab crafting tables,
including scene activation, rendered camera/hands, grid selection, crafting,
exit/reopen, resource reload, idle-item culling bounds, a lowered cover, and
preservation of the stored height after a neighbor change. This does not prove
arbitrary layouts, deeper offsets, every obstructing shape, or client-only
Cinematic mode against an unmodified server.
