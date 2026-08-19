package net.minecraft.client.yiz.xian.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.yiz.xian.client.model.TaxutiModel;
import net.minecraft.client.yiz.xian.client.renderer.layer.TaxutiShadowLayer;
import net.minecraft.client.yiz.xian.entity.TaxutiEntity;

import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 踏虚体 GeckoLib 渲染器（叠加黑色影子层）。
 */
public class TaxutiRenderer extends GeoEntityRenderer<TaxutiEntity> {

    public TaxutiRenderer(EntityRendererProvider.Context context) {
        super(context, new TaxutiModel());
        this.shadowRadius = 1.5f;
        this.addRenderLayer(new TaxutiShadowLayer(this));
    }
}
