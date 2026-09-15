package com.mdvcraft.headores.listener;

import com.mdvcraft.headores.config.PluginSettings;
import com.mdvcraft.headores.tracking.ResourceKeys;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ResourceProtectionListener implements Listener {
    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final ResourceKeys keys;

    public ResourceProtectionListener(JavaPlugin plugin, PluginSettings settings, ResourceKeys keys) {
        this.plugin = plugin;
        this.settings = settings;
        this.keys = keys;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        if (!settings.protection().preventFluidDestruction()
                || !settings.protection().fluids().contains(event.getBlock().getType())) return;
        if (!isProtectedResource(event.getToBlock())) return;
        event.setCancelled(true);
        debug("Flujo de " + event.getBlock().getType() + " bloqueado en " + location(event.getToBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!settings.protection().preventFluidDestruction() || !isProtectedBucket(event.getBucket())) return;
        Block target = event.getBlock();
        Block relative = event.getBlockClicked().getRelative(event.getBlockFace());
        Block protectedBlock = isProtectedResource(target) ? target : (isProtectedResource(relative) ? relative : null);
        if (protectedBlock == null) return;
        event.setCancelled(true);
        debug("Cubeta " + event.getBucket() + " bloqueada en " + location(protectedBlock));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (settings.protection().preventPhysicsDestruction() && isProtectedResource(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        if (!settings.protection().preventFluidDestruction() || !isProtectedResource(event.getBlock())) return;
        Material source = event.getSourceBlock() == null ? null : event.getSourceBlock().getType();
        if (settings.protection().fluids().contains(source)
                || settings.protection().fluids().contains(event.getChangedType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        if (!settings.protection().preventExplosionDestruction()) return;
        protectExplosionList(event.blockList(), "explosión de bloque");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        if (!settings.protection().preventExplosionDestruction()) return;
        protectExplosionList(event.blockList(), "explosión de entidad");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!settings.protection().preventPistonDestruction()) return;
        if (pistonTouchesProtectedResource(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
            debug("Pistón bloqueado para proteger un recurso en " + location(event.getBlock()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!settings.protection().preventPistonDestruction()) return;
        if (pistonTouchesProtectedResource(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
            debug("Retracción de pistón bloqueada para proteger un recurso en " + location(event.getBlock()));
        }
    }

    private void protectExplosionList(List<Block> blocks, String source) {
        int before = blocks.size();
        blocks.removeIf(block -> isProtectedResource(block) || isSupportForProtectedResource(block));
        int protectedCount = before - blocks.size();
        if (protectedCount > 0) debug("Se protegieron " + protectedCount + " bloque(s) ante " + source + ".");
    }

    private boolean pistonTouchesProtectedResource(List<Block> movedBlocks, BlockFace direction) {
        for (Block block : movedBlocks) {
            if (isProtectedResource(block) || isSupportForProtectedResource(block)) return true;
            Block destination = block.getRelative(direction);
            if (isProtectedResource(destination) || isSupportForProtectedResource(destination)) return true;
        }
        return false;
    }

    private boolean isSupportForProtectedResource(Block block) {
        Block above = block.getRelative(BlockFace.UP);
        if (isProtectedResource(above)) return true;

        for (BlockFace face : HORIZONTAL_FACES) {
            Block adjacent = block.getRelative(face);
            if (adjacent.getType() != Material.PLAYER_WALL_HEAD || !isProtectedResource(adjacent)) continue;
            BlockData data = adjacent.getBlockData();
            if (data instanceof Directional directional
                    && adjacent.getRelative(directional.getFacing().getOppositeFace()).equals(block)) {
                return true;
            }
        }
        return false;
    }

    private boolean isProtectedResource(Block block) {
        if (block == null) return false;
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) return false;
        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        return pdc.has(keys.oreKey(), PersistentDataType.STRING)
                || pdc.has(keys.nodeKey(), PersistentDataType.STRING)
                || pdc.has(keys.lootNodeKey(), PersistentDataType.STRING);
    }

    private boolean isProtectedBucket(Material bucket) {
        return (bucket == Material.WATER_BUCKET && settings.protection().fluids().contains(Material.WATER))
                || (bucket == Material.LAVA_BUCKET && settings.protection().fluids().contains(Material.LAVA));
    }

    private String location(Block block) {
        return block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private void debug(String message) {
        if (settings.debug()) plugin.getLogger().info(message);
    }
}
