package com.beltorio.client;

import com.beltorio.block.entity.MechanicalArmBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class MechanicalArmBlockEntityRenderer implements BlockEntityRenderer<MechanicalArmBlockEntity> {

    private static final Identifier TEXTURE_ID = Identifier.of("beltorio", "block/mechanical_arm");

    public MechanicalArmBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    @Override
    public void render(MechanicalArmBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {

        Sprite sprite = MinecraftClient.getInstance().getBakedModelManager()
                .getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getSprite(TEXTURE_ID);

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getSolid());

        Direction facing = Direction.NORTH;
        if (entity.getCachedState() != null && entity.getCachedState().contains(Properties.HORIZONTAL_FACING)) {
            facing = entity.getCachedState().get(Properties.HORIZONTAL_FACING);
        }

        float yRot = switch (facing) {
            case SOUTH -> 180f;
            case WEST -> 90f;
            case EAST -> -90f;
            default -> 0f; // NORTH
        };

        matrices.push();
        // Rotate around center of block for facing
        matrices.translate(0.5f, 0f, 0.5f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yRot));
        matrices.translate(-0.5f, 0f, -0.5f);

        // ── Draw base (static) ──
        // Base: from [1,0,1] to [15,4,15] in block coords (1/16 units)
        float bx0 = 1f / 16f, by0 = 0f, bz0 = 1f / 16f;
        float bx1 = 15f / 16f, by1 = 4f / 16f, bz1 = 15f / 16f;
        drawBox(matrices, buffer, sprite, light, overlay,
                bx0, by0, bz0, bx1, by1, bz1,
                0, 0, 8, 8,     // top/bottom UV
                0, 8, 8, 12);   // side UV

        // ── Draw pillar (static) ──
        // Pillar: from [6,4,6] to [10,14,10]
        float px0 = 6f / 16f, py0 = 4f / 16f, pz0 = 6f / 16f;
        float px1 = 10f / 16f, py1 = 14f / 16f, pz1 = 10f / 16f;
        drawBox(matrices, buffer, sprite, light, overlay,
                px0, py0, pz0, px1, py1, pz1,
                8, 0, 12, 4,    // top UV
                8, 0, 12, 8);   // side UV

        // ── Draw arm (animated) ──
        float armRotation = entity.getArmRotationRad(tickDelta);

        matrices.push();
        // Pivot at top of pillar center: (8/16, 14/16, 8/16)
        matrices.translate(8f / 16f, 14f / 16f, 8f / 16f);
        // Rotate around X axis (pitch) — the arm extends along Z in model-local space (NORTH facing)
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(armRotation));
        matrices.translate(-8f / 16f, -14f / 16f, -8f / 16f);

        // Arm body: from [5,11,0] to [11,14,16]
        float ax0 = 5f / 16f, ay0 = 11f / 16f, az0 = 0f;
        float ax1 = 11f / 16f, ay1 = 14f / 16f, az1 = 1f;
        drawArm(matrices, buffer, sprite, light, overlay,
                ax0, ay0, az0, ax1, ay1, az1);

        // Output claw: from [6,7,14] to [10,11,16]
        float cx0 = 6f / 16f, cy0 = 7f / 16f, cz0 = 14f / 16f;
        float cx1 = 10f / 16f, cy1 = 11f / 16f, cz1 = 1f;
        drawBox(matrices, buffer, sprite, light, overlay,
                cx0, cy0, cz0, cx1, cy1, cz1,
                8, 8, 12, 10,   // top/bottom UV
                8, 8, 12, 12);  // side UV

        // Input back piece: from [6,11,0] to [10,14,2]
        float ix0 = 6f / 16f, iy0 = 11f / 16f, iz0 = 0f;
        float ix1 = 10f / 16f, iy1 = 14f / 16f, iz1 = 2f / 16f;
        drawBox(matrices, buffer, sprite, light, overlay,
                ix0, iy0, iz0, ix1, iy1, iz1,
                0, 0, 4, 2,     // top/bottom UV
                0, 0, 4, 3);    // side UV

        // ── Render held item ──
        ItemStack heldItem = entity.getHeldItem();
        if (!heldItem.isEmpty()) {
            matrices.push();
            // Position at claw tip center: roughly (8/16, 9/16, 15/16)
            matrices.translate(8f / 16f, 9f / 16f, 15f / 16f);
            matrices.scale(0.35f, 0.35f, 0.35f);
            ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
            itemRenderer.renderItem(heldItem, ModelTransformationMode.FIXED, light,
                    OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, entity.getWorld(), 0);
            matrices.pop();
        }

        matrices.pop(); // arm rotation pop

        matrices.pop(); // facing rotation pop
    }

    private void drawArm(MatrixStack matrices, VertexConsumer buffer, Sprite sprite,
                         int light, int overlay,
                         float x0, float y0, float z0, float x1, float y1, float z1) {
        Matrix4f model = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();

        // North face (z=z0): UV [0,8,6,11]
        drawQuad(model, normal, buffer, sprite, light, overlay,
                x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0,
                0, 8, 6, 11, 0f, 0f, -1f);

        // South face (z=z1): UV [0,8,6,11]
        drawQuad(model, normal, buffer, sprite, light, overlay,
                x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1,
                0, 8, 6, 11, 0f, 0f, 1f);

        // East face (x=x1): UV [0,8,16,11]
        drawQuad(model, normal, buffer, sprite, light, overlay,
                x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1,
                0, 8, 16, 11, 1f, 0f, 0f);

        // West face (x=x0): UV [0,8,16,11]
        drawQuad(model, normal, buffer, sprite, light, overlay,
                x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0,
                0, 8, 16, 11, -1f, 0f, 0f);

        // Top face (y=y1): UV [0,8,6,16]
        drawQuad(model, normal, buffer, sprite, light, overlay,
                x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1,
                0, 8, 6, 16, 0f, 1f, 0f);

        // Bottom face (y=y0): UV [0,8,6,16]
        drawQuad(model, normal, buffer, sprite, light, overlay,
                x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0,
                0, 8, 6, 16, 0f, -1f, 0f);
    }

    private void drawBox(MatrixStack matrices, VertexConsumer buffer, Sprite sprite,
                         int light, int overlay,
                         float x0, float y0, float z0, float x1, float y1, float z1,
                         int tbU0, int tbV0, int tbU1, int tbV1,
                         int sU0, int sV0, int sU1, int sV1) {
        Matrix4f model = matrices.peek().getPositionMatrix();
        Matrix3f normal = matrices.peek().getNormalMatrix();

        // North (z=z0)
        drawQuad(model, normal, buffer, sprite, light, overlay,
                x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0,
                sU0, sV0, sU1, sV1, 0f, 0f, -1f);

        // South (z=z1)
        drawQuad(model, normal, buffer, sprite, light, overlay,
                x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1,
                sU0, sV0, sU1, sV1, 0f, 0f, 1f);

        // East (x=x1)
        drawQuad(model, normal, buffer, sprite, light, overlay,
                x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1,
                sU0, sV0, sU1, sV1, 1f, 0f, 0f);

        // West (x=x0)
        drawQuad(model, normal, buffer, sprite, light, overlay,
                x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0,
                sU0, sV0, sU1, sV1, -1f, 0f, 0f);

        // Top (y=y1)
        drawQuad(model, normal, buffer, sprite, light, overlay,
                x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1,
                tbU0, tbV0, tbU1, tbV1, 0f, 1f, 0f);

        // Bottom (y=y0)
        drawQuad(model, normal, buffer, sprite, light, overlay,
                x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0,
                tbU0, tbV0, tbU1, tbV1, 0f, -1f, 0f);
    }

    private void drawQuad(Matrix4f model, Matrix3f normal, VertexConsumer buffer, Sprite sprite,
                          int light, int overlay,
                          float x0, float y0, float z0,
                          float x1, float y1, float z1,
                          float x2, float y2, float z2,
                          float x3, float y3, float z3,
                          int uvX0, int uvY0, int uvX1, int uvY1,
                          float nx, float ny, float nz) {
        // Convert 16x16 pixel UV coordinates to atlas UV via sprite
        float u0 = sprite.getFrameU(uvX0 / 16f);
        float v0 = sprite.getFrameV(uvY0 / 16f);
        float u1 = sprite.getFrameU(uvX1 / 16f);
        float v1 = sprite.getFrameV(uvY1 / 16f);

        buffer.vertex(model, x0, y0, z0).color(255, 255, 255, 255).texture(u0, v0)
                .overlay(overlay).light(light).normal(normal, nx, ny, nz);
        buffer.vertex(model, x1, y1, z1).color(255, 255, 255, 255).texture(u1, v0)
                .overlay(overlay).light(light).normal(normal, nx, ny, nz);
        buffer.vertex(model, x2, y2, z2).color(255, 255, 255, 255).texture(u1, v1)
                .overlay(overlay).light(light).normal(normal, nx, ny, nz);
        buffer.vertex(model, x3, y3, z3).color(255, 255, 255, 255).texture(u0, v1)
                .overlay(overlay).light(light).normal(normal, nx, ny, nz);
    }
}
