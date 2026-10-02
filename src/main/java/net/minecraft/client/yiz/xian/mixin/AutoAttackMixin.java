package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自动攻击 Mixin — 消费 {@link YizAttributes#AUTO_ATTACK} 属性。
 *
 * <p>触发条件：AUTO_ATTACK &gt; 0 + 按住攻击键 + 冷却满 + 准星在近战范围内瞄到可攻击实体。</p>
 *
 * <p><b>为什么不再反射调 {@code Minecraft.startAttack()}</b>（2026-10-03 修复）：
 * 它是 private，只能按「参数签名 + 返回类型」反射定位；而 Minecraft 里「空参 + 返回 boolean」
 * 的方法有 28 个（{@code checkIs64Bit}/{@code isRunning}/{@code isDemo}/…），
 * {@code MixinAccess.invoke} 取的是第一个匹配 → 实际调到的是无关方法，返回值被丢弃，
 * 表现为「属性有值、也瞄着怪，但就是不自动挥」，且全程无报错。
 * 现改为照抄原版 {@code startAttack()} 的实体分支，全走公开 API：
 * Forge 输入事件 → {@code isHandsBusy} → {@code gameMode.attack}（内部已重置攻击冷却）→ 按事件决定挥手。</p>
 */
@Mixin(Minecraft.class)
public abstract class AutoAttackMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void yizxian_autoAttack(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator()) return;
        if (mc.gameMode == null || mc.level == null) return;
        if (mc.screen != null) return;                              // 开着 GUI 不自动打（与原版一致）
        if (player.getAttackStrengthScale(0f) < 1.0f) return;        // 冷却未满：不挥，避免连发
        if (player.getAttributeValue(YizAttributes.AUTO_ATTACK.get()) <= 0) return;
        if (!mc.options.keyAttack.isDown()) return;

        LivingEntity target = aimingTarget(mc, player);
        if (target == null) return;   // 必须瞄着可攻击实体，避免空挥浪费冷却/误拆方块

        // ── 原版 Minecraft.startAttack() 的实体分支（1.20.1）──
        InputEvent.InteractionKeyMappingTriggered hook =
                ForgeHooksClient.onClickInput(0, mc.options.keyAttack, InteractionHand.MAIN_HAND);
        if (hook.isCanceled() || player.isHandsBusy()) return;
        mc.gameMode.attack(player, target);   // 发包 + player.attack + resetAttackStrengthTicker
        if (hook.shouldSwingHand()) player.swing(InteractionHand.MAIN_HAND);
    }

    /** 准星命中的可攻击实体（近战范围内）；无则返回 null。 */
    private static LivingEntity aimingTarget(Minecraft mc, LocalPlayer player) {
        if (!(mc.hitResult instanceof EntityHitResult ehr)) return null;
        var entity = ehr.getEntity();
        if (entity == null || entity == player || !(entity instanceof LivingEntity living)) return null;
        double reach = 3.0;
        try {
            var reachInst = player.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
            if (reachInst != null) reach = Math.max(3.0, reachInst.getValue());
        } catch (Throwable ignored) {}
        return player.distanceTo(entity) <= reach ? living : null;
    }
}
