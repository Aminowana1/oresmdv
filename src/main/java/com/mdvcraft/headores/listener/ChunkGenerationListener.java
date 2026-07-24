package com.mdvcraft.headores.listener;

import com.mdvcraft.headores.generation.GenerationQueueManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public final class ChunkGenerationListener implements Listener {
    private final GenerationQueueManager queueManager;

    public ChunkGenerationListener(GenerationQueueManager queueManager) {
        this.queueManager = queueManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        queueManager.handleChunkLoad(event.getChunk(), event.isNewChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        queueManager.handleChunkUnload(event.getChunk());
    }
}
