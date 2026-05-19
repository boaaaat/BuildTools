package com.abhil.buildtools.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class BuildToolsClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DETAILED_PREVIEW = BUILDER
            .comment("Render per-block preview outlines whenever possible.")
            .define("detailedPreview", true);

    public static final ModConfigSpec.IntValue SELF_PREVIEW_COLOR = BUILDER
            .comment("RGB color for your own selection and operation previews.")
            .defineInRange("selfPreviewColor", 0x33FF40, 0, 0xFFFFFF);

    public static final ModConfigSpec.IntValue SHARED_PREVIEW_COLOR = BUILDER
            .comment("RGB color for other players' shared selection previews.")
            .defineInRange("sharedPreviewColor", 0x99E6FF, 0, 0xFFFFFF);

    public static final ModConfigSpec.BooleanValue SHOW_SHARED_SELECTIONS = BUILDER
            .comment("Render shared selections from other players.")
            .define("showSharedSelections", true);

    public static final ModConfigSpec.BooleanValue OVERLAY_ENABLED = BUILDER
            .comment("Show the BuildTools status overlay while holding a BuildTools item.")
            .define("overlayEnabled", true);

    public static final ModConfigSpec.EnumValue<OverlayPosition> OVERLAY_POSITION = BUILDER
            .comment("Screen corner used for the BuildTools status overlay.")
            .defineEnum("overlayPosition", OverlayPosition.BOTTOM_LEFT);

    public static final ModConfigSpec.DoubleValue OVERLAY_SCALE = BUILDER
            .comment("Scale multiplier for the BuildTools status overlay.")
            .defineInRange("overlayScale", 1.0D, 0.5D, 2.0D);

    public static final ModConfigSpec.IntValue OVERLAY_AUTO_HIDE_SECONDS = BUILDER
            .comment("Seconds before the overlay hides after its last update. Zero keeps it visible.")
            .defineInRange("overlayAutoHideSeconds", 0, 0, 60);

    public static final ModConfigSpec.BooleanValue SHOW_OVERLAY_MATERIALS = BUILDER
            .comment("Show material-related status lines in the overlay.")
            .define("showOverlayMaterials", true);

    public static final ModConfigSpec.BooleanValue SHOW_OVERLAY_LIMITS = BUILDER
            .comment("Show limits and warning status lines in the overlay.")
            .define("showOverlayLimits", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BuildToolsClientConfig() {
    }

    public enum OverlayPosition {
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        TOP_LEFT,
        TOP_RIGHT
    }
}
