package com.mdvcraft.headores.loot.service;

import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class VirtualLootStorage {
    private final NamespacedKey contentsKey;

    public VirtualLootStorage(NamespacedKey contentsKey) {
        this.contentsKey = contentsKey;
    }

    public void save(PersistentDataContainer pdc, ItemStack[] contents) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) out.writeObject(item);
            out.flush();
            pdc.set(contentsKey, PersistentDataType.BYTE_ARRAY, bytes.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo serializar el inventario virtual", exception);
        }
    }

    public ItemStack[] load(PersistentDataContainer pdc, int expectedSize) {
        byte[] raw = pdc.get(contentsKey, PersistentDataType.BYTE_ARRAY);
        if (raw == null || raw.length == 0) return new ItemStack[expectedSize];
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(raw))) {
            int storedSize = Math.max(0, Math.min(54, in.readInt()));
            ItemStack[] result = new ItemStack[expectedSize];
            for (int i = 0; i < storedSize; i++) {
                Object value = in.readObject();
                if (i < expectedSize && value instanceof ItemStack stack) result[i] = stack;
            }
            return result;
        } catch (Exception exception) {
            return new ItemStack[expectedSize];
        }
    }

    public void clear(PersistentDataContainer pdc) {
        pdc.remove(contentsKey);
    }
}
