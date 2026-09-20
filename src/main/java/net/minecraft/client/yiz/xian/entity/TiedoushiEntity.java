package net.minecraft.client.yiz.xian.entity;

import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.tool.health.EntityASMUtil;
import net.minecraft.client.yiz.tool.health.ManaTracker;
import net.minecraft.client.yiz.xian.entity.ai.TiedoushiMeleeGoal;
import net.minecraft.client.yiz.xian.entity.ai.TiedoushiRetaliateGoal;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 铁斗士 —— 三费棋子，纯近战 + 满蓝自动释放 skill1。
 *
 * <p>属性取自走棋三费标准表：80 血 / 12 攻 / 0.5 每秒回血 / 格挡 0.4 / 减伤 10 / 护甲 8 /
 * 法防 8 / 传导限伤 90% / 攻击距离 3 / 移速 0.23 / 仇恨距离 24。血量、攻击、回血随星级倍率放大。</p>
 *
 * <p>蓝条：上限 140、每秒回 8、每次攻击回 12；满蓝立即以自身为中心释放 skill1。
 * 攻击与技能都在动画关键帧出伤，不按挥砍瞬间结算。</p>
 */
public class TiedoushiEntity extends YizxianMob {

    // ── 三费标准模板（applyVanillaDifficultyScale / applyEntityAttributes 用）──
    private static final double TEMPLATE_MAX_HEALTH = 80.0;
    private static final double TEMPLATE_ATTACK = 12.0;
    private static final double TEMPLATE_MOVE_SPEED = 0.23;
    private static final double TEMPLATE_ATTACK_RANGE = 3.0;
    private static final double TEMPLATE_FOLLOW_RANGE = 60.0;
    private static final double TEMPLATE_REGEN = 0.5;

    // ── 蓝条与技能 ──
    private static final float MANA_MAX = 140.0F;
    private static final float MANA_REGEN = 8.0F;
    private static final float MANA_PER_ATTACK = 12.0F;
    private static final float MANA_DECAY_PER_SECOND = 10.0F; // 非仇恨状态每秒衰减
    private static final float SKILL_RADIUS = 12.0F;
    private static final double SKILL_FALLOFF_PER_BLOCK = 0.08;
    private static final double SKILL_ATTACK_MULT = 2.5;
    private static final double SKILL_DREAM_MOB = 0.50;
    private static final double SKILL_DREAM_PLAYER = 0.25;

    // ── 攻击伤害关键帧（tick，自整套攻击动画开始算：0.75s / 1.4s / 2.1s）──
    private static final int[] ATTACK_DAMAGE_TICKS = {15, 28, 42};
    /** 三次伤害倍率：第 1 段 ×1.4，第 2/3 段 ×1.25。 */
    private static final double[] ATTACK_DAMAGE_MULT = {1.4, 1.25, 1.25};
    // 技能：动画第 1 秒（20t）开始，每 tick 结算一次，持续 1 秒（20 次）
    private static final int SKILL_DAMAGE_START_TICK = 20;
    private static final int SKILL_DAMAGE_DURATION_TICK = 20;

    // ── 攻击范围 ──
    /** 第 1/3 段：以自身为中心半径 4 格。 */
    private static final double ATTACK_RADIUS = 4.0;
    /** 第 2 段：朝目标方向长 4 格、宽 3 格（半宽 1.5）、高 3 格。 */
    private static final double ATTACK2_DEPTH = 4.0;
    private static final double ATTACK2_HALF_WIDTH = 1.5;
    private static final double ATTACK_HEIGHT = 3.0;

    /** 整套攻击动画 2.5s（50t），间隔取 51t：上一套播完下一套立即接上。 */
    private static final int ATTACK_INTERVAL = 51;
    private static final byte EVENT_ATTACK = 70;
    private static final byte EVENT_SKILL = 61;

    // 动画状态（客户端渲染驱动）
    public final AnimationState idleState = new AnimationState();
    public final AnimationState walkState = new AnimationState();
    public final AnimationState chaseState = new AnimationState();
    public final AnimationState attackState = new AnimationState();
    public final AnimationState skillState = new AnimationState();

    private boolean manaInitialized;
    private boolean attackPending;
    private int attackDamageCursor;
    private int attackBaseTick = -1;
    /** 当前正在施放的技能（由战斗组件的 skillId 选出）与已持续 tick。 */
    private net.minecraft.client.yiz.creature.CreatureSkill activeSkill;
    private int skillElapsed;

    /** 同步数据懒加载（避免类加载期 defineId 抢占其它实体的数据 id）。 */
    private static final class DataHolder {
        static final EntityDataAccessor<Integer> ATTACK_ANIM =
            SynchedEntityData.defineId(TiedoushiEntity.class, EntityDataSerializers.INT);
    }

    public TiedoushiEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 中立单位参照原版铁傀儡：用 Mob 基础模板而非 Monster
        AttributeSupplier.Builder builder = Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, TEMPLATE_MAX_HEALTH)
            .add(Attributes.MOVEMENT_SPEED, TEMPLATE_MOVE_SPEED)
            .add(Attributes.ATTACK_DAMAGE, TEMPLATE_ATTACK)
            .add(Attributes.ARMOR, 0.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.0)
            .add(Attributes.FOLLOW_RANGE, TEMPLATE_FOLLOW_RANGE);
        addStandardCustomAttributes(builder);
        addManaAttributes(builder);
        return builder;
    }

    @Override
    protected double yizxianMaxHealthTemplate() {
        return TEMPLATE_MAX_HEALTH;
    }

    /** 三费标准属性分配（生成/加载后第一 tick 由基类 aiStep 调一次）。 */
    @Override
    protected void applyEntityAttributes() {
        applyVanillaDifficultyScale();
        setAttr(YizAttributes.ATTACK_STRENGTH, "attack_strength", 0.0);
        setAttr(YizAttributes.SPELL_POWER, "spell_power", 100.0);
        setAttr(YizAttributes.DAMAGE_BLOCK, "damage_block", scaleDifficulty(0.4));
        setAttr(YizAttributes.DAMAGE_REDUCTION, "damage_reduction", scaleDifficulty(10.0));
        setAttr(YizAttributes.ARMOR, "armor", scaleDifficulty(8.0));
        setAttr(YizAttributes.SPELL_DEFENSE, "spell_defense", scaleDifficulty(8.0));
        setAttr(YizAttributes.CONDUCTION_CAP, "conduction_cap", 90.0);
        setAttr(YizAttributes.INVINCIBILITY_MULT, "invincibility_mult", 16.0);
        setAttr(YizAttributes.LIFE_REGEN_RATE, "life_regen_rate", TEMPLATE_REGEN);
        setAttr(YizAttributes.MAX_MANA, "max_mana", MANA_MAX);
        setAttr(YizAttributes.MANA_REGEN, "mana_regen", MANA_REGEN);
    }

    private void setAttr(net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                         String idKey, double value) {
        net.minecraft.client.yiz.tool.attribute.EntityAttributeGate.set(this, attr, idKey, value);
        net.minecraft.client.yiz.tool.attribute.AttributeStandardizer.registerStandard(this, attr.get(), idKey, value);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new TiedoushiMeleeGoal(this));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        // 中立单位参照原版铁傀儡：受击反击 + 主动仇恨所有敌对生物（Enemy 接口，不含本模组棋子）
        this.targetSelector.addGoal(1, new TiedoushiRetaliateGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 5, false, false,
            e -> e instanceof Enemy));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DataHolder.ATTACK_ANIM, 0);
    }

    public double getAttackRange() {
        // 组件优先（数据包/原型可覆盖），未配置回退实体模板值
        return net.minecraft.client.yiz.creature.CreatureProfileRegistry.combatOf(this)
            .rangeOr(TEMPLATE_ATTACK_RANGE);
    }

    /** 整套攻击动画时长 +1 tick，播完立即接下一套；可由战斗组件覆盖。 */
    public int getAttackInterval() {
        return net.minecraft.client.yiz.creature.CreatureProfileRegistry.combatOf(this)
            .intervalOr(ATTACK_INTERVAL);
    }

    /** 近战一次：播放整套三段连贯攻击动画 + 回蓝；三次伤害在动画关键帧结算（见 aiStep）。 */
    public void performAttack() {
        if (this.level().isClientSide()) return;
        this.level().broadcastEntityEvent(this, EVENT_ATTACK);
        this.swing(InteractionHand.MAIN_HAND);
        this.attackPending = true;
        this.attackDamageCursor = 0;
        this.attackBaseTick = this.tickCount;
        ManaTracker.add(this, MANA_PER_ATTACK);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) return;

        // 出生属性/星级/满血已由基类 aiStep 一次跑完（applyEntityAttributes → registerSecureHealth
        // → applyChessStarIfNeeded）。此处不再重跑 applyVanillaDifficultyScale：那会把星级放大后的
        // max_health 打回 1 星基准并覆写 AttributeStandardizer 标准，与 enforceSecureHealthState
        // 每 tick 拉回星级值互相对打（max_health 80↔180 每秒震荡）。

        // 初始蓝从 0 起算（ManaTracker.defaultEmpty 已保证无记录即为 0，这里显式落一条记录）
        if (!this.manaInitialized) {
            this.manaInitialized = true;
            ManaTracker.setZero(this);
        }

        // 攻击伤害关键帧结算：整套动画三次触发点，按序逐个结算
        if (this.attackPending) {
            while (this.attackDamageCursor < ATTACK_DAMAGE_TICKS.length
                && this.tickCount >= this.attackBaseTick + ATTACK_DAMAGE_TICKS[this.attackDamageCursor]) {
                this.dealAttackDamage(this.attackDamageCursor);
                this.attackDamageCursor++;
            }
            if (this.attackDamageCursor >= ATTACK_DAMAGE_TICKS.length) {
                this.attackPending = false;
            }
        }

        // 技能：由技能注册表按战斗组件的 skillId 派发，每 tick 驱动（具体行为在技能实现里）
        var combatSpec = net.minecraft.client.yiz.creature.CreatureProfileRegistry.combatOf(this);
        if (this.activeSkill != null) {
            if (!this.activeSkill.tick(this, combatSpec, this.skillElapsed++)) {
                this.activeSkill.stop(this);
                this.activeSkill = null;
            }
        }

        // 仇恨状态：有存活的攻击目标（中立单位被攻击后锁定攻击者）
        boolean hasAggro = this.getTarget() != null && this.getTarget().isAlive();

        // 非仇恨不回蓝：撤销基类本 tick 的 MANA_REGEN 回蓝（MANA_REGEN × 0.05/tick），改为每秒衰减 10
        if (!hasAggro) {
            ManaTracker.add(this, -(MANA_REGEN * 0.05F) - (MANA_DECAY_PER_SECOND / 20.0F));
        }

        // 满蓝且处于仇恨状态 → 选中技能并开始施放（满蓝清零，从零继续累加）
        if (this.activeSkill == null && hasAggro) {
            float max = ManaTracker.getMax(this);
            if (max > 0.0F && ManaTracker.get(this) >= max) {
                var skill = net.minecraft.client.yiz.creature.CreatureSkills.fromSpec(combatSpec);
                if (skill != null && skill.start(this, combatSpec)) {
                    ManaTracker.setZero(this);
                    this.activeSkill = skill;
                    this.skillElapsed = 0;
                }
            }
        }
    }

    /** 按段结算伤害：1/3 段=自身半径 4 格；2 段=朝目标方向长 4 宽 3 高 3。倍率见 ATTACK_DAMAGE_MULT。 */
    private void dealAttackDamage(int stage) {
        double atk = this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (atk <= 0.0) return;
        float dmg = (float) (atk * ATTACK_DAMAGE_MULT[stage]);
        if (dmg <= 0.0F) return;
        List<LivingEntity> targets = (stage == 1)
            ? inFront(ATTACK2_DEPTH, ATTACK2_HALF_WIDTH, ATTACK_HEIGHT)
            : nearby(ATTACK_RADIUS);
        for (LivingEntity t : targets) {
            t.hurt(this.damageSources().mobAttack(this), dmg);
        }
    }

    /** skill1 结算：12 格半径，每远 1 格衰减 8%；2.5×攻击力 + 目标最大生命 50%（玩家 25%）真伤。
     *  ratio = 本次结算占总量的比例（持续伤害按 tick 分批，合计 1.0，总输出与一次性相同）。
     *  半径/倍率/衰减均可由技能参数覆盖，缺省用本实体常量。
     *  普通伤害走破无敌帧通道，否则每 tick 的等量伤害会被 20 tick 无敌帧吞掉。 */
    private void dealSkillDamage(float ratio, net.minecraft.client.yiz.creature.CombatSpec spec) {
        double atk = this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double radius = spec.paramOr("radius", SKILL_RADIUS);
        double falloff = spec.paramOr("falloff", SKILL_FALLOFF_PER_BLOCK);
        double mult = spec.paramOr("attack_mult", SKILL_ATTACK_MULT);
        double dreamMob = spec.paramOr("dream_mob", SKILL_DREAM_MOB);
        double dreamPlayer = spec.paramOr("dream_player", SKILL_DREAM_PLAYER);
        for (LivingEntity t : nearby(radius)) {
            double factor = 1.0 - Math.sqrt(this.distanceToSqr(t)) * falloff;
            if (factor <= 0.0) continue;
            float dmg = (float) (mult * atk * factor * ratio);
            if (dmg > 0.0F) {
                net.minecraft.client.yiz.api.YizModQZKAPI.pierceInvulnerabilityDamage(t, dmg, this);
            }
            double pct = (t instanceof Player ? dreamPlayer : dreamMob) * factor;
            double amount = t.getMaxHealth() * pct * ratio;
            if (amount > 0.0) {
                EntityASMUtil.applyProportionalDreamDamage(this, t, amount);
            }
        }
    }

    /**
     * 铁斗士 skill1：动画第 1 秒起每 tick 一跳、持续 1 秒（分批结算，总输出与一次性相同）。
     *
     * <p>无状态实现：施放进度由实体的 skillElapsed 提供。开始/持续时间可由技能参数
     * {@code start_tick} / {@code duration_tick} 覆盖。</p>
     */
    public static final class Skill1 implements net.minecraft.client.yiz.creature.CreatureSkill {
        public static final net.minecraft.resources.ResourceLocation ID =
            new net.minecraft.resources.ResourceLocation("yizxianmod", "tiedoushi_skill1");

        @Override
        public net.minecraft.resources.ResourceLocation id() {
            return ID;
        }

        @Override
        public boolean start(LivingEntity caster, net.minecraft.client.yiz.creature.CombatSpec spec) {
            caster.level().broadcastEntityEvent(caster, EVENT_SKILL);
            return true;
        }

        @Override
        public boolean tick(LivingEntity caster, net.minecraft.client.yiz.creature.CombatSpec spec, int elapsed) {
            if (!(caster instanceof TiedoushiEntity entity)) return false;
            int startTick = (int) spec.paramOr("start_tick", SKILL_DAMAGE_START_TICK);
            int duration = (int) spec.paramOr("duration_tick", SKILL_DAMAGE_DURATION_TICK);
            if (duration <= 0) return false;
            int t = elapsed - startTick;
            if (t >= 0 && t < duration) {
                entity.dealSkillDamage(1.0F / duration, spec);
            }
            return elapsed < startTick + duration;
        }
    }

    /** 自身中心半径 r 内的存活实体（不含自己）。 */
    private List<LivingEntity> nearby(double r) {
        return this.level().getEntitiesOfClass(LivingEntity.class,
            this.getBoundingBox().inflate(r),
            e -> e != this && e.isAlive() && this.distanceTo(e) <= r);
    }

    /** 前方 depth 格、左右各 halfWidth 格、高 height 格的矩形范围（按朝向判定，不用轴对齐盒近似）。 */
    private List<LivingEntity> inFront(double depth, double halfWidth, double height) {
        Vec3 look = this.getLookAngle();
        Vec3 fwd = new Vec3(look.x, 0.0, look.z);
        if (fwd.lengthSqr() < 1.0E-6) fwd = new Vec3(0.0, 0.0, 1.0);
        fwd = fwd.normalize();
        Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
        final Vec3 f = fwd;
        final Vec3 r = right;
        return this.level().getEntitiesOfClass(LivingEntity.class,
            this.getBoundingBox().inflate(depth + halfWidth, height, depth + halfWidth),
            e -> {
                if (e == this || !e.isAlive()) return false;
                Vec3 d = e.position().subtract(this.position());
                double along = d.x * f.x + d.z * f.z;
                double lateral = Math.abs(d.x * r.x + d.z * r.z);
                return along >= -0.5 && along <= depth && lateral <= halfWidth
                    && d.y >= -1.0 && d.y <= height;
            });
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_ATTACK) {
            this.attackState.stop();
            this.attackState.start(this.tickCount);
            return;
        }
        if (id == EVENT_SKILL) {
            this.skillState.stop();
            this.skillState.start(this.tickCount);
            return;
        }
        super.handleEntityEvent(id);
    }
}
