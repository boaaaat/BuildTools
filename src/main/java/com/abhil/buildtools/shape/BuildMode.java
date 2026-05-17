package com.abhil.buildtools.shape;

import net.minecraft.network.chat.Component;

public enum BuildMode {
    FILL("fill"),
    REPLACE("replace"),
    OVERWRITE("overwrite");

    private final String key;

    BuildMode(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("buildtools.mode." + key);
    }

    public Component description() {
        return Component.translatable("buildtools.mode." + key + ".description");
    }

    public BuildMode next() {
        BuildMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
