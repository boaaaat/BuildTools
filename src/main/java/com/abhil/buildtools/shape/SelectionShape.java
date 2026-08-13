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
    PYRAMID("pyramid"),
    GABLE_ROOF("gable_roof"),
    HIP_ROOF("hip_roof"),
    A_FRAME("a_frame"),
    ROOM_FRAME("room_frame"),
    BRIDGE("bridge"),
    TOWER("tower"),
    CUSTOM_SMART("custom_smart"),
    STAIRS("stairs"),
    CURVE("curve");

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
            DOME,
            PYRAMID
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
            PYRAMID,
            GABLE_ROOF,
            HIP_ROOF,
            A_FRAME,
            ROOM_FRAME,
            BRIDGE,
            TOWER,
            CURVE,
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
            PYRAMID,
            GABLE_ROOF,
            HIP_ROOF,
            A_FRAME,
            ROOM_FRAME,
            BRIDGE,
            TOWER,
            CURVE,
            CUSTOM_SMART,
            STAIRS
    };

    private static final SelectionShape[] BRUSH = {
            SPHERE,
            CYLINDER,
            CUBOID,
            FLOOR
    };

    private static final SelectionShape[] ADVANCED_STRUCTURES = {
            GABLE_ROOF,
            HIP_ROOF,
            A_FRAME,
            ROOM_FRAME,
            BRIDGE,
            TOWER,
            CURVE
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

    public static SelectionShape[] brushShapes() {
        return BRUSH.clone();
    }

    public static SelectionShape[] advancedStructureShapes() {
        return ADVANCED_STRUCTURES.clone();
    }

    public boolean isAdvancedStructure() {
        for (SelectionShape shape : ADVANCED_STRUCTURES) {
            if (shape == this) {
                return true;
            }
        }
        return false;
    }
}
