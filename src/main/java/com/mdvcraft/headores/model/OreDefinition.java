package com.mdvcraft.headores.model;

import org.bukkit.Material;

import java.util.List;
import java.util.Set;

public final class OreDefinition extends ResourceDefinition {
    private final List<Material> replace;
    private final int minY;
    private final int maxY;
    private final double chunkChance;
    private final int veinsPerChunk;
    private final int veinMin;
    private final int veinMax;
    private final String placement;
    private final double requiredPickaxePower;
    private final Set<Material> avoidNearMaterials;

    public OreDefinition(
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
            List<Material> replace,
            int minY,
            int maxY,
            double chunkChance,
            int veinsPerChunk,
            int veinMin,
            int veinMax,
            String placement,
            double requiredPickaxePower,
            Set<Material> avoidNearMaterials
    ) {
        super(key, trackingBit, mmoitemsBlockId, textureHash, displayName, worlds, dropType, dropId,
                dropAmount, preventVanillaDrops, ignoreSilkTouch, dropNaturally, breakSound, failSound,
                fallbackCommand, applyPhysicsOnPlace, mmocoreXp);
        this.replace = List.copyOf(replace);
        this.minY = minY;
        this.maxY = maxY;
        this.chunkChance = Math.max(0.0D, Math.min(1.0D, chunkChance));
        this.veinsPerChunk = Math.max(1, veinsPerChunk);
        this.veinMin = Math.max(1, veinMin);
        this.veinMax = Math.max(this.veinMin, veinMax);
        this.placement = placement;
        this.requiredPickaxePower = Math.max(0.0D, requiredPickaxePower);
        this.avoidNearMaterials = Set.copyOf(avoidNearMaterials);
    }

    public List<Material> replace() { return replace; }
    public int minY() { return minY; }
    public int maxY() { return maxY; }
    public double chunkChance() { return chunkChance; }
    public int veinsPerChunk() { return veinsPerChunk; }
    public int veinMin() { return veinMin; }
    public int veinMax() { return veinMax; }
    public String placement() { return placement; }
    public double requiredPickaxePower() { return requiredPickaxePower; }
    public Set<Material> avoidNearMaterials() { return avoidNearMaterials; }
}
