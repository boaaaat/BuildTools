package com.abhil.buildtools.server;

import com.abhil.buildtools.shape.Selection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

public final class SelectionMeasurements {
    private SelectionMeasurements() {
    }

    public static Result measure(ServerPlayer player, SelectionMeasure measure) {
        if (measure == SelectionMeasure.OFF) {
            return Result.empty();
        }
        Selection selection = BuildToolsState.selection(player);
        if (selection.dimension() == null || !selection.isComplete()) {
            return Result.lines(List.of("Measurement: " + measure.displayName().getString(), "Set selection points first."));
        }
        if (!selection.dimension().equals(player.level().dimension())) {
            return Result.lines(List.of("Measurement: " + measure.displayName().getString(), "Selection is in another dimension."));
        }
        if (measure == SelectionMeasure.POINT_DISTANCE) {
            return pointDistance(selection.points());
        }
        if (measure == SelectionMeasure.PATH_LENGTH) {
            return pathLength(selection.points());
        }

        List<BlockPos> generated = BuildToolsState.generatedSelection(player);
        if (generated.isEmpty()) {
            return Result.lines(List.of("Measurement: " + measure.displayName().getString(), "Selected shape is empty."));
        }

        Bounds bounds = Bounds.of(generated);
        return switch (measure) {
            case MIDPOINT -> midpoint(bounds);
            case DIMENSIONS -> dimensions(bounds, generated.size());
            case SELECTION_COUNT -> selectionCount(player, generated);
            case POINT_DISTANCE -> pointDistance(selection.points());
            case PATH_LENGTH -> pathLength(selection.points());
            case BOUNDS -> bounds(bounds);
            case CENTER_LINES -> centerLines(bounds);
            case OFF -> Result.empty();
        };
    }

    private static Result midpoint(Bounds bounds) {
        Center center = bounds.center();
        List<BlockPos> centerBlocks = bounds.centerBlocks();
        List<Marker> markers = new ArrayList<>(centerBlocks.size());
        for (int i = 0; i < centerBlocks.size(); i++) {
            BlockPos pos = centerBlocks.get(i);
            markers.add(new Marker(centerBlocks.size() == 1 ? "Midpoint" : "Midpoint " + (i + 1), pos.getX(), pos.getY(), pos.getZ()));
        }
        return new Result(
                List.of(
                        "Measurement: Midpoint",
                        "Midpoint: " + center.format(),
                        "Center blocks: " + centerBlocks.size(),
                        "Blocks: " + compactBlocks(centerBlocks)),
                markers,
                centerBlocks);
    }

    private static Result dimensions(Bounds bounds, int selectedCount) {
        return Result.lines(List.of(
                "Measurement: Dimensions",
                "Size: " + bounds.width() + "x" + bounds.height() + "x" + bounds.depth(),
                "Box volume: " + bounds.volume(),
                "Selected blocks: " + selectedCount));
    }

    private static Result selectionCount(ServerPlayer player, List<BlockPos> generated) {
        int air = 0;
        for (BlockPos pos : generated) {
            BlockState state = player.level().getBlockState(pos);
            if (state.isAir()) {
                air++;
            }
        }
        return Result.lines(List.of(
                "Measurement: Selection Count",
                "Selected blocks: " + generated.size(),
                "Air: " + air,
                "Solid: " + (generated.size() - air)));
    }

    private static Result pointDistance(List<BlockPos> points) {
        if (points.size() < 2) {
            return Result.lines(List.of("Measurement: Point Distance", "Add at least two advanced points."));
        }
        BlockPos first = points.get(0);
        BlockPos second = points.get(1);
        int dx = second.getX() - first.getX();
        int dy = second.getY() - first.getY();
        int dz = second.getZ() - first.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Result.lines(List.of(
                "Measurement: Point Distance",
                "Points: #1 to #2",
                "Delta: " + dx + ", " + dy + ", " + dz,
                "Distance: " + decimal(distance) + " blocks"));
    }

    private static Result pathLength(List<BlockPos> points) {
        if (points.size() < 2) {
            return Result.lines(List.of("Measurement: Path Length", "Add at least two advanced points."));
        }
        double length = 0.0D;
        for (int i = 1; i < points.size(); i++) {
            length += Math.sqrt(points.get(i - 1).distSqr(points.get(i)));
        }
        return Result.lines(List.of(
                "Measurement: Path Length",
                "Points: " + points.size(),
                "Path: " + decimal(length) + " blocks"));
    }

    private static Result bounds(Bounds bounds) {
        return Result.lines(List.of(
                "Measurement: Bounds",
                "Min: " + bounds.minX() + ", " + bounds.minY() + ", " + bounds.minZ(),
                "Max: " + bounds.maxX() + ", " + bounds.maxY() + ", " + bounds.maxZ()));
    }

    private static Result centerLines(Bounds bounds) {
        Center center = bounds.center();
        return new Result(
                List.of(
                        "Measurement: Center Lines",
                        "Center: " + center.format(),
                        "Half X/Y/Z: " + decimal(bounds.width() / 2.0D) + ", "
                                + decimal(bounds.height() / 2.0D) + ", "
                                + decimal(bounds.depth() / 2.0D)),
                List.of(new Marker("Center", center.x(), center.y(), center.z())),
                List.of());
    }

    private static String format(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static String compactBlocks(List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return "none";
        }
        int shown = Math.min(positions.size(), 4);
        List<String> parts = new ArrayList<>(shown + 1);
        for (int i = 0; i < shown; i++) {
            parts.add(format(positions.get(i)));
        }
        if (positions.size() > shown) {
            parts.add("+" + (positions.size() - shown) + " more");
        }
        return String.join("; ", parts);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public record Result(List<String> lines, List<Marker> markers, List<BlockPos> insertPoints) {
        public Result {
            lines = List.copyOf(lines);
            markers = List.copyOf(markers);
            insertPoints = insertPoints == null ? List.of() : insertPoints.stream().map(BlockPos::immutable).toList();
        }

        public static Result empty() {
            return new Result(List.of(), List.of(), List.of());
        }

        public static Result lines(List<String> lines) {
            return new Result(lines, List.of(), List.of());
        }
    }

    public record Marker(String label, double x, double y, double z) {
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static Bounds of(List<BlockPos> positions) {
            int minX = positions.stream().mapToInt(BlockPos::getX).min().orElse(0);
            int minY = positions.stream().mapToInt(BlockPos::getY).min().orElse(0);
            int minZ = positions.stream().mapToInt(BlockPos::getZ).min().orElse(0);
            int maxX = positions.stream().mapToInt(BlockPos::getX).max().orElse(0);
            int maxY = positions.stream().mapToInt(BlockPos::getY).max().orElse(0);
            int maxZ = positions.stream().mapToInt(BlockPos::getZ).max().orElse(0);
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        private int width() {
            return maxX - minX + 1;
        }

        private int height() {
            return maxY - minY + 1;
        }

        private int depth() {
            return maxZ - minZ + 1;
        }

        private int volume() {
            return width() * height() * depth();
        }

        private List<BlockPos> centerBlocks() {
            int[] xs = centerCoordinates(minX, width());
            int[] ys = centerCoordinates(minY, height());
            int[] zs = centerCoordinates(minZ, depth());
            List<BlockPos> positions = new ArrayList<>(xs.length * ys.length * zs.length);
            for (int x : xs) {
                for (int y : ys) {
                    for (int z : zs) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
            return positions;
        }

        private static int[] centerCoordinates(int min, int size) {
            int lower = min + size / 2 - 1;
            int upper = min + size / 2;
            return size % 2 == 0 ? new int[] { lower, upper } : new int[] { upper };
        }

        private Center center() {
            return new Center((minX + maxX) / 2.0D, (minY + maxY) / 2.0D, (minZ + maxZ) / 2.0D);
        }
    }

    private record Center(double x, double y, double z) {
        private String format() {
            return decimal(x) + ", " + decimal(y) + ", " + decimal(z);
        }
    }
}
