# Testing Funky Locomotion (P0 fixes)

This document covers the automated tests added with the first batch of P0 fixes,
what they do and do not prove, and the in-game checks that still need a real
Minecraft/Angelica/Hodgepodge client or dedicated server.

## Running the automated tests

```bash
./gradlew test
```

Useful variants:

```bash
# one class
./gradlew test --tests com.rwtema.funkylocomotion.blocks.TileMovingBaseNbtTest

# one method
./gradlew test --tests 'com.rwtema.funkylocomotion.blocks.TileMovingBaseNbtTest.roundTripsCollisionBoxesAndScheduledTicks'

# full build (compile + tests + jar)
./gradlew build
```

The HTML report is written to:

```
build/reports/tests/test/index.html
```

The test source set is a normal JUnit 4 source set. It compiles against the
RetroFuturagradle `patchedMc` output, so it can use Minecraft classes such as
`NBTTagCompound`, `AxisAlignedBB`, `ForgeDirection`, `S35PacketUpdateTileEntity`
and `ByteBuf` without launching a client or a server.

## What is covered automatically

| Test | Covers |
|---|---|
| `SmokeTest` | The JUnit/RFG test harness itself. |
| `BlockPosTest` | `BlockPos.advance(int)` maps `0..5` through `ForgeDirection`; `6`, `7` and out-of-range values are no-ops instead of indexing `Facing` arrays. |
| `TileTeleportTest` | `TileTeleport.getTeleportFacing(int)` for all metadata `0..11`; destination offsets for every source/destination metadata pair, including puller metadata. This is the regression test for the `ArrayIndexOutOfBoundsException`. |
| `TileMovingBaseNbtTest` | NBT round-trip of `block`/`desc`, time/maxTime/dir, collision boxes, scheduled tick time/priority, light values; missing keys; clamping; the old `"TimeTime"` typo is not treated as `"TickTime"`. |
| `TileMovingProgressTest` | Zero-duration movers do not produce NaN/Infinity in progress/offset/movement vectors. |
| `TileMovingServerDescriptionTest` | `getDescriptionPacket()` clones the live `desc` tag, does not mutate it, and two packets keep independent snapshots; a null description still returns no packet. |
| `MessageClearTileTest` | Packet coordinate round-trip, including negative and large values. |
| `MessageClearTileHandlerTest` | The client handler removes the source tile through `World.removeTileEntity` and drops it from `loadedTileEntityList`. |
| `BlockMovingSideTest` | Server-side truck creation returns `TileMovingServer`, independent of thread names. |
| `BlockSliderFacingTest` | Slider wrench clicks flip the current face and select other faces for both push and pull metadata. |
| `TilePusherNbtTest` | Pusher power state is restored from both the current `powered` key and the legacy `Powered` key. |
| `TileCacheKeyTest` | Client tile-cache keys include the dimension as well as coordinates. |
| `DescriptorRegistryTest` | Missing/unknown description ids fall back to the vanilla descriptor. |
| `MoveSnapshotTest` | Registry-name/meta round-trip, legacy numeric block ids, `BlockId` fallback, and unknown/missing blocks falling back to air. |
| `MoveBlacklistTest` | Mod and registry-name blacklist matching. |
| `MoveCheckRegistryTest` | Registered move checkers can veto a move; the default checker is restored between tests. |
| `FactoryRegistryTest` | A block subclass inherits its parent block class's move factory. |
| `ProxyRegistryTest` | Proxies are found directly and through an interface on a superclass; unrelated objects return null. |
| `ItemBlockTeleporterTest` | Generated teleporter IDs are never zero and stay in the 20-bit range. |
| `ItemHelperTest` | Null and ordinary items are not wrenches (no eager optional API initialization). |
| `MovePermissionRegistryTest` | Registered `IMovePermissions` can veto a move; no permissions means allowed. |
| `MoveSourceHelperTest` | Multi-storage energy payment and clamped booster move times. |
| `TeleportIdDataTest` | Teleporter pairs retain one id, released ids stay reserved across NBT reloads, and zero requests always allocate a fresh id. |
| `WrenchFactoryTest` | Only public abstract interface methods are copied; `<init>`/`<clinit>`, default, static, private, protected, native, synthetic and bridge methods are rejected. |
| `BlockSliderRenderSideTest` | Slider side visibility for unset/valid/unknown/invalid render sides, and that the render side is isolated per thread. |
| `MoverRenderResultTest` | A not-ready block render is retried next frame; a render exception disables the pass; a successful empty render is skipped; a successful render with geometry is cached. |
| `MoveCheckReflectorTest` | Static `_Immovable()` markers prevent movement; classes without the marker remain movable. |
| `AngelicaCompatTest` | Angelica bounds registration fails silently when Angelica is absent. |
| `TilePusherLimitsTest` | Config clamping, zero-energy/free-move mode, energy capacity/cost overflow protection, and the inclusive BFS block limit. |

### What the headless tests cannot prove

The unit tests deliberately do not bootstrap Forge, a `WorldServer`/`WorldClient`,
chunks, the block/tile registries, the network pipeline, or the OpenGL renderer.
They therefore cannot catch:

- real `World`/`Chunk` lifecycle problems (unloaded chunks, world changes,
  save/reload, dimension travel);
- actual S35 serialization on the Netty thread under load;
- Angelica worker-thread meshing and Celeritas chunk rebuilding;
- Hodgepodge's S35 batching, GL-state fixes, or `fixPreserveQuadOrder`;
- TESR behaviour, texture reloads, shaders, or GL state leakage;
- packet ordering/loss and client/server desync.

Those require the in-game checklist below.

## Headless dedicated-server harness

The JUnit tests stop at Minecraft class boundaries. The repository also has a
test-only dedicated-server harness that boots a real Forge server, a `WorldServer`,
chunks, real tile entities and the move manager's tick lifecycle:

```bash
./gradlew runServer -PflHarness=true
```

The first run creates `run/server/eula.txt` and a flat-world
`run/server/server.properties` automatically. The harness source set lives in
`src/harness/java` and is **not** part of the mod jar; it is only added to the
server classpath when `-PflHarness=true` is set.

What it verifies:

- FML dedicated-server startup with the normal dev runtime (CoFH, FMP,
  CodeChickenCore) and without client-only classes on the server;
- `BlockMoving.createTileEntity` creates a `TileMovingServer` in a real
  `WorldServer`;
- a pusher performs a real 3-block frame move over 20 ticks, including
  `MoveManager` finish/cleanup and block placement;
- a second pusher performs the same move with `energyPerBlock=0`, an empty
  zero-capacity energy buffer, and no booster/RF input;
- a **puller teleporter** with metadata `6` performs a real move through the
  teleporter cache and `MoveManager` (the old `advance(meta ^ 1)` path would
  throw `ArrayIndexOutOfBoundsException`);
- a structure larger than `TilePusher.maxTiles` is rejected by the real BFS;
- the server log is free of `NoClassDefFoundError`, `ClassCastException`,
  `ConcurrentModificationException` and `ArrayIndexOutOfBoundsException` during
  these operations.

A pass prints `[FL-HARNESS] PASS` and shuts the server down. A failure prints
`[FL-HARNESS] FAIL: ...` and exits non-zero. The harness files are prepared only
when `-PflHarness=true` is present, so normal `runServer` runs are unaffected.

The harness does **not** verify client rendering, Angelica worker-thread
meshing, Hodgepodge packet batching, shaders, or actual S35 delivery. Those are
covered by the in-game checklist below.

## In-game validation checklist for this P0 batch

Run at least a dedicated server + client, a client with Angelica, and a client
with Hodgepodge (the GTNH default config). Start with the automated
`runServer -PflHarness=true` smoke test above, then cover the following in-game
checks. For the full matrix see [`AUDIT.md`](AUDIT.md) §13.

### 1. Teleporter metadata (`BlockPos.advance` / `TileTeleport`)

- [ ] Place a **puller** teleporter with metadata `6..11` (world edit, NBT,
      command, or item variant) and activate it. No `ArrayIndexOutOfBoundsException`
      in the server log.
- [ ] Push teleporter, same dimension: structure arrives at the corresponding
      offset behind the destination teleporter.
- [ ] Pull teleporter, same dimension: no crash and the destination offset is
      consistent with the face the teleporter looks at.
- [ ] Cross-dimension teleport: source blocks become air, destination blocks
      appear, no updates/lighting at the source coordinates in the destination
      dimension (see the separate cross-world fix checklist).
- [ ] All six facings for both push and pull metadata (`0..11`).

### 2. Mover NBT round-trip (`TileMovingBase`)

- [ ] Start a move over a platform/chest, save and quit **mid-move**, restart.
      Collision boxes are restored (entities do not fall through), and the move
      completes or can be recovered.
- [ ] Chunk unload/reload mid-move (move the player away and back). Collision
      boxes and scheduled ticks survive.
- [ ] Move a block with a scheduled tick (flowing water, a mod machine) and
      verify the tick resumes after the move/reload.
- [ ] A mover with no `Collisions` tag (old world/creative placement) loads
      without an exception and remains pass-through only when it has no boxes.
- [ ] Light level/opacity of the moved block matches the original after reload
      (glowstone still lights, glass still transmits).

### 3. Client source tile cleanup (`MessageClearTile`)

- [ ] Move a chest with items. Open the chest GUI during the move; it closes
      instead of staying open on a destroyed tile.
- [ ] Move a sign, skull, flower pot, beacon, mob spawner and a mod TESR block.
      No ghost/invalid tile entity remains at the source; `F3` chunk/TE counts do
      not grow after repeated moves.
- [ ] Move the same block back and forth several times; the reused tile still
      renders and does not tick at the old position.
- [ ] Move a block at a position that was previously used in another dimension;
      no stale tile from the other dimension is reused (until the dimension-aware
      cache fix lands, verify this does not happen in normal play).

### 4. S35 description snapshot (`TileMovingServer`)

- [ ] Move a large frame (hundreds of blocks) with Hodgepodge default config.
      No `ConcurrentModificationException`, `IllegalStateException` or corrupted
      NBT in the client/server log.
- [ ] Watch a mover for several seconds: time/direction/light progress smoothly;
      no flicker between two ticks.
- [ ] Move a block with collision boxes and a light value; the client receives
      the correct boxes/light in the first description packet.
- [ ] With Hodgepodge `batchDescriptionPacketsCode=true` (if enabled in the
      pack), repeat the large-frame test.

### 5. Logical side (`BlockMoving.createTileEntity`)

- [ ] Dedicated server: place a moving block through `/setblock`, a world edit,
      a command, or a chunk-generation path (not the normal pusher path). No
      `ClassCastException` (`TileMovingClient` cast to `TileMovingServer`).
- [ ] Integrated server: normal pusher/slider/teleporter moves still create
      server movers and render client movers.
- [ ] A server-side thread other than `"Server thread"` (e.g. a mod's worldgen
      worker) that creates the block still gets a server tile.

### 6. Wrench bytecode generation (`WrenchFactory`)

- [ ] Start a dev client with BuildCraft, CoFH, MFR, AE2 and EnderIO APIs
      present. The wrench is created and implements the expected interfaces.
- [ ] Remove one optional API jar and start again. No `ClassFormatError`,
      `VerifyError`, or startup crash; the wrench still works for the remaining
      APIs.
- [ ] An interface with a `default` method keeps the default implementation
      (the generated wrench must not return `false/0/null` for it).
- [ ] Test right-click/left-click wrench behaviour on frames, pushers, sliders,
      teleporters and a vanilla block.

### 7. Slider rendering under threaded meshing (`BlockSlider`)

- [ ] Angelica + GTNH: place two sliders with different arrow directions in
      different chunk sections so they mesh concurrently (fly between them or
      use a high render distance). The arrow faces are on the correct sides; no
      face is culled because another worker set a different `renderSide`.
- [ ] Rotate a slider with the wrench (sneak and non-sneak) and verify the
      arrow/front faces update after a block update.
- [ ] Resource reload (`F3+T`) while looking at sliders; no stale/missing faces.
- [ ] Sliders in the inventory render with the correct front/arrow textures.

### 8. Render retry (`TileEntityRenderMoving`)

- [ ] Join a world with an active mover; the block is visible on the first
      frame after its S35 arrives, even if the first render attempt is not ready.
- [ ] Move a block whose ISBRH/TESR requires its world/data (e.g. a mod machine
      or a connected block). It appears after the data is ready instead of staying
      invisible for the mover's lifetime.
- [ ] Cause a renderer exception (dev build or a known-bad optional block); the
      error crate is shown, the error is logged once, and other movers continue
      rendering.
- [ ] `F3+T` while a mover is active: render caches are rebuilt and the block
      remains visible.

### 9. Pusher limits (`TilePusher`)

- [ ] Build a sticky structure larger than the configured
      `maximumBlocksPushed` (default 1024, max 8192). Activating the pusher
      returns without a multi-second freeze or OOM; the move is rejected.
- [ ] Set `maximumBlocksPushed=0` or a negative value in the config; it is
      clamped to `1`, not an instant rejection/exception.
- [ ] Set `maximumBlocksPushed` very high and `energyPerBlock` very high; the
      energy storage does not overflow to a negative capacity and the pusher
      behaves predictably.
- [ ] Move exactly the configured limit (accepted) and one block over the limit
      (rejected).
- [ ] Teleporter and booster paths use the same limit/energy calculations.

### 10. API source cleanup (`src/api`)

- [ ] The built jar does **not** contain `cofh/api/**` or `thaumcraft/api/**`
      classes. It still contains `com/rwtema/funkylocomotion/api/**`.
- [ ] Run without CoFHCore: placing/loading a pusher/booster is the known hard
      dependency case; no `NoClassDefFoundError` from an unrelated class.
- [ ] Run without Thaumcraft: startup is clean and node moving is simply not
      registered.
- [ ] Run with GTNH Thaumcraft and CoFHCore: node moves, COFH facade stickiness
      and the wrench still work.
- [ ] `./gradlew build` succeeds from a clean checkout (the new Thaumcraft
      `dev` dependency is resolvable from the configured repositories).

## Additional checks for the P1 batch

### Move finalisation

- [ ] Move a frame/chest and verify there is no stone block left behind, even if
      the destination block recreation fails partway through.
- [ ] Save/restart while a move is finishing; the mover is either completed once
      or retried, not duplicated.
- [ ] Move a light-emitting/transparent block; the destination light is correct
      and the source does not retain stale skylight.
- [ ] Trigger a failing tile recreation (e.g. a deliberately broken optional
      mod tile) and verify other movers in the same batch still finish.
- [ ] Move a large frame and confirm the client does not receive a full chunk
      resend for every moved block.

### Descriptions and client cache

- [ ] Move a vanilla chest, a sign, and a mod TESR block. Vanilla/sign
      descriptions still render; mod descriptors still use their custom format.
- [ ] Load a move saved by the previous build (numeric/legacy block tag) and
      finish it; the block is restored, not replaced by air.
- [ ] Mix old and new descriptions in one move (a legacy numeric block and a
      registry-name block) and verify both render.
- [ ] Move blocks back and forth between dimensions; no tile from the previous
      dimension is reused at the same coordinates.
- [ ] Move the same block repeatedly; no ghost tile entity remains and the cache
      does not grow across a world unload.

### Wrench/frame/slider interaction

- [ ] Wrench a 5x5 area of frames and pushers; only the server mutates the world,
      no client-side double activation/flicker.
- [ ] Click the front face of a slider; it flips, and clicking a different face
      selects that face for both push and pull variants.
- [ ] Use the hammer wrench on a frame face; the frame changes once, not twice.

### Entities on moving structures

- [ ] Stand on a moving platform; the player moves with it and keeps ground
      contact (no floating or falling through).
- [ ] Sneak at the edge of a moving platform; the player does not slide off.
- [ ] Put a mob, item entity, minecart/boat and a pressure plate on a moving
      platform; collisions, ground state and sounds behave like vanilla.
- [ ] Move a platform under an entity in a web/water; the web slowdown and
      fall/step logic still match vanilla.
- [ ] Move a platform up/down; no fall damage/fall state is triggered by the
      platform motion itself.

### Teleporters and pushers

- [ ] Open a chest at the destination of a teleport; the GUI closes before the
      destination tile is destroyed.
- [ ] Power a pusher, save/reload, and verify its powered state persists.
- [ ] Start the pack without CoFHCore; Forge now reports the missing required
      dependency instead of a `NoClassDefFoundError` on placement.
- [ ] Check the log uses the `funkylocomotion` logger and shows formatted
      messages (not just the first argument).

## P2 checks

### Blacklist and move checks

- [ ] Send `FMLInterModComms.sendMessage("funkylocomotion", "blacklist", "mod:gregtech")`
      from another mod (or use a small test mod) and verify GregTech blocks are rejected.
- [ ] Try `block:modid:name` and `ore:ingotIron` entries; both forms reject the
      matching blocks and do not blacklist unrelated blocks.
- [ ] Register a custom `IMoveChecker`; verify it can veto a move and that other
      movers still work.

### FMP and recipes

- [ ] Move a ForgeMultipart/FMP microblock or multipart machine; the client
      reconstructs the parts and renders them during/after the move.
- [ ] Start a dev client/NEI and check frame/pusher/wrench/teleporter recipes;
      no ore dictionary debug spam, correct TE fallback, no recipe-sorter warning.
- [ ] Craft teleporter items repeatedly; the generated ID is non-zero and the
      tooltip words are stable after a resource reload/language change.

### Rendering and items

- [ ] Open the creative inventory/NEI; frame items expose the 16 metadata
      variants and empty direction lists do not show `" ()"`.
- [ ] Hold the eye wrench in a GUI; the pupil follows the mouse and no GL state
      leaks into the rest of the GUI.
- [ ] Use the obstruction particles with the eye wrench; other particles are not
      corrupted, and Angelica does not report batch/draw errors.
- [ ] Inspect pusher/slider inventory and world models after rotating; matrices
      and UV rotations are restored.
- [ ] `F3+T` while a mover is visible; cached mover geometry is rebuilt instead
      of showing stale textures.

### Blocks, drops and optional mods

- [ ] Break/move blocks with `onBlockAdded` state (redstone, rails, mod
      machines); state is initialized after the silent write.
- [ ] Break an obstacle with a frame; drops, neighbour notifications and client
      updates happen once.
- [ ] Start without ForgeMultipart, Thaumcraft and CoFH appearance support;
      startup is clean and the optional integrations are simply absent.
- [ ] Start with FMP + Thaumcraft + CoFH; all integrations register.

### Registries

- [ ] Register a move factory for a block superclass; subclasses use it.
- [ ] Register a sticky proxy for an interface implemented by a block's
      superclass; sticky movement still works.

## P3 checks

- [ ] Place a pusher as a player, register an `IMovePermissions` that denies
      that player, and verify the pusher refuses to move; remove the checker and
      verify it moves again.
- [ ] Break a teleporter with a pickaxe and with pick-block; the dropped/picked
      item keeps its teleporter ID and stats/exhaustion are applied once.
- [ ] Change a slider's facing with the wrench; the slide direction resets
      deterministically and persists through save/reload.
- [ ] Move a structure using a block implementing `IAdvStickyBlock`; the extra
      positions are included in the move.
- [ ] Verify crash reports still render a readable move context if a custom
      block's `toString()`/collection throws.
- [ ] Save a mover mid-animation; the tile's time is persisted within the
      throttled chunk-save interval and resumes correctly.
- [ ] Start a mover with no description data; the client receives a DNR packet
      instead of rendering a stale/invisible tile.

## Feature checks

- [ ] Run `/funkylocomotion reload` after changing `maximumBlocksPushed` /
      `energyPerBlock`; existing pushers/boosters get the new capacity.
- [ ] Set `energyPerBlock=0` and reload; a pusher and a teleporter pair move
      with no RF input and no boosters, and existing pushers/boosters drop to
      zero capacity. Negative values behave as `0`, not as a huge cost.
- [ ] Add a `renderBlacklist` entry and `/fl reload`; the matching mover renders
      the error/empty crate instead of the tile entity.
- [ ] Place a pusher as a player, register an `IMovePermissions` that denies the
      player, and verify the pusher refuses; allow it and verify it moves.
- [ ] Use the eye wrench on teleporter A, then on teleporter B; both share an ID
      and move a payload.
- [ ] Place a third teleporter with the same id; it receives a fresh id. Break
      the pair, restart the server, and verify the old id is not silently reused.
- [ ] Open the Forge mod config GUI for Funky Locomotion; edit values, close,
      and verify the new values apply (or use `/fl reload`).
- [ ] Register an `IMoveFactory`/`IMoveChecker`/`IDescriptionProxy` through
      `FunkyLocomotionAPI` from a test mod and verify each hook is used.
- [ ] Move a structure with a booster; the shared energy helper still charges
      the source and booster correctly.

## Regression commands before merging the branch

```bash
./gradlew clean test
./gradlew build
git status --short
```

For the complete long-term matrix (vanilla, Angelica only, Hodgepodge only,
Angelica + Hodgepodge, shaders, Java 8/17/21, LAN/dedicated server), use
[`AUDIT.md`](AUDIT.md) §13.
