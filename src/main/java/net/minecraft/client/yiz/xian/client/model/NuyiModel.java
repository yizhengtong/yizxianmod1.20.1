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
import net.minecraft.client.yiz.xian.entity.NuyiEntity;
import net.minecraft.util.Mth;

/**
 * 怒翼模型（Blockbench 5.2.0 导出，原版 ModelPart 层级）。
 *
 * <p><b>几何</b>：直接沿用导出文件（{@code gui/实体项目/怒翼.java}）的部件坐标，不做层级重排。
 * 导出的部件全部挂在根节点、坐标已是"世界摆位"（y≈17~20，即标准模型空间里脚底 y=24 往上 4~7 像素），
 * 所以渲染器<b>不要</b>再叠加原版 {@code PhantomRenderer.scale} 里的 {@code translate(0, 1.3125, 0.1875)}
 * —— 那是给原版 PhantomModel（部件摆在 y≈0）做补偿用的，套到本模型上会把怒翼整体压到碰撞箱下面。
 * 贴图 64×64，与原版幻翼同布局。</p>
 *
 * <p><b>动画</b>：扇翅/摆尾公式逐行取自原版 {@code PhantomModel.setupAnim}
 * （角度增量 {@link NuyiEntity#FLAP_DEGREES_PER_TICK}、相位 {@code id×3 + ageInTicks}），
 * 因此怒翼的扇翅节拍与幻翼完全同步（客户端扇翅声也用同一公式，见 {@code NuyiEntity.tick}）。</p>
 */
public class NuyiModel<T extends NuyiEntity> extends HierarchicalModel<T> {

    /** 扇翅最大摆角（度）——原版幻翼同为 16。 */
    private static final float FLAP_AMPLITUDE_DEG = 16.0F;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart leftWing;
    private final ModelPart leftWingTip;
    private final ModelPart rightWing;
    private final ModelPart rightWingTip;
    private final ModelPart tail;
    private final ModelPart tail2;

    public NuyiModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.leftWing = root.getChild("left_wing");
        this.leftWingTip = root.getChild("left_wing_tip");
        this.rightWing = root.getChild("right_wing");
        this.rightWingTip = root.getChild("right_wing_tip");
        this.tail = root.getChild("tail");
        this.tail2 = root.getChild("tail2");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        partdefinition.addOrReplaceChild("body", CubeListBuilder.create()
            .texOffs(0, 8).addBox(-3.0F, -2.0F, -8.0F, 5.0F, 3.0F, 9.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.5F, 19.0F, 0.0F));

        partdefinition.addOrReplaceChild("head", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-5.0F, -4.0F, -5.0F, 9.0F, 4.0F, 5.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.5F, 20.25F, -8.0F));

        partdefinition.addOrReplaceChild("left_wing", CubeListBuilder.create()
            .texOffs(23, 12).addBox(0.0F, 0.0F, 0.0F, 6.0F, 2.0F, 9.0F, new CubeDeformation(0.0F)),
            PartPose.offset(2.5F, 17.0F, -8.0F));

        partdefinition.addOrReplaceChild("left_wing_tip", CubeListBuilder.create()
            .texOffs(16, 24).addBox(0.0F, 0.0F, 0.0F, 13.0F, 1.0F, 9.0F, new CubeDeformation(0.0F)),
            PartPose.offset(8.5F, 17.0F, -8.0F));

        partdefinition.addOrReplaceChild("right_wing", CubeListBuilder.create()
            .texOffs(23, 12).mirror().addBox(-6.0F, 0.0F, 0.0F, 6.0F, 2.0F, 9.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offset(-2.5F, 17.0F, -8.0F));

        partdefinition.addOrReplaceChild("right_wing_tip", CubeListBuilder.create()
            .texOffs(16, 24).mirror().addBox(-13.0F, 0.0F, 0.0F, 13.0F, 1.0F, 9.0F, new CubeDeformation(0.0F)).mirror(false),
            PartPose.offset(-8.5F, 17.0F, -8.0F));

        partdefinition.addOrReplaceChild("tail", CubeListBuilder.create()
            .texOffs(3, 20).addBox(-2.0F, 0.0F, 0.0F, 3.0F, 2.0F, 6.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.5F, 17.0F, 1.0F));

        partdefinition.addOrReplaceChild("tail2", CubeListBuilder.create()
            .texOffs(4, 29).addBox(-1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.5F, 17.5F, 7.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.root.getAllParts().forEach(ModelPart::resetPose);

        // 扇翅相位：与原版幻翼一致（每实体 id×3 错开 + 世界时间），客户端扇翅声用同一公式
        float f = (float) (entity.getUniqueFlapTickOffset() + ageInTicks)
            * NuyiEntity.FLAP_DEGREES_PER_TICK * ((float) Math.PI / 180F);
        float flap = Mth.cos(f) * FLAP_AMPLITUDE_DEG * ((float) Math.PI / 180F);
        this.leftWing.zRot = flap;
        this.leftWingTip.zRot = flap;
        this.rightWing.zRot = -flap;
        this.rightWingTip.zRot = -flap;

        // 摆尾：两节同相位（原版幻翼公式）
        float tailRot = -(5.0F + Mth.cos(f * 2.0F) * 5.0F) * ((float) Math.PI / 180F);
        this.tail.xRot = tailRot;
        this.tail2.xRot = tailRot;

        // 头部随视角：原版幻翼的头挂在 body 上、完全靠机体朝向带动（不做头转），
        // 本模型头是根子节点，直接吃视角角度，盘旋时能"盯着"目标（视觉上更贴合俯冲 AI）
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
    }
}
