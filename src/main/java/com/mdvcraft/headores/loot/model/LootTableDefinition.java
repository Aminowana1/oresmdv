package com.mdvcraft.headores.loot.model;

import java.util.List;

public record LootTableDefinition(
        int rollsMin,
        int rollsMax,
        int maxSlots,
        boolean mergeSameItems,
        List<LootEntry> entries
) {
    public LootTableDefinition {
        rollsMin = Math.max(1, rollsMin);
        rollsMax = Math.max(rollsMin, rollsMax);
        maxSlots = Math.max(1, maxSlots);
        entries = List.copyOf(entries == null ? List.of() : entries);
    }

    public LootTableDefinition withEntries(List<LootEntry> newEntries) {
        return new LootTableDefinition(rollsMin, rollsMax, maxSlots, mergeSameItems, newEntries);
    }

    public LootTableDefinition withRolls(int min, int max) {
        return new LootTableDefinition(min, max, maxSlots, mergeSameItems, entries);
    }

    public LootTableDefinition withMaxSlots(int value) {
        return new LootTableDefinition(rollsMin, rollsMax, value, mergeSameItems, entries);
    }

    public LootTableDefinition withMerge(boolean value) {
        return new LootTableDefinition(rollsMin, rollsMax, maxSlots, value, entries);
    }
}
