package com.abhil.buildtools.shape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ShapeGeneratorTest {
    @Test
    void cuboidIncludesEveryBlockInBounds() {
        Selection selection = new Selection(UUID.randomUUID(), Level.OVERWORLD, new BlockPos(0, 0, 0), new BlockPos(2, 1, 2), SelectionShape.CUBOID);

        List<BlockPos> positions = ShapeGenerator.generate(selection);

        assertEquals(18, positions.size());
        assertTrue(positions.contains(new BlockPos(0, 0, 0)));
        assertTrue(positions.contains(new BlockPos(2, 1, 2)));
    }

    @Test
    void hollowBoxOnlyIncludesShell() {
        Selection selection = new Selection(UUID.randomUUID(), Level.OVERWORLD, new BlockPos(0, 0, 0), new BlockPos(2, 2, 2), SelectionShape.HOLLOW_BOX);

        List<BlockPos> positions = ShapeGenerator.generate(selection);

        assertEquals(26, positions.size());
        assertTrue(!positions.contains(new BlockPos(1, 1, 1)));
    }

    @Test
    void lineHasStableUniquePositions() {
        Selection selection = new Selection(UUID.randomUUID(), Level.OVERWORLD, new BlockPos(0, 0, 0), new BlockPos(3, 0, 0), SelectionShape.LINE);

        List<BlockPos> positions = ShapeGenerator.generate(selection);

        assertEquals(4, positions.size());
        assertEquals(positions.size(), new HashSet<>(positions).size());
    }

    @Test
    void advancedLineFollowsOrderedPoints() {
        Selection selection = new Selection(
                UUID.randomUUID(),
                Level.OVERWORLD,
                new BlockPos(0, 0, 0),
                new BlockPos(2, 0, 2),
                SelectionShape.LINE,
                List.of(new BlockPos(0, 0, 0), new BlockPos(2, 0, 0), new BlockPos(2, 0, 2)));

        List<BlockPos> positions = ShapeGenerator.generate(selection);

        assertEquals(List.of(
                new BlockPos(0, 0, 0),
                new BlockPos(1, 0, 0),
                new BlockPos(2, 0, 0),
                new BlockPos(2, 0, 1),
                new BlockPos(2, 0, 2)), positions);
    }

    @Test
    void cylinderUsesEllipticalFootprintAcrossHeight() {
        Selection selection = new Selection(UUID.randomUUID(), Level.OVERWORLD, new BlockPos(0, 0, 0), new BlockPos(2, 1, 2), SelectionShape.CYLINDER);

        List<BlockPos> positions = ShapeGenerator.generate(selection);

        assertEquals(10, positions.size());
        assertTrue(positions.contains(new BlockPos(1, 0, 1)));
        assertTrue(positions.contains(new BlockPos(1, 1, 1)));
    }
}
