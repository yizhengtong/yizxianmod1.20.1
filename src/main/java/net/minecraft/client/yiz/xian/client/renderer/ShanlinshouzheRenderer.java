package net.minecraft.client.yiz.xian.client.renderer;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.client.yiz.xian.client.model.ShanlinshouzheModel;
import net.minecraft.client.yiz.xian.entity.ShanlinshouzheEntity;
import net.minecraft.resources.ResourceLocation;

/**
 * 山林首者渲染器（原版 ModelPart + AnimationDefinition）。
 */
public class ShanlinshouzheRenderer extends MobRenderer<ShanlinshouzheEntity, ShanlinshouzheModel<ShanlinshouzheEntity>> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
        new ResourceLocation(YizxianMod.MODID, "shanlinshouzhe"), "main");

    public ShanlinshouzheRenderer(EntityRendererProvider.Context context) {
        super(context, new ShanlinshouzheModel<>(context.bakeLayer(LAYER)), 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(ShanlinshouzheEntity entity) {
        return new ResourceLocation(YizxianMod.MODID, "textures/entity/shanlinshouzhe.png");
    }
}
