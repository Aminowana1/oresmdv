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
import java.util.Arrays;
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
    private static final List<String> DEFAULT_NODE_FILES = List.of(
            "bolsa_abandonada.yml", "vasija_antigua.yml", "cofre_antiguo.yml"
    );

    private final JavaPlugin plugin;
    private final File directory;
    private final Map<String, File> nodeFiles;
    private final Map<String, YamlConfiguration> nodeYamls;
    private final Map<String, LootNodeDefinition> nodes;
    private List<LootNodeDefinition> activeNodes;
    private final long activeMask;

    private LootNodeRegistry(JavaPlugin plugin, File directory,
                             Map<String, File> nodeFiles,
                             Map<String, YamlConfiguration> nodeYamls,
                             Map<String, LootNodeDefinition> nodes,
                             List<LootNodeDefinition> activeNodes, long activeMask) {
        this.plugin = plugin;
        this.directory = directory;
        this.nodeFiles = nodeFiles;
        this.nodeYamls = nodeYamls;
        this.nodes = nodes;
        this.activeNodes = activeNodes;
        this.activeMask = activeMask;
    }

    public static LootNodeRegistry load(JavaPlugin plugin, PluginSettings settings, ResourceRegistry resources) {
        String directoryName = settings.lootNodes().directoryName();
        if (directoryName == null || directoryName.isBlank()) directoryName = "lootnodes";
        File directory = new File(plugin.getDataFolder(), directoryName);
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().severe("No pude crear la carpeta de loot nodes: " + directory.getPath());
        }

        // Instalaciones viejas: primero migra el archivo combinado para no
        // permitir que ejemplos nuevos tapen archivos del usuario.
        migrateLegacyFile(plugin, settings, directory);

        // Instalaciones nuevas: si después de migrar sigue vacío, copia ejemplos.
        if (listYamlFiles(directory).length == 0) {
            copyBundledDefaults(plugin, directoryName);
        }

        Map<String, LootNodeDefinition> nodes = new LinkedHashMap<>();
        Map<String, File> nodeFiles = new LinkedHashMap<>();
        Map<String, YamlConfiguration> nodeYamls = new LinkedHashMap<>();
        Set<Integer> usedBits = new HashSet<>();
        for (ResourceDefinition resource : resources.allResources()) usedBits.add(resource.trackingBit());

        if (settings.lootNodes().enabled()) {
            File[] files = listYamlFiles(directory);
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String key = yaml.getString("id", stripExtension(file.getName())).trim().toLowerCase(Locale.ROOT);
                if (key.isBlank()) {
                    plugin.getLogger().warning("Loot node sin id válido en " + file.getName() + ". Se omitió.");
                    continue;
                }
                if (nodes.containsKey(key)) {
                    plugin.getLogger().warning("Loot node duplicado '" + key + "' en " + file.getName() + ". Se omitió.");
                    continue;
                }
                LootNodeDefinition definition = loadNode(plugin, key, yaml, usedBits);
                if (definition == null) continue;
                nodes.put(key, definition);
                nodeFiles.put(key, file);
                nodeYamls.put(key, yaml);
            }
        }

        List<LootNodeDefinition> sorted = new ArrayList<>(nodes.values());
        sorted.sort(Comparator.comparingInt(LootNodeDefinition::trackingBit));
        Map<String, LootNodeDefinition> sortedNodes = new LinkedHashMap<>();
        Map<String, File> sortedFiles = new LinkedHashMap<>();
        Map<String, YamlConfiguration> sortedYamls = new LinkedHashMap<>();
        for (LootNodeDefinition node : sorted) {
            sortedNodes.put(node.key(), node);
            sortedFiles.put(node.key(), nodeFiles.get(node.key()));
            sortedYamls.put(node.key(), nodeYamls.get(node.key()));
        }

        List<LootNodeDefinition> activeNodes = List.copyOf(sortedNodes.values().stream()
                .filter(LootNodeDefinition::enabled)
                .toList());
        long activeMask = 0L;
        for (LootNodeDefinition node : activeNodes) activeMask |= node.trackingMask();
        return new LootNodeRegistry(plugin, directory, sortedFiles, sortedYamls, sortedNodes, activeNodes, activeMask);
    }

    private static void copyBundledDefaults(JavaPlugin plugin, String directoryName) {
        for (String name : DEFAULT_NODE_FILES) {
            String resource = directoryName.replace('\\', '/') + "/" + name;
            try {
                plugin.saveResource(resource, false);
            } catch (IllegalArgumentException ignored) {
                // El JAR puede provenir de una instalación migrada sin ejemplos; no es fatal.
            }
        }
    }

    private static void migrateLegacyFile(JavaPlugin plugin, PluginSettings settings, File directory) {
        String legacyName = settings.lootNodes().legacyFileName();
        if (legacyName == null || legacyName.isBlank()) legacyName = "lootnodes.yml";
        File legacy = new File(plugin.getDataFolder(), legacyName);
        if (!legacy.exists() || !legacy.isFile()) return;

        YamlConfiguration legacyYaml = YamlConfiguration.loadConfiguration(legacy);
        ConfigurationSection root = legacyYaml.getConfigurationSection("loot-nodes");
        if (root == null) return;

        int migrated = 0;
        for (String key : root.getKeys(false)) {
            ConfigurationSection source = root.getConfigurationSection(key);
            if (source == null) continue;
            File target = new File(directory, safeFileName(key) + ".yml");
            if (target.exists()) continue;
            YamlConfiguration out = new YamlConfiguration();
            out.set("id", key.toLowerCase(Locale.ROOT));
            copySection(source, out, "");
            try {
                out.save(target);
                migrated++;
            } catch (IOException exception) {
                plugin.getLogger().severe("No pude migrar loot node '" + key + "' a " + target.getName() + ": " + exception.getMessage());
            }
        }
        if (migrated > 0) {
            plugin.getLogger().info("Migrados " + migrated + " loot nodes desde " + legacy.getName()
                    + " a la carpeta " + directory.getName() + "/. El archivo antiguo se conserva como respaldo.");
        }
    }

    private static void copySection(ConfigurationSection source, YamlConfiguration target, String prefix) {
        for (String key : source.getKeys(false)) {
            Object value = source.get(key);
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (value instanceof ConfigurationSection child) copySection(child, target, path);
            else target.set(path, value);
        }
    }

    private static File[] listYamlFiles(File directory) {
        File[] files = directory.listFiles(file -> file.isFile()
                && (file.getName().toLowerCase(Locale.ROOT).endsWith(".yml")
                || file.getName().toLowerCase(Locale.ROOT).endsWith(".yaml")));
        return files == null ? new File[0] : files;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String safeFileName(String key) {
        String safe = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return safe.isBlank() ? "loot_node" : safe;
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
            case CUSTOM_VANILLA -> {
                String encoded = entry.getString("item-data", "").trim();
                if (encoded.isEmpty()) yield null;
                try {
                    byte[] bytes = Base64.getDecoder().decode(encoded);
                    yield LootItemReference.customVanilla(bytes);
                } catch (Throwable ignored) {
                    yield null;
                }
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
        YamlConfiguration yaml = nodeYamls.get(key);
        File file = nodeFiles.get(key);
        if (current == null || yaml == null || file == null) return false;

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

        writeLootToYaml(yaml, updated);
        try {
            yaml.save(file);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("No pude guardar " + file.getName() + ": " + exception.getMessage());
            return false;
        }
    }

    private static void writeLootToYaml(YamlConfiguration yaml, LootNodeDefinition node) {
        String base = "loot";
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
                case CUSTOM_VANILLA -> {
                    byte[] bytes = entry.item().storedItemBytes();
                    yaml.set(path + ".item-data", bytes == null ? null : Base64.getEncoder().encodeToString(bytes));
                    if (entry.item().vanillaMaterial() != null) {
                        yaml.set(path + ".material", entry.item().vanillaMaterial().name());
                    }
                }
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

    public LootNodeDefinition node(String key) {
        return key == null ? null : nodes.get(key.toLowerCase(Locale.ROOT));
    }
    public List<LootNodeDefinition> allNodes() { return List.copyOf(nodes.values()); }
    public List<LootNodeDefinition> activeNodes() { return activeNodes; }
    public Map<String, LootNodeDefinition> nodes() { return Map.copyOf(nodes); }
    public long activeMask() { return activeMask; }
    public File directory() { return directory; }
    public File fileFor(String key) { return nodeFiles.get(key); }
}
