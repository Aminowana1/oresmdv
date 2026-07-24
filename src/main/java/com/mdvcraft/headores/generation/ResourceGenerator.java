package com.mdvcraft.headores.generation;

import com.mdvcraft.headores.config.PluginSettings;
import com.mdvcraft.headores.config.ResourceRegistry;
import com.mdvcraft.headores.model.OreDefinition;
import com.mdvcraft.headores.model.ResourceDefinition;
import com.mdvcraft.headores.model.TreeNodeDefinition;
import com.mdvcraft.headores.tracking.ChunkRollTracker;
import com.mdvcraft.headores.tracking.ResourceKeys;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ResourceGenerator {
    private static final BlockFace[] VEIN_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };
    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };
    private static final BlockFace[] HEAD_ROTATIONS = {
            BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST,
            BlockFace.EAST_NORTH_EAST, BlockFace.EAST, BlockFace.EAST_SOUTH_EAST,
            BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST, BlockFace.SOUTH,
            BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST,
            BlockFace.NORTH_NORTH_WEST
    };

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final ResourceRegistry registry;
    private final ChunkRollTracker tracker;
    private final ResourceKeys keys;
    private final Map<String, PlayerProfile> profileCache = new HashMap<>();

    public ResourceGenerator(
            JavaPlugin plugin,
            PluginSettings settings,
            ResourceRegistry registry,
            ChunkRollTracker tracker,
            ResourceKeys keys
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.registry = registry;
        this.tracker = tracker;
        this.keys = keys;
    }

    public GenerationResult generate(Chunk chunk, boolean force, boolean fromCommand) {
        if (registry.allResources().isEmpty()) return new GenerationResult(0, 0, 0);

        long mask = settings.tracking().enabled() ? tracker.readMask(chunk) : 0L;
        int rolled = 0;
        int placed = 0;
        int failures = 0;
        String worldName = chunk.getWorld().getName();

        for (ResourceDefinition resource : registry.allResources()) {
            if (!force && settings.tracking().enabled() && tracker.hasRolled(mask, resource.trackingMask())) {
                continue;
            }

            boolean completed = false;
            try {
                rolled++;
                if (resource.isAllowedInWorld(worldName)) {
                    if (resource instanceof OreDefinition ore) {
                        if (force || ThreadLocalRandom.current().nextDouble() <= ore.chunkChance()) {
                            for (int i = 0; i < ore.veinsPerChunk(); i++) {
                                placed += generateVein(chunk, ore, force);
                            }
                        }
                    } else if (resource instanceof TreeNodeDefinition node) {
                        if (force || ThreadLocalRandom.current().nextDouble() <= node.chunkChance()) {
                            for (int i = 0; i < node.nodesPerChunk(); i++) {
                                placed += generateTreeNode(chunk, node);
                            }
                        }
                    }
                }
                completed = true;
            } catch (Throwable throwable) {
                failures++;
                plugin.getLogger().severe("Error generando el recurso '" + resource.key() + "' en chunk "
                        + chunk.getX() + "," + chunk.getZ() + ": " + throwable.getClass().getSimpleName()
                        + " - " + throwable.getMessage());
            }

            // El bit se marca tras completar la tirada, incluso cuando la probabilidad falla o no hay posición válida.
            // Si ocurrió una excepción, se deja pendiente para poder reintentarlo en otra carga.
            if (completed && settings.tracking().enabled()) {
                mask = tracker.markRolled(mask, resource.trackingMask());
            }
        }

        tracker.writeMask(chunk, mask);

        if (settings.debug() && settings.debugLogGeneratedChunks() && (placed > 0 || fromCommand)) {
            plugin.getLogger().info("Chunk " + chunk.getX() + "," + chunk.getZ()
                    + " procesado. Tiradas nuevas: " + rolled + ", cabezas colocadas: " + placed
                    + (failures > 0 ? ", errores: " + failures : ""));
        }
        return new GenerationResult(rolled, placed, failures);
    }

    private int generateVein(Chunk chunk, OreDefinition ore, boolean force) {
        World world = chunk.getWorld();
        int minY = Math.max(world.getMinHeight(), ore.minY());
        int maxY = Math.min(world.getMaxHeight() - 1, ore.maxY());
        if (minY > maxY) return 0;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Block start = null;
        for (int attempt = 0; attempt < 96; attempt++) {
            int x = (chunk.getX() << 4) + random.nextInt(16);
            int y = random.nextInt(minY, maxY + 1);
            int z = (chunk.getZ() << 4) + random.nextInt(16);
            Block candidate = world.getBlockAt(x, y, z);
            if (canPlaceOreAt(candidate, ore)) {
                start = candidate;
                break;
            }
        }
        if (start == null) return 0;

        int targetAmount = random.nextInt(ore.veinMin(), ore.veinMax() + 1);
        int placed = 0;
        Block current = start;
        for (int i = 0; i < targetAmount; i++) {
            if (current != null && canPlaceOreAt(current, ore) && placeHeadOre(current, ore)) {
                placed++;
            }
            current = findNearbyReplaceable(current, ore);
            if (current == null && !force) break;
        }
        return placed;
    }

    private Block findNearbyReplaceable(Block origin, OreDefinition ore) {
        if (origin == null) return null;
        int start = ThreadLocalRandom.current().nextInt(VEIN_FACES.length);
        for (int i = 0; i < VEIN_FACES.length; i++) {
            Block next = origin.getRelative(VEIN_FACES[(start + i) % VEIN_FACES.length]);
            if (canPlaceOreAt(next, ore)) return next;
        }
        return null;
    }

    private boolean canPlaceOreAt(Block block, OreDefinition ore) {
        if (block == null || !ore.replace().contains(block.getType())) return false;
        return ore.avoidNearMaterials().isEmpty() || !isNearAvoidedMaterial(block, ore);
    }

    private boolean isNearAvoidedMaterial(Block block, OreDefinition ore) {
        for (BlockFace face : VEIN_FACES) {
            if (ore.avoidNearMaterials().contains(block.getRelative(face).getType())) return true;
        }
        return false;
    }

    private int generateTreeNode(Chunk chunk, TreeNodeDefinition node) {
        World world = chunk.getWorld();
        int minY = Math.max(world.getMinHeight(), node.minY());
        int maxY = Math.min(world.getMaxHeight() - 1, node.maxY());
        if (minY > maxY) return 0;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 160; attempt++) {
            int x = (chunk.getX() << 4) + random.nextInt(16);
            int y = random.nextInt(minY, maxY + 1);
            int z = (chunk.getZ() << 4) + random.nextInt(16);
            Block log = world.getBlockAt(x, y, z);
            if (!node.attachTo().contains(log.getType())) continue;

            BlockFace face = findFreeSideForNode(log, node);
            if (face == null) continue;
            if (placeTreeNode(log.getRelative(face), face, node)) return 1;
        }
        return 0;
    }

    private BlockFace findFreeSideForNode(Block log, TreeNodeDefinition node) {
        int start = ThreadLocalRandom.current().nextInt(HORIZONTAL_FACES.length);
        for (int i = 0; i < HORIZONTAL_FACES.length; i++) {
            BlockFace face = HORIZONTAL_FACES[(start + i) % HORIZONTAL_FACES.length];
            Block target = log.getRelative(face);
            if (!target.getType().isAir()) continue;
            if (node.onlyOnSurfaceLogs() && !hasEnoughAirAroundNode(target)) continue;
            return face;
        }
        return null;
    }

    private boolean hasEnoughAirAroundNode(Block target) {
        int air = 0;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP}) {
            if (target.getRelative(face).getType().isAir()) air++;
        }
        return air >= 2;
    }

    private boolean placeTreeNode(Block block, BlockFace facing, TreeNodeDefinition node) {
        block.setType(Material.PLAYER_WALL_HEAD, node.applyPhysicsOnPlace());
        BlockData data = block.getBlockData();
        if (data instanceof Directional directional) {
            directional.setFacing(facing);
            block.setBlockData(directional, node.applyPhysicsOnPlace());
        }

        BlockState state = block.getState();
        if (!(state instanceof Skull skull)) return false;
        applyTexture(skull, node.textureHash());
        tagResource(skull.getPersistentDataContainer(), node, false);
        skull.update(true, node.applyPhysicsOnPlace());
        return true;
    }

    private boolean placeHeadOre(Block block, OreDefinition ore) {
        block.setType(Material.PLAYER_HEAD, ore.applyPhysicsOnPlace());
        BlockState state = block.getState();
        if (!(state instanceof Skull skull)) return false;
        applyTexture(skull, ore.textureHash());
        skull.setRotation(HEAD_ROTATIONS[ThreadLocalRandom.current().nextInt(HEAD_ROTATIONS.length)]);
        tagResource(skull.getPersistentDataContainer(), ore, true);
        skull.update(true, ore.applyPhysicsOnPlace());
        return true;
    }

    private void tagResource(PersistentDataContainer pdc, ResourceDefinition resource, boolean ore) {
        pdc.set(ore ? keys.oreKey() : keys.nodeKey(), PersistentDataType.STRING, resource.key());
        pdc.set(keys.blockIdKey(), PersistentDataType.STRING, resource.mmoitemsBlockId());
        pdc.set(keys.dropTypeKey(), PersistentDataType.STRING, resource.dropType());
        pdc.set(keys.dropIdKey(), PersistentDataType.STRING, resource.dropId());
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
                    UUID.nameUUIDFromBytes(("mdvheadores:" + textureHash).getBytes(StandardCharsets.UTF_8)), null);
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(URI.create("https://textures.minecraft.net/texture/" + textureHash).toURL());
            profile.setTextures(textures);
            profileCache.put(textureHash, profile);
            return profile;
        } catch (MalformedURLException exception) {
            plugin.getLogger().warning("Textura inválida: " + textureHash);
            return null;
        }
    }

    public void clearCaches() {
        profileCache.clear();
    }
}
