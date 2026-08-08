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

    private static final double RAGE_SPEED_BONUS = 0.2;
    // 1.20.1 AttributeModifier 用 UUID（非 ResourceLocation）；确定性 UUID 保证 remove 幂等
    private static final java.util.UUID RAGE_SPEED_ID =
        java.util.UUID.nameUUIDFromBytes((YizxianMod.MODID + ":quanshouzhe_rage_speed").getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private static final EntityDataAccessor<Integer> DATA_CAST_PHASE =
        SynchedEntityData.defineId(QuanshouzheEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RAGING =
        SynchedEntityData.defineId(QuanshouzheEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HEAVY_ATTACK =
        SynchedEntityData.defineId(QuanshouzheEntity.class, EntityDataSerializers.BOOLEAN);

    private final int[] skillCooldowns = new int[0];

    private final ServerBossEvent bossEvent = new ServerBossEvent(
        Component.literal("辖界者"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    private long clientCastStartTick = -1;

    private UUID lastPlayerHurtBy;
    private long lastPlayerHurtTime = -1000;

    private static final int CONDUCTION_HIT_CD_FALLBACK = 20;
    private long lastConductionHitTick = Long.MIN_VALUE;

    private static final ThreadLocal<Boolean> CONDUCTION_HIT_FLASH = ThreadLocal.withInitial(() -> false);

    public static boolean isConductionHitFlash() {
        return CONDUCTION_HIT_FLASH.get();
    }

    private long conductionHitCdTicks() {
        var inst = this.getAttribute(net.minecraft.client.yiz.attribute.YizAttributes.INVINCIBILITY_MULT.get());
        double v = inst != null ? inst.getValue() : 0;
        return v > 0 ? (long) v : CONDUCTION_HIT_CD_FALLBACK;
    }

    public final AnimationState attackAnimationState = new AnimationState();
    public final AnimationState sonicBoomAnimationState = new AnimationState();
    public final AnimationState diggingAnimationState = new AnimationState();
    public final AnimationState emergeAnimationState = new AnimationState();
    public final AnimationState roarAnimationState = new AnimationState();
    public final AnimationState sniffAnimationState = new AnimationState();
    private int roarEndTick = -1;
    private int tendrilAnimation;
    private int tendrilAnimationO;
    private int combatStartTick = -1;
    private int rageEndTick = -1;

    private int attackCounter;
    @Nullable private UUID counteringTarget;
    private int heavyAttackTick;
    private double lastDreamValue = Double.NaN;
    /** 受击后完全无敌窗口（参考受保护血量系统）：真实扣血后置 N tick，期间免疫一切伤害。 */
    private int qzkInvincibleTimer = 0;
    private static final int QZK_INVINCIBLE_TICKS = 16; // 0.8 秒

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
        EntityAttributeGate.set(this, YizAttributes.ATTACK_STRENGTH, "attack_strength", scaleDifficulty(60.0));
        EntityAttributeGate.set(this, YizAttributes.SPELL_POWER, "spell_power", scaleDifficulty(100.0));
        EntityAttributeGate.set(this, YizAttributes.LIFE_STEAL, "life_steal", scaleDifficulty(10.0));
        EntityAttributeGate.set(this, YizAttributes.DAMAGE_BLOCK, "damage_block", scaleDifficulty(1.0));
        EntityAttributeGate.set(this, YizAttributes.DAMAGE_REDUCTION, "damage_reduction", scaleDifficulty(25.0));
        // 强化(2026-08-09): 无敌帧/传导间隔 16→30 tick
        EntityAttributeGate.set(this, YizAttributes.INVINCIBILITY_MULT, "invincibility_mult", scaleDifficulty(30.0));
        EntityAttributeGate.set(this, YizAttributes.ARMOR, "armor", scaleDifficulty(15.0));
        EntityAttributeGate.set(this, YizAttributes.SPELL_DEFENSE, "spell_defense", scaleDifficulty(15.0));
        EntityAttributeGate.set(this, YizAttributes.CONDUCTION_CAP, "conduction_cap", scaleDifficulty(12.0));
        EntityAttributeGate.set(this, YizAttributes.SECURE_PULSE, "secure_pulse", 1.0);
        net.minecraft.client.yiz.tool.health.SecureHealthClosure.register(this, (float) this.getAttributeValue(Attributes.MAX_HEALTH));
        net.minecraft.client.yiz.tool.health.SecureHealthClosure.setMaxHealth(this, (float) this.getAttributeValue(Attributes.MAX_HEALTH));
        net.minecraft.client.yiz.tool.health.HealthWriteGuard.register(this);
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
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        // 拦截外部直写血量通道：外部模组绕过 setHealth()/hurt() 直接 entityData.set(DATA_HEALTH_ID, v)
        // 会触发本回调 → 立即校正回表值，防止外部接管血量显示/判定。
        if (!level().isClientSide()) {
            var vanillaHealth = net.minecraft.client.yiz.tool.health.HealthChannelScanner.getVanillaHealthAccessor();
            if (vanillaHealth != null && key.equals(vanillaHealth)) {
                float realHp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
                float written = this.getEntityData().get(vanillaHealth);
                if (written != realHp) {
                    LOGGER.info("[QZK-DEF] 血量通道被外部直写 {} -> 校正回表值 {} (gameTime={})", written, realHp, this.level().getGameTime());
                    this.getEntityData().set(vanillaHealth, realHp);
                }
            }
        }
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

    /** MeleeGoal 调用：对目标执行一次攻击。 */
    public void attackTarget(LivingEntity target) {
        PoshiBypassBridge.beginBypass();
        try {
            float baseAtk = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
            if (this.counteringTarget != null && this.counteringTarget.equals(target.getUUID())) {
                this.counteringTarget = null;
                this.level().broadcastEntityEvent(this, (byte)4);
                this.hit(target, baseAtk * 1.7f);
                return;
            }
            this.attackCounter++;
            if (this.attackCounter % 4 == 0) {
                this.performHeavyAttack();
            } else {
                this.level().broadcastEntityEvent(this, (byte)4);
                this.hit(target, baseAtk * (0.5f + this.random.nextFloat() * 0.3f));
            }
        } finally {
            PoshiBypassBridge.endBypass();
        }
    }

    private void hit(LivingEntity target, float dmg) {
        if (isObserver(target)) return;
        net.minecraft.client.yiz.tool.health.VitalitySeveranceHandler.addStackingBan(target, 5.0f, 7 * 20L);
        target.invulnerableTime = 0;
        net.minecraft.client.yiz.tool.health.EntityASMUtil.applyDreamDamage(this, target);
        target.hurt(this.damageSources().mobAttack(this), dmg);
    }

    private void performHeavyAttack() {
        this.setHeavyAttacking(true);
        this.heavyAttackTick = 7;
        this.level().broadcastEntityEvent(this, (byte)4);
        float baseAtk = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float dmg = baseAtk * (0.9f + this.random.nextFloat() * 0.4f);
        AABB aabb = this.getBoundingBox().inflate(9.0);
        List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class, aabb,
            e -> e.isAlive() && e != this && !isObserver(e) && this.distanceTo(e) <= 9.0);
        for (LivingEntity e : nearby) {
            this.hit(e, dmg);
        }
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
     * 每 tick 强制执行安全血量状态（服务端）——纯技术原理，不针对任何外部模组。
     *
     * <p>真实血量以 {@link SecureHealthClosure} 外部表为唯一来源（逻辑血量权威）。
     * vanilla {@code health} 字段与所有 Float 血量 DataParameter 只是"显示/兼容通道"，
     * 任何外部系统绕过 {@code hurt()} 直接改这些通道（直写 DataParameter / 字段 / 极端值）
     * 都会在此被校正回表值——外部无法通过篡改显示通道改变逻辑血量。</p>
     *
     * <p>这是"实体永不真正受伤"思路的通用落地（外部表血量权威）：
     * 每次 {@code baseTick}（死亡判定前）与 {@code aiStep} 双保险调用，
     * 确保本 tick 的 {@code getHealth/isAlive/isDeadOrDying} 判定基于干净状态。</p>
     */
    private void enforceSecureHealthState() {
        float realHp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
        // 1. vanilla health 字段 + 原版 DATA_HEALTH_ID 校正回表值（反射直写底层，绕过一切 setHealth override）
        try {
            net.minecraft.client.yiz.tool.health.EntityActuallyHurt.catchSetTrueHealth(this, realHp);
        } catch (Throwable ignored) {}
        // 2. 所有 Float 血量 DataParameter 通道（含外部注册的自定义通道）校正回表值
        //    ——外部直写任意 Float 血量通道篡改显示/判定，在此统一校正
        try {
            for (var channel : net.minecraft.client.yiz.tool.health.HealthChannelScanner.getAllFloatChannels(this)) {
                float cur = this.getEntityData().get(channel);
                if (cur != realHp) this.getEntityData().set(channel, realHp);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 每 tick 开头（baseTick 判定死亡前）清外部模组的死亡标记——
     * 确保本 tick 的 isDeadOrDying()/getHealth() 判定基于干净状态（SecureHealthClosure 表），
     * 而非外部字节码注入的死亡标记。
     */
    @Override
    public void baseTick() {
        if (!level().isClientSide()) {
            this.enforceSecureHealthState();
        }
        super.baseTick();
    }

    public void aiStep() {
        super.aiStep();
        this.tendrilAnimationO = this.tendrilAnimation;
        if (this.tendrilAnimation > 0) this.tendrilAnimation--;
        if (!level().isClientSide()) {
            // 强制安全血量状态：校正所有血量通道回表值（通用防御）
            this.enforceSecureHealthState();
            // 受击无敌窗口递减
            if (this.qzkInvincibleTimer > 0) this.qzkInvincibleTimer--;
            // 无敌帧到期（1.20.1 无 LivingEntityMixin.onTick，此处双保险；customServerAiStep 也调）
            net.minecraft.client.yiz.handler.AttackInvulnerabilityTracker.onTick(this, this.level().getGameTime());
            double dream = this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.2;
            if (dream != this.lastDreamValue) {
                EntityAttributeGate.set(this, YizAttributes.FIRST_DREAM, "first_dream", dream);
                this.lastDreamValue = dream;
            }
            for (int i = 0; i < skillCooldowns.length; i++) {
                if (skillCooldowns[i] > 0) skillCooldowns[i]--;
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
        // ═══ 每 tick 状态清理（1.20.1 无 LivingEntityMixin.onTick，此处手动模拟）═══
        // 无敌帧到期检查：缺这行会导致 INVINCIBILITY_MULT 激活后永不过期 → 第一次受击后永远免疫（打不中）
        long gt = this.level().getGameTime();
        net.minecraft.client.yiz.handler.AttackInvulnerabilityTracker.onTick(this, gt);
        // 硬控计时递减（STUN/FREEZE 到期移除）
        net.minecraft.client.yiz.core.StatusEffectDispatcher.tickControlTimers(this);
        // 血量外部表：死亡实体清理
        net.minecraft.client.yiz.tool.health.SecureHealthClosure.tick(this);

        // 防 DataParameter 直写秒杀：每 tick 把 vanilla health 字段 + DATA_HEALTH_ID 写回外部表真值
        try {
            float realHp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
            net.minecraft.client.yiz.tool.health.EntityActuallyHurt.catchSetTrueHealth(this, realHp);
        } catch (Throwable ignored) {}

        // 防 removed/removalReason 字段直写
        try {
            if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0 && this.isRemoved()) {
                clearForcedRemoved();
            }
        } catch (Throwable ignored) {}

        // 防 MAX_HEALTH 属性被外部改
        try {
            var hpInst = this.getAttribute(Attributes.MAX_HEALTH);
            float protectedMax = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getMaxHealth(this);
            if (hpInst != null && hpInst.getValue() != protectedMax && protectedMax > 0) {
                hpInst.setBaseValue(protectedMax);
                hpInst.removeModifiers();
            }
        } catch (Throwable ignored) {}
        this.updateTargetFromHate();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        if (this.combatStartTick < 0 && this.getTarget() != null) {
            this.combatStartTick = this.tickCount;
        }
        boolean rageCondition = this.getHealth() <= this.getMaxHealth() * 0.5
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

    // ═══ 血量外部存储（外部哈希表方案）═══

    @Override
    public float getHealth() {
        // 服务端强读外部表：绝不回退 vanilla 通道。
        // 否则外部模组直写 DATA_HEALTH_ID 通道后 getHealth() 会读通道值 → 血量被外部接管。
        if (!level().isClientSide()) {
            if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this)) {
                return net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
            }
            // 表被移除（removeAll = 死亡流程触发）→ 返回 0 保持死亡判定，不回退 vanilla 通道
            return 0.0F;
        }
        // 客户端：读 vanilla 通道用于渲染显示（真实血量以服务端表为准，S2C 同步显示）
        return super.getHealth();
    }

    private static final ThreadLocal<Boolean> INTERNAL_HEALTH_WRITE = ThreadLocal.withInitial(() -> false);

    /** 标记内部治疗路径（heal 写表），供 setHealth 放行；外部调用一律黑洞。 */
    public static void beginInternalHealthWrite() { INTERNAL_HEALTH_WRITE.set(true); }
    public static void endInternalHealthWrite() { INTERNAL_HEALTH_WRITE.remove(); }

    @Override
    public void setHealth(float health) {
        // 黑洞逻辑（参考受保护血量系统）：外部任何写入血量的操作（含黎玄纪元 setHealth 直扣）
        // 全部丢弃，不重定向到 hurt、不改表。真实血量只由内部路径管理：
        //   - hurt() 扣表（SecureHealthClosure.setHealth）
        //   - heal() 写表（经 beginInternalHealthWrite 标记）
        if (level().isClientSide()) { super.setHealth(health); return; }
        if (INTERNAL_HEALTH_WRITE.get()) {
            float current = getHealth();
            if (health >= current) {
                net.minecraft.client.yiz.tool.health.SecureHealthClosure.setHealth(this, health);
            }
            INTERNAL_HEALTH_WRITE.remove();
            return;
        }
        // 外部写入（扣血或治疗）：全部丢弃，只允许内部管理
    }

    @Override
    public boolean isAlive() {
        // 统一走 getHealth()（服务端强读表、未注册=0），避免 isAlive 与 isDeadOrDying 语义不一致
        return !isRemoved() && getHealth() > 0;
    }

    private static final ThreadLocal<Boolean> FORCE_REMOVE = ThreadLocal.withInitial(() -> false);

    public static void beginForceRemove() { FORCE_REMOVE.set(true); }
    public static void endForceRemove() { FORCE_REMOVE.remove(); }

    private static java.lang.reflect.Field REMOVAL_REASON_FIELD;
    private static java.lang.reflect.Field REMOVED_FIELD;
    private static volatile boolean REMOVED_FIELDS_READY;

    @Override
    public boolean saveAsPassenger(net.minecraft.nbt.CompoundTag compound) {
        if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            String s = this.getEncodeId();
            if (s == null) return false;
            compound.putString("id", s);
            this.saveWithoutId(compound);
            return true;
        }
        return super.saveAsPassenger(compound);
    }

    private void clearForcedRemoved() {
        if (!REMOVED_FIELDS_READY) {
            try {
                REMOVAL_REASON_FIELD = Entity.class.getDeclaredField("removalReason");
                REMOVAL_REASON_FIELD.setAccessible(true);
                REMOVED_FIELD = Entity.class.getDeclaredField("removed");
                REMOVED_FIELD.setAccessible(true);
                REMOVED_FIELDS_READY = true;
            } catch (Throwable ignored) {}
        }
        if (REMOVAL_REASON_FIELD == null || REMOVED_FIELD == null) return;
        try {
            REMOVAL_REASON_FIELD.set(this, null);
            REMOVED_FIELD.setBoolean(this, false);
        } catch (Throwable ignored) {}
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide()
                && !FORCE_REMOVE.get()
                && net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            return;
        }
        super.remove(reason);
    }

    @Override
    public boolean shouldBeSaved() {
        boolean reg = net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this);
        float hp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
        if (reg && hp > 0) {
            return true;
        }
        return super.shouldBeSaved();
    }

    @Override
    public void setPose(net.minecraft.world.entity.Pose pose) {
        if (pose == Pose.DYING && !level().isClientSide()
                && net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            return;
        }
        super.setPose(pose);
    }

    @Override
    protected void dropAllDeathLoot(net.minecraft.world.damagesource.DamageSource source) {
        if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            return;
        }
        super.dropAllDeathLoot(source);
    }

    @Override
    public boolean isDeadOrDying() {
        return net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) <= 0;
    }

    /**
     * 拦截外部模组绕过 {@code hurt()} 直接调 {@code LivingEntity.actuallyHurt()} 的扣血
     * （1.20.1 的 actuallyHurt 是 protected，外部模组可反射/子类调用）。
     * 重定向到 {@link #hurt} 走传导限伤（衰减→限伤→CD）——外部直接 actuallyHurt 打不穿。
     */
    @Override
    protected void actuallyHurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // 完全接管：不调 super.actuallyHurt（那会直接改 vanilla 血量字段绕过传导限伤）。
        // 重定向到 hurt() 走完整传导链；hurt() 内部 CD 内 return false → 实际不掉血。
        this.hurt(source, amount);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) return false;
        if (amount <= 0) return false;
        // 受击后完全无敌窗口（参考受保护血量系统）：每次真实受伤后 N tick 免疫一切伤害，
        // 防止外部高频磨血。invincibleTimer 由扣血成功处设置，每 tick 递减。
        if (this.qzkInvincibleTimer > 0) {
            return false;
        }
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

        float reduced = amount;
        var redInst = this.getAttribute(net.minecraft.client.yiz.attribute.YizAttributes.DAMAGE_REDUCTION.get());
        if (redInst != null && redInst.getValue() > 0)
            reduced *= (float) (1.0 - Math.min(1.0, redInst.getValue() / 100.0));
        var blockInst = this.getAttribute(net.minecraft.client.yiz.attribute.YizAttributes.DAMAGE_BLOCK.get());
        if (blockInst != null && blockInst.getValue() > 0)
            reduced = Math.max(0, reduced - (float) blockInst.getValue());
        var capInst = this.getAttribute(net.minecraft.client.yiz.attribute.YizAttributes.CONDUCTION_CAP.get());
        double capPercent = capInst != null ? capInst.getValue() : 0;
        if (capPercent <= 0) capPercent = 25.0;
        // 动态传导限伤：血越低限得越狠（满血 12% 上限，残血趋近 3 点）——参考受保护血量系统的传导引擎。
        // 固定 cap 在残血时仍放行大额伤害 → 被高频磨血磨穿；动态 cap 让残血阶段每次最多扣 3 点。
        float current = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
        float maxHp = this.getMaxHealth();
        float baseCap = Math.max(3.0f, (float) (maxHp * capPercent / 100.0));
        float ratio = maxHp > 0 ? (current / maxHp) : 0.5f;   // 0~1，满血=1 残血→0
        float dynamicCap = Math.max(3.0f, baseCap * (0.3f + 0.7f * ratio)); // 满血≈baseCap，残血→3
        float limited = Math.min(reduced, dynamicCap);
        if (limited <= 0) return false;

        float next = Math.max(0, current - limited);
        net.minecraft.client.yiz.tool.health.SecureHealthClosure.setHealth(this, next);
        long nowTick = this.level().getGameTime();
        this.lastConductionHitTick = nowTick;
        // 受击后完全无敌窗口：本次真实扣血后 N tick 免疫一切伤害（防高频磨血）
        this.qzkInvincibleTimer = QZK_INVINCIBLE_TICKS;

        this.hurtTime = 10;
        this.hurtDuration = 10;
        CONDUCTION_HIT_FLASH.set(true);
        try {
            // 1.20.1 差异：Level.broadcastDamageEvent 是空实现，必须调 ServerLevel 版本才发红闪包
            if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.broadcastDamageEvent(this, source);
            }
        } finally {
            CONDUCTION_HIT_FLASH.remove();
        }

        net.minecraft.client.yiz.handler.AttackInvulnerabilityTracker.onHurtSuccess(this, this.level().getGameTime());

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
    @Override
    public void kill() {
        if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            return; // 血量未归零：拒绝外部 kill() 秒杀
        }
        super.kill();
    }

    /**
     * 拦截外部反向 heal（heal(负数) 扣血）与 heal 强制扣血。
     * 正向治疗放行（回血合法）；负值/扣血方向重定向 hurt 走传导限伤。
     */
    @Override
    public void heal(float healAmount) {
        if (healAmount < 0) {
            // 外部模组用负 heal 扣血 → 黑洞丢弃（不重定向，避免磨血）
            return;
        }
        // 正向治疗：标记内部写表，放行 setHealth（否则被黑洞吞掉）
        beginInternalHealthWrite();
        try {
            super.heal(healAmount);
        } finally {
            endInternalHealthWrite();
        }
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (!level().isClientSide()) {
            float hp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
            if (hp > 0.0F) return;

            this.bossEvent.removeAllPlayers();
            QuanshouzheSkillManager.clear(this.getUUID());
            if (!this.isRemoved() && !this.dead) {
                net.minecraft.world.entity.LivingEntity killCredit = this.getKillCredit();
                if (this.deathScore >= 0 && killCredit != null) {
                    killCredit.awardKillScore(this, this.deathScore, source);
                }
                if (this.isSleeping()) this.stopSleeping();
                this.dead = true;
                this.getCombatTracker().recheckStatus();
                if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    this.dropAllDeathLoot(source);
                    this.level().broadcastEntityEvent(this, (byte) 3);
                }
                this.setPose(net.minecraft.world.entity.Pose.DYING);
            }
        }
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
