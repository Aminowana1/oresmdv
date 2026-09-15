package com.mdvcraft.headores.loot.model;

import org.bukkit.Material;

public enum LootContainerType {
    PLAYER_HEAD(Material.PLAYER_HEAD, 1, true),
    CHEST(Material.CHEST, 1, false),
    BARREL(Material.BARREL, 1, false),
    DECORATED_POT(Material.DECORATED_POT, 2, false);

    private final Material material;
    private final int verticalClearance;
    private final boolean virtualInventory;

    LootContainerType(Material material, int verticalClearance, boolean virtualInventory) {
        this.material = material;
        this.verticalClearance = verticalClearance;
        this.virtualInventory = virtualInventory;
    }

    public Material material() { return material; }
    public int verticalClearance() { return verticalClearance; }
    public boolean virtualInventory() { return virtualInventory; }
    public boolean singleDrop() { return this == DECORATED_POT; }
}
