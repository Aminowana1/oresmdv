package com.mdvcraft.headores.service;

import com.mdvcraft.headores.config.PluginSettings;
import com.mdvcraft.headores.model.AwardedXp;
import com.mdvcraft.headores.model.IntRange;
import com.mdvcraft.headores.model.MmoCoreXpSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public final class MmoCoreBridge {
    private final PluginSettings settings;

    public MmoCoreBridge(PluginSettings settings) {
        this.settings = settings;
    }

    public AwardedXp award(Player player, MmoCoreXpSettings xp, String source) {
        if (player == null || xp == null || !xp.enabled()) return AwardedXp.NONE;
        int professionXp = roll(xp.professionAmount());
        String professionId = xp.professionId() == null ? "" : xp.professionId();
        if (professionXp > 0 && !professionId.isBlank()) {
            dispatch(player, professionId, professionXp, xp.split(), source);
        } else {
            professionXp = 0;
        }

        int mainXp = roll(xp.mainAmount());
        if (mainXp > 0) dispatch(player, "main", mainXp, xp.split(), source);
        return new AwardedXp(professionId, professionXp, mainXp);
    }

    private int roll(IntRange range) {
        if (range == null) return 0;
        if (range.max() <= range.min()) return Math.max(0, range.min());
        return Math.max(0, ThreadLocalRandom.current().nextInt(range.min(), range.max() + 1));
    }

    private void dispatch(Player player, String target, int amount, boolean split, String source) {
        String command = settings.mmocoreExpCommand();
        if (command == null || command.isBlank()) return;
        command = command
                .replace("%player%", player.getName())
                .replace("%target%", target)
                .replace("%profession%", target)
                .replace("%amount%", Integer.toString(amount))
                .replace("%split%", Boolean.toString(split))
                .replace("%source%", source == null ? "" : source);
        if (command.startsWith("/")) command = command.substring(1);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}
