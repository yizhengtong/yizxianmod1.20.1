package net.minecraft.client.yiz.xian;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.yiz.xian.client.renderer.QuanshouzheRenderer;
import net.minecraft.client.yiz.xian.client.screen.EntityAttributeEditScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 资源模组客户端（1.20.1 移植版）— 注册辖界者渲染器 + 实体属性编辑 Screen。
 */
@Mod.EventBusSubscriber(modid = YizxianMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class YizxianModClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 自走棋棋子星级描边：EntityOutline 公开门面注册动态 Provider，
            // 仅棋子（费用>0）按星级返回 白(1星)/蓝(2星)/金(3星) RGBA，非棋子不描边
            net.minecraft.client.yiz.api.EntityOutline.register(entity -> {
                if (entity instanceof net.minecraft.client.yiz.xian.entity.base.YizxianMob ym) {
                    if (ym.getChessCostForRender() <= 0) return null;
                    return switch (ym.getChessStarForRender()) {
                        case 2 -> new float[]{0.15f, 0.45f, 1f, 1f};
                        case 3 -> new float[]{1f, 0.84f, 0f, 1f};
                        default -> new float[]{1f, 1f, 1f, 1f};
                    };
                }
                return null;
            });
            MenuScreens.register(
                net.minecraft.client.yiz.xian.menu.YizxianMenus.ENTITY_ATTRIBUTE_EDIT_MENU.get(),
                EntityAttributeEditScreen::new);
            MenuScreens.register(
                net.minecraft.client.yiz.xian.menu.YizxianMenus.LIGHT_COMPASS_MENU.get(),
                net.minecraft.client.yiz.xian.client.screen.LightCompassScreen::new);
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
            net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes.QUANSHOUZHE.get(),
            QuanshouzheRenderer::new);
        event.registerEntityRenderer(
            net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes.XIEYULONG.get(),
            net.minecraft.client.yiz.xian.client.renderer.XieyulongRenderer::new);
        // 三技能弹道：空渲染器（本体不可见，靠 vanilla 粒子显示）
        event.registerEntityRenderer(
            net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes.XIEYULONG_FIRE.get(),
            net.minecraft.client.yiz.xian.client.renderer.FireballEntityRenderer::new);
        event.registerEntityRenderer(
            net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes.XIEYULONG_METEOR.get(),
            net.minecraft.client.yiz.xian.client.renderer.FireballEntityRenderer::new);
        event.registerEntityRenderer(
            net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes.XIEYULONG_POISON_CLOUD.get(),
            net.minecraft.client.yiz.xian.client.renderer.NoopEntityRenderer::new);
        // 踏虚体 + orb 弹道
        event.registerEntityRenderer(
            net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes.TAXUTI.get(),
            net.minecraft.client.yiz.xian.client.renderer.TaxutiRenderer::new);
        event.registerEntityRenderer(
            net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes.TAXUTI_ORB.get(),
            net.minecraft.client.yiz.xian.client.renderer.NoopEntityRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(QuanshouzheRenderer.LAYER,
            net.minecraft.client.yiz.xian.client.model.QuanshouzheModel::createBodyLayer);
    }
}
