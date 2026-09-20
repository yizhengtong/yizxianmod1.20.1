package net.minecraft.client.yiz.xian.entity.ai;

import net.minecraft.client.yiz.xian.entity.TiedoushiEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 铁斗士近战 Goal —— 贴近目标按固定间隔结算一次攻击（动画 + 伤害 + 回蓝）。
 *
 * <p>本 Goal 必须每 tick 收到 tick()：原版 Mob.serverAiStep 会按
 * (serverTickCount + entityId) % 2 隔 tick 才走 goalSelector.tick()，其余 tick 只走
 * tickRunningGoals(false)，而后者仅 tick requiresUpdateEveryTick() 为 true 的 Goal。
 * 若把节流写成 mob.tickCount % N == 0，约一半实体永远命中不到该条件 → 从不 moveTo →
 * 原地发呆；同时 attackCooldown 每 2 tick 才减 1，攻击间隔翻倍。</p>
 */
public class TiedoushiMeleeGoal extends Goal {

    /** 重新寻路间隔（tick）：过密会反复重置寻路器导致原地踏步。 */
    private static final int REPATH_INTERVAL = 10;

    private final TiedoushiEntity mob;
    private int attackCooldown;
    private int repathCooldown;

    public TiedoushiMeleeGoal(TiedoushiEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        this.attackCooldown = 0;
        this.repathCooldown = 0;
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) return;
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double range = this.mob.getAttackRange();
        double distSq = this.mob.distanceToSqr(target);
        if (distSq > range * range) {
            if (this.repathCooldown <= 0) {
                this.repathCooldown = REPATH_INTERVAL;
                this.mob.getNavigation().moveTo(target, 1.2);
            }
        } else if (!this.mob.getNavigation().isDone()) {
            this.mob.getNavigation().stop();
        }
        if (this.repathCooldown > 0) {
            this.repathCooldown--;
        }
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
            return;
        }
        if (distSq <= range * range) {
            // 间隔取「即将播放的那段动画」的长度，保证 1→2→3→4 首尾衔接
            this.attackCooldown = this.mob.getAttackInterval();
            this.mob.performAttack();
        }
    }
}
