package net.minecraft.client.yiz.xian.client.renderer;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.client.yiz.xian.client.model.TiedoushiModel;
import net.minecraft.client.yiz.xian.entity.TiedoushiEntity;
import net.minecraft.resources.ResourceLocation;

/**
 * 铁斗士渲染器（原版 ModelPart 模型，单张纹理）。
 */
public class TiedoushiRenderer extends MobRenderer<TiedoushiEntity, TiedoushiModel<TiedoushiEntity>> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
        new ResourceLocation(YizxianMod.MODID, "tiedoushi"), "main");

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        YizxianMod.MODID, "textures/entity/tiedoushi.png");

    public TiedoushiRenderer(EntityRendererProvider.Context context) {
        super(context, new TiedoushiModel<>(context.bakeLayer(LAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(TiedoushiEntity entity) {
        return TEXTURE;
    }
}
