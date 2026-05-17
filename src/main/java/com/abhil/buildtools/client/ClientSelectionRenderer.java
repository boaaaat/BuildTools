package com.abhil.buildtools.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
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

        List<BlockPos> preview = ClientSelectionData.preview();
        if (!preview.isEmpty()) {
            AABB bounds = null;
            int individual = Math.min(preview.size(), 256);
            for (int i = 0; i < individual; i++) {
                BlockPos pos = preview.get(i);
                renderBox(poseStack, lines, new AABB(pos).inflate(0.01D), 0.2F, 1.0F, 0.25F, 0.35F);
                bounds = bounds == null ? new AABB(pos) : bounds.minmax(new AABB(pos));
            }
            for (int i = individual; i < preview.size(); i++) {
                BlockPos pos = preview.get(i);
                bounds = bounds == null ? new AABB(pos) : bounds.minmax(new AABB(pos));
            }
            if (bounds != null) {
                renderBox(poseStack, lines, bounds.inflate(0.03D), 0.2F, 1.0F, 0.25F, 1.0F);
            }
        }

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer lines, AABB box, float red, float green, float blue, float alpha) {
        LevelRenderer.renderLineBox(poseStack, lines, box, red, green, blue, alpha);
    }
}
