package com.abhil.buildtools.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class BuildToolsNetworking {
    public static final int MAX_PREVIEW_POSITIONS = 4096;

    private BuildToolsNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToClient(SelectionSyncPayload.TYPE, SelectionSyncPayload.STREAM_CODEC, SelectionSyncPayload::handle);
        registrar.playToClient(PreviewPayload.TYPE, PreviewPayload.STREAM_CODEC, PreviewPayload::handle);
        registrar.playToClient(ToolStatusPayload.TYPE, ToolStatusPayload.STREAM_CODEC, ToolStatusPayload::handle);
        registrar.playToClient(MeasurementOverlayPayload.TYPE, MeasurementOverlayPayload.STREAM_CODEC, MeasurementOverlayPayload::handle);
        registrar.playToClient(TextPromptPayload.TYPE, TextPromptPayload.STREAM_CODEC, TextPromptPayload::handle);
        registrar.playToServer(AdvancedShapeOptionPayload.TYPE, AdvancedShapeOptionPayload.STREAM_CODEC, AdvancedShapeOptionPayload::handle);
        registrar.playToServer(ArchPeakPayload.TYPE, ArchPeakPayload.STREAM_CODEC, ArchPeakPayload::handle);
        registrar.playToServer(AdvancedSelectionActionPayload.TYPE, AdvancedSelectionActionPayload.STREAM_CODEC, AdvancedSelectionActionPayload::handle);
        registrar.playToServer(BlockRotationModePayload.TYPE, BlockRotationModePayload.STREAM_CODEC, BlockRotationModePayload::handle);
        registrar.playToServer(BridgeWidthPayload.TYPE, BridgeWidthPayload.STREAM_CODEC, BridgeWidthPayload::handle);
        registrar.playToServer(BrushSettingPayload.TYPE, BrushSettingPayload.STREAM_CODEC, BrushSettingPayload::handle);
        registrar.playToServer(GradientDirectionPayload.TYPE, GradientDirectionPayload.STREAM_CODEC, GradientDirectionPayload::handle);
        registrar.playToServer(MaterialWeightPayload.TYPE, MaterialWeightPayload.STREAM_CODEC, MaterialWeightPayload::handle);
        registrar.playToServer(MaterialSelectionQueryPayload.TYPE, MaterialSelectionQueryPayload.STREAM_CODEC, MaterialSelectionQueryPayload::handle);
        registrar.playToServer(PresetLibraryQueryPayload.TYPE, PresetLibraryQueryPayload.STREAM_CODEC, PresetLibraryQueryPayload::handle);
        registrar.playToServer(PaletteLibraryQueryPayload.TYPE, PaletteLibraryQueryPayload.STREAM_CODEC, PaletteLibraryQueryPayload::handle);
        registrar.playToServer(RequestPreviewPayload.TYPE, RequestPreviewPayload.STREAM_CODEC, RequestPreviewPayload::handle);
        registrar.playToServer(RoadWidthPayload.TYPE, RoadWidthPayload.STREAM_CODEC, RoadWidthPayload::handle);
        registrar.playToServer(StairDirectionPayload.TYPE, StairDirectionPayload.STREAM_CODEC, StairDirectionPayload::handle);
        registrar.playToServer(ScrollToolPayload.TYPE, ScrollToolPayload.STREAM_CODEC, ScrollToolPayload::handle);
        registrar.playToServer(TowerFloorHeightPayload.TYPE, TowerFloorHeightPayload.STREAM_CODEC, TowerFloorHeightPayload::handle);
        registrar.playToServer(OpenToolMenuPayload.TYPE, OpenToolMenuPayload.STREAM_CODEC, OpenToolMenuPayload::handle);
        registrar.playToServer(PickMaterialPayload.TYPE, PickMaterialPayload.STREAM_CODEC, PickMaterialPayload::handle);
        registrar.playToServer(ShortcutActionPayload.TYPE, ShortcutActionPayload.STREAM_CODEC, ShortcutActionPayload::handle);
        registrar.playToServer(BlueprintLibraryQueryPayload.TYPE, BlueprintLibraryQueryPayload.STREAM_CODEC, BlueprintLibraryQueryPayload::handle);
        registrar.playToServer(TextPromptResponsePayload.TYPE, TextPromptResponsePayload.STREAM_CODEC, TextPromptResponsePayload::handle);
    }
}
