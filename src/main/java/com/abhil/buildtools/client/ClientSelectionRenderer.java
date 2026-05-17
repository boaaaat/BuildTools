package com.abhil.buildtools.client;

import com.abhil.buildtools.shape.SelectionShape;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class ClientSelectionRenderer {
    private ClientSelectionRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || Minecraft.getInstance().level == null) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        ClientSelectionData.first().ifPresent(pos -> renderBox(poseStack, lines, new AABB(pos), 0.1F, 0.8F, 1.0F, 1.0F));
        ClientSelectionData.second().ifPresent(pos -> renderBox(poseStack, lines, new AABB(pos), 1.0F, 0.7F, 0.1F, 1.0F));
        renderSelectionHandles(poseStack, lines);
        renderAdvancedPoints(event, poseStack, buffers, lines);

        List<BlockPos> preview = ClientSelectionData.preview();
        if (!preview.isEmpty()) {
            AABB bounds = null;
            for (BlockPos pos : preview) {
                bounds = bounds == null ? new AABB(pos) : bounds.minmax(new AABB(pos));
            }

            if (rendersAffectedBlocks(ClientSelectionData.shape(), ClientSelectionData.detailedPreview())) {
                renderAffectedEdges(poseStack, lines, preview, 0.2F, 1.0F, 0.25F, 0.9F);
            } else if (bounds != null) {
                renderBox(poseStack, lines, bounds.inflate(0.03D), 0.2F, 1.0F, 0.25F, 1.0F);
            }
        }

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static boolean rendersAffectedBlocks(SelectionShape shape, boolean detailedPreview) {
        return detailedPreview || shape != SelectionShape.CUBOID;
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer lines, AABB box, float red, float green, float blue, float alpha) {
        LevelRenderer.renderLineBox(poseStack, lines, box, red, green, blue, alpha);
    }

    private static void renderSelectionHandles(PoseStack poseStack, VertexConsumer lines) {
        if (ClientSelectionData.first().isEmpty() || ClientSelectionData.second().isEmpty()) {
            return;
        }
        BlockPos first = ClientSelectionData.first().get();
        BlockPos second = ClientSelectionData.second().get();
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX()) + 1;
        int maxY = Math.max(first.getY(), second.getY()) + 1;
        int maxZ = Math.max(first.getZ(), second.getZ()) + 1;
        double size = 0.12D;

        for (int x : new int[] { minX, maxX }) {
            for (int y : new int[] { minY, maxY }) {
                for (int z : new int[] { minZ, maxZ }) {
                    renderBox(poseStack, lines, new AABB(
                            x - size,
                            y - size,
                            z - size,
                            x + size,
                            y + size,
                            z + size), 1.0F, 1.0F, 0.15F, 1.0F);
                }
            }
        }
    }

    private static void renderAdvancedPoints(RenderLevelStageEvent event, PoseStack poseStack, MultiBufferSource buffers, VertexConsumer lines) {
        List<BlockPos> points = ClientSelectionData.points();
        if (points.isEmpty()) {
            return;
        }

        for (int i = 0; i < points.size(); i++) {
            BlockPos point = points.get(i);
            renderBox(poseStack, lines, new AABB(point).inflate(0.08D), 1.0F, 0.25F, 0.95F, 1.0F);
            renderPointLabel(event, poseStack, buffers, point, Integer.toString(i + 1));
        }
    }

    private static void renderPointLabel(RenderLevelStageEvent event, PoseStack poseStack, MultiBufferSource buffers, BlockPos point, String label) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        poseStack.pushPose();
        poseStack.translate(point.getX() + 0.5D, point.getY() + 1.2D, point.getZ() + 0.5D);
        poseStack.mulPose(event.getCamera().rotation());
        poseStack.scale(-0.03F, -0.03F, 0.03F);
        float x = -font.width(label) / 2.0F;
        font.drawInBatch(
                label,
                x,
                0.0F,
                0xFFFFFFFF,
                false,
                poseStack.last().pose(),
                buffers,
                Font.DisplayMode.SEE_THROUGH,
                0xAA000000,
                LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private static void renderAffectedEdges(PoseStack poseStack, VertexConsumer lines, List<BlockPos> positions, float red, float green, float blue, float alpha) {
        Set<BlockPos> occupied = new HashSet<>(positions);
        Set<Edge> edges = new HashSet<>();
        for (BlockPos pos : positions) {
            for (Direction direction : Direction.values()) {
                if (!occupied.contains(pos.relative(direction))) {
                    addFaceEdges(edges, pos, direction);
                }
            }
        }

        for (Edge edge : edges) {
            renderLine(poseStack, lines, edge, red, green, blue, alpha);
        }
    }

    private static void addFaceEdges(Set<Edge> edges, BlockPos pos, Direction direction) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int x1 = x + 1;
        int y1 = y + 1;
        int z1 = z + 1;

        switch (direction) {
            case DOWN -> addFace(edges, x, y, z, x1, y, z, x1, y, z1, x, y, z1);
            case UP -> addFace(edges, x, y1, z, x1, y1, z, x1, y1, z1, x, y1, z1);
            case NORTH -> addFace(edges, x, y, z, x1, y, z, x1, y1, z, x, y1, z);
            case SOUTH -> addFace(edges, x, y, z1, x1, y, z1, x1, y1, z1, x, y1, z1);
            case WEST -> addFace(edges, x, y, z, x, y1, z, x, y1, z1, x, y, z1);
            case EAST -> addFace(edges, x1, y, z, x1, y1, z, x1, y1, z1, x1, y, z1);
        }
    }

    private static void addFace(
            Set<Edge> edges,
            int ax,
            int ay,
            int az,
            int bx,
            int by,
            int bz,
            int cx,
            int cy,
            int cz,
            int dx,
            int dy,
            int dz) {
        edges.add(Edge.of(ax, ay, az, bx, by, bz));
        edges.add(Edge.of(bx, by, bz, cx, cy, cz));
        edges.add(Edge.of(cx, cy, cz, dx, dy, dz));
        edges.add(Edge.of(dx, dy, dz, ax, ay, az));
    }

    private static void renderLine(PoseStack poseStack, VertexConsumer lines, Edge edge, float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        float x1 = edge.x1();
        float y1 = edge.y1();
        float z1 = edge.z1();
        float x2 = edge.x2();
        float y2 = edge.y2();
        float z2 = edge.z2();
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length <= 0.0F) {
            return;
        }
        nx /= length;
        ny /= length;
        nz /= length;
        lines.addVertex(pose, x1, y1, z1).setColor(red, green, blue, alpha).setNormal(pose, nx, ny, nz);
        lines.addVertex(pose, x2, y2, z2).setColor(red, green, blue, alpha).setNormal(pose, nx, ny, nz);
    }

    private record Edge(int x1, int y1, int z1, int x2, int y2, int z2) {
        private static Edge of(int x1, int y1, int z1, int x2, int y2, int z2) {
            if (comesAfter(x1, y1, z1, x2, y2, z2)) {
                return new Edge(x2, y2, z2, x1, y1, z1);
            }
            return new Edge(x1, y1, z1, x2, y2, z2);
        }

        private static boolean comesAfter(int ax, int ay, int az, int bx, int by, int bz) {
            if (ax != bx) {
                return ax > bx;
            }
            if (ay != by) {
                return ay > by;
            }
            return az > bz;
        }
    }
}
