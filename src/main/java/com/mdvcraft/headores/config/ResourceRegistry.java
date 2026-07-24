package com.mdvcraft.headores.config;

import com.mdvcraft.headores.model.IntRange;
import com.mdvcraft.headores.model.MmoCoreXpSettings;
import com.mdvcraft.headores.model.OreDefinition;
import com.mdvcraft.headores.model.ResourceDefinition;
import com.mdvcraft.headores.model.TreeNodeDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ResourceRegistry {
    private static final Pattern TEXTURE_URL_PATTERN = Pattern.compile("https?://textures\\.minecraft\\.net/texture/([a-zA-Z0-9]+)");
    private static final Map<String, Integer> KNOWN_TRACKING_BITS = Map.ofEntries(
            Map.entry("viridita", 0),
            Map.entry("amatista_encantada", 1),
            Map.entry("umbrita", 2),
            Map.entry("nimbrel", 3),
            Map.entry("oricalco", 4),
            Map.entry("mithril", 5),
            Map.entry("nudo_vivo", 6),
            Map.entry("tronco_corrupto", 7),
            Map.entry("nudo_runico", 8),
            Map.entry("savia_aurea", 9),
            Map.entry("nudo_primordial", 10)
    );

    private final Map<String, OreDefinition> ores;
    private final Map<String, TreeNodeDefinition> treeNodes;
    private final List<ResourceDefinition> allResources;
    private final long activeMask;

    private ResourceRegistry(
            Map<String, OreDefinition> ores,
            Map<String, TreeNodeDefinition> treeNodes,
            List<ResourceDefinition> allResources,
            long activeMask
    ) {
        this.ores = Map.copyOf(ores);
        this.treeNodes = Map.copyOf(treeNodes);
        this.allResources = List.copyOf(allResources);
        this.activeMask = activeMask;
    }

    public static ResourceRegistry load(JavaPlugin plugin, PluginSettings settings) {
        FileConfiguration cfg = plugin.getConfig();
        File mmoItemsFile = new File(plugin.getServer().getWorldContainer(), settings.mmoitemsBlockFile());
        YamlConfiguration mmoBlocks = YamlConfiguration.loadConfiguration(mmoItemsFile);
        if (!mmoItemsFile.exists()) {
            plugin.getLogger().warning("No se encontró el archivo de bloques de MMOItems: " + mmoItemsFile.getPath());
        }

        Map<String, OreDefinition> ores = new LinkedHashMap<>();
        Map<String, TreeNodeDefinition> nodes = new LinkedHashMap<>();
        Set<Integer> usedBits = new HashSet<>();

        ConfigurationSection oreSection = cfg.getConfigurationSection("ores");
        if (oreSection != null) {
            for (String key : oreSection.getKeys(false)) {
                ConfigurationSection sec = oreSection.getConfigurationSection(key);
                if (sec == null || !sec.getBoolean("enabled", true)) continue;
                OreDefinition ore = loadOre(plugin, mmoBlocks, key, sec, usedBits);
                if (ore != null) ores.put(key, ore);
            }
        } else {
            plugin.getLogger().warning("No hay sección 'ores' en config.yml.");
        }

        ConfigurationSection nodeSection = cfg.getConfigurationSection("tree-nodes");
        if (nodeSection != null) {
            for (String key : nodeSection.getKeys(false)) {
                ConfigurationSection sec = nodeSection.getConfigurationSection(key);
                if (sec == null || !sec.getBoolean("enabled", true)) continue;
                TreeNodeDefinition node = loadNode(plugin, mmoBlocks, key, sec, usedBits);
                if (node != null) nodes.put(key, node);
            }
        }

        List<ResourceDefinition> all = new ArrayList<>();
        all.addAll(ores.values());
        all.addAll(nodes.values());
        all.sort(Comparator.comparingInt(ResourceDefinition::trackingBit));

        long activeMask = 0L;
        for (ResourceDefinition resource : all) activeMask |= resource.trackingMask();

        return new ResourceRegistry(ores, nodes, all, activeMask);
    }

    private static OreDefinition loadOre(
            JavaPlugin plugin,
            YamlConfiguration mmoBlocks,
            String key,
            ConfigurationSection sec,
            Set<Integer> usedBits
    ) {
        Common common = loadCommon(plugin, mmoBlocks, key, sec, usedBits, false);
        if (common == null) return null;

        List<Material> replace = readMaterials(plugin, sec, "replace", key);
        if (replace.isEmpty()) {
            plugin.getLogger().warning("La veta '" + key + "' no tiene bloques válidos para reemplazar.");
            return null;
        }

        Set<Material> avoided = new HashSet<>();
        if (sec.getBoolean("avoid-near-vanilla-ores", true)) avoided.addAll(defaultVanillaOres());
        avoided.addAll(readMaterials(plugin, sec, "avoid-near-materials", key));

        OreDefinition ore = new OreDefinition(
                key,
                common.trackingBit,
                common.mmoitemsBlockId,
                common.textureHash,
                common.displayName,
                common.worlds,
                common.dropType,
                common.dropId,
                common.dropAmount,
                common.preventVanillaDrops,
                common.ignoreSilkTouch,
                common.dropNaturally,
                common.breakSound,
                common.failSound,
                common.fallbackCommand,
                common.applyPhysicsOnPlace,
                common.mmocoreXp,
                replace,
                sec.getInt("min-y", -48),
                sec.getInt("max-y", 48),
                sec.getDouble("chunk-chance", 0.21D),
                sec.getInt("veins-per-chunk", 1),
                sec.getInt("vein-min", 1),
                sec.getInt("vein-max", 2),
                sec.getString("placement", "FLOOR_HEAD").toUpperCase(Locale.ROOT),
                sec.getDouble("required-pickaxe-power", 0.0D),
                avoided
        );
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Veta cargada: " + key + " bit=" + ore.trackingBit() + " -> "
                    + ore.mmoitemsBlockId() + " drop " + ore.dropType() + ":" + ore.dropId());
        }
        return ore;
    }

    private static TreeNodeDefinition loadNode(
            JavaPlugin plugin,
            YamlConfiguration mmoBlocks,
            String key,
            ConfigurationSection sec,
            Set<Integer> usedBits
    ) {
        Common common = loadCommon(plugin, mmoBlocks, key, sec, usedBits, true);
        if (common == null) return null;

        List<Material> attachTo = readMaterials(plugin, sec, "attach-to", key);
        if (attachTo.isEmpty()) {
            plugin.getLogger().warning("El nodo de árbol '" + key + "' no tiene bloques válidos en attach-to.");
            return null;
        }

        TreeNodeDefinition node = new TreeNodeDefinition(
                key,
                common.trackingBit,
                common.mmoitemsBlockId,
                common.textureHash,
                common.displayName,
                common.worlds,
                common.dropType,
                common.dropId,
                common.dropAmount,
                common.preventVanillaDrops,
                common.ignoreSilkTouch,
                common.dropNaturally,
                common.breakSound,
                common.failSound,
                common.fallbackCommand,
                common.applyPhysicsOnPlace,
                common.mmocoreXp,
                attachTo,
                sec.getInt("min-y", 50),
                sec.getInt("max-y", 120),
                sec.getDouble("chunk-chance", 0.12D),
                sec.getInt("nodes-per-chunk", 1),
                sec.getDouble("required-axe-power", 0.0D),
                sec.getBoolean("only-on-surface-logs", true)
        );
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Nodo cargado: " + key + " bit=" + node.trackingBit() + " -> "
                    + node.mmoitemsBlockId() + " drop " + node.dropType() + ":" + node.dropId());
        }
        return node;
    }

    private static Common loadCommon(
            JavaPlugin plugin,
            YamlConfiguration mmoBlocks,
            String key,
            ConfigurationSection sec,
            Set<Integer> usedBits,
            boolean node
    ) {
        int trackingBit = sec.contains("tracking-bit")
                ? sec.getInt("tracking-bit", -1)
                : KNOWN_TRACKING_BITS.getOrDefault(key, -1);
        if (trackingBit < 0 || trackingBit > 62) {
            plugin.getLogger().warning("El recurso '" + key + "' necesita tracking-bit entre 0 y 62. Se omitió.");
            return null;
        }
        if (!usedBits.add(trackingBit)) {
            plugin.getLogger().warning("tracking-bit duplicado (" + trackingBit + ") en '" + key + "'. Se omitió.");
            return null;
        }

        String miBlockId = sec.getString("mmoitems-block-id", "").trim();
        if (miBlockId.isEmpty()) {
            plugin.getLogger().warning("El recurso '" + key + "' no tiene mmoitems-block-id.");
            return null;
        }

        String rawTexture = sec.getString("skull-texture", null);
        if (sec.getBoolean("texture-from-mmoitems", true)) {
            String fromMi = mmoBlocks.getString(miBlockId + ".base.skull-texture.value", null);
            if (fromMi != null && !fromMi.isBlank()) rawTexture = fromMi;
        }
        String textureHash = extractTextureHash(rawTexture);
        if (textureHash == null || textureHash.isBlank()) {
            plugin.getLogger().warning("No pude leer textura para '" + key + "' usando el bloque MMOItems '" + miBlockId + "'.");
            return null;
        }

        String displayName = sec.getString("display-name", null);
        if (sec.getBoolean("name-from-mmoitems", true)) {
            String fromMi = mmoBlocks.getString(miBlockId + ".base.name", null);
            if (fromMi != null && !fromMi.isBlank()) displayName = fromMi;
        }
        if (displayName == null || displayName.isBlank()) displayName = key;

        return new Common(
                trackingBit,
                miBlockId,
                textureHash,
                displayName,
                new HashSet<>(sec.getStringList("worlds")),
                sec.getString("drop-type", "MATERIAL"),
                sec.getString("drop-id", ""),
                Math.max(1, sec.getInt("drop-amount", 1)),
                sec.getBoolean("prevent-vanilla-drops", true),
                sec.getBoolean("ignore-silk-touch", true),
                sec.getBoolean("drop-naturally", true),
                sec.getString("break-sound", node ? "BLOCK_WOOD_BREAK" : "BLOCK_AMETHYST_BLOCK_BREAK"),
                sec.getString("fail-sound", "BLOCK_NOTE_BLOCK_BASS"),
                sec.getString("fallback-command", null),
                sec.getBoolean("apply-physics-on-place", true),
                loadXp(sec.getConfigurationSection("mmocore-xp"))
        );
    }

    private static MmoCoreXpSettings loadXp(ConfigurationSection sec) {
        if (sec == null) return MmoCoreXpSettings.disabled();
        return new MmoCoreXpSettings(
                sec.getBoolean("enabled", false),
                sec.getString("profession-id", ""),
                parseRange(sec.getString("profession-amount", "0")),
                parseRange(sec.getString("main-amount", "0")),
                sec.getBoolean("split", false)
        );
    }

    private static IntRange parseRange(String raw) {
        if (raw == null || raw.isBlank()) return IntRange.zero();
        try {
            String normalized = raw.trim();
            int separator = normalized.indexOf('-', 1);
            if (separator > 0) {
                int a = Integer.parseInt(normalized.substring(0, separator).trim());
                int b = Integer.parseInt(normalized.substring(separator + 1).trim());
                return new IntRange(Math.min(a, b), Math.max(a, b));
            }
            int value = Integer.parseInt(normalized);
            return new IntRange(value, value);
        } catch (NumberFormatException ignored) {
            return IntRange.zero();
        }
    }

    private static List<Material> readMaterials(JavaPlugin plugin, ConfigurationSection sec, String path, String owner) {
        List<Material> materials = new ArrayList<>();
        for (String raw : sec.getStringList(path)) {
            Material material = Material.matchMaterial(raw);
            if (material != null && material.isBlock()) {
                materials.add(material);
            } else {
                plugin.getLogger().warning("Material inválido en " + path + " de '" + owner + "': " + raw);
            }
        }
        return materials;
    }

    private static Set<Material> defaultVanillaOres() {
        Set<Material> result = new HashSet<>();
        String[] names = {
                "COAL_ORE", "DEEPSLATE_COAL_ORE", "COPPER_ORE", "DEEPSLATE_COPPER_ORE",
                "IRON_ORE", "DEEPSLATE_IRON_ORE", "GOLD_ORE", "DEEPSLATE_GOLD_ORE",
                "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE", "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE",
                "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE", "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE",
                "NETHER_GOLD_ORE", "NETHER_QUARTZ_ORE", "ANCIENT_DEBRIS"
        };
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) result.add(material);
        }
        return result;
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

        if (trimmed.matches("[a-zA-Z0-9]{20,}")) return trimmed;
        return null;
    }

    public Map<String, OreDefinition> ores() { return ores; }
    public Map<String, TreeNodeDefinition> treeNodes() { return treeNodes; }
    public List<ResourceDefinition> allResources() { return allResources; }
    public long activeMask() { return activeMask; }
    public OreDefinition ore(String key) { return ores.get(key); }
    public TreeNodeDefinition treeNode(String key) { return treeNodes.get(key); }

    public long maskForResourceKeys(List<String> keys, JavaPlugin plugin) {
        long mask = 0L;
        Map<String, ResourceDefinition> byKey = new HashMap<>();
        for (ResourceDefinition resource : allResources) byKey.put(resource.key(), resource);
        for (String key : keys) {
            ResourceDefinition resource = byKey.get(key);
            if (resource == null) {
                plugin.getLogger().warning("Recurso legado desconocido en generation-tracking.legacy-assumed-resources: " + key);
                continue;
            }
            mask |= resource.trackingMask();
        }
        return mask;
    }

    private record Common(
            int trackingBit,
            String mmoitemsBlockId,
            String textureHash,
            String displayName,
            Set<String> worlds,
            String dropType,
            String dropId,
            int dropAmount,
            boolean preventVanillaDrops,
            boolean ignoreSilkTouch,
            boolean dropNaturally,
            String breakSound,
            String failSound,
            String fallbackCommand,
            boolean applyPhysicsOnPlace,
            MmoCoreXpSettings mmocoreXp
    ) {}
}
