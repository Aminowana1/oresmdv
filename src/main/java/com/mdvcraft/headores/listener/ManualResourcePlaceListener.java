package com.mdvcraft.headores.listener;

import com.mdvcraft.headores.service.ManualResourceService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public final class ManualResourcePlaceListener implements Listener {
    private final ManualResourceService manualResources;

    public ManualResourcePlaceListener(ManualResourceService manualResources) {
        this.manualResources = manualResources;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        manualResources.convertPlaced(event.getBlockPlaced(), event.getItemInHand());
    }
}
