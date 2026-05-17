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
        registrar.playToServer(RequestPreviewPayload.TYPE, RequestPreviewPayload.STREAM_CODEC, RequestPreviewPayload::handle);
    }
}
