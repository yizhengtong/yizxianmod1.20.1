package net.minecraft.client.yiz.xian.entity.base;

import net.minecraft.client.yiz.editor.PoshiBearer;
import net.minecraft.client.yiz.editor.PoshiBypassBridge;
import net.minecraft.client.yiz.tool.YizieManager;
import net.minecraft.client.yiz.tool.attribute.EntityAttributeGate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * 本模组所有实体共用基类（1.20.1 移植版）。
 *
 * <p>与 1.21.1 同逻辑：正常移动完全保留原版，只免疫"主动外力"——防 TP / 防速度注入 /
 * 药水免疫 / 防流体推动 / 防击退 / 不可上船 / 蜘蛛网免疫 / 水上行走。</p>
 */
public abstract class YizxianMob extends Mob implements PoshiBearer {

    private static final byte[] DOOR_KEY = new byte[32];
    static {
        new SecureRandom().nextBytes(DOOR_KEY);
    }

    private static final ThreadLocal<byte[]> GATE_TOKEN = new ThreadLocal<>();

    private static final String MOD_PREFIX = "net.minecraft.client.yiz.xian.";

    private static final String[] ENGINE_PREFIXES = {
        "net.minecraft.",
        "net.minecraftforge.",
        "com.mojang.",
    };

    private static final String[] COMMAND_FRAME_PREFIXES = {
        "net.minecraft.server.commands.",
    };

    private static final String[] EXTERNAL_FORCE_PREFIXES = {
        "net.minecraft.world.level.Explosion",
    };

    private static final Set<String> GATED_METHODS = Set.of(
        "setPos", "moveTo", "teleportTo", "absMoveTo", "teleportRelative", "randomTeleport",
        "setDeltaMovement", "addDeltaMovement", "knockback");

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicInteger REJECT_LOG_COUNT = new AtomicInteger();

    private static volatile boolean potionImmunity = true;

    public static void setPotionImmunity(boolean enabled) { potionImmunity = enabled; }
    public static boolean isPotionImmunity() { return potionImmunity; }

    private boolean inPhysicalMove;
    private Vec3 lastGatedPos;
    private long lastGatedTick = -1;

    private boolean yizxianAttrsApplied;

    private double templateMaxHealth = -1;
    private double templateAttackDamage = -1;

    private double lastMirrorArmor = Double.NaN;
    private double lastMirrorSpellDefense = Double.NaN;

    protected YizxianMob(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    //  门禁判定 

    @Override
    public void aiStep() {
        withGate(() -> {
            boolean server = !this.level().isClientSide();
            if (server) {
                if (!this.yizxianAttrsApplied) {
                    this.yizxianAttrsApplied = true;
                    this.applyEntityAttributes();
                    this.registerSecureHealth();
                    registerImmortal(this);   // 加入独立线程不死守卫注册表
                }
                this.mirrorDefensiveAttributes();
                // 每 tick 强制校正（通用，不点名任何模组）：表值回写自身通道 + 清未知 Float delta + 防 removed/MAX_HEALTH 篡改
                this.enforceSecureHealthState();
                // 属性标准化守护：周期性审计并还原被外部篡改的属性
                net.minecraft.client.yiz.tool.attribute.AttributeStandardizer.tick(this);
                if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) <= 0.0F) {
                    net.minecraft.client.yiz.tool.attribute.AttributeStandardizer.cleanup(this);
                    // 死亡放行：vanilla tickDeath 死亡动画（20 tick）后自然 remove(KILLED)，
                    // 由 remove override 与 EntityRemoveProtectionMixin 放行。不再 checkAndRemove
                    // 立即移除（会跳过倒地动画，且抢在 tick() 掉落分支之前导致掉落丢失）。
                    net.minecraft.client.yiz.xian.core.EntityRemoveProtection.allowDeathRemove(this.getUUID());
                }
                if (potionImmunity) this.removeAllEffects();
            }
            super.aiStep();
            if (server && potionImmunity) {
                this.removeAllEffects();
            }
        });
    }

    protected void applyEntityAttributes() {
        // 空实现：子类覆写分配受保护属性
    }

    protected double difficultyMultiplier() {
        return switch (this.level().getDifficulty()) {
            case HARD -> 1.0;
            case NORMAL -> 0.75;
            case EASY, PEACEFUL -> 0.5;
        };
    }

    protected double scaleDifficulty(double templateValue) {
        if (templateValue <= 0) return templateValue;
        return Math.max(1.0, templateValue * difficultyMultiplier());
    }

    protected void applyVanillaDifficultyScale() {
        double mult = difficultyMultiplier();
        var hp = this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        var atk = this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (hp != null) {
            if (this.templateMaxHealth < 0) this.templateMaxHealth = hp.getBaseValue();
            double oldMax = this.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            float ratio = oldMax > 0 ? net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) / (float) oldMax : 1.0F;
            hp.setBaseValue(this.templateMaxHealth * mult);
            if (oldMax != this.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) && ratio > 0) {
                this.setHealth((float) (this.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) * ratio));
            }
        }
        if (atk != null) {
            if (this.templateAttackDamage < 0) this.templateAttackDamage = atk.getBaseValue();
            atk.setBaseValue(this.templateAttackDamage * mult);
        }
    }

    public void refreshDifficultyAttributes() {
        if (this.level().isClientSide()) return;
        this.applyEntityAttributes();
    }

    private void mirrorDefensiveAttributes() {
        double armor = this.getAttributeValue(net.minecraft.client.yiz.attribute.YizAttributes.ARMOR.get());
        if (armor != this.lastMirrorArmor) {
            this.lastMirrorArmor = armor;
            net.minecraft.client.yiz.tizMod.mirrorArmor(this);
        }
        double sd = this.getAttributeValue(net.minecraft.client.yiz.attribute.YizAttributes.SPELL_DEFENSE.get());
        if (sd != this.lastMirrorSpellDefense) {
            this.lastMirrorSpellDefense = sd;
            net.minecraft.client.yiz.tizMod.mirrorSpellDefense(this);
        }
    }

    protected boolean isObserver(net.minecraft.world.entity.LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player p && p.isCreative();
    }

    private boolean motionGate() {
        if (this.tickCount == 0) return true;
        if (Arrays.equals(GATE_TOKEN.get(), DOOR_KEY)) return true;
        return net.minecraft.client.yiz.tool.ExternalCallGuard.isTrustedCall(GATED_METHODS);
    }

    private boolean isAllowedPositionChange(double x, double y, double z) {
        if (this.level().isClientSide()) return true;
        if (this.tickCount == 0 || this.lastGatedPos == null) {
            this.lastGatedPos = new Vec3(x, y, z);
            this.lastGatedTick = this.tickCount;
            return true;
        }
        if (this.inPhysicalMove) return true;
        if (Arrays.equals(GATE_TOKEN.get(), DOOR_KEY)) return true;
        return false;
    }

    @Override
    public void move(MoverType type, Vec3 pos) {
        this.inPhysicalMove = true;
        try {
            super.move(type, pos);
        } finally {
            this.inPhysicalMove = false;
        }
    }

    protected static void withGate(Runnable action) {
        GATE_TOKEN.set(DOOR_KEY);
        try {
            action.run();
        } finally {
            GATE_TOKEN.remove();
        }
    }

    //  反击递归保护 

    private static final ThreadLocal<Boolean> COUNTER_RECURSION_GUARD = ThreadLocal.withInitial(() -> Boolean.FALSE);

    protected static boolean isCounterInProgress() {
        return COUNTER_RECURSION_GUARD.get();
    }

    protected static void beginCounterWindow() {
        COUNTER_RECURSION_GUARD.set(Boolean.TRUE);
    }

    protected static void endCounterWindow() {
        COUNTER_RECURSION_GUARD.set(Boolean.FALSE);
    }

    //  坐标变动入口 

    @Override
    public void setPos(double x, double y, double z) {
        if (!isAllowedPositionChange(x, y, z)) return;
        super.setPos(x, y, z);
    }

    @Override
    public void moveTo(double x, double y, double z) {
        if (!isAllowedPositionChange(x, y, z)) return;
        super.moveTo(x, y, z);
    }

    @Override
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        if (!isAllowedPositionChange(x, y, z)) return;
        super.moveTo(x, y, z, yRot, xRot);
    }

    @Override
    public void moveTo(Vec3 position) {
        if (!isAllowedPositionChange(position.x, position.y, position.z)) return;
        super.moveTo(position);
    }

    @Override
    public void teleportTo(double x, double y, double z) {
        if (!isAllowedPositionChange(x, y, z)) return;
        super.teleportTo(x, y, z);
    }

    @Override
    public boolean teleportTo(ServerLevel level, double x, double y, double z,
                              Set<RelativeMovement> relativeMovements, float yRot, float xRot) {
        if (!isAllowedPositionChange(x, y, z)) return false;
        return super.teleportTo(level, x, y, z, relativeMovements, yRot, xRot);
    }

    @Override
    public void absMoveTo(double x, double y, double z) {
        if (!isAllowedPositionChange(x, y, z)) return;
        super.absMoveTo(x, y, z);
    }

    @Override
    public void absMoveTo(double x, double y, double z, float yRot, float xRot) {
        if (!isAllowedPositionChange(x, y, z)) return;
        super.absMoveTo(x, y, z, yRot, xRot);
    }

    @Override
    public void teleportRelative(double x, double y, double z) {
        if (!isAllowedPositionChange(x, y, z)) return;
        super.teleportRelative(x, y, z);
    }

    @Override
    public boolean randomTeleport(double x, double y, double z, boolean mayPlaceOn) {
        if (!isAllowedPositionChange(x, y, z)) return false;
        return super.randomTeleport(x, y, z, mayPlaceOn);
    }

    //  速度量入口 

    @Override
    public void setDeltaMovement(double x, double y, double z) {
        if (!motionGate()) return;
        super.setDeltaMovement(x, y, z);
    }

    @Override
    public void setDeltaMovement(Vec3 deltaMovement) {
        if (!motionGate()) return;
        super.setDeltaMovement(deltaMovement);
    }

    @Override
    public void addDeltaMovement(Vec3 deltaMovement) {
        if (!motionGate()) return;
        super.addDeltaMovement(deltaMovement);
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (!motionGate()) return;
        super.knockback(strength, x, z);
    }

    //  特判免疫 

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        target.invulnerableTime = 0;
        PoshiBypassBridge.beginBypass();
        try {
            return super.doHurtTarget(target);
        } finally {
            PoshiBypassBridge.endBypass();
        }
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 speedMultiplier) {
        // 蜘蛛网等"卡住"方块无效
    }

    @Override
    public float getWaterSlowDown() {
        return 1.0F;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isInWater() && !this.isUnderWater() && this.getDeltaMovement().y < 0.0) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.0, 1.0));
        }
        super.travel(travelVector);
    }

    @Override
    public boolean isAffectedByPotions() {
        return potionImmunity ? false : super.isAffectedByPotions();
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effectInstance) {
        return potionImmunity ? false : super.canBeAffected(effectInstance);
    }

    @Override
    public boolean addEffect(MobEffectInstance effectInstance, Entity entity) {
        return potionImmunity ? false : super.addEffect(effectInstance, entity);
    }

    @Override
    public void forceAddEffect(MobEffectInstance effectInstance, Entity entity) {
        if (potionImmunity) return;
        super.forceAddEffect(effectInstance, entity);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    //  血量外部存储保护（所有本模组实体通用）
    // 把辖界者的全套血量保护 override 下沉到基类：任何 YizxianMob 子类自动注册外部血量表、
    // getHealth/isAlive/isDeadOrDying 读表、setHealth 扣血方向丢弃、hurt/actuallyHurt/kill/die/
    // remove/setPose/dropAllDeathLoot 全门控——本模组所有实体免改血（不依赖 agent/mixin）。

    private static final ThreadLocal<Boolean> FORCE_REMOVE = ThreadLocal.withInitial(() -> false);

    public static void beginForceRemove() { FORCE_REMOVE.set(true); }
    public static void endForceRemove() { FORCE_REMOVE.remove(); }

    //  独立线程不死守卫（独立线程思路，2026-08-11）
    // 不依赖原版 tick：外部注入 等 mod 会改原版 tick 链/覆盖 isDeadOrDying，Java override 在原版 tick
    // 里可能被绕过。独立守护线程每 ~50ms 直接强制受保护实体状态（dead/deathTime/removed/pose），
    // 外部 mod 无法让辖界者（表值>0）保持死亡/倒地。表值=0（玩家按规则打死）才放行正常死亡并注销。
    private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID, YizxianMob> IMMORTAL_REGISTRY =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean GUARD_STARTED = false;

    private static void ensureGuardStarted() {
        if (GUARD_STARTED) return;
        synchronized (YizxianMob.class) {
            if (GUARD_STARTED) return;
            GUARD_STARTED = true;
            Thread t = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(50L);   // ≈ 1 tick
                    } catch (InterruptedException e) {
                        return;
                    }
                    try {
                        for (YizxianMob e : IMMORTAL_REGISTRY.values()) {
                            try {
                                e.immortalGuard();
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                }
            }, "yiz-immortal-guard");
            t.setDaemon(true);
            t.start();
        }
    }

    private static void registerImmortal(YizxianMob e) {
        if (e == null || e.level().isClientSide()) return;
        ensureGuardStarted();
        IMMORTAL_REGISTRY.putIfAbsent(e.getUUID(), e);
    }

    private static void unregisterImmortal(java.util.UUID id) {
        if (id != null) IMMORTAL_REGISTRY.remove(id);
    }

    /** 独立线程不死守卫：表值>0 强制恢复不死状态；表值=0 强制移除（死亡清理，发移除包 → 客户端移除）。 */
    private void immortalGuard() {
        if (this.level().isClientSide()) return;
        if (!net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this)) {
            unregisterImmortal(this.getUUID());
            return;
        }
        float hp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
        if (hp > 0) {
            this.dead = false;
            this.deathTime = 0;
            if (this.isRemoved()) this.clearForcedRemoved();
            if (this.getPose() == net.minecraft.world.entity.Pose.DYING) {
                this.setPose(net.minecraft.world.entity.Pose.STANDING);
            }
        } else {
            unregisterImmortal(this.getUUID());
            // 表值=0（死亡）：仅标记，由主线程 tick 统一处理（生成掉落物必须在主线程才正确显示，
            // 独立线程 addFreshEntity 不掉落物/不同步）
            this.pendingDeathRemove = true;
        }
    }

    /** onSyncedDataUpdated 混淆串钳制回写的防重入标记（回写自身也是合法 set，需避免循环触发）。 */
    private static final ThreadLocal<Boolean> SELF_CORRECTING = ThreadLocal.withInitial(() -> false);

    private static java.lang.reflect.Field REMOVAL_REASON_FIELD;
    private static java.lang.reflect.Field REMOVED_FIELD;
    private static volatile boolean REMOVED_FIELDS_READY;

    private static final int CONDUCTION_HIT_CD_FALLBACK = 20;
    protected long lastConductionHitTick = Long.MIN_VALUE;

    /** 传导受击 CD（tick）：动态跟随无敌帧属性 INVINCIBILITY_MULT（模板 16 = 0.8s）。 */
    protected long conductionHitCdTicks() {
        var inst = this.getAttribute(net.minecraft.client.yiz.attribute.YizAttributes.INVINCIBILITY_MULT.get());
        double v = inst != null ? inst.getValue() : 0;
        long r = v > 0 ? (long) v : CONDUCTION_HIT_CD_FALLBACK;
        // 诊断（限频）：确认传导 CD 读到的属性值（编辑器改 INVINCIBILITY_MULT 后是否实时跟随）
        if (COND_DIAG.incrementAndGet() % 30 == 1) {
            LOGGER.warn("[COND-DIAG] conductionHitCdTicks: INVINCIBILITY_MULT={} -> cdTicks={}", v, r);
        }
        return r;
    }

    /**
     * 单发传导上限：maxHp × CONDUCTION_CAP%（无属性 → 5% 兜底）。
     *  无 3 点下限：尊重编辑器显式设置的低值（如 0.25% → 400×0.0025=1 点），
     * 否则测试/配置低限伤时被抬到 3 点无法生效。仅防 NaN/负值（→0 表示该实体不吃传导伤害）。
     */
    protected float conductionCap() {
        double capPct;
        boolean edited = net.minecraft.client.yiz.tool.attribute.AttributeStandardizer.isEdited(this, "conduction_cap");
        var inst = this.getAttribute(net.minecraft.client.yiz.attribute.YizAttributes.CONDUCTION_CAP.get());
        double attrVal = inst != null ? inst.getValue() : 5.0;
        if (edited) {
            // 编辑器合法编辑（markEdited）：直接读属性，实时跟随编辑器改值（不依赖 vault 同步）
            capPct = attrVal;
        } else {
            // 未编辑：读权威表防篡改（外部改属性不影响）；未登记 fallback 属性值（5% 兜底）
            Float vault = net.minecraft.client.yiz.tool.health.ConductionCapVault.getPercent(this);
            capPct = vault != null ? vault : attrVal;
        }
        if (Float.isNaN((float) capPct) || capPct < 0) capPct = 0;
        //  用属性 API 而非 this.getMaxHealth()（虚拟调用可被外部 agent 注入压负 → cap 计算错误）
        float maxHp = (float) this.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        float r = (float) (maxHp * capPct / 100.0);
        // 诊断（限频）：确认 cap 实际来源（编辑器改后是否实时跟随）
        if (COND_DIAG.incrementAndGet() % 20 == 1) {
            LOGGER.warn("[CapD] edited={} attr={} vault={} -> capPct={} cap={}",
                edited, attrVal, net.minecraft.client.yiz.tool.health.ConductionCapVault.getPercent(this), capPct, r);
        }
        return r;
    }

    /** 受保护最大生命值来源（默认属性；子类可硬编码覆盖，防属性被外部清空）。 */
    protected float secureMaxHealth() {
        float m = (float) this.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        return m > 0 ? m : 1.0F;
    }

    /** 定义混淆血量存储（首次 aiStep 应用属性后调用一次）：哨兵 enc(-1) → 设为满血。 */
    protected void registerSecureHealth() {
        if (level().isClientSide()) return;
        try {
            int key = this.entityData.get(net.minecraft.client.yiz.tool.health.HealthChannels.SECURE_OBF_KEY);
            String enc = this.entityData.get(net.minecraft.client.yiz.tool.health.HealthChannels.SECURE_OBF);
            float v = net.minecraft.client.yiz.tool.health.FloatObf.dec(enc, key);
            float maxHp = secureMaxHealth();
            // 未初始化（哨兵 -1）或损坏/超上限 → 用受保护 maxHp 初始化
            if (Float.isNaN(v) || v < 0 || v > maxHp) {
                net.minecraft.client.yiz.tool.health.SecureHealthClosure.beginObfWrite();
                try {
                    this.entityData.set(net.minecraft.client.yiz.tool.health.HealthChannels.SECURE_OBF,
                        net.minecraft.client.yiz.tool.health.FloatObf.enc(maxHp, key));
                } finally {
                    net.minecraft.client.yiz.tool.health.SecureHealthClosure.endObfWrite();
                }
            }
            // 注册服务端权威表（逻辑血量唯一来源；外部注入 直写混淆串不影响本表）
            net.minecraft.client.yiz.tool.health.SecureHealthClosure.registerAuthority(this,
                net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this));
        } catch (Throwable ignored) {}
    }

    /** 定义混淆血量存储：SECURE_OBF（哨兵 enc(-1,随机key)，首个服务端 tick 经 registerSecureHealth 设满血）+ SECURE_OBF_KEY（随机）。 */
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        if (!this.entityData.hasItem(net.minecraft.client.yiz.tool.health.HealthChannels.SECURE_OBF)) {
            int key = new java.security.SecureRandom().nextInt();
            this.entityData.define(net.minecraft.client.yiz.tool.health.HealthChannels.SECURE_OBF,
                net.minecraft.client.yiz.tool.health.FloatObf.enc(-1.0F, key));
            this.entityData.define(net.minecraft.client.yiz.tool.health.HealthChannels.SECURE_OBF_KEY, key);
        }
    }

    /**
     * 混淆串钳制钩子（遗漏的层，与数据层拦截 mixin 双保险）：
     * {@code SECURE_OBF} 串被外部改为非法值（dec 失败 / 越界 / 负数）→ 服务端回写表值 enc。
     * 覆盖路径：外部 DataItem 直写 + 手动 onSyncedDataUpdated、网络同步到达（客户端不校验，服务端权威）。
     */
    @Override
    public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (this.level().isClientSide()) return;
        if (!net.minecraft.client.yiz.tool.health.SecureHealthClosure.hasObf(this)) return;
        if (key.getId() != net.minecraft.client.yiz.tool.health.HealthChannels.SECURE_OBF.getId()) return;
        correctObfHealthString();
    }

    /** 校验 SECURE_OBF 混淆串：非法（dec 失败 / 越界 / 负数）→ 回写表值 enc（防外部直写串）。
     *  由 onSyncedDataUpdated（即时）与 enforceSecureHealthState（每 tick 兜底，防 DataItem 直写不触发钩子）共用。 */
    private void correctObfHealthString() {
        if (SELF_CORRECTING.get()) return;
        boolean invalid;
        int k = this.entityData.get(net.minecraft.client.yiz.tool.health.HealthChannels.SECURE_OBF_KEY);
        try {
            String enc = this.entityData.get(net.minecraft.client.yiz.tool.health.HealthChannels.SECURE_OBF);
            float v = net.minecraft.client.yiz.tool.health.FloatObf.dec(enc, k);
            //  用属性读 maxHp（this.getMaxHealth() 虚拟调用可被外部 agent 注入压负 → 误判串非法反复回写）
            float maxHp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getMaxHealth(this);
            invalid = Float.isNaN(v) || v < 0 || v > maxHp;
        } catch (Throwable t) {
            invalid = true;   // dec 抛异常 = 串损坏
        }
        if (!invalid) return;
        SELF_CORRECTING.set(true);
        try {
            net.minecraft.client.yiz.tool.health.SecureHealthClosure.beginObfWrite();
            try {
                this.entityData.set(net.minecraft.client.yiz.tool.health.HealthChannels.SECURE_OBF,
                    net.minecraft.client.yiz.tool.health.FloatObf.enc(
                        net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this), k));
            } finally {
                net.minecraft.client.yiz.tool.health.SecureHealthClosure.endObfWrite();
            }
        } finally {
            SELF_CORRECTING.remove();
        }
    }

    /** 持久化混淆血量串 + key（防 reload 时 key 变 → 垃圾值）。 */
    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        try {
            tag.putString("yizxian_obf_health", this.entityData.get(net.minecraft.client.yiz.tool.health.HealthChannels.SECURE_OBF));
            tag.putInt("yizxian_obf_key", this.entityData.get(net.minecraft.client.yiz.tool.health.HealthChannels.SECURE_OBF_KEY));
        } catch (Throwable ignored) {}
    }

    /** 恢复混淆血量串 + key。 */
    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            if (tag.contains("yizxian_obf_key", net.minecraft.nbt.Tag.TAG_INT)) {
                int key = tag.getInt("yizxian_obf_key");
                this.entityData.set(net.minecraft.client.yiz.tool.health.HealthChannels.SECURE_OBF_KEY, key);
                if (tag.contains("yizxian_obf_health", net.minecraft.nbt.Tag.TAG_STRING)) {
                    this.entityData.set(net.minecraft.client.yiz.tool.health.HealthChannels.SECURE_OBF,
                        tag.getString("yizxian_obf_health"));
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public float getHealth() {
        // 受保护实体（SECURE_PULSE>0，属性同步两端一致）：读混淆 String DataParameter dec——
        // 服务端+客户端一致（客户端显示用）。非受保护走 vanilla。
        if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.hasObf(this)) {
            return net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
        }
        return super.getHealth();
    }

    @Override
    public void setHealth(float health) {
        // 客户端 → vanilla（不写混淆串，服务端权威）；服务端非受保护 → vanilla；
        // 受保护：治疗方向写混淆串，外部扣血方向丢弃（只有 hurt() 传导链能扣）。
        if (level().isClientSide() || !net.minecraft.client.yiz.tool.health.SecureHealthClosure.hasObf(this)) {
            super.setHealth(health);
            return;
        }
        float current = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
        if (health >= current) {
            net.minecraft.client.yiz.tool.health.SecureHealthClosure.setHealth(this, health);
        }
    }

    @Override
    public boolean isAlive() {
        if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.hasObf(this)) {
            return !isRemoved() && net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0;
        }
        return super.isAlive();
    }

    @Override
    public boolean isDeadOrDying() {
        if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.hasObf(this)) {
            return net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) <= 0;
        }
        return super.isDeadOrDying();
    }

    @Override
    public void heal(float healAmount) {
        if (healAmount < 0 && net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this)) {
            // 外部模组用负 heal 扣血 → 重定向 hurt 走传导限伤
            this.hurt(this.damageSources().generic(), -healAmount);
            return;
        }
        // secure 实体（混淆串/外部表血量）：治疗方向写表值（吸血/阶段回血/涨跌多空吸血扩展对辖界者生效）。
        // setHealth 内部 requireTrusted——本模组 heal 受信任；外部直接 heal 拉血被拒（表值不变）。
        if (healAmount > 0 && !this.level().isClientSide()
                && net.minecraft.client.yiz.tool.health.SecureHealthClosure.hasObf(this)) {
            net.minecraft.client.yiz.tool.health.SecureHealthClosure.setHealth(this,
                net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) + healAmount);
            return;
        }
        super.heal(healAmount);
    }

    @Override
    public void kill() {
        if (!level().isClientSide()
                && net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this)
                && net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            return;
        }
        super.kill();
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (!level().isClientSide()
                && net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this)
                && net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            return;
        }
        super.die(source);
    }

    @Override
    protected void actuallyHurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // 拦截外部绕过 hurt() 直接调 actuallyHurt()（1.20.1 是 protected，可反射/子类调用）：重定向 hurt 走传导限伤
        if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this)) {
            this.hurt(source, amount);
            return;
        }
        super.actuallyHurt(source, amount);
    }

    /**
     * 基类默认伤害链（子类可 override 扩展反击等）：vanilla 闸门（只放行 minecraft 伤害类型，通用不点名）
     * → 传导 CD → 传导限伤 → 扣表。本模组所有实体免改血的核心。
     */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (level().isClientSide()) return false;
        // 受保护实体一律走传导链（isSecure）；非受保护走原版
        if (!net.minecraft.client.yiz.tool.health.SecureHealthClosure.isSecure(this)) {
            return super.hurt(source, amount);
        }
        if (amount <= 0) return false;
        long cdTicks = conductionHitCdTicks();
        if (this.lastConductionHitTick != Long.MIN_VALUE
                && this.level().getGameTime() - this.lastConductionHitTick < cdTicks) {
            return false;
        }
        // vanilla 闸门：只接受 minecraft 命名空间的伤害类型（1.20.1 用 Registries.DAMAGE_TYPE 注册表）
        var type = source.type();
        var key = this.level().registryAccess()
            .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
            .getKey(type);
        if (key == null || !key.getNamespace().equals("minecraft")) return false;
        float limited = Math.min(amount, conductionCap());
        if (limited <= 0) return false;
        float current = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
        float next = Math.max(0, current - limited);
        net.minecraft.client.yiz.tool.health.SecureHealthClosure.setHealth(this, next);
        this.lastConductionHitTick = this.level().getGameTime();
        this.hurtTime = 10;
        this.hurtDuration = 10;
        try {
            if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.broadcastDamageEvent(this, source);
            }
        } catch (Throwable ignored) {}
        if (next <= 0) {
            this.die(source);
            return true;
        }
        return true;
    }

    @Override
    public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        if (!level().isClientSide()) {
            // 诊断：服务端实体移除（确认死亡后是否真的 remove；客户端残留疑因 Destroy 广播链路）
            LOGGER.warn("[QZK-REMOVE] uuid={} reason={} FORCE_REMOVE={} hp={} wasRemoved={}",
                this.getUUID(), reason, FORCE_REMOVE.get(),
                net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this), this.isRemoved());
        }
        if (!level().isClientSide()
                && !FORCE_REMOVE.get()
                && net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this)
                && net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            return;
        }
        // 实体正常移除/死亡：清理服务端权威表与完整性表
        net.minecraft.client.yiz.tool.health.SecureHealthClosure.removeAuthority(this);
        net.minecraft.client.yiz.tool.health.SecureHealthClosure.removeIntegrity(this.getUUID());
        super.remove(reason);
        // 兜底：死亡移除后手动广播 Destroy 包（vanilla 移除广播链路在多实体/召唤竞态下可能未达客户端
        // → 客户端残留"打死不倒地"实体；这里无论 vanilla 是否广播，都显式让客户端移除该实体 id）
        if (!level().isClientSide() && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            try {
                net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket pkt =
                    new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(this.getId());
                for (net.minecraft.server.level.ServerPlayer p : sl.players()) {
                    p.connection.send(pkt);
                }
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void setPose(net.minecraft.world.entity.Pose pose) {
        // 客户端也拦 DYING（外部注入 压客户端 isDeadOrDying → vanilla 客户端调 setPose(DYING) → 倒地；
        // 之前 !clientSide 只拦服务端，客户端被放行是倒地根因）
        if (pose == net.minecraft.world.entity.Pose.DYING
                && net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this)
                && net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            return;
        }
        super.setPose(pose);
    }

    @Override
    protected void dropAllDeathLoot(net.minecraft.world.damagesource.DamageSource source) {
        if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this)
                && net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            return;
        }
        super.dropAllDeathLoot(source);
    }

    @Override
    public boolean saveAsPassenger(net.minecraft.nbt.CompoundTag compound) {
        if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this)
                && net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            String s = this.getEncodeId();
            if (s == null) return false;
            compound.putString("id", s);
            this.saveWithoutId(compound);
            return true;
        }
        return super.saveAsPassenger(compound);
    }

    @Override
    public boolean shouldBeSaved() {
        boolean reg = net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this);
        float hp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
        if (reg && hp > 0) return true;
        return super.shouldBeSaved();
    }

    //  每 tick 强制校正（通用，不点名任何模组）

    @Override
    public void baseTick() {
        // 不死守卫已移到独立守护线程 immortalGuard（独立线程思路），不依赖原版 tick。
        super.baseTick();
    }

    /** 客户端+服务端不死守卫（关键）：外部注入 压客户端 getHealth()（agent 包装）→ 客户端 isDeadOrDying
     *  true → vanilla 死亡动画。用静态 SecureHealthClosure（外部注入 压不到）判断，表值>0 时每 tick
     *  强制恢复 dead/deathTime/pose——外部注入 判死被每 tick 拉回，辖界者不倒。表值=0 服务端主线程强制移除。 */
    @Override
    public void tick() {
        super.tick();
        if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this)) {
            float hp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
            if (hp > 0) {
                this.dead = false;
                this.deathTime = 0;
                // 表值恢复（复活）→ 撤销死亡放行标记，防残留被外部利用移除活实体
                net.minecraft.client.yiz.xian.core.EntityRemoveProtection.revokeDeathAllow(this.getUUID());
                if (this.getPose() == net.minecraft.world.entity.Pose.DYING) {
                    this.setPose(net.minecraft.world.entity.Pose.STANDING);
                }
            } else if ((hp <= 0 || this.pendingDeathRemove) && !this.isRemoved() && this.deathTime >= 20) {
                // 死亡兜底移除：正常路径由 vanilla tickDeath（表值0 放行）死亡动画 20 tick 后
                // remove(KILLED) 完成；掉落物已在 die() 的 vanilla 掉落链产生
                //（dropAllDeathLoot → LootTable 钻石 + dropExperience 经验球）。
                // 此处仅防 tickDeath 意外未移除导致残留：动画播完仍存在则强制移除。
                beginForceRemove();
                try {
                    net.minecraft.client.yiz.xian.core.EntityRemoveProtection.allowDeathRemove(this.getUUID());
                    this.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                } catch (Throwable ignored) {}
                finally {
                    endForceRemove();
                }
            }
        }
    }

    /** 待死亡移除兜底标记（immortalGuard 独立线程表值0 时设，主线程 tick 动画播完后兜底移除）。 */
    private boolean pendingDeathRemove;

    /** 死亡动画拦截（关键兜底）：表值>0 时 tickDeath 空实现——不累积 deathTime、不进入倒地死亡动画。
     *  外部 agent（外部注入）用平行死亡标记/覆盖 isDeadOrDying 触发 vanilla 死亡流程时，
     *  tickDeath 是死亡动画与移除的根源，拦截它即"外部注入 判死也不倒地"。表值=0 放行正常死亡。 */
    /** 死亡动画诊断限频：每实体（UUID+端）各自 10 条——多只辖界者测试时单端 static 10 条会被第一只占满，
     *  后续实体客户端/服务端死亡日志静默 → 无法确认每只是否判死（"部分打死不倒地"诊断盲区）。 */
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> TICKDEATH_LOG_BY_UUID =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** 诊断：传导 CD 读值限频（编辑器改 INVINCIBILITY_MULT 是否实时跟随）。 */
    private static final java.util.concurrent.atomic.AtomicInteger COND_DIAG = new java.util.concurrent.atomic.AtomicInteger();

    @Override
    protected void tickDeath() {
        if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            return;
        }
        // 摸底：表值=0 时 tickDeath 是否执行（每 UUID+端 独立限频，多只实体互不淹没）
        String deathKey = this.getUUID() + (this.level().isClientSide() ? "#c" : "#s");
        java.util.concurrent.atomic.AtomicInteger deathLog =
            TICKDEATH_LOG_BY_UUID.computeIfAbsent(deathKey, k -> new java.util.concurrent.atomic.AtomicInteger());
        if (deathLog.incrementAndGet() <= 10) {
            LOGGER.warn("[QZK-DEATH] tickDeath 表值0 deathTime={} removed={} {} uuid={}", this.deathTime, this.isRemoved(), this.level().isClientSide() ? "client" : "server", this.getUUID());
        }
        // 表值=0（真实死亡）：先标记死亡移除放行（aiStep 的 allowDeathRemove 可能因死亡实体 aiStep 停止而没执行，
        // 导致 vanilla tickDeath 的 remove(KILLED) 被 EntityRemoveProtectionMixin 拦 → 辖界者倒地不移除残留）
        net.minecraft.client.yiz.xian.core.EntityRemoveProtection.allowDeathRemove(this.getUUID());
        super.tickDeath();
    }

    /** 每 tick 校正受保护实体：混淆血量 dec 回写 vanilla 通道（防通道/外部直写拉低）+ 清未知 Float delta + 防 removed/MAX_HEALTH/SECURE_PULSE 篡改。 */
    protected void enforceSecureHealthState() {
        if (!net.minecraft.client.yiz.tool.health.SecureHealthClosure.hasObf(this)) return;
        // 混淆串每 tick 校验回写（DataItem 直写不触发 onSyncedDataUpdated，只能每 tick 兜底拉回）
        this.correctObfHealthString();
        // 权威表 → 混淆串（外部注入 直写串被表值覆盖，客户端显示拉回；服务端逻辑血量始终读表）
        try {
            net.minecraft.client.yiz.tool.health.SecureHealthClosure.enforceAuthority(this);
        } catch (Throwable ignored) {}
        // 表值完整性校验（防外部 DataItem 直写混淆串突破传导 cap——外部注入 直写大额立即回滚）
        try {
            net.minecraft.client.yiz.tool.health.SecureHealthClosure.enforceTableIntegrity(this, this.conductionCap());
        } catch (Throwable ignored) {}
        try {
            // 混淆串 dec 回写 vanilla DATA_HEALTH_ID 通道（红闪/死亡动画等读通道；getHealth 仍以混淆串为准）
            float realHp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
            net.minecraft.client.yiz.tool.health.EntityActuallyHurt.catchSetTrueHealth(this, realHp);
        } catch (Throwable ignored) {}
        try {
            var vanillaHealth = net.minecraft.client.yiz.tool.health.HealthChannelScanner.getVanillaHealthAccessor();
            net.minecraft.client.yiz.tool.health.DirectHealthFallback.forEachFloatItem(this, (acc, cur, item) -> {
                if (vanillaHealth != null && acc.getId() == vanillaHealth.getId()) return;
                if (cur != 0.0F) {
                    item.setValue(0.0F);
                    item.setDirty(true);
                }
            });
        } catch (Throwable ignored) {}
        try {
            if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0 && this.isRemoved()) {
                clearForcedRemoved();
            }
        } catch (Throwable ignored) {}
        try {
            var hpInst = this.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            float protectedMax = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getMaxHealth(this);
            if (hpInst != null && hpInst.getValue() != protectedMax && protectedMax > 0) {
                hpInst.setBaseValue(protectedMax);
                hpInst.removeModifiers();
            }
        } catch (Throwable ignored) {}
        //  类还原兜底：SECURE_PULSE 被外部清零 → 防御失效。每 tick 若 <1 则经 Gate 受保护重设 
        // （与 AttributeStandardizer 审计互补：审计 20 tick 一轮，此处每 tick 兜底，防两次审计之间被清）
        try {
            var pulseInst = this.getAttribute(net.minecraft.client.yiz.attribute.YizAttributes.SECURE_PULSE.get());
            if (pulseInst != null && pulseInst.getValue() < 1.0) {
                net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> ro =
                    net.minecraftforge.registries.RegistryObject.create(
                        new net.minecraft.resources.ResourceLocation("yizmodqzk", "secure_pulse"),
                        net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES);
                net.minecraft.client.yiz.tool.attribute.EntityAttributeGate.set(this, ro, "secure_pulse", 1.0);
            }
        } catch (Throwable ignored) {}
        //  isDead 平行死亡标记主动清除（学 UomWither）：血量>0 时强制 dead=false，
        // 防外部打「平行死亡标记」绕过 isDeadOrDying（与 baseTick 双保险）
        try {
            if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
                java.lang.reflect.Field deadF = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("dead");
                deadF.setAccessible(true);
                deadF.setBoolean(this, false);
            }
        } catch (Throwable ignored) {}
        //  传导限伤属性权威检测（防外部改 conduction_cap 绕过限伤）：属性≠权威值 → 还原 + 日志 
        try {
            net.minecraft.client.yiz.tool.health.ConductionCapVault.checkAndRestore(this);
        } catch (Throwable ignored) {}
    }

    private void clearForcedRemoved() {
        if (!REMOVED_FIELDS_READY) {
            try {
                REMOVAL_REASON_FIELD = net.minecraft.world.entity.Entity.class.getDeclaredField("removalReason");
                REMOVAL_REASON_FIELD.setAccessible(true);
                REMOVED_FIELD = net.minecraft.world.entity.Entity.class.getDeclaredField("removed");
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
}
