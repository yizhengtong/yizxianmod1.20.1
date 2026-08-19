package net.minecraft.client.yiz.xian.entity.skill;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 踏虚体 orb 球。原地膨胀的能量球，持续范围内敌人扣百分比血量 + 施加凋零。无渲染器（vanilla 粒子显示）。
 */
public class TaxutiOrbEntity extends Entity {

    private static final EntityDataAccessor<Integer> MAX_AGE =
        SynchedEntityData.defineId(TaxutiOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ORB_RADIUS =
        SynchedEntityData.defineId(TaxutiOrbEntity.class, EntityDataSerializers.FLOAT);

    private static final float MAX_HP_PERCENT = 0.05f;  // 每 tick 扣目标最大生命 5%（攻击）+ 5%（涨跌多空真实伤害）
    private static final int WITHER_DURATION = 200;
    private static final int WITHER_AMPLIFIER = 1;

    private LivingEntity owner;
    private int age;

    public TaxutiOrbEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public void setOwner(LivingEntity owner) {
        this.owner = owner;
    }

    public void setMaxAge(int maxAge) {
        this.entityData.set(MAX_AGE, maxAge);
    }

    public void setOrbRadius(float radius) {
        this.entityData.set(ORB_RADIUS, Math.max(0.5f, radius));
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(MAX_AGE, 100);
        this.entityData.define(ORB_RADIUS, 3.0f);
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;
        if (this.level().isClientSide()) {
            this.clientTick();
        } else {
            this.serverTick();
        }
    }

    private void clientTick() {
        float radius = this.entityData.get(ORB_RADIUS);
        if (this.age % 3 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2.0;
                double dist = radius * this.random.nextDouble();
                this.level().addParticle(ParticleTypes.ENCHANT,
                    this.getX() + Math.cos(angle) * dist,
                    this.getY() + this.random.nextDouble() * 2.0,
                    this.getZ() + Math.sin(angle) * dist,
                    0.0, 0.05, 0.0);
            }
        }
    }

    private void serverTick() {
        float radius = this.entityData.get(ORB_RADIUS);
        AABB area = new AABB(this.getX() - radius, this.getY() - radius, this.getZ() - radius,
            this.getX() + radius, this.getY() + radius, this.getZ() + radius);
        for (LivingEntity t : this.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != this.owner && !(e instanceof net.minecraft.client.yiz.xian.entity.base.YizxianMob))) {
            float hpPart = t.getMaxHealth() * MAX_HP_PERCENT;
            if (this.owner != null) {
                // 攻击部分：目标最大生命 5%（普通伤害）
                t.hurt(t.damageSources().mobAttack(this.owner), hpPart);
                // 涨跌多空部分：目标最大生命 5%（真实伤害）
                net.minecraft.client.yiz.tool.health.EntityASMUtil.applyDreamDamage(this.owner, t, hpPart);
            }
            t.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_DURATION, WITHER_AMPLIFIER));
        }
        if (this.age >= this.entityData.get(MAX_AGE)) {
            this.discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.age);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getInt("Age");
    }
}
