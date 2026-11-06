package com.rwtema.funkylocomotion.blocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Persistent teleporter id registry. A pair shares one id, so an id may be
 * issued to at most two placed teleporters. The issued set is kept even after
 * both are broken so a later session cannot accidentally reuse the id.
 */
public class TeleportIdData extends WorldSavedData {

    private static final String DATA_NAME = "funkylocomotion_teleport_ids";
    private static final int MAX_ID = 0xFFFFF;

    private final Set<Integer> issued = new HashSet<Integer>();
    private final Map<Integer, Integer> placed = new HashMap<Integer, Integer>();
    private int nextId = 1;

    public TeleportIdData(String name) {
        super(name);
    }

    public static TeleportIdData get(World world) {
        if (world == null || world.isRemote || world.mapStorage == null) return null;

        WorldSavedData data = world.mapStorage.loadData(TeleportIdData.class, DATA_NAME);
        if (!(data instanceof TeleportIdData)) {
            data = new TeleportIdData(DATA_NAME);
            world.mapStorage.setData(DATA_NAME, data);
        }
        return (TeleportIdData) data;
    }

    /**
     * @return the id to actually use. A requested id is preserved while it has
     *         fewer than two placed users; otherwise a fresh id is allocated.
     */
    public int allocate(int requested) {
        int count = placed.containsKey(requested) ? placed.get(requested) : 0;
        if (requested != 0 && requested <= MAX_ID && (!issued.contains(requested) || count > 0 && count < 2)) {
            issued.add(requested);
            placed.put(requested, count + 1);
            markDirty();
            return requested;
        }

        int id = nextId;
        while (issued.contains(id)) {
            id++;
            if (id > MAX_ID) id = 1;
        }

        nextId = id >= MAX_ID ? 1 : id + 1;
        issued.add(id);
        placed.put(id, 1);
        markDirty();
        return id;
    }

    public int reassign(int oldId, int requested) {
        if (oldId != 0) release(oldId);
        return allocate(requested);
    }

    public void release(int id) {
        if (id == 0) return;

        Integer count = placed.get(id);
        if (count == null) return;

        if (count <= 1) placed.remove(id);
        else placed.put(id, count - 1);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        issued.clear();
        placed.clear();
        nextId = tag.getInteger("Next");
        if (nextId <= 0 || nextId > MAX_ID) nextId = 1;

        NBTTagList list = tag.getTagList("IDs", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int id = entry.getInteger("ID");
            int count = entry.getInteger("Count");
            if (id <= 0 || id > MAX_ID) continue;

            issued.add(id);
            if (count > 0) placed.put(id, Math.min(count, 2));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("Next", nextId);

        NBTTagList list = new NBTTagList();
        for (Integer id : issued) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("ID", id);
            entry.setInteger("Count", placed.containsKey(id) ? placed.get(id) : 0);
            list.appendTag(entry);
        }
        tag.setTag("IDs", list);
    }
}
