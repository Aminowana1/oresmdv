package com.mdvcraft.headores.loot.service;

import com.mdvcraft.headores.loot.model.LootEntry;
import com.mdvcraft.headores.loot.model.LootNodeDefinition;
import com.mdvcraft.headores.loot.model.LootTableDefinition;
import org.bukkit.Material;
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
        List<RolledReward> rewards = new ArrayList<>();
        int attempts = 0;
        int maxAttempts = Math.max(8, rolls * 4);

        while (rolls > 0 && !pool.isEmpty() && attempts++ < maxAttempts) {
            LootEntry entry = weightedPick(pool, random);
            if (entry == null) break;

            int amount = random.nextInt(entry.minAmount(), entry.maxAmount() + 1);

            // Construimos una sola unidad/prototipo. La cantidad lógica se conserva
            // aparte para poder repartirla después en varios slots sin truncarla al
            // max stack size del ItemStack.
            ItemStack prototype = resolver.buildLoot(entry.item(), 1);
            if (prototype != null && prototype.getType() != Material.AIR) {
                prototype = prototype.clone();
                prototype.setAmount(1);

                boolean merged = table.mergeSameItems() && mergeAmount(rewards, prototype, amount);
                if (!merged) {
                    // max-slots es un límite duro de slots finales. No agregamos un
                    // nuevo grupo si ni siquiera cabe su primer slot.
                    if (rewards.size() >= table.maxSlots()) break;
                    rewards.add(new RolledReward(prototype, amount));
                }
                rolls--;
            }

            if (!entry.repeatable()) pool.remove(entry);
        }

        return spreadAcrossSlots(rewards, table.maxSlots());
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

    private static boolean mergeAmount(List<RolledReward> rewards, ItemStack incoming, int amount) {
        for (RolledReward existing : rewards) {
            if (!existing.prototype.isSimilar(incoming)) continue;
            existing.totalAmount = safeAdd(existing.totalAmount, amount);
            return true;
        }
        return false;
    }

    /**
     * Expande las cantidades para ocupar tantos slots como sea posible hasta
     * maxSlots. Ejemplo: HILO x10 + otras 4 recompensas de una unidad con
     * maxSlots=12 termina usando 8 slots para el hilo y 4 para las demás.
     *
     * La primera fase reserva los slots mínimos necesarios para respetar el
     * tamaño máximo de stack. La segunda usa los slots sobrantes para separar
     * las pilas de manera uniforme y visualmente dispersa.
     */
    private static List<ItemStack> spreadAcrossSlots(List<RolledReward> rewards, int maxSlots) {
        if (rewards.isEmpty() || maxSlots <= 0) return List.of();

        int groupCount = Math.min(rewards.size(), maxSlots);
        int[] slotsPerReward = new int[groupCount];
        for (int i = 0; i < groupCount; i++) slotsPerReward[i] = 1;
        int usedSlots = groupCount;

        // Fase 1: intenta garantizar suficientes slots para no superar el tamaño
        // máximo de stack de cada material/item.
        boolean progressed = true;
        while (usedSlots < maxSlots && progressed) {
            progressed = false;
            for (int i = 0; i < groupCount && usedSlots < maxSlots; i++) {
                RolledReward reward = rewards.get(i);
                int maxStack = Math.max(1, reward.prototype.getMaxStackSize());
                int required = ceilDiv(reward.totalAmount, maxStack);
                if (slotsPerReward[i] < required) {
                    slotsPerReward[i]++;
                    usedSlots++;
                    progressed = true;
                }
            }
        }

        // Fase 2: cualquier slot restante se usa para abrir las pilas todavía más,
        // hasta un máximo natural de una unidad por slot.
        progressed = true;
        while (usedSlots < maxSlots && progressed) {
            progressed = false;
            for (int i = 0; i < groupCount && usedSlots < maxSlots; i++) {
                RolledReward reward = rewards.get(i);
                if (slotsPerReward[i] < reward.totalAmount) {
                    slotsPerReward[i]++;
                    usedSlots++;
                    progressed = true;
                }
            }
        }

        List<ItemStack> result = new ArrayList<>(usedSlots);
        for (int i = 0; i < groupCount; i++) {
            RolledReward reward = rewards.get(i);
            int slotCount = slotsPerReward[i];
            int maxStack = Math.max(1, reward.prototype.getMaxStackSize());

            // Si la configuración pide más cantidad de la que físicamente cabe en
            // maxSlots, maxSlots sigue siendo el límite duro. En configuraciones
            // normales esta rama no se alcanza; evita overstack ilegales.
            long capacity = (long) slotCount * maxStack;
            int effectiveAmount = (int) Math.min((long) reward.totalAmount, capacity);
            if (effectiveAmount <= 0) continue;

            int base = effectiveAmount / slotCount;
            int remainder = effectiveAmount % slotCount;
            for (int slot = 0; slot < slotCount; slot++) {
                int amount = base + (slot < remainder ? 1 : 0);
                if (amount <= 0) continue;
                ItemStack stack = reward.prototype.clone();
                stack.setAmount(Math.min(amount, maxStack));
                result.add(stack);
            }
        }
        return result;
    }

    private static int ceilDiv(int value, int divisor) {
        if (value <= 0) return 0;
        return 1 + ((value - 1) / Math.max(1, divisor));
    }

    private static int safeAdd(int first, int second) {
        long sum = (long) first + second;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    private static final class RolledReward {
        private final ItemStack prototype;
        private int totalAmount;

        private RolledReward(ItemStack prototype, int totalAmount) {
            this.prototype = prototype;
            this.totalAmount = Math.max(1, totalAmount);
        }
    }
}
