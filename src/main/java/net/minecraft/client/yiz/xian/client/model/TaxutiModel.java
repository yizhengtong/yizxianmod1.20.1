package net.minecraft.client.yiz.xian.client.model;

import net.minecraft.client.yiz.xian.entity.TaxutiEntity;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;

/**
 * 踏虚体 GeckoLib 模型。显式指定三资源路径；纹理按形态切换（踏虚体影分身=dark 纹理）。
 */
public class TaxutiModel extends GeoModel<TaxutiEntity> {

    private static final ResourceLocation MODEL =
        new ResourceLocation("yizxianmod", "geo/taxuti.geo.json");
    private static final ResourceLocation ANIMATION =
        new ResourceLocation("yizxianmod", "animations/taxuti.animation.json");
    private static final ResourceLocation TEXTURE =
        new ResourceLocation("yizxianmod", "textures/entity/taxuti.png");
    private static final ResourceLocation TEXTURE_SHADOW =
        new ResourceLocation("yizxianmod", "textures/entity/taxuti_dark.png");

    @Override
    public ResourceLocation getModelResource(TaxutiEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(TaxutiEntity entity) {
        return entity.isShadowForm() ? TEXTURE_SHADOW : TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(TaxutiEntity entity) {
        return ANIMATION;
    }
}
