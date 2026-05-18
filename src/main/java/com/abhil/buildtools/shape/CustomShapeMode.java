package com.abhil.buildtools.shape;

import net.minecraft.network.chat.Component;

public enum CustomShapeMode {
    AUTO("auto"),
    LINE("line"),
    POLYGON_FILL("polygon_fill"),
    SURFACE("surface"),
    VOLUME("volume");

    private final String key;

    CustomShapeMode(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("buildtools.custom_shape_mode." + key);
    }

    public CustomShapeMode next() {
        CustomShapeMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
