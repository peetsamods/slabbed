# MC 1.20.1 Forge Attachment Persistence Decision

## Scope

Book III slice:

```text
forge-1.20.1-attachment-persistence-decision
```

Decision-first only. No runtime source was changed in this slice.

Forbidden lanes remain:

- networking/client sync migration
- model loading migration
- mixin migration
- gametest migration
- behavior parity
- live proof
- release work
- commits, tags, pushes

## Current Slabbed Storage Shape

Current donor file:

```text
src/main/java/com/slabbed/anchor/SlabAnchorAttachment.java
```

The NeoForge donor stores eight marker sets as per-`LevelChunk` data attachments:

- `ANCHOR_TYPE`
- `FROZEN_FLAT_TYPE`
- `LOWERED_SLAB_CARRIER_TYPE`
- `COMPOUND_FULL_BLOCK_ANCHOR_TYPE`
- `COMPOUND_VISIBLE_SIDE_LOWER_SLAB_TYPE`
- `COMPOUND_VISIBLE_SIDE_UPPER_SLAB_TYPE`
- `COMPOUND_VISIBLE_SIDE_DOUBLE_SLAB_TYPE`
- `COMPOUND_VISIBLE_OWNER_TOP_SLAB_TYPE`

Each marker set is a `LongOpenHashSet` of packed `BlockPos` longs.

Mutation is server-side. Query paths are used from:

- server `Level`
- client `Level`
- non-`Level` render `BlockGetter` views via client fallback predicates

The donor NeoForge data attachment path provides:

- chunk-owned storage
- compact long-array serialization
- chunk dirtying through `setData`
- automatic synced attachment data for client chunk mirrors

Forge 1.20.1 must replace those guarantees deliberately.

## Forge 1.20.1 Evidence

Primary Forge docs:

```text
https://docs.minecraftforge.net/en/1.20.1/datastorage/capabilities/
https://docs.minecraftforge.net/en/1.20.1/datastorage/saveddata/
```

Local Forge API evidence from the locked toolchain:

```text
net.minecraft.world.level.chunk.ChunkAccess
- setUnsaved(boolean)
- isUnsaved()

net.minecraftforge.event.AttachCapabilitiesEvent<T>
- getObject()
- addCapability(ResourceLocation, ICapabilityProvider)
- addListener(Runnable)

net.minecraftforge.common.capabilities.ICapabilitySerializable<T extends Tag>
- combines ICapabilityProvider and INBTSerializable<T>

net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent
- register(Class<T>)

net.minecraftforge.common.capabilities.AutoRegisterCapability
- available annotation
```

## Options Considered

### Option A - Forge LevelChunk Capability

Use a Forge capability attached to `LevelChunk`.

Shape:

- define a Slabbed-owned `SlabAnchorStore` interface or class
- store the eight `LongOpenHashSet` marker sets inside one chunk capability
- attach with `AttachCapabilitiesEvent<LevelChunk>`
- serialize with `ICapabilitySerializable<CompoundTag>`
- call `chunk.setUnsaved(true)` on mutation
- keep client sync as a later explicit networking slice

Why it fits:

- matches current per-chunk ownership
- keeps chunk unload/save locality
- preserves compact long-set storage
- lets most future `SlabAnchorAttachment` query/mutation logic remain conceptually intact
- gives a clear server-persistence slice before client sync

Risk:

- Forge capabilities do not replace NeoForge attachment auto-sync.
- Client mirror and non-`Level` render fallback require a later networking/client sync slice.

### Option B - Dimension SavedData

Use Forge/Minecraft `SavedData` from the level `DimensionDataStorage`.

Why it is not the first choice:

- Slabbed's current truth is chunk-owned, not global dimension-owned
- all anchors would live in a per-dimension global map unless extra chunk indexing is built
- chunk unload/save locality becomes less direct
- client sync is still custom
- render-view fallback still needs a client mirror

SavedData may still be useful later for global indexes or migrations, but it is
not the smallest replacement for the existing chunk attachment model.

## Decision

Chosen path:

```text
Forge LevelChunk capability, one Slabbed anchor store per chunk.
```

Do not use `SavedData` as the primary anchor store for this port.

Do not collapse client sync into this slice. Treat networking/client sync as the
next separate Book III sub-slice after server-side persistence compiles.

## Implementation Plan For Next Slice

Next slice:

```text
forge-1.20.1-anchor-store-capability-server
```

Allowed then:

1. Add a Slabbed anchor store type holding the eight marker sets.
2. Add a Forge capability provider for `LevelChunk`.
3. Register/attach the capability during Forge lifecycle.
4. Replace only the internal NeoForge attachment plumbing in `SlabAnchorAttachment.java`.
5. Preserve public method names and legal state behavior.
6. Keep client fallback predicates in place but expect them to remain unfilled until client sync.
7. Prove with `./gradlew --no-daemon compileJava` and `git diff --check`.

Forbidden then unless explicitly authorized:

- client networking/sync
- render model hooks
- mixins
- gametest migration
- behavior parity changes
- live proof

## Server Capability Slice Result

Implemented slice:

```text
forge-1.20.1-anchor-store-capability-server
```

Implemented:

1. `SlabAnchorMarker` names the eight donor marker buckets.
2. `SlabAnchorStore` stores those buckets as `LongOpenHashSet` values inside one per-chunk object.
3. `SlabAnchorCapabilityProvider` serializes/deserializes the store as a `CompoundTag`.
4. `SlabAnchorCapabilities` registers `SlabAnchorStore` as a Forge capability type and attaches one provider to each `LevelChunk`.
5. The Forge entrypoint registers capability events during mod initialization.

Still intentionally untouched:

- the gameplay-facing `SlabAnchorAttachment.java` public facade
- networking/client sync
- non-`Level` render fallback
- model hooks
- mixins
- gametests
- behavior parity

Proof:

```text
./gradlew --no-daemon compileJava
-> BUILD SUCCESSFUL
```

## Storage Facade Slice Result

Implemented slice:

```text
forge-1.20.1-slab-anchor-attachment-storage-facade
```

Implemented:

1. Replaced NeoForge attachment registration/type tokens in `SlabAnchorAttachment.java` with `SlabAnchorMarker` values.
2. Routed internal get/set/remove storage helpers through the Forge `LevelChunk` `SlabAnchorStore` capability.
3. Added `replace` and `clear` operations to `SlabAnchorStore` for the facade's copy-on-write storage pattern.
4. Updated the no-op runtime diagnostics anchor-event signature to accept `SlabAnchorMarker`.
5. Updated Terrain Slabs compat from NeoForge `ModList` to Forge `ModList` as an unchanged named-compat dependency needed by `SlabSupport`.

Still intentionally untouched:

- networking/client sync
- non-`Level` render fallback
- model hooks
- mixins
- gametests
- behavior parity
- live proof

Proof:

```text
./gradlew --no-daemon compileJava
-> BUILD SUCCESSFUL
```

## Proof Status

Proven:

- the current donor storage surface is per-chunk marker sets
- Forge 1.20.1 has chunk capability and serializable capability APIs
- Forge 1.20.1 chunk data can be marked unsaved
- SavedData is a less direct fit for the current chunk-owned anchor model
- isolated server-side Forge `LevelChunk` capability storage scaffold compiles
- gameplay-facing `SlabAnchorAttachment` storage facade compiles against the Forge capability store
- storage-facade savepoint is closed at `c7a57620` with tag `save/forge-1-20-1-storage-facade`
- the next persistence/view-truth order is decided: networking/client mirror sync before non-`Level` render-view bridge lookup

Not proven:

- client chunk mirror
- non-`Level` render fallback
- save/reload behavior
- live behavior

In-progress Book III implementation:

```text
forge-1.20.1-non-level-render-view-anchor-lookup
```

Scope:

- server `LevelChunk` capability remains authoritative
- Forge network sync mirrors complete marker buckets to clients
- client `Level` queries read the mirror
- non-`Level` fallback predicates may read the same mirror for the current client dimension
- model hooks, mixins, gametests, behavior parity, save/reload proof, Visual Triad proof, and live proof remain untouched

## View Truth Order Decision

Decision record:

```text
docs/porting/mc-1.20.1-forge-view-truth-order-decision.md
```

Chosen order:

```text
1. networking/client mirror sync
2. non-Level render-view bridge lookup
```

Reason:

`SlabAnchorAttachment` already contains non-`Level` fallback predicate readers
for chunk render views, but this Forge branch has no client mirror writer/sync
surface feeding those predicates. Build the mirror/network sync path first, then
wire render-view lookup to that mirror in a separate slice.
