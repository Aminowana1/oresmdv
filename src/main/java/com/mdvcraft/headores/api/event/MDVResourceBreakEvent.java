package com.mdvcraft.headores.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Evento publico disparado cuando un jugador rompe correctamente una veta o nodo de MDVHeadOres.
 * Solo se llama después de superar el requisito de poder y entregar/generar el drop.
 */
public final class MDVResourceBreakEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    public enum ResourceKind { ORE, TREE_NODE }

    private final Player player;
    private final ResourceKind resourceKind;
    private final String resourceKey;
    private final String mmoitemsBlockId;
    private final String dropType;
    private final String dropId;
    private final int dropAmount;
    private final Location location;
    private final String professionId;
    private final int professionXp;
    private final int mainXp;

    public MDVResourceBreakEvent(Player player, ResourceKind resourceKind, String resourceKey,
                                 String mmoitemsBlockId, String dropType, String dropId, int dropAmount,
                                 Location location, String professionId, int professionXp, int mainXp) {
        this.player = player;
        this.resourceKind = resourceKind;
        this.resourceKey = resourceKey;
        this.mmoitemsBlockId = mmoitemsBlockId;
        this.dropType = dropType;
        this.dropId = dropId;
        this.dropAmount = Math.max(1, dropAmount);
        this.location = location == null ? null : location.clone();
        this.professionId = professionId == null ? "" : professionId;
        this.professionXp = Math.max(0, professionXp);
        this.mainXp = Math.max(0, mainXp);
    }

    public Player getPlayer() { return player; }
    public ResourceKind getResourceKind() { return resourceKind; }
    public String getResourceKey() { return resourceKey; }
    public String getMmoitemsBlockId() { return mmoitemsBlockId; }
    public String getDropType() { return dropType; }
    public String getDropId() { return dropId; }
    public int getDropAmount() { return dropAmount; }
    public Location getLocation() { return location == null ? null : location.clone(); }
    public String getProfessionId() { return professionId; }
    public int getProfessionXp() { return professionXp; }
    public int getMainXp() { return mainXp; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
