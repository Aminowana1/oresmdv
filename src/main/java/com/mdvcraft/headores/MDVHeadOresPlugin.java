package com.mdvcraft.headores;

import com.mdvcraft.headores.command.MDVHeadOresCommand;
import com.mdvcraft.headores.config.PluginSettings;
import com.mdvcraft.headores.config.ResourceRegistry;
import com.mdvcraft.headores.generation.GenerationQueueManager;
import com.mdvcraft.headores.generation.ResourceGenerator;
import com.mdvcraft.headores.listener.ChunkGenerationListener;
import com.mdvcraft.headores.listener.ResourceBreakListener;
import com.mdvcraft.headores.listener.ResourceProtectionListener;
import com.mdvcraft.headores.service.MmoCoreBridge;
import com.mdvcraft.headores.service.MmoItemsBridge;
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
    private GenerationQueueManager queueManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        keys = new ResourceKeys(this);
        reloadPlugin();

        PluginCommand command = getCommand("mdvheadores");
        if (command != null) command.setExecutor(new MDVHeadOresCommand(this));
        getLogger().info("MDVHeadOres 1.1.1 activado. Vetas: " + registry.ores().size()
                + ", nodos: " + registry.treeNodes().size()
                + ", recursos rastreados: " + tracker.activeResourceCount());
    }

    @Override
    public void onDisable() {
        if (queueManager != null) queueManager.stop();
        unregisterRuntimeListeners();
    }

    public synchronized void reloadPlugin() {
        if (queueManager != null) queueManager.stop();
        unregisterRuntimeListeners();

        reloadConfig();
        settings = PluginSettings.load(this);
        registry = ResourceRegistry.load(this, settings);
        tracker = new ChunkRollTracker(this, keys, settings, registry);
        generator = new ResourceGenerator(this, settings, registry, tracker, keys);
        queueManager = new GenerationQueueManager(this, settings, tracker, generator);

        ToolPowerService toolPower = new ToolPowerService(this, settings);
        MmoItemsBridge mmoItems = new MmoItemsBridge(this, settings.debug());
        MmoCoreBridge mmoCore = new MmoCoreBridge(settings);

        runtimeListeners.add(new ChunkGenerationListener(queueManager));
        runtimeListeners.add(new ResourceProtectionListener(this, settings, keys));
        runtimeListeners.add(new ResourceBreakListener(
                this, settings, registry, keys, toolPower, mmoItems, mmoCore));
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
    public GenerationQueueManager queueManager() { return queueManager; }
}
