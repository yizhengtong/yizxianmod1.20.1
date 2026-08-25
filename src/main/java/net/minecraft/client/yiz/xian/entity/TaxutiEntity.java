package net.minecraft.client.yiz.xian.entity;

import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.tool.attribute.EntityAttributeGate;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes;
import net.minecraft.client.yiz.xian.entity.skill.TaxutiOrbEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;

/**
 * 踏虚体（虚末麟/kirin）— 1.20.1 版，GeckoLib 动画 Boss。
 *
 * <p>与邪狱龙（飞行）不同，踏虚体是<b>地面近战 Boss</b>：高攻 155 / 410 血 / 80 索敌，
 * 主动索敌玩家 + 传送突袭 + orb 球范围技能 + 半血影分身（暗形态）+ 每 tick 回血。</p>
 *
 * <p>继承 {@link YizxianMob} 获得全套血量保护；实现 {@link GeoEntity} 用 GeckoLib 渲染。</p>
 */
public class TaxutiEntity extends YizxianMob implements GeoEntity {

    /** 踏虚体（影分身暗形态）：true 用 dark 纹理。 */
    private static final EntityDataAccessor<Boolean> IS_SHADOW =
        SynchedEntityData.defineId(TaxutiEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    // 护甲/法防指数减伤参数（与辖界者一致：锚定 x=20→50%、x=50→75%）
    private static final double EXP_REDUCTION_BASE = 40.0;
    private static final double EXP_REDUCTION_EXP = Math.log(2.0) / Math.log(1.0 + 50.0 / EXP_REDUCTION_BASE);
    /** 按攻击者累积的格挡加成：每次被同一实体攻击，对其实体格挡 +1。 */
    private final Map<UUID, Integer> attackerBlockBonus = new HashMap<>();

    /** Boss 血条。 */
    private final ServerBossEvent bossEvent = new ServerBossEvent(
        Component.literal("踏虚体"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

    private int orbCooldown;
    private int teleportCooldown;
    private int meleeCooldown;
    private boolean shadowCloneSpawned;

    public TaxutiEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.xpReward = 50;
        this.bossEvent.setDarkenScreen(true);
        this.bossEvent.setPlayBossMusic(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_SHADOW, false);
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
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        // 主动索敌所有实体（除创造玩家/同类）；mustSee=false 无需视线（源 FoundTargetGoal）
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, false,
            target -> !(target instanceof Player p && p.isCreative()) && target.getType() != this.getType()));
    }

    // ---- GeckoLib ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(ANIM_IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ---- 属性 ----

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 410.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.ATTACK_DAMAGE, 155.0)
            .add(Attributes.FOLLOW_RANGE, 80.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.ARMOR, 0.0)
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

    @Override
    protected void applyEntityAttributes() {
        applyVanillaDifficultyScale();
        setAttr(YizAttributes.ATTACK_STRENGTH, "attack_strength", scaleDifficulty(60.0));
        setAttr(YizAttributes.SPELL_POWER, "spell_power", 100.0);
        setAttr(YizAttributes.LIFE_STEAL, "life_steal", scaleDifficulty(10.0));
        setAttr(YizAttributes.DAMAGE_BLOCK, "damage_block", scaleDifficulty(3.0));
        setAttr(YizAttributes.DAMAGE_REDUCTION, "damage_reduction", scaleDifficulty(25.0));
        setAttr(YizAttributes.INVINCIBILITY_MULT, "invincibility_mult", 24.0);
        setAttr(YizAttributes.ARMOR, "armor", scaleDifficulty(30.0));
        setAttr(YizAttributes.SPELL_DEFENSE, "spell_defense", scaleDifficulty(30.0));
        setAttr(YizAttributes.CONDUCTION_CAP, "conduction_cap", 40.0);
        setAttr(YizAttributes.SECURE_PULSE, "secure_pulse", 1.0);
        // 涨跌多空 = 攻击力 × 50% 转换率
        double dream = this.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5;
        setAttr(YizAttributes.FIRST_DREAM, "long_short", dream);
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
        net.minecraft.client.yiz.tool.health.EntityActuallyHurt.catchSetTrueHealth(this, next);
        this.lastConductionHitTick = this.level().getGameTime();
        this.invulnerableTime = (int) conductionHitCdTicks();
        this.hurtTime = 10;
        this.hurtDuration = 10;
        this.broadcastHurtFlash(source);
        if (next <= 0) this.die(source);
        return true;
    }

    /** 近战命中额外附加涨跌多空伤害（攻击 × 50%）。 */
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
        if (!this.level().isClientSide()) {
            this.serverTick();
        }
        super.tick();
    }

    private void serverTick() {
        this.clearInvalidTarget();
        // 每 tick 回血
        if (this.getHealth() < this.getMaxHealth() && this.isAlive()) {
            this.heal(1.25f);
        }
        this.updateSkills();
        this.updateTeleport();
        this.updateMelee();
        this.updateShadowClone();
        // Boss 血条进度（读权威表，防外部 agent 压 getHealth 影响血条）
        float bossHp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
        float bossMax = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getMaxHealth(this);
        this.bossEvent.setProgress(bossMax > 0 ? bossHp / bossMax : 0);
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

    /** orb 球技能：有目标时每 330 tick 施放一次（范围百分比扣血 + 凋零）。 */
    private void updateSkills() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        if (this.orbCooldown > 0) this.orbCooldown--;
        if (this.orbCooldown <= 0) {
            this.spawnOrbAt(target);
            this.orbCooldown = 330;
        }
    }

    private void spawnOrbAt(LivingEntity target) {
        TaxutiOrbEntity orb = new TaxutiOrbEntity(YizxianEntityTypes.TAXUTI_ORB.get(), this.level());
        orb.setOwner(this);
        orb.setMaxAge(70);
        orb.setOrbRadius(6.0f);
        orb.setPos(target.getX(), target.getY() + 0.5, target.getZ());
        this.level().addFreshEntity(orb);
    }

    /** 传送突袭：目标 >10 格时每 300 tick 传送到目标处。 */
    private void updateTeleport() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        if (this.teleportCooldown > 0) this.teleportCooldown--;
        if (this.teleportCooldown <= 0 && this.distanceToSqr(target) > 100.0) {
            withGate(() -> this.teleportTo(target.getX(), target.getY(), target.getZ()));
            this.teleportCooldown = 300;
        }
    }

    /** 近战：目标 ≤3 格时打（源 doHurtDistance=3），超出则走向目标 + 面向目标。 */
    private void updateMelee() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) return;
        this.getLookControl().setLookAt(target, 30f, 30f);
        double attackRange = 3.0;
        double distSq = this.distanceToSqr(target);
        if (distSq > attackRange * attackRange) {
            this.getNavigation().moveTo(target, 1.0);
        }
        if (this.meleeCooldown > 0) this.meleeCooldown--;
        if (this.meleeCooldown <= 0 && distSq <= attackRange * attackRange) {
            this.doHurtTarget(target);
            this.meleeCooldown = 20;
        }
    }

    /** 影分身：半血时一次性生成 dark 纹理克隆。 */
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
        TaxutiEntity clone = YizxianEntityTypes.TAXUTI.get().create(this.level());
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
}
