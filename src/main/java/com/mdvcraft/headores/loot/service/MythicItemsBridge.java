package com.mdvcraft.headores.loot.service;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Optional;

public final class MythicItemsBridge {
    private final JavaPlugin plugin;
    private final boolean debug;

    public MythicItemsBridge(JavaPlugin plugin, boolean debug) {
        this.plugin = plugin;
        this.debug = debug;
    }

    public String identify(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        try {
            Object manager = itemManager();
            if (manager == null) return null;
            for (String methodName : new String[]{"getMythicTypeFromItem", "getItemName", "getItemId", "getItemID"}) {
                Object value = invokeOneArg(manager, methodName, stack);
                String id = unwrapString(value);
                if (id != null && !id.isBlank()) return id;
            }
        } catch (Throwable throwable) {
            debug("Error identificando MythicItem: " + throwable.getMessage());
        }
        return null;
    }

    public ItemStack buildItem(String itemId, int amount) {
        try {
            Object manager = itemManager();
            if (manager == null) return null;

            Object direct = invokeTwoArgs(manager, "getItemStack", itemId, Math.max(1, amount));
            ItemStack stack = unwrapItemStack(direct);
            if (stack == null) stack = unwrapItemStack(invokeOneArg(manager, "getItemStack", itemId));
            if (stack != null) {
                stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));
                return stack;
            }

            Object item = invokeOneArg(manager, "getItem", itemId);
            item = unwrapOptional(item);
            if (item != null) {
                for (String methodName : new String[]{"generateItemStack", "getItemStack", "build"}) {
                    Object generated = invokeOneArg(item, methodName, Math.max(1, amount));
                    stack = unwrapItemStack(generated);
                    if (stack != null) {
                        stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));
                        return stack;
                    }
                }
                Object generated = invokeNoArg(item, "generateItemStack");
                stack = unwrapItemStack(generated);
                if (stack != null) {
                    stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));
                    return stack;
                }
            }
        } catch (Throwable throwable) {
            debug("Error creando MythicItem " + itemId + ": " + throwable.getMessage());
        }
        return null;
    }

    private Object itemManager() throws Exception {
        Class<?> mythic = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
        Object instance = mythic.getMethod("inst").invoke(null);
        return instance == null ? null : instance.getClass().getMethod("getItemManager").invoke(instance);
    }

    private Object invokeOneArg(Object target, String name, Object arg) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
            Class<?> param = method.getParameterTypes()[0];
            if (arg != null && !wrap(param).isInstance(arg)) continue;
            try { return method.invoke(target, arg); } catch (Throwable ignored) {}
        }
        return null;
    }


    private Object invokeTwoArgs(Object target, String name, Object first, Object second) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 2) continue;
            Class<?>[] params = method.getParameterTypes();
            if (first != null && !wrap(params[0]).isInstance(first)) continue;
            if (second != null && !wrap(params[1]).isInstance(second)) continue;
            try { return method.invoke(target, first, second); } catch (Throwable ignored) {}
        }
        return null;
    }

    private Object invokeNoArg(Object target, String name) {
        try { return target.getClass().getMethod(name).invoke(target); }
        catch (Throwable ignored) { return null; }
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Object unwrapOptional(Object value) {
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    private static String unwrapString(Object value) {
        value = unwrapOptional(value);
        return value instanceof String string ? string : null;
    }

    private static ItemStack unwrapItemStack(Object value) {
        value = unwrapOptional(value);
        return value instanceof ItemStack stack ? stack.clone() : null;
    }

    private void debug(String message) {
        if (debug) plugin.getLogger().warning(message);
    }
}
