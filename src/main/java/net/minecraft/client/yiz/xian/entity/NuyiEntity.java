package net.minecraft.client.yiz.xian.entity;

import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.tool.chess.ChessUnitTable;
import net.minecraft.client.yiz.xian.entity.base.YizxianMob;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * 怒翼 —— 一费棋子，原版幻翼（Phantom）同款飞行 AI + 碰撞箱接触伤害。
 *
 * <p><b>AI 来源</b>：整套飞行/盘旋/俯冲逻辑按原版 {@code net.minecraft.world.entity.monster.Phantom}
 * 逐段搬运到本类（1.20.1，Forge 47.4.22 官方映射源码），不改机制：</p>
 * <ul>
 *   <li>飞行 = 覆写 {@link #travel} 去掉重力（等价原版 {@code FlyingMob.travel}），速度完全由
 *       {@link NuyiMoveControl} 直接写 {@code deltaMovement} 驱动；</li>
 *   <li>姿态 = {@link NuyiBodyRotationControl}（身体朝向直接吃 {@code yRot}）+ {@link NuyiLookControl}（空实现）；</li>
 *   <li>目标 = {@link NuyiTargetGoal}（原版只找玩家；此处按需求扩为「玩家 + Enemy 接口敌对生物」）；</li>
 *   <li>节奏 = {@link NuyiAttackStrategyGoal}（盘旋 N 秒 → 俯冲）→ {@link NuyiSweepAttackGoal}（俯冲撞到目标出伤）
 *       → {@link NuyiCircleAroundAnchorGoal}（绕锚点盘旋）。</li>
 * </ul>
 *
 * <p><b>继承线</b>：继承 {@link YizxianMob}（不是直接继承原版 {@code Phantom}），因此本模组所有防护照旧生效：
 * 混淆血量权威表、防外部改血/清零、防 TP、防速度注入、防击退、属性标准化审计、自走棋星级与描边。</p>
 *
 * <p><b>属性</b>：一费标准表（{@code auto-chess-standards}）+ 用户指定数值：
 * 生命值模板 {@value #TEMPLATE_MAX_HEALTH}（标准表一费 24，此处按需求取 12）、攻击力模板 {@value #TEMPLATE_ATTACK}
 * （= 碰撞伤害设计值 4）。生命/攻击仍走本模组既定难度缩放（HARD ×1.0 / NORMAL ×0.75 / EASY ×0.5）与
 * 星级倍率（1-3 费 ×1/×1.5/×2.25），所以实机数值 = 模板 × 难度 × 星级。</p>
 */
public class NuyiEntity extends YizxianMob {

    // ── 扇翅节拍（与原版幻翼完全一致；客户端模型用同一套公式，见 NuyiModel.setupAnim）──
    /** 每 tick 扇翅角度增量（度）。 */
    public static final float FLAP_DEGREES_PER_TICK = 7.448451F;
    /** 一次完整扇翅的 tick 数（原版 {@code Mth.ceil(24.166098F)}）。 */
    public static final int TICKS_PER_FLAP = Mth.ceil(24.166098F);

    // ── 一费标准模板（applyVanillaDifficultyScale / applyEntityAttributes 用）──
    /** 生命值模板（用户需求 12 点；一费标准表为 24）。 */
    private static final double TEMPLATE_MAX_HEALTH = 12.0;
    /** 攻击力模板 = 碰撞伤害设计值（4 点）。 */
    private static final double TEMPLATE_ATTACK = 4.0;
    private static final double TEMPLATE_MOVE_SPEED = 0.23;
    /** 仇恨距离（一费派生值 max(24, 攻击距离×1.6) 恒为 24）。 */
    private static final double TEMPLATE_FOLLOW_RANGE = 24.0;
    private static final double TEMPLATE_REGEN = 0.1;
    private static final float MANA_MAX = 80.0F;
    private static final float MANA_REGEN = 4.0F;

    // ── 碰撞伤害 ──
    /** 碰撞判定外扩（与原版幻翼俯冲判定同为 {@code inflate(0.2)}）。 */
    private static final double CONTACT_INFLATE = 0.2D;
    /**
     * 碰撞伤害能否伤害玩家。按需求「玩家也在敌人之列」= true。
     * （对玩家只走普通伤害通道，不做多空/真伤直改 —— 与本模组其它棋子对玩家的处理一致。）
     */
    private static final boolean CONTACT_HITS_PLAYERS = true;

    /**
     * 碰撞目标判定：玩家 + {@link Enemy} 接口敌对生物，排除同类/同队/召唤者/创造旁观。
     *
     * <p>与目标选择共用 {@code TargetingConditions.forCombat()}（不含射程：碰撞即接触，不需要距离门），
     * 相当于原版 {@code canAttack(target, TargetingConditions.DEFAULT)} —— 顺带挡住和平难度、
     * 隐身目标与全队免伤。</p>
     */
    private static final TargetingConditions CONTACT_CONDITIONS = TargetingConditions.forCombat();

    /** 飞行目标点（原版幻翼同名字段语义：MoveControl 朝它飞）。 */
    Vec3 moveTargetPoint = Vec3.ZERO;
    /** 盘旋锚点（原版幻翼同名语义）。 */
    BlockPos anchorPoint = BlockPos.ZERO;
    /** 攻击阶段：盘旋 / 俯冲。 */
    AttackPhase attackPhase = AttackPhase.CIRCLE;

    public NuyiEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.xpReward = 5;
        this.moveControl = new NuyiMoveControl(this);
        this.lookControl = new NuyiLookControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, TEMPLATE_MAX_HEALTH)
            .add(Attributes.MOVEMENT_SPEED, TEMPLATE_MOVE_SPEED)
            .add(Attributes.ATTACK_DAMAGE, TEMPLATE_ATTACK)
            .add(Attributes.ARMOR, 0.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.0)
            .add(Attributes.FOLLOW_RANGE, TEMPLATE_FOLLOW_RANGE);
        addStandardCustomAttributes(builder);
        addManaAttributes(builder);
        return builder;
    }

    @Override
    protected double yizxianMaxHealthTemplate() {
        return TEMPLATE_MAX_HEALTH;
    }

    /** 一费标准属性分配（生成/加载后第一 tick 由基类 aiStep 调一次）。 */
    @Override
    protected void applyEntityAttributes() {
        applyVanillaDifficultyScale();
        setAttr(YizAttributes.ATTACK_STRENGTH, "attack_strength", 0.0);
        setAttr(YizAttributes.SPELL_POWER, "spell_power", 100.0);
        setAttr(YizAttributes.DAMAGE_BLOCK, "damage_block", scaleDifficulty(0.0));
        setAttr(YizAttributes.DAMAGE_REDUCTION, "damage_reduction", scaleDifficulty(0.0));
        setAttr(YizAttributes.ARMOR, "armor", scaleDifficulty(4.0));
        setAttr(YizAttributes.SPELL_DEFENSE, "spell_defense", scaleDifficulty(4.0));
        setAttr(YizAttributes.CONDUCTION_CAP, "conduction_cap", 100.0);
        setAttr(YizAttributes.INVINCIBILITY_MULT, "invincibility_mult", 16.0);
        setAttr(YizAttributes.LIFE_REGEN_RATE, "life_regen_rate", TEMPLATE_REGEN);
        setAttr(YizAttributes.MAX_MANA, "max_mana", MANA_MAX);
        setAttr(YizAttributes.MANA_REGEN, "mana_regen", MANA_REGEN);
    }

    private void setAttr(net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                         String idKey, double value) {
        net.minecraft.client.yiz.tool.attribute.EntityAttributeGate.set(this, attr, idKey, value);
        net.minecraft.client.yiz.tool.attribute.AttributeStandardizer.registerStandard(this, attr.get(), idKey, value);
    }

    // ── 原版幻翼骨架同款覆写 ──────────────────────────────────────────────

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new NuyiAttackStrategyGoal());
        this.goalSelector.addGoal(2, new NuyiSweepAttackGoal());
        this.goalSelector.addGoal(3, new NuyiCircleAroundAnchorGoal());
        this.targetSelector.addGoal(1, new NuyiTargetGoal());
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new NuyiBodyRotationControl(this);
    }

    /** 原版幻翼：扇翅节拍（声音与客户端动画共用）。 */
    public boolean isFlapping() {
        return (this.getUniqueFlapTickOffset() + this.tickCount) % TICKS_PER_FLAP == 0;
    }

    /** 原版幻翼：每实体相位错开（id×3），避免同屏多只同步扇翅。 */
    public int getUniqueFlapTickOffset() {
        return this.getId() * 3;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return dimensions.height * 0.35F;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 1.0F;
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    /** 飞行单位不吃摔落伤害（原版 {@code FlyingMob} 同款空实现）。 */
    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    /**
     * 飞行移动（等价原版 {@code FlyingMob.travel}）。
     *
     * <p>关键点：重力是在 {@code LivingEntity.travel} 里加的（{@code d2 -= 0.08}），幻翼家族靠覆写 travel
     * 彻底绕开它 —— 所以这里<b>不能</b>调 {@code super.travel}（基类会走原版重力路径，怒翼会直接坠地）。
     * 速度来源只有 {@link NuyiMoveControl} 每 tick 写的 {@code deltaMovement}，本方法只负责位移与阻力。</p>
     */
    @Override
    public void travel(Vec3 travelVector) {
        if (this.isControlledByLocalInstance()) {
            if (this.isInWater()) {
                this.moveRelative(0.02F, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8F));
            } else if (this.isInLava()) {
                this.moveRelative(0.02F, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
            } else {
                BlockPos ground = this.getBlockPosBelowThatAffectsMyMovement();
                float f = 0.91F;
                if (this.onGround()) {
                    f = this.level().getBlockState(ground).getFriction(this.level(), ground, this) * 0.91F;
                }
                float f1 = 0.16277137F / (f * f * f);
                f = 0.91F;
                if (this.onGround()) {
                    f = this.level().getBlockState(ground).getFriction(this.level(), ground, this) * 0.91F;
                }
                this.moveRelative(this.onGround() ? 0.1F * f1 : 0.02F, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale((double) f));
            }
        }

        this.calculateEntityAnimation(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            // 原版幻翼：扇翅到最低点时在客户端放一声翅膀声（纯表现，不涉及同步通道）
            float f = Mth.cos((float) (this.getUniqueFlapTickOffset() + this.tickCount) * FLAP_DEGREES_PER_TICK
                * ((float) Math.PI / 180F) + (float) Math.PI);
            float f1 = Mth.cos((float) (this.getUniqueFlapTickOffset() + this.tickCount + 1) * FLAP_DEGREES_PER_TICK
                * ((float) Math.PI / 180F) + (float) Math.PI);
            if (f > 0.0F && f1 <= 0.0F) {
                this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.PHANTOM_FLAP,
                    this.getSoundSource(), 0.95F + this.random.nextFloat() * 0.05F,
                    0.95F + this.random.nextFloat() * 0.05F, false);
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide()) return;
        // 碰撞伤害：碰撞箱接触敌人即出伤（俯冲撞人之外，平时掠过也会蹭到）
        this.contactDamage();
    }

    /**
     * 碰撞箱接触伤害：与自身碰撞箱（外扩 {@value #CONTACT_INFLATE}）相交的敌人各吃一次普通攻击伤害。
     *
     * <p><b>节流</b>：命中后原版 {@code hurt} 会把目标 {@code invulnerableTime} 置 20，本判定只处理
     * {@code invulnerableTime <= 0} 的目标 ⇒ 同一目标最多 1 次/秒。注意基类
     * {@link YizxianMob#doHurtTarget} 是<b>破无敌帧</b>写入（出伤前把目标无敌帧清零，供多段攻击用），
     * 所以节流必须放在这里判，不能指望 {@code doHurtTarget} 自己挡。</p>
     *
     * <p>伤害值取自身 {@code ATTACK_DAMAGE} 属性（模板 4），因此星级倍率与难度缩放照常生效。</p>
     */
    private void contactDamage() {
        List<LivingEntity> hits = this.level().getEntitiesOfClass(LivingEntity.class,
            this.getBoundingBox().inflate(CONTACT_INFLATE), this::isContactEnemy);
        for (LivingEntity target : hits) {
            if (target.invulnerableTime > 0) continue;
            this.doHurtTarget(target);
        }
    }

    /** 碰撞伤害目标判定：玩家（可关）+ Enemy 接口敌对生物；排除同类、同队、召唤者、创造/旁观。 */
    private boolean isContactEnemy(LivingEntity candidate) {
        if (candidate == this || !candidate.isAlive()) return false;
        boolean player = candidate instanceof Player;
        if (player && !CONTACT_HITS_PLAYERS) return false;
        if (!player && !(candidate instanceof Enemy)) return false;
        if (candidate.getType() == this.getType()) return false;
        if (player) {
            // 自走棋召唤物不吃自家召唤者
            UUID owner = ChessUnitTable.getOwner(this);
            if (owner != null && owner.equals(candidate.getUUID())) return false;
        }
        return CONTACT_CONDITIONS.test(this, candidate);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        SpawnGroupData spawnGroupData, CompoundTag tag) {
        // 原版幻翼：出生点上空 5 格作为盘旋锚点（否则会朝世界原点 (0,0,0) 飞）
        this.anchorPoint = this.blockPosition().above(5);
        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, tag);
    }

    /** 锚点持久化键（加模组前缀，避免与第三方模组/原版幻翼的 AX/AY/AZ 撞键）。 */
    private static final String TAG_ANCHOR = "YizNuyiAnchor";

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putIntArray(TAG_ANCHOR, new int[]{this.anchorPoint.getX(), this.anchorPoint.getY(), this.anchorPoint.getZ()});
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int[] anchor = tag.getIntArray(TAG_ANCHOR);
        // 只接受长度合法、坐标在世界范围内的锚点：外部模组往存档里塞越界锚点会让盘旋目标点算到世界外，
        // 怒翼会一直朝边界飞（3 千万是原版世界边界上限）
        if (anchor.length == 3
            && Math.abs(anchor[0]) <= 30000000 && Math.abs(anchor[2]) <= 30000000
            && anchor[1] >= this.level().getMinBuildHeight() && anchor[1] <= this.level().getMaxBuildHeight()) {
            this.anchorPoint = new BlockPos(anchor[0], anchor[1], anchor[2]);
        }
    }

    /** 攻击阶段（原版幻翼 AttackPhase）。 */
    enum AttackPhase {
        CIRCLE,
        SWOOP
    }

    /**
     * 目标选择（原版 {@code PhantomAttackPlayerTargetGoal} 的扩展版）：
     * 原版只扫玩家；此处按需求扫「玩家 + {@link Enemy} 接口敌对生物」（本模组棋子不实现 Enemy，不会被误锁）。
     */
    class NuyiTargetGoal extends Goal {
        private final TargetingConditions attackTargeting = TargetingConditions.forCombat();
        private int nextScanTick = reducedTickDelay(20);

        @Override
        public boolean canUse() {
            if (this.nextScanTick > 0) {
                --this.nextScanTick;
                return false;
            }
            this.nextScanTick = reducedTickDelay(60);
            double range = Math.max(8.0D, NuyiEntity.this.getAttributeValue(Attributes.FOLLOW_RANGE));
            // 竖直方向给足高度：幻翼在目标上空 20~40 格盘旋，扁盒子会永远扫不到人（原版同款 16/64/16）
            List<LivingEntity> candidates = NuyiEntity.this.level().getEntitiesOfClass(LivingEntity.class,
                NuyiEntity.this.getBoundingBox().inflate(16.0D, 64.0D, 16.0D),
                e -> (e instanceof Player || e instanceof Enemy)
                    && this.attackTargeting.range(range).test(NuyiEntity.this, e));
            if (candidates.isEmpty()) return false;
            // 原版：优先打最高的目标（幻翼从上方俯冲）。方法引用写成显式参数类型，
            // 否则 Comparator.comparingDouble 在嵌套 sort 目标类型下推断不出 T（Java 推断限制）
            candidates.sort(Comparator.comparingDouble((Entity e) -> e.getY()).reversed());
            NuyiEntity.this.setTarget(candidates.get(0));
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = NuyiEntity.this.getTarget();
            return target != null && NuyiEntity.this.canAttack(target, TargetingConditions.DEFAULT);
        }
    }

    /** 攻击节奏（原版 {@code PhantomAttackStrategyGoal}）：盘旋一段时间 → 切俯冲 → 俯冲完回盘旋。 */
    class NuyiAttackStrategyGoal extends Goal {
        private int nextSweepTick;

        @Override
        public boolean canUse() {
            LivingEntity target = NuyiEntity.this.getTarget();
            return target != null && NuyiEntity.this.canAttack(target, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            this.nextSweepTick = this.adjustedTickDelay(10);
            NuyiEntity.this.attackPhase = AttackPhase.CIRCLE;
            this.setAnchorAboveTarget();
        }

        @Override
        public void stop() {
            NuyiEntity.this.anchorPoint = NuyiEntity.this.level()
                .getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, NuyiEntity.this.anchorPoint)
                .above(10 + NuyiEntity.this.random.nextInt(20));
        }

        @Override
        public void tick() {
            if (NuyiEntity.this.attackPhase == AttackPhase.CIRCLE) {
                --this.nextSweepTick;
                if (this.nextSweepTick <= 0) {
                    NuyiEntity.this.attackPhase = AttackPhase.SWOOP;
                    this.setAnchorAboveTarget();
                    this.nextSweepTick = this.adjustedTickDelay((8 + NuyiEntity.this.random.nextInt(4)) * 20);
                    NuyiEntity.this.playSound(SoundEvents.PHANTOM_SWOOP, 10.0F,
                        0.95F + NuyiEntity.this.random.nextFloat() * 0.1F);
                }
            }
        }

        private void setAnchorAboveTarget() {
            LivingEntity target = NuyiEntity.this.getTarget();
            if (target == null) return;
            NuyiEntity.this.anchorPoint = target.blockPosition().above(20 + NuyiEntity.this.random.nextInt(20));
            if (NuyiEntity.this.anchorPoint.getY() < NuyiEntity.this.level().getSeaLevel()) {
                NuyiEntity.this.anchorPoint = new BlockPos(NuyiEntity.this.anchorPoint.getX(),
                    NuyiEntity.this.level().getSeaLevel() + 1, NuyiEntity.this.anchorPoint.getZ());
            }
        }
    }

    /** 姿态（原版 {@code PhantomBodyRotationControl}）：身体朝向直接吃 yRot，不做原版走路转身插值。 */
    class NuyiBodyRotationControl extends BodyRotationControl {
        NuyiBodyRotationControl(Mob mob) {
            super(mob);
        }

        @Override
        public void clientTick() {
            NuyiEntity.this.yHeadRot = NuyiEntity.this.yBodyRot;
            NuyiEntity.this.yBodyRot = NuyiEntity.this.getYRot();
        }
    }

    /**
     * 是否继承原版幻翼的「怕猫」特性（猫在 16 格内会中止俯冲并嘶叫）。
     *
     * <p>默认关闭：幻翼怕猫是原版怪物的彩蛋设定，怒翼是本模组棋子，不该被一只猫废掉俯冲。
     * 想还原原版行为把这里改 true 即可（下方代码完整保留）。</p>
     */
    private static final boolean SCARED_OF_CATS = false;

    /** 俯冲（原版 {@code PhantomSweepAttackGoal}）：朝目标俯冲，碰撞箱相交即出伤并转回盘旋。 */
    class NuyiSweepAttackGoal extends NuyiMoveTargetGoal {
        private static final int CAT_SEARCH_TICK_DELAY = 20;
        private boolean isScaredOfCat;
        private int catSearchTick;

        @Override
        public boolean canUse() {
            return NuyiEntity.this.getTarget() != null && NuyiEntity.this.attackPhase == AttackPhase.SWOOP;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = NuyiEntity.this.getTarget();
            if (target == null) {
                return false;
            } else if (!target.isAlive()) {
                return false;
            } else {
                if (target instanceof Player player && (target.isSpectator() || player.isCreative())) {
                    return false;
                }
                if (!this.canUse()) {
                    return false;
                } else {
                    if (SCARED_OF_CATS && NuyiEntity.this.tickCount > this.catSearchTick) {
                        this.catSearchTick = NuyiEntity.this.tickCount + CAT_SEARCH_TICK_DELAY;
                        List<net.minecraft.world.entity.animal.Cat> cats = NuyiEntity.this.level()
                            .getEntitiesOfClass(net.minecraft.world.entity.animal.Cat.class,
                                NuyiEntity.this.getBoundingBox().inflate(16.0D),
                                net.minecraft.world.entity.EntitySelector.ENTITY_STILL_ALIVE);
                        for (net.minecraft.world.entity.animal.Cat cat : cats) {
                            cat.hiss();
                        }
                        this.isScaredOfCat = !cats.isEmpty();
                    }
                    return !this.isScaredOfCat;
                }
            }
        }

        @Override
        public void stop() {
            NuyiEntity.this.setTarget(null);
            NuyiEntity.this.attackPhase = AttackPhase.CIRCLE;
        }

        @Override
        public void tick() {
            LivingEntity target = NuyiEntity.this.getTarget();
            if (target == null) return;
            NuyiEntity.this.moveTargetPoint = new Vec3(target.getX(), target.getY(0.5D), target.getZ());
            if (NuyiEntity.this.getBoundingBox().inflate(CONTACT_INFLATE).intersects(target.getBoundingBox())) {
                NuyiEntity.this.doHurtTarget(target);
                NuyiEntity.this.attackPhase = AttackPhase.CIRCLE;
                if (!NuyiEntity.this.isSilent()) {
                    NuyiEntity.this.level().levelEvent(1039, NuyiEntity.this.blockPosition(), 0);
                }
            } else if (NuyiEntity.this.horizontalCollision || NuyiEntity.this.hurtTime > 0) {
                NuyiEntity.this.attackPhase = AttackPhase.CIRCLE;
            }
        }
    }

    /** 飞行目标基类（原版 {@code PhantomMoveTargetGoal}）：占 MOVE 旗标 + 到点判定。 */
    abstract class NuyiMoveTargetGoal extends Goal {
        NuyiMoveTargetGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        protected boolean touchingTarget() {
            return NuyiEntity.this.moveTargetPoint.distanceToSqr(
                NuyiEntity.this.getX(), NuyiEntity.this.getY(), NuyiEntity.this.getZ()) < 4.0D;
        }
    }

    /** 绕锚点盘旋（原版 {@code PhantomCircleAroundAnchorGoal}）：随机半径/高度/顺逆时针，撞到目标即换点。 */
    class NuyiCircleAroundAnchorGoal extends NuyiMoveTargetGoal {
        private float angle;
        private float distance;
        private float height;
        private float clockwise;

        @Override
        public boolean canUse() {
            return NuyiEntity.this.getTarget() == null || NuyiEntity.this.attackPhase == AttackPhase.CIRCLE;
        }

        @Override
        public void start() {
            this.distance = 5.0F + NuyiEntity.this.random.nextFloat() * 10.0F;
            this.height = -4.0F + NuyiEntity.this.random.nextFloat() * 9.0F;
            this.clockwise = NuyiEntity.this.random.nextBoolean() ? 1.0F : -1.0F;
            this.selectNext();
        }

        @Override
        public void tick() {
            if (NuyiEntity.this.random.nextInt(this.adjustedTickDelay(350)) == 0) {
                this.height = -4.0F + NuyiEntity.this.random.nextFloat() * 9.0F;
            }
            if (NuyiEntity.this.random.nextInt(this.adjustedTickDelay(250)) == 0) {
                ++this.distance;
                if (this.distance > 15.0F) {
                    this.distance = 5.0F;
                    this.clockwise = -this.clockwise;
                }
            }
            if (NuyiEntity.this.random.nextInt(this.adjustedTickDelay(450)) == 0) {
                this.angle = NuyiEntity.this.random.nextFloat() * 2.0F * (float) Math.PI;
                this.selectNext();
            }
            if (this.touchingTarget()) {
                this.selectNext();
            }
            if (NuyiEntity.this.moveTargetPoint.y < NuyiEntity.this.getY()
                && !NuyiEntity.this.level().isEmptyBlock(NuyiEntity.this.blockPosition().below(1))) {
                this.height = Math.max(1.0F, this.height);
                this.selectNext();
            }
            if (NuyiEntity.this.moveTargetPoint.y > NuyiEntity.this.getY()
                && !NuyiEntity.this.level().isEmptyBlock(NuyiEntity.this.blockPosition().above(1))) {
                this.height = Math.min(-1.0F, this.height);
                this.selectNext();
            }
        }

        private void selectNext() {
            if (BlockPos.ZERO.equals(NuyiEntity.this.anchorPoint)) {
                NuyiEntity.this.anchorPoint = NuyiEntity.this.blockPosition();
            }
            this.angle += this.clockwise * 15.0F * ((float) Math.PI / 180F);
            NuyiEntity.this.moveTargetPoint = Vec3.atLowerCornerOf(NuyiEntity.this.anchorPoint)
                .add(this.distance * Mth.cos(this.angle), -4.0F + this.height, this.distance * Mth.sin(this.angle));
        }
    }

    /** 视线控制（原版 {@code PhantomLookControl}）：空实现 —— 幻翼的头朝向完全由移动方向决定。 */
    class NuyiLookControl extends LookControl {
        NuyiLookControl(Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {
        }
    }

    /**
     * 飞行移动控制（原版 {@code PhantomMoveControl}）：朝 {@link #moveTargetPoint} 计算期望速度，
     * 每 tick 把 {@code deltaMovement} 以 0.2 的系数插值过去（0.2 是幻翼特有的"飘"，别改成 1.0）。
     * 撞墙时直接掉头 180°。俯仰角（xRot）也在这里写，渲染器据此整机俯仰。
     */
    class NuyiMoveControl extends MoveControl {
        private float speed = 0.1F;

        NuyiMoveControl(Mob mob) {
            super(mob);
        }

        @Override
        public void tick() {
            if (NuyiEntity.this.horizontalCollision) {
                NuyiEntity.this.setYRot(NuyiEntity.this.getYRot() + 180.0F);
                this.speed = 0.1F;
            }
            double d0 = NuyiEntity.this.moveTargetPoint.x - NuyiEntity.this.getX();
            double d1 = NuyiEntity.this.moveTargetPoint.y - NuyiEntity.this.getY();
            double d2 = NuyiEntity.this.moveTargetPoint.z - NuyiEntity.this.getZ();
            double d3 = Math.sqrt(d0 * d0 + d2 * d2);
            if (Math.abs(d3) > (double) 1.0E-5F) {
                double d4 = 1.0D - Math.abs(d1 * (double) 0.7F) / d3;
                d0 *= d4;
                d2 *= d4;
                d3 = Math.sqrt(d0 * d0 + d2 * d2);
                double d5 = Math.sqrt(d0 * d0 + d2 * d2 + d1 * d1);
                float f = NuyiEntity.this.getYRot();
                float f1 = (float) Mth.atan2(d2, d0);
                float f2 = Mth.wrapDegrees(NuyiEntity.this.getYRot() + 90.0F);
                float f3 = Mth.wrapDegrees(f1 * (180F / (float) Math.PI));
                NuyiEntity.this.setYRot(Mth.approachDegrees(f2, f3, 4.0F) - 90.0F);
                NuyiEntity.this.yBodyRot = NuyiEntity.this.getYRot();
                if (Mth.degreesDifferenceAbs(f, NuyiEntity.this.getYRot()) < 3.0F) {
                    this.speed = Mth.approach(this.speed, 1.8F, 0.005F * (1.8F / this.speed));
                } else {
                    this.speed = Mth.approach(this.speed, 0.2F, 0.025F);
                }
                float f4 = (float) (-(Mth.atan2(-d1, d3) * (double) (180F / (float) Math.PI)));
                NuyiEntity.this.setXRot(f4);
                float f5 = NuyiEntity.this.getYRot() + 90.0F;
                double d6 = (double) (this.speed * Mth.cos(f5 * ((float) Math.PI / 180F))) * Math.abs(d0 / d5);
                double d7 = (double) (this.speed * Mth.sin(f5 * ((float) Math.PI / 180F))) * Math.abs(d2 / d5);
                double d8 = (double) (this.speed * Mth.sin(f4 * ((float) Math.PI / 180F))) * Math.abs(d1 / d5);
                Vec3 vec3 = NuyiEntity.this.getDeltaMovement();
                NuyiEntity.this.setDeltaMovement(vec3.add((new Vec3(d6, d8, d7)).subtract(vec3).scale(0.2D)));
            }
        }
    }
}
