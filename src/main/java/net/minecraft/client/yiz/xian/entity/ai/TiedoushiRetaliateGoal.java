package net.minecraft.client.yiz.xian.entity.ai;

import net.minecraft.client.yiz.core.StatusEffectDispatcher;
import net.minecraft.client.yiz.xian.entity.TiedoushiEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 铁斗士反击 Goal —— 中立单位：不主动索敌，被攻击后锁定攻击者反击。
 * 跳过创造模式/无敌目标；受硬控时中断。
 */
public class TiedoushiRetaliateGoal extends Goal {

    private final TiedoushiEntity mob;
    private LivingEntity target;

    public TiedoushiRetaliateGoal(TiedoushiEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        LivingEntity lastHurt = mob.getLastHurtByMob();
        if (lastHurt != null && isValidTarget(lastHurt)) {
            this.target = lastHurt;
            return true;
        }
        return false;
    }

    private boolean isValidTarget(LivingEntity e) {
        if (!e.isAlive() || e.isInvulnerable()) return false;
        if (e instanceof net.minecraft.world.entity.player.Player p && p.isCreative()) return false;
        // 超出仇恨范围（FOLLOW_RANGE）不反应
        double range = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        return mob.distanceToSqr(e) <= range * range;
    }

    @Override
    public void start() {
        mob.setTarget(this.target);
        this.target = null;
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = mob.getTarget();
        return t != null && isValidTarget(t) && !StatusEffectDispatcher.hasHardControl(mob);
    }
}
