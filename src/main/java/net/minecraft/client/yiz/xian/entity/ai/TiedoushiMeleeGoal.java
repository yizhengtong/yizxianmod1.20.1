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
    /** 攻击中的重寻路间隔：目标正被击飞推开，需要更紧地跟随。 */
    private static final int ATTACK_REPATH_INTERVAL = 5;

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
        boolean attacking = this.attackCooldown > 0;

        // 移动策略：
        //  · 超出攻击距离 → 寻路接近；
        //  · 攻击中（整套动画播放期间）→ 继续朝目标水平移动。攻击动画里已做脚步位移动画，
        //    所以边打边压上去没有动画违和；否则目标被击飞推开后本体会原地挥空。
        //    （留 1 格贴身余量，避免顶进目标碰撞箱里抖动）
        //  · 其余情况（在攻击距离内且未在攻击）→ 停下。
        if (distSq > range * range || (attacking && distSq > 1.0)) {
            if (this.repathCooldown <= 0) {
                // 攻击中重寻路更密：击飞每个周期会把目标推开约 1.8 格，10 tick 的旧路径会明显滞后
                this.repathCooldown = attacking ? ATTACK_REPATH_INTERVAL : REPATH_INTERVAL;
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
