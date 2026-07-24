package com.mdvcraft.headores.model;

public record IntRange(int min, int max) {
    public IntRange {
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }
    }

    public static IntRange zero() {
        return new IntRange(0, 0);
    }
}
