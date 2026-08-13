package com.abhil.buildtools.shape;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class ShapeGenerator {
    private ShapeGenerator() {
    }

    public static List<BlockPos> generate(Selection selection) {
        return generate(selection, CustomShapeMode.AUTO, StairDirectionOverride.POINT_ORDER);
    }

    public static List<BlockPos> generate(Selection selection, CustomShapeMode customMode, StairDirectionOverride stairDirection) {
        return generate(selection, customMode, stairDirection, Options.DEFAULT);
    }

    public static List<BlockPos> generate(Selection selection, CustomShapeMode customMode, StairDirectionOverride stairDirection, int roadWidth) {
        return generate(selection, customMode, stairDirection, new Options(roadWidth, ArchMode.OPEN, 50, ArchDirection.X, 50, false, false, ShapeDetailMode.PLAIN, RoofDirection.AUTO, 0, true, false, 3, false, true, roadWidth, true, BridgeSupportMode.POSTS, 4, 4, 1, TowerTopStyle.BATTLEMENTS));
    }

    public static List<BlockPos> generate(Selection selection, CustomShapeMode customMode, StairDirectionOverride stairDirection, Options options) {
        return generate(selection, customMode, stairDirection, options, false);
    }

    public static List<BlockPos> generate(Selection selection, CustomShapeMode customMode, StairDirectionOverride stairDirection, Options options, boolean advancedSelectionMode) {
        if (selection.shape() == SelectionShape.CUSTOM_SMART) {
            return customSmart(selection.points(), customMode);
        }
        if (usesAdvancedSmartMode(selection, customMode)) {
            return customSmart(selection.points(), customMode);
        }
        if (!selection.isComplete()) {
            return List.of();
        }
        if (selection.points().size() > 1 && selection.shape() == SelectionShape.LINE) {
            return polyline(selection.points());
        }
        BlockPos a = selection.first();
        BlockPos b = selection.second();
        return switch (selection.shape()) {
            case CUBOID -> cuboid(a, b, Filter.ALL);
            case WALLS -> walls(selection, a, b, advancedSelectionMode);
            case FLOOR -> cuboid(a, b, Filter.FLOOR);
            case CEILING -> cuboid(a, b, Filter.CEILING);
            case HOLLOW_BOX -> cuboid(a, b, Filter.SHELL);
            case LINE -> line(a, b);
            case CYLINDER -> cylinder(a, b);
            case SPHERE -> sphere(a, b, true, options.sphereHollow());
            case ELLIPSOID -> sphere(a, b, false, options.ellipsoidHollow());
            case ROAD -> road(selection.points().size() > 1 ? selection.points() : List.of(a, b), options.roadWidth());
            case TUNNEL -> tunnel(a, b);
            case ARCH -> arch(a, b, options.archMode(), options.archPeak(), options.archDirection());
            case DOME -> dome(a, b);
            case PYRAMID -> pyramid(a, b);
            case GABLE_ROOF -> gableRoof(a, b, options.roofDirection(), options.shapeDetailMode(), options.roofOverhang(), options.gableEndCaps());
            case HIP_ROOF -> hipRoof(a, b, options.roofDirection(), options.shapeDetailMode(), options.roofOverhang());
            case A_FRAME -> aFrame(a, b, options.roofDirection(), options.shapeDetailMode(), options.roofOverhang(), options.aFrameFloorFrame());
            case ROOM_FRAME -> roomFrame(a, b, options.shapeDetailMode(), options.roomStudSpacing(), options.roomFloorBeams(), options.roomCeilingJoists());
            case BRIDGE -> bridge(selection.points().size() > 1 ? selection.points() : List.of(a, b), a, b, options.bridgeWidth(), options.bridgeRails(), options.bridgeSupportMode(), options.bridgeSupportSpacing());
            case TOWER -> tower(a, b, options.shapeDetailMode(), options.towerFloorHeight(), options.towerWallThickness(), options.towerTopStyle());
            case CUSTOM_SMART -> customSmart(selection.points(), customMode);
            case STAIRS -> stairs(selection, stairDirection);
            case CURVE -> curve(selection.points().isEmpty() ? List.of(a, b) : selection.points(), options.curvePeak());
        };
    }

    public static List<BlockPos> cuboid(BlockPos a, BlockPos b, Filter filter) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        List<BlockPos> positions = new ArrayList<>();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (filter.accepts(x, y, z, minX, minY, minZ, maxX, maxY, maxZ)) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return positions;
    }

    private static boolean usesAdvancedSmartMode(Selection selection, CustomShapeMode customMode) {
        return customMode != CustomShapeMode.AUTO
                && selection.points().size() >= 3
                && selection.shape() != SelectionShape.WALLS
                && selection.shape() != SelectionShape.CURVE;
    }

    public static List<BlockPos> walls(Selection selection, BlockPos a, BlockPos b, boolean advancedSelectionMode) {
        List<BlockPos> points = selection.points();
        if (points.size() < 3) {
            return cuboid(a, b, advancedSelectionMode ? Filter.ALL : Filter.WALLS);
        }
        int minY = Math.min(a.getY(), b.getY());
        int maxY = Math.max(a.getY(), b.getY());
        Set<BlockPos> footprint = new LinkedHashSet<>();
        for (int i = 1; i < points.size(); i++) {
            footprint.addAll(horizontalLine(points.get(i - 1), points.get(i), minY));
        }
        if (points.size() >= 3) {
            footprint.addAll(horizontalLine(points.getLast(), points.getFirst(), minY));
        }

        Set<BlockPos> positions = new LinkedHashSet<>();
        for (BlockPos floorPos : footprint) {
            for (int y = minY; y <= maxY; y++) {
                positions.add(new BlockPos(floorPos.getX(), y, floorPos.getZ()));
            }
        }
        return List.copyOf(positions);
    }

    private static List<BlockPos> horizontalLine(BlockPos a, BlockPos b, int y) {
        return line(new BlockPos(a.getX(), y, a.getZ()), new BlockPos(b.getX(), y, b.getZ()));
    }

    public static List<BlockPos> line(BlockPos a, BlockPos b) {
        int dx = b.getX() - a.getX();
        int dy = b.getY() - a.getY();
        int dz = b.getZ() - a.getZ();
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        if (steps == 0) {
            return List.of(a.immutable());
        }

        Set<BlockPos> positions = new LinkedHashSet<>();
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            positions.add(new BlockPos(
                    Mth.floor(a.getX() + dx * t + 0.5D),
                    Mth.floor(a.getY() + dy * t + 0.5D),
                    Mth.floor(a.getZ() + dz * t + 0.5D)));
        }
        return List.copyOf(positions);
    }

    public static List<BlockPos> polyline(List<BlockPos> points) {
        if (points.isEmpty()) {
            return List.of();
        }
        if (points.size() == 1) {
            return List.of(points.getFirst().immutable());
        }

        Set<BlockPos> positions = new LinkedHashSet<>();
        for (int i = 1; i < points.size(); i++) {
            positions.addAll(line(points.get(i - 1), points.get(i)));
        }
        return List.copyOf(positions);
    }

    public static List<BlockPos> curve(List<BlockPos> points, int peakPercent) {
        if (points.isEmpty()) {
            return List.of();
        }
        if (points.size() == 1) {
            return List.of(points.getFirst().immutable());
        }
        if (points.size() == 2) {
            return line(points.getFirst(), points.getLast());
        }

        double bias = (Mth.clamp(peakPercent, 0, 100) - 50.0D) / 50.0D;
        Set<BlockPos> positions = new LinkedHashSet<>();
        BlockPos previousSample = points.getFirst().immutable();
        positions.add(previousSample);

        for (int i = 0; i < points.size() - 1; i++) {
            BlockPos p0 = points.get(Math.max(0, i - 1));
            BlockPos p1 = points.get(i);
            BlockPos p2 = points.get(i + 1);
            BlockPos p3 = points.get(Math.min(points.size() - 1, i + 2));
            double tangent1X = curveTangent(p0.getX(), p1.getX(), p2.getX(), bias);
            double tangent1Y = curveTangent(p0.getY(), p1.getY(), p2.getY(), bias);
            double tangent1Z = curveTangent(p0.getZ(), p1.getZ(), p2.getZ(), bias);
            double tangent2X = curveTangent(p1.getX(), p2.getX(), p3.getX(), bias);
            double tangent2Y = curveTangent(p1.getY(), p2.getY(), p3.getY(), bias);
            double tangent2Z = curveTangent(p1.getZ(), p2.getZ(), p3.getZ(), bias);
            int samples = curveSampleCount(p0, p1, p2, p3);

            for (int sample = 1; sample <= samples; sample++) {
                double t = (double) sample / (double) samples;
                double t2 = t * t;
                double t3 = t2 * t;
                double h00 = 2.0D * t3 - 3.0D * t2 + 1.0D;
                double h10 = t3 - 2.0D * t2 + t;
                double h01 = -2.0D * t3 + 3.0D * t2;
                double h11 = t3 - t2;
                BlockPos currentSample = new BlockPos(
                        Mth.floor(h00 * p1.getX() + h10 * tangent1X + h01 * p2.getX() + h11 * tangent2X + 0.5D),
                        Mth.floor(h00 * p1.getY() + h10 * tangent1Y + h01 * p2.getY() + h11 * tangent2Y + 0.5D),
                        Mth.floor(h00 * p1.getZ() + h10 * tangent1Z + h01 * p2.getZ() + h11 * tangent2Z + 0.5D));
                positions.addAll(line(previousSample, currentSample));
                previousSample = currentSample;
            }
        }
        return List.copyOf(positions);
    }

    private static double curveTangent(double previous, double current, double next, double bias) {
        return 0.5D * ((1.0D + bias) * (current - previous) + (1.0D - bias) * (next - current));
    }

    private static int curveSampleCount(BlockPos p0, BlockPos p1, BlockPos p2, BlockPos p3) {
        int span = Math.max(
                Math.max(chebyshevDistance(p0, p1), chebyshevDistance(p1, p2)),
                chebyshevDistance(p2, p3));
        return Math.max(8, span * 4);
    }

    private static int chebyshevDistance(BlockPos a, BlockPos b) {
        return Math.max(
                Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())),
                Math.abs(a.getZ() - b.getZ()));
    }

    public static List<BlockPos> road(List<BlockPos> points, int width) {
        if (points.isEmpty()) {
            return List.of();
        }
        int roadWidth = Math.max(1, width);
        if (points.size() == 1) {
            PathPoint point = new PathPoint(points.getFirst().getX(), points.getFirst().getY(), points.getFirst().getZ());
            Set<BlockPos> positions = new LinkedHashSet<>();
            stampRoadCrossSection(positions, point, point, point, roadWidth);
            return List.copyOf(positions);
        }
        List<PathPoint> center = smoothPath(points);
        Set<BlockPos> positions = new LinkedHashSet<>();
        if (center.size() == 1) {
            stampRoadCrossSection(positions, center.getFirst(), center.getFirst(), center.getFirst(), roadWidth);
            return List.copyOf(positions);
        }
        for (int i = 0; i < center.size(); i++) {
            PathPoint previous = center.get(Math.max(0, i - 1));
            PathPoint current = center.get(i);
            PathPoint next = center.get(Math.min(center.size() - 1, i + 1));
            stampRoadCrossSection(positions, previous, current, next, roadWidth);
        }
        return List.copyOf(positions);
    }

    private static List<PathPoint> smoothPath(List<BlockPos> points) {
        if (points.size() < 3) {
            return linearPath(points);
        }
        List<PathPoint> path = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            BlockPos p0 = points.get(Math.max(0, i - 1));
            BlockPos p1 = points.get(i);
            BlockPos p2 = points.get(i + 1);
            BlockPos p3 = points.get(Math.min(points.size() - 1, i + 2));
            int samples = Math.max(2, (int) Math.ceil(Math.sqrt(p1.distSqr(p2)) * 4.0D));
            for (int step = 0; step <= samples; step++) {
                if (i > 0 && step == 0) {
                    continue;
                }
                double t = (double) step / (double) samples;
                path.add(new PathPoint(
                        catmullRom(p0.getX(), p1.getX(), p2.getX(), p3.getX(), t),
                        catmullRom(p0.getY(), p1.getY(), p2.getY(), p3.getY(), t),
                        catmullRom(p0.getZ(), p1.getZ(), p2.getZ(), p3.getZ(), t)));
            }
        }
        return path;
    }

    private static List<PathPoint> linearPath(List<BlockPos> points) {
        List<PathPoint> path = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            BlockPos a = points.get(i);
            BlockPos b = points.get(i + 1);
            int samples = Math.max(1, (int) Math.ceil(Math.sqrt(a.distSqr(b)) * 4.0D));
            for (int step = 0; step <= samples; step++) {
                if (i > 0 && step == 0) {
                    continue;
                }
                double t = (double) step / (double) samples;
                path.add(new PathPoint(
                        Mth.lerp(t, a.getX(), b.getX()),
                        Mth.lerp(t, a.getY(), b.getY()),
                        Mth.lerp(t, a.getZ(), b.getZ())));
            }
        }
        return path;
    }

    private static double catmullRom(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5D * (2.0D * p1
                + (-p0 + p2) * t
                + (2.0D * p0 - 5.0D * p1 + 4.0D * p2 - p3) * t2
                + (-p0 + 3.0D * p1 - 3.0D * p2 + p3) * t3);
    }

    private static void stampRoadCrossSection(Set<BlockPos> positions, PathPoint previous, PathPoint current, PathPoint next, int width) {
        double dx = next.x() - previous.x();
        double dz = next.z() - previous.z();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.0001D) {
            dx = 0.0D;
            dz = 1.0D;
            length = 1.0D;
        }
        double perpendicularX = -dz / length;
        double perpendicularZ = dx / length;
        double centerOffset = (width - 1) / 2.0D;
        for (int offsetIndex = 0; offsetIndex < width; offsetIndex++) {
            double offset = offsetIndex - centerOffset;
            positions.add(new BlockPos(
                    roundedBlock(current.x() + perpendicularX * offset),
                    roundedBlock(current.y()),
                    roundedBlock(current.z() + perpendicularZ * offset)));
        }
    }

    private static int roundedBlock(double value) {
        return Mth.floor(value + 0.5D);
    }

    public static List<BlockPos> cylinder(BlockPos a, BlockPos b) {
        int minY = Math.min(a.getY(), b.getY());
        int maxY = Math.max(a.getY(), b.getY());
        double centerX = (a.getX() + b.getX()) / 2.0D;
        double centerZ = (a.getZ() + b.getZ()) / 2.0D;
        double radiusX = Math.max(0.5D, Math.abs(a.getX() - b.getX()) / 2.0D);
        double radiusZ = Math.max(0.5D, Math.abs(a.getZ() - b.getZ()) / 2.0D);
        int minX = Math.min(a.getX(), b.getX());
        int maxX = Math.max(a.getX(), b.getX());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxZ = Math.max(a.getZ(), b.getZ());
        List<BlockPos> positions = new ArrayList<>();

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    double nx = (x - centerX) / radiusX;
                    double nz = (z - centerZ) / radiusZ;
                    if (nx * nx + nz * nz <= 1.0D) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return positions;
    }

    public static List<BlockPos> sphere(BlockPos a, BlockPos b, boolean forceRound) {
        return sphere(a, b, forceRound, false);
    }

    public static List<BlockPos> sphere(BlockPos a, BlockPos b, boolean forceRound, boolean hollow) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        double centerX = (minX + maxX) / 2.0D;
        double centerY = (minY + maxY) / 2.0D;
        double centerZ = (minZ + maxZ) / 2.0D;
        double radiusX = Math.max(0.5D, (maxX - minX) / 2.0D);
        double radiusY = Math.max(0.5D, (maxY - minY) / 2.0D);
        double radiusZ = Math.max(0.5D, (maxZ - minZ) / 2.0D);
        if (forceRound) {
            double radius = Math.max(radiusX, Math.max(radiusY, radiusZ));
            radiusX = radius;
            radiusY = radius;
            radiusZ = radius;
        }
        List<BlockPos> positions = new ArrayList<>();
        Set<BlockPos> filled = hollow ? new LinkedHashSet<>() : null;

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    double nx = (x - centerX) / radiusX;
                    double ny = (y - centerY) / radiusY;
                    double nz = (z - centerZ) / radiusZ;
                    if (nx * nx + ny * ny + nz * nz <= 1.0D) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (hollow) {
                            filled.add(pos);
                        } else {
                            positions.add(pos);
                        }
                    }
                }
            }
        }
        return hollow ? surfaceOnly(filled) : positions;
    }

    public static List<BlockPos> tunnel(BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        double centerX = (minX + maxX) / 2.0D;
        double centerY = minY;
        double radiusX = Math.max(1.0D, (maxX - minX) / 2.0D);
        double radiusY = Math.max(1.0D, maxY - minY);
        List<BlockPos> positions = new ArrayList<>();

        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    double nx = (x - centerX) / radiusX;
                    double ny = (y - centerY) / radiusY;
                    double value = nx * nx + ny * ny;
                    if (y == minY || (ny >= 0.0D && value <= 1.08D && value >= 0.70D)) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return positions;
    }

    public static List<BlockPos> arch(BlockPos a, BlockPos b) {
        return arch(a, b, false, 50);
    }

    public static List<BlockPos> arch(BlockPos a, BlockPos b, boolean edgeWalls, int peakPercent) {
        return arch(a, b, edgeWalls ? ArchMode.EDGE_WALLS : ArchMode.OPEN, peakPercent, ArchDirection.X);
    }

    public static List<BlockPos> arch(BlockPos a, BlockPos b, ArchMode mode, int peakPercent, ArchDirection direction) {
        mode = mode == null ? ArchMode.OPEN : mode;
        direction = direction == null ? ArchDirection.X : direction;
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        int minArch = direction == ArchDirection.X ? minX : minZ;
        int maxArch = direction == ArchDirection.X ? maxX : maxZ;
        int minDepth = direction == ArchDirection.X ? minZ : minX;
        int maxDepth = direction == ArchDirection.X ? maxZ : maxX;
        double peak = Mth.clamp(peakPercent, 0, 100) / 100.0D;
        double centerArch = Mth.lerp(peak, minArch, maxArch);
        double leftRadius = Math.max(1.0D, centerArch - minArch);
        double rightRadius = Math.max(1.0D, maxArch - centerArch);
        double radiusY = Math.max(1.0D, maxY - minY);
        List<BlockPos> positions = new ArrayList<>();

        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    int archCoord = direction == ArchDirection.X ? x : z;
                    int depthCoord = direction == ArchDirection.X ? z : x;
                    double radius = archCoord <= centerArch ? leftRadius : rightRadius;
                    double nx = (archCoord - centerArch) / radius;
                    double ny = (y - minY) / radiusY;
                    double value = nx * nx + ny * ny;
                    boolean archShell = ny >= 0.0D && value <= 1.08D && value >= 0.70D;
                    boolean edgeWall = mode == ArchMode.EDGE_WALLS && (archCoord == minArch || archCoord == maxArch);
                    boolean endWall = mode == ArchMode.WALLS && (depthCoord == minDepth || depthCoord == maxDepth) && ny >= 0.0D && value <= 1.08D;
                    if (edgeWall || endWall || archShell) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return positions;
    }

    private static List<BlockPos> surfaceOnly(Set<BlockPos> filled) {
        List<BlockPos> surface = new ArrayList<>();
        for (BlockPos pos : filled) {
            for (Direction direction : Direction.values()) {
                if (!filled.contains(pos.relative(direction))) {
                    surface.add(pos);
                    break;
                }
            }
        }
        return surface;
    }

    public static List<BlockPos> dome(BlockPos a, BlockPos b) {
        List<BlockPos> shell = new ArrayList<>();
        Set<BlockPos> all = new LinkedHashSet<>();
        int minX = Math.min(a.getX(), b.getX());
        int baseY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int topY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        double centerX = (minX + maxX) / 2.0D;
        double centerY = baseY;
        double centerZ = (minZ + maxZ) / 2.0D;
        double radiusX = Math.max(0.5D, (maxX - minX + 1) / 2.0D);
        double radiusY = Math.max(0.5D, topY - baseY + 0.5D);
        double radiusZ = Math.max(0.5D, (maxZ - minZ + 1) / 2.0D);

        for (int y = baseY - Mth.ceil(radiusY); y <= topY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    double nx = (x - centerX) / radiusX;
                    double ny = (y - centerY) / radiusY;
                    double nz = (z - centerZ) / radiusZ;
                    if (nx * nx + ny * ny + nz * nz <= 1.0D) {
                        all.add(new BlockPos(x, y, z));
                    }
                }
            }
        }

        for (BlockPos pos : all) {
            if (pos.getY() < baseY) {
                continue;
            }
            boolean edge = false;
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                if (!all.contains(pos.relative(direction))) {
                    edge = true;
                    break;
                }
            }
            if (edge) {
                shell.add(pos);
            }
        }
        return shell;
    }

    public static List<BlockPos> pyramid(BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        Set<BlockPos> positions = new LinkedHashSet<>();

        for (int y = minY; y <= maxY; y++) {
            int inset = y - minY;
            int layerMinX = minX + inset;
            int layerMaxX = maxX - inset;
            int layerMinZ = minZ + inset;
            int layerMaxZ = maxZ - inset;
            if (layerMinX > layerMaxX || layerMinZ > layerMaxZ) {
                break;
            }
            for (int z = layerMinZ; z <= layerMaxZ; z++) {
                for (int x = layerMinX; x <= layerMaxX; x++) {
                    if (x == layerMinX || x == layerMaxX || z == layerMinZ || z == layerMaxZ
                            || layerMaxX - layerMinX <= 1 || layerMaxZ - layerMinZ <= 1) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
            if (layerMaxX - layerMinX <= 1 && layerMaxZ - layerMinZ <= 1) {
                break;
            }
        }
        return List.copyOf(positions);
    }

    public static List<BlockPos> gableRoof(BlockPos a, BlockPos b, RoofDirection direction, ShapeDetailMode detailMode, int overhang, boolean endCaps) {
        Bounds bounds = Bounds.of(a, b).expandHorizontal(overhang);
        Direction.Axis ridgeAxis = roofAxis(bounds, direction);
        Set<BlockPos> positions = new LinkedHashSet<>();
        int slopeMin = ridgeAxis == Direction.Axis.X ? bounds.minZ() : bounds.minX();
        int slopeMax = ridgeAxis == Direction.Axis.X ? bounds.maxZ() : bounds.maxX();
        int halfSpan = (slopeMax - slopeMin) / 2;
        int roofHeight = Math.min(bounds.height() - 1, halfSpan);
        for (int yOffset = 0; yOffset <= roofHeight; yOffset++) {
            int y = bounds.minY() + yOffset;
            int left = slopeMin + yOffset;
            int right = slopeMax - yOffset;
            addRoofLine(positions, bounds, ridgeAxis, left, y);
            if (right != left) {
                addRoofLine(positions, bounds, ridgeAxis, right, y);
            }
            if (endCaps && detailMode == ShapeDetailMode.DETAILED) {
                addRoofEndFill(positions, bounds, ridgeAxis, left, right, y);
            }
        }
        return List.copyOf(positions);
    }

    public static List<BlockPos> hipRoof(BlockPos a, BlockPos b, RoofDirection direction, ShapeDetailMode detailMode, int overhang) {
        Bounds bounds = Bounds.of(a, b).expandHorizontal(overhang);
        Set<BlockPos> positions = new LinkedHashSet<>();
        Direction.Axis axis = roofAxis(bounds, direction);
        int maxInset = Math.min(Math.min(bounds.widthX(), bounds.widthZ()) / 2, bounds.height() - 1);
        for (int inset = 0; inset <= maxInset; inset++) {
            int y = bounds.minY() + inset;
            addRectangleRing(positions, bounds.minX() + inset, bounds.maxX() - inset, y, bounds.minZ() + inset, bounds.maxZ() - inset);
            if (detailMode == ShapeDetailMode.DETAILED && inset % 3 == 0) {
                if (axis == Direction.Axis.X) {
                    addLineX(positions, bounds.minX() + inset, bounds.maxX() - inset, y, Mth.floor((bounds.minZ() + bounds.maxZ()) / 2.0D));
                } else {
                    addLineZ(positions, Mth.floor((bounds.minX() + bounds.maxX()) / 2.0D), y, bounds.minZ() + inset, bounds.maxZ() - inset);
                }
            }
        }
        return List.copyOf(positions);
    }

    public static List<BlockPos> aFrame(BlockPos a, BlockPos b, RoofDirection direction, ShapeDetailMode detailMode, int overhang, boolean floorFrame) {
        Bounds bounds = Bounds.of(a, b).expandHorizontal(overhang);
        Direction.Axis ridgeAxis = roofAxis(bounds, direction);
        Set<BlockPos> positions = new LinkedHashSet<>();
        int slopeMin = ridgeAxis == Direction.Axis.X ? bounds.minZ() : bounds.minX();
        int slopeMax = ridgeAxis == Direction.Axis.X ? bounds.maxZ() : bounds.maxX();
        int halfSpan = (slopeMax - slopeMin) / 2;
        int roofHeight = Math.min(bounds.height() - 1, halfSpan);
        for (int yOffset = 0; yOffset <= roofHeight; yOffset++) {
            int y = bounds.minY() + yOffset;
            int left = slopeMin + yOffset;
            int right = slopeMax - yOffset;
            addRoofLine(positions, bounds, ridgeAxis, left, y);
            if (right != left) {
                addRoofLine(positions, bounds, ridgeAxis, right, y);
            }
        }
        if (detailMode == ShapeDetailMode.DETAILED) {
            for (int yOffset = 0; yOffset <= roofHeight; yOffset++) {
                int y = bounds.minY() + yOffset;
                addRoofEndFill(positions, bounds, ridgeAxis, slopeMin + yOffset, slopeMax - yOffset, y);
            }
            int step = 3;
            int alongMin = ridgeAxis == Direction.Axis.X ? bounds.minX() : bounds.minZ();
            int alongMax = ridgeAxis == Direction.Axis.X ? bounds.maxX() : bounds.maxZ();
            for (int along = alongMin; along <= alongMax; along += step) {
                addAFrameRafter(positions, bounds, ridgeAxis, along, roofHeight);
            }
            addAFrameRafter(positions, bounds, ridgeAxis, alongMax, roofHeight);
        }
        if (floorFrame) {
            addRectangleRing(positions, bounds.minX(), bounds.maxX(), bounds.minY(), bounds.minZ(), bounds.maxZ());
        }
        return List.copyOf(positions);
    }

    public static List<BlockPos> roomFrame(BlockPos a, BlockPos b, ShapeDetailMode detailMode, int studSpacing, boolean floorBeams, boolean ceilingJoists) {
        Bounds bounds = Bounds.of(a, b);
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            positions.add(new BlockPos(bounds.minX(), y, bounds.minZ()));
            positions.add(new BlockPos(bounds.minX(), y, bounds.maxZ()));
            positions.add(new BlockPos(bounds.maxX(), y, bounds.minZ()));
            positions.add(new BlockPos(bounds.maxX(), y, bounds.maxZ()));
        }
        addRectangleRing(positions, bounds.minX(), bounds.maxX(), bounds.minY(), bounds.minZ(), bounds.maxZ());
        addRectangleRing(positions, bounds.minX(), bounds.maxX(), bounds.maxY(), bounds.minZ(), bounds.maxZ());
        if (floorBeams) {
            addRectangleCross(positions, bounds.minX(), bounds.maxX(), bounds.minY(), bounds.minZ(), bounds.maxZ());
        }
        if (detailMode == ShapeDetailMode.DETAILED) {
            addStuds(positions, bounds, studSpacing);
        }
        if (ceilingJoists) {
            int spacing = Math.max(1, studSpacing);
            for (int x = bounds.minX(); x <= bounds.maxX(); x += spacing) {
                addLineZ(positions, x, bounds.maxY(), bounds.minZ(), bounds.maxZ());
            }
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z += spacing) {
                addLineX(positions, bounds.minX(), bounds.maxX(), bounds.maxY(), z);
            }
        }
        return List.copyOf(positions);
    }

    public static List<BlockPos> bridge(List<BlockPos> points, BlockPos a, BlockPos b, int width, boolean rails, BridgeSupportMode supportMode, int supportSpacing) {
        if (points.isEmpty()) {
            return List.of();
        }
        Bounds bounds = Bounds.of(a, b);
        List<PathPoint> center = points.size() < 3 ? linearPath(points) : smoothPath(points);
        Set<BlockPos> positions = new LinkedHashSet<>();
        int bridgeWidth = Math.max(1, width);
        for (int i = 0; i < center.size(); i++) {
            PathPoint previous = center.get(Math.max(0, i - 1));
            PathPoint current = center.get(i);
            PathPoint next = center.get(Math.min(center.size() - 1, i + 1));
            stampBridgeCrossSection(positions, previous, current, next, bridgeWidth, bounds, rails, supportMode, supportSpacing, i);
        }
        return List.copyOf(positions);
    }

    public static List<BlockPos> tower(BlockPos a, BlockPos b, ShapeDetailMode detailMode, int floorHeight, int wallThickness, TowerTopStyle topStyle) {
        Bounds bounds = Bounds.of(a, b);
        Set<BlockPos> positions = new LinkedHashSet<>();
        double centerX = (bounds.minX() + bounds.maxX()) / 2.0D;
        double centerZ = (bounds.minZ() + bounds.maxZ()) / 2.0D;
        double radiusX = Math.max(0.5D, (bounds.maxX() - bounds.minX()) / 2.0D);
        double radiusZ = Math.max(0.5D, (bounds.maxZ() - bounds.minZ()) / 2.0D);
        double innerScale = Math.max(0.0D, 1.0D - (double) Math.max(1, wallThickness) / Math.max(1.0D, Math.min(radiusX, radiusZ)));
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    double nx = (x - centerX) / radiusX;
                    double nz = (z - centerZ) / radiusZ;
                    double value = nx * nx + nz * nz;
                    if (value <= 1.0D && value >= innerScale * innerScale) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        if (detailMode == ShapeDetailMode.DETAILED) {
            int spacing = Math.max(1, floorHeight);
            for (int y = bounds.minY() + spacing; y < bounds.maxY(); y += spacing) {
                addTowerFloor(positions, bounds, centerX, centerZ, radiusX, radiusZ, y);
            }
        }
        if (topStyle == TowerTopStyle.FLAT) {
            addTowerFloor(positions, bounds, centerX, centerZ, radiusX, radiusZ, bounds.maxY());
        } else if (topStyle == TowerTopStyle.BATTLEMENTS) {
            addTowerCrenellations(positions, bounds, centerX, centerZ, radiusX, radiusZ);
        } else {
            addTowerRoof(positions, bounds, centerX, centerZ, radiusX, radiusZ);
        }
        return List.copyOf(positions);
    }

    private static Direction.Axis roofAxis(Bounds bounds, RoofDirection direction) {
        if (direction == RoofDirection.X) {
            return Direction.Axis.X;
        }
        if (direction == RoofDirection.Z) {
            return Direction.Axis.Z;
        }
        return bounds.widthX() >= bounds.widthZ() ? Direction.Axis.X : Direction.Axis.Z;
    }

    private static void addRoofLine(Set<BlockPos> positions, Bounds bounds, Direction.Axis ridgeAxis, int slopeCoord, int y) {
        if (ridgeAxis == Direction.Axis.X) {
            addLineX(positions, bounds.minX(), bounds.maxX(), y, slopeCoord);
        } else {
            addLineZ(positions, slopeCoord, y, bounds.minZ(), bounds.maxZ());
        }
    }

    private static void addRoofEndFill(Set<BlockPos> positions, Bounds bounds, Direction.Axis ridgeAxis, int left, int right, int y) {
        if (ridgeAxis == Direction.Axis.X) {
            for (int z = left; z <= right; z++) {
                positions.add(new BlockPos(bounds.minX(), y, z));
                positions.add(new BlockPos(bounds.maxX(), y, z));
            }
        } else {
            for (int x = left; x <= right; x++) {
                positions.add(new BlockPos(x, y, bounds.minZ()));
                positions.add(new BlockPos(x, y, bounds.maxZ()));
            }
        }
    }

    private static void addAFrameRafter(Set<BlockPos> positions, Bounds bounds, Direction.Axis ridgeAxis, int along, int roofHeight) {
        int slopeMin = ridgeAxis == Direction.Axis.X ? bounds.minZ() : bounds.minX();
        int slopeMax = ridgeAxis == Direction.Axis.X ? bounds.maxZ() : bounds.maxX();
        for (int yOffset = 0; yOffset <= roofHeight; yOffset++) {
            int y = bounds.minY() + yOffset;
            int left = slopeMin + yOffset;
            int right = slopeMax - yOffset;
            if (ridgeAxis == Direction.Axis.X) {
                positions.add(new BlockPos(along, y, left));
                positions.add(new BlockPos(along, y, right));
            } else {
                positions.add(new BlockPos(left, y, along));
                positions.add(new BlockPos(right, y, along));
            }
        }
    }

    private static void addRectangleRing(Set<BlockPos> positions, int minX, int maxX, int y, int minZ, int maxZ) {
        if (minX > maxX || minZ > maxZ) {
            return;
        }
        addLineX(positions, minX, maxX, y, minZ);
        addLineX(positions, minX, maxX, y, maxZ);
        addLineZ(positions, minX, y, minZ, maxZ);
        addLineZ(positions, maxX, y, minZ, maxZ);
    }

    private static void addRectangleCross(Set<BlockPos> positions, int minX, int maxX, int y, int minZ, int maxZ) {
        int centerX = Mth.floor((minX + maxX) / 2.0D);
        int centerZ = Mth.floor((minZ + maxZ) / 2.0D);
        addLineX(positions, minX, maxX, y, centerZ);
        addLineZ(positions, centerX, y, minZ, maxZ);
    }

    private static void addLineX(Set<BlockPos> positions, int minX, int maxX, int y, int z) {
        for (int x = minX; x <= maxX; x++) {
            positions.add(new BlockPos(x, y, z));
        }
    }

    private static void addLineZ(Set<BlockPos> positions, int x, int y, int minZ, int maxZ) {
        for (int z = minZ; z <= maxZ; z++) {
            positions.add(new BlockPos(x, y, z));
        }
    }

    private static void addStuds(Set<BlockPos> positions, Bounds bounds, int spacing) {
        int step = Math.max(1, spacing);
        for (int x = bounds.minX(); x <= bounds.maxX(); x += step) {
            addVertical(positions, x, bounds.minY(), bounds.maxY(), bounds.minZ());
            addVertical(positions, x, bounds.minY(), bounds.maxY(), bounds.maxZ());
        }
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z += step) {
            addVertical(positions, bounds.minX(), bounds.minY(), bounds.maxY(), z);
            addVertical(positions, bounds.maxX(), bounds.minY(), bounds.maxY(), z);
        }
    }

    private static void addVertical(Set<BlockPos> positions, int x, int minY, int maxY, int z) {
        for (int y = minY; y <= maxY; y++) {
            positions.add(new BlockPos(x, y, z));
        }
    }

    private static void stampBridgeCrossSection(Set<BlockPos> positions, PathPoint previous, PathPoint current, PathPoint next, int width, Bounds bounds, boolean rails, BridgeSupportMode supportMode, int supportSpacing, int index) {
        double dx = next.x() - previous.x();
        double dz = next.z() - previous.z();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.0001D) {
            dx = 0.0D;
            dz = 1.0D;
            length = 1.0D;
        }
        double perpendicularX = -dz / length;
        double perpendicularZ = dx / length;
        double centerOffset = (width - 1) / 2.0D;
        int y = Mth.clamp(roundedBlock(current.y()), bounds.minY(), bounds.maxY());
        for (int offsetIndex = 0; offsetIndex < width; offsetIndex++) {
            double offset = offsetIndex - centerOffset;
            int x = Mth.clamp(roundedBlock(current.x() + perpendicularX * offset), bounds.minX(), bounds.maxX());
            int z = Mth.clamp(roundedBlock(current.z() + perpendicularZ * offset), bounds.minZ(), bounds.maxZ());
            positions.add(new BlockPos(x, y, z));
            boolean edge = offsetIndex == 0 || offsetIndex == width - 1;
            if (rails && edge && y + 1 <= bounds.maxY()) {
                positions.add(new BlockPos(x, y + 1, z));
            }
            if (edge && supportMode != BridgeSupportMode.NONE && index % Math.max(1, supportSpacing) == 0) {
                if (supportMode == BridgeSupportMode.POSTS) {
                    for (int supportY = bounds.minY(); supportY < y; supportY++) {
                        positions.add(new BlockPos(x, supportY, z));
                    }
                } else if (supportMode == BridgeSupportMode.ARCHES) {
                    int span = Math.max(1, width - 1);
                    int archRise = Math.max(1, Math.min(3, y - bounds.minY()));
                    int archY = Math.max(bounds.minY(), y - archRise + Mth.floor(Math.sin((double) offsetIndex / (double) span * Math.PI) * archRise));
                    for (int supportY = archY; supportY < y; supportY++) {
                        positions.add(new BlockPos(x, supportY, z));
                    }
                }
            }
        }
    }

    private static void addTowerFloor(Set<BlockPos> positions, Bounds bounds, double centerX, double centerZ, double radiusX, double radiusZ, int y) {
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                double nx = (x - centerX) / radiusX;
                double nz = (z - centerZ) / radiusZ;
                if (nx * nx + nz * nz <= 0.82D) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
    }

    private static void addTowerCrenellations(Set<BlockPos> positions, Bounds bounds, double centerX, double centerZ, double radiusX, double radiusZ) {
        int index = 0;
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                double nx = (x - centerX) / radiusX;
                double nz = (z - centerZ) / radiusZ;
                double value = nx * nx + nz * nz;
                if (value <= 1.0D && value >= 0.68D && index++ % 2 == 0) {
                    positions.add(new BlockPos(x, bounds.maxY(), z));
                }
            }
        }
    }

    private static void addTowerRoof(Set<BlockPos> positions, Bounds bounds, double centerX, double centerZ, double radiusX, double radiusZ) {
        int roofHeight = Math.min(4, bounds.height());
        for (int offset = 0; offset < roofHeight; offset++) {
            int y = bounds.maxY() - roofHeight + 1 + offset;
            double scale = Math.max(0.15D, 1.0D - (double) offset / (double) Math.max(1, roofHeight));
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    double nx = (x - centerX) / Math.max(0.5D, radiusX * scale);
                    double nz = (z - centerZ) / Math.max(0.5D, radiusZ * scale);
                    double value = nx * nx + nz * nz;
                    if (value <= 1.0D && value >= 0.65D) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    public static List<BlockPos> customSmart(List<BlockPos> points, CustomShapeMode mode) {
        if (points.isEmpty()) {
            return List.of();
        }
        if (points.size() == 1) {
            return List.of(points.getFirst().immutable());
        }
        CustomShapeMode resolved = mode == CustomShapeMode.AUTO ? inferCustomMode(points) : mode;
        return switch (resolved) {
            case AUTO, LINE -> polyline(points);
            case POLYGON_FILL -> polygonFill(points);
            case SURFACE -> customSurface(points);
            case VOLUME -> convexVolume(points);
        };
    }

    public static List<BlockPos> stairs(Selection selection, StairDirectionOverride override) {
        if (!selection.isComplete()) {
            return List.of();
        }
        BlockPos a = selection.first();
        BlockPos b = selection.second();
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        Direction direction = stairDirection(selection, override);
        int length = direction.getAxis() == Direction.Axis.X ? maxX - minX + 1 : maxZ - minZ + 1;
        int height = maxY - minY + 1;
        Set<BlockPos> positions = new LinkedHashSet<>();

        for (int along = 0; along < length; along++) {
            int topY = minY + Mth.floor((double) along * (double) (height - 1) / (double) Math.max(1, length - 1));
            if (direction == Direction.EAST || direction == Direction.WEST) {
                int x = direction == Direction.EAST ? minX + along : maxX - along;
                for (int z = minZ; z <= maxZ; z++) {
                    positions.add(new BlockPos(x, topY, z));
                }
            } else {
                int z = direction == Direction.SOUTH ? minZ + along : maxZ - along;
                for (int x = minX; x <= maxX; x++) {
                    positions.add(new BlockPos(x, topY, z));
                }
            }
        }
        return List.copyOf(positions);
    }

    public static Direction stairDirection(Selection selection, StairDirectionOverride override) {
        if (override.direction() != null) {
            return override.direction();
        }
        List<BlockPos> points = selection.points();
        if (points.size() >= 2) {
            Direction fromPoints = horizontalDirection(points.getFirst(), points.getLast());
            if (fromPoints != null) {
                return fromPoints;
            }
        }
        if (!selection.isComplete()) {
            return Direction.NORTH;
        }
        int dx = selection.second().getX() - selection.first().getX();
        int dz = selection.second().getZ() - selection.first().getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static CustomShapeMode inferCustomMode(List<BlockPos> points) {
        if (points.size() <= 2) {
            return CustomShapeMode.LINE;
        }
        return isAxisCoplanar(points) ? CustomShapeMode.POLYGON_FILL : CustomShapeMode.VOLUME;
    }

    private static boolean isAxisCoplanar(List<BlockPos> points) {
        int x = points.getFirst().getX();
        int y = points.getFirst().getY();
        int z = points.getFirst().getZ();
        boolean sameX = true;
        boolean sameY = true;
        boolean sameZ = true;
        for (BlockPos point : points) {
            sameX &= point.getX() == x;
            sameY &= point.getY() == y;
            sameZ &= point.getZ() == z;
        }
        return sameX || sameY || sameZ;
    }

    private static List<BlockPos> polygonFill(List<BlockPos> points) {
        if (!isAxisCoplanar(points)) {
            return polyline(points);
        }
        Set<BlockPos> positions = new LinkedHashSet<>(polyline(closed(points)));
        int minX = points.stream().mapToInt(BlockPos::getX).min().orElse(0);
        int minY = points.stream().mapToInt(BlockPos::getY).min().orElse(0);
        int minZ = points.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        int maxX = points.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int maxY = points.stream().mapToInt(BlockPos::getY).max().orElse(0);
        int maxZ = points.stream().mapToInt(BlockPos::getZ).max().orElse(0);
        if (minX == maxX) {
            fill2D(points, positions, Plane.X, minX, minY, maxY, minZ, maxZ);
        } else if (minY == maxY) {
            fill2D(points, positions, Plane.Y, minY, minX, maxX, minZ, maxZ);
        } else {
            fill2D(points, positions, Plane.Z, minZ, minX, maxX, minY, maxY);
        }
        return List.copyOf(positions);
    }

    private static void fill2D(List<BlockPos> points, Set<BlockPos> positions, Plane plane, int fixed, int minA, int maxA, int minB, int maxB) {
        for (int a = minA; a <= maxA; a++) {
            for (int b = minB; b <= maxB; b++) {
                if (insidePolygon(points, plane, a + 0.5D, b + 0.5D)) {
                    positions.add(plane.pos(fixed, a, b));
                }
            }
        }
    }

    private static boolean insidePolygon(List<BlockPos> points, Plane plane, double a, double b) {
        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            double ai = plane.a(points.get(i));
            double bi = plane.b(points.get(i));
            double aj = plane.a(points.get(j));
            double bj = plane.b(points.get(j));
            if ((bi > b) != (bj > b) && a < (aj - ai) * (b - bi) / (bj - bi) + ai) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static List<BlockPos> customSurface(List<BlockPos> points) {
        if (isAxisCoplanar(points)) {
            return polygonFill(points);
        }
        Set<BlockPos> volume = new LinkedHashSet<>(convexVolume(points));
        Set<BlockPos> surface = new LinkedHashSet<>();
        for (BlockPos pos : volume) {
            for (Direction direction : Direction.values()) {
                if (!volume.contains(pos.relative(direction))) {
                    surface.add(pos);
                    break;
                }
            }
        }
        return List.copyOf(surface);
    }

    private static List<BlockPos> convexVolume(List<BlockPos> points) {
        if (points.size() < 4) {
            return polygonFill(points);
        }
        List<HullPlane> planes = hullPlanes(points);
        if (planes.isEmpty()) {
            return cuboid(boundsMin(points), boundsMax(points), Filter.ALL);
        }
        int minX = points.stream().mapToInt(BlockPos::getX).min().orElse(0);
        int minY = points.stream().mapToInt(BlockPos::getY).min().orElse(0);
        int minZ = points.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        int maxX = points.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int maxY = points.stream().mapToInt(BlockPos::getY).max().orElse(0);
        int maxZ = points.stream().mapToInt(BlockPos::getZ).max().orElse(0);
        List<BlockPos> positions = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (insideHull(planes, x + 0.5D, y + 0.5D, z + 0.5D)) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        positions.addAll(points);
        return List.copyOf(new LinkedHashSet<>(positions));
    }

    private static List<HullPlane> hullPlanes(List<BlockPos> points) {
        List<HullPlane> planes = new ArrayList<>();
        for (int i = 0; i < points.size() - 2; i++) {
            for (int j = i + 1; j < points.size() - 1; j++) {
                for (int k = j + 1; k < points.size(); k++) {
                    HullPlane plane = HullPlane.of(points.get(i), points.get(j), points.get(k), points);
                    if (plane != null && planes.stream().noneMatch(plane::samePlane)) {
                        planes.add(plane);
                    }
                }
            }
        }
        return planes;
    }

    private static boolean insideHull(List<HullPlane> planes, double x, double y, double z) {
        for (HullPlane plane : planes) {
            if (!plane.inside(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    private static List<BlockPos> closed(List<BlockPos> points) {
        List<BlockPos> closed = new ArrayList<>(points);
        if (!closed.getFirst().equals(closed.getLast())) {
            closed.add(closed.getFirst());
        }
        return closed;
    }

    private static BlockPos boundsMin(List<BlockPos> points) {
        return new BlockPos(
                points.stream().mapToInt(BlockPos::getX).min().orElse(0),
                points.stream().mapToInt(BlockPos::getY).min().orElse(0),
                points.stream().mapToInt(BlockPos::getZ).min().orElse(0));
    }

    private static BlockPos boundsMax(List<BlockPos> points) {
        return new BlockPos(
                points.stream().mapToInt(BlockPos::getX).max().orElse(0),
                points.stream().mapToInt(BlockPos::getY).max().orElse(0),
                points.stream().mapToInt(BlockPos::getZ).max().orElse(0));
    }

    private static Direction horizontalDirection(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx == 0 && dz == 0) {
            return null;
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private enum Plane {
        X {
            @Override
            double a(BlockPos pos) {
                return pos.getY();
            }

            @Override
            double b(BlockPos pos) {
                return pos.getZ();
            }

            @Override
            BlockPos pos(int fixed, int a, int b) {
                return new BlockPos(fixed, a, b);
            }
        },
        Y {
            @Override
            double a(BlockPos pos) {
                return pos.getX();
            }

            @Override
            double b(BlockPos pos) {
                return pos.getZ();
            }

            @Override
            BlockPos pos(int fixed, int a, int b) {
                return new BlockPos(a, fixed, b);
            }
        },
        Z {
            @Override
            double a(BlockPos pos) {
                return pos.getX();
            }

            @Override
            double b(BlockPos pos) {
                return pos.getY();
            }

            @Override
            BlockPos pos(int fixed, int a, int b) {
                return new BlockPos(a, b, fixed);
            }
        };

        abstract double a(BlockPos pos);

        abstract double b(BlockPos pos);

        abstract BlockPos pos(int fixed, int a, int b);
    }

    private record HullPlane(double nx, double ny, double nz, double d, int side) {
        private static HullPlane of(BlockPos a, BlockPos b, BlockPos c, List<BlockPos> points) {
            double ux = b.getX() - a.getX();
            double uy = b.getY() - a.getY();
            double uz = b.getZ() - a.getZ();
            double vx = c.getX() - a.getX();
            double vy = c.getY() - a.getY();
            double vz = c.getZ() - a.getZ();
            double nx = uy * vz - uz * vy;
            double ny = uz * vx - ux * vz;
            double nz = ux * vy - uy * vx;
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length <= 0.0001D) {
                return null;
            }
            nx /= length;
            ny /= length;
            nz /= length;
            double d = -(nx * a.getX() + ny * a.getY() + nz * a.getZ());
            boolean positive = false;
            boolean negative = false;
            for (BlockPos point : points) {
                double signed = nx * point.getX() + ny * point.getY() + nz * point.getZ() + d;
                positive |= signed > 0.001D;
                negative |= signed < -0.001D;
            }
            if (positive && negative) {
                return null;
            }
            return new HullPlane(nx, ny, nz, d, positive ? 1 : -1);
        }

        private boolean inside(double x, double y, double z) {
            double signed = nx * x + ny * y + nz * z + d;
            return side >= 0 ? signed >= -0.501D : signed <= 0.501D;
        }

        private boolean samePlane(HullPlane other) {
            return Math.abs(Math.abs(nx * other.nx + ny * other.ny + nz * other.nz) - 1.0D) < 0.001D
                    && Math.abs(Math.abs(d) - Math.abs(other.d)) < 0.001D;
        }
    }

    private record PathPoint(double x, double y, double z) {
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static Bounds of(BlockPos a, BlockPos b) {
            return new Bounds(
                    Math.min(a.getX(), b.getX()),
                    Math.min(a.getY(), b.getY()),
                    Math.min(a.getZ(), b.getZ()),
                    Math.max(a.getX(), b.getX()),
                    Math.max(a.getY(), b.getY()),
                    Math.max(a.getZ(), b.getZ()));
        }

        private int widthX() {
            return maxX - minX + 1;
        }

        private int widthZ() {
            return maxZ - minZ + 1;
        }

        private int height() {
            return maxY - minY + 1;
        }

        private Bounds expandHorizontal(int amount) {
            int distance = Math.max(0, amount);
            return new Bounds(minX - distance, minY, minZ - distance, maxX + distance, maxY, maxZ + distance);
        }
    }

    public record Options(
            int roadWidth,
            ArchMode archMode,
            int archPeak,
            ArchDirection archDirection,
            int curvePeak,
            boolean sphereHollow,
            boolean ellipsoidHollow,
            ShapeDetailMode shapeDetailMode,
            RoofDirection roofDirection,
            int roofOverhang,
            boolean gableEndCaps,
            boolean aFrameFloorFrame,
            int roomStudSpacing,
            boolean roomFloorBeams,
            boolean roomCeilingJoists,
            int bridgeWidth,
            boolean bridgeRails,
            BridgeSupportMode bridgeSupportMode,
            int bridgeSupportSpacing,
            int towerFloorHeight,
            int towerWallThickness,
            TowerTopStyle towerTopStyle) {
        public static final Options DEFAULT = new Options(3, ArchMode.OPEN, 50, ArchDirection.X, 50, false, false, ShapeDetailMode.PLAIN, RoofDirection.AUTO, 0, true, false, 3, false, true, 3, true, BridgeSupportMode.POSTS, 4, 4, 1, TowerTopStyle.BATTLEMENTS);

        public Options {
            roadWidth = Math.max(1, roadWidth);
            archMode = archMode == null ? ArchMode.OPEN : archMode;
            archPeak = Mth.clamp(archPeak, 0, 100);
            archDirection = archDirection == null ? ArchDirection.X : archDirection;
            curvePeak = Mth.clamp(curvePeak, 0, 100);
            shapeDetailMode = shapeDetailMode == null ? ShapeDetailMode.PLAIN : shapeDetailMode;
            roofDirection = roofDirection == null ? RoofDirection.AUTO : roofDirection;
            roofOverhang = Mth.clamp(roofOverhang, 0, 3);
            roomStudSpacing = Mth.clamp(roomStudSpacing, 2, 6);
            bridgeWidth = Math.max(1, bridgeWidth);
            bridgeSupportMode = bridgeSupportMode == null ? BridgeSupportMode.POSTS : bridgeSupportMode;
            bridgeSupportSpacing = Mth.clamp(bridgeSupportSpacing, 2, 12);
            towerFloorHeight = Mth.clamp(towerFloorHeight, 2, 16);
            towerWallThickness = Mth.clamp(towerWallThickness, 1, 3);
            towerTopStyle = towerTopStyle == null ? TowerTopStyle.BATTLEMENTS : towerTopStyle;
        }
    }

    public enum Filter {
        ALL {
            @Override
            boolean accepts(int x, int y, int z, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
                return true;
            }
        },
        WALLS {
            @Override
            boolean accepts(int x, int y, int z, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
                return x == minX || x == maxX || z == minZ || z == maxZ;
            }
        },
        FLOOR {
            @Override
            boolean accepts(int x, int y, int z, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
                return y == minY;
            }
        },
        CEILING {
            @Override
            boolean accepts(int x, int y, int z, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
                return y == maxY;
            }
        },
        SHELL {
            @Override
            boolean accepts(int x, int y, int z, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
                return x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
            }
        };

        abstract boolean accepts(int x, int y, int z, int minX, int minY, int minZ, int maxX, int maxY, int maxZ);
    }
}
