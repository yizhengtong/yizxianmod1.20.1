package net.minecraft.client.yiz.xian.client.model;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.yiz.xian.client.animation.TiedoushiAnimations;
import net.minecraft.client.yiz.xian.entity.TiedoushiEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AnimationState;

/**
 * 铁斗士模型（Blockbench 5.1.6 导出，原版 ModelPart 层级 + AnimationState 驱动）。
 */
public class TiedoushiModel<T extends TiedoushiEntity> extends HierarchicalModel<T> {

    /**
     * 动画播放速度倍率：与攻击速度同步（1.3 = 快 30%）。
     *
     * <p>用原版 {@code animate(state, def, ageInTicks, speed)} 重载提速，不动 Blockbench 关键帧；
     * 实体的伤害关键帧 tick 数已按同一倍率折算（见 {@code TiedoushiEntity.SPEED_SCALE}），两者必须一致。</p>
     */
    private static final float ANIM_SPEED = 1.3F;

    private final ModelPart root;
    private final ModelPart all;
    private final ModelPart body;
    private final ModelPart torso;
    private final ModelPart chest;
    private final ModelPart head;
    private final ModelPart arms;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart leftFist;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart rightFist;
    private final ModelPart waist;
    private final ModelPart legs;
    private final ModelPart rightLeg;
    private final ModelPart rightCalf;
    private final ModelPart leftLeg;
    private final ModelPart leftCalf;

    public TiedoushiModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.all = root.getChild("all");
        this.body = this.all.getChild("body");
        this.torso = this.body.getChild("torso");
        this.chest = this.torso.getChild("chest");
        this.head = this.chest.getChild("head");
        this.arms = this.chest.getChild("arms");
        this.leftArm = this.arms.getChild("left_arm");
        this.leftForearm = this.leftArm.getChild("left_forearm");
        this.leftFist = this.leftForearm.getChild("left_fist");
        this.rightArm = this.arms.getChild("right_arm");
        this.rightForearm = this.rightArm.getChild("right_forearm");
        this.rightFist = this.rightForearm.getChild("right_fist");
        this.waist = this.torso.getChild("waist");
        this.legs = this.waist.getChild("legs");
        this.rightLeg = this.legs.getChild("right_leg");
        this.rightCalf = this.rightLeg.getChild("right_calf");
        this.leftLeg = this.legs.getChild("left_leg");
        this.leftCalf = this.leftLeg.getChild("left_calf");
    }

    public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition all = partdefinition.addOrReplaceChild("all", CubeListBuilder.create(), PartPose.offset(0.0F, 4.0F, 0.0F));

		PartDefinition body = all.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, -3.0F, 0.0F));

		PartDefinition torso = body.addOrReplaceChild("torso", CubeListBuilder.create(), PartPose.offset(0.0F, -1.0F, 0.0F));

		PartDefinition chest = torso.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 40).addBox(-9.0F, -12.0F, -6.0F, 18.0F, 12.0F, 11.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 3.0F, 0.0F));

		PartDefinition head = chest.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -9.0F, -5.5F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(24, 0).addBox(-1.0F, -2.0F, -7.5F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -13.0F, -2.0F));

		PartDefinition arms = chest.addOrReplaceChild("arms", CubeListBuilder.create(), PartPose.offset(0.0F, -3.0F, 0.0F));

		PartDefinition left_arm = arms.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(60, 58).addBox(0.0F, -2.5F, -3.0F, 4.0F, 10.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(9.0F, -7.0F, 0.0F));

		PartDefinition left_forearm = left_arm.addOrReplaceChild("left_forearm", CubeListBuilder.create().texOffs(60, 58).addBox(-2.0F, 0.5F, -2.0F, 4.0F, 11.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, 7.0F, -1.0F));

		PartDefinition left_fist = left_forearm.addOrReplaceChild("left_fist", CubeListBuilder.create().texOffs(60, 58).addBox(-2.0F, 0.5F, -3.0F, 4.0F, 9.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 11.0F, 1.0F));

		PartDefinition right_arm = arms.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(60, 21).addBox(-4.0F, -2.5F, -3.0F, 4.0F, 10.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-9.0F, -7.0F, 0.0F));

		PartDefinition right_forearm = right_arm.addOrReplaceChild("right_forearm", CubeListBuilder.create().texOffs(60, 21).addBox(-2.0F, 0.5F, -3.0F, 4.0F, 10.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, 7.0F, 0.0F));

		PartDefinition right_fist = right_forearm.addOrReplaceChild("right_fist", CubeListBuilder.create().texOffs(60, 21).addBox(-2.0F, 0.5F, -3.0F, 4.0F, 10.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 10.0F, 0.0F));

		PartDefinition waist = torso.addOrReplaceChild("waist", CubeListBuilder.create().texOffs(0, 70).addBox(-4.5F, -1.0F, -2.0F, 9.0F, 5.0F, 6.0F, new CubeDeformation(0.5F)), PartPose.offset(0.0F, 4.0F, -1.0F));

		PartDefinition legs = waist.addOrReplaceChild("legs", CubeListBuilder.create(), PartPose.offset(0.0F, 7.0F, 1.0F));

		PartDefinition right_leg = legs.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(37, 0).addBox(-5.5F, -1.0F, -3.0F, 6.0F, 8.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, -2.0F, 0.0F));

		PartDefinition right_calf = right_leg.addOrReplaceChild("right_calf", CubeListBuilder.create().texOffs(37, 0).addBox(-2.5F, -1.0F, -2.0F, 6.0F, 8.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(-3.0F, 8.0F, -1.0F));

		PartDefinition left_leg = legs.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(60, 0).addBox(-0.5F, -1.0F, -3.0F, 6.0F, 8.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, -2.0F, 0.0F));

		PartDefinition left_calf = left_leg.addOrReplaceChild("left_calf", CubeListBuilder.create().texOffs(60, 0).addBox(-3.5F, -1.0F, -2.0F, 6.0F, 8.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(3.0F, 8.0F, -1.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;

        // 待机/行走/追击：三条互斥的循环动画（用 walkAnimation 的稳定移动标志，避免渲染帧抖动导致状态反复重启）
        // 只有存在目标时才播行走/追击：铁斗士是棋子，没有游荡 AI，无目标时即便被攻击击退推着走，
        // 也应保持原地静止的待机动画（walkAnimation 会因被动位移而被判定为"在走"）。
        boolean hasTarget = entity.getTarget() != null && entity.getTarget().isAlive();
        boolean moving = entity.walkAnimation.isMoving() && hasTarget;
        boolean chasing = moving && entity.getTarget() != null;
        entity.idleState.animateWhen(!moving, entity.tickCount);
        entity.walkState.animateWhen(moving && !chasing, entity.tickCount);
        entity.chaseState.animateWhen(chasing, entity.tickCount);
        this.animate(entity.idleState, TiedoushiAnimations.IDLE, ageInTicks, ANIM_SPEED);
        this.animate(entity.walkState, TiedoushiAnimations.WALK, ageInTicks, ANIM_SPEED);
        this.animate(entity.chaseState, TiedoushiAnimations.CHASE, ageInTicks, ANIM_SPEED);

        // 攻击/技能动画随"怒击"层数同步提速（与 TiedoushiEntity.getAttackInterval 的间隔缩放同倍率，
        // 否则间隔缩短后动画会被下一套截断）；待机/行走/追击保持基础倍率，避免走路动画无故加速。
        float attackSpeed = ANIM_SPEED * (1.0F + 0.04F * entity.getRageStacks());

        // 攻击：整套三段连贯动画，播完即停
        if (entity.attackState.isStarted()) {
            this.animate(entity.attackState, TiedoushiAnimations.ATTACK, ageInTicks, attackSpeed);
            stopWhenDone(entity.attackState, TiedoushiAnimations.ATTACK.lengthInSeconds() * 1000.0F + 60.0F);
        }

        // 技能
        if (entity.skillState.isStarted()) {
            this.animate(entity.skillState, TiedoushiAnimations.SKILL_1, ageInTicks, attackSpeed);
            stopWhenDone(entity.skillState, TiedoushiAnimations.SKILL_1.lengthInSeconds() * 1000.0F + 60.0F);
        }
    }

    private static void stopWhenDone(AnimationState state, float durationMs) {
        if (state.isStarted() && state.getAccumulatedTime() >= durationMs) {
            state.stop();
        }
    }
}
