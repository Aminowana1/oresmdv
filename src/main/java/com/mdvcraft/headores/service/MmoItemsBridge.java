package com.mdvcraft.headores.service;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class MmoItemsBridge {
    private final JavaPlugin plugin;
    private final boolean debug;

    public MmoItemsBridge(JavaPlugin plugin, boolean debug) {
        this.plugin = plugin;
        this.debug = debug;
    }

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
            if (debug) {
                plugin.getLogger().warning("Error creando item MMOItems " + typeId + ":" + itemId + " -> "
                        + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            }
        }
        return null;
    }

    private Object getType(Class<?> typeClass, String typeId) {
        try {
            return typeClass.getMethod("get", String.class).invoke(null, typeId);
        } catch (Throwable ignored) {
        }
        try {
            return typeClass.getMethod("valueOf", String.class).invoke(null, typeId.toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ItemStack invokeItemStack(Object target, String methodName, Object type, String itemId) {
        Object result = invokeObject(target, methodName, type, itemId);
        return result instanceof ItemStack stack ? stack.clone() : null;
    }

    private Object invokeObject(Object target, String methodName, Object type, String itemId) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != 2) continue;
            try {
                return method.invoke(target, type, itemId);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private ItemStack buildFromMmoItem(Object mmoItem) {
        try {
            Object builder = mmoItem.getClass().getMethod("newBuilder").invoke(mmoItem);
            if (builder != null) {
                Object result = builder.getClass().getMethod("build").invoke(builder);
                if (result instanceof ItemStack stack) return stack.clone();
            }
        } catch (Throwable ignored) {
        }
        try {
            Object result = mmoItem.getClass().getMethod("build").invoke(mmoItem);
            if (result instanceof ItemStack stack) return stack.clone();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object getStaticField(Class<?> type, String name) {
        try {
            Field field = type.getField(name);
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
