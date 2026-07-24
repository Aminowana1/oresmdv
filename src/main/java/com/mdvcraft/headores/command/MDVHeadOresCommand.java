package com.mdvcraft.headores.command;

import com.mdvcraft.headores.MDVHeadOresPlugin;
import com.mdvcraft.headores.generation.QueueSnapshot;
import com.mdvcraft.headores.tracking.ChunkRollTracker;
import com.mdvcraft.headores.tracking.ResourceKeys;
import com.mdvcraft.headores.util.FormatUtil;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public final class MDVHeadOresCommand implements CommandExecutor {
    private static final String PREFIX = "§6§l[§5§lMDVCRAFT§6§l]  §4»  ";
    private final MDVHeadOresPlugin plugin;

    public MDVHeadOresCommand(MDVHeadOresPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mdvheadores.admin")) {
            sender.sendMessage(PREFIX + "§cNo tienes permiso para usar este comando.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "reload" -> reload(sender);
            case "inspect" -> inspect(sender);
            case "queue", "status", "cola", "estado" -> status(sender);
            case "generate" -> generate(sender, args);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private boolean reload(CommandSender sender) {
        plugin.reloadPlugin();
        sender.sendMessage(PREFIX + "§aMDVHeadOres recargado. Vetas: §f" + plugin.registry().ores().size()
                + " §aNodos: §f" + plugin.registry().treeNodes().size()
                + " §aBits activos: §f" + plugin.tracker().activeResourceCount());
        return true;
    }

    private boolean inspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando solo puede usarlo un jugador.");
            return true;
        }
        Block target = player.getTargetBlockExact(8);
        if (target == null) {
            sender.sendMessage(PREFIX + "§cNo estás mirando ningún bloque cercano.");
            return true;
        }

        sender.sendMessage(PREFIX + "§eBloque: §f" + target.getType().name() + " §7en §f"
                + target.getWorld().getName() + " " + target.getX() + " " + target.getY() + " " + target.getZ());
        BlockState state = target.getState();
        ResourceKeys keys = plugin.keys();
        if (state instanceof TileState tileState) {
            PersistentDataContainer pdc = tileState.getPersistentDataContainer();
            String ore = pdc.get(keys.oreKey(), PersistentDataType.STRING);
            String node = pdc.get(keys.nodeKey(), PersistentDataType.STRING);
            if (ore != null) sender.sendMessage(PREFIX + "§aVeta MDVHeadOres: §f" + ore);
            else if (node != null) sender.sendMessage(PREFIX + "§aNodo MDVHeadOres: §f" + node);
            else sender.sendMessage(PREFIX + "§7No tiene marca de recurso MDVHeadOres.");
        } else if (target.getType() == Material.PLAYER_HEAD || target.getType() == Material.PLAYER_WALL_HEAD) {
            sender.sendMessage(PREFIX + "§7La cabeza no contiene TileState legible.");
        }

        Chunk chunk = target.getChunk();
        ChunkRollTracker tracker = plugin.tracker();
        long mask = tracker.readMask(chunk);
        sender.sendMessage(PREFIX + "§7Tiradas registradas en el chunk: §f"
                + tracker.completedResourceCount(mask) + "§7/§f" + tracker.activeResourceCount()
                + " §8(mask=" + Long.toUnsignedString(mask) + ")");
        return true;
    }

    private boolean status(CommandSender sender) {
        QueueSnapshot snapshot = plugin.queueManager().snapshot();
        sender.sendMessage(PREFIX + "§dEstado de generación MDVHeadOres");
        sender.sendMessage("§7Throttle: " + (snapshot.throttleEnabled() ? "§aON" : "§cOFF")
                + " §8| §7Cola: §f" + snapshot.queued() + "§7/§f" + snapshot.maxQueueSize()
                + " §8| §7Listos: §f" + snapshot.ready());
        sender.sendMessage("§7Procesados: §a" + snapshot.processedTotal()
                + " §8| §7Encolados: §e" + snapshot.queuedTotal()
                + " §8| §7Aplazados descargados: §6" + snapshot.deferredUnloadedTotal()
                + " §8| §7Cola llena: §c" + snapshot.skippedQueueFullTotal());
        sender.sendMessage("§7Chunks migrados al esquema por recurso: §f" + snapshot.migratedChunks());

        if (snapshot.queued() > 0) {
            sender.sendMessage("§7Primer chunk listo en: §f" + FormatUtil.duration(snapshot.firstReadyInMillis() / 1000L)
                    + " §8| §7Más viejo: §f" + FormatUtil.duration(snapshot.oldestAgeMillis() / 1000L)
                    + " §8| §7Vaciar aprox.: §f" + FormatUtil.duration(snapshot.estimatedSeconds()));
        }
        if (!snapshot.byWorld().isEmpty()) {
            StringBuilder worlds = new StringBuilder();
            int shown = 0;
            for (Map.Entry<String, Integer> entry : snapshot.byWorld().entrySet()) {
                if (shown++ > 0) worlds.append("§8, ");
                worlds.append("§f").append(entry.getKey()).append("§7:§e").append(entry.getValue());
                if (shown >= 5 && snapshot.byWorld().size() > shown) {
                    worlds.append("§8, §7+").append(snapshot.byWorld().size() - shown).append(" mundos");
                    break;
                }
            }
            sender.sendMessage("§7Por mundo: " + worlds);
        }
        return true;
    }

    private boolean generate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando solo puede usarlo un jugador.");
            return true;
        }

        int radius = 2;
        if (args.length >= 2) {
            try {
                radius = Math.max(0, Math.min(16, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
                sender.sendMessage(PREFIX + "§cRadio inválido.");
                return true;
            }
        }
        boolean force = args.length >= 3 && args[2].equalsIgnoreCase("force");
        Chunk center = player.getLocation().getChunk();
        int total = 0;
        int queued = 0;

        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                Chunk chunk = player.getWorld().getChunkAt(x, z);
                if (plugin.settings().throttle().enabled() && plugin.settings().throttle().commandGenerateUsesQueue()) {
                    if (plugin.queueManager().enqueue(chunk, force, true)) queued++;
                } else {
                    plugin.tracker().prepareChunk(chunk, false);
                    plugin.generator().generate(chunk, force, true);
                }
                total++;
            }
        }

        if (plugin.settings().throttle().enabled() && plugin.settings().throttle().commandGenerateUsesQueue()) {
            sender.sendMessage(PREFIX + "§aGeneración encolada en §f" + queued + "§a/§f" + total
                    + "§a chunks. Force: §f" + force + "§a. Cola: §f" + plugin.queueManager().size());
        } else {
            sender.sendMessage(PREFIX + "§aGeneración ejecutada en §f" + total + "§a chunks. Force: §f" + force);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(PREFIX + "§eUsa: /mdvheadores reload, inspect, queue o generate <radio> [force]");
    }
}
