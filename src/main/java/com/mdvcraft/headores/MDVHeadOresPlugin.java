package com.mdvcraft.headores;

import org.bukkit.Bukkit;
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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    private NamespacedKey oreKey;
    private NamespacedKey blockIdKey;
    private NamespacedKey dropTypeKey;
    private NamespacedKey dropIdKey;
    private NamespacedKey generatedChunkKey;

    private boolean debug;
    private boolean generateOnNewChunks;
    private boolean markGeneratedChunks;
    private String defaultDropCommand;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        oreKey = new NamespacedKey(this, "ore_key");
        blockIdKey = new NamespacedKey(this, "mmoitems_block_id");
        dropTypeKey = new NamespacedKey(this, "drop_type");
        dropIdKey = new NamespacedKey(this, "drop_id");
        generatedChunkKey = new NamespacedKey(this, "generated_chunk");

        loadSettings();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("MDVHeadOres activado. Vetas cargadas: " + ores.size());
    }

    private void loadSettings() {
        reloadConfig();
        FileConfiguration cfg = getConfig();

        debug = cfg.getBoolean("debug", false);
        generateOnNewChunks = cfg.getBoolean("generate-on-new-chunks", true);
        markGeneratedChunks = cfg.getBoolean("mark-generated-chunks", true);
        defaultDropCommand = cfg.getString("default-drop-command", "mi give MATERIAL %drop_id% %player% 1");

        ores.clear();

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
            ore.preventVanillaDrops = sec.getBoolean("prevent-vanilla-drops", true);
            ore.breakSound = sec.getString("break-sound", "");
            ore.dropCommand = sec.getString("drop-command", null);

            ores.put(key, ore);
            if (debug) getLogger().info("Veta cargada: " + key + " -> " + miBlockId + " drop " + ore.dropType + ":" + ore.dropId);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!generateOnNewChunks || !event.isNewChunk()) return;
        Chunk chunk = event.getChunk();

        Bukkit.getScheduler().runTask(this, () -> generateInChunk(chunk, false, false));
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
        if (oreName == null) return;

        OreDefinition ore = ores.get(oreName);
        if (ore == null) {
            if (debug) getLogger().warning("Se rompió una veta desconocida: " + oreName);
            event.setDropItems(false);
            return;
        }

        if (ore.preventVanillaDrops) {
            event.setDropItems(false);
        }

        Player player = event.getPlayer();
        String command = ore.dropCommand;
        if (command == null || command.isBlank()) {
            command = defaultDropCommand;
        }

        command = command
                .replace("%player%", player.getName())
                .replace("%world%", block.getWorld().getName())
                .replace("%x%", Integer.toString(block.getX()))
                .replace("%y%", Integer.toString(block.getY()))
                .replace("%z%", Integer.toString(block.getZ()))
                .replace("%ore%", ore.key)
                .replace("%drop_type%", ore.dropType)
                .replace("%drop_id%", ore.dropId);

        if (command.startsWith("/")) command = command.substring(1);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

        if (ore.breakSound != null && !ore.breakSound.isBlank()) {
            try {
                Sound sound = Sound.valueOf(ore.breakSound.toUpperCase(Locale.ROOT));
                block.getWorld().playSound(block.getLocation(), sound, 0.8f, 1.15f);
            } catch (IllegalArgumentException ignored) {
                if (debug) getLogger().warning("Sonido inválido: " + ore.breakSound);
            }
        }
    }

    private void generateInChunk(Chunk chunk, boolean force, boolean fromCommand) {
        if (ores.isEmpty()) return;

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
            if (ore.replace.contains(candidate.getType())) {
                start = candidate;
                break;
            }
        }

        if (start == null) return 0;

        int targetAmount = ore.veinMin + random.nextInt(ore.veinMax - ore.veinMin + 1);
        int placed = 0;
        Block current = start;

        for (int i = 0; i < targetAmount; i++) {
            if (current != null && ore.replace.contains(current.getType())) {
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
            if (ore.replace.contains(next.getType())) return next;
        }
        return null;
    }

    private boolean placeHeadOre(Block block, OreDefinition ore) {
        block.setType(Material.PLAYER_HEAD, false);

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

        skull.update(true, false);
        return true;
    }

    private void applyTexture(Skull skull, String textureHash) {
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), null);
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL("http://textures.minecraft.net/texture/" + textureHash));
            profile.setTextures(textures);
            skull.setOwnerProfile(profile);
        } catch (MalformedURLException exception) {
            getLogger().warning("URL de textura inválida para cabeza: " + textureHash);
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
            sender.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §eUsa: /mdvheadores reload o /mdvheadores generate <radio> [force]");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            sender.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §aMDVHeadOres recargado. Vetas: §f" + ores.size());
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
            for (int cx = center.getX() - radius; cx <= center.getX() + radius; cx++) {
                for (int cz = center.getZ() - radius; cz <= center.getZ() + radius; cz++) {
                    Chunk chunk = player.getWorld().getChunkAt(cx, cz);
                    generateInChunk(chunk, force, true);
                    count++;
                }
            }

            sender.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §aGeneración ejecutada en §f" + count + "§a chunks. Force: §f" + force);
            return true;
        }

        sender.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §eUsa: /mdvheadores reload o /mdvheadores generate <radio> [force]");
        return true;
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
        boolean preventVanillaDrops;
        String breakSound;
        String dropCommand;
    }
}
