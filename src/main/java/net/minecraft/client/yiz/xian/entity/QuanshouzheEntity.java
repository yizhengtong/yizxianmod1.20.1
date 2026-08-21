package net.minecraft.client.yiz.xian.entity;

import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.editor.PoshiBypassBridge;
import net.minecraft.client.yiz.tool.attribute.EntityAttributeGate;
import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 辖界者 — Boss 生物（1.20.1 Forge 移植版）。
 *
 * <p>纯近战 / 中立反击 / 狂暴机制 / 血量外部表（SecureHealthClosure）/ 传导限伤 /
 * Boss 血条 / 仇恨系统 完整移植。模型与动画（Warden 骨骼复用）后续接入。</p>
 */
public class QuanshouzheEntity extends YizxianMob {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int PHASE_NONE = 0;

    /** 受击诊断限频。 */
    private final AtomicInteger hurtLogCount = new AtomicInteger(0);
    /** 摸底诊断：服务端 Boss 血条进度限频。 */
    private static final AtomicInteger BOSS_DIAG_LOG = new AtomicInteger();
    /** 防御激活确认日志是否已打。 */
    private boolean defenseLogged = false;

    private static final double RAGE_SPEED_BONUS = 0.2;
    // 护甲/法防指数减伤参数（与前置库 LivingEntityMixin 一致：锚定 x=20→50%、x=50→75%）
    private static final double EXP_REDUCTION_BASE = 40.0;
    private static final double EXP_REDUCTION_EXP =
        Math.log(2.0) / Math.log(1.0 + 50.0 / EXP_REDUCTION_BASE);
    // 1.20.1 AttributeModifier 用 UUID（非 ResourceLocation）；确定性 UUID 保证 remove 幂等
    private static final java.util.UUID RAGE_SPEED_ID =
        java.util.UUID.nameUUIDFromBytes((YizxianMod.MODID + ":quanshouzhe_rage_speed").getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private static final EntityDataAccessor<Integer> DATA_CAST_PHASE =
        SynchedEntityData.defineId(QuanshouzheEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RAGING =
        SynchedEntityData.defineId(QuanshouzheEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HEAVY_ATTACK =
        SynchedEntityData.defineId(QuanshouzheEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_FORM_PHASE =
        SynchedEntityData.defineId(QuanshouzheEntity.class, EntityDataSerializers.INT);

    private final int[] skillCooldowns = new int[0];
    private int skillValue = 0;
    private static final int SKILL_MAX = 300;
    private int skill1DamageStartTick = -1;
    private int skill1DamageEndTick = -1;
    private int skill2DamageStartTick = -1;
    private int skill2DamageEndTick = -1;
    private int forcedSkillIndex = 0;
    private int forcedSkillCount = 0;
    private int skillLockUntilTick = -1;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
        Component.literal("辖界者"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    private long clientCastStartTick = -1;

    private UUID lastPlayerHurtBy;
    private long lastPlayerHurtTime = -1000;

    // lastConductionHitTick / conductionHitCdTicks 继承自基类 YizxianMob（传导受击 CD 动态跟随 INVINCIBILITY_MULT）
    // CONDUCTION_HIT_FLASH / isConductionHitFlash / broadcastHurtFlash 已下沉到基类 YizxianMob

    //  测试阶段硬编码防御（不依赖属性，防属性被外部清空导致防御失效）
    // 400 血 / 单发限伤 1 点 / 传导 CD 40 tick。正式版恢复属性驱动（conduction_cap 12%、invincibility_mult）。
    @Override
    protected float secureMaxHealth() { return 400.0F; }

    // 传导上限（CONDUCTION_CAP）与传导 CD（INVINCIBILITY_MULT）交由基类 YizxianMob 属性驱动：
    // 不再硬编码 override（否则编辑器改属性永远不生效）。基类实现：编辑器 markEdited → 读属性实时跟随；
    // 未编辑 → 权威表/属性标准值。硬编码已删（原 conductionCap=25%、conductionHitCdTicks=16）。

    public final AnimationState attackAnimationState = new AnimationState();
    public final AnimationState sonicBoomAnimationState = new AnimationState();
    public final AnimationState diggingAnimationState = new AnimationState();
    public final AnimationState emergeAnimationState = new AnimationState();
    public final AnimationState roarAnimationState = new AnimationState();
    public final AnimationState sniffAnimationState = new AnimationState();
    public final AnimationState qiDanAnimationState = new AnimationState();
    public final AnimationState qiDan2AnimationState = new AnimationState();
    public final AnimationState qiDan3AnimationState = new AnimationState();
    private int roarEndTick = -1;
    private int tendrilAnimation;
    private int tendrilAnimationO;
    private int combatStartTick = -1;
    private int rageEndTick = -1;

    private int attackCounter;
    @Nullable private UUID counteringTarget;
    private int heavyAttackTick;
    private double lastDreamValue = Double.NaN;

    private static final double HATE_SPREAD_RANGE = 15.0;
    private final Set<UUID> hateSet = new HashSet<>();

    public QuanshouzheEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        this.bossEvent.setDarkenScreen(true);
        this.bossEvent.setPlayBossMusic(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 400.0)
            .add(Attributes.MOVEMENT_SPEED, 0.30)
            .add(Attributes.ATTACK_DAMAGE, 50.0)
            .add(Attributes.ARMOR, 0.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.FOLLOW_RANGE, 60.0)
            // 1.20.1 无 Attributes.STEP_HEIGHT（1.21 才加入原版）；步高由自定义属性后续补
            // yizmodqzk 自定义属性骨架（基值 0，数值由 applyEntityAttributes 经 EntityAttributeGate 分配）
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

    /** 分配辖界者受保护自定义属性值（生成/加载后第一 tick 由 YizxianMob.aiStep 调用一次）。 */
    @Override
    protected void applyEntityAttributes() {
        applyVanillaDifficultyScale();
        setAttr(YizAttributes.ATTACK_STRENGTH, "attack_strength", scaleDifficulty(60.0));
        setAttr(YizAttributes.SPELL_POWER, "spell_power", 100.0);
        setAttr(YizAttributes.LIFE_STEAL, "life_steal", scaleDifficulty(10.0));
        setAttr(YizAttributes.DAMAGE_BLOCK, "damage_block", scaleDifficulty(1.0));
        setAttr(YizAttributes.DAMAGE_REDUCTION, "damage_reduction", scaleDifficulty(25.0));
        // 传导间隔/无敌帧 = 标准 16 tick（0.8 秒，固定不随难度）；传导时间动态跟随无敌帧属性 INVINCIBILITY_MULT
        setAttr(YizAttributes.INVINCIBILITY_MULT, "invincibility_mult", 16.0);
        setAttr(YizAttributes.ARMOR, "armor", scaleDifficulty(15.0));
        setAttr(YizAttributes.SPELL_DEFENSE, "spell_defense", scaleDifficulty(15.0));
        // 传导上限 = 最大生命值 × cap%；形态1 初始 25%（形态2 由 applyFormPhase 改 15%）
        setAttr(YizAttributes.CONDUCTION_CAP, "conduction_cap", 25.0);
        setAttr(YizAttributes.SECURE_PULSE, "secure_pulse", 1.0);
        // 血量外部表注册已下沉到基类 YizxianMob.registerSecureHealth()（applyEntityAttributes 后自动执行）
    }

    /**
     * 分配受保护属性 + 注册到属性标准化守护（类还原关键）：
     * AttributeStandardizer 每 20 tick 审计，属性被外部篡改（base 改/修饰符清/负值）→ 还原到标准值。
     * registerStandard 记录标准 base + 标准 prot_ modifier 值，还原时经 EntityAttributeGate 重写。
     */
    private void setAttr(net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                         String idKey, double value) {
        EntityAttributeGate.set(this, attr, idKey, value);
        net.minecraft.client.yiz.tool.attribute.AttributeStandardizer.registerStandard(this, attr.get(), idKey, value);
    }

    /** 形态2 → 形态1 回退缓冲起点 tick（-1=无缓冲）。 */
    private int phaseRegressBufferTick = -1;

    /** 三阶段形态属性应用（阶段变化时调用一次）：攻击/攻击强度/吸血按阶段值×难度缩放。
     *  阶段1: 50攻/60攻强/10%吸血  阶段2: 55攻/70攻强/15%吸血  阶段3: 60攻/80攻强/18%吸血。
     *  涨跌多空(20/30/40%)与每tick回血(0.05/0.06/0.07)在 aiStep 每 tick 跟随阶段，不在此应用。 */
    private void applyFormPhase(int phase) {
        if (this.level().isClientSide()) return;
        double mult = difficultyMultiplier();
        float atk = switch (phase) { case 2 -> 65f; default -> 50f; };
        double atkStr = switch (phase) { case 2 -> 80.0; default -> 60.0; };
        double lifesteal = switch (phase) { case 2 -> 20.0; default -> 10.0; };
        // 攻击（vanilla base 动态改；applyVanillaDifficultyScale 只第一 tick 跑，阶段变化由这里接管）
        var atkInst = this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (atkInst != null) atkInst.setBaseValue(atk * mult);
        // 受保护属性（setAttr 同步 AttributeStandardizer 标准值，防 20 tick 审计还原旧阶段值）
        setAttr(YizAttributes.ATTACK_STRENGTH, "attack_strength", atkStr * mult);
        setAttr(YizAttributes.LIFE_STEAL, "life_steal", lifesteal * mult);
        // 传导限伤随形态：形态1=25% / 形态2=15%
        double cap = switch (phase) { case 2 -> 15.0; default -> 25.0; };
        setAttr(YizAttributes.CONDUCTION_CAP, "conduction_cap", cap);
        LOGGER.info("[QZK-PHASE] 辖界者进入阶段{}：攻击{} 攻强{}% 吸血{}% 限伤{}%",
            phase, atk * mult, atkStr * mult, lifesteal * mult, cap);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new net.minecraft.client.yiz.xian.entity.ai.QuanshouzheMeleeGoal(this));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new net.minecraft.client.yiz.xian.entity.ai.QuanshouzheRetaliateGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CAST_PHASE, PHASE_NONE);
        this.entityData.define(DATA_RAGING, false);
        this.entityData.define(DATA_HEAVY_ATTACK, false);
        this.entityData.define(DATA_FORM_PHASE, 1);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key.equals(DATA_CAST_PHASE) && level().isClientSide() && getCastPhase() != PHASE_NONE) {
            this.clientCastStartTick = level().getGameTime();
        }
        if (key.equals(DATA_POSE) && getPose() == Pose.ROARING) {
            this.roarAnimationState.start(this.tickCount);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3 && net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            return;
        }
        if (id == 4) {
            this.roarAnimationState.stop();
            this.attackAnimationState.start(this.tickCount);
        } else if (id == 61) {
            this.tendrilAnimation = 10;
        } else if (id == 62) {
            this.sonicBoomAnimationState.start(this.tickCount);
        } else if (id == 60) {
            this.qiDanAnimationState.start(this.tickCount);
        } else if (id == 59) {
            this.qiDan2AnimationState.start(this.tickCount);
        } else if (id == 58) {
            this.qiDan3AnimationState.start(this.tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    public float getTendrilAnimation(float partialTick) {
        return Mth.lerp(partialTick, this.tendrilAnimationO, this.tendrilAnimation) / 10.0F;
    }

    public int getCastPhase() { return this.entityData.get(DATA_CAST_PHASE); }
    public void setCastPhase(int phase) { this.entityData.set(DATA_CAST_PHASE, phase); }

    public boolean isRaging() { return this.tickCount < this.rageEndTick; }
    public void setRaging(boolean raging) { this.entityData.set(DATA_RAGING, raging); }

    public boolean isSkillReady(int index) { return true; }
    public void setSkillCooldown(int index, int ticks) {}

    public float getCastProgress(float partialTick) {
        if (getCastPhase() == PHASE_NONE || clientCastStartTick < 0) return 0f;
        int windup = getCastWindup(getCastPhase());
        float elapsed = level().getGameTime() + partialTick - clientCastStartTick;
        return Mth.clamp(elapsed / windup, 0f, 1f);
    }

    public static int getCastWindup(int phase) {
        return 0;
    }

    public boolean isHeavyAttacking() { return this.entityData.get(DATA_HEAVY_ATTACK); }
    public void setHeavyAttacking(boolean v) { this.entityData.set(DATA_HEAVY_ATTACK, v); }

    public void attackTarget(LivingEntity target) {
        PoshiBypassBridge.beginBypass();
        try {
            if (isSkillLocked()) {
                return;
            }
            if (this.forcedSkillCount > 0) {
                this.forcedSkillCount--;
                useSkillByIndex(this.forcedSkillIndex);
                return;
            }
            float baseAtk = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
            if (this.counteringTarget != null && this.counteringTarget.equals(target.getUUID())) {
                this.counteringTarget = null;
                this.level().broadcastEntityEvent(this, (byte)4);
                this.hit(target, baseAtk * 1.7f);
                this.onAttackDone();
                return;
            }
            this.attackCounter++;
            if (this.attackCounter % 4 == 0) {
                this.performHeavyAttack();
            } else {
                this.level().broadcastEntityEvent(this, (byte)4);
                this.hit(target, baseAtk * (0.5f + this.random.nextFloat() * 0.3f));
                this.onAttackDone();
            }
        } finally {
            PoshiBypassBridge.endBypass();
        }
    }

    private void hit(LivingEntity target, float dmg) {
        if (isObserver(target)) return;
        net.minecraft.client.yiz.tool.health.VitalitySeveranceHandler.addStackingBan(target, 5.0f, 7 * 20L);
        target.invulnerableTime = 0;
        net.minecraft.client.yiz.tool.health.EntityASMUtil.applyDreamDamage(this, target,
            this.getAttributeValue(net.minecraft.client.yiz.attribute.YizAttributes.FIRST_DREAM.get()));
        float targetHpDream = switch (this.getFormPhase()) {
            case 2 -> target.getMaxHealth() * 0.05f;
            default -> 0f;
        };
        if (targetHpDream > 0) {
            net.minecraft.client.yiz.tool.health.EntityASMUtil.applyDreamDamage(this, target, targetHpDream);
        }
        target.hurt(this.damageSources().mobAttack(this), dmg);
    }

    private void performHeavyAttack() {
        this.setHeavyAttacking(true);
        this.heavyAttackTick = 7;
        this.level().broadcastEntityEvent(this, (byte)4);
        float baseAtk = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float dmg = baseAtk * (0.9f + this.random.nextFloat() * 0.4f);
        double radius = this.getHeavyAttackRadius();
        AABB aabb = this.getBoundingBox().inflate(radius);
        List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class, aabb,
            e -> e.isAlive() && e != this && !isObserver(e) && this.distanceTo(e) <= radius);
        for (LivingEntity e : nearby) {
            this.hit(e, dmg);
        }
        this.onAttackDone();
    }

    private void onAttackDone() {
        addSkill(1 + this.random.nextInt(2));
        tryUseSkillAfterAttack();
    }

    private void addSkill(int amount) {
        this.skillValue = Math.min(SKILL_MAX, this.skillValue + amount);
    }

    private void tryUseSkillAfterAttack() {
        if (this.skillValue >= 90) {
            if (this.random.nextFloat() < 0.25f) useSkill3();
        } else if (this.skillValue >= 45) {
            if (this.random.nextFloat() < 0.25f) useSkill2();
        } else if (this.skillValue >= 30) {
            if (this.random.nextFloat() < 0.4f) useSkill1();
        }
    }

    private void useSkill1() {
        this.skillValue = Math.max(0, this.skillValue - 30);
        this.level().broadcastEntityEvent(this, (byte) 60);
        applySkill1Slow();
        this.skill1DamageStartTick = this.tickCount + 20;
        this.skill1DamageEndTick = this.tickCount + 60;
        this.skillLockUntilTick = this.tickCount + 56;
    }

    private void useSkill2() {
        this.skillValue = Math.max(0, this.skillValue - 45);
        this.level().broadcastEntityEvent(this, (byte) 59);
        net.minecraft.client.yiz.tool.health.HealthModificationScheduler.schedule(this,
            net.minecraft.client.yiz.tool.health.HealthModificationScheduler.once("skill2_anim2", 10, e -> {
                if (e instanceof QuanshouzheEntity q) {
                    q.level().broadcastEntityEvent(q, (byte) 59);
                }
            }));
        this.skill2DamageStartTick = this.tickCount;
        this.skill2DamageEndTick = this.tickCount + 20;
        this.skillLockUntilTick = this.tickCount + 40;
    }

    private void useSkill3() {
        this.skillValue = Math.max(0, this.skillValue - 90);
        this.level().broadcastEntityEvent(this, (byte) 58);
        net.minecraft.client.yiz.tool.health.HealthModificationScheduler.schedule(this,
            net.minecraft.client.yiz.tool.health.HealthModificationScheduler.once("skill3_damage", 42, e -> {
                if (e instanceof QuanshouzheEntity q) {
                    q.applySkill3Damage();
                }
            }));
        this.skillLockUntilTick = this.tickCount + 62;
    }

    private void useSkillByIndex(int index) {
        switch (index) {
            case 3 -> useSkill3();
            case 2 -> useSkill2();
            default -> useSkill1();
        }
    }

    public void setForcedSkill(int index, int count) {
        this.forcedSkillIndex = index;
        this.forcedSkillCount = count;
    }

    private boolean isSkillLocked() {
        return this.skillLockUntilTick >= 0 && this.tickCount < this.skillLockUntilTick;
    }

    private void applySkill3Damage() {
        net.minecraft.world.phys.AABB aabb = this.getBoundingBox().inflate(60.0);
        var nearby = this.level().getEntitiesOfClass(LivingEntity.class, aabb,
            e -> e.isAlive() && e != this && !isObserver(e));
        float atkStr = (float) this.getAttributeValue(net.minecraft.client.yiz.attribute.YizAttributes.ATTACK_STRENGTH.get());
        float spellPower = (float) this.getAttributeValue(net.minecraft.client.yiz.attribute.YizAttributes.SPELL_POWER.get());
        for (LivingEntity e : nearby) {
            if (e instanceof Player p && !hasAttackedBoss(p)) {
                continue;
            }
            e.hurt(this.damageSources().mobAttack(this), atkStr);
            float dream = (spellPower / 100.0f) * 0.9f * e.getMaxHealth() + 50.0f;
            net.minecraft.client.yiz.tool.health.EntityASMUtil.applyDreamDamage(this, e, dream);
        }
    }

    private boolean hasAttackedBoss(Player p) {
        return this.lastPlayerHurtBy != null
            && this.lastPlayerHurtBy.equals(p.getUUID())
            && this.level().getGameTime() - this.lastPlayerHurtTime <= 600;
    }

    private void applySkill1Slow() {
        net.minecraft.world.phys.AABB aabb = this.getBoundingBox().inflate(14.0);
        var nearby = this.level().getEntitiesOfClass(LivingEntity.class, aabb,
            e -> e.isAlive() && e != this && !isObserver(e));
        for (LivingEntity e : nearby) {
            e.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
        }
    }

    private void applySkill1Damage() {
        net.minecraft.world.phys.AABB aabb = this.getBoundingBox().inflate(18.0);
        var nearby = this.level().getEntitiesOfClass(LivingEntity.class, aabb,
            e -> e.isAlive() && e != this && !isObserver(e));
        float atk = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float spellPower = (float) this.getAttributeValue(net.minecraft.client.yiz.attribute.YizAttributes.SPELL_POWER.get());
        for (LivingEntity e : nearby) {
            e.hurt(this.damageSources().mobAttack(this), atk * 0.1f);
            float dream = (spellPower / 100.0f) * 0.03f * e.getMaxHealth();
            net.minecraft.client.yiz.tool.health.EntityASMUtil.applyDreamDamage(this, e, dream);
        }
    }

    private void applySkill2Damage() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        skill2Damage(target, false);
        net.minecraft.world.phys.AABB aabb = target.getBoundingBox().inflate(4.0);
        var nearby = this.level().getEntitiesOfClass(LivingEntity.class, aabb,
            e -> e.isAlive() && e != this && e != target && !isObserver(e));
        for (LivingEntity e : nearby) {
            skill2Damage(e, true);
        }
    }

    private void skill2Damage(LivingEntity e, boolean half) {
        float atk = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float spellPower = (float) this.getAttributeValue(net.minecraft.client.yiz.attribute.YizAttributes.SPELL_POWER.get());
        float normal = atk * 0.5f;
        float dream = (spellPower / 100.0f) * 0.015f * e.getMaxHealth();
        if (half) {
            normal *= 0.5f;
            dream *= 0.5f;
        }
        e.hurt(this.damageSources().mobAttack(this), normal);
        net.minecraft.client.yiz.tool.health.EntityASMUtil.applyDreamDamage(this, e, dream);
    }

    public int getFormPhase() {
        return this.entityData.get(DATA_FORM_PHASE);
    }

    private int updateFormPhase() {
        float hp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
        float maxHp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getMaxHealth(this);
        float ratio = maxHp > 0 ? hp / maxHp : 1.0F;
        int cur = this.entityData.get(DATA_FORM_PHASE);
        if (ratio < 0.80F) {
            this.phaseRegressBufferTick = -1;
            if (cur != 2) {
                this.entityData.set(DATA_FORM_PHASE, 2);
                applyFormPhase(2);
            }
        } else if (cur == 2) {
            if (this.phaseRegressBufferTick < 0) {
                this.phaseRegressBufferTick = this.tickCount;
            }
            if (this.tickCount - this.phaseRegressBufferTick >= 60) {
                this.entityData.set(DATA_FORM_PHASE, 1);
                this.phaseRegressBufferTick = -1;
                applyFormPhase(1);
            }
        }
        return this.entityData.get(DATA_FORM_PHASE);
    }

    public int getAttackInterval() {
        return switch (getFormPhase()) {
            case 2 -> 7;
            default -> 15;
        };
    }

    public double getAttackRange() {
        return switch (getFormPhase()) {
            case 2 -> 9.5;
            default -> 5.25;
        };
    }

    public double getHeavyAttackRadius() {
        return switch (getFormPhase()) {
            case 2 -> 18.0;
            default -> 9.0;
        };
    }

    public boolean tickHeavyAttack() {
        if (!this.isHeavyAttacking()) return false;
        if (--this.heavyAttackTick <= 0) {
            this.setHeavyAttacking(false);
            return true;
        }
        return false;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        if (target != null && !level().isClientSide() && this.hateSet.isEmpty()) {
            spreadHate(target);
        }
    }

    private void spreadHate(LivingEntity target) {
        EntityType<?> type = target.getType();
        this.hateSet.add(target.getUUID());
        AABB aabb = this.getBoundingBox().inflate(HATE_SPREAD_RANGE);
        List<Entity> sameType = this.level().getEntities(this, aabb,
            e -> e.isAlive() && e != this && e.getType() == type);
        for (Entity e : sameType) {
            this.hateSet.add(e.getUUID());
        }
    }

    private void updateTargetFromHate() {
        if (this.hateSet.isEmpty()) return;
        LivingEntity cur = this.getTarget();
        if (cur != null && cur.isAlive()
                && this.hateSet.contains(cur.getUUID())
                && isValidRetaliateTarget(cur)) {
            return;
        }
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        Iterator<UUID> it = this.hateSet.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = this.level() instanceof ServerLevel sl ? sl.getEntity(id) : null;
            if (!(e instanceof LivingEntity le) || !le.isAlive() || !isValidRetaliateTarget(le)) {
                it.remove();
                continue;
            }
            double d = this.distanceToSqr(le);
            if (d < bestDist) {
                bestDist = d;
                best = le;
            }
        }
        if (best != null) {
            this.setTarget(best);
        }
    }

    /**
     * 通用不死强制（不点名任何模组）：每 tick 判定死亡前，只要外部表血量 &gt; 0，
     * 就把 vanilla dead/deathTime 强制拉回活着——任何外部注入/直写想判死都无效。
     */
    @Override
    // baseTick 不死强制已下沉到基类 YizxianMob（每 tick dead/deathTime 校正）

    public void aiStep() {
        super.aiStep();
        this.tendrilAnimationO = this.tendrilAnimation;
        if (this.tendrilAnimation > 0) this.tendrilAnimation--;
        if (!level().isClientSide()) {
            // 无敌帧到期（1.20.1 无 LivingEntityMixin.onTick，此处双保险；customServerAiStep 也调）
            net.minecraft.client.yiz.handler.AttackInvulnerabilityTracker.onTick(this, this.level().getGameTime());
            if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
                int formPhase = updateFormPhase();
                double dreamPct = switch (formPhase) { case 2 -> 0.4; default -> 0.2; };
                double dream = this.getAttributeValue(Attributes.ATTACK_DAMAGE) * dreamPct;
                if (dream != this.lastDreamValue) {
                    setAttr(YizAttributes.FIRST_DREAM, "long_short", dream);
                    this.lastDreamValue = dream;
                }
                float regen = switch (formPhase) { case 2 -> 0.08f; default -> 0.05f; };
                if (regen > 0 && net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this)) {
                    this.heal(regen);
                }
            }
            for (int i = 0; i < skillCooldowns.length; i++) {
                if (skillCooldowns[i] > 0) skillCooldowns[i]--;
            }
            if (this.skill1DamageEndTick >= 0) {
                if (this.tickCount >= this.skill1DamageStartTick && this.tickCount < this.skill1DamageEndTick) {
                    if (this.tickCount % 2 == 0) applySkill1Damage();
                } else if (this.tickCount >= this.skill1DamageEndTick) {
                    this.skill1DamageStartTick = -1;
                    this.skill1DamageEndTick = -1;
                }
            }
            if (this.skill2DamageEndTick >= 0) {
                if (this.tickCount >= this.skill2DamageStartTick && this.tickCount < this.skill2DamageEndTick) {
                    applySkill2Damage();
                } else if (this.tickCount >= this.skill2DamageEndTick) {
                    this.skill2DamageStartTick = -1;
                    this.skill2DamageEndTick = -1;
                }
            }
            this.tickHeavyAttack();
            if (this.getPose() == Pose.ROARING && this.tickCount >= this.roarEndTick) {
                this.setPose(Pose.STANDING);
            }
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
    protected void customServerAiStep() {
        super.customServerAiStep();
        //  每 tick 状态清理（1.20.1 无 LivingEntityMixin.onTick，此处手动模拟）
        // 无敌帧到期检查：缺这行会导致 INVINCIBILITY_MULT 激活后永不过期 → 第一次受击后永远免疫（打不中）
        long gt = this.level().getGameTime();
        net.minecraft.client.yiz.handler.AttackInvulnerabilityTracker.onTick(this, gt);
        // 硬控计时递减（STUN/FREEZE 到期移除）
        net.minecraft.client.yiz.core.StatusEffectDispatcher.tickControlTimers(this);
        // 血量外部表：死亡实体清理
        net.minecraft.client.yiz.tool.health.SecureHealthClosure.tick(this);

        // 防御激活确认（一次）：getHealth() 应返回表值（注入/override 生效），isAlive/isDeadOrDying 按表判定
        if (!defenseLogged) {
            defenseLogged = true;
            float t = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
            LOGGER.info("[QZK-DEF] 辖界者防御激活: 表值={} getHealth()={} isAlive={} isDeadOrDying={} maxHp={}",
                t, this.getHealth(), this.isAlive(), this.isDeadOrDying(), this.getMaxHealth());
        }

        // 每 tick 校正（表值回写通道/清 delta/防 removed/防 MAX_HEALTH）已下沉到基类 YizxianMob.enforceSecureHealthState()
        this.updateTargetFromHate();
        //  Boss 血条读权威表（外部注入 压 getHealth()/getMaxHealth() 虚拟调用不影响血条显示/死亡判定）
        float bossHp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
        float bossMax = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getMaxHealth(this);
        this.bossEvent.setProgress(bossHp / bossMax);
        // 摸底诊断：服务端 Boss 血条进度（限频）
        if (BOSS_DIAG_LOG.incrementAndGet() % 30 == 1) {
            LOGGER.info("[QZK-BOSS] 服务端 Boss 血条: {}/{} = {}", bossHp, bossMax, bossMax > 0 ? bossHp / bossMax : 0);
        }
        if (this.combatStartTick < 0 && this.getTarget() != null) {
            this.combatStartTick = this.tickCount;
        }
        boolean rageCondition = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this)
                <= net.minecraft.client.yiz.tool.health.SecureHealthClosure.getMaxHealth(this) * 0.5
            || (this.combatStartTick >= 0 && this.tickCount - this.combatStartTick >= 100);
        if (rageCondition) {
            if (this.isRaging()) {
                this.rageEndTick = this.tickCount + 120;
            } else {
                enterRage();
                this.rageEndTick = this.tickCount + 120;
            }
        } else if (this.tickCount >= this.rageEndTick) {
            exitRage();
        }
    }

    private void enterRage() {
        this.setRaging(true);
        this.playSound(SoundEvents.WARDEN_AGITATED, 5.0F, 1.0F);
        var inst = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null) {
            inst.addTransientModifier(new AttributeModifier(
                RAGE_SPEED_ID, "yizxianmod:quanshouzhe_rage_speed", RAGE_SPEED_BONUS, AttributeModifier.Operation.ADDITION));
        }
    }

    private void exitRage() {
        this.setRaging(false);
        this.rageEndTick = -1;
        var inst = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst != null) {
            inst.removeModifier(RAGE_SPEED_ID);
        }
    }

    //  血量外部存储保护已下沉到基类 YizxianMob 
    // getHealth/setHealth/isAlive/isDeadOrDying/actuallyHurt/kill/remove/setPose/dropAllDeathLoot/
    // saveAsPassenger/shouldBeSaved/heal/beginForceRemove 全部由基类提供；此处仅保留辖界者专属的
    // 强化 hurt()（护甲/法防指数减免 + 反击 + 狂暴）与 die()（Boss 事件/技能清理）。

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) return false;
        if (amount <= 0) return false;
        //  vanilla 无敌帧（受击窗思路，免改关键）：外部注入 走 hurt() 时被挡，
        // 不会连续扣血。外部注入 若强行清 invulnerableTime，由下方传导 CD（lastConductionHitTick）兜底。
        if (this.invulnerableTime > 0) return false;
        long cdTicks = conductionHitCdTicks();
        long hitCdStart = this.lastConductionHitTick;
        if (hitCdStart != Long.MIN_VALUE
                && this.level().getGameTime() - hitCdStart < cdTicks) {
            return false;
        }

        net.minecraft.client.yiz.handler.AttackInvulnerabilityTracker.HurtHeadResult head =
            net.minecraft.client.yiz.handler.AttackInvulnerabilityTracker.onHurtHead(this);
        if (head == net.minecraft.client.yiz.handler.AttackInvulnerabilityTracker.HurtHeadResult.CANCEL) {
            return false;
        }
        if (source.getEntity() instanceof LivingEntity attacker) {
            net.minecraft.client.yiz.handler.InvulnBreakHandler.apply(attacker, this);
        }

        // 通用防御（代码级，不点名任何模组）：只接受 vanilla 伤害类型，挡住所有模组自定义伤害类型
        // （任何模组的自定义伤害源都会被拒）——Boss 不受模组特殊伤害。1.20.1 用 Registries.DAMAGE_TYPE。
        var type = source.type();
        var dmgKey = this.level().registryAccess()
            .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
            .getKey(type);
        if (dmgKey == null || !dmgKey.getNamespace().equals("minecraft")) {
            return false;
        }

        //  护甲/法防指数减免（物理→ARMOR，其余→SPELL_DEFENSE）：与前置库 LivingEntityMixin 同一公式，
        //    让辖界者的 ARMOR/SPELL_DEFENSE 属性真正生效（override hurt 不走 vanilla 护甲公式）。
        boolean isPhysical = source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.IS_FALL);
        var expAttr = isPhysical ? YizAttributes.ARMOR : YizAttributes.SPELL_DEFENSE;
        var expInst = this.getAttribute(expAttr.get());
        if (expInst != null && expInst.getValue() > 0) {
            double reduction = 1.0 - Math.pow(
                    1.0 + expInst.getValue() / EXP_REDUCTION_BASE,
                    -EXP_REDUCTION_EXP);
            amount *= (float) (1.0 - Math.min(1.0, reduction));
        }

        float reduced = amount;
        //  百分比减免
        var redInst = this.getAttribute(net.minecraft.client.yiz.attribute.YizAttributes.DAMAGE_REDUCTION.get());
        if (redInst != null && redInst.getValue() > 0)
            reduced *= (float) (1.0 - Math.min(1.0, redInst.getValue() / 100.0));
        //  固定格挡
        var blockInst = this.getAttribute(net.minecraft.client.yiz.attribute.YizAttributes.DAMAGE_BLOCK.get());
        if (blockInst != null && blockInst.getValue() > 0)
            reduced = Math.max(0, reduced - (float) blockInst.getValue());
        //  传导限伤（测试阶段走硬编码 conductionCap()=1；正式版改回 maxHp × conduction_cap%）
        float cap = conductionCap();
        float limited = Math.min(reduced, cap);
        if (limited <= 0) return false;

        float current = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
        float next = Math.max(0, current - limited);
        net.minecraft.client.yiz.tool.health.SecureHealthClosure.setHealth(this, next);
        // 即时回写 vanilla DATA_HEALTH 通道（客户端血条读它），避免等 enforce 每 tick 才同步 → 血条滞后一拍
        net.minecraft.client.yiz.tool.health.EntityActuallyHurt.catchSetTrueHealth(this, next);
        // 受击诊断（限频）：确认外部伤害是否真的打到表上；三值对比定位 current 来源
        // （current=line 上游 SecureHealthClosure.getHealth；shc=再调一次；vh=虚拟 this.getHealth 可能被外部 agent 包装；directDec=直接 dec 串）
        if (hurtLogCount.incrementAndGet() <= 60) {
            String src = source != null ? source.getMsgId() : "?";
            float shc2 = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
            float vh = this.getHealth();
            float directDec;
            try {
                int k = this.entityData.get(net.minecraft.client.yiz.tool.health.HealthChannels.SECURE_OBF_KEY);
                String e = this.entityData.get(net.minecraft.client.yiz.tool.health.HealthChannels.SECURE_OBF);
                directDec = net.minecraft.client.yiz.tool.health.FloatObf.dec(e, k);
            } catch (Throwable t) { directDec = -999f; }
            LOGGER.warn("[QZK-HURT] 真实扣表: src={} amount={} reduced={} cap={} 表 {} -> {} | shc={} vh={} directDec={}",
                src, amount, reduced, cap, current, next, shc2, vh, directDec);
        }
        long nowTick = this.level().getGameTime();
        this.lastConductionHitTick = nowTick;
        // vanilla 无敌帧跟随传导 CD（INVINCIBILITY_MULT 标准 16 tick/0.8s）：不再硬编码 20，
        // 否则传导 CD 调小（如 16）被 20 tick 盖住 → 传导时间不跟随属性变化
        this.invulnerableTime = (int) conductionHitCdTicks();

        this.hurtTime = 10;
        this.hurtDuration = 10;
        // 1.20.1 差异：Level.broadcastDamageEvent 是空实现，必须调 ServerLevel 版本才发红闪包。
        // 基类 broadcastHurtFlash 已包好红闪门禁（mixin 只放行本模组传导扣血红闪）。
        this.broadcastHurtFlash(source);

        net.minecraft.client.yiz.handler.AttackInvulnerabilityTracker.onHurtSuccess(this, this.level().getGameTime());

        addSkill(2 + this.random.nextInt(3));
        if (next <= 0) {
            this.die(source);
            return true;
        }

        if (source.getEntity() instanceof LivingEntity attacker2) {
            if (isValidRetaliateTarget(attacker2)) {
                this.setTarget(attacker2);
                this.counteringTarget = attacker2.getUUID();
                if (this.distanceToSqr(attacker2) <= 27.5625 && !isCounterInProgress()) {
                    beginCounterWindow();
                    try {
                        this.attackTarget(attacker2);
                    } finally {
                        endCounterWindow();
                    }
                }
            }
            if (attacker2 instanceof Player p && !p.isCreative()) {
                this.lastPlayerHurtBy = p.getUUID();
                this.lastPlayerHurtTime = this.level().getGameTime();
            }
        }
        return true;
    }

    public boolean isValidRetaliateTarget(LivingEntity e) {
        if (!e.isAlive() || e.isInvulnerable()) return false;
        if (e instanceof Player p && p.isCreative()) return false;
        return true;
    }

    @Nullable
    public LivingEntity getRecentPlayerAttacker() {
        if (this.lastPlayerHurtBy == null
                || this.level().getGameTime() - this.lastPlayerHurtTime > 600) {
            return null;
        }
        if (this.level() instanceof ServerLevel sl) {
            Player p = sl.getPlayerByUUID(this.lastPlayerHurtBy);
            return (p != null && p.isAlive()) ? p : null;
        }
        return null;
    }

    /**
     * 拦截外部模组强制死亡（kill() 路径）。外部 kill() 直接拒绝——
     * 辖界者只允许逻辑血量≤0 时由 die() 自行接管死亡。
     */
    // kill()/heal() 保护已下沉到基类 YizxianMob（血量未归零拒绝 kill、负 heal 重定向 hurt）

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (!level().isClientSide()) {
            float hp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
            if (hp > 0.0F) return;

            this.bossEvent.removeAllPlayers();
            QuanshouzheSkillManager.clear(this.getUUID());
            if (!this.isRemoved() && !this.dead) {
                // 走完整 vanilla 死亡链：super.die 内部负责 dead=true + dropAllDeathLoot
                //（LootTable 掉落 getDefaultLootTable + 经验球 dropExperience/getExperienceReward）
                // + 死亡广播 + setPose(DYING)。掉落物走标准死亡掉落流程，稳定可配。
                super.die(source);
            }
        }
    }

    /** vanilla 掉落表（1.20.1 绑定方式）：Mob 构造时经 getDefaultLootTable() 初始化 lootTable 字段。
     *  对应数据包 data/yizxianmod/loot_tables/entities/quanshouzhe.json（掉 1~3 钻石）。 */
    @Override
    protected net.minecraft.resources.ResourceLocation getDefaultLootTable() {
        return new net.minecraft.resources.ResourceLocation("yizxianmod", "entities/quanshouzhe");
    }

    /** vanilla 死亡经验：dropExperience 在 dropAllDeathLoot 内调用，返回 50 XP。 */
    @Override
    public int getExperienceReward() {
        return 50;
    }

    /** 总是掉经验：辖界者自定义 hurt() 不设 lastHurtByPlayerTime，
     *  若不 override 则 vanilla dropExperience 的 lastHurtByPlayerTime>0 条件不满足 → 不掉经验。 */
    @Override
    protected boolean isAlwaysExperienceDropper() {
        return true;
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("yizxianmod_boss_health", net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this));
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("yizxianmod_boss_health", net.minecraft.nbt.Tag.TAG_FLOAT)) {
            float hp = tag.getFloat("yizxianmod_boss_health");
            net.minecraft.client.yiz.tool.health.SecureHealthClosure.register(this, hp);
            net.minecraft.client.yiz.tool.health.SecureHealthClosure.setHealth(this, hp);
        }
        this.dead = false;
        this.deathTime = 0;
        this.hurtTime = 0;
    }
}
