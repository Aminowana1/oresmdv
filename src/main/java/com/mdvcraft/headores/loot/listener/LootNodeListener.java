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
            // La interacción entrega el premio una sola vez, pero la vasija queda.
            event.setCancelled(true);
            if (!service.claimPot(block)) sendNoLoot(event.getPlayer());
            return;
        }
        if (node.containerType() == LootContainerType.PLAYER_HEAD) {
            event.setCancelled(true);
            if (!service.openVirtual(event.getPlayer(), block, node)) sendNoLoot(event.getPlayer());
            return;
        }

        // CHEST/BARREL: genera el loot justo antes de que Minecraft abra el
        // inventario físico. El contenido queda en slots aleatorios y el bloque
        // nunca se elimina automáticamente al vaciarse.
        if (!service.ensurePhysicalInventoryGenerated(block, node)) {
            event.setCancelled(true);
            sendNoLoot(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        LootNodeDefinition node = service.definition(block);
        if (node != null) {
            event.setExpToDrop(0);

            if (node.containerType() == LootContainerType.PLAYER_HEAD) {
                // Las bolsas/cabezas siguen siendo el único tipo que desaparece
                // automáticamente cuando su inventario virtual queda vacío.
                event.setDropItems(false);
                event.setCancelled(true);
                event.getPlayer().sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §eVacía esta bolsa para retirarla.");
                return;
            }

            if (node.containerType() == LootContainerType.DECORATED_POT) {
                // Si nunca fue reclamada, romperla también entrega su premio.
                // No cancelamos el break: la vasija solo desaparece porque el
                // jugador la destruyó. Evitamos que además dropee la vasija item.
                event.setDropItems(false);
                if (!service.claimPot(block)) sendNoLoot(event.getPlayer());
                return;
            }

            // CHEST/BARREL: si se rompen antes del primer acceso, genera primero
            // el loot para que el contenido salga con la rotura vanilla.
            service.ensurePhysicalInventoryGenerated(block, node);
            // No cancelamos y no forzamos setDropItems(false): el contenedor y su
            // contenido se rompen de forma vanilla por decisión del jugador.
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
        }
        // Los inventarios físicos (CHEST/BARREL) nunca se eliminan al vaciarse.
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

    private static void sendNoLoot(org.bukkit.entity.Player player) {
        player.sendMessage("§6§l[§5§lMDVCRAFT§6§l]  §4»  §eEste contenedor todavía no tiene recompensas válidas configuradas.");
    }

    private static Block holderBlock(InventoryHolder holder) {
        return holder instanceof BlockInventoryHolder blockHolder ? blockHolder.getBlock() : null;
    }
}
