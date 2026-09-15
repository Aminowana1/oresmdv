package com.mdvcraft.headores.loot.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

public final class LootItemReference {
    private final LootItemType type;
    private final Material vanillaMaterial;
    private final String itemType;
    private final String itemId;
    private final byte[] storedItemBytes;

    private LootItemReference(LootItemType type, Material vanillaMaterial, String itemType, String itemId, byte[] storedItemBytes) {
        this.type = type;
        this.vanillaMaterial = vanillaMaterial;
        this.itemType = itemType;
        this.itemId = itemId;
        this.storedItemBytes = storedItemBytes == null ? null : storedItemBytes.clone();
    }

    public static LootItemReference vanilla(Material material) {
        return new LootItemReference(LootItemType.VANILLA, material, null, material.name(), null);
    }

    /**
     * Vanilla con metadata/componentes propios. Se normaliza a amount=1; la cantidad
     * de la recompensa se mantiene en LootEntry.
     */
    public static LootItemReference customVanilla(ItemStack item) {
        if (item == null || item.getType().isAir()) throw new IllegalArgumentException("item");
        ItemStack stored = item.clone();
        stored.setAmount(1);
        return new LootItemReference(LootItemType.CUSTOM_VANILLA, stored.getType(), null, null, stored.serializeAsBytes());
    }

    public static LootItemReference customVanilla(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("bytes");
        ItemStack decoded = ItemStack.deserializeBytes(bytes);
        Material material = decoded == null ? null : decoded.getType();
        return new LootItemReference(LootItemType.CUSTOM_VANILLA, material, null, null, bytes);
    }

    public static LootItemReference mmoItem(String type, String id) {
        return new LootItemReference(LootItemType.MMOITEM, null, type, id, null);
    }

    public static LootItemReference mythic(String id) {
        return new LootItemReference(LootItemType.MYTHICMOBS, null, null, id, null);
    }

    public LootItemType type() { return type; }
    public Material vanillaMaterial() { return vanillaMaterial; }
    public String itemType() { return itemType; }
    public String itemId() { return itemId; }
    public byte[] storedItemBytes() { return storedItemBytes == null ? null : storedItemBytes.clone(); }

    public ItemStack storedItem() {
        if (storedItemBytes == null || storedItemBytes.length == 0) return null;
        try { return ItemStack.deserializeBytes(storedItemBytes.clone()); }
        catch (Throwable ignored) { return null; }
    }

    public String stableKey() {
        return switch (type) {
            case VANILLA -> "VANILLA:" + vanillaMaterial.name();
            case CUSTOM_VANILLA -> "CUSTOM_VANILLA:" + (vanillaMaterial == null ? "ITEM" : vanillaMaterial.name()) + ":" + Arrays.hashCode(storedItemBytes);
            case MMOITEM -> "MMOITEM:" + itemType + ":" + itemId;
            case MYTHICMOBS -> "MYTHICMOBS:" + itemId;
        };
    }
}
