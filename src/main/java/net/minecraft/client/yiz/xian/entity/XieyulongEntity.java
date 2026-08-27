package net.minecraft.client.yiz.xian.entity;

import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.tool.attribute.EntityAttributeGate;
import net.minecraft.client.yiz.xian.entity.ai.XieyulongFlightMoveControl;
import net.minecraft.client.yiz.xian.entity.ai.XieyulongFlightPursuitGoal;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes;
import net.minecraft.client.yiz.xian.entity.skill.XieyulongFireEntity;
import net.minecraft.client.yiz.xian.entity.skill.XieyulongMeteorEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * 邪狱龙（踏虚体邪狱龙）— 1.20.1 版，GeckoLib 动画实体。
 *
 * <p>继承 {@link YizxianMob} 获得全套血量保护（外部表/传导限伤/不死守卫/混淆血量串），
 * 实现 {@link GeoEntity} 用 GeckoLib 的 geo 骨骼 + animation 关键帧渲染（不再走原版 ModelPart）。</p>
 *
 * <p>M2 飞行：fly 动画 + 飞行移动控制 + 起降决策 + 追击 AI；三技能、踏虚体影分身后续接入。</p>
 */
public class XieyulongEntity extends YizxianMob implements GeoEntity {

    /** 是否处于飞行状态（驱动 fly 动画；M1 恒 false，飞行 AI 在 M2 接入）。 */
    private static final EntityDataAccessor<Boolean> IS_FLYING =
        SynchedEntityData.defineId(XieyulongEntity.class, EntityDataSerializers.BOOLEAN);

    /** 踏虚体（影分身暗形态）：true 用 dark 纹理。 */
    private static final EntityDataAccessor<Boolean> IS_SHADOW =
        SynchedEntityData.defineId(XieyulongEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ANIM_WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ANIM_FLY = RawAnimation.begin().thenLoop("fly");

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    // 护甲/法防指数减伤参数（与辖界者一致：锚定 x=20→50%、x=50→75%）
    private static final double EXP_REDUCTION_BASE = 40.0;
    private static final double EXP_REDUCTION_EXP = Math.log(2.0) / Math.log(1.0 + 20.0 / EXP_REDUCTION_BASE);
    /** 按攻击者累积的格挡加成：每次被同一实体攻击，对其实体格挡 +1。 */
    private final Map<UUID, Integer> attackerBlockBonus = new HashMap<>();

    /** Boss 血条。 */
    private final ServerBossEvent bossEvent = new ServerBossEvent(
        Component.literal("邪狱龙"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    /** 无目标后的降落冷却结束 tick（0=未开始计时）。 */
    private int flightCooldownEndTick;
    /** 正在降落（无目标且飞行中，冷却结束后下落，着地停飞）。 */
    private boolean isLanding;
    /** 火球冷却（tick）。 */
    private int fireballCooldown;
    /** 陨石冷却（tick）。 */
    private int meteorCooldown;
    /** 近战冷却（tick）。 */
    private int meleeCooldown;
    /** 踏虚体影分身是否已生成（半血一次性触发）。 */
    private boolean shadowCloneSpawned;

    public XieyulongEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        this.moveControl = new XieyulongFlightMoveControl(this);
        this.bossEvent.setDarkenScreen(true);
        this.bossEvent.setPlayBossMusic(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_FLYING, false);
        this.entityData.define(IS_SHADOW, false);
    }

    public boolean isFlying() {
        return this.entityData.get(IS_FLYING);
    }

    public void setFlying(boolean flying) {
        this.entityData.set(IS_FLYING, flying);
    }

    public boolean isShadowForm() {
        return this.entityData.get(IS_SHADOW);
    }

    public void setShadowForm(boolean shadow) {
        this.entityData.set(IS_SHADOW, shadow);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 飞行追击（有目标且飞行时朝目标移动）
        this.goalSelector.addGoal(1, new XieyulongFlightPursuitGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        // 主动索敌所有实体（除创造玩家/同类）；mustSee=false 无需视线（源 FoundTargetGoal）
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, false,
            target -> !(target instanceof Player p && p.isCreative()) && target.getType() != this.getType()));
    }

    /** 飞行速度（飞行移动控制用）。 */
    public float getFlyingSpeed() {
        return 0.3f;
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide()) {
            this.clearInvalidTarget();
            // 每 tick 回血已属性化：LIFE_REGEN_RATE=25 → ticker 25×0.05=1.25/秒（AttributeEffectTicker 每 tick 应用）
            this.updateFlightState();
            this.updateSkills();
            this.updateMelee();
            this.updateShadowClone();
            // Boss 血条进度（读权威表，防外部 agent 压 getHealth 影响血条）
            float bossHp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
            float bossMax = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getMaxHealth(this);
            this.bossEvent.setProgress(bossMax > 0 ? bossHp / bossMax : 0);
        }
        super.tick();
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

    /** 目标若为创造模式玩家则清除（不攻击也不锁定）。 */
    private void clearInvalidTarget() {
        if (this.getTarget() instanceof Player p && p.isCreative()) {
            this.setTarget(null);
        }
    }

    /** 起降决策：有存活目标起飞（上抛 + 飞行）；无目标且飞行中，冷却 300 tick 后降落，着地停飞。 */
    private void updateFlightState() {
        LivingEntity target = this.getTarget();
        boolean hasTarget = target != null && target.isAlive();
        if (hasTarget) {
            if (!this.isFlying()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.6, 0.0));
                this.setFlying(true);
            }
            this.flightCooldownEndTick = 0;
            this.isLanding = false;
        } else if (this.isFlying()) {
            if (!this.isLanding) {
                if (this.flightCooldownEndTick == 0) {
                    this.flightCooldownEndTick = this.tickCount + 300;
                }
                if (this.tickCount >= this.flightCooldownEndTick) {
                    this.isLanding = true;
                }
            }
            if (this.isLanding) {
                if (this.onGround()) {
                    this.setFlying(false);
                    this.isLanding = false;
                    this.flightCooldownEndTick = 0;
                } else {
                    Vec3 vel = this.getDeltaMovement();
                    this.setDeltaMovement(vel.x, Math.max(vel.y, -0.2), vel.z);
                }
            }
        }
    }

    /** 三技能循环：有目标时火球（40 tick）与陨石（80 tick）交替施放。 */
    private void updateSkills() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        if (this.fireballCooldown > 0) this.fireballCooldown--;
        if (this.meteorCooldown > 0) this.meteorCooldown--;
        if (this.fireballCooldown <= 0) {
            if (net.minecraft.client.yiz.tool.health.ManaTracker.consume(this, 30)) {
                this.fireSkillEntity(target);
                this.fireballCooldown = 40;
            }
        }
        if (this.meteorCooldown <= 0) {
            if (net.minecraft.client.yiz.tool.health.ManaTracker.consume(this, 50)) {
                this.spawnMeteor(target);
                this.meteorCooldown = 80;
            }
        }
    }

    /** 火球：从头部前方朝目标发射（命中爆炸 + 毒云）。 */
    private void fireSkillEntity(LivingEntity target) {
        XieyulongFireEntity fire = new XieyulongFireEntity(
            YizxianEntityTypes.XIEYULONG_FIRE.get(), this.level());
        fire.setOwner(this);
        fire.setMaxAge(80);
        Vec3 start = this.getFireStartPos();
        fire.setPos(start.x, start.y, start.z);
        Vec3 dir = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(start);
        fire.setMoveDirection(dir);
        this.level().addFreshEntity(fire);
    }

    /** 陨石：在目标头顶随机偏移处生成，下落命中爆炸 + 毒云。 */
    private void spawnMeteor(LivingEntity target) {
        double offsetX = (this.random.nextDouble() - 0.5) * 16.0;
        double offsetZ = (this.random.nextDouble() - 0.5) * 16.0;
        double spawnY = target.getY() + 20.0 + this.random.nextDouble() * 10.0;
        XieyulongMeteorEntity meteor = new XieyulongMeteorEntity(
            YizxianEntityTypes.XIEYULONG_METEOR.get(), this.level());
        meteor.setOwner(this);
        meteor.setPos(target.getX() + offsetX, spawnY, target.getZ() + offsetZ);
        meteor.setDamageRadius(7.0f);
        meteor.setMaxAge(200);
        this.level().addFreshEntity(meteor);
    }

    private Vec3 getFireStartPos() {
        Vec3 look = this.getLookAngle();
        return this.position().add(0.0, this.getBbHeight() * 0.7, 0.0).add(look.scale(1.0));
    }

    /** 近战：目标 ≤6 格时每 20 tick 打一次（源 doHurtDistance=6，走基类 doHurtTarget）。 */
    private void updateMelee() {
        if (this.meleeCooldown > 0) this.meleeCooldown--;
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        this.getLookControl().setLookAt(target, 30f, 30f);
        double attackRange = 6.0;
        if (this.meleeCooldown <= 0 && this.distanceToSqr(target) <= attackRange * attackRange) {
            this.doHurtTarget(target);
            this.meleeCooldown = 20;
        }
    }

    /** 踏虚体影分身：半血时一次性生成 dark 纹理克隆（攻击减半、速度更快、跟随同一目标）。 */
    private void updateShadowClone() {
        if (this.shadowCloneSpawned || this.isShadowForm()) return;
        float maxHp = this.getMaxHealth();
        float hp = this.getHealth();
        if (maxHp > 0 && hp > 0 && hp <= maxHp * 0.5f) {
            this.shadowCloneSpawned = true;
            this.spawnShadowClone();
        }
    }

    private void spawnShadowClone() {
        XieyulongEntity clone = YizxianEntityTypes.XIEYULONG.get().create(this.level());
        if (clone == null) return;
        clone.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
        clone.setShadowForm(true);
        var atk = clone.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) {
            atk.setBaseValue(this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5);
        }
        clone.setTarget(this.getTarget());
        this.level().addFreshEntity(clone);
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isFlying() && this.isAlive()) {
            if (this.isInWater()) {
                this.moveRelative(0.02f, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
            } else if (this.isInLava()) {
                this.moveRelative(0.02f, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
            } else {
                // 有目标朝前飞；无目标原地悬停（不前飞，仅垂直校正到地面以上 8 格）
                LivingEntity target = this.getTarget();
                boolean hasTarget = target != null && target.isAlive();
                float yawRad = this.getYRot() * (float) Math.PI / 180.0f;
                double hSpeed = hasTarget ? 0.33 : 0.0;
                double vx = -Math.sin(yawRad) * hSpeed;
                double vz = Math.cos(yawRad) * hSpeed;
                Vec3 selfPos = this.position();
                double groundY = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                    (int) Math.floor(selfPos.x), (int) Math.floor(selfPos.z));
                double desiredY = groundY + 8.0;
                if (hasTarget) {
                    desiredY = Math.max(desiredY, target.getY() + 2.0);
                }
                double vy = Mth.clamp((desiredY - selfPos.y) * 0.15, -0.2, 0.2);
                Vec3 prevVel = this.getDeltaMovement();
                double smooth = 0.4;
                this.setDeltaMovement(
                    prevVel.x + (vx - prevVel.x) * smooth,
                    prevVel.y + (vy - prevVel.y) * smooth,
                    prevVel.z + (vz - prevVel.z) * smooth);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.95));
            }
        } else {
            super.travel(travelVector);
        }
    }

    // ---- GeckoLib 动画接入 ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movementPredicate));
    }

    private PlayState movementPredicate(AnimationState<XieyulongEntity> state) {
        if (this.isFlying()) {
            state.setControllerSpeed(1.0f);
            return state.setAndContinue(ANIM_FLY);
        }
        if (state.isMoving()) {
            state.setControllerSpeed(1.0f);
            return state.setAndContinue(ANIM_WALK);
        }
        return state.setAndContinue(ANIM_IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ---- 属性 ----

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 525.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.ATTACK_DAMAGE, 210.0)
            .add(Attributes.ARMOR, 30.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.FOLLOW_RANGE, 80.0);
        // 标准自定义属性集（基值 0，含护甲穿透/基础回血；数值由 applyEntityAttributes 经 EntityAttributeGate 分配）+ 法力
        addStandardCustomAttributes(builder);
        addManaAttributes(builder);
        return builder;
    }

    /** 分配邪狱龙受保护自定义属性（生成/加载后第一 tick 由 YizxianMob.aiStep 调用一次）。 */
    @Override
    protected void applyEntityAttributes() {
        applyVanillaDifficultyScale();
        setAttr(YizAttributes.ATTACK_STRENGTH, "attack_strength", scaleDifficulty(60.0));
        setAttr(YizAttributes.SPELL_POWER, "spell_power", 100.0);
        setAttr(YizAttributes.LIFE_STEAL, "life_steal", scaleDifficulty(10.0));
        setAttr(YizAttributes.DAMAGE_BLOCK, "damage_block", scaleDifficulty(5.0));
        setAttr(YizAttributes.DAMAGE_REDUCTION, "damage_reduction", scaleDifficulty(45.0));
        setAttr(YizAttributes.INVINCIBILITY_MULT, "invincibility_mult", 24.0);
        setAttr(YizAttributes.ARMOR, "armor", scaleDifficulty(30.0));
        setAttr(YizAttributes.SPELL_DEFENSE, "spell_defense", scaleDifficulty(30.0));
        setAttr(YizAttributes.CONDUCTION_CAP, "conduction_cap", 40.0);
        setAttr(YizAttributes.SECURE_PULSE, "secure_pulse", 1.0);
        // 涨跌多空 = 攻击力 × 70% 转换率
        double dream = this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.7;
        setAttr(YizAttributes.FIRST_DREAM, "long_short", dream);
        // 法力：实体技能系统接入法力
        setAttr(YizAttributes.MAX_MANA, "max_mana", 200.0);
        setAttr(YizAttributes.MANA_REGEN, "mana_regen", 20.0);
        // 基础回血属性化（每 tick 1.25，rate=25 → ticker 25×0.05）
        setAttr(YizAttributes.LIFE_REGEN_RATE, "life_regen_rate", 25.0);
    }

    private void setAttr(net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                         String idKey, double value) {
        EntityAttributeGate.set(this, attr, idKey, value);
        net.minecraft.client.yiz.tool.attribute.AttributeStandardizer.registerStandard(this, attr.get(), idKey, value);
    }

    // ---- 伤害链（照辖界者：指数减免 → 百分比减免 → 固定格挡+按攻击者累积 → 传导限伤） ----

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
        // 护甲/法防指数减免（物理→ARMOR，其余→SPELL_DEFENSE；近战判物理走 ARMOR）
        net.minecraft.world.entity.Entity directHit = source.getDirectEntity();
        boolean isMelee = directHit instanceof net.minecraft.world.entity.LivingEntity
            && directHit == source.getEntity();
        boolean isPhysical = source.is(DamageTypeTags.IS_PROJECTILE)
            || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FALL)
            || isMelee;
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
        // 固定格挡（基础 DAMAGE_BLOCK + 按攻击者累积的 +1）
        float block = 0f;
        var blockInst = this.getAttribute(YizAttributes.DAMAGE_BLOCK.get());
        if (blockInst != null) block = (float) blockInst.getValue();
        if (source.getEntity() instanceof LivingEntity attacker) {
            block += this.attackerBlockBonus.getOrDefault(attacker.getUUID(), 0);
            this.attackerBlockBonus.merge(attacker.getUUID(), 1, Integer::sum);
        }
        if (block > 0) reduced = Math.max(0, reduced - block);
        // 传导限伤
        float limited = Math.min(reduced, conductionCap());
        if (limited <= 0) return false;
        float current = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
        float next = Math.max(0, current - limited);
        net.minecraft.client.yiz.tool.health.SecureHealthClosure.setHealth(this, next);
        // 攻击者吸血（secure 自管 hurt 绕过 mixin onHurtReturn，此处补）
        net.minecraft.client.yiz.tool.health.EntityASMUtil.applyLifesteal(source.getEntity(), limited);
        net.minecraft.client.yiz.tool.health.EntityActuallyHurt.catchSetTrueHealth(this, next);
        this.lastConductionHitTick = this.level().getGameTime();
        this.invulnerableTime = (int) conductionHitCdTicks();
        this.hurtTime = 10;
        this.hurtDuration = 10;
        this.broadcastHurtFlash(source);
        if (next <= 0) this.die(source);
        return true;
    }

    /** 近战命中额外附加涨跌多空伤害（攻击 × 70%）。 */
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
}
