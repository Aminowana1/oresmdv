package com.mdvcraft.headores.generation;

import com.mdvcraft.headores.config.PluginSettings;
import com.mdvcraft.headores.tracking.ChunkRollTracker;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cola principal, recuperación de rechazados y escaneo incremental de chunks cargados.
 *
 * Todo se ejecuta en el hilo principal porque Bukkit no permite acceder de forma segura
 * a chunks/bloques desde hilos asíncronos. El rendimiento se controla mediante lotes y
 * presupuestos temporales por ejecución para evitar picos aunque haya miles de chunks.
 */
public final class GenerationQueueManager {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final ChunkRollTracker tracker;
    private final ResourceGenerator generator;

    /**
     * LinkedHashMap funciona simultáneamente como cola FIFO y conjunto de deduplicación.
     * También permite retirar un chunk descargado en O(1), evitando acumular miles de
     * entradas muertas durante exploración rápida.
     */
    private final LinkedHashMap<ChunkKey, PendingChunk> queue = new LinkedHashMap<>();
    private final LinkedHashMap<ChunkKey, RetryChunk> retryQueue = new LinkedHashMap<>();

    private BukkitTask processingTask;
    private BukkitTask maintenanceTask;
    private BukkitTask rescanTriggerTask;
    private LoadedChunkScan scan;

    private long queuedTotal;
    private long processedTotal;
    private long skippedQueueFullTotal;
    private long deferredUnloadedTotal;
    private long cancelledOnUnloadTotal;
    private long retriedTotal;
    private long retryDroppedTotal;
    private long scansStartedTotal;
    private long scansCompletedTotal;
    private long scannedChunksTotal;
    private long scanTriggersSkippedTotal;
    private long lastQueueFullWarningNanos;

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

        // Un único worker por tick atiende tanto reintentos compactos como el escaneo
        // incremental. Cuando no hay trabajo, su coste es una comprobación mínima.
        maintenanceTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::runMaintenance,
                1L,
                1L
        );

        int rescanTicks = settings.throttle().rescanLoadedChunksIntervalTicks();
        if (rescanTicks > 0) {
            rescanTriggerTask = Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    () -> startIncrementalScan(false),
                    Math.max(40L, rescanTicks),
                    rescanTicks
            );
        }

        // Completa/migra chunks que ya estaban cargados al arrancar o tras /reload.
        Bukkit.getScheduler().runTaskLater(plugin, () -> startIncrementalScan(true), 40L);
    }

    public void stop() {
        stopTasksOnly();
        queue.clear();
        retryQueue.clear();
        scan = null;
        generator.clearCaches();
    }

    private void stopTasksOnly() {
        if (processingTask != null) {
            processingTask.cancel();
            processingTask = null;
        }
        if (maintenanceTask != null) {
            maintenanceTask.cancel();
            maintenanceTask = null;
        }
        if (rescanTriggerTask != null) {
            rescanTriggerTask.cancel();
            rescanTriggerTask = null;
        }
    }

    public void handleChunkLoad(Chunk chunk, boolean newChunk) {
        long mask = tracker.prepareChunk(chunk, newChunk);
        if (newChunk) {
            if (!settings.generateOnNewChunks()) return;
        } else if (!settings.tracking().processExistingChunksOnLoad()) {
            return;
        }

        if (!tracker.hasMissingActiveRolls(mask)) return;
        if (settings.throttle().enabled()) {
            enqueuePrepared(chunk, mask, false, false, true);
        } else {
            generator.generate(chunk, false, false);
            processedTotal++;
        }
    }


    /**
     * Compatibilidad con pregeneradores como Chunky. ChunkPopulateEvent puede llegar en
     * rutas de generación donde queremos asegurar el alta en la misma cola principal.
     * La deduplicación de enqueuePrepared evita trabajo doble si ChunkLoadEvent ya lo hizo.
     */
    public void handleChunkPopulate(Chunk chunk) {
        if (!settings.chunky().enabled() || !settings.chunky().listenChunkPopulate()) return;
        long mask = tracker.prepareChunk(chunk, true);
        if (!tracker.hasMissingActiveRolls(mask)) return;

        // Modo de pregeneración robusto: procesa el chunk DURANTE ChunkPopulateEvent,
        // antes de que Chunky pueda descargarlo. Esto introduce backpressure natural
        // (Chunky solo avanza cuando termina MDVHeadOres), evita colas gigantes y
        // garantiza que cada chunk pregenerado quede con sus tiradas persistidas.
        // Está pensado para activarse temporalmente al pregenerar mundos grandes.
        if (settings.chunky().directProcessOnPopulate() && Bukkit.isPrimaryThread()) {
            ChunkKey key = ChunkKey.of(chunk);
            queue.remove(key);
            retryQueue.remove(key);
            generator.generate(chunk, false, false);
            processedTotal++;
            return;
        }

        if (settings.throttle().enabled()) {
            enqueuePrepared(chunk, mask, false, false, true);
            // El chunk ya terminó de poblarse: lo promovemos a listo sin saltarnos
            // el worker, el presupuesto temporal ni la deduplicación. Esto ayuda a
            // que pregeneradores rápidos como Chunky no descarguen el chunk antes.
            ChunkKey key = ChunkKey.of(chunk);
            PendingChunk pending = queue.get(key);
            if (pending != null) queue.put(key, pending.readyNow());
        } else {
            generator.generate(chunk, false, false);
            processedTotal++;
        }
    }

    /**
     * Retira inmediatamente de memoria las entradas de chunks descargados. En la próxima
     * carga, ChunkLoadEvent volverá a encolarlos porque sus bits pendientes no se marcaron.
     */
    public void handleChunkUnload(Chunk chunk) {
        ChunkKey key = ChunkKey.of(chunk);
        boolean removed = queue.remove(key) != null;
        removed |= retryQueue.remove(key) != null;
        if (removed) cancelledOnUnloadTotal++;
    }

    public boolean enqueue(Chunk chunk, boolean force, boolean fromCommand) {
        if (chunk == null) return false;
        long mask = tracker.prepareChunk(chunk, false);
        if (!force && !tracker.hasMissingActiveRolls(mask)) return false;
        return enqueuePrepared(chunk, mask, force, fromCommand, true);
    }

    private boolean enqueuePrepared(
            Chunk chunk,
            long knownMask,
            boolean force,
            boolean fromCommand,
            boolean allowRetry
    ) {
        if (!force && !tracker.hasMissingActiveRolls(knownMask)) return false;

        ChunkKey key = ChunkKey.of(chunk);
        PendingChunk existing = queue.get(key);
        if (existing != null) {
            // Un comando force puede mejorar una entrada natural ya encolada.
            if (force && !existing.force()) {
                queue.put(key, existing.withForce(true, fromCommand || existing.fromCommand()));
            }
            return false;
        }

        RetryChunk waitingRetry = retryQueue.get(key);
        if (waitingRetry != null) {
            if (force && !waitingRetry.force()) {
                retryQueue.put(key, waitingRetry.withForce(true, fromCommand || waitingRetry.fromCommand()));
            }
            return false;
        }

        if (queue.size() >= settings.throttle().maxQueueSize()
                && settings.throttle().skipIfQueueFull()) {
            skippedQueueFullTotal++;
            if (allowRetry) addRetry(key, chunk.getWorld().getName(), force, fromCommand);
            warnQueueFullRateLimited(chunk);
            return false;
        }

        long now = System.nanoTime();
        long readyAt = now + ticksToNanos(settings.throttle().delayAfterChunkLoadTicks());
        queue.put(key, new PendingChunk(
                chunk.getWorld().getName(),
                force,
                fromCommand,
                now,
                readyAt
        ));
        queuedTotal++;
        return true;
    }

    private void addRetry(ChunkKey key, String worldName, boolean force, boolean fromCommand) {
        RetryChunk existing = retryQueue.get(key);
        if (existing != null) {
            if (force && !existing.force()) {
                retryQueue.put(key, existing.withForce(true, fromCommand || existing.fromCommand()));
            }
            return;
        }

        if (retryQueue.size() >= settings.throttle().maxRetrySize()) {
            retryDroppedTotal++;
            return;
        }
        retryQueue.put(key, new RetryChunk(worldName, force, fromCommand));
    }

    private void warnQueueFullRateLimited(Chunk chunk) {
        if (!settings.debug()) return;
        long now = System.nanoTime();
        // Como máximo un aviso cada 10 segundos para no empeorar el problema con logs.
        if (now - lastQueueFullWarningNanos < 10_000_000_000L) return;
        lastQueueFullWarningNanos = now;
        plugin.getLogger().warning("Cola de generación llena. Los chunks cargados se reintentarán "
                + "de forma incremental. Ejemplo: " + chunk.getWorld().getName() + " "
                + chunk.getX() + "," + chunk.getZ());
    }

    private void processQueue() {
        if (queue.isEmpty()) return;

        int processedThisRun = 0;
        int dequeuedThisRun = 0;
        long started = System.nanoTime();
        long deadline = started + millisToNanos(settings.throttle().maxProcessingMillisPerRun());
        int maxChunks = settings.throttle().chunksPerInterval();
        int maxDequeues = settings.throttle().maxDequeuesPerRun();

        while (processedThisRun < maxChunks
                && dequeuedThisRun < maxDequeues
                && !queue.isEmpty()) {
            // Siempre permite al menos una entrada; después respeta el presupuesto.
            if (dequeuedThisRun > 0 && System.nanoTime() >= deadline) break;

            Iterator<Map.Entry<ChunkKey, PendingChunk>> iterator = queue.entrySet().iterator();
            Map.Entry<ChunkKey, PendingChunk> entry = iterator.next();
            PendingChunk pending = entry.getValue();
            long now = System.nanoTime();
            if (pending.readyAtNanos() > now) return;

            ChunkKey key = entry.getKey();
            iterator.remove();
            dequeuedThisRun++;

            World world = Bukkit.getWorld(key.worldId());
            if (world == null) continue;
            if (!world.isChunkLoaded(key.x(), key.z())) {
                // Normalmente ChunkUnloadEvent ya lo retiró; esto cubre una carrera rara.
                deferredUnloadedTotal++;
                if (settings.debug() && settings.debugLogUnloadedChunks()) {
                    plugin.getLogger().info("Chunk aplazado porque ya no está cargado; se reintentará "
                            + "en la próxima carga: " + pending.worldName() + " "
                            + key.x() + "," + key.z());
                }
                continue;
            }

            Chunk chunk = world.getChunkAt(key.x(), key.z());
            long mask = tracker.prepareChunk(chunk, false);
            if (!pending.force() && !tracker.hasMissingActiveRolls(mask)) continue;

            generator.generate(chunk, pending.force(), pending.fromCommand());
            processedTotal++;
            processedThisRun++;
        }
    }

    private void runMaintenance() {
        processRetryQueue();
        processScanBatch();
    }

    /**
     * Reintenta únicamente chunks que fueron rechazados por cola llena. Esto evita tener
     * que depender del escaneo global para el caso común de presión durante exploración.
     */
    private void processRetryQueue() {
        if (!settings.throttle().enabled() || retryQueue.isEmpty()) return;
        if (queue.size() >= settings.throttle().maxQueueSize()
                && settings.throttle().skipIfQueueFull()) return;

        int inspected = 0;
        long deadline = System.nanoTime()
                + millisToNanos(settings.throttle().retryMaxMillisPerTick());
        int max = settings.throttle().retryChunksPerTick();

        while (inspected < max && !retryQueue.isEmpty()) {
            if (inspected > 0 && System.nanoTime() >= deadline) break;
            if (queue.size() >= settings.throttle().maxQueueSize()
                    && settings.throttle().skipIfQueueFull()) break;

            Iterator<Map.Entry<ChunkKey, RetryChunk>> iterator = retryQueue.entrySet().iterator();
            Map.Entry<ChunkKey, RetryChunk> entry = iterator.next();
            ChunkKey key = entry.getKey();
            RetryChunk retry = entry.getValue();
            iterator.remove();
            inspected++;

            World world = Bukkit.getWorld(key.worldId());
            if (world == null || !world.isChunkLoaded(key.x(), key.z())) continue;

            Chunk chunk = world.getChunkAt(key.x(), key.z());
            long mask = tracker.prepareChunk(chunk, false);
            if (!retry.force() && !tracker.hasMissingActiveRolls(mask)) continue;

            boolean queued = enqueuePrepared(
                    chunk,
                    mask,
                    retry.force(),
                    retry.fromCommand(),
                    false
            );
            if (queued) {
                retriedTotal++;
            } else if (queue.size() >= settings.throttle().maxQueueSize()
                    && settings.throttle().skipIfQueueFull()) {
                // La cola volvió a llenarse entre comprobaciones. Conserva la entrada y
                // detiene este tick para no girar sobre el mismo problema.
                retryQueue.put(key, retry);
                break;
            }
        }
    }

    /**
     * Inicia un escaneo por lotes. Si el anterior continúa, no crea otro escaneo solapado.
     */
    private void startIncrementalScan(boolean startup) {
        if (!settings.tracking().processExistingChunksOnLoad()) return;
        if (scan != null) {
            scanTriggersSkippedTotal++;
            return;
        }
        scan = new LoadedChunkScan(new ArrayList<>(Bukkit.getWorlds()), startup);
        scansStartedTotal++;
    }

    /**
     * Revisa como máximo N chunks y M milisegundos por tick. No carga chunks y procesa
     * un mundo por vez, por lo que puede recorrer decenas de miles sin un tick gigante.
     */
    private void processScanBatch() {
        LoadedChunkScan active = scan;
        if (active == null) return;

        int inspectedThisTick = 0;
        int maxChunks = settings.throttle().rescanLoadedChunksPerTick();
        long deadline = System.nanoTime()
                + millisToNanos(settings.throttle().rescanMaxMillisPerTick());

        while (inspectedThisTick < maxChunks) {
            if (inspectedThisTick > 0 && System.nanoTime() >= deadline) break;

            if (active.currentChunks == null || active.currentIndex >= active.currentChunks.length) {
                active.currentChunks = null;
                active.currentIndex = 0;
                if (active.worldIndex >= active.worlds.size()) {
                    scannedChunksTotal += active.scanned;
                    scansCompletedTotal++;
                    scan = null;
                    if (settings.debug()) {
                        plugin.getLogger().info("Escaneo incremental completado. Chunks revisados: "
                                + active.scanned + ", pendientes encontrados: " + active.missing
                                + ", encolados: " + active.enqueued + ".");
                    }
                    return;
                }

                World world = active.worlds.get(active.worldIndex++);
                active.currentWorld = world;
                // getLoadedChunks crea solo una instantánea de referencias. No carga ni
                // mantiene chunks; el recorrido real se distribuye entre muchos ticks.
                active.currentChunks = world.getLoadedChunks();
                if (active.currentChunks.length == 0) continue;
            }

            Chunk chunk = active.currentChunks[active.currentIndex++];
            inspectedThisTick++;
            active.scanned++;

            World world = active.currentWorld;
            if (world == null || !world.isChunkLoaded(chunk.getX(), chunk.getZ())) continue;

            long mask = tracker.prepareChunk(chunk, false);
            if (!tracker.hasMissingActiveRolls(mask)) continue;
            active.missing++;

            if (settings.throttle().enabled()) {
                if (enqueuePrepared(chunk, mask, false, false, true)) active.enqueued++;
            } else {
                generator.generate(chunk, false, false);
                processedTotal++;
                active.enqueued++;
            }
        }
    }

    public QueueSnapshot snapshot() {
        long now = System.nanoTime();
        int ready = 0;
        long oldestAgeNanos = 0L;
        long firstReadyNanos = 0L;
        Map<String, Integer> byWorld = new LinkedHashMap<>();

        Iterator<Map.Entry<ChunkKey, PendingChunk>> iterator = queue.entrySet().iterator();
        if (iterator.hasNext()) {
            PendingChunk first = iterator.next().getValue();
            firstReadyNanos = Math.max(0L, first.readyAtNanos() - now);
        }
        for (PendingChunk pending : queue.values()) {
            if (pending.readyAtNanos() <= now) ready++;
            oldestAgeNanos = Math.max(oldestAgeNanos, now - pending.enqueuedAtNanos());
            byWorld.merge(pending.worldName(), 1, Integer::sum);
        }

        double chunksPerSecond = settings.throttle().enabled()
                ? (20.0D / settings.throttle().intervalTicks())
                * settings.throttle().chunksPerInterval()
                : 0.0D;
        long estimatedSeconds = chunksPerSecond <= 0.0D
                ? 0L
                : (long) Math.ceil(queue.size() / chunksPerSecond);

        LoadedChunkScan activeScan = scan;
        return new QueueSnapshot(
                settings.throttle().enabled(),
                queue.size(),
                settings.throttle().maxQueueSize(),
                retryQueue.size(),
                settings.throttle().maxRetrySize(),
                ready,
                queuedTotal,
                processedTotal,
                skippedQueueFullTotal,
                deferredUnloadedTotal,
                cancelledOnUnloadTotal,
                retriedTotal,
                retryDroppedTotal,
                tracker.migratedChunks(),
                nanosToMillis(firstReadyNanos),
                nanosToMillis(oldestAgeNanos),
                estimatedSeconds,
                Map.copyOf(byWorld),
                activeScan != null,
                activeScan == null ? 0L : activeScan.scanned,
                activeScan == null ? 0L : activeScan.missing,
                scansStartedTotal,
                scansCompletedTotal,
                scannedChunksTotal,
                scanTriggersSkippedTotal
        );
    }

    public int size() {
        return queue.size();
    }

    private static long ticksToNanos(long ticks) {
        return ticks <= 0 ? 0L : ticks * 50_000_000L;
    }

    private static long millisToNanos(double millis) {
        if (millis <= 0.0D) return 0L;
        return Math.max(1L, (long) (millis * 1_000_000.0D));
    }

    private static long nanosToMillis(long nanos) {
        return nanos <= 0L ? 0L : nanos / 1_000_000L;
    }

    private record ChunkKey(UUID worldId, int x, int z) {
        static ChunkKey of(Chunk chunk) {
            return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        }
    }

    private record PendingChunk(
            String worldName,
            boolean force,
            boolean fromCommand,
            long enqueuedAtNanos,
            long readyAtNanos
    ) {
        PendingChunk withForce(boolean newForce, boolean newFromCommand) {
            return new PendingChunk(
                    worldName,
                    newForce,
                    newFromCommand,
                    enqueuedAtNanos,
                    readyAtNanos
            );
        }

        PendingChunk readyNow() {
            return new PendingChunk(worldName, force, fromCommand, enqueuedAtNanos, System.nanoTime());
        }
    }

    private record RetryChunk(String worldName, boolean force, boolean fromCommand) {
        RetryChunk withForce(boolean newForce, boolean newFromCommand) {
            return new RetryChunk(worldName, newForce, newFromCommand);
        }
    }

    private static final class LoadedChunkScan {
        private final List<World> worlds;
        @SuppressWarnings("unused")
        private final boolean startup;
        private int worldIndex;
        private World currentWorld;
        private Chunk[] currentChunks;
        private int currentIndex;
        private long scanned;
        private long missing;
        private long enqueued;

        private LoadedChunkScan(List<World> worlds, boolean startup) {
            this.worlds = List.copyOf(worlds);
            this.startup = startup;
        }
    }
}
