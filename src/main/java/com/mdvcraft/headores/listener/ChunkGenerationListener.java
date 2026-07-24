package com.mdvcraft.headores.listener;

import com.mdvcraft.headores.generation.GenerationQueueManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public final class ChunkGenerationListener implements Listener {
    private final GenerationQueueManager queueManager;

    public ChunkGenerationListener(GenerationQueueManager queueManager) {
        this.queueManager = queueManager;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        queueManager.handleChunkLoad(event.getChunk(), event.isNewChunk());
    }
}
