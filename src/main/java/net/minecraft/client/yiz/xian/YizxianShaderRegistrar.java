package net.minecraft.client.yiz.xian;

import net.minecraft.client.yiz.xian.render.ZhaoMingLightShaders;
import net.minecraft.client.yiz.xian.render.glow.OutlineShaders;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

/**
 * 下游着色器事件注册（1.20.1 移植版）。
 *
 * <p>1.20.1 的 {@link RegisterShadersEvent} 由 Forge 在 game 总线触发，故本类挂 Bus.FORGE。
 * 只注册 glow_edge / zhaoming_plasma 两个 shader + RenderType（基础设施）。
 * 渲染调用侧（ItemRendererGlowMixin/GlowEdgeBakedModel/EnergyWaveRenderer）未移植
 * ——挂载物品（terraprisma/terra_blade 等）1.20.1 不存在，见 port-gap-list.md #8。</p>
 */
@Mod.EventBusSubscriber(modid = YizxianMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class YizxianShaderRegistrar {

    private YizxianShaderRegistrar() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        OutlineShaders.onRegisterShaders(event);
        ZhaoMingLightShaders.onRegisterShaders(event);
    }
}
