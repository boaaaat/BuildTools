package com.abhil.buildtools.network;

import com.abhil.buildtools.BuildTools;
import com.abhil.buildtools.client.ClientSelectionData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PreviewPayload(
        List<BlockPos> positions,
        boolean detailed,
        List<Integer> colors,
        int totalPositions,
        Optional<BlockPos> boundsMin,
        Optional<BlockPos> boundsMax) implements CustomPacketPayload {
    public static final Type<PreviewPayload> TYPE = new Type<>(BuildTools.id("preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PreviewPayload> STREAM_CODEC = CustomPacketPayload.codec(
            PreviewPayload::write,
            PreviewPayload::read);

    public PreviewPayload(List<BlockPos> positions, boolean detailed) {
        this(positions, detailed, List.of(), positions.size(), minBounds(positions), maxBounds(positions));
    }

    public PreviewPayload(List<BlockPos> positions, boolean detailed, List<Integer> colors) {
        this(positions, detailed, colors, positions.size(), minBounds(positions), maxBounds(positions));
    }

    public static PreviewPayload create(List<BlockPos> fullPositions, boolean detailed, List<Integer> fullColors) {
        int total = fullPositions.size();
        if (total <= BuildToolsNetworking.MAX_PREVIEW_POSITIONS) {
            return new PreviewPayload(List.copyOf(fullPositions), detailed, List.copyOf(fullColors), total, minBounds(fullPositions), maxBounds(fullPositions));
        }
        List<BlockPos> sampled = new ArrayList<>(BuildToolsNetworking.MAX_PREVIEW_POSITIONS);
        boolean hasColors = fullColors.size() == total;
        List<Integer> sampledColors = new ArrayList<>(hasColors ? BuildToolsNetworking.MAX_PREVIEW_POSITIONS : 0);
        int last = total - 1;
        int sampleLast = BuildToolsNetworking.MAX_PREVIEW_POSITIONS - 1;
        for (int i = 0; i < BuildToolsNetworking.MAX_PREVIEW_POSITIONS; i++) {
            int index = (int) ((long) i * last / sampleLast);
            sampled.add(fullPositions.get(index));
            if (hasColors) {
                sampledColors.add(fullColors.get(index));
            }
        }
        return new PreviewPayload(List.copyOf(sampled), detailed, List.copyOf(sampledColors), total, minBounds(fullPositions), maxBounds(fullPositions));
    }

    private static PreviewPayload read(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<BlockPos> positions = new ArrayList<>(Math.min(count, BuildToolsNetworking.MAX_PREVIEW_POSITIONS));
        for (int i = 0; i < count; i++) {
            BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
            if (i < BuildToolsNetworking.MAX_PREVIEW_POSITIONS) {
                positions.add(pos);
            }
        }
        boolean detailed = buffer.readBoolean();
        int colorCount = buffer.readVarInt();
        List<Integer> colors = new ArrayList<>(Math.min(colorCount, BuildToolsNetworking.MAX_PREVIEW_POSITIONS));
        for (int i = 0; i < colorCount; i++) {
            int color = buffer.readInt();
            if (i < BuildToolsNetworking.MAX_PREVIEW_POSITIONS) {
                colors.add(color);
            }
        }
        int totalPositions = buffer.readVarInt();
        Optional<BlockPos> boundsMin = buffer.readBoolean() ? Optional.of(buffer.readBlockPos()) : Optional.empty();
        Optional<BlockPos> boundsMax = buffer.readBoolean() ? Optional.of(buffer.readBlockPos()) : Optional.empty();
        return new PreviewPayload(positions, detailed, colors, totalPositions, boundsMin, boundsMax);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(positions.size());
        for (BlockPos pos : positions) {
            BlockPos.STREAM_CODEC.encode(buffer, pos);
        }
        buffer.writeBoolean(detailed);
        buffer.writeVarInt(colors.size());
        for (int color : colors) {
            buffer.writeInt(color);
        }
        buffer.writeVarInt(totalPositions);
        buffer.writeBoolean(boundsMin.isPresent());
        boundsMin.ifPresent(buffer::writeBlockPos);
        buffer.writeBoolean(boundsMax.isPresent());
        boundsMax.ifPresent(buffer::writeBlockPos);
    }

    public static void handle(PreviewPayload payload, IPayloadContext context) {
        ClientSelectionData.setPreview(
                payload.positions(),
                payload.detailed(),
                payload.colors(),
                payload.totalPositions(),
                payload.boundsMin(),
                payload.boundsMax());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static Optional<BlockPos> minBounds(List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(
                positions.stream().mapToInt(BlockPos::getX).min().orElse(0),
                positions.stream().mapToInt(BlockPos::getY).min().orElse(0),
                positions.stream().mapToInt(BlockPos::getZ).min().orElse(0)));
    }

    private static Optional<BlockPos> maxBounds(List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(
                positions.stream().mapToInt(BlockPos::getX).max().orElse(0),
                positions.stream().mapToInt(BlockPos::getY).max().orElse(0),
                positions.stream().mapToInt(BlockPos::getZ).max().orElse(0)));
    }
}
