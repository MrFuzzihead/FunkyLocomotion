package com.rwtema.funkylocomotion.movepermissions;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import com.rwtema.funkylocomotion.api.BlockPos;

/**
 * IMC/config blacklist for blocks that must never be moved.
 *
 * <p>
 * Entries:
 * <ul>
 * <li>{@code modid} or {@code mod:modid} — all blocks from that mod;</li>
 * <li>{@code block:modid:name} or {@code modid:name} — one block;</li>
 * <li>{@code ore:oreName} — every block registered for an ore dictionary name.</li>
 * </ul>
 */
public final class MoveBlacklist {

    private static final Set<String> blockNames = ConcurrentHashMap.newKeySet();
    private static final Set<String> modIds = ConcurrentHashMap.newKeySet();
    private static final Set<String> oreNames = ConcurrentHashMap.newKeySet();

    private MoveBlacklist() {}

    public static void add(String entry) {
        if (entry == null) return;
        entry = entry.trim();
        if (entry.isEmpty()) return;

        if (entry.startsWith("mod:")) {
            modIds.add(
                entry.substring(4)
                    .trim());
        } else if (entry.startsWith("block:")) {
            blockNames.add(
                entry.substring(6)
                    .trim());
        } else if (entry.startsWith("ore:")) {
            oreNames.add(
                entry.substring(4)
                    .trim());
        } else if (entry.indexOf(':') >= 0) {
            blockNames.add(entry);
        } else {
            modIds.add(entry);
        }
    }

    public static void clear() {
        blockNames.clear();
        modIds.clear();
        oreNames.clear();
    }

    static boolean matches(String blockName, String modId) {
        return blockName != null && blockNames.contains(blockName) || modId != null && modIds.contains(modId);
    }

    public static boolean isBlacklisted(World world, BlockPos pos, Block block) {
        if (block == null) return false;

        String blockName = Block.blockRegistry.getNameForObject(block);
        String modId = blockName != null && blockName.indexOf(':') > 0 ? blockName.substring(0, blockName.indexOf(':'))
            : "";
        if (matches(blockName, modId)) return true;

        if (!oreNames.isEmpty()) {
            Item item = Item.getItemFromBlock(block);
            for (String ore : oreNames) {
                for (ItemStack stack : OreDictionary.getOres(ore)) {
                    if (stack != null && stack.getItem() == item) return true;
                }
            }
        }

        return false;
    }
}
