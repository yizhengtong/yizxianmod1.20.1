package net.minecraft.client.yiz.xian.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.yiz.xian.YizxianMod;
import net.minecraft.client.yiz.xian.client.model.NuyiModel;
import net.minecraft.client.yiz.xian.entity.NuyiEntity;
import net.minecraft.resources.ResourceLocation;

/**
 * 怒翼渲染器（原版 ModelPart 模型，单张纹理）。
 *
 * <p>整机俯仰：原版 {@code PhantomRenderer.setupRotations} 在父类旋转之后额外
 * {@code mulPose(Axis.XP.rotationDegrees(getXRot()))}，把机体按飞行俯仰角绕 X 轴转 —— 俯冲时机头朝下、
 * 爬升时朝上。俯仰角由 {@code NuyiEntity.NuyiMoveControl} 每 tick 写入，所以这里必须照做，
 * 否则模型永远平飞、和幻翼手感差很远。</p>
 *
 * <p>不照搬原版 {@code PhantomRenderer.scale}：那里是按 phantomSize 放大 + 位置补偿
 * （{@code translate(0, 1.3125, 0.1875)}），本模型部件坐标已是世界摆位，叠加会整体下移（见 NuyiModel 类注释）。</p>
 */
public class NuyiRenderer extends MobRenderer<NuyiEntity, NuyiModel<NuyiEntity>> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
        new ResourceLocation(YizxianMod.MODID, "nuyi"), "main");

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        YizxianMod.MODID, "textures/entity/nuyi.png");

    public NuyiRenderer(EntityRendererProvider.Context context) {
        super(context, new NuyiModel<>(context.bakeLayer(LAYER)), 0.75F);
    }

    @Override
    public ResourceLocation getTextureLocation(NuyiEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void setupRotations(NuyiEntity entity, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTicks) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
    }
}
