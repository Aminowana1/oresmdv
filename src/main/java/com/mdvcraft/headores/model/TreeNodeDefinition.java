package com.mdvcraft.headores.model;

import org.bukkit.Material;

import java.util.List;
import java.util.Set;

public final class TreeNodeDefinition extends ResourceDefinition {
    private final List<Material> attachTo;
    private final int minY;
    private final int maxY;
    private final double chunkChance;
    private final int nodesPerChunk;
    private final double requiredAxePower;
    private final boolean onlyOnSurfaceLogs;

    public TreeNodeDefinition(
            String key,
            int trackingBit,
            String mmoitemsBlockId,
            String textureHash,
            String displayName,
            Set<String> worlds,
            String dropType,
            String dropId,
            int dropAmount,
            boolean preventVanillaDrops,
            boolean ignoreSilkTouch,
            boolean dropNaturally,
            String breakSound,
            String failSound,
            String fallbackCommand,
            boolean applyPhysicsOnPlace,
            MmoCoreXpSettings mmocoreXp,
            List<Material> attachTo,
            int minY,
            int maxY,
            double chunkChance,
            int nodesPerChunk,
            double requiredAxePower,
            boolean onlyOnSurfaceLogs
    ) {
        super(key, trackingBit, mmoitemsBlockId, textureHash, displayName, worlds, dropType, dropId,
                dropAmount, preventVanillaDrops, ignoreSilkTouch, dropNaturally, breakSound, failSound,
                fallbackCommand, applyPhysicsOnPlace, mmocoreXp);
        this.attachTo = List.copyOf(attachTo);
        this.minY = minY;
        this.maxY = maxY;
        this.chunkChance = Math.max(0.0D, Math.min(1.0D, chunkChance));
        this.nodesPerChunk = Math.max(1, nodesPerChunk);
        this.requiredAxePower = Math.max(0.0D, requiredAxePower);
        this.onlyOnSurfaceLogs = onlyOnSurfaceLogs;
    }

    public List<Material> attachTo() { return attachTo; }
    public int minY() { return minY; }
    public int maxY() { return maxY; }
    public double chunkChance() { return chunkChance; }
    public int nodesPerChunk() { return nodesPerChunk; }
    public double requiredAxePower() { return requiredAxePower; }
    public boolean onlyOnSurfaceLogs() { return onlyOnSurfaceLogs; }
}
