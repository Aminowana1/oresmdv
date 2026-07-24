package com.mdvcraft.headores.listener;

import com.mdvcraft.headores.api.event.MDVResourceBreakEvent;
import com.mdvcraft.headores.config.PluginSettings;
import com.mdvcraft.headores.config.ResourceRegistry;
import com.mdvcraft.headores.model.AwardedXp;
import com.mdvcraft.headores.model.OreDefinition;
import com.mdvcraft.headores.model.ResourceDefinition;
import com.mdvcraft.headores.model.TreeNodeDefinition;
import com.mdvcraft.headores.service.MmoCoreBridge;
import com.mdvcraft.headores.service.MmoItemsBridge;
import com.mdvcraft.headores.service.ToolPowerService;
import com.mdvcraft.headores.tracking.ResourceKeys;
import com.mdvcraft.headores.util.FormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ResourceBreakListener implements Listener {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final ResourceRegistry registry;
    private final ResourceKeys keys;
    private final ToolPowerService toolPower;
    private final MmoItemsBridge mmoItems;
    private final MmoCoreBridge mmoCore;

    public ResourceBreakListener(
            JavaPlugin plugin,
            PluginSettings settings,
            ResourceRegistry registry,
            ResourceKeys keys,
            ToolPowerService toolPower,
            MmoItemsBridge mmoItems,
            MmoCoreBridge mmoCore
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.registry = registry;
        this.keys = keys;
        this.toolPower = toolPower;
        this.mmoItems = mmoItems;
        this.mmoCore = mmoCore;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type != Material.PLAYER_HEAD && type != Material.PLAYER_WALL_HEAD) return;
        BlockState state = block.getState();
        if (!(state instanceof TileState tileState)) return;

        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        String oreKey = pdc.get(keys.oreKey(), PersistentDataType.STRING);
        if (oreKey != null) {
            handleOre(event, oreKey);
            return;
        }
        String nodeKey = pdc.get(keys.nodeKey(), PersistentDataType.STRING);
        if (nodeKey != null) handleNode(event, nodeKey);
    }

    private void handleOre(BlockBreakEvent event, String key) {
        OreDefinition ore = registry.ore(key);
        if (ore == null) {
            event.setDropItems(false);
            if (settings.debug()) plugin.getLogger().warning("Se rompió una veta desconocida: " + key);
            return;
        }

        Player player = event.getPlayer();
        double currentPower = toolPower.pickaxePower(player.getInventory().getItemInMainHand());
        if (currentPower + 0.0001D < ore.requiredPickaxePower()) {
            event.setCancelled(true);
            String message = settings.noPowerMessage()
                    .replace("%required%", FormatUtil.number(ore.requiredPickaxePower()))
                    .replace("%power%", FormatUtil.number(currentPower))
                    .replace("%ore%", ore.key());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            FormatUtil.playSound(event.getBlock().getLocation(), ore.failSound(), 0.7F, 0.75F);
            return;
        }

        finishBreak(event, ore, MDVResourceBreakEvent.ResourceKind.ORE, 1.15F);
    }

    private void handleNode(BlockBreakEvent event, String key) {
        TreeNodeDefinition node = registry.treeNode(key);
        if (node == null) {
            event.setDropItems(false);
            if (settings.debug()) plugin.getLogger().warning("Se rompió un nodo desconocido: " + key);
            return;
        }

        Player player = event.getPlayer();
        double currentPower = toolPower.axePower(player.getInventory().getItemInMainHand());
        if (currentPower + 0.0001D < node.requiredAxePower()) {
            event.setCancelled(true);
            String message = settings.noAxePowerMessage()
                    .replace("%required%", FormatUtil.number(node.requiredAxePower()))
                    .replace("%power%", FormatUtil.number(currentPower))
                    .replace("%node%", node.key());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            FormatUtil.playSound(event.getBlock().getLocation(), node.failSound(), 0.7F, 0.75F);
            return;
        }

        finishBreak(event, node, MDVResourceBreakEvent.ResourceKind.TREE_NODE, 1.05F);
    }

    private void finishBreak(
            BlockBreakEvent event,
            ResourceDefinition resource,
            MDVResourceBreakEvent.ResourceKind kind,
            float pitch
    ) {
        if (resource.preventVanillaDrops() || resource.ignoreSilkTouch()) event.setDropItems(false);
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (resource.dropNaturally()) {
            ItemStack drop = mmoItems.buildItem(resource.dropType(), resource.dropId(), resource.dropAmount());
            if (drop != null && drop.getType() != Material.AIR) {
                Location location = block.getLocation().add(0.5D, 0.35D, 0.5D);
                Item item = block.getWorld().dropItemNaturally(location, drop);
                item.setPickupDelay(10);
            } else {
                runFallback(resource, player, block);
            }
        } else {
            runFallback(resource, player, block);
        }

        AwardedXp xp = mmoCore.award(player, resource.mmocoreXp(), resource.key());
        Bukkit.getPluginManager().callEvent(new MDVResourceBreakEvent(
                player,
                kind,
                resource.key(),
                resource.mmoitemsBlockId(),
                resource.dropType(),
                resource.dropId(),
                resource.dropAmount(),
                block.getLocation(),
                xp.professionId(),
                xp.professionXp(),
                xp.mainXp()
        ));
        FormatUtil.playSound(block.getLocation(), resource.breakSound(), 0.8F, pitch);
    }

    private void runFallback(ResourceDefinition resource, Player player, Block block) {
        String command = resource.fallbackCommand();
        if (command == null || command.isBlank()) command = settings.defaultFallbackCommand();
        if (command == null || command.isBlank()) return;

        command = command
                .replace("%player%", player.getName())
                .replace("%world%", block.getWorld().getName())
                .replace("%x%", Integer.toString(block.getX()))
                .replace("%y%", Integer.toString(block.getY()))
                .replace("%z%", Integer.toString(block.getZ()))
                .replace("%ore%", resource.key())
                .replace("%node%", resource.key())
                .replace("%drop_type%", resource.dropType())
                .replace("%drop_id%", resource.dropId())
                .replace("%amount%", Integer.toString(resource.dropAmount()));
        if (command.startsWith("/")) command = command.substring(1);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        if (settings.debug()) {
            plugin.getLogger().warning("No pude crear el ItemStack MMOItems para " + resource.dropType()
                    + ":" + resource.dropId() + ". Usé fallback-command.");
        }
    }
}
