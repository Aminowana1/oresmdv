package com.mdvcraft.headores.generation;

import com.mdvcraft.headores.config.PluginSettings;
import com.mdvcraft.headores.tracking.ChunkRollTracker;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GenerationQueueManager {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final ChunkRollTracker tracker;
    private final ResourceGenerator generator;
    private final Deque<PendingChunk> queue = new ArrayDeque<>();
    private final Set<String> queuedKeys = new HashSet<>();

    private BukkitTask processingTask;
    private BukkitTask rescanTask;
    private long queuedTotal;
    private long processedTotal;
    private long skippedQueueFullTotal;
    private long deferredUnloadedTotal;

    public GenerationQueueManager(
            JavaPlugin plugin,
            PluginSettings settings,
            ChunkRollTracker tracker,
            ResourceGenerator generator
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.tracker = tracker;
        this.generator = generator;
    }

    public void start() {
        stopTasksOnly();
        if (settings.throttle().enabled()) {
            processingTask = Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    this::processQueue,
                    20L,
                    settings.throttle().intervalTicks()
            );
        }

        int rescanTicks = settings.throttle().rescanLoadedChunksIntervalTicks();
        if (rescanTicks > 0) {
            rescanTask = Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    this::enqueueMissingLoadedChunks,
                    Math.max(40L, rescanTicks),
                    rescanTicks
            );
        }

        // Migra y completa los chunks que ya estaban cargados durante /reload o arranque.
        Bukkit.getScheduler().runTaskLater(plugin, this::enqueueMissingLoadedChunks, 40L);
    }

    public void stop() {
        stopTasksOnly();
        queue.clear();
        queuedKeys.clear();
        generator.clearCaches();
    }

    private void stopTasksOnly() {
        if (processingTask != null) {
            processingTask.cancel();
            processingTask = null;
        }
        if (rescanTask != null) {
            rescanTask.cancel();
            rescanTask = null;
        }
    }

    public void handleChunkLoad(Chunk chunk, boolean newChunk) {
        tracker.prepareChunk(chunk, newChunk);
        if (newChunk) {
            if (!settings.generateOnNewChunks()) return;
        } else if (!settings.tracking().processExistingChunksOnLoad()) {
            return;
        }

        if (!tracker.hasMissingActiveRolls(chunk)) return;
        if (settings.throttle().enabled()) {
            enqueue(chunk, false, false);
        } else {
            generator.generate(chunk, false, false);
            processedTotal++;
        }
    }

    public boolean enqueue(Chunk chunk, boolean force, boolean fromCommand) {
        if (chunk == null) return false;
        tracker.prepareChunk(chunk, false);
        if (!force && !tracker.hasMissingActiveRolls(chunk)) return false;

        String key = chunkKey(chunk.getWorld(), chunk.getX(), chunk.getZ());
        if (!queuedKeys.add(key)) return false;

        if (queue.size() >= settings.throttle().maxQueueSize() && settings.throttle().skipIfQueueFull()) {
            queuedKeys.remove(key);
            skippedQueueFullTotal++;
            if (settings.debug()) {
                plugin.getLogger().warning("Cola de generación llena. El chunk se reintentará mientras siga cargado o en su próxima carga: "
                        + chunk.getWorld().getName() + " " + chunk.getX() + "," + chunk.getZ());
            }
            return false;
        }

        long now = System.currentTimeMillis();
        queue.addLast(new PendingChunk(
                chunk.getWorld().getUID(),
                chunk.getWorld().getName(),
                chunk.getX(),
                chunk.getZ(),
                force,
                fromCommand,
                now,
                now + settings.throttle().delayAfterChunkLoadTicks() * 50L
        ));
        queuedTotal++;
        return true;
    }

    private void processQueue() {
        if (queue.isEmpty()) return;
        long now = System.currentTimeMillis();
        int processedThisRun = 0;
        int dequeuedThisRun = 0;
        int maxDequeuesThisRun = Math.max(64, settings.throttle().chunksPerInterval() * 32);

        while (processedThisRun < settings.throttle().chunksPerInterval()
                && dequeuedThisRun < maxDequeuesThisRun
                && !queue.isEmpty()) {
            PendingChunk pending = queue.peekFirst();
            if (pending == null || pending.readyAtMillis() > now) return;
            queue.removeFirst();
            dequeuedThisRun++;

            World world = Bukkit.getWorld(pending.worldId());
            String key = pending.worldId() + ":" + pending.x() + ":" + pending.z();
            queuedKeys.remove(key);
            if (world == null) continue;

            if (!world.isChunkLoaded(pending.x(), pending.z())) {
                deferredUnloadedTotal++;
                if (settings.debug() && settings.debugLogUnloadedChunks()) {
                    plugin.getLogger().info("Chunk aplazado porque ya no está cargado; se reintentará en la próxima carga: "
                            + pending.worldName() + " " + pending.x() + "," + pending.z());
                }
                continue;
            }

            Chunk chunk = world.getChunkAt(pending.x(), pending.z());
            tracker.prepareChunk(chunk, false);
            if (!pending.force() && !tracker.hasMissingActiveRolls(chunk)) continue;

            generator.generate(chunk, pending.force(), pending.fromCommand());
            processedTotal++;
            processedThisRun++;
        }
    }

    public void enqueueMissingLoadedChunks() {
        if (!settings.tracking().processExistingChunksOnLoad()) return;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                tracker.prepareChunk(chunk, false);
                if (!tracker.hasMissingActiveRolls(chunk)) continue;
                if (settings.throttle().enabled()) {
                    enqueue(chunk, false, false);
                } else {
                    generator.generate(chunk, false, false);
                    processedTotal++;
                }
            }
        }
    }

    public QueueSnapshot snapshot() {
        long now = System.currentTimeMillis();
        int ready = 0;
        long oldestAge = 0L;
        long firstReady = 0L;
        Map<String, Integer> byWorld = new HashMap<>();

        PendingChunk first = queue.peekFirst();
        if (first != null) firstReady = Math.max(0L, first.readyAtMillis() - now);
        for (PendingChunk pending : queue) {
            if (pending.readyAtMillis() <= now) ready++;
            oldestAge = Math.max(oldestAge, now - pending.enqueuedAtMillis());
            byWorld.merge(pending.worldName(), 1, Integer::sum);
        }

        double chunksPerSecond = settings.throttle().enabled()
                ? (20.0D / settings.throttle().intervalTicks()) * settings.throttle().chunksPerInterval()
                : 0.0D;
        long estimatedSeconds = chunksPerSecond <= 0.0D ? 0L : (long) Math.ceil(queue.size() / chunksPerSecond);

        return new QueueSnapshot(
                settings.throttle().enabled(),
                queue.size(),
                settings.throttle().maxQueueSize(),
                ready,
                queuedTotal,
                processedTotal,
                skippedQueueFullTotal,
                deferredUnloadedTotal,
                tracker.migratedChunks(),
                firstReady,
                oldestAge,
                estimatedSeconds,
                Map.copyOf(byWorld)
        );
    }

    public int size() {
        return queue.size();
    }

    private String chunkKey(World world, int x, int z) {
        return world.getUID() + ":" + x + ":" + z;
    }

    private record PendingChunk(
            UUID worldId,
            String worldName,
            int x,
            int z,
            boolean force,
            boolean fromCommand,
            long enqueuedAtMillis,
            long readyAtMillis
    ) {}
}
