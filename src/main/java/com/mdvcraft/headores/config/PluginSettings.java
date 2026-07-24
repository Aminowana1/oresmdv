package com.mdvcraft.headores.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
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
        GenerationTracking tracking
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
                Math.max(1, cfg.getInt("generation-throttle.interval-ticks", 6)),
                Math.max(1, cfg.getInt("generation-throttle.chunks-per-interval", 1)),
                Math.max(64, cfg.getInt("generation-throttle.max-queue-size", 15000)),
                cfg.getBoolean("generation-throttle.skip-if-queue-full", true),
                cfg.getBoolean("generation-throttle.command-generate-uses-queue", true),
                Math.max(0, cfg.getInt("generation-throttle.rescan-loaded-chunks-interval-ticks", 1200))
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
                tracking
        );
    }

    public record GenerationThrottle(
            boolean enabled,
            int delayAfterChunkLoadTicks,
            int intervalTicks,
            int chunksPerInterval,
            int maxQueueSize,
            boolean skipIfQueueFull,
            boolean commandGenerateUsesQueue,
            int rescanLoadedChunksIntervalTicks
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
