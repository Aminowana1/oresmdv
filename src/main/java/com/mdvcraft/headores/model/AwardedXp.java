package com.mdvcraft.headores.model;

public record AwardedXp(String professionId, int professionXp, int mainXp) {
    public static final AwardedXp NONE = new AwardedXp("", 0, 0);
}
