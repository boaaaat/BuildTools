package com.abhil.buildtools.shape;

import net.minecraft.network.chat.Component;

public enum SelectionShape {
    CUBOID("cuboid"),
    WALLS("walls"),
    FLOOR("floor"),
    CEILING("ceiling"),
    HOLLOW_BOX("hollow_box"),
    LINE("line"),
    CYLINDER("cylinder"),
    SPHERE("sphere"),
    ELLIPSOID("ellipsoid"),
    ROAD("road"),
    TUNNEL("tunnel"),
    ARCH("arch"),
    DOME("dome");

    private final String key;

    SelectionShape(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("buildtools.shape." + key);
    }

    public SelectionShape next() {
        SelectionShape[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
