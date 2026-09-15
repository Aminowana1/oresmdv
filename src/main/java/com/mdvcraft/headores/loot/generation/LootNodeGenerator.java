package com.mdvcraft.headores.loot.generation;

import com.mdvcraft.headores.loot.model.LootContainerType;
import com.mdvcraft.headores.loot.model.LootNodeDefinition;
import com.mdvcraft.headores.tracking.ResourceKeys;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DecoratedPot;
import org.bukkit.block.Skull;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class LootNodeGenerator {
    private static final BlockFace[] HEAD_ROTATIONS = {
            BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST,
            BlockFace.EAST_NORTH_EAST, BlockFace.EAST, BlockFace.EAST_SOUTH_EAST,
            BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST, BlockFace.SOUTH,
            BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST,
            BlockFace.NORTH_NORTH_WEST
    };
    private static final BlockFace[] HORIZONTAL = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final JavaPlugin plugin;
    private final ResourceKeys keys;
    private final Map<String, PlayerProfile> profileCache = new HashMap<>();
    private final List<Material> potterySherds;

    public LootNodeGenerator(JavaPlugin plugin, ResourceKeys keys) {
        this.plugin = plugin;
        this.keys = keys;
        this.potterySherds = new ArrayList<>(Tag.ITEMS_DECORATED_POT_SHERDS.getValues());
    }

    /**
     * Coloca manualmente un loot node en un bloque exacto. No consume la
     * probabilidad del chunk ni toca su tracking-bit; solo valida soporte y
     * espacio usando las mismas reglas que la generación natural.
     */
    public boolean placeAt(Block target, LootNodeDefinition node) {
        if (target == null || node == null) return false;
        if (!canPlace(target, node)) return false;
        return place(target, node);
    }

    public int generate(Chunk chunk, LootNodeDefinition node) {
        World world = chunk.getWorld();
        int minY = Math.max(world.getMinHeight() + 1, node.minY());
        int maxY = Math.min(world.getMaxHeight() - node.containerType().verticalClearance(), node.maxY());
        if (minY > maxY) return 0;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < node.placementAttempts(); attempt++) {
            int x = (chunk.getX() << 4) + random.nextInt(16);
            int y = random.nextInt(minY, maxY + 1);
            int z = (chunk.getZ() << 4) + random.nextInt(16);
            Block target = world.getBlockAt(x, y, z);
            if (!canPlace(target, node)) continue;
            if (place(target, node)) return 1;
        }
        return 0;
    }

    private boolean canPlace(Block target, LootNodeDefinition node) {
        Block support = target.getRelative(BlockFace.DOWN);
        Material supportType = support.getType();
        if (!supportType.isSolid() || supportType == Material.BARRIER || support.isLiquid()) return false;

        int clearance = node.containerType().verticalClearance();
        for (int i = 0; i < clearance; i++) {
            Block space = target.getRelative(BlockFace.UP, i);
            if (!node.replaceableSpace().contains(space.getType())) return false;
        }

        if (node.containerType() == LootContainerType.CHEST) {
            for (BlockFace face : HORIZONTAL) {
                Material adjacent = target.getRelative(face).getType();
                if (adjacent == Material.CHEST || adjacent == Material.TRAPPED_CHEST) return false;
            }
        }
        return true;
    }

    private boolean place(Block target, LootNodeDefinition node) {
        for (int i = 0; i < node.containerType().verticalClearance(); i++) {
            Block space = target.getRelative(BlockFace.UP, i);
            if (!space.getType().isAir()) space.setType(Material.AIR, false);
        }

        target.setType(node.containerType().material(), false);
        BlockState state = target.getState();
        if (!(state instanceof TileState tile)) {
            target.setType(Material.AIR, false);
            return false;
        }

        if (state instanceof Skull skull) {
            applyTexture(skull, node.textureHash());
            skull.setRotation(HEAD_ROTATIONS[ThreadLocalRandom.current().nextInt(HEAD_ROTATIONS.length)]);
        } else if (state instanceof DecoratedPot pot) {
            randomizePot(pot);
        } else if (state instanceof Container container) {
            container.setCustomName(ChatColor.translateAlternateColorCodes('&', node.displayName()));
        }

        PersistentDataContainer pdc = tile.getPersistentDataContainer();
        pdc.set(keys.lootNodeKey(), PersistentDataType.STRING, node.key());
        pdc.set(keys.lootGeneratedKey(), PersistentDataType.BYTE, (byte) 0);
        tile.update(true, false);
        return true;
    }

    private void randomizePot(DecoratedPot pot) {
        if (potterySherds.isEmpty()) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (DecoratedPot.Side side : DecoratedPot.Side.values()) {
            pot.setSherd(side, potterySherds.get(random.nextInt(potterySherds.size())));
        }
    }

    private void applyTexture(Skull skull, String textureHash) {
        PlayerProfile profile = getCachedProfile(textureHash);
        if (profile != null) skull.setOwnerProfile(profile);
    }

    private PlayerProfile getCachedProfile(String textureHash) {
        if (textureHash == null || textureHash.isBlank()) return null;
        PlayerProfile cached = profileCache.get(textureHash);
        if (cached != null) return cached;
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(
                    UUID.nameUUIDFromBytes(("mdvheadores-loot:" + textureHash).getBytes(StandardCharsets.UTF_8)), null);
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(URI.create("https://textures.minecraft.net/texture/" + textureHash).toURL());
            profile.setTextures(textures);
            profileCache.put(textureHash, profile);
            return profile;
        } catch (MalformedURLException exception) {
            plugin.getLogger().warning("Textura inválida de loot node: " + textureHash);
            return null;
        }
    }

    public void clearCaches() {
        profileCache.clear();
    }
}
