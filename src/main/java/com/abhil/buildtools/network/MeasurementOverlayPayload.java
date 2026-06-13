package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.client.ClientSelectionData;
import com.abhil.buildtools.server.SelectionMeasurements;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MeasurementOverlayPayload(List<Marker> markers) implements CustomPacketPayload {
    public static final Type<MeasurementOverlayPayload> TYPE = new Type<>(BuildTools.id("measurement_overlay"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeasurementOverlayPayload> STREAM_CODEC = CustomPacketPayload.codec(
            MeasurementOverlayPayload::write,
            MeasurementOverlayPayload::read);
    private static final int MAX_MARKERS = 8;
    private static final int MAX_LABEL_LENGTH = 32;

    public MeasurementOverlayPayload(SelectionMeasurements.Result result) {
        this(result.markers().stream()
                .map(marker -> new Marker(marker.label(), marker.x(), marker.y(), marker.z()))
                .toList());
    }

    private static MeasurementOverlayPayload read(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<Marker> markers = new ArrayList<>(Math.min(count, MAX_MARKERS));
        for (int i = 0; i < count; i++) {
            Marker marker = new Marker(buffer.readUtf(MAX_LABEL_LENGTH), buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            if (i < MAX_MARKERS) {
                markers.add(marker);
            }
        }
        return new MeasurementOverlayPayload(markers);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(markers.size());
        for (Marker marker : markers) {
            buffer.writeUtf(trim(marker.label()), MAX_LABEL_LENGTH);
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

    private static String trim(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_LABEL_LENGTH ? value.substring(0, MAX_LABEL_LENGTH) : value;
    }

    public record Marker(String label, double x, double y, double z) {
    }
}
