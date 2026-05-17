package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.client.ClientToolStatusData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToolStatusPayload(boolean visible, String title, List<String> lines, int accentColor) implements CustomPacketPayload {
    public static final Type<ToolStatusPayload> TYPE = new Type<>(BuildTools.id("tool_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToolStatusPayload> STREAM_CODEC = CustomPacketPayload.codec(
            ToolStatusPayload::write,
            ToolStatusPayload::read);
    private static final int MAX_LINES = 8;
    private static final int MAX_TEXT_LENGTH = 96;

    public static ToolStatusPayload hidden() {
        return new ToolStatusPayload(false, "", List.of(), 0);
    }

    private static ToolStatusPayload read(RegistryFriendlyByteBuf buffer) {
        boolean visible = buffer.readBoolean();
        String title = buffer.readUtf(MAX_TEXT_LENGTH);
        int count = buffer.readVarInt();
        List<String> lines = new ArrayList<>(Math.min(count, MAX_LINES));
        for (int i = 0; i < count; i++) {
            String line = buffer.readUtf(MAX_TEXT_LENGTH);
            if (i < MAX_LINES) {
                lines.add(line);
            }
        }
        int accentColor = buffer.readInt();
        return new ToolStatusPayload(visible, title, lines, accentColor);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(visible);
        buffer.writeUtf(trim(title));
        buffer.writeVarInt(lines.size());
        for (String line : lines) {
            buffer.writeUtf(trim(line));
        }
        buffer.writeInt(accentColor);
    }

    public static void handle(ToolStatusPayload payload, IPayloadContext context) {
        if (payload.visible()) {
            ClientToolStatusData.set(payload.title(), payload.lines(), payload.accentColor());
        } else {
            ClientToolStatusData.clear();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static String trim(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_TEXT_LENGTH ? value.substring(0, MAX_TEXT_LENGTH) : value;
    }
}
