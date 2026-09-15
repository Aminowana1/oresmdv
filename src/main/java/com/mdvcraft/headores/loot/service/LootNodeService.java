package com.mdvcraft.headores.loot.service;

import com.mdvcraft.headores.loot.config.LootNodeRegistry;
import com.mdvcraft.headores.loot.model.LootContainerType;
import com.mdvcraft.headores.loot.model.LootNodeDefinition;
import com.mdvcraft.headores.tracking.ResourceKeys;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class LootNodeService {
    private final JavaPlugin plugin;
    private final ResourceKeys keys;
    private final LootNodeRegistry registry;
    private final LootTableService lootTables;
    private final VirtualLootStorage virtualStorage;
    private final Map<String, Inventory> openVirtualInventories = new HashMap<>();

    public LootNodeService(JavaPlugin plugin, ResourceKeys keys, LootNodeRegistry registry,
                           LootTableService lootTables) {
        this.plugin = plugin;
        this.keys = keys;
        this.registry = registry;
        this.lootTables = lootTables;
        this.virtualStorage = new VirtualLootStorage(keys.lootContentsKey());
    }

    public String nodeKey(Block block) {
        if (block == null) return null;
        BlockState state = block.getState();
        if (!(state instanceof TileState tile)) return null;
        return tile.getPersistentDataContainer().get(keys.lootNodeKey(), PersistentDataType.STRING);
    }

    public LootNodeDefinition definition(Block block) {
        String key = nodeKey(block);
        return key == null ? null : registry.node(key);
    }

    public boolean isLootNode(Block block) {
        return nodeKey(block) != null;
    }

    /**
     * Garantiza que un contenedor físico tenga su botín generado.
     *
     * @return true si ya estaba generado o si pudo generar al menos una
     *         recompensa válida. false si la tabla está vacía o ninguna
     *         recompensa pudo construirse. En ese caso el nodo NO se consume.
     */
    public boolean ensurePhysicalInventoryGenerated(Block block, LootNodeDefinition node) {
        BlockState state = block.getState();
        if (!(state instanceof Container) || !(state instanceof TileState tile)) return false;
        PersistentDataContainer pdc = tile.getPersistentDataContainer();
        if (isGenerated(pdc)) return true;

        List<ItemStack> rolled = lootTables.roll(node);
        if (rolled.isEmpty()) return false;

        // IMPORTANTE: primero persistimos el PDC y actualizamos el TileState.
        // Si llenamos el inventario antes de tile.update(), un BlockState/snapshot
        // antiguo puede reescribir el cofre o barril y dejarlo vacío.
        pdc.set(keys.lootGeneratedKey(), PersistentDataType.BYTE, (byte) 1);
        if (!tile.update(true, false)) return false;

        // Volvemos a obtener el estado REAL ya actualizado y recién entonces
        // distribuimos el loot en posiciones aleatorias del inventario físico.
        BlockState refreshed = block.getState();
        if (!(refreshed instanceof Container liveContainer)) return false;
        placeRandom(liveContainer.getInventory(), rolled);
        return true;
    }

    /**
     * Abre una bolsa/cabeza con inventario virtual.
     *
     * Si no existe ninguna recompensa válida, la bolsa desaparece inmediatamente
     * y en silencio. De este modo una bolsa vacía nunca queda como PLAYER_HEAD
     * decorativa ni muestra mensajes administrativos al jugador.
     */
    public boolean openVirtual(Player player, Block block, LootNodeDefinition node) {
        BlockState state = block.getState();
        if (!(state instanceof TileState tile)) return false;
        String cacheKey = cacheKey(block);
        Inventory inventory = openVirtualInventories.get(cacheKey);
        if (inventory == null) {
            LootInventoryHolder holder = new LootInventoryHolder(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(), node.key());
            inventory = Bukkit.createInventory(holder, node.inventorySize(),
                    ChatColor.translateAlternateColorCodes('&', node.displayName()));
            holder.attach(inventory);

            PersistentDataContainer pdc = tile.getPersistentDataContainer();
            if (isGenerated(pdc)) {
                inventory.setContents(virtualStorage.load(pdc, node.inventorySize()));
                if (isEmpty(inventory)) {
                    openVirtualInventories.remove(cacheKey);
                    block.setType(Material.AIR, false);
                    return true;
                }
            } else {
                List<ItemStack> rolled = lootTables.roll(node);
                if (rolled.isEmpty()) {
                    block.setType(Material.AIR, false);
                    return true;
                }
                placeRandom(inventory, rolled);
                pdc.set(keys.lootGeneratedKey(), PersistentDataType.BYTE, (byte) 1);
                virtualStorage.save(pdc, inventory.getContents());
                tile.update(true, false);
            }
            openVirtualInventories.put(cacheKey, inventory);
        }
        player.openInventory(inventory);
        return true;
    }

    /**
     * Rompe una bolsa/cabeza manualmente sin dropear nunca la PLAYER_HEAD.
     *
     * - Sin loot válido: simplemente desaparece.
     * - Sin abrir todavía: tira la tabla una vez y suelta el resultado al suelo.
     * - Ya abierta: suelta únicamente el contenido restante.
     * - Si alguien la estaba viendo, se cierra su inventario antes de eliminarla
     *   para impedir duplicaciones.
     */
    public void breakVirtualHead(Block block, LootNodeDefinition node) {
        if (block == null || node == null || node.containerType() != LootContainerType.PLAYER_HEAD) return;

        String key = cacheKey(block);
        List<ItemStack> drops = new ArrayList<>();
        Inventory openInventory = openVirtualInventories.remove(key);

        if (openInventory != null) {
            collectContents(openInventory.getContents(), drops);
            openInventory.clear();

            // InventoryCloseEvent persiste en el siguiente tick. Para entonces el
            // bloque ya será AIR, así que no puede volver a guardar/duplicar loot.
            List<HumanEntity> viewers = new ArrayList<>(openInventory.getViewers());
            for (HumanEntity viewer : viewers) viewer.closeInventory();
        } else {
            BlockState state = block.getState();
            if (state instanceof TileState tile) {
                PersistentDataContainer pdc = tile.getPersistentDataContainer();
                if (isGenerated(pdc)) {
                    collectContents(virtualStorage.load(pdc, node.inventorySize()), drops);
                } else {
                    collectContents(lootTables.roll(node).toArray(ItemStack[]::new), drops);
                }
            }
        }

        Location dropAt = block.getLocation().add(0.5D, 0.35D, 0.5D);
        block.setType(Material.AIR, false);
        for (ItemStack stack : drops) {
            Item item = block.getWorld().dropItemNaturally(dropAt, stack);
            item.setPickupDelay(5);
        }
    }

    public void saveVirtual(LootInventoryHolder holder, Inventory inventory) {
        World world = holder.world();
        if (world == null || !world.isChunkLoaded(holder.x() >> 4, holder.z() >> 4)) {
            openVirtualInventories.remove(holder.cacheKey());
            return;
        }
        Block block = world.getBlockAt(holder.x(), holder.y(), holder.z());
        BlockState state = block.getState();
        if (!(state instanceof TileState tile)) {
            openVirtualInventories.remove(holder.cacheKey());
            return;
        }
        String key = tile.getPersistentDataContainer().get(keys.lootNodeKey(), PersistentDataType.STRING);
        if (!holder.nodeKey().equals(key)) {
            openVirtualInventories.remove(holder.cacheKey());
            return;
        }
        if (isEmpty(inventory)) {
            openVirtualInventories.remove(holder.cacheKey());
            block.setType(Material.AIR, false);
            return;
        }
        virtualStorage.save(tile.getPersistentDataContainer(), inventory.getContents());
        tile.update(true, false);
        if (inventory.getViewers().isEmpty()) openVirtualInventories.remove(holder.cacheKey());
    }

    public boolean claimPot(Block block) {
        LootNodeDefinition node = definition(block);
        if (node == null || node.containerType() != LootContainerType.DECORATED_POT) return false;
        BlockState state = block.getState();
        if (!(state instanceof TileState tile)) return false;
        PersistentDataContainer pdc = tile.getPersistentDataContainer();

        // Ya fue saqueada: la vasija permanece físicamente y no vuelve a dar loot.
        if (isGenerated(pdc)) return true;

        // Primero resolvemos el botín. Si la tabla está vacía o sus referencias
        // no pueden construirse, la vasija permanece intacta y reclamable.
        List<ItemStack> rolled = lootTables.roll(node);
        if (rolled.isEmpty()) return false;

        // Marca antes del drop para que dos eventos del mismo tick no dupliquen la recompensa.
        pdc.set(keys.lootGeneratedKey(), PersistentDataType.BYTE, (byte) 1);
        if (!tile.update(true, false)) return false;

        // La vasija NO desaparece al reclamarla. Solo desaparece si un jugador la rompe.
        Location dropAt = block.getLocation().add(0.5D, 0.4D, 0.5D);
        for (ItemStack stack : rolled) {
            Item item = block.getWorld().dropItemNaturally(dropAt, stack);
            item.setPickupDelay(5);
        }
        return true;
    }

    public void removeIfEmptyPhysical(Block block) {
        // CHEST/BARREL nunca se eliminan automáticamente. Este método se conserva
        // como no-op por compatibilidad interna con versiones anteriores.
    }

    public void persistAllVirtual() {
        List<Inventory> inventories = new ArrayList<>(openVirtualInventories.values());
        for (Inventory inventory : inventories) {
            if (inventory.getHolder(false) instanceof LootInventoryHolder holder) saveVirtual(holder, inventory);
        }
        openVirtualInventories.clear();
    }

    private boolean isGenerated(PersistentDataContainer pdc) {
        Byte value = pdc.get(keys.lootGeneratedKey(), PersistentDataType.BYTE);
        return value != null && value != 0;
    }

    private static void placeRandom(Inventory inventory, List<ItemStack> items) {
        if (items.isEmpty() || inventory.getSize() <= 0) return;
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null || inventory.getItem(i).getType() == Material.AIR) slots.add(i);
        }
        Collections.shuffle(slots, ThreadLocalRandom.current());
        int index = 0;
        for (ItemStack item : items) {
            if (index >= slots.size()) break;
            inventory.setItem(slots.get(index++), item);
        }
    }

    private static void collectContents(ItemStack[] contents, List<ItemStack> output) {
        if (contents == null) return;
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) continue;
            output.add(stack.clone());
        }
    }

    private static boolean isEmpty(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) return false;
        }
        return true;
    }

    private static String cacheKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
