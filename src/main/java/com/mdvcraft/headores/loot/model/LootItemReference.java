package com.mdvcraft.headores.loot.model;

import org.bukkit.Material;

public record LootItemReference(
        LootItemType type,
        Material vanillaMaterial,
        String itemType,
        String itemId
) {
    public static LootItemReference vanilla(Material material) {
        return new LootItemReference(LootItemType.VANILLA, material, null, material.name());
    }

    public static LootItemReference mmoItem(String type, String id) {
        return new LootItemReference(LootItemType.MMOITEM, null, type, id);
    }

    public static LootItemReference mythic(String id) {
        return new LootItemReference(LootItemType.MYTHICMOBS, null, null, id);
    }

    public String stableKey() {
        return switch (type) {
            case VANILLA -> "VANILLA:" + vanillaMaterial.name();
            case MMOITEM -> "MMOITEM:" + itemType + ":" + itemId;
            case MYTHICMOBS -> "MYTHICMOBS:" + itemId;
        };
    }
}
