package com.mdvcraft.headores.tracking;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class ResourceKeys {
    private final NamespacedKey oreKey;
    private final NamespacedKey nodeKey;
    private final NamespacedKey blockIdKey;
    private final NamespacedKey dropTypeKey;
    private final NamespacedKey dropIdKey;
    private final NamespacedKey legacyGeneratedChunkKey;
    private final NamespacedKey rollMaskKey;
    private final NamespacedKey lootNodeKey;
    private final NamespacedKey lootGeneratedKey;
    private final NamespacedKey lootContentsKey;

    public ResourceKeys(Plugin plugin) {
        oreKey = new NamespacedKey(plugin, "ore_key");
        nodeKey = new NamespacedKey(plugin, "tree_node_key");
        blockIdKey = new NamespacedKey(plugin, "mmoitems_block_id");
        dropTypeKey = new NamespacedKey(plugin, "drop_type");
        dropIdKey = new NamespacedKey(plugin, "drop_id");
        legacyGeneratedChunkKey = new NamespacedKey(plugin, "generated_chunk");
        rollMaskKey = new NamespacedKey(plugin, "roll_mask");
        lootNodeKey = new NamespacedKey(plugin, "loot_node_key");
        lootGeneratedKey = new NamespacedKey(plugin, "loot_generated");
        lootContentsKey = new NamespacedKey(plugin, "loot_contents");
    }

    public NamespacedKey oreKey() { return oreKey; }
    public NamespacedKey nodeKey() { return nodeKey; }
    public NamespacedKey blockIdKey() { return blockIdKey; }
    public NamespacedKey dropTypeKey() { return dropTypeKey; }
    public NamespacedKey dropIdKey() { return dropIdKey; }
    public NamespacedKey legacyGeneratedChunkKey() { return legacyGeneratedChunkKey; }
    public NamespacedKey rollMaskKey() { return rollMaskKey; }
    public NamespacedKey lootNodeKey() { return lootNodeKey; }
    public NamespacedKey lootGeneratedKey() { return lootGeneratedKey; }
    public NamespacedKey lootContentsKey() { return lootContentsKey; }
}
