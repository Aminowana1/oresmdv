package com.mdvcraft.headores.model;

public record MmoCoreXpSettings(
        boolean enabled,
        String professionId,
        IntRange professionAmount,
        IntRange mainAmount,
        boolean split
) {
    public static MmoCoreXpSettings disabled() {
        return new MmoCoreXpSettings(false, "", IntRange.zero(), IntRange.zero(), false);
    }
}
