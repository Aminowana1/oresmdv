package com.mdvcraft.headores.loot.config;

import com.mdvcraft.headores.config.PluginSettings;
import com.mdvcraft.headores.config.ResourceRegistry;
import com.mdvcraft.headores.loot.model.LootContainerType;
import com.mdvcraft.headores.loot.model.LootEntry;
import com.mdvcraft.headores.loot.model.LootItemReference;
import com.mdvcraft.headores.loot.model.LootItemType;
import com.mdvcraft.headores.loot.model.LootNodeDefinition;
import com.mdvcraft.headores.loot.model.LootTableDefinition;
import com.mdvcraft.headores.model.ResourceDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LootNodeRegistry {
    private static final Pattern TEXTURE_URL_PATTERN = Pattern.compile("https?://textures\\.minecraft\\.net/texture/([a-zA-Z0-9]+)");
    private static final List<String> DEFAULT_REPLACEABLE_SPACE = List.of(
            "AIR", "CAVE_AIR", "VOID_AIR", "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN",
            "DEAD_BUSH", "SNOW", "VINE", "GLOW_LICHEN"
    );

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<String, LootNodeDefinition> nodes;
    private List<LootNodeDefinition> activeNodes;
    private final long activeMask;

    private LootNodeRegistry(JavaPlugin plugin, File file, YamlConfiguration yaml,
                             Map<String, LootNodeDefinition> nodes,
                             List<LootNodeDefinition> activeNodes, long activeMask) {
        this.plugin = plugin;
        this.file = file;
        this.yaml = yaml;
        this.nodes = nodes;
        this.activeNodes = activeNodes;
        this.activeMask = activeMask;
    }

    public static LootNodeRegistry load(JavaPlugin plugin, PluginSettings settings, ResourceRegistry resources) {
        String fileName = settings.lootNodes().fileName();
        if (fileName == null || fileName.isBlank()) fileName = "lootnodes.yml";
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            try {
                plugin.saveResource(fileName, false);
            } catch (IllegalArgumentException ignored) {
                try {
                    if (file.getParentFile() != null) file.getParentFile().mkdirs();
                    file.createNewFile();
                } catch (IOException exception) {
                    plugin.getLogger().severe("No pude crear " + fileName + ": " + exception.getMessage());
                }
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, LootNodeDefinition> nodes = new LinkedHashMap<>();
        Set<Integer> usedBits = new HashSet<>();
        for (ResourceDefinition resource : resources.allResources()) usedBits.add(resource.trackingBit());

        if (settings.lootNodes().enabled()) {
            ConfigurationSection root = yaml.getConfigurationSection("loot-nodes");
            if (root != null) {
                for (String key : root.getKeys(false)) {
                    ConfigurationSection sec = root.getConfigurationSection(key);
                    if (sec == null) continue;
                    LootNodeDefinition definition = loadNode(plugin, key, sec, usedBits);
                    if (definition != null) nodes.put(key, definition);
                }
            }
        }

        List<LootNodeDefinition> sorted = new ArrayList<>(nodes.values());
        sorted.sort(Comparator.comparingInt(LootNodeDefinition::trackingBit));
        nodes.clear();
        for (LootNodeDefinition node : sorted) nodes.put(node.key(), node);

        List<LootNodeDefinition> activeNodes = List.copyOf(nodes.values().stream()
                .filter(LootNodeDefinition::enabled)
                .toList());
        long activeMask = 0L;
        for (LootNodeDefinition node : activeNodes) activeMask |= node.trackingMask();
        return new LootNodeRegistry(plugin, file, yaml, nodes, activeNodes, activeMask);
    }

    private static LootNodeDefinition loadNode(JavaPlugin plugin, String key, ConfigurationSection sec,
                                               Set<Integer> usedBits) {
        boolean enabled = sec.getBoolean("enabled", true);
        int bit = sec.getInt("tracking-bit", -1);
        if (bit < 0 || bit > 62) {
            plugin.getLogger().warning("Loot node '" + key + "' necesita tracking-bit entre 0 y 62. Se omitió.");
            return null;
        }
        if (!usedBits.add(bit)) {
            plugin.getLogger().warning("tracking-bit duplicado (" + bit + ") en loot node '" + key + "'. Se omitió.");
            return null;
        }

        ConfigurationSection container = sec.getConfigurationSection("container");
        if (container == null) {
            plugin.getLogger().warning("Loot node '" + key + "' no tiene sección container.");
            return null;
        }
        LootContainerType type;
        try {
            type = LootContainerType.valueOf(container.getString("type", "PLAYER_HEAD").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Tipo de contenedor inválido en loot node '" + key + "'.");
            return null;
        }

        String textureHash = null;
        if (type == LootContainerType.PLAYER_HEAD) {
            textureHash = extractTextureHash(container.getString("texture", ""));
            if (textureHash == null && enabled) {
                plugin.getLogger().warning("Loot node PLAYER_HEAD '" + key + "' no tiene una textura válida. Se omitió.");
                return null;
            }
        }

        ConfigurationSection generation = sec.getConfigurationSection("generation");
        if (generation == null) {
            plugin.getLogger().warning("Loot node '" + key + "' no tiene sección generation.");
            return null;
        }

        Set<Material> replaceable = new HashSet<>();
        List<String> rawReplaceable = generation.getStringList("replaceable-space");
        if (rawReplaceable.isEmpty()) rawReplaceable = DEFAULT_REPLACEABLE_SPACE;
        for (String raw : rawReplaceable) {
            Material material = Material.matchMaterial(raw);
            if (material != null) replaceable.add(material);
            else plugin.getLogger().warning("Material reemplazable inválido en loot node '" + key + "': " + raw);
        }
        replaceable.add(Material.AIR);
        replaceable.add(Material.CAVE_AIR);
        replaceable.add(Material.VOID_AIR);

        LootTableDefinition loot = readLoot(sec.getConfigurationSection("loot"), type);
        return new LootNodeDefinition(
                key,
                enabled,
                bit,
                sec.getString("display-name", key),
                type,
                textureHash,
                container.getInt("inventory-size", type == LootContainerType.PLAYER_HEAD ? 18 : 27),
                new HashSet<>(generation.getStringList("worlds")),
                generation.getInt("min-y", -55),
                generation.getInt("max-y", 120),
                generation.getDouble("chunk-chance", 0.02D),
                generation.getInt("nodes-per-chunk", 1),
                generation.getInt("placement-attempts", 48),
                replaceable,
                loot
        );
    }

    private static LootTableDefinition readLoot(ConfigurationSection sec, LootContainerType type) {
        if (sec == null) return new LootTableDefinition(1, 1, 1, true, List.of());
        int min = sec.getInt("rolls.min", type.singleDrop() ? 1 : 3);
        int max = sec.getInt("rolls.max", type.singleDrop() ? 1 : 6);
        if (type.singleDrop()) min = max = 1;
        int maxSlots = sec.getInt("max-slots", type.singleDrop() ? 1 : 6);
        boolean merge = sec.getBoolean("merge-same-items", true);
        List<LootEntry> entries = new ArrayList<>();
        ConfigurationSection entriesSec = sec.getConfigurationSection("entries");
        if (entriesSec != null) {
            for (String entryKey : entriesSec.getKeys(false)) {
                ConfigurationSection entry = entriesSec.getConfigurationSection(entryKey);
                if (entry == null) continue;
                LootItemReference ref = readReference(entry);
                if (ref == null) continue;
                entries.add(new LootEntry(
                        entryKey,
                        ref,
                        entry.getDouble("weight", 1.0D),
                        entry.getInt("amount.min", 1),
                        entry.getInt("amount.max", 1),
                        entry.getBoolean("repeatable", true)
                ));
            }
        }
        return new LootTableDefinition(min, max, maxSlots, merge, entries);
    }

    private static LootItemReference readReference(ConfigurationSection entry) {
        LootItemType type;
        try {
            type = LootItemType.valueOf(entry.getString("type", "VANILLA").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return switch (type) {
            case VANILLA -> {
                Material material = Material.matchMaterial(entry.getString("material", ""));
                yield material == null || material == Material.AIR ? null : LootItemReference.vanilla(material);
            }
            case MMOITEM -> {
                String itemType = entry.getString("item-type", "").trim();
                String id = entry.getString("item-id", "").trim();
                yield itemType.isEmpty() || id.isEmpty() ? null : LootItemReference.mmoItem(itemType, id);
            }
            case MYTHICMOBS -> {
                String id = entry.getString("item-id", "").trim();
                yield id.isEmpty() ? null : LootItemReference.mythic(id);
            }
        };
    }

    public synchronized boolean updateLoot(String key, LootTableDefinition loot) {
        LootNodeDefinition current = nodes.get(key);
        if (current == null) return false;
        LootNodeDefinition updated = current.withLoot(current.containerType().singleDrop()
                ? new LootTableDefinition(1, 1, 1, loot.mergeSameItems(), loot.entries())
                : loot);
        nodes.put(key, updated);
        if (updated.enabled()) {
            List<LootNodeDefinition> refreshed = new ArrayList<>(activeNodes);
            for (int i = 0; i < refreshed.size(); i++) {
                if (refreshed.get(i).key().equals(key)) {
                    refreshed.set(i, updated);
                    break;
                }
            }
            activeNodes = List.copyOf(refreshed);
        }
        writeLootToYaml(updated);
        try {
            yaml.save(file);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("No pude guardar " + file.getName() + ": " + exception.getMessage());
            return false;
        }
    }

    private void writeLootToYaml(LootNodeDefinition node) {
        String base = "loot-nodes." + node.key() + ".loot";
        LootTableDefinition loot = node.loot();
        yaml.set(base + ".rolls.min", node.containerType().singleDrop() ? 1 : loot.rollsMin());
        yaml.set(base + ".rolls.max", node.containerType().singleDrop() ? 1 : loot.rollsMax());
        yaml.set(base + ".max-slots", node.containerType().singleDrop() ? 1 : loot.maxSlots());
        yaml.set(base + ".merge-same-items", loot.mergeSameItems());
        yaml.set(base + ".entries", null);
        for (LootEntry entry : loot.entries()) {
            String path = base + ".entries." + entry.key();
            yaml.set(path + ".type", entry.item().type().name());
            switch (entry.item().type()) {
                case VANILLA -> yaml.set(path + ".material", entry.item().vanillaMaterial().name());
                case MMOITEM -> {
                    yaml.set(path + ".item-type", entry.item().itemType());
                    yaml.set(path + ".item-id", entry.item().itemId());
                }
                case MYTHICMOBS -> yaml.set(path + ".item-id", entry.item().itemId());
            }
            yaml.set(path + ".weight", entry.weight());
            yaml.set(path + ".amount.min", entry.minAmount());
            yaml.set(path + ".amount.max", entry.maxAmount());
            yaml.set(path + ".repeatable", entry.repeatable());
        }
    }

    private static String extractTextureHash(String rawTexture) {
        if (rawTexture == null || rawTexture.isBlank()) return null;
        String trimmed = rawTexture.trim();
        Matcher direct = TEXTURE_URL_PATTERN.matcher(trimmed);
        if (direct.find()) return direct.group(1);
        try {
            String decoded = new String(Base64.getDecoder().decode(trimmed), StandardCharsets.UTF_8);
            Matcher matcher = TEXTURE_URL_PATTERN.matcher(decoded);
            if (matcher.find()) return matcher.group(1);
        } catch (IllegalArgumentException ignored) {
        }
        return trimmed.matches("[a-zA-Z0-9]{20,}") ? trimmed : null;
    }

    public LootNodeDefinition node(String key) { return nodes.get(key); }
    public List<LootNodeDefinition> allNodes() { return List.copyOf(nodes.values()); }
    public List<LootNodeDefinition> activeNodes() { return activeNodes; }
    public Map<String, LootNodeDefinition> nodes() { return Map.copyOf(nodes); }
    public long activeMask() { return activeMask; }
    public File file() { return file; }
}
