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
        return generate(selection, customMode, stairDirection, new Options(roadWidth, ArchMode.OPEN, 50, ArchDirection.X, false, false));
    }

    public static List<BlockPos> generate(Selection selection, CustomShapeMode customMode, StairDirectionOverride stairDirection, Options options) {
        if (selection.shape() == SelectionShape.CUSTOM_SMART) {
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
            case WALLS -> cuboid(a, b, Filter.WALLS);
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
            case CUSTOM_SMART -> customSmart(selection.points(), customMode);
            case STAIRS -> stairs(selection, stairDirection);
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

    public record Options(int roadWidth, ArchMode archMode, int archPeak, ArchDirection archDirection, boolean sphereHollow, boolean ellipsoidHollow) {
        public static final Options DEFAULT = new Options(3, ArchMode.OPEN, 50, ArchDirection.X, false, false);

        public Options {
            roadWidth = Math.max(1, roadWidth);
            archMode = archMode == null ? ArchMode.OPEN : archMode;
            archPeak = Mth.clamp(archPeak, 0, 100);
            archDirection = archDirection == null ? ArchDirection.X : archDirection;
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
