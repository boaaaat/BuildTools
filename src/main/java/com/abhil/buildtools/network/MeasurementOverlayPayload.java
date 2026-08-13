package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.client.ClientSelectionData;
import com.abhil.buildtools.server.SelectionMeasurements;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MeasurementOverlayPayload(List<Marker> markers) implements CustomPacketPayload {
    public static final Type<MeasurementOverlayPayload> TYPE = new Type<>(BuildTools.id("measurement_overlay"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeasurementOverlayPayload> STREAM_CODEC = CustomPacketPayload.codec(
            MeasurementOverlayPayload::write,
            MeasurementOverlayPayload::read);
    private static final int MAX_MARKERS = 8;

    public MeasurementOverlayPayload(SelectionMeasurements.Result result) {
        this(result.markers().stream()
                .map(marker -> new Marker(marker.label(), marker.x(), marker.y(), marker.z()))
                .toList());
    }

    private static MeasurementOverlayPayload read(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<Marker> markers = new ArrayList<>(Math.min(count, MAX_MARKERS));
        for (int i = 0; i < count; i++) {
            Marker marker = new Marker(
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble());
            if (i < MAX_MARKERS) {
                markers.add(marker);
            }
        }
        return new MeasurementOverlayPayload(markers);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(markers.size(), MAX_MARKERS);
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Marker marker = markers.get(i);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, marker.label());
            buffer.writeDouble(marker.x());
            buffer.writeDouble(marker.y());
            buffer.writeDouble(marker.z());
        }
    }

    public static void handle(MeasurementOverlayPayload payload, IPayloadContext context) {
        ClientSelectionData.setMeasurementMarkers(payload.markers().stream()
                .map(marker -> new ClientSelectionData.MeasurementMarker(marker.label(), marker.x(), marker.y(), marker.z()))
                .toList());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Marker(Component label, double x, double y, double z) {
    }
}
