package com.mdvcraft.headores.loot.editor;

import com.mdvcraft.headores.loot.config.LootNodeRegistry;
import com.mdvcraft.headores.loot.model.LootEntry;
import com.mdvcraft.headores.loot.model.LootItemReference;
import com.mdvcraft.headores.loot.model.LootNodeDefinition;
import com.mdvcraft.headores.loot.service.LootItemResolver;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public final class LootEditorManager implements Listener {
    private static final int INVENTORY_SIZE = 54;
    private static final int CONTROL_ROW = 45;
    private static final DecimalFormat PERCENT = new DecimalFormat("0.##");

    private final LootNodeRegistry registry;
    private final LootItemResolver resolver;
    private final int rewardsPerPage;

    public LootEditorManager(LootNodeRegistry registry, LootItemResolver resolver, int rewardsPerPage) {
        this.registry = registry;
        this.resolver = resolver;
        this.rewardsPerPage = Math.max(1, Math.min(CONTROL_ROW, rewardsPerPage));
    }

    public boolean open(Player player, String nodeKey) {
        LootNodeDefinition node = registry.node(nodeKey);
        if (node == null) return false;
        renderMain(player, node, 0);
        return true;
    }

    private void renderMain(Player player, LootNodeDefinition node, int requestedPage) {
        int page = normalizePage(node, requestedPage);
        int maxPage = maxPage(node);
        LootEditorHolder holder = new LootEditorHolder(node.key(), page);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                "§8Loot: " + strip(node.displayName()) + " §7[" + (page + 1) + "/" + (maxPage + 1) + "]");
        holder.attach(inventory);

        double totalWeight = node.loot().entries().stream().mapToDouble(LootEntry::weight).sum();
        int from = page * rewardsPerPage;
        int to = Math.min(node.loot().entries().size(), from + rewardsPerPage);
        for (int globalIndex = from; globalIndex < to; globalIndex++) {
            LootEntry entry = node.loot().entries().get(globalIndex);
            int slot = globalIndex - from;
            ItemStack icon = resolver.build(entry.item(), 1);
            if (icon == null) icon = named(Material.BARRIER, "§cItem no disponible");
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null
                        ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(" ");
                lore.add("§7Referencia: §f" + reference(entry.item()));
                lore.add("§7Peso: §f" + PERCENT.format(entry.weight()));
                double chance = totalWeight <= 0.0D ? 0.0D : entry.weight() * 100.0D / totalWeight;
                lore.add("§7Probabilidad relativa: §a" + PERCENT.format(chance) + "%");
                lore.add("§7Cantidad: §f" + entry.minAmount() + "-" + entry.maxAmount());
                lore.add("§7Repetible: " + (entry.repeatable() ? "§aSí" : "§cNo"));
                lore.add(" ");
                lore.add("§eClick para editar esta recompensa.");
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            inventory.setItem(slot, icon);
        }

        if (page > 0) {
            inventory.setItem(45, control(Material.ARROW, "§ePágina anterior",
                    "§7Ir a página §f" + page + "§7/§f" + (maxPage + 1)));
        }
        inventory.setItem(46, control(Material.LIME_DYE, "§aPremios mínimos",
                "§f" + node.loot().rollsMin(), node.containerType().singleDrop() ? "§7Vasijas siempre usan 1." : "§7Izq +1 | Der -1"));
        inventory.setItem(47, control(Material.GREEN_DYE, "§aPremios máximos",
                "§f" + node.loot().rollsMax(), node.containerType().singleDrop() ? "§7Vasijas siempre usan 1." : "§7Izq +1 | Der -1"));
        inventory.setItem(48, control(Material.CHEST, "§6Máximo de slots de loot",
                "§f" + node.loot().maxSlots(), node.containerType().singleDrop() ? "§7Vasijas siempre usan 1." : "§7Izq +1 | Der -1"));
        inventory.setItem(49, control(Material.HOPPER, "§eUnir objetos iguales",
                node.loot().mergeSameItems() ? "§aACTIVADO" : "§cDESACTIVADO", "§7Click para alternar."));
        inventory.setItem(50, control(node.containerType().material(), "§d" + strip(node.displayName()),
                "§7Tipo: §f" + node.containerType().name(),
                "§7Recompensas: §f" + node.loot().entries().size(),
                "§7Página: §f" + (page + 1) + "/" + (maxPage + 1),
                " ",
                "§aSHIFT + click §7en un item de tu inventario",
                "§7para añadirlo rápidamente.",
                "§7También puedes ponerlo en el cursor y",
                "§7hacer click en un slot vacío.",
                "§7El item original NO se consume."));
        inventory.setItem(51, control(Material.BOOK, "§bPaginación",
                "§7Hasta §f" + rewardsPerPage + " §7recompensas por página.",
                "§7Las recompensas pueden ocupar páginas ilimitadas."));
        if (page < maxPage) {
            inventory.setItem(52, control(Material.ARROW, "§ePágina siguiente",
                    "§7Ir a página §f" + (page + 2) + "§7/§f" + (maxPage + 1)));
        }
        inventory.setItem(53, named(Material.BARRIER, "§cCerrar"));
        player.openInventory(inventory);
    }

    private void renderEntry(Player player, LootNodeDefinition node, LootEntry entry, int returnPage) {
        LootEntryEditorHolder holder = new LootEntryEditorHolder(node.key(), entry.key(), returnPage);
        Inventory inventory = Bukkit.createInventory(holder, 27, "§8Editar recompensa");
        holder.attach(inventory);
        ItemStack preview = resolver.build(entry.item(), 1);
        inventory.setItem(4, preview == null ? named(Material.BARRIER, "§cItem no disponible") : preview);
        inventory.setItem(10, control(Material.GOLD_NUGGET, "§6Peso / probabilidad", "§f" + PERCENT.format(entry.weight()), clickHelp()));
        inventory.setItem(12, control(Material.IRON_NUGGET, "§eCantidad mínima", "§f" + entry.minAmount(), clickHelp()));
        inventory.setItem(14, control(Material.IRON_INGOT, "§eCantidad máxima", "§f" + entry.maxAmount(), clickHelp()));
        inventory.setItem(16, control(Material.REPEATER, "§bRepetible", entry.repeatable() ? "§aSí" : "§cNo", "§7Click para alternar."));
        inventory.setItem(22, named(Material.REDSTONE_BLOCK, "§cEliminar recompensa"));
        inventory.setItem(26, named(Material.ARROW, "§eVolver"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder(false) instanceof LootEditorHolder holder) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            LootNodeDefinition node = registry.node(holder.nodeKey());
            if (node == null) { player.closeInventory(); return; }

            // Como MDVCrates: SHIFT+click desde el inventario del jugador añade una copia
            // como posible recompensa, sin mover ni consumir el item real.
            if (event.getClickedInventory() != null && event.getClickedInventory() != top) {
                if (event.isShiftClick()) {
                    event.setCancelled(true);
                    ItemStack current = event.getCurrentItem();
                    if (current != null && current.getType() != Material.AIR) addEntry(player, node, current);
                }
                return;
            }

            if (event.getClickedInventory() != top) return;
            event.setCancelled(true);
            int slot = event.getRawSlot();

            if (slot >= 0 && slot < rewardsPerPage) {
                int globalIndex = holder.page() * rewardsPerPage + slot;
                if (globalIndex < node.loot().entries().size()) {
                    renderEntry(player, node, node.loot().entries().get(globalIndex), holder.page());
                } else {
                    ItemStack cursor = event.getCursor();
                    if (cursor == null || cursor.getType() == Material.AIR) {
                        player.sendMessage("§ePon un item en tu cursor o usa SHIFT+click desde tu inventario.");
                        return;
                    }
                    addEntry(player, node, cursor);
                }
                return;
            }

            if (slot == 45 && holder.page() > 0) {
                renderMain(player, node, holder.page() - 1);
                return;
            }
            if (slot == 52 && holder.page() < maxPage(node)) {
                renderMain(player, node, holder.page() + 1);
                return;
            }
            if (slot == 53) { player.closeInventory(); return; }
            if (slot == 49) {
                registry.updateLoot(node.key(), node.loot().withMerge(!node.loot().mergeSameItems()));
                renderMain(player, registry.node(node.key()), holder.page());
                return;
            }
            if (node.containerType().singleDrop()) return;
            int delta = event.isLeftClick() ? 1 : event.isRightClick() ? -1 : 0;
            if (delta == 0) return;
            if (slot == 46) {
                int min = Math.max(1, node.loot().rollsMin() + delta);
                int max = Math.max(min, node.loot().rollsMax());
                registry.updateLoot(node.key(), node.loot().withRolls(min, max));
            } else if (slot == 47) {
                int max = Math.max(node.loot().rollsMin(), node.loot().rollsMax() + delta);
                registry.updateLoot(node.key(), node.loot().withRolls(node.loot().rollsMin(), max));
            } else if (slot == 48) {
                registry.updateLoot(node.key(), node.loot().withMaxSlots(Math.max(1, node.loot().maxSlots() + delta)));
            } else return;
            renderMain(player, registry.node(node.key()), holder.page());
            return;
        }

        if (top.getHolder(false) instanceof LootEntryEditorHolder holder) {
            if (event.getClickedInventory() != top) return;
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            LootNodeDefinition node = registry.node(holder.nodeKey());
            if (node == null) { player.closeInventory(); return; }
            LootEntry entry = findEntry(node, holder.entryKey());
            if (entry == null) { renderMain(player, node, holder.returnPage()); return; }
            int slot = event.getRawSlot();
            if (slot == 26) { renderMain(player, node, holder.returnPage()); return; }
            if (slot == 22) {
                List<LootEntry> entries = new ArrayList<>(node.loot().entries());
                entries.removeIf(e -> e.key().equals(entry.key()));
                registry.updateLoot(node.key(), node.loot().withEntries(entries));
                renderMain(player, registry.node(node.key()), holder.returnPage());
                return;
            }
            LootEntry updated = entry;
            if (slot == 16) updated = entry.withRepeatable(!entry.repeatable());
            else {
                int delta = clickDelta(event.getClick());
                if (delta == 0) return;
                if (slot == 10) updated = entry.withWeight(Math.max(0.1D, entry.weight() + delta));
                else if (slot == 12) {
                    int min = Math.max(1, entry.minAmount() + delta);
                    updated = entry.withAmounts(min, Math.max(min, entry.maxAmount()));
                } else if (slot == 14) {
                    int max = Math.max(entry.minAmount(), entry.maxAmount() + delta);
                    updated = entry.withAmounts(entry.minAmount(), max);
                } else return;
            }
            replaceEntry(node, updated);
            LootNodeDefinition refreshed = registry.node(node.key());
            renderEntry(player, refreshed, findEntry(refreshed, updated.key()), holder.returnPage());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof LootEditorHolder)
                && !(top.getHolder(false) instanceof LootEntryEditorHolder)) return;
        for (int slot : event.getRawSlots()) {
            if (slot < top.getSize()) { event.setCancelled(true); return; }
        }
    }

    private void addEntry(Player player, LootNodeDefinition node, ItemStack source) {
        LootItemReference ref = resolver.identify(source);
        if (ref == null) {
            player.sendMessage("§cNo pude identificar ese item como vanilla, MMOItems o MythicMobs.");
            return;
        }
        List<LootEntry> entries = new ArrayList<>(node.loot().entries());
        String key = nextKey(entries);
        int amount = Math.max(1, source.getAmount());
        entries.add(new LootEntry(key, ref, 1.0D, amount, amount, true));
        registry.updateLoot(node.key(), node.loot().withEntries(entries));
        LootNodeDefinition refreshed = registry.node(node.key());
        int page = (refreshed.loot().entries().size() - 1) / rewardsPerPage;
        renderMain(player, refreshed, page);
        player.sendMessage("§aRecompensa añadida. §7Página §f" + (page + 1) + "§7.");
    }

    private void replaceEntry(LootNodeDefinition node, LootEntry updated) {
        List<LootEntry> entries = new ArrayList<>(node.loot().entries());
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).key().equals(updated.key())) { entries.set(i, updated); break; }
        }
        registry.updateLoot(node.key(), node.loot().withEntries(entries));
    }

    private int maxPage(LootNodeDefinition node) {
        int count = Math.max(1, node.loot().entries().size());
        return Math.max(0, (count - 1) / rewardsPerPage);
    }

    private int normalizePage(LootNodeDefinition node, int page) {
        return Math.max(0, Math.min(page, maxPage(node)));
    }

    private static LootEntry findEntry(LootNodeDefinition node, String key) {
        if (node == null) return null;
        return node.loot().entries().stream().filter(e -> e.key().equals(key)).findFirst().orElse(null);
    }

    private static int clickDelta(ClickType click) {
        return switch (click) {
            case LEFT -> 1;
            case SHIFT_LEFT -> 5;
            case RIGHT -> -1;
            case SHIFT_RIGHT -> -5;
            default -> 0;
        };
    }

    private static String clickHelp() { return "§7Izq +1 | Shift+Izq +5 | Der -1 | Shift+Der -5"; }

    private static String nextKey(List<LootEntry> entries) {
        int i = 1;
        while (true) {
            String key = "entry_" + i++;
            boolean exists = entries.stream().anyMatch(entry -> entry.key().equals(key));
            if (!exists) return key;
        }
    }

    private static String reference(LootItemReference ref) {
        return switch (ref.type()) {
            case VANILLA -> "VANILLA / " + ref.vanillaMaterial().name();
            case CUSTOM_VANILLA -> "CUSTOM_VANILLA / " + (ref.vanillaMaterial() == null ? "ITEM" : ref.vanillaMaterial().name());
            case MMOITEM -> "MMOITEM / " + ref.itemType() + ":" + ref.itemId();
            case MYTHICMOBS -> "MYTHICMOBS / " + ref.itemId();
        };
    }

    private static ItemStack control(Material material, String name, String... lore) {
        ItemStack item = named(material, name);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String strip(String value) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', value));
    }
}
