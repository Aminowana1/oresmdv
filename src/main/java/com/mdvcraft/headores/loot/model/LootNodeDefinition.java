package com.mdvcraft.headores.loot.model;

import org.bukkit.Material;

import java.util.Set;

public record LootNodeDefinition(
        String key,
        boolean enabled,
        int trackingBit,
        String displayName,
        LootContainerType containerType,
        String textureHash,
        int inventorySize,
        Set<String> worlds,
        int minY,
        int maxY,
        double chunkChance,
        int nodesPerChunk,
        int placementAttempts,
        Set<Material> replaceableSpace,
        LootTableDefinition loot
) {
    public LootNodeDefinition {
        inventorySize = normalizeInventorySize(inventorySize);
        worlds = Set.copyOf(worlds == null ? Set.of() : worlds);
        int lowY = Math.min(minY, maxY);
        int highY = Math.max(minY, maxY);
        minY = lowY;
        maxY = highY;
        chunkChance = Math.max(0.0D, Math.min(1.0D, chunkChance));
        nodesPerChunk = Math.max(1, nodesPerChunk);
        placementAttempts = Math.max(1, Math.min(256, placementAttempts));
        replaceableSpace = Set.copyOf(replaceableSpace == null ? Set.of() : replaceableSpace);

        if (loot == null) loot = new LootTableDefinition(1, 1, 1, true, java.util.List.of());
        int capacity = switch (containerType) {
            case PLAYER_HEAD -> inventorySize;
            case CHEST, BARREL -> 27;
            case DECORATED_POT -> 1;
        };
        if (containerType.singleDrop()) {
            loot = new LootTableDefinition(1, 1, 1, loot.mergeSameItems(), loot.entries());
        } else if (loot.maxSlots() > capacity) {
            loot = loot.withMaxSlots(capacity);
        }
    }

    private static int normalizeInventorySize(int size) {
        if (size <= 9) return 9;
        if (size <= 18) return 18;
        if (size <= 27) return 27;
        if (size <= 36) return 36;
        if (size <= 45) return 45;
        return 54;
    }

    public long trackingMask() { return 1L << trackingBit; }
    public boolean isAllowedInWorld(String world) { return worlds.isEmpty() || worlds.contains(world); }

    public LootNodeDefinition withLoot(LootTableDefinition updated) {
        return new LootNodeDefinition(key, enabled, trackingBit, displayName, containerType, textureHash,
                inventorySize, worlds, minY, maxY, chunkChance, nodesPerChunk, placementAttempts,
                replaceableSpace, updated);
    }
}
