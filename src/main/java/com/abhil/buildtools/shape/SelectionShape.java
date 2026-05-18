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
    DOME("dome"),
    CUSTOM_SMART("custom_smart"),
    STAIRS("stairs");

    private static final SelectionShape[] BASIC = {
            CUBOID,
            WALLS,
            FLOOR,
            CEILING,
            HOLLOW_BOX,
            LINE,
            CYLINDER,
            SPHERE,
            ELLIPSOID,
            ROAD,
            TUNNEL,
            ARCH,
            DOME
    };

    private static final SelectionShape[] WITH_STAIRS = {
            CUBOID,
            WALLS,
            FLOOR,
            CEILING,
            HOLLOW_BOX,
            LINE,
            CYLINDER,
            SPHERE,
            ELLIPSOID,
            ROAD,
            TUNNEL,
            ARCH,
            DOME,
            STAIRS
    };

    private static final SelectionShape[] ADVANCED_SELECTION = {
            CUBOID,
            WALLS,
            FLOOR,
            CEILING,
            HOLLOW_BOX,
            LINE,
            CYLINDER,
            SPHERE,
            ELLIPSOID,
            ROAD,
            TUNNEL,
            ARCH,
            DOME,
            CUSTOM_SMART,
            STAIRS
    };

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

    public static SelectionShape[] basicShapes() {
        return BASIC.clone();
    }

    public static SelectionShape[] shapesWithStairs() {
        return WITH_STAIRS.clone();
    }

    public static SelectionShape[] advancedSelectionShapes() {
        return ADVANCED_SELECTION.clone();
    }
}
