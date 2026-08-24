package net.minecraft.client.yiz.xian.entity.base;

import net.minecraft.client.yiz.editor.PoshiBearer;
import net.minecraft.client.yiz.editor.PoshiBypassBridge;
import net.minecraft.client.yiz.tool.YizieManager;
import net.minecraft.client.yiz.tool.attribute.EntityAttributeGate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
    private boolean levelCallbackInstalled;
    private volatile boolean reAddQueued;

    /** 字段级位置保护：缓存最近一次安全位置，检测被外部直接写 position 字段异常传送后恢复。 */
    private double safeX, safeY, safeZ;
    private boolean safePosReady;
    private boolean restoringPos;

    private double templateMaxHealth = -1;
    private double templateAttackDamage = -1;

    private double lastMirrorArmor = Double.NaN;
    private double lastMirrorSpellDefense = Double.NaN;

    /**
     * 实体身份快照：id/uuid/stringUUID 被外部直改后按原值恢复（通用防“毁对象式”清除，不针对任何模组）。
     * 守卫线程写、主线程在身份读取兜底里读，必须 volatile 保证可见性。
     */
    private volatile int identityId = Integer.MIN_VALUE;
    private volatile UUID identityUuid;
    private volatile String identityStringUuid;
    private volatile boolean identityReady;

    /** SafeLevelCallback 实例引用：外部把 levelCallback 直写为 NULL/其他回调后据此重装。 */
    private SafeLevelCallback safeLevelCallback;

    protected YizxianMob(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    //  门禁判定 

    @Override
    public void aiStep() {
        // 身份完整性必须在所有 UUID/registry 查询之前恢复（外部直改 id/uuid 会让不死注册表按错误键查找）
        guardIdentity();
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
                // 替换 levelCallback 为 SafeLevelCallback（拦截比 setRemoved 更底层的 onRemove 移除）
                this.installSafeLevelCallback();
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
            // 注册 MAX_HEALTH 到属性标准化守护（20 tick 审计兜底还原）——vanilla 属性不走 setAttr，此前是审计盲区
            net.minecraft.client.yiz.tool.attribute.AttributeStandardizer.registerStandard(this,
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, "max_health", 0);
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
        // 必须 put 替换而非 putIfAbsent：退出存档时旧实体对象可能仍留在注册表里
        //（非 FORCE_REMOVE 路径不会 unregister），重进后同 UUID 的新实体会被旧条目挡掉，
        // 导致新实体完全没有不死守卫覆盖。替换时同步摘掉旧 id 的 agent 保护。
        YizxianMob old = IMMORTAL_REGISTRY.put(e.getUUID(), e);
        if (old != null && old != e) {
            net.minecraft.client.yiz.tool.health.EntityASMUtil.unregisterProtectedId(old.getId());
        }
        // 加入辖界者 id 集合（agent 拦截 Int2ObjectMap.remove 用，阻止列表清从 EntityTickList/ChunkMap 删辖界者）
        net.minecraft.client.yiz.tool.health.EntityASMUtil.registerProtectedId(e.getId());
    }

    private static void unregisterImmortal(java.util.UUID id) {
        if (id == null) return;
        YizxianMob e = IMMORTAL_REGISTRY.remove(id);
        if (e != null) {
            net.minecraft.client.yiz.tool.health.EntityASMUtil.unregisterProtectedId(e.getId());
        }
    }

    /** 服务器停止/退出存档时清空不死注册表：避免旧实体对象残留，重进后同 UUID 新实体
     *  被旧条目拉回 → 多份血条/实体实例。由 YizxianMod.onServerStopping 调用。 */
    public static void clearImmortalRegistry() {
        for (java.util.UUID id : new java.util.ArrayList<>(IMMORTAL_REGISTRY.keySet())) {
            unregisterImmortal(id);
        }
    }

    /** /yiz remove 后门清理：从不死注册表和 agent 保护 id 集合中移除该实体。 */
    public static void forceRemoveCleanup(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof YizxianMob mob) {
            unregisterImmortal(mob.getUUID());
            net.minecraft.client.yiz.tool.health.EntityASMUtil.unregisterProtectedId(mob.getId());
        }
    }

    /** 懒加载身份字段的 Unsafe 偏移（按 Entity 的字段类型+双名定位，不依赖 setAccessible 调用栈）。 */
    private static void ensureIdentityFields() {
        if (IDENTITY_FIELDS_READY) return;
        synchronized (YizxianMob.class) {
            if (IDENTITY_FIELDS_READY) return;
            try {
                sun.misc.Unsafe u = null;
                try {
                    java.lang.reflect.Constructor<sun.misc.Unsafe> ctor =
                        sun.misc.Unsafe.class.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    u = ctor.newInstance();
                } catch (Throwable t) {
                    java.lang.reflect.Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                    uf.setAccessible(true);
                    u = (sun.misc.Unsafe) uf.get(null);
                }
                IDENTITY_UNSAFE = u;
                ID_FIELD_OFFSET = entityFieldOffset("id", "f_19848_", int.class);
                UUID_FIELD_OFFSET = entityFieldOffset("uuid", "f_19820_", java.util.UUID.class);
                STRING_UUID_FIELD_OFFSET = entityFieldOffset("stringUUID", "f_19821_", String.class);
            } catch (Throwable ignored) {
            } finally {
                IDENTITY_FIELDS_READY = true;
            }
        }
    }

    private static long entityFieldOffset(String official, String srg, Class<?> type) {
        if (IDENTITY_UNSAFE == null) return -1L;
        java.lang.reflect.Field f = findField(net.minecraft.world.entity.Entity.class, official, srg);
        if (f != null && f.getType() == type) {
            return IDENTITY_UNSAFE.objectFieldOffset(f);
        }
        for (java.lang.reflect.Field cand : net.minecraft.world.entity.Entity.class.getDeclaredFields()) {
            if (cand.getType() == type && !java.lang.reflect.Modifier.isStatic(cand.getModifiers())) {
                return IDENTITY_UNSAFE.objectFieldOffset(cand);
            }
        }
        return -1L;
    }

    /**
     * 完整性守卫统一闸门：只有在服务端正常运行（未停机、未进入 save-and-quit 流程）时才允许
     * 恢复身份 / 重新加入世界。停机保存期间任何 addFreshEntity / 结构回填都会与
     * saveAllChunks、实体拆卸流程并发，破坏存档写出。
     */
    private boolean integrityGuardAllowed() {
        if (this.level().isClientSide()) return false;
        if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            return !net.minecraft.client.yiz.xian.core.EntityRemoveProtection.isShuttingDownOrSaving(sl);
        }
        return false;
    }

    /**
     * 身份完整性守卫：外部把 id/uuid/stringUUID 直改成 -1/随机值后，按首次快照恢复。
     * 只做 Entity 基础身份字段的通用保护，不涉及任何模组专有字段。
     */
    private void guardIdentity() {
        if (!integrityGuardAllowed()) return;
        try {
            ensureIdentityFields();
            if (IDENTITY_UNSAFE == null) return;
            int curId = ID_FIELD_OFFSET >= 0 ? IDENTITY_UNSAFE.getInt(this, ID_FIELD_OFFSET) : this.getId();
            UUID curUuid = UUID_FIELD_OFFSET >= 0 ? (UUID) IDENTITY_UNSAFE.getObject(this, UUID_FIELD_OFFSET) : this.getUUID();
            String curStr = STRING_UUID_FIELD_OFFSET >= 0
                ? (String) IDENTITY_UNSAFE.getObject(this, STRING_UUID_FIELD_OFFSET)
                : this.getStringUUID();
            if (!identityReady) {
                // 快照必须是有效身份：读到空值说明字段已被清空或尚未就绪，
                // 此时记下来会把「空身份」当成正确身份，之后永远恢复不回来。
                if (curUuid == null || curStr == null) return;
                identityId = curId;
                identityUuid = curUuid;
                identityStringUuid = curStr;
                identityReady = true;
                return;
            }
            boolean broken = identityId != curId
                || !Objects.equals(identityUuid, curUuid)
                || !Objects.equals(identityStringUuid, curStr);
            if (!broken) return;
            if (ID_FIELD_OFFSET >= 0) {
                IDENTITY_UNSAFE.putInt(this, ID_FIELD_OFFSET, identityId);
                if (curId != identityId) {
                    // 受保护 id 集合按 id 索引：id 被改期间集合里仍是旧值，恢复后必须同步，
                    // 否则所有按 id 的判定都对不上，底层保护整体失效。
                    net.minecraft.client.yiz.tool.health.EntityASMUtil.unregisterProtectedId(curId);
                    net.minecraft.client.yiz.tool.health.EntityASMUtil.registerProtectedId(identityId);
                }
            }
            if (UUID_FIELD_OFFSET >= 0) {
                IDENTITY_UNSAFE.putObject(this, UUID_FIELD_OFFSET, identityUuid);
            }
            if (STRING_UUID_FIELD_OFFSET >= 0) {
                IDENTITY_UNSAFE.putObject(this, STRING_UUID_FIELD_OFFSET, identityStringUuid);
            }
            LOGGER.warn("[QZK-IDENTITY] restored id={} uuid={} stringUUID={} on {}",
                identityId, identityUuid, identityStringUuid, this.getClass().getSimpleName());
            // 身份被改过的实体可能已被按错误键摘出世界结构，交给列表清守卫复核
            requestWorldIntegrityCheck();
        } catch (Throwable ignored) {
            // 任何一步失败都不能破坏正常 tick
        }
    }

    /** 主线程/守卫线程都可调用：标记一次世界完整性复核（幂等，入队执行）。停机保存期间不排任务。 */
    private void requestWorldIntegrityCheck() {
        if (!integrityGuardAllowed()) return;
        reAddQueued = false;
        if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.getServer().tell(new net.minecraft.server.TickTask(0, () -> reAddIfRemovedFromWorld()));
        }
    }

    /** 独立线程不死守卫：表值>0 强制恢复不死状态；表值=0 强制移除（死亡清理，发移除包 → 客户端移除）。 */
    private void immortalGuard() {
        // 停机保存/退出期间禁止任何身份恢复与结构回填，避免污染 saveAllChunks
        if (!integrityGuardAllowed()) return;
        // /yiz remove 后门：正在强制清除时不恢复/不重加
        if (net.minecraft.client.yiz.tool.health.EntityASMUtil.isForceRemoving(this.getId())) return;
        // 先恢复被直改的 id/uuid/stringUUID，再按 UUID 查询注册表（顺序不可反）
        guardIdentity();
        if (!net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this)) {
            unregisterImmortal(this.getUUID());
            return;
        }
        float hp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
        if (hp > 0) {
            this.dead = false;
            this.deathTime = 0;
            // 外部“毁对象式清除”会把 canUpdate 置 false 停掉主线程更新；独立守卫负责拉回
            try { this.canUpdate(true); } catch (Throwable ignored) {}
            // 能力容器被外部作废后不会自行恢复，挂在其上的数据会一并失效；复活是幂等操作
            try { this.reviveCaps(); } catch (Throwable ignored) {}
            if (this.isRemoved()) this.clearForcedRemoved();
            if (this.getPose() == net.minecraft.world.entity.Pose.DYING) {
                this.setPose(net.minecraft.world.entity.Pose.STANDING);
            }
            // 免疫「forceSetPos 直写 position 字段」：先恢复异常传送的位置（避免带着未加载 chunk 的坐标重新加入）
            this.guardPosition();
            // 免疫「列表清」：检测被外部从世界结构删除则重新加入
            this.reAddIfRemovedFromWorld();
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
    private static java.lang.reflect.Field LEVEL_CALLBACK_FIELD;
    private static java.lang.reflect.Field ENTITY_MANAGER_FIELD;
    private static java.lang.reflect.Field KNOWN_UUIDS_FIELD;
    private static java.lang.reflect.Field ENTITY_TICK_LIST_FIELD;

    /** 身份字段 Unsafe 句柄（id/uuid/stringUUID 直读直写；只按 Entity 类型+字段类型定位，不猜模组字段）。 */
    private static volatile boolean IDENTITY_FIELDS_READY;
    private static sun.misc.Unsafe IDENTITY_UNSAFE;
    private static long ID_FIELD_OFFSET = -1L;
    private static long UUID_FIELD_OFFSET = -1L;
    private static long STRING_UUID_FIELD_OFFSET = -1L;

    /** 世界内部结构探测缓存（按类型定位 Minecraft 自身结构，不点名任何模组）。 */
    private static volatile boolean WORLD_PROBE_FIELDS_READY;
    private static java.lang.reflect.Field CHUNK_MAP_FIELD;
    private static java.lang.reflect.Field CHUNK_MAP_ENTITY_MAP_FIELD;
    private static java.lang.reflect.Field SECTION_STORAGE_FIELD;
    private static java.lang.reflect.Field SECTION_STORAGE_SECTIONS_FIELD;
    private static java.lang.reflect.Field SECTION_MULTIMAP_FIELD;
    private static java.lang.reflect.Field SECTION_MULTIMAP_BY_CLASS_FIELD;

    private static final int CONDUCTION_HIT_CD_FALLBACK = 20;
    protected long lastConductionHitTick = Long.MIN_VALUE;

    /** 受击红闪门禁：仅本模组传导扣血流程广播红闪时打开，拦截外部绕过 hurt() 直接广播的红闪。
     *  与 1.21.1 的 QuanshouzheEntity.CONDUCTION_HIT_FLASH 同逻辑，但下沉到基类——
     *  所有 YizxianMob 子类（辖界者/邪狱龙/踏虚体/山林首者）共用。 */
    private static final ThreadLocal<Boolean> CONDUCTION_HIT_FLASH = ThreadLocal.withInitial(() -> false);

    public static boolean isConductionHitFlash() {
        return CONDUCTION_HIT_FLASH.get();
    }

    /** 广播受击红闪（服务端）：包住 ServerLevel.broadcastDamageEvent，让红闪门控 mixin 放行。 */
    protected void broadcastHurtFlash(net.minecraft.world.damagesource.DamageSource source) {
        if (this.level().isClientSide()) return;
        CONDUCTION_HIT_FLASH.set(true);
        try {
            if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.broadcastDamageEvent(this, source);
            }
        } finally {
            CONDUCTION_HIT_FLASH.remove();
        }
    }

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
            int key = this.entityData.get(net.minecraft.client.yiz.tool.health.HealthChannels.getSecureObfKey());
            String enc = this.entityData.get(net.minecraft.client.yiz.tool.health.HealthChannels.getSecureObf());
            float v = net.minecraft.client.yiz.tool.health.FloatObf.dec(enc, key);
            float maxHp = secureMaxHealth();
            // 未初始化（哨兵 -1）或损坏/超上限 → 用受保护 maxHp 初始化
            if (Float.isNaN(v) || v < 0 || v > maxHp) {
                net.minecraft.client.yiz.tool.health.SecureHealthClosure.beginObfWrite();
                try {
                    this.entityData.set(net.minecraft.client.yiz.tool.health.HealthChannels.getSecureObf(),
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
        if (!this.entityData.hasItem(net.minecraft.client.yiz.tool.health.HealthChannels.getSecureObf())) {
            int key = new java.security.SecureRandom().nextInt();
            this.entityData.define(net.minecraft.client.yiz.tool.health.HealthChannels.getSecureObf(),
                net.minecraft.client.yiz.tool.health.FloatObf.enc(-1.0F, key));
            this.entityData.define(net.minecraft.client.yiz.tool.health.HealthChannels.getSecureObfKey(), key);
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
        if (key.getId() != net.minecraft.client.yiz.tool.health.HealthChannels.getSecureObf().getId()) return;
        correctObfHealthString();
    }

    /** 校验 SECURE_OBF 混淆串：非法（dec 失败 / 越界 / 负数）→ 回写表值 enc（防外部直写串）。
     *  由 onSyncedDataUpdated（即时）与 enforceSecureHealthState（每 tick 兜底，防 DataItem 直写不触发钩子）共用。 */
    private void correctObfHealthString() {
        if (SELF_CORRECTING.get()) return;
        boolean invalid;
        int k = this.entityData.get(net.minecraft.client.yiz.tool.health.HealthChannels.getSecureObfKey());
        try {
            String enc = this.entityData.get(net.minecraft.client.yiz.tool.health.HealthChannels.getSecureObf());
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
                this.entityData.set(net.minecraft.client.yiz.tool.health.HealthChannels.getSecureObf(),
                    net.minecraft.client.yiz.tool.health.FloatObf.enc(
                        net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this), k));
            } finally {
                net.minecraft.client.yiz.tool.health.SecureHealthClosure.endObfWrite();
            }
        } finally {
            SELF_CORRECTING.remove();
        }
    }

    /**
     * 身份读取的空值兜底 —— 身份字段被外部清空时，调用方仍能拿到稳定身份。
     *
     * <p>实体身份是一切按 UUID 索引的表（血量权威表、注册表、跟踪表）的键。字段被置空后，
     * 每一处查表都会直接抛空指针，服务端 tick 当场崩溃 —— 这是比「移除实体」更廉价的打法：
     * 不用绕过任何保护，只要让保护自己炸掉。</p>
     *
     * <p>这里只保证读到的身份稳定；字段本身的写回交给身份守卫统一处理，避免高频路径上写字段。</p>
     */
    @Override
    public java.util.UUID getUUID() {
        java.util.UUID current = super.getUUID();
        if (current != null) return current;
        java.util.UUID snapshot = this.identityUuid;
        return snapshot != null ? snapshot : current;
    }

    @Override
    public String getStringUUID() {
        String current = super.getStringUUID();
        if (current != null) return current;
        String snapshot = this.identityStringUuid;
        if (snapshot != null) return snapshot;
        java.util.UUID uuidSnapshot = this.identityUuid;
        return uuidSnapshot != null ? uuidSnapshot.toString() : current;
    }

    /**
     * 不参与自然清除。
     *
     * <p>「持久化标记」是可以被外部改写的普通状态：置 false 后原版自己就会在玩家走远时清掉实体，
     * 移除请求由原版发出、看起来完全合法。这里从判定源头返回固定值，使该标记被改写也不生效。</p>
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    /** 持久化混淆血量串 + key（防 reload 时 key 变 → 垃圾值）。 */
    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        try {
            tag.putString("yizxian_obf_health", this.entityData.get(net.minecraft.client.yiz.tool.health.HealthChannels.getSecureObf()));
            tag.putInt("yizxian_obf_key", this.entityData.get(net.minecraft.client.yiz.tool.health.HealthChannels.getSecureObfKey()));
        } catch (Throwable ignored) {}
    }

    /** 恢复混淆血量串 + key。 */
    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        // 鉴权必须在 super 之前：vanilla 的 readAdditionalSaveData 会读 NBT 的 Health 字段直接调
        // setHealth（恢复血量），外部 mod 借道这里即可绕过传导限伤一次扣血。只在 vanilla 实体
        // 加载流程（Entity.load → readAdditionalSaveData）调用时才放行整个 super 链。
        if (!isVanillaEntityLoadCaller()) return;
        super.readAdditionalSaveData(tag);
        try {
            if (tag.contains("yizxian_obf_key", net.minecraft.nbt.Tag.TAG_INT)) {
                int key = tag.getInt("yizxian_obf_key");
                this.entityData.set(net.minecraft.client.yiz.tool.health.HealthChannels.getSecureObfKey(), key);
                if (tag.contains("yizxian_obf_health", net.minecraft.nbt.Tag.TAG_STRING)) {
                    this.entityData.set(net.minecraft.client.yiz.tool.health.HealthChannels.getSecureObf(),
                        tag.getString("yizxian_obf_health"));
                }
            }
        } catch (Throwable ignored) {}
    }

    /** 调用栈是否含 vanilla Entity.load（读存档必经帧）——用于拒绝外部 mod 直接调 readAdditionalSaveData 篡改血量。 */
    protected static boolean isVanillaEntityLoadCaller() {
        try {
            return java.lang.StackWalker.getInstance(java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames.anyMatch(f -> {
                    String cls = f.getDeclaringClass().getName();
                    String m = f.getMethodName();
                    return "net.minecraft.world.entity.Entity".equals(cls)
                        && ("load".equals(m) || "m_20258_".equals(m));
                }));
        } catch (Throwable t) {
            return true;  // 鉴权异常时保守放行，避免正常读存档失效
        }
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
        this.broadcastHurtFlash(source);
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
        boolean forceRemoving = net.minecraft.client.yiz.tool.health.EntityASMUtil.isForceRemoving(this.getId());
        if (!level().isClientSide()
                && !FORCE_REMOVE.get()
                && !forceRemoving
                && net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this)
                && net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this) > 0) {
            return;
        }
        // 实体正常移除/死亡：清理服务端权威表与完整性表
        net.minecraft.client.yiz.tool.health.SecureHealthClosure.removeAuthority(this);
        net.minecraft.client.yiz.tool.health.SecureHealthClosure.removeIntegrity(this.getUUID());
        super.remove(reason);
        if (!level().isClientSide() && (FORCE_REMOVE.get() || forceRemoving)) {
            unregisterImmortal(this.getUUID());
        }
        // 兜底：死亡移除后手动广播 Destroy 包（vanilla 移除广播链路在多实体/召唤竞态下可能未达客户端
        // → 客户端残留"打死不倒地"实体；这里无论 vanilla 是否广播，都显式让客户端移除该实体 id）
        if (!level().isClientSide() && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            try {
                net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket pkt =
                    new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(this.getId());
                // 用 getPlayerList().getPlayers() 覆盖所有在线玩家（含死亡/重生切换中的玩家）：
                // sl.players() 在玩家死亡重生窗口可能不含该玩家 → 移除包丢失 → 客户端残留实体模型。
                for (net.minecraft.server.level.ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
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
        guardIdentity();
        super.tick();
        if (net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(this)) {
            float hp = net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(this);
            if (hp > 0) {
                this.dead = false;
                this.deathTime = 0;
                try { this.canUpdate(true); } catch (Throwable ignored) {}
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
            //  独立权威上限 = 模板值 × 难度乘数。不能读属性自身当保护值（SecureHealthClosure.getMaxHealth
            // 读的就是该属性 → 比较自己跟自己恒 false，空转）；用 templateMaxHealth 才是「未被篡改」的基准。
            double authoritativeMax = this.templateMaxHealth * difficultyMultiplier();
            if (hpInst != null && authoritativeMax > 0
                    && Math.abs(hpInst.getValue() - authoritativeMax) > 0.001) {
                hpInst.setBaseValue(authoritativeMax);
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
                java.lang.reflect.Field deadF = findField(net.minecraft.world.entity.LivingEntity.class, "dead", "f_20890_");
                if (deadF != null) {
                    deadF.setAccessible(true);
                    deadF.setBoolean(this, false);
                }
            }
        } catch (Throwable ignored) {}
        //  传导限伤属性权威检测（防外部改 conduction_cap 绕过限伤）：属性≠权威值 → 还原 + 日志 
        try {
            net.minecraft.client.yiz.tool.health.ConductionCapVault.checkAndRestore(this);
        } catch (Throwable ignored) {}
    }

    /**
     * 反射字段双名兼容：dev 环境是 official 名，生产环境（reobf jar）是 SRG 名 f_xxx。
     * 先试 official 名，NoSuchFieldException 后回退 SRG 名，找不到返回 null。
     */
    private static java.lang.reflect.Field findField(Class<?> clazz, String official, String srg) {
        try {
            return clazz.getDeclaredField(official);
        } catch (NoSuchFieldException e) {
            try {
                return clazz.getDeclaredField(srg);
            } catch (NoSuchFieldException e2) {
                return null;
            }
        }
    }

    private void clearForcedRemoved() {
        // 1.20.1 无 removed 字段（isRemoved 判 removalReason != null），原反射 "removed" 字段必然失败
        // 导致整个清除失效（REMOVED_FIELD==null → 提前 return，removalReason 从未被清）。
        // 改用官方 protected unsetRemoved() 清空 removalReason，反射直清作兜底（unsetRemoved 可能被外部 override）。
        try {
            this.unsetRemoved();
        } catch (Throwable ignored) {
            try {
                if (REMOVAL_REASON_FIELD == null) {
                    REMOVAL_REASON_FIELD = findField(net.minecraft.world.entity.Entity.class, "removalReason", "f_146795_");
                    if (REMOVAL_REASON_FIELD != null) REMOVAL_REASON_FIELD.setAccessible(true);
                }
                if (REMOVAL_REASON_FIELD != null) REMOVAL_REASON_FIELD.set(this, null);
            } catch (Throwable ignored2) {}
        }
    }

    /**
     * 替换 levelCallback 为 {@link SafeLevelCallback}（onRemove 空实现）——拦截比 setRemoved 更底层的移除。
     * 原版 setRemoved 是 final（无法 override），它内部靠 levelCallback.onRemove(reason) 完成「从 EntitySection 移除」；
     * 替换 levelCallback 后，无论移除请求从 setRemoved/discard/反射直写/直调 onRemove 哪条路进来，
     * 最终落到 onRemove 都是空实现 → 活实体不会真正离开世界。
     */
    private void installSafeLevelCallback() {
        try {
            if (LEVEL_CALLBACK_FIELD == null) {
                LEVEL_CALLBACK_FIELD = findField(net.minecraft.world.entity.Entity.class, "levelCallback", "f_146801_");
                if (LEVEL_CALLBACK_FIELD != null) LEVEL_CALLBACK_FIELD.setAccessible(true);
            }
            if (LEVEL_CALLBACK_FIELD == null) { levelCallbackInstalled = true; return; }
            net.minecraft.world.level.entity.EntityInLevelCallback current =
                (net.minecraft.world.level.entity.EntityInLevelCallback) LEVEL_CALLBACK_FIELD.get(this);
            // 已装且未被外部替换 → 无需处理（不再只看布尔标记：外部直写 NULL/其他回调后下一 tick 会重装）
            if (current instanceof SafeLevelCallback sc) {
                safeLevelCallback = sc;
                levelCallbackInstalled = true;
                return;
            }
            if (safeLevelCallback != null) {
                // 回调被外部直写替换：复用已包装实例；若原版 delegate 已被换掉（例如 addFreshEntity 重置），
                // 仅在 delegate 仍可用时保留，否则更新为当前回调。
                if (current != null && current != net.minecraft.world.level.entity.EntityInLevelCallback.NULL
                        && current != safeLevelCallback.delegate) {
                    safeLevelCallback.delegate = current;
                }
                if (safeLevelCallback.delegate != null && safeLevelCallback.delegate != net.minecraft.world.level.entity.EntityInLevelCallback.NULL) {
                    this.setLevelCallback(safeLevelCallback);
                    levelCallbackInstalled = true;
                    return;
                }
                safeLevelCallback = null;
            }
            if (current != null && current != net.minecraft.world.level.entity.EntityInLevelCallback.NULL) {
                safeLevelCallback = new SafeLevelCallback(current);
                this.setLevelCallback(safeLevelCallback);
                levelCallbackInstalled = true;
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 包装原版 EntityInLevelCallback：onMove 委托原版（保持正常移动/section 更新），
     * onRemove 对「存活实体」空实现（拦截 EntitySection 移除）；白名单（真实死亡 / FORCE_REMOVE / 未注册）放行。
     */
    private final class SafeLevelCallback implements net.minecraft.world.level.entity.EntityInLevelCallback {
        private net.minecraft.world.level.entity.EntityInLevelCallback delegate;
        SafeLevelCallback(net.minecraft.world.level.entity.EntityInLevelCallback delegate) { this.delegate = delegate; }
        @Override public void onMove() {
            // 立即恢复被 forceSetPos 直写 position 字段的异常传送（NaN/Infinity/超远距离都算异常；
            // 纯距离比较对 NaN 永远为 false，必须显式判非有限值）
            if (restoringPos) { delegate.onMove(); return; }
            double cx = YizxianMob.this.getX(), cy = YizxianMob.this.getY(), cz = YizxianMob.this.getZ();
            boolean broken = !Double.isFinite(cx) || !Double.isFinite(cy) || !Double.isFinite(cz);
            if (!broken && safePosReady) {
                double dx = cx - safeX, dy = cy - safeY, dz = cz - safeZ;
                broken = !Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)
                        || dx * dx + dy * dy + dz * dz > 4096.0;
            }
            if (broken && safePosReady) {
                restoringPos = true;
                try {
                    withGate(() -> YizxianMob.this.setPos(safeX, safeY, safeZ));
                } finally {
                    restoringPos = false;
                }
                return;
            }
            delegate.onMove();
            if (!broken) {
                safeX = cx; safeY = cy; safeZ = cz;
                safePosReady = true;
            }
        }
        @Override public void onRemove(net.minecraft.world.entity.Entity.RemovalReason reason) {
            if (FORCE_REMOVE.get()
                    || net.minecraft.client.yiz.tool.health.EntityASMUtil.isForceRemoving(YizxianMob.this.getId())
                    || !net.minecraft.client.yiz.tool.health.SecureHealthClosure.isRegistered(YizxianMob.this)
                    || net.minecraft.client.yiz.tool.health.SecureHealthClosure.getHealth(YizxianMob.this) <= 0) {
                delegate.onRemove(reason);
            }
        }
    }

    /**
     * 免疫「列表清」：检测实体是否已被从世界结构（EntityLookup）移除，若缺失则重新加入。
     *
     * <p>外部「列表清」会反射直删 EntitySection/EntityTickList/EntityLookup/ChunkMap 里的实体，
     * 不经过 setRemoved/levelCallback.onRemove（mixin/替换 levelCallback 都拦不住）。这里每 tick
     * 用 {@code level.getEntity(id)} 检测实体是否还在 EntityLookup，不在则清移除状态 + 重新 addFreshEntity
     * 走完整加入流程（重新塞回所有结构）。独立线程并发加入用 try-catch 容错。</p>
     */
    private void reAddIfRemovedFromWorld() {
        if (!integrityGuardAllowed()) return;
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        if (net.minecraft.client.yiz.tool.health.EntityASMUtil.isForceRemoving(this.getId())) return;
        if (reAddQueued) return;
        reAddQueued = true;
        // 最小稳定修复：独立守卫线程不直接操作 ServerLevel/EntityLookup，避免并发修改导致
        // ConcurrentModificationException/Duplicate UUID。用 tell 入队到服务端线程执行
        //（execute 在非服务端线程会直接内联执行，不能保证切线程）。
        sl.getServer().tell(new net.minecraft.server.TickTask(0, () -> {
            try {
                // 任务入队后服务器可能刚好开始停机保存：执行前再查一次，避免 saveAllChunks 期间回填
                if (!integrityGuardAllowed()) return;
                if (this.level() != sl) return;
                boolean byIdMissing = sl.getEntity(this.getId()) != this;
                boolean notAdded = !this.isAddedToWorld();
                boolean tickMissing = isTickListMissing(sl);
                boolean chunkMissing = isChunkMapMissing(sl);
                boolean sectionMissing = isSectionMissing(sl);
                boolean knownUuidMissing = isKnownUuidMissing(sl);
                if (byIdMissing || notAdded || tickMissing || sectionMissing || knownUuidMissing || chunkMissing) {
                    this.clearForcedRemoved();
                    // 按缺失项逐个回填，不经过任何新增入口：外部对加入路径的封锁影响不到这里
                    boolean repaired = net.minecraft.client.yiz.xian.core.WorldPresenceGuard.repair(sl, this);
                    if (!repaired) {
                        // 结构定位不可用时退回官方加入流程（UUID 已在登记表时会被拒绝，先清）
                        this.clearKnownUuid();
                        sl.addFreshEntity(this);
                    }
                    LOGGER.warn("[QZK-READD] restored id={} uuid={} direct={} (byId={} added={} tick={} section={} knownUuid={} chunk={})",
                        this.getId(), this.getUUID(), repaired,
                        byIdMissing, notAdded, tickMissing, sectionMissing, knownUuidMissing, chunkMissing);
                    // 回填会重建原版 levelCallback，需下次 aiStep 重新装 SafeLevelCallback
                    this.levelCallbackInstalled = false;
                }
            } catch (Throwable t) {
                LOGGER.warn("[QZK-READD] add failed id={} uuid={}: {}", this.getId(), this.getUUID(), t.toString());
            } finally {
                reAddQueued = false;
            }
        }));
    }

    /** 检测实体是否已被从 EntityTickList 移除（灭神模式列表清只删 tickList/chunkMap，不删 byId，需单独检测）。 */
    private boolean isTickListMissing(net.minecraft.server.level.ServerLevel sl) {
        try {
            if (ENTITY_TICK_LIST_FIELD == null) {
                ENTITY_TICK_LIST_FIELD = findField(net.minecraft.server.level.ServerLevel.class, "entityTickList", "f_143243_");
                if (ENTITY_TICK_LIST_FIELD != null) ENTITY_TICK_LIST_FIELD.setAccessible(true);
            }
            if (ENTITY_TICK_LIST_FIELD == null) return false;
            Object tickList = ENTITY_TICK_LIST_FIELD.get(sl);
            if (tickList instanceof net.minecraft.world.level.entity.EntityTickList etl) {
                return !etl.contains(this);
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 懒加载世界内部结构的反射字段（全部按“类型形状”定位，不猜任何模组字段名）。 */
    private static void ensureWorldProbeFields() {
        if (WORLD_PROBE_FIELDS_READY) return;
        synchronized (YizxianMob.class) {
            if (WORLD_PROBE_FIELDS_READY) return;
            try {
                CHUNK_MAP_FIELD = findFieldByType(net.minecraft.server.level.ServerChunkCache.class, net.minecraft.server.level.ChunkMap.class);
                if (CHUNK_MAP_FIELD != null) {
                    CHUNK_MAP_FIELD.setAccessible(true);
                    CHUNK_MAP_ENTITY_MAP_FIELD = findIntKeyedMapField(net.minecraft.server.level.ChunkMap.class);
                    if (CHUNK_MAP_ENTITY_MAP_FIELD != null) CHUNK_MAP_ENTITY_MAP_FIELD.setAccessible(true);
                }
                SECTION_STORAGE_FIELD = findFieldByType(
                    net.minecraft.world.level.entity.PersistentEntitySectionManager.class,
                    net.minecraft.world.level.entity.EntitySectionStorage.class);
                if (SECTION_STORAGE_FIELD != null) {
                    SECTION_STORAGE_FIELD.setAccessible(true);
                    SECTION_STORAGE_SECTIONS_FIELD = findFieldByType(
                        net.minecraft.world.level.entity.EntitySectionStorage.class, java.util.Map.class);
                    if (SECTION_STORAGE_SECTIONS_FIELD != null) SECTION_STORAGE_SECTIONS_FIELD.setAccessible(true);
                }
                SECTION_MULTIMAP_FIELD = findFieldByType(
                    net.minecraft.world.level.entity.EntitySection.class, net.minecraft.util.ClassInstanceMultiMap.class);
                if (SECTION_MULTIMAP_FIELD != null) {
                    SECTION_MULTIMAP_FIELD.setAccessible(true);
                    SECTION_MULTIMAP_BY_CLASS_FIELD = findFieldByType(
                        net.minecraft.util.ClassInstanceMultiMap.class, java.util.Map.class);
                    if (SECTION_MULTIMAP_BY_CLASS_FIELD != null) SECTION_MULTIMAP_BY_CLASS_FIELD.setAccessible(true);
                }
            } catch (Throwable ignored) {
            } finally {
                WORLD_PROBE_FIELDS_READY = true;
            }
        }
    }

    /** 是否已从 ChunkMap.entityMap（按 Integer 键的 Map）被直删。 */
    private boolean isChunkMapMissing(net.minecraft.server.level.ServerLevel sl) {
        try {
            ensureWorldProbeFields();
            if (CHUNK_MAP_FIELD == null || CHUNK_MAP_ENTITY_MAP_FIELD == null) return false;
            Object chunkMap = CHUNK_MAP_FIELD.get(sl.getChunkSource());
            if (chunkMap == null) return false;
            Object entityMap = CHUNK_MAP_ENTITY_MAP_FIELD.get(chunkMap);
            return entityMap instanceof Map<?, ?> map && !map.containsKey(this.getId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** ChunkMap 单独重同步：先按正常通道摘除，再重新加入跟踪。 */
    private void resyncChunkTracking(net.minecraft.server.level.ServerLevel sl) {
        try {
            net.minecraft.server.level.ServerChunkCache cache = sl.getChunkSource();
            try {
                cache.removeEntity(this);
            } catch (Throwable ignored) {}
            java.lang.reflect.Method add = findMethod(cache.getClass(), "addEntity", "m_8443_",
                net.minecraft.world.entity.Entity.class);
            if (add != null) {
                add.setAccessible(true);
                add.invoke(cache, this);
                return;
            }
            // 无 addEntity 可用时退回完整加入（byId 仍在时可能被拒绝，仅作最后兜底）
            this.clearKnownUuid();
            sl.addFreshEntity(this);
            this.levelCallbackInstalled = false;
        } catch (Throwable ignored) {}
    }

    /** 是否已从当前 EntitySection 的 ClassInstanceMultiMap 各类型 List 中被直删。 */
    private boolean isSectionMissing(net.minecraft.server.level.ServerLevel sl) {
        try {
            ensureWorldProbeFields();
            if (SECTION_STORAGE_FIELD == null || SECTION_STORAGE_SECTIONS_FIELD == null
                    || SECTION_MULTIMAP_FIELD == null || SECTION_MULTIMAP_BY_CLASS_FIELD == null) {
                return false;
            }
            Object manager = readEntityManager(sl);
            if (manager == null) return false;
            Object storage = SECTION_STORAGE_FIELD.get(manager);
            if (storage == null) return false;
            Object sections = SECTION_STORAGE_SECTIONS_FIELD.get(storage);
            if (!(sections instanceof Map<?, ?> sectionsMap)) return false;
            for (Object section : sectionsMap.values()) {
                if (section == null) continue;
                Object multimap = SECTION_MULTIMAP_FIELD.get(section);
                if (multimap == null) continue;
                Object byClass = SECTION_MULTIMAP_BY_CLASS_FIELD.get(multimap);
                if (!(byClass instanceof Map<?, ?> byClassMap)) continue;
                for (Object list : byClassMap.values()) {
                    if (list instanceof java.util.Collection<?> collection && collection.contains(this)) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 是否已从 PersistentEntitySectionManager.knownUuids 被直删。 */
    private boolean isKnownUuidMissing(net.minecraft.server.level.ServerLevel sl) {
        try {
            if (KNOWN_UUIDS_FIELD == null) {
                KNOWN_UUIDS_FIELD = findField(net.minecraft.world.level.entity.PersistentEntitySectionManager.class, "knownUuids", "f_157491_");
                if (KNOWN_UUIDS_FIELD != null) KNOWN_UUIDS_FIELD.setAccessible(true);
            }
            if (KNOWN_UUIDS_FIELD == null) return false;
            Object mgr = readEntityManager(sl);
            Object known = mgr == null ? null : KNOWN_UUIDS_FIELD.get(mgr);
            return known instanceof java.util.Set<?> set && !set.contains(this.getUUID());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 按“类型形状”找字段：目标类声明的第一个类型可赋值给 expectType 的非静态字段。 */
    private static java.lang.reflect.Field findFieldByType(Class<?> owner, Class<?> expectType) {
        for (java.lang.reflect.Field f : owner.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (expectType.isAssignableFrom(f.getType())) return f;
        }
        return null;
    }

    /** 在类里找 key 为 Integer 的 Map 字段（ChunkMap.entityMap 的形状；不猜字段名）。 */
    private static java.lang.reflect.Field findIntKeyedMapField(Class<?> owner) {
        for (java.lang.reflect.Field f : owner.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (!java.util.Map.class.isAssignableFrom(f.getType())) continue;
            java.lang.reflect.Type generic = f.getGenericType();
            if (generic instanceof java.lang.reflect.ParameterizedType pt
                    && pt.getActualTypeArguments().length > 0
                    && pt.getActualTypeArguments()[0] == Integer.class) {
                return f;
            }
        }
        return null;
    }

    /** 读取 ServerLevel.entityManager（双名兼容反射，失败返回 null）。 */
    private static Object readEntityManager(net.minecraft.server.level.ServerLevel sl) {
        try {
            if (ENTITY_MANAGER_FIELD == null) {
                ENTITY_MANAGER_FIELD = findField(net.minecraft.server.level.ServerLevel.class, "entityManager", "f_143244_");
                if (ENTITY_MANAGER_FIELD != null) ENTITY_MANAGER_FIELD.setAccessible(true);
            }
            return ENTITY_MANAGER_FIELD == null ? null : ENTITY_MANAGER_FIELD.get(sl);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 双名查找方法（返回 null 表示没找到）。 */
    private static java.lang.reflect.Method findMethod(Class<?> owner, String official, String srg, Class<?>... params) {
        try {
            return owner.getDeclaredMethod(official, params);
        } catch (NoSuchMethodException e1) {
            try {
                return owner.getDeclaredMethod(srg, params);
            } catch (NoSuchMethodException e2) {
                return null;
            }
        }
    }

    /** 清 PersistentEntitySectionManager.knownUuids 里的本实体 UUID（双名兼容反射）。 */
    private void clearKnownUuid() {
        try {
            if (KNOWN_UUIDS_FIELD == null) {
                KNOWN_UUIDS_FIELD = findField(net.minecraft.world.level.entity.PersistentEntitySectionManager.class, "knownUuids", "f_157491_");
                if (KNOWN_UUIDS_FIELD != null) KNOWN_UUIDS_FIELD.setAccessible(true);
            }
            if (KNOWN_UUIDS_FIELD == null || !(this.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
            Object mgr = readEntityManager(sl);
            if (mgr == null) return;
            Object knownUuids = KNOWN_UUIDS_FIELD.get(mgr);
            if (knownUuids instanceof java.util.Set) {
                ((java.util.Set<?>) knownUuids).remove(this.getUUID());
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 字段级位置保护：外部「forceSetPos」直接写 position 字段（绕过 setPos/moveTo 门禁）把实体传送到远处。
     * 检测 position 距离最近一次安全位置过远（>64 格）则恢复，否则更新安全位置。
     */
    private void guardPosition() {
        if (this.level().isClientSide()) return;
        double cx = this.getX(), cy = this.getY(), cz = this.getZ();
        // NaN/Infinity 坐标会被外部“放逐式清除”写入；距离比较对 NaN 永远为 false，必须先显式判非有限值
        if (!Double.isFinite(cx) || !Double.isFinite(cy) || !Double.isFinite(cz)) {
            if (safePosReady) {
                try {
                    withGate(() -> this.setPos(safeX, safeY, safeZ));
                } catch (Throwable ignored) {}
            }
            return;
        }
        if (!safePosReady) {
            safeX = cx; safeY = cy; safeZ = cz;
            safePosReady = true;
            return;
        }
        double dx = cx - safeX, dy = cy - safeY, dz = cz - safeZ;
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)
                || dx * dx + dy * dy + dz * dz > 4096.0) {
            // 被异常传送，恢复到安全位置（withGate 绕过自己的门禁）
            try {
                withGate(() -> this.setPos(safeX, safeY, safeZ));
            } catch (Throwable ignored) {}
        } else {
            safeX = cx; safeY = cy; safeZ = cz;
        }
    }
}
