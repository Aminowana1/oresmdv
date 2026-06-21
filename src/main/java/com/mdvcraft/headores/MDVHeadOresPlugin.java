package com.mdvcraft.headores;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MDVHeadOresPlugin extends JavaPlugin implements Listener {

    private final Random random = new Random();
    private final Map<String, OreDefinition> ores = new HashMap<>();
    private final Map<String, TreeNodeDefinition> treeNodes = new HashMap<>();
    private final Deque<PendingChunk> generationQueue = new ArrayDeque<>();
    private final Set<String> queuedChunkKeys = new HashSet<>();
    private final Map<String, PlayerProfile> profileCache = new HashMap<>();
    private final Pattern powerLorePattern = Pattern.compile("(?i)(poder\\s+de\\s+pico|pickaxe\\s+power|pickaxe-power).*?([+-]?\\d+(?:[\\.,]\\d+)?)");
    private final Pattern axePowerLorePattern = Pattern.compile("(?i)(poder\\s+de\\s+hacha|axe\\s+power|axe-power).*?([+-]?\\d+(?:[\\.,]\\d+)?)");

    private NamespacedKey oreKey;
    private NamespacedKey nodeKey;
    private NamespacedKey blockIdKey;
    private NamespacedKey dropTypeKey;
    private NamespacedKey dropIdKey;
    private NamespacedKey generatedChunkKey;

    private boolean debug;
    private boolean generateOnNewChunks;
    private boolean markGeneratedChunks;
    private boolean vanillaPickaxesHavePower;
    private boolean vanillaAxesHavePower;
    private String defaultFallbackCommand;
    private String mmocoreExpCommandTemplate;
    private String noPowerMessage;
    private String noAxePowerMessage;

    private boolean generationThrottleEnabled;
    private int generationIntervalTicks;
    private int generationChunksPerInterval;
    private int generationDelayTicks;
    private int generationMaxQueueSize;
    private boolean generationSkipIfQueueFull;
    private boolean commandGenerateUsesQueue;
    private BukkitTask generationTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        oreKey = new NamespacedKey(this, "ore_key");
        nodeKey = new NamespacedKey(this, "tree_node_key");
        blockIdKey = new NamespacedKey(this, "mmoitems_block_id");
        dropTypeKey = new NamespacedKey(this, "drop_type");
        dropIdKey = new NamespacedKey(this, "drop_id");
        generatedChunkKey = new NamespacedKey(this, "generated_chunk");

        loadSettings();
        restartGenerationTask();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("MDVHeadOres activado. Vetas cargadas: " + ores.size() + ", nodos de árbol cargados: " + treeNodes.size());
    }

    @Override
    public void onDisable() {
        if (generationTask != null) {
            generationTask.cancel();
            generationTask = null;
        }
        generationQueue.clear();
        queuedChunkKeys.clear();
        profileCache.clear();
    }

    private void loadSettings() {
        reloadConfig();
        FileConfiguration cfg = getConfig();

        debug = cfg.getBoolean("debug", false);
        generateOnNewChunks = cfg.getBoolean("generate-on-new-chunks", true);
        markGeneratedChunks = cfg.getBoolean("mark-generated-chunks", true);
        vanillaPickaxesHavePower = cfg.getBoolean("vanilla-pickaxes-have-power", true);
        vanillaAxesHavePower = cfg.getBoolean("vanilla-axes-have-power", true);
        defaultFallbackCommand = cfg.getString("default-fallback-command", "mi give %drop_type% %drop_id% %player% %amount%");
        mmocoreExpCommandTemplate = cfg.getString("mmocore-exp-command", "mmocore admin exp give %player% %target% %amount% %split%");

        generationThrottleEnabled = cfg.getBoolean("generation-throttle.enabled", true);
        generationIntervalTicks = Math.max(1, cfg.getInt("generation-throttle.interval-ticks", 4));
        generationChunksPerInterval = Math.max(1, cfg.getInt("generation-throttle.chunks-per-interval", 1));
        generationDelayTicks = Math.max(0, cfg.getInt("generation-throttle.delay-after-chunk-load-ticks", 40));
        generationMaxQueueSize = Math.max(64, cfg.getInt("generation-throttle.max-queue-size", 10000));
        generationSkipIfQueueFull = cfg.getBoolean("generation-throttle.skip-if-queue-full", true);
        commandGenerateUsesQueue = cfg.getBoolean("generation-throttle.command-generate-uses-queue", true);
        noPowerMessage = cfg.getString("no-power-message", "&6&l[&5&lMDVCRAFT&6&l]  &4»  &cTu pico no tiene suficiente poder para minar esta veta. &7Requiere: &f%required%&7. Tu poder: &f%power%&c.");
        noAxePowerMessage = cfg.getString("no-axe-power-message", "&6&l[&5&lMDVCRAFT&6&l]  &4»  &cTu hacha no tiene suficiente poder para extraer este recurso. &7Requiere: &f%required%&7. Tu poder: &f%power%&c.");

        ores.clear();
        treeNodes.clear();
        profileCache.clear();

        File miBlockFile = new File(getServer().getWorldContainer(), cfg.getString("mmoitems-block-file", "plugins/MMOItems/item/block.yml"));
        YamlConfiguration mmoBlocks = YamlConfiguration.loadConfiguration(miBlockFile);
        if (!miBlockFile.exists()) {
            getLogger().warning("No se encontró el archivo de bloques de MMOItems: " + miBlockFile.getPath());
        }

        ConfigurationSection oreSection = cfg.getConfigurationSection("ores");
        if (oreSection == null) {
            getLogger().warning("No hay sección 'ores' en config.yml.");
            return;
        }

        for (String key : oreSection.getKeys(false)) {
            ConfigurationSection sec = oreSection.getConfigurationSection(key);
            if (sec == null || !sec.getBoolean("enabled", true)) continue;

            String miBlockId = sec.getString("mmoitems-block-id", "").trim();
            if (miBlockId.isEmpty()) {
                getLogger().warning("La veta '" + key + "' no tiene mmoitems-block-id.");
                continue;
            }

            String rawTexture = sec.getString("skull-texture", null);
            if (sec.getBoolean("texture-from-mmoitems", true)) {
                String fromMi = mmoBlocks.getString(miBlockId + ".base.skull-texture.value", null);
                if (fromMi != null && !fromMi.isBlank()) rawTexture = fromMi;
            }

            String textureHash = extractTextureHash(rawTexture);
            if (textureHash == null || textureHash.isBlank()) {
                getLogger().warning("No pude leer textura para la veta '" + key + "' usando el bloque MMOItems '" + miBlockId + "'.");
                continue;
            }

            String displayName = sec.getString("display-name", null);
            if (sec.getBoolean("name-from-mmoitems", true)) {
                String fromMi = mmoBlocks.getString(miBlockId + ".base.name", null);
                if (fromMi != null && !fromMi.isBlank()) displayName = fromMi;
            }
            if (displayName == null || displayName.isBlank()) displayName = key;

            List<Material> replaceMaterials = new ArrayList<>();
            for (String matName : sec.getStringList("replace")) {
                Material mat = Material.matchMaterial(matName);
                if (mat != null && mat.isBlock()) {
                    replaceMaterials.add(mat);
                } else {
                    getLogger().warning("Material inválido en replace de '" + key + "': " + matName);
                }
            }
            if (replaceMaterials.isEmpty()) {
                getLogger().warning("La veta '" + key + "' no tiene bloques válidos para reemplazar.");
                continue;
            }

            OreDefinition ore = new OreDefinition();
            ore.key = key;
            ore.mmoitemsBlockId = miBlockId;
            ore.textureHash = textureHash;
            ore.displayName = displayName;
            ore.worlds = new HashSet<>(sec.getStringList("worlds"));
            ore.replace = replaceMaterials;
            ore.minY = sec.getInt("min-y", -48);
            ore.maxY = sec.getInt("max-y", 48);
            ore.chunkChance = sec.getDouble("chunk-chance", 0.21);
            ore.veinsPerChunk = Math.max(1, sec.getInt("veins-per-chunk", 1));
            ore.veinMin = Math.max(1, sec.getInt("vein-min", 1));
            ore.veinMax = Math.max(ore.veinMin, sec.getInt("vein-max", 2));
            ore.placement = sec.getString("placement", "FLOOR_HEAD").toUpperCase(Locale.ROOT);
            ore.dropType = sec.getString("drop-type", "MATERIAL");
            ore.dropId = sec.getString("drop-id", "");
            ore.dropAmount = Math.max(1, sec.getInt("drop-amount", 1));
            ore.preventVanillaDrops = sec.getBoolean("prevent-vanilla-drops", true);
            ore.ignoreSilkTouch = sec.getBoolean("ignore-silk-touch", true);
            ore.dropNaturally = sec.getBoolean("drop-naturally", true);
            ore.requiredPickaxePower = Math.max(0, sec.getDouble("required-pickaxe-power", 0));
            ore.breakSound = sec.getString("break-sound", "");
            ore.failSound = sec.getString("fail-sound", "BLOCK_NOTE_BLOCK_BASS");
            ore.fallbackCommand = sec.getString("fallback-command", null);
            ore.mmocoreXp = new MmoCoreXpSettings();
            loadMmoCoreXpSettings(sec.getConfigurationSection("mmocore-xp"), ore.mmocoreXp, key);

            ore.applyPhysicsOnPlace = sec.getBoolean("apply-physics-on-place", true);
            ore.avoidNearMaterials = new HashSet<>();
            if (sec.getBoolean("avoid-near-vanilla-ores", true)) {
                ore.avoidNearMaterials.addAll(defaultVanillaOreMaterials());
            }
            for (String matName : sec.getStringList("avoid-near-materials")) {
                Material mat = Material.matchMaterial(matName);
                if (mat != null && mat.isBlock()) {
                    ore.avoidNearMaterials.add(mat);
                } else {
                    getLogger().warning("Material inválido en avoid-near-materials de '" + key + "': " + matName);
                }
            }

            ores.put(key, ore);
            if (debug) getLogger().info("Veta cargada: " + key + " -> " + miBlockId + " drop " + ore.dropType + ":" + ore.dropId + " poder requerido " + ore.requiredPickaxePower);
        }

        loadTreeNodes(cfg, mmoBlocks);
    }

    private void loadTreeNodes(FileConfiguration cfg, YamlConfiguration mmoBlocks) {
        ConfigurationSection nodeSection = cfg.getConfigurationSection("tree-nodes");
        if (nodeSection == null) {
            if (debug) getLogger().info("No hay sección 'tree-nodes' en config.yml.");
            return;
        }

        for (String key : nodeSection.getKeys(false)) {
            ConfigurationSection sec = nodeSection.getConfigurationSection(key);
            if (sec == null || !sec.getBoolean("enabled", true)) continue;

            String miBlockId = sec.getString("mmoitems-block-id", "").trim();
            if (miBlockId.isEmpty()) {
                getLogger().warning("El nodo de árbol '" + key + "' no tiene mmoitems-block-id.");
                continue;
            }

            String rawTexture = sec.getString("skull-texture", null);
            if (sec.getBoolean("texture-from-mmoitems", true)) {
                String fromMi = mmoBlocks.getString(miBlockId + ".base.skull-texture.value", null);
                if (fromMi != null && !fromMi.isBlank()) rawTexture = fromMi;
            }

            String textureHash = extractTextureHash(rawTexture);
            if (textureHash == null || textureHash.isBlank()) {
                getLogger().warning("No pude leer textura para el nodo de árbol '" + key + "' usando el bloque MMOItems '" + miBlockId + "'.");
                continue;
            }

            String displayName = sec.getString("display-name", null);
            if (sec.getBoolean("name-from-mmoitems", true)) {
                String fromMi = mmoBlocks.getString(miBlockId + ".base.name", null);
                if (fromMi != null && !fromMi.isBlank()) displayName = fromMi;
            }
            if (displayName == null || displayName.isBlank()) displayName = key;

            List<Material> attachTo = new ArrayList<>();
            for (String matName : sec.getStringList("attach-to")) {
                Material mat = Material.matchMaterial(matName);
                if (mat != null && mat.isBlock()) {
                    attachTo.add(mat);
                } else {
                    getLogger().warning("Material inválido en attach-to de '" + key + "': " + matName);
                }
            }
            if (attachTo.isEmpty()) {
                getLogger().warning("El nodo de árbol '" + key + "' no tiene bloques válidos en attach-to.");
                continue;
            }

            TreeNodeDefinition node = new TreeNodeDefinition();
            node.key = key;
            node.mmoitemsBlockId = miBlockId;
            node.textureHash = textureHash;
            node.displayName = displayName;
            node.worlds = new HashSet<>(sec.getStringList("worlds"));
            node.attachTo = attachTo;
            node.minY = sec.getInt("min-y", 50);
            node.maxY = sec.getInt("max-y", 120);
            node.chunkChance = sec.getDouble("chunk-chance", 0.12);
            node.nodesPerChunk = Math.max(1, sec.getInt("nodes-per-chunk", 1));
            node.dropType = sec.getString("drop-type", "MATERIAL");
            node.dropId = sec.getString("drop-id", "");
            node.dropAmount = Math.max(1, sec.getInt("drop-amount", 1));
            node.preventVanillaDrops = sec.getBoolean("prevent-vanilla-drops", true);
            node.ignoreSilkTouch = sec.getBoolean("ignore-silk-touch", true);
            node.dropNaturally = sec.getBoolean("drop-naturally", true);
            node.requiredAxePower = Math.max(0, sec.getDouble("required-axe-power", 0));
            node.breakSound = sec.getString("break-sound", "BLOCK_WOOD_BREAK");
            node.failSound = sec.getString("fail-sound", "BLOCK_NOTE_BLOCK_BASS");
            node.fallbackCommand = sec.getString("fallback-command", null);
            node.mmocoreXp = new MmoCoreXpSettings();
            loadMmoCoreXpSettings(sec.getConfigurationSection("mmocore-xp"), node.mmocoreXp, key);
            node.applyPhysicsOnPlace = sec.getBoolean("apply-physics-on-place", true);
            node.onlyOnSurfaceLogs = sec.getBoolean("only-on-surface-logs", true);

            treeNodes.put(key, node);
            if (debug) getLogger().info("Nodo de árbol cargado: " + key + " -> " + miBlockId + " drop " + node.dropType + ":" + node.dropId + " poder hacha requerido " + node.requiredAxePower);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!generateOnNewChunks || !event.isNewChunk()) return;
        Chunk chunk = event.getChunk();

        if (generationThrottleEnabled) {
            enqueueChunk(chunk, false, false);
        } else {
            Bukkit.getScheduler().runTask(this, () -> generateInChunk(chunk, false, false));
        }
    }

    private void restartGenerationTask() {
        if (generationTask != null) {
            generationTask.cancel();
            generationTask = null;
        }

        if (!generationThrottleEnabled) return;

        generationTask = Bukkit.getScheduler().runTaskTimer(this, this::processGenerationQueue, 20L, Math.max(1L, generationIntervalTicks));
    }

    private boolean enqueueChunk(Chunk chunk, boolean force, boolean fromCommand) {
        if (chunk == null) return false;

        if (markGeneratedChunks && !force) {
            String marked = chunk.getPersistentDataContainer().get(generatedChunkKey, PersistentDataType.STRING);
            if (marked != null) return false;
        }

        String key = chunkQueueKey(chunk.getWorld(), chunk.getX(), chunk.getZ());
        if (queuedChunkKeys.contains(key)) return false;

        if (generationQueue.size() >= generationMaxQueueSize && generationSkipIfQueueFull) {
            if (debug) getLogger().warning("Cola de generación llena. Chunk omitido: " + chunk.getWorld().getName() + " " + chunk.getX() + "," + chunk.getZ());
            return false;
        }

        PendingChunk pending = new PendingChunk();
        pending.worldId = chunk.getWorld().getUID();
        pending.worldName = chunk.getWorld().getName();
        pending.x = chunk.getX();
        pending.z = chunk.getZ();
        pending.force = force;
        pending.fromCommand = fromCommand;
        pending.readyAtMillis = System.currentTimeMillis() + (generationDelayTicks * 50L);

        generationQueue.addLast(pending);
        queuedChunkKeys.add(key);
        return true;
    }

    private String chunkQueueKey(World world, int x, int z) {
        return world.getUID() + ":" + x + ":" + z;
    }

    private void processGenerationQueue() {
        if (generationQueue.isEmpty()) return;

        long now = System.currentTimeMillis();
        int processed = 0;

        while (processed < generationChunksPerInterval && !generationQueue.isEmpty()) {
            PendingChunk pending = generationQueue.peekFirst();
            if (pending == null) return;
            if (pending.readyAtMillis > now) return;

            generationQueue.removeFirst();
            World world = Bukkit.getWorld(pending.worldId);
            if (world == null) {
                queuedChunkKeys.remove(pending.worldId + ":" + pending.x + ":" + pending.z);
                continue;
            }

            queuedChunkKeys.remove(chunkQueueKey(world, pending.x, pending.z));

            if (!world.isChunkLoaded(pending.x, pending.z)) {
                if (debug) getLogger().info("Chunk omitido porque ya no está cargado: " + pending.worldName + " " + pending.x + "," + pending.z);
                continue;
            }

            Chunk chunk = world.getChunkAt(pending.x, pending.z);
            generateInChunk(chunk, pending.force, pending.fromCommand);
            processed++;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type != Material.PLAYER_HEAD && type != Material.PLAYER_WALL_HEAD) return;

        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) return;

        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        String oreName = pdc.get(oreKey, PersistentDataType.STRING);
        String nodeName = pdc.get(nodeKey, PersistentDataType.STRING);

        if (oreName == null && nodeName != null) {
            handleTreeNodeBreak(event, nodeName);
            return;
        }

        if (oreName == null) return;

        OreDefinition ore = ores.get(oreName);
        if (ore == null) {
            if (debug) getLogger().warning("Se rompió una veta desconocida: " + oreName);
            event.setDropItems(false);
            return;
        }

        Player player = event.getPlayer();

        if (ore.requiredPickaxePower > 0) {
            double currentPower = getPickaxePower(player.getInventory().getItemInMainHand());
            if (currentPower + 0.0001 < ore.requiredPickaxePower) {
                event.setCancelled(true);
                String msg = noPowerMessage
                        .replace("%required%", formatNumber(ore.requiredPickaxePower))
                        .replace("%power%", formatNumber(currentPower))
                        .replace("%ore%", ore.key);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                playConfiguredSound(block.getLocation(), ore.failSound, 0.7f, 0.75f);
                return;
            }
        }

        if (ore.preventVanillaDrops || ore.ignoreSilkTouch) {
            event.setDropItems(false);
        }

        if (ore.dropNaturally) {
            ItemStack drop = buildMmoItemStack(ore.dropType, ore.dropId, ore.dropAmount);
            if (drop != null && drop.getType() != Material.AIR) {
                Location dropLoc = block.getLocation().add(0.5, 0.35, 0.5);
                Item dropped = block.getWorld().dropItemNaturally(dropLoc, drop);
                dropped.setPickupDelay(10);
            } else {
                runFallbackCommand(ore, player, block);
            }
        } else {
            runFallbackCommand(ore, player, block);
        }

        giveMmoCoreXp(player, ore.mmocoreXp, ore.key);
        playConfiguredSound(block.getLocation(), ore.breakSound, 0.8f, 1.15f);
    }

    private void handleTreeNodeBreak(BlockBreakEvent event, String nodeName) {
        Block block = event.getBlock();
        TreeNodeDefinition node = treeNodes.get(nodeName);
        if (node == null) {
            if (debug) getLogger().warning("Se rompió un nodo de árbol desconocido: " + nodeName);
            event.setDropItems(false);
            return;
        }

        Player player = event.getPlayer();

        if (node.requiredAxePower > 0) {
            double currentPower = getAxePower(player.getInventory().getItemInMainHand());
            if (currentPower + 0.0001 < node.requiredAxePower) {
                event.setCancelled(true);
                String msg = noAxePowerMessage
                        .replace("%required%", formatNumber(node.requiredAxePower))
                        .replace("%power%", formatNumber(currentPower))
                        .replace("%node%", node.key);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                playConfiguredSound(block.getLocation(), node.failSound, 0.7f, 0.75f);
                return;
            }
        }

        if (node.preventVanillaDrops || node.ignoreSilkTouch) {
            event.setDropItems(false);
        }

        if (node.dropNaturally) {
            ItemStack drop = buildMmoItemStack(node.dropType, node.dropId, node.dropAmount);
            if (drop != null && drop.getType() != Material.AIR) {
                Location dropLoc = block.getLocation().add(0.5, 0.35, 0.5);
                Item dropped = block.getWorld().dropItemNaturally(dropLoc, drop);
                dropped.setPickupDelay(10);
            } else {
                runFallbackCommand(node, player, block);
            }
        } else {
            runFallbackCommand(node, player, block);
        }

        giveMmoCoreXp(player, node.mmocoreXp, node.key);
        playConfiguredSound(block.getLocation(), node.breakSound, 0.8f, 1.05f);
    }

    private void loadMmoCoreXpSettings(ConfigurationSection sec, MmoCoreXpSettings settings, String ownerKey) {
        if (settings == null) return;
        if (sec == null) {
            settings.enabled = false;
            settings.professionId = "";
            settings.professionAmount = new IntRange(0, 0);
            settings.mainAmount = new IntRange(0, 0);
            settings.split = false;
            return;
        }

        settings.enabled = sec.getBoolean("enabled", false);
        settings.professionId = sec.getString("profession-id", "");
        settings.professionAmount = parseIntRange(sec.getString("profession-amount", "0"));
        settings.mainAmount = parseIntRange(sec.getString("main-amount", "0"));
        settings.split = sec.getBoolean("split", false);

        if (settings.enabled && debug) {
            getLogger().info("XP MMOCore cargada para " + ownerKey + ": prof=" + settings.professionId + " " + settings.professionAmount.min + "-" + settings.professionAmount.max + ", main=" + settings.mainAmount.min + "-" + settings.mainAmount.max);
        }
    }

    private IntRange parseIntRange(String raw) {
        if (raw == null || raw.isBlank()) return new IntRange(0, 0);
        raw = raw.trim();
        try {
            if (raw.contains("-")) {
                String[] parts = raw.split("-", 2);
                int a = Integer.parseInt(parts[0].trim());
                int b = Integer.parseInt(parts[1].trim());
                return new IntRange(Math.min(a, b), Math.max(a, b));
            }
            int value = Integer.parseInt(raw);
            return new IntRange(value, value);
        } catch (NumberFormatException exception) {
            return new IntRange(0, 0);
        }
    }

    private int rollIntRange(IntRange range) {
        if (range == null) return 0;
        if (range.max <= range.min) return Math.max(0, range.min);
        return Math.max(0, range.min + random.nextInt(range.max - range.min + 1));
    }

    private void giveMmoCoreXp(Player player, MmoCoreXpSettings settings, String source) {
        if (player == null || settings == null || !settings.enabled) return;

        int professionAmount = rollIntRange(settings.professionAmount);
        if (professionAmount > 0 && settings.professionId != null && !settings.professionId.isBlank()) {
            dispatchMmoCoreExpCommand(player, settings.professionId, professionAmount, settings.split, source);
        }

        int mainAmount = rollIntRange(settings.mainAmount);
        if (mainAmount > 0) {
            dispatchMmoCoreExpCommand(player, "main", mainAmount, settings.split, source);
        }
    }

    private void dispatchMmoCoreExpCommand(Player player, String target, int amount, boolean split, String source) {
        String command = mmocoreExpCommandTemplate;
        if (command == null || command.isBlank()) return;

        command = command
                .replace("%player%", player.getName())
                .replace("%target%", target)
                .replace("%profession%", target)
                .replace("%amount%", Integer.toString(amount))
                .replace("%split%", Boolean.toString(split))
                .replace("%source%", source == null ? "" : source);

        if (command.startsWith("/")) command = command.substring(1);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private void runFallbackCommand(OreDefinition ore, Player player, Block block) {
        String command = ore.fallbackCommand;
        if (command == null || command.isBlank()) {
            command = defaultFallbackCommand;
        }

        command = command
                .replace("%player%", player.getName())
                .replace("%world%", block.getWorld().getName())
                .replace("%x%", Integer.toString(block.getX()))
                .replace("%y%", Integer.toString(block.getY()))
                .replace("%z%", Integer.toString(block.getZ()))
                .replace("%ore%", ore.key)
                .replace("%drop_type%", ore.dropType)
                .replace("%drop_id%", ore.dropId)
                .replace("%amount%", Integer.toString(ore.dropAmount));

        if (command.startsWith("/")) command = command.substring(1);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (debug) getLogger().warning("No pude crear el ItemStack MMOItems para " + ore.dropType + ":" + ore.dropId + ". Usé fallback-command.");
    }

    private void runFallbackCommand(TreeNodeDefinition node, Player player, Block block) {
        String command = node.fallbackCommand;
        if (command == null || command.isBlank()) {
            command = defaultFallbackCommand;
        }

        command = command
                .replace("%player%", player.getName())
                .replace("%world%", block.getWorld().getName())
                .replace("%x%", Integer.toString(block.getX()))
                .replace("%y%", Integer.toString(block.getY()))
                .replace("%z%", Integer.toString(block.getZ()))
                .replace("%ore%", node.key)
                .replace("%node%", node.key)
                .replace("%drop_type%", node.dropType)
                .replace("%drop_id%", node.dropId)
                .replace("%amount%", Integer.toString(node.dropAmount));

        if (command.startsWith("/")) command = command.substring(1);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (debug) getLogger().warning("No pude crear el ItemStack MMOItems para " + node.dropType + ":" + node.dropId + ". Usé fallback-command.");
    }

    private ItemStack buildMmoItemStack(String typeId, String itemId, int amount) {
        try {
            Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Object plugin = getStaticField(mmoItemsClass, "plugin");
            if (plugin == null) return null;

            Class<?> typeClass = Class.forName("net.Indyuce.mmoitems.api.Type");
            Object type = getMmoItemsType(typeClass, typeId);
            if (type == null) {
                if (debug) getLogger().warning("Tipo MMOItems no encontrado: " + typeId);
                return null;
            }

            ItemStack direct = tryInvokeItemStack(plugin, "getItem", type, itemId);
            if (direct != null) {
                direct.setAmount(Math.max(1, amount));
                return direct;
            }

            Object mmoItem = tryInvokeObject(plugin, "getMMOItem", type, itemId);
            if (mmoItem != null) {
                ItemStack built = buildFromMmoItemObject(mmoItem);
                if (built != null) {
                    built.setAmount(Math.max(1, amount));
                    return built;
                }
            }
        } catch (Throwable throwable) {
            if (debug) getLogger().warning("Error creando item MMOItems " + typeId + ":" + itemId + " -> " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
        return null;
    }

    private Object getMmoItemsType(Class<?> typeClass, String typeId) {
        try {
            Method get = typeClass.getMethod("get", String.class);
            return get.invoke(null, typeId);
        } catch (Throwable ignored) {
        }

        try {
            Method valueOf = typeClass.getMethod("valueOf", String.class);
            return valueOf.invoke(null, typeId.toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
        }
        return null;
    }

    private ItemStack tryInvokeItemStack(Object target, String methodName, Object type, String itemId) {
        Object result = tryInvokeObject(target, methodName, type, itemId);
        if (result instanceof ItemStack stack) return stack.clone();
        return null;
    }

    private Object tryInvokeObject(Object target, String methodName, Object type, String itemId) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName)) continue;
            if (method.getParameterCount() != 2) continue;
            try {
                return method.invoke(target, type, itemId);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private ItemStack buildFromMmoItemObject(Object mmoItem) {
        try {
            Method newBuilder = mmoItem.getClass().getMethod("newBuilder");
            Object builder = newBuilder.invoke(mmoItem);
            if (builder == null) return null;
            Method build = builder.getClass().getMethod("build");
            Object result = build.invoke(builder);
            if (result instanceof ItemStack stack) return stack.clone();
        } catch (Throwable ignored) {
        }

        try {
            Method build = mmoItem.getClass().getMethod("build");
            Object result = build.invoke(mmoItem);
            if (result instanceof ItemStack stack) return stack.clone();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object getStaticField(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getField(fieldName);
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private double getPickaxePower(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0;

        Double fromPdc = readPowerFromPersistentData(item);
        if (fromPdc != null) return fromPdc;

        Double fromNbt = readPowerFromMmoItemsNbt(item);
        if (fromNbt != null) return fromNbt;

        Double fromLore = readPowerFromLore(item);
        if (fromLore != null) return fromLore;

        if (vanillaPickaxesHavePower) {
            return vanillaPickaxePower(item.getType());
        }

        return 0;
    }

    private double getAxePower(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0;

        Double fromPdc = readAxePowerFromPersistentData(item);
        if (fromPdc != null) return fromPdc;

        Double fromNbt = readAxePowerFromMmoItemsNbt(item);
        if (fromNbt != null) return fromNbt;

        Double fromLore = readAxePowerFromLore(item);
        if (fromLore != null) return fromLore;

        if (vanillaAxesHavePower) {
            return vanillaAxePower(item.getType());
        }

        return 0;
    }

    private Double readAxePowerFromPersistentData(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        for (NamespacedKey key : pdc.getKeys()) {
            String raw = (key.getNamespace() + ":" + key.getKey()).toLowerCase(Locale.ROOT);
            if (!raw.contains("axe") || !raw.contains("power")) continue;

            Double value = readDoubleFromPdc(pdc, key);
            if (value != null) return value;
        }
        return null;
    }

    private Double readAxePowerFromMmoItemsNbt(ItemStack item) {
        try {
            Class<?> nbtClass = Class.forName("net.Indyuce.mmoitems.api.item.nbt.NBTItem");
            Object nbtItem = createMmoNbtItem(nbtClass, item);
            if (nbtItem == null) return null;

            String[] tags = new String[] {
                    "MMOITEMS_AXE_POWER",
                    "AXE_POWER",
                    "axe-power",
                    "axe_power"
            };

            for (String tag : tags) {
                Boolean has = tryInvokeBoolean(nbtItem, "hasTag", tag);
                if (has != null && !has) continue;

                Double value = tryInvokeDouble(nbtItem, "getDouble", tag);
                if (value != null && value > 0) return value;

                Integer intValue = tryInvokeInteger(nbtItem, "getInteger", tag);
                if (intValue == null) intValue = tryInvokeInteger(nbtItem, "getInt", tag);
                if (intValue != null && intValue > 0) return intValue.doubleValue();

                String strValue = tryInvokeString(nbtItem, "getString", tag);
                if (strValue != null && !strValue.isBlank()) {
                    try {
                        return Double.parseDouble(strValue.replace(',', '.'));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Throwable throwable) {
            if (debug) getLogger().warning("No pude leer NBT de MMOItems para poder de hacha: " + throwable.getClass().getSimpleName());
        }
        return null;
    }

    private Double readAxePowerFromLore(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null) return null;

        for (String line : meta.getLore()) {
            String clean = ChatColor.stripColor(line);
            if (clean == null) continue;
            Matcher matcher = axePowerLorePattern.matcher(clean);
            if (!matcher.find()) continue;

            try {
                return Double.parseDouble(matcher.group(2).replace(',', '.'));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Double readPowerFromPersistentData(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        for (NamespacedKey key : pdc.getKeys()) {
            String raw = (key.getNamespace() + ":" + key.getKey()).toLowerCase(Locale.ROOT);
            if (!raw.contains("pickaxe") || !raw.contains("power")) continue;

            Double value = readDoubleFromPdc(pdc, key);
            if (value != null) return value;
        }
        return null;
    }

    private Double readDoubleFromPdc(PersistentDataContainer pdc, NamespacedKey key) {
        try {
            Double value = pdc.get(key, PersistentDataType.DOUBLE);
            if (value != null) return value;
        } catch (Throwable ignored) {
        }
        try {
            Integer value = pdc.get(key, PersistentDataType.INTEGER);
            if (value != null) return value.doubleValue();
        } catch (Throwable ignored) {
        }
        try {
            Long value = pdc.get(key, PersistentDataType.LONG);
            if (value != null) return value.doubleValue();
        } catch (Throwable ignored) {
        }
        try {
            String value = pdc.get(key, PersistentDataType.STRING);
            if (value != null) return Double.parseDouble(value.replace(',', '.'));
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Double readPowerFromMmoItemsNbt(ItemStack item) {
        try {
            Class<?> nbtClass = Class.forName("net.Indyuce.mmoitems.api.item.nbt.NBTItem");
            Object nbtItem = createMmoNbtItem(nbtClass, item);
            if (nbtItem == null) return null;

            String[] tags = new String[] {
                    "MMOITEMS_PICKAXE_POWER",
                    "PICKAXE_POWER",
                    "pickaxe-power",
                    "pickaxe_power"
            };

            for (String tag : tags) {
                Boolean has = tryInvokeBoolean(nbtItem, "hasTag", tag);
                if (has != null && !has) continue;

                Double value = tryInvokeDouble(nbtItem, "getDouble", tag);
                if (value != null && value > 0) return value;

                Integer intValue = tryInvokeInteger(nbtItem, "getInteger", tag);
                if (intValue == null) intValue = tryInvokeInteger(nbtItem, "getInt", tag);
                if (intValue != null && intValue > 0) return intValue.doubleValue();

                String strValue = tryInvokeString(nbtItem, "getString", tag);
                if (strValue != null && !strValue.isBlank()) {
                    try {
                        return Double.parseDouble(strValue.replace(',', '.'));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Throwable throwable) {
            if (debug) getLogger().warning("No pude leer NBT de MMOItems para poder de pico: " + throwable.getClass().getSimpleName());
        }
        return null;
    }

    private Object createMmoNbtItem(Class<?> nbtClass, ItemStack item) {
        try {
            Constructor<?> constructor = nbtClass.getConstructor(ItemStack.class);
            return constructor.newInstance(item);
        } catch (Throwable ignored) {
        }

        try {
            Method get = nbtClass.getMethod("get", ItemStack.class);
            return get.invoke(null, item);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Boolean tryInvokeBoolean(Object target, String methodName, String arg) {
        try {
            Method method = target.getClass().getMethod(methodName, String.class);
            Object result = method.invoke(target, arg);
            if (result instanceof Boolean value) return value;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Double tryInvokeDouble(Object target, String methodName, String arg) {
        try {
            Method method = target.getClass().getMethod(methodName, String.class);
            Object result = method.invoke(target, arg);
            if (result instanceof Number value) return value.doubleValue();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Integer tryInvokeInteger(Object target, String methodName, String arg) {
        try {
            Method method = target.getClass().getMethod(methodName, String.class);
            Object result = method.invoke(target, arg);
            if (result instanceof Number value) return value.intValue();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String tryInvokeString(Object target, String methodName, String arg) {
        try {
            Method method = target.getClass().getMethod(methodName, String.class);
            Object result = method.invoke(target, arg);
            if (result != null) return result.toString();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Double readPowerFromLore(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null) return null;

        for (String line : meta.getLore()) {
            String clean = ChatColor.stripColor(line);
            if (clean == null) continue;
            Matcher matcher = powerLorePattern.matcher(clean);
            if (!matcher.find()) continue;

            try {
                return Double.parseDouble(matcher.group(2).replace(',', '.'));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private double vanillaPickaxePower(Material material) {
        return switch (material) {
            case WOODEN_PICKAXE -> 1;
            case STONE_PICKAXE -> 2;
            case IRON_PICKAXE -> 3;
            case GOLDEN_PICKAXE -> 2;
            case DIAMOND_PICKAXE -> 4;
            case NETHERITE_PICKAXE -> 5;
            default -> 0;
        };
    }

    private double vanillaAxePower(Material material) {
        return switch (material) {
            case WOODEN_AXE -> 1;
            case STONE_AXE -> 2;
            case IRON_AXE -> 3;
            case GOLDEN_AXE -> 2;
            case DIAMOND_AXE -> 4;
            case NETHERITE_AXE -> 5;
            default -> 0;
        };
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return Integer.toString((int) Math.rint(value));
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private void playConfiguredSound(Location location, String soundName, float volume, float pitch) {
        if (soundName == null || soundName.isBlank()) return;
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            location.getWorld().playSound(location, sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            if (debug) getLogger().warning("Sonido inválido: " + soundName);
        }
    }

    private void generateInChunk(Chunk chunk, boolean force, boolean fromCommand) {
        if (ores.isEmpty() && treeNodes.isEmpty()) return;

        if (markGeneratedChunks && !force) {
            String marked = chunk.getPersistentDataContainer().get(generatedChunkKey, PersistentDataType.STRING);
            if (marked != null) return;
        }

        int placed = 0;
        World world = chunk.getWorld();

        for (OreDefinition ore : ores.values()) {
            if (!ore.worlds.isEmpty() && !ore.worlds.contains(world.getName())) continue;
            if (!force && random.nextDouble() > ore.chunkChance) continue;

            for (int i = 0; i < ore.veinsPerChunk; i++) {
                placed += generateVein(chunk, ore, force);
            }
        }

        for (TreeNodeDefinition node : treeNodes.values()) {
            if (!node.worlds.isEmpty() && !node.worlds.contains(world.getName())) continue;
            if (!force && random.nextDouble() > node.chunkChance) continue;

            for (int i = 0; i < node.nodesPerChunk; i++) {
                placed += generateTreeNode(chunk, node);
            }
        }

        if (markGeneratedChunks) {
            chunk.getPersistentDataContainer().set(generatedChunkKey, PersistentDataType.STRING, "true");
        }

        if (debug && (placed > 0 || fromCommand)) {
            getLogger().info("Chunk " + chunk.getX() + "," + chunk.getZ() + " generado. Cabezas colocadas: " + placed);
        }
    }

    private int generateVein(Chunk chunk, OreDefinition ore, boolean force) {
        World world = chunk.getWorld();
        int minY = Math.max(world.getMinHeight(), ore.minY);
        int maxY = Math.min(world.getMaxHeight() - 1, ore.maxY);
        if (minY > maxY) return 0;

        Block start = null;
        for (int attempt = 0; attempt < 96; attempt++) {
            int x = (chunk.getX() << 4) + random.nextInt(16);
            int y = minY + random.nextInt(maxY - minY + 1);
            int z = (chunk.getZ() << 4) + random.nextInt(16);
            Block candidate = world.getBlockAt(x, y, z);
            if (canPlaceOreAt(candidate, ore)) {
                start = candidate;
                break;
            }
        }

        if (start == null) return 0;

        int targetAmount = ore.veinMin + random.nextInt(ore.veinMax - ore.veinMin + 1);
        int placed = 0;
        Block current = start;

        for (int i = 0; i < targetAmount; i++) {
            if (current != null && canPlaceOreAt(current, ore)) {
                if (placeHeadOre(current, ore)) placed++;
            }

            current = findNearbyReplaceable(current, ore);
            if (current == null && !force) break;
        }

        return placed;
    }

    private Block findNearbyReplaceable(Block origin, OreDefinition ore) {
        if (origin == null) return null;

        BlockFace[] faces = new BlockFace[] {
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
                BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
        };

        for (int i = 0; i < 8; i++) {
            BlockFace face = faces[random.nextInt(faces.length)];
            Block next = origin.getRelative(face);
            if (canPlaceOreAt(next, ore)) return next;
        }
        return null;
    }

    private boolean canPlaceOreAt(Block block, OreDefinition ore) {
        if (block == null || !ore.replace.contains(block.getType())) return false;
        if (!ore.avoidNearMaterials.isEmpty() && isNearAvoidedMaterial(block, ore)) return false;
        return true;
    }

    private boolean isNearAvoidedMaterial(Block block, OreDefinition ore) {
        BlockFace[] faces = new BlockFace[] {
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
                BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
        };

        for (BlockFace face : faces) {
            if (ore.avoidNearMaterials.contains(block.getRelative(face).getType())) return true;
        }
        return false;
    }

    private Set<Material> defaultVanillaOreMaterials() {
        Set<Material> set = new HashSet<>();
        String[] names = new String[] {
                "COAL_ORE", "DEEPSLATE_COAL_ORE",
                "COPPER_ORE", "DEEPSLATE_COPPER_ORE",
                "IRON_ORE", "DEEPSLATE_IRON_ORE",
                "GOLD_ORE", "DEEPSLATE_GOLD_ORE",
                "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE",
                "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE",
                "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE",
                "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE",
                "NETHER_GOLD_ORE", "NETHER_QUARTZ_ORE",
                "ANCIENT_DEBRIS"
        };
        for (String name : names) {
            Material mat = Material.matchMaterial(name);
            if (mat != null) set.add(mat);
        }
        return set;
    }

    private int generateTreeNode(Chunk chunk, TreeNodeDefinition node) {
        World world = chunk.getWorld();
        int minY = Math.max(world.getMinHeight(), node.minY);
        int maxY = Math.min(world.getMaxHeight() - 1, node.maxY);
        if (minY > maxY) return 0;

        for (int attempt = 0; attempt < 160; attempt++) {
            int x = (chunk.getX() << 4) + random.nextInt(16);
            int y = minY + random.nextInt(maxY - minY + 1);
            int z = (chunk.getZ() << 4) + random.nextInt(16);
            Block log = world.getBlockAt(x, y, z);
            if (!node.attachTo.contains(log.getType())) continue;

            BlockFace face = findFreeSideForNode(log, node);
            if (face == null) continue;

            Block target = log.getRelative(face);
            if (placeTreeNode(target, face, node)) return 1;
        }

        return 0;
    }

    private BlockFace findFreeSideForNode(Block log, TreeNodeDefinition node) {
        BlockFace[] faces = new BlockFace[] {
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
        };

        for (int i = 0; i < faces.length; i++) {
            BlockFace face = faces[random.nextInt(faces.length)];
            Block target = log.getRelative(face);
            if (!isAirForTreeNode(target)) continue;
            if (node.onlyOnSurfaceLogs && !hasEnoughAirAroundNode(target)) continue;
            return face;
        }
        return null;
    }

    private boolean isAirForTreeNode(Block block) {
        Material type = block.getType();
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
    }

    private boolean hasEnoughAirAroundNode(Block target) {
        int air = 0;
        BlockFace[] faces = new BlockFace[] {
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP
        };
        for (BlockFace face : faces) {
            if (target.getRelative(face).getType().isAir()) air++;
        }
        return air >= 2;
    }

    private boolean placeTreeNode(Block block, BlockFace facing, TreeNodeDefinition node) {
        block.setType(Material.PLAYER_WALL_HEAD, node.applyPhysicsOnPlace);

        BlockData data = block.getBlockData();
        if (data instanceof Directional directional) {
            directional.setFacing(facing);
            block.setBlockData(directional, node.applyPhysicsOnPlace);
        }

        BlockState state = block.getState();
        if (!(state instanceof Skull skull)) {
            return false;
        }

        applyTexture(skull, node.textureHash);

        PersistentDataContainer pdc = skull.getPersistentDataContainer();
        pdc.set(nodeKey, PersistentDataType.STRING, node.key);
        pdc.set(blockIdKey, PersistentDataType.STRING, node.mmoitemsBlockId);
        pdc.set(dropTypeKey, PersistentDataType.STRING, node.dropType);
        pdc.set(dropIdKey, PersistentDataType.STRING, node.dropId);

        skull.update(true, node.applyPhysicsOnPlace);
        return true;
    }

    private boolean placeHeadOre(Block block, OreDefinition ore) {
        block.setType(Material.PLAYER_HEAD, ore.applyPhysicsOnPlace);

        BlockState state = block.getState();
        if (!(state instanceof Skull skull)) {
            return false;
        }

        applyTexture(skull, ore.textureHash);

        BlockFace[] rotations = new BlockFace[] {
                BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST,
                BlockFace.EAST_NORTH_EAST, BlockFace.EAST, BlockFace.EAST_SOUTH_EAST,
                BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST, BlockFace.SOUTH,
                BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
                BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST,
                BlockFace.NORTH_NORTH_WEST
        };
        skull.setRotation(rotations[random.nextInt(rotations.length)]);

        PersistentDataContainer pdc = skull.getPersistentDataContainer();
        pdc.set(oreKey, PersistentDataType.STRING, ore.key);
        pdc.set(blockIdKey, PersistentDataType.STRING, ore.mmoitemsBlockId);
        pdc.set(dropTypeKey, PersistentDataType.STRING, ore.dropType);
        pdc.set(dropIdKey, PersistentDataType.STRING, ore.dropId);

        skull.update(true, ore.applyPhysicsOnPlace);
        return true;
    }

    private void applyTexture(Skull skull, String textureHash) {
        PlayerProfile profile = getCachedProfile(textureHash);
        if (profile != null) {
            skull.setOwnerProfile(profile);
        }
    }

    private PlayerProfile getCachedProfile(String textureHash) {
        if (textureHash == null || textureHash.isBlank()) return null;

        PlayerProfile cached = profileCache.get(textureHash);
        if (cached != null) return cached;

        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(("mdvheadores:" + textureHash).getBytes(StandardCharsets.UTF_8)), null);
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL("http://textures.minecraft.net/texture/" + textureHash));
            profile.setTextures(textures);
            profileCache.put(textureHash, profile);
            return profile;
        } catch (MalformedURLException exception) {
            getLogger().warning("URL de textura inválida para cabeza: " + textureHash);
            return null;
        }
    }

    private String extractTextureHash(String rawTexture) {
        if (rawTexture == null || rawTexture.isBlank()) return null;
        rawTexture = rawTexture.trim();

        if (rawTexture.startsWith("http://") || rawTexture.startsWith("https://")) {
            return textureHashFromUrl(rawTexture);
        }

        // Si el usuario pega solo el hash de textures.minecraft.net/texture/<hash>.
        if (rawTexture.matches("^[a-fA-F0-9]{32,}$")) {
            return rawTexture;
        }

        try {
            String decoded = new String(Base64.getDecoder().decode(rawTexture), StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("textures\\.minecraft\\.net/texture/([a-zA-Z0-9]+)").matcher(decoded);
            if (matcher.find()) return matcher.group(1);
        } catch (IllegalArgumentException ignored) {
            // No era base64 válido.
        }

        return null;
    }

    private String textureHashFromUrl(String url) {
        int index = url.lastIndexOf("/texture/");
        if (index < 0) return null;
        return url.substring(index + "/texture/".length()).replace("\"", "").trim();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("mdvheadores")) return false;
        if (!sender.hasPermission("mdvheadores.admin")) {
            sender.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §cNo tienes permiso para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §eUsa: /mdvheadores reload, /mdvheadores inspect o /mdvheadores generate <radio> [force]");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            generationQueue.clear();
            queuedChunkKeys.clear();
            restartGenerationTask();
            sender.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §aMDVHeadOres recargado. Vetas: §f" + ores.size() + " §aNodos: §f" + treeNodes.size());
            return true;
        }

        if (args[0].equalsIgnoreCase("inspect")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Este comando solo puede usarlo un jugador.");
                return true;
            }

            Block target = player.getTargetBlockExact(8);
            if (target == null) {
                sender.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §cNo estás mirando ningún bloque cercano.");
                return true;
            }

            sender.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §eBloque: §f" + target.getType().name() + " §7en §f" + target.getWorld().getName() + " " + target.getX() + " " + target.getY() + " " + target.getZ());
            BlockState state = target.getState();
            if (state instanceof TileState tileState) {
                PersistentDataContainer pdc = tileState.getPersistentDataContainer();
                String oreName = pdc.get(oreKey, PersistentDataType.STRING);
                if (oreName != null) {
                    sender.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §aEs una veta MDVHeadOres: §f" + oreName);
                } else {
                    String nodeName = pdc.get(nodeKey, PersistentDataType.STRING);
                    if (nodeName != null) {
                        sender.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §aEs un nodo de árbol MDVHeadOres: §f" + nodeName);
                    } else {
                        sender.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §7No tiene marca de veta/nodo MDVHeadOres.");
                    }
                }
            } else {
                sender.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §7No es TileState; no puede tener marca del plugin.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("generate")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Este comando solo puede usarlo un jugador.");
                return true;
            }

            int radius = 2;
            if (args.length >= 2) {
                try {
                    radius = Math.max(0, Math.min(16, Integer.parseInt(args[1])));
                } catch (NumberFormatException ignored) {
                    sender.sendMessage("§cRadio inválido.");
                    return true;
                }
            }

            boolean force = args.length >= 3 && args[2].equalsIgnoreCase("force");
            Chunk center = player.getLocation().getChunk();
            int count = 0;
            int queued = 0;
            for (int cx = center.getX() - radius; cx <= center.getX() + radius; cx++) {
                for (int cz = center.getZ() - radius; cz <= center.getZ() + radius; cz++) {
                    Chunk chunk = player.getWorld().getChunkAt(cx, cz);
                    if (generationThrottleEnabled && commandGenerateUsesQueue) {
                        if (enqueueChunk(chunk, force, true)) queued++;
                    } else {
                        generateInChunk(chunk, force, true);
                    }
                    count++;
                }
            }

            if (generationThrottleEnabled && commandGenerateUsesQueue) {
                sender.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §aGeneración encolada en §f" + queued + "§a/§f" + count + "§a chunks. Force: §f" + force + "§a. Cola actual: §f" + generationQueue.size());
            } else {
                sender.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §aGeneración ejecutada en §f" + count + "§a chunks. Force: §f" + force);
            }
            return true;
        }

        sender.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §eUsa: /mdvheadores reload, /mdvheadores inspect o /mdvheadores generate <radio> [force]");
        return true;
    }

    private static final class PendingChunk {
        UUID worldId;
        String worldName;
        int x;
        int z;
        boolean force;
        boolean fromCommand;
        long readyAtMillis;
    }

    private static final class IntRange {
        final int min;
        final int max;

        IntRange(int min, int max) {
            this.min = min;
            this.max = max;
        }
    }

    private static final class MmoCoreXpSettings {
        boolean enabled;
        String professionId;
        IntRange professionAmount;
        IntRange mainAmount;
        boolean split;
    }

    private static final class TreeNodeDefinition {
        String key;
        String mmoitemsBlockId;
        String textureHash;
        String displayName;
        Set<String> worlds;
        List<Material> attachTo;
        int minY;
        int maxY;
        double chunkChance;
        int nodesPerChunk;
        String dropType;
        String dropId;
        int dropAmount;
        boolean preventVanillaDrops;
        boolean ignoreSilkTouch;
        boolean dropNaturally;
        double requiredAxePower;
        String breakSound;
        String failSound;
        String fallbackCommand;
        boolean applyPhysicsOnPlace;
        boolean onlyOnSurfaceLogs;
        MmoCoreXpSettings mmocoreXp;
    }

    private static final class OreDefinition {
        String key;
        String mmoitemsBlockId;
        String textureHash;
        String displayName;
        Set<String> worlds;
        List<Material> replace;
        int minY;
        int maxY;
        double chunkChance;
        int veinsPerChunk;
        int veinMin;
        int veinMax;
        String placement;
        String dropType;
        String dropId;
        int dropAmount;
        boolean preventVanillaDrops;
        boolean ignoreSilkTouch;
        boolean dropNaturally;
        double requiredPickaxePower;
        String breakSound;
        String failSound;
        String fallbackCommand;
        boolean applyPhysicsOnPlace;
        Set<Material> avoidNearMaterials;
        MmoCoreXpSettings mmocoreXp;
    }
}
