package com.abhil.buildtools.server;

import net.minecraft.network.chat.Component;

public enum PaletteMode {
    SINGLE("single"),
    GRADIENT("gradient"),
    RANDOM("random");

    private final String key;

    PaletteMode(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("buildtools.palette_mode." + key);
    }
}
