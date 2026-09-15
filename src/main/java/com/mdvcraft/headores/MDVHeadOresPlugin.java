package com.mdvcraft.headores;

import com.mdvcraft.headores.command.MDVHeadOresCommand;
import com.mdvcraft.headores.config.PluginSettings;
import com.mdvcraft.headores.config.ResourceRegistry;
import com.mdvcraft.headores.generation.GenerationQueueManager;
import com.mdvcraft.headores.generation.ResourceGenerator;
import com.mdvcraft.headores.listener.ChunkGenerationListener;
import com.mdvcraft.headores.listener.ResourceBreakListener;
import com.mdvcraft.headores.listener.ManualResourcePlaceListener;
import com.mdvcraft.headores.listener.ResourceProtectionListener;
import com.mdvcraft.headores.loot.config.LootNodeRegistry;
import com.mdvcraft.headores.loot.editor.LootEditorManager;
import com.mdvcraft.headores.loot.generation.LootNodeGenerator;
import com.mdvcraft.headores.loot.listener.LootNodeListener;
import com.mdvcraft.headores.loot.service.LootItemResolver;
import com.mdvcraft.headores.loot.service.LootNodeService;
import com.mdvcraft.headores.loot.service.LootTableService;
import com.mdvcraft.headores.service.MmoCoreBridge;
import com.mdvcraft.headores.service.MmoItemsBridge;
import com.mdvcraft.headores.service.ManualResourceService;
import com.mdvcraft.headores.service.ToolPowerService;
import com.mdvcraft.headores.tracking.ChunkRollTracker;
import com.mdvcraft.headores.tracking.ResourceKeys;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class MDVHeadOresPlugin extends JavaPlugin {
    private final List<Listener> runtimeListeners = new ArrayList<>();

    private ResourceKeys keys;
    private PluginSettings settings;
    private ResourceRegistry registry;
    private ChunkRollTracker tracker;
    private ResourceGenerator generator;
    private LootNodeGenerator lootNodeGenerator;
    private ManualResourceService manualResourceService;
    private GenerationQueueManager queueManager;
    private LootNodeRegistry lootNodeRegistry;
    private LootNodeService lootNodeService;
    private LootEditorManager lootEditor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        keys = new ResourceKeys(this);
        reloadPlugin();

        PluginCommand command = getCommand("mdvheadores");
        if (command != null) {
            MDVHeadOresCommand executor = new MDVHeadOresCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        getLogger().info("MDVHeadOres 1.3.1 activado. Vetas: " + registry.ores().size()
                + ", nodos: " + registry.treeNodes().size()
                + ", loot nodes activos: " + lootNodeRegistry.activeNodes().size()
                + "/" + lootNodeRegistry.allNodes().size()
                + ", recursos rastreados: " + tracker.activeResourceCount());
    }

    @Override
    public void onDisable() {
        if (lootNodeService != null) lootNodeService.persistAllVirtual();
        if (queueManager != null) queueManager.stop();
        unregisterRuntimeListeners();
    }

    public synchronized void reloadPlugin() {
        if (lootNodeService != null) lootNodeService.persistAllVirtual();
        if (queueManager != null) queueManager.stop();
        unregisterRuntimeListeners();

        reloadConfig();
        settings = PluginSettings.load(this);
        registry = ResourceRegistry.load(this, settings);
        lootNodeRegistry = LootNodeRegistry.load(this, settings, registry);
        tracker = new ChunkRollTracker(this, keys, settings, registry, lootNodeRegistry);
        lootNodeGenerator = new LootNodeGenerator(this, keys);
        generator = new ResourceGenerator(this, settings, registry, tracker, keys, lootNodeRegistry, lootNodeGenerator);
        manualResourceService = new ManualResourceService(this, registry, keys);
        queueManager = new GenerationQueueManager(this, settings, tracker, generator);

        ToolPowerService toolPower = new ToolPowerService(this, settings);
        MmoItemsBridge mmoItems = new MmoItemsBridge(this, settings.debug());
        MmoCoreBridge mmoCore = new MmoCoreBridge(settings);
        LootItemResolver lootResolver = new LootItemResolver(this, settings.debug());
        LootTableService lootTables = new LootTableService(lootResolver);
        lootNodeService = new LootNodeService(this, keys, lootNodeRegistry, lootTables);
        lootEditor = new LootEditorManager(lootNodeRegistry, lootResolver, settings.lootNodes().editorRewardsPerPage());

        runtimeListeners.add(new ChunkGenerationListener(queueManager));
        runtimeListeners.add(new ResourceProtectionListener(this, settings, keys));
        runtimeListeners.add(new ManualResourcePlaceListener(manualResourceService));
        runtimeListeners.add(new ResourceBreakListener(
                this, settings, registry, keys, toolPower, mmoItems, mmoCore));
        runtimeListeners.add(new LootNodeListener(this, lootNodeService));
        if (settings.lootNodes().editorEnabled()) runtimeListeners.add(lootEditor);
        for (Listener listener : runtimeListeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
        queueManager.start();
    }

    private void unregisterRuntimeListeners() {
        for (Listener listener : runtimeListeners) HandlerList.unregisterAll(listener);
        runtimeListeners.clear();
    }

    public ResourceKeys keys() { return keys; }
    public PluginSettings settings() { return settings; }
    public ResourceRegistry registry() { return registry; }
    public ChunkRollTracker tracker() { return tracker; }
    public ResourceGenerator generator() { return generator; }
    public LootNodeGenerator lootNodeGenerator() { return lootNodeGenerator; }
    public ManualResourceService manualResources() { return manualResourceService; }
    public GenerationQueueManager queueManager() { return queueManager; }
    public LootNodeRegistry lootNodeRegistry() { return lootNodeRegistry; }
    public LootEditorManager lootEditor() { return lootEditor; }
}
