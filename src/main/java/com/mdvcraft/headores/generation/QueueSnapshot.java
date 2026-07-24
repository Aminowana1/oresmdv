package com.mdvcraft.headores.generation;

import java.util.Map;

public record QueueSnapshot(
        boolean throttleEnabled,
        int queued,
        int maxQueueSize,
        int retryQueued,
        int maxRetrySize,
        int ready,
        long queuedTotal,
        long processedTotal,
        long skippedQueueFullTotal,
        long deferredUnloadedTotal,
        long cancelledOnUnloadTotal,
        long retriedTotal,
        long retryDroppedTotal,
        long migratedChunks,
        long firstReadyInMillis,
        long oldestAgeMillis,
        long estimatedSeconds,
        Map<String, Integer> byWorld,
        boolean scanActive,
        long currentScanChecked,
        long currentScanMissing,
        long scansStartedTotal,
        long scansCompletedTotal,
        long scannedChunksTotal,
        long scanTriggersSkippedTotal
) {}
