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
            // 实体探查镜 GUI
            MenuScreens.register(
                net.minecraft.client.yiz.xian.menu.YizxianMenus.ENTITY_PROBE_MENU.get(),
                net.minecraft.client.yiz.xian.client.screen.EntityProbeScreen::new);
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
            net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes.QUANSHOUZHE.get(),
            QuanshouzheRenderer::new);
        event.registerEntityRenderer(
            net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes.TIEDOUSHI.get(),
            net.minecraft.client.yiz.xian.client.renderer.TiedoushiRenderer::new);
        event.registerEntityRenderer(
            net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes.NUYI.get(),
            net.minecraft.client.yiz.xian.client.renderer.NuyiRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(QuanshouzheRenderer.LAYER,
            net.minecraft.client.yiz.xian.client.model.QuanshouzheModel::createBodyLayer);
        event.registerLayerDefinition(
            net.minecraft.client.yiz.xian.client.renderer.TiedoushiRenderer.LAYER,
            net.minecraft.client.yiz.xian.client.model.TiedoushiModel::createBodyLayer);
        event.registerLayerDefinition(
            net.minecraft.client.yiz.xian.client.renderer.NuyiRenderer.LAYER,
            net.minecraft.client.yiz.xian.client.model.NuyiModel::createBodyLayer);
    }
}
