# Live-Drive Mode — driving MC 26.1.2 via computer-use (Slabbed test rig)

How an agent (Claude/Codex) tests Slabbed **live** in the real game by controlling the desktop.
Ported from the 1.21.1 guide; everything here is **empirically verified in this 26.1.2 session
(2026-06-16)** unless marked theory. Read this BEFORE deciding "I can't reproduce a placement bug" —
you can: you place blocks with a rebound key, and aim with `/tp`.

> The single most important idea: you are blind except for screenshots, and **not all input reaches
> the game**. Win by (1) knowing which inputs are delivered, (2) using `/slabdy` + command echoes as
> your eyes, (3) routing around the rest with commands + a keybind trick.

## 0. Cheat sheet

| Need | How |
|---|---|
| Move | `hold_key text:"w" duration:2.5` (works) |
| Break a block | `left_click` (works) — breaks the crosshair block |
| **Place a block** | rebind Use→`r` (§5), press `r`. **Right-click is NOT delivered.** |
| Aim the camera | `/tp @s x y z <yaw> <pitch>` — **mouse-look is NOT delivered** |
| Chat / commands | `t`, **wait 0.6s**, type `/cmd`, `Return`. The wait stops the leading `/` being dropped. |
| Read targeted block | `/slabdy` HUD (top-left): `pos  Name · dy=… <status> · side=… · half=…` + `src=…` |
| Items in hotbar | `/item replace entity @s hotbar.N with minecraft:<id> 64` (N is 0-indexed) |
| Read raw geometric dy | `/setblock x y z <block>` (terrain path, NO freeze/anchor) then `/slabdy` |

**Delivered to MC:** left-click, letter keys, number keys, `Return`, `Backspace`, `hold_key`.
**NOT delivered:** right-click, `Escape`, F-keys (F3/F11), mouse-look, mouse drag-aim.

## 1. Environment (26.1.2 specifics)

- **Profile:** Modrinth App → `Fabric 26.1.2`. Mods dir:
  `~/Library/Application Support/ModrinthApp/profiles/Fabric 26.1.2/mods/`
  (jar `slabbed-0.2.0-beta.4+26.1.2-port.jar` + `fabric-api-0.151.0+26.1.2.jar`). Swap the jar in
  **before** launch.
- **Build the jar with Java 25** (Loom needs it; Java 21 javac can't read the MC named jar →
  "cannot access BlockState"):
  `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home`
  then `./gradlew build -x runGameTest`. Output: `build/libs/slabbed-0.2.0-beta.4+26.1.2-port.jar`.
- **Stage:** `cp -p build/libs/…port.jar "$PROF/mods/…port.jar"`. Restart the instance to load it.
- **request_access** for `Minecraft`/`java` AND `Modrinth App` (you need Modrinth to Stop/Play). The
  running game is a `java` process; `open_application "Minecraft"` opens the **launcher**, not the game.
- **Stage Manager OFF** (it reshuffles windows on focus change).
- **Steam's notification helper** (`com.valvesoftware.steam.helper`) can overlay the game's top-right and
  block computer-use clicks there ("would land on Steam Helper"). Click elsewhere / wait it out.
- Computer-use toolkit: one `ToolSearch { query:"computer-use", max_results:30 }`.

## 2. Chat & commands

1. `key "t"` → opens chat. 2. **`wait 0.6`** (critical — else the `/` races chat-open and your command
leaks in as plain text: `<Peetsa> tp @s …`). 3. `type "/tp @s …"` (full, with slash). 4. `Return` (runs
it, re-grabs mouse, closes chat). Echoes print bottom-left (`Teleported …`, `Changed the block at …`,
`Could not set the block` = setblock no-op when blockstate is identical → set `air` first).

## 3. Camera — aim with `/tp`, never the mouse

`/tp @s <x> <y> <z> <yaw> <pitch>`. **Yaw:** 0→+Z(south), 90→−X(west), 180→−Z(north), −90/270→+X(east).
**Pitch:** −90 up, 0 horizon, +90 down. Eye ≈ feet Y + 1.62. Aiming at a specific face is **iterative**:
`/tp` a guess → screenshot → read `/slabdy side=` → nudge yaw/pitch. To read a block from above: stand
over it, `pitch 90` (targets the column below). To place ON top: `side=up`. To place against a side:
aim at that face (`side=east` etc.); the slab lands in the adjacent cell, and **half=UPPER/LOWER** of
where you aim picks top/bottom slab.

## 4. Placing — the keybind rebind (CRITICAL, do not forget)

Right-click isn't delivered, so default Use (bound to `mouse.right`) can't place. Rebind Use to a
keyboard key (delivered). **Do it with MC STOPPED** (MC overwrites `options.txt` on quit):

```bash
f=~/Library/Application\ Support/ModrinthApp/profiles/Fabric\ 26.1.2/options.txt
cp "$f" "$f.slabbed-bak"
grep -nE '^key_' "$f"                                    # find an UNBOUND key (r is free by default)
sed -i '' 's/^key_key.use:key.mouse.right$/key_key.use:key.keyboard.r/' "$f"
grep -n '^key_key.use' "$f"                              # confirm -> key.keyboard.r
```
Then Modrinth **Play**, wait ~40s, and **press `r`** to place / use (fires the real `onPlaced` →
freeze/anchor path). **REVERT WHEN DONE** (MC stopped):
`sed -i '' 's/^key_key.use:key.keyboard.r$/key_key.use:key.mouse.right/' "$f"`. Always tell Julia the
keybind state.

## 5. The killer A/B — terrain vs placed dy (proves freeze/anchor fixes)

To prove a "placed block must not snap" fix without trusting a single read: in the **same cell**,
1. `/setblock x y z <block>` → `/slabdy` reads the **raw geometric** dy (no freeze) — e.g. `-0.500`.
2. `/setblock x y z air`, then **place with `r`** → `/slabdy` reads the **frozen** dy — e.g. `0.000`.
Geometric≠placed proves the config genuinely lowers AND the freeze-guard caught it. (Used 2026-06-16 to
prove the snap fix at `(9,-58,5)`.) `/setblock` is a no-op if the new blockstate equals the old → set
`air` first.

## 6. `/slabdy` overlay — your instrument

Always-on HUD (mixin-driven, `-Dslabbed.targetDyOverlay=false` to disable). 3 lines: `pos Name`,
`src=VANILLA/MOD · dy=… <flush/LOWERED/RAISED> · side=… · half=UPPER/LOWER`, `src=<why>`
(FROZEN-FLAT / ANCHORED / compound-side / geometric / -). `dy` updates live — aim once, change the
world around it (`/setblock` a slab below, break a neighbour), re-screenshot to watch it react.

## 7. When live is impractical — gametest fallback

For pure `onPlaced` LOGIC, a server gametest is faster + deterministic. `authorBlock` helper =
`level.setBlock(abs, state, Block.UPDATE_ALL); state.getBlock().setPlacedBy(level, abs, …, null,
ItemStack.EMPTY)` (fires `BlockOnPlacedAnchorMixin`). Plain `helper.setBlock` = terrain (no onPlaced).
Run `./gradlew runGameTest` (Java 25). Render/cull/geometry bugs (DODO, smoosh, chain) are **render-only
→ live A/B is mandatory** (no gametest can see them).

## 8. Etiquette checklist

- [ ] **Revert the Use→`r` keybind** (MC stopped) before Julia plays normally.
- [ ] Note the jar version/byte-size tested + that it's the one in her profile.
- [ ] Leave proof structures + tell her the coords; clean up scratch builds.
- [ ] Commit locally only; **never push without explicit request.**
- [ ] Record durable gotchas to memory.
