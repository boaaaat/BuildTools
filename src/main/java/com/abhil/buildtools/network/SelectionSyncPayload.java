package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.client.ClientSelectionData;
import com.abhil.buildtools.shape.SelectionShape;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SelectionSyncPayload(String dimension, Optional<BlockPos> first, Optional<BlockPos> second, SelectionShape shape) implements CustomPacketPayload {
    public static final Type<SelectionSyncPayload> TYPE = new Type<>(BuildTools.id("selection_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectionSyncPayload> STREAM_CODEC = CustomPacketPayload.codec(
            SelectionSyncPayload::write,
            SelectionSyncPayload::read);

    private static SelectionSyncPayload read(RegistryFriendlyByteBuf buffer) {
        String dimension = buffer.readUtf();
        Optional<BlockPos> first = buffer.readBoolean() ? Optional.of(BlockPos.STREAM_CODEC.decode(buffer)) : Optional.empty();
        Optional<BlockPos> second = buffer.readBoolean() ? Optional.of(BlockPos.STREAM_CODEC.decode(buffer)) : Optional.empty();
        SelectionShape shape = buffer.readEnum(SelectionShape.class);
        return new SelectionSyncPayload(dimension, first, second, shape);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUtf(dimension);
        buffer.writeBoolean(first.isPresent());
        first.ifPresent(pos -> BlockPos.STREAM_CODEC.encode(buffer, pos));
        buffer.writeBoolean(second.isPresent());
        second.ifPresent(pos -> BlockPos.STREAM_CODEC.encode(buffer, pos));
        buffer.writeEnum(shape);
    }

    public static void handle(SelectionSyncPayload payload, IPayloadContext context) {
        ClientSelectionData.setSelection(payload.dimension(), payload.first(), payload.second(), payload.shape());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
