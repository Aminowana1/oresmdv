package com.mdvcraft.headores.loot.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class LootEditorHolder implements InventoryHolder {
    private final String nodeKey;
    private final int page;
    private Inventory inventory;

    public LootEditorHolder(String nodeKey, int page) {
        this.nodeKey = nodeKey;
        this.page = Math.max(0, page);
    }

    public String nodeKey() { return nodeKey; }
    public int page() { return page; }
    public void attach(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
