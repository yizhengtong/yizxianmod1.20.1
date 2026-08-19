package net.minecraft.client.yiz.xian.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 火球/陨石发光球体渲染器：把 fire_ball.png 作为 billboard 渲染（始终面向镜头，半透明发光）。
 */
public class FireballEntityRenderer extends EntityRenderer<Entity> {

    private static final ResourceLocation BALL_TEXTURE =
        new ResourceLocation("yizxianmod", "textures/entity/fire_ball.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(BALL_TEXTURE);
    private static final float BALL_SIZE = 1.5f;

    public FireballEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(Entity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(BALL_SIZE, BALL_SIZE, BALL_SIZE);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m4 = pose.pose();
        Matrix3f m3 = pose.normal();
        VertexConsumer vc = buffer.getBuffer(RENDER_TYPE);
        vertex(vc, m4, m3, packedLight, 0.0F, 0, 0, 1);
        vertex(vc, m4, m3, packedLight, 1.0F, 0, 1, 1);
        vertex(vc, m4, m3, packedLight, 1.0F, 1, 1, 0);
        vertex(vc, m4, m3, packedLight, 0.0F, 1, 0, 0);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m4, Matrix3f m3, int packedLight,
                               float x, int y, int u, int v) {
        vc.vertex(m4, x - 0.5F, (float) y - 0.5F, 0.0F)
            .color(255, 255, 255, 255)
            .uv((float) u, (float) v)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(packedLight)
            .normal(m3, 0.0F, 1.0F, 0.0F)
            .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(Entity entity) {
        return BALL_TEXTURE;
    }
}
