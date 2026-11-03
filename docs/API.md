# Funky Locomotion API

The public API lives in `com.rwtema.funkylocomotion.api`. The stable convenience
entry point is `FunkyLocomotionAPI`.

```java
FunkyLocomotionAPI.registerMoveFactory(Block block, IMoveFactory factory);
FunkyLocomotionAPI.registerMoveFactory(Class<? extends Block> blockClass, IMoveFactory factory);
FunkyLocomotionAPI.registerMoveFactoryForTile(Class<? extends TileEntity> tileClass, IMoveFactory factory);
FunkyLocomotionAPI.registerDescription(IDescriptionProxy proxy, boolean fallback);
FunkyLocomotionAPI.registerMoveChecker(IMoveChecker checker);
FunkyLocomotionAPI.registerMovePermissions(IMovePermissions permissions);
FunkyLocomotionAPI.addBlacklist(String entry);
```

## Movement checks

- `IMoveCheck` on a block or tile entity is checked first.
- `IMoveChecker` implementations registered through `MoveCheckRegistry` (or the
  facade) can veto an entire move. `_Immovable()` classes are handled by the
  default checker.
- `IMovePermissions` implementations are called with the pusher/teleporter
  owner's username and UUID (captured when the block is placed). A checker can
  integrate claims or server permissions.

## Blacklist

The blacklist accepts:

- `modid` or `mod:modid` — all blocks from a mod;
- `modid:name` or `block:modid:name` — one registry name;
- `ore:oreName` — all blocks in an ore dictionary entry.

It can be extended with the facade or with
`FMLInterModComms.sendMessage("funkylocomotion", "blacklist", "mod:gregtech")`.

## Descriptions

`IDescriptionProxy` reconstructs a client-side tile entity for rendering.
Register custom proxies with `registerDescription(proxy, fallback)`, where
`fallback = true` means low priority. `DescribeVanilla` is the final fallback.

## Teleporters

Use the eye wrench on a placed teleporter to copy its ID onto the wrench. Use
the wrench on another teleporter to apply that ID. Both teleporters then share
a link. IDs are non-zero, deduplicated within a server session, and allocated
through `TeleportIdData`, a `WorldSavedData` registry that keeps issued IDs
reserved across restarts. Items keep their ID in NBT when broken.
