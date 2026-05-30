package com.abhil.buildtools.shape;

import net.minecraft.network.chat.Component;

public enum ArchMode {
    OPEN("open"),
    EDGE_WALLS("edge_walls"),
    WALLS("walls");

    private final String key;

    ArchMode(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("buildtools.arch_mode." + key);
    }

    public ArchMode next() {
        ArchMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
