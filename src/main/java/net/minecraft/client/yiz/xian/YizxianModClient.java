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
            MenuScreens.register(
                net.minecraft.client.yiz.xian.menu.YizxianMenus.ENTITY_ATTRIBUTE_EDIT_MENU.get(),
                EntityAttributeEditScreen::new);
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
            net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes.QUANSHOUZHE.get(),
            QuanshouzheRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(QuanshouzheRenderer.LAYER,
            net.minecraft.client.yiz.xian.client.model.QuanshouzheModel::createBodyLayer);
    }
}
