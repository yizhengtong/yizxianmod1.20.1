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

import java.util.List;

/**
 * 邪狱龙火球弹道。朝目标直线飞行，命中或撞墙时爆炸：范围真实伤害 + 生成毒云。
 * 无渲染器（靠 vanilla 粒子显示），owner 为施放者（不序列化，弹道短命无需持久化）。
 */
public class XieyulongFireEntity extends Entity {

    private static final EntityDataAccessor<Integer> MAX_AGE =
        SynchedEntityData.defineId(XieyulongFireEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HAS_IMPACTED =
        SynchedEntityData.defineId(XieyulongFireEntity.class, EntityDataSerializers.BOOLEAN);

    private static final float DAMAGE_RADIUS = 7.0f;
    private static final float FIREBALL_RATE = 0.5f;   // 命中 = 攻击 × 50%
    private static final float CLOUD_DREAM_RATE = 0.5f; // 毒云每 tick = 涨跌多空 × 50%

    private LivingEntity owner;
    private int age;
    private float speed = 1.0f;
    private Vec3 direction = new Vec3(0.0, 0.0, 1.0);

    public XieyulongFireEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public void setOwner(LivingEntity owner) {
        this.owner = owner;
    }

    public void setMaxAge(int maxAge) {
        this.entityData.set(MAX_AGE, maxAge);
    }

    public void setMoveDirection(Vec3 dir) {
        double len = dir.length();
        this.direction = len > 0.0 ? dir.scale(1.0 / len) : new Vec3(0.0, 0.0, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(MAX_AGE, 120);
        this.entityData.define(HAS_IMPACTED, false);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(0.2f, 0.2f);
    }

    private boolean hasImpacted() {
        return this.entityData.get(HAS_IMPACTED);
    }

    private void setHasImpacted(boolean impacted) {
        this.entityData.set(HAS_IMPACTED, impacted);
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
            // 命中：大爆炸（EXPLOSION 闪 + 扩散火环 + LARGE_SMOKE + 火苗）
            if (this.age == 1) {
                this.level().addParticle(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.5, this.getZ(), 0.0, 0.0, 0.0);
            }
            if (this.age % 2 == 0) {
                for (int i = 0; i < 2; i++) {
                    double angle = this.random.nextDouble() * Math.PI * 2.0;
                    double r = 3.5 * this.random.nextDouble();
                    this.level().addParticle(ParticleTypes.LARGE_SMOKE,
                        this.getX() + Math.cos(angle) * r, this.getY() + 0.1, this.getZ() + Math.sin(angle) * r,
                        0.0, 0.05, 0.0);
                }
            }
            if (this.age <= 15 && this.age % 3 == 0) {
                double ringRadius = 7.0 * (this.age / 15.0);
                for (int i = 0; i < 16; i++) {
                    double angle = i / 16.0 * Math.PI * 2.0;
                    this.level().addParticle(ParticleTypes.FLAME,
                        this.getX() + Math.cos(angle) * ringRadius, this.getY() + 0.2, this.getZ() + Math.sin(angle) * ringRadius,
                        Math.cos(angle) * 0.05, 0.02, Math.sin(angle) * 0.05);
                }
            }
            if (this.age % 6 == 0) {
                for (int i = 0; i < 2; i++) {
                    double angle = this.random.nextDouble() * Math.PI * 2.0;
                    double r = 7.0 * this.random.nextDouble();
                    this.level().addParticle(ParticleTypes.FLAME,
                        this.getX() + Math.cos(angle) * r, this.getY() + 0.1, this.getZ() + Math.sin(angle) * r,
                        (this.random.nextDouble() - 0.5) * 0.05, this.random.nextDouble() * 0.05, (this.random.nextDouble() - 0.5) * 0.05);
                }
            }
            return;
        }
        // 飞行尾迹：白雾
        if (this.age % 3 == 0) {
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
            if (this.age >= 80) {
                this.discard();
            }
            return;
        }
        // 分步移动 + 撞墙/命中判定
        Vec3 step = this.direction.scale(this.speed / 3.0);
        for (int i = 0; i < 3; i++) {
            this.move(MoverType.SELF, step);
            if (this.horizontalCollision || this.verticalCollision || this.onGround()) {
                this.impact();
                return;
            }
        }
        if (!this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.5),
                e -> e != this.owner && e.isAlive() && !(e instanceof net.minecraft.client.yiz.xian.entity.base.YizxianMob)).isEmpty()) {
            this.impact();
            return;
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
        // 爆炸命中：攻击 × 50%（一次性）
        if (this.owner != null) {
            float dmg = (float) this.owner.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * FIREBALL_RATE;
            AABB area = new AABB(this.getX() - DAMAGE_RADIUS, this.getY() - 1.0, this.getZ() - DAMAGE_RADIUS,
                this.getX() + DAMAGE_RADIUS, this.getY() + 2.0, this.getZ() + DAMAGE_RADIUS);
            for (LivingEntity t : this.level().getEntitiesOfClass(LivingEntity.class, area,
                    e -> e.isAlive() && e != this.owner && !(e instanceof net.minecraft.client.yiz.xian.entity.base.YizxianMob))) {
                t.hurt(t.damageSources().mobAttack(this.owner), dmg);
            }
        }
        // 生成毒云（每 tick 涨跌多空 × 50%）
        if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            XieyulongPoisonCloudEntity cloud = new XieyulongPoisonCloudEntity(
                net.minecraft.client.yiz.xian.entity.registry.YizxianEntityTypes.XIEYULONG_POISON_CLOUD.get(), this.level());
            cloud.setOwner(this.owner);
            cloud.setPos(this.getX(), this.getY(), this.getZ());
            cloud.setMaxAge(80);
            cloud.setCloudRadius(7.0f);
            cloud.setDreamRate(CLOUD_DREAM_RATE);
            sl.addFreshEntity(cloud);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.age);
        tag.putDouble("DirX", this.direction.x);
        tag.putDouble("DirY", this.direction.y);
        tag.putDouble("DirZ", this.direction.z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getInt("Age");
        this.direction = new Vec3(tag.getDouble("DirX"), tag.getDouble("DirY"), tag.getDouble("DirZ"));
    }
}
