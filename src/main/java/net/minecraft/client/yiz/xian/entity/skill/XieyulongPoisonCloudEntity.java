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
 * 邪狱龙毒云。火球/陨石爆炸后留下的范围毒区：持续中毒 + 周期性伤害。无渲染器（vanilla 粒子显示）。
 */
public class XieyulongPoisonCloudEntity extends Entity {

    private static final EntityDataAccessor<Integer> MAX_AGE =
        SynchedEntityData.defineId(XieyulongPoisonCloudEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> CLOUD_RADIUS =
        SynchedEntityData.defineId(XieyulongPoisonCloudEntity.class, EntityDataSerializers.FLOAT);

    private static final float DEFAULT_DREAM_RATE = 0.5f;
    private static final int POISON_DURATION = 100;
    private static final int POISON_AMPLIFIER = 1;

    private LivingEntity owner;
    private int age;
    private float dreamRate = DEFAULT_DREAM_RATE;

    public XieyulongPoisonCloudEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public void setOwner(LivingEntity owner) {
        this.owner = owner;
    }

    public void setMaxAge(int maxAge) {
        this.entityData.set(MAX_AGE, maxAge);
    }

    public void setCloudRadius(float radius) {
        this.entityData.set(CLOUD_RADIUS, Math.max(0.5f, radius));
    }

    /** 每 tick 涨跌多空伤害倍率（火球 50% / 陨石 70%）。 */
    public void setDreamRate(float rate) {
        this.dreamRate = rate;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(MAX_AGE, 100);
        this.entityData.define(CLOUD_RADIUS, 4.0f);
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
        float radius = this.entityData.get(CLOUD_RADIUS);
        if (this.age % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2.0;
                double dist = radius * this.random.nextDouble();
                this.level().addParticle(ParticleTypes.SMOKE,
                    this.getX() + Math.cos(angle) * dist,
                    this.getY() + this.random.nextDouble() * 2.0,
                    this.getZ() + Math.sin(angle) * dist,
                    (this.random.nextDouble() - 0.5) * 0.1,
                    this.random.nextDouble() * 0.1,
                    (this.random.nextDouble() - 0.5) * 0.1);
            }
        }
    }

    private void serverTick() {
        float radius = this.entityData.get(CLOUD_RADIUS);
        AABB area = new AABB(this.getX() - radius, this.getY() - 0.5, this.getZ() - radius,
            this.getX() + radius, this.getY() + 2.0, this.getZ() + radius);
        for (LivingEntity t : this.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != this.owner && !(e instanceof net.minecraft.client.yiz.xian.entity.base.YizxianMob))) {
            t.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_DURATION, POISON_AMPLIFIER));
            if (this.owner != null) {
                double dream = this.owner.getAttributeValue(net.minecraft.client.yiz.attribute.YizAttributes.FIRST_DREAM.get()) * this.dreamRate;
                net.minecraft.client.yiz.tool.health.EntityASMUtil.applyDreamDamage(this.owner, t, dream);
            }
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
