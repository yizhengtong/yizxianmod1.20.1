package net.minecraft.client.yiz.xian.item.legacy;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 内置攻速武器的无敌帧处理。
 *
 * <p>之前用 {@code COOLDOWN_REDUCTION} 属性时，前置库 {@code InvulnBreakHandler} 会在
 * {@code LivingEntity#hurt} HEAD 把目标无敌帧清零。改为物品内置攻速后属性没了，
 * 目标重新受 0.5 秒无敌帧限制，DPS 会明显下降。</p>
 *
 * <p>这里只对「极尽钻石剑」清零目标无敌帧；普通怀旧剑严格按 1.8 保留 0.5 秒无敌帧，
 * 不做任何额外增强。</p>
 */
public final class LegacyIFrames {

    private LegacyIFrames() {
    }

    public static void breakFor(DamageSource source, LivingEntity target) {
        if (target == null || target.invulnerableTime <= 0) {
            return;
        }
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity living)) {
            return;
        }
        if (living.getMainHandItem().getItem() instanceof LegacyExtremeDiamondSwordItem) {
            target.invulnerableTime = 0;
        }
    }
}
