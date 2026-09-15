package com.mdvcraft.headores.loot.listener;

import com.mdvcraft.headores.loot.model.LootContainerType;
import com.mdvcraft.headores.loot.model.LootNodeDefinition;
import com.mdvcraft.headores.loot.service.LootInventoryHolder;
import com.mdvcraft.headores.loot.service.LootNodeService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

public final class LootNodeListener implements Listener {
    private static final BlockFace[] HORIZONTAL = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final JavaPlugin plugin;
    private final LootNodeService service;

    public LootNodeListener(JavaPlugin plugin, LootNodeService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        LootNodeDefinition node = service.definition(block);
        if (node == null) return;

        if (node.containerType() == LootContainerType.DECORATED_POT) {
            event.setCancelled(true);
            service.claimPot(block);
            return;
        }
        if (node.containerType() == LootContainerType.PLAYER_HEAD) {
            event.setCancelled(true);
            service.openVirtual(event.getPlayer(), block, node);
            return;
        }

        service.ensurePhysicalInventoryGenerated(block, node);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        LootNodeDefinition node = service.definition(block);
        if (node != null) {
            event.setDropItems(false);
            event.setExpToDrop(0);
            event.setCancelled(true);
            if (node.containerType() == LootContainerType.DECORATED_POT) {
                service.claimPot(block);
            } else {
                event.getPlayer().sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §eVacía este contenedor para retirarlo.");
            }
            return;
        }

        Block above = block.getRelative(BlockFace.UP);
        if (service.isLootNode(above)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §cNo puedes romper el soporte de un contenedor de botín.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder(false);
        if (holder instanceof LootInventoryHolder virtual) {
            plugin.getServer().getScheduler().runTask(plugin, () -> service.saveVirtual(virtual, inventory));
            return;
        }
        Block block = holderBlock(holder);
        if (block != null && service.isLootNode(block)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> service.removeIfEmptyPhysical(block));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Block source = holderBlock(event.getSource().getHolder(false));
        Block destination = holderBlock(event.getDestination().getHolder(false));
        if ((source != null && service.isLootNode(source))
                || (destination != null && service.isLootNode(destination))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.CHEST) return;
        for (BlockFace face : HORIZONTAL) {
            Block adjacent = event.getBlockPlaced().getRelative(face);
            LootNodeDefinition node = service.definition(adjacent);
            if (node != null && node.containerType() == LootContainerType.CHEST) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §cNo puedes unir un cofre a un contenedor de botín.");
                return;
            }
        }
    }

    private static Block holderBlock(InventoryHolder holder) {
        return holder instanceof BlockInventoryHolder blockHolder ? blockHolder.getBlock() : null;
    }
}
