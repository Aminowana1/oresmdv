package com.mdvcraft.headores.loot.model;

public record LootEntry(
        String key,
        LootItemReference item,
        double weight,
        int minAmount,
        int maxAmount,
        boolean repeatable
) {
    public LootEntry {
        weight = Double.isFinite(weight) ? Math.max(0.0001D, weight) : 1.0D;
        minAmount = Math.max(1, minAmount);
        maxAmount = Math.max(minAmount, maxAmount);
    }

    public LootEntry withWeight(double value) {
        return new LootEntry(key, item, value, minAmount, maxAmount, repeatable);
    }

    public LootEntry withAmounts(int min, int max) {
        return new LootEntry(key, item, weight, min, max, repeatable);
    }

    public LootEntry withRepeatable(boolean value) {
        return new LootEntry(key, item, weight, minAmount, maxAmount, value);
    }
}
