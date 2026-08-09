package net.minecraft.client.yiz.xian.entity.ai;

import net.minecraft.client.yiz.xian.entity.QuanshouzheEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 辖界者近战挥砍 Goal（1.20.1 移植版）— 贴近目标即时结算伤害。
 * 攻击动画 = Warden 关键帧（broadcastEntityEvent byte 4 触发）；
 * 攻击间隔/距离按三阶段形态（getAttackInterval / getAttackRange），狂暴只保留移速加成。
 */
public class QuanshouzheMeleeGoal extends Goal {

    private final QuanshouzheEntity mob;
    private int attackCooldown;

    public QuanshouzheMeleeGoal(QuanshouzheEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
            && this.mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
            && this.mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public void start() {
        this.attackCooldown = 0;
    }

    @Override
    public void tick() {
        if (this.mob.isHeavyAttacking()) {
            if (this.attackCooldown > 0) this.attackCooldown--;
            LivingEntity t = this.mob.getTarget();
            if (t != null) this.mob.getLookControl().setLookAt(t, 30f, 30f);
            return;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null) return;
        this.mob.getLookControl().setLookAt(target, 30f, 30f);
        double attackRange = this.mob.getAttackRange();
        double distSq = this.mob.distanceToSqr(target);
        if (distSq > attackRange * attackRange) {
            this.mob.getNavigation().moveTo(target, 1.0);
        }
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
            return;
        }
        if (distSq <= attackRange * attackRange) {
            this.attackCooldown = this.mob.getAttackInterval();
            this.mob.swing(InteractionHand.MAIN_HAND);
            this.mob.attackTarget(target);
        }
    }
}
