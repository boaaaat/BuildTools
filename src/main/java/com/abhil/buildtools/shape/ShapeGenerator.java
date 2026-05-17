package com.abhil.buildtools.shape;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

public final class ShapeGenerator {
    private ShapeGenerator() {
    }

    public static List<BlockPos> generate(Selection selection) {
        if (!selection.isComplete()) {
            return List.of();
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
            case SPHERE -> sphere(a, b, true);
            case ELLIPSOID -> sphere(a, b, false);
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

        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    double nx = (x - centerX) / radiusX;
                    double ny = (y - centerY) / radiusY;
                    double nz = (z - centerZ) / radiusZ;
                    if (nx * nx + ny * ny + nz * nz <= 1.0D) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return positions;
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
