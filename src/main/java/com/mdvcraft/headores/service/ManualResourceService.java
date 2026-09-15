package com.mdvcraft.headores.service;

import com.mdvcraft.headores.config.ResourceRegistry;
import com.mdvcraft.headores.model.OreDefinition;
import com.mdvcraft.headores.model.ResourceDefinition;
import com.mdvcraft.headores.model.TreeNodeDefinition;
import com.mdvcraft.headores.tracking.ResourceKeys;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Crea cabezas administrativas colocables y convierte la cabeza colocada en
 * exactamente el mismo recurso PDC que usa la generación natural.
 */
public final class ManualResourceService {
    public static final String KIND_ORE = "ore";
    public static final String KIND_NODE = "node";

    private final JavaPlugin plugin;
    private final ResourceRegistry registry;
    private final ResourceKeys keys;
    private final Map<String, PlayerProfile> profileCache = new HashMap<>();

    public ManualResourceService(JavaPlugin plugin, ResourceRegistry registry, ResourceKeys keys) {
        this.plugin = plugin;
        this.registry = registry;
        this.keys = keys;
    }

    public ItemStack createHead(String kind, String id, int amount) {
        ResourceDefinition resource = resolve(kind, id);
        if (resource == null) return null;

        ItemStack stack = new ItemStack(Material.PLAYER_HEAD, Math.max(1, Math.min(64, amount)));
        ItemMeta rawMeta = stack.getItemMeta();
        if (!(rawMeta instanceof SkullMeta meta)) return null;

        PlayerProfile profile = profile(resource.textureHash());
        if (profile != null) meta.setOwnerProfile(profile);

        String typeName = resource instanceof OreDefinition ? "Veta" : "Nodo de árbol";
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6&l[MDV] &f" + resource.displayName()));
        meta.setLore(List.of(
                ChatColor.translateAlternateColorCodes('&', "&7Recurso administrativo colocable."),
                ChatColor.translateAlternateColorCodes('&', "&7Tipo: &e" + typeName),
                ChatColor.translateAlternateColorCodes('&', "&7ID: &f" + resource.key()),
                "",
                ChatColor.translateAlternateColorCodes('&', "&aAl colocarla se convierte en"),
                ChatColor.translateAlternateColorCodes('&', "&aun recurso MDVHeadOres real.")
        ));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.manualResourceKindKey(), PersistentDataType.STRING,
                resource instanceof OreDefinition ? KIND_ORE : KIND_NODE);
        pdc.set(keys.manualResourceIdKey(), PersistentDataType.STRING, resource.key());
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean convertPlaced(Block block, ItemStack source) {
        if (block == null || source == null || source.getType() != Material.PLAYER_HEAD) return false;
        ItemMeta meta = source.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer itemPdc = meta.getPersistentDataContainer();
        String kind = itemPdc.get(keys.manualResourceKindKey(), PersistentDataType.STRING);
        String id = itemPdc.get(keys.manualResourceIdKey(), PersistentDataType.STRING);
        if (kind == null || id == null) return false;

        ResourceDefinition resource = resolve(kind, id);
        if (resource == null) return false;

        BlockState state = block.getState();
        if (!(state instanceof Skull skull)) return false;

        // La textura suele transferirse desde el ItemStack, pero la volvemos a
        // aplicar para que la cabeza manual sea idéntica a la natural.
        PlayerProfile profile = profile(resource.textureHash());
        if (profile != null) skull.setOwnerProfile(profile);

        PersistentDataContainer blockPdc = skull.getPersistentDataContainer();
        blockPdc.remove(keys.oreKey());
        blockPdc.remove(keys.nodeKey());
        blockPdc.set(resource instanceof OreDefinition ? keys.oreKey() : keys.nodeKey(),
                PersistentDataType.STRING, resource.key());
        blockPdc.set(keys.blockIdKey(), PersistentDataType.STRING, resource.mmoitemsBlockId());
        blockPdc.set(keys.dropTypeKey(), PersistentDataType.STRING, resource.dropType());
        blockPdc.set(keys.dropIdKey(), PersistentDataType.STRING, resource.dropId());
        skull.update(true, false);
        return true;
    }

    public ResourceDefinition findAny(String id) {
        if (id == null) return null;
        String key = id.toLowerCase(Locale.ROOT);
        OreDefinition ore = registry.ore(key);
        if (ore != null) return ore;
        return registry.treeNode(key);
    }

    private ResourceDefinition resolve(String kind, String id) {
        if (kind == null || id == null) return null;
        String key = id.toLowerCase(Locale.ROOT);
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case KIND_ORE, "ores", "veta", "mineral" -> registry.ore(key);
            case KIND_NODE, "tree", "tree-node", "nodo" -> registry.treeNode(key);
            default -> null;
        };
    }

    private PlayerProfile profile(String textureHash) {
        if (textureHash == null || textureHash.isBlank()) return null;
        PlayerProfile cached = profileCache.get(textureHash);
        if (cached != null) return cached;
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(
                    UUID.nameUUIDFromBytes(("mdvheadores-admin:" + textureHash).getBytes(StandardCharsets.UTF_8)), null);
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(URI.create("https://textures.minecraft.net/texture/" + textureHash).toURL());
            profile.setTextures(textures);
            profileCache.put(textureHash, profile);
            return profile;
        } catch (MalformedURLException exception) {
            plugin.getLogger().warning("Textura inválida en cabeza administrativa: " + textureHash);
            return null;
        }
    }

    public void clearCaches() {
        profileCache.clear();
    }
}
