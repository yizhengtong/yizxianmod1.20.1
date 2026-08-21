package net.minecraft.client.yiz.xian.entity;

import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.tool.attribute.EntityAttributeGate;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 山林首者 — 1.20.1 版，原版 ModelPart 渲染 + AnimationDefinition 动画（抡锤子的 Warden 同人实体）。
 *
 * <p>动画：静止 n0 / 移动 a0 / 反击 a1 / 技能 a2 / 普通攻击 a3 / 死亡 a4。
 * 反击只在受伤时触发；技能条上限 30（攻击+2、受伤+1），到 30 释放「撼山震林」。</p>
 */
public class ShanlinshouzheEntity extends YizxianMob {

    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState walkAnimationState = new AnimationState();
    public final AnimationState counterAnimationState = new AnimationState();
    public final AnimationState skillAnimationState = new AnimationState();
    public final AnimationState attackAnimationState = new AnimationState();
    public final AnimationState deathAnimationState = new AnimationState();

    private static final double EXP_REDUCTION_BASE = 40.0;
    private static final double EXP_REDUCTION_EXP = Math.log(2.0) / Math.log(1.0 + 50.0 / EXP_REDUCTION_BASE);

    private static final int SKILL_MAX = 30;
    private static final float SKILL_RADIUS = 6.0f;
    private static final float SKILL_FALLOFF = 0.15f;   // 每向外一格 -15%

    private int meleeCooldown;
    private int skillValue = 0;
    private boolean counterPending = false;
    /** 反击状态结束 tick：反击动画播放期间不再触发新的反击（后续受击走普通攻击动画）。 */
    private int counterActiveUntilTick;

    /** Boss 血条。 */
    private final ServerBossEvent bossEvent = new ServerBossEvent(
        Component.literal("山林首者"), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);

    public ShanlinshouzheEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        // 中立 AI：只主动索敌敌对生物（Monster），不主动攻击玩家/中立/友善生物；同类也不打。
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, false,
            target -> target instanceof Monster && target.getType() != this.getType()));
    }

    // ---- 属性 ----

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 100.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.ATTACK_DAMAGE, 25.0)
            .add(Attributes.ARMOR, 0.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.FOLLOW_RANGE, 35.0)
            .add(YizAttributes.ATTACK_STRENGTH.get(), 0.0)
            .add(YizAttributes.SPELL_POWER.get(), 0.0)
            .add(YizAttributes.GENERIC_DAMAGE.get(), 0.0)
            .add(YizAttributes.MELEE_DAMAGE.get(), 0.0)
            .add(YizAttributes.RANGED_DAMAGE.get(), 0.0)
            .add(YizAttributes.DAMAGE_REDUCTION.get(), 0.0)
            .add(YizAttributes.DAMAGE_BLOCK.get(), 0.0)
            .add(YizAttributes.INVINCIBILITY_MULT.get(), 0.0)
            .add(YizAttributes.DODGE_CHANCE.get(), 0.0)
            .add(YizAttributes.LIFE_STEAL.get(), 0.0)
            .add(YizAttributes.ARMOR.get(), 0.0)
            .add(YizAttributes.SPELL_DEFENSE.get(), 0.0)
            .add(YizAttributes.VITALITY_SEVERANCE_RATE.get(), 0.0)
            .add(YizAttributes.VITALITY_SEVERANCE_TIME.get(), 0.0)
            .add(YizAttributes.FIRST_DREAM.get(), 0.0)
            .add(YizAttributes.CONDUCTION_CAP.get(), 0.0)
            .add(YizAttributes.SECURE_PULSE.get(), 0.0);
    }

    @Override
    protected void applyEntityAttributes() {
        applyVanillaDifficultyScale();
        setAttr(YizAttributes.ARMOR, "armor", 10.0);
        setAttr(YizAttributes.SPELL_DEFENSE, "spell_defense", 10.0);
        setAttr(YizAttributes.DAMAGE_REDUCTION, "damage_reduction", 15.0);
        setAttr(YizAttributes.DAMAGE_BLOCK, "damage_block", 1.0);
        setAttr(YizAttributes.INVINCIBILITY_MULT, "invincibility_mult", 10.0);
        setAttr(YizAttributes.CONDUCTION_CAP, "conduction_cap", 90.0);
        setAttr(YizAttributes.SECURE_PULSE, "secure_pulse", 1.0);
        // 涨跌多空 = 攻击 × 25% 转换率
        double dream = this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.25;
        setAttr(YizAttributes.FIRST_DREAM, "long_short", dream);
    }

    private void setAttr(net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                         String idKey, double value) {
        EntityAttributeGate.set(this, attr, idKey, value);
        net.minecraft.client.yiz.tool.attribute.AttributeStandardizer.registerStandard(this, attr.get(), idKey, value);
    }

    // ---- 伤害链（双抗/减免/格挡/限伤 + 反击/技能条触发） ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide()) return false;
        if (!net.minecraft.client.yiz.tool.health.SecureHealthClosure.isSecure(this)) return super.hurt(source, amount);
        if (amount <= 0) return false;
        if (this.invulnerableTime > 0) return false;
        long cdTicks = conductionHitCdTicks();
        if (this.lastConductionHitTick != Long.MIN_VALUE && this.level().getGameTime() - this.lastConductionHitTick < cdTicks) {
            return false;
        }
        // vanilla 伤害类型闸门
        var type = source.type();
        var dmgKey = this.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getKey(type);
        if (dmgKey == null || !dmgKey.getNamespace().equals("minecraft")) return false;
        // 护甲/法防指数减免（物理→ARMOR，其余→SPELL_DEFENSE）
        boolean isPhysical = source.is(DamageTypeTags.IS_PROJECTILE)
            || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FALL);
        var expAttr = isPhysical ? YizAttributes.ARMOR : YizAttributes.SPELL_DEFENSE;
        var expInst = this.getAttribute(expAttr.get());
        if (expInst != null && expInst.getValue() > 0) {
            double reduction = 1.0 - Math.pow(1.0 + expInst.getValue() / EXP_REDUCTION_BASE, -EXP_REDUCTION_EXP);
            amount *= (float) (1.0 - Math.min(1.0, reduction));
        }
        float reduced = amount;
        // 百分比减免
        var redInst = this.getAttribute(YizAttributes.DAMAGE_REDUCTION.get());
        if (redInst != null && redInst.getValue() > 0) {
            reduced *= (float) (1.0 - Math.min(1.0, redInst.getValue() / 100.0));
        }
        // 固定格挡
        var blockInst = this.getAttribute(YizAttributes.DAMAGE_BLOCK.get());
        if (blockInst != null && blockInst.getValue() > 0) {
            reduced = Math.max(0, reduced - (float) blockInst.getValue());
        }
        // 传导限伤
        float limited = Math.min(reduced, conductionCap());
        if (limited <= 0) return false;
        float current = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
        float next = Math.max(0, current - limited);
        net.minecraft.client.yiz.tool.health.SecureHealthClosure.setHealth(this, next);
        net.minecraft.client.yiz.tool.health.EntityActuallyHurt.catchSetTrueHealth(this, next);
        this.lastConductionHitTick = this.level().getGameTime();
        this.invulnerableTime = (int) conductionHitCdTicks();
        this.hurtTime = 10;
        this.hurtDuration = 10;
        this.broadcastHurtFlash(source);
        // 反击触发：受击时若不在反击状态内 → 立即排队一次反击（反击动画）。
        // 已经进入反击状态后的受击不再触发新的反击（后续攻击走普通攻击动画）。
        if (source.getEntity() instanceof LivingEntity attacker && this.isValidTarget(attacker)) {
            if (!this.counterPending && this.tickCount >= this.counterActiveUntilTick) {
                this.counterPending = true;
                this.meleeCooldown = 0;   // 立刻反击
                this.counterActiveUntilTick = this.tickCount + 28;   // 反击动画约 550ms 内不再触发
            }
            this.setTarget(attacker);
        }
        this.skillValue = Math.min(SKILL_MAX, this.skillValue + 1);
        if (next <= 0) this.die(source);
        return true;
    }

    private boolean isValidTarget(LivingEntity e) {
        if (e == null || !e.isAlive()) return false;
        if (e instanceof Player p && p.isCreative()) return false;
        return e.getType() != this.getType();
    }

    /** 近战命中额外附加涨跌多空伤害（攻击 × 25%）。 */
    @Override
    public boolean doHurtTarget(Entity target) {
        if (target instanceof LivingEntity lt) {
            double dream = this.getAttributeValue(YizAttributes.FIRST_DREAM.get());
            if (dream > 0) {
                net.minecraft.client.yiz.tool.health.EntityASMUtil.applyDreamDamage(this, lt, dream);
            }
        }
        return super.doHurtTarget(target);
    }

    // ---- 每 tick 逻辑 ----

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.updateAnimationStates();
        } else {
            this.updateMelee();
            // 每秒回血 0.25
            if (this.getHealth() < this.getMaxHealth() && this.isAlive()) {
                this.heal(0.0125f);
            }
            // Boss 血条进度（读权威表，防外部 agent 压 getHealth 影响血条）
            float bossHp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
            float bossMax = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getMaxHealth(this);
            this.bossEvent.setProgress(bossMax > 0 ? bossHp / bossMax : 0);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        this.bossEvent.removeAllPlayers();
        super.die(source);
    }

    private void updateAnimationStates() {
        if (this.isDeadOrDying()) {
            this.deathAnimationState.startIfStopped(this.tickCount);
            this.idleAnimationState.stop();
            this.walkAnimationState.stop();
            this.counterAnimationState.stop();
            this.skillAnimationState.stop();
            this.attackAnimationState.stop();
            return;
        }
        // 客户端 deltaMovement 对服务端驱动的 Mob 不可靠（正常 Mob 不发速度包），
        // 改用 walkAnimation（由 calculateEntityAnimation 按实际位置差更新）判断移动，
        // 避免 idle/walk 状态来回切换导致原地抽搐。
        boolean moving = this.walkAnimation.speed() > 0.05F;
        if (moving) {
            this.walkAnimationState.startIfStopped(this.tickCount);
            this.idleAnimationState.stop();
        } else {
            this.idleAnimationState.startIfStopped(this.tickCount);
            this.walkAnimationState.stop();
        }
    }

    /** 服务端：近战（普通攻击/反击），技能条累积与释放。 */
    private void updateMelee() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        this.getLookControl().setLookAt(target, 30f, 30f);
        double attackRange = 4.0;   // 攻击距离 +1（原 3.0）
        double distSq = this.distanceToSqr(target);
        if (distSq > attackRange * attackRange) {
            this.getNavigation().moveTo(target, 1.0);
        }
        if (this.meleeCooldown > 0) this.meleeCooldown--;
        if (this.meleeCooldown <= 0 && distSq <= attackRange * attackRange) {
            this.doHurtTarget(target);
            this.meleeCooldown = 20;
            // 动画：反击（受伤触发）或普通攻击
            this.level().broadcastEntityEvent(this, this.counterPending ? (byte) 58 : (byte) 60);
            this.counterPending = false;
            // 技能条 +2
            this.skillValue = Math.min(SKILL_MAX, this.skillValue + 2);
        }
        // 技能条满 30 → 释放技能
        if (this.skillValue >= SKILL_MAX) {
            this.releaseSkill();
        }
    }

    /** 技能「撼山震林」：以自身为中心 R6 AOE，每向外一格效果 -15%。 */
    private void releaseSkill() {
        this.skillValue = 0;
        this.level().broadcastEntityEvent(this, (byte) 59);
        AABB area = this.getBoundingBox().inflate(SKILL_RADIUS);
        // 中立 AI：技能只打敌对生物 + 当前反击目标（被玩家攻击时允许技能命中该玩家），
        // 不误伤旁观玩家/中立/友善生物，同类也不打。
        LivingEntity currentTarget = this.getTarget();
        for (LivingEntity t : this.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != this && e.getType() != this.getType()
                        && (e instanceof Monster || e == currentTarget))) {
            double dist = this.distanceTo(t);
            if (dist > SKILL_RADIUS) continue;
            float falloff = Math.max(0.0f, 1.0f - SKILL_FALLOFF * (float) dist);
            // 200%伤害(50 vanilla) + 50%转换率×伤害(25 true) + 目标最大生命 25%
            float vanillaDmg = (50.0f + t.getMaxHealth() * 0.25f) * falloff;
            float trueDmg = 25.0f * falloff;
            t.hurt(t.damageSources().mobAttack(this), vanillaDmg);
            net.minecraft.client.yiz.tool.health.EntityASMUtil.applyDreamDamage(this, t, trueDmg);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 58) {
            this.counterAnimationState.start(this.tickCount);
            this.walkAnimationState.stop();
            this.idleAnimationState.stop();
            this.attackAnimationState.stop();
        } else if (id == 59) {
            this.skillAnimationState.start(this.tickCount);
            this.walkAnimationState.stop();
            this.idleAnimationState.stop();
            this.attackAnimationState.stop();
        } else if (id == 60) {
            this.attackAnimationState.start(this.tickCount);
            this.walkAnimationState.stop();
            this.idleAnimationState.stop();
            this.counterAnimationState.stop();
        } else {
            super.handleEntityEvent(id);
        }
    }
}
