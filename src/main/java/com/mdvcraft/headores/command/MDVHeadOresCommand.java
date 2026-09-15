package com.mdvcraft.headores.command;

import com.mdvcraft.headores.MDVHeadOresPlugin;
import com.mdvcraft.headores.generation.QueueSnapshot;
import com.mdvcraft.headores.loot.model.LootNodeDefinition;
import com.mdvcraft.headores.model.OreDefinition;
import com.mdvcraft.headores.model.ResourceDefinition;
import com.mdvcraft.headores.service.ManualResourceService;
import com.mdvcraft.headores.tracking.ChunkRollTracker;
import com.mdvcraft.headores.tracking.ResourceKeys;
import com.mdvcraft.headores.util.FormatUtil;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MDVHeadOresCommand implements TabExecutor {
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

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "inspect" -> inspect(sender);
            case "queue", "status", "cola", "estado" -> status(sender);
            case "generate" -> generate(sender, args);
            case "loot" -> loot(sender, args);
            case "head", "cabeza" -> head(sender, args);
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
                + " §aLoot nodes activos: §f" + plugin.lootNodeRegistry().activeNodes().size()
                + "§7/§f" + plugin.lootNodeRegistry().allNodes().size()
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
            String lootNode = pdc.get(keys.lootNodeKey(), PersistentDataType.STRING);
            if (ore != null) sender.sendMessage(PREFIX + "§aVeta MDVHeadOres: §f" + ore);
            else if (node != null) sender.sendMessage(PREFIX + "§aNodo MDVHeadOres: §f" + node);
            else if (lootNode != null) sender.sendMessage(PREFIX + "§6Loot node MDVHeadOres: §f" + lootNode);
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
                + " §8| §7Reintentos: §f" + snapshot.retryQueued() + "§7/§f" + snapshot.maxRetrySize()
                + " §8| §7Listos: §f" + snapshot.ready());
        sender.sendMessage("§7Procesados: §a" + snapshot.processedTotal()
                + " §8| §7Encolados: §e" + snapshot.queuedTotal()
                + " §8| §7Recuperados: §b" + snapshot.retriedTotal()
                + " §8| §7Retirados al descargar: §6" + snapshot.cancelledOnUnloadTotal());
        sender.sendMessage("§7Carreras descargadas: §6" + snapshot.deferredUnloadedTotal()
                + " §8| §7Cola llena: §c" + snapshot.skippedQueueFullTotal()
                + " §8| §7Reintentos descartados por límite: §c" + snapshot.retryDroppedTotal());
        sender.sendMessage("§7Chunks migrados al esquema por recurso: §f" + snapshot.migratedChunks());
        sender.sendMessage("§7Escaneo incremental: " + (snapshot.scanActive() ? "§aACTIVO" : "§7inactivo")
                + " §8| §7Actual: §f" + snapshot.currentScanChecked()
                + " §8| §7Pendientes vistos: §e" + snapshot.currentScanMissing()
                + " §8| §7Completados: §f" + snapshot.scansCompletedTotal()
                + "§7/§f" + snapshot.scansStartedTotal());

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

    private boolean loot(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "§eUsa: /mdvheadores loot <list|editor|spawn> [id]");
            return true;
        }
        if (args[1].equalsIgnoreCase("list")) {
            sender.sendMessage(PREFIX + "§dLoot nodes definidos: §f" + plugin.lootNodeRegistry().allNodes().size()
                    + " §8| §7activos: §a" + plugin.lootNodeRegistry().activeNodes().size());
            for (LootNodeDefinition node : plugin.lootNodeRegistry().allNodes()) {
                sender.sendMessage("§7- §f" + node.key() + " §8[§e" + node.containerType().name()
                        + "§8] " + (node.enabled() ? "§aON" : "§cOFF")
                        + " §7bit=§f" + node.trackingBit() + " §7loot=§f" + node.loot().entries().size());
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("editor")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(PREFIX + "§cEl editor solo puede abrirlo un jugador.");
                return true;
            }
            if (!plugin.settings().lootNodes().editorEnabled()) {
                sender.sendMessage(PREFIX + "§cEl editor de loot está desactivado en config.yml.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(PREFIX + "§eUsa: /mdvheadores loot editor <id>");
                return true;
            }
            if (!plugin.lootEditor().open(player, args[2])) {
                sender.sendMessage(PREFIX + "§cNo existe el loot node '" + args[2] + "'.");
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("spawn") || args[1].equalsIgnoreCase("generar")
                || args[1].equalsIgnoreCase("place")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(PREFIX + "§cSolo un jugador puede generar un loot node en su ubicación.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(PREFIX + "§eUsa: /mdvheadores loot spawn <id>");
                return true;
            }
            LootNodeDefinition node = plugin.lootNodeRegistry().node(args[2].toLowerCase(Locale.ROOT));
            if (node == null) {
                sender.sendMessage(PREFIX + "§cNo existe el loot node '" + args[2] + "'.");
                return true;
            }
            Block target = player.getLocation().getBlock();
            if (!plugin.lootNodeGenerator().placeAt(target, node)) {
                sender.sendMessage(PREFIX + "§cNo pude colocarlo exactamente en tu posición. "
                        + "§7Debes estar sobre un bloque sólido y el espacio debe ser reemplazable/libre.");
                return true;
            }
            sender.sendMessage(PREFIX + "§aLoot node §f" + node.key() + " §agenerado en §f"
                    + target.getWorld().getName() + " " + target.getX() + " " + target.getY() + " " + target.getZ()
                    + "§a. El botín se tirará normalmente en el primer acceso.");
            return true;
        }
        sender.sendMessage(PREFIX + "§eUsa: /mdvheadores loot <list|editor|spawn> [id]");
        return true;
    }

    private boolean head(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "§cEste comando solo puede usarlo un jugador.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + "§eUsa: /mdvheadores head <list|ore|node|id> [id] [cantidad]");
            return true;
        }

        if (args[1].equalsIgnoreCase("list")) {
            sender.sendMessage(PREFIX + "§dVetas disponibles: §f" + String.join("§7, §f", plugin.registry().ores().keySet()));
            sender.sendMessage(PREFIX + "§dNodos disponibles: §f" + String.join("§7, §f", plugin.registry().treeNodes().keySet()));
            return true;
        }

        String kind;
        String id;
        int amountIndex;
        if (isOreWord(args[1]) || isNodeWord(args[1])) {
            if (args.length < 3) {
                sender.sendMessage(PREFIX + "§eUsa: /mdvheadores head " + args[1] + " <id> [cantidad]");
                return true;
            }
            kind = isOreWord(args[1]) ? ManualResourceService.KIND_ORE : ManualResourceService.KIND_NODE;
            id = args[2];
            amountIndex = 3;
        } else {
            id = args[1];
            ResourceDefinition resource = plugin.manualResources().findAny(id);
            if (resource == null) {
                sender.sendMessage(PREFIX + "§cNo existe ninguna veta o nodo con ID '" + id + "'.");
                return true;
            }
            kind = resource instanceof OreDefinition ? ManualResourceService.KIND_ORE : ManualResourceService.KIND_NODE;
            amountIndex = 2;
        }

        int amount = 1;
        if (args.length > amountIndex) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[amountIndex])));
            } catch (NumberFormatException exception) {
                sender.sendMessage(PREFIX + "§cCantidad inválida. Usa un número entre 1 y 64.");
                return true;
            }
        }

        ItemStack head = plugin.manualResources().createHead(kind, id, amount);
        if (head == null) {
            sender.sendMessage(PREFIX + "§cNo existe ese recurso o no pude crear su cabeza.");
            return true;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(head);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        sender.sendMessage(PREFIX + "§aRecibiste §f" + amount + "§a cabeza(s) colocable(s) del recurso §f" + id
                + "§a. Al colocarlas serán minerales/nodos reales de MDVHeadOres.");
        return true;
    }

    private static boolean isOreWord(String raw) {
        return raw.equalsIgnoreCase("ore") || raw.equalsIgnoreCase("ores")
                || raw.equalsIgnoreCase("veta") || raw.equalsIgnoreCase("mineral");
    }

    private static boolean isNodeWord(String raw) {
        return raw.equalsIgnoreCase("node") || raw.equalsIgnoreCase("nodo")
                || raw.equalsIgnoreCase("tree") || raw.equalsIgnoreCase("tree-node");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(PREFIX + "§eComandos:");
        sender.sendMessage("§7/mdvheadores reload");
        sender.sendMessage("§7/mdvheadores inspect");
        sender.sendMessage("§7/mdvheadores queue");
        sender.sendMessage("§7/mdvheadores generate <radio> [force]");
        sender.sendMessage("§7/mdvheadores loot <list|editor|spawn> [id]");
        sender.sendMessage("§7/mdvheadores head <list|ore|node|id> [id] [cantidad]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("mdvheadores.admin")) return List.of();
        if (args.length == 1) return filter(List.of("reload", "inspect", "queue", "generate", "loot", "head"), args[0]);

        if (args[0].equalsIgnoreCase("loot")) {
            if (args.length == 2) return filter(List.of("list", "editor", "spawn"), args[1]);
            if (args.length == 3 && (args[1].equalsIgnoreCase("editor") || args[1].equalsIgnoreCase("spawn")
                    || args[1].equalsIgnoreCase("generar") || args[1].equalsIgnoreCase("place"))) {
                return filter(plugin.lootNodeRegistry().allNodes().stream().map(LootNodeDefinition::key).toList(), args[2]);
            }
        }

        if (args[0].equalsIgnoreCase("head") || args[0].equalsIgnoreCase("cabeza")) {
            if (args.length == 2) {
                List<String> values = new ArrayList<>(List.of("list", "ore", "node"));
                values.addAll(plugin.registry().ores().keySet());
                values.addAll(plugin.registry().treeNodes().keySet());
                return filter(values, args[1]);
            }
            if (args.length == 3 && isOreWord(args[1])) return filter(plugin.registry().ores().keySet(), args[2]);
            if (args.length == 3 && isNodeWord(args[1])) return filter(plugin.registry().treeNodes().keySet(), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(Collection<String> values, String token) {
        String prefix = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
