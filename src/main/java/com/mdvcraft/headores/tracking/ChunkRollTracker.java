package com.mdvcraft.headores.tracking;

import com.mdvcraft.headores.config.PluginSettings;
import com.mdvcraft.headores.config.ResourceRegistry;
import org.bukkit.Chunk;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkRollTracker {
    private final JavaPlugin plugin;
    private final ResourceKeys keys;
    private final PluginSettings settings;
    private final ResourceRegistry registry;
    private final long legacyAssumedMask;
    private long migratedChunks;

    public ChunkRollTracker(
            JavaPlugin plugin,
            ResourceKeys keys,
            PluginSettings settings,
            ResourceRegistry registry
    ) {
        this.plugin = plugin;
        this.keys = keys;
        this.settings = settings;
        this.registry = registry;
        this.legacyAssumedMask = registry.maskForResourceKeys(
                settings.tracking().legacyAssumedResources(), plugin);
    }

    /**
     * Inicializa el seguimiento de un chunk al cargarlo.
     * Los chunks existentes sin máscara reciben únicamente los bits de los recursos legados.
     * Los chunks nuevos comienzan en cero para tirar todos los recursos activos.
     */
    public long prepareChunk(Chunk chunk, boolean newChunk) {
        if (!settings.tracking().enabled()) return 0L;

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        Long stored = pdc.get(keys.rollMaskKey(), PersistentDataType.LONG);
        if (stored != null) return stored;

        boolean hasLegacyMarker = pdc.has(keys.legacyGeneratedChunkKey(), PersistentDataType.STRING);
        long initialMask = 0L;
        if (hasLegacyMarker || (!newChunk && settings.tracking().assumeLegacyResourcesOnExistingChunks())) {
            initialMask = legacyAssumedMask;
            migratedChunks++;
        }

        pdc.set(keys.rollMaskKey(), PersistentDataType.LONG, initialMask);
        if (hasLegacyMarker) pdc.remove(keys.legacyGeneratedChunkKey());
        return initialMask;
    }

    public long readMask(Chunk chunk) {
        if (!settings.tracking().enabled()) return 0L;
        Long stored = chunk.getPersistentDataContainer().get(keys.rollMaskKey(), PersistentDataType.LONG);
        return stored == null ? 0L : stored;
    }

    public void writeMask(Chunk chunk, long mask) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (!settings.tracking().enabled()) {
            pdc.set(keys.legacyGeneratedChunkKey(), PersistentDataType.STRING, "true");
            return;
        }
        pdc.set(keys.rollMaskKey(), PersistentDataType.LONG, mask);
    }

    public boolean hasMissingActiveRolls(Chunk chunk) {
        if (!settings.tracking().enabled()) {
            return !chunk.getPersistentDataContainer().has(
                    keys.legacyGeneratedChunkKey(), PersistentDataType.STRING);
        }
        return hasMissingActiveRolls(readMask(chunk));
    }

    public boolean hasMissingActiveRolls(long mask) {
        if (!settings.tracking().enabled()) return true;
        long active = registry.activeMask();
        return (mask & active) != active;
    }

    public boolean hasRolled(long mask, long resourceMask) {
        return (mask & resourceMask) != 0L;
    }

    public long markRolled(long mask, long resourceMask) {
        return mask | resourceMask;
    }

    public long activeMask() {
        return registry.activeMask();
    }

    public long legacyAssumedMask() {
        return legacyAssumedMask;
    }

    public long migratedChunks() {
        return migratedChunks;
    }

    public int completedResourceCount(long mask) {
        return Long.bitCount(mask & registry.activeMask());
    }

    public int activeResourceCount() {
        return Long.bitCount(registry.activeMask());
    }
}
