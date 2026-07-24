package com.mdvcraft.headores.generation;

import java.util.Map;

public record QueueSnapshot(
        boolean throttleEnabled,
        int queued,
        int maxQueueSize,
        int ready,
        long queuedTotal,
        long processedTotal,
        long skippedQueueFullTotal,
        long deferredUnloadedTotal,
        long migratedChunks,
        long firstReadyInMillis,
        long oldestAgeMillis,
        long estimatedSeconds,
        Map<String, Integer> byWorld
) {}
