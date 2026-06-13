package com.abhil.buildtools.server;

import net.minecraft.network.chat.Component;

public enum SelectionMeasure {
    OFF("off"),
    MIDPOINT("midpoint"),
    DIMENSIONS("dimensions"),
    SELECTION_COUNT("selection_count"),
    POINT_DISTANCE("point_distance"),
    PATH_LENGTH("path_length"),
    BOUNDS("bounds"),
    CENTER_LINES("center_lines");

    private final String key;

    SelectionMeasure(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("buildtools.measure." + key);
    }

    public Component description() {
        return Component.translatable("buildtools.measure." + key + ".description");
    }

    public SelectionMeasure next() {
        SelectionMeasure[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
