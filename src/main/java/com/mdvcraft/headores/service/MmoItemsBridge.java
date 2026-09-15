package com.mdvcraft.headores.service;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Set;

public final class MmoItemsBridge {
    private static final Set<String> EQUIPMENT_TYPE_IDS = Set.of(
            "SWORD", "DAGGER", "HAMMER", "BOW", "CROSSBOW", "SPEAR", "GAUNTLET", "WHIP",
            "STAFF", "WAND", "TOME", "LUTE", "MUSKET", "GREATSWORD", "LONG_SWORD", "KATANA",
            "THRUSTING_SWORD", "AXE", "GREATAXE", "HALBERD", "LANCE", "GREATHAMMER", "GREATSTAFF",
            "STAVE", "GREATBOW", "SHIELD", "ARMOR", "TOOL", "ACCESSORY", "ORNAMENT", "RING",
            "AMULET", "AMULETO", "BRACELET", "GLOVES", "ARTIFACT", "CATALYST", "OFF_CATALYST",
            "MAIN_CATALYST", "ARMAS_MAGICAS", "SOPORTE_MAGICO"
    );

    private final JavaPlugin plugin;
    private final boolean debug;

    public MmoItemsBridge(JavaPlugin plugin, boolean debug) {
        this.plugin = plugin;
        this.debug = debug;
    }

    /** Construcción normal, usada por drops de recursos y previews del editor. */
    public ItemStack buildItem(String typeId, String itemId, int amount) {
        try {
            Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Object mmoItemsPlugin = getStaticField(mmoItemsClass, "plugin");
            if (mmoItemsPlugin == null) return null;

            Class<?> typeClass = Class.forName("net.Indyuce.mmoitems.api.Type");
            Object type = getType(typeClass, typeId);
            if (type == null) {
                if (debug) plugin.getLogger().warning("Tipo MMOItems no encontrado: " + typeId);
                return null;
            }

            ItemStack direct = invokeItemStack(mmoItemsPlugin, "getItem", type, itemId);
            if (direct != null) {
                direct.setAmount(Math.max(1, amount));
                return direct;
            }

            Object mmoItem = invokeObject(mmoItemsPlugin, "getMMOItem", type, itemId);
            if (mmoItem != null) {
                ItemStack built = buildFromMmoItem(mmoItem);
                if (built != null) {
                    built.setAmount(Math.max(1, amount));
                    return built;
                }
            }
        } catch (Throwable throwable) {
            debug("Error creando item MMOItems " + typeId + ":" + itemId, throwable);
        }
        return null;
    }

    /**
     * Construcción exclusiva de loot nodes. El equipamiento se genera desde el
     * template para que MMOItems tire sus modifiers aleatorios y luego se vuelve
     * no identificado, preservando dentro del objeto la versión ya aleatorizada.
     */
    public ItemStack buildLootItem(String typeId, String itemId, int amount) {
        try {
            Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Object mmoItemsPlugin = getStaticField(mmoItemsClass, "plugin");
            if (mmoItemsPlugin == null) return null;

            Class<?> typeClass = Class.forName("net.Indyuce.mmoitems.api.Type");
            Object type = getType(typeClass, typeId);
            if (type == null) return null;

            if (!isEquipmentType(type, typeId)) {
                return buildItem(typeId, itemId, amount);
            }

            ItemStack randomized = buildRandomizedFromTemplate(mmoItemsPlugin, type, itemId);
            if (randomized == null) {
                debug("No pude generar la tirada aleatoria de equipamiento " + typeId + ":" + itemId,
                        new IllegalStateException("MMOItemTemplate no produjo un item"));
                return null;
            }

            ItemStack unidentified = buildUnidentified(type, randomized);
            if (unidentified == null) {
                if (debug) {
                    plugin.getLogger().warning("Se rechazó el loot de equipamiento " + typeId + ":" + itemId
                            + " porque MMOItems no pudo convertirlo a no identificado.");
                }
                return null;
            }
            unidentified.setAmount(Math.max(1, amount));
            return unidentified;
        } catch (Throwable throwable) {
            debug("Error generando loot MMOItems aleatorio/no identificado " + typeId + ":" + itemId, throwable);
            return null;
        }
    }

    private ItemStack buildRandomizedFromTemplate(Object mmoItemsPlugin, Object type, String itemId) {
        try {
            Object templates = invokeNoArg(mmoItemsPlugin, "getTemplates");
            if (templates == null) return null;
            Object template = invokeCompatible(templates, "getTemplate", type, itemId);
            if (template == null) return null;

            // MMOItemTemplate#newBuilder() es la ruta de generación real: aplica
            // los modifiers disponibles según la configuración de MMOItems.
            Object templateBuilder = invokeNoArg(template, "newBuilder");
            if (templateBuilder == null) return null;
            Object mmoItem = invokeNoArg(templateBuilder, "build");
            if (mmoItem == null) return null;
            return buildFromMmoItem(mmoItem);
        } catch (Throwable throwable) {
            debug("No pude construir MMOItem desde template aleatorio", throwable);
            return null;
        }
    }

    private ItemStack buildUnidentified(Object type, ItemStack identified) {
        try {
            Object nbtItem = wrapNbtItem(identified);
            if (nbtItem == null) return null;
            Object template = invokeNoArg(type, "getUnidentifiedTemplate");
            if (template == null) return null;
            Object builder = invokeCompatible(template, "newBuilder", nbtItem);
            if (builder == null) return null;
            Object result = invokeNoArg(builder, "build");
            return result instanceof ItemStack stack ? stack.clone() : null;
        } catch (Throwable throwable) {
            debug("No pude convertir MMOItem a no identificado", throwable);
            return null;
        }
    }

    private Object wrapNbtItem(ItemStack stack) {
        // MythicLib moderno: NBTItem.get(ItemStack)
        try {
            Class<?> nbtClass = Class.forName("io.lumine.mythic.lib.api.item.NBTItem");
            for (Method method : nbtClass.getMethods()) {
                if (!method.getName().equals("get") || !Modifier.isStatic(method.getModifiers())
                        || method.getParameterCount() != 1) continue;
                Class<?> parameter = method.getParameterTypes()[0];
                if (!parameter.isAssignableFrom(ItemStack.class) && !ItemStack.class.isAssignableFrom(parameter)) continue;
                Object result = method.invoke(null, stack);
                if (result != null) return result;
            }
        } catch (Throwable ignored) {
        }

        // Compatibilidad con builds que exponen el wrapper desde MythicLib.plugin.
        try {
            Class<?> mythicLibClass = Class.forName("io.lumine.mythic.lib.MythicLib");
            Object mythicLib = getStaticField(mythicLibClass, "plugin");
            Object version = invokeNoArg(mythicLib, "getVersion");
            Object wrapper = invokeNoArg(version, "getWrapper");
            return invokeCompatible(wrapper, "getNBTItem", stack);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isEquipmentType(Object type, String typeId) {
        try {
            Object value = invokeNoArg(type, "isWeapon");
            if (value instanceof Boolean bool && bool) return true;
        } catch (Throwable ignored) {
        }
        try {
            Object source = invokeNoArg(type, "getModifierSource");
            if (source != null) {
                Object equipment = invokeNoArg(source, "isEquipment");
                if (equipment instanceof Boolean bool && bool) return true;
                Object weapon = invokeNoArg(source, "isWeapon");
                if (weapon instanceof Boolean bool && bool) return true;
            }
        } catch (Throwable ignored) {
        }

        String normalized = normalizeId(typeId);
        if (EQUIPMENT_TYPE_IDS.contains(normalized)) return true;

        try {
            Object supertype = invokeNoArg(type, "getSupertype");
            Object id = supertype == null ? null : invokeNoArg(supertype, "getId");
            return id instanceof String string && EQUIPMENT_TYPE_IDS.contains(normalizeId(string));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object getType(Class<?> typeClass, String typeId) {
        try {
            return typeClass.getMethod("get", String.class).invoke(null, typeId);
        } catch (Throwable ignored) {
        }
        try {
            return typeClass.getMethod("valueOf", String.class).invoke(null, normalizeId(typeId));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ItemStack invokeItemStack(Object target, String methodName, Object type, String itemId) {
        Object result = invokeObject(target, methodName, type, itemId);
        return result instanceof ItemStack stack ? stack.clone() : null;
    }

    private Object invokeObject(Object target, String methodName, Object type, String itemId) {
        return invokeCompatible(target, methodName, type, itemId);
    }

    private ItemStack buildFromMmoItem(Object mmoItem) {
        try {
            Object builder = invokeNoArg(mmoItem, "newBuilder");
            if (builder != null) {
                Object result = invokeNoArg(builder, "build");
                if (result instanceof ItemStack stack) return stack.clone();
            }
        } catch (Throwable ignored) {
        }
        try {
            Object result = invokeNoArg(mmoItem, "build");
            if (result instanceof ItemStack stack) return stack.clone();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeCompatible(Object target, String methodName, Object... args) {
        if (target == null) return null;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) continue;
            Class<?>[] params = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < params.length; i++) {
                if (args[i] == null) continue;
                if (!box(params[i]).isAssignableFrom(args[i].getClass())) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) continue;
            try {
                return method.invoke(target, args);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private Object getStaticField(Class<?> type, String name) {
        try {
            Field field = type.getField(name);
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String normalizeId(String raw) {
        return raw == null ? "" : raw.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private void debug(String prefix, Throwable throwable) {
        if (!debug) return;
        plugin.getLogger().warning(prefix + " -> " + throwable.getClass().getSimpleName()
                + ": " + throwable.getMessage());
    }
}
