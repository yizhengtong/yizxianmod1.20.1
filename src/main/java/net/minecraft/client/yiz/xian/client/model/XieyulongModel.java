package net.minecraft.client.yiz.xian.client.model;

import net.minecraft.client.yiz.xian.entity.XieyulongEntity;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;

/**
 * 邪狱龙 GeckoLib 模型。
 *
 * <p>直接继承 {@code GeoModel} 显式指定三资源路径（避免 DefaultedGeoModel 的 subtype 子目录约定）：
 * geo=assets/yizxianmod/geo/xieyulong.geo.json、
 * 动画=assets/yizxianmod/animations/xieyulong.animation.json；
 * 纹理按形态切换（踏虚体=dark 纹理）。</p>
 */
public class XieyulongModel extends GeoModel<XieyulongEntity> {

    private static final ResourceLocation MODEL =
        new ResourceLocation("yizxianmod", "geo/xieyulong.geo.json");
    private static final ResourceLocation ANIMATION =
        new ResourceLocation("yizxianmod", "animations/xieyulong.animation.json");
    private static final ResourceLocation TEXTURE =
        new ResourceLocation("yizxianmod", "textures/entity/xieyulong.png");
    private static final ResourceLocation TEXTURE_SHADOW =
        new ResourceLocation("yizxianmod", "textures/entity/xieyulong_dark.png");

    @Override
    public ResourceLocation getModelResource(XieyulongEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(XieyulongEntity entity) {
        return entity.isShadowForm() ? TEXTURE_SHADOW : TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(XieyulongEntity entity) {
        return ANIMATION;
    }
}
