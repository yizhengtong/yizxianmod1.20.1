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

    // ═══════════════════ 门禁判定 ═══════════════════

    @Override
    public void aiStep() {
        withGate(() -> {
            boolean server = !this.level().isClientSide();
            if (server) {
                if (!this.yizxianAttrsApplied) {
                    this.yizxianAttrsApplied = true;
                    this.applyEntityAttributes();
                }
                this.mirrorDefensiveAttributes();
                if (this.getHealth() <= 0.0F) {
                    net.minecraft.client.yiz.xian.core.EntityRemoveProtection.allowDeathRemove(this.getUUID());
                    YizieManager.checkAndRemove(this);
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
            float ratio = oldMax > 0 ? this.getHealth() / (float) oldMax : 1.0F;
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

    // ═══════════════════ 反击递归保护 ═══════════════════

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

    // ═══════════════════ 坐标变动入口 ═══════════════════

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

    // ═══════════════════ 速度量入口 ═══════════════════

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

    // ═══════════════════ 特判免疫 ═══════════════════

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
}
