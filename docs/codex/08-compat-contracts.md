# 08 — Compatibility Contracts

Compatibility is named law, not generic promotion.

## Terrain Slabs contract

Terrain Slabs / Countered custom slab blocks must not become generic Slabbed slab sources.

They may participate only through a named direct-support compat law that proves:

- surface kind is valid
- subject block/item is eligible
- model/outline/raycast remain coherent
- culling is not reopened
- generated/snowy/waterlogged variants are not silently admitted

## Legal scoped path

A proven dry bottom-like Terrain Slabs state may directly support named Slabbed subjects. That support must not imply:

- generic `isSupportingSlab`
- lowered slab lane inheritance
- recursive carrier scans
- culling-sensitive generic support paths
- Terrain Slabs self-model offsetting

## Forbidden compat shortcuts

- global culling disable
- model wrapper removal
- broad `ClientDy` edits
- broad `SlabSupport` generic promotion
- global solidity/sturdy-face lies
- treating all custom slabs as vanilla slabs

## Fresh RED requirement

Do not touch Terrain Slabs culling unless a fresh RED proof identifies culling as the failing layer.

Do not expand Terrain Slabs variants unless proof names the exact state, for example:

```text
terrainslabs:<block>[generated=false,snowy=false,type=bottom,waterlogged=false]
```

## Compat proof schema

```text
custom state=
surface kind=
subject state=
expected dy=
actual dy=
model dy=
outline dy=
target dy=
culling proof=
variant boundaries=
```
