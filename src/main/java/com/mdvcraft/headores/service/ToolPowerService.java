package com.mdvcraft.headores.service;

import com.mdvcraft.headores.config.PluginSettings;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToolPowerService {
    private static final Pattern PICKAXE_LORE = Pattern.compile(
            "(?i)(poder\\s+de\\s+pico|pickaxe\\s+power|pickaxe-power).*?([+-]?\\d+(?:[\\.,]\\d+)?)");
    private static final Pattern AXE_LORE = Pattern.compile(
            "(?i)(poder\\s+de\\s+hacha|axe\\s+power|axe-power).*?([+-]?\\d+(?:[\\.,]\\d+)?)");

    private final JavaPlugin plugin;
    private final PluginSettings settings;

    public ToolPowerService(JavaPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public double pickaxePower(ItemStack item) {
        return readPower(item, "pickaxe", PICKAXE_LORE,
                new String[]{"MMOITEMS_PICKAXE_POWER", "PICKAXE_POWER", "pickaxe-power", "pickaxe_power"},
                settings.vanillaPickaxesHavePower() ? vanillaPickaxePower(item == null ? Material.AIR : item.getType()) : 0.0D);
    }

    public double axePower(ItemStack item) {
        return readPower(item, "axe", AXE_LORE,
                new String[]{"MMOITEMS_AXE_POWER", "AXE_POWER", "axe-power", "axe_power"},
                settings.vanillaAxesHavePower() ? vanillaAxePower(item == null ? Material.AIR : item.getType()) : 0.0D);
    }

    private double readPower(ItemStack item, String keyword, Pattern lorePattern, String[] nbtTags, double vanillaFallback) {
        if (item == null || item.getType() == Material.AIR) return 0.0D;
        Double pdc = readFromPdc(item, keyword);
        if (pdc != null) return pdc;
        Double nbt = readFromMmoItemsNbt(item, nbtTags);
        if (nbt != null) return nbt;
        Double lore = readFromLore(item, lorePattern);
        return lore != null ? lore : vanillaFallback;
    }

    private Double readFromPdc(ItemStack item, String keyword) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (NamespacedKey key : pdc.getKeys()) {
            String raw = (key.getNamespace() + ":" + key.getKey()).toLowerCase(Locale.ROOT);
            if (!raw.contains(keyword) || !raw.contains("power")) continue;
            Double value = readNumber(pdc, key);
            if (value != null) return value;
        }
        return null;
    }

    private Double readNumber(PersistentDataContainer pdc, NamespacedKey key) {
        try {
            Double value = pdc.get(key, PersistentDataType.DOUBLE);
            if (value != null) return value;
        } catch (Throwable ignored) {}
        try {
            Integer value = pdc.get(key, PersistentDataType.INTEGER);
            if (value != null) return value.doubleValue();
        } catch (Throwable ignored) {}
        try {
            Long value = pdc.get(key, PersistentDataType.LONG);
            if (value != null) return value.doubleValue();
        } catch (Throwable ignored) {}
        try {
            String value = pdc.get(key, PersistentDataType.STRING);
            if (value != null) return Double.parseDouble(value.replace(',', '.'));
        } catch (Throwable ignored) {}
        return null;
    }

    private Double readFromMmoItemsNbt(ItemStack item, String[] tags) {
        try {
            Class<?> nbtClass = Class.forName("net.Indyuce.mmoitems.api.item.nbt.NBTItem");
            Object nbtItem = createNbtItem(nbtClass, item);
            if (nbtItem == null) return null;
            for (String tag : tags) {
                Boolean has = invokeBoolean(nbtItem, "hasTag", tag);
                if (has != null && !has) continue;
                Double doubleValue = invokeNumber(nbtItem, "getDouble", tag);
                if (doubleValue != null && doubleValue > 0) return doubleValue;
                Double integerValue = invokeNumber(nbtItem, "getInteger", tag);
                if (integerValue == null) integerValue = invokeNumber(nbtItem, "getInt", tag);
                if (integerValue != null && integerValue > 0) return integerValue;
                String stringValue = invokeString(nbtItem, "getString", tag);
                if (stringValue != null && !stringValue.isBlank()) {
                    try {
                        return Double.parseDouble(stringValue.replace(',', '.'));
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (ClassNotFoundException ignored) {
            // MMOItems ausente: se usa lore/PDC/vanilla.
        } catch (Throwable throwable) {
            if (settings.debug()) {
                plugin.getLogger().warning("No pude leer NBT de MMOItems: " + throwable.getClass().getSimpleName());
            }
        }
        return null;
    }

    private Object createNbtItem(Class<?> nbtClass, ItemStack item) {
        try {
            Constructor<?> constructor = nbtClass.getConstructor(ItemStack.class);
            return constructor.newInstance(item);
        } catch (Throwable ignored) {}
        try {
            return nbtClass.getMethod("get", ItemStack.class).invoke(null, item);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Boolean invokeBoolean(Object target, String methodName, String argument) {
        try {
            Object result = target.getClass().getMethod(methodName, String.class).invoke(target, argument);
            return result instanceof Boolean value ? value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Double invokeNumber(Object target, String methodName, String argument) {
        try {
            Object result = target.getClass().getMethod(methodName, String.class).invoke(target, argument);
            return result instanceof Number value ? value.doubleValue() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String invokeString(Object target, String methodName, String argument) {
        try {
            Object result = target.getClass().getMethod(methodName, String.class).invoke(target, argument);
            return result == null ? null : result.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Double readFromLore(ItemStack item, Pattern pattern) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) return null;
        for (String line : meta.getLore()) {
            String clean = ChatColor.stripColor(line);
            if (clean == null) continue;
            Matcher matcher = pattern.matcher(clean);
            if (!matcher.find()) continue;
            try {
                return Double.parseDouble(matcher.group(2).replace(',', '.'));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private double vanillaPickaxePower(Material material) {
        return switch (material) {
            case WOODEN_PICKAXE -> 1;
            case STONE_PICKAXE -> 2;
            case IRON_PICKAXE -> 3;
            case GOLDEN_PICKAXE -> 2;
            case DIAMOND_PICKAXE -> 4;
            case NETHERITE_PICKAXE -> 5;
            default -> 0;
        };
    }

    private double vanillaAxePower(Material material) {
        return switch (material) {
            case WOODEN_AXE -> 1;
            case STONE_AXE -> 2;
            case IRON_AXE -> 3;
            case GOLDEN_AXE -> 2;
            case DIAMOND_AXE -> 4;
            case NETHERITE_AXE -> 5;
            default -> 0;
        };
    }
}
