package com.mdvcraft.headores.loot.service;

import com.mdvcraft.headores.loot.model.LootItemReference;
import com.mdvcraft.headores.service.MmoItemsBridge;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Locale;

public final class LootItemResolver {
    private final JavaPlugin plugin;
    private final MmoItemsBridge mmoItems;
    private final MythicItemsBridge mythicItems;
    private final boolean debug;

    public LootItemResolver(JavaPlugin plugin, boolean debug) {
        this.plugin = plugin;
        this.debug = debug;
        this.mmoItems = new MmoItemsBridge(plugin, debug);
        this.mythicItems = new MythicItemsBridge(plugin, debug);
    }

    public LootItemReference identify(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return null;

        LootItemReference mmo = identifyMmoItem(stack);
        if (mmo != null) return mmo;

        String mythicId = mythicItems.identify(stack);
        if (mythicId != null && !mythicId.isBlank()) return LootItemReference.mythic(mythicId);

        if (isPlainVanilla(stack)) return LootItemReference.vanilla(stack.getType());
        // Vanilla con metadata/componentes (por ejemplo ENCHANTED_BOOK con
        // StoredEnchantments, pociones, mapas, items renombrados/encantados, etc.).
        return LootItemReference.customVanilla(stack);
    }

    public ItemStack build(LootItemReference ref, int amount) {
        if (ref == null) return null;
        int safeAmount = Math.max(1, amount);
        ItemStack stack = switch (ref.type()) {
            case VANILLA -> new ItemStack(ref.vanillaMaterial(), safeAmount);
            case CUSTOM_VANILLA -> ref.storedItem();
            case MMOITEM -> mmoItems.buildItem(ref.itemType(), ref.itemId(), safeAmount);
            case MYTHICMOBS -> mythicItems.buildItem(ref.itemId(), safeAmount);
        };
        if (stack == null || stack.getType() == Material.AIR) return null;
        stack.setAmount(Math.max(1, Math.min(safeAmount, stack.getMaxStackSize())));
        return stack;
    }

    /** Construcción real de recompensas de loot nodes. */
    public ItemStack buildLoot(LootItemReference ref, int amount) {
        if (ref == null) return null;
        int safeAmount = Math.max(1, amount);
        ItemStack stack = switch (ref.type()) {
            case VANILLA -> new ItemStack(ref.vanillaMaterial(), safeAmount);
            case CUSTOM_VANILLA -> ref.storedItem();
            case MMOITEM -> mmoItems.buildLootItem(ref.itemType(), ref.itemId(), safeAmount);
            case MYTHICMOBS -> mythicItems.buildItem(ref.itemId(), safeAmount);
        };
        if (stack == null || stack.getType() == Material.AIR) return null;
        stack.setAmount(Math.max(1, Math.min(safeAmount, stack.getMaxStackSize())));
        return stack;
    }

    private LootItemReference identifyMmoItem(ItemStack stack) {
        try {
            Class<?> clazz = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Object instance = clazz.getField("plugin").get(null);
            if (instance == null) return null;

            Object typeObj = invokeStackMethod(instance, new String[]{"getTypeName", "getType", "getItemType"}, stack);
            Object idObj = invokeStackMethod(instance, new String[]{"getID", "getId", "getItemId"}, stack);
            String type = normalizeType(typeObj);
            String id = idObj instanceof String string ? string : null;
            if (type != null && id != null && !id.isBlank()) return LootItemReference.mmoItem(type, id);

            Object mmoItem = invokeStackMethod(instance, new String[]{"getMMOItem"}, stack);
            if (mmoItem != null) {
                Object maybeType = invokeNoArg(mmoItem, new String[]{"getType", "getItemType"});
                Object maybeId = invokeNoArg(mmoItem, new String[]{"getId", "getID", "getItemId"});
                type = normalizeType(maybeType);
                id = maybeId instanceof String string ? string : null;
                if (type != null && id != null && !id.isBlank()) return LootItemReference.mmoItem(type, id);
            }
        } catch (Throwable throwable) {
            if (debug && plugin.getServer().getPluginManager().isPluginEnabled("MMOItems")) {
                plugin.getLogger().warning("No pude identificar un MMOItem para el editor: " + throwable.getMessage());
            }
        }
        return null;
    }

    private static Object invokeStackMethod(Object target, String[] names, ItemStack stack) {
        for (String name : names) {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isAssignableFrom(ItemStack.class)
                        && !ItemStack.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
                try { return method.invoke(target, stack); } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String[] names) {
        for (String name : names) {
            try { return target.getClass().getMethod(name).invoke(target); }
            catch (Throwable ignored) {}
        }
        return null;
    }

    private static String normalizeType(Object typeObj) {
        if (typeObj == null) return null;
        if (typeObj instanceof String string) return string.toUpperCase(Locale.ROOT);
        for (String methodName : new String[]{"getId", "getID", "getName"}) {
            try {
                Object value = typeObj.getClass().getMethod(methodName).invoke(typeObj);
                if (value instanceof String string && !string.isBlank()) return string.toUpperCase(Locale.ROOT);
            } catch (Throwable ignored) {}
        }
        String raw = typeObj.toString();
        return raw == null || raw.isBlank() ? null : raw.toUpperCase(Locale.ROOT);
    }

    private static boolean isPlainVanilla(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.getType().isItem()) return false;
        // isSimilar ignora amount y compara metadata/componentes. Esto detecta
        // correctamente libros encantados y cualquier otro vanilla con estado.
        return stack.isSimilar(new ItemStack(stack.getType()));
    }
}
