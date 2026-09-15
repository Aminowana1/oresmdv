package com.mdvcraft.headores.loot.service;

import com.mdvcraft.headores.loot.model.LootEntry;
import com.mdvcraft.headores.loot.model.LootNodeDefinition;
import com.mdvcraft.headores.loot.model.LootTableDefinition;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class LootTableService {
    private final LootItemResolver resolver;

    public LootTableService(LootItemResolver resolver) {
        this.resolver = resolver;
    }

    public List<ItemStack> roll(LootNodeDefinition node) {
        LootTableDefinition table = node.loot();
        if (table.entries().isEmpty()) return List.of();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int rolls = node.containerType().singleDrop()
                ? 1
                : random.nextInt(table.rollsMin(), table.rollsMax() + 1);

        List<LootEntry> pool = new ArrayList<>(table.entries());
        List<ItemStack> result = new ArrayList<>();
        int attempts = 0;
        int maxAttempts = Math.max(8, rolls * 4);

        while (rolls > 0 && !pool.isEmpty() && attempts++ < maxAttempts) {
            LootEntry entry = weightedPick(pool, random);
            if (entry == null) break;
            int amount = random.nextInt(entry.minAmount(), entry.maxAmount() + 1);
            ItemStack built = resolver.buildLoot(entry.item(), amount);
            if (built != null) {
                if (!merge(result, built, table.mergeSameItems())) {
                    if (result.size() >= table.maxSlots()) break;
                    result.add(built);
                }
                rolls--;
            }
            if (!entry.repeatable()) pool.remove(entry);
            if (result.size() >= table.maxSlots() && !table.mergeSameItems()) break;
        }
        return result;
    }

    private static LootEntry weightedPick(List<LootEntry> entries, ThreadLocalRandom random) {
        double total = 0.0D;
        for (LootEntry entry : entries) total += Math.max(0.0D, entry.weight());
        if (total <= 0.0D) return null;
        double roll = random.nextDouble(total);
        double cursor = 0.0D;
        for (LootEntry entry : entries) {
            cursor += Math.max(0.0D, entry.weight());
            if (roll < cursor) return entry;
        }
        return entries.get(entries.size() - 1);
    }

    private static boolean merge(List<ItemStack> items, ItemStack incoming, boolean enabled) {
        if (!enabled) return false;
        for (ItemStack existing : items) {
            if (!existing.isSimilar(incoming)) continue;
            int capacity = existing.getMaxStackSize() - existing.getAmount();
            if (capacity <= 0) continue;
            int moved = Math.min(capacity, incoming.getAmount());
            existing.setAmount(existing.getAmount() + moved);
            incoming.setAmount(incoming.getAmount() - moved);
            if (incoming.getAmount() <= 0) return true;
        }
        return false;
    }
}
