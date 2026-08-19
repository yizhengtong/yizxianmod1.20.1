package net.minecraft.client.yiz.xian.entity.ai;

import net.minecraft.client.yiz.xian.entity.XieyulongEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 邪狱龙飞行追击目标：飞行且有存活目标时，把目标位置（半身高偏移）交给飞行移动控制。
 */
public class XieyulongFlightPursuitGoal extends Goal {

    private final XieyulongEntity dragon;
    private final double speedModifier;

    public XieyulongFlightPursuitGoal(XieyulongEntity dragon, double speedModifier) {
        this.dragon = dragon;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.dragon.isFlying()) {
            return false;
        }
        LivingEntity target = this.dragon.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.dragon.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.dragon.getTarget();
        if (target == null) {
            return;
        }
        this.dragon.getMoveControl().setWantedPosition(
            target.getX(), target.getY() + (double) target.getBbHeight() * 0.5, target.getZ(),
            this.speedModifier);
    }

    @Override
    public void stop() {
        this.dragon.getNavigation().stop();
    }
}
