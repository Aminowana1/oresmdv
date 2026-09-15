package com.mdvcraft.headores.loot.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class LootEntryEditorHolder implements InventoryHolder {
    private final String nodeKey;
    private final String entryKey;
    private Inventory inventory;
    public LootEntryEditorHolder(String nodeKey, String entryKey) {
        this.nodeKey = nodeKey;
        this.entryKey = entryKey;
    }
    public String nodeKey() { return nodeKey; }
    public String entryKey() { return entryKey; }
    public void attach(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
