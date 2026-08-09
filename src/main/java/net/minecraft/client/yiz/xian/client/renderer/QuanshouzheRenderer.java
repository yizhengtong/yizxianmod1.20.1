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
 * 辖界者渲染器（1.20.1 移植版）— Warden 骨架 + 三阶段纹理。
 * 阶段1 warden.png / 阶段2 warden2.png / 阶段3 warden3.png（getFormPhase 实时判定）。
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
        String tex = switch (entity.getFormPhase()) {
            case 2 -> "warden2.png";
            case 3 -> "warden3.png";
            default -> "warden.png";
        };
        return new ResourceLocation(YizxianMod.MODID, "textures/entity/quanshouzhe/" + tex);
    }
}
