package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.client.ClientSelectionData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PreviewPayload(List<BlockPos> positions) implements CustomPacketPayload {
    public static final Type<PreviewPayload> TYPE = new Type<>(BuildTools.id("preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PreviewPayload> STREAM_CODEC = CustomPacketPayload.codec(
            PreviewPayload::write,
            PreviewPayload::read);

    private static PreviewPayload read(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<BlockPos> positions = new ArrayList<>(Math.min(count, BuildToolsNetworking.MAX_PREVIEW_POSITIONS));
        for (int i = 0; i < count; i++) {
            BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
            if (i < BuildToolsNetworking.MAX_PREVIEW_POSITIONS) {
                positions.add(pos);
            }
        }
        return new PreviewPayload(positions);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(positions.size());
        for (BlockPos pos : positions) {
            BlockPos.STREAM_CODEC.encode(buffer, pos);
        }
    }

    public static void handle(PreviewPayload payload, IPayloadContext context) {
        ClientSelectionData.setPreview(payload.positions());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
