package net.minecraft.client.yiz.xian.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.client.yiz.xian.client.model.QuanshouzheModel;
import net.minecraft.client.yiz.xian.entity.QuanshouzheEntity;
import net.minecraft.resources.ResourceLocation;

/**
 * 辖界者渲染器（1.20.1 移植版）— Warden 骨架 + warden.png 纹理。
 */
public class QuanshouzheRenderer extends MobRenderer<QuanshouzheEntity, QuanshouzheModel<QuanshouzheEntity>> {

    private static final float MODEL_SCALE = 1.2F;

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
        new ResourceLocation(YizxianMod.MODID, "quanshouzhe"), "main");

    public QuanshouzheRenderer(EntityRendererProvider.Context context) {
        super(context, new QuanshouzheModel<>(context.bakeLayer(LAYER)), 1.0F);
    }

    @Override
    protected void scale(QuanshouzheEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(QuanshouzheEntity entity) {
        return new ResourceLocation(YizxianMod.MODID, "textures/entity/quanshouzhe/warden.png");
    }
}
