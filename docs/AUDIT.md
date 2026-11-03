# Funky Locomotion — Correctness, Thread-Safety, Compatibility & Refactor Audit

- **Repository:** `MrFuzzihead/FunkyLocomotion` (1.7.10 Forge frames mod)
- **Audit branch:** `refactor/angelica-hodgepodge-compat`
- **Baseline:** `main` @ `6310790` (“Update build tooling and Gradle wrapper”)
- **Scope:** `src/main/java`, `src/api/java`, `src/main/resources`, Gradle/mod metadata
- **Focus:** Angelica (Celeritas + GL 3.3 core/GLSM) and Hodgepodge (GTNH), plus FMP, Thaumcraft, CoFH and general GTNH modpack compatibility
- **Status:** the P0 and P1 batches are implemented on this branch. This document remains the full audit/backlog; see [`TESTING.md`](TESTING.md) for unit coverage, the dedicated-server harness, and the in-game checklist.

All line numbers refer to the baseline above. They will drift as code is refactored; use the class/method names as the stable reference.

---

## 0. TL;DR — the ten things that matter most

| # | Finding | Severity | Area |
|---|---------|----------|------|
| 1 | `ChunkRerenderer` writes `RenderGlobal.worldRenderers` / calls `WorldRenderer.updateRenderer` directly. Under Angelica the vanilla renderer array is replaced by a 1×1×1 dummy grid (all coordinates collapse to index 0), so the direct call rebuilds the chunk at world origin instead of the moved chunk, and it is precisely the pattern Angelica labels as “not thread safe” for Forge Relocation / ProjectRed Frames. It also throws AIOOBE/divide-by-zero across world loads. | **P0** | Angelica / rendering |
| 2 | `TileMovingClient.getRenderBoundingBox()` can construct a `FakeWorldClient` on an **Angelica chunk-meshing worker thread**. The fake-world cache is a plain `HashMap`, and construction fires `WorldEvent.Load` and touches `Minecraft`. Two workers ⇒ corrupted map / duplicate fake worlds / hangs. | **P0** | Angelica / thread safety |
| 3 | `BlockSlider.renderSide` is **static** and is written by `RenderBlockSlider.renderWorldBlock` while `BlockSlider.shouldSideBeRendered` reads it. Angelica meshes sections on multiple threads ⇒ wrong/missing faces and leaked state. | **P0** | Angelica / rendering |
| 4 | `TileTeleport.getDestinationPos()` calls `BlockPos.advance(meta ^ 1)`. `BlockPos.advance(int)` indexes `Facing.offsets*ForSide[0..5]`; puller teleporter metadata (`6..11`) ⇒ `ArrayIndexOutOfBoundsException`. For metadata `6..11` the `^ 1` produces invalid orientation values. | **P0** | Crash / teleporters |
| 5 | For cross-world teleports, `MoveManager` posts source-block updates to **`dstWorld`** (`postUpdateBlock(dstWorld, pos)`) instead of `srcWorld`, causing light/neighbour updates at the source coordinates in the destination dimension. | **P0** | World corruption |
| 6 | `TileMovingBase.readFromNBT` looks for `"Collisions"` as a **compound** (writes a **list**) and for `"TimeTime"` (writes `"TickTime"`). Reloading a world mid-move loses collision boxes and scheduled ticks; movers can become stuck/invisible. | **P0** | Persistence |
| 7 | `MessageClearTile` removes the client tile entity with `chunk.chunkTileEntityMap.remove(new ChunkCoordinates(...))`, but the map is keyed by `ChunkPosition`. The removal silently does nothing; a stale/invalid TE remains in the chunk map (ghost TEs, stale tick/save, divergent TE state). | **P0** | Client state |
| 8 | `TileMovingServer.getDescriptionPacket()` mutates and returns the **same** `desc` NBT instance. `S35PacketUpdateTileEntity` is serialized on the Netty thread; the server thread mutates `desc` on the next tick/send ⇒ CME/corrupt NBT. Hodgepodge’s S35 batcher is not applied to FL’s manual broadcast. | **P0** | Thread safety / network |
| 9 | `BlockMoving.createTileEntity` uses `FMLCommonHandler.getEffectiveSide()`, which is **thread-name based** (`“Server thread”`). On any other server-side thread it returns `CLIENT` and creates a `TileMovingClient` in a `WorldServer`; `MoveManager` then casts it to `TileMovingServer` ⇒ `ClassCastException`. | **P0** | Crash / side-safety |
| 10 | `WrenchFactory` copies **every** method of every discovered wrench interface into a generated class, including `<clinit>`, static/private/default methods, and computes `maxStack/maxLocals` incorrectly for `long`/`double`. Depending on the installed mod set this can produce `ClassFormatError`/`VerifyError`; default methods are silently overridden with `false/0/null`. | **P0** | Startup robustness / mod compat |

Also high-impact: `TileMovingClient.renderStatic` permanently disables a pass after one failed/empty render (`skipPass = true` before the attempt), `BlockHelper.postUpdateBlock` uses a hard-coded old light opacity of 255 (over-eager skylight recalculation), `TilePusher`’s flood fill is unbounded, and the hard CoFH energy dependency is not declared in `@Mod.dependencies`/`mcmod.info`.

### Implemented in the initial P0 batch

- `BlockPos.advance(int)` now delegates to `ForgeDirection`; teleporter source/destination offsets use `meta % 6` and the opposite face for all `0..11` metadata.
- `TileMovingBase` writes/reads `"Collisions"` as a list, `"TickTime"` with the correct key/type, guards null/absent tags, and clamps light values.
- `MessageClearTile` removes source tiles through `World.removeTileEntity` and revalidates reused raw tiles.
- `MoveManager` posts source-block updates to `srcWorld` instead of `dstWorld` for cross-dimension teleports.
- `TileMovingServer.getDescriptionPacket()` sends a deep copy of `desc`, so the live tag is not mutated while Netty serializes it.
- `BlockMoving.createTileEntity` uses `world.isRemote` only, not the thread-name-based effective side.
- `WrenchFactory` skips initializers/static/default/private/native/synthetic/bridge methods, emits class-file v52, and uses `COMPUTE_MAXS`.
- `BlockSlider`’s render side is a `ThreadLocal`, not a plain static; the renderer restores it (and `renderAllFaces`) in a `finally`.
- `TileEntityRenderMoving` retries a block render that returns `false`, instead of permanently skipping that pass.
- `TilePusher` caps the BFS at `maxTiles`, clamps config values, and protects energy capacity/cost from `int` overflow; teleporters/boosters use the same math.
- The copied CoFH/Thaumcraft API stubs in `src/api` were replaced with `devOnlyNonPublishable`/`compileOnly` dependencies.
- `ChunkRerenderer` no longer indexes `RenderGlobal.worldRenderers`; it only calls `markBlockForUpdate`, and `TileMovingClient` is registered as an Angelica dynamic-bounds class.
- `FakeWorldClient` uses a concurrent/synchronized cache, and non-mover lookups delegate to the real world; `getRenderBoundingBox` no longer constructs a fake world and handles zero-duration/teleport scaling.
- `MoveCheckReflector` treats `LinkageError` (client-only method signatures on a dedicated server) as movable without spamming a stack trace.
- `BlockHelper.postUpdateBlock` now uses the replaced block's actual light opacity (and skips light propagation when it is unknown), and guards unloaded chunks.
- `MoveManager.finishMoving` no longer does the air→stone→real-block toggle, removes the full-chunk resend, is idempotent per tile, catches/reports per-tile failures, and cleans up before recreating.
- `MoveManager.startMoving` uses flag `0` for the no-update phase, records source light opacity, and closes destination inventories/watchers during teleports.
- `TileMovingClient` now uses a dimension-aware tile cache, skips client-side light recalculation, handles unknown block ids, and matches blacklisted classes by assignability.
- The slider, frame, wrench and moving-block paths no longer mutate client worlds or duplicate collision/particle state; the `_Immovable` typo has a corrected alias.
- Descriptor fallback/`DescID` handling, teleporter cache cleanup, mover tick-phase handling, pusher power NBT, the logger name/format, and the CoFH dependency declaration were corrected.
- `EntityMovingEventHandler` now delegates to vanilla `Entity.moveEntity` instead of a divergent copy, and clears its movement map only on the END tick phase.
- Block snapshots now use `MoveSnapshot`: a shared versioned reader/writer with registry-name + numeric-id fallback and legacy numeric/string compatibility for both server move data and client descriptions.
- `MoveManager` uses a per-move crash context map instead of a shared static map, and `startMoving` is no longer globally synchronized.

Each item has a focused JUnit regression test where the behaviour is reachable without a live world. A real dedicated server harness (`./gradlew runServer -PflHarness=true`) covers the world/chunk/move paths; see [`TESTING.md`](TESTING.md).

Remaining work is P2: FMP describer registration, silent-write `onBlockAdded`/bounds handling, `breakBlockWithDrop` tool/notification semantics, TESR cache invalidation, and the other rendering/API cleanup items below.

---

## 1. Build & baseline observations

- `./gradlew compileJava` succeeds on the baseline (Jabel, Java 8 target).
- The repo is on a GTNH Gradle convention (`2.0.33`; `2.0.34` is available). `usesMixins = false`; `usesMixinDebug = false`; no coremod. Access transformers are used for `Chunk`, `PlayerManager$PlayerInstance`, `S35PacketUpdateTileEntity`, `S33PacketUpdateSign`, `Tessellator`, and `RenderGlobal`.
- `dependencies.gradle`:
  - `implementation("com.github.GTNewHorizons:ForgeMultipart:1.7.2:dev")` and `CodeChickenCore` are **published** dependencies, although both integrations are optional at runtime.
  - `NEI` is `runtimeOnlyNonPublishable` (correct).
  - `curse.maven:cofh-core` is `devOnlyNonPublishable`, but **CoFH energy classes are a hard runtime dependency** of `TilePusher`/`TileBooster` (see §10.1). A standalone install without CoFHCore will crash when a pusher/booster is placed or loaded.
- `mcmod.info` has an empty `dependencies` array; `@Mod.dependencies` only has `after:ThermalExpansion`, `after:ThermalFoundation`, `required-after:Forge`. It does not declare FMP/Thaumcraft/CoFHCore.
- `LogHelper` logs under the name `newframes`, not `funkylocomotion`, making modpack log filtering confusing.
- `apiPackage = api` moved the old `framesapi.*` classes to `com.rwtema.funkylocomotion.api.*` (commit `427e3ff`). Third-party 1.7.10 addons compiled against `framesapi.*` will now fail with `NoClassDefFoundError`. (No GTNH-wide usage was found in a quick GitHub search, but this is a binary-compatibility break for a released mod API. If the mod has ever shipped with `framesapi`, keep deprecated aliases.)
- The `_Immovable()` convention **is** used by GTNH mods (e.g. the GTNH Immersive Engineering port’s `TileEntityMultiblockPart` and multiblock TEs), so `MoveCheckReflector` is a real compatibility surface and should be preserved and documented (and its typo fixed — see §5).

---

## 2. Thread-safety model (what is actually safe, and what only looks safe)

### 2.1 Server-thread-only state (currently assumed, not enforced)

These are plain mutable collections/fields that are only safe because 1.7.10 ticks them on the server thread. They must be documented and, where cheap, made concurrent or confined:

| State | Location | Risk |
|---|---|---|
| `MoveManager.vars` (static `LinkedHashMap`) | `movers/MoveManager.java:63` | Shared crash-report context. `startMoving` is `synchronized`, `finishMoving` is **not**. Re-entrancy (a block callback starting another move) clears the outer context. `synchronized` on a static method serialises every dimension’s move. |
| `MoverEventHandler.movers` (`WeakSet`) and `skip` | `movers/MoverEventHandler.java:12-13` | `WeakHashMap` is not thread-safe. `skip` is a global hack; movers registered during `startMoving` are dropped by the second `movers.clear()`. |
| `MovingTileRegistry.movingTilesSet` (`WeakSet`) | `movers/MovingTileRegistry.java:12` | Server-thread only; `WeakHashMap` iteration while a tile invalidates is avoided only by copying to a list. |
| `TileTeleport.cache` (`TLongObjectHashMap<WeakSet<...>>`) | `blocks/TileTeleport.java:32` | Server-thread only. `WeakSet` is not concurrent. Entries are never removed when the set becomes empty. |
| `EntityMovingEventHandler.client/server` (`WeakHashMap<Entity, Vec3>`) | `EntityMovingEventHandler.java:18-19` | One map per logical side; values are always `null` (a `Set` would do). Client map is client-thread only. |
| `MoveCheckReflector.cache` (`HashMap<Class, Boolean>`) | `movepermissions/MoveCheckReflector.java:14` | Unbounded class cache; not concurrent. |
| `DescriptorRegistry.proxyMap/proxyList` | `description/DescriptorRegistry.java:12-13` | `proxyList` is returned live and iterated by `MoveManager`; registration during iteration ⇒ CME. |
| `FactoryRegistry.moveFactoryMap*` | `factory/FactoryRegistry.java:16-18` | Registration during init; reads during ticks. `moveFactoryMapInheritanceClass` iteration order is unspecified. |
| `ProxyRegistry.proxies` | `proxydelegates/ProxyRegistry.java:7` | Class→proxy cache grows unbounded; `getInterfaces()` only, no superclass walk. |
| `TileMovingClient.cachedTiles`, `renderBlackList`, `renderErrorList` | `blocks/TileMovingClient.java:31-33` | Client-thread only, dimensionless, never pruned; `renderBlackList` is never populated. |
| `WordDictionary.words` | `rendering/WordDictionary.java:22` | Client-thread only; non-deterministic order. |
| `BlockSlider.renderSide` | `blocks/BlockSlider.java:27` | **Not** safe: read during Angelica worker-thread meshing. See §7.1. |

### 2.2 The `FakeWorldClient` problem (P0, Angelica)

`TileMovingClient.getRenderBoundingBox()` (`blocks/TileMovingClient.java:146-162`) calls:

```java
block.getCollisionBoundingBoxFromPool(FakeWorldClient.getFakeWorldWrapper(worldObj), xCoord, yCoord, zCoord)
```

when `tile == null`. `FakeWorldClient.getFakeWorldWrapper` (`fakes/FakeWorldClient.java:59-67`) lazily constructs a `FakeWorldClient` and stores it in:

```java
private static final HashMap<World, FakeWorldClient> cache = new HashMap<>();
```

`FakeWorldClient` extends `WorldClient` and constructs a new `NetHandlerPlayClient(Minecraft.getMinecraft(), null, new NetworkManager(true))`, which fires `WorldEvent.Load` (the same event the “Hotfix CME” commit worked around) and allocates world/provider/map-storage objects.

Why this is dangerous under Angelica:

- `AngelicaChunkBuilderMeshingTask` runs on **worker threads** and calls `TileEntityRenderBoundsRegistry.classify(tileEntity)` (line ~218 of the current Angelica source). For a class classified `STATIC`, it then calls `tileEntity.getRenderBoundingBox()` on the worker to pack section-local bounds.
- `TileEntityRenderBoundsRegistry.classify` itself calls `te.getRenderBoundingBox()` on the first instance of a class.
- Therefore, the first `TileMovingClient` encountered by a chunk build can call `getRenderBoundingBox()` **off the client thread**. If the packet hasn’t populated `tile`, FL creates a `FakeWorldClient` on that worker: `Minecraft` access off-thread, `WorldEvent.Load` handlers off-thread (including mods that bind TESRs ⇒ `ConcurrentModificationException` in `TileEntityRendererDispatcher`), and concurrent `HashMap.put` from two workers (classic 1.7.10 HashMap corruption/infinite loop).
- `FakeWorldClient.getBlock`/`getTileEntity`/`isAirBlock`/`isSideSolid` do not delegate to the real world for non-mover positions. On a worker this is not only wrong for neighbour-dependent rendering (fences, glass, stairs, redstone), it also means the fake world is being used as an `IBlockAccess` during meshing.

**Fixes (in order):**

1. Register the class as dynamic with Angelica via reflection, before any chunk mesh:
   ```java
   // ProxyClient.registerRendering() or FMLInitializationEvent, client only
   try {
       Class.forName("com.gtnewhorizons.angelica.rendering.TileEntityRenderBoundsRegistry")
            .getMethod("registerDynamicClass", String.class)
            .invoke(null, "com.rwtema.funkylocomotion.blocks.TileMovingClient");
   } catch (Throwable ignored) { /* Angelica absent */ }
   ```
   `TileEntityRenderBoundsRegistry` checks `dynamicClassNames` before calling `getRenderBoundingBox()`, so the worker never calls into FL’s dynamic box. (Also add the class to the documented `dynamicBoundsTileEntities` config option for users who run an older Angelica.)
2. Make `getRenderBoundingBox()` cheap and side-effect free: derive the box from the cached `collisions` array and the block’s static collision box, plus `offset(true)`. Do **not** construct a fake world there. Cache the block collision box at packet time.
3. Make `FakeWorldClient.cache` a `ConcurrentHashMap` + `computeIfAbsent`, and refuse creation unless `Minecraft.getMinecraft().isCallingFromMinecraftThread()` (or `Thread.currentThread() == Minecraft.getMinecraft().mcThread`). On the render thread only.
4. Guard `maxTime <= 0` in `offset()`/`getRenderBoundingBox()` (currently `(time + f) / maxTime` ⇒ NaN before the first S35 arrives).

### 2.3 Chunk writes and Angelica’s `ConcurrentTileEntityMap`

Angelica’s `MixinChunk` replaces `chunk.chunkTileEntityMap` with `ConcurrentTileEntityMap` (a `HashMap` subclass with locks and a render-thread invalidation queue). FL does direct chunk/TE manipulation:

- `BlockHelper.silentSetBlock` / `silentClear` write `ExtendedBlockStorage` directly (`helper/BlockHelper.java:20-60`). This is fine on the server thread as a “no updates yet” primitive, but it bypasses `Block.onBlockAdded`, `Chunk.func_150807_a`, and Celeritas’s change tracking. Every direct write must be followed by `world.markBlockForUpdate`/`RenderGlobal.markBlockRangeForRenderUpdate` (Celeritas’s `MixinRenderGlobal` overwrites `markBlocksForUpdate` and schedules a rebuild).
- `MessageClearTile` uses the wrong key type (see §3.3) and removes the TE from `loadedTileEntityList` directly. Prefer `world.removeTileEntity(x, y, z)` (which uses `Chunk.removeTileEntity` → `chunkTileEntityMap.remove(ChunkPosition)` and `invalidate()`), after caching the TE for reuse.
- `ChunkUnloadFactory.saveTile` calls `chunk.chunkTileEntityMap.remove(...)`, `tile.getWorldObj().loadedTileEntityList.remove(tile)`, and `tile.onChunkUnload()` directly (`factory/ChunkUnloadFactory.java:13-24`). It is currently unused; if it is revived, use `World.removeTileEntity` and check `tile.getWorldObj() != null` after `onChunkUnload`.
- `MoveManager.startMoving` sets blocks with flag `1` (neighbour notify) during the “NO BLOCK UPDATES” phase. That fires `onNeighborBlockChange` on all six neighbours while the source block is still present. Use flag `0` and send the client packets explicitly (the code already does).
- `MoveManager.finishMoving` sets air then **stone** to force a block update, then sends `chunk.isModified = true` + `FLNetwork.updateChunk(chunk)`. This is a heavy hack; see §4.6.

---

## 3. Critical bugs (P0)

### 3.1 `BlockPos.advance(int)` and puller teleporters (`TileTeleport`) — AIOOBE

**Files:** `api/BlockPos.java:46-51`, `blocks/TileTeleport.java:94-100`

```java
public BlockPos advance(int i) {
    return new BlockPos(x + Facing.offsetsXForSide[i], ...); // 6 entries
}
```

`TileTeleport.getDestinationPos` does:

```java
BlockPos srcPos = new BlockPos(this).advance(getBlockMetadata() ^ 1);
BlockPos dstPos = new BlockPos(tile).advance(tile.getBlockMetadata() ^ 1);
```

Metadata for `BlockTeleport` is `0..5` (push) or `6..11` (pull). `6 ^ 1 = 7`, `7 ^ 1 = 6`, etc. → `advance(6..11)` → `ArrayIndexOutOfBoundsException`. A puller teleporter placed via world edit, NBT, a command, or a future item variant crashes the server tick. It is also a public API landmine for any addon calling `advance(6)`.

The `^ 1` is also semantically wrong: for metadata `0..5` it happens to equal `Facing.oppositeSide[meta]`, but for `6..11` it produces invalid orientation values and does not express "the face the teleporter is looking at". Use `meta % 6` explicitly.

**Fix:**

```java
private static ForgeDirection facing(int meta) {
    return ForgeDirection.getOrientation(meta % 6).getOpposite();
}
BlockPos srcPos = new BlockPos(this).advance(facing(getBlockMetadata()));
BlockPos dstPos = new BlockPos(tile).advance(facing(tile.getBlockMetadata()));
```

and make `advance(int)` delegate to `ForgeDirection.getOrientation(i)` (returning the same position or throwing a clear `IllegalArgumentException` for `UNKNOWN`/invalid), never raw `Facing` arrays.

### 3.2 Cross-world teleport updates the wrong world

**File:** `movers/MoveManager.java:266-270`

```java
for (BlockPos pos : srcBlocksToBecomeAir) {
    BlockHelper.postUpdateBlock(dstWorld, pos); // BUG: should be srcWorld
}
```

For `srcWorld != dstWorld` this relights, remeshes, notifies neighbours, and possibly triggers comparator updates at the **source coordinates in the destination dimension**, corrupting unrelated blocks. It also leaves the source world’s direct `silentClear` without its intended light/neighbour update (the final `finishMoving` happens only after the animation).

**Fix:** `BlockHelper.postUpdateBlock(srcWorld, pos);`, and add a regression test for a cross-dimensional teleporter with blocks at overlapping coordinates.

### 3.3 `MessageClearTile` removes the tile entity with the wrong key type

**File:** `network/MessageClearTile.java:67-73`

```java
clientWorld.loadedTileEntityList.remove(tile);
Chunk chunk = clientWorld.getChunkFromBlockCoords(x, z);
chunk.chunkTileEntityMap.remove(new ChunkCoordinates(x & 15, y, z & 15));
tile.invalidate();
```

`Chunk.chunkTileEntityMap` is `Map<ChunkPosition, TileEntity>` (`Chunk.java:66`). `ChunkCoordinates.equals` requires `instanceof ChunkCoordinates`, so the removal is a no-op. The TE is removed from `loadedTileEntityList` but remains in the chunk map (invalid); `world.getTileEntity` still returns it, and the chunk map accumulates invalid TEs until a chunk reload/`removeInvalidTileEntity`. `rawTile` reuse can still work via `cachedTiles`, but the stale TE may be re-ticked/saved or returned to a `FakeWorldClient` lookup, and chunk map state diverges from the loaded-TE list.

**Fix:** cache the tile first, then call `clientWorld.removeTileEntity(x, y, z)` (which uses the correct `ChunkPosition` and invalidates), or use `new ChunkPosition(x & 15, y, z & 15)` explicitly. Do not manipulate `loadedTileEntityList` directly. Add a dimension/world component to `cachedTiles` (see §4.4).

### 3.4 Shared `desc` NBT in S35 packets

**File:** `blocks/TileMovingServer.java:24-40`

```java
public Packet getDescriptionPacket() {
    if (desc == null) return null;
    desc.setInteger("Time", time);
    desc.setInteger("MaxTime", maxTime);
    desc.setByte("Dir", (byte) dir);
    ...
    return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, desc);
}
```

`desc` is the same `NBTTagCompound` instance stored in the tile and (via `MoveManager`) in the move entry. `S35PacketUpdateTileEntity.writePacketData` serializes the tag on the Netty thread. The server thread mutates `desc` every tick (`time`), and `MoveManager.networkUpdateTile` calls `getDescriptionPacket` again. Result: `ConcurrentModificationException` in `NBTTagCompound`/`CompressedStreamTools`, or packets that contain fields from a different tick. Hodgepodge’s `TileEntityDescriptionBatcher` does **not** cover FL’s manual `PlayerInstance.sendToAllPlayersWatchingChunk(packet)` path (it hooks `EntityPlayerMP.func_147097_b`, `ForgeHooks.onBlockBreakEvent`, and the private `PlayerInstance.sendTileToAllPlayersWatchingChunk`), so FL is exposed unless it batches itself.

**Fix:** clone the base tag and write the dynamic fields on the clone:

```java
NBTTagCompound copy = (NBTTagCompound) desc.copy();
copy.setInteger("Time", time); ...
return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, copy);
```

`NBTTagCompound.copy()` is cheap relative to serialization; Hodgepodge has a `speedupNBTTagCompoundCopy` patch. Also consider a single immutable `DescSnapshot` built when fields change.

### 3.5 `TileMovingBase` NBT key/type mismatches

**File:** `blocks/TileMovingBase.java:81-122`

```java
if (tag.hasKey("Collisions", 10)) collisions = AxisTags(tag.getTagList("Collisions", 10));
...
if (tag.hasKey("TimeTime", 3)) {
    scheduledTickTime = tag.getInteger("TickTime");
    ...
}
```

- `writeToNBT` writes `"Collisions"` with `TagsAxis(...)`, which is an **NBTTagList** (id 9), not a compound (id 10). `hasKey("Collisions", 10)` is always false ⇒ collision boxes are lost on save/load. A mover that survives a restart (or a chunk unload/reload) becomes pass-through: entities fall through platforms.
- `writeToNBT` writes `"TickTime"`/`"TickPriority"`; `readFromNBT` checks `"TimeTime"` ⇒ scheduled ticks are never restored. Blocks that depend on a scheduled tick (e.g. flowing water, some mod machines) may not resume.
- `lightOpacity` defaults to 255 in the field but reads a missing key as 0; `readFromNBT` should default to 255 or 0 consistently and clamp to 0..255.
- `writeToNBT` writes `"BlockTag"`/`"DescTag"` without null guards; `block`/`desc` can be null for a freshly created server TE (before `MoveManager` initialises it) and for NBT-corrupt data.

**Fix:** `hasKey("Collisions", 9)`, `hasKey("TickTime", 3)`, null guards, clamp light values, and add a version key (`"FLVersion"`) for future format changes.

### 3.6 `BlockMoving.createTileEntity` uses a thread-name side check

**File:** `blocks/BlockMoving.java:88-93`

```java
if (world.isRemote || FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT)
    return new TileMovingClient();
else return new TileMovingServer();
```

FML 1.7.10’s `getEffectiveSide()` is:

```java
Thread thr = Thread.currentThread();
if (thr.getName().equals("Server thread")) return Side.SERVER;
return Side.CLIENT;
```

Any server-side TE creation on a thread not literally named `"Server thread"` (world generation workers, chunk I/O callbacks, other mods’ threaded loaders, Angelica’s meshing context if it ever creates a TE) yields a `TileMovingClient` in a `WorldServer`. `MoveManager.startMoving` then does `(TileMovingServer) dstWorld.getTileEntity(...)` (`MoveManager.java:281`) ⇒ `ClassCastException`. It is also wrong for integrated-server logic that runs on the client thread.

**Fix:** `return world.isRemote ? new TileMovingClient() : new TileMovingServer();`; remove the `FMLCommonHandler` import. Add `@SideOnly(Side.CLIENT)` to `TileMovingClient` and ensure `BlockMoving` does not class-load it server-side (it won’t if the branch is not taken; the method reference is resolved lazily by HotSpot, but a `@SideOnly` annotation makes the intent explicit and lets Forge strip it).

### 3.7 `WrenchFactory` bytecode generation hazards

**File:** `asm/WrenchFactory.java`

The generator iterates every `MethodNode` of each present wrench interface and emits a public stub, skipping only methods already present on `ItemWrench`. Problems:

1. **`<clinit>`**: interfaces with static fields have a `<clinit>`; emitting `ACC_PUBLIC <clinit>` is an illegal class initializer ⇒ `ClassFormatError`. (Rare among wrench interfaces, but fatal when present.) Skip `<clinit>`/`<init>` and non-`ACC_ABSTRACT`/non-`ACC_PUBLIC` methods.
2. **Default methods**: Java 8 interfaces with `default` implementations are copied as stubs returning `false/0/null`; the default behaviour is lost. For a wrench that may be a *tool*, a `null` return can NPE in the owning mod. Only implement **abstract** interface methods; let defaults work (generated class should be class-file version 52+).
3. **Static interface methods**: copied as instance methods with the same name/desc (legal but confusing). Skip `ACC_STATIC`.
4. **`maxStack`/`maxLocals`**: `visitMaxs(returnOpCode != RETURN ? 1 : 0, 1 + args.length)` is wrong for `long`/`double` returns/args (2 slots each). `LCONST_0` + `LRETURN` needs max stack 2; a `long` argument needs 2 local slots. Use `ClassWriter.COMPUTE_MAXS` (or `COMPUTE_FRAMES` with a proper class loader) and stop hand-computing. (No current wrench interface in the default list is known to use `long`/`double`, so this is latent, but it is a one-line fix.)
5. **Class version**: `V1_6` (50) cannot inherit Java 8 default methods; use `V1_8` (52) for the generated class.
6. **`loader` cast**: `(LaunchClassLoader) ItemWrench.class.getClassLoader()` at class-init time throws `ClassCastException` in non-Launch environments. Use `instanceof` and fall back to normal `Class.forName`.
7. **Cache/validation**: skip non-interface classes (`ACC_INTERFACE` check), cache the generated class per class-loader, and log which interfaces were implemented/skipped. A silent `IOException` currently produces a plain `ItemWrench` with no wrench interfaces; log a warning so users can diagnose a broken optional API jar.

### 3.8 `TileMovingClient.renderStatic` permanently disables a pass after one failure

**File:** `rendering/TileEntityRenderMoving.java:200-236`

```java
mover.cachedState[pass] = null;
mover.skipPass[pass] = true;          // pessimistic before the attempt
try {
    if (renderBlocks.renderBlockByRenderType(...)) { ... mover.skipPass[pass] = false; }
} catch (Exception e) { ... mover.skipPass[pass] = true; }
```

If the block renderer returns `false` on its first call (some ISBRHs return false until their `IBlockAccess` is “ready”, or for a conditional model), `skipPass[pass]` stays `true` and the block is **never rendered again** for the lifetime of the mover. On the client, the first frame can race the S35 packet/chunk load.

**Fix:** only set `skipPass[pass] = true` when the renderer is definitively empty (returned `false` with zero vertices *and* no exception), and allow periodic/`init`-time retries. Catch `Throwable` (or at least `LinkageError`) so a missing optional renderer class disables that block rather than crashing.

### 3.9 `BlockSlider.renderSide` is a cross-thread static

**Files:** `blocks/BlockSlider.java:27, 50-53`, `rendering/RenderBlockSlider.java:49-70`

```java
public static int renderSide = -1;
...
return renderSide != 6 && (renderSide == -1 || side == renderSide || side == Facing.oppositeSide[renderSide])
    && super.shouldSideBeRendered(...);
```

`renderWorldBlock` sets the static, renders the overlay, then resets it. Angelica’s `AngelicaChunkBuilderMeshingTask` calls `RenderBlocks.renderBlockByRenderType` on **worker threads** (one `RenderBlocks` per task, but the static is JVM-global). Two sections containing sliders can mesh concurrently ⇒ a worker sees another worker’s `renderSide`, culling the wrong faces. If the render throws, `renderSide` is never reset. This is exactly the class of global-state problem Angelica’s own source comments about (`canRenderInPass`/BuildCraft).

**Fix:** remove the static. Options, in order of preference:
1. Bake the arrow into a separate model/face layer (e.g. a second `IIcon` selected by `getIcon(IBlockAccess, ...)` based on the tile’s `getSlideDir()`, with `shouldSideBeRendered` derived from the same immutable per-position data).
2. Pass the side via the `RenderBlocks` instance (a `ThreadLocal<Integer>` keyed to the renderer, cleared in `finally`).
3. At minimum, `try/finally` reset and a `ThreadLocal` fallback; never use a bare static.

### 3.10 `TilePusher` flood fill is unbounded

**File:** `blocks/TilePusher.java:98-134`

`getBlockPosIterate` performs a BFS over the sticky graph and accumulates `posList`/`toIterate` without any limit. `maxTiles` is only used to size `EnergyStorage`; it is not enforced during the search. A player can build a huge connected frame/sticky structure (or a mod block that reports every side sticky) and cause a multi-million-position allocation/loop before the energy check rejects the move. On a server this is a tick hang/OOM vector.

**Fix:** pass a limit (config `maximumBlocksPushed`, clamped to a sane max, e.g. 4096/8192) into the BFS, abort early and show the obstruction/limit message. Also check the limit before `checkPositions` and before `energy = posList.size() * powerPerTile` (guard integer overflow).

---

## 4. High-severity bugs (P1)

### 4.1 Block/tile snapshot format is inconsistent and lossy

`MoveManager` builds two different schemas:

- **Server restore tag** (`tile.block`, returned by `IMoveFactory.destroyBlock`): `"Block"` is a **registry name string**, `"Meta"` a byte, optional `"Tile"` NBT (see `DefaultMoveFactory`).
- **Client render description** (`tile.desc`): `"Block"` is an **int block ID**, `"Meta"` a byte, optional `"Tile"` and `"DescID"` (see `MoveManager.java:166-191` and `TileMovingClient.onDataPacket`).

Consequences:
- `Block.getBlockById` returns `null` for an unknown/negative ID (`Block.getIdFromBlock` can return `-1` for unregistered blocks) → NPE in `shouldRenderInPass`/`getRenderBoundingBox`.
- `Block.getBlockFromName(tile.block.getString("Block"))` returns `null` for old/numeric tags or a custom factory that writes an int → the block becomes air/stone (see §4.6).
- `MoveManager` deliberately sets `"DescID"` even when the proxy ID is empty due to `if (!"DescID".isEmpty())` (`MoveManager.java:177`) — that string literal is never empty, so vanilla tiles get `DescID=""`. This is **load-bearing**: fixing it to `!d.getID().isEmpty()` would make `TileMovingClient` call `DescriptorRegistry.getDescriptor(null)` for vanilla tiles and lose the vanilla TE renderer. The correct fix is to always write the ID (including `""`) or make `getDescriptor(null/empty)` fall back to `DescribeVanilla`.
- `DescriptorRegistry.register(d, priority)` is inverted: `priority == true` appends (lowest precedence) while `priority == false` inserts at index 0 (highest). `DescribeVanilla`/`DescribeVanillaSign` are registered with `false`, so signs win. A mod following the parameter name gets the opposite. Rename to `fallback` or invert the branches, and document that the first matching proxy wins.
- `MoveManager` passes `null` as the `Entity` to `Block.addCollisionBoxesToList(...)` (`MoveManager.java:143-150`). A number of modded blocks dereference `entity` (or use its position/facing). Pass a synthetic `Entity`/the activating player, or catch and fall back to `block.getCollisionBoundingBoxFromPool`.

**Recommendation:** define a versioned `BlockSnapshot`/`TileSnapshot` (registry name, meta, NBT, description ID, collision boxes, light values) with `write`/`read` helpers and one code path for server and client; keep the old tags readable for one release.

### 4.2 `BlockHelper.postUpdateBlock` uses the wrong old light opacity

**File:** `helper/BlockHelper.java:62-106`

```java
int k2 = 255;
...
if (j2 != k2 && (j2 < k2 || chunk.getSavedLightValue(Sky, ...) > 0 || ...)) {
    chunk.propagateSkylightOcclusion(...);
}
```

This was copied from `Chunk.func_150807_a`, where `k2` is the **previous block’s** light opacity. Setting it to 255 means “the old block was fully opaque”, so every placement with `j2 < 255` (almost every block) runs `propagateSkylightOcclusion` for the column. For a 1024-block frame this is a large light update burst (and under Angelica, extra section rebuilds).

**Fix:** capture `int oldOpacity = chunk.getBlock(...).getLightOpacity(...)` before the write and use it; ideally reuse the vanilla light-update code path instead of duplicating it.

### 4.3 `finishMoving` finalisation is heavy, non-idempotent and lossy

**File:** `movers/MoveManager.java:410-514`

- `setBlock(air)` then `setBlock(stone)` per tile is a hack to force updates. `setBlock` with flag `0` runs `Chunk.func_150807_a` (light recalculation, `onBlockAdded`, `breakBlock`) twice per tile, then `silentSetBlock` writes the real block without `onBlockAdded`. Use `world.removeTileEntity` + `silentSetBlock`/`setBlock(..., 2)` and explicit `markBlockForUpdate`; do not place stone.
- `if (tile.block != null)` — an **empty** tag is non-null; `Block.getBlockFromName("")` returns `null` → `block = Blocks.air`, so a mover with missing/old NBT is replaced by air. More importantly, `readFromNBT` always gives an empty compound for a missing `"BlockTag"`, so a mover created without `MoveManager` initialisation keeps `tile.block` empty and `finishMoving` sets it to air. Guard with `tile.block == null || !tile.block.hasKey("Block")`, and use a registry-name lookup with an ID fallback.
- `BlockHelper.silentClear(chunk, pos)` before `factory.recreateBlock` does not remove any stale TE. `factory.recreateBlock` → `chunk.addTileEntity`, which can coexist with the old invalid TE until `World.updateEntities` removes it. Call `world.removeTileEntity` explicitly first.
- `chunk.isModified = true; FLNetwork.updateChunk(chunk)` calls `PlayerInstance.sendChunkUpdate()`, which sends S22/S23/S21 depending on the accumulated block-change flags. For small moves this is redundant with the per-block `markBlockForUpdate` already done by `postUpdateBlock`; for large moves it can send whole chunk packets. Let `PlayerManager` batch the changes; if a manual flush is needed, send an S22 per chunk (`S22PacketMultiBlockChange`) with the changed positions.
- `finishMoving` is called from `TileMovingServer.updateEntity()` for **every** finished tile and processes **all** finished tiles from `MovingTileRegistry`. It is not idempotent: if `setBlock` fails (chunk unloaded, y out of range, another mod cancels), the tile remains valid and `finishMoving` runs again the next tick. Add a `finished`/`finishing` flag and a per-tile try/catch/log so one bad block cannot abort the batch.
- The player re-activation (`tile.activatingPlayer` → `Block.onBlockActivated`) can run with a player in another world/dimension, or after the player logged out; check `player.worldObj == tile.getWorldObj()` and distance, and catch exceptions.
- `vars.put("chunks", tiles)` (`MoveManager.java:419`) stores the tile list under the wrong key; the crash report will not show the chunk set.

### 4.4 `cachedTiles` is dimensionless, never pruned and keyed by mutable/weak-hash objects

**File:** `blocks/TileMovingClient.java:31`, `network/MessageClearTile.java:73`, `blocks/TileMovingClient.java:86-92`

- Keys are `ChunkCoordinates` (absolute coords). The cache is static and survives world/dimension changes. A mover at the same coordinates in another dimension can reuse a TE from the previous world. Add `world.provider.dimensionId`/`World` to the key (or clear on `WorldEvent.Unload`/`ClientDisconnectionFromServerEvent`).
- Entries are only removed on a successful lookup. Missed/aborted moves leave keys + weak references forever. Prune on a timer/`onChunkUnload`, or store the cache in the `WorldClient`/a `WeakHashMap<World, ...>`.
- `ChunkCoordinates` is mutable and its vanilla `hashCode` has precedence/shift bugs; Hodgepodge’s `MixinChunkCoordinates_BetterHash` improves it, but a `long` packed key is cheaper and avoids the class dependency.
- `MessageClearTile` lacks a dimension ID; the handler uses the current client world. For same-dimension moves this is fine, but it should still carry and validate the dimension for future cross-dimension/teleport logic.

### 4.5 `TileMovingClient.onDataPacket` robustness/perf

**File:** `blocks/TileMovingClient.java:60-128`

- `block = Block.getBlockById(tag.getInteger("Block"))` can return `null`; then `shouldRenderInPass`/`getRenderBoundingBox`/`renderTileEntityAt` NPE. Default to `Blocks.air` and mark the entry as errored.
- `BlockHelper.postUpdateBlock(worldObj, new BlockPos(this))` performs a full chunk light recalculation (`chunk.generateSkylightMap`/`relightBlock`/`world.func_147451_t`) for **every** moving block on the client. The mover is a TESR-only block (`BlockMoving.getRenderType() == -1`); the client only needs `world.markBlockForUpdate` (which Celeritas routes to a section rebuild). Remove the light work from the client path.
- The reused inner TE is left `isInvalid() == true` (it was invalidated by `MessageClearTile`). Some TESRs/mods skip invalid TEs. Call `tile.validate()` after repurposing (it only clears the flag) and reset `tileEntityInvalid` state.
- `rawTile` reuse requires the S35 to arrive after `MessageClearTile`; same-channel ordering guarantees that, but a dropped/duplicated packet leaves the cache entry forever. The cache should be keyed by the source position **and** dimension, and validated against the expected block/meta.
- `checkClass` only checks the exact class; a subclass of a blacklisted class slips through. Use `Class.isAssignableFrom` (or cache a per-class result).
- `renderBlackList` is never populated; either remove it or populate it from config/`IDescriptionProxy` failures.

### 4.6 `MoveManager` mutates the world with neighbour updates during the “no updates” phase

**File:** `movers/MoveManager.java:277-343`

`dstWorld.setBlock(..., BlockMoving.instance, 0, 1)` and `srcWorld.setBlock(..., BlockMoving.instance, 0, 1)` use flag `1` (neighbour notify) and not flag `2` (client mark). So while the source blocks are still present and the destination tiles are being created, every neighbour of every moved block receives `onNeighborBlockChange` with `BlockMoving` as the new block. Torches/redstone/machines can react (pop off, schedule ticks, emit packets) mid-move. The comment “from now on — NO BLOCK UPDATES” is inaccurate.

**Fix:** use flag `0` for the crate placement, send `S23PacketBlockChange`/S22 explicitly (already done), and run `postUpdateBlock`/neighbour notifications only after the destination blocks are fully in place (as `finishMoving` does). Consider a `World` flag/`BlockSnapshot` capture to suppress Forge block events during the atomic swap.

### 4.7 Teleport destination watchers and inventories

`MoveManager` only collects `srcWatchingPlayers` from source chunks (`MoveManager.java:118-124, 222-223`). For a teleport, destination blocks are destroyed by `BlockHelper.breakBlockWithDrop` (which may drop TE inventories), but players watching the **destination** are not in the list, so their open container is not closed. They can keep a GUI open on a destroyed TE and dupe/void items on click. Collect watchers for both source and destination chunks (per world) and close containers for both.

### 4.8 `EntityMovingEventHandler.moveEntity` is a divergent copy of vanilla `Entity.moveEntity`

**File:** `EntityMovingEventHandler.java:35-246`

The copy is based on 1.7.10 `Entity.moveEntity` but omits/alters (verified against the decompiled vanilla method):
- `isInWeb` slowdown and motion damping;
- `isCollidedHorizontally`/`isCollidedVertically`/`isCollided` updates;
- `onGround` assignment and `updateFallState(y, onGround)`;
- motion zeroing on collision, and the vanilla sneak/step bookkeeping in the same order.

The result is “mostly works” movement that can leave entities floating, not registering ground contact, not triggering pressure plates/sounds, and diverging from vanilla on stairs/slabs. Prefer `entity.moveEntity(dx, dy, dz)` (the moving block’s `addCollisionBoxesToList` already supplies the transformed collision boxes) and only intercept where necessary (e.g. sneaking players). If the copy must stay, port it 1:1 from the exact Forge/MCP version and add a comment with the source.

Also:
- `WeakHashMap<Entity, Vec3>` values are always `null`; use `Set<Entity>` (`Collections.newSetFromMap(new WeakHashMap<>())`).
- The map is cleared on both `START` and `END` phases; only `END` is needed.
- Moving entities from a `TickEvent.ServerTickEvent` handler is outside `World.updateEntities`; `EntityPlayerMP` movement packets processed later in the tick can overwrite the change. Consider moving entities in the `WorldTickEvent.END` for their world or using `EntityPlayerMP.setPositionAndUpdate` (with the usual teleport caveats).

### 4.9 `TileMovingClient.getRenderBoundingBox` and `maxTime == 0`

`offset(boolean)` (`TileMovingBase.java:218-222`) divides by `maxTime`. Before the first S35, `maxTime == 0` and `offset(true)` returns `Infinity`/`NaN`, which propagates through `AxisAlignedBB.getOffsetBoundingBox` and frustum culling. `shouldRenderInPass` already returns false for `maxTime == 0`, but culling/`getRenderBoundingBox` can be called first (especially under Angelica, which uses the bounding box to classify/cull TEs). Guard `maxTime <= 0` in `offset`, `getMovVec`, `progress`, and `getRenderBoundingBox` (return the un-offset crate box).

For teleport (`dir >= 6`) the renderer **scales** the block, but `getRenderBoundingBox` only translates by `h*dir` (zero for UNKNOWN/dir 6/7). Culling can pop the growing/shrinking block. Build a scaled AABB for `dir >= 6` (center ± 0.5*scale, or the interpolated bounds).

### 4.10 `BlockMoving` collision/particle/light details

- `addCollisionBoxesToList` (`BlockMoving.java:52-66`): for `dir == 6/7` (`UNKNOWN`), `ForgeDirection.getOrientation(dir)` returns `UNKNOWN` with zero offsets; `world.getTileEntity(x, y, z)` is the same tile, so its collision boxes are added twice. Return early for `UNKNOWN`.
- `randomDisplayTick` (`BlockMoving.java:153-166`) sets `fakeWorld.offset`/`dir` on a shared per-world fake world, then only resets `offset`; `dir` leaks to the next caller. Use `try/finally` and reset both. Also guard `mover.block == null`.
- `getLightOpacity` returns `TileMovingBase.lightOpacity` whose field default is 255; a freshly created client mover before the S35 is fully opaque for lighting purposes. Default to 0 for `isAir`/before data, or only report the target block’s opacity once known.
- `BlockMoving.instance` static is set in the constructor; fine, but `BlockMoving.crate`/`crate_error` are static icons used by the TESR; resource reloads update the icons but cached vertex states keep old UVs (see §7.3).

### 4.11 `ItemWrench.onItemUseFirst` runs world-mutating logic on the client

**File:** `items/ItemWrench.java:88-189`

```java
if (world.isRemote) {
    FunkyLocomotion.proxy.sendUsePacket(...);
}
// ... continues on both sides
...
block.onBlockActivated(world, dx, dy, dz, player, face, hitX, hitY, hitZ);
```

The client sends the C08 packet **and then also executes** the 5×5 mass-wrench loops, calling `onBlockActivated` on the client for every matching frame/pusher. Many `onBlockActivated` implementations mutate the world client-side, causing desync/ghost blocks. Return after sending the packet (or wrap all world mutations in `if (!world.isRemote)`).

Additional issues:
- `isInaccessible` passes the **original** `block` to `block.canCollideCheck(world.getBlockMetadata(dx2,...), false)` and `block.collisionRayTrace(...)` (`ItemWrench.java:122-137, 191-209`) instead of the candidate `blockingBlock`. Wrong meta/block ⇒ wrong “is the face accessible” result. The last two `collisionRayTrace` vectors use `0.1/0.9` while the first uses `-0.1/1.1` (inconsistent; likely a typo).
- `onItemUseFirst` returns `true` unconditionally after the loops, even if nothing was wrenchable; that is intentional for a wrench but should be documented (it suppresses normal item use for all blocks).
- `leftClick` cancels every left-click while holding the wrench (including when the target is not a mover); intentional, but add a config/permission check if claim mods are supported.

### 4.12 `BlockStickyFrame.onBlockActivated` also mutates on the client

**File:** `blocks/BlockStickyFrame.java:59-74`

```java
int i = ...;
world.setBlock(x, y, z, block, meta, 2);
return true;
```

No `!world.isRemote` guard. The server sends the real change; the client also changes its local world. It usually converges, but it can flicker/desync and it fires client-side `World.setBlock` events. Return `true` on the client without mutating; only the server should change the block. Also `if (i > 63 || i < 0) i = 0;` is dead code (XOR of a masked value cannot exceed 63) and silently loses the meta if it ever triggered.

### 4.13 `BlockSlider.onBlockActivated` compares a face to a metadata value

**File:** `blocks/BlockSlider.java:77-103`

```java
if (side == meta) side = Facing.oppositeSide[side];
world.setBlockMetadataWithNotify(x, y, z, (meta < 6 ? 0 : 6) + side, 3);
```

`side` is 0..5, `meta` is 0..11; for puller sliders (`meta >= 6`) the comparison is never true, so clicking the front face does not flip the facing (the setter writes the same face). Compare `side == (meta % 6)`.

`TileSlider.onDataPacket` (`blocks/TileSlider.java:67-75`) reads `"dir"` from the packet but `readFromNBT` uses `"SlideDirection"`; keep both for compatibility, but document/version the keys.

### 4.14 `TileTeleport` cache/partner lookup can NPE and leaks

**File:** `blocks/TileTeleport.java:65-92`

```java
if (world == null || world.isRemote) iterator.remove();

if (DimensionManager.getWorld(world.provider.dimensionId) != world) { ... }
if (!world.blockExists(...)) continue;
```

If `world == null`, the code removes the entry but then dereferences `world.provider` ⇒ NPE. `validate()` can add a tile with a null `worldObj` (`worldObj == null || !worldObj.isRemote`). Add `continue` after removals and only add to the cache when `worldObj != null && !worldObj.isRemote`.

`cache` only removes tiles from the per-ID `WeakSet`; the outer `TLongObjectHashMap` entry is never removed when the set becomes empty. Prune empty sets in `unCache`/`invalidate`.

### 4.15 `MoverEventHandler` drops movers registered during a move and processes on the wrong phase

**File:** `movers/MoverEventHandler.java:28-43`

```java
if (skip && event.phase != TickEvent.Phase.END || event.side != Side.SERVER || movers.isEmpty()) return;
IMover[] iMovers = movers.toArray(new IMover[0]);
movers.clear();
for (IMover mover : iMovers) { ... }
movers.clear();
skip = true;
```

- The condition runs on both `START` and `END` when `skip == false` (the intended “post world tick” name is misleading).
- The second `movers.clear()` drops any mover registered by a `startMoving()` callback (e.g. a neighbour pusher reacting to the block updates). Those movers wait for another neighbour update.
- One mover throwing aborts the rest (no per-mover catch).
- `skip` is global across all server worlds; a mover registered during dimension A’s tick can be processed on dimension B’s tick (it uses its own world, so correctness is okay, but the “skip” optimisation is fragile).

**Fix:** use a local snapshot, leave newly added movers for the next tick (`skip = false`), filter `event.phase == END`, and try/catch per mover. Replace `skip` with `movers.isEmpty()` (a `WeakHashMap` size check is cheap) or an `AtomicBoolean`/dirty flag.

### 4.16 `MoveManager` static crash context

`MoveManager.vars` (`MoveManager.java:63`) is a single static map shared by `startMoving` (synchronized) and `finishMoving` (not synchronized). It exists only to build a nice crash report. Refactor to a per-call `MoveContext` passed to the execute phase (and to `finishMoving`, which can build its own context), or use a `ThreadLocal<Deque<MoveContext>>`. This also removes the `synchronized static` bottleneck that serialises moves across dimensions.

### 4.17 Hard CoFH energy dependency is undeclared

`TilePusher` (`blocks/TilePusher.java:23-30, 200-217`) and `TileBooster` use `cofh.api.energy.EnergyStorage` and implement `IEnergyReceiver`. These are **classes/interfaces from CoFHCore**, not in the published jar (the bundled `cofh/api` was deleted in `427e3ff`). A standalone install without CoFHCore will fail to load `TilePusher` when a pusher is placed/loaded (`NoClassDefFoundError`).

**Fix (choose one):**
1. Add `required-after:CoFHCore` to `@Mod.dependencies` and `mcmod.info` (simplest; GTNH has CoFHCore).
2. Re-introduce a small `cofh.api.energy` API shim (license permitting) or shade only the API classes.
3. Introduce an internal `IEnergyStorage` abstraction with a CoFH adapter loaded reflectively, so the mod works without CoFH (RF-less mode: configurable free/alternate energy).

Also mention the energy capacity overflow: `maxTiles * powerPerTile` with unbounded config values overflows `int`; clamp.

---

## 5. Medium-severity findings (P2)

### 5.1 `TilePusher.readFromNBT` key case mismatch

`blocks/TilePusher.java:40-52`: writes `"powered"`, reads `"Powered"`. Power state is always lost on world reload/chunk reload. Fix the key (and support both for one release).

### 5.2 `handleIMC` is not registered

`FunkyLocomotion.java:146-153` has no `@EventHandler` annotation, so the IMC blacklist handler never runs. Add `@EventHandler` and implement the TODO: accept a block registry name / ore dictionary name / mod ID, store it in a `MoveBlacklist` registry checked by `BlockHelper.canMoveBlock`, and log unknown entries. This is the natural place to also accept `IMoveCheck`/`IMovePermissions` registrations.

### 5.3 `BlockHelper` direct-write correctness

- `silentSetBlock` (`helper/BlockHelper.java:20-56`) indexes `chunk.getBlockStorageArray()[y >> 4]` without checking `y` (0..255); a malformed `BlockPos`/custom API caller can throw AIOOBE. Clamp/validate.
- `silentSetBlock` doesn’t call `Block.onBlockAdded`; `DefaultMoveFactory.recreateBlock`/`finishMoving` never invoke it. Blocks that initialise state there (redstone, rails, mod machines) may misbehave after a move. Call `block.onBlockAdded(world, x, y, z)` after the silent write (and consider `onBlockPlacedBy` if a player context is available) or use `World.setBlock` with flags that suppress neighbour updates but still run `onBlockAdded`.
- `postUpdateBlock` passes `Blocks.air` as the “old block” to `notifyBlockChange`/`notifyBlockOfNeighborChange`; capture and pass the actual old block so mods comparing neighbour identities behave.
- `postUpdateBlock` calls `world.func_147451_t` (light) and `markBlockForUpdate`; on the client (§4.5) this is wasteful.

### 5.4 `BlockHelper.breakBlockWithDrop` semantics

`helper/BlockHelper.java:138-148`:

```java
world.playAuxSFX(2001, ...);                 // one packet per block
block.dropBlockAsItem(world, ..., meta, 0);  // no tool/fortune/silk semantics, no XP
world.setBlock(pos, Blocks.air, 0, 2);       // flag 2: client update, NO neighbour notify
```

- No `onBlockDestroyedByPlayer`/`harvestBlock` ⇒ no stats/XP/tool checks. Frames can “mine” obsidian with no pickaxe.
- No neighbour notification ⇒ torches/redstone attached to the destroyed obstacle stay floating until another update. Use flag `3` or call `world.notifyBlockChange` after.
- `playAuxSFX` + `dropBlockAsItem` per obstacle can be a lot of packets/entities; cap/batch.
- `Block.getDrops` may return nothing for a block that requires a tool; `dropBlockAsItem` respects `getDrops` but not `canHarvestBlock`; decide whether frames should ignore tool requirements (config).

### 5.5 `TilePusher`/`TileSlider`/`TileTeleport` duplicate energy/booster/move code

`TilePusher.startMoving` (`blocks/TilePusher.java:136-177`) and `TileTeleport.startMoving` (`blocks/TileTeleport.java:102-160`) are near-identical (~100 lines each). `TileSlider` inherits the pusher version. Extract an `AbstractMoveSource`/`MoveSource` with hooks:

- `getMoveDirection()`, `getDestinationLinks()`, `canUseEnergy()`, `drainEnergy()`, `getMoveTime()`, `getActivatingPlayer()`.
- One energy/booster loop, one `MoveManager.startMoving` call.
- Add unit tests for push/pull/slider/teleport energy math.

### 5.6 `TileTeleport` partner/ID design

- IDs are 20-bit random (`ItemBlockTeleporter.assignRandomID`), with no uniqueness check and a static `Random`. Collisions are possible (birthday problem at ~1k pairs). Use a persistent counter/UUID, or verify uniqueness against the server cache on assignment.
- A world can contain more than two teleporters with the same ID; `getTileTeleport` picks the first other one. Add a pairing UI/tool (wrench eye shows the partner; sneak-wrench cycles), or enforce a pair by consuming/assigning on placement.
- `TeleportID = 0` is “unlinked”; `assignRandomID` can produce 0 (`Math.abs(nextInt()) & 0xFFFFF`), creating an unlinked item. Use `1 + rand.nextInt(0xFFFFF)`.
- The item’s lore word phrase (`WordDictionary`) is non-deterministic because `words` comes from a `HashSet` over `StringTranslate` values; the same ID can display differently after a restart/language change. Use a fixed, ordered word list (and keep the ID as the source of truth).

### 5.7 `FactoryRegistry`/`DescriptorRegistry`/`ProxyRegistry` lookup semantics

- `FactoryRegistry.getFactory(Block)` ignores `moveFactoryMapInheritanceClass` for blocks (only TEs) and `moveFactoryMapBlockClass` is exact-class only. A block extending a registered block class silently falls back to `DefaultMoveFactory` (data loss for its TE). Add a cached class-hierarchy lookup (most-specific first, deterministic order).
- `moveFactoryMapInheritanceClass` is a `HashMap`; when two entries match (e.g. `TileEntity` and a subclass), the winner is unspecified. Use an ordered list/priority or a `ClassValue` cache.
- `DescriptorRegistry.getProxyList()` returns the mutable list; `MoveManager` iterates it during a move. Return an immutable/copy-on-write list.
- `DescriptorRegistry.register`’s `priority` flag is inverted (see §4.1).
- `ProxyRegistry.register` uses `assert` for null/interface checks (disabled at runtime) and `getInterface` only walks direct interfaces, not superclasses. Walk `getSuperclass()` too, cache results in a `ClassValue`, and use explicit `IllegalArgumentException`s.
- `COFHStickiness.isStickySide_do` casts `world.getBlock` to `IBlockAppearance` without `instanceof`; the proxy is registered for `IBlockAppearance`, but a mod could hand the cache a class that no longer implements it. Add a guard and a null check for `getVisualBlock`.

### 5.8 `MoveCheckReflector` and the `_Immovable` contract

- `MoveCheckReflector._canMoveClass` looks up `getMethod("_Immovable")`; `BlockMoving` declares `_Immoveable` (`BlockMoving.java:46-48`) — a typo, so the block’s immovability marker is never found. It happens to be unbreakable, so `canMoveBlock` returns false earlier, but an API/custom path can miss it. Rename and keep a deprecated alias.
- The reflection cache is an unbounded `HashMap<Class, Boolean>`; use `ClassValue<Boolean>` (or a `WeakHashMap`) to avoid classloader leaks.
- On any reflection failure it returns `true` (“movable”); for a broken `_Immovable` method this is unsafe. Consider a configurable fail-safe (`false` for blacklisted mod namespaces, `true` otherwise) and log once.
- `IMoveChecker` is implemented only by `MoveCheckReflector`; there is no registry. Add `MoveCheckRegistry.register(IMoveChecker)` and call it from `BlockHelper.canMoveBlock`; this is the natural API for claims, GT machines, AE2, etc.
- `IMovePermissions` is completely unused. Wire it into `TilePusher`/`TileTeleport` using the activating player (or the pusher’s owner) so server admins/claim mods can prevent moves.

### 5.9 Fake world fidelity

`FakeWorldClient` (`fakes/FakeWorldClient.java`) returns air for all non-mover positions:

- `getBlock`/`getBlockMetadata`/`isAirBlock`/`isSideSolid`/`getTileEntity` should delegate to the real world when there is no `TileMovingClient` at the position, overlaying mover positions. Without this, fences/glass/stairs/redstone next to a moving block render disconnected, and block renderers that inspect neighbours (e.g. `RenderBlocks` face culling) can cull the wrong faces.
- `isValid` requires `DimensionManager.isDimensionRegistered`; custom client dimensions can be registered differently. Relax to `world != null && world.provider != null` (and keep the dimension check only if it is known to be needed).
- Construction is heavy (`WorldClient` + `NetHandlerPlayClient` + `NetworkManager` + `WorldEvent.Load`). Consider a lighter `World`/`IBlockAccess` wrapper that does not extend `WorldClient` (most TESRs only need `IBlockAccess`/`World`), or at minimum make construction lazy/synchronized and reuse one per real world.
- `FakeWorldClient.register` registers `FakeWorldManager` on `MinecraftForge.EVENT_BUS`; `cache.remove(event.world)` on unload, and `cache.clear()` if the server is stopping. Add a client-disconnect clear too.
- `spawnParticle` uses the mutable `offset`/`dir` fields; `BlockMoving.randomDisplayTick` leaks `dir` (§4.10). Make the particle offset explicit parameters or reset in `finally`.

### 5.10 `ChunkRerenderer` details (beyond the P0)

- `markBlock` is called from `TileMovingClient.onDataPacket` (client thread). `reRenderChunks` runs on both `RenderTickEvent` phases (no phase filter) and on the main menu (`renderGlobal.worldRenderers` can be null) ⇒ NPE/AIOOBE.
- No bounds check for `worldRenderers[iterator.next()]`; the set is not cleared on world load, so indices from a larger previous grid can exceed the new array.
- `MathHelper.bucketInt(x,16) % renderChunksWide` divides by zero if `loadRenderers` hasn’t run.
- `worldRenderer.updateRenderer(renderViewEntity)` with `renderViewEntity == null` (main menu) can NPE.
- `redrawChunksInstantly = true` (default) rebuilds all marked chunks synchronously during the render tick; a large frame causes a frame hitch. Default to `false` (queue) and cap rebuilds per tick, or drop the direct path entirely under Angelica.
- `worldRenderersToUpdate.removeAll(renderersToUpdate)` is O(n·m); use a `LinkedHashSet`/identity set.

**Recommended design:** a `ClientChunkUpdateQueue` that stores `(World, BlockPos)` or `long` packed positions, deduplicates, and on the render tick:
1. calls `world.markBlockForUpdate(x, y, z)` (vanilla + Celeritas both handle it);
2. if a vanilla `RenderGlobal` is detected (no Celeritas), resolves the `WorldRenderer` with bounds checks and a sanity check that the array length matches the grid dimensions;
3. never calls `updateRenderer` directly when a custom terrain renderer is active;
4. clears on `WorldEvent.Load`/`Unload` and client disconnect.

### 5.11 `RenderItemWrench` state/input issues

- `Mouse.getEventY()` returns the last mouse *event* Y (often stale/0), while `Mouse.getX()` returns the current position. Use `Mouse.getY()`/`getEventX` consistently and convert with the current `ScaledResolution`.
- `BufferUtils.createFloatBuffer(16)` per frame → GC churn. Cache a `ThreadLocal<FloatBuffer>` (or a single buffer on the render thread).
- GL state leaks: `GL_BLEND` is enabled and never disabled; the blend func is changed to `(0,0,0,0)`/`(770,771,1,0)` and not restored; `GL_CULL_FACE` is force-enabled at the end. Hodgepodge’s `fixCreativeTabAlphaTest`/`fixPotionEffectAlphaTest` fix *other* mods’ leaks, but FL should restore its own. Use `GL11.glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT)`/`glPopAttrib` or GTNHLib’s `GlStateManager`.
- `FunkyLocomotion.wrench.getRenderPasses(k)` draws the static eye icon under the dynamic base/pupil; verify this is intended (it may show a static pupil behind the moving one).
- `handleRenderType` only handles `INVENTORY`; the held-item eye is the static icon. Fine, but document it.

### 5.12 `ParticleObstruction` calls `tessellator.draw()` inside `renderParticle`

`particles/ParticleObstruction.java:32-45`:

```java
super.renderParticle(...);
tessellator.draw();          // flushes the entire particle batch!
...
tessellator.startDrawingQuads();
```

`EffectRenderer` batches all particles into one `Tessellator`; calling `draw()` from one particle flushes everyone else’s vertices mid-frame, then draws with depth test disabled, then restarts. This is fragile in vanilla and outright incompatible with Angelica’s particle batching/instancing (`EntityBatch`/`ParticleRenderer`), which assumes it controls the draw boundaries. Replace with a dedicated render layer/pass (two `EffectRenderer` passes, one depth-tested/one not) or a custom `EntityFX` that does not flush. Also `MessageObstruction` spawns 10 particles per obstructed position; `checkPositions` can call it for many positions → particle spam. Deduplicate/limit.

### 5.13 `Recipes` correctness/performance

- `Recipes.addRecipes` logs every ore dictionary name starting with `"ingot"` (`Recipes.java:28-30`); debug spam on every startup. Remove.
- `getOreWithVanillaFallback(Blocks.piston, "thermalexpansion:machineFrame")` (`Recipes.java:34`) passes a **registry name** as an ore dictionary key. `OreDictionary.getOres("thermalexpansion:machineFrame")` is empty, so the fallback (a piston) is always used. Use the actual ore name (`"machineFrame"` if TE registers it, or `"gearEnderium"`/the intended TE frame ore); verify with `OreDictionary.getOres` in a GTNH instance.
- `addCustomRecipe` registers `RecipeSorter` with the same name `"funky:recipe"` for two different anonymous classes (`Recipes.java:199-202`); the second overwrites the first and the first recipe’s class may be unsorted. Use unique names per recipe class or a single named recipe class.
- The frame copy/reset recipes create 63 `ShapelessOreRecipe`s plus one recipe whose input is a list of 63 alternatives (`Recipes.java:163-188`). This bloats recipe/NEI loading and is awkward for recipe viewers. Consider one shapeless recipe with a single frame + a custom `IRecipe` for the reset, or an `IRecipe` that matches any frame metadata.
- `Recipes.addRecipes` runs in `FMLPostInitializationEvent`; if a mod registers ore dictionary entries later (some do in `FMLLoadCompleteEvent`), fallbacks may be wrong. Prefer `FMLLoadCompleteEvent` for ore-dependent recipes or resolve lazily.

### 5.14 `LogHelper`

`LogHelper.info/error` build a `StringBuilder` with the extra args and then log the **first arg only** (`logger.info(info)`/`logger.error(info)`), dropping the formatted message. Fix to `logger.info(temp.toString())`/`logger.error(temp.toString())`. Change the logger name to `funkylocomotion` (or `FunkyLocomotion`) for log filtering.

### 5.15 `FunkyLocomotion` startup guards

- `init` catches only `ClassNotFoundException` around `Class.forName("thaumcraft.api.nodes.INode")` (`FunkyLocomotion.java:125-131`); a `NoClassDefFoundError`/`LinkageError` from a half-present Thaumcraft install crashes startup. Use `Loader.isModLoaded("Thaumcraft")` + catch `Throwable`.
- `Class.forName("cofh.api.block.IBlockAppearance")` likewise; use `Loader.isModLoaded("CoFHCore")`.
- `Block block = (Block) Block.blockRegistry.getObject("ForgeMultipart:block")` can return `null`; `FactoryRegistry.moveFactoryMapBlock.put(null, ...)` would make **all** null-block lookups use `FMPMover`; `FMPStickness.init(null)` NPEs. Guard `b != null && b != Blocks.air`.
- `TilePusher.maxTiles`/`powerPerTile` config values are not clamped; `EnergyStorage(maxTiles * powerPerTile)` can overflow/negative. Clamp to e.g. 1..4096 and 1..1_000_000, and guard the multiplication with `long`.
- `redrawChunksInstantly` default `true` is the laggy option; default `false` and/or remove it under Celeritas.
- `proxy.registerRendering()` is called on both sides; the server `Proxy` is a no-op. Fine.
- `@Mod.dependencies` / `mcmod.info`: add `after:ForgeMultipart;after:Thaumcraft;after:CoFHCore` (or `required-after:CoFHCore` while the energy API is hard).

### 5.16 `ItemHelper` class loading

`ItemHelper` uses `Class.forName(name)` (initialize = true) for every wrench interface during class initialization. That triggers static initializers of third-party API classes at a possibly bad time (before the owning mod is ready). Use `Class.forName(name, false, ItemHelper.class.getClassLoader())` and cache `isWrench` per `Item` class (`ClassValue<Boolean>`) because it is called on every block interaction.

### 5.17 `WordDictionary`

- Static initializer calls `Minecraft.getMinecraft().getResourceManager()`; add `@SideOnly(Side.CLIENT)` to the class and guard `Minecraft.getMinecraft() != null`.
- Reflection into `StringTranslate` (`field_74817_a`/`field_74816_c`) is brittle across Hodgepodge/Forge patches; catch `Throwable` and fall back to the 26 letters + a small static word list.
- The word list is a `HashSet` over all language values ⇒ non-deterministic order and mixed languages. Use an ordered, fixed list (or a stable sort of the current language’s ASCII words).

### 5.18 `BlockStickyFrame`/`ItemBlockFrame`/creative tab

- `BlockStickyFrame.getSubBlocks` only exposes meta 0; the other 15 metadata variants per block (64 combinations) are not in the creative tab/NEI. Feature: expose all variants or a config/creative-only “all frames” item.
- `ItemBlockFrame.getItemStackDisplayName` appends `" ()"` for index 16/32/48 with meta 0 (empty direction list); skip the parentheses when the list is empty.
- `CreativeTabFrames.getTabIconItem` returns `Item.getItemFromBlock(FunkyLocomotion.pusher)` which is null before `preInit`; NEI/creative tab construction after preInit, but a fallback (`frame[0]` or `Items.piston`) avoids an NPE if the tab is queried early.
- `BlockStickyFrame.onBlockActivated` client mutation (§4.12); `BlockFunc` `if (i > 63 || i < 0)` dead code.

---

## 6. Low-severity / cleanup (P3)

| Item | Location | Note |
|---|---|---|
| Unused/dead classes | `fakes/OffsetBlockAccess`, `rendering/FramesBlockAccess`, `debug/DebugEventHandler`, `factory/ChunkUnloadFactory`, `fmp/FramePart` | Remove or wire up. `DebugEventHandler` would spam reddust for every TE if ever registered. |
| Unused API | `IAdvStickyBlock` | `TilePusher.getBlockPosIterate` ignores it. Implement or remove. |
| Unused API | `IMovePermissions` | No permission checks anywhere; wire into pushers/teleporters. |
| Unused API | `IMoveChecker` | Only implemented by `MoveCheckReflector`; no registry. |
| Typos | `TileMovingBase.getTransformedColisions`, `BlockMoving._Immoveable`, `progress()` dead | Rename `getTransformedCollisions`; fix `_Immovable` (keep alias). |
| Dead code | `TileMovingBase.getCombinedCollisions`, `progress`, `TileMovingClient.renderBlackList` | Remove or use. |
| `EntityMovingEventHandler` value type | `WeakHashMap<Entity, Vec3>` with null values | Use a `Set`. |
| `TileMovingBase.updateEntity` | `time = time + 1 - 1;` | Dead code; remove. |
| `ClientTimer` | `event.renderTickTime % 1` | `renderTickTime` is already in [0,1); no-op. |
| `RenderBlockSlider` | `renderer.renderAllFaces = false` not restored | Save/restore. |
| `RenderBlockPusher/Slider` inventory | `glRotatef(90)` not undone | `glPushMatrix`/`glPopMatrix`. |
| `FLRenderHelper` | `Blocks.stone.setBlockBounds(0,0,0,1,1,1)`, `RenderBlocks.getInstance()` | Mutates a global block/singleton; use a private dummy or explicit render bounds. |
| `BlockMoving.randomDisplayTick` | `fakeWorld.dir` not reset | `try/finally`. |
| `BlockMoving.addCollisionBoxesToList` | UNKNOWN dir adds own boxes twice | Return early for `UNKNOWN`. |
| `BlockTeleport.getPickBlock` | `getDrops(...).get(0)` | Always one stack, but guard empty. |
| `BlockTeleport.removedByPlayer`/`harvestBlock` | Manual drops bypass `onBlockDestroyedByPlayer`/XP | Use `World.destroyBlock`/`harvestBlock` semantics or document. |
| `ItemBlockTeleporter` | static `Random`, 0 ID possible, no uniqueness | See §5.6. |
| `TileMovingServer` | `markTileEntityChunkModified` every tick | Throttle; only mark when NBT actually changes (time/maxTime) or on a save interval. |
| `TileMovingServer` | `getDescriptionPacket` null `desc` | Return an empty/invalid packet or handle; currently a mover with `desc == null` sends nothing and renders as `DNR`. |
| `MoveManager` | `makeString` for collections/unknown objects can throw | Wrap in try/catch so the crash report survives. |
| `MoveManager` | `vars` static | Per-call context. |
| `MoveManager` | `HashMap<BlockPos, ...>`/`HashSet<BlockPos>` | `BlockPos` is mutable and has a collision-prone hash; use immutable `long` keys/fastutil. |
| `BlockPos` | mutable public fields used as map keys | Make immutable (final + constructor) or provide `copy`. |
| `BlockPos.hashCode` | `256*x + 7936*z + y` | Many collisions (e.g. `x=0,z=1,y=0` vs `x=31,z=0,y=0`); use `(x*31+z)*31+y` or pack. |
| `TilePusher`/`TileBooster` defaults | 256/1000 vs config 1024/250 | Make the config defaults the source of truth; clamp. |
| `TileBooster` capacity | field initializer uses static config | Config reload doesn’t update existing tiles; use `getMaxEnergyStored` dynamically or document restart. |
| `ItemWrench` `isInaccessible` | wrong block/meta + `0.1/0.9` vs `-0.1/1.1` | Fix. |
| `BlockSlider` | `TileSlider.getSlideDir` deterministic only from coords; `slideDir` not reset on facing change | Reset when the facing changes; persist immediately. |
| `RenderItemWrench` | `Mouse.getEventY` | Use `Mouse.getY`. |
| `WordDictionary` | mixed-language `HashSet` | Fixed list. |
| `LogHelper` | logs `info` instead of `temp` | Fix. |
| `mcmod.info` | empty dependencies/logo | Fill in. |

---

## 7. Rendering & GL audit (vanilla, Angelica, Hodgepodge)

### 7.1 What Angelica changes that FL touches

| Angelica feature | FL interaction | Action |
|---|---|---|
| Celeritas terrain renderer; `RenderGlobal` `sortAndRender`/`clipRenderersByFrustum`/`markBlocksForUpdate` overwritten; `worldRenderers` reduced to a 1×1×1 dummy grid (all coordinates collapse to index 0) | `ChunkRerenderer` reads `renderGlobal.worldRenderers`/`worldRenderersToUpdate` and calls `updateRenderer` directly | **Do not touch `worldRenderers`.** Use `world.markBlockForUpdate`/`markBlockRangeForRenderUpdate`; gate any vanilla fallback on a sanity check. |
| Per-section chunk meshing on worker threads | `BlockSlider.renderSide` static; `FLRenderHelper` global block bounds; `RenderBlocks.getInstance`; any shared `RenderBlocks` | Remove global/static render state; use per-task `RenderBlocks` (Angelica does) and `ThreadLocal` state where unavoidable. |
| `TileEntityRenderBoundsRegistry` classifies TE bounds (STATIC/INFINITE/DYNAMIC), calls `getRenderBoundingBox` on workers for STATIC | `TileMovingClient.getRenderBoundingBox` constructs a fake world / calls inner TE on a worker | Register `TileMovingClient` as dynamic; make the box cheap/side-effect-free; no fake world. |
| TE bounds cached per class/instance unless DYNAMIC | Mover’s box changes every tick | Dynamic registration is mandatory for correct culling. |
| `OverridesShouldRenderInPass` marker injected by `TileEntityMarkerTransform` for TEs overriding `shouldRenderInPass` | `TileMovingClient` overrides it | Verify the marker is present at runtime; test pass 1 (water/glass). |
| `MixinTileEntityRendererDispatcher` makes `mapSpecialRenderers` a `ConcurrentHashMap`; TESR batching/deferred draws | `TileEntityRenderMoving` calls `getSpecialRenderer`/`func_147496_a` and mutates `Minecraft.theWorld` | Avoid relying on the map being a HashMap; guard `thePlayer`; no fake-world creation during render/world-change. |
| `MixinTessellator` + `ModelPartBatcher` + GLSM (GL 3.3 core) | `Tessellator.getVertexState`/`setVertexState` cache; `GL11.glGetFloat` in the wrench renderer; `glBegin`-style calls | `setVertexState` works but copies; GLSM emulates `glGetFloat`; still avoid per-frame buffer allocations and leaking GL state. Prefer `GTNHLib` `DirectTessellator`/`TesrMeshProvider` for cached geometry. |
| Shaders/Iris shadow pass; depth/alpha handling; particle/entity batching | `ParticleObstruction` calls `tessellator.draw()` inside a particle; `TileEntityRenderMoving` changes GL state | Do not own the draw boundary; use a separate render pass; restore GL state. |
| `ChunkRerenderer`/`WorldRenderer` interplay | See above | See §5.10. |

**Angelica’s README explicitly lists Forge Relocation and ProjectRed Frames as “currently non Thread Safe” incompatibilities.** FL is in the same architectural family (frames + direct chunk/TE writes + custom chunk re-render). The goal should be to remove the patterns that force that classification, not to add Angelica-specific hacks.

### 7.2 Threaded-meshing checklist

- [ ] No static mutable render state (`BlockSlider.renderSide`, `FLRenderHelper` globals).
- [ ] No mutation of vanilla shared objects during rendering (`Blocks.stone.setBlockBounds`).
- [ ] Per-thread `RenderBlocks`/`Tessellator` scratch state (Angelica provides per-task; FL must not share).
- [ ] `getIcon`/`shouldSideBeRendered`/`getRenderType`/`canRenderInPass` pure (no lazy mutation, no world writes). `canRenderInPass` may have side effects in other mods’ ISBRHs; call it once per pass and cache the result for the frame.
- [ ] `getRenderBoundingBox` no world/fake-world construction; cheap.
- [ ] `randomDisplayTick`/TESR only on the client/render thread; no worker access.
- [ ] All fake-world caches concurrent or render-thread confined.
- [ ] TESR does not swap global `Minecraft.theWorld`/`thePlayer.worldObj` if avoidable; if kept, guard nulls and defer to a single render-thread scope.

### 7.3 TESR cache invalidation

`TileMovingClient.cachedState[2]`/`skipPass[2]` are per-TE and never invalidated except on the TESR world change (`func_147496_a`). Cases that require invalidation:

- Resource pack reload (block icons/UVs change). Add an `IResourceManagerReloadListener` (or a `TextureStitchEvent.Post` handler) that clears all mover caches.
- Light/AO changes during the move (the cached vertices bake brightness). Short moves make this minor, but shaders/dynamic lights can make it visible. Invalidate when the block/meta changes (never) or on a light update at the destination (throttled).
- `TessellatorVertexState` is cached per TE; identical movers could share one state per (block, meta, pass, light) to reduce memory/`setVertexState` copies. `setVertexState` copies the raw int[] every frame; for a 1024-block frame this is non-trivial. A per-block display list/VBO or GTNHLib `DirectTessellator`/Angelica `TesrMeshProvider` cache would be better.

### 7.4 GL state hygiene

Audit all `GL11` calls in the TESR/item renderer for push/pop or explicit restore:
- `RenderItemWrench`: blend enable/function, cull face, alpha test, color mask, texture 2D, lighting.
- `TileEntityRenderMoving`: `RenderHelper.disableStandardItemLighting`/`enableStandardItemLighting`, `glShadeModel`, `glBlendFunc`, `glEnable(GL_BLEND)`, `glEnable(GL_CULL_FACE)`, matrix push/pop.
- `RenderBlockPusher/Slider`: matrix rotations (inventory), `uvRotate*`, `renderAllFaces`, override texture.

Under GLSM, most raw `GL11` calls are redirected and tracked; a leak still changes the tracked state for subsequent rendering, and Hodgepodge’s `fixGlStateBugs`/`fixCreativeTabAlphaTest` only patch specific vanilla leaks. Use `GlStateManager`/`glPushAttrib` where cheap.

---

## 8. Hodgepodge compatibility notes

Hodgepodge is mostly a set of fixes; FL is compatible with most of it, but several patches interact:

| Hodgepodge patch | Interaction with FL | Action |
|---|---|---|
| `TileEntityDescriptionBatcher` (S35 batching; `batchDescriptionPacketsMixins=true`, `batchDescriptionPacketsCode=false` by default) | FL manually calls `getDescriptionPacket` + `PlayerInstance.sendToAllPlayersWatchingChunk(packet)`, bypassing the batcher’s hooks (`EntityPlayerMP.func_147097_b`, `ForgeHooks.onBlockBreakEvent`, private `PlayerInstance.sendTileToAllPlayersWatchingChunk`). | Flush `desc` (clone) and implement FL-side batching (S22/S35 per chunk and per tick). Do not rely on Hodgepodge to batch FL’s packets. |
| `MixinTessellator` “preserve quad order” (`fixPreserveQuadOrder=true`) | FL caches `TessellatorVertexState` from `getVertexState`; Hodgepodge removes the distance sort. Opaque is fine; translucent cached states may blend in insertion order. | Do not cache pass-1 vertex states, or sort at render time; test water/glass/stained glass. |
| `PlayerManagerTransformer` “speedupPlayerManager” (rewrites a local `ArrayList` to `ObjectOpenHashSet` in `filterChunkLoadQueue`) | FL reads `PlayerInstance.playersWatchingChunk` (still a `List`) and `player.loadedChunks` (still a `List`); AT access is compatible. | Prefer public/API access or `ChunkWatchEvent`; avoid depending on the local type. |
| `MixinChunk` early TE coordinate check | FL’s `DefaultMoveFactory.loadTile` uses `chunk.addTileEntity` with correct coords; `TileMovingClient.onDataPacket` sets coords on a reused TE directly. | Validate reused TE coords and clamp; do not reuse a TE from another chunk/world. |
| `MixinS35PacketUpdateTileEntity_ByteArray` (single-key byte-array fast path) | FL’s S35 NBT has many keys (Block/Meta/Time/…) so the fast path is not used; `TileSlider`/`TileTeleport` have a single int key, also not a byte array. | No action; optionally pack `Collisions` as a byte array to benefit, but that is a wire-format change. |
| `fixGlStateBugs`, `fixCreativeTabAlphaTest`, `fixPotionEffectAlphaTest` | FL leaks blend/cull state in `RenderItemWrench`/TESR. | Fix FL’s own state leaks; do not depend on Hodgepodge. |
| `fixVanillaIOOBERenderDistance` | `ChunkRerenderer`’s manual index math can AIOOBE independently. | Fix §3.1/§5.10. |
| `optimizeWorldUpdateLight`/`fixGetBlockLightValue` | FL’s `postUpdateBlock` duplicates vanilla light logic with a wrong old opacity. | Fix §4.2; use vanilla light paths. |
| `removeInvalidChunkEntites` | Can mask FL’s stale-TE bug (§3.3). | Fix the key type; test with Hodgepodge both ways. |
| `preventChunkLoadingFromBlockUpdates` (default false; game-breaking config) | FL’s `notifyBlockChange`/`postUpdateBlock`/`silentSetBlock` can run in unloaded chunks. | Add `world.blockExists` guards; document that FL may not work with this config enabled. |
| `fixTooManyAllocationsChunkPositionIntPair`/`MixinChunkCoordinates_BetterHash` | FL uses `ChunkCoordinates` keys in `cachedTiles`. | Use packed `long` keys; don’t rely on the vanilla/hodgepodge hash. |
| `speedupNBTTagCompoundCopy` | FL clones `desc` for every S35 if we apply §3.4. | Fine; the copy is fast under Hodgepodge. |

**Hodgepodge’s `hodgepodge.cfg` defaults** relevant to FL: `batchDescriptionPacketsMixins=true`, `batchDescriptionPacketsCode=false`, `fixPreserveQuadOrder=true`, `fixGlStateBugs=true`, `fixCreativeTabAlphaTest=true`, `removeInvalidChunkEntites=true`, `earlyChunkTileCoordinateCheck=true`, `optimizeWorldUpdateLight=true`. Test with the GTNH default config (all on) and with code batching on.

---

## 9. Other mod compatibility

### 9.1 ForgeMultipart (FMP)

- `FMPMover`/`FMPStickness` are registered in `init` if FMP is loaded, but `FMPDescriber` is **never registered** (`DescriptorRegistry` only gets `DescribeVanilla`/`DescribeVanillaSign`). FMP tiles therefore fall through to `DescribeVanilla`, whose `getDescriptionPacket` handling for `TileMultipart` is not the custom byte format. `FMPDescriber.recreateTileEntity` also reads the parts and then **discards them**, returning `block.createTileEntity(...)`; even if registered, the client would render an empty multipart.
- `FMPDescriber` writes a version byte `2` then `t.writeDesc(s)`; the read path assumes the first byte is `nparts` and calls `MultiPartRegistry.readPart`/`part.readDesc`, but never creates a `TileMultipart` or adds the parts. Fix: read the multiplex desc using FMP’s `TileMultipart`/`MultipartHelper` API, create a `TileMultipart`, add the parts, and return it; register the describer.
- `FMPMover.loadTile` calls `MultipartHelper.sendDescPacket` during `finishMoving`; ensure the block is already set (it is) and the packet is sent after the client knows the block. FMP’s `onMoved` should be called after the parts are added.
- `FramePart` (an FMP part for frames) is never registered (`MultiPartRegistry.registerParts` missing) and returns `null` from `getBlock`/`getBounds`; remove it or implement/register it.
- `FMPStickness` compares `MicroMaterialRegistry.materialName(((Microblock) part).getMaterial())` to `keyOpen`; okay, but `partMap(side.ordinal())` only sees a part in that slot; a frame microblock in another slot is not sticky. Document/verify.
- `FunkyLocomotion.init` should null-check the FMP block (`Block.blockRegistry.getObject("ForgeMultipart:block")`) and use `Loader.isModLoaded("ForgeMultipart")`; a missing block should skip both registrations.

### 9.2 Thaumcraft

- `NodeMover` is registered only if `Class.forName("thaumcraft.api.nodes.INode")` succeeds and `Thaumcraft:blockAiry` exists; use `Loader.isModLoaded("Thaumcraft")` and catch `Throwable`.
- `NodeMover` degrades node modifiers based on Manhattan distance; it does not preserve node `vis`/aspects beyond the TE NBT (the TE NBT should include them). Test with TC4/TC4.2 (GTNH) nodes, including cross-dimension moves.
- `NodeMover` `OldDim`/`OldPos*` keys are written on save; if a node is moved multiple times, the fields are overwritten each time; fine.

### 9.3 CoFH / Thermal Expansion

- Energy: hard dependency (see §4.17). Thermal Expansion is `after:` but CoFHCore is not declared.
- `COFHStickiness` uses `IBlockAppearance` to make facades/covers sticky; test with TE strongboxes/caches, Thermal Dynamics covers, and `IBlockAppearance` blocks whose appearance is another `BlockStickyFrame`. `ProxyRegistry.getInterface` must walk superclasses for blocks that inherit `IBlockAppearance`.
- `COFHStickiness` casts without `instanceof`; add a guard.
- Recipe fallback `"thermalexpansion:machineFrame"` is not an ore name (§5.13).

### 9.4 GTNH Immersive Engineering / multiblocks

The GTNH IE port’s multiblock TEs implement `public static boolean _Immovable() { return true; }` (e.g. `TileEntityMultiblockPart`). `MoveCheckReflector` handles them, but:
- The typo `_Immoveable` in `BlockMoving` means the block-level marker is not found (the block is unbreakable, so this is masked).
- `MoveCheckReflector` returns `true` on reflection failure; a broken IE class means a multiblock could be moved and corrupted. Cache/log and consider a namespace blacklist.
- Add a config blacklist for GT/IE/AE2/IC2 TEs anyway; `_Immovable` is opt-in and many mods do not implement it.

### 9.5 GregTech / AE2 / IC2 / EnderIO / BuildCraft

These mods’ machines/cables/grids are generally not safe to serialize/relocate with a simple TE NBT copy. `_Immovable`/`IMoveCheck` coverage is incomplete. Recommendation:
- Ship a default blacklist by mod ID/class name for known-unsafe TEs (GregTech machines/hatches, AE2 grid TEs, IC2 reactors/cables, EnderIO conduits, BuildCraft pipes, Forestry machines, Railcraft anchors/tanks).
- Add `IMoveCheck` adapters for GT/AE2 where a soft dependency is acceptable.
- Provide a config `blacklist` (registry names, ore names, mod IDs) and an IMC key (`blacklist`) — implement the TODO in `handleIMC`.
- Add a “move only blocks with no TileEntity” safe mode for packs that cannot guarantee TE safety.

### 9.6 Claims / permissions

`IMovePermissions` is unused; `TilePusher`/`TileTeleport` do not know who activated them. Add `EntityPlayer`/owner capture on activation (the `TileMovingServer.cacheActivate` already stores a player for re-activation; the pusher can store the placer/owner in NBT) and a `MovePermissionRegistry` that claim mods (FTB Utilities/Teams, GriefPrevention forks, etc.) can hook. At minimum, prevent moving blocks in other players’ claims if the pusher has an owner.

### 9.7 NEI / recipe viewers

- The 64 frame variants are not in the creative tab; if you add them, NEI will show them.
- The 63 copy recipes + reset recipe should be reduced; NEI will list every variant.
- `Recipes.addRecipes` must not log ore names.
- `ItemBlockTeleporter` NBT-based output recipes: NEI shows the output without NBT; the teleporter ID is only visible in the tooltip. Consider a custom NEI handler or a craftable “frequency” item.

---

## 10. Performance & optimization opportunities

Ordered by expected impact for a GTNH/Angelica pack:

1. **Stop direct `WorldRenderer` rebuilds** (Angelica: eliminate the dummy-grid work; vanilla: queue instead of instant). Large frame starts currently rebuild chunks synchronously and again via Celeritas.
2. **Batch block/TE updates.** Replace per-block S23 + per-block S35 with:
   - `S22PacketMultiBlockChange` per chunk (one packet per affected chunk, up to 64/… blocks);
   - one S35 per TE, batched per tick/player (Hodgepodge-style), or a custom `MessageMoveSnapshot` per structure/chunk;
   - `world.markBlockForUpdate` only where necessary; let `PlayerManager` flush S22.
   Remove `FLNetwork.updateChunk`/`chunk.isModified` from `finishMoving` unless a full chunk resend is truly required.
3. **Remove the stone/air toggle and the client light recalculation** (§4.3, §4.5). This is two full light/remesh passes per block.
4. **Cache collision/entity queries per structure per tick.** `TileMovingBase.updateEntity` currently allocates `getTransformedColisions()` twice per tile and queries entities per collision box. Compute the structure’s AABB once, query entities once, and reuse the transformed boxes; cache the array until `time` changes.
5. **Cap the BFS** at `maxTiles` and use fastutil `LongOpenHashSet`/`Long2ObjectOpenHashMap` for positions; avoid `BlockPos` allocation/hash collisions. A 1024-block frame currently creates several thousand objects per move.
6. **Share cached vertex states** per (block, meta, pass, light) instead of per TE; avoid `setVertexState` copying each frame (use a VBO/display list or GTNHLib `DirectTessellator`/Angelica `TesrMeshProvider`). Cache `RenderBlocks` per world (already) and reuse a single `Tessellator` state.
7. **`TileMovingClient.onDataPacket`**: don’t call `postUpdateBlock` (light); just `markBlockForUpdate`. This is a big client-start spike for large frames.
8. **`TileMovingServer.markTileEntityChunkModified` every tick**: throttle to every N ticks/save tick; the NBT only changes with `time`.
9. **`ObstructionHelper`/particles**: one packet per obstructed position (not per player), 1–2 particles, dedupe positions; cap the number of obstruction checks.
10. **`Recipes`**: replace 63 recipes + the 63-alternative reset with a small custom `IRecipe`; remove the ore-name logging. NEI load time and memory.
11. **`WordDictionary`**: build once from a fixed list, not from all `StringTranslate` values; cache per language.
12. **`ItemHelper.isWrench`**: cache per `Item` class; avoid `Class.forName` initialization.
13. **`MoveCheckReflector`**: `ClassValue<Boolean>`; cache `_Immovable` method handles.
14. **`ProxyRegistry`/`FactoryRegistry`**: `ClassValue`/ordered lists; avoid per-call interface iteration.
15. **`FakeWorldClient`**: one per dimension (already), but avoid constructing `NetworkManager`/`NetHandlerPlayClient` if possible; a lightweight `World` wrapper would be cheaper. Cache the block collision box.
16. **`RenderItemWrench`**: cache the `FloatBuffer`; avoid `mouse`/matrix queries when the mouse hasn’t moved.
17. **`ChunkRerenderer`**: cap rebuilds per tick (e.g. 4–8), deduplicate, and default `redrawChunksInstantly=false`.

---

## 11. Refactor plan (suggested sequence)

The following is a low-risk, reviewable sequence. Each step should be a separate PR and keep the mod working on vanilla + Angelica + Hodgepodge.

### Phase 0 — Safety net (1–2 PRs)

1. Add a tiny pure-Java test source set (JUnit) for `BlockPos`, `MovePlan`/`BlockLink`, energy math, and NBT snapshot round-trips (no Minecraft runtime needed where possible).
2. Add a `docs/COMPATIBILITY.md` matrix and this audit to the repo.
3. Add a `runClientAngelica`/`runClientHodgepodge` dev setup (or at least document the GTNH versions) and a smoke-test checklist.

### Phase 1 — P0 correctness (small, surgical)

1. `BlockPos.advance(int)` → `ForgeDirection`; fix `TileTeleport.getDestinationPos`; tests.
2. `MoveManager.postUpdateBlock(srcWorld, ...)`; cross-world test.
3. `MessageClearTile` → `World.removeTileEntity`; dimension-aware cache keys.
4. `TileMovingBase` NBT keys/types; round-trip test.
5. `TileMovingServer.getDescriptionPacket` clone.
6. `BlockMoving.createTileEntity` → `world.isRemote`.
7. `WrenchFactory` generation hardening (skip `<clinit>`/static/private, class v52, COMPUTE_MAXS, interface check, fallback log).
8. `TileMovingClient.renderStatic` retry/skip logic; catch `Throwable`.
9. `BlockSlider` static removal (per-position/per-thread).
10. `TilePusher` BFS cap.

### Phase 2 — Angelica integration

1. Add `AngelicaCompat` (reflection) registering `TileMovingClient` as a dynamic-bounds TE; call early in client init.
2. Rewrite `ChunkRerenderer` as a `ClientChunkUpdateQueue`:
   - store `(World, BlockPos)`/`long` positions, dedupe, cap per tick;
   - call `world.markBlockForUpdate` + `renderGlobal.markBlockRangeForRenderUpdate`;
   - vanilla-only direct rebuild with bounds/sanity checks; no direct rebuild if `CeleritasWorldRenderer`/a custom renderer is detected;
   - clear on world load/unload/disconnect.
3. Make `FakeWorldClient` thread-safe and render-thread-only; no construction in `getRenderBoundingBox`; overlay non-mover positions by delegating to the real world (if feasible).
4. Remove remaining static render state (`FLRenderHelper`, `Blocks.stone` bounds, `RenderBlocks.getInstance`).
5. GL state push/pop in TESR/item renderer; test under GLSM/core profile and Iris shadows.

### Phase 3 — Hodgepodge / network

1. FL-side batching: S22 per chunk, S35 per player/tick (or a custom structure snapshot message); remove `updateChunk`.
2. Clone/flush S35 tags; add a `DescSnapshot` builder.
3. Test with Hodgepodge `fixPreserveQuadOrder` on/off, `batchDescriptionPacketsCode` on/off, `removeInvalidChunkEntites` on/off.
4. Dimension-aware/teleport-safe messages (`MessageClearTile` + `MessageObstruction`).

### Phase 4 — Structural refactors

1. `MoveContext` per call; delete `MoveManager.vars`/`synchronized static`.
2. `MovePlan`/`MoveExecutor`: separate “plan” (validate, collect, snapshot) from “apply” (destroy/create), with a per-tile try/catch and a `finished` flag.
3. `AbstractMoveSource` for pusher/slider/teleporter energy/booster/move logic.
4. `BlockSnapshot`/`TileSnapshot` versioned wire/NBT format (registry name + meta + NBT + description ID), with legacy readers.
5. `BlockPos` immutable + packed `long` keys; fastutil collections in `MoveManager`/`TilePusher`/`TileTeleport`.
6. `DescriptorRegistry`/`FactoryRegistry`/`ProxyRegistry` with ordered, class-hierarchy-aware, thread-safe lookups.
7. `IMoveCheck`/`IMovePermissions` registries; IMC blacklist; default mod blacklist.
8. `TileMovingBase` encapsulation (private fields, accessors), remove dead methods, rename typo.

### Phase 5 — Features/polish

See §12.

---

## 12. Feature roadmap

### Compatibility & safety

- Default blacklist for known-unsafe TEs (GregTech, AE2, IC2, EnderIO, BuildCraft, Forestry, Railcraft, ProjectRed) with per-entry config and IMC.
- `IMoveCheck`/`IMovePermissions` registries; claim mod integration; owner/permission checks on pushers/teleporters.
- `_Immovable` documentation + a proper `IMoveCheck` alternative; don’t rely on reflection alone.
- FMP fixes (describer registration, part reconstruction) and a test with a frame microblock, a cover, and a multipart machine.
- Thaumcraft node move tests (TC4/TC4.2), node degradation config.
- CoFH energy abstraction (optional dependency) or a documented required dependency.
- GTNH IE multiblock test (`_Immovable`), GT machine test (blacklist).

### Robustness / persistence

- Crash-safe move journal: persist the plan/snapshots before the animation; on server start, detect `TileMovingServer` leftovers and either resume or roll back. This is the single biggest “server crash mid-move” quality-of-life feature.
- Idempotent `finishMoving`, per-tile recovery, and a “stuck mover” admin command (`/fl fix`, `/fl list`).
- Versioned NBT/wire format with migrations.
- Teleporter ID registry with uniqueness, pairing UI, dimension-aware links, and a link tool.

### Gameplay / UX

- Frame variants in creative/NEI (64 combinations), copy/reset recipe improvements.
- Wrench modes: normal/eye/hammer, area size config, undo, claim permission.
- Slider direction GUI/reversal, speed tiers, entity conveyor mode.
- Booster tiers/GUI/redstone control.
- Pusher pulse/continuous modes, pulse duration, entity-only mode, sound/particles.
- Teleporter: frequency item, public/private links, energy cost scaling by distance/dimension, chunk-loading behavior.
- Camouflage/facades: integrate `IBlockAppearance`/COFH covers and FMP covers; frame “closed/open” textures already support this.
- Move preview: show the structure outline/obstruction when holding the eye wrench (particles already exist; fix batching and add a HUD preview).

### Rendering

- Smooth interpolation independent of tick rate; entity/platform movement with vanilla `moveEntity`.
- TESR batching/caching (Angelica `TesrMeshProvider`/GTNHLib `DirectTessellator`); shader-compatible lighting; no ghost blocks after moves.
- Dynamic lights/shadows compatibility (Iris shadow pass).
- Resource-reload invalidation.

### Developer/API

- Public API docs for `IStickyBlock`, `IAdvStickyBlock`, `ISlipperyBlock`, `IMoveCheck`, `IMoveFactory`, `IDescriptionProxy`, `IMovePermissions`.
- Keep `framesapi.*` compatibility aliases for one major version.
- A `FunkyLocomotionAPI` facade with registration helpers (factories, descriptors, blacklist, permissions).
- JUnit tests for pure logic + a scripted integration test world (GTNH dev).
- Config GUI (Forge `GuiConfig`) and localization for categories.

---

## 13. Test matrix

Run at least the following before releasing the refactor branch:

### Environment

- [ ] Dedicated server + vanilla client (no Angelica/Hodgepodge).
- [ ] Client with Angelica only (OpenGL path, render distance 2/8/16/32).
- [ ] Client with Hodgepodge only (default GTNH config; then `batchDescriptionPacketsCode=true`).
- [ ] Client with Angelica + Hodgepodge (the GTNH combination).
- [ ] Angelica + Iris shaders (Complementary + Euphoria) and shadow pass.
- [ ] Java 8 and Java 17/21 (JVM Downgrader/Jabel) if supported.
- [ ] Single-player, LAN, and dedicated server.

### Moves

- [ ] Push/pull 1, 64, 1024, and 1025 blocks (limit/energy behavior).
- [ ] Slider (all 6 facings, rotation via wrench, arrow rendering).
- [ ] Teleporter push (normal), pull (if reachable), same-dimension, cross-dimension, overlapping coordinates, obstructed destination, unlinked item.
- [ ] Overlapping source/destination (frame moves into its own space).
- [ ] Destination in an unloaded chunk; source chunk unload mid-move; world unload mid-move; server restart mid-move.
- [ ] Blacklisted/`_Immovable` blocks, GT machine, IE multiblock, FMP microblock, Thaumcraft node.
- [ ] Chest with items, sign, mob spawner, beacon, skull, flower pot, comparator, redstone, torch on the moved block, light-emitting block, transparent block (water/glass), TESR block, ISBRH block, fluid, falling block.
- [ ] Entity on a platform, player sneaking on a platform, mob in a moving wall, item entities, minecarts/boats.
- [ ] Frame with all 64 metadata combinations; wrench hammer area; dispenser placement/wrenching.
- [ ] Booster energy (multiple boosters, insufficient energy), pusher power pulse/continuous.

### Rendering/threading

- [ ] No ghost blocks at the source/destination.
- [ ] No missing faces on sliders under Angelica (two sliders in different sections meshing concurrently).
- [ ] No invisible moved blocks after a transient render failure/resource reload.
- [ ] TESRs (chest, sign, spawner, beacon) render during the move and after; no CME in `TileEntityRendererDispatcher`.
- [ ] No `FakeWorldClient` creation on worker threads (add an assertion/log in dev: `Thread.currentThread() == Minecraft.getMinecraft().mcThread`).
- [ ] No `ConcurrentModificationException` in S35/NBT serialization under network load.
- [ ] No `ArrayIndexOutOfBoundsException` from `ChunkRerenderer` on world change/render distance change.
- [ ] `/reload` (resource reload) while a mover is active; no stale textures.
- [ ] Shader shadow pass: moving blocks cast/receive shadows correctly.
- [ ] Particle obstruction: only when holding the eye wrench; no batch corruption; no spam.

### Performance

- [ ] Profile a 1024-block move: tick time, chunk rebuild time, network bytes, GC.
- [ ] Compare `redrawChunksInstantly` on/off and Angelica on/off.
- [ ] Check `TilePusher` BFS memory/time for a large sticky structure.

---

## 14. Appendix — verified upstream facts

These were checked against the decompiled 1.7.10 sources in `build/rfg/minecraft-src` and the current GTNH Angelica/Hodgepodge sources. They underpin the findings above.

### Vanilla/FML 1.7.10

- `FMLCommonHandler.getEffectiveSide()` is thread-name based: `"Server thread"` → `Side.SERVER`, everything else → `Side.CLIENT` (`FMLCommonHandler.java:152-161`).
- `Chunk.chunkTileEntityMap` is `Map<ChunkPosition, TileEntity>` (`Chunk.java:66`); `ChunkCoordinates` is a different class with an `instanceof ChunkCoordinates` `equals`.
- `World.setBlock` flags: bit 1 = `notifyBlockChange` (server neighbours), bit 2 = `markBlockForUpdate` (client). `markAndNotifyBlock` implements this (`World.java:542-570`).
- `Chunk.func_150807_a` calls `block1.breakBlock(...)` and `block.onBlockAdded(...)`, removes TEs via `shouldRefresh`, and updates light (`Chunk.java:600-720`). Direct `ExtendedBlockStorage` writes bypass all of it.
- `World.updateEntities` iterates `loadedTileEntityList` with an `Iterator` and removes invalid TEs via `iterator.remove()`; TEs added during the tick go to `addedTileEntityList` (`World.java:2148-2242`). This is why `finishMoving` does not CME, but also why a TE added during the tick is not updated until the next tick.
- `TileEntity.validate()` only clears `tileEntityInvalid`; `invalidate()` sets it (`TileEntity.java:230-241`). `Chunk.func_150812_a` calls `validate()` when a TE is added to a chunk (`Chunk.java:953-970`).
- `Tessellator.getVertexState` sorts quads, copies the sorted buffer back into `rawBuffer`, and returns a copy; `setVertexState` copies the state’s buffer into the shared `rawBuffer` every call (`Tessellator.java`).
- `PlayerManager.PlayerInstance.sendToAllPlayersWatchingChunk` skips players whose `loadedChunks` still contains the chunk (pending chunk send) — FL’s use of it is correct (`PlayerManager.java:540-555`). `filterChunkLoadQueue` maintains `loadedChunks`; `addPlayer`/`removePlayer` add/remove it.
- `PlayerManager.PlayerInstance.sendTileToAllPlayersWatchingChunk` is **private** (`PlayerManager.java:628`); Hodgepodge shadows it for batching.
- `ShapedOreRecipe` converts a `Block` ingredient to `new ItemStack(block, 1, WILDCARD_VALUE)`, so `Blocks.piston` as a fallback is valid; the `"thermalexpansion:machineFrame"` string is still not an ore name.
- `Entity.moveEntity` includes `ySize *= 0.4F`, web handling, collision flags, `onGround`, `updateFallState`, block collisions, etc. FL’s copy omits several side effects.

### Angelica (current source)

- `Angelica` README: “Known (temporary) Incompatibilities: Forge Relocation — Currently non Thread Safe; ProjectRed Frames — Currently non Thread Safe.” This is the same architectural pattern FL uses.
- `MixinRenderGlobal` (celeritas/terrain) overwrites `sortAndRender`, `clipRenderersByFrustum`, `markBlocksForUpdate`; `markBlocksForUpdate` → `CeleritasWorldRenderer.scheduleRebuildForBlockArea`. It also modifies `loadRenderers` to minimize `worldRenderers` (`renderChunksWide` → 0/1, `16` → 1), so the vanilla array is a 1×1×1 dummy grid (all coordinates collapse to index 0).
- `AngelicaChunkBuilderMeshingTask` creates a `RenderBlocks` per task and calls `renderBlockByRenderType` on worker threads; it calls `TileEntityRenderBoundsRegistry.classify(te)` and, for `STATIC`, `te.getRenderBoundingBox()` on the worker (§2.2).
- `TileEntityRenderBoundsRegistry.registerDynamicClass(String)` is called from `ClientProxy` for `AngelicaConfig.dynamicBoundsTileEntities`; `classify` checks `dynamicClassNames` before calling `getRenderBoundingBox`.
- `MixinTileEntityRendererDispatcher` (early/rendering) replaces `mapSpecialRenderers` with a `ConcurrentHashMap`; `MixinTileEntityRendererDispatcher` (angelica/tesr) wraps `TileEntitySpecialRenderer.renderTileEntityAt` in `TesrBatchRenderer.beginScope/endScope` and optional `TesrProviderDispatch`/entity batching.
- `TileEntityMarkerTransform` auto-adds `SendsDescriptionPacket`/`OverridesShouldRenderInPass`/`OverridesGetDistanceFrom` to TEs that declare the corresponding methods; `TileMovingClient.shouldRenderInPass` should be auto-marked.
- `MixinChunk` (celeritas) wraps `chunkTileEntityMap` in `ConcurrentTileEntityMap` (a `HashMap` subclass with read/write locks and a render-thread invalidation queue).
- `MixinTileEntity` overwrites `getRenderBoundingBox` (vanilla fallback) and caches `angelica$boundsClass`; `angelica$getCachedRenderBoundingBox` is used for STATIC TEs.
- GLSM emulates `glGetFloat(GL_MODELVIEW_MATRIX)` (`GLSMQueries.glGetFloat`), so `RenderItemWrench`’s matrix read works under Angelica (but is slower/less direct).
- `MixinTessellator` (angelica/rendering) adds per-vertex state for Celeritas meshing; `MixinTessellator_CoreProfile` intercepts `draw()` for streaming VBO/VAO. `TessellatorVertexState` exists but `setVertexState` is vanilla; the core-profile draw path reads the raw buffer.

### Hodgepodge (current source)

- `SpeedupsConfig`: `batchDescriptionPacketsMixins` default **true**, `batchDescriptionPacketsCode` default **false**, `directTileEntityArraySerialization` default **true**; `FixesConfig.fixPreserveQuadOrder` default **true**.
- `TileEntityDescriptionBatcher` queues `S35PacketUpdateTileEntity` per player and flushes at `ServerTickEvent.END`; it is hooked into `EntityPlayerMP.func_147097_b`, `ForgeHooks.onBlockBreakEvent`, and the private `PlayerInstance.sendTileToAllPlayersWatchingChunk` (not `sendToAllPlayersWatchingChunk`).
- `MixinTessellator` changes the `getVertexState` `PriorityQueue` comparator to preserve insertion order (`thenComparingInt`), so FL’s cached translucent vertex states are no longer distance-sorted.
- `PlayerManagerTransformer` rewrites only the local `ArrayList` in `filterChunkLoadQueue` to `ObjectOpenHashSet`; `EntityPlayerMP.loadedChunks` and `PlayerInstance.playersWatchingChunk` remain `List`s.
- `MixinChunk` (early) validates TE coordinates in `Chunk.func_150812_a`; `earlyChunkTileCoordinateCheck` default true.
- `MixinS35PacketUpdateTileEntity_ByteArray` only optimizes NBT with a single byte-array key; FL’s S35 has many keys.
- `fixPreserveQuadOrder`/`fixGlStateBugs`/`fixCreativeTabAlphaTest`/`removeInvalidChunkEntites`/`optimizeWorldUpdateLight` are active by default in GTNH; test both with and without.

---

## 15. Suggested first PRs (concrete, small, reviewable)

1. **`fix/teleporter-advance`** — `BlockPos.advance(int)` via `ForgeDirection`, `TileTeleport.getDestinationPos` uses the actual facing; JUnit test for all metadata 0..11.
2. **`fix/nbt-roundtrip`** — `TileMovingBase` `"Collisions"` type 9, `"TickTime"` key, null guards, light clamp; NBT round-trip test.
3. **`fix/message-clear-te`** — use `World.removeTileEntity`; add dimension to `cachedTiles`; clear on world unload.
4. **`fix/s35-clone`** — clone `desc` in `getDescriptionPacket`; add a stress test that sends S35s while ticking.
5. **`fix/tile-side`** — `world.isRemote` only; add `@SideOnly(CLIENT)` to `TileMovingClient`.
6. **`fix/wrench-factory`** — skip `<clinit>`/static/private, v52, `COMPUTE_MAXS`, interface check, fallback logging.
7. **`fix/block-slider-threading`** — remove the static `renderSide`; per-render context; test under Angelica.
8. **`fix/chunk-rerenderer`** — bounds/phase/world guards, default `redrawChunksInstantly=false`, queue cap.
9. **`feat/angelica-dynamic-bounds`** — reflective `registerDynamicClass`; no fake world in `getRenderBoundingBox`; thread-safe fake-world cache.
10. **`fix/cross-world-post-update`** — `srcWorld` in `MoveManager`; cross-dimension teleport test.
11. **`fix/pusher-limit`** — BFS cap + config clamp + energy overflow guard.
12. **`fix/logging-imc`** — `@EventHandler` on `handleIMC`, `LogHelper` message formatting, logger name.

---

*End of audit.*
