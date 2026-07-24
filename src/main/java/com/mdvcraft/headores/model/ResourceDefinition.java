package com.mdvcraft.headores.model;

import java.util.Set;

public abstract class ResourceDefinition {
    private final String key;
    private final int trackingBit;
    private final String mmoitemsBlockId;
    private final String textureHash;
    private final String displayName;
    private final Set<String> worlds;
    private final String dropType;
    private final String dropId;
    private final int dropAmount;
    private final boolean preventVanillaDrops;
    private final boolean ignoreSilkTouch;
    private final boolean dropNaturally;
    private final String breakSound;
    private final String failSound;
    private final String fallbackCommand;
    private final boolean applyPhysicsOnPlace;
    private final MmoCoreXpSettings mmocoreXp;

    protected ResourceDefinition(
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
            MmoCoreXpSettings mmocoreXp
    ) {
        this.key = key;
        this.trackingBit = trackingBit;
        this.mmoitemsBlockId = mmoitemsBlockId;
        this.textureHash = textureHash;
        this.displayName = displayName;
        this.worlds = Set.copyOf(worlds);
        this.dropType = dropType;
        this.dropId = dropId;
        this.dropAmount = Math.max(1, dropAmount);
        this.preventVanillaDrops = preventVanillaDrops;
        this.ignoreSilkTouch = ignoreSilkTouch;
        this.dropNaturally = dropNaturally;
        this.breakSound = breakSound;
        this.failSound = failSound;
        this.fallbackCommand = fallbackCommand;
        this.applyPhysicsOnPlace = applyPhysicsOnPlace;
        this.mmocoreXp = mmocoreXp == null ? MmoCoreXpSettings.disabled() : mmocoreXp;
    }

    public String key() { return key; }
    public int trackingBit() { return trackingBit; }
    public long trackingMask() { return 1L << trackingBit; }
    public String mmoitemsBlockId() { return mmoitemsBlockId; }
    public String textureHash() { return textureHash; }
    public String displayName() { return displayName; }
    public Set<String> worlds() { return worlds; }
    public String dropType() { return dropType; }
    public String dropId() { return dropId; }
    public int dropAmount() { return dropAmount; }
    public boolean preventVanillaDrops() { return preventVanillaDrops; }
    public boolean ignoreSilkTouch() { return ignoreSilkTouch; }
    public boolean dropNaturally() { return dropNaturally; }
    public String breakSound() { return breakSound; }
    public String failSound() { return failSound; }
    public String fallbackCommand() { return fallbackCommand; }
    public boolean applyPhysicsOnPlace() { return applyPhysicsOnPlace; }
    public MmoCoreXpSettings mmocoreXp() { return mmocoreXp; }

    public boolean isAllowedInWorld(String worldName) {
        return worlds.isEmpty() || worlds.contains(worldName);
    }
}
