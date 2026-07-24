package com.mdvcraft.headores.util;

import org.bukkit.Location;
import org.bukkit.Sound;

import java.util.Locale;

public final class FormatUtil {
    private FormatUtil() {}

    public static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) return Integer.toString((int) Math.rint(value));
        return String.format(Locale.US, "%.2f", value);
    }

    public static String duration(long seconds) {
        if (seconds <= 0) return "0s";
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0) return hours + "h " + minutes + "m " + secs + "s";
        if (minutes > 0) return minutes + "m " + secs + "s";
        return secs + "s";
    }

    public static void playSound(Location location, String rawSound, float volume, float pitch) {
        if (location == null || rawSound == null || rawSound.isBlank()) return;
        try {
            Sound sound = Sound.valueOf(rawSound.toUpperCase(Locale.ROOT));
            location.getWorld().playSound(location, sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
        }
    }
}
