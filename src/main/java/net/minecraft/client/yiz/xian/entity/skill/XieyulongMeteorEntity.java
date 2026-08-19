package net.minecraft.client.yiz.xian.entity.skill;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 邪狱龙陨石。从天而降，着地爆炸：范围真实伤害 + 生成毒云。无渲染器（vanilla 粒子显示）。
 */
public class XieyulongMeteorEntity extends Entity {

    private static final EntityDataAccessor<Integer> MAX_AGE =
        SynchedEntityData.defineId(XieyulongMeteorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DAMAGE_RADIUS =
        SynchedEntityData.defineId(XieyulongMeteorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> HAS_IMPACTED =
        SynchedEntityData.defineId(XieyulongMeteorEntity.class, EntityDataSerializers.BOOLEAN);

    private static final float DEFAULT_DAMAGE_RADIUS = 7.0f;
    private static final float METEOR_RATE = 0.7f;   // 命中 = 攻击 × 70%
    private static final float CLOUD_DREAM_RATE = 0.7f; // 毒云每 tick = 涨跌多空 × 70%

    private LivingEntity owner;
    private int age;

    public XieyulongMeteorEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public void setOwner(LivingEntity owner) {
        this.owner = owner;
    }

    public void setMaxAge(int maxAge) {
        this.entityData.set(MAX_AGE, maxAge);
    }

    public void setDamageRadius(float radius) {
        this.entityData.set(DAMAGE_RADIUS, Math.max(0.5f, radius));
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(MAX_AGE, 400);
        this.entityData.define(DAMAGE_RADIUS, DEFAULT_DAMAGE_RADIUS);
        this.entityData.define(HAS_IMPACTED, false);
    }

    private boolean hasImpacted() {
        return this.entityData.get(HAS_IMPACTED);
    }

    private void setHasImpacted(boolean impacted) {
        this.entityData.set(HAS_IMPACTED, impacted);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(0.2f, 0.2f);
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
        if (this.hasImpacted()) {
            float radius = this.entityData.get(DAMAGE_RADIUS);
            // 着地：大爆炸（EXPLOSION 闪 + 扩散火环 + LARGE_SMOKE + 火苗）
            if (this.age == 1) {
                this.level().addParticle(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
            }
            if (this.age % 2 == 0) {
                for (int i = 0; i < 2; i++) {
                    double angle = this.random.nextDouble() * Math.PI * 2.0;
                    double r = radius * 0.6 * this.random.nextDouble();
                    this.level().addParticle(ParticleTypes.LARGE_SMOKE,
                        this.getX() + Math.cos(angle) * r, this.getY() + 0.1, this.getZ() + Math.sin(angle) * r,
                        0.0, 0.08, 0.0);
                }
            }
            if (this.age <= 20 && this.age % 4 == 0) {
                double ringRadius = radius * (this.age / 20.0);
                for (int i = 0; i < 20; i++) {
                    double angle = i / 20.0 * Math.PI * 2.0;
                    this.level().addParticle(ParticleTypes.FLAME,
                        this.getX() + Math.cos(angle) * ringRadius, this.getY() + 0.2, this.getZ() + Math.sin(angle) * ringRadius,
                        Math.cos(angle) * 0.08, 0.03, Math.sin(angle) * 0.08);
                }
            }
            if (this.age % 8 == 0) {
                for (int i = 0; i < 2; i++) {
                    double angle = this.random.nextDouble() * Math.PI * 2.0;
                    double r = radius * this.random.nextDouble();
                    this.level().addParticle(ParticleTypes.FLAME,
                        this.getX() + Math.cos(angle) * r, this.getY() + 0.1, this.getZ() + Math.sin(angle) * r,
                        (this.random.nextDouble() - 0.5) * 0.05, this.random.nextDouble() * 0.05, (this.random.nextDouble() - 0.5) * 0.05);
                }
            }
            return;
        }
        // 下落：白雾尾迹
        if (!this.onGround() && this.age % 2 == 0) {
            for (int i = 0; i < 2; i++) {
                this.level().addParticle(ParticleTypes.CLOUD,
                    this.getX() + (this.random.nextDouble() - 0.5) * 0.3,
                    this.getY() + this.random.nextDouble() * 0.3,
                    this.getZ() + (this.random.nextDouble() - 0.5) * 0.3,
                    0.0, -0.05, 0.0);
            }
        }
    }

    private void serverTick() {
        if (this.hasImpacted()) {
            if (this.age >= this.entityData.get(MAX_AGE)) {
                this.discard();
            }
            return;
        }
        // 下落
        this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.08, 0.0));
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (this.onGround() && this.getDeltaMovement().y <= 0.0) {
            this.impact();
        }
        if (this.age >= this.entityData.get(MAX_AGE)) {
            this.impact();
        }
    }

    private void impact() {
        if (this.hasImpacted()) {
            return;
        }
        this.setHasImpacted(true);
        this.age = 0;
        this.setDeltaMovement(Vec3.ZERO);
        this.playSound(SoundEvents.GENERIC_EXPLODE, 1.0f, 1.0f);
        // 爆炸命中：攻击 × 70%（一次性）
        if (this.owner != null) {
            float dmg = (float) this.owner.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * METEOR_RATE;
            float radius = this.entityData.get(DAMAGE_RADIUS);
            AABB area = new AABB(this.getX() - radius, this.getY() - 1.0, this.getZ() - radius,
                this.getX() + radius, this.getY() + 2.0, this.getZ() + radius);
            for (LivingEntity t : this.level().getEntitiesOfClass(LivingEntity.class, area,
                    e -> e.isAlive() && e != this.owner && !(e instanceof net.minecraft.client.yiz.xian.entity.base.YizxianMob))) {
                t.hurt(t.damageSources().mobAttack(this.owner), dmg);
            }
        }
        // 生成毒云（每 tick 涨跌多空 × 70%）
        if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            XieyulongPoisonCloudEntity cloud = new XieyulongPoisonCloudEntity(
                net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes.XIEYULONG_POISON_CLOUD.get(), this.level());
            cloud.setOwner(this.owner);
            cloud.setPos(this.getX(), this.getY(), this.getZ());
            cloud.setMaxAge(100);
            cloud.setCloudRadius(7.0f);
            cloud.setDreamRate(CLOUD_DREAM_RATE);
            sl.addFreshEntity(cloud);
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
