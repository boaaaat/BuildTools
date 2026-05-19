package com.abhil.buildtools.server;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class SurfacePlacementSupport {
    private SurfacePlacementSupport() {
    }

    static List<BlockPos> candidates(Level level, List<BlockPos> generated) {
        Map<Column, Bounds> columns = new LinkedHashMap<>();
        for (BlockPos pos : generated) {
            columns.computeIfAbsent(new Column(pos.getX(), pos.getZ()), ignored -> new Bounds())
                    .include(pos.getY());
        }

        LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
        for (BlockPos pos : generated) {
            BlockState state = level.getBlockState(pos);
            if (state.canBeReplaced() && touchesSolidBlock(level, pos)) {
                candidates.add(pos.immutable());
            }
        }

        for (Map.Entry<Column, Bounds> entry : columns.entrySet()) {
            Column column = entry.getKey();
            Bounds bounds = entry.getValue();
            int minScanY = Mth.clamp(bounds.minY() - 1, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
            int maxScanY = Mth.clamp(bounds.maxY(), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
            for (int y = maxScanY; y >= minScanY; y--) {
                BlockPos surface = new BlockPos(column.x(), y, column.z());
                if (!isSolidSurface(level.getBlockState(surface))) {
                    continue;
                }
                BlockPos candidate = surface.above();
                if (candidate.getY() <= bounds.maxY() + 1 && level.getBlockState(candidate).canBeReplaced()) {
                    candidates.add(candidate.immutable());
                }
                break;
            }
        }
        return List.copyOf(candidates);
    }

    static boolean touchesSolidBlock(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (isSolidSurface(level.getBlockState(pos.relative(direction)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSolidSurface(BlockState state) {
        return !state.isAir() && !state.canBeReplaced();
    }

    private record Column(int x, int z) {
    }

    private static final class Bounds {
        private int minY = Integer.MAX_VALUE;
        private int maxY = Integer.MIN_VALUE;

        private void include(int y) {
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        private int minY() {
            return minY;
        }

        private int maxY() {
            return maxY;
        }
    }
}
