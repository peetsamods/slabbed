# 07 — Live Test and Log Recipes

Use when live play shows ghosting, weird hitboxes, targeting theft, moving-up behavior, clipping, missing faces, or no meaningful live difference after automated proof.

## Goblin/live loop

1. Record exact shape, held item, aim location, and wrong behavior.
2. Extract one repeated illegal state or contradiction.
3. Add/identify RED proof for that one mechanism.
4. Fix one layer only.
5. Run compile/gametest.
6. Re-goblin the same shape.
7. Savepoint immediately after one confirmed live win.

## Standard client gametest command

```bash
./gradlew --no-daemon runClientGameTest --console plain
```

## Generic log extraction

```bash
mkdir -p tmp/<issue>-fail-$(git rev-parse --short HEAD)
for f in run/logs/latest.log build/run/client/logs/latest.log build/run/clientGameTest/logs/latest.log build/run/clientGameTest/logs/debug.log; do
  if [ -f "$f" ]; then
    safe_name="$(echo "$f" | tr '/.' '__')"
    cp "$f" "tmp/<issue>-fail-$(git rev-parse --short HEAD)/${safe_name}.copy.log"
    rg -n "GREEN|RED|FAIL|ERROR|SLABBED|LOWERED|PHASE|GOBLIN|RETARGET|dy=|owner|target|MISS|StackOverflow|ArrayIndexOutOfBounds|InvalidInjectionException|MixinApplyError" "$f" > "tmp/<issue>-fail-$(git rev-parse --short HEAD)/${safe_name}.extract.txt" || true
  fi
done
```

## Retarget/owner markers to grep

```bash
rg -n "RETARGET|owner|target|MISS|sideOwnerWouldWin|anchoredUpPreserve|visibleOwner|dy=|face=|hit=|held|vanilla|final" build/run/clientGameTest/logs/latest.log run/logs/latest.log
```

## SBBS manual-live acceptance markers

Use when Julia reruns the slab-held lowered-side rescue lane in `runClient`.

```bash
rg -n "SLAB_HELD_BLOCK_LOWERED_FACE_SIDE_RESCUE|SLAB_HELD_BLOCK_LOWERED_FACE_SIDE_RESCUE_SUMMARY|MC1211_SBBS_FINAL_SLAB_TARGET|finalResult=|failureLayer=|lanternUnderPos|chainLanternPos" run/logs/latest.log build/run/clientGameTest/logs/latest.log
```

## Render/culling markers to grep

```bash
rg -n "CULL|FACE|shouldDraw|neighbor|render|ChunkRendererRegion|YOffsetEmitter|modelDy|outlineDy|targetDy|dy=" build/run/clientGameTest/logs/latest.log run/logs/latest.log
```

## Runtime forbidden markers

Treat these as hard blockers unless explained:

```text
Invalid player data
InvalidInjectionException
MixinApplyError
updateCrosshairTarget
onPlayerInteractBlock
Vec3d.ofCenter
ArrayIndexOutOfBoundsException
StackOverflowError
NoClassDefFoundError
```

## Live report schema

```text
Shape:
Held item:
Aim location:
Expected owner:
Actual owner:
Model looked:
Outline looked:
Click result:
Neighbor/update/reload involved:
Video/screenshot/log path:
```
