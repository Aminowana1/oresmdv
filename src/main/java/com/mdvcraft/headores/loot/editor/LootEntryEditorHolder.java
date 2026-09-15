package com.mdvcraft.headores.loot.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class LootEntryEditorHolder implements InventoryHolder {
    private final String nodeKey;
    private final String entryKey;
    private final int returnPage;
    private Inventory inventory;

    public LootEntryEditorHolder(String nodeKey, String entryKey, int returnPage) {
        this.nodeKey = nodeKey;
        this.entryKey = entryKey;
        this.returnPage = Math.max(0, returnPage);
    }

    public String nodeKey() { return nodeKey; }
    public String entryKey() { return entryKey; }
    public int returnPage() { return returnPage; }
    public void attach(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
