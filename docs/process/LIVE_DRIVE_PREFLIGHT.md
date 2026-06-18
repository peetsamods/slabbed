# Slabbed Live Drive Preflight

Use this before any live-client, Modrinth-profile, jar-staging, screenshot, recorder, or manual placement proof.

## Required Working Card Fields

```text
Objective:
Root/profile:
Allowed work:
Forbidden lanes:
Proof gate:
Stop condition:
```

The `Root/profile` line must name the repo root and the exact Modrinth profile, such as `Fabric 26.1.2` or `TEST_ SLABBED 26.1.2`.

## Preflight

1. Run the Git preflight from `AGENTS.md` and confirm this root:

```text
/Users/joolmac/CascadeProjects/Slabbed-port-26.1.2
```

2. Confirm the intended profile:

```text
Fabric 26.1.2
TEST_ SLABBED 26.1.2
```

3. Confirm the staged jar is the intended jar. Record file name, size, timestamp, and whether duplicate Slabbed jars are present.
4. Confirm required companion mods for the test, especially Terrain Slabs and dependencies in `TEST_ SLABBED 26.1.2`.
5. Confirm keybind state before and after the session. If a live-driving keybind is changed, revert it to `key.mouse.right` before handing control back to Julia unless she asks otherwise.
6. Use fresh positions for placement proof. Do not reuse old `/setblock` or prior bug coordinates as proof of current placement behavior.
7. Use `/slabdy` or the current diagnostic surface to record dy, source, half, and relevant block identity.
8. Keep command-created fixtures separate from player-placement proof.
9. Note focus behavior: Escape may not reach Minecraft; switching focus through Modrinth can expose the game menu. F11 may trigger macOS behavior rather than Minecraft fullscreen.

## Minimal Evidence Template

```text
Profile:
Jar:
Companion mods:
Keybind before:
Keybind after:
Fresh position(s):
Action:
Observed /slabdy:
Expected:
Verdict:
Evidence path or screenshot:
```

## Stop Conditions

- Wrong or ambiguous profile.
- Duplicate or ambiguous Slabbed jar.
- Keybind changed and not restorable.
- Minecraft access/focus prevents reliable action.
- Live observation disagrees with automation proof; switch to `docs/process/FALSE_GREEN_CHECKLIST.md`.
