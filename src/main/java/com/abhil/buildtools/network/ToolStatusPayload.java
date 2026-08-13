package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.client.ClientToolStatusData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToolStatusPayload(boolean visible, Component title, List<Component> lines, int accentColor) implements CustomPacketPayload {
    public static final Type<ToolStatusPayload> TYPE = new Type<>(BuildTools.id("tool_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToolStatusPayload> STREAM_CODEC = CustomPacketPayload.codec(
            ToolStatusPayload::write,
            ToolStatusPayload::read);
    private static final int MAX_LINES = 8;

    public static ToolStatusPayload hidden() {
        return new ToolStatusPayload(false, Component.empty(), List.of(), 0);
    }

    private static ToolStatusPayload read(RegistryFriendlyByteBuf buffer) {
        boolean visible = buffer.readBoolean();
        Component title = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
        int count = buffer.readVarInt();
        List<Component> lines = new ArrayList<>(Math.min(count, MAX_LINES));
        for (int i = 0; i < count; i++) {
            Component line = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
            if (i < MAX_LINES) {
                lines.add(line);
            }
        }
        int accentColor = buffer.readInt();
        return new ToolStatusPayload(visible, title, lines, accentColor);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(visible);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, title);
        int count = Math.min(lines.size(), MAX_LINES);
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, lines.get(i));
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
}
