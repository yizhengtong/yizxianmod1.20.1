package net.minecraft.client.yiz.xian.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自动攻击 Mixin — 消费 {@link YizAttributes#AUTO_ATTACK} 属性（1.21.1 移植版）。
 *
 * <p>1.21.1 完整版有三触发源：ILeftHandRender 武器 + auto_attack 附魔 + AUTO_ATTACK 属性。
 * 1.20.1 无 ILeftHandRender 武器接口、无 yizmodqzk:auto_attack 附魔，故只保留属性触发源
 * （见 port-gap-list.md #14）。</p>
 *
 * <p>触发条件：AUTO_ATTACK > 0 + 按住攻击键 + 冷却满 + 瞄准范围内可攻击实体。</p>
 */
@Mixin(Minecraft.class)
public abstract class AutoAttackMixin {

    @Shadow public LocalPlayer player;
    @Shadow public abstract boolean startAttack();

    @Inject(method = "tick", at = @At("TAIL"))
    private void yizxian_autoAttack(CallbackInfo ci) {
        if (player == null || player.isSpectator()) return;
        if (player.getAttackStrengthScale(0f) < 1.0f) return;

        boolean shouldAttack = false;

        // AUTO_ATTACK 属性（装备授予，免附魔）+ 按住攻击键
        if (player.getAttributeValue(YizAttributes.AUTO_ATTACK.get()) > 0
                 && Minecraft.getInstance().options.keyAttack.isDown()) {
            shouldAttack = true;
        }

        // 必须瞄准实体且在近战范围内，避免空挥浪费冷却或误拆方块
        if (shouldAttack && !isAimingAtAttackableEntity(player)) {
            return;
        }

        if (shouldAttack) {
            startAttack();
        }
    }

    /** 检查玩家是否正在瞄准一个可攻击的实体且在近战范围内（对齐 1.21.1 交互距离：ENTITY_REACH 默认 3.0 + ATTACK_RANGE 镜像）。 */
    private static boolean isAimingAtAttackableEntity(LocalPlayer player) {
        var hit = Minecraft.getInstance().hitResult;
        if (hit == null) return false;
        if (!(hit instanceof EntityHitResult ehr)) return false;
        var entity = ehr.getEntity();
        if (entity == null || entity == player) return false;
        if (!(entity instanceof LivingEntity)) return false;
        double reach = 3.0;
        try {
            var reachInst = player.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
            if (reachInst != null) reach = Math.max(3.0, reachInst.getValue());
        } catch (Throwable ignored) {}
        return player.distanceTo(entity) <= reach;
    }
}
