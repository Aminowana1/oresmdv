package com.mdvcraft.headores.loot.service;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class LootInventoryHolder implements InventoryHolder {
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final String nodeKey;
    private Inventory inventory;

    public LootInventoryHolder(UUID worldId, int x, int y, int z, String nodeKey) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.nodeKey = nodeKey;
    }

    public void attach(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
    public World world() { return Bukkit.getWorld(worldId); }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public String nodeKey() { return nodeKey; }
    public String cacheKey() { return worldId + ":" + x + ":" + y + ":" + z; }
}
