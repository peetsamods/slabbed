# Live-Drive Mode — Driving Minecraft via Computer-Use (Slabbed test rig)

A practical instruction sheet for an agent (Codex, Claude, etc.) that needs to **test the Slabbed mod live in a real Minecraft instance** by controlling the desktop with computer-use tools (screenshot + synthetic mouse/keyboard). Written 2026-06-11 from a working session; everything here is empirically verified unless marked "theory."

The single most important idea: **you are flying blind except for screenshots, and not all input you send actually reaches the game.** Success comes from (1) knowing exactly which inputs are delivered, (2) using in-game *instruments* (the `/slabdy` overlay + command echoes) as your eyes, and (3) routing around the inputs that don't work with commands and a keybind trick.

---

## 0. TL;DR cheat sheet

| Need | How |
|---|---|
| Move (W/A/S/D, sprint, jump, sneak) | `hold_key` with `text:"w"` etc. and a `duration` (seconds). **Works.** |
| Break a block | left-click (`left_click`). **Works.** |
| **Place a block** | Rebind "Use" to a keyboard key (`r`) in `options.txt`, then press `r`. Right-click does **NOT** work — see §5. |
| Aim the camera | `/tp @s x y z <yaw> <pitch>` — mouse-look does **NOT** work. See §4. |
| Type chat / commands | press `t`, `wait 1`, type, `Return`. Slash can drop — see §3. |
| Read what you're looking at | `/slabdy` overlay (top-left): `target_pos  BlockName  side=<face>  dy=<value>`. Your primary instrument. |
| Put exact items in hotbar | `/item replace entity @s hotbar.N with <item> 64` (N is 0-indexed). |
| Read a tiny HUD value precisely | `zoom` tool on the overlay region. |
| Pause menu / Options | game **auto-pauses when it loses focus** → click another app then back. `Escape` does **NOT** work. |

**Inputs that DO reach MC:** left-click, letter keys, number keys, `Return`, `Backspace`, `hold_key` (held keys).
**Inputs that DO NOT reach MC:** right-click (place/use), `Escape`, function keys (F3/F11 etc.), mouse-look (camera via mouse motion), `mouse_move`/drag for aiming.

---

## 1. When to use live-drive (and when not to)

Use it to confirm **rendering / visual / interaction** behavior that headless gametests can't see: model offset (dy) on screen, culled faces, outline vs. mesh alignment, placement feel, break/replace pops, client-server sync.

**Do NOT use it for `onPlaced`-path logic if you can avoid it** — placement is the most fragile part of the rig. If you only need to prove the *logic* of an `onPlaced` fix, a server gametest is faster and deterministic (see §11). Use live-drive when you specifically need to *see* the result or reproduce a player-driven sequence.

---

## 2. Prerequisites & environment setup

1. **Profile:** Modrinth App profile `Slabbed 1.21.1` (or the 1.21.11 equivalent). It has the mod jar + `terrain_slabs` + Sodium/Lithium etc. The jar under test must be swapped into `…/ModrinthApp/profiles/Slabbed 1.21.1/mods/` *before* launch.
   - Profile path: `~/Library/Application Support/ModrinthApp/profiles/Slabbed 1.21.1/`
2. **Computer-use access:** call `request_access` for `Minecraft`/`java` **and** `Modrinth App` (you need Modrinth to Stop/Play). Minecraft runs as a `java` process — `open_application "Minecraft"` only opens the *launcher*; bring the game window forward by clicking it (Finder/window must be granted).
3. **Stage Manager OFF** (macOS Control Center). With it on, windows reshuffle every time focus changes and you lose the game window. This is mandatory.
4. **Tooling:** load the computer-use toolkit in one `ToolSearch` call: `{ query: "computer-use", max_results: 30 }`. `hold_key` and `zoom` may need loading too.

---

## 3. Chat & commands (your control plane)

Commands are how you do almost everything (teleport-aim, setblock, give, gamemode). The pattern that works:

1. `key "t"` — opens chat.
2. **`wait 1`** (1 second). Without this, the next characters can race the chat-open and the **leading `/` gets dropped**, so your command leaks into chat as plain text. (When batching, put a wait after every `t`.)
3. `type "/setblock 213 -60 212 stone"` — type the full command including the slash.
4. `screenshot` to verify the command text shows correctly in the input line (the slash is the thing to check).
5. `key "Return"` — runs it. Pressing Return also **re-grabs the mouse** and closes chat.

Gotchas:
- If chat won't open (you press `t` and get a literal `t` in an already-open box), chat was already open. `Backspace` to clear, or `Return` to submit/close, then reopen.
- `Escape` does not close chat — use `Return`.
- Command echoes print in the bottom-left chat log (e.g. `Changed the block at 213, -60, 212`, `Teleported …`) — read these to confirm.

---

## 4. Camera — aim with `/tp`, never the mouse

MC uses raw mouse input; synthetic mouse motion/warps are ignored, so you **cannot turn the camera with the mouse**. Instead set position *and* facing in one command:

```
/tp @s <x> <y> <z> <yaw> <pitch>
```

**Yaw** (horizontal facing):
- `0` → +Z (south)
- `90` → −X (west)
- `180` → −Z (north)
- `-90` or `270` → +X (east)

**Pitch** (vertical): `-90` = straight up, `0` = horizon, `+90` = straight down.

Eye height ≈ feet Y + 1.62.

**Aiming at a block face is iterative.** There is no formula you can trust blindly because the overlay is ground truth and small angle changes flip which face you hit. The loop:

1. `/tp` to a guess (position + yaw + pitch).
2. `screenshot`, read the `/slabdy` overlay: it tells you the exact `target_pos` and `side=<face>` you're pointing at.
3. Adjust pitch/yaw a few degrees and repeat until `side=up` (for placing on top), `side=north/…` (for a side), etc.

**Reliable trick for "place on top of block X":** stand directly over it and look straight down (`pitch 90`) — the overlay will read `side=up` on the block beneath you. Caveat: if you're standing *on* the target column, the placement cell may be your own body (placement silently fails). Stand 1–2 blocks offset, or aim from the side at a shallow angle. See §6.

---

## 5. Placing blocks — the keyboard-rebind workaround (critical)

Right-click is **not delivered** to MC, so the default "Use Item/Place Block" (bound to `mouse.right`) can't fire — no placement, no `onPlaced`. Fix: rebind "Use" to a **keyboard key**, which *is* delivered.

**Procedure (do it with MC stopped — MC overwrites `options.txt` on quit):**

1. In Modrinth App, click **Stop** (top-right of the instance page). Wait for "No instances running".
2. Edit the controls file:
   ```bash
   f=~/Library/Application\ Support/ModrinthApp/profiles/Slabbed\ 1.21.1/options.txt
   # pick a key that is UNBOUND — check the key_ list first:
   grep -n '^key_' "$f"
   sed -i '' 's/^key_key.use:key.mouse.right$/key_key.use:key.keyboard.r/' "$f"
   grep -n '^key_key.use' "$f"   # confirm -> key.keyboard.r
   ```
   `r` is unbound by default in vanilla; verify nothing else uses your chosen key.
3. In Modrinth, click **Play**, wait ~30–45s, click **Singleplayer**, double-click the world.
4. Now **press `r`** (the `key` tool) to place a block / use an item. Single tap = one placement. This fires the real `onPlaced` path.

**Same trick generalizes:** anything gated behind a non-delivered key (Escape menus, etc.) can be rebound to a delivered keyboard key.

**ETIQUETTE — REVERT IT WHEN DONE.** You changed the user's keybinds. Before they play normally, revert (MC stopped):
```bash
sed -i '' 's/^key_key.use:key.keyboard.r$/key_key.use:key.mouse.right/' "$f"
```
Always tell the user the keybind state in your report.

---

## 6. Movement — `hold_key`

Keyboard movement keys are delivered, but a normal keypress is a *tap* (you twitch an inch). Use `hold_key` to press-and-hold:

```
hold_key text:"w" duration:2.5     # walk forward ~11 blocks (≈4.3 blocks/s)
```

- `w/a/s/d` move relative to **yaw** (the way you're facing), regardless of pitch.
- `space` jump, `shift` sneak, hold a movement key + `left.control` to sprint.
- **Measure movement** by looking straight down (`/tp … 0 90`) and reading the overlay's block coords before/after — they change by your displacement. (Verified: held `w` 2.5s, Z went 200→211.)

For *precise* test setups, `/tp` is still better than walking — it's exact. Use WASD when reproducing a player-style sequence, not when you need a block placed at an exact coordinate.

---

## 7. The `/slabdy` overlay — your primary instrument

Toggle with `/slabdy`. Top-left HUD shows, for the block under your crosshair:
```
[slabdy] <x>, <y>, <z>  <BlockName>  side=<face>  dy=<value>
```
- `dy` is the Slabbed vertical model offset (`0.000`, `-0.500`, `-1.000`, `+0.500`). **This is what you're usually verifying.**
- `side=` tells you which face you're aiming at — essential for placement aiming.
- Use the **`zoom`** tool on the overlay region (e.g. `region:[290,222,560,250]` when windowed) to read the value unambiguously for the record.
- The overlay resets to OFF on relaunch — re-enable it each session.

Also useful: the `/slabdy` value updates live, so once you're aimed at a block you can change the world around it (setblock a slab below, break a neighbor) and **re-screenshot to watch its dy react** without re-aiming.

---

## 8. Window / focus / mouse-grab management

- The game auto-pauses (singleplayer) when it loses focus. Bringing it back shows the **Game Menu** — which is how you reach **Options** without `Escape` (click another app to defocus, click MC to refocus → menu appears → left-click Options).
- "Back to Game" (left-click) resumes and re-grabs the mouse.
- After `/tp`+`Return`, the mouse is grabbed and you're in first-person; clicks then act in-world (left-click breaks, `r` places).
- If a left-click "does nothing," the window likely isn't focused/grabbed — click its title bar, then into the viewport (note: a viewport click while grabbed will *break* the targeted block). Aim at sky first if you don't want to break anything.

---

## 9. Hotbar / inventory setup

You can't reliably drag items (no usable mouse precision). Use commands:
```
/give @s minecraft:oak_log 64
/item replace entity @s hotbar.0 with minecraft:oak_log 64       # slot 1 (0-indexed!)
/item replace entity @s hotbar.1 with minecraft:smooth_stone_slab 64
```
Select a hotbar slot with the number keys (`1`–`9`). Confirm the held item via screenshot (and by what `r` actually places).

---

## 10. Worked example — the FROZEN_FLAT live test (end to end)

Goal: prove a real-placed flat block stays at `dy=0` when a slab is later placed under it (does not "pop" to `-0.5`).

1. `/slabdy` (overlay on). `/item replace entity @s hotbar.0 with minecraft:oak_log 64`.
2. `/setblock 213 -60 212 stone` — temp support.
3. `/tp @s 213.5 -59 210.5 0 20` → screenshot → adjust pitch until overlay reads `213, -60, 212 Stone side=up` (aiming at the stone's *top*).
4. `key "1"` (select log), `key "r"` → log places on top at `(213,-59,212)`. Overlay: `Oak Log dy=0.000`. Real `onPlaced` ran → it's recorded frozen-flat.
5. `/setblock 213 -60 212 air` → support gone, log **floats**; overlay still `dy=0.000` (no pop). ✓
6. `/setblock 213 -60 212 smooth_stone_slab[type=bottom]` → slab directly under the floating log (the exact reported violation).
7. Screenshot + `zoom` the overlay: **`Oak Log dy=0.000`** with a visible half-block gap above the slab → the block stayed put. **PASS.**

Contrast control: a block placed by `setblock` (no `onPlaced`) instead of `r` would read `dy=-0.500` here — that's the bug the freeze suppresses.

---

## 11. When live is impractical: the gametest fallback for `onPlaced`

If the placement rig is too fiddly or you need determinism, prove `onPlaced` logic with a **server gametest** that runs the real mixin path. The helper in `SlabbedLabFixtureTest`:
```java
private static BlockState authorBlock(ServerWorld world, BlockPos pos, BlockState state) {
    world.setBlockState(pos, state, Block.NOTIFY_ALL);
    state.getBlock().onPlaced(world, pos, state, null, ItemStack.EMPTY); // fires BlockOnPlacedAnchorMixin
    return world.getBlockState(pos);
}
```
`authorBlock` = real placement path; plain `setBlockState` = the no-`onPlaced` (terrain) path. Run with `./gradlew runGameTest`.
**Note:** the `*ClientGameTest.java` files (which use `mc.interactionManager.interactBlock`, true client placement) are **NOT compiled on 1.21.1** — `build.gradle` `sourceSets.gametest` includes only the server files. Don't rely on them here.

---

## 12. Failure modes & fixes (quick reference)

| Symptom | Cause | Fix |
|---|---|---|
| `r`/click "does nothing" placing | placement cell = your body, or wrong face | offset 1–2 blocks; check overlay `side=`; aim at a clear cell |
| Command typed but no effect | leading `/` dropped (sent as chat) | `wait 1` after `t`; screenshot the input line before `Return` |
| Camera won't turn | mouse-look not delivered | use `/tp … yaw pitch` |
| Right-click won't place | right button not delivered | rebind "Use" to keyboard key (§5) |
| Escape won't open menu | Escape not delivered | defocus→refocus to trigger pause menu (§8) |
| Overlay gone after relaunch | resets each session | re-run `/slabdy` |
| Windows keep reshuffling | Stage Manager on | turn it OFF |
| Block placed is wrong item | wrong hotbar slot selected | `/item replace … hotbar.N`; select with number key |

---

## 13. Session etiquette / cleanup checklist

- [ ] **Revert any keybind/config changes** (especially "Use" → `mouse.right`) with MC stopped.
- [ ] Leave proof structures in-world if useful, and tell the user *where* (coords) so they can look.
- [ ] Note the jar version/byte-size tested and whether it's the one in the user's profile.
- [ ] Write a findings report: what you tried, what held, what broke (with coords + overlay readings), and what's a real bug vs. expected-by-design.
- [ ] Commit code locally only if asked; **never push without explicit request.**
- [ ] Record durable lessons (new gotchas, working coordinates/rigs) to memory.

---

*Core philosophy: treat the game as a black box you can only see through screenshots and only nudge through a known-good set of inputs. Build instruments (the overlay, command echoes) into your loop, verify every step visually, and never assume an input landed — confirm it.*
