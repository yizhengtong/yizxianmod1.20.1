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
import net.minecraft.client.yiz.xian.client.animation.ShanlinshouzheAnimations;
import net.minecraft.client.yiz.xian.entity.ShanlinshouzheEntity;

/**
 * 山林首者模型（原版 ModelPart，Warden 同人骨骼 + 右手武器）。动画走原版 AnimationDefinition。
 */
public class ShanlinshouzheModel<T extends ShanlinshouzheEntity> extends HierarchicalModel<T> {

    private final ModelPart root;

    public ShanlinshouzheModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        partdefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 32).addBox(-8.0F, -16.0F, -5.0F, 16.0F, 16.0F, 10.0F, new CubeDeformation(0.0F))
            .texOffs(52, 32).addBox(-23.0F, -25.0F, 1.0F, 16.0F, 16.0F, 0.0F, new CubeDeformation(0.0F))
            .texOffs(58, 0).addBox(8.0F, -25.0F, 1.0F, 16.0F, 16.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -10.0F, -1.0F));

        partdefinition.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(90, 11).mirror().addBox(0.0F, -13.0F, -4.1F, 9.0F, 21.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false)
            .texOffs(90, 11).addBox(-9.0F, -13.0F, -4.1F, 9.0F, 21.0F, 0.0F, new CubeDeformation(0.0F))
            .texOffs(0, 0).addBox(-9.0F, -13.0F, -4.0F, 18.0F, 21.0F, 11.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 3.0F, -1.0F));

        partdefinition.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, 58).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 28.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(13.0F, -10.0F, 0.0F));

        PartDefinition right_arm = partdefinition.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(44, 50).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 28.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(-13.0F, -10.0F, 0.0F));

        PartDefinition wuqi = right_arm.addOrReplaceChild("wuqi", CubeListBuilder.create(), PartPose.offset(13.0F, 30.0F, 0.0F));
        PartDefinition bone = wuqi.addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        bone.addOrReplaceChild("body_r1", CubeListBuilder.create().texOffs(20, 33).addBox(-0.5F, -36.0F, -0.5F, 1.0F, 36.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-15.0F, -9.0F, 7.0F, 1.5708F, 0.0F, 0.0F));
        PartDefinition bone3 = bone.addOrReplaceChild("bone3", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition bone2 = bone3.addOrReplaceChild("bone2", CubeListBuilder.create().texOffs(10, 4).addBox(-22.0F, -20.0F, -32.0F, 8.0F, 20.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
        bone2.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(15, 10).addBox(-8.0F, -7.0F, -1.0F, 9.0F, 10.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-15.0F, -25.0F, -26.0F, 1.5708F, 0.0F, 0.0F));
        bone2.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(11, 8).addBox(-10.0F, -9.0F, -1.0F, 14.0F, 14.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-15.0F, -21.0F, -26.0F, 1.5708F, 0.0F, 0.0F));
        bone2.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(12, 7).addBox(-8.0F, -7.0F, -1.0F, 10.0F, 10.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-15.0F, 3.0F, -26.0F, 1.5708F, 0.0F, 0.0F));

        partdefinition.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(76, 76).addBox(-2.9F, 0.0F, -3.0F, 6.0F, 13.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(6.0F, 11.0F, -1.0F));

        partdefinition.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(76, 48).addBox(-3.1F, 0.0F, -3.0F, 6.0F, 13.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-6.0F, 11.0F, -1.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        this.animate(entity.idleAnimationState, ShanlinshouzheAnimations.IDLE, ageInTicks);
        this.animate(entity.walkAnimationState, ShanlinshouzheAnimations.WALK, ageInTicks);
        this.animate(entity.counterAnimationState, ShanlinshouzheAnimations.COUNTER, ageInTicks);
        this.animate(entity.skillAnimationState, ShanlinshouzheAnimations.SKILL, ageInTicks);
        this.animate(entity.attackAnimationState, ShanlinshouzheAnimations.ATTACK, ageInTicks);
        this.animate(entity.deathAnimationState, ShanlinshouzheAnimations.DEATH, ageInTicks);
        stopAnimationWhenDone(entity.counterAnimationState, 550);
        stopAnimationWhenDone(entity.skillAnimationState, 583);
        stopAnimationWhenDone(entity.attackAnimationState, 833);
    }

    private static void stopAnimationWhenDone(net.minecraft.world.entity.AnimationState state, long durationMs) {
        if (state.getAccumulatedTime() >= durationMs) {
            state.stop();
        }
    }

    @Override
    public ModelPart root() {
        return this.root;
    }
}
