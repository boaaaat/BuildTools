package com.abhil.buildtools.client;

import com.abhil.buildtools.shape.SelectionShape;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class ClientSelectionData {
    private static String dimension = "";
    private static Optional<BlockPos> first = Optional.empty();
    private static Optional<BlockPos> second = Optional.empty();
    private static SelectionShape shape = SelectionShape.CUBOID;
    private static List<BlockPos> points = List.of();
    private static List<BlockPos> preview = List.of();
    private static boolean detailedPreview;
    private static final Map<UUID, SharedSelection> sharedSelections = new LinkedHashMap<>();

    private ClientSelectionData() {
    }

    public static void setSelection(String syncedDimension, Optional<BlockPos> syncedFirst, Optional<BlockPos> syncedSecond, SelectionShape syncedShape, List<BlockPos> syncedPoints) {
        dimension = syncedDimension;
        first = syncedFirst;
        second = syncedSecond;
        shape = syncedShape;
        points = List.copyOf(syncedPoints);
    }

    public static void setPreview(List<BlockPos> positions, boolean detailed) {
        preview = List.copyOf(positions);
        detailedPreview = detailed;
    }

    public static void setSharedSelection(UUID owner, String syncedDimension, Optional<BlockPos> syncedFirst, Optional<BlockPos> syncedSecond, SelectionShape syncedShape, List<BlockPos> syncedPoints, List<BlockPos> syncedPreview, boolean syncedDetailedPreview) {
        sharedSelections.put(owner, new SharedSelection(
                owner,
                syncedDimension,
                syncedFirst,
                syncedSecond,
                syncedShape,
                List.copyOf(syncedPoints),
                List.copyOf(syncedPreview),
                syncedDetailedPreview));
    }

    public static void removeSharedSelection(UUID owner) {
        sharedSelections.remove(owner);
    }

    public static Optional<BlockPos> first() {
        return first;
    }

    public static Optional<BlockPos> second() {
        return second;
    }

    public static List<BlockPos> preview() {
        return preview;
    }

    public static List<BlockPos> points() {
        return points;
    }

    public static SelectionShape shape() {
        return shape;
    }

    public static boolean detailedPreview() {
        return detailedPreview;
    }

    public static Collection<SharedSelection> sharedSelections() {
        return List.copyOf(sharedSelections.values());
    }

    public record SharedSelection(
            UUID owner,
            String dimension,
            Optional<BlockPos> first,
            Optional<BlockPos> second,
            SelectionShape shape,
            List<BlockPos> points,
            List<BlockPos> preview,
            boolean detailedPreview) {
    }
}
