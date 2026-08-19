package net.minecraft.client.yiz.xian.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.yiz.xian.client.model.XieyulongModel;
import net.minecraft.client.yiz.xian.entity.XieyulongEntity;

import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 邪狱龙 GeckoLib 渲染器（踏虚体邪狱龙）。
 */
public class XieyulongRenderer extends GeoEntityRenderer<XieyulongEntity> {

    public XieyulongRenderer(EntityRendererProvider.Context context) {
        super(context, new XieyulongModel());
        this.shadowRadius = 1.5f;
    }
}
