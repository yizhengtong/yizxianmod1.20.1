package net.minecraft.client.yiz.xian.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.client.yiz.xian.entity.QuanshouzheEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 辖界者屏幕顶部 Boss 血条自定义渲染（1.20.1）。
 *
 * <p>用户提供两张纹理：{@code bossbar_frame.png}（外框 255×46）+ {@code bossbar_fill.png}（血 215×12）。
 * 拦截 {@link CustomizeGuiOverlayEvent.BossEventProgress}（Forge 钩子），对辖界者（名称「辖界者」）的
 * BossEvent 改为：外框全图打底 + 血条按血量比例从右往左裁掉左段（像素密度恒定不压缩）+
 * 血量文本中心 (126,37) RGB(177,63,0)。整体放大 1.5 倍贴屏幕顶部。其他 Boss 血条不受影响。</p>
 */
@Mod.EventBusSubscriber(modid = YizxianMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BossHealthOverlayHandler {

    private BossHealthOverlayHandler() {}

    /** 外框纹理（全图打底）。 */
    private static final ResourceLocation FRAME =
        new ResourceLocation(YizxianMod.MODID, "textures/gui/bossbar_frame.png");
    /** 血纹理（按血量裁左段）。 */
    private static final ResourceLocation FILL =
        new ResourceLocation(YizxianMod.MODID, "textures/gui/bossbar_fill.png");

    private static final int TEX_W = 255;   // 框纹理宽
    private static final int TEX_H = 46;    // 框纹理高

    /** fill 条（条.png 215×12）纹理尺寸。 */
    private static final int FILL_TEX_W = 215;
    private static final int FILL_TEX_H = 12;

    /** fill 条嵌入框纹理的左上角（相对血条左上角，0-based）：用户指定 (19,31)。 */
    private static final int FILL_REGION_X = 19;
    private static final int FILL_REGION_Y = 31;
    private static final int FILL_REGION_W = FILL_TEX_W;   // 215
    private static final int FILL_REGION_H = FILL_TEX_H;   // 12

    /** 血量文本中心（相对血条左上角）：水平 fill 居中 19+215/2=126、垂直 fill 中部 31+12/2=37。 */
    private static final int HP_TEXT_CENTER_X = 19 + FILL_TEX_W / 2;
    private static final int HP_TEXT_CENTER_Y = 31 + FILL_TEX_H / 2;
    /** 血量文本颜色 RGB(177,63,0)。 */
    private static final int HP_TEXT_COLOR = 0xB13F00;

    /** 血条整体显示缩放（屏幕里太小，放大到 1.5 倍，围绕血条左上角 scale，血条+文本+位置同步放大）。 */
    private static final float BAR_SCALE = 1.5f;

    @SubscribeEvent
    public static void onBossEventProgress(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (boss == null || boss.getName() == null) return;
        // 服务端 BossEvent 名称固定 Component.literal("辖界者")（不随客户端语言翻译）
        if (!boss.getName().getString().contains("辖界者")) return;

        // 垂直排列：多个辖界者血条按 BossOverlay 中辖界者 BossEvent 顺序下移（避免全叠在 y=0）。
        // BossOverlay.events 是私有 Map，按类型反射取（不依赖字段名，生产 SRG 安全）。
        int y = 0;
        try {
            Object overlay = Minecraft.getInstance().gui.getBossOverlay();
            if (overlay != null) {
                for (java.lang.reflect.Field f : overlay.getClass().getDeclaredFields()) {
                    if (java.util.Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object raw = f.get(overlay);
                        if (raw instanceof java.util.Map<?, ?> map) {
                            int idx = 0;
                            for (Object v : map.values()) {
                                if (v instanceof LerpingBossEvent e) {
                                    if (e == boss) break;
                                    if (e.getName() != null && e.getName().getString().contains("辖界者")) idx++;
                                }
                            }
                            y = idx * Math.round(TEX_H * BAR_SCALE);
                            break;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        renderYizxianBar(event.getGuiGraphics(), event.getX(), y, boss);
        event.setCanceled(true);   // 阻止原版 drawBar
    }

    private static void renderYizxianBar(GuiGraphics gui, int x, int y, LerpingBossEvent boss) {
        // 整体放大 BAR_SCALE 倍（屏幕里太小）：围绕血条左上角 scale，血条+文本+位置按比例放大
        int w = Math.round(TEX_W * BAR_SCALE);
        int h = Math.round(TEX_H * BAR_SCALE);
        int cx = (gui.guiWidth() - w) / 2;

        var pose = gui.pose();
        pose.pushPose();
        pose.translate(cx, y, 0.0F);
        pose.scale(BAR_SCALE, BAR_SCALE, 1.0F);

        // 外框全图（局部坐标，纹理像素）
        gui.blit(FRAME, 0, 0, 0.0f, 0.0f, TEX_W, TEX_H, TEX_W, TEX_H);
        // 血：嵌套在框内 (19,31)-(234,43) 区域，从右往左缩短（纹理左段按 p 截取，像素密度恒定）
        float p = Mth.clamp(boss.getProgress(), 0.0f, 1.0f);
        if (p > 0.001f) {
            gui.blit(FILL, FILL_REGION_X, FILL_REGION_Y,
                (int) (FILL_REGION_W * p), FILL_REGION_H,
                0.0f, 0.0f, (int) (FILL_TEX_W * p), FILL_TEX_H, FILL_TEX_W, FILL_TEX_H);
        }
        // 实际血量文本：中心 (126,37)，RGB(177,63,0)
        var font = Minecraft.getInstance().font;
        String hpText = readYizxianHealthText(boss);
        if (hpText != null) {
            gui.drawCenteredString(font, hpText,
                HP_TEXT_CENTER_X, HP_TEXT_CENTER_Y - font.lineHeight / 2, HP_TEXT_COLOR);
        }
        pose.popPose();
    }

    /**
     * 血量文本：用 {@code boss.getProgress()}（服务端 setProgress 每 tick 同步，与进度条同源）计算，
     * 避免客户端 {@code SecureHealthClosure.getHealth} 因 DATA_HEALTH/混淆串同步滞后
     * 导致文本不随实际血量变动。格式 hp/maxHp。
     */
    private static String readYizxianHealthText(LerpingBossEvent boss) {
        float max = 0;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            for (Entity e : level.entitiesForRendering()) {
                if (e instanceof QuanshouzheEntity q && !q.isRemoved()) {
                    max = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getMaxHealth(q);
                    break;
                }
            }
        }
        if (max <= 0) max = 400f;
        int hp = (int) (boss.getProgress() * max);
        return hp + "/" + (int) max;
    }
}
