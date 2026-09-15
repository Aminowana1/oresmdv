package com.mdvcraft.headores.loot.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class LootEditorHolder implements InventoryHolder {
    private final String nodeKey;
    private Inventory inventory;
    public LootEditorHolder(String nodeKey) { this.nodeKey = nodeKey; }
    public String nodeKey() { return nodeKey; }
    public void attach(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
