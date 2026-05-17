package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.client.ClientSelectionData;
import com.abhil.buildtools.shape.SelectionShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SelectionSyncPayload(
        UUID owner,
        boolean shared,
        boolean remove,
        String dimension,
        Optional<BlockPos> first,
        Optional<BlockPos> second,
        SelectionShape shape,
        List<BlockPos> points,
        List<BlockPos> preview,
        boolean detailedPreview) implements CustomPacketPayload {
    public static final Type<SelectionSyncPayload> TYPE = new Type<>(BuildTools.id("selection_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectionSyncPayload> STREAM_CODEC = CustomPacketPayload.codec(
            SelectionSyncPayload::write,
            SelectionSyncPayload::read);

    private static SelectionSyncPayload read(RegistryFriendlyByteBuf buffer) {
        UUID owner = buffer.readUUID();
        boolean shared = buffer.readBoolean();
        boolean remove = buffer.readBoolean();
        String dimension = buffer.readUtf();
        Optional<BlockPos> first = buffer.readBoolean() ? Optional.of(BlockPos.STREAM_CODEC.decode(buffer)) : Optional.empty();
        Optional<BlockPos> second = buffer.readBoolean() ? Optional.of(BlockPos.STREAM_CODEC.decode(buffer)) : Optional.empty();
        SelectionShape shape = buffer.readEnum(SelectionShape.class);
        int count = buffer.readVarInt();
        List<BlockPos> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(BlockPos.STREAM_CODEC.decode(buffer));
        }
        int previewCount = buffer.readVarInt();
        List<BlockPos> preview = new ArrayList<>(previewCount);
        for (int i = 0; i < previewCount; i++) {
            preview.add(BlockPos.STREAM_CODEC.decode(buffer));
        }
        boolean detailedPreview = buffer.readBoolean();
        return new SelectionSyncPayload(owner, shared, remove, dimension, first, second, shape, points, preview, detailedPreview);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(owner);
        buffer.writeBoolean(shared);
        buffer.writeBoolean(remove);
        buffer.writeUtf(dimension);
        buffer.writeBoolean(first.isPresent());
        first.ifPresent(pos -> BlockPos.STREAM_CODEC.encode(buffer, pos));
        buffer.writeBoolean(second.isPresent());
        second.ifPresent(pos -> BlockPos.STREAM_CODEC.encode(buffer, pos));
        buffer.writeEnum(shape);
        buffer.writeVarInt(points.size());
        for (BlockPos point : points) {
            BlockPos.STREAM_CODEC.encode(buffer, point);
        }
        buffer.writeVarInt(preview.size());
        for (BlockPos point : preview) {
            BlockPos.STREAM_CODEC.encode(buffer, point);
        }
        buffer.writeBoolean(detailedPreview);
    }

    public static void handle(SelectionSyncPayload payload, IPayloadContext context) {
        if (payload.shared()) {
            if (payload.remove()) {
                ClientSelectionData.removeSharedSelection(payload.owner());
            } else {
                ClientSelectionData.setSharedSelection(payload.owner(), payload.dimension(), payload.first(), payload.second(), payload.shape(), payload.points(), payload.preview(), payload.detailedPreview());
            }
            return;
        }
        ClientSelectionData.setSelection(payload.dimension(), payload.first(), payload.second(), payload.shape(), payload.points());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
