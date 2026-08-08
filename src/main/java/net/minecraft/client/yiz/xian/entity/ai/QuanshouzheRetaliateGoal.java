package net.minecraft.client.yiz.xian.entity.ai;

import net.minecraft.client.yiz.core.StatusEffectDispatcher;
import net.minecraft.client.yiz.xian.entity.QuanshouzheEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 辖界者反击 Goal（1.20.1 移植版）— 中立生物：不主动索敌，被攻击后锁定攻击者反击。
 * 优先玩家（跳过创造模式/无敌），否则 vanilla 最后攻击者。
 */
public class QuanshouzheRetaliateGoal extends Goal {

    private final QuanshouzheEntity mob;
    private LivingEntity target;

    public QuanshouzheRetaliateGoal(QuanshouzheEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        LivingEntity playerAttacker = mob.getRecentPlayerAttacker();
        if (playerAttacker != null && isValidTarget(playerAttacker)) {
            this.target = playerAttacker;
            return true;
        }
        LivingEntity lastHurt = mob.getLastHurtByMob();
        if (lastHurt != null && isValidTarget(lastHurt)) {
            this.target = lastHurt;
            return true;
        }
        return false;
    }

    private static boolean isValidTarget(LivingEntity e) {
        if (!e.isAlive() || e.isInvulnerable()) return false;
        if (e instanceof net.minecraft.world.entity.player.Player p && p.isCreative()) return false;
        return true;
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
        return t != null && isValidTarget(t)
            && !StatusEffectDispatcher.hasHardControl(mob);
    }
}
