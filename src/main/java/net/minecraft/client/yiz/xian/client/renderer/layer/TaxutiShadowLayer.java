package net.minecraft.client.yiz.xian.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.yiz.xian.entity.TaxutiEntity;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 踏虚体「黑色影子」渲染层：本体常驻暗纹理半透明叠加（上移 1.5，alpha 0.5）。
 * 影分身本身已是暗形态，不再叠影子。
 */
public class TaxutiShadowLayer extends GeoRenderLayer<TaxutiEntity> {

    private static final ResourceLocation DARK_TEXTURE =
        new ResourceLocation("yizxianmod", "textures/entity/taxuti_dark.png");
    private static final float SHADOW_OFFSET_Y = 1.5f;
    private static final float SHADOW_ALPHA = 0.5f;

    public TaxutiShadowLayer(GeoRenderer<TaxutiEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, TaxutiEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        if (animatable.isShadowForm()) {
            return;
        }
        RenderType darkType = RenderType.entityTranslucent(DARK_TEXTURE);
        VertexConsumer darkConsumer = bufferSource.getBuffer(darkType);
        poseStack.pushPose();
        poseStack.translate(0.0, SHADOW_OFFSET_Y, 0.0);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, darkType, darkConsumer,
            partialTick, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, SHADOW_ALPHA);
        poseStack.popPose();
    }
}
