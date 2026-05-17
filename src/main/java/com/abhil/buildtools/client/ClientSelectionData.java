package com.abhil.buildtools.client;

import com.abhil.buildtools.shape.SelectionShape;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public final class ClientSelectionData {
    private static String dimension = "";
    private static Optional<BlockPos> first = Optional.empty();
    private static Optional<BlockPos> second = Optional.empty();
    private static SelectionShape shape = SelectionShape.CUBOID;
    private static List<BlockPos> points = List.of();
    private static List<BlockPos> preview = List.of();
    private static boolean detailedPreview;

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
}
