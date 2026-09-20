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

    // ── 向前击飞（普攻命中按概率把目标沿「铁斗士→目标」连线抛出）──
    /** 击飞触发概率(%)。 */
    private static final double LAUNCH_CHANCE = 100.0;
    /** 击飞高度(格)：抛物线顶点相对起飞点的高度。 */
    private static final double LAUNCH_HEIGHT = 2.0;
    /** 击飞水平(格)：沿连线推出的距离。 */
    private static final double LAUNCH_DISTANCE = 1.8;
    /** 击飞时间(tick)：上抛 + 下落总时长（顶点在 2/3 处）。1.2 秒 = 24 tick。 */
    private static final double LAUNCH_TIME = 24.0;

    // ── 战斗状态移速加成（贴脸衔接连续攻击；脱战移除）──
    private static final double COMBAT_SPEED_BONUS = 0.15;
    private static final java.util.UUID COMBAT_SPEED_ID = java.util.UUID.nameUUIDFromBytes(
        ("yizxianmod:tiedoushi_combat_speed").getBytes(java.nio.charset.StandardCharsets.UTF_8));

    // ── 怒击：连续攻击同一目标时攻速递增（每次 +4%，最多 +40%），切换锁定目标归零 ──
    /** 每层攻速加成（百分比）。 */
    private static final double RAGE_SPEED_PER_HIT = 0.04;
    /** 最大层数 = 10 层 → 攻速 +40%。 */
    private static final int RAGE_MAX_STACKS = 10;
    private java.util.UUID rageTargetUuid;
    private int rageStacks;

    // ── 蓝条与技能 ──
    private static final float MANA_MAX = 140.0F;    private static final float MANA_REGEN = 8.0F;
    private static final float MANA_PER_ATTACK = 12.0F;
    private static final float MANA_DECAY_PER_SECOND = 10.0F; // 非仇恨状态每秒衰减
    private static final float SKILL_RADIUS = 12.0F;
    private static final double SKILL_FALLOFF_PER_BLOCK = 0.08;
    private static final double SKILL_ATTACK_MULT = 2.5;
    private static final double SKILL_DREAM_MOB = 0.50;
    // 注：原先还有 SKILL_DREAM_PLAYER（对玩家 25% 多空真伤）——已按需求移除，玩家只吃普通伤害

    // ── 攻击/动画节奏：整体比原始 Blockbench 动画快 30%（动画侧由 TiedoushiModel 的
    //    ANIM_SPEED 提速，此处所有 tick 数按 1.3 折算，保证伤害关键帧仍落在对应动作上）──
    /** 攻击/动画提速倍率（与 TiedoushiModel.ANIM_SPEED 必须一致）。 */
    private static final double SPEED_SCALE = 1.3;

    // ── 攻击伤害关键帧（tick，自整套攻击动画开始算；原 15/28/42 按 1.3 折算）──
    private static final int[] ATTACK_DAMAGE_TICKS = {12, 22, 32};
    /** 三次伤害倍率：第 1 段 ×1.4，第 2/3 段 ×1.25。 */
    private static final double[] ATTACK_DAMAGE_MULT = {1.4, 1.25, 1.25};
    /** 多空伤害占比：双轨的第二轨 = 普通伤害 × 该比例（对齐辖界者的「攻击力 × 比例」写法）。 */
    private static final float DREAM_DAMAGE_RATIO = 0.4F;
    // 技能：动画第 0.75 秒（15t）开始，每 tick 结算一次，持续 0.75 秒（15 次）
    private static final int SKILL_DAMAGE_START_TICK = 15;
    private static final int SKILL_DAMAGE_DURATION_TICK = 15;

    // ── 攻击范围 ──
    /** 第 1/3 段：以自身为中心半径 4 格。 */
    private static final double ATTACK_RADIUS = 4.0;
    /** 第 2 段：朝目标方向长 4 格、宽 3 格（半宽 1.5）、高 3 格。 */
    private static final double ATTACK2_DEPTH = 4.0;
    private static final double ATTACK2_HALF_WIDTH = 1.5;
    private static final double ATTACK_HEIGHT = 3.0;

    /** 整套攻击动画 2.5s（50t），提速 30% 后约 38.5t；间隔取 39t：上一套播完下一套立即接上。 */
    private static final int ATTACK_INTERVAL = 39;
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
        /** 怒击层数（0~10），同步给客户端用于动画同步提速。 */
        static final EntityDataAccessor<Integer> RAGE_STACKS =
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
            .add(Attributes.FOLLOW_RANGE, TEMPLATE_FOLLOW_RANGE)
            // 向前击飞所需属性：EntityAttributeGate 不会补建缺失实例，必须在此挂载
            .add(net.minecraft.client.yiz.attribute.YizAttributes.KNOCKBACK_ATTACK.get(), 0.0)
            .add(net.minecraft.client.yiz.attribute.YizAttributes.KNOCKBACK_HEIGHT.get(), 0.0)
            .add(net.minecraft.client.yiz.attribute.YizAttributes.KNOCKBACK_DISTANCE.get(), 0.0)
            .add(net.minecraft.client.yiz.attribute.YizAttributes.KNOCKBACK_TIME.get(), 0.0);
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
        // 向前击飞：普攻命中按概率把目标沿连线抛出（高度/水平/时间共同决定弹道）
        setAttr(YizAttributes.KNOCKBACK_ATTACK, "knockback_attack", LAUNCH_CHANCE);
        setAttr(YizAttributes.KNOCKBACK_HEIGHT, "knockback_height", LAUNCH_HEIGHT);
        setAttr(YizAttributes.KNOCKBACK_DISTANCE, "knockback_distance", LAUNCH_DISTANCE);
        setAttr(YizAttributes.KNOCKBACK_TIME, "knockback_time", LAUNCH_TIME);
    }

    private void setAttr(net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                         String idKey, double value) {
        net.minecraft.client.yiz.tool.attribute.EntityAttributeGate.set(this, attr, idKey, value);
        net.minecraft.client.yiz.tool.attribute.AttributeStandardizer.registerStandard(this, attr.get(), idKey, value);
    }

    /**
     * 战斗状态移速加成：进入战斗（有存活目标）时挂上移速 modifier，脱战移除。
     *
     * <p>modifier 名用 {@code yizxianmod:} 前缀 + 固定 UUID：前缀让 20 tick 的属性审计把它当
     * "家族自身 modifier"保留（见 {@code AttributeStandardizer.FAMILY_MODIFIER_PREFIXES}），
     * 固定 UUID 保证幂等、不会重复叠加。MOVEMENT_SPEED 本身未注册进审计表
     * （{@code applyVanillaDifficultyScale} 只注册 MAX_HEALTH），因此不会被判为外部篡改。</p>
     */
    private void applyCombatSpeed(boolean inCombat) {
        var inst = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) return;
        boolean applied = inst.getModifier(COMBAT_SPEED_ID) != null;
        if (inCombat == applied) return;
        if (inCombat) {
            inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                COMBAT_SPEED_ID, "yizxianmod:tiedoushi_combat_speed",
                COMBAT_SPEED_BONUS, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        } else {
            inst.removeModifier(COMBAT_SPEED_ID);
        }
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
        this.entityData.define(DataHolder.RAGE_STACKS, 0);
    }

    public double getAttackRange() {
        // 组件优先（数据包/原型可覆盖），未配置回退实体模板值
        return net.minecraft.client.yiz.creature.CreatureProfileRegistry.combatOf(this)
            .rangeOr(TEMPLATE_ATTACK_RANGE);
    }

    /** 整套攻击动画时长 +1 tick，播完立即接下一套；可由战斗组件覆盖，再按怒击层数提速。 */
    public int getAttackInterval() {
        int base = net.minecraft.client.yiz.creature.CreatureProfileRegistry.combatOf(this)
            .intervalOr(ATTACK_INTERVAL);
        // 怒击：攻速 ×(1 + 4%×层数)，最多 ×1.4 → 间隔按同倍率缩短
        double speed = 1.0 + RAGE_SPEED_PER_HIT * this.rageStacks;
        return Math.max(6, (int) Math.round(base / speed));
    }

    /** 当前怒击层数（0~10）。客户端读同步值（用于动画同步提速），服务端读本字段。 */
    public int getRageStacks() {
        return this.level().isClientSide()
            ? this.entityData.get(DataHolder.RAGE_STACKS)
            : this.rageStacks;
    }

    /**
     * 怒击层数维护：对<b>同一目标</b>连续攻击每层 +4% 攻速（上限 10 层 = +40%）；
     * <b>切换锁定目标归零</b>（新目标的第一次攻击从 0 层重新累积）。
     */
    private void updateRageStacks() {
        net.minecraft.world.entity.LivingEntity target = this.getTarget();
        if (target == null) return;
        if (target.getUUID().equals(this.rageTargetUuid)) {
            if (this.rageStacks < RAGE_MAX_STACKS) this.rageStacks++;
        } else {
            this.rageTargetUuid = target.getUUID();
            this.rageStacks = 0;
        }
        this.entityData.set(DataHolder.RAGE_STACKS, this.rageStacks);
    }

    /** 近战一次：播放整套三段连贯攻击动画 + 回蓝；三次伤害在动画关键帧结算（见 aiStep）。 */
    public void performAttack() {
        if (this.level().isClientSide()) return;
        this.updateRageStacks();
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

        // 战斗状态移速加成：贴脸衔接连续攻击（脱战即移除，不影响巡逻/待机手感）
        applyCombatSpeed(hasAggro);

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
            // 双轨伤害（同辖界者：普通 hurt + 涨跌多空直改）：
            // ① 普通伤害 = 原数值，走破无敌帧通道（三段伤害相隔约 10 tick，原版 20 tick 无敌帧会吃掉后两段）
            net.minecraft.client.yiz.api.YizModQZKAPI.pierceInvulnerabilityDamage(t, dmg, this);
            // ② 多空伤害 = 普通伤害 × 40%（直改真实血量：对无血量槽/走外部数值通道的第三方生物同样生效）
            //    ⚠️ 对玩家只造成普通伤害，不施加多空直改
            if (t instanceof net.minecraft.world.entity.player.Player) continue;
            net.minecraft.client.yiz.tool.health.EntityASMUtil.applyDreamDamage(
                this, t, dmg * DREAM_DAMAGE_RATIO);
        }
    }

    /** skill1 结算：12 格半径，每远 1 格衰减 8%；2.5×攻击力 + 目标最大生命 50% 真伤。
     *  ratio = 本次结算占总量的比例（持续伤害按 tick 分批，合计 1.0，总输出与一次性相同）。
     *  半径/倍率/衰减均可由技能参数覆盖，缺省用本实体常量。
     *  普通伤害走破无敌帧通道，否则每 tick 的等量伤害会被 20 tick 无敌帧吞掉。
     *  ⚠️ 对玩家只造成普通伤害，不施加任何多空直改。 */
    private void dealSkillDamage(float ratio, net.minecraft.client.yiz.creature.CombatSpec spec) {
        double atk = this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double radius = spec.paramOr("radius", SKILL_RADIUS);
        double falloff = spec.paramOr("falloff", SKILL_FALLOFF_PER_BLOCK);
        double mult = spec.paramOr("attack_mult", SKILL_ATTACK_MULT);
        double dreamMob = spec.paramOr("dream_mob", SKILL_DREAM_MOB);
        for (LivingEntity t : nearby(radius)) {
            double factor = 1.0 - Math.sqrt(this.distanceToSqr(t)) * falloff;
            if (factor <= 0.0) continue;
            float dmg = (float) (mult * atk * factor * ratio);
            if (dmg > 0.0F) {
                net.minecraft.client.yiz.api.YizModQZKAPI.pierceInvulnerabilityDamage(t, dmg, this);
            }
            // 多空真伤只对生物生效；玩家只吃上面那份普通伤害
            if (t instanceof Player) continue;
            double amount = t.getMaxHealth() * dreamMob * factor * ratio;
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
