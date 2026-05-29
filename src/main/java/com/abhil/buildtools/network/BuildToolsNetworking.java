package com.abhil.buildtools.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class BuildToolsNetworking {
    public static final int MAX_PREVIEW_POSITIONS = 4096;

    private BuildToolsNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SelectionSyncPayload.TYPE, SelectionSyncPayload.STREAM_CODEC, SelectionSyncPayload::handle);
        registrar.playToClient(PreviewPayload.TYPE, PreviewPayload.STREAM_CODEC, PreviewPayload::handle);
        registrar.playToClient(ToolStatusPayload.TYPE, ToolStatusPayload.STREAM_CODEC, ToolStatusPayload::handle);
        registrar.playToServer(ArchPeakPayload.TYPE, ArchPeakPayload.STREAM_CODEC, ArchPeakPayload::handle);
        registrar.playToServer(AdvancedSelectionActionPayload.TYPE, AdvancedSelectionActionPayload.STREAM_CODEC, AdvancedSelectionActionPayload::handle);
        registrar.playToServer(PaletteWeightPayload.TYPE, PaletteWeightPayload.STREAM_CODEC, PaletteWeightPayload::handle);
        registrar.playToServer(RequestPreviewPayload.TYPE, RequestPreviewPayload.STREAM_CODEC, RequestPreviewPayload::handle);
        registrar.playToServer(RoadWidthPayload.TYPE, RoadWidthPayload.STREAM_CODEC, RoadWidthPayload::handle);
        registrar.playToServer(ScrollToolPayload.TYPE, ScrollToolPayload.STREAM_CODEC, ScrollToolPayload::handle);
        registrar.playToServer(OpenToolMenuPayload.TYPE, OpenToolMenuPayload.STREAM_CODEC, OpenToolMenuPayload::handle);
        registrar.playToServer(ShortcutActionPayload.TYPE, ShortcutActionPayload.STREAM_CODEC, ShortcutActionPayload::handle);
        registrar.playToServer(BlueprintLibraryQueryPayload.TYPE, BlueprintLibraryQueryPayload.STREAM_CODEC, BlueprintLibraryQueryPayload::handle);
    }
}
