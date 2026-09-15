package com.mdvcraft.headores.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record PluginSettings(
        boolean debug,
        boolean debugLogGeneratedChunks,
        boolean debugLogUnloadedChunks,
        String mmoitemsBlockFile,
        boolean generateOnNewChunks,
        boolean vanillaPickaxesHavePower,
        boolean vanillaAxesHavePower,
        String defaultFallbackCommand,
        String mmocoreExpCommand,
        String noPowerMessage,
        String noAxePowerMessage,
        GenerationThrottle throttle,
        ResourceProtection protection,
        GenerationTracking tracking,
        LootNodesSettings lootNodes,
        ChunkyCompatibility chunky
) {
    public static PluginSettings load(JavaPlugin plugin) {
        FileConfiguration cfg = plugin.getConfig();

        Set<Material> fluids = new HashSet<>();
        List<String> configuredFluids = cfg.getStringList("resource-protection.fluids");
        if (configuredFluids.isEmpty()) configuredFluids = List.of("WATER", "LAVA");
        for (String raw : configuredFluids) {
            Material material = Material.matchMaterial(raw);
            if (material == Material.WATER || material == Material.LAVA) {
                fluids.add(material);
            } else {
                plugin.getLogger().warning("Fluido inválido en resource-protection.fluids: " + raw);
            }
        }

        GenerationThrottle throttle = new GenerationThrottle(
                cfg.getBoolean("generation-throttle.enabled", true),
                Math.max(0, cfg.getInt("generation-throttle.delay-after-chunk-load-ticks", 40)),
                Math.max(1, cfg.getInt("generation-throttle.interval-ticks", 1)),
                Math.max(1, cfg.getInt("generation-throttle.chunks-per-interval", 8)),
                Math.max(64, cfg.getInt("generation-throttle.max-queue-size", 15000)),
                cfg.getBoolean("generation-throttle.skip-if-queue-full", true),
                cfg.getBoolean("generation-throttle.command-generate-uses-queue", true),
                Math.max(0, cfg.getInt("generation-throttle.rescan-loaded-chunks-interval-ticks", 1200)),
                Math.max(1, cfg.getInt("generation-throttle.rescan-loaded-chunks-per-tick", 128)),
                positiveDouble(cfg.getDouble("generation-throttle.rescan-max-millis-per-tick", 1.0D), 1.0D),
                Math.max(1, cfg.getInt("generation-throttle.retry-chunks-per-tick", 64)),
                positiveDouble(cfg.getDouble("generation-throttle.retry-max-millis-per-tick", 0.5D), 0.5D),
                positiveDouble(cfg.getDouble("generation-throttle.max-processing-millis-per-run", 1.5D), 1.5D),
                Math.max(1, cfg.getInt("generation-throttle.max-dequeues-per-run", 16)),
                Math.max(64, cfg.getInt("generation-throttle.max-retry-size", 30000))
        );

        ResourceProtection protection = new ResourceProtection(
                cfg.getBoolean("resource-protection.prevent-fluid-destruction", true),
                Set.copyOf(fluids),
                cfg.getBoolean("resource-protection.prevent-explosion-destruction", true),
                cfg.getBoolean("resource-protection.prevent-piston-destruction", true),
                cfg.getBoolean("resource-protection.prevent-physics-destruction", true)
        );

        GenerationTracking tracking = new GenerationTracking(
                cfg.getBoolean("generation-tracking.enabled", cfg.getBoolean("mark-generated-chunks", true)),
                cfg.getBoolean("generation-tracking.process-existing-chunks-on-load", true),
                cfg.getBoolean("generation-tracking.assume-legacy-resources-on-existing-chunks", true),
                List.copyOf(cfg.getStringList("generation-tracking.legacy-assumed-resources"))
        );

        String legacyLootFile = cfg.getString("loot-nodes.legacy-file",
                cfg.getString("loot-nodes.file", "lootnodes.yml"));

        // Compatibilidad 1.3.4: si la lista todavía no existe, conserva el
        // comportamiento anterior (equipamiento no identificado). En cuanto
        // el administrador define `types`, incluso una lista vacía se respeta.
        Set<String> defaultUnidentifiedTypes = Set.of(
                "SWORD", "DAGGER", "HAMMER", "BOW", "CROSSBOW", "SPEAR", "GAUNTLET", "WHIP",
                "STAFF", "WAND", "TOME", "LUTE", "MUSKET", "GREATSWORD", "LONG_SWORD", "KATANA",
                "THRUSTING_SWORD", "AXE", "GREATAXE", "HALBERD", "LANCE", "GREATHAMMER", "GREATSTAFF",
                "STAVE", "GREATBOW", "SHIELD", "ARMOR", "TOOL", "ACCESSORY", "ORNAMENT", "RING",
                "AMULET", "AMULETO", "BRACELET", "GLOVES", "ARTIFACT", "CATALYST", "OFF_CATALYST",
                "MAIN_CATALYST", "ARMAS_MAGICAS", "SOPORTE_MAGICO"
        );
        Set<String> unidentifiedTypes = new LinkedHashSet<>();
        if (cfg.isSet("loot-nodes.mmoitems.unidentified.types")) {
            for (String raw : cfg.getStringList("loot-nodes.mmoitems.unidentified.types")) {
                if (raw == null || raw.isBlank()) continue;
                unidentifiedTypes.add(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
            }
        } else {
            unidentifiedTypes.addAll(defaultUnidentifiedTypes);
        }

        LootNodesSettings lootNodes = new LootNodesSettings(
                cfg.getBoolean("loot-nodes.enabled", true),
                cfg.getString("loot-nodes.directory", "lootnodes"),
                legacyLootFile,
                cfg.getBoolean("loot-nodes.editor.enabled", true),
                Math.max(1, Math.min(45, cfg.getInt("loot-nodes.editor.rewards-per-page", 45))),
                cfg.getBoolean("loot-nodes.mmoitems.unidentified.enabled", true),
                Set.copyOf(unidentifiedTypes)
        );

        ChunkyCompatibility chunky = new ChunkyCompatibility(
                cfg.getBoolean("chunky-compatibility.enabled", true),
                cfg.getBoolean("chunky-compatibility.listen-chunk-populate", true)
        );

        return new PluginSettings(
                cfg.getBoolean("debug", false),
                cfg.getBoolean("debug-options.log-generated-chunks", true),
                cfg.getBoolean("debug-options.log-unloaded-chunks", false),
                cfg.getString("mmoitems-block-file", "plugins/MMOItems/item/block.yml"),
                cfg.getBoolean("generate-on-new-chunks", true),
                cfg.getBoolean("vanilla-pickaxes-have-power", true),
                cfg.getBoolean("vanilla-axes-have-power", true),
                cfg.getString("default-fallback-command", "mi give %drop_type% %drop_id% %player% %amount%"),
                cfg.getString("mmocore-exp-command", "mmocore admin exp give %player% %target% %amount% %split%"),
                cfg.getString("no-power-message", "&6&l[&5&lMDVCRAFT&6&l]  &4»  &cTu pico no tiene suficiente poder para minar esta veta. &7Requiere: &f%required%&7. Tu poder: &f%power%&c."),
                cfg.getString("no-axe-power-message", "&6&l[&5&lMDVCRAFT&6&l]  &4»  &cTu hacha no tiene suficiente poder para extraer este recurso. &7Requiere: &f%required%&7. Tu poder: &f%power%&c."),
                throttle,
                protection,
                tracking,
                lootNodes,
                chunky
        );
    }

    private static double positiveDouble(double value, double fallback) {
        if (!Double.isFinite(value) || value <= 0.0D) return fallback;
        return value;
    }

    public record GenerationThrottle(
            boolean enabled,
            int delayAfterChunkLoadTicks,
            int intervalTicks,
            int chunksPerInterval,
            int maxQueueSize,
            boolean skipIfQueueFull,
            boolean commandGenerateUsesQueue,
            int rescanLoadedChunksIntervalTicks,
            int rescanLoadedChunksPerTick,
            double rescanMaxMillisPerTick,
            int retryChunksPerTick,
            double retryMaxMillisPerTick,
            double maxProcessingMillisPerRun,
            int maxDequeuesPerRun,
            int maxRetrySize
    ) {}

    public record LootNodesSettings(
            boolean enabled,
            String directoryName,
            String legacyFileName,
            boolean editorEnabled,
            int editorRewardsPerPage,
            boolean mmoItemsUnidentifiedEnabled,
            Set<String> mmoItemsUnidentifiedTypes
    ) {
        public LootNodesSettings {
            mmoItemsUnidentifiedTypes = Set.copyOf(
                    mmoItemsUnidentifiedTypes == null ? Set.of() : mmoItemsUnidentifiedTypes);
        }
    }

    public record ChunkyCompatibility(
            boolean enabled,
            boolean listenChunkPopulate
    ) {}

    public record ResourceProtection(
            boolean preventFluidDestruction,
            Set<Material> fluids,
            boolean preventExplosionDestruction,
            boolean preventPistonDestruction,
            boolean preventPhysicsDestruction
    ) {}

    public record GenerationTracking(
            boolean enabled,
            boolean processExistingChunksOnLoad,
            boolean assumeLegacyResourcesOnExistingChunks,
            List<String> legacyAssumedResources
    ) {}
}
